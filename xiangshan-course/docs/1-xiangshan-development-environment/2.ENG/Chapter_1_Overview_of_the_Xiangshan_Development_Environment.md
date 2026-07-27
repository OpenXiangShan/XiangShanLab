# Chapter 1: Overview of the Xiangshan Development Environment

:::info
🎯Learning Objectives

* **Understanding the Xiangshan Development Environment:** A Comprehensive Overview of the Xiangshan Processor Development Process
* **Understanding the Toolchain:** How Different Repositories Work Together
* **Complete “Hello XiangShan”:** Run Your First RISC-V Program in the XiangShan Environment
* **Understanding the multi-repository collaboration architecture of the Xiangshan project:** xs-env serves as the “entry point” (environment integration), XiangShan (core design), NEMU (reference model), nexus-am (application framework), difftest (collaborative verification), and others.
* **Understanding DiffTest is critical:** DiffTest is a core mechanism of Xiangshan and the very essence that sets Xiangshan apart from “toy CPUs.”
* **Building confidence:** Through hands-on practice, build the confidence that “I can learn this and I can do it.”

:::

Welcome to the world of Xiangshan processor! If this is your first time exploring processor design, it might seem a bit complicated. Don’t worry—this article will guide you through the basics in the most accessible way possible.

# 1 Overview of the Xiangshan Development Environment

## 1.1 Overall Structure of Xiangshan Environment

![figure-001-overall-structure-xiangshan-environment](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-001-overall-structure-xiangshan-environment.webp)

Figure 1: Overall Framework of the Xiangshan Environment

Figure 1 clearly illustrates that Xiangshan, as a high-performance RISC-V processor project, is not a single repository but rather a comprehensive engineering ecosystem that works in tandem. It can be divided into four main components:

```
1. **Design Tools:** Use the Chisel language to design CPU hardware (Chisel is a Scala-based hardware description language). <font style="color:#8A8F8D;">You can think of it as an architect’s drawing board, where you create the “blueprints” for the processor.</font>
2. Simulation and verification tools: such as Verilator or VCS for simulating CPU execution; DiffTest for verifying the result correctness. <font style="color:#8A8F8D;">Once the design is complete, simulation is required to verify that the functionality is correct. This is similar to using prototypes to test product designs in a factory to ensure there are no issues before mass production.</font>
3. **Supporting toolchain:** Includes RISC-V compilers, debugging tools, and configuration files used to generate programs that run on the processor and control simulations. <font style="color:#8A8F8D;">This serves as the bridge between software and hardware.</font>
4. **Performance analysis tools:** such as DRAMSim3 and SimPoint, which are used to evaluate CPU performance, identify bottlenecks, and pinpoint opportunities for optimization. <font style="color:#8A8F8D;">These tools are used to evaluate processor performance, identify bottlenecks, and pinpoint opportunities for optimization.</font>
```

:::info
Narrator: Imagine you’re **designing and testing a new car**:

* **Design tools**<font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> You are drawing a car design on the blueprint</font>
* **Simulation tools**<font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> Simulating car driving on a computer</font>
* **Supporting toolchain**<font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> Toolkits and testing equipment required for automobile manufacturing</font>
* **<font style="color:rgb(15, 17, 21);">Performance Analysis Tool</font>**<font style="color:rgb(15, 17, 21);"> </font><font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> Measuring a car's speed and fuel consumption with instruments</font>

Xiangshan Development is the complete process of **designing and testing RISC-V processors on a computer**!

:::

:::color4
**Tips for Beginners:** When you first encounter this material, focus on understanding the relationships between these four major modules; there’s no need to delve into every detail. Subsequent chapters will cover each one in turn.

:::

## 1.2 Xiangshan Development Philosophy

**Core Development Philosophy:** The development of the Xiangshan processor follows a technical path that progresses from **ensuring functional correctness** to **exploring performance**, and ultimately culminates in a **high-quality implementation**. These three stages build upon one another and together form the core development philosophy of Xiangshan as an open-source, high-performance processor project.

### <font style="color:#000000;">1. Correctness of instruction-level implementations: The foundation of a building</font>

<font style="color:#000000;">This is the starting point for processor development and the foundation for all subsequent work.</font>

* **<font style="color:#000000;">Functionally correct</font>**<font style="color:#000000;">: No matter how advanced the microarchitecture may be, the first priority must be to ensure that the processor can execute every instruction in the instruction set accurately and without error.</font>
* **Verification Driven:** In Xiangshan’s development, this level is typically ensured through extensive instruction-level test suites (such as the RISC-V Architecture Test Suite) and formal verification. Only after ensuring functional correctness at the instruction level does subsequent performance optimization become meaningful.

### <font style="color:#000000;">2. Exploring Microarchitecture Performance: The Engine of Innovation</font>

<font style="color:#000000;">Building on its robust functionality, Xiangshan—as a product of the integration of research and engineering—places great emphasis on innovation and exploration at the microarchitecture level.</font>

* **Identifying Performance Bottlenecks:** Exploration involves analyzing the shortcomings of existing designs. For example, is the front-end instruction fetch bandwidth sufficient? Is the branch predictor accurate enough? Could data prefetching be optimized?
* **Architectural Space Exploration:** Using simulators and performance models, experiment with various parameters—such as cache hierarchies, pipeline depths, and out-of-order execution window sizes—to identify the Pareto-optimal solution in terms of performance, power consumption, and area.
* **Nanhu/Kunminghu Architecture:** The successive architectures of Xiangshan (such as Nanhu and Kunminghu) are themselves the result of this exploration; they represent attempts at microarchitecture design tailored to different target scenarios (such as desktop-class and server-class systems).

### <font style="color:#000000;">3. Implementing High-Performance RTL: From Schematics to Reality</font>

<font style="color:#000000;">This is the process of transforming a microarchitecture conceptualization into actual hardware circuits that can be fabricated and operated.</font>

* **Synthesizable code:** RTL (Register-Transfer Level) code is not merely for software simulation; it must be capable of being converted by logic synthesis tools into an actual gate-level netlist. This requires a rigorous coding style and clear timing specifications.
* **Agile Development Language:** Xiangshan uses Chisel (a hardware construction language embedded in Scala) to generate RTL. This high-level language allows developers to describe complex hardware logic in a more efficient and parameterized manner, thereby supporting the complexity of high-performance designs.
* **PPA (Performance, Power, Area) Optimization:** At the RTL level, it is necessary to precisely control critical path delays, optimize power consumption, and implement a reasonable layout to ensure that the final chip operates stably at the target frequency.

***

**<font style="color:#000000;">In summary, the development logic behind the Xiangshan processor is as follows:</font>**

1. **First and foremost, it must “work”** (the instructions must be correct);
2. **Second, it must be “highly functional”** (exploring microstructures to achieve higher performance);
3. **Ultimately, it must be “implementable”** (a high-performance RTL implementation that ensures it can be fabricated).

<font style="color:#000000;">This three-in-one model makes Xiangshan not only a research platform for the academic community, but also a high-performance processor core with industrial-grade potential.</font>

## 1.3 Xiangshan Development Environment Learning Path

![figure-002-xiangshan-development-environment-learning](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-002-xiangshan-development-environment-learning.png)

Figure 2: Program Execution Flowchart

**Correctness of instruction functionality:**

1. Since the instruction-level simulator serves as the gold standard for verifying the correctness of instruction-level functionality, the execution results of the RTL implementation and the Gem5 implementation must be compared using Difftest and NEMU.
2. XianShan adaptive **instruction simulator learning path (Figure 2—yellow)** — Use software simulators (such as <font style="color:#DF2A3F;">NEMU or Spike</font>) to quickly verify program logic.
   * **Feature:** Fastest speed
   * **Output:** Log of instruction execution results, showing the result of each instruction, including the values of the architecture registers (integer, floating-point, vector, CSR) and memory.
   * **Objective:** Functional Validation

**Exploring Performance Microarchitectures:**

1. **Microarchitecture Performance Exploration Path (Figure 2—Purple)** — Use precise simulators such as Gem5 to simulate behavior on a per-clock-cycle basis.
   * **Features:** High precision, suitable for performance analysis
   * **Output:** Detailed performance data
   * **Objective:** Performance Exploration

**RTL Implementation:**

1. **RTL Implementation Path (Figure 2—blue) — This is the actual hardware design of the Xiangshan processor, written in the Chisel language.**
   * **Features:** The most precise but slowest method, requiring the conversion of hardware descriptions into simulatable code
   * **Output:** SystemVerilog code suitable for simulation during the compilation phase; waveform simulation files during the execution phase
   * **Goal:** Implementation

**Final verification:** The results from the three paths were cross-checked using the DiffTest tool to ensure that all implementations behave identically.

:::color4
**Suggested learning path:**

1. Beginners should start by understanding the overall process
2. Focus on mastering the (green) RTL implementation path (this is the core)
3. Gradually familiarize yourself with the roles of the other two paths

:::

:::info
Narrator:

* Hardware Developer: Xiangshan's development approach is:\
  \*\*Chisel: Write hardware → Generate Verilog → Simulate → Run workload → Use NEMU as the gold model for differential verification \*\*

Performance Explorer

* Xiangshan isn’t just about “writing RTL and calling it a day”; it encompasses the entire workflow—**from design and verification to software and performance evaluation**.

:::

## 1.4 Introduction to the Xiangshan Development Environment Repository

The Xiangshan development environment consists of multiple open-source projects, each with specific responsibilities. The table below lists the main components and their functions:

| Component Name | Official Repository Address | Key Responsibilities | Key Learning Points |
| --- | --- | --- | --- |
| **xs-env** | <https://github.com/OpenXiangShan/xs-env.git> | Environment Integration and One-Click Deployment | Mastering environment configuration is the starting point for all development |
| **XiangShan** | <https://github.com/OpenXiangShan/XiangShan.git> | Core Processor Design | Understanding the Chisel code structure: the “heart” of the processor |
| **NEMU** | <https://github.com/OpenXiangShan/NEMU.git> | High-Performance Instruction Set Simulator | As the “gold standard” for verification, understanding how it works |
| **nexus-am** | <https://github.com/OpenXiangShan/nexus-am.git> | Abstract Machine Library | Learn how to write programs for a bare-metal environment |
| **difftest** | <https://github.com/OpenXiangShan/difftest.git> | Co-simulation Verification Framework | Master verification methods to ensure design correctness |
| **DRAMsim3** | <https://github.com/OpenXiangShan/DRAMsim3.git> | DRAM Memory System Simulation | Understanding the Impact of the Memory Subsystem on Performance |
| **XiangShan Gem5** | <https://github.com/OpenXiangShan/GEM5.git> | Cycle-accurate system simulator adapted for Xiangshan | For architecture exploration and performance analysis |
| **XiangShan Document** | <https://github.com/OpenXiangShan/XiangShan-doc.git> | Xiangshan Kunminghu V2 Microarchitecture Design Document | Understand the Xiangshan microarchitecture, including the processor front-end, back-end, memory access, and more |

![figure-003-introduction-xiangshan-development-environment](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-003-introduction-xiangshan-development-environment.png)

Figure 3. Screenshot of the Xiangshan code repository

**Official documentation resources:**

* Main Documentation: [XiangShan Official Documentation](https://docs.xiangshan.cc/zh-cn/latest/)
* If you encounter any issues, consult the official documentation first

:::info

* Tips: You can use Doubao to explore GitHub and get a basic understanding of Xiangshan's various repositories.

<https://www.doubao.com/chat/?channel=xiazais>

:::

![figure-004-introduction-xiangshan-development-environment](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-004-introduction-xiangshan-development-environment.png)

:::info
Ask questions of interest in the dialog box, and the large language model will provide an overview of the repository and explain the code.

<https://www.doubao.com/chat/38416864906978818>

:::

![figure-005-introduction-xiangshan-development-environment](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-005-introduction-xiangshan-development-environment.png)

## 1.5 Workload Execution Process

### 1.5.1 What is a workload?

A workload is a program running on the CPU, such as a simple “Hello XiangShan” program or a complex performance test program. In the XiangShan environment, there are two main ways to run workloads:

### Method 1:

### Run using NEMU (Quick Test)

**Use Cases:** Quickly verify program logic without delving into hardware details\
**Process:**

1. Generate a Workload program using nexus-am
2. Compiling the NEMU Emulator
3. Running programs in NEMU

**Advantages:** Fast—completed in just a few seconds\
**Disadvantage:** Does not verify the hardware implementation details

### Method 2:

### Run a simulation using Xiangshan RTL (real-world testing)

**Use Case:** Verifying the correctness of hardware designs\
**Process:**

1. Generate the Workload program
2. Convert Chisel designs to Verilog code
3. Compile into a simulable program using Verilator
4. Run the simulation and verify the results

:::danger
**Special Recommendation: Use DiffTest mode**

DiffTest is a **key feature of Xiangshan Environment**; it allows NEMU (the software simulator) and Xiangshan RTL (the hardware design) to run the same program simultaneously while comparing their states in real time. It’s like having an experienced teacher right beside you, checking whether your answers are correct at every step.

:::

**DiffTest Workflow:**

```plain
Program → NEMU (Reference Model) → State Comparison → Report Discrepancies
                       ↓
Program → Xiangshan RTL (Design Under Test) → State Comparison → Report Discrepancies
```

If the two states remain consistent, it indicates that the hardware design is correct; if discrepancies arise, the hardware design needs to be debugged.

## 1.6 Getting Started: Running Your First “Hello XiangShan”

This is your first hands-on assignment! Follow the steps below to successfully run your first program in the Xiangshan environment.

### 1.6.1 Step 1: Preparing the Environment

```bash
# 1. Clone the environment repository
git clone https://github.com/OpenXiangShan/xs-env.git
cd xs-env

# 2. Install the required packages (For local development, you will need root privileges to run this shell script when setting up a new environment)
sudo -s ./setup-tools.sh

# 3. Run the environment setup script
source setup.sh
source env.sh

# 4. (Optional) Update submodule
source update-submodule.sh
```

### 1.6.2 Step 2: Compile the Xiangshan emulator

This is the most time-consuming step; please be patient:

```bash
# Go to the Xiangshan Project Directory
cd $NOOP_HOME

# Initialize the project
make init

# !!!NOTE: MinimalConfig is intended solely for testing purposes. In actual use, use the default configuration; omitting the `COFIG` directive will result in the default configuration.
# Compile the emulator (using the minimal configuration to speed up the process)
# Note: This step may take approximately 1 hour (depending on device performance)
make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32
```

**Parameter Description:**

* `CONFIG=MinimalConfig`: Using the minimal configuration results in faster compilation (this configuration removes the L2 cache, effectively reducing the design complexity; however, the Xiangshan core remains an out-of-order, superscalar processor core under this configuration)

However, this configuration is quite limited and not practical for real-world use. You may want to consider other configurations:[xiangshan env Q\&A 操作顺序](https://bosc.yuque.com/staff-xmw8rg/fb7qy3/uq1ha9ygllae5drs?inner=UrQgm)

* `EMU_TRACE=1`: Enable debug tracing
* `-j32`: Parallel compilation; adjust the number based on the number of CPU cores (you can use the `nproc`command to check the number of CPU cores; it is recommended to reserve 1–2 CPU cores for other background processes)

### 1.6.3 Step 3: Compile the “Hello XiangShan” program

```bash
# Go to the Applications folder
cd $AM_HOME/apps/hello/

# Compile Hello XiangShan
make ARCH=riscv64-xs
```

Once the compilation is complete, the file `hello-riscv64-xs.bin`will be generated in the `build/` directory.

### 1.6.4 Step 4: Run the program

```bash
# Back to the Xiangshan Project Directory
cd $NOOP_HOME

# Run Hello XiangShan (without DiffTest; faster)
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --no-diff
```

If everything goes well, you should see the following output:

```plain
Hello XiangShan!
```

![figure-006-xiang-shan-without](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-006-xiang-shan-without.png)

Congratulations! You've successfully run your first program!

### 1.6.5 Step 5: (Advanced) Run using DiffTest

```bash
# Verify using DiffTest
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --diff ready-to-run/riscv64-nemu-interpreter-so
```

If you see **“All tests passed,” it means the hardware design is completely correct**. Next, we'll manually introduce an error and observe how DiffTest detects and reports it:

```bash
# Use the `sed` command to inject an error into `Alu.scala` that causes the addition instruction to produce an incorrect result.
sed -i 's/io.add := io.src(0) + io.src(1)/io.add := io.src(0)\/\/ + io.src(1)/' src/main/scala/xiangshan/backend/fu/Alu.scala

# Recompile the simulation program (This step is important; you must run this command every time you update the RTL code)
make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32
# !!!NOTE: MinimalConfig is intended solely for testing purposes. In actual use, use the default configuration; omitting the `COFIG` directive will result in the default configuration.

# Verify again using DiffTest
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --diff ready-to-run/riscv64-nemu-interpreter-so
```

The current version of Xiangshan RTL contains errors that we manually introduced, so any program that includes addition instructions may produce errors. You should see the following output:

![figure-007-verify-again-using-diff](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-007-verify-again-using-diff.png)

This indicates that DiffTest detected a discrepancy between the Device Under Test (DUT, i.e., the Xiangshan processor core) and the Reference Design (REF, i.e., the NEMU instruction set simulator) in the value of register `a0`at PC `0x0080000078`(since we consider REF to be the correct implementation, “right” denotes the value provided by REF, while “wrong” denotes the value provided by DUT). Because an error occurred, program execution was forced to stop. In the Xiangshan emulation, the emulator also lists the contents of all registers to assist with debugging. Finally, please remember to restore the file from which the error was introduced to its original state:

```bash
# Restore Alu.scala; this step will undo the error we previously introduced manually.
git restore src/main/scala/xiangshan/backend/fu/Alu.scala

# Recompile the simulation program (skipping this step will result in the use of a Shangshan processor design containing errors in subsequent operations)
make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32
```

## 1.7 Guide to Daily Use

### 1.7.1 Procedure for each use of the environment

Every time you open a new terminal window, you have to reconfigure the environment:

```bash
cd xs-env
source env.sh
cd $NOOP_HOME
```

Tip: You can add aliases to `~/.bashrc`to simplify the process:

```bash
alias sourceXS="source /path/to/xs-env/env.sh"
```

### 1.7.2 Update project branch

```bash
cd XiangShan
make init
```

This command will update all submodules to the correct branch.

## 1.8 Troubleshooting Guide

[FAQ 问题汇总](https://bosc.yuque.com/staff-xmw8rg/fb7qy3/he7pildwqd681q26)

### 1.8.2 Ways to Get Help

1. **Consult the official documentation** - the most authoritative resource
2. **Search community discussions** - most issues have ready-made solutions
3. **Ask a question** - describe the specific problem and the solutions you’ve already tried in the community

## 1.9 Learning Path Planning

![figure-008-learning-planning-ask-question](./img/chapter-1-overview-of-the-xiangshan-development-environment/figure-008-learning-planning-ask-question.jpeg)

## 1.10 Learning Outcomes and Recommendations

After studying this article

**What you've already learned:**

1. Gained an understanding of the overall architecture of the XiangShan development environment
2. Mastered the development and verification process for RISC-V processors
3. Learned how to configure and use the XiangShan environment
4. Successfully ran the first “Hello XiangShan” program
5. Learned about the next steps in the learning path

**Tips for beginners:**

**Start simple** - don’t try to understand every detail right from the start.

**Get hands-on** - processor design is a highly practical field, so practice as much as you can.

**Make good use of resources** - official documentation and community discussions are valuable learning resources.

**Be patient** - compilation and simulation may take a long time, which is normal.

**Get involved in the community** - the appeal of open-source projects lies in their communities, so actively participate in discussions and contribute.

### Next Steps

:::danger

1. If you successfully ran “Hello XiangShan,” congratulations! You can now try running more complex programs.
2. If you encounter any problems, don’t get discouraged—it’s all part of the learning process.
3. Consider joining the XiangShan Open Source Community to connect and learn from other developers.

:::

The Xiangshan processor is an outstanding open-source project. Not only does it offer a high-performance RISC-V processor design, but more importantly, it provides a comprehensive platform for learning and hands-on practice. Whether you are a student, researcher, or engineer, you will find opportunities to learn and grow here.

We wish you every success on your journey in processor design!


> 更新: 2026-05-14 15:04:38  
