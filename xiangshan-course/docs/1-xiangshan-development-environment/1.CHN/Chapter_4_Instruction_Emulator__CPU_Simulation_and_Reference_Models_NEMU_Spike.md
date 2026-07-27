# 第四章 指令模拟器：CPU仿真与参考模型（NEMU + Spike）

# 4 仿真器

:::info

### 🎯\*\*<font style="color:rgb(38, 38, 38);"> 本章目标</font>\*\*

<font style="color:rgb(38, 38, 38);">学完本章，你应该能回答：</font>

* <font style="color:rgb(38, 38, 38);">为什么 CPU 还没做出来程序却能运行？</font>
* <font style="color:rgb(38, 38, 38);">什么是仿真 CPU？</font>
* <font style="color:rgb(38, 38, 38);">为什么系统里有两个仿真器？</font>
* <font style="color:rgb(38, 38, 38);">Spike 和 NEMU 分别负责什么？</font>
* <font style="color:rgb(38, 38, 38);">系统如何判断 CPU 是否正确？</font>

:::

## ![figure-001-cpu-bug-run-debug](./img/chapter-4-instruction-emulator-cpu-simulation-and-reference-models-nemu-spike/figure-001-cpu-bug-run-debug.png)

## 4.1 模拟指令器的作用

### 4.1.1为什么需要“仿真CPU”

<font style="color:rgb(38, 38, 38);">在真实的香山 CPU 硬件（电路逻辑）还没完全做出来之前，或者当你怀疑硬件设计有 Bug 时，</font>仍然需要：<font style="color:rgb(38, 38, 38);">能运行程序、测试程序、验证 CPU 是否正确、调试CPU设计。</font>

:::info
旁白：你有没有想过？

* <font style="color:rgb(38, 38, 38);">真实 CPU 还没做出来，已经写好的程序运行在哪里？</font>
* <font style="color:rgb(38, 38, 38);">怎么知道 CPU 执行出的 </font><font style="color:rgb(38, 38, 38);background-color:rgba(0, 0, 0, 0.06);">1 + 1 = 3</font><font style="color:rgb(38, 38, 38);"> 是代码写错了，还是电路连错了？</font>

:::

但真实情况是：

```plain
CPU 还不存在
程序已经写好了
```

程序必须运行在某个 CPU 上。

解决办法只有一个：

> 先用软件模拟一个 CPU

这种软件就叫：

> CPU 仿真器

***

:::color4
📌 一句话理解 **仿真器 = 软件实现的 CPU**

:::

### <font style="color:rgb(31, 31, 31);">4.1.2 什么是“参考模型” (Reference Model)？</font>

当你设计一个 CPU 时，会遇到一个根本问题：

> 你怎么知道自己做对了？

你必须有一个：

> 永远正确的执行标准

这个标准就叫：

> 参考模型（Reference Model）

作用是：

```plain
你的CPU运行结果
必须 == 参考模型运行结果
```

否则说明：

> CPU 有 bug

**<font style="color:rgb(31, 31, 31);">因此，参考模型就是你的“标准答案”。</font>**

<font style="color:rgb(31, 31, 31);">当你写代码实现 </font><code><font style="color:rgb(68, 71, 70);">add</font></code><font style="color:rgb(31, 31, 31);"> 指令时，你怎么知道你写的逻辑对不对？参考模型就是</font>**<font style="color:rgb(31, 31, 31);">永远不会出错</font>**<font style="color:rgb(31, 31, 31);">的模型。</font>

* **<font style="color:rgb(31, 31, 31);">规则</font>**<font style="color:rgb(31, 31, 31);">：香山运行结果 对比 参考模型运行结果。</font>
* **<font style="color:rgb(31, 31, 31);">不符</font>**<font style="color:rgb(31, 31, 31);">：只要不一样，就代表香山有 Bug。</font>

### **<font style="color:rgb(38, 38, 38);">4.1.3 为什么必须同时存在 NEMU 和 Spike？</font>**

很多新手都会问：

> 有一个仿真器不就够了吗？

不够。

因为：

一个只能执行\
两个才能验证

必须形成：

```plain
对照关系
```

执行流程如下：

```plain
程序
 ↓
同时送入两个CPU

        ┌──────────┐
        │  Spike   │ → 标准结果A
        └──────────┘

        ┌──────────┐
        │  NEMU    │ → 你的结果B
        └──────────┘

             ↓
         结果比较
```

如果：

```plain
A == B → CPU正确
A ≠ B → CPU有bug
```

这套机制叫：

> 差分测试（Differential Testing）将会在第八章详细讲解

***

### 4.1.4 NEMU 和 Spike 的本质分工

| 维度 | Spike | NEMU |
| --- | --- | --- |
| 角色 | 标准答案 | 被测试CPU |
| 能否修改 | 不建议 | 经常修改 |
| 目的 | 判断对错 | 实现功能 |
| 权威性 | 最高 | - |

:::color4
一句话总结：

Spike 负责提供判断对错的标准\
NEMU 负责尝试实现

:::

<font style="color:rgb(31, 31, 31);">无论在 Spike 还是 NEMU 中，程序在仿真器中的真实执行逻辑都一样：</font>

```plain
循环执行
{
    取指令
    解析指令
    执行指令
    更新寄存器
}
```

<font style="color:rgb(31, 31, 31);">也就是说：</font>

> <font style="color:rgb(31, 31, 31);">仿真器在软件里模拟 CPU 的指令周期</font>

***

### 4.1.5 整个系统运行全流程（全局视角）

真实完整执行链如下：

```plain
程序源码
 ↓
编译器
 ↓
可执行程序
 ↓
仿真器（Spike / NEMU）
 ↓
模拟CPU执行
 ↓
输出结果
```

等真实 CPU 完成后：

```plain
程序
 ↓
真实CPU
 ↓
结果
```

目标是保证：

```plain
真实CPU结果 == Spike结果
```

:::color4 <font style="color:rgb(38, 38, 38);">本章核心：Spike 是标准答案 CPU，NEMU 是你的实现 CPU，两者对比才能验证设计是否正确。</font>

:::

## <font style="color:rgb(31, 31, 31);">4.2 RISC-V指令集仿真器（NEMU的介绍与操作）</font>

### <font style="color:rgb(38, 38, 38);">4.2.1 NEMU 在整个系统中的位置</font>

:::info
在香山开发流程中，NEMU 扮演着“**标准答案**”的角色：

* **真实世界**：程序 → 真实 CPU（香山硬件） → 运行结果。
* **开发世界**：程序 → **NEMU (模拟 CPU)** → 运行结果。

当我们对比两者的结果时，如果不一样，那一定是“香山硬件”这个学徒做错了。

:::

<font style="color:rgb(38, 38, 38);">因为真实 CPU 还没完成，所以：</font>**<font style="color:rgb(38, 38, 38);">NEMU 临时扮演 CPU</font>**

### **<font style="color:rgb(38, 38, 38);">4.2.2 NEMU 到底模拟了什么？</font>**

:::info <font style="color:rgb(38, 38, 38);">NEMU 模拟的是 CPU 的</font>**<font style="color:rgb(38, 38, 38);">灵魂</font>**<font style="color:rgb(38, 38, 38);">——CPU指令执行过程（CPU核心行为）：</font>

* **<font style="color:rgb(38, 38, 38);">取指</font>**<font style="color:rgb(38, 38, 38);">：从内存拿指令。</font>
* **<font style="color:rgb(38, 38, 38);">译码</font>**<font style="color:rgb(38, 38, 38);">：看懂指令要干什么。</font>
* **<font style="color:rgb(38, 38, 38);">执行</font>**<font style="color:rgb(38, 38, 38);">：更新寄存器和内存。</font>

<font style="color:rgb(38, 38, 38);">它不会模拟显卡、声卡或绚丽的界面，它只专注于让程序在逻辑上跑通。</font>

:::

### **<font style="color:rgb(38, 38, 38);">4.2.3 仿真器 vs 模拟器</font>**

很多新人会混淆这两个词。

| 概念 | 含义 | 精度 |
| --- | --- | --- |
| 模拟器 | 模拟行为 | 较粗略 |
| 仿真器 | 模拟内部执行 | 更精确 |

NEMU属于：**仿真器级别**

也就是说它会：

✔ 按真实 CPU 执行逻辑一步步运行\
✔ 每条指令都真实执行

### 4.2.4 实际运行一次 NEMU 在发生什么？

当你输入运行命令时，本质发生的是：

```plain
加载程序
↓
NEMU开始模拟CPU
↓
逐条执行指令
↓
输出结果
```

真实执行逻辑其实是：

```plain
while(程序没结束){
    取指令
    译码
    执行
}
```

也就是说：

NEMU 在软件中扮演 CPU 执行循环

### 4.2.5 逐步学习NEMU

[NEMU代码导读](https://ysyx.oscc.cc/slides/2306/07.html)

我们使用NEMU模拟器作为香山的实现参考。NEMU模拟器是一个解释型的指令集模拟器。相比其他的RISC-V解释型指令集模拟器（如spike），NEMU在运行速度上有数量级的优势。

#### 4.2.5.1 NEMU的用处

##### 1. 权威的验证基准

作为官方推荐的ISA参考设计，NEMU提供了一套标准化的指令集模拟器，可作为验证其他实现的参考模型。它确保了指令集实现的正确性和一致性，为芯片设计、编译器开发等工作提供了可靠的验证依据。

##### 2. 高效的开发体验

* **简洁易用**：NEMU设计理念简单，降低了学习和使用成本，开发者能够快速上手并进行验证工作。
* **性能优异**：经过优化后，NEMU的性能与QEMU相近，能够在保证准确性的同时，提供高效的模拟运行体验。

##### 3. 强大的辅助功能

NEMU提供了一系列API，能够辅助香山等芯片进行微架构的比较和验证。这些API简化了验证流程，提高了验证效率，帮助开发者更快地发现和解决问题。

##### 4. 广泛的适用性

NEMU不仅适用于指令集验证，还可以用于教学、研究等多个领域。它为开发者提供了一个灵活、可扩展的平台，能够满足不同场景下的需求。

#### 4.2.5.2 工具链

##### Makefile部分

* `nemu/Makefile`
  * `SRCS`: 和YEMU差不多，是需要编译的源文件
  * `CFLAGS`: 刚才看到的编译选项
  * `include $(NEMU_HOME)/scripts/native.mk`: 包含其他文件
* `nemu/scripts/native.mk`
  * 一些用于运行和清除编译结果的伪目标
* `nemu/scripts/build.mk`
  * 编译规则
  * 包含源文件与头文件的依赖关系（由gcc的`-MMD`选项生成，并通过`fixdep`工具处理）

##### 调试和配置工具

GDB（GNU Debugger）是GNU项目下的开源调试器，主要用于调试C/C++程序。

kconfig/menuconfig修改编译和环境配置

* **Kconfig**：一种配置语言，用于定义配置选项（通常出现在名为`Kconfig`的文件中）。每个配置选项可以指定类型（布尔型、整型、字符串型等）、依赖关系、默认值等。
* **menuconfig**：一个基于文本界面的配置工具，它读取Kconfig文件生成菜单，用户可以通过菜单选择配置选项，并生成配置文件（通常为`.config`）。

NEMU的编译配置可以通过在NEMU路径下，运行`menuconfig`命令配置编译选项，比如是否使用`debug`模式。

### 4.2.6  NEMU编译与配置

#### 4.2.6.1 编译概述

NEMU是difftest机制中用于参考的golden模型。

#### 4.2.6.2 编译配置

主要使用config配置：

| 配置名称 | 用途说明 | 主要特点 | 典型使用场景 |
| --- | --- | --- | --- |
| **riscv64-xs\_defconfig** | 基础香山配置 | 最基本的香山处理器仿真配置 | 日常开发、功能验证 |
| **riscv64-xs-ref\_defconfig** | 香山参考配置 | 性能基准配置，调试功能较少 | 性能回归测试、对比分析 |
| **riscv64-xs-diff-spike\_defconfig** | 差分测试配置 | 专为与Spike模拟器对比设计 | 指令级正确性验证、模拟器验证 |
| **riscv64-gem5-ref\_defconfig** | Gem5参考配置 | 与Gem5系统模拟器兼容 | 系统级功能/性能对比、外设验证 |
| **riscv64-xs-dual-ref\_defconfig** | 双核香山参考配置 | 支持多核处理器仿真 | 多核程序开发、多核性能评估 |
| **riscv64-xs-dual-ref-debug\_defconfig** | 双核香山调试配置 | 增强调试功能的多核配置 | 多核问题定位、并发调试 |

#### 4.2.6.3 使用命令编译NEMU

在使用NEMU模拟器运行workload时，我们需要将模拟器的**虚拟外设与香山的外设地址空间对齐**。进入`/xs-env/NEMU`目录，运行以下命令：

```bash
cd $NEMU_HOME
make clean
#这里要运行不同的配置来编译NEMU，不同的功能用处的NEMU有不同的配置
make riscv64-xs_defconfig
//将NEMU编译成裸机，从而可以运行之前步骤的Coremark
make -j

//与香山核协同仿真的NEMU模拟器配置与独立运行时略有不同。我们使用以下的命令编译仿真中使用的NEMU：
make clean-softfloat / make clean-all
make riscv64-xs-ref_defconfig
//如果不想要make clean-softfloat  或make clean-all，并且有需求编译-so
//可以先编译make riscv64-xs-ref_defconfig
```

这里的`riscv64-xs-ref_defconfig` 是在`configs` 下预先写好的配置文件。其中：

* `riscv64-xs-ref_defconfig`是为difftest生成动态链接库的配置。
* `riscv64-xs_defconfig`是与香山外设对齐，可直接执行的NEMU模拟器。

#### 4.2.6.4 问题解决

##### 编译错误处理

使用`riscv64-xs-ref_defconfig` 的配置，编译的时候会报错：

```shell
+ ccache g++ /nfs/home/yourhome/xs-env/NEMU/build/riscv64-nemu-interpreter-so
/usr/bin/ld: resource/softfloat/build/softfloat.a(s_mulAddF64.o): warning: relocation against `softfloat_roundingMode' in read-only section `.text'
/usr/bin/ld: resource/softfloat/build/softfloat.a(f16_roundToInt.o): relocation R_X86_64_PC32 against symbol `softfloat_exceptionFlags' can not be used when making a shared object; recompile with -fPIC
/usr/bin/ld: final link failed: bad value
collect2: error: ld returned 1 exit status
make: *** [/nfs/home/yourhome/xs-env/NEMU/scripts/build.mk:81: /nfs/home/yourhome/xs-env/NEMU/build/riscv64-nemu-interpreter-so] Error 1
```

##### **解决办法**：

在ready-to-run文件夹下有编译好的nemu动态链接库，可以直接用，后面在使用的时候加入其路径

```shell
yourhome@open01:~/xs-env/NEMU$ cd ready-to-run
yourhome@open01:~/xs-env/NEMU/ready-to-run$ ls
auto_bump.sh             copy_and_run.bin          microbench.bin                          
riscv64-nemu-interpreter-so
bump_all_from_docker.sh  coremark-2-iteration.bin  README.md                               
riscv64-nutshell-spike-so
bump-nemu.sh             Dockerfile                riscv64-nemu-interpreter-debug-so       
riscv64-spike-so
bump-spike-nutshell.sh   flash_recursion_test.bin  riscv64-nemu-interpreter-dual-debug-so
bump-spike.sh            linux.bin                 riscv64-nemu-interpreter-dual-so
```

##### 使用NEMU作为香山的测试程序

利用前面生成好的香山仿真程序，NEMU动态链接库与workload，可以**默认在差分测试框架打开的情况下让香山核运行指定的应用程序**，进入`/xs-env/XiangShan`目录运行指令`./build/emu -i MY_WORKLOAD.bin`，其中将`MY_WORKLOAD.bin`替换为想要运行镜像的路径，比如前面生成的coremark，即可让香山仿真运行指定的程序了。例如：

`./build/emu -i $NOOP_HOME/ready-to-run/linux.bin`

### 4.2.7 NEMU执行与调试

#### 4.2.7.1 加载二进制文件

运行`./build/riscv64-nemu-interpreter -b MY_WORKLOAD.bin`，其中将`MY_WORKLOAD.bin`替换为想要运行镜像的路径，例如上一节中生成的coremark，即可让NEMU模拟器运行指定的程序了。例如：

`./build/riscv64-nemu-interpreter -b $NOOP_HOME/ready-to-run/linux.bin`

查看运行结果：

```bash
yourhome@open01:~/xs-env/NEMU$ ./build/riscv64-nemu-interpreter -b /nfs/home/yourhome/xs-env/nexus-am/apps/coremark/build/coremark-riscv64-xs.bin
[src/isa/riscv64/init.c:234,init_isa] NEMU will start from pc 0x80000000
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'uartlite' at [0x00000000000003f8, 0x0000000000000404]
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'screen' at [0x0000000000000100, 0x0000000000000107]
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'keyboard' at [0x0000000000000060, 0x0000000000000063]
[src/device/sdcard.c:137,init_sdcard] Can not find sdcard image: 
[src/monitor/image_loader.c:204,load_img] Loading image (checkpoint/bare metal app/bbl) from cmdline: /nfs/home/yourhome/xs-env/nexus-am/apps/coremark/build/coremark-riscv64-xs.bin

[src/monitor/image_loader.c:260,load_img] Read 16544 bytes from file /nfs/home/yourhome/xs-env/nexus-am/apps/coremark/build/coremark-riscv64-xs.bin to 0x0x772df9ff4000
[src/monitor/monitor.c:63,welcome] Debug: OFF
[src/monitor/monitor.c:68,welcome] Build time: 16:35:39, Jan 15 2026
Welcome to riscv64-NEMU!
For help, type "help"
Running CoreMark for 10 iterations
2K performance run parameters for coremark.
CoreMark Size    : 666
Total time (ms)  : 24439
Iterations       : 10
Compiler version : GCC15.0.0 20241107 (experimental)
seedcrc          : 0xe9f5
[0]crclist       : 0xe714
[0]crcmatrix     : 0x1fd7
[0]crcstate      : 0x8e3a
[0]crcfinal      : 0xfcaf
Finished in 24439 ms.
==================================================
CoreMark Iterations/Sec 409.18
[/nfs/home/yourhome/xs-env/NEMU/src/isa/riscv64/include/../instr/special.h:38,execute] nemu_trap case 0
[src/cpu/cpu-exec.c:938,cpu_exec] nemu: HIT GOOD TRAP at pc = 0x0000000080001c30
[src/cpu/cpu-exec.c:944,cpu_exec] trap code:0
[src/cpu/cpu-exec.c:155,monitor_statistic] host time spent = 30,130 us
[src/cpu/cpu-exec.c:157,monitor_statistic] total guest instructions = 3,156,150
[src/cpu/cpu-exec.c:158,monitor_statistic] vst count = 0, vst unit count = 0, vst unit optimized count = 0
[src/cpu/cpu-exec.c:161,monitor_statistic] simulation frequency = 104,751,078 instr/s
[src/utils/state.c:30,is_exit_status_bad] NEMU exit with good state: 2, halt ret: 0
```

这里的终端输出，显示了在\[src/monitor/image\_loader.c:204,load\_img] 这个位置加载了二进制文件。

使用`--help`命令查看参数，这里使用-b是为了：run with batch mode，也就是批量执行，如果想要单步执行就去掉这个参数。\
单步执行的输出：

```bash
yourhome@open01:~/xs-env/NEMU$ ./build/riscv64-nemu-interpreter  /nfs/home/yourhome/xs-env/nexus-am/apps/coremark/build/coremark-riscv64-xs.bin
[src/isa/riscv64/init.c:234,init_isa] NEMU will start from pc 0x80000000
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'uartlite' at [0x00000000000003f8, 0x0000000000000404]
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'screen' at [0x0000000000000100, 0x0000000000000107]
[src/device/io/port-io.c:35,add_pio_map_with_diff] Add port-io map 'keyboard' at [0x0000000000000060, 0x0000000000000063]
[src/device/sdcard.c:137,init_sdcard] Can not find sdcard image: 
[src/monitor/image_loader.c:204,load_img] Loading image (checkpoint/bare metal app/bbl) from cmdline: /nfs/home/yourhome/xs-env/nexus-am/apps/coremark/build/coremark-riscv64-xs.bin

[src/monitor/image_loader.c:260,load_img] Read 16544 bytes from file /nfs/home/yourhome/xs-env/nexus-am/apps/coremark/build/coremark-riscv64-xs.bin to 0x0x7773f84f7000
[src/monitor/monitor.c:63,welcome] Debug: OFF
[src/monitor/monitor.c:68,welcome] Build time: 09:19:15, Jan 22 2026
Welcome to riscv64-NEMU!
For help, type "help"
(nemu) si 
[src/cpu/cpu-exec.c:155,monitor_statistic] host time spent = 470 us
[src/cpu/cpu-exec.c:157,monitor_statistic] total guest instructions = 75
[src/cpu/cpu-exec.c:158,monitor_statistic] vst count = 0, vst unit count = 0, vst unit optimized count = 0
[src/cpu/cpu-exec.c:161,monitor_statistic] simulation frequency = 159,574 instr/s
(nemu) help
help - Display information about all supported commands
c - Continue the execution of the program
si - step
info - info r - print register values; info w - show watch point state
x - Examine memory
p - Evaluate the value of expression
w - Set watchpoint
d - Delete watchpoint
detach - detach diff test
attach - attach diff test
save - save snapshot
load - load snapshot
q - Exit NEMU
(nemu) 
```

#### 4.2.7.2 NEMU能加载哪种格式的二进制文件？

##### 文件格式区别

| 特性 | **BIN文件** | **ELF文件** |
| --- | --- | --- |
| **完整名称** | 二进制文件 | 可执行和链接格式 |
| **文件结构** | 纯二进制数据，无结构头 | 结构化格式，包含多个节（Sections） |
| **地址信息** | 无地址信息，需要外部指定加载地址 | 包含加载地址、入口点等元数据 |
| **调试信息** | 不包含调试信息 | 可以包含符号表、调试信息（DWARF） |
| **重定位信息** | 无 | 包含重定位信息 |
| **节（Section）** | 没有明确的节划分 | 有.text、.data、.bss等明确划分 |
| **文件大小** | 通常较小（只包含代码和数据） | 通常较大（包含额外信息） |
| **可读性** | 无法直接阅读 | 可用readelf、objdump等工具分析 |
| **典型用途** | 固件、引导程序、嵌入式系统 | 操作系统可执行文件、共享库 |
| **跨平台性** | 依赖特定硬件架构 | 定义了平台无关的结构, 但仍有大部分架构/平台相关的部分 |
| **加载复杂度** | 简单，直接复制到内存 | 复杂，需要解析和重定位 |

参考github的readme对nemu的介绍：

**What is NOT supported 不支持哪些功能**

* Cannot directly run an ELF 无法直接运行ELF文件
  * GEM5's System call emulation is not supported.（[What is system call emulation](https://stackoverflow.com/questions/48986597/when-to-use-full-system-fs-vs-syscall-emulation-se-with-userland-programs-in-gem)？）
  * QEMU's User space emulation is not supported.（[What is user space emulation](https://www.qemu.org/docs/master/user/main.html)？）

nemu无法直接执行elf格式的二进制文件。

#### 4.2.7.3 NEMU是如何依次执行命令的？

nemu的命令：

NEMU Checkpoint部分相关参数介绍，具体请RTFSC：

1. `-b`：以`batch`模式运行（省略的话，会在启动NEMU后暂停等待输入命令）
2. `-D`：生成Checkpoint的工作根目录，会自动创建指定目录，可以任取，例如`-D simpoint_checkpoint`
3. `-C`：描述任务的名字（上节三步流程的Profiling和Cluster等），可以任取，例如`-C profiling`
4. `-w`：workload的名字，可以任取，例如`-w bbl`
5. `--simpoint-profile`：进行SimPoint Profiling，用于Profiling环节
6. `--cpt-interval`：用于Profiling环节：采样的区间大小，以指令数为单位，用于Checkpoint环节：设置Checkpoint的区间，需和profiling过程中的`--cpt-interval`参数保持一致。
7. `-S`：指定Cluster环节的结果，用于Checkpointing环节
8. `--checkpoint-format`：支持选择`gz`或者`zstd`两种格式生成checkpoint，如果不指定该参数，默认使用`gz`格式。
9. `-r`或`--cpt-restorer`：指定GCPT恢复程序的二进制文件`gcpt.bin`路径。指定路径后，恢复程序将被加载至`0x80000000`或FLASH的起始地址，自起始地址开始的1M空间用于存放恢复程序及Checkpoint环节保存的体系结构状态，而该参数会覆盖这段空间中的恢复程序部分，并且无论用户指定的Workload或FLASH镜像是否预留有这部分空间。

使用单步执行查看调试的打印和执行记录（加上`-b`指令就是批量运行，没有额外的设置就是单步执行）。\
示例输出

```bash
(nemu) si
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x0000000080000000:   93 00 00 00     p_li_0     ra
(M)0x0000000080000000:   93 00 00 00     p_li_0     ra
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x0000000080000004:   13 01 00 00     p_li_0     sp
(M)0x0000000080000004:   13 01 00 00     p_li_0     sp
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x0000000080000008:   93 01 00 00     p_li_0     gp
(M)0x0000000080000008:   93 01 00 00     p_li_0     gp
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x000000008000000c:   13 02 00 00     p_li_0     tp
(M)0x000000008000000c:   13 02 00 00     p_li_0     tp
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x0000000080000010:   93 02 00 00     p_li_0     t0
(M)0x0000000080000010:   93 02 00 00     p_li_0     t0
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x0000000080000014:   13 03 00 00     p_li_0     t1
(M)0x0000000080000014:   13 03 00 00     p_li_0     t1
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x0000000080000018:   93 03 00 00     p_li_0     t2
(M)0x0000000080000018:   93 03 00 00     p_li_0     t2
[src/cpu/cpu-exec.c:740,fetch_decode] (M) 0x000000008000001c:   13 04 00 00     p_li_0     s0
```

#### 4.2.7.4 如何生成适配Gem5/RTL的配置文件

方法1（推荐）：

```makefile
git clone https://github.com/OpenXiangShan/NEMU.git
cd NEMU
export NEMU_HOME=`pwd`
make riscv64-gem5-ref_defconfig # 配置NEMU作为reference model模式
make -j 10
# 设置GEM5需要的环境变量
export GCB_REF_SO=`realpath build/riscv64-nemu-interpreter-so`
```

方法2：使用`menuconfig`工具修改配置之后重新编译。

#### 4.2.7.5 DEBUG调试过程

使用`menuconfig`命令，配置需要查看的对象，之后选中进行`debug`。

![figure-002-debug-menuconfig-inspect-nemu](./img/chapter-4-instruction-emulator-cpu-simulation-and-reference-models-nemu-spike/figure-002-debug-menuconfig-inspect-nemu.png)

图1：NEMU menuconfig调试配置界面

**图表解读：**

这张图展示了NEMU的menuconfig调试配置界面，这是一个基于文本的交互式配置工具：

1. **配置菜单**
   * 分层显示所有可配置选项
   * 使用方向键导航，空格键选择/取消选择
   * 支持搜索功能快速定位配置项
2. **配置项说明**
   * 显示当前选中配置项的详细说明
   * 包括配置项的作用、依赖关系、默认值等
   * 帮助用户理解每个配置项的含义
3. **配置类型**
   * `[ ]`：布尔选项（开启/关闭）
   * `( )`：单选选项（多选一）
   * `{ }`：依赖选项（依赖其他选项）
   * `< >`：数值或字符串选项
4. **常用配置区域**
   * **Debug选项**：启用调试功能，如指令跟踪、内存访问跟踪等
   * **性能选项**：优化仿真性能的配置
   * **功能选项**：启用/禁用特定功能模块

**使用建议：** 初学者可以先使用默认配置，随着对NEMU理解的深入，再根据需要调整特定配置。

## 4.3 Spike模拟器

Spike 是一个：官方参考级 RISC-V CPU 模型

它的特点：

| 特点 | 含义 |
| --- | --- |
| 极度精确 | 严格按规范执行 |
| 非常稳定 | 很少出错 |
| 实现简单 | 不模拟复杂硬件细节 |

:::color4
一句话理解：Spike 是“标准答案版 CPU”

:::

***

### 4.3.1 Spike概述

[Spike模拟器](https://risc-v.ibugone.com/toolchain/spike/)

<https://zhuanlan.zhihu.com/p/641312376>

Spike是一个开源的RISC-V指令集架构（ISA）模拟器，主要用于模拟RISC-V CPU的运行和行为。

### 4.3.2 Spike的功能与用途

**RISC-V模拟**：Spike能够模拟一个或多个RISC-V处理器的功能模型，支持多种RISC-V ISA扩展，包括RV32I、RV64I等基础指令集。

**系统仿真**：Spike可以与其他工具（如pk、fesrv）配合使用，完成系统的仿真，支持裸机程序、实时操作系统（RTOS）等的开发。

**性能分析**：Spike还可以进行性能仿真，帮助开发者分析和优化程序的性能。

**调试支持**：Spike支持与GDB等调试工具的集成，方便开发者调试RISC-V程序。

### 4.3.3 Spike的工作机制

**通信机制**：Spike通过tohost和fromhost寄存器实现目标系统（仿真中的RISC-V处理器）与宿主机之间的数据交换，支持输入/输出处理、系统调用和异常处理。

**程序执行**：Spike的程序启动逻辑包括参数解析、内存和处理器初始化、程序加载等，仿真器通过主循环执行指令。

### 4.3.4 如何使用Spike

**安装Spike**：用户可以通过克隆Spike的GitHub仓库并按照说明进行编译和安装，安装完成后即可使用spike命令运行RISC-V程序。

**运行程序**：例如，用户可以直接运行一个RISC-V的裸机程序，如hello.elf，来测试Spike的功能。

## 4.4 总结与建议

### 4.4.1 仿真器类型概览

通过本章的学习，你应该已经了解了香山处理器开发中常用的两种仿真器：

1. **NEMU（指令集仿真器）**
   * 用途：作为参考模型，验证指令集正确性
   * 特点：速度快、精度高、支持调试
   * 适用场景：日常开发、功能验证、性能测试
2. **Spike（快速仿真器）**
   * 用途：快速功能验证和性能评估
   * 特点：速度极快、功能相对简单
   * 适用场景：快速迭代、早期验证

### 4.4.2 仿真器在香山开发中的作用

1. **验证作用**：确保香山处理器的指令集实现正确
2. **调试作用**：提供详细的执行跟踪和调试信息
3. **性能作用**：作为性能对比的基准参考
4. **开发作用**：加速软件开发，无需等待硬件

### 4.4.3 给新手的建议

1. **从NEMU开始**：NEMU是香山开发中最常用的仿真器，先掌握它
2. **理解配置**：不同的配置适用于不同的场景，理解其区别
3. **善用调试**：仿真器提供了强大的调试功能，学会使用它们
4. **对比学习**：通过对比不同仿真器，加深对仿真技术的理解
5. **实践为主**：多动手操作，从简单的Hello Xiangshan开始

### 4.4.4 下一步行动

1. 如果你成功编译和运行了NEMU，可以尝试使用不同的配置
2. 如果想深入学习，可以研究NEMU的源代码实现
3. 如果对性能感兴趣，可以对比NEMU和Spike的性能差异
4. 考虑参与香山开源社区，学习更多仿真器的使用技巧

仿真器是处理器开发中不可或缺的工具，它让开发者能够在软件中"预演"硬件的运行，大大提高了开发效率和成功率。掌握仿真器的使用将为你的处理器设计之路打下坚实的基础。

祝你在处理器仿真的道路上越走越远！

# 学习路径规划 改为绘图

### 4.6.1 第一阶段：仿真器基础概念（1周）

**目标：** 理解仿真器的基本概念和作用\
**任务：**

1. 阅读本章，理解NEMU和Spike的基本概念
2. 了解指令集仿真器与周期级仿真器的区别
3. 掌握仿真器在处理器开发中的作用

### 4.6.2 第二阶段：NEMU基础使用（2周）

**目标：** 掌握NEMU的基本编译和运行\
**任务：**

1. 按照1.3.3节的步骤编译NEMU
2. 尝试使用不同的配置编译NEMU
3. 运行简单的测试程序（如Hello World）

### 4.6.3 第三阶段：NEMU高级功能（3周）

**目标：** 掌握NEMU的高级功能和调试技巧\
**任务：**

1. 学习使用menuconfig配置NEMU
2. 掌握NEMU的调试命令和技巧
3. 理解NEMU在difftest中的作用

### 4.6.4 第四阶段：多仿真器对比（2周）

**目标：** 了解不同仿真器的特点和适用场景\
**任务：**

1. 学习Spike模拟器的基本使用
2. 对比NEMU和Spike的特点和性能
3. 理解不同仿真器在香山开发中的角色


> 更新: 2026-04-24 01:47:37  
