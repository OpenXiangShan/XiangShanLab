# 第十章 Diplomacy 基础简介

Diplomacy与CDE超通俗入门教程（是什么/为什么/怎么做）

## 前言

为降低入门门槛，全文摒弃晦涩源码堆砌，统一采用**是什么、为什么、怎么做**的逻辑拆解，搭配极简通俗示例，聚焦核心学习要点。

**本文不包含**：零基础上手实操步骤、完整项目代码演练。如需实操学习，可结合 Rocket-chip、香山（XiangShan）、缓存仓库（HuanCun）源码、官方例程与大佬分享视频进阶。

**核心学习目标**：搞懂两大框架的核心作用、运行逻辑、核心组件、使用场景，建立Chisel大规模SoC设计的参数化思维。

## 核心前置认知

传统Verilog/原生Chisel开发的痛点：硬件模块参数固定、模块互联参数需人工手动对齐、复杂流水线/SoC拓扑下参数耦合严重，代码复用性极差，牵一发而动全身。

RISC-V高端开源芯片（Rocket、香山）的解决方案：**CDE负责全局静态参数配置，Diplomacy负责模块互联动态参数协商**，二者配合实现芯片设计的**参数化、可配置、高复用**。

# Diplomacy 框架详解

## 1.1 什么是Diplomacy？（核心定义）

**官方定义**：Diplomacy is a parameter negotiation framework for generating parameterized protocol implementations.

**通俗翻译**：Diplomacy是一套**模块互联参数自动协商框架**，专门解决多个硬件模块连接时，端口参数、协议参数不匹配的问题，自动完成参数对齐，最终生成标准化的协议硬件电路。

**核心定位**：专注**模块之间**的拓扑连接与参数协商，是SoC总线、流水线、多级缓存互联的核心底层框架，TileLink总线协议完全基于Diplomacy扩展实现。

## 1.2 为什么需要Diplomacy？（解决的核心问题）

### 传统开发痛点

1. 模块参数硬编码，修改一个参数需要改动多个关联模块，无复用性；

2. 多模块互联时，端口位宽、传输大小、缓存属性等参数需人工逐一核对，极易出错；

3. 复杂乱序流水线、多级缓存、多核SoC场景下，参数耦合极其复杂，人工协商参数成本极高、可读性极差、难以维护。

### Diplomacy的核心价值

1. **自动化参数协商**：无需人工手动计算对齐参数，框架自动根据用户定义规则完成上下游参数匹配；

2. **拓扑可视化管理**：将所有硬件模块抽象为有向无环图（DAG），模块即节点、连线即边，清晰管理SoC整体拓扑；

3. **延迟实例化**：利用Scala懒加载特性，先完成所有参数协商，再生成最终RTL硬件电路，避免参数冲突；

4. **协议标准化**：支撑TileLink、AXI4等总线协议的参数化扩展，适配各类复杂片上互联场景。

## 1.3 Diplomacy核心核心知识点（必学）

Diplomacy所有功能围绕四大核心要素展开：**LazyModule、Node、Edge、参数协商规则**，以下逐个拆解，搭配极简示例。

### 知识点1：LazyModule 懒加载模块（基础载体）

#### 是什么

LazyModule是Diplomacy的**最小模块单元**，所有支持参数协商的硬件模块，都必须继承该类。

它将模块拆分为两个阶段，彻底实现「参数协商」与「硬件生成」解耦：

1. **协商阶段（非懒加载）**：定义子模块、拓扑节点、参数协商规则，完成所有参数对齐；

2. **RTL生成阶段（懒加载lazy）**：参数协商完成后，通过`LazyModuleImp`实例化最终硬件电路。

#### 为什么

传统Chisel模块实例化和参数定义同步进行，无法实现跨模块参数预协商；LazyModule的延迟实例化特性，是Diplomacy自动参数协商的基础。

#### 怎么做（极简示例）

定义一个可参与Diplomacy参数协商的基础模块，固定代码结构：

```scala
// 所有Diplomacy模块必须隐式携带全局参数上下文Parameters
class MyDiplomacyModule(implicit p: Parameters) extends LazyModule {
  // 1. 协商阶段：定义节点、子模块、参数规则（无硬件生成）
  val myNode = TLIdentityNode() 

  // 2. 懒加载RTL：参数协商完成后，才会执行硬件实例化
  lazy val module = new LazyModuleImp(this) {
    // 最终硬件逻辑，可使用协商完成的参数
    val (tl, edge) = myNode.in(0)
  }
}
```

### 知识点2：Node 拓扑节点（核心协商单元）

#### 是什么

Node是Diplomacy拓扑图的**核心节点**，不对应具体硬件IO，专门负责：参数定义、参数协商、协议属性描述、模块互联对接。

所有模块的互联、参数传递、协议适配，全部通过Node完成，是Diplomacy的灵魂。

#### 核心分类（重点掌握6类常用节点）

根据数据流方向和功能，官方划分6类核心节点，适配所有SoC互联场景：

1. **ClientNode（客户端节点）**：主动发起请求的模块（CPU、DMA、L1缓存），仅输出数据流、下发参数；
2. **ManagerNode（管理端节点）**：接收请求、返回响应的模块（外设、内存、寄存器），仅接收数据流、接收上游参数；
3. **RegisterNode（寄存器节点）**：简化版Manager节点，自动生成寄存器读写逻辑，无需手动处理TileLink协议；
4. **IdentityNode（恒等节点）**：纯转发节点，不修改参数、不修改数据流，用于合并/拆分多个模块节点；
5. **AdapterNode（适配节点）**：可修改上下游参数/协议，不改变连接边数量，用于协议转换、参数适配（如缓存参数重配置）；
6. **NexusNode（汇聚节点）**：支持输入输出边数量不匹配，用于总线交叉开关、多模块汇聚场景（如TLXbar总线）。

#### 怎么做（极简示例-IdentityNode多模块合并）

场景：将两个客户端模块合并为一个统一对外节点，无需修改内部逻辑：

```scala
class ClientGroup(implicit p: Parameters) extends LazyModule {
  // 实例化两个独立客户端
  val client1 = LazyModule(new MyClient)
  val client2 = LazyModule(new MyClient)
  // 定义恒等转发节点
  val node = TLIdentityNode()
  // 绑定多个子节点，自动合并拓扑
  node := client1.node
  node := client2.node

  lazy val module = new LazyModuleImp(this) { /* 无需额外逻辑，自动转发 */ }
}
```

### 知识点3：NodeImp 节点实现器（参数协商规则核心）

#### 是什么

NodeImp是**参数协商规则的定义模板**（全局单例object），所有Node的参数协商方式、硬件端口类型、协议属性，都由NodeImp统一规定。

核心泛型四元组（所有协商的基础）：`[D向下参数, U向上参数, E边参数, B硬件Bundle类型]`

* D（DownwardParam）：从上位模块向下传递的参数；
* U（UpwardParam）：从下位模块向上反馈的参数；
* E（EdgeParam）：上下游协商后，最终生效的边参数；
* B（Bundle）：最终生成的硬件端口类型。

#### 为什么

不同硬件模块、不同总线协议的参数协商规则不同，通过NodeImp统一封装规则，实现节点复用、协议可扩展。

#### 怎么做（极简示例-位宽协商规则）

定义一个简单的位宽协商规则：上下游位宽不匹配时，取最小值作为最终硬件位宽：

```scala
// 1. 定义三类参数结构体
case class DownwardParam(width: Int) // 向下传递位宽
case class UpwardParam(width: Int)   // 向上反馈位宽
case class EdgeParam(width: Int)     // 最终协商位宽

// 2. 定义节点协商规则
object AdderNodeImp extends SimpleNodeImp[DownwardParam, UpwardParam, EdgeParam, UInt] {
  // 核心协商逻辑：取上下游位宽最小值
  def edge(pd: DownwardParam, pu: UpwardParam, p: Parameters, sourceInfo: SourceInfo) = {
    if (pd.width < pu.width) EdgeParam(pd.width) else EdgeParam(pu.width)
  }
  // 根据协商参数生成硬件端口
  def bundle(e: EdgeParam) = UInt(e.width.W)
  // 拓扑渲染参数（可视化用）
  def render(e: EdgeParam) = RenderedEdge("blue", s"width = ${e.width}")
}
```

### 知识点4：方向体系（最易混淆核心点）

#### 是什么

Diplomacy的参数/数据流存在**两套独立方向维度**，两两组合形成4种参数场景，是拓扑协商的核心逻辑：

1. **内外方向（In/Out）**：相对于当前节点，数据流入为In、流出为Out；
2. **上下方向（Up/Down）**：相对于拓扑整体，从Master（主设备）到Slave（从设备）为Down，反向为Up。

组合场景：UI(上入)、DI(下入)、UO(上出)、DO(下出)

#### 通俗理解

以CPU访问外设为例：CPU（Master）→ 总线 → 外设（Slave），数据流、参数全部向下（Down）；外设返回数据、属性参数全部向上（Up）。

### 知识点5：拓扑连接运算符

#### 是什么

Diplomacy提供4种专属连接运算符，用于节点之间的拓扑绑定，自动生成对应数量的连接边：

1. **:=** 基础连接：两个节点之间生成**单条**连接边；
2. **:=**\* 客户端驱动多连接：边数量由右侧客户端节点决定；
3. **:\*=** 管理端驱动多连接：边数量由左侧管理端节点决定；
4. **:*=*** 自适应多连接：自动根据两侧节点边数量匹配，适配动态拓扑。

#### 怎么做（示例）

```scala
// 单条边基础连接
manager.node := client.node
// 自适应多边连接（多模块批量绑定）
manager.node :=* clientGroup.node
```

### 知识点6：核心协商函数dFn/uFn

#### 是什么

所有可协商节点（Adapter/Nexus）的核心逻辑函数，是**参数协商的真正执行者**：

* **dFn**：处理向下流动的参数，定义下游参数转换、匹配规则；
* **uFn**：处理向上流动的参数，定义上游参数转换、匹配规则。

最终所有参数经过dFn/uFn处理后，生成稳定的Edge边参数，再映射为硬件Bundle端口。

# CDE 参数配置框架详解

## 2.1 什么是CDE？（核心定义）

**CDE全称Context-Dependent Environments**，是一套**全局层级化Key-Value参数传递框架**。

**通俗翻译**：CDE专门负责整个SoC的**全局静态参数配置**，可以在模块层级中自上而下传递参数，支持不同模块、不同核配置差异化参数，解决全局参数管理问题。

**核心定位**：Diplomacy管「模块互联动态协商」，CDE管「全局静态参数配置」，二者互补，共同支撑芯片参数化设计。

## 2.2 为什么需要CDE？（解决的核心问题）

### 传统参数配置痛点

1. 全局参数繁多，参数名易冲突，无统一命名空间；

2. 多层级模块中，子模块无法灵活继承、覆盖父模块参数；

3. 多核、多模块差异化配置困难，一套代码无法生成多组不同配置的硬件；

4. 无法根据模块上下文（位置、层级）动态适配参数。

### CDE核心价值

1. **命名空间隔离**：通过Field唯一Key区分参数，彻底解决参数冲突；

2. **层级覆盖**：支持父模块默认参数、子模块按需重写，灵活适配差异化需求；

3. **上下文感知**：支持根据模块所在位置、层级动态生成参数；

4. **可组合扩展**：多个配置文件可自由拼接、覆盖，实现设计空间灵活迭代。

## 2.3 CDE核心知识点（必学）

### 知识点1：核心三要素（Field、Parameters、Config）

#### 是什么

1. **Field\[T] 参数键**：参数的唯一标识，定义参数类型、默认值，相当于Map的Key；
2. **Parameters 参数上下文**：全局参数载体，所有模块通过隐式参数`p: Parameters`获取配置；
3. **Config 配置类**：参数键值对的具体实现，定义每个Field对应的具体数值。

#### 怎么做（极简示例）

```scala
// 1. 定义参数Key（指定类型、默认值）
case object CoreWidth extends Field[Int](32) // 核心位宽，默认32位
case object CoreName extends Field[String]("Rocket") // 核心名称

// 2. 定义自定义配置
val MyCoreConfig: Parameters = Config((site, here, up) => {
  case CoreWidth => 64  // 覆盖默认值，改为64位
  case CoreName => "XiangShan"
})

// 3. 模块中获取全局参数
class CoreModule(implicit p: Parameters) extends Module {
  val width = p(CoreWidth) // 读取配置：64
  val name = p(CoreName)   // 读取配置：XiangShan
}
```

### 知识点2：三大上下文变量（site/here/up）

这是CDE**上下文依赖**的核心，实现参数动态适配，三者作用：

1. **here**：当前配置层的参数视图，仅查询当前Config的参数；
2. **up**：上层配置层的参数视图，查询父级、全局默认参数；
3. **site**：全局完整配置视图，查询所有层级的最终生效参数。

#### 通俗示例

子模块需要基于全局默认参数，局部微调：

```scala
val SubConfig: Parameters = Config((site, here, up) => {
  // 基于全局位宽，动态调整
  case CoreWidth => up(CoreWidth) / 2 
})
```

### 知识点3：配置组合规则（alter/orElse）

多个Config可自由组合，核心两个方法，优先级规则是重点：

1. **x.alter(y)**：y优先级高于x，先查y、再查x；
2. **x.orElse(y)**：x优先级高于y，先查x、再查y；

查询逻辑：**自顶向下遍历配置层，找到第一个定义的参数即返回**。

#### 简化记忆

`alter`：右边覆盖左边；`orElse`：左边覆盖右边。

# Diplomacy与CDE 区别与配合关系

| **框架** | **核心作用** | **作用阶段** | **作用范围** |
| --- | --- | --- | --- |
| CDE | 全局静态参数配置、层级参数传递 | 模块初始化阶段 | 全局所有模块通用参数 |
| Diplomacy | 模块互联动态参数协商、拓扑管理 | 模块绑定互联阶段 | 模块间总线、端口、协议参数 |

**完整工作流程**：

1. CDE加载全局Config，给所有模块分配基础静态参数；

2. Diplomacy基于CDE基础参数，通过Node、Edge完成多模块互联参数协商；

3. 所有参数协商完成后，统一生成最终可综合RTL硬件电路。

# 进阶学习资源（精准对标）

## 源码资源

* Diplomacy核心源码：rocket-chip/src/main/scala/diplomacy（LazyModule、Nodes核心实现）
* Diplomacy官方教程：rocket-chip/docs/src/diplomacy/adder\_tutorial.md（最简加法器协商示例）
* CDE核心源码：cde/cde/src/chipsalliance/rocketchip/config.scala
* 工程实战参考：香山SoC、HuanCun缓存模块（工业级参数协商实战）

## 视频资源

* Diplomacy入门：https://www.bilibili.com/video/BV17r4y1i7qt
* CDE参数配置入门：https://www.bilibili.com/video/BV1Ao4y1U74F

## 论文文档

Diplomacy官方论文：https://carrv.github.io/2017/papers/cook-diplomacy-carrv2017.pdf


> 更新: 2026-05-21 19:00:02  
