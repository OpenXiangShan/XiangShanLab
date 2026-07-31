# 香山昆明湖执行 CBO Clean 指令的流程分析

## CBO Clean 指令介绍

### 这条指令是什么

`cbo.clean`（Cache Block Clean）是 RISC-V `Zicbom`（Cache-Block Management Operations）扩展定义的缓存块管理指令。它没有通用寄存器目的操作数，也不产生返回值；唯一的操作数是内存地址。汇编中通常写作 `cbo.clean 0(a0)`，其中 `a0` 对应指令编码中的 `rs1`。指令以 `rs1` 给出的有效地址定位一个缓存块，并对执行该指令的 coherent agent 可访问的整组一致性缓存执行 clean 操作。

clean 的含义是：如果该缓存块自上一次对该块执行 `cbo.inval`、`cbo.clean` 或 `cbo.flush` 后，曾被一致性 agent 的普通 store 修改，则把该块的最新副本向所有 agent 的公共可见点写出。对于 `Zicbom`，该公共点覆盖系统中的所有 agent；因此，非一致 DMA 设备等 agent 可以在该操作完成后从该公共点观察到先前由 CPU 写入的数据。若缓存块未被修改，规范不要求发生写传输。

`cbo.clean` 的操作粒度是整个缓存块，而非某次 store 的 1、4 或 8 字节。缓存块大小由实现决定；软件必须通过执行环境提供的发现机制获得管理类 CBO 的块大小，不能把它写死为某个 DCache line 大小。`rs1` 不要求按块大小对齐；硬件会选择包含该地址的完整缓存块。汇编中的 `offset` 可以省略；若写出，则表达式必须计算为 `0`。

它与同属 `Zicbom` 的另外两条指令分工不同：`cbo.clean` 只确保脏数据写出而不要求丢弃缓存副本，`cbo.flush` 原子地执行“clean 后 inval”，`cbo.inval` 则只丢弃缓存副本。规范还允许实现把 `cbo.clean` 实现为更强的 flush，因为对软件而言，clean 后立即丢弃该组缓存副本的结果不可与单纯 clean 区分。

### 这条指令会做什么

执行 `cbo.clean` 时，处理器首先用 `rs1` 计算有效地址，并经地址翻译得到对应的物理缓存块；随后对一致性缓存中的相关副本执行 clean。可以用如下伪代码概括软件可见语义：

```text
block = 包含有效地址 rs1 的缓存块
if block 自上次 inval / clean / flush 后被 coherent agent 修改:
    将 block 的最新数据写到所有 agent 的公共可见点
保留 block 的缓存副本
```

这里的“保留”是与 `cbo.flush` 的关键区别：完成 clean 后，后续 CPU load 仍可能命中该块的缓存副本；ISA 不保证数据一定驻留在某一级缓存，也不规定写传输经过哪些缓存或互连层级。另一方面，clean 并非普通 store，也不自动建立完整的跨 agent 同步关系。按照 RVWMO，它产生的 clean 操作在保序规则中按 store 对待，并额外保证同一 hart 中、随后对重叠地址执行的 load 不会在全局内存顺序上排到此前的 clean 之前。若需要约束其他访问，软件仍应使用合适的 `FENCE`，并与设备驱动或并发协议规定的同步步骤配合。

这条指令是显式内存访问，会进行地址翻译、PMP 和 PMA 检查；管理类 CBO 在对应物理地址允许 load 或 store 时可以访问该块。访问不被允许时，可能产生 store page-fault、store guest-page-fault 或 store access-fault；故障地址是原始 `rs1`，而非对齐后的块首地址，且不会产生地址未对齐异常。管理类 CBO 不检查或设置页表 Dirty 位，但会按规定处理 Accessed 位。它们忽略 PMA 的 cacheable 属性以及 PBMT 从 cacheable 到 non-cacheable 的降级，不过这不等于可以绕过访问权限。

`Zicbom` 的存在也不代表所有特权级均可执行。`menvcfg`、`senvcfg` 和虚拟化环境的 `henvcfg` 中的 `CBCFE` 控制位可以让较低特权级的 `cbo.clean` 产生非法指令或虚拟指令异常。因此操作系统在把此能力暴露给用户态或虚拟机前，需要完成扩展、缓存块大小和权限配置的发现与管理。

### 这条指令对程序执行有什么帮助

`cbo.clean` 的核心用途是把 CPU 一致性域内已经修改的数据交给非一致 agent。例如 CPU 填充了供 DMA 设备读取的发送缓冲区后，驱动可逐缓存块执行 `cbo.clean`，使设备从与 CPU 共享的可见点取得更新后的内容，而不必等待该缓存块因替换而偶然回写。常见流程是先完成缓冲区的普通 store，再对覆盖范围内的完整缓存块 clean，并按设备互连和驱动模型要求加入 `FENCE` 或 doorbell 写入的顺序控制。

它也适合实现需要“写回但仍希望保留本地缓存副本”的场景。与 `cbo.flush` 相比，clean 后 CPU 若继续读取或修改同一缓冲区，仍可能复用缓存中的数据，从而避免下一次访问必然重新取数；与仅依赖 cache eviction 相比，它又为软件提供了确定的写回时机。实际性能仍依赖于块大小、脏块比例、缓存层次、互连与内存带宽，以及同时发生的访存和 DMA 流量。

软件必须按缓存块边界处理范围：首尾未覆盖完整块时，不能盲目 clean 范围外的相邻数据，除非这些数据也可以一并写回。对于 CPU 与 CPU 之间的正常一致性共享，通常应使用原子操作、锁或 release/acquire 协议，而不是把 `cbo.clean` 当成同步原语；对于设备内存、I/O 区域或体系结构定义之外的 agent，也必须以平台和驱动文档规定的缓存维护流程为准。

参考资料：[RISC-V Instruction Set Manual, Volume I，CMO Extensions for Base Cache Management Operation ISA（Version 1.0.0）](https://docs.riscv.org/reference/isa/unpriv/cmo.html)。

## 香山昆明湖源代码分析

本节**只依据** `/home/yanyusong/cbo-kmhv2/XiangShan/src/` 中的 Chisel/Scala
代码分析。昆明湖没有把 `cbo.clean` 实现为普通 load、普通 store 或单纯的 DCache hit/miss
请求；它在后端被归为 Store Unit uop，在 StoreQueue 中获得“队首执行、清空 StoreBuffer、
发 CMO、等待 CBOAck、写回 ROB”的专用控制路径，并由 DCache MissQueue 内独立的
`CMOUnit` 生成 TileLink `CacheBlockOperation`。

### 代码路径总览

从后端起，数据和控制路径为：

```text
Decode: CBO_CLEAN -> FuType.stu + LSUOpType.cbo_clean
  -> Rename: 为 rs1 分配/读取物理源寄存器，携带 ROB index
  -> Dispatch: 按 Store FuType 分配 SQ 项并送入 Mem Scheduler 的 issue queue
  -> MemBlock / StoreUnit: 计算 VA、TLB read 翻译、PMP 检查，生成 StoreQueue 地址项
  -> StoreQueue: 等待队首与地址有效，先 flush StoreBuffer，再发 cmoOpReq
  -> MemBlock -> DCache MissQueue.CMOUnit: CacheBlockOperation A 请求，等待 CBOAck D 响应
  -> StoreQueue: 接收 cmoOpResp，产生带 flushPipe 的 mmioStout
  -> MemBlock / WbDataPath: 进入标准内存写回通路，带回 ROB index、异常和 flushPipe
  -> ROB: 写回完成后作为 STORE 类型提交，计入 retire
```

其中前半段复用普通后端的 Rename、Dispatch、Issue 和 Writeback 框架；真正针对 CBO Clean
的分支集中在 Decode、StoreUnit、StoreQueue、CMO 接口和 DCache `CMOUnit`。

### 1. Decode：识别 `cbo.clean` 并指定 Store Unit 执行类型

`LSUOpType` 在 [package.scala:583](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala:583)
中定义 CBO 子操作。`cbo_clean` 的低位编码为 `1100`；`isCbo` 先判断 CBO 类别位，
`isCboClean` 再与 clean 子操作匹配：

```scala
def cbo_clean = "b1100".U
def isCbo(op: UInt): Bool = op(3, 2) === "b11".U && (op(6, 4) === "b000".U)
def isCboClean(op: UInt): Bool = isCbo(op) && (op(3, 0) === cbo_clean)
```

Decode 表在 [DecodeUnit.scala:476](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:476)
将指令映射为：

```scala
CBO_CLEAN -> XSDecode(
  SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_clean, SelImm.IMM_S)
```

这组字段决定了后续流水线的基本形态。

- `SrcType.reg`：地址基址来自 `rs1`，因此会在 Rename 后成为一个物理源寄存器。
- `SrcType.DC`：第二源为 don't-care；CBO clean 不携带普通 store data 源寄存器。
- `SrcType.X`：没有整数目的寄存器，正常完成时不写 GPR。
- `FuType.stu`：把 uop 投递到 Store Unit/Store Queue 侧，而不是 Load Unit。
- `LSUOpType.cbo_clean`：携带“这是 clean 而非 flush、inval 或 zero”的子类型，供
  StoreUnit、StoreQueue 和 DCache 使用。
- `SelImm.IMM_S`：采用 store 风格的立即数选择；对合法的 CBO 汇编该 offset 为零。

Decode 还做扩展与特权状态检查。`isCboClean/isCboFlush` 在 `HasCMO` 未实现或 CSR 的
`illegalInst.cboCF` 置位时被送入 illegal-instruction exception；虚拟化限制则使用
`virtualInst.cboCF`，见 [DecodeUnit.scala:882](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:882)。
因此，后续的 CMO 路径仅处理已通过扩展和权限检查的 uop。

### 2. Rename、Dispatch 与内存调度：复用通用乱序框架

`cbo.clean` 没有单独的 Rename 模块分支。Rename 的通用输出条件在
[Rename.scala:414](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala:414)
中要求各类 free list 能分配且 RAB 未处于 walk；输出 uop 同时携带物理源寄存器、ROB index、
`fuType` 与 `fuOpType`。由于 Decode 给出的目的类型为 `SrcType.X`，Rename/写回不会为该
uop 建立有效的整数目的寄存器写入。

Dispatch 也依赖通用 `FuType.isStore` 分类。它在
[NewDispatch.scala:536](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:536)
将 `FuType.isStore` 计入 store/AMO 分配数量，并在
[NewDispatch.scala:514](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:514)
实例化 `LsqEnqCtrl`：

```scala
io.toMem.lsqEnqIO <> lsqEnqCtrl.io.enqLsq
fromRenameUpdate(i).bits.lqIdx := s0_enqLsq_resp(i).lqIdx
fromRenameUpdate(i).bits.sqIdx := s0_enqLsq_resp(i).sqIdx
```

`lsqCanAccept` 是 Dispatch `valid/ready` 的一部分，见
[NewDispatch.scala:444](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala:444)。
所以 CBO clean 与普通 store 一样，必须同时通过 issue queue 选择和 LSQ 容量检查，才能从
Rename fire 到 Dispatch。

内存调度器接收 `CtrlBlock` 输出的 `memUops`，并把 issue 结果送至 datapath，见
[Backend.scala:407](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:407)
和 [Backend.scala:477](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:477)：

```scala
memScheduler.io.fromDispatch.uops <> ctrlBlock.io.toIssueBlock.memUops
dataPath.io.fromMemIQ <> memScheduler.io.toDataPathAfterDelay
```

这说明 CBO clean 在调度阶段仍是标准内存 issue queue 中、等待 `rs1` 物理源就绪的 uop；
并不存在绕过 Rename/Issue 直接进入缓存的快速路径。

### 3. MemBlock 与 Store Unit：把 CBO 当作“带特殊语义的 store address”处理

`MemBlock` 的 OOO→内存接口定义 `issueSta`，见
[MemBlock.scala:118](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:118)。
每个 Store Unit 的端口连接在 [MemBlock.scala:1249](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1249)：

```scala
stu.io.dcache        <> dcache.io.lsu.sta(i)
stu.io.stin          <> io.ooo_to_mem.issueSta(i)
stu.io.lsq           <> lsq.io.sta.storeAddrIn(i)
stu.io.lsq_replenish <> lsq.io.sta.storeAddrInRe(i)
stu.io.tlb           <> dtlb_st.head.requestor(i)
stu.io.pmp           <> pmp_check(...).resp
```

因此 CBO 的地址先经过 Store Unit 的 s0/s1/s2 地址流水，再作为 `storeAddrIn` 写进
StoreQueue；DCache 并不会在这一阶段收到普通的写数据请求。

StoreUnit 内部针对 CBO 做了以下特殊处理。

1. **识别全部 CBO 与“非 zero CBO”。** [StoreUnit.scala:122](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:122)
把 `LSUOpType.isCboAll` 写进 `s0_wlineflag`；
[StoreUnit.scala:159](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:159)
再区分 `s0_isCbo` 和 `s0_isCbo_noZero`。clean 属于后者，因此与 `cbo.zero` 走不同的
权限和后续执行分支。
2. **不因普通 store 对齐规则报错。** `s0_addr_aligned` 末尾 `|| s0_isCbo`，见
[StoreUnit.scala:165](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:165)。
这使 CBO 可以以块内任意地址定位缓存块，而不是要求普通 half/word/doubleword 的对齐。
3. **使用整线形式的掩码语义。** 对 `s0_isCbo`，`s0_mask` 填满 `VLEN/8` 个 1，普通
scalar store 则调用 `genVWmask128`，见
[StoreUnit.scala:200](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:200)。
该掩码不是把 clean 变为向数据阵列写 VLEN 位数据，而是让下游存储侧以整块 CBO 语义处理该项。
4. **TLB 按 read 权限检查。** 对 `s0_isCbo_noZero`，TLB 请求命令为 `TlbCmd.read`，而普通
store 为 `TlbCmd.write`，见
[StoreUnit.scala:215](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215)。
这符合 clean 需要管理/读取现有缓存块而不是提供 store payload 的性质。
5. **异常仍映射到 store 类。** s2 中 `s2_pmp.ld && s2_isCbo_noZero` 会置
`storeAccessFault`，见 [StoreUnit.scala:489](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:489)。
也就是说实现以 load 权限探测 CBO Clean，但向体系结构报告 store access fault；同时 CBO
落在实际 MMIO 区时也会走 store fault 分支。

这五点解释了为什么本指令不经过 Load Unit：它复用了 Store Unit 的地址、TLB、PMP、SQ 和
按序完成机制，但并不是一条普通数据 store。

### 4. LSQ / StoreQueue：CBO 的按序与 StoreBuffer drain 控制

LSQ 顶层显式定义 `cmoOpReq/cmoOpResp` 和 `flushSbuffer`，见
[LSQWrapper.scala:120](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:120)。
它把 Store Unit 的地址/数据接口、ROB 接口、StoreBuffer 接口以及 CMO 接口交给
StoreQueue：

```scala
storeQueue.io.storeAddrIn <> io.sta.storeAddrIn
storeQueue.io.sbuffer     <> io.sbuffer
storeQueue.io.rob         <> io.rob
storeQueue.io.cmoOpReq    <> io.cmoOpReq
storeQueue.io.cmoOpResp   <> io.cmoOpResp
storeQueue.io.flushSbuffer <> io.flushSbuffer
```

上述连接位于 [LSQWrapper.scala:186](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:186)。

StoreQueue 将 MMIO、uncached operation 和 CMO 共用一个专用状态机；其注释明确规定必须
“Store Unit 写回标记 pending → 到达 ROB head 才发请求 → 收响应 → 写回 ROB → ROB 提交”，见
[StoreQueue.scala:820](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:820)。
这个设计让 CBO clean 不会像缓存命中 load 那样在乱序执行后立即退休。

真正开启 CMO 的条件在 [StoreQueue.scala:985](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:985)：

```scala
val deqCanDoCbo = GatedRegNext(
  LSUOpType.isCbo(uop(deqPtr).fuOpType) && allocated(deqPtr) &&
  addrvalid(deqPtr) && !hasException(deqPtr)
) && memBackTypeMM(deqPtr)
```

这里要求当前 SQ 出队项确实是 clean/flush/inval 类 CBO、已分配、地址翻译已完成、没有异常，
并且属于 coherent memory-back 类型。`cbo.zero` 不满足 `LSUOpType.isCbo`，它使用另一条
zero 专用处理路径。

满足上述条件后，StoreQueue **先排空 StoreBuffer**，再发 CMO：

```scala
io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr

io.flushSbuffer.valid := deqCanDoCbo && !cboFlushedSb &&
  (mmioState === s_req) && !io.flushSbuffer.empty || cboZeroFlushSb
```

代码位于 [StoreQueue.scala:1025](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1025)
和 [StoreQueue.scala:1033](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1033)。
`cboMmioPAddr` 在状态机进入请求准备时经 `get_block_addr` 对物理地址对齐到缓存块，见
[StoreQueue.scala:841](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:841)。
所以 clean 不是根据原始字节地址向 DCache 发送一个普通 store，而是发送“该地址所在 block”的
CMO。

CMO 请求 fire 后，StoreQueue 禁止普通 uncache 请求并进入 `s_resp`；收到 CMO 响应才进入
`s_wb`，见 [StoreQueue.scala:1008](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1008)。
响应中的 `denied` 映射为 `storeAccessFault`，`corrupt` 映射为 `hardwareError`，见
[StoreQueue.scala:869](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:869)。

### 5. DCache 的特殊支持：独立 CMOUnit、CacheBlockOperation 与 CBOAck

这是缓存端最关键的专用实现。DCache 定义了独立的 CMO 请求/响应 bundle：

```scala
class CMOReq extends Bundle {
  val opcode = UInt(3.W)   // 0-cbo.clean, 1-cbo.flush, 2-cbo.inval, 3-cbo.zero
  val address = UInt(64.W)
}
class CMOResp extends Bundle {
  val address = UInt(64.W)
  val nderr, denied, corrupt = Bool()
}
```

定义见 [DCacheWrapper.scala:619](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:619)。
`opcode=0` 直接编码 CBO Clean，而不是让普通 DCache store pipeline 重新猜测操作类型。

DCache 的 `MissQueue` 专门实例化一个 `CMOUnit`，见
[MissQueue.scala:1061](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1061)。
它与常规 MSHR entry 并列，却不占用某一个普通 load/store miss entry；自身有四态状态机：

```scala
val s_idle :: s_sreq :: s_wresp :: s_lsq_resp :: Nil = Enum(4)
```

见 [MissQueue.scala:299](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:299)。
状态迁移是：

```scala
s_idle      -- io.req.fire ---------> s_sreq
s_sreq      -- io.req_chanA.fire ---> s_wresp
s_wresp     -- io.resp_chanD.fire --> s_lsq_resp
s_lsq_resp  -- io.resp_to_lsq.fire -> s_idle
```

实现位于 [MissQueue.scala:319](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:319)。
这带来两个硬件保证：CMOUnit 仅在 idle 接收新请求（`io.req.ready := state === s_idle`），并且在
等待 lower-level 响应时不接收第二个 CMO。

在 `s_sreq`，CMOUnit 调用 `edge.CacheBlockOperation`，以块大小 `log2Up(cfg.blockBytes)`、
保存的物理块地址和 CBO opcode 组装 TileLink A 通道请求：

```scala
io.req_chanA.valid := state === s_sreq && !io.wfi.wfiReq
io.req_chanA.bits := edge.CacheBlockOperation(
  fromSource = (cfg.nMissEntries + 1).U,
  toAddress = req.address,
  lgSize = (log2Up(cfg.blockBytes)).U,
  opcode = req.opcode
)._2
```

见 [MissQueue.scala:350](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:350)。
这是 cache 对 clean 的最直接特殊支持：请求的传输粒度来自 DCache 配置的 blockBytes，而不是
源程序所使用的标量 word 大小。

MissQueue 只把 TileLink `CBOAck` 路由给 CMOUnit：

```scala
cmo_unit.io.req <> io.cmo_req
io.cmo_resp <> cmo_unit.io.resp_to_lsq
when (io.mem_grant.valid && io.mem_grant.bits.opcode === TLMessages.CBOAck) {
  cmo_unit.io.resp_chanD <> io.mem_grant
}
```

见 [MissQueue.scala:1230](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1230)。
随后，`cmo_unit.io.req_chanA` 与普通 refill/miss 的 Acquire 请求一起进入 TileLink A 通道仲裁，
见 [MissQueue.scala:1254](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1254)。
因此 CBO clean 是 cache/互连可识别的事务，而非“把 line 读出后软件写回”的模拟。

在 DCache 外层，CMO 接口直接接入 MissQueue：

```scala
io.cmoOpReq <> missQueue.io.cmo_req
io.cmoOpResp <> missQueue.io.cmo_resp
```

见 [DCacheWrapper.scala:1532](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1532)。
在 CMOUnit 收到 CBOAck 后，`denied/corrupt` 被锁存并经 `CMOResp` 原样返还 LSQ，见
[MissQueue.scala:334](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:334)
与 [MissQueue.scala:363](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:363)。

### 6. 完成、Writeback、Commit 与 Retire

StoreQueue 接收成功或错误响应后并不直接删除 ROB 项，而是先产生 `mmioStout`。该输出携带原
uop、SQ index、异常向量，并对 CBO 设置 `flushPipe`：

```scala
io.mmioStout.valid := mmioState === s_wb && !isVec(deqPtr)
io.mmioStout.bits.uop := uncacheUop
io.mmioStout.bits.uop.sqIdx := deqPtrExt(0)
io.mmioStout.bits.uop.flushPipe := deqCanDoCbo // flush Pipeline to keep order in CMO
```

见 [StoreQueue.scala:1055](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1055)。
这解释了 CBO clean 即使不写寄存器，也必须等待 lower-level acknowledgement 后才能被 ROB
认为执行完成；`flushPipe` 则要求后端维持 CMO 的顺序语义。

MemBlock 把这类“由 StoreQueue 完成”的输出复用到第一条 store 写回端口：

```scala
sqOtherStout.valid := lsq.io.mmioStout.valid || lsq.io.cboZeroStout.valid
...
when (otherStout.valid && !storeUnits(0).io.stout.valid) {
  stOut(0).valid := true.B
  stOut(0).bits  := otherStout.bits
}
```

见 [MemBlock.scala:1361](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1361)。
`stOut` 是 `writebackSta` 的组成部分，导出到 `mem_to_ooo.writeBack`，见
[MemBlock.scala:545](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:545)。

后端的 `WbDataPath` 接收所有 memory writeback，并逐项转送 `robIdx`、`rfWen`、异常、
`flushPipe`、`lqIdx` 与 `sqIdx`，见
[Backend.scala:671](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/Backend.scala:671)：

```scala
sink.bits.robIdx := source.bits.uop.robIdx
sink.bits.intWen.foreach(_ := source.bits.uop.rfWen)
sink.bits.exceptionVec.foreach(_ := source.bits.uop.exceptionVec)
sink.bits.flushPipe.foreach(_ := source.bits.uop.flushPipe)
sink.bits.sqIdx.foreach(_ := source.bits.uop.sqIdx)
```

对正常 clean，`rfWen` 保持 0；对失败 clean，StoreQueue 设置的异常向量随同一写回路径进入
ROB。ROB 只有在条目已完成写回且未被阻塞时产生 `commitValid`，其核心条件为
`commit_vDeqGroup && commit_wDeqGroup`，见
[Rob.scala:791](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:791)。

由于 CBO 在 Decode 时被标记为 `FuType.stu`，它的 commit type 是 STORE。ROB 在提交时将
STORE 类型计入 `stCommitVec` 并向 LSQ 产生 `scommit`，见
[Rob.scala:839](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:839)。
最后，ROB 以 `commitValid` 和指令大小计算 `retireCounter`、更新 retired-instruction 性能计数，
见 [Rob.scala:1256](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:1256)。

综上，`cbo.clean` 的“执行完成”不是 Store Unit 产生地址时，也不是 DCache 发出 A 通道事务时；
源码定义的完成点是：**DCache 接收 CBOAck → CMOUnit 回送 CMOResp → StoreQueue 形成带异常/flush
信息的 writeback → ROB 的 writeback 条件满足 → 作为 STORE commit 并计入 retire**。

## CBO Clean 演示程序

演示程序位于 `/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_clean`。其
`Makefile` 通过 `MARCH` 加入 `zicbom`，使 GNU 工具链能够接受 `cbo.clean` 汇编助记符；
程序使用一个 64 B 对齐的 `volatile` 缓存块作为唯一目标。`volatile` 使 fill、checksum 和
clean 前后的读写都保留为真实内存访问，而内联汇编的 `memory` clobber 阻止编译器把前后访存
重排到 `cbo.clean` 两侧。

```c
#include <klib.h>
#include <stdint.h>

#define CBO_BLOCK_BYTES 64
#define WORD_COUNT (CBO_BLOCK_BYTES / sizeof(uint64_t))

static volatile uint64_t demo_block[WORD_COUNT]
    __attribute__((aligned(CBO_BLOCK_BYTES)));

static inline void cbo_clean(void *address) {
  __asm__ volatile("cbo.clean 0(%0)" : : "r"(address) : "memory");
}

static void fill_block(uint64_t seed) {
  for (int index = 0; index < WORD_COUNT; index++) {
    demo_block[index] = seed + (uint64_t)index;
  }
}

static uint64_t checksum_block(void) {
  uint64_t checksum = 0;

  for (int index = 0; index < WORD_COUNT; index++) {
    checksum += demo_block[index];
  }
  return checksum;
}

int main(void) {
  const uint64_t initial_seed = 0x1122334455667700ULL;
  const uint64_t post_clean_value = 0xa500000000000000ULL;

  printf("CBO Clean demonstration starts\n");
  printf("target block: 0x%lx, bytes: %d\n", (unsigned long)demo_block,
         CBO_BLOCK_BYTES);

  fill_block(initial_seed);
  printf("before cbo.clean: word[0]=0x%lx word[7]=0x%lx checksum=0x%lx\n",
         (unsigned long)demo_block[0], (unsigned long)demo_block[WORD_COUNT - 1],
         (unsigned long)checksum_block());

  cbo_clean((void *)demo_block);
  printf("after cbo.clean:  word[0]=0x%lx word[7]=0x%lx checksum=0x%lx\n",
         (unsigned long)demo_block[0], (unsigned long)demo_block[WORD_COUNT - 1],
         (unsigned long)checksum_block());

  demo_block[0] = post_clean_value;
  demo_block[WORD_COUNT - 1] = post_clean_value + (WORD_COUNT - 1);
  printf("after post-clean stores: word[0]=0x%lx word[7]=0x%lx checksum=0x%lx\n",
         (unsigned long)demo_block[0], (unsigned long)demo_block[WORD_COUNT - 1],
         (unsigned long)checksum_block());
  printf("CBO Clean demonstration ends\n");

  return 0;
}
```

可使用以下命令构建：

```sh
source ~/cbo-kmhv2/env.sh
cd ~/cbo-kmhv2/nexus-am/apps/cbo_clean
make ARCH=riscv64-xs
```

`cbo_clean-riscv64-xs.elf` 的相关反汇编如下。前半部分是对 8 个 word 的普通 store 和
load/checksum，`s0` 保存 `demo_block` 的基地址；目标 CBO 的机器码是 `0x0014200f`，随后
立刻出现同一块的 load/checksum，最后两条 `c.sd` 是 clean 后的普通 store。

```text
80000174: 00c786b3  add     a3,a5,a2
8000017c: 20876733  sh3add  a4,a4,s0
80000180: e314      c.sd    a3,0(a4)
...                         # fill_block: 写入 demo_block[0..7]
80000188: 600c      c.ld    a1,0(s0)
8000018a: 7c10      c.ld    a2,56(s0)
80000192: 2087e733  sh3add  a4,a5,s0
80000196: 6318      c.ld    a4,0(a4)
...                         # checksum_block: clean 前读取 8 个 word
800001ac: 0014200f  cbo.clean (s0)
800001b0: 600c      c.ld    a1,0(s0)
800001b2: 7c10      c.ld    a2,56(s0)
800001ba: 2087e733  sh3add  a4,a5,s0
800001be: 6318      c.ld    a4,0(a4)
...                         # checksum_block: clean 后再次读取 8 个 word
800001da: e018      c.sd    a4,0(s0)
800001e0: fc1c      c.sd    a5,56(s0)
```

这也与波形分析的目标一致：`0x800001ac` 是 Decode、Rename、Store Unit、StoreQueue 和
CMOUnit 全程追踪的 PC，运行时 `s0` 对应的块地址为 `0x80001780`。

该程序刻意把 `cbo.clean` 放在两组普通内存访问之间，以演示它是“缓存块管理操作”而不是
会改变寄存器或内存数据的普通算术/写零指令。

1. **clean 前制造脏块并读取它。** `fill_block()` 写入 8 个连续的 64-bit word（正好 64 B），
   然后打印首尾 word 和 checksum。这些 store 为目标块创造需要 clean 的修改，checksum 的
   load 则使示例中的 clean 前访存可见。
2. **对该块发射 CBO Clean。** 内联汇编以 `demo_block` 地址作为 `rs1`，生成
   `cbo.clean 0(s0)`。预期硬件将包含 `0x80001780` 的缓存块 clean；该指令没有目的寄存器，
   程序不会从它取得返回值。
3. **clean 后验证内容不变。** 再次读取相同首尾 word 和 checksum；结果应与 clean 前一致，
   因为 clean 的架构可见效果是写回脏数据而非修改缓存块内容。本次仿真输出的两个 checksum
   均为 `0x89119a22ab33b81c`。
4. **证明后续普通 store 仍可发生。** 程序将 `word[0]` 和 `word[7]` 改写为
   `0xa500000000000000` 和 `0xa500000000000007`，随后重算 checksum；这说明 CBO 完成后该块
   仍可被正常读取和写入。本次输出的 post-clean checksum 是 `0xb0c339a0066ca1c`。

该单核演示和其波形能够证明昆明湖实际发出了 CMO Clean 请求并返回成功；它不引入非一致 DMA
观察者，因此不能单凭 printf 直接测得外设侧可见性。若要验证完整的 CPU→DMA 软件协议，应在
clean 后加入平台规定的同步/doorbell，并让非一致 agent 读取该缓冲区。

## 波形图分析

### 分析对象、方法与约定

- 波形文件：`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-12-11-26.fst`。
- 目标指令：PC `0x800001ac`、机器码 `0x0014200f`、反汇编 `cbo.clean (s0)`；`s0` 在本程序中保存目标块首地址 `0x80001780`。
- 本节使用 `/home/yanyusong/wavekit` 开源库的 `FstReader` 读取 FST；时钟为 `TOP.clock`，所有周期号均为**上升沿采样**的绝对 cycle，`time` 为波形原生时间单位。每个 Decoupled 接口均以 `fire = valid && ready` 判断真正传输。
- 波形层级的核心前缀为 `TOP.SimTop.cpu.l_soc.core_with_l2.core`。Decode 前端入口从 `backend.inner_ctrlBlock.decode` 开始；目标 uop 在 Rename 后的 ROB index 为 `66`，Store Queue index 为 `9`。ROB index 是循环编号，故本分析在 Rename 之后始终以“ROB 66 + 周期窗口 + `fuOpType=0x0c`”共同识别目标，不能只用 ROB 数值在全程搜索。

### 总体逐周期时间线

| 上升沿 cycle（time） | 波形位置及 `fire` | 目标指令的状态与关键值 |
|---:|---|---|
| 25890（51780） | `decode.io_in_3` | `valid=1, ready=1`；`PC=0x800001ac`、`instr=0x0014200f`，译码输出 `fuType=0x10000`、`fuOpType=0x0c`。 |
| 25891（51782） | `rename.io_in_3` / `rename.io_out_3` | 两个边界均 fire；Rename 分配 `robIdx=66`，保持相同 PC、指令、功能类型和功能操作码。 |
| 25892（51784） | `dispatch.io_fromRename_3`、`inner_memScheduler.io_fromDispatch_uops_2` | Dispatch 输入和内存调度器入队均 fire；`robIdx=66`、`fuOpType=0x0c`，物理源寄存器 `psrc_0=148`。 |
| 25893（51786） | `dispatch.io_toMem_lsqEnqIO_req_3` | 内存侧 LSQ 分配请求 `valid=1`；该 uop 的 `sqIdx=9`，随后以 Store Unit 路径执行。 |
| 25898（51796） | `memBlock.inner_StoreUnit_1.s0_isCbo` | `s0_isCbo=1`，表明目标进入 Store Unit 1 的 CBO 专用控制分支。 |
| 25899（51798） | Store Unit 1 → LSQ | `io_lsq.valid=1`，携带 `robIdx=66`、`fuOpType=0x0c`、VA/PA `0x80001780`；同时 s1 的 CBO 标志有效。 |
| 26770（53540） | `storeQueue.deqCanDoCbo` | StoreQueue 已判定该 CBO 可由队首发起；之后进入 CMO 的请求准备过程。 |
| 26778–26790（53556–53580） | `storeQueue.io_flushSbuffer_valid` | `valid=1, empty=0`，StoreQueue 先请求清空 StoreBuffer，防止先前普通 store 与 CMO 重排。 |
| 26791（53582） | `io_flushSbuffer_empty` | StoreBuffer 清空，`empty=1`。 |
| 26792（53584） | StoreQueue/CMOUnit 请求 | `cboFlushedSb=1`，`io_cmoOpReq.fire`；`opcode=0`、`address=0x80001780`。 |
| 26793（53586） | DCache `CMOUnit.io_req_chanA.fire` | TileLink A 通道接收 CacheBlockOperation；地址仍为 `0x80001780`，总线 opcode 为 `12`（CBO Clean）。 |
| 26794–26863（53588–53726） | `CMOUnit.state=2` | 等待 TileLink D 响应；CMOUnit 不接受第二个 CMO 请求。 |
| 26864（53728） | `CMOUnit.io_resp_chanD.fire` | D 通道响应到达。 |
| 26865（53730） | `CMOUnit.io_resp_to_lsq.fire` / StoreQueue `io_cmoOpResp.fire` | 结果送回 LSQ；`denied=0`、`corrupt=0`。 |
| 26866（53732） | `storeQueue.io_mmioStout.fire` | ROB 66、SQ 9 完成写回；`flushPipe=1`、`rfWen=0`。 |
| 26872（53744） | `inner_ctrlBlock.io_redirect_valid` | 观察到 redirect；它发生在 CMO 已应答、目标写回之后。 |
| 26878（53756） | `rob.difftest_commit` lane 0 | `PC=0x800001ac`、`robIdx=66`、`skip=0`、`rfwen=0`，目标正常退休。 |

下文解释每一边界的生产者、消费者、控制意义及其 Chisel 根据。

### 1. Decode：将 CBO Clean 归类为 Store Unit 操作

目标在 Decode 的第 3 lane 出现。`decode.io_in_3_valid && decode.io_in_3_ready` 为 1，故 cycle 25890 的输入传输已经发生；同一采样点 `io_in_3_bits_pc=0x800001ac`、`io_in_3_bits_instr=0x0014200f`。Decoder 输出中 `fuType=0x10000`、`fuOpType=0x0c`：前者是 Store Unit 类别，后者是 `cbo_clean` 的内部 LSU 操作编码。

这一分类来自 [DecodeUnit.scala:476](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:476)：

```scala
CBO_CLEAN -> XSDecode(
  SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_clean, SelImm.IMM_S)
```

含义如下。

- `SrcType.reg` 令 `rs1` 成为真正的地址源；本程序的 `s0` 之后经物理寄存器 `148` 传递。
- `SrcType.DC` 表示不存在第二个通用寄存器源，`SrcType.X` 表示没有整数目的寄存器结果。
- `FuType.stu` 使该 uop 走 Store Unit 而非 Load Unit；`LSUOpType.cbo_clean` 保留 CMO 子类型给后续流水线和 StoreQueue。
- `SelImm.IMM_S` 与该编码格式一致；汇编的偏移量为零，实际地址完全来自 `rs1`。

Decode 对 CBO clean/flush 的合法性还会检查 `HasCMO` 和 CSR 的 `cboCF` 权限位；相关非法指令组合见 [DecodeUnit.scala:882](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:882)。本次波形中该指令继续进入 Rename 且最终 `skip=0` 退休，因此没有触发该非法指令路径。

### 2. Rename、Dispatch 与内存调度器：从 PC 身份切换到 ROB 身份

cycle 25891，`rename.io_in_3` 与 `rename.io_out_3` 都为 `valid=ready=1`。输入/输出的 PC、指令、`fuType=0x10000` 和 `fuOpType=0x0c` 不变；输出新增 `robIdx=66`。从这一点起，PC 只是交叉校验，ROB 66 才是跨后端和内存层的主身份。

cycle 25892，`dispatch.io_fromRename_3.fire` 把同一 uop 交给 Dispatch；波形显示：

```text
robIdx=66, fuOpType=0x0c, psrc_0=148
```

`psrc_0=148` 是 Rename 后承载 `s0` 值的物理寄存器编号；它是 Store Unit 地址生成的输入，不是要写回的目的寄存器。没有 `pdest`/`rfWen` 的有效结果通路，这与无 GPR 结果的 CBO 语义相符。

同一 cycle，`backend.inner_memScheduler.io_fromDispatch_uops_2.fire` 也观察到 `robIdx=66`、`fuOpType=0x0c`、`psrc_0=148`。这说明 Dispatch 已把 CBO 放入内存调度器；波形没有显示此 uop 在该接口上的 `valid && !ready`，所以不存在该边界的背压。MemBlock 的对外 issue 端口定义为 `issueSta`（Store address）Decoupled 接口，见 [MemBlock.scala:118](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:118)。

cycle 25893，`dispatch.io_toMem_lsqEnqIO_req_3_valid=1`，uop 带 `robIdx=66` 和 `sqIdx=9` 进入 LSQ 分配路径。波形中的 `lqIdx` 字段在该 bundle 中也存在，但本 CBO 后续没有进入任何 Load Unit 的 `io_ldin`：在 cycle 25890–26880 的窗口内，LoadUnit 0、1、2 的 `io_ldin_valid && robIdx==66` 命中数均为零。因此不能把 bundle 内的 `lqIdx` 数值解释成此 CBO 发起了 load；实际执行与完成由 SQ/CMO 路径承担。

### 3. Store Unit：CBO 特有的地址、TLB、掩码和权限处理

MemBlock 把 `issueSta(i)` 连接到 `storeUnits(i).io.stin`，并把 Store Unit 的地址结果连接到 LSQ、DTLB、PMP 和 DCache，见 [MemBlock.scala:1249](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1249)：

```scala
stu.io.dcache        <> dcache.io.lsu.sta(i)
stu.io.stin          <> io.ooo_to_mem.issueSta(i)
stu.io.lsq           <> lsq.io.sta.storeAddrIn(i)
stu.io.lsq_replenish <> lsq.io.sta.storeAddrInRe(i)
stu.io.tlb           <> dtlb_st.head.requestor(i)
```

目标使用 `StoreUnit_1`。cycle 25898 的 `s0_isCbo=1` 是第一条专用处理证据；下一周期 `io_lsq_valid=1`，携带 `robIdx=66`、`fuOpType=0x0c`、`vaddr=0x80001780`、`paddr=0x80001780`。地址翻译没有改变该裸机地址，且该路径没有出现异常向量。

StoreUnit 的 CBO 控制不是普通 store 的简单复用：

```scala
val s0_wlineflag = Mux(s0_use_flow_rs, LSUOpType.isCboAll(s0_uop.fuOpType), false.B)
val s0_isCbo = s0_use_flow_rs && LSUOpType.isCboAll(s0_stin.uop.fuOpType)
val s0_isCbo_noZero = s0_use_flow_rs && LSUOpType.isCbo(s0_stin.uop.fuOpType)
```

代码位于 [StoreUnit.scala:122](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:122) 和 [StoreUnit.scala:159](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:159)。`cbo.clean` 属于 `isCbo` 而不是 `cbo.zero`，所以 `s0_isCbo_noZero=1`。它造成以下三个关键差异：

1. **不做普通对齐异常。** `s0_addr_aligned` 以 `|| s0_isCbo` 放宽 CBO 的对齐约束，见 [StoreUnit.scala:165](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:165)。即使 `rs1` 不是块对齐地址，硬件仍可选择所在缓存块；本程序恰好使用块首地址。
2. **以整条缓存线语义进入存储侧。** `s0_mask` 对 CBO 使用全 `VLEN/8` 的 1 掩码，而普通标量 store 使用访问大小生成的掩码，见 [StoreUnit.scala:200](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:200)。这不是写入 128 bit 数据，而是让后续存储侧按 CBO/整块操作处理。
3. **TLB 按 read 权限翻译，异常仍按 store 报告。** `s0_isCbo_noZero` 选择 `TlbCmd.read`，见 [StoreUnit.scala:215](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215)。在 s2，PMP 的 load 权限失败会映射为 `storeAccessFault`（`s2_pmp.ld && s2_isCbo_noZero`），见 [StoreUnit.scala:489](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:489)。这正是 CBO Clean 需要读取/管理缓存块、但体系结构异常类别仍属于 store 的实现方式。

注意：`s1_isCbo`、`s2_isCbo` 在该 FST 中没有独立的 valid 位，空闲时可保持先前寄存器值。因此本分析只把 cycle 25898 的 `s0_isCbo=1` 和 cycle 25899 的 `io_lsq_valid=1` 当作传输证据，不把随后静态保持的 s1/s2 位误判为该指令长时间占用 Store Unit。

### 4. LSQ / StoreQueue：先等待队首，再清空 StoreBuffer，最后允许 CMO

StoreQueue 把 CBO 与 MMIO/uncached store 放进同一“必须在 ROB 队首完成”的控制框架；状态机注释明确规定“Store Unit 写回标记 pending → 到达 ROB head 后发请求 → 收响应 → 写回 ROB → ROB commit”，见 [StoreQueue.scala:820](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:820)。目标在 cycle 25899 写入 SQ 9 后，并没有立即向 DCache 发 CMO：它一直保留到可由队首执行的时刻。

该门控的核心是：

```scala
val deqCanDoCbo = GatedRegNext(
  LSUOpType.isCbo(uop(deqPtr).fuOpType) && allocated(deqPtr) &&
  addrvalid(deqPtr) && !hasException(deqPtr)
) && memBackTypeMM(deqPtr)

io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr
```

见 [StoreQueue.scala:985](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:985) 和 [StoreQueue.scala:1025](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1025)。其中 `cboMmioPAddr` 在请求准备时由 `get_block_addr(...)` 生成，故传到 CMO 的是缓存块地址，见 [StoreQueue.scala:841](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:841)。

波形把这组门控逐项展示出来：

- cycle 26770，`deqCanDoCbo` 从 0 变为 1，说明 SQ 9 已分配、地址有效、无异常且允许进入 coherent memory-back 路径。
- cycle 26778，`mmioState=1 (s_req)`、`io_flushSbuffer_valid=1` 而 `io_flushSbuffer_empty=0`。StoreQueue 尚未允许 CMO request，而是先请求清空 StoreBuffer。
- cycle 26791，`io_flushSbuffer_empty=1`；cycle 26792，`cboFlushedSb=1`，`io_cmoOpReq_valid && ready=1`。请求的 `opcode=0`、`address=0x80001780`，与目标 CBO Clean 和 Store Unit 地址一致。

先 flush StoreBuffer 的逻辑由 [StoreQueue.scala:1033](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1033) 实现：只有 `io.flushSbuffer.empty` 后才置位 `cboFlushedSb`。这解释了从 `deqCanDoCbo` 到真正 `io_cmoOpReq.fire` 的 22 个周期，而不是把这段延迟归因于 DCache backpressure。

CMO 响应在 cycle 26865 与 `io_cmoOpResp.ready=1` 握手；`denied=0`、`corrupt=0`。StoreQueue 随即在 cycle 26866 令 `io_mmioStout.fire`，输出 `robIdx=66`、`sqIdx=9`、`rfWen=0`、`flushPipe=1`。对应代码在 [StoreQueue.scala:1008](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1008) 和 [StoreQueue.scala:1055](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1055)：CMO 完成后转入 `s_wb`，并显式设置 `flushPipe := deqCanDoCbo`（源代码注释为“flush Pipeline to keep order in CMO”）。

### 5. MemBlock 与 DCache：CMOUnit 的状态机和 TileLink 往返

MemBlock 将 LSQ 的 `cmoOpReq/cmoOpResp` 直接连接到 DCache，见 [MemBlock.scala:1210](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1210)：

```scala
lsq.io.cmoOpReq  <> dcache.io.cmoOpReq
lsq.io.cmoOpResp <> dcache.io.cmoOpResp
```

DCache 的请求类型定义把 `opcode=0` 指定为 `cbo.clean`，见 [DCacheWrapper.scala:619](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:619)：

```scala
class CMOReq extends Bundle {
  val opcode = UInt(3.W)   // 0-cbo.clean, 1-cbo.flush, 2-cbo.inval, 3-cbo.zero
  val address = UInt(64.W)
}
```

实际执行单元是 MissQueue 内的 `CMOUnit`。其状态编码和握手迁移为 `s_idle(0) → s_sreq(1) → s_wresp(2) → s_lsq_resp(3) → s_idle`，见 [MissQueue.scala:299](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:299)。波形与源码逐项一致：

```scala
// s_idle
when (io.req.fire) { state_next := s_sreq }
// s_sreq
when (io.req_chanA.fire) { state_next := s_wresp }
// s_wresp
when (io.resp_chanD.fire) { state_next := s_lsq_resp }
// s_lsq_resp
when (io.resp_to_lsq.fire) { state_next := s_idle }
```

上述代码位于 [MissQueue.scala:319](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:319)。本次 CMOUnit 波形如下：

- **请求接收（26792）**：`io_req.valid=1, io_req.ready=1`，state 仍为 `0`；寄存器捕获 `opcode=0` 和 `address=0x80001780`。`io.req.ready := state === s_idle` 的来源见 [MissQueue.scala:350](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:350)。
- **发出 TileLink A（26793）**：state 变为 `1`，`io_req_chanA.valid=1, ready=1`，因此 fire。`CMOUnit` 用保存的请求地址和操作码构造 `edge.CacheBlockOperation`，见 [MissQueue.scala:352](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:352)。波形中 A 地址为 `0x80001780`、opcode 为 `12`，对应 TileLink CBO Clean。
- **等待 D（26794–26863）**：state 为 `2`；`io_resp_chanD.ready=1`，但 `valid=0`，共持续 70 个完整采样周期。这是 CMO 的外部响应等待，不是 StoreQueue 或 issue queue 的 ready/valid 堵塞。
- **接收 TileLink D（26864）**：`io_resp_chanD.valid=1, ready=1`，fire 后捕获 `denied/corrupt`。本次两者均为 0。
- **回送 LSQ（26865）**：state 为 `3`，`io_resp_to_lsq.valid=1, ready=1`，将同一块地址和错误状态发送回 StoreQueue。输出字段定义和赋值见 [MissQueue.scala:363](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:363)。
- **重新可接收（26866）**：state 回到 `0`，`io_req_ready=1`。该 CMO 从 StoreQueue 发请求到 LSQ 收响应相隔 73 个周期。

### 6. 写回、redirect 与退休

`cbo.clean` 没有数据写回寄存器，但必须等 CMO 成功/失败状态返回后才能让 ROB 完成。StoreQueue 的 `io_mmioStout` 在 cycle 26866 fire，波形明确给出 `rfWen=0`、`flushPipe=1`；DCache 响应没有 `denied/corrupt`，所以没有 access fault 或 hardware error。StoreQueue 源码也在 CMO 响应错误时分别置 `storeAccessFault` 和 `hardwareError`，见 [StoreQueue.scala:869](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:869)。

cycle 26872，CtrlBlock 的 `io_redirect_valid` 为 1；这是写回的 `flushPipe=1` 之后 6 个周期观测到的流水线 redirect。`flushPipe` 的源头以及“保持 CMO 顺序”的注释是直接代码依据；但当前 FST 没有把 redirect 的 producer ROB index 作为同一条可直接读取的绑定字段导出，因此只能确认两者的时序一致，不能仅凭该波形断言 redirect 的唯一 producer。重要的是目标 uop 没有被杀死：cycle 26878，`rob.difftest_commit` lane 0 显示 `PC=0x800001ac, robIdx=66, skip=0, rfwen=0`，完成架构退休。

ROB 对 store 类型提交会向 LSQ 发出 `scommit/pendingst`，相关产生逻辑见 [Rob.scala:839](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:839)。该接口与 StoreQueue 的“仅在队首处理 CMO”状态机共同保证：后续指令不会在 CMO 返回前把该 CBO 当作已完成的普通无副作用操作。

### 7. 对 Store Unit、Load Unit、Cache 和 MemBlock 的结论

- **Store Unit**：这是 CBO Clean 的执行入口而非普通数据写入口。它生成 `s0_isCbo/s0_isCbo_noZero`，放宽对齐检查，选择 TLB read 权限，保留 store 类异常语义，并把 VA/PA 与 CBO uop 送入 SQ。波形在 StoreUnit 1 的 `s0_isCbo` 和 `io_lsq.valid` 中直接证实该路径。
- **Load Unit**：本指令没有进入任何 LoadUnit。目标生命周期内三个 `inner_LoadUnit_{0,1,2}.io_ldin_valid && robIdx==66` 均未命中；CBO 的“需要读取缓存状态”由 Store Unit 的 TLB read/PMP 控制和 DCache CMO 事务完成，不能误解为普通 load pipeline。
- **LSQ / StoreQueue**：它保存 SQ 9，等队首条件满足后强制先清空 StoreBuffer，再将 `opcode=0` 和块地址送往 DCache；响应后产生无 GPR 写回、带 `flushPipe` 的完成消息。这是本实现保证 CMO 顺序的核心。
- **MemBlock**：负责把内存调度器的 `issueSta` 接入 Store Unit，并把 LSQ 的 CMO request/response 无损连接到 DCache；它不把 CBO 当成 LoadUnit/DCache 普通 load request。
- **DCache / MissQueue CMOUnit**：把 CMO 转成专用 TileLink CacheBlockOperation，等待 D 通道，再把错误状态返回 LSQ。本次地址、CBO opcode、A/D 握手和无错误响应均与 Chisel 状态机一致。

因此，波形不仅证明了 `cbo.clean` 被译码和退休，还证明了昆明湖 V2 的实际特殊处理顺序：**Store Unit 分类与翻译 → SQ 队首等待 → StoreBuffer drain → DCache CMOUnit / TileLink CBO → LSQ/StoreQueue 完成写回与流水线顺序控制 → ROB 退休**。
