## 学习Diplomacy的项目
### 方案1：单数据流通路 
**路径**: DCache → IOPMP (bypass, APB悬空) → Memory

**描述**:
- 简单的单向数据通路，适用于基础的内存访问场景
- IOPMP 配置为旁路模式，APB 配置接口悬空
- 实现内存访问的基本保护与验证

**对应目录**: `./IopmpSystem`
- 核心模块: `Iopmp.scala`, `IopmpChecker.scala`, `IopmpBridge.scala`
- 特点: 实现 IOPMP 的基本功能，包括多种检查器实现

> 对于目录`./ChiselIOPMP` 
> 实现的是一个DMA → IOPMP → Memory


### 方案2：复杂总线系统（最复杂）
**路径1**: AXI4 Master (64bit) → XBar → DMAC (CFG-64bit)
**路径2**: AXI4 Master (64bit) → XBar → APB Master (32bit) → IOPMP (CFG-32bit)
**路径3**: DMAC (256bit) → IOPMP (Data-256bit) → Memory (64bit)

**描述**:
- 复杂的 2-to-1 XBar 系统，带位宽转换和协议转换
- 使用 TileLink XBar (tlxbar) 实现总线互联
- 支持 AXI4 到 APB 的协议转换
- 支持 256bit 到 64bit 的位宽转换
- 实现复杂的配置和数据通路分离

**对应目录**: `暂未实现`
- 核心模块: `IopmpSystem.scala`, `Dcache.scala`, `Memory.scala`
- 特点: 系统级集成，包含完整的 DCache 和 Memory 子系统

### 方案3：2-to-1 XBar 方向通路
**路径1**: DMAC → XBar → Memory
**路径2**: DCache → XBar → Memory

**描述**:
- 简化的 2-to-1 XBar 系统
- 两个主设备(DMAC 和 DCache)共享一个从设备(Memory)
- 实现基本的总线仲裁和地址解码
- 适用于中等复杂度的片上系统

**对应目录**: `TwoToOneXbarSystem/`
- 核心模块: `mytop.scala`, `AXI4DCache.scala`, `AXI4DMAC.scala`, `AXI4Memory.scala`
- 特点: 顶层核心是 `TwoToOneXbarSystem`，实现两个 AXI4 Master 共享一个 Memory Slave 的 2-to-1 XBar 系统

## 学习乱序处理器微架构的项目

### miniXiangShan

miniXiangShan 是使用 Scala/Chisel 实现、用于学习香山风格乱序微架构的处理器项目，
覆盖取指、译码、寄存器重命名、分派、发射、执行、访存、提交和重定向。
仿真、FPGA 和性能实验统一使用 RV64IM，支持 Zicsr/Zifencei；
性能实验与 NutShell 乱序核运行同一份 RV64 机器码。

**主要模块**:

- 前端：BTB/PHT 分支预测、取指流水线与指令缓存。
- 后端：寄存器重命名、发射队列、整数与乘除执行单元、Load/Store 队列、ROB 和重定向恢复。
- 存储系统：分离的 L1 I/D Cache、统一 L2 Cache，以及 AXI3 对外接口；性能平台适配为共享 AXI4 环境。
- 特权与地址翻译：64 位 CSR、M/S/U 特权模式，以及 Bare/Sv39 MMU、TLB 和三级硬件页表遍历器。
  支持 4 KiB、2 MiB 和 1 GiB 页，A/D 位由软件维护；尚未实现 PMP/PMA 检查。
- 验证：仿真配置提供 Difftest 接口；仓库包含 MMU、访存、唤醒、取指等模块的功能回归。

**获取与运行**:

对应目录为 `miniXiangShan/`，通过 Git 子模块引用独立仓库
`XiangShanLab/miniXiangShan`，由父仓库固定使用的提交版本。
在 XiangShanLab 根目录初始化：

```bash
git submodule update --init --recursive -- xiangshan-programming-practice/miniXiangShan
cd xiangshan-programming-practice/miniXiangShan
```

| 命令 | 用途 |
| --- | --- |
| `make sim` | 生成 RV64 仿真 RTL，启用 Difftest 接口 |
| `make fpga` | 生成 RV64 FPGA RTL，AXI3 地址与数据均为 64 位 |
| `make test` | 运行全部 RV64 模块回归 |
| `make test-mmu` | 运行 MMU 功能与流水线回归 |
| `make test-pipeline` | 运行 MMU、访存、唤醒、取指和立即数生成回归 |
| `make perf` | 获取固定版本依赖，构建并运行 miniXiangShan 与 NutShell 的 CoreMark 对比 |

**性能对比**:

`make perf` 使用同一份 RV64 CoreMark 镜像和同一个 AXI4 仿真平台，
校验 host 参考程序与两个 RTL 运行的 CRC，再比较周期数、退休指令数和 IPC。
NutShell 与 CoreMark 的源码按锁定版本下载到 `.deps/`，无需手动放入仓库。

子模块 README 中记录的 CoreMark 2K、10 次迭代结果为：
miniXiangShan 使用 3,255,350 周期，NutShell OOO 使用 3,406,098 周期，
miniXiangShan 少用约 4.43% 周期，双方 CRC 校验通过。
该结果仅比较同频条件下的周期数，未测量综合频率；实际运行结果以 `perf/results/` 为准。

环境要求与项目结构见 [miniXiangShan README](./miniXiangShan/README.md)，
性能命令细节见 [性能实验说明](./miniXiangShan/perf/README.md)，
比较口径与适用范围见 [性能测试方法](./miniXiangShan/perf/METHODOLOGY.md)。
