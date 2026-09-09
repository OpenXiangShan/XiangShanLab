# 香山 kunminghu-v3 Issue #6149 分析：`sbpctl.RAS_ENABLE=0` 后 RAS/µRAS 仍驱动 secret 相关的 wrong-path fetch

## 0. 摘要

`sbpctl`（自定义 CSR，地址 `0x5c0`）的 `RAS_ENABLE`（bit 6）在 kunminghu-v3 上的文档语义是 "Enable the return-address stack predictor"。软件把这一位清零后，合理预期是：**RAS 不再决定下一个 fetch PC**。Issue #6149 报告并用波形证明：这个预期不成立。

**What happened.** V3 前端有两条 return target 预测路径：S1 的 MicroRAS（µRAS，给最早一级预测提供快速返回目标）和 S3 的主 RAS（基于较晚、较可信的 CFI 信息维护的 speculative 栈）。在基线 RTL 里，`RAS_ENABLE` 这一位只存在于 control plane，没有贯穿到 state 更新和 next-PC 数据通路，以下三处同时缺失防护：

1. **Wiring**：µRAS 的 enable 被硬连为 `true.B`（`Bpu.scala:96`）；主 RAS 接了 `ctrl.rasEnable`（`Bpu.scala:113`），但模块内部从不使用它。
2. **State 更新**：主 RAS 的三类状态变化——S3 speculative push/pop（`Ras.scala:76`）、redirect 恢复（`Ras.scala:100`）、commit 训练（`Ras.scala:107-110`）——都不检查 `io.enable`；`MicroRas.scala` 里则完全没有 `io.enable`。禁用期间栈内容照常被 call/return 修改。
3. **最终 consumer（next-PC mux）**：S1 的 uBTB/aBTB target 选择只判断 `isReturn && uras.specOut.isCanUse`（`Bpu.scala:298`、`Bpu.scala:332`、`Bpu.scala:518-525`），S3 的选择只判断 `s3_firstTakenBranch.bits.attribute.isReturn`（`Bpu.scala:404` 的 `s3_useRas`，随后 `Bpu.scala:422` 用 `ras.io.topRetAddr` 覆盖 target）。两处都不检查 enable，被禁用预测器的输出可以直接成为 next-PC。

**实际影响。** 这不是架构层面的功能错误——后端执行 `ret` 时仍按 `ra` 计算正确目标并 redirect，程序最终结果正确。真正的危害是**微架构隔离失效**：issue 作者的 PoC（本地已在 V3 基线 `e85a929a3` 上复现，见 §1.5）证明，`RAS_ENABLE=0` 期间：

- 内存中的 secret bit 选择两个不同的 call site，把 secret 相关的返回地址留在"已被禁用"的 RAS/µRAS 状态里；
- 随后一条架构上应返回 `safe_return` 的 `ret`，被禁用的预测器将其预测为 secret 选定的 gadget 地址，且 FTQ/IFU **确实对这个 wrong path 发出了 fetch**（secret=0 经 S1 µRAS 预测并 fetch `gadget0`，secret=1 经 S3 主 RAS 预测并 fetch `gadget1`）；
- 两次运行唯一不同的输入是 secret bit，fetch 流却分叉到不同地址——secret 相关的 wrong-path fetch 是 Spectre 类侧信道的前提条件（gadget 内含 probe load，可进一步在 cache 中留下痕迹；本报告只证实到 "secret 相关 fetch" 这一层，cache 副效应的量化见波形分析章节）。

**触发条件。** (a) `sbpctl.RAS_ENABLE=0`，其余预测器（uBTB/aBTB/mBTB/TAGE/SC/ITTAGE）保持使能——return 的识别依赖 BTB 元数据；(b) 存在由 secret 选择、且**不正常返回**的 call（污染 RAS 栈）；(c) 之后执行一条真正的 `ret`；(d) V3 微架构（µRAS 是 V3 新增结构；V2 的对照结论见下）。

**修复与现状。** PR #6461（`fix(Bpu): enable & debug-related fix & cleanup`，标记 `Fixes #6149`，截至 2026-09-07 仍是 open/draft）对 S3 主 RAS 路径的修复完整而直接：三类 state 更新全部加上 `io.enable` + `s3_useRas` 增加 `ras.io.enable` 的 consumer 侧检查 + 依赖关系重构（`ras/uras.enable := rasEnable && mbtbEnable`）。但 S1 µRAS 路径疑似仍有残留：`MicroRas.scala` 依旧不使用 `io.enable`，S1 的两个 target mux 依旧不检查 enable——`uras.io.enable` 的接线更像是仅仅把信号连到了输入端口，模块内部和 consumer 都没有真正使用它。这个判断来自静态分析（与 gpt 分析一致），待在应用补丁后的构建上复跑 PoC 验证（§2）。

**V2 对照结果（动态实验已完成，§3）。** 同一个 bug 类**在 kunminghu-v2 上同样成立**（双 secret 实验 `secret_dependent_wrong_path_fetch=true`），但经由的是 V2 特有的第三条路径：V2 的 S2/S3 `jalr_target` 覆盖确实有 `ras_enable` gate（静态分析这部分正确），但 RAS 栈顶还通过 `last_stage_spec_info.topAddr`（无 gate）被存入 FTQ 的 `ftq_redirect_mem`，而 IFU predecode 写回发现 RET 时，FTQ 直接用这个栈顶替换 redirect target（`NewFtq.scala:1143-1145`，无 `ras_enable` 检查）——禁用期间压入的 secret 相关返回地址由此再次驱动 fetch。禁用期间 speculative 栈照常更新（state 层缺陷与 V3 相同）。这印证了 §1.6 框架的核心论点：**有状态预测器的输出不止一个 consumer，最终 consumer 的 gate 必须覆盖每一个 next-PC 来源**。

一句话总结：一个"禁用某微架构预测器"的 CSR 位，只改写了 control plane 的寄存器值，没有传达到预测器的 state 更新逻辑和 next-PC 数据通路；被禁用的 RAS/µRAS 因此继续积累 secret 相关状态并继续驱动 fetch。#6461 的修复思路正是把 control plane、state 更新、consumer 三者重新对齐。

## 1. Bug 分析

### 1.1 微架构背景：V3 的两级 return 预测

V3 BPU 是多级覆盖预测结构：S1 有快速预测器（uBTB/aBTB/uTAGE），S3 有更复杂的预测器（mBTB/TAGE/SC/ITTAGE/RAS），S3 可以 override S1。取指链路：

```text
PC -> BPU(S1 快 / S3 准) -> FTQ -> IFU -> ICache
```

return target 的预测有两条路径，这是理解本 bug 的关键结构：

```text
快路径（S1）                          慢/准路径（S3）
uBTB / aBTB 发现 RET                  mBTB + TAGE/... 确认 RET
      |                                     |
      v                                     v
   MicroRAS (uras)                        主 RAS (ras)
   跟踪 S1-S3 流水中尚未落实到             S3 按最终 prediction 做
   主 RAS 的 call/ret，forwarding          speculative push/pop
   推导"当前有效栈顶"                          |
      |                                       v
      v                               晚期 return target
   早期 return target                       |
      |                                     v
      +------------ S3 override ------------+
                          |
                          v
                    FTQ -> IFU fetch
```

- **主 RAS**（`ras/Ras.scala`）：speculative 输入直接来自 S3 最终预测——`Bpu.scala:241`：`ras.io.specIn.valid := s3_fire`。即 S3 认为预测块里有 call/return 时，speculative 栈立刻 push/pop。
- **µRAS**（`ras/MicroRas.scala`）：模块头注释自述为 "speculative micro Return Address Stack"，跟踪 S1–S3 流水里的 pending call/return，为更早的 RET 推导有效栈顶。它的输入是 S1 的预测属性（`Bpu.scala:229-231`）、redirect（`:232`）、S3 override（`:233-236`），以及主 RAS 的栈顶 `fullRetAddr := ras.io.topRetAddr`（`:237`）。可以把它理解成 RAS 的 **forwarding/影子状态**：主 RAS 要到 S3 才更新，而 S1 现在就需要返回目标，µRAS 负责提前算出"等流水线里这些 call/ret 生效后栈顶应该是什么"。

这个结构决定了：disable 语义必须同时约束**两个**预测器、**两类**状态（speculative 栈和影子跟踪），以及**两个** consumer（S1 mux 和 S3 mux）。

### 1.2 `sbpctl` 与 `RAS_ENABLE` 的传播路径

`sbpctl` 定义在 `NewCSR/CSRCustom.scala:70-78`（注意：`backend/fu/CSR.scala` 里还有一份旧的 snake_case 实现，未生效，读代码时不要混淆）：

```scala
class SbpctlBundle extends CSRBundle {
  val RAS_ENABLE    = RW(6).withReset(true.B).withDescription("Enable the return-address stack predictor.")
  val ITTAGE_ENABLE = RW(5).withReset(true.B)
  val SC_ENABLE     = RW(4).withReset(true.B)
  val TAGE_ENABLE   = RW(3).withReset(true.B)
  val MBTB_ENABLE   = RW(2).withReset(true.B)
  val ABTB_ENABLE   = RW(1).withReset(true.B)
  val UBTB_ENABLE   = RW(0).withReset(true.B)
}
```

复位值全 1（`0x7f`）。传播链：`NewCSR.scala:1443-1449` 把各位映射进 `bp_ctrl.{ubtb,abtb,mbtb,tage,sc,ittage,ras}Enable`，`Frontend.scala:153` 整体接入 `bpu.io.ctrl := csrCtrl.bp_ctrl`。写 `0x3f` 即保留 bit0-5、清 bit6，实现"仅关闭 RAS"。

### 1.3 缺失的 gate：control plane / state 更新 / consumer

#### 1.3.1 Wiring：µRAS 使能被硬连为常量

`Bpu.scala:94-96`（基线）：

```scala
fallThrough.io.enable := true.B // fallThrough is always enabled
utage.io.enable       := true.B
uras.io.enable        := true.B
```

即使软件清零 `RAS_ENABLE`，µRAS 的 enable 输入恒为 1。主 RAS 则不同，enable 信号已实际接线：

```scala
ras.io.enable := Mux(constCtrl(0), constCtrl(7), ctrl.rasEnable)   // Bpu.scala:104（常量覆盖模式）
ras.io.enable := ctrl.rasEnable                                    // Bpu.scala:113（普通模式）
```

但如下一小节所示，`Ras.scala` 内部没有任何逻辑使用 `io.enable`——**接了线不等于实现了 enable 语义**，这正是原 issue 指出的问题。

#### 1.3.2 State 更新：三类状态变化均不检查 enable

主 RAS 的全部状态变化（`ras/Ras.scala`）：

```scala
// speculative 更新：S3 预测出 call/ret 即 push/pop，无 enable
private val specPush = io.specIn.valid && io.specIn.bits.attribute.isCall   // Ras.scala:66
private val specPop  = io.specIn.valid && io.specIn.bits.attribute.isReturn // Ras.scala:67
stack.spec.fire     := io.specIn.valid                                     // Ras.scala:76

// redirect 恢复：预测错误时恢复栈指针，无 enable
stack.redirect.valid  := redirect.valid && (isBefore(redirectTOSW, stackTOSW) || !stackNearOverflow)  // Ras.scala:100

// commit 训练：指令提交后修正 committed 栈，无 enable
private val commitValid = RegNext(io.commit.valid, init = false.B)          // Ras.scala:107
private val commitInfo  = RegEnable(io.commit.bits, io.commit.valid)        // Ras.scala:108
stack.commit.valid     := commitValid                                       // Ras.scala:110
```

µRAS（`ras/MicroRas.scala`）全文没有任何 `io.enable` 引用。它持续根据 `s1_fire/s2_fire/s3_fire`、`hasRedirect`、`hasOverride` 维护 `s2_hasPush/s2_hasPop/s3_hasPush/s3_hasPop`、`retAddr`、`isCanUse` 等 speculative 跟踪状态。

**后果**：`RAS_ENABLE=0` 期间，call/return 照常改写主 RAS speculative 栈与 µRAS 影子状态，secret 相关的返回地址可以在"禁用"状态下自由积累。

#### 1.3.3 最终 consumer：S1/S3 的 target mux 都不检查 enable

**S1 路径**（uBTB 与 aBTB 两处，结构相同）：

```scala
// Bpu.scala:296-300（uBTB target 选择）
Mux(
  ubtb.io.prediction.bits.attribute.isReturn && uras.io.specOut.isCanUse,
  uras.io.specOut.retTarget,   // µRAS 目标直接胜出
  <ubtb 自身目标>
)

// Bpu.scala:330-334（aBTB target 选择，同构）
Mux(
  s1_abtbFirstTakenBr.attribute.isReturn && uras.io.specOut.isCanUse,
  uras.io.specOut.retTarget,
  ...
)
```

另有 S1 预测属性修正处 `Bpu.scala:518-525`，同样只检查 `isReturn && isCanUse` 就用 `uras.io.specOut.retTarget` 覆盖 target。

**S3 路径**：

```scala
private val s3_useRas = s3_firstTakenBranch.bits.attribute.isReturn          // Bpu.scala:404，无 enable
...
s3_prediction.target :=
  MuxCase(
    s3_fallThroughPrediction.target,
    Seq(
      (s3_taken && s3_useRas)    -> ras.io.topRetAddr,   // Bpu.scala:422，主 RAS 栈顶直接胜出
      (s3_taken && s3_useIttage) -> ittage.io.prediction.target,
      s3_taken                   -> s3_firstTakenBranch.bits.target
    )
  )
```

**后果**：即使栈里是"禁用"期间积累的陈旧/secret 相关内容，只要 BTB 把当前 `jalr` 标注为 return 且（S1 路）`isCanUse` 有效，RAS/µRAS 的输出就直接成为预测 target，随后经 FTQ 驱动 IFU fetch。**最终 next-PC mux 是控制流安全的最后一道保险，这里恰好没有设防。**

值得对比：TAGE/ITTAGE 一类表结构有内建的 `hit/valid` 输出，可以（也应该）在模块内做 gate；RAS 是栈结构，没有天然的 "hit" 概念，#6461 的修法就是在 consumer 侧用 `ras.io.enable` 充当这个 valid（见 §2）。

### 1.4 PoC 设计与攻击链

V3 使用的 PoC 是 issue 作者附件 `xiangshan-ras-enable-secret-fetch-poc.zip` 中的 `ras_enable_secret_fetch.S`（编译期参数 `SECRET_BIT` 0/1 生成两个变体），地址布局（objdump 实测，链接基址 `0x80000000`）：

| 符号 | 地址 | 作用 |
| --- | ---: | --- |
| `_start` | `0x80000000` | 把 secret bit 写到 `0x80003000` |
| `disable_ras` | `0x80000020` | `csrw 0x5c0, 0x7f`（全开，训练阶段标记） |
| `attack_disable_ras` | `0x8000002c` | `csrw 0x5c0, 0x3f`（**只清 bit6，RAS 禁用**） |
| `train_ret_loop` | `0x8000003c` | 128 次迭代：`la ra, train_return; j ret_trigger` |
| `train_return` | `0x80000048` | 计数回跳 |
| `delay_loop` | `0x80000060` | 768 次迭代，排空流水线 |
| `after_secret_load` | `0x80000068` | `ld t1, 0(s4); andi t1,1; beqz t1, poison0_site` |
| `poison1_site` | `0x80000080` | `jal ra, poison_common`（secret=1 走这条） |
| `gadget1` | `0x80000084` | `lui t3; ld t4, 0(t3)`（probe load，PROBE1） |
| `wrong_path1` | `0x80000094` | 死循环 |
| `poison0_site` | `0x800000c0` | `jal ra, poison_common`（secret=0 走这条） |
| `gadget0` | `0x800000c4` | probe load（PROBE0） |
| `poison_common` | `0x80000100` | `j ret_probe`（**不从 call 正常返回**） |
| `ret_probe` | `0x80000140` | `la ra, safe_return`（架构返回目标是 safe_return） |
| `ret_trigger` | `0x80000148` | `ret`（被攻击的 return） |
| `safe_return` | `0x8000014c` | 恢复 `0x3f` 后进入 `done` 死循环 |

攻击链与各阶段的必要性：

```text
secret bit（内存值）
   |
   | beqz 分支（secret 相关）
   v
poison0_site 或 poison1_site 的 jal ra      <-- 不同的 call site
   |                                           压入各自返回地址 0x800000c4 / 0x80000084
   |                                           （RAS_ENABLE=0，但 gate 缺失，照常 push）
   v
poison_common: j ret_probe                  <-- 故意不 ret，返回地址滞留在"禁用"的栈里
   |
   v
ret_probe: la ra, safe_return               <-- 架构上 ra 指向 safe_return
ret_trigger: ret                            <-- 架构 target = safe_return
   |
   | 预测却来自被禁用的 RAS/µRAS 栈顶 = secret 选定的 gadget 地址
   v
FTQ -> IFU 对 gadgetN 发出真实 fetch         <-- secret 相关的 wrong-path fetch
   |
   v
后端执行 ret 发现误预测 -> redirect 回 safe_return（架构结果正确）
（gadgetN 中的 probe load 若已speculative执行，则留下 cache 痕迹 = 侧信道载荷）
```

设计要点：

- **训练循环在 RAS 已禁用后进行**：`jalr` 的 return 属性来自 BTB 元数据，首次执行的 `jalr` 未必被及时标注为 return，前端也就不会查询 RAS/µRAS。先用 128 次迭代把 `ret_trigger` 的元数据训练到 BTB 中，保证攻击阶段该 `jalr` 被稳定识别为 return。
- **用 `j` 而不是 `ret` 离开训练**：`j` 是 `jal x0`，rd=x0 不算 call，不额外污染栈。
- **poison call 不返回**：模拟被打断/被切换的调用帧。若正常返回，push 与 pop 配对，栈顶不会残留 secret 相关地址。
- **`.balign 64`**：把 poison/gadget/ret 隔离到不同 fetch block，避免同块内 predecode 修正干扰预测路径。
- **delay 循环**：拉开攻击 `ret` 与 poison call 的距离，让被污染的栈顶稳定留存。
- **gadget 内是 probe load + 死循环**：wrong path 一旦被 fetch 并执行，`ld t4, 0(t3)` 访问 secret 选定的 PROBE 地址，是向 cache 副效应转化的载荷；死循环保证现象可被周期上限稳定截获。

### 1.5 V3 基线复现（`e85a929a3`）

复现方法：Verilator emu（`--no-diff --dump-wave-full`），两个 secret 变体各运行一次，`monitor_ras_enable_secret_fetch.py`（作者附件自带）从波形中提取"RAS 禁用态下的 RAS/µRAS 驱动预测 + FTQ fetch"事件。monitor 的判定字段：`both_runs_ras_disabled`、`prediction_targets_differ`、`secret_dependent_wrong_path_fetch` 均要求 `csr_ras_enable=0` 采样成立，非 disable 窗口内的正常预测不计入统计。

**结果一：issue 作者环境**（`xiangshan-ras-enable-secret-fetch-poc.zip`）：

| 指标 | secret=0 | secret=1 |
| --- | --- | --- |
| 首个禁用态预测事件类型 | `s1_disabled_uras_prediction`（S1 µRAS） | `s3_disabled_ras_prediction`（S3 主 RAS） |
| 预测 target | `0x40000062` = `gadget0`（`0x800000c4`） | `0x40000042` = `gadget1`（`0x80000084`） |
| 事件时刻（FST time / cycle） | 23800 / C11900 | 16956 / C8478 |
| FTQ 随后 fetch 该目标 | 是（ftq_idx=10，t=23802） | 是（ftq_idx=11，t=16958） |

**结果二：本报告独立复现**（`v3`，同一 emu `e85a929a3`、同一参数；PoC 为作者原版**加一处 mtvec 处理器**，见下）：

| 指标 | secret=0 | secret=1 |
| --- | --- | --- |
| 首个泄露事件 | `s3_disabled_ras_prediction` → `gadget0`（t=15404 / C7702） | `s3_disabled_ras_prediction` → `gadget1`（t=13460 / C6730） |
| FTQ→IFU 随后 fetch | 是（ftq_idx=10，t=15406，目标 `0x40000062`） | 是（ftq_idx=9，t=13476，目标 `0x40000042`） |
| 窗口内泄露事件全量 | S3→`gadget1`×8、S3→`gadget0`×4 | S3→`gadget1`×12、**S1 µRAS→`gadget1`×2** |
| 程序结果 | 完整运行并进入 `done`（C11000 时 5557 条提交） | 完整运行并进入 `done`（5567 条提交） |

综合两份结果的结论：**两个 secret 值下唯一不同的输入是内存中的 secret bit，而 RAS 禁用态下的预测 target 与 FTQ 实际 fetch 地址随之分叉**（`secret_dependent_wrong_path_fetch=true`）——泄露链路成立，且 S1 µRAS 与 S3 主 RAS 两条预测路径都实际命中（作者运行中 secret0 首次暴露于 S1 路径，secret1 首次暴露于 S3 路径；本报告复现中 secret1 同时出现两类事件；哪条路径先转化为实际 fetch 受调度时序影响，与 bug 本身无关）。

### 1.6 Bug 类别归纳：predictor disable 的四层 invariant

本 bug 与 #6159（uBTB/aBTB enable 开关同类问题，维护者把 #6149 回复为 "Same BP switch problem as #6159"）同属一个 bug 类：**"disable 某微架构预测器"的 CSR 位没有完整贯穿预测器子系统**。审查这类问题时，有效的检查框架是沿数据流核对四层 invariant（本 case 的缺失项加粗）：

| level | invariant | #6149 基线状态 |
| --- | --- | --- |
| 1. 请求/活动 gate | disable 后是否仍有 SRAM 读、lookup、流水 fire | BTB 等仍使能（本 PoC 的前提而非缺陷） |
| 2. state 更新 gate | speculative 更新、训练、redirect 恢复、commit 更新是否全部停止 | **缺失**（`Ras.scala` 三处 + `MicroRas.scala` 全部） |
| 3. 输出有效 gate | `hit/valid/isCanUse` 是否在 disable 后强制失效 | **缺失**（µRAS `isCanUse` 不受 enable 影响；RAS 无 hit 概念也未设等价物） |
| 4. 最终 consumer gate | next-PC mux 是否绝对禁止其成为控制流来源 | **缺失**（S1 两处 mux、S3 `s3_useRas`） |

第 4 层是最后一道硬保险：生产者内部状态出错尚可容忍，最终 next-PC mux 不能出错。#6461 对 S3 主 RAS 正是"第 2 层 + 第 4 层"的双重防御（defence-in-depth），而 µRAS 疑似只做了接线、没做 2/3/4 任何一层——见下节。

另一个有普遍教训的架构事实：**预测器不是彼此独立的**。#6461 重构后的依赖关系是 `utage ← abtbEnable`、`tage/sc/ittage/ras/uras ← rasEnable && mbtbEnable` 等——uTAGE 是 aBTB 的纠错伴随结构，TAGE/SC/ITTAGE 依赖 mBTB 提供分支候选与元数据，µRAS 依赖主 RAS 栈顶。CSR 看起来是"一位对一个预测器"，真正的开关语义必须沿这个 producer/corrector 依赖图传播。把模块树当成可独立开关的预测器清单，正是这类 bug 反复出现的根源。

## 2. #6461 修复评估（已在 PR head `dcdf1c1c9` 上实验验证）

### 2.1 修复内容（对照基线逐条核实）

1. **主 RAS 状态三处 gate**（`Ras.scala`）：`stack.spec.fire := io.specIn.valid && io.enable`；redirect 恢复附加 `io.enable`；commit 路径改为 `RegNext(io.commit.valid && io.enable)` / `RegEnable(io.commit.bits, io.commit.valid && io.enable)`。
2. **S3 consumer gate**（`Bpu.scala:388`）：`s3_useRas = attribute.isReturn && ras.io.enable`，S3 target mux 因此不再选择 `ras.io.topRetAddr`。
3. **enable 接线与依赖重构**（`Bpu.scala:101/108`）：`ras.io.enable := ctrl.rasEnable && ctrl.mbtbEnable`、`uras.io.enable := ctrl.rasEnable && ctrl.mbtbEnable`，并给 utage/tage/sc/ittage 增加依赖 gate，给各子预测器 `prediction.valid` 补 enable gate。
4. **既有 assertion**（`frontend/Bundles.scala`，基线 `e85a929a3` 已存在，由更早的 #5639 引入）：分支解析为 return 时检查 prediction source 不得为 mBTB（`XSError(en && retError, "prediction source cannot be mbtb when resolved branch type is return")`）。其隐含不变量是"resolved return 的 S3 target 一定取自 RAS"。

静态疑点（实验前）：`MicroRas.scala` 全文 0 处 `io.enable`，S1 的两个 target mux（`Bpu.scala:279/313`）仍只查 `isReturn && uras.io.specOut.isCanUse`——µRAS 的 enable 只是接线，模块和 consumer 都未消费。

### 2.2 实验中的新发现：gate 修复使既有 assertion 的隐含不变量失效（非硬件功能错误）

在 PR head 原样构建的 emu 上运行本 PoC，程序在训练阶段第一条 `ret` 处（instrCnt=28，C8473）即 abort：

```text
[ERROR][time=8477] SimTop...frontend.inner.ftq:
  prediction source cannot be mbtb when resolved branch type is return
```

因果链需要准确表述（经基线源码核实，这条 assertion 并非 #6461 新增，基线 `Bundles.scala:411/440` 已有）：

1. assertion 编码的不变量："resolved return 的 S3 source 必为 RAS，不得为 mBTB"；
2. 基线上该不变量"成立"恰恰依赖 #6149 的 bug——`s3_useRas` 不检查 enable，return 的 target 恒由 RAS 提供（这正是泄露路径），因此 source 恒为 s3Ras，assertion 永不触发（本报告的基线复跑全程未触发，与此一致），**bug 掩盖了这条过时的验证断言**；
3. #6461 修复 gate 后（`s3_useRas = isReturn && ras.io.enable`），RAS 禁用时 return 的 target 只能来自 mBTB，source=s3Mbtb 成为正常状态，旧不变量被暴露——**任何"关闭 RAS 后执行 ret"的程序都会触发这条 fatal assertion**，修复 #6149 的 patch 无法运行 #6149 自身的复现场景。

需要强调：这是**验证断言与修复后行为不一致**的问题，不是新的硬件功能错误——下方对照③证明禁用该 assertion 后功能行为与 RAS 开启时完全一致。它仍应随 PR 处理（断言需对 `!ras_enable` 豁免或更新不变量），否则 RAS 禁用模式在仿真中不可用。

用最小 PoC（`v3-patched/poc/ras_min_ret_assert.S`：设置 mtvec、一次 `csrw sbpctl`、两条 `la ra; ret`、park 循环；编译期开关 `RAS_OFF` 选择写 `0x3f` 还是 `0x7f`，其余指令逐字节一致）做三组对照复核：

| 运行 | 构建 | 结果 |
| --- | --- | --- |
| `RAS_OFF=1`（关 RAS） | PR head 原样 | **ABORT @ C8357，instrCnt=18**：`Assertion failed ... Ftq.sv:41996`，`[ERROR] ... prediction source cannot be mbtb when resolved branch type is return` |
| `RAS_OFF=0`（对照，RAS 开） | PR head 原样 | 正常 park，C20000 提交 11630 条 |
| `RAS_OFF=1`（关 RAS） | PR head + 仅禁用该 assertion | 与对照完全一致（11630 条）——RTL 功能行为本身正常，唯一阻塞就是这条 assertion |

综上，assertion 触发本身不是硬件产生错误 target 的证据，它是修复暴露出的过时验证断言，属于应随 PR 一并修正的问题。为继续实验，本报告在构建中把这一条 assertion 置为 `false.B`（仅此一处改动，不影响任何功能逻辑；构建其余与 PR head 一致）。

### 2.3 复跑结果（同一 PoC、同一 monitor，构建：PR head + 上述 assertion 禁用）

| 指标 | V3 基线（§1.5 本报告复现） | V3 + #6461 |
| --- | --- | --- |
| `s3_disabled_ras_prediction` | secret1 12 次、secret0 4 次（S3 mux 选择 RAS 栈顶） | **0 次**——`s3_useRas` 全程为 0，修复生效 |
| 主 RAS state 在禁用期间吸收 poison 地址 | 是（push 后 `ras_top` 变为 gadget 地址） | **否**（`ras_top` 稳定不变，state gate 生效） |
| `s1_disabled_uras_prediction` | secret1 2 次 | **secret1 6 次，target=`gadget1`（`0x40000042`）**，`csr_ras_enable=0`——**µRAS 残留确认** |
| S1 错误目标是否转化为 FTQ fetch | 是（基线 fetch gadget1） | **否**：S1 预测到 gadget1 后，后续 FTQ 分配/fetch 流（`0x40000036`、`0x40000050`、`0x40000080` 等）中无 gadget1——S1 的错误目标在 fetch 发出前被后续仲裁纠正 |
| 端到端判定 `secret_dependent_wrong_path_fetch` | true | **false** |

### 2.4 结论

1. **S3 主 RAS 路径：修复完整**——state 更新、S3 consumer、端到端 fetch 三层全部验证通过。
2. **S1 µRAS 路径：残留确认**——µRAS 在禁用期间仍跟踪 poison call 并驱动 S1 预测 target（6 次 secret 相关事件），静态分析与动态行为一致。本 PoC 配置下错误目标未成为真实 fetch：S3（不再使用 RAS）的预测与 S1 不一致时仲裁纠正了取指流。这属于依赖仲裁顺序的偶然行为，而非设计保证——若 S3/mBTB 未覆盖该块（miss、别名等），S1 的 gadget 目标仍可能直接驱动 fetch。若把 `RAS_ENABLE` 当作隔离原语，µRAS 的输出面（`specOut.isCanUse/retTarget`）与 S1 consumer 仍应补 gate。
3. **既有 assertion 的不变量与 RAS 禁用模式冲突**（§2.2，非硬件功能错误）：修复使原来因 bug 而不可达的状态变为正常状态，暴露了这条过时的验证断言；建议随 PR 更新（例如对 `!ras_enable` 豁免）。v3 的两项发现应区分开：µRAS 状态在禁用边界上的错误使用是真 bug（上文第 2 点）；单纯的 assertion abort 只是验证判据问题，不能单独作为硬件错误 target 的证据（对照③）。
4. PR 作者已承认 enable 0→1 切换后短期内可能使用陈旧数据并归为性能问题；结合本节证据，µRAS 残留与 assertion 冲突两点都建议反馈给 PR。

## 3. V2（kunminghu-v2）迁移与复现：**bug 类成立，但经由 V2 特有的第三条 consumer 路径**

> 实验结论：**#6149 的泄露链路在 V2 上复现**，但路径不是 V3 的 S1 µRAS / S3 主 RAS target mux，而是 FTQ 的 IFU predecode RET redirect 路径。适用范围说明：本节全部结论针对 **V2 基线**（`0fa7bb82`）；#6461 是 V3 的 PR，不适用于 V2，本节与 patch 无关。实验环境：`xs-env/XiangShan @ 0fa7bb82`（difftest 子模块 copilot/flash-42）。

### 3.1 V2 与 V3 的关键差异（迁移时逐项核对处理）

| 维度 | V3（基线 e85a929a3） | V2（0fa7bb82 / 5d3934132 两版核实一致） |
| --- | --- | --- |
| BPU 结构 | uBTB/aBTB/uTAGE(S1) + mBTB/TAGE/SC/ITTAGE/RAS(S3)，µRAS 为 S1 forwarding 结构 | uFTB/FTB/TAGE-SC/ITTAGE/RAS 三级，无独立 µRAS，单一 RAS 提供 S2/S3 返回目标 |
| RAS 实现文件 | `bpu/ras/Ras.scala` + `ras/MicroRas.scala` | `frontend/newRAS.scala`（旧 `RAS.scala` 已整体注释） |
| `sbpctl` 位布局 | bit0-6 = UBTB/ABTB/MBTB/TAGE/SC/ITTAGE/**RAS**（`RW(6)`） | bit0-6 = UBTB/BTB/BIM/TAGE/SC/**RAS**/LOOP（`RW(5)`） |
| "仅关闭 RAS"的写值 | `0x3f` | `0x5f`（直接沿用 `0x3f` 会误关 LOOP 而 RAS 仍使能，实验无效） |
| 波形地址编码 | `PrunedAddr`（`>>1`） | 完整 vaddr |
| S2/S3 jalr target mux gate | **无**（`Bpu.scala:404`） | **有**（`newRAS.scala:630/660`：`when(is_ret && io.ctrl.ras_enable)` 才覆盖 `jalr_target`；生成 SV 的 `_GEN/_GEN_0` 已核实） |
| state 更新 gate | 无 | 无（`s2_spec_push/pop` 不含 `ras_enable`，禁用期间 speculative 栈照常更新——波形实测禁用期间栈顶变化 secret0/secret1 各 4/2 次） |
| **RAS 顶层旁路输出** | — | **`io_out_last_stage_spec_info_topAddr := s3_top`（RAS.sv:1297）无 `ras_enable` gate**，存入 FTQ `ftq_redirect_mem` |
| **IFU predecode RET redirect** | — | **`NewFtq.scala:1143-1145`：`when(pd.isRet && pd.valid) { toBpuCfi.target := toBpuCfi.topAddr }`，无 `ras_enable` 检查** |
| 仿真启动流程 | replay emu 直接从镜像启动 | flash 引导（0x10000000，约 1400 周期）后 `jr 0x80000000` |

### 3.2 迁移产物

`v2/`（运行方法见其 `README.md`）：`ras_enable_secret_fetch_v2.S`（`RAS_OFF=0x5f` + mtvec 处理器，后者是裸机测试的必要加固，原因存档于 `v2/env-troubleshooting.md`）、`linker.ld`、`run_v2_secret_fetch.sh`、`monitor_v2_ras_enable_secret_fetch.py`（事件类含 S2/S3 禁用态预测、**IFU-RET 禁用态 redirect**、FTQ gadget fetch、禁用期间栈顶变化计数）、`summarize_pair_v2.py`、双 secret 波形（`ras_enable_secret{0,1}_v2_9400_10200.vcd`，各约 259 MB，窗口 C9400-C10200 覆盖攻击阶段）与 monitor JSON/log。

### 3.3 复现结果（双 secret，`pair_summary_v2.json`）

| 指标 | secret=0 | secret=1 |
| --- | --- | --- |
| 禁用态首个泄露事件 | `ifu_ret_redirect_gadget` | `ifu_ret_redirect_gadget` |
| 事件时刻（VCD time / cycle） | 19802 / C9901 | 19732 / C9866 |
| redirect target（= RAS 栈顶实测） | `0x800000c4` = `gadget0` | `0x80000084` = `gadget1` |
| FTQ→IFU 随后 fetch 该地址 | 是（ftq_idx=14，t=19808） | 是（ftq_idx=13，t=19738） |
| 架构恢复 | 后端 `ret` 解析 redirect 到 `safe_return`（`0x8000014c`，t=19763 实测） | 同左 |

判定字段：`both_runs_ras_disabled=true`、`prediction_targets_differ=true`、`post_prediction_fetch_targets_differ=true`、`secret_dependent_wrong_path_fetch=true`。两个运行唯一不同的输入是 secret bit，而禁用态 RAS 栈顶、redirect target、FTQ fetch 起点全部随之分叉。

**V2 泄露路径**（与 V3 对照）：

```text
state 层（与 V3 同病）：RAS_ENABLE=0 期间，secret 选定的 poison jal 照常 speculative push
    poisonN: jal ra, poison_common  --(s2_spec_push, 无 ras_enable gate)--> s3_top = 0x80000084/c4
                                        （波形：禁用期间 s3_top 变化并保持为 gadgetN 地址）

数据通路（V2 特有 consumer）：
    ret_trigger 的 ret 到达 IFU predecode 写回（pdWb.isRet=1）
        -> NewFtq.scala:1143-1145: toBpuCfi.target := ftq_redirect_mem.topAddr
           （topAddr <- ras.last_stage_spec_info.topAddr = s3_top，全链路无 ras_enable 检查）
        -> IFU redirect target = secret 选定的 gadgetN 地址
        -> FTQ->IFU 真实 fetch gadgetN（wrong path）
        -> 后端执行 ret（ra=safe_return）发现误预测 -> redirect 恢复，架构结果正确
```

注意被**排除**的路径：S2/S3 的 `jalr_target` 覆盖（`ras_drove_target_while_disabled=false` 全程成立）——V2 在这个 consumer 上确实有 gate，静态分析这部分结论正确；泄露经由的是旁路的 `topAddr` 输出与 IFU-RET redirect。按 §1.6 框架表述：V2 实现了"最终 consumer gate"的一个实例（jalr mux），但 RAS 的输出不止一个 consumer——**gate 了一个 mux，漏了另一个 mux**。

环境与工具问题的排查过程（emu 构建甄别、flash 引导、波形格式、mtvec 问题等）已存档至 `6149/v2/env-troubleshooting.md`，此处不重复。

## 4. 波形分析

### 4.1 波形档案

| 运行 | 波形文件 | 窗口 | 
| --- | --- | --- |
| V3 secret=0 | `v3/ras_enable_secret0_mtvec_6500_9000.fst` | C6500-C9000 |
| V3 secret=1 | `v3/ras_enable_secret1_mtvec_6500_9000.fst` | C6500-C9000 |
| V2 secret=0 | `v2/ras_enable_secret0_v2_9400_10200.vcd` | C9400-C10200 |
| V2 secret=1 | `v2/ras_enable_secret1_v2_9400_10200.vcd` | C9400-C10200 |

信号与时间约定：V3 地址为 `PrunedAddr`（`>>1`，如 `gadget1 0x80000084 -> 0x40000042`），V2 为完整 vaddr；两种波形的 time 都是 2 × cycle。

### 4.2 V3 泄露事件时间线（secret=1，S3 主 RAS 路径）

背景：`beqz`（`0x80000070`）尚未解析，前端按 not-taken 预测走 fall-through，speculative执行 `poison1_site` 的 `jal ra, poison_common`。

| time / cycle | 事件 | 波形证据 |
| --- | --- | --- |
| 13456-57 / C6728 | S3 预测 poison1_site 块（含 `jal ra`），speculative push 返回地址 `0x80000084`；同拍 FTQ 分配 idx7（start=`0x40000080`=poison_common） | `ras_top: 0 -> 0x40000042`；`ras_en=0` 全程 |
| 13459-60 / C6730 | 前端以 RAS 栈顶作为下一个预测块：FTQ 分配 idx9，start=`0x40000042`（gadget1） | `ftq_start=0x40000042`，`ras_top=0x40000042` |
| 13461-62 / C6731 | `ret_trigger` 的 `ret` 流经 S3：`s3_taken=1`、`s3_useRas=1`、`s3_target=0x40000042`——S3 target mux 选择 `ras.io.topRetAddr`，override 生效 | `s3_useRas=1 && s3_taken=1 && s3_target==ras_top` |
| 13476 / C6738 | FTQ→IFU 对 `0x40000042` 发出真实 fetch（ftq_idx=9） | monitor `ftq_fetch_gadget`，start=`0x40000042` |
| 13477-78 / C6739 | RAS 栈被 pop（`spec_pop`，同样无 enable gate） | `ras_top: 0x40000042 -> 0` |
| 15433-34 / C7717 | 后端解析 `ret`（架构 target = `ra` = safe_return），redirect 转化为 FTQ fetch `0x400000a6`（`0x8000014c`） | `ftq_start=0x400000a6` |

解读：从 push 到 wrong-path fetch 仅 10 个周期——禁用态下 RAS 栈顶在预测流水内写入后即被使用。恢复用了约 980 个周期，期间错误路径上的 `gadget1` probe load 与 `wrong_path1` 循环被speculative fetch/执行，最后由后端 redirect 清除；程序最终在 `done` 循环正常提交（C11000 时 5567 条）。secret=0 的对称事件：S3 预测 → `gadget0`（`0x40000062`）于 t=15404（C7702），FTQ fetch t=15406（idx10），机制相同。

### 4.3 V2 泄露事件时间线（secret=1，IFU predecode RET redirect 路径）

| time / cycle | 事件 | 波形证据 |
| --- | --- | --- |
| 19649 / C9824 | poison1 `jal ra` 的speculative push（`s2_spec_push`，无 `ras_enable` gate） | `s3_top -> 0x80000084`（禁用期间首次栈顶变化） |
| 19652-57 / C9826 | 前端speculative fetch `ret_probe` 块（`0x80000140`，含 `la ra; ret`） | `ftq_start=0x80000140` |
| 19733-34 / C9867 | `ret` 到达 IFU predecode 写回：`pd_isRet=1`，FTQ 用保存的 RAS 栈顶替换 redirect target | `io_toBpu_redirect_bits_cfiUpdate_pd_isRet=1`，`cfiUpdate_target=0x80000084`（=`s3_top`），`ras_en=0` |
| 19738-42 / C9869-71 | FTQ→IFU 对 `0x80000084`（gadget1）发出 fetch | `io_toIfu_req_bits_startAddr=0x80000084`（ftq_idx=13） |
| 19741 / C9870 | RAS 栈被 pop | `s3_top: 0x80000084 -> 0` |
| 19763 / C9882 | 后端解析 `ret`，redirect 到 `safe_return` | `ftq_start=0x8000014c` |

解读：V2 的泄露不发生在 BPU 预测级（S2/S3 `jalr_target` mux 的 gate 全程有效，`ras_drove_target_while_disabled=false`），而发生在 IFU predecode 写回的 redirect 环节——`NewFtq.scala:1143-1145` 用 `ftq_redirect_mem.topAddr`（来自无 gate 的 `ras.last_stage_spec_info.topAddr`）替换 RET 的 redirect target。从 push 到 wrong-path fetch 约 45 个周期，后端 12 个周期内完成恢复。

### 4.4 两代实现的对照

1. **共同前提（state 层缺陷）**：V2/V3 两代的 speculative push 均不受 `ras_enable` 约束，secret 选定的返回地址都能在禁用期间入栈（V3 t=13456/C6728，V2 t=19649/C9824，均实测）。
2. **差异在 consumer**：V3 在 BPU 预测级泄露——S1 µRAS / S3 主 RAS 的 target mux 不检查 enable，预测本身就指向 gadget；V2 在 redirect 环节泄露——预测级 mux 有 gate，但 IFU predecode RET redirect 将另一个无 gate 的 RAS 栈顶副本用作 target。
3. **恢复路径相同**：均为后端执行 `ret`（target=`ra`=`safe_return`）发现误预测后 redirect，架构结果正确。wrong-path window：V3 约 980 周期，V2 约 12 周期。
4. §1.5 开放点现状：secret=0 运行中 8 次指向 `gadget1` 的 S3 禁用态预测均未转化为 FTQ fetch（monitor `ftq_fetch_opposite_after_prediction=false`）；这些预测发生在 `beqz` 解析前的 fall-through speculative路径上，随后被分支解析 redirect 清除，S3 override 的 fetch 未及发出。

### 4.5 侧信道证据边界

本报告证实到"secret 相关的 wrong-path fetch"（FTQ→IFU 请求 + 地址分叉）。gadget 内 probe load（`ld t4, 0(t3)`，PROBE0/1_ADDR）是否确实speculative执行并留下可测量的 cache 差异，属于下一层证据，需要另做 flush+reload 类测量实验，不在讨论范围内。

## 5. 结论

**Bug 定性。** #6149 是一个"预测器 disable 语义不完整"的微架构隔离缺陷：`sbpctl.RAS_ENABLE` 只改写 control plane 的寄存器值，没有贯穿到 RAS/µRAS 的 state 更新和 next-PC 数据通路。危害不是架构功能错误（后端执行 `ret` 后总能恢复，程序结果正确），而是 secret 相关的 wrong-path fetch——被禁用的 RAS 状态继续吸收由 secret 选择的 call site 返回地址，并继续驱动取指流分叉，构成 Spectre 类侧信道的前提条件（本报告证实到 fetch 层，cache 影响未量化）。

**三组实验的核心数字。**

| 构建 | S1 µRAS 禁用态预测 | S3 主 RAS 禁用态预测 | secret 相关 fetch | 判定 |
| --- | --- | --- | --- | --- |
| kunminghu-v3 基线 `e85a929a3` | 2 次（secret1 运行） | 16 次 | **是**（S1/S3 双路径均转化为实际 fetch） | bug 成立 |
| kunminghu-v2 `0fa7bb82` | —（无 µRAS） | 0 次（mux 有 gate） | **是**（经 IFU predecode RET redirect 第三条路径） | bug 成立 |
| kunminghu-v3 + #6461 `dcdf1c1c9`* | **6 次（残留）** | 0 次 | 否（S1 错误目标被仲裁纠正） | S3 修复完整，S1 残留 |

\* PR head 自带的 assertion 与 `RAS_ENABLE=0` 冲突，需禁用该 assertion 才能运行（§2.2，附最小 PoC 与三组对照）。

**逐条结论。**

1. **V3**：基线上 S1 µRAS 与 S3 主 RAS 两条泄露路径均被波形证实并完成逐周期时间线（§4.2）；issue 作者的原始数据与本地独立复现一致（§1.5）。
2. **V2**：同一 bug 类成立但路径不同——S2/S3 `jalr_target` mux 有 gate（这部分静态分析与波形一致），泄露经由 `NewFtq.scala:1143-1145` 的 IFU predecode RET redirect：RAS 栈顶经无 gate 的 `last_stage_spec_info.topAddr` 存入 `ftq_redirect_mem`，被直接用作 redirect target。**同一个 bug 类在两代实现中以不同 consumer 出现**，说明这是"有状态预测器的输出面有多个 consumer、disable 语义必须覆盖每一个 next-PC 来源"这一结构性问题。
3. **#6461 修复评估**：S3 主 RAS 路径（state 更新、consumer、端到端 fetch）三层全部修复并验证；**S1 µRAS 路径残留**——`MicroRas.scala` 仍不消费 `io.enable`，S1 mux 仍不检查 enable，禁用态仍有 6 次 secret 相关的 S1 预测事件；本配置下未转化为实际 fetch，属于依赖仲裁顺序的偶然行为，若 mBTB 未覆盖该块，S1 目标仍可能直接驱动 fetch。建议随 PR 补 µRAS 输出面与 S1 consumer 的 gate。
4. **修复暴露的验证断言缺陷**（非硬件功能错误）：既有 assertion "prediction source cannot be mbtb when resolved branch type is return"（#5639 引入，非 #6461 新增）在基线上被 #6149 的 bug 掩盖（return 恒由 RAS 提供 target，source 恒为 s3Ras）；#6461 修复 gate 后，RAS 禁用时 return 由 mBTB 提供成为正常状态，最小 PoC（关 RAS + 两条 ret）即触发 fatal abort。禁用该 assertion 后功能行为与 RAS 开启时完全一致（对照实验），故不属于硬件产生错误 target，但应随 PR 更新断言（对 `!ras_enable` 豁免）。

**对使用者的建议。** 若软件依赖 `sbpctl.RAS_ENABLE` 做隔离（如密钥相关代码段），在 V3 基线与 V2 上都不能信任该位；在 #6461 合入后 V3 的 S3 路径可用，但 S1 µRAS 路径与 V2 的 IFU-RET redirect 路径仍在，应等待上述残留修复后再依赖该语义。

## 参考资料

- [XiangShan Issue #6149](https://github.com/OpenXiangShan/XiangShan/issues/6149)
- [XiangShan PR #6461：fix(Bpu): enable & debug-related fix & cleanup](https://github.com/OpenXiangShan/XiangShan/pull/6461)（标记 Fixes #6149，截至 2026-09-07 open/draft，head `dcdf1c1`）
- [XiangShan Issue #6159（同类 BP 开关问题）](https://github.com/OpenXiangShan/XiangShan/issues/6159)
