## 学习Diplomacy的项目
### 方案1：单数据流通路 
**路径**: DCache → IOPMP (bypass, APB悬空) → Memory

**描述**:
- 简单的单向数据通路，适用于基础的内存访问场景
- IOPMP 配置为旁路模式，APB 配置接口悬空
- 实现内存访问的基本保护与验证

**对应目录**: `./ChiselIOPMP_1` 
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

**对应目录**: `ChiselMyTop/`
- 核心模块: `mytop.scala`, `AXI4DCache.scala`, `AXI4DMAC.scala`, `AXI4Memory.scala`
- 特点: 自定义顶层设计，灵活配置各模块接口