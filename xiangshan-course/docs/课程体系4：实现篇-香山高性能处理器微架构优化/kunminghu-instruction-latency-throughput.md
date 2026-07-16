# Kunminghu Instruction Latency Throughput

> 分析对象：OpenXiangShan `kunminghu-v2`，commit `52262f303fc06daf84cdab7011d59b7df65ce7e8`。  
> 本文只统计译码后进入后端执行的指令类别，重点修正 latency 口径：`FuConfig.latency = CertainLatency(0)` 不是“从发射到写回 0 cycle”。

## 1. 统计口径

本文把 latency 拆成四层，避免把不同事件混在一起：

| 口径 | 起点 | 终点 | 用途 |
| --- | --- | --- | --- |
| FU 内部 latency | FU input valid/fire 后进入 FU | FU output valid | 对应 `FuConfig.latency` 和 wrapper 内部 pipeline。ALU/JMP/BRH 可为 0。 |
| issue -> bypass latency | issue queue `s0.fire` | Exu 输出进入 bypass source，可被 `readForward/readBypass` 选择 | 描述依赖指令最早可通过旁路获得结果的时间。 |
| issue -> regCache latency | issue queue `s0.fire` | `RegCache` 写入/更新可供后续 `readRegCache` 使用 | 描述结果写入 regCache 副本的时间。对有 regCache 路径的 producer，通常比 bypass 晚 1 cycle。 |
| issue -> physical RF latency | issue queue `s0.fire` | 物理寄存器堆 `Regfile.mem` 被写入 | 描述结果真正落到 PRF 的时间。通常比 bypass 晚 1 cycle。 |

关键修正：

- ALU wrapper 中 `io.out.valid := io.in.valid`，说明 **FU 内部** 是组合输出；但 DataPath 已经把 issue s0 到 Exu input 放到 s1 读寄存器阶段，因此 **issue 到 bypass 至少 1 cycle**。
- BypassNetwork 对 regCache 写使能做 `GatedValidRegNext`，写数据来自 `bypassDataVec`，因此 **issue 到 regCache 写入通常比 issue 到 bypass 再晚 1 cycle**。
- DataPath 对写回到 PRF 的 `wen` 再做 `RegNext`，因此 **issue 到物理寄存器写入比 issue 到 writeback/bypass 再晚 1 cycle**。
- 依赖指令不需要等 PRF 真写入；它可以通过 bypass network 的 `readForward/readBypass/readBypass2` 提前拿到生产者结果。

## 2. 源码证据链

### 2.1 issue s0 到 Exu input 是一拍

`DataPath.scala` 在 issue 输入侧明确标注 `IQ(s0) --[Ctrl]--> s1Reg`。当 `s0.fire` 且未 flush/cancel 时，`s1_valid := true.B`，同时 `s1_data.fromIssueBundle(s0.bits)` 捕获 issue payload；随后 `s1Reg --[Ctrl]--> exu(s1)` 把 `s1_toExuValid/Data` 接到 `toExu.valid/bits`。

证据：

- `src/main/scala/xiangshan/backend/datapath/DataPath.scala:572-608`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala:669-678`

核心代码：

```scala
// DataPath.scala:572-608
when (s0.fire && !s1_flush && !s0_ldCancel) {
  s1_valid := true.B
}.otherwise {
  s1_valid := false.B
}
when (s0.valid) {
  s1_data.fromIssueBundle(s0.bits)
}
s0.ready := notBlock && !s0_cancel

// DataPath.scala:669-678
toExu(i)(j).valid := s1_toExuValid(i)(j)
s1_toExuReady(i)(j) := toExu(i)(j).ready
sinkData := s1_toExuData(i)(j)
```

### 2.2 ALU FU 内部是 0，但整体不是 0

ALU 的 wrapper 直接把输入 valid 传给输出 valid，并组合产生结果。这只证明 **FU input -> FU output** 是 0 cycle。

证据：

- `src/main/scala/xiangshan/backend/fu/wrapper/Alu.scala:8-23`
- `src/main/scala/xiangshan/backend/fu/FuConfig.scala:310-320`

核心代码：

```scala
// wrapper/Alu.scala:13-14
io.out.valid := io.in.valid
io.in.ready := io.out.ready

// FuConfig.scala:310-320
val AluCfg: FuConfig = FuConfig (
  name = "alu",
  fuType = FuType.alu,
  piped = true,
  writeIntRf = true,
)
```

因此 ALU 的正确结论是：

- FU 内部 latency：0 cycle。
- issue fire -> bypass/forward 可用：1 cycle。
- issue fire -> regCache 写入：2 cycles，前提是该 producer 配置了 `needWriteRegCache`。
- issue fire -> PRF 真写入：2 cycles。

### 2.3 Bypass network 的三类读取路径

`BypassNetwork` 从所有 Exu 输出接收 `valid/pdest/data`，并形成三类数据源：

- `forwardDataVec`：当前周期 Exu output data，用于 `readForward`。
- `bypassDataVec`：`RegEnable` 记录上一周期 Exu output data，用于 `readBypass`。
- `bypass2DataVec`：在部分 VF/mem producer/sink 上再经一层 `RegNext`，用于 `readBypass2`。

证据：

- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala:54-67`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala:96-103`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala:122-127`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala:139-174`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala:195-220`

核心代码：

```scala
// BypassNetwork.scala:61-64
sink.valid := source.valid
sink.bits.pdest := source.bits.pdest
sink.bits.data := source.bits.data(0)

// BypassNetwork.scala:96-102
private val forwardDataVec = VecInit(fromExus.map(x => ZeroExt(x.bits.data, RegDataMaxWidth)))
private val bypassDataVec = VecInit(fromExus.map(x => ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)))

// BypassNetwork.scala:163-168
src := Mux1H(Seq(
  readForward -> Mux1H(..., forwardDataVec),
  readBypass  -> Mux1H(..., bypassDataVec),
  readBypass2 -> Mux1H(..., bypass2DataVec),
  ...
))
```

RegCache 也从 registered bypass 数据更新，不是从 PRF 更新：

```scala
// BypassNetwork.scala:203-218
private val bypassIntWenVec = VecInit(forwardIntWenVec.map(x => GatedValidRegNext(x)))
private val bypassRCDataVec = VecInit(fromExus.zip(bypassDataVec).filter(_._1.bits.params.needWriteRegCache).map(_._2))
io.toDataPath.zipWithIndex.foreach { case (x, i) =>
  x.wen := bypassIntWenVec(i)
  x.data := bypassRCDataVec(i)
}
```

### 2.4 RegCache 写入比 bypass 晚一拍

BypassNetwork 对 regCache 写入路径使用 registered bypass 数据：`bypassDataVec` 是 Exu output data 的 `RegEnable` 副本，`bypassIntWenVec` 是 `forwardIntWenVec` 的 `GatedValidRegNext`。因此 regCache 更新不是 Exu output 当拍完成，而是在 bypass 可见后一拍形成写入。

证据：

- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala:96-103`
- `src/main/scala/xiangshan/backend/datapath/BypassNetwork.scala:203-220`

核心代码：

```scala
// BypassNetwork.scala:96-102
private val bypassDataVec = VecInit(fromExus.map(x => ZeroExt(RegEnable(x.bits.data, x.valid), RegDataMaxWidth)))

// BypassNetwork.scala:203-218
private val bypassIntWenVec = VecInit(forwardIntWenVec.map(x => GatedValidRegNext(x)))
private val bypassRCDataVec = VecInit(fromExus.zip(bypassDataVec).filter(_._1.bits.params.needWriteRegCache).map(_._2))
io.toDataPath.zipWithIndex.foreach { case (x, i) =>
  x.wen := bypassIntWenVec(i)
  x.data := bypassRCDataVec(i)
}
```

### 2.5 PRF 写入比 writeback/bypass 晚一拍

DataPath 从 writeback bundle 接入 PRF 写口时，地址和数据用 `RegEnable` 捕获，写使能 `wen` 用 `RegNext`。Regfile 内部在 `wenOH` 有效时写 `mem(i)`。

证据：

- `src/main/scala/xiangshan/backend/datapath/DataPath.scala:341-343`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala:352-365`
- `src/main/scala/xiangshan/backend/datapath/DataPath.scala:374-376`
- `src/main/scala/xiangshan/backend/regfile/Regfile.scala:112-129`
- `src/main/scala/xiangshan/backend/regfile/Regfile.scala:179-182`

核心代码：

```scala
// DataPath.scala:341-343
intRfWaddr := io.fromIntWb.map(x => RegEnable(x.addr, x.wen)).toSeq
intRfWdata := io.fromIntWb.map(x => RegEnable(x.data, x.wen)).toSeq
intRfWen := RegNext(VecInit(io.fromIntWb.map(_.wen).toSeq))

// Regfile.scala:124-129
val wenOH = VecInit(io.writePorts.map(w => w.wen && w.addr === i.U))
when(wenOH.asUInt.orR) {
  if (i == 0) mem_0 := wData
  else mem(i) := wData
}
```

### 2.6 Vector 算术/部分 vector mem 多一拍 OG2

`Og2ForVector` 把来自 OG1 的 VF arithmetic 和 VecMem 输入再寄存到 `s2_toExuValid/Data` 后送 Exu，因此这类路径在 issue s1 之后还多一个 OG2 stage。

证据：

- `src/main/scala/xiangshan/backend/datapath/Og2ForVector.scala:18-20`
- `src/main/scala/xiangshan/backend/datapath/Og2ForVector.scala:30-51`
- `src/main/scala/xiangshan/backend/datapath/Og2ForVector.scala:53-67`

核心代码：

```scala
// Og2ForVector.scala:30-51
val s2_toExuValid = Reg(...)
val s2_toExuData  = Reg(...)
when(s1_validVec2(i)(j) && s1_readyVec2(i)(j) && !s2_flush && !s1_ldCancel) {
  s2_toExuValid(i)(j) := true.B
  s2_toExuData(i)(j) := s1_dataVec2(i)(j)
}
toExu(i)(j).valid := s2_toExuValid(i)(j)
toExu(i)(j).bits := s2_toExuData(i)(j)
```

## 3. 固定 latency 计算公式

在无 flush、无 replay、无 backpressure、writeback 仲裁当拍获 grant 的最佳情况下：

```text
标量/非 OG2 路径:
  issue -> bypass   = 1(DataPath s1) + FU_internal_latency
  issue -> regCache = issue -> bypass + 1(BypassNetwork regCache GatedValidRegNext, if needWriteRegCache)
  issue -> PRF      = issue -> bypass + 1(DataPath PRF wen RegNext)

Vector arith / need OG2 路径:
  issue -> bypass   = 1(DataPath s1) + 1(OG2) + FU_internal_latency
  issue -> regCache = issue -> bypass + 1(BypassNetwork regCache GatedValidRegNext, if needWriteRegCache)
  issue -> PRF      = issue -> bypass + 1(DataPath PRF wen RegNext)
```

如果 FU 是 `UncertainLatency()`，或者 memory/cache/TLB/LSQ 参与，表中不写固定 cycle，只写变量来源。

## 4. 指令类别 latency / throughput 表

说明：

- `FU 内部` 来自 `FuConfig.latency` 或 wrapper。未显式指定 latency 的 piped FU 采用默认 `CertainLatency(0)`。
- `issue->bypass` 是依赖指令最早可通过 bypass 看到结果的周期数。
- `issue->regCache` 是结果写入 regCache 副本、后续可经 `readRegCache` 使用的周期数；仅对有 regCache 写路径的 producer 有意义。
- `issue->PRF` 是结果真正写入物理寄存器堆的周期数。
- throughput 是独立指令在资源、issue、writeback 无冲突时的峰值；实际会被 issue queue、写回端口、源读端口、ROB/commit、memory 系统等降低。

| 指令类别 | Decode/FU marker | FU 内部 latency | issue -> bypass | issue -> regCache 写入 | issue -> PRF 写入 | 峰值吞吐/II | 主要资源和证据 |
| --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| 整数 ALU | `FuType.alu`, `AluCfg` | 0 | 1 | 2（若配置） | 2 | 最多 4 inst/cycle，II=1 | ALU0-3 四个 ALU：`Parameters.scala:395,399,403,407`；ALU wrapper 组合输出：`wrapper/Alu.scala:13-23` |
| 跳转 JAL/JALR | `FuType.jmp`, `JmpCfg` | 0 | 1 | 2（若配置） | 2 | 最多 3 inst/cycle，II=1 | BJU0-2 支持 Brh/Jmp：`Parameters.scala:396,400,404`；`JmpCfg` 默认 0：`FuConfig.scala:224-235` |
| 条件分支 | `FuType.brh`, `BrhCfg` | 0 | 1 | 通常不写 | 通常无 PRF 写；若无 rd 则不写 | 最多 3 inst/cycle，II=1 | BJU0-2：`Parameters.scala:396,400,404`；`BrhCfg` 默认 0：`FuConfig.scala:237-246` |
| 整数乘法 | `FuType.mul`, `MulCfg` | 2 | 3 | 4（若配置） | 4 | 最多 2 inst/cycle，II=1 若 pipeline 接收不阻塞 | ALU0/1 支持 Mul：`Parameters.scala:395,399`；`MulCfg latency=2`：`FuConfig.scala:322-332` |
| 位操作/BKU | `FuType.bku`, `BkuCfg` | 2 | 3 | 4（若配置） | 4 | 最多 2 inst/cycle，II=1 若 pipeline 接收不阻塞 | ALU0/1 支持 Bku：`Parameters.scala:395,399`；`BkuCfg latency=2`：`FuConfig.scala:360-371` |
| 整数除法 | `FuType.div`, `DivCfg` | variable | variable | variable | variable | BJU3 单实例，非流水，受 divider busy/input buffer 限制 | BJU3 支持 Div：`Parameters.scala:408`；`DivCfg piped=false, UncertainLatency, inputBuffer=4`：`FuConfig.scala:334-345` |
| CSR | `FuType.csr`, `CsrCfg` | variable | variable | variable | variable | BJU3 单实例，通常串行/flushPipe | BJU3：`Parameters.scala:408`；`CsrCfg piped=false, UncertainLatency, flushPipe`：`FuConfig.scala:296-308` |
| Fence | `FuType.fence`, `FenceCfg` | variable | 通常无普通 bypass 结果 | 通常不写 | 通常无 PRF 写 | BJU3 单实例，受 memory/order/flush 限制 | `FenceCfg piped=false, UncertainLatency, flushPipe`：`FuConfig.scala:347-358` |
| I2F | `FuType.i2f`, `I2fCfg` | 2 | 3 | 4（若配置） | 4 | BJU2 单实例，II=1 若 pipeline 不阻塞 | BJU2 支持 I2f：`Parameters.scala:404`；`I2f latency=2`：`FuConfig.scala:248-260` |
| I2V/F2V | `FuType.i2v/f2v` | 0 | 1 | 2（若配置） | 2 | I2V 在 BJU2，F2V 在 FEX0，II=1 | `I2v/F2v latency=0`：`FuConfig.scala:262-294`；资源：`Parameters.scala:404,422` |
| FP ALU | `FuType.falu`, `FaluCfg` | 1 | 2 | 3（若配置） | 3 | 最多 3 inst/cycle，II=1 | FEX0/2/4 支持 Falu：`Parameters.scala:422,426,430`；`Falu latency=1`：`FuConfig.scala:695-709` |
| FP FMA/MAC | `FuType.fmac`, `FmacCfg` | 3 | 4 | 5（若配置） | 5 | 最多 3 inst/cycle，II=1 | FEX0/2/4 支持 Fmac：`Parameters.scala:422,426,430`；`Fmac latency=3`：`FuConfig.scala:711-724` |
| FP convert | `FuType.fcvt`, `FcvtCfg` | 2 | 3 | 4（若配置） | 4 | FEX0 单实例，II=1 | FEX0 支持 Fcvt：`Parameters.scala:422`；`Fcvt latency=2`：`FuConfig.scala:741-755` |
| FP div/sqrt | `FuType.fDivSqrt`, `FdivCfg` | variable | variable | variable | variable | FEX1/FEX3 两实例，非流水/迭代限制 | FEX1/FEX3：`Parameters.scala:423,427`；`Fdiv UncertainLatency`：`FuConfig.scala:726-739` |
| Vector integer ALU | `FuType.vialuF`, `VialuCfg` | 1 | 3 | 4（若配置） | 4 | 最多 2 inst/cycle，II=1，按 uop/128b 结果 | VFEX0/VFEX2 支持 Vialu：`Parameters.scala:444,448`；OG2 加一拍；`Vialu latency=1`：`FuConfig.scala:528-545` |
| Vector integer MAC | `FuType.vimac`, `VimacCfg` | 2 | 4 | 5（若配置） | 5 | VFEX0 单实例，II=1 | `Vimac latency=2`：`FuConfig.scala:547-564`；VFEX0：`Parameters.scala:444` |
| Vector permute/pack | `FuType.vppu/vipu` | 2 | 4 | 5（若配置） | 5 | VPPU VFEX0 单实例；VIPU VFEX1 单实例 | `Vppu/Vipu latency=2`：`FuConfig.scala:583-616`；资源：`Parameters.scala:444-445` |
| Vector FP ALU | `FuType.vfalu`, `VfaluCfg` | 1 | 3 | 4（若配置） | 4 | 最多 2 inst/cycle，II=1 | VFEX1/VFEX3 支持 Vfalu：`Parameters.scala:445,449`；`Vfalu latency=1`：`FuConfig.scala:618-636` |
| Vector FP FMA | `FuType.vfma`, `VfmaCfg` | 3 | 5 | 6（若配置） | 6 | 最多 2 inst/cycle，II=1 | VFEX0/VFEX2 支持 Vfma：`Parameters.scala:444,448`；`Vfma latency=3`：`FuConfig.scala:638-655` |
| Vector FP convert | `FuType.vfcvt`, `VfcvtCfg` | 2 | 4 | 5（若配置） | 5 | VFEX1 单实例，II=1 | `Vfcvt latency=2`：`FuConfig.scala:676-693`；VFEX1：`Parameters.scala:445` |
| Vector div | `FuType.vidiv/vfdiv` | variable | variable | variable | variable | VFEX4 单 issue block，非流水/迭代限制 | VFEX4：`Parameters.scala:452`；`Vidiv/Vfdiv UncertainLatency`：`FuConfig.scala:566-581,657-674` |
| Load | `FuType.ldu`, `LduCfg` | variable | variable；L1 hit/TLB hit 最佳路径仍需 mem/cache 进一步展开 | variable | variable | 3 load pipelines；受 TLB、cache bank、LSQ、replay、MSHR 限制 | LDU0-2：`Parameters.scala:474-480`；memory FU 属 `UncertainLatency` |
| Store address | `FuType.sta`, `StaCfg` | variable | 通常无普通 rd bypass | 通常不写 | 通常无 PRF 写 | 2 STA pipelines；受 SQ/地址检查/order 限制 | STA0/1：`Parameters.scala:468-472` |
| Store data | `FuType.std`, `StdCfg` | 0/数据通路类 | 无 PRF 写 | 通常不写 | 无 PRF 写 | 2 STD pipelines；受 SQ/merge/drain 限制 | STD0/1：`Parameters.scala:489-492` |
| AMO/LR/SC | `FuType.mou/moud` | variable | variable | variable | variable | STA/STD 相关资源，受 cache/coherence/order 限制 | STA 支持 Mou，STD 支持 Moud：`Parameters.scala:468,471,489,492` |
| Vector load/store/segment | `FuType.vldu/vstu/vsegldu/vsegstu` | variable | variable | variable | variable | VLSU0/1 两条 vector mem pipeline，segment 资源更窄 | VLSU0/1：`Parameters.scala:483-486`；vector mem `UncertainLatency`：`FuConfig.scala:757-837` |

## 5. 资源吞吐汇总

| 资源 | 数量/宽度 | 适用指令 | 峰值吞吐 | 退化场景 | 证据 |
| --- | ---: | --- | --- | --- | --- |
| Decode/Rename | DecodeWidth=6, RenameWidth=6 | 所有指令 | 前端供应充足时最多 6 inst/cycle 进入后端前段 | decode 分支切分、rename 资源、redirect、IBuffer 空/满 | `Parameters.scala:149-150` |
| Commit | CommitWidth=8, RobCommitWidth=8 | 所有提交 | 最多 8 inst/cycle | older instruction 未完成、异常/redirect、ROB 头阻塞 | `Parameters.scala:151-152` |
| Int ALU | 4 | ALU | 最多 4/cycle | issue block 冲突、源读端口、IntWB 端口、依赖未醒 | `Parameters.scala:395,399,403,407` |
| BJU | 3 + BJU3 特殊 | branch/jump/I2F/I2V/CSR/Fence/Div | branch/jump 最多 3/cycle；CSR/Div/Fence 单 BJU3 | redirect、CSR/Fence 串行、Div busy | `Parameters.scala:396,400,404,408` |
| MUL/BKU | 2 | mul/bku | 最多 2/cycle | ALU0/1 结构冲突、IntWB 端口、pipeline backpressure | `Parameters.scala:395,399` |
| FP Falu/Fmac | Falu/Fmac 各最多 3 | scalar FP | 最多 3/cycle | FP issue block、FpWB/IntWB 端口冲突、FPU pipeline backpressure | `Parameters.scala:422,426,430` |
| FP Div | 2 | fdiv/fsqrt | 非流水变量吞吐 | divider busy、input/output backpressure | `Parameters.scala:423,427` |
| VF arithmetic | 5 VFEX，其中 VFEX4 为 div | vector arithmetic | 按 FU 类型 1-2/cycle 常见 | OG2、VF/V0/VL 读写口、vector uop、writeback 端口 | `Parameters.scala:443-453` |
| Load pipeline | 3 | scalar load | 最多 3/cycle 发射到 load pipe | TLB miss、DCache miss/bank conflict、load replay、MSHR 满 | `Parameters.scala:474-480` |
| Store addr/data | STA 2 + STD 2 | scalar store/AMO | 地址 2/cycle，数据 2/cycle | SQ 满、store-load violation、cache drain/order 限制 | `Parameters.scala:468-492` |
| Vector mem | VLSU 2 | vector load/store | 最多 2/cycle 发射到 VLSU，写回另受 `VecMemInstWbWidth=1` | VLSQ、segment split、TLB/cache、replay、merge buffer | `Parameters.scala:216-233,483-486` |

## 6. ALU timing 示例

| Cycle | 事件 | 说明 | 证据 |
| --- | --- | --- | --- |
| N | issue queue s0 `fire` | DataPath 捕获 issue payload，准备进入 s1 read-reg/exu 输入阶段 | `DataPath.scala:598-607` |
| N+1 | `toExu.valid`，ALU wrapper `io.out.valid := io.in.valid` | ALU 结果当拍进入 Exu output/bypass source；依赖指令可通过 `readForward` 选择 `forwardDataVec` | `DataPath.scala:669-678`; `wrapper/Alu.scala:13-23`; `BypassNetwork.scala:96-103,163-168` |
| N+1 | WbDataPath/Exu output 可形成写回 bundle | 若写回仲裁当拍 grant，`fromIntWb.wen` 可到 DataPath | `BypassNetwork.scala:54-67`; `DataPath.scala:341-343` |
| N+2 | regCache 写入/更新 | BypassNetwork 使用 `GatedValidRegNext` 形成 regCache 写使能，数据来自 `bypassDataVec`；若 producer `needWriteRegCache`，后续可经 `readRegCache` 使用 | `BypassNetwork.scala:203-218` |
| N+2 | PRF `wen` 生效并写 `Regfile.mem` | DataPath 对 `wen` 做 `RegNext`，Regfile 内部按写口更新 mem | `DataPath.scala:341-343`; `Regfile.scala:124-129` |

结论：ALU “0 cycle”只成立于 **FU input 到 FU output**，不成立于 “issue 到执行单元再到物理寄存器写回”。对本统计口径，ALU 是：

```text
issue -> bypass = 1 cycle
issue -> regCache write = 2 cycles, if needWriteRegCache
issue -> physical RF write = 2 cycles
```

## 7. Bypass network 对调度 latency 的含义

依赖指令的真实可执行时间通常由 wakeup/select 与 bypass 一起决定，而不是由 PRF 写入决定。

- 当生产者结果在当前周期从 Exu 输出，消费者若被标记 `readForward`，可从 `forwardDataVec` 直接取数。
- 当消费者晚一拍进入 Exu，若被标记 `readBypass`，可从 `bypassDataVec` 取上一拍寄存的生产者结果。
- 对 VF/mem 的部分 producer/sink，`readBypass2` 提供第二级寄存旁路，匹配更长的数据返回和端口时序。
- RegCache 保存的是 bypass 后的数据副本，用来缓解后续整数源读压力；它不是 PRF 本体。分析 latency 时要把 `issue -> regCache 写入` 单独列出，不能只写 bypass 或 PRF。

所以，调度器/scoreboard 使用的 producer latency 应接近 “issue -> bypass/wakeup 可见” 口径；架构状态和寄存器堆一致性分析使用 “issue -> PRF 写入” 口径。

## 8. 可变 latency 分类

以下类别无法仅用 `FuConfig.latency` 给出固定 cycle：

| 类别 | 变量来源 |
| --- | --- |
| Div/FDiv/VDiv | 迭代次数、输入 buffer、非流水 busy、输出 backpressure |
| CSR/Fence | 特权检查、CSR 读写副作用、flushPipe、序列化、异常/redirect |
| Load/Vector Load | DTLB/PTW、DCache hit/miss、MSHR、bank conflict、load replay、uncache/MMIO、异常 |
| Store/Vector Store | SQ/VLSQ、地址/数据分离、commit 授权、store buffer、cache drain、memory ordering |
| AMO/LR/SC | 原子序列、cache/coherence、重试/replay、order 限制 |
| Vector segment/memory | uop 拆分、merge buffer、元素/segment 数、VLSQ、cache/TLB/replay |

## 9. 使用建议

如果后续要把每一条 RISC-V 指令展开为逐指令表，可以按 decode 表把指令先归入本文的 `FuType/FuConfig` 类别，再应用上面的公式：

```text
fixed non-OG2: issue_to_bypass = 1 + fu_latency
fixed OG2:     issue_to_bypass = 2 + fu_latency
RegCache write: issue_to_regcache = issue_to_bypass + 1, if needWriteRegCache
PRF write:      issue_to_prf = issue_to_bypass + 1
variable:      必须继续展开具体 FU/mem/cache/CSR/divider 状态机
```

这能避免再次把 ALU/JMP/BRH 的 FU 内部 0-cycle 误写成 “指令发射到写回 0-cycle”。
