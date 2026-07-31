# 香山昆明湖执行 CBO Inval 指令的流程分析

## CBO Inval 指令介绍

### 这条指令是什么

`cbo.inval`（Cache Block Invalidate）是 RISC-V `Zicbom`（Cache-Block Management Operations）扩展定义的缓存块管理指令。它没有目的寄存器和返回值，唯一的操作数是地址；汇编常写作 `cbo.inval 0(a0)`，其中 `a0` 是指令编码中的 `rs1`。该指令选择包含 `rs1` 有效地址的一个完整缓存块，并在执行 hart 可访问的整组一致性缓存中对该块执行 invalidate 操作。

invalidate 的语义是释放该缓存块的缓存副本。对于 `Zicbom`，这会使非一致 agent 在与 CPU 的公共可见点之前完成的 store 对 CPU 一致性域可见：CPU 后续重新 load 该块时，不应继续使用此前已经被释放的旧副本。`cbo.inval` 因而特别适用于“设备写、CPU 读”的数据流，但它**不负责写回 CPU 的脏数据**；若 CPU 先前也修改过该块，直接 inval 可能使内存中暴露陈旧内容。需要先写回数据时应使用 `cbo.clean` 或 `cbo.flush`。

与其他 `Zicbom` 指令相同，操作粒度是整个缓存块，块大小由实现决定并须由软件发现。`rs1` 不必按块大小对齐，硬件会以它所在的块为目标；若发生页故障或访问故障，故障虚拟地址仍是原始 `rs1`，而不是块首地址。汇编的 `offset` 可省略，但若给出则必须为 `0`。执行环境可以通过 CBO invalidate enable（`CBIE`）控制低特权级的实际行为：它可以禁止该指令、允许真正的 inval，或将该指令提升为 flush。

### 这条指令会做什么

`cbo.inval` 的软件可见语义可概括为：

```text
block = 包含有效地址 rs1 的缓存块
从执行 hart 可访问的 coherent caches 中释放 block 的全部副本
```

释放后，未来的 load 是否立即重新取数、从哪一级缓存或内存取得数据，以及其他硬件预取是否又分配了副本，都属于实现细节；软件能依赖的是该 invalidate 操作对相关一致性缓存副本的作用，而不是“永远不再缓存”。如果对应区域被非一致设备更新，CPU 必须在设备写完成并按平台协议建立完成顺序后再 inval 和读取，否则仍可能看到设备尚未完成的内容。

从 RVWMO 看，invalidate 在保序规则中按 store 对待，并额外保证同一 hart 程序顺序中、随后对重叠地址执行的 load 不会在全局内存顺序上排到该 inval 之前。因此可将“先 inval、后读取同一块”作为缓存维护序列的一部分；但它不是通用全栅栏，不能替代对非重叠访问、MMIO 通知或跨 hart 发布所需的 `FENCE`、原子操作及设备协议。

最重要的限制是数据丢失风险。若块中存在自上次 `inval`、`clean` 或 `flush` 后由 coherent agent 写入的修改，真正的 inval 不会保证将这些修改写到公共可见点，低层内存可能仍是旧值。RISC-V 规范特别指出：低特权级对含敏感数据的缓存块执行 inval，可能使陈旧内存数据暴露，从而造成安全问题。为避免这种情况，高特权级软件应在允许低特权级 inval 前先 clean 或 flush 该块，或者把 `CBIE` 配置为让低特权级指令陷入异常或改为执行 flush。

该指令也是显式内存访问，会进行地址翻译、PMP、PMA 与权限检查；管理类 CBO 在该物理地址允许普通 load 或 store 时可访问该块。否则会产生 store page-fault、store guest-page-fault 或 store access-fault，且不会产生地址未对齐异常。它不检查也不设置页表 Dirty 位，但会按规定处理 Accessed 位。尽管 `Zicbom` 管理指令忽略 PMA 的 cacheable 属性和 PBMT 的 cacheability 降级，访问权限和平台支持条件仍然有效。

### 这条指令对程序执行有什么帮助

`cbo.inval` 的典型用途是使 CPU 放弃可能陈旧的缓存数据，从而读取非一致 DMA 设备写入的最新缓冲区。例如网卡、存储控制器或加速器把数据写入主存后，驱动在确认设备完成写入、并按所需规则完成同步后，对接收缓冲区的各完整缓存块执行 inval；CPU 随后的 load 将不再依赖此前缓存的副本。相对于 `cbo.flush`，它无需写回一个确定为干净的 CPU 缓存块，因而可以避免不必要的写流量。

但它只适用于 CPU 已不持有需要保留的脏修改，或系统将该指令配置为 flush 的场景。若 CPU 和设备可能同时写同一缓存块，单独 inval 不能解决竞争、数据所有权或字节级合并问题；驱动应通过缓冲区所有权划分、完成队列和屏障确保两侧不会并发修改同一数据。对正常一致性的多 hart 共享内存，也应优先使用锁和原子同步，而非通过 inval 实现通信。

为正确处理任意长度缓冲区，软件需要先取得 CBO 管理块大小，并按块边界维护；范围首尾若与其他对象共享同一块，应避免错误地使无关数据失效。实现上的性能取决于失效范围、后续访问是否立即造成 cache miss、TLB 与 DCache 资源及 DMA/互连流量。也就是说，`cbo.inval` 提供的是精确的缓存副本管理能力，不是自动的 DMA 同步或内存一致性万能机制。

参考资料：[RISC-V Instruction Set Manual, Volume I，CMO Extensions for Base Cache Management Operation ISA（Version 1.0.0）](https://docs.riscv.org/reference/isa/unpriv/cmo.html)。

## 香山昆明湖源代码分析

### 分析范围与总览

本节**只依据**昆明湖工程 `XiangShan/src/main` 下的 Chisel 源码梳理 `cbo.inval`。这里的“执行完成”指该指令收到 DCache CMO 应答、经 StoreQueue 回写到后端并满足 ROB commit 条件；不使用波形、仿真或 `src/` 目录以外组件来推断内部行为。

```text
Decode
  -> Rename / Dispatch（ROB、SQ 分配）
  -> Sta / Std issue queue
  -> StoreUnit（地址生成、TLB 翻译）
  -> StoreQueue CMO 状态机（到 ROB 队首，排空 StoreBuffer）
  -> MemBlock CMO sideband -> DCache CMOUnit
  -> TileLink CacheBlockOperation / CBOAck
  -> StoreQueue mmioStout -> MemBlock stOut -> Backend writebackSta
  -> ROB Commit -> Retire 计数
```

该路径虽然在后端被编排为 **store 类**操作，但并不等价于一次带写数据的普通 store：StoreUnit 负责地址、翻译和异常入口；真正的 cache-block operation 由 StoreQueue 经专用 `cmoOpReq/cmoOpResp` 端口送入 DCache。

### 1. Decode：识别、权限控制与执行单元选择

[`DecodeStage.scala:83`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeStage.scala#L83) 为每条解码 lane 实例化 `DecodeUnit`，并将控制流输入交给该单元。因此，从后端 Decode 起，`cbo.inval` 的静态指令信息会被转换为后续流水线携带的 `DynInst` 控制字段。

[`DecodeUnit.scala:476`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476) 的解码表给出了最关键的归类：

```scala
CBO_INVAL -> XSDecode(
  SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_inval, SelImm.IMM_S)
```

- `SrcType.reg` 表示 `rs1` 是整数寄存器来源，供地址生成使用；`SrcType.DC` 与 `SrcType.X` 表示不需要第二个寄存器源、也没有整数目的寄存器。
- `FuType.stu` 将指令送到 Store Unit 类执行资源；`LSUOpType.cbo_inval` 保留了“这是 inval 而非普通 store”的精确语义；`SelImm.IMM_S` 复用 store 风格立即数编码。
- `DecodeUnit` 同时以 `isCboInval` 分类该指令，并把 `illegalInst.cboI || !HasCMO` 写入非法指令判断；也就是说，没有 CMO 实现参数时，不能把该编码当作可执行的普通 store。见 [`DecodeUnit.scala:882`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L882) 与 [`DecodeUnit.scala:913`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L913)。
- CBO 的权限和虚拟化控制来自 CSR：[`NewCSR.scala:1502`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L1502) 依据各特权级 EnvCfg 的 `CBIE` 形成 `illegalInst.cboI`、`virtualInst.cboI` 和 `special.cboI2F`。当 `cboI2F` 生效时，Decode 不改变其 store 类路由，而是将操作码改写为 `cbo_flush`，见 [`DecodeUnit.scala:1168`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L1168)。

### 2. Rename、Dispatch 与 Store 类调度

Rename 根据 Decode 给出的源类型读取 RAT；[`Rename.scala:385`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L385) 将逻辑源寄存器映射为 `psrc`，而 [`Rename.scala:402`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L402) 仅在写使能时分配 `pdest`。因此，`cbo.inval` 保留地址源 `psrc(0)`，但因没有目的寄存器不会消耗整数目的物理寄存器。

Rename 输出经 [`CtrlBlock.scala:703`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L703) 进入 Dispatch；同一模块将 Dispatch 的 ROB 入队与 LSQ 分配接口分别接到 ROB 和 MemBlock，见 [`CtrlBlock.scala:707`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L707) 与 [`CtrlBlock.scala:733`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L733)。

由于它仍是 scalar store 类微操作，[`NewDispatch.scala:673`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L673) 对它检查 Store Queue 空闲项；[`NewDispatch.scala:688`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L688) 为 scalar store 申请 SQ 项并将带 `sqIdx` 的 uop 发给 LSQ。Issue 侧复用 Sta/Std 两条 store 调度通路：[`Scheduler.scala:492`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/issue/Scheduler.scala#L492) 把 store address 入队信息接到 Std IQ，并保留 `psrc` 与 `sqIdx`。这是资源和依赖跟踪的复用；源码后续会用 `fuOpType` 把 CBO 分流为专门的 CMO 请求，故不能把此处的 Std 通路解释为 CBO 写入了普通 store data。

### 3. StoreUnit：地址、对齐和 TLB 的 CBO 特殊处理

[`MemBlock.scala:1239`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1239) 将 `issueSta` 接到 `StoreUnit.stin`，使 CBO 先走 StoreUnit 的地址执行入口。StoreUnit 用 `src(0)` 加符号扩展的立即数生成虚拟地址，见 [`StoreUnit.scala:141`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L141)。

其特殊处理集中在 CBO 分类、对齐和翻译命令：

```scala
val s0_isCbo = LSUOpType.isCbo(s0_uop.fuOpType)
val s0_isCbo_noZero = LSUOpType.isCboClean(...) ||
  LSUOpType.isCboFlush(...) || LSUOpType.isCboInval(...)
val s0_addr_aligned = LookupTree(...) || s0_isCbo

io.tlb.req.bits.cmd := Mux(s0_isCbo_noZero, TlbCmd.read, TlbCmd.write)
io.tlb.req.bits.memidx.is_ld := false.B
io.tlb.req.bits.memidx.is_st := true.B
```

见 [`StoreUnit.scala:159`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L159) 和 [`StoreUnit.scala:215`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L215)。这说明：第一，CBO 被允许绕过普通 store 的地址未对齐检查；第二，流水线/SQ 标记仍把它归作 store，但 `cbo.inval` 等非-zero CBO 给 TLB 的访问命令是 `read`，而不是普通 store 使用的 `write`。地址翻译、PMP/PMA 等检查仍由该 TLB 路径承担，异常也因此能回写到对应 SQ/ROB 项。

### 4. StoreQueue：队首串行化、StoreBuffer 排空与 CMO 完成

StoreQueue 为 MMIO、uncached 和 CMO 共用一个“等待 ROB 队首后执行”的控制框架，其注释明确列出“执行单元回写为 pending → 到 ROB head 发请求 → 收到应答 → 回写 ROB → ROB commit”的生命周期，见 [`StoreQueue.scala:820`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L820)。对 CBO，状态机为 `s_idle -> s_req -> s_resp -> s_wb`：在 `s_idle` 只有当该 SQ 项有效、地址有效、无异常且 `rob.pendingst` 指向该项时，才锁存 `uncacheUop`；同时将物理地址经 `get_block_addr` 对齐到 cache block 首地址，见 [`StoreQueue.scala:841`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L841)。

```scala
io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr

io.flushSbuffer.valid := deqCanDoCbo && !cboFlushedSb &&
  (mmioState === s_req) && !io.flushSbuffer.empty
```

见 [`StoreQueue.scala:1008`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1008)。`cmoOpCode` 取自 `fuOpType(1,0)`；对 `cbo.inval`，DCacheWrapper 中定义的编码为 2。重点是 StoreQueue 在 `cboFlushedSb` 变为真之前持续请求 `flushSbuffer`，并且只在 `flushSbuffer.empty` 时允许 CMO 请求发出（[`StoreQueue.scala:1033`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1033)）。这是一条针对 CMO 的显式排序措施：较早进入 StoreBuffer 的 store 必须先排空，CBO 才能离开 SQ。

`cmoOpReq.fire` 后进入等待应答的 `s_resp`；`cmoOpResp.fire` 后转入 `s_wb`。随后 `mmioStout` 带着原 uop 回写，并专门令 `uop.flushPipe := deqCanDoCbo` 来维持 CMO 顺序，见 [`StoreQueue.scala:1012`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1012) 与 [`StoreQueue.scala:1055`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1055)。因此，收到 DCache 应答不是立即退休，而是成为可回写、可提交的 store 类执行完成事件。

### 5. MemBlock 与 DCache：独立 CMO sideband

MemBlock 没有将这条请求塞入普通 `load` 或普通 store-data 接口，而是直接把 LSQ 的两个专用端口接到 DCache：

```scala
lsq.io.cmoOpReq  <> dcache.io.cmoOpReq
lsq.io.cmoOpResp <> dcache.io.cmoOpResp
```

见 [`MemBlock.scala:1210`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1210)。回程的 `mmioStout` 也被 MemBlock 仲裁并入第 0 个 StoreUnit 回写端口，见 [`MemBlock.scala:1354`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1354)。这两个连接是 CBO 从 LSQ 返回普通后端 writeback 网络的边界。

DCache 对应定义了独立 bundle：

```scala
class CMOReq extends Bundle {
  val opcode = UInt(3.W) // 0 clean, 1 flush, 2 inval, 3 zero
  val address = UInt(64.W)
}
class CMOResp extends Bundle { val address; val nderr; val denied; val corrupt }
```

见 [`DCacheWrapper.scala:619`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L619)。因此 `cbo.inval` 的特殊 cache 支持首先体现在协议层：它用 opcode 2 和块首地址表达，而非将普通 store mask/data 改造成失效请求。DCacheWrapper 将该端口接到 MissQueue，见 [`DCacheWrapper.scala:1532`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1532)。

MissQueue 内的 [`CMOUnit`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L299) 是专用的四态状态机：`s_idle` 接收并锁存一个 `CMOReq`，`s_sreq` 发出 cache-block 请求，`s_wresp` 等待 D 通道返回，`s_lsq_resp` 向 LSQ 返回 `CMOResp`。其请求生成逻辑为：

```scala
edge.CacheBlockOperation(
  fromSource = ..., toAddress = req.address,
  lgSize = log2Up(cfg.blockBytes), opcode = req.opcode)
```

见 [`MissQueue.scala:352`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L352)。这里明确以 `cfg.blockBytes` 发起整块操作，且 `io.req.ready := state === s_idle`（[`MissQueue.scala:341`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L341)）使该单元一次只接受一个未完成 CMO。D 通道中只有 `TLMessages.CBOAck` 会送入 `resp_chanD`，见 [`MissQueue.scala:1229`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L1229)；CMOUnit 将 `denied`、`corrupt`、`nderr` 采样后带回 LSQ，而不是把它伪装成一次普通 load 返回数据。

### 6. 回写、Commit 与 Retire

`mmioStout` 被并入 `stOut(0)` 后，后端将 Store 地址执行回写 `writebackSta` 纳入统一 writeback 收集，见 [`Backend.scala:977`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala#L977)。ROB 按 writeback 所携带的 `robIdx` 更新相应 ROB 项的完成状态，见 [`Rob.scala:528`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L528)。

ROB 对 store 类项的提交门槛在 [`RobBundles.scala:170`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/RobBundles.scala#L170) 中定义：

```scala
robCommitEntry.commit_v := robEntry.valid
robCommitEntry.commit_w := (robEntry.uopNum === 0.U) &&
  (robEntry.stdWritebacked === true.B)
```

也就是说，CBO 的 ROB 项即使已经到队首，也必须等回写完成位满足 `commit_w`。ROB 进一步以 `commit_v && commit_w`、无异常/redirect 等阻塞、以及不被更老项阻塞为条件产生 `commitValid`，见 [`Rob.scala:780`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L780)。因此缓存操作的外部 `CBOAck` 已被 StoreQueue 转换为后端完成回写，最终成为可提交条件的一部分，而不是绕过 ROB 的异步副作用。

最后，ROB 在提交周期寄存真实提交条目数，并通过 `retireCounter` 驱动 `io.csr.perfinfo.retiredInstr`，见 [`Rob.scala:1248`](home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L1248)。对无目的寄存器的 `cbo.inval`，退休不涉及 RAT 的目的寄存器提交；其架构完成点仍由这条 `commitValid`/`retireCounter` 链路定义。

### 7. 源码结论：Cache 对 CBO Inval 的专用支持

- **独立编码与接口：**`CMOReq` 以 opcode 2 表示 inval，拥有独立 `cmoOpReq/cmoOpResp`，不复用普通 load 响应或 store 数据请求。
- **块粒度地址：**StoreQueue 在离开队首前用 `get_block_addr` 得到块首物理地址；CMOUnit 以 `cfg.blockBytes` 生成 `CacheBlockOperation`。
- **严格顺序：**CBO 到 ROB 队首后先排空 StoreBuffer，DCache 侧 `CMOUnit` 也只允许一个在途请求，完成回写再用 `flushPipe` 保持流水线顺序。
- **独立完成与错误回传：**DCache 只以 `CBOAck` 完成该请求，并将 `nderr/denied/corrupt` 经 `CMOResp` 返回 LSQ；随后由 StoreQueue 的回写与 ROB 的 `commit_w` 门槛把操作纳入精确提交/退休。

从这些 `src/main` 代码可以确定昆明湖为 CBO Inval 配置了从 Decode、LSQ 到 DCache 的专用控制与 cache-block 请求通路；但仅凭这里的边界代码，不能进一步断言下级缓存如何选择具体 cache way、命中/未命中后的替换动作或一致性域中的最终实现，这些属于本节分析范围之外。

## CBO Inval 演示程序

### 演示程序 C 代码

演示程序位于
[`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/cbo_inval.c:1`](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/cbo_inval.c#L1)。
它构造一个恰好 64 B、按 64 B 对齐的 `volatile` 数据块；先读取全部八个 64-bit word，
再对该块内偏移 8 B 的地址执行 `cbo.inval`，最后重新读取并比对 checksum。

```c
#include <klib.h>

enum {
  CacheBlockBytes = 64,
  CacheBlockWords = CacheBlockBytes / sizeof(unsigned long),
};

static volatile unsigned long cache_block[CacheBlockWords]
    __attribute__((aligned(CacheBlockBytes))) = {
        0x1020304050607080UL, 0x1122334455667788UL,
        0x8877665544332211UL, 0x0f1e2d3c4b5a6978UL,
        0x55aa55aa55aa55aaUL, 0xaa55aa55aa55aa55UL,
        0x0123456789abcdefUL, 0xfedcba9876543210UL,
};

static unsigned long read_cache_block(void) {
  unsigned long checksum = 0;
  for (int index = 0; index < CacheBlockWords; index++) {
    checksum ^= cache_block[index];
  }
  return checksum;
}

static void cbo_inval(const void *address) {
  asm volatile("cbo.inval (%0)" : : "r"(address) : "memory");
}

int main(void) {
  printf("CBO Inval demo starts\n");
  printf("cache block address = %p, size = %d bytes\n", cache_block,
         CacheBlockBytes);

  unsigned long before = read_cache_block();
  printf("pre-inval reads complete, checksum = 0x%lx\n", before);

  cbo_inval((const void *)cache_block + sizeof(unsigned long));
  printf("cbo.inval completed for an address within the cached block\n");

  unsigned long after = read_cache_block();
  printf("post-inval reads complete, checksum = 0x%lx\n", after);

  if (before != after) {
    printf("FAIL: cbo.inval changed memory data\n");
    return 1;
  }
  printf("PASS: cache block was invalidated and data remained coherent\n");
  return 0;
}
```

### 演示程序反汇编结果

使用 `ARCH=riscv64-xs MARCH=rv64gc_zicbom` 构建的 ELF 为
`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_inval/build/cbo_inval-riscv64-xs.elf`。
以下是 `main` 中与预访问、CBO 和后访问直接相关的反汇编；`s1` 保存
`cache_block=0x80001780`，`a5` 保存 block 内的操作数地址 `0x80001788`。

```text
8000015a: 00001497           auipc s1,0x1
8000015e: 62648493           addi  s1,s1,1574 # 80001780 <cache_block>
80000162: 46a1               li    a3,8
80000164: 00379713           slli  a4,a5,0x3
80000168: 9726               add   a4,a4,s1
8000016a: 6318               ld    a4,0(a4)
8000016e: 8c39               xor   s0,s0,a4
80000170: fed79ae3           bne   a5,a3,80000164 <main+0x3a>

80000182: 00001797           auipc a5,0x1
80000186: 60678793           addi  a5,a5,1542 # 80001788 <cache_block+0x8>
8000018a: 0007a00f           cbo.inval (a5)

8000019c: 4781               li    a5,0
8000019e: 46a1               li    a3,8
800001a0: 00379713           slli  a4,a5,0x3
800001a4: 9726               add   a4,a4,s1
800001a6: 6318               ld    a4,0(a4)
800001aa: 8db9               xor   a1,a1,a4
800001ac: fed79ae3           bne   a5,a3,800001a0 <main+0x76>
```

指令字 `0x0007a00f` 的 `rs1` 字段选择 `a5`；`rd=x0`，因此没有寄存器结果。虽然
传给指令的是 `0x80001788`，波形中的 StoreQueue CMO 地址为 `0x80001780`，证明硬件按
64 B cache block 规整了地址。

### 演示程序与预期行为分析

1. **失效前的缓存相关访问。** `read_cache_block()` 使用八条 64-bit `ld` 读取整个
   数据块，并把数据异或为 `before`。`volatile` 防止编译器折叠这些访问；随后的 `printf`
   也使仿真日志明确划分失效前阶段。
2. **块内非对齐操作数。** 数据块首地址是 `0x80001780`，但 CBO 操作数是
   `0x80001788`。这刻意验证 `cbo.inval` 按“包含该地址的 cache block”操作，而不是要求
   软件把 `rs1` 写成块首地址。该程序只进行读访问，不会在 inval 前人为制造需要写回的
   脏数据，避免把演示变成数据丢失场景。
3. **失效后的访问和功能检查。** `cbo.inval` 后重新读取同一八个 word 并计算 `after`。
   若缓存维护错误地改变了架构可见内容，程序输出 `FAIL` 并返回 1；正常情况下输出
   `PASS`。本次仿真的 `before` 和 `after` 都是 `0x866b486d0a6f4c61`，且波形中 CMO 请求
   的 `opcode=2`、块地址 `0x80001780`、响应 `denied=0/corrupt=0`，所以预期行为得到满足。

该程序验证的重点是“CBO 指令确实发出、目标 block 正确、执行完成后数据保持一致”。它并不
把 post-inval 首次读取的 DCache miss 作为硬性断言：预取或其他一致性状态可能影响该读取的
具体命中层级；如需验证该性能现象，应额外关联后续 load 的 LQ/DCache hit/miss/replay 信号。

## 波形图分析

### 分析对象、方法和身份锚点

本节使用 `~/wavekit/.venv` 中的 Python 和 `PYTHONPATH=~/wavekit/src` 加载
Wavekit `FstReader`，以 `TOP.clock` 的**上升沿**采样
`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-12-01-06.fst`。
cycle 是从仿真起始时钟边沿计数的绝对周期，time 是 FST 的原始仿真时间。以下分析不以
持续不变的 PC 总线值作为唯一身份，而在 Rename 后携带 ROB index；本条指令的稳定身份为：

| 项目 | 波形值 | 说明 |
|---|---:|---|
| PC | `0x000000008000018a` | 程序中内联汇编 `cbo.inval (a5)` 的地址 |
| instruction bits | `0x0007a00f` | 反汇编为 `cbo.inval (a5)` |
| Rename 后 ROB | `83` (`0x53`) | 后续 Store Unit、StoreQueue、redirect 和 commit 的关联键 |
| Store Queue index | `19` | `sqIdx`，在调度、Store Unit 与最终 commit 中一致 |
| 虚拟地址 | `0x0000000080001788` | 程序传入 `cache_block + 8` |
| CMO 块地址 | `0x0000000080001780` | StoreQueue 用 `get_block_addr` 对 64 B block 向下对齐后的地址 |

Wavekit 查询到最终 `rob.difftest_commit` lane 0 在 cycle 22442、time 44884
提交 `PC=0x8000018a, instr=0x0007a00f, rob=83, sq=19`。这闭合了
Decode 中的 PC/instruction 与执行完成之间的身份链。

### 总体逐周期时间线

| cycle | time | 模块/边界 | 关键波形值（均在上升沿采样） | 解释 |
|---:|---:|---|---|---|
| 21468 | 42936 | `decode.io_in_5` | `valid=1, ready=1, fire=1`；PC/bits 匹配 | 目标进入 Decode lane 5。 |
| 21468 | 42936 | `decode.io_out_5` | `valid=1, ready=1, fire=1, fuOpType=0x0e, rfWen=0` | Decode 产生 store/CMO uop，无整数目的寄存器写回。 |
| 21469 | 42938 | `rename.io_in_5` / `rename.io_out_5` | 两侧均 `valid=ready=1`；`psrc0=170, pdest=0, rob=83` | 读取基址物理寄存器，分配 ROB=83；没有目的物理寄存器。 |
| 21470 | 42940 | `dispatch.io_fromRename_5` / `io_enqRob_req_5` | `valid=ready=1, fire=1, rob=83, sq=19, fuOpType=0x0e` | Dispatch 同时把该 uop 送往 ROB、LSQ/SQ 分配及 issue 队列。 |
| 21470 | 42940 | `IssueQueueStaMou_1.io_enq_0` | `valid=ready=1, fire=1, rob=83, sq=19` | Store-address/MOU 队列接收 CBO 地址半部。 |
| 21470 | 42940 | `IssueQueueStdMoud_1.io_enq_0` | `valid=ready=1, fire=1, rob=83, sq=19` | Store-data/MOU 队列也接收同一 store 类 uop；这是 STU 双路径资源分配。 |
| 21472 | 42944 | `IssueQueueStdMoud_1.io_deqDelay_0` | `valid=ready=1, fire=1, rob=83, sq=19, fuOpType=0x0e` | store-data 一侧先被调度；CBO 无普通 store data，它不直接发 DCache store request。 |
| 21477 | 42954 | `IssueQueueStaMou_1.io_deqDelay_0` | `valid=ready=1, fire=1, rob=83, sq=19, fuOpType=0x0e` | 地址一侧被发射，进入 Store Unit 1。 |
| 21479 | 42958 | `StoreUnit_1.io_stin` / TLB request | `io_stin.valid=1, rob=83, sq=19`；`io_tlb_req.valid=1, vaddr=0x80001788` | Store Unit 计算 CBO 有效地址并做地址翻译。 |
| 21480 | 42960 | `StoreUnit_1.io_lsq` | `valid=1, rob=83, sq=19, updateAddrValid=1, vaddr=paddr=0x80001788` | 翻译结果与地址状态写入 LSQ/StoreQueue；不是普通 load writeback。 |
| 22356 | 44712 | StoreQueue CMO FSM | `mmioState=1(s_req), cboMmioPAddr=0x80001780, cboFlushedSb=0` | ROB head 的该 CBO 被选中，块地址已规整；等待/执行 StoreBuffer 排空条件。 |
| 22370 | 44740 | StoreQueue → DCache | `cmoOpReq.valid=ready=1, fire=1, opcode=2, address=0x80001780` | 发送实际 `cbo.inval` CMO 请求。 |
| 22371 | 44742 | DCache `CMOUnit` → TileLink A | `state=1(s_sreq), req_chanA.valid=ready=1, opcode=0xe, address=0x80001780` | CMOUnit 把 CMOReq 编码为 cache-block operation 并送往 L2。 |
| 22372–22427 | 44744–44854 | DCache `CMOUnit` | `state=2(s_wresp), no_pending=0` | 请求在 cache hierarchy 中执行，等待 CBOAck；此期间没有重发。 |
| 22376–22439 | 44752–44878 | L2 slice 2, MSHR 0 | `req_cboInval=1` | L2 确认该请求为 CBO Inval 并保持对应 MSHR/CMO 操作活跃。 |
| 22428 | 44856 | L2/DCache D channel | `resp_chanD.valid=ready=1, denied=0, corrupt=0` | 收到 `CBOAck`，无 access fault 或 hardware error。 |
| 22429 | 44858 | `CMOUnit` → StoreQueue | `state=3(s_lsq_resp), resp_to_lsq.valid=ready=1` | CMOUnit 把成功响应返回 LSQ。 |
| 22430 | 44860 | StoreQueue | `mmioState=3(s_wb), mmioStout.valid=1, rob=83, sq=19`；`DiffCMOInvalEvent.valid=1` | StoreQueue 对 ROB 写回“执行完成”，并产生 CBO Inval difftest 事件。 |
| 22436 | 44872 | MemBlock redirect | `io_mem_redirect.valid=1, robIdx=83, level=0, isVlsException=0` | 该 ROB 对齐的内存完成/恢复 redirect。 |
| 22441 | 44882 | Frontend redirect | `io_frontend_toFtq_redirect.valid=1` | 前端消费上述恢复信息。 |
| 22442 | 44884 | ROB commit | `valid=1, PC/bits/rob/sq` 全部匹配 | `cbo.inval` 的架构提交点。 |

### 1. Decode：由 CBO 编码产生 Store Unit uop

在 cycle 21468，`decode.io_in_5` 与 `decode.io_out_5` 都 `fire`。关键字段为
`fuOpType=0x0e`、`rfWen=0`、`flushPipe=0`、`waitForward=0`、`blockBackward=0`：

- `fuOpType=0x0e` 是本配置中 `LSUOpType.cbo_inval` 的编码，决定后续进入 store
  类执行资源和 CMO 控制路径；
- `rfWen=0` 与 `pdest=0` 一致，说明它不是产生 GPR 结果的 load/ALU 指令；
- Decode 边界没有反压；本条指令没有在 decode 处要求 pipeline flush 或 backward block。

对应 Chisel decode 表在
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:476`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476)：

```scala
CBO_INVAL -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_inval, SelImm.IMM_S)
```

这解释了波形中的单源寄存器、无目的寄存器、Store Unit 功能类型和 `0x0e` 操作码。
其中 `SelImm.IMM_S` 使编码中的零 offset 作为 store 型立即数参与地址形成。

同时，`decode.decoders_5.isCboInval=1` 在目标 decode 窗口有效，而
`illegalInst.cboI=0`、`virtualInst.cboI=0`、`special.cboI2F=0`。这与
[`DecodeUnit.scala:884`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L884)
和 [`DecodeUnit.scala:913`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L913)
的检查一致：没有因 `CBIE`/`HasCMO` 失败而变为非法指令，也没有在特权环境控制下改写为
`cbo.flush`。

### 2. Rename、Dispatch 与调度：携带 ROB=83 / SQ=19

cycle 21469，Rename lane 5 的输入与输出均为 `valid=ready=1`。输出的
`psrc0=170` 是持有 a5/基址的物理寄存器，`pdest=0` 和 `rfWen=0` 表明不会分配 GPR
目的寄存器；`robIdx=83` 成为该指令越过乱序边界后的唯一标识。下一拍，Dispatch 的
`io_fromRename_5` 和 `io_enqRob_req_5` 都以 `valid=ready=1` 接收它，且分配
`sqIdx=19`。

在周期 21470，Dispatch 将此 store 类 uop 同时送给两个 memory issue queue：

- `IssueQueueStaMou_1` 负责 store address / memory operation；
- `IssueQueueStdMoud_1` 负责 store data 通路。

两条 enqueue 都 `fire`，没有 issue queue 入队反压。随后 StdMoud 路径 cycle 21472
发射，StaMou 路径 cycle 21477 发射。后者才将 `rob=83` 交给 `StoreUnit_1`，因此它是
形成 CBO 地址和地址翻译请求的决定性路径；前者反映 CBO 在 Kunminghu 中仍按 store 型
uop 占用 store-data 调度资源，而不是 Load Unit 数据返回路径。

MemBlock 中的两条路径在
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1239`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1239)
和 [`MemBlock.scala:1248`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1248)
分别连接到 `issueStd` 和 `StoreUnit.io.stin`：

```scala
stdExeUnits(i).io.in.valid := io.ooo_to_mem.issueStd(i).valid
...
stu.io.stin <> io.ooo_to_mem.issueSta(i)
io.mem_to_ooo.stIn(i).valid := stu.io.issue.valid
```

因此，`IssueQueueStdMoud_1` 的 fire 不表示普通 cache store 已发生；它只完成 store-data
分支的发射。真正 CMO 由 address/StoreQueue 分支在后续完成。

### 3. Store Unit：CBO 的地址翻译与普通 store 的差异

目标进入 `inner_StoreUnit_1` 的 cycle 21479，波形为：

| 信号 | 值 | 去向/意义 |
|---|---:|---|
| `io_stin.valid` | 1 | 接收 StaMou 发射的 ROB=83、SQ=19 uop。 |
| `s0_isCbo` | 1 | Store Unit 已识别为 CBO，而不是普通 store。 |
| `s0_vaddr` | `0x80001788` | 由基址寄存器与零 store immediate 形成，保留原始块内地址。 |
| `io_tlb_req.valid` | 1 | 向 DTLB 发起翻译请求。 |
| `io_tlb_resp.valid`（cycle 21480） | 1 | 同拍返回物理地址 `0x80001788`，无 TLB miss/replay 证据。 |
| `io_lsq.valid`（cycle 21480） | 1 | 把地址完成的 store/CMO uop 送给 LSQ。 |
| `io_lsq.bits.updateAddrValid` | 1 | 令 SQ=19 的地址状态有效。 |
| `io_lsq.bits.vaddr/paddr` | 都为 `0x80001788` | 本测试直接映射，VA 与 PA 相同。 |

[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:159`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L159)
显式区分 `s0_isCbo` 和 `s0_isCbo_noZero`，并在
[`StoreUnit.scala:167`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L167)
令 CBO 跳过普通 store 的地址未对齐条件：

```scala
val s0_isCbo = s0_use_flow_rs && LSUOpType.isCboAll(s0_stin.uop.fuOpType)
val s0_isCbo_noZero = s0_use_flow_rs && LSUOpType.isCbo(s0_stin.uop.fuOpType)
...
val s0_addr_aligned = LookupTree(...) || s0_isCbo
```

更关键的是 TLB command 的特殊选择：
[`StoreUnit.scala:215`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala#L215)
对 `s0_isCbo_noZero` 使用 `TlbCmd.read`，其余普通 store 使用 `TlbCmd.write`，但
`memidx.is_st` 仍为 1。也就是说，CBO 在流水线资源归类上是 store/SQ 操作，在地址权限
翻译时则用其专门规定的 read-style 请求；它不会进入 `LoadUnit` 或产生 load data writeback。

```scala
io.tlb.req.bits.cmd := Mux(s0_isCbo_noZero, TlbCmd.read, TlbCmd.write)
io.tlb.req.bits.memidx.is_ld := false.B
io.tlb.req.bits.memidx.is_st := true.B
```

本 FST 中没有任何 `IssueQueueLdu*` 的 `valid=1` 目标 PC enqueue，也没有目标 ROB
出现在 DCache 的 `io_lsu_load_*_req`；这是对“CBO 不走 Load Unit 普通 load request”的
波形反证。随后程序的 post-inval `read_cache_block()` 会产生独立的 Load Unit/DCache load
事务，但它们拥有不同的 PC/ROB/LQ 身份，不能混入本条 `cbo.inval` 的执行轨迹。

### 4. LSQ / StoreQueue：从 SQ entry 变成串行 CMO

`io_lsq.valid` 在 cycle 21480 将 `rob=83, sq=19, fuOp=0x0e` 与已翻译地址送入
LSQ。之后该 uop 不能像普通 cached store 一样进入 SBuffer 的 data 写请求；StoreQueue
将 CBO 与 MMIO/uncached 类操作放到专用 `mmioState` FSM，等待它到达 ROB head 并满足
store-side ordering 条件。

源码在
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:820`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L820)
定义状态编码：`0=s_idle, 1=s_req, 2=s_resp, 3=s_wb, 4=s_wait`。在 `s_idle` 选中
ROB head 的 pending store 时，源码把 `paddr` 通过 `get_block_addr` 写入
`cboMmioPAddr`：

```scala
val s_idle :: s_req :: s_resp :: s_wb :: s_wait :: Nil = Enum(5)
...
cboFlushedSb := false.B
cboMmioPAddr := get_block_addr(paddrModule.io.rdata(0))
```

波形中的目标状态转换如下（先前出现的 `0x40600000` 是较早 UART 输出的 MMIO store，
不是 ROB=83；因此这里以 `cboMmioPAddr=0x80001780` 过滤）：

| cycle | `mmioState` | `cboFlushedSb` | `cboMmioPAddr` | 说明 |
|---:|---|---:|---|---|
| 22356 | `1 (s_req)` | 0 | `0x80001780` | SQ=19 到达可处理位置，捕获并规整 CBO 地址。 |
| 22370 | `1 (s_req)` | 1 | `0x80001780` | StoreBuffer 已满足排空要求；`cmoOpReq.fire`。 |
| 22371 | `2 (s_resp)` | 1 | `0x80001780` | 请求已经被 DCache 接受，等待 CMO response。 |
| 22430 | `3 (s_wb)` | 1 | `0x80001780` | response 已被接收，向 ROB 写回完成。 |
| 22431 | `4 (s_wait)` | 1 | `0x80001780` | 等待 ROB 的 store commit。 |
| 22445 | `0 (s_idle)` | 1 | `0x80001780` | commit 后 FSM 可服务下一条 MMIO/CMO。 |

从 issue（21477）到 CMO request（22370）相隔 893 cycles。波形证明请求接口本身没有
`valid && !ready`：fire 发生的唯一周期中 `valid=ready=1`。因此不能把该间隔归因于
DCache 反压；可以确定的是，这段时间处于 StoreQueue 的程序顺序/ROB-head/StoreBuffer
排空等待。`io.cmoOpReq.valid` 的源码门控正是
[`StoreQueue.scala:1025`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1025)：

```scala
io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr
io.cmoOpResp.ready := deqCanDoCbo && (mmioState === s_resp)
```

这也解释了为什么进入 `s_req`（22356）后还要到 22370 才发 request：`cboFlushedSb` 必须
从 0 变为 1。该位的置位条件在
[`StoreQueue.scala:1033`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1033)
中要求 SBuffer empty；这是 CBO Inval 相比普通 store 的关键串行控制。

### 5. MemBlock 与 DCache：CMOReq 不是普通 load/store request

cycle 22370，StoreQueue 的 `io_cmoOpReq` 与 LSQ、DCache 外层和 DCache 内层接口都看到
同一拍的：

```text
valid = 1, ready = 1, fire = 1
opcode = 2            # CMOReq: cbo.inval
address = 0x80001780  # cache block base
```

`opcode=2` 的含义由
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:619`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L619)
定义：`0=clean, 1=flush, 2=inval, 3=zero`。MemBlock 没有把它拼入
`io_lsu_load_*_req` 或 `io_lsu_store_req`，而是专门直连：

[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1210`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1210)

```scala
lsq.io.cmoOpReq  <> dcache.io.cmoOpReq
lsq.io.cmoOpResp <> dcache.io.cmoOpResp
```

因此 MemBlock 对该 CBO 的特殊处理是：保留 Store Unit/LSQ 的地址与提交顺序，但从
LSQ 直接走 DCache 的 CMO sideband，而不是普通 load/store port。该设计避免把 invalidate
伪装成有 store data/mask 的写请求。

### 6. DCache MissQueue / CMOUnit：发送 CacheBlockOperation 并等待 CBOAck

DCacheWrapper 将 sideband CMO 接口接到 `MissQueue.cmo_unit`：
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1532`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1532)。
CMOUnit 的 FSM 见
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:299`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L299)：
`0=s_idle, 1=s_sreq, 2=s_wresp, 3=s_lsq_resp`。

本条事务的 Wavekit 状态机证据为：

| cycle | CMOUnit state | 输入/输出握手 | 控制含义 |
|---:|---|---|---|
| 22370 | `s_idle` | `io_req.valid=ready=1`，CMO opcode=2，地址=`0x80001780` | 接收 StoreQueue CMOReq，并清除旧错误位。 |
| 22371 | `s_sreq` | `io_req_chanA.valid=ready=1`，TL opcode=`0xe`，地址=`0x80001780` | 通过 `edge.CacheBlockOperation` 发出 TileLink CBOInval。 |
| 22372–22427 | `s_wresp` | `resp_chanD.ready=1`，`no_pending=0` | CMO 在 cache hierarchy 执行，CMOUnit 不接受第二条请求。 |
| 22428 | `s_wresp` | `resp_chanD.valid=ready=1, denied=0, corrupt=0` | 收到 L2 `CBOAck`。 |
| 22429 | `s_lsq_resp` | `resp_to_lsq.valid=ready=1, denied=0, corrupt=0` | 成功结果返还 StoreQueue。 |
| 22430 | `s_idle` | `io_req.ready=1` | CMOUnit 可接收下一条 CMO。 |

CMOUnit 的源码精确对应上述四个状态和连接：

```scala
val s_idle :: s_sreq :: s_wresp :: s_lsq_resp :: Nil = Enum(4)
...
io.req_chanA.valid := state === s_sreq && !io.wfi.wfiReq
io.req_chanA.bits := edge.CacheBlockOperation(... opcode = req.opcode)._2
...
io.resp_to_lsq.valid := state === s_lsq_resp
```

MissQueue 只在 D channel opcode 为 `CBOAck` 时把响应接给 CMOUnit：
[`MissQueue.scala:1230`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L1230)。
这解释了 `resp_chanD` 在 22428 的 fire 与下一拍 `resp_to_lsq` 的 fire 之间的一拍状态转换。

### 7. L2：CBO Inval 的一致性处理

Wavekit 在
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_2.mshrCtl.mshrs_0.req_cboInval`
观察到 cycle 22376–22439 连续为 1。该信号把 DCache 发出的 TileLink CBO 绑定到 L2
slice 2 的 MSHR 0；其活动时间覆盖 CMOUnit 的等待响应阶段。

L2 MainPipe 将 A channel 的 `CBOInval` 识别为 `req_cbo_inval_s3`：
[`/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:164`](/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala#L164)。
对有效目录项，CBO Inval 会要求必要的 probe/release，并允许目录 meta 写入：

```scala
val req_cbo_inval_s3 = sinkA_req_s3 && req_s3.opcode === CBOInval
...
req_cbo_inval_s3 && (isValid(meta_s3.state))  // need_release_s3_a
...
val metaW_valid_s3_cmo = req_cbo_inval_s3 && dirResult_s3.hit
```

此外，L2 MSHR 在 CBO Inval 路径中选择 `Evict`，而不是 CBOClean 的 write-clean：
[`/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:552`](/home/yanyusong/cbo-kmhv2/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala#L552)。
这与 Inval “不以写回为目的”的语义一致。FST 没有转储该地址对应的 L2 directory way/meta
命中字段，故本报告只依据 `req_cboInval` 和成功的 `CBOAck` 证明 L2 CBO 路径确实参与，
不声称该块在 L2 的具体 hit/miss 或替换 way。

### 8. 响应、写回、redirect 与提交

CMO response 在 cycle 22429 返回 StoreQueue 后，StoreQueue 在 cycle 22430 产生：

```text
io.mmioStout.valid = 1
io.mmioStout.bits.uop.robIdx = 83
io.mmioStout.bits.uop.sqIdx = 19
cmoInvalEvent.valid = 1
cmoInvalEvent.addr = 0x80001780
```

对应的 difftest 事件生成逻辑在
[`/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1410`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1410)：

```scala
cmoInvalEvent.valid := io.mmioStout.fire && deqCanDoCbo &&
  LSUOpType.isCboInval(uop(deqPtr).fuOpType)
cmoInvalEvent.addr := cboMmioPAddr
```

随后 `backend.io_mem_redirect.valid` 在 cycle 22436 对 `robIdx=83` 断言，
`level=0`、`isVlsException=0`；cycle 22441 的 frontend redirect 是其消费者，且
`debugIsMemVio=0, debugIsCtrl=0`。该 redirect 属于 CBO StoreQueue 完成后的内存恢复协议，
不是分支错误预测，也不是 store-load violation 或异常：CBO 的非法/虚拟化控制均为 0，
DCache/L2 response 的 `denied/corrupt` 均为 0。
最终 cycle 22442 提交，因此从 `CBOAck`（22428）到 commit 仅 14 cycles。

### 9. 停顿、泡泡与结论

| 周期范围 | 边界 | `valid/ready/fire` 证据 | 持续时间 | 可证明的原因 |
|---|---|---|---:|---|
| 21468–21470 | Decode/Rename/Dispatch | 所有目标接口均 `1/1/1` | 0 | 无前端、rename 或 dispatch 反压。 |
| 21470 | 两个 issue queue enqueue | 均 `1/1/1` | 0 | 无 IQ 入队阻塞。 |
| 21472、21477 | StdMoud/StaMou dequeue | 均 `1/1/1` | 0 | 两条 store 型调度路径均立即发射。 |
| 21479–21480 | StoreUnit/TLB/LSQ | 请求、TLB 返回和 `io_lsq.valid` 连续完成 | 1 cycle | 无 TLB miss、地址异常或 replay 证据。 |
| 21480–22356 | StoreQueue 等待 | CMO request 尚未 valid | 876 cycles | 只能证明尚未成为可发 CMO；不能从当前转储唯一归因到某条更老 store。 |
| 22356–22370 | StoreQueue `s_req` | `cboFlushedSb: 0→1`；request 尚未 valid | 14 cycles | 明确由 CBO 要求 StoreBuffer 排空这一门控造成。 |
| 22370 | CMO request | `1/1/1` | 0 | DCache/MissQueue 无 request backpressure。 |
| 22372–22427 | CMOUnit `s_wresp` | 等待 `CBOAck` | 56 cycles | L2/一致性层实际处理时间；L2 MSHR `req_cboInval=1`。 |
| 22428–22442 | response→commit | D channel、LSQ response、mmioStout、redirect、commit 依次发生 | 14 cycles | 成功收尾，无 denied/corrupt/exception。 |

综上，这条 `cbo.inval` 在 Kunminghu 中不是普通 Load Unit 操作，也不是带 data/mask 的普通
DCache store。它**以 `FuType.stu` 进入 Store Unit 和 SQ 以继承地址翻译、PMP/PMA、ROB
顺序与 store-commit 规则**，但在 StoreQueue 被转换成 `CMOReq(opcode=2)`；MemBlock 直接把
该请求送进 DCache MissQueue 的 CMOUnit；CMOUnit 发出 TileLink CacheBlockOperation，L2 的
CBO Inval 路径处理完成后返回 `CBOAck`，最终由 StoreQueue 写回 ROB 并提交。程序在其后的
load 中得到相同 checksum，结合 `denied=corrupt=0` 与正式 commit，证明演示场景的 CBO
Inval 控制链和架构数据正确性均满足预期。
