# 香山 Issue #6135 分析报告：`sbpctl.RAS_ENABLE=0` 被无视于 return 预测，构成 secret 依赖的时序侧信道

Issue：[#6135 RAS disable control is ignored for return prediction and leaves a secret-dependent timing side channel](https://github.com/OpenXiangShan/XiangShan/issues/6135)（2026-06-25，YzhDDDing 报告）

复现基线：

| 处理器 | 提交 | 说明 |
| --- | --- | --- |
| kunminghu-v3 | `e85a929a3`（2026-06-26，issue 次日） | issue 未声明 commit；该提交的缺陷代码与 issue 引用逐行一致 |
| kunminghu-v2 | `0fa7bb82` | 上一代基线，验证缺陷类是否同样成立（V2 部分为本报告独立工作，见 §5） |

## 1. 摘要

现代乱序处理器用**返回地址栈（RAS, Return Address Stack）**预测函数返回：`call` 把返回地址压栈，`ret` 弹栈取目标。与所有分支预测器一样，RAS 可能被投机执行污染，因此香山提供了一个软件开关——自定义 CSR `sbpctl`（地址 `0x5c0`）的 `RAS_ENABLE`（bit 6），文档语义 "Enable the return-address stack predictor"。安全敏感代码（如密钥处理段）的合理用法是：**执行前清零这一位，让 RAS 不再影响取指，从而隔离预测器状态**。

Issue #6135 报告并证明：这个开关**不生效**——而且后果不止于多余的错误路径取指。本报告在两代香山基线上独立复现了完整的 Spectre-RSB 类攻击链，四个运行（两代 × 双 secret）全部端到端成立：

1. 内存中的一个 secret bit 选择两个不同的 call site，`jal` 把 secret 相关的返回地址留在"已禁用"的 RAS 栈中；
2. 随后一条架构上应返回 `common_after` 的 `ret`，被禁用的预测器预测为 secret 选定的返回地址——**必然误预测**（架构目标由 `ra` 决定，与栈顶不同）；
3. 前端对错误路径发出真实 fetch，gadget 被译码、派遣；
4. **乱序核在 `ret` 退休前投机执行了 gadget 的 probe load**，被 secret 选中的 cache line 完成填充（DCache A 通道请求有波形证据；AcquireBlock 即向下一级存储请求填充 cache line）；
5. 后端解析 `ret` 后 redirect 冲刷错误路径，**架构结果始终正确**；
6. 测量段用 `rdcycle`（读周期计数器）给两条 probe line 的访问计时：**被填充的线显著更快**（V3：48 vs 161 拍；V2：42 vs 144 拍，均超过 3 倍差），程序据此走进 `hit_loop`/`miss_loop` 分支并把时序差写入内存——**secret 由程序自身的架构输出直接读出**。

**结论要点**：

- 这是**微架构隔离失效**，不是架构功能错误——程序结果正确，危害在保密性：软件显式关闭的预测器仍在泄露信息。
- 根因（§2）：`RAS_ENABLE` 只存在于控制平面，没有贯穿到 RAS/µRAS 的**状态更新**和 **next-PC 数据通路**。
- 两代泄露路径不同（§4、§5）：V3 在 BPU 预测级（S1 µRAS 与 S3 主 RAS 的目标选择均不检查 enable）；V2 预测级有 gate，泄露经 IFU predecode RET redirect（RAS 栈顶的旁路副本无 gate 地替换 redirect target）。**只堵住一个消费点不足以封堵这类缺陷。**
- 修复状态：#6135 无修复 PR（维护者回复"待 V3 设计稳定后重新审视"）。针对同一根因的 PR #6461（Fixes #6149）修复了 V3 的 S3 主 RAS 路径，但 S1 µRAS 残留、且不覆盖 V2；残留修复完成前，两代处理器上 `sbpctl.RAS_ENABLE` 都不能当作隔离原语使用（§7）。

触发条件（两代一致）：(a) 清零 `RAS_ENABLE`、其余预测器保持使能——return 的识别依赖 BTB 元数据；(b) secret 选择的 call 把返回地址留在栈中；(c) 随后一条真正的 `ret` 且架构目标与栈中地址不同；(d) 错误路径上的 gadget 访问 secret 选定的 cache line，且该线测量前处于未命中状态。

## 2. 缺陷分析

### 2.1 V3 的两级 return 预测

V3 BPU 是多级覆盖预测结构，return 目标的预测有快慢两条路径：

```text
快路径（S1）                          慢/准路径（S3）
uBTB / aBTB 发现 RET                  mBTB + TAGE/... 确认 RET
      |                                     |
      v                                     v
   MicroRAS (µRAS)                        主 RAS
   （S1-S3 流水的影子/转发栈：            （S3 speculative 栈：
     主 RAS 要到 S3 才更新，               按 S3 最终预测 push/pop）
     µRAS 提前算出"当流水里这些
     call/ret 生效后栈顶是什么"）
      |                                     |
      v                                     v
   早期 return target                  晚期 return target（可覆盖 S1）
      +------------ S3 override ------------+
                          |
                          v
                    FTQ → IFU fetch
```

这个结构意味着：disable 语义必须同时约束**两个**预测器（µRAS 与主 RAS）、**两类**状态（speculative 栈与影子跟踪）、**两个**消费点（S1/S3 的目标选择）。基线 RTL 三处都没做。

### 2.2 三层缺失的 gate（行号在 `e85a929a3` 上逐一核实，与 issue 引用一致）

**第一层——接线**（`frontend/bpu/Bpu.scala`）：

```scala
uras.io.enable := true.B         // :96  µRAS 使能被硬连为常量
ras.io.enable  := ctrl.rasEnable // :113  主 RAS 接了线，但模块内部从不使用
```

**第二层——状态更新**：`ras/Ras.scala` 的三类状态变化——S3 speculative push/pop（:66-76）、redirect 恢复（:100）、commit 训练（:107-110）——均不检查 `io.enable`；`ras/MicroRas.scala` 全文无一处 `io.enable`。禁用期间 call/return 照常改写栈内容。

**第三层——最终消费点（next-PC 数据通路）**：

```scala
// S1（:298，aBTB :332 同构）：只判 isReturn && isCanUse，不查 enable
Mux(ubtb...isReturn && uras.io.specOut.isCanUse, uras.io.specOut.retTarget, ...)

// S3（:404/:422）：s3_useRas 不含 enable，主 RAS 栈顶直接胜出
private val s3_useRas = s3_firstTakenBranch.bits.attribute.isReturn
(s3_taken && s3_useRas) -> ras.io.topRetAddr
```

被禁用的 RAS/µRAS 输出可以直接成为预测目标并经 FTQ 驱动取指。**最终 next-PC 选择是控制流安全的最后一道保险，这里恰好没有设防。**

值得对比：TAGE 一类表结构预测器有内建的 hit/valid 输出，可以在模块内做 gate；RAS 是栈结构，没有天然的 "hit" 概念——这解释了为什么这个缺陷在两代实现里都以"消费点不检查 enable"的形式出现。

### 2.3 V2 的对应缺陷

V2 无 µRAS，S2/S3 `jalr_target` 覆盖**有** `ras_enable` gate（`newRAS.scala:630/660`），状态更新同样无 gate；但 RAS 栈顶还通过 `last_stage_spec_info.topAddr`（无 gate）写入 FTQ 的 `ftq_redirect_mem`，当 IFU predecode 写回发现 RET 时，FTQ 直接用它替换 redirect target（`frontend/NewFtq.scala:1143-1145`）——同一缺陷类经第三条消费路径出现。§5 的 V2 波形证实这条路径实际驱动了完整攻击链。

### 2.4 这类缺陷的通用检查框架

"禁用某微架构预测器"的 CSR 位要真正生效，沿数据流有四层不变量需要成立（本 case 的缺失项加粗）：

| 层 | 不变量 | 状态 |
| --- | --- | --- |
| 1. 请求/活动 gate | disable 后是否仍有 lookup、流水 fire | BTB 等仍使能（本 PoC 的前提而非缺陷） |
| 2. 状态更新 gate | speculative 更新、训练、redirect 恢复是否全部停止 | **缺失**（V3/V2 均是） |
| 3. 输出有效 gate | `hit/valid/isCanUse` 是否在 disable 后强制失效 | **缺失**（µRAS 的 `isCanUse` 不受 enable 影响；RAS 无 hit 概念也未设等价物） |
| 4. 最终消费点 gate | next-PC 选择是否绝对禁止其成为控制流来源 | **V3 缺失**（S1/S3 mux）；**V2 只做了预测级一处，漏了 `topAddr` 旁路** |

第 4 层是最后的硬保险：预测器内部状态出错尚可容忍，next-PC 不能出错。另一个普遍教训是**预测器不是彼此独立的**——µRAS 依赖主 RAS 栈顶、TAGE 依赖 mBTB 提供分支候选，真正的开关语义必须沿这条生产者/纠错者依赖图传播，把模块树当成可独立开关的清单正是这类缺陷反复出现的根源（维护者对同类的 #6149/#6159 亦作过相同归类）。

## 3. PoC 与攻击链

PoC 为 issue 作者附件 `xiangshan-ras-disable-sidechannel-poc.zip` 中的 `ras_disable_sidechannel.S`（编译期参数 `SECRET_BIT` 0/1 生成两个变体）。V2 版为本报告移植，仅两处适配（§5.2）。

### 3.1 布局

链接基址 `0x80000000`，两代符号布局完全相同（交付包含 objdump 可核对）：

| 符号 | 地址 | 作用 |
| --- | ---: | --- |
| `_start` 热身段 | `0x8000001c`-`0x800000d0` | 16 次 `a0=0` 的直通 call/ret |
| evict_loop | `0x800000e0`- | 驱逐两条 probe line（步长 4096 保持 set index） |
| `attacker_context_secret{0,1}` | `0x80000140` / `0x80000b00` | 15 个 nop 填充，call 位于 fetch block 尾部 |
| `secret{0,1}_call` | `0x8000017c` / `0x80000b3c` | `jal ra, victim_context_entry`（压栈点） |
| `secret{0,1}_ret_site` | `0x80000180` / `0x80000b40` | 80 个 nop（错误路径缓冲） |
| `secret{0,1}_gadget` | `0x800002c0` / `0x80000c80` | 3 条 probe load + 跳死循环 |
| `victim_context_entry` | `0x80000cc0` | `a0=1` 时：禁用 RAS → divu 链 → ret |
| （禁用点） | `0x80000cc8` | `csrw 0x5c0, 0x3f`（只清 bit6） |
| divu 链 | `0x80000cdc`-`d3c` | 25 条 `divu`，ra := common_after |
| `victim_actual_ret` | `0x80000d40` | 被攻击的 `ret` |
| `common_after` | `0x80000d44` | 架构目标；等待 + 测量 |
| `measure_probe{0,1}` | `0x80000d60`/`d80` | `rdcycle` 计时 load |
| `hit_loop`/`miss_loop` | `0x80000dac`/`da4` | 架构可见结果 |
| `result_delta{0,1}` | `0x80001000`/`0x80001008` | 时序差写入内存 |

probe 地址：`probe0 = 0x80020000`、`probe1 = 0x80020040`（相邻两条 cache line，分别由两个 gadget 触碰）。

### 3.2 攻击链

```text
热身（RAS 使能）：16× call/ret，把 victim 的 ret 训练进 BTB 元数据
        |
驱逐：把 probe0/probe1 两条 line 踢出 DCache，fence
        |
seed：secretN_call: jal ra, victim        <-- secret 相关返回地址入栈（secret bit 选定）
        |
victim: csrw 0x5c0, 0x3f                  <-- RAS "禁用"
        divu ×25：ra := common_after       <-- 架构目标 ≠ 栈顶 → 必然误预测；
        |                                     除法延迟数百拍 = 投机窗口
ret
        |  预测（被禁用的 RAS/µRAS）：secretN_ret_site
        v
FTQ/IFU 真实 fetch secretN_ret_site（80 nop）→ secretN_gadget   [fetch 证据]
        |  译码、派遣                                               [前端流水证据]
        v
乱序核在 ret 退休前执行 gadget 的 probe load(secretN line)        [LoadUnit + DCache A 通道证据]
        |
后端解析 ret（目标 common_after）→ redirect 冲刷错误路径          [架构结果正确]
        |
等待 + fence → rdcycle 量 probe0/probe1                          [时序差证据]
        |  被填充线 V3 48 拍 / V2 42 拍，未填充线 V3 161 / V2 144
        v
hit_loop/miss_loop + 时序差写入 result_delta0/1                   [secret 架构可读]
```

### 3.3 每个设计步骤为什么必要

- **热身要在 RAS 使能时做**：`ret` 的 return 属性来自 BTB 元数据，首次执行的 `jalr` 未必被标注为 return；先把 victim 的 ret 训练进 BTB，攻击阶段它才会被稳定识别为 return 并触发 RAS 查询（这也解释了触发条件 (a)：其余预测器必须保持使能）。
- **压栈在禁用之前、`csrw` 之后才 `ret`**：这构成对修复的一个夹逼检验——只修状态更新 gate（禁用后不再 push/pop）不够，**禁用前入栈的既有内容仍会经消费点缺陷泄漏**；反之只修消费点也挡不住禁用期间的状态积累。
- **divu 链一石二鸟**：架构上把 `ra` 算成 `common_after`（保证与栈顶不同、必然误预测）；25 条除法各数十拍，把投机窗口拉到数百拍，让错误路径完整走完 fetch→译码→派遣→执行。
- **gadget 内 3 条对同一地址的 load**：提高 cache 填充的可靠性，随后跳死循环等待冲刷。
- **`.balign 64` + 512 nop 隔离**：call 放在 fetch block 末尾使返回地址落在下一对齐块，两组 secret 块之间垫 512 nop 防顺序预取串扰——错误路径的 fetch 足迹可以干净地归因到被选中的那一个 secret（判据要求"未选中的 ret_site 未被 fetch"）。
- **等待循环 + fence 再测量**：给错误路径发出的 fill 请求留出完成时间，fence 隔离测量的访存乱序。

### 3.4 判据（oracle）

判定脚本（作者 monitor，交付包含两代版本）从波形提取四层证据，全部通过方为 `success`：

| 层 | 波形证据 | 对应判据（节选） |
| --- | --- | --- |
| 禁用生效 | CSR 写端口采样（`0x5c0`=0x3f）与 `ras_enable` 寄存器值 | `csr_disable_seen`、`ras_enable_zero_seen` |
| 禁用态预测 | µRAS/主 RAS 在 `ras_enable=0` 期间输出 secret 选定 ret_site | `disabled_ras_prediction_to_expected_ret_site` |
| 错误路径执行 | FTQ fetch（ret_site 与 gadget）、译码、派遣、LoadUnit S1、**DCache A 通道 AcquireBlock 到 secret 选定 probe 地址** | `expected_*_ftq_fetch/decode/dispatch/probe_load/dcache_probe_request` |
| 时序差与结果 | `rdcycle` 前后差被写入 `result_delta0/1`、selected probe 更快、`hit_loop` 提交、恢复 redirect 到 `common_after` | `rdcycle_deltaN_observed`、`selected_probe_faster_than_other`、`hit_loop_seen`、`victim_return_redirected_to_common_after` |

另有反向判据 `unexpected_ret_site_ftq_fetch_after_prediction` 要求为 false（未选中的 secret 不得留痕），保证泄露归因干净。

## 4. V3 复现结果（`e85a929a3`，双 secret）

### 4.1 判据总表

21 项判据全部按预期成立（20 项为真，1 项要求为 false 的确为 false），两 secret 的端到端判定均为 `success=true`。`rdcycle` 实测：

| | probe0（delta0） | probe1（delta1） | 程序走向 |
| --- | ---: | ---: | --- |
| secret=0 | **48** | 161 | hit_loop |
| secret=1 | 161 | **48** | hit_loop |

两次运行唯一的输入差异是编译期 secret bit，被填充的 line、测量分支与两个落盘值全部随之翻转。

### 4.2 泄露时间线（secret=0；time 为波形时间，= 2 × cycle）

| time / cycle | 事件 |
| --- | --- |
| 9796-98 / C4898 | victim 写 `sbpctl=0x3f`，`ras_enable` 1→0 |
| 9848 / C4924 | **S1 µRAS 预测 ret → `secret0_ret_site`**（`isCanUse=1`，全程 `ras_enable=0`） |
| 9850 / C4925 | FTQ 分配并 fetch `secret0_ret_site`（idx=28）——错误路径 fetch 发出 |
| 9852 / C4926 | S3 主 RAS pop 同一地址（同样无 enable gate） |
| 9860 / C4930 | 顺序 fetch 穿越 80 nop 到达 `secret0_gadget`（idx=33） |
| 10056-66 / C5028-33 | gadget 译码、派遣 |
| 10076-82 / C5038-41 | probe0 load 进入 LoadUnit S1；**DCache A 通道发出 AcquireBlock(0x80020000)**——乱序执行先于 ret 解析 |
| 10348 / C5174 | 后端解析 `ret`（ra=common_after）redirect 恢复；错误路径窗口 ≈249 cycle |
| 10916–11440 / C5458-5720 | 测量代码提交 → delta0=48、delta1=161 落盘 → `hit_loop` 提交 |

secret=1 完全对称（µRAS 预测 `secret1_ret_site` 于 t=9822，delta1=48）。

### 4.3 与 issue 作者数据的逐拍对照

作者在其环境（5 月构建的 emu）的 monitor 数据与本报告独立复现，对齐预测事件后逐项对比：

| 事件（相对 µRAS 首次预测的 tick 数） | 作者 | 本报告 |
| --- | ---: | ---: |
| S1 µRAS 预测 secret0_ret_site | 0 | 0 |
| S3 主 RAS pop → 同一地址 | **+4** | **+4** |
| FTQ fetch ret_site | +2 | +2 |
| FTQ fetch gadget | +12 | +12 |
| gadget 译码 | +210 | +208 |
| gadget 派遣 | +216 | +218 |
| probe load S1 | +226 | +228 |
| DCache A 通道请求 | +232 | +234 |
| victim ret redirect 恢复 | +498 | +500 |
| `hit_loop` commit | +1584 | +1592 |

相对时序 ±8 tick 逐拍吻合，泄露链结构（µRAS 先行、主 RAS 4 tick 后跟进）完全一致；命中线时序差与作者完全相同（48），缺失线 161 vs 作者 156（访存模型版本差异，量级一致）。

## 5. V2 迁移与复现（本报告的独立工作）

issue 及作者 PoC 仅针对 kunminghu-v3；V2 上的验证——PoC 移植、判据脚本移植、泄露路径确认——为本报告独立完成。V2 与 V3 的微结构差异足以让"同一缺陷在上一代是否同样可利用"成为一个独立问题：V2 没有 µRAS，预测级的 RAS target 覆盖有 `ras_enable` gate，如果只看 BPU 预测级，V2 似乎是安全的——结果证明泄露从另一条路径完整成立。

### 5.1 V2 与 V3 的关键差异（迁移时逐项核对）

| 维度 | V3（`e85a929a3`） | V2（`0fa7bb82`） |
| --- | --- | --- |
| BPU 结构 | uBTB/aBTB(S1) + mBTB/TAGE/SC/ITTAGE/RAS(S3)，µRAS 为 S1 影子栈 | uFTB/FTB/TAGE-SC/ITTAGE/RAS，无 µRAS，单一 newRAS |
| `sbpctl` 位布局 | bit6=RAS，"只关 RAS" 写 `0x3f` | **bit5=RAS、bit6=LOOP，写 `0x5f`**；直接沿用 `0x3f` 会关掉 LOOP 而 RAS 仍使能，实验静默无效 |
| 预测级 RAS target gate | 无（S1/S3 mux 均不检查 enable） | **有**（`newRAS.scala:630/660`） |
| RAS 状态更新 gate | 无 | 无（禁用期间 speculative 栈照常 push/pop，与 V3 同病） |
| 实际泄露路径 | BPU 预测级（S1 µRAS / S3 主 RAS） | **FTQ 的 IFU predecode RET redirect**：栈顶经 `last_stage_spec_info.topAddr`（无 gate）存入 `ftq_redirect_mem`，predecode 发现 RET 时直接替换 redirect target（`NewFtq.scala:1143-1145`） |
| 波形地址编码 | `PrunedAddr`（低位裁剪，匹配时 `>>1`） | 完整 vaddr |
| L1 DCache 几何 | 64 组 × 64B line | 相同（32KB/8-way/64B）——PoC 的驱逐步长 4096 原样适用 |
| 启动 | 直接从镜像起 | flash 引导（约 1400 cycle）后跳转 `0x80000000` |

### 5.2 PoC 移植

V2 版 `ras_disable_sidechannel_v2.S` 相对 V3 原版只有两处有意改动，其余逐指令一致（符号地址逐一相同，可直接 diff 核对）：

1. **`csrw` 写值 `0x3f` → `0x5f`**：按 V2 位布局只清除 bit5（RAS_ENABLE），保留其余预测器。
2. **新增 mtvec 处理器（park 循环）**：裸机环境下若错误路径 fetch 到未映射内存，trap 会落到复位值 `mtvec=0` 并在 PC 0 反复 fault，表现为前端假挂死（两代均复现过该现象，属测试代码缺陷而非 RTL 问题）。处理器只负责停靠，不改变任何攻击语义——本 PoC 的错误路径全程在映射区域内，该加固是纯保险。

攻击序列本身不需要任何改动：种栈 → 禁用 → `divu` 链拉开投机窗口 → `ret` 误预测 → gadget probe load → 测量，在 V2 上按原样生效。

### 5.3 判据移植（v2 monitor）

作者 monitor 按 V3 波形信号写死，不能直接用于 V2。本报告将其判据族逐一移植到 V2 的信号名（`monitor_v2_ras_disable_sidechannel.py`），事件名与 V3 版保持一致以便对表：

| 判据层级 | V3 信号（作者版） | V2 信号（移植版） |
| --- | --- | --- |
| 禁用生效 | CSR 写端口采样 + `sbpctl.regOut` | `bp_ctrl_ras_enable` 的 1→0 边沿（写值 bit5/`0x5f`） |
| 禁用态预测 | µRAS `specOut` / 主 RAS `io_spec_popAddr`（PrunedAddr） | **FTQ→BPU redirect 的 `cfiUpdate`：`pd_isRet=1` 且 target=secret 选定 ret_site**（完整 vaddr），辅以 `RASStack.io_spec_pop_valid` 与栈顶 `s3_top` |
| 错误路径 fetch | `io_frontend_fromFtq_wen/startPc` | `ftq.io_toIfu_req_valid/startAddr` |
| 译码/派遣 | ctrlBlock 8 槽 decode/dispatch | `ctrlBlock.decode.io_out_N`（6 槽）/ `ctrlBlock.dispatch.io_enqRob_req_N` |
| probe load | `lsTopdownInfo` 3 通道 | `LoadUnit_{0..2}.io_lsTopdownInfo_s1_vaddr` |
| DCache 请求 | `dcache.client.out.a_*`（TL-A） | 同族信号（`auto_client_out_a_*`，AcquireBlock=opcode 6） |
| 结果提交 | difftest `endpoint.commit/store` | 同族信号（commit 8 槽、store 3 槽，含 `io_bits_data` 读出落盘 delta） |

移植中发现一处 V2 特有语义：后端解析 `ret` 的恢复 redirect，其 `cfiUpdate_pc` 报告的是错误路径上最后一条指令的 PC（而非 `ret` 自身），且可携带 `pd_isRet=1`——因此恢复事件按"redirect target == `common_after`"判定（本 PoC 中到达 `common_after` 的 redirect 唯一，与泄露 redirect 的 target 无歧义）。另外 v2 版的 `success` 合成条件比作者 V3 版更严：21 项判据必须全部按预期成立，而非作者的子集。

### 5.4 结果与泄露时间线（secret=0）

| time / cycle | 事件 |
| --- | --- |
| 18052 / C9026 | 写 `sbpctl=0x5f`（bit5），`ras_enable` 1→0 |
| 18152 / C9076 | **IFU predecode RET redirect：`cfiUpdate.target` 被 RAS 栈顶替换为 `secret0_ret_site`（0x80000180）**（`pd_isRet=1`）——`NewFtq.scala:1143-1145` 路径 |
| 18158 / C9079 | FTQ→IFU fetch `0x80000180`（idx=34） |
| 18230 / C9115 | 顺序 fetch 穿越 80 nop 到 `secret0_gadget`（idx=44） |
| 18260-66 / C9130-33 | gadget 译码、派遣 |
| 18276-80 / C9138-40 | probe0 load 进入 LoadUnit S1；**DCache A 通道发出 AcquireBlock(0x80020000)**——乱序执行先于 ret 解析 |
| 18560 / C9280 | 后端解析 `ret` → redirect `common_after`；错误路径窗口 ≈201 cycle |
| 19208–19520 / C9604–9760 | delta0=42、delta1=144 落盘 → `hit_loop` 提交 |

secret=1 对称（redirect 目标 `0x80000b40` 于 t=18188，delta1=42）。结果可复现到拍：两次独立运行的禁用/泄露时间戳完全相同（secret0：18052/18152；secret1：18088/18188）。

### 5.5 被排除的路径与结构性结论

- **被排除**：V2 的 S2/S3 `jalr_target` 覆盖在全部运行中保持 gated（`ras_enable=0` 期间无一次由 RAS 驱动预测级 target）；全部泄露事件落在 IFU predecode RET redirect 路径——预测级有 gate 不等于安全，RAS 输出的**旁路副本**（`topAddr` → `ftq_redirect_mem`）同样是 next-PC 来源。
- **结构性结论**：两代以不同消费点实现同一缺陷类（V3：预测级 mux；V2：redirect 级替换），说明这不是某一代的笔误，而是"有状态预测器的输出面有多个消费点、disable 语义必须覆盖每一个 next-PC 来源"的结构性问题。仅修复 BPU 预测级的 mux（如 V2 已做、如 #6461 对 V3 S3 所做）不足以封堵该缺陷类。

## 6. 证据边界

本报告的证明终点是：secret bit → 错误路径 fetch → 乱序 probe load → DCache line 填充 → `rdcycle` 时序差 → 架构可见分支与内存落盘，每级均有波形时间戳与程序自有输出双重证据。周期级仿真的访存时序与真实 ASIC 存在差异（缺失线时序差 V3 161 拍 vs 作者环境 156 拍），但命中/缺失的相对差超过 3 倍，远大于该误差，结论稳健。本报告不涉及跨核、跨特权级等更强攻击者模型的论证。

## 7. 结论与建议

1. **缺陷定性**：`sbpctl.RAS_ENABLE` 只改写控制平面的寄存器值，未贯穿到 RAS/µRAS 的状态更新与 next-PC 数据通路；被禁用的 RAS 持续积累 secret 相关状态并驱动取指流分叉。危害为微架构隔离失效（Spectre-RSB 类侧信道），非架构功能错误。
2. **两代基线均端到端成立**：V3（S1 µRAS + S3 主 RAS 预测级路径，48 vs 161 拍）、V2（IFU predecode RET redirect 路径，42 vs 144 拍）。V2 结果同时表明：仅修复预测级 mux 不足以封堵该缺陷类，RAS 输出的每一个 next-PC 来源（含 `topAddr` 旁路副本）都必须受 enable 约束。
3. **修复状态与建议**：PR #6461（Fixes #6149）对 V3 S3 主 RAS 路径的修复完整（状态更新、消费点、端到端 fetch 三层验证），但 S1 µRAS 仍有残留（`MicroRas.scala` 不消费 `io.enable`，S1 目标选择不检查 enable，禁用期间仍观测到 secret 相关预测事件），V2 路径不受该 PR 影响。建议：补齐 µRAS 输出面与 S1 消费点的 gate；V2 如需长期维护，应同样处理 `NewFtq.scala` 的 `topAddr` 消费点。
4. **对软件使用者**：上述修复完成前，不要依赖 `sbpctl.RAS_ENABLE` 做隔离（如密钥相关代码段）；应假设 RAS 始终参与预测，采用配对 call/ret、返回栈清洗等软件对策。

## 8. 交付物清单与复现方法

```text
analysis.md            本报告
v3/                    kunminghu-v3（e85a929a3）复现
  poc/                 PoC 源码与编译产物、复现脚本、monitor、summarize
  waves/               双 secret 波形（focused VCD，xz 压缩）
  results/             monitor 判据 JSON/log、运行日志、objdump、pair_summary
v2/                    kunminghu-v2（0fa7bb82）复现：同构
v3.zip / v2.zip        两个复现目录的打包（内容与散装目录一致）
```

波形说明：`waves/` 内为**聚焦证据信号集**的 VCD（xz 压缩后每个仅 ~20 KB，解压后约 0.5 MB，包含判据所需的全部信号与完整时间网格；monitor 可直接解析复核，交付前已复核其判据结果与全量波形完全一致）。全量 SoC 波形（数百 MB/个）可由 `poc/` 内脚本对相应 emu 重新生成。

复现（V3 需对应提交构建的 Verilator emu，V2 需 kunminghu-v2 emu，路径均可用环境变量 `EMU=` 覆盖）：

```bash
cd v3/poc && bash reproduce_v3_6135.sh     # 双 secret 运行 + monitor + 汇总
cd v2/poc && bash run_v2_sidechannel.sh
```

波形解压后可直接用包内 monitor 复核全部判据；文件校验值见 `README.md`。

## 参考资料

- [XiangShan Issue #6135](https://github.com/OpenXiangShan/XiangShan/issues/6135)（含作者 PoC 附件 `xiangshan-ras-disable-sidechannel-poc.zip`）
- [XiangShan Issue #6149](https://github.com/OpenXiangShan/XiangShan/issues/6149)：同一根因的独立报告（2026-09），维护者归类 "Same BP switch problem as #6159"
- [XiangShan PR #6461](https://github.com/OpenXiangShan/XiangShan/pull/6461)：fix(Bpu): enable & debug-related fix & cleanup（Fixes #6149）
- V3 源码：OpenXiangShan/XiangShan @ `e85a929a3`；V2 源码：@ `0fa7bb82`
