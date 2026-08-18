<!-- # 第八章 Chisel 底层原理 -->
# Chapter 8: Chisel's Underlying Principles

<!-- **文档定位**：从零通俗讲解 Chisel 完整编译链路，包含前端解析、Annotation 注解机制、Elaborate 电路展开、Convert 中间码转换、FIRRTL 编译、Verilog 生成全流程。 -->
**Document scope**: A from-scratch, plain-language explanation of Chisel's complete compilation pipeline, covering front-end parsing, the Annotation mechanism, circuit elaboration, intermediate representation conversion, FIRRTL compilation, and end-to-end Verilog generation.

<!-- **核心总链路（全文贯穿核心）** -->
**Core pipeline (the central thread throughout this chapter)**

<!-- 用户 Chisel 代码 → **Chisel 前端解析**（语法检查+电路提取+注解收集） →**Elaborate 电路完整展开** → **Convert 转为 FIRRTL 中间表示** → FIRRTL 编译器优化 → 后端生成标准 Verilog 硬件代码 -->
User Chisel code → **Chisel front-end parsing** (syntax checking + circuit extraction + annotation collection) → **complete circuit elaboration** → **conversion to the FIRRTL intermediate representation** → FIRRTL compiler optimization → back-end generation of standard Verilog hardware code

<!-- # Chisel 编译整体架构总览 -->
# Overview of the Chisel Compilation Architecture

<!-- ## 1.1 为什么 Chisel 需要整套编译流水线？ -->
## 1.1 Why Does Chisel Need a Complete Compilation Pipeline?

<!-- Chisel 是**Scala 高阶硬件描述语言**，不是原生硬件语言：用户写的是面向对象、高阶函数、批量生成的 Scala 代码，无法直接综合成硬件。必须通过一整套固定编译流水线，完成「高级代码 → 标准硬件电路」的翻译、展开、转换、优化、生成。 -->
Chisel is a **Scala-based high-level hardware construction language**, not a native hardware language. Users write object-oriented Scala code with higher-order functions and bulk generation, which cannot be synthesized directly into hardware. A fixed compilation pipeline is therefore required to translate, elaborate, convert, optimize, and generate **high-level code → standard hardware circuitry**.

<!-- 区别于 Verilog：Verilog 写一行就是一行电路，Chisel 写的是**生成电路的代码**，必须编译展开才能得到真实硬件结构。 -->
Unlike Verilog, where each line describes a circuit construct, Chisel describes **code that generates a circuit**; it must be compiled and elaborated before the actual hardware structure exists.

<!-- ## 1.2 完整三级编译分层 -->
## 1.2 The Three Complete Compilation Layers

<!-- * **前端层（Frontend）**：代码解析、语义校验、电路抽取、Annotation 注解收集绑定 -->
* **Front end**: code parsing, semantic checking, circuit extraction, and collection and binding of Annotations
<!-- * **中层展开转换（Elaborate + Convert）**：实例化所有模块、展开完整电路、转 FIRRTL 标准中间码 -->
* **Middle-layer elaboration and conversion (Elaborate + Convert)**: instantiate all modules, elaborate the complete circuit, and convert it to the standard FIRRTL intermediate representation
<!-- * **后端编译生成（FIRRTL Compiler）**：电路优化、化简、位宽对齐、最终输出 Verilog -->
* **Back-end compilation and generation (FIRRTL Compiler)**: optimize and simplify the circuit, align widths, and finally emit Verilog

<!-- # Chisel 前端原理 -->
# Chisel Front-End Principles

<!-- ## 2.1 前端核心定位 -->
## 2.1 The Front End's Core Role

<!-- Chisel 前端 = **翻译员 + 检查员 + 便利贴管理员**。 -->
Chisel's front end = **translator + inspector + sticky-note manager**.

<!-- 它不生成最终电路，只做三件基础核心事： -->
It does not generate the final circuit. It performs three fundamental tasks:

<!-- 1. 读懂你写的 Chisel 代码，检查语法、连线、类型、位宽错误 -->
1. Read the Chisel code, checking syntax, connections, types, and widths.
<!-- 2. 剥离 Scala 程序逻辑，精准提取纯硬件电路结构 -->
2. Strip away Scala program logic and precisely extract the pure hardware structure.
<!-- 3. 收集所有编译约束（注解），贴在对应电路上，跟随流水线传递 -->
3. Collect all compilation constraints (Annotations), attach them to the corresponding circuit targets, and carry them through the pipeline.

<!-- ## 2.2 三大核心概念通俗精讲 -->
## 2.2 Plain-Language Explanation of the Three Core Concepts

<!-- ### 2.2.1 Annotation -->
### 2.2.1 Annotation

<!-- **定义**：不属于电路逻辑本身，是给编译器看的「特殊约束指令」。 -->
**Definition**: An Annotation is not part of the circuit logic itself; it is a "special constraint instruction" for the compiler.

<!-- **通俗比喻**：贴在模块、信号、端口上的便利贴，告诉编译器：这根线不能删、这个模块是黑盒、这个名字要保留、禁止优化等。 -->
**Plain-language analogy**: An Annotation is a sticky note attached to a module, signal, or port, telling the compiler that a wire must not be removed, a module is a black box, a name must be preserved, an optimization is forbidden, and so on.

<!-- **常见注解作用**：禁止优化、保留命名、黑盒标记、顶层引脚、调试打印、时序约束。 -->
**Common uses of Annotations**: disable optimization, preserve names, mark black boxes, identify top-level pins, enable debug printing, and express timing constraints.

<!-- ### 2.2.2 AnnotationSeq（注解序列） -->
### 2.2.2 `AnnotationSeq` (Annotation Sequence)

<!-- **定义**：所有 Annotation 有序组成的集合，是一整本便利贴记录本。 -->
**Definition**: An ordered collection of all Annotations, like a complete notebook of sticky-note records.

<!-- **流水线特性**：编译每一个阶段都会 **消耗、新增、保留、更新** 注解，全程动态变化，贯穿全流程。 -->
**Pipeline behavior**: Every compilation phase may **consume, add, preserve, or update** Annotations. The sequence changes dynamically and persists throughout the entire flow.

<!-- ### 2.2.3 Target（电路唯一门牌号） -->
### 2.2.3 `Target` (The Circuit's Unique Address)

<!-- **定义**：每一个模块、Wire、Reg、端口、阵列的唯一标识。 -->
**Definition**: The unique identifier of each module, `Wire`, `Reg`, port, or array.

<!-- **作用**：让注解精准绑定电路，不会贴错位置，电路改名、重构后依然能精准匹配。 -->
**Purpose**: It binds an Annotation precisely to a circuit element, preventing misplaced notes and allowing exact matching even after the circuit is renamed or refactored.

<!-- ## 2.3 官方 Annotation 源码 -->
## 2.3 Official Annotation Source Code

```scala
/** <!-- 辅助信息的基础父类：所有注解都继承该特质 --> */
/** Base trait for auxiliary information: every Annotation extends this trait. */
trait Annotation extends Product{
  // <!-- Product 是 Scala 基础特质，自动支持成员遍历、等值判断、解构 -->
  // Product is a fundamental Scala trait that automatically supports member traversal, equality checks, and destructuring.

  /**
   * <!-- 根据信号重命名映射，更新当前注解的绑定目标
   * 电路优化、改名、重构时，自动修正便利贴指向 -->
   * Update this Annotation's bound targets according to a signal-renaming map.
   * During circuit optimization, renaming, or refactoring, automatically fix the sticky note's target.
   */
  def update(renames: RenameMap): Seq[Annotation]

  /** <!-- 可选序列化：将注解转为字符串，用于打印、保存、调试 --> */
  /** Optional serialization: convert the Annotation to a string for printing, storage, and debugging. */
  def serialize: String = this.toString

  /**
   * <!-- 私有递归方法：遍历所有成员，提取内部所有 Target 门牌号
   * 适配复杂嵌套注解 -->
   * Private recursive method: traverse all members and extract every nested Target address.
   * Supports complex nested Annotations.
   */
  private def extractComponents(Is: Traversable[_]): Traversable[Target] = (...)

  /** <!-- 对外公开方法：返回当前注解绑定的所有电路目标 --> */
  /** Public method: return all circuit targets bound by this Annotation. */
  def getTargets: Seq[Target] = extractComponents(productIterator.toIterable).toSeq

  /** <!-- 框架内部专用：注解去重，剔除重复冗余注解 --> */
  /** Framework-internal helper: deduplicate Annotations and remove redundant copies. */
  private[firrtl] def dedup: Option[(Any, Annotation, Reference Target)] = None
}
```

<!-- ## 2.4 所有方法逐句通俗解析 -->
## 2.4 Plain-Language, Line-by-Line Explanation of Every Method

<!-- * **继承 Product**：无需手动写遍历、对比逻辑，自动支持注解批量处理、去重、查找。 -->
* **Extending `Product`**: No need to write traversal or comparison logic manually; it enables bulk processing, deduplication, and lookup of Annotations.
<!-- * **update()**：流水线优化会改信号名，这个方法自动更新便利贴的绑定对象，防止注解失效、贴错线。 -->
* **`update()`**: Pipeline optimizations may rename signals; this method updates the sticky note's bound target so the Annotation does not become invalid or attach to the wrong wire.
<!-- * **serialize()**：把注解变成可读字符串，日志、报错、导出文件都靠它。 -->
* **`serialize()`**: Converts an Annotation into a readable string used by logs, error messages, and exported files.
<!-- * **extractComponents()**：递归扒干净注解内部所有绑定的电路位置，支持多层嵌套。 -->
* **`extractComponents()`**: Recursively extracts every bound circuit location inside an Annotation, including multiple nesting levels.
<!-- * **getTargets()**：对外统一接口，快速查询这条注解管控哪些硬件。 -->
* **`getTargets()`**: The unified public interface for quickly querying which hardware elements an Annotation controls.
<!-- * **dedup()**：自动去重，避免重复注解冲突、降低编译开销。 -->
* **`dedup()`**: Automatically removes duplicates, preventing conflicting Annotations and reducing compilation overhead.

<!-- ## 2.5 前端完整工作流程 -->
## 2.5 Complete Front-End Workflow

<!-- 1. **代码加载**：读取全部 Chisel 模块代码，初始化编译环境 -->
1. **Load code**: Read all Chisel module code and initialize the compilation environment.
<!-- 2. **语法扫描**：逐层遍历模块、端口、信号、逻辑语句 -->
2. **Scan syntax**: Traverse modules, ports, signals, and logic statements layer by layer.
<!-- 3. **语义校验**：拦截位宽不匹配、端口悬空、模块实例错误、类型错误 -->
3. **Check semantics**: Catch width mismatches, floating ports, incorrect module instantiation, and type errors.
<!-- 4. **注解收集**：捕获所有自定义/内置注解，绑定对应 Target -->
4. **Collect Annotations**: Capture all custom and built-in Annotations and bind them to the corresponding Targets.
<!-- 5. **纯电路提取**：剥离 Scala 运行逻辑，只保留硬件结构 -->
5. **Extract the pure circuit**: Strip away Scala runtime logic and retain only the hardware structure.
<!-- 6. **输出初始数据**：输出原始电路结构 + 完整 AnnotationSeq，交给中层流水线 -->
6. **Emit initial data**: Output the raw circuit structure and complete `AnnotationSeq` to the middle-layer pipeline.

<!-- # Elaborate 电路展开 & Convert 中间码转换 -->
# Circuit Elaboration with `Elaborate` & Intermediate Representation Conversion with `Convert`

<!-- 前端只做「解析和收集」，**真正把代码变成完整硬件电路**，靠 Elaborate + Convert 完成。 -->
The front end only performs "parsing and collection". **Elaborate + Convert are what actually turn the code into a complete hardware circuit.**

<!-- ## 3.1 核心概念通俗释义 -->
## 3.1 Plain-Language Definitions of the Core Concepts

<!-- ### 3.1.1 Elaborate（电路展开） -->
### 3.1.1 `Elaborate` (Circuit Elaboration)

<!-- **一句话定义**：把「高阶、参数化、批量生成的 Chisel 抽象代码」**彻底展开成无歧义、完整、扁平的真实硬件电路**。 -->
**One-sentence definition**: **Fully expand high-level, parameterized, bulk-generated Chisel code into an unambiguous, complete, flat hardware circuit.**

<!-- **具体做的事**： -->
**What it does**:

<!-- * 实例化所有子模块，展开嵌套模块层级 -->
* Instantiate every child module and expand nested module hierarchies.
<!-- * 展开所有 for-yield、map 批量生成的阵列电路 -->
* Expand all array circuits generated in bulk by `for-yield` and `map`.
<!-- * 解析所有参数、泛型，固定硬件位宽、深度、数量 -->
* Resolve all parameters and generics, fixing hardware widths, depths, and quantities.
<!-- * 展开 when/else 分支、多路选择逻辑，生成真实组合/时序电路 -->
* Expand `when`/`else` branches and multiplexer logic to generate real combinational and sequential circuits.
<!-- * 补齐所有隐式连线、默认赋值，消除悬空、锁存风险 -->
* Complete implicit connections and default assignments, eliminating floating signals and latch risks.

<!-- ### 3.1.2 Convert（格式转换） -->
### 3.1.2 `Convert` (Format Conversion)

<!-- **一句话定义**：把 Elaborate 展开后的 Chisel 内部电路结构，**统一翻译成 FIRRTL 标准中间表示**。 -->
**One-sentence definition**: **Translate the internal Chisel circuit structure produced by `Elaborate` into the standard FIRRTL intermediate representation.**

<!-- **作用**：屏蔽 Chisel 高阶语法差异，给后端编译器提供唯一、标准、可优化的电路输入。 -->
**Purpose**: Hide Chisel's high-level syntax differences and provide the back-end compiler with one standard, optimizable circuit input.

<!-- ## 3.2 官方完整流水线源码 -->
## 3.2 Official Complete Pipeline Source Code

<!-- 以下为 Chisel 官方 `emitVerilog` 生成 Verilog 的顶层流水线代码，包含**全7个编译阶段**，是 Chisel 编译的真实执行顺序： -->
The following is the top-level pipeline used by Chisel's official `emitVerilog` to generate Verilog. It contains **all seven compilation phases** and shows the actual execution order of Chisel compilation:

```scala
def emitVerilog(gen: => RawModule): String = {
val phase = new PhaseManager(
Seq(
Dependency[chisel3.stage.phases.Checks],
Dependency[chisel3.stage.phases.Elaborate],
Dependency[chisel3.stage.phases.AddImplicitOutputFile],
Dependency[chisel3.stage.phases.AddImplicitOutputAnnotationFile],
Dependency[chisel3.stage.phases.MaybeAspectPhase],
Dependency[chisel3.stage.phases.Convert],
Dependency[firrtl.stage.phases.Compiler]
)
)
phase
.transform(Seq(ChiselGeneratorAnnotation(() => gen), RunFirrtlTransformAnnotation(new VerilogEmitter)))
.collectFirst {
}
```

<!-- ## 3.3 七大编译阶段逐阶段精讲（全流程核心） -->
## 3.3 Detailed Explanation of the Seven Compilation Phases (The Core of the Full Flow)

<!-- ### 阶段1：Checks（语法与电路检查） -->
### Phase 1: `Checks` (Syntax and Circuit Checks)

<!-- 承接前端结果，做最终合规校验：检查端口初始化、位宽匹配、连线合法性、模块实例规范，提前拦截所有编译期错误。 -->
Take the front-end result and perform the final compliance checks: verify port initialization, width matching, legal connections, and module-instantiation rules to catch all compile-time errors early.

<!-- ### 阶段2：Elaborate（核心电路展开） -->
### Phase 2: `Elaborate` (Core Circuit Elaboration)

<!-- 整条流水线**最重要阶段**。将参数化、批量生成、面向对象的 Chisel 代码，实例化为完整、确定、可综合的硬件电路结构。 -->
The **most important phase** in the pipeline. It instantiates parameterized, bulk-generated, object-oriented Chisel code as a complete, deterministic, synthesizable hardware structure.

<!-- ### 阶段3：AddImplicitOutputFile（自动绑定输出文件） -->
### Phase 3: `AddImplicitOutputFile` (Automatically Bind Output Files)

<!-- 自动配置编译输出路径，绑定后续生成的 .fir 中间文件、.v 硬件文件，无需手动配置路径。 -->
Automatically configure the compilation output path and bind the later-generated `.fir` intermediate file and `.v` hardware file, without manual path configuration.

<!-- ### 阶段4：AddImplicitOutputAnnotationFile（自动导出注解文件） -->
### Phase 4: `AddImplicitOutputAnnotationFile` (Automatically Export the Annotation File)

<!-- 将全程收集、更新、去重的 AnnotationSeq 导出为注解文件，保存所有编译约束与硬件标记。 -->
Export the `AnnotationSeq` collected, updated, and deduplicated throughout the flow to an Annotation file, preserving all compilation constraints and hardware markers.

<!-- ### 阶段5：MaybeAspectPhase（可选扩展插件阶段） -->
### Phase 5: `MaybeAspectPhase` (Optional Extension-Plugin Phase)

<!-- 预留扩展接口，用于自定义编译插件、电路监测、额外约束、工程拓展功能，默认不生效。 -->
Reserve an extension interface for custom compiler plugins, circuit monitoring, extra constraints, and project extensions; it is inactive by default.

<!-- ### 阶段6：Convert（核心格式转换） -->
### Phase 6: `Convert` (Core Format Conversion)

<!-- 将 Elaborate 展开后的 Chisel 原生电路，**无损转换为标准 FIRRTL 中间表示**，完成从「Chisel 高阶电路」到「通用可优化电路」的转换。 -->
**Losslessly convert the native Chisel circuit** produced by `Elaborate` into the standard FIRRTL intermediate representation, transforming a "high-level Chisel circuit" into a "general optimizable circuit."

<!-- ### 阶段7：Compiler（FIRRTL 后端编译） -->
### Phase 7: `Compiler` (FIRRTL Back-End Compilation)

<!-- FIRRTL 官方编译器，完成电路优化、化简、时序规整、位宽适配、语法转换，最终输出标准可综合 Verilog 代码。 -->
The official FIRRTL compiler optimizes and simplifies the circuit, regularizes timing, adapts widths, and converts syntax, finally emitting standard synthesizable Verilog code.

<!-- ## 3.4 流水线入参通俗解析 -->
## 3.4 Plain-Language Explanation of the Pipeline Inputs

```scala
phase.transform(Seq(
  ChiselGeneratorAnnotation(() => gen),
  RunFirrtlTransformAnnotation(new VerilogEmitter)
))
```

<!-- * **ChiselGeneratorAnnotation**：向流水线传入用户定义的顶层硬件模块，告知编译器需要编译的电路对象。 -->
* **`ChiselGeneratorAnnotation`**: Passes the user-defined top-level hardware module into the pipeline and tells the compiler which circuit object to compile.
<!-- * **RunFirrtlTransformAnnotation**：指定后端发射模式，声明本次编译最终目标是**生成 Verilog 代码**。 -->
* **`RunFirrtlTransformAnnotation`**: Selects the back-end emission mode and declares that the final target of this compilation is **Verilog code**.

<!-- # 全流程闭环串联（前端+中层+后端） -->
# Connecting the Complete Flow (Front End + Middle Layer + Back End)

<!-- ## 4.1 完整闭环链路 -->
## 4.1 Complete Closed-Loop Pipeline

<!-- 1. **前端阶段**：解析 Chisel 代码 → 校验语法 → 收集 Annotation 注解 → 绑定 Target 电路位置 → 输出原始电路+注解序列 -->
1. **Front-end phase**: parse Chisel code → check syntax → collect Annotations → bind circuit locations through Targets → output the raw circuit and Annotation sequence.
<!-- 2. **检查阶段**：全局电路合规性复检，拦截非法硬件逻辑 -->
2. **Checks phase**: recheck global circuit compliance and intercept illegal hardware logic.
<!-- 3. **展开阶段**：Elaborate 实例化所有模块、展开批量电路、固化硬件参数 -->
3. **Elaboration phase**: `Elaborate` instantiates all modules, expands bulk-generated circuits, and fixes hardware parameters.
<!-- 4. **文件生成阶段**：绑定输出路径、导出注解文件 -->
4. **File-generation phase**: bind output paths and export the Annotation file.
<!-- 5. **转换阶段**：Convert 将 Chisel 电路转为标准 FIRRTL 中间码 -->
5. **Conversion phase**: `Convert` turns the Chisel circuit into the standard FIRRTL intermediate representation.
<!-- 6. **后端编译阶段**：FIRRTL 优化化简 → 生成最终 Verilog -->
6. **Back-end compilation phase**: optimize and simplify with FIRRTL → generate the final Verilog.

<!-- ## 4.2 层级对应关系 -->
## 4.2 Layer Correspondence

<!-- * **前端**：负责「读代码、查错、贴标签」 -->
* **Front end**: "read code, find errors, and attach labels."
<!-- * **Elaborate**：负责「把抽象代码变成真实电路」 -->
* **`Elaborate`**: "turn abstract code into a real circuit."
<!-- * **Convert**：负责「把 Chisel 电路翻译成通用中间语言」 -->
* **`Convert`**: "translate the Chisel circuit into a general intermediate language."
<!-- * **FIRRTL Compiler**：负责「优化电路、产出 Verilog」 -->
* **FIRRTL Compiler**: "optimize the circuit and produce Verilog."

<!-- # 核心知识点总结 -->
# Summary of the Core Knowledge

<!-- ## 5.1 三大核心模块定义 -->
## 5.1 Definitions of the Three Core Components

<!-- * **Annotation**：单条编译约束便利贴，控制编译器行为，不影响电路逻辑 -->
* **Annotation**: One compilation-constraint sticky note that controls compiler behavior without changing circuit logic.
<!-- * **Elaborate**：电路实例化与展开，从参数化代码生成真实硬件结构 -->
* **`Elaborate`**: Circuit instantiation and elaboration, generating the real hardware structure from parameterized code.
<!-- * **Convert**：Chisel 电路转 FIRRTL 中间表示，统一后端编译标准 -->
* **`Convert`**: Convert the Chisel circuit to the FIRRTL intermediate representation, unifying the back-end compilation standard.

<!-- ## 5.2 流水线七阶段核心口诀 -->
## 5.2 Mnemonic for the Seven Pipeline Phases

<!-- 先检查、再展开、出文件、存注解、可扩展、转中间、编终码 -->
Check first, elaborate next, emit files, save Annotations, allow extensions, convert to an intermediate representation, and compile the final code.

<!-- ## 5.3 编译本质一句话 -->
## 5.3 The Essence of Compilation in One Sentence

<!-- **Chisel 编译 = 前端解析贴标签 + 中层展开转中间 + 后端优化出 Verilog** -->
**Chisel compilation = front-end parsing and labeling + middle-layer elaboration and conversion + back-end optimization and Verilog emission**


<!-- > 更新: 2026-05-22 11:04:20
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/csp0mmp2olpl7a5p> -->

> Updated: 2026-05-22 11:04:20
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/csp0mmp2olpl7a5p>
