# CBO Zero 演示程序场景分析报告

## 结论

**场景满足要求，判定通过。** 波形和程序输出共同证明：程序先向一个 64 B、64 B 对齐的块写入非零数据，随后执行 `cbo.zero`，StoreQueue 对该 CBO 微操作写入全零数据；紧随其后的读回结果为 `word[0]=0`、`word[7]=0`、`nonzero_words=0`。之后普通 store 能再次写入预期的非零数据，说明零化后的块仍可正常访问。

## 工件与方法

- 程序镜像：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_zero/build/cbo_zero-riscv64-xs.bin`
- ELF：`/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_zero/build/cbo_zero-riscv64-xs.elf`
- 波形：`/home/yanyusong/cbo-kmhv2/XiangShan/build/2026-07-30-11-31-16.fst`（347 MB）
- 香山源码根目录：`/home/yanyusong/cbo-kmhv2/XiangShan`
- 本分析使用 wavekit 的 `FstReader` 解析 FST，以 `TOP.clock` 上升沿采样并按 cycle 查询；辅助使用 `fstminer` 做全层级目标值定位。
- 演示程序将 `demo_block` 设为 64 B 对齐、64 B 大小，目标地址为 `0x800017c0`；源码见 [cbo_zero.c:4](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_zero/cbo_zero.c#L4)、[cbo_zero.c:8](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_zero/cbo_zero.c#L8) 和 [cbo_zero.c:48](/home/yanyusong/cbo-kmhv2/nexus-am/apps/cbo_zero/cbo_zero.c#L48)。

## 目标指令

ELF 反汇编确定目标指令为：

```text
0x800001ac: 0044200f  cbo.zero (s0)
```

其中 `s0` 指向 `demo_block`。波形在提交边界记录到相同 PC 与指令字：`pc=0x00000000800001ac`、`instr=0x0044200f`，故不存在反汇编与波形的 PC/编码不一致。

香山的 CBO 译码表在 [DecodeUnit.scala:476](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L476) 中将该指令送往 Store Unit，并指定 `LSUOpType.cbo_zero`：

```scala
object CBODecode extends DecodeConstants {
  val decodeArray: Array[(BitPat, XSDecodeBase)] = Array(
    CBO_ZERO  -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X,
      FuType.stu, LSUOpType.cbo_zero, SelImm.IMM_S),
```

`cbo_zero` 的编码为 `0b0111`，见 [package.scala:582](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/package.scala#L582)：

```scala
// bit encoding: | cbo_zero 01 | size(2bit) 11 |
def cbo_zero  = "b0111".U
def isCboZero(op:UInt): Bool = op(3,0) === cbo_zero
```

## 周期级证据

以下均为 `TOP.clock` 上升沿的绝对 cycle 与 time（单位 ps）。`fire = valid & ready`。

| 周期 / 时间 | 边界或模块 | 波形证据 | 含义 |
|---|---|---|---|
| 25637 / 51274 | `dispatch.io_fromRename_3` | `pc=0x800001ac`，`valid=1`，`ready=1`，`fire=1`，`fuType=0x10000`，`fuOp=0x7`，`robIdx=26` | CBO Zero 以 Store Unit 微操作成功从重命名级进入分发级；此处无背压。 |
| 25638 / 51276 | `inner_memScheduler.IssueQueueStaMou_1.entries.enqEntries_0.io_commonOut_transEntry` | `valid=1`，`pc=0x800001ac`，`fuType=0x10000`，`fuOp=0x7`，`robIdx=26`，`sqIdx=7` | 微操作进入 store-address 调度队列，并获得 Store Queue 项 7。 |
| 25641 / 51282 | `inner_lsq.io_std_storeDataIn_1` → `storeQueue.dataModule` | `valid=1`，`fuOp=0x7`，`sqIdx=7`，`inData=0x0`，`wen=1`，`waddr=7`，`wdata=0x0`（128 位） | StoreQueue 对 SQ#7 写入全零数据，直接证明 CBO Zero 的数据路径被触发。 |
| 26551 / 53102 | `rob.difftest_commit` | `valid=1`，`pc=0x800001ac`，`instr=0x0044200f`，`robIdx=26`，`isStore=1`，`isLoad=0`，`rfWen=0` | 指令以 store 类微操作退休；无整数寄存器写回，符合 CBO Zero 的语义。 |

从分发 `fire` 到 StoreQueue 全零写入间隔为 **4 cycles**。StoreQueue 写入至退休间隔为 **910 cycles**。本次波形未针对 ROB head、所有更老指令及 cache/LSQ 阻塞原因逐项展开，因此不能把这 910 cycles 归因于 CBO Zero 本身；可确认的是分发接口没有出现 `valid=1 && ready=0` 的目标指令背压。

StoreQueue 的实现与该波形完全一致。见 [StoreQueue.scala:594](/home/yanyusong/cbo-kmhv2/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L594)：

```scala
when (io.storeDataIn(i).fire) {
  dataModule.io.data.waddr(i) := stWbIndex
  dataModule.io.data.wdata(i) := Mux(
    io.storeDataIn(i).bits.uop.fuOpType === LSUOpType.cbo_zero,
    0.U,
    Mux(isVec, io.storeDataIn(i).bits.data,
      genVWdata(io.storeDataIn(i).bits.data,
        io.storeDataIn(i).bits.uop.fuOpType(2,0)))
  )
  dataModule.io.data.wen(i) := true.B
}
```

其中 `fuOp=0x7` 与 `LSUOpType.cbo_zero` 相符，且波形中的 `wdata=0` 正是该 `Mux` 的第一分支结果。

## 端到端功能结果

波形的 `TOP.difftest_uart_out_valid/ch` 在 cycle `4645` 至 `51485` 输出了以下关键文本：

```text
target block: 0x800017c0, bytes: 64
before cbo.zero: word[0]=0x1122334455667700 word[7]=0x1122334455667707 checksum=0x89119a22ab33b81c
after cbo.zero: word[0]=0x0 word[7]=0x0 nonzero_words=0
after post-zero stores: word[0]=0xa500000000000000 word[7]=0xa500000000000007 checksum=0x280000000000001c
CBO Zero demonstration ends
```

这满足三项检查：

1. **前置条件成立**：块首尾字在执行前均为不同的非零种子值。
2. **CBO Zero 生效**：执行后首尾字都为零，8 个 64 位字中非零字数为 0，覆盖完整 64 B 目标块。
3. **后续可写性成立**：执行普通 store 后首尾字恢复为预期的 `0xa5...00` 和 `0xa5...07`，校验和为 `0x280000000000001c`。

## 限制与结论说明

- 仿真使用了 `--no-diff`，所以本报告不把未启用的差分检查结果作为通过依据；通过依据是波形中的 CBO 微操作、StoreQueue 全零写入、ROB 正确退休及 UART 端到端结果。
- 已检查的目标路径未显示分发背压。未对所有 redirect、异常、DCache FSM 和全局 ROB 阻塞信号做穷举，因此不对 910-cycle 退休间隔作超出波形证据的性能归因。
- 就本演示要求而言，CBO Zero 的译码、入队、全零写入、提交和软件可见结果均已闭环，**场景分析通过**。
