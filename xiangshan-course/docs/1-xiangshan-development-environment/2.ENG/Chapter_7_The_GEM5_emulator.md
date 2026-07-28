# Chapter 7: The GEM5 emulator

# 1 Overview

:::info

### 🎯 Learning Objectives

By the end of this chapter, you will no longer be just a “script runner”; instead, you will be able to answer the following questions:

* **Position:** Where exactly does GEM5 fit into the “full suite” of the XiangShan developing tools?
* **Motivation:** Given that NEMU is available, why should I spend time learning GEM5, which is so slow?
* **Practical Application:** How can I conduct an in-depth “performance checkup” for XiangShan?

:::

## 1.1 What is GEM5?

GEM5 is a powerful, **cycle-accurate emulator** primarily used for research and development in computer architecture. It can simulate a wide range of computer architectures, from simple single-core processors to complex multi-core systems, providing chip designers with a flexible, configurable emulation platform.

**Figure Caption: GEM5 Architecture Diagram**

```plain
Application Layer      ──┐
                         │
System Layer           ──┤  GEM5模拟器
                         │
Microarchitecture Layer ─┘
```

*Figure 1.1: Three-layer architectural model of the GEM5 emulator*

:::info
\*\*What is “cycle-accurate”? \*\*

* **NEMU (instruction-level)**: Focuses only on the result. For example, when executing `add`, it only checks whether 1 + 1 equals 2.
* **GEM5 (cycle-accurate)**: Focuses on the **process**. It simulates the CPU’s behavior for every clock cycle: Which pipeline stage did this `add` operation pass through? Did it “stall” because it couldn’t access the data?

**In a nutshell:** Cycle-accurate simulation is like mapping out every “heartbeat” of the CPU.

:::

## 1.2 Why do we need the XS-GEM5?

XS-GEM5 is a version of the mainline GEM5 specifically optimized for the XiangShan processor. As an open-source RISC-V processor, XiangShan requires dedicated performance evaluation tools to:

1. **Efficiently iterate on new microarchitectural features**—rapidly validating design improvements
2. **Perform end-to-end performance evaluation**—comprehensively assessing system performance
3. **Validate architectural design**—ensuring the design meets its intended objectives

**Chart Description: Relationship Between XS-GEM5 and Mainline GEM5**

```plain
Mainstream GEM5 ──┬── Universal Emulator
                  │
                  └── XS-GEM5 (Exclusive to XiangShan)
```

\_Figure 1.2: Relationship between XS-GEM5 and the mainline GEM5\
\_

*The Role of GEM5 in the Toolchain*

| **Development phase** | **Objective** | **Core Tools** | **Accuracy** | **Speed** | **Character Analogies** |
| --- | --- | --- | --- | --- | --- |
| **Step 1** | *Check if the feature works* | **NEMU** | *Low (instruction-level)* | *Extremely fast* | **Personal Trainer:** Quick Corrections |
| **Step 2** | *Check how fast it runs* | **GEM5** | **High (cycle-accurate)** | **slower** | **Health Screening Center:** In-Depth Analysis |
| **Step 3** | *Hardware Implementation* | **FPGA/Chip** | *Highest* | *Real-time* | **Official Match:** True Performance |

:::info
**XS-GEM5: XiangShan’s Dedicated “Diagnostic Tool”**

**XS-GEM5** is a version of the mainline GEM5 that has been deeply customized for XiangShan. Its unique value lies in:

1. **Microarchitectural alignment:** The cache and branch predictor parameters it simulates are nearly identical to those of the actual XiangShan RTL.
2. **5% Margin of Error:** Its benchmark results deviate only minimally from those of the actual hardware, making them highly valuable for reference.

:::

## 1.3 Key Features of XS-GEM5

* **Aligned with the XiangShan microarchitecture**—adapted to the XiangShan microarchitecture based on the GEM5 O3CPU
* **Supports the Difftest feature**—enables instruction-level comparison and verification against other reference models
* **Supports the Checkpoint feature**—allows execution to resume from a specific state, facilitating debugging
* **Integrated performance analysis scripts**—provides dedicated performance analysis tools for the XiangShan architecture

## 1.4 Learning Path

For beginners, we recommend following this learning path:

### 1.4.1 Phase 1: Basic Understanding (1–2 days)

* Learn the basic concepts and architecture of GEM5
* Understand the differences between XS-GEM5 and the mainline GEM5
* Master the basic emulator operation workflow

### 1.4.2 Phase 2: Environment Setup (2–3 days)

* Learn how to configure the development environment
* Master compiling and building XS-GEM5
* Understand environment variable settings

### 1.4.3 Phase 3: Actual Execution (3–5 days)

* Run simple benchmark programs
* Learn to view and analyze simulation results
* Master basic debugging techniques

### 1.4.4 Phase 4: Advanced Applications (5–7 days)

* Perform verification using Difftest
* Debug using Checkpoint
* Perform performance analysis using perfCCT

**Chart Description: Learning Path Timeline**

```plain
Basic Understanding (1–2 days) → Environment Setup (2–3 days) → Practical Implementation (3–5 days) → Advanced Applications (5–7 days)
```

*Figure 1.3: XS-GEM5 Learning Path Timeline*

***

# 2 Quick Start

## 2.1 Official Documentation

* [Introduction - XiangShan GEM5 Emulator Documentation](https://xs-gem5.readthedocs.io/zh-cn/latest/introduction/)
* [Quick Start - XiangShan GEM5 Emulator Documentation](https://xs-gem5.readthedocs.io/zh-cn/latest/quick_start/#python)

## 2.2 Important File Paths

The path to the XiangShan configuration file in XS-GEM5 is:

```makefile
/nfs/home/yourhome/GEM5/configs/common/xiangshan.py
```

**Figure caption: XS-GEM5 file directory structure**

```plain
GEM5/
├── build/           # Build output
├── configs/         # Configuration files
│   └── common/xiangshan.py
├── src/            # Source code
└── util/           # Utility scripts
```

*Figure 2.1: Main directory structure of XS-GEM5*

***

# 3 Environment Setup and Installation

## 3.1 Environment Variable Settings

Before you start using XS-GEM5, you need to set the following environment variables correctly:

```makefile
export gem5_home=/nfs/home/yourhome/GEM5/

# Set the environment variables required for GEM5
export GCBV_REF_SO=/nfs/home/yourhome/xs-env/NEMU/ready-to-run/riscv64-nemu-interpreter-so
export GCBV_REF_SO=/nfs/home/share/gem5_ci/ref/normal/riscv64-nemu-interpreter-so
```

## 3.2 Frequently Asked Questions and Solutions

### 3.2.1 Segmentation fault issue

You may encounter a segmentation fault when running the following command:

```bash
(py38) yourhome@open01:~/GEM5$ ./build/RISCV/gem5.opt ./configs/example/kmhv3.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin
```

**Error message:**

```plain
gem5 has encountered a segmentation fault!
```

**Causes and Solutions:**\
This is typically caused by a mismatch in the NEMU file version. You need to change the NEMU path to the appropriate version.

**Figure Description: Segmentation Fault Troubleshooting Process**

```plain
Segmentation fault occurs → Check NEMU version → Update environment variables → Run again
```

*Figure 3.1: Segmentation Fault Troubleshooting Flowchart*

***

# 4 NEMU Reference Model Generation

## 4.1 NEMU Configuration File

XiangShan GEM5 requires a specific NEMU reference model. Before compiling NEMU, you must use the correct configuration file. The following is a list of available configuration files:

```bash
yourhome@open01:~/xs-env/NEMU/configs$ ls
riscv32-pa_defconfig                    riscv64-pa_defconfig                  riscv64-xs-cpt-with-libcheckpoint_defconfig  riscv64-xs-kunminghu-v3-ref_defconfig     riscv64-xs-southlake-ref_defconfig
riscv64-gem5-multicore-ref_defconfig    riscv64-spm-ref-xs_defconfig          riscv64-xs-custom-tensor_defconfig           riscv64-xs-novga_defconfig                riscv64-xs-spmem-ref_defconfig
riscv64-gem5-ref_defconfig              riscv64-spm-xs_defconfig              riscv64-xs_defconfig                         riscv64-xs-ref_bitmap_defconfig           riscv64-xs-spmem-so-ref_defconfig
riscv64-nanhu_defconfig                 riscv64-xs-ahead-ref_defconfig        riscv64-xs-diff-spike-agnostic_defconfig     riscv64-xs-ref-debug_defconfig            riscv64-yanqihu_defconfig
riscv64-nanhu-dual-ref_defconfig        riscv64-xs_bitmap_defconfig           riscv64-xs-diff-spike_defconfig              riscv64-xs-ref_defconfig                  riscv64-yanqihu-dual-ref_defconfig
riscv64-nanhu-ref_defconfig             riscv64-xs-clang_defconfig            riscv64-xs-diff-spike-withflash_defconfig    riscv64-xs-southlake-debug_defconfig      riscv64-yanqihu-ref_defconfig
riscv64-nutshell_defconfig              riscv64-xs-clang-ref_defconfig        riscv64-xs-dual-ref-debug_defconfig          riscv64-xs-southlake_defconfig
riscv64-nutshell-diff-spike_defconfig   riscv64-xs-cpt_defconfig              riscv64-xs-dual-ref_defconfig                riscv64-xs-southlake-fpga_defconfig
riscv64-nutshell-ref_defconfig          riscv64-xs-cpt-with-flash_defconfig   riscv64-xs-fpga_defconfig                    riscv64-xs-southlake-ref-debug_defconfig
```

Run `make riscv64-gem5-ref_defconfig` to generate the ref nemu required by gem5

## 4.2 Compilation Commands

Compile GEM5 using the following command:

```makefile
# Using gold-linker can speed up the compilation process
scons build/RISCV/gem5.opt --gold-linker -j$(nproc)

# Or specify a specific number of threads
scons build/RISCV/gem5.opt --gold-linker -j8
```

## 4.3 Environment Setup

Before compiling, you need to set the correct environment variables:

```makefile
export GCBV_REF_SO=/nfs/home/yourhome/xs-env/NEMU/build/riscv64-nemu-interpreter-so
```

## 4.4 Configuring the Python Environment

GEM5 requires a specific Python environment. Here's how to set it up:

```makefile
yourhome@open01:~/GEM5$ conda activate py38
export LD_LIBRARY_PATH=/nfs/home/yourhome/miniconda3/envs/py38/lib:$LD_LIBRARY_PATH

# If the installation package is missing, use `conda install` to install it
# Rebuild GEM5
scons build/RISCV/gem5.opt --gold-linker -j8
```

**Figure Caption: Compilation Process**

```plain
Environment Setup → Configure Variables → Activate Python → Execute Compilation → Verify Results
```

*Figure 4.1: XS-GEM5 Compilation Flowchart*

***

# 5 Running and Testing

## 5.1 Run the benchmark

Use the following command to run the CoreMark benchmark:

```makefile
# Run the workload, making sure to enter the .bin file
./build/RISCV/gem5.opt ./configs/example/kmhv2.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin
```

## 5.2 Retrieve Performance Metrics

Once the run is complete, you can view performance metrics such as IPC (instructions per cycle):

```makefile
# Get IPC
grep 'cpu.ipc' m5out/stats.txt

# Output example
system.cpu.ipc                         0.771001                       # IPC: Instructions Per Cycle ((Count/Cycle))
```

## 5.3 Difftest Features

Difftest is a key feature of XS-GEM5. It can:

* Compare instruction-level differences between the GEM5 emulator and the golden model
* Check for ISA-level differences
* Verify memory access consistency

**Figure Caption: Difftest Verification Process**

```plain
GEM5 Execution → State Recording → NEMU Execution → State Comparison → Reporting Differences
```

*Figure 5.1: Difftest Instruction-Level Verification Process*

***

# 6 Performance analysis tool perfCCT

## 6.1 Analysis of the kmhv2.py Configuration File

:::info
**Configuration File Analysis: ./configs/example/kmhv2.py**

1. **Configuration Details:** Defines the configuration to simulate the XiangShan processor, including parameters such as CPU type, cache architecture, and memory system.
2. **Purpose of the perfcct script:** Collects and analyzes architecture-level performance data and generates visual reports.
3. **Execution Results:** Generates a database file containing detailed performance data.
4. **Result Information:** Includes key performance metrics such as instruction execution traces, cache hit rates, and branch prediction accuracy.

:::

## 6.2 Examples of using perfCCT

```makefile
# Run GEM5 and enable the architecture database
(py38) yourhome@open01:~/GEM5$ ./build/RISCV/gem5.opt ./configs/example/kmhv2.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin --enable-arch-db --arch-db-file=m5out/test.db

# Sample Output
Running CoreMark for 2 iterations
2K performance run parameters for coremark.
CoreMark Size    : 666
Total time (ms)  : 0
Iterations       : 2
Compiler version : GCC13.2.0
seedcrc          : 0xe9f5
[0]crclist       : 0xe714
[0]crcmatrix     : 0x1fd7
[0]crcstate      : 0x8e3a
[0]crcfinal      : 0x72be
Finished in 0 ms.
==================================================
Exiting @ tick 286766946 because m5_exit instruction encountered when simulating XS
build/RISCV/sim/arch_db.cc:56: warn: saving memdb to m5out/test.db ...

# Analyzing the results using perfcct
(py38) yourhome@open01:~/GEM5$ python3 util/perfcct.py m5out/test.db --zoom 1.5 -p 333 --visual | less
```

## 6.3 Complete Workflow

The following is the complete workflow for performance analysis using the XS-GEM5:

```makefile
# 1. Set up the Python environment
conda activate py38

# 2. Compile GEM5
scons build/RISCV/gem5.opt --gold-linker -j64

# 3. Run tests and collect data
./build/RISCV/gem5.opt ./configs/example/kmhv2.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin --enable-arch-db --arch-db-file=m5out/test.db

# 4. View Key Performance Indicators
grep 'cpu.ipc' m5out/stats.txt

# 5. Use perfct for a detailed analysis
python3 util/perfcct.py m5out/test.db --zoom 1.5 -p 333 --visual | less

# 6. Exit the environment
conda deactivate
```

**Figure Caption: perfCCT Analysis Flowchart**

```plain
Run Test → Generate Database → Basic Analysis → Detailed Analysis → Visualize Results
```

*Figure 6.1: Complete perfCCT Performance Analysis Workflow*

***

# Summary

This document provides a detailed overview of how to use the XS-GEM5 emulator and outlines a learning path. With this guide, beginners can:

1. Understand the basic concepts and architecture of the GEM5 emulator
2. Master the configuration and installation of the XS-GEM5 environment
3. Learn how to run benchmark tests and analyze performance results
4. Use advanced features such as Difftest and perfCCT for in-depth analysis


> 更新: 2026-04-23 15:06:11  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/oe6pk1qblblvog1l>
