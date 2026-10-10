# 向量浮点加法中舍入模式对结果的影响

本文根据当前的 `vector-fadd.c` 与对应反汇编重新整理，说明 FP32 向量浮点加法的舍入理论、如何配置舍入模式，以及这些模式对本程序计算结果的影响。

## 1.指令选取

### 1.1 当前程序源码

以下是本次分析对应的完整源码。它保留了用户当前版本的向量状态初始化、四个相同的加法 lane，以及结果写回；舍入模式没有在 `main` 中显式修改。

```c
#include <am.h>
#include <klib.h>

#define VECTOR_LEN 4

static void init_vector_state() {
  // Enable the vector unit and clear vector control state.
  asm volatile("li t0, 0x2500\n"
               "csrs mstatus, t0\n"
               "csrwi vcsr, 0\n"
               "csrwi vstart, 0"
               :
               :
               : "t0", "memory");
}

int main() {
  init_vector_state();

  float lhs[VECTOR_LEN] = {1.0f, 1.0f, 1.0f, 1.0f};
  float rhs[VECTOR_LEN] = {0.1f, 0.1f, 0.1f, 0.1f};
  float result[VECTOR_LEN] = {0.0f, 0.0f, 0.0f, 0.0f};

  asm volatile("vsetivli zero, 4, e32, m1, ta, ma\n"
               "vle32.v v1, (%0)\n"
               "vle32.v v2, (%1)\n"
               "vfadd.vv v3, v1, v2\n"
               "vse32.v v3, (%2)\n"
               "fence rw, rw"
               :
               : "r"(lhs), "r"(rhs), "r"(result)
               : "v1", "v2", "v3", "memory");

  // printf("vfadd.vv result: %f %f %f %f\n", result[0], result[1], result[2], result[3]);

  return 0;
}
```

### 1.2 对应反汇编

以下摘录保留了启动代码中对 `fcsr` 的初始化，以及 `main` 的完整反汇编。启动阶段把 `fcsr` 清零；`main` 中的 `csrwi vcsr,0` 清的是向量定点控制状态，并没有覆盖浮点舍入模式。

```asm
0000000080000000 <_start>:
    ...
    80000082: 00305073  csrwi fcsr,0
    ...

000000008000012a <main>:
    8000012a: 7179                 addi      sp,sp,-48
    8000012c: 6289                 lui       t0,0x2
    8000012e: 5002829b             addiw     t0,t0,1280       # 0x2500
    80000132: 3002a073             csrs      mstatus,t0
    80000136: 00f05073             csrwi     vcsr,0
    8000013a: 00805073             csrwi     vstart,0
    8000013e: 00001797             auipc     a5,0x1
    80000142: 1b278793             addi      a5,a5,434
    80000146: cd027057             vsetivli  zero,4,e32,m1,ta,ma
    8000014a: 6b94                 ld        a3,16(a5)
    8000014c: 638c                 ld        a1,0(a5)
    8000014e: 6790                 ld        a2,8(a5)
    80000150: 6f98                 ld        a4,24(a5)
    80000152: 5e0030d7             vmv.v.i   v1,0
    80000156: 101c                 addi      a5,sp,32
    80000158: e836                 sd        a3,16(sp)
    8000015a: 0207e0a7             vse32.v   v1,(a5)
    8000015e: e02e                 sd        a1,0(sp)
    80000160: e432                 sd        a2,8(sp)
    80000162: ec3a                 sd        a4,24(sp)
    80000164: 0814                 addi      a3,sp,16
    80000166: cd027057             vsetivli  zero,4,e32,m1,ta,ma
    8000016a: 02016087             vle32.v   v1,(sp)
    8000016e: 0206e107             vle32.v   v2,(a3)
    80000172: 021111d7             vfadd.vv  v3,v1,v2
    80000176: 0207e1a7             vse32.v   v3,(a5)
    8000017a: 0330000f             fence     rw,rw
    8000017e: 4501                 li        a0,0
    80000180: 6145                 addi      sp,sp,48
    80000182: 8082                 ret
```

反汇编与当前源码吻合：加法指令在 `0x80000172`；其前没有 `csrw frm` 或 `fsrm`。本次运行的模式来自启动代码清零后的 `fcsr.frm=000`，即 RNE。数组常量复制、结果数组清零等编译器生成代码不改变舍入模式。

## 2. 舍入模式

浮点加法先形成精确数学结果，再把它编码到目标精度。若结果不能由 FP32 精确表示，就会落在两个相邻的 FP32 值之间；舍入模式决定选哪一个。两个相邻数间距称为 ULP。精确结果可表示时，各模式结果相同；只有精度需要丢弃时，舍入方向才可能改变结果位型。

RISC-V `frm` 的五种标准舍入模式为：

| `frm` | 名称 | 选择规则 | 正数发生不精确时的方向 |
|---:|---|---|---|
| `000` | RNE | 最近值；正好等距时取有效数最低位为 0 的结果（ties to even） | 取较近值 |
| `001` | RTZ | 向零舍入 | 取绝对值较小的一侧 |
| `010` | RDN | 向负无穷舍入 | 取数值较小的一侧 |
| `011` | RUP | 向正无穷舍入 | 取数值较大的一侧 |
| `100` | RMM | 最近值；正好等距时取绝对值较大的结果 | 取较近值 |

“向上/向下”应按数值轴方向理解，而不是对正负数都按绝对值理解。例如，对负数，向负无穷会更负，向正无穷则更接近零。RMM 只在恰好等距时与 RNE 的 tie 规则不同；不是中点时，二者都选最近值。

### 2.1 `1.0f + 0.1f` 的精确位置

`1.0f` 的 FP32 位型是 `0x3f800000`。十进制常量 `0.1f` 存入 FP32 后的位型是 `0x3dcccccd`，它表示的精确二进制数约为 `0.10000000149011611938`。所以两个已编码操作数的精确和为：

```text
1.0f + 0.1f = 1.10000000149011611938...
```

相邻的 FP32 候选值是：

| 候选编码 | 数值 | 与精确和的关系 |
|---|---:|---|
| `0x3f8ccccc` | `1.099999904632568359375` | 小于精确和 |
| `0x3f8ccccd` | `1.10000002384185791015625` | 大于精确和，且更接近精确和 |

精确和不在中点上，而是更靠近上方的 `0x3f8ccccd`。因此本例能观察定向舍入（RTZ/RDN 与 RUP）与最近舍入的差异，但**不能单独区分 RNE 和 RMM 的 tie 行为**。

## 3. 不同模式对当前数据的预期影响

对四个相同 lane `[1.0f] + [0.1f]`，所有 lane 的结果相同：

| `frm` | 模式 | 每 lane 结果位型 | 解释 |
|---:|---|---|---|
| `000` | RNE | `0x3f8ccccd` | 最近值；精确和更靠近上方 FP32 |
| `001` | RTZ | `0x3f8ccccc` | 正数向零，取下方值 |
| `010` | RDN | `0x3f8ccccc` | 正数向负无穷，取下方值 |
| `011` | RUP | `0x3f8ccccd` | 正数向正无穷，取上方值 |
| `100` | RMM | `0x3f8ccccd` | 最近值；本例不是中点，因此和 RNE 相同 |

对应的四元素结果向量位型分别为：

```text
RNE / RUP / RMM: [0x3f8ccccd, 0x3f8ccccd, 0x3f8ccccd, 0x3f8ccccd]
RTZ / RDN:       [0x3f8ccccc, 0x3f8ccccc, 0x3f8ccccc, 0x3f8ccccc]
```

结果均丢弃了非零低位，因此 `fflags.NX`（inexact，不精确）应置位；本次正常有限数加法不应产生 NV、DZ、OF 或 UF。`fflags` 是累积标志，多条操作的异常标志可能 OR 到一起；每次独立比较前应清零，读完后再记录。

## 4. 如何配置舍入模式

### 4.1 `frm`、`fcsr` 与 `vcsr` 的区别

- `frm` 是浮点舍入模式 CSR；它位于 `fcsr[7:5]`，向量浮点加法使用这里的动态舍入模式。
- `fcsr` 同时包含 `frm` 和累计异常标志 `fflags`。写 `fcsr=0` 会把 `frm` 与 `fflags` 都清零，也就是设为 RNE 并清标志。
- `vcsr` 包含向量定点舍入控制 `vxrm` 与饱和标志 `vxsat`。`csrwi vcsr,0` **不等于**设置浮点舍入模式。
- 标准编码 `101`、`110`、`111` 是保留值；本实验只使用 `frm=0..4`。

可用以下汇编设置某一种模式：

```asm
csrwi frm, 0   # RNE
csrwi frm, 1   # RTZ
csrwi frm, 2   # RDN
csrwi frm, 3   # RUP
csrwi frm, 4   # RMM
```

如果模式值存在通用寄存器中，使用 `csrw frm, rs1`。写入必须发生在对应 `vfadd.vv` 之前。把设置与运算放在同一段 `asm volatile` 中，可以让测试序列直观且避免 C 编译器把独立语句重排到浮点指令之后。

### 4.2 将当前程序改成五种模式依次执行

下面给出基于当前输入数组的测试核心。每次运行前先设置 `frm`、清空 `fflags`，执行同一条向量加法，再读取累计异常标志。`rm=0..4` 依次对应上表的五种模式。

```c
static unsigned run_vfadd_mode(unsigned rm, const float *lhs, const float *rhs, float *result) {
  unsigned flags;
  asm volatile("csrw frm, %4\n"
               "csrwi fflags, 0\n"
               "vsetivli zero, 4, e32, m1, ta, ma\n"
               "vle32.v v1, (%1)\n"
               "vle32.v v2, (%2)\n"
               "vfadd.vv v3, v1, v2\n"
               "vse32.v v3, (%3)\n"
               "csrr %0, fflags\n"
               "fence rw, rw"
               : "=r"(flags)
               : "r"(lhs), "r"(rhs), "r"(result), "r"(rm)
               : "v1", "v2", "v3", "memory");
  return flags;
}

int main() {
  init_vector_state();

  float lhs[VECTOR_LEN] = {1.0f, 1.0f, 1.0f, 1.0f};
  float rhs[VECTOR_LEN] = {0.1f, 0.1f, 0.1f, 0.1f};
  float results[5][VECTOR_LEN];
  const unsigned modes[] = {0, 1, 2, 3, 4};
  unsigned flags[5];

  for (unsigned i = 0; i < 5; i++) {
    flags[i] = run_vfadd_mode(modes[i], lhs, rhs, results[i]);
    // 比较 results[i][0..3] 的 FP32 原始位型，并记录 flags[i]。
  }

  return 0;
}
```

示例把“设置模式—清标志—执行—读标志”放在同一内联汇编块内，并将五次结果保存到 `results[5][4]`。若编译器或 ABI 对该向量扩展的寄存器约束有额外要求，应按当前工具链调整 clobber 列表，并检查反汇编确认每轮的 `csrw frm` 位于 `vfadd.vv` 之前。

## 5.源码与反汇编分析

### 5.1 状态初始化没有改变舍入模式

当前 `init_vector_state()` 执行 `csrs mstatus, 0x2500`，为浮点/向量扩展设置非 Off 状态，并清零 `vcsr`、`vstart`。其中 `vcsr=0` 是向量定点状态初始化；它不会把 `frm` 切换到 RTZ、RDN、RUP 或 RMM。

实际反汇编中的 `0x80000082: csrwi fcsr,0` 位于 `_start`，把 `frm` 初始化成 `000`，并清空 `fflags`。在 `main` 的 `vfadd.vv` 前，反汇编没有浮点 CSR 写入，所以当前程序按 RNE 执行。这与结果表第一行对应。

### 5.2 向量配置、装载、逐 lane 加法与存储

1. `vsetivli zero,4,e32,m1,ta,ma` 设置 `vl=4`、`SEW=32`、`LMUL=1`，后续向量浮点指令对四个 32 位活动元素执行。
2. `vle32.v v1,(sp)` 与 `vle32.v v2,(a3)` 分别把 `lhs`、`rhs` 的四个 FP32 值装入向量寄存器。编译器把数组从常量区拷贝到栈，所以地址寄存器显示为 `sp/a3`，这不改变数组内容。
3. `vfadd.vv v3,v1,v2` 对应 PC `0x80000172`，使用当前 `frm`，逐 lane 计算 `v1[i]+v2[i]`，并把每个 lane 的异常标志并入 `fflags`。
4. `vse32.v v3,(a5)` 将四个 FP32 结果写到 `result` 数组。`fence rw,rw` 约束此前读写的可观察顺序，不负责改变舍入模式。

当前程序没有打印结果，因为 `printf` 行仍被注释。即使取消注释，也建议同时检查十六进制位型；常规 `%f` 默认打印位数可能掩盖相邻 FP32 值只差一个 ULP 的情况。可通过 union 将浮点位型读作 `uint32_t`，避免仅依赖十进制显示。

### 5.3 通路中的舍入模式传递

香山 `VFAlu` 包装器按数据宽度切分向量操作数，并实例化 `VectorFloatAdder`；`vs2`、`vs1` 分别接到子模块 `fp_a`、`fp_b`，舍入控制由 `rm` 接入 `mod.io.round_mode`。本程序用 `SEW=32`、128 位向量数据时，两个 64 位计算切片各处理两个 FP32 lane。因而同一条 `vfadd.vv` 的 `frm` 对四个 lane 生效，不会因数据切片而改变。

源码位置：

- VFALU.scala：64 位切片宽度与 `VectorFloatAdder` 实例：xiangshan/backend/fu/wrapper/VFALU.scala:14
- VFALU.scala：操作数切分、`round_mode` 传递和结果收集：xiangshan/backend/fu/wrapper/VFALU.scala:224
- Instructions.scala：`VFADD_VV` 编码匹配：xiangshan/backend/decode/isa/Instructions.scala:512

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
}
```

当前已能读取的 Yunsuan `VectorFloatAdder.scala` 与上述理论对应。FP32 lane 进入 `FloatAdderF32WidenF16MixedPipeline` 后，`round_mode` 被同时送入 far path 和 close path（约 414–426 行）；顶层根据符号关系和指数差选择路径（约 427–460 行）。两条路径都把 5 种模式译成控制信号：RNE=`000`、RTZ=`001`、RDN=`010`、RUP=`011`、RMM=`100`。

舍入不是对完整无限精度结果再调用一个独立模块，而是和尾数计算、规格化一起完成：

- **Far path（指数差较大）**：先右移较小操作数，并从移位结果提取 guard、round、sticky 信息；若指数差超过有效位范围，则直接令 guard/round 为 0，并用较小尾数非零信息形成 sticky。加/减路径会对舍弃位做补码修正，再与结果有效数最低位组成舍入判定信息。normal 与 overflow 两种尾数布局分别计算 `far_case_normal_round_up`、`far_case_overflow_round_up`（约 871–910、925–951 行）。
- **Close path（同号数相加或指数接近的异号数相减）**：并行计算候选差值/和，按候选结果选择并前导零规格化。`CS2_round_up`、`CS3_round_up` 将被舍弃的最低位与模式、结果符号及候选有效数低位组合（约 1120–1133 行）；选择舍入候选后再形成结果。
- **舍入方向**：far path 的判定式明确体现：RNE 在 guard=1 且 (round/sticky 非零或保留有效数最低位为 1) 时增加一个 ULP；RMM 在 guard=1 时增加；RDN 仅对负结果且存在非零舍弃位时向更负方向调整；RUP 仅对正结果且存在非零舍弃位时向更正方向调整；RTZ 不增加。close path 的控制式用符号和候选位表达同一组定向规则。这里所谓“加一”是对保留尾数的最低有效位加 1，发生尾数进位时还可能使指数增加，并非对整个 IEEE 754 编码直接加 1。
- **NX 与 UF 的源码结论**：far path 的 `NX` 由舍弃位非零或 `OF` 产生；close path 也会根据舍弃位和是否向上选择候选设置 `NX`。但这两个 FP32 计算子模块中的 `UF` 都只声明为 `WireInit(false.B)`，没有后续赋值，因此本版本这条向量 FP32 加法实现不会由 far/close 子模块置位 underflow flag。`OF` 在 far path 有明确计算；close path 的 `OF` 仍保持初值 false。故对本文普通的 `1.0f + 0.1f` 样例，预期 `NX=1`、`NV/DZ/OF/UF=0` 与代码一致；对于极小结果或溢出边界，不应仅凭 IEEE 754 理论断言这版 RTL 已完整产生 UF/OF，需结合这份实现的限制进一步验证。

### 5.4 从译码到提交：`vfadd.vv` 的信号传递

本节跟踪反汇编中 PC=`0x80000172`、指令字=`0x021111d7` 的 `vfadd.vv v3,v1,v2`。VCD 的 timescale 为 1 ps，下面列出的是 VCD 绝对时间戳，不是时钟周期编号；本次采样窗口没有可用的时钟边沿记录，因此不要把相邻时间戳直接换算成周期数。短脉冲以 VCD 原始值变化确认。完整层级前缀为 `TOP.SimTop.cpu.l_soc.core_with_l2.core.backend`，可在波形工具中先按模块名搜索，再按下表信号名筛选。

```mermaid
flowchart LR
  A[向量译码<br/>DecodeUnit / VecDecoder] --> B[重命名<br/>Rename]
  B --> C[派遣<br/>Dispatch]
  C --> D[ROB 入队]
  C --> E[向量发射队列<br/>IssueQueueVfmaVialuFixVfalu]
  E --> F[选择 / 操作数收集<br/>deq + OG1/OG2]
  F --> G[VFALU 包装器<br/>VfExuBlock.exus_3.Vfalu]
  G --> H[VectorFloatAdder<br/>FP32 lane 子模块]
  H --> I[执行单元写回<br/>CtrlBlock / WB]
  I --> J[物理寄存器文件 + ROB]
  J --> K[提交 / 架构状态]
```

**`FloatAdder.scala` 与这条向量路径的关系**：此前讨论的 `FloatAdder.scala` 是标量浮点执行包装器 `FALU` 所实例化的标量加法器（xiangshan/backend/fu/wrapper/FALU.scala）。当前指令是 `vfadd.vv`，译码功能类型是 `vfalu`，波形进入 `VfExuBlock...Vfalu`，再到 `VectorFloatAdder` 的 lane 子模块；它不会先进入标量 `FALU`/`FloatAdder`。两条路径共享浮点舍入语义，但入口包装器和数据并行组织不同。



本次实际观测到的是 RNE 下每 lane `0x3f8ccccd`、NX 置位。以及本条加法自身不访问 LSQ 或 DCache。

### 5.5 五种舍入模式在代码中如何决定“加一”或“舍弃”

本节的“加一”指对**保留有效数的最低位增加 1 个 ULP**；“舍弃”指保留当前有效数，不因为被丢弃的低位而增加它。它不是把 sign/exponent/fraction 拼成的整个 IEEE 754 位型直接加 1。若尾数加一产生进位，结果可能需要规格化并增加指数。代码也可能预先并行计算“未加一”和“加一”两个候选，最后用选择信号选结果，并不一定存在一条独立的 `mantissa := mantissa + 1` 语句。

#### Far path：用 G/R/S 和保留位最低位作判定

指数差较大时进入 `FarPathF32WidenF16MixedPipeline`。小操作数右移后，代码取出 guard、round、sticky 位；sticky 表示更低位中是否至少有一个 1。为了把判定写得紧凑，源码把舍入信息组合为 `lgs_normal_reg`（normal 结果）或 `lgs_overflow_reg`（加法造成尾数溢出时的位布局）：

```scala
// normal 情形，略去寄存器及 EOP 补码修正的外围代码
val lgs_normal_reg = Cat(FS0_0_reg, correctedGuard, correctedRoundOrSticky)

val far_case_normal_round_up_reg =
  (RNE_reg & lgs_normal_reg(1) & (lgs_normal_reg(2) | lgs_normal_reg(0))) |
  (RDN_reg & far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
  (RUP_reg & !far_sign_result_reg & (lgs_normal_reg(1) | lgs_normal_reg(0))) |
  (RMM_reg & lgs_normal_reg(1))
```

在该组合中，`lgs(2)` 是保留有效数最低位，`lgs(1)` 是 guard，`lgs(0)` 汇总 round/sticky。对于普通加法舍入，`lgs(1)|lgs(0)` 表示存在非零舍弃信息。逐模式读这段逻辑：

| 模式 | 代码条件 | 何时加一 | 何时舍弃 | 直观含义 |
|---|---|---|---|---|
| **RNE** 最近偶数 | `G && (R_or_S || LSB)`，对应 `lgs(1) && (lgs(0) || lgs(2))` | 高于中点（`G=1` 且后续低位非零），或恰好中点且保留值最低位为 1 | 低于中点；恰好中点且保留值最低位为 0 | 非 tie 时选最近值；tie 时把结果调成最低位为 0 的偶数有效数。 |
| **RTZ** 向零 | 这条模式没有参与 `far_case_*_round_up` 的模式项 | 不因普通舍入增加 ULP | 有被丢弃位也保留截断后的有效数 | 正负数都向绝对值较小的方向。 |
| **RDN** 向负无穷 | `RDN && sign && (G || R_or_S)` | 结果为负且有非零舍弃信息 | 结果为正，或舍弃信息全为 0 | 只在截断结果还需要变得更负时加一个有效数单位。 |
| **RUP** 向正无穷 | `RUP && !sign && (G || R_or_S)` | 结果为正且有非零舍弃信息 | 结果为负，或舍弃信息全为 0 | 只在截断结果还需要变得更正时加一个有效数单位。 |
| **RMM** 最近值，tie 取最大幅值 | `RMM && G` | guard=1，即到达或超过中点 | guard=0 | 正好中点时选绝对值较大的候选；非 tie 时仍按最近值规则。 |

`far_case_overflow_round_up_reg` 对 overflow 尾数布局使用同一模式策略，但 G/R/S 的位位置由 `lgs_overflow_reg` 重新组织，不能把 normal 的位下标直接套过去。源码另有 `EOP`（有效减法）补码修正项：它用于修正减法路径的补码表示，不是第六种舍入模式；读源码时应把它与上表中 RNE/RTZ/RDN/RUP/RMM 的模式判定分开。选定加一时，结果尾数通过 `FS0`/`FS1` 等候选选择以及末位异或等逻辑形成；如果发生尾数进位，指数路径也会相应选 `EA+1`。位置约为 `VectorFloatAdder.scala:871–962`。

#### Close path：比较候选差值，并按模式选择舍入候选

指数接近的异号数相减可能产生长串前导零，因此走 `ClosePathF32WidenF16MixedPipeline`：代码并行形成 `CS2`、`CS3` 等候选，再规格化。此路径没有直接复用 far path 的 `lgs` 三位总线，而是把“被丢弃的最低有效位”、候选结果的低位和结果符号编码到 `CS2_round_up`/`CS3_round_up`：

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

`CS2` 与 `CS3` 对应相反的操作数大小关系，故低位和符号项看起来不完全相同；它们是在各自候选布局下实现一致的舍入语义。按模式理解：

- **RNE**：只有被丢弃的 guard 类低位为 1 时才可能加一；若候选低位编码表明是 tie，则再看保留结果的奇偶性（源码中由 `CSx(1) & CSx(0)` 编码），奇数才选加一候选，偶数则保留。
- **RTZ**：没有独立的 `RTZ` 加一项，因此只选未因舍入上调的候选，向零截断。
- **RDN**：源码分别用 `(RDN & fp_a_sign)` 和 `(RDN & !fp_a_sign)` 适配 CS2/CS3 的候选方向；语义上只有负结果且低位不精确时向负无穷调整。
- **RUP**：源码分别用 `!fp_a_sign` 和 `fp_a_sign` 适配 CS2/CS3；语义上只有正结果且低位不精确时向正无穷调整。
- **RMM**：被丢弃的 guard 类低位为 1 时选向绝对值较大的候选；恰好中点时因此远离零。

当舍入判定要求上调时，`sel_CS4` 选择已经包含相应调整的候选；否则由 `sel_CS2`/`sel_CS3` 等选择未上调候选。源码随后由 `NX` 逻辑单独记录是否有非零信息被丢弃。因此，“加不加一”和“是否不精确”是两个不同判断：例如 RTZ 可以不加一但仍然 `NX=1`。位置约为 `VectorFloatAdder.scala:1038–1048、1120–1158`。

#### 用本例核对模式判断

`1.0f + 0.1f` 的精确和位于 `0x3f8ccccc` 与 `0x3f8ccccd` 之间，并更靠近上方值。按上述判定，RNE 与 RMM 选上方候选；正数的 RUP 也向上调整；RTZ 与 RDN 不上调，选下方候选。因此结果仍是：RNE/RUP/RMM=`0x3f8ccccd`，RTZ/RDN=`0x3f8ccccc`。这与第 2 节的理论表一致；当前提供的反汇编只实际运行默认 RNE，其他模式是依据源码和舍入规则推导，需写入 `frm` 并重新运行才能在本程序波形或内存结果中逐项观测。

## 6. 从数据结果验证模式确实生效

- 检查生成的反汇编：五种模式测试时，每轮的 `csrw frm, ...` 必须在对应 `vfadd.vv` 之前；当前基线反汇编没有这条写入，因此当前仅验证 RNE。
- 按十六进制比较每 lane 的 FP32 位型：预期 RTZ/RDN 为 `0x3f8ccccc`，RNE/RUP/RMM 为 `0x3f8ccccd`。
- 每轮在运算前清 `fflags`，再读取 `fflags.NX`；当前数据为不精确加法，NX 应为 1。
- 若需要证明 RNE 和 RMM 在 tie 时的差异，应另选恰好位于两个相邻 FP32 候选值正中间的输入。本例不是 tie，RNE 与 RMM 都选最近的上方数。
- 对带负号的输入，RDN/RUP 的数值方向与正数相反；若对照负数样本，应按“向正/负无穷”解释，不能简单照搬正数的上下位型分组。

## 7. 结论

当前最新程序的实际输入是四组 `1.0f + 0.1f`，不是遗留文档中的 `1.0f + 2.0f`。它将结果舍入到 FP32，且输入和不精确。当前反汇编显示启动阶段清零 `fcsr`，`main` 中没有重新写 `frm`，所以现有二进制只按默认 RNE 运行，预期每 lane 为 `0x3f8ccccd` 并置 NX。要比较五种模式，应在每条 `vfadd.vv` 前将 `frm` 依次设为 `0、1、2、3、4`；本例预期 RTZ/RDN 得 `0x3f8ccccc`，RNE/RUP/RMM 得 `0x3f8ccccd`。
