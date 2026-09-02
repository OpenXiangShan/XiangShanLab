# XiangShanLab: XiangShan Learning and Practice Platform

[中文](./README.md) | [Official Website](https://openxiangshan.cc/) | [GitHub Issues](https://github.com/OpenXiangShan/XiangShanLab/issues)

XiangShanLab is an open learning and practice repository for XiangShan processor development and verification. It organizes materials on the development environment, Scala/Chisel, Diplomacy, RISC-V specifications, processor microarchitecture, runtime scenario analysis, debugging, and engineering practice. It also includes question banks, bug cases, research references, AI-assisted tools, and contest projects.

This repository is intended for:

- beginners who want to run XiangShan and follow a systematic learning path;
- developers learning Chisel, Diplomacy, SoC integration, and interconnect design;
- researchers studying XiangShan pipelines, memory systems, prediction, exceptions, and debug mechanisms;
- engineers working on RISC-V verification, bug diagnosis, waveform analysis, or AI-oriented extensions.

> The repository is under active development. Some chapters, exercises, translations, and tools are incomplete. Issues and pull requests are welcome.

## Quick Start

### 1. Clone the Repository

The repository includes the `wavekit-xslab` submodule, so a recursive clone is recommended:

```bash
git clone --recursive https://github.com/OpenXiangShan/XiangShanLab.git
cd XiangShanLab
```

If you have already cloned the repository without submodules, initialize them separately:

```bash
git submodule update --init --recursive
```

### 2. Choose a Starting Point

- **New to XiangShan**: read the [learning-path guide](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md), then begin with the [development environment course](./xiangshan-course/docs-en/1-xiangshan-development-environment/Introduction_Preface.md).
- **Learning Chisel or Diplomacy**: use the [English programming course](./xiangshan-course/docs-en/2-xiangshan-programming/) together with the [practice projects](./xiangshan-programming-practice/README.md).
- **Studying XiangShan microarchitecture**: proceed from [superscalar fundamentals](./xiangshan-course/docs-en/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/) to the [design documents](./xiangshan-course/docs-en/4-xiangshan-microarchitecture-analysis/2-xiangshan-design-document/) and [source-code analysis](./xiangshan-course/docs-en/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/).
- **Working on scenario verification or debugging**: use the [scenario-analysis materials](./xiangshan-course/docs-en/5-xiangshan-scenarios-analysis/), [debug cases](./xiangshan-course/docs-en/6-xiangshan-debug/), and [tool collection](./tools/README.md).
- **Preparing a contest project**: see the [2026 CIE RISC-V Contest application track](./2026-CIE-RISC-V-Contest-Application-Track/README.md) and its [submission guide](./2026-CIE-RISC-V-Contest-Application-Track/SUBMISSION_GUIDE.md).

## Suggested Learning Path

### Foundation

1. Set up the environment and complete Hello XiangShan.
2. Learn Scala, Chisel, and basic hardware engineering practices.
3. Study the RISC-V ISA, privileged architecture, and common simulators.
4. Build a foundation in pipelines, hazards, out-of-order execution, and memory hierarchies.

### Advanced Study

1. Read the XiangShan design documents and module-level source analyses.
2. Use waveforms to follow instructions from fetch to commit.
3. Study prediction, redirects, memory replay, exceptions, and Difftest scenarios.
4. Reinforce implementation and diagnosis skills through questions, bug cases, and practice projects.

### Specialized Tracks

- **XiangShan processor**: focus on microarchitecture, scenario analysis, debugging, and source-analysis tools.
- **DSU / SoC**: focus on Chisel, Diplomacy, TileLink, AXI, and interconnect projects.
- **XiangShan AI**: after learning the architecture, explore custom instructions, operator acceleration, and hardware/software co-design.

The current [learning-path guide](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md) is written in Chinese and provides more detailed sequencing, task claiming, and delivery guidance.

## Course Structure

The main course is located in [`xiangshan-course/`](./xiangshan-course/README_EN.md):

| Chapter | Topics | Entry |
| --- | --- | --- |
| 1. Development Environment | Tools, applications, NEMU, Spike, DRAMsim3, simulation, GEM5, and Difftest | [Start](./xiangshan-course/docs-en/1-xiangshan-development-environment/Introduction_Preface.md) |
| 2. XiangShan Programming | Scala, Chisel, engineering practice, Diplomacy, TileLink, and AXI | [Start](./xiangshan-course/docs-en/2-xiangshan-programming/) |
| 3. RISC-V Specifications | RISC-V ISA and specification materials used by the course | [Start](./xiangshan-course/docs-en/3-riscv-specification/) |
| 4. Microarchitecture Analysis | Superscalar fundamentals, design documents, and frontend/backend/memory source analysis | [Start](./xiangshan-course/docs-en/4-xiangshan-microarchitecture-analysis/) |
| 5. Scenario Analysis | Instruction lifecycles, prediction, prefetching, memory behavior, replay, and conflicts | [Start](./xiangshan-course/docs-en/5-xiangshan-scenarios-analysis/) |
| 6. Debugging | Bug categories, exceptions, CSRs, PMA/PMP, X-state, and RTL cases | [Start](./xiangshan-course/docs-en/6-xiangshan-debug/) |
| 7. XiangShan NoC | Cache coherence, CHI transactions and nodes, XSCache, DDR controllers, and deadlock scenarios | [Start](./xiangshan-course/docs/7-xiangshan-NoC/) |
| 8. XiangShan AI | Reserved for future XiangShan AI-extension course materials | [Directory](./xiangshan-course/docs/8-xiangshan-AI/) |
| 9. XiangShan AIA | AIA specification, design, integration, and isolation analysis | [Start](./xiangshan-course/docs/9-xiangshan-AIA/) |
| 10. Agile Tools | Reserved for future agile-development tool materials | [Directory](./xiangshan-course/docs/10-xiangshan-agile-tools/) |

Chinese materials live mainly under [`docs/`](./xiangshan-course/docs/), while English materials live under [`docs-en/`](./xiangshan-course/docs-en/) and selected bilingual directories. English coverage currently focuses on the first six chapters, and translation progress varies by chapter.

## Repository Map

| Directory | Purpose | Recommended Entry |
| --- | --- | --- |
| [`xiangshan-course/`](./xiangshan-course/) | The core, structured XiangShan learning course | [Course README](./xiangshan-course/README_EN.md) |
| [`xiangshan-programming-practice/`](./xiangshan-programming-practice/) | Chisel, Diplomacy, IOPMP, AXI XBar, non-blocking cache, and MMU/SMMPT projects | [Practice README](./xiangshan-programming-practice/README.md) |
| [`xiangshan-question-bank/`](./xiangshan-question-bank/) | Questions on development, Chisel, Diplomacy, ISA, microarchitecture, verification, and system software | [Hello XiangShan](./xiangshan-question-bank/1-xiangshan-development/hello-xiangshan.md) |
| [`xiangshan-bugs-library/`](./xiangshan-bugs-library/) | XiangShan issue/PR cases and summaries of microarchitectural and exception-related bugs | [Microarchitecture Summary](./xiangshan-bugs-library/micro-arch-summary.md) |
| [`xiangshan-research/`](./xiangshan-research/) | Papers and research directions related to XiangShan | [Paper Index](./xiangshan-research/xiangshan-related-papers.md) |
| [`tools/`](./tools/) | Source analysis, waveform tracing, verification-scenario generation, bug analysis, and specification lookup | [Tools README](./tools/README.md) |
| [`XiangShanLab-user-guide/`](./XiangShanLab-user-guide/) | Learning paths, assignment submission, documentation feedback, and community collaboration | [Learning-Path Guide](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md) |
| [`2026-CIE-RISC-V-Contest-Application-Track/`](./2026-CIE-RISC-V-Contest-Application-Track/) | The 2026 CIE RISC-V Contest application-track project and encrypted submission area | [Contest README](./2026-CIE-RISC-V-Contest-Application-Track/README.md) |

## Programming Practice

[`xiangshan-programming-practice/`](./xiangshan-programming-practice/README.md) contains standalone projects, including:

- `ChiselIOPMP/`: an IOPMP core and related data-path examples;
- `IopmpSystem/`: a DCache–IOPMP–Memory system experiment;
- `TwoToOneXbarSystem/`: a 2-to-1 AXI4 XBar experiment;
- `NonBlockingCache/`: a non-blocking cache design exercise;
- `mmu-smmpt/`: MMU / SMMPT implementations and tests.

Dependencies and commands differ across projects. Follow the README and build files in each project directory.

## AI and Analysis Tools

[`tools/`](./tools/README.md) provides Codex/agent-oriented workflows for hardware learning, verification, and analysis. Current areas include:

- XiangShan Kunminghu source-code structure and mechanism analysis;
- pipeline and single-instruction waveform tracing with WaveKit;
- conversion of microarchitectural mechanisms into executable verification scenarios;
- closed-loop test generation, simulation, and waveform checking;
- XiangShan issue/PR indexing and bug-cause analysis;
- queries for RISC-V security, isolation, I/O protection, and trusted-debug specifications.

Most tools use `SKILL.md` as their entry point and keep supporting material under `references/`, `scripts/`, and `agents/`. For source, waveform, or simulation tasks, provide the relevant paths, branch or commit, target PC, and run commands.

## Contributing

Contributions are welcome for documentation fixes, new course material, question answers, experiments, bug cases, and tools.

Suggested workflow:

1. Fork the repository and create a focused branch from the latest main branch.
2. Keep changes scoped and verify relative links and commands.
3. Describe the purpose, affected areas, and validation steps in the pull request.
4. If you cannot provide a fix yet, report the problem through [GitHub Issues](https://github.com/OpenXiangShan/XiangShanLab/issues).

The [community governance strategy](./XiangShanLab-user-guide/XiangShan-Community-Decentralized-Governance-Strategy.md) describes the current task-claiming, synchronization, and delivery process in Chinese. The specific Hello XiangShan submission process is documented in the [submission guide](./XiangShanLab-user-guide/how-to-commit-hello-xiangshan.md).

## Licensing and Scope

- This repository contains multiple course, code, data, and tool modules. Follow the license and documentation in each directory.
- Code in `xiangshan-course` uses Apache-2.0, while course documentation uses CC BY-NC 4.0. See the [course README](./xiangshan-course/README_EN.md) for details.
- External papers, specifications, images, source code, and links remain the property of their respective authors and projects.
- This is not the main XiangShan RTL repository. Use the related projects below for the processor source and development environment.

## Related Projects

- [OpenXiangShan/XiangShan](https://github.com/OpenXiangShan/XiangShan): the main XiangShan processor repository;
- [OpenXiangShan/xs-env](https://github.com/OpenXiangShan/xs-env): the XiangShan development and simulation environment;
- [XiangShanLab/wavekit-xslab](https://github.com/XiangShanLab/wavekit-xslab): the WaveKit submodule used by this repository;
- [openxiangshan.cc](https://openxiangshan.cc/): the official XiangShan website.

---

Start with the [learning-path guide](./XiangShanLab-user-guide/XiangShanLab‑Learning‑Path‑Guide.md), complete your first XiangShan run, and then move into programming, microarchitecture, scenario analysis, and debugging.
