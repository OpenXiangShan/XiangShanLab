# 香山昆明湖 V3 MDP / LoadQueueRAW 波形分析

## 方法与结论摘要

本分析使用 wavekit 开源仓库中的 `FstReader` 解析 `2026-07-21-10-21-43.fst`，按 `TOP.clock` 上升沿采样。波形中的核心层级为：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core
```

采样结果与反汇编一致：

| 指令 | PC | instr | foldpc | ftqOffset | 说明 |
|---|---:|---:|---:|---:|---|
| `sd t3,0(t0)` | `0x800001be` | `0x01c2b023` | 222 | 16 | 每次循环把递增的 `t3` 写到 `x` |
| `ld t4,0(a5)` | `0x800001c2` | `0x0007be83` | 224 | 18 | 同地址读取 `x` |

结论：

1. 第一次循环时 SSIT 是冷状态，store/load 在 rename/dispatch 均为 `storeSetHit=0`，load 没有 `loadWaitBit`，所以可以在 store 地址算出前执行并写回旧值 `0x0`。
2. 当 older store ROB163 的地址后来进入 StoreQueue/LoadQueueRAW 时，LoadQueueRAW 用 store 物理地址 `0x80001640` 和 mask `0xff` 命中了 already-data-valid 的 younger load ROB164，cycle 4396/time 8792 产生 memory violation、rollback 和 `mdpTrain`。
3. CtrlBlock 在 cycle 4398/time 8796 把该 violation 转成 backend redirect：`pc=target=0x800001c2`、`rob=164`、`debugIsMemVio=1`，前端从这条 load 重新取指。
4. `mdpTrain` 在 cycle 4397 到 4400 训练 SSIT：load foldpc 224 和 store foldpc 222 被写成同一个 store-set id `ssid=29`。
5. 后续循环并不是“load 绝对不会进入 LoadUnit”。更准确地说，load 会带着 `loadWaitBit=1` 和 `waitForRobIdx=<预测 store ROB>` 进入 LoadUnit/StoreQueue forwarding；当预测的 store 地址还没有 ready 时，StoreQueue 返回 `addrInvalid=1`，load 不产生有效 `ldout`，等待 replay。store 地址/数据 ready 后，load 再写回正确的 store-forward 数据。因此避免的是“load 在被预测依赖的 store 之前错误完成/写回”，不是简单地禁止 load 提前做地址生成。

## 全局时间线

`time = cycle * 2`，以下 cycle 均为 `TOP.clock` 上升沿采样。

| cycle | time | 事件 | 波形证据 |
|---:|---:|---|---|
| 4328 | 8656 | 首次循环 decode | lane5 `pc=0x800001be` fire=1，lane6 `pc=0x800001c2` fire=1 |
| 4329 | 8658 | 首次 rename | store ROB163 `storeSetHit=0 ssid=29`；load ROB164 `storeSetHit=0 ssid=20`，ssid 因 hit=0 无效 |
| 4330 | 8660 | 首次 dispatch | store ROB163、load ROB164 同拍 fire；load `loadWaitBit=0` |
| 4334 | 8668 | 首次 load 地址生成 | LU0 `rob=164 paddr=0x80001640 loadWaitBit=0 waitRob=184 hit=0` |
| 4336 | 8672 | LoadQueueRAW 首次看到 load | RAW query `rob=164 paddr=0x80001640 mask=0xff sq=4 dataValid=0` |
| 4388 | 8776 | load 再次 query，数据已有效 | RAW query `rob=164 paddr=0x80001640 mask=0xff dataValid=1`，这是后续 rollback 能命中的 entry |
| 4389 | 8778 | 错误旧值写回 | LU0 `ldout rob=164 data=0x0` |
| 4392 | 8784 | older store 地址执行 | SU0 `rob=163 paddr=0x80001640 storeSetHit=0 sq=3` |
| 4393 | 8786 | store 地址进入 RAW | StoreUnit `toSqAddr rob=163 paddr=0x80001640 mask=0xff`；RAW `storeIn0 rob=163` |
| 4396 | 8792 | RAW 检出违例并训练 | `io_mem_memoryViolation_valid=1 rob=164 ftq=17 off=18 stOff=16`；`io_mem_mdpTrain_valid=1` |
| 4397 | 8794 | CtrlBlock 生成 MDP update | `memPredUpdate.valid=1 ldpc=224 stpc=222` |
| 4398 | 8796 | backend redirect 到 load PC | `io_redirect_valid=1 pc=target=0x800001c2 rob=164 debugIsMemVio=1` |
| 4400 | 8800 | SSIT 写入 store set | `ssit.s2.valid=1 ldpc=224 stpc=222 allocSsid=29 loadAssigned=0 storeAssigned=0` |
| 4418-4421 | 8836-8842 | 首次 load replay 后正确写回 | LU0 `rob=164 hit=1 ssid=29`，随后 `ldout data=0x1` |
| 4423 | 8846 | 下一次循环 dispatch 命中 StoreSet | store ROB234/load ROB235 均 `storeSetHit=1 ssid=29`；LFST 对 load 返回 `shouldWait=1 waitRob=234` |
| 4427 | 8854 | load 带 MDP 等待信息进入 LU | LU0 `rob=235 loadWaitBit=1 waitRob=234 hit=1 ssid=29` |
| 4429 | 8858 | load 被精确 MDP 等待阻止写回 | StoreQueue forward response `addrInvalid=1 addrPtr=4`，没有 `ldout` |
| 4484 | 8968 | 预测 store 地址 ready | SU0 `toSqAddr rob=234 paddr=0x80001640 mask=0xff`，LFST storeIssue `hit=1 ssid=29` |
| 4488 | 8976 | load 等 store ready 后正确写回 | LU1 `ldout rob=235 data=0x2` |

## 第一次不知道依赖时发生了什么

首次循环的 store/load 同在一个 fetch block 中：`sd` 的 `ftqOffset=16`，`ld` 的 `ftqOffset=18`。decode、rename、dispatch 都同拍或相邻拍完成，说明前端和后端边界没有把这对访存指令强制串行化。

关键冷启动事实是：

```text
cycle 4329:
  store ROB163: storeSetHit=0, ssid=29
  load  ROB164: storeSetHit=0, ssid=20

cycle 4330:
  dispatch store/load fire=1
  loadWaitBit=0
```

因此 load 没有被 StoreSet 预测为需要等待 ROB163。load 在 cycle 4334 进入 LoadUnit，地址为 `0x80001640`，但 store ROB163 因为 `t0` 上有长的 `addi +1/-1` 依赖链，直到 cycle 4392 才进入 StoreUnit 地址执行。

在 store 地址 ready 前，load ROB164 已经在 cycle 4389 产生 `ldout data=0x0`。对这个程序而言，第一次循环正确值应该是 store 写入的 `1`；`0` 是 `x` 的初始旧值，所以这是典型的 younger load 在 older same-address store 前错误完成。

LoadQueueRAW 的作用是在 store 地址晚到时补救。ROB164 在 cycle 4336 已被 RAW query 看到，但当时 `dataValid=0`；cycle 4388 的同 ROB 再次 query 已经 `dataValid=1`，这才满足后面 `detectRollback` 中 `datavalid(j)` 的条件。cycle 4393，ROB163 的 store 地址和 mask 到达：

```text
SU0.toSqAddr: rob=163 paddr=0x80001640 mask=0xff
RAW.storeIn0: rob=163 paddr=0x80001640 mask=0xff ftqOffset=16
```

LoadQueueRAW 随后在 cycle 4396 输出：

```text
io_mem_memoryViolation_valid = 1
io_mem_memoryViolation_bits_robIdx = 164
io_mem_memoryViolation_bits_ftqIdx = 17
io_mem_memoryViolation_bits_ftqOffset = 18
io_mem_memoryViolation_bits_stFtqIdx = 17
io_mem_memoryViolation_bits_stFtqOffset = 16
io_mem_mdpTrain_valid = 1
```

这说明 RAW 检测选中的 rollback 目标是 younger load ROB164，而 store 侧元数据正是同一个 FTQ entry 内 offset 16 的 `sd`。

## Redirect 与 replay 路径

cycle 4396 是 MemBlock/LoadQueueRAW 给 backend 的 violation 和 `mdpTrain`，还不是前端 redirect。CtrlBlock 里 `loadReplay` 对 `memViolation` 做了寄存，且 pcMem 读 FTQ 的起始 PC，再加 offset 得到真实 redirect PC。

波形显示 redirect 在 cycle 4398 出现在 CtrlBlock/backend/frontend 边界：

```text
cycle 4398 time 8796:
  CtrlBlock.io_redirect_valid = 1
  io_redirect_bits_pc         = 0x800001c2
  io_redirect_bits_target     = 0x800001c2
  io_redirect_bits_robIdx     = 164
  io_redirect_bits_ftqIdx     = 17
  io_redirect_bits_ftqOffset  = 18
  io_redirect_bits_debugIsMemVio = 1
  io_redirect_bits_debugIsCtrl   = 0

  CtrlBlock.io_frontend_toFtq_redirect_valid = 1
  Frontend.io_backend_toFtq_redirect_valid   = 1
  Frontend.io_backend_toFtq_redirect_bits_pc = 0x800001c2
  Frontend.io_backend_toFtq_redirect_bits_target = 0x800001c2
```

cycle 4399，FTQ 内部 `redirectReg_valid=1`、`io_toIfu_redirect_valid=1`，并且 `io_backendRedirectTopdown_memoryViolationRedirect=1`。这证明第一次错误 load 触发的是 memory-violation redirect，而不是 branch/control redirect。

redirect 后，同一 load PC 被重新执行。波形里 ROB164 的 replay 版本在 cycle 4418-4421 再次经过 LoadUnit，`storeSetHit=1 ssid=29`，最终 `ldout data=0x1`。旧值 `0x0` 因 redirect/replay 被恢复路径覆盖，后续同一程序语义继续使用正确的第一次循环 load 值。

## MDP 训练过程：SSIT/LFST 如何建立关系

`mdpTrain` 的 load/store FTQ 元数据在 CtrlBlock 中还原为 MDP 使用的折叠 PC。波形如下：

| cycle | time | 信号 | 值 |
|---:|---:|---|---|
| 4396 | 8792 | `io_mem_mdpTrain_valid` | 1 |
| 4397 | 8794 | `memCtrl.io_memPredUpdate_valid` | 1 |
| 4397 | 8794 | `memCtrl.io_memPredUpdate_ldpc` | 224 |
| 4397 | 8794 | `memCtrl.io_memPredUpdate_stpc` | 222 |
| 4398 | 8796 | `memCtrl.ssit_io_update_REG_valid` | 1 |
| 4399 | 8798 | `ssit.s1_mempred_update_req_valid` | 1, `ldpc=224 stpc=222` |
| 4400 | 8800 | `ssit.s2_mempred_update_req_valid` | 1, `ldpc=224 stpc=222` |
| 4400 | 8800 | `ssit.s2_loadAssigned/s2_storeAssigned` | 0 / 0 |
| 4400 | 8800 | `ssit.s2_allocSsid` | 29 |

这正好对应首次碰到这对 load/store 时 SSIT 两边都未分配的情况。SSIT 在 s2 阶段把 load foldpc 224 和 store foldpc 222 都写成 `valid=1, ssid=29, strict=0`。

训练生效后，后续 rename 看到：

```text
store pc 0x800001be: storeSetHit=1 ssid=29
load  pc 0x800001c2: storeSetHit=1 ssid=29
```

接着 LFST 在 dispatch 阶段把“当前最近的同 store-set store”传给 younger load。

## 后续循环如何避免再次错误完成

以第二个完整的 store/load 对为例：

```text
cycle 4423 time 8846:
  dispatch lane1 store pc=0x800001be rob=234 storeSetHit=1 ssid=29
    LFST req: isstore=1, ssid=29, rob=234

  dispatch lane2 load pc=0x800001c2 rob=235 storeSetHit=1 ssid=29
    LFST resp: shouldWait=1, waitRob=234
```

这里有一个容易误读的波形细节：`dispatch.io_fromRename_*_bits_loadWaitBit` 仍然可能显示为 0，因为那是 rename/waittable 传来的原始字段；dispatch 之后的 `fromRenameUpdate` 才用 LFST response 覆盖它。最终进入 LoadUnit 的 uop 能证明覆盖已经生效：

```text
cycle 4427 time 8854:
  LU0.ldin rob=235
  loadWaitBit=1
  waitForRobIdx=234
  storeSetHit=1
  ssid=29
  paddr=0x80001640
```

load ROB235 仍然可以进入 LoadUnit 做地址生成和 StoreQueue forward 查询，但 StoreQueue 识别出它在等的 store ROB234 地址还未 ready：

```text
cycle 4429 time 8858:
  StoreQueue forward s2Resp:
    addrInvalid.valid = 1
    addrInvalid.bits  = 4
    dataInvalid.valid = 0
    forwardInvalid    = 0
    matchInvalid      = 0
```

同一拍没有 ROB235 的有效 `ldout`。这就是后续循环避免错误的核心：MDP 不是把 load 固定停在 rename/dispatch 前，而是把 precise wait 信息送到 StoreQueue forward 逻辑；如果预测 store 的地址还未 ready，load 被 replay/等待，而不是用 cache 旧值完成。

等 ROB234 的 store 地址进入 StoreQueue：

```text
cycle 4484 time 8968:
  SU0.toSqAddr rob=234 paddr=0x80001640 mask=0xff
  SU0.updateLFST rob=234 storeSetHit=1 ssid=29
  RAW.storeIn0 rob=234 paddr=0x80001640 mask=0xff
```

随后 load replay：

```text
cycle 4487 time 8974:
  StoreQueue forward s2Resp:
    addrInvalid.valid = 0
    dataInvalid.valid = 0

cycle 4488 time 8976:
  LU1.ldout rob=235 data=0x2
```

后面多轮循环重复同样模式：

| load ROB | waitForRobIdx | 初次等待现象 | store ready 后写回 |
|---:|---:|---|---|
| 235 | 234 | `addrInvalid=1` | cycle 4488 `data=0x2` |
| 306 | 305 | `addrInvalid=1` | cycle 4503 `data=0x3` |
| 25 | 24 | `addrInvalid=1` | cycle 4528 `data=0x4` |
| 96 | 95 | `addrInvalid=1` | cycle 4556 `data=0x5` |
| 167 | 166 | `addrInvalid=1` | cycle 4579 `data=0x6` |
| 238 | 237 | `addrInvalid=1` | cycle 4607 `data=0x7` |

这些值与程序的 `t3=1..10` 递增写入一致；首次 replay 后读到 `1`，之后依次读到 `2,3,4,...`。

## 信号来源与去向

| 波形信号 | 生产者 | 消费者 | 本次值/作用 |
|---|---|---|---|
| `io_mem_memoryViolation_valid` | MemBlock/LoadQueueRAW rollback | Backend/CtrlBlock | cycle 4396 为 1，携带 load ROB164 与 store FTQ offset |
| `io_mem_mdpTrain_valid` | LoadQueueRAW `io.mdpTrain` | CtrlBlock/MemCtrl/SSIT | cycle 4396 为 1，用来训练 load/store foldpc |
| `memCtrl.io_memPredUpdate_ldpc/stpc` | CtrlBlock 读 pcMem 后 XORFold | MemCtrl/SSIT | cycle 4397 为 `224/222` |
| `ssit.s2_allocSsid` | SSIT update s2 | SSIT data array/debug array | cycle 4400 为 29 |
| `storeSetHit/ssid` | SSIT rename read data | Rename uop -> Dispatch -> LoadUnit/StoreUnit | 后续 store/load 均命中 `ssid=29` |
| `LFST.resp.shouldWait/robIdx` | LFST dispatch read/update | Dispatch `fromRenameUpdate` | load ROB235 得到 `shouldWait=1 waitRob=234` |
| `loadWaitBit/waitForRobIdx` | Dispatch 覆盖后的 uop 字段 | LoadUnit -> StoreQueue forward MDP query | ROB235 进入 LU 时为 `1/234` |
| `StoreQueue forward addrInvalid` | VirtualStoreQueue + physical StoreQueue forward | LoadUnit replay/不写回 | store 地址未 ready 时阻止 load 完成 |

## 源码依据与解释

[StoreSet.scala:40](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala:40) 定义 SSIT entry：

```scala
class SSITEntry(implicit p: Parameters) extends XSBundle {
  val valid = Bool()
  val ssid = UInt(SSIDWidth.W) // store set identifier
  val strict = Bool() // strict load wait is needed
}
```

[StoreSet.scala:125](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala:125) 显示 SSIT 在 decode 读、rename 出结果：

```scala
valid_array.io.ren.get(i) := io.ren(i)
data_array.io.ren.get(i) := io.ren(i)
valid_array.io.raddr(i) := io.raddr(i)
data_array.io.raddr(i) := io.raddr(i)

io.rdata(i).valid := valid_array.io.rdata(i)
io.rdata(i).ssid := data_array.io.rdata(i).ssid
io.rdata(i).strict := data_array.io.rdata(i).strict
```

[CtrlBlock.scala:208](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:208) 解释 memory violation 到 replay/MDP update 的路径：

```scala
private val memViolation = io.fromMem.violation
val loadReplay = Wire(ValidIO(new Redirect))
loadReplay.valid := GatedValidRegNext(memViolation.valid)
loadReplay.bits := RegEnable(memViolation.bits, memViolation.valid)
loadReplay.bits.debugIsCtrl := false.B
loadReplay.bits.debugIsMemVio := true.B
...
memCtrl.io.memPredUpdate.ldpc := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
...
memCtrl.io.memPredUpdate.stpc := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
memCtrl.io.memPredUpdate.valid := RegNext(mdpTrainValid)
```

[CtrlBlock.scala:325](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:325) 解释 cycle 4398 的 redirect 为什么是 load PC：

```scala
redirectGen.io.loadReplay <> loadReplay
...
redirectGen.io.loadReplay.bits.target := load_target
...
redirectGen.io.loadReplay.bits.pc := loadRedirectStartPcRead + loadRedirectPcOffset
...
io.frontend.toFtq.redirect.valid := s5_flushFromRobValid || s3_redirectGen.valid
io.frontend.toFtq.redirect.bits := Mux(s5_flushFromRobValid, frontendFlushBits, s3_redirectGen.bits)
```

[StoreSet.scala:170](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala:170) 到 [StoreSet.scala:263](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala:263) 解释 SSIT update 两级流水和首次分配：

```scala
val s1_mempred_update_req_valid = RegNext(io.update.valid)
...
val s2_mempred_update_req_valid = RegNext(s1_mempred_update_req_valid)
...
val s2_allocSsid = Mux(s2_ldSsidAllocate < s2_stSsidAllocate, s2_ldSsidAllocate, s2_stSsidAllocate)

when(s2_mempred_update_req_valid){
  switch (Cat(s2_loadAssigned, s2_storeAssigned)) {
    is ("b00".U(2.W)) {
      update_ld_ssit_entry(pc = s2_mempred_update_req.ldpc, valid = true.B, ssid = s2_allocSsid, strict = false.B)
      update_st_ssit_entry(pc = s2_mempred_update_req.stpc, valid = true.B, ssid = s2_allocSsid, strict = false.B)
    }
```

[MemCtrl.scala:14](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/MemCtrl.scala:14) 说明 SSIT/LFST 的连接关系：

```scala
private val ssit = Module(new SSIT)
private val lfst = Module(new LFST)
ssit.io.update <> RegNext(io.memPredUpdate)
...
lfst.io.storeIssue <> RegNext(io.stIn)
lfst.io.dispatch <> io.dispatchLFSTio
io.ssit2Rename := ssit.io.rdata
```

[Rename.scala:453](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala:453) 说明 rename 如何把 SSIT 结果写入 uop：

```scala
uops(i).storeSetHit := io.ssit(i).valid
uops(i).loadWaitStrict := io.ssit(i).strict && io.ssit(i).valid
uops(i).ssid := io.ssit(i).ssid
uops(i).loadWaitBit := io.waittable(i)
```

[Dispatch.scala:759](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala:759) 说明 dispatch 如何用 LFST 覆盖 load 等待信息：

```scala
io.lfst.req(i).valid := fromRename(i).fire && updatedUop(i).storeSetHit
io.lfst.req(i).bits.isstore := isStore(i)
io.lfst.req(i).bits.ssid := updatedUop(i).ssid
io.lfst.req(i).bits.robIdx := updatedUop(i).robIdx

fromRenameUpdate(i).bits.loadWaitBit := io.lfst.resp(i).bits.shouldWait
fromRenameUpdate(i).bits.waitForRobIdx := io.lfst.resp(i).bits.robIdx
```

[StoreSet.scala:383](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala:383) 说明 LFST 可以命中同一 dispatch bundle 内较老 store：

```scala
val hitInDispatchBundleVec = if(i > 0){
  WireInit(VecInit((0 until i).map(j =>
    io.dispatch.req(j).valid &&
    io.dispatch.req(j).bits.isstore &&
    io.dispatch.req(j).bits.ssid === io.dispatch.req(i).bits.ssid
  )))
}
...
io.dispatch.resp(i).bits.shouldWait := (
  (valid(io.dispatch.req(i).bits.ssid) || hitInDispatchBundle) &&
  io.dispatch.req(i).valid &&
  (!io.dispatch.req(i).bits.isstore || io.csrCtrl.storeset_wait_store)
) && !io.csrCtrl.lvpred_disable || io.csrCtrl.no_spec_load
...
when(hitInDispatchBundleVec(j)){
  io.dispatch.resp(i).bits.robIdx := io.dispatch.req(j).bits.robIdx
}
```

[LoadQueueRAW.scala:128](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala:128) 说明 RAW 为什么会记录首次 ROB164：

```scala
val hasAddrInvalidStore = io.query.map(_.req.bits.sqIdx).map(sqIdx => {
  io.stAddrReadySqPtr.isBefore(sqIdx)
})
val needEnqueue = canEnqueue.zip(hasAddrInvalidStore).zip(cancelEnqueue).map {
  case ((v, r), c) => v && r && !c
}
...
uop(enqIndex).robIdx := enq.bits.robIdx
uop(enqIndex).sqIdx := enq.bits.sqIdx
uop(enqIndex).ftqPtr := enq.bits.ftqPtr
uop(enqIndex).ftqOffset := enq.bits.ftqOffset
uop(enqIndex).pc := enq.bits.pc
datavalid(enqIndex) := enq.bits.dataValid
```

[LoadQueueRAW.scala:234](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala:234) 到 [LoadQueueRAW.scala:396](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala:396) 说明 store 地址晚到时如何找 younger same-address load，并产生 rollback/mdpTrain：

```scala
// When store writes back, it searches LoadQueue for younger load instructions
// with the same load physical address. They loaded wrong data and need re-execution.
...
allocated(j) && storeIn(i).valid &&
  isAfter(uop(j).robIdx, storeIn(i).bits.uop.robIdx) &&
  datavalid(j) && !uop(j).robIdx.needFlush(io.redirect) && !willRevoke(j)
...
redirect.valid := rollbackLqWb(i).valid
redirect.bits.robIdx := rollbackLqWb(i).bits.robIdx
redirect.bits.ftqIdx := rollbackLqWb(i).bits.ftqPtr
redirect.bits.ftqOffset := rollbackLqWb(i).bits.ftqOffset
redirect.bits.stFtqIdx := stFtqIdx(i)
redirect.bits.stFtqOffset := stFtqOffset(i)
redirect.bits.level := RedirectLevel.flush
redirect.bits.target := rollbackLqWb(i).bits.pc
...
io.mdpTrain := Mux1H(oldestOH, allRedirect)
```

[LSQBundle.scala:308](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala:308) 定义 MDP query 字段：

```scala
class MDPQueryIO (implicit p: Parameters) extends XSBundle {
  // load inst will not be executed until former store (predicted by mdp) addr calcuated
  val loadWaitBit        = Bool()
  val waitForRobIdx      = new RobPtr
}
```

[NewLoadUnit.scala:355](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala:355) 说明 LoadUnit 将等待信息送给 StoreQueue forward：

```scala
storeForwardReq.loadWaitBit := uop.loadWaitBit
storeForwardReq.loadWaitStrict := uop.loadWaitStrict
storeForwardReq.ssid := uop.ssid
storeForwardReq.storeSetHit := uop.storeSetHit
storeForwardReq.waitForRobIdx := uop.waitForRobIdx
```

[VirtualStoreQueue.scala:229](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/VirtualStoreQueue.scala:229) 说明 `waitForRobIdx` 如何定位 store queue entry：

```scala
val s0MdpHitVec = WireInit(VecInit((0 until StoreQueueSize).map(j =>
  s0Req.bits.loadWaitBit && dataEntries(j).robIdx === s0Req.bits.waitForRobIdx && ctrlEntries(j).allocated)))
val s1ReqValid  = RegNext(s0Req.valid && s0Req.bits.loadWaitBit)
```

[NewStoreQueue.scala:520](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala:520) 说明为什么波形里 `addrInvalid=1` 会阻止 load 完成：

```scala
val s2PreciseMdpWait     = s2MdpQueryRespValid && s2HasAddrInvalidVec(s2AddrInvalidSqIdx.value)
val s2MdpHitOutOfRange   = s2MdpQueryRespValid && s2AddrInvalidSqIdx.isNotBefore(s2PhysicalQueueUpper)
val s2NeedPreciseMdpWait = s2PreciseMdpWait || s2MdpHitOutOfRange
...
s2Resp.bits.addrInvalid.valid := Mux(s2LoadWaitStrict, s2StrictMdpWait, s2NeedPreciseMdpWait)
s2Resp.bits.addrInvalid.bits := Mux(s2LoadWaitStrict, s2WaitStrictSqIdx, s2AddrInvalidSqIdx)
```

[Parameters.scala:820](/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/Parameters.scala:820) 给出本配置中的 StoreSet 参数：

```scala
def WaitTableSize = 1024
def MemPredPCWidth = log2Up(WaitTableSize)
def SSITSize = WaitTableSize
def LFSTSize = 64
def SSIDWidth = log2Up(LFSTSize)
def LFSTWidth = 2
def StoreSetEnable = true
def LFSTEnable = true
```

## 异常与注意点

1. 波形中的核心层级比常见文档多一级 `cpu`：实际是 `TOP.SimTop.cpu.l_soc.core_with_l2.core`。
2. `dispatch.io_fromRename_*_bits_loadWaitBit` 是 rename 侧原始字段，不能用它判断 StoreSet 最终是否生效。最终是否等待要看 LFST response 和进入 LoadUnit 的 uop 字段；ROB235 的 LoadUnit 波形明确为 `loadWaitBit=1 waitForRobIdx=234`。
3. 本次分析中心是 MDP/RAW 微架构路径；difftest 顶层信号存在，但本报告没有把全部 commit/CSR difftest 状态展开。对于本问题，关键 architectural 安全性由 memory-violation redirect 和 replay 后正确 `ldout` 值支撑。
