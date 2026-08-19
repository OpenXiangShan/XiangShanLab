# RISC-V M 标准扩展：RV64M 整数乘法与除法（`XLEN=64`）解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA `v20260120`，M Extension Version 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)。本文面向 `RV64`，先读 RV32M 的八条通用 `XLEN` 指令，再读 RV64M 增加的五条 `*W` 指令；凡是 RV64M 给出更具体规则处，以 RV64M 的规则为准。

## 1. 定位与结论

`M` 是标准的整数乘法和除法扩展，操作数来自两个整数寄存器。它不属于裸 `I` 基础 ISA：一个 `RV64I` 实现可以没有硬件整数乘除，而 `RV64IM` 才明确包含本章的能力。`M` 的设计意图之一是让低成本实现或可由附加加速器处理乘除的场景不必把乘除硬件放入基础 ISA。T1-VERIFIED: [M Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

对 RV64 的阅读结论如下：

| 问题 | RV64M 的准确结论 | 容易误解成 | 标签 |
| --- | --- | --- | --- |
| 普通 `M` 指令 | `MUL`、`MULH`、`MULHSU`、`MULHU`、`DIV`、`DIVU`、`REM`、`REMU` 仍存在，并以 `XLEN=64` 运算 | 它们仍是 32-bit 运算，或被 `*W` 指令替换 | T1-VERIFIED |
| RV64M 增量 | 新增 `MULW`、`DIVW`、`DIVUW`、`REMW`、`REMUW`；它们取输入低 32 bit，结果符号扩展到 64 bit | `*W` 结果总是零扩展 | T1-VERIFIED |
| 乘法结果 | `MUL` 给低 64 bit；三条 `MULH*` 给完整 128-bit 乘积的高 64 bit，符号组合不同 | `MULH` 是 `MUL` 的“更高精度版本”或四者可互换 | T1-VERIFIED |
| 除零 | 不产生本章定义的整数除零异常；商为全 1，余数为被除数 | 必然 trap，或结果为 0 | T1-VERIFIED |
| 有符号除法溢出 | 唯一情形是最小有符号数除以 `-1`；商为原被除数，余数为 0 | 与除零相同，或无符号除法也会溢出 | T1-VERIFIED |

`M` 蕴含 `Zmmul`；后者仅保留乘法子集，即 `MUL`、`MULH`、`MULHSU`、`MULHU` 和 RV64 专有的 `MULW`，不提供本章的除法/余数指令。本文只解释完整 `M`；`Zmmul` 的教学页应单独讨论其部署边界。T1-VERIFIED: [M Extension 2.0, Zmmul](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

### 1.1 术语与记号

| 记号 | 本文含义 | 标签 |
| --- | --- | --- |
| `XLEN` | 整数寄存器宽度；本篇目标 RV64，因此 `XLEN=64` | T1-VERIFIED |
| `L` | 除法边界表中的操作宽度：普通 `DIV[U]`/`REM[U]` 为 `XLEN=64`，`*W` 形式为 32 | T1-VERIFIED |
| `low_N(x)` | `x` 的低 `N` bit；这是本文为解释位宽引入的记号 | INTERPRETIVE |
| `sext32(x)` | 把 32-bit `x` 的 bit 31 复制到 bit 63:32 后得到的 64-bit 值 | INTERPRETIVE |
| word / `*W` | 32-bit 子宽度运算；不是“64-bit 寄存器中的无符号 word” | T1-VERIFIED |
| `rd`、`rs1`、`rs2` | 目的整数寄存器和两个源整数寄存器 | T1-VERIFIED |

## 2. 证据、版本与范围边界

### 2.1 证据优先级

| 层级 | 本次来源 | 用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1：UDB | [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 机器可读/生成文档的导航与语义交叉检查；连续部署产物不是本篇固定版本的替代物 | T1-VERIFIED |
| Layer 2：Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 解释页面中 `norm:` 标注的规范性约束；不以其替代指令正文 | T1-VERIFIED |
| Layer 3：Ratified ISA | [M Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)、[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)、[RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 本篇的指令语义、RV64 位宽与编码主依据 | T1-VERIFIED |
| Layer 3：命名 | [ISA Extension Naming](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) | `M`、`G` 与 `RV64IM` 等 ISA 名称的边界 | T1-VERIFIED |

### 2.2 标签

- **T1-VERIFIED**：可直接在本节列出的 RISC-V 一级规范中定位。
- **INTERPRETIVE**：由规范事实整理出的阅读记号、例子或工程结论；不增加 ISA 保证。
- **UNVERIFIED**：本地处理器、汇编器、编译器和性能数据尚未实测，不能从 ISA 正文推出。

### 2.3 版本锚点

官方 ratified specifications library 当前把 Unprivileged ISA 列为 `v20260120`（2026 年 1 月），其中 M 章节标题为 **Version 2.0**。本文固定到用户给出的该版本，不以 UDB 连续部署或 ISA Manual 主线快照覆盖其语义。T1-VERIFIED: [Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)；[M Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

**SPEC-UPDATE-ALERT：** 在将本文用于新项目评审前，应重新核验 [ratified library](https://docs.riscv.org/reference/home/index.html)、[M chapter](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html) 和 [ISA Manual releases](https://github.com/riscv/riscv-isa-manual/releases)。本篇不据此断言某个具体核、Linux 发行版或工具链已经实现 `M`。T1-VERIFIED / UNVERIFIED。

## 3. 为什么 RV32M 与 RV64M 必须合读

M 章节首先以 `XLEN` 定义八条乘除指令的共同语义；因此它们在 RV32M 中用 32-bit `XLEN`，在 RV64M 中用 64-bit `XLEN`。RV64M 不是另一套只含 word 运算的扩展，而是在这些普通指令之上增加五条以低 32 bit 运算的 `*W` 指令。RV64I 规定 RV64 的 `XLEN=64`，而 RV32/64G 指令列表把该五条列为“RV64M Standard Extension (in addition to RV32M)”。T1-VERIFIED: [M Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)；[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)；[RV32/64G listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

| 层次 | RV32M 语义在 RV64 中的取值 | RV64M 的增量/更具体规则 | 标签 |
| --- | --- | --- | --- |
| 普通乘法 | 乘数、乘积片段与写回宽度为 `XLEN=64` | 无 `W` 的 `MUL*` 都处理完整 64-bit GPR 值 | T1-VERIFIED |
| 普通除法/余数 | `DIV[U]`、`REM[U]` 的 `L=XLEN=64` | 除零/有符号溢出规则仍适用，但结果宽度为 64 | T1-VERIFIED |
| word 乘法 | RV32M 没有额外 `MULW` mnemonic | `MULW` 对两个源的低 32 bit 相乘，低 32-bit 结果符号扩展 | T1-VERIFIED |
| word 除法/余数 | RV32M 没有额外 `DIV*W`/`REM*W` mnemonic | 四条 `*W` 指令按有/无符号解释低 32 bit，所有结果符号扩展 | T1-VERIFIED |

因此，阅读 RV64M 时不应把 `DIVU` 与 `DIVUW`、或 `REMU` 与 `REMUW` 当作“同宽的 unsigned / word 别名”：前者是 64-bit 无符号操作，后者只看低 32 bit，且最终仍是符号扩展的 64-bit GPR 结果。T1-VERIFIED: [M Extension 2.0, Division Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

## 4. 指令全景与编码

### 4.1 RV64M 的 13 条指令

| 指令族 | 指令 | RV64 下的输入宽度 | 写回到 `rd` 的内容 | 标签 |
| --- | --- | ---: | --- | --- |
| 低半乘法 | `MUL` | 64 x 64 | 完整 128-bit 乘积的低 64 bit | T1-VERIFIED |
| 高半乘法 | `MULH` | 64 x 64，signed x signed | 完整乘积的高 64 bit | T1-VERIFIED |
| 高半乘法 | `MULHSU` | 64 x 64，`rs1` signed x `rs2` unsigned | 完整乘积的高 64 bit | T1-VERIFIED |
| 高半乘法 | `MULHU` | 64 x 64，unsigned x unsigned | 完整乘积的高 64 bit | T1-VERIFIED |
| 除法 | `DIV` / `DIVU` | 64 / 64 | 有符号 / 无符号商 | T1-VERIFIED |
| 余数 | `REM` / `REMU` | 64 / 64 | 对应有符号 / 无符号余数 | T1-VERIFIED |
| word 乘法 | `MULW` | 低 32 x 低 32 | `sext32(low_32(product))` | T1-VERIFIED / INTERPRETIVE |
| word 除法 | `DIVW` / `DIVUW` | 低 32 / 低 32 | 32-bit 有符号 / 无符号商，再符号扩展 | T1-VERIFIED |
| word 余数 | `REMW` / `REMUW` | 低 32 / 低 32 | 32-bit 有符号 / 无符号余数，再符号扩展 | T1-VERIFIED |

前八条与 RV32M 共用 `OP` 格式：`opcode=0110011`、`funct7=0000001`；五条 RV64M word 指令使用 `OP-32`：`opcode=0111011`、`funct7=0000001`。两组中 `funct3=000` 对应 `MUL`/`MULW`，`001/010/011` 仅在普通 `OP` 组中对应三条高半乘法，`100/101/110/111` 对应 `DIV`、`DIVU`、`REM`、`REMU` 或相应的 `*W` 形式。T1-VERIFIED: [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

| `funct3` | `OP` (`0110011`) | `OP-32` (`0111011`) | 标签 |
| --- | --- | --- | --- |
| `000` | `MUL` | `MULW` | T1-VERIFIED |
| `001` | `MULH` | 无 M 指令 | T1-VERIFIED |
| `010` | `MULHSU` | 无 M 指令 | T1-VERIFIED |
| `011` | `MULHU` | 无 M 指令 | T1-VERIFIED |
| `100` | `DIV` | `DIVW` | T1-VERIFIED |
| `101` | `DIVU` | `DIVUW` | T1-VERIFIED |
| `110` | `REM` | `REMW` | T1-VERIFIED |
| `111` | `REMU` | `REMUW` | T1-VERIFIED |

## 5. 乘法：低半、高半与 signedness

`MUL` 对 `rs1` 和 `rs2` 做 `XLEN x XLEN` 乘法，并把完整 `2 x XLEN` 乘积的低 `XLEN` bit 写入 `rd`；在 RV64M 中即低 64 bit。`MULH`、`MULHU` 与 `MULHSU` 做同一宽度的乘法，但写回高 64 bit，三者分别把两个源解释为 signed x signed、unsigned x unsigned、以及 `rs1` signed x `rs2` unsigned。T1-VERIFIED: [M Extension 2.0, Multiplication Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

### 5.1 选择哪条高半乘法指令

| 想要的数学乘法 | 低 64 bit | 高 64 bit | 关键点 | 标签 |
| --- | --- | --- | --- | --- |
| signed x signed | `MUL` | `MULH` | 低半位模式相同，但高半取决于符号解释 | T1-VERIFIED / INTERPRETIVE |
| unsigned x unsigned | `MUL` | `MULHU` | 两个源都按无符号数解释 | T1-VERIFIED |
| signed `rs1` x unsigned `rs2` | `MUL` | `MULHSU` | 源操作数顺序有语义；交换 `rs1`/`rs2` 会改变哪一项是 signed | T1-VERIFIED |

`MULHSU` 的典型用途是多字长 signed 乘法：带符号被乘数的最高 word 含符号位，而乘数的较低 word 按 unsigned 处理。该用途说明其混合 signedness 是明确的 ISA 语义，并非编译器的命名习惯。T1-VERIFIED: [M Extension 2.0, Multiplication Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

### 5.2 同时需要高、低半时的推荐顺序

当同一乘积的高、低半都需要时，规范推荐先发高半、再发低半，并保持两个源寄存器的次序一致：

~~~asm
# 以 64-bit signed x signed 为例：{t0, t1} 是完整 128-bit 乘积。
# 约束：t0 不能与 a0 或 a1 重名；两条指令的 a0/a1 次序必须一致。
mulh    t0, a0, a1       # 高 64 bit
mul     t1, a0, a1       # 低 64 bit
~~~

若乘法的 signedness 不同，把第一条替换为 `MULHU` 或 `MULHSU`。满足上述寄存器约束的相邻序列可被微架构融合为一次乘法，但这是实现可采用的优化，并非程序可依赖的时延、吞吐或融合保证。T1-VERIFIED: [M Extension 2.0, Multiplication Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

### 5.3 RV64 中提取 32-bit 乘积上半部的条件

在 RV64 中，若两个 32-bit signed 参数已经正确符号扩展，或两个 32-bit unsigned 参数的高 32 bit 已清零，则 `MUL` 的 64-bit 输出正好容纳该 64-bit 乘积，因而可从中取得其 bit 63:32。若事先不知道输入是否已作正确的 sign/zero extension，规范建议先将两个源均左移 32 bit，再使用匹配 signedness 的 `MULH[[S]U]`。T1-VERIFIED: [M Extension 2.0, Multiplication Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

这条规则不能泛化成“任何 `MUL` 都能取任意 32-bit 乘积的高半”。决定正确性的前提是源寄存器的高 32 bit 表示；实际编译器是否产生该序列须由目标 ABI、优化级和反汇编结果另行核验。T1-VERIFIED / UNVERIFIED。

## 6. 除法与余数：普通 RV64 宽度

`DIV` 和 `DIVU` 分别执行 signed 与 unsigned 的 64-bit 整数除法，商向零截断。`REM` 与 `REMU` 给出相应余数；对于 `REM`，非零余数的符号与被除数相同。除有符号溢出情形外，二者满足 `dividend = divisor * quotient + remainder`。T1-VERIFIED: [M Extension 2.0, Division Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

| 指令 | `rs1` / `rs2` 的解释 | `rd` | 普通非边界情形 | 标签 |
| --- | --- | --- | --- |
| `DIV` | 64-bit signed / signed | 有符号商 | 向零截断 | T1-VERIFIED |
| `DIVU` | 64-bit unsigned / unsigned | 无符号商的 64-bit 位模式 | 向零截断 | T1-VERIFIED |
| `REM` | 64-bit signed / signed | 有符号余数 | 非零时符号与被除数相同 | T1-VERIFIED |
| `REMU` | 64-bit unsigned / unsigned | 无符号余数的 64-bit 位模式 | 与 `DIVU` 配对 | T1-VERIFIED |

### 6.1 除零和有符号溢出不是异常路径

规范为除零和唯一的 signed overflow 给出确定结果。下表的 `L` 在这一节为 64；在下一节的 `*W` 指令中为 32。

| 条件 | 被除数 | 除数 | `DIVU[W]` | `REMU[W]` | `DIV[W]` | `REM[W]` | 标签 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 除零 | `x` | `0` | `2^L - 1` | `x` | `-1` | `x` | T1-VERIFIED |
| signed overflow | `-2^(L-1)` | `-1` | 不适用 | 不适用 | `-2^(L-1)` | `0` | T1-VERIFIED |

换言之，在 RV64 的普通形式中，`DIV x, 0` 与 `DIVU x, 0` 的 64-bit 输出都是 `0xffffffffffffffff`；`REM x, 0` 与 `REMU x, 0` 都写回原 64-bit 被除数。signed overflow 仅是 `INT64_MIN / -1`，其商保持 `INT64_MIN`、余数为零；无符号除法没有对应溢出。T1-VERIFIED: [M Extension 2.0, Division Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

这意味着程序不能把“执行了 `DIV`”当作已检查除数非零。若语言、算法或接口要求把除零转成控制流，软件必须显式检查除数；结果为全 1 也不能作为可靠的除零判定，因为合法计算也可能产生同样的位模式。前一句是 ISA 边界，后一句是基于位模式冲突的 INTERPRETIVE 结论。T1-VERIFIED / INTERPRETIVE。

### 6.2 同时需要商和余数时

规范推荐保留相同源寄存器并先算商、后算余数：

~~~asm
# 以 signed 64-bit 除法为例。
# 约束：t0 不能与 a0 或 a1 重名，保证后一条仍读到原始被除数和除数。
div     t0, a0, a1       # quotient
rem     t1, a0, a1       # remainder
~~~

无符号情形使用 `DIVU` / `REMU`。符合该模式时，微架构可以融合为一次除法；规范没有规定任何实现必须融合，也没有规定除法时延。T1-VERIFIED: [M Extension 2.0, Division Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

## 7. RV64M 专有的 32-bit `*W` 指令

RV64M 的五条 word 指令都从 `rs1`、`rs2` 取低 32 bit，而目的寄存器始终接收符号扩展至 64 bit 的 32-bit 结果。这一规则尤其覆盖 `DIVUW` 与 `REMUW`：虽然输入按 unsigned 解释，结果仍不是零扩展。T1-VERIFIED: [M Extension 2.0, Multiplication and Division Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

### 7.1 `MULW`

`MULW` 将两个源的低 32 bit 相乘，把乘积低 32 bit 符号扩展写入 `rd`。T1-VERIFIED: [M Extension 2.0, MULW](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html) 乘积低半的位模式不取决于把两个 32-bit 因子当 signed 还是 unsigned；但写回 64-bit GPR 时，bit 31 决定高 32 bit。INTERPRETIVE。

例如低 32-bit 乘积为 `0x80000000` 时，`MULW` 的结果是 `0xffffffff80000000`，而不是 `0x0000000080000000`。这是 `sext32` 的直接结果，不代表该数在随后每条指令中都会被当作 signed；比特的 signed/unsigned 解释仍由所选指令决定。T1-VERIFIED / INTERPRETIVE。

### 7.2 `DIVW`、`DIVUW`、`REMW`、`REMUW`

| 指令 | 低 32-bit 操作数解释 | 32-bit 结果 | 写回 RV64 `rd` | 标签 |
| --- | --- | --- | --- | --- |
| `DIVW` | signed / signed | 商，向零截断 | `sext32(quotient)` | T1-VERIFIED |
| `DIVUW` | unsigned / unsigned | 商 | `sext32(quotient)` | T1-VERIFIED |
| `REMW` | signed / signed | 余数 | `sext32(remainder)` | T1-VERIFIED |
| `REMUW` | unsigned / unsigned | 余数 | `sext32(remainder)` | T1-VERIFIED |

特别地，`REMW` 与 `REMUW` 在除零时也**始终**符号扩展 32-bit 结果。故 `REMUW rd, rs1, x0` 的结果是 `sext32(low_32(rs1))`，不是原封不动的 64-bit `rs1`，也不是 `zero_extend(low_32(rs1))`。同理，`DIVUW rd, rs1, x0` 的 32-bit 全 1 商会符号扩展为 64-bit 的 `-1`。T1-VERIFIED: [M Extension 2.0, Division Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

### 7.3 以 `L=32` 代入边界表

| 指令组 | 低 32-bit 除零结果写回到 RV64 `rd` | signed overflow 结果 | 标签 |
| --- | --- | --- | --- |
| `DIVW` | `sext32(0xffffffff)`，即 `0xffffffffffffffff` | `sext32(0x80000000)`，即 `0xffffffff80000000` | T1-VERIFIED / INTERPRETIVE |
| `DIVUW` | `sext32(0xffffffff)`，即 `0xffffffffffffffff` | 不适用 | T1-VERIFIED / INTERPRETIVE |
| `REMW` | `sext32(low_32(rs1))` | `0` | T1-VERIFIED / INTERPRETIVE |
| `REMUW` | `sext32(low_32(rs1))` | 不适用 | T1-VERIFIED / INTERPRETIVE |

这里的 signed overflow 是 `0x80000000 / 0xffffffff`，即 32-bit `INT32_MIN / -1`；即使源寄存器的上半部分不是这些位，word 指令也只观察低 32 bit。T1-VERIFIED: [M Extension 2.0, division boundary table](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)

## 8. 汇编阅读示例：宽度先行

以下代码只展示架构语义，不声明任何特定核心的周期数、融合行为、异常向量或编译器输出。

~~~asm
# a0 = 0x0000000100000000, a1 = 2
mul     t0, a0, a1      # t0 = 0x0000000200000000：完整 64-bit 运算的低半
mulw    t1, a0, a1      # t1 = 0x0000000000000000：低 32 bit 的 0 x 2

# 无符号 word 除法也以符号扩展格式写回。
# 若 low_32(a0) / low_32(a1) 的 32-bit 商是 0x80000000，
# 则 DIVUW 的 rd = 0xffffffff80000000。
divuw   t2, a0, a1
~~~

读这类代码时按以下顺序判断最稳妥：先确认 mnemonic 是否带 `W`，再确定每个源的有效位宽和 signedness，最后决定 `rd` 的 sign extension。不要根据寄存器名字、C 变量类型或汇编注释跳过位宽判断。前两句由 ISA 直接支持；最后一句是 INTERPRETIVE 的审阅方法。T1-VERIFIED / INTERPRETIVE。

## 9. ISA、实现、profile 与软件边界

| 层次 | 本篇可确认的内容 | 本篇不确认的内容 | 标签 |
| --- | --- | --- | --- |
| ISA：RV64M | 13 条指令的位级结果、除零/overflow 值、推荐配对序列与编码 | 乘法器/除法器结构、周期、功耗、是否真的融合 | T1-VERIFIED / UNVERIFIED |
| 微架构 | 符合推荐序列的乘法或除法**可以**被融合 | 任一核必须融合，或融合一定更快 | T1-VERIFIED / UNVERIFIED |
| ISA 名称 | `RV64IM` 表示 RV64I 加 M；`G = IMAFDZicsr_Zifencei`，所以 `G` 含 `M` | 某个自称“64-bit RISC-V”的 SoC 必然具备 M | T1-VERIFIED / UNVERIFIED |
| profile / platform | 裸 `RV64I` 与独立 `M` 的边界可由 ISA 名称表达 | 未核验某个 profile、板卡、内核镜像或固件的实际要求 | T1-VERIFIED / UNVERIFIED |
| 软件/工具链 | 汇编器、编译器和 JIT 必须生成与目标 `-march` 相容的指令序列 | 本地 GCC/LLVM 是否接受、如何优化或是否以库调用替代除法 | UNVERIFIED |

`M` 的扩展性并不说明它“性能弱于”把乘除放在别的 ISA 基础集合中的设计。对本课程而言，可靠的跨架构锚点是：应比对准确的位宽、signedness、除零和 overflow 语义，而不是仅按 `mul`/`div` mnemonic 相似性推断等价性；不在此对 Arm/x86 作性能或软件生态结论。INTERPRETIVE。

## 10. 六维总结

| 维度 | 结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| 1. Ratification & maturity | 用户指定的 ratified library `v20260120` 包含 M Extension Version 2.0 | 需在下一次评审前复核固定版本是否仍是目标基线 | T1-VERIFIED |
| 2. ISA semantics | 普通八条以 RV64 `XLEN=64` 运算；五条 `*W` 以低 32 bit 运算并符号扩展 | `DIVUW`/`REMUW` 不能当成零扩展 word 指令 | T1-VERIFIED |
| 3. Platform requirements | ISA 允许实现按扩展组合选择 M；M 不属于裸 I | 特定 profile、系统或芯片是否要求 M 未在本文核验 | T1-VERIFIED / UNVERIFIED |
| 4. Software evidence | ISA 可用 `RV64IM` 命名，且 `G` 包含 M | 本地工具链版本、`-march` 行为、库调用与反汇编没有实测 | T1-VERIFIED / UNVERIFIED |
| 5. Competitive anchor | 比较其他 ISA 时应核对结果宽度、signedness 和边界值 | 本文不宣称任何跨 ISA 性能、异常或 ABI 等价 | INTERPRETIVE |
| 6. Deployment intent | M 从基础 ISA 分离以支持低成本实现和附加加速器场景 | 不能由此反推任一具体实现没有/有硬件乘除单元 | T1-VERIFIED / UNVERIFIED |

## 11. 常见误解

| 误解 | 正确表述 | 标签 |
| --- | --- | --- |
| “RV64M 只需要读五条 `*W` 指令。” | RV64M 保留八条以 `XLEN=64` 运作的普通 M 指令，并新增五条 `*W`。 | T1-VERIFIED |
| “`MULH`、`MULHU`、`MULHSU` 只是不同别名。” | 三者的高半结果取决于 signed x signed、unsigned x unsigned、signed x unsigned 的明确解释。 | T1-VERIFIED |
| “`MULW` 是 unsigned word multiply。” | `MULW` 定义的是低 32-bit 乘积再符号扩展；它没有独立的 `MULUW`。 | T1-VERIFIED |
| “`DIVUW` / `REMUW` 会零扩展。” | 它们把 32-bit unsigned 商/余数**符号扩展**到 64 bit。 | T1-VERIFIED |
| “整数除零必然触发异常。” | M 的除零商为全 1、余数为被除数；需要控制流检查时由软件显式完成。 | T1-VERIFIED |
| “`INT64_MIN / -1` 可以得到正 `2^63`。” | 正 `2^63` 不可表示为 signed 64-bit；`DIV` 写回被除数 `INT64_MIN`，`REM` 为 0。 | T1-VERIFIED |
| “同一对乘除指令一定只执行一次硬件操作。” | 规范允许微架构融合推荐的相邻序列，但未要求融合。 | T1-VERIFIED |
| “有 `RV64I` 就一定有 `M`。” | `M` 是独立标准扩展；应通过 ISA string、文档或实际探测确认。 | T1-VERIFIED / UNVERIFIED |

## 12. 验证清单

- [ ] 重新打开 [M Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html)、[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html) 和 [RV32/64G listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)，确认使用的仍是 `v20260120` 语义。
- [ ] 对每条无 `W` 指令按 64-bit `XLEN` 建模；对每条 `*W` 指令先截取低 32 bit，再把结果符号扩展。
- [ ] 检查高半乘法 signedness：`MULH`、`MULHU`、`MULHSU`，特别是 `MULHSU` 中 `rs1`/`rs2` 的次序。
- [ ] 若高低半都需要，确保两条乘法源操作数顺序相同且高半目的寄存器不覆盖源；若商余都需要，确保商目的寄存器不覆盖源。
- [ ] 单测普通 64-bit 与 word 32-bit 的除零、`INT64_MIN / -1`、`INT32_MIN / -1`，并单测 `DIVUW`/`REMUW` 输出的 bit 63:32。
- [ ] 检查对象文件的实际 ISA 属性和编译选项（例如是否确实面向包含 `M` 的目标）；用 `objdump -dr` 核验关键路径是 M 指令还是库调用。该项在本文中尚未实测。UNVERIFIED。
- [ ] 将 ISA 指令语义、具体核的时序/融合、平台要求以及语言运行时的除零策略分别记录，避免把任一层的结论扩大到另一层。

## 13. 参考资料

1. [RISC-V Unprivileged ISA `v20260120` index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) - 用户指定的官方 ratified 版本入口。
2. [M Extension for Integer Multiplication and Division, Version 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/m-st-ext.html) - 乘法、除法、word 指令、边界值和 `Zmmul` 的主依据。
3. [RV64I Base Integer Instruction Set, Version 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html) - `XLEN=64` 与 RV64 word 结果的基础上下文。
4. [RV32/64G Instruction Set Listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) - RV32M/RV64M 指令枚举与编码。
5. [ISA Extension Naming Conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) - `M`、`RV64IM` 和 `G` 的命名边界。
6. [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) - Layer 1 机器可读文档的连续部署入口；不替代固定 ratified 版本。
