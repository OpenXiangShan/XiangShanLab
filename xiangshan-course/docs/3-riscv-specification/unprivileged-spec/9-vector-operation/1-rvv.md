# RISC-V V 向量扩展（RVV 1.0）解析

> 规范基线：RISC-V International 的 [RISC-V Unprivileged ISA `v20260120`, Chapter 30, "V" Standard Extension for Vector Operations, Version 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)。本文聚焦单字母 `V` 应用处理器向量扩展；`Zve*`、`Zvl*` 只用于说明依赖关系，Vector Crypto、`Zvfh*`、`Zvfbf*` 等后续扩展不被误写成基础 `V` 的固有能力。

## 1. 定位与核心结论

RISC-V `V` 是面向数据并行计算的可伸缩向量 ISA。它增加 32 个向量寄存器 `v0`--`v31` 和 7 个非特权 CSR，通过运行时配置 `SEW`、`LMUL` 与 `vl`，让同一份向量长度无关代码在不同 `VLEN` 的实现上执行。单条指令实际处理多少元素由当前配置和实现共同决定，而不是固定写死为 128、256 或 512 bit。T1-VERIFIED: [V 1.0, Introduction, Parameters and Programmer's Model](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

`V` 的关键抽象不是“一个更宽的 SIMD 寄存器”，而是 **应用向量长度 `AVL` -> 硬件本轮长度 `vl` -> 循环 strip mining**。软件告诉硬件还有多少元素，硬件依据 `VLMAX = LMUL * VLEN / SEW` 选择本轮 `vl`；循环再推进指针和剩余计数。这样，代码通常无需知道具体 `VLEN`。T1-VERIFIED: [V 1.0, Configuration-Setting Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

| 常见问题 | 准确结论 | 不应由此推出 | 标签 |
| --- | --- | --- | --- |
| `VLEN` 是多少 | 每个 hart 的实现常量，满足 `VLEN >= ELEN`、是 2 的幂且不大于 `2^16` bit；基础 `V` 又要求至少 128 bit | 所有 RVV CPU 都是 128-bit datapath，或一条指令固定完成 128 bit 工作 | T1-VERIFIED |
| `vl` 是什么 | 当前指令要处理的元素个数，是运行时状态 | `vl` 是 bit 数，或恒等于 `VLEN/SEW` | T1-VERIFIED |
| `LMUL` 做什么 | 把一个或多个向量寄存器组织成寄存器组；也支持分数值以提高混合宽度代码的寄存器利用率 | `LMUL` 是硬件 lane 数或性能倍数 | T1-VERIFIED |
| `v0` 是否永远是 mask | 掩码指令编码使用 `v0` 的 mask bits；在不需要掩码时，`v0` 仍可作为普通向量寄存器 | `v0` 永远不能存普通数据 | T1-VERIFIED |
| `ta`/`ma` 是否把元素清零 | agnostic 元素可保持旧值或被写成全 1，选择甚至可不确定 | agnostic 等价于 zeroing | T1-VERIFIED |
| 支持 `V` 是否等于支持所有向量扩展 | 基础 `V` 覆盖 8/16/32/64-bit 的整数、定点及 FP32/FP64 等规定能力；半精度、BF16、密码等另有扩展 | `V` 自动包含 `Zvfh`、BF16、Vector Crypto 或未来矩阵扩展 | T1-VERIFIED |

## 2. 证据、版本与范围边界

### 2.1 证据优先级

| 层级 | 本次来源 | 用途与边界 | 标签 |
| --- | --- | --- | --- |
| Layer 1: UDB | [UnifiedDB continuous deployment](https://riscv.github.io/riscv-unified-db/) | 用机器可读扩展、指令、CSR 与 profile 数据交叉检查；连续部署产物不是固定 ratified 快照 | T1-VERIFIED |
| Layer 2: Normative | [RISC-V Normative Rules](https://github.com/riscv/docs-resources/blob/main/normative-rules.md) | 解释规范中 MUST/SHALL/SHOULD 等约束；具体 RVV 语义仍锚定 ratified 正文 | T1-VERIFIED |
| Layer 3: Ratified ISA | [V 1.0 in Unprivileged ISA `v20260120`](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html) | 本文的主依据：程序员模型、配置、访存、运算、异常与标准子集 | T1-VERIFIED |
| Layer 3: Profile | [RVA23 Profile v1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html) | 区分“扩展本身”与“某一应用处理器 profile 强制要求” | T1-VERIFIED |
| Tier-1 software | [GCC RISC-V Vector Intrinsics](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Vector-Intrinsics.html)、[LLVM RVV backend](https://llvm.org/docs/RISCV/RISCVVectorExtension.html) | 只用于说明当前编译器接口和后端建模，不替代 ISA 语义 | T1-VERIFIED |
| Tier-1 comparator | [Arm SVE Programmer's Guide](https://developer.arm.com/documentation/102476/latest/)、[Intel AVX-512 overview](https://www.intel.com/content/www/us/en/developer/articles/technical/intel-avx-512-instructions.html) | 仅作编程模型对照，不用类比推导 RVV 保证 | T1-VERIFIED |

UnifiedDB 的某些历史 ISA 页面仍带有旧 draft 叙述，因此遇到冲突时，本文以用户指定的 `v20260120` ratified library 为准。2026-08-19 检查时，官方 ratified library 展示的 Unprivileged ISA 最新固定版本仍为 `v20260120`；UDB `main` 的更晚生成日期只表示数据库连续构建更新。T1-VERIFIED: [Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)；[UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)

### 2.2 标签约定

- **T1-VERIFIED**：能在 RISC-V ratified 规范、profile、官方工具链文档或一级厂商架构资料中直接定位。
- **T2-CROSS-CHECKED**：可靠二级资料仅作补充，不覆盖 T1 语义。
- **INTERPRETIVE**：由规范事实推导的教学归纳或算例，不增加 ISA 保证。
- **UNVERIFIED**：具体处理器、OS、固件、工具链版本或性能尚未在本地实测。

### 2.3 状态时间线

| 状态 | 时间 | 事实 | 标签 |
| --- | --- | --- | --- |
| RATIFIED | 2021-11 | UDB profile 数据记录 `V 1.0.0` 为 ratified；RISC-V International 于 2021-12-02 公布 Vector 等规范已获批准 | T1-VERIFIED: [UDB RVA22 profile release](https://riscv-software-src.github.io/riscv-unified-db/pdfs/RVA22ProfileRelease.pdf)、[RVI announcement](https://riscv.org/blog/riscv-ratifies-15-new-specifications/) |
| RATIFIED PROFILE | 2024-10 | RVA23U64 把 `V` 从 RVA22 的 optional 提升为 mandatory | T1-VERIFIED: [RVA23U64 Mandatory Extensions](https://docs.riscv.org/reference/rva23/rva23-profiles.html) |
| RATIFIED LIBRARY SNAPSHOT | 2026-01 | `v20260120` 收录 `V` Version 1.0，作为本文可复现文本 | T1-VERIFIED: [Unprivileged ISA index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html) |

**SPEC-UPDATE CHECK：** 未发现比 `v20260120` 更晚的 ratified Unprivileged ISA 固定快照。以后复审时仍须重新检查 [ratified library](https://docs.riscv.org/reference/home/index.html)，不能把 UDB `main` 的构建日期直接当作 `V` 的新 ratified 版本。T1-VERIFIED

### 2.4 本文边界

本文讲的是 `V 1.0` 的架构可见语义，不承诺某个实现的 lane 数、datapath 宽度、每周期吞吐、乱序执行方式、cache 带宽或功耗。`VLEN` 是架构寄存器长度，微架构可以用更窄或更宽的执行资源分拍完成一条指令。后一句属于允许多种实现的 INTERPRETIVE 说明，不能用 `VLEN` 反推性能。

## 3. 先建立参数模型

### 3.1 九个容易混淆的量

| 名称 | 类型 | 含义 | 关键关系 | 标签 |
| --- | --- | --- | --- | --- |
| `ELEN` | hart 实现常量 | 任一操作可产生或消费的最大元素位宽 | `ELEN >= 8`，2 的幂 | T1-VERIFIED |
| `VLEN` | hart 实现常量 | 单个架构向量寄存器的 bit 数 | `ELEN <= VLEN <= 2^16`，2 的幂 | T1-VERIFIED |
| `SEW` | `vtype` 动态配置 | 当前所选元素宽度 | 基础编码给出 8/16/32/64；是否支持还受扩展与配置约束 | T1-VERIFIED |
| `LMUL` | `vtype` 动态配置 | 默认向量寄存器组倍率 | `1/8`、`1/4`、`1/2`、`1`、`2`、`4`、`8`，具体分数配置受 `ELEN` 约束 | T1-VERIFIED |
| `VLMAX` | 派生值 | 当前 `SEW/LMUL` 下单条指令最多容纳的元素数 | `VLMAX = LMUL * VLEN / SEW` | T1-VERIFIED |
| `AVL` | 软件输入 | 尚希望本轮处理的应用元素数 | 由 `vset{i}vl{i}` 的寄存器或立即数提供 | T1-VERIFIED |
| `vl` | 动态 CSR | 本条普通向量指令实际覆盖的元素计数上界 | `0 <= vl <= min(AVL, VLMAX)`，并服从更严格选择规则 | T1-VERIFIED |
| `EEW` | 每操作数有效属性 | 某个操作数的有效元素宽度 | 多数普通操作数 `EEW=SEW`；widen/narrow 和访存可不同 | T1-VERIFIED |
| `EMUL` | 每操作数派生属性 | 某个操作数实际占用的有效寄存器组倍率 | 通常 `EMUL=(EEW/SEW)*LMUL`，且最大为 8 | T1-VERIFIED |

`SEW/LMUL` 决定每个元素对应多少寄存器容量，因此保持 `SEW/LMUL` 比值不变就保持 `VLMAX` 不变。这是混合宽度循环常从 `e8,mf2` 切换到 `e16,m1`、`e32,m2`、`e64,m4` 的原因。T1-VERIFIED: [V 1.0, Mapping across Mixed-Width Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 3.2 算例：`VLEN=256`

以下只代入规范公式，不代表某个真实处理器。INTERPRETIVE

| `SEW` | `LMUL` | 寄存器组容量 | `VLMAX` | 可用对齐组数 |
| ---: | ---: | ---: | ---: | ---: |
| 8 | 1 | 256 bit | 32 | 32 |
| 32 | 1 | 256 bit | 8 | 32 |
| 32 | 4 | 1024 bit | 32 | 8 |
| 64 | 8 | 2048 bit | 32 | 4 |
| 16 | 1/2 | 使用一个寄存器低半部 | 8 | 32 个寄存器名可分别承载短向量 |

`LMUL>1` 时，寄存器号必须按组大小对齐：`LMUL=2` 使用偶数起始寄存器，`LMUL=4` 使用 4 的倍数，`LMUL=8` 使用 8 的倍数；不合法的组起点是 reserved encoding。`LMUL<1` 仍只指定一个物理向量寄存器名，未使用的高部属于 tail。T1-VERIFIED: [V 1.0, Vector Register Grouping and Element Mapping](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

## 4. 程序员模型

### 4.1 向量寄存器与上下文状态

`V` 增加 32 个固定为 `VLEN` bit 的架构寄存器 `v0`--`v31`。元素从最低有效 bit 开始按编号递增地打包；当 `LMUL>1` 时，元素先填满最低编号寄存器，再继续到组内下一个寄存器。架构规定软件所见布局，但允许微架构在内部重排数据，只要外部行为一致。T1-VERIFIED: [V 1.0, Vector Registers and Mapping](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

特权状态中的 `mstatus.VS` 跟踪向量上下文：`Off` 时执行向量指令或访问向量 CSR 会产生 illegal-instruction exception；改变向量状态会使 `VS` 变为 `Dirty`。有 H 扩展且运行在 guest (`V=1`) 时，`mstatus.VS` 和 `vsstatus.VS` 同时生效，任一为 `Off` 都禁止访问。T1-VERIFIED: [V 1.0, Vector Context Status](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 4.2 七个非特权 CSR

| 地址 | CSR | 访问 | 用途 | 关键点 | 标签 |
| --- | --- | --- | --- | --- | --- |
| `0x008` | `vstart` | URW | 下一次从哪个元素开始/恢复 | 正常完成向量指令后清零；主要服务 resumable trap | T1-VERIFIED |
| `0x009` | `vxsat` | URW | 定点累计饱和标志 | 任一相关结果饱和即可置 1；也映射到 `vcsr[0]` | T1-VERIFIED |
| `0x00A` | `vxrm` | URW | 2-bit 定点舍入模式 | 也映射到 `vcsr[2:1]`；独立于 FP `frm` | T1-VERIFIED |
| `0x00F` | `vcsr` | URW | 合并的向量控制/状态 | `vxrm` + `vxsat` | T1-VERIFIED |
| `0xC20` | `vl` | URO | 当前向量长度，单位是元素 | 由配置指令或 fault-only-first load 改写 | T1-VERIFIED |
| `0xC21` | `vtype` | URO | `SEW`、`LMUL`、tail/mask policy、`vill` | 只能由 `vset*` 指令更新 | T1-VERIFIED |
| `0xC22` | `vlenb` | URO | 单个向量寄存器的字节数 | 恒为 `VLEN/8` 的设计时常量 | T1-VERIFIED |

复位后，规范只要求向量扩展处于一致、可保存再恢复的状态；推荐 `vtype.vill=1` 且 `vl=0`。`vstart`、`vxrm`、`vxsat` 和向量寄存器本身可为任意值，所以软件在首次使用前必须配置所需状态，不能依赖清零。T1-VERIFIED: [V 1.0, State at Reset](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 4.3 `vtype`

| 位 | 字段 | 含义 | 标签 |
| --- | --- | --- | --- |
| `XLEN-1` | `vill` | 上次配置不受支持；依赖 `vtype` 的向量指令将触发 illegal instruction | T1-VERIFIED |
| `XLEN-2:8` | reserved | 写入非零属于不支持配置 | T1-VERIFIED |
| 7 | `vma` | 1: mask agnostic；0: mask undisturbed | T1-VERIFIED |
| 6 | `vta` | 1: tail agnostic；0: tail undisturbed | T1-VERIFIED |
| `5:3` | `vsew` | 选择 `SEW` | T1-VERIFIED |
| `2:0` | `vlmul` | 以有符号编码选择 `LMUL=2^vlmul` | T1-VERIFIED |

所有实现必须支持 tail/mask 的四种 policy 组合。`undisturbed` 表示目标对应元素保持旧值；`agnostic` 表示每个对应元素可保持旧值或写为全 1，且结果组合不要求确定。mask 结果的 tail 始终按 tail-agnostic 处理；汇编中的 `ta/tu` 与 `ma/mu` 标志必须显式写出。T1-VERIFIED: [V 1.0, Tail and Mask Policies](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

若请求的 `vtype` 不受实现支持，`vset*` 设置 `vill=1`，把 `vtype` 其余位和 `vl` 清零。软件可以通过检查 `vill` 轻量探测配置，而不应假定每个理论组合都可用。依赖 `vtype` 的后续向量指令会产生 illegal instruction；`vset*` 以及 whole-register loads/stores 不依赖 `vtype`。T1-VERIFIED: [V 1.0, Unsupported vtype and vill](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

## 5. `vset*` 与 strip mining

### 5.1 三条配置指令

```asm
vsetvli  rd, rs1, e32, m2, ta, ma  # AVL 来自 x[rs1]，vtype 由立即数给出
vsetivli rd, 16,  e16, m1, ta, ma  # AVL 是 0..31 的 5-bit 零扩展立即数
vsetvl   rd, rs1, rs2              # AVL 来自 x[rs1]，完整 vtype 来自 x[rs2]
```

三者都会依据 AVL 和新 `vtype` 更新 `vl`，并把新 `vl` 写入 `rd`（若 `rd=x0` 则丢弃）。`vsetvl` 读取完整 XLEN-wide `vtype` 值，适合上下文恢复；实现必须检查其中每一位，不得忽略未知高位。T1-VERIFIED: [V 1.0, Configuration-Setting Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 5.2 AVL 编码和 `vl` 选择

| `rd` | `rs1` | AVL | 典型用途 | 标签 |
| --- | --- | --- | --- | --- |
| 任意 | 非 `x0` | `x[rs1]` | 正常 strip mining | T1-VERIFIED |
| 非 `x0` | `x0` | `~0` | 请求 `VLMAX`，结果写入 `rd` | T1-VERIFIED |
| `x0` | `x0` | 当前 `vl` | 在不改变 `VLMAX` 的前提下改 `vtype` 并保持 `vl` | T1-VERIFIED |

合法配置下，规范约束如下：

1. `AVL <= VLMAX` 时，`vl = AVL`。
2. `VLMAX < AVL < 2*VLMAX` 时，`ceil(AVL/2) <= vl <= VLMAX`。
3. `AVL >= 2*VLMAX` 时，`vl = VLMAX`。
4. 同一实现对相同 AVL 和 VLMAX 的选择必须确定。
5. 因此 `AVL=0 -> vl=0`，`AVL>0 -> vl>0`，且 `vl<=AVL`、`vl<=VLMAX`。

T1-VERIFIED: [V 1.0, Constraints on Setting vl](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

第二条给实现保留了平衡最后两轮工作量的空间，所以可移植软件必须使用返回的 `vl` 推进循环，不能在 `AVL>VLMAX` 时自行假设本轮必为 `VLMAX`。INTERPRETIVE，依据上述 T1 规则。

### 5.3 一个长度无关的整数加法循环

下面是依据规范配置和 unit-stride 访存语义编写的教学示例。它没有在本地汇编、链接或仿真，运行结果标记为 UNVERIFIED。

```asm
# a0 = int32_t *dst, a1 = const int32_t *lhs
# a2 = const int32_t *rhs, a3 = 剩余元素数
loop:
    vsetvli t0, a3, e32, m1, ta, ma
    vle32.v  v1, (a1)
    vle32.v  v2, (a2)
    vadd.vv  v3, v1, v2
    vse32.v  v3, (a0)

    slli     t1, t0, 2
    add      a0, a0, t1
    add      a1, a1, t1
    add      a2, a2, t1
    sub      a3, a3, t0
    bnez     a3, loop
```

这里 `t0` 而不是 `VLEN/32` 决定指针增量；因此 `VLEN=128` 与 `VLEN=512` 的实现可执行同一控制流，只是迭代次数不同。该可移植性结论受前提限制：目标都必须支持代码要求的扩展、元素类型和配置。T1-VERIFIED: [V 1.0, Portability and Strip Mining](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

## 6. 一条向量指令究竟作用于哪些元素

### 6.1 操作数形式

常见后缀表达第二操作数来源：`.vv` 为 vector-vector，`.vx` 为 vector-integer-scalar，`.vi` 为 vector-immediate，`.vf` 为 vector-floating-scalar；reduction 常用 `.vs` 表示一个向量组加上保存在某个向量寄存器 element 0 的 scalar。标量也可能来自 `x`、`f` 或向量寄存器 element 0，具体由指令类别规定。T1-VERIFIED: [V 1.0, Scalar Operands and Arithmetic Encoding](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

许多指令有可选掩码：汇编末尾写 `v0.t` 表示启用 mask，编码字段却是 `vm=0`；不写 mask 表示 `vm=1`。这是一个反直觉的 active-low encoding，阅读 RTL 或反汇编时不能把 `vm=1` 误认成“mask enabled”。T1-VERIFIED: [V 1.0, Mask Encoding](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 6.2 元素集合

对元素索引 `i`，可以用下表理解执行边界：

| 集合 | 条件 | 是否执行/异常 | 目标行为 | 标签 |
| --- | --- | --- | --- | --- |
| prestart | `0 <= i < vstart` | 不执行，不产生异常 | 保持不变 | T1-VERIFIED |
| active | `vstart <= i < vl` 且 mask bit=1，或指令无掩码 | 执行，可产生该元素定义的异常 | 写入计算结果 | T1-VERIFIED |
| inactive | `vstart <= i < vl` 且 mask bit=0 | 不执行，不产生异常 | 由 `vma` 决定 undisturbed/agnostic | T1-VERIFIED |
| tail | `vl <= i <` 当前目标布局的 tail 上界 | 不执行，不产生异常 | 由 `vta` 决定；mask 目标有更宽松的 tail-agnostic 规则 | T1-VERIFIED |

若 `vstart >= vl`，没有 body element，任何向量目标元素都不更新，连 agnostic tail 也不更新；`vl=0` 是这个规则的特例。向 `x` 或 `f` 标量寄存器写结果的某些向量指令仍会在 `vl=0` 时执行其标量写回语义，必须按具体指令阅读。T1-VERIFIED: [V 1.0, Prestart, Active, Inactive, Body and Tail](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 6.3 EEW、EMUL 与重叠约束

普通同宽运算通常是 `EEW=SEW, EMUL=LMUL`。widening 指令常让目的为 `EEW=2*SEW, EMUL=2*LMUL`；narrowing 指令则让宽源为 `EEW=2*SEW, EMUL=2*LMUL`。若任一操作数需要 `EMUL>8`，编码 reserved，例如 `LMUL=8` 时再产生双宽目的组不可用。T1-VERIFIED: [V 1.0, Vector Operands and Widening/Narrowing](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

源和目的重叠只有规范列出的情形合法：同 EEW 可重叠；窄目的覆盖宽源时必须共用最低编号寄存器；宽目的覆盖窄源时，窄源 `EMUL>=1` 且二者最高编号寄存器相同。限制的目的之一是让无寄存器重命名实现也能从 `vstart` 恢复。T1-VERIFIED: [V 1.0, Vector Register Group Overlap](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

## 7. 向量访存

### 7.1 寻址模式与顺序

| 模式 | 地址 | 代表 mnemonic | 元素访问顺序保证 | 标签 |
| --- | --- | --- | --- | --- |
| unit-stride | `base + i*EEW/8` | `vle32.v` / `vse32.v` | 元素间不保证顺序 | T1-VERIFIED |
| constant-stride | `base + i*x[rs2]` | `vlse32.v` / `vsse32.v` | 元素间不保证顺序；支持负 stride 和零 stride | T1-VERIFIED |
| indexed-unordered | `base + zero_extend(index[i])` | `vluxei32.v` / `vsuxei32.v` | 不保证元素顺序 | T1-VERIFIED |
| indexed-ordered | `base + zero_extend(index[i])` | `vloxei32.v` / `vsoxei32.v` | 按元素顺序进行 memory access | T1-VERIFIED |

所有模式的 base 都来自 `x[rs1]`，stride 来自 GPR，indexed offset 是**字节偏移**而不是自动乘以数据 EEW 的数组下标。offset 比 XLEN 窄时零扩展，比 XLEN 宽时只取低 XLEN bit。T1-VERIFIED: [V 1.0, Vector Load/Store Addressing Modes](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

unit-stride、constant-stride 和 unordered indexed 即使访问 strongly ordered I/O region，也不能推导出元素按索引顺序发起；需要有序向量 I/O 时应使用 ordered indexed 形式，并仍结合平台 memory-ordering 规则分析。T1-VERIFIED: [V 1.0, Vector Memory Ordering](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 7.2 内存 EEW 与数据 EEW

unit/constant-stride 指令把传输元素的 EEW 编码在指令中；indexed 指令编码的是 index EEW，而数据保持当前 `SEW/LMUL`。相应 `EMUL=(EEW/SEW)*LMUL` 必须落在 `1/8..8`，寄存器组也必须合法。由此可见，`vle8.v` 中的 `8` 是内存和目的元素 EEW，而 `vluxei8.v` 中的 `8` 是 index 宽度，不是数据元素宽度。T1-VERIFIED: [V 1.0, Load/Store Width Encoding](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 7.3 掩码、fault-only-first、segment 与 whole-register

向量 load/store 只对 active 元素访问内存或产生异常；masked-off 元素不应触碰对应地址。普通 load 的 inactive 目标由 `vma` 决定。T1-VERIFIED: [V 1.0, Vector Loads and Stores](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

| 功能 | 代表指令 | 精确用途与限制 | 标签 |
| --- | --- | --- | --- |
| mask spill/fill | `vlm.v`, `vsm.v` | 传输 `ceil(vl/8)` bytes；这些指令的 `vstart` 单位是 byte | T1-VERIFIED |
| fault-only-first | `vle32ff.v` | element 0 同步异常正常 trap；后续元素异常不 trap 而把 `vl` 缩到该索引 | T1-VERIFIED |
| segment | `vlseg<nf>e<eew>.v` 等 | 在内存连续 field 与连续编号寄存器间搬运，适合 AoS/SoA 转换；`NFIELDS=1..8` 且 `EMUL*NFIELDS<=8` | T1-VERIFIED |
| whole-register | `vl1re8.v`, `vl2re8.v`, `vs1r.v`, `vs2r.v` 等 | 不依赖当前 `vl/vtype` 保存或恢复 1/2/4/8 个完整向量寄存器 | T1-VERIFIED |

fault-only-first 适合有数据相关退出条件的 while-loop，但它不是“忽略所有错误”：element 0 的同步异常仍 trap；后续异常可能缩短 `vl`，且实现即使没有异常也获准少处理一些元素（`vstart=0, vl>0` 时至少处理一个）。软件必须在指令后读取实际 `vl`。T1-VERIFIED: [V 1.0, Unit-Stride Fault-Only-First Loads](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

规范指出 fault-only-first 可探测相邻有效地址，存在安全考量，因此只定义 unit-stride 形式，没有 constant-stride 或 scatter/gather fault-only-first。该章节没有规定额外缓解机制。T1-VERIFIED: [V 1.0, Fault-Only-First Security Note](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

## 8. 运算指令族

### 8.1 整数与定点

除非具体指令另有说明，普通向量整数运算溢出时按位宽回绕。饱和、舍入或截窄行为只由对应定点/clip 指令提供，不能由 `vadd` 名称类推。T1-VERIFIED: [V 1.0, Vector Integer Arithmetic](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

| 类别 | 代表 mnemonic | 核心语义 | 标签 |
| --- | --- | --- | --- |
| 加减 | `vadd`, `vsub`, `vrsub` | 同宽逐元素加减，普通溢出回绕 | T1-VERIFIED |
| widening 加减 | `vwaddu`, `vwadd`, `vwsubu`, `vwsub` | 窄输入产生双宽结果；`.w*` 形式含一个已有宽操作数 | T1-VERIFIED |
| 扩展 | `vzext.vf2`, `vsext.vf4` | 把 `SEW/2`、`SEW/4` 或 `SEW/8` 源零/符号扩展到 `SEW` | T1-VERIFIED |
| carry/borrow | `vadc`, `vmadc`, `vsbc`, `vmsbc` | 支持多字整数；数据结果与 carry-mask 结果分为不同指令 | T1-VERIFIED |
| 逻辑与移位 | `vand`, `vor`, `vxor`, `vsll`, `vsrl`, `vsra` | 逐元素位运算或移位 | T1-VERIFIED |
| 比较 | `vmseq`, `vmsne`, `vmslt[u]`, `vmsle[u]`, `vmsgt[u]` | 生成单 bit/元素的 mask 结果 | T1-VERIFIED |
| min/max | `vmin[u]`, `vmax[u]` | signed/unsigned 由 mnemonic 区分 | T1-VERIFIED |
| 乘除 | `vmul`, `vmulh[u/su]`, `vdiv[u]`, `vrem[u]` | 同宽乘积低/高部、除法与余数 | T1-VERIFIED |
| widening multiply/MAC | `vwmul[u/su]`, `vwmacc[u/su]` | 产生或累加双宽结果 | T1-VERIFIED |
| merge/move | `vmerge`, `vmv.v.v`, `vmv.v.x`, `vmv.v.i` | mask 选择或把 vector/scalar 广播到 active elements | T1-VERIFIED |

定点扩展使用独立 `vxrm` 舍入模式和累计 `vxsat` 标志：`vsadd[u]`/`vssub[u]` 饱和加减，`vaadd[u]`/`vasub[u]` 舍入平均，`vssrl`/`vssra` 舍入缩放移位，`vnclip[u]` 舍入、缩放并饱和到窄结果，`vsmul` 做饱和分数乘。软件负责解释隐含分母和清理/读取 `vxsat`。T1-VERIFIED: [V 1.0, Vector Fixed-Point Arithmetic](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

`vxrm` 的四种模式为 round-to-nearest-up (`rnu`)、round-to-nearest-even (`rne`)、round-down/truncate (`rdn`) 和 round-to-odd (`rod`)。这套模式只服务向量定点指令，不等于浮点 `frm`。T1-VERIFIED: [V 1.0, vxrm](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 8.2 浮点

基础 `V` 依赖 `F`、`D`，为 EEW=32/64 实现向量浮点指令、FP32/FP64 转换和相应 reduction；基础 `V` 本身不让 EEW=16 自动成为 binary16 浮点，后者由 `Zvfhmin`/`Zvfh` 增补。T1-VERIFIED: [V 1.0, V for Application Processors and Zvfh*](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

向量浮点遵循 IEEE 754-2008 兼容值和标量浮点 NaN 规则，使用动态舍入模式 `frm`；任一 active FP 元素产生的异常会累计到标准 `fflags`，inactive 元素不设置 FP 异常标志。`mstatus.FS=Off` 时执行向量 FP 指令会产生 illegal instruction，改变 FP 状态还须把 `FS` 标 Dirty。T1-VERIFIED: [V 1.0, Vector Floating-Point Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

| 类别 | 代表 mnemonic | 标签 |
| --- | --- | --- |
| 加减、乘除 | `vfadd`, `vfsub`, `vfmul`, `vfdiv`, `vfrdiv` | T1-VERIFIED |
| widening | `vfwadd`, `vfwsub`, `vfwmul` | T1-VERIFIED |
| fused multiply-add | `vfmacc`, `vfnmacc`, `vfmsac`, `vfnmsac` 及重排变体 | T1-VERIFIED |
| sqrt/reciprocal estimate | `vfsqrt.v`, `vfrec7.v`, `vfrsqrt7.v` | T1-VERIFIED |
| min/max/sign inject | `vfmin`, `vfmax`, `vfsgnj[n/x]` | T1-VERIFIED |
| compare/classify | `vmfeq`, `vmfne`, `vmflt`, `vmfle`, `vmfgt`, `vmfge`, `vfclass.v` | T1-VERIFIED |
| convert | `vfcvt.*`, `vfwcvt.*`, `vfncvt.*` | T1-VERIFIED |
| merge/move | `vfmerge.vfm`, `vfmv.v.f` | T1-VERIFIED |

表中各族语义来自 [V 1.0, Vector Floating-Point Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)。

### 8.3 Reduction、mask 与 permutation

reduction 把一个向量组和保存在单个向量寄存器 element 0 的 scalar 组合，最终把 scalar 结果写到目标向量寄存器 element 0。整数包括 sum/and/or/xor/min/max 及 widening sum；浮点包括 ordered/unordered sum、min/max 与 widening sum。reduction 要求 `vstart=0`，非零 `vstart` 会触发 illegal instruction。T1-VERIFIED: [V 1.0, Vector Reduction Operations](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

| 类别 | 代表指令 | 用途 | 标签 |
| --- | --- | --- | --- |
| mask 逻辑 | `vmand`, `vmor`, `vmxor`, `vmnand`, `vmnor` 等 | 单 bit 元素的布尔组合，与 LMUL 无关 | T1-VERIFIED |
| mask 查询 | `vcpop.m`, `vfirst.m` | 统计 active set bits / 找第一个 set bit | T1-VERIFIED |
| prefix mask | `vmsbf.m`, `vmsif.m`, `vmsof.m` | first set bit 之前/含 first/仅 first 的 mask | T1-VERIFIED |
| index 生成 | `viota.m`, `vid.v` | 生成前缀计数或元素索引 | T1-VERIFIED |
| gather | `vrgather`, `vrgatherei16` | 用元素索引在向量寄存器组内重排 | T1-VERIFIED |
| slide | `vslideup/down`, `vslide1up/down`, FP slide1 | 上下滑动并可插入一个 scalar | T1-VERIFIED |
| compress | `vcompress.vm` | 按 mask 把选中元素紧密压到目的前部 | T1-VERIFIED |
| whole-register move | `vmv1r.v`, `vmv2r.v`, `vmv4r.v`, `vmv8r.v` | 不依赖当前 element layout 复制完整寄存器组 | T1-VERIFIED |

这些指令属于 [V 1.0, Mask and Permutation Instructions](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)。特别要区分 `vrgather` 的寄存器内索引与 indexed load/store 的内存地址索引，两者不属于同一语义层。

## 9. 异常、恢复与上下文切换

向量指令发生同步异常或异步中断时，`*epc` 指向这条向量指令，`vstart` 保存发生 trap 的元素索引。handler 返回后可从该元素重启；在它之前已提交的元素保持结果，正常完成后 `vstart` 归零。T1-VERIFIED: [V 1.0, Exception Handling](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

精确向量 trap 要求：旧指令已提交、新指令未改变架构状态、当前向量指令在 `vstart` 前的元素已提交，而 `vstart` 及之后不能留下会使重启得到错误终态的影响。对幂等内存，store 可能已更新故障元素之后的位置，只要重启仍正确；对非幂等内存，发生同步 store trap 时不得更新索引大于等于故障元素的位置。T1-VERIFIED: [V 1.0, Precise Vector Traps](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

`V` 标准扩展要求 precise traps。规范讨论 imprecise、selectable 和 swappable trap 模式，但当前标准扩展没有定义保存/恢复不透明微架构向量状态的 swappable 机制，不能把讨论性章节当成已实现 ISA。T1-VERIFIED: [V 1.0, Standard Vector Extensions and Trap Modes](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

上下文切换至少要考虑 32 个向量寄存器和全部向量 CSR；whole-register load/store 与 `vlenb` 让软件在不知道内容 `SEW/LMUL` 时保存/恢复完整状态。规范还警告，带 active vector state 的线程通常不能在 `VLEN` 或 `ELEN` 不同的 harts 间迁移。T1-VERIFIED: [V 1.0, Parameters and Whole-Register Loads/Stores](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

## 10. `V`、`Zve*`、`Zvl*` 与后续扩展

### 10.1 最小向量长度扩展

`Zvl32b`、`Zvl64b`、`Zvl128b`、`Zvl256b`、`Zvl512b`、`Zvl1024b` 分别声明最小 `VLEN`；更长的 `Zvl` 蕴含所有更短者。它们是能力下界，不表示实际 `VLEN` 必然恰好等于名字中的数字。T1-VERIFIED: [V 1.0, Zvl*](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

### 10.2 Embedded vector 子集

| 扩展 | 最小 VLEN | 支持 EEW | FP32 | FP64 | 标签 |
| --- | ---: | --- | --- | --- | --- |
| `Zve32x` | 32 | 8/16/32 | 否 | 否 | T1-VERIFIED |
| `Zve32f` | 32 | 8/16/32 | 是 | 否 | T1-VERIFIED |
| `Zve64x` | 64 | 8/16/32/64 | 否 | 否 | T1-VERIFIED |
| `Zve64f` | 64 | 8/16/32/64 | 是 | 否 | T1-VERIFIED |
| `Zve64d` | 64 | 8/16/32/64 | 是 | 是 | T1-VERIFIED |

表格来自 [V 1.0, Zve* Vector Extensions for Embedded Processors](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)。这些子集对少数 64-bit 高部乘法、定点乘法和浮点 permutation 有明确删减，不能仅凭表中的 EEW/FP 列推导它们拥有完整 `V` 指令集。

### 10.3 单字母 `V` 的准确依赖

`V` 面向 application processor profile，具有 precise traps，依赖 `Zvl128b` 和 `Zve64d`，因此最小 VLEN 为 128，支持 EEW 8/16/32/64，并依赖 `F`、`D` 提供 FP32/FP64 向量浮点。若实现提供 `misa`，支持 `V` 时设置 `misa.V`。T1-VERIFIED: [V 1.0, V for Application Processors](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)

| 能力 | 裸 `V 1.0` | 需要额外扩展 | 标签 |
| --- | --- | --- | --- |
| 整数、定点、mask、reduction、permutation、规定的向量访存 | 是 | 无 | T1-VERIFIED |
| FP32/FP64 向量浮点 | 是，且 `V` 本身依赖 `F/D` | `F`、`D` 已由 `V` 依赖包含 | T1-VERIFIED |
| binary16 仅与 FP32 互转 | 否 | `Zvfhmin` | T1-VERIFIED |
| 完整 binary16 向量浮点 | 否 | `Zvfh` | T1-VERIFIED |
| BF16 | 否 | `Zvfbfmin` / `Zvfbfwma` 等 | T1-VERIFIED: [RVA23 options](https://docs.riscv.org/reference/rva23/rva23-profiles.html) |
| Vector Crypto | 否 | `Zvbb`、`Zvkn*`、`Zvks*`、`Zvbc` 等对应扩展 | T1-VERIFIED: [Vector Crypto 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/vector-crypto.html) |

## 11. 六维架构映射

下表按 `spec-learning` 的六个维度收束范围。Arm/x86 只作对照，不宣称指令逐条等价。

| 维度/Feature | RISC-V `V`（规范与状态） | Arm/x86 对照 | Profile / 平台要求 | 软件证据 | 标签 |
| --- | --- | --- | --- | --- | --- |
| 成熟度 | `V 1.0`，2021-11 ratified；收录于 `v20260120` | Arm SVE 与 Intel AVX-512 均有公开一级架构/编程资料；不据此比较产品普及率 | RVA23U64 mandatory；RVA22U64 曾是 optional | GCC 与 LLVM 官方文档均声明支持 RVV 1.0 | T1-VERIFIED |
| ISA 语义 | 动态 `vl/vtype`、`VLEN` 实现相关、32 个 `v` 寄存器、逐元素 mask、strip mining | SVE 同属 vector-length-agnostic/predicated 模型；AVX-512 使用固定 512-bit ZMM 并有 opmask | `V` 自身要求 `Zvl128b`；profile 可规定更完整扩展集合 | LLVM 用 scalable vector type 建模未知 VLEN | T1-VERIFIED |
| 配置与可移植性 | `vset*` 协商 AVL 与本轮 `vl`，软件按返回值推进 | SVE 用 predicate 和长度查询支持跨实现；AVX-512 的 ISA 寄存器宽度固定 | RVA23 只保证其明确 mandatory 集合，不能推广到“所有 RISC-V” | 编译器可生成 `vsetvli` 并优化冗余配置 | T1-VERIFIED |
| 访存 | unit/strided/indexed、ordered/unordered、segment、fault-only-first | SVE 也提供 predicated gather/scatter；AVX-512 提供 gather/scatter 和 mask fault suppression 的具体指令 | 内存模型、PMA、OS 映射仍由其他 ISA/platform 层定义 | 是否成功自动向量化取决于别名、对齐、成本模型等，本文未本地测量 | T1-VERIFIED + UNVERIFIED performance |
| 数值能力 | 基础 `V` 含整数、定点、FP32/FP64；FP16/BF16/crypto 模块化扩展 | SVE/AVX-512 的具体数据类型受各自版本或 feature subset 约束 | RVA23 另把 `Zvfhmin`、`Zvbb`、`Zvkt` 列为 mandatory，说明它们不等于裸 `V` | GCC RVV intrinsics 1.0；LLVM backend 1.0 | T1-VERIFIED |
| 部署意图 | `V` 面向 application processors；`Zve*` 面向 embedded；后续扩展可服务 crypto/ML | SVE 官方资料定位 HPC/ML；AVX-512 官方资料定位高吞吐 SIMD | 本文只断言 RVA23U64，不代表每个 server/client/embedded profile | 实际 SoC 的 VLEN、吞吐和 OS enablement 必须逐平台验证 | T1-VERIFIED + UNVERIFIED implementation |

RISC-V 与 Arm 的相似点是都允许编写不固化物理向量长度的循环；关键差异是 RVV 把 `SEW/LMUL`、AVL 和 `vl` 协商显式放进 `vset*` 状态，而 SVE 以 predicate 和专用长度相关指令组织循环。AVX-512 则以固定宽度寄存器和 opmask 为主。以上是对各自一级资料的结构化 INTERPRETIVE 对照，不表示异常、尾部、访存顺序或 ABI 完全等价。T1 sources: [RVV 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)、[Arm SVE guide](https://developer.arm.com/documentation/102476/latest/)、[Intel AVX-512](https://www.intel.com/content/www/us/en/developer/articles/technical/intel-avx-512-instructions.html)

## 12. 软件接口与 ABI 边界

GCC 官方手册说明 `riscv_vector.h` 提供 ratified RVV intrinsic specification 1.0 的接口；LLVM 官方 RVV 文档说明后端支持 RVV 1.0，并用 scalable vector type 表达编译时未知但运行期间恒定的 VLEN。它们证明工具链存在规范化编程接口，不证明当前机器安装的 GCC/Clang 版本、默认 flags、libc 或 OS 已启用向量状态。T1-VERIFIED: [GCC RVV Intrinsics](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Vector-Intrinsics.html)；[LLVM RVV Extension](https://llvm.org/docs/RISCV/RISCVVectorExtension.html)

Unprivileged ISA 附录中的 “Calling Convention for Vector State” 明确标为 **Not authoritative - Placeholder Only**，不能拿它作为稳定 ABI 规范。函数调用时哪些向量寄存器 caller/callee-saved、是否使用 vector calling convention，应转查目标平台当前 ratified ABI 和工具链文档。T1-VERIFIED: [Unprivileged ISA index](https://docs.riscv.org/reference/isa/v20260120/unpriv/unpriv-index.html)

本文没有在本地执行 `gcc -march=rv64gcv`、Clang、assembler、Spike、QEMU 或真实硬件测试，也没有验证目标操作系统的 context-switch/`mstatus.VS` 管理，因此具体工具版本与运行行为为 **UNVERIFIED**。

## 13. 高频误区检查表

| 误区 | 正确检查方式 | 标签 |
| --- | --- | --- |
| 把 `VLEN` 当执行单元宽度 | `VLEN` 只定义架构寄存器状态；查具体实现文档才能知道 datapath/lane/吞吐 | T1-VERIFIED + UNVERIFIED implementation |
| 用 `VLEN/SEW` 代替 `vl` | 先算 `VLMAX=LMUL*VLEN/SEW`，再使用 `vset*` 返回的 `vl` | T1-VERIFIED |
| 忽略分数 LMUL | 混合宽度代码用分数 LMUL 保持 `SEW/LMUL`，并检查实现支持与 `vill` | T1-VERIFIED |
| 把 `vm=1` 当 mask enabled | 编码 `vm=0` 才使用 `v0` mask；汇编通常以 `v0.t` 明示 | T1-VERIFIED |
| 认为 agnostic 是 0 | 只能依赖“旧值或全 1，且组合可不确定”，不可读取后当稳定数据 | T1-VERIFIED |
| masked-off load 仍会 fault | inactive 元素不访问内存也不产生异常；先确认该元素确实 inactive | T1-VERIFIED |
| 所有向量访存按元素顺序 | 只有 ordered indexed 形式给出元素顺序保证；其他形式不保证 | T1-VERIFIED |
| `*ff` 忽略所有异常 | element 0 同步异常仍 trap；后续异常通过缩短 `vl` 报告进度 | T1-VERIFIED |
| `V` 自动有 FP16/BF16/crypto | 检查完整 ISA string/profile 中对应 `Zv*` 扩展 | T1-VERIFIED |
| trap 后总从元素 0 重跑 | 普通可恢复向量 trap 用 `vstart`；reduction 等少数指令要求 `vstart=0` | T1-VERIFIED |
| `v0` 不能存数据 | 只有执行 masked instruction 时编码固定读取 `v0` mask；其他时候可作为普通寄存器 | T1-VERIFIED |
| 宽化结果仍占原寄存器组 | 对每个 operand 计算 EEW/EMUL，确认不超过 8 且组对齐、重叠合法 | T1-VERIFIED |

## 14. 阅读与验证清单

### 14.1 读一段 RVV 汇编时

- [ ] 找到最近一次支配该指令的 `vsetvli`、`vsetivli` 或 `vsetvl`，记录 `SEW`、`LMUL`、`vta`、`vma` 和 AVL 来源。
- [ ] 计算 `VLMAX`，但用实际 `vl` 判断 active body，而不是用计算上限代替运行时值。
- [ ] 按每个 operand 分别确定 `EEW/EMUL`，尤其检查 widening、narrowing、indexed load/store。
- [ ] 检查寄存器组起点对齐、`EMUL<=8`、源/目的重叠规则和 `v0` mask 冲突。
- [ ] 区分 prestart、active、inactive、tail，确认代码是否错误依赖 agnostic 值。
- [ ] 对 load/store 标注 unit/stride/indexed、ordered/unordered、mask、segment、fault-only-first。
- [ ] 对可能 trap 的指令检查 `vstart` 恢复规则；对 reduction/compress 等再查其特例。

以上清单由 [V 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html) 的配置、operand、mask、memory 与 exception 章节归纳，标记为 INTERPRETIVE。

### 14.2 下次里程碑复审时

- [ ] 重查 [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)，确认 Unprivileged ISA 固定版本和 `V` 版本。
- [ ] 重查 [UnifiedDB](https://riscv.github.io/riscv-unified-db/) 的 `V`、指令、CSR 和目标 profile 数据；若与 ratified 文本冲突，以 ratified 文本为准并记录差异。
- [ ] 重查 [RVA23](https://docs.riscv.org/reference/rva23/rva23-profiles.html) 或项目真正采用的 profile，避免把 profile 要求泛化为所有 RISC-V。
- [ ] 重查 [GCC RVV intrinsics](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Vector-Intrinsics.html) 与 [LLVM RVV backend](https://llvm.org/docs/RISCV/RISCVVectorExtension.html)，记录项目锁定版本和编译 flags。
- [ ] 在目标编译器、模拟器和硬件上分别验证 assembly acceptance、illegal configuration、tail/mask policy、fault-only-first 和 context switching。
- [ ] 对真实 SoC 单独记录 `VLEN/ELEN`、支持的 `Zvl/Zv*`、微架构吞吐和 OS enablement；这些都不能从裸 `V 1.0` 推导。

### 14.3 仍需项目侧回答的问题

1. 目标 ISA string 和 profile 是什么，是否只有 `V`，还是还包含 `Zvfh*`、BF16、Vector Crypto？UNVERIFIED
2. 各 hart 的 `VLEN/ELEN` 是否一致，调度器是否允许跨异构 hart 迁移 active vector context？UNVERIFIED
3. OS 是否启用并正确保存 `VS`、全部向量 CSR 和 32 个向量寄存器？UNVERIFIED
4. 工具链按 intrinsic、自动向量化还是手写汇编使用 RVV，锁定了哪个 ABI 与 intrinsic 版本？UNVERIFIED
5. 目标 workload 的瓶颈是 ISA 能力、向量执行吞吐、cache/memory bandwidth，还是软件向量化质量？UNVERIFIED

## 参考资料

1. [RISC-V Unprivileged ISA `v20260120`: V Standard Extension for Vector Operations, Version 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html)
2. [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)
3. [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
4. [RISC-V Normative Rules Guidelines](https://github.com/riscv/docs-resources/blob/main/normative-rules.md)
5. [RVA23 Profiles v1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html)
6. [GCC RISC-V Vector Intrinsics](https://gcc.gnu.org/onlinedocs/gcc/RISC-V-Vector-Intrinsics.html)
7. [LLVM RISC-V Vector Extension](https://llvm.org/docs/RISCV/RISCVVectorExtension.html)
8. [Arm Introduction to SVE](https://developer.arm.com/documentation/102476/latest/)
9. [Intel AVX-512 Instructions](https://www.intel.com/content/www/us/en/developer/articles/technical/intel-avx-512-instructions.html)
