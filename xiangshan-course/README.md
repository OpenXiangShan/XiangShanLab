# 香山处理器学习课程 / Xiangshan Processor Learning Course

[English](#english) | [中文](#中文)

[![License: Apache 2.0](https://img.shields.io/badge/Code%20License-Apache_2.0-blue.svg)](LICENSE-CODE)
[![License: CC BY-NC 4.0](https://img.shields.io/badge/Docs%20License-CC_BY--NC_4.0-lightgrey.svg)](docs/LICENSE)

## 中文

本仓库是香山处理器学习课程文档仓库，面向希望系统学习香山开发环境、Chisel/Diplomacy 编程、RISC-V 规范、香山微结构、典型场景分析、调试方法以及后续开发工具的学习者。

当前仓库以 Markdown 文档、图片、附件和用户指南为主。课程内容仍在持续补充中，部分目录为后续章节预留。

### 适合人群

- 开芯院新员工培训
- 合作企业、高校成员学习
- 香山处理器爱好者自学
- 需要了解香山开发、验证、调试流程的工程师和研究者

### 仓库结构

| 路径 | 内容 |
| --- | --- |
| `docs/1-xiangshan-development-environment/` | 香山开发环境。包含中文 `1.CHN/`、英文 `2.ENG/` 章节，以及 `FAQ/`。 |
| `docs/2-xiangshan-programming/` | 香山相关编程基础。包含 Chisel 和 Diplomacy 两部分。 |
| `docs/3-riscv-specification/` | RISC-V 规范学习资料，目前包含持续更新占位说明。 |
| `docs/4-xiangshan-microarchitecture-analysis/` | 香山微结构分析。包含超标量基础、设计文档、源码分析。 |
| `docs/5-xiangshan-scenarios-analysis/` | 典型场景分析。包含场景描述、指令/模块分析、图片和附件。 |
| `docs/6-xiangshan-debug/` | 香山调试案例。包含入门进阶级问题分类、指令生成器问题、RTL 问题、异常/CSR/PMA/PMP 等案例。 |
| `docs/7-xiangshan-development/` | 香山开发章节预留目录。 |
| `docs/8-xiangshan-development-tools/` | 香山开发工具章节预留目录。 |
| `assets/` | 仓库级图片、图表等资源。 |
| `user-guide/` | 用户指南，例如 hello xiangshan 提交说明、文档问题反馈说明。 |
| `LICENSE-CODE` | 代码许可证。 |
| `docs/LICENSE` | 文档许可证。 |

### 课程目录

#### 1. 香山开发环境

- [中文课程](docs/1-xiangshan-development-environment/1.CHN/)
- [English Course](docs/1-xiangshan-development-environment/2.ENG/)
- [环境 FAQ](docs/1-xiangshan-development-environment/FAQ/xs-env-FAQ.md)

主要内容包括开发环境总述、工具准备、应用程序、指令模拟器、NEMU/Spike 参考模型、DRAMsim3、香山仿真流程、GEM5、Difftest 协同仿真框架等。

#### 2. 香山编程

- [Chisel 编程](docs/2-xiangshan-programming/1-chisel/)
- [Diplomacy 与协议扩展](docs/2-xiangshan-programming/2-diplomacy/)

主要内容包括 Scala/Chisel 基础语法、工程实践、常见错误、Chisel 底层原理、香山 Chisel 编码规范、Diplomacy 基础、TileLink/AXI 扩展和实践练习。

#### 3. RISC-V 规范

- [RISC-V Specification](docs/3-riscv-specification/)

该部分用于沉淀与课程相关的 RISC-V 规范学习资料。

#### 4. 香山微结构分析

- [超标量基础知识](docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/)
- [香山设计文档](docs/4-xiangshan-microarchitecture-analysis/2-xiangshan-design-document/)
- [香山源码分析](docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/)

主要内容包括流水线、冒险、依赖、多发射、乱序执行、Tomasulo/Scoreboard、寄存器重命名、派遣队列、发射队列、旁路网络、执行单元、物理寄存器、CSR、ROB、前端/后端/存储子系统源码分析等。

#### 5. 香山场景分析

- [场景描述](docs/5-xiangshan-scenarios-analysis/scenarios-description/)
- [场景分析](docs/5-xiangshan-scenarios-analysis/scenarios-analysis/)

主要内容包括标量 load/store/add、vector load/store/add、AMO、CBO、DIV、FENCE、JAL/JALR、预取、BPU、指令缓存、MDP 等场景。

#### 6. 香山调试

- [Upper Beginner Level](docs/6-xiangshan-debug/Upper_Beginner_Level/)

主要内容包括 bug 分析分类、指令生成器问题、香山 RTL 问题、异常触发、CSR、PMA/PMP、X-state、未初始化、指令执行结果错误等案例。

#### 7-8. 开发与工具

- [香山开发](docs/7-xiangshan-development/)
- [香山开发工具](docs/8-xiangshan-development-tools/)

这两个目录当前为空，作为后续课程内容预留。

### 用户指南

- [如何提交 hello xiangshan](user-guide/how-to-commit-hello-xiangshan.md)
- [如何反馈文档问题](user-guide/how-to-report-document-issues.md)

### 许可证

本仓库不同类型内容使用不同许可证：

| 内容 | 路径 | 许可证 | 商业使用 |
| --- | --- | --- | --- |
| 教学文档 | `docs/` | CC BY-NC 4.0 | 禁止，除非获得授权 |
| 代码内容 | 代码相关文件 | Apache License 2.0 | 允许，需遵守许可证 |

基于本课程内容提供收费培训、认证或考试服务，需要获得北京开源芯片研究院明确授权。

### 反馈

如果发现文档错误、链接失效、翻译问题或内容不清晰，请通过 GitHub Issues 反馈：

- [OpenXiangShan/XiangShanLab Issues](https://github.com/OpenXiangShan/XiangShanLab/issues)