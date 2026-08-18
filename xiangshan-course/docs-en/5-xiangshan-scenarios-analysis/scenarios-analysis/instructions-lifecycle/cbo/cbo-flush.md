# 香山昆明湖执行 CBO Flush 指令的流程分析

## CBO Flush 指令介绍

### 这条指令是什么

`cbo.flush`（Cache Block Flush）是 RISC-V `Zicbom`（Cache-Block Management Operations）扩展中的缓存块管理指令。它没有目的寄存器和返回值，唯一的操作数是地址；汇编通常写作 `cbo.flush 0(a0)`，其中 `a0` 是编码中的 `rs1`。该地址用于定位其所在的一个完整缓存块，指令再对执行 hart 所属 coherent agent 可访问的整组一致性缓存执行 flush 操作。

flush 的架构定义是**原子地执行一次 clean，随后执行一次 inval**。前半部分会在缓存块自上次 `cbo.inval`、`cbo.clean` 或 `cbo.flush` 后被一致性 agent 修改时，把最新数据写至所有 agent 的公共可见点；后半部分则释放该组一致性缓存中该缓存块的全部副本。因此，执行完成后，CPU 对该块的旧缓存副本不再可用，而先前 CPU store 的数据已对非一致 agent 可见。这里的“原子”仅指 clean 与 inval 两个缓存管理动作作为一个 flush 操作连续完成，不能将它理解成对块内数据的读写原子性或并发同步原语。

操作粒度是 ISA 定义的缓存块，不是一个字、双字或任意字节区间。块大小实现相关，软件应从执行环境的发现机制取得 `Zicbom` 管理类 CBO 的块大小，再按此大小遍历缓冲区；不能假定等于 64 B 或某一级 cache line 大小。`rs1` 无须对齐，包含该有效地址的缓存块就是目标块。若指令写作带 `offset` 的形式，offset 必须为 `0`；也可以省略。

### 这条指令会做什么

可以把 `cbo.flush` 的软件可见效果理解为：

```text
block = 包含有效地址 rs1 的缓存块
if block 自上次 inval / clean / flush 后被 coherent agent 修改:
    将 block 的最新数据写到所有 agent 的公共可见点
从执行 hart 可访问的 coherent caches 中释放 block 的全部副本
```

这意味着它既解决“CPU 写入何时对非一致设备可见”的问题，也解决“CPU 下次访问不应继续使用旧缓存副本”的问题。典型例子是双向 DMA 缓冲区的所有权切换：CPU 将输出数据写入内存后，flush 能写回脏数据并移除本地副本；若设备随后改写该缓冲区，CPU 之后重新读取时不会继续从此前保留的副本取值。是否重新分配、从哪个层级取数以及何时发生总线事务，均由实现决定。

在 RVWMO 中，flush 操作在保序规则中按 store 对待，并存在额外的重叠地址规则：同一 hart 中，位于 flush 之后且访问重叠地址的 load，不能在全局内存顺序上排到该 flush 之前。但 flush 本身不是全栅栏，不能自动排序所有此前和此后的普通内存访问；软件要把缓冲区访问、缓存维护和通知设备的 MMIO/doorbell 写入建立正确顺序时，仍须遵从平台规定并使用适当的 `FENCE`。它也不取代多 hart 间的锁、原子操作或 release/acquire 发布协议。

和其他管理类 CBO 一样，`cbo.flush` 是显式内存访问：有效地址需要翻译，且通过 PMP、PMA 与页表权限检查。若不能访问，指令会以 store page-fault、store guest-page-fault 或 store access-fault 的形式报告异常；`*tval` 中记录的是未对齐也未经向下取整的 `rs1` 值，且 CMO 不会产生地址未对齐异常。管理类 CBO 忽略 PMA cacheable 属性及 PBMT 的 cacheability 降级，但不能跨越访问控制。较低特权级能否执行还受 `menvcfg`、`senvcfg`、`henvcfg` 中 `CBCFE` 的控制；未获允许时会产生非法指令或虚拟指令异常。

### 这条指令对程序执行有什么帮助

`cbo.flush` 最直接的价值是支持非一致 DMA 的缓冲区交接。以 CPU 写、设备读为例，CPU 写完若干完整缓存块后，逐块 flush 可以确保脏数据离开 CPU 缓存并对设备可见；以设备写、CPU 读为例，在设备已完成写入且完成状态已按平台规则同步后，flush 可让 CPU 丢弃其可能持有的旧副本，再读取设备更新后的数据。相比只执行 `cbo.clean`，flush 额外避免了 CPU 将旧副本留在缓存中；相比只执行 `cbo.inval`，它不会直接丢弃尚未写回的脏数据。

它同样适用于固件、内核和驱动中需要明确缩小缓存所有权范围的场景。不过 flush 往往比 clean 有更高的后续访问代价：因为后续 CPU 访问该块可能需要重新从更低层或内存取数。因此，若数据交给设备后 CPU 仍要频繁读取且设备不会修改它，clean 通常更合适；若需要让 CPU 放弃该副本，则 flush 更符合语义。

软件应仅对完整覆盖的缓存块使用该快速路径，并小心首尾部分以及同一缓存块内无关数据的影响。完成缓存维护不等于完成设备协议：还需要保证设备确实支持并遵守该公共可见点、使用与设备一致的地址和内存属性，并配合状态寄存器、I/O 屏障及并发同步。对系统定义之外的 agent，`Zicbom` 不保证具有预期效果。

参考资料：[RISC-V Instruction Set Manual, Volume I，CMO Extensions for Base Cache Management Operation ISA（Version 1.0.0）](https://docs.riscv.org/reference/isa/unpriv/cmo.html)。

## 香山昆明湖源代码分析

本节只依据昆明湖 V2 的 `~/cbo-kmhv2/XiangShan/src/` 源码说明
`cbo.flush` 的实现。它在前端看来是一条普通的非控制流指令，但后端把它归入 store
功能单元、分配 Store Queue（SQ）项；地址翻译完成后，StoreQueue 不把它作为普通
store 写入 StoreBuffer，而是等待顺序条件满足后向 DCache 的专用 CMO 通道发送
Cache-Block-Operation。收到下级的 `CBOAck` 后，StoreQueue 才产生 store 类写回；ROB
使该项完成、在队首提交，并以 `flushPipe` 清除所有年轻流水工作。

整体数据/控制路径可以概括为：

```text
Decode(CBO_FLUSH)
  -> Rename(分配 robIdx；无目的寄存器重命名)
  -> Dispatch(按 FuType.stu 分配 SQ 项并进入 LSU issue 路径)
  -> HybridUnit/DTLB(翻译 rs1 地址；识别 clean/flush/inval CBO)
  -> StoreQueue(等待 ROB 队首、排空 StoreBuffer、对齐物理块地址)
  -> MemBlock.cmoOpReq
  -> DCacheWrapper -> MissQueue.CMOUnit
  -> TileLink CacheBlockOperation / CBOAck
  -> cmoOpResp -> StoreQueue.mmioStout
  -> MemBlock.stOut -> ROB 标准 store writeback
  -> ROB Commit + flushPipe redirect -> LSQ/SQ retire
```

### Decode：译码为 store 类型的 `LSUOpType.cbo_flush`

[`backend/decode/DecodeUnit.scala:476`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476)
中的 `CBODecode` 是 CBO 指令的入口。对 `CBO_FLUSH`，它产生 `SrcType.reg`、
`SrcType.DC`、`FuType.stu`、`LSUOpType.cbo_flush` 和 `SelImm.IMM_S`：

```scala
CBO_FLUSH -> XSDecode(
  SrcType.reg, SrcType.DC, SrcType.X,
  FuType.stu, LSUOpType.cbo_flush, SelImm.IMM_S)
```

因此，`rs1` 是地址基址来源；该指令没有整数目的寄存器，第二源操作数为 `DC`，而不是
普通 store 的寄存器 store-data。最关键的是 `FuType.stu`：后续 Rename、Dispatch、ROB
都按 store 类 uop 处理它。内部 CBO 子操作编码由
[`package.scala:584`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala#L584)
定义；其中 `cbo_flush = b1101`，`isCboFlush` 以该编码识别指令。

译码阶段还负责是否允许执行的检查。
[`DecodeUnit.scala:882`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L882)
先用完整指令位模式生成 `isCboFlush`，随后
[`DecodeUnit.scala:913`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L913)
在 `illegalInst.cboCF` 为真或实现参数 `HasCMO` 关闭时，为 clean/flush 置非法指令异常：

```scala
(io.fromCSR.illegalInst.cboCF || !HasCMO.B) &&
  (isCboClean || isCboFlush)
```

相应的虚拟化限制由同一文件中 `virtualInst.cboCF` 的判断生成 virtual instruction
异常。也就是说，后续缓存路径只会看见已通过 CMO 使能/特权检查的 `cbo.flush`；若此处
失败，它会作为异常 uop 进入 ROB，而不会发出 CMO 请求。

### Rename、Dispatch 与 Issue：复用通用 store 资源

从 Decode 到 Rename/Dispatch 没有 `cbo_flush` 名称的专用旁路。CtrlBlock 通过
[`backend/CtrlBlock.scala:703`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L703)
的 `PipeGroupConnect(renameOut, dispatch.io.fromRename, ...)` 将 Rename 的动态 uop 送入
Dispatch。Rename 在
[`backend/rename/Rename.scala:176`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L176)
开始为待进入 ROB 的指令分配 `robIdx`，并在
[`Rename.scala:346`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L346)
写入每条 uop 的 `robIdx`。由于该 CBO 的 decode 控制没有写目的寄存器，它不需要普通
整数目的物理寄存器分配；但 `robIdx` 是之后 StoreQueue 请求、写回和提交关联同一条
指令的稳定身份。

Dispatch 仍然完全依据 `FuType.isStore` 分类。对 store，
[`backend/dispatch/NewDispatch.scala:676`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L676)
要求有可用 SQ 项；
[`NewDispatch.scala:688`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L688)
至 [`NewDispatch.scala:701`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L701)
为标量 store 设置 `needAlloc = 2.U`，并把 uop 送到 `lsqEnqCtrl`，再由
[`NewDispatch.scala:524`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L524)
输出至 MemBlock 的 `lsqEnqIO`。这说明 `cbo.flush` 与普通标量 store 一样占用 SQ
资源、受 SQ 空闲数反压；区别尚未发生在 Rename 或 Dispatch。

调度器选择 `FuType.stu` 对应的 LSU store 执行端口后，uop 进入 HybridUnit。这里同样
没有 CBO 专属 issue queue；CBO 与 store 共用地址生成、DTLB 和异常检查的前半段。

### 地址执行与 DTLB：识别 CBO，禁止其进入普通 DCache store pipe

[`mem/pipeline/HybridUnit.scala:577`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala#L577)
明确把 `cbo_clean`、`cbo_flush`、`cbo_inval` 三种管理类 CBO 识别为
`s1_mmio_cbo`：

```scala
val s1_mmio_cbo = (s1_in.uop.fuOpType === LSUOpType.cbo_clean ||
                   s1_in.uop.fuOpType === LSUOpType.cbo_flush ||
                   s1_in.uop.fuOpType === LSUOpType.cbo_inval) &&
                  !s1_ld_flow && !s1_prf
val s1_mmio = s1_mmio_cbo
```

这里的 `mmio` 是该 LSU 特殊/串行路径的复用命名，并不表示 `cbo.flush` 被作为普通 MMIO
写入。该阶段仍然使用 DTLB 响应产生物理地址；同文件
[`HybridUnit.scala:583`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala#L583)
保留原始虚拟地址的低 6 位并取得 `paddr`，而后由 StoreQueue 再对物理地址按 cache
block 向下对齐。

对普通 store，地址生成结果会送到 DCache store pipe；但
[`HybridUnit.scala:773`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala#L773)
把 `s1_mmio` 纳入 `io.stu_io.dcache.s1_kill`：

```scala
io.stu_io.dcache.s1_kill := s1_tlb_miss || s1_exception ||
  s1_mmio || s1_in.uop.robIdx.needFlush(io.redirect)
```

因此 `cbo.flush` 不会伪装成“携带某个数据和 mask 的普通 cache store”进入 MainPipe；其
翻译结果、SQ 项、异常状态仍会保留，等待 StoreQueue 的专用 CMO 流程。DTLB miss、访问
异常或更老 redirect 会在这一阶段 kill 掉该路径，阻止后续 CMO 请求。

### StoreQueue：以 ROB 队首为界，排空 StoreBuffer 后发起 CMO

StoreQueue 的
[`mem/lsqueue/StoreQueue.scala:820`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L820)
将 MMIO、uncached operation 与 CMO 放在同一套串行状态机中：`s_idle -> s_req ->
s_resp -> s_wb`。注释说明这类操作先由 store 执行端写回并标记 pending，只有到达 ROB
队首才发送请求，收到响应后才向 ROB 写回，最后再由 ROB 提交。

在 `s_idle`，
[`StoreQueue.scala:841`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L841)
要求 `io.rob.pendingst`、当前 SQ 项等于 `pendingPtr`、地址/数据已有效且没有异常；满足时
锁存 `uncacheUop`，进入 `s_req`。同段还执行：

```scala
cboFlushedSb := false.B
cboMmioPAddr := get_block_addr(paddrModule.io.rdata(0))
```

`get_block_addr` 是本实现确定 CBO 操作粒度的位置：无论 `rs1` 是否落在块首，发给
DCache 的都是所在物理 cache block 的地址。先等到 ROB 头部则保证较老指令已满足
提交顺序，避免 CBO 被更年轻的访问越过。

[`StoreQueue.scala:985`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L985)
的 `deqCanDoCbo` 进一步要求 SQ 项为已分配、地址有效、无异常的 `LSUOpType.isCbo(...)`
项，并要求 `memBackTypeMM`。当它为真时，代码禁用普通 uncache 请求，专门处理 CMO：

```scala
when (io.cmoOpReq.fire) {
  noPending := false.B
  mmioState := s_resp
}
when (mmioState === s_resp && io.cmoOpResp.fire) {
  noPending := true.B
  mmioState := s_wb
}
```

请求发出前还必须排空 StoreBuffer。
[`StoreQueue.scala:1033`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1033)
在 `s_req` 且 `cboFlushedSb` 尚未置位时拉高 `io.flushSbuffer.valid`；只有
`io.flushSbuffer.empty` 时，
[`StoreQueue.scala:1035`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1035)
才把 `cboFlushedSb := true.B`。之后
[`StoreQueue.scala:1025`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1025)
才允许 `cmoOpReq.valid`，并将操作码和对齐物理地址传出：

```scala
io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb &&
  (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode  := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr
```

这正是 `cbo.flush` 的第一项重要专用支持：它不会与仍滞留在 StoreBuffer 的更老 store
并发进行 cache-block operation。`cbo.zero` 没有走这一接口，而是通过全行 zero store
路径处理；`cbo.clean`、`cbo.flush`、`cbo.inval` 才共用此 CMO 通路。

### MemBlock 与 DCache：专用 CMO 接口和 CacheBlockOperation

MemBlock 本身只进行接口连接：
[`mem/MemBlock.scala:1210`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1210)
将 `lsq.io.cmoOpReq/cmoOpResp` 与 `dcache.io.cmoOpReq/cmoOpResp` 直接相连。DCache 的请求
bundle 在
[`cache/dcache/DCacheWrapper.scala:619`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L619)
定义，专门携带 3-bit `opcode` 与 64-bit `address`；注释编码为 `0-clean, 1-flush,
2-inval, 3-zero`。这与普通 MainPipe store 的地址、数据、字节掩码请求是两条不同的
接口。

[`DCacheWrapper.scala:1534`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L1534)
又将该接口直接接到 MissQueue 的 `cmo_req/cmo_resp`，因此 CMO 不经普通 store 的
MainPipe 命中/写数据阵列流程，而进入专用的 `CMOUnit`。

[`cache/dcache/mainpipe/MissQueue.scala:299`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L299)
定义了独立的 `CMOUnit`。它有四个状态：

```text
s_idle --req.fire--> s_sreq --req_chanA.fire--> s_wresp
       <--resp_to_lsq.fire-- s_lsq_resp <--resp_chanD.fire--
```

源码中 `s_idle` 时锁存 CMO 请求并清除 `nderr/denied/corrupt`；`s_sreq` 时通过
[`MissQueue.scala:352`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L352)
发出 TileLink A 通道请求：

```scala
io.req_chanA.bits := edge.CacheBlockOperation(
  fromSource = (cfg.nMissEntries + 1).U,
  toAddress = req.address,
  lgSize = (log2Up(cfg.blockBytes)).U,
  opcode = req.opcode
)._2
```

这是 cache 对 `cbo.flush` 的第二项、也是最核心的专用实现：DCache 使用 TileLink
`CacheBlockOperation`，并以 `cfg.blockBytes` 作为操作大小，而不是发射普通 store 或
load miss 请求。`opcode=1` 的含义由上游 `CMOReq` 保持到该 TileLink 操作。

MissQueue 在
[`MissQueue.scala:1230`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L1230)
把 `cmo_unit.io.req` 接到 `io.cmo_req`，且只在下级 D 通道 opcode 为 `TLMessages.CBOAck`
时把返回连接给 `CMOUnit`：

```scala
cmo_unit.io.req <> io.cmo_req
io.cmo_resp <> cmo_unit.io.resp_to_lsq
when (io.mem_grant.valid && io.mem_grant.bits.opcode === TLMessages.CBOAck) {
  cmo_unit.io.resp_chanD <> io.mem_grant
}
```

`s_wresp` 接收该 ack 后保存 `denied/corrupt`，转到 `s_lsq_resp`；最终由
`resp_to_lsq` 将地址和错误状态交还 StoreQueue。专用单元一次只能处于一个非 idle
CMO 状态，`io.req.ready := state === s_idle`，故 CMO 请求在 DCache 入口也天然串行化。

### CMO 响应、写回、Commit 与 Retire

StoreQueue 在 `cmoOpResp.fire` 后从 `s_resp` 转入 `s_wb`。此时
[`StoreQueue.scala:1055`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1055)
产生 `mmioStout` 作为 store 类完成写回。它带回先前锁存的 `uncacheUop`、SQ 索引和
异常信息；对 CBO 特别设置：

```scala
io.mmioStout.bits.uop.flushPipe := deqCanDoCbo
when (io.mmioStout.fire) {
  completed(deqPtr) := true.B
}
```

也就是说，DCache 返回 `CBOAck` 前，ROB 看不到该 store 已完成；只有该写回握手后 SQ
项才标记 `completed`。MemBlock 在
[`MemBlock.scala:1361`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1361)
把 `mmioStout`（以及与本指令无关的 `cboZeroStout`）复用到 `stOut(0)`。因此这个名称为
`mmioStout` 的端口实际也是 clean/flush/inval 的 ROB store-writeback 通道。

ROB 对 store 类指令的标准完成条件是收到匹配 `robIdx` 的 store writeback；
[`backend/rob/Rob.scala:1052`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L1052)
为需要标准 store 写回的新项把 `stdWritebacked` 置为假，
[`Rob.scala:1057`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L1057)
在 `canStdWbSeq` 命中相同 `robIdx` 时置为真。故 CMO 响应到达前，`cbo.flush` 即使已经
到达 ROB 队首，也不能作为完成的 store 提交。

当它已完成且位于队首，ROB 的提交逻辑在
[`Rob.scala:794`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L794)
生成 `io.commits.commitValid`；
[`Rob.scala:801`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L801)
要求该项的 `commit_v`、`commit_w` 均为真且没有更老阻塞项。由于 decode 时的 `FuType`
为 `stu`，提交信息被归类为 `CommitType.STORE`；ROB 再在
[`Rob.scala:839`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L839)
生成 store commit 向量，并在
[`Rob.scala:851`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L851)
把队首提交边界交回 LSQ/SQ，完成 retirement。

这里还有 CBO 独有的提交期控制效果。`flushPipe` 的定义位于
[`Bundle.scala:193`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/Bundle.scala#L193)，
注释说明它“在 commit 时像异常一样 flush 全部流水，但该指令本身可以 commit”。`cbo.flush`
在 StoreQueue 写回时已经将该位设为真。ROB 在
[`Rob.scala:622`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L622)
据此生成 `isFlushPipe`，并在
[`Rob.scala:635`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L635)
产生 `flushOut`。对于没有异常、没有 replay 的正常 CBO，
[`Rob.scala:641`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L641)
选择 `RedirectLevel.flushAfter`：目标 `cbo.flush` 自己保留并退休，所有年轻的取指、
Rename、Issue 和 LSU 工作被清除，随后从正确的顺序点继续执行。

综上，昆明湖对 `cbo.flush` 的特殊支持不在普通 DCache 数据阵列的某个“写一条 cache
line”分支，而是由四个互相配合的机制实现：**(1)** HybridUnit 将三种 clean/flush/inval
CBO 从普通 store pipe 中 kill 并保留翻译结果；**(2)** StoreQueue 在 ROB 队首等待和
StoreBuffer 排空后才发 CMO；**(3)** DCache MissQueue 的 `CMOUnit` 用
`CacheBlockOperation`/`CBOAck` 执行完整 cache block 操作；**(4)** CMO 完成后以
`flushPipe` 在 ROB 提交时冲刷流水线、禁止年轻指令跨越该缓存维护点。这也解释了为何
`cbo.flush` 虽然被 Decode 为 store 类型，却不等价于普通 store。

## CBO Flush 演示程序

演示程序位于 `~/cbo-kmhv2/nexus-am/apps/cbo_flush`。`Makefile` 将
`MARCH` 设置为包含 `zicbom` 的 ISA 字符串；程序把目标数组按 64 B 对齐，并使用
`volatile` 保证初始化、校验和 Flush 后的访问都实际生成内存指令。

```c
#include <klib.h>
#include <stdint.h>

#define CBO_BLOCK_BYTES 64
#define WORD_BYTES sizeof(uint64_t)
#define WORD_COUNT (CBO_BLOCK_BYTES / WORD_BYTES)

static volatile uint64_t demo_block[WORD_COUNT]
    __attribute__((aligned(CBO_BLOCK_BYTES)));

static inline void cbo_flush(void *address) {
  __asm__ volatile("cbo.flush 0(%0)" : : "r"(address) : "memory");
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
  const uint64_t first_seed = 0x1122334455667700ULL;
  const uint64_t second_seed = 0xa500000000000000ULL;
  uint64_t checksum_before_flush;
  uint64_t checksum_after_flush;

  fill_block(first_seed);
  checksum_before_flush = checksum_block();
  printf("CBO Flush demo: pre-accesses complete\n");

  printf("CBO Flush: execute\n");
  cbo_flush((void *)demo_block);

  checksum_after_flush = checksum_block();
  fill_block(second_seed);
  (void)checksum_block();
  printf("CBO Flush demo: post-accesses complete\n");

  return checksum_before_flush != checksum_after_flush;
}
```

使用下列命令构建：

```bash
cd ~/cbo-kmhv2
source env.sh
make -C nexus-am/apps/cbo_flush ARCH=riscv64-xs
```

对生成的 `cbo_flush-riscv64-xs.elf` 反汇编后，与目标指令直接相关的部分如下。`s0`
保存 `demo_block` 的地址；`cbo.flush` 之后立即开始的 `ld` 循环是 Flush 后的
`checksum_block()` 读取。

```text
8000017c:  00001517  auipc     a0,0x1
80000180:  1ec50513  addi      a0,a0,492 # printf 前阶段提示
80000184:  1ae010ef  jal       80001332 <printf_>
80000188:  00001517  auipc     a0,0x1
8000018c:  20850513  addi      a0,a0,520 # Flush 前提示
80000190:  1a2010ef  jal       80001332 <printf_>
80000194:  0024200f  cbo.flush (s0)
80000198:  4481      li        s1,0
8000019a:  4781      li        a5,0
8000019c:  46a1      li        a3,8
8000019e:  2087e733  sh3add    a4,a5,s0
800001a2:  6318      ld        a4,0(a4)
800001a4:  2785      addiw     a5,a5,1
800001a6:  94ba      add       s1,s1,a4
800001a8:  fed79be3  bne       a5,a3,8000019e
```

该程序刻意把三个阶段放在同一缓存块上：

1. **Flush 前访问：** `fill_block(first_seed)` 对 8 个双字进行 store，随后
   `checksum_block()` 对同一块逐字 load；这为缓存块制造真实的读写访问，并在输出
   `pre-accesses complete` 前完成校验。
2. **执行 Flush：** 内联汇编发射 `cbo.flush 0(%0)`；`memory` clobber 阻止编译器把
   前后的普通内存访问跨过这条指令重排。由于数组按 64 B 对齐，传入的地址正好是目标
   缓存块起始地址；即使软件传入块内地址，指令的语义也仍然作用于整个所在缓存块。
3. **Flush 后访问：** 紧随其后的 `checksum_block()` 再次 load 这 8 个双字。预期它们
   与 Flush 前校验和相同：Flush 应写回脏数据而不是丢弃数据。之后以第二个种子重新
   写入并再读取一次，确保 Flush 后还有明确的缓存访存活动。

因此，预期串口依次输出：

```text
CBO Flush demo: pre-accesses complete
CBO Flush: execute
CBO Flush demo: post-accesses complete
```

最后的返回值是 `checksum_before_flush != checksum_after_flush`。正常实现中二者相等，
程序返回 0；若 Flush 错误地丢弃了该脏块的数据，校验和不同，程序会以非零状态结束。
在本次昆明湖 V2 仿真中，程序最终得到 `HIT GOOD TRAP`，符合该预期。

## 波形图分析

### 分析对象、方法与判定规则

本节分析的波形为
`/home/yanyusong/cbo-kmhv2/XiangShan/build/cbo-flush-wave-analysis.fst`，目标动态指令是
反汇编中的 `0x80000194: 0024200f  cbo.flush (s0)`。程序中 `s0` 指向的测试块起始地址为
`0x800016c0`，并且该地址按 64 B cache line 对齐。波形中的 `clock` 在偶数 FST 时间点为高电平，
因此下文以 `t/2` 近似表示第几个核心周期，并在每次传输同时观察 `valid`、`ready` 和
`fire = valid && ready`；例如 `t=24432` 是约第 12216 个核心周期。

分析按照 `analyze-xiangshan-wavekit` 的指令追踪工作流进行：先用 PC、指令字定位 Decode，再在
Rename 之后固定使用 `robIdx=88`，并用 `sqIdx=5` 和物理地址 `0x800016c0` 交叉验证。当前
wavekit 的 `FstReader` 与本次 Verilator 5.048 所产生的 `VerilatedFst` 层级不兼容，不能直接建树；
因此信号层级、追踪项目和解释遵循 wavekit skill，实际 FST 值变化由同版本 Verilator FST API
提取。这样不会把工具兼容性问题误报为硬件行为。

一个重要的识别注意事项是：ROB 环形编号会复用，不能在整个长仿真中仅搜索数值 `88`。本节仅把下列
连续证据链中出现的 `88` 认作目标指令：`PC/instr -> Rename robIdx=88 -> Dispatch sqIdx=5 ->
StoreUnit_1 -> StoreQueue CMO(address=0x800016c0, opcode=1) -> ROB`。

| 波形时间 | 约核心周期 | 阶段/握手 | 目标指令的观测结果 |
| --- | ---: | --- | --- |
| `22308` | 11154 | Decode `io_out_0` | `valid=1, ready=1`，`pc=0x80000194`，`instr=0x0024200f`。 |
| `22310` | 11155 | Rename `io_out_0` | `valid=1, ready=1`，分配 `robIdx=88`，`fuOp=13`。 |
| `22312..22518` | 11156..11259 | Dispatch | `valid=1, ready=0`；在 `22518` 变为 `valid=ready=1`，随后发往 issue queue，携带 `robIdx=88, sqIdx=5, fuOp=13`。 |
| `22526` | 11263 | `StoreUnit_1.io_stin` | `valid=ready=1`，`robIdx=88`，源操作数地址为 `0x800016c0`，立即数为 0。 |
| `24068..24074` | 12034..12037 | `StoreUnit_1.io_stout` | 地址/异常处理后的 store-class 完成项有效；其中 `t=24072` uop 为 `robIdx=88, sqIdx=5`。 |
| `24310..24432` | 12155..12216 | StoreQueue 排空 StoreBuffer | 在 `s_req` 中先看到 `flushSbuffer.valid=1, empty=0`；`24430` 后 empty，`24432` 置 `cboFlushedSb=1` 并使 CMO 请求 fire。 |
| `24434` | 12217 | CMOUnit TileLink A | `state=s_sreq`，`io_req_chanA.valid=ready=1`，发出 CacheBlockOperation。 |
| `24584` | 12292 | CMOUnit TileLink D | `io_resp_chanD.valid=ready=1`，得到下级响应。 |
| `24586..24588` | 12293..12294 | CMO 返回/StoreQueue 写回 | `cmoOpResp.fire` 后进入 `s_wb`，`mmioStout.valid=ready=1`，写回 `robIdx=88, sqIdx=5, flushPipe=1`。 |
| `24598` | 12299 | ROB redirect | `flushOut.valid=1, robIdx=88, level=0`，触发该指令的提交时流水线清空。 |
| `24612` | 12306 | Commit/Retire | lane 0 的 `commitValid_0=1` 且 `isCommit=1`；`24614` 队首变为 `robIdx=89`，说明 `88` 已退休。 |

### Decode、Rename、Dispatch 与发射

Decode 的关键不是把这条指令当作普通 load/store，而是把它归入 `FuType.stu`（Store Unit）并赋予
`LSUOpType.cbo_flush`。源码的 decode 表如下，`t=22308` 的指令字恰好与该项相符；波形中显示的
`fuOp=13` 是本次 elaboration 后的编码，语义应以 Chisel 常量而非这个裸数值为准。

[`DecodeUnit.scala:476`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476)

```scala
val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
  CBO_ZERO  -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X, FuType.stu, LSUOpType.cbo_zero , SelImm.IMM_S),
  CBO_CLEAN -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X, FuType.stu, LSUOpType.cbo_clean, SelImm.IMM_S),
  CBO_FLUSH -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X, FuType.stu, LSUOpType.cbo_flush, SelImm.IMM_S),
  CBO_INVAL -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X, FuType.stu, LSUOpType.cbo_inval, SelImm.IMM_S)
)
```

* `Decode.io_out_0` 的 producer 是 DecodeBuffer/DecodeUnit，consumer 是 Rename；`22308` 的
  `valid && ready` 表明该 32-bit 指令确实穿过此边界，而非仅在前端重取队列中短暂出现。
* Rename 的 `22310` handshake 为它分配 `robIdx=88`。从这里开始 PC 只用于核对，ROB 编号才是后端
  身份；CBO 没有目的通用寄存器，后续完成项的 `rfWen=0` 也与此一致。
* Dispatch 在 `22312` 已持有该项却 `ready=0`，直到 `22518` 才 fire，停留约 103 个周期。这是实际
  波形可见的后端背压；本次没有提取到能唯一归因到某一个 issue queue/LSQ 门控的信号，故不能把它
  武断归因成“LSQ 满”或“ROB 满”。`22518` 的 `io_toIssueQueues_22` 同时带有 `robIdx=88`、`sqIdx=5`，
  证明它被作为 store-class uop 投递。
* `22526` 的 `StoreUnit_1.io_stin.valid && ready` 是发射到执行单元的 fire。`src_0=0x800016c0`
  与 `imm=0` 构成块地址；其 `sqIdx=5` 使后续 StoreQueue 项与 ROB 项可以双重关联。

### Store Unit、Load Unit 与 MemBlock 的特殊处理

目标在 `StoreUnit_1` 的常规地址翻译/异常路径中运行，随后在 `t=24068..24074` 出现 `io_stout.valid`；
`t=24072` 的输出 uop 是 `robIdx=88, sqIdx=5`。这一步把地址完成状态送入 LSQ/StoreQueue，使 CBO 能在
ROB 队首等待并执行；它**不是**向 DCache 的普通 store-data 写请求。

这一分流由 HybridUnit 的 `s1_mmio_cbo` 和 `s1_mmio` 完成。producer 是对输入 uop 的
`fuOpType` 比较，consumer 一方面是 Store Unit 的 DCache store pipe kill，另一方面是 LSQ 的 CMO
通道。因此，`cbo.flush` 仍利用 Store Unit 取得翻译后的地址、异常和 SQ 状态，但不会把该 uop 当成
一笔普通写入送进 DCache main pipe。

[`HybridUnit.scala:577`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala#L577)

```scala
val s1_mmio_cbo = (s1_in.uop.fuOpType === LSUOpType.cbo_clean ||
                   s1_in.uop.fuOpType === LSUOpType.cbo_flush ||
                   s1_in.uop.fuOpType === LSUOpType.cbo_inval) && !s1_ld_flow && !s1_prf
val s1_mmio = s1_mmio_cbo
...
io.stu_io.dcache.s1_kill := s1_tlb_miss || s1_exception || s1_mmio ||
  s1_in.uop.robIdx.needFlush(io.redirect)
```

因此 Load Unit 没有 CBO 专用执行阶段：该指令从 decode 起就是 `FuType.stu`，波形也直接给出了目标的
`StoreUnit_1.io_stin` 和 `io_stout` 链。三个 LoadUnit 在此时段仍可服务程序的其他 load；不能因 ROB
编号环绕复用而把仿真中其他时刻值为 88 的 LDU uop 误归给该 CBO。对目标来说，`lqIdx` bundle 字段不是
身份锚点，`sqIdx=5` 才是有效的 store-side 关联；没有观察到目标从任何 `LoadUnit.io_ldin/ldout` 经过。

MemBlock 把 StoreQueue 的 CMO ready/valid 对接到 DCache：`io.cmoOpReq` 的 producer 是 StoreQueue，
consumer 是 `DCacheWrapper`；反向的 `io.cmoOpResp` 则把 CacheBlockOperation 的最终状态送回
StoreQueue。此次 `t=24432` 的请求为 `valid=ready=1`、`opcode=1`、`address=0x800016c0`，所以该握手是
唯一的 CBO cache 请求，而不是普通 StoreUnit store pipe。

### StoreQueue：顺序、StoreBuffer 排空与完成写回

StoreQueue 把 CBO 纳入 MMIO/uncached 类的顺序化 FSM。`mmioState` 编码由 `Enum(5)` 的定义可知为
`s_idle=0, s_req=1, s_resp=2, s_wb=3, s_wait=4`。对目标项，`24310` 已在 `s_req`，但
`flushSbuffer.valid=1 && flushSbuffer.empty=0`；这说明 flush 先要求更早的 StoreBuffer 数据排空。
`24430` 出现 `empty=1`，`24432` 的 `cboFlushedSb=1` 允许 CMO 请求 fire，二者间约 60 个 FST tick
（约 30 核心周期）是能从波形精确归属给 StoreBuffer drain 的等待部分。

[`StoreQueue.scala:832`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L832)

```scala
val s_idle :: s_req :: s_resp :: s_wb :: s_wait :: Nil = Enum(5)
val mmioState = RegInit(s_idle)
val cboFlushedSb = RegInit(false.B)
...
val deqCanDoCbo = GatedRegNext(LSUOpType.isCbo(uop(deqPtr).fuOpType) &&
  allocated(deqPtr) && addrvalid(deqPtr) && !hasException(deqPtr)) && memBackTypeMM(deqPtr)
...
io.cmoOpReq.valid := deqCanDoCbo && cboFlushedSb && (mmioState === s_req) && !io.wfi.wfiReq
io.cmoOpReq.bits.opcode := cmoOpCode
io.cmoOpReq.bits.address := cboMmioPAddr
io.flushSbuffer.valid := deqCanDoCbo && !cboFlushedSb && (mmioState === s_req) &&
  !io.flushSbuffer.empty || cboZeroFlushSb
```

这里的 producer/consumer 关系解释了波形中四个关键控制信号：`deqCanDoCbo` 确认队首项是可执行 CBO，
`flushSbuffer.valid` 向 StoreBuffer 请求 drain，`cboFlushedSb` 是 drain 完成的锁存条件，最后
`cmoOpReq.valid` 将它们与 `s_req` 状态合取。于是 CBO 的 cache 操作既不会越过先前 store，也不会在
StoreBuffer 未空时开始。

收到 CMO 响应后，`t=24586` 的 `cmoOpResp.valid && ready` 使 FSM 从 `s_resp` 进入 `s_wb`；
`t=24588` 的 `mmioStout.valid && ready` 是送回 ROB/写回网络的最终完成。这一完成项保留
`robIdx=88, sqIdx=5`，`rfWen=0`、两项已导出的异常位为 0，并且 CMO 响应 `denied=0, corrupt=0`。所以
本条 CBO 没有 GPR 写回、没有 cache 响应错误，也没有通过该路径报告地址/访问异常。随后 FSM 到
`s_wait`，等待 ROB 真正提交这一顺序化项。

[`StoreQueue.scala:1012`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L1012)

```scala
when (io.cmoOpReq.fire) { mmioState := s_resp }
when (mmioState === s_resp) {
  when (io.cmoOpResp.fire) { mmioState := s_wb }
}
...
io.mmioStout.valid := mmioState === s_wb
io.mmioStout.bits.uop := uncacheUop
io.mmioStout.bits.uop.flushPipe := LSUOpType.isCbo(uncacheUop.fuOpType)
io.mmioStout.bits.uop.robIdx := uncacheUop.robIdx
```

### DCache/MissQueue 的 CMO FSM 与 cache 请求

在 DCache 一侧，CMO 并不复用普通 load/store 的 tag/data main pipe，而是由 MissQueue 内独立的
`CMOUnit` 形成单请求 FSM。`t=24434` 时它从 `s_idle` 进入 `s_sreq`，并且 TileLink A 通道
`valid && ready`；随后 `t=24436` 为 `s_wresp`，一直等到 `t=24584` 的 D 通道 `valid && ready`。
从 A fire 到 D fire 共 150 FST tick（约 75 核心周期），这是下级缓存/总线完成 cache block operation 的
外部等待，而不是 StoreQueue 背压。

[`MissQueue.scala:299`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L299)

```scala
val s_idle :: s_sreq :: s_wresp :: s_lsq_resp :: Nil = Enum(4)
val state = RegInit(s_idle)
...
io.req.ready := state === s_idle
io.resp.valid := state === s_lsq_resp
when (io.req.fire) { state := s_sreq }
when (io.req.fire) {
  io.req_chanA.valid := true.B
  io.req_chanA.bits := edge.CacheBlockOperation(io.req.bits.opcode, io.req.bits.address)
  state := s_wresp
}
when (io.resp_chanD.fire) { state := s_lsq_resp }
when (io.resp.fire) { state := s_idle }
```

波形状态与上述枚举逐一对应：`0=s_idle`、`1=s_sreq`、`2=s_wresp`、`3=s_lsq_resp`。A 通道的请求由
`edge.CacheBlockOperation(opcode=1, address=0x800016c0)` 构造；D 通道的 `CBOAck` 被转换为上行
`cmoOpResp`。因此这里观察到的是针对一条完整 cache line 的 CMO，而不是测试程序前后 `printf`/load/store
所产生的普通 cache 访问。

### ROB 的 flush、Commit 与 Retire

`mmioStout` 将 `flushPipe=1` 写回 ROB 后，`t=24598` 可见 `ROB.io_flushOut.valid=1` 和
`robIdx=88`。该 redirect 的 `level=0` 是设计生成的 `flushAfter` 级别编码；其实际含义由
`flushPipe` 控制：这条已经完成的 CBO 在提交时清空其后的流水线项，但自身允许提交。波形中
`flushOut` 只高一个采样周期，符合一次提交边界 redirect，而非异常或 load replay。

[`Bundle.scala:193`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/Bundle.scala#L193)

```scala
val flushPipe = Bool() // This inst will flush all the pipe when commit, like exception but can commit
```

[`Rob.scala:622`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala#L622)

```scala
val isFlushPipe = deqPtrEntry.commit_w && (deqHasFlushPipe || deqHasReplayInst)
io.flushOut.valid := RegNext(deqNeedFlush && !deqHasFlushed)
io.flushOut.bits.robIdx := Mux(needModifyFtqIdxOffset, firstVInstrRobIdx, deqPtr)
io.flushOut.bits.level := Mux(
  deqHasReplayInst || intrEnable || deqHasException || needModifyFtqIdxOffset,
  RedirectLevel.flush, RedirectLevel.flushAfter)
...
val isBlocked = intrEnable || (deqNeedFlush && !deqHasFlushed)
commitValidThisLine(i) := commit_vDeqGroup(i) && commit_wDeqGroup(i) &&
  !isBlocked && !isBlockedByOlder && !hasCommitted(i)
```

这里 `flushOut` 的 producer 是 ROB 队首的 `deqNeedFlush` 判断，consumer 是后端/前端 redirect 网络；
`commitValidThisLine` 则在 flush 已经发出后解除 `isBlocked`。对应波形中 `t=24600`
`commitValid_0=1` 但 `isCommit=0`，是提交候选已经形成而提交边界尚未打开；到 `t=24612`
`isCommit=1` 且 lane 0 的 `commitValid_0` 仍为 1，才是体系结构退休点。`t=24614` lane 0 的 ROB
编号转为 89，进一步验证 ROB 88 已被弹出。

该演示使用 `--no-diff`，所以没有把提交状态与参考模型做 diff 比较；不过波形本身已经给出完成项
`rfWen=0`、异常位为 0、`denied=0`、`corrupt=0`，并且目标项从 CMO 应答后经 flush/commit 正常退休。
结合程序在 Flush 前写入、Flush 后再次读取并最终 `HIT GOOD TRAP` 的结果，可确认本场景确实执行了
“先排空旧 store、再对 `0x800016c0` 所在 cache line 发出 CBO Flush、收到 CBOAck 后进行提交边界清空”的
完整实现路径。
