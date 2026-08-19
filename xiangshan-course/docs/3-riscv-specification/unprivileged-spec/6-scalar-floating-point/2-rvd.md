# RISC-V D 标准扩展：双精度浮点指令解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA v20260120，D Extension Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)。D 以 F 为前提：本文先说明 RV32D/RV64D 的共同双精度语义，再单列 RV64D 的增量。本文只讨论 ISA 可观察契约，不把某条指令的延迟、浮点单元数量、流水线位置或某个操作系统的上下文切换策略写成 D 扩展保证。

## 1. 定位与结论

`D` 是标准双精度浮点扩展，为 RISC-V 增加符合 IEEE 754-2008 的双精度计算指令；它**依赖 F**，不是可脱离单精度浮点状态独立存在的扩展。D 将既有的 32 个 `f0-f31` 寄存器扩展到可容纳 64-bit 双精度值；在 F+D、未实现 Q 的组合中，这意味着 `FLEN=64`，而不是新增一套双精度寄存器。T1-VERIFIED: [D Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-d-extension-for-double-precision-floating-point-version-2-2)

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| D 与 F 的关系 | `D -> F`；F 提供单精度状态、`fcsr`、舍入和异常标志的共同基础 | 只实现 D 指令而无需 F | T1-VERIFIED |
| 寄存器宽度 | 在 F+D、未实现 Q 的组合中，`f0-f31` 均为 64 bit，`FLEN=64` | D 额外增加 32 个寄存器 | T1-VERIFIED |
| 单精度值 | 同一组 64-bit `f` 寄存器还可持有 `.S` 值；有效 `.S` 值必须 NaN-boxed | `.S` 值在 D 机器上只需低 32 bit 正确 | T1-VERIFIED |
| RV32D 与 RV64D | RV32D 已有 `FLD/FSD` 和 `.D` 计算；RV64D 再增加 L/LU 整数转换与 `FMV.X.D`/`FMV.D.X` | D 只能用于 RV64 | T1-VERIFIED |
| 双精度访存原子性 | `FLD/FSD` 仅在有效地址自然对齐且 `XLEN>=64` 时保证原子执行 | D 的所有 64-bit 访存天然原子 | T1-VERIFIED |
| 位模式路径 | `FLD/FSD` 与 RV64 的 `FMV.X.D`/`FMV.D.X` 不修改传输位，保留非 canonical NaN payload | 所有 D 指令都会 canonicalize NaN | T1-VERIFIED |
| 应用处理器 profile | `RVA23U64` 将 D 列为 mandatory extension；这不等于所有 RISC-V 实现都必须有 D | D 是所有 RISC-V 的基础要求 | T1-VERIFIED |

本文中的“double”是 IEEE 754 双精度格式，`.D` 是对应指令后缀；它们不等于 RV64 的“64-bit 整数寄存器环境”。`XLEN` 决定整数寄存器宽度，`FLEN` 决定浮点寄存器宽度，两者必须分开看。T1-VERIFIED: [D register state](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-1-d-register-state)

## 2. 规范来源、版本与证据标签

### 2.1 来源层级

| 层级 | 来源 | 本文用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1: UDB | [RISC-V UnifiedDB continuous deployment](https://riscv.github.io/riscv-unified-db/) | 检查机器可读规范和指令资料的当前入口；它由 `main` 连续生成，不能静默替代固定版本的语义锚点 | T1-VERIFIED |
| Layer 2: Normative | [Normative Rules guidelines](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 说明 HTML 中 `norm:` 锚点对应可定位的规范性文字；一个锚点不必恰好对应一条规则 | T1-VERIFIED |
| Layer 3: Ratified ISA | [D Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html) | D 的依赖、寄存器、NaN boxing、访存、转换、移动、比较和分类的主依据 | T1-VERIFIED |
| Layer 3: Ratified prerequisite | [F Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html) | D 明确“analogously”继承的 fcsr、舍入、flags、FMA、最小/最大、比较和分类细节 | T1-VERIFIED |
| Layer 3: Instruction listing | [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 交叉核对 RV32D/RV64D 的助记符、编码空间和 RV64-only 项 | T1-VERIFIED |
| Layer 3: Profile / ABI | [RVA23 profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html) / [RISC-V ABIs v1.0](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html) | 仅区分 ISA、profile 与硬件浮点 ABI 的不同承诺 | T1-VERIFIED |

证据标签的含义如下：

- **T1-VERIFIED**：可在 RISC-V ratified 规范、profile、ABI 或指定的一手来源中直接定位。
- **INTERPRETIVE**：对规范指令表的计数、归类或工程阅读结论；不增加 ISA 保证。
- **UNVERIFIED**：本文没有对具体核、编译器、操作系统或开发板做实测，不能将其行为说成已验证事实。

**SPEC-UPDATE-ALERT：** 本文固定用户指定的 `v20260120` 快照。此次检查时，官方 ratified specifications library 仍将 Unprivileged ISA 列为 `v20260120`；UnifiedDB 是从主线连续生成的资料入口，可能先出现更新。用于新项目评审前，应重新核对 [ratified library](https://docs.riscv.org/reference/home/index.html)、[D 章节](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html) 与 [UnifiedDB](https://riscv.github.io/riscv-unified-db/)。T1-VERIFIED.

### 2.2 范围边界

| 范围 | 本文处理方式 | 标签 |
| --- | --- | --- |
| F 与 D | D 的共同浮点状态和继承语义；重点解释 D 带来的双精度与 NaN boxing 变化 | T1-VERIFIED |
| RV32D | 说明其已有的双精度指令及 RV32 边界 | T1-VERIFIED |
| RV64D | 重点说明 `L/LU` 转换、`W` 结果符号扩展和 64-bit raw move | T1-VERIFIED |
| Q、H、Zfa | 只指出它们是独立扩展或更宽/更窄格式背景，不展开其指令语义 | T1-VERIFIED / UNVERIFIED |
| 特权态、OS、ABI | 只说明需要另查的接口边界，不从 D 正文推导上下文切换或工具链行为 | T1-VERIFIED / UNVERIFIED |
| Arm/x86 | 本文不做跨 ISA 语义、ABI 或性能映射 | UNVERIFIED |

本文关于寄存器宽度的主线是 F+D 且未实现 Q 的组合。D 页面同时说明，若再实现 Q，`FLEN` 会扩展至 128；届时 `.D` 也成为相对更宽 `f` 寄存器的窄值，应按 Q 的递归 NaN-boxing 规则另行分析。T1-VERIFIED: [D register-state note](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-1-d-register-state)；[Q Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html)

## 3. 从 F 到 D：先区分 XLEN、FLEN 与依赖关系

D 依赖 F，因此 F 的单精度指令和 `fcsr` 状态仍然存在。对 F+D、未实现 Q 的组合，D 的变化不是把整数寄存器“变成浮点寄存器”，而是把同一组浮点寄存器的最大宽度从 32 bit 提升到 64 bit。T1-VERIFIED: [D introduction and register state](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-d-extension-for-double-precision-floating-point-version-2-2)

| 配置 | XLEN | FLEN | `f0-f31` 可直接承载的标准值 | D 专属 raw move |
| --- | ---: | ---: | --- | --- |
| RV32F | 32 | 32 | `.S` | 无 |
| RV32D | 32 | 64 | `.S`（NaN-boxed）、`.D` | 无 `FMV.X.D` / `FMV.D.X` |
| RV64D | 64 | 64 | `.S`（NaN-boxed）、`.D` | 有 `FMV.X.D` / `FMV.D.X` |

这里的 RV32D 并不矛盾：它是 `XLEN=32`、`FLEN=64` 的组合。双精度数可在浮点寄存器和内存中传输、也可用 `.D` 指令计算；但标准 ISA 只在 `XLEN>=64` 时提供双精度与整数寄存器之间的完整 64-bit raw move。T1-VERIFIED: [D conversion and move instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)

`fcsr` 仍是 F 所定义的 32-bit 控制/状态 CSR，其中 `frm` 给出动态舍入模式，`fflags` 保存 NV、DZ、OF、UF、NX 五个累积异常标志。D 不新增“dcsr”或第二份双精度 flags；使用 `.D` 的舍入与异常仍进入该共同状态。T1-VERIFIED: [F fcsr](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#fcsr)；[D dependency on F](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-d-extension-for-double-precision-floating-point-version-2-2)

## 4. NaN boxing：D 中单精度值的寄存器表示

### 4.1 合法的 `.S` 值必须装箱

在 `FLEN=64` 的 D 实现中，32-bit 单精度值是一个相对较窄的值。合法的 `.S` 值位于寄存器低 32 bit，`f[63:32]` 必须全部为 1：

~~~text
63                             32 31                             0
+--------------------------------+--------------------------------+
|            all 1s              |     IEEE 754 single encoding   |
+--------------------------------+--------------------------------+
                 NaN-boxed .S value in an FLEN=64 f register
~~~

从较宽的 `.D` 位模式观察时，一个合法 boxed `.S` 看起来是负 quiet NaN；这不表示该 `.S` 数值被转换成 NaN，而是区分窄格式值的寄存器表示规则。任何写入窄结果的浮点操作都必须把上部 `FLEN-n` bit 写成 1。T1-VERIFIED: [NaN boxing](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)

### 4.2 传输操作与计算操作的边界

| 路径 | 对 `.S` 值的行为 | 例子 | 标签 |
| --- | --- | --- | --- |
| 窄值传入 `f` 寄存器 | 形成有效 NaN-boxed 值 | `FLW`、`FMV.W.X` | T1-VERIFIED |
| 窄值传出 `f` 寄存器 | 仅传输低 32 bit，忽略上部 32 bit | `FSW`、`FMV.X.W` | T1-VERIFIED |
| 非传输的 `.S` 浮点操作 | 检查 `f[63:32]` 是否全为 1；不是则把输入当作 32-bit canonical NaN | `FADD.S`、`FCVT.D.S` 的 `.S` 源 | T1-VERIFIED |
| `.D` 操作 | 使用完整 64-bit `.D` 操作数；不存在“相对 D 的 32-bit boxing 检查” | `FADD.D`、`FCVT.S.D` 的 `.D` 源 | T1-VERIFIED |

因此，`FSW` 或 `FMV.X.W` 会原样取低 32 bit，即使源寄存器不是合法 boxed `.S`；反之，`FADD.S` 这类非传输运算不能只忽略高半部。这个差异专门防止错误地把未装箱的窄值当作正常操作数。T1-VERIFIED: [narrow transfer in/out](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#norm:FP_transfer_instrs_narrow_transfer_in)；[invalid narrow input](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#norm:FP_nontransfer_instrs_improper_nan-boxed_input)

NaN boxing 不等于 NaN payload 传播策略。它规定窄值在较宽寄存器中的合法形状；计算产生 NaN 时，除某条指令另有规则外，F 的默认 canonical-NaN 规则仍适用。明确的 raw transfer 保留传输位；`FSGNJ.D` 则保留除其指定 sign-bit 选择以外的位，不会 canonicalize NaN。T1-VERIFIED: [F NaN generation](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-3-nan-generation-and-propagation)；[D computation](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-4-double-precision-floating-point-computational-instructions)

## 5. 共享的浮点控制、舍入与异常语义

D 的 `.D` 计算继承 F 的浮点控制模型。需要舍入的操作由指令 `rm` 选择静态模式，或由 `rm=111` 选择 `fcsr.frm` 动态模式；`fflags` 是 sticky 累积状态，不表示“最后一条指令”必然发生的异常。基础 F/D 浮点状态不因 flags 置位而自动产生浮点 trap；软件若需要观察或复位累计状态，须显式读取或写入相应 CSR。T1-VERIFIED: [F rounding modes and fflags](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#fcsr)；[D dependency](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-d-extension-for-double-precision-floating-point-version-2-2)

| 共同规则 | `.D` 中的含义 | 标签 |
| --- | --- | --- |
| 舍入 | `RNE`、`RTZ`、`RDN`、`RUP`、`RMM`，或由 `frm` 选取；保留 `rm`/`frm` 编码不能作为稳定软件接口 | T1-VERIFIED |
| 累积 flags | NV、DZ、OF、UF、NX 与 `.S` 共用同一 `fflags` | T1-VERIFIED |
| 次正规数 | 按 IEEE 754-2008 处理，tininess 在舍入后检测 | T1-VERIFIED |
| 默认 NaN | 除另有说明外，浮点操作产生 canonical NaN | T1-VERIFIED |
| 保留 payload 的路径 | `FLD/FSD` 与 `FMV.X.D`/`FMV.D.X` 保留传输位；`FSGNJ.D` 保留非 sign-bit 位且不 canonicalize NaN | T1-VERIFIED |

上述是程序员可见语义。写入动态舍入 CSR 是否让某个具体流水线串行化、NaN 检查放在译码还是执行阶段、次正规数是否走专用慢路径，都不是 D 规定的微架构接口。UNVERIFIED.

## 6. 双精度内存访问：FLD 与 FSD

`FLD` 从内存读取双精度值到浮点寄存器 `rd`，`FSD` 将浮点寄存器的双精度值写回内存。它们使用标准 `LOAD-FP` / `STORE-FP` 访存编码；在 D 实现中，一个读取的 64-bit 位模式也可以恰好是 boxed `.S` 值。T1-VERIFIED: [FLD/FSD](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fld_fsd)；[RV32/64G listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

| 属性 | `FLD` | `FSD` | 标签 |
| --- | --- | --- | --- |
| 数据方向 | memory -> `f[rd]` | `f[rs2]` -> memory | T1-VERIFIED |
| 传输宽度 | 64 bit | 64 bit | T1-VERIFIED |
| 非 canonical NaN payload | 原样进入寄存器 | 原样写回内存 | T1-VERIFIED |
| 原子执行保证 | 仅当有效地址自然对齐且 `XLEN>=64` | 同左 | T1-VERIFIED |

这条原子性条件有两个部分，不能省略任一部分。在 RV64D 中，自然对齐的 `FLD/FSD` 有该保证；D 正文没有对 RV32D 或非自然对齐情况给出同样保证。是否透明完成、产生何种 trap、如何连接总线或缓存，必须由 execution environment、特权架构和平台文档另行确认。T1-VERIFIED / UNVERIFIED: [D alignment rule](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#norm:fld_fsd_atomic_align)

## 7. 指令全景与编码视图

按基础助记符计数，不含伪指令：RV32D 有 26 条 D 格式相关指令；RV64D 在此基础上多出 4 条 L/LU 整数转换和 2 条双精度 raw move，共 32 条。计数是对官方指令表的整理，助记符存在性是 T1-VERIFIED，计数本身为 INTERPRETIVE。T1-VERIFIED: [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

| 指令族 | RV32D 与 RV64D 共同部分 | RV64D 增量 | 主要结果 |
| --- | --- | --- | --- |
| 访存 | `FLD`、`FSD` | 无 | 64-bit 原始浮点位模式 |
| 基本算术 | `FADD.D`、`FSUB.D`、`FMUL.D`、`FDIV.D`、`FSQRT.D` | 无 | 双精度浮点 |
| 最小/最大 | `FMIN.D`、`FMAX.D` | 无 | 双精度浮点 |
| FMA | `FMADD.D`、`FMSUB.D`、`FNMSUB.D`、`FNMADD.D` | 无 | 单次舍入的乘加 |
| 符号注入 | `FSGNJ.D`、`FSGNJN.D`、`FSGNJX.D` | 无 | 按位符号操作 |
| D/S 格式转换 | `FCVT.S.D`、`FCVT.D.S` | 无 | 单精度与双精度 |
| 整数转换 | `FCVT.W[U].D`、`FCVT.D.W[U]` | `FCVT.L[U].D`、`FCVT.D.L[U]` | 双精度与 32/64-bit 整数 |
| 位模式移动 | 无 | `FMV.X.D`、`FMV.D.X` | 不解释数值的 64-bit 搬运 |
| 比较 | `FEQ.D`、`FLT.D`、`FLE.D` | 无 | 整数 0/1 |
| 分类 | `FCLASS.D` | 无 | 整数 10-bit one-hot mask |

除融合乘加外，双精度算术、以 `.D` 为目的格式的转换、双精度移动、比较和分类使用 `OP-FP` major opcode，`fmt=01` 选择双精度格式；融合乘加使用含 `rs3` 的 R4 形式。`FCVT.S.D` / `FCVT.D.S` 的 `rs2` 字段编码**源格式**，`fmt` 编码**目的格式**，所以 `FCVT.S.D` 的 `fmt=00`、`rs2=D`，而 `FCVT.D.S` 相反；不要把其 `rs2` 当成第二个数值操作数。`FLD/FSD` 分别使用 `LOAD-FP` / `STORE-FP` 编码。T1-VERIFIED: [D conversion encoding](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)；[instruction listing](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

## 8. 双精度计算、最小/最大与 FMA

### 8.1 基本计算

| 指令 | 数学操作 | rm | 结果 |
| --- | --- | --- | --- |
| `FADD.D` | `rs1 + rs2` | 有 | `rd` |
| `FSUB.D` | `rs1 - rs2` | 有 | `rd` |
| `FMUL.D` | `rs1 x rs2` | 有 | `rd` |
| `FDIV.D` | `rs1 / rs2` | 有 | `rd` |
| `FSQRT.D` | `sqrt(rs1)` | 有 | `rd` |

D 把这些操作定义为对应单精度指令的双精度版本：操作数和结果是 `.D`，舍入与 flags 沿用共同的 F 浮点模型。规范没有为它们规定固定延迟、吞吐率、是否共享乘法器或是否用迭代除法。T1-VERIFIED / UNVERIFIED: [D computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-4-double-precision-floating-point-computational-instructions)

### 8.2 `FMIN.D` 与 `FMAX.D`

`FMIN.D` / `FMAX.D` 的规则来自对应的单精度操作，不能按整数位模式比较来替代：

1. 它们选择较小/较大的数；只对这两条指令，`-0.0` 小于 `+0.0`。
2. 两个输入都是 NaN 时，结果为 canonical NaN。
3. 仅一个输入为 NaN 时，结果为另一个非 NaN 操作数。
4. 任一 signaling NaN 都会置 NV，即使结果不是 NaN。

T1-VERIFIED: [D analogous computational semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-4-double-precision-floating-point-computational-instructions)；[FMIN/FMAX rules inherited from F](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

### 8.3 融合乘加

| 指令 | 精确数学形式 |
| --- | --- |
| `FMADD.D` | `(rs1 x rs2) + rs3` |
| `FMSUB.D` | `(rs1 x rs2) - rs3` |
| `FNMSUB.D` | `-(rs1 x rs2) + rs3` |
| `FNMADD.D` | `-(rs1 x rs2) - rs3` |

FMA 在最终结果处舍入，不把中间乘积先舍入为独立双精度值。`FNMSUB.D` 和 `FNMADD.D` 的负号作用于乘积，不能望文生义地理解为“对整个和取负”。这些行为是 D 对 FMA 单精度规则的双精度同构定义。T1-VERIFIED: [D computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-4-double-precision-floating-point-computational-instructions)；[F FMA semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

## 9. 符号注入与 RV64 专属 raw move

### 9.1 `FSGNJ.D` 家族

三个符号注入指令从 `rs1` 取得除 sign bit 之外的全部双精度位：

| 指令 | `rd` 的 sign bit | flags |
| --- | --- | --- |
| `FSGNJ.D` | `rs2` 的 sign bit | 不设置 |
| `FSGNJN.D` | `rs2` sign bit 的反相 | 不设置 |
| `FSGNJX.D` | `rs1` 和 `rs2` sign bit 的 XOR | 不设置 |

它们是按位符号操作，不会以普通算术方式 canonicalize NaN。令 `rs1=rs2` 时，汇编器可用 `FMV.D`、`FNEG.D`、`FABS.D` 这样的伪指令书写相应模式；这些伪指令与 RV64-only 的真实指令 `FMV.D.X` 不是同一件事。T1-VERIFIED: [D sign injection](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)；[F sign-injection semantics](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-7-single-precision-floating-point-conversion-and-move-instructions)

### 9.2 `FMV.X.D` 与 `FMV.D.X`

只有 `XLEN>=64` 才有双精度与整数寄存器之间的 raw move：

| 指令 | 方向 | 语义 |
| --- | --- | --- |
| `FMV.X.D rd, rs1` | `f` -> `x` | 将 `rs1` 的 64-bit IEEE 754 编码原样放入整数 `rd` |
| `FMV.D.X rd, rs1` | `x` -> `f` | 将整数 `rs1` 的 64-bit IEEE 754 编码原样写入浮点 `rd` |

两条指令不做数值转换，也不修改非 canonical NaN payload。它们不能在 RV32D 中使用；RV32D 也不以“分别移动上、下 32 bit”的旧式 D 寄存器指令作为标准替代。需要数值解释或舍入时应选择 `FCVT.*`，而不是 `FMV.*`。T1-VERIFIED: [D raw moves](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)

## 10. 双精度、单精度与整数之间的转换

### 10.1 整数转换

| 方向 | RV32D/RV64D 共同指令 | RV64D-only 指令 |
| --- | --- | --- |
| double -> signed integer | `FCVT.W.D` | `FCVT.L.D` |
| double -> unsigned integer | `FCVT.WU.D` | `FCVT.LU.D` |
| signed integer -> double | `FCVT.D.W` | `FCVT.D.L` |
| unsigned integer -> double | `FCVT.D.WU` | `FCVT.D.LU` |

`FCVT.<int>.D` 的有效输入范围和无效输入行为与 `FCVT.<int>.S` 相同：若舍入后结果不能在目标整数格式中表示，结果夹到最近边界并置 NV；不发生 NV 时，若舍入改变了值则置 NX。所有浮点/整数转换都按 `rm`，但 `FCVT.D.W[U]` 始终精确，数学上不受舍入模式影响。T1-VERIFIED: [D integer conversion rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)；[F conversion ranges and flags](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#int_conv)

RV64 的一个易错规则是：`FCVT.W[U].D` 先得到 32-bit 结果，再**符号扩展**到 64-bit 整数寄存器；这也包括无符号的 `WU` 形式。因此，不能因为 mnemonic 含 `U` 就假设寄存器高 32 bit 被零扩展。`FCVT.L[U].D` 与 `FCVT.D.L[U]` 则仅在 RV64 中存在。T1-VERIFIED: [RV64 conversion delta](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)

### 10.2 `FCVT.S.D` 与 `FCVT.D.S`

| 指令 | 方向 | 舍入 | 寄存器表示结果 |
| --- | --- | --- | --- |
| `FCVT.S.D` | double -> single | 按 `rm` | 低 32 bit 为 `.S` 结果，高 32 bit 为 1，形成合法 NaN-boxed `.S` |
| `FCVT.D.S` | single -> double | 永不舍入 | 完整 64-bit `.D` 结果 |

二者都使用浮点寄存器作为源和目的；`FCVT.D.S` 的 `.S` 源是窄格式的非传输操作数，因此必须通过 NaN-boxing 检查。即使 `FCVT.D.S` 数学上不舍入，指令的 `rm` 编码仍应使用合法值；软件宜编码 `RNE`。T1-VERIFIED: [D S/D conversions](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compute)；[F rounding-mode encoding rule](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#norm:roundingmode_rsv)；[D NaN boxing](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)

## 11. 比较与分类

### 11.1 `FEQ.D`、`FLT.D`、`FLE.D`

| 指令 | 条件 | 任一 NaN 时结果 | 任一 NaN 时 NV |
| --- | --- | --- | --- |
| `FEQ.D` | `rs1 == rs2` | 0 | 仅 signaling NaN |
| `FLT.D` | `rs1 < rs2` | 0 | 是 |
| `FLE.D` | `rs1 <= rs2` | 0 | 是 |

三条指令都向整数 `rd` 写 0 或 1。`FEQ.D` 是 quiet comparison，`FLT.D` / `FLE.D` 是 signaling comparison；“结果为 0”不能用来判断是否置 NV。T1-VERIFIED: [D compare instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-6-double-precision-floating-point-compare-instructions)；[F compare semantics inherited by D](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-8-single-precision-floating-point-compare-instructions)

### 11.2 `FCLASS.D`

`FCLASS.D` 将双精度值分类为写入整数 `rd` 的 10-bit one-hot mask；其余位清零，恰好一个 bit 为 1，且它不设置浮点异常标志。

| `rd` bit | 类别 |
| ---: | --- |
| 0 | `-infinity` |
| 1 | 负 normal |
| 2 | 负 subnormal |
| 3 | `-0` |
| 4 | `+0` |
| 5 | 正 subnormal |
| 6 | 正 normal |
| 7 | `+infinity` |
| 8 | signaling NaN |
| 9 | quiet NaN |

T1-VERIFIED: [D classify instruction](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#fl-compare)；[FCLASS semantics inherited by D](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-9-single-precision-floating-point-classify-instruction)

## 12. ISA、实现、profile 与软件边界

| 维度 | 本文可以确认的结论 | 不能从 D 正文直接推出的结论 | 标签 |
| --- | --- | --- | --- |
| 成熟度 | 用户指定的 `v20260120` ratified library 收录 D Extension 2.2 | 未来 UDB 主线或草案变化已自动纳入本文 | T1-VERIFIED / UNVERIFIED |
| ISA 语义 | D 依赖 F；在 F+D、未实现 Q 时 `FLEN=64`；并定义 NaN boxing、`.D` 指令、RV64-only 项和 raw-transfer 位保留 | 未列出的厂商扩展或非标准 NaN mode | T1-VERIFIED / UNVERIFIED |
| EEI / 平台 | `FLD/FSD` 仅在自然对齐且 `XLEN>=64` 时有原子性保证 | 某 SoC 的非对齐策略、总线拆分、缓存一致性与 trap 入口 | T1-VERIFIED / UNVERIFIED |
| 应用 profile | `RVA23U64` 把 F、D 列为 mandatory extension | 所有 RISC-V 核、微控制器或旧 profile 都要求 D | T1-VERIFIED / UNVERIFIED |
| ABI / 软件 | 硬件浮点 ABI 的 `ABI_FLEN` 不得宽于 ISA `FLEN`；`LP64D` 对应 `ABI_FLEN=64` | 某工具链版本是否默认生成 D、某系统是否用 LP64D、具体库是否可用 | T1-VERIFIED / UNVERIFIED |
| 微架构与跨 ISA | 规范允许内部实现细节不同，本文未做 Arm/x86 映射 | 延迟、吞吐、端口、寄存器重命名、跨 ISA 性能或 ABI 等价性 | UNVERIFIED |

这一区分尤其重要：`RVA23U64` 的 D 要求是**指定 profile**的承诺；`LP64D` 是特定硬件浮点调用约定；它们都不能反过来改变 D 指令本身的 ISA 语义。T1-VERIFIED: [RVA23U64 mandatory extensions](https://docs.riscv.org/reference/rva23/rva23-profiles.html)；[hardware floating-point calling convention](https://docs.riscv.org/reference/abi/v1.0/riscv-cc-procedure-calling-convention.html)

## 13. 常见误区

1. **“D 是单独的双精度 ISA。”** D 依赖 F，F 的状态、控制和单精度指令仍是组合的一部分。
2. **“RV64D 的 64 指的是浮点寄存器才有 64 bit。”** RV32D 同样有 `FLEN=64`；RV64D 的额外含义是 `XLEN=64`。
3. **“D 寄存器中的 `.S` 只需低 32 bit 正确。”** 非传输 `.S` 操作会检查上 32 bit 是否全 1。
4. **“未 boxed 的 `.S` 会按低 32 bit 正常计算。”** 这类操作数会被当作 canonical NaN；但窄值 transfer out 仍只取低位。
5. **“`FLD/FSD` 在所有 D 实现上天然原子。”** 规范保证需要自然对齐并且 `XLEN>=64`。
6. **“`FMV.X.D` 是 double 转整数。”** 它搬运位模式；有数值语义和舍入的是 `FCVT.*`。
7. **“`FCVT.WU.D` 在 RV64 总是零扩展。”** `FCVT.W[U].D` 的 32-bit 结果按规范符号扩展。
8. **“`FCVT.S.D` 与 `FCVT.D.S` 都可能舍入。”** 前者按 `rm` 窄化，后者永不舍入。
9. **“D 另有一套 flags / rounding CSR。”** D 使用 F 已定义的共同 `fcsr`、`frm` 和 `fflags`。

## 14. 面向实现与验证的检查清单

### 14.1 ISA 语义检查

- [ ] 在 F+D、未实现 Q 的配置中确认 `f0-f31` 为 64-bit 浮点状态，而非新增寄存器文件。
- [ ] 用合法 boxed `.S`、高 32 bit 非全 1 的 `.S`、qNaN 和 sNaN 覆盖 `.S` 操作与 `FCVT.D.S` 的输入检查。
- [ ] 验证 `FLW` / `FMV.W.X` 形成合法 boxing，`FSW` / `FMV.X.W` 只取低 32 bit。
- [ ] 验证 `FLD/FSD` 对 64-bit NaN payload 的原样传输，并将 RV64 自然对齐、RV32D、非对齐情形分开记录。
- [ ] 覆盖 `FADD.D`、`FDIV.D`、`FSQRT.D`、`FMIN.D` / `FMAX.D`、FMA 的舍入和 `fflags` 路径。
- [ ] 验证 `FCVT.S.D` 的窄化与 boxing、`FCVT.D.S` 的无舍入行为，以及 W/L/LU 的边界与 NV/NX。
- [ ] 在 RV64D 中验证 `FCVT.W[U].D` 的符号扩展、`FMV.X.D` / `FMV.D.X` 的完整 64-bit 位保留。
- [ ] 验证 `FEQ.D` 与 `FLT.D` / `FLE.D` 对 NaN 的不同 NV 行为，以及 `FCLASS.D` 的 one-hot mask 和无 flags 行为。

### 14.2 工具链、ABI 与版本检查

- [ ] 用目标 ISA 字符串、ELF 属性和反汇编确认构建目标是否真的包含 `D`，不要仅从源代码中的 `double` 推断。
- [ ] 需要硬件浮点调用约定时，确认 ABI 的 `ABI_FLEN` 与 ISA `FLEN` 的关系，并区分 `LP64D` 与仅整数调用约定。
- [ ] 对具体核单独记录 `.D` 除法、平方根、FMA 和动态 `frm` 写入的性能数据，并标为实测实现属性而非 ISA 规定。
- [ ] 下次评审前重新检查 [D 章节](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)、[F 章节](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)、[RV32/64G 指令表](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) 与 [ratified library](https://docs.riscv.org/reference/home/index.html)。

## 15. 小结

D 的核心是：在 F+D、未实现 Q 的组合中，将同一组 `f0-f31` 扩展为 `FLEN=64`，加入双精度 `.D` 指令，并为较窄 `.S` 值规定 NaN-boxing。最需要防止的错误是混淆 `XLEN` 与 `FLEN`、把未装箱 `.S` 当成普通单精度输入、把 `FLD/FSD` 的原子性条件扩大到 RV32D 或非对齐访问，以及把 `FMV.X.D` 的 raw move 当成 `FCVT` 数值转换。RV64D 的额外内容集中在 64-bit L/LU 转换和双精度 raw move；它们不改变 D 与 F 共享的 `fcsr`、舍入和累积 flags 契约；若同时有 Q，则要按 `FLEN=128` 的递归 boxing 规则重新看窄值表示。T1-VERIFIED.

## 16. 参考资料

1. [RISC-V Unprivileged ISA v20260120: D Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)
2. [RISC-V Unprivileged ISA v20260120: F Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)
3. [RISC-V Unprivileged ISA v20260120: RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)
4. [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)
5. [RISC-V UnifiedDB continuous deployment](https://riscv.github.io/riscv-unified-db/)
6. [RISC-V Normative Rules guidelines](https://github.com/riscv/docs-resources/blob/main/normative-rules.md)
