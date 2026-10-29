# 向量浮点加法中舍入模式对结果的影响

本文以当前 `source-code/rounding-mode.c` 中的 FP32 向量浮点加法为例，结合测试指令、反汇编与香山 `VectorFloatAdder` 源码，分析五种舍入模式的选择规则、配置方式，以及它们对不同输入结果和异常标志的影响。

## 1. 指令选取

### 1.1 当前测试源码

本次分析选择 `vfadd.vv`。它对向量寄存器中的 FP32 元素逐 lane 相加，使用浮点 CSR `frm` 指定的动态舍入模式。测试对六类输入分别执行 RNE、RTZ、RDN、RUP、RMM，并用相同的指令序列观察结果变化。

当前测试的运算核心如下，摘自 `source-code/rounding-mode.c`。`lhs`、`rhs`、`dst` 使用 `uint32_t` 保存 FP32 原始位型；每个基础输入复制到四个 lane，目的数组预置为 `0x4b000000`。

```c
#define LANES 4
#define RM_RNE 0u
#define RM_RTZ 1u
#define RM_RDN 2u
#define RM_RUP 3u
#define RM_RMM 4u
#define SENTINEL 0x4b000000u

static void init_state(void) {
  asm volatile("li t0, 0x6600\n"
               "csrs mstatus, t0\n"
               "csrwi fcsr, 0\n"
               "csrwi vcsr, 0\n"
               "csrwi vstart, 0"
               :
               :
               : "t0", "memory");
}

__attribute__((noinline))
static unsigned run_unmasked(unsigned rm, unsigned vl, const uint32_t *lhs,
                             const uint32_t *rhs, uint32_t *dst, int clear_flags) {
  unsigned flags;
  if (clear_flags)
    asm volatile("csrw frm, %4\n"
                 "csrwi fflags, 0\n"
                 "vsetvli zero, %5, e32, m1, tu, mu\n"
                 "vle32.v v1, (%1)\n"
                 "vle32.v v2, (%2)\n"
                 "vle32.v v3, (%3)\n"
                 "vfadd.vv v3, v1, v2\n"
                 "vse32.v v3, (%3)\n"
                 "csrr %0, fflags"
                 : "=r"(flags)
                 : "r"(lhs), "r"(rhs), "r"(dst), "r"(rm), "r"(vl)
                 : "v1", "v2", "v3", "memory");
  else
    asm volatile("csrw frm, %4\n"
                 "vsetvli zero, %5, e32, m1, tu, mu\n"
                 "vle32.v v1, (%1)\n"
                 "vle32.v v2, (%2)\n"
                 "vle32.v v3, (%3)\n"
                 "vfadd.vv v3, v1, v2\n"
                 "vse32.v v3, (%3)\n"
                 "csrr %0, fflags"
                 : "=r"(flags)
                 : "r"(lhs), "r"(rhs), "r"(dst), "r"(rm), "r"(vl)
                 : "v1", "v2", "v3", "memory");
  return flags;
}
```

`clear_flags=1` 用于独立比较每次运算；`clear_flags=0` 保留已有异常标志，用于分析多条指令的累积效果。每次调用都先写 `frm`，因此运算模式由参数 `rm` 决定。

带 mask 的测试使用另一个入口：

```c
__attribute__((noinline))
static unsigned run_masked(unsigned rm, unsigned vl, uint32_t mask_bits,
                           const uint32_t *lhs, const uint32_t *rhs, uint32_t *dst) {
  unsigned flags;
  asm volatile("csrw frm, %4\n"
               "csrwi fflags, 0\n"
               "vsetvli zero, %5, e32, m1, tu, mu\n"
               "vle32.v v1, (%1)\n"
               "vle32.v v2, (%2)\n"
               "vle32.v v3, (%3)\n"
               "vmv.v.x v0, %6\n"
               "vfadd.vv v3, v1, v2, v0.t\n"
               "vse32.v v3, (%3)\n"
               "csrr %0, fflags"
               : "=r"(flags)
               : "r"(lhs), "r"(rhs), "r"(dst), "r"(rm), "r"(vl),
                 "r"(mask_bits)
               : "v0", "v1", "v2", "v3", "memory");
  return flags;
}
```

两段代码执行同一种浮点加法。区别在于第二段使用 `v0.t` 选择活动元素，便于结合舍入结果分析 masked-off lane 的目的值和异常标志。

### 1.2 对应反汇编

当前 `run_unmasked` 的反汇编如下。GNU objdump 将浮点 CSR 操作显示为伪指令：`fsrm` 对应写 `frm`，`fsflagsi zero,0` 对应清 `fflags`，`frflags` 对应读 `fflags`。

```asm
000000008000012a <run_unmasked>:
    8000012a: c795                 beqz      a5,80000156
    8000012c: 00251073             fsrm      a0
    80000130: 00105073             fsflagsi  zero,0
    80000134: 0105f057             vsetvli   zero,a1,e32,m1,tu,mu
    80000138: 02066087             vle32.v   v1,(a2)
    8000013c: 0206e107             vle32.v   v2,(a3)
    80000140: 02076187             vle32.v   v3,(a4)
    80000144: 021111d7             vfadd.vv  v3,v1,v2
    80000148: 020761a7             vse32.v   v3,(a4)
    8000014c: 00102673             frflags   a2
    80000150: 0006051b             sext.w    a0,a2
    80000154: 8082                 ret
    80000156: 00251073             fsrm      a0
    8000015a: 0105f057             vsetvli   zero,a1,e32,m1,tu,mu
    8000015e: 02066087             vle32.v   v1,(a2)
    80000162: 0206e107             vle32.v   v2,(a3)
    80000166: 02076187             vle32.v   v3,(a4)
    8000016a: 021111d7             vfadd.vv  v3,v1,v2
    8000016e: 020761a7             vse32.v   v3,(a4)
    80000172: 00102673             frflags   a2
    80000176: 0006051b             sext.w    a0,a2
    8000017a: 8082                 ret
```

`a0` 到 `a5` 依次承载 `rm`、`vl`、`lhs`、`rhs`、`dst`、`clear_flags`。入口先判断是否清 flags：非零时执行 `0x80000144` 的加法，零时跳转到不清 flags 的分支，在 `0x8000016a` 执行加法。两条路径都在运算前写入模式。

当前带 mask 的函数被常量传播为 `run_masked.constprop.0`，其中模式固定为 RNE，VL 固定为 4：

```asm
00000000800001ce <run_masked.constprop.0>:
    800001ce: 4781                 li        a5,0
    800001d0: 4711                 li        a4,4
    800001d2: 00279073             fsrm      a5
    800001d6: 00105073             fsflagsi  zero,0
    800001da: 01077057             vsetvli   zero,a4,e32,m1,tu,mu
    800001de: 0205e087             vle32.v   v1,(a1)
    800001e2: 02066107             vle32.v   v2,(a2)
    800001e6: 0206e187             vle32.v   v3,(a3)
    800001ea: 5e054057             vmv.v.x   v0,a0
    800001ee: 001111d7             vfadd.vv  v3,v1,v2,v0.t
    800001f2: 0206e1a7             vse32.v   v3,(a3)
    800001f6: 00102573             frflags   a0
    800001fa: 2501                 sext.w    a0,a0
    800001fc: 8082                 ret
```

从反汇编可以直接确定：每条被分析的加法都使用此前显式写入的 `frm`；带 mask 的加法还受 v0 中相应 mask 位约束。

## 2. 舍入模式

浮点加法的数学含义是先求两个已编码操作数的精确和，再将它表示为目标精度。精确和能由 FP32 表示时，各模式结果相同；不能表示时，它位于相邻的两个 FP32 候选之间，舍入模式决定选择哪一个。

### 2.1 五种模式的理论含义

| `frm` | 模式 | 选择规则 | 当前测试中主要观察的差异 |
|---:|---|---|---|
| `000` | RNE | 最近值；恰好等距时取有效数最低位为 0 的候选 | 中点的下候选是奇数还是偶数 |
| `001` | RTZ | 向零舍入 | 正负结果均取幅值较小的一侧 |
| `010` | RDN | 向负无穷舍入 | 负结果必要时增加幅值，变得更负 |
| `011` | RUP | 向正无穷舍入 | 正结果只要存在非零舍弃信息就上调 |
| `100` | RMM | 最近值；恰好等距时取幅值较大的候选 | 正负中点均远离零 |

“向上”“向下”按数值轴理解。对于负数，RDN 选择更负的值，RUP 选择更接近零的值。RMM 只在中点的平局规则上区别于 RNE，不是所有不精确结果都远离零。

### 2.2 FP32 精度、ULP 与中点

FP32 正规数由 1 位符号、8 位指数、23 位 fraction 构成，隐含最高位使有效精度达到 24 位。其数值为：

```text
(-1)^sign × (1 + fraction / 2^23) × 2^(exponent - 127)
```

在 `[1,2)` 区间，相邻 FP32 的间距为 `2^-23`。这一间距称为此处的一个 ULP；恰好中点距两侧均为 `2^-24`。因此当前测试选择 `2^-25` 和 `2^-24` 作为小操作数，分别构造低于中点和恰好中点的结果。

以 `1.0` 附近的两个候选为例：

| 位型 | 精确数值 |
|---|---|
| `0x3f800000` | `1` |
| `0x3f800001` | `1 + 2^-23` |
| 两者中点 | `1 + 2^-24`，不能由 FP32 精确表示 |

RNE 中的“偶数”指保留有效数最低位为 0。例如 `0x3f800000`、`0x3f800002` 为偶数候选，`0x3f800001` 为奇数候选，与十进制整数的奇偶分类不同。

### 2.3 guard、round、sticky 如何表达舍弃信息

硬件通常保留高位有效数，同时记录低位信息：

- `LSB` 是保留有效数的最低位；
- `G`（guard）是紧邻保留范围的下一位；
- `R`（round）是 G 后的一位；
- `S`（sticky）是其余更低位的 OR。

`G=1,R=0,S=0` 表示恰好半 ULP；`G=0` 且 `R|S=1` 表示低于中点但仍不精确；`G=1` 且 `R|S=1` 表示超过中点。对本测试普通同号加法，是否增加保留幅值可以概括为：

| 模式 | 增加 1 ULP 的条件 |
|---|---|
| RNE | `G && (R || S || LSB)` |
| RTZ | 不因普通舍入增加幅值 |
| RDN | `sign && (G || R || S)` |
| RUP | `!sign && (G || R || S)` |
| RMM | `G` |

这里的“加一”是增加保留有效数的最低单位。若产生尾数进位，还需规格化并调整指数；对负数增加幅值意味着更负，不能把它统一理解为数值增加。

## 3. 不同模式对当前数据的预期影响

### 3.1 当前六类输入

当前测试中每组输入在四个 lane 上相同：

| 用例 | `lhs` 位型 | `rhs` 位型 | 数学关系 |
|---|---|---|---|
| `exact` | `0x3f800000` | `0x3f000000` | `1 + 0.5`，精确可表示 |
| `inexact` | `0x3f800000` | `0x33000000` | `1 + 2^-25`，四分之一 ULP |
| `pos_mid_even` | `0x3f800000` | `0x33800000` | `1 + 2^-24`，正中点且下候选为偶数 |
| `neg_mid_even` | `0xbf800000` | `0xb3800000` | `-1 - 2^-24`，负中点且近零候选为偶数 |
| `pos_mid_odd` | `0x3f800001` | `0x33800000` | `(1 + 2^-23) + 2^-24`，正中点且下候选为奇数 |
| `overflow` | `0x7f7fffff` | `0x7f7fffff` | 最大有限 FP32 加自身，正溢出 |

这些输入分别改变精确性、距中点的位置、结果符号、候选奇偶和指数范围，使五种模式的判定条件能够被逐项分析。

### 3.2 精确结果与低于中点

`exact` 的结果为 `1.5`，位型 `0x3fc00000`。舍弃信息全为 0，五种模式结果相同，NX 为 0。

`inexact` 的精确和为 `1 + 2^-25`，只比 `0x3f800000` 高四分之一 ULP，更接近下候选。因此 RNE、RMM 都保留下候选；RTZ、RDN 对正数也选择下候选；只有 RUP 选择 `0x3f800001`。五种模式都丢弃非零低位，所以 NX 均为 1。

### 3.3 正中点与最低保留位的奇偶

`pos_mid_even` 的精确和恰好在 `0x3f800000` 与 `0x3f800001` 中间。下候选最低位为 0，RNE 保留它；RMM 按最大幅值选择上候选；RUP 也选上候选，RTZ、RDN 选下候选。

`pos_mid_odd` 将较大操作数换为 `0x3f800001`。中点两侧成为 `0x3f800001` 与 `0x3f800002`，下候选最低位为 1，RNE 因而增加一个 ULP，选择偶数上候选。RMM、RUP 仍选上候选，RTZ、RDN 保留下候选。

两个中点一起分析，才能看清 RNE 的选择取决于候选奇偶：它既不总在中点向零，也不总在中点远离零。

### 3.4 负中点与舍入方向

`neg_mid_even` 的精确和是 `-1 - 2^-24`，位于近零的 `0xbf800000` 与更负的 `0xbf800001` 中间。

RNE 取偶数近零候选，RTZ 向零，RUP 向正无穷，三者均为 `0xbf800000`；RDN 向更负方向，RMM 在中点远离零，两者均为 `0xbf800001`。这组输入区分了正数下经常相同的 RTZ 与 RDN，也说明 RUP 并非对正负数都增加幅值。

### 3.5 正溢出与结果范围

最大有限 FP32 为 `(2 - 2^-23) × 2^127`，编码 `0x7f7fffff`。它与自身相加需要更大的指数范围，不能作为有限 FP32 保存。

对这个正溢出结果，RTZ、RDN 返回最大有限数 `0x7f7fffff`；RNE、RUP、RMM 返回正无穷 `0x7f800000`。无论最后返回有限数还是无穷，这次操作都发生了溢出和不精确，故 `OF|NX=0x05`。

### 3.6 六类输入在五种模式下的结果

下表每格为一个活动 lane 的结果位型，基础测试的四个 lane 结果相同。`fflags` 从高到低为 `NV DZ OF UF NX`：

| 用例 | RNE | RTZ | RDN | RUP | RMM | `fflags` |
|---|---|---|---|---|---|---|
| `exact` | `0x3fc00000` | `0x3fc00000` | `0x3fc00000` | `0x3fc00000` | `0x3fc00000` | `0x00` |
| `inexact` | `0x3f800000` | `0x3f800000` | `0x3f800000` | `0x3f800001` | `0x3f800000` | `0x01` |
| `pos_mid_even` | `0x3f800000` | `0x3f800000` | `0x3f800000` | `0x3f800001` | `0x3f800001` | `0x01` |
| `neg_mid_even` | `0xbf800000` | `0xbf800000` | `0xbf800001` | `0xbf800000` | `0xbf800001` | `0x01` |
| `pos_mid_odd` | `0x3f800002` | `0x3f800001` | `0x3f800001` | `0x3f800002` | `0x3f800002` | `0x01` |
| `overflow` | `0x7f800000` | `0x7f7fffff` | `0x7f7fffff` | `0x7f800000` | `0x7f800000` | `0x05` |

`fflags` 为累计状态，表中 flags 指运算前已清零时这条指令产生的值。连续运算不清零时，读回值还会包含此前指令留下的异常标志。

## 4. 如何配置舍入模式

### 4.1 `frm`、`fcsr` 与 `vcsr` 的区别

`frm` 位于 `fcsr[7:5]`，`fflags` 位于 `fcsr[4:0]`。写 `fcsr=0` 同时设置 RNE 并清空 flags；单独写 `frm` 不会清除 flags，单独清 `fflags` 也不会改变模式。

`vcsr` 包含向量定点舍入控制 `vxrm` 和饱和标志 `vxsat`。当前源码中的 `csrwi vcsr,0` 只初始化定点向量状态，浮点加法仍读取 `frm`。

标量浮点指令可具有静态 `rm` 字段，`rm=111` 表示读取动态 `frm`；`vfadd.vv` 本身没有这个静态舍入字段。测试函数参数虽然叫 `rm`，其值实际通过 `csrw frm` 写进 CSR。动态 `frm` 只使用 `000..100`，不能把静态字段的 DYN=`111` 当作一种动态模式写入。

### 4.2 每轮先设置模式，再执行同一条指令

固定模式可以使用：

```asm
csrwi frm, 0   # RNE
csrwi frm, 1   # RTZ
csrwi frm, 2   # RDN
csrwi frm, 3   # RUP
csrwi frm, 4   # RMM
```

当前测试在循环中把 `rm=0..4` 传给 `run_unmasked()`，所以使用寄存器形式 `csrw frm,rs1`，随后清 flags、配置 VL、装载操作数并执行加法。模式和运算放在同一段 `asm volatile` 中，使两者的指令先后关系明确。

`memory` clobber 告诉编译器汇编会访问内存，向量寄存器 clobber 声明 v1/v2/v3 被修改。位型由 `vle32.v` 原样装入，`vfadd.vv` 将它们解释为 FP32。C 中把整数强制转换为 `float` 会改变数值含义，不能代替这种位型传递。

## 5. 源码与反汇编分析

### 5.1 初始化、装载与结果写回

`init_state()` 将 `mstatus` 的 FS/VS 设置为可用状态，并清零 `fcsr`、`vcsr`、`vstart`。这建立了初始状态，但每次加法的模式仍由本轮 `csrw frm` 决定。

反汇编中的 `vsetvli zero,a1,e32,m1,tu,mu` 设置元素宽度为 32 位、LMUL 为 1，VL 来自函数参数。三个 `vle32.v` 分别装入 lhs、rhs 和旧目的值；`vfadd.vv` 计算活动元素；`vse32.v` 写回结果；`frflags` 读取异常标志。

因此，对基础测试的 `vl=4`，可以将每个活动 lane 理解为第 3 节的一次独立 FP32 舍入，而一条向量指令的 flags 来自所有活动 lane 的聚合。

### 5.2 从译码到向量加法器的舍入模式传递

下文 `backend/` 路径均位于 XiangShan 的 `src/main/scala/xiangshan/` 下，`yunsuan/` 路径相对于 XiangShan 根目录。`backend/decode/VecDecoder.scala` 将 `VFADD_VV` 译码为 `FuType.vfalu` 和 `VfaluType.vfadd`；`backend/fu/FuConfig.scala` 中 `VfaluCfg` 声明 `needSrcFrm=true`、`writeFflags=true`。

`backend/Backend.scala` 将浮点 CSR 模式送入向量浮点执行块：

```scala
vfExuBlock.io.frm.foreach(_ := csrio.fpu.frm)
```

`backend/fu/wrapper/VFALU.scala` 按 64 位切片实例化 `VectorFloatAdder`，并连接操作数和舍入模式：

```scala
private val dataWidthOfDataModule = 64
private val numVecModule = dataWidth / dataWidthOfDataModule
private val vfalus = Seq.fill(numVecModule)(Module(new VectorFloatAdder))
```

```scala
vfalus.zipWithIndex.foreach {
  case (mod, i) =>
    mod.io.fp_a       := vs2Split.io.outVec64b(i)
    mod.io.fp_b       := vs1Split.io.outVec64b(i)
    mod.io.round_mode := rm
    resultData(i)     := mod.io.fp_result
    fflagsData(i)     := mod.io.fflags
    // 其余控制连接略。
}
```

每个 64 位切片处理两个 FP32 元素；在 128 位向量宽度下，四个 lane 对应两个切片。各切片接收相同模式，不会为同一条加法的不同 lane 另选舍入规则。这条指令进入向量 `VFALU/VectorFloatAdder`，而不是标量 `FALU/FloatAdder`。

### 5.3 当前测试为什么走 far path

在 `yunsuan/src/main/scala/yunsuan/vector/VectorFloatAdder.scala` 的 FP32 加法器中，`round_mode` 同时送入 far path 和 close path。路径选择依赖有效加减关系和指数差：

```scala
val EOP = (fp_a_to32.head(1) ^ io.is_sub ^ fp_b_to32.head(1)).asBool
val is_far_path = !EOP |
  (EOP & absEaSubEb(absEaSubEb.getWidth - 1, 1).orR) |
  (absEaSubEb === 1.U & (Efp_a_is_zero ^ Efp_b_is_zero))
```

`EOP` 表示有效减法。当前六组基础输入均是同号相加，`is_sub=0` 且两符号相同，所以 `EOP=0`，选择 far path。即使两个指数相同，同号加法仍走这条路径。

far path 右移较小操作数完成对阶，并保留 G/R/S 信息；close path 主要处理指数接近的有效减法，在结果消去后进行规格化。当前中点和方向舍入的分析应具体对应 far path。

### 5.4 G/R/S 怎样进入模式判定

`FarPathF32WidenF16MixedPipeline` 随运算接收事件锁存模式：

```scala
val RNE_reg = RegEnable(io.round_mode === "b000".U, fire)
val RTZ_reg = RegEnable(io.round_mode === "b001".U, fire)
val RDN_reg = RegEnable(io.round_mode === "b010".U, fire)
val RUP_reg = RegEnable(io.round_mode === "b011".U, fire)
val RMM_reg = RegEnable(io.round_mode === "b100".U, fire)
```

normal 情形的 `lgs_normal_reg` 由保留有效数最低位、guard 和 round/sticky 构成。对当前有效加法，可以读为：

```text
lgs_normal_reg(2) = LSB
lgs_normal_reg(1) = G
lgs_normal_reg(0) = R | S
```

源码中的进位判定为：

```scala
val far_case_normal_round_up_reg =
  (RegEnable(EOP, fire) & !lgs_normal_reg(1) & !lgs_normal_reg(0)) |
  (RNE_reg & lgs_normal_reg(1) & (lgs_normal_reg(2) | lgs_normal_reg(0))) |
  (RDN_reg & far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
  (RUP_reg & !far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
  (RMM_reg & lgs_normal_reg(1))
```

第一项是有效减法补码修正，当前 `EOP=0` 时不生效。其余项与第 2 节的理论条件逐一对应：RNE 查看 guard、低位信息和奇偶；RDN/RUP 结合结果符号；RMM 只需 guard 判定是否达到半 ULP；RTZ 没有普通舍入进位项。

### 5.5 用当前输入逐项代入“加一”或“舍弃”

| 当前输入 | sign | LSB | G | R\|S | 选择增加幅值的模式 |
|---|---:|---:|---:|---:|---|
| `inexact` | 0 | 0 | 0 | 1 | RUP |
| `pos_mid_even` | 0 | 0 | 1 | 0 | RUP、RMM |
| `neg_mid_even` | 1 | 0 | 1 | 0 | RDN、RMM |
| `pos_mid_odd` | 0 | 1 | 1 | 0 | RNE、RUP、RMM |

对 `inexact`，RNE 的 guard 条件为 0，RMM 的 guard 条件也为 0；RUP 看到正符号且有非零舍弃信息，故只有它增加幅值。

对 `pos_mid_even`，RNE 的 `(LSB | R_or_S)` 为 0，保留 `0x3f800000`；RMM 的 guard 为 1，选择 `0x3f800001`。这就是两种最近舍入在当前测试中的直接区别。

对 `pos_mid_odd`，RNE 的 LSB 变为 1，于是与 RMM、RUP 一起选择 `0x3f800002`。对 `neg_mid_even`，RDN 的符号条件成立而 RUP 不成立，所以前者得到更负的 `0xbf800001`，后者保留 `0xbf800000`。

`round_up` 控制的是有效数候选选择。源码通过 `FS0/FS1` 和末位组合形成结果，不要求存在一条把整个 IEEE 754 编码直接加 1 的语句。RTZ 选择不增加幅值的候选，但仍可能产生 NX。

### 5.6 尾数进位、指数溢出与 NX

`far_case_overflow_reg` 表示尾数加法产生更高位，需要改变位布局并规格化；它不等于最终异常 OF。尾数布局改变后，`lgs_overflow_reg` 重新提取 LSB/G/R/S，并使用相同的模式规则。

最终指数超过有限范围时，`OF_reg` 触发溢出结果选择。源码中的条件为：

```scala
val result_overflow_reg = Mux(
  RTZ_reg | (RDN_reg & !far_sign_result_reg) | (RUP_reg & far_sign_result_reg),
  Cat(far_sign_result_reg, Fill(exponentWidth-1,1.U), 0.U,
      Fill(significandWidth-1,1.U)),
  Cat(far_sign_result_reg, Fill(exponentWidth,1.U),
      Fill(significandWidth-1,0.U))
)
```

第一种编码是同号最大有限数，第二种编码是同号无穷。当前 `overflow` 为正结果，条件化简为 RTZ 或 RDN，因此它们返回最大有限数，其余模式返回正无穷。

NX 单独由舍弃信息和 OF 决定：

```scala
NX := Mux(far_case_normal_reg,
          lgs_normal_reg(1,0).orR,
          lgs_overflow_reg(1,0).orR) | OF_reg
```

因此“是否加一”与“是否不精确”是两个判断：`inexact` 在 RTZ 下不进位仍置 NX，`overflow` 即使返回最大有限数也置 OF 与 NX。

### 5.7 close path 中的舍入控制

FP32 close path 将低位、候选方向和符号结合。把精度选择固定为 FP32 后，其核心可摘写为：

```scala
val CS2_round_up = significand_fp_b(0) & (
  (RUP & !fp_a_sign) | (RDN & fp_a_sign) |
  (RNE & CS2(1) & CS2(0)) | RMM
)
val CS3_round_up = significand_fp_a(0) & (
  (RUP & fp_a_sign) | (RDN & !fp_a_sign) |
  (RNE & CS3(1) & CS3(0)) | RMM
)
```

CS2/CS3 对应相反的候选大小关系，符号项因而不同；后续 `sel_CS2/sel_CS3/sel_CS4` 选择相应候选。其语义仍是 RNE 按最近偶数、RMM 在中点远离零、RDN/RUP 按结果方向调整，RTZ 不引入普通进位项。

当前基础输入没有有限异号消去场景，所以它们具体演示的是 far path 的规则。close path 在此用于说明同一模式信号如何作用于另一条数据通路，不能将本次同号加法结果直接当成 close path 的计算结果。

## 6. 当前测试中的 mask、VL 与异常标志

### 6.1 masked-off 元素是否参与舍入和异常聚合

`test_mask_and_vl()` 使用 `inexact` 输入。执行 `run_masked(RM_RNE,4,0x5,...)` 时，mask 低四位为 `0101b`，lane 0/2 活动，lane 1/3 保持预置目的值：

```text
lane 0: 0x3f800000
lane 1: 0x4b000000
lane 2: 0x3f800000
lane 3: 0x4b000000
fflags: 0x01
```

全 mask 的 `0x0` 不更新任何元素，也不应产生本次加法的 flags。关键在于 inactive lane 不得把“若执行就会产生的 NX”并入架构状态。

`VFALU.scala` 根据元素宽度、mask、VL 和 vstart 形成 lane flags 使能，然后按位 OR：

```scala
val outFFlags = allFFlagsEn.zip(allFFlags).map {
  case (en, fflags) => Mux(en, fflags, 0.U(5.W))
}.reduce(_ | _)
```

这使活动 lane 的 NX 能进入指令 flags，而 masked-off、tail 元素以 0 参与聚合。目的值的 undisturbed 合并与 flags 门控是两项不同的工作。

### 6.2 VL 缩短时只处理活动范围

当前 `run_unmasked(RM_RUP,2,...)` 只处理 lane 0/1，结果应为 `0x3f800001`，NX 为 1；`VL=0` 时没有活动元素，清零后的 flags 保持 0。

从第 1 节汇编可见，加载、计算和存储共用同一个 VL。因此 VL=2 时，内存后两个位置保持 sentinel 也可能直接来自 `vse32.v` 只写两个元素；这不能单独说明 v3 的 tail 内容。`tu` 规定寄存器 tail 保持，但要直接观察它，需要先以完整 VL 装入旧 v3，再在运算后以完整 VL 导出。部分 mask 场景的 VL 为 4，未加 mask 的存储会导出四个元素，可以直接观察 masked-off 目的值。

### 6.3 不清 flags 时的累计语义

`test_flags_accumulate()` 先执行不精确加法，再执行正溢出，最后执行 `+inf + -inf`。仅第一条调用清 flags：

```c
run_unmasked(RM_RNE, LANES, lhs, rhs, dst, 1);
run_unmasked(RM_RNE, LANES, lhs, rhs, dst, 0);
run_unmasked(RM_RNE, LANES, pos_inf, neg_inf, dst, 0);
```

这里摘录调用形式；两次 lhs/rhs 调用之间，源码将输入从 `inexact` 换成最大有限数。三次读回应为：

```text
NX -> OF|NX -> NV|OF|NX
01 -> 05    -> 15
```

`+inf + -inf` 由加法器特殊值分支产生 NV，不经过普通有限数 G/R/S 规则。它本次只贡献 `0x10`，累计后仍有 OF/NX，说明异常标志不会因下一条指令未产生同类异常而自动清除。

`backend/rob/Rob.scala` 按 `robIdx` 汇总写回 flags，并在提交时送给 CSR；`backend/fu/NewCSR/Unprivileged.scala` 执行逐位累积：

```scala
reg.NX := robCommit.fflags.bits(0) || reg.NX
reg.OF := robCommit.fflags.bits(2) || reg.OF
reg.NV := robCommit.fflags.bits(4) || reg.NV
```

这也是独立比较不同舍入模式前必须清 `fflags` 的原因：否则读到的是多条指令的累计状态。

### 6.4 同一中点输入下连续切换模式

`test_mode_switch()` 固定使用 `pos_mid_even`，模式顺序为：

```c
const unsigned sequence[] = {
  RM_RNE, RM_RUP, RM_RTZ, RM_RMM, RM_RDN, RM_RNE
};
```

每轮都先写 `frm` 再执行加法，相应结果为：

```text
RNE         RUP         RTZ         RMM         RDN         RNE
3f800000 -> 3f800001 -> 3f800000 -> 3f800001 -> 3f800000 -> 3f800000
```

输入不变时，结果差异直接来自模式选择。源码的 `RegEnable(..., fire)` 使舍入判定随相应运算锁存，这样后续候选选择使用的是该次运算的模式。相邻结果相同的两轮仍应按各自规则理解，不能只凭输出相同就认定两个模式等价。

## 7. 结论

当前测试通过同一条 `vfadd.vv`、六类输入和五种 `frm` 展示舍入规则。低于中点的输入区分最近舍入与正向舍入，正负中点揭示 RDN/RUP 的符号方向，两种候选奇偶揭示 RNE 与 RMM 的平局规则，最大有限数加自身则说明溢出结果也受模式控制。

从实现看，`frm` 经向量浮点执行块传给 `VectorFloatAdder`，far path 根据 G/R/S、保留最低位和结果符号选择有效数候选；异常标志独立计算，经活动 lane 门控、ROB 提交后累积到 CSR。因而分析一次浮点加法时，应同时看精确和的位置、候选奇偶、模式选择以及 flags，而不能只看结果是否增加了一个 ULP。
