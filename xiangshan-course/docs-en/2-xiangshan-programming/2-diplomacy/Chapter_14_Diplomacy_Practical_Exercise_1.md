<!-- # 第十四章 Diplomacy 实战演练一 -->
# Chapter 14: Diplomacy Practical Exercise 1

<!-- 参考： -->
References:

<https://zhuanlan.zhihu.com/p/659308008>

<https://zhuanlan.zhihu.com/p/633327505>

<!-- # 题目 -->
# Assignment

<!-- TODO: 需要增加 这几个项目的代码解析 和 开源源码 -->
TODO: add code analysis and open-source source references for these projects.

<!-- 围绕iopmp、dcache、memory、dmac选3个方向出题。（参考Xiangshan SimMMIO.scala） -->
Choose three exercise directions around IOPMP, DCache, memory, and DMAC (with XiangShan's `SimMMIO.scala` as a reference).

<!-- 1是单数据流通路 -->
1 is a single data path.

<!-- 2是2to1xbar，且带位宽转换和协议转换（最难），需要用tlxbar -->
2 is a 2-to-1 Xbar with width and protocol conversion (the most difficult case), requiring `tlxbar`.

<!-- 3是2to1 xbar方向通路 -->
3 is a 2-to-1 Xbar data path.

<!-- 1. dcache->iopmp（bypass,apb悬空）->memory -->
1. `dcache -> iopmp` (bypass, APB unconnected) `-> memory`
<!-- 2. axi\_master(64bit)->xbar->dmac（cfg-64bit） -->
2. `axi_master` (64-bit) `-> xbar -> dmac` (64-bit configuration)

<!-- axi\_master(64bit)->xbar->apb\_master(32bit)->iopmp(cfg-32bit) -->
`axi_master` (64-bit) `-> xbar -> apb_master` (32-bit) `-> iopmp` (32-bit configuration)

<!-- dmac(256bit)->iopmp(data-256bit)->memory(64bit) -->
`dmac` (256-bit) `-> iopmp` (256-bit data) `-> memory` (64-bit)

<!-- 3. dmac->xbar->memory -->
3. `dmac -> xbar -> memory`

<!-- dcache->xbar->memory -->
`dcache -> xbar -> memory`

<!-- # Diplomacy 编程实践 -->
# Diplomacy Programming Practice

<!-- ## 前言 -->
## Preface

:::danger
<!-- 🎯 **欢迎学习！你的 SoC 硬件“积木”搭建指南** -->
🎯 **Welcome! Your guide to building SoC hardware with “building blocks”**

<!-- 如果你第一次接触 **Diplomacy 框架**，看到“节点”、“连接”这些术语可能会觉得有些复杂。别担心！ -->
If this is your first encounter with the **Diplomacy framework**, terms such as “nodes” and “connections” may seem complicated. Do not worry!

<!-- 本教程的目标是，带领你像搭积木一样，用 **Diplomacy** 轻松构建可工作的处理器子系统。你将能掌握： -->
This tutorial guides you in using **Diplomacy** to build a working processor subsystem as if you were assembling building blocks. You will learn to:

<!-- 1. **理解抽象**：明白 Diplomacy 如何用“声明式连接”替代繁琐的连线。 -->
1. **Understand the abstraction**: see how Diplomacy replaces tedious wiring with “declarative connections.”
<!-- 2. **动手实践**：亲手搭建“单数据流”与“多主共享”两个经典系统。 -->
2. **Practice hands-on**: build two classic systems, a “single data path” and a “multi-master shared path.”
<!-- 3. **学会调试**：掌握查看波形、验证数据通路的核心方法。 -->
3. **Learn debugging**: master the core methods for viewing waveforms and validating data paths.
<!-- 4. **建立信心**：获得构建更复杂系统（如多级互联、带权限检查）的基石。 -->
4. **Build confidence**: gain the foundation for constructing more complex systems, such as multilevel interconnects and permission checking.

<!-- 让我们一起，从一行连接代码开始，揭开高性能 SoC 互联设计的神秘面纱。 -->
Starting with a single connection line, let us uncover the design of high-performance SoC interconnects.

:::

<!-- **案例演进关系**： -->
**Case progression**:

<!-- * **案例一**是**单数据流通路**，就像一条没有岔路的单车道路。它帮助你理解最基础的“模块-节点-连接”概念和 AXI4 协议流。 -->
* **Case 1** is a **single data path**, like a one-lane road without branches. It introduces the basic “module-node-connection” concepts and AXI4 protocol flow.
<!-- * **案例二**是**多主设备共享通路**，引入了 **Xbar（交叉开关）** 这个“交通枢纽”，就像一座立交桥，让多条车道（主设备）有序地驶向同一个目的地（内存）。这是在案例一基础上，学习如何处理并发和仲裁。 -->
* **Case 2** is a **multi-master shared path**. It introduces the **Xbar (crossbar)** as a “traffic hub,” like an interchange that directs multiple lanes (masters) to one destination (memory). It builds on Case 1 to teach concurrency and arbitration.

:::info
<!-- **新手建议**：如果你是 Diplomacy 完全的新手，**强烈建议从案例一开始**。案例一中的每一个概念都是案例二的基础。当你理解了案例一中“`:=`连接符是什么意思”、“数据到底怎么流”之后，再看案例二的 Xbar 就会豁然开朗。 -->
**Advice for beginners**: if you are completely new to Diplomacy, **start with Case 1**. Every concept in Case 1 is a foundation for Case 2. Once you understand what the `:=` connector means and how data flows in Case 1, the Xbar in Case 2 will be much easier to understand.

:::

<!-- # 案例一：单数据流通路 -->
# Case 1: Single Data Path

<!-- ## 1.1 题目要求 -->
## 1.1 Requirements

<!-- 让我们从最简单的“单车道”开始。这个案例的目标是构建一条从 Dcache 发起，经过 IOPMP，最终到达 Memory 的**点对点数据通路**：`dcache -> iopmp（bypass,apb悬空） -> memory` -->
Let us start with the simplest “single lane.” The goal is to build a **point-to-point data path** from DCache, through IOPMP, to Memory: `dcache -> iopmp (bypass, APB unconnected) -> memory`.

<!-- **系统要求**： -->
**System requirements**:

<!-- * 一个主设备：数据缓存（dcache），**发起者**。像 CPU 的“手”，主动向内存发起读写请求。 -->
* One master: the data cache (`dcache`), the **initiator**. Like the CPU’s hand, it actively issues reads and writes to memory.
<!-- * 一个从设备：内存（memory），**响应者**。像仓库，接收请求，存入或取出数据。 -->
* One slave: memory (`memory`), the **responder**. Like a warehouse, it receives requests and stores or retrieves data.
<!-- * 一个桥接设备：IOPMP（I/O内存保护单元），**安全检查站/直通通道**。在案例一中设为“直通”(bypass)，不检查，只转发。 -->
* One bridge: IOPMP (I/O memory protection unit), a **security checkpoint/pass-through**. In Case 1 it is set to bypass mode, so it performs no checks and only forwards requests.
<!-- * 使用AXI4总线协议 -->
* Use the AXI4 bus protocol.
<!-- * IOPMP的APB配置接口悬空（不连接） -->
* Leave the IOPMP APB configuration interface unconnected.
<!-- * 实现完整的读写通路 -->
* Implement complete read and write paths.

<!-- ## 1.2 系统架构设计 -->
## 1.2 System Architecture

<!-- 基于我们提供的代码，系统架构如下： -->
Based on the provided code, the system architecture is as follows:

<!-- ![画板](img/chapter-14-diplomacy-practical-exercise-1/figure-001-chapter-14-diplomacy-practical-exercise-1.jpeg) -->
![Diagram](img/chapter-14-diplomacy-practical-exercise-1/figure-001-chapter-14-diplomacy-practical-exercise-1.jpeg)

<!-- **模块功能**： -->
**Module functions**:

<!-- 1. **AXI4 Dcache**：数据缓存模拟器，作为AXI4主设备发起读写请求 -->
1. **AXI4 DCache**: data-cache model that issues read and write requests as an AXI4 master
<!-- 2. **AXI4 IOPMP**：I/O内存保护单元，在bypass模式下透明转发请求 -->
2. **AXI4 IOPMP**: I/O memory protection unit that transparently forwards requests in bypass mode
<!-- 3. **AXI4 Memory**：内存模拟器，作为AXI4从设备响应请求 -->
3. **AXI4 Memory**: memory model that responds to requests as an AXI4 slave
<!-- 4. **外部控制接口**：用于测试平台控制Dcache发起请求 -->
4. **External control interface**: lets the testbench control requests issued by DCache
<!-- 5. **APB配置接口**：IOPMP的配置总线，本案例中悬空 -->
5. **APB configuration interface**: the IOPMP configuration bus, left unconnected in this case

<!-- ## 1.3 代码深度解析 -->
## 1.3 In-Depth Code Analysis

<!-- ### 1.3.1 顶层系统设计（IopmpSystemLazy） -->
### 1.3.1 Top-Level System Design (`IopmpSystemLazy`)

<!-- 这是系统的核心集成模块，完整展示了Diplomacy的单数据流通路连接，也是案例一最核心部分的代码： -->
This is the system’s core integration module. It fully demonstrates Diplomacy’s single-data-path connection and is the central code in Case 1:

```scala
// 文件：IopmpSystemLazy.scala
// File: IopmpSystemLazy.scala
class IopmpSystemLazy(
  numBridge: Int = 1,
  memDepth: Int = 1024
)(implicit p: Parameters) extends LazyModule {

  // 1. 实例化“积木块”Dcache模块
  // 1. Instantiate the DCache building block.
  val dcache = LazyModule(new DcacheLazy())

  // 2. 实例化“积木块”IOPMP模块
  // 2. Instantiate the IOPMP building block.
  val iopmp = LazyModule(new IopmpLazy(numBridge))

  // 3. 实例化“积木块”Memory模块
  // 3. Instantiate the Memory building block.
  val memory = LazyModule(new MemoryLazy(depth = memDepth))

  // 4. 【核心】Diplomacy连接：声明数据流向 dcache -> iopmp -> memory
  // 4. Core Diplomacy connection: declare the data flow dcache -> iopmp -> memory.
  iopmp.slaveNodes(0) := dcache.masterNode // 规则: 从设备 := 主设备
  // Rule: slave := master.
  memory.slaveNode := iopmp.masterNodes(0)

  lazy val module = new Imp // 5. 硬件实现在这里
  // 5. The hardware implementation is defined here.
}
```

:::info
<!-- **核心思想**：记住 Diplomacy 的连接公式 <code>**下游 := 上游**</code>。 -->
**Core idea**: remember Diplomacy’s connection formula, <code>**downstream := upstream**</code>.

<!-- * <code>**:=**</code>**操作符** 读作“连接到”。 -->
* Read the <code>**:=**</code> **operator** as “connect to.”
<!-- * **数据流向** 是**从右向左**流，即从 `上游`流到 `下游`。 -->
* **Data flows** **from right to left**, from the `upstream` side to the `downstream` side.
<!-- * 谁是上游？**数据的生产者、请求的发起者**是上游（通常是 Master）。 -->
* What is upstream? The **data producer and request initiator** is upstream (usually the master).
<!-- * 谁是下游？**数据的消费者、请求的接收者**是下游（通常是 Slave）。 -->
* What is downstream? The **data consumer and request receiver** is downstream (usually the slave).

<!-- 所以 `iopmp.slaveNodes(0) := dcache.masterNode`的含义是：**IOPMP 的从端口（下游）接收来自 Dcache 主端口（上游）的数据**。这两行代码就等价于画出了上面的系统架构图！ -->
Therefore, `iopmp.slaveNodes(0) := dcache.masterNode` means that **the IOPMP slave port (downstream) receives data from the DCache master port (upstream)**. These two lines are equivalent to the system architecture diagram above.

:::

<!-- **Diplomacy核心要点**： -->
**Key Diplomacy points**:

<!-- 1. **模块化设计**：每个功能模块独立实例化为LazyModule -->
1. **Modular design**: instantiate each functional module independently as a `LazyModule`
<!-- 2. **节点明确分工**： -->
2. **Clear node roles**:
   <!-- * `dcache.masterNode`：主设备节点，发起请求 -->
   * `dcache.masterNode`: master node that initiates requests
   <!-- * `iopmp.slaveNodes(0)`：从设备节点，接收来自Dcache的请求 -->
   * `iopmp.slaveNodes(0)`: slave node that receives requests from DCache
   <!-- * `iopmp.masterNodes(0)`：主设备节点，转发请求到Memory -->
   * `iopmp.masterNodes(0)`: master node that forwards requests to Memory
   <!-- * `memory.slaveNode`：从设备节点，接收来自IOPMP的请求 -->
   * `memory.slaveNode`: slave node that receives requests from IOPMP
<!-- 3. **拓扑清晰**：使用`:=`操作符建立链式连接关系 -->
3. **Clear topology**: use the `:=` operator to establish a chained connection

<!-- ### 1.3.2 连接方向语义 -->
### 1.3.2 Connection-Direction Semantics

<!-- Diplomacy的连接操作符`:=`有明确的流向语义： -->
Diplomacy’s `:=` connection operator has explicit flow semantics:

```scala
// 语法：下游 := 上游
// Syntax: downstream := upstream.
// 语义：数据从上游流向下游
// Semantics: data flows from upstream to downstream.

iopmp.slaveNodes(0) := dcache.masterNode  // 数据：Dcache -> IOPMP
// Data: DCache -> IOPMP.
memory.slaveNode := iopmp.masterNodes(0)  // 数据：IOPMP -> Memory
// Data: IOPMP -> Memory.
```

<!-- **连接解释**： -->
**Connection explanation**:

<!-- 1. 第一行：IOPMP的从设备节点接收来自Dcache主设备节点的数据 -->
1. First line: the IOPMP slave node receives data from the DCache master node.
<!-- 2. 第二行：Memory的从设备节点接收来自IOPMP主设备节点的数据 -->
2. Second line: the Memory slave node receives data from the IOPMP master node.
<!-- 3. 数据流向：Dcache → IOPMP → Memory -->
3. Data flow: DCache → IOPMP → Memory.

<!-- ## 1.4 模块实现细节 -->
## 1.4 Module Implementation Details

<!-- ### 1.4.1 Dcache模块实现 -->
### 1.4.1 DCache Module Implementation

<!-- Dcache模块实现了完整的AXI4主设备功能： -->
The DCache module implements complete AXI4 master functionality:

<!-- **核心设计**： -->
**Core design**:

```scala
class DcacheLazy(...)(implicit p: Parameters) extends LazyModule {
  val masterNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "dcache",
      id = IdRange(0, IopmpParams.axi_idNum)
    ))
  )))

  lazy val module = new Imp

  class Imp extends LazyModuleImp(this) {
    // 六状态状态机
    // Six-state machine.
    object State extends ChiselEnum {
      val sIdle, sReadAddr, sReadData, sWriteAddr, sWriteData, sWriteResp = Value
    }
    val state = RegInit(State.sIdle)

    // 外部控制接口
    // External control interface.
    val io = IO(new Bundle {
      val req_valid = Input(Bool())
      val req_ready = Output(Bool())
      // ... 其他信号
      // ... other signals
    })

    // 获取AXI4 Bundle
    // Obtain the AXI4 bundle.
    val (masterBundle, masterEdge) = masterNode.out.head

    // 状态机实现
    // State-machine implementation.
    switch(state) {
      is(State.sIdle) {
        when(io.req_valid) {
          // 保存请求参数
          // Save request parameters.
          req_addr_reg := io.req_addr
          req_write_reg := io.req_write
          // ... 其他参数
          // ... other parameters

          // 根据读写类型进入不同状态
          // Enter a state based on the read/write type.
          when(io.req_write) {
            state := State.sWriteAddr
          }.otherwise {
            state := State.sReadAddr
          }
        }
      }
      // ... 其他状态处理
      // ... other state handling
    }

    // AXI4通道信号赋值
    // Assign AXI4 channel signals.
    masterBundle.ar.valid := state === State.sReadAddr
    masterBundle.ar.bits.addr := req_addr_reg
    // ... 其他信号
    // ... other signals

    // 控制信号
    // Control signals.
    io.req_ready := state === State.sIdle
    io.busy := state =/= State.sIdle
  }
}
```

<!-- **关键特性**： -->
**Key features**:

<!-- 1. **六状态状态机**：精确控制AXI4协议的5个独立通道 -->
1. **Six-state machine**: precisely control AXI4’s five independent channels
<!-- 2. **外部控制接口**：通过简单的握手信号控制复杂的AXI4操作 -->
2. **External control interface**: control complex AXI4 operations through simple handshake signals
<!-- 3. **ID管理**：为每个事务分配唯一的ID，支持乱序响应 -->
3. **ID management**: assign a unique ID to each transaction and support out-of-order responses
<!-- 4. **突发传输支持**：支持多拍数据的突发传输 -->
4. **Burst-transfer support**: support bursts spanning multiple beats

<!-- ### 1.4.2 IOPMP模块设计 -->
### 1.4.2 IOPMP Module Design

<!-- IOPMP模块在bypass模式下作为透明桥接器： -->
In bypass mode, the IOPMP module acts as a transparent bridge:

<!-- **设计原理**： -->
**Design principle**:

```scala
class IopmpLazy(numBridge: Int)(implicit p: Parameters) extends LazyModule {
  // 从设备节点数组（接收来自主设备的请求）
  // Slave-node array (receives requests from masters).
  val slaveNodes = Seq.tabulate(numBridge) { i =>
    AXI4SlaveNode(Seq(AXI4SlavePortParameters(
      Seq(AXI4SlaveParameters(
        address = Seq(AddressSet(0x0, (1L << IopmpParams.axi_addrBits) - 1)),
        regionType = RegionType.UNCACHED,
        executable = false,
        supportsRead = TransferSizes(1, IopmpParams.axi_beatByte),
        supportsWrite = TransferSizes(1, IopmpParams.axi_beatByte)
      )),
      beatBytes = IopmpParams.axi_beatByte
    )))
  }

  // 主设备节点数组（向从设备转发请求）
  // Master-node array (forwards requests to slaves).
  val masterNodes = Seq.tabulate(numBridge) { i =>
    AXI4MasterNode(Seq(AXI4MasterPortParameters(
      Seq(AXI4MasterParameters(
        name = s"iopmp_master_$i",
        id = IdRange(0, IopmpParams.axi_idNum)
      ))
    )))
  }

  // APB配置节点
  // APB configuration node.
  val apb_s = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address = Seq(AddressSet(IopmpParams.regcfg_base, IopmpParams.regcfg_mask)),
      supportsRead = TransferSizes(1, 4),
      supportsWrite = TransferSizes(1, 4)
    )),
    beatBytes = 4
  )))

  // 内部连接：在bypass模式下，slaveNode直接连接到masterNode
  // Internal connection: in bypass mode, connect slaveNode directly to masterNode.
  (masterNodes zip slaveNodes).foreach { case (master, slave) =>
    master := slave
  }
}
```

<!-- **bypass模式实现**： -->
**Bypass-mode behavior**:

<!-- * 当IOPMP工作在bypass模式时，不进行权限检查 -->
* When IOPMP operates in bypass mode, it performs no permission checks.
<!-- * 从设备节点接收的请求直接转发到主设备节点 -->
* Requests received by slave nodes are forwarded directly to master nodes.
<!-- * APB配置接口可以悬空，不影响数据通路 -->
* The APB configuration interface may remain unconnected without affecting the data path.

<!-- ### 1.4.3 Memory模块实现 -->
### 1.4.3 Memory Module Implementation

<!-- Memory模块实现了较完整的AXI4从设备功能： -->
The Memory module implements a relatively complete AXI4 slave:

<!-- **核心设计**： -->
**Core design**:

```scala
class MemoryLazy(...)(implicit p: Parameters) extends LazyModule {
  val slaveNode = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address = Seq(address),
      regionType = RegionType.UNCACHED,
      executable = false,
      supportsRead = TransferSizes(1, beatBytes),
      supportsWrite = TransferSizes(1, beatBytes),
      interleavedId = Some(0)
    )),
    beatBytes = beatBytes
  )))

  lazy val module = new Imp

  class Imp extends LazyModuleImp(this) {
    val (slaveBundle, slaveEdge) = slaveNode.in.head

    // 使用Chisel Mem实现存储
    // Implement storage with Chisel Mem.
    val mem = Mem(depth, UInt(IopmpParams.axi_dataBits.W))

    // 读通道状态机
    // Read-channel state machine.
    object ReadState extends ChiselEnum {
      val sIdle, sRead = Value
    }
    val readState = RegInit(ReadState.sIdle)

    // 写通道状态机
    // Write-channel state machine.
    object WriteState extends ChiselEnum {
      val sIdle, sWriteData, sWriteResp = Value
    }
    val writeState = RegInit(WriteState.sIdle)

    // 读状态机实现
    // Read-state-machine implementation.
    switch(readState) {
      is(ReadState.sIdle) {
        when(slaveBundle.ar.fire) {
          // 从内存读取数据
          // Read data from memory.
          readDataBuf := mem.read(slaveBundle.ar.bits.addr >> log2Ceil(beatBytes).U)
          readValid := true.B
          readState := ReadState.sRead
        }
      }
      // ... 其他状态处理
      // ... other state handling
    }

    // 写状态机实现
    // Write-state-machine implementation.
    switch(writeState) {
      is(WriteState.sIdle) {
        when(slaveBundle.aw.fire) {
          writeState := WriteState.sWriteData
        }
      }
      is(WriteState.sWriteData) {
        when(slaveBundle.w.fire) {
          // 写入内存
          // Write to memory.
          mem.write(writeAddrIndex, slaveBundle.w.bits.data)
          writeCount := writeCount + 1.U

          when(slaveBundle.w.bits.last || writeCount === writeLen) {
            writeState := WriteState.sWriteResp
            writeRespValid := true.B
          }
        }
      }
      // ... 其他状态处理
      // ... other state handling
    }

    // 调试接口
    // Debug interface.
    val debug = IO(new Bundle {
      val mem_addr = Input(UInt(memAddrWidth.W))
      val mem_rdata = Output(UInt(IopmpParams.axi_dataBits.W))
    })
    debug.mem_rdata := mem.read(debug.mem_addr)
  }
}
```

<!-- **关键特性**： -->
**Key features**:

<!-- 1. **双独立状态机**：读通道和写通道使用独立的状态机，支持全双工操作 -->
1. **Two independent state machines**: use separate machines for read and write channels to support full-duplex operation
<!-- 2. **突发传输支持**：正确处理AXI4突发传输，支持地址递增模式 -->
2. **Burst-transfer support**: correctly handle AXI4 bursts, including incrementing-address mode
<!-- 3. **异步调试接口**：提供直接访问内存的接口，便于验证 -->
3. **Asynchronous debug interface**: provide direct memory access for verification
<!-- 4. **正确响应生成**：按照AXI4协议生成正确的响应信号 -->
4. **Correct response generation**: generate response signals according to the AXI4 protocol

<!-- ## 1.5 Diplomacy连接模式详解 -->
## 1.5 Diplomacy Connection Patterns in Detail

<!-- ### 1.5.1 链式连接拓扑 -->
### 1.5.1 Chained Connection Topology

<!-- 本案例采用最简单的链式连接： -->
This case uses the simplest chained connection:

<!-- ![画板](img/chapter-14-diplomacy-practical-exercise-1/figure-001-chapter-14-diplomacy-practical-exercise-1.jpeg) -->
![Diagram](img/chapter-14-diplomacy-practical-exercise-1/figure-001-chapter-14-diplomacy-practical-exercise-1.jpeg)

<!-- 在Diplomacy中表示为： -->
In Diplomacy, it is expressed as:

```plain
iopmp.slaveNodes(0) := dcache.masterNode
memory.slaveNode := iopmp.masterNodes(0)
```

<!-- ### 1.5.2 参数自动协商 -->
### 1.5.2 Automatic Parameter Negotiation

<!-- 当建立连接时，Diplomacy自动执行参数协商： -->
When the connection is established, Diplomacy automatically negotiates parameters:

<!-- 1. **位宽对齐**：确保Dcache、IOPMP、Memory的`beatBytes`一致 -->
1. **Width alignment**: ensure that `beatBytes` matches across DCache, IOPMP, and Memory
<!-- 2. **ID空间分配**：协调主设备的ID范围，避免冲突 -->
2. **ID-space allocation**: coordinate the master ID range and avoid conflicts
<!-- 3. **地址映射**：验证Memory的地址空间在Dcache可访问范围内 -->
3. **Address mapping**: verify that Memory’s address space is accessible by DCache
<!-- 4. **协议特性协商**：确认支持的传输大小、突发类型等是否兼容 -->
4. **Protocol-feature negotiation**: confirm that supported transfer sizes, burst types, and other features are compatible

<!-- ### 1.5.3 编译时错误检查 -->
### 1.5.3 Compile-Time Error Checking

<!-- Diplomacy在编译时检查常见错误： -->
Diplomacy checks common errors at compile time:

<!-- 1. **节点角色不匹配**：尝试将主设备节点连接到主设备节点 -->
1. **Node-role mismatch**: attempting to connect a master node to another master node
<!-- 2. **参数不兼容**：位宽、ID范围等参数不匹配 -->
2. **Parameter incompatibility**: widths, ID ranges, or other parameters do not match
<!-- 3. **地址空间冲突**：多个从设备地址重叠 -->
3. **Address-space conflict**: address spaces of multiple slaves overlap
<!-- 4. **连接方向错误**：数据流向不符合物理连接 -->
4. **Incorrect connection direction**: data flow does not match the physical connection

<!-- ## 1.6 系统包装与接口暴露 -->
## 1.6 System Wrapper and Interface Exposure

<!-- ### 1.6.1 顶层包装器（IopmpSystemWrapper） -->
### 1.6.1 Top-Level Wrapper (`IopmpSystemWrapper`)

<!-- 为了方便使用，系统提供了顶层包装器： -->
For convenience, the system provides a top-level wrapper:

```scala
class IopmpSystemWrapper(...)(implicit p: Parameters) extends LazyModule {
  val system = LazyModule(new IopmpSystemLazy(numBridge, memDepth))

  lazy val module = new LazyModuleImp(this) {
    // 1. APB配置接口
    // 1. APB configuration interface.
    val apb_s = IO(new APBSlaveBundle(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits))
    apb_s <> system.module.apb_s

    // 2. 中断输出
    // 2. Interrupt output.
    val int = IO(Output(Bool()))
    int := system.module.int

    // 3. Dcache控制接口
    // 3. DCache control interface.
    val dcache_ctrl = IO(new Bundle {
      // ... 与dcache_io相同的结构
      // ... same structure as dcache_io
    })
    dcache_ctrl <> system.module.dcache_io

    // 4. Memory调试接口
    // 4. Memory debug interface.
    val mem_debug = IO(new Bundle {
      val mem_addr = Input(UInt(log2Ceil(memDepth).W))
      val mem_rdata = Output(UInt(IopmpParams.axi_dataBits.W))
    })
    mem_debug <> system.module.mem_debug

    // 5. 系统状态
    // 5. System status.
    val status = IO(new Bundle {
      val dcache_busy = Output(Bool())
      val iopmp_int = Output(Bool())
    })
    status <> system.module.status
  }
}
```

<!-- ### 1.6.2 Verilog生成入口 -->
### 1.6.2 Verilog Generation Entry Point

<!-- 系统提供了Verilog生成入口： -->
The system provides a Verilog-generation entry point:

```scala
object IopmpSystem extends App {
  implicit val p: Parameters = Parameters.empty

  val top = LazyModule(new IopmpSystemWrapper(numBridge = 1, memDepth = 1024))

  ChiselStage.emitSystemVerilog(
    top.module,
    args = Array("--dump-fir"),
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--disable-annotation-unknown",
      "--lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none",
      "--split-verilog",
      "-o=./build/iopmp_system"
    )
  )
}
```

<!-- ## 1.7 测试与验证建议 -->
## 1.7 Testing and Verification Suggestions

<!-- ### 1.7.1 验证策略 -->
### 1.7.1 Verification Strategy

<!-- 1. **单元测试**：单独测试每个模块的功能 -->
1. **Unit tests**: test each module independently
<!-- 2. **集成测试**：验证完整数据通路的正确性 -->
2. **Integration tests**: verify the complete data path
<!-- 3. **边界测试**：测试地址边界、数据边界等特殊情况 -->
3. **Boundary tests**: test address boundaries, data boundaries, and other special cases
<!-- 4. **性能测试**：测试突发传输、并发访问等场景 -->
4. **Performance tests**: test bursts, concurrent accesses, and similar scenarios

<!-- ### 1.7.2 调试支持 -->
### 1.7.2 Debug Support

<!-- 在硬件模块中添加调试信息： -->
Add debug information to the hardware modules:

```scala
// 在Dcache模块中添加调试打印
// Add debug printing in the DCache module.
when(masterBundle.ar.fire) {
  printf(p"[DCache] AR request: addr=0x${Hexadecimal(masterBundle.ar.bits.addr)}, " +
         p"id=${masterBundle.ar.bits.id}, len=${masterBundle.ar.bits.len}\n")
}

// 在Memory模块中添加调试打印
// Add debug printing in the Memory module.
when(slaveBundle.ar.fire) {
  printf(p"[Memory] Read request: addr=0x${Hexadecimal(slaveBundle.ar.bits.addr)}\n")
}
when(slaveBundle.w.fire) {
  printf(p"[Memory] Write data: addr=0x${Hexadecimal(writeAddr)}, " +
         p"data=0x${Hexadecimal(slaveBundle.w.bits.data)}\n")
}
```

<!-- ### 1.7.3 测试用例示例 -->
### 1.7.3 Example Test Cases

```plain
// 简单的读写测试
// Simple read/write test.
// 1. 向地址0x1000写入数据0x12345678
// 1. Write 0x12345678 to address 0x1000.
// 2. 从地址0x1000读取数据，验证是否为0x12345678
// 2. Read from address 0x1000 and verify that the value is 0x12345678.
// 3. 使用Memory调试接口直接读取验证
// 3. Read directly through the Memory debug interface for verification.

// 突发传输测试
// Burst-transfer test.
// 1. 发起长度为4的突发写操作
// 1. Issue a burst write of length 4.
// 2. 发起长度为4的突发读操作
// 2. Issue a burst read of length 4.
// 3. 验证所有数据正确
// 3. Verify all data.
```

<!-- ## 1.8 扩展性设计 -->
## 1.8 Extensibility

<!-- 当前系统可轻松扩展： -->
The current system can be extended easily:

```scala
// 添加第二个主设备
// Add a second master.
val dcache2 = LazyModule(new DcacheLazy())
// 需要修改IOPMP以支持多个主设备
// Modify IOPMP to support multiple masters.
// iopmp.slaveNodes(1) := dcache2.masterNode

// 添加第二个从设备
// Add a second slave.
val memory2 = LazyModule(new MemoryLazy(depth = 512))
// 需要修改IOPMP以支持多个从设备
// Modify IOPMP to support multiple slaves.
// memory2.slaveNode := iopmp.masterNodes(1)

// 修改IOPMP工作模式
// Change the IOPMP operating mode.
// 通过APB配置接口设置IOPMP为非bypass模式
// Use the APB configuration interface to set IOPMP to non-bypass mode.
// 启用权限检查功能
// Enable permission checking.
```

<!-- ## 1.9 常见问题与解决方案 -->
## 1.9 Common Problems and Solutions

<!-- ### 问题1：连接方向错误 -->
### Problem 1: Incorrect Connection Direction

<!-- **现象**：编译错误，提示节点角色不匹配 -->
**Symptom**: compilation fails with a node-role mismatch.

<!-- **解决**：检查`:=`操作符左右两边的节点角色 -->
**Solution**: check the node roles on both sides of the `:=` operator.

```scala
// 正确：Slave := Master
// Correct: Slave := Master.
iopmp.slaveNodes(0) := dcache.masterNode

// 错误：Master := Slave
// Incorrect: Master := Slave.
dcache.masterNode := iopmp.slaveNodes(0)  // 编译错误
// Compilation error.
```

<!-- ### 问题2：参数不匹配 -->
### Problem 2: Parameter Mismatch

<!-- **现象**：编译错误，提示参数无法合并 -->
**Symptom**: compilation fails because parameters cannot be merged.

<!-- **解决**：检查连接模块的参数配置 -->
**Solution**: check the parameter configuration of the connected modules.

```scala
// 确保所有模块的beatBytes一致
// Ensure that beatBytes matches across all modules.
val dcache = LazyModule(new DcacheLazy(beatBytes = 8))
val iopmp = LazyModule(new IopmpLazy(numBridge = 1))  // 内部使用IopmpParams.axi_beatByte
// Internally uses IopmpParams.axi_beatByte.
val memory = LazyModule(new MemoryLazy(beatBytes = 8))

// 确保IopmpParams.axi_beatByte与其他模块一致
// Ensure IopmpParams.axi_beatByte matches the other modules.
```

<!-- ### 问题3：地址空间冲突 -->
### Problem 3: Address-Space Conflict

<!-- **现象**：多个从设备响应同一地址空间 -->
**Symptom**: multiple slaves respond to the same address space.

<!-- **解决**：明确划分地址空间 -->
**Solution**: partition the address spaces explicitly.

```scala
// 为不同从设备分配不同的地址空间
// Assign different address spaces to different slaves.
val memory1 = LazyModule(new MemoryLazy(
  address = AddressSet(0x80000000L, 0x0fffffffL)  // 256MB空间
  // 256 MB space.
))

val memory2 = LazyModule(new MemoryLazy(
  address = AddressSet(0x90000000L, 0x0fffffffL)  // 与memory1重叠，错误！
  // Overlaps memory1; incorrect.
))
```

<!-- ### 问题4：ID空间耗尽 -->
### Problem 4: ID-Space Exhaustion

<!-- **现象**：主设备ID范围不足 -->
**Symptom**: the master’s ID range is insufficient.

<!-- **解决**：合理分配ID范围 -->
**Solution**: allocate ID ranges appropriately.

```scala
val dcache = LazyModule(new DcacheLazy())
// Dcache内部使用IdRange(0, IopmpParams.axi_idNum)
// DCache internally uses IdRange(0, IopmpParams.axi_idNum).

// 确保IopmpParams.axi_idNum足够大
// Ensure that IopmpParams.axi_idNum is large enough.
object IopmpParams {
  val axi_idNum = 256  // 支持256个不同的ID
  // Supports 256 distinct IDs.
}
```

##


<!-- > 更新: 2026-06-23 14:24:14
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/umli5i56isuyxnox> -->
> Updated: 2026-06-23 14:24:14
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/umli5i56isuyxnox>
