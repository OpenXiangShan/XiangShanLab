# Kunminghu V2 `prefetch.r` 与普通 Load 的 MSHR 仲裁分析

## 方法与结论摘要

本分析使用 **wavekit 开源库**中的 `FstReader` 解析并按时钟查询 FST，而不是用文本化波形猜测时序。输入波形为
[`prefetch-priority-final.fst`](/home/agent2/prefetch-env/XiangShan/build/prefetch-priority-final.fst)，时钟为 `TOP.clock`，在上升沿采样；波形中 `cN` 的仿真时间为 `t=2*N`。FST 共采到 4425 个时钟样点。

测试的直接结论如下。

1. `prefetch.r` 和普通整数 `ld` 没有一条全局的“按指令类型排序”的优先级规则。两者都译码到 `FuType.ldu`，都从 LoadUnit 的同一个整数 issue 输入 `io.ldin` 进入。上游同一 IQ 内确实存在年龄选择，但当前 IQ 还有复杂/简单/直通入口的结构优先级，因此不能概括为全局严格的 ROB-oldest。
2. 跨 LoadPipe 汇入 DCache 的 `missReqArb` 后，仲裁依据是**固定端口号**，而不是 ROB 年龄、`prefetch.r` 类型或 `cmd`。端口优先级为 `mainPipe(in0) > ldu0(in1) > ldu1(in2) > ldu2(in3)`。
3. 本次实测冲突在 `c4330 / t8660`：更老普通 load 为 `PC=0x80000144, ROB=62, LQ=5`，位于 `ldu2/in3`；更年轻的 `prefetch.r` 为 `PC=0x80000154, ROB=66, LQ=9`，位于 `ldu0/in1`。两者均 L1D miss，但年轻 `prefetch.r` 的 `valid/ready/fire=1/1/1`，老 load 为 `1/0/0`。因此年轻预取先被 MissQueue 接受并分配到 MSHR2；老 load 被送入 fast replay，`c4333` 才重试成功并进入 MSHR5。
4. 这里的 `s2_nack_no_mshr=1` 是 `miss_req_valid && !miss_req.ready` 的名字，包含共享仲裁端口未获 grant 的情况；它**不能单独证明 MSHR 已满**。本例中 MissQueue 在冲突时 `valid/ready/fire=1/1/1`、`alloc=1`、`merge=0`、`reject=0`，故胜负由端口仲裁造成，不是 MSHR 容量不足。

这回答题目中的具体问题：更老 load 发生 DCache miss 时，若更年轻 `prefetch.r` 同时位于更高优先级 LoadPipe，则年轻预取可以先进入 MissQueue/MSHR；老 load 不会丢失，会被 `mq_nack` 触发 fast replay 后重新竞争。若将老 load 放在 `ldu0`、预取放在 `ldu2`，同一固定端口规则反过来会让老 load 获胜。这个结论针对该 V2 配置和本次波形，不能外推成“软件预取普遍高于/低于普通 load”。

## 测试程序与构建产物

程序目录：[`prefetch-priority`](/home/agent2/prefetch-env/nexus-am/apps/prefetch-priority)。

- [`main.c`](/home/agent2/prefetch-env/nexus-am/apps/prefetch-priority/main.c:1) 放置两个互不重叠、页对齐的 8 KiB BSS 流：`prefetch_stream=0x80002000`、`demand_stream=0x80004000`。测试前不访问它们，使首轮访问在 L1D 中冷启动。
- [`prefetch_priority.S`](/home/agent2/prefetch-env/nexus-am/apps/prefetch-priority/prefetch_priority.S:1) 默认执行六条更老、不同 64B cache line 的 `ld`，随后六条更年轻、不同 line 的 `prefetch.r`。汇编器不认识该扩展助记符，故使用由 DecodeUnit 定义的 raw `.word` 编码。
- 附带 `pf_priority_reverse` 与 `pf_priority_pair` 控制入口，便于交换静态程序顺序或做成对实验；默认 `main` 只调用正向 burst，避免混入其他访问。
- [`analyze_wave.py`](/home/agent2/prefetch-env/nexus-am/apps/prefetch-priority/analyze_wave.py:1) 使用 `wavekit.FstReader` 自动查找同周期的预取/普通 load 冲突，打印仲裁、fast replay、MissQueue 的 `valid/ready/fire` 和动态 MSHR 号。

关键反汇编在 [`prefetch-priority-riscv64-xs.txt`](/home/agent2/prefetch-env/nexus-am/apps/prefetch-priority/build/prefetch-priority-riscv64-xs.txt:1)：

```text
80000144: 14053f03  ld t5,320(a0)       # a0=0x80004000 -> 0x80004140
80000148: 0015e013  .word 0x0015e013   # prefetch.r 0(a1)
8000014c: 0415e013  .word 0x0415e013   # prefetch.r 64(a1)
80000150: 0815e013  .word 0x0815e013   # prefetch.r 128(a1)
80000154: 0c15e013  .word 0x0c15e013   # prefetch.r 192(a1), 0x800020c0
```

`0x14053f03` 与波形的老 load 指令一致；`0x0c15e013` 与波形的目标 `prefetch.r` 指令一致。二者地址相距不同 cache line，因此本实验的胜者不可能由同一行 MSHR merge 解释。

构建与运行命令如下，已执行成功：

```bash
make -C /home/agent2/prefetch-env/nexus-am/apps/prefetch-priority \
  ARCH=riscv64-xs AM_HOME=/home/agent2/prefetch-env/nexus-am -j2

cd /home/agent2/prefetch-env/XiangShan
./build/emu --no-diff --dump-wave-full \
  --wave-path=/home/agent2/prefetch-env/XiangShan/build/prefetch-priority-final.fst \
  -C 6000 \
  -i /home/agent2/prefetch-env/nexus-am/apps/prefetch-priority/build/prefetch-priority-riscv64-xs.bin

cd /home/agent2/prefetch-env/nexus-am/apps/prefetch-priority
PYTHONDONTWRITEBYTECODE=1 /home/agent2/wavekit-xslab/.venv/bin/python \
  analyze_wave.py /home/agent2/prefetch-env/XiangShan/build/prefetch-priority-final.fst
```

仿真输出为 `HIT GOOD TRAP at pc = 0x80000252`，并报告 `instrCnt=135`、`cycleCnt=4369`。生成的 FST 为 40 MiB。`--no-diff` 表示不做参考模型比较，但内部 ROB difftest 信号仍被完整 dump，可用于退休核对。

## 全局时间线

下表的 `fire` 均为同一上升沿的 `valid & ready`。`ROB` 号在 rename 后用于识别动态实例；`LQ` 是 load queue 指针。两个目标均为程序中唯一的首轮动态实例。

| cycle/time | 阶段或事件 | 老普通 load | 年轻 `prefetch.r` | 握手/结论 |
|---|---|---|---|---|
| c4319/t8638 | IFU -> IBuffer | `PC=0x80000144`, `0x14053f03` | - | packet `1/1/1` |
| c4320/t8640 | IFU -> IBuffer | - | `PC=0x80000154`, `0x0c15e013` | packet `1/1/1` |
| c4321/t8642 | IBuffer 输出 | old load lane0 | - | `1/1/1` |
| c4322/t8644 | `cfVec` / Decode / Rename 输入 | - | `PC=0x80000154`, FTQ=21, offset=4 | 三个边界均 `1/1/1` |
| c4323/t8646 | Rename 输出 / Dispatch | `ROB=62` 已到 dispatch | PFR 经 rename 输出，下一拍到 dispatch | 每个可见 Decoupled 边界 `1/1/1` |
| c4324/t8648 | Dispatch / LSQ | old: `ROB=62,LQ=5` 入 LSQ | PFR: `ROB=66` 从 rename 到 dispatch | dispatch `1/1/1` |
| c4325/t8650 | LSQ / ROB enqueue | - | PFR: `ROB=66,LQ=9` 入 LSQ/ROB | `flushPipe=0` |
| c4328/t8656 | Mem scheduler issue | `issueLda_2`, `ROB=62,LQ=5,op=3` | `issueLda_0`, `ROB=66,LQ=9,op=9` | 两者 `1/1/1` 同拍 issue |
| c4330/t8660 | `missReqArb` 冲突 | `in3=1/0/0`, `source=0,cmd=0,0x80004140` | `in1=1/1/1`, `source=3,cmd=2,0x800020c0` | PFR 获 grant，old load nack |
| c4331/t8662 | MQ pipe / fast replay | old load `fast_rep_in/out` 有效 | pipe 寄存器携带 PFR，MSHR2 acquire fire | PFR 的 `rfWen=0` writeback |
| c4332/t8664 | MissEntry 生效 / replay s1 | old load 进入 LDU2 fast replay s1 | `entries_2.req_valid=1,prefetch=1` | PFR MSHR2 已驻留 |
| c4333/t8666 | replay 后再次仲裁 | old load `s2_fast=1`，`in3=1/1/1` | - | 老 load 此次获得 grant |
| c4334/t8668 | MQ pipe / acquire | pipe 携带 old load，MSHR5 acquire fire | - | old load 分配路径完成 |
| c4335/t8670 | MissEntry 生效 | `entries_5.req_valid=1,prefetch=0` | - | 老 load 已驻留 MSHR5 |
| c4394/t8788 | LSU writeback | `ROB=62`, `paddr=0x80004140`, `rfWen=1` | - | old load 数据回写 |
| c4408/t8816 | Difftest commit lane2 | `PC=0x80000144`, `ROB=62` | - | 正常退休 |
| c4409/t8818 | Difftest commit lane2 | - | `PC=0x80000154`, `ROB=66`, `rfWen=0` | 正常退休 |
| c4421/t8842 | AM `_halt` | - | - | `DiffTrap code=0`, 正常结束 |

## 逐级分析

### Frontend、IBuffer 与 Decode

目标 PFR 在 `c4322` 的
`TOP.SimTop.cpu.l_soc.core_with_l2.core.frontend.io_backend_cfVec_2_*` 上观测为：

```text
valid/ready/fire = 1/1/1
pc               = 0x80000154
instr            = 0x0c15e013
ftqPtr/offset    = 21 / 4
pd.valid         = 1
pd.isRVC          = 0
pd.brType         = 0
pd.isCall/isRet   = 0 / 0
pred_taken        = 0
```

IFU 到 IBuffer 的连接由 [`Frontend.scala:226`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:226) 明确给出，后端看到的 `cfVec` 则直接来自 IBuffer 输出（[`Frontend.scala:439`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:439)）：

```scala
// IFU-Ibuffer
ifu.io.toIbuffer <> ibuffer.io.in
...
io.backend.cfVec <> ibuffer.io.out
```

同一拍 Decode 的 `io_in_2` 和 `io_out_2` 都是 `valid/ready/fire=1/1/1`，PC 仍为 `0x80000154`。在 `c4322 -> c4323`，`decodePipeRenameModule_2.io_in -> io_out` 亦为 `1/1/1`。这说明本次动态 PFR 没有在 frontend/decode/rename 边界等待。

译码与普通 `ld` 的统一入口来自 [`DecodeUnit.scala:165`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:165) 及 [`DecodeUnit.scala:1102`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1102)：

```scala
LD -> XSDecode(SrcType.reg, SrcType.imm, SrcType.X,
  FuType.ldu, LSUOpType.ld, SelImm.IMM_I, xWen = T)

val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") &&
  inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)

when (isPreW || isPreR || isPreI) {
  decodedInst.selImm := SelImm.IMM_S
  decodedInst.fuType := FuType.ldu.U
  decodedInst.canRobCompress := false.B
}
...
isPreR -> LSUOpType.prefetch_r
```

因此 raw encoding `0x0c15e013` 被正确识别为 `prefetch_r`，而非普通 ALU I-type 指令；波形中的 `fuOpType=0x9` 与此对应。`rd=x0` 也解释了后续 `rfWen=0`。

### Rename、Dispatch、ROB 与 LSQ

两个动态实例的可见元数据如下。物理寄存器字段仅用于追踪，不是本题仲裁条件。

| 指令 | Rename/Dispatch 时刻 | ROB | FTQ | `fuType/fuOpType` | `rfWen` | LQ | 物理寄存器 |
|---|---:|---:|---:|---|---:|---:|---|
| `ld t5,320(a0)` | dispatch c4323 | 62 | 20/12 | `0x8000 / 0x3` | 1 | 5 | `psrc0=26,pdest=33` |
| `prefetch.r 192(a1)` | dispatch c4324 | 66 | 21/4 | `0x8000 / 0x9` | 0 | 9 | `psrc0=24,pdest=0` |

`dispatch.io_fromRename_0`（old）在 c4323 与 `dispatch.io_fromRename_2`（PFR）在 c4324 都有 `valid/ready/fire=1/1/1`。PFR 被送到 memory IQ 后在 c4328 由 `issueLda_0` 发射；old 则由 `issueLda_2` 发射。Dispatch 的 IQ 输出连接由 [`NewDispatch.scala:449`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:449) 与 [`NewDispatch.scala:496`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:496) 规定：

```scala
fromRenameUpdate(i).valid := fromRename(i).valid && allowDispatch(i) &&
  !uopBlockByIQ(i) && thisCanActualOut(i) && lsqCanAccept && ...
fromRename(i).ready := allowDispatch(i) && !uopBlockByIQ(i) &&
  thisCanActualOut(i) && lsqCanAccept
...
io.toIssueQueues.zip(IQSelUop).map { x =>
  x._1.valid := x._2.valid
  x._1.bits := x._2.bits
}
```

ROB 的实际 enqueue 是 dispatch 的 `fire` 延一拍注册，源码见 [`CtrlBlock.scala:703`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:703) 和 [`NewDispatch.scala:823`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:823)：

```scala
PipeGroupConnect(renameOut, dispatch.io.fromRename, s1_s3_redirect.valid,
  dispatch.io.toRenameAllFire, "renamePipeDispatch")
...
sink.valid := RegNext(source.valid && !rob.io.redirect.valid)
sink.bits := RegEnable(source.bits, source.valid)
...
io.enqRob.req(i).valid := fromRename(i).fire
io.enqRob.req(i).bits := updatedUop(i)
```

LSQ 分配也只把 load 计为一个 LQ allocation（[`NewDispatch.scala:688`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:688)）：

```scala
}.elsewhen(isLoadVec(i) || isVLoadVec(i)) {
  enqLsqIO.needAlloc(i) := 1.U // load | vload
}
enqLsqIO.req(i).valid := io.fromRename(i).fire && ...
```

波形 `io_mem_lsqEnqIO_req_0` 在 c4324 为 old 的 `ROB=62,LQ=5,fuOp=3,rfWen=1,flushPipe=0`；`req_2` 在 c4325 为 PFR 的 `ROB=66,LQ=9,fuOp=9,rfWen=0,flushPipe=0`。两条 uop 同时携带调试 `sqIdx.value=3, flag=0`，但这不是 store queue allocation；它们均由 `needAlloc=1` 作为 load 进入 LQ。LQ/SQ 指针响应会写回 uop，见 [`LSQWrapper.scala:414`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:414)：

```scala
for ((resp, i) <- io.enq.resp.zipWithIndex) {
  lqOffset(i) := loadFlowPopCount(i)
  resp.lqIdx := lqPtr + lqOffset(i)
  sqOffset(i) := storeFlowPopCount(i)
  resp.sqIdx := sqPtr + sqOffset(i)
}
```

### Issue 与 LoadUnit / LoadPipe

`c4328/t8656` 是双方第一次同时进入 memory execution 的关键点：

| issue 端口 | `valid/ready/fire` | PC | ROB/LQ | `fuOpType` | 含义 |
|---|---|---|---|---|---|
| `backend.io_mem_issueLda_0` | 1/1/1 | `0x80000154` | 66 / 9 | `0x9` | 年轻 `prefetch.r` |
| `backend.io_mem_issueLda_2` | 1/1/1 | `0x80000144` | 62 / 5 | `0x3` | 更老普通 `ld` |

配置中 `LoadPipelineWidth=3`（[`Parameters.scala:214`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214)），MemBlock 将每个 `issueLda(i)` 接到对应 LoadUnit 的单一 `ldin` 接口（[`MemBlock.scala:858`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:858)）：

```scala
// LoadUnit IO
val ldin  = Flipped(Decoupled(new MemExuInput))
val ldout = Decoupled(new MemExuOutput)

for (i <- 0 until LduCnt) {
  loadUnits(i).io.ldin <> io.ooo_to_mem.issueLda(i)
}
```

LoadUnit 的 source mux 直接说明“整数 load / software prefetch first issue”在同一个 `io.ldin` 源上，而不是按软件预取另开一个 execution port（[`LoadUnit.scala:290`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:290)）：

```scala
// src 8: int read / software prefetch first issue from RS (io.in)
...
io.ldin.valid, // int flow first issue or software prefetch
...
io.ldin.ready := s0_can_go && io.dcache.req.ready &&
  s0_src_ready_vec(int_iss_idx)
```

同一 IQ 内的年龄检测会从可 issue entry 中选最老者（[`AgeDetector.scala:92`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/issue/AgeDetector.scala:92)），但 IQ 的最终拼接还有 `comp > simp > enq` 结构优先级（[`IssueQueue.scala:563`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/issue/IssueQueue.scala:563)）。因此源码没有“普通 load 高于 PFR”或反之的判定；本次波形也显示两者可以同拍在不同 LDU 发射。

LoadUnit 在选中后才根据 `fuOpType` 生成 DCache cmd/source（[`LoadUnit.scala:406`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:406)）：

```scala
io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd, M_PFR,
  Mux(s0_sel_src.prf_wr, M_PFW, M_XRD))
io.dcache.req.bits.vaddr := s0_dcache_vaddr
io.dcache.req.bits.instrtype := Mux(s0_sel_src.prf,
  DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
io.dcache.req.bits.debug_robIdx := s0_sel_src.uop.robIdx.value
io.dcache.req.bits.lqIdx := s0_sel_src.uop.lqIdx
```

所以本例字节地址为：old `0x80004000 + 0x140 = 0x80004140`，PFR `0x80002000 + 0x0c0 = 0x800020c0`；经过 LoadPipe 后送往 MissQueue 的地址是 cache-line 对齐形式，但两者仍互异。

## DCache 仲裁：为什么年轻预取先赢（端口优先级，非 PFR 类型优先级）

### c4330 的直接波形证据

常量 `LOAD_SOURCE=0`、`DCACHE_PREFETCH_SOURCE=3` 定义在 [`DCacheWrapper.scala:99`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:99)，`M_XRD=0`、`M_PFR=2` 定义在 [`CacheConstants.scala:29`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/CacheConstants.scala:29)。冲突拍的结果如下：

| `missReqArb` 端口 | producer | 身份 | `valid` | `ready` | `fire` | `source/cmd` | 地址 |
|---|---|---|---:|---:|---:|---|---|
| `in0` | MainPipe | 无有效请求 | 0 | 1 | 0 | payload 无效，不解释 | - |
| `in1` | `ldu0` | PFR, PC `0x80000154`, ROB66 | 1 | 1 | 1 | 3 / 2 | `0x800020c0` |
| `in2` | `ldu1` | PFR, PC `0x80000148`, ROB63 | 1 | 0 | 0 | 3 / 2 | `0x80002000` |
| `in3` | `ldu2` | old LD, PC `0x80000144`, ROB62 | 1 | 0 | 0 | 0 / 0 | `0x80004140` |
| `out` | -> MissQueue | PFR | 1 | 1 | 1 | 3 / 2 | `0x800020c0` |

`in0.valid=0` 时其 payload 是 don't-care，报告特意不把该处仿真垃圾位解释成请求。三个有效请求同时存在，而 `out` 选择 `in1`。这是判断优先级的最直接证据：ROB62 比 ROB66 老，但没有赢。

LoadPipe 的局部状态进一步交叉验证：

```text
ldu0: s2_valid=1, miss_req=1/1, fire=1, nack=0, prefetch=1
ldu1: s2_valid=1, miss_req=1/0, fire=0, nack=1, prefetch=1
ldu2: s2_valid=1, miss_req=1/0, fire=0, nack=1, prefetch=0
```

`LoadPipe` 的 nack 定义并不比较 ROB，也并不读取 `cmd` 作优先级判断（[`LoadPipe.scala:384`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:384)）：

```scala
val s2_miss_req_valid_dup = s2_valid_dup && s2_can_send_miss_req_dup
val s2_miss_req_fire      = s2_miss_req_valid_dup && io.miss_req.ready
val s2_nack_no_mshr       = s2_miss_req_valid_dup && !io.miss_req.ready
...
io.miss_req.valid       := s2_miss_req_valid
io.miss_req.bits.source := s2_instrtype
io.miss_req.bits.cmd    := s2_req.cmd
io.miss_req.bits.addr   := get_block_addr(s2_paddr)
```

这里 `s2_nack_no_mshr` 是“该 LoadPipe 的 miss request 没有 ready”的实现名。本 build 中实际回送给 LoadPipe 的 ready 由下文的 `MissReadyGen` 产生；其固定端口遮罩可使 nack 置位，故应理解为 **miss-request-ready backpressure**，而不应擅自等价成“物理 MSHR 全满”。

### 「missReqArb 按固定端口优先级仲裁」是什么意思

这里的“固定端口优先级”不是“普通 load 一类、PFR 一类各有一个静态端口”，也不是按 ROB 年龄、`cmd` 或 `source` 比较；它是 **elaboration 时把 producer 接到固定输入编号，运行时永远优先选择编号更小的有效输入**。所以这是 strict/static-priority arbiter，而不是 round-robin，也不记录上次获胜者。

`DCacheWrapper` 明确注释 `higher priority is given to lower indices`，并把 MainPipe 固定接到 0、把 `ldu(w)` 固定接到 `w + 1`（[`DCacheWrapper.scala:1031`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1031)、[`DCacheWrapper.scala:1474`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1474)）：

```scala
// higher priority is given to lower indices
val MissReqPortCount = 1 + backendParams.LduCnt + ...
val MainPipeMissReqPort = 0
...
missReqArb.io.in(MainPipeMissReqPort) <> mainPipe.io.miss_req
for (w <- 0 until backendParams.LduCnt) {
  missReqArb.io.in(w + 1) <> ldu(w).io.miss_req
  missReadyGen.io.in(w + 1) <> ldu(w).io.miss_req
}
```

本 FST 的 wavekit hierarchy query 找到 `io_in_0_valid` 到 `io_in_3_valid`，没有找到 `io_in_4_valid`；结合本配置的 3 条 LoadPipe（[`Parameters.scala:214`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214)），当前实际的固定顺序是：

| 固定优先级 | `missReqArb` 输入 | elaboration 时的固定 producer | c4330 的动态请求 |
|---:|---|---|---|
| 最高 | `in0` | `mainPipe.io.miss_req` | 无有效请求 |
| 第 2 | `in1` | `ldu(0).io.miss_req` / DCache LoadPipe0 | PFR，PC `0x80000154`，ROB66 |
| 第 3 | `in2` | `ldu(1).io.miss_req` / DCache LoadPipe1 | PFR，PC `0x80000148`，ROB63 |
| 最低 | `in3` | `ldu(2).io.miss_req` / DCache LoadPipe2 | old LD，PC `0x80000144`，ROB62 |

因此 `in1` 不是“预取专用端口”，`in3` 也不是“普通 load 专用端口”；它们分别是 **LoadPipe0/2 的端口**。同一条管线在不同周期可以送普通 load 或 software PFR。`MemBlock` 将 `issueLda(i)` 接到 `LoadUnit(i)`（[`MemBlock.scala:858`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:858)），再把同 index 的 LoadUnit DCache 接口接给 `dcache.io.lsu.load(i)`（[`MemBlock.scala:886`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:886)）；DCache 的 `ldu(w)` 又接这个 `load(w)` 接口（[`DCacheWrapper.scala:1381`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1381)）。这是本波形中 PFR 落在 `ldu0/in1`、old LD 落在 `ldu2/in3` 的固定路由基础。

#### 代码如何实现“低 index 必胜”

选择树在叶子处直接写成 `Mux(in(0).valid, ..., in(1))`，较大的树则只要左半边有任一有效请求就选择左半边；因此输出总是最低编号的 valid 输入（[`DCacheWrapper.scala:868`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:868)）。其 grant 向量由下列前缀 OR 生成（[`DCacheWrapper.scala:857`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:857)）：

```scala
case _ => true.B +:
  request.tail.init.scanLeft(request.head)(_ || _).map(!_)

val grant = ArbiterCtrl(io.in.map(_.valid))
for ((in, g) <- io.in.zip(grant))
  in.ready := g && io.out.ready
```

对当前 4 个端口，若 `v_i = in_i.valid`，这等价于：

```text
grant[0] = 1
grant[1] = !v0
grant[2] = !(v0 || v1)
grant[3] = !(v0 || v1 || v2)
tree_arbiter_ready[i] = grant[i] && out.ready
```

上面的 `tree_arbiter_ready` 是 `TreeArbiter` 内部为 output handshake 计算的 ready；实际回送给各 LoadPipe 的 `io.miss_req.ready` 则是为时序单独生成的 `MissReadyGen` 路径。生成 RTL 中，`ldu0/1/2.io_miss_req_ready` 分别接到 `_missReadyGen_io_in_1/2/3_ready`（[`DCache.sv:22179`](/home/agent2/prefetch-env/XiangShan/build/rtl/DCache.sv:22179)、[`DCache.sv:22350`](/home/agent2/prefetch-env/XiangShan/build/rtl/DCache.sv:22350)、[`DCache.sv:22521`](/home/agent2/prefetch-env/XiangShan/build/rtl/DCache.sv:22521)）。`MissReadyGen` 使用每端口的 MissQueue query-ready，但仍加上完全相同的“所有更低端口均无 valid”条件（[`DCacheWrapper.scala:917`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:917)）：

```scala
r.ready := mqReadyVec(idx) &&
  !Cat(io.in.slice(0, idx).map(_.valid)).orR
```

两段逻辑都没有读取 ROB index、`source`、`cmd` 或 PFR 标志。换言之，**端口位置是唯一的相互竞争优先级**：若把同拍的 old LD 放在 `in1`、PFR 放在 `in3`，结果会反过来，尽管它们的年龄和类型不变。

#### 本场景的 wavekit 端口归属与 grant

本报告的 `analyze_wave.py` 用 wavekit 直接采样 `missReqArb.io_in_{0..3}_{valid,ready,bits_source,bits_cmd,bits_addr}`、同 index 的 `ldu_*`、以及 `missQueue.io_queryMQ_*_ready`。c4330 的关联结果如下；`source/cmd=3/2` 即 PFR，`0/0` 即普通读。

| 指令 | 波形中的 LoadUnit / LoadPipe | `missReqArb` 输入 | c4330 `valid/ready/fire` | 结果 |
|---|---|---|---|---|
| 较年轻 PFR：PC `0x80000154`，ROB66，addr `0x800020c0` | `LoadUnit0` / `ldu0` | `in1` | `1/1/1`，`source/cmd=3/2` | 当拍输出至 MissQueue，并分配 MSHR2 |
| 另一条 PFR：PC `0x80000148`，ROB63，addr `0x80002000` | `LoadUnit1` / `ldu1` | `in2` | `1/0/0`，`source/cmd=3/2` | 被 `in1` 压住 |
| 更老普通 load：PC `0x80000144`，ROB62，addr `0x80004140` | `LoadUnit2` / `ldu2` | `in3` | `1/0/0`，`source/cmd=0/0` | 被 `in1` 压住，进入 fast replay；c4333 仍从 `in3` 以 `1/1/1` 获胜，并分配 MSHR5 |

同一拍 `missQueue.io_queryMQ_0..3_ready = 1/1/1/1`，且 arbiter `out.ready=1`。所以 `in2`、`in3` 的 `ready=0` 不是 MissQueue 对这两个地址单独拒绝，而恰好是固定端口遮罩：`in0.valid=0`，`in1.valid=1`，故 `in1` 可 fire，而所有更高编号的有效端口都被遮住。输出 `out` 也逐字段等于 `in1`：`src=3`、`cmd=2`、`addr=0x800020c0`。

结论是：**当前场景中 PFR 被路由到 `in1`，普通 load 被路由到 `in3`；PFR 先进入 MSHR 的原因是 `in1 > in3`，不是 “PFR 比 load 优先”，更不是它比 ROB62 老。** PFR 换到较高编号的 LoadPipe 时也会输给较低编号端口上的普通 load。

## MissQueue 与 MSHR 的两级时序

MissQueue enqueue 并不是“arbiter fire 当拍立即看到 entry 已 valid”。源码明确写了两级：arbiter/query 决定 allocation，先进入 pipe register，下一阶段才真正装入 MissEntry（[`MissQueue.scala:151`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:151)）。

```text
s0: request arbiter / judge alloc or merge
                  -> pipeline register (alloc, merge, req, id)
s1: real alloc or merge in MissEntry
```

本次实际时序为：

| cycle | `missQueue.io_req` | alloc/merge/reject/accept | pipe register | MissEntry / acquire |
|---|---|---|---|---|
| c4330 | PFR `v/r/fire=1/1/1`, src=3 cmd=2 addr=`0x800020c0` | `1/0/0/1` | 仍显示前一条 old request，属寄存器延迟 | 决定为 PFR 新分配 |
| c4331 | 下一条普通 load `0x80004040` 已被接收 | `1/0/0/1` | PFR: `alloc=1,mshr=2,src=3,cmd=2` | MSHR2 TileLink acquire `1/1/1`, `source=2`, `reqSource=6`, addr=`0x800020c0` |
| c4332 | 下一条 PFR 被接收 | `1/0/0/1` | 下一条请求 | `entries_2.req_valid=1, source=3, prefetch=1` |
| c4333 | old LD `0x80004140` `v/r/fire=1/1/1` | `1/0/0/1` | - | old load replay 后终于被 MQ 接收 |
| c4334 | 下一条 PFR 被接收 | `1/0/0/1` | old LD: `alloc=1,mshr=5,src=0,cmd=0` | MSHR5 acquire `1/1/1`, `source=5`, `reqSource=2`, addr=`0x80004140` |
| c4335 | 下一条 PFR 被接收 | `1/0/0/1` | - | `entries_5.req_valid=1, source=0, prefetch=0` |

MissQueue 的 `alloc/merge/reject/accept` 组合由 [`MissQueue.scala:1076`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1076) 产生，随后写入 pipeline register（[`MissQueue.scala:1106`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1106)）：

```scala
val merge  = ParallelORR(Cat(secondary_ready_vec ++ ...))
val reject = ParallelORR(Cat(secondary_reject_vec ++ ...))
val alloc  = !reject && !merge && ParallelORR(Cat(primary_ready_vec))
val accept = alloc || merge
...
miss_req_pipe_reg.alloc   := alloc && io.req.valid && !io.req.bits.cancel && !io.wbq_block_miss_req
miss_req_pipe_reg.merge   := merge && io.req.valid && !io.req.bits.cancel && !io.wbq_block_miss_req
miss_req_pipe_reg.mshr_id := io.resp.id
```

`reqSource=6` 对应 `L1DataPrefetch`，`reqSource=2` 对应 `CPULoadData`，枚举见 [`BusKeyField.scala:24`](/home/agent2/prefetch-env/XiangShan/utility/src/main/scala/utility/TLUtils/BusKeyField.scala:24)。MissEntry 会以 `prefetch && !secondary_fired` 给 PFR 的 acquire 标这个来源（[`MissQueue.scala:854`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:854)）：

```scala
when(prefetch && !secondary_fired) {
  io.mem_acquire.bits.user.lift(ReqSourceKey).foreach(
    _ := MemReqSource.L1DataPrefetch.id.U)
}.otherwise {
  when(req.isFromLoad) {
    io.mem_acquire.bits.user.lift(ReqSourceKey).foreach(
      _ := MemReqSource.CPULoadData.id.U)
  }
}
```

因此从 `source=3/cmd=2` 到 MSHR2 的 `reqSource=6` 也与源码一致，不是波形字段被误解。

### 同一 cache line 的限定

本程序刻意避免同 line；如果将地址改成同一 line，结论需要分情况：

- `MissReq` 将 `source >= DCACHE_PREFETCH_SOURCE` 定义为来自 prefetch，而不是单看 `cmd`（[`MissQueue.scala:88`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:88)）。
- 已有 PFR MSHR 而后到普通 load 时，若块/alias 匹配且尚未 refill，普通 load 可以 merge；`late_prefetch` 会将 entry 的 `prefetch := false`，把其视为 demand（[`MissQueue.scala:738`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:738)、[`MissQueue.scala:940`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:940)）。
- 已有普通 demand MSHR 后到的 prefetch 不走普通 load merge 路径；源码断言 late prefetch 不应作为 merge request。这类 case 不应拿来证明本报告的端口胜负。

相关核心代码如下：

```scala
def should_merge(new_req: MissReqWoStoreData): Bool = {
  val block_match = get_block(req.addr) === get_block(new_req.addr)
  val alias_match = is_alias_match(req.vaddr, new_req.vaddr)
  block_match && alias_match &&
    (before_req_sent_can_merge(new_req) || before_data_refill_can_merge(new_req))
}
...
io.prefetch_info.late_prefetch := io.req.valid && !io.req.bits.isFromPrefetch &&
  req_valid && (get_block(req.addr) === get_block(io.req.bits.addr)) && prefetch
when(io.prefetch_info.late_prefetch) { prefetch := false.B }
```

## Fast Replay、bubble 与性能影响

老 load 在 c4330 没有被 kill，也没有等待软件重新发射。LoadUnit 将 DCache 的 `mq_nack` 纳入 fast replay 条件（[`LoadUnit.scala:1260`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1260)、[`LoadUnit.scala:1319`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1319)），并在 s3 把它送回 `io.fast_rep_out`（[`LoadUnit.scala:1813`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1813)：

```scala
val s2_mq_nack = io.dcache.s2_mq_nack && ...
val s2_dcache_fast_rep = s2_mq_nack || ...
val s2_fast_rep = !s2_in.isFastReplay && ... && s2_dcache_fast_rep && s2_troublem
...
io.fast_rep_out.valid := s3_valid && s3_fast_rep
io.fast_rep_out.bits := s3_in
```

实际 bubble/恢复序列：

| cycle range | 边界 | `valid/ready/fire` 或状态 | 可证明的原因 | 影响 |
|---|---|---|---|---|
| c4320-c4328 | IFU/IBuffer/Decode/Rename/Dispatch/Issue，PFR | 每个目标边界均 `1/1/1` | 未观测到 frontend、rename、ROB、LSQ 或 IQ backpressure | PFR 不是由前端 bubble 获利 |
| c4330 | `ldu1` / `ldu2` miss request | `1/0/0`, `s2_nack_no_mshr=1` | 有效的低优先级 arbiter port 被 `in1` 屏蔽 | old LD 和另一 PFR 各被拒一次 |
| c4331 | `LoadUnit_2.fast_rep_in/out` | old `PC=0x80000144,ROB=62,LQ=5` 均 valid | `mq_nack` 导致 fast replay | 老 load 回到 LDU 流程 |
| c4332 | `LoadUnit_2.s1` | `isFastReplay=1` | replay 从 s0/s1 重新推进 | 仍未获得 MSHR |
| c4333 | `LoadUnit_2.s2` / `missReqArb.in3` | `isFastReplay=1`; `1/1/1` | 更高优先级输入本拍没有挡住 `in3` | 老 load 重试成功 |
| c4330 -> c4333 | 老 load 首次请求到被接受 | 3 个采样周期 | 固定端口仲裁 | 可精确归因的额外请求延迟为 3 cycle |
| c4333 -> c4394 | 老 load MSHR accepted 到 writeback | 61 个采样周期 | 真实 demand miss 需等待数据返回；本波形未显示额外同周期仲裁阻塞 | 不是可归咎于 c4330 单次仲裁的全部延迟 |

PFR 在 c4331 的 `writebackLda_0` 已为 `valid/ready=1/1`，`ROB=66`、`paddr/vaddr=0x800020c0`、`rfWen=0`、`flushPipe=0`、`replayInst=0`；它不需要把 load data 写入整数寄存器。old load 的 `writebackLda_2` 到 c4394 才为 `valid/ready=1/1`，`ROB=62`、`paddr/vaddr=0x80004140`、`rfWen=1`、data=`0`（BSS 内容）。

性能含义是明确的：持续由 `ldu0`/`ldu1` 产生 miss 时，`ldu2` 可能反复遭到固定优先级压制并产生 fast replay。若业务关注 demand load 尾延迟，可评估让 scheduler 避免把 demand 长期放入低优先级 LDU，或将 `missReqArb` 改为 age/fairness-aware 策略；这属于微架构策略取舍，而不是本程序需要的功能修复。

## Frontend Redirect、flush 与异常核对

真实测试动态实例的窗口定义为 `c4318..c4411`：覆盖两条取指、发射、仲裁、老 load 回写以及二者退休。在这个窗口，用 wavekit 对以下信号逐拍查询，所有值均为 0：

```text
frontend.io_backend_toFtq_redirect_valid
backend.io_frontend_toFtq_redirect_valid
backend.io_mem_redirect_valid
backend.io_mem_memoryViolation_valid
inner_ctrlBlock.io_frontend_toFtq_redirect_valid
inner_ctrlBlock.io_redirect_valid
rob.io_flushOut_valid
rob.io_redirect_valid
rob.io_exception_valid
rob.io_commits_info_2_needFlush
LoadUnit_0.io_redirect_valid
LoadUnit_2.io_redirect_valid
```

这与 Dispatch `hasException=0/flushPipe=0`、两个 LSU writeback 的 `flushPipe=0`、两次 commit 的 `needFlush=0` 相互印证。CtrlBlock 到 frontend 的 redirect 产生关系在 [`CtrlBlock.scala:331`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:331)；frontend 会将后端 redirect 打一拍为 `needFlush` 并驱动 IBuffer flush（[`Frontend.scala:111`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:111)）：

```scala
io.frontend.toFtq.redirect.valid := s6_flushFromRobValid || s3_redirectGen.valid
io.frontend.toFtq.redirect.bits := Mux(s6_flushFromRobValid,
  frontendFlushBits, s3_redirectGen.bits)
...
val needFlush = RegNext(io.backend.toFtq.redirect.valid)
ibuffer.io.flush := needFlush
```

FST 更早的启动期确实有两次与静态 PC `0x80000144/0x80000154` 重叠的 IFU `f3_flush`（c4223、c4258），不能忽略但也不能归给本测试实例。其根因是启动代码 `ROB17 PC=0x8000007e: csrs mstatus,a0` 引发 redirect：ROB `flushOut` 于 c4238、CtrlBlock redirect 于 c4239、各级 flush 于 c4240、frontend redirect 于 c4244，目标为 `0x80000082`。这发生在真实 burst 取指前；真实 `IFU -> IBuffer` packet 的 c4319/c4320 均为 `valid/ready=1/1` 且 `io_flush=0`。因此报告结论是“目标动态实例没有 redirect/flush”，不是错误地说“整个 FST 没有 redirect”。

## 架构态与 Difftest 状态

ROB 为每条 commit 生成 `DiffInstrCommit`；load 还生成 `DiffLoadEvent`，源码在 [`Rob.scala:1543`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1543)：

```scala
difftest.valid := io.commits.commitValid(i) && io.commits.isCommit
difftest.rfwen := io.commits.commitValid(i) && commitInfo.rfWen &&
  basicDebug.ldest =/= 0.U
...
difftestLoadEvent.valid := io.commits.commitValid(i) &&
  io.commits.isCommit && loadCheck
difftestLoadEvent.paddr := exuOut.paddr
difftestLoadEvent.opType := uop.fuOpType
```

lane2 在 c4408/c4409 的有效退休数据如下：

| 字段 | old `ld` c4408 | `prefetch.r` c4409 |
|---|---|---|
| `isCommit/commitValid` | 1 / 1 | 1 / 1 |
| PC / instr | `0x80000144 / 0x14053f03` | `0x80000154 / 0x0c15e013` |
| ROB / LQ / SQ | 62 / 5 / 0 | 66 / 9 / 0 |
| `isLoad/isStore` | 1 / 0 | 1 / 0 |
| `rfwen` | 1 | 0 |
| `wdest/wpdest` | x30 / 33 | 0 / 0 |
| `skip/special` | 0 / 0 | 0 / 0 |
| LoadEvent valid / paddr | 1 / `0x80004140` | 1 / `0x800020c0` |
| LoadEvent opType / index | 3 / 2 | 9 / 2 |
| atomic / vector load | 0 / 0 | 0 / 0 |
| `needFlush` | 0 | 0 |

PFR 因而会作为 load 类 memory event 留下追踪记录，但不会写 GPR。这与 `rd=x0`、decode 后 `rfWen=0` 和 LSU writeback 共同一致。

波形 dump 的 CSR 由
`inner_intExuBlock.exus_7.csr.csrMod.diffCSRState_csr_*` 提供；在 c4408 和 c4409 两拍值完全相同：

| CSR/状态 | 值 |
|---|---|
| privilegeMode | `0x3` |
| mstatus | `0x8000000a00006000` |
| sstatus | `0x8000000200006000` |
| mepc/sepc/mtval/stval/mtvec/stvec/mcause/scause/satp/mip/mie/medeleg | `0x0` |
| mideleg | `0x1444` |
| mscratch / sscratch | `0x9e84d9dd3497739e` / `0x1958b331db5b053a` |

这些被 dump 的特权/CSR 字段在两条目标指令之间没有改变，且 target 窗口没有 `exception` 或 `redirect`。完整 GPR/FPR/vector architectural array 未在该 FST 的目标 scope 中作为可匹配的 difftest state dump；可用的 Difftest commit、LSU writeback 和 CSR state 已逐项记录。最后 c4421 是 AM `_halt` 指令 `0x0005006b` 在 `PC=0x80000252` 产生的 `DiffTrap code=0`，不是 PFR 或 old load 的异常。`TOP.difftest_exit` 始终为 0，与本次命令的 `--no-diff` 一致，不能用它判断程序结束。

## FSM/状态寄存器汇总

本条路径没有一个“以 PFR 为状态”的单一 enum FSM；应以实际模块的状态寄存器解释，而不能把无关 CMO FSM 的 `state` 套到该请求。

| 模块 | 状态形式 | 本请求的波形状态 | 对行为的作用 | 代码依据 |
|---|---|---|---|---|
| `LoadUnit_0` (PFR) | s0/s1/s2/s3 valid 寄存器 | c4328 s0、c4329 s1、c4330 s2、c4331 s3 有效 | 将 PFR 推入 DCache 并完成无 GPR 写回的 pipeline 输出 | [`LoadUnit.scala:904`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:904), [`LoadUnit.scala:1163`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1163), [`LoadUnit.scala:1537`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1537) |
| `LoadUnit_2` (old LD) | 相同 s0/s1/s2/s3 valid，加 `isFastReplay` | c4330 s2 miss+nack；c4331 fast replay；c4332 s1 fast；c4333 s2 fast 且 fire | nack 后以 replay 而非 squash 重新请求 | [`LoadUnit.scala:1319`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1319) |
| `MissEntry_2` (PFR) | `req_valid,prefetch,s_acquire,s_grantack,s_mainpipe_req,w_grantfirst,w_grantlast,...` boolean phase registers | c4332: `req_valid=1,prefetch=1,s_acquire=1,s_grantack=0,s_mainpipe_req=0,w_grantfirst=0,w_grantlast=0`; `denied=corrupt=0` | allocation 后 acquire 已发送，等待 grant/refill | [`MissQueue.scala:475`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:475), [`MissQueue.scala:551`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:551) |
| `MissEntry_5` (old LD) | 同上 | c4335 起 `req_valid=1,prefetch=0,s_acquire=1,s_grantack=0,s_mainpipe_req=0`; `denied=corrupt=0` | old demand 的独立 miss 已驻留 | 同上 |
| `CMOUnit` | `s_idle/s_sreq/s_wresp/s_lsq_resp` | 未参与本 test | CMO state 存在于 MissQueue 文件但没有被本请求关联 | [`MissQueue.scala:308`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:308) |

MissEntry 的 boolean phase 字段由 allocation 初始化：`s_acquire := io.acquire_fired_by_pipe_reg`，随后 receive grant/mainpipe fire 改变相应 phase（[`MissQueue.scala:551`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:551)、[`MissQueue.scala:697`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:697)）。在本波形截面中，两个 entry 刚被分配且等待内存返回，因此未把它们误称为已 refill 或已完成。

## 信号来源、去向与代码依据

| 信号/边界 | producer -> consumer | 本例值/含义 | 源码定位 |
|---|---|---|---|
| `frontend.io_backend_cfVec_2` | IBuffer -> backend | c4322 PFR `PC=0x80000154`, `0x0c15e013`, `1/1/1` | [`Frontend.scala:226`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:226), [`Frontend.scala:439`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:439) |
| Decode fields | DecodeUnit -> Rename | `FuType.ldu`, PFR `fuOp=0x9` | [`DecodeUnit.scala:1102`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1102) |
| `dispatch.io_fromRename` -> IQ/ROB/LSQ | Rename/Dispatch -> memory IQ, ROB, LSQ | old ROB62 / PFR ROB66；二者 dispatch fire | [`NewDispatch.scala:442`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:442), [`NewDispatch.scala:688`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:688), [`NewDispatch.scala:823`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:823) |
| `issueLda(i)` -> `LoadUnit(i).ldin` | mem scheduler -> MemBlock/LoadUnit | c4328 old LDU2、PFR LDU0 同拍 fire | [`MemBlock.scala:858`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:858) |
| `LoadUnit.io.dcache.req` | LoadUnit -> DCache LoadPipe | PFR 映射 src3/cmd2；old 映射 src0/cmd0 | [`LoadUnit.scala:406`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:406) |
| `ldu(i).io.miss_req` -> `missReqArb` | LoadPipe -> DCache arbiter | c4330 in1 PFR 获 grant，in3 old load 不获 grant | [`DCacheWrapper.scala:1474`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1474) |
| `missReqArb.out` -> `MissQueue.req` | DCache arbiter -> MissQueue | c4330 PFR MQ fire/alloc | [`DCacheWrapper.scala:1532`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1532) |
| `s2_mq_nack` -> `fast_rep_out` | DCache response -> LoadUnit replay -> same LDU input mux | old load c4330 nack，c4331-c4333 replay | [`LoadPipe.scala:530`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:530), [`LoadUnit.scala:1813`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1813) |
| `writebackLda` -> ROB/Difftest | LoadUnit -> backend WB -> ROB commit | PFR c4331 no GPR write；old c4394 GPR write；c4408/4409 retire | [`LoadUnit.scala:1634`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1634), [`Rob.scala:1543`](/home/agent2/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1543) |

## 异常、不一致与范围限制

1. GNU/AM 工具链的反汇编将扩展指令显示为 `.word`，不是 decode 失败；波形 `instr=0x0c15e013`、DecodeUnit 条件和 `fuOpType=0x9` 共同证实它确实是 `prefetch.r`。
2. `MissQueue.miss_req_pipe_reg` 是顺序寄存器。因此 c4330 看到的 pipe payload 是前一请求，不能错误地拿它否定 c4330 的 PFR arbiter fire；c4331 才能看到 PFR 的 pipe state，c4332 才能看到 `entries_2`。
3. 本报告用不同 cache line 隔离端口仲裁；同 line merge、late-prefetch demotion、MSHR 满和 writeback-queue block 是不同实验，应分别测量。
4. 分析脚本固定为本次 Kunminghu V2 FST 已解析出的层次路径。换 config 或重新生成 RTL 后，应先用 `FstReader.get_matched_signals()` 重新解析 scope，再复用逻辑；脚本对找不到信号会显式报错而非虚构结果。
5. 在所列目标窗口内，关键仲裁和控制信号的 unknown mask 均为 0；无 X/Z 值参与上述 `valid/ready/fire` 判定。
