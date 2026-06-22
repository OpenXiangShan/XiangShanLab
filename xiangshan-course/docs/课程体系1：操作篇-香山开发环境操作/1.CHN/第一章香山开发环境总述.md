# 第一章 香山开发环境总述

:::info
🎯全章节学习目标

* **<font style="color:rgb(15, 17, 21);">理解香山开发环境</font>**<font style="color:rgb(15, 17, 21);">：了解香山处理器开发所涉及的完整流程</font>
* **<font style="color:rgb(15, 17, 21);">认识工具链关系</font>**<font style="color:rgb(15, 17, 21);">：了解各仓库如何协同工作</font>
* **<font style="color:rgb(15, 17, 21);">完成 Hello XiangShan</font>**<font style="color:rgb(15, 17, 21);">：在香山环境中运行第一个RISC-V程序</font>
* **<font style="color:rgb(15, 17, 21);">理解香山项目的多仓库协作架构</font>**<font style="color:rgb(15, 17, 21);">：xs-env是“入口”（环境集成）、XiangShan（核心设计）、NEMU（参考模型）、nexus-am（应用程序框架）、difftest（协同验证）等。</font>
* **<font style="color:rgb(15, 17, 21);">理解DiffTest 是灵魂</font>**<font style="color:rgb(15, 17, 21);">：DiffTest 是香山非常核心的机制，香山区别于“玩具 CPU”的灵魂</font>
* **<font style="color:rgb(15, 17, 21);">建立信心</font>**<font style="color:rgb(15, 17, 21);">：通过实际操作，建立"我能学到也能做到"的信心</font>

:::

欢迎来到香山处理器的世界！如果你是第一次接触处理器设计，可能会感到有些复杂。别担心，本文将以最友好的方式带你入门。

# 1 香山开发环境总述

## 1.1 香山环境的整体架构

![1778742075650-5f81d957-9a5c-437e-98a5-53800d014b73.png](./img/HwOjzyf_D-IM3VVV/1778742075650-5f81d957-9a5c-437e-98a5-53800d014b73-549267.png)

图1：香山环境的整体框架

从图1，可以直观地感受到**香山作为一个高性能 RISC-V 处理器项目**，不是单一仓库，而是一整套协同工作的工程体系，可以分成四个主要部分：

```
1. **设计工具**：使用 Chisel 语言设计 CPU 硬件（Chisel 是一种基于 Scala 的硬件描述语言）。<font style="color:#8A8F8D;">你可以把它想象成建筑师的绘图板，在这里设计出处理器的"蓝图"。</font>
2. **仿真与验证工具**：如 Verilator 或 VCS 用于模拟运行 CPU；DiffTest 用于验证结果正确性。<font style="color:#8A8F8D;">设计完成后，需要通过仿真来验证功能是否正确。这就像在工厂里用模型测试产品设计，确保大规模生产前没有问题。</font>
3. **配套工具链**：包括 RISC-V 编译器、调试工具和配置文件，用于生成能在处理器上运行的程序和控制仿真。<font style="color:#8A8F8D;">这是连接软件和硬件的桥梁。</font>
4. **性能分析工具**：如 DRAMSim3 和 SimPoint，用于评估CPU的性能表现，找出瓶颈和优化空间。<font style="color:#8A8F8D;">用于评估处理器的性能表现，找出瓶颈和优化空间。</font>
```

:::info <font style="color:rgb(15, 17, 21);">旁白：想象你要</font>**<font style="color:rgb(15, 17, 21);">设计和测试一台新型汽车</font>**<font style="color:rgb(15, 17, 21);">：</font>

* **<font style="color:rgb(15, 17, 21);">设计工具</font>**~~<font style="color:rgb(15, 17, 21);"></font>~~<font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> 你在图纸上画汽车设计图</font>
* **<font style="color:rgb(15, 17, 21);">仿真工具</font>**~~<font style="color:rgb(15, 17, 21);"></font>~~<font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> 在电脑上模拟汽车行驶</font>
* **<font style="color:rgb(15, 17, 21);">配套工具链</font>**~~<font style="color:rgb(15, 17, 21);"></font>~~<font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> 制造汽车所需的工具箱和测试设备</font>
* **<font style="color:rgb(15, 17, 21);">性能分析工具</font>**<font style="color:rgb(15, 17, 21);"> </font><font style="color:rgb(15, 17, 21);">👉</font><font style="color:rgb(15, 17, 21);"> 用仪器测量汽车的速度和油耗</font>

<font style="color:rgb(15, 17, 21);">香山开发就是</font>**<font style="color:rgb(15, 17, 21);">在电脑上设计和测试RISC-V处理器</font>**<font style="color:rgb(15, 17, 21);">的完整流程！</font>

:::

:::color4
**新手建议：** 初次接触时，先理解这四大模块的关系，不需要深究每个细节。后续章节会逐一讲解。

:::

## 1.2 香山开发理念

**<font style="color:#000000;">核心开发理念：</font>**<font style="color:#000000;"> 香山处理器的开发 从</font>**<font style="color:#000000;">功能正确</font>**<font style="color:#000000;">到</font>**<font style="color:#000000;">性能探索</font>**<font style="color:#000000;">，最终落地于</font>**<font style="color:#000000;">高质量实现</font>**<font style="color:#000000;">的技术路径。这三者层层递进，共同构成了香山作为开源高性能处理器项目的核心开发理念。</font>

### <font style="color:#000000;">1. 指令级功能实现的正确性：大厦的地基</font>

<font style="color:#000000;">这是处理器开发的起点，也是所有工作的前提。</font>

* **<font style="color:#000000;">功能正确</font>**<font style="color:#000000;">：无论微架构多么先进，首先必须保证处理器能够准确无误地执行指令集中的每一条指令。</font>
* **<font style="color:#000000;">验证驱动</font>**<font style="color:#000000;">：在香山的开发中，这一层面通常通过大量的指令测试集（如RISC-V 架构测试套件）和形式化验证来保证。只有确保了指令级的功能正确，后续的性能优化才有意义。</font>

### <font style="color:#000000;">2. 微架构性能探索：创新的引擎</font>

<font style="color:#000000;">在功能正确的基础上，香山作为一个科研与工程结合的产物，非常注重微架构层面的创新与探索。</font>

* **<font style="color:#000000;">寻找性能瓶颈</font>**<font style="color:#000000;">：探索意味着分析现有设计的不足。例如，前端取指带宽是否足够？分支预测器是否足够精准？数据预取能否更智能？</font>
* **<font style="color:#000000;">架构空间探索</font>**<font style="color:#000000;">：通过模拟器和性能模型，尝试不同的缓存层次结构、流水线深度、乱序执行窗口大小等参数，寻找性能、功耗与面积的帕累托最优解。</font>
* **<font style="color:#000000;">南湖/昆明湖架构</font>**<font style="color:#000000;">：香山的历代架构（如南湖、昆明湖）本身就是这种探索的成果，它们代表了针对不同目标场景（如桌面级、服务器级）的微架构设计尝试。</font>

### <font style="color:#000000;">3. 高性能RTL的实现：从图纸到现实</font>

<font style="color:#000000;">这是将微架构的构想转化为实际可流片、可运行的硬件电路的过程。</font>

* **<font style="color:#000000;">可综合的代码</font>**<font style="color:#000000;">：RTL（寄存器传输级）代码不仅仅是软件模拟，它必须能够被逻辑综合工具转换成实际的门级网表。这要求代码风格严谨，时序清晰。</font>
* **<font style="color:#000000;">敏捷开发语言</font>**<font style="color:#000000;">：香山使用 Chisel（一种嵌入在Scala中的硬件构造语言）来生成RTL。这种高级语言允许开发者以更高效、更参数化的方式描述复杂的硬件逻辑，从而支撑起高性能设计的复杂度。</font>
* **<font style="color:#000000;">PPA（性能、功耗、面积）调优</font>**<font style="color:#000000;">：在RTL层面，需要精细地控制关键路径的延迟、优化功耗，并合理布局，确保最终流片出来的芯片能够在目标频率下稳定运行。</font>

***

**<font style="color:#000000;">总结来看，香山处理器的开发逻辑是：</font>**

1. **<font style="color:#000000;">首先要“能用”</font>**<font style="color:#000000;">（指令正确）；</font>
2. **<font style="color:#000000;">其次要“好用”</font>**<font style="color:#000000;">（微架构探索，追求更高性能）；</font>
3. **<font style="color:#000000;">最终要“能做出来”</font>**<font style="color:#000000;">（高性能RTL实现，确保可流片）。</font>

<font style="color:#000000;">这种三位一体的模式，使得香山不仅是一个学术界的研究平台，也是一个具备工业级潜力的高性能处理器核。</font>

## 1.3 香山开发环境学习路径

![1773819758648-3b96213e-c08c-4321-b3af-3ff7b7551cc5.png](./img/HwOjzyf_D-IM3VVV/1773819758648-3b96213e-c08c-4321-b3af-3ff7b7551cc5-530672.png)

图2：程序运行流程图

***

**指令功能正确性：**

1. 指令级模拟器作为指令功能正确性的黄金模型，RTL 实现和Gem5 实现的执行结果需要通过Difftest 和NEMU 做比较
2. 适配香山的**指令模拟器学习路径（图2-黄色）** - 使用软件模拟器（如 <font style="color:#DF2A3F;">NEMU、Spike</font>）快速验证程序逻辑。
   * **特点：** 速度最快
   * **输出：** 指令执行结果日志，每条指令执行的结果，包括架构寄存器（整数、浮点、向量、CSR）、Memory 的值
   * **目标**：功能验证

**性能微架构探索：**

1. **微架构性能探索路径（图2-紫色）** - 使用 Gem5 等精确模拟器，模拟每个时钟周期的行为。
   * **特点：** 精度高，适合性能分析
   * \*\*输出：\*\*详细的性能数据
   * **目标**：性能探索

**RTL 落地实现：**

1. **RTL 实现路径（图2-蓝色） - 这是香山处理器的真实硬件设计，使用 Chisel 语言编写。**
   * \*\*特点： \*\*最真实但最慢，需要将硬件描述转换为可仿真代码
   * \*\*输出： \*\*编译阶段输出可仿真的system-verilog 代码、执行阶段输出波形仿真文件
   * \*\*目标： \*\*实现落地

**最终验证：** 三条路径的结果通过 DiffTest 工具进行交叉比对，确保所有实现的行为完全一致。

***

:::color4
**学习路径建议：**

1. 初学者先从理解整体流程开始
2. 重点掌握（绿色） RTL 实现路径（这是核心）
3. 逐步了解其他两条路径的作用

:::

:::info
旁白：

* **硬件开发者**：香山的开发方式是：\
  \*\*Chisel 写硬件 → 生成 Verilog → 仿真 → 跑 workload → 用 NEMU 当黄金模型做差分验证 \*\*

性能探索者

* 香山不是“写 RTL 就完了”，而是**设计 + 验证 + 软件 + 性能评估**一整条链路

:::

## 1.3 香山开发环境仓库介绍

香山开发环境由多个开源项目组成，每个项目都有特定的职责。下表列出了主要组件及其作用：

| 组件名称 | 官方地址 | 核心职责 | 学习重点 |
| --- | --- | --- | --- |
| **xs-env** | <https://github.com/OpenXiangShan/xs-env.git> | 环境集成与一键部署 | 掌握环境配置方法，这是所有开发的起点 |
| **XiangShan** | <https://github.com/OpenXiangShan/XiangShan.git> | 处理器核心设计 | 理解 Chisel 代码结构，这是处理器的"心脏" |
| **NEMU** | <https://github.com/OpenXiangShan/NEMU.git> | 高效指令集模拟器 | 作为验证的"黄金标准"，理解其工作原理 |
| **nexus-am** | <https://github.com/OpenXiangShan/nexus-am.git> | 抽象机器库 | 学习如何为裸机环境编写程序 |
| **difftest** | <https://github.com/OpenXiangShan/difftest.git> | 协同仿真验证框架 | 掌握验证方法，确保设计正确性 |
| **DRAMsim3** | <https://github.com/OpenXiangShan/DRAMsim3.git> | DRAM 内存系统仿真 | 理解内存子系统对性能的影响 |
| **香山Gem5** | <https://github.com/OpenXiangShan/GEM5.git> | 适配香山的周期级系统模拟器 | 用于架构探索和性能分析 |
| **香山设计文档** | <https://github.com/OpenXiangShan/XiangShan-doc.git> | 香山昆明湖V2 微架构设计文档 | 了解香山微架构, 包括处理器前端、后端、访存等 |

![1770806502366-0a0f3e4a-44da-4f48-b970-ce712305f090.png](./img/HwOjzyf_D-IM3VVV/1770806502366-0a0f3e4a-44da-4f48-b970-ce712305f090-028835.png)

图3. 香山代码仓库截图

**官方文档资源：**

* 主文档：[XiangShan 官方文档](https://docs.xiangshan.cc/zh-cn/latest/)
* 遇到问题时，优先查阅官方文档

:::info

* Tips 可以采用豆包解析github，初步了解香山的各个仓库

<https://www.doubao.com/chat/?channel=xiazais>

:::

![1773385727542-b2b4cadc-241f-4ffa-89c3-4d414f10ae94.png](./img/HwOjzyf_D-IM3VVV/1773385727542-b2b4cadc-241f-4ffa-89c3-4d414f10ae94-147343.png)

:::info
在对话框问感兴趣的问题，由大模型做仓库介绍和代码讲解

<https://www.doubao.com/chat/38416864906978818>

:::

![1773385957337-8626e146-ce86-4cc7-9231-ad62650a5a1c.png](./img/HwOjzyf_D-IM3VVV/1773385957337-8626e146-ce86-4cc7-9231-ad62650a5a1c-819998.png)

## 1.4 Workload 运行流程

### 1.4.1 什么是 Workload？

Workload 是CPU运行的程序，比如简单的 "Hello XiangShan" 或复杂的性能测试程序。在香山环境中，有两种主要方式运行 Workload：

### 方式一：

### 使用 NEMU 运行（快速测试）

**适用场景：** 快速验证程序逻辑，不涉及硬件细节\
**流程：**

1. 使用 nexus-am 生成 Workload 程序
2. 编译 NEMU 模拟器
3. 在 NEMU 中运行程序

**优点：** 速度快，几秒内完成\
**缺点：** 不验证硬件实现细节

### 方式二：

### 使用香山 RTL 仿真运行（真实测试）

**适用场景：** 验证硬件设计正确性\
**流程：**

1. 生成 Workload 程序
2. 将 Chisel 设计转换为 Verilog 代码
3. 使用 Verilator 编译成可仿真程序
4. 运行仿真，验证结果

:::danger
**特别推荐：使用 DiffTest 模式**

DiffTest 是**香山环境的一大特色**，它让 NEMU（软件模拟器）和香山 RTL（硬件设计）同时运行同一个程序，并实时比对两者的状态。这就像有一个经验丰富的老师在旁边，每一步都检查你的答案是否正确。

:::

**DiffTest 工作流程：**

```plain
程序 → NEMU（参考模型） → 状态比对 → 报告差异
         ↓
程序 → 香山 RTL（待验证） → 状态比对 → 报告差异
```

如果两者状态始终一致，说明硬件设计正确；如果出现差异，就需要调试硬件设计。

## 1.5 新手实战 - 运行第一个 Hello XiangShan

这是你的第一个实战任务！跟着下面的步骤，你将成功在香山环境中运行第一个程序。

### 1.5.1 第一步：环境准备

```bash
# 1. 克隆环境仓库
git clone https://github.com/OpenXiangShan/xs-env.git
cd xs-env

# 2. 安装需要的包 (本地开发, 配置新环境需要 root 权限执行该 shell 脚本)
sudo -s ./setup-tools.sh

# 3. 执行环境设置脚本
source setup.sh
source env.sh

# 4. （可选）更新子模块
source update-submodule.sh
```

### 1.5.2 第二步：编译香山仿真器

这是最耗时的步骤，请耐心等待：

```bash
# 进入香山项目目录
cd $NOOP_HOME

# 初始化项目
make init

# !!!NOTE MinimalConfig 只是用来测试环境，在实际使用时，使用默认配置，不加COFIG 配置 即为默认配置
# 编译仿真器（使用最小配置加快速度）
# 注意：这一步可能需要1小时左右 (取决于设备性能)
make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32
```

**参数说明：**

* `CONFIG=MinimalConfig`：使用最小配置，编译更快 (该配置移除了L2缓存, 有效降低了设计的复杂度, 但该配置下的香山核仍是乱序超标量处理器核)

但是这个配置能做的很少，无法实际使用，可以选择其他配置：

[xiangshan env Q\&A 操作顺序](https://bosc.yuque.com/staff-xmw8rg/fb7qy3/uq1ha9ygllae5drs?inner=UrQgm)

* `EMU_TRACE=1`：启用调试跟踪功能
* `-j32`：并行编译，数字根据你的 CPU 核心数调整 (可以通过 `nproc`指令查询 CPU 核心数量, 建议保留 1-2 个 CPU 核心用于后台其他进程)

### 1.5.3 第三步：编译 Hello XiangShan 程序

```bash
# 进入应用程序目录
cd $AM_HOME/apps/hello/

# 编译 Hello XiangShan
make ARCH=riscv64-xs
```

编译成功后，会在 `build/` 目录下生成 `hello-riscv64-xs.bin` 文件。

### 1.5.4 第四步：运行程序

```bash
# 返回香山项目目录
cd $NOOP_HOME

# 运行 Hello XiangShan（不使用 DiffTest，速度更快）
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --no-diff
```

如果一切正常，你应该看到输出：

```plain
Hello XiangShan!
```

![1770882600453-461702bc-7d74-4357-924b-3677969832cd.png](./img/HwOjzyf_D-IM3VVV/1770882600453-461702bc-7d74-4357-924b-3677969832cd-648534.png)

恭喜！你成功运行了第一个程序！

### 1.5.5 第五步：（进阶）使用 DiffTest 运行

```bash
# 使用 DiffTest 验证
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --diff ready-to-run/riscv64-nemu-interpreter-so
```

如果看到 "**All tests passed**"，**说明硬件设计完全正确**。接下来, 我们手动注入一个错误, 并观察 DiffTest 如何发现并汇报错误:

```bash
# 使用 sed 指令向 Alu.scala 中注入一个错误, 该错误会使加法指令计算错误的结果
sed -i 's/io.add := io.src(0) + io.src(1)/io.add := io.src(0)\/\/ + io.src(1)/' src/main/scala/xiangshan/backend/fu/Alu.scala

# 重新编译仿真程序 (这一步很重要 在每次更新 RTL 代码后都需要重新执行这条指令)
make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32
# !!!NOTE MinimalConfig 只是用来测试环境，在实际使用时，使用默认配置，不加COFIG 配置 即为默认配置

# 使用 DiffTest 再次进行验证
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --diff ready-to-run/riscv64-nemu-interpreter-so
```

现在的香山 RTL 包含了我们手动注入的错误, 因此包含加法指令的程序都有可能出现错误, 你应该会看到输出:

![1772243408467-23ecd62d-4b4a-40e5-8e1c-73beddb5b59e.png](./img/HwOjzyf_D-IM3VVV/1772243408467-23ecd62d-4b4a-40e5-8e1c-73beddb5b59e-442573.png)

这表明, DiffTest 在 PC `0x0080000078`处发现了测试设计 (DUT, 也就是香山处理器核) 和参考设计 (REF, 也就是 NEMU 指令集模拟器) 在 `a0`寄存器出现了不一样的结果 (我们认为 REF 是正确的实现, 所以 `right`表示 REF 给出的值, 而 `wrong`表示 DUT 给出的值). 因为出现了错误, 所以程序的执行被迫停止. 在香山仿真中, 仿真程序还会列举出所有寄存器的内容, 为调试错误提供帮助. 在最后, 请记得将刚才植入错误的文件恢复原样:

```bash
# 恢复 Alu.scala, 这一步将撤销我们先前手动注入的错误
git restore src/main/scala/xiangshan/backend/fu/Alu.scala

# 重新编译仿真程序 (跳过这一步会导致后续的操作中使用包含错误的香山处理器设计)
make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32
```

## 1.6 日常使用指南

### 1.6.1 每次使用环境的流程

每次打开新的终端窗口，都需要重新配置环境：

```bash
cd xs-env
source env.sh
cd $NOOP_HOME
```

**小技巧：** 可以在 `~/.bashrc` 中添加别名简化操作：

```bash
alias sourceXS="source /path/to/xs-env/env.sh"
```

### 1.6.2 更新项目分支

```bash
cd XiangShan
make init
```

这个命令会更新所有子模块到正确的分支。

## 1.7 故障排除指南

[FAQ 问题汇总](https://bosc.yuque.com/staff-xmw8rg/fb7qy3/he7pildwqd681q26)

### 1.7.2 寻求帮助的途径

1. **查阅官方文档** - 最权威的参考资料
2. **搜索社区讨论** - 大部分问题都有现成解决方案
3. **提问** - 在社区中描述具体问题和已尝试的解决方法

## 1.8 学习路径规划

![画板](./img/HwOjzyf_D-IM3VVV/1773648085655-e578c4a6-3ddc-4eeb-84d4-4d629b508379-474519.jpeg)

## 1.9 学习收获与建议

通过本文的学习

**已经掌握的知识：**

1. 理解了香山开发环境的整体架构
2. 掌握了 RISC-V 处理器的开发验证流程
3. 学会了如何配置和使用香山环境
4. 成功运行了第一个 Hello XiangShan 程序
5. 了解了后续的学习路径

**给新手的建议：**

\*\*从简单开始 - \*\*不要一开始就试图理解所有细节

\*\*动手实践 - \*\*处理器设计是实践性很强的领域，多动手操作

\*\*善用资源 - \*\*官方文档、社区讨论都是宝贵的学习资源

\*\*保持耐心 - \*\*编译和仿真可能需要很长时间，这是正常的

\*\*参与社区 - \*\*开源项目的魅力在于社区，积极参与讨论和贡献

### 下一步行动

:::danger

1. 如果你成功运行了 Hello XiangShan，恭喜你！可以尝试运行更复杂的程序
2. 如果遇到问题，不要气馁，这是学习的一部分
3. 考虑加入香山开源社区，与其他开发者交流学习

:::

香山处理器是一个优秀的开源项目，它不仅提供了高性能的 RISC-V 处理器设计，更重要的是构建了一个完整的学习和实践平台。无论你是学生、研究者还是工程师，都能在这里找到学习和成长的机会。

祝你在处理器设计的道路上越走越远！


> 更新: 2026-05-14 15:03:25  
