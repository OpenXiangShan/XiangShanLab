# 第七章：GEM5模拟器

# 1 概述

:::info

### 🎯 学习目标

学完本章，你将不再只是一个“脚本运行员”，而能回答：

* **地位**：在香山开发的“全家桶”里，GEM5 到底在哪一层？
* **动机**：既然有了 NEMU，为什么我还要花时间学这么慢的 GEM5？
* **实操**：如何为香山做一次深度的“性能体检”？

:::

## 1.1 什么是GEM5？

GEM5是一款功能强大的**周期级模拟器**，主要用于计算机体系结构的研究和开发。它能够模拟从简单单核处理器到复杂多核系统的各种计算机架构，为芯片设计者提供了一个灵活、可配置的仿真平台。

**图表说明：GEM5架构层次图**

```plain
应用层 ──┐
          │
系统层 ──┤  GEM5模拟器
          │
微架构层─┘
```

*图1.1：GEM5模拟器的三层架构模型*

:::info
\*\*什么叫“周期级”？ \*\*

* **NEMU（指令级）**：只关心结果。比如执行 `add`，它只看 1+1 是不是等于 2。
* **GEM5（周期级）**：关心**过程**。它模拟 CPU 每一个时钟周期的动作：这步 `add` 经过了哪级流水线？有没有因为拿不到数据而“发呆”？

**一句话总结**：周期级模拟就是把 CPU 的每一个“心跳”都画出来。

:::

## 1.2 为什么需要XS-GEM5？

XS-GEM5是基于主线GEM5专门为香山处理器优化的版本。香山作为一款开源RISC-V处理器，需要专门的性能评估工具来：

1. **高效迭代微架构新特性** - 快速验证设计改进
2. **端到端性能评估** - 全面评估系统性能
3. **架构设计验证** - 确保设计符合预期目标

**图表说明：XS-GEM5与主线GEM5关系**

```plain
主线GEM5 ──┬── 通用模拟器
           │
           └── XS-GEM5（香山专用）
```

\_图1.2：XS-GEM5与主线GEM5的关系\
\_

*GEM5 在工具链中的位置*

| **开发阶段** | **目标** | **核心工具** | **精度** | **速度** | **角色类比** |
| --- | --- | --- | --- | --- | --- |
| **第一步** | *验证功能对不对* | **NEMU** | *低 (指令级)* | *极快* | **随身教练***：快速纠错* |
| **第二步** | *验证跑得快不快* | **GEM5** | **高 (周期级)** | **较慢** | **体检中心***：深度分析* |
| **第三步** | *硬件最终实现* | **FPGA/芯片** | *最高* | *实时* | **正式比赛***：真实表现* |

:::info
**XS-GEM5：香山的专属“体检设备”**

**XS-GEM5** 是基于主线 GEM5 为香山深度定制的版本，它的特殊价值在于：

1. **微架构对齐**：它模拟的缓存、分支预测器参数与真实的香山 RTL 几乎一致。
2. **5% 误差线**：它的跑分结果与真实硬件误差极小，具有极高的参考价值。

:::

## 1.3 XS-GEM5的核心特点

* **与香山微架构对齐** - 在GEM5 O3CPU基础上与香山微架构进行适配
* **支持Difftest功能** - 可以与其他参考模型进行指令级对比验证
* **支持Checkpoint功能** - 支持从特定状态恢复执行，便于调试
* **集成性能分析脚本** - 提供针对香山架构的专用性能分析工具

## 1.4 学习路径规划 画图

对于新手，建议按照以下路径学习：

### 1.4.1 第一阶段：基础了解（1-2天）

* 了解GEM5的基本概念和架构
* 理解XS-GEM5与主线GEM5的区别
* 掌握基本的模拟器运行流程

### 1.4.2 第二阶段：环境搭建（2-3天）

* 学会配置开发环境
* 掌握编译和构建XS-GEM5
* 理解环境变量的设置

### 1.4.3 第三阶段：实际运行（3-5天）

* 运行简单的基准测试程序
* 学习查看和分析模拟结果
* 掌握基本的调试技巧

### 1.4.4 第四阶段：高级应用（5-7天）

* 使用Difftest进行验证
* 利用Checkpoint进行调试
* 使用perfCCT进行性能分析

**图表说明：学习路径时间规划**

```plain
基础了解 (1-2天) → 环境搭建 (2-3天) → 实际运行 (3-5天) → 高级应用 (5-7天)
```

*图1.3：XS-GEM5学习路径时间规划*

***

# 2 快速开始

## 2.1 官方文档参考

* [介绍 - 香山GEM5模拟器文档](https://xs-gem5.readthedocs.io/zh-cn/latest/introduction/)
* [快速开始 - 香山GEM5模拟器文档](https://xs-gem5.readthedocs.io/zh-cn/latest/quick_start/#python)

## 2.2 重要文件路径

XS-GEM5中的香山配置文件路径为：

```makefile
/nfs/home/yourhome/GEM5/configs/common/xiangshan.py
```

**图表说明：XS-GEM5文件目录结构**

```plain
GEM5/
├── build/           # 编译输出
├── configs/         # 配置文件
│   └── common/xiangshan.py
├── src/            # 源代码
└── util/           # 工具脚本
```

*图2.1：XS-GEM5主要目录结构*

***

# 3 环境配置与安装

## 3.1 环境变量设置

在开始使用XS-GEM5之前，需要正确设置以下环境变量：

```makefile
export gem5_home=/nfs/home/yourhome/GEM5/

# 设置GEM5需要的环境变量
export GCBV_REF_SO=/nfs/home/yourhome/xs-env/NEMU/ready-to-run/riscv64-nemu-interpreter-so
export GCBV_REF_SO=/nfs/home/share/gem5_ci/ref/normal/riscv64-nemu-interpreter-so
```

## 3.2 常见问题与解决

### 3.2.1 段错误问题

运行以下命令时可能遇到段错误：

```bash
(py38) yourhome@open01:~/GEM5$ ./build/RISCV/gem5.opt ./configs/example/kmhv3.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin
```

**错误信息：**

```plain
gem5 has encountered a segmentation fault!
```

**原因与解决：**\
这通常是因为NEMU文件版本不匹配导致的。需要更换为合适版本的NEMU路径。

**图表说明：段错误排查流程**

```plain
出现段错误 → 检查NEMU版本 → 更新环境变量 → 重新运行
```

*图3.1：段错误排查流程图*

***

# 4 NEMU参考模型生成

## 4.1 NEMU配置文件

香山GEM5需要特定的NEMU参考模型。在编译NEMU之前，必须使用正确的配置文件。以下是可用的配置文件列表：

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

make riscv64-gem5-ref\_defconfig 来生成gem5 需要的ref nemu

## 4.2 编译指令

使用以下命令编译GEM5：

```makefile
# 使用gold-linker链接，可以加快编译速度
scons build/RISCV/gem5.opt --gold-linker -j$(nproc)

# 或者指定具体的线程数
scons build/RISCV/gem5.opt --gold-linker -j8
```

## 4.3 环境配置

在编译前需要设置正确的环境变量：

```makefile
export GCBV_REF_SO=/nfs/home/yourhome/xs-env/NEMU/build/riscv64-nemu-interpreter-so
```

## 4.4 Python环境配置

GEM5需要特定的Python环境，配置方法如下：

```makefile
yourhome@open01:~/GEM5$ conda activate py38
export LD_LIBRARY_PATH=/nfs/home/yourhome/miniconda3/envs/py38/lib:$LD_LIBRARY_PATH

# 如果缺少安装包，使用conda install安装
# 重新构建GEM5
scons build/RISCV/gem5.opt --gold-linker -j8
```

**图表说明：编译流程**

```plain
环境准备 → 配置变量 → 激活Python → 执行编译 → 验证结果
```

*图4.1：XS-GEM5编译流程图*

***

# 5 运行与测试

## 5.1 运行基准测试

使用以下命令运行CoreMark基准测试：

```makefile
# 运行workload，注意输入的是bin文件
./build/RISCV/gem5.opt ./configs/example/kmhv2.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin
```

## 5.2 获取性能指标

运行完成后，可以查看性能指标如IPC（每周期指令数）：

```makefile
# 获取IPC
grep 'cpu.ipc' m5out/stats.txt

# 输出示例
system.cpu.ipc                         0.771001                       # IPC: Instructions Per Cycle ((Count/Cycle))
```

## 5.3 Difftest功能

Difftest是XS-GEM5的重要功能，它可以：

* 比较GEM5模拟器和golden model的指令级差异
* 检查ISA级差异
* 验证内存访问一致性

**图表说明：Difftest验证流程**

```plain
GEM5执行 → 记录状态 → NEMU执行 → 对比状态 → 报告差异
```

*图5.1：Difftest指令级验证流程*

***

# 6 性能分析工具perfCCT

## 6.1 kmhv2.py配置文件解析

:::info
**配置文件分析：./configs/example/kmhv2.py**

1. **配置内容**：定义了香山处理器的仿真配置，包括CPU类型、缓存结构、内存系统等参数
2. **perfcct脚本作用**：收集和分析架构级别的性能数据，生成可视化报告
3. **执行结果**：生成包含详细性能数据的数据库文件
4. **结果信息**：包含指令执行轨迹、缓存命中率、分支预测准确率等关键性能指标

:::

## 6.2 perfCCT使用示例

```makefile
# 运行GEM5并启用架构数据库
(py38) yourhome@open01:~/GEM5$ ./build/RISCV/gem5.opt ./configs/example/kmhv2.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin --enable-arch-db --arch-db-file=m5out/test.db

# 输出信息示例
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

# 使用perfcct分析结果
(py38) yourhome@open01:~/GEM5$ python3 util/perfcct.py m5out/test.db --zoom 1.5 -p 333 --visual | less
```

## 6.3 完整工作流程

以下是使用XS-GEM5进行性能分析的完整工作流程：

```makefile
# 1. 激活Python环境
conda activate py38

# 2. 编译GEM5
scons build/RISCV/gem5.opt --gold-linker -j64

# 3. 运行测试并收集数据
./build/RISCV/gem5.opt ./configs/example/kmhv2.py --raw-cpt --generic-rv-cpt=../xs-env/NEMU/ready-to-run/coremark-2-iteration.bin --enable-arch-db --arch-db-file=m5out/test.db

# 4. 查看基础性能指标
grep 'cpu.ipc' m5out/stats.txt

# 5. 使用perfcct进行详细分析
python3 util/perfcct.py m5out/test.db --zoom 1.5 -p 333 --visual | less

# 6. 退出环境
conda deactivate
```

**图表说明：perfCCT分析流程图**

```plain
运行测试 → 生成数据库 → 基础分析 → 详细分析 → 结果可视化
```

*图6.1：perfCCT性能分析完整流程*

***

# 总结

本文档详细介绍了XS-GEM5模拟器的使用方法和学习路径。通过本指南，新手可以：

1. 理解GEM5模拟器的基本概念和架构
2. 掌握XS-GEM5的环境配置和安装方法
3. 学会运行基准测试和分析性能结果
4. 使用高级功能如Difftest和perfCCT进行深入分析


> 更新: 2026-04-24 01:46:40  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/fglpd87ocb4xldu1>
