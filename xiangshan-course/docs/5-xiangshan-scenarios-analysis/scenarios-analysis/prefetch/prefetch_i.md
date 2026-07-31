# 香山昆明湖执行 PREFETCH_I 指令的流程分析

## PREFETCH_I 指令介绍

### 这条指令是什么

`PREFETCH.I`（汇编格式为 `prefetch.i offset(base)`）是 RISC-V **Zicbop**（Cache-Block Prefetch Operations）扩展中的一条 cache-block prefetch 指令。它向硬件提供一个**提示（HINT）**：软件预计在不久后会以**指令取指**方式访问包含目标有效地址的缓存块。

它的有效地址为：

```text
EA = x[rs1] + sign_extend(imm[11:0])
```

其中 `rs1` 是汇编中的 `base`，立即数使用 12 bit 偏移；该指令编码占用 `OP-IMM / ORI` 形式，但 `rd` 固定为 `x0`，并且低位 `imm[4:0]` 必须为 `0`。因此汇编器写法看起来像 load/store 的地址操作数，但该地址指向的是**待取指的代码块**，而不是要读写的数据。

```asm
# 提示硬件：后续很可能从 a5 所指的代码块取指。
prefetch.i 0(a5)
```

`PREFETCH.I` 与同属 Zicbop 的两条数据预取指令的区别如下：

| 指令 | 软件提示的未来访问类型 | 典型目标缓存侧 |
|---|---|---|
| `prefetch.i` | instruction fetch | ICache / instruction-fetch cache hierarchy |
| `prefetch.r` | data read（load） | DCache / data-read hierarchy |
| `prefetch.w` | data write（store） | DCache / data-write hierarchy |

作为 HINT，`PREFETCH.I` 不返回数据、不写通用寄存器、也不保证目标块一定被填入任何一级缓存；实现可以接受、延迟、过滤或忽略该提示。它更不是同步指令：不能取代 `fence`、`fence.i`、TLB 刷新或任何保证代码修改可见性的机制。RISC-V Zicbop 规范将它定义为“提示硬件某缓存块很可能很快用于 instruction fetch”，并明确允许实现选择是否把该块缓存到取指可访问的缓存中。

### 这条指令会做什么

从软件视角，执行 `prefetch.i offset(base)` 的效果可概括为：

1. 计算 `base + offset` 的有效地址；
2. 以该地址所在的**缓存块**为粒度向硬件发出“即将取指”的提示；
3. 继续执行后续指令，不等待预取请求完成，也不会从目标地址读取一条指令返回给寄存器；
4. 硬件可在适当情况下把该块送入 ICache、L2 或其他被 instruction fetch 访问的层级，或在已经命中、请求重复、资源不足时不再发起下游请求。

因此，它只影响**性能路径**，不改变程序的架构功能路径。无论硬件是否实际填充缓存，程序后续仍必须通过正常的 `jal`、`jalr`、顺序取指或其他控制流到达目标代码。

在本实验的昆明湖实现中，`PREFETCH.I` 并不是前端直接执行的一条特殊控制指令，而是先被 Decode 归为 `FuType.ldu` 与 `LSUOpType.prefetch_i`，经 Rename、Dispatch、LQ、memory issue queue 和 LoadUnit 计算出地址；随后 LoadUnit 识别 `prf_i`，不发 DCache 请求，而是产生 `ifetchPrefetch` 送至前端 ICache。ICache 将该软件预取暂存在 `softPrefetch` 缓冲中，并赋予它高于 FTQ 预取的仲裁优先级。具体 Chisel 代码和完整路径见“香山昆明湖源代码分析”章节。

可以把它理解为以下伪代码，但不能把伪代码中的 `request_instruction_cache_block` 视为强制同步操作：

```text
address = base + sign_extend(offset)
hint_to_hardware(
  access_kind = instruction_fetch,
  cache_block_containing(address)
)
continue_without_waiting()
```

### 这条指令对程序执行有什么帮助

`PREFETCH.I` 的主要目标是隐藏“代码块尚未在取指缓存中”时的等待时间。软件若能比真正执行目标代码更早知道将跳转或调用哪个代码区域，就可以提前发出提示，并在两者之间安排独立计算、数据访存或其他可执行工作。

典型适用场景包括：

| 场景 | 预取对象 | 为什么可能有效 |
|---|---|---|
| 间接调用 / 函数指针 | 即将调用的函数入口 | 目标函数的第一条取指可能是 ICache miss；在解析函数指针后立即预取可提前启动访问。|
| 解释器、JIT、动态分发 | 下一个 handler 或生成代码块 | 控制流目标分散且预测器难以提前覆盖时，软件掌握的目标地址可补充硬件预测。|
| 大型状态机 / 冷路径 | 即将进入的少用代码页或处理分支 | 在进入错误处理、格式转换、协议处理等较冷代码前缩短首次取指延迟。|
| 分块算法的下一阶段 | 下一阶段代码入口 | 当前阶段尾部已知下一阶段入口时，可将代码预取与当前阶段收尾重叠。|

本演示程序正采用“先提示、后使用”的基本模式：先访问 `before_data`，然后对 `prefetched_code` 的函数入口执行 `prefetch.i`，再访问 `after_data` 并打印信息，最后通过 `jal` 调用该函数。预取与实际取指之间的间隔称为**预取距离**；距离太短时，ICache 请求可能来不及完成，距离太长时目标块可能被替换或污染其他更有价值的缓存行。

使用这条指令时还应注意以下限制：

- **没有功能性保证**：它是 hint，不能据此省略正常跳转、正常取指或错误处理；
- **可能无性能收益**：目标块已在 ICache、请求被合并、硬件忽略 hint 或真正使用时机过晚，都可能使收益为零；
- **可能造成污染或带宽竞争**：过度预取会占用 ICache、MSHR、下级缓存或互连带宽，并可能挤出更热的代码；
- **需要合适的预取距离**：应在“地址已知”与“即将执行”之间插入足够而不过长的独立工作；
- **不能替代 `fence.i`**：若场景是自修改代码、JIT 写入新指令或代码/数据一致性维护，必须使用相应的同步与平台规定流程；`PREFETCH.I` 只表达未来取指意图。`FENCE.I` 的职责是 instruction-fetch synchronization，与预取提示是不同问题。

## 香山昆明湖源代码分析

> 本节**只依据** `/home/yanyusong/cbo-kmhv2/XiangShan/src/` 中的昆明湖 Chisel 源码进行静态解析；不使用波形中的 cycle、信号取值或仿真统计。

### 总体数据通路

`PREFETCH.I` 并不直接在前端执行。它先作为一条普通后端微操作经历 Decode、Rename、Dispatch、Memory Scheduler、LoadUnit 和 ROB；特殊性在于 LoadUnit 的 S0 阶段识别 `prf_i` 后，不把它作为 DCache load，而是转换成送给 Frontend ICache 的 `SoftIfetchPrefetchBundle`。完整的模块级路径如下：

```text
DecodeUnit
  -> Rename
  -> NewDispatch（ROB / LQ 分配、送 memory issue queue）
  -> Backend.memScheduler
  -> BackendIO.issueLda
  -> XSCore / MemBlock.LoadUnit
  -> LoadUnit S0：识别 prefetch_i
       ├─ 普通 DTLB / DCache 数据访问路径被抑制
       └─ io.ifetchPrefetch
  -> MemBlock.ifetchPrefetch
  -> XSCore.frontend.softPrefetch
  -> Frontend.ICache.softPrefetch
  -> ICache Prefetcher / MissUnit

同时：LoadUnit.ldout -> Backend writeback datapath -> ROB -> Commit / Difftest retire
```

这条路径解释了一个重要设计选择：**`PREFETCH.I` 重用 LDU 的乱序调度、ROB、LQ、flush 与完成汇报框架，但其缓存效果由 ICache 实现，而不是由 DCache 实现。**

### 1. Decode：从编码识别到 `FuType.ldu`

软件预取的识别在 [DecodeUnit.scala:1102](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1102>) 完成。该实现将 `opcode=0010011`、`funct3=110`、`rd=x0` 定义为 software prefetch 的公共编码，再以 `rs2` 区分读、写和指令预取：

```scala
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") &&
  inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
val isPreI = isSoftPrefetch && inst.RS2 === 0.U(5.W)
```

因此 `PREFETCH.I` 的 ISA 语义不是普通立即数 ALU 指令：它的目标寄存器固定为 `x0`，其地址基址来自 `rs1`，而 `rs2=0` 是识别 `prefetch_i` 的子操作编码。

在 [DecodeUnit.scala:1132](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1132>)，三种 software prefetch 都被送入 Load Unit，并使用 S 型立即数选择器：

```scala
}.elsewhen (isPreW || isPreR || isPreI) {
  decodedInst.selImm := SelImm.IMM_S
  decodedInst.fuType := FuType.ldu.U
  decodedInst.canRobCompress := false.B
}
```

`selImm=IMM_S` 表示预取的地址偏移依照 store-style immediate 解码；`fuType=ldu` 是后续进入 load 类型调度/执行资源的根本原因。`canRobCompress=false` 则要求该操作在 ROB 中保留独立的完成与提交记录，不能依靠 ROB 压缩合并。

最后，[DecodeUnit.scala:1166](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1166>) 将精确的功能操作码写为 `LSUOpType.prefetch_i`：

```scala
(isPreW || isPreR || isPreI) -> Mux1H(Seq(
  isPreW -> LSUOpType.prefetch_w,
  isPreR -> LSUOpType.prefetch_r,
  isPreI -> LSUOpType.prefetch_i,
)),
```

从这里开始，`fuType=ldu` 决定“在哪个执行资源中运行”，`fuOpType=prefetch_i` 决定“LoadUnit 内部采取何种特殊控制动作”。

### 2. Rename：保留乱序身份，但不分配目的寄存器

Decode 输出首先进入 Rename。对 `PREFETCH.I` 而言，Rename 仍需完成两类工作：

1. 将地址基址 `rs1` 映射为物理源寄存器 `psrc(0)`，使后续 LoadUnit 可以在乱序环境中读取最新地址基值；
2. 分配 ROB 身份，以便该提示指令能够等待执行完成、响应 flush/exception，并在程序序上退休。

但因为识别条件要求 `RD=0`，它没有有效整数目的寄存器。Rename 中针对有写回目的寄存器的保护可见于 [Rename.scala:682](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala:682>)：

```scala
io.out.map { case x =>
  when(x.valid && x.bits.rfWen){
    assert(x.bits.ldest =/= 0.U, "rfWen cannot be 1 when Int regfile ldest is 0")
  }
}
```

这说明 `ldest=x0` 的 `PREFETCH.I` 必须以 `rfWen=false` 通过 Rename；它没有 data result，也不占用可提交的 integer destination。与此同时，Rename 输出的 `DynInst` 仍包含 `robIdx`、`psrc`、`fuType`、`fuOpType`、异常信息、FTQ 元数据等，作为后续流水级的统一身份载体。

Rename 的重要作用不是给预取创造数据依赖，而是把“地址基寄存器依赖”和“程序顺序依赖”分别编码为 `psrc` 与 `robIdx`。

### 3. Dispatch：ROB/LQ 分配并送入 memory issue queue

Dispatch 使用 Rename 输出的 `DynInst`，根据 `fuType` 做资源归类。对于标量 load 类指令，LSQ 分配逻辑位于 [NewDispatch.scala:688](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:688>)：

```scala
when(!io.fromRename(i).fire) {
  enqLsqIO.needAlloc(i) := 0.U
}.elsewhen(isLoadVec(i) || isVLoadVec(i)) {
  enqLsqIO.needAlloc(i) := 1.U // load | vload
}.elsewhen(isStoreVec(i) || isVStoreVec(i)) {
  enqLsqIO.needAlloc(i) := 2.U // store | vstore
}.otherwise {
  enqLsqIO.needAlloc(i) := 0.U
}
enqLsqIO.req(i).valid := io.fromRename(i).fire && !isAMOVec(i) &&
  !isSegment(i) && !isfofFixVlUop(i)
enqLsqIO.req(i).bits := io.fromRename(i).bits
```

因为 Decode 已把 `PREFETCH.I` 标记为 `FuType.ldu`，它在这里走 load 类分配规则：获得 load-side 顺序/取消身份，而不是 Store Queue 身份。LQ 项的作用是让这个 LDU 类 uop 在 redirect、ROB commit、load cancel、调试信息等机制中与普通 load 使用同一套基础设施；它**不等价于**必然访问 DCache。

Dispatch 在 [NewDispatch.scala:724](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:724>) 复制 `fromRename` 的内容为 `updatedUop`，并把已有 ROB 号继续传向 store-set/LFST 及 issue 侧：

```scala
for (i <- 0 until RenameWidth) {
  updatedUop(i) := fromRename(i).bits
  updatedUop(i).debugInfo.eliminatedMove := fromRename(i).bits.eliminatedMove
  io.lfst.req(i).valid := fromRename(i).fire && updatedUop(i).storeSetHit
  io.lfst.req(i).bits.isstore := isStore(i)
  io.lfst.req(i).bits.robIdx := updatedUop(i).robIdx
}
```

`PREFETCH.I` 是 load 类而非 store 类；若它命中 store-set，`loadWaitBit` 可作为 load ordering 控制的一部分，但不会转入 StoreUnit。Dispatch 后，memory scheduler 把它保存在可服务 `FuType.ldu` 的 issue queue，等待地址源寄存器 ready 和 LDU issue port 可用。

### 4. Issue：从 memory scheduler 到 `issueLda`

后端对 memory 执行资源的接口定义在 [Backend.scala:1028](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:1028>)：

```scala
val issueLda = MixedVec(Seq.fill(params.LduCnt)(DecoupledIO(new MemExuInput())))
val issueSta = MixedVec(Seq.fill(params.StaCnt)(DecoupledIO(new MemExuInput())))
val issueStd = MixedVec(Seq.fill(params.StdCnt)(DecoupledIO(new MemExuInput())))
```

其中 `issueLda` 是标量 Load Address 执行端口集合，`issueSta` 和 `issueStd` 分别服务 store address 与 store data。`issueUops` 的排列也明确把 `issueLda` 列为独立资源组：[Backend.scala:1045](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:1045>)。

```scala
private [backend] def issueUops: Seq[DecoupledIO[MemExuInput]] = {
  issueSta ++ issueHylda ++ issueHysta ++ issueLda ++ issueVldu ++ issueStd
}.toSeq
```

Memory Scheduler 按该端口序列选择 ready 的 LDU uop；当选中 `PREFETCH.I` 时，其 `MemExuInput` 携带：

- `uop`：包括 `fuType=ldu`、`fuOpType=prefetch_i`、`robIdx`、`lqIdx`、立即数和 exception/flush 元数据；
- `src(0)`：Rename 后物理寄存器文件提供的地址基值；
- `ready/valid`：与 MemBlock 的 LoadUnit 输入构成标准 Decoupled 握手。

Backend 将 memory scheduler 的输出统一送到 `io.mem.issueUops`：[Backend.scala:791](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:791>)：

```scala
io.mem.issueUops.zip(toMem.flatten).foreach { case (sink, source) =>
  sink.valid := source.valid
  source.ready := sink.ready
  // 将 uop、src、ROB/LQ 等字段重组为 MemExuInput
}
```

因此，issue 阶段并不知道或不需要知道 ICache 的协议；它只负责把一个 LDU 类 uop 发射到正确的 LDU 执行端口。

### 5. XSCore 与 MemBlock：把 LDA 发射送给 LoadUnit

在顶层，Backend 的标量 load issue 接口连接到 MemBlock：

[XSCore.scala:217](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/XSCore.scala:217>)：

```scala
memBlock.io.ooo_to_mem.issueLda <> backend.io.mem.issueLda
memBlock.io.ooo_to_mem.issueSta <> backend.io.mem.issueSta
memBlock.io.ooo_to_mem.issueStd <> backend.io.mem.issueStd
```

MemBlock 为每条 LoadUnit 建立输入、redirect、LSQ、wakeup 和 DCache 接口：[MemBlock.scala:850](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:850>)：

```scala
for (i <- 0 until LduCnt) {
  loadUnits(i).io.redirect <> redirect
  loadUnits(i).io.ldin <> io.ooo_to_mem.issueLda(i)
  loadUnits(i).io.robDeqIdx <> io.ooo_to_mem.lsqio.pendingPtr
  loadUnits(i).io.feedback_slow <> io.mem_to_ooo.ldaIqFeedback(i).feedbackSlow
  io.mem_to_ooo.ldCancel.drop(HyuCnt)(i) := loadUnits(i).io.ldCancel
  io.mem_to_ooo.wakeup.drop(HyuCnt)(i) := loadUnits(i).io.wakeup
}
```

这里的 `redirect` 输入仍然对 `PREFETCH.I` 生效：若该 ROB 项在进入/停留 LDU 前被更老的异常、分支错误预测或访存违例 flush，它会通过 LDU 的 normal kill/redirect 逻辑被取消。也就是说，软件指令预取不会绕开乱序核的精确状态约束。

### 6. LoadUnit：`prefetch_i` 的核心特殊实现

这是 `PREFETCH.I` 与普通 load 的分叉点。LoadUnit 从 integer issue source 建立内部 `FlowSource` 时，显式生成三种预取类型位：[LoadUnit.scala:639](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:639>)：

```scala
out.prf    := LSUOpType.isPrefetch(src.uop.fuOpType)
out.prf_rd := src.uop.fuOpType === LSUOpType.prefetch_r
out.prf_wr := src.uop.fuOpType === LSUOpType.prefetch_w
out.prf_i  := src.uop.fuOpType === LSUOpType.prefetch_i
```

`prf_i` 会在 S0 同时影响 TLB、DCache、wakeup 和前端预取出口。

#### 6.1 地址计算与 S0 source arbitration

LoadUnit 先从多个候选 source（普通 integer issue、replay、fast replay、vector、硬件预取等）按优先级选择一个。S0 的选择与有效条件在 [LoadUnit.scala:330](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:330>)：

```scala
for(i <- 1 until SRC_NUM){
  s0_src_ready_vec(i) := !s0_src_valid_vec.take(i).reduce(_ || _)
}
val s0_src_select_vec = WireInit(VecInit(
  (0 until SRC_NUM).map(i => s0_src_valid_vec(i) && s0_src_ready_vec(i))))
// ...
s0_valid := !s0_kill && (... && io.dcache.req.ready && ...)
```

普通 LDU issue source 被选中后，S0 计算 `base + sign-extended immediate`，将地址写入 `s0_out.vaddr` 和 `s0_dcache_vaddr`。对 `prefetch.i offset(rs1)` 而言，该地址是**指令地址**，稍后成为 ICache 的软件预取虚拟地址。

#### 6.2 特殊 TLB 处理：不把它当成普通 data-load translation

[LoadUnit.scala:338](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:338>) 把 `prf_i` 纳入 `s0_tlb_no_query`：

```scala
val s0_tlb_no_query = s0_hw_prf_select || s0_sel_src.prf_i ||
  s0_src_select_vec(fast_rep_idx) || s0_src_select_vec(mmio_idx) ||
  s0_src_select_vec(nc_idx)
```

随后，TLB request 的控制位由该条件驱动：[LoadUnit.scala:383](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:383>)：

```scala
io.tlb.req.valid := s0_tlb_valid
io.tlb.req.bits.isPrefetch := s0_sel_src.prf
io.tlb.req.bits.kill := s0_kill || s0_tlb_no_query
io.tlb.req.bits.memidx.is_ld := true.B
io.tlb.req.bits.memidx.is_st := false.B
io.tlb.req.bits.memidx.idx := s0_sel_src.uop.lqIdx.value
io.tlb.req.bits.no_translate := s0_tlb_no_query
io.tlb.req.bits.debug.robIdx := s0_sel_src.uop.robIdx
```

这里看似仍生成了一个 TLB bundle，是为了复用 PMP/debug/LDU 接口结构；但 `kill` 与 `no_translate` 明确标示 `prefetch_i` 不应等待/采用普通 DTLB 翻译结果。真正的指令预取地址随后按虚拟地址形式交给 Frontend ICache，由其自己的取指侧机制处理。

#### 6.3 特殊 DCache 处理：`prefetch_i` 强制不发请求

最关键的逻辑在 [LoadUnit.scala:406](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:406>)：

```scala
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
io.dcache.req.bits.cmd := Mux(s0_sel_src.prf_rd,
  MemoryOpConstants.M_PFR,
  Mux(s0_sel_src.prf_wr, MemoryOpConstants.M_PFW, MemoryOpConstants.M_XRD)
)
io.dcache.req.bits.vaddr := s0_dcache_vaddr
io.dcache.req.bits.lqIdx := s0_sel_src.uop.lqIdx
```

`!s0_sel_src.prf_i` 是一个硬性门控。因此：

- 普通 load：`M_XRD`，进入 DCache；
- `prefetch.r`：`M_PFR`，可作为数据读预取请求进入 DCache；
- `prefetch.w`：`M_PFW`，可作为数据写预取请求进入 DCache；
- **`prefetch.i`：不论 `s0_valid` 是否为真，DCache `req.valid` 都为假。**

所以 `PREFETCH.I` 不会进入 DCache main pipe、DCache MissQueue、数据 refill、load replay、store-to-load forwarding 或 DCache bank conflict 路径。这是昆明湖对 instruction prefetch 和 data prefetch 的明确分工。

此外，普通 load fast wakeup 也显式排除所有 prefetch：[LoadUnit.scala:879](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:879>)：

```scala
io.wakeup.valid := s0_fire && ... &&
  (s0_src_valid_vec(int_iss_idx) && !s0_sel_src.prf && ...)
```

因此软件预取不会伪装成“有数据结果已经可旁路”的普通 load。

#### 6.4 专用 ICache 预取出口

`PREFETCH.I` 的真正效果由 [LoadUnit.scala:888](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:888>) 实现：

```scala
// prefetch.i(Zicbop)
io.ifetchPrefetch.valid := RegNext(
  s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
io.ifetchPrefetch.bits.vaddr := RegEnable(
  s0_out.vaddr, 0.U,
  s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
```

两个细节值得注意：

1. 使用 `RegNext`，所以 ICache 请求在 S0 选择后的下一拍输出；
2. 输出条件要求来源是 `int_iss_idx`，表示架构软件指令发射，而不是硬件预取/replay 等其他 LDU source。

该接口只带 `valid + vaddr`，没有 read data、byte mask 或 DCache command，充分体现它是“取指行提示”而非数据访存。

### 7. MemBlock、XSCore、Frontend：LDU 到 ICache 的跨模块连接

MemBlock IO 将指令预取定义为每条 LDU 一路 `ValidIO[SoftIfetchPrefetchBundle]`：[MemBlock.scala:311](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:311>)：

```scala
val ifetchPrefetch = Vec(LduCnt, ValidIO(new SoftIfetchPrefetchBundle))
```

然后将每条 LoadUnit 的专用输出直接连出：[MemBlock.scala:877](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:877>)：

```scala
// SoftPrefetch to frontend (prefetch.i)
loadUnits(i).io.ifetchPrefetch <> io.ifetchPrefetch(i)
```

与此同时，同一个 LoadUnit 的 DCache IO 仍按普通方式连接：

```scala
loadUnits(i).io.dcache <> dcache.io.lsu.load(i)
```

这两条并行连接说明特殊行为由 LoadUnit 内部的 `req.valid` 门控决定，而不是 MemBlock 在外部把该指令改投其他执行单元。

在 [XSCore.scala:134](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/XSCore.scala:134>)，MemBlock 的输出跨越 core 顶层接到 Frontend：

```scala
frontend.io.softPrefetch <> memBlock.io.ifetchPrefetch
```

Frontend 保留与 LDU 数量相同的输入向量，并把它直接交给 ICache：[Frontend.scala:83](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:83>)、[Frontend.scala:188](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:188>)：

```scala
val softPrefetch = Vec(backendParams.LduCnt,
  Flipped(Valid(new SoftIfetchPrefetchBundle)))
// ...
icache.io.softPrefetch <> io.softPrefetch
```

因此软件预取没有经过 FTQ 重新包装，也不需要经过 Decode/IFU 反馈；它以一条从后端直达 ICache 的旁路提示连接存在。

### 8. ICache：昆明湖对 `PREFETCH.I` 的专门支持

#### 8.1 单项软件预取缓冲

ICache 的接口定义明确注释其来源是 MemBlock：[ICache.scala:543](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:543>)：

```scala
// memblock
val softPrefetch: Vec[Valid[SoftIfetchPrefetchBundle]] =
  Vec(backendParams.LduCnt, Flipped(Valid(new SoftIfetchPrefetchBundle)))
```

ICache 随后使用 `softPrefetchValid` 与 `softPrefetch` 两个寄存器存下软件请求：[ICache.scala:662](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:662>)：

```scala
private val softPrefetchValid = RegInit(false.B)
private val softPrefetch = RegInit(0.U.asTypeOf(new IPrefetchReq))

when(io.softPrefetch.map(_.valid).reduce(_ || _)) {
  softPrefetchValid := true.B
  softPrefetch.fromSoftPrefetch(MuxCase(
    0.U.asTypeOf(new SoftIfetchPrefetchBundle),
    io.softPrefetch.map(req => req.valid -> req.bits)
  ))
}.elsewhen(prefetcher.io.req.fire) {
  softPrefetchValid := false.B
}
```

这段代码实现了以下语义：

- 任意 LDU 发来 `softPrefetch.valid` 时，ICache 捕获一条请求；
- 请求被 `prefetcher.io.req.fire` 消费时清除 valid；
- 它是一个**单项寄存器**，不是 FIFO。

源码的 FIXME 注释也明确说明当前实现的限制：若已有 pending 软件预取，新的请求会覆盖它；同一周期多个请求只会按 `MuxCase` 选中第一条。由此可见，昆明湖的实现假设 `prefetch.i` 频率较低，并以硬件成本较小的暂存器替代队列。

#### 8.2 高于 FTQ 硬件预取的优先级

ICache 将 FTQ 预测产生的预取包装成 `ftqPrefetch`，但软件预取享有更高优先级：[ICache.scala:681](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:681>)：

```scala
private val ftqPrefetch = WireInit(0.U.asTypeOf(new IPrefetchReq))
ftqPrefetch.fromFtqICacheInfo(io.ftqPrefetch.req.bits)

// software prefetch has higher priority
prefetcher.io.req.valid := softPrefetchValid || io.ftqPrefetch.req.valid
prefetcher.io.req.bits := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
io.ftqPrefetch.req.ready := prefetcher.io.req.ready && !softPrefetchValid
```

当 `softPrefetchValid=1` 时：

- `prefetcher.io.req.bits` 强制选择软件 `PREFETCH.I` 请求；
- FTQ 的 `ready` 被压低，避免预测器预取与软件请求竞争同一个 prefetcher 输入；
- 软件请求一旦 `fire`，寄存器在前述逻辑中被清空，FTQ 预取才可恢复。

这是 ICache 对架构显式 `PREFETCH.I` 的最重要特殊支持：它不是与硬件预测预取等权的普通 hint，而是被赋予优先调度权。

#### 8.3 从 ICache prefetcher 到 MissUnit

当 ICache prefetcher 判定需要下游取行时，其 MSHR 请求连到 ICache MissUnit：[ICache.scala:690](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:690>)：

```scala
missUnit.io.fetch_req <> mainPipe.io.mshr.req
missUnit.io.prefetch_req <> prefetcher.io.MSHRReq
missUnit.io.mem_grant <> bus.d
```

这里的 `prefetcher.io.MSHRReq` 是 `PREFETCH.I` 在 ICache miss 情形下能够继续走向 MissUnit、片外/下级存储系统、refill 和 meta/data array 更新的接口。是否真的进入该接口由 ICache prefetcher 的命中、重复请求、MSHR 资源和策略决定；但从静态代码可明确确认：**`PREFETCH.I` 的 miss 请求归属 ICache MissUnit，而绝不归属 DCache MissQueue。**

#### 8.4 软件预取的可观测保护/统计点

ICache 还为软件预取设置了三个专用性能事件：[ICache.scala:750](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:750>)：

```scala
XSPerfAccumulate("softPrefetch_drop_not_ready",
  io.softPrefetch.map(_.valid).reduce(_ || _) && softPrefetchValid &&
  !prefetcher.io.req.fire)
XSPerfAccumulate("softPrefetch_drop_multi_req",
  PopCount(io.softPrefetch.map(_.valid)) > 1.U)
XSPerfAccumulate("softPrefetch_block_ftq",
  softPrefetchValid && io.ftqPrefetch.req.valid)
```

它们分别衡量：前一条软件预取尚未发出时又有请求到来、同周期多个 LDU 同时发起软件预取、以及软件预取阻塞 FTQ 预取。这些计数器正对应单项缓冲和高优先级仲裁的设计风险。

### 9. Store Unit、StoreBuffer 与 DCache 为什么不参与

Decode 已将 `PREFETCH.I` 指为 `FuType.ldu`，而非 `FuType.stu`。MemBlock 对 StoreUnit 的接线是独立的 `[issueSta -> stu.io.stin]` 路径：[MemBlock.scala:1239](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1239>)：

```scala
for (i <- 0 until StaCnt) {
  val stu = storeUnits(i)
  stu.io.dcache <> dcache.io.lsu.sta(i)
  stu.io.stin <> io.ooo_to_mem.issueSta(i)
  stu.io.lsq <> lsq.io.sta.storeAddrIn(i)
  stu.io.tlb <> dtlb_st.head.requestor(i)
}
```

而 `PREFETCH.I` 的 issue 端口是 `issueLda`，故不会进入：

- StoreUnit 的地址生成、store address TLB/DCache 请求；
- StoreDataUnit 的数据写入路径；
- Store Queue 提交、StoreBuffer drain；
- DCache store request、store miss 或 coherence 写权限路径。

与之相对，`prefetch.w` 虽仍由 LDU 的 prefetch 分类驱动 DCache `M_PFW` 请求，但也不是 `PREFETCH.I` 的实现路径。`PREFETCH.I` 的“i”最终选择的是 ICache，而不是 store-side 硬件。

### 10. LoadUnit writeback、Backend writeback datapath、ROB Commit / Retire

即使它没有 GPR 结果，`PREFETCH.I` 仍需要完成并写回 ROB，以保证精确异常、flush 和 in-order retirement。LoadUnit 的通用 `ldout` 逻辑在 [LoadUnit.scala:1789](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1789>)：

```scala
val s3_ldout_valid = s3_mmio_req.valid ||
  s3_out.valid && RegNext(!s2_out.isvec && !s2_out.isFrmMisAlignBuf)
io.ldout.valid := s3_ldout_valid
io.ldout.bits := s3_ld_wb_meta
io.ldout.bits.uop.rfWen := s3_rfWen &&
  !io.ldout.bits.uop.exceptionVec.asUInt.orR
io.ldout.bits.isFromLoadUnit := true.B
```

`ldout` 传递 uop/ROB/异常完成信息；对于 `rd=x0` 的预取，`rfWen` 不会让它写入整数寄存器文件，但 ROB 仍得到“该 uop 已完成”的 writeback。

MemBlock 将 LDU 的输出汇入自身的 LDA writeback 端口，Backend 则把 Memory Exu 的 writeback 输入 writeback datapath：[Backend.scala:666](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:666>)：

```scala
wbDataPath.io.fromMemExu.flatten.zip(io.mem.writeBack).foreach {
  case (sink, source) =>
    sink.valid := source.valid
    source.ready := sink.ready
    sink.bits.data := VecInit(Seq.fill(sink.bits.params.wbPathNum)(source.bits.data))
    sink.bits.pdest := source.bits.uop.pdest
    sink.bits.robIdx := source.bits.uop.robIdx
    sink.bits.intWen.foreach(_ := source.bits.uop.rfWen)
    sink.bits.fpWen.foreach(_ := source.bits.uop.fpWen)
}
```

所以 writeback datapath 的 consumer 不只关心数据：`robIdx`、`intWen`、异常及 mem/debug 元数据也会送往 ROB。对 `PREFETCH.I`，`pdest=0`/`intWen=false` 使它不产生整数数据提交，但其 ROB 完成位会被设置。

BackendIO 把 LDA 的写回端口明确列入 memory writeback 序列：[Backend.scala:1055](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:1055>)：

```scala
private [backend] def writeBack: Seq[DecoupledIO[MemExuOutput]] = {
  writebackSta ++ writebackHyuLda ++ writebackHyuSta ++
    writebackLda ++ writebackVldu ++ writebackStd
}
```

ROB 只有在头部 uop 已完成且没有更老的阻塞条件时，才使对应 `commitValid` 生效。其提交侧会将 debug uop 的阶段时间、提交信息与 Difftest 状态统一处理；例如 [Rob.scala:1403](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1403>) 计算 dispatch、issue、writeback 到 commit 的延迟：

```scala
val dispatchLatency = commitDebugUop.map(uop =>
  uop.debugInfo.dispatchTime - uop.debugInfo.renameTime)
val issueLatency = commitDebugUop.map(uop =>
  uop.debugInfo.issueTime - uop.debugInfo.selectTime)
val executeLatency = commitDebugUop.map(uop =>
  uop.debugInfo.writebackTime - uop.debugInfo.issueTime)
val commitLatency = commitDebugUop.map(uop => timer - uop.debugInfo.writebackTime)
```

退休/Difftest 输出则由 [Rob.scala:1543](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1543>) 的 `commitValid && isCommit` 驱动：

```scala
difftest.valid := io.commits.commitValid(i) && io.commits.isCommit
difftest.rfwen := io.commits.commitValid(i) && commitInfo.rfWen &&
  basicDebug.ldest =/= 0.U
```

对 `PREFETCH.I`，第一行保证它是可见的已退休指令；第二行因为 `rd=x0` 保证不产生 GPR 写回记录。这正是“有 ROB/Commit 精确顺序、无 architectural register result”的实现。

### 11. 源码级结论

昆明湖的 `PREFETCH.I` 实现不是在前端直接插入请求，也不是把指令误当成 DCache 数据 load。它采用以下分层策略：

1. **后端统一性**：Decode 为 `ldu + prefetch_i`，Rename/Dispatch/Issue/ROB 均使用标准乱序 load uop 机制；
2. **LoadUnit 分流**：保留地址计算、LQ、ROB、flush/异常与完成上报，但用 `prf_i` 关闭 DCache request，标记 TLB 请求为 `no_translate`；
3. **前端专用通道**：LoadUnit 生成 `SoftIfetchPrefetchBundle`，经 MemBlock 和 XSCore 直接送给 Frontend ICache；
4. **ICache 特殊仲裁**：ICache 用单项 `softPrefetch` 缓冲接收请求，软件预取优先于 FTQ 预取，必要时由 ICache prefetcher 发给 ICache MissUnit；
5. **精确退休**：LoadUnit writeback 仍携带 ROB 完成信息，ROB 按程序序 commit；但 `rd=x0` 导致无 integer register writeback。

由此，`PREFETCH.I` 在微架构上是“**由后端 LDU 发起、由前端 ICache 实际执行、由 ROB 精确退休**”的跨前后端缓存提示指令。

## PREFETCH_I 演示程序

### 1. 程序目标与文件位置

演示程序位于：

```text
/home/yanyusong/cbo-kmhv2/nexus-am/apps/prefetch_i/
├── Makefile
└── prefetch_i.c
```

它故意组织为“**预取前数据访存 -> `PREFETCH.I` -> 预取后数据访存 -> 执行被预取代码**”四个阶段：

1. `touch_data(before_data, ...)` 以 64 B 步长对一组 `volatile` 数据进行写后读，形成可见的数据缓存访问；
2. 对 64 B 对齐、禁止内联的 `prefetched_code()` 函数地址执行 `prefetch.i`；
3. `touch_data(after_data, ...)` 和 `printf` 在调用目标函数前继续执行，给 ICache 预取留出处理窗口；
4. 调用 `prefetched_code()`，以真实控制流使用被预取的指令行，并打印校验值确认程序未被优化掉或异常终止。

`before_data`、`after_data` 都被声明为 `volatile`，避免编译器将循环删除或仅保留寄存器计算；函数的 `noinline` 和 `aligned(64)` 属性保证预取目标是独立、可识别的代码入口。

### 2. Makefile 与 `Zicbop` 构建选项

`Makefile` 如下：

```makefile
NAME = prefetch_i
SRCS = prefetch_i.c

MARCH = rv64gc_zba_zbb_zbc_zbs_zbkb_zbkc_zbkx_zknd_zkne_zknh_zkr_zksed_zksh_zkt_zicbop

include $(AM_HOME)/Makefile.app
```

关键点是 `MARCH` 末尾的 `zicbop`。GNU 汇编器只有在启用该扩展时才接受 `prefetch.i` 助记符；应用使用 Nexus-AM 的 `Makefile.app` 完成 AM 库链接、ELF 生成、反汇编输出与 `.bin` 镜像生成。

构建命令示例：

```bash
cd /home/yanyusong/cbo-kmhv2
source env.sh
cd nexus-am/apps/prefetch_i
make ARCH=riscv64-xs CROSS_COMPILE=riscv64-linux-gnu-
```

生成的仿真镜像为：

```text
build/prefetch_i-riscv64-xs.bin
```

### 3. 完整 C 程序

```c
#include <klib.h>

#define CACHE_LINE_BYTES 64
#define ARRAY_WORDS 1024

static volatile unsigned long before_data[ARRAY_WORDS]
  __attribute__((aligned(CACHE_LINE_BYTES)));
static volatile unsigned long after_data[ARRAY_WORDS]
  __attribute__((aligned(CACHE_LINE_BYTES)));

static unsigned long touch_data(volatile unsigned long *data, unsigned long seed)
{
  unsigned long sum = seed;

  for (int index = 0; index < ARRAY_WORDS; index += 8) {
    data[index] = seed + (unsigned long)index;
    sum += data[index];
  }

  return sum;
}

__attribute__((noinline, aligned(CACHE_LINE_BYTES)))
static unsigned long prefetched_code(unsigned long value)
{
  return (value << 1) ^ 0x5a5a5a5aUL;
}

static void prefetch_instruction_line(const void *address)
{
  asm volatile("prefetch.i 0(%0)" : : "r"(address) : "memory");
}

int main(void)
{
  unsigned long sum;

  printf("PREFETCH.I demo: start prefetch_i scenario\n");
  printf("before PREFETCH.I: touch data cache lines\n");
  sum = touch_data(before_data, 0x1000UL);

  printf("issue PREFETCH.I for prefetched_code at %p\n", prefetched_code);
  prefetch_instruction_line(prefetched_code);

  printf("after PREFETCH.I: continue data accesses before code use\n");
  sum += touch_data(after_data, 0x2000UL);
  sum = prefetched_code(sum);

  printf("PREFETCH.I demo: target executed, checksum = %lx\n", sum);
  return 0;
}
```

### 4. 关键代码说明

| 代码 | 作用 |
|---|---|
| `CACHE_LINE_BYTES=64` | 数据数组与目标函数都按 64 B 对齐，便于将场景与一个明确的缓存行关联。|
| `volatile unsigned long ...` | 强制保留内存 store 与随后 load，避免循环被优化为纯算术。|
| `index += 8` | 每个 `unsigned long` 为 8 B，故循环每次跨越 64 B，触及一个新的数据缓存行。|
| `noinline` | 防止 `prefetched_code()` 被折叠到 `main()`，确保存在可预取、可调用的独立代码地址。|
| `aligned(64)` | 让 `prefetched_code()` 从缓存行边界开始，预取地址与函数入口一致。|
| `asm volatile` | 禁止编译器删除、合并或将该 hint 移出指定位置。|
| `"memory"` clobber | 将内联汇编视为内存屏障，防止编译器把相邻内存访问跨越该汇编语句重排。|

`PREFETCH.I` 是提示指令，程序语义不依赖其是否最终填入 ICache；因此程序仍会直接调用目标函数。该设计适合验证微架构是否识别和处理该指令，而不会把“性能优化是否发生”误当成“功能正确性是否成立”。

### 5. 反汇编与目标地址

构建得到的 `build/prefetch_i-riscv64-xs.txt` 中，目标函数与关键 `main` 片段如下：

```text
0000000080000140 <prefetched_code>:
    80000140:  5a5a67b7            lui     a5,0x5a5a6
    80000144:  a5a78793            addi    a5,a5,-1446
    80000148:  0506                slli    a0,a0,0x1
    8000014a:  8d3d                xor     a0,a0,a5
    8000014c:  8082                ret

0000000080000182 <main>:
    ...
    800001a8:  00003717            auipc   a4,0x3
    800001ac:  5d870713            addi    a4,a4,1496 # 80003780 <before_data>
    800001b0:  e31c                sd      a5,0(a4)
    800001b2:  6314                ld      a3,0(a4)
    800001b6:  04070713            addi    a4,a4,64
    ...
    800001c0:  00000597            auipc   a1,0x0
    800001c4:  f8058593            addi    a1,a1,-128 # 80000140 <prefetched_code>
    ...
    800001d4:  00000797            auipc   a5,0x0
    800001d8:  f6c78793            addi    a5,a5,-148 # 80000140 <prefetched_code>
    800001dc:  0007e013            prefetch.i 0(a5)
    ...
    800001f4:  00001717            auipc   a4,0x1
    800001f8:  58c70713            addi    a4,a4,1420 # 80001780 <after_data>
    800001fc:  e31c                sd      a5,0(a4)
    800001fe:  6314                ld      a3,0(a4)
    80000202:  04070713            addi    a4,a4,64
    ...
    8000020e:  f33ff0ef            jal     80000140 <prefetched_code>
```

反汇编确认了以下对应关系：

| 地址 / 指令 | 对应 C 语句 | 说明 |
|---|---|---|
| `0x80000140 <prefetched_code>` | `prefetched_code()` | 被预取的函数入口，按 64 B 对齐。|
| `0x800001b0: sd`、`0x800001b2: ld` | 第一次 `touch_data()` | 对 `before_data` 执行写后读。|
| `0x800001b6: addi ...,64` | `index += 8` | 每轮循环跨一个 64 B 数据行。|
| `0x800001d4/0x800001d8` | `prefetched_code` 作为参数 | 用 PC-relative 指令把目标地址写入 `a5`。|
| `0x800001dc: 0007e013` | 内联汇编 | 机器码为 `0x0007e013`，助记符准确反汇编为 `prefetch.i 0(a5)`。|
| `0x800001fc: sd`、`0x800001fe: ld` | 第二次 `touch_data()` | 对 `after_data` 执行预取后的写后读访问。|
| `0x8000020e: jal 0x80000140` | `prefetched_code(sum)` | 真实跳转到被预取的指令地址。|

特别地，`prefetch.i` 的立即数为 0，基址寄存器为 `a5`，所以预取地址精确等于 `a5=0x80000140`。该地址与随后 `jal` 的目标一致，形成“先提示、后使用”的演示关系。

### 6. 场景执行顺序与预期观察点

```text
打印开始信息
  -> before_data: 每 64 B 的 store + load
  -> 打印预取目标函数地址
  -> prefetch.i 0(a5), a5 = &prefetched_code
  -> after_data: 每 64 B 的 store + load
  -> jal prefetched_code
  -> 打印 checksum，正常返回
```

该安排同时覆盖了三个场景要求：

1. **预取前的缓存相关操作**：`before_data` 循环制造连续、跨缓存行的读写访问；
2. **插入架构 `PREFETCH.I` 指令**：内联汇编与反汇编共同确认不是伪代码或编译器内建替代；
3. **预取后的独立工作与目标使用**：`after_data` 循环及 `printf` 延后目标函数调用，最后以 `jal` 真正消费目标代码行。

程序的预期串口输出为：

```text
PREFETCH.I demo: start prefetch_i scenario
before PREFETCH.I: touch data cache lines
issue PREFETCH.I for prefetched_code at 0000000080000140
after PREFETCH.I: continue data accesses before code use
PREFETCH.I demo: target executed, checksum = 5a6e025a
```

其中 checksum 只用于证明预取后的访存、函数调用和返回值计算均执行完成；它不是缓存命中率或预取成功率的性能指标。缓存行为和 `PREFETCH.I` 的微架构路径应由后续“波形图分析”章节中的信号证据判断。

## 波形图分析

### 方法、对象与结论摘要

本节使用 wavekit 开源库 `/home/yanyusong/wavekit` 的 `FstReader` 解析全波形文件
`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-16-25-24.fst`。时钟选择
`TOP.clock`，以**上升沿**采样；下文的 `cycle` 与 `time` 均为 wavekit 采样数组中读取到的绝对值。

分析对象是应用 ELF 反汇编中的：

```text
PC=0x800001dc, instruction=0x0007e013, prefetch.i 0(a5)
a5=0x0000000080000140 <prefetched_code>
```

程序实际打印出目标地址 `0x80000140` 并正常结束。波形也在 Decode、Rename、Dispatch、LDA issue、LoadUnit、ICache、ROB writeback/commit 中观测到同一条指令，因此反汇编、程序和波形一致。

**核心结论**：此 `PREFETCH.I` 被译码为 `LSUOpType.prefetch_i`，作为标量 Load（LDU）类微操作分配 `ROB=(flag=1,value=35)` 和 `LQ=(flag=1,value=40)`，由 `issueLda_2` 发到 `LoadUnit_2`。LDU 的 `s0_sel_src.prf_i=1` 会：

1. 让 DTLB 请求带 `kill=1`、`no_translate=1`，即不等待普通数据地址翻译；
2. 强制 `io.dcache.req.valid=0`，所以不访问 DCache、没有 DCache miss/replay/nack；
3. 在下一周期产生 `ifetchPrefetch.valid=1, vaddr=0x80000140`，经 MemBlock 送到前端 ICache；
4. ICache 在 cycle 22785 以 `valid=1 && ready=1` 接收软件预取。该块没有继续产生 ICache MSHR 请求（`MSHRReq.valid=0`），即命中/已覆盖的指令预取路径；
5. LDU 仍在 cycle 22787 对 ROB 回报完成，地址字段为 `vaddr=0x80000140`、`paddr=0`、异常位均为 0；最终 ROB 在 cycle 23656 正常提交该指令。

### 全局逐周期时间线

| cycle | time | 模块 / 边界 | valid / ready / fire | 本指令身份与有效载荷 | 含义 |
|---:|---:|---|---|---|---|
| 22773 | 45546 | Decode -> Rename lane 2 | `1 / 1 / 1` | `pc=0x800001dc`，`instr=0x0007e013`，FTQ=`(1,23)`、offset=`4`，`lsrc0=15(a5)`，`ldest=0` | 已由前端 FTQ/IBuffer 流送入 Rename；`rd=x0`，不申请架构目的寄存器。|
| 22773 | 45546 | Rename -> Dispatch lane 2 | `1 / 1 / 1` | ROB=`(1,35)`，`psrc0=166`，`pdest=0`，`fuOpType=0x08` | Rename 将逻辑源寄存器 `a5` 映射到物理寄存器 166；无目的物理寄存器分配。|
| 22774 | 45548 | Dispatch fromRename lane 2 | `1 / 1 / 1` | `pc/instr/ROB/psrc0/fuOpType` 均保持为 `0x800001dc / 0x0007e013 / 35 / 166 / 0x08` | Dispatch 接收该 LDU 类操作并为其准备 LSQ/IQ 元数据。|
| 22774--22782 | 45548--45564 | LDU issue queue 等待 | 未在外部 issue 端口出现 ROB 35 | `issueLda_2.ready=1`；目标直到 cycle 22783 才被选择 | 该指令在队列中驻留 9 个 cycle。导出的波形未包含该 entry 的选择仲裁 / source-ready 位，故不能把等待归因到某一个未导出的仲裁条件。|
| 22783 | 45566 | Backend -> MemBlock `issueLda_2` | `1 / 1 / 1` | ROB=35，LQ=40，`fuOpType=0x08`，`src0=0x80000140` | 标量 LDA issue 端口成功把地址源操作数送入 `LoadUnit_2.io_ldin`。|
| 22783 | 45566 | `LoadUnit_2` S0 | `s0_valid=1`，`s0_fire=1` | `prf=1`，`prf_i=1`，ROB=35，LQ=40，`s0_out_vaddr=0x80000140` | 特殊 `PREFETCH.I` 执行路径被选择。|
| 22783 | 45566 | LDU -> DTLB | `req.valid=1`，但 `kill=1`，`no_translate=1` | `debug.robIdx=35` | 波形中有请求 bundle，但该请求被标记取消/免翻译；没有普通 DTLB 请求的有效执行。|
| 22783 | 45566 | LDU -> DCache | `req.valid=0`，`ready=1` | `vaddr=0x80000140`，`resp.valid=0`，`s2_mq_nack=0` | `prf_i` 显式禁止 DCache 请求，故不走数据缓存 load/miss/replay 路径。|
| 22784 | 45568 | LDU -> MemBlock -> Frontend | `LoadUnit_2.ifetchPrefetch.valid=1` | `vaddr=0x80000140`；`frontend.io_softPrefetch_2.valid=1`、地址相同 | S0 的 `RegNext` 产生软件指令预取请求。|
| 22785 | 45570 | ICache 软件预取缓冲 -> prefetcher | `req.valid=1`，`req.ready=1`，`fire=1` | `isSoftPrefetch=1`，行起点 `0x80000140`，next line `0x80000180` | ICache 接收软件预取，优先级高于 FTQ 预取。|
| 22786--22788 | 45572--45576 | ICache prefetcher 内部 | `s1_isSoftPrefetch=1`，随后 `s2_isSoftPrefetch=1` | 同一软件请求的内部流水标记 | 请求在 ICache prefetch 流水级中继续可见。|
| 22780--22950 | 45560--45900 | ICache MSHR / redirect | `MSHRReq.valid=0`、`MSHRReq.ready=1`；两个 redirect valid 均为 0 | 无下游 block 地址请求、无恢复 | 软件预取被接收但无需 refill；没有异常、访存违例或控制流恢复。|
| 22787 | 45574 | LDU -> ROB writeback 22 | `valid=1` | ROB=35，`vaddr=0x80000140`，`paddr=0`，exception bits 0--4 均为 0 | 该提示指令完成并唤醒 ROB；其完成不依赖 DCache 返回数据。|
| 23656 | 47312 | ROB commit lane 3 | `commitValid=1` | `pc=0x800001dc`，`instr=0x0007e013`，ROB=35 | 正常退休；没有整数/浮点/向量寄存器写回。|

### 1. Decode：从指令编码到 LDU 功能类型

#### 波形证据

在 cycle 22773，`rename.io_in_2` 是 Decode 到 Rename 的 Decoupled 边界：

```text
valid=1, ready=1, fire=1
pc=0x800001dc, instr=0x0007e013
ftqPtr.flag=1, ftqPtr.value=23, ftqOffset=4
lsrc0=15 (a5), ldest=0, fuOpType=0x08
```

这给出了本条指令在后端的第一个稳定锚点。FTQ 编号和 offset 是前端预测/取指携带的定位信息；此指令本身不是分支，波形中没有对它产生的预测重定向。`lsrc0=15` 对应 RISC-V ABI 中的 `a5`，与反汇编的 `prefetch.i 0(a5)` 一致；`ldest=0` 对应 `rd=x0`，因此它没有 architectural destination。

Decode lane 2 的 `isSoftPrefetch=1`、`isPreI=1` 与 PC `0x800001dc` 同时出现，说明并非普通 `OP-IMM`，而是香山识别到的 software prefetch 编码。

#### Chisel 根因

[DecodeUnit.scala:1102](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1102>) 使用 opcode、funct3、rd 和 rs2 区分这三类软件预取：

```scala
val isSoftPrefetch = inst.OPCODE === BitPat("b0010011") &&
  inst.FUNCT3 === BitPat("b110") && inst.RD === 0.U
val isPreW = isSoftPrefetch && inst.RS2 === 3.U(5.W)
val isPreR = isSoftPrefetch && inst.RS2 === 1.U(5.W)
val isPreI = isSoftPrefetch && inst.RS2 === 0.U(5.W)
```

[DecodeUnit.scala:1132](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1132>) 与 [DecodeUnit.scala:1170](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:1170>) 将其路由到 Load Unit：

```scala
}.elsewhen (isPreW || isPreR || isPreI) {
  decodedInst.selImm := SelImm.IMM_S
  decodedInst.fuType := FuType.ldu.U
  decodedInst.canRobCompress := false.B
}
// ...
isPreI -> LSUOpType.prefetch_i,
```

因此波形里的 `fuOpType=0x08` 是此构建中 `prefetch_i` 的实际编码；其 consumer 是后续的 Dispatch、LDA issue queue 和 LoadUnit，而不是 StoreUnit。

### 2. Rename：ROB 身份、物理源寄存器和无目的寄存器

cycle 22773，Rename 输入 lane 2 与输出 lane 2 都满足 `valid && ready`。输入 `lsrc0=15` 被映射为输出 `psrc0=166`；输出 `pdest=0`。同时 Rename 分配：

```text
robIdx.flag=1, robIdx.value=35
fuOpType=0x08
```

从这里开始，分析以 `ROB=35` 而不是仅以 PC 追踪该指令。`pdest=0` 并不表示异常，而是本条 `rd=x0` 的正常结果：预取是提示指令，不产生 GPR 值；但它仍需要 ROB 项记录完成、异常和提交次序。

[Rename.scala:628](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala:628>) 展示了普通有目的寄存器指令才更新 rename table；配合 `rd=x0`/`pdest=0` 的波形，可知此指令不会分配或写回整数物理目的寄存器。

### 3. Dispatch、LSQ 与 Issue：为何它仍有 LQ 项

cycle 22774，`dispatch.io_fromRename_2` 显示：

```text
valid=1, ready=1, fire=1
pc=0x800001dc, instr=0x0007e013, ROB=35
psrc0=166, pdest=0, fuOpType=0x08
firedVec_2=1, allowDispatch_2=1
```

这证明 Dispatch 没有因 ROB/LSQ/IQ backpressure 阻塞该指令。之后它获得 `LQ=40`，该编号在 cycle 22783 的 `issueLda_2`、`LoadUnit_2.io_ldin` 和 `LoadUnit_2.s0_sel_src_uop_lqIdx` 中一致出现。换言之，`PREFETCH.I` 在资源归类上是 load-like 操作，使用 LQ 身份支持顺序、flush 和调试关联；但它并不因此发起 DCache 数据 load。

[NewDispatch.scala:688](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:688>) 的 LSQ 分配逻辑为 scalar load 设置一项分配请求：

```scala
when(!io.fromRename(i).fire) {
  enqLsqIO.needAlloc(i) := 0.U
}.elsewhen(isLoadVec(i) || isVLoadVec(i)) {
  enqLsqIO.needAlloc(i) := 1.U // load | vload
}
enqLsqIO.req(i).valid := io.fromRename(i).fire && !isAMOVec(i) &&
  !isSegment(i) && !isfofFixVlUop(i)
enqLsqIO.req(i).bits := io.fromRename(i).bits
```

cycle 22774 至 22782，目标 ROB 35 未出现在外部 `issueLda_2` 发射端口；cycle 22783 才出现。因此它在 load issue queue 内等待 9 cycle。`issueLda_2.ready` 在最终发射周期为 1，且 `LoadUnit_2.io_ldin.ready=1`。本 FST 没有把该 IQ entry 的 select/grant/source-ready 信号以可用的 ROB 标记导出，不能可靠地将这 9 cycle 归因到某个未导出的仲裁条件；报告不把它误报为 DCache、TLB 或 LSQ 满导致的 stall。

### 4. Issue -> MemBlock -> LoadUnit：LDA2 端口和特殊 S0 控制

cycle 22783 是后端执行入口的关键握手：

```text
backend.io_mem_issueLda_2.valid=1
backend.io_mem_issueLda_2.ready=1
uop.robIdx=35, uop.lqIdx=40, uop.fuOpType=0x08
src[0]=0x0000000080000140

LoadUnit_2.io_ldin.valid=1
LoadUnit_2.io_ldin.ready=1
```

`issueLda_2` 的 producer 是后端 load issue queue，consumer 是 MemBlock 内的 `LoadUnit_2`。MemBlock 使用 LDA 类端口连接 LDU；从上层连接可见该端口跨 Backend/MemBlock 边界：

[XSCore.scala:217](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/XSCore.scala:217>)：

```scala
memBlock.io.ooo_to_mem.issueLda <> backend.io.mem.issueLda
```

在同一个采样周期，LoadUnit 的 S0 信号为：

```text
s0_valid=1, s0_fire=1
s0_sel_src.prf=1, s0_sel_src.prf_i=1
s0_sel_src_uop_robIdx=35, s0_sel_src_uop_lqIdx=40
s0_out_vaddr=0x80000140, s0_dcache_vaddr=0x80000140
```

`s0_fire` 说明该 uop 已被 S0 source arbiter 选中且通过 S0。`prf` 表示所有 software prefetch 类操作，`prf_i` 是仅对 `PREFETCH.I` 的细分位。地址来自物理源寄存器 `psrc0=166` 中的值再加 S 型立即数 0，因此结果仍是 `0x80000140`。

[LoadUnit.scala:635](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:635>) 定义从整数 issue source 建立的 prefetch 分类：

```scala
out.prf    := LSUOpType.isPrefetch(src.uop.fuOpType)
out.prf_rd := src.uop.fuOpType === LSUOpType.prefetch_r
out.prf_wr := src.uop.fuOpType === LSUOpType.prefetch_w
out.prf_i  := src.uop.fuOpType === LSUOpType.prefetch_i
```

### 5. LoadUnit 对 PREFETCH.I 的特殊处理

#### 5.1 DTLB：请求 bundle 存在，但被标记为不翻译

cycle 22783 的 LDU2 波形：

```text
s0_tlb_valid=1, io_tlb_req.valid=1
io_tlb_req.bits.kill=1
io_tlb_req.bits.no_translate=1
```

这不是一个正常会等待 translation response 的 load。Chisel 中 `prf_i` 被列入 `s0_tlb_no_query`，随后 `kill` 和 `no_translate` 都由该条件驱动：

[LoadUnit.scala:338](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:338>)：

```scala
val s0_tlb_no_query = s0_hw_prf_select || s0_sel_src.prf_i ||
  s0_src_select_vec(fast_rep_idx) || s0_src_select_vec(mmio_idx) ||
  s0_src_select_vec(nc_idx)
// ...
io.tlb.req.bits.kill := s0_kill || s0_tlb_no_query
io.tlb.req.bits.no_translate := s0_tlb_no_query
```

因此 I-cache 软件预取不把 LDU DTLB 的 paddr 当作其下游地址；writeback 中 `debug_paddr=0` 与这个特性一致，不是物理地址翻译失败。

#### 5.2 DCache：显式绕开，不会形成 data load

同周期：

```text
io_dcache_req.valid=0, io_dcache_req.ready=1
io_dcache_resp.valid=0, io_dcache_s2_mq_nack=0
```

即 DCache 有能力接收请求，但本条指令根本没有发出请求。根因是：

[LoadUnit.scala:406](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:406>)：

```scala
io.dcache.req.valid := s0_valid && !s0_sel_src.prf_i && !s0_nc_with_data
```

`s0_valid=1` 但 `prf_i=1`，所以整个布尔式为 0。相比之下，`prefetch.r`/`prefetch.w` 会按 `prf_rd`/`prf_wr` 编码成 `M_PFR`/`M_PFW`；`prefetch.i` 专门被排除在 DCache 外。这也解释了：

- 没有 DCache load response、miss request、MSHR、replay、store-to-load forwarding 或 bank-conflict 事件；
- `LQ=40` 仅是 load-like uop 的身份/顺序资源，而不是一次 DCache 数据读取的结果载体；
- `s0_wakeup.valid` 的普通整数 load 分支要求 `!s0_sel_src.prf`，因此本条指令不产生普通 load fast wakeup。

#### 5.3 ICache 软件预取输出

[LoadUnit.scala:888](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:888>) 是 `PREFETCH.I` 的直接出口：

```scala
io.ifetchPrefetch.valid := RegNext(
  s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
io.ifetchPrefetch.bits.vaddr := RegEnable(
  s0_out.vaddr, 0.U,
  s0_src_select_vec(int_iss_idx) && s0_sel_src.prf_i)
```

因此 cycle 22783 的整数 issue / `prf_i` 条件在 cycle 22784 变为：

```text
LoadUnit_2.io_ifetchPrefetch.valid=1
LoadUnit_2.io_ifetchPrefetch.bits.vaddr=0x80000140
memBlock.io_ifetchPrefetch_2.valid=1
frontend.io_softPrefetch_2.valid=1
frontend.io_softPrefetch_2.bits.vaddr=0x80000140
```

这是本场景最重要的 producer -> signal -> consumer 链路：

```text
issueLda_2 (ROB 35, LQ 40)
  -> LoadUnit_2 S0(prf_i)
  -> LoadUnit_2.io_ifetchPrefetch
  -> MemBlock.io_ifetchPrefetch_2
  -> Frontend.io_softPrefetch_2
  -> frontend.inner_icache.io_softPrefetch_2
  -> ICache.prefetcher.io_req
```

### 6. MemBlock 与 Store Unit：为何 Store 路径完全不参与

`PREFETCH.I` 在 Decode 被指定为 `FuType.ldu`，故 MemBlock 把它送至 LDU 而不是 store address/data 单元。波形窗口 cycle 22770--22789 中检查：

```text
io_mem_issueSta_0 / io_mem_issueSta_1: 无 valid 且 ROB=35 的事务
io_mem_issueStd_0 / io_mem_issueStd_1: 无 valid 且 ROB=35 的事务
```

因此它没有进入 `StoreUnit.stin`、没有 store-address 生成、没有 store-data 写入、没有 SQ 分配/commit，也不会训练 store-side prefetch 或进入 StoreBuffer。

MemBlock 中 StoreUnit 的输入仅由 `issueSta` 驱动：

[MemBlock.scala:1239](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1239>)：

```scala
for (i <- 0 until StaCnt) {
  val stu = storeUnits(i)
  stu.io.dcache <> dcache.io.lsu.sta(i)
  stu.io.stin <> io.ooo_to_mem.issueSta(i)
  stu.io.lsq <> lsq.io.sta.storeAddrIn(i)
  stu.io.tlb <> dtlb_st.head.requestor(i)
}
```

这与波形的“ROB 35 只出现在 `issueLda_2`/`LoadUnit_2`，从未出现在 `issueSta`/`issueStd`”一致。这里的 Store Unit 并不是被 stall，而是根据功能类型根本不是该指令的数据通路 consumer。

### 7. Frontend ICache：软件预取缓冲、握手和未发起 MSHR

在 cycle 22784，ICache 中软件预取寄存器尚未置位：

```text
softPrefetchValid=0, prefetcher.io_req.valid=0
```

cycle 22785，来自 LDU2 的请求被写进该寄存器并优先选择：

```text
softPrefetchValid=1
softPrefetch_startAddr=0x80000140
softPrefetch_nextlineStart=0x80000180
prefetcher.io_req.valid=1
prefetcher.io_req.ready=1
prefetcher.io_req.bits.isSoftPrefetch=1
fire=1
```

这正是一个无反压的软件预取接口传输。cycle 22786 `softPrefetchValid` 清零；随后 `s1_isSoftPrefetch=1`，cycle 22788 开始 `s2_isSoftPrefetch=1`，说明请求进入 ICache prefetcher 的内部处理级。这里 `softPrefetchValid` 是可观察到的请求缓冲状态寄存器；FST 没有导出一个可映射名称的 ICache 软件预取 FSM 枚举，故不能臆造额外 FSM state 名称。

ICache Chisel 首先把任一路 `io.softPrefetch` 存入一个单项寄存器：

[ICache.scala:665](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:665>)：

```scala
when(io.softPrefetch.map(_.valid).reduce(_ || _)) {
  softPrefetchValid := true.B
  softPrefetch.fromSoftPrefetch(MuxCase(
    0.U.asTypeOf(new SoftIfetchPrefetchBundle),
    io.softPrefetch.map(req => req.valid -> req.bits)
  ))
}.elsewhen(prefetcher.io.req.fire) {
  softPrefetchValid := false.B
}
```

随后以 software prefetch 优先于 FTQ prefetch 的方式送入 prefetcher：

[ICache.scala:684](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:684>)：

```scala
prefetcher.io.req.valid := softPrefetchValid || io.ftqPrefetch.req.valid
prefetcher.io.req.bits := Mux(softPrefetchValid, softPrefetch, ftqPrefetch)
io.ftqPrefetch.req.ready := prefetcher.io.req.ready && !softPrefetchValid
```

在 cycle 22780--22950，`prefetcher.io_MSHRReq.valid=0` 且 `ready=1`。这严格表示该软件请求**没有**从 ICache prefetcher 发出下游 MSHR 请求，而不是 “MSHR 满导致请求被阻塞”。仿真性能计数也对应：`prefetch_req_receive_sw=1`，`prefetch_req_send_sw=0`，`softPrefetch_drop_not_ready=0`，`softPrefetch_drop_multi_req=0`，`softPrefetch_block_ftq=0`。

因此这次运行验证的是 ICache 已覆盖/命中的软件预取路径。若要观察 ICache `missUnit.io.prefetch_req` 的后续总线事务，需要选择启动时未被取过的目标指令行。

### 8. Writeback、Commit 与架构态

cycle 22787，ROB 的 writeback port 22 收到：

```text
io_writeback_22.valid=1
robIdx=35
debug_vaddr=0x80000140
debug_paddr=0x000000000000
exceptionVec[0..4]=0
```

`paddr=0` 与前述 `no_translate` 一致；异常位为 0 证明该预取没有产生 load page fault、access fault 等异常。该指令无数据读取结果、`rd=x0`，所以不存在 GPR/FPR/vector write data 的架构变化。

LoadUnit 的常规 `ldout` writeback 接口负责把 uop 和异常信息送到后端；其 `rfWen` 受目的寄存器与异常控制：

[LoadUnit.scala:1789](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:1789>)：

```scala
val s3_ldout_valid = s3_mmio_req.valid ||
  s3_out.valid && RegNext(!s2_out.isvec && !s2_out.isFrmMisAlignBuf)
io.ldout.valid := s3_ldout_valid
io.ldout.bits := s3_ld_wb_meta
io.ldout.bits.uop.rfWen := s3_rfWen && !io.ldout.bits.uop.exceptionVec.asUInt.orR
io.ldout.bits.isFromLoadUnit := true.B
```

最终 cycle 23656，ROB commit lane 3 为：

```text
commitValid=1, PC=0x800001dc, instr=0x0007e013, ROB=35
```

ROB 的 difftest 提交接口由 `commitValid && isCommit` 驱动；对于本条 `rd=x0` 指令，`rfwen` 不会置位：

[Rob.scala:1543](</home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1543>)：

```scala
difftest.valid := io.commits.commitValid(i) && io.commits.isCommit
difftest.rfwen := io.commits.commitValid(i) && commitInfo.rfWen &&
  basicDebug.ldest =/= 0.U
```

### 9. Redirect、异常、Bubble 与状态总结

#### Redirect / flush / exception

在执行、ICache 接收、writeback 周围检查：

```text
backend.io_frontend_toFtq_redirect.valid=0
backend.io_mem_redirect.valid=0
LoadUnit_2.io_dcache_s2_mq_nack=0
ROB writeback exceptionVec[0..4]=0
```

所以 `PREFETCH.I` 没有触发 branch redirect、load replay、访存违例恢复、DCache nack、异常或 trap。它最后正常在 ROB 35 提交。

#### Bubble / stall 判定

| 区间 | 接口 | 观察 | 判定 |
|---|---|---|---|
| 22773 | Decode->Rename | `valid=1, ready=1, fire=1` | 无 Decode/Rename backpressure。|
| 22774 | Rename->Dispatch | `valid=1, ready=1, firedVec_2=1, allowDispatch_2=1` | 无 ROB/LSQ dispatch 阻塞。|
| 22774--22782 | LDU IQ | 目标尚未到 `issueLda_2` | 9-cycle queue residence；FST 无 ROB-tagged select/grant/source-ready，原因保持“未解析”，不归因给 DCache/DTLB。|
| 22783 | issueLda_2->LDU2 | `valid=1, ready=1, fire=1` | 无执行入口阻塞。|
| 22783 | LDU->DCache | `ready=1` 但 `valid=0` | 这是 `prf_i` 的设计性绕开，不是 DCache backpressure。|
| 22785 | ICache req | `valid=1, ready=1, fire=1` | 无软件预取缓冲或 prefetcher 反压。|
| 22780--22950 | ICache MSHR | `valid=0, ready=1` | 没有 refill 请求；不是 MSHR full。|

#### 可观测状态寄存器 / 流水标记

| 模块 | 信号 | cycle / 值 | 作用 |
|---|---|---|---|
| LoadUnit_2 S0 | `s0_valid`, `s0_fire` | 22783 / `1,1` | 当前 LDU 流水级接收并执行本条 uop。|
| ICache | `softPrefetchValid` | 22785 / `1`，22786 / `0` | 单项软件预取缓冲已装入、随后因 `req.fire` 被消费。|
| ICache prefetcher | `s1_isSoftPrefetch` | 22786 起 / `1` | S1 内的软件预取标签。|
| ICache prefetcher | `s2_isSoftPrefetch` | 22788 起 / `1` | S2 内的软件预取标签。|
| StoreUnit | 无 ROB=35 的输入 | 22770--22789 | 本指令不进入 store address/data 状态机。|

### 10. 本条指令的完整数据/控制流

```text
FTQ(23, offset 4)
  -> Decode lane 2: isSoftPrefetch=1, isPreI=1
  -> Rename lane 2: a5(lsrc=15) -> psrc=166, ROB=35, pdest=0
  -> Dispatch lane 2: fire, LQ allocation -> LQ=40
  -> LDU issue queue
  -> backend.io_mem_issueLda_2: src0=0x80000140, ROB=35, LQ=40
  -> MemBlock.LoadUnit_2 S0: prf=1, prf_i=1
      -> TLB request marked kill/no_translate
      -> DCache req.valid forced low
      -> RegNext ifetchPrefetch(vaddr=0x80000140)
  -> Frontend softPrefetch_2
  -> ICache softPrefetchValid
  -> ICache prefetcher.req fire (software priority)
  -> no MSHR request in this run
  -> LoadUnit writeback: ROB=35, vaddr=0x80000140, paddr=0, no exception
  -> ROB commit lane 3: PC=0x800001dc, instr=0x0007e013
```

该路径解释了 `PREFETCH.I` 与 `PREFETCH.R/W`、普通 load、store 的本质差异：它复用 LDU 的重命名、调度、LQ、ROB 完成机制，以保持乱序执行和异常/flush 管理的一致性；但在 LDU S0 通过 `prf_i` 明确切断 DCache 和普通 DTLB 数据访问，将控制流转化为发送给前端 ICache 的软件指令预取请求。
