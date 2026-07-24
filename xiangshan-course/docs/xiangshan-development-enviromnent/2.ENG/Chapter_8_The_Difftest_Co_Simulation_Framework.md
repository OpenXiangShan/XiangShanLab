# Chapter 8: The Difftest Co-Simulation Framework

# 1 Overview of the Difftest Co-Simulation Framework

:::info

## 🎯 Learning Objectives

By the end of this chapter, you will have developed the core mindset required for high-performance processor verification and will be able to answer the following questions:

* **What is DiffTest?** (Understand the essence of differential testing)
* **Why run tests “back-to-back”?** (Understand the rationale behind verification)
* **Where should I look when XiangShan makes a mistake?** (Master log analysis and fault localization)
* **How do I enable these “eyes” using parameters?** (Master core operations)

:::

## 1.1 What is Difftest?

Difftest is an efficient processor verification method that ensures the correctness of a processor design by comparing the execution results of the design under test (DUT) with those of a reference model (REF). It is one of the core components of the XiangShan processor verification infrastructure.

:::info
**Finding that missing “1” among hundreds of billions of instructions.**

* **Challenge:** Modern processors execute billions of instructions per second. If just one instruction calculates `1 + 1 = 3`, the program might not crash until hours later.
* **Dilemma:** When the program crashes, you have no idea which second or which line of hardware code caused the error.
* **Solution:** Hire a “top-notch detective”—**DiffTest**. It monitors every instruction and, as soon as it detects a “suspect” (inconsistency), immediately “apprehends” it on the spot.

:::

**Key Learning Points for This Chapter:**

1. How difftest works
2. What difftest compares
3. The impact of enabling difftest during compilation on RTL
4. How to configure difftest at runtime
5. The difference between --diff and --no-diff
6. How to interpret difftest failure logs

## 1.2 Key Features

### 1.2.1 Infrastructure for Processor Verification

* **Multi-platform support:** Provides APIs for HDLs (such as Chisel and Verilog), and supports RTL emulators (such as Verilator and VCS) and RISC-V ISSs (such as NEMU and Spike) as reference designs.
* **Hardware acceleration support:** Supports integration with hardware simulation accelerators to improve verification efficiency.
* **End-to-End Coverage:** Provides comprehensive verification support for processor design, ranging from instruction set verification to microarchitecture verification.

### 1.2.2 Co-Simulation on SMP Processors

* **Complex Scenario Verification:** Supports SMP Linux kernels and multithreaded programs, capable of simulating real-world multi-core processor runtime environments.
* **Consistency Checks:** Performs online checks for cache and memory consistency to ensure the correctness and stability of multi-core processors.

### 1.2.3 Summary of Advantages

* **Efficient and Reliable:** Automated comparison and verification reduce the workload and error rate associated with manual verification, improving verification efficiency and reliability.
* **Flexible Scalability:** Supports multiple reference designs and hardware platforms, adapting to diverse verification requirements and scenarios.
* **Early Issue Detection:** Identifies potential issues during the design phase, reducing the costs and risks associated with later fixes.

```plain
DUT Execution → State Extraction → REF Execution → State Comparison → Result Report
```

**Figure 1.1 Basic Verification Workflow of Difftest**

## 1.3 Learning Path

### 1.3.1 Phase 1: Conceptual Understanding (1–2 days)

* Understand the basic principles and core concepts of Difftest
* Learn about the concepts of DUT and REF
* Master the basic workflow of co-simulation

### 1.3.2 Phase 2: Environment Setup (2–3 days)

* Learn the source code directory structure of Difftest
* Master compilation and configuration methods
* Understand environment variable settings

### 1.3.3 Phase 3: Practical Application (3–5 days)

* Run simple Difftest tests
* Learn how to use command-line arguments
* Master basic debugging techniques

### 1.3.4 Phase 4: Advanced Applications (5–7 days)

* Analyze Difftest failure logs
* Use different reference models (NEMU/Spike)
* Understand the 32 check items for state comparison

***

# 2 Introduction to the Principle

## 2.1 Core Principle

Difftest compares the execution results of a model (hereinafter referred to as the DUT) with those of a correctly implemented model (hereinafter referred to as the REF).

**Core Concept:** For two implementations based on the same specification, given the same defined inputs, their behavior should be consistent.

```plain
DUT (Design Under Test) ──┬── State Probe
                          │
REF (Reference Model)   ──┼── ISA Checker
                          │
                 Comparison Results
```

**Figure 2.1 DiffTest Framework Architecture Diagram**

:::info
DiffTest establishes a rigorous real-time verification system:

1. **DUT (Design Under Test):** The XiangShan processor (your hardware code). It is the “student” being examined and also the potential “suspect.”
2. **REF (Reference Model):** NEMU or Spike. It is the absolutely correct “gold standard” and serves as the “expert witness.”
3. **Probe:** A “monitor” embedded in the hardware, responsible for reporting XiangShan’s secrets (registers, memory state) to the detective in real time.

:::

## 2.2 Verification Comparison Process

Difftest performs verification comparisons through the following steps:

1. **Determine whether the first instruction has been committed**
2. **Determine whether the time limit for simulating the system has been reached**
3. **Check for interrupts or exceptions; if any are found, handle them**
4. **Check the execution results of normal instructions**

2.3 References

* [DiffTest - XiangShan Official Documentation](https://docs.xiangshan.cc/zh-cn/latest/tools/difftest/)
* [DiffTest Principles PPT](https://oscpu.github.io/ysyx/events/2021-07-17_Difftest/DiffTest-%E4%B8%80%E7%A7%8D%E9%AB%98%E6%95%88%E7%9A%84%E5%A4%84%E7%90%86%E5%99%A8%E9%AA%8C%E8%AF%81%E6%96%B9%E6%B3%95.pdf)

```plain
Instruction Commit Checks ──┬── TLB Checks
                            ├── Cache Coherency Checks
                            ├── Vectorization Checks
                            └── 28 Other Status Checks
```

**Figure 2.2: 32 state checks covered by Difftest**

:::info
**Monitoring Network: What Are These 32 “Comprehensive Checks” Comparing?**

DiffTest doesn’t just look at the final results; it covers **32 key state checks** to ensure that every step of the hardware’s operation complies with specifications:

* **Register State:** Whether the values of general-purpose registers and the program counter (PC) are consistent.
* **Memory Access:** Whether the data and addresses being read or written are correct.
* **Exception Handling:** Whether the jump logic is consistent when encountering invalid instructions or interrupts.
* **Cache Coherency:** Whether data across all cache levels is synchronized in a multi-core environment.

:::

## 2.4 Difftest Framework Architecture Diagram

```plain
Difftest Framework
├── Core Concepts
│   ├── DUT (Device Under Test)
│   ├── REF (Reference Model)
│   └── State Comparison
├── Verification Process
│   ├── Instruction Commit Check
│   ├── State Synchronization
│   ├── Comparison Verification
│   └── Result Processing
├── Verification Items
│   ├── Register State
│   ├── Memory Access
│   ├── Exception Handling
│   └── Timing Consistency
└── Application Scenarios
    ├── Single-Core Verification
    ├── Multi-Core Verification
    ├── System-Level Verification
    └── Performance Analysis
```

**Figure 2.3 Mind map of the overall Difftest architecture**

***

# 3 Source Code Structure and Compilation

## 3.1 Source Code Directory Structure

```bash
(base) limiao@hwfuzz:~/Fuzzing/xs-env/XiangShan/difftest$ tree -L 5
.
├── build.sc
├── config
│   └── config.h
├── doc
│   └── usage.md
├── LICENSE
├── Makefile
├── README.md
├── scripts
│   └── coverage
│       ├── coverage.py
│       ├── statistics.py
│       └── vtransform.py
├── src
│   ├── main
│   │   └── scala
│   │       ├── Batch.scala
│   │       ├── Bundles.scala
│   │       ├── common
│   │       │   └── Mem.scala
│   │       ├── Difftest.scala # Defines the modules and interfaces for retrieving various states of the DUT
│   │       ├── DPIC.scala
│   │       └── Merge.scala
│   └── test
│       ├── csrc
│       │   ├── common
│       │   │   ├── common.cpp
│       │   │   ├── common.h
│       │   │   ├── compress.cpp
│       │   │   ├── compress.h
│       │   │   ├── coverage.cpp
│       │   │   ├── coverage.h
│       │   │   ├── device.cpp
│       │   │   ├── device.h
│       │   │   ├── dut.cpp
│       │   │   ├── dut.h
│       │   │   ├── flash.cpp
│       │   │   ├── flash.h
│       │   │   ├── golden.cpp
│       │   │   ├── golden.h
│       │   │   ├── keyboard.cpp
│       │   │   ├── lightsss.cpp
│       │   │   ├── lightsss.h
│       │   │   ├── macro.h
│       │   │   ├── main.cpp
│       │   │   ├── ram.cpp
│       │   │   ├── ram.h
│       │   │   ├── remote_bitbang.cpp
│       │   │   ├── remote_bitbang.h
│       │   │   ├── sdcard.cpp
│       │   │   ├── sdcard.h
│       │   │   ├── SimJTAG.cpp
│       │   │   ├── uart.cpp
│       │   │   └── vga.cpp
│       │   ├── difftest
│       │   │   ├── difftest.cpp
│       │   │   ├── difftest.h # Declarations of all type methods for difftest
│       │   │   ├── difftrace.cpp
│       │   │   ├── difftrace.h
│       │   │   ├── goldenmem.cpp
│       │   │   ├── goldenmem.h
│       │   │   ├── refproxy.cpp
│       │   │   └── refproxy.h
│       │   ├── plugin
│       │   │   ├── include
│       │   │   ├── runahead
│       │   │   └── spikedasm
│       │   ├── vcs
│       │   │   └── vcs_main.cpp
│       │   └── verilator
│       │       ├── emu.cpp
│       │       ├── emu.h
│       │       ├── snapshot.cpp
│       │       └── snapshot.h
│       ├── scala
│       │   └── DifftestMain.scala
│       └── vsrc
│           ├── common
│           │   ├── assert.v
│           │   ├── ref.v
│           │   └── SimJTAG.v
│           └── vcs
│               └── top.v
├── vcs.mk
└── verilator.mk
```

```plain
Difftest Source Code Structure
├── Configuration Layer
│   ├── config.h
│   └── Makefile
├── Core Layer
│   ├── Difftest.scala (Interface Definitions)
│   ├── difftest.h (Type Declarations)
│   └── difftest.cpp (Implementation)
├── Tools Layer
│   ├── Code Coverage Analysis
│   ├── Performance Metrics
│   └── Visualization
├── Testing Layer
│   ├── Unit Testing
│   ├── Integration Testing
│   └── System Testing
└── Platform Adaptation
    ├── Verilator Support
    ├── VCS Support
    └── Hardware Accelerators
```

```plain
Difftest.scala ── Defines the interface for retrieving the DUT state
difftest.h ────── Declares all type methods
main.cpp ──────── Main program entry point
config.h ──────── Configuration file
```

**Figure 3.1: Description of the Functions of Key Difftest Files**

## 3.2 Effects of Enabling Difftest During Compilation

Enabling Difftest during compilation has the following effects on the RTL:

1. **Added probe modules:** State extraction probes are inserted into the RTL.
2. **Interface extensions:** Interfaces for communicating with the reference model are added.
3. **Performance overhead:** A certain amount of time is required to simulate the model.
4. **Debugging support:** More detailed debugging information is provided.

# 4 Instructions for Use

## 4.1 Usage Process

1. **Execute Instructions:** The processor commits instructions or updates other information.
2. **Synchronize Simulation:** The emulator executes the same instructions to ensure synchronization with the design under test.
3. **Compare States:** Compare the microarchitectural states between the design under test and a reference design (such as NEMU or Spike) to check for any discrepancies.
4. **Process Results:** Based on the comparison results, decide whether to terminate the test or continue execution.

```plain
Start → Load Workload → DUT Execution → State Extraction → REF Synchronous Execution
                              ↓
                     State Comparison → Consistent → Continue Execution
                              ↓
                        Inconsistent → Report Error → End
```

**Figure 4.1: Complete Difftest Workflow Diagram**

## 4.2 Command-line arguments

### 4.2.1 Basic Parameters

`-i, --image=FILE` specifies the workload to be executed by the design under test (DUT).

### 4.2.2 Important Parameters Descriptions

* `--diff`: Enable the Difftest comparison feature
* `--no-diff`: Disable the Difftest comparison feature
* `--help`: Display a complete list of command-line options

```plain
--diff mode:    DUT execution → State comparison → REF verification → Report results
--no-diff mode: DUT execution → Direct output of results (no verification)
```

**Figure 4.2: Comparison of --diff and --no-diff modes**

## 4.3 API Calls

For more information about the API, please refer to: [GitHub - OpenXiangShan/difftest readme](https://github.com/OpenXiangShan/difftest/tree/da8d34450242bd1451cf187664b9259c73bb6183?tab=readme-ov-file#apis)

# Chapter 5: Troubleshooting and Log Analysis

## 5.1 Enabling Difftest at Runtime

To enable Difftest at runtime, the following settings must be configured correctly:

1. **Reference model path:** Set the correct NEMU or Spike path
2. **Environment variables:** Configure the necessary environment variables
3. **Workload:** Specify the binary file to be tested
4. **Comparison options:** Select the state items to be compared

## 5.2 Interpreting Difftest Failure Logs

### 5.2.1 Common Error Types

1. **Register value mismatch:** Inconsistencies in general-purpose registers, PC values, etc.
2. **Memory access errors:** Inconsistencies in memory read/write results
3. **Exception handling errors:** Inconsistencies in interrupt or exception handling
4. **Timing-related errors:** Inconsistencies in timing-related states

### 5.2.2 Log Interpretation Method

When a Difftest fails, an error is reported immediately, and the log displays the following:

1. **Mismatched registers:** Lists all registers with mismatched values
2. **Expected vs. actual values:** Displays the expected value from the REF and the actual value from the DUT
3. **Error location:** Indicates the instruction location where the error occurred
4. **Context information:** Provides the execution context at the time the error occurred

```plain
Error Type: Register Mismatch
Location: 0x80000000
Register: x1
Expected Value: 0x00000001
Actual Value: 0x00000002
Context: Last 10 instructions
```

**Figure 5.1 Example structure of a Difftest error log**

:::info
\*\*How to Interpret Failure Logs? \*\*

When the debugger detects an inconsistency, it prints the following log and forces the simulation to stop:

**Three-Step Troubleshooting Process:**

1. **Check the PC:** Search for this address in the disassembly file `build/xxx.txt` to identify the specific instruction.
2. **Examine the waveform:** Locate the waveform for the corresponding clock cycle based on the PC, and examine the specific electrical signals during instruction execution.
3. **Modify the code:** Correct the Chisel logic and rerun the simulation until you see `HIT GOOD TRAP`.

:::

## 5.3 Practical Application Examples

### 5.3.1 XiangShan + NEMU

If the comparison results differ, the values of the inconsistent registers will be printed to the console, allowing you to compare and view the current state.\
!\[\[Pasted image 20260212144920.png]]

### 5.3.2 gem5 + NEMU

For detailed log output, see: [附件: long\_text\_5C3FF108-02FE-429A-9B8A-5EEF7147ED6A.txt](./attachments/Tx2WKbaV7BeEOgLO/long_text_5C3FF108-02FE-429A-9B8A-5EEF7147ED6A.txt)

# 6 Summary

## 6.1 Key Points Summary

1. **Difftest Principle:** Verification based on a comparison of the states of the DUT and REF
2. **Verification Scope:** Covers 32 state check items
3. **Usage Process:** Four-step method (Execute, Synchronize, Compare, Process)
4. **Troubleshooting:** Identify issues through log analysis

**Self-Assessment Checklist**

* \[ ] Why does DiffTest pause at the moment a bug occurs, rather than hours later?
* \[ ] Can you list at least three types of states that DiffTest compares? (Hint: registers, memory, exceptions)
* \[ ] What type of file follows `--diff` in the command-line arguments? (Hint: NEMU’s dynamic library .so)
* \[ ] In the log, whose results do `Expected` and `Actual` represent, respectively?

## 6.2 Suggestions for Further Study

1. **Dive into the source code:** Study the implementation details of `difftest.cpp` and `difftest.h`
2. **Expand the application:** Try applying Difftest to other processor designs
3. **Performance optimization:** Learn how to reduce the time it takes to simulate with Difftest
4. **Custom development:** Add new state checks based on your requirements

##


> 更新: 2026-04-24 01:30:16  
