# 标量浮点乘法：舍入模式的配置与结果差异

　　同一条 `fmul.s`、同一对操作数，只把舍入模式从 RNE 换成 RUP，结果可能相差一个最低位；到了指数边界，还会从最大有限数变成无穷。差异只出现在精确积无法用 24 位有效数表示的时候。本文用 11 组输入，把五种舍入模式各自起作用的条件逐一拆开，再顺着香山的源码看这些条件怎样变成保留位末位的加一、无穷与最大有限数之间的选择，以及 `fflags` 中的不精确与下溢。

　　舍入本身的推导另有专文。[《标量浮点乘法的算法和代码分析》](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/backend/FloatMul.md)从 48 位积讲到 guard、round、sticky，再到五种模式与上下溢，本文只在需要处重述结论，不再展开。NaN、无穷、零这些不参与数值通路的操作数，已在[《标量浮点乘法特殊操作数的生命周期》](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/5-xiangshan-scenarios-analysis/instructions-lifecycle/scalar-float-mul/fmul-special-operand-lifecycle.md)中按指令生命周期讨论；本文的操作数一律取有限的非零数，以便把注意力集中在舍入与指数越界上。

## 1. 指令、程序与反汇编

### 1.1 `fmul.s` 的舍入字段

　　`fmul.s` 属于 OP-FP 主操作码 `1010011`，`funct5` 为 `00010`，格式位 `fmt` 为 `00`，三者拼成 `funct7 = 0001000`。剩下的 `funct3` 不与运算种类有关，它整段留给舍入模式，记作 `rm`。约定与编码如下：

| `rm` | 助记符 | 含义 |
| ---: | --- | --- |
| `000` | RNE | 就近舍入，正中点取偶 |
| `001` | RTZ | 向零舍入 |
| `010` | RDN | 向 $-\infty$ 舍入 |
| `011` | RUP | 向 $+\infty$ 舍入 |
| `100` | RMM | 就近舍入，正中点取幅值大者 |
| `111` | DYN | 不使用静态编码，改读 `fcsr.frm` |

　　前五种把模式直接写在指令里，同一段代码里相邻的两条乘法可以各带各的模式，不必先写 CSR。`111` 是一个间接层：指令里只声明“动态”，真正的三位模式放在 `fcsr` 的 `frm` 域中。汇编器在省略舍入模式时默认写成 `111`，所以源码里的 `fmul.s fa2, fa0, fa1` 与显式的 `fmul.s fa2, fa0, fa1, dyn` 是同一条指令。

### 1.2 测试程序

　　程序（`source-code/main.c`）取 11 组输入，每组先在五种静态模式下各乘一次，再把模式写进 `frm`、用动态编码复算一遍。这样一次运行既覆盖了静态字段的五种取值，也验证了动态通路确实读到了同一个模式。每一轮只做一次乘法，乘前清 `fflags`，乘后立即读回，因此读到的标志只属于这一次运算，不会混入此前的积累。

　　单次乘法的骨架如下，`RND` 由宏参数拼成汇编助记符后缀，五个包装函数分别展开成 `rne`、`rtz`、`rdn`、`rup`、`rmm`：

```c
#define CASE(RND, idx, a, b, col)                                           \
  do {                                                                      \
    uint32_t r_, f_;                                                        \
    asm volatile(                                                           \
        "csrw    fflags, zero\n\t"                                          \
        "fmv.w.x fa0, %2\n\t"                                               \
        "fmv.w.x fa1, %3\n\t"                                               \
        "fmul.s  fa2, fa0, fa1, " #RND "\n\t"                               \
        "fmv.x.w %0, fa2\n\t"                                               \
        "csrr    %1, fflags"                                                \
        : "=r"(r_), "=r"(f_)                                                \
        : "r"((uint32_t)(a)), "r"((uint32_t)(b))                            \
        : "fa0", "fa1", "fa2");                                             \
    res_static[(idx)][(col)] = r_;                                          \
    flg_static[(idx)][(col)] = f_;                                          \
  } while (0)
```

　　操作数以 `uint32_t` 的位型传入，经 `fmv.w.x` 原样搬进浮点寄存器。若改成 C 的 `float` 参数，编译器会在调用点做一次整数到浮点的转换，位型与数值的对应关系就不再由测试控制，次正规数与最小指数附近的输入尤其容易被改掉。动态通路另写一段汇编：`csrw frm, %4` 先写模式，再执行不带模式后缀的 `fmul.s`，此时汇编器给出的是 `funct3 = 111`。

　　11 组输入覆盖的判定点如下表。第三、四列是操作数的位型及其对应的真值，第五列是精确积的数学值。

| 编号 | 名称 | `a` | `b` | 精确积 | 考察点 |
| ---: | --- | --- | --- | --- | --- |
| 0 | `exact` | `0x3FC00000` = $1.5$ | `0x40000000` = $2$ | $3$ | 精确，五种模式应当一致 |
| 1 | `below_mid` | `0x3F800001` = $1+2^{-23}$ | `0x3F800001` = $1+2^{-23}$ | $1+2^{-22}+2^{-46}$ | 低于中点，只有 RUP 进位 |
| 2 | `tie_even` | `0x3FC00000` = $1.5$ | `0x3F800003` = $1+3\times 2^{-23}$ | $1.5+2^{-21}+2^{-24}$ | 正中点，下候选末位为偶 |
| 3 | `tie_odd` | `0x3FC00000` = $1.5$ | `0x3F800001` = $1+2^{-23}$ | $1.5+2^{-23}+2^{-24}$ | 正中点，下候选末位为奇 |
| 4 | `neg_tie_even` | `0xBFC00000` = $-1.5$ | `0x3F800003` = $1+3\times 2^{-23}$ | $-(1.5+2^{-21}+2^{-24})$ | 负中点，RDN 与 RUP 对调 |
| 5 | `ovf_pos` | `0x7F7FFFFF` = $(2-2^{-23})\times 2^{127}$ | `0x40000000` = $2$ | $(2-2^{-23})\times 2^{128}$ | 正上溢，选无穷还是最大有限数 |
| 6 | `ovf_neg` | `0xFF7FFFFF` = $-(2-2^{-23})\times 2^{127}$ | `0x40000000` = $2$ | $-(2-2^{-23})\times 2^{128}$ | 负上溢，方向与正侧相反 |
| 7 | `uf_tie_pos` | `0x00000001` = $2^{-149}$ | `0x3F000000` = $0.5$ | $2^{-150}$ | 下溢中点，零与最小次正规数等距 |
| 8 | `uf_below_pos` | `0x00000001` = $2^{-149}$ | `0x3E800000` = $0.25$ | $2^{-151}$ | 下溢且低于中点 |
| 9 | `uf_tie_neg` | `0x80000001` = $-2^{-149}$ | `0x3F000000` = $0.5$ | $-2^{-150}$ | 负的下溢中点 |
| 10 | `sub_exact` | `0x00000002` = $2^{-148}$ | `0x3F000000` = $0.5$ | $2^{-149}$ | 次正规数区间内的精确结果 |

　　0 号是基准：精确结果没有舍弃信息，五种模式理应给出同一个位型。1 号把乘积放在下候选之上、中点之下，用来区分“看到非零舍入信息就加一”的 RUP 与其余四种。2、3 号都在中点上，区别只在下候选的末位：2 号的下候选是偶数，RNE 留下；3 号的下候选是奇数，RNE 进位，于是与 RMM 走到一起。两组合起来才能说明 RNE 在中点并不总向一个方向，它看的是候选的奇偶。4 号把同一组中点取负，RDN 与 RUP 的作用随之对调，而 RNE、RMM 的选择只与幅值有关，不随符号改变。

　　5、6 号把指数推到上界之外。上溢时五种模式分成的不是“进位与截断”，而是“给无穷还是给同号最大有限数”，并且正负两侧的分配相反，必须成对来看。7、8、9 号落在次正规数的最低端：7 号是零与最小次正规数的正中点，8 号低于中点，9 号是 7 号的负数版本。10 号的结果虽小，却能精确表示，用来检验“小”并不等于“下溢”。

### 1.3 反汇编

　　程序把五个静态分支展开在 `main` 的循环体里，每个分支只出现一次，循环换的是操作数。`funct3` 的五种取值在机器码上只差最低的几位：

```text
80000198:  10b50653   fmul.s  fa2,fa0,fa1,rne
8000022a:  10b51653   fmul.s  fa2,fa0,fa1,rtz
800002bc:  10b52653   fmul.s  fa2,fa0,fa1,rdn
8000034e:  10b53653   fmul.s  fa2,fa0,fa1,rup
800003e0:  10b54653   fmul.s  fa2,fa0,fa1,rmm
800004b0:  002b9073   fsrm    s7
800004c0:  10b57653   fmul.s  fa2,fa0,fa1
```

　　前五条只有 `funct3` 不同，从 `000` 逐级加到 `100`，每换一档机器码加 `0x1000`。`0x10b57653` 的 `funct3` 是 `111`，即动态编码；它前面那条 `fsrm s7` 是 `csrw frm, s7` 的伪指令，把循环变量 `m` 写进 `frm`。

## 2. 舍入的判据

### 2.1 精确积、截断点与三个位

　　两个单精度规格化数的有效数都是 24 位，乘积是 48 位定点整数。把小数点移到前导 1 之后，目标格式只保留小数点后的 23 位，其余位必须舍弃。舍弃的位置一确定，真值就落在两个相邻的可表示数之间；这两个数到下一位的间距就是该量级的一个 ULP，真值距下候选超过、等于还是不足半个 ULP，决定了五种模式各自的选择。

　　判断无需保留全部被舍弃的位。保留部分的最低位记作 LSB，紧邻截断点之下的一位是 guard，再下一位是 round，更低位只要有一个 1 就归约为 sticky。三位的组合把“与中点的关系”压缩成三种：guard 为 0 表示低于中点；guard 为 1 而 round、sticky 全为 0 表示恰好居中；guard 为 1 且 round、sticky 有任一为 1 表示高于中点。被舍弃的位全为 0 时真值本身可表示，五种模式都不进位，这一情形不进入下面的判据。

### 2.2 五种模式

　　把“对保留部分加一”记作一次进位，五种模式的判据如下表。表中的符号位指结果的符号位，不是操作数的符号位；RDN 与 RUP 的“向上”“向下”按数值轴理解，对负数加一意味着幅值变大、数值更负。

| 模式 | 对保留部分加一的条件 | 正中点时 |
| --- | --- | --- |
| RNE | `G && (R \|\| S \|\| LSB)` | 下候选末位为奇才进位 |
| RMM | `G` | 一律进位 |
| RTZ | 永不加一 | 截断 |
| RDN | `sign && (G \|\| R \|\| S)` | 负结果进位，正结果截断 |
| RUP | `!sign && (G \|\| R \|\| S)` | 正结果进位，负结果截断 |

　　RNE 与 RMM 都取较近的候选，分歧只在正中点：RNE 看下候选的奇偶，RMM 不看奇偶，一律取幅值大的上候选。两者在“高于中点”时都进位，在“低于中点”时都截断，所以一组恰好居中的数据才能把它们分开。RTZ 不区分符号，任何非零舍弃信息都截断；RDN 与 RUP 则把符号纳入判据，正负两侧恰成镜像。

　　这张表与香山 `FloatFMA` 中的 `round_add1_f32` 逐项对应，第 5 节会把这层对应展开。需要注意，判据只回答“保留下来的那一位加不加一”，不回答“结果是多少”；加一可能令有效数溢出到 24 位之外，此时还要把小数点左移一位、指数加一，这一步与舍入是两件事。

### 2.3 上溢与下溢

　　指数越界时，模式影响的不是最低位，而是交付哪一个数。上溢的判据只看舍入之后的指数：单精度的指数上界是 127，舍入后一旦超过 127，阶码域就落到全 1，而全 1 已经留给无穷与 NaN。此时五种模式的选择是

| 模式 | 正上溢 | 负上溢 |
| --- | --- | --- |
| RNE、RMM | $+\infty$ | $-\infty$ |
| RTZ | $+MaxFinite$ | $-MaxFinite$ |
| RDN | $+MaxFinite$ | $-\infty$ |
| RUP | $+\infty$ | $-MaxFinite$ |

　　就近的两种一律给同号无穷；定向的三种看舍入方向的无穷端是否与结果同号，同号给无穷，异号给同号最大有限数 $MaxFinite=(2-2^{-23})\times 2^{127}$。标志却不随模式改变，上溢与不精确同时置起，因为无论交付无穷还是最大有限数，都与真值不同。

　　下溢由两个条件合成：舍入之后的量级仍小于最小规格化数 $2^{-126}$，并且被舍弃的位非零。第一条里的“舍入之后”是 RISC-V 的选择，称为 tininess after rounding；同样很小但不精确的真值，如果舍入把它顶到了 $2^{-126}$，就不算小，下溢不置。下溢结果的格点不再是随指数浮动的 ULP，而是固定的 $2^{-149}$：阶码域取 0，尾数域直接承载从 $2^{-149}$ 往上的各位。

　　真值落在半个最低位附近时，两个候选只剩零与最小次正规数 $2^{-149}$。恰好等于半个最低位时，RNE 取偶而选零，RMM 取幅值大者而选 $2^{-149}$，RDN 在负结果选 $-2^{-149}$，RUP 在正结果选 $+2^{-149}$，其余组合给带符号的零。低于半个最低位时 guard 为 0，就近的两种都截断到零，只有 RUP 对正结果、RDN 对负结果仍进位。这个差别正是 7 号与 8 号两组输入要分开观察的。

## 3. 五种模式下的结果

### 3.1 完整结果表

　　下表每一格是单次乘法写回的目标寄存器位型，`fflags` 一列按 `NV DZ OF UF NX` 从高到低给出五位。表中只列一次乘法产生的标志，测试在每次乘法前已把 `fflags` 清零。

| 用例 | RNE | RTZ | RDN | RUP | RMM | `fflags` |
| --- | --- | --- | --- | --- | --- | --- |
| `exact` | `0x40400000` | `0x40400000` | `0x40400000` | `0x40400000` | `0x40400000` | `0x00` |
| `below_mid` | `0x3F800002` | `0x3F800002` | `0x3F800002` | `0x3F800003` | `0x3F800002` | `0x01` |
| `tie_even` | `0x3FC00004` | `0x3FC00004` | `0x3FC00004` | `0x3FC00005` | `0x3FC00005` | `0x01` |
| `tie_odd` | `0x3FC00002` | `0x3FC00001` | `0x3FC00001` | `0x3FC00002` | `0x3FC00002` | `0x01` |
| `neg_tie_even` | `0xBFC00004` | `0xBFC00004` | `0xBFC00005` | `0xBFC00004` | `0xBFC00005` | `0x01` |
| `ovf_pos` | `0x7F800000` | `0x7F7FFFFF` | `0x7F7FFFFF` | `0x7F800000` | `0x7F800000` | `0x05` |
| `ovf_neg` | `0xFF800000` | `0xFF7FFFFF` | `0xFF800000` | `0xFF7FFFFF` | `0xFF800000` | `0x05` |
| `uf_tie_pos` | `0x00000000` | `0x00000000` | `0x00000000` | `0x00000001` | `0x00000001` | `0x03` |
| `uf_below_pos` | `0x00000000` | `0x00000000` | `0x00000000` | `0x00000001` | `0x00000000` | `0x03` |
| `uf_tie_neg` | `0x80000000` | `0x80000000` | `0x80000001` | `0x80000000` | `0x80000001` | `0x03` |
| `sub_exact` | `0x00000001` | `0x00000001` | `0x00000001` | `0x00000001` | `0x00000001` | `0x00` |

　　`fflags` 的五位在任何一组里都没有 `NV` 或 `DZ`：操作数都是有限非零数，乘法不会产生无效操作，也不会除零。剩下的 `OF`、`UF`、`NX` 三位只有三种取值：精确为零，仅不精确为 `0x01`，下溢连同不精确为 `0x03`，上溢连同不精确为 `0x05`。标志与模式的关系要分两层看：上溢组里五种模式的结果分成两类，标志却同为 `0x05`；下溢组里各模式的结果不同，标志同为 `0x03`；精确组里结果相同，标志也相同。

　　把 `source-code/` 里的镜像放进香山的 RTL 仿真（昆明湖 V2 配置，提交编号 `e7bab53e66`）跑一遍，程序按上表顺序逐组输出。每行先是五种静态模式写回的目标寄存器位型，随后是五组 `fflags`，都按 RNE、RTZ、RDN、RUP、RMM 的顺序排列：

```text
OBS exact          rne=40400000 rtz=40400000 rdn=40400000 rup=40400000 rmm=40400000 flags=00/00/00/00/00
OBS below_mid      rne=3f800002 rtz=3f800002 rdn=3f800002 rup=3f800003 rmm=3f800002 flags=01/01/01/01/01
OBS tie_even       rne=3fc00004 rtz=3fc00004 rdn=3fc00004 rup=3fc00005 rmm=3fc00005 flags=01/01/01/01/01
OBS tie_odd        rne=3fc00002 rtz=3fc00001 rdn=3fc00001 rup=3fc00002 rmm=3fc00002 flags=01/01/01/01/01
OBS neg_tie_even   rne=bfc00004 rtz=bfc00004 rdn=bfc00005 rup=bfc00004 rmm=bfc00005 flags=01/01/01/01/01
OBS ovf_pos        rne=7f800000 rtz=7f7fffff rdn=7f7fffff rup=7f800000 rmm=7f800000 flags=05/05/05/05/05
OBS ovf_neg        rne=ff800000 rtz=ff7fffff rdn=ff800000 rup=ff7fffff rmm=ff800000 flags=05/05/05/05/05
OBS uf_tie_pos     rne=00000000 rtz=00000000 rdn=00000000 rup=00000001 rmm=00000001 flags=03/03/03/03/03
OBS uf_below_pos   rne=00000000 rtz=00000000 rdn=00000000 rup=00000001 rmm=00000000 flags=03/03/03/03/03
OBS uf_tie_neg     rne=80000000 rtz=80000000 rdn=80000001 rup=80000000 rmm=80000001 flags=03/03/03/03/03
OBS sub_exact      rne=00000001 rtz=00000001 rdn=00000001 rup=00000001 rmm=00000001 flags=00/00/00/00/00
FMUL_ROUND_MODE_SUMMARY checks=220 failures=0
HIT GOOD TRAP at pc = 0x8000059a
```

　　位型按 `%08x` 打印，与上表的 `0x` 前缀只是格式差别。每一行的五个结果与五组标志都与上表逐项相同；动态 `frm` 通路不单独打印，它的 55 次乘法和 55 组标志并入最后的汇总，220 项检查全部通过。静态字段与动态 CSR 最后汇入同一个 `rm` 信号，所以两套写法给出同一张表。

### 3.2 精确结果与低于中点

　　`exact` 的两个操作数是 1.5 与 2.0，精确积为 3.0，位型 `0x40400000`。被舍弃的位全为 0，五种模式都不进位，`NX` 不置。`sub_exact` 的精确积是 $2^{-149}$，正是最小次正规数的权值，虽然落在次正规数区间，却不需要舍弃任何非零位，所以五种模式同样一致，`UF` 与 `NX` 都不置。这两组一起说明：结果很小并不必然下溢，下溢的标志要求“小”与“不精确”同时成立。

　　`below_mid` 的两个操作数都是 $1+2^{-23}$，平方后是 $1+2^{-22}+2^{-46}$。截断到 23 位尾数，保留部分是 $1+2^{-22}$，位型 `0x3F800002`；余下的 $2^{-46}$ 远小于半个最低位 $2^{-24}$，guard 与 round 都是 0，只有 sticky 为 1。RNE 与 RMM 的判据都以 guard 开头，guard 为 0 就截断；RTZ、RDN 对正结果也截断；只有 RUP 看到“非零舍弃信息”且结果为正，才进位到 `0x3F800003`。这一组把 RUP 与其余四种分开，也说明 RMM 并不是“只要不精确就向远处走”——它在低于中点时同样截断。

### 3.3 两个中点

　　`tie_even` 的精确积是 $1.5+2^{-21}+2^{-24}$，恰好落在 `0x3FC00004` 与 `0x3FC00005` 的正中。下候选的尾数是 `0x400004`，末位为 0；上候选的末位为 1。RNE 在中点取偶，保留下候选；RMM 在中点取幅值大的上候选；RUP 对正结果也取上候选；RTZ 与 RDN 对正结果都截断，保留下候选。于是五种模式分成 `0x3FC00004` 与 `0x3FC00005` 两组，RNE 单独站在下候选一侧，这正是它与 RMM 的唯一分歧。

　　`tie_odd` 把乘数换成 $1+2^{-23}$，精确积是 $1.5+2^{-23}+2^{-24}$，中点两侧变成 `0x3FC00001` 与 `0x3FC00002`，下候选末位为 1。RNE 取偶，改为进位，与 RMM、RUP 一起得到 `0x3FC00002`；RTZ 与 RDN 仍截断到 `0x3FC00001`。两组中点对照，RNE 的行为从“截断”变成“进位”，而 RMM 两组都进位：奇偶才是 RNE 的判据，中点本身不是。

### 3.4 负中点与方向

　　`neg_tie_even` 是 `tie_even` 的负数版本，精确积为 $-(1.5+2^{-21}+2^{-24})$，位型在 `0xBFC00004` 与 `0xBFC00005` 之间。RNE 与 RMM 只看幅值，选择与正版本相同，只是符号位变成 1：RNE 取 `0xBFC00004`，RMM 取 `0xBFC00005`。RTZ 向零，取幅值小的 `0xBFC00004`；RUP 向 $+\infty$，对负数也取幅值小的 `0xBFC00004`；RDN 向 $-\infty$，取更负的 `0xBFC00005`。

　　这一组把 RTZ 与 RDN 分开，也把 RUP 与 RMM 分开。正数一侧，RTZ 与 RDN 常常给出同一个结果；换到负数，RDN 向 $-\infty$，改取幅值大者，两者才分开。RUP 对负结果取幅值小者，RMM 在中点取幅值大者，负中点正是它们的另一处分歧。方向的判据里带符号位，符号一变，哪一侧“进位”就随之对调，模式的名字（向零、向负无穷、向正无穷）比“加一/不加一”更接近它的本义。

### 3.5 上溢

　　`ovf_pos` 取最大有限数与 2.0 相乘，精确积是 $(2-2^{-23})\times 2^{128}$，规格化后的指数为 128，超过单精度的上界 127。RNE 与 RMM 取同号无穷 `0x7F800000`；RTZ 与 RDN 对正结果取最大有限数 `0x7F7FFFFF`；RUP 向 $+\infty$，也取无穷。`ovf_neg` 是同一对操作数取负，五种模式的选择完全镜像：RNE、RMM、RDN 取负无穷 `0xFF800000`，RTZ 与 RUP 取负的最大有限数 `0xFF7FFFFF`。两侧合起来，只有 RTZ 在两个符号下都给最大有限数；RDN 与 RUP 各在一个符号下给无穷，另一个符号下给最大有限数。

　　两组的 `fflags` 都是 `0x05`，即 `OF` 与 `NX`。给无穷还是给最大有限数，交付的数都与真值不同；即使真值恰好是 $2^{128}$ 这样整齐的数，上溢本身已经说明它超出了格式的指数范围，`NX` 照置。上溢与下溢不会同时出现，指数要么越过上界，要么落进次正规数区间。

### 3.6 下溢与次正规数

　　`uf_tie_pos` 是最小次正规数 $2^{-149}$ 乘以 0.5，精确积为 $2^{-150}$，正好是零与 $2^{-149}$ 的中点。零的尾数为 0，是偶候选，RNE 因而取 $+0$；RMM 在中点取幅值大的 $2^{-149}$，RUP 对正结果也取 $2^{-149}$；RTZ 与 RDN 取 $+0$。`uf_tie_neg` 取负，RNE 仍取偶，得到 $-0$（位型 `0x80000000`）；RMM 取 $-2^{-149}$；RDN 向 $-\infty$，也取 $-2^{-149}$；RTZ 与 RUP 取 $-0$。两组的 `fflags` 都是 `0x03`，即 `UF` 与 `NX`：结果虽然是零或最小次正规数，被舍弃的部分不为零，量级也没有达到 $2^{-126}$。

　　`uf_below_pos` 把乘数换成 0.25，精确积为 $2^{-151}$，是最低位 $2^{-149}$ 的四分之一。截断后 guard 为 0，RNE、RMM、RTZ、RDN 都取 $+0$，只有 RUP 进位到 `0x00000001`。与 `uf_tie_pos` 合看，RMM 在“恰好一半”时取 $2^{-149}$，在“不足一半”时取零；它虽然只看 guard，但 guard 恰好把这两种情形分在了两边。

　　下溢标志的“小”按舍入之后判，这一点在边界上会改变标志。[《标量浮点乘法的算法和代码分析》](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/backend/FloatMul.md)第 7 节的注给出一组输入：真值恰好落在最大次正规数 `0x007FFFFF` 与最小规格化数 `0x00800000` 的正中。RNE 取偶、RMM 与 RUP 取大，三者都进位到 `0x00800000`，舍入之后已经不小，只置 `NX`；RTZ 与 RDN 截断到 `0x007FFFFF`，仍是次正规数，置 `UF` 与 `NX`。同一对操作数只换模式，标志就从 `0x01` 变成 `0x03`。这组输入不在上面的 11 组之列，因为它跨越的是规格化与次正规的分界，与其余各组考察的“最低位选哪一个”不同，但它是“模式影响标志”的最直接例子。

### 3.7 静态字段与动态 `frm`

　　程序对每一组输入都用两种方式各算一遍：静态方式把模式写进指令的 `rm` 字段，动态方式先 `csrw frm` 再执行 `funct3 = 111` 的乘法。两种方式写回的结果与 `fflags` 应当逐项相同，程序在运行时把每一格与期望表比对，任何一格不符都会打印 `FAIL` 并增加失败计数。这说明动态通路并不是另一套舍入逻辑：它只是把模式从指令字段换成了 CSR 字段，进入执行单元后仍汇入同一个 `rm` 信号。

## 4. 配置舍入模式

### 4.1 `fcsr` 与 `frm`

　　`fcsr` 是一个 32 位 CSR，低 8 位分成两段：`frm` 在 `[7:5]`，`fflags` 在 `[4:0]`，五位从高到低是 `NV`、`DZ`、`OF`、`UF`、`NX`。`frm` 与 `fflags` 各自还有独立的 CSR 地址，`csrw frm, rs` 只改模式，`csrw fflags, rs` 只改标志；`csrw fcsr, rs` 一次写整个 `fcsr`，模式与标志同时被覆盖。测试里先 `csrw frm` 再 `csrw fflags, zero`，两步分开，避免把模式写回默认值。

　　`fflags` 的五位是粘性的：一次运算置起某一位后，后续运算不会把它清掉，只有软件显式写入才能清除。测试在每次乘法前清零，读回的就是这一次运算的结果；真实程序若连续做多次浮点运算再读，看到的是这段时间内所有运算的并集。第 3 节表中“一次乘法的标志”与“累计标志”因此不是一回事，读代码时不能把两者混用。

### 4.2 静态与动态的取舍

　　静态模式把三位模式放进每条指令的 `funct3`，代价是每条浮点指令都要自带一份模式，`funct3` 不再能承担别的区分，好处是模式切换不经过 CSR。动态模式把三位集中到 `frm`，指令里只留一个“读 `frm`”的编码，代码可以只在一处设置全局模式，代价是每次切换都要写 CSR。

　　RISC-V 手册在讲 `frm` 时明确提到，典型实现会把写动态舍入模式 CSR 的操作序列化，静态模式正是为那些需要在不同舍入模式之间频繁切换的专门运算准备的。香山属于这类实现：所有 CSR 指令在译码时都带 `blockBackward` 标记（`DecodeUnit.scala:212-218`），带此标记的指令进入 ROB 后，后续指令要等它离开 ROB、且 ROB 清空才允许继续入队（`Rob.scala:192`、`Rob.scala:459`）。因此一段“写 `frm`、再做乘法”的代码，在两条指令之间有一次实质性的串行化；若把模式编码进每条乘法，这段串行化就不存在。模式切换越频繁，静态字段的收益越大；若整个程序只用一种模式，动态方式只在初始化时写一次，代价可以忽略。

　　动态方式还有一个正确性问题：后随的乘法必须读到新写的 `frm`。香山的 `blockBackward` 恰好保证了这一点——CSR 指令未离开 ROB 之前，后面的指令根本不会派发，乘法不可能提前用旧模式执行。若实现只把 `frm` 当作普通旁路信号而不做这种排序，就需要另设机制保证 CSR 写与浮点读的先后；这是动态模式在微架构上比静态模式多出来的一笔账。

### 4.3 保留编码

　　`frm` 只有 `000` 到 `100` 五个有效值，`101`、`110`、`111` 是保留编码。静态 `rm` 的 `101`、`110` 也是保留编码；`111` 不是一种模式，而是“去读 `frm`”的开关，因此它是否合法取决于 `frm` 当时的值。香山的译码把这两层分开检查：静态字段为 `101` 或 `110` 直接判非法；为 `111` 时再看 `frm` 是否保留（`DecodeUnit.scala:911`）。CSR 一侧则把“`frm` 落在保留编码”汇总成 `illegalInst.frm`（`NewCSR.scala:990`、`NewCSR.scala:1490`）。软件不应把 `111` 写进 `frm`，它只属于指令字段。

## 5. 香山中的模式传递与舍入实现

### 5.1 从译码到执行单元

　　译码阶段，`FPDecoder` 把指令中的三位直接取出，写入控制信号：

```scala
ctrl.rm := inst.RM
```

　　这一行在 `backend/decode/FPDecoder.scala:342`。控制信号随微指令一路带到执行单元；`fmul.s` 的 `FuType` 在 `backend/decode/VecDecoder.scala:548` 被定为 `FuType.fmac`，由浮点乘加单元承接。`FmacCfg` 声明 `needSrcFrm = true`（`backend/fu/FuConfig.scala:715-727`），意思是这个单元除了操作数之外还要一份当前的 `frm`；配置里的 `latency = CertainLatency(3)`、`writeFflags = true` 也在这里定下。

　　`frm` 从 CSR 到执行单元的路径是：`Backend` 把 `csrio.fpu.frm` 接到浮点执行块（`backend/Backend.scala:661-664`），`ExuBlock` 用 `RegNext` 打一拍（`backend/exu/ExuBlock.scala:43`），`ExeUnit` 再分发给各个功能单元（`backend/exu/ExeUnit.scala:400`）。打这一拍是为了时序，`frm` 在一条乘法进入执行单元之前就已经稳定，不会与乘法本身的流水线竞争。

　　真正决定用哪一个模式的是 `FpPipedFuncUnit` 里的三行：

```scala
protected val frm     = io.frm.getOrElse(0.U(3.W))
protected val instRm  = inCtrl.fpu.getOrElse(0.U.asTypeOf(new FPUCtrlSignals)).rm
protected val rm      = Mux(instRm =/= "b111".U, instRm, frm)
```

　　这段在 `backend/fu/fpu/FpPipedFuncUnit.scala:14-16`。静态字段不是 `111` 时，`instRm` 原样胜出；是 `111` 时，`Mux` 才转向 `frm`。动态与静态的分叉只在这一处，之后的单元看到的都是同一个三位 `rm`。`FMA` 包装层把它接到 `FloatFMA` 的 `round_mode` 端口（`backend/fu/wrapper/FMA.scala:36`）。

### 5.2 `FloatFMA` 中的舍入

　　`fmul` 在 `FloatFMA` 里走的仍是乘加通路，只是加数被强制为零：

```scala
val is_fmul   = io.op_code === FmaOpCode.fmul
...
val fp_c_f32  = Mux(is_fmul, 0.U(32.W), sign_inv(io.fp_c(31,0),fp_c_is_sign_inv))
```

　　`is_fmul` 在 `yunsuan/src/main/scala/yunsuan/fpu/FloatFMA.scala:51`，加数置零在 `FloatFMA.scala:89`。加数为零，乘加就退化成乘法：符号取两个操作数符号位的异或，有效数进入 Booth 编码与进位保留加法树，指数相加。

　　有效数相乘与规格化之后，舍入级要回答的只是“保留位加不加一”。五种模式在 `FloatFMA.scala:492-496` 被译成五个布尔信号，并随流水线打拍到第三级：

```scala
val RNE = io.round_mode === "b000".U
val RTZ = io.round_mode === "b001".U
val RDN = io.round_mode === "b010".U
val RUP = io.round_mode === "b011".U
val RMM = io.round_mode === "b100".U
```

　　G、R、S 三位在 `FloatFMA.scala:505`、`513-519` 从规格化后的结果中取出。乘加通路里，加法树可能给出负的补码结果，此时取出的位要按补码语义修正，`FloatFMA.scala:522` 与 `526` 分别对 round 与 guard 做这件事；对 `fmul`，加数为零，加法树求的是两个有效数的无符号积，这一修正不改变下面的判据。

　　加一的完整条件在 `FloatFMA.scala:547-552`：

```scala
round_add1_f32 := RNE_reg2 & (guard_f32 & (fraction_result_no_round_f32_reg2(0) | round_f32 | sticky_f32_reg2)) |
  RDN_reg2 & sign_result_temp_f32_reg2 & (guard_f32|round_f32|sticky_f32_reg2) |
  RUP_reg2 & !sign_result_temp_f32_reg2 & (guard_f32|round_f32|sticky_f32_reg2) |
  RMM_reg2 & guard_f32 |
  adder_is_negative_f32_reg2 & !guard_f32 & !round_f32 & !sticky_f32_reg2
```

　　前四项与第 2 节的表逐项对应：RNE 看 guard，并用 LSB 的或项处理中点取偶；RDN、RUP 把符号位纳入，正负两侧互为镜像；RMM 只看 guard。最后一项是乘加通路特有的补码修正：当加法树的结果为负、且被舍弃的位恰好为零时，取反后的有效数需要一次额外的进位。`fmul` 的加数为零，加法树求的是两个有效数的无符号积，这一项不会成立，所以对本文的乘法可以只看前四项。

　　加一若使 23 位尾数全 1 再加一，尾数溢出，需要把小数点左移一位、指数加一，`FloatFMA.scala:574` 的 `exponent_add_1_f32` 报的就是这件事。结果位的拼接与指数调整在 `FloatFMA.scala:584-620`，其中 `fraction_result_temp` 按 `round_add1` 选择原尾数还是加一后的尾数。

### 5.3 上溢、下溢与标志

　　指数越界的判定在 `FloatFMA.scala:597-603`。`exponent_overflow_f32` 把“指数加一后越过上界”汇总成上溢；`exponent_is_min_f32` 则判断有效数无论怎样左移都触不到隐含位，结果只能是次正规数或零。上溢时结果不再走常规拼接，而是在两个常量之间二选一（`FloatFMA.scala:663-666`）：

```scala
val result_overflow_up_f32   = Cat(sign_result_temp_f32_reg2, Fill(8,1.U), 0.U((24-1).W))
val result_overflow_down_f32 = Cat(sign_result_temp_f32_reg2, Fill(8-1,1.U), 0.U, Fill(24-1,1.U))
```

　　`up` 是同号无穷，`down` 是同号最大有限数。选择信号在 `FloatFMA.scala:799`：

```scala
val is_overflow_f32_down_reg2 = RTZ_reg2 | (RDN_reg2 & !sign_result_temp_f32_reg2.asBool) | (RUP_reg2 & sign_result_temp_f32_reg2.asBool)
```

　　逐项读：RTZ 一律取最大有限数；RDN 在正结果取最大有限数、在负结果取负无穷；RUP 在正结果取正无穷、在负结果取负最大有限数；两者都不成立时（RNE、RMM）取无穷。这与第 2.3 节的表完全一致。上溢分支同时把 `fflags` 置成 `"b00101"`（`FloatFMA.scala:810`），即 `OF` 与 `NX`，不区分模式。

　　不精确标志由三位直接或出（`FloatFMA.scala:630`）：

```scala
NX_f32 := guard_f32 | round_f32 | sticky_f32_reg2
```

　　下溢标志还要多两个条件（`FloatFMA.scala:633`）：

```scala
UF_f32 := NX_f32 & exponent_is_min_f32 & ( !exponent_add_1_f32 | !(guard_f32 & round_add1_uf_f32))
```

　　先要不精确，再要求舍入前的指数已经压到最小，最后排除“舍入恰好把次正规数顶回最小规格化数”的情形。后一个括号就是 tininess after rounding 在电路里的落点：若加一产生了尾数进位、把结果送进了 `0x00800000`，下溢不应再置。`round_add1_uf_f32` 另用一组位置更低的 guard、round、sticky，专门判断这次进位是否真的发生在下溢边界上。

　　结果选择是一条优先级链（`FloatFMA.scala:802-819`）：NaN 最高，其次无穷，其次上溢，其次零，最后才是常规结果。本文的 11 组输入只落在“上溢”与“常规”两个分支，NaN、无穷与操作数为零的分支由特殊操作数文档讨论。

### 5.4 标志的提交与累积

　　执行单元给出的 `fflags` 随写回信息进入 ROB，但真正写 CSR 发生在提交阶段。ROB 按提交的有效指令收集 `wflags`，把各条指令的标志或在一起（`backend/rob/Rob.scala:715-717`），再送给 CSR。CSR 一侧的 `Unprivileged` 把这一组标志与寄存器旧值逐位相或（`backend/fu/NewCSR/Unprivileged.scala:45-50`）：

```scala
when (robCommit.fflags.valid) {
  reg.NX := robCommit.fflags.bits(0) || reg.NX
  reg.UF := robCommit.fflags.bits(1) || reg.UF
  reg.OF := robCommit.fflags.bits(2) || reg.OF
  reg.DZ := robCommit.fflags.bits(3) || reg.DZ
  reg.NV := robCommit.fflags.bits(4) || reg.NV
}
```

　　五位各自粘住，只有软件写 `fflags` 才能清零。测试在乘法前 `csrw fflags, zero`，所以读到的就是这一条乘法的贡献；若不清零，读回值会是此前所有已提交浮点指令的并集。由于标志在提交阶段才写 CSR，被分支预测错误或异常冲刷掉的指令不会留下标志，这一点与体系结构要求的精确异常一致。

## 6. 与其他文档的分工

　　本文把 `fmul.s` 的操作数限制在有限非零数，围绕舍入模式回答三个问题：模式怎样写进指令或 CSR，五种模式在什么条件下给出不同的最低位或不同的溢出结果，这些条件在香山的 `FloatFMA` 中对应哪几行。算法层面的完整推导——48 位积怎样从 Booth 编码与进位保留加法树得到、guard/round/sticky 怎样从移位与掩码中提取、上下溢的判据为什么落在舍入之后——在[《标量浮点乘法的算法和代码分析》](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/backend/FloatMul.md)中展开。NaN、无穷、零与信号 NaN 的识别、规范 NaN 的生成、特殊操作数在流水线中的逐拍流动，则在[《标量浮点乘法特殊操作数的生命周期》](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/5-xiangshan-scenarios-analysis/instructions-lifecycle/scalar-float-mul/fmul-special-operand-lifecycle.md)与[《标量浮点乘法特殊操作数的执行过程》](https://github.com/OpenXiangShan/XiangShanLab/tree/master/xiangshan-course/docs/5-xiangshan-scenarios-analysis/instructions-lifecycle/scalar-float-mul/fmul-special-operand-execution-process.md)中讨论。

　　这些文档合起来，一条 `fmul.s` 的数值行为才算完整：特殊值先由操作数判定截走，有限数进入乘法通路，有效数相乘后按模式舍入，指数越界时再按模式选择无穷或最大有限数，最后把不精确、上溢、下溢写进 `fflags`。本文的 11 组输入是这条链路上舍入与越界两段的取样；它们不追求覆盖所有浮点组合，而是让每一种模式的判据至少在一组输入上单独显形。
