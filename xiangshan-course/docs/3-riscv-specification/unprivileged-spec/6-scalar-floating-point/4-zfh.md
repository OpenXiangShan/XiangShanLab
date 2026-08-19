# RISC-V Zfh 标准扩展：半精度浮点指令解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA v20260120，Zfh and Zfhmin Extensions for Half-Precision Floating-Point Version 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html)。本文重点讨论完整的 Zfh；Zfhmin 仅用于说明其与 Zfh 的边界。

## 1. 定位与结论

Zfh 是 RISC-V 的 16-bit 半精度 binary floating-point 标准扩展，遵循 IEEE 754-2008 算术标准，并依赖单精度浮点扩展 F。它不是独立的浮点寄存器文件，也不把浮点寄存器宽度缩窄为 16 bit；半精度值保存在 F 所提供的浮点状态中，并通过 NaN boxing 保持为合法的窄格式值。T1-VERIFIED: [Zfh Extension 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#chap:zfh)

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 前提扩展 | Zfh 依赖 F；没有 F 就没有 Zfh 的浮点状态、fcsr 或 `.H` 指令 | Zfh 可单独提供半精度运算 | T1-VERIFIED |
| 数值格式 | `.H` 表示 IEEE 754-2008 的 16-bit 半精度 binary 格式 | H 是整数 halfword 或 bfloat16 | T1-VERIFIED |
| 寄存器表示 | H 值被 NaN-box 到更宽的浮点寄存器表示中；F-only 时写入 H 结果会使 f 寄存器高 16 bit 为 1 | 只要低 16 bit 正确，任何 f 寄存器内容都可当作 H 计算 | T1-VERIFIED |
| 指令覆盖 | 完整 Zfh 提供 `.H` 的访存、算术、FMA、转换、符号注入、位模式移动、比较和分类 | Zfh 只有 FLH/FSH 与 H/S 转换 | T1-VERIFIED |
| RV64 增量 | `FCVT.L[U].H` 和 `FCVT.H.L[U]` 仅 RV64 可用 | 所有 `.L` 转换也适用于 RV32 | T1-VERIFIED |
| Zfhmin 关系 | Zfhmin 是只含数据传输和部分格式转换的 Zfh 子集，并不等于完整 Zfh | 名称相近表示指令集合相同 | T1-VERIFIED |

本文只解释程序员可见的 ISA 契约。指令延迟、是否复用单精度执行单元、是否把 H 运算先提升到内部更高精度、OS/ABI 的保存策略，以及某个汇编器的 `-march` 支持，都不是 Zfh 正文给出的实现保证。UNVERIFIED。

## 2. 规范来源、版本与证据标签

### 2.1 版本锚点

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Zfh and Zfhmin Extension 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html) | 本文 Zfh 指令集合、依赖关系、访存、转换、比较和 Zfhmin 边界的主依据 | T1-VERIFIED |
| [F Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html) | Zfh 所继承的 fcsr、舍入、异常、算术、比较和分类语义 | T1-VERIFIED |
| [D Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing) | 多精度浮点状态下 NaN boxing 的通用读写规则 | T1-VERIFIED |
| [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 交叉核对 RV32Zfh/RV64Zfh 的助记符和编码分类 | T1-VERIFIED |
| [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html) | 确认用户指定的 Unprivileged ISA 入口版本为 v20260120（January 2026） | T1-VERIFIED |

本文的证据标签含义：

- **T1-VERIFIED**：可以在 RISC-V 官方 ratified 规范正文中定位。
- **T2-CROSS-CHECKED**：由补充官方资料交叉支持，但不替代 T1 正文。
- **INTERPRETIVE**：把规范事实整理为表格、计数或阅读结论，不增加 ISA 保证。
- **UNVERIFIED**：没有对具体硬件、工具链或平台进行验证，不能写成已验证实现事实。

**SPEC-UPDATE-ALERT：** 本文固定在用户指定的 v20260120 快照。提交新设计、软件兼容性或工具链结论前，应重新检查 [ratified library](https://docs.riscv.org/reference/home/index.html) 和 [Zfh 章节](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html)，不要把连续部署页面、草案或某个处理器的实现行为替代这里的版本锚点。T1-VERIFIED。

### 2.2 范围边界

| 范围 | 本文处理方式 | 标签 |
| --- | --- | --- |
| F + Zfh | 主范围；解释 H 值装入 F 的浮点状态、`.H` 指令以及共享的 fcsr | T1-VERIFIED |
| RV32 | 覆盖共同的 W/WU 转换和所有非 L 指令 | T1-VERIFIED |
| RV64 | 补充 L/LU 整数转换及其 RV64-only 边界 | T1-VERIFIED |
| D/Q 组合 | 只说明 `FCVT.D.H`/`FCVT.H.D` 与 `FCVT.Q.H`/`FCVT.H.Q` 在相应扩展存在时可用，以及递归 NaN boxing | T1-VERIFIED |
| Zfhmin | 作为完整 Zfh 的严格子集说明；不把它的限制推广给 Zfh | T1-VERIFIED |
| ABI、编译器和内核 | 未在本次规范阅读中核验；不从 ISA 文本推导 | UNVERIFIED |

## 3. H 格式、F 依赖与 NaN boxing

### 3.1 先区分格式宽度和寄存器最大宽度

Zfh 增加的 H 格式是 16-bit 半精度格式；它不增加一套 16-bit 物理或架构寄存器。Zfh 明确依赖 F，并把 D 章节中的 NaN boxing 方案扩展到“将半精度值装箱在单精度值中”；若同时实现 D 或 Q，该单精度容器还可以继续装箱到双精度或四精度表示中。T1-VERIFIED: [Zfh introduction](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#chap:zfh)

| 组合 | 最大浮点寄存器宽度 FLEN | 合法 H 值的可观察布局 | 标签 |
| --- | ---: | --- | --- |
| F + Zfh | 32 | `f[15:0]` 为 H 的 IEEE 位模式，`f[31:16]` 全为 1 | T1-VERIFIED |
| F + D + Zfh | 64 | `f[15:0]` 为 H 位模式，`f[63:16]` 全为 1 | T1-VERIFIED |
| F + D + Q + Zfh | 128 | `f[15:0]` 为 H 位模式，`f[127:16]` 全为 1 | T1-VERIFIED |

上表把“窄 n-bit 值位于低 n bit，余下高位均为 1”的一般 NaN-boxing 规则代入 n=16 后展开。Zfh 正文明确给出 H-in-S 和递归装箱；D 正文规定多个精度并存时的通用规则。T1-VERIFIED: [Zfh introduction](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#chap:zfh), [NaN Boxing of Narrower Values](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)

### 3.2 写入、读取与未装箱输入

任何向 f 寄存器写入窄于 FLEN 的浮点结果的操作都必须把最高的 `FLEN-n` 位写成 1，形成合法的 NaN-boxed 值。窄格式的加载和 `FMV.n.X` 是“传入”浮点寄存器的传输操作，会创建合法装箱值；相反，从浮点寄存器向外输出窄格式时，只传输低 n 位并忽略其高位。T1-VERIFIED: [NaN boxing transfer rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)

对非传输的窄格式浮点操作，硬件必须检查输入是否正确 NaN-boxed；若高位不全为 1，则把该操作数当作该窄格式的 canonical NaN。这是架构可观察的错误隔离规则，不能把“不合法装箱”当作实现可自由解释的原始 16-bit 数据。T1-VERIFIED: [NaN boxing input checking](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)

这里的 `0xffff_xxxx` 等完整寄存器位型随 FLEN 改变；不应把 F-only 的 32-bit 布局硬编码为 D/Q 实现的完整寄存器内容。INTERPRETIVE。

### 3.3 继承的控制、舍入与异常状态

Zfh 不新增独立的舍入模式或异常 CSR；它使用 F 的 `fcsr`，其中 `frm` 为动态舍入模式字段，`fflags` 为 NV、DZ、OF、UF、NX 的累积异常标志。会发生舍入的浮点指令可在 `rm` 字段指定静态模式，`rm=111` 选择 `frm`；基础 RISC-V 不会因设置浮点异常标志而自动产生浮点 trap。T1-VERIFIED: [F fcsr and rounding](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-2-floating-point-control-and-status-register)

| 编码 | 助记名 | 舍入方式 |
| ---: | --- | --- |
| 000 | RNE | 最近值，平分时取偶数 |
| 001 | RTZ | 向零 |
| 010 | RDN | 向负无穷 |
| 011 | RUP | 向正无穷 |
| 100 | RMM | 最近值，平分时取最大绝对值 |
| 101-110 | 保留 | 不可作为稳定软件接口 |
| 111 | DYN | 仅在指令 `rm` 中选择 `frm`；`frm=111` 本身保留 |

T1-VERIFIED: [F rounding-mode encodings](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#norm:dyn_round_enc)

## 4. 半精度访存：FLH 与 FSH

`FLH` 和 `FSH` 是 LOAD-FP 与 STORE-FP 的 16-bit 变体，使用基础浮点访存的 base+offset 地址形式：`rs1` 提供基址，指令携带 12-bit 有符号字节偏移。`FLH` 从内存装载 16 bit 到浮点寄存器并进行 NaN boxing；`FSH` 只取 `rs2` 的低 16 bit 写入内存。T1-VERIFIED: [Zfh half-precision load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-1-half-precision-load-and-store-instructions), [F load/store addressing](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-5-single-precision-load-and-store-instructions)

| 指令 | 传输方向 | 架构可见位行为 | 标签 |
| --- | --- | --- | --- |
| `FLH rd, offset(rs1)` | memory -> f 寄存器 | 读取 16 bit 原始位模式，写入 `rd` 时形成 H 的 NaN-boxed 值 | T1-VERIFIED |
| `FSH rs2, offset(rs1)` | f 寄存器 -> memory | 只写 `rs2[15:0]`，忽略 f 寄存器中的装箱高位 | T1-VERIFIED |

两条指令都不改变被传输的 16-bit 位，因此非 canonical NaN 的 payload 在内存与低 16-bit 值之间保留。只有 effective address 自然对齐时，规范才保证 FLH/FSH 原子执行；非对齐访问是执行环境（EEI）决定透明处理还是引发 contained/fatal trap 的范围。T1-VERIFIED: [Zfh FLH/FSH atomicity and bit preservation](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-1-half-precision-load-and-store-instructions), [F misaligned access rule](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-5-single-precision-load-and-store-instructions)

不要把“自然对齐时原子”写成“所有 H 访问都是原子的”，也不要从 ISA 推导缓存行、总线事务大小或 LSU 的微架构处理方式。T1-VERIFIED / UNVERIFIED。

## 5. `.H` 计算指令与编码视图

### 5.1 H 格式字段

Zfh 为大多数浮点指令的 2-bit `fmt` 字段新增 H 值：`fmt=10` 表示 16-bit half-precision。`.H` 计算指令在语义上对应 `.S` 指令，但读取半精度操作数并产生半精度结果。T1-VERIFIED: [Zfh computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-2-half-precision-computational-instructions)

| `fmt` | 助记名 | 浮点格式 |
| ---: | --- | --- |
| 00 | S | 32-bit 单精度 |
| 01 | D | 64-bit 双精度 |
| 10 | H | 16-bit 半精度 |
| 11 | Q | 128-bit 四精度 |

T1-VERIFIED: [Zfh format field table](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-2-half-precision-computational-instructions)

从编码类别看，`FLH`/`FSH` 分别使用 LOAD-FP/STORE-FP 的 H 宽度编码；算术、转换、移动、比较和分类位于 OP-FP；四条 FMA 各有自己的 R4-type major opcode，H 均由 `fmt=10` 区分。T1-VERIFIED: [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

| 编码类别 | major opcode | H 的区分字段 |
| --- | --- | --- |
| `FLH` | LOAD-FP (`0000111`) | `funct3=001` |
| `FSH` | STORE-FP (`0100111`) | `funct3=001` |
| 非 FMA `.H` 指令 | OP-FP (`1010011`) | `fmt=10` |
| `FMADD.H` | `1000011` | R4-type 的 `fmt=10` |
| `FMSUB.H` | `1000111` | R4-type 的 `fmt=10` |
| `FNMSUB.H` | `1001011` | R4-type 的 `fmt=10` |
| `FNMADD.H` | `1001111` | R4-type 的 `fmt=10` |

### 5.2 完整 Zfh 的指令族

下表按助记符族归纳完整 Zfh。D/Q 相关的格式转换是否可用取决于 D/Q 是否同时实现；其余列属于 Zfh 的 H 指令集合。T1-VERIFIED: [Zfh computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-2-half-precision-computational-instructions), [Zfh conversion and move instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-3-half-precision-conversion-and-move-instructions)

| 指令族 | 指令 | 主要结果/说明 |
| --- | --- | --- |
| 访存 | `FLH`, `FSH` | 16-bit 内存传输；载入时装箱 |
| 基本算术 | `FADD.H`, `FSUB.H`, `FMUL.H`, `FDIV.H`, `FSQRT.H` | H 操作数和 H 结果 |
| 最小/最大 | `FMIN.H`, `FMAX.H` | H 格式的 minimum-number / maximum-number 语义 |
| 融合乘加 | `FMADD.H`, `FMSUB.H`, `FNMSUB.H`, `FNMADD.H` | 三个 H 源操作数，最终 H 结果一次舍入 |
| 整数转换 | `FCVT.W[U].H`, `FCVT.H.W[U]` | 32-bit signed/unsigned 整数与 H 互转 |
| RV64 整数转换 | `FCVT.L[U].H`, `FCVT.H.L[U]` | 64-bit signed/unsigned 整数与 H 互转，仅 RV64 |
| H/S 转换 | `FCVT.S.H`, `FCVT.H.S` | H 与 S 互转 |
| 条件格式转换 | `FCVT.D.H`, `FCVT.H.D`; `FCVT.Q.H`, `FCVT.H.Q` | 分别要求 D 或 Q 同时存在 |
| 符号注入 | `FSGNJ.H`, `FSGNJN.H`, `FSGNJX.H` | H 位模式的 sign 操作 |
| 位模式移动 | `FMV.X.H`, `FMV.H.X` | H 与整数寄存器之间的原始位搬运 |
| 比较 | `FEQ.H`, `FLT.H`, `FLE.H` | 将 0/1 写入整数寄存器 |
| 分类 | `FCLASS.H` | 将 10-bit one-hot 类别掩码写入整数寄存器 |

上述表是对规范图表与文字清单的归纳，非额外的指令编码定义。INTERPRETIVE。

### 5.3 算术、最小/最大与 FMA

`FADD.H`、`FSUB.H`、`FMUL.H`、`FDIV.H` 和 `FSQRT.H` 分别与 `.S` 对应指令同构；它们对 H 操作数进行计算，结果为 H。会发生舍入的计算使用 `rm`/`frm`，并更新共享的 `fflags`。T1-VERIFIED: [Zfh computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-2-half-precision-computational-instructions), [F computational and rounding semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

`FMIN.H`/`FMAX.H` 继承 F 的对应语义：仅对这两条指令，`-0.0` 小于 `+0.0`；两个输入都是 NaN 时结果为 canonical NaN；仅一个输入为 NaN 时返回非 NaN 输入；任一 signaling NaN 都置 NV，即使最终结果不是 NaN。T1-VERIFIED: [Zfh computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-2-half-precision-computational-instructions), [F minimum/maximum semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

四条 H FMA 的数学形式与 `.S` 相同：

| 指令 | 精确数学形式 |
| --- | --- |
| `FMADD.H` | `(rs1 x rs2) + rs3` |
| `FMSUB.H` | `(rs1 x rs2) - rs3` |
| `FNMSUB.H` | `-(rs1 x rs2) + rs3` |
| `FNMADD.H` | `-(rs1 x rs2) - rs3` |

FMA 在最终结果处舍入，不把中间乘积单独舍入为 H；当两个乘数为无穷和零时，即使加数是 quiet NaN，也须置 NV。T1-VERIFIED: [Zfh computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-2-half-precision-computational-instructions), [F fused multiply-add semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

## 6. 转换、符号注入与位模式移动

### 6.1 H 与整数之间的转换

Zfh 的 H/整数转换按 F 的 S/整数转换类比定义。`FCVT.W.H`/`FCVT.L.H` 将 H 转为有符号 32/64-bit 整数；`FCVT.H.W`/`FCVT.H.L` 将有符号 32/64-bit 整数转为 H；带 `U` 的变体使用无符号整数。`L` 与 `LU` 形式仅 RV64 可用。T1-VERIFIED: [Zfh integer conversion instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-3-half-precision-conversion-and-move-instructions)

| 方向 | RV32 和 RV64 共同指令 | 仅 RV64 |
| --- | --- | --- |
| H -> signed integer | `FCVT.W.H` | `FCVT.L.H` |
| H -> unsigned integer | `FCVT.WU.H` | `FCVT.LU.H` |
| signed integer -> H | `FCVT.H.W` | `FCVT.H.L` |
| unsigned integer -> H | `FCVT.H.WU` | `FCVT.H.LU` |

这些整数/浮点转换按 `rm` 舍入。若浮点到整数的舍入结果无法表示，结果被裁剪到目标格式的最近边界并置 NV；若舍入后结果与输入值不同且未置 NV，则置 NX。RV64 上 `FCVT.W[U].H` 的 32-bit 结果符号扩展到整数寄存器宽度。T1-VERIFIED: [Zfh integer conversion instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-3-half-precision-conversion-and-move-instructions), [F conversion semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-7-single-precision-floating-point-conversion-and-move-instructions)

“无符号 W 转换的目的寄存器也符号扩展”看似反直觉，但这是 RV64 整数寄存器中 W 宽度结果的架构表示规则，不代表该 32-bit 值先被当作有符号数重新计算。T1-VERIFIED / INTERPRETIVE。

### 6.2 H 与其他浮点格式之间的转换

`FCVT.S.H` 和 `FCVT.H.S` 分别在 H 与 S 之间转换；若实现 D，则有 H/D 双向转换；若实现 Q，则有 H/Q 双向转换。向更宽二进制格式转换是精确的，向 H 缩窄时遵守 `rm`。T1-VERIFIED: [Zfh floating-point conversion instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-3-half-precision-conversion-and-move-instructions), [D floating-point conversion semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)

| 已实现的扩展 | 可用的 H 浮点格式转换 | 说明 |
| --- | --- | --- |
| F + Zfh | `FCVT.S.H`, `FCVT.H.S` | 基本 H/S 路径 |
| F + D + Zfh | 另加 `FCVT.D.H`, `FCVT.H.D` | 依赖 D |
| F + D + Q + Zfh | 另加 `FCVT.Q.H`, `FCVT.H.Q` | 依赖 Q；Q 又建立在 D/F 基础上 |

不要把 `FCVT.S.H` 误当作位模式移动：它解释 H 数值并生成 S 数值；要原封不动取走 H 的 16-bit 编码，应使用 `FMV.X.H`。T1-VERIFIED / INTERPRETIVE。

### 6.3 符号注入

`FSGNJ.H`、`FSGNJN.H` 和 `FSGNJX.H` 与单精度对应指令同构：结果的非 sign 位取自 `rs1`；sign 位分别取自 `rs2`、`rs2` sign 的反相、以及两者 sign 的 XOR。它们不置浮点异常标志，也不 canonicalize NaN。T1-VERIFIED: [Zfh sign injection](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-3-half-precision-conversion-and-move-instructions), [F sign injection semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-7-single-precision-floating-point-conversion-and-move-instructions)

当两个源寄存器相同时，汇编器可以把 `FSGNJ.H`、`FSGNJN.H`、`FSGNJX.H` 分别写成 H 格式的 move、negate、absolute-value 伪指令。伪指令名称不是新增的硬件指令编码。T1-VERIFIED / INTERPRETIVE。

### 6.4 FMV.X.H 与 FMV.H.X：搬运位，不转换数值

| 指令 | 位行为 | 关键边界 |
| --- | --- | --- |
| `FMV.X.H rd, rs1` | 把 `rs1` 中 H 的 IEEE 754 16-bit 编码放到整数 `rd` 的低 16 bit，并用 H sign bit 填充 `rd[XLEN-1:16]` | 是位模式移动，不是 H -> int 数值转换 |
| `FMV.H.X rd, rs1` | 取整数 `rs1[15:0]` 的 IEEE 754 H 编码写入浮点 `rd`，并 NaN-box 该结果 | 高于 bit 15 的整数源位被忽略 |

两条 FMV 都不修改被传输的 16-bit 位，因此非 canonical NaN payload 得以保留。T1-VERIFIED: [Zfh FMV.X.H and FMV.H.X](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-3-half-precision-conversion-and-move-instructions)

在 RV64 上，`FMV.X.H` 的高 48 bit 也复制 H sign bit；它不是零扩展，也不是 `FCVT.W.H`。T1-VERIFIED: [Zfh FMV.X.H](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-3-half-precision-conversion-and-move-instructions)

## 7. 比较、分类、NaN 与次正规数

### 7.1 比较指令

`FEQ.H`、`FLT.H`、`FLE.H` 与单精度比较指令同构：条件成立时向整数 `rd` 写 1，否则写 0。若任一操作数是 NaN，三者的比较结果都为 0；`FLT.H` 和 `FLE.H` 是 signaling comparison，任一 NaN 都置 NV；`FEQ.H` 是 quiet comparison，只有任一输入为 signaling NaN 才置 NV。T1-VERIFIED: [Zfh compare instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#flt-pt-to-int-move), [F compare semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-8-single-precision-floating-point-compare-instructions)

| 指令 | 条件 | 遇 NaN 的结果 | NV 规则 |
| --- | --- | --- | --- |
| `FEQ.H` | `rs1 == rs2` | 0 | 仅 signaling NaN |
| `FLT.H` | `rs1 < rs2` | 0 | 任一 NaN |
| `FLE.H` | `rs1 <= rs2` | 0 | 任一 NaN |

### 7.2 FCLASS.H

`FCLASS.H` 检查一个 H 值并向整数 `rd` 写入 10-bit one-hot 类别掩码，恰有一位为 1，且不设置浮点异常标志。类别与 `FCLASS.S` 相同：负无穷、负正规数、负次正规数、-0、+0、正次正规数、正正规数、正无穷、signaling NaN、quiet NaN。T1-VERIFIED: [Zfh classify instruction](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#half-pr-flt-pt-compare), [F FCLASS result encoding](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-9-single-precision-floating-point-classify-instruction)

| `rd` bit | H 值类别 |
| ---: | --- |
| 0 | `-infinity` |
| 1 | 负正规数 |
| 2 | 负次正规数 |
| 3 | `-0` |
| 4 | `+0` |
| 5 | 正次正规数 |
| 6 | 正正规数 |
| 7 | `+infinity` |
| 8 | signaling NaN |
| 9 | quiet NaN |

### 7.3 NaN 与次正规数的继承规则

除非具体指令另有说明，浮点运算产生 NaN 时返回该格式的 canonical NaN；FLH/FSH、FMV.X.H/FMV.H.X 的传输路径，以及 FSGNJ.H 家族不应被误写成 canonicalize 非 canonical NaN payload 的操作。对于 H，canonical NaN 的正号和 quiet-bit 结构来自共享的浮点规则；其常见 16-bit 记法 `0x7e00` 是将该定义套入 binary16 得出的位型。T1-VERIFIED / INTERPRETIVE: [F NaN generation](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-3-nan-generation-and-propagation), [Zfh transfer preservation](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#23-1-1-half-precision-load-and-store-instructions)

H 的次正规数运算遵从共享的 IEEE 754-2008 规则，tininess 在舍入后检测；基础 F/Zfh 不把 flush-to-zero 作为可移植的默认行为。T1-VERIFIED: [F subnormal arithmetic](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-4-subnormal-arithmetic)

## 8. Zfh 与 Zfhmin 的严格边界

Zfhmin 是 Zfh 的子集，目标是让软件把 H 主要作为存储格式，并把大多数计算放在更高精度中完成。它同样依赖 F，但只包含数据传输和格式转换的一小部分；不能假定有任何 `.H` 算术、FMA、比较、分类或 H/整数转换。T1-VERIFIED: [Zfhmin definition](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#half-pr-flt-class)

| 能力 | 完整 Zfh | Zfhmin |
| --- | --- | --- |
| `FLH`, `FSH` | 有 | 有 |
| `FMV.X.H`, `FMV.H.X` | 有 | 有 |
| `FCVT.S.H`, `FCVT.H.S` | 有 | 有 |
| 条件 D/Q 格式转换 | D/Q 同时存在时有 | D/Q 同时存在时有 |
| H 算术、FMA、平方根、最小/最大 | 有 | 无 |
| H/整数转换 | 有 | 无 |
| `FSGNJ.H` | 有 | 无；可用 `FSGNJ.S` 在 f 寄存器间搬运 H 值 |
| H 比较与分类 | 有 | 无 |

T1-VERIFIED: [Zfhmin instruction subset](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#half-pr-flt-class)

规范说明，Zfhmin 可以通过把 H 操作数先转为 S、在 S 上运算、再转回 H 来忠实模拟 H 加减乘除和开方；但以这种方式模拟 H FMA 时，在某些输入的 RNE/RMM 舍入下会有 1 ulp 误差。因此“可用更高精度序列模拟”不等价于“语义上总能替换一个硬件 H FMA”。T1-VERIFIED: [Zfhmin emulation note](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html#half-pr-flt-class)

## 9. 六维映射与实现边界

| 维度 | Zfh 的规范结论 | 不应从规范推出的结论 | 标签 |
| --- | --- | --- | --- |
| 成熟度 | 用户指定的官方 ratified library 中给出 Zfh/Zfhmin Version 1.0 | 某个核、发行版或工具链一定实现 Zfh | T1-VERIFIED / UNVERIFIED |
| ISA 语义 | F 之上的 H 格式、NaN boxing、`.H` 运算/转换/比较/分类 | 固定执行延迟、吞吐率或内部数据通路宽度 | T1-VERIFIED / UNVERIFIED |
| 平台要求 | Zfh 正文定义 ISA 扩展语义和依赖 F | 本文不依据 Zfh 正文判断 RVA23、服务器平台或某产品是否必须实现 Zfh | T1-VERIFIED / UNVERIFIED |
| 软件接口 | Zfhmin 明确描述一种 H 存储、高精度计算的可行软件模式 | 当前 Linux、GCC、LLVM、libc 对某个 `-march` 组合的具体支持版本 | T1-VERIFIED / UNVERIFIED |
| 竞争锚点 | 本文不做 Arm/x86 性能、ABI 或指令逐项映射 | H 格式相同就意味着相同异常、NaN 或 FMA 语义 | UNVERIFIED |
| 部署意图 | 完整 Zfh 提供直接 H 计算；Zfhmin 面向以 H 存储为主的实现 | 任何实现都应以同一种方式做 mixed-precision 运算 | T1-VERIFIED / UNVERIFIED |

## 10. 常见误区

| 误区 | 正确理解 |
| --- | --- |
| “Zfh 增加 16-bit f 寄存器。” | Zfh 使用 F 的浮点状态；H 是 NaN-boxed 窄格式值。 |
| “H 值只看低 16 bit，装箱高位没有语义。” | 非传输 H 运算会检查装箱；不合法值被视作 canonical H NaN。 |
| “`FMV.X.H` 等于 `FCVT.W.H`。” | 前者搬运 16-bit IEEE 编码并符号填充，后者进行数值转换和舍入/异常处理。 |
| “`FLH` 的结果可以直接作为任意 32-bit 位模式使用。” | `FLH` 写入的是合法 NaN-boxed H；若要读其 H 原始位，使用 `FMV.X.H`。 |
| “Zfhmin 可以无差别替代 Zfh。” | Zfhmin 没有 H 算术、FMA、比较、分类和 H/整数转换；H FMA 模拟还可能有 1 ulp 差异。 |
| “H->S 是位扩展。” | `FCVT.S.H` 是数值格式转换；位传输应使用 FMV。 |
| “ISA 指令存在就代表编译器、OS 和 ABI 已支持。” | 这需要按具体工具链、ABI 和平台另行核验。 |

## 11. 面向实现/验证的检查清单

- [ ] 解码只在 F 已实现时允许 Zfh；RV32 不接受 `FCVT.L[U].H` 或 `FCVT.H.L[U]`。
- [ ] 任何写 H 结果到 f 寄存器的路径，包括 `FLH`、`FMV.H.X`、H 算术和产生 H 结果的格式转换，都形成合法 NaN-boxed 值。
- [ ] 所有非传输 H 操作在执行前检查装箱高位；未装箱操作数按 canonical H NaN 处理。
- [ ] `FSH`、`FMV.X.H` 仅以低 16-bit H 编码为传输值，不意外修改非 canonical NaN payload。
- [ ] `FMV.X.H` 按 H sign bit 填充整数目标的上部 `XLEN-16` 位；不要零扩展。
- [ ] H 算术、FMA 和转换共享 `frm`/`fflags` 的正确舍入与累积异常语义。
- [ ] 覆盖 `FEQ.H`、`FLT.H`、`FLE.H` 的 qNaN/sNaN NV 区别，以及 `FCLASS.H` 的 10 个 one-hot 输出。
- [ ] 覆盖自然对齐和非对齐 FLH/FSH 的 EEI 分支，不把非对齐行为硬编码为一个 ISA 结论。
- [ ] 需要 Zfhmin 时，明确测试其子集而非复用完整 Zfh 的算术测试；特别检查 H FMA 的软件替代策略。
- [ ] 在下一次评审前重新检查 [Zfh 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html)、[F 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html) 和 [D NaN boxing](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing) 是否有更新。

## 12. 小结

Zfh 的核心不是“把浮点宽度缩成 16 bit”，而是在 F 的浮点状态和控制语义之上增加合法 NaN-boxed 的 H 值以及完整 `.H` 指令族。理解时要固定三条边界：H 值必须正确装箱、FMV 与 FCVT 分别是位搬运与数值转换、Zfhmin 只是完整 Zfh 的有限子集。T1-VERIFIED / INTERPRETIVE。

## 13. 参考资料

1. [RISC-V Zfh and Zfhmin Extensions for Half-Precision Floating-Point, Version 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zfh.html)
2. [RISC-V F Extension for Single-Precision Floating-Point, Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)
3. [RISC-V D Extension for Double-Precision Floating-Point, Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)
4. [RISC-V RV32/64G Instruction Listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)
5. [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)
