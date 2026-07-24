# 5. Move 指令消除

:::info

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">🧭</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 学习目标</font>

* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">理解</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：Move 指令消除的核心思想与性能收益</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">掌握</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：香山处理器中 Move 消除的实现流程</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">了解</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：引用计数机制的工作原理</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">能够</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：在源码中定位 Move 消除相关逻辑</font>

:::

***

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.1 什么是 Move 指令消除 </font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">📋</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.1.1 Move 指令的本质</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">在 RISC-V 架构中，</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">mv rd, rs1</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">并不是一条独立的硬件指令，而是</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">addi rd, rs1, 0</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">的汇编助记符。它的唯一功能是将一个寄存器的值复制到另一个寄存器。</font>

```plain
# 典型的Move指令序列
add x1, x2, x3    # x1 = x2 + x3
mv  x4, x1        # x4 = x1 (需要1拍执行)
add x5, x4, x6    # x5 = x4 + x6 (依赖x4)
```

:::warning
⚠️<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">warning 性能瓶颈 传统处理器执行 Move 指令需要：</font>

1. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">从 FreeList 分配一个新的物理寄存器</font>
2. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">占用一个执行单元进行 "加 0" 运算</font>
3. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">引入至少 1 拍的数据依赖延迟 这对于频繁出现的 Move 指令来说是巨大的性能浪费。 </font>

:::

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.1.2 智能工厂类比</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">我们继续使用 "智能工厂" 的类比来理解 Move 消除：</font>

* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> = 产品生产订单</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">逻辑寄存器</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> = 产品型号</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">物理寄存器</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> = 仓库货架</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Move 指令</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> = "将 A 货架的产品复制到 B 货架" 的搬运订单</font>

**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">传统处理方式</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：专门安排工人将产品从 A 货架搬到 B 货架，占用人力和时间。</font>

**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Move 消除方式</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：直接在系统中记录 "B 型号产品现在存放在 A 货架"，不需要实际搬运。</font>

***

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.2 香山处理器的实现原理 </font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">💡</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.2.1 核心设计思想</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理器在</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">重命名阶段</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">实现 Move 指令消除，核心思想非常简单：</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">对 Move 指令不再分配新的物理寄存器，而是直接将其目的逻辑寄存器重命名至源操作数对应的物理寄存器。</font>[**<font style="color:#8A8F8D;background-color:rgba(0, 0, 0, 0.04);">香山</font>**](https://docs.xiangshan.cc/projects/design/zh-cn/nanhu/backend/rename/?f_link_type=f_linkinlinenote\&flow_extra=eyJpbmxpbmVfZGlzcGxheV9wb3NpdGlvbiI6MCwiZG9jX3Bvc2l0aW9uIjowLCJkb2NfaWQiOiJiM2RlM2YyYWEyODQwYjZlLWNkMWU5M2E5MDE4OTgzNmUifQ%3D%3D)

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">这样，Move 指令本身不需要进入执行阶段，直接在重命名阶段就被 "消除" 了。</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.2.2 执行流程对比</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">我们用一个具体的例子来对比两种处理方式：</font>

```plain
指令序列：
1. add x1, x2, x3
2. mv  x4, x1
3. add x5, x4, x6
```

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">传统处理方式（无 Move 消除）</font>

```plain
逻辑寄存器映射：
x1 → P100 (分配新物理寄存器)
x4 → P101 (分配新物理寄存器)
x5 → P102 (分配新物理寄存器)

执行时序：
周期1: add x1, x2, x3 → 写入P100
周期2: mv  x4, x1     → 从P100读取，写入P101
周期3: add x5, x4, x6 → 从P101读取，计算后写入P102
```

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理方式（有 Move 消除）</font>

```plain
逻辑寄存器映射：
x1 → P100 (分配新物理寄存器)
x4 → P100 (直接指向源操作数的物理寄存器)
x5 → P101 (分配新物理寄存器)

执行时序：
周期1: add x1, x2, x3 → 写入P100
周期2: (无操作，Move指令被消除)
周期2: add x5, x4, x6 → 直接从P100读取，计算后写入P101
```

:::color3
💡<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">关键收益</font>

* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">延迟减少</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：第 3 条指令可以提前 1 拍执行</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">资源节省</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：节省了一个物理寄存器和一个执行单元的占用</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">ILP 提升</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：消除了虚假的数据依赖，提高了指令级并行度 </font>

:::

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.2.3 可消除的指令类型</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理器不仅支持标准的</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">mv</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令消除，还支持以下指令的消除：</font>

| **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令类型</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">汇编示例</font>** | **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">消除条件</font>** |
| :--- | :--- | :--- |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">整数移动</font> | <code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">mv rd, rs1</font></code> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">无条件</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">立即数加载</font> | <code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">li rd, 0</font></code> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">立即数为 0 时，直接指向 x0 对应的物理寄存器</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">逻辑操作</font> | <code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">or rd, rs1, x0</font></code> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">第二个源操作数为 x0</font> |
| <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">浮点移动</font> | <code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">fmv.s rd, rs1</font></code> | <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">无条件</font> |

:::color4
🚨<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">易错点 不是所有看起来像 "移动" 的指令都能被消除：</font>

* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">❌</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">mv x0, rs1</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：目的寄存器是 x0，不能被重命名</font>
* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">❌</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">lw rd, 0(rs1)</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：这是访存指令，不是寄存器到寄存器的移动</font>
* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">❌</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">add rd, rs1, rs2</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：有两个源操作数，不是简单的移动</font>

:::

***

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.3 引用计数机制 </font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">⚙️</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.3.1 为什么需要引用计数</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Move 消除带来了一个新问题：</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">一个物理寄存器可能被多个逻辑寄存器同时引用</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">。</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">在没有 Move 消除的情况下，物理寄存器的释放规则很简单：当指令提交时，释放该指令之前占用的旧物理寄存器。</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">但在有 Move 消除的情况下，如果我们仍然按照这个规则释放物理寄存器，就会导致其他还在引用这个物理寄存器的逻辑寄存器读到错误的值。</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.3.2 香山的解决方案</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理器为每个物理寄存器维护一个</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">引用计数器</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，记录有多少个逻辑寄存器正在引用这个物理寄存器。</font>**<font style="color:rgba(0, 0, 0, 0.5);background-color:rgba(0, 0, 0, 0.04);"></font>**

```plain
物理寄存器状态表：
物理寄存器 | 引用计数 | 状态
-----------|----------|------
P100       | 2        | 已提交 (被x1和x4同时引用)
P101       | 1        | 等待执行
P102       | 0        | 空闲
```

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">物理寄存器的释放规则变为：</font>

1. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">当指令提交时，将其旧物理寄存器的引用计数减 1</font>
2. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">当引用计数变为 0 时，将该物理寄存器归还到 FreeList</font>

:::success <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font>❤️<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">新手入门 现阶段你只需要记住：引用计数是为了解决 "一个物理寄存器被多个逻辑寄存器引用" 的问题。具体的实现细节可以在你掌握了重命名的基本流程后再深入研究。 </font>

:::

***

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.4 性能影响与限制 </font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">⚠️</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.4.1 性能收益</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Move 指令消除在实际程序中能带来显著的性能提升：</font>

* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">典型的 SPEC CPU2006 程序中，Move 指令占比约为 10%-15%</font>
* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">消除这些指令可以带来约 5%-8% 的整体性能提升</font>
* <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">在函数调用频繁的程序中，收益更加明显（因为函数调用会产生大量的参数传递 Move 指令）</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.4.2 设计权衡</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Move 消除也不是没有代价的：</font>

* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">硬件复杂度增加</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：需要额外的电路来识别可消除的指令</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">引用计数开销</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：每个物理寄存器需要额外的几位来存储引用计数</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">释放逻辑复杂化</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：物理寄存器的释放不再是简单的单周期操作</font>

:::color3 <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> </font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">⚠️</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">性能权衡 香山处理器的设计选择是：用少量的硬件复杂度增加，换取显著的性能提升。这是现代高性能处理器的普遍设计思路。</font>

:::

***

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.5 源码导航 </font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">🔍</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.5.1 核心模块位置</font>

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Move 指令消除的逻辑主要位于重命名模块中：</font>

| 功能 | 源码路径（精确到文件） | 核心类 |
| --- | --- | --- |
| Move 指令识别与消除主逻辑 | `src/main/scala/xiangshan/backend/rename/Rename.scala` | `Rename` |
| 重命名表（RAT）维护 | `src/main/scala/xiangshan/backend/rename/RenameTable.scala` | `RenameTable` |
| 整数空闲寄存器管理 | `src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala` | `MEFreeList` |
| 浮点 / 向量空闲寄存器管理 | `src/main/scala/xiangshan/backend/rename/freelist/StdFreeList.scala` | `StdFreeList` |
| 指令压缩与 ROB 压缩 | `src/main/scala/xiangshan/backend/rename/CompressUnit.scala` | `CompressUnit` |
| 快照管理 | `src/main/scala/xiangshan/backend/rename/Snapshot.scala` | `Snapshot` |

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">5.5.2 关键代码片段</font>

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">1. Rename 阶段 Move 指令判断与异常处理</font>

```scala
// 在Rename.scala中，接收Decode阶段传递的isMove信号
val isMove = Wire(Vec(RenameWidth, Bool()))
isMove zip io.in.map(_.bits) foreach {
  case (move, in) => 
  // 关键：有异常的指令不能进行Move消除
  move := Mux(in.exceptionVec.asUInt.orR, false.B, in.isMove)
}
```

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">2. 物理寄存器分配控制（核心优化）</font>

```scala
// 整数物理寄存器分配请求：只有非Move指令才需要分配新寄存器
intFreeList.io.allocateReq(i) := needIntDest(i) && !isMove(i)

// 浮点/向量寄存器暂不支持Move消除，仍需正常分配
fpFreeList.io.allocateReq(i) := needFpDest(i)
vecFreeList.io.allocateReq(i) := needVecDest(i)
v0FreeList.io.allocateReq(i) := needV0Dest(i)
vlFreeList.io.allocateReq(i) := needVlDest(i)
```

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">3. 目的寄存器重命名（直接指向源物理寄存器）</font>

```scala
// 第一条指令的pdest处理
io.out(0).bits.pdest := Mux(isMove(0), uops(0).psrc.head, uops(0).pdest)

// 后续指令的pdest处理（包含同周期旁路逻辑）
for (i <- 1 until RenameWidth) {
  // 先处理同周期前序指令的旁路
  io.out(i).bits.psrc(0) := io.out.take(i).map(_.bits.pdest)
  .zip(bypassCond(0)(i-1).asBools)
  .foldLeft(uops(i).psrc(0))((z, next) => Mux(next._2, next._1, z))

  // 关键：Move指令直接使用源操作数的物理寄存器作为目的寄存器
  io.out(i).bits.pdest := Mux(isMove(i), io.out(i).bits.psrc(0), uops(i).pdest)
}
```

#### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">4. 指令状态标记（标记为已消除，不进入执行阶段）</font>

```scala
// 标记指令为Move指令且已被消除
uops(i).eliminatedMove := isMove(i)
uops(i).isMove := isMove(i)

// Move指令不需要执行，将numUops和numWB设为0
when(isMove(i) || hasExceptionExceptFlushPipe) {
  uops(i).numUops := 0.U
  uops(i).numWB := 0.U
}
```

💡<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">源码提示 你可以在 GitHub 上直接查看最新的源码：</font>[<font style="color:rgb(0, 102, 255);">香山处理器重命名模块</font>](https://link.wtturl.cn/?target=https%3A%2F%2Fgithub.com%2FOpenXiangShan%2FXiangShan%2Ftree%2Fmaster%2Fsrc%2Fmain%2Fscala%2Fxiangshan%2Fbackend%2Frename\&scene=im\&aid=582478\&lang=zh)

***

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">✅</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 核心要点总结</font>

* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Move 指令识别</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：在</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">Decode 阶段通过译码表直接标记</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，Rename 阶段只需要读取</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">in.isMove</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">字段</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">异常处理</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：有异常的指令不能进行 Move 消除，会被当作普通指令处理</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">物理寄存器分配</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：只有非 Move 指令才需要从 FreeList 分配新的物理寄存器</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">目的寄存器重命名</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：Move 指令直接将目的逻辑寄存器指向源操作数的物理寄存器</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令状态</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：Move 指令被标记为</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">eliminatedMove = true</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">numUops = 0</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">，不会进入执行阶段</font>
* **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">性能统计</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：通过</font><code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">move_instr_count</font></code><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">计数器可以统计 Move 消除的效果</font>

***

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">📚</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);"> 扩展阅读</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">官方文档</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：</font>[<font style="color:rgb(0, 102, 255);">香山处理器重命名模块设计文档</font>](https://link.wtturl.cn/?target=https%3A%2F%2Fdocs.xiangshan.cc%2Fzh-cn%2Flatest%2Fbackend%2Frename%2F\&scene=im\&aid=582478\&lang=zh)
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">经典论文</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：RENO: A Rename-Based Instruction Optimizer (ISCA 2005)</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">相关章节</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：下一章我们将学习 "指令融合" 技术，它与 Move 消除都是在重命名阶段实现的重要优化</font>


> 更新: 2026-05-29 16:45:29  
