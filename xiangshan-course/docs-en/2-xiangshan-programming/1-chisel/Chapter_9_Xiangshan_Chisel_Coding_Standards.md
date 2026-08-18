<!-- # 第九章 香山 Chisel 代码规范 -->
# Chapter 9: XiangShan Chisel Coding Standards

<!-- # 前言 -->
# Preface

<!-- 这份规范是香山处理器**最新官方强制工程标准**，适配前端重构场景（ICache、Ifu、InstrUncache 已完成落地），兼容原有规范 v2 且规则更严格。 -->
This standard is the XiangShan processor's **latest official mandatory engineering standard**. It supports front-end refactoring scenarios (the ICache, Ifu, and InstrUncache refactors have been completed), remains compatible with the original v2 standard, and imposes stricter rules.

<!-- 核心宗旨：**代码整齐无歧义、硬件无隐性 Bug、可读性拉满、适配自动化校验、方便迭代维护、开源友好**。 -->
Core goals: **unambiguous, well-organized code; no hidden hardware bugs; maximum readability; compatibility with automated checks; easy iteration and maintenance; and an open-source-friendly style**.

<!-- 适用范围：香山仓库及子仓库 Scala 代码，当前优先在 MemBlock、L2 模块试用，后续全仓库推广。 -->
Scope: Scala code in the XiangShan repository and its subrepositories. It is currently being applied first to the MemBlock and L2 modules and will later be rolled out across the repository.

<!-- # 代码格式化工具规范（CI 强制校验） -->
# Code-Formatting Tool Standards (CI-Enforced Checks)

<!-- 香山前端已启用两套代码规范校验工具，分工明确，覆盖格式、命名、文件、语法全场景，是代码提交的基础门槛。 -->
The XiangShan front end uses two code-standard checking tools with clearly separated responsibilities. Together they cover formatting, naming, files, and syntax, and form the basic gate for code submission.

<!-- ## 1.1 Scalafmt（强制格式化，CI 必检） -->
## 1.1 Scalafmt (Mandatory Formatting, Required by CI)

<!-- 核心作用：自动统一代码缩进、对齐、折行、空格、换行，解决代码排版混乱问题，**未通过校验会直接导致 CI 报错，无法合入代码**。 -->
Core role: automatically standardize indentation, alignment, line wrapping, spaces, and line breaks. It resolves layout inconsistencies, and **a failed check makes CI fail and prevents the code from being merged**.

<!-- 配置文件：仓库根目录 `.scalafmt.conf` -->
Configuration file: `.scalafmt.conf` in the repository root.

<!-- ### 1.1.1 本地校验/格式化命令 -->
### 1.1.1 Local Check and Formatting Commands

<!-- * 检查代码是否合规：`make check-format` -->
* Check whether the code complies: `make check-format`
<!-- * 自动修复所有格式问题：`make reformat` -->
* Automatically fix all formatting issues: `make reformat`
<!-- * 注意目前make check-format reformat 只会修改前端代码，如要修改/检测自己的代码需要手动修改`.scalafmt.conf`添加自己的文件 -->
* Note that `make check-format` and `make reformat` currently modify only front-end code. To format or check your own code, manually add its files to `.scalafmt.conf`.

<!-- ### 1.1.2 IDE 自动格式化配置 -->
### 1.1.2 IDE Automatic Formatting Configuration

<!-- VS Code、IDEA 均支持保存自动格式化，无需手动执行命令，推荐全员开启。同时可配置 Git 提交钩子，实现提交前自动校验，杜绝不合格代码提交： -->
Both VS Code and IDEA support formatting on save, so commands do not need to be run manually; enabling it for everyone is recommended. A Git commit hook can also run an automatic pre-commit check to prevent non-compliant code from being submitted:

<!-- 将以下脚本命名为 `pre-commit`，放入对应 Git 钩子目录并赋予可执行权限，提交代码时自动校验格式，失败则禁止提交： -->
Name the following script `pre-commit`, place it in the appropriate Git hook directory, and make it executable. Formatting is checked automatically when code is committed, and a failure blocks the commit:

```bash
#!/bin/bash
make check-format 2>/dev/null | grep -v '^file excluded'
if [ ${PIPESTATUS[0]} -ne 0 ]; then
    echo "Format checking failed, refusing to commit"
    echo "hint: Run 'make reformat' will resolve this issue"
    exit 1
fi
```

<!-- 钩子文件目录： -->
Hook file locations:

<!-- * XiangShan 原生环境：`.../XiangShan/.git/hooks` -->
* Native XiangShan environment: `.../XiangShan/.git/hooks`
<!-- * xs-env 环境：`.../xs-env/.git/modules/XiangShan/hooks` -->
* `xs-env` environment: `.../xs-env/.git/modules/XiangShan/hooks`

<!-- ### 1.1.3 重点避坑规则（高频报错） -->
### 1.1.3 Key Pitfall-Avoidance Rule (Frequent Formatting Error)

<!-- Scalafmt 会自动对齐函数返回值，容易出现误对齐问题，**所有独立函数之间必须加空行**，解决对齐报错： -->
Scalafmt automatically aligns function return types, which can result in accidental alignment. **Every independent function must be separated from the next by a blank line** to avoid alignment errors:

<!-- ❌ 错误写法（无空行，格式化错乱） -->
❌ Incorrect form (no blank lines; formatting becomes inconsistent)

```scala
def +(offset:  UInt):       PrunedAddr = PrunedAddrInit(toUInt + offset)
def +(that:    PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt + that.toUInt)
def -(that:    PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt - that.toUInt)
```

<!-- ✅ 正确写法（函数间空行分隔，格式合规） -->
✅ Correct form (blank lines between functions; formatting compliant)

```scala
def +(offset: UInt): PrunedAddr = PrunedAddrInit(toUInt + offset)

def +(that: PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt + that.toUInt)

def -(that: PrunedAddr): PrunedAddr = PrunedAddrInit(toUInt - that.toUInt)
```

<!-- 补充：Chisel 中 when、elsewhen 属于函数而非关键字，必须和普通函数保持一致的空格格式，禁止特殊对待。 -->
Additional note: In Chisel, `when` and `elsewhen` are functions rather than keywords. They must use the same spacing style as ordinary functions and must not be treated as special cases.

<!-- ### 1.1.4 特殊场景忽略格式化 -->
### 1.1.4 Ignoring Formatting in Special Cases

<!-- 仅允许**大量重复匹配逻辑**（如指令解码数组）关闭格式化，其余场景禁止跳过。使用 `// format off` 和 `// format on` 包裹特殊代码段即可。 -->
Only **large amounts of repetitive matching logic** (such as instruction-decoding arrays) may disable formatting. Skipping formatting elsewhere is prohibited. Wrap the special code section with `// format off` and `// format on`.

<!-- ## 1.2 Scalastyle（规范校验，辅助检查） -->
## 1.2 Scalastyle (Standards Checking, Auxiliary Inspection)

<!-- 核心作用：补充 Scalafmt 缺失的校验规则，负责文件头、命名、导入、类型标注、文件大小检查，**无自动修复功能、不接入 CI、仅开发提醒**。 -->
Core role: supplement Scalafmt's missing checks by inspecting file headers, naming, imports, type annotations, and file size. It **does not auto-fix issues, is not integrated into CI, and serves only as a development reminder**.

<!-- 配置更新：原有 2019 年旧配置已过时，最新优化配置已提 PR（#4329），当前校验规则与本规范完全对齐。 -->
Configuration update: The original 2019 configuration is obsolete. An updated configuration has been proposed in PR #4329, and the current checks are fully aligned with this standard.

<!-- IDE 支持：IDEA 可直接开启检查（编辑器-检查-scala-代码样式-scala样式检查）；VS Code 暂无插件，可通过官方命令行手动校验。 -->
IDE support: IDEA can enable the check directly (Editor → Inspections → Scala → Code Style → Scala Style Inspection). VS Code has no plugin yet, so use the official command line for manual checks.

<!-- ### 1.2.1 核心强制检查项 -->
### 1.2.1 Core Mandatory Checks

<!-- 1. **文件头检查**：必须遵循「至少一行Copyright + 空行 + Mulan License」固定格式，格式错误直接告警。 -->
1. **File-header check**: Follow the fixed format "at least one Copyright line + a blank line + the Mulan License"; format errors produce an immediate warning.
<!-- 2. **命名检查**：class/object 大驼峰、方法/变量小驼峰，trait、特殊变量需手动合规。 -->
2. **Naming check**: Use UpperCamelCase for classes and objects and lowerCamelCase for methods and variables; manually ensure that traits and special variables comply.
<!-- 3. **Import 检查**：禁止块导入、禁止通配符导入（仅 chisel3.*、chisel3.util.* 豁免）。 -->
3. **Import check**: Block imports and wildcard imports are prohibited, except for `chisel3.*` and `chisel3.util.*`.
<!-- 4. **类型标注**：所有公开成员必须手动添加类型标注，禁止隐式推导。 -->
4. **Type annotations**: Every public member must have an explicit type annotation; implicit inference is prohibited.
<!-- 5. **文件可读性检查**：单文件≤800行、单行≤120字符、单类方法≤30个、单方法≤50行。 -->
5. **File-readability check**: A file must contain no more than 800 lines, a line no more than 120 characters, a class no more than 30 methods, and a method no more than 50 lines.

<!-- ### 1.2.2 工具局限性（无需兼容） -->
### 1.2.2 Tool Limitation (No Compatibility Required)

<!-- Scalastyle 无法区分符号语义，会误判可变参数符号 `UInt*`，要求两侧加空格，与 Scalafmt 冲突，**以 Scalafmt 格式化结果为准，忽略该告警**。 -->
Scalastyle cannot distinguish the symbol's meaning and misinterprets the varargs symbol `UInt*`, requiring spaces on both sides and conflicting with Scalafmt. **Follow the Scalafmt result and ignore this warning.**

<!-- # 全局命名规范 -->
# Global Naming Standards

<!-- 所有代码命名**统一驼峰式**，禁止随意大小写、下划线、缩写乱象，保证 Chisel 代码与生成的 Verilog 信号一一对应，波形调试无歧义。 -->
All code names must consistently use **camel case**. Arbitrary capitalization, underscores, and inconsistent abbreviations are prohibited, ensuring a one-to-one correspondence between Chisel code and generated Verilog signals and unambiguous waveform debugging.

<!-- ## 2.1 基础命名规则 -->
## 2.1 Basic Naming Rules

<!-- * **大驼峰（UpperCamelCase）**：类、特征、单例对象、常量、状态枚举 -->
* **UpperCamelCase**: classes, traits, singleton objects, constants, and state enumerations
<!-- * **小驼峰（lowerCamelCase）**：方法、普通变量、IO 端口、线网、寄存器 -->
* **lowerCamelCase**: methods, ordinary variables, IO ports, wires, and registers
<!-- * **缩写规则**：缩写统一视为单个单词（ICache、Ifu、Ras），禁止 Icache、IFUWBPtr 这类不规范写法；短缩写建议写全称（UCnt → UsefulCnt） -->
* **Abbreviations**: Treat an abbreviation as one word (`ICache`, `Ifu`, `Ras`). Forms such as `Icache` and `IFUWBPtr` are prohibited; expand short abbreviations where practical (`UCnt` → `UsefulCnt`).
<!-- * **全局禁止**：常规命名使用下划线、全大写、全小写混搭 -->
* **Globally prohibited**: underscores, all-uppercase names, and mixed all-uppercase/all-lowercase styles in ordinary names

<!-- ## 2.2 特殊豁免场景（仅以下情况可使用下划线） -->
## 2.2 Special Exemptions (Only These Cases May Use Underscores)

<!-- * 流水级信号：s1\_valid、s2\_ready（s0/s1/s2 前缀） -->
* Pipeline-stage signals: `s1_valid`, `s2_ready` (with an `s0`/`s1`/`s2` prefix)
<!-- * 调试/性能信号：debug\_xxx、perf\_xxx（最终交付 RTL 需剔除） -->
* Debug/performance signals: `debug_xxx`, `perf_xxx` (must be removed from the final delivered RTL)
<!-- * 多组 IO 区分：io\_xxx（尽量少用） -->
* Distinguishing multiple IO groups: `io_xxx` (use sparingly)
<!-- * 状态机标识：s\_idle、s\_busy -->
* State-machine markers: `s_idle`, `s_busy`

<!-- ## 2.3 模块命名专属规则（解决 Verilog 重名后缀问题） -->
## 2.3 Module Naming Rules (Avoiding Verilog Duplicate-Name Suffixes)

<!-- Scala 仅保证包内类唯一，但不同包同名 Module 会导致 Verilog 自动加 `_1` 后缀，无法区分模块，**所有自定义 Module 必须全局唯一命名**。 -->
Scala guarantees unique class names only within a package. Modules with the same name in different packages cause Verilog to append an automatic `_1` suffix, making them difficult to distinguish. **Every custom `Module` must therefore have a globally unique name.**

<!-- ❌ 错误写法（重名冲突） -->
❌ Incorrect form (duplicate-name conflict)

```scala
// <!-- icache 模块 -->
// ICache module
class CtrlUnit extends ICacheModule
// <!-- dcache 模块 -->
// DCache module
class CtrlUnit extends XSModule
// <!-- 最终生成：CtrlUnit.sv、CtrlUnit_1.sv，无法区分 -->
// Generated files: CtrlUnit.sv and CtrlUnit_1.sv, which cannot be distinguished.
```

<!-- ✅ 正确写法（模块名带归属前缀，全局唯一） -->
✅ Correct form (a module name with an ownership prefix; globally unique)

```scala
class ICacheCtrlUnit extends ICacheModule
class DCacheCtrlUnit extends XSModule
// <!-- 最终生成：ICacheCtrlUnit.sv、DCacheCtrlUnit.sv，清晰对应 -->
// Generated files: ICacheCtrlUnit.sv and DCacheCtrlUnit.sv, with a clear correspondence.
```

<!-- ## 2.4 统一命名习惯（杜绝乱象） -->
## 2.4 Consistent Naming Habits (Eliminate Inconsistency)

<!-- * 缩写统一：frm→from、excp→exception、buf→buffer、DCache（非Dcache） -->
* Standardize abbreviations: `frm` → `from`, `excp` → `exception`, `buf` → `buffer`, `DCache` (not `Dcache`)
<!-- * 同一语义全程统一：misalign 系列变量禁止 mis\_align、misAlign、misalign\_buf 混搭 -->
* Keep one spelling for one meaning: do not mix `mis_align`, `misAlign`, and `misalign_buf` for the `misalign` family of variables.
<!-- * 命名直观：变量名无需注释即可看懂含义，禁止模糊、歧义命名 -->
* Make names self-explanatory: a variable's meaning should be clear without a comment; ambiguous names are prohibited.

<!-- # 文件与包结构规范（统一工程目录） -->
# File and Package Structure Standards (Unified Project Layout)

<!-- ## 3.1 文件命名规则 -->
## 3.1 File-Naming Rules

<!-- * 基础规则：文件名与文件内唯一主类名完全一致（Ftq.scala 对应 Ftq 类） -->
* Basic rule: The file name must exactly match the only primary class name in the file (`Ftq.scala` corresponds to class `Ftq`).
<!-- * 合法特例：多个关联IO、LazyModule+LazyModuleImp、密封类及其子类可放同一文件 -->
* Valid exceptions: multiple related IO definitions, `LazyModule` + `LazyModuleImp`, and a sealed class with its subclasses may share one file.
<!-- * 通用组件：SRAMTemplate、FIFO 等通用工具模块，统一放入 Utility 仓库 -->
* Common components: Place generic utility modules such as `SRAMTemplate` and `FIFO` in the Utility repository.

<!-- ## 3.2 包与目录规范 -->
## 3.2 Package and Directory Standards

<!-- * 包名**全小写**，紧跟文件 License 之后声明，禁止随意命名 -->
* Package names must be **all lowercase**, declared immediately after the file's License, and must not be arbitrary.
<!-- * 目录、包、硬件结构三者统一：主模块单独建目录，子模块归属主模块包 -->
* Keep directories, packages, and hardware structure consistent: create a separate directory for the top module, and place child modules in the top module's package.
<!-- * 示例：Ifu 主模块路径 `src/scala/xiangshan/frontend/ifu/Ifu.scala`，子模块 PreDecode 同属该包 -->
* Example: The Ifu top module is at `src/scala/xiangshan/frontend/ifu/Ifu.scala`; its child module `PreDecode` belongs to the same package.

<!-- ## 3.3 Import 导入规范 -->
## 3.3 Import Standards

<!-- * 禁止混合导入：不能同时导入包全部（xxx.\_）和包内单个成员 -->
* No mixed imports: do not import an entire package (`xxx._`) and individual members of that package at the same time.
<!-- * 禁止非法通配符：除 chisel3.*、chisel3.util.* 外，所有 xxx.\_ 导入禁止使用 -->
* No unauthorized wildcards: except for `chisel3.*` and `chisel3.util.*`, all `xxx._` imports are prohibited.
<!-- * 导入排序：按字典序排列，Scalafmt 可自动修复，禁止手动乱序 -->
* Import ordering: sort imports lexicographically. Scalafmt can fix the order automatically; manual disorder is prohibited.
<!-- * 减少跨包导入：同级包尽量不互相导入，公共逻辑统一抽离到父包 -->
* Minimize cross-package imports: sibling packages should avoid importing one another; move shared logic into the parent package.

<!-- # Bundle 接口规范 -->
# `Bundle` Interface Standards

<!-- ## 4.1 核心强制规则 -->
## 4.1 Core Mandatory Rules

<!-- 1. **绝对禁止匿名 Bundle**：所有模块 IO 必须独立命名 Bundle 类，支持 IDE 跳转、复用 -->
1. **Anonymous `Bundle`s are strictly prohibited**: every module IO must use a separately named `Bundle` class to support IDE navigation and reuse.
<!-- 2. **分层结构化**：同类信号、模块交互信号统一封装子 Bundle，禁止零散平铺 -->
2. **Use hierarchical structure**: group related signals and module-interaction signals in child `Bundle`s; do not scatter them as flat fields.
<!-- 3. **信号方向分离**：优先区分输入/输出 Bundle，禁止同一 Bundle 混杂多方向信号（Valid/Decoupled 接口除外） -->
3. **Separate signal directions**: prefer distinct input and output `Bundle`s; do not mix signals with different directions in one `Bundle` (except for `Valid`/`Decoupled` interfaces).
<!-- 4. **控制信号独立**：纯控制信号使用 ValidIO/DecoupledIO，禁止与普通数据信号混编，避免门控异常 -->
4. **Keep control signals independent**: use `ValidIO`/`DecoupledIO` for pure control signals; do not mix them with ordinary data signals, which can cause gating anomalies.

<!-- ## 4.2 优劣写法对比 -->
## 4.2 Comparison of Poor and Good Forms

<!-- ❌ 错误写法（匿名、信号零散、层级混乱） -->
❌ Incorrect form (anonymous, scattered signals, and confused hierarchy)

```scala
val io = IO(new Bundle{
  val xxx = UInt(8.W)
  val yyy = Bool()
  val zzz_a = UInt(4.W)
  val zzz_b = Bool()
})
```

<!-- ✅ 正确写法（分层命名、结构清晰、可复用） -->
✅ Correct form (hierarchically named, clear, and reusable)

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

<!-- ## 4.3 高阶规范：功能聚合 Bundle -->
## 4.3 Advanced Rule: Functional Aggregation Bundles

<!-- 多模块交互的零散状态、计数、指针信号，统一封装 Info 类 Bundle，大幅简化顶层连线，避免漏连、错连。典型场景：LSQ 队列空满、指针、计数信号统一封装为 LSQInfoBundle。 -->
Group scattered state, counter, and pointer signals exchanged by multiple modules into an `Info`-style `Bundle`. This greatly simplifies top-level wiring and prevents missing or incorrect connections. A typical example is grouping LSQ queue empty/full status, pointers, and counters into `LSQInfoBundle`.

<!-- # 模块固定结构 -->
# Fixed Module Structure

<!-- 所有 Module 代码必须严格遵循固定顺序，分区清晰，彻底解决变量未定义报错、逻辑混杂、代码难读问题。 -->
Every `Module` implementation must follow a fixed order with clearly separated sections. This eliminates undefined-variable errors, mixed logic, and unreadable code.

<!-- **标准固定顺序（背诵执行）** -->
**Standard fixed order (memorize and follow)**

<!-- 1. **参数处理**：参数别名、参数计算、参数合法性校验、参数打印 -->
1. **Parameter handling**: parameter aliases, parameter calculation, validity checks, and parameter printing
<!-- 2. **IO 端口定义**：加载独立命名的 IO Bundle -->
2. **IO port definition**: load a separately named IO `Bundle`
<!-- 3. **IO 别名简化**：常用端口短名替换，减少重复代码 -->
3. **IO aliasing**: use short aliases for frequently used ports to reduce repeated code
<!-- 4. **子模块实例化**：统一 new 所有子模块，**只实例、不连线** -->
4. **Child-module instantiation**: instantiate all child modules in one place; **instantiate only, do not wire**
<!-- 5. **Wire 线网定义**：所有组合逻辑、跨模块中间线提前定义 -->
5. **`Wire` definitions**: define all combinational logic and cross-module intermediate wires in advance
<!-- 6. **Reg 寄存器定义**：时序逻辑寄存器、状态寄存器初始化 -->
6. **`Reg` definitions**: define sequential-logic registers and initialize state registers
<!-- 7. **寄存器更新逻辑**：集中更新所有寄存器 -->
7. **Register-update logic**: update all registers in one section
<!-- 8. **状态机逻辑**：严格三段式状态机独立编写 -->
8. **State-machine logic**: write the three state-machine sections separately and strictly
<!-- 9. **模块连线逻辑**：按模块集中统一连线 -->
9. **Module wiring logic**: group and centralize wiring by module
<!-- 10. **性能/调试计数器**：perf、debug 信号统计 -->
10. **Performance/debug counters**: collect `perf` and `debug` signal statistics

<!-- # 赋值核心规则：禁止分散 when（根治优先级隐性 Bug） -->
# Core Assignment Rule: Prohibit Scattered `when` Assignments (Eliminate Hidden Priority Bugs)

<!-- ## 6.1 核心原理 -->
## 6.1 Core Principle

<!-- **Chisel 代码后置赋值覆盖前置赋值，越靠后的 when 优先级越高**。若同一信号赋值散落多处，会产生隐性优先级，波形无异常、综合电路错误，极难排查。 -->
**A later assignment in Chisel code overrides an earlier assignment, so a later `when` has higher priority.** If assignments to one signal are scattered across multiple locations, hidden priority is introduced: the waveform may look normal while the synthesized circuit is wrong, making the issue extremely difficult to diagnose.

<!-- ## 6.2 禁止写法 & 正确写法 -->
## 6.2 Prohibited and Correct Forms

<!-- ❌ 错误（分散赋值，隐性优先级） -->
❌ Incorrect (scattered assignments and hidden priority)

```scala
val a = Wire(UInt(4.W))
a := 0.U
when (condA) { a := 1.U }
// <!-- 大量无关代码穿插 -->
// A large amount of unrelated code is interleaved here.
when (condB) { a := 2.U }
// <!-- 实际优先级 condB > condA，代码无直观体现 -->
// The actual priority is condB > condA, which is not obvious from the code.
```

<!-- ✅ 强制规范（同一信号所有赋值集中在一个 when-elsewhen-otherwise） -->
✅ Mandatory form (all assignments to one signal are grouped in one `when`-`elsewhen`-`otherwise` chain)

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

<!-- 核心优势：优先级一目了然、无隐性电路、彻底规避玄学 Bug -->
Key benefits: priority is immediately visible, no hidden circuit behavior is introduced, and elusive bugs are avoided.

<!-- # 三段式状态机规范（香山唯一标准） -->
# Three-Part State-Machine Standard (XiangShan's Sole Standard)

<!-- 所有状态机**必须严格拆分三段，绝对禁止混写**，状态定义优先使用 EnumUInt，规避语法问题、支持波形精准对应。 -->
Every state machine **must be strictly split into three parts; mixing them is absolutely prohibited**. Prefer `EnumUInt` for state definitions to avoid syntax problems and enable precise waveform correlation.

<!-- ## 7.1 三段式核心分工（互不干扰） -->
## 7.1 Responsibilities of the Three Parts (Independent of One Another)

<!-- 1. **状态寄存器更新**：仅刷新当前状态寄存器，只赋值 state -->
1. **State-register update**: refresh only the current state register and assign only `state`.
<!-- 2. **下一状态计算**：纯组合逻辑，仅更新 stateNext，不修改当前状态 -->
2. **Next-state calculation**: pure combinational logic that updates only `stateNext` and does not modify the current state.
<!-- 3. **状态输出逻辑**：根据当前状态生成控制信号，**不修改任何状态** -->
3. **State-output logic**: generate control signals from the current state and **modify no state**.

<!-- ## 7.2 优先状态定义方式（EnumUInt 替代原生 Enum） -->
## 7.2 Preferred State Definition (`EnumUInt` Instead of the Native `Enum`)

<!-- 规避原生 Enum 解构语法报错，支持参数校验、位宽校验、独热码校验，适配硬件开发。 -->
This avoids syntax errors from native `Enum` destructuring and supports parameter checks, width checks, and one-hot checks suitable for hardware development.

```scala
private object FsmState extends EnumUInt(2) {
  def Idle: UInt = 0.U(width.W)
  def Test: UInt = 1.U(width.W)
}
private val state = RegInit(FsmState.Idle)
private val stateNext = WireInit(state)
```

<!-- ## 7.3 禁止行为 -->
## 7.3 Prohibited Practices

<!-- * 输出逻辑中修改状态寄存器 -->
* Modify the state register in output logic.
<!-- * 省略 otherwise 导致信号悬空 -->
* Omit `otherwise`, leaving signals floating.
<!-- * 使用 DontCare 填充默认值、规避连线校验 -->
* Use `DontCare` as a default value to bypass connection checks.
<!-- * 状态跳转、状态更新、输出逻辑混写 -->
* Mix state transitions, state updates, and output logic.

<!-- # 模块连线规范（整洁无错、杜绝乱连线） -->
# Module-Wiring Standards (Clean, Correct, and Free of Spurious Connections)

<!-- ## 8.1 核心原则 -->
## 8.1 Core Principle

<!-- **一个子模块的所有连线必须集中写在一起**，禁止东一条、西一条穿插连线。 -->
**All connections for one child module must be written together in one place**; interleaving connections throughout the file is prohibited.

<!-- ## 8.2 两种标准连线方式 -->
## 8.2 Two Standard Wiring Styles

<!-- ### 8.2.1 简单模块：直接连线 -->
### 8.2.1 Simple Modules: Direct Wiring

<!-- 适用于无中间逻辑处理的简单子模块 -->
Use this style for simple child modules without intermediate logic.

```scala
// <!-- 模块A 集中连线 -->
// Group all Module A connections here.
modA.io.in <> io.in
modA.io.fromB := modB.io.toA

// <!-- 模块B 集中连线 -->
// Group all Module B connections here.
modB.io.flush := io.flush
modB.io.fromA := modA.io.toB
```

<!-- ### 8.2.2 复杂模块：先定义中间 Wire（强制） -->
### 8.2.2 Complex Modules: Define Intermediate `Wire`s First (Mandatory)

<!-- 模块互相嵌套、需要信号运算/选择时，必须提前新建 Wire，**禁止使用别名替代硬件线** -->
When modules are nested or signals require computation or selection, create new `Wire`s in advance. **Do not use aliases in place of hardware wires.**

<!-- ❌ 错误（别名：无新硬件，仅引用原信号，易引发逻辑错乱） -->
❌ Incorrect (alias: no new hardware is created; it only refers to the original signal and can easily confuse the logic)

```scala
val AToB = modA.io.toB
```

<!-- ✅ 正确（新建 Wire：生成独立硬件线，逻辑隔离） -->
✅ Correct (new `Wire`: creates an independent hardware wire and isolates the logic)

```scala
val AToB = Wire(modA.io.toB.cloneType)
val BToA = Wire(modB.io.toA.cloneType)
// <!-- 统一连线 -->
// Centralize the connections.
modA.io.fromB := BToA
AToB := modA.io.toB
```

<!-- # 变量修饰符、类型标注与静态常量规范（private / public / final） -->
# Standards for Variable Modifiers, Type Annotations, and Static Constants (`private` / `public` / `final`)

<!-- 本章统一规范 Chisel/Scala 中**变量类型标注、修饰符使用、静态常量定义**的强制标准，包含 private、public、final（static 替代）、多修饰符组合顺序、公开成员类型约束等核心规则，解决工程权限滥用、封装混乱、常量不规范、隐式推导报错等高频问题，为香山强制校验规则。 -->
This chapter defines mandatory standards for **variable type annotations, modifier use, and static-constant definitions** in Chisel/Scala. It covers `private`, `public`, and `final` (`final` as the replacement for `static`), modifier ordering, and type constraints on public members. These rules address frequent problems such as overbroad access, poor encapsulation, inconsistent constants, and errors from implicit inference, and form part of XiangShan's mandatory checks.

<!-- 核心原则：**默认私有、按需公开、公开必标注、常量必终态、严格封装、杜绝全局滥用**。 -->
Core principles: **private by default, public only when needed, explicit types for public members, immutable constants, strict encapsulation, and no abuse of global scope**.

<!-- ## 基础认知：Scala 与 Java 修饰符差异 -->
## Basics: Modifier Differences Between Scala and Java

<!-- * Scala **默认权限为 public**，不写修饰符即公开，无显性 public 关键字声明。 -->
* Scala's **default visibility is public**: omitting a modifier makes a member public; there is no explicit `public` keyword.
<!-- * Scala 无 Java 原生 `static`，统一使用 **final + object 全局常量** 实现静态效果。 -->
* Scala has no native Java-style `static`; use **`final` + an `object` global constant** to achieve static behavior.
<!-- * Chisel 硬件开发必须严格手动控权限，禁止依赖默认 public 开放所有成员。 -->
* Chisel hardware development must control visibility explicitly; do not rely on the default public visibility for every member.

<!-- ## 修饰符组合顺序（固定强制，不可乱序） -->
## Modifier Ordering (Fixed and Mandatory)

<!-- 多修饰符叠加时，严格遵循固定顺序，与官方规范、Scalastyle 校验对齐，禁止随意调换顺序： -->
When multiple modifiers are combined, follow this fixed order to match the official standard and Scalastyle checks; arbitrary reordering is prohibited:

<!-- **override → private/protected → implicit → final → def/val** -->
**`override` → `private`/`protected` → `implicit` → `final` → `def`/`val`**

<!-- ✅ 正确示例：`private final val MaxBufDepth: Int = 128` -->
✅ Correct example: `private final val MaxBufDepth: Int = 128`

<!-- ❌ 错误示例：`final private val MaxBufDepth: Int = 128` -->
❌ Incorrect example: `final private val MaxBufDepth: Int = 128`

<!-- ## 私有权限 private（工程强制优先使用） -->
## Private Visibility (`private`, the Engineering Default)

<!-- 所有模块内部资源，**默认全部私有**，仅对外交互端口允许公开，最小化暴露域，规避跨模块误修改、隐性电路变更。 -->
All resources internal to a module are **private by default**. Only external-interaction ports may be public, minimizing the exposed surface and preventing accidental cross-module modifications or hidden circuit changes.

<!-- ### 必须使用 private 的场景 -->
### Situations That Require `private`

<!-- * 模块内部所有 Wire、Reg、中间组合信号、临时变量 -->
* All internal `Wire`s, `Reg`s, intermediate combinational signals, and temporary variables
<!-- * 模块内部实例化的子模块、局部工具对象 -->
* Child modules and local utility objects instantiated inside the module
<!-- * 内部辅助判断条件、局部计算变量、临时计数 -->
* Internal helper conditions, local calculation variables, and temporary counters
<!-- * 仅内部调用的工具方法、逻辑处理函数 -->
* Utility methods and logic-processing functions called only internally

<!-- ### 正确与错误示例 -->
### Correct and Incorrect Examples

<!-- ❌ 错误（默认公开，权限泛滥、存在隐性风险） -->
❌ Incorrect (public by default, with excessive visibility and hidden risks)

```scala
val cnt = RegInit(0.U(4.W))
val validWire = Wire(Bool())
def calcResult(): UInt = { /* ... */ }
```

<!-- ✅ 强制正确（内部资源全部私有，封装合规） -->
✅ Mandatory correct form (all internal resources are private and encapsulated)

```scala
private val cnt = RegInit(0.U(4.W))
private val validWire = Wire(Bool())
private def calcResult(): UInt = { /* ... */ }
```

<!-- ## 公开权限 public（严格限制，禁止滥用） -->
## Public Visibility (Strictly Limited; No Abuse)

<!-- Scala 无显性 public 关键字，**不写修饰符即为 public**，该权限仅允许用于对外交互资源，其余场景一律禁止。 -->
Scala has no explicit `public` keyword; **omitting a modifier makes a member public**. Public visibility is allowed only for external-interaction resources and is prohibited elsewhere.

<!-- ### 仅允许公开的场景 -->
### Situations Allowed to Be Public

<!-- * 模块顶层 `io` 端口（唯一默认公开成员） -->
* A module's top-level `io` port (the only member public by default)
<!-- * 跨模块必须调用的全局工具方法、通用工具类 -->
* Global utility methods and common utility classes that must be called across modules
<!-- * 全局统一枚举、参数样例类、公共配置常量 -->
* Globally shared enumerations, parameter case classes, and public configuration constants

<!-- ### 严格禁止的公开行为 -->
### Strictly Prohibited Public Behavior

<!-- * 禁止将内部寄存器、中间线网、临时计算变量设为公开 -->
* Do not make internal registers, intermediate wires, or temporary calculation variables public.
<!-- * 禁止将模块内部辅助方法对外开放 -->
* Do not expose module-internal helper methods.
<!-- * 禁止为了方便连线，随意将内部信号改成公开权限 -->
* Do not make internal signals public merely to simplify wiring.

<!-- 核心原因：公开信号会暴露内部硬件逻辑，极易被外部误赋值、误引用，产生隐性电路 Bug，同时破坏模块封装性。 -->
Core reason: Public signals expose internal hardware logic, making accidental external assignments or references likely. This creates hidden circuit bugs and breaks module encapsulation.

<!-- ## 类型标注强制规范（公私区分） -->
## Mandatory Type-Annotation Rules (Public vs. Private)

<!-- 统一 Chisel/Scala 类型推导规则，规避隐式推导导致的类型错乱、编译告警、硬件位宽异常问题。 -->
Standardize Chisel/Scala type-inference rules to avoid type confusion, compiler warnings, and unexpected hardware widths caused by implicit inference.

<!-- * **公开成员必须显式标注类型**：所有 public 的 val、def、IO 端口、全局常量、公共方法，禁止依赖隐式类型推导，保证类型透明、可校验、可复用。 -->
* **Public members must have explicit type annotations**: every public `val`, `def`, IO port, global constant, and public method must state its type rather than relying on implicit inference, ensuring transparency, checkability, and reuse.
<!-- * **私有成员可省略标注**：模块内部 private 的变量、方法、临时信号，可省略类型标注，由编译器自动推导，简化冗余代码。 -->
* **Private members may omit annotations**: internal private variables, methods, and temporary signals may rely on compiler inference to reduce redundant code.
<!-- * **常量强制精准标注**：所有 final 全局常量，必须手动标注基础类型与硬件位宽，禁止模糊推导。 -->
* **Constants require precise annotations**: every global `final` constant must explicitly state its base type and hardware width; vague inference is prohibited.

<!-- ## final 静态常量规范 -->
## `final` Static-Constant Standards

<!-- Scala/Chisel 无 static 关键字，**全局常量、硬件固定参数、编码常量统一使用 final 修饰**，放置在独立 object 中，实现静态全局调用效果，完全替代 Java static 能力。 -->
Scala/Chisel has no `static` keyword. **Global constants, fixed hardware parameters, and encoding constants must all use the `final` modifier**, be placed in a dedicated `object`, and thereby provide static global access in place of Java's `static`.

<!-- ### final 使用强制规则 -->
### Mandatory Rules for Using `final`

<!-- * 所有硬件固定常量、位宽定义、编码值、默认参数，必须加 **final** -->
* All fixed hardware constants, width definitions, encoding values, and default parameters must use **`final`**.
<!-- * 全局常量统一放入 `object XXXConst` 单例对象，禁止散落在模块内部 -->
* Put all global constants in a singleton `object XXXConst`; do not scatter them inside modules.
<!-- * 常量命名严格使用 **大驼峰**，符合香山统一命名规范 -->
* Name constants strictly with **UpperCamelCase**, following XiangShan's unified naming standard.
<!-- * 禁止使用 var 定义常量，所有静态常量必须为 val + final -->
* Do not use `var` for constants; every static constant must be `val` + `final`.

<!-- ### 标准静态常量模板 -->
### Standard Static-Constant Template

```scala
final val PageOffsetWidth = 12
```

<!-- ## 工程避坑总结 -->
## Engineering Pitfall-Avoidance Summary

<!-- * **能私有绝不公开**：内部所有线网、寄存器、方法默认 private，杜绝默认public隐患 -->
* **Prefer private over public**: make all internal wires, registers, and methods private by default to eliminate risks from default public visibility.
<!-- * **公开必标类型**：所有对外暴露的成员禁止隐式推导，统一显式标注类型 -->
* **Public means typed**: every exposed member must have an explicit type; implicit inference is prohibited.
<!-- * **常量必final、必归object**：杜绝模块内散落魔数、零散常量 -->
* **Constants must be `final` and belong to an `object`**: eliminate magic numbers and scattered constants inside modules.
<!-- * **禁止滥用公开权限**：不允许为了省事开放内部信号，破坏封装层级 -->
* **Do not abuse public visibility**: never expose internal signals for convenience and thereby break encapsulation.
<!-- * **修饰符顺序严格对齐**：规避格式校验告警、统一代码审美 -->
* **Keep modifier ordering exact**: avoid formatting warnings and maintain a consistent code style.

<!-- # EnumUInt 常量规范 -->
# `EnumUInt` Constant Standards

<!-- 香山新增 EnumUInt 工具类，自带参数校验、位宽校验、独热码校验，规避常量定义错误，优先替代 NamedUInt、原生 ChiselEnum。 -->
XiangShan has added the `EnumUInt` utility class, which provides parameter, width, and one-hot checks. It avoids constant-definition errors and is preferred over `NamedUInt` and the native `ChiselEnum`.

<!-- ## 10.1 核心校验规则（高频错误避坑） -->
## 10.1 Core Validation Rules (Avoiding Frequent Errors)

<!-- * 常量数量与实际定义个数必须匹配，允许重复值需开启 `allowDuplicate = true` -->
* The number of constants must match the number actually defined; enable `allowDuplicate = true` when duplicate values are allowed.
<!-- * 常量方法必须大驼峰，小写方法不会被识别为常量 -->
* Constant methods must use UpperCamelCase; lowercase methods are not recognized as constants.
<!-- * 独热码模式需开启 `useOneHot = true`，严格校验独热编码 -->
* Enable `useOneHot = true` for one-hot mode to validate one-hot encodings strictly.
<!-- * 所有常量必须显式指定位宽 `width.W`，禁止默认1位宽 -->
* Every constant must explicitly specify a width, `width.W`; the default one-bit width is prohibited.

<!-- ## 10.2 与 ChiselEnum 取舍 -->
## 10.2 Choosing Between `EnumUInt` and `ChiselEnum`

<!-- * EnumUInt：优势是自带独热码、固定位宽校验，适配现有代码 -->
* `EnumUInt`: provides built-in one-hot and fixed-width checks and fits the existing code base.
<!-- * ChiselEnum：优势是类型安全，需手动转换 UInt，适合强类型场景 -->
* `ChiselEnum`: provides type safety but requires manual conversion to `UInt`, making it suitable for strongly typed scenarios.
<!-- * 当前规范：优先使用 EnumUInt，后续会融合两者优势优化 -->
* Current standard: prefer `EnumUInt`; future improvements will combine the strengths of both.

<!-- # 参数化设计规范（分层解耦） -->
# Parameterization Standards (Layered Decoupling)

<!-- 所有模块参数禁止零散定义，统一使用「样例类+特质解包」模式，分层管理参数，支持参数校验、全局复用，减少编译开销。 -->
Module parameters must not be defined in scattered locations. Use the "case class + trait unpacking" pattern to manage parameters in layers, support validation and global reuse, and reduce compilation overhead.

<!-- ## 11.1 标准模板 -->
## 11.1 Standard Template

```scala
// <!-- 1. 定义参数样例类（带默认值、参数校验） -->
// 1. Define the parameter case class (with defaults and parameter checks).
case class AaaParameters(
  p1: Int = 100,
  p2: Boolean = true
) {
  // <!-- p1 参数超出合法范围 -->
  // p1 is outside the valid range.
  // require(p1 < 1000, "p1参数超出合法范围")
  require(p1 < 1000, "p1 is outside the valid range")
}

// <!-- 2. 定义参数解包特质 -->
// 2. Define the parameter-unpacking trait.
trait HasAaaParameters extends HasXSParameters {
  def aaaParams: AaaParameters = coreParams.aaaParams
  def p1: Int = aaaParams.p1
  def p2: Boolean = aaaParams.p2
}

// <!-- 3. 模块继承特质，直接使用参数 -->
// 3. Have the module extend the trait and use the parameters directly.
class Aaa extends Module with HasAaaParameters {
  // <!-- 直接使用 p1、p2，无需重复写 aaaParams -->
  // Use p1 and p2 directly; there is no need to repeat aaaParams.
}
```

<!-- ## 11.2 子模块参数规范 -->
## 11.2 Child-Module Parameter Standards

<!-- 多级模块参数逐级嵌套，子模块参数类挂载到父参数类，解包特质继承父级特质，禁止直接继承顶层参数，保证层级清晰。 -->
Parameters for multilevel modules must be nested level by level. Attach a child module's parameter class to its parent parameter class, and have the unpacking trait inherit the parent trait. Direct inheritance from top-level parameters is prohibited to keep the hierarchy clear.

<!-- # 寄存器打拍与门控规范（时序优化） -->
# Register Pipelining and Gating Standards (Timing Optimization)

<!-- 统一寄存器打拍方式，兼顾时序、面积、功耗，杜绝随意使用寄存器导致的时序问题。 -->
Standardize register pipelining to balance timing, area, and power, and prevent timing problems caused by arbitrary register use.

<!-- * 控制信号 valid 打拍：强制使用 RegNext，禁止 GatedRegNext -->
* Pipeline the `valid` control signal with `RegNext`; `GatedRegNext` is prohibited.
<!-- * 大位宽 bits 数据打拍：强制使用 RegEnable，禁止门控寄存器 -->
* Pipeline wide `bits` data with `RegEnable`; gated registers are prohibited.
<!-- * 小位宽、低翻转频率数据：可使用 GatedRegNext 优化功耗 -->
* For narrow, low-toggle-rate data, `GatedRegNext` may be used to reduce power.

<!-- # 代码语法与排版细则 -->
# Detailed Code Syntax and Layout Rules

<!-- ## 13.1 空格规范 -->
## 13.1 Spacing Rules

<!-- * 函数/关键字与括号之间：when(、if( 无空格 -->
* Between a function/keyword and its opening parenthesis: no space, as in `when(` and `if(`.
<!-- * 括号、大括号前后、运算符两侧：必须加单个空格 -->
* Put exactly one space around parentheses and braces and on both sides of operators.
<!-- * 禁止多空格、行尾空格、空行残留空格 -->
* Multiple spaces, trailing spaces, and spaces on blank lines are prohibited.

<!-- ## 13.2 换行与空行规范 -->
## 13.2 Line-Break and Blank-Line Rules

<!-- * 单行代码≤120字符，超长手动拆分，复杂逻辑分段换行 -->
* Keep each line at no more than 120 characters; split long lines manually and break complex logic into sections.
<!-- * 不同功能代码块之间加空行分隔，禁止连续2行及以上空行 -->
* Separate code blocks for different functions with blank lines; two or more consecutive blank lines are prohibited.
<!-- * 文件首行无空行，文件末尾必须留一个空行 -->
* Do not begin a file with a blank line; end every file with one blank line.

<!-- ## 13.3 注释规范 -->
## 13.3 Comment Rules

<!-- * 禁止中文注释、中文字符，所有注释使用英文 -->
* Chinese comments and Chinese characters are prohibited; all comments must be in English.
<!-- * 单行注释 `//` 与内容之间留1个空格，代码行尾注释与代码之间留1个空格 -->
* Leave one space between `//` and the comment text, and one space between trailing comments and the code before them.
<!-- * 代码块必须加功能注释，连续10行无注释视为不规范 -->
* Add a functional comment to every code block; ten consecutive uncommented lines are considered non-compliant.
<!-- * 使用层级注释（大标题、小标题）梳理代码结构 -->
* Use hierarchical comments (major and minor headings) to organize the code structure.

<!-- ## 13.4 杜绝冗余写法 -->
## 13.4 Eliminate Redundant Forms

<!-- * 禁止多余括号、无意义分号 -->
* Unnecessary parentheses and meaningless semicolons are prohibited.
<!-- * 禁止使用魔数，所有常量必须命名定义 -->
* Magic numbers are prohibited; every constant must have a named definition.
<!-- * 重复逻辑抽象为通用函数，禁止复制粘贴 -->
* Abstract repeated logic into generic functions; copy-and-paste is prohibited.

<!-- # 括号、大括号、空格专项统一规范 -->
# Unified Rules for Parentheses, Braces, and Spaces

<!-- 本节整合全文所有 `()`、`{}`、空格 相关强制规范，统一书写标准、解决格式报错、对齐错乱、CI 校验失败等高频问题，为代码格式化核心必守规则，所有场景无特殊豁免，严格执行。 -->
This section consolidates all mandatory rules in this document for `()`, `{}`, and spaces. It standardizes notation and addresses frequent formatting errors, misalignment, and CI failures. These are core formatting rules with no special exemptions and must be followed strictly in every situation.

<!-- ## 14.1 小括号 () 规范 -->
## 14.1 Parentheses `()`

<!-- * **关键字/函数紧贴括号**：`when(`、`if(`、`def 方法名(`、`UInt(8.W)` 等场景，括号前**禁止加空格**，杜绝多余空格报错。 -->
* **Keep keywords/functions adjacent to the opening parenthesis**: in forms such as `when(`, `if(`, `def methodName(`, and `UInt(8.W)`, **do not add a space before the parenthesis**, avoiding errors from extra spaces.
<!-- * **括号内侧无空格**：圆括号内部首尾禁止多余空格，示例：`UInt(4.W)` 正确，`UInt( 4.W )` 错误。 -->
* **No inner spaces**: do not put extra spaces at the beginning or end inside parentheses. `UInt(4.W)` is correct; `UInt( 4.W )` is incorrect.
<!-- * **括号外侧必加单空格**：独立括号表达式、参数括号结束后，外侧需保留单个标准空格，适配 Scalafmt 自动对齐规则。 -->
* **One outer space where required**: leave one standard space outside a standalone parenthesized expression and after a closing parameter parenthesis, matching Scalafmt alignment rules.
<!-- * **禁止冗余括号**：逻辑运算、赋值、条件判断中，无需嵌套的多余括号必须删除，保持代码简洁无冗余。 -->
* **No redundant parentheses**: remove unnecessary nesting in logical operations, assignments, and conditions to keep code concise.

<!-- ## 14.2 大括号 {} 规范 -->
## 14.2 Braces `{}`

<!-- * **大括号前留单空格**：`when() {`、`elsewhen() {`、`Bundle {`、`类/方法实现 {` 等所有场景，左大括号前必须保留一个标准空格。 -->
* **Leave one space before a brace**: in all forms such as `when() {`, `elsewhen() {`, `Bundle {`, and `class/method body {`, keep one standard space before the opening brace.
<!-- * **禁止首行嵌套无换行**：大括号开启代码块后，内部逻辑必须另起新行，禁止行内嵌套逻辑，保证层级清晰。 -->
* **Do not nest on the opening line without a break**: after a brace opens a block, put the internal logic on a new line; inline nesting is prohibited to keep the hierarchy clear.
<!-- * **代码块闭合规范**：右大括号独立成行（极简短逻辑除外，以 Scalafmt 自动格式化结果为准），对齐对应代码块起始位置。 -->
* **Block-closing rule**: put the closing brace on its own line (except for extremely short logic, following Scalafmt's result) and align it with the start of the corresponding block.
<!-- * **功能块空行分隔**：不同大括号包裹的独立逻辑块、函数、代码段之间，必须加空行分隔，规避格式化错乱、对齐异常问题。 -->
* **Separate functional blocks with blank lines**: put blank lines between independent logic blocks, functions, and code sections enclosed by different braces to avoid formatting and alignment problems.

<!-- ## 14.3 全局空格统一规范 -->
## 14.3 Global Spacing Rules

<!-- * **运算符两侧必加单空格**：赋值 `:=`、加减乘除、逻辑与或、比较运算符等，两侧统一保留单个空格，禁止无空格、多空格混搭。 -->
* **Use one space on both sides of operators**: assignment `:=`, arithmetic, logical, and comparison operators must all have exactly one space on each side; no-space and multi-space mixtures are prohibited.
<!-- * **注释固定空格规则**：单行注释 `//` 后必须留1个空格；代码行尾注释与前置代码之间，必须留1个空格。 -->
* **Fixed comment spacing**: leave one space after `//` and one space between trailing comments and the preceding code.
<!-- * **禁止各类多余空格**：杜绝行尾空格、空行残留空格、代码段首尾多余空格、连续多空格对齐（仅允许 Scalafmt 自动对齐空格）。 -->
* **No extra spaces of any kind**: eliminate trailing spaces, spaces on blank lines, extra spaces around code sections, and runs of spaces for alignment (only Scalafmt-generated alignment spaces are allowed).
<!-- * **特殊语法空格豁免**：仅 `UInt*` 可变参数符号，无需遵循两侧加空格规则，以 Scalafmt 格式化结果为准，忽略 Scalastyle 误告警。 -->
* **Special syntax exemption**: only the `UInt*` varargs symbol is exempt from the spaces-on-both-sides rule. Follow Scalafmt and ignore Scalastyle's false warning.
<!-- * **缩写符号无特殊空格**：ICache、Ifu、Ras 等标准缩写视为完整单词，空格规则与普通单词完全一致，无需特殊适配。 -->
* **No special spacing for abbreviations**: standard abbreviations such as `ICache`, `Ifu`, and `Ras` are treated as complete words and follow ordinary spacing rules.

<!-- # 高效开发工具：DataView 自动连线 -->
# Efficient Development Tool: Automatic Wiring with `DataView`

<!-- 摒弃手动逐字段连线，使用 Chisel DataView 工具实现 Bundle 自动映射，大幅减少冗余代码、连线错误。 -->
Stop wiring every field manually. Use Chisel's `DataView` tool to map `Bundle`s automatically, greatly reducing redundant code and wiring errors.

<!-- 核心用法：导入工具包后，通过 `viewAsSupertype` 自动完成父子 Bundle 字段映射，无需逐行赋值。 -->
Core usage: after importing the utility package, call `viewAsSupertype` to map parent and child `Bundle` fields automatically; no line-by-line assignments are needed.

<!-- # Git 提交与 PR 规范 -->
# Git Commit and PR Standards

<!-- ## 16.1 分支命名前缀规范 -->
## 16.1 Branch-Name Prefixes

<!-- * fix：Bug 修复 -->
* `fix`: bug fix
<!-- * feat：新增功能 -->
* `feat`: new feature
<!-- * refactor：代码重构（不改功能） -->
* `refactor`: code refactoring (no functional change)
<!-- * style：格式优化 -->
* `style`: formatting improvements
<!-- * perf：性能优化 -->
* `perf`: performance optimization
<!-- * timing/area/power：时序、面积、功耗优化 -->
* `timing`/`area`/`power`: timing, area, and power optimization
<!-- * docs/chore/ci：文档、工程配置、CI 流程修改 -->
* `docs`/`chore`/`ci`: documentation, project-configuration, and CI-process changes

<!-- ## 16.2 Commit 规范 -->
## 16.2 Commit Standards

<!-- * 格式：`类型[模块]: 简短描述` + 空行 + 详细说明 -->
* Format: `type[module]: short description` + a blank line + a detailed explanation
<!-- * 标题≤50字符，正文每行≤72字符，首字母大写、语句完整、无句尾标点 -->
* Keep the subject at no more than 50 characters and each body line at no more than 72 characters; capitalize the first letter, use complete sentences, and omit terminal punctuation.
<!-- * 一个 commit 只做一件事，禁止大跨度、多改动合并提交 -->
* One commit should do one thing; broad, multi-change commits are prohibited.

<!-- ## 16.3 PR 合入前置要求 -->
## 16.3 Requirements Before Merging a PR

<!-- * 代码编译通过，无格式、规范告警 -->
* The code compiles successfully with no formatting or standards warnings.
<!-- * 提交前执行 `make reformat`格式化，人工复核格式 -->
* Run `make reformat` before committing and review the resulting format manually.
<!-- * Bug 修复需验证对应场景，性能改动需完成基础测试 -->
* Verify the relevant scenario for every bug fix; complete basic tests for performance changes.
<!-- * 时序/面积影响改动，需提前完成物理后端评估 -->
* Complete a physical back-end evaluation in advance for changes affecting timing or area.
<!-- * 清理废弃注释、冗余代码 -->
* Remove obsolete comments and redundant code.

<!-- # 规范速记 -->
# Standards at a Glance

<!-- * **工具校验**：Scalafmt 强制 CI 校验，提交必格式化；Scalastyle 辅助规范检查 -->
* **Tool checks**: Scalafmt formatting is mandatory in CI and before submission; Scalastyle provides auxiliary standards checks.
<!-- * **赋值集中唯一**：一个信号只在一处赋值，杜绝分散 when 优先级 Bug -->
* **Single assignment site**: assign each signal in one place to eliminate priority bugs from scattered `when`s.
<!-- * **IO 规范严谨**：全部命名 Bundle、分层封装、禁止匿名、模块名全局唯一 -->
* **Strict IO rules**: use named `Bundle`s, hierarchical encapsulation, no anonymous bundles, and globally unique module names.
<!-- * **命名统一合规**：全程驼峰，仅流水级、调试信号可下划线 -->
* **Consistent compliant naming**: use camel case throughout; only pipeline-stage and debug signals may contain underscores.
<!-- * **状态机三段分离**：更新、跳转、输出完全独立，优先 EnumUInt 定义状态 -->
* **Three-part state-machine separation**: keep update, transition, and output logic completely independent; prefer `EnumUInt` for states.
<!-- * **模块连线集中**：单模块连线聚合，复杂场景新建 Wire，禁止别名滥用 -->
* **Centralized module wiring**: group one module's connections; create new `Wire`s in complex cases and do not abuse aliases.
<!-- * **参数分层管理**：样例类定义参数，特质解包，逐级嵌套 -->
* **Layered parameter management**: define parameters in case classes, unpack them in traits, and nest them level by level.
<!-- * **提交规范严格**：单Commit单功能、格式合规、注释干净、编译通过 -->
* **Strict submission rules**: one function per commit, compliant formatting, clean comments, and a successful build.
<!-- * **工具校验**：Scalafmt 强制 CI 校验，提交必格式化；Scalastyle 辅助规范检查 -->
* **Tool checks**: Scalafmt formatting is mandatory in CI and before submission; Scalastyle provides auxiliary standards checks.
<!-- * **代码顺序固定**：参数→IO→别名→子模块→线网→寄存器→逻辑→状态机→连线→计数器 -->
* **Fixed code order**: parameters → IO → aliases → child modules → wires → registers → logic → state machine → wiring → counters.
<!-- * **赋值集中唯一**：一个信号只在一处赋值，杜绝分散 when 优先级 Bug -->
* **Single assignment site**: assign each signal in one place to eliminate priority bugs from scattered `when`s.
<!-- * **IO 规范严谨**：全部命名 Bundle、分层封装、禁止匿名、模块名全局唯一 -->
* **Strict IO rules**: use named `Bundle`s, hierarchical encapsulation, no anonymous bundles, and globally unique module names.
<!-- * **命名统一合规**：全程驼峰，仅流水级、调试信号可下划线 -->
* **Consistent compliant naming**: use camel case throughout; only pipeline-stage and debug signals may contain underscores.
<!-- * **状态机三段分离**：更新、跳转、输出完全独立，优先 EnumUInt 定义状态 -->
* **Three-part state-machine separation**: keep update, transition, and output logic completely independent; prefer `EnumUInt` for states.
<!-- * **模块连线集中**：单模块连线聚合，复杂场景新建 Wire，禁止别名滥用 -->
* **Centralized module wiring**: group one module's connections; create new `Wire`s in complex cases and do not abuse aliases.
<!-- * **参数分层管理**：样例类定义参数，特质解包，逐级嵌套 -->
* **Layered parameter management**: define parameters in case classes, unpack them in traits, and nest them level by level.
<!-- * **提交规范严格**：单Commit单功能、格式合规、注释干净、编译通过 -->
* **Strict submission rules**: one function per commit, compliant formatting, clean comments, and a successful build.

<!-- # 参考素材 -->
# References

<!-- 1. Java: <https://google.github.io/styleguide/javaguide.html> -->
1. Java: <https://google.github.io/styleguide/javaguide.html>
<!-- 2. Scala: <https://docs.scala-lang.org/style/> -->
2. Scala: <https://docs.scala-lang.org/style/>
<!-- 3. Chisel: <https://www.chisel-lang.org/docs/developers/style> -->
3. Chisel: <https://www.chisel-lang.org/docs/developers/style>


<!-- > 更新: 2026-06-23 14:13:59
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/etidwiqvoguqfx5z> -->

> Updated: 2026-06-23 14:13:59
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/etidwiqvoguqfx5z>
