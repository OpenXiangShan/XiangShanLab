<!-- # 第十五章 Diplomacy 实战演练二 -->
# Chapter 15: Diplomacy Practical Exercise 2

<!-- ## 案例二：双主设备通过Xbar共享内存 -->
## Case 2: Two Masters Sharing Memory Through an Xbar

<!-- ### 2.1 题目要求 -->
### 2.1 Requirements

<!-- 构建一个双主设备共享内存系统：`dmac -> xbar -> memory`和 `dcache -> xbar -> memory` -->
Build a two-master shared-memory system: `dmac -> xbar -> memory` and `dcache -> xbar -> memory`.

<!-- **系统要求**： -->
**System requirements**:

<!-- * 两个主设备：DMA控制器（dmac）和数据缓存（dcache） -->
* Two masters: a DMA controller (`dmac`) and a data cache (`dcache`)
<!-- * 一个从设备：内存（memory） -->
* One slave: memory (`memory`)
<!-- * 使用AXI4交叉开关（Xbar）实现多对一连接 -->
* Use an AXI4 crossbar (Xbar) for a many-to-one connection
<!-- * 实现完整的仲裁和数据通路 -->
* Implement complete arbitration and data paths

<!-- ### 2.2 系统架构设计 -->
### 2.2 System Architecture

<!-- 基于我们提供的代码，系统架构如下： -->
Based on the provided code, the system architecture is as follows:

<!-- ![画板](img/chapter-14-diplomacy-practical-exercise-1/figure-001-system-architecture.jpeg) -->
![Diagram](img/chapter-14-diplomacy-practical-exercise-1/figure-001-system-architecture.jpeg)

<!-- **代码中出现的模块及其功能**： -->
**Modules appearing in the code and their functions**:

<!-- 1. **AXI4 DMAC**：DMA控制器，支持内存数据传输 -->
1. **AXI4 DMAC**: DMA controller supporting memory data transfers
<!-- 2. **AXI4 DCache**：CPU数据缓存，发起内存访问 -->
2. **AXI4 DCache**: CPU data cache that initiates memory accesses
<!-- 3. **AXI4 Xbar**：交叉开关，仲裁多主设备访问 -->
3. **AXI4 Xbar**: crossbar that arbitrates accesses from multiple masters
<!-- 4. **AXI4 Memory**：共享内存设备 -->
4. **AXI4 Memory**: shared memory device
<!-- 5. **AXI4 DummyMaster**：DMAC配置接口的主设备端，由于DMAC的设计特性，其必须得接上这样一个空接口才能正常运行生成Verilog -->
5. **AXI4 DummyMaster**: master side of the DMAC configuration interface. Because of the DMAC design, this dummy interface must be connected for the design to run and generate Verilog.

<!-- ### 2.3 代码深度解析 -->
### 2.3 In-Depth Code Analysis

<!-- #### 2.3.1 顶层系统设计（TwoToOneXbarSystem） -->
#### 2.3.1 Top-Level System Design (`TwoToOneXbarSystem`)

<!-- 这是系统的核心集成模块，完整展示了Diplomacy的多模块互联，也是本案例最核心的部分： -->
This is the system's core integration module. It shows Diplomacy's multi-module interconnection and is the central part of this case:

```scala
class TwoToOneXbarSystem(implicit p: Parameters) extends LazyModule {
  // 1. 实例化DMAC模块
  // 1. Instantiate the DMAC module.
  val dmac = LazyModule(new AXI4DMAC(Seq(AddressSet(0x40000000L, 0xfff))))

  // 2. 为DMAC创建虚拟主设备用于配置接口
  // 2. Create a virtual master for the DMAC configuration interface.
  val dummyMaster = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "dummy",
      id = IdRange(0, 1)
    ))
  )))
  dmac.node := dummyMaster  // 连接虚拟主设备到DMAC
  // Connect the virtual master to the DMAC.

  // 3. 实例化DCache模块
  // 3. Instantiate the DCache module.
  val dcache = LazyModule(new AXI4DCache(AXI4MasterParameters(
    name = "dcache_master",
    id = IdRange(0, 256),
    aligned = true
  )))

  // 4. 实例化Memory模块
  // 4. Instantiate the Memory module.
  val memory = LazyModule(new AXI4Memory(
    address = Seq(AddressSet(0x80000000L, 0x0fffffffL)),
    size = 0x10000000L,
    executable = true,
    beatBytes = 8
  ))

  // 5. 创建AXI4交叉开关
  // 5. Create the AXI4 crossbar.
  val xbar = AXI4Xbar()

  // 6. Diplomacy连接：多主设备 -> Xbar -> 内存
  // 6. Diplomacy connections: multiple masters -> Xbar -> memory.
  xbar := dmac.masterNode  // DMAC主端口连接到Xbar
  // Connect the DMAC master port to the Xbar.
  xbar := dcache.node      // DCache连接到Xbar
  // Connect the DCache to the Xbar.
  memory.node := xbar      // Xbar输出连接到内存
  // Connect the Xbar output to memory.

  lazy val module = new TwoToOneXbarSystemModule(this)
}
```

<!-- **Diplomacy核心要点**： -->
**Key Diplomacy points**:

<!-- 1. **模块化设计**：每个功能模块独立实例化为LazyModule -->
1. **Modular design**: instantiate each functional block independently as a `LazyModule`
<!-- 2. **节点明确分工**： -->
2. **Clear node roles**:
   <!-- * `dmac.node`：从设备节点，接收配置请求 -->
   * `dmac.node`: slave node that receives configuration requests
   <!-- * `dmac.masterNode`：主设备节点，发起DMA传输，连接到 -->
   * `dmac.masterNode`: master node that initiates DMA transfers and connects to the Xbar
   <!-- * `dcache.node`：主设备节点，发起缓存访问 -->
   * `dcache.node`: master node that initiates cache accesses
   <!-- * `memory.node`：从设备节点，接收内存请求 -->
   * `memory.node`: slave node that receives memory requests
<!-- 3. **拓扑清晰**：使用`:=`操作符建立清晰的连接关系 -->
3. **Clear topology**: use the `:=` operator to establish explicit connections

<!-- #### 3.3.2 AXI4交叉开关（AXI4Xbar） -->
#### 2.3.2 AXI4 Crossbar (`AXI4Xbar`)

<!-- Diplomacy框架提供的`AXI4Xbar`是系统关键组件，自动处理： -->
The `AXI4Xbar` provided by Diplomacy is a key system component that automatically handles:

<!-- 1. **地址解码**：根据地址空间路由请求 -->
1. **Address decoding**: route requests according to the address space
<!-- 2. **仲裁逻辑**：多主设备竞争时的优先级处理 -->
2. **Arbitration logic**: handle priorities when masters compete
<!-- 3. **ID管理**：保持事务ID的唯一性 -->
3. **ID management**: preserve transaction-ID uniqueness
<!-- 4. **数据通路**：正确路由读写数据 -->
4. **Data paths**: route read and write data correctly

<!-- #### 2.3.3 DMAC模块的双角色设计 -->
#### 2.3.3 DMAC's Dual-Role Design

<!-- AXI4DMAC模块展示了复杂模块的Diplomacy设计： -->
The `AXI4DMAC` module demonstrates Diplomacy's design for a complex module:

```scala
class AXI4DMAC(address: Seq[AddressSet])(implicit p: Parameters) 
extends AXI4SlaveModule(address, executable = false) {

  // 从设备节点：接收配置请求
  // Slave node: receive configuration requests.
  // 继承自AXI4SlaveModule，已包含node
  // Inherits from AXI4SlaveModule, which already provides node.

  // 主设备节点：发起DMA传输
  // Master node: initiate DMA transfers.
  val masterNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "dmac_master",
      id = IdRange(0, 1 << 14),  // 支持最多16384个ID
      // Supports up to 16,384 IDs.
      aligned = true
    ))
  )))
}
```

<!-- **关键设计**： -->
**Key design points**:

<!-- 1. **双节点设计**：同时包含主从节点，支持配置和传输 -->
1. **Dual-node design**: includes both master and slave nodes for configuration and transfers
<!-- 2. **ID空间管理**：为主设备分配充足的ID范围 -->
2. **ID-space management**: allocate a sufficiently large ID range to the master
<!-- 3. **模块继承**：复用AXI4SlaveModule的基础功能 -->
3. **Module inheritance**: reuse the basic functionality of `AXI4SlaveModule`

<!-- #### 2.3.4 硬件实现模块 -->
#### 2.3.4 Hardware Implementation Module

<!-- 硬件逻辑在`TwoToOneXbarSystemModule`中实现： -->
The hardware logic is implemented in `TwoToOneXbarSystemModule`:

```scala
class TwoToOneXbarSystemModule(outer: TwoToOneXbarSystem) 
extends LazyModuleImp(outer) {

  val io = IO(new Bundle {
    // DMAC配置接口
    // DMAC configuration interface.
    val dma_cfg_wen = Input(Bool())
    val dma_cfg_addr = Input(UInt(12.W))
    val dma_cfg_wdata = Input(UInt(64.W))
    val dma_cfg_ren = Input(Bool())
    val dma_cfg_rdata = Output(UInt(64.W))
    val dma_start = Input(Bool())
    val dma_src_addr = Input(UInt(64.W))
    val dma_dst_addr = Input(UInt(64.W))

    // 系统控制
    // System control.
    val system_reset = Input(Bool())

    // 调试信号
    // Debug signals.
    val dma_status_busy = Output(Bool())
    val dma_status_done = Output(Bool())
    val cycle_counter = Output(UInt(32.W))
  })

  // 获取虚拟主设备接口
  // Obtain the virtual-master interface.
  val (dummy_bundle, _) = outer.dummyMaster.out.head

  // 配置虚拟主设备不发起实际请求
  // Configure the virtual master not to issue real requests.
  dummy_bundle.aw.valid := false.B
  dummy_bundle.w.valid := false.B
  dummy_bundle.ar.valid := false.B
  dummy_bundle.r.ready := true.B
  dummy_bundle.b.ready := true.B

  // 其他硬件逻辑...
  // Other hardware logic...
}
```

<!-- ### 2.4 Diplomacy连接模式详解 -->
### 2.4 Diplomacy Connection Patterns in Detail

<!-- #### 2.4.1 星型连接拓扑 -->
#### 2.4.1 Star Connection Topology

<!-- 本案例采用典型的星型连接： -->
This case uses a typical star connection:

<!--
```plain
主设备1 -----
                |
    主设备2 -----+---- Xbar ---- 从设备
                |
    主设备n -----
```
-->
```plain
Master 1 ------
                |
    Master 2 ---+---- Xbar ---- Slave
                |
    Master N ---
```

<!-- 在Diplomacy中表示为： -->
In Diplomacy, it is expressed as:

```scala
xbar := master1.node
xbar := master2.node
// ... 更多主设备
// ... more masters
slave.node := xbar
```

<!-- #### 2.4.2 连接方向语义 -->
#### 2.4.2 Connection-Direction Semantics

<!-- Diplomacy的连接操作符`:=`有明确的流向语义： -->
Diplomacy's `:=` connection operator has explicit flow semantics:

```scala
// 语法：下游 := 上游
// Syntax: downstream := upstream.
// 语义：数据从上游流向下游
// Semantics: data flows from upstream to downstream.

memory.node := xbar      // 数据：Xbar -> Memory
// Data: Xbar -> Memory.
xbar := dmac.masterNode  // 数据：DMAC -> Xbar
// Data: DMAC -> Xbar.
xbar := dcache.node      // 数据：DCache -> Xbar
// Data: DCache -> Xbar.
```

<!-- #### 2.4.3 参数自动协商 -->
#### 2.4.3 Automatic Parameter Negotiation

<!-- 当建立连接时，Diplomacy自动执行参数协商： -->
When connections are established, Diplomacy automatically negotiates parameters:

<!-- 1. **位宽对齐**：确保所有连接的位宽一致 -->
1. **Width alignment**: ensure that all connected widths match
<!-- 2. **ID空间分配**：协调各主设备的ID范围 -->
2. **ID-space allocation**: coordinate the ID ranges of all masters
<!-- 3. **地址映射**：验证地址空间不冲突 -->
3. **Address mapping**: verify that address spaces do not conflict
<!-- 4. **协议特性**：协商支持的传输类型和突发长度 -->
4. **Protocol features**: negotiate supported transfer types and burst lengths

<!-- ### 2.5 模块实现细节 -->
### 2.5 Module Implementation Details

<!-- #### 2.5.1 DCache模块简化实现 -->
#### 2.5.1 Simplified DCache Implementation

<!-- DCache实现展示了一个最小化的AXI4主设备： -->
The DCache implementation demonstrates a minimal AXI4 master:

```scala
class AXI4DCache(params: AXI4MasterParameters)(implicit p: Parameters) 
extends LazyModule {

  val node = AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(params))))

  override lazy val module = new LazyModuleImp(this) {
    val (axi_bundle, _) = node.out.head

    // 简化实现：不发起实际请求，仅占位
    // Simplified implementation: issue no real requests; this is a placeholder.
    axi_bundle.ar.valid := false.B
    axi_bundle.r.ready := true.B
    axi_bundle.aw.valid := false.B
    axi_bundle.w.valid := false.B
    axi_bundle.b.ready := true.B
  }
}
```

<!-- **注意**：这是一个最小化实现，实际DCache会有复杂的缓存逻辑。 -->
**Note**: this is a minimal implementation; a real DCache contains complex cache logic.

<!-- #### 2.5.2 Memory模块实现 -->
#### 2.5.2 Memory Module Implementation

<!-- Memory模块的关键特性包括： -->
Key features of the Memory module include:

<!-- 1. **同步存储器**：使用SyncReadMem存储数据 -->
1. **Synchronous memory**: store data with `SyncReadMem`
<!-- 2. **地址映射**：支持特定的地址空间 -->
2. **Address mapping**: support a specific address space
<!-- 3. **突发传输**：支持AXI4突发读写 -->
3. **Burst transfers**: support AXI4 burst reads and writes
<!-- 4. **立即响应**：简化设计，无复杂延迟 -->
4. **Immediate responses**: simplified design without complex latency

<!-- ### 2.6 Diplomacy优势在本案例的体现 -->
### 2.6 How Diplomacy's Advantages Appear in This Case

<!-- #### 2.6.1 拓扑抽象 -->
#### 2.6.1 Topology Abstraction

<!-- Diplomacy将复杂的物理连接抽象为逻辑连接： -->
Diplomacy abstracts complex physical connections as logical connections:

```scala
// 逻辑描述
// Logical description.
xbar := dmac.masterNode
xbar := dcache.node
memory.node := xbar

// 物理实现由Diplomacy自动生成，包括：
// Physical implementation generated automatically by Diplomacy, including:
// 1. 仲裁逻辑
// 1. Arbitration logic.
// 2. 地址解码
// 2. Address decoding.
// 3. 数据多路复用
// 3. Data multiplexing.
// 4. 响应路由
// 4. Response routing.
```

<!-- #### 2.6.2 参数化设计 -->
#### 2.6.2 Parameterized Design

<!-- 系统高度可配置： -->
The system is highly configurable:

```scala
// 可配置参数
// Configurable parameters.
val dcache = LazyModule(new AXI4DCache(AXI4MasterParameters(
  name = "dcache_master",
  id = IdRange(0, 256),  // 可配置的ID范围
  // Configurable ID range.
  aligned = true
)))

val memory = LazyModule(new AXI4Memory(
  address = Seq(AddressSet(0x80000000L, 0x0fffffffL)),  // 可配置地址
  // Configurable address.
  size = 0x10000000L,  // 可配置大小
  // Configurable size.
  beatBytes = 8  // 可配置位宽
  // Configurable beat width.
)))
```

<!-- #### 2.6.3 自动错误检查 -->
#### 2.6.3 Automatic Error Checking

<!-- Diplomacy在编译时检查常见错误： -->
Diplomacy checks common errors at compile time:

<!-- 1. **地址冲突**：多个从设备地址重叠 -->
1. **Address conflicts**: address spaces of multiple slaves overlap
<!-- 2. **位宽不匹配**：连接设备位宽不一致 -->
2. **Width mismatch**: connected devices have different widths
<!-- 3. **协议不兼容**：设备间协议特性不匹配 -->
3. **Protocol incompatibility**: protocol features do not match between devices
<!-- 4. **连接错误**：主从角色颠倒 -->
4. **Connection errors**: master and slave roles are reversed

<!-- ### 2.7 测试与验证建议 -->
### 2.7 Testing and Verification Suggestions

<!-- #### 2.7.1 验证策略 -->
#### 2.7.1 Verification Strategy

<!-- 1. **单元测试**：单独测试每个模块 -->
1. **Unit tests**: test each module independently
<!-- 2. **集成测试**：验证Xbar的正确路由 -->
2. **Integration tests**: verify correct Xbar routing
<!-- 3. **并发测试**：同时发起DMA和Cache访问 -->
3. **Concurrency tests**: issue DMA and cache accesses simultaneously
<!-- 4. **边界测试**：测试地址边界情况 -->
4. **Boundary tests**: test address-boundary cases

<!-- #### 2.7.2 调试支持 -->
#### 2.7.2 Debug Support

<!-- 在硬件模块中添加调试信息： -->
Add debug information to the hardware modules:

```scala
// 在LazyModuleImp中添加调试打印
// Add debug printing in LazyModuleImp.
when(axi_bundle.ar.fire) {
  printf(p"[DCache] AR request: addr=0x${Hexadecimal(axi_bundle.ar.bits.addr)}\n")
}

// 周期计数器用于跟踪进度
// Use a cycle counter to track progress.
val cycle_counter = RegInit(0.U(32.W))
cycle_counter := cycle_counter + 1.U
when(cycle_counter % 1000.U === 0.U) {
  printf(p"[System] Cycle ${cycle_counter}: System running\n")
}
```

<!-- ### 2.8 扩展性设计 -->
### 2.8 Extensibility

<!-- 当前系统可轻松扩展： -->
The current system can be extended easily:

```scala
// 添加第三个主设备
// Add a third master.
val master3 = LazyModule(new AXI4MasterModule(...))
xbar := master3.node  // 只需增加一行连接
// Only one connection line is needed.

// 添加第二个从设备
// Add a second slave.
val peripheral = LazyModule(new AXI4Peripheral(...))
peripheral.node := xbar  // Xbar自动处理地址解码
// The Xbar handles address decoding automatically.
```

<!-- ### 2.9 常见问题与解决方案 -->
### 2.9 Common Problems and Solutions

<!-- #### 问题1：Xbar地址冲突 -->
#### Problem 1: Xbar Address Conflict

<!-- **现象**：多个从设备地址空间重叠 -->
**Symptom**: address spaces of multiple slaves overlap.

<!-- **解决**：明确划分地址空间 -->
**Solution**: define non-overlapping address spaces.

```scala
// 正确：不重叠的地址空间
// Correct: non-overlapping address spaces.
val memory1 = AXI4Memory(AddressSet(0x80000000L, 0x0fffffffL))
val memory2 = AXI4Memory(AddressSet(0x90000000L, 0x0fffffffL))  // 错误：重叠
// Incorrect: overlaps.
```

<!-- #### 问题2：ID空间耗尽 -->
#### Problem 2: ID-Space Exhaustion

<!-- **现象**：主设备ID范围不足 -->
**Symptom**: the master's ID range is insufficient.

<!-- **解决**：合理分配ID范围 -->
**Solution**: allocate ID ranges appropriately.

```scala
// 为不同主设备分配独立的ID范围
// Allocate independent ID ranges to different masters.
val dmac = AXI4MasterParameters(id = IdRange(0, 256))      // ID 0-255
val dcache = AXI4MasterParameters(id = IdRange(256, 512))  // ID 256-511
```

<!-- #### 问题3：性能瓶颈 -->
#### Problem 3: Performance Bottleneck

<!-- **现象**：Xbar成为系统瓶颈 -->
**Symptom**: the Xbar becomes the system bottleneck.

<!-- **解决**： -->
**Solutions**:

<!-- 1. 增加Xbar的数据宽度 -->
1. Increase the Xbar data width.
<!-- 2. 使用多级Xbar结构 -->
2. Use a multilevel Xbar structure.
<!-- 3. 优化仲裁算法 -->
3. Optimize the arbitration algorithm.


<!-- > 更新: 2026-05-25 16:14:35
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/gg2owl9na6mufrgt> -->
> Updated: 2026-05-25 16:14:35
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/gg2owl9na6mufrgt>
