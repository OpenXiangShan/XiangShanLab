# RISC-V Zc* 代码尺寸缩减扩展（Version 1.0.0）解析

> 规范基线：用户指定的 [RISC-V Unprivileged ISA `v20260120`](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) 中的 ["Zc*" Extension for Code Size Reduction, Version 1.0.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html)。本文只讨论该固定 ratified-library 快照的 `Zc*` 组合、编码和架构语义；不把某个芯片的 `misa` 实现、profile、编译器版本或链接器 relaxation 行为当作规范已保证的事实。

## 1. 定位与核心结论

`Zc*` 是一组代码尺寸缩减扩展：`Zca`、`Zcf`、`Zcd` 将既有 `C` 扩展的整数和浮点压缩部分拆分命名，`Zcb`、`Zcmp`、`Zcmt` 则新增仅有 16-bit 编码的指令；`Zce` 是面向微控制器的组合包。它不是 `C` 的新版本，也不意味着实现任一 `Zc*` 名称就拥有全部 `C` 或全部其他 `Zc*` 指令。T1-VERIFIED: [Zc* Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-1-zc-overview)

| 问题 | 准确结论 | 不应由此推出 | 标签 |
| --- | --- | --- | --- |
| 与 `C` 的关系 | `C` 的内容可由 `Zca` 与条件性的 `Zcf`/`Zcd` 表达；`Zcb`、`Zcmp`、`Zcmt` 是额外扩展 | `C` 自动包含全部 `Zc*` | T1-VERIFIED |
| 新增编码 | `Zcb`、`Zcmp`、`Zcmt` 的指令均使用 16-bit 编码 | 每条都只是单条 32-bit 指令的一对一别名 | T1-VERIFIED |
| 主要部署 | `Zcb` 面向可广泛实现的简单操作；`Zcmp`、`Zcmt` 主要面向嵌入式 CPU | 所有 application/RVA profile 都可使用 `Zcmp`、`Zcmt` | T1-VERIFIED |
| 配置约束 | `Zcmp`、`Zcmt` 重用部分 `c.fsdsp` 编码，故各自与 `Zcd` 冲突 | 已有 `C+D` 或显式 `Zcd` 的配置不可无条件追加 `Zcmp` 或 `Zcmt` | T1-VERIFIED |
| 代码尺寸 | 新扩展覆盖更多常见小操作、函数序言/尾声和表跳转 | 任意程序必然缩小，或运行速度必然提高 | T1-VERIFIED / INTERPRETIVE |

`Zc*` 所谓“弥补 `C` 的不足”，应理解为**代码密度覆盖和可组合性**的补足，而不是 `C` 的 ISA 正确性缺陷：`C` 已定义的指令语义仍然完整；`Zcb` 使用目前保留的 16-bit 编码覆盖原本没有短编码的常见模式，`Zcmp`/`Zcmt` 则复用与 `Zcd` 互斥的 `c.fsdsp` 编码槽；`Zca`/`Zcf`/`Zcd` 负责把浮点压缩访存从整数核心中拆分命名。T1-VERIFIED / INTERPRETIVE: [C Extension 2.0, Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html#27-1-1-overview)；[Zc* Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-1-zc-overview)

## 2. 证据、版本与范围边界

### 2.1 证据优先级

| 层级 | 本次来源 | 用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1：UDB | [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 机器可读资料和连续部署版本的交叉检查；不替代固定规范的语义锚点 | T1-VERIFIED |
| Layer 2：Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 说明 `norm:` 锚点标识合规实现必须满足的架构可见行为 | T1-VERIFIED |
| Layer 3：Ratified ISA | [Zc* 1.0.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html)、[C 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html) | Zc* 组合、编码、依赖、冲突与 `C` 覆盖边界的主依据 | T1-VERIFIED |
| Layer 3：命名规则 | [ISA Extension Naming Conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) | ISA string 的 base、`C` 和 `Z*` 命名背景 | T1-VERIFIED |

### 2.2 标签

- **T1-VERIFIED**：可直接在本章指定的 RISC-V ratified 规范或规范性资料中定位。
- **INTERPRETIVE**：由规范事实归纳出的设计、审阅或教学方法，不增加新的 ISA 保证。
- **UNVERIFIED**：本地 SoC、profile、OS、assembler、linker、compiler 或反汇编输出尚未实测。

### 2.3 版本锚点

| 项目 | 本文采用的结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| Zc* 章节 | `"Zc*" Extension for Code Size Reduction, Version 1.0.0` | 以用户指定的 `v20260120` 页面为准 | T1-VERIFIED |
| 页面状态 | `v20260120` 是官方 Unprivileged ISA release，ratified library 当前入口列出该版本 | 不能用未固定的网页内容默默覆盖本文基线 | T1-VERIFIED |
| UDB | UDB 是从主线持续生成的机器可读资料 | 连续部署产物可能比固定 ratified 快照更新 | T1-VERIFIED |
| 后续课程 | `Zca`、`Zcf`、`Zcd`、`Zcb`、`Zcmp`、`Zcmt`、`Zce` 都在本文范围 | 不把 `Zc*` 以外的 `Z*` 扩展或厂商 `X*` 扩展归入本章 | T1-VERIFIED |

**SPEC-UPDATE-ALERT：** 本文核对的是 `v20260120` 的固定官方页面，而 UnifiedDB 是随 `main` 连续构建的资料。下次课程或项目评审前，应重新检查 [ratified library](https://docs.riscv.org/reference/home/index.html)、[Zc* 1.0.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html) 和 [UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)，并显式记录是否更换规范基线。T1-VERIFIED

## 3. Zc* 家族、依赖与冲突

| 扩展 | 内容 | 直接依赖/适用性 | 关键边界 | 标签 |
| --- | --- | --- | --- | --- |
| `Zca` | 原 `C` 中除全部压缩 FP load/store 之外的部分 | 整数压缩核心 | 不含 `c.flw*`、`c.fsw*`、`c.fld*`、`c.fsd*` | T1-VERIFIED |
| `Zcf` | `c.flw`、`c.flwsp`、`c.fsw`、`c.fswsp` | `Zca + F`，仅 RV32 | 不可指定给 RV64 | T1-VERIFIED |
| `Zcd` | `c.fld`、`c.fldsp`、`c.fsd`、`c.fsdsp` | `Zca + D` | 与 `Zcmp`、`Zcmt` 冲突 | T1-VERIFIED |
| `Zcb` | byte/halfword 访存、扩展、取反、乘法等简单 16-bit 操作 | `Zca`，部分指令还需 B/M 家族 | 实际可用指令取决于各自前提 | T1-VERIFIED |
| `Zcmp` | `cm.push`/`cm.pop`/`cm.popret`/`cm.popretz` 和双寄存器 move | `Zca` | 重用 `c.fsdsp` 部分编码；不兼容 `Zcd` 与 application-class profiles | T1-VERIFIED |
| `Zcmt` | `cm.jt`、`cm.jalt` 和 `jvt` CSR | `Zca + Zicsr` | 重用 `c.fsdsp` 部分编码；不兼容 `Zcd` 与 RVA profiles | T1-VERIFIED |
| `Zce` | 面向微控制器的 Zc 组合包 | 依 RV32/RV64 与 `F` 选择不同成员 | 不是完整 `C` 的同义词，也不包含 `Zcd` | T1-VERIFIED |

规范将 `Zca`、`Zcf`、`Zcd` 描述为既有 `C` 的子集，将其他成员描述为仅含 16-bit 编码的新扩展。`Zcmp` 与 `Zcmt` 可同时出现在 `Zce` 中，但它们分别占用 `Zcd` 的编码空间；因此“所有 Zc 指令可自由组合”不是合法的配置模型。T1-VERIFIED: [Zc* Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-1-zc-overview)；[Zcmp](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmp)；[Zcmt](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmt)

## 4. Zc* 怎样补足 C 的代码密度覆盖

| `C` 的边界或未覆盖模式 | 对应 Zc* 补足 | 获得的代码密度机会 | 不能误读为 | 标签 |
| --- | --- | --- | --- | --- |
| `C` 将整数压缩与条件性的 FP 压缩访存用单字母扩展一起表达 | `Zca`、`Zcf`、`Zcd` 分别命名整数核心、RV32 单精度和双精度压缩访存 | ISA/profile 能精确表达所选压缩子集 | 新增了与 `C` 不同的基础 load/store 语义 | T1-VERIFIED |
| `C` 没有压缩的整数 byte/halfword load/store | `Zcb` 增加 `c.lbu`、`c.lhu`、`c.lh`、`c.sb`、`c.sh` | 常见小对象、字符和 16-bit 数据访问可使用 16-bit 编码 | 任意寄存器、任意 offset 都可编码 | T1-VERIFIED |
| `C` 没有这些 byte/halfword/word 扩展、取反和乘法的新增短编码 | `Zcb` 增加 `c.[sz]ext.*`、`c.not`、`c.mul` | 位宽整理与常见计算可缩短 | `Zcb` 不依赖其他扩展，或 `c.sext.w` 是 Zcb 新指令 | T1-VERIFIED |
| `C` 的单条压缩访存不能把常见的多寄存器序言/尾声折叠为一条 16-bit 指令 | `Zcmp` 加入 PUSH/POP、POPRET 和双 move | 栈帧建立、callee-saved 保存恢复、返回可显著缩短 | 该复杂指令在异常时等同于不可观察的单次访存 | T1-VERIFIED |
| `C` 没有通过函数地址表压缩远距离固定 call/jump 的机制 | `Zcmt` 加入 `cm.jt`/`cm.jalt` 和 `jvt` | linker 可将特定 `j`、`jal` 或固定位置的 `auipc+jr`/`auipc+jalr` 序列改为表跳转 | 不需要表项、状态保存、执行权限或 `fence.i` | T1-VERIFIED |
| 嵌入式实现希望一次表达一组有用的新增压缩能力 | `Zce` 定义 MCU 导向的标准组合 | 工具链和平台可用一个组合名描述配置 | `Zce` 自动兼容 D/Zcd 或所有 profile | T1-VERIFIED |

上述比较是“覆盖面”而非“优劣”结论。`Zcmp` 和 `Zcmt` 为换取 16-bit 代码密度引入了多访存重执行、CSR 状态和编码冲突等实现/系统代价；是否值得采用取决于目标是否为嵌入式、是否需要 `D`、profile 是否允许以及软件是否能正确处理这些边界。T1-VERIFIED / INTERPRETIVE: [Zcmp](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmp)；[Zcmt](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmt)

## 5. C 的拆分：Zca、Zcf 与 Zcd

`C` 是 `Zca`、以及条件性的 `Zcf`/`Zcd` 的上集：`C` 总是蕴含 `Zca`；在 RV32 上，`C+F` 蕴含 `Zcf`；`C+D` 蕴含 `Zcd`。这组规则主要提供可组合、可命名的边界，并不把原 `C` 指令重新定义为另一套执行语义。T1-VERIFIED: [C in Zc*](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#C)

| 子集 | 内容 | 选择约束 | 与 C 的关系 | 标签 |
| --- | --- | --- | --- | --- |
| `Zca` | `C` 排除所有压缩 FP load/store | 不含八个 `c.f*` 访存 mnemonic | `C` 总是蕴含 `Zca` | T1-VERIFIED |
| `Zcf` | `c.flw`、`c.flwsp`、`c.fsw`、`c.fswsp` | 仅 RV32，且需 `Zca+F` | RV32 `C+F` 蕴含 `Zcf` | T1-VERIFIED |
| `Zcd` | `c.fld`、`c.fldsp`、`c.fsd`、`c.fsdsp` | 需 `Zca+D` | `C+D` 蕴含 `Zcd` | T1-VERIFIED |

`Zca` 解决的是“只描述非浮点压缩核心”的配置粒度问题，`Zcf`/`Zcd` 解决的是“分别声明压缩单精度/双精度访存”的命名问题。它们不是对 `C` 功能不足的补丁；`Zcb`、`Zcmp`、`Zcmt` 才是在原 `C` 未覆盖模式上增加新 16-bit 指令。T1-VERIFIED / INTERPRETIVE: [Zca](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-5-zca)；[Zcf](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-6-zcf-rv32-only)；[Zcd](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-7-zcd)

## 6. Zcb：补足小宽度访存与常见算术短编码

`Zcb` 定义简单、面向广泛 CPU 的代码尺寸缩减操作；规范指出其编码目前对所有架构保留且不与既有扩展冲突。`Zcb` 依赖 `Zca`，但并非每一条 `Zcb` 指令都在所有 `Zcb` 实现中无条件存在：部分指令的语义还要求相关的 B/M 扩展。T1-VERIFIED: [Zcb](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-8-zcb)

### 6.1 新增的 16-bit 指令类别

| 类别 | 指令 | XLEN | 寄存器/前提 | 标签 |
| --- | --- | --- | --- | --- |
| byte/halfword load | `c.lbu`、`c.lhu`、`c.lh` | RV32、RV64 | `rd'`、`rs1'` 均为 `x8`--`x15` | T1-VERIFIED |
| byte/halfword store | `c.sb`、`c.sh` | RV32、RV64 | `rs1'`、`rs2'` 均为 `x8`--`x15` | T1-VERIFIED |
| 零扩展 | `c.zext.b` | RV32、RV64 | `rd'/rs1'` 为 `x8`--`x15` | T1-VERIFIED |
| 符号/halfword 扩展 | `c.sext.b`、`c.zext.h`、`c.sext.h` | RV32、RV64 | 分别需要 `Zbb`；寄存器为 `x8`--`x15` | T1-VERIFIED |
| word 零扩展 | `c.zext.w` | 仅 RV64 | 需要 `Zba`；寄存器为 `x8`--`x15` | T1-VERIFIED |
| 一元逻辑 | `c.not` | RV32、RV64 | `rd'/rs1'` 为 `x8`--`x15` | T1-VERIFIED |
| 低 XLEN 位乘法 | `c.mul` | RV32、RV64 | 需要 `M` 或 `Zmmul`；两个操作数为 prime GPR | T1-VERIFIED |

`c.lbu`/`c.lhu`/`c.lh` 以零扩展或符号扩展方式把小宽度数据写到 `XLEN`；`c.sb`/`c.sh` 只写源寄存器的低 byte/halfword。和基础访存一样，异常、权限、端序和 EEI 边界来自 underlying 访存语义；压缩编码本身不创建另一套内存模型。T1-VERIFIED / INTERPRETIVE: [c.lbu](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-c_lbu)；[c.lhu](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-c_lhu)；[c.lh](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-c_lh)；[c.sb](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-c_sb)；[c.sh](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-c_sh)

`c.sext.w` 不在 `Zcb` 的新增编码列表中；它只是 RV64 上 `c.addiw rd, 0` 的伪指令。把 mnemonic 相似性误写成 `Zcb` 的硬件 opcode，会导致 assembler、decoder 和 ISA string 审阅出错。T1-VERIFIED: [Zcb](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-8-zcb)

### 6.2 阅读 Zcb 的正确顺序

1. 先确认实现有 `Zca`，再确认本条指令的额外依赖，例如 `c.mul` 的 `M`/`Zmmul`、`c.sext.b` 的 `Zbb` 或 RV64 `c.zext.w` 的 `Zba`。
2. 再确认 `rd'`、`rs1'`、`rs2'` 的实际寄存器均落在 `x8`--`x15`；不能因 mnemonic 合法就假定任意 GPR 能编码。
3. 最后按 underlying load/store 或算术操作检查异常、数据宽度与结果扩展规则。以上是 INTERPRETIVE 的审阅顺序，依赖条件和寄存器限制为 T1-VERIFIED。

## 7. Zcmp：压缩函数序言、尾声与双寄存器 move

`Zcmp` 的指令可以作为一系列既有 32-bit RISC-V 指令执行，但它们不是“只完成一次简单操作”的别名。它包含 `cm.push`、`cm.pop`、`cm.popret`、`cm.popretz`、`cm.mva01s` 和 `cm.mvsa01`，可在 RV32/RV64 使用，依赖 `Zca`。T1-VERIFIED: [Zcmp](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmp)

| 指令族 | 架构可见作用 | C 未覆盖的代码尺寸机会 | 标签 |
| --- | --- | --- | --- |
| `cm.push` | 调整 `sp` 建立栈帧，并保存 register list | 多条栈调整和 store 序言合成一个 16-bit 指令 | T1-VERIFIED |
| `cm.pop` | 从栈帧恢复 register list，并调整 `sp` | 多条 load 和栈回收尾声合成一个 16-bit 指令 | T1-VERIFIED |
| `cm.popret` | 恢复、调整 `sp` 并 `ret` | 压缩常见 return 尾声 | T1-VERIFIED |
| `cm.popretz` | `cm.popret` 加上将零写入 `a0` | 压缩返回零的常见尾声 | T1-VERIFIED |
| `cm.mva01s` / `cm.mvsa01` | 在 `a0`/`a1` 与两个 `s0`--`s7` 保存寄存器间成对搬移；`cm.mvsa01` 要求两个目标寄存器不同 | 压缩常见参数/保存寄存器交换 | T1-VERIFIED |

`reg_list` 可编码 `{ra}` 到包含 `ra` 和若干 `s` 寄存器的有限集合；`{ra, s0-s10}` 不是有效列表，若要包含 `s10` 必须同时包含 `s11`。这些列表和 stack adjustment 的合法值还会随 RV32E、RV32I、RV64 变化，不能把一套 ABI 寄存器列表或立即数范围泛化给全部基础 ISA。T1-VERIFIED: [Zcmp](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmp)；[PUSH/POP register instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-pushpop)

双寄存器 move 也有独立的编码约束：`cm.mvsa01 r1s', r2s'` 要求两个目标 `s` 寄存器不同；在 RV32E 中，超出可用 `s0`/`s1` 映射的编码保留。T1-VERIFIED: [cm.mvsa01](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-cm_mvsa01)

### 7.1 不是普通单次访存：故障与重执行边界

PUSH/POP 的正确执行要求 `sp` 指向幂等内存，因为 trap 返回后整个序列会被重新执行，且同一序列中可以有多个 trap。对 PUSH，多个 store 可按任意顺序、组合为更宽访问或重复发出，但 `sp` 调整只能在确定整条 PUSH 会提交时提交；对 POP/POPRET，某些 load 的结果可在 fault 前暂时更新目的寄存器，然而最终的 `sp` 调整、可选 `li a0, 0` 和可选 `ret` 必须等到整条指令确定提交后才完成。T1-VERIFIED: [PUSH/POP fault handling](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#pushpop-idempotent-memory)

对非幂等内存，若实现不支持 PUSH/POP，其实现可借助 idempotency PMA 产生 access-fault 以避免不可预测结果；软件只有在能够容忍异常时重复发出相应访存的条件下，才应在非幂等区域使用这类指令。中断能否发生在序列中由实现定义，因此不能把 `cm.push` 当作“全程不可中断”的微架构承诺。T1-VERIFIED: [Non-idempotent memory handling](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#pushpop_non-idem-mem)；[PUSH/POP fault handling](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#pushpop-idempotent-memory)

### 7.2 Zcmp 的组合限制

`Zcmp` 重用 `c.fsdsp` 的部分编码，所以与 `Zcd` 不兼容；而 `C+D` 会蕴含 `Zcd`。规范将 `Zcmp` 定位为嵌入式 CPU 的复杂操作，并明确指出它不兼容 application-class profiles。前两句是 T1-VERIFIED: [Zcmp](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmp)；[C in Zc*](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#C)。把该约束视为“仅靠 decoder 开关即可消除的 ABI 问题”是不应作出的 INTERPRETIVE 推论。

## 8. Zcmt：表跳转与 `jvt` 架构状态

`Zcmt` 增加 `cm.jt` 和 `cm.jalt`，以及 Jump Vector Table 的 `jvt` CSR。该机制使用含 256 个 XLEN 宽表项、至少 64-byte 对齐的指令内存表来保存函数地址；表项采用当前数据端序，和普通指令取指恒为 little-endian 的规则不同。只有 `jvt.mode=0`（Jump Table Mode）时，两个 table-jump 指令才按本节语义执行；其他保留 mode 下它们也是 reserved。T1-VERIFIED: [Zcmt](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmt)；[Table Jump Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-tablejump)；[jvt CSR](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#csrs-jvt)

| 指令 | 编码分界 | 语义 | 代码尺寸用途 | 标签 |
| --- | --- | --- | --- | --- |
| `cm.jt index` | `0..31`（`index < 32`） | 从 JVT 读取表项并跳转 | 可代替 32-bit `j` 或固定目标的 `auipc+jr` 序列 | T1-VERIFIED |
| `cm.jalt index` | `32..255`（`index >= 32`） | 从 JVT 读取表项、跳转并把 `pc+2` 写入 `ra` | 可代替 32-bit `jal ra` 或固定目标的 `auipc+jalr ra` 序列 | T1-VERIFIED |

这种表跳转是一种 dictionary compression：linker 可以将指定的 32-bit `j`、32-bit `jal ra`，或目标固定时的 64-bit `auipc+jr`/`auipc+jalr ra` 序列替换成 16-bit table jump 加一项表项。是否在单个程序上净缩小，取决于可共享的调用目标数、表项成本、重定位布局和本地链接器支持；规范没有保证任何二进制一定获益。T1-VERIFIED / INTERPRETIVE: [Table Jump Overview](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#insns-tablejump)

### 8.1 JVT、取指权限与 `fence.i`

| 项目 | 规范规则 | 影响 | 标签 |
| --- | --- | --- | --- |
| 两次隐式取指 | table jump 先取 `cm.jt`/`cm.jalt`，再取 JVT 表项 | 两次取指都要求 execute permission；read permission 无关 | T1-VERIFIED |
| 例外归属 | 任一次取指异常都归因于 table jump 指令 | `xEPC` 为 table jump 的 PC；`xTVAL` 可给出故障取指地址 | T1-VERIFIED |
| 表更新 | 修改 JVT 内存后需要 `fence.i` 才保证对取指可见 | 把普通数据写表后直接跳转不足以保证新表项可见 | T1-VERIFIED |
| `jvt` CSR | 地址 `0x017`，URW，XLEN-bit WARL | 实现 Zcmt 时必须实现；可为只读值 | T1-VERIFIED |
| `jvt.mode` | `0` 为 Jump Table Mode，必须实现；其他值保留 | 保留 mode 下 `cm.jt`/`cm.jalt` 也是 reserved | T1-VERIFIED |
| BASE | `jvt.base` 至少 64-byte 对齐；有虚拟内存时为虚拟地址 | OS/运行时须按上下文维护该状态 | T1-VERIFIED |
| 上下文切换 | `jvt` 是系统软件上下文的架构状态 | 必须保存/恢复 | T1-VERIFIED |
| `Smstateen` | 若实现 `Smstateen`，`jvt` CSR 需要 state enable | 特权软件还须按该控制机制开放访问 | T1-VERIFIED |

`Zcmt` 依赖 `Zca+Zicsr`，同样重用 `c.fsdsp` 的部分编码而与 `Zcd` 不兼容。规范将它定位为嵌入式 CPU 的复杂操作，并明确标为与 RVA profiles 不兼容；不能只因目标有 `Zicsr` 或 `misa.C` 就推定可执行 `cm.jt`。这些 ISA/profile 事实为 T1-VERIFIED: [Zcmt](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#Zcmt)；[jvt CSR](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#csrs-jvt)。

## 9. Zce、`misa.C` 与 ISA string 的配置边界

`Zce` 是 MCU 导向的标准组合包，而不是 `C` 的同义词。规范给出的成员依赖如下：

| 基础配置 | `Zce` 包含的 Zc* 成员 | 不能由此推出 | 标签 |
| --- | --- | --- | --- |
| RV32，未指定 `F` | `Zca + Zcb + Zcmp + Zcmt` | 有 `Zcd` 或完整 `C`；是否另行实现 `D` 仍须检查冲突 | T1-VERIFIED |
| RV32，指定 `F` | `Zca + Zcb + Zcmp + Zcmt + Zcf` | 有 `Zcd` 或可与 D 无条件组合 | T1-VERIFIED |
| RV64 | `Zca + Zcb + Zcmp + Zcmt` | 有 `Zcf`；`Zcf` 不存在于 RV64 | T1-VERIFIED |

规范示例把 `RV32IMC` 表达改为 `RV32IM_Zce`，把 `RV32IMCF` 改为 `RV32IMF_Zce`。这些例子说明组合的命名方式，不是对本地 compiler、assembler 或 ELF attributes 的支持承诺。T1-VERIFIED / UNVERIFIED: [Zce](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#28-1-3-zce)；[ISA naming](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html)

`misa.C` 也不能当作“全部 Zc* 功能已发现”的替代品。规范列出的置位组合是：`Zca` 且无 `F`；RV32 的 `Zca+Zcf+F` 且无 `D`；RV32 的 `Zca+Zcf+Zcd+D`；以及 RV64 的 `Zca+Zcd+D`。后两种含 `D` 的组合明确排除 `Zcmp` 和 `Zcmt`，这些 ISA 组合事实为 T1-VERIFIED: [MISA.C](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#misaC)。软件、固件和验证环境须以完整 ISA 配置或平台发现机制确认具体 Zc 子扩展，这是 INTERPRETIVE 的工程建议。

## 10. 汇编阅读示例：前提必须随 mnemonic 一起检查

以下片段仅解释规范语义与压缩机会，不宣称本地 GNU assembler、LLVM、linker 或芯片已经接受/生成这些编码。

~~~asm
# Zcb：a0=x10、a1=x11，均位于 prime GPR 组 x8--x15。
c.lbu    a0, 0(a1)       # 需要 Zca + Zcb
c.sext.h a0              # 还需要 Zbb
c.mul    a0, a1          # 还需要 M 或 Zmmul

# Zcmp：展示常见函数序言/尾声；合法 reg_list 和 stack_adj 取决于 XLEN/ABI。
cm.push    {ra, s0-s2}, -64
# ... function body ...
cm.popret  {ra, s0-s2}, 64

# Zcmt：假定 jvt.mode=0、jvt 已配置、表项内存已更新并执行过所需 fence.i。
cm.jt    12              # index < 32
cm.jalt 40               # index >= 32，link address 为 pc+2
~~~

从反汇编审阅角度，先查 ISA string/平台配置是否有相应子扩展及其依赖，再查 operands 是否满足 prime register、`reg_list`、index 或 `XLEN` 条件，最后才把 mnemonic 还原到 underlying 操作或多操作序列。该顺序是 INTERPRETIVE；依赖、编码分界和指令语义是 T1-VERIFIED。

## 11. ISA、实现、profile 与软件边界

| 层次 | 本文可确认 | 本文不确认 | 标签 |
| --- | --- | --- | --- |
| ISA | Zc* 子扩展的指令、依赖、冲突、`jvt` 和 fault/retry 语义 | 某个核是否实现任一 Zc 子扩展 | T1-VERIFIED / UNVERIFIED |
| 微架构 | `Zcmp` 序列可重执行，提交点有明确架构约束；Zcmt 有两次隐式取指 | decoder 如何展开、访存如何合并、是否支持非幂等区域 | T1-VERIFIED / UNVERIFIED |
| 平台/profile | `Zcmp` 不兼容 application-class profiles；`Zcmt` 不兼容 RVA profiles | 某块板卡或 OS 所属 profile、PMA、上下文切换实现 | T1-VERIFIED / UNVERIFIED |
| 工具链 | 规范给出 `Zce` 的 ISA string 表达示例和 linker 可替换的 Zcmt 序列 | 本地 GCC/LLVM/binutils 接受的 `-march`、优化与 relaxation 行为 | T1-VERIFIED / UNVERIFIED |
| 跨 ISA 对比 | `Zc*` 可作为 mixed-length code-density 扩展家族讨论 | 不在本文宣称与 Arm Thumb、microMIPS 等性能或 ABI 等价 | INTERPRETIVE |

特别是 `Zcmp`/`Zcmt` 不应被简化为“decode 后执行若干普通指令即可”：前者带有 re-execution、idempotency、提交原子性和可能的部分寄存器更新边界，后者带有 execute permission、`fence.i`、JVT 上下文状态与异常归属。这些都是处理器验证和 OS 支持应明确覆盖的架构可见条件。T1-VERIFIED / INTERPRETIVE: [PUSH/POP fault handling](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#pushpop-idempotent-memory)；[Table jump fault handling](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html#tablejump-fault-handling)

## 12. 六维总结

| 维度 | 结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| 1. Ratification & maturity | 用户指定的 ratified-library `v20260120` 含 Zc* Version 1.0.0 | 下次评审须重新核对固定版本与连续部署资料 | T1-VERIFIED |
| 2. ISA semantics | `Zca`/`Zcf`/`Zcd` 拆分 C；`Zcb` 加简单短编码；`Zcmp` 加多操作序言/尾声；`Zcmt` 加表跳转 | Zcmp/Zcmt 与 Zcd 冲突，且复杂操作有额外语义 | T1-VERIFIED |
| 3. Platform requirements | Zcmp 面向 embedded 且不兼容 application-class profiles；Zcmt 面向 embedded 且不兼容 RVA profiles | 裸 ISA 页面不证明具体平台已启用它们 | T1-VERIFIED / UNVERIFIED |
| 4. Software evidence | 规范给出组合命名和 linker 可用的 table-jump 替换场景 | 本地工具链、ELF 属性、libc/OS 保存 `jvt` 的情况未实测 | T1-VERIFIED / UNVERIFIED |
| 5. Competitive anchor | 可按“混合长度、短小访存、序言/尾声和间接调用代码密度”比较 | 本文不作跨 ISA 性能或生态排名 | INTERPRETIVE |
| 6. Deployment intent | `Zce`、Zcmp、Zcmt 的文字定位指向 MCU/embedded 代码尺寸目标 | 不保证全部嵌入式或通用处理器均采用同一组合 | T1-VERIFIED / INTERPRETIVE |

## 13. 常见误解

| 误解 | 正确表述 | 标签 |
| --- | --- | --- |
| “`Zc*` 就是 `C` 的别名。” | `Zc*` 同时含 C 的子集和新增的 `Zcb`/`Zcmp`/`Zcmt`；`C` 不自动蕴含后三者。 | T1-VERIFIED |
| “`Zca` 新增了一套整数压缩指令。” | `Zca` 是 C 中排除压缩 FP load/store 后的已有部分。 | T1-VERIFIED |
| “`Zcf` 可用于 RV64。” | `Zcf` 只与 RV32 相关，不能指定给 RV64。 | T1-VERIFIED |
| “有 `C+D` 后可再加 `Zcmp` 或 `Zcmt`。” | `C+D` 蕴含 `Zcd`，而 `Zcmp`、`Zcmt` 均与 `Zcd` 冲突。 | T1-VERIFIED |
| “`Zcb` 中所有 mnemonic 都不需要其他扩展。” | `c.sext.b`/`c.zext.h`/`c.sext.h` 需 `Zbb`，`c.zext.w` 需 `Zba`，`c.mul` 需 `M` 或 `Zmmul`。 | T1-VERIFIED |
| “`c.sext.w` 是 Zcb 的新 opcode。” | 它是 RV64 上 `c.addiw rd, 0` 的伪指令。 | T1-VERIFIED |
| “`cm.push` 是一条从头到尾完全不可观察的单次 store。” | 它的访存可重试/重复；只有指定的最终提交状态有原子性约束。 | T1-VERIFIED |
| “跳转表项只需 readable。” | Zcmt 的两次隐式取指均需要 execute permission，read permission 无关。 | T1-VERIFIED |
| “`misa.C=1` 即代表所有 Zc* 指令可用。” | `misa.C` 的置位组合不包含对 `Zcb`/`Zcmp`/`Zcmt` 的完整发现。 | T1-VERIFIED |

## 14. 验证清单

- [ ] 重新打开 [Zc* 1.0.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html)、[C 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html)、[ISA naming](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) 和 [ratified library](https://docs.riscv.org/reference/home/index.html)，确认课程仍采用 `v20260120`。
- [ ] 对目标配置分别列出 `Zca`、`Zcf`、`Zcd`、`Zcb`、`Zcmp`、`Zcmt`、`Zce`，而不是只记录一个 `C` 或 `misa.C` 位。
- [ ] 验证组合冲突：显式 `Zcd` 或 `C+D` 组合时拒绝 `Zcmp` 和 `Zcmt`；在 RV64 上拒绝 `Zcf`；按目标 profile 检查 Zcmp/Zcmt 的适用性。
- [ ] 对 Zcb 覆盖 prime GPR 边界和每条额外依赖：`Zbb`、`Zba`、`M`/`Zmmul`，以及 RV64-only 的 `c.zext.w`。
- [ ] 对 Zcmp 覆盖 register list、stack adjustment、精确/非精确异常、重执行、幂等内存和 `sp`/`ret` 提交边界。
- [ ] 对 Zcmt 覆盖 `cm.jt` 的 `0..31`、`cm.jalt` 的 `32..255`、`jvt.mode=0`、256 项 JVT、64-byte 对齐、当前数据端序、两次 execute fetch、表更新后的 `fence.i`、`Smstateen` state enable 和 context switch 保存/恢复。
- [ ] 对实际对象文件检查 ISA 属性、编译选项和 `objdump -dr` 输出，确认本地 assembler/linker 是否真正生成预期 Zc 指令；这一步在本文中未执行，故为 UNVERIFIED。

## 15. 参考资料

1. [RISC-V Unprivileged ISA `v20260120` index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) - 用户指定的官方固定版本入口。
2. ["Zc*" Extension for Code Size Reduction, Version 1.0.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zc.html) - 本章 Zc* 语义、依赖、冲突、PUSH/POP 与 table jump 的主依据。
3. ["C" Extension for Compressed Instructions, Version 2.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/c-st-ext.html) - C 的原有 16-bit 编码覆盖及其与 Zc* 的比较基线。
4. [ISA Extension Naming Conventions](https://docs.riscv.org/reference/isa/v20260120/unpriv/naming.html) - ISA string、`C` 与 `Z*` 命名背景。
5. [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) - `norm:` 规则的规范性说明。
6. [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) - Layer 1 连续部署入口；不替代固定 ratified 版本。
