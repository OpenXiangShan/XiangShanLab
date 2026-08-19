# XiangShanLab Tools

本仓库收集了一组面向香山 / Kunminghu 代码分析、波形分析、验证场景生成、Bug 知识库整理和 RISC-V 安全规范查询的 Codex skill 与配套脚本。它不是单一二进制工具，而是给 Codex/agent 在硬件验证与微架构分析任务中复用的工具目录。

## 目录概览

| 目录 | 用途 |
| --- | --- |
| `xiangshan-code-analyzer/` | 香山 Kunminghu 源码深度分析 skill，覆盖前端、后端、访存、Cache、AIA/IOPMP/AXI、Difftest、异常/调试/特权态等主题。 |
| `analyze-xiangshan-wavekit/` | 基于 wavekit 的香山波形单指令追踪与流水线行为分析 skill，强调从波形证据还原 PC、ROB/LQ/SQ/FTQ、valid/ready/fire、redirect、bubble 和 difftest 状态。 |
| `wavekit-xslab/` | XiangShanLab 的 WaveKit 工具与波形分析仓库；作为 Git 子模块接入，固定到对应提交。 |
| `xiangshan-scenario-wave-test/` | 从微架构场景描述生成测试程序、构建镜像、运行 emu dump wave，并用 wavekit 验证场景是否真实复现的闭环 skill。 |
| `scenarios-extractor/` | 将机制名、模块名或优化点转换为可执行的验证场景描述，包括 stimulus、期望观察、失败特征、checker、coverage 和所需证据。 |
| `verification-driver/` | 验证驱动规则库，提供架构验证、微架构场景验证、系统验证、边界条件、冲突、FSM、性能瓶颈、debug、虚拟化和保护等场景模板。 |
| `xiangshan-bugs-analyzer/` | 拉取、索引和总结 OpenXiangShan/XiangShan issue/PR 的工具与数据，用于 bug 归因、修复背景分析和模块风险梳理。 |
| `specification-analyzer/` | RISC-V Security Horizontal Committee 相关规范查询 skill，用于安全、隔离、机密计算、I/O 保护、可信调试和 CFI 等规范状态确认。 |

## 典型使用场景

- **源码解释**：分析某个香山模块的接口、数据通路、控制通路、FSM、队列、仲裁、重放、redirect、异常和性能行为。
- **波形定位**：给定波形、反汇编和目标 PC，按流水线阶段追踪一条指令，并给出源码依据与周期级证据。
- **场景生成**：根据 `MDP`、`StoreSet`、`LSQ replay`、`branch redirect` 等机制生成 directed test / constrained random / assertion / coverage 可落地的验证场景。
- **场景实测闭环**：生成小测试，构建 RISC-V 镜像，运行香山 emu dump wave，再用 wavekit 判断场景是否复现。
- **Bug 分析**：从 GitHub issue/PR 数据中整理模块相关 bug、修复 PR、常见原因和验证风险。
- **规范查证**：查询 RISC-V 安全相关规范的类别、状态和官方来源。

## 入口文件

多数工具以 `SKILL.md` 作为入口，Codex/agent 会先读取这些文件，再按其中的引用加载 `references/`、`scripts/` 或 `agents/`。

常用入口：

- `xiangshan-code-analyzer/skills/analyze-xiangshan-kunminghu/SKILL.md`
- `analyze-xiangshan-wavekit/SKILL.md`
- `xiangshan-scenario-wave-test/SKILL.md`
- `scenarios-extractor/skills/SKILL.md`
- `xiangshan-bugs-analyzer/fetch-xiangshan-bugs/SKILL.md`
- `specification-analyzer/riscv-security-hc-spec/SKILL.md`

## 脚本与数据

- `xiangshan-code-analyzer/**/scripts/`：保存分析结果、周同步检查、前端文档整理等辅助脚本。
- `xiangshan-bugs-analyzer/fetch-xiangshan-bugs/scripts/`：从 GitHub 收集 issue/PR，并生成 JSONL 与 Markdown 索引。
- `xiangshan-bugs-analyzer/kunminghu-bugs/`：已生成的 Kunminghu bug 数据与摘要。
- `verification-driver/skills/*.md`：面向不同验证维度的规则文件，可被场景生成和模块分析复用。

## 使用方式

在 Codex 中描述任务时，直接给出目标和必要输入即可，例如：

```text
分析 XiangShan kunminghu-v2 的 LoadQueue replay 逻辑，输出源码依据、FSM/队列行为和验证特别注意。
```

```text
根据 MDP 机制生成验证场景，要求包含冲突、饱和、恢复、forward progress 和 waveform 观察点。
```

```text
给定 XiangShan 源码路径、FST 波形、反汇编和目标 PC，追踪这条指令从前端到提交的完整路径。
```

对于需要真实构建、运行 emu 或访问 GitHub 的任务，应同时提供：

- XiangShan 源码路径或分支 / commit。
- 测试目录、构建命令或现有工程约定。
- 波形路径、反汇编路径、目标 PC。
- 是否允许访问网络或使用已有本地数据。

## 维护约定

- 新增 skill 时优先放置清晰的 `SKILL.md`，并把长篇规则、模板和背景资料拆到 `references/`。
- 脚本放在对应工具的 `scripts/` 下，避免跨目录隐式依赖。
- 生成的数据应单独放入结果目录，并在 README 或索引文件中记录来源、时间、过滤条件和后续分析建议。
- 面向香山源码的结论应尽量保留分支 / commit、文件路径、行号和关键信号名，避免只写抽象描述。
- 涉及规范状态、GitHub issue/PR、远端源码或工具版本时，应以当前官方来源或本地同步数据为准。
