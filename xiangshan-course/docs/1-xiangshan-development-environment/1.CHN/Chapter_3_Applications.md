# 第三章：应用程序

# 3 应用程序

:::info

### 🎯 本章目标

完成本章后，你应该能够：

* **理解逻辑**：说出为什么香山不能直接跑普通的 .exe 或 .elf。
* **掌握工具**：理解 AM (Abstract Machine) 层的意义，并熟练使用 ARCH 参数。
* **进阶认知**：理解性能评估（CoreMark/SPEC）及加速仿真（SimPoint）的本质。

:::

![1770715590089-0d4a497b-381c-47c2-aa33-15d939bf1ee2.png](img/chapter-3-applications/figure-001-applications-overview.webp)

本章讲解横向绿色板块

### **1️⃣**\*\* 从问题出发：程序为什么不能直接在CPU运行？ \*\*

你在 Windows 上双击一个 `.exe` 能运行，是因为有 Windows 操作系统帮你管理内存、显示器和文件。\
**但香山现在只是一个“毛坯房”：**

1. 它没有操作系统（Linux/Windows）。
2. 它没有显示器驱动。
3. 它甚至不知道内存从哪个地址开始。

**结论**：\
要让程序在这种“裸机” CPU 上跑，需要一个\*\*“转化”\*\*。这个转化就是由 \*\*AM (Abstract Machine)\*\*做到的。

## 3.1 nexus-am框架

### **2️⃣**\*\* AM 到底是什么？ \*\*

**工程定义**：\
AM 是程序与硬件之间的**最小运行接口层**。

**结构图**：

> **应用程序 (App)** *（比如 Hello XiangShan）*\
>      ↓ 调用\
> **AM (Abstract Machine)** *（翻译官：负责把“打印”翻译成“写寄存器”）*\
>      ↓ 执行\
> **CPU (香山/NEMU)** *（搬砖工：只负责执行最简单的加减跳转）*

:::danger
**新手必须理解的事实**：\
CPU 不会直接运行你的 C 代码，它只运行经过 AM 包装、符合特定硬件环境的二进制指令流。

:::

### 3.1.1 nexus-am概述

源代码：[GitHub - OpenXiangShan/nexus-am](https://github.com/OpenXiangShan/nexus-am)

#### 核心目的：

* 在没有操作系统的情况下**敏捷地**生成工作负载
* 为**裸机**（如香山）提供运行时框架

**优点：**

* 轻量且易用
* 实现了基本的**系统调用接口和异常处理**
* 支持多种ISA和配置

![1770712564169-fa5f9058-3cf5-482e-99b7-8c896b076803.png](img/chapter-3-applications/figure-002-isa-nexus-am-framework.png)

图1：nexus-am框架结构

**图1解读：**

这张图展示了nexus-am（抽象机器）框架的整体结构，它是一个为裸机环境设计的轻量级运行时框架：

1. **应用程序层**（顶部）
   * 用户编写的各种应用程序
   * 包括Hello XiangShan、CoreMark等测试程序
   * 使用标准C/C++语言编写
2. **抽象机器接口**（中部）
   * 提供统一的系统调用接口
   * 包括文件操作、内存管理、线程调度等
   * 屏蔽底层硬件差异
3. **平台适配层**（底部）
   * 针对不同硬件平台的实现
   * 包括香山（xs）、NEMU、QEMU等
   * 提供硬件特定的驱动和初始化

#### **关键特性：**

nexus-am让开发者可以在没有操作系统的情况下，像在有操作系统的环境中一样编写和运行程序。

### 3.1.2 使用命令

#### 3️⃣ 程序是如何被编译出来的？

理解编译流水线，是你排查“文件找不着”问题的关键：

**流程图**：\
`C 源代码` → `Makefile (烹饪食谱)` → `选择 ARCH (选择餐具)` → `RISC-V GCC (厨师)` → `RISC-V 二进制镜像 (.bin)`

```plain
C程序 （源代码）
 ↓
Makefile（烹饪食谱）
 ↓
ARCH选择 （ 选择餐具）
 ↓
RISC-V GCC（厨师）
 ↓
RISC-V程序 即RISC-V 二进制镜像 (.bin)
```

:::danger
**核心变量**：

* `$AM_HOME`：告诉系统 AM 的源代码在哪里，这是编译的基石。
* ARCH 决定目标平台

:::

[使用 AM 生成自定义 workload - 香山 XiangShan](https://xiangshan-doc-test.readthedocs.io/next/tools/gen-workload-with-am/)

#### 3.1.2.1 程序编译框架

##### 3.1.2.1.1 Makefile构建系统

使用makefile命令文件对整个框架进行编译。

[makefile详细说明](https://bosc.yuque.com/staff-xmw8rg/fb7qy3/aa0v630i80i1y4g1)

:::danger
**新手提示：** Makefile就像一本烹饪食谱📖，告诉系统如何一步步"烹饪"出可执行程序。

:::

#### 3.1.2.2如何使用框架？

1. 在保存应用的 `apps` 路径下面使用 `mkdir`创建自己的示例
2. 创建和按照相应的格式修改 `makefile`
3. 创建源文件 `xx.c` (包含相应的头文件)
4. 使用编译命令： `make ARCH = riscv64-xs`（这里的参数选择在下面解析 `makefile` 对参数传递过程可以看到所在的文件路径）
5. 最终得到应用程序会被生成在 `build` 文件夹里面
6. 后面就可以使用这里的镜像运行在处理器上面了

### 3.1.3 实例：使用 AM 生成 workload

[AM](https://github.com/OpenXiangShan/nexus-am) 是一个裸机运行时环境，用户可以使用 AM 来编译在香山裸机上运行的程序。使用 AM 编译程序的示例如下：

进入实例的文件夹：运行命令 `make ARCH=riscv64-xs -j8`

运行时**常见报错**：

```shell
yourhome@open01:~/xs-env/nexus-am/apps/coremark$ make ARCH=riscv64-xs -j8
Makefile:8: /Makefile.app: No such file or directory
make: *** No rule to make target '/Makefile.app'.  Stop.
yourhome@open01:~/xs-env$ source env.sh
SET XS_PROJECT_ROOT: /nfs/home/yourhome/xs-env
SET NOOP_HOME (XiangShan RTL Home): /nfs/home/yourhome/xs-env/XiangShan
SET NEMU_HOME: /nfs/home/yourhome/xs-env/NEMU
SET AM_HOME: /nfs/home/yourhome/xs-env/nexus-am
SET DRAMSIM3_HOME: /nfs/home/yourhome/xs-env/DRAMsim3
yourhome@open01:~/xs-env$ cd ..
yourhome@open01:~$ cd xs-env/nexus-am/apps/coremark
yourhome@open01:~/xs-env/nexus-am/apps/coremark$ make ARCH=riscv64-xs -j8
# Building coremark [riscv64-xs] with AM_HOME {/nfs/home/yourhome/xs-env/nexus-am}
+ CC src/core_portme.c
make: riscv64-unknown-linux-gnu-gcc: No such file or directory
make: *** [/nfs/home/yourhome/xs-env/nexus-am/Makefile.compile:29: /nfs/home/yourhome/xs-env/nexus-am/apps/coremark/build/riscv64-xs//./src/core_portme.o] Error 127
```

**解决方法：** 缺少 `$AM_HOME`环境变量和 gcc 编译器的路径变量

1. 前者要运行 `env.sh`:实现环境变量的设置
2. 后者是加入环境变量：`export PATH=$PATH:即为安装 gcc 的位置`

生成的`coremark-riscv64-xs.bin`可以作为仿真中的程序输入。要使用 AM 生成自定义的 workload。

正确编译完成后可以在对应的文件夹里面找到 build 文件，里面有接下来要使用的 bin 文件

```bash
.
├── build
│   ├── hello-riscv64-xs.bin #纯粹的二进制机器代码，没有格式和地址标记，通常是计算机的指令合集
│   ├── hello-riscv64-xs.elf #Executable and Linkable Format）是一种标准化的多功能文件格式，包含机器码、数据以及符号表、调试信息、段描述等丰富的辅助信息。ELF文件支持从编译、链接到调试的完整开发流程，适用于有操作系统的复杂系统。
│   ├── hello-riscv64-xs.txt #反汇编的代码
│   └── riscv64-xs
│       ├── hello.d
│       └── hello.o
├── hello.c
└── Makefile
```

### 3.1.4 主要参数配置（makefile 内部参数的传递）

#### make ARCH=riscv64-xs中ARCH包含的命令和香山的常见使用命令

查看 `coremark` 实例的 `makefile`文件

```makefile
NAME = coremark#名称
SRCS = $(shell find -L ./src/ -name "*.c")#源码
ifdef ITERATIONS #是否进行迭代
CFLAGS += -DITERATIONS=$(ITERATIONS)
NAME = coremark-$(ITERATIONS)-iteration
endif

include $(AM_HOME)/Makefile.app
```

这里主要包含了 home 目录下的 makefile.app 文件（后缀是为了区分功能，根本上还是 makefile 文件）

```makefile
APP_DIR ?= $(shell pwd)
INC_DIR += $(APP_DIR)/include/
DST_DIR ?= $(APP_DIR)/build/$(ARCH)/    	#目标目录，根据不同的架构区分
BINARY  ?= $(APP_DIR)/build/$(NAME)-$(ARCH)	#将可执行文件路径设置为 $(APP_DIR)/build/应用名-架构
BINARY_REL = $(shell realpath $(BINARY) --relative-to .)

## Paste in "Makefile.check" here
#$(info ...)是Makefile的内置函数，用于输出信息。
#它会在Makefile解析阶段打印出给定的字符串，而不会像@echo那样在规则执行阶段才打印。
include $(AM_HOME)/Makefile.check #调用了makefile.check文件
$(info # Building $(NAME) [$(ARCH)] with AM_HOME {$(AM_HOME)})

## Default: Build a runnable image
default: image

LIBS    += klib
INC_DIR += $(addsuffix /include/, $(addprefix $(AM_HOME)/libs/, $(LIBS)))

## Paste in "Makefile.compile" here
include $(AM_HOME)/Makefile.compile #这里包含了主要使用的编译文件

## Produce a list of files to be linked: app objects, AM, and libraries
LINK_LIBS  = $(sort $(LIBS))
LINK_FILES = \
  $(OBJS) \
  $(AM_HOME)/am/build/am-$(ARCH).a \
  $(addsuffix -$(ARCH).a, $(join \
    $(addsuffix /build/, $(addprefix $(AM_HOME)/libs/, $(LINK_LIBS))), \
    $(LINK_LIBS) \
))

$(OBJS): $(PREBUILD)
image:   $(OBJS) am $(LIBS) prompt
prompt:  $(OBJS) am $(LIBS)
run:     default

prompt:
    @echo \# Creating binary image [$(ARCH)]

clean-am:
    @$(MAKE) -s -C $(AM_HOME)/am clean

clean: 
    rm -rf $(APP_DIR)/build/

.PHONY: default run image prompt clean
```

查看调用的makefile.check文件。这里包含了`ARCH`这个变量可以选择的参数范围

```makefile
## Always build "default"
.DEFAULT_GOAL = default

## Ignore checks for make clean
ifneq ($(MAKECMDGOALS),clean)

## Check: Environment variable $AM_HOME must exist
ifeq ($(AM_HOME),) 
$(error Environment variable AM_HOME must be defined.)
endif

## Check: Environment variable $ARCH must be in the supported list
ARCH  ?= native
ARCHS := $(basename $(notdir $(shell ls $(AM_HOME)/am/arch/*.mk)))
ifeq ($(filter $(ARCHS), $(ARCH)), )
$(error Invalid ARCH. Supported: $(ARCHS))
endif

## ARCH=x86-qemu -> ISA=x86; PLATFORM=qemu
ARCH_SPLIT  = $(subst -, ,$(ARCH))
ISA        ?= $(word 1,$(ARCH_SPLIT))
PLATFORM   ?= $(word 2,$(ARCH_SPLIT))

include $(AM_HOME)/am/arch/$(ARCH).mk

endif

```

能选的架构在对应的路径里面\
`ARCHS := $(basename $(notdir $(shell ls $(AM_HOME)/am/arch/*.mk)))`\
在这个路径下面可以看到能选择的 `ARCH`

```plain
~/openxiangshan/xs-env/nexus-am/am/arch<master>$ tree
.
├── am_native-navy.mk
├── isa
│   ├── mips32.mk
│   ├── riscv32.mk
│   ├── riscv64.mk
│   ├── x86_64.mk
│   └── x86.mk
├── mips32-navy.mk
├── mips32-nemu.mk
├── mips32-sdi.mk
├── native.mk
├── platform
│   ├── navy.mk
│   ├── nemu.mk
│   └── sdi.mk
├── riscv32-navy.mk
├── riscv32-nemu.mk
├── riscv32-noop.mk
├── riscv32-sdi.mk
├── riscv64-navy.mk
├── riscv64-nemu.mk
├── riscv64-noop.mk
├── riscv64-nutshell.mk
├── riscv64-sdi.mk
├── riscv64-xs-dual.mk
├── riscv64-xs-flash.mk
├── riscv64-xs.mk
├── riscv64-xs-southlake-flash.mk
├── riscv64-xs-southlake.mk
├── x86_64-qemu.mk
├── x86-navy.mk
├── x86-nemu.mk
├── x86-qemu.mk
└── x86-sdi.mk

```

这些不同的 `.mk`文件里面定义了使用的变量的具体值 (以 `riscv64.mk` 为例)

```plain
MARCH ?= rv64gc

ifeq ($(LINUX_GNU_TOOLCHAIN),1)
CROSS_COMPILE := riscv64-linux-gnu-
else
CROSS_COMPILE := riscv64-unknown-linux-gnu-
endif

COMMON_FLAGS  := -fno-pic -march=$(MARCH) -mcmodel=medany
CFLAGS        += $(COMMON_FLAGS) -static
ASFLAGS       += $(COMMON_FLAGS) -O0
LDFLAGS       += -melf64lriscv

```

## 3.2 arch-fuzz框架

:::info
这一章可以让你意识到一个非常关键的认知升级：

**CPU验证不是运行软件，而是验证架构正确性**

:::

因此出现：

```plain
arch-fuzz
```

它的定位是：自动生成极端指令测试 CPU 边界行为

这是从：**软件测试思维 → 芯片验证思维** 的认知跨越。

### **<font style="color:rgb(38, 38, 38);">4️⃣</font>\*\*\*\*<font style="color:rgb(38, 38, 38);"> ARCH 参数是决策指南 </font>**

<font style="color:rgb(38, 38, 38);">在编译时，你会遇到 </font><font style="color:rgb(38, 38, 38);background-color:rgba(0, 0, 0, 0.06);">make ARCH=???</font><font style="color:rgb(38, 38, 38);">。新手最困惑的就是该填什么。请参考下表：</font>

| <font style="color:rgb(38, 38, 38);">ARCH 参数</font> | <font style="color:rgb(38, 38, 38);">适用场景</font> | <font style="color:rgb(38, 38, 38);">为什么选它？</font> |
| --- | --- | --- |
| <font style="color:rgb(38, 38, 38);background-color:rgba(0, 0, 0, 0.06);">native</font> | <font style="color:rgb(38, 38, 38);">本机测试</font> | <font style="color:rgb(38, 38, 38);">快速检查 C 语言逻辑是否有 Bug，不涉及 RISC-V。</font> |
| <font style="color:rgb(38, 38, 38);background-color:rgba(0, 0, 0, 0.06);">riscv64-nemu</font> | <font style="color:rgb(38, 38, 38);">软件模拟器</font> | **<font style="color:rgb(38, 38, 38);">最常用</font>**<font style="color:rgb(38, 38, 38);">。验证程序在 RISC-V 指令集下是否正确。</font> |
| <font style="color:rgb(38, 38, 38);background-color:rgba(0, 0, 0, 0.06);">riscv64-xs</font> | **<font style="color:rgb(38, 38, 38);">香山仿真</font>** | **<font style="color:rgb(38, 38, 38);">核心参数</font>**<font style="color:rgb(38, 38, 38);">。生成的镜像专门给香山仿真环境跑。</font> |
| <font style="color:rgb(38, 38, 38);background-color:rgba(0, 0, 0, 0.06);">riscv64-qemu</font> | <font style="color:rgb(38, 38, 38);">标准模拟器</font> | <font style="color:rgb(38, 38, 38);">当你怀疑 NEMU 有 Bug 时，用它做交叉对比。</font> |

### 3.2.1 概述

`arch-fuzz` 项目提供多种测试套件，用于对 RISC-V 处理器及模拟器进行模糊测试。每个测试套件采用不同的策略生成测试用例，以发现处理器实现中的潜在问题。

### 3.2.2 核心组件

* **生成器 (Generators)**: 负责生成汇编指令、操作数、基本块和程序
* **发布器 (Publishers)**: 负责编译、运行测试用例并收集结果
* **指令集 (InstructionSet)**: 提供 RISC-V 指令的元数据和约束信息

### 3.2.3 使用步骤

1. **首先是生成合适的 yaml 文件**

参考yaml文件中的对象格式，相关可选值在 `base.py` 文件里面

```python
class InstructionConstraint(ABC):
    extension = ""
    format

    def __init__(
        self,
        mnemonic: str,
        operands: List[OperandConstraint],
        format: InstructionFormat,
        type: str = ""
    ):
        self.mnemonic = mnemonic.replace("_", ".")
        self.operands = operands
        self.format = format
        self.type = type
```

操作数限制: `data_structure.py`

```python
class OperandConstraint:
    type: str  # 'reg', 'imm', 'csr', 'vreg', 'freg', 'label', 'vm' etc.
    literal:str = ""
    width: int = 5
    signed: bool = False
    alignment: int = 1
    range: tuple = (-1, -1)  # 对于立即数
    reg_type: Optional[RegisterType] = None  # 寄存器类型
    required: str = ""
    field:str = ''
    option:bool = False
    mem:bool = False
```

2. 然后按照加入的 yaml 文件名字，在 config.py 文件和 build.py 文件的对应位置加上相应的指令集名字
3. 运行 generate\_instruction.py 和 main.py 文件

:::info

#### 🔟 验证工具：Arch-Fuzz 【拓展】

当你觉得香山跑程序很稳的时候，可以用 **Arch-Fuzz**。

* **它干什么**：随机生成各种“奇葩”指令组合，试图让 CPU 出错。
* **定位**：属于高级验证阶段(极端验证)，新手在初期可以不关注。

:::

## 3.3 性能测试方法

### 3.3.1 SimPoint技术概述

官方文档：[SimPoint - XiangShan 官方文档](https://docs.xiangshan.cc/zh-cn/latest/tools/simpoint/)

现代计算机架构研究需要理解处理器在执行应用程序时的周期级行为。这需要模拟器模型化每一个周期。但是，这样做是以牺牲速度为代价的。更糟糕的是，为了在性能、复杂性、面积、和功耗之间做权衡，benchmark需要在不同的架构配置上模拟运行。

做计算机架构研究就绕不过simpoint，为了度量所实现的微架构的性能，通常都需要在新架构上运行一些大型的benchmark程序，俗称跑分。在多种多样的跑分软件中，SPEC基本上称霸了CPU性能测试的江湖，SPEC对厂商有强约束条件，在跑分时需要严格按照规定的规则运行SPEC benchmark程序，跑出的分数也就更能体现微架构的性能。但是 spec 程序的速度很慢，要加快仿真的速度，这里就有 checkpoint 的方法。

#### 5️⃣ Benchmark：衡量 CPU 有多快 【理解】

**为什么需要 **5️⃣**？**\
就像跑分软件一样，我们需要标准化的程序来衡量香山的性能。

因此，Benchmark就是用来测CPU性能的。

| <font style="color:rgb(38, 38, 38);">测试</font> | <font style="color:rgb(38, 38, 38);">用途</font> |
| --- | --- |
| **Hello** | <font style="color:rgb(38, 38, 38);">环境验证 （听个响）</font> |
| **CoreMark** | <font style="color:rgb(38, 38, 38);">性能基准，初级性能测试（测测主频和流水线效率）。</font> |
| **SPEC CPU** | <font style="color:rgb(38, 38, 38);">架构评估，终极性能测试（模拟真实工业负载，非常复杂）。</font> |

### 3.3.2 核心思想

程序运行时所执行的指令流切分成连续的不重叠**intervals**，然后把有相似行为的**intervals**聚和成一个**phase**，然后在每个聚类中选取一个指令**interval**做代表，使用GEM5仿真的时候只执行选取出的指令**interval**运行，然后根据每个聚类的切分成连续的不重叠**intervals**大小对执行的指令**interval**加权重，再加到一起就是原程序的跑分结果。实验证明在指令集和编译器相同的情况下，与原程序跑分结果约等，可以作为相同指令集/编译器情况下的微架构性能对比。

### 3.3.3 如何制作复杂性能测试程序的切片？

#### 6️⃣\*\*<font style="color:rgb(38, 38, 38);"> 为什么 SPEC 不能直接跑？ </font>\*\*

**<font style="color:rgb(38, 38, 38);">核心矛盾</font>**<font style="color:rgb(38, 38, 38);">：</font>

* **<font style="color:rgb(38, 38, 38);">仿真太慢</font>**<font style="color:rgb(38, 38, 38);">：香山仿真器运行 1 秒钟的真实时间，可能只模拟了 CPU 零点几毫秒的动作</font>
* **<font style="color:rgb(38, 38, 38);">数据太大</font>**<font style="color:rgb(38, 38, 38);">：SPEC 完整跑一遍可能要运行几万亿条指令，仿真器要跑几年。</font>

<font style="color:rgb(38, 38, 38);">解决方案：</font>

```plain
SimPoint
```

参考文档： [SPEC生成Simpoint](https://bosc.yuque.com/yny0gi/fzefxm/fd3ll585d99xl7k0)

编译 spec:   [编译 SPEC 2006](https://bosc.yuque.com/yny0gi/fzefxm/agbcrhau8zz2g1qu)

```bash
#使用命令
git clone git@github.com:OpenXiangShan/riscv-rootfs.git
cd riscv-rootfs
export RISCV_ROOTFS_HOME=$PWD
make
echo $PWD
#编译spec06
export PATH=$PATH:/nfs/home/wangran/toolchain/gcc-ubuntu241108/bin 
export SPEC=/nfs/home/share/cpu2006v99
export SPEC_LITE=$(pwd)
 cd CPU2006LiteWrapper/

pushd $SPEC && source shrc && popd

git clone git@github.com:xyyy1420/checkpoint_scripts.git
cd checkpoint_scripts/checkpoint_scripts
source /nfs/home/share/workload_env/env.sh
export XIANGSHAN_FDT=/nfs/home/share/workload_env/workload_build_env/dts/build/xiangshan.dtb
export GCPT_HOME=/nfs/home/share/workload_env/LibCheckpointAlpha
export RISCV_ROOTFS_HOME=/nfs/home/liruoshi/riscv-rootfs #这里换成前面的根目录
```

### 3.3.4 如何使用香山执行 spec 的切片

SPEC 设计这套测试套件旨在利用从真实用户应用程序开发的工作负载，对各种实际硬件上的计算密集型性能进行比较测量。这些基准测试以源代码形式提供，用户需要熟练使用编译器命令以及通过控制台或命令提示符窗口使用命令解释器执行其他命令，才能生成可执行二进制文件。

[仿真测试香山 SPEC checkpoints 性能评估说明](https://bosc.yuque.com/yny0gi/gr7hyo/simulation-perf-test)，设置好权限之后执行开箱即用的脚本：\
主要功能模块包括：

1. 环境配置 (set\_env 函数)

* 加载环境变量
* 配置两套测试数据集（GCC12编译，O3优化，30%/80%覆盖率）
* 设置检查点路径和配置文件路径

2. 参数配置

* 服务器列表：7台小型服务器（open07-open15）
* 线程数：16
* 版本标识：kunminghu
* 测试目录名：可自定义或自动生成（格式：SPEC06\_EmuTasks\_月日\_时分）

3. 编译阶段（当前被注释）

* 使用200个并行任务编译仿真器
* 启用DRAMSim3内存仿真和跟踪功能

4. 运行阶段

* 在7台服务器上分布式运行仿真测试（支持断点续跑）
* 生成测试报告并保存到日志文件
* 可选：发送邮件通知（当前被注释）

**成功运行脚本之后输出**

```makefile
yourhome@open01:~/xs-env/XiangShan$ bash tmp/cr-run.sh
SET XS_PROJECT_ROOT: /nfs/home/yourhome/xs-env
SET NOOP_HOME (XiangShan RTL Home): /nfs/home/yourhome/xs-env/XiangShan
SET NEMU_HOME: /nfs/home/yourhome/xs-env/NEMU
SET AM_HOME: /nfs/home/yourhome/xs-env/nexus-am
SET DRAMSIM3_HOME: /nfs/home/yourhome/xs-env/DRAMsim3
checkpoint test: SPEC06_EmuTasks_0126_1713
========== make start at 2026年 01月 26日 星期一 17:13:17 CST ==========
========== make end at 2026年 01月 26日 星期一 17:13:17 CST ==========
********** cal start at 2026年 01月 26日 星期一 17:13:17 CST **********
RESUME: True
evaluate execute hours: 15.2768976
opitimize execute hours: 15.2768976
All:   142
First: bwaves_28260_0.701585
Last:  xalancbmk_23785_0.079368
2026年 01月 26日 星期一 17:13:26 CST
ssh yourhome@open07 NOOP_HOME=/nfs/home/yourhome/xs-env/XiangShan numactl -m 0 -C 0-15 /nfs/home/yourhome/xs-env/XiangShan/build/emu --diff /nfs/home/yourhome/xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so --enable-fork -W 20000000 -I 40000000 -r /nfs-nvme/home/share/zyy/shared_payloads/old-gcpt-restorer/gcpt.bin -i /nfs-nvme/home/share/checkpoints_profiles/spec06_rv64gcb_o3_20m_gcc12-fpcontr-off/take_cpt/bwaves/28260/_28260_0.701585_.gz -s 7412
2026年 01月 26日 星期一 17:13:43 CST
ssh yourhome@open08 NOOP_HOME=/nfs/home/yourhome/xs-env/XiangShan numactl -m 0 -C 32-47 /nfs/home/yourhome/xs-env/XiangShan/build/emu --diff /nfs/home/yourhome/xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so --enable-fork -W 20000000 -I 40000000 -r /nfs-nvme/home/share/zyy/shared_payloads/old-gcpt-restorer/gcpt.bin -i /nfs-nvme/home/share/checkpoints_profiles/spec06_rv64gcb_o3_20m_gcc12-fpcontr-off/take_cpt/cactusADM/16088/_16088_0.670211_.gz -s 8281
2026年 01月 26日 星期一 17:13:59 CST
ssh yourhome@open08 NOOP_HOME=/nfs/home/yourhome/xs-env/XiangShan numactl -m 1 -C 64-79 /nfs/home/yourhome/xs-env/XiangShan/build/emu --diff /nfs/home/yourhome/xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so --enable-fork -W 20000000 -I 40000000 -r /nfs-nvme/home/share/zyy/shared_payloads/old-gcpt-restorer/gcpt.bin -i /nfs-nvme/home/share/checkpoints_profiles/spec06_rv64gcb_o3_20m_gcc12-fpcontr-off/take_cpt/calculix/200664/_200664_0.243956_.gz -s 9119
```

### 3.3.5 SimPoint技术原理

#### 7️⃣\*\*<font style="color:rgb(38, 38, 38);"> SimPoint 与 Checkpoint：统计学的胜利 </font>\*\*

<font style="color:rgb(38, 38, 38);">为了解决上面的“太慢”问题，香山使用了 </font>**<font style="color:rgb(38, 38, 38);">SimPoint</font>**<font style="color:rgb(38, 38, 38);"> 技术：</font>

* **<font style="color:rgb(38, 38, 38);">本质</font>**<font style="color:rgb(38, 38, 38);">：</font>**<font style="color:rgb(38, 38, 38);">SimPoint = 用采样替代完整执行</font>**<font style="color:rgb(38, 38, 38);">。</font>
* **<font style="color:rgb(38, 38, 38);">逻辑</font>**<font style="color:rgb(38, 38, 38);">：</font>
  1. **<font style="color:rgb(38, 38, 38);">分析</font>**<font style="color:rgb(38, 38, 38);">：先用快速模拟器把程序跑一遍，找出哪些片段是重复的。</font>
  2. **<font style="color:rgb(38, 38, 38);">采样</font>**<font style="color:rgb(38, 38, 38);">：只选取最有代表性的片段（例如只取 1 亿条指令）。</font>
  3. **<font style="color:rgb(38, 38, 38);">保存 (Checkpoint)</font>**<font style="color:rgb(38, 38, 38);">：把这 1 亿条指令开始时的 CPU 状态保存下来。</font>
  4. **<font style="color:rgb(38, 38, 38);">快进</font>**<font style="color:rgb(38, 38, 38);">：仿真时直接从这个状态“瞬移”开始。</font>

:::danger
**<font style="color:rgb(38, 38, 38);">一句话总结</font>**<font style="color:rgb(38, 38, 38);">：SimPoint = 用统计学替代完整执行</font>

<font style="color:rgb(38, 38, 38);">通过 SimPoint，将几年的仿真时间缩短到了几小时，且准确度保持在 98% 以上。</font>

:::

<font style="color:rgb(38, 38, 38);"></font>

![1770715819511-591a73d5-6a47-41da-803a-4298fba520f5.png](img/chapter-3-applications/figure-003-sim-point-checkpoint.png)

图2：SimPoint技术原理

**图 2 解读：**

这张图展示了SimPoint技术的核心工作原理，它是一种用于加速处理器性能测试的采样技术：

1. **完整程序执行**（左侧）
   * 传统的性能测试需要运行完整程序
   * 耗时非常长，SPEC程序可能需要数天甚至数周
   * 每个指令都需要被模拟执行
2. **程序特征分析**（中部）
   * 分析程序的执行特征（如指令混合、分支行为等）
   * 将程序划分为多个连续的区间（intervals）
   * 计算每个区间的特征向量
3. **聚类分析**（中部下方）
   * 使用聚类算法（如K-means）将相似区间分组
   * 每个组代表程序的一个"阶段"（phase）
   * 同一阶段内的区间具有相似行为特征
4. **代表性采样**（右侧）
   * 从每个聚类中选择一个代表性区间
   * 只运行这些代表性区间
   * 根据聚类大小对结果进行加权
5. **性能估算**（底部）
   * 加权后的结果近似等于完整程序运行结果
   * 精度通常在1-3%误差范围内
   * 速度提升可达数十倍甚至数百倍

**关键优势：** SimPoint技术让原本需要数天完成的SPEC测试可以在几小时内完成，大大加快了处理器性能评估的速度。

### 3.3.6 checkpoint(simpoint)加快 spec 测试

#### **<font style="color:rgb(38, 38, 38);">9️⃣</font>\*\*\*\*<font style="color:rgb(38, 38, 38);"> checkpoint 是什么（紧跟 SimPoint）【理解】</font>**

<font style="color:rgb(38, 38, 38);">关系：</font>

```plain
SimPoint → 找关键片段
checkpoint → 保存状态
```

[SimPoint - XiangShan 官方文档](https://docs.xiangshan.cc/zh-cn/latest/tools/simpoint/)

[香山SimPoint](https://bosc.yuque.com/staff-xmw8rg/ygobtw/tdvf6kbfl0q74ilm)

参考论文：<https://cseweb.ucsd.edu//~calder/papers/ASPLOS-02-SimPoint.pdf>

可以用作加快 NEMU xiangshan 等的仿真过程，默认在使用香山的 `spec`测试里面是包含这里的采样技术的。

SimPoint是体系结构研究的重要工具，主要思想是：将程序执行的指令流分割成不同的段，然后将程序段聚类，相同类型的程序段会被分到同一类，然后在同一聚类中选取一个程序段作为代表，使用仿真软件运行作为代表的指令段，然后根据指令段所在聚类的规模大小进行加权计算，就可以得出约等于原程序的跑分结果（需要在指令集和编译器相同的情况下运行）。

## 学习路径规划 （要改图）

### 3.6.1 第一阶段：基础应用程序（1周）

**目标：** 掌握简单的应用程序编译和运行\
**任务：**

1. 阅读本章，理解nexus-am框架的基本概念
2. 按照1.3.2节的步骤编译和运行Hello XiangShan
3. 尝试修改Hello XiangShan程序，输出不同内容

### 3.6.2 第二阶段：性能测试程序（2周）

**目标：** 掌握性能测试程序的编译和运行\
**任务：**

1. 编译和运行CoreMark性能测试程序
2. 理解性能测试指标的含义
3. 尝试分析测试结果

### 3.6.3 第三阶段：复杂基准测试（3周）

**目标：** 了解SPEC基准测试的使用\
**任务：**

1. 学习SimPoint技术的基本原理
2. 理解checkpoint的工作机制
3. 尝试运行简单的SPEC测试脚本

### 3.6.4 第四阶段：高级测试技术（4周）

**目标：** 掌握高级测试和验证技术\
**任务：**

1. 学习arch-fuzz模糊测试框架
2. 理解自动化测试脚本的编写
3. 尝试编写简单的测试用例

# 总结与建议

### 1 应用程序类型概览

通过本章的学习，你应该已经了解了香山处理器测试中常用的三种应用程序类型：

1. **简单测试程序**（如Hello XiangShan）
   * 用途：验证基本功能是否正常
   * 特点：简单、快速、易于调试
2. **性能测试程序**（如CoreMark）
   * 用途：评估处理器性能指标
   * 特点：标准化、可比较、中等复杂度
3. **复杂基准测试**（如SPEC）
   * 用途：全面评估处理器性能
   * 特点：复杂、耗时、权威性强

### 2 测试技术概览

1. **传统完整测试**
   * 运行完整程序
   * 结果准确但耗时极长
2. **SimPoint采样测试**
   * 运行代表性程序片段
   * 速度快，精度可接受
   * 适合快速迭代开发
3. **模糊测试**（arch-fuzz）
   * 自动生成测试用例
   * 发现边界情况和异常行为
   * 适合验证测试

:::info
**如果你能回答以下问题，说明你已经成功通关：**

* \[ ] 为什么需要 AM？（提示：接口层）
* \[ ] 跑香山仿真时，ARCH 应该填什么？（提示：riscv64-xs）
* \[ ] 看到什么字样代表程序跑通了？（提示：HIT GOOD TRAP）
* \[ ] 为什么不能把 SPEC 全量跑完？（提示：仿真太慢）

:::

应用程序测试是处理器开发中至关重要的一环，它确保处理器不仅能够运行，而且能够高效、稳定地运行。掌握这些测试技术将为你的处理器设计之路打下坚实的基础。

祝你在处理器测试的道路上越走越远！


> 更新: 2026-04-24 01:45:42  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/fuwntm9m4dakokve>
