# 第九章 香山 Chisel 代码规范

# 前言

这份规范是香山处理器**最新官方强制工程标准**，适配前端重构场景（ICache、Ifu、InstrUncache 已完成落地），兼容原有规范 v2 且规则更严格。

核心宗旨：**代码整齐无歧义、硬件无隐性 Bug、可读性拉满、适配自动化校验、方便迭代维护、开源友好**。

适用范围：香山仓库及子仓库 Scala 代码，当前优先在 MemBlock、L2 模块试用，后续全仓库推广。

# 代码格式化工具规范（CI 强制校验）

香山前端已启用两套代码规范校验工具，分工明确，覆盖格式、命名、文件、语法全场景，是代码提交的基础门槛。

## 1.1 Scalafmt（强制格式化，CI 必检）

核心作用：自动统一代码缩进、对齐、折行、空格、换行，解决代码排版混乱问题，**未通过校验会直接导致 CI 报错，无法合入代码**。

配置文件：仓库根目录 `.scalafmt.conf`

### 1.1.1 本地校验/格式化命令

* 检查代码是否合规：`make check-format`
* 自动修复所有格式问题：`make reformat`
* 注意目前make check-format reformat 只会修改前端代码，如要修改/检测自己的代码需要手动修改`.scalafmt.conf`添加自己的文件

### 1.1.2 IDE 自动格式化配置

VS Code、IDEA 均支持保存自动格式化，无需手动执行命令，推荐全员开启。同时可配置 Git 提交钩子，实现提交前自动校验，杜绝不合格代码提交：

将以下脚本命名为 `pre-commit`，放入对应 Git 钩子目录并赋予可执行权限，提交代码时自动校验格式，失败则禁止提交：

```bash
#!/bin/bash
make check-format 2>/dev/null | grep -v '^file excluded'
if [ ${PIPESTATUS[0]} -ne 0 ]; then
    echo "Format checking failed, refusing to commit"
    echo "hint: Run 'make reformat' will resolve this issue"
    exit 1
fi
```

钩子文件目录：

* XiangShan 原生环境：`.../XiangShan/.git/hooks`
* xs-env 环境：`.../xs-env/.git/modules/XiangShan/hooks`

### 1.1.3 重点避坑规则（高频报错）

Scalafmt 会自动对齐函数返回值，容易出现误对齐问题，**所有独立函数之间必须加空行**，解决对齐报错：

❌ 错误写法（无空行，格式化错乱）

```scala
def +(offset:  UInt):       PrunedAddr = PrunedAddrInit(toUInt + offset)
def +(that:    PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt + that.toUInt)
def -(that:    PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt - that.toUInt)
```

✅ 正确写法（函数间空行分隔，格式合规）

```scala
def +(offset: UInt): PrunedAddr = PrunedAddrInit(toUInt + offset)

def +(that: PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt + that.toUInt)

def -(that: PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt - that.toUInt)
```

补充：Chisel 中 when、elsewhen 属于函数而非关键字，必须和普通函数保持一致的空格格式，禁止特殊对待。

### 1.1.4 特殊场景忽略格式化

仅允许**大量重复匹配逻辑**（如指令解码数组）关闭格式化，其余场景禁止跳过。使用 `// format off` 和 `// format on` 包裹特殊代码段即可。

## 1.2 Scalastyle（规范校验，辅助检查）

核心作用：补充 Scalafmt 缺失的校验规则，负责文件头、命名、导入、类型标注、文件大小检查，**无自动修复功能、不接入 CI、仅开发提醒**。

配置更新：原有 2019 年旧配置已过时，最新优化配置已提 PR（#4329），当前校验规则与本规范完全对齐。

IDE 支持：IDEA 可直接开启检查（编辑器-检查-scala-代码样式-scala样式检查）；VS Code 暂无插件，可通过官方命令行手动校验。

### 1.2.1 核心强制检查项

1. **文件头检查**：必须遵循「至少一行Copyright + 空行 + Mulan License」固定格式，格式错误直接告警。
2. **命名检查**：class/object 大驼峰、方法/变量小驼峰，trait、特殊变量需手动合规。
3. **Import 检查**：禁止块导入、禁止通配符导入（仅 chisel3.*、chisel3.util.* 豁免）。
4. **类型标注**：所有公开成员必须手动添加类型标注，禁止隐式推导。
5. **文件可读性检查**：单文件≤800行、单行≤120字符、单类方法≤30个、单方法≤50行。

### 1.2.2 工具局限性（无需兼容）

Scalastyle 无法区分符号语义，会误判可变参数符号 `UInt*`，要求两侧加空格，与 Scalafmt 冲突，**以 Scalafmt 格式化结果为准，忽略该告警**。

# 全局命名规范

所有代码命名**统一驼峰式**，禁止随意大小写、下划线、缩写乱象，保证 Chisel 代码与生成的 Verilog 信号一一对应，波形调试无歧义。

## 2.1 基础命名规则

* **大驼峰（UpperCamelCase）**：类、特征、单例对象、常量、状态枚举
* **小驼峰（lowerCamelCase）**：方法、普通变量、IO 端口、线网、寄存器
* **缩写规则**：缩写统一视为单个单词（ICache、Ifu、Ras），禁止 Icache、IFUWBPtr 这类不规范写法；短缩写建议写全称（UCnt → UsefulCnt）
* **全局禁止**：常规命名使用下划线、全大写、全小写混搭

## 2.2 特殊豁免场景（仅以下情况可使用下划线）

* 流水级信号：s1\_valid、s2\_ready（s0/s1/s2 前缀）
* 调试/性能信号：debug\_xxx、perf\_xxx（最终交付 RTL 需剔除）
* 多组 IO 区分：io\_xxx（尽量少用）
* 状态机标识：s\_idle、s\_busy

## 2.3 模块命名专属规则（解决 Verilog 重名后缀问题）

Scala 仅保证包内类唯一，但不同包同名 Module 会导致 Verilog 自动加 `_1` 后缀，无法区分模块，**所有自定义 Module 必须全局唯一命名**。

❌ 错误写法（重名冲突）

```scala
// icache 模块
class CtrlUnit extends ICacheModule
// dcache 模块
class CtrlUnit extends XSModule
// 最终生成：CtrlUnit.sv、CtrlUnit_1.sv，无法区分
```

✅ 正确写法（模块名带归属前缀，全局唯一）

```scala
class ICacheCtrlUnit extends ICacheModule
class DCacheCtrlUnit extends XSModule
// 最终生成：ICacheCtrlUnit.sv、DCacheCtrlUnit.sv，清晰对应
```

## 2.4 统一命名习惯（杜绝乱象）

* 缩写统一：frm→from、excp→exception、buf→buffer、DCache（非Dcache）
* 同一语义全程统一：misalign 系列变量禁止 mis\_align、misAlign、misalign\_buf 混搭
* 命名直观：变量名无需注释即可看懂含义，禁止模糊、歧义命名

# 文件与包结构规范（统一工程目录）

## 3.1 文件命名规则

* 基础规则：文件名与文件内唯一主类名完全一致（Ftq.scala 对应 Ftq 类）
* 合法特例：多个关联IO、LazyModule+LazyModuleImp、密封类及其子类可放同一文件
* 通用组件：SRAMTemplate、FIFO 等通用工具模块，统一放入 Utility 仓库

## 3.2 包与目录规范

* 包名**全小写**，紧跟文件 License 之后声明，禁止随意命名
* 目录、包、硬件结构三者统一：主模块单独建目录，子模块归属主模块包
* 示例：Ifu 主模块路径 `src/scala/xiangshan/frontend/ifu/Ifu.scala`，子模块 PreDecode 同属该包

## 3.3 Import 导入规范

* 禁止混合导入：不能同时导入包全部（xxx.\_）和包内单个成员
* 禁止非法通配符：除 chisel3.*、chisel3.util.* 外，所有 xxx.\_ 导入禁止使用
* 导入排序：按字典序排列，Scalafmt 可自动修复，禁止手动乱序
* 减少跨包导入：同级包尽量不互相导入，公共逻辑统一抽离到父包

# Bundle 接口规范

## 4.1 核心强制规则

1. **绝对禁止匿名 Bundle**：所有模块 IO 必须独立命名 Bundle 类，支持 IDE 跳转、复用
2. **分层结构化**：同类信号、模块交互信号统一封装子 Bundle，禁止零散平铺
3. **信号方向分离**：优先区分输入/输出 Bundle，禁止同一 Bundle 混杂多方向信号（Valid/Decoupled 接口除外）
4. **控制信号独立**：纯控制信号使用 ValidIO/DecoupledIO，禁止与普通数据信号混编，避免门控异常

## 4.2 优劣写法对比

❌ 错误写法（匿名、信号零散、层级混乱）

```scala
val io = IO(new Bundle{
  val xxx = UInt(8.W)
  val yyy = Bool()
  val zzz_a = UInt(4.W)
  val zzz_b = Bool()
})
```

✅ 正确写法（分层命名、结构清晰、可复用）

```scala
class ZZZBundle extends Bundle {
  val a = UInt(4.W)
  val b = Bool()
}
class SomeIO extends Bundle {
  val xxx = UInt(8.W)
  val yyy = Bool()
  val zzz = new ZZZBundle
}
val io = IO(new SomeIO)
```

## 4.3 高阶规范：功能聚合 Bundle

多模块交互的零散状态、计数、指针信号，统一封装 Info 类 Bundle，大幅简化顶层连线，避免漏连、错连。典型场景：LSQ 队列空满、指针、计数信号统一封装为 LSQInfoBundle。

# 模块固定结构

所有 Module 代码必须严格遵循固定顺序，分区清晰，彻底解决变量未定义报错、逻辑混杂、代码难读问题。

**标准固定顺序（背诵执行）**

1. **参数处理**：参数别名、参数计算、参数合法性校验、参数打印
2. **IO 端口定义**：加载独立命名的 IO Bundle
3. **IO 别名简化**：常用端口短名替换，减少重复代码
4. **子模块实例化**：统一 new 所有子模块，**只实例、不连线**
5. **Wire 线网定义**：所有组合逻辑、跨模块中间线提前定义
6. **Reg 寄存器定义**：时序逻辑寄存器、状态寄存器初始化
7. **寄存器更新逻辑**：集中更新所有寄存器
8. **状态机逻辑**：严格三段式状态机独立编写
9. **模块连线逻辑**：按模块集中统一连线
10. **性能/调试计数器**：perf、debug 信号统计

# 赋值核心规则：禁止分散 when（根治优先级隐性 Bug）

## 6.1 核心原理

**Chisel 代码后置赋值覆盖前置赋值，越靠后的 when 优先级越高**。若同一信号赋值散落多处，会产生隐性优先级，波形无异常、综合电路错误，极难排查。

## 6.2 禁止写法 & 正确写法

❌ 错误（分散赋值，隐性优先级）

```scala
val a = Wire(UInt(4.W))
a := 0.U
when (condA) { a := 1.U }
// 大量无关代码穿插
when (condB) { a := 2.U } // 实际优先级 condB > condA，代码无直观体现
```

✅ 强制规范（同一信号所有赋值集中在一个 when-elsewhen-otherwise）

```scala
val a = Wire(UInt(4.W))
when (condB) {
  a := 2.U
}.elsewhen (condA) {
  a := 1.U
}.otherwise {
  a := 0.U
}
```

核心优势：优先级一目了然、无隐性电路、彻底规避玄学 Bug

# 三段式状态机规范（香山唯一标准）

所有状态机**必须严格拆分三段，绝对禁止混写**，状态定义优先使用 EnumUInt，规避语法问题、支持波形精准对应。

## 7.1 三段式核心分工（互不干扰）

1. **状态寄存器更新**：仅刷新当前状态寄存器，只赋值 state
2. **下一状态计算**：纯组合逻辑，仅更新 stateNext，不修改当前状态
3. **状态输出逻辑**：根据当前状态生成控制信号，**不修改任何状态**

## 7.2 优先状态定义方式（EnumUInt 替代原生 Enum）

规避原生 Enum 解构语法报错，支持参数校验、位宽校验、独热码校验，适配硬件开发。

```scala
private object FsmState extends EnumUInt(2) {
  def Idle: UInt = 0.U(width.W)
  def Test: UInt = 1.U(width.W)
}
private val state = RegInit(FsmState.Idle)
private val stateNext = WireInit(state)
```

## 7.3 禁止行为

* 输出逻辑中修改状态寄存器
* 省略 otherwise 导致信号悬空
* 使用 DontCare 填充默认值、规避连线校验
* 状态跳转、状态更新、输出逻辑混写

# 模块连线规范（整洁无错、杜绝乱连线）

## 8.1 核心原则

**一个子模块的所有连线必须集中写在一起**，禁止东一条、西一条穿插连线。

## 8.2 两种标准连线方式

### 8.2.1 简单模块：直接连线

适用于无中间逻辑处理的简单子模块

```scala
// 模块A 集中连线
modA.io.in <> io.in
modA.io.fromB := modB.io.toA

// 模块B 集中连线
modB.io.flush := io.flush
modB.io.fromA := modA.io.toB
```

### 8.2.2 复杂模块：先定义中间 Wire（强制）

模块互相嵌套、需要信号运算/选择时，必须提前新建 Wire，**禁止使用别名替代硬件线**

❌ 错误（别名：无新硬件，仅引用原信号，易引发逻辑错乱）

```scala
val AToB = modA.io.toB
```

✅ 正确（新建 Wire：生成独立硬件线，逻辑隔离）

```scala
val AToB = Wire(modA.io.toB.cloneType)
val BToA = Wire(modB.io.toA.cloneType)
// 统一连线
modA.io.fromB := BToA
AToB := modA.io.toB
```

# 变量修饰符、类型标注与静态常量规范（private / public / final）

本章统一规范 Chisel/Scala 中**变量类型标注、修饰符使用、静态常量定义**的强制标准，包含 private、public、final（static 替代）、多修饰符组合顺序、公开成员类型约束等核心规则，解决工程权限滥用、封装混乱、常量不规范、隐式推导报错等高频问题，为香山强制校验规则。

核心原则：**默认私有、按需公开、公开必标注、常量必终态、严格封装、杜绝全局滥用**。

## 基础认知：Scala 与 Java 修饰符差异

* Scala **默认权限为 public**，不写修饰符即公开，无显性 public 关键字声明。
* Scala 无 Java 原生 `static`，统一使用 **final + object 全局常量** 实现静态效果。
* Chisel 硬件开发必须严格手动控权限，禁止依赖默认 public 开放所有成员。

## 修饰符组合顺序（固定强制，不可乱序）

多修饰符叠加时，严格遵循固定顺序，与官方规范、Scalastyle 校验对齐，禁止随意调换顺序：

**override → private/protected → implicit → final → def/val**

✅ 正确示例：`private final val MaxBufDepth: Int = 128`

❌ 错误示例：`final private val MaxBufDepth: Int = 128`

## 私有权限 private（工程强制优先使用）

所有模块内部资源，**默认全部私有**，仅对外交互端口允许公开，最小化暴露域，规避跨模块误修改、隐性电路变更。

### 必须使用 private 的场景

* 模块内部所有 Wire、Reg、中间组合信号、临时变量
* 模块内部实例化的子模块、局部工具对象
* 内部辅助判断条件、局部计算变量、临时计数
* 仅内部调用的工具方法、逻辑处理函数

### 正确与错误示例

❌ 错误（默认公开，权限泛滥、存在隐性风险）

```scala
val cnt = RegInit(0.U(4.W))
val validWire = Wire(Bool())
def calcResult(): UInt = { /* ... */ }
```

✅ 强制正确（内部资源全部私有，封装合规）

```scala
private val cnt = RegInit(0.U(4.W))
private val validWire = Wire(Bool())
private def calcResult(): UInt = { /* ... */ }
```

## 公开权限 public（严格限制，禁止滥用）

Scala 无显性 public 关键字，**不写修饰符即为 public**，该权限仅允许用于对外交互资源，其余场景一律禁止。

### 仅允许公开的场景

* 模块顶层 `io` 端口（唯一默认公开成员）
* 跨模块必须调用的全局工具方法、通用工具类
* 全局统一枚举、参数样例类、公共配置常量

### 严格禁止的公开行为

* 禁止将内部寄存器、中间线网、临时计算变量设为公开
* 禁止将模块内部辅助方法对外开放
* 禁止为了方便连线，随意将内部信号改成公开权限

核心原因：公开信号会暴露内部硬件逻辑，极易被外部误赋值、误引用，产生隐性电路 Bug，同时破坏模块封装性。

## 类型标注强制规范（公私区分）

统一 Chisel/Scala 类型推导规则，规避隐式推导导致的类型错乱、编译告警、硬件位宽异常问题。

* **公开成员必须显式标注类型**：所有 public 的 val、def、IO 端口、全局常量、公共方法，禁止依赖隐式类型推导，保证类型透明、可校验、可复用。
* **私有成员可省略标注**：模块内部 private 的变量、方法、临时信号，可省略类型标注，由编译器自动推导，简化冗余代码。
* **常量强制精准标注**：所有 final 全局常量，必须手动标注基础类型与硬件位宽，禁止模糊推导。

## final 静态常量规范

Scala/Chisel 无 static 关键字，**全局常量、硬件固定参数、编码常量统一使用 final 修饰**，放置在独立 object 中，实现静态全局调用效果，完全替代 Java static 能力。

### final 使用强制规则

* 所有硬件固定常量、位宽定义、编码值、默认参数，必须加 **final**
* 全局常量统一放入 `object XXXConst` 单例对象，禁止散落在模块内部
* 常量命名严格使用 **大驼峰**，符合香山统一命名规范
* 禁止使用 var 定义常量，所有静态常量必须为 val + final

### 标准静态常量模板

```scala
final val PageOffsetWidth = 12
```

## 工程避坑总结

* **能私有绝不公开**：内部所有线网、寄存器、方法默认 private，杜绝默认public隐患
* **公开必标类型**：所有对外暴露的成员禁止隐式推导，统一显式标注类型
* **常量必final、必归object**：杜绝模块内散落魔数、零散常量
* **禁止滥用公开权限**：不允许为了省事开放内部信号，破坏封装层级
* **修饰符顺序严格对齐**：规避格式校验告警、统一代码审美

# EnumUInt 常量规范

香山新增 EnumUInt 工具类，自带参数校验、位宽校验、独热码校验，规避常量定义错误，优先替代 NamedUInt、原生 ChiselEnum。

## 10.1 核心校验规则（高频错误避坑）

* 常量数量与实际定义个数必须匹配，允许重复值需开启 `allowDuplicate = true`
* 常量方法必须大驼峰，小写方法不会被识别为常量
* 独热码模式需开启 `useOneHot = true`，严格校验独热编码
* 所有常量必须显式指定位宽 `width.W`，禁止默认1位宽

## 10.2 与 ChiselEnum 取舍

* EnumUInt：优势是自带独热码、固定位宽校验，适配现有代码
* ChiselEnum：优势是类型安全，需手动转换 UInt，适合强类型场景
* 当前规范：优先使用 EnumUInt，后续会融合两者优势优化

# 参数化设计规范（分层解耦）

所有模块参数禁止零散定义，统一使用「样例类+特质解包」模式，分层管理参数，支持参数校验、全局复用，减少编译开销。

## 11.1 标准模板

```scala
// 1. 定义参数样例类（带默认值、参数校验）
case class AaaParameters(
  p1: Int = 100,
  p2: Boolean = true
) {
  require(p1 < 1000, "p1 参数超出合法范围")
}

// 2. 定义参数解包特质
trait HasAaaParameters extends HasXSParameters {
  def aaaParams: AaaParameters = coreParams.aaaParams
  def p1: Int = aaaParams.p1
  def p2: Boolean = aaaParams.p2
}

// 3. 模块继承特质，直接使用参数
class Aaa extends Module with HasAaaParameters {
  // 直接使用 p1、p2，无需重复写 aaaParams
}
```

## 11.2 子模块参数规范

多级模块参数逐级嵌套，子模块参数类挂载到父参数类，解包特质继承父级特质，禁止直接继承顶层参数，保证层级清晰。

# 寄存器打拍与门控规范（时序优化）

统一寄存器打拍方式，兼顾时序、面积、功耗，杜绝随意使用寄存器导致的时序问题。

* 控制信号 valid 打拍：强制使用 RegNext，禁止 GatedRegNext
* 大位宽 bits 数据打拍：强制使用 RegEnable，禁止门控寄存器
* 小位宽、低翻转频率数据：可使用 GatedRegNext 优化功耗

# 代码语法与排版细则

## 13.1 空格规范

* 函数/关键字与括号之间：when(、if( 无空格
* 括号、大括号前后、运算符两侧：必须加单个空格
* 禁止多空格、行尾空格、空行残留空格

## 13.2 换行与空行规范

* 单行代码≤120字符，超长手动拆分，复杂逻辑分段换行
* 不同功能代码块之间加空行分隔，禁止连续2行及以上空行
* 文件首行无空行，文件末尾必须留一个空行

## 13.3 注释规范

* 禁止中文注释、中文字符，所有注释使用英文
* 单行注释 `//` 与内容之间留1个空格，代码行尾注释与代码之间留1个空格
* 代码块必须加功能注释，连续10行无注释视为不规范
* 使用层级注释（大标题、小标题）梳理代码结构

## 13.4 杜绝冗余写法

* 禁止多余括号、无意义分号
* 禁止使用魔数，所有常量必须命名定义
* 重复逻辑抽象为通用函数，禁止复制粘贴

# 括号、大括号、空格专项统一规范

本节整合全文所有 `()`、`{}`、空格 相关强制规范，统一书写标准、解决格式报错、对齐错乱、CI 校验失败等高频问题，为代码格式化核心必守规则，所有场景无特殊豁免，严格执行。

## 14.1 小括号 () 规范

* **关键字/函数紧贴括号**：`when(`、`if(`、`def 方法名(`、`UInt(8.W)` 等场景，括号前**禁止加空格**，杜绝多余空格报错。
* **括号内侧无空格**：圆括号内部首尾禁止多余空格，示例：`UInt(4.W)` 正确，`UInt( 4.W )` 错误。
* **括号外侧必加单空格**：独立括号表达式、参数括号结束后，外侧需保留单个标准空格，适配 Scalafmt 自动对齐规则。
* **禁止冗余括号**：逻辑运算、赋值、条件判断中，无需嵌套的多余括号必须删除，保持代码简洁无冗余。

## 14.2 大括号 {} 规范

* **大括号前留单空格**：`when() {`、`elsewhen() {`、`Bundle {`、`类/方法实现 {` 等所有场景，左大括号前必须保留一个标准空格。
* **禁止首行嵌套无换行**：大括号开启代码块后，内部逻辑必须另起新行，禁止行内嵌套逻辑，保证层级清晰。
* **代码块闭合规范**：右大括号独立成行（极简短逻辑除外，以 Scalafmt 自动格式化结果为准），对齐对应代码块起始位置。
* **功能块空行分隔**：不同大括号包裹的独立逻辑块、函数、代码段之间，必须加空行分隔，规避格式化错乱、对齐异常问题。

## 14.3 全局空格统一规范

* **运算符两侧必加单空格**：赋值 `:=`、加减乘除、逻辑与或、比较运算符等，两侧统一保留单个空格，禁止无空格、多空格混搭。
* **注释固定空格规则**：单行注释 `//` 后必须留1个空格；代码行尾注释与前置代码之间，必须留1个空格。
* **禁止各类多余空格**：杜绝行尾空格、空行残留空格、代码段首尾多余空格、连续多空格对齐（仅允许 Scalafmt 自动对齐空格）。
* **特殊语法空格豁免**：仅 `UInt*` 可变参数符号，无需遵循两侧加空格规则，以 Scalafmt 格式化结果为准，忽略 Scalastyle 误告警。
* **缩写符号无特殊空格**：ICache、Ifu、Ras 等标准缩写视为完整单词，空格规则与普通单词完全一致，无需特殊适配。

# 高效开发工具：DataView 自动连线

摒弃手动逐字段连线，使用 Chisel DataView 工具实现 Bundle 自动映射，大幅减少冗余代码、连线错误。

核心用法：导入工具包后，通过 `viewAsSupertype` 自动完成父子 Bundle 字段映射，无需逐行赋值。

# Git 提交与 PR 规范

## 16.1 分支命名前缀规范

* fix：Bug 修复
* feat：新增功能
* refactor：代码重构（不改功能）
* style：格式优化
* perf：性能优化
* timing/area/power：时序、面积、功耗优化
* docs/chore/ci：文档、工程配置、CI 流程修改

## 16.2 Commit 规范

* 格式：`类型[模块]: 简短描述` + 空行 + 详细说明
* 标题≤50字符，正文每行≤72字符，首字母大写、语句完整、无句尾标点
* 一个 commit 只做一件事，禁止大跨度、多改动合并提交

## 16.3 PR 合入前置要求

* 代码编译通过，无格式、规范告警
* 提交前执行 `make reformat`格式化，人工复核格式
* Bug 修复需验证对应场景，性能改动需完成基础测试
* 时序/面积影响改动，需提前完成物理后端评估
* 清理废弃注释、冗余代码

# 规范速记

* **工具校验**：Scalafmt 强制 CI 校验，提交必格式化；Scalastyle 辅助规范检查
* **赋值集中唯一**：一个信号只在一处赋值，杜绝分散 when 优先级 Bug
* **IO 规范严谨**：全部命名 Bundle、分层封装、禁止匿名、模块名全局唯一
* **命名统一合规**：全程驼峰，仅流水级、调试信号可下划线
* **状态机三段分离**：更新、跳转、输出完全独立，优先 EnumUInt 定义状态
* **模块连线集中**：单模块连线聚合，复杂场景新建 Wire，禁止别名滥用
* **参数分层管理**：样例类定义参数，特质解包，逐级嵌套
* **提交规范严格**：单Commit单功能、格式合规、注释干净、编译通过
* **工具校验**：Scalafmt 强制 CI 校验，提交必格式化；Scalastyle 辅助规范检查
* **代码顺序固定**：参数→IO→别名→子模块→线网→寄存器→逻辑→状态机→连线→计数器
* **赋值集中唯一**：一个信号只在一处赋值，杜绝分散 when 优先级 Bug
* **IO 规范严谨**：全部命名 Bundle、分层封装、禁止匿名、模块名全局唯一
* **命名统一合规**：全程驼峰，仅流水级、调试信号可下划线
* **状态机三段分离**：更新、跳转、输出完全独立，优先 EnumUInt 定义状态
* **模块连线集中**：单模块连线聚合，复杂场景新建 Wire，禁止别名滥用
* **参数分层管理**：样例类定义参数，特质解包，逐级嵌套
* **提交规范严格**：单Commit单功能、格式合规、注释干净、编译通过

# 参考素材

1. Java: <https://google.github.io/styleguide/javaguide.html>
2. Scala: <https://docs.scala-lang.org/style/>
3. Chisel: <https://www.chisel-lang.org/docs/developers/style>


> 更新: 2026-06-23 14:13:59  
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/etidwiqvoguqfx5z>
