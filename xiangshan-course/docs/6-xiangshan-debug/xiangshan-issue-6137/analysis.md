# 香山 kunminghu-v3 Issue #6137 分析：`sbpctl` 禁用 ABTB 后陈旧预测仍可驱动瞬态 I/D-cache 访问

## 0. 摘要

Issue #6137 报告：在 kunminghu-v3 上通过自定义 CSR `sbpctl`（0x5c0）将 `ABTB_ENABLE` 置 0 后，ABTB（Ahead BTB，超前分支目标缓冲）的输出接口仍然有效，陈旧的 `.valid`/`.ctr` 字段继续伴随 BPU 预测流，构成 Spectre v2（Branch Target Injection）形态的瞬态执行窗口：瞬态取指攻击者布置的 gadget、瞬态访问 secret 并通过 probe array 在 D$ 中编码 secret 字节。

本报告在 issue 附件实证的作者构建树 `3931c5112c`（2026-05-18，clean 构建）上复现该 bug，三层证据齐全：

1. 基线自检证据：基线 RTL 自带的 Bpu 输出一致性断言（`Bpu.scala:556`）在攻击相位于 C9824 触发并停机——abort 周期、断言行号与作者附件 `normal_assert_run.log` 逐项一致；
2. 波形证据：`ABTB_ENABLE=0` 生效后的 13 拍内，ABTB 输出（`io.prediction`/`io.abtbResult`/`io.meta` 的 valid）持续置位（26 次采样事件、18 次 ABTB/BPU PC 失配），期间 BPU 反复发出 target 为 gadget（0x80001000）的预测包，disable 后 I$ 对 gadget 块的取指请求持续出现（伴随 icache 活动 24 次采样，与作者证据的 24 一致）；
3. 端到端证据：双 secret（5/7）配对运行中，disable 后瞬态路径执行了 gadget 内的 `ld secret_data`（C9831）与 `ld probe_array[secret*64]`（C9838），两条运行各自只触碰与自身 secret 值对应的 probe 行（`0x80005140` / `0x800051c0`），`secret_dependent_wrong_path_dcache=true`。在该树上，泄露链由 disable 后的陈旧输出直接驱动——issue 的核心主张成立。

对上游修复链（`fix-bpu-enable` 分支：`2a4721ab8` 直接修复 + `dcdf1c1c9` 互联 enable）在 PR head 上的实验验证表明：修复对缺陷本体完整生效——disable 后输出端口有效位当拍归零、断言全程静默、不再产生任何以 stale 目的地的预测包。修复版上同一 PoC 的端到端泄露仍能完成，但其因果链变为"预测在 CSR 落地前一拍已发出"的边界竞态（v2 上同样存在，见 §3），属于 `sbpctl` 语义边界（非隔离 fence）而非 #6137 的硬件缺陷，与 #6149 分析的结论一致。

在 kunminghu-v2 上，同一 PoC 的端到端泄露同样成立（C9753 redirect → C9761 fetch → C9784 probe 行访问，经由预 retire 在途预测）。v2 没有 ABTB，各预测器输出 gate 完好（disable 后 ~5 拍内 hit 全部归零、无持续失配），因此 v2 上不存在 #6137 的缺陷形态；两代真正的共同点是都挡不住边界竞态，差别只在 v3 多出的陈旧输出窗口，而它已被修复消除。

## 1. Bug 分析

### 1.1 微架构背景：v3 的 ABTB

kunminghu-v3 的 BPU 在传统多级预测器（uBTB/mBTB/TAGE/SC/ITTAGE/RAS）之外引入了 ABTB（Ahead BTB）：它在 S0/S1 阶段超前于主预测器流水提供分支目标，用于更早的 next-PC 重定向，并配有 uTAGE 作为其方向纠错伴随结构（`utage.io.abtbPrediction := abtb.io.abtbResult`，Bpu.scala:207）。ABTB 输出为 4 lane 的 `io.prediction`（预测向量）、`io.abtbResult`（供 uTAGE 消费）与 `io.meta`（经 `s2_abtbMeta/s3_abtbMeta` 两级寄存后进入 `fastTrain` 快速训练路径，Bpu.scala:153-154、188）。

### 1.2 `sbpctl` 与 `ABTB_ENABLE` 的传播路径

CSR 侧：`NewCSR.scala:1419` 将 `sbpctl.regOut.ABTB_ENABLE` 接到 `bp_ctrl.abtbEnable`，经 frontend 进入 BPU。BPU 侧（基线代码）：

```scala
private val ctrl = DelayN(io.ctrl, 2)          // Bpu.scala:89，为时序延迟 2 拍
abtb.io.enable   := ctrl.abtbEnable            // Bpu.scala:106
```

ABTB 内部，`io.enable` 只参与流水推进的 gate（AheadBtb.scala:83-85）：

```scala
s0_fire := io.enable && predictReqValid
s1_fire := io.enable && s1_valid && s2_ready && predictReqValid
s2_fire := io.enable && s2_valid && predictionSent
```

### 1.3 缺失的 gate：fire 被关，输出没关

disable 生效后 `s0/s1/s2_fire` 被阻断，但 `RegEnable` 语义下流水 valid 寄存器保持旧值，而三个输出接口的 valid 完全不检查 enable（AheadBtb.scala，作者树行号）：

```scala
pred.valid      := s2_valid && s2_hitMask(i)   // :198  io.prediction（4 lane 同式）
pred.valid      := s2_valid && s2_hitMask(i)   // :208  io.abtbResult
io.meta.valid   := s2_valid                    // :223  io.meta → fastTrain
```

也就是说：disable 后 SRAM 内容与流水 valid 原样保留，输出端口继续把上一次使能期间锁存的 hit/taken/target 呈现给消费者。消费者侧同样不设防——S1 组合逻辑 `s1_abtbTakenMask`/`s1_abtbResult`（Bpu.scala:295 起）直接消费这些 valid/ctr 并参与 S1 重定向仲裁；`io.meta` 一路锁存到 S3 供 `fastTrain` 使用。

这一缺陷恰好被基线 RTL 自带的验证断言捕获（Bpu.scala:554-556）：

```scala
when(io.toFtq.prediction.fire && abtb.io.prediction.map(_.valid).reduce(_ || _)) {
  assert(abtb.io.debug_startPc === s1_startPc.head.unGuard)
}
```

语义：只要 BPU 向 FTQ 发射预测且任一 ABTB lane valid，ABTB 内部 PC 就必须跟上 BPU 的 S1 startPc。disable 后 ABTB 流水冻结（PC 停在旧值）而 valid 仍在，该不变量立即被破坏——断言触发本身就是"陈旧输出伴随活跃预测流"的直接证据（作者附件的 `normal_assert_run.log` 即此断言失败；本报告复现见 §1.5）。

### 1.4 PoC 设计与攻击链

PoC（`poc_abtb_dcache_gadget.S`，issue 附件）为五段式：

1. 训练（320 次迭代）：`bnez t3, gadget` 恒 taken，把 ABTB 对 `branch_site` 的表项训成强 taken；gadget 中 `ld s1, 0(s0)`（s0=train_data）同时烧热 gadget 取指路径；
2. 关闭：`csrw sbpctl, zero` 清掉全部预测器 enable 位；
3. 换靶 + 慢条件：`la s0, secret_data` 架构上把指针换成 secret，随后 `ld t3, 0(flag_zero)` 冷加载使 t3 变 0 但要等数十拍——分支在条件就绪前持续沿预测路径前冲；
4. 陈旧劫持：架构结果应为 not-taken，但训练出的 taken 预测把前端引向 `gadget`（4KB 对齐）；
5. 瞬态编码：wrong-path 上执行 `ld secret; andi 0xff; slli 6; ld probe_array[secret<<6]`——256 槽 × 64B probe array，Flush+Reload 读出热槽即得 secret。

双 secret 规范化：原 PoC 固定 secret=7。本报告将其参数化并成对运行 secret=5/7：训练字节为 0，`probe[0]` 行在训练期已被烧热，secret=0 的瞬态访问会 L1 命中而不可见，故配对选用两个均为冷行的取值。两次运行唯一输入差异是内存中的 secret 字节，输出对比即构成泄露判定（§1.5）。

### 1.5 V3 基线复现（作者构建树 `3931c5112c`）

基线选取：issue #6137 创建于 2026-06-25，页面的 Branch 字段为 `kunminghu-v3`，正文与附件均未指定具体 commit；作者附件运行日志显示其实际构建树为 `3931c5112c dirty:1`（2026-05-18，上游可得）。本报告直接在该 commit 上以 clean checkout 构建复现（dirty:0）。缺陷存在于 ABTB 引入起、直至 `2a4721ab8` 修复前的所有 kunminghu-v3 提交，修复对照使用 fix-bpu-enable 分支 head `dcdf1c1c9`（§2）；该组合与 #6149 报告"基线 `e85a929a3` vs PR head"的处理方式一致（非单变量对比：差异包含中间演进与修复链）。

运行 A（断言默认开启，作者原始 bin）：PoC 运行在 C9824 停机，与作者附件逐项一致：

```text
Core 0's Commit SHA is: 3931c5112c, dirty: 0        ← 作者为 dirty:1
Assertion failed at .../build/rtl/Bpu.sv:7667        ← 与作者 log 同一行号
The simulation stopped. There might be some assertion failed.
Guest cycle spent: 9,824                             ← 与作者 log 完全相同
Assertion failed at Bpu.scala:556                    ← 作者树行号
```

运行 B（`STOP_COND=0` 构建，断言只打印不停止）：同一 PoC、同窗口跑满全程，波形覆盖完整攻击相位。作者原始 bin 与本报告参数化 bin（secret 5/7）三次运行结果一致（事件周期相同）：

| 指标 | 本报告（clean 构建） | 作者附件（dirty:1） |
| --- | --- | --- |
| 首个 disable 态 PC 失配 | C9817 | C9817 |
| `disabled_abtb_output_valid`（端口 valid 直测） | 26 | —（作者未直测端口） |
| `disabled_abtb_prediction_fire` | 26 | 32 |
| `disabled_abtb_pc_mismatch` | 18 | 24 |
| `disabled_abtb_with_icache_activity` | 24 | 24 |
| wrong-path `ld secret_data`（0x80003000） | C9831 ×2 | C9835 |
| wrong-path `ld probe[secret*64]` | C9838 ×2 @ 0x80005140/0x800051c0 | C9842（D$ 外通道 9843） |
| 断言打印（全窗口运行） | ×8 | — |
| 双 secret（5/7）端到端判定 | `secret_dependent_wrong_path_dcache=true`（各自只命中自身 probe 行，无交叉） | —（作者仅 secret=7） |

（±4 拍的事件级差异与作者的本地修改/运行开关有关，事件类型与量级一致；作者证据里 secret 字节为 7，probe 行地址 0x800051c0 与本报告一致。）

### 1.6 Bug 类别归纳：predictor disable 的四层 invariant

本 bug 与 #6149（RAS disable）、#6159（uBTB/aBTB enable）同属一个 bug 家族："disable 某微架构预测器"的 CSR 位没有完整贯穿预测器子系统。沿用 #6149 报告归纳的四层检查框架（本 case 状态）：

| level | invariant | #6137 基线状态 |
| --- | --- | --- |
| 1. 请求/活动 gate | disable 后是否仍有 SRAM 读、流水 fire | 有 gate（`s0/s1/s2_fire` 带 `io.enable`） |
| 2. state 更新 gate | 训练/speculative 更新是否停止 | 未处理（表项保留；修复作者明示靠输出 gate 即可，与本报告 §6 结论一致） |
| 3. 输出有效 gate | `valid/hit` 在 disable 后是否强制失效 | 缺失（三个输出接口的 valid 均不看 enable，本 bug 核心） |
| 4. 最终 consumer gate | next-PC 仲裁是否绝对禁止其成为控制流来源 | 缺失（S1 `s1_abtbTakenMask`/`s1_abtbResult`、uTAGE、`fastTrain` meta 路径均直连） |

另一个同族教训在 #6137 上再次出现：预测器不是独立开关的清单，而是带依赖的图——uTAGE 依赖 ABTB、TAGE/SC/ITTAGE/RAS 依赖 mBTB。这也是后续 `dcdf1c1c9` 互联 enable 修复的内容（§2.1）。

## 2. 修复评估（fix-bpu-enable 链，已在 `dcdf1c1c9` 上实验验证）

### 2.1 修复内容

`2a4721ab8 fix(Bpu): fix io.enable gate for each sub-predictor`（#6137 的直接修复）：

- AheadBtb.scala 三处（基线行号 198/208/223 对应处）：`pred.valid := s2_valid && s2_hitMask(i) && io.enable`（`io.prediction` 与 `io.abtbResult`），`io.meta.valid := s2_valid && io.enable`；
- 同一提交还 gate 了 ras/tage/sc/utage/mbtb 的输出 valid/hit、ras 的 redirect/commit 训练、utage 的 t0 训练，并删除了旧的 Constantin 测试接线；
- 作者注释明确残余：enable 0→1 后短时间内可能使用陈旧数据（重开窗口，定位为性能问题——本 PoC 全程 disable，不涉及）。

`dcdf1c1c9 fix(Bpu): interconnected io.enable`（同一 PR 的 follow-up，也是 #6461 的 PR head）：`utage ← abtbEnable`、`uras ← rasEnable && mbtbEnable`、`tage/sc/ittage/ras ← xxEnable && mbtbEnable`。

### 2.2 实验结果：缺陷本体修复完整

同一 PoC、同参数在 `dcdf1c1c9`（断言默认开启）上运行，跑满 C12006 全程零断言触发——对照基线 C9824 停机。波形层面（secret=7）：

| 指标 | 基线 `3931c5112c` | 修复版 `dcdf1c1c9` |
| --- | --- | --- |
| `disabled_abtb_output_valid`（端口直测） | 26 | 0 |
| `io.meta.valid` 在 disable 后 | 持续 1 达 13 拍（C9817-9829） | disable 当拍归零 |
| disable 后以 stale 目的地的预测包 | 持续产生（startPc=branch_site、target=gadget） | 无 |
| 断言 | C9824 停机 / 全窗口版打印 ×8 | 静默 |
| 内部 `s2_valid` 冻结 | 至 C9829 | 至 C9693（内部寄存器未 gate，但端口已隔离，无害） |

层 3（输出有效 gate）修复被端口直测证实；层 4 由断言静默佐证（S1 消费面不再见到 disable 下的 ABTB valid）。

对照 monitor JSON 时注意口径：基于内部 `s2_valid`/`s2_startPc` 的计数（修复版 `disabled_abtb_prediction_fire=20`、`disabled_abtb_pc_mismatch=18`）仍非零——这是内部流水寄存器未 gate 且需数拍排空的正常表现，输出已被隔离，不构成缺陷残留。

### 2.3 残余：边界竞态下的端到端泄露仍可完成（语义边界，非 #6137 缺陷）

修复版上双 secret 配对的端到端判定仍为 true：`loadunit_secret_data`、`loadunit_probe_secret_line`（各自命中 `0x80005140`/`0x800051c0`）在 disable 后照常发生。时间线（§4.3）显示其因果链为：C9683（`abtb.io_enable` 仍为 1）branch_site 块的 taken→gadget 预测已经发出并完成 S1 重定向，C9684 disable 才落地——这条在途预测不属于"disable 后的陈旧输出"，gate 修复无法（也不应）将其撤回。基线上泄露由 disable 后的陈旧输出直接驱动（§4.2），修复后这条路径消失，泄露退化为仅剩预 disable 在途预测一条路，与 v2（预测器 gate 本就完好）的行为完全一致——本质是 `sbpctl` 写入只改变此后预测的生成，不冲刷已发射预测/取指，sbpctl 不是隔离 fence。此语义边界与 #6149 报告 §5 的结论一致，应作为使用文档语义说明而非 #6137 的修复遗漏。

另有一处静态疑点如实记录：修复未 gate `abtb.io.predCtrl.jumpValidVec/conditionValidVec`（S1 `s1_abtbTakenMask` 的输入之一）。在本 PoC 的泄露窗口内两组向量在基线与修复版上均为 0（冻结块内无跳转），该输出路径在本 PoC 中未被激活，无法实证其影响；若按四层框架做深度审计，建议连同 predCtrl 一并 gate（§6）。

### 2.4 结论

1. #6137 缺陷本体（disable 后输出持续有效并驱动新预测）：在作者构建树上由断言停机、26 次端口 valid、13 拍内持续产生的 stale 目标预测包直接证实，修复后全部归零/静默。修复完整且必要。
2. 修复版上端到端泄露仍可完成，但因果链已变为预 disable 在途预测（与 v2 同型），属于 `sbpctl` 语义边界。报告证据因此分层表述：缺陷本体（断言+端口+stale 预测包）与端到端泄露（修复前后路径不同）分别归因，不互相冒领。
3. 修复作者自述的 0→1 重开窗口残余与本 PoC 无关；predCtrl 输出路径建议随 PR 一并审计。

## 3. V2（kunminghu-v2）迁移与复现：无 ABTB，同一 PoC 的泄露经预 retire 在途预测成立

### 3.1 V2 与 V3 的关键差异（迁移时逐项核对）

| 项 | V3 | V2（`0fa7bb825`） |
| --- | --- | --- |
| ABTB | 有（S0/S1 超前预测） | 无（前端为 FauFTB/FTB/TAGE/ITTAGE/SC/RAS/Loop） |
| `sbpctl` 位 | 含 `ABTB_ENABLE` | UBTB/BTB/BIM/TAGE/SC/RAS/LOOP，无 ABTB 位 |
| 预测器输出 gate | 基线缺失（本 bug） | 完好：FauFTB `hit := s1_hit && fauftb_enable`（FauFTB.scala:115，`fauftb_enable = RegNext(io.ctrl.ubtb_enable)`）；FTB `s1_hit = ... && io.ctrl.btb_enable`（FTB.scala:707）；Tage/SC 同式；RAS 消费带 `ras_enable`（newRAS.scala:630/660） |
| 泄露路径 | 基线：陈旧输出直接驱动（已修复）＋边界竞态；修复后仅竞态 | 仅边界竞态 |

v2 无 ABTB 且输出 gate 齐全，因此 #6137 的缺陷形态（disable 后输出持续有效）在 v2 上结构性不存在——这与 #6149 当年 v2 的局面不同（当时 v2 经由 IFU predecode RET 重定向的第三路径成立）；对 #6137 的攻击形态（条件分支训练→disable→慢条件），v2 的 predecode 只处理无条件控制流，不构成额外路径。

### 3.2 迁移产物

同一份 PoC 二进制（sbpctl 地址与 `csrw sbpctl, zero` 语义在 v2 完全一致，逐位 disable 所有预测器），双 secret 5/7，窗口 C9400-9900，emu 为 kunminghu-v2 `0fa7bb825` 构建。monitor 针对 v2 信号层次改写（Composer 级 S1 hit、FTB s1_hit、FTQ→IFU 取指、FTQ redirect、I$/D$ TL-A 外通道）。

### 3.3 复现结果（双 secret）

| 事件 | secret=5 | secret=7 |
| --- | --- | --- |
| `disable_instant`（frontend 侧 enable 读 0） | C9760 | C9760 |
| redirect→gadget（disable 前发出） | C9753 | C9753 |
| FTQ→IFU fetch gadget（在途 redirect 执行） | C9761 | C9761 |
| `flag_zero` cold miss（D$ A 通道，维持瞬态窗口） | C9776 | C9776 |
| probe[secret*64] D$ miss | C9784 @ 0x80005140 | C9784 @ 0x800051c0 |
| 预测器 hit 在 disable 后 | 10 次采样（全部落在 C9760-9764） | 同左 |
| disable 后新 gadget redirect | 0 | 0 |
| 持续性陈旧预测 | 无（hit/redirect 在 ~5 拍内归零） | 同左 |

enable 侧的传播链 `csrCtrl → BPU DelayN(2) → FauFTB RegNext(1) → 预测流水` 解释了 5 拍的收尾窗口；此后预测面完全静默。端到端泄露经由 C9753 的在途 redirect 完成——与 v3 修复版行为一致；v3 基线多出的、由陈旧输出直接驱动的泄露路径，正是 #6137 的实体。

> 注：v2 波形未暴露 LoadUnit 内部接口，瞬态 load 证据取自 D$ TL-A 外通道（probe 行必为冷 miss，外通道可见）；预测器 hit 取 Composer 级 S1 hit（FauFTB gate 之后）与 FTB `s1_hit`（RTL 内含 `btb_enable`）。

## 4. 波形分析

### 4.1 波形档案

| 变体 | 波形（窗口） | 绑定结果 |
| --- | --- | --- |
| v3 作者树 `3931c5112c`，断言默认 | `authON_orig_9500_10100.fst`（原始 bin，至 C9824 停机） | abort 证据（§1.5 运行 A） |
| v3 作者树 `3931c5112c`，`STOP_COND=0` | `authOFF_secret7_9600_10300.fst`、`authOFF_secret5_9600_10300.fst`、`authOFF_orig_9600_10300.fst` | 全窗口泄露链（§1.5 运行 B、§4.2） |
| v3 修复版 `dcdf1c1c9` | `fixhead_secret7_9300_10400.fst`、`fixhead_secret5_9300_10400.fst` | 修复验证（§2.2/2.3） |
| v2 `0fa7bb825` | `v2_secret7_9400_9900.fst`、`v2_secret5_9400_9900.fst` | v2 复现（§3.3） |

（地址均为半字折叠编码（字节地址>>1），表中已还原为字节地址。）

### 4.2 V3 基线泄露时间线（作者树，secret=7，S1 源路径）

| 周期 | 事件 | 信号依据 |
| --- | --- | --- |
| C9817 前 | 最后一次训练迭代，`abtb.io_enable` 仍为 1，流水正常（fire 110） | trace `fire_s012` 列 |
| C9817 | `abtb.io_enable`→0（disable 落地，DelayN(2) 后），`s0/s1/s2_fire` 全部归零 | `en`/`fire_s012` 列 |
| C9817-9829 | 陈旧输出窗口（13 拍）：内部 PC 冻结在 0x80000060（branch_site），`s2_valid`/`io.meta.valid`（RTL 直连同源）持续为 1；期间 BPU 反复发射预测包——startPc=branch_site（0x80000060）、target=gadget（0x80001000），即 disable 后 stale 表项仍在驱动新预测；I$ 对 branch_site 块与 gadget 块的取指请求交替 fire（伴随 icache 活动 24 次采样） | 端口 valid 直测、预测包 `pred_target`、`ic_req_start` |
| C9824 | 断言 `Bpu.scala:556` 触发（断言 ON 构建于此停机；STOP_COND=0 构建继续，全窗口共打印 ×8） | run log |
| C9830 | 内部 `s2_valid` 被流水排空清零，陈旧输出窗口结束 | trace |
| ~C9817-9830 | `flag_zero` 冷加载维持瞬态窗口（作者证据：D$ 外通道 9833） | 作者 `security_evidence.json` |
| C9831 | wrong-path `ld secret_data`（0x80003000） | loadunit lqWrite |
| C9838 | wrong-path `ld probe_array[7*64]`（0x800051c0） | loadunit lqWrite |

### 4.3 修复版时间线（secret=7，`dcdf1c1c9`）

C9683（en=1）branch_site 块预测包 startPc=gadget（重定向已发出）→ C9684 disable 落地 + I$ fetch gadget + `io.meta.valid` 当拍归零、端口 valid 全程 0、断言静默 → C9686-9693 瞬态流顺序取指 0x80001040/0x80001080/0x800010c0（gadget 块及其后继块内执行）→ secret/probe load 完成。与基线的差异：disable 后不再产生任何 stale 目的地的预测包，泄露完全依赖 disable 前一拍已发出的在途重定向。

### 4.4 V2 时间线（secret=7）

C9753 redirect→gadget（预测器仍使能）→ C9760 CSR 侧 enable 读 0 → C9761 fetch gadget（在途）→ C9764 预测器 hit 最后一次 → C9776 flag 冷 miss → C9784 probe[7*64] miss。disable 后无任何新 gadget redirect，无持续失配。

### 4.5 三代对照与证据边界

| | v3 基线（作者树） | v3 修复版 | v2 |
| --- | --- | --- | --- |
| disable 后输出端口有效 | 26 次 / 13 拍（缺陷） | 0 | 0（结构性） |
| disable 后 stale 目的地的预测包 | 持续产生 | 0 | 0 |
| 断言 | C9824 触发停机 | 静默 | n/a |
| 端到端 secret 相关 D$ 访问 | 有（陈旧输出直接驱动） | 有（在途预测） | 有（在途预测） |

证据边界：瞬态执行证据以微架构事件（取指请求、load 写回、D$ A 通道）为准，等价于 issue 作者使用 XS-JS judge 的结论口径；本报告未做 NEMU 对拍（瞬态访问不影响架构状态，difftest 全程可过/已用 `--no-diff` 排除干扰）。

## 5. 结论

1. #6137 属实且已由 `fix-bpu-enable` 链修复：`ABTB_ENABLE=0` 后 ABTB 三个输出接口的 valid 不随 disable 失效，且在作者构建树上 stale 表项持续驱动 branch_site→gadget 的新预测包达 13 拍（断言停机、26 次端口 valid、24 次伴随 icache 活动与作者证据一致）；`2a4721ab8` 的输出 gate 修复在 PR head 上端口直测归零、断言静默，验证完整。
2. 该缺陷是 #6149/#6159 同族问题在"BTB 目标注入"维度的实例，四层 invariant 框架中缺失第 3、4 层；修复落在第 3 层（输出有效 gate），依赖图（uTAGE←ABTB 等）由 `dcdf1c1c9` 补齐。
3. PoC 的端到端 secret 泄露在修复版与 v2 上经"预 disable 在途预测"完成：CSR 写只约束此后的预测生成，不撤回已发射预测，`sbpctl` 不是隔离 fence。该窗口在三代表现一致（v3 修复版 C9683→9705、v2 C9753→9784），属于语义边界，建议在文档中明示；对需要硬隔离的场景需要 fence + 预测器冲刷语义（超出本 issue 范围）。
4. v2 无 ABTB 且预测器输出 gate 完好；v2 的复现价值在于提供了"gate 完好时同一 PoC 的行为基线"，反证 v3 基线由陈旧输出直接驱动的泄露路径正是 #6137 的实体。

## 6. 建议修复方案（评估）

`2a4721ab8 + dcdf1c1c9` 已覆盖四层框架的第 1/3 层与依赖图传播，与 #6461（RAS 维度）合流为同一条 PR 链，方向正确。补充建议：

1. predCtrl 输出路径：`io.predCtrl.jumpValidVec/conditionValidVec` 未随输出 gate（S1 `s1_abtbTakenMask` 的输入），本 PoC 未观察到其活动，建议按第 3 层 invariant 一并 gate 或说明豁免理由；
2. 断言可以保留：Bpu 的 abtb 输出一致性断言在基线上准确捕获了本缺陷（作者树 `Bpu.scala:556`），修复后自然静默，是这条 PR 链有效的回归哨兵；
3. 语义文档：`sbpctl` 各 enable 位应文档化"非 fence"语义（disable 不撤回在途预测），避免将其误用作安全隔离原语——这一点同时适用于 v2/v3 与 #6149 的 RAS 位。

## 参考

- Issue #6137: ABTB can emit stale predictions after `sbpctl` disables ABTB and drive transient I/D-cache accesses（含 PoC 附件 `xiangshan-abtb-disable-stale-prediction-poc.zip`、`security_evidence.json`、`normal_assert_run.log`；附件实证构建树 `3931c5112c dirty:1`，2026-05-18，#5969）
- 修复链：`fix-bpu-enable` @ `2a4721ab8`（直接修复）、`dcdf1c1c9`（互联 enable，即 #6461 PR head）
- 同族：#6149（RAS disable，分析文档见 https://github.com/OpenXiangShan/XiangShanLab/blob/master/xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6149/ ）、#6159（uBTB/aBTB enable）
- 本报告复现环境：v3 作者树 `3931c5112c` 与修复版 `dcdf1c1c9`（Verilator emu，`--no-diff --dump-wave-full`），v2 `0fa7bb825`；PoC 双 secret 规范化与 monitor/trace 工具见 delivery 各目录
