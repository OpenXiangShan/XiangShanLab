# RISC-V I 基础整数指令集：RV64I（`XLEN=64`）解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA `v20260120`](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html)。本文联合阅读 [RV32I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html) 与 [RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)：后者只列出相对 RV32I 的差异，不能孤立阅读。分析范围是 `RV64I` 基础整数 ISA，不把 `M`、`A`、`F/D`、`C`、`Zicsr`、`Zifencei` 或特权 ISA 当作它的隐含部分。

## 1. 定位与结论

`RV64I` 是 64-bit 的基础整数 ISA：有 32 个整数通用寄存器（GPR），每个宽度为 64 bit，即 `XLEN=64`。它继承 RV32I 的基础指令，并额外加入 64-bit load/store 与 32-bit word 运算指令；同时，原有的普通整数计算、寄存器比较、地址计算与非 `*W` 移位均按 64 bit 的 `XLEN` 运作。T1-VERIFIED: [RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

最容易混淆的结论是：**寄存器宽度为 64 bit，不意味着每一条指令都只处理 64-bit 数据。** `RV64I` 的 `*W` 指令把输入的低 32 bit 作为 word 运算，得到的低 32 bit 结果再符号扩展到 64 bit；`LW` 也对 32-bit 内存值符号扩展，而 `LWU` 才是零扩展。T1-VERIFIED: [RV64I 2.1, Integer Computational Instructions and Loads](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

| 问题 | RV64I 的准确结论 | 不应由此推出 | 标签 |
| --- | --- | --- | --- |
| 寄存器宽度 | `x0`--`x31` 均为 64 bit，`XLEN=64` | 有 64 个 GPR，或每个地址都可访问 | T1-VERIFIED |
| 地址空间 | 支持的用户地址空间扩展为 64 bit | 物理地址、虚拟地址或具体 OS 一定实现全部 `2^64` 个地址 | T1-VERIFIED |
| 普通整数计算 | 非 `*W` 算术、逻辑、比较与移位以 64-bit `XLEN` 值工作 | 整数溢出会产生异常 | T1-VERIFIED |
| `*W` 指令 | 以 32-bit word 计算，结果 bit 31 符号扩展到 bit 63 | `*W` 是“零扩展的无符号 32-bit 运算” | T1-VERIFIED |
| 基础 ISA 边界 | `I` 有整数计算、load/store、控制转移、`FENCE`、`ECALL`/`EBREAK` | `I` 自动包含乘除、原子、CSR、`FENCE.I`、浮点或压缩指令 | T1-VERIFIED |

### 1.1 术语

| 术语 | 本文含义 | 标签 |
| --- | --- | --- |
| `XLEN` | 整数寄存器宽度；RV64I 中为 64 | T1-VERIFIED |
| word / `.W` / `*W` | 32 bit 数据宽度；不是“当前寄存器宽度” | T1-VERIFIED |
| doubleword / `.D` | 64 bit 数据宽度；例如 `LD`、`SD` | T1-VERIFIED |
| RV64I | 以 RV64 为基础的 `I` 基础整数 ISA；不是一个泛称的“所有 64 位 RISC-V 功能集合” | T1-VERIFIED |
| EEI | execution environment interface，定义程序可访问的地址、I/O、异常处理等执行环境契约 | T1-VERIFIED |

## 2. 证据、版本与范围边界

### 2.1 证据优先级

| 层级 | 本次来源 | 用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1：UDB | [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 机器可读说明的导航交叉检查；连续部署产物不替代固定 ratified 语义锚点 | T1-VERIFIED |
| Layer 2：Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 解释 `norm:` 规则的规范性来源；本篇语义仍以 ratified 正文为准 | T1-VERIFIED |
| Layer 3：Ratified ISA | [RV32I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)、[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)、[RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 指令语义、格式、编码边界与 RV64I 增量的主依据 | T1-VERIFIED |
| Layer 3：Ratified profile / ABI | [RVA23 Profile v1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html)、[RISC-V ABIs v1.0](https://docs.riscv.org/reference/abi/index.html) | 区分 ISA、应用处理器 profile 与软件 ABI 的承诺 | T1-VERIFIED |
| Tier-1 comparator | [Arm A64 GPR guide](https://developer.arm.com/documentation/102374/latest/Registers-in-AArch64---general-purpose-registers) | 只用于比较 32-bit 子寄存器写回规则；不替代 RISC-V 语义 | T1-VERIFIED |

### 2.2 标签

- **T1-VERIFIED**：可直接在 RISC-V ratified 规范、profile、ABI 或指定的一级厂商资料中定位。
- **T2-CROSS-CHECKED**：可靠补充资料交叉支持；不得覆盖 T1 规范语义。
- **UNVERIFIED**：具体 CPU、固件、OS、加载器或本地工具链尚未实测。
- **INTERPRETIVE**：由规范事实归纳的阅读结论，不增加新的 ISA 保证。

### 2.3 版本锚点

| 状态 | 项目 | 对本文的含义 | 标签 |
| --- | --- | --- | --- |
| RATIFIED | RV32I 2.1 / RV64I 2.1 | 两个基础整数章节均为 ratified；RV64I 以 RV32I 为前提 | T1-VERIFIED |
| RATIFIED 2024-10-17 | RVA23 Profile v1.0 | `RVA23U64` 选用 RV64I 作为 mandatory base，但还要求许多独立扩展 | T1-VERIFIED |
| RATIFIED LIBRARY 2026-01 | Unprivileged ISA `v20260120` | 用户指定、本文固定的可复现语义快照 | T1-VERIFIED |

**SPEC-UPDATE-ALERT：** 本次检查时，官方 ratified library 仍把 Unprivileged ISA 列为 `v20260120`。RISC-V ISA Manual 的主线快照和 UDB 连续部署可能出现更晚的生成版本，但不能自动当作 ratified 替代品；在把本文用于新项目评审前，应重新检查 [ratified library](https://docs.riscv.org/reference/home/index.html) 与 [ISA Manual releases](https://github.com/riscv/riscv-isa-manual/releases)。T1-VERIFIED: [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)

## 3. 为什么必须把 RV32I 与 RV64I 合在一起读

RV64I 章节明确只描述它与 RV32I 的差异。因而，R/I/S/U/B/J 格式、整数立即数的总体规则、`JAL`/`JALR`/条件分支、基础 load/store、`FENCE`、`ECALL`/`EBREAK` 等来自 RV32I；RV64I 在这些共性之上改变 `XLEN`，加入 word 变体和 64-bit 访存。T1-VERIFIED: [RV32I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)；[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

| 类别 | RV32I 提供并由 RV64I 继承的内容 | RV64I 的关键变化 | 标签 |
| --- | --- | --- | --- |
| 程序员模型 | 32 个 `x` 寄存器、只读零寄存器 `x0`、`pc` | 每个 GPR 与支持的用户地址空间为 64 bit | T1-VERIFIED |
| 指令格式 | 固定 32-bit 基础编码，R/I/S/U 格式及 B/J 立即数变体 | 格式本身不改；寄存器与普通算术语义按 `XLEN=64` 扩展 | T1-VERIFIED |
| 普通计算 | `ADDI`、`ADD`、`SLT`、逻辑、移位等 | 普通移位量由 5 bit 变为 6 bit；加入 `*W` word 指令 | T1-VERIFIED |
| 控制转移 | `JAL`、`JALR`、六种条件分支、无架构可见 delay slot | 比较和寄存器目标按 64 bit；直接位移编码范围不因 `XLEN` 自动变大 | T1-VERIFIED |
| 访存 | byte/halfword/word load/store 及 EEI 地址规则 | 加入 `LD`、`LWU`、`SD`；`LW` 在 RV64I 中仍符号扩展 | T1-VERIFIED |

### 3.1 `I`、`G` 与独立扩展不能混写

`RV64I` 只描述基础整数能力。通用用途简称 `G` 的定义是 `IMAFDZicsr_Zifencei`，因此 `rv64g` 比 `rv64i` 多出乘除、原子、浮点、CSR 和 instruction-fetch fence 等独立内容。`C` 也只是可选压缩扩展；它能把指令对齐约束降为 `IALIGN=16`，但不改变 RV64I 的基础语义。T1-VERIFIED: [ISA naming](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html)；[RV32/64G listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)；[C extension](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html)

| 能力 | 是否由裸 `RV64I` 提供 | 正确边界 | 标签 |
| --- | --- | --- | --- |
| 整数加减、比较、逻辑、移位、load/store、分支/跳转 | 是 | 算术只含基础 I 操作；无乘除、原子或浮点 | T1-VERIFIED |
| `FENCE`、`ECALL`、`EBREAK` | 是 | 具体 I/O、服务号、陷阱处理由 EEI / 特权环境决定 | T1-VERIFIED |
| CSR 读写 | 否 | 属于 `Zicsr` | T1-VERIFIED |
| `FENCE.I` | 否 | 属于 `Zifencei`，不应由 `FENCE` 名称相近而误推 | T1-VERIFIED |
| 乘除、原子、浮点、压缩编码 | 否 | 分别需要 `M`、`A`、`F/D`、`C` 等扩展 | T1-VERIFIED |

## 4. RV64I 程序员模型：64-bit GPR，不是“所有地址均可用”

RV64I 的 `x0`--`x31` 是 32 个 64-bit 整数寄存器，`x0` 的所有位恒为零；`pc` 保存当前指令地址。基础 ISA 没有硬编码的 stack pointer 或 return-address 寄存器，任何 `x` 寄存器都可用于这些角色；标准调用约定通常取 `x1` 作返回地址、`x5` 作备用 link register、`x2` 作栈指针。这些名字是软件 ABI 约定，不是 RV64I 新增的硬件寄存器类型。T1-VERIFIED: [RV32I programmers' model](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

| 寄存器/状态 | RV64I 的 ISA 语义 | 常见 ABI 角色 | 标签 |
| --- | --- | --- | --- |
| `x0` / `zero` | 读取恒为 0；写回被丢弃 | 零值、丢弃纯计算结果 | T1-VERIFIED |
| `x1` | 普通 64-bit GPR | `ra`，常规返回地址 | T1-VERIFIED |
| `x5` | 普通 64-bit GPR | 备用 link register | T1-VERIFIED |
| `x2` | 普通 64-bit GPR | `sp`，栈指针 | T1-VERIFIED |
| `pc` | 当前指令地址 | 由控制流指令更新 | T1-VERIFIED |

“支持 64-bit 用户地址空间”只说明 RV64I 的整数寄存器和用户地址模型能表达 64-bit 地址。哪一段地址可 load/store、是否为 memory 或 I/O、是否经过虚拟地址翻译、是否映射到物理存储，都由 execution environment 与特权架构定义。因此，`XLEN=64` 不等于“实现有 64-bit 物理地址”或“每个 64-bit 位模式均可访问”。T1-VERIFIED: [RV64I load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)；[RISC-V ISA overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/intro.html)

## 5. 指令全景：继承集合与 RV64I 增量

RV32I 有 40 条独特基础指令；按 RV64I 指令列表，RV64I 在其上增加 3 条访存 mnemonic 与 9 条 `*W` mnemonic，并改变普通移位的 shift-amount 宽度。这给出 52 条基础 mnemonic 的课程计数；该数字是按两个规范表机械相加的 INTERPRETIVE 统计，不应代替具体 assembler 的伪指令、HINT 或扩展指令清单。T1-VERIFIED: [RV32I introduction](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)；[RV32/64G listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

### 5.1 RV32I 共性指令族（在 RV64I 中以 64-bit `XLEN` 解释）

| 指令族 | mnemonic | RV64I 下的核心语义 | 标签 |
| --- | --- | --- | --- |
| 立即数算术/比较/逻辑 | `ADDI`、`SLTI`、`SLTIU`、`XORI`、`ORI`、`ANDI` | 12-bit 立即数先符号扩展至 64 bit，再按该操作计算 | T1-VERIFIED |
| 立即数移位 | `SLLI`、`SRLI`、`SRAI` | 对 64-bit 值移位，立即数 shift amount 为 6 bit | T1-VERIFIED |
| 寄存器算术/比较/逻辑 | `ADD`、`SUB`、`SLT`、`SLTU`、`XOR`、`OR`、`AND` | 对 64-bit GPR 运算；有符号/无符号由指令决定 | T1-VERIFIED |
| 寄存器移位 | `SLL`、`SRL`、`SRA` | 对 64-bit 值移位，仅使用 `rs2[5:0]` | T1-VERIFIED |
| 上半立即数 / PC 相对 | `LUI`、`AUIPC` | 形成 32-bit U-immediate 后符号扩展至 64 bit | T1-VERIFIED |
| 跳转 | `JAL`、`JALR` | `JAL` 为 PC 相对；`JALR` 以寄存器加符号扩展 12-bit 偏移并清零目标 bit 0 | T1-VERIFIED |
| 条件分支 | `BEQ`、`BNE`、`BLT`、`BGE`、`BLTU`、`BGEU` | 比较完整 64-bit GPR；B-immediate 仍是以 2 bytes 为单位的短位移 | T1-VERIFIED |
| 基础 load | `LB`、`LH`、`LW`、`LBU`、`LHU` | 分别扩展 8/16/32-bit 数据至 64 bit；`LW` 为符号扩展 | T1-VERIFIED |
| 基础 store | `SB`、`SH`、`SW` | 从 `rs2` 的低 8/16/32 bit 写入内存 | T1-VERIFIED |
| 内存排序 | `FENCE` | 按 predecessor/successor 集合排序 memory 与 device I/O 访问 | T1-VERIFIED |
| 请求陷阱 | `ECALL`、`EBREAK` | 向执行环境请求精确 trap；服务与调试动作由 EEI 定义 | T1-VERIFIED |

### 5.2 RV64I 专有新增 mnemonic

| 新增指令 | 数据宽度 | 精确效果 | 易错点 | 标签 |
| --- | ---: | --- | --- | --- |
| `LD` | 64 | 从内存读取 64 bit 到 `rd` | 这才是普通 RV64I doubleword load | T1-VERIFIED |
| `LWU` | 32 -> 64 | 读取 32 bit 并零扩展到 64 bit | 与 `LW` 的符号扩展不同 | T1-VERIFIED |
| `SD` | 64 | 把 `rs2` 的低 64 bit 写入内存 | 不是 `SW` 的同义词 | T1-VERIFIED |
| `ADDIW` | 32 -> 64 | 低 32-bit 加法结果符号扩展 | `ADDIW rd, rs1, 0` 是 `SEXT.W` 伪指令 | T1-VERIFIED |
| `SLLIW`、`SRLIW`、`SRAIW` | 32 -> 64 | 立即数 word shift 后符号扩展 | `imm[5] != 0` 的编码为 reserved | T1-VERIFIED |
| `ADDW`、`SUBW` | 32 -> 64 | 低 32-bit 加/减结果符号扩展 | 不是 64-bit `ADD`/`SUB` 的别名 | T1-VERIFIED |
| `SLLW`、`SRLW`、`SRAW` | 32 -> 64 | 低 32-bit shift 后符号扩展 | 寄存器 shift amount 只取 `rs2[4:0]` | T1-VERIFIED |

`SLLI`、`SRLI`、`SRAI` 在 RV64I 中没有改名，却从 RV32I 的 5-bit shift amount 扩展到 6 bit；`SLL`、`SRL`、`SRA` 同样只看 `rs2[5:0]`。这是 RV64I 对原有 mnemonic 的语义变化，不能只按“新增的 `*W` 指令”理解 RV64I。T1-VERIFIED: [RV64I 2.1, shifts](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

## 6. 核心：64-bit XLEN 运算与 32-bit `*W` 运算

### 6.1 普通运算的宽度

除另有 `W` 后缀外，RV64I 整数计算在 64-bit `XLEN` 上进行。例如 `ADD`/`SUB` 结果取低 64 bit，`SLT`/`SLTU` 比较完整的 64-bit 寄存器值，普通移位的合法位移范围为 0--63。基础整数计算不产生算术 overflow 异常；若软件需要溢出检查，必须用比较或其他指令序列显式表达。T1-VERIFIED: [RV32I integer computation](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)；[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

| 对比 | 普通形式 | word 形式 | 结果写入 `rd` | 标签 |
| --- | --- | --- | --- | --- |
| 立即数加法 | `ADDI`：64-bit 加法 | `ADDIW`：低 32-bit 加法 | 普通形式保留低 64 bit；word 形式 `sext32(result)` | T1-VERIFIED |
| 寄存器加/减 | `ADD` / `SUB`：64-bit | `ADDW` / `SUBW`：32-bit | `*W` 一律令 bit 63:31 等于结果 bit 31 | T1-VERIFIED |
| 立即数移位 | `SLLI` / `SRLI` / `SRAI`：`shamt[5:0]` | `SLLIW` / `SRLIW` / `SRAIW`：`shamt[4:0]` | `*W` 先得 32-bit 结果，再符号扩展 | T1-VERIFIED |
| 寄存器移位 | `SLL` / `SRL` / `SRA`：`rs2[5:0]` | `SLLW` / `SRLW` / `SRAW`：`rs2[4:0]` | `*W` 忽略输入高 32 bit 并符号扩展结果 | T1-VERIFIED |

### 6.2 `*W` 的符号扩展不变量

RV64I 规范指出，编译器与调用约定维护一个不变量：寄存器中表示的 32-bit 值是符号扩展格式，**包括 32-bit unsigned 值**。所以 `0xffffffffu` 可在 64-bit 寄存器中表示成 `0xffffffffffffffff`；在此不变量成立时，64-bit `SLTU` 和无符号分支仍能正确比较两个 32-bit unsigned 值。`*W` 指令正是维持这一表示不变量的基础工具。T1-VERIFIED: [RV64I 2.1, 32-bit value invariant](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

这不是说 unsigned 值被“改成 signed”。寄存器只保存比特；有符号或无符号解释来自所选指令、语言类型和 ABI 约定。`*W` 的架构效果是复制 word 结果的 bit 31 到 bit 63:32，而 `LWU` 则把内存 word 的 bit 31 以上清零。T1-VERIFIED: [RV64I load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

~~~asm
# 假设 *(uint32_t *)a0 的内存比特为 0x80000001。
lw      t0, 0(a0)       # t0 = 0xffffffff80000001  (符号扩展)
lwu     t1, 0(a0)       # t1 = 0x0000000080000001  (零扩展)
addiw   t2, t1, 0       # t2 = 0xffffffff80000001  (SEXT.W)

# 64-bit 与 word shift 的移位量不同。
sll     t3, t4, t5     # shift amount = t5[5:0]，范围 0--63
sllw    t6, t4, t5     # shift amount = t5[4:0]，范围 0--31；结果符号扩展
~~~

上例仅展示 ISA 数据变换，不表示 `a0` 指向的地址一定可访问，也不声明任何特定 ABI 或编译器必定生成该序列。地址有效性与实际 code generation 均取决于 EEI / 工具链，故为 UNVERIFIED。各条指令的位级结果为 T1-VERIFIED: [RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

### 6.3 reserved 不等于可移植的非法指令

`SLLIW`、`SRLIW`、`SRAIW` 若 `imm[5] != 0`，在当前规范中是 **reserved**；早期文本曾把该情况写为 illegal-instruction exception，后来改成 reserved。通用软件不应依赖此类编码一定触发非法指令异常，也不应把它当作稳定的扩展空间。更一般地，RV32I 说明 reserved instruction 的解码行为是 UNSPECIFIED，具体平台可要求 trap，也可允许非标准扩展使用保留空间。T1-VERIFIED: [RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)；[RV32I instruction-format rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

## 7. 访存：`LD`、`LW`、`LWU` 与 `SD`

RV64I 仍是 load-store 架构：整数算术只操作 GPR，内存由 load/store 指令访问。有效地址由 base register 与符号扩展的 12-bit offset 相加形成；RV64I 允许用 64-bit 寄存器表达该地址，但 EEI 决定实际哪些地址、哪些访问宽度、哪些 memory/I/O 区域合法。T1-VERIFIED: [RV32I load/store model](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)；[RV64I load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

| 指令 | 读取 / 写入宽度 | RV64I 中写入 GPR 的规则 | 标签 |
| --- | ---: | --- | --- |
| `LB` / `LBU` | 8 bit load | 分别符号扩展 / 零扩展至 64 bit | T1-VERIFIED |
| `LH` / `LHU` | 16 bit load | 分别符号扩展 / 零扩展至 64 bit | T1-VERIFIED |
| `LW` | 32 bit load | 符号扩展至 64 bit | T1-VERIFIED |
| `LWU` | 32 bit load | **零扩展**至 64 bit | T1-VERIFIED |
| `LD` | 64 bit load | 原样载入 64 bit | T1-VERIFIED |
| `SB` / `SH` / `SW` / `SD` | 8 / 16 / 32 / 64 bit store | 分别取 `rs2` 的低 8 / 16 / 32 / 64 bit 写入内存 | T1-VERIFIED |

读写 `x0` 的语义也需区别。计算指令写 `x0` 没有架构可见寄存器结果，但以 `x0` 为目标的 load 仍必须报告异常并产生其他访存副作用，不能因为最终丢弃读值就被软件视为无操作。T1-VERIFIED: [RV32I load/store rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

### 7.1 对齐、端序与原子性边界

自然对齐的基础 load/store 不会产生 address-misaligned exception。未自然对齐访问是否完成、是否透明地由硬件或陷阱处理、产生 address-misaligned 还是 access-fault、以及 trap 是 contained 还是 fatal，都由 EEI 定义；即使未对齐访问成功，也可能很慢且不保证原子性。端序同样由 EEI 定义；只有特定 profile 才可能给出额外约束。T1-VERIFIED: [RV32I load/store and misalignment](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

| 层次 | 可以得出的结论 | 不可以得出的结论 | 标签 |
| --- | --- | --- | --- |
| 裸 RV64I | 自然对齐 load/store 不会因地址未对齐而异常；未对齐规则交给 EEI | 所有未对齐访问可用、快速或原子 | T1-VERIFIED |
| 裸 RV64I | EEI 选择端序和可访问地址/宽度 | 必然 little-endian，或全部 64-bit 地址均有效 | T1-VERIFIED |
| RVA23U64 | mandatory base 为 RV64I，且 profile 要求 little-endian | 任意 RV64I 实现都符合 RVA23 或拥有其所有 extensions | T1-VERIFIED |

## 8. 立即数、地址构造与控制转移

### 8.1 立即数为何仍是有限宽度

基础指令为固定 32 bit，`rs1`、`rs2`、`rd` 的位置在各格式中保持稳定。除 CSR 的 5-bit immediate 外，基础整数立即数都符号扩展，且它们的符号位位于 `inst[31]`。这使 RV64I 的 12-bit I/S immediate、20-bit U immediate、B/J 位移并不会因为 GPR 变成 64 bit 而变成 64-bit immediate。T1-VERIFIED: [RV32I instruction formats](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

| 指令 / 格式 | RV64I 中的关键行为 | 常见错误 | 标签 |
| --- | --- | --- | --- |
| `LUI` / U-type | 形成低 12 bit 为 0 的 32-bit U-immediate，再符号扩展至 64 bit | 把 `LUI` 当作一条任意 64-bit constant load | T1-VERIFIED |
| `AUIPC` / U-type | 32-bit U offset 符号扩展至 64 bit后加当前 `pc` | 认为它产生无范围限制的 64-bit PC-relative 位移 | T1-VERIFIED |
| I/S-type | 12-bit immediate 符号扩展 | 把 offset 一律当成零扩展 | T1-VERIFIED |
| B-type | 以 2-byte 单位编码的有符号条件分支位移，范围约 +/-4 KiB | 以为 64-bit GPR 使分支位移变为 64 bit | T1-VERIFIED |
| J-type | `JAL` 的 2-byte 单位有符号位移，范围约 +/-1 MiB | 以为 `JAL` 能直接跳到任意 64-bit 地址 | T1-VERIFIED |

在 RV64I 中，`LUI` 或 `AUIPC` 的 U-immediate 结果会符号扩展。规范给出，把 `LUI` 与 `LD`、或 `AUIPC` 与 `JALR` 等配对时，能构造的地址偏移集合为 `[-2^31-2^11, 2^31-2^11-1]`。更远的地址、位置无关重定位和任意 64-bit 常量需要汇编器/链接器选择更长序列；不要用“寄存器是 64 bit”替代对实际 relocation 的检查。T1-VERIFIED: [RV64I 2.1, LUI/AUIPC](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

### 8.2 控制流仍继承 RV32I 的直接编码范围

`JAL` 把 `pc+4` 写入 `rd`，使用 PC 相对 J-immediate；`JALR` 的目标为 `rs1 + sext(imm12)` 后清零最低位，再把 `pc+4` 写入 `rd`。`JALR x0, 0(x1)` 是常见的 `ret` 伪指令形式，但“返回”只是 ABI 对寄存器的约定。RV32I/RV64I 的控制转移没有架构可见 delay slot；若跳转或已取分支的目标不满足当前 `IALIGN`，异常报告在导致该目标的控制转移指令上。T1-VERIFIED: [RV32I control transfer](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

## 9. `FENCE`、环境陷阱、NOP 与 HINT

`FENCE` 是基础 `I` 的 memory-ordering 指令，可按 predecessor/successor 集合排列 device input/output（`I`/`O`）和 memory read/write（`R`/`W`）操作。它并不自动给外部设备的非内存通知机制排序，也不是 `FENCE.I` 的别名；instruction-fetch coherence 由独立 `Zifencei` 处理。T1-VERIFIED: [RV32I FENCE](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)；[Zifencei](https://docs.riscv.org/reference/isa/v20260120/unpriv/zifencei.html)

`ECALL` 和 `EBREAK` 使支持它们的 execution environment 产生精确 requested trap：前者用于请求环境服务，后者用于将控制权交给调试环境。参数放置、系统调用号、陷阱入口和恢复协议不由裸 RV64I 规定。T1-VERIFIED: [RV32I ECALL/EBREAK](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)

规范的 canonical `NOP` 编码是 `ADDI x0, x0, 0`。RV64I 保留 RV32I 的 microarchitectural HINT，并因新增计算指令扩大 HINT 编码空间；HINT 不应承载软件功能正确性，简单实现可忽略它们。T1-VERIFIED: [RV32I NOP and HINT](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)；[RV64I HINT](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)

## 10. ISA、profile、ABI 与跨架构映射

### 10.1 不同层的承诺

| 层次 | 本文可确认的事实 | 仍须单独核验 | 标签 |
| --- | --- | --- | --- |
| ISA：RV64I | `XLEN=64`、指令语义、`*W` 符号扩展、`LD/LWU/SD` | 具体核的时延、流水线、cache、总线和可访问地址 | T1-VERIFIED / UNVERIFIED |
| EEI / platform | 定义合法地址、端序、I/O、未对齐访问和陷阱处理 | 某块板卡或 OS 的实际 memory map、PMA、MMIO 协议 | T1-VERIFIED / UNVERIFIED |
| RVA23U64 | RV64I 是 mandatory base，profile 还规定许多 mandatory extensions，并要求 little-endian | 每个 RV64I 实现都满足 RVA23 | T1-VERIFIED |
| psABI | 64-bit ABI 族为 `LP64`、`LP64F`、`LP64D`、`LP64Q`；`LP64*` 与 RV64* ISA 相容 | 本地 GCC/Clang、libc、loader 的实际版本和生成汇编 | T1-VERIFIED / UNVERIFIED |

裸 `RV64I` 能作为编译目标骨架，却不是现代通用 OS 软件的完整功能集合。举例说，RVA23U64 虽以 RV64I 为 mandatory base，却还要求 `M/A/F/D/C/B`、`Zicsr`、计数器及许多 profile 扩展；不能把“某实现是 64-bit RISC-V”缩写成“它只需 RV64I”。T1-VERIFIED: [RVA23U64 mandatory base and extensions](https://docs.riscv.org/reference/rva23/rva23-profiles.html)

### 10.2 概念性竞争锚点

该表只比较可见数据宽度接口，不把不同 ISA 的异常、memory model、ABI、微架构性能或地址翻译机制视作等价。

| Feature | RISC-V（规范与状态） | Arm/x86 analogue | Platform requirement | Software evidence | Tag |
| --- | --- | --- | --- | --- | --- |
| 64-bit GPR | RV64I 有 32 个 64-bit `x` GPR，`XLEN=64` | AArch64 有 31 个 GPR，可作为 64-bit `X` 使用；x86 此处不作逐指令映射 | RV64I 只规定 ISA 状态；EEI 决定地址与访存属性 | RV64 psABI 采用 `LP64*` ABI 家族 | T1-VERIFIED |
| 32-bit 子宽度结果 | RV64I `*W` 结果**符号扩展**到 64 bit | AArch64 写 `Wn` 会把对应 `Xn` 高 32 bit 清零 | 这是 ISA 语义差异，不是优劣或性能结论 | 编译器必须按目标 ABI / ISA lowering 选择正确形式 | T1-VERIFIED / INTERPRETIVE |
| 64-bit load/store | `LD` / `SD` 是 RV64I 基础指令；`LW` 与 `LWU` 的扩展规则不同 | 只作“存在宽度选择”的概念类比，不声明指令一一对应 | 合法地址、对齐、端序由 EEI / profile 约束 | 实际对象代码须反汇编核验 | T1-VERIFIED / UNVERIFIED |

这里最有用的对照是第二行：AArch64 的 32-bit `W` 写回会零扩展，而 RV64I 的 `*W` 写回符号扩展。跨 ISA 移植汇编、JIT 或模拟器时不能只看 mnemonic 中有无 `W`，必须按目标 ISA 的完整结果规则建模。RISC-V 与 Arm 的原始事实为 T1-VERIFIED；“不能只看 mnemonic”是 INTERPRETIVE。T1-VERIFIED: [RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html)；[Arm A64 GPR guide](https://developer.arm.com/documentation/102374/latest/Registers-in-AArch64---general-purpose-registers)

## 11. 六维总结

| 维度 | 结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| 1. Ratification & maturity | RV64I 2.1 在 `v20260120` ratified library 中；RVA23 v1.0 是 ratified profile | 主线快照/连续生成物不能自动覆盖用户指定的 ratified 锚点 | T1-VERIFIED |
| 2. ISA semantics | `XLEN=64`；普通操作是 64-bit；`*W` 是低 32-bit 运算并符号扩展；新增 `LD/LWU/SD` | 64-bit GPR 不扩大直接 immediate/branch 编码范围 | T1-VERIFIED |
| 3. Platform requirements | EEI 决定合法地址、端序、未对齐处理、I/O 与陷阱；RVA23U64 进一步要求 little-endian | 裸 RV64I 不声明某个 SoC 的物理地址宽度或 MMIO 行为 | T1-VERIFIED / UNVERIFIED |
| 4. Software evidence | Ratified psABI 定义 `LP64*` ABI 家族；RV64I 规范说明 32-bit sign-extension 不变量 | 本机工具链、链接器、C 库与反汇编尚未实测 | T1-VERIFIED / UNVERIFIED |
| 5. Competitive anchor | AArch64 的 32-bit 写零扩展，与 RV64I `*W` 符号扩展不同 | 不由此比较性能、memory model 或应用适用性 | T1-VERIFIED / INTERPRETIVE |
| 6. Deployment intent | RV64I 是 64-bit RISC-V 软件栈的基础整数底座，也是 RVA23U64 的 base | 完整应用处理器目标依赖 profile 规定的扩展，而不是裸 `rv64i` | T1-VERIFIED |

## 12. 常见误解

| 误解 | 正确表述 | 标签 |
| --- | --- | --- |
| “RV64I 有 64 个 64-bit 寄存器。” | 它有 **32 个** 64-bit GPR：`x0`--`x31`。 | T1-VERIFIED |
| “`XLEN=64` 表示 64-bit 物理地址必然可用。” | RV64I 只扩展支持的用户地址空间；合法地址范围由 EEI / 平台定义。 | T1-VERIFIED |
| “`LW` 在 RV64I 中零扩展。” | `LW` 符号扩展；零扩展的 32-bit load 是 `LWU`。 | T1-VERIFIED |
| “`ADDW` 与 `ADD` 只差性能。” | 两者的架构结果不同：`ADDW` 只保留低 32 bit 并符号扩展，`ADD` 保留低 64 bit。 | T1-VERIFIED |
| “所有有 `W` 的 RISC ISA 指令都零扩展。” | RV64I `*W` 符号扩展；例如 AArch64 `W` 写回为零扩展，二者不可混用。 | T1-VERIFIED |
| “64-bit PC/寄存器意味着 `JAL` 可直达任意 64-bit 地址。” | `JAL` 仍只有 J-immediate 的约 +/-1 MiB PC-relative 范围；更远目标需序列或重定位。 | T1-VERIFIED |
| “`FENCE.I` 是 `I` 的一部分。” | `FENCE` 属于基础 I；`FENCE.I` 属于独立 `Zifencei`。 | T1-VERIFIED |
| “reserved `SLLIW` 编码必然 trap。” | 当前规范写作 reserved，通用软件不能依赖特定异常行为。 | T1-VERIFIED |
| “`rv64i` 与 `rv64g` 可互换。” | `G = IMAFDZicsr_Zifencei`，远大于裸 `I`。 | T1-VERIFIED |

## 13. 验证清单

在把本文用于新核、模拟器、汇编器、JIT、OS 或课程实验前，逐项确认：

- [ ] 重新打开 [RV32I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html)、[RV64I 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html) 与 [Unprivileged ISA index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html)，确认固定版本仍适用。
- [ ] 检查 [ratified library](https://docs.riscv.org/reference/home/index.html)、[UDB deployment](https://riscv.github.io/riscv-unified-db/) 和 [ISA Manual releases](https://github.com/riscv/riscv-isa-manual/releases)，若 ratified 版本或 normative text 更新，刷新本文的版本锚点。
- [ ] 对每个用到的整数指令区分普通 XLEN 形式与 `*W` 形式，检查 `shamt[5:0]`、`rs2[5:0]`、`shamt[4:0]`、`rs2[4:0]` 是否正确。
- [ ] 对所有 32-bit load 与中间值明确选用 `LW` 还是 `LWU`；检查 ABI 是否需要 32-bit sign-extension 不变量。
- [ ] 对 `LUI/AUIPC`、分支和 `JAL/JALR` 检查 relocation 与实际链接范围，不把 GPR 宽度当作 immediate 编码范围。
- [ ] 对 load/store 查询目标 EEI、设备树、ACPI、固件或平台手册：合法地址、端序、alignment、MMIO 副作用、trap 处理和 memory/I/O 分类。
- [ ] 若声称 RVA23 兼容，重新核验 [RVA23U64](https://docs.riscv.org/reference/rva23/rva23-profiles.html) 的 mandatory base 与完整 mandatory extension 集，不能只检查 `rv64i`。
- [ ] 用目标 assembler/LLVM/GCC 编译最小样例，保存 `objdump -dr`：至少覆盖 `LW`/`LWU`/`LD`、`ADD`/`ADDW`、普通/word shift、`LUI`/`AUIPC` 与 `JALR`。
- [ ] 把汇编器接受、链接器 relocation、模拟器执行与真实硬件运行分别记录；本篇没有针对本地工具链或任何 CPU 做运行实测，这些结论维持 UNVERIFIED。

## 14. 参考资料

1. [RISC-V Unprivileged ISA `v20260120` index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) - 用户指定的官方 ratified 版本入口。
2. [RV32I Base Integer Instruction Set, Version 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv32.html) - RV64I 继承的程序员模型、格式、控制流、访存、`FENCE` 与环境陷阱语义。
3. [RV64I Base Integer Instruction Set, Version 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv64.html) - `XLEN=64`、`*W`、64-bit shift、`LD/LWU/SD` 与 RV64I HINT 主依据。
4. [RV32/64G Instruction Set Listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) - RV64I 增量 mnemonic 与编码表。
5. [RISC-V ISA Naming Conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) - `RV64I`、`G` 与扩展命名边界。
6. [RVA23 Profile v1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html) - `RVA23U64` 的 RV64I mandatory base 与 little-endian 要求。
7. [RISC-V ABIs Specification v1.0](https://docs.riscv.org/reference/abi/index.html) - `LP64*` ABI 家族与 RV64 关联。
8. [Arm A64 general-purpose registers](https://developer.arm.com/documentation/102374/latest/Registers-in-AArch64---general-purpose-registers) - 32-bit `W` 写零扩展的对照材料。
