# RISC-V F 标准扩展：RV64F 单精度浮点指令解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA v20260120，F Extension Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)。本文先覆盖 RV32F 的共同语义，再补充 RV64F 的变化；同一规则在 RV64F 中有更具体表述时，以 RV64F 的 XLEN=64 语义为准。

## 1. 定位与结论

F 是 RISC-V 的单精度浮点标准扩展。它增加 IEEE 754-2008 单精度计算指令、32 个浮点寄存器和浮点控制/状态寄存器 fcsr；F 依赖 Zicsr 访问控制状态寄存器。F 不是双精度扩展，也不自动表示 D 或 Q 已实现。T1-VERIFIED: [F Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float)

面向 RV64 的关键结论如下：

| 问题 | RV64F 的准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 浮点寄存器宽度 | F-only 的 FLEN=32；f0-f31 各 32 bit，和 64-bit 整数寄存器文件分离 | RV64 等于浮点寄存器也有 64 bit | T1-VERIFIED |
| RV32F 继承 | FADD.S、FLW、FCVT.W.S 等共同指令在 RV64 仍存在，浮点操作数仍是单精度 | RV64F 只剩 RV64 新增指令 | T1-VERIFIED |
| RV64 增量 | 增加 FCVT.L[U].S、FCVT.S.L[U]；FCVT.W[U].S 的 32-bit 结果符号扩展到 XLEN | W 形式结果一定零扩展 | T1-VERIFIED |
| 位模式移动 | FMV.X.W 在 RV64 的高 32 bit 复制浮点数 sign bit；FMV.W.X 只取整数源低 32 bit | FMV.X.W 是数值转换或零扩展 | T1-VERIFIED |
| 异常处理 | NV、DZ、OF、UF、NX 是累积 sticky flags；基础 F 不因置位而产生浮点 trap | IEEE 异常一定立即陷入 | T1-VERIFIED |
| NaN | 算术结果默认使用正号 canonical NaN，单精度模式为 0x7fc00000；FLW/FSW 和 FMV 保留原始 payload | 所有路径都会 canonicalize NaN | T1-VERIFIED |

本文讨论 ISA 可观察契约，不把某条指令的延迟、流水线位置、浮点单元数量或 OS 的上下文切换策略当成 F 扩展保证。

## 2. 规范来源、版本与证据标签

### 2.1 版本锚点

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [F Extension 2.2 正文](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html) | 本文的寄存器、异常、表示、指令语义主依据 | T1-VERIFIED |
| [RISC-V ratified specifications library](https://docs.riscv.org/reference/home/index.html) | 确认用户指定的 Unprivileged ISA v20260120 版本入口 | T1-VERIFIED |
| [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 交叉核对 RV32F/RV64F 指令集合和 RV64-only 标注 | T1-VERIFIED |
| [D Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html) | 说明 F 与 D 合并时 FLEN/NaN boxing 的边界，不替代本文 F-only 语义 | T1-VERIFIED |

本文的证据标签含义：

- **T1-VERIFIED**：可以在上述 RISC-V ratified 规范正文中定位。
- **T2-CROSS-CHECKED**：由补充规范或官方工具/ABI资料交叉支持，但不覆盖 T1。
- **INTERPRETIVE**：把规范事实整理成记号、计数或工程阅读结论，不增加 ISA 保证。
- **UNVERIFIED**：没有本地实现、工具链或平台实测证据，不能写成已验证事实。

**SPEC-UPDATE-ALERT：** 本文固定用户指定的 v20260120 快照。用于新项目评审时，应重新检查 [ratified library](https://docs.riscv.org/reference/home/index.html) 与 [F 章节](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)，不要把连续部署文档或某个具体核的行为替代版本锚点。T1-VERIFIED。

### 2.2 范围边界

| 范围 | 本文处理方式 | 标签 |
| --- | --- | --- |
| RV32F | 作为 F 指令的共同基线，XLEN=32 | T1-VERIFIED |
| RV64F | 重点；按 XLEN=64 解释继承指令，并单列 L/LU 转换与符号扩展 | T1-VERIFIED |
| F+D/Q | 只说明与 F-only 的边界；双精度指令和 NaN boxing 细节见 D/Q 章节 | T1-VERIFIED |
| 特权态、OS、ABI | 仅指出需要另查的接口，不从 F 正文推导实现承诺 | UNVERIFIED |
| Arm/x86 对比 | 只保留规范正文对 FMA 命名的提示，不做跨 ISA 性能或 ABI 推论 | T1-VERIFIED / UNVERIFIED |

## 3. RV32F 到 RV64F：先固定 XLEN，再看 FLEN

F 章节用 XLEN 表示整数寄存器宽度，用 FLEN 表示浮点寄存器宽度。F-only 的 FLEN=32；因此 RV32F 是 XLEN=32、FLEN=32，而 RV64F 是 XLEN=64、FLEN=32。RV64 的“64”只改变整数寄存器和整数量转换的宽度，不把 F 的单精度寄存器改成双精度寄存器。T1-VERIFIED: [F register state](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-1-f-register-state)

| 层次 | RV32F | RV64F |
| --- | --- | --- |
| 整数寄存器 | XLEN=32 | XLEN=64 |
| 浮点寄存器 | f0-f31，各 32 bit | f0-f31，各 32 bit |
| 单精度计算 | 同一组 .S 指令 | 同一组 .S 指令，仍以 32-bit 浮点值计算 |
| W 整数转换 | 结果已经是 XLEN 宽度 | FCVT.W[U].S 结果符号扩展到 64 bit |
| L 整数转换 | 不存在 | FCVT.L[U].S、FCVT.S.L[U] 仅 RV64 |
| X/W 位移动 | FMV.X.W 产生 32-bit 整数值 | 低 32 bit 原样搬运，高 32 bit 复制源 sign bit |

如果同时实现 D，D 会把浮点寄存器的最大宽度提升到 FLEN=64，并对较窄的单精度值使用 NaN boxing 规则。那是 F+D 组合的寄存器表示约束；不能反过来套到 F-only 的 32-bit f 寄存器上。T1-VERIFIED: [D Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)

## 4. F 的程序员可见状态

### 4.1 浮点寄存器文件

F 增加 f0-f31 共 32 个浮点寄存器，每个寄存器宽度为 FLEN=32。普通浮点运算从该文件读取并写回，整数量转换和比较则直接读写整数寄存器，避免为了常见混合格式序列额外插入整数与浮点之间的搬运。规范允许实现内部对浮点格式重新编码，但没有规定外部可见的物理寄存器组织。T1-VERIFIED: [F register state](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-1-f-register-state)

### 4.2 fcsr 位域

fcsr 是 32-bit 可读写 CSR：

~~~text
31                         8 7       5 4             0
+---------------------------+---------+---------------+
| reserved                  |   frm   |    fflags     |
+---------------------------+---------+---------------+
~~~

| 字段 | 位 | 含义 | 标签 |
| --- | ---: | --- | --- |
| fflags | 4:0 | NV、DZ、OF、UF、NX 五个累积异常标志 | T1-VERIFIED |
| frm | 7:5 | 动态舍入模式；指令 rm=111 时被选中 | T1-VERIFIED |
| reserved | 31:8 | 未实现其他标准扩展时读为 0，写入应忽略；标准软件应保留 | T1-VERIFIED |

FRCSR/FSCSR 是访问整个 fcsr 的汇编器伪指令；FRRM/FSRM 只访问 frm；FRFLAGS/FSFLAGS 只访问 fflags。FS* 形式把旧值返回到 rd，再写入 rs1 的相应低位。T1-VERIFIED: [fcsr](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-2-floating-point-control-and-status-register)

### 4.3 舍入模式

需要舍入的浮点运算和转换由指令 rm 字段选择静态模式，rm=111 选择 fcsr.frm 的动态模式：

| 编码 | 名称 | 含义 |
| ---: | --- | --- |
| 000 | RNE | Round to Nearest, ties to Even |
| 001 | RTZ | Round towards Zero |
| 010 | RDN | Round Down，向负无穷 |
| 011 | RUP | Round Up，向正无穷 |
| 100 | RMM | Round to Nearest, ties to Max Magnitude |
| 101-110 | 保留 | 不可当作未来模式以外的稳定行为 |
| 111 | DYN | 在指令 rm 中选择 frm；在 frm 寄存器中本身是保留编码 |

依赖舍入的指令遇到保留静态或动态模式时，行为是 reserved；实现仍可对保留编码发出 illegal-instruction exception。数学上不受舍入影响的 widening conversion 也有 rm 字段，软件应编码 RNE。T1-VERIFIED: [rounding modes](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#norm:dyn_round_enc)

### 4.4 累积异常标志

| 标志 | 名称 | 阅读方式 |
| --- | --- | --- |
| NV | Invalid Operation | 无效操作 |
| DZ | Divide by Zero | 除零 |
| OF | Overflow | 上溢 |
| UF | Underflow | 下溢 |
| NX | Inexact | 舍入后不精确 |

这些位记录自软件上次清除以来任何浮点算术指令产生过的条件，因此读取到 1 不能直接证明“最后一条指令”产生了异常。基础 RISC-V F 不支持因设置标志而产生浮点 trap，软件必须显式读/清 fflags。T1-VERIFIED: [exception flags](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#norm:fcsr-fflags_op)

## 5. 数值表示、NaN 与次正规数

### 5.1 IEEE 单精度与 canonical NaN

F 的单精度计算遵循 IEEE 754-2008。除非某条指令另有说明，浮点运算产生 NaN 时返回 canonical NaN：正号、fraction 仅 quiet bit 置位；单精度位模式为 0x7fc00000。实现可以提供非标准 NaN payload 传播模式，但 canonical 结果必须可用且应是默认模式。T1-VERIFIED: [NaN generation](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-3-nan-generation-and-propagation)

必须区分两类路径：

| 路径 | 对 NaN payload 的处理 |
| --- | --- |
| 算术、产生新结果的操作 | 按该指令规则，通常 canonicalize |
| FLW、FSW、FMV.W.X、FMV.X.W、FSGNJ* | 只搬运/选择位，不修改非 canonical payload |

上表中的“通常”不替代具体指令规则；例如 FMIN/FMAX 在单 NaN 输入时返回另一个非 NaN 操作数。T1-VERIFIED: [F load/store and move](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-5-single-precision-load-and-store-instructions)

### 5.2 次正规数

次正规数按 IEEE 754-2008 处理，tininess 在舍入之后检测。F 没有把 flush-to-zero 作为基础架构行为；若某个实现提供非标准模式，不能当作 F 的可移植语义。T1-VERIFIED: [subnormal arithmetic](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-4-subnormal-arithmetic)

## 6. 浮点内存访问

FLW 和 FSW 使用基础整数 ISA 的 base+offset 地址形式：rs1 提供基址，带 12-bit signed byte offset。FLW 从内存读 32 bit 到浮点寄存器 rd，FSW 把浮点寄存器 rs2 的 32 bit 写回内存。两条指令不改写传输位，非 canonical NaN payload 也会保留。T1-VERIFIED: [FLW/FSW](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-5-single-precision-load-and-store-instructions)

只有 effective address 自然对齐时，FLW/FSW 才保证原子执行。非对齐浮点访问是 execution environment 的责任：可以透明处理，也可以产生 contained 或 fatal trap。F 正文不承诺某个缓存、总线或具体 LSU 的处理路径。T1-VERIFIED / UNVERIFIED。

## 7. 指令全景与编码视图

下面按基本指令助记符统计，不含伪指令和旧的 .S.X/.X.S 别名：RV32F 有 26 条，RV64F 在此基础上增加 4 条 L/LU 转换，共 30 条。这个计数是对规范指令清单的整理。INTERPRETIVE: [F Extension](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)

| 指令族 | RV32F 与 RV64F 共同部分 | RV64F 增量 | 主要结果 |
| --- | --- | --- | --- |
| 访存 | FLW、FSW | 无 | 32-bit 原始浮点位 |
| 基本算术 | FADD.S、FSUB.S、FMUL.S、FDIV.S、FSQRT.S | 无 | 单精度浮点 |
| 最小/最大 | FMIN.S、FMAX.S | 无 | 单精度浮点 |
| FMA | FMADD.S、FMSUB.S、FNMSUB.S、FNMADD.S | 无 | 单次舍入的乘加 |
| 符号注入 | FSGNJ.S、FSGNJN.S、FSGNJX.S | 无 | 按位符号操作 |
| 整数转换 | FCVT.W.S、FCVT.WU.S、FCVT.S.W、FCVT.S.WU | FCVT.L.S、FCVT.LU.S、FCVT.S.L、FCVT.S.LU | 浮点与 32/64-bit 整数 |
| 位模式移动 | FMV.X.W、FMV.W.X | 无 | 不解释数值的 32-bit 搬运 |
| 比较 | FEQ.S、FLT.S、FLE.S | 无 | 写整数 0/1 |
| 分类 | FCLASS.S | 无 | 写整数 10-bit one-hot mask |

F 的计算类指令使用 OP-FP major opcode；F-only 的 fmt 字段固定为 S=00。FMA 使用 R4 格式，额外编码 rs3；其余一/二源计算通常使用 R-type 形状。FLW/FSW 使用带 12-bit 偏移的加载/存储格式。T1-VERIFIED: [computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

常用主 opcode 视图如下；具体 funct7、rm 和 rs2 组合应以官方指令表为准：

| 编码类别 | opcode | 典型指令 | 格式 |
| --- | --- | --- | --- |
| LOAD-FP | 0000111 | FLW（funct3=010） | I-type |
| STORE-FP | 0100111 | FSW（funct3=010） | S-type |
| OP-FP | 1010011 | FADD.S、FCVT、FEQ.S、FCLASS.S 等 | R-type |
| FMADD | 1000011 | FMADD.S | R4-type |
| FMSUB | 1000111 | FMSUB.S | R4-type |
| FNMSUB | 1001011 | FNMSUB.S | R4-type |
| FNMADD | 1001111 | FNMADD.S | R4-type |

T1-VERIFIED: [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

## 8. 基本计算与最小/最大

### 8.1 五条基本算术

| 指令 | 数学操作 | rm | 结果 |
| --- | --- | --- | --- |
| FADD.S | rs1 + rs2 | 有 | rd |
| FSUB.S | rs1 - rs2 | 有 | rd |
| FMUL.S | rs1 x rs2 | 有 | rd |
| FDIV.S | rs1 / rs2 | 有 | rd |
| FSQRT.S | sqrt(rs1) | 有 | rd |

这些指令在单精度格式上执行 IEEE 754 运算，并根据 rm 或 frm 舍入；对应异常条件更新 fflags。规范没有为它们规定固定延迟、吞吐率、是否共享乘法器或是否旁路。T1-VERIFIED / UNVERIFIED: [F computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

### 8.2 FMIN.S 与 FMAX.S

FMIN.S/FMAX.S 不是把普通整数比较直接套到浮点位模式上：

1. 分别选择较小/较大数；仅对这两条指令，-0.0 小于 +0.0。
2. 两个输入都是 NaN 时返回 canonical NaN。
3. 只有一个输入是 NaN 时返回非 NaN 操作数。
4. 任一 signaling NaN 都置 NV，即使最终返回的是非 NaN。

这些规则是 v2.2 页面明确给出的 minimum-number/maximum-number 行为；不要把它们写成“NaN 总是传播”。T1-VERIFIED: [FMIN/FMAX](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

## 9. 融合乘加（FMA）

四条 FMA 指令把乘法和加/减组合在一个浮点结果中：

| 指令 | 精确数学形式 |
| --- | --- |
| FMADD.S | (rs1 x rs2) + rs3 |
| FMSUB.S | (rs1 x rs2) - rs3 |
| FNMSUB.S | -(rs1 x rs2) + rs3 |
| FNMADD.S | -(rs1 x rs2) - rs3 |

FMA 的结果在最终结果处舍入，不把中间乘积舍入成一次独立的单精度结果；四条指令都带 rm。若两个乘数分别为无穷和零，即使加数是 quiet NaN，也必须置 NV。T1-VERIFIED: [FMA](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#single-float-compute)

FNMSUB 和 FNMADD 的命名容易按直觉读反：RISC-V 定义的是“先取乘积的负号，再加/减 rs3”，而不是对整个和取负。规范还指出其有符号零行为与 x86/ARM 的相应 FMA 语义有关；这只是命名说明，不构成跨 ISA ABI 或性能结论。T1-VERIFIED / UNVERIFIED。

## 10. 符号注入与位模式移动

### 10.1 FSGNJ.S 家族

三条指令都从 rs1 取除 sign bit 外的全部位：

| 指令 | rd 的 sign bit | 是否置浮点异常标志 |
| --- | --- | --- |
| FSGNJ.S | 取 rs2 的 sign bit | 否 |
| FSGNJN.S | 取 rs2 sign bit 的反相 | 否 |
| FSGNJX.S | rs1 sign bit XOR rs2 sign bit | 否 |

它们不 canonicalize NaN。rs1=rs2 时，汇编器可把 FSGNJ.S/FSGNJN.S/FSGNJX.S 写成 FMV.S/FNEG.S/FABS.S 伪指令；这些是命名便利，不是额外硬件指令。T1-VERIFIED: [sign injection](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-7-single-precision-floating-point-conversion-and-move-instructions)

### 10.2 FMV.X.W 与 FMV.W.X

FMV.X.W 把 f 寄存器的 IEEE 754 32-bit 编码原样放入整数 rd 的低 32 bit；RV64 时 rd[63:32] 复制该浮点编码的 sign bit。FMV.W.X 从整数 rs1 的低 32 bit 原样写入浮点 rd。两者都保留非 canonical NaN payload，不执行数值转换。T1-VERIFIED: [FMV.X.W/FMV.W.X](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-7-single-precision-floating-point-conversion-and-move-instructions)

旧工具可能继续接受 FMV.X.S/FMV.S.X 名称；规范正文采用 W 名称，因为它们搬运的是 32 bit 位模式而不是“解释成单精度后转换”。是否接受旧别名属于工具链行为，需由具体版本验证。T1-VERIFIED / UNVERIFIED。

## 11. 浮点与整数之间的转换

### 11.1 指令集合

| 方向 | RV32F/RV64F 共同指令 | RV64-only 指令 |
| --- | --- | --- |
| single -> signed integer | FCVT.W.S | FCVT.L.S |
| single -> unsigned integer | FCVT.WU.S | FCVT.LU.S |
| signed integer -> single | FCVT.S.W | FCVT.S.L |
| unsigned integer -> single | FCVT.S.WU | FCVT.S.LU |

所有转换都按 rm 字段舍入。单精度到整数时，若舍入结果不可表示，设置 NV 并裁剪到目标格式的最近边界；只有舍入后值改变且没有设置 NV 时，才设置 NX。T1-VERIFIED: [conversion instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-7-single-precision-floating-point-conversion-and-move-instructions)

### 11.2 RV64 的 W/L 边界

| 转换 | 舍入后有效输入范围 | 负向越界、-inf | 正向越界、+inf、NaN |
| --- | --- | --- | --- |
| FCVT.W.S | -2^31 到 2^31-1 | -2^31 | 2^31-1 |
| FCVT.WU.S | 0 到 2^32-1 | 0 | 2^32-1 |
| FCVT.L.S | -2^63 到 2^63-1 | -2^63 | 2^63-1 |
| FCVT.LU.S | 0 到 2^64-1 | 0 | 2^64-1 |

RV64 中 FCVT.W[U].S 先得到 32-bit 结果，再符号扩展到整数寄存器宽度；这条规则也适用于无符号 W 转换的目的寄存器表示。FCVT.L[U].S 和 FCVT.S.L[U] 在 RV32 中不存在。T1-VERIFIED: [integer conversion table](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#int_conv)

用 FCVT.S.W rd, x0 可以得到正零，而且该初始化不会设置任何异常标志。T1-VERIFIED: [FCVT rounding](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#norm:fcvt_round)

## 12. 比较与分类

### 12.1 FEQ.S、FLT.S、FLE.S

| 指令 | 条件 | NaN 时结果 | NaN 时 NV |
| --- | --- | --- | --- |
| FEQ.S | rs1 == rs2 | 0 | 仅 signaling NaN |
| FLT.S | rs1 < rs2 | 0 | 任一 NaN |
| FLE.S | rs1 <= rs2 | 0 | 任一 NaN |

三条指令都把整数 0 或 1 写入 rd。这里 FEQ.S 是 quiet comparison，而 FLT.S/FLE.S 是 signaling comparison；不能只看“比较结果为 0”来推断是否设置 NV。T1-VERIFIED: [compare instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-8-single-precision-floating-point-compare-instructions)

### 12.2 FCLASS.S

FCLASS.S 写入整数 rd 的 10-bit one-hot mask，其他位清零，且绝不设置浮点异常标志：

| rd bit | 类别 |
| ---: | --- |
| 0 | -infinity |
| 1 | 负 normal |
| 2 | 负 subnormal |
| 3 | -0 |
| 4 | +0 |
| 5 | 正 subnormal |
| 6 | 正 normal |
| 7 | +infinity |
| 8 | signaling NaN |
| 9 | quiet NaN |

恰好一个 bit 会置 1，因此 FCLASS.S 是区分 -0/+0、次正规数、sNaN/qNaN 而不污染 fflags 的直接接口。T1-VERIFIED: [FCLASS.S](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-9-single-precision-floating-point-classify-instruction)

## 13. 六维映射与实现边界

| Feature | RISC-V status | Arm/x86 analogue | Platform | Software | Tag |
| --- | --- | --- | --- | --- | --- |
| 单精度算术与 FMA | F 2.2，IEEE 754-2008 单精度；FMA 采用 R4 编码 | F 页面仅说明相关 FMA 语义，不据此推导编码等价 | 需要实现 F 浮点状态与 Zicsr 访问 | 编译器是否发出 .S/FMA 由目标 ISA 和 ABI 决定 | T1-VERIFIED / UNVERIFIED |
| fcsr/fflags/frm | 32-bit CSR；flags 累积且基础 F 不因置位 trap | 不做跨 ISA 异常模型等价推论 | OS/特权保存策略不由 F 章定义 | 软件显式读写/清除 flags | T1-VERIFIED / UNVERIFIED |
| FLW/FSW | 32-bit 原始位传输；自然对齐才有原子保证 | 不做跨 ISA 访存原子性等价推论 | 非对齐行为由 EEI 决定 | 地址对齐与异常处理由平台/运行时确认 | T1-VERIFIED / UNVERIFIED |
| FCVT.W/L 与 FCVT.S.W/L | RV64 增加 L/LU；W 结果在 XLEN>32 时符号扩展 | 不把 x86/Arm 转换指令名直接映射为 RISC-V 语义 | 需要正确实现 NV/NX 边界结果 | 编译器整数/浮点转换需按 -march/-mabi 验证 | T1-VERIFIED / UNVERIFIED |
| NaN 与位搬运 | 算术默认 canonical NaN；FMV/FSGNJ/FLW/FSW 保留位模式 | 不据此声称 payload 传播策略相同 | F-only FLEN=32；F+D 才进入 NaN boxing | 测试应分别覆盖算术 NaN 与 raw move | T1-VERIFIED |
| FCLASS/compare | 10-bit 分类；FEQ quiet，FLT/FLE signaling | 不做跨 ISA compare exception 等价推论 | 不依赖浮点 trap 入口 | 软件可用 FCLASS 区分零、次正规数和 NaN 类别 | T1-VERIFIED / UNVERIFIED |

下表把规范事实与实现未知量分开：

| 维度 | 本文可确认的结论 | 尚不能由 F 正文推出的结论 | 标签 |
| --- | --- | --- | --- |
| ISA | 单精度指令、f0-f31、FLEN=32、fcsr、舍入和 flags 语义 | 某个未列出的指令或非标准 NaN 模式 | T1-VERIFIED |
| 微架构 | 允许分离寄存器文件、内部 recoding；fcsr 写入在典型实现中可能导致流水线串行化 | 延迟、吞吐、端口、旁路、是否迭代除法 | T1-VERIFIED / UNVERIFIED |
| 平台/EEI | 自然对齐 FLW/FSW 才有原子保证；非对齐行为由 EEI 决定 | 某 SoC 的总线拆分、缓存一致性或异常入口 | T1-VERIFIED / UNVERIFIED |
| 特权/OS | F 依赖 Zicsr；F 本身不定义 OS 保存/恢复策略 | FS 状态位、懒保存、上下文切换成本 | T1-VERIFIED / UNVERIFIED |
| 软件/ABI | 转换和比较直接使用整数寄存器，调用约定需另查 psABI | 某 GCC/LLVM 版本默认是否发出 F 指令、寄存器分配和性能 | T1-VERIFIED / UNVERIFIED |
| Arm/x86 对照 | F 正文只在 FMA 命名注释中提到相应语义 | 跨 ISA 编码、异常模型等价性和性能等价性 | T1-VERIFIED / UNVERIFIED |

## 14. 常见误区

1. **把 RV64F 当成 FLEN=64。** F-only 仍是 FLEN=32；只有 D 等扩展改变浮点寄存器最大宽度。
2. **把 FCVT.WU.S 的 RV64 结果当作零扩展。** 规范要求 FCVT.W[U].S 的 32-bit 结果符号扩展到 XLEN。
3. **把 FMV.X.W 当成浮点到整数转换。** 它是位模式搬运，RV64 高半复制 sign bit。
4. **认为任何 NaN 都会传播 payload。** 算术默认 canonicalize；FLW/FSW、FMV、FSGNJ* 才明确保留位模式。
5. **用普通比较推导 FMIN/FMAX。** -0/+0 和单 NaN 输入有专门规则，sNaN 还会设置 NV。
6. **认为 FEQ.S、FLT.S、FLE.S 对 NaN 的 flag 行为相同。** FEQ.S 只对 sNaN 置 NV，后两者对任一 NaN 置 NV。
7. **把 rm=111 和 frm=111 混为一谈。** 指令 rm=111 表示选择动态模式；frm=111 本身是保留值。
8. **以为 fflags 置位会自动 trap。** 基础 F 要求软件显式检查，不能依赖隐式异常入口。
9. **把 FSGNJ.S 当成数值正负号运算。** 它按位注入 sign bit，不 canonicalize NaN，也不设置 flags。

## 15. 面向实现/验证的检查清单

### 15.1 ISA 语义检查

- [ ] 在 RV64F 配置中确认 f0-f31 为 32-bit 浮点状态，且整数与浮点寄存器编号空间分离。
- [ ] 覆盖 RNE、RTZ、RDN、RUP、RMM，以及 rm=111 读取 frm 的路径；确认保留模式没有被当成稳定 ABI。
- [ ] 检查 NV、DZ、OF、UF、NX 的 sticky 行为、软件清除和无 trap 语义。
- [ ] 用 canonical NaN、sNaN、qNaN、-0、+0、次正规数覆盖 FMIN/FMAX、比较、FCLASS 和 FSGNJ*。
- [ ] 验证 FLW/FSW 与 FMV 的 payload 原样传输；分别测试自然对齐和非对齐地址的 EEI 行为。
- [ ] 验证 FCVT.W[U].S 的 RV64 符号扩展、FCVT.L[U].S 的边界裁剪、NX/NV 优先关系。
- [ ] 验证 FMV.X.W 的高 32 bit 等于浮点 sign bit，而不是零。
- [ ] 若同时实现 D，另外检查单精度 NaN boxing；不要用 F-only 的 32-bit 假设替代 D 章节。

### 15.2 文档与工具链检查

- [ ] 用目标 ISA 字符串、ELF 属性和反汇编确认工具是否选择 F、D、Zicsr 及 RV64-only 转换。
- [ ] 用目标 ABI 文档确认浮点参数/返回值约定；F 规范正文不等于 psABI。
- [ ] 对具体核记录除法、平方根、FMA 的延迟/吞吐和 fcsr 写入代价，但将这些数据标为实现实测而不是 T1。

## 16. 小结

对 64-bit 目标，F 的核心不是“把所有浮点寄存器变成 64 bit”，而是以 XLEN=64 的整数环境承载 FLEN=32 的 IEEE 单精度浮点状态。RV64F 继承 RV32F 的 .S 运算和访存，额外提供 64-bit 整数转换，并把 W 转换结果符号扩展到 XLEN。实现和软件最容易出错的边界集中在 fcsr 的动态舍入/累积 flags、NaN canonicalization 与原始位搬运、FMIN/FMAX 的 NaN/零规则，以及 F-only 与 F+D 的寄存器表示差异。T1-VERIFIED。

## 17. 参考资料

1. [RISC-V Unprivileged ISA v20260120：F Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)
2. [RISC-V Unprivileged ISA v20260120：D Extension 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)
3. [RISC-V Unprivileged ISA v20260120：RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)
4. [RISC-V ratified specifications library](https://docs.riscv.org/reference/home/index.html)
