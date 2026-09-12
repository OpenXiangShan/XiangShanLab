# XiangShanLab：香山处理器学习与实践仓库

[English](./README_EN.md) | [香山官网](https://openxiangshan.cc/) | [GitHub Issues](https://github.com/OpenXiangShan/XiangShanLab/issues)

XiangShanLab 是面向香山处理器学习、开发、验证和研究的开放仓库。仓库以课程文档为主，同时收录编程实践、问题题库、Bug 案例、分析工具、研究论文、周报、合作机构资料以及竞赛项目。

## 快速开始

### 克隆仓库

仓库包含 `tools/wavekit-xslab` Git 子模块，建议递归克隆：

```bash
git clone --recursive https://github.com/OpenXiangShan/XiangShanLab.git
cd XiangShanLab
```

如果已经完成普通克隆：

```bash
git submodule update --init --recursive
```

### 按目标选择入口

- **第一次学习香山**：阅读[学习路径指引](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md)，再从[香山开发环境课程](./xiangshan-course/docs/1-xiangshan-development-environment/)开始。
- **学习 Scala、Chisel 和 Diplomacy**：阅读[香山编程课程](./xiangshan-course/docs/2-xiangshan-programming/)，并完成[编程实践](./xiangshan-programming-practice/)中的工程。
- **学习 RISC-V 规范**：进入[RISC-V 规范资料](./xiangshan-course/docs/3-riscv-specification/)。
- **分析香山微架构**：从[超标量基础](./xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/)开始，再阅读设计文档和源码分析。
- **研究运行场景和调试问题**：使用[场景分析](./xiangshan-course/docs/5-xiangshan-scenarios-analysis/)、[调试案例](./xiangshan-course/docs/6-xiangshan-debug/)和[问题题库](./xiangshan-question-bank/)。
- **使用 AI 辅助工具分析源码、波形或 Bug**：查看[工具目录](./tools/README.md)及各工具的 `SKILL.md`。
- **参加 2026 CIE RISC-V 大赛**：查看[应用方向赛题](./2026-CIE-RISC-V-Contest-Application-Track/README.md)和[提交指南](./2026-CIE-RISC-V-Contest-Application-Track/SUBMISSION_GUIDE.md)。

## 课程目录

课程主体位于 [`xiangshan-course/`](./xiangshan-course/)，中文资料位于 `docs/`，英文资料位于 `docs-en/`。

| 章节 | 内容 | 入口 |
| --- | --- | --- |
| 1 | 香山开发环境、模拟器、仿真流程与 Difftest | [开始学习](./xiangshan-course/docs/1-xiangshan-development-environment/) |
| 2 | Scala、Chisel、Diplomacy、TileLink 与 AXI | [开始学习](./xiangshan-course/docs/2-xiangshan-programming/) |
| 3 | RISC-V 指令、异常和相关规范资料 | [开始学习](./xiangshan-course/docs/3-riscv-specification/) |
| 4 | 超标量、乱序执行、前端、后端和存储子系统 | [开始学习](./xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/) |
| 5 | 指令生命周期、预测、预取、访存、重放和冲突 | [开始学习](./xiangshan-course/docs/5-xiangshan-scenarios-analysis/) |
| 6 | Bug 分析、异常、CSR、PMA/PMP、X-state 和调试案例 | [开始学习](./xiangshan-course/docs/6-xiangshan-debug/) |
| 7 | NoC、Cache 一致性、CHI、XSCache、DDR 和死锁 | [开始学习](./xiangshan-course/docs/7-xiangshan-NoC/) |
| 8 | 香山 AI 相关资料 | [进入目录](./xiangshan-course/docs/8-xiangshan-AI/) |
| 9 | AIA 规范、设计、集成和隔离 | [开始学习](./xiangshan-course/docs/9-xiangshan-AIA/) |
| 10 | 安全方向资料，包括 Zicfilp、Zicfiss 和时序 FENCE | [进入目录](./xiangshan-course/docs/10-xiangshan-security/) |
| 11 | 验证方向资料，包括 UVM 模板 | [进入目录](./xiangshan-course/docs/11-xiangshang-verification/) |
| 12 | 工作负载分析资料 | [进入目录](./xiangshan-course/docs/12-workload-analysis/) |
| 13 | 香山敏捷工具资料 | [进入目录](./xiangshan-course/docs/13-xiangshan-aglie-tools/) |

课程仓库的详细说明、许可证和中英文资料入口见[`xiangshan-course/README.md`](./xiangshan-course/README.md)。

## 仓库结构

| 目录 | 内容 | 推荐入口 |
| --- | --- | --- |
| [`xiangshan-course/`](./xiangshan-course/) | 香山系统化学习课程，包含中文和英文文档 | [课程 README](./xiangshan-course/README.md) |
| [`xiangshan-programming-practice/`](./xiangshan-programming-practice/) | IOPMP、AXI XBar、非阻塞 Cache、MMU/SMMPT 等实践工程 | [实践目录](./xiangshan-programming-practice/) |
| [`xiangshan-question-bank/`](./xiangshan-question-bank/) | 香山开发、Chisel、Diplomacy、ISA、微架构、验证和系统软件题库 | [Hello XiangShan 题目](./xiangshan-question-bank/1-xiangshan-development/hello-xiangshan.md) |
| [`xiangshan-bugs-library/`](./xiangshan-bugs-library/) | 异常类和微架构类 Bug 摘要，以及 Bug 数据处理脚本 | [微架构 Bug 摘要](./xiangshan-bugs-library/micro-arch-summary.md) |
| [`tools/`](./tools/) | 源码分析、波形分析、场景提取、验证驱动、Bug 分析和规范查询工具 | [工具 README](./tools/README.md) |
| [`XiangShanLab-user-guide/`](./XiangShanLab-user-guide/) | 学习路径、任务提交、文档反馈和社区协作说明 | [学习路径指引](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md) |
| [`xiangshan-research/`](./xiangshan-research/) | 香山相关论文、研究方向和文献索引 | [论文索引](./xiangshan-research/xiangshan-related-papers.md) |
| [`xiangshan-collaborators/`](./xiangshan-collaborators/) | 香山合作高校和科研机构资料 | [合作机构](./xiangshan-collaborators/xiangshan-collaborators.md) |
| [`weekly-status/`](./weekly-status/) | 周报、状态模板和周报生成脚本 | [周报目录](./weekly-status/) |
| [`2026-CIE-RISC-V-Contest-Application-Track/`](./2026-CIE-RISC-V-Contest-Application-Track/) | 2026 CIE RISC-V 大赛应用方向赛题与提交文件 | [赛题说明](./2026-CIE-RISC-V-Contest-Application-Track/README.md) |

## 编程实践

[`xiangshan-programming-practice/`](./xiangshan-programming-practice/)中的项目相互独立，依赖和运行命令以各项目 README、`Makefile` 或 `build.sbt` 为准：

- `ChiselIOPMP/`：DMA 到 IOPMP 再到 Memory 的 Chisel 实验；
- `IopmpSystem/`：DCache、IOPMP 和 Memory 组成的系统级实验；
- `TwoToOneXbarSystem/`：两个 AXI4 Master 共享一个 Memory Slave 的 2-to-1 XBar 实验；
- `NonBlockingCache/`：非阻塞 Cache 设计与测试；
- `mmu-smmpt/`：MMU / SMMPT 相关实现、模块和测试。

## 工具与数据

[`tools/`](./tools/)中的工具主要服务于 Codex/agent 辅助的硬件学习和验证工作，当前包括：

- `xiangshan-code-analyzer/`：香山 Kunminghu 源码分析；
- `analyze-xiangshan-wavekit/`、`xiangshan-wave-analysis/`：波形和流水线行为分析；
- `scenarios-extractor/`、`verification-driver/`：验证场景提取与验证规则；
- `xiangshan-debug/`：调试和 Difftest 追踪；
- `xiangshan-bugs-analyzer/`：香山 Issue/PR 数据整理和 Bug 分析；
- `riscv-skill-pack/`、`specification-analyzer/`：RISC-V 技术和安全规范查询；
- `wavekit-xslab/`：作为 Git 子模块接入的 WaveKit 工具。

多数工具以 `SKILL.md` 为入口。涉及源码、波形或仿真时，应同时提供对应路径、分支或 commit、目标 PC 和运行命令。

## 参与贡献

欢迎补充课程、修正文档、回答题目、添加实践工程、整理 Bug 案例和完善工具。

建议：

1. 从最新主分支创建聚焦的工作分支；
2. 修改后检查 Markdown 相对链接、命令和目录名称；
3. 在 Pull Request 中说明修改内容、影响范围和验证方式；
4. 如果暂时无法修复问题，可通过 [GitHub Issues](https://github.com/OpenXiangShan/XiangShanLab/issues) 报告。

任务认领、同步和交付规则见[社区去中心化治理策略](./XiangShanLab-user-guide/XiangShan-Community-Decentralized-Governance-Strategy.md)，Hello XiangShan 的提交流程见[提交指南](./XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md)。

## 许可与相关项目

- 本仓库包含课程、代码、数据和工具模块，各目录的许可证和说明可能不同，请以对应目录为准。
- `xiangshan-course` 的代码采用 Apache-2.0，课程文档采用 CC BY-NC 4.0，详情见[课程 README](./xiangshan-course/README.md)。
- 本仓库不是香山处理器 RTL 主仓库。处理器源码、开发环境和官网见：
  - [OpenXiangShan/XiangShan](https://github.com/OpenXiangShan/XiangShan)
  - [OpenXiangShan/xs-env](https://github.com/OpenXiangShan/xs-env)
  - [XiangShanLab/wavekit-xslab](https://github.com/XiangShanLab/wavekit-xslab)
  - [openxiangshan.cc](https://openxiangshan.cc/)
