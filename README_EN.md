# XiangShanLab: XiangShan Learning and Practice Repository

[中文](./README.md) | [XiangShan Website](https://openxiangshan.cc/) | [GitHub Issues](https://github.com/OpenXiangShan/XiangShanLab/issues)

XiangShanLab is an open repository for learning, developing, verifying, and researching the XiangShan processor. It contains the main course documentation together with programming exercises, question banks, bug cases, analysis tools, research papers, weekly reports, collaborator information, and contest materials.

## Quick Start

### Clone the Repository

The repository includes the `tools/wavekit-xslab` Git submodule. A recursive clone is recommended:

```bash
git clone --recursive https://github.com/OpenXiangShan/XiangShanLab.git
cd XiangShanLab
```

For an existing non-recursive clone:

```bash
git submodule update --init --recursive
```

### Choose an Entry Point

- **New to XiangShan**: read the [learning-path guide](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md), then start with the [development-environment course](./xiangshan-course/docs-en/1-xiangshan-development-environment/).
- **Learning Scala, Chisel, or Diplomacy**: use the [programming course](./xiangshan-course/docs-en/2-xiangshan-programming/) and the [programming exercises](./xiangshan-programming-practice/).
- **Studying RISC-V**: see the [RISC-V specification materials](./xiangshan-course/docs-en/3-riscv-specification/).
- **Studying XiangShan microarchitecture**: begin with [microarchitecture analysis](./xiangshan-course/docs-en/4-xiangshan-microarchitecture-analysis/).
- **Analyzing runtime scenarios or bugs**: use the [scenario-analysis materials](./xiangshan-course/docs-en/5-xiangshan-scenarios-analysis/), [debugging materials](./xiangshan-course/docs-en/6-xiangshan-debug/), and [question bank](./xiangshan-question-bank/).
- **Using AI-assisted source, waveform, or bug analysis**: see the [tool directory](./tools/README.md) and the `SKILL.md` file in each tool.
- **Preparing a 2026 CIE RISC-V Contest project**: read the [application-track description](./2026-CIE-RISC-V-Contest-Application-Track/README.md) and [submission guide](./2026-CIE-RISC-V-Contest-Application-Track/SUBMISSION_GUIDE.md).

## Course Structure

The main course is located in [`xiangshan-course/`](./xiangshan-course/). Chinese materials are under `docs/`; English materials are under `docs-en/`.

| Chapter | Topics | Entry |
| --- | --- | --- |
| 1 | Development environment, simulators, simulation flow, and Difftest | [Start](./xiangshan-course/docs-en/1-xiangshan-development-environment/) |
| 2 | Scala, Chisel, Diplomacy, TileLink, and AXI | [Start](./xiangshan-course/docs-en/2-xiangshan-programming/) |
| 3 | RISC-V instructions, exceptions, and related specifications | [Start](./xiangshan-course/docs-en/3-riscv-specification/) |
| 4 | Superscalar execution, out-of-order execution, frontend, backend, and memory subsystems | [Start](./xiangshan-course/docs-en/4-xiangshan-microarchitecture-analysis/) |
| 5 | Instruction lifecycles, prediction, prefetching, memory behavior, replay, and conflicts | [Start](./xiangshan-course/docs-en/5-xiangshan-scenarios-analysis/) |
| 6 | Bug analysis, exceptions, CSRs, PMA/PMP, X-state, and debugging cases | [Start](./xiangshan-course/docs-en/6-xiangshan-debug/) |
| 7 | XiangShan development materials | [Directory](./xiangshan-course/docs-en/7-xiangshan-development/) |
| 8 | XiangShan development tools | [Directory](./xiangshan-course/docs-en/8-xiangshan-development-tools/) |
| Additional | Chinese materials for NoC, AI, AIA, security, verification, workload analysis, and agile tools | [Chinese course](./xiangshan-course/docs/) |

English coverage is currently smaller than the Chinese course. For the complete and newest chapter structure, see the [Chinese course README](./xiangshan-course/README.md) and the [English course README](./xiangshan-course/README_EN.md).

## Repository Map

| Directory | Contents | Recommended Entry |
| --- | --- | --- |
| [`xiangshan-course/`](./xiangshan-course/) | Structured XiangShan learning materials in Chinese and English | [Course README](./xiangshan-course/README_EN.md) |
| [`xiangshan-programming-practice/`](./xiangshan-programming-practice/) | IOPMP, AXI XBar, non-blocking cache, and MMU/SMMPT projects | [Practice directory](./xiangshan-programming-practice/) |
| [`xiangshan-question-bank/`](./xiangshan-question-bank/) | Questions on XiangShan development, Chisel, Diplomacy, ISA, microarchitecture, verification, and system software | [Hello XiangShan question](./xiangshan-question-bank/1-xiangshan-development/hello-xiangshan.md) |
| [`xiangshan-bugs-library/`](./xiangshan-bugs-library/) | Exception and microarchitecture bug summaries plus data-processing scripts | [Microarchitecture summary](./xiangshan-bugs-library/micro-arch-summary.md) |
| [`tools/`](./tools/) | Source analysis, waveform analysis, scenario extraction, verification drivers, bug analysis, and specification lookup | [Tools README](./tools/README.md) |
| [`XiangShanLab-user-guide/`](./XiangShanLab-user-guide/) | Learning paths, task submission, documentation feedback, and community collaboration | [Learning-path guide](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md) |
| [`xiangshan-research/`](./xiangshan-research/) | XiangShan-related papers, research directions, and literature index | [Paper index](./xiangshan-research/xiangshan-related-papers.md) |
| [`xiangshan-collaborators/`](./xiangshan-collaborators/) | XiangShan collaborators from universities and research institutions | [Collaborators](./xiangshan-collaborators/xiangshan-collaborators.md) |
| [`weekly-status/`](./weekly-status/) | Weekly reports, status template, and report-generation scripts | [Weekly reports](./weekly-status/) |
| [`2026-CIE-RISC-V-Contest-Application-Track/`](./2026-CIE-RISC-V-Contest-Application-Track/) | 2026 CIE RISC-V Contest application-track materials and submission files | [Contest description](./2026-CIE-RISC-V-Contest-Application-Track/README.md) |

## Programming Exercises

Projects under [`xiangshan-programming-practice/`](./xiangshan-programming-practice/) are maintained independently. Follow the README, `Makefile`, or `build.sbt` in each project:

- `ChiselIOPMP/`: a Chisel experiment for a DMA–IOPMP–Memory path;
- `IopmpSystem/`: a system-level DCache–IOPMP–Memory experiment;
- `TwoToOneXbarSystem/`: a 2-to-1 XBar experiment with two AXI4 masters sharing a memory slave;
- `NonBlockingCache/`: non-blocking cache design and tests;
- `mmu-smmpt/`: MMU / SMMPT implementations, modules, and tests.

## Tools and Data

The tools under [`tools/`](./tools/) support Codex/agent-assisted hardware learning and verification:

- `xiangshan-code-analyzer/`: XiangShan Kunminghu source analysis;
- `analyze-xiangshan-wavekit/` and `xiangshan-wave-analysis/`: waveform and pipeline behavior analysis;
- `scenarios-extractor/` and `verification-driver/`: verification-scenario extraction and verification rules;
- `xiangshan-debug/`: debugging and Difftest tracing;
- `xiangshan-bugs-analyzer/`: XiangShan issue/PR indexing and bug analysis;
- `riscv-skill-pack/` and `specification-analyzer/`: RISC-V technical and security specification lookup;
- `wavekit-xslab/`: the WaveKit tool included as a Git submodule.

Most tools use `SKILL.md` as their entry point. For source, waveform, or simulation tasks, provide the relevant path, branch or commit, target PC, and run command.

## Contributing

Contributions are welcome for course material, documentation fixes, question answers, programming exercises, bug cases, and tools.

Please:

1. Create a focused branch from the latest main branch.
2. Check Markdown relative links, commands, and directory names after editing.
3. Describe the changes, affected areas, and validation steps in the pull request.
4. Report issues through [GitHub Issues](https://github.com/OpenXiangShan/XiangShanLab/issues) when a fix is not yet available.

The [community governance strategy](./XiangShanLab-user-guide/XiangShan-Community-Decentralized-Governance-Strategy.md) documents task claiming, synchronization, and delivery. The [Hello XiangShan submission guide](./XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md) documents that specific workflow.

## License and Related Projects

- This repository contains course, code, data, and tool modules with potentially different licenses. Follow the license and documentation in each directory.
- Code in `xiangshan-course` is licensed under Apache-2.0, while course documentation is licensed under CC BY-NC 4.0. See the [course README](./xiangshan-course/README_EN.md).
- This is not the main XiangShan RTL repository. See the related projects:
  - [OpenXiangShan/XiangShan](https://github.com/OpenXiangShan/XiangShan)
  - [OpenXiangShan/xs-env](https://github.com/OpenXiangShan/xs-env)
  - [XiangShanLab/wavekit-xslab](https://github.com/XiangShanLab/wavekit-xslab)
  - [openxiangshan.cc](https://openxiangshan.cc/)
