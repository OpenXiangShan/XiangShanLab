# 香山昆明湖执行 PREFETCH_R 指令的流程分析

## PREFETCH_R 指令介绍

### 这条指令是什么

`PREFETCH.R`（汇编写作 `prefetch.r offset(base)`）是 RISC-V Zicbop 软件预取扩展中的一条
**数据缓存读意图预取**指令。程序员用它告诉处理器：以 `base + offset` 为地址的缓存行很可能会在
稍后的程序执行中被读取，因此可以尽早启动地址翻译和数据缓存访问。

它没有面向软件可见的结果寄存器，也不会像普通 `ld` 一样把数据返回到通用寄存器。昆明湖的译码器
要求它的 `rd` 为 `x0`，并把该编码识别为 `prefetch_r` 类型的 LoadUnit 操作：
[DecodeUnit.scala:1102](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1102)。
因此，`PREFETCH.R` 的“R”表示对后续**读取**的意图，而不是“把数据读到某个寄存器”。

昆明湖同时区分三种软件预取操作：`PREFETCH.R` 走 DCache 的读意图预取命令 `M_PFR`，
`PREFETCH.W` 走写意图预取 `M_PFW`，而 `PREFETCH.I` 是不同的指令侧预取语义。对应的 LSU
操作编码定义在 [package.scala:559](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala#L559)，
DCache 命令定义在 [CacheConstants.scala:29](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/CacheConstants.scala#L29)。

### 这条指令会做什么

执行时，LoadUnit 先按普通 LSU 地址生成规则计算有效地址，即基址寄存器加上指令中编码的
S-type 12-bit offset；不过它会把该请求标记为预取。具体而言，DTLB 请求携带
`isPrefetch = true`，而发送到 DCache 的请求携带 `prf = true`、`prf_rd = true`，并由
`prf_rd` 选择 `M_PFR` 命令：[LoadUnit.scala:383](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L383)、
[LoadUnit.scala:406](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L406)、
[LoadUnit.scala:615](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L615)。

换言之，这条指令可让处理器提前进行 TLB 查询、DCache tag 查询，以及在未命中时的缓存行获取或
相关权限处理；但它不是普通架构数据加载：不会修改内存、不会向 GPR 写回数据，也不会把读取值提供
给后续数据相关指令。它仍作为一个独立 uop 经过调度、ROB 完成、Commit 和 Retire，以维持异常和
程序顺序的精确控制。

预取是**提示（hint）**而不是“数据必然已经在缓存中”的承诺。实际是否填入缓存、是否与已有请求合并，
以及后续 demand load 能否命中，取决于地址翻译结果、缓存状态、未命中队列和当时的资源竞争。

### 这条指令对程序执行有什么帮助

它的主要目的，是把随后普通 load 可能遇到的地址翻译、缓存未命中和内存访问延迟，提前与当前的
独立计算或其他访存重叠。对于顺序扫描、分块计算、指针追踪中“未来地址已知”的场景，软件可以在
真正消费数据之前预取对应缓存行；如果预取有足够的提前量且该行未被替换，后续 demand load 就更可能
直接命中较近的缓存层次，从而降低其可见停顿。

收益并非无条件成立。预取距离过短时，数据可能尚未来得及返回；距离过长时，缓存行可能在使用前被
替换。对不会访问的数据预取还会造成 cache pollution，并可能占用带宽、MSHR 或其他缓存资源。因此
应只在访问模式较可预测、确实会重用该缓存行、且程序有可用于隐藏延迟的工作时使用。

本文的演示正采用这一模式：先通过普通内存访问建立 cache 场景，再对 `demo_block + 32` 发出
`PREFETCH.R`，随后读取位于同一 64 B cache line 的 `demo_block[4]` 与 `demo_block[5]`。这样可以
在波形中区分“预取请求”与“后续普通读取请求”，并观察两者在 LoadUnit、DCache 和 MemBlock 中的关系。

## 香山昆明湖源代码分析

本节**只依据**昆明湖 Chisel 源码
`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala` 分析 `PREFETCH.R` 的后端生命周期；不使用波形
的周期、数值或性能结论。核心结论是：`PREFETCH.R` 在后端被实现为一种带特殊 `fuOpType` 的
**标量 LoadUnit uop**。它仍接受 Rename、ROB、Load Queue、调度、精确提交和退休的通用控制；
但地址翻译和 DCache 请求会被打上 `prefetch` 标记，并把 cache 命令改为 `M_PFR`。

### 1. 操作类型：`prefetch_r` 是 LoadUnit 的软件读预取 uop

[package.scala:559](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala#L559)
定义了 LSU operation 的三个软件预取编码：

```scala
// Zicbop software prefetch
// bit encoding: | prefetch 1 | 0 | prefetch type (2bit) |
def prefetch_i = "b1000".U
def prefetch_r = "b1001".U
def prefetch_w = "b1010".U

def isPrefetch(op: UInt): Bool =
  op(3) && (op(5, 4) === "b000".U) && (op(8, 7) === "b00".U)
```

`prefetch_r` 因而不是普通 `ld` 的同义别名，也不是 StoreUnit 的 store-prefetch 请求；它是一个
LSU operation code。之后所有特殊处理均由 `LSUOpType.isPrefetch`、
`fuOpType === LSUOpType.prefetch_r` 这两个判断触发。

### 2. Decode：识别编码、选择 LoadUnit、抑制 GPR 写回

[DecodeUnit.scala:1102](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1102)
先按固定编码识别 software prefetch：opcode 必须是 `0010011`、funct3 必须是 `110`，且 `rd` 必须是
`x0`；随后用指令中的 `RS2` 字段区分三类预取。

```scala
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") &&
  inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
val isPreI = isSoftPrefetch && inst.RS2 === 0.U(5.W)
```

对于 `isPreR`，同一文件把生成的 `DynInst` 改写为 LoadUnit uop：

```scala
}.elsewhen (isPreW || isPreR || isPreI) {
  decodedInst.selImm := SelImm.IMM_S
  decodedInst.fuType := FuType.ldu.U
  decodedInst.canRobCompress := false.B
}
...
io.deq.decodedInst.fuOpType := MuxCase(decodedInst.fuOpType, Seq(
  (isPreW || isPreR || isPreI) -> Mux1H(Seq(
    isPreW -> LSUOpType.prefetch_w,
    isPreR -> LSUOpType.prefetch_r,
    isPreI -> LSUOpType.prefetch_i,
  )),
))
```

这三项赋值定义了后端路径：

- `selImm = IMM_S`：基址寄存器与 S-type 形式的 12-bit offset 共同形成地址；
- `fuType = ldu`：指令流入 memory scheduler 的 load-address/LoadUnit 路径，不流入 StoreUnit；
- `canRobCompress = false`：它不与同 ROB entry 内相邻指令压缩，能以独立 uop 参与精确完成；
- `fuOpType = prefetch_r`：后续 LoadUnit 将它转换为读意图的预取请求。

同文件还统一收紧整数写回使能：

```scala
io.deq.decodedInst.rfWen := (decodedInst.ldest =/= 0.U) && decodedInst.rfWen
```

由于 software prefetch 的编码要求 `rd=x0`，即使该指令通过 LoadUnit 完成，也不会获得 GPR 目的
寄存器写回。这是它与普通整数 load 最根本的架构态差异之一。

### 3. Rename：保持普通 uop 的物理寄存器与 ROB 身份机制

PFR 没有独立的 Rename 模块分支；Decode 给出的 `fuType=ldu`、逻辑源寄存器和 `ldest=x0` 直接进入
通用 Rename。Rename 的职责是把逻辑源寄存器转为物理源、给 uop 分配/携带 ROB 身份，并对同拍更早
lane 的新目的寄存器做 bypass。

[Rename.scala:533](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L533)
给出目的物理寄存器和源旁路的核心写法：

```scala
io.out(0).bits.pdest := Mux(isMove(0), uops(0).psrc.head, uops(0).pdest)
...
io.out(i).bits.psrc(0) := io.out.take(i).map(_.bits.pdest)
  .zip(bypassCond(0)(i-1).asBools)
  .foldLeft(uops(i).psrc(0)) { (z, next) => Mux(next._2, next._1, z) }
```

PFR 的基址是整数源寄存器，故它需要正常的 `psrc(0)` 物理映射并等待该物理源 ready；但 `rd=x0`
使其没有可见的整数目的写回。Rename 并不会因为 `isPrefetch` 把它删除：它仍须分配 ROB 身份，
从而在异常、redirect 和提交顺序上与其他乱序 uop 一致。

### 4. Dispatch、ROB 分配和进入 memory issue queue

Dispatch 也没有针对 `prefetch_r` 的单独 bypass。它把 Rename fire 的 uop 写进 ROB；uop 的
`numWB` 由普通规则产生，single-step 才会强制为零：

[NewDispatch.scala:822](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L822)

```scala
for (i <- 0 until RenameWidth) {
  io.enqRob.needAlloc(i) := fromRename(i).valid
  io.enqRob.req(i).valid := fromRename(i).fire
  io.enqRob.req(i).bits := updatedUop(i)
  io.enqRob.req(i).bits.hasException := updatedUop(i).hasException || updatedUop(i).singleStep
  io.enqRob.req(i).bits.numWB := Mux(updatedUop(i).singleStep, 0.U, updatedUop(i).numWB)
}
```

所以 PFR 仍占一个 ROB 完成条件：只有 LoadUnit 产生相应的完成/writeback 后，ROB 的 `commit_w`
才会满足。其 `rd=x0` 只意味着完成时不写寄存器，**不意味着**可以在 Dispatch 阶段丢弃。

Dispatch 的 backpressure 同样走一般机制。例如：

```scala
thisCanActualOut := VecInit((0 until RenameWidth).map(i =>
  !blockedByWaitForward(i) && notBlockedByPrevious(i) && io.enqRob.canAccept))
val stall_rob = hasValidInstr && !io.enqRob.canAccept
```

因此 PFR 会受到 ROB 接受能力、前序 `waitForward/blockBackward` 和 issue-queue 资源的约束；源码没有
为它提供“忽略 ROB 满”或“无条件发射”的特殊规则。

[CtrlBlock.scala:751](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L751)
把 Dispatch 的输出按功能类别接到各 issue block：

```scala
val toIssueBlockUops = Seq(
  io.toIssueBlock.intUops,
  io.toIssueBlock.fpUops,
  io.toIssueBlock.vfUops,
  io.toIssueBlock.memUops
).flatten
toIssueBlockUops.zip(dispatch.io.toIssueQueues).map(x => x._1 <> x._2)
```

由于 Decode 已设为 `FuType.ldu`，PFR 被放入 `memUops`，由 memory scheduler 选择可执行的
load-address 端口。这里的 `<>` 是 Decoupled 连接：uop 保持 `valid`，直到 issue queue 用 `ready`
接收；后续也由 scheduler 在物理源操作数就绪和执行端口可用时发射。

### 5. MemBlock：将 load-address issue 端口接到 LoadUnit 和 DCache load port

[MemBlock.scala:854](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L854)
展示了 scalar LoadUnit 的直连关系：

```scala
loadUnits(i).io.redirect <> redirect

// get input form dispatch
loadUnits(i).io.ldin <> io.ooo_to_mem.issueLda(i)
loadUnits(i).io.robDeqIdx <> io.ooo_to_mem.lsqio.pendingPtr
...
// dcache access
loadUnits(i).io.dcache <> dcache.io.lsu.load(i)
```

因此 memory scheduler 发出的 PFR 不需要穿过 StoreUnit、Sbuffer 或 AtomicsUnit；它以
`issueLda(i)` 形式进入某个 `LoadUnit(i)`。MemBlock 还将该 LoadUnit 的 DCache request ready
回传：

```scala
loadUnits(i).io.dcache.req.ready := dcache.io.lsu.load(i).req.ready
```

这个 ready 参与 LoadUnit S0 的接收条件，因而 DCache 反压可以自然阻止新的 PFR 进入执行路径。

### 6. LoadUnit：地址生成、DTLB 预取属性和 `M_PFR` 生成

`LoadUnit` 是 PFR 的专用实现重点。首先，来自整数 issue 的输入转换为内部 `FlowSource`。
[LoadUnit.scala:615](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L615)
定义地址和预取控制位：

```scala
def fromIntIssueSource(src: MemExuInput): FlowSource = {
  val out = WireInit(0.U.asTypeOf(new FlowSource))
  val addr = io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), VAddrBits)
  out.mask := genVWmask(addr, src.uop.fuOpType(1,0))
  out.uop := src.uop
  ...
  out.prf    := LSUOpType.isPrefetch(src.uop.fuOpType)
  out.prf_rd := src.uop.fuOpType === LSUOpType.prefetch_r
  out.prf_wr := src.uop.fuOpType === LSUOpType.prefetch_w
  out.prf_i  := src.uop.fuOpType === LSUOpType.prefetch_i
  ...
}
```

这里的 `addr` 是基址加符号扩展 offset；PFR 的 `prf=true`、`prf_rd=true`、`prf_wr=false`、
`prf_i=false`。注意 `prefetch_i` 也进入该枚举体系，但后文 `!prf_i` 会阻止它发 DCache 请求；这
避免把 instruction prefetch 误当成 data-cache 读请求。

#### 6.1 DTLB 请求

[LoadUnit.scala:360](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L360)
规定 LoadUnit 只有在 S0 输入来源有效且 DCache 可接受时才推进；对整数 issue 来源，`int_iss_idx`
包含在 `s0_tlb_valid` 的选择中：

```scala
s0_tlb_valid := (
  s0_src_valid_vec(mab_idx) || ... ||
  s0_src_valid_vec(int_iss_idx) ||
  s0_src_valid_vec(l2l_fwd_idx)
) && io.dcache.req.ready
```

[LoadUnit.scala:383](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L383)
将 PFR 的内部属性携带到 DTLB：

```scala
io.tlb.req.valid := s0_tlb_valid
io.tlb.req.bits.cmd := Mux(s0_sel_src.prf,
  Mux(s0_sel_src.prf_wr, TlbCmd.write, TlbCmd.read), TlbCmd.read)
io.tlb.req.bits.isPrefetch := s0_sel_src.prf
io.tlb.req.bits.vaddr := s0_tlb_vaddr
io.tlb.req.bits.memidx.is_ld := true.B
io.tlb.req.bits.memidx.is_st := false.B
io.tlb.req.bits.memidx.idx := s0_sel_src.uop.lqIdx.value
io.tlb.req.bits.debug.robIdx := s0_sel_src.uop.robIdx
```

因此 PFR 的翻译命令为 `TlbCmd.read`，并通过 `isPrefetch=true` 明确通知 DTLB；它仍保留 load-side
的 LQ index 和 ROB index，支持调试、异常/flush 判定和与普通 load 一致的顺序控制。它不会设置
store memory index，故没有 StoreUnit 所需的 store 语义。

#### 6.2 DCache 请求

紧随其后的 [LoadUnit.scala:406](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L406)
是 PFR 的核心 cache 映射：

```scala
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd,
  MemoryOpConstants.M_PFR,
  Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD))
io.dcache.req.bits.vaddr := s0_dcache_vaddr
io.dcache.req.bits.data := DontCare
io.dcache.req.bits.instrtype := Mux(s0_sel_src.prf,
  DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
io.dcache.req.bits.debug_robIdx := s0_sel_src.uop.robIdx.value
io.dcache.req.bits.lqIdx := s0_sel_src.uop.lqIdx
```

对于 PFR，条件化简为：`valid = s0_valid && !s0_nc_with_data`，命令为 `M_PFR`，source type 为
`DCACHE_PREFETCH_SOURCE`，store data 被置为 `DontCare`。这既把它与普通 `M_XRD` 区分，又把它与
写意图的 `M_PFW` 区分；但地址、ROB/LQ 调试身份仍随请求进入 DCache。

### 7. DCache 的特殊支持

#### 7.1 命令语义

[CacheConstants.scala:24](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/CacheConstants.scala#L24)
定义 cache 命令和权限辅助函数：

```scala
def M_XRD = "b00000".U // int load
def M_XWR = "b00001".U // int store
def M_PFR = "b00010".U // prefetch with intent to read
def M_PFW = "b00011".U // prefetch with intent to write

def isPrefetch(cmd: UInt) = cmd === M_PFR || cmd === M_PFW
def isRead(cmd: UInt) = cmd === M_XRD || cmd === M_XLR || cmd === M_XSC || isAMO(cmd)
def isWriteIntent(cmd: UInt) = isWrite(cmd) || cmd === M_PFW || cmd === M_XLR
```

`M_PFR` 被单独编码为 read-intent prefetch；尤其 `isWriteIntent` 不包含 `M_PFR`，而包含
`M_PFW`。因此 cache coherence/permission 决策可以让读预取与写预取获得不同的权限需求。

#### 7.2 LoadPipe：允许预取访问 tag，但不读取数据阵列

[LoadPipe.scala:112](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala#L112)
的 S0 明确限制 LoadPipe 只接收普通 load 与两类软件预取：

```scala
io.lsu.req.ready := (!io.nack && not_nacked_ready) || (io.nack && nacked_ready)
io.meta_read.valid := io.lsu.req.fire && !io.nack
io.tag_read.valid := io.lsu.req.fire && !io.nack
...
assert(RegNext(!(s0_valid &&
  (s0_req.cmd =/= MemoryOpConstants.M_XRD &&
   s0_req.cmd =/= MemoryOpConstants.M_PFR &&
   s0_req.cmd =/= MemoryOpConstants.M_PFW))),
  "LoadPipe only accepts load req / softprefetch read or write!")
```

这表明 PFR 不是绕过 DCache 的旁路请求：它和普通 load 一样进行 metadata/tag read、bank/set 选择和
命中判断。进入 S1 后，LoadPipe 通过 `instrtype` 得到预取属性：

```scala
val s1_is_prefetch = s1_req.instrtype === DCACHE_PREFETCH_SOURCE.U
```

[LoadPipe.scala:305](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala#L305)
中的两个分支体现其特殊性：

```scala
val s1_hit = s1_tag_match_dup_dc && s1_has_permission &&
  s1_hit_coh === s1_new_hit_coh
val s1_will_send_miss_req = s1_valid && !s1_nack && !s1_hit

io.banked_data_read.valid := s1_fire && !s1_nack && !s1_is_prefetch &&
  !io.lsu.s1_kill_data_read
```

PFR 仍参与 tag/coherence permission 判断；未命中时仍满足 `s1_will_send_miss_req`，会走 miss 请求。
但是 `s1_is_prefetch` 令 `banked_data_read.valid` 为假：预取的目的只是让 line/permission 提前进入
cache，而不是把命中数据送回架构寄存器。这正是“预取”和普通 `ld` 的关键 cache 实现差异。

S3 也继续保留该属性：

```scala
val s3_is_prefetch = s3_req_instrtype === DCACHE_PREFETCH_SOURCE.U
io.access_flag_write.valid := s3_valid && s3_hit && !s3_is_prefetch
```

因此软件预取命中不会当作普通需求 load 去更新 access flag；普通 load 命中已预取的 line 时，才由
非预取访问清除/更新相应预取和访问标记。这允许 DCache 区分“预取进入的 line”和“后来真正使用的
line”。

#### 7.3 MissQueue：以 prefetch 身份记录未命中事务

[MissQueue.scala:70](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L70)
在 miss request bundle 中给出更细分类：

```scala
def isFromPrefetch = source >= DCACHE_PREFETCH_SOURCE.U
def isPrefetchWrite = source === DCACHE_PREFETCH_SOURCE.U &&
  cmd === MemoryOpConstants.M_PFW
def isPrefetchRead = source === DCACHE_PREFETCH_SOURCE.U &&
  cmd === MemoryOpConstants.M_PFR
```

[MissQueue.scala:470](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L470)
进一步用 command 判定 incoming miss 是否为预取：

```scala
val miss_req_pipe_reg_bits = io.miss_req_pipe_reg.req
val input_req_is_prefetch = isPrefetch(miss_req_pipe_reg_bits.cmd)
...
io.perf_pending_prefetch := req_valid && prefetch && !secondary_fired
io.perf_pending_normal   := req_valid && (!prefetch || secondary_fired)
```

所以 PFR miss 不是与普通 miss 混为同一不可辨别事务：MissQueue 保存 `prefetch` 状态并提供独立的
pending 性能统计；同时仍可通过 primary/secondary accept 与已有 miss 合并。源码这部分说明了 cache
对 PFR 的特殊支持是“复用 load miss 基础设施、但携带预取类型/权限/统计”，而不是另建一个完全
脱离 DCache 的 prefetch pipeline。

### 8. LoadUnit 完成、writeback 和 ROB Commit/Retire

PFR 虽不需要普通 load 数据，仍必须完成 LDU pipeline，使 ROB 能释放该 entry。LoadUnit S3 使用
通用 `ldout` 返回完成信息；[LoadUnit.scala:1768](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L1768)
的关键赋值为：

```scala
val s3_ldout_valid = s3_mmio_req.valid ||
  s3_out.valid && RegNext(!s2_out.isvec && !s2_out.isFrmMisAlignBuf)
io.ldout.valid := s3_ldout_valid
io.ldout.bits := s3_ld_wb_meta
io.ldout.bits.data := Mux(s3_valid, s3_ld_data_frm_pipe(0), s3_ld_data_frm_mmio)
io.ldout.bits.uop.rfWen := s3_rfWen &&
  !io.ldout.bits.uop.exceptionVec.asUInt.orR
io.ldout.bits.isFromLoadUnit := true.B
io.ldout.bits.uop.fuType := FuType.ldu.U
```

对 PFR，Decode 已保证 `rd=x0`，所以即便它通过 `ldout` 返回至 backend，最终 `rfWen` 不会形成
架构 GPR 更新。`ldout.valid` 的意义是“该 ROB uop 已完成/可写回”，不是“存在程序可读取的 load
data”。若发生 TLB miss、DCache nack、forward failure、RAR/RAW violation 等，LoadUnit 前一阶段会
把原因写入 `rep_info` 并走 replay 控制；成功路径才抵达上面的 S3 writeback。

ROB 的提交规则与指令种类无关，但 PFR 必须满足它。 [Rob.scala:780](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L780)
要求 ROB 处于 idle、无 blockCommit，并且该 entry 同时 `commit_v` 和 `commit_w`：

```scala
io.commits.isCommit := state === s_idle && !blockCommit
val commit_vDeqGroup = VecInit(robDeqGroup.map(_.commit_v))
val commit_wDeqGroup = VecInit(robDeqGroup.map(_.commit_w))
...
commitValidThisLine(i) := commit_vDeqGroup(i) && commit_wDeqGroup(i) &&
  !isBlocked && !isBlockedByOlder && !hasCommitted(i)
```

PFR 的 writeback 负责满足其中的完成侧 `commit_w`；它仍会受更老异常、interrupt、redirect、
`needFlush` 和 commit width 顺序限制。成功 commit 后，ROB 将 load-class commit 数交给 LSQ：

```scala
val ldCommitVec = VecInit((0 until CommitWidth).map(i =>
  io.commits.commitValid(i) && io.commits.info(i).commitType === CommitType.LOAD))
io.lsq.lcommit := RegNext(Mux(io.commits.isCommit, PopCount(ldCommitVec), 0.U))
```

最后，[Rob.scala:1248](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L1248)
把 commit 数转换为退休计数：

```scala
val isCommit = io.commits.isCommit
val isCommitReg = GatedValidRegNext(io.commits.isCommit)
val trueCommitCnt = RegEnable(..., isCommit) +& fuseCommitCnt
val retireCounter = Mux(isCommitReg, trueCommitCnt, 0.U)
io.csr.perfinfo.retiredInstr := retireCounter
```

同时，difftest/架构写回端再一次以 `rfWen && ldest=/=0` 过滤 GPR 写：

```scala
difftest.valid := io.commits.commitValid(i) && io.commits.isCommit
difftest.rfwen := io.commits.commitValid(i) && commitInfo.rfWen &&
  basicDebug.ldest =/= 0.U
```

因此 PFR 的完整“完成 -> commit -> retire”语义是：它作为一个已完成的 load-class uop 从 ROB 退休，
可推进指令退休计数与 LSQ load commit；但由于 `rd=x0`，不会产生 GPR 写回数据。cache line 的填充或
permission 获取是它的微架构效果，架构态效果仅是这条无目的寄存器指令按顺序退休。

### 9. 与 StoreUnit、Sbuffer 及 ICache 软件预取的边界

PFR 在 Decode 被固定到 `FuType.ldu`，MemBlock 又固定把 `issueLda` 接到 `LoadUnit`，故它不经过
StoreUnit 或 Sbuffer 的 store-data/commit 通路。Sbuffer 中确有 `store_prefetch` 接口，但它服务的是
store pipeline/hardware store prefetch：

[Sbuffer.scala:203](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L203)

```scala
val store_prefetch = Vec(StorePipelineWidth, DecoupledIO(new StorePrefetchReq))
```

[Sbuffer.scala:402](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L402)
由 committed store 或 store prefetcher 触发：

```scala
io.store_prefetch(i).valid := prefetcher.io.prefetch_req(i).valid ||
  (io.in(i).fire && io.in(i).bits.vecValid && io.in(i).bits.prefetch)
```

这与 LoadUnit 产生 `M_PFR` 的 PFR 无关。类似地，frontend `IPrefetch`/`ICache` 的
`isSoftPrefetch` 是 instruction-cache 预取路径；PFR 在源码中明确被 Decode 为 `ldu` 并由
LoadUnit 的 `!prf_i` 分支排除 `prefetch_i`，所以不能把 PFR 的 DCache 请求误归入 ICache
software-prefetch 实现。

综上，昆明湖对 `PREFETCH.R` 的专门支持集中在三个点：**Decode 的 PFR 操作码识别，LoadUnit 的
`prf/prf_rd` 到 DTLB/DCache 控制转换，以及 DCache LoadPipe/MissQueue 对 `M_PFR` 的 tag、permission、
miss 和预取状态处理。** Rename、Dispatch、issue、writeback、ROB commit/retire 则复用普通标量
load uop 的乱序和精确状态机制。

## PREFETCH_R 演示程序

演示程序位于
`/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_r/prefetch_r.c`，构建文件为
`/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_r/Makefile`。它使用一个 64 B 对齐、恰好
64 B 大小的 `demo_block`；因而 `demo_block[0]` 到 `demo_block[7]` 位于同一条缓存行中。

### C 代码

```c
#include <klib.h>
#include <stdint.h>

#define CACHE_LINE_BYTES 64
#define WORD_COUNT (CACHE_LINE_BYTES / sizeof(uint64_t))

static volatile uint64_t demo_block[WORD_COUNT]
    __attribute__((aligned(CACHE_LINE_BYTES)));

static inline void prefetch_r(const void *base_address) {
  __asm__ volatile(
      "mv t0, %0\n\t"
      ".word 0x0212e013"
      :
      : "r"(base_address)
      : "t0", "memory");
}

static uint64_t initialize_and_read_block(uint64_t seed) {
  uint64_t checksum = 0;

  for (int index = 0; index < WORD_COUNT; index++) {
    demo_block[index] = seed + (uint64_t)index;
    checksum += demo_block[index];
  }
  return checksum;
}

static uint64_t read_prefetched_word(void) {
  uint64_t value = demo_block[4];

  value += demo_block[5];
  return value;
}

int main(void) {
  const uint64_t initial_seed = 0x1122334455667700ULL;
  const uint64_t before_checksum = initialize_and_read_block(initial_seed);
  const uint64_t *prefetch_base = (const uint64_t *)demo_block;

  printf("PREFETCH.R demonstration starts\n");
  printf("target cache line: 0x%lx, prefetch address: 0x%lx\n",
         (unsigned long)demo_block, (unsigned long)&demo_block[4]);
  printf("before prefetch.r: checksum=0x%lx word[4]=0x%lx\n",
         (unsigned long)before_checksum, (unsigned long)demo_block[4]);

  prefetch_r(prefetch_base);
  printf("prefetch.r issued for base + 32 bytes\n");

  const uint64_t post_prefetch_value = read_prefetched_word();
  printf("after prefetch.r: word[4] + word[5] = 0x%lx\n",
         (unsigned long)post_prefetch_value);
  printf("PREFETCH.R demonstration ends\n");

  return 0;
}
```

`volatile` 使初始化、预取前读取和预取后读取都保留为实际内存访问，避免编译器把它们完全折叠为
常量计算；内联汇编的 `"memory"` clobber 则防止编译器把普通内存访问跨越 `prefetch_r()` 任意重排。
汇编先把 C 形参移到 `t0/x5`，再发出固定指令字；这样 raw `.word` 的 `rs1` 字段与 `t0` 一致。

### 指令编码与反汇编

`0x0212e013` 的字段满足昆明湖 DecodeUnit 对 `PREFETCH.R` 的识别条件：

| 字段 | 值 | 作用 |
| --- | --- | --- |
| opcode `[6:0]` | `0x13` (`0010011`) | software-prefetch 指令大类。 |
| rd `[11:7]` | `x0` | PFR 没有架构目的寄存器。 |
| funct3 `[14:12]` | `110` | software-prefetch 固定 funct3。 |
| rs1 `[19:15]` | `x5` (`t0`) | 提供预取基址。 |
| rs2 `[24:20]` | `1` | Decode 将它判定为 `prefetch_r`。 |
| S-type offset | `0x20` | 预取地址为 `t0 + 32`。 |

对生成的 ELF 执行：

```bash
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/nexus-am/apps/prefetch_r
make ARCH=riscv64-xs
riscv64-linux-gnu-objdump -d build/prefetch_r-riscv64-xs.elf
```

得到的关键反汇编如下：

```text
80000158: 20976733           sh3add  a4,a4,s1
8000015c: 00c786b3           add     a3,a5,a2
80000160: e314               sd      a3,0(a4)
80000162: 6318               ld      a4,0(a4)
80000164: 0785               addi    a5,a5,1
80000166: 943a               add     s0,s0,a4
80000168: feb796e3           bne     a5,a1,80000154 <main+0x2a>
...
80000194: 7090               ld      a2,32(s1)
...
800001a4: 82a6               mv      t0,s1
800001a6: 0212e013           .word   0x0212e013
...
800001b6: 708c               ld      a1,32(s1)
800001b8: 749c               ld      a5,40(s1)
800001ba: ...                # add a1, a1, a5 并调用 printf
```

GNU `objdump` 不认识该自定义/扩展 mnemonic，故把它显示为 `.word 0x0212e013`；这不是非法指令。
昆明湖的 DecodeUnit 会根据上述字段将它识别为 `prefetch.r`。其中 `s1` 保存 `demo_block` 的首地址，
`mv t0,s1` 之后执行 `.word`，所以目标地址为 `s1 + 0x20`，即 `demo_block[4]`。

### 场景设计与执行顺序

该程序刻意将预取前后的访问限制在同一条缓存行内，避免“预取的是 A，随后访问的是 B”的歧义：

1. **建立目标缓存行。** `demo_block` 以 64 B 对齐，数组大小也为 64 B；循环先对每个元素执行
   store，再立即 load 并累加 checksum。反汇编中的 `sd a3,0(a4)` 与 `ld a4,0(a4)` 就是该预取前
   的实际数据缓存访问。
2. **预取前可见访问。** 在发射 PFR 之前，`printf` 读取并打印 `demo_block[4]`；反汇编的
   `ld a2,32(s1)` 对应该读取。由此可在波形中同时观察普通 load 与后续 `M_PFR` 的区别。
3. **发射读意图预取。** `prefetch_r(prefetch_base)` 先把缓存行首地址写入 `t0`，执行
   `prefetch.r 0x20(t0)`。它请求地址 `demo_block + 32 B`，即缓存行中第 5 个 64-bit word，
   对应 `demo_block[4]`。
4. **预取后再次使用该行。** PFR 之后先输出提示信息，再读取 `demo_block[4]` 和
   `demo_block[5]` 并求和；反汇编中的 `ld a1,32(s1)` 与 `ld a5,40(s1)` 是这两个实际需求 load。
   因为 offset 32 和 40 都落在同一条从 `demo_block` 起始的 64 B 缓存行内，后续访问与预取目标
   共享 cache line。
5. **检查程序功能。** 程序打印预取前 checksum、预取地址和预取后两字之和。PFR 是提示性操作，
   不改变 `demo_block` 内容；因此预取后的读值应当与初始化值一致。仿真运行至 `GOOD TRAP` 表明
   程序按预期返回。

该场景的目标不是用 UART/`printf` 时间直接量化性能收益，而是构造一个清楚的功能链：先产生真实
cache 访存，再用 PFR 对同一缓存行的中部地址提出读意图预取，最后用同一缓存行的正常 loads 验证
程序数据语义未被改变。性能收益、命中/未命中和请求完成细节应以本章之后的波形分析为准。

## 波形图分析

### 方法、对象与结论

本节使用 wavekit 开源仓库 `/home/yanyusong/wavekit` 的 `FstReader` 解析并查询波形文件
`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-16-39-21.fst`。核心层级为
`TOP.SimTop.cpu.l_soc.core_with_l2.core`，时钟为 `TOP.clock`；以下所有数值均由 wavekit
以**上升沿**采样，cycle 为绝对时钟周期、time 为 FST 仿真时间。

跟踪对象是演示程序中 PC `0x800001a6` 的 32-bit 指令 `0x0212e013`。反汇编显示它位于
`mv t0, s1` 之后，编码的 `rs1=t0`、立即数为 `0x20`，故软件语义为
`prefetch.r 0x20(t0)`。目标周期中 `t0` 的实际源操作数为 `0x80001740`，因此目标虚拟地址
为 `0x80001760`；这与程序中打印的 prefetch address 一致。

**结论：** 该指令确实走入 LoadUnit，而不是 StoreUnit；LoadUnit 在 cycle 25671 产生
DTLB 预取访问和 DCache `M_PFR`（读意图预取）请求，两个 Decoupled 边界均握手成功。
目标 uop 在 cycle 25674 经 LDA writeback 返回，`rfwen=0`、`data=0`、`replay=0`：它不写
通用寄存器，也没有 replay。波形中未发现由这条 uop 产生的 rename/ROB/LoadUnit redirect 或
CtrlBlock flush。

### 全局时间线

`fire = valid && ready`。IBuffer 的 `io_out` 和 CtrlBlock 的 `cfVec` 是逐 lane 的 `Valid`
输出，未随该 lane 一起导出 ready；因此对它们只能记录可见性，不能把连续 `valid=1` 擅自解释
为 `valid && !ready` 停顿。其后的 Rename、Dispatch、issue、LoadUnit 和 writeback 均为有
ready 的接口。

| cycle | time | 边界/模块 | 目标身份与关键值 | valid/ready/fire 与结果 |
| ---: | ---: | --- | --- | --- |
| 25635–25663 | 51270–51326 | IBuffer `io_out_4`、CtrlBlock `io_frontend_cfVec_4` | PC=`0x800001a6`，instr=`0x0212e013`，FTQ=`22`，offset=`1` | 两处 lane-4 `valid=1`，目标指令可见。|
| 25663 | 51326 | DecodeUnit lane 4 | `isSoftPrefetch=1`，`isPreR=1`，`isPreW=0`，`isPreI=0` | 确认为 `PREFETCH.R`，而非 `PREFETCH.W/I`。|
| 25664 | 51328 | Rename `io_in_4` | PC=`0x800001a6`，instr=`0x0212e013`，`fuOp=0x9`，逻辑源寄存器=`x5`，逻辑目的=`x0` | `1/1/1`；Decode -> Rename 输入无反压。|
| 25664 | 51328 | Rename `io_out_4` | PC/instr 不变，ROB=`12`，物理源 `psrc0=39`，`pdest=0` | `1/1/1`；为 `t0` 建立物理源标签，不分配有效目的物理寄存器。|
| 25665 | 51330 | CtrlBlock -> memory issue queue，lane 6 | ROB=`12`，LQ=`48`，SQ flag=`0`，`fuOp=0x9`，`psrc0=39`，`pdest=0` | `1/1/1`；Dispatch 成功入 memory scheduler。|
| 25671 | 51342 | Scheduler -> `io_mem_issueLda_1` | PC=`0x800001a6`，ROB=`12`，LQ=`48`，SQ=`36`（无效字段），src0=`0x80001740` | `1/1/1`；选中 LDA port 1 发射。|
| 25671 | 51342 | `inner_LoadUnit_1.io_ldin` | 同一 PC/ROB/LQ/fuOp；src0=`0x80001740` | `1/1/1`；MemBlock 将该 issue 端口直接交给 LoadUnit 1。|
| 25671 | 51342 | LoadUnit 1 -> DTLB | `tlb_req.valid=1`，`isPrefetch=1`，vaddr=`0x80001760` | 不以普通 load 身份翻译；携带预取属性。|
| 25671 | 51342 | LoadUnit 1 -> DCache | `cmd=2`，vaddr=`0x80001760`，`instrtype=3` | `valid=1`、`ready=1`、`fire=1`；`cmd=2` 是 `M_PFR`。|
| 25674 | 51348 | `io_mem_writebackLda_1` | ROB=`12`，LQ=`48`，`data=0`，`rfwen=0`，`replay=0` | `valid=1`、`ready=1`、`fire=1`；完成控制性 writeback，无 GPR 更新。|

从 Dispatch fire（25665）到 issue fire（25671）相隔 6 cycles。当前 dump 没有导出能把
ROB=12 唯一关联到 issue-queue entry 的 entry-valid、select 原因和 ready-generation 内部信号，
所以只能报告它在 scheduler 中等待了 6 cycles，**不能**把这 6 cycles 归因于 cache、TLB、
ROB 满或操作数未就绪。进入 LoadUnit 后，DTLB/DCache 请求在同一采样周期被接收，没有可见
`valid=1 && ready=0` 气泡。

### 1. Frontend、FTQ 与 IBuffer

wavekit 在 `TOP.SimTop.cpu.l_soc.core_with_l2.core.frontend.inner_ibuffer.io_out_4_*` 与
`TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.io_frontend_cfVec_4_*` 上同时找到了
目标：PC、指令字、FTQ pointer 和 offset 分别为 `0x800001a6`、`0x0212e013`、`22`、`1`。
这建立了从 frontend 到 CtrlBlock 的 PC 锚点；反汇编、波形指令字和程序地址三者一致，未发现
大小端或 radix 不一致。

IBuffer 的 packet 输入 `io_in_valid` 在该窗口保持为 1，而全局 `io_in_ready` 在 25664、以及
25666–25670 为 1。它不是与 `io_out_4` 一一对应的 per-lane ready，故不能用它反推该 uop 的
逐 lane fire。可严格确认的是：目标在 25663 到达 Decoder；此处没有波形证据表明目标被 redirect
或 flush 掉。

[IBuffer.scala:35](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/IBuffer.scala#L35)
定义 IBuffer 的输入输出接口；该模块保存取回的控制流/指令信息并以多 lane 输出给后端。对本指令，
producer 是 IBuffer lane 4，consumer 是 CtrlBlock 的 frontend `cfVec_4` 与 Decode lane 4。

### 2. Decode：软预取分类和 uop 属性

cycle 25663，`decode.decoders_4` 的 `isSoftPrefetch=1`、`isPreR=1`，而 `isPreW/isPreI=0`。
这是一条最直接的功能证据：不是仅凭指令字推测，而是 DecodeUnit 中实际判定网络已经给出 PFR
分支。译码输出保留指令的基址来源，且 `rd=x0` 表明此指令无架构目的寄存器。

[DecodeUnit.scala:1102](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1102)
的逻辑如下：

```scala
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") &&
  inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
val isPreI = isSoftPrefetch && inst.RS2 === 0.U(5.W)
...
}.elsewhen (isPreW || isPreR || isPreI) {
  decodedInst.selImm := SelImm.IMM_S
  decodedInst.fuType := FuType.ldu.U
  decodedInst.canRobCompress := false.B
}
...
isPreR -> LSUOpType.prefetch_r
```

因此，`isPreR=1` 的来源是指令 immediate 内的 `RS2=1` 字段；它把 Decode 输出的功能单元类型
改写成 LoadUnit (`FuType.ldu`)，立即数选择为 S-type 拼接方式，并将 `fuOpType` 设为
`LSUOpType.prefetch_r`。后续波形中看到的 `fuOp=0x9` 即为这一枚举在已生成 RTL 中的数值表示；
其 producer 是 DecodeUnit，consumer 是 Rename、Dispatch、issue 和 LoadUnit。

### 3. Rename：逻辑寄存器到物理寄存器、ROB 身份建立

cycle 25664 的 `rename.io_in_4` 有 `valid=ready=1`：

- 输入 `pc=0x800001a6`、`instr=0x0212e013`、`fuOp=0x9`；
- `lsrc0=5`，对应指令实际使用的 `t0/x5`；
- `ldest=0`，对应 `rd=x0`，所以不会产生可提交的整数寄存器写。

同一 cycle 的 `rename.io_out_4` 也 `fire`，其 `psrc0=39`、`pdest=0`、ROB pointer=`12`。这说明
RenameTable 已把逻辑 `x5` 映射成物理寄存器 39；`pdest=0` 配合后续 `rfwen=0` 与指令无写回语义
一致。此后所有后端跟踪都采用 ROB=12，而不是只依赖 PC：Dispatch、issue、LoadUnit 和 writeback
都保持该 ROB 编号。

[Rename.scala:533](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L533)
显示 Rename 对 `pdest/psrc` 的生成和前序 lane bypass；本指令的实际观测值表明其只消费 `psrc0`，
没有新整数目的寄存器。`io_in/out` 的 `ready=1` 是对 Rename 分配端和后续 Dispatch 接受端没有
阻塞的波形证明。

### 4. Dispatch、ROB 与 memory scheduler

cycle 25665，`io_toIssueBlock_memUops_6` 发生 fire：

- `valid=1`、`ready=1`；
- PC/instr 仍为 `0x800001a6/0x0212e013`；
- `robIdx.flag=1,value=12`，`lqIdx.flag=1,value=48`；
- `sqIdx.flag=0`，其显示 value 36 只是无效 bundle 的残留值，不应解释为 Store Queue 分配；
- `psrc0=39`、`pdest=0`、`fuOp=0x9` 与 Rename 输出一致。

`lqIdx.flag=1` 说明它使用 load-side 的排序/追踪资源；但是它没有 store queue 身份。`canRobCompress`
在 decode 被强制为 false，故其以独立 ROB uop 参与精确异常/提交顺序，而不是与邻近指令压缩。

[CtrlBlock.scala:751](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L751)
将 Dispatch 的 `toIssueQueues` 与 `toIssueBlock.memUops` 用 `<>` 连接；[CtrlBlock.scala:876](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L876)
声明其为 `Vec[DecoupledIO[DynInst]]`。这解释了此处的 ready/valid：producer 是 Dispatch，consumer
是 memory issue queue，`ready=1` 所以 ROB=12 的 uop 在 25665 被接收。

该 dump 的 ROB `io_commits_info_*` 和 difftest endpoint 虽有 PC/instruction 字段，但用上、下降沿
扫描 cycle 25660–25820 的 eight commit lanes 时均未找到 PC `0x800001a6` 的有效记录；这是
`--no-diff` 波形中该 commit 可观测性的限制，不能把它误报为“未提交”。相反，后续的
LDA writeback 成功、没有 flush/replay，并且整机最终 `GOOD TRAP`，说明该 uop 没有因异常被杀掉。

### 5. Issue：从 scheduler 到 Load Address port

目标从 Dispatch 入队后，在 cycle 25671 由 `backend.io_mem_issueLda_1` 发射：

```text
valid=1 ready=1 fire=1
PC=0x800001a6  ROB=12  LQ=48  fuOp=0x9
src0=0x80001740
```

此处 `src0` 已是物理寄存器 39 读出的数据，即程序在前一条 `mv t0,s1` 中建立的缓存行首地址。
接口的 consumer 是 `MemBlock` 中的 LoadUnit 1。波形中 `sqIdx` 数值虽显示为 36，但本指令的 SQ
flag 在 Dispatch 为 0，故它不是 StoreUnit 操作；无效字段不能作为 store 身份。

### 6. MemBlock 与 LoadUnit 的特殊处理

[MemBlock.scala:854](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L854)
明确把每个 load-address issue 端口连到对应 LoadUnit：

```scala
loadUnits(i).io.redirect <> redirect
loadUnits(i).io.ldin <> io.ooo_to_mem.issueLda(i)
...
loadUnits(i).io.dcache <> dcache.io.lsu.load(i)
loadUnits(i).io.dcache.req.ready := dcache.io.lsu.load(i).req.ready
```

这与 cycle 25671 的同周期观察一致：`io_mem_issueLda_1` 和 `inner_LoadUnit_1.io_ldin` 同时为
`valid=ready=1`，PC/ROB/LQ/fuOp 都保持不变。MemBlock 没有把它转发到 hybrid/store pipeline，
而是连接到普通 scalar LoadUnit 1 和 `dcache.io.lsu.load(1)`。

[LoadUnit.scala:615](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L615)
是这条指令最关键的控制转换：

```scala
val addr = io.ldin.bits.src(0) + SignExt(io.ldin.bits.uop.imm(11, 0), VAddrBits)
out.prf    := LSUOpType.isPrefetch(src.uop.fuOpType)
out.prf_rd := src.uop.fuOpType === LSUOpType.prefetch_r
out.prf_wr := src.uop.fuOpType === LSUOpType.prefetch_w
out.prf_i  := src.uop.fuOpType === LSUOpType.prefetch_i
```

对 ROB=12，wavekit 的 `src0=0x80001740` 与 immediate `0x20` 得到 `0x80001760`。`fuOp=0x9`
使 `prf/prf_rd` 为真、`prf_wr/prf_i` 为假；因此这是读意图数据预取，而不是 instruction prefetch，
也不是写意图 prefetch。

接着，[LoadUnit.scala:383](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L383)
向 DTLB 输出：

```scala
io.tlb.req.valid := s0_tlb_valid
io.tlb.req.bits.cmd := Mux(s0_sel_src.prf,
  Mux(s0_sel_src.prf_wr, TlbCmd.write, TlbCmd.read), TlbCmd.read)
io.tlb.req.bits.isPrefetch := s0_sel_src.prf
io.tlb.req.bits.vaddr := s0_tlb_vaddr
```

波形对应值为 `tlb_req.valid=1`、`isPrefetch=1`、`vaddr=0x80001760`。其 producer 是 LoadUnit 的
S0 选择结果，consumer 是 LoadUnit 的 DTLB 请求端；读请求被显式标记为 prefetch，供翻译/性能路径
与普通需求 load 区分。

### 7. DCache：`M_PFR` 请求及完成语义

[LoadUnit.scala:406](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala#L406)
将 `prf_rd` 翻译成 DCache 命令：

```scala
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd,
  MemoryOpConstants.M_PFR,
  Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD))
io.dcache.req.bits.vaddr := s0_dcache_vaddr
io.dcache.req.bits.instrtype := Mux(s0_sel_src.prf,
  DCACHE_PREFETCH_SOURCE.U, LOAD_SOURCE.U)
```

cycle 25671 的对应波形为 `dcache_req.valid=1`、`ready=1`、`cmd=2`、
`vaddr=0x80001760`、`instrtype=3`。因此 request 已被 DCache 接收，不是仅停留在 LoadUnit 的
组合输出上。[CacheConstants.scala:29](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/CacheConstants.scala#L29)
定义 `M_PFR="b00010".U`，所以波形的十进制 2 与源码严格对应：

```scala
def M_XRD = "b00000".U
def M_XWR = "b00001".U
def M_PFR = "b00010".U // prefetch with intent to read
def M_PFW = "b00011".U // prefetch with intent to write
```

本 FST 对 DCache 导出了大量 `state_vec_*`，但这些是共享的 cache entry 状态而没有可与 ROB=12 或
LQ=48 直接关联的 request-ID/entry-ID 链路；不能可靠指定哪一个 state vector 属于该预取，故不对
其数值和命名做猜测。可被严格归因的 cache 证据止于 LoadUnit->DCache 的 fire。DCache 是否命中、
是否分配 miss entry、何时 refill，需要进一步导出同一 request 的 MSHR ID/response trace；当前报告
不把未关联的 cache state 误解释为本指令结果。

### 8. StoreUnit 与 StoreBuffer：为何本条 PFR 不走 store 路径

波形没有找到任一 `inner_StoreUnit_*` 输入带有 PC `0x800001a6` 的有效事件；同时 Dispatch 的
`sqIdx.flag=0`。这与 Decode 把 PFR 的 `fuType` 指定为 `ldu`、MemBlock 把它连至 LoadUnit 的源码
完全一致。故 StoreUnit 不参与这条软件读预取的地址生成、TLB 请求、DCache 请求或 writeback。

Store 侧确实也有“预取”控制，但它是不同来源的 store/hardware-prefetch 路径：
[Sbuffer.scala:203](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L203)
定义 `store_prefetch` 到 DCache 的 Decoupled 接口，
[Sbuffer.scala:402](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L402)
由 committed store 或 store prefetcher 驱动：

```scala
val store_prefetch = Vec(StorePipelineWidth, DecoupledIO(new StorePrefetchReq))
...
io.store_prefetch(i).valid := prefetcher.io.prefetch_req(i).valid ||
  (io.in(i).fire && io.in(i).bits.vecValid && io.in(i).bits.prefetch)
```

这与本 PFR 的 LoadUnit `M_PFR` 路径不同；若执行 `prefetch.w`，才会由 LoadUnit 的 `prf_wr` 选择
`M_PFW`，但仍不意味着该 ISA 指令必须经过 StoreUnit/Sbuffer。本次 PC 的“StoreUnit 无事件”是
PREFETCH.R 专属的正确现象，而非遗漏。

### 9. Writeback、提交可见性、异常与 redirect

在 cycle 25674，`backend.io_mem_writebackLda_1` 为 ROB=12 输出：

```text
valid=1, ready=1, fire=1
lq=48, replay=0, data=0, rfwen=0
```

`rfwen=0` 与 `rd=x0`、Rename 的 `pdest=0` 一致，说明 writeback 的作用是让 ROB 获知此 memory
uop 已完成，而不是把加载数据写入 GPR；`data=0` 也不是程序可见 load result。`replay=0` 排除了
该请求被 LoadUnit replay queue 重新发射的情况。

在 cycle 25640–25779 窗口，以下 redirect/flush 波形全部没有 asserted cycle：

- `inner_ctrlBlock.io_toIssueBlock_flush_valid`；
- `inner_ctrlBlock.rob.io_redirect_valid`；
- `inner_ctrlBlock.rename.io_redirect_valid`；
- `inner_LoadUnit_1.io_redirect_valid`。

因此没有观察到分支恢复、ROB flush、Rename flush、LoadUnit replay/违例 redirect 或由该预取引发的
异常恢复。commit/difftest 的目标 PC 有效记录在本次 `--no-diff` dump 中不可见，已在上文说明；可见的
writeback 加上最终仿真 `HIT GOOD TRAP at pc = 0x800001ea` 支持“执行完成且未陷入异常”的结论，但
不虚构未导出的 CSR、trap cause、GPR/FPR 或 cache refill 状态。

### 10. 信号来源、去向与性能影响汇总

| producer | 信号/值 | consumer | 对本 PFR 的意义 |
| --- | --- | --- | --- |
| IBuffer lane 4 | PC/instr/FTQ=`0x800001a6`/`0x0212e013`/`22:1` | CtrlBlock、Decode lane 4 | 建立 frontend 身份。|
| DecodeUnit lane 4 | `isPreR=1`、`fuOp=0x9`、`fuType=ldu` | Rename | 选择 LoadUnit 软件读预取语义。|
| Rename lane 4 | `ROB=12`、`psrc0=39`、`pdest=0` | Dispatch/issue | 用物理寄存器携带基址，且无目的寄存器。|
| Dispatch lane 6 | `valid&&ready`、`LQ=48` | memory issue queue | 分配/携带 load-side 身份。|
| scheduler LDA1 | `src0=0x80001740`、ROB=12 | MemBlock/LoadUnit 1 | 把基址送到地址生成。|
| LoadUnit 1 | `isPrefetch=1`、vaddr=`0x80001760` | DTLB | 标记为预取读翻译。|
| LoadUnit 1 | `cmd=M_PFR(2)`、`instrtype=3`、`valid&&ready` | DCache load port 1 | 真正发出 cache 读意图预取。|
| LDA writeback 1 | `rfwen=0`、`replay=0` | ROB | 完成该无结果 uop。|

本波形中可归因于该指令的性能事实是：Rename、Dispatch、issue->LoadUnit、LoadUnit->DCache 和
writeback 边界均发生 fire；没有目标相关 redirect/replay，LoadUnit/DCache 也没有 backpressure。
唯一可见的等待是 Dispatch 后至 issue 的 6 cycles，但由于缺少 ROB=12 对应 issue-entry 的 ready、
source-ready、selection 和 queue-occupancy dump，无法将其归因于任何具体资源。若要进一步评估
PREFETCH.R 的收益，应重新导出 DCache MSHR/request-id、tag hit/miss、refill、load response 及
issue-queue select 原因，并与删除 `prefetch.r` 的对照程序比较后续 `ld 32(s1)` 的延迟。
