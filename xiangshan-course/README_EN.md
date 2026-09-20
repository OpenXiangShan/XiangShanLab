# XiangShan Processor Learning Course

[中文 README](./README.md) | [Back to XiangShanLab](../README_EN.md)

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE-CODE)
[![Docs License: CC BY-NC 4.0](https://img.shields.io/badge/Docs%20License-CC_BY--NC_4.0-lightgrey.svg)](docs/LICENSE)

This directory contains the XiangShan Processor Learning Course in XiangShanLab. It is intended for learners who want to study the XiangShan development environment, Chisel/Diplomacy, RISC-V, microarchitecture, execution scenarios, debugging, and related development tools.

The course mainly consists of Markdown documents, with images, presentations, PDFs, code examples, and other learning attachments. Content is continuously evolving, and some chapters are still under construction.

## Quick Start

Chinese materials are under [`docs/`](./docs/), and English materials are under [`docs-en/`](./docs-en/).

Recommended path:

1. Start with the [XiangShan development environment](./docs-en/1-xiangshan-development-environment/Introduction_Preface.md) to understand the toolchain, simulators, and simulation flow.
2. Continue with [XiangShan programming](./docs-en/2-xiangshan-programming/) for Scala, Chisel, Diplomacy, TileLink, and AXI.
3. Select the RISC-V, microarchitecture, scenario-analysis, and debugging chapters according to your goals.
4. Use the [learning-path guide](../XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md) and [submission guide](../XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md) for exercises and contributions.

## Chinese Course

The Chinese course currently contains 14 chapter directories:

| Chapter | Topic | Entry |
| --- | --- | --- |
| 1 | Development environment, simulators, simulation flow, and Difftest | [Development Environment](./docs/1-xiangshan-development-environment/) |
| 2 | Scala, Chisel, Diplomacy, TileLink, and AXI | [Programming](./docs/2-xiangshan-programming/) |
| 3 | RISC-V instructions and specifications | [RISC-V Specification](./docs/3-riscv-specification/) |
| 4 | Superscalar and out-of-order execution, frontend, backend, and memory subsystems | [Microarchitecture Analysis](./docs/4-xiangshan-microarchitecture-analysis/) |
| 5 | Instruction lifecycles, prediction, prefetching, memory behavior, replay, and conflicts | [Scenario Analysis](./docs/5-xiangshan-scenarios-analysis/) |
| 6 | Bug analysis, exceptions, CSRs, PMA/PMP, X-state, and debugging cases | [Debugging](./docs/6-xiangshan-debug/) |
| 7 | NoC, cache coherence, CHI, XSCache, DDR, and deadlocks | [NoC and Caches](./docs/7-xiangshan-NoC/) |
| 8 | XiangShan AI materials | [XiangShan AI](./docs/8-xiangshan-AI/) |
| 9 | AIA specifications, design, integration, and isolation | [XiangShan AIA](./docs/9-xiangshan-AIA/) |
| 10 | Security-related materials | [XiangShan Security](./docs/10-xiangshan-security/) |
| 11 | DDR-related materials | [XiangShan DDR](./docs/11-xiangshan-ddr/) |
| 12 | Workload and virtual-machine scenario analysis | [Workload Analysis](./docs/12-workload-analysis/) |
| 13 | Verification-related materials | [XiangShan Verification](./docs/13-xiangshang-verification/) |
| 14 | XiangShan agile tools | [Agile Tools](./docs/14-xiangshan-aglie-tools/) |

Chapters 1 through 6 currently contain the most complete foundational and advanced materials. Chapters 7 through 14 are being expanded.

## English Course

The English course currently contains the following chapters:

| Chapter | Topic | Entry |
| --- | --- | --- |
| 1 | XiangShan development environment, simulators, and Difftest | [Development Environment](./docs-en/1-xiangshan-development-environment/) |
| 2 | Scala, Chisel, Diplomacy, TileLink, and AXI | [Programming](./docs-en/2-xiangshan-programming/) |
| 3 | RISC-V specification materials | [RISC-V Specification](./docs-en/3-riscv-specification/) |
| 4 | Microarchitecture fundamentals, design documents, and source analysis | [Microarchitecture Analysis](./docs-en/4-xiangshan-microarchitecture-analysis/) |
| 5 | Pipeline and instruction execution scenarios | [Scenario Analysis](./docs-en/5-xiangshan-scenarios-analysis/) |
| 6 | Debugging and bug-analysis materials | [Debugging](./docs-en/6-xiangshan-debug/) |
| 7 | XiangShan development materials (reserved) | [Development](./docs-en/7-xiangshan-development/) |
| 8 | XiangShan development tools (reserved) | [Development Tools](./docs-en/8-xiangshan-development-tools/) |

The English tree also contains a standalone [XiangShan microarchitecture collection](./docs-en/xiangshan-microarchitecture/). English coverage is currently smaller than the Chinese course; the directory structure is the source of truth.

## Repository Layout

```text
xiangshan-course/
├── README.md
├── README_EN.md
├── LICENSE-CODE
├── assets/
│   ├── diagrams/
│   └── images/
├── docs/                 # Chinese course, 14 chapter directories
└── docs-en/              # English course, 8 chapter directories and a microarchitecture topic
```

Images, attachments, and code examples are generally kept beside the chapter that uses them. Shared assets are stored under `assets/`.

## Related Links

- [XiangShanLab learning-path guide](../XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md)
- [Hello XiangShan submission guide](../XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md)
- [Documentation issue guide](../XiangShanLab-user-guide/how-to-report-document-issues.md)
- [XiangShanLab root README](../README_EN.md)

## Licenses

- Course and repository code follow [`LICENSE-CODE`](./LICENSE-CODE).
- Course documentation follows [`docs/LICENSE`](./docs/LICENSE).
- When a file or subdirectory provides additional license information, follow that local notice.
