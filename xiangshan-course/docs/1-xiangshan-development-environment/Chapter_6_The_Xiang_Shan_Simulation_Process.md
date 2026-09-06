# 第六章：香山核仿真流程

# 6 香山核仿真流程

:::info

### 🎯\*\*<font style="color:rgb(38, 38, 38);"> 本章目标</font>\*\*

学完本章，你将能够清晰回答：

* **程序如何在香山 CPU 中运行？**（理解执行链条）
* **为什么 CPU 设计必须先仿真？**（理解工程动机）
* **仿真流程中每个组件负责什么？**（逐一了解）
* **系统如何判断 CPU 是否正确？**（掌握差分测试原理）
* **一次完整仿真的标准指令是什么？**（掌握核心操作）

:::

![1771068691800-b38a602b-a511-4c65-b520-c62f1204e9a9.png](img/chapter-5-dramsim3-memory-simulation/figure-001-dramsim3-memory-simulation.png)

## 6.1 为什么必须做CPU仿真

在芯片设计领域，**“流片”（制造真实芯片）的成本极高，动辄数千万甚至上亿元**。

```plain
设计 → 流片 → 制造 → 测试
```

如果制造出来发现错误，那么整个芯片报废，流片费用白费。

:::info
旁白：如果芯片造出来才发现 `1 + 1 = 3`，整个项目将彻底报废。这将是重大的失败。

:::

因此工程上必须先做：在制造之前，先在电脑里用软件模拟运行未来的 CPU。

* **目的**：在物理制造之前，揪出所有的逻辑 Bug。

:::color4
**📌**\*\* 本章核心理解一句话：\*\* CPU 仿真 = 在电脑里提前运行并验证“还没有生产制造出来”的CPU。

:::

### 6.1.1 仿真系统全景图

在开始敲命令前，请务必记住这张逻辑地图：

```plain
程序 (.bin) 
 ↓
AM运行环境
 ↓
CPU仿真器NEMU（香山核）
 ↓
参考模型 (Spike)
 ↓
判断对错 （结果比较）
 ↓
判断是否正确
```

### **6.1.2 各组件分别是什么？**

**系统中出现的名词很多，新手最容易混乱。下面一次理清：**

| **组件** | **本质角色** | **类比** |
| :---: | :---: | :---: |
| 程序 | 测试输入 | 试卷题目 |
| AM | 运行环境 | 考场规则 |
| 香山核 | 被测试CPU | 学生 |
| 参考模型 | 标准答案 | 标准解 |
| 比较器 | 判卷系统 | 老师 |

:::color4
**不要忘了：最关键关系 香山核是被测试对象，不是标准答案。**

:::

## 6.2 香山核整体仿真流程

:::warning
**香山核的整体仿真流程:**

1. **Chisel转化成SystemVerilog** - 将高级硬件描述转换为标准硬件语言
2. **Verilator等仿真工具把SV转换成C++** - 生成可仿真的C++模型
3. **仿真器将C++编译为可执行程序emu** - 生成可以直接运行的程序
4. **使用emu运行workload** - 在仿真器中执行测试程序

**主要使用的命令：**

* `**make verilog**` - 生成SV代码，可以用于FPGA验证
* `**make sim-verilog**` - 生成用于仿真的SV代码
* `**make emu**` - 运行Verilator仿真，直接输出可以执行的程序
* `**make clean**` - 清理difftest目录，删除整个build目录，清理前面生成的SV文件，编译修改编译参数之后重新生成
* `--help` - 查看命令的参数

:::

:::info
**新手理解：** 想象你要烤一个蛋糕🎂：

1. **写食谱**（Chisel代码） - 描述蛋糕的制作方法
2. **标准化食谱**（SystemVerilog） - 将方法写成标准格式
3. **准备厨房工具**（Verilator编译） - 准备好所有厨具
4. **开始烘焙**（运行emu） - 按照食谱开始制作
5. **品尝测试**（运行workload） - 测试蛋糕是否成功

:::

## 6.3 香山示例命令

### 6.3.1 基础环境设置

在单独使用香山时需要配置的命令：

```bash
git clone https://github.com/OpenXiangShan/XiangShan.git
cd XiangShan; git fetch origin; git checkout origin/xxx; make init;
export NOOP_HOME=`pwd`
make_verilog
#这里由于网络可能会克隆缺失导致编译失败，重新进行上面的命令就好
```

在单独克隆和运行香山的时候，使用diftest作为对比的nemu版本在ready-to-run文件里面。

## 6.4 编译流程详解

### 6.4.1 参考文档链接

[前端开发环境 - XiangShan 官方文档](https://docs.xiangshan.cc/zh-cn/latest/tools/xsenv/)

### 6.4.2 使用Verilator生成香山核的仿真程序

Verilator的核心作用是将Verilog/SystemVerilog描述的硬件电路设计（如香山核RTL代码）**高效编译成C++或SystemC中间库**，随后开发者编写C++包装文件调用这些库，并通过标准C编译器**统一链接生成可执行的仿真程序**；这一过程将硬件仿真转化为高性能的软件执行，显著提升仿真速度，特别适合大规模处理器（如香山核）的功能验证与回归测试。

```bash
make emu EMU_TRACE=1 -j32
```

这里`CONFIG`参数的不同选择在config文件里面。

### 6.4.3 可配置参数系统

香山处理器提供了丰富的配置选项，这些配置在`/xs-env/XiangShan/src/main/scala/top/Configs.scala`文件中定义。主要配置类包括：

1. **MinimalConfig** - 最小配置，用于快速仿真（一般不用 ）
2. **DefaultConfig** - 默认配置，平衡性能和面积
3. **MediumConfig** - 中等配置，更好的性能
4. **FpgaDefaultConfig** - FPGA专用配置
5. **KunminghuV2Config** - 昆明湖V2版本配置

### 6.4.4 可执行代码在XiangShan上运行

用verilator，香山如何仿真执行：

```shell
# 使用下载好的nemu
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --diff ready-to-run/riscv64-nemu-interpreter-so
#使用自己编译的nemu
./build/emu -i $AM_HOME/apps/hello/build/hello-riscv64-xs.bin --diff /nfs/home/yourhome/xs-env/NEMU/build/riscv64-nemu-interpreter-so
```

加载相应的bin文件，之后在emu上面仿真。

最终成功的输出结果：这里终端输出`hello xiangshan`即为成功。\
参考成功的示例：

![1770621631510-97acd557-05e5-4eb1-aed2-c6ae8f1ac81e.png](img/chapter-6-the-xiang-shan-simulation-process/figure-002-nemu-run.png)

图1：香山仿真成功运行Hello World程序

**图表解读：**

这张图展示了香山处理器成功运行Hello World程序的终端输出：

1. **仿真器启动信息**（顶部）
   * 显示仿真器编译时间和配置信息
   * 包括处理器核心数、缓存配置等
   * 验证环境设置正确
2. **程序加载过程**（中部）
   * 显示二进制文件加载信息
   * 包括加载地址、文件大小等
   * 验证程序加载成功
3. **程序执行输出**（中部下方）
   * 显示程序运行结果
   * "hello xiangshan"表示程序成功执行
   * 验证处理器功能正常
4. **仿真统计信息**（底部）
   * 显示仿真性能数据
   * 包括执行指令数、仿真速度等
   * 帮助评估仿真效率

**关键信息：** 看到"hello xiangshan"输出，说明从Chisel代码到仿真运行的完整流程都成功了！

### 6.4.5 Chisel代码如何转换成SV代码

在`/xs-env/XiangShan`下运行`make verilog`，该命令将会编译香山的Chisel代码，生成**SystemVerilog**，输出的文件在`XiangShan/build/rtl/XSTop.sv`。

**通过Mill运行Chisel编译器生成SystemVerilog**：

```makefile
verilog: $(call docker-deps,$(TOP_V))

$(SIM_TOP_V): $(SCALA_FILE) $(TEST_FILE)
  mkdir -p $(@D)
  @echo -e "\n[mill] Generating Verilog files..." > $(TIMELOG)
  @date -R | tee -a $(TIMELOG)
    #主要的Mill编译命令
    #使用Mill作为构建工具
    #运行测试套件中的特定主类
    #传递硬件配置参数（核心数、发射宽度等）
    #控制仿真行为（周期数、波形等）
    #输出到指定目录便于后续分析
  $(TIME_CMD) mill -i $(MILL_BUILD_ARGS) xiangshan.test.runMain $(SIMTOP)    \
    --target-dir $(@D) --config $(CONFIG) --issue $(ISSUE) $(SIM_MEM_ARGS)    \
    --num-cores $(NUM_CORES) $(SIM_ARGS) --full-stacktrace
    
    ifeq ($(CHISEL_TARGET),systemverilog)# 这里定义了目标的文件是sv
    $(MEM_GEN_SEP) "$(MEM_GEN)" "$@.conf" "$(@D)"
    @{ git log -n 1; git diff; } | sed 's/^/\/\// ' > $(dir $@).__diff__
    @cat $(dir $@).__diff__ $@ > $(dir $@).__out__ && mv $(dir $@).__out__ $@
ifeq ($(PLDM),1)
    sed -i -e 's/$$fatal/$$finish/g' $(RTL_DIR)/*.$(RTL_SUFFIX)
    sed -i -e '/sed/! { \|$(SED_IFNDEF)|, \|$(SED_ENDIF)| { \|$(SED_IFNDEF)|d; \|$(SED_ENDIF)|d; } }' $(RTL_DIR)/*.$(RTL_SUFFIX)
else
ifeq ($(ENABLE_XPROP),1)
    sed -i -e "s/\$$fatal/assert(1\'b0)/g" $(RTL_DIR)/*.$(RTL_SUFFIX)
else
    sed -i -e 's/$$fatal/xs_assert_v2(`__FILE__, `__LINE__)/g' $(RTL_DIR)/*.$(RTL_SUFFIX)
endif
endif
    sed -i -e "s/\$$error(/\$$fwrite(32\'h80000002, /g" $(RTL_DIR)/*.$(RTL_SUFFIX)
endif

sim-verilog: $(call docker-deps,$(SIM_TOP_V))+
```

### 6.4.6 Mill工具使用

* **管理Scala/Chisel项目的依赖关系**
* **编译Scala源代码**
* **处理项目模块结构**

命令：`mill -i xiangshan.test.runMain $(SIMTOP)`

* `-i`：交互模式，允许与构建交互
* `xiangshan.test.runMain`：指定要运行的Main类路径
* `$(SIMTOP)`：实际的Main类名（硬件生成器）
* Mill会加载这个Main类并执行，触发Chisel编译流程

## 6.5 仿真方法详解

### 6.5.1 仿真命令分类

1. 行为仿真make emu
2. 可综合的代码的功能仿真：
   * `make verilog -jN` - 没有difftest，用于流片
   * `make sim-verilog -jN` - 有difftest，用于仿真

### 6.5.2 使用 verilator 完成仿真（make emu）

#### 基本操作命令

使用Verilator生成香山核的仿真程序，进入`XiangShan`目录，运行命令：

```shell
make emu CONFIG=KunminghuV2Config EMU_TRACE=1 -j32
```

:::success
**参数含义：**

`CONFIG` - 香山的仿真程序的配置选择

`EMU_TRACE=1` - 会为仿真程序添加波形输出功能，允许在仿真过程中启用波形输出。

`EMU_TRACE=1`默认生成的波形格式为vcd，可以使用gtkwave等开源工具或dve等商业工具进行查看。另外，也可以通过`EMU_TRACE=vcd`以及`EMU_TRACE=VCD`命令生成vcd格式的波形，二者的作用和`EMU_TRACE=1`相同。由于vcd波形体积较大，需要占用大量硬盘空间；同时使用gtkwave等开源工具打开波形的速度较慢，我们提供`EMU_TRACE=fst`或`EMU_TRACE=FST`命令生成fst格式的波形。fst格式的波形大小不到vcd格式波形大小的10%，但缺点是该格式的波形为gtkwave专属，只能由gtkwave打开。

:::

要仿真默认配置的完整香山核，可以使用以下命令：

```shell
make emu -j32
```

生成行为仿真用verilog代码和可综合verilog代码的命令是不同的。\
通过make verilog命令可以生成可综合的verilog代码；\
而**make emu用于行为仿真**。

#### PerfCCT性能计数器

官方介绍文档：[PerfCCT usage - 香山GEM5模拟器文档](https://xs-gem5.readthedocs.io/zh-cn/latest/tools/alignToRTL/PerfCCT_usage/)

在编译香山的时候使用的命令：

```makefile
  生成仿真Verilog
  make sim-verilog
  #或启用DRAMsim3，编译香山，带上WITH_CHISELDB=1
  make sim-verilog WITH_DRAMSIM3=1 WITH_CHISELDB=1 DRAMSIM3_HOME=/nfs/home/yourhome/xs-env/DRAMsim3

  运行仿真
  make emu
  #或自定义核心数，
  make emu NUM_CORES=4
  执行emu时，带上--dump-db --dump-select-db "lifetime"，生成一个db文件
```

### 6.5.2 VCS仿真（make simv）

注意：这里只能在`eda01`服务器上面进行，否则会报错。

[VCS验证框架介绍 - XiangShan官方文档](https://docs.xiangshan.cc/zh-cn/latest/tools/vcs/)

[附件: 【环境】昆明湖vcs+verdi编译仿真流程(1).pdf](./attachments/-U527MQWZ2MsCMFu/【环境】昆明湖vcs+verdi编译仿真流程\(1\).pdf)

进入香山根目录，执行，进行编译：

```bash
-bash-4.2$ make simv RELEASE=1 CONSIDER_FSDB=1 -j16
make -C ./difftest simv NUM_CORES=1 RTL_SUFFIX=sv
make[1]: Entering directory `/nfs/home/yourhome/xs-env/XiangShan/difftest'
vcs.mk:51: *** VERDI_HOME is not set. Try whereis verdi, abandon /bin/verdi and set VERID_HOME manually.  Stop.
make[1]: Leaving directory `/nfs/home/yourhome/xs-env/XiangShan/difftest'
make: *** [simv] Error 2
```

**报错1**：缺少环境变量。运行`source ~/.bashrc`。

`simv`会被在`difftest`目录中生成。

编译香山的Chisel代码，生成Verilog，输出的文件在XiangShan/build/XSTop.v。make verilog命令生成的Verilog文件用于生成FPGA的bitstream和流片，去除了Difftest等仿真用的调试模块。make sim-verilog命令生成带有Difftest的用于仿真的Verilog文件。

```bash
make sim-verilog -jN    //带difftest
make verilog -jN    //N可以用12
#也可以直接make simv，simv是依赖于sim-verilog的
make simv -jN
```

**报错2：生成波形的时候报错**\
open01执行文件为build/simv，读入workload，使用NEMU作为参照进行difftest测试，并生成fsdb波形文件的时候报错：

```bash
yourhome@open01:~/xs-env/XiangShan$ ./build/simv +workload=./ready-to-run/microbench.bin  +diff=./ready-to-run/riscv64-nemu-interpreter-so +dump-wave=fsdb
./build/simv: /lib/x86_64-linux-gnu/libpthread.so.0: version `GLIBC_PRIVATE' not found (required by ./build/simv)
```

服务器不对，应该是用eda01服务器就可以：

```bash
yourhome@eda01:~/xs-env/XiangShan$ ./build/simv +workload=./ready-to-run/microbench.bin  +diff=./ready-to-run/riscv64-nemu-interpreter-so +dump-wave=fsdb
Chronologic VCS simulator copyright 1991-2020
Contains Synopsys proprietary information.
Compiler version Q-2020.03-SP2_Full64; Runtime version Q-2020.03-SP2_Full64;  Jan 16 14:51 2026
ram image:./ready-to-run/microbench.bin
diff-test ref so:./ready-to-run/riscv64-nemu-interpreter-so
Core  x's Commit SHA is: f9fd32a05b, dirty: 0
*Verdi* Loading libsscore_vcs202003.so
FSDB Dumper for VCS, Release Verdi_R-2020.12-SP1, Linux x86_64/64bit, 03/02/2021
(C) 1996 - 2021 by Synopsys, Inc.
*Verdi* : Create FSDB file 'simv.fsdb'
*Verdi* : Begin traversing the scopes, layer (0).
*Verdi* : Enable +mda dumping.
*Verdi* : End of traversing.
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 128M.
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 256M.
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 512M.
simv compiled at Jan 16 2026, 11:46:26
Using simulated 8386560MB RAM
The image is ./ready-to-run/microbench.bin
Using simulated 32768B flash
The reference model is ./ready-to-run/riscv64-nemu-interpreter-so
The first instruction of core 0 has commited. Difftest enabled.
*Verdi* FSDB: For performance reasons, the Memory Size Limit has been increased to 1024M.
======= Running MicroBench [input *test*] =======
[qsort] Quick sort: * Passed.
```

* `+workload`：从ready-to-run中选取要使用的workload（.bin文件）。
* `+diff`：选取nemu动态链接库，一般在ready-to-run中会有。如果没有或想用xs-env下的环境变量，参考上面的NEMU编译。
* `+dump-wave=fsdb`：如果不打波形可以去掉

### 6.5.3 仿真配置详解

香山的配置参数文件：`/xs-env/XiangShan/src/main/scala/top/Configs.scala`

自定义配置参考文档：[香山参数系统说明 - XiangShan官方文档](https://docs.xiangshan.cc/zh-cn/latest/misc/config/)

#### 香山处理器可配置参数说明

|  |  |  |  |
| --- | --- | --- | --- |
| **参数名称** | **含义与作用** | **可选值** | **使用示例** |
| **CONFIG** | 指定处理器配置类，决定微架构参数 | MinimalConfig、DefaultConfig、MediumConfig、FpgaDefaultConfig、KunminghuV2Config | `make emu CONFIG=MinimalConfig` |
| **MFC** | Chisel到Verilog的编译参数，控制代码生成过程 | --target、--firtool-opt、--split-verilog、--dump-fir、--repl-seq-mem、--disable-annotation-unknown | 在Makefile中定义 |
| **EMU\_TRACE** | 控制是否生成仿真波形文件用于调试 | 1/vcd/VCD、fst/FST、不设置 | `make emu EMU_TRACE=1`      或 `make emu EMU_TRACE=fst` |
| **EMU\_THREADS** | 启用Verilator的多线程仿真，加速仿真速度 | 0（单线程）、2、4、8等 | `make emu EMU_THREADS=2` |
| **NUM\_CORES** | 指定生成的处理器核心数量 | 正整数（1, 2, 4, 8等） | `make emu NUM_CORES=2` |
| **WITH\_DRAMSIM3** | 启用DRAMSim3进行精确的DRAM时序仿真 | 1（启用）、0或不设置（使用简单内存模型） | `export DRAMSIM3_HOME=/path/to/dramsim3; make emu WITH_DRAMSIM3=1` |
| **WITH\_CHISELDB** | 启用Chisel数据库，用于调试和性能分析 | 1（启用，默认）、0（禁用） | `make emu WITH_CHISELDB=1` |

## 6.6 波形查看方法

### 6.6.1. 使用 emu 保存波形文件

我们可以使用 `--dump-wave` 参数打开波形，并使用 `-b` 和 `-e` 参数设置生成波形的开始和结束周期，例如想要生成 10000 ~ 11000 周期的波形，可以使用如下命令：

```plain
./build/emu -i MY_WORKLOAD.bin --dump-wave -b 10000 -e 11000
```

其中 `-b` 和 `-e` 的默认值为 0，注意仅当 `-e` 参数大于 `-b` 时才会真正记录波形；波形文件将会生成在 `./build/` 目录下，根据 `EMU_TRACE` 的不同参数，格式为 vcd 或 fst。vcd 波形文件可以后续使用 gtkwave 等开源工具或者 dve 等商业工具进行查看，fst 波形文件目前只能由 gtkwave 打开。

这里的波形会保存在一个.vcd 文件里面，而不是会直接显示。

### 6.6.2. gtkwave 查看波形文件

**报错：**\
display 问题：

```plain
yourhome@open01:~/xs-env/XiangShan$ gtkwave -f /nfs/home/yourhome/xs-env/XiangShan/build/2026-01-15-17-49-27_10000.vcd
Could not initialize GTK!  Is DISPLAY env var/xhost set?

VCD files and save files may be compressed with zip or gzip.
GHW files may be compressed with gzip or bzip2.
Other formats must remain uncompressed due to their non-linear access.
Note that DUMPFILE is optional if the --dump or --nocli options are specified.
SAVEFILE and RCFILE are always optional.

Report bugs to <bybell@rocketmail.com>.
```

这里运行必须使用 MobaXterm 打开 gtkwave 才能正常显示界面

vs-code里面缺少图形界面

使用命令 `gtkwave`即可打开

### 6.6.3. 使用 verdi 查看波形

在 `eda01`里面进行配置

```bash
module avail  # 查看可以使用的eda工具 
module load **  # 选择vcs, verdi和license，之后就可以使用vcs和verdi进行编译/运行/看波形了# 
e.g. module load synopsys/vcs/Q-2020.03-SP2 synopsys/verdi/R-2020.12-SP1 license
# 同样可以在.bashrc中设置别名：
alias modulevcs="module load synopsys/vcs/Q-2020.03-SP2 synopsys/verdi/R-2020.12-SP1 license"
```

之后运行终端会输出

```bash
yourhome@eda01:~/xs-env/XiangShan$ Verdi -ssf ./simv.fsdb
logDir = /nfs/home/yourhome/xs-env/XiangShan/VerdiLog


                                   Verdi (R)

               Version R-2020.12-SP1 for linux64 - Mar 02, 2021

                    Copyright (c) 1999 - 2021 Synopsys, Inc.
   This software and the associated documentation are proprietary to Synopsys,
 Inc. This software may only be used in accordance with the terms and conditions
 of a written license agreement with Synopsys, Inc. All other use, reproduction,
            or distribution of this software is strictly prohibited.
rcfile = /nfs/home/yourhome/xs-env/XiangShan/novas.rc
guiConfFile (read)= /nfs/home/yourhome/xs-env/XiangShan/novas.conf (working directory)
guiConfFile (write)= /nfs/home/yourhome/xs-env/XiangShan/novas.conf (working directory)
```

成功显示波形界面：

![1768548114223-5f126a7d-97ff-4fd3-b8d9-46e36b1e418e.png](img/chapter-6-the-xiang-shan-simulation-process/figure-003-bashrc-waveform-bash.png)

### 6.6.4 关联波形和代码的命令

```bash
verdi -ssf simv.fsdb -dbdir ./build/simv.daidir  
```

## 6.7 总结与展望

如果你能回答下面问题，说明你真的理解了：

* 为什么CPU必须先仿真？
* 仿真流程有哪些角色？
* 为什么需要参考模型？
* 仿真系统真正比较的是什么？
* 程序在系统中扮演什么角色？
* 你能分清 **DUT**（香山）和 **REF**（NEMU）的关系吗？
* 看到 `nemu: HIT GOOD TRAP` 意味着什么？（提示：演完了，没出错）
* 为什么仿真命令里需要 `--diff` 参数？（提示：要对答案）
* 为什么 `make emu` 这么慢？（提示：要把硬件逻辑“渲染”成 C++ 程序）

# 学习路径规划 改为图

### 第一阶段：基础概念理解（1周）

**目标：** 理解香山核仿真的基本流程和概念\
**任务：**

1. 阅读本章1.2节，理解香山核仿真的完整流程
2. 学习Chisel、SystemVerilog、Verilator的基本概念
3. 掌握make命令的基本用法

### 第二阶段：环境搭建与基础编译（2周）

**目标：** 成功搭建香山仿真环境并完成基础编译\
**任务：**

1. 按照1.3.1节的步骤克隆和设置香山环境
2. 尝试运行`make verilog`生成SystemVerilog代码
3. 编译最小配置的仿真程序`make emu CONFIG=MinimalConfig`

### 第三阶段：仿真运行与调试（3周）

**目标：** 掌握香山仿真的运行和调试技巧\
**任务：**

1. 运行Hello World程序验证仿真环境
2. 学习使用波形调试工具（gtkwave等）
3. 掌握difftest的基本使用方法
4. 尝试不同的配置参数进行仿真

### 第四阶段：高级仿真技术（4周）

**目标：** 掌握高级仿真技术和性能分析\
**任务：**

1. 学习使用VCS进行专业级仿真
2. 掌握性能计数器的使用方法
3. 分析仿真性能数据
4. 优化仿真配置和参数


> 更新: 2026-04-24 01:48:05  
