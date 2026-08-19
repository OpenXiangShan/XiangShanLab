# RISC-V A（Atomic）扩展解析

> 规范基线：RISC-V Unprivileged ISA `v20260120`，章节 *"A" Extension for Atomic Instructions, Version 2.1*。本文按 `spec-learning` 的证据层级编写：规范正文为主要依据，所有跨规范、工具链和平台结论标注可信度。

## 1. 定位与范围

`A` 是 RISC-V 的原子指令扩展，用于在多处理器共享内存中构建同步原语。它提供两类基本能力：

- `Zalrsc`：加载保留/条件存储（LR/SC）指令；
- `Zaamo`：原子读-改-写（AMO）指令。

`A` 并不等同于所有后续原子相关扩展。`Zawrs`、`Zacas`、`Zabha`、`Zalasr` 在当前手册中均是独立章节；是否实现它们必须由 ISA/ELF 属性、工具链 `-march` 字符串或平台文档分别确认，不能从有 `A` 直接推出。`misa.A` 若对软件可见，只能报告 `A`，不能分别证明这些独立扩展。 T1-VERIFIED: [v20260120 unprivileged index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html)；[A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

| 层次 | 本文结论 | 证据等级 |
| --- | --- | --- |
| ISA 语义 | `A = Zaamo + Zalrsc`，提供 AMO 与 LR/SC | T1-VERIFIED |
| 内存排序 | `aq`、`rl` 位对同一地址域约束 acquire/release；跨内存与 I/O 域需要 `FENCE` | T1-VERIFIED |
| 微架构 | 保留集大小、失效时机、LR/SC 失败实现细节可由实现定义 | T1-VERIFIED |
| 平台 | 能否可靠获得 LR/SC eventuality、是否存在原子性粒度 PMA，取决于平台约束 | T1-VERIFIED |
| 软件 | 编译器和 ABI 如何选择指令序列需由其版本与目标 ISA 选项复核 | T1-VERIFIED / UNVERIFIED |

本文讨论的是程序员可见的 ISA 契约，不把“使用了 A 指令”误写成“某个具体核、总线或缓存已经正确支持原子性”。

## 2. 规范来源与阅读方法

### 2.1 证据优先级

| 导航层级 | 本次来源 | 用途 |
| --- | --- | --- |
| Layer 1：UDB | [UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 机器可读结构和扩展分解的交叉检查；其连续部署产物不替代版本固定的 ratified 语义锚点 |
| Layer 2：Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | `norm:` 锚点的规范性解释 |
| Layer 3：Ratified ISA | [v20260120 A 扩展正文](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html) | 本文的指令语义、排序、对齐、LR/SC 循环主依据 |
| Layer 3：Ratified platform | [RVA23 Profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html) / [Machine PMA](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html) | profile 与区域级 AMO/reservation/MAG 约束 |
| Tier-1 supporting software | [GCC RISC-V options](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Options.html) / [RISC-V psABI atomics](https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-atomic.adoc) | 官方上游工具链和 ABI 映射证据 |

### 2.2 本文中的标签

- **T1-VERIFIED**：可直接在 RISC-V ratified 规范、规范性规则、profile，或官方上游工具链/ABI 文档中定位。
- **T2-CROSS-CHECKED**：由可靠的补充来源交叉支持，例如厂商技术材料；它不覆盖或推翻 T1 结论。
- **UNVERIFIED**：特定实现、操作系统、SoC 或测试环境尚无本地证据；不能作为已证实事实。

## 3. A 扩展解决的问题

普通的“先读、在寄存器中计算、再写回”由多条指令构成；其他 hart 可以在两次内存访问之间观察或修改该位置。因此它无法单独构成互斥锁、无锁计数器、引用计数或 compare-and-swap（CAS）式同步。T1-VERIFIED: [A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

`A` 将原子更新暴露为两种接口：

1. **AMO**：以一条指令完成读-改-写，适合 `fetch_add`、交换、位操作及 min/max。
2. **LR/SC**：先以 `LR` 建立保留，再以 `SC` 尝试提交新值，适合 CAS、复杂条件更新和软件锁。

两者允许软件根据算法选择；规范并未要求实现把 LR/SC 翻译为某一种特定的缓存一致性事务或总线锁定。 T1-VERIFIED: [A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

## 4. 成熟度、版本与编码概览

### 4.1 版本锚点与演进边界

| 时间/状态 | 项目 | 对本文的含义 | 标签 |
| --- | --- | --- | --- |
| RATIFIED 2019-12 | `A` 2.1 | `A` 2.1 已 ratified；后续手册版本的变化不应被误读为新的 `A` 指令集版本 | T1-VERIFIED [UDB 历史版本说明](https://riscv.github.io/riscv-isa-manual/snapshot/spec/) |
| RATIFIED 2024-10-17 | RVA23 Profile v1.0 | 应用处理器 profile 对 `A`、原子 PMA 与保留集给出额外承诺 | T1-VERIFIED [RVA23](https://docs.riscv.org/reference/rva23/index.html) |
| RATIFIED LIBRARY 2026-01 | Unprivileged ISA `v20260120` | 本文采用的规范快照；其 A 章节为 Version 2.1 | T1-VERIFIED [RVI 文档库](https://docs.riscv.org/reference/home/index.html) |

**SPEC-UPDATE-ALERT：** UDB 的连续部署产物和手册快照可随主分支变化；本文把用户指定的 `v20260120` 固定为可复现的语义锚点。评审新项目时，应重新检查 [UDB 部署页](https://riscv.github.io/riscv-unified-db/) 与 [RVI ratified library](https://docs.riscv.org/reference/home/index.html)，不能只沿用本文的版本号。 T1-VERIFIED: [RVI ratified library](https://docs.riscv.org/reference/home/index.html)

### 4.2 五层文档导航结果

| 层级 | 本次检查的来源 | 用法与结论 | 标签 |
| --- | --- | --- | --- |
| Layer 1：UDB | [UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) / [A 章节生成页](https://riscv.github.io/riscv-unified-db/manual/html/isa/isa_20240411/chapters/a-st-ext.html) | 用于机器可读/生成文档交叉检查 `A = Zaamo + Zalrsc`；UDB 连续部署不是替代 ratified 版本锚点 | T1-VERIFIED |
| Layer 2：Normative | [Normative Rules 指南](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | `norm:` 标识的规则用于判定约束是否为规范性要求 | T1-VERIFIED |
| Layer 3：Ratified | [v20260120 A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html) | 本文 ISA 行为的主依据 | T1-VERIFIED |
| Layer 3：Ratified profile | [RVA23 profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html) | 仅用于 RVA23 的平台保证，不外推给所有 RISC-V 实现 | T1-VERIFIED |
| Layer 3：Ratified privileged ISA | [Machine PMA](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html) | 用于 AMO 支持等级、保留可用性与 MAG 对齐边界 | T1-VERIFIED |

### 4.3 通用编码布局

标准 `A` 指令使用 `opcode=0101111`。其字段布局为：

```text
31          27 26 25 24       20 19       15 14   12 11        7 6       0
+-------------+--+--+-----------+-----------+-------+-----------+---------+
|   funct5     |aq|rl|    rs2    |    rs1    | funct3|    rd     |0101111  |
+-------------+--+--+-----------+-----------+-------+-----------+---------+
```

`funct3=010` 选择 32-bit 的 `.W` 形式，`funct3=011` 选择 64-bit 的 `.D` 形式；`.D` 仅在 RV64 可用。`LR` 的 `rs2` 编码为零，`SC` 和 AMO 使用 `rs2` 作为待写入值或操作数。 T1-VERIFIED: [RV32/64G 指令表](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)

### 4.4 指令家族

| 类别 | 指令 | `funct5` | 程序员可见结果 | 标签 |
| --- | --- | --- | --- |
| 保留加载 | `LR.W` / `LR.D` | `00010` | 从 `rs1` 加载旧值到 `rd`，并建立覆盖所访问字节的 reservation set | T1-VERIFIED |
| 条件存储 | `SC.W` / `SC.D` | `00011` | reservation 有效且覆盖目标时写入 `rs2`；成功写 `rd=0`，失败不写内存且写 `rd!=0` | T1-VERIFIED |
| 交换 | `AMOSWAP.W/D` | `00001` | 返回旧值，并把 `rs2` 写到目标地址 | T1-VERIFIED |
| 加法 | `AMOADD.W/D` | `00000` | 返回旧值，并原子写回 `old + rs2` | T1-VERIFIED |
| 逻辑 | `AMOXOR` / `AMOAND` / `AMOOR` | `00100` / `01100` / `01000` | 返回旧值，并写回相应位运算结果 | T1-VERIFIED |
| 有符号比较 | `AMOMIN` / `AMOMAX` | `10000` / `10100` | 返回旧值，并写回有符号最小/最大值 | T1-VERIFIED |
| 无符号比较 | `AMOMINU` / `AMOMAXU` | `11000` / `11100` | 返回旧值，并写回无符号最小/最大值 | T1-VERIFIED |

表中的 `AMO*` 均有 `.W` 形式，RV64 还具有 `.D` 形式。RV64 上 `.W` AMO 写入 `rd` 的旧 32-bit 值会符号扩展，`rs2` 的高 32 位被忽略；这与把 `.W` 误当作零扩展 64-bit 运算不同。 T1-VERIFIED: [A 2.1, Zaamo](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

## 5. 原子性与排序：两个独立问题

### 5.1 先分清“不可分割”与“可观察顺序”

AMO 的读、计算和写回是一个原子读-改-写；LR/SC 成功配对时也提供其规定的原子性。**未设置 `aq`/`rl` 仍然是原子操作**，只是没有由这两个位额外引入 acquire/release 排序。把“没有 `.aq/.rl`”说成“不是原子”是错误的。 T1-VERIFIED: [A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

RISC-V 的基础模型是 RVWMO；原子性并不自动把所有前后访存变成全局顺序一致。需要的可观察顺序由 `aq`、`rl` 和必要时的 `FENCE` 共同建立。 T1-VERIFIED: [RVWMO explanatory material](https://docs.riscv.org/reference/isa/unpriv/mm-eplan.html)

### 5.2 `aq` / `rl` 位的含义

| `aq` | `rl` | 对同一地址域的附加排序 | 典型用途 | 标签 |
| ---: | ---: | --- | --- | --- |
| 0 | 0 | 不增加 acquire/release 约束 | relaxed 原子更新、并行归约 | T1-VERIFIED |
| 1 | 0 | acquire：该原子访问不能被同一地址域中其后的访存从外部观察为排在它之前 | 获取锁、读取已发布数据 | T1-VERIFIED |
| 0 | 1 | release：同一地址域中其前的访存不能被从外部观察为排在它之后 | 解锁、发布数据 | T1-VERIFIED |
| 1 | 1 | 在该原子访问的地址域内提供 sequentially consistent 语义 | 需要较强顺序的单一域同步点 | T1-VERIFIED |

地址空间由执行环境划分为 **memory** 与 **I/O** 两个地址域。原子指令的 `aq`/`rl` 只约束它访问所在的域：访问 memory 的 AMO 不会仅凭这两个位排序 I/O，反之亦然。跨 memory/I/O 域的排序必须使用适当的 `FENCE`；`aqrl` 不是跨域“万能屏障”。 T1-VERIFIED: [A 2.1, ordering](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

对 LR/SC 配对，常见的 release-consistency 写法是 `LR.aq` 与 `SC.rl`。单独使用 `LR.rl` 或 `SC.aq` 没有相应的常规 acquire/release 效果，规范明确不建议把它们作为独立操作使用；不要把它们当作更强的 fence。 T1-VERIFIED: [A 2.1, LR/SC ordering](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

### 5.3 互斥锁示例

以下是基于 `AMOSWAP` 的测试并设置锁。`amoswap.w.aq` 读取旧锁值并尝试写 1；只有读到 0 的 hart 得到锁。释放端以 `.rl` 写 0，使临界区内的先前 memory 域访问先于解锁对外可见。 T1-VERIFIED: [A 2.1, ordering example](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

```asm
# a0 = &lock, t0 = 1
again:
    amoswap.w.aq t1, t0, (a0)  # t1 <- old lock; lock <- 1
    bnez         t1, again     # old lock != 0: retry

    # critical section

    amoswap.w.rl x0, x0, (a0)  # lock <- 0; discard old value
```

这段代码只说明 memory 域锁的基本排序方式。若临界区还需要与 MMIO 可观察顺序协作，必须依据所访问 I/O 区域的 PMA 与协议加入合适的 `FENCE`；不能从该示例自动推出设备层的排序或原子性。 [T1-VERIFIED / UNVERIFIED：具体 I/O 平台策略未在本文给出]

## 6. `Zalrsc`：LR/SC 的精确契约

### 6.1 基本状态机

`LR.W` 从 `rs1` 指向的 word 读取数据到 `rd`，同时在本 hart 上登记一个至少覆盖该 word 字节的 reservation set。`SC.W` 只有在 reservation 仍有效、且 reservation set 包含其写入字节时才会把 `rs2` 写到 `rs1`；成功时 `rd=0`，失败时不写内存且 `rd` 为非零。`LR.D`/`SC.D` 对 doubleword 的规则相同，只在 RV64 可用。 T1-VERIFIED: [A 2.1, Zalrsc](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

无论 `SC` 成功还是失败，执行 `SC` 都会使该 hart 的 reservation 失效。失败的 `SC` 在内存保护角度可被当成一次 store，因此它也可能触发与写访问相同的权限/PMP/PMA 检查；“失败就不会有任何副作用或异常”不是 ISA 保证。 T1-VERIFIED: [A 2.1, Zalrsc](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

| 事件 | 架构保证 | 不应假定的实现细节 |
| --- | --- | --- |
| `LR` | 读出当前值并建立 reservation | reservation 恰等于一个 cache line、固定为某个字节数 |
| 与最新 `LR` 同址同宽的 `SC` | reservation 有效时写入且状态为 0；否则不写且状态非零 | 非零失败码的具体编码，或一次失败的唯一原因 |
| `SC` 执行完毕 | 本 hart 的 reservation 被无效化 | 失败不会触发权限检查或不会影响本地实现状态 |
| 其他 hart/设备写 reservation set | 可使本 hart 的 `SC` 失败 | 所有失效都必须以同一缓存/总线机制出现 |

同一 hart 只追踪最新 `LR` 所建立的 reservation；`SC` 必须针对该最新 `LR` 的同一有效地址和同一数据大小，才有受约束循环的进展资格。实现可以因保守的 reservation set、上下文切换、异常或内部资源策略而让 `SC` 失败，只受下面的 eventuality 规则约束。 T1-VERIFIED: [A 2.1, Zalrsc](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

### 6.2 受约束 LR/SC 循环（constrained loop）

受约束循环是可移植软件获取 LR/SC 进展保证的必要形式，不是一个可选的性能建议。其关键限制如下：

| 约束 | 规范要求 | 标签 |
| --- | --- | --- |
| 指令数量与布局 | 整个循环至多 16 条指令，且顺序放置在内存中 | T1-VERIFIED |
| LR 到 SC 的动态路径 | 仅能包含允许的 base `I` 指令；禁止 load、store、后向跳转、已取后向分支、`JALR`、`FENCE` 和 `SYSTEM` | T1-VERIFIED |
| `C` 扩展 | 若支持 `C`，允许相应压缩形式，但不会把被禁类别变为允许 | T1-VERIFIED |
| 失败重试代码 | 可以用回到 LR/SC 序列的后向跳转/分支重试；除此以外仍受相同限制 | T1-VERIFIED |
| 地址与大小 | `SC` 必须匹配同 hart 最新 `LR` 的有效地址和数据大小 | T1-VERIFIED |
| 地址区域 | LR 与 SC 必须落在执行环境声明具有 LR/SC eventuality 属性的内存区域 | T1-VERIFIED |

“最多 16 条顺序放置指令”是规范约束。规范说明其设计目标是在 base ISA 下适配 64 个连续指令字节，但 **不能把 16 条指令机械等同于 64 bytes**，尤其在存在压缩指令时。 T1-VERIFIED: [A 2.1, Eventual Success](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

一个符合结构的 word CAS 轮廓如下；真实 CAS 还需在失败时把观察到的值返回给调用者，并选择对应的 C/C++ memory order 映射。

```asm
# a0 = address, a1 = expected, a2 = desired
retry:
    lr.w.aq t0, (a0)          # 建立 reservation 并读取旧值
    bne     t0, a1, mismatch  # 条件不满足，退出本次尝试
    sc.w.rl t1, a2, (a0)      # t1=0 成功；t1!=0 则 reservation 已失效
    bnez    t1, retry
    # success
mismatch:
    # t0 是本次 LR 观察到的值
```

上例在 `LR` 和 `SC` 之间只有允许的整数比较/分支，在失败后回跳到 `LR`，因而具备受约束循环的形状；但能否取得 eventuality 仍取决于地址区域的 PMA/执行环境声明。 T1-VERIFIED: [A 2.1, Eventual Success](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

### 6.3 eventuality 是 livelock-freedom，不是“SC 必成”

对于进入受约束循环的 hart `H`，执行环境必须最终使下列事件之一发生：

1. `H` 或其他 hart 对 `H` 的 reservation set 执行成功的 `SC`；
2. 其他 hart 执行无条件 store/AMO，或其他设备写入该 reservation set；
3. `H` 通过分支或跳转退出循环；
4. `H` 发生 trap。

因此，若一组 hart 都运行受约束循环，且没有其他 hart 或设备对相关 reservation set 执行无条件 store/AMO，则**至少一个** hart 最终会退出其循环。这是 livelock-freedom；它不是“每个 hart 都不会饥饿”，也不是“每条 `SC` 或每个 hart 最终必定成功”。冲突写持续存在时，规范不保证任何 hart 退出。 T1-VERIFIED: [A 2.1, Eventual Success](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

不满足这些结构约束的 LR/SC 序列属于 unconstrained sequence：某些实现上可能偶尔成功，也可能永远失败。可移植软件必须检测重复失败并准备不依赖 unconstrained LR/SC 的 fallback；不能以“在某个开发板上跑过”为通用进展证明。 T1-VERIFIED: [A 2.1, Eventual Success](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

### 6.4 保留与对齐

LR/SC 的地址必须自然对齐：`.W` 为 4-byte 对齐，`.D` 为 8-byte 对齐。后文的 Misaligned Atomicity Granule（MAG）只放宽特定条件下的 **AMO**，不改变 LR/SC 的对齐异常规则。 T1-VERIFIED: [Machine PMA](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html)

## 7. `Zaamo`：原子读-改-写操作

### 7.1 AMO 的可见效果

一条 AMO 按以下不可分割的语义执行：从 `rs1` 地址读出旧值，将旧值写入 `rd`，用 `rs2` 的原始值与旧值执行指定二元操作，再把结果写回原地址。它适合 fetch-and-add、位集合/清除、交换、并行归约和锁实现。若返回旧值无用，`rd=x0` 可以丢弃它。 T1-VERIFIED: [A 2.1, Zaamo](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

```asm
# a0 = &counter, a1 = increment
amoadd.w.aqrl a2, a1, (a0)  # a2 <- old counter; counter <- old counter + a1

# a0 = &flags, a1 = bit mask
amoor.w.rl x0, a1, (a0)    # flags <- flags | mask; 丢弃旧 flags
```

`.W` 和 `.D` 的差异必须按 XLEN 理解：

| 形式 | 可用架构 | 操作宽度 | RV64 中 `rd` 的值 | 标签 |
| --- | --- | --- | --- | --- |
| `.W` | RV32、RV64 | 32 bit | 旧 32-bit 值符号扩展至 64 bit；`rs2` 高 32 bit 忽略 | T1-VERIFIED |
| `.D` | RV64 | 64 bit | 返回旧 64-bit 值 | T1-VERIFIED |

### 7.2 PMA 与未对齐 AMO

默认情况下，AMO 地址必须按操作数自然对齐；否则可能发生 address-misaligned 或 access-fault 异常。access-fault 可表示该访问不应由陷阱处理程序模拟，因此“捕获未对齐异常后软件一定能模拟”也不是通用保证。 T1-VERIFIED: [A 2.1, Zaamo](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)

特权 ISA 定义了 **Misaligned Atomicity Granule PMA（MAG）**：若某个区域声明了 MAG，且一次 AMO 覆盖的所有字节都落在同一个自然对齐的粒度中，则该 AMO 可免于因对齐产生异常，并在 RVWMO 中作为单个内存操作原子执行。若区域未声明 MAG，或访问跨越粒度边界，则未对齐 AMO 会异常。 T1-VERIFIED: [Machine PMA, MAG](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html)

MAG 的范围有严格边界：它不适用于 LR/SC，也不自动使向量访问原子。它不是“所有未对齐访问都原子”的全局开关。 T1-VERIFIED: [Machine PMA, MAG](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html)

### 7.3 AMO 与 I/O 区域

一个实现支持 `A` 不代表任意物理地址都支持完整 AMO 集合。PMA 将区域的 AMO 能力分为 `AMONone`、`AMOSwap`、`AMOLogical` 和 `AMOArithmetic`；main memory 和 I/O 区域都可能只支持子集或完全不支持。`AMOArithmetic` 才覆盖 A 中定义的全部 AMO。 T1-VERIFIED: [Machine PMA, AMO PMA](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html)

对 memory-mapped I/O 使用 AMO 时，既要确认该区域的 AMO PMA 等级，也要确认 memory/I/O 域排序需求。ISA 给出可表达的原子操作与顺序位，但设备协议、桥接器和外设寄存器副作用仍是平台契约。 [T1-VERIFIED / UNVERIFIED：未指定具体 SoC 的 MMIO 原子支持]

## 8. 平台语义：ISA、PMA 与 Profile 不能混为一谈

### 8.1 三层承诺模型

| 层次 | 可以从该层得出的结论 | 不能从该层直接得出的结论 | 标签 |
| --- | --- | --- | --- |
| ISA：`A` | hart 可解码并执行 `Zaamo + Zalrsc` 定义的指令语义 | 任意物理地址支持所有 AMO；任意 LR/SC 循环有进展 | T1-VERIFIED |
| PMA / 执行环境 | 某地址范围的 AMO 等级、reservability、eventuality、MAG 等性质 | 所有进程可不经系统接口获知或使用这些属性 | T1-VERIFIED |
| Profile：RVA23 | 指定类别的应用处理器应向可移植软件提供统一的一组 ISA/PMA 承诺 | 所有 RISC-V 核都符合 RVA23，或 profile 覆盖未列出的扩展 | T1-VERIFIED |

因此，`-march=rv64gc`、`misa.A=1` 或反汇编中出现 `amoadd` 只证明“指令集能力”这一层。若算法依赖 LR/SC eventuality 或向特定区域发 AMO，还必须核验目标地址的 PMA 和平台 profile。 T1-VERIFIED: [A 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；[Machine PMA](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html)

### 8.2 RVA23 的原子相关承诺

RVA23 是面向 64-bit 应用处理器的软件基线，只有 `RVA23U64`（user-mode）与 `RVA23S64`（supervisor-mode）两个 profile。`A` 是 `RVA23U64` 的 mandatory extension；`RVA23S64` 包含其所需的非特权扩展。以下保证仅适用于符合该 profile 的实现，不是每个实现 A 的 RISC-V 核的普适性质。 T1-VERIFIED: [RVA23 profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html)

| RVA23 原子相关项 | 对 cacheable 且 coherent 的 main memory 的要求 | 与裸 `A` 的关系 | 标签 |
| --- | --- | --- | --- |
| `A` | 支持 A 原子指令 | ISA 功能基线 | T1-VERIFIED |
| `Ziccrse` | 支持 `RsrvEventual` | 为合规 constrained LR/SC loop 提供区域级进展属性 | T1-VERIFIED |
| `Ziccamoa` | 支持 A 中全部 atomics | 不是仅“核会解码 AMO”，而是规定的 main-memory 区域能力 | T1-VERIFIED |
| `Za64rs` | reservation set 连续、自然对齐，最大 64 bytes | 是最大范围约束，**不等于** reservation 固定为 64 B 或等于 cache line | T1-VERIFIED |

RVA23 将 `Zacas` 和 `Zabha` 放在 development options，而不是把它们包含进 A 的承诺。尤其 `Ziccamoa` 不能被扩写成“main memory 支持 CAS、byte/halfword AMO 等所有原子扩展”；它的范围是 **all atomics in A**。 T1-VERIFIED: [RVA23 profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html)

### 8.3 PMA 支持层次与软件决策

| 属性 | 取值/含义 | 软件含义 | 标签 |
| --- | --- | --- | --- |
| AMO PMA | `AMONone` / `AMOSwap` / `AMOLogical` / `AMOArithmetic` | 只有 `AMOArithmetic` 覆盖 A 的完整 AMO 集；I/O 或特殊 memory 可能较弱 | T1-VERIFIED |
| Reservability PMA | `RsrvNone` / `RsrvNonEventual` / `RsrvEventual` | `RsrvNonEventual` 允许 LR/SC，但软件必须为无法取得进展准备 fallback | T1-VERIFIED |
| MAG PMA | `MAGNN` 等粒度 | 只在访问完全落入同一粒度时放宽指定访问的未对齐原子性 | T1-VERIFIED |

规范要求执行环境传达具备 LR/SC eventuality 属性的区域；至于平台/固件如何向软件暴露 AMO PMA、MAG 或其他区域属性，本文没有针对任意 Linux、SBI、ACPI/Device Tree 或某一 SoC 的发现路径作出断言。前者为 T1-VERIFIED: [A 2.1, Eventual Success](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)；具体机器的可见接口与驱动支持为 **UNVERIFIED**，需要按平台文档和实测另行确认。

## 9. 软件、编译器与 ABI 证据

### 9.1 编译器 ISA 选项

GCC 的当前 RISC-V 选项文档列出 `a` 2.0/2.1、`zaamo` 1.0 和 `zalrsc` 1.0；`g` 是 `i,m,a,f,d,zicsr,zifencei` 的简称。GCC 2024 年合入的支持说明 `A` 蕴含 `Zaamo` 与 `Zalrsc`。这说明工具链接受这些 ISA 名称，但不替代 CPU、链接器或运行时对所选扩展的实际支持核验。 T1-VERIFIED: [GCC options](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Options.html)；[GCC change](https://gcc.gnu.org/pipermail/gcc-patches/2024-June/654330.html)

```text
# 典型目标 ISA 写法；具体工具链版本须先验证其支持的扩展版本。
-march=rv64gc
-march=rv64imafd_zicsr_zifencei_zaamo_zalrsc
-march=rva23u64
```

第二行是显式展开的示意，并非要求所有项目都手工替代 `a`。编译、汇编、链接和运行时的最低版本组合，以及目标硬件是否接受拆分扩展字符串，在没有本地工具链与板卡实测时均为 **UNVERIFIED**。

### 9.2 C/C++ 原子操作的映射

RISC-V ELF psABI 的 atomics 文档将 C/C++ 原子操作映射到 RVWMO 原语：有直接 AMO 对应的 RMW 操作可使用 `.aq`、`.rl`、`.aqrl`；没有直接 AMO 对应的操作使用 LR/SC 重试循环。它还特别区分不同原子 ABI 的互操作性。 T1-VERIFIED: [RISC-V Atomics ABI](https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-atomic.adoc)

| 语言层 memory order | 直接 AMO 的代表性映射 | LR/SC 的代表性映射 | 注意事项 | 标签 |
| --- | --- | --- | --- |
| relaxed | `amo<op>.w/d` | `lr; op; sc; retry` | 仍原子，但无额外 acquire/release | T1-VERIFIED |
| acquire | `amo<op>.aq` | `lr.aq; op; sc; retry` | 对读/改/写的语言模型映射，不等于任意 I/O 屏障 | T1-VERIFIED |
| release | `amo<op>.rl` | `lr; op; sc.rl; retry` | 需要结合对象地址所在域理解 | T1-VERIFIED |
| acq_rel | `amo<op>.aqrl` | `lr.aq; op; sc.rl; retry` | 常见 RMW 表达 | T1-VERIFIED |
| seq_cst | 依 ABI 映射使用 `.aqrl` 与可能的 fence | 依 ABI 映射；不能自行删去 ABI 要求的 fence | 不同 atomic ABI 可能不兼容 | T1-VERIFIED |

这张表描述 psABI 的 mapping 文档，不是对某个 GCC/LLVM 版本“必然生成完全相同汇编”的承诺。必须用实际 `-march`、优化级别、ABI 属性和反汇编验证输出；本课程文档没有运行编译器，因此具体代码生成是 **UNVERIFIED**。

### 9.3 操作系统与库

`A` 是用户态可见 ISA 扩展，但操作系统还承担调度、陷阱、PMA 暴露、可执行文件 ISA 属性和多库原子 ABI 兼容等系统层责任。本文未对 Linux 内核版本、glibc、musl、JVM、Rust 标准库或某个发行版的 A/Zaamo/Zalrsc 支持状态下结论；这些均为 **UNVERIFIED**，应在目标发行版和交叉工具链中分别验证。

## 10. 跨架构映射：相似接口不代表相同内存模型

本节满足 `spec-learning` 的竞争锚点要求，只比较程序员可见的同步原语。它不是性能、缓存一致性协议或 SoC 互连的等价性证明。

| 任务 | RISC-V | Arm AArch64 | x86-64 | 关键差异 | 标签 |
| --- | --- | --- | --- | --- |
| 条件 RMW | `LR/SC` 重试循环 | exclusive load/store（例如 `LDXR`/`STXR`，可带 acquire/release 变体） | `CMPXCHG` 加 `LOCK` 可提供原子 compare-exchange | RISC-V A 2.1 本身无单条 CAS；`Zacas` 是单独扩展 | T1-VERIFIED（RISC-V/AMD）/ T2-CROSS-CHECKED（Arm） |
| fetch-and-op | `AMOADD`、`AMOOR` 等 | 基线可用 exclusive-loop；Arm LSE 可给单条原子 RMW | `LOCK XADD` 等 | 指令形态不同，语言级原子接口可能相同 | T1-VERIFIED（RISC-V/AMD）/ T2-CROSS-CHECKED（Arm） |
| acquire/release | 同一原子指令的 `aq`/`rl` 位 | acquire/release load/store 或 exclusive 变体 | x86 内存模型与 `LOCK` 指令语义不能简单逐位对应 | 不能用 mnemonic 相似性替代各 ISA 的 memory model 分析 | T1-VERIFIED（RISC-V）/ T2-CROSS-CHECKED（Arm/x86 mapping） |
| 进展 | 合规 constrained LR/SC + `RsrvEventual` 有 livelock-freedom 条件 | exclusive 监视器的实现/进展语义需依 Arm 规范与平台确认 | CAS/LOCK 路径的进展语义依 x86 规范与系统条件确认 | 不把一方的“失败重试”规则移植为另一方保证 | T1-VERIFIED（RISC-V）/ UNVERIFIED（跨 ISA 进展强弱结论） |

Arm 官方材料展示了 AArch64 的 `LDXR`/`STXR`、带 acquire 的 `LDAXR`、带 release 的 `STLXR`，以及 LSE 的单条 `LDADD`/`CAS` 路径；AMD64 APM 说明 `LOCK` 可使特定 RMW 指令原子化，包含 `CMPXCHG`、`XADD` 等。它们可作为接口类比，但不会推导出 RISC-V 的 `aqrl` 与 Arm/x86 是逐指令等价的。 T1-VERIFIED（AMD）: [AMD64 APM](https://docs.amd.com/api/khub/documents/68GKiN0gMEd6bMddsmhPwg/content)；T2-CROSS-CHECKED（Arm example）: [Arm example](https://developer.arm.com/community/arm-community-blogs/b/tools-software-ides-blog/posts/compiler-flags-across-architectures-march-mtune-and-mcpu)

## 11. 六维总结

| 维度 | 结论 | 边界 | 标签 |
| --- | --- | --- | --- |
| 1. Ratification & maturity | `A` 2.1 是 ratified；当前课程锚点为 `v20260120` | 不因当前手册包含更多原子章节而自动扩大 A 的 ISA 范围 | T1-VERIFIED |
| 2. ISA semantics | A 由 `Zaamo` 和 `Zalrsc` 组成，提供 AMO 与 LR/SC；`aq`/`rl` 支持 release consistency 与同域 SC 语义 | 原子性、排序、跨域 I/O 排序必须分别判断 | T1-VERIFIED |
| 3. Platform requirements | PMA 定义 AMO、reservability、MAG；RVA23 给 coherent main memory 的 A 相关 profile 承诺 | A 本身不要求全部地址都提供完整 AMO 或 eventuality | T1-VERIFIED |
| 4. Software evidence | GCC 识别 A、Zaamo、Zalrsc；psABI 定义 C/C++ 原子映射 | 本机生成代码、Linux/库支持版本未经实测 | T1-VERIFIED / UNVERIFIED |
| 5. Competitive anchor | 可分别类比 Arm exclusive/LSE 与 x86 `LOCK` RMW/CAS | 不是 memory-model、性能或进展保证的等价声明 | T2-CROSS-CHECKED / UNVERIFIED |
| 6. Deployment intent | A 是多 hart 共享内存同步的基础；RVA23 将其纳入应用处理器基线 | 是否适合特定嵌入式/服务器/设备访问场景仍由 profile、PMA、OS 和产品需求决定 | T1-VERIFIED / UNVERIFIED |

## 12. 常见误解与边界检查

| 误解 | 正确表述 | 标签 |
| --- | --- | --- |
| “没写 `.aq/.rl` 的 AMO 不是原子。” | 它仍是原子 RMW；缺少的是额外 acquire/release 排序。 | T1-VERIFIED |
| “`aqrl` 就是全系统 fence。” | 它只约束该原子访问所在的 memory 或 I/O 地址域；跨域需 `FENCE`。 | T1-VERIFIED |
| “有 A 就一定有单条 CAS。” | A 2.1 没有 `AMOCAS`；CAS 可通过 LR/SC 构造，单条 CAS 由独立 `Zacas` 提供。 | T1-VERIFIED |
| “LR/SC 的 `SC` 最终都会成功。” | 只有满足约束并在 `RsrvEventual` 区域时才有条件化 livelock-freedom；无冲突时也仅保证至少一个参与 hart 退出。 | T1-VERIFIED |
| “A 表示任意物理地址都有完整原子支持。” | 区域 AMO 与 reservability 由 PMA/执行环境决定。 | T1-VERIFIED |
| “`Za64rs` 表示 reservation 是一个 64-byte cache line。” | 它只限制 reservation set 连续、自然对齐且最大 64 B，不规定缓存行或固定粒度。 | T1-VERIFIED |
| “MAG 让所有未对齐访问原子。” | MAG 有区域和访问范围限制；LR/SC 与 vector access 不因此获得未对齐原子性。 | T1-VERIFIED |
| “某编译器接受 `-march` 就能证明硬件和 OS 支持。” | 编译器、ISA 解码、PMA、profile 和 OS 交付是不同证据层。 | T1-VERIFIED / UNVERIFIED |

## 13. 验证清单

在把本文结论用于新核、新板卡、固件或课程实验前，逐项确认：

- [ ] 重新打开 [用户指定的 v20260120 Unprivileged index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) 与 [A 2.1 正文](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html)，确认版本、章节和 `norm:` 规则没有新的替代版本。
- [ ] 检查 [RVI ratified library](https://docs.riscv.org/reference/home/index.html) 与 [UDB deployment](https://riscv.github.io/riscv-unified-db/)，若手册或扩展版本变化，更新本文的 **SPEC-UPDATE-ALERT** 和版本表。
- [ ] 对目标 ISA 字符串、`misa`/ELF 属性与实际反汇编交叉检查，确认所用对象确实要求 `A` 或明确的 `Zaamo`/`Zalrsc`。
- [ ] 对每个使用 AMO/LR/SC 的物理地址范围，读取平台 PMA/内存映射文档：AMO 等级、`RsrvEventual`、MAG、memory/I/O 分类和 MMIO 副作用。
- [ ] 若声称 RVA23 兼容，重新核验 [RVA23 profile](https://docs.riscv.org/reference/rva23/rva23-profiles.html) 的 `A`、`Ziccrse`、`Ziccamoa` 与 `Za64rs` 要求，而非只检查 `misa.A`。
- [ ] 对 LR/SC 算法审计是否为 constrained loop：至多 16 条顺序指令、允许的动态路径、同址同宽、失败重试、以及 unconstrained fallback。
- [ ] 对 I/O 原子访问添加目标设备/互连协议证据；没有该证据时，保留 **UNVERIFIED**，不要由普通内存 AMO 推论 MMIO 可用。
- [ ] 用目标 GCC/LLVM、链接器、C 库和优化级别编译最小 `stdatomic`/`__atomic` 样例，并保存 `objdump -dr` 结果；重点核对 memory order、atomic ABI 属性与 LR/SC 重试代码。
- [ ] 在至少一个含并发竞争的压力测试中验证功能；该实测只能佐证该平台，不能替代 ISA 的普适语义或进展证明。

### 尚未解决的问题

以下问题超出用户提供的规范链接，本文刻意不猜测：具体 XiangShan/其他核的 reservation 粒度与失效策略、某块板卡的 PMA 表和 MMIO AMO 支持、Linux/固件向软件披露 `RsrvEventual` 的接口、以及本地 GCC/LLVM 的实际代码生成。这些事项均为 **UNVERIFIED**，需要以 RTL、平台文档、内核接口或可复现反汇编/测试补证。

## 14. 参考资料

1. [RISC-V Unprivileged ISA v20260120 index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) - 用户指定的版本入口与章节目录。
2. ["A" Extension for Atomic Instructions, Version 2.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/a-st-ext.html) - `A`、排序、`Zalrsc`、eventuality 和 `Zaamo` 的主规范。
3. [RISC-V Privileged ISA: Machine PMA](https://docs.riscv.org/reference/isa/v20260120/priv/machine.html) - AMO PMA、reservability PMA、MAG 与地址域。
4. [RVA23 Profiles](https://docs.riscv.org/reference/rva23/rva23-profiles.html) - RVA23 的 A、`Ziccrse`、`Ziccamoa`、`Za64rs` 要求。
5. [RISC-V Normative Rules Guidelines](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) - 规范性锚点解释。
6. [RISC-V Atomics ABI Specification](https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-atomic.adoc) - C/C++ 原子到 RISC-V 原语的 ABI 映射。
7. [GCC RISC-V Options](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Options.html) - `a`、`zaamo` 和 `zalrsc` 选项支持。
