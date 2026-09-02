# XiangShanLab：香山处理器学习与实践平台

[English](./README_EN.md) | [香山官网](https://openxiangshan.cc/) | [GitHub Issues](https://github.com/OpenXiangShan/XiangShanLab/issues)

XiangShanLab 是面向香山处理器学习、开发与验证的开放式知识与实践仓库。仓库围绕香山开发环境、Scala/Chisel、Diplomacy、RISC-V 规范、处理器微架构、运行场景分析、调试方法和工程实践组织内容，并提供题库、Bug 案例、研究资料、AI 辅助工具与竞赛项目。

本仓库适合：

- 希望从零开始运行并系统学习香山的学习者；
- 希望掌握 Chisel、Diplomacy、SoC 集成与总线互联的开发者；
- 希望分析香山流水线、访存、预测、异常和调试机制的研究者；
- 希望开展 RISC-V 验证、Bug 定位、波形分析或 AI 加速扩展的工程人员。

> 仓库内容仍在持续建设中。部分章节、题目和工具可能尚未完善，欢迎通过 Issue 或 Pull Request 参与改进。

## 快速开始

### 1. 克隆仓库

仓库包含 `wavekit-xslab` 子模块，建议递归克隆：

```bash
git clone --recursive https://github.com/OpenXiangShan/XiangShanLab.git
cd XiangShanLab
```

如果已经完成普通克隆，可补充初始化子模块：

```bash
git submodule update --init --recursive
```

### 2. 选择入口

- **第一次接触香山**：阅读[学习路径指引](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md)，然后从[开发环境课程](./xiangshan-course/docs/1-xiangshan-development-environment/Introduction_Preface.md)开始。
- **学习 Chisel / Diplomacy**：进入[香山编程课程](./xiangshan-course/docs/2-xiangshan-programming/)并结合[编程实践](./xiangshan-programming-practice/README.md)动手练习。
- **分析香山微架构**：依次学习[超标量基础](./xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/1_Single_Cycle_vs_Multi_Cycle_vs_Pipeline.md)、[设计文档](./xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/2-xiangshan-design-document/)和[源码分析](./xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/)。
- **开展场景验证与调试**：使用[场景分析](./xiangshan-course/docs/5-xiangshan-scenarios-analysis/)、[调试案例](./xiangshan-course/docs/6-xiangshan-debug/)及[工具集](./tools/README.md)。
- **准备竞赛项目**：查看[2026 CIE RISC-V 大赛应用方向](./2026-CIE-RISC-V-Contest-Application-Track/README.md)及其[提交指南](./2026-CIE-RISC-V-Contest-Application-Track/SUBMISSION_GUIDE.md)。

## 推荐学习路径

### 基础阶段

1. 搭建环境并完成 Hello XiangShan；
2. 学习 Scala、Chisel 与基本工程实践；
3. 了解 RISC-V 指令集、特权架构与常用模拟器；
4. 建立流水线、冒险、乱序执行和存储层次的基础认识。

### 进阶阶段

1. 阅读香山设计文档和模块源码分析；
2. 结合波形理解指令从取指到提交的生命周期；
3. 学习预测、重定向、访存重放、异常与 Difftest 等典型场景；
4. 通过题库、Bug 案例和实践项目巩固分析与实现能力。

### 方向化实践

- **香山处理器方向**：重点学习微架构、场景分析、调试和源码工具。
- **DSU / SoC 方向**：重点学习 Chisel、Diplomacy、TileLink、AXI 和互联实践。
- **香山 AI 方向**：在掌握架构基础后，尝试自定义指令、算子加速和软硬件协同优化。

更完整的分阶段建议、任务认领与交付说明见[学习路径指引](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md)。

## 课程体系

课程主体位于 [`xiangshan-course/`](./xiangshan-course/README.md)：

| 章节 | 内容 | 入口 |
| --- | --- | --- |
| 1. 香山开发环境 | 工具准备、应用程序、NEMU、Spike、DRAMsim3、香山仿真、GEM5、Difftest | [开始学习](./xiangshan-course/docs/1-xiangshan-development-environment/Introduction_Preface.md) |
| 2. 香山编程 | Scala、Chisel、工程实践、Diplomacy、TileLink、AXI | [开始学习](./xiangshan-course/docs/2-xiangshan-programming/) |
| 3. RISC-V 规范 | 与课程相关的 RISC-V ISA 与规范资料 | [开始学习](./xiangshan-course/docs/3-riscv-specification/) |
| 4. 香山微结构分析 | 超标量基础、设计文档、前端/后端/访存等源码分析 | [开始学习](./xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/) |
| 5. 香山场景分析 | 指令生命周期、预测、预取、访存、重放、冲突等动态场景 | [开始学习](./xiangshan-course/docs/5-xiangshan-scenarios-analysis/) |
| 6. 香山调试 | Bug 分类、异常、CSR、PMA/PMP、X-state 和 RTL 案例 | [开始学习](./xiangshan-course/docs/6-xiangshan-debug/) |
| 7. 香山 NoC | Cache 一致性、CHI 事务与节点、XSCache、DDR 控制器和死锁场景 | [开始学习](./xiangshan-course/docs/7-xiangshan-NoC/) |
| 8. 香山 AI | 面向香山 AI 扩展的后续课程目录，持续补充中 | [查看目录](./xiangshan-course/docs/8-xiangshan-AI/) |
| 9. 香山 AIA | AIA 规范、设计、集成与隔离分析 | [开始学习](./xiangshan-course/docs/9-xiangshan-AIA/) |
| 10. 香山敏捷工具 | 面向敏捷开发工具的后续课程目录，持续补充中 | [查看目录](./xiangshan-course/docs/10-xiangshan-agile-tools/) |

课程同时提供 [`docs-en/`](./xiangshan-course/docs-en/) 英文资料；目前英文内容主要覆盖前六章，不同章节的翻译进度可能不同。

## 仓库资源地图

| 目录 | 说明 | 推荐入口 |
| --- | --- | --- |
| [`xiangshan-course/`](./xiangshan-course/) | 系统化课程文档，是本仓库的核心学习内容 | [课程说明](./xiangshan-course/README.md) |
| [`xiangshan-programming-practice/`](./xiangshan-programming-practice/) | Chisel、Diplomacy、IOPMP、AXI XBar、非阻塞 Cache、MMU/SMMPT 等实践工程 | [实践说明](./xiangshan-programming-practice/README.md) |
| [`xiangshan-question-bank/`](./xiangshan-question-bank/) | 开发、Chisel、Diplomacy、ISA、微架构、验证和系统软件题库 | [Hello XiangShan](./xiangshan-question-bank/1-xiangshan-development/hello-xiangshan.md) |
| [`xiangshan-bugs-library/`](./xiangshan-bugs-library/) | 香山 Issue/PR 案例、微架构 Bug 与异常类 Bug 汇总 | [微架构 Bug 摘要](./xiangshan-bugs-library/micro-arch-summary.md) |
| [`xiangshan-research/`](./xiangshan-research/) | 香山相关论文与研究脉络整理 | [论文索引](./xiangshan-research/xiangshan-related-papers.md) |
| [`tools/`](./tools/) | 源码分析、波形追踪、验证场景生成、Bug 分析和规范查询工具 | [工具说明](./tools/README.md) |
| [`XiangShanLab-user-guide/`](./XiangShanLab-user-guide/) | 学习路径、作业提交、文档反馈和社区协作说明 | [学习路径指引](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md) |
| [`2026-CIE-RISC-V-Contest-Application-Track/`](./2026-CIE-RISC-V-Contest-Application-Track/) | 2026 CIE RISC-V 大赛应用方向赛题与加密提交区 | [赛题说明](./2026-CIE-RISC-V-Contest-Application-Track/README.md) |

## 编程实践

[`xiangshan-programming-practice/`](./xiangshan-programming-practice/README.md) 提供可独立阅读和运行的实验工程，包括：

- `ChiselIOPMP/`：IOPMP 核心实现及相关数据通路示例；
- `IopmpSystem/`：DCache—IOPMP—Memory 系统级实验；
- `TwoToOneXbarSystem/`：2-to-1 AXI4 XBar 互联实验；
- `NonBlockingCache/`：非阻塞 Cache 设计实践；
- `mmu-smmpt/`：MMU / SMMPT 相关实现与测试。

各工程的依赖和运行方法不同，请以对应目录中的 README 与构建文件为准。

## AI 与分析工具

[`tools/`](./tools/README.md) 主要面向 Codex/agent 辅助的硬件学习、验证和分析工作流，当前覆盖：

- 香山 Kunminghu 源码结构与机制分析；
- 基于 WaveKit 的流水线和单指令波形追踪；
- 微架构机制到可执行验证场景的转换；
- 测试生成、仿真、波形检查的场景闭环；
- 香山 Issue/PR 数据整理与 Bug 归因；
- RISC-V 安全、隔离、I/O 保护和可信调试规范查询。

多数工具以 `SKILL.md` 为入口，并通过 `references/`、`scripts/` 和 `agents/` 提供配套资料。涉及真实源码、波形或仿真时，请同时提供对应路径、分支或 commit、目标 PC 和运行命令。

## 参与贡献

欢迎修正文档、补充课程、回答题目、添加实验、整理 Bug 案例或完善工具。

建议流程：

1. Fork 本仓库并从最新主分支创建工作分支；
2. 尽量保持修改聚焦，并核对文档中的相对链接与命令；
3. 在 Pull Request 中说明修改目的、影响范围和验证方式；
4. 如发现问题但暂时无法修复，可先提交 [GitHub Issue](https://github.com/OpenXiangShan/XiangShanLab/issues)。

社区任务的认领、同步与交付原则可参考[社区去中心化治理策略](./XiangShanLab-user-guide/XiangShan-Community-Decentralized-Governance-Strategy.md)。Hello XiangShan 任务的特定提交流程见[提交指南](./XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md)。

## 许可与说明

- 本仓库由多个课程、代码、数据和工具模块组成，请以各目录中的许可证和说明为准。
- `xiangshan-course` 中的代码采用 Apache-2.0，课程文档采用 CC BY-NC 4.0；详情见其[课程 README](./xiangshan-course/README.md)。
- 外部论文、规范、图片、代码和链接的版权归原作者或对应项目所有。
- 本仓库不是香山处理器 RTL 主仓库；香山处理器源码与开发环境请参考下方关联项目。

## 关联项目

- [OpenXiangShan/XiangShan](https://github.com/OpenXiangShan/XiangShan)：香山处理器主仓库；
- [OpenXiangShan/xs-env](https://github.com/OpenXiangShan/xs-env)：香山开发与仿真环境；
- [XiangShanLab/wavekit-xslab](https://github.com/XiangShanLab/wavekit-xslab)：本仓库使用的 WaveKit 子模块；
- [openxiangshan.cc](https://openxiangshan.cc/)：香山官方网站。

---

从[学习路径指引](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md)开始，完成第一次香山运行，再逐步进入编程、微架构、场景分析与调试实践。
