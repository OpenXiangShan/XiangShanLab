# RISC-V Q 标准扩展：四倍精度浮点指令解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA v20260120，Q Extension Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html)。本文以该 ratified 章节为语义锚点；Q 继承 F/D 定义的浮点状态、舍入和异常规则时，显式链接到相应章节，不把实现习惯写成 ISA 保证。

## 1. 定位与结论

Q 是 RISC-V 的 128-bit binary quad-precision 浮点标准扩展，遵循 IEEE 754-2008 算术标准。它依赖 D，因而实现 Q 的 hart 也有 F 与 D 的基础能力；Q 将 32 个浮点寄存器 `f0`-`f31` 的最大可容纳宽度扩展为 `FLEN=128`。T1-VERIFIED: [Q 2.2 正文](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html)

| 问题 | 准确结论 | 不能由此推出 | 标签 |
| --- | --- | --- | --- |
| 前置扩展 | `Q` 依赖 `D`；`D` 又依赖 `F` | 仅有 F 或 D 的实现一定支持 Q | T1-VERIFIED |
| 寄存器状态 | Q 时 `FLEN=128`，同一组 `f0`-`f31` 可保存 S、D、Q 值 | 另有一套 128-bit 浮点寄存器，或整数寄存器变为 128 bit | T1-VERIFIED |
| Q 指令格式 | 大多数浮点计算指令的 `fmt=11` 表示 `.Q` | 所有浮点格式转换或所有扩展都只需 Q 即可使用 | T1-VERIFIED |
| 位模式移动 | RV32 与 RV64 都没有 `FMV.X.Q` / `FMV.Q.X` | 可用多条整数寄存器直接拼装/拆出 Q 位模式 | T1-VERIFIED |
| `FLQ` / `FSQ` 原子性 | 仅当有效地址自然对齐且 `XLEN=128` 时，规范保证其原子执行 | RV32/RV64 上自然对齐的 16-byte 访问一定原子 | T1-VERIFIED |
| 微架构与软件 | 延迟、吞吐、异常实现路径、编译器是否生成 `.Q`、ABI 调用约定需单独验证 | Q ISA 已规定这些实现或生态行为 | UNVERIFIED |

本文讨论程序员可见的 ISA 契约。`Q` 的名称表示 IEEE binary128 格式，不等于 RISC-V 整数架构宽度为 128；RV32Q 和 RV64Q 都可以存在，但它们无法得到 `FLQ` / `FSQ` 的 128-bit 访存原子性保证。T1-VERIFIED: [Q load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-1-quad-precision-load-and-store-instructions)

## 2. 规范来源、版本与证据标签

### 2.1 五层导航结果

| 导航层级 | 本次来源 | 用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1: UDB | [UnifiedDB Q extension record](https://riscv.github.io/riscv-unified-db/resolved_spec/ext/Q.yaml) | 机器可读交叉检查 Q 2.2.0、`ratified`、依赖 D、`FLEN=128`，以及 Q 指令清单 | T1-VERIFIED |
| Layer 2: Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 解释规范正文中 `norm:` 锚点的约束性质 | T1-VERIFIED |
| Layer 3: Ratified ISA | [v20260120 Q 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html) | 本文的主要语义依据 | T1-VERIFIED |
| Layer 3: Ratified ISA | [v20260120 D 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html) / [F 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html) | Q 所引用的 NaN boxing、浮点状态、异常、舍入和 D 类比语义 | T1-VERIFIED |

本文标签含义：

- **T1-VERIFIED**：可在 RISC-V ratified 规范正文、规范性规则，或官方 UnifiedDB 记录中定位。
- **T2-CROSS-CHECKED**：可靠的补充来源支持，但不覆盖 T1 结论。
- **UNVERIFIED**：特定实现、平台、工具链、OS 或 ABI 没有本地证据；不得写成既成事实。

**SPEC-UPDATE-ALERT：** 本文固定用户给出的 `v20260120` 文档快照。当前 [UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) 是连续部署产物，不能替代该版本固定的 ratified 语义锚点；在新的评审节点前，应重新核查两者以及 [RVI ratified specifications library](https://docs.riscv.org/reference/home/index.html)。T1-VERIFIED。

### 2.2 版本和范围边界

| 项目 | 本文范围 | 边界 | 标签 |
| --- | --- | --- | --- |
| Q 版本 | Q Extension Version 2.2，文档快照 `v20260120` | 不从旧草案或未固定的网页推导不同语义 | T1-VERIFIED |
| 体系结构 | RV32 与 RV64 的共同 Q 语义，单列 RV64-only 整数转换 | 不讨论不存在于 RV32/RV64 的 `XLEN=128` ISA 配置 | T1-VERIFIED |
| 浮点格式 | Q、以及与 F/D 的 S/D 转换和 recursive NaN boxing | H 转换还要求 Zfhmin；不能因有 Q 自动推出 Zfhmin | T1-VERIFIED |
| 特权态和上下文切换 | 只说明 Q 使用现有浮点寄存器状态 | OS 保存恢复策略、`mstatus.FS` 管理和 ABI 细节不在 Q 章节中 | UNVERIFIED |
| 实现与性能 | 不假定硬件 datapath、寄存器文件物理宽度或指令时序 | 这些是实现选择，而非 Q 语义 | UNVERIFIED |

本文的范围是用户级 ISA 的 Q 2.2 语义，目标架构分别记为 `RV32Q` / `RV64Q`；没有把 Q 归入某个具体 profile，也没有声称 RVA23、Server Platform 或 Server SoC 对 Q 有 MUST/SHOULD/MAY 要求。若项目要采用某个 profile 或平台规范，应在该规范版本中另行确认 Q 是否被列入。UNVERIFIED: 本文未指定 profile 或 platform specification。

### 2.3 成熟度时间线

| 状态 | 时间/版本 | 证据和解释 | 标签 |
| --- | --- | --- | --- |
| `RATIFIED` | 2019-04 | 当前 UnifiedDB `Q.yaml` 的 `Q 2.2.0` 元数据记录；用户指定的 Q 网页显示 Version 2.2 且位于 Ratified Specifications Library，但页面本身不显示日期 | T1-VERIFIED |
| `RATIFIED LIBRARY` | `v20260120` | 本文固定的 RISC-V Unprivileged ISA 文档快照，Q 章节为 Version 2.2 | T1-VERIFIED |
| `PUBLIC REVIEW` / `DRAFT` / `RATIFICATION PLAN` | 不适用 | 本文没有把 Q 当前 ratified 版本重新标成草案；未来版本仍需重新检查官方库 | T1-VERIFIED |

## 3. Q 的状态模型：先看依赖，再看 FLEN

### 3.1 扩展依赖和寄存器容量

`Q -> D -> F` 是 ISA 扩展依赖链。F 定义 32 个浮点寄存器和 `fcsr`，D 将其最大宽度提升到 64 bit，Q 进一步使 `FLEN=128`。因此 Q 不是只添加若干 `.Q` 助记符，而是对同一 `f0`-`f31` 建立 128-bit 最大浮点状态。T1-VERIFIED: [Q 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html)；[D register state](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-1-d-register-state)；[F register state](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-1-f-register-state)

| 已实现的最高标准格式 | `FLEN` | 一个 f 寄存器可保存的标准浮点格式 |
| --- | ---: | --- |
| F | 32 | S |
| D（含 F） | 64 | S、D |
| Q（含 D、F） | 128 | S、D、Q |

这里的 `FLEN=128` 是浮点寄存器的架构可见最大宽度；它不改变 `XLEN`，也不使 32-bit 指令编码容纳 128-bit 整数操作数。T1-VERIFIED: [D register state](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-1-d-register-state)

### 3.2 Recursive NaN boxing

当窄格式值保存在更宽的 f 寄存器中，合法 NaN-boxed 的值位于低 `n` bit，上方 `FLEN-n` bit 必须全部为 1。Q 将 D 的规则递归扩展：一个 S 值先被 box 到 D，再将这个 D 值 box 到 Q。作为更宽格式观察时，合法窄值呈现为负 quiet NaN。T1-VERIFIED: [Q 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html)；[D NaN boxing](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)

```text
FLEN = 128

Q value:  [127:0]       = binary128 value
D value:  [127:64] = all 1s, [63:0]  = binary64 value
S value:  [127:64] = all 1s, [63:32] = all 1s, [31:0] = binary32 value
```

普通的窄格式浮点运算会检查其输入是否正确 NaN-boxed；若不正确，该操作数按对应窄格式的 canonical NaN 对待。另一方面，`FL<n>`、`FS<n>` 和 `FMV.<n>.X` / `FMV.X.<n>` 等传输操作把窄值写入 f 寄存器时建立合法 boxing，从 f 寄存器传出时则只取低 `n` bit。这个规则解释了为何不能把上部位随意当作可计算的“额外 payload”。T1-VERIFIED: [D NaN boxing](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)

### 3.3 Q 继承的 `fcsr`、舍入和异常边界

Q 不另设一套控制/状态 CSR。对于需要舍入的 `.Q` 算术和转换，`rm=111` 选择 `fcsr.frm` 动态舍入模式，其他合法编码选择静态舍入模式；异常以 `fflags` 中的 NV、DZ、OF、UF、NX 累积记录。基础 F/D/Q 语义不因置位 `fflags` 自动产生浮点 trap。`FMIN.Q` / `FMAX.Q`、比较和符号注入指令虽然也占用 `rm` 字段，却按各自指令定义把它用于操作选择或固定编码，而不是一概当作舍入模式。T1-VERIFIED: [F fcsr](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-2-floating-point-control-and-status-register)；[F rounding modes](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#norm:dyn_round_enc)；[Q computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-2-quad-precision-computational-instructions)

`Q` 正文将大部分指令定义为 D 对应指令的类比，因此下面的“类比”是对既有 F/D 浮点语义的继承，不是可以忽略 NaN、舍入或异常规则的简写。T1-VERIFIED: [Q computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-2-quad-precision-computational-instructions)

## 4. 指令编码和全景

### 4.1 `fmt=11` 与 Q 访存宽度

对于大多数浮点计算指令，`fmt` 的 2-bit 编码如下；Q 新增 `11`，助记符为 `.Q`。T1-VERIFIED: [Q format field](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-2-quad-precision-computational-instructions)

| `fmt` | 后缀 | 浮点格式 |
| ---: | --- | --- |
| `00` | `.S` | 32-bit single precision |
| `01` | `.D` | 64-bit double precision |
| `10` | `.H` | 16-bit half precision |
| `11` | `.Q` | 128-bit quad precision |

`FLQ` / `FSQ` 分别是 `LOAD-FP` / `STORE-FP` 的 128-bit 变体，使用新的 `funct3` width 值；官方编码图给出 width=`Q`，即 `funct3=100`。它们仍采用基址加 12-bit signed byte offset 的加载/存储形状。T1-VERIFIED: [Q load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-1-quad-precision-load-and-store-instructions)；[UDB FLQ record](https://riscv.github.io/riscv-unified-db/resolved_spec/inst/Q/flq.yaml)

```text
FLQ:  imm[11:0] | rs1 | 100 | rd  | 0000111  (LOAD-FP)
FSQ:  imm[11:5] | rs2 | rs1 | 100 | imm[4:0] | 0100111  (STORE-FP)
```

### 4.2 按指令族理解 Q

| 指令族 | Q 形式 | 主要结果或作用 | 标签 |
| --- | --- | --- | --- |
| 访存 | `FLQ`、`FSQ` | 在 f 寄存器与内存间传输 128-bit 原始位模式 | T1-VERIFIED |
| 基本算术 | `FADD.Q`、`FSUB.Q`、`FMUL.Q`、`FDIV.Q`、`FSQRT.Q` | Q 操作数到 Q 结果 | T1-VERIFIED |
| 最小/最大 | `FMIN.Q`、`FMAX.Q` | 按 D 对应指令的规则选择 Q 值 | T1-VERIFIED |
| 融合乘加 | `FMADD.Q`、`FMSUB.Q`、`FNMSUB.Q`、`FNMADD.Q` | Q 格式 FMA，使用 R4 形式的 `rs3` | T1-VERIFIED |
| 整数转换 | `FCVT.W[U].Q`、`FCVT.Q.W[U]`；RV64 再有 `FCVT.L[U].Q`、`FCVT.Q.L[U]` | Q 与 32/64-bit 有符号或无符号整数之间转换 | T1-VERIFIED |
| 浮点格式转换 | `FCVT.S.Q`、`FCVT.Q.S`、`FCVT.D.Q`、`FCVT.Q.D` | Q 与 S/D 之间转换 | T1-VERIFIED |
| 符号注入 | `FSGNJ.Q`、`FSGNJN.Q`、`FSGNJX.Q` | Q 位模式的 sign bit 选择/反相/XOR | T1-VERIFIED |
| 比较 | `FEQ.Q`、`FLT.Q`、`FLE.Q` | 整数寄存器得到 0 或 1 | T1-VERIFIED |
| 分类 | `FCLASS.Q` | 整数寄存器得到 10-bit one-hot 分类掩码 | T1-VERIFIED |

表中是按主规范章节的指令族归纳，而非把每一条 `.Q` 都重新定义一次。各类操作的具体 NaN、舍入、符号零和异常行为应沿用对应 F/D 规则；只要需要确认某个 `funct5`、`rm` 或 `rs2` 固定字段，应以官方编码图或 [UDB instruction records](https://riscv.github.io/riscv-unified-db/resolved_spec/index.yaml) 为准。T1-VERIFIED。

## 5. Q 访存：128 bit 传输不等于 RV64 原子访问

### 5.1 `FLQ` / `FSQ` 的数据语义

`FLQ` 从内存读取 128 bit 到浮点目的寄存器，`FSQ` 将浮点源寄存器的 128 bit 写入内存。两条指令不会修改传输中的位，特别是不会 canonicalize 非 canonical NaN 的 payload。T1-VERIFIED: [Q load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-1-quad-precision-load-and-store-instructions)

这使它们可用于 Q 位模式的存取与保存，但不等价于可以经整数寄存器直接取得完整 Q 位模式；在 RV32/RV64 上，后者没有 `FMV.X.Q` / `FMV.Q.X` 这样的单条指令。T1-VERIFIED: [Q convert and move](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#quad-compute)

### 5.2 原子性条件的精确含义

规范只在两个条件同时满足时保证 `FLQ` / `FSQ` 原子执行：有效地址自然对齐，且 `XLEN=128`。在 RV32 和 RV64 上，即使地址按 16 byte 对齐，Q 章节也没有承诺该访问对并发观察者不可分割。T1-VERIFIED: [Q load/store](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-1-quad-precision-load-and-store-instructions)

| 场景 | 可以说的结论 | 不可说的结论 |
| --- | --- | --- |
| `XLEN=128` 且地址自然对齐 | `FLQ` / `FSQ` 有规范保证的原子执行 | 任意未对齐 Q 访问也原子 |
| RV64，地址 16-byte 对齐 | 可以执行的实现依赖于执行环境和实现；Q 本身不额外保证 128-bit 原子性 | RV64 天然保证单次 16-byte 原子 load/store |
| RV32，地址 16-byte 对齐 | 同样没有 Q 给出的 128-bit 原子性保证 | 可用 Q 访存替代明确的并发同步原语 |
| 非 canonical NaN payload | 传输位不变 | 算术结果也必然保留该 payload |

未对齐访问由执行环境决定如何处理；缓存行大小、总线事务拆分、对齐 trap 类型和并发可见性都不属于 Q 的指定内容。UNVERIFIED。

## 6. Q 计算、转换与位操作

### 6.1 计算类指令的继承关系

Q 的算术、最小/最大和 FMA 指令定义为 D 对应指令的类比：它们读取 Q 操作数，产生 Q 结果。`FADD.Q` 等使用 `OP-FP` 形式，FMA 使用含 `rs3` 的 R4 形式；二者的 `fmt` 都选择 Q。T1-VERIFIED: [Q computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-2-quad-precision-computational-instructions)

这意味着下列 F/D 规则继续重要：需要舍入的结果依指定舍入模式产生；异常更新累积 `fflags`；FMA 是其相应融合操作而不是先单独舍入乘积的两条指令替代。不要从“类比 D”误读成这些指令只是把低 64 bit 当作 D 来计算。T1-VERIFIED: [D computational instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#21-1-4-double-precision-floating-point-computational-instructions)；[F fcsr](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html#20-1-2-floating-point-control-and-status-register)

### 6.2 整数与 Q 之间的转换

| 指令 | 方向 | RV32 | RV64 | 舍入要点 |
| --- | --- | --- | --- | --- |
| `FCVT.W.Q` / `FCVT.WU.Q` | Q -> signed/unsigned 32-bit integer | 有 | 有 | 按转换规则和 `rm` |
| `FCVT.Q.W` / `FCVT.Q.WU` | signed/unsigned 32-bit integer -> Q | 有 | 有 | 32-bit 整数可精确表示为 Q |
| `FCVT.L.Q` / `FCVT.LU.Q` | Q -> signed/unsigned 64-bit integer | 无 | 有 | RV64-only，按转换规则和 `rm` |
| `FCVT.Q.L` / `FCVT.Q.LU` | signed/unsigned 64-bit integer -> Q | 无 | 有 | RV64-only，始终精确，不受舍入模式影响 |

Q 正文明确给出 W/L 和 U 变体，以及 L/LU 形式仅限 RV64；尤其 `FCVT.Q.L[U]` 始终精确且不受 rounding mode 影响。不要把这个特殊规则扩展到一般的 Q->整数或 Q<->窄浮点转换。T1-VERIFIED: [Q convert and move](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#quad-compute)

### 6.3 Q 与其他浮点格式的转换

Q 正文直接定义以下与 S/D 的双向转换：`FCVT.S.Q`、`FCVT.Q.S`、`FCVT.D.Q`、`FCVT.Q.D`。Q 到 S/D 的窄化路径需要按 `rm` 舍入；对有限数值而言，S/D 到 Q 是扩宽转换，输入值可在 Q 中精确表示，但 NaN、异常标志和保留编码仍应按 F/D 的转换规则核查。T1-VERIFIED: [Q convert and move](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#quad-compute)；[D conversion rules](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)

当前 UnifiedDB 指令记录还列出 `FCVT.H.Q` / `FCVT.Q.H`，并明确要求 `Q` 与 `Zfhmin` 两个扩展。这是对“Q 不自动蕴含 H/Zfhmin”的机器可读交叉检查；用户指定的 Q 2.2 章节正文只直接列出 S/D 转换，实际使用 H 形式前必须同时确认目标 ISA 包含 Zfhmin。T1-VERIFIED: [UDB FCVT.H.Q](https://riscv.github.io/riscv-unified-db/resolved_spec/inst/Q/fcvt.h.q.yaml)；[UDB FCVT.Q.H](https://riscv.github.io/riscv-unified-db/resolved_spec/inst/Q/fcvt.q.h.yaml)

### 6.4 符号注入与缺失的整数位移动

`FSGNJ.Q`、`FSGNJN.Q`、`FSGNJX.Q` 采用与 D 相同的规则：从 `fs1` 取除 sign bit 外的所有位，分别使用 `fs2` 的 sign bit、其反相或两者 sign bit 的 XOR 作为结果 sign bit。它们不置浮点异常标志，也不 canonicalize NaN。T1-VERIFIED: [Q convert and move](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#quad-compute)；[UDB FSGNJ.Q](https://riscv.github.io/riscv-unified-db/resolved_spec/inst/Q/fsgnj.q.yaml)

与 S/D 不同，RV32 和 RV64 没有 `FMV.X.Q` 或 `FMV.Q.X`。完整 Q 位模式需要通过内存与整数寄存器之间转移；这里的“经内存”是 Q 规范明确给出的接口边界，不能擅自设计成可移植的寄存器拆分 ABI。T1-VERIFIED: [Q convert and move](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#quad-compute)；UNVERIFIED: 具体 ABI 和汇编器扩展。

## 7. 比较与分类

### 7.1 比较写入整数寄存器

`FEQ.Q`、`FLT.Q`、`FLE.Q` 按 D 对应比较指令的语义在 Q 操作数上工作，结果写入整数寄存器的 0 或 1。比较的 NaN 行为不能简单概括为“遇到 NaN 全都一样”：例如 `FEQ.Q` 是 quiet comparison，任一输入为 NaN 时结果为 0，只有 signaling NaN 才置 NV；其余比较的精确异常规则应以对应 F/D 规范为准。T1-VERIFIED: [Q compare](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#22-1-4-quad-precision-floating-point-compare-instructions)；[UDB FEQ.Q](https://riscv.github.io/riscv-unified-db/resolved_spec/inst/Q/feq.q.yaml)

### 7.2 `FCLASS.Q` 的 one-hot 掩码

`FCLASS.Q` 与 D 对应分类指令相同，但检查 Q 值。它在整数目的寄存器中写入 10-bit one-hot 掩码，恰有一位为 1，并且不置浮点异常标志。T1-VERIFIED: [Q classify](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html#quad-float-compare)；[UDB FCLASS.Q](https://riscv.github.io/riscv-unified-db/resolved_spec/inst/Q/fclass.q.yaml)

| 位 | Q 值类别 |
| ---: | --- |
| 0 | `-infinity` |
| 1 | negative normal |
| 2 | negative subnormal |
| 3 | `-0` |
| 4 | `+0` |
| 5 | positive subnormal |
| 6 | positive normal |
| 7 | `+infinity` |
| 8 | signaling NaN |
| 9 | quiet NaN |

分类可用于软件分支和诊断；它不是异常清除、NaN canonicalization 或位模式搬运指令。T1-VERIFIED。

## 8. 六维映射与实现边界

### 8.1 跨架构、平台和软件映射

下表只把本次已核对的 Q ISA 事实放入 RISC-V 一列；Arm/x86 对照、具体 profile/platform 和工具链支持没有在本任务中取得一手证据，因此明确保留为 `UNVERIFIED`，不把“都有某种四倍精度实现”写成等价性结论。

| Feature | RISC-V（规范与状态） | Arm/x86 analogue | Platform requirement | Software evidence | Tag |
| --- | --- | --- | --- | --- | --- |
| IEEE binary128 算术 | Q 2.2 ratified；依赖 D，`FLEN=128`，提供 `.Q` 计算族 | 未在本任务中核对 Arm ARM 或 Intel/AMD 手册中的对应 ISA/状态 | 未指定 RVA23 或 Server Platform/SoC 版本；不声明 MUST/SHOULD/MAY | 未核对 GCC/LLVM/libquadmath 或 ABI 版本 | T1-VERIFIED / UNVERIFIED |
| 128-bit 浮点访存 | `FLQ` / `FSQ` 为 Q 的 LOAD-FP/STORE-FP 变体；自然对齐且 `XLEN=128` 才保证原子性 | 未核对 Arm/x86 的 128-bit 浮点 load/store 原子性契约 | RV32/RV64 上 Q 本身不保证 16-byte 原子访问 | 未核对 OS/编译器对 `FLQ` / `FSQ` 的生成和展开 | T1-VERIFIED / UNVERIFIED |
| 窄格式寄存器表示 | S-in-D-in-Q recursive NaN boxing；窄操作检查高位全 1 | 未核对 Arm/x86 的寄存器 NaN-boxing 规则 | 仅适用于实现了对应 F/D/Q 状态的 hart；没有额外平台要求 | 未核对上下文切换、调试器和 ABI 的保存格式 | T1-VERIFIED / UNVERIFIED |
| Q 位模式搬运 | RV32/RV64 没有 `FMV.X.Q` / `FMV.Q.X`，完整位模式经内存转移 | 未核对 Arm/x86 的等价跨寄存器搬运指令 | 具体内存通路和对齐由执行环境决定 | 未核对汇编器伪指令或 ABI 约定 | T1-VERIFIED / UNVERIFIED |
| Q 与整数/浮点格式转换 | W/L、U 变体和 S/D 双向转换；L/LU 两方向 RV64-only，`FCVT.Q.L[U]` 精确且不受 `rm` 影响 | 未核对对应 Arm/x86 指令、异常和 ABI 映射 | Q 章节不定义 profile 的启用级别 | 未核对编译器合法 `-march`、运行库和异常测试 | T1-VERIFIED / UNVERIFIED |

### 8.2 六维结论

| 维度 | Q 的规范事实 | 本文不作的外推 | 标签 |
| --- | --- | --- | --- |
| 成熟度 | Q 2.2 为 ratified；UnifiedDB 记录 Q 2.2.0 / ratified / 2019-04 | 某个产品或 profile 必然包含 Q | T1-VERIFIED / UNVERIFIED |
| ISA 语义 | binary128、依赖 D、`FLEN=128`、`.Q` 指令族及访存边界 | 固定执行周期或硬件结构 | T1-VERIFIED / UNVERIFIED |
| 平台 | 仅 `XLEN=128` 加自然对齐获得 `FLQ` / `FSQ` 原子性保证 | RV32/RV64 的 16-byte 原子访问、内存系统支持 | T1-VERIFIED / UNVERIFIED |
| 软件 | 可由 `FCVT`、`FLQ` / `FSQ` 与 Q 计算指令表达规范语义 | GCC/LLVM/libm 版本已经默认生成或完整支持 Q | T1-VERIFIED / UNVERIFIED |
| 竞争对照 | Q 是 RISC-V binary128 ISA 格式 | 不在未查询一手 Arm/x86 文档时宣称等价、性能相同或 ABI 兼容 | UNVERIFIED |
| 部署意图 | 标准 ISA 允许实现提供四倍精度算术 | 具体产品会采用软浮点、硬件 Q datapath 或特定寄存器切分方案 | T1-VERIFIED / UNVERIFIED |

## 9. 常见误区

| 误区 | 正确理解 |
| --- | --- |
| “RV64Q 的 Q 访存天然是 128-bit 原子操作。” | Q 只在自然对齐且 `XLEN=128` 时保证 `FLQ` / `FSQ` 原子执行；RV64 不满足该 XLEN 条件。 |
| “Q 有独立的 128-bit f 寄存器编号。” | Q 使用既有 `f0`-`f31`，改变的是其最大架构可见宽度 `FLEN=128`。 |
| “有 Q 就能使用 H 相关转换。” | `FCVT.H.Q` / `FCVT.Q.H` 还要求 Zfhmin；Q 主章节直接列出的双向转换是 S/D。 |
| “可以用 `FMV.X.Q` 直接读出 binary128 位模式。” | RV32/RV64 均没有 `FMV.X.Q` / `FMV.Q.X`；规范要求通过内存与整数寄存器转移。 |
| “NaN boxing 只影响调试显示。” | 窄格式计算会检查 boxing；无效 boxing 的输入按对应窄格式 canonical NaN 对待。 |
| “`.Q` 只是 `.D` 的低 64 bit 计算。” | `.Q` 使用 128-bit quad-precision 操作数和结果，且 `fmt=11` 专门选择 Q。 |

## 10. 面向实现和验证的检查清单

1. 确认 ISA 声明同时具有 F、D、Q，而不是只从某个 `.Q` 助记符或寄存器宽度推断。
2. 对 f 寄存器状态验证 `FLEN=128`，并覆盖 S-in-D-in-Q 的 recursive NaN boxing 及 invalid-box 输入路径。
3. 覆盖 `FLQ` / `FSQ` 的全 128-bit 原样传输，特别是非 canonical NaN payload；不要把这些测试与算术 NaN 传播测试混为一类。
4. 在 RV32/RV64 并发测试中，不把 16-byte 对齐的 `FLQ` / `FSQ` 当作有规范保证的原子操作；若需要该性质，单独核查平台与同步设计。
5. 覆盖 `fmt=11` 的算术、FMA、S/D 转换、W/L 整数转换、比较和分类；确认 L/LU 转换在 RV32 被拒绝或不可用。
6. 覆盖 `FCVT.Q.L[U]` 的精确且不受 `rm` 影响这一特殊规则，并将它和其他受舍入影响的转换分开测试。
7. 确认没有错误生成或解码 `FMV.X.Q` / `FMV.Q.X`；完整 Q 位模式的 RV32/RV64 路径应经内存设计和测试。
8. 下一次评审前重新检查 [Q 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html)、[D NaN boxing](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html#nanboxing)、[UnifiedDB Q record](https://riscv.github.io/riscv-unified-db/resolved_spec/ext/Q.yaml) 与 [RVI ratified library](https://docs.riscv.org/reference/home/index.html)。

## 11. 小结

Q 以 `Q -> D -> F` 的依赖链，把标准浮点寄存器状态扩展到 `FLEN=128`，并通过 `.Q` 形式提供 binary128 的访存、计算、转换、符号注入、比较和分类。阅读或实现时最重要的边界有三个：recursive NaN boxing、RV32/RV64 中缺少整数-Q 位移动，以及 `FLQ` / `FSQ` 只有在自然对齐且 `XLEN=128` 时才有规范保证的原子性。其余性能、ABI 和具体平台内存行为必须从相应实现或平台资料中另行验证。

## 12. 参考资料

1. [RISC-V Unprivileged ISA v20260120: Q Extension for Quad-Precision Floating-Point, Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/q-st-ext.html)
2. [RISC-V Unprivileged ISA v20260120: D Extension for Double-Precision Floating-Point, Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/d-st-ext.html)
3. [RISC-V Unprivileged ISA v20260120: F Extension for Single-Precision Floating-Point, Version 2.2](https://docs.riscv.org/reference/isa/v20260120/unpriv/f-st-ext.html)
4. [RISC-V UnifiedDB: Q extension record](https://riscv.github.io/riscv-unified-db/resolved_spec/ext/Q.yaml)
5. [RISC-V documentation library](https://docs.riscv.org/reference/home/index.html)
