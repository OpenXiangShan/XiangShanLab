# 香山处理器学习课程

[English README](./README_EN.md) | [返回 XiangShanLab 根目录](../README.md)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE-CODE)
[![Docs License: CC BY-NC 4.0](https://img.shields.io/badge/Docs%20License-CC_BY--NC_4.0-lightgrey.svg)](docs/LICENSE)

本目录是 XiangShanLab 的香山处理器学习课程，面向希望系统学习香山开发环境、Chisel/Diplomacy、RISC-V、微架构、场景分析、调试和相关开发工具的学习者。

课程以 Markdown 文档为主，并包含图片、演示文稿、PDF、代码样例和其他学习附件。内容持续更新，部分章节仍处于建设阶段。

## 快速开始

中文课程位于 [`docs/`](./docs/)，英文课程位于 [`docs-en/`](./docs-en/)。

建议按以下顺序开始：

1. 阅读[香山开发环境](./docs/1-xiangshan-development-environment/Introduction_Preface.md)，了解工具链、模拟器和仿真流程。
2. 学习[香山编程](./docs/2-xiangshan-programming/)，掌握 Scala、Chisel、Diplomacy、TileLink 和 AXI。
3. 根据目标继续学习 RISC-V 规范、微架构、运行场景和调试章节。
4. 结合仓库根目录的[学习路径指引](../XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md)和[提交指南](../XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md)开展练习与贡献。

## 中文课程

| 章节 | 主题 | 入口 |
| --- | --- | --- |
| 1 | 香山开发环境、模拟器、仿真流程和 Difftest | [开发环境](./docs/1-xiangshan-development-environment/) |
| 2 | Scala、Chisel、Diplomacy、TileLink 和 AXI | [香山编程](./docs/2-xiangshan-programming/) |
| 3 | RISC-V 指令集和规范资料 | [RISC-V 规范](./docs/3-riscv-specification/) |
| 4 | 超标量、乱序执行、前端、后端和存储子系统 | [微架构分析](./docs/4-xiangshan-microarchitecture-analysis/) |
| 5 | 指令生命周期、预测、预取、访存、重放和冲突 | [场景分析](./docs/5-xiangshan-scenarios-analysis/) |
| 6 | Bug 分析、异常、CSR、PMA/PMP、X-state 和调试案例 | [香山调试](./docs/6-xiangshan-debug/) |
| 7 | NoC、Cache 一致性、CHI、XSCache、DDR 和死锁 | [NoC 与缓存](./docs/7-xiangshan-NoC/) |
| 8 | 香山 AI 相关资料 | [香山 AI](./docs/8-xiangshan-AI/) |
| 9 | AIA 规范、设计、集成和隔离 | [香山 AIA](./docs/9-xiangshan-AIA/) |
| 10 | 安全方向资料 | [香山安全](./docs/10-xiangshan-security/) |
| 11 | DDR 相关资料 | [香山 DDR](./docs/11-xiangshan-ddr/) |
| 12 | 工作负载和虚拟机运行场景 | [工作负载分析](./docs/12-workload-analysis/) |
| 13 | 验证方向资料 | [香山验证](./docs/13-xiangshang-verification/) |
| 14 | 香山敏捷工具资料 | [敏捷工具](./docs/14-xiangshan-aglie-tools/) |

其中，第 1 至第 6 章包含较完整的基础和进阶学习内容；第 7 至第 14 章持续补充中。

## 英文课程

英文材料位于 [`docs-en/`](./docs-en/)，当前包含以下章节：

| 章节 | 主题 | 入口 |
| --- | --- | --- |
| 1 | XiangShan development environment, simulators, and Difftest | [Development Environment](./docs-en/1-xiangshan-development-environment/) |
| 2 | Scala, Chisel, Diplomacy, TileLink, and AXI | [Programming](./docs-en/2-xiangshan-programming/) |
| 3 | RISC-V specification materials | [RISC-V Specification](./docs-en/3-riscv-specification/) |
| 4 | Microarchitecture fundamentals, design documents, and source analysis | [Microarchitecture Analysis](./docs-en/4-xiangshan-microarchitecture-analysis/) |
| 5 | Pipeline and instruction execution scenarios | [Scenario Analysis](./docs-en/5-xiangshan-scenarios-analysis/) |
| 6 | Debugging and bug-analysis materials | [Debugging](./docs-en/6-xiangshan-debug/) |
| 7 | XiangShan development materials (reserved) | [Development](./docs-en/7-xiangshan-development/) |
| 8 | XiangShan development tools (reserved) | [Development Tools](./docs-en/8-xiangshan-development-tools/) |

此外，英文目录包含独立的[香山微架构资料](./docs-en/xiangshan-microarchitecture/)。英文覆盖范围目前小于中文课程，以实际目录为准。

## 目录结构

```text
xiangshan-course/
├── README.md
├── README_EN.md
├── LICENSE-CODE
├── assets/
│   ├── diagrams/
│   └── images/
├── docs/                 # 中文课程，共 14 个章节目录
└── docs-en/              # 英文课程，共 8 个章节目录及微架构专题
```

课程文档中的图片、附件和代码样例通常存放在对应章节目录下；公共资源存放在 `assets/`。

## 相关入口

- [XiangShanLab 学习路径指引](../XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md)
- [Hello XiangShan 提交指南](../XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md)
- [文档问题反馈指南](../XiangShanLab-user-guide/how-to-report-document-issues.md)
- [XiangShanLab 根目录 README](../README.md)

## 许可证

- 课程代码和仓库代码相关内容遵循 [`LICENSE-CODE`](./LICENSE-CODE)。
- 课程文档遵循 [`docs/LICENSE`](./docs/LICENSE)。
- 具体文件或子目录存在额外许可说明时，以其说明为准。
