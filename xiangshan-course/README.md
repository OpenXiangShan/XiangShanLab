# 香山处理器学习课程

> 香山处理器学习课程文档仓库

[English README](./README_EN.md)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE-CODE)
[![License: CC BY-NC 4.0](https://img.shields.io/badge/Docs%20License-CC_BY--NC_4.0-lightgrey.svg)](docs/LICENSE)

本仓库是香山处理器学习课程文档仓库，面向希望系统学习香山开发环境、Chisel/Diplomacy 编程、RISC-V 规范、香山微结构、典型场景分析、调试方法以及后续开发工具的学习者。

当前仓库以 Markdown 文档、图片、附件和用户指南为主。课程内容仍在持续补充中，部分目录为后续章节预留。

## 课程介绍

### 1. [香山开发环境](./docs/1-xiangshan-development-environment/1.CHN/Introduction_Preface.md)

- [中文课程](./docs/1-xiangshan-development-environment/1.CHN/)
- [English Course](./docs/1-xiangshan-development-environment/2.ENG/)
- [环境 FAQ](./docs/1-xiangshan-development-environment/FAQ/xs-env-FAQ.md)

主要内容包括开发环境总述、工具准备、应用程序、指令模拟器、NEMU/Spike 参考模型、DRAMsim3、香山仿真流程、GEM5、Difftest 协同仿真框架等。

### 2. [香山编程](./docs/2-xiangshan-programming/)

- [Chisel 编程](./docs/2-xiangshan-programming/1-chisel/Chapter_1_Basic_Scala_Syntax.md)
- [Diplomacy 与协议扩展](./docs/2-xiangshan-programming/2-diplomacy/Chapter_10_An_Introduction_to_the_Basics_of_Diplomacy.md)

主要内容包括 Scala/Chisel 基础语法、工程实践、常见错误、Chisel 底层原理、香山 Chisel 编码规范、Diplomacy 基础、TileLink/AXI 扩展和实践练习。

### 3. [RISC-V 规范](./docs/3-riscv-specification/)

- [RISC-V Specification](./docs/3-riscv-specification/)

该部分用于沉淀与课程相关的 RISC-V 规范学习资料。

### 4. [香山微结构分析](./docs/4-xiangshan-microarchitecture-analysis/)

- [超标量基础知识](./docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/1_Single_Cycle_vs_Multi_Cycle_vs_Pipeline.md)
- [香山设计文档](./docs/4-xiangshan-microarchitecture-analysis/2-xiangshan-design-document/)
- [香山源码分析](./docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/)

主要内容包括流水线、冒险、依赖、多发射、乱序执行、Tomasulo/Scoreboard、寄存器重命名、派遣队列、发射队列、旁路网络、执行单元、物理寄存器、CSR、ROB、前端/后端/存储子系统源码分析等。

### 5. [香山场景分析](./docs/5-xiangshan-scenarios-analysis/)

- [场景描述](./docs/5-xiangshan-scenarios-analysis/scenarios-description/)
- [场景分析](./docs/5-xiangshan-scenarios-analysis/scenarios-analysis/)

主要内容包括标量 load/store/add、vector load/store/add、AMO、CBO、DIV、FENCE、JAL/JALR、预取、BPU、指令缓存、MDP 等场景。

### 6. [香山调试](./docs/6-xiangshan-debug/)

- [Upper Beginner Level](./docs/6-xiangshan-debug/Upper_Beginner_Level/)

主要内容包括 bug 分析分类、指令生成器问题、香山 RTL 问题、异常触发、CSR、PMA/PMP、X-state、未初始化、指令执行结果错误等案例。

### 7-8. 开发与工具

- [香山开发](./docs/7-xiangshan-development/)
- [香山开发工具](./docs/8-xiangshan-development-tools/)

这两个目录当前为空，作为后续课程内容预留。

## 仓库结构

- `docs/`：课程主体文档
  - `1-xiangshan-development-environment/`：开发环境
  - `2-xiangshan-programming/`：Chisel / Diplomacy 编程
  - `3-riscv-specification/`：RISC-V 规范
  - `4-xiangshan-microarchitecture-analysis/`：香山微结构分析
  - `5-xiangshan-scenarios-analysis/`：典型场景分析
  - `6-xiangshan-debug/`：调试方法与问题分析
  - `7-xiangshan-development/`：后续开发相关内容
  - `8-xiangshan-development-tools/`：后续开发工具相关内容
- `user-guide/`：作业提交、问题反馈等用户指南
- `assets/`：仓库公共资源

## 课程目录

1. [香山开发环境](./docs/1-xiangshan-development-environment/1.CHN/Introduction_Preface.md)
2. [香山编程](./docs/2-xiangshan-programming/1-chisel/Chapter_1_Basic_Scala_Syntax.md)
3. [RISC-V 规范](./docs/3-riscv-specification/)
4. [香山微结构分析](./docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/1_Single_Cycle_vs_Multi_Cycle_vs_Pipeline.md)
5. [香山场景分析](./docs/5-xiangshan-scenarios-analysis/scenarios-description/scalar-load-scenarios.md)
6. [香山调试](./docs/6-xiangshan-debug/Upper_Beginner_Level/Bug_Analysis_Categories.md)
7. [香山开发](./docs/7-xiangshan-development/)
8. [香山开发工具](./docs/8-xiangshan-development-tools/)

## 学习路径

1. 从 `docs/1-xiangshan-development-environment/` 开始，先了解环境和基础流程。
2. 再进入 `docs/2-xiangshan-programming/` 学习 Chisel 与 Diplomacy。
3. 按需阅读 `docs/3` 到 `docs/6`，逐步深入架构、场景和调试。
4. 结合 `user-guide/` 中的说明完成任务提交和问题反馈。

## Documentation Notes

- `docs/1-xiangshan-development-environment/1.CHN/`：中文版本
- `docs/1-xiangshan-development-environment/2.ENG/`：英文版本
- 其他章节目前以中文资料为主，部分内容带英文标题或双语材料。

## Licenses

- 文档内容：`docs/` 下资料遵循 `docs/LICENSE`
- 代码与仓库脚本：遵循 `LICENSE-CODE`

## 相关链接

- [提交 hello xiangshan 指南](./user-guide/how-to-commit-hello-xiangshan.md)
- [文档问题反馈指南](./user-guide/how-to-report-document-issues.md)

