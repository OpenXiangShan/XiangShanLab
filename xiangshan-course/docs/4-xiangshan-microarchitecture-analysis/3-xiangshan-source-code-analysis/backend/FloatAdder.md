> 源码版本：
>
> Yunsuan：215839f51983bf8d1c2be0107866016749cf0f1b。

# 1. 标量浮点加法

文件路径：

XiangShan/yunsuan/src/main/scala/yunsuan/fpu/FloatAdder.scala

XiangShan/yunsuan/src/main/scala/yunsuan/vector/VectorFloatAdder.scala

## 1.1 Scalar Floating-Point Addition（标量浮点加法）

> 如果你第一次阅读香山浮点执行单元的源码，可能会同时遇到指令译码、浮点寄存器、FALU、IEEE-754、far path、close path 和 `fflags` 等概念。不要把它们看成互相独立的模块：一条 `fadd.s` 或 `fadd.d` 会把这些模块串成一条完整的数据通路。

### 1.1.1 先建立整体认识

一条标量浮点加法的大致数据流如下：

```plain
┌────────────── 前端/译码 ──────────────┐
│  指令字 → FADD_S/FADD_D               │
│          src1=fp, src2=fp            │
│          fu=falu, fpWen=1            │
└────────────────┬─────────────────────┘
                 ↓
┌─────────── 后端数据通路 ───────────────┐
│  浮点寄存器重命名、读取、唤醒、发射        │
│  src0/src1 = 实际浮点数据              │
└────────────────┬─────────────────────┘
                 ↓
┌────────────── 标量 FALU ──────────────┐
│  FALU.scala → FloatAdder.scala       │
│  格式选择 → 特殊值分类                  │
│  指数比较 → far path/close path        │
│  规格化 → 舍入 → fflags                │
└────────────────┬─────────────────────┘
                 ↓
       浮点结果 + 异常标志 → 写回
```

> **图表解读：** 指令编码只负责描述“使用哪些浮点寄存器、执行什么格式的加法”。真正的浮点数值是在后端寄存器数据通路中准备好，再传给 FALU。浮点加法器完成计算后，结果和 `fflags` 一起进入写回路径。

### 1.1.2 阅读路线

* 第 2 节只讲 IEEE-754 加法、路径分流和舍入的数学原理。
* 从第 3 节开始，结合香山 `FloatAdder` 与向量浮点运算源码分析硬件实现。
* 最后通过具体数值例子连接理论结果与 RTL 行为。


# 2. IEEE-754 浮点加法：理论算法

本节只介绍数学表示和算法，不引用 Chisel 代码、信号名或源码行号。后续章节再把这些步骤映射到香山的模块与 RTL。

## 2.1 浮点数表示

二进制浮点数由符号、指数和 fraction（小数部分）组成。对正规数，其数值可写成：

```text
(-1)^s × (1.f)₂ × 2^(E - bias)
```

其中 `s` 是符号位，`E` 是偏置指数，`f` 是 fraction；`1.f` 中的前导 1 是隐含位。对非正规数，前导位为 0，指数采用最小有效指数，因而可表示接近零的数。

| 格式 | 符号位 | 指数位 | fraction 位 | 有效精度 |
|---|---:|---:|---:|---:|
| FP16 | 1 | 5 | 10 | 11 位（二进制） |
| FP32 | 1 | 8 | 23 | 24 位（二进制） |
| FP64 | 1 | 11 | 52 | 53 位（二进制） |

指数全 0、指数全 1 等编码留给零、非正规数、无穷和 NaN；它们不能一概按普通正规数公式处理。

## 2.2 把加法和减法统一成带符号有效数运算

对 `a + b`，原始符号决定两数是同号还是异号；对 `a - b`，可视为 `a + (-b)`，即先翻转第二个操作数的符号。

- 有效符号相同：有效数相加，结果符号沿用共同符号。
- 有效符号不同：较大绝对值减去较小绝对值，结果符号取绝对值较大操作数的符号。
- 绝对值相等而异号：结果为零；零的符号还需按舍入规则确定。

因此，加法器的核心不只是做一次加法，还要比较符号、指数和有效数，以决定是否相减以及结果符号。

## 2.3 指数对齐

设两个有限数为 `A = mA × 2^eA`、`B = mB × 2^eB`，并令 `eA ≥ eB`。要在同一指数下运算，需把较小指数的有效数右移：

```text
A ± B = (mA ± (mB >> (eA-eB))) × 2^eA
```

右移会丢弃低位，但这些位仍可能影响最终舍入。因此实现需保留足够的额外精度，或用 guard、round、sticky 信息压缩记录被移出的部分。指数差很大时，小操作数的有效数主体可能全部移出；只要记录其低位是否曾有 1（sticky），仍能正确判断舍入方向。

## 2.4 两类数值关系与规格化

### 指数差较大或有效符号相同

同号运算是有效数相加；异号但指数差较大时，小操作数对齐后通常不足以让大操作数发生严重抵消。可以围绕较大指数右移对齐，再做有效数加/减。相加可能产生最高位进位，此时有效数右移一位、指数加一；相减的结果通常仍接近较大操作数。

### 异号且数值接近

两个大小接近的异号数相减，前导有效位可能抵消，差值会出现许多前导零。需要检测差值的前导零数量，把结果有效数左移到规范位置，并相应减小指数。绝对值相同则得到零，必须单独处理。

这就是浮点加法常把“右移对齐后计算”和“近数相减后左规格化”分开处理的原因：两类输入需要的主要移位方向不同。

## 2.5 舍入

对齐和运算先得到比目标格式更精确的结果。打包时依据目标精度与舍入模式选择相邻的可表示数。若精确结果本来可表示，各舍入模式结果一致；若不可表示，结果可能相差一个 ULP（目标格式相邻数的间距）。

常用的三个概念是：

- **guard**：保留位之后的第一位；
- **round**：再下一位；
- **sticky**：其余被丢弃低位的逻辑或；
- 另需检查当前最低保留位，以处理最近值舍入的平局。

五种模式如下：

| 编码 | 模式 | 理论规则 |
|---|---|---|
| `000` | RNE | 最近值；恰好等距时选最低有效位为 0 的结果（ties to even） |
| `001` | RTZ | 向零舍入 |
| `010` | RDN | 向负无穷舍入 |
| `011` | RUP | 向正无穷舍入 |
| `100` | RMM | 最近值；恰好等距时选绝对值较大的结果 |

RDN/RUP 是沿数轴方向舍入，不等同于对正负数都做“幅度减小/增加”。例如负数向负无穷会变得更负，负数向正无穷则更接近零。

### 中点示例：舍入模式如何改变结果

FP32 在 `1.0` 处的 ULP 为 `2^-23`。精确值 `x = 1 + 2^-24` 正好处于 `0x3f800000` 与 `0x3f800001` 的中点：

| 模式 | 结果 | 选择理由 |
|---|---:|---|
| RNE | `0x3f800000` | 下方候选最低保留位为 0，平局取偶数 |
| RTZ | `0x3f800000` | 正数向零 |
| RDN | `0x3f800000` | 正数向负无穷 |
| RUP | `0x3f800001` | 正数向正无穷 |
| RMM | `0x3f800001` | 平局取最大幅度 |

对 `x = -(1 + 2^-24)`，两个候选是 `0xbf800000`（-1）和 `0xbf800001`（更负一个 ULP）：RDN、RMM 选更负值；RNE、RTZ、RUP 选 -1。该例直观展示了方向性舍入受结果符号影响。

RNE 与 RMM 的差别在 tie-breaking。若 `x = 1 + 3×2^-24`，中点两侧为 `0x3f800001` 与 `0x3f800002`：RNE 选偶数的后者，RMM 也选幅度更大的后者。两者在这个例子结果相同，但依据不同；比较模式时必须看平局规则，而不能只看一个样例。

## 2.6 特殊值与异常标志的理论边界

IEEE-754 对 NaN、无穷、零和非正规数规定了独立于普通有效数运算的行为。例如 `+∞ + -∞` 是无效运算并产生 NaN；NaN 参与运算通常传播 NaN；零与有限数相加通常得到该有限数；两个异号同幅值数相加得到带规则符号的零。

RISC-V `fflags` 五位从高到低为 `NV DZ OF UF NX`：无效、除零、溢出、下溢、不精确。加法通常不产生 DZ；sNaN 或异号无穷相加会涉及 NV；舍弃非零低位会涉及 NX。溢出和下溢是否置位还取决于最终结果、舍入和格式边界。精确的特殊值优先级与标志置位方式属于具体实现分析，放在后续源码章节。

# 3. 源码总体结构与格式分派

## 3.1 源码范围与模块层次

标量浮点加法入口位于 `yunsuan/src/main/scala/yunsuan/fpu/FloatAdder.scala`。标量顶层接收 64 位操作数、舍入模式、操作码及 canonical NaN 标志；它从 `op_code` 推导浮点格式（`FAluOpcode.getFormat(io.op_code)`），并按格式选择流水线：

- `FloatAdderF64Pipeline` 处理 FP64；
- `FloatAdderF32F16MixedPipeline` 以 32 位内部路径处理 FP32 和 FP16；
- 两条流水线分别实例化 far path 与 close path；其较低层算术结构位于 `yunsuan/src/main/scala/yunsuan/vector/VectorFloatAdder.scala`。

## 3.2 FP64 与 FP32/FP16 分派

顶层把完整 64 位输入送入 FP64 路径，把低 32 位送入混合路径；输出则由流水保存的格式选择对应结果和标志。格式编码为 `01=FP16`、`10=FP32`、`11=FP64`，混合路径收到 `fp_format - 1` 后，以 bit 0 区分 FP32（1）与 FP16（0）。

```scala
val F64Adder = Module(new FloatAdderF64Pipeline(is_print = false, hasMinMaxCompare = true))
val F32Adder = Module(new FloatAdderF32F16MixedPipeline(is_print = false, hasMinMaxCompare = true))
val fp_format = FAluOpcode.getFormat(io.op_code) - 1.U
```

这是两条并行实例化、外层选择输出的结构，不是单个 datapath 在运行时变宽或变窄。标量顶层用 `RegEnable(FAluOpcode.getFormat(io.op_code), fire)` 保存结果格式；向量封装中的 `io.fp_format` 不是标量 `FloatAdder` 的输入。

## 3.3 FP16 内部表示及结果打包

混合路径将 FP16 的符号、指数和 fraction 按内部位布局映射到 32 位通路：

```scala
val fp_a_16as32 = Cat(
  io.fp_a(15),
  Cat(0.U(3.W), io.fp_a(14,10)),
  Cat(io.fp_a(9,0), 0.U(13.W))
)
val fp_a_to32 = Mux(res_is_f32, io.fp_a, fp_a_16as32)
```

B 操作数采用相同变换。该布局服务于 FP16/FP32 混合 datapath，不能简单理解为先将半精度数值转换成标准 IEEE FP32，再执行一次普通 FP32 运算。输出为 FP16 时，代码从内部结果取出符号、指数和 fraction 对应位重新打包；FP32 则直接取 32 位结果。

顶层将 FP16/FP32 结果扩展到 64 位接口。算术、min/max 和符号注入等被 `resultNeedBox` 覆盖的窄格式结果，高位填 1 形成 NaN-box；`fflags` 则由选中的格式流水线提供。

## 3.4 运算符号、并行候选路径和流水选择

两种精度流水线都根据符号和 `is_sub` 生成有效运算符号关系：

```scala
val EOP = (fp_a_sign ^ io.is_sub ^ fp_b_sign).asBool
```

`EOP=0` 表示有效符号相同、有效数相加；`EOP=1` 表示有效符号不同、有效数相减。顶层把 `io.op_code(0)` 作为快速 `is_sub` 控制，同时把完整 `op_code` 送入流水线以选择加减、比较等操作。

far path 与 close path 同时接收操作数及舍入模式，far path 输出的 `absEaSubEb` 用于产生路径选择。FP64 与扩宽 FP64 顶层将 `!EOP || absEaSubEb[高位范围].orR || (absEaSubEb == 1 && (a_is_zero ^ b_is_zero))` 作为 far-path 条件，并用 `RegEnable(is_far_path, fire)` 保存选择；FP32/F16 混合路径用同类条件生成 `is_far_path_reg`。随后按寄存的选择挑选两个候选结果。同号以及指数差较大的异号运算走右移对齐路径；异号且指数接近时走近数相减和左规格化路径。

`fire` 使能相关状态寄存器更新。仅凭顶层这段代码可以确认选择信号、格式和操作状态受 `fire` 控制；精确级数、延迟与吞吐率须继续核对所有子模块和调用者，不能仅由 `Pipeline` 类名推断。

## 3.5 同一单元中的其他浮点操作

构造时 `hasMinMaxCompare = true`，所以该单元还复用输入分类、比较等逻辑。标量 `FloatAdder` 支持 `fmin/fmax`、`fminm/fmaxm` 和符号注入；本模块不实现 `feq/flt/fle` 或 `fclass`；本次补充的向量 `VectorFloatAdder` 源码还包含 `fne/fgt/fge`、`fmerge/fmove` 和 reduction 专用 min/max/sum 选择。它们并非都经过浮点加法 datapath：符号注入直接拼接符号与数值位；比较根据符号、指数和有效数关系输出布尔结果；min/max 选择输入；`fclass` 输出类别位图。`op_code` 最终在加减结果和这些独立结果之间进行选择。

# 4. 编码、特例与算术路径

## 4.1 浮点编码的硬件表示

### 4.1.1 FP32、FP64 与有效数宽度

源码使用如下参数描述浮点格式。

FP32 内部路径：

```scala
val exponentWidth = 8
val significandWidth = 24
val floatWidth = exponentWidth + significandWidth
```

位置：

```text
FloatAdder.scala:105-107附近
```

这里的 `significandWidth = 24` 包含隐含的最高有效位，因此最终 fraction 字段仍然是 23 位。

FP64 内部路径：

```scala
val exponentWidth = 11
val significandWidth = 53
val floatWidth = exponentWidth + significandWidth
```

位置：

```text
FloatAdder.scala:324-326附近
```

浮点编码可以表示为：

```text
sign | exponent | fraction
```

对正规数：

```text
significand = 1.fraction
```

对非正规数：

```text
significand = 0.fraction
```

源码通过 exponent 是否为零来决定隐含位：

```scala
val Efp_a_is_not_zero = Efp_a.orR
val Efp_b_is_not_zero = Efp_b.orR

val significand_fp_a = Cat(Efp_a_is_not_zero, fp_a_mantissa)
val significand_fp_b = Cat(Efp_b_is_not_zero, fp_b_mantissa)
```

FP64 对应代码：

```text
VectorFloatAdder.scala:1250-1305附近
```

FP32 对应代码：

```text
VectorFloatAdder.scala:846-875附近
```

`Cat(Efp_a_is_not_zero, fp_a_mantissa)` 的含义是：

* 指数非零时，在 fraction 前补 `1`；
* 指数为零时，在 fraction 前补 `0`。

### 4.1.2 字段提取

FP64 路径：

```scala
val fp_a_mantissa = io.fp_a.tail(1 + exponentWidth)
val fp_b_mantissa = io.fp_b.tail(1 + exponentWidth)

val Efp_a = io.fp_a(floatWidth - 2, floatWidth - 1 - exponentWidth)
val Efp_b = io.fp_b(floatWidth - 2, floatWidth - 1 - exponentWidth)
```

位置：

```text
VectorFloatAdder.scala:1250-1305附近
```

对 FP64，字段对应关系为：

```text
io.fp_a(63)    -> sign
io.fp_a(62,52) -> exponent
io.fp_a(51,0)  -> fraction
```

FP32 的字段逻辑相同，只是指数宽度变为 8，fraction 宽度变为 23。

## 4.2 特殊值分类

### 4.2.1 NaN、无穷和零

`FloatAdderF64Pipeline` 在顶层识别特殊值：

```scala
val fp_a_is_NAN = io.fp_aIsFpCanonicalNAN | Efp_a_is_all_one & fp_a_mantissa_isnot_zero

val fp_a_is_infinite = !io.fp_aIsFpCanonicalNAN & Efp_a_is_all_one & (!fp_a_mantissa_isnot_zero)

val fp_a_is_zero = !io.fp_aIsFpCanonicalNAN & Efp_a_is_zero & !fp_a_mantissa_isnot_zero
```

位置：

```text
FloatAdder.scala:357-375附近
```

判定规则是：

| exponent | fraction | 类型 |
|---|---|---|
| 全 0 | 全 0 | 零 |
| 全 0 | 非零 | 非正规数 |
| 非全 0、非全 1 | 任意 | 正规数 |
| 全 1 | 全 0 | 无穷 |
| 全 1 | 非零 | NaN |

在 `FloatAdderF32F16MixedPipeline` 中，FP32 和内部 FP16 的 exponent 识别略有不同，但总体分类规则相同：

```text
FloatAdder.scala:166-189附近
```

### 4.2.2 signaling NaN 和 quiet NaN

对于 exponent 全 1 且 fraction 非零的编码，源码进一步检查 fraction 的最高有效部分：

```scala
val fp_a_is_SNAN =
  !io.fp_aIsFpCanonicalNAN &
  Efp_a_is_all_one &
  fp_a_mantissa_isnot_zero &
  !fp_a_to64(significandWidth - 2)

val fp_a_is_QNAN =
  !io.fp_aIsFpCanonicalNAN &
  Efp_a_is_all_one &
  fp_a_mantissa_isnot_zero &
  fp_a_to64(significandWidth - 2)
```

位置：

```text
FloatAdder.scala:367-372附近
```

对 `sNaN`，结果需要设置 invalid operation，即 `NV=1`。

### 4.2.3 特殊值结果优先级

FP64 顶层在正常 far/close 结果之前，先处理特殊值：

```
when(RegEnable(fp_a_is_NAN | fp_b_is_NAN | (EOP & fp_a_is_infinite & fp_b_is_infinite), fire) ){
  float_adder_result := RegEnable(Cat(0.U,Fill(exponentWidth,1.U),1.U,Fill(significandWidth-2,0.U)), fire)
}.elsewhen(RegEnable(fp_a_is_infinite | fp_b_is_infinite, fire)) {
  float_adder_result := RegEnable(Cat(Mux(fp_a_is_infinite,fp_a_to64.head(1),io.is_sub^fp_b_to64.head(1)), Fill(exponentWidth,1.U),Fill(significandWidth-1,0.U)), fire)
}.elsewhen(fp_a_is_zero_reg & fp_b_is_zero_reg){
  float_adder_result := RegEnable(Cat(Mux(io.round_mode==="b010".U & EOP | (fp_a_to64.head(1).asBool & !EOP),1.U,0.U),0.U(63.W)), fire)
}.elsewhen(fp_a_is_zero_reg){
  float_adder_result := RegEnable(Cat(io.is_sub ^ fp_b_to64.head(1),fp_b_to64(62,0)), fire)
}.elsewhen(fp_b_is_zero_reg){
  float_adder_result := RegEnable(fp_a_to64, fire)
}.otherwise{
  float_adder_result := Mux(is_far_path_reg, U_far_path.io.fp_c, U_close_path.io.fp_c)
}
```

大概意思就是：

```scala
when (NaN 或 异号无穷相加) {
  float_adder_result := canonicalNaN
}.elsewhen (无穷) {
  float_adder_result := infinity
}.elsewhen (双零) {
  float_adder_result := zero
}.elsewhen (a 为零) {
  float_adder_result := b
}.elsewhen (b 为零) {
  float_adder_result := a
}.otherwise {
  float_adder_result := Mux(is_far_path_reg, U_far_path.io.fp_c, U_close_path.io.fp_c)
}
```

对应代码：

```text
FloatAdder.scala:391-401附近
```

因此，普通尾数加法不会处理 NaN、无穷和零的所有组合；这些情况在进入正常数路径前已经被截走。

## 4.3 有效运算符号和路径选择

### 4.3.1 加法与减法统一为有效数加减

源码先把 `is_sub` 作用到第二个操作数的符号上：

```scala
val fp_a_sign = io.fp_a.head(1).asBool
val fp_b_sign = io.fp_b.head(1).asBool

val efficient_fp_b_sign = (fp_b_sign ^ io.is_sub).asBool

val EOP = (fp_a_sign ^ efficient_fp_b_sign).asBool
```

FP64 代码位置：

```text
VectorFloatAdder.scala:1250-1305附近
```

含义为：

```text
EOP = 0：两个有效操作数同号，需要做有效数相加
EOP = 1：两个有效操作数异号，需要做有效数相减
```

例如：

```text
(+a) + (+b) -> 同号相加
(+a) + (-b) -> 异号相减
(+a) - (+b) -> (+a) + (-b)，异号相减
```

`FloatAdder.scala` 顶层也使用相同逻辑：

```scala
val EOP = (fp_a_to64.head(1) ^ io.is_sub ^ fp_b_to64.head(1)).asBool
```

位置：

```text
FloatAdder.scala:342附近
```

### 4.3.2 指数比较

far path 计算两个指数的差：

```scala
val isEfp_bGreater = Efp_b > Efp_a

val Efp_aSubEfp_b = U_Efp_aSubEfp_b.io.c
val Efp_bSubEfp_a = U_Efp_bSubEfp_a.io.c

absEaSubEb := Mux(isEfp_bGreater, Efp_bSubEfp_a, Efp_aSubEfp_b)
```

FP64 位置：

```text
VectorFloatAdder.scala:1250-1305附近
```

随后选择较大指数：

```scala
val E_greater = Mux(isEfp_bGreater, Efp_b, Efp_a)
val EA        = Mux(EOP, E_greater - 1.U, E_greater)
val EA_add1   = EA + 1.U
```

`EA` 是后续结果的基准指数。异号相减时减 1，是为了配合补码式尾数运算和后续规格化。

### 4.3.3 far path 和 close path

顶层根据有效符号和指数差选择路径：

```scala
val is_far_path =
  !EOP |
  (EOP & absEaSubEb(absEaSubEb.getWidth - 1, 1).orR) |
  (absEaSubEb === 1.U & (Efp_a_is_zero ^ Efp_b_is_zero))
```

FP64 选择逻辑：

```text
FloatAdder.scala:379附近
```

可以按数学意义理解为：

| 条件 | 运算特点 | 路径 |
|---|---|---|
| 同号 | 有效数直接相加 | far path |
| 异号且指数差大 | 小数对齐后相减，结果接近大数 | far path |
| 异号且指数接近 | 相减结果可能有大量前导零 | close path |

两条路径同时计算，最后使用锁存的路径选择信号选择结果：

```scala
float_adder_result := Mux(is_far_path_reg, U_far_path.io.fp_c, U_close_path.io.fp_c)
```

## 4.4 Far path：指数对齐和尾数运算

### 4.4.1 构造两个有效数

FP64 far path：

```scala
val significand_fp_a = Cat(Efp_a_is_not_zero, fp_a_mantissa)
val significand_fp_b = Cat(Efp_b_is_not_zero, fp_b_mantissa)

val greaterSignificand = Mux(isEfp_bGreater, significand_fp_b, significand_fp_a)

val smallerSignificand = Mux(isEfp_bGreater, significand_fp_a, significand_fp_b)
```

位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

有效数的最高位是隐藏位，低位是原始 fraction。

### 4.4.2 右移对齐

far path 需要把较小指数的有效数右移 `absEaSubEb` 位：

```scala
val farmaxShiftValue = (significandWidth + 2).U

val fp_b_mantissa_widen =
  Mux(
    EOP,
    Cat(~significand_fp_b, 1.U, Fill(widenWidth, 1.U)),
    Cat(0.U, significand_fp_b, 0.U(widenWidth.W))
  )

val U_far_rshift_1 = Module(new FarShiftRightWithMuxInvFirst(fp_b_mantissa_widen.getWidth, farmaxShiftValue.getWidth))
U_far_rshift_1.io.src := fp_b_mantissa_widen
U_far_rshift_1.io.shiftValue := Efp_aSubEfp_b.asTypeOf(farmaxShiftValue)
U_far_rshift_1.io.EOP := EOP

// 另一路计算 A 右移 (E_b-E_a)，两种大小关系的候选并行生成
val fp_a_mantissa_widen = Mux(
  EOP,
  Cat(~significand_fp_a, 1.U, Fill(widenWidth, 1.U)),
  Cat(0.U, significand_fp_a, 0.U(widenWidth.W)))
val U_far_rshift_2 = Module(new FarShiftRightWithMuxInvFirst(
  fp_a_mantissa_widen.getWidth, farmaxShiftValue.getWidth))
U_far_rshift_2.io.src := fp_a_mantissa_widen
U_far_rshift_2.io.shiftValue := Efp_bSubEfp_a.asTypeOf(farmaxShiftValue)
U_far_rshift_2.io.EOP := EOP
val far_rshift_widen_result = Mux(
  isEfp_bGreater, U_far_rshift_2.io.result, U_far_rshift_1.io.result)
```

FP64 位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

这里有两个关键点：

1. 右移输入被扩展，保证低位信息不会过早丢失；
2. 异号运算时使用按位取反和补偿位，为后续补码式减法准备操作数。

右移后的低位被整理为：

```scala
val B_wire =
  Mux(
    absEaSubEb_is_greater,
    Fill(significandWidth + 1, EOP),
    far_rshift_widen_result.head(significandWidth + 1)
  )

val B_guard_normal_reg = RegEnable(Mux(
  absEaSubEb_is_greater,
  false.B,
  Mux(EOP,
    !far_rshift_widen_result.head(significandWidth + 2)(0).asBool,
    far_rshift_widen_result.head(significandWidth + 2)(0).asBool)
), fire)
val B_round_normal_reg = RegEnable(Mux(
  absEaSubEb_is_greater,
  false.B,
  Mux(EOP,
    !far_rshift_widen_result.head(significandWidth + 3)(0).asBool,
    far_rshift_widen_result.head(significandWidth + 3)(0).asBool)
), fire)
val B_sticky_normal_reg = Mux(
  RegEnable(absEaSubEb_is_greater, fire),
  RegEnable(smallerSignificand.orR, fire),
  Mux(EOP_reg,
    RegEnable(~far_rshift_widen_result.tail(significandWidth + 3), fire).asUInt.orR,
    RegEnable(far_rshift_widen_result.tail(significandWidth + 3), fire).orR)
)
val B_rsticky_normal_reg = B_round_normal_reg | B_sticky_normal_reg
```

含义为：

```text
B       = 参与主尾数运算的部分
B_guard = guard 位
B_round = round 位
B_sticky = 更低位的 OR
```

位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

当指数差大于有效数宽度时，较小操作数的主尾数已经完全移出，只需要用 sticky 位记录它是否非零。

### 4.4.3 尾数加法器

far path 构造基准操作数 `A`：

```scala
val A_wire =
  Mux(
    EOP,
    Cat(greaterSignificand, 0.U),
    Cat(0.U, greaterSignificand)
  )
```

然后使用两个加法器分别计算普通结果和处理进位/舍入的候选结果：

```scala
val U_FS0 =
  Module(new FarPathAdderF64WidenPipeline(
    AW = significandWidth + 1,
    AdderType = "FS0",
    stage0AdderWidth = 0
  ))

U_FS0.io.A := A_wire
U_FS0.io.B := B_wire

val FS0 = U_FS0.io.result

// 第二个候选把 A 增加 2 个扩展尾数最低位单位，用于舍入进位选择
val U_FS1 = Module(new FarPathAdderF64WidenPipeline(
  AW = significandWidth + 1, AdderType = "FS1", stage0AdderWidth = 0))
U_FS1.io.fire := fire
U_FS1.io.A := A_wire + 2.U
U_FS1.io.B := B_wire
val FS1 = U_FS1.io.result
```

代码位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

其数学意义可以近似写成：

```text
同号：  A + B
异号：  A - B
```

`FS0.head(1)` 用于判断有效数加法是否产生了最高位进位：

```scala
val far_case_normal =
  !FS0.head(1).asBool

val far_case_overflow =
  FS0.head(1).asBool
```

### 4.4.4 far path 的舍入和结果打包

源码先构造用于舍入判断的三位信息：

```scala
val lgs_normal_reg =
  Cat(
    FS0(0),
    Mux(
      EOP_reg,
      (~Cat(B_guard_normal_reg, B_rsticky_normal_reg)).asUInt + 1.U,
      Cat(B_guard_normal_reg, B_rsticky_normal_reg)
    )
  )
```

然后根据舍入模式决定是否加一：

```scala
val far_case_normal_round_up =
  (EOP_reg & !lgs_normal_reg(1) & !lgs_normal_reg(0)) |
  (RNE_reg &  lgs_normal_reg(1) & (lgs_normal_reg(2) | lgs_normal_reg(0))) |
  (RDN_reg &  far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
  (RUP_reg & !far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
  (RMM_reg & lgs_normal_reg(1))
```

位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

其中：

* RNE 根据 guard、round/sticky 和最低有效位实现 ties-to-even；
* RTZ 不触发加一；
* RDN 对负数方向舍入；
* RUP 对正数方向舍入；
* RMM 在 guard 为 1 时向最大幅度舍入。

结果指数：

```scala
far_exponent_result :=
  Mux(
    far_case_overflow |
    (FS1.head(1).asBool & FS0(0) & far_case_normal_round_up) |
    (RegEnable(!EA.orR, fire) & FS0.tail(1).head(1).asBool),
    RegEnable(EA_add1, fire),
    RegEnable(EA, fire)
  )
```

位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

如果尾数加法产生进位，结果有效数右移一位，指数增加一。
普通和溢出候选的 fraction 会由 `FS0/FS1` 与 `round_up` 组合选择；`FS1` 使舍入进位后的候选可并行准备，不必等主加法结果再串行加一。发生指数溢出时，源码按舍入模式决定输出无穷还是最大有限数：

```scala
val result_overflow = Mux(
  RTZ_reg | (RDN_reg & !far_sign_result_reg) | (RUP_reg & far_sign_result_reg),
  Cat(far_sign_result_reg, Fill(exponentWidth - 1, 1.U), 0.U,
      Fill(significandWidth - 1, 1.U)), // 最大有限数
  Cat(far_sign_result_reg, Fill(exponentWidth, 1.U),
      Fill(significandWidth - 1, 0.U)) // 同符号无穷
)
```

最终结果：

```scala
io.fp_c := Mux(
  OF,
  result_overflow,
  Cat(
    far_sign_result_reg,
    far_exponent_result,
    far_fraction_result
  )
)
```

### 4.4.5 far path 的异常标志

far path 中：

```scala
OF := RegEnable(EA_add1.andR, fire) & (far_case_overflow | (FS1.head(1).asBool & FS0(0) & far_case_normal_round_up))
NX := Mux(far_case_normal,lgs_normal_reg(1,0).orR,lgs_overflow(1,0).orR) | OF
```

位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

含义：

* `OF`：结果指数超出格式可表示范围；
* `NX`：guard、round、sticky 中至少有一个有效，或者发生溢出；
* `NV`、`DZ`、`UF` 在这段 far-path 逻辑中不赋值。当前 Yunsuan 的 FP32/FP64 far/close 算术路径将 `UF` 保持为 0；不能据此推断其他路径会上报 `UF`。加法的 `DZ` 通常为 0，`NV` 由顶层特殊值处理。

## 4.5 Close path：尾数相减和左规格化

### 4.5.1 为什么需要 close path

异号且指数接近时，结果可能出现大量前导零：

```text
1.000000 × 2^e
-0.111111 × 2^e
----------------
 0.000001 × 2^e
```

如果仍按 far path 的右移对齐方式处理，无法高效确定结果的最高有效位。因此 close path 直接计算差值，并使用前导零检测器确定左移量。

### 4.5.2 候选差值

FP64 close path 首先构造两个有效数：

```scala
val significand_fp_a = Cat(Efp_a_is_not_zero, fp_a_mantissa)
val significand_fp_b = Cat(Efp_b_is_not_zero, fp_b_mantissa)
```

源码先计算四个基础候选差值，并额外计算 `CS4`，用于指数相差 1 且需要按舍入修正的边界：

```scala
val U_CS0 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS0"))
U_CS0.io.adder_op0 := significand_fp_a
U_CS0.io.adder_op1 := significand_fp_b
val CS0 = U_CS0.io.result(significandWidth - 1, 0)

val U_CS1 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS1"))
U_CS1.io.adder_op0 := significand_fp_b
U_CS1.io.adder_op1 := significand_fp_a
val CS1 = U_CS1.io.result(significandWidth - 1, 0)

val U_CS2 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS2"))
U_CS2.io.adder_op0 := Cat(1.U, fp_a_mantissa)
U_CS2.io.adder_op1 := Cat(1.U, fp_b_mantissa)
val CS2 = U_CS2.io.result(significandWidth, 0)

val U_CS3 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS3"))
U_CS3.io.adder_op0 := Cat(1.U, fp_b_mantissa)
U_CS3.io.adder_op1 := Cat(1.U, fp_a_mantissa)
val CS3 = U_CS3.io.result(significandWidth, 0)
```

`CS0/CS1` 是包含隐含位的两个有效数按相反顺序相减；`CS2/CS3` 是在两边都显式补入 1 后的差值，用于指数不同的候选规格化形式。`CS4` 则把较大指数一侧有效数与另一操作数右移一位后的有效数相减：

```scala
val U_CS4 = Module(new ClosePathAdder(adderWidth = significandWidth, adderType = "CS0"))
U_CS4.io.adder_op0 := RegEnable(
  Mux(Efp_b_is_greater, significand_fp_b, significand_fp_a), fire)
U_CS4.io.adder_op1 := RegEnable(Mux(
  Efp_b_is_greater,
  Cat(0.U, significand_fp_a(significandWidth - 1, 1)),
  Cat(0.U, significand_fp_b(significandWidth - 1, 1))), fire)
val CS4_reg = U_CS4.io.result
```

该候选对应舍入后发生边界进位、需要用不再左移的结果形式输出的情形。源码位置：`VectorFloatAdder.scala:1434-1594附近`。

在该 close path 中，被移出的一位作为 `B_guard`；源码将 `B_round` 与 `B_sticky` 固定为 0。NX 再根据选中的候选差值、guard 和舍入修正判定。因此，不能把 far path 的长右移和 sticky 汇总直接套用到 close path。

### 4.5.3 选择正确的差值

`CS2_round_up`、`CS3_round_up` 先依据被舍弃的低位及舍入模式判断候选差值是否需要校正。FP64 逻辑为：

```scala
val CS2_round_up = significand_fp_b(0) & (
  (RUP & !fp_a_sign) | (RDN & fp_a_sign) |
  (RNE & CS2(1) & CS2(0)) | RMM)
val CS3_round_up = significand_fp_a(0) & (
  (RUP & fp_a_sign) | (RDN & !fp_a_sign) |
  (RNE & CS3(1) & CS3(0)) | RMM)
```

随后结合指数是否相等、哪个指数较大、候选差值的高位/最低位及舍入校正，生成互斥的选择条件：

```scala
val sel_CS0 = exp_is_equal & !U_CS0.io.result.head(1).asBool
val sel_CS1 = exp_is_equal &  U_CS0.io.result.head(1).asBool
val sel_CS2 = !exp_is_equal & !Efp_b_is_greater &
  ((!U_CS2.io.result.head(1).asBool | !U_CS2.io.result(0).asBool) | !CS2_round_up)
val sel_CS3 = !exp_is_equal &  Efp_b_is_greater &
  ((!U_CS3.io.result.head(1).asBool | !U_CS3.io.result(0).asBool) | !CS3_round_up)
val sel_CS4 = !exp_is_equal & (
  (!Efp_b_is_greater & U_CS2.io.result.head(1).asBool & U_CS2.io.result(0).asBool & CS2_round_up) |
  ( Efp_b_is_greater & U_CS3.io.result.head(1).asBool & U_CS3.io.result(0).asBool & CS3_round_up))
```

`CS0..CS3` 经 `Mux1H` 组成用于 LZD 的 `CS_0123_result`；`CS4` 单独旁路，不进入这个 Mux：

```scala
val CS_0123_result = Mux1H(
  Seq(sel_CS0, sel_CS1, sel_CS2, sel_CS3),
  Seq(Cat(CS0, 0.U), Cat(CS1, 0.U), CS2, CS3))
```

之后 `mask_onehot` 根据同一组选择信号生成前导零检测边界 mask。源码位置：`VectorFloatAdder.scala:1434-1594附近`。

### 4.5.4 前导零检测

为了得到规范化结果，源码构造优先级 mask：

```scala
val priority_mask = CS_0123_result | mask_onehot
val lzd_0123 = LZD(priority_mask)
```

位置：

```text
VectorFloatAdder.scala:1434-1594附近
```

`LZD` 的结果是左移量。随后通过左移模块完成规格化：

```scala
val U_Lshift = Module(new CloseShiftLeftWithMux(CS_0123_result.getWidth, priority_mask.getWidth.U.getWidth))
U_Lshift.io.src := RegEnable(CS_0123_result, fire)
U_Lshift.io.shiftValue := lzd_0123_reg
val CS_0123_lshift_result_reg = U_Lshift.io.result(significandWidth, 1)
```

位置：

```text
VectorFloatAdder.scala:1434-1594附近
```

数学上相当于：

```text
normalized_significand = difference << leading_zero_count
result_exponent = EA - leading_zero_count
```

### 4.5.5 指数、符号和 fraction

close path 的指数修正：

```scala
val close_exponent_result = RegEnable(EA, fire) - EA_sub_value
```

位置：

```text
VectorFloatAdder.scala:1434-1594附近
```

其中 `EA_sub_value` 通常是前导零数量；如果已经处于特殊边界，则使用单独的修正值。

结果符号由实际较大操作数和舍入方向共同决定：

```scala
close_sign_result := RegEnable(Mux1H(
  Seq(
    sel_CS0 & exp_is_equal & (U_CS0.io.result.head(1).asBool | U_CS1.io.result.head(1).asBool),
    sel_CS0 & exp_is_equal & !U_CS0.io.result.head(1).asBool & !U_CS1.io.result.head(1).asBool,
    sel_CS1, sel_CS2, sel_CS3, sel_CS4
  ),
  Seq(fp_a_sign, RDN, !fp_a_sign, fp_a_sign, !fp_a_sign,
      Mux(Efp_b_is_greater, !fp_a_sign, fp_a_sign))
), fire)
```

完全相消时第二个选择项用 `RDN` 生成负零；其余候选的符号根据减法方向和较大绝对值操作数确定。源码位置：`VectorFloatAdder.scala:1434-1594附近`。

最终打包：

```scala
io.fp_c :=
  Cat(
    close_sign_result,
    close_exponent_result,
    close_fraction_result
  )
```

位置：

```text
VectorFloatAdder.scala:1434-1594附近
```

# 5. 舍入与异常标志的源码实现

## 5.1 舍入模式控制

far path 和 close path 都使用 RISC-V 五种舍入模式：

| 编码 | 名称 | 作用 |
|---|---|---|
| `000` | RNE | 最近值，平局取偶数 |
| `001` | RTZ | 向零舍入 |
| `010` | RDN | 向负无穷舍入 |
| `011` | RUP | 向正无穷舍入 |
| `100` | RMM | 最近值，平局取最大幅度 |

FP64 far path 中的模式寄存器：

```scala
val RNE_reg = RegEnable(io.round_mode === "b000".U, fire)
val RTZ_reg = RegEnable(io.round_mode === "b001".U, fire)
val RDN_reg = RegEnable(io.round_mode === "b010".U, fire)
val RUP_reg = RegEnable(io.round_mode === "b011".U, fire)
val RMM_reg = RegEnable(io.round_mode === "b100".U, fire)
```

位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

## 5.2 Guard、round、sticky 与舍入控制

右移对齐会产生低位信息：

```text
主 fraction | guard | round | sticky
```

其中：

* `guard` 是第一个被丢弃的位；
* `round` 是第二个被丢弃的位；
* `sticky` 是剩余所有低位的 OR。

far path 对这些位的提取：

```scala
val B_guard_normal_reg = RegEnable(Mux(
  absEaSubEb_is_greater,
  false.B,
  Mux(EOP,
    !far_rshift_widen_result.head(significandWidth + 2)(0).asBool,
    far_rshift_widen_result.head(significandWidth + 2)(0).asBool)
), fire)
val B_round_normal_reg = RegEnable(Mux(
  absEaSubEb_is_greater,
  false.B,
  Mux(EOP,
    !far_rshift_widen_result.head(significandWidth + 3)(0).asBool,
    far_rshift_widen_result.head(significandWidth + 3)(0).asBool)
), fire)
val B_sticky_normal_reg = Mux(
  RegEnable(absEaSubEb_is_greater, fire),
  RegEnable(smallerSignificand.orR, fire),
  Mux(EOP_reg,
    RegEnable(~far_rshift_widen_result.tail(significandWidth + 3), fire).asUInt.orR,
    RegEnable(far_rshift_widen_result.tail(significandWidth + 3), fire).orR)
)
val B_rsticky_normal_reg = B_round_normal_reg | B_sticky_normal_reg
```

位置：

```text
VectorFloatAdder.scala:1305-1419附近
```

RNE 的核心判断可以抽象为：

```text
round_up = guard && (round || sticky || fraction_lsb)
```

源码中的对应形式：

```scala
RNE_reg & lgs_normal_reg(1) & (lgs_normal_reg(2) | lgs_normal_reg(0))
```

如果 `round_up` 为真，fraction 增加一；如果增加导致进位，指数也增加一。

这里的“加一”是对保留有效数末位加一个该格式的最低有效位单位（一个 ULP 的末位单位），不是给整个浮点数的数值加 1。如果进位越过有效数字段边界，结果需要重新规格化并增加指数。

## 5.3 舍入控制与理论规则的对应

RTL 中常见的抽象判定可以写为：

```text
inexact = guard | round | sticky
RNE 加一：guard & (round | sticky | LSB)
RTZ 加一：不因舍入而加一
RDN 加一：结果为负且存在非零被舍弃位
RUP 加一：结果为正且存在非零被舍弃位
RMM 加一：guard 为 1 时向最大幅度方向舍入
```

FP64 far path 的 `far_case_normal_round_up` 把舍入模式寄存值、结果符号和 LGS 信息组合成加一控制。RNE 在 guard 为 1 且（round/sticky 有非零信息，或保留 LSB 为 1）时进位；RDN/RUP 结合符号决定方向；RMM 检查 guard。异号运算中低位还可能经过补码变换，所以这些表达式必须结合构造 LGS 的上下文理解，不能把局部信号位机械当作未经变换的 G/R/S。

舍入加一可能使尾数产生进位，继而规格化并增加指数；若指数超出格式范围，可能同时报告 OF 与 NX。被舍弃低位非零时报告 NX；精确可表示结果不因舍入产生 NX。加法的 `DZ` 通常为 0，NV 则由 sNaN 或无效无穷组合等特殊情形产生。`fflags` 五位顺序为 `NV DZ OF UF NX`。

## 5.4 特殊值旁路、比较操作和源码边界

在普通 far/close 运算结果之外，流水线会分类 NaN、无穷和零，并寄存分类状态。结果优先级大意为：NaN 或异号无穷相加输出 canonical NaN；否则无穷旁路并修正符号；双零按有效符号与舍入方向生成有符号零；单零旁路另一操作数；其余有限非零值选择算术路径结果。sNaN 或异号无穷相加会设置 `NV`；特例之外的普通运算标志取自被选中的算术路径。

`fp_*IsFpCanonicalNAN` 是上游传入的 canonical NaN 标记，分类和结果修正会结合该标记。向量模块把各格式的运算结果与 `fflags` 分别接出；顶层 `VectorFloatAdder` 的 `fflags` 是 20 位，由四个 5 位组按结果格式、`is_vec` 和 lane 选择/门控后拼接，不能当成跨所有 lane 汇总的单个 5 位标志。源码中也存在定义后未参与关键选择的信号（例如 `fp_a_is_QNAN`），阅读时应以实际数据依赖为准。

本次补充的 `VectorFloatAdder.scala` 已包含 FP32/F16 混合与 FP64 的 far/close 模块、候选减法器、LZD/左移器和舍入选择逻辑；前文的 `FloatAdder.scala` 负责标量格式分派与特殊值顶层控制。向量包装器顶层输出 `fflags` 为 20 位，由四组 5 位标志按格式、向量模式及有效 lane 选择/门控后拼接，不应误读成一个已经跨全部 lane 做 OR 的 5 位标志。具体到某条指令哪些 lane 有效，还要结合 VFALU mask 与后端调用连接。`RegEnable` 展示了局部状态更新条件，但精确端到端延迟和握手契约仍需结合调用者。


# 6. 具体计算示例：`1.25 + 2.5`

考虑：

```asm
fadd.s fa5, fa5, fa3
```

假设：

```text
fa5 = 1.25
fa3 = 2.5
```

FP32 编码为：

```text
1.25 = 0x3fa00000
2.5  = 0x40200000
```

拆分字段：

```text
1.25:
  sign     = 0
  exponent = 127
  fraction = 0x200000
  significand = 1.01b

2.5:
  sign     = 0
  exponent = 128
  fraction = 0x200000
  significand = 1.01b
```

因为两个数同号，`EOP = 0`，选择 far path：

1. 以指数较大的 `2.5` 为基准；
2. 将 `1.25` 的有效数右移 1 位；
3. 对齐后的有效数相加；
4. 结果规格化为 `1.11b * 2^1`；
5. 该值正好可以用 FP32 表示，不需要舍入进位；
6. 结果为：

```text
3.75 = 0x40700000
```

如果结果写入 64 位浮点寄存器，FP32 NaN-box 后的寄存器值为：

```text
0xffffffff40700000
```

这个例子说明：内存中可以预先存放两个操作数，但执行 `fadd.s` 时真正参与运算的输入是浮点寄存器读出的 `0x3fa00000` 和 `0x40200000`，而不是再次访问内存。


## 6.1 不同舍入模式的结果对比

FP32 在 1.0 处 ULP 为 `2^-23`。精确结果 `1 + 2^-24` 正好处于 `0x3f800000` 与 `0x3f800001` 的中点：RNE、RTZ、RDN 得到 `0x3f800000`；RUP、RMM 得到 `0x3f800001`。对负数 `-(1 + 2^-24)`，RNE、RTZ、RUP 得到 `0xbf800000`；RDN、RMM 得到 `0xbf800001`。这些结果不可精确表示，正常情况下会置 NX。

非中点 `1 + 2^-25` 比 1.0 更近，因此 RNE、RTZ、RDN、RMM 得到 `0x3f800000`，RUP 得到 `0x3f800001`。此例中 RMM 按最近值选择，并不因为模式名称就总是远离零。

# 7. 流水与执行重叠的边界

`FloatAdder` 内部的 `RegEnable` 表明格式、路径选择和控制状态会在 `fire` 有效时更新。指令发射后，处理器不必停下等待整条浮点计算完成；其他无依赖指令可在满足资源条件时继续推进。不过能否同周期重叠，取决于执行单元、寄存器依赖、发射队列、写回端口、唤醒/旁路等整体后端条件。仅凭本文件无法推出固定流水级数、延迟或每周期吞吐率。

# 8. 代码定位与总结

| 运算功能 | 文件 | 原源码关键位置（以所分析版本为准） |
|---|---|---|
| 顶层格式分派、FP16/FP32 混合路径与特殊值选择 | `yunsuan/src/main/scala/yunsuan/fpu/FloatAdder.scala` | 10-102附近 |
| FP64 顶层流水与特殊值、比较选择 | `yunsuan/src/main/scala/yunsuan/fpu/FloatAdder.scala` | 323-479附近 |
| FP32 far path | `yunsuan/src/main/scala/yunsuan/vector/VectorFloatAdder.scala` | 779-981附近 |
| FP32 close path | `yunsuan/src/main/scala/yunsuan/vector/VectorFloatAdder.scala` | 1018-1183附近 |
| FP64 far path | `yunsuan/src/main/scala/yunsuan/vector/VectorFloatAdder.scala` | 1250-1419附近 |
| FP64 close path | `yunsuan/src/main/scala/yunsuan/vector/VectorFloatAdder.scala` | 1434-1594附近 |

从理论看，浮点加法需要解码、分类、统一符号、对齐、有效数运算、规格化和舍入。从实现看，顶层按 FP16/FP32/FP64 分派，特殊值走旁路，普通有限数由 far/close 候选路径完成计算，再根据 `fire` 对齐选择状态、输出格式化结果和异常标志。理论算法与代码结构在这里一一对应。







