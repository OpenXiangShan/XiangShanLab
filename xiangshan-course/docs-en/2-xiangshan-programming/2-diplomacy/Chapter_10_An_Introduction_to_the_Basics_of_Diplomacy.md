<!-- # 第十章 Diplomacy 基础简介 -->
# Chapter 10: An Introduction to the Basics of Diplomacy

<!-- Diplomacy与CDE超通俗入门教程（是什么/为什么/怎么做） -->
An Accessible Introductory Tutorial to Diplomacy and CDE (What They Are / Why They Matter / How to Use Them)

<!-- ## 前言 -->
## Preface

<!-- 为降低入门门槛，全文摒弃晦涩源码堆砌，统一采用**是什么、为什么、怎么做**的逻辑拆解，搭配极简通俗示例，聚焦核心学习要点。 -->
To lower the entry barrier, this tutorial avoids obscure source-code dumps and consistently explains concepts through **what, why, and how**, using minimal, accessible examples to focus on the key learning points.

<!-- **本文不包含**：零基础上手实操步骤、完整项目代码演练。如需实操学习，可结合 Rocket-chip、香山（XiangShan）、缓存仓库（HuanCun）源码、官方例程与大佬分享视频进阶。 -->
**This tutorial does not include**: step-by-step hands-on instructions for beginners or a complete project code walkthrough. For practical study, combine it with the Rocket-chip, XiangShan, and HuanCun cache source code, official examples, and advanced video tutorials.

<!-- **核心学习目标**：搞懂两大框架的核心作用、运行逻辑、核心组件、使用场景，建立Chisel大规模SoC设计的参数化思维。 -->
**Core learning objectives**: understand the purpose, execution logic, core components, and use cases of the two frameworks, and develop a parameterized way of thinking about large-scale SoC design in Chisel.

<!-- ## 核心前置认知 -->
## Essential Background

<!-- 传统Verilog/原生Chisel开发的痛点：硬件模块参数固定、模块互联参数需人工手动对齐、复杂流水线/SoC拓扑下参数耦合严重，代码复用性极差，牵一发而动全身。 -->
The pain points of traditional Verilog or plain Chisel development are fixed hardware-module parameters, manual alignment of inter-module connection parameters, severe coupling in complex pipelines and SoC topologies, poor code reuse, and changes in one place affecting everything else.

<!-- RISC-V高端开源芯片（Rocket、香山）的解决方案：**CDE负责全局静态参数配置，Diplomacy负责模块互联动态参数协商**，二者配合实现芯片设计的**参数化、可配置、高复用**。 -->
The solution used by advanced open-source RISC-V processors such as Rocket and XiangShan is: **CDE handles global static parameter configuration, while Diplomacy handles dynamic parameter negotiation for inter-module connections**. Together they enable **parameterized, configurable, and highly reusable** chip designs.

<!-- # Diplomacy 框架详解 -->
# Diplomacy Framework in Detail

<!-- ## 1.1 什么是Diplomacy？（核心定义） -->
## 1.1 What Is Diplomacy? (Core Definition)

<!-- **官方定义**：Diplomacy is a parameter negotiation framework for generating parameterized protocol implementations. -->
**Official definition**: Diplomacy is a parameter negotiation framework for generating parameterized protocol implementations.

<!-- **通俗翻译**：Diplomacy是一套**模块互联参数自动协商框架**，专门解决多个硬件模块连接时，端口参数、协议参数不匹配的问题，自动完成参数对齐，最终生成标准化的协议硬件电路。 -->
**In plain language**: Diplomacy is an **automatic parameter-negotiation framework for interconnecting modules**. It resolves mismatches in port and protocol parameters when hardware modules are connected, automatically aligns those parameters, and ultimately generates standardized protocol hardware circuits.

<!-- **核心定位**：专注**模块之间**的拓扑连接与参数协商，是SoC总线、流水线、多级缓存互联的核心底层框架，TileLink总线协议完全基于Diplomacy扩展实现。 -->
**Core role**: Diplomacy focuses on topology connections and parameter negotiation **between modules**. It is the underlying framework for SoC buses, pipelines, and multilevel-cache interconnects; the TileLink bus protocol is implemented entirely as a Diplomacy extension.

<!-- ## 1.2 为什么需要Diplomacy？（解决的核心问题） -->
## 1.2 Why Is Diplomacy Needed? (The Core Problems It Solves)

<!-- ### 传统开发痛点 -->
### Pain Points of Traditional Development

<!-- 1. 模块参数硬编码，修改一个参数需要改动多个关联模块，无复用性； -->
1. Module parameters are hard-coded, so changing one parameter requires edits in multiple related modules, leaving little reuse;

<!-- 2. 多模块互联时，端口位宽、传输大小、缓存属性等参数需人工逐一核对，极易出错； -->
2. When multiple modules are interconnected, parameters such as port width, transfer size, and cache attributes must be checked manually one by one, which is highly error-prone;

<!-- 3. 复杂乱序流水线、多级缓存、多核SoC场景下，参数耦合极其复杂，人工协商参数成本极高、可读性极差、难以维护。 -->
3. In complex out-of-order pipelines, multilevel caches, and multicore SoCs, parameter coupling becomes extremely complicated; manual negotiation is costly, hard to read, and difficult to maintain.

<!-- ### Diplomacy的核心价值 -->
### Core Value of Diplomacy

<!-- 1. **自动化参数协商**：无需人工手动计算对齐参数，框架自动根据用户定义规则完成上下游参数匹配； -->
1. **Automated parameter negotiation**: the framework matches upstream and downstream parameters according to user-defined rules, without manual alignment calculations;

<!-- 2. **拓扑可视化管理**：将所有硬件模块抽象为有向无环图（DAG），模块即节点、连线即边，清晰管理SoC整体拓扑； -->
2. **Visual topology management**: all hardware modules are abstracted as a directed acyclic graph (DAG), where modules are nodes and connections are edges, making the overall SoC topology clear;

<!-- 3. **延迟实例化**：利用Scala懒加载特性，先完成所有参数协商，再生成最终RTL硬件电路，避免参数冲突； -->
3. **Delayed instantiation**: Scala's lazy-loading feature allows all parameter negotiation to finish before the final RTL hardware circuit is generated, avoiding parameter conflicts;

<!-- 4. **协议标准化**：支撑TileLink、AXI4等总线协议的参数化扩展，适配各类复杂片上互联场景。 -->
4. **Protocol standardization**: Diplomacy supports parameterized extensions of bus protocols such as TileLink and AXI4, adapting to complex on-chip interconnect scenarios.

<!-- ## 1.3 Diplomacy核心核心知识点（必学） -->
## 1.3 Core Diplomacy Concepts (Must Learn)

<!-- Diplomacy所有功能围绕四大核心要素展开：**LazyModule、Node、Edge、参数协商规则**，以下逐个拆解，搭配极简示例。 -->
All Diplomacy functionality revolves around four core elements: **LazyModule, Node, Edge, and parameter-negotiation rules**. They are broken down below with minimal examples.

<!-- ### 知识点1：LazyModule 懒加载模块（基础载体） -->
### Concept 1: LazyModule (The Basic Lazy-Loading Container)

<!-- #### 是什么 -->
#### What

<!-- LazyModule是Diplomacy的**最小模块单元**，所有支持参数协商的硬件模块，都必须继承该类。 -->
LazyModule is Diplomacy's **smallest module unit**. Every hardware module that supports parameter negotiation must extend this class.

<!-- 它将模块拆分为两个阶段，彻底实现「参数协商」与「硬件生成」解耦： -->
It splits a module into two stages, fully decoupling "parameter negotiation" from "hardware generation":

<!-- 1. **协商阶段（非懒加载）**：定义子模块、拓扑节点、参数协商规则，完成所有参数对齐； -->
1. **Negotiation stage (non-lazy)**: define child modules, topology nodes, and parameter-negotiation rules, and align all parameters;

<!-- 2. **RTL生成阶段（懒加载lazy）**：参数协商完成后，通过`LazyModuleImp`实例化最终硬件电路。 -->
2. **RTL-generation stage (lazy)**: after parameter negotiation is complete, instantiate the final hardware circuit through `LazyModuleImp`.

<!-- #### 为什么 -->
#### Why

<!-- 传统Chisel模块实例化和参数定义同步进行，无法实现跨模块参数预协商；LazyModule的延迟实例化特性，是Diplomacy自动参数协商的基础。 -->
Traditional Chisel instantiates modules while defining their parameters, so it cannot perform cross-module pre-negotiation. LazyModule's delayed-instantiation property is the foundation of Diplomacy's automatic parameter negotiation.

<!-- #### 怎么做（极简示例） -->
#### How (Minimal Example)

<!-- 定义一个可参与Diplomacy参数协商的基础模块，固定代码结构： -->
Define a basic module that participates in Diplomacy parameter negotiation using the following standard structure:

```scala
// 所有Diplomacy模块必须隐式携带全局参数上下文Parameters
// Every Diplomacy module must implicitly carry the global Parameters context.
class MyDiplomacyModule(implicit p: Parameters) extends LazyModule {
  // 1. 协商阶段：定义节点、子模块、参数规则（无硬件生成）
  // 1. Negotiation stage: define nodes, child modules, and parameter rules (no hardware generation).
  val myNode = TLIdentityNode() 

  // 2. 懒加载RTL：参数协商完成后，才会执行硬件实例化
  // 2. Lazy RTL stage: hardware is instantiated only after parameter negotiation completes.
  lazy val module = new LazyModuleImp(this) {
    // 最终硬件逻辑，可使用协商完成的参数
    // Final hardware logic, which can use the negotiated parameters.
    val (tl, edge) = myNode.in(0)
  }
}
```

<!-- ### 知识点2：Node 拓扑节点（核心协商单元） -->
### Concept 2: Node (The Core Negotiation Unit)

<!-- #### 是什么 -->
#### What

<!-- Node是Diplomacy拓扑图的**核心节点**，不对应具体硬件IO，专门负责：参数定义、参数协商、协议属性描述、模块互联对接。 -->
Node is the **core vertex** in a Diplomacy topology graph. It does not represent specific hardware I/O; it is responsible for parameter definitions, parameter negotiation, protocol-property descriptions, and inter-module connections.

<!-- 所有模块的互联、参数传递、协议适配，全部通过Node完成，是Diplomacy的灵魂。 -->
All module interconnections, parameter propagation, and protocol adaptation are performed through Nodes; they are the essence of Diplomacy.

<!-- #### 核心分类（重点掌握6类常用节点） -->
#### Main Categories (Six Common Node Types to Master)

<!-- 根据数据流方向和功能，官方划分6类核心节点，适配所有SoC互联场景： -->
Based on data-flow direction and function, the framework defines six core node types that cover SoC interconnect scenarios:

<!-- 1. **ClientNode（客户端节点）**：主动发起请求的模块（CPU、DMA、L1缓存），仅输出数据流、下发参数； -->
1. **ClientNode**: a module that initiates requests (CPU, DMA, or L1 cache); it only outputs data flow and sends parameters downstream;
<!-- 2. **ManagerNode（管理端节点）**：接收请求、返回响应的模块（外设、内存、寄存器），仅接收数据流、接收上游参数； -->
2. **ManagerNode**: a module that receives requests and returns responses (peripherals, memory, or registers); it only receives data flow and upstream parameters;
<!-- 3. **RegisterNode（寄存器节点）**：简化版Manager节点，自动生成寄存器读写逻辑，无需手动处理TileLink协议； -->
3. **RegisterNode**: a simplified ManagerNode that automatically generates register read/write logic, without manually handling the TileLink protocol;
<!-- 4. **IdentityNode（恒等节点）**：纯转发节点，不修改参数、不修改数据流，用于合并/拆分多个模块节点； -->
4. **IdentityNode**: a pure forwarding node that changes neither parameters nor data flow, used to merge or split multiple module nodes;
<!-- 5. **AdapterNode（适配节点）**：可修改上下游参数/协议，不改变连接边数量，用于协议转换、参数适配（如缓存参数重配置）； -->
5. **AdapterNode**: can modify upstream/downstream parameters or protocols without changing the number of connection edges, and is used for protocol conversion and parameter adaptation (such as cache reconfiguration);
<!-- 6. **NexusNode（汇聚节点）**：支持输入输出边数量不匹配，用于总线交叉开关、多模块汇聚场景（如TLXbar总线）。 -->
6. **NexusNode**: supports different numbers of input and output edges, and is used for bus crossbars and multi-module aggregation (such as a TLXbar bus).

<!-- #### 怎么做（极简示例-IdentityNode多模块合并） -->
#### How (Minimal Example: Merging Multiple Modules with IdentityNode)

<!-- 场景：将两个客户端模块合并为一个统一对外节点，无需修改内部逻辑： -->
Scenario: merge two client modules into one unified external node without changing their internal logic:

```scala
class ClientGroup(implicit p: Parameters) extends LazyModule {
  // 实例化两个独立客户端
  // Instantiate two independent clients.
  val client1 = LazyModule(new MyClient)
  val client2 = LazyModule(new MyClient)
  // 定义恒等转发节点
  // Define an identity forwarding node.
  val node = TLIdentityNode()
  // 绑定多个子节点，自动合并拓扑
  // Bind multiple child nodes and merge the topology automatically.
  node := client1.node
  node := client2.node

  lazy val module = new LazyModuleImp(this) { /* 无需额外逻辑，自动转发 */ }
  // No extra logic is needed; forwarding is automatic.
}
```

<!-- ### 知识点3：NodeImp 节点实现器（参数协商规则核心） -->
### Concept 3: NodeImp (The Core of Parameter-Negotiation Rules)

<!-- #### 是什么 -->
#### What

<!-- NodeImp是**参数协商规则的定义模板**（全局单例object），所有Node的参数协商方式、硬件端口类型、协议属性，都由NodeImp统一规定。 -->
NodeImp is a **template for defining parameter-negotiation rules** (a global singleton object). It uniformly specifies how every Node negotiates parameters, its hardware port type, and its protocol properties.

<!-- 核心泛型四元组（所有协商的基础）：`[D向下参数, U向上参数, E边参数, B硬件Bundle类型]` -->
Its core generic quadruple (the basis of all negotiations) is: `[D downward parameters, U upward parameters, E edge parameters, B hardware Bundle type]`.

<!-- * D（DownwardParam）：从上位模块向下传递的参数； -->
* D (`DownwardParam`): parameters propagated downward from an upper module;
<!-- * U（UpwardParam）：从下位模块向上反馈的参数； -->
* U (`UpwardParam`): parameters fed back upward from a lower module;
<!-- * E（EdgeParam）：上下游协商后，最终生效的边参数； -->
* E (`EdgeParam`): the edge parameters that take effect after upstream/downstream negotiation;
<!-- * B（Bundle）：最终生成的硬件端口类型。 -->
* B (`Bundle`): the type of the generated hardware port.

<!-- #### 为什么 -->
#### Why

<!-- 不同硬件模块、不同总线协议的参数协商规则不同，通过NodeImp统一封装规则，实现节点复用、协议可扩展。 -->
Different hardware modules and bus protocols require different negotiation rules. NodeImp encapsulates these rules uniformly, enabling node reuse and protocol extensibility.

<!-- #### 怎么做（极简示例-位宽协商规则） -->
#### How (Minimal Example: Width-Negotiation Rule)

<!-- 定义一个简单的位宽协商规则：上下游位宽不匹配时，取最小值作为最终硬件位宽： -->
Define a simple width-negotiation rule: when upstream and downstream widths differ, use the smaller value as the final hardware width:

```scala
// 1. 定义三类参数结构体
// 1. Define three parameter structures.
case class DownwardParam(width: Int) // 向下传递位宽
case class UpwardParam(width: Int)   // 向上反馈位宽
case class EdgeParam(width: Int)     // 最终协商位宽

// 2. 定义节点协商规则
// 2. Define the node-negotiation rule.
object AdderNodeImp extends SimpleNodeImp[DownwardParam, UpwardParam, EdgeParam, UInt] {
  // 核心协商逻辑：取上下游位宽最小值
  // Core negotiation logic: select the smaller upstream/downstream width.
  def edge(pd: DownwardParam, pu: UpwardParam, p: Parameters, sourceInfo: SourceInfo) = {
    if (pd.width < pu.width) EdgeParam(pd.width) else EdgeParam(pu.width)
  }
  // 根据协商参数生成硬件端口
  // Generate the hardware port from the negotiated parameters.
  def bundle(e: EdgeParam) = UInt(e.width.W)
  // 拓扑渲染参数（可视化用）
  // Topology rendering parameters (for visualization).
  def render(e: EdgeParam) = RenderedEdge("blue", s"width = ${e.width}")
}
```

<!-- ### 知识点4：方向体系（最易混淆核心点） -->
### Concept 4: Direction System (The Most Easily Confused Point)

<!-- #### 是什么 -->
#### What

<!-- Diplomacy的参数/数据流存在**两套独立方向维度**，两两组合形成4种参数场景，是拓扑协商的核心逻辑： -->
Diplomacy parameter and data flow has **two independent direction dimensions**. Their combinations form four parameter scenarios and are central to topology negotiation:

<!-- 1. **内外方向（In/Out）**：相对于当前节点，数据流入为In、流出为Out； -->
1. **In/Out direction**: relative to the current node, incoming data is In and outgoing data is Out;
<!-- 2. **上下方向（Up/Down）**：相对于拓扑整体，从Master（主设备）到Slave（从设备）为Down，反向为Up。 -->
2. **Up/Down direction**: relative to the overall topology, flow from the Master to the Slave is Down, and the reverse is Up.

<!-- 组合场景：UI(上入)、DI(下入)、UO(上出)、DO(下出) -->
The combined scenarios are UI (upper-in), DI (lower-in), UO (upper-out), and DO (lower-out).

<!-- #### 通俗理解 -->
#### Intuitive Explanation

<!-- 以CPU访问外设为例：CPU（Master）→ 总线 → 外设（Slave），数据流、参数全部向下（Down）；外设返回数据、属性参数全部向上（Up）。 -->
Consider a CPU accessing a peripheral: CPU (Master) -> bus -> peripheral (Slave). Data and parameters flow downward (Down), while data and attribute parameters returned by the peripheral flow upward (Up).

<!-- ### 知识点5：拓扑连接运算符 -->
### Concept 5: Topology Connection Operators

<!-- #### 是什么 -->
#### What

<!-- Diplomacy提供4种专属连接运算符，用于节点之间的拓扑绑定，自动生成对应数量的连接边： -->
Diplomacy provides four dedicated connection operators for binding nodes in the topology and automatically generating the corresponding number of connection edges:

<!-- 1. **:=** 基础连接：两个节点之间生成**单条**连接边； -->
1. **`:=`** basic connection: generates **one** connection edge between two nodes;
<!-- 2. **:=**\* 客户端驱动多连接：边数量由右侧客户端节点决定； -->
2. **`:=*`** client-driven multiple connection: the right-side client node determines the number of edges;
<!-- 3. **:\*=** 管理端驱动多连接：边数量由左侧管理端节点决定； -->
3. **`:*=`** manager-driven multiple connection: the left-side manager node determines the number of edges;
<!-- 4. **:*=*** 自适应多连接：自动根据两侧节点边数量匹配，适配动态拓扑。 -->
4. **Adaptive multiple connection** (`:*=` followed by `*`): automatically matches the edge counts on both sides for dynamic topologies.

<!-- #### 怎么做（示例） -->
#### How (Example)

```scala
// 单条边基础连接
// Basic single-edge connection.
manager.node := client.node
// 自适应多边连接（多模块批量绑定）
// Adaptive multi-edge connection (batch binding of multiple modules).
manager.node :=* clientGroup.node
```

<!-- ### 知识点6：核心协商函数dFn/uFn -->
### Concept 6: Core Negotiation Functions dFn/uFn

<!-- #### 是什么 -->
#### What

<!-- 所有可协商节点（Adapter/Nexus）的核心逻辑函数，是**参数协商的真正执行者**： -->
These are the core logic functions of all negotiable nodes (Adapter/Nexus) and the **actual executors of parameter negotiation**:

<!-- * **dFn**：处理向下流动的参数，定义下游参数转换、匹配规则； -->
* **dFn**: processes parameters flowing downward and defines downstream parameter conversion and matching rules;
<!-- * **uFn**：处理向上流动的参数，定义上游参数转换、匹配规则。 -->
* **uFn**: processes parameters flowing upward and defines upstream parameter conversion and matching rules.

<!-- 最终所有参数经过dFn/uFn处理后，生成稳定的Edge边参数，再映射为硬件Bundle端口。 -->
After all parameters are processed by dFn/uFn, stable Edge parameters are produced and then mapped to hardware Bundle ports.

<!-- # CDE 参数配置框架详解 -->
# CDE Parameter-Configuration Framework in Detail

<!-- ## 2.1 什么是CDE？（核心定义） -->
## 2.1 What Is CDE? (Core Definition)

<!-- **CDE全称Context-Dependent Environments**，是一套**全局层级化Key-Value参数传递框架**。 -->
**CDE stands for Context-Dependent Environments**. It is a **global hierarchical key-value parameter-propagation framework**.

<!-- **通俗翻译**：CDE专门负责整个SoC的**全局静态参数配置**，可以在模块层级中自上而下传递参数，支持不同模块、不同核配置差异化参数，解决全局参数管理问题。 -->
**In plain language**: CDE handles **global static parameter configuration** for the entire SoC. It propagates parameters top-down through the module hierarchy, supports differentiated settings for different modules and cores, and solves the problem of global parameter management.

<!-- **核心定位**：Diplomacy管「模块互联动态协商」，CDE管「全局静态参数配置」，二者互补，共同支撑芯片参数化设计。 -->
**Core role**: Diplomacy manages "dynamic negotiation for inter-module connections," while CDE manages "global static parameter configuration." They complement each other and jointly support parameterized chip design.

<!-- ## 2.2 为什么需要CDE？（解决的核心问题） -->
## 2.2 Why Is CDE Needed? (The Core Problems It Solves)

<!-- ### 传统参数配置痛点 -->
### Pain Points of Traditional Parameter Configuration

<!-- 1. 全局参数繁多，参数名易冲突，无统一命名空间； -->
1. There are many global parameters, names easily collide, and no unified namespace exists;

<!-- 2. 多层级模块中，子模块无法灵活继承、覆盖父模块参数； -->
2. In a multi-level module hierarchy, child modules cannot flexibly inherit or override parent-module parameters;

<!-- 3. 多核、多模块差异化配置困难，一套代码无法生成多组不同配置的硬件； -->
3. Differentiated configuration for multicore, multi-module designs is difficult, and one codebase cannot generate multiple hardware configurations;

<!-- 4. 无法根据模块上下文（位置、层级）动态适配参数。 -->
4. Parameters cannot be adapted dynamically according to module context (position and hierarchy).

<!-- ### CDE核心价值 -->
### Core Value of CDE

<!-- 1. **命名空间隔离**：通过Field唯一Key区分参数，彻底解决参数冲突； -->
1. **Namespace isolation**: a unique Field key distinguishes each parameter and eliminates parameter collisions;

<!-- 2. **层级覆盖**：支持父模块默认参数、子模块按需重写，灵活适配差异化需求； -->
2. **Hierarchical overriding**: parent modules can provide defaults and child modules can override them as needed, flexibly meeting differentiated requirements;

<!-- 3. **上下文感知**：支持根据模块所在位置、层级动态生成参数； -->
3. **Context awareness**: parameters can be generated dynamically from a module's position and hierarchy;

<!-- 4. **可组合扩展**：多个配置文件可自由拼接、覆盖，实现设计空间灵活迭代。 -->
4. **Composable extensibility**: multiple configuration files can be freely composed and overridden, enabling flexible iteration across the design space.

<!-- ## 2.3 CDE核心知识点（必学） -->
## 2.3 Core CDE Concepts (Must Learn)

<!-- ### 知识点1：核心三要素（Field、Parameters、Config） -->
### Concept 1: The Three Core Elements (Field, Parameters, Config)

<!-- #### 是什么 -->
#### What

<!-- 1. **Field\[T] 参数键**：参数的唯一标识，定义参数类型、默认值，相当于Map的Key； -->
1. **`Field[T]` parameter key**: the unique identifier for a parameter; it defines the parameter type and default value and acts as a Map key;
<!-- 2. **Parameters 参数上下文**：全局参数载体，所有模块通过隐式参数`p: Parameters`获取配置； -->
2. **`Parameters` parameter context**: the global parameter carrier; every module obtains configuration through the implicit parameter `p: Parameters`;
<!-- 3. **Config 配置类**：参数键值对的具体实现，定义每个Field对应的具体数值。 -->
3. **`Config` configuration class**: the concrete implementation of parameter key-value pairs, defining the value associated with each Field.

<!-- #### 怎么做（极简示例） -->
#### How (Minimal Example)

```scala
// 1. 定义参数Key（指定类型、默认值）
// 1. Define parameter keys (type and default value).
case object CoreWidth extends Field[Int](32) // 核心位宽，默认32位
// Core width, 32 bits by default.
case object CoreName extends Field[String]("Rocket") // 核心名称
// Core name.

// 2. 定义自定义配置
// 2. Define a custom configuration.
val MyCoreConfig: Parameters = Config((site, here, up) => {
  case CoreWidth => 64  // 覆盖默认值，改为64位
  // Override the default and set it to 64 bits.
  case CoreName => "XiangShan"
})

// 3. 模块中获取全局参数
// 3. Obtain global parameters in a module.
class CoreModule(implicit p: Parameters) extends Module {
  val width = p(CoreWidth) // 读取配置：64
  // Read the configuration: 64.
  val name = p(CoreName)   // 读取配置：XiangShan
  // Read the configuration: XiangShan.
}
```

<!-- ### 知识点2：三大上下文变量（site/here/up） -->
### Concept 2: The Three Context Variables (site/here/up)

<!-- 这是CDE**上下文依赖**的核心，实现参数动态适配，三者作用： -->
These variables are the core of CDE's **context dependence** and enable dynamic parameter adaptation:

<!-- 1. **here**：当前配置层的参数视图，仅查询当前Config的参数； -->
1. **here**: the parameter view of the current configuration layer; it queries only the current Config;
<!-- 2. **up**：上层配置层的参数视图，查询父级、全局默认参数； -->
2. **up**: the parameter view of the upper configuration layers; it queries parent and global-default parameters;
<!-- 3. **site**：全局完整配置视图，查询所有层级的最终生效参数。 -->
3. **site**: the complete global configuration view; it queries the final effective parameters at every level.

<!-- #### 通俗示例 -->
#### Intuitive Example

<!-- 子模块需要基于全局默认参数，局部微调： -->
A child module needs to make a local adjustment based on a global default:

```scala
val SubConfig: Parameters = Config((site, here, up) => {
  // 基于全局位宽，动态调整
  // Dynamically adjust based on the global width.
  case CoreWidth => up(CoreWidth) / 2 
})
```

<!-- ### 知识点3：配置组合规则（alter/orElse） -->
### Concept 3: Configuration Composition Rules (alter/orElse)

<!-- 多个Config可自由组合，核心两个方法，优先级规则是重点： -->
Multiple Configs can be freely composed. The two key methods and their precedence rules are:

<!-- 1. **x.alter(y)**：y优先级高于x，先查y、再查x； -->
1. **`x.alter(y)`**: y has higher priority than x; query y first, then x;
<!-- 2. **x.orElse(y)**：x优先级高于y，先查x、再查y； -->
2. **`x.orElse(y)`**: x has higher priority than y; query x first, then y;

<!-- 查询逻辑：**自顶向下遍历配置层，找到第一个定义的参数即返回**。 -->
Lookup logic: **traverse configuration layers from top to bottom and return the first defined parameter**.

<!-- #### 简化记忆 -->
#### Memory Aid

<!-- `alter`：右边覆盖左边；`orElse`：左边覆盖右边。 -->
`alter`: the right side overrides the left; `orElse`: the left side overrides the right.

<!-- # Diplomacy与CDE 区别与配合关系 -->
# Differences and Cooperation Between Diplomacy and CDE

<!-- | **框架** | **核心作用** | **作用阶段** | **作用范围** | -->
<!-- | --- | --- | --- | --- | -->
<!-- | CDE | 全局静态参数配置、层级参数传递 | 模块初始化阶段 | 全局所有模块通用参数 | -->
<!-- | Diplomacy | 模块互联动态参数协商、拓扑管理 | 模块绑定互联阶段 | 模块间总线、端口、协议参数 | -->
| **Framework** | **Core role** | **Stage** | **Scope** |
| --- | --- | --- | --- |
| CDE | Global static parameter configuration and hierarchical parameter propagation | Module initialization | Common parameters for all modules |
| Diplomacy | Dynamic parameter negotiation for inter-module connections and topology management | Module binding and interconnection | Bus, port, and protocol parameters between modules |

<!-- **完整工作流程**： -->
**Complete workflow**:

<!-- 1. CDE加载全局Config，给所有模块分配基础静态参数； -->
1. CDE loads the global Config and assigns base static parameters to all modules;

<!-- 2. Diplomacy基于CDE基础参数，通过Node、Edge完成多模块互联参数协商； -->
2. Based on the CDE parameters, Diplomacy negotiates interconnection parameters among modules through Nodes and Edges;

<!-- 3. 所有参数协商完成后，统一生成最终可综合RTL硬件电路。 -->
3. Once all parameter negotiation is complete, the final synthesizable RTL hardware circuit is generated.

<!-- # 进阶学习资源（精准对标） -->
# Further Learning Resources (Direct References)

<!-- ## 源码资源 -->
## Source Code

<!-- * Diplomacy核心源码：rocket-chip/src/main/scala/diplomacy（LazyModule、Nodes核心实现） -->
* Diplomacy core source: rocket-chip/src/main/scala/diplomacy (core implementations of LazyModule and Nodes)
<!-- * Diplomacy官方教程：rocket-chip/docs/src/diplomacy/adder\_tutorial.md（最简加法器协商示例） -->
* Official Diplomacy tutorial: rocket-chip/docs/src/diplomacy/adder\_tutorial.md (a minimal adder negotiation example)
<!-- * CDE核心源码：cde/cde/src/chipsalliance/rocketchip/config.scala -->
* CDE core source: cde/cde/src/chipsalliance/rocketchip/config.scala
<!-- * 工程实战参考：香山SoC、HuanCun缓存模块（工业级参数协商实战） -->
* Engineering references: XiangShan SoC and HuanCun cache modules (industrial-scale parameter-negotiation practice)

<!-- ## 视频资源 -->
## Video Resources

<!-- * Diplomacy入门：https://www.bilibili.com/video/BV17r4y1i7qt -->
* Introduction to Diplomacy: https://www.bilibili.com/video/BV17r4y1i7qt
<!-- * CDE参数配置入门：https://www.bilibili.com/video/BV1Ao4y1U74F -->
* Introduction to CDE parameter configuration: https://www.bilibili.com/video/BV1Ao4y1U74F

<!-- ## 论文文档 -->
## Paper

<!-- Diplomacy官方论文：https://carrv.github.io/2017/papers/cook-diplomacy-carrv2017.pdf -->
Official Diplomacy paper: https://carrv.github.io/2017/papers/cook-diplomacy-carrv2017.pdf

<!-- > 更新: 2026-05-21 19:00:02  -->
> Updated: 2026-05-21 19:00:02
<!-- > 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/sqgnoxq546wl0e5v> -->
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/sqgnoxq546wl0e5v>
