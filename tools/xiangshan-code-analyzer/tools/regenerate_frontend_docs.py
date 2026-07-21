#!/usr/bin/env python3

from __future__ import annotations

import re
from pathlib import Path


WORKSPACE = Path(__file__).resolve().parent
TARGET = Path(
    "/nfs/home/yuanmiaomiao/XiangShanLab/xiangshan-course/docs/"
    "课程体系4：实现篇-香山高性能处理器微架构优化/"
    "中级-高性能香山处理器代码深入解析"
)
GENERATED = TARGET / "generated_frontend_docs"
OUTPUT = TARGET / "regenerated_frontend_docs"
BRANCH = "kunminghu-v2"
COMMIT = "52262f303fc06daf84cdab7011d59b7df65ce7e8"
BASE = f"https://github.com/OpenXiangShan/XiangShan/blob/{BRANCH}/"


INTRO = {
    "Bim": """## 统一五问导读

| 问题 | 回答 |
| --- | --- |
| **Who** | 旧 `BIM` 文件描述独立 bimodal 方向预测器；当前有效硬件是 `Tage.scala` 内的 `TageBTable`。 |
| **What** | 以 PC 索引 2-bit 饱和计数器，为 TAGE 提供无 tag 的基础/备用方向。 |
| **How** | S0 计算 bank/index 并读表，S1 选出各分支槽计数器；update 使用 `WrBypass` 解决同地址读后写并做饱和增减。 |
| **From what** | 查询 PC 来自 BPU 公共预测请求；训练来自 FTQ 在分支提交后形成的 `update`。 |
| **To what** | 输出进入 TAGE provider/alternate 选择；结果变化由 BPU 的 S2/S3 比较转换为 redirect。 |

## 状态机与论文理论

独立 `Bim.scala` 在本 commit 中整体被块注释，因此没有有效 FSM；当前 `TageBTable` 使用“复位扫描 / 可查询 / 更新冲突”隐式生命周期。经典理论来自 James E. Smith 的 *A Study of Branch Prediction Strategies*：2-bit 计数器只有连续相反结果才会跨过强/弱状态边界，减少循环退出一次造成的长期翻转。香山的具体 bank、端口冲突和旁路规则以源码为准。

## 示例讲解

若某 PC 的计数器为 `10`（弱 taken），一次 not-taken 更新后变成 `01`（弱 not-taken）；下一次相同 PC 查询时，若没有更长历史 tagged provider，TAGE 可使用这个基础方向。若 update 与查询命中同一 SRAM 地址，`WrBypass` 提供最新值，避免读取旧计数器。
""",
    "BPU": """## 统一五问导读

| 问题 | 回答 |
| --- | --- |
| **Who** | `Predictor`/BPU 是所有子预测器、全局历史、下一 PC、redirect 和 FTQ 握手的总控。 |
| **What** | 组织 S0-S3 多级覆盖预测，把 `FauFTB → Tage_SC → FTB → ITTAGE → RAS` 的组合结果变成 FTQ prediction block。 |
| **How** | 早级先追求低延迟，晚级比较方向、CFI、target、fall-through 和 multi-hit；不一致时产生 S2/S3 redirect，并修复历史。 |
| **From what** | 输入来自 reset vector、CSR 控制、FTQ ready、后端 redirect、FTQ commit update。 |
| **To what** | 输出到 FTQ；历史/恢复控制广播到 Composer 中所有预测器；redirect 反馈给 FTQ/IFU/ICache。 |

## 状态机与论文理论

BPU 没有单一 `Enum` FSM，而由 S0/S1/S2/S3 valid、ready、fire、redirect 和历史指针构成隐式流水状态机。理论上属于 decoupled/elastic instruction fetching：快速预测先维持带宽，较慢但准确的预测在后级覆盖，错误代价由局部 redirect 限制。Design Doc 引用 Reinman 等人的 scalable frontend 与 Perais 等人的 elastic instruction fetching；香山实际覆盖条件以代码比较向量为准。

## 示例讲解

FauFTB 在 S1 预测顺序执行；TAGE 在 S2 发现 slot0 taken，FTB 同时给出目标。BPU 比较 S1/S2 的 taken mask、最后 taken 分支和 target，产生 S2 redirect。若 S3 的 ITTAGE 又把 JALR target 改成另一地址，则再产生 S3 redirect；FTQ 只保留最终正确的年轻边界。
""",
    "FauFTB": """## 统一五问导读

> 用户写作 `FeuFTB`，官方源码模块名为 **`FauFTB`**，本文按真实类名分析。

| 问题 | 回答 |
| --- | --- |
| **Who** | `FauFTB` 是预测器链最前面的快速 fetch-target predictor，也常被称作 uFTB/fast FTB。 |
| **What** | 用较小、低延迟的表保存预测块入口、分支槽、target 和 fall-through，尽早给 S1 下一 PC。 |
| **How** | PC 索引并行匹配表项；命中后构造 `FullBranchPrediction`；提交 update 时命中修改、未命中分配，并处理写旁路/替换。 |
| **From what** | 查询来自 BPU S0 PC；训练来自 FTQ commit update；flush/redirect 来自 BPU 公共控制。 |
| **To what** | 输出先进入 TAGE_SC，并把快速表项/命中信息旁送给后级 FTB 复用和校验。 |

## 状态机与论文理论

FauFTB 主要是 S0 请求、S1 返回、update 三阶段隐式状态机。理论基础是分级 BTB/FTB：小表低延迟覆盖热点控制流，大表稍晚提供容量和准确率。XiangShan FTB 论文 *A design of fetch target buffer implemented on XiangShan processor*（DOI `10.1117/12.2642006`）解释了按 fetch block 保存多个分支信息为何有利于宽取指和时序；FauFTB 的具体组织以源码为准。

## 示例讲解

循环头 PC 连续命中 FauFTB，S1 即得到循环回边 target；若后级 FTB/TAGE 与其一致，不产生覆盖。若新控制流首次出现，FauFTB miss 先走 fall-through，提交后 FTQ update 分配表项，下一次进入该 PC 时即可在早级命中。
""",
    "FTB": """## 统一五问导读

| 问题 | 回答 |
| --- | --- |
| **Who** | `FTB` 是容量更大、信息更完整的 fetch target buffer，位于 TAGE_SC 之后、ITTAGE 之前。 |
| **What** | 以 fetch block 为单位保存条件分支槽、tail jump、target、fall-through、call/ret/JAL/JALR 类型及替换元数据。 |
| **How** | 组相联 tag lookup；多命中用优先选择但标记 `multiHit`；update 时先查旧项，空 way 优先，否则按替换策略分配。 |
| **From what** | 查询 PC、FauFTB 早级 entry/hit、TAGE 修正方向；训练来自 FTQ 保存的旧 FTB entry 与真实提交控制流。 |
| **To what** | 输出完整块预测给 ITTAGE/RAS；target/CFI/fall-through/multi-hit 差异由 BPU 生成 S2/S3 redirect。 |

## 状态机与论文理论

FTB 使用查询流水、update-read、update-write 和两拍 update stall 的隐式状态机。论文 DOI `10.1117/12.2642006` 强调：与“每个 branch 一个 BTB entry”相比，FTB 按取指块组织多个分支，能限制预测宽度、改善时序，并在块内表达第一个 taken 控制流。香山额外保存训练所需的旧 entry，以便增量修改而不是盲目覆盖。

## 示例讲解

一个预测块含两个条件分支和尾部 JAL：TAGE 给出方向，FTB 提供三个槽位及 target。若 slot0 taken，则后续槽位在该次预测中被屏蔽；若真实预译码发现 slot0 实际不是 branch，FTQ 标记 false hit，提交 update 时修复该 FTB entry。若两个 way 同时 tag hit，硬件选择一路继续工作但 `multiHit` 触发 redirect/修复，避免长期不确定。
""",
    "ITTAGE": """## 统一五问导读

| 问题 | 回答 |
| --- | --- |
| **Who** | `ITTAGE` 是针对 JALR/间接分支 target 的 tagged geometric-history predictor。 |
| **What** | 对同一间接分支可能跳向多个目标的情况，用不同长度路径/分支历史选择 target provider。 |
| **How** | 多张带 tag 的历史表并行查询；最长匹配项为 provider，较短匹配为 alternate；target 误预测时更新 provider 并在更长历史表分配。 |
| **From what** | PC、全局/路径历史和 FTB 标出的 JALR 槽位来自 BPU/上游预测；真实 target 与 update meta 来自 FTQ/后端提交。 |
| **To what** | 覆盖 FTB 的 JALR target，再交给 RAS；target 差异由 BPU S3 redirect。 |

## 状态机与论文理论

ITTAGE 没有单一 FSM，使用查询 S0-S3、provider/alternate metadata、update/allocate 和 useful-bit aging 的 entry 生命周期。源码引用 André Seznec 的 *A 64-Kbytes ITTAGE indirect branch predictor*（JWAC-2, 2011）：把 TAGE 的 tagged geometric history 思路从方向预测扩展到 target 预测，用较长历史区分同一 JALR 在不同调用/路径上下文中的目标。

## 示例讲解

虚函数调用点 PC 固定，但对象类型 A/B 导致两个 target。短历史表容易混淆；长历史表若捕获到之前的类型检查分支模式，可命中不同 tag 并给出正确 target。若 provider target 错，提交 update 降低其置信/有用度并尝试在更长历史表分配新 target；下次相同上下文由新 provider 命中。
""",
    "SC": """## 统一五问导读

| 问题 | 回答 |
| --- | --- |
| **Who** | `SC` 是 TAGE 后的 statistical corrector，由 `Tage_SC` 组合模块驱动。 |
| **What** | 汇总多组历史/PC 相关有符号计数器，在 TAGE 不够可靠时翻转或确认其方向。 |
| **How** | 多表读出后形成加权和，与动态阈值比较；训练只在 SC 有价值或置信不足时更新计数器和阈值。 |
| **From what** | 输入包括 TAGE 原始方向/置信、PC、不同历史折叠；训练来自已提交真实方向和预测 meta。 |
| **To what** | 修正后的条件分支方向写回组合 prediction，后续 FTB/ITTAGE/RAS 使用；差异由 BPU redirect。 |

## 状态机与论文理论

SC 以 S0-S3 pipeline valid 和 update 条件构成隐式状态机。O-GEHL（DOI `10.1145/1080695.1070003`）展示了多张几何历史表计数器求和与动态阈值思想；Michaud 的 DOI `10.1145/3226098` 明确指出 TAGE 仍可能被使用相同输入信息的统计校正器显著补强。香山的表组、求和位宽和阈值更新以 `SC.scala` 为准。

## 示例讲解

TAGE provider 弱 taken，但多个 SC 分量对当前 PC+历史给出负贡献，总和越过 not-taken 阈值，于是 SC 翻转结果。若真实结果确为 not-taken，SC 计数器被强化；若翻转错误，则反向训练并调整阈值，减少未来在低收益区域过度覆盖 TAGE。
""",
    "Tage": """## 统一五问导读

| 问题 | 回答 |
| --- | --- |
| **Who** | `Tage` 负责条件分支方向，是 `Tage_SC` 的主预测器；`TageBTable` 提供基础方向。 |
| **What** | 多张 tag 表使用几何增长的历史长度，最长匹配表捕获长相关，短表/基表提供 alternate。 |
| **How** | 折叠历史生成 index/tag；选择 provider/alternate；根据 ctr、useAltOnNa 和 useful bit 得到方向；误预测时在更长表分配。 |
| **From what** | PC、全局历史及 folded history 来自 BPU；真实方向、旧 meta 和分配信息来自 FTQ commit update。 |
| **To what** | 输出条件分支 taken mask 给 SC/FTB；方向改变由 BPU 转成 S2 redirect。 |

## 状态机与论文理论

TAGE 是查询、provider 选择、SC 接续、update/allocation/aging 的隐式表项生命周期。Seznec 的 *A new case for the TAGE branch predictor*（DOI `10.1145/2155620.2155635`）说明 tagged geometric history：从短到长的历史表覆盖不同相关距离，tag 降低 alias，最长命中 provider 优先，alternate 用于新分配/低置信 provider。香山还实现 bank 化、折叠历史、useful 位和周期性清理。

## 示例讲解

某分支每 16 次循环才 not-taken，基表主要学到 taken；短历史表也难区分。长历史 provider 命中包含循环相位的模式并预测 not-taken。若该 provider 刚分配且计数器弱，`useAltOnNa` 可暂时采用 alternate，直到 provider 被真实结果训练稳定。
""",
    "RAS": """## 统一五问导读

| 问题 | 回答 |
| --- | --- |
| **Who** | `RAS`/`newRAS` 是预测器链最后一级，专门预测 return 的 JALR target。 |
| **What** | 保存 call 的 fall-through return address，并维护投机栈、提交栈、重复计数和 redirect 快照。 |
| **How** | taken call 做 speculative push，taken ret 做 pop；S3 cancel 恢复快照并补做漏掉的 push/pop；提交更新 architectural/commit stack。 |
| **From what** | call/ret 类型来自 FTB/预译码，push 地址来自 call fall-through，恢复/提交 meta 来自 FTQ。 |
| **To what** | return target 覆盖普通 JALR target，作为组合预测最终输出；栈快照写入 FTQ 供 redirect 恢复。 |

## 状态机与论文理论

RAS 用指针、计数和 valid 表示隐式 stack 状态机：normal push/pop、S3 cancel repair、backend redirect repair、commit consolidation、near-overflow gating。Skadron 等人的 return-address-stack repair 论文（DOI `10.1109/MICRO.1998.742787`）讨论投机路径污染后的恢复；Park/Lee 的 overflow repair 论文（DOI `10.1145/977091.977139`）说明有限深度栈溢出后不能简单继续覆盖而不修复。

## 示例讲解

递归函数连续 call 同一 return address 时，香山可用 entry 内计数压缩重复地址，而非每次都占新提交栈槽。若错误路径先 push 再 redirect，FTQ 保存的 `ssp/TOSR/TOSW/NOS` 恢复栈；若 spec queue 接近满，`spec_near_overflow` 阻止继续投机 push/pop，避免覆盖仍需恢复的记录。
""",
}


EXTRA = {
    "Bim": """## 表容量、冲突与边界

- Bimodal 表没有 FIFO 式 underflow；未命中 tag 的问题也不存在，因为它是无 tag PC-indexed 表，主要风险是 **alias**。
- SRAM reset 扫描期间 `ready` 被压低，避免读到未初始化计数器；单端口 update/read 冲突时响应 valid 被屏蔽。
- `WrBypass` 容量有限，但它不是预测队列；旁路未命中时读取 SRAM 已提交值，不会数组越界。

```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......" },
    { "name": "req.valid", "wave": "01..0.." },
    { "name": "req.ready", "wave": "10.1..." },
    { "name": "req.pc", "wave": "x=..x..", "data": ["pc0"] },
    { "name": "update.valid", "wave": "0.10..." },
    { "name": "s1_resp_valid", "wave": "0...10." },
    { "name": "counter", "wave": "x...=x.", "data": ["2b10"] }
  ],
  "config": { "hscale": 1 }
}
```
""",
    "BPU": """## 容量与边界补充

- BPU 本身不保存无限 prediction block；FTQ 满时 `io.bpu_to_ftq.resp.ready=0`，S1/S2 前进条件被阻止，payload 必须保持。
- 这属于下游队列 overflow 的反压传播；underflow 表现为没有有效 S0/S1 请求，而不是读取不存在的预测项。
- 子预测器任一 SRAM reset/update 冲突导致 Composer ready 下降，BPU 必须整体停住，防止各预测器 stage 失配。
""",
    "FauFTB": """## 表容量、冲突与边界

- FauFTB 是有限表，容量压力表现为 replacement/alias，而不是 FIFO overflow；新 entry 只能覆盖替换策略选择的 way。
- update 与 lookup 冲突时使用 ready/valid 或写旁路保证不读取半更新 entry。
- 多命中/错误 entry 不允许静默强化：后级比较和提交 update 会修复；miss 时输出 fall-through，不存在 underflow 读取。

```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......" },
    { "name": "s0_valid", "wave": "01..0.." },
    { "name": "s0_pc", "wave": "x=..x..", "data": ["pc0"] },
    { "name": "s1_hit", "wave": "0.10..." },
    { "name": "s1_target", "wave": "x..=x..", "data": ["target0"] },
    { "name": "update.valid", "wave": "0....10" },
    { "name": "redirect", "wave": "0......" }
  ],
  "config": { "hscale": 1 }
}
```
""",
    "FTB": """```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......." },
    { "name": "s0_valid", "wave": "01..0..." },
    { "name": "s1_hit", "wave": "0.10...." },
    { "name": "s2_full_pred", "wave": "x..=x...", "data": ["entry0"] },
    { "name": "multiHit", "wave": "0...10.." },
    { "name": "s3_redirect", "wave": "0....10." },
    { "name": "update.valid", "wave": "0......1" }
  ],
  "config": { "hscale": 1 }
}
```
""",
    "ITTAGE": """## 表容量、分配与边界

- tagged tables 是固定容量；误预测分配若找不到 `u=0` 的候选 entry，会跳过或等待 useful aging，而不能越界写表。
- 无 provider 命中时使用 FTB/alternate target，不存在从空表读取未定义 target 的 underflow。
- update 与 lookup 的 SRAM 端口冲突通过 ready/valid 和 metadata 延迟处理，确保 provider index/tag 与 target 对齐。

```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......." },
    { "name": "s0_valid", "wave": "01..0..." },
    { "name": "s1_match", "wave": "0.10...." },
    { "name": "provider", "wave": "x..=x...", "data": ["T2"] },
    { "name": "jalr_target", "wave": "x...=x..", "data": ["targetB"] },
    { "name": "s3_redirect", "wave": "0....10." },
    { "name": "update.valid", "wave": "0......1" }
  ],
  "config": { "hscale": 1 }
}
```
""",
    "SC": """## 表容量、求和与边界

- SC 表固定容量，主要风险是不同 PC/历史 alias；计数器采用饱和更新，不会数值 overflow/underflow 回绕。
- 动态阈值同样有位宽/饱和边界；达到上下界后保持，避免阈值回绕改变训练方向。
- 无有效 SC 响应或访问冲突时保留 TAGE 方向，不能用未定义求和覆盖主预测器。

```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......." },
    { "name": "tage_valid", "wave": "01..0..." },
    { "name": "tage_taken", "wave": "0=.....x", "data": ["1"] },
    { "name": "sc_sum", "wave": "x..=x...", "data": ["negative"] },
    { "name": "sc_override", "wave": "0...10.." },
    { "name": "final_taken", "wave": "0....=x.", "data": ["0"] },
    { "name": "update.valid", "wave": "0......1" }
  ],
  "config": { "hscale": 1 }
}
```
""",
    "Tage": """## 表容量、分配与边界

- TAGE 每张 tagged table 容量固定；allocation 只选择 provider 之后更长历史表中的可替换项，候选不足时不越界分配。
- useful bit 饱和并周期性清理，防止所有 entry 永久保持不可替换；方向计数器饱和，避免数值 overflow/underflow。
- 无 tagged provider 时回退 `TageBTable`；provider/alternate metadata valid 控制选择，不读取“空 provider”。

```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......." },
    { "name": "s0_valid", "wave": "01..0..." },
    { "name": "table_match", "wave": "0.1....." },
    { "name": "provider", "wave": "x..=x...", "data": ["T3"] },
    { "name": "alternate", "wave": "x..=x...", "data": ["T1"] },
    { "name": "s2_taken", "wave": "0...10.." },
    { "name": "update_alloc", "wave": "0......1" }
  ],
  "config": { "hscale": 1 }
}
```
""",
    "RAS": """```waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p........" },
    { "name": "s2_spec_push", "wave": "01..0...." },
    { "name": "spec_push_addr", "wave": "x=..x....", "data": ["retA"] },
    { "name": "s2_spec_pop", "wave": "0...10..." },
    { "name": "spec_pop_addr", "wave": "x....=x..", "data": ["retA"] },
    { "name": "s3_cancel", "wave": "0.....10." },
    { "name": "redirect_valid", "wave": "0.......1" }
  ],
  "config": { "hscale": 1 }
}
```
""",
}


HEADING_MAP = {
    "Scope": "分析范围",
    "What Exists in the File": "文件中存在什么",
    "Effective Current Replacement": "当前真正生效的替代实现",
    "Algorithm Principle": "算法原理",
    "Algorithm Example Walkthrough": "算法示例推演",
    "Stage-by-Stage Algorithm": "逐流水级算法",
    "Redirect Signal Generation": "Redirect 信号生成",
    "Predictor Relationship": "预测器关系",
    "Paper Context": "论文理论背景",
    "Scenarios": "典型场景",
    "Diagram": "结构图",
    "Interface and Role": "接口与角色",
    "Module Role": "模块角色",
    "State Machine": "状态机",
    "Storage and Capacity": "存储结构与容量",
}


def source_path(label: str) -> str:
    path = label
    if path.startswith("src/main/scala/"):
        return path
    if path.startswith("frontend/"):
        return "src/main/scala/xiangshan/" + path
    if path.startswith("xiangshan/"):
        return "src/main/scala/" + path
    if path.startswith("icache/"):
        return "src/main/scala/xiangshan/frontend/" + path
    if path == "Parameters.scala":
        return "src/main/scala/xiangshan/Parameters.scala"
    return "src/main/scala/xiangshan/frontend/" + path


REF_RE = re.compile(
    r"`?(?P<path>(?:src/main/scala/)?(?:frontend/|xiangshan/|icache/)?"
    r"[A-Za-z0-9_/]+\.scala):(?P<start>\d+)(?:-(?P<end>\d+))?`?"
)


FILE_RE = re.compile(
    r"`(?P<path>(?:src/main/scala/)?(?:frontend/|xiangshan/|icache/)?"
    r"[A-Za-z0-9_/]+\.scala)`"
)


def link_refs(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        label = match.group("path")
        start = match.group("start")
        end = match.group("end")
        anchor = f"#L{start}" + (f"-L{end}" if end else "")
        shown = f"{label}:{start}" + (f"-{end}" if end else "")
        return f"[{shown}]({BASE}{source_path(label)}{anchor})"

    text = REF_RE.sub(repl, text)

    def file_repl(match: re.Match[str]) -> str:
        label = match.group("path")
        return f"[{label}]({BASE}{source_path(label)})"

    text = FILE_RE.sub(file_repl, text)

    lines = []
    for line in text.splitlines():
        linked = re.findall(r"\[([^\]]+\.scala):\d+(?:-\d+)?\]\(([^)]+)\)", line)
        if linked:
            last_label, last_url = linked[-1]
            base_url = last_url.split("#L", 1)[0]

            def bare_repl(match: re.Match[str]) -> str:
                start = match.group("start")
                end = match.group("end")
                shown = start + (f"-{end}" if end else "")
                anchor = f"#L{start}" + (f"-L{end}" if end else "")
                return f"[{last_label}:{shown}]({base_url}{anchor})"

            line = re.sub(r"`(?P<start>\d+)(?:-(?P<end>\d+))?`", bare_repl, line)
        lines.append(line)
    return "\n".join(lines) + ("\n" if text.endswith("\n") else "")


def translate_headings(text: str) -> str:
    for old, new in HEADING_MAP.items():
        text = re.sub(rf"^(##+) {re.escape(old)}$", rf"\1 {new}", text, flags=re.MULTILINE)
    return text


def regenerate_predictor(name: str) -> None:
    src = TARGET / f"Frontend-{name}.md"
    body = src.read_text(encoding="utf-8")
    body = re.sub(r"^# .*?$", f"# Frontend {name} 分支预测器深入分析", body, count=1, flags=re.MULTILINE)
    body = translate_headings(body)
    body = link_refs(body)
    marker = body.find("\n## ")
    if marker == -1:
        marker = len(body)
    header = (
        body[:marker]
        + "\n\n> 官方源码：`https://github.com/OpenXiangShan/XiangShan.git`；"
        + f"分支 `{BRANCH}`；分析 commit `{COMMIT}`。"
        + "论文解释算法原理，源码决定香山的有效参数、流水、更新与恢复。\n\n"
        + INTRO[name]
        + "\n"
    )
    body = header + body[marker:]
    body += "\n" + EXTRA.get(name, "")
    (OUTPUT / f"Frontend-{name}.md").write_text(body, encoding="utf-8")


def regenerate_existing_chinese(src_name: str, dst_name: str, title: str, theory: str) -> None:
    candidates = [GENERATED / src_name, TARGET / src_name]
    src = next(path for path in candidates if path.exists())
    body = src.read_text(encoding="utf-8")
    body = re.sub(r"^# .*?$", f"# {title}", body, count=1, flags=re.MULTILINE)
    body = link_refs(body)
    marker = body.find("\n## ")
    guide = f"""

> 官方源码：`https://github.com/OpenXiangShan/XiangShan.git`；分支 `{BRANCH}`；分析 commit `{COMMIT}`。

## 统一五问导读

{theory}

## 论文与理论边界

FTQ/IBuffer/ICache 不是单一方向预测算法，但属于解耦前端和控制流交付体系。相关理论包括 scalable/elastic instruction fetching、有限队列反压、非阻塞缓存与 miss-status handling。本文用理论解释“为什么存在”，所有指针、状态机、端口、容量、overflow/underflow 和恢复结论以本 commit 源码为准。

## 示例讲解索引

后文的正常路径、阻塞路径、redirect/flush、满空边界和波形段落均给出具体示例；阅读时建议从“一个 prediction block 的正常流动”开始，再对照 overflow/underflow 和恢复场景。
"""
    body = body[:marker] + guide + body[marker:]
    (OUTPUT / dst_name).write_text(body, encoding="utf-8")


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for old in OUTPUT.glob("Frontend-*.md"):
        old.unlink()

    for name in ["Bim", "BPU", "FauFTB", "FTB", "ITTAGE", "SC", "Tage", "RAS"]:
        regenerate_predictor(name)

    regenerate_existing_chinese(
        "Frontend-FTQ深入解析.md",
        "Frontend-FTQ.md",
        "Frontend FTQ 分支预测生命周期深入分析",
        """| 问题 | 回答 |
| --- | --- |
| **Who** | FTQ 是 BPU、IFU/ICache、后端提交和预测器训练之间的生命周期中心。 |
| **What** | 保存 prediction block 的 PC、预测 meta、历史/RAS 快照、预译码和提交状态。 |
| **How** | 多指针环形队列 + commit/fetch/hit 状态向量；redirect 恢复年轻边界，commit 后生成 predictor update。 |
| **From what** | 来自 BPU S1-S3 prediction、IFU `pdWb`、后端 commit/redirect。 |
| **To what** | 请求发往 IFU/ICache，PC 信息发往后端，训练和恢复信息返回 BPU。 |""",
    )
    regenerate_existing_chinese(
        "Frontend-IBuffer深入解析.md",
        "Frontend-IBuffer.md",
        "Frontend IBuffer 分支预测取指缓冲深入分析",
        """| 问题 | 回答 |
| --- | --- |
| **Who** | IBuffer 位于 IFU 与 Decode 之间，是预测取指结果的弹性队列。 |
| **What** | 将最多 `PredictWidth` 个可变有效指令压紧、缓存，并按 `DecodeWidth` 顺序输出。 |
| **How** | 48-entry/6-bank 环形存储、旁路、output register 和逐 lane Decoupled 握手。 |
| **From what** | 来自 IFU 的真实指令、预译码、异常、taken、FTQ ptr/offset。 |
| **To what** | 输出 `CtrlFlow` 到 Decode；full/ready 把后端压力反馈给 IFU。 |""",
    )
    regenerate_existing_chinese(
        "Frontend-ICache深入解析.md",
        "Frontend-ICache.md",
        "Frontend ICache 控制流交付深入分析",
        """| 问题 | 回答 |
| --- | --- |
| **Who** | Frontend ICache 由 MainPipe、IPrefetch、WayLookup、MissUnit/MSHR、数组和控制单元组成。 |
| **What** | 为 FTQ prediction block 提供低延迟指令数据，并处理翻译、权限、miss、refill、预取和 fence.i。 |
| **How** | 命中流水与慢路径解耦；WayLookup/FIFO/MSHR 用 ready/valid 和状态位管理有限 outstanding 资源。 |
| **From what** | demand/prefetch 地址来自 FTQ，ITLB/PMP 给出翻译与权限，L2/TileLink 返回 refill。 |
| **To what** | 指令数据和异常到 IFU；miss 请求到 L2；容量反压回 FTQ/预取流水。 |""",
    )


if __name__ == "__main__":
    main()
