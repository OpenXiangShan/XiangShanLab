# 第三章 Chisel 基础语法

## 前言

Chisel是一款基于Scala的硬件构建语言，兼顾高级语言的简洁性与硬件描述的精准性，广泛应用于RISC-V处理器、片上总线、数字电路设计场景。本教程从零起步，循序渐进讲解Chisel基础语法、核心数据类型、连线规则、控制语句、自定义接口与测试方法，配套完整代码示例与对应Verilog解析，适配新手零基础入门。

## Chisel基础模块与核心语法

### 1.1 最简模块：直通模块

基础Chisel模块必须继承`Module`基类，通过`IO(new Bundle)`定义端口，使用`:=`完成硬件连线。

#### Chisel示例代码

```scala
class Example extends Module {
  // 定义模块IO端口
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))  // 4比特无符号输入端口
    val out = Output(UInt(4.W))// 4比特无符号输出端口
  })
  // 硬件连线：输入直连输出
  io.out := io.in
}
```

#### 对应生成Verilog代码

```verilog
module Example(
  input        clock,
  input        reset,
  input  [3:0] io_in,
  output [3:0] io_out
);
  assign io_out = io_in;
endmodule
```

#### 核心知识点解析

* Chisel模块默认隐性包含`clock`和`reset`信号，无需手动定义，生成Verilog时自动引入；
* `val`：Scala/Chisel关键字，用于定义不可变硬件变量，所有硬件信号均使用`val`定义，禁止使用`var`；
* `Input/Output`：定义端口方向；`UInt(4.W)`：定义4比特无符号硬件信号；
* `:=`：硬件连线专用运算符，代表**右侧信号驱动左侧信号**，具备单向驱动属性。

### 1.2 连线变量：Wire线网

`Wire`用于定义组合逻辑线网，作为信号中转节点，不存储数据，仅用于信号传递，属于纯组合逻辑。

#### Chisel示例代码

```scala
class WireExample extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val out = Output(UInt(4.W))
  })
  // 定义4比特无符号线网
  val w = Wire(UInt(4.W))
  w := io.in    // 输入信号赋值给线网
  io.out := w   // 线网信号赋值给输出
}
```

该代码生成的硬件与直通模块完全一致，`Wire`仅作为信号中转，不改变硬件逻辑。

**语法简化**：中转Wire可直接简写为 `val w = io.in`，效果完全一致。

### 1.3 时序变量：Reg寄存器

`Reg`用于定义时序逻辑寄存器，依赖时钟信号，可存储数据，是时序电路的核心单元。

#### Chisel示例代码

```scala
class RegExample extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val out = Output(UInt(4.W))
  })
  // 定义4比特寄存器
  val r = Reg(UInt(4.W))
  r := io.in    // 时钟沿更新寄存器值
  io.out := r   // 寄存器输出连接模块输出
}
```

#### 对应生成Verilog代码

```verilog
module RegExample(
  input        clock,
  input        reset,
  input  [3:0] io_in,
  output [3:0] io_out
);
  reg [3:0] r;
  always @(posedge clock) begin
    r <= io_in;
  end
  assign io_out = r;
endmodule
```

### 1.4 初始化寄存器：RegInit

常规`Reg`无复位初始值，`RegInit`可实现复位时寄存器初始化，适配带复位的时序电路。

#### Chisel示例代码

```scala
class RegInitExample extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val out = Output(UInt(4.W))
  })
  // 复位时初始化为4比特0
  val r = RegInit(0.U(4.W))
  r := io.in
  io.out := r
}
```

#### 对应生成Verilog代码

```verilog
module RegInitExample(
  input        clock,
  input        reset,
  input  [3:0] io_in,
  output [3:0] io_out
);
  reg [3:0] r;
  always @(posedge clock) begin
    if (reset) begin
      r <= 4'h0;
    end else begin
      r <= io_in;
    end
  end
  assign io_out = r;
endmodule
```

### 1.5 核心符号区分：= 与 :=

* **=**：Scala语法，用于定义`val`变量、赋值常量，属于代码定义语法，无硬件连线意义；
* **:=**：Chisel硬件专属连线语法，单向驱动，用于硬件信号连接，是生成电路的核心语法。

## Chisel核心数据类型

### 2.1 UInt 无符号整数

`UInt`是最常用的硬件数据类型，用于定义无符号比特信号，支持线网、寄存器、常量定义。

#### 基础定义

```scala
val data  = Wire(UInt(10.W))  // 10比特无符号线网
val dataR = Reg(UInt(10.W))  // 10比特无符号寄存器
```

#### 常量定义

* 十进制：`5.U` / `5.U(4.W)`（指定4比特位宽）
* 二进制：`"b101".U`，支持下划线分隔可读性：`"b100_1010".U`

#### 典型示例：8位自增计数器

```scala
class Counter extends Module {
  val io = IO(new Bundle {})
  // 复位初始化为0，8比特计数器
  val cnt = RegInit(0.U(8.W))
  cnt := cnt + 1.U  // 每个时钟周期自增1
}
```

#### 比特提取

支持截取UInt任意区间比特，生成新信号：

```scala
val a = "b10010110".U
val b = a(3, 1)   // 截取bit1~bit3，b = "b011".U
```

### 2.2 Bool 布尔类型

`Bool`为1比特布尔信号，仅包含两种状态：`true.B`、`false.B`，多用于判断条件、标志位。

#### 基础使用

```scala
val a = Wire(Bool())
a := true.B

// 从UInt中提取单比特，自动转为Bool类型
val data = Reg(UInt(7.W))
val bit0 = data(0)
val bit2 = data(2)
```

#### 重要禁忌

**不支持直接修改UInt单比特**，以下代码编译报错：

```scala
val data = Wire(UInt(7.W))
data(2) := false.B  // 报错：只读信号无法赋值
```

替代方案：通过`Vec[Bool]`间接修改

```scala
val b = Wire(Vec(7, Bool()))
b(2) := true.B
val data = b.asUInt  // 转为UInt信号
```

#### Bool逻辑运算

```scala
val flag2 = flag0 && flag1  // 逻辑与
val flag3 = flag0 || flag1  // 逻辑或
val flag4 = !flag0          // 逻辑非
```

#### 信号比较运算

硬件信号比较必须使用专用运算符，区别于Scala语法：

* `===`：硬件相等比较，返回Bool
* `=/=`：硬件不等比较，返回Bool
* `==`：仅用于Scala语法比较，**不用于硬件信号**

```scala
val eq = data0 === data1  // 相等则为true.B
val ne = data0 =/= data1  // 不等则为true.B
```

### 2.3 Cat 信号拼接

`Cat`用于拼接多个UInt/Bool信号，生成新的宽位UInt信号，拼接顺序为**高位在前、低位在后**。

```scala
val a = Wire(UInt(3.W))
val b = Wire(Bool())
a := 2.U
b := true.B
val c = Cat(4.U(3.W), b, a)  // 拼接结果：100 1 010 = 1001010.U
```

### 2.4 Vec 数组信号

`Vec`用于定义同类型信号数组，支持固定索引、动态索引，适用于寄存器组、标志位组场景。

#### 基础定义

```scala
val flags = Wire(Vec(8, Bool()))    // 8组布尔线网
val data  = Reg(Vec(15, UInt(7.W))) // 15组7比特寄存器
```

#### 索引访问（支持动态索引）

```scala
flags(4) := true.B       // 固定索引赋值
data(0) := 3.U

val idx = Wire(UInt(4.W))
val res = data(idx)      // 动态索引读取
```

### 2.5 SInt 有符号整数

`SInt`用于定义带正负号的整数信号，支持负数赋值与运算。

```scala
val a = Wire(SInt(8.W))
a := -5.S  // 8比特有符号负数
```

## Chisel控制语句与选择器

### 3.1 Mux 二选一选择器

基础组合逻辑选择器，格式：`Mux(条件, 条件成立值, 条件不成立值)`，支持嵌套使用。

```scala
// 基础用法
val res = Mux(cond, x, y)

// 嵌套用法
val res = Mux(condA, x, Mux(condB, y, z))
```

### 3.2 when/elsewhen/otherwise 条件语句

对应Verilog的if/else语句，用于多分支组合/时序逻辑，是最常用的控制语句。

#### 标准示例

```scala
when (condA) {
  r := x
}.elsewhen (condB) {
  r := y
}.otherwise {
  r := z
}
```

#### 关键注意事项（避免锁存器）

若为`Wire`组合逻辑，必须补全`otherwise`或提前赋值默认值，否则会生成非法锁存器。

```scala
// 安全写法：先赋默认值，再覆盖
r := z
when (condB) { r := y }
when (condA) { r := x }
```

### 3.3 Mux1H 独热码选择器

位于`chisel3.util`工具库，专为\*\*独热码（One-Hot）\*\*场景设计，支持数组、UInt多选信号选择。

```scala
val oneHotConditions = Wire(Vec(4, Bool()))
val values = Wire(Vec(4, UInt(32.W)))
val result = Mux1H(oneHotConditions, values)
```

### 3.4 PriorityMux

#### 3.4.1 基本概念与原理

基础的 `Mux`组件仅支持二选一操作，但在实际硬件设计中，我们经常面临多选一的决策需求。解决这一问题的传统方法是**级联（Cascade）**：将 N个输入信号通过 N−1个二选一 Mux 串联起来。

然而，这种级联结构存在一个隐含的顺序问题：当多个控制信号同时有效时，应该优先响应哪一个？为了解决这种冲突，**PriorityMux（优先级多路选择器）** 被引入。

**核心机制**：

* **优先级排序**：控制信号具有明确的优先级。排在前面（通常在序列左侧或高位）的信号优先级最高。
* **胜出规则**：如果多个选择信号同时为 `1`（有效），PriorityMux 只会选中**优先级最高**的那个输入通道，而屏蔽掉所有低优先级的输入。

#### 3.4.2 代码实现分析 (`SeqUtils`)

在 Chisel3 中，`PriorityMux`的具体实现被封装在工具类 `chisel3.util.SeqUtils`中。它采用了**递归**的编程思想来构建选择逻辑树，核心函数 `priorityMux`的逻辑如下：

```plain
// 简化的 SeqUtils 内部实现
private[chisel3] object SeqUtils {
  /**
   * 返回第一个条件为真(predicate)的数据值
   */
  def priorityMux[T <: Data](in: Seq[(Bool, T)]): T = {
    if (in.size == 1) {
      // 递归出口 (Base Case)：只剩最后一个输入信号
      in.head._2 
    } else {
      // 递归体 (Recursive Case)：
      // 使用 Mux 判断当前第一个选择信号
      // 如果为真，选中当前数据；否则递归处理剩余的序列
      Mux(in.head._1, in.head._2, priorityMux(in.tail))
    }
  }
}
```

#### 3.4.3 API 使用示例

在实际开发中，开发者通常不需要手动构建 `(Bool, T)`元组序列，Chisel 提供了便捷的 `apply`方法重载。以下展示了如何使用 `PriorityMux`根据控制信号从多个输入中选择数据。

```plain
import chisel3._
import chisel3.util._

class PriorityMuxExample extends Module {
  val io = IO(new Bundle {
    val selector = Input(UInt(3.W)) // 3位选择信号 (可表示0~7)
    // 定义3个数据输入，UInt会自动转换为Bool作为选择条件
    val dataIn   = Input(Vec(3, UInt(8.W))) 
    val out      = Output(UInt(8.W))
  })

  // 直接使用 PriorityMux
  // 它会自动将 selector 的每一位与 dataIn 的对应元素配对
  // 例如：selector(0) -> dataIn(0), selector(1) -> dataIn(1)...
  io.out := PriorityMux(io.selector, io.dataIn)
  
  // 逻辑推演：
  // 若 selector = "b101".U (bit0=1, bit1=0, bit2=1)
  // 虽然 bit2 也为 1，但 bit0 优先级更高，最终 out = dataIn(0)
}
```

#### 3.4.4 衍生组件：PriorityEncoder

有了 `PriorityMux`，我们可以很方便地实现一个功能相反的组件——**PriorityEncoder（优先级编码器）**。

**功能定义**：

给定一个 `Bool`信号序列，PriorityEncoder 负责找出**优先级最高**的那个 `true`所在的**索引位置（位序号）**。

在实现上，Chisel 巧妙地复用了 `PriorityMux`：让它去选择在 0, 1, 2, ... 这些索引值。

```plain
// 示例：PriorityEncoder
val req = VecInit(false.B, true.B, false.B, true.B) // 请求信号
val encodedIndex = PriorityEncoder(req) // 结果为 1.U (索引1的优先级最高)
```

#### 3.4.5 核心知识点解析

1. **数据结构**：`PriorityMux`的底层输入是一个 `Seq[(Bool, T)]`序列，其中每个元素是一个（条件，数据）对。
2. **短路特性**：由于是组合逻辑，一旦高位条件满足，低位信号的计算路径实际上被“短路”屏蔽，这确保了逻辑的高效性。
3. **适用场景**：
   * **总线仲裁**：多个主设备（Master）请求总线时，根据固定优先级分配使用权。
   * **中断控制器**：处理多个中断源，优先响应高优先级中断。
   * **指令译码**：在控制单元中，根据指令字段的优先级匹配特定的微操作。

#### 3.4.6 核心开发规范

* **输入有效性**：使用 `PriorityMux`时，必须确保输入序列中**至少有一个条件为真**，或者通过 `Mux`为其提供一个明确的默认值。否则，在生成的硬件中，输出端口可能处于未定义的（X）状态。
* **类型与位宽**：序列中所有待选择的数据 `T`必须具有相同的 Chisel 数据类型（如都是 `UInt(8.W)`），否则编译器会报类型不匹配错误。
* **优先级理解**：明确设计中的优先级顺序是固定的（由序列顺序或信号位序决定），这与可配置的仲裁器（Arbiter）不同。

### 3.5 OneHot 编码与转换

#### **3.5.1 核心定义与功能**

* **OneHot（独热）编码**：在一个 N位向量中，有且仅有一位为 `1`。其值由“1”所在的位置表示。例如，`"b0010".U`表示状态 2。

1. <code>**UIntToOH**</code>**（编码/译码）**：将普通索引转换为 OneHot 向量。

```scala
import chisel3.util._
val idx = 2.U(3.W)      // 普通索引
val oneHot = UIntToOH(idx) // 得到 "b00000100".U
```

*原理*：`1.U << idx`。

2. <code>**OHToUInt**</code>**（解码/编码）**：将 OneHot 向量转换回普通索引。

```scala
val oneHotSig = "b00010000".U
val decodedIdx = OHToUInt(oneHotSig) // 得到 4.U
```

**核心应用**：

* 与 <code>**Mux1H**</code> 配合，实现基于 OneHot 信号的高效数据选择：

```scala
val selOneHot = UIntToOH(selector, 4)
val selectedData = Mux1H(selOneHot, dataVec)
```

**主要场景**：

1. **状态机编码**：OneHot 状态机在 FPGA 中时序更优。
2. **仲裁与选择**：用于交叉开关、多路复用器，明确选择单一源。

**核心规范**：

* **合法性**：关键路径建议用 `PopCount`验证输入是否为合法 OneHot（仅一位为1）。
* **位宽**：使用 `UIntToOH(in, width)`指定输出位宽，避免位宽爆炸。
* **无锁存器**：确保组合逻辑在所有条件下都有定义。

## Bundle自定义接口类型

`Bundle`是Chisel自定义接口核心，可将多个不同类型、不同位宽的信号封装为一组接口，适配复杂模块IO。

### 4.1 基础Bundle定义

```scala
class A extends Bundle {
  val x = Bool()
  val y = UInt(2.W)
  val z = UInt(4.W)
}

// 信号调用
val test = new A
test.x := true.B
test.z := 9.U
```

### 4.2 Bundle嵌套定义

```scala
class B extends Bundle {
  val ctrlA = new A  // 嵌套自定义Bundle
  val u = Bool()
}
```

### 4.3 Bundle寄存器初始化

#### 复位置零（常用）

```scala
val regA = RegInit(0.U.asTypeOf(new A))
```

#### 复位自定义初值（多用于测试）

```scala
import chisel3.experimental.BundleLiterals._
val regA = RegInit((new A).Lit(
  _.x -> true.B,
  _.y -> 1.U,
  _.z -> 7.U
))
```

### 4.4 带方向Bundle与Flipped反转

`Flipped`可快速反转Bundle信号方向，双向接口互联时需使用`<>`连线，替代`:=`。

```scala
class C extends Bundle {
  val p = Input(Bool())
  val q = Output(UInt(4.W))
}

class FlipExample extends Module {
  val io = IO(new Bundle() {
    val port1 = new C
    val port2 = Flipped(new C) // 所有信号方向反转
  })
  io.port1 <> io.port2 // 双向接口专用连线符
}
```

### 4.5 Valid/Decoupled握手接口

Chisel内置标准流水线握手接口，适配数据流传输场景。

* **Valid**：包含`valid`有效标志 + `bits`数据
* **Decoupled**：标准握手，包含`valid`、`ready`、`bits`
* `fire`：握手成功标志，等价于`valid && ready`

```scala
// Valid接口
val outWithValid = Valid(new A)
val validFlag = outWithValid.valid
val dataBits  = outWithValid.bits

// Decoupled标准握手接口
val in = Decoupled(new A)
in.ready := true.B
val handshakeFire = in.fire
```

## Chisel基础测试方法

Chisel内置单元测试框架，通过`poke`赋值输入、`expect`校验输出、`peek`读取信号，快速验证电路功能。

```scala
test(new Example) { c =>
  c.io.in.poke(0.U)     // 输入赋值0
  c.io.out.expect(0.U)  // 断言输出为0
  c.io.in.poke(1.U)     // 输入赋值1
  c.io.out.expect(1.U)  // 断言输出为1
  c.io.in.poke(2.U)
  c.io.out.expect(2.U)
}
println("SUCCESS!!")
```

#### 测试核心API

* `poke()`：给模块输入信号赋值
* `expect()`：校验输出信号是否符合预期（断言）
* `peek()`：读取信号值，不做断言校验

## 核心开发规范总结

1. 所有硬件信号统一使用`val`定义，禁止使用Scala`var`；
2. 硬件连线必须使用`:=`，双向接口互联使用`<>`；
3. 组合逻辑必须补全默认值，杜绝非法锁存器；
4. 硬件信号比较统一使用`===`、`=/=`，区分Scala语法`==`；
5. 时序电路优先使用`RegInit`配置复位初值，保证电路可收敛；
6. 复杂接口统一使用Bundle封装，配合Valid/Decoupled实现标准化数据流。


> 更新: 2026-05-27 10:40:51  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/mwg7b5s0523ep5pt>
