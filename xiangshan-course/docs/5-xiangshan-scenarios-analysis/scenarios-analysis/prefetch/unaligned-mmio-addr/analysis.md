# `prefetch.r` 非对齐 MMIO：波形与源码交叉验证报告

## 结论

本次测试在昆明湖 V2 emu 上执行了地址为 `0x38000001` 的 `prefetch.r`。该地址位于片上 TIMER 的 MMIO 区间，且对 `prefetch.r` 的半字对齐要求而言是非对齐的。最终没有进入 cause 4（load address misaligned）或 cause 5（load access fault）处理函数：

```text
prefetch.r completed: misaligned=0 access_fault=0
Core 0: HIT GOOD TRAP at pc = 0x80000370
```

这里的 `HIT GOOD TRAP` 来自测试程序末尾的 `_halt(0)`，不是本条 `prefetch.r` 的异常。

FST 对目标事务给出了直接证据：在 LoadUnit S2，目标 uop 同时满足 `isPrefetch=1`、输入 `isMisalign=1`、`io_pmp_mmio=1`，但局部信号 `s2_isMisalign=0`，随后传出的 cause 4/cause 5 位均为 `0`，且没有 redirect、ROB exception 或 trap。源码说明这是软件 prefetch 的显式策略：把局部异常向量清零，并把 `s2_isMisalign` 清零。由于 MMIO 形成的 load access fault 分支依赖 `s2_out.isMisalign`，该清零也使该分支为假。

对于“这条指令是否真正发起了读请求”，结论需要按接口层级表述：它**确实**完成了 `LoadUnit -> DCache` 的内部 `M_PFR` 请求握手；但该请求在 DCache S2 被标记为 `cancel=1`，没有形成有效的 MissQueue/MSHR 分配，也没有在 DCache 或 Uncache 的外部 TileLink A 通道上形成对 `0x38000000/0x38000001` 的事务。因此它不是一次真正到 TIMER 设备的 MMIO 读。下文的“读请求是否真正发起”一节给出该结论的同周期波形和源码因果链。

需要严格限定一件事：本个输入地址走到了 S0 的 `misalignWith16Byte=1` 路径，所以 S0 的 cause 4 位为 `0`；FST 中可见的 S1/S2 相关异常位也为 `0`。S1 输入的 cause 5 位和局部 `s2_exception_vec_4` 未被转储。因此波形没有展示“exception vector 从 1 变成 0”的边沿；它直接展示的是 `s2_in_r_isMisalign=1 -> s2_isMisalign=0`。异常向量清零赋值本身由源码直接证明，FST 则证明最终可见的 exception vector bit 4/5 仍为 `0`。

## 试验材料与方法

| 项目 | 内容 |
| --- | --- |
| 测试程序 | [`main.c`](/home/agent1/prefetch-env/nexus-am/apps/prefetch-misalign/main.c:1) |
| ELF / 反汇编 | `/home/agent1/prefetch-env/nexus-am/apps/prefetch-misalign/build/prefetch-misalign-riscv64-xs.elf` / `prefetch-misalign-riscv64-xs.txt` |
| 目标波形 | [`build/2026-08-26-11-42-52.fst`](/home/agent1/prefetch-env/XiangShan/build/2026-08-26-11-42-52.fst) |
| 仿真命令 | `./build/emu --no-diff --dump-wave-full -i /home/agent1/prefetch-env/nexus-am/apps/prefetch-misalign/build/prefetch-misalign-riscv64-xs.bin` |
| 波形读取器 | `wavekit` 开源库，版本 `0.7.0`，使用 `wavekit.FstReader` |
| 顶层 / 时钟 | `TOP` / `TOP.clock`；使用 `sample_on_posedge=True` 采样 |
| LoadUnit 根路径 | `TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_LoadUnit_2` |

本报告按 `analyze-xiangshan-wavekit` 的工作流，使用 `FstReader.load_waveform(..., sample_on_posedge=True)` 将 FST 变更流同步到时钟上升沿，并以 PC、ROB index、LQ index、`fuOpType` 和访存地址联合关联，不以单个信号猜测事务身份。

本次实际执行的只读查询环境如下；表中的时间与 cycle 都来自该查询。

```bash
PYTHONPATH=/home/agent1/wavekit-xslab/src \
  /home/agent1/wavekit-xslab/.venv/bin/python /tmp/wave_extract.py
```

该脚本使用的公开 API 是 `wavekit.FstReader` 与 `FstReader.load_waveform`；为定位 PC 变化时刻，还读取了对应 FST signal 的 value changes。波形时间范围为 `[0, 49793]`，目标发生在 `time=36766`，即 posedge `cycle=18383`。

## 测试程序、指令和 MMIO 身份

测试程序为 cause 4 和 cause 5 分别注册了只打印并计数的处理函数，然后对 `0x38000001` 发出 `prefetch.r`：

```c
irq_handler_reg(EXCEPTION_LOAD_ADDR_MISALIGNED, load_misaligned_handler);
irq_handler_reg(EXCEPTION_LOAD_ACCESS_FAULT, load_access_fault_handler);
issue_prefetch_r(0x38000001UL);
printf("prefetch.r completed: misaligned=%u access_fault=%u\n",
       misaligned_seen, access_fault_seen);
```

反汇编精确定位到目标指令：

```text
8000033a:  38000637  lui  a2,0x38000
8000033e:  0605      addi a2,a2,1       # a2 = 0x38000001
80000340:  00166013  .word 0x00166013  # prefetch.r 0(a2)
```

所以本报告使用的目标 PC 是 **`0x80000340`**，目标虚实地址是 **`0x38000001`**。

`0x00166013` 的 decode 条件是 `opcode=0010011`、`funct3=110`、`rd=0`、`rs2=1`，因此被译为 `LSUOpType.prefetch_r`。见 [DecodeUnit.scala:1102](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1102) 和 [DecodeUnit.scala:1170](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1170)。波形在 LDU 输入处也观察到 `fuOpType=9`、`prf=1`、`prf_rd=1`，与 `prefetch_r` 编码一致。

TIMER MMIO 范围定义为 `AddressSet(0x38000000L, TIMERConsts.size - 1)`，见 [SoC.scala:77](/home/agent1/prefetch-env/XiangShan/src/main/scala/system/SoC.scala:77)。`0x38000001` 因而确实在该设备空间。异常号定义为 `loadAddrMisaligned=4`、`loadAccessFault=5`，见 [package.scala:830](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/package.scala:830)。

## PC 关联与流水线时间线

下表中的 `valid/ready=1/1` 或 `fire=1` 是事务真正前进的证据。到 LoadUnit 后，PC、ROB=`0x41`（65）、LQ=`12`、`fuOpType=9` 和地址共同标识同一条 uop。

| 阶段 | cycle / time | 波形中的目标证据 | 结论 |
| --- | --- | --- | --- |
| Decode | 18376 / 36752 | decode 第 2 路输入与输出同时见到 `PC=0x80000340`，`valid/ready=1/1` | 指令已被译码并下送 |
| Rename | 18377 / 36754 | rename 第 2 路输入与输出为该 PC，`valid/ready=1/1` | 保持同一 uop 身份 |
| Dispatch / MemScheduler 入队 | 18378 / 36756 | mem uop 第 8 路见到该 PC，`valid/ready=1/1` | 进入内存调度器 |
| MemScheduler 发射 | 18381 / 36762 | 发射路径携带 `ROB=65`、`LQ=12`、`fuOp=9`，`valid/ready=1/1` | 与后续 LDU 输入的非 PC 字段匹配 |
| LoadUnit S0 | 18383 / 36766 | `PC=0x80000340`、`src0/vaddr=0x38000001`、`s0_fire=1`、`prf=1`、`prf_rd=1` | 目标软件 `prefetch.r` 被 LDU 接收 |
| LoadUnit S1 | 18384 / 36768 | `PC=0x80000340`、`isPrefetch=1`、`isMisalign=1` | 非对齐状态进入后级 |
| LoadUnit S2 | 18385 / 36770 | `PC=0x80000340`、`vaddr=paddr=0x38000001`、`isPrefetch=1`、`io_pmp_mmio=1` | 同一事务已完成地址/MMIO 判定 |
| LoadUnit S3 / 输出 | 18386 / 36772 | `io_ldout_valid=1`，仍带目标 PC、地址和 `isPrefetch=1` | 该 uop 正常从 LDU 输出 |

从调度器入队到发射之间有两个采样周期（18379--18380）未见目标发射。FST 没有转储足以把该等待唯一归因到某一 scheduler entry、选择仲裁或资源冲突的目标相关内部状态，因此报告不对该两周期作超出证据的归因。进入 LDU 后，`io_ldin`、DCache 请求和 `io_ldout` 的相关握手均成功，S2 `ready=1`，没有观察到可归属于该 uop 的流水线回压。

LoadUnit 本身是 S0/S1/S2/S3 有效位流水结构；目标范围内未找到可观测、单独命名的 FSM state 信号，故这里以有效位/握手表示状态，而不虚构 FSM 状态转换。

## 直接波形证据：非对齐、MMIO 与异常结果

所有以下信号均来自 `inner_LoadUnit_2`。S2 的 `io_pmp_mmio` 是 LoadUnit 的 PMP 输入端口；源码中 `s2_pmp` 由 `WireInit(io.pmp)` 建立，见 [LoadUnit.scala:1195](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1195)。

| 时间 | 输入/局部/输出信号 | 观测值 | 直接说明 |
| --- | --- | --- | --- |
| c18383 / t36766, S0 | `io_ldin_valid/ready`, `io_dcache_req_valid/ready`, `io_dcache_req_bits_cmd` | `1/1`, `1/1`, `2` | uop 已被接受并实际发起 DCache 请求；源码将 `prf_rd` 映射为 `M_PFR` |
| c18383 / t36766, S0 | `s0_alignType`, `s0_addr_aligned`, `s0_misalignWith16Byte` | `1`, `0`, `1` | `prefetch.r` 的半字对齐检查失败，且走 `misalignWith16Byte` 路径 |
| c18383 / t36766, S0 | `s0_sel_src_uop_exceptionVec_4` | `0` | 进入 S0 的 cause 4 位不是 1 |
| c18384 / t36768, S1 | `s1_in_r_isPrefetch`, `s1_in_r_isMisalign` | `1`, `1` | 非对齐标志确实被带入后级，并非测试地址被错误地当作对齐 |
| c18384 / t36768, S1 | `s1_in_r_uop_exceptionVec_4`, `s1_exception_new_vec_4/5` | `0`, `0/0` | S1 可见异常位为零 |
| c18385 / t36770, S2 | `s2_in_r_isPrefetch`, `s2_in_r_isMisalign` | `1`, `1` | S2 输入仍是软件 prefetch，且仍带有 misalign 标志 |
| c18385 / t36770, S2 | `s2_in_r_vaddr/paddr`, `io_pmp_mmio`, `s2_actually_uncache` | `0x38000001 / 0x38000001`, `1`, `1` | 该事务在物理地址判定后确为 MMIO/uncache 路径 |
| c18385 / t36770, S2 | `s2_isMisalign` | **`0`** | 这是核心波形证据：输入为 1，LDU 的局部后继值已被清零 |
| c18385 / t36770, S2 | `s2_ready` | `1` | S2 没有因该事务停住 |
| c18385 / t36770, S2 | `s2_real_exceptionVec_4/5`, `s2_real_exception` | `0/0`, `0` | MMIO 判定已经成立时，最终 S2 异常结果仍为零 |
| c18386 / t36772, S3/LDU out | `s3_in_uop_exceptionVec_4/5`, `io_ldout_valid/ready`, `io_ldout_bits_uop_exceptionVec_4/5` | `0/0`, `1/1`, `0/0` | cause 4/cause 5 没有在输出端重新出现，且 LDU 输出被接受 |

为避免误读，`s2_mmio=0` 和 LDU 输出的 debug MMIO 位为 `0` 并不与 `io_pmp_mmio=1` 矛盾。源码把 `s2_mmio` 定义为 `!s2_prf && ...`，即软件 prefetch 不走普通 MMIO load 输出路径，见 [LoadUnit.scala:1250](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1250)。PMP 对该物理地址的 MMIO 识别仍可由波形中的 `io_pmp_mmio=1` 直接确认。

S0 请求命令的映射也在源码中明确：`io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd, MemoryOpConstants.M_PFR, ...)`，见 [LoadUnit.scala:407](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:407)。因此波形中的 `cmd=2` 与 `prf_rd=1` 是同一条 `prefetch.r` 访存控制路径的相互验证。

### exception vector 的证据边界

下列事实是 FST 直接给出的：

- S0 的输入 cause 4 位为 `0`；S1 输入 cause 4 位以及 `s1_exception_new_vec_4/5` 为 `0`；S2 输入 cause 4/5 位与 `s2_real_exceptionVec_4/5` 为 `0`；S3 与 `io_ldout` 的 bit 4/bit 5 也均为 `0`。
- FST 没有转储 `s1_in_r_uop_exceptionVec_5` 和局部 `s2_exception_vec_4`（优化掉或未 dump）。
- 因而，本报告不会说“在波形中看到 exceptionVec bit 4/5 从 1 清成 0”。本例中它们起始就是 0。
- 波形实际展示的、与源码条件逐项对应的转换是：`s2_in_r_isMisalign=1`、`s2_in_r_isPrefetch=1`，同时 `s2_isMisalign=0`。

这也解释了为何必须同时看源码和波形：仅看最终 exception vector 会遗漏此前存在的非对齐状态；仅看源码则无法确认该 clear 分支是否在此次具体 MMIO 事务上被命中。

## LoadUnit 源码因果链

### 1. S0 保留 `isMisalign`，但本例不置 cause 4 位

下列是 [LoadUnit.scala:815](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:815) 的原始逻辑：

```scala
s0_out.uop.exceptionVec(loadAddrMisaligned) :=
  (!s0_addr_aligned || s0_sel_src.uop.exceptionVec(loadAddrMisaligned)) &&
  s0_sel_src.vecActive && !s0_misalignWith16Byte
s0_out.isMisalign :=
  (!s0_addr_aligned || s0_sel_src.uop.exceptionVec(loadAddrMisaligned)) &&
  s0_sel_src.vecActive
```

本波形中的 `s0_addr_aligned=0`、`s0_misalignWith16Byte=1` 正好代入为：`exceptionVec(4)=0`，但 `isMisalign=1`。这与 S1/S2 输入观测完全一致，也说明这里不是“地址实际上对齐了”。

### 2. 软件 prefetch 显式清除局部异常向量和 `s2_isMisalign`

这是本问题的直接源码证据，来自 [LoadUnit.scala:1231](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1231)：

```scala
// soft prefetch will not trigger any exception (but ecc error interrupt may
// be triggered)
val s2_tlb_unrelated_exceps = s2_in.uop.exceptionVec(breakPoint)
when (!s2_in.delayedLoadError &&
      (s2_prf || s2_in.tlbMiss && !s2_tlb_unrelated_exceps)) {
  s2_exception_vec := 0.U.asTypeOf(s2_exception_vec.cloneType)
  s2_isMisalign := false.B
}
```

该条件在本事务上成立：波形的 `s2_in_r_isPrefetch=1` 即 `s2_prf=1`。因此赋值无须依赖 TLB miss 子条件。相同上升沿中，波形显示输入 `s2_in_r_isMisalign=1` 而局部 `s2_isMisalign=0`，是这个 `when` 分支在本事务上生效的直接运行时证据。FST 中未保留 `s2_exception_vec_4`，所以对异常向量本身的清零采取“源码赋值 + 最终各可见位为 0”的交叉证据，而不伪称看到了未转储位的翻转。

### 3. 为什么 MMIO 不会在后续构成 access fault

MMIO/uncache 的相关中间量在 [LoadUnit.scala:1214](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1214) 计算；波形中的 `io_pmp_mmio=1` 和 `s2_actually_uncache=1` 与此对应。

随后实际异常向量的 cause 5 项包含专门的 MMIO 非对齐分支，原文见 [LoadUnit.scala:1343](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1343)：

```scala
val s2_real_exceptionVec = WireInit(s2_exception_vec)
s2_real_exceptionVec(loadAddrMisaligned) :=
  (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) &&
  s2_actually_pbmt_nc && !s2_isvec && !s2_prf
s2_real_exceptionVec(loadAccessFault) :=
  (s2_exception_vec(loadAccessFault) ||
    s2_fwd_frm_d_chan && s2_d_denied ||
    s2_fwd_data_valid && s2_fwd_frm_mshr && s2_mshr_denied) && !s2_prf ||
  (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_actually_all_mmio
```

注意 cause 5 的最后一个 MMIO 项没有写 `!s2_prf`。这正是本次需要验证的细节，而不是可以忽略的表面差异。后续赋值给出关键连接，见 [LoadUnit.scala:1408](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1408)：

```scala
s2_out := s2_in
// ...
s2_out.isMisalign       := s2_isMisalign
s2_out.uop.exceptionVec := s2_real_exceptionVec
```

因此该次事务的实际代入关系是：

```text
s2_prf = 1
  -> 软件 prefetch 分支令 s2_isMisalign = 0
  -> s2_out.isMisalign = 0
  -> (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_actually_all_mmio = 0
  -> loadAccessFault 的 MMIO 非对齐项不成立
```

FST 已独立验证这条链的关键运行时值：`s2_in_r_isMisalign=1`、`s2_isMisalign=0`、`io_pmp_mmio=1`、`s2_real_exceptionVec_5=0`、`s2_real_exception=0`。这比仅凭“最后没有 trap”更强，因为它同时确认了 MMIO 和非对齐输入确实存在。

## 读请求是否真正发起：按接口层级判断

“发起读请求”不能只看一个 `valid`。本设计在这里至少有三个不同语义的边界：LoadUnit 向 DCache 发起的前端查询、DCache 向 MissQueue 提交可分配的 miss，以及 DCache/Uncache 向 L2 或设备侧发出的 TileLink A 请求。本次目标 uop 在第一个边界真实前进，在第二个边界出现了携带 `cancel=1` 的传输，但没有跨过“有效分配”这一语义门槛；第三个边界没有目标地址的读事务。

| 层级 | 结果 | 目标事务的直接波形证据 | 含义 |
| --- | --- | --- | --- |
| LoadUnit -> DCache | **是** | `c18383/t36766`：`LoadUnit_2.io_dcache_req.valid/ready=1/1`、`cmd=2 (M_PFR)`、`vaddr=0x38000001`；同拍 `dcache.ldu_2.io_lsu_req.valid/ready=1/1` | `prefetch.r` 真正进入 DCache 前端，进行了这一级的请求/查询 |
| DCache tag/miss 判定 | **是** | `c18384/t36768`：`dcache.ldu_2.s1_valid=1`、`s1_will_send_miss_req=1` | 该请求没有在输入握手处被丢弃；DCache 已将其推进到 miss 请求准备阶段 |
| DCache -> MissQueue | **有 Decoupled 握手，但被取消** | `c18385/t36770`：`io_miss_req.valid/ready=1/1`、`cmd=2`、块地址 `0x38000000`、`cancel=1`；`missReqArb.in_3`、arbiter 输出和 `missQueue.io_req` 均为 `valid/ready=1/1` 且 `cancel=1` | 传输接口确实握手，但 payload 明确表示“取消”；这不是有效 MSHR 分配 |
| MissQueue allocation / merge | **否** | 同一拍：`miss_req_pipe_reg_alloc=0`、`miss_req_pipe_reg_merge=0`、`acquire_from_pipereg_valid=0`、`io_mem_acquire_valid=0` | 没有分配或合并 MSHR，也没有生成 refill/acquire 请求 |
| 外部 DCache TileLink A | **否** | 同一窗口 `dcache.auto_client_out_a_valid=0`；全 FST 扫描没有任何 `valid=1` 的 A 请求地址为 `0x38000000` 或 `0x38000001` | 没有从 DCache 发出该 cache block 的下游读/Acquire |
| 外部 Uncache/MMIO TileLink A | **否** | 同一窗口 `inner_uncache.auto_client_out_a_valid=0`；全 FST 扫描的 281 个 valid 样本中没有地址为 `0x38000000` 或 `0x38000001` 的样本 | 没有从 Uncache 单元向 TIMER 发出 MMIO 读 |

为便于复核，表中 DCache A 通道的完整 FST 路径为 `TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache.dcache.auto_client_out_a_{valid,ready,bits_address[47:0]}`；Uncache A 通道为 `TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_uncache.auto_client_out_a_{valid,ready,bits_address[47:0]}`。两组信号均以 `TOP.clock` 的上升沿采样；全波形扫描范围为报告前文记录的 `[0, 49793]`。

表中的两个地址均被搜索，是为了避免地址格式造成的误判：DCache LoadPipe 会把 `s2_paddr=0x38000001` 用 `get_block_addr` 转换为 cache block 地址 `0x38000000`，见 [LoadPipe.scala:439](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:439)。所以，外部路径上未观察到的是原始字节地址和对齐后的 cache block 地址两者。

### 为什么出现“握手了，但没有读设备”

LoadUnit S0 对软件 read prefetch 明确生成 `M_PFR`，而不是普通 load 的 `M_XRD`，见 [LoadUnit.scala:406](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:406)：

```scala
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd,
  MemoryOpConstants.M_PFR,
  Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD))
```

这对应 c18383 的第一层握手。到 S2，目标地址的 PMP/MMIO 判定已经成立，波形为 `s2_actually_uncache=1`。源码对 software prefetch 同时给出了两个看似相反、但组合后正好解释此现象的条件：普通 uncache 路径以 `!s2_prf` 为门，而发往 DCache 的 kill 不以 `!s2_prf` 为门。

```scala
val s2_uncache = !s2_prf && s2_actually_uncache
// ...
io.dcache.s2_kill := s2_pmp.ld || s2_pmp.st || s2_actually_uncache || s2_kill
```

以上代码分别见 [LoadUnit.scala:1219](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1219) 和 [LoadUnit.scala:1523](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1523)。这正对应目标拍的 `s2_actually_uncache=1`、`s2_uncache=0`、`io_dcache_s2_kill=1`：software prefetch 不走普通 MMIO/uncache load 路径，但其已经开始的 DCache 请求会被 kill。

DCache LoadPipe 将该 kill 直接编码到 miss payload 的取消位：

```scala
io.miss_req.valid := s2_miss_req_valid
io.miss_req.bits.addr := get_block_addr(s2_paddr)
io.miss_req.bits.cancel := io.lsu.s2_kill || s2_tag_error || s2_btot_occupy_fail
```

见 [LoadPipe.scala:433](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:433)。本次波形中的 `io_lsu_s2_kill=1` 正好使 `io_miss_req_bits_cancel=1`；`s2_nack_no_mshr=0` 且 `io_miss_req_ready=1`，所以这里不是 MSHR 满或 `ready` 回压，而是取消位本身造成的 `s2_mq_nack=1`。源码也把 `cancel` 纳入该 nack 条件，见 [LoadPipe.scala:530](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala:530)。

MissQueue 对这个取消位有明确的语义定义，而不只是把它当作一个调试标志：

```scala
// For now, miss queue entry req is actually valid when req.valid && !cancel
// ...
miss_req_pipe_reg.alloc := alloc && io.req.valid && !io.req.bits.cancel && !io.wbq_block_miss_req
miss_req_pipe_reg.merge := merge && io.req.valid && !io.req.bits.cancel && !io.wbq_block_miss_req
```

该注释和赋值分别见 [MissQueue.scala:78](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:78) 与 [MissQueue.scala:1106](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1106)。因此 `missQueue.io_req.valid/ready=1/1` 不能被简化为“已接收有效读请求”；在本拍 `cancel=1` 的条件下，源码和 `alloc=merge=0` 的波形共同证明它没有成为 MissQueue entry。

最后，MissQueue 的 `can_send_acquire` 明确以 pipeline reg 的 `alloc` 为前提，见 [MissQueue.scala:246](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:246)，并在 [MissQueue.scala:1248](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1248) 驱动 `io.mem_acquire`；DCacheWrapper 再将该接口连到外部 `bus.a`，见 [DCacheWrapper.scala:1551](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1551)。波形中的 `io_mem_acquire_valid=0` 和 `auto_client_out_a_valid=0` 因而是下游无 DCache 读事务的直接证据。作为交叉检查，完整 FST 里该 DCache A 通道仍有 24 次其他请求，目标附近最近的一次是 `c18406/t36812`、地址 `0x80002580`；它不是目标地址，说明“未见目标请求”不是因为该 A 通道没有被转储。

设备侧也有独立的外部证据。Uncache 单元的 `clientNode` 是其 TileLink client，见 [Uncache.scala:202](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:202)，其 `mem_acquire` 直接接到 `bus.a` 且以 `q0_canSent` 驱动 valid，见 [Uncache.scala:217](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:217) 和 [Uncache.scala:429](/home/agent1/prefetch-env/XiangShan/src/main/scala/xiangshan/cache/dcache/Uncache.scala:429)。相应 FST 信号 `inner_uncache.auto_client_out_a_valid` 在 c18380--c18410 始终为 0；完整波形中虽有 281 个其他 valid 样本，但其中没有上述两个目标地址。故本条 `prefetch.r` 既没有走 DCache refill/acquire，也没有走 Uncache 的 MMIO read。

综合起来，准确的表述是：**LoadUnit 真正发起并完成了一次内部的 DCache software-prefetch 请求；对应 LoadPipe 也产生了一个已握手但带取消位的 miss payload；然而并没有发起可到达内存层次或 TIMER 设备的读请求。**

## 架构可见结果、redirect 与缓存侧现象

在 `t=36766` 至 `t=36780` 的八个上升沿样本中，下列信号全部为 `0`：

- `TOP.SimTop.endpoint.trap.io_bits_hasTrap`
- `core.memBlock.io_redirect_valid`
- `core.backend.inner_ctrlBlock.rob.io_redirect_valid`
- `core.backend.inner_ctrlBlock.rob.io_exception_valid`
- `core.backend.inner_ctrlBlock.rob.io_flushOut_valid`
- `rob.exceptionGen.io_redirect_valid` 与 `rob.exceptionGen.io_flush`

同一 uop 的 ROB commit-info 观测为 `PC=0x80000340`、`debug_instr=0x00166013`、`commit_v=1`、`basicDebug_isXSTrap=0`、`needFlush=0`、`mmio=0`。其 ROB index 为 `0x41` 的 ExceptionGen writeback 也报告 `hasException=0`、`exceptionVec_4=0`、`exceptionVec_5=0`。这与程序中两个计数器均为 0 相互印证。

缓存侧仍确实将其作为软件 prefetch 处理：LoadUnit_2 的仿真性能计数为 `s0_software_prefetch_fire=1`、`s2_prefetch=1`、`s2_prefetch_miss=1`、`s2_prefetch_ignored=1`、`s2_prefetch_accept=0`。`ignored` 在源码中对应 DCache 的 `s2_mq_nack`；本次具体原因是带 `cancel=1` 的 miss payload，而不是 `valid/ready` 未握手或 MSHR 已满。该 payload 经过 MissQueue 接口但未成为有效 entry（`alloc=merge=0`），它不等同于异常，也没有阻止本 uop 从 LDU 输出或完成 ROB 可见提交。

## 结论的适用范围

可以确定的是：在这份 FST、这个程序和当前昆明湖 V2 源码下，非对齐 TIMER-MMIO 地址的 `prefetch.r` 被 Decode 识别为软件 read prefetch，进入 LDU 时保留了 `isMisalign=1`，在 S2 因 `s2_prf=1` 被清零，因而没有生成 cause 4 或 cause 5，也没有 trap/redirect/flush。

不能从本份 FST 单独断言的是：一个已经为 `1` 的 exception vector 位在本次实际事务中发生了可见的 `1 -> 0` 翻转，因为本例 S0 的 cause 4 位已为 0，且 S1 cause 5 位与 S2 的局部 cause 4 位没有被 dump。源码明确含有整向量清零赋值；波形明确含有 misalign 标志清零及最终 exception vector 为零。这两个层次合起来构成对本次行为的可信证据。
