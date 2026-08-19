# RISC-V C 标准扩展：RVC 压缩指令（Version 2.0）解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA `v20260120`](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) 中的 ["C" Extension for Compressed Instructions, Version 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html)。本文讨论该固定 ratified-library 快照中的 `C` 扩展，不把后续的 `Zc*` 代码尺寸扩展、某个实现的取指结构、汇编器版本或特定 profile 的实际要求混入 `C` 的 ISA 语义。

## 1. 定位与结论

`C` 是 RISC-V 的标准 16-bit 压缩指令编码扩展；可附加到 `RV32I`、`RV32E`、`RV64I` 或 `RV64E`，这些组合统称为 **RVC**。它不是独立 ISA，也不增加一套脱离基础 ISA 的通用计算语义。其设计目标是为常见操作提供较短编码，并让 16-bit 与 32-bit 指令在同一指令流中自由混排。T1-VERIFIED: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)

规范给出的典型统计是：程序中约 50%--60% 的指令可替换为 RVC，静态代码尺寸可降低约 25%--30%。这是规范说明的典型收益，不是对每个程序、编译选项、链接布局或微架构的性能保证。T1-VERIFIED / INTERPRETIVE: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)

| 问题 | 准确结论 | 不应由此推出 | 标签 |
| --- | --- | --- | --- |
| `C` 的作用 | 为常见的基础整数、以及适用时的 `F`/`D` 浮点 load/store 提供 16-bit 编码 | `C` 是一套可单独执行的 CPU ISA | T1-VERIFIED |
| 指令长度 | `C` 允许 16-bit 和 32-bit 指令混排；实现 `C` 时 `IALIGN=16` | 所有指令都会变成 16 bit，或程序只含一种长度 | T1-VERIFIED |
| 架构语义 | RVC 的设计约束是每条指令对应一条基础 `I`/`E` 或适用的 `F`/`D` 指令 | 每条 RVC 都在所有可见细节上与其文字展开完全相同 | T1-VERIFIED |
| 代码密度 | 小立即数、常用寄存器和常见调用约定模式更容易压缩 | 任意汇编源都会获得相同压缩率 | T1-VERIFIED / INTERPRETIVE |
| 扩展边界 | `C` 是独立标准扩展，`G` 的定义本身不含 `C` | 任意 `RV64G` 或任意 64-bit RISC-V 核必然支持压缩指令 | T1-VERIFIED |

### 1.1 为什么常见指令可以变短

RVC 主要利用以下高频约束：立即数或地址偏移较小；操作数是 `x0`、ABI link register `x1` 或 stack pointer `x2`；目的寄存器与第一源寄存器相同；或者寄存器落在高频的八个寄存器中。压缩并非对任意 32-bit 指令进行逐位截断，而是为这些常见模式重新分配稀缺的 16-bit 编码位。T1-VERIFIED: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)

RVC 的一对一设计使硬件可以在 decode 阶段将压缩指令展开，再沿用已有执行路径；规范也指出，编译器可以不了解 `C`，由 assembler 或 linker 完成压缩，不过压缩感知的编译器通常能得到更好的结果。这些是允许的实现和工具链组织方式，不规定某个处理器必须有名为“decompressor”的独立流水级，也不保证本地工具链会选择压缩。T1-VERIFIED / UNVERIFIED: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)

### 1.2 `IALIGN=16` 的精确含义

启用 `C` 后，32-bit 指令可从任意 16-bit 边界开始，`IALIGN=16`；规范明确指出，此时不存在会产生 instruction-address-misaligned exception 的指令。这里说的是**指令地址对齐异常**，不是数据 load/store 的地址对齐、访问权限、页错误或总线错误。后者仍由相应基础访存指令和 execution environment 定义。T1-VERIFIED: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)

## 2. 证据、版本与范围边界

### 2.1 证据优先级

| 层级 | 本次来源 | 用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1：UDB | [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 机器可读资料和最新连续部署的导航交叉检查；不替代固定版本的语义锚点 | T1-VERIFIED |
| Layer 2：Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 说明 `norm:` 锚点所标识的是合规实现必须满足的架构可见行为 | T1-VERIFIED |
| Layer 3：Ratified ISA | [C Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html)、[RV32I](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)、[RV64I](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html) | C 指令语义、其基础 ISA 上下文和 LR/SC 边界的主依据 | T1-VERIFIED |
| Layer 3：命名规则 | [ISA Extension Naming Conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) | `C`、`G`、`RV32I`/`RV64I` 的命名与组合边界 | T1-VERIFIED |

### 2.2 标签

- **T1-VERIFIED**：可直接在指定的 RISC-V ratified 规范或其规范性资料中定位。
- **INTERPRETIVE**：由规范事实归纳出的阅读或实现审阅方法，不增加新的 ISA 保证。
- **UNVERIFIED**：本地处理器、工具链、链接器、操作系统或反汇编输出尚未实测。

### 2.3 版本锚点与范围

| 项目 | 本文采用的结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| C 章节 | `"C" Extension for Compressed Instructions, Version 2.0` | 以用户给定的 `v20260120` 页面为准 | T1-VERIFIED |
| 可附加的基础 ISA | 页面明确列出 `RV32I`、`RV32E`、`RV64I`、`RV64E` | 不能从这里推导 `RV128C` 的编码或语义 | T1-VERIFIED |
| `Zc*` | 是另一个单独章节和扩展家族 | 不在本篇把 `Zca`、`Zcb`、`Zcmp` 等当作 C Extension 2.0 的隐含部分 | T1-VERIFIED |
| 浮点压缩访存 | 取决于 `F` 和/或 `D` 是否同时实现 | `C` 不单独提供浮点寄存器或浮点算术 | T1-VERIFIED |

**SPEC-UPDATE-ALERT：** 当前核对的官方 ratified library 页面使用 `v20260120`。UnifiedDB 是按主线持续生成的资料，可能比这个固定快照更新；在下一次课程或项目评审前，应重新检查 [ratified library](https://docs.riscv.org/reference/home/index.html)、[C Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html) 和 [UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)，不要把连续部署内容自动当作同一份 ratified 基线。T1-VERIFIED

## 3. RVC 的长度、象限与九种格式

### 3.1 先按低两位判定长度

RVC 的主要 16-bit 编码由 `inst[1:0]` 的 `00`、`01`、`10` 三个象限组成；低两位为 `11` 时表示该指令宽于 16 bit，其中包括基础 ISA 的 32-bit 指令。再以 `inst[15:13]` 在每个 16-bit 象限中选择具体大类。这个长度判定是混合长度取指/译码的起点，不应把 `11` 当成第四种 RVC 指令象限。T1-VERIFIED: [C Extension 2.0, RVC Instruction Set Listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-8-rvc-instruction-set-listings)

| `inst[1:0]` | 本章中的含义 | 示例类别 | 标签 |
| --- | --- | --- | --- |
| `00` | RVC quadrant 0 | `C.ADDI4SPN`、寄存器基址 load/store | T1-VERIFIED |
| `01` | RVC quadrant 1 | `C.ADDI`、`C.J`、`C.BEQZ`、压缩 ALU | T1-VERIFIED |
| `10` | RVC quadrant 2 | `C.SLLI`、SP 基址 load/store、`C.JR`/`C.ADD` | T1-VERIFIED |
| `11` | 宽于 16-bit 的指令编码空间 | 基础 ISA 的 32-bit 指令等 | T1-VERIFIED |

### 3.2 九种 16-bit 格式

| 格式 | 名称 | 典型用途 | 可使用的整数寄存器字段 | 标签 |
| --- | --- | --- | --- | --- |
| CR | Register | `C.MV`、`C.ADD`、`C.JR`、`C.JALR` | 完整 5-bit `rd/rs1`、`rs2` | T1-VERIFIED |
| CI | Immediate | `C.LI`、`C.ADDI`、SP load | 完整 5-bit `rd/rs1` | T1-VERIFIED |
| CSS | Stack-relative Store | SP 相对 store | 完整 5-bit `rs2` | T1-VERIFIED |
| CIW | Wide Immediate | `C.ADDI4SPN` | 压缩 `rd'` | T1-VERIFIED |
| CL | Load | 小寄存器组的 load | 压缩 `rs1'`、`rd'` | T1-VERIFIED |
| CS | Store | 小寄存器组的 store | 压缩 `rs1'`、`rs2'` | T1-VERIFIED |
| CA | Arithmetic | `C.SUB`、`C.AND` 等 | 压缩 `rd'/rs1'`、`rs2'` | T1-VERIFIED |
| CB | Branch/Arithmetic | `C.BEQZ`、`C.SRLI`、`C.ANDI` | 压缩 `rd'/rs1'` | T1-VERIFIED |
| CJ | Jump | `C.J`、`C.JAL` | 无通用 GPR 操作数字段 | T1-VERIFIED |

`CR`、`CI`、`CSS` 的完整 5-bit 寄存器字段可表示完整的整数 GPR 编号；`CIW`、`CL`、`CS`、`CA`、`CB` 的三位 prime 字段则映射为 `x8`--`x15`。对于压缩的寄存器型浮点 load/store，对应三位字段映射为 `f8`--`f15`。基础 `E` ISA 对可用寄存器的规则仍来自它自身，`C` 不会额外创建寄存器。T1-VERIFIED: [C Extension 2.0, Compressed Instruction Formats](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-2-compressed-instruction-formats)

| 三位值 | 整数寄存器 | ABI 名 | 浮点寄存器 | ABI 名 |
| --- | --- | --- | --- | --- |
| `000`--`111` | `x8`--`x15` | `s0`、`s1`、`a0`--`a5` | `f8`--`f15` | `fs0`、`fs1`、`fa0`--`fa5` |

立即数字段看起来常被“打散”，这是刻意安排：尽量使寄存器源字段及相同意义的立即数字段处于相同位置，以减少硬件中的立即数选择逻辑。需要符号扩展时，符号位统一来自 `inst[12]`；但**不是每一个 RVC 立即数都符号扩展**，访存偏移和 `C.ADDI4SPN` 等另有零扩展与缩放规则。T1-VERIFIED: [C Extension 2.0, Compressed Instruction Formats](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-2-compressed-instruction-formats)

## 4. RV32C、RV64C 与 F/D 的交叉条件

同一 16-bit 编码位模式并非脱离目标 ISA 就能唯一命名。译码必须先知道是 RV32 还是 RV64，以及相关的 `F`/`D` 扩展是否存在。例如，部分 RV32C 浮点访存码位在 RV64C 中复用为整数 doubleword 访存。T1-VERIFIED: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)

| 编码位置/指令族 | RV32C 含义 | RV64C 含义 | 条件与易错点 | 标签 |
| --- | --- | --- | --- | --- |
| Q0 register-based load | `C.FLW` | `C.LD` | `C.FLW` 只适用于 RV32FC；`C.LD` 只适用于 RV64C | T1-VERIFIED |
| Q0 register-based store | `C.FSW` | `C.SD` | `C.FSW` 只适用于 RV32FC；`C.SD` 只适用于 RV64C | T1-VERIFIED |
| Q1 | `C.JAL` | `C.ADDIW` | RV32 的短范围 call 与 RV64 的 word add-immediate 复用码位 | T1-VERIFIED |
| Q2 SP-relative load | `C.FLWSP` | `C.LDSP` | 前者仅 RV32FC，后者仅 RV64C | T1-VERIFIED |
| Q2 SP-relative store | `C.FSWSP` | `C.SDSP` | 前者仅 RV32FC，后者仅 RV64C | T1-VERIFIED |
| CA arithmetic | 相应 `C.ADDW`/`C.SUBW` 码位为 RES | `C.ADDW`、`C.SUBW` | 两条 word ALU 指令仅 RV64C | T1-VERIFIED |
| double-precision load/store | `C.FLD`/`C.FSD` | `C.FLD`/`C.FSD` | 两者都要求 `D`，即 RV32DC 或 RV64DC | T1-VERIFIED |

若实现 `C` 且实现了相关的标准 `F` 和/或 `D` 扩展，规范要求提供相应的压缩浮点 load/store。这条要求不等于“只要有 C 就自动有 F/D”，也不等于“只要有 F/D 就自动有 C”；应先按 ISA string 与实现配置确认扩展集合。T1-VERIFIED: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)

本页明确讨论的可附加 base ISA 是 RV32 与 RV64 的 `I`/`E` 变体，未给出 `RV128C`、`C.LQ` 或 `C.SQ` 的条目。因此，不能把“访存偏移按访问宽度缩放”的一般性设计说明外推为本页已定义的 RV128C 指令清单。T1-VERIFIED

## 5. 访存：SP 专用形式与 prime 寄存器形式

RVC 有两套 load/store 形式。一套以 ABI stack pointer `x2` 为基址，能对完整数据寄存器字段进行访问，针对函数序言和尾声的保存/恢复；另一套以 `rs1'` 和 `rd'`/`rs2'` 表示八个高频寄存器。数据传输的偏移量为零扩展，并依访问宽度缩放：word 为 4 倍、doubleword 为 8 倍、quadword 为 16 倍。后一条是格式的通用说明；本页实际列出的 RV32/RV64 指令应以上表和下表为准。T1-VERIFIED: [C Extension 2.0, Load and Store Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-3-load-and-store-instructions)

### 5.1 Stack-pointer-based load/store

| 指令 | 适用 ISA | 展开语义 | 特殊限制 | 标签 |
| --- | --- | --- | --- | --- |
| `C.LWSP` / `C.SWSP` | RV32C、RV64C | `lw` / `sw`，基址为 `x2`，offset 按 4 缩放 | `C.LWSP` 的 `rd=x0` 为 RES | T1-VERIFIED |
| `C.LDSP` / `C.SDSP` | RV64C | `ld` / `sd`，基址为 `x2`，offset 按 8 缩放 | `C.LDSP` 的 `rd=x0` 为 RES | T1-VERIFIED |
| `C.FLWSP` / `C.FSWSP` | RV32FC | `flw` / `fsw`，基址为 `x2`，offset 按 4 缩放 | 需要 `F`，仅 RV32 | T1-VERIFIED |
| `C.FLDSP` / `C.FSDSP` | RV32DC、RV64DC | `fld` / `fsd`，基址为 `x2`，offset 按 8 缩放 | 需要 `D` | T1-VERIFIED |

### 5.2 Register-based load/store

| 指令 | 适用 ISA | 展开语义 | 寄存器限制 | 标签 |
| --- | --- | --- | --- | --- |
| `C.LW` / `C.SW` | RV32C、RV64C | `lw rd', offset(rs1')` / `sw rs2', offset(rs1')` | base 和数据 GPR 都取 `x8`--`x15` | T1-VERIFIED |
| `C.LD` / `C.SD` | RV64C | `ld` / `sd` | base 和数据 GPR 都取 `x8`--`x15` | T1-VERIFIED |
| `C.FLW` / `C.FSW` | RV32FC | `flw` / `fsw` | `rs1'` 为 `x8`--`x15`，FP 数据寄存器为 `f8`--`f15` | T1-VERIFIED |
| `C.FLD` / `C.FSD` | RV32DC、RV64DC | `fld` / `fsd` | `rs1'` 为 `x8`--`x15`，FP 数据寄存器为 `f8`--`f15` | T1-VERIFIED |

压缩形式改变的是指令编码，不会把 `LW`、`LD`、`FLD` 等 underlying load 的数据宽度、异常、端序、访问权限或 memory-order 语义替换成另一套规则。阅读 `C.LWSP` 时，先还原为 `lw rd, offset(x2)`，再用目标 RV32I/RV64I 与 EEI 的规则判断访存；不要仅因目标是 `x0` 而忽略规范中明确列为 RES 的编码。T1-VERIFIED / INTERPRETIVE: [C Extension 2.0, Load and Store Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-3-load-and-store-instructions)

## 6. 控制转移：短位移、`pc+2` 与寄存器限制

所有 RVC 控制转移偏移都以 2 bytes 为单位。它们与 `IALIGN=16` 相适配，但可达范围仍很小，不能因为指令采用 16-bit 编码就把它们当作任意距离的 branch/call。T1-VERIFIED: [C Extension 2.0, Control Transfer Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-4-control-transfer-instructions)

| 指令 | 适用 ISA | 核心语义 | 范围/限制 | 标签 |
| --- | --- | --- | --- | --- |
| `C.J` | RV32C、RV64C | `pc + sext(offset)`，等价目标动作为 `jal x0, offset` | 约 `+/- 2 KiB` | T1-VERIFIED |
| `C.JAL` | 仅 RV32C | 跳转，并把下一条地址 `pc+2` 写入 `x1` | 约 `+/- 2 KiB`；RV64 对应码位是 `C.ADDIW` | T1-VERIFIED |
| `C.JR` | RV32C、RV64C | 跳至 `rs1`，对应 `jalr x0, 0(rs1)` | `rs1=x0` 为 RES | T1-VERIFIED |
| `C.JALR` | RV32C、RV64C | 跳至 `rs1`，把 `pc+2` 写入 `x1` | `rs1=x0` 编码为 `C.EBREAK` | T1-VERIFIED |
| `C.BEQZ` / `C.BNEZ` | RV32C、RV64C | 将 `rs1'` 与 `x0` 比较后条件转移 | 约 `+/- 256 B`；`rs1'` 仅为 `x8`--`x15` | T1-VERIFIED |

特别要区分 `C.JALR` 与基础 `JALR` 的 link address：前者写入 `pc+2`，基础 32-bit `JALR` 写入 `pc+4`。规范指出，因这一点 `C.JALR` 严格说不完全展开成一条基础 RVI 指令；“RVC 以一条基础指令为语义模板”不应被误读成此处 link 值也相同。T1-VERIFIED: [C Extension 2.0, Control Transfer Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-4-control-transfer-instructions)

## 7. 整数计算与常量生成

### 7.1 常量、`sp` 调整和移位

| 指令族 | 指令与语义 | 关键编码条件 | 标签 |
| --- | --- | --- | --- |
| 常量 | `C.LI rd, imm` -> `addi rd, x0, sext(imm)` | `rd=x0` 是 HINT | T1-VERIFIED |
| 高位常量 | `C.LUI rd, imm` -> `lui rd, imm` | `imm=0` 为 RES；`rd=x2, imm!=0` 改解为 `C.ADDI16SP`；`rd=x0, imm!=0` 是 HINT | T1-VERIFIED |
| 加立即数 | `C.ADDI rd, imm` -> `addi rd, rd, imm` | 非零 `rd` 且 `imm=0` 是 HINT；`rd=x0` 是 `C.NOP` 编码空间 | T1-VERIFIED |
| RV64 word add | `C.ADDIW rd, imm` -> `addiw rd, rd, imm` | 仅 RV64C；`imm=0` 可作 `sext.w rd`；`rd=x0` 为 RES | T1-VERIFIED |
| 调整栈指针 | `C.ADDI16SP` -> `addi x2, x2, nzimm` | 非零立即数按 16 缩放，范围 `[-512, 496]` | T1-VERIFIED |
| 构造栈内对象指针 | `C.ADDI4SPN rd', nzuimm` -> `addi rd', x2, nzuimm` | 零扩展立即数按 4 缩放，且不可为 0 | T1-VERIFIED |
| 移位/按位与 | `C.SLLI`、`C.SRLI`、`C.SRAI`、`C.ANDI` | `C.SLLI` 的 `shamt=0` 或 `rd=x0` 为 HINT；`C.SRLI`/`C.SRAI` 的 `shamt=0` 为 HINT；`C.ANDI` 使用符号扩展的 6-bit immediate | T1-VERIFIED |

`C.ADDI16SP` 面向标准 ABI 的常见函数序言/尾声：标准 calling convention 中 `sp` 保持 16-byte 对齐，因而这种以 16 为单位的压缩位移可覆盖常见栈帧调整。该 ABI 背景解释了编码取舍，但不把 `x2` 变成基础硬件中唯一可作为地址基址的寄存器。T1-VERIFIED / INTERPRETIVE: [C Extension 2.0, Integer Register-Immediate Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-5-2-integer-register-immediate-operations)

对 `C.SLLI`、`C.SRLI`、`C.SRAI`，RV32C 要求 `shamt[5]=0`；取 1 的码位留给 custom extensions。不能把 RV64C 的更宽 shift amount 规则带入 RV32C，也不能把 custom 编码当作可移植的标准 RVC 指令。T1-VERIFIED: [C Extension 2.0, Integer Register-Immediate Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-5-2-integer-register-immediate-operations)

### 7.2 寄存器-寄存器 ALU

| 格式 | 指令 | 展开语义 | 寄存器范围 | 标签 |
| --- | --- | --- | --- | --- |
| CR | `C.MV rd, rs2` | `add rd, x0, rs2` | `rs2=x0` 且 `rd/rs1!=x0` 时为 `C.JR`；`rd/rs1=x0, rs2=x0` 为 RES；`rd=x0, rs2!=x0` 为 HINT | T1-VERIFIED |
| CR | `C.ADD rd, rs2` | `add rd, rd, rs2` | `rs2` 不得为 `x0`，否则进入 `C.JALR`/`C.EBREAK` 编码 | T1-VERIFIED |
| CA | `C.SUB`、`C.XOR`、`C.OR`、`C.AND` | 对 `rd'/rs1'` 与 `rs2'` 执行对应运算 | 两个操作数在 `x8`--`x15` | T1-VERIFIED |
| CA | `C.SUBW`、`C.ADDW` | `subw` / `addw`，结果按 RV64 word 规则符号扩展 | 仅 RV64C，操作数在 `x8`--`x15` | T1-VERIFIED |

`C.MV` 的规范展开是 `add rd, x0, rs2`，而常见的 `mv` pseudoinstruction 通常基于 `addi`。实现可为 rename 或其他优化采用不同内部处理，但软件应只依赖其架构结果，不依赖是否发生 move elimination、macro-op fusion 或特定时延。T1-VERIFIED / UNVERIFIED: [C Extension 2.0, Integer Register-Register Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-5-3-integer-register-register-operations)

## 8. `C.NOP`、HINT、RES、Custom 与非法编码

RVC 编码表会把不同的无效或非普通操作数情况标为 `RES`、`Custom` 或 `HINT`，三者不能混为“都会无害执行”。`RES` 是留给未来标准扩展的保留编码，`Custom` 是面向非标准扩展的指定空间，`HINT` 则是除推进 `pc` 和增加适用的性能计数器外不改变架构可见状态的提示编码。可移植软件不得把 `RES` 或 `Custom` 当作确定的标准语义。T1-VERIFIED: [C Extension 2.0, RVC Instruction Set Listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-8-rvc-instruction-set-listings)

| 编码类别 | 架构含义 | 软件/验证处理方式 | 标签 |
| --- | --- | --- | --- |
| `C.NOP` | 不改变用户可见状态，只推进 `pc` 并更新适用的性能计数器 | 可以作为 no-op 读取；`imm!=0` 的相关码位是 HINT | T1-VERIFIED |
| HINT | 忽略它的实现可将其作为只推进 `pc` 并增加适用性能计数器的 no-op 执行 | 不要让正确性依赖 HINT 被某个实现识别或优化 | T1-VERIFIED |
| RES | 保留给未来标准扩展 | 不生成、不测试为“必然非法”、不用于可移植二进制 | T1-VERIFIED / INTERPRETIVE |
| Custom | 指定给非标准扩展 | 仅在清楚目标厂商契约时使用 | T1-VERIFIED / INTERPRETIVE |
| 全零 16-bit 指令 | 永久保留为 illegal instruction | 可用于捕获执行清零/不存在的内存区域等错误，不能由自定义扩展重定义 | T1-VERIFIED |

RVC HINT 常刻意复用不会改变状态的计算，例如目标为 `x0`，或把一个寄存器写回自身。RVC HINT 并不要求与同样字面操作数的 RVI HINT 编码具有相同含义。T1-VERIFIED: [C Extension 2.0, HINT Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#rvc-hints)

## 9. 与 A 扩展的 LR/SC 序列边界

`C` 自身不提供原子操作。对于同时支持 `A` 和 `C` 的实现，受约束 LR/SC 序列中原本允许的 `I` 指令，其有效压缩 `C` 形式也允许出现在该序列中；规范给出的推论是，含这些有效 C 指令的 LR/SC 序列仍须能够最终完成。这里的限定是“受约束序列中允许的 I 指令的有效压缩形式”，不是所有 C 指令都自动成为 LR/SC 序列的合法成员。T1-VERIFIED: [C Extension 2.0, Usage of C Instructions in LR/SC Sequences](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-6-usage-of-c-instructions-in-lrsc-sequences)

| 层次 | 可以确认 | 不应扩大为 | 标签 |
| --- | --- | --- | --- |
| ISA | A+C 实现须处理符合约束的压缩 I 指令 LR/SC 序列 | 任何任意长度、任意 C 指令混入的循环都享有相同保证 | T1-VERIFIED |
| 微架构 | 可在 decode 展开 C 再进入既有原子/执行逻辑 | 必须采用某种 reservation、pipeline 或 replay 实现 | UNVERIFIED |
| 软件 | 可在满足 A 规范约束时使用合法压缩形式 | 不检查实际 `-march` 与汇编输出也必然生成 C 指令 | T1-VERIFIED / UNVERIFIED |

## 10. 汇编阅读示例：先还原语义，再判断是否可压缩

以下示例仅说明 ISA 语义与常见压缩机会，不宣称本地 assembler、linker 或编译器一定采用这些编码。

~~~asm
# RV64C 的典型函数序言/尾声片段。
# sp 的调整可由 C.ADDI16SP 表达；ra 的保存/恢复使用 SP 相对形式。
c.addi16sp sp, -32     # addi sp, sp, -32
c.sdsp     ra, 24(sp)  # sd   ra, 24(sp)

# ... 函数体 ...

c.ldsp     ra, 24(sp)  # ld   ra, 24(sp)
c.addi16sp sp, 32      # addi sp, sp, 32
c.jr       ra          # jalr x0, 0(ra)

# a0 是 x10，属于 prime register group x8--x15。
c.beqz     a0, .Ldone # beq a0, x0, .Ldone；位移以 2-byte 为单位
.Ldone:
~~~

上述 `C.SDSP`/`C.LDSP` 是 RV64C 专属；RV32C 的对应常见整数栈访问使用 `C.SWSP`/`C.LWSP`。仅根据 mnemonic 中是否带 `c.` 仍不够：还要检查 `XLEN`、是否是 `F`/`D` 访存、寄存器是否落在 prime group、立即数是否落在该格式范围，以及保留条件是否被触发。前半句是 T1-VERIFIED，后半句是 INTERPRETIVE 的反汇编审阅步骤。

## 11. ISA、实现、profile 与软件边界

### 11.1 ISA string 不替代实际配置核验

ISA naming 规则把 `C` 定义为 16-bit compressed-instruction standard extension，`G` 则是 `IMAFDZicsr_Zifencei` 的缩写，不包含 `C`。因此 `RV64GC` 明确表达同时有 general-purpose 基线和 C，而仅写 `RV64G` 不能推出存在 RVC。ISA string 大小写不敏感，但 extension 的 canonical 排序和版本书写仍应遵守命名规则。T1-VERIFIED: [ISA Extension Naming Conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html)

| 层次 | 本篇可确认的内容 | 本篇不确认的内容 | 标签 |
| --- | --- | --- | --- |
| ISA | `C` 的格式、编码条件、展开语义、RV32/RV64 复用和 HINT/RES 边界 | 某个 SoC 是否实现 C | T1-VERIFIED / UNVERIFIED |
| 微架构 | RVC 可以在 decode 展开；16/32-bit 指令可混排 | 取指跨 line 处理、decode 宽度、uop 数、功耗、时延、融合策略 | T1-VERIFIED / UNVERIFIED |
| 平台/profile | 基础 ISA 可选择是否附加 C；本文不把任一 profile 的额外要求归给 C 章节 | 某个 profile、OS 镜像或板卡是否要求/暴露 C | T1-VERIFIED / UNVERIFIED |
| 工具链 | assembler/linker 可以参与压缩；二进制的 ISA string 可表达 C | 本地 GCC/LLVM/binutils 是否接受何种语法、何时压缩或如何 relaxation | T1-VERIFIED / UNVERIFIED |
| 跨 ISA 对比 | 可把 RVC 归为 mixed 16/32-bit code-density 方案 | 不在本文宣称与 Arm Thumb、microMIPS 等的性能、ABI 或二进制兼容性等价 | INTERPRETIVE |

### 11.2 一个保守的实现审阅流程

下面是从规范一对一映射推得的审阅流程，而非必须的硬件微架构：先依据低位识别 16-bit 或更长指令；对 16-bit 指令依据 `XLEN`、扩展集合和字段限制选中 RVC 语义；再将其交给与 underlying `I`/`F`/`D` 指令一致的异常、寄存器和执行语义检查。这样可以避免把 RV32 的 `C.JAL` 当作 RV64 的 `C.ADDIW`，或把 `C.FLW` 当作 RV64 的 `C.LD`。INTERPRETIVE，语义依据为 T1-VERIFIED: [C Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html)

## 12. 六维总结

| 维度 | 结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| 1. Ratification & maturity | 用户指定的官方 ratified-library 快照包含 C Extension Version 2.0 | 下一次评审应重新核对目标基线，不能用 UDB 主线自动替代 | T1-VERIFIED |
| 2. ISA semantics | RVC 以 16-bit 编码表达常见基础/FP 访存操作，`IALIGN=16`，并有明确 RV32/RV64 复用 | `C.JALR` 的 link 为 `pc+2`，不可机械当作基础 `JALR` 的相同结果 | T1-VERIFIED |
| 3. Platform requirements | C 是可附加的独立 ISA 扩展；若 C 与相关 F/D 并存，相关 FP 压缩访存必须提供 | 本篇未核验任何平台 profile 或具体芯片要求 | T1-VERIFIED / UNVERIFIED |
| 4. Software evidence | 编译器可不感知 C，assembler/linker 可以参与压缩 | 本地 `-march`、relaxation、反汇编和 ELF 属性尚未实测 | T1-VERIFIED / UNVERIFIED |
| 5. Competitive anchor | 适合以混合长度、常用 ABI 寄存器和静态/动态代码密度为比较维度 | 本文不作 Arm/x86 的性能或软件生态结论 | INTERPRETIVE |
| 6. Deployment intent | 嵌入式代码尺寸、取指带宽和 I-cache miss 是规范说明的主要收益方向 | 不保证任一工作负载的速度、功耗或 cache miss 改善比例 | T1-VERIFIED / INTERPRETIVE |

## 13. 常见误解

| 误解 | 正确表述 | 标签 |
| --- | --- | --- |
| “有 C 后，程序中的所有指令都为 16 bit。” | C 允许 16-bit 与 32-bit 指令自由混排；`inst[1:0]=11` 表示宽于 16 bit 的编码空间。 | T1-VERIFIED |
| “RVC 是一套独立、只给 MCU 用的 ISA。” | RVC 是附加到 RV32I/E 或 RV64I/E 的编码扩展；规范同时把降低取指带宽和 I-cache miss 作为更广泛的设计动机。 | T1-VERIFIED |
| “`C` 自动包含浮点压缩指令。” | `C` 不包含 F/D；只有 C 与相关 F/D 同时实现时，才必须提供对应 FP load/store 压缩形式。 | T1-VERIFIED |
| “`C.JAL` 在 RV64C 中也是短 call。” | 同一码位在 RV64C 中为 `C.ADDIW`；`C.JAL` 仅为 RV32C。 | T1-VERIFIED |
| “`C.BEQZ` 能测试任意 GPR。” | 它使用 `rs1'`，仅能编码 `x8`--`x15`。 | T1-VERIFIED |
| “reserved、custom、hint 都等于非法指令。” | 指定的全零 16-bit 编码被永久定义为 illegal；RES、Custom、HINT 分别有不同的用途与可移植性边界。 | T1-VERIFIED |
| “无 instruction-address-misaligned exception 就没有对齐问题。” | 这是指令地址对齐结论；数据访存的未对齐和异常规则仍来自 underlying 指令与 EEI。 | T1-VERIFIED |
| “压缩率 25%--30% 是任何二进制都保证的结果。” | 这是规范中典型程序的统计，不是 ABI、编译器、链接布局或应用工作负载无关的保证。 | T1-VERIFIED / INTERPRETIVE |

## 14. 验证清单

- [ ] 重新打开 [C Extension 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html)、[RV32I](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)、[RV64I](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html) 与 [naming rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html)，确认课程采用的仍是 `v20260120` 基线。
- [ ] 对每个 16-bit 指令先检查 `inst[1:0]`，再按 Q0/Q1/Q2、`inst[15:13]` 和目标 `XLEN` 判定语义；不要仅靠反汇编 mnemonic 猜测。
- [ ] 对 prime 字段验证寄存器确实是 `x8`--`x15` 或相应的 `f8`--`f15`；对 SP 形式验证基址为 `x2`。
- [ ] 分别测试 RV32 和 RV64 的复用码位：`C.JAL`/`C.ADDIW`，`C.FLW`/`C.LD`，`C.FSW`/`C.SD`，以及其 SP 相对形式。
- [ ] 覆盖边界编码：`C.LWSP`/`C.LDSP` 的 `rd=x0`，`C.ADDI4SPN` 的零立即数，`C.JR rs1=x0`，`C.LUI` 的零立即数，以及 RV32C 的 `shamt[5]=1` custom 空间。
- [ ] 对控制流验证 `C.J`、`C.BEQZ`/`C.BNEZ` 的 2-byte 偏移单位与有限范围，并单测 `C.JALR` 写入 `pc+2`。
- [ ] 若使用 LR/SC，核对序列仍满足 A 扩展对 constrained LR/SC 的全部条件，并确认其中只加入允许的有效压缩 I 指令。
- [ ] 检查实际对象文件的 ISA 属性、编译选项和 `objdump -dr` 输出，确认目标确实启用了 C 且预期位置已被压缩。这一步在本文中未执行，故为 UNVERIFIED。

## 15. 参考资料

1. [RISC-V Unprivileged ISA `v20260120` index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) - 用户指定的官方固定版本入口。
2. ["C" Extension for Compressed Instructions, Version 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html) - RVC 格式、指令、RV32/RV64 复用、HINT 与 LR/SC 的主依据。
3. [RV32I Base Integer Instruction Set](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html) - 基础控制流、访存与 constrained LR/SC 上下文。
4. [RV64I Base Integer Instruction Set](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html) - RV64 `XLEN`、`ADDIW`、`LD`/`SD` 等 underlying 指令上下文。
5. [ISA Extension Naming Conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) - `C`、`G` 与 ISA string 的组合边界。
6. [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) - `norm:` 标记和规范性规则的说明。
7. [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) - Layer 1 机器可读资料的连续部署入口；不替代固定 ratified 版本。
