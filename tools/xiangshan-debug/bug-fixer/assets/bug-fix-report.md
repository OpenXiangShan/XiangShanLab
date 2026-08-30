# XiangShan Issue #<ISSUE> 复现、根因与修复验证报告

## 1. 结论摘要

| 项目 | 结果 |
| --- | --- |
| Issue | `<URL>` |
| 基线 | XiangShan `<commit>`；submodule/config `<values>` |
| 输入 | `<image path>`；SHA-256 `<hash>`；seed `<seed>` |
| 复现结论 | `<R0/R1 + 一句话证据>` |
| 根因 | `<错误谓词/状态转换 -> 错误动作 -> 架构可见结果>` |
| 修复 | `<文件/模块/逻辑变化>` |
| 验证级别 | `<D1/P1/V1/V2/V3/V4>` |
| 未完成项 | `<无，或明确列出>` |

## 2. Issue 契约与判定标准

### 2.1 期望与实际

- 期望：`<架构/微架构行为>`
- 实际：`<日志中的具体 PC/instr/value/cause/assertion>`
- 触发条件：`<输入、配置、特权态、地址、seed>`

### 2.2 A/B oracle

| 判定 | 精确谓词 |
| --- | --- |
| Baseline fail | `<必须出现的日志/架构态/波形条件>` |
| Fixed pass | `<必须消失的条件 + 必须出现的成功条件>` |
| Reject | `<timeout/提前退出/不同错误/禁用 checker 等无效通过>` |

## 3. 复现环境与产物绑定

| 项目 | 路径/值 | 校验 |
| --- | --- | --- |
| 外层运行目录 | `<absolute path>` | fresh timestamped run |
| replay driver | `<path + sha256>` | return `<rc>` |
| XiangShan source | `<absolute path>` | HEAD `<sha>`；dirty `<state>` |
| emu/config | `<path + build command/config>` | build return `<rc>` |
| image | `<absolute path>` | SHA-256 `<hash>` |
| stdout/stderr | `<absolute paths>` | per-image return `<rc>` |
| disassembly | `<absolute path or absent>` | PC/instr checked |
| waveform | `<exact path printed by matching stdout>` | exists/nonempty |
| clock/edge/timescale | `<clock>` / `<edge>` / `<verified unit or raw>` | `<method>` |

关键命令：

```text
<replay/build/run commands exactly as executed>
```

## 4. Baseline 失败证据

### 4.1 日志锚点

| 证据标签 | 文件:行 | 值/事件 | 含义 |
| --- | --- | --- | --- |
| `LOG-OBSERVED` | `<path:line>` | `<text/value>` | `<first divergence>` |

### 4.2 反汇编与目标身份

| 字段 | 值 |
| --- | --- |
| PC / instruction / mnemonic | `<...>` |
| FTQ / full ROB / LQ / SQ | `<... or not allocated>` |
| architectural / physical regs | `<...>` |
| vaddr / paddr / data / mask | `<...>` |
| expected side effect | `<...>` |

说明 PC 重复、wrapper 延迟或缺失身份信号的消歧方式。

## 5. Baseline 波形时间线

本节明确说明使用 WaveKit 的 `<FstReader/VcdReader/FsdbReader>` 查询波形。

| cycle | raw time | identity | module/boundary | valid | ready | fire | key values | evidence/source |
| ---: | ---: | --- | --- | ---: | ---: | ---: | --- | --- |
| `<C>` | `<T>` | `<ROB/FTQ/LQ/SQ>` | `<producer -> consumer>` | `<v>` | `<r>` | `<v&r>` | `<value/state>` | `WAVE-OBSERVED` / `<source:line>` |

记录采样边沿、`end_cycle` 为 exclusive、X/Z mask 检查和未解析信号。

## 6. 因果链与根因

```text
<输入/状态>
  -> <最早错误谓词或缺失事件>
  -> <错误请求/响应/redirect/replay/exception/state transition>
  -> <下游消费>
  -> <commit/difftest/assertion 可见失败>
```

| 链路 | 结论 | 证据标签 | 波形 | 源码 |
| --- | --- | --- | --- | --- |
| producer | `<...>` | `WAVE-OBSERVED + SOURCE-PROVEN` | `<cycle/signal/value>` | `<absolute path:line>` |
| gate/FSM | `<...>` | `<...>` | `<...>` | `<...>` |
| consumer | `<...>` | `<...>` | `<...>` | `<...>` |
| architecture | `<...>` | `CORRELATED` | `<commit/difftest>` | `<...>` |

根因结论（带置信度）：

> `<At cycle C... expression E... condition K... action A... mismatch M...>`

## 7. 替代假设与反证

| 假设 | 若成立应观察到 | 实际证据 | 结论 |
| --- | --- | --- | --- |
| `<primary>` | `<...>` | `<...>` | proven/inferred |
| `<alternative 1>` | `<...>` | `<...>` | rejected/open |
| `<alternative 2>` | `<...>` | `<...>` | rejected/open |

## 8. 代码级修复

### 8.1 Patch contract

| 项目 | 内容 |
| --- | --- |
| 修改 allowlist | `<files>` |
| 故障逻辑 | `<module/block/predicate>` |
| 新语义 | `<corrected condition/action>` |
| 保持语义 | `<other ops/modes/hit-miss/replay/flush/reset>` |
| 新测试/断言 | `<test or assertion>` |
| 预期 fixed waveform | `<signal/event>` |

### 8.2 Diff

```diff
<exact applied or proposed patch>
```

如果未应用，明确写 `P1 Proposed`；不要使用“已修复”。

## 9. 修复前后 A/B 验证

| Gate | Baseline | Fixed | 证据路径 | 结果 |
| --- | --- | --- | --- | --- |
| experiment identity | `<commit/config/image/argv/seed>` | `<same + patch>` | `<metadata>` | pass/fail |
| failure signature | `<present>` | `<absent>` | `<logs:line>` | pass/fail |
| success signature | `<not reached>` | `<present>` | `<logs:line>` | pass/fail |
| checker/process | `<rc/difftest/assert>` | `<rc/difftest/assert>` | `<logs>` | pass/fail |
| architectural result | `<wrong>` | `<expected>` | `<wave/log>` | pass/fail |
| causal waveform | `<old predicate/event>` | `<new predicate/event>` | `<cycles/signals>` | pass/fail |
| focused regression | `<n/a or baseline>` | `<tests/results>` | `<logs>` | pass/fail |

Fixed 波形关键时间线：

| cycle | raw time | identity | corrected event | downstream result |
| ---: | ---: | --- | --- | --- |
| `<C>` | `<T>` | `<...>` | `<...>` | `<...>` |

## 10. 验证级别、风险与后续工作

- 已达到：`<R0/R1/D1/P1/V1/V2/V3/V4>`
- 未执行：`<commands/tests and reason>`
- 仍未解析：`<missing signal/config/source edge>`
- 影响范围：`<versions/configurations/commands>`
- 建议上游回归：`<directed tests/assertions/coverage>`

## 11. 证据索引

- Issue context: `<absolute path>`
- Baseline manifest: `<absolute path>`
- Baseline logs/disassembly/waveform: `<absolute paths>`
- Source checkout and diff: `<absolute paths + commit>`
- Fixed metadata/logs/waveform: `<absolute paths>`
- Regression logs: `<absolute paths>`
