# Xiangshan Processor Learning Course

> Xiangshan Processor Learning Course documentation repository

[中文版 README](./README.md)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE-CODE)
[![License: CC BY-NC 4.0](https://img.shields.io/badge/Docs%20License-CC_BY--NC_4.0-lightgrey.svg)](docs/LICENSE)

This repository is the documentation hub for the Xiangshan Processor Learning Course. It is intended for learners who want to systematically study the Xiangshan development environment, Chisel/Diplomacy programming, the RISC-V specification, Xiangshan microarchitecture, typical scenario analysis, debugging methods, and the development tools that follow.

The repository is currently centered on Markdown documents, images, attachments, and user guides. Course content is still being expanded, and some directories are reserved for future chapters.

## Course Overview

### 1. [Xiangshan Development Environment](./docs/1-xiangshan-development-environment/2.ENG/Introduction_Preface.md)

- [Chinese Course](./docs/1-xiangshan-development-environment/1.CHN/)
- [English Course](./docs/1-xiangshan-development-environment/2.ENG/)
- [Environment FAQ](./docs/1-xiangshan-development-environment/FAQ/xs-env-FAQ.md)

The main content includes the development environment overview, tool preparation, applications, instruction simulators, NEMU/Spike reference models, DRAMsim3, the Xiangshan simulation flow, GEM5, and the Difftest co-simulation framework.

### 2. [Xiangshan Programming](./docs/2-xiangshan-programming/)

- [Chisel Programming](./docs/2-xiangshan-programming/1-chisel/Chapter_1_Basic_Scala_Syntax.md)
- [Diplomacy and Protocol Extensions](./docs/2-xiangshan-programming/2-diplomacy/Chapter_10_An_Introduction_to_the_Basics_of_Diplomacy.md)

The main content includes basic Scala/Chisel syntax, engineering practice, common errors, Chisel fundamentals, Xiangshan Chisel coding standards, Diplomacy basics, TileLink/AXI extensions, and practical exercises.

### 3. [RISC-V Specification](./docs/3-riscv-specification/)

- [RISC-V Specification](./docs/3-riscv-specification/)

This section is used to collect course-related learning materials for the RISC-V specification.

### 4. [Xiangshan Microarchitecture Analysis](./docs/4-xiangshan-microarchitecture-analysis/)

- [Superscalar Basics](./docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/1_Single_Cycle_vs_Multi_Cycle_vs_Pipeline.md)
- [Xiangshan Design Documents](./docs/4-xiangshan-microarchitecture-analysis/2-xiangshan-design-document/)
- [Xiangshan Source Code Analysis](./docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/)

The main content includes pipelines, hazards, dependencies, superscalar issue, out-of-order execution, Tomasulo/Scoreboard, register renaming, dispatch queues, issue queues, bypass networks, execution units, physical registers, CSR, ROB, and source code analysis of the frontend/backend/memory subsystems.

### 5. [Xiangshan Scenario Analysis](./docs/5-xiangshan-scenarios-analysis/)

- [Scenario Descriptions](./docs/5-xiangshan-scenarios-analysis/scenarios-description/)
- [Scenario Analysis](./docs/5-xiangshan-scenarios-analysis/scenarios-analysis/)

The main content includes scalar load/store/add, vector load/store/add, AMO, CBO, DIV, FENCE, JAL/JALR, prefetching, BPU, instruction cache, MDP, and related scenarios.

### 6. [Xiangshan Debugging](./docs/6-xiangshan-debug/)

- [Upper Beginner Level](./docs/6-xiangshan-debug/Upper_Beginner_Level/)

The main content includes bug analysis categories, instruction generator issues, Xiangshan RTL issues, exception triggering, CSR, PMA/PMP, X-state, uninitialized state, and incorrect instruction execution results.

### 7-8. Development and Tools

- [Xiangshan Development](./docs/7-xiangshan-development/)
- [Xiangshan Development Tools](./docs/8-xiangshan-development-tools/)

These two directories are currently empty and reserved for future course content.

## Repository Structure

- `docs/`: main course documentation
  - `1-xiangshan-development-environment/`: development environment
  - `2-xiangshan-programming/`: Chisel / Diplomacy programming
  - `3-riscv-specification/`: RISC-V specification
  - `4-xiangshan-microarchitecture-analysis/`: Xiangshan microarchitecture analysis
  - `5-xiangshan-scenarios-analysis/`: typical scenario analysis
  - `6-xiangshan-debug/`: debugging methods and issue analysis
  - `7-xiangshan-development/`: future development content
  - `8-xiangshan-development-tools/`: future development tools content
- `user-guide/`: task submission and issue reporting guides
- `assets/`: shared repository assets

## Course Outline

1. [Xiangshan Development Environment](./docs/1-xiangshan-development-environment/1.CHN/Introduction_Preface.md)
2. [Xiangshan Programming](./docs/2-xiangshan-programming/1-chisel/Chapter_1_Basic_Scala_Syntax.md)
3. [RISC-V Specification](./docs/3-riscv-specification/)
4. [Xiangshan Microarchitecture Analysis](./docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/1_Single_Cycle_vs_Multi_Cycle_vs_Pipeline.md)
5. [Xiangshan Scenario Analysis](./docs/5-xiangshan-scenarios-analysis/scenarios-description/scalar-load-scenarios.md)
6. [Xiangshan Debugging](./docs/6-xiangshan-debug/Upper_Beginner_Level/Bug_Analysis_Categories.md)
7. [Xiangshan Development](./docs/7-xiangshan-development/)
8. [Xiangshan Development Tools](./docs/8-xiangshan-development-tools/)

## Suggested Learning Path

1. Start with `docs/1-xiangshan-development-environment/` for environment setup and basic workflow.
2. Continue with `docs/2-xiangshan-programming/` for Chisel and Diplomacy.
3. Read `docs/3` through `docs/6` to go deeper into architecture, scenarios, and debugging.
4. Use `user-guide/` for task submission and issue reporting.

## Documentation Notes

- `docs/1-xiangshan-development-environment/1.CHN/`: Chinese materials
- `docs/1-xiangshan-development-environment/2.ENG/`: English materials
- Most other chapters are currently Chinese-first, with some bilingual or English-titled files.

## Licenses

- Documentation: see `docs/LICENSE`
- Code and repository scripts: see `LICENSE-CODE`

## Related Links

- [Hello Xiangshan submission guide](./user-guide/how-to-commit-hello-xiangshan.md)
- [Documentation issue guide](./user-guide/how-to-report-document-issues.md)

