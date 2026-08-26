# 香山昆明湖执行 FENCE_I 指令的流程分析

## FENCE_I 指令介绍

### 这条指令是什么
TODO

### 这条指令会做什么
TODO

### 这条指令对程序执行有什么帮助
TODO

## 香山昆明湖源代码分析
TODO

## FENCE_I 演示程序
TODO

## 波形图分析

### 分析对象、方法和边界

本节分析的目标是演示程序中位于 PC `0x800001fe` 的 `fence.i`，其机器码为
`0x0000100f`。反汇编在此之前的 `0x800001e8` 将 `0x02a00513`
（`addi a0, zero, 42`）写入可执行数据区 `generated_code`，所以该 Fence 的作用对象明确是
“先前的数据写入、后续的指令取指”。

- 波形：`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-17-25-30.fst`。
- 时钟：`TOP.clock`；全部周期均为该时钟的正沿采样，`time = 2 * cycle + 1`。
- 工具：`/home/yanyusong/wavekit` 的 `FstReader`；先用 PC 和指令字建立锚点，再从 Rename 后用
  `robIdx=(flag=1,value=32)` 追踪。
- RTL 根目录：`/home/yanyusong/cbo-kmhv2/XiangShan/src/main`。

该 FST 没有以 `IBuffer` 名称导出可直接匹配的前端队列信号。因此，前端部分采用第一个已导出的
Decode `io_in` Decoupled 边界作为“前端/IBuffer 已送达后端”的可验证证据；不能从本波形捏造更早的
ICache response 或 IBuffer 内部 ready 信号。

### 全程时间线

| 周期（time） | 边界或状态 | `valid/ready/fire` 和关键值 | 结论 |
| --- | --- | --- | --- |
| 45916 (91833) | `decode.io_in_3` | `valid=1, ready=1, fire=1`；PC=`0x800001fe`，instr=`0x0000100f`，FTQ=`flag:1,value:42`，offset=`0` | 指令从前端进入 Decode lane 3。 |
| 45916 | `decodePipeRenameModule_3.io_in` | `valid=1,ready=1,fire=1`；译码得到 `fuType=512`、`fuOpType=18` | Decode 识别为 Fence 功能单元操作。 |
| 45917–45980 | Decode→Rename 流水寄存器输出 | `valid=1, ready=0, fire=0`，共 64 个周期 | Rename 侧反压，指令保持不变。 |
| 45981 | 同一输出 | `valid=1,ready=1,fire=1` | 指令进入 Rename/Dispatch 前端。 |
| 45982–46865 | `dispatch.io_fromRename_3` | `valid=1,ready=0,fire=0`，ROB ID=`1:32`，共 884 个周期 | Dispatch 的 lane 3 被 `waitForward` 阻塞，而非 ROB、IQ 或 LSQ 容量不足。 |
| 46866 | Dispatch→IssueQueue 6 | `fromRename_3.valid=ready=1`；`toIssueQueues_6.valid=ready=1`，`fire=1`；PC/ROB ID 不变 | 指令正式进入整数 IssueQueue 6。 |
| 46870 | IssueQueue 6→EXU 7/Fence | `Fence.io_in.valid=ready=1, fire=1`；ROB=`1:32`，`fuOpType=18` | 指令被选择到专用 Fence 功能单元。 |
| 46871–46888 | `Fence.state=s_wait(1)` | `flushSb=1`；`sbIsEmpty=0` | Fence 等待先前 StoreBuffer 和 uncache 写请求排空。 |
| 46889 | `Fence.state=s_icache(3)` | `fencei=1`；`Fence.io_out.valid=ready=1, fire=1`；ROB=`1:32` | 发起 ICache 全失效并把执行完成写回 ROB。 |
| 46890 | Frontend/ICache | `inner_icache_io_fencei_REG=1`、`metaArray.io_flushAll=1`、`missUnit.io_fencei=1` | ICache 元数据全失效，所有取指 MSHR 得到 fence 控制。 |
| 46898 | ICache 后续管线 | `mainPipe.io_flush=1`、`icache.io_flush=1`、`prefetcher.io_flush=1`、`wayLookup.io_flush=1` | 清除/恢复前端取指相关流水状态。 |
| 46899 (93799) | ROB difftest commit lane 0 | `valid=1`、PC=`0x800001fe`、ROB=`32`、`rfwen=fpwen=vecwen=0` | 该指令无寄存器写回、无异常地退休。 |

### 前端到 Decode：PC、指令和预测元数据

第 `45916` 周期，`TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.decode.io_in_3`
的 `valid=1`、`ready=1`，所以 `fire=1`。该接口给出的 `pc=0x800001fe`、
`instr=0x0000100f` 与反汇编完全一致；`ftqPtr.flag=1`、`ftqPtr.value=42`、`ftqOffset=0`
说明此指令来自 FTQ entry 42 的首条位置。由于是 `fence.i` 而不是控制流指令，目标指令自身不产生
分支预测修正。

Decode→Rename 的连线由 [`CtrlBlock.scala:576`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L576)
建立；`PipelineConnect` 负责 `valid/ready` 传播和重定向清除，随后同一 `DecodedInst` 同时送到 Rename
和 Dispatch 的 rename 输入：

```scala
private val decodePipeRename = Wire(Vec(RenameWidth, DecoupledIO(new DecodedInst)))
PipelineConnect(decode.io.out(i), decodePipeRename(i), rename.io.in(i).ready,
  s1_s3_redirect.valid || s2_s4_pendingRedirectValid,
  moduleName = Some("decodePipeRenameModule"))
decodePipeRename(i).ready := rename.io.in(i).ready
rename.io.in(i).valid := decodePipeRename(i).valid && !fusionDecoder.io.clear(i)
dispatch.io.renameIn(i).valid := decodePipeRename(i).valid && !fusionDecoder.io.clear(i) &&
  !decodePipeRename(i).bits.isMove
```

波形中的 `decodePipeRenameModule_3.io_in` 在同周期 fire，并给出 `fuType=512`、
`fuOpType=18`。随后 `io_out` 在第 `45917–45980` 周期持续 `valid=1,ready=0`，PC、指令字、
FTQ ID、`fuType` 和 `fuOpType` 全不变；第 `45981` 周期 ready 变为 1 并 fire。这个 64 周期的
停顿是标准 Decoupled 反压，而不是该指令被 flush 或重新取指。

### Rename、ROB 分配和 Dispatch 停顿

Rename 输出的可观察边界是 `dispatch.io_fromRename_3`。从第 `45982` 周期开始，该接口持续携带：

```text
PC=0x800001fe, ROB=(flag=1,value=32), fuType=512, fuOpType=18,
ldest=0, rfWen=0
```

这说明 Rename 已为该指令分配稳定的 ROB 身份，且它既没有整数目的寄存器也不需要物理寄存器写回；后续
所有阶段均以 `ROB 1:32` 而不是 PC 关联。`dispatch.io_fromRename_3` 在第 `45982–46865`
周期维持 `valid=1,ready=0`，故没有 fire；第 `46866` 周期变为 `valid=ready=1`。

该 884 周期的反压可以从 Dispatch 内部条件精确定位：第 `45982`、`46041` 和 `46865` 周期，
`allowDispatch_3=1`、`uopBlockByIQ_3=0`、`io_enqRob_canAccept=1`，但
`blockedByWaitForward_3=1`，因而 `thisCanActualOut_3=0`；第 `46866` 周期
`blockedByWaitForward_3` 变为 0，`thisCanActualOut_3=1`，于是 lane 3 立即 fire。也就是说，
波形排除了 ROB 满、IssueQueue 资源拒绝和 LSQ 容量拒绝，实际原因是程序顺序相关的 `waitForward`/
`blockBackward` 门控；该信号的更上游依赖在本 FST 中没有以可归因的单独名称导出，不能进一步臆测。

[`NewDispatch.scala:443`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L443)
给出了 `ready` 的合取条件，[`NewDispatch.scala:809`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L809)
定义了这里观测到的 `thisCanActualOut`：

```scala
fromRenameUpdate(i).valid := fromRename(i).valid && allowDispatch(i) && !uopBlockByIQ(i) &&
  thisCanActualOut(i) && lsqCanAccept && !fromRename(i).bits.eliminatedMove &&
  !fromRename(i).bits.hasException && !fromRenameUpdate(i).bits.singleStep
fromRename(i).ready := allowDispatch(i) && !uopBlockByIQ(i) && thisCanActualOut(i) && lsqCanAccept

thisCanActualOut := VecInit((0 until RenameWidth).map(i =>
  !blockedByWaitForward(i) && notBlockedByPrevious(i) && io.enqRob.canAccept))
```

### Dispatch 到 IssueQueue，再到 Fence EXU

第 `46866` 周期，`dispatch.io_toIssueQueues_6` 观察到：

```text
valid=1, ready=1, fire=1
PC=0x800001fe, ROB=(1,32), fuType=512, fuOpType=18,
psrc0=0, psrc1=0, ldest=0, rfWen=0
```

`psrc0/1=0`、`ldest=0` 与无寄存器读写的 Fence 语义一致。该 uop 在 IssueQueue 6 到 Fence EXU
输入之间经过了三个周期的调度；第 `46870` 周期
`backend.inner_intExuBlock.exus_7.Fence.io_in.valid=1` 且 `ready=1`，ROB ID 仍为 `1:32`，所以
这是可验证的 issue fire。该 FST 未导出 IssueQueue 6 内部 entry 的 valid/state/选择仲裁信号；能够
严格陈述的是 enqueue 在 `46866`，EXU 接收在 `46870`，中间不存在目标 ROB 的 flush。

### Fence 功能单元：StoreBuffer 排空、ICache 请求和写回

Fence 单元的状态机定义见 [`Fence.scala:47`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L47)。
状态编码按声明顺序为 `s_idle=0`、`s_wait=1`、`s_tlb=2`、`s_icache=3`、`s_fence=4`、
`s_nofence=5`。对应的波形如下：

| 周期范围 | `Fence.state` | `io_in.ready` | `flushSb` | `sbIsEmpty` | `fencei` | `io_out.valid/ready` | 含义 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 46870 | 0 (`s_idle`) | 1 | 0 | 0 | 0 | 0/1 | 接收目标 uop（fire）。 |
| 46871–46887 | 1 (`s_wait`) | 0 | 1 | 0 | 0 | 0/1 | 持续命令 MemBlock 排空旧写；尚未安全失效 ICache。 |
| 46888 | 1 (`s_wait`) | 0 | 1 | 1 | 0 | 0/1 | 观察到 StoreBuffer/uncache 均空，状态转移条件满足。 |
| 46889 | 3 (`s_icache`) | 0 | 0 | 1 | 1 | 1/1（fire） | 发出单周期 `fencei`，并以零结果写回同一 ROB。 |
| 46890 起 | 0 (`s_idle`) | 1 | 0 | 1 | 0 | 0/1 | 单元恢复可接收新 Fence。 |

[`Fence.scala:59`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/fu/Fence.scala#L59)
直接解释这三个控制输出及转移条件；`io.out.valid` 只在完成 TLB/ICache/Fence 动作的状态有效，结果数据为零：

```scala
sbuffer := state === s_wait
fencei  := state === s_icache
when (state === s_wait && func === FenceOpType.fencei && sbEmpty) { state := s_icache }
when (state =/= s_idle && state =/= s_wait) { state := s_idle }

io.in.ready := state === s_idle
io.out.valid := state =/= s_idle && state =/= s_wait
io.out.bits.res.data := 0.U
io.out.bits.ctrl.robIdx := uop.ctrl.robIdx
```

因此该指令的“执行延迟”不是 ALU 计算，而是第 `46871–46888` 周期等待存储系统完成顺序化；从 EXU
接收 (`46870`) 到写回 (`46889`) 共 19 周期，其中 18 个周期属于等待状态。

### Load Unit、Store Unit、LSQ、MemBlock 和 DCache 的特殊处理

#### LSQ 元数据接口：请求有效不等于分配队列项

第 `46867` 周期，Dispatch 的 `io_toMem_lsqEnqIO_req_3.valid=1`，而 uop 仍是
`ROB=1:32, fuType=512, fuOpType=18`。它的旁带 `lqIdx.flag=0,value=18`、
`sqIdx.flag=1,value=51` 看似带有 SQ 指针，但**并不表示 Fence 真的分配了 StoreQueue entry**：同周期
`io_toMem_lsqEnqIO_needAlloc_3=0`（所有 lane 的 `needAlloc` 也均为 0）。这是通用 `DynInst`
接口在每次 Dispatch fire 时传递 uop 和当前指针响应的表现。

[`NewDispatch.scala:688`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/dispatch/NewDispatch.scala#L688)
明确规定：只有 `FuType.isStore/isVStore` 时 `needAlloc=2`，只有 Load 时为 1，其他类型（含
`fuType=512` 的 Fence）为 0；但 `req.valid` 仍会在普通 fire 时置位：

```scala
when(!io.fromRename(i).fire) {
  enqLsqIO.needAlloc(i) := 0.U
}.elsewhen(isStoreVec(i) || isVStoreVec(i)) {
  enqLsqIO.needAlloc(i) := 2.U
}.elsewhen(isLoadVec(i) || isVLoadVec(i)) {
  enqLsqIO.needAlloc(i) := 1.U
}.otherwise {
  enqLsqIO.needAlloc(i) := 0.U
}
enqLsqIO.req(i).valid := io.fromRename(i).fire && !isAMOVec(i) && !isSegment(i) && !isfofFixVlUop(i)
enqLsqIO.req(i).bits := io.fromRename(i).bits
```

结论：目标 `fence.i` **不会进入 Load Unit、Store Unit、LoadQueue 或 StoreQueue 作为一条 load/store
操作**，不会发出 DCache load/store request，也没有 LQ/SQ 的地址、数据、mask、TLB、命中/未命中、
replay 或 memory-violation redirect 可归属于它。其对内存系统的特殊作用是以下的全局 StoreBuffer drain，
不是把 Fence 本身当成存储执行。

#### MemBlock/Sbuffer：等待真正的旧写完成

Fence 的 `flushSb` 经 [`XSCore.scala:228`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/XSCore.scala#L228)
送入 `MemBlock.io.ooo_to_mem.flushSb`，`MemBlock` 取 `sbuffer.io.flush.empty` 与
`uncache.io.flush.empty` 的与值，再打一拍送回 `sbIsEmpty`：

```scala
backend.io.fenceio.sbuffer.sbIsEmpty := memBlock.io.mem_to_ooo.sbIsEmpty
memBlock.io.ooo_to_mem.flushSb := backend.io.fenceio.sbuffer.flushSb

val fenceFlush = io.ooo_to_mem.flushSb
val stIsEmpty = sbuffer.io.flush.empty && uncache.io.flush.empty
io.mem_to_ooo.sbIsEmpty := RegNext(stIsEmpty)
sbuffer.io.flush.valid := RegNext(fenceFlush || atomicsFlush || cmoFlush)
uncache.io.flush.valid := sbuffer.io.flush.valid
```

上述连接分别位于 [`XSCore.scala:190`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/XSCore.scala#L190)
和 [`MemBlock.scala:1769`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala#L1769)。波形的逐拍结果是：

| 周期 | MemBlock/Sbuffer 信号 | 意义 |
| --- | --- | --- |
| 46871 | `io_ooo_to_mem_flushSb=1`，`io_mem_to_ooo_sbIsEmpty=0` | Fence 开始请求排空；旧写尚未全部完成。 |
| 46872 | `inner_sbuffer_io_flush_valid_REG=1`，`inner_sbuffer.io_flush_valid=1` | `RegNext(fenceFlush)` 使 drain 请求进入 Sbuffer/uncache。 |
| 46873–46885 | `sbuffer_state=2`，`flush_valid=1`，`flush_empty=0` | Sbuffer 正处于 flush 流程，仍有在途/待写项目。 |
| 46886 | `inner_sbuffer.empty=1`，但 `flush_empty=0` | 内部普通空条件先满足，flush 接口尚未确认完成。 |
| 46887 | `sbuffer.io_flush_empty=1`、`sbuffer.io_sbempty=1` | StoreBuffer 确认 drain 完成。 |
| 46888 | `mem_to_ooo_sbIsEmpty=1` | 经 MemBlock 寄存器返回 Fence，允许转入 `s_icache`。 |

这个链路是本场景最重要的 Store Unit/Cache 交互：前面的 `generated_code[0]` 写入及演示程序的其它
stores 先被顺序化到可见状态，之后才能使 ICache 中的旧指令副本失效。Load Unit 对本 Fence 没有专用
数据路径；它不会发出 load request，也不会参与重放。DCache 只通过 `sbuffer.io.dcache` 消费已经
排空的旧 store，Fence 本身未对 DCache MainPipe 发出 load/store 访问。

### ICache、MissUnit 和前端恢复

Backend 到 Frontend 的 `fencei` 由 [`XSCore.scala:139`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/XSCore.scala#L139)
直接连接。第 `46889` 周期 Fence 输出高；第 `46890` 周期前端的
`inner_icache_io_fencei_REG=1`，并且下列信号同为 1：

```text
frontend.inner_icache.io_fencei
frontend.inner_icache.metaArray.io_flushAll
frontend.inner_icache.missUnit.io_fencei
frontend.inner_icache.io_fencei_probe
```

ICache 把输入 `io.fencei` 同时扇出给 MetaArray 和 MissUnit，源码见
[`ICache.scala:629`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala#L629)：

```scala
metaArray.io.flushAll := io.fencei
metaArray.io.flush <> mainPipe.io.metaArrayFlush
missUnit.io.fencei := io.fencei
missUnit.io.flush  := io.flush
```

MetaArray 的全失效逻辑在 [`ICache.scala:379`](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala#L379)：

```scala
// flush all (e.g. fence.i)
when(io.flushAll) {
  (0 until nWays).foreach(w => valid_array(w) := 0.U)
}
```

因此 `46890` 的 `flushAll=1` 不是只清某一个 set，而是把所有 way 的有效位清零；下一次从
`generated_code` 取指时必须重新获取当前内存中的 `0x02a00513`。第 `46898` 周期的
`mainPipe.io_flush=1`、`icache.io_flush=1`、`prefetcher.io_flush=1` 与 `wayLookup.io_flush=1`
是这一全失效后的前端管线恢复/取消活动预取的控制，而非本 Fence 的 redirect。

### 写回、提交、异常与重定向检查

在第 `46889` 周期，`exus_7.io_out.valid=1`、`io_out.ready=1`，因此写回 fire；
`io_out.bits.robIdx=(1,32)`，`res.data=0`，`redirect.valid=0`。随后 ROB 的 difftest commit lane 0
在第 `46899` 周期报告：

```text
valid=1, pc=0x800001fe, robIdx=32, skip=0,
rfwen=0, fpwen=0, vecwen=0, v0wen=0, wpdest=0, wdest=0, sqIdx=0
```

在 `46865–46900` 的覆盖窗口，`CtrlBlock.io_redirect_valid=0`、`ROB.io_redirect_valid=0`、
`ROB.io_flushOut_valid=0`、`ROB.io_exception_valid=0`，且 Fence 写回侧 `redirect.valid=0`。所以目标
指令没有触发分支/异常/内存违例重定向，也没有杀死年轻指令；`fence.i` 的可观察效果是 StoreBuffer
顺序化和 ICache/前端失效，而不是控制流跳转。

### 小结：该指令对存储和取指的精确作用

1. 此 `fence.i` 先因为 `waitForward` 在 Dispatch 停顿 884 周期，后在 Fence EXU 接收。
2. 它不是 Load/Store 指令：LSQ 接口的 `req.valid` 是通用随行消息，但 `needAlloc=0`，没有 LQ/SQ
   entry、LoadUnit/StoreUnit 执行、DCache 数据请求或 replay。
3. 它在 `s_wait` 用 `flushSb` 连续请求 MemBlock 排空真正的旧 stores；波形证明 `sbIsEmpty` 从 0
   变为 1 后才进入 `s_icache`。
4. 它在 `s_icache` 只拉高一周期 `fencei`，使 ICache MetaArray 的全体有效位清零，并通知 MissUnit；
   之后前端流水的 `flush` 传播。
5. 它无 GPR/FPR/CSR 写回、无异常、无 redirect，最终以 ROB 32 正常提交。演示程序随后再次从
   `generated_code` 取指并返回 42，验证了该控制路径确实让后续取指观察到改写后的指令。
