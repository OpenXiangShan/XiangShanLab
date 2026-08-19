# RISC-V E 缩减整数基础 ISA：RV32E 与 RV64E

> 规范基线：RISC-V Unprivileged ISA `v20260120`，章节 *"RV32E and RV64E Base Integer Instruction Sets, Version 2.0"*。本文以用户指定的固定快照解释 **RV32E**，并在需要时说明 RV64E 的对应含义。事实结论均标注证据等级；没有特定芯片、编译器版本或板卡实测支持的内容不外推为已证实事实。

## 1. 结论与阅读范围

`E` 的本质不是增加一批新运算指令，而是把基础整数 ISA 的通用整数寄存器（GPR）从 32 个缩减为 16 个。`RV32E` 是 `RV32I` 的缩减变体，`RV64E` 是 `RV64I` 的缩减变体；规范明确说二者唯一的 ISA 改变是整数寄存器数量变为 16。因此，理解 E 前应先掌握对应的 `RV32I` 或 `RV64I` 基础指令语义。 T1-VERIFIED: [RV32E/RV64E 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

RISC-V ISA 名称以 `RV32I`、`RV32E`、`RV64I` 或 `RV64E` 开头；因此严谨地说，E 是 **Reduced Integer 基础 ISA**，不是把 `E` 作为普通后缀叠加在 `I` 后面。例如应写 `rv32e`，而不是 `rv32ie`。 T1-VERIFIED: [ISA naming, 36.1.2 and Table 1](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html)

| 问题 | 直接答案 | 证据等级 |
| --- | --- | --- |
| RV32E 改了什么？ | 仅提供 `x0-x15` 这 16 个 GPR；`x0` 仍是恒为零的专用寄存器 | T1-VERIFIED |
| RV32E 引入了新助记符吗？ | 没有。它沿用 RV32I 的指令集编码和指令语义，只限制可指定的寄存器 | T1-VERIFIED |
| 为什么要有它？ | 为嵌入式微控制器提供更小的基础核；规范给出的小型 RV32I 核观察值显示，移除上 16 个寄存器可约节省除存储器外核面积的 25%，并相应降低核功耗 | T1-VERIFIED，限于规范报告的实现观察 |
| 最适合什么？ | 资源敏感的嵌入式微控制器和小型控制核；RV64E 还面向大型 SoC 内的微控制器，以及希望降低上下文状态的高线程 64-bit 处理器 | T1-VERIFIED |
| 代价是什么？ | 可供编译器分配的整数寄存器减少，寄存器压力、溢出和调用约定都必须重新评估；具体代码尺寸与性能变化取决于工作负载和工具链 | INTERPRETIVE / UNVERIFIED（未针对具体程序实测） |

本文讨论非特权 ISA 的程序员可见契约，**不**把“有 E”直接推导为某颗芯片的寄存器文件端口数、时序、上下文切换实现、操作系统支持或二进制生态兼容性。

## 2. 证据、版本与边界

### 2.1 来源层级

| 优先级 | 本次来源 | 用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1 | [UnifiedDB continuous deployment](https://riscv.github.io/riscv-unified-db/) | 检查当前机器可读规范入口和生成时间；它来自主分支，不能覆盖固定版本的语义锚点 | T1-VERIFIED |
| Layer 2 | [Normative Rules guidelines](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 判断有 `norm:` 标记时的规范性约束写法；本章没有需要额外摘录的 E 专属规则 | T1-VERIFIED |
| Layer 3 | [v20260120 RV32E/RV64E 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html) | 寄存器数、编码、设计意图和标准扩展兼容性的主依据 | T1-VERIFIED |
| Tier-1 软件 | [RISC-V psABI calling convention](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html) / [GCC RISC-V options](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Options.html) | 将 ISA 约束与 ABI、`-march` / `-mabi` 选择分开说明 | T1-VERIFIED |
| Tier-1 profile | [RVA23 profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html) | 仅说明 E 与 64-bit 应用处理器 profile 的边界 | T1-VERIFIED |

**SPEC-UPDATE-ALERT：** 用户指定的 `v20260120` 是本文可复现的语义基线；当前 UDB 部署来自 2026-08-18 的主分支，ISA 手册仓库也有更晚的自动发布物。它们可用于发现更新，但不应静默替换本文对 `v20260120` 的逐条解释。下次评审应重新核对 [UDB](https://riscv.github.io/riscv-unified-db/) 与 [ISA releases](https://github.com/riscv/riscv-isa-manual/releases)。 T1-VERIFIED

### 2.2 成熟度与 ABI 状态必须分开

| 状态 | 项目 | 当前结论 | 依据 |
| --- | --- | --- | --- |
| **RATIFIED 2023-01** | RV32E/RV64E Base Integer ISA v2.0 | E 基础 ISA 本身已 ratified | [RVI technical-specifications archive](https://riscv.atlassian.net/wiki/spaces/HOME/pages/16154899/RISC-V%2BTechnical%2BSpecifications%2BArchive)；[v20260120 unprivileged preface](https://docs.riscv.org/reference/isa/v20260120/_attachments/riscv-unprivileged.pdf) |
| **DRAFT（当前 ABI 状态页）** | ILP32E calling convention | ILP32E ABI 尚未是稳定 ABI | [ABI status](https://docs.riscv.org/reference/abi/status.html) |

**SPEC-UPDATE-ALERT（ABI 文本冲突）：** 当前 psABI 的 ILP32E 小节仍含有“RV32E 不是 ratified base ISA”的历史性解释；这与 `v20260120` 前言和 RVI 技术规格记录中“RV32E/RV64E v2.0 已于 2023-01 ratified”的状态冲突。本文以当前 ISA 状态为准：**E ISA 已 ratified；ILP32E ABI 仍为 Draft。** psABI 中关于 ABI 可能变化的警告仍然有效。 T1-VERIFIED: [psABI ILP32E](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html)；[ABI status](https://docs.riscv.org/reference/abi/status.html)

### 2.3 证据标签

- **T1-VERIFIED**：可直接定位到 RISC-V 规范、profile、psABI 或 GCC 官方文档。
- **T2-CROSS-CHECKED**：有独立可靠资料交叉支持，但不代替规范主依据。
- **INTERPRETIVE**：由已证实语义导出的工程分析，不是 ISA 对任何实现的保证。
- **UNVERIFIED**：缺少特定产品、工具链版本或实测证据，不能当作部署事实。

## 3. 为什么需要 E：减少的是寄存器状态，而不是指令语义

### 3.1 规范给出的设计动机

RV32E/RV64E 面向嵌入式系统中的微控制器。规范特别指出，RV32E 的目标是为嵌入式微控制器提供更小的基础核；RV64E 除大型 SoC 内的微控制器外，也有降低高线程 64-bit 处理器上下文状态的用途。 T1-VERIFIED: [RV32E/RV64E 2.0, introduction](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

规范记录了一项**实现观察**：在小型 RV32I 核实现中，上半部分 16 个寄存器约占“除存储器外的整个核面积”的四分之一；移除它们约可节省 25% 的该部分面积，并有相应的核功耗下降。这是设计取舍的量级证据，**不是**所有 RV32E 实现都必然得到 25% 面积或功耗收益的架构保证。 T1-VERIFIED: [RV32E/RV64E 2.0, programmers' model note](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

| 设计压力 | E 的处理方式 | 得到的直接收益 | 必须付出的代价 |
| --- | --- | --- | --- |
| 小型微控制器希望压缩核内状态 | 将整数 GPR 从 32 个减为 16 个 | 寄存器文件及其相关实现状态可缩小；规范报告的小核观察值约为 25% 非存储器核面积 | 同时活跃的局部值更容易超过可用寄存器数 |
| 功耗预算受限 | 少维护 16 个架构整数寄存器 | 规范报告相应的核功耗下降 | 实际节能取决于寄存器文件、旁路网络、工艺和工作负载 |
| 高线程 RV64 设计希望减少每线程架构上下文 | RV64E 保留 64-bit XLEN，同时把 GPR 数缩至 16 | 每个线程的整数寄存器状态更小 | 线程内的寄存器压力可能更高 |

表中第一、二、三行的动机和观察值为 T1-VERIFIED；“更容易溢出”“取决于实现”的因果分析为 INTERPRETIVE。它们不能替代综合、功耗或编译器基准测试。

### 3.2 不是“16 个寄存器就自动缩短指令”

RV32E/RV64E 与 RV32I/RV64I 分别使用**相同**的指令集编码；寄存器字段仍是原有编码布局。区别是只提供 `x0-x15`，凡编码指定 `x16-x31` 的情况均为 reserved。因而 E 的直接目标是缩减寄存器状态，并没有把上半部分寄存器编码立即重新定义为新的通用 opcode 空间。 T1-VERIFIED: [RV32E/RV64E 2.0, instruction-set encoding](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

在旧草案中，这些上半寄存器编码曾被视为 custom；当前 Version 2.0 采取更保守的规则，将它们保留，以便未来在 custom 空间和新的标准编码之间分配。因此，不能把“RV32E 中出现 `x16-x31`”默认解释成合法自定义指令、NOP 或某个固定的非法指令行为。 T1-VERIFIED: [RV32E/RV64E 2.0, encoding note](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

这也解释了一个常见的工程误判：E **可能**借助较小的寄存器文件降低硬件成本，但不凭自身改变 32-bit 基础指令的寄存器字段宽度；程序二进制的总大小仍会受到压缩扩展、寄存器溢出、函数调用和优化策略影响。 INTERPRETIVE；具体程序的收益 UNVERIFIED。

## 4. RV32E 程序员模型

### 4.1 寄存器数量与 XLEN 要分开看

| 基础 ISA | XLEN / 整数寄存器宽度 | 提供的 GPR | 可写 GPR 数 | 与 I 版本的关系 |
| --- | ---: | --- | ---: | --- |
| RV32I | 32 bit | `x0-x31` | 31 | RV32 基础整数 ISA |
| **RV32E** | **32 bit** | **`x0-x15`** | **15** | RV32I 的缩减版本 |
| RV64I | 64 bit | `x0-x31` | 31 | RV64 基础整数 ISA |
| **RV64E** | **64 bit** | **`x0-x15`** | **15** | RV64I 的缩减版本 |

RV32E/RV64E 的 16 个寄存器包括 `x0`，而 `x0` 是专用零寄存器；所以通常可自由写入的整数寄存器只有 `x1-x15` 共 15 个。把“16 个 GPR”误写成“16 个可写寄存器”会使寄存器分配和 ABI 分析多算一个寄存器。 T1-VERIFIED: [RV32E/RV64E 2.0, programmers' model](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

`E` 不改变 RV32 与 RV64 的 XLEN 区分：RV32E 是 32-bit 整数寄存器状态，RV64E 是 64-bit 整数寄存器状态。把 RV64E 描述为“RV32E 加宽到 64 位并仍有 32 个寄存器”是错误的。 T1-VERIFIED: [RV32E/RV64E 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)；[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

### 4.2 与 RV32I/RV64I 的逐项差异

| 维度 | RV32I / RV64I | RV32E / RV64E | 课程中的正确推论 |
| --- | --- | --- | --- |
| 整数寄存器 | `x0-x31` | `x0-x15` | 上半 16 个寄存器不可用 |
| `x0` | 专用零寄存器 | 同样是专用零寄存器 | 16 个总数不等于 16 个工作寄存器 |
| 指令语义 | 对应 I 基础 ISA 的算术、控制流、load/store 等语义 | 除寄存器数外唯一 ISA 变化 | 不能把 E 当作另一套算术或访存语义 |
| 指令编码 | 对应 I 的编码 | 相同编码布局 | 不能以 E 的名义重新解释寄存器字段 |
| `x16-x31` 的编码 | 指定已有上半寄存器 | reserved | 不能作为可移植软件的 custom 编码使用 |

该表前四行是 T1-VERIFIED；最后一列是对规范文本的直接阅读结论。主来源为 [RV32E/RV64E 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)。

### 4.3 汇编示意：差异在寄存器可用范围

以下代码使用 `x5-x7`，在 RV32I 与 RV32E 中都处于可提供的寄存器范围内：

```asm
# a0 = base address, a1 = value
lw   t0, 0(a0)       # t0 is x5
add  t1, t0, a1      # t1 is x6
sw   t1, 0(a0)
```

下列写法指定 `x16`，因此不符合 RV32E/RV64E 的寄存器约束；它在 I 基础 ISA 上可以有普通寄存器含义，但在 E 中对应编码是 reserved。

```asm
add  x16, x5, x6     # not a portable RV32E/RV64E instruction
```

这两个例子不说明某个汇编器必然接受或拒绝源代码，也不承诺某硬件对 reserved encoding 的运行时异常表现；它们只说明由 ISA 定义的可移植机器码边界。 T1-VERIFIED / UNVERIFIED（具体 assembler、linker 与 trap 行为未在本文固定）。

## 5. 指令、扩展与二进制边界

### 5.1 E 没有“专属指令清单”

因为 RV32E/RV64E 的唯一 ISA 改变是 GPR 数量，不能把它讲成像 `M`、`A`、`C` 那样新增一张 E 指令表。应从 RV32I/RV64I 已有的算术、控制流和访存语义出发，再施加“只能指定 `x0-x15`”这一约束。 T1-VERIFIED: [RV32E/RV64E 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

除非另有说明，与 RV32I/RV64I 兼容的标准扩展也分别与 RV32E/RV64E 兼容。这是**扩展的 ISA 兼容性**表述，不等于任何具体组合都有稳定 ABI、编译器支持、操作系统支持或平台 profile 承诺。 T1-VERIFIED: [RV32E/RV64E 2.0, introduction note](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)

| 结论层次 | 可以得出什么 | 不能直接得出什么 |
| --- | --- | --- |
| ISA | 标准扩展若与 RV32I/RV64I 兼容，除非另有说明也与对应 E 基础 ISA 兼容 | 某实现一定实现该扩展 |
| 编码 | 只允许 `x0-x15`，涉及 `x16-x31` 的编码 reserved | reserved 一定 trap、NOP 或可作为 custom 指令 |
| 软件 | 编译器必须为目标 ISA 选择合法寄存器 | 任意 RV32I 对象文件都能与 RV32E 对象文件直接链接或运行 |
| 平台 | 平台可选择 E 作为小型核基础 | 它满足 RVA23 或其他应用处理器 profile |

前两行是 T1-VERIFIED；后两行是边界提醒。对具体对象文件、链接器和运行时的结果必须以目标 ABI、ELF 属性、编译器版本和平台文档验证，当前为 UNVERIFIED。

### 5.2 `misa` 与运行时发现的边界

特权 ISA 的 `misa` 扩展字段中，字母 `E` 表示 RV32E/64E 基础 ISA，字母 `I` 表示 RV32I/64I 基础 ISA。是否在某执行环境中可读、写或可靠使用 `misa`，取决于特权架构和执行环境；用户态应用不能仅凭本文假定可直接读取它。 T1-VERIFIED: [Machine ISA, `misa` extension field](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html)

因此，部署检查应区分“实现有 E 基础 ISA”“执行环境向软件暴露可用发现机制”“工具链按 E 生成代码”三件事。它们不是同一个问题。 T1-VERIFIED / INTERPRETIVE。

## 6. ABI 与工具链：ISA 缩减会传导到调用约定

### 6.1 ILP32E 的已知边界

RISC-V psABI 定义了面向 RV32E 的 **ILP32E** 调用约定。与普通整数调用约定相比，它使用 32-bit 栈对齐，`x16-x31` 不参与调用约定，故有 6 个参数寄存器 `a0-a5`、2 个被调用者保存寄存器 `s0-s1` 和 3 个临时寄存器 `t0-t2`。 T1-VERIFIED: [psABI ILP32E calling convention](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html)

RV32E/RV64E v2.0 已是 ratified ISA；但当前 ABI 状态页将 ILP32E 标为 Draft。psABI 的 ILP32E 小节说明它记录的是当时 GCC 的实现，未来可能改变；该小节中关于 E ISA 未 ratified 的旧背景文字与当前 ISA 状态不一致，已在第 2.2 节标为更新警报。因此，不应把 ILP32E 的细节当作永久不变的二进制兼容承诺。 T1-VERIFIED: [v20260120 unprivileged preface](https://docs.riscv.org/reference/isa/v20260120/_attachments/riscv-unprivileged.pdf)；[psABI ILP32E](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html)；[ABI status](https://docs.riscv.org/reference/abi/status.html)

ILP32E 不兼容于要求 load/store 对齐超过 32 bit 的 ISA；psABI 特别指出它不得与 `D` 扩展一起使用。这里的限制来自 ABI 规则，不应被误读成“E ISA 自身绝对不能组合任何浮点扩展”。 T1-VERIFIED: [psABI ILP32E calling convention](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html)

| ABI 项 | 常规 RV32 整数 ABI 的直觉 | ILP32E 的明确差异 | 工程影响 |
| --- | --- | --- | --- |
| 参数寄存器 | 更多寄存器可参与 | `a0-a5`，共 6 个 | 更多参数可能更早经栈传递 |
| callee-saved | 更多寄存器可供跨调用保留 | `s0-s1`，共 2 个 | 跨调用活跃值更容易需要保存或重算 |
| temporaries | 更多临时寄存器 | `t0-t2`，共 3 个 | 寄存器分配空间更窄 |
| 栈对齐 | 常规 ABI 规则 | 仅需 32-bit 对齐 | 不能与需要更严格对齐的 ISA/ABI 组合混用 |

前三列是 T1-VERIFIED；“更早经栈传递”“更容易保存或重算”是编译器寄存器分配的 INTERPRETIVE 后果，实际效果需查看生成汇编和基准结果。

### 6.2 编译器选项不是 ABI 稳定性的替代品

GCC 文档接受 `-march=rv32e`，也说明 `ilp32e` 只能与 `rv32e` 使用；它还列出 `lp64e` 只能与 `rv64e` 使用，但明确警告这两个 E ABI 目前并未得到良好规范化、可能改变。 T1-VERIFIED: [GCC RISC-V options](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Options.html)

```text
# 仅作目标选择的形状示例；实际工具链版本和 C 库必须另行验证
-march=rv32e -mabi=ilp32e
```

上例不是可移植构建命令的承诺：是否支持所需扩展、运行库、启动文件、调试器和链接器，要按具体 GCC/binutils/LLVM/libc 版本验证。 T1-VERIFIED（GCC 选项存在）/ UNVERIFIED（本仓库未执行工具链构建）。

## 7. E 通常用在哪里，何时不应优先选择

### 7.1 规范直接支持的场景

| 场景 | 规范中的依据 | 选择 E 的理由 | 不应越界的结论 |
| --- | --- | --- | --- |
| 资源敏感的嵌入式微控制器 | RV32E/RV64E 章节明确说为嵌入式系统中的 microcontrollers 设计 | 缩减 GPR 状态以构建更小的基础核 | 不等于所有 MCU 都实现 E |
| 大型 SoC 中的微控制器 | 规范明确提到 RV64E 的兴趣场景 | 64-bit 控制核也可缩减寄存器状态 | 不是 RV32E 的专属或已普遍部署事实 |
| 高线程 64-bit 处理器的上下文状态优化 | 规范明确提到 RV64E 的兴趣场景 | 每线程整数寄存器状态较少 | 不保证吞吐、延迟或上下文切换时间必然更优 |

该表的“规范中的依据”和“选择理由”为 T1-VERIFIED；最后一列是为了约束误读的 INTERPRETIVE 说明。来源：[RV32E/RV64E 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)。

“IoT、传感器节点、可穿戴设备、极低成本控制器”常被用作 E 的直观例子，但用户指定的 ISA 章节没有列出这些具体产品类别，也没有给出采用率；在没有厂商一手资料时，它们是 **UNVERIFIED 的可能部署例子**，不应写成“E 的已验证常用市场”。

### 7.2 选择决策表

| 目标环境 | 建议 | 原因与核验项 | 标签 |
| --- | --- | --- | --- |
| 裸机、小型控制固件，面积/功耗优先 | 评估 RV32E | 规范目标与小核面积/功耗动机匹配；查看寄存器溢出、代码尺寸、`ilp32e` 工具链和外设驱动 | T1-VERIFIED + INTERPRETIVE |
| 有 RTOS 的控制核 | 先做编译和任务切换测量 | E 减少架构 GPR 状态，但 ABI 寄存器也更少；需测量栈、上下文和热点路径 | INTERPRETIVE / UNVERIFIED |
| 64-bit SoC 内管理/控制 hart | 评估 RV64E 的状态开销 | 规范直接列为兴趣场景；需确认 `lp64e`、运行库与调试生态 | T1-VERIFIED + UNVERIFIED |
| RVA23 64-bit 应用处理器软件基线 | 不把 E 当作替代基线 | RVA23U64/RVA23S64 的强制基础 ISA 是 RV64I，而不是 RV64E | T1-VERIFIED |
| 高性能通用代码，寄存器密集 | 默认先评估 RV32I/RV64I | 32 个 GPR 能缓解循环展开、软件流水和 cache tiling 的寄存器压力；实际优劣仍要测量 | T1-VERIFIED（RV32I rationale）+ INTERPRETIVE |

最后一行的背景来自 RV32I 章节：更多 GPR 有助于高性能代码中的 loop unrolling、software pipelining 和 cache tiling。它不是“E 一定慢”的结论；工作负载、缓存、`C` 扩展和优化器都可能改变结果。 T1-VERIFIED: [RV32I 2.1 rationale](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

## 8. 六维映射：把 ISA、平台与生态分开

E 是寄存器文件规模的基础 ISA 选择，不是可与某一条 Arm/x86 指令逐一对应的特性。下表以“同样的架构取舍”作比较锚点；没有为本课程进一步核对 Arm/x86 最新手册的逐条寄存器或 ABI 细节，故比较列不作为跨架构事实依据。

| 维度 | RISC-V E 的可证实边界 | Arm/x86 比较锚点 | 平台/软件含义 | 标签 |
| --- | --- | --- | --- | --- |
| 1. 成熟度 | E 基础 ISA v2.0 已 ratified；ILP32E 在 psABI 状态页为 Draft，ABI 章提示其可能变化 | 不做版本化指令等价声明 | 项目不能只根据 ISA ratification 推导 ABI 稳定性 | T1-VERIFIED / UNVERIFIED（比较细节） |
| 2. ISA 语义 | 32/64-bit XLEN 不变，GPR 数减为 16，`x16-x31` 编码 reserved | “较少架构 GPR”是设计维度，不是特定指令等价 | RTL/模拟器应遵守寄存器可用范围和 reserved 编码规则 | T1-VERIFIED |
| 3. 平台要求 | E 章节描述目标与兼容性，不给出通用服务器或 OS 平台 MUST | RVA23 强制 RV64I | 应用处理器 profile 不能用 RV64E 代替 RV64I | T1-VERIFIED |
| 4. 软件证据 | GCC 识别 `rv32e`/`ilp32e` 与 `rv64e`/`lp64e`，但 E ABI 被警告为未良好规范化 | 无本次 Arm/x86 工具链映射 | 构建系统必须锁定实际工具链和 C 库版本 | T1-VERIFIED / UNVERIFIED |
| 5. 竞争锚点 | E 的直接优势是缩小整数寄存器状态，而非新算法能力 | 与 Arm/x86 的价值比较只能在同一面积、功耗、ABI 和工作负载下完成 | 不应凭寄存器数就宣称跨 ISA 性能或能效领先/落后 | INTERPRETIVE / UNVERIFIED |
| 6. 部署意图 | 嵌入式 MCU、小型控制核；RV64E 还包括 SoC MCU 与高度线程化 64-bit 处理器的上下文状态 | 不是服务器/桌面通用二进制基线的替代 | 先按部署域选择，再测寄存器压力与软件生态 | T1-VERIFIED / INTERPRETIVE |

这个映射的结论是：RISC-V E 与 Arm/x86 的关系是“相同的寄存器资源取舍维度”，而不是一项可直接比较的 ISA 指令能力。将 E 说成“比 Arm/x86 少/多多少寄存器所以更好”缺少架构版本、ABI、实现与基准前提，属于 UNVERIFIED。

## 9. 常见误解

| 误解 | 正确说法 |
| --- | --- |
| “E 是在 RV32I 上加一个扩展。” | ISA 名称中 `RV32E` / `RV64E` 本身是基础 ISA；它们分别是 I 版本的减少寄存器变体。 T1-VERIFIED |
| “RV32E 有 16 个可随便写的寄存器。” | `x0` 是专用零寄存器，通用可写寄存器为 `x1-x15` 共 15 个。 T1-VERIFIED |
| “只剩 16 个寄存器，所以 32-bit 指令可马上多出一半 opcode。” | E 使用 I 的相同编码；`x16-x31` 相关编码为 reserved，不是自动可用的 opcode 空间。 T1-VERIFIED |
| “所有 RV32I 二进制也可直接跑在 RV32E。” | 只要实际引用 `x16-x31` 就不符合 E 约束；对象/ABI/运行时兼容性还需单独验证。 T1-VERIFIED / UNVERIFIED |
| “E 一定减少程序大小且总是更低功耗。” | 规范只报告小核的面积/功耗观察。代码尺寸和整体能耗取决于溢出、`C` 扩展、编译器和实现。 T1-VERIFIED / INTERPRETIVE |
| “ILP32E 已是稳定的长期 ABI。” | 当前 psABI 标 ILP32E 为 Draft，并警告未来可能改变。 T1-VERIFIED |

## 10. 验证清单与待确认项

在下一次里程碑评审或真正选择 E 之前，逐项完成：

- [ ] 重读 [固定语义锚点 `v20260120` 的 RV32E/RV64E 章节](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)，核对 `x0-x15`、reserved encoding 和标准扩展兼容性的措辞。
- [ ] 查看 [当前 UDB](https://riscv.github.io/riscv-unified-db/) 与 [ISA release 列表](https://github.com/riscv/riscv-isa-manual/releases)，确认 `v20260120` 后是否影响 E 的语义或状态。
- [ ] 为目标工具链实际执行 `-march=rv32e -mabi=ilp32e` 的编译、反汇编与链接检查，确认没有 `x16-x31` 的使用；本课程尚未执行此项，UNVERIFIED。
- [ ] 核对 [ILP32E psABI](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html) 和 [ABI status](https://docs.riscv.org/reference/abi/status.html)，特别是 Draft 状态、栈对齐和与 `D` 的限制。
- [ ] 在目标工作负载上比较 RV32I/RV32E 的 `.text`、栈峰值、寄存器溢出、周期和功耗；规范中的约 25% 观察不能代替此数据，UNVERIFIED。
- [ ] 若目标是 64-bit 应用处理器，核对 [RVA23 profile](https://docs.riscv.org/reference/rva23/rva23-profiles.html)：它的 mandatory base 是 RV64I，不能以 RV64E 取代。

当前公开缺口：本课程没有目标 SoC、具体 E 核、编译器版本、RTOS/运行库或功耗报告的证据。因此“某产品已采用 E”“E 在该固件上更小/更快/更省电”“LP64E 可稳定部署”均保持 UNVERIFIED。

## 11. 参考资料

1. [RISC-V Unprivileged ISA `v20260120`: RV32E and RV64E Base Integer Instruction Sets, Version 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32e.html)
2. [RISC-V Unprivileged ISA `v20260120`: RV32I Base Integer Instruction Set, Version 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)
3. [RISC-V Unprivileged ISA `v20260120`: RV64I Base Integer Instruction Set, Version 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)
4. [RISC-V ISA naming conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html)
5. [RISC-V psABI: procedure calling convention](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html) and [ABI status](https://docs.riscv.org/reference/abi/status.html)
6. [GCC RISC-V options](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Options.html)
7. [RVA23 profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html)
8. [RISC-V Technical Specifications Archive: RV32E/RV64E ratified in January 2023](https://riscv.atlassian.net/wiki/spaces/HOME/pages/16154899/RISC-V%2BTechnical%2BSpecifications%2BArchive)
