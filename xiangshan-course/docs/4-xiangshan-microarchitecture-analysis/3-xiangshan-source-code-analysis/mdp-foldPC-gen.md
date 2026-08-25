# XiangShan KunMingHu v3 MDP fold PC 生成与使用路径分析

## 分析范围

- skill：`/nfs/home/yanyusong/XiangShanLab/tools/xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu`
- 源码：`/nfs/home/yanyusong/mdp-kmhv3/XiangShan`
- 源码提交：`055d8ad9e56b0b618f2d549a97f3a028986b4849`
- weekly sync：已执行，结果为 `skip: last sync 0.22 days ago < 7 days`
- 输出文件：`/nfs/home/yanyusong/XiangShanLab/tools/mdp-foldPC-gen.md`

本分析聚焦 memory dependency predictor，也就是 `mem/mdp` 里的 Store Set / WaitTable 相关 folded PC。设计文档目录 `/nfs/home/yuanmiaomiao/XiangShanLab/XiangShan-Design-Doc` 在本机不存在，因此行为判断以 KunMingHu v3 源码为准。

## 一句话结论

KunMingHu v3 的 MDP fold PC 是一个用于访问 MDP 表的 10-bit PC 哈希索引：

```text
mdp_fold_pc = XORFold(pc(VAddrBits - 1, 1), MemPredPCWidth)
MemPredPCWidth = log2Up(WaitTableSize) = log2Up(1024) = 10
```

它有两条有效生成路径：

1. 查询路径：IFU 对每条送往 IBuffer 的指令生成 `foldpc`，随 `CtrlFlow -> DecodeInUop -> DecodeOutUop` 透传，decode fire 时送到 `MemCtrl -> SSIT.raddr`。
2. 训练路径：发生 load violation / MDP train 时，后端 `CtrlBlock` 从 `pcMem` 读 FTQ start PC，加上指令 offset，再用同一 `XORFold` 公式重新生成 `ldpc/stpc/waddr`，用于更新 SSIT/WaitTable。

## Who / Why / How / From What / To What

| 问题 | 回答 |
| --- | --- |
| who | 查询路径由 `frontend.ifu.Ifu` 生成，`IBuffer` 和 backend decode 透传，`CtrlBlock` 交给 `MemCtrl`，`MemCtrl` 驱动 `SSIT` 读口。训练路径由 `backend.CtrlBlock` 根据 `io.fromMem.mdpTrain` 重新计算。 |
| why | MDP 的 SSIT/WaitTable 是有限项表，当前 `WaitTableSize = SSITSize = 1024`，不能用完整 PC 直接索引；fold PC 用较低硬件成本把指令 PC 映射到表项。 |
| how | `XORFold` 先把 `pc(VAddrBits-1,1)` 补零到 10 的整数倍，再按 10-bit 分片做并行 XOR。 |
| from what | 查询路径来自 IFU 每 lane 对齐后的真实指令 PC；训练路径来自 backend `pcMem(ftqIdx) + ftqOffset`。 |
| to what | 查询路径到 `SSIT.io.raddr`；训练路径到 `MemPredUpdateReq.ldpc/stpc/waddr`，再写 SSIT/WaitTable。 |

## 参数来源与 fold 算法

MDP PC 索引宽度来自 `WaitTableSize`：

```scala
// load violation predict
def WaitTableSize = 1024
def MemPredPCWidth = log2Up(WaitTableSize)
// store set parameters
def SSITSize = WaitTableSize
```

证据：`/nfs/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/Parameters.scala:818-827`。

`XORFold` 的源码：

```scala
object XORFold {
  def apply(input: UInt, resWidth: Int): UInt = {
    require(resWidth > 0)
    val fold_range = (input.getWidth + resWidth - 1) / resWidth
    val value = ZeroExt(input, fold_range * resWidth)
    ParallelXOR((0 until fold_range).map(i => value(i*resWidth+resWidth-1, i*resWidth)))
  }
}
```

证据：`/nfs/home/yanyusong/mdp-kmhv3/XiangShan/utility/src/main/scala/utility/BitUtils.scala:236-242`。

这不是简单截低 10 位，而是跨 PC 位段 XOR，因此高位 PC 也会参与 MDP 表索引。bit0 被丢弃是因为 RISC-V 指令至少 16-bit 对齐；压缩指令存在时，bit1 仍然保留在输入里。

## 查询路径：每条指令的 foldPC 如何进入 MDP

### 1. IFU 先得到每 lane 的真实指令 PC

IFU 在 s1 阶段对取回的指令进行对齐。基础 PC 来自 `getInstrPc`，随后对跨半条 RVI 指令、invalid taken 边界做修正：

```scala
private val s1_baseAlignedInstrPcVec = VecInit(s1_alignedInstrVec.map(instr => getInstrPc(instr, s1_fetchBlock)))
private val s1_alignedInstrPcVec = WireDefault(s1_baseAlignedInstrPcVec)
for (i <- 0 until IBufferEnqueueWidth) {
  when(s1_alignedInstrVec(i).isPrevEndHalfRvi) {
    s1_alignedInstrPcVec(i) := s1_prevEndHalfRviPc
  }.elsewhen(s1_alignedInstrVec(i).invalidTaken) {
    s1_alignedInstrPcVec(i) := Mux(
      s1_alignedInstrVec(i).blockSel,
      s1_totalEndHalfRviPc,
      s1_firstEndHalfRviPc
    )
  }
}
```

证据：`src/main/scala/xiangshan/frontend/ifu/Ifu.scala:304-331`。

这说明 MDP fold PC 是按每条可能送入 IBuffer 的指令计算，不是按 fetch block 起始地址统一计算。

### 2. IFU s1 对每 lane PC 做 XORFold

```scala
private val s1_alignedFoldPc =
  VecInit(s1_alignedInstrPcVec.map(i => XORFold(i(VAddrBits - 1, 1), MemPredPCWidth)))
```

证据：`src/main/scala/xiangshan/frontend/ifu/Ifu.scala:336-337`。

这里 `VecInit(...map...)` 的宽度是 `IBufferEnqueueWidth`，所以一组 fetch bundle 中每条 lane 都有独立的 MDP fold PC。

### 3. IFU s1 到 s2 寄存，随 fetch payload 发送到 IBuffer

```scala
private val s2_alignedInstrPcVec = RegEnable(s1_alignedInstrPcVec, s1_fire)
private val s2_alignedFoldPc     = RegEnable(s1_alignedFoldPc, s1_fire)
...
io.toIBuffer.bits.pc     := s2_alignedInstrPcVec // for debug
io.toIBuffer.bits.foldpc := s2_alignedFoldPc
```

证据：`src/main/scala/xiangshan/frontend/ifu/Ifu.scala:397-403`、`:524-564`。

`FetchToIBuffer` 明确定义了每 lane 的 `foldpc`：

```scala
class FetchToIBuffer extends FrontendBundle {
  ...
  val foldpc: Vec[UInt] = Vec(IBufferEnqueueWidth, UInt(MemPredPCWidth.W))
}
```

证据：`src/main/scala/xiangshan/frontend/Bundles.scala:313-319`。

### 4. IBuffer 保存并输出 foldPC

`IBufEntry` 保存 `foldpc`，从 fetch bundle 进入：

```scala
class IBufEntry extends IBufferBundle {
  val inst: UInt = UInt(32.W)
  val pc: PrunedAddr = PrunedAddr(VAddrBits)
  val foldpc: UInt = UInt(MemPredPCWidth.W)
  ...
  def fromFetch(fetch: FetchToIBuffer, i: Int): IBufEntry = {
    inst := fetch.instrs(i)
    pc := fetch.pc(i)
    foldpc := fetch.foldpc(i)
    ...
  }
}
```

证据：`src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala:49-75`。

输出时继续透传到 `IBufOutEntry` 和 `CtrlFlow`：

```scala
result.foldpc := foldpc
...
cf.foldpc := foldpc
```

证据：`src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala:78-97`、`:132-158`。

IBuffer 的存储结构是完整 `IBufEntry` 向量，enqueue/dequeue 不会重算 folded PC，只搬运该字段：

```scala
private val ibuf: Vec[IBufEntry] = RegInit(VecInit.fill(Size)(0.U.asTypeOf(new IBufEntry)))
private val enqData = VecInit.tabulate(EnqueueWidth)(i => Wire(new IBufEntry).fromFetch(io.in.bits, i))
...
ibuf(bank + idx * NumWriteBank) := Mux(wen, writeEntry, ibuf(bank + idx * NumWriteBank))
...
deqEntries(i).bits := Mux1H(UIntToOH(deqBankPtrVec(i).value), readStage1)
```

证据：`src/main/scala/xiangshan/frontend/ibuffer/IBuffer.scala:75-86`、`:149-158`、`:286-348`。

### 5. Frontend 到 backend 的连接

```scala
ifu.io.toIBuffer <> ibuffer.io.in
...
io.backend.cfVec <> ibuffer.io.out
```

证据：`src/main/scala/xiangshan/frontend/Frontend.scala:236-253`。

`CtrlFlow` 中包含 `foldpc`：

```scala
class CtrlFlow extends XSBundle {
  val instr = UInt(32.W)
  val pc = UInt(VAddrBits.W)
  val foldpc = UInt(MemPredPCWidth.W)
  ...
}
```

证据：`src/main/scala/xiangshan/Bundle.scala:93-98`。

### 6. Decode 透明透传 foldPC

`DecodeInUop` 和 `DecodeOutUop` 都有 `foldpc` 字段，注释明确说明用于 MDP：

```scala
class DecodeInUop extends XSBundle {
  val foldpc = UInt(MemPredPCWidth.W) // for mdp
  ...
}
class DecodeOutUop extends XSBundle {
  val foldpc = UInt(MemPredPCWidth.W) // for mdp
  ...
}
```

证据：`src/main/scala/xiangshan/backend/Bundles.scala:106-137`。

`CtrlBlock` 从 frontend `CtrlFlow` 构造 `DecodeInUop`：

```scala
val decodeConnectFromFrontend = Wire(Vec(DecodeWidth, new DecodeInUop))
decodeConnectFromFrontend.zip(decodeFromFrontend).map(x => x._1.connectCtrlFlow(x._2.bits))
...
decodeIn.bits := Mux(decodeBufValid(i), decodeBufBits(i), decodeConnectFromFrontend(i))
```

证据：`src/main/scala/xiangshan/backend/CtrlBlock.scala:500-529`。

`DecodeUnit` 查表译码后调用 `connectDecodeInUop`，把 `DecodeInUop` 的同名字段复制到 `DecodeOutUop`：

```scala
val decodedInst: DecodeOutUop = Wire(new DecodeOutUop()).decode(ctrl_flow.instr, decode_table)
decodedInst.connectDecodeInUop(io.enq.decodeInUop)
```

证据：`src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:822-824`；`connectDecodeInUop` 见 `src/main/scala/xiangshan/backend/Bundles.scala:200-204`。

复杂指令拆分路径也保留同一个 fold PC，因为拆分 uop 从 latched decoded inst 复制：

```scala
val csBundle = Wire(Vec(maxUopSize, new DecodeOutUop))
csBundle.foreach { case dst =>
  dst := latchedInst
  dst.numWB := latchedUopInfo.numOfWB
  ...
}
```

证据：`src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala:188-199`。

## MDP 查询端：foldPC 如何成为 SSIT 读地址

`CtrlBlock` 在 decode 输出 fire 时把 folded PC 抽出来：

```scala
// memory dependency predict
// when decode, send fold pc to mdp
private val mdpFlodPcVecVld = Wire(Vec(DecodeWidth, Bool()))
private val mdpFlodPcVec = Wire(Vec(DecodeWidth, UInt(MemPredPCWidth.W)))
for (i <- 0 until DecodeWidth) {
  mdpFlodPcVecVld(i) := decode.io.out(i).fire
  mdpFlodPcVec(i) := decode.io.out(i).bits.foldpc
}
...
memCtrl.io.mdpFoldPcVecVld := mdpFlodPcVecVld
memCtrl.io.mdpFlodPcVec := mdpFlodPcVec
```

证据：`src/main/scala/xiangshan/backend/CtrlBlock.scala:639-653`。

`MemCtrl` 把它接到 SSIT 的读使能和读地址：

```scala
private val ssit = Module(new SSIT)
private val lfst = Module(new LFST)
...
for (i <- 0 until RenameWidth) {
  ssit.io.ren(i) := io.mdpFoldPcVecVld(i)
  ssit.io.raddr(i) := io.mdpFlodPcVec(i)
}
io.ssit2Rename := ssit.io.rdata
```

证据：`src/main/scala/xiangshan/backend/ctrlblock/MemCtrl.scala:14-31`。

`SSIT` 的读地址注释直接写明它是 xor hashed decode PC：

```scala
val ren = Vec(DecodeWidth, Input(Bool()))
val raddr = Vec(DecodeWidth, Input(UInt(MemPredPCWidth.W))) // xor hashed decode pc(VaddrBits-1, 1)
val rdata = Vec(RenameWidth, Output(new SSITEntry))
```

证据：`src/main/scala/xiangshan/mem/mdp/StoreSet.scala:52-63`。

SSIT 在 decode 阶段读，结果送 rename：

```scala
// raddrs are sent to ssit in decode
// rdata will be send to rename
require(DecodeWidth == RenameWidth)
...
valid_array.io.ren.get(i) := io.ren(i)
data_array.io.ren.get(i) := io.ren(i)
valid_array.io.raddr(i) := io.raddr(i)
data_array.io.raddr(i) := io.raddr(i)
...
io.rdata(i).valid := valid_array.io.rdata(i)
io.rdata(i).ssid := data_array.io.rdata(i).ssid
io.rdata(i).strict := data_array.io.rdata(i).strict
```

证据：`src/main/scala/xiangshan/mem/mdp/StoreSet.scala:65-67`、`:125-140`。

这条查询路径的动态语义是：当第 `i` 路 decode 输出真正 fire，`decode.io.out(i).bits.foldpc` 作为 SSIT 的第 `i` 个读地址；下一阶段 rename 看到对应 `SSITEntry(valid, ssid, strict)`，用于后续 store-set/LFST 相关控制。

## MDP 训练端：load violation 后如何重新生成 folded PC

MDP 训练入口来自 memory side 的 `io.fromMem.mdpTrain`。`CtrlBlock` 不复用前端携带的 `foldpc`，而是从 `pcMem` 中用 FTQ index 读出 fetch block start PC，再加上 load/store 的 offset，重新计算 folded PC：

```scala
val mdpTrainValid = io.fromMem.mdpTrain.valid
for ((pcMemIdx, i) <- pcMemRdIndexes("memPredLoad").zipWithIndex) {
  val ren   = mdpTrainValid
  val raddr = io.fromMem.mdpTrain.bits.ftqIdx.value
  val offset = RegEnable(io.fromMem.mdpTrain.bits.getPcOffset, mdpTrainValid)
  pcMem.io.ren.get(pcMemIdx) := ren
  pcMem.io.raddr(pcMemIdx) := raddr
  memCtrl.io.memPredUpdate.ldpc := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)

  // update wait table, will be remove in the future
  memCtrl.io.memPredUpdate.waddr := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
  memCtrl.io.memPredUpdate.wdata := true.B
}
for ((pcMemIdx, i) <- pcMemRdIndexes("memPredStore").zipWithIndex) {
  val ren   = mdpTrainValid
  val raddr = io.fromMem.mdpTrain.bits.stFtqIdx.value
  val offset = RegEnable(io.fromMem.mdpTrain.bits.getStPcOffset, mdpTrainValid)
  pcMem.io.ren.get(pcMemIdx) := ren
  pcMem.io.raddr(pcMemIdx) := raddr
  memCtrl.io.memPredUpdate.stpc := XORFold((pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
}
memCtrl.io.memPredUpdate.valid := RegNext(mdpTrainValid) // pc is ready, 1 cycle later
```

证据：`src/main/scala/xiangshan/backend/CtrlBlock.scala:217-238`。

`pcMem` 保存 FTQ start PC，写入来自 frontend FTQ：

```scala
private val pcMem = Module(new SyncDataModuleTemplate(PrunedAddr(VAddrBits), FtqSize, numPcMemRead, 1, "BackendPC", hasRen = hasRen))
...
pcMem.io.wen.head   := GatedValidRegNext(io.frontend.fromFtq.wen)
pcMem.io.waddr.head := RegEnable(io.frontend.fromFtq.ftqIdx, io.frontend.fromFtq.wen)
pcMem.io.wdata.head := RegEnable(io.frontend.fromFtq.startPc, io.frontend.fromFtq.wen)
```

证据：`src/main/scala/xiangshan/backend/CtrlBlock.scala:75-105`、`:776-778`。

`MemPredUpdateReq` 明确规定 `ldpc/stpc` 默认应该已经 xor folded：

```scala
class MemPredUpdateReq extends XSBundle  {
  val valid = Bool()
  val waddr = UInt(MemPredPCWidth.W)
  val wdata = Bool()
  // by default, ldpc/stpc should be xor folded
  val ldpc = UInt(MemPredPCWidth.W)
  val stpc = UInt(MemPredPCWidth.W)
}
```

证据：`src/main/scala/xiangshan/Bundle.scala:584-596`。

## SSIT 内部如何使用 folded PC

SSIT 有两组存储：

- `valid_array`: `SSITSize` 项，每项 Bool，表示某 folded PC 是否已经有 store set 记录。
- `data_array`: `SSITSize` 项，每项 `SSITDataEntry(ssid, strict)`。

```scala
val valid_array = Module(new SyncDataModuleTemplate(
  Bool(),
  SSITSize,
  SSIT_READ_PORT_NUM,
  SSIT_WRITE_PORT_NUM,
  hasRen = hasRen,
))

val data_array = Module(new SyncDataModuleTemplate(
  new SSITDataEntry,
  SSITSize,
  SSIT_READ_PORT_NUM,
  SSIT_WRITE_PORT_NUM,
  hasRen = hasRen,
))
```

证据：`src/main/scala/xiangshan/mem/mdp/StoreSet.scala:86-101`。

### search/read

decode 查询时，`foldpc` 是 `raddr`。SSIT 同时读 valid 和 data：

```scala
valid_array.io.raddr(i) := io.raddr(i)
data_array.io.raddr(i) := io.raddr(i)
io.rdata(i).valid := valid_array.io.rdata(i)
io.rdata(i).ssid := data_array.io.rdata(i).ssid
io.rdata(i).strict := data_array.io.rdata(i).strict
```

证据：`StoreSet.scala:130-140`。

如果 `valid=false`，rename 侧应视为当前指令没有已知 store set；如果 `strict=true`，表示该 folded PC 对应的 load 需要更保守等待。

### update

当 `io.update.valid` 有效时，SSIT 复用 decode 读端口 0/1 读取 load/store 的旧条目：

```scala
when (io.update.valid) {
  valid_array.io.raddr(SSIT_UPDATE_LOAD_READ_PORT) := io.update.ldpc
  valid_array.io.raddr(SSIT_UPDATE_STORE_READ_PORT) := io.update.stpc
  data_array.io.raddr(SSIT_UPDATE_LOAD_READ_PORT) := io.update.ldpc
  data_array.io.raddr(SSIT_UPDATE_STORE_READ_PORT) := io.update.stpc

  valid_array.io.ren.get(SSIT_UPDATE_LOAD_READ_PORT)  := true.B
  valid_array.io.ren.get(SSIT_UPDATE_STORE_READ_PORT) := true.B
  data_array.io.ren.get(SSIT_UPDATE_LOAD_READ_PORT)   := true.B
  data_array.io.ren.get(SSIT_UPDATE_STORE_READ_PORT)  := true.B
}
```

证据：`StoreSet.scala:170-187`。

源码注释说明这个复用是安全的：`If io.update.valid, a redirect will be send to frontend, then decode will not need to read SSIT`，证据见 `StoreSet.scala:69-77`。也就是说，训练更新和正常 decode 查询共享读端口；当 load violation 触发训练时，前端会 redirect，decode 不需要正常读 SSIT。

### replace / merge store-set

SSIT 的更新规则遵循 Store Sets 思路：

```scala
val s2_ldSsidAllocate = XORFold(s2_mempred_update_req.ldpc, SSIDWidth)
val s2_stSsidAllocate = XORFold(s2_mempred_update_req.stpc, SSIDWidth)
val s2_allocSsid = Mux(s2_ldSsidAllocate < s2_stSsidAllocate, s2_ldSsidAllocate, s2_stSsidAllocate)
val s2_winnerSSID = Mux(s2_loadOldSSID < s2_storeOldSSID, s2_loadOldSSID, s2_storeOldSSID)
```

证据：`StoreSet.scala:202-219`。

四类情况：

| 情况 | 条件 | 行为 | 代码证据 |
| --- | --- | --- | --- |
| load/store 都未分配 | `b00` | 用 `s2_allocSsid` 同时给 load PC 和 store PC 写 SSIT | `StoreSet.scala:246-263` |
| load 已分配，store 未分配 | `b10` | 给 store PC 写入由 load PC 派生的 SSID | `StoreSet.scala:264-273` |
| store 已分配，load 未分配 | `b01` | 给 load PC 写入由 store PC 派生的 SSID | `StoreSet.scala:274-283` |
| 两者都已分配 | `b11` | 选择较小旧 SSID 为 winner，load/store 都改写为 winner；若本来相同，则把 load entry 设为 strict | `StoreSet.scala:284-305` |

写入口封装：

```scala
def update_ld_ssit_entry(pc: UInt, valid: Bool, ssid: UInt, strict: Bool) = {
  valid_array.io.wen(SSIT_UPDATE_LOAD_WRITE_PORT) := true.B
  valid_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) := pc
  valid_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT) := valid
  data_array.io.wen(SSIT_UPDATE_LOAD_WRITE_PORT) := true.B
  data_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) := pc
  data_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT).ssid := ssid
  data_array.io.wdata(SSIT_UPDATE_LOAD_WRITE_PORT).strict := strict
}
```

证据：`StoreSet.scala:220-244`。

### 写端口冲突

如果 load folded PC 和 store folded PC 相同，SSIT 禁用 store 写口，只保留 load 写口，避免 `SyncDataModuleTemplate` 同地址多写冲突：

```scala
when(valid_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) === valid_array.io.waddr(SSIT_UPDATE_STORE_WRITE_PORT)){
  valid_array.io.wen(SSIT_UPDATE_STORE_WRITE_PORT) := false.B
}

when(data_array.io.waddr(SSIT_UPDATE_LOAD_WRITE_PORT) === data_array.io.waddr(SSIT_UPDATE_STORE_WRITE_PORT)){
  data_array.io.wen(SSIT_UPDATE_STORE_WRITE_PORT) := false.B
}
```

证据：`StoreSet.scala:308-315`。

这个冲突可能有两种来源：同一条 load/store pair 本身映射到同一个 folded PC，或者不同完整 PC 因 XORFold 别名映射到同一个 10-bit 索引。代码选择 load 写口优先。

### release / flush

SSIT 没有按单条指令释放 entry；它通过周期性 flush 清空 valid bits：

```scala
val resetStepCounter = RegInit(0.U(log2Up(SSITSize + 1).W))
val s_idle :: s_flush :: Nil = Enum(2)
val state = RegInit(s_flush)
...
is(s_flush) {
  when(resetStepCounter === (SSITSize - 1).U) {
    state := s_idle
    resetStepCounter := 0.U
  }.otherwise{
    resetStepCounter := resetStepCounter + 1.U
  }
  valid_array.io.wen(SSIT_MISC_WRITE_PORT) := true.B
  valid_array.io.waddr(SSIT_MISC_WRITE_PORT) := resetStepCounter
  valid_array.io.wdata(SSIT_MISC_WRITE_PORT) := false.B
}
```

证据：`StoreSet.scala:142-168`。

这是一种老化/重置机制。reset 后从 `s_flush` 开始逐项清 valid；运行中由 `lvpred_timeout` 选择 resetCounter 的某一位触发再次 flush。

## WaitTable 中的 folded PC

`WaitTable` 也以 `MemPredPCWidth` 作为读/写地址宽度：

```scala
val raddr = Vec(DecodeWidth, Input(UInt(MemPredPCWidth.W))) // decode pc(VaddrBits-1, 1)
val rdata = Vec(DecodeWidth, Output(Bool())) // loadWaitBit
val update = Input(new MemPredUpdateReq)
...
val data = RegInit(VecInit(Seq.fill(WaitTableSize)(0.U(2.W))))
```

证据：`src/main/scala/xiangshan/mem/mdp/WaitTable.scala:33-46`。

读路径：

```scala
for (i <- 0 until DecodeWidth) {
  io.rdata(i) := (data(io.raddr(i))(LWTUse2BitCounter.B.asUInt) || io.csrCtrl.no_spec_load) && !io.csrCtrl.lvpred_disable
}
```

证据：`WaitTable.scala:49-52`。

写路径：

```scala
when(io.update.valid){
  data(io.update.waddr) := Cat(data(io.update.waddr)(0), true.B)
}
```

证据：`WaitTable.scala:54-57`。

但当前 `MemCtrl` 中 `WaitTable` 读结果未接入 rename：

```scala
//  io.waitTable2Rename := waittable.io.rdata
io.waitTable2Rename := DontCare
io.ssit2Rename := ssit.io.rdata
```

证据：`src/main/scala/xiangshan/backend/ctrlblock/MemCtrl.scala:28-31`。

所以在这份 v3 源码中，MDP 查询的有效路径是 SSIT；WaitTable 的更新地址仍由 `waddr` 生成，源码注释也写了未来可能移除。

## uncache 与异常边界

uncache 路径在 IFU s2 会覆盖单个 lane 的 `instrs/pc/isRvc/valid/enqEnable`：

```scala
when(s2_reqIsUncache) {
  io.toIBuffer.bits.instrs(s2_alignShiftNum) := ...
  io.toIBuffer.bits.pc(s2_alignShiftNum) := uncachePc
  ...
  io.toIBuffer.bits.valid     := Cat(0.U(FetchBlockInstNum.W), UIntToOH(s2_alignShiftNum))
  io.toIBuffer.bits.enqEnable := Cat(0.U(FetchBlockInstNum.W), UIntToOH(s2_alignShiftNum))
}
```

证据：`src/main/scala/xiangshan/frontend/ifu/Ifu.scala:636-662`。

这段没有额外覆盖 `foldpc`，依赖 s1 已经对 `s1_alignedInstrPcVec` 做了跨半条指令/边界修正，再统一生成 `s1_alignedFoldPc`。因此对于被允许 enqueue 的单条 uncache 指令，MDP fold PC 仍来自同 lane 的对齐 PC。

## SimFrontend 路径

仿真前端绕过真实 IFU/IBuffer，但用同一公式生成 `foldpc`：

```scala
cfVec.bits.pc     := fetchOut.pc
cfVec.bits.foldpc := XORFold(fetchOut.pc(VAddrBits - 1, 1), MemPredPCWidth)
```

证据：`src/main/scala/xiangshan/frontend/simfrontend/SimFrontend.scala:233-241`。

## 动态流程

### 正常查询

1. IFU s1 得到每 lane 对齐 PC。
2. IFU s1 计算 `XORFold(pc[VAddrBits-1:1], 10)`。
3. IFU s2 把 `foldpc Vec` 发送到 IBuffer。
4. IBuffer 保存或 bypass 完整 `IBufEntry`。
5. backend decode 收到 `CtrlFlow.foldpc`，转换到 `DecodeInUop/DecodeOutUop`。
6. `decode.io.out(i).fire` 时，`CtrlBlock` 送 `mdpFlodPcVec(i)` 到 `MemCtrl`。
7. `SSIT` 用该 folded PC 读 `valid/ssid/strict`，结果送 rename。

### replay / load violation 训练

1. memory side 发现 load/store 依赖预测不足，产生 `io.fromMem.mdpTrain.valid`。
2. `CtrlBlock` 用 load 的 `ftqIdx + getPcOffset` 和 store 的 `stFtqIdx + getStPcOffset` 重构完整指令 PC。
3. 对 load/store PC 分别 `XORFold(..., MemPredPCWidth)`，生成 `ldpc/stpc`。
4. `MemPredUpdateReq.valid` 延后一拍，因为 `pcMem` 读数据一拍后可用。
5. `SSIT` 复用读端口读取旧 load/store entry，再按四种 store-set merge 规则更新。
6. 如果两个 folded PC 相同，store 写口被关闭，load 写口优先。

## Theory-to-Code Mapping

| 理论概念 | 课程/架构背景 | 代码实体 | 具体信号 | XiangShan 实现方式 | 差异/注意点 |
| --- | --- | --- | --- | --- | --- |
| 多发射前端 metadata | 多条指令并行进入后端，需要每条指令携带自己的控制信息 | `FetchToIBuffer`、`IBufEntry`、`CtrlFlow` | `foldpc Vec` | 每个 IBuffer enqueue lane 单独计算和保存 folded PC | 不是每个 fetch block 一个索引，而是每条指令一个索引 |
| memory dependence prediction | 动态调度中的 load/store 顺序风险需要预测 | `SSIT`、`LFST`、`MemCtrl` | `ssit.io.raddr`、`ssit.io.rdata` | decode 用 folded PC 查 SSIT，rename 使用 store-set 结果 | folded PC 会别名，不是完整 PC 精确匹配 |
| replay 后训练 | 依赖预测失败后应强化预测器 | `MemPredUpdateReq`、`CtrlBlock`、`SSIT` | `ldpc/stpc/waddr` | 后端从 FTQ PC + offset 重新计算 folded PC 后更新表 | 训练路径不依赖前端保存的旧 `foldpc` |
| 结构冲突 | 表端口有限，需要规定端口复用 | `SSIT_READ_PORT_NUM = DecodeWidth`，更新复用 port0/1 | `SSIT_UPDATE_LOAD_READ_PORT`、`SSIT_UPDATE_STORE_READ_PORT` | update valid 时接管读端口；注释说明此时会 redirect，decode 不需要读 | 同地址双写时 load 写口优先，store 写口屏蔽 |

## 数据路径图

```mermaid
flowchart LR
  PC[IFU aligned PC per instruction lane] --> FOLD[XORFold pc(VAddrBits-1,1), MemPredPCWidth]
  FOLD --> FTIB[FetchToIBuffer.foldpc Vec]
  FTIB --> IBE[IBufEntry.foldpc]
  IBE --> CF[CtrlFlow.foldpc]
  CF --> DIN[DecodeInUop.foldpc]
  DIN --> DOUT[DecodeOutUop.foldpc]
  DOUT --> CB[CtrlBlock mdpFlodPcVec]
  CB --> MC[MemCtrl]
  MC --> SSIT[SSIT raddr]
  SSIT --> RN[Rename ssit2Rename]
```

## 训练路径图

```mermaid
flowchart LR
  MT[mem mdpTrain ftqIdx/offset] --> PCM[CtrlBlock pcMem read]
  PCM --> LPC[load PC = pcMem(ftqIdx) + getPcOffset]
  PCM --> SPC[store PC = pcMem(stFtqIdx) + getStPcOffset]
  LPC --> LFP[XORFold -> ldpc/waddr]
  SPC --> SFP[XORFold -> stpc]
  LFP --> UREQ[MemPredUpdateReq]
  SFP --> UREQ
  UREQ --> SSITR[SSIT update read old entries]
  SSITR --> MERGE[Store-set allocate/merge]
  MERGE --> SSITW[SSIT valid/data write]
```

## 时序图

```waveform-draw
{signal: [
  {name: "s1_fire", wave: "010...."},
  {name: "s1_aligned_pc", wave: "x=.....", data: ["pc per lane"]},
  {name: "s1_foldpc", wave: "x=.....", data: ["XORFold"]},
  {name: "s2_foldpc", wave: "x.=-...", data: ["RegEnable"]},
  {name: "toIBuffer.fire", wave: "0.10..."},
  {name: "IBuffer entry", wave: "x..=...", data: ["foldpc stored/bypassed"]},
  {name: "decode.out.fire", wave: "0...10."},
  {name: "SSIT.ren", wave: "0...10."},
  {name: "SSIT.raddr", wave: "x...=x.", data: ["decode foldpc"]}
]}
```

```waveform-draw
{signal: [
  {name: "mdpTrain.valid", wave: "010..."},
  {name: "pcMem.ren", wave: "010..."},
  {name: "pcMem.rdata + offset", wave: "x.=...", data: ["reconstructed PC"]},
  {name: "memPredUpdate.valid", wave: "0.10.."},
  {name: "memPredUpdate.ldpc/stpc", wave: "x.=x..", data: ["folded load/store PC"]},
  {name: "SSIT update read", wave: "0.10.."},
  {name: "SSIT write", wave: "0..10."}
]}
```

## 伪代码

```scala
// query path, IFU
for (lane <- 0 until IBufferEnqueueWidth) {
  pc = alignedInstrPc(lane)
  foldpc(lane) = XORFold(pc(VAddrBits - 1, 1), MemPredPCWidth)
}

// query path, decode -> MDP
for (i <- 0 until DecodeWidth) {
  if (decodeOut(i).fire) {
    ssit.ren(i) = true
    ssit.raddr(i) = decodeOut(i).bits.foldpc
  }
}

// training path, after memory violation
if (mdpTrain.valid) {
  loadPc  = pcMem(mdpTrain.ftqIdx)   + mdpTrain.getPcOffset
  storePc = pcMem(mdpTrain.stFtqIdx) + mdpTrain.getStPcOffset
  update.ldpc  = XORFold(loadPc(VAddrBits - 1, 1), MemPredPCWidth)
  update.waddr = update.ldpc
  update.stpc  = XORFold(storePc(VAddrBits - 1, 1), MemPredPCWidth)
  update.valid = RegNext(true)
}
```

## 源码证据索引

| 主题 | 文件与行号 |
| --- | --- |
| MDP PC 宽度参数 | `src/main/scala/xiangshan/Parameters.scala:818-827` |
| XORFold 算法 | `utility/src/main/scala/utility/BitUtils.scala:236-242` |
| IFU 每 lane PC 修正 | `src/main/scala/xiangshan/frontend/ifu/Ifu.scala:304-331` |
| IFU 每 lane folded PC 生成 | `src/main/scala/xiangshan/frontend/ifu/Ifu.scala:336-337` |
| IFU s1/s2 寄存与发送 | `src/main/scala/xiangshan/frontend/ifu/Ifu.scala:397-403`、`:524-564` |
| FetchToIBuffer 字段 | `src/main/scala/xiangshan/frontend/Bundles.scala:313-319` |
| IBuffer 保存/输出 | `src/main/scala/xiangshan/frontend/ibuffer/Bundles.scala:49-97`、`:132-158` |
| IBuffer 队列读写 | `src/main/scala/xiangshan/frontend/ibuffer/IBuffer.scala:75-86`、`:149-158`、`:286-348` |
| Frontend 连接 | `src/main/scala/xiangshan/frontend/Frontend.scala:236-253` |
| CtrlFlow 字段 | `src/main/scala/xiangshan/Bundle.scala:93-98` |
| DecodeIn/Out 字段 | `src/main/scala/xiangshan/backend/Bundles.scala:106-137` |
| Decode 透传 | `src/main/scala/xiangshan/backend/CtrlBlock.scala:500-529`、`backend/decode/DecodeUnit.scala:822-824` |
| CtrlBlock 提取 decode foldPC | `src/main/scala/xiangshan/backend/CtrlBlock.scala:639-653` |
| MemCtrl 接 SSIT | `src/main/scala/xiangshan/backend/ctrlblock/MemCtrl.scala:14-31` |
| SSIT IO 与读路径 | `src/main/scala/xiangshan/mem/mdp/StoreSet.scala:52-67`、`:125-140` |
| SSIT update/merge | `src/main/scala/xiangshan/mem/mdp/StoreSet.scala:170-187`、`:202-230`、`:246-315` |
| SSIT flush 状态 | `src/main/scala/xiangshan/mem/mdp/StoreSet.scala:142-168` |
| WaitTable folded PC | `src/main/scala/xiangshan/mem/mdp/WaitTable.scala:33-57` |
| 后端训练 folded PC | `src/main/scala/xiangshan/backend/CtrlBlock.scala:217-238` |
| pcMem 写入 FTQ start PC | `src/main/scala/xiangshan/backend/CtrlBlock.scala:75-105`、`:776-778` |
| MemPredUpdateReq 定义 | `src/main/scala/xiangshan/Bundle.scala:584-596` |
| SimFrontend 等价公式 | `src/main/scala/xiangshan/frontend/simfrontend/SimFrontend.scala:233-241` |
