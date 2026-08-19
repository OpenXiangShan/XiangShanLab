# RISC-V 其他原子扩展：Zawrs、Zacas、Zabha、Zalasr

> 规范基线：RISC-V Unprivileged ISA 第 12--16 章，用户指定的 ratified 版本 `v20260120`。本文以 `A` 扩展（`A = Zaamo + Zalrsc`）为比较基线，分析 `Zawrs`、`Zacas`、`Zabha`、`Zalasr` 分别补足什么能力；`RVA23` 是应用处理器 **profile**，不是 `A` 的别名，文中单独说明其要求。规范正文是主证据，具体核、PMA 表、操作系统和本地工具链未实测的部分明确标为 `UNVERIFIED`。

## 1. 结论与范围

`A` 扩展已经提供两条原子路径：`Zaamo` 的 word/doubleword 原子读改写（AMO），以及 `Zalrsc` 的 load-reserved/store-conditional（LR/SC）配对。它并不自动包含以下四项独立扩展：

- `Zawrs`：把基于 `LR` reservation 的空转轮询变为可低功耗暂停的等待；
- `Zacas`：提供单条 `AMOCAS` 条件更新，并扩展到 RV64 的 128-bit；
- `Zabha`：提供 byte/halfword 粒度的 AMO（以及与 `Zacas` 组合时的 subword CAS）；
- `Zalasr`：提供独立的 load-acquire 与 store-release，而不是借用成对 LR/SC 或无用的 RMW。

因此，`misa.A=1`、`-march` 中含 `a`，或二进制只声明 `A`，均不足以证明上述四项存在。应分别检查目标 ISA 字符串、ELF 属性、平台声明和实际反汇编。T1-VERIFIED: [A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；[v20260120 unprivileged index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html)

| 扩展 | 用户指定版本 | 直接增加的程序员可见能力 | 与 `A` 的关系 | RVA23U64 v1.0 中的状态 | 标签 |
| --- | --- | --- | --- | --- | --- |
| `Zawrs` | 1.01 | `WRS.NTO` / `WRS.STO`：等待 reservation set 的变化或中断 | 只能与 `Zalrsc` 的 `LR` 一起使用 | mandatory | T1-VERIFIED |
| `Zacas` | 1.0.0 | `AMOCAS.W/D/Q` 单指令 compare-and-swap | 依赖 `Zaamo` | development option | T1-VERIFIED |
| `Zabha` | 1.0 | byte/halfword AMO；有 `Zacas` 时再有 `AMOCAS.B/H` | 依赖 `Zaamo` | development option | T1-VERIFIED |
| `Zalasr` | 1.0 | 独立的 load-acquire / store-release | 可独立实现；语义上补足 `Zaamo` / `Zalrsc` | 在所引 RVA23U64 v1.0 表中未列为 mandatory 或 development option | T1-VERIFIED |

四个章节位于 [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html) 所列的 `v20260120` Unprivileged ISA 中；这里的“已 ratified”只表示 ISA 语义稳定，**不**表示每个 RVA23 实现、每块主存或每个 MMIO 区域都必须支持全部操作。T1-VERIFIED: [ratified library](https://docs.riscv.org/reference/home/index.html)；[RVA23 v1.0](https://docs.riscv.org/reference/rva23/v1.0/rva23-profiles.html)

## 2. 规范来源与证据边界

### 2.1 证据优先级

| 层级 | 本次来源 | 用途 | 标签 |
| --- | --- | --- | --- |
| Layer 1：UDB | [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 机器可读结构和版本变化的交叉检查；连续部署产物不替代固定版本的语义锚点 | T1-VERIFIED |
| Layer 2：Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 解释 `norm:` 规则及其规范性地位 | T1-VERIFIED |
| Layer 3：Ratified ISA | [A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)、[Zawrs 1.01](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)、[Zacas 1.0.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)、[Zabha 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zabha.html)、[Zalasr 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html) | 本文的 ISA 语义、排序、对齐及限制的主依据 | T1-VERIFIED |
| Layer 3：Ratified profile / PMA | [RVA23 v1.0](https://docs.riscv.org/reference/rva23/v1.0/rva23-profiles.html)、[Atomicity PMAs](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html#atomicity-pmas) | 区分 profile 承诺与地址区域的原子能力 | T1-VERIFIED |
| 上游软件证据 | [LLVM RISC-V Target User Guide](https://llvm.org/docs/RISCVUsage.html) | 只说明上游 LLVM 文档中的支持状态；不替代本地编译实测 | T1-VERIFIED / UNVERIFIED |

### 2.2 文中标签

- **T1-VERIFIED**：可直接在 RISC-V ratified 规范、profile、PMA 或官方上游工具链资料中定位。
- **T2-CROSS-CHECKED**：可靠补充资料的交叉验证；不能推翻 T1 语义。
- **UNVERIFIED**：具体 CPU、固件、Linux、库、设备寄存器或本地工具链尚未实测，不能作为已证实事实。

### 2.3 版本锚点

**SPEC-UPDATE-ALERT：** 本次检查时，官方 ratified library 的 Unprivileged ISA 入口仍列为 `v20260120`；而 [UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) 是从主分支持续生成的产物，页面显示的提交与生成日期可以晚于本课程的固定锚点。本文因此以用户指定的 `v20260120` 保证可复现语义，不把 UDB 的连续部署版本误当作它的 ratified 替代品。新项目评审时应重新核验两者。T1-VERIFIED: [ratified library](https://docs.riscv.org/reference/home/index.html)；[UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)

## 3. `A` 的原始能力与四个缺口

`A` 的 `Zaamo` 以单条指令完成 read-modify-write，操作宽度是 `.W`，RV64 再有 `.D`；`Zalrsc` 以 `LR.W/D` 与 `SC.W/D` 组成条件更新。`A` 中的 `aq` / `rl` 位能表达 acquire、release 和同一地址域内的 sequentially-consistent 排序，但它们不是跨 memory/I/O 域的万能屏障。T1-VERIFIED: [A 2.1 §12.1--§12.1.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

| `A` 的能力缺口 | 只有 `A` 时的常见做法 | 对应扩展如何补足 | 仍然不能推出的结论 | 标签 |
| --- | --- | --- | --- | --- |
| 等待共享变量变化时不断执行 load/LR 与分支 | busy polling；消耗执行资源、缓存/互连读带宽和能耗 | `Zawrs` 让 hart 基于有效 reservation 暂停到低功耗状态 | `WRS` 返回不等于目标值已改变；仍须重读并检查谓词 | T1-VERIFIED |
| 没有原生单指令 CAS | `LR`、比较、`SC`、失败重试的循环 | `Zacas` 的 `AMOCAS.W/D/Q` 在一条指令中完成 load/compare/conditional-store | CAS 不会自动消除 ABA；也不保证任意物理地址支持 CAS | T1-VERIFIED |
| 没有 B/H AMO | 位操作可用宽 AMO 掩码；其他操作可用宽 LR/SC 模拟 | `Zabha` 原生提供 B/H AMO，配合 `Zacas` 还有 B/H CAS | `Zabha` 故意没有 B/H LR/SC；未对齐和 MMIO 仍受区域规则限制 | T1-VERIFIED |
| 没有通用、独立且有序的 atomic load/store | 用 `LR.aq` 伪作 load，或用 `AMOSWAP.rl` 伪作 store，必要时再加 `FENCE` | `Zalasr` 提供 B/H/W/D 的 standalone load-acquire / store-release | 不替代跨 memory/I/O 域的 `FENCE`；不提供无排序但“额外保证原子”的单独编码 | T1-VERIFIED |

### 3.1 原子性、排序和等待是三件事

1. **原子性**：AMO、成功的 LR/SC、`AMOCAS`，以及 `Zalasr` 指定的单次 load/store 的内存操作边界，解决“某次访问能否被拆开观察”的问题。
2. **排序**：`aq` / `rl` 约束同一地址域的可观察顺序；访问 memory 域的原子指令不会仅因设置 `aq/rl` 就排序 I/O 域，反之亦然，跨域必须使用适当的 `FENCE`。
3. **等待**：`Zawrs` 减少等待期间的运行与轮询成本；它没有把一次 reservation 失效变成带载荷、必达或已验证的事件通知。

把这三件事混成“原子指令天然全局有序且会可靠唤醒”是错误的。T1-VERIFIED: [A ordering](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；[Zawrs](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)

## 4. `Zawrs`：等待 reservation set，而不是反复读内存

### 4.1 语义

`Zawrs` 定义 `WRS.NTO`（no timeout）和 `WRS.STO`（short timeout）。软件先以 `LR` 登记包含目标字节的 reservation set；随后执行 WRS。只要 reservation 仍有效、没有观察到 pending interrupt，且（对 `WRS.STO`）实现定义的短超时尚未到，hart 可以停顿在低功耗状态。典型用途是等待锁变量、空队列的生产者、或由另一个 hart / 加速器 / 外部 I/O agent 更新的完成标志。T1-VERIFIED: [Zawrs §13.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)

~~~text
repeat:
    observed = LR(wait_address)       # 建立 reservation，并读取当前状态
    if predicate(observed):
        proceed
    WRS.NTO or WRS.STO                # 只在 reservation 仍有效时允许停顿
    # 返回后重新 LR / 重查 predicate
~~~

上图是算法轮廓，不表示 `WRS` 自己读取了新值；每次从 WRS 返回后仍须重新读取并检查条件。T1-VERIFIED: [Zawrs §13.1.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)

| 指令 | 停顿上界 | 用途 | 精确边界 | 标签 |
| --- | --- | --- | --- | --- |
| `WRS.NTO` | 无架构规定的短超时 | 无 deadline 的内存等待 | 在 reservation 有效且无 pending interrupt 时可停顿；可受 `TW` / `VTW` 虚拟化控制影响 | T1-VERIFIED |
| `WRS.STO` | 实现定义的“short” timeout | 周期性重查 deadline 或执行其他任务 | timeout 时长可在实现之间、甚至实现内部显著变化；不是计时器 API | T1-VERIFIED |

两条指令在所有特权级可用，并遵循 `WFI` 对 pending interrupt 的恢复规则；**即使中断被禁用**，pending interrupt 也会阻止持续停顿。实现还可以因任何原因偶发地结束等待。因此，“WRS 返回”既不能等价为“有人写了目标变量”，也不能等价为“等待超时”。T1-VERIFIED: [Zawrs §13.1.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)

### 4.2 它补足 `A` 的什么不足

`A` 可用 `LR` 或普通 load 构成自旋循环，但没有“对 reservation set 变化等待并进入低功耗”的指令。`Zawrs` 补的是 **contended wait 的能耗与重复读取流量**，不改变 `A` 的读改写能力，也不替代 acquire/release 或 `FENCE` 的同步职责。该结论直接来自 `Zawrs` 的设计目标；“可能降低互连压力”是从不再持续执行轮询 load 推出的实现层解释，实际收益取决于微架构，故为 **UNVERIFIED**。T1-VERIFIED: [Zawrs introduction](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)

### 4.3 使用限制

- `Zawrs` 只在有 `LR` reservation 的情况下有意义，故不能单独取代 `Zalrsc`。
- `WRS.NTO` 在非 M 模式受 `mstatus.TW` 约束；在 VS/VU 模式也可能受 `hstatus.VTW` 约束而产生相应异常。虚拟机监控器必须按特权规范处理这些边界。
- `WRS.NTO` 与 `WFI` 不同：当控制它的 `TW=0` 时，U-mode 的 `WRS.NTO` 不被指定为 illegal instruction，规范明确预期它可用于用户态无 deadline 的内存等待。
- WRS 章节没有赋予它 `aq/rl` 或跨域 fence 语义；发布数据、读取发布数据和 MMIO 顺序仍须由算法选择的原子访问与 `FENCE` 建立。前半句为 T1-VERIFIED；最后一句是基于 `A` 排序规则的保守应用。

## 5. `Zacas`：把 compare-and-swap 变成单条条件更新

### 5.1 `AMOCAS` 的读、比、写约定

`Zacas` 依赖 `Zaamo`，定义 `AMOCAS.W`、`AMOCAS.D` 和仅 RV64 可用的 `AMOCAS.Q`。它原子地读取 `rs1` 指向位置，将该旧值与 `rd` 中的 compare 值进行逐位比较；相等则写入 `rs2` 的 swap 值；**无论成功或失败，读到的旧值都会写回 `rd`**。因此，调用者若需要保留 expected 值，应在执行前另存一份。T1-VERIFIED: [Zacas §14.1--§14.1.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)

~~~text
old = MEM[rs1]
if old == rd:              # rd 是输入的 expected
    MEM[rs1] = rs2         # rs2 是 desired
rd = old                   # rd 同时成为输出
~~~

| 形式 | 访问宽度 | 可用架构 / 寄存器要求 | 对齐要求 | 标签 |
| --- | --- | --- | --- | --- |
| `AMOCAS.W` | 32 bit | RV32、RV64 | 4 B | T1-VERIFIED |
| `AMOCAS.D` | 64 bit | RV64 用单寄存器；RV32 用偶数起始寄存器对 | 8 B | T1-VERIFIED |
| `AMOCAS.Q` | 128 bit | 仅 RV64；`rd:rd+1` 和 `rs2:rs2+1` 均为偶数起始寄存器对 | 16 B | T1-VERIFIED |

未对齐 `AMOCAS` 采用与 `A` AMO 相同的 address-misaligned / access-fault 选择；不能假定异常处理程序一定能模拟它。T1-VERIFIED: [Zacas §14.1.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)

### 5.2 成功、失败与排序

| 情形 | `aq` | `rl` | 应特别注意的边界 | 标签 |
| --- | --- | --- | --- | --- |
| compare 成功 | 有 `aq` 时 acquire | 有 `rl` 时 release | 与 `A` 的原子排序一样仍只覆盖被访问的地址域 | T1-VERIFIED |
| compare 失败 | 有 `aq` 时仍是 acquire | 无论 `rl` 如何都**没有** release | 失败路径不能被当作 release 操作 | T1-VERIFIED |
| 失败时的写 | 可以完全不写，也可以回写读到的旧值 | 即使产生该写，也没有 release | 指令无论是否实际写回都按 AMO 参加 RVWMO PPO 规则 | T1-VERIFIED |

`AMOCAS` 始终需要写权限，即使 compare 将失败。这避免把它误用为绕开写访问保护的“只读比较”。若需要在 memory/I/O 域之间建立顺序，仍应使用 `FENCE`。T1-VERIFIED: [Zacas ordering and permissions](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)

### 5.3 它补足 `A` 的什么不足

只有 `A` 时，word/doubleword CAS 要用 `LR → compare → SC → retry` 建立；`SC` 会因 reservation 失效而失败，且可移植的 forward-progress 依赖 constrained LR/SC loop、同址同宽和具有 eventuality 属性的区域。`AMOCAS` 则把条件 load/store 合并为一条指令，直接匹配常见 compare-exchange 接口，也提供 `AMOCAS.Q` 以原子处理 RV64 上的 128-bit 组合对象。T1-VERIFIED: [A LR/SC and constrained loops](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；[Zacas](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)

`AMOCAS.Q` 的典型用法是把 pointer 与修改计数器作为一个 128-bit 对象比较和更新，从而构造常见的 ABA 缓解方案。**单宽 CAS 并不自动消除 ABA**；只有算法把版本/计数等额外状态纳入同一次比较时，才能处理“值 A→B→A”的问题。前一句为 T1-VERIFIED 的规范示例方向，后一句是该示例的算法解释。T1-VERIFIED: [Zacas queue example](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)

### 5.4 PMA 边界

实现 `Zacas` 不代表任意地址都能执行每种 CAS。Atomicity PMA 在 `A` 的四级 AMO 支持之外增加递进的 `AMOCASW`、`AMOCASD` 和 `AMOCASQ`；它们均以 `AMOArithmetic` 支持为前提。主存、I/O 或特殊区域可能只支持其中一部分，或完全不支持。T1-VERIFIED: [Atomicity PMAs](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html#atomicity-pmas)

## 6. `Zabha`：把 AMO 的宽度降到 byte 和 halfword

### 6.1 指令集合与数据宽度

`Zabha` 依赖 `Zaamo`，加入如下 B/H 原子读改写：

~~~text
AMO{ADD,AND,OR,XOR,SWAP,MIN,MINU,MAX,MAXU}.{B,H}
~~~

若同时实现 `Zacas`，还加入 `AMOCAS.B` 和 `AMOCAS.H`。B/H AMO 写入 `rd` 的旧值总是符号扩展；`rs2` 的高位忽略。B/H CAS 的 compare 值同样只使用 `rd` 的相应低位。T1-VERIFIED: [Zabha §15.1.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zabha.html)

| 项目 | `Zabha` 的规则 | 边界 | 标签 |
| --- | --- | --- | --- |
| 宽度 | B=8 bit、H=16 bit | 不是把 `A` 的所有原子形式机械复制到 subword | T1-VERIFIED |
| 排序 | 可使用 `aq` / `rl` 提供 release consistency 语义 | 仍是访问所在地址域内的排序 | T1-VERIFIED |
| 对齐 | `rs1` 要按操作数大小自然对齐 | 不对齐沿用 `A` 的异常选项 | T1-VERIFIED |
| LR/SC | 不提供 B/H `LR`、`SC` | 这是规范明确的“low utility”取舍 | T1-VERIFIED |

### 6.2 它补足 `A` 的什么不足

`A` 缺少 subword AMO。位操作可用更宽的 AMO 加掩码模拟，非位操作则可用更宽的 LR/SC 模拟；`Zabha` 规范明确指出这会产生以下问题：

| 宽操作模拟的不足 | `Zabha` 的补足点 | 不能过度外推的结论 | 标签 |
| --- | --- | --- | --- |
| 大规模 / NUMA、高竞争下的 LR/SC 模拟存在可扩展性和公平性问题 | 原生 B/H AMO 减少以宽 LR/SC 完成 subword 更新的需要 | 不承诺特定核必然更快或绝对公平 | T1-VERIFIED / UNVERIFIED |
| 在 non-idempotent I/O 区做宽 AMO 可能出现非预期副作用 | 访问宽度可匹配真正的 B/H 字段 | 仍必须确认该 I/O 区支持相应 AMO 和协议语义 | T1-VERIFIED / UNVERIFIED |
| 宽访问可能额外触发 breakpoint / watchpoint | 仅触及真正的 B/H 对象 | 具体调试器触发规则仍取决于平台 | T1-VERIFIED / UNVERIFIED |
| 编译器内联的模拟序列增大代码尺寸 | 可用单条 B/H AMO 表达相应操作 | 某个编译器是否实际生成它须看版本、`-march` 与优化级 | T1-VERIFIED / UNVERIFIED |

这是“**操作宽度**”上的补足，不是“补齐所有 subword 原子接口”。尤其 `Zabha` 不提供 B/H LR/SC；若算法需要 B/H compare-exchange 的单条指令，还要有 `Zacas`。T1-VERIFIED: [Zabha introduction](https://docs.riscv.org/reference/isa/v20260120/unpriv/zabha.html)

## 7. `Zalasr`：独立的 load-acquire 与 store-release

### 7.1 为什么 `A` 的现有路径不够精细

`Zaamo` 和 `Zabha` 的 AMO 都是同时读和写的 RMW。`Zalrsc` 虽然有只读 `LR` 和只写 `SC`，但 `LR` 暗示后面会有 `SC`，而 `SC` 又要求前面存在合格的 `LR`，并受 reservation 与循环限制约束；它们不是一般意义上可独立使用的有序 load/store。T1-VERIFIED: [Zalasr §16.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html)

`A` 可以用 `LR.aq` 实现 sequence-consistent load，用 `AMOSWAP.rl x0,...` 实现 sequence-consistent store，但规范指出前者可能受 LR/SC eventual-success 机制影响，后者引入无用的读和额外排序约束。`Zalasr` 的目标正是避免这种“为了排序而做额外 RMW”的办法。T1-VERIFIED: [A §12.1.4](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

### 7.2 指令形式

| 类别 | 指令形式 | 必须置位的排序位 | 宽度 | 标签 |
| --- | --- | --- | --- | --- |
| load-acquire | `lb/lh/lw/ld.{aq,aqrl} rd, (rs1)` | `aq` 必须为 1；`rl` 可选 | B/H/W，`ld` 仅 RV64 | T1-VERIFIED |
| store-release | `sb/sh/sw/sd.{rl,aqrl} rs2, (rs1)` | `rl` 必须为 1；`aq` 可选 | B/H/W，`sd` 仅 RV64 | T1-VERIFIED |

load-acquire 固有 acquire-RCsc 注记；若选 `aqrl`，还带 release-RCsc。store-release 固有 release-RCsc；若选 `aqrl`，还带 acquire-RCsc。没有 `aq` 的 load 编码、没有 `rl` 的 store 编码均为 RESERVED。T1-VERIFIED: [Zalasr §16.1.2--§16.1.3](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html)

| 不提供的单独形式 | 规范给出的原因 | 容易混淆之处 | 标签 |
| --- | --- | --- | --- |
| `aq=rl=0` 的“仅保证原子” load/store | 正确对齐的普通 load/store 已可做到 | `Zalasr` 的核心不是凭空创造普通 load/store 的原子性，而是把 acquire/release 注记放入独立访问 | T1-VERIFIED |
| 仅 `rl` 的 load-release | 语言级 memory model 不支持这种常规接口 | `aqrl` load 仍是合法的 acquire+release 形式；被保留的是“只有 rl”的编码 | T1-VERIFIED |
| 仅 `aq` 的 store-acquire | 语言级 memory model 不支持这种常规接口 | `aqrl` store 仍是合法的 acquire+release 形式；被保留的是“只有 aq”的编码 | T1-VERIFIED |

窄 load 的结果符号扩展到 `rd`，store 只取 `rs2` 低位。访问要求自然对齐；若所有访问字节都在同一 Misaligned Atomicity Granule（MAG）PMA 内，可免于对齐异常，并在 RVWMO 中作为一个原子 memory operation。T1-VERIFIED: [Zalasr §16.1.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html)

### 7.3 它补足 `A` 的什么不足

`Zalasr` 填补的是 **独立且单向的 acquire/release 排序粒度**：

| 需要表达的语言级原语 | 只有 `A` 时的代价 | `Zalasr` 的表达 | 标签 |
| --- | --- | --- | --- |
| `atomic_load(memory_order_acquire)` | `LR.aq` 可能携带 reservation / 后续 `SC` 语境 | `lw.aq`、`ld.aq` 等独立 load | T1-VERIFIED |
| `atomic_store(memory_order_release)` | `AMOSWAP.rl` 是 RMW，带无用读取 | `sw.rl`、`sd.rl` 等独立 store | T1-VERIFIED |
| B/H 有序 load/store | `A` 没有匹配的 standalone atomic load/store | `lb/lh.aq`、`sb/sh.rl` | T1-VERIFIED |

`Zalasr` 可以独立实现；它与 `Zaamo`、`Zalrsc`、`Zabha` 是互补而不是“替代 A”。Zalasr 原文说，结合 `Zaamo`、`Zabha` 与 `Zalasr`，C++ 原子操作可以获得单指令支持；本文只把这句话用于说明非 RMW load/store 的缺口被补足，不将它外推为某个 `compare_exchange` 编码的保证。`A` 的 CAS 示例由 LR/SC 循环构造，`AMOCAS` 的专用编码则由 `Zacas` 定义。因此，若 ABI、工具链或手写汇编要求一个原生的 **单条 CAS 指令**，应显式要求 `Zacas`；最后一句是对两章规范的 INTERPRETIVE 归纳。T1-VERIFIED: [Zalasr](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html)；[A CAS example](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；[Zacas](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)

和所有 `aq/rl` 原子访问一样，`Zalasr` 不会自动跨越 memory/I/O 两个地址域排序。它减少的是本域内不必要的 `FENCE` 或 RMW，不是删除跨域 `FENCE` 的许可证。T1-VERIFIED: [A ordering](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

## 8. 四项扩展如何组合

| 场景 | 最小的 ISA 关注点 | 主要解决的问题 | 仍须确认 |
| --- | --- | --- | --- |
| 锁竞争者等待解锁 | `Zalrsc + Zawrs` | 在没有状态变化时避免持续轮询 | 重新检查谓词；中断、`TW/VTW`、reservation 行为 |
| 无锁栈 / 队列的 compare-exchange | `Zaamo + Zacas` | 单指令 CAS；RV64 可用 Q-CAS 表达 pointer+counter | `AMOCASW/D/Q` PMA、ABA 算法设计、失败路径排序 |
| 字节标志或半字计数器 | `Zaamo + Zabha` | 与数据实际宽度一致的 RMW | B/H 对齐、目标区域 AMO 支持；需要 CAS 时再加 `Zacas` |
| C/C++ acquire-load / release-store | `Zalasr` | 避免用 LR 或 AMOSWAP 伪装独立有序访问 | ABI mapping、跨 memory/I/O 域的 `FENCE`、实际编译输出 |

这些扩展按能力维度互补：`Zawrs` 是等待，`Zacas` 是条件 RMW，`Zabha` 是更细的 RMW 宽度，`Zalasr` 是非 RMW 的有序访问。它们不是一个由 `A` 自动蕴含的单一功能包。T1-VERIFIED: [A](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；[Zawrs](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)；[Zacas](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)；[Zabha](https://docs.riscv.org/reference/isa/v20260120/unpriv/zabha.html)；[Zalasr](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html)

## 9. RVA23、PMA 与平台边界

### 9.1 `A` 与 RVA23 不能混写

RVA23U64 是为 64-bit 应用处理器规定的软件基线。它把 `A`、`Ziccrse`（coherent/cacheable main memory 的 `RsrvEventual`）、`Ziccamoa`（该类 main memory 支持 `A` 中的全部 atomics）和 `Za64rs` 列为 mandatory；`Zawrs` 也是新增 mandatory。T1-VERIFIED: [RVA23 mandatory extensions](https://docs.riscv.org/reference/rva23/v1.0/rva23-profiles.html)

| RVA23U64 v1.0 项 | profile 承诺 | 不能据此声称 | 标签 |
| --- | --- | --- | --- |
| `A` | mandatory | 每个地址都支持 A 的所有原子；需看区域 PMA | T1-VERIFIED |
| `Zawrs` | mandatory | 一切 RISC-V CPU 或任何 guest 都有 Zawrs | T1-VERIFIED |
| `Zabha`、`Zacas` | development options，意图成为未来 profile 的 mandatory | 当前 RVA23 已强制 B/H AMO 或 CAS | T1-VERIFIED |
| `Ziccamoc` | development option；要求主存有 `AMOCASQ` PMA 支持 | 只要有 `Zacas` 就任意地址可 Q-CAS | T1-VERIFIED |
| `Zalasr` | 未列入该表的 mandatory/development option | RVA23 自动包含 `Zalasr` | T1-VERIFIED |

特别地，`Ziccamoa` 的“all atomics in A”范围仅为 `A` 本身，不能扩写成 `Zacas`、`Zabha` 或 `Zalasr` 的支持声明。T1-VERIFIED: [RVA23 v1.0](https://docs.riscv.org/reference/rva23/v1.0/rva23-profiles.html)

### 9.2 地址区域与 I/O

| 要核验的层次 | 问题 | 与四项扩展的关系 | 标签 |
| --- | --- | --- | --- |
| ISA decode | hart 是否实现该独立扩展 | `A` 的存在不证明其余四项 | T1-VERIFIED |
| PMA / memory map | 该物理地址支持何种 AMO / reservation / MAG / CAS 等级 | `Zacas` 特别需要 `AMOCASW/D/Q`；Zabha、Zalasr 也不能从“CPU 支持”外推到任意区域 | T1-VERIFIED / UNVERIFIED |
| 地址域 | 访问是 memory 还是 I/O | `aq/rl` 只排序访问所在域；跨域用 `FENCE` | T1-VERIFIED |
| 设备协议 | MMIO 寄存器是否允许相应宽度和 RMW | Zabha 避免宽访问副作用，但不证明外设接受 subword AMO | UNVERIFIED |

RVWMO 的正式模型针对 regular main memory；本课程不把它扩写为任意 non-idempotent I/O 的完整 RCsc 保证。对于 MMIO，除 ISA/PMA 以外还必须取得具体 SoC、桥接器和设备协议证据。T1-VERIFIED: [A ordering](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；[Zabha motivation](https://docs.riscv.org/reference/isa/v20260120/unpriv/zabha.html)；具体平台为 UNVERIFIED。

## 10. 软件证据与跨架构锚点

### 10.1 工具链

本次查阅的上游 LLVM RISC-V 文档将 `Zabha`、`Zacas`、`Zalasr` 列为 “Supported”，将 `Zawrs` 列为 “Assembly Support”。这仅说明该上游文档当前记录的编译器能力，不等于本地 Clang/GCC、汇编器、链接器、C 库、内核或 CPU 已支持同一组合。T1-VERIFIED: [LLVM RISC-V Target User Guide](https://llvm.org/docs/RISCVUsage.html)

| 验证对象 | 必须实际检查的证据 | 当前结论 |
| --- | --- | --- |
| 汇编器 | 目标版本能否接受相应 `-march` 和 mnemonic | UNVERIFIED |
| 编译器 lowering | `stdatomic` / `__atomic` 在目标优化级生成什么指令 | UNVERIFIED |
| ELF / loader | 二进制是否声明并在运行时要求独立扩展 | UNVERIFIED |
| CPU / 仿真器 | 非法指令、PMA fault、并发压力和 I/O 行为 | UNVERIFIED |

### 10.2 概念性竞争锚点

下表只帮助建立直觉，不把不同 ISA 的内存模型、进展保证、功耗或异常语义视为等价。

| RISC-V 能力 | 概念上接近的 Arm / x86 接口 | 必须保留的差异 | 标签 |
| --- | --- | --- | --- |
| `Zawrs` | Arm `WFE/SEV` 类等待、x86 `MONITOR/MWAIT` 或 `UMONITOR/UMWAIT` 类地址等待 | Zawrs 明确绑定 RISC-V reservation set，且可任意提前返回；不作逐指令等价主张 | T1-VERIFIED（RISC-V）/ INTERPRETIVE |
| `Zacas` | AArch64 LSE CAS、x86 `LOCK CMPXCHG` / `CMPXCHG16B` | 宽度、寄存器约定、失败排序与 memory model 均需分别核验 | T1-VERIFIED（RISC-V / x86 comparator）/ INTERPRETIVE |
| `Zabha` | subword locked RMW 的程序接口 | 不能从接口相似推出 I/O 副作用或性能相同 | T1-VERIFIED（RISC-V）/ INTERPRETIVE |
| `Zalasr` | AArch64 acquire-load / release-store 风格接口 | `aq/rl` 的地址域规则与各 ISA 的模型不能逐位映射 | T1-VERIFIED（RISC-V）/ INTERPRETIVE |

x86 的 `CMPXCHG`、`CMPXCHG8B`、`CMPXCHG16B` 与 `XADD` 可配合 `LOCK` 成为原子内存操作，是 `Zacas` / AMO 的有用接口类比；它不替代对 RISC-V `aq/rl` 和 PMA 的分析。T1-VERIFIED（x86 comparator）: [AMD64 APM](https://docs.amd.com/api/khub/documents/sfvvekC9mDflu6vd3R0NXA/content)

## 11. 六维总结

| 维度 | 结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| 1. Ratification & maturity | `Zawrs 1.01`、`Zacas 1.0.0`、`Zabha 1.0`、`Zalasr 1.0` 位于 `v20260120` ratified library | ratified ISA 不等于 profile mandatory 或产品已部署 | T1-VERIFIED |
| 2. ISA semantics | 四项分别补足等待、CAS、B/H RMW、独立 acquire/release load/store | 它们不能互相自动替代，也不由 `A` 自动蕴含 | T1-VERIFIED |
| 3. Platform requirements | PMA、reservation、MAG、memory/I/O 域决定具体地址能否安全使用 | 特别是 `AMOCAS.Q`、MMIO 和未对齐访问不能只看 ISA 名称 | T1-VERIFIED / UNVERIFIED |
| 4. Software evidence | 上游 LLVM 文档记录了不同程度的支持 | 本地生成代码、ABI 和运行时行为尚未验证 | T1-VERIFIED / UNVERIFIED |
| 5. Competitive anchor | 可分别类比 wait、CAS、subword RMW、acquire/release 接口 | 不是 Arm/x86 内存模型或性能等价声明 | INTERPRETIVE |
| 6. Deployment intent | 面向低功耗等待、无锁算法、紧凑数据结构和语言级原子编译 | 采用哪一项取决于 profile、PMA、工具链和软件兼容性 | T1-VERIFIED / UNVERIFIED |

## 12. 常见误解

| 误解 | 正确表述 | 标签 |
| --- | --- | --- |
| “有 `A` 就有四个其他原子扩展。” | `A` 只由 `Zaamo` 与 `Zalrsc` 组成；四项均为独立扩展。 | T1-VERIFIED |
| “`WRS` 被唤醒说明等待值已改变。” | 实现可任意提前结束停顿，且中断、timeout 等也会结束；必须重读检查。 | T1-VERIFIED |
| “单条 CAS 自动解决 ABA。” | CAS 仍可能观察 A→B→A；`AMOCAS.Q` 可供 pointer+counter 等算法使用。 | T1-VERIFIED / INTERPRETIVE |
| “`Zabha` 有所有 B/H 原子，包括 LR/SC。” | 它只提供 B/H AMO；规范刻意省略 B/H LR/SC。 | T1-VERIFIED |
| “`Zalasr` 是新的 RMW 指令。” | 它是独立的 load 或 store，核心是 acquire/release 注记。 | T1-VERIFIED |
| “`aqrl` 可以删除所有 `FENCE`。” | `aq/rl` 只排序该访问所在的 memory 或 I/O 域；跨域仍需 `FENCE`。 | T1-VERIFIED |
| “RVA23 已强制四项。” | RVA23U64 强制 `A` 与 `Zawrs`；`Zabha`、`Zacas` 是 development options，`Zalasr` 未列入该表。 | T1-VERIFIED |
| “实现 `Zacas` 就能向所有地址发 `AMOCAS.Q`。” | 每个地址区域还受递进的 `AMOCASW/D/Q` PMA 支持限制。 | T1-VERIFIED |

## 13. 验证清单

在把本文用于新核、板卡、固件或课程实验前，逐项检查：

- [ ] 重新打开 [v20260120 unprivileged index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) 以及 [A](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)、[Zawrs](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html)、[Zacas](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html)、[Zabha](https://docs.riscv.org/reference/isa/v20260120/unpriv/zabha.html)、[Zalasr](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html)，确认版本和章节未被新的 ratified release 取代。
- [ ] 检查目标硬件的 ISA discovery、ELF 属性或平台文档，分别确认 `zawrs`、`zacas`、`zabha`、`zalasr`；不要只检查 `misa.A`。
- [ ] 若声称 RVA23 兼容，重新核验 [RVA23 v1.0](https://docs.riscv.org/reference/rva23/v1.0/rva23-profiles.html) 中 `A`、`Zawrs`、`Zabha`、`Zacas` 和 `Ziccamoc` 的 precise status。
- [ ] 对每个目标物理地址检查 [Atomicity PMAs](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html#atomicity-pmas)：AMO 等级、reservation/eventuality、MAG、`AMOCASW/D/Q`、memory/I/O 分类。
- [ ] 对 `Zawrs` 循环验证：WRS 返回后重读谓词、处理中断和 timeout、以及虚拟化下 `TW/VTW` 行为。
- [ ] 对 `Zacas` 验证：保存 expected、正确处理失败时只有 acquire 而无 release、按对象宽度检查对齐与 PMA、以算法而非 CAS 本身处理 ABA。
- [ ] 对 `Zabha` 验证：B/H 对齐、符号扩展、`rs2` 高位忽略、MMIO 寄存器副作用；若需要 B/H CAS，确认 `Zacas` 同时存在。
- [ ] 对 `Zalasr` 验证：需要的是 `aq` load 还是 `rl` store，是否跨 memory/I/O 域需要 `FENCE`，以及 MAG 是否覆盖可能未对齐的访问。
- [ ] 用目标 GCC/LLVM、汇编器、链接器和 C 库编译最小 `stdatomic` / `__atomic` 样例，保存 `objdump -dr` 结果；把汇编接受、代码生成和硬件执行分开判定。
- [ ] 在并发压力测试和目标 MMIO 环境中验证功能。一次板卡实测只能佐证该平台，不能替代 ISA 的普适语义。

## 14. 参考资料

1. [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html) - `v20260120` 的 ratified 文档入口。
2. ["A" Extension for Atomic Instructions, Version 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html) - `Zaamo`、`Zalrsc`、`aq/rl`、LR/SC eventuality 与 AMO 基线。
3. ["Zawrs" Extension for Wait-on-Reservation-Set Instructions, Version 1.01](https://docs.riscv.org/reference/isa/v20260120/unpriv/zawrs.html) - `WRS.NTO` / `WRS.STO` 语义与特权边界。
4. ["Zacas" Extension for Atomic Compare-and-Swap (CAS) Instructions, Version 1.0.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zacas.html) - `AMOCAS.W/D/Q`、失败排序和 Q-CAS 示例。
5. ["Zabha" Extension for Byte and Halfword Atomic Memory Operations, Version 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zabha.html) - B/H AMO 及宽操作模拟的局限。
6. ["Zalasr" Atomic Load-Acquire and Store-Release Instructions, Version 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/zalasr.html) - 独立 acquire-load / release-store 语义。
7. [RVA23 Profiles v1.0](https://docs.riscv.org/reference/rva23/v1.0/rva23-profiles.html) - profile 中 A、Zawrs、Zabha、Zacas 的 status。
8. [RISC-V Privileged ISA: Atomicity PMAs](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html#atomicity-pmas) - AMO、reservation、MAG 与 `AMOCASW/D/Q` 地址区域能力。
9. [LLVM RISC-V Target User Guide](https://llvm.org/docs/RISCVUsage.html) - 上游 LLVM 的扩展支持状态。
