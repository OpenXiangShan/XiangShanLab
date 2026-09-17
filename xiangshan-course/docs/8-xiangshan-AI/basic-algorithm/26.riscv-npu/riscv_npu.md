# RISC-V 与 NPU 系统架构

## 1. 文字讲解

### 1.1 NPU 解决什么问题

本章把 NPU（Neural Processing Unit）定义为面向神经网络张量算子的专用执行单元。它重点处理卷积、矩阵乘法、全连接、激活和池化等规则计算。

NPU 不是某一种统一的标准架构。不同实现可能使用向量单元、MAC 阵列、脉动阵列或其他数据通路。本章沿用第 25 章的教学方案：

- RV32I 处理器负责程序控制。
- `custom-0` 自定义指令负责提交和等待 NPU 命令。
- 算子参数通过内存描述符传递。
- NPU 使用 INT8 输入与权重、INT32 部分和。
- 计算核心采用二维 PE/MAC 阵列。
- 主示例采用 Output Stationary 数据流。

本章讨论功能结构和数据流，不给出具体 RTL、总线时序、缓存一致性协议、面积、频率、功耗或性能结论。

### 1.2 RISC-V 与 NPU 怎样分工

RISC-V 控制端适合执行控制流和地址管理，NPU 执行端负责规则的张量循环：

```text
模型程序
   |
   v
RV32I 软件：准备张量、描述符和命令
   |
   v
custom-0：提交 NPU 命令 / 等待完成
   |
   v
NPU：搬运数据 -> 分块计算 -> 后处理 -> 写回
```

两者之间传递的是“命令和描述符”，而不是每一次乘加操作。以卷积为例，RV32I 软件只需提交一次卷积任务，卷积内部的通道循环、空间窗口循环和地址生成由 NPU 控制器完成。

本教学系统采用共享地址空间：描述符中的地址指向输入、权重、偏置和输出。真实系统还必须明确虚拟地址或物理地址、访问权限、缓存一致性、内存屏障和异常处理。本章代码使用 Python 字典模拟共享内存，不覆盖这些 SoC 集成问题。

### 1.3 NPU 的功能模块

一个简化的 RISC-V NPU 系统可以划分为以下模块：

```text
                 RV32I Core
        +--------------------------+
        | custom-0 decode / issue  |
        +-------------+------------+
                      |
                      v
        +--------------------------+
        | command queue / status   |
        | descriptor fetch/check   |
        +-------------+------------+
                      |
         +------------+------------+
         |                         |
         v                         v
+------------------+     +-------------------+
| Load/Store DMA   |     | address generator |
+--------+---------+     +---------+---------+
         |                         |
         v                         v
+------------------------------------------------+
| Global Buffer / Scratchpad                       |
| input tile | weight tile | output/partial sums   |
+------------------------+-----------------------+
                         |
                         v
               +-------------------+
               | 2-D PE / MAC array|
               | INT8 x INT8       |
               | INT32 accumulate  |
               +---------+---------+
                         |
                         v
               +-------------------+
               | post-processing   |
               | bias / requant    |
               | ReLU / pooling    |
               +---------+---------+
                         |
                         v
                    output buffer
```

各模块职责如下：

- **指令接口**：识别第 25 章定义的自定义指令，接收描述符地址并返回命令编号。
- **命令队列**：保存待执行任务。本教学方案按提交顺序执行有依赖的任务。
- **描述符单元**：读取并检查算子类型、张量地址、形状、步长和量化参数。
- **DMA 或 Load/Store 单元**：在共享内存和局部缓冲区之间传输张量块。
- **地址生成器**：产生卷积窗口、矩阵分块和池化窗口的访问地址。
- **Global Buffer / Scratchpad**：保存当前输入块、权重块和输出部分和。
- **PE 阵列**：并行执行乘法和累加。
- **Accumulator**：使用 INT32 保存尚未完成的输出部分和。
- **后处理单元**：完成偏置加法、重新量化、饱和裁剪和 ReLU。
- **池化单元**：执行最大池化等窗口归约。
- **状态单元**：记录完成标志与错误码，供 `NPU_WAIT` 或中断处理使用。

这里的 Global Buffer 是由软件或 NPU 显式管理的局部存储概念，不自动等同于 CPU Cache。

### 1.4 PE 与 MAC 阵列

PE（Processing Element）是阵列中的基本计算单元。最小 PE 可以包含：

- 一个或多个 INT8 乘法器；
- 一个 INT32 加法器；
- 保存部分和的本地寄存器；
- 输入、权重和部分和的转发通路；
- 有效位与边界控制。

单个输出元素通常需要多次乘加。例如矩阵乘法 $C=AB$ 中：

$$
C_{i,j}=\sum_{k=0}^{K-1}A_{i,k}B_{k,j}.
$$

二维阵列可以让不同 PE 同时计算不同的 $(i,j)$ 输出。矩阵尺寸大于阵列尺寸时，控制器把矩阵切成多个 tile，阵列分批完成。

阵列的“行数乘列数”只表示可同时容纳的 PE 位置。实际吞吐还受数据供给、形状边界、调度和实现时序影响，因此不能只根据 PE 数量推断系统性能。

### 1.5 什么是数据流

数据流（Dataflow）规定输入、权重和部分和在存储层级与 PE 阵列之间怎样移动，以及哪类数据尽量保持不动。

**Weight Stationary（WS）**

- 权重在 PE 本地保存一段时间；
- 激活流过阵列；
- 部分和需要在 PE 之间传递或在其他位置归约。

**Output Stationary（OS）**

- 一个输出 tile 的部分和留在 PE 本地累加；
- 输入和权重依次送入阵列；
- 完成全部 $K$ 维累加后才写出输出。

**Row Stationary（RS）**

- 以卷积行计算为基本映射单位；
- 同时利用卷积核行、输入特征行和部分和的局部复用；
- 需要更复杂的映射与控制。

“Stationary”不是数据永远不动，而是在一段计算期间尽量把某类数据保留在较近的存储位置。不存在脱离模型形状、缓冲区和实现约束后仍然普遍最优的数据流。

本章代码使用 Output Stationary：每个输出 tile 的 INT32 累加器在遍历全部 $K$ tile 期间保持不变。

### 1.6 为什么需要分块

完整张量通常不能同时放入 PE 阵列和局部缓冲区，因此 NPU 把计算划分为 tile：

```text
A: (M x K) -> A_tile: (T_M x T_K)
B: (K x N) -> B_tile: (T_K x T_N)
C: (M x N) -> C_tile: (T_M x T_N)
```

Output Stationary 的基本流程为：

1. 清零一个 $T_M\times T_N$ 的 INT32 累加 tile。
2. 读取一块 $A_{I,K}$ 和一块 $B_{K,J}$。
3. 执行 tile 矩阵乘法并更新本地累加器。
4. 沿完整 $K$ 维重复步骤 2 和 3。
5. 添加偏置并进行重新量化、ReLU等后处理。
6. 写回当前输出 tile。

tile 大小必须同时满足阵列形状和局部存储容量。边界 tile 小于完整 tile 时，需要使用有效位屏蔽无效 PE，而不能让填充值影响输出。

### 1.7 卷积怎样映射到 PE 阵列

卷积可以展开为矩阵乘法。设卷积输出共有 $M=N_bH_{\text{out}}W_{\text{out}}$ 个空间位置，每个卷积窗口包含 $K=C_{\text{in}}K_hK_w$ 个元素，输出通道数为 $O=C_{\text{out}}$：

```text
输入窗口矩阵 A: (M, K)
卷积核矩阵 B:   (K, O)
输出矩阵 C:     (M, O)

C = A @ B
```

显式构造输入窗口矩阵通常称为 im2col。它便于理解和调用矩阵乘法，但会复制重叠窗口中的输入元素。真实 NPU 可以由地址生成器直接产生卷积窗口，在局部缓冲区中复用输入，而不必把完整 im2col 矩阵写入内存。

### 1.8 LeNet 在阵列上的计算形状

沿用第 25 章的现代 LeNet，每个卷积或全连接可以表示成 GEMM：

| 层 | $M$ | $K$ | $O$ | GEMM 形状 |
|---|---:|---:|---:|---|
| Conv1 | $28\times28=784$ | $1\times5\times5=25$ | $6$ | $(784,25)@(25,6)$ |
| Conv2 | $10\times10=100$ | $6\times5\times5=150$ | $16$ | $(100,150)@(150,16)$ |
| FC1 | $1$ | $400$ | $120$ | $(1,400)@(400,120)$ |
| FC2 | $1$ | $120$ | $84$ | $(1,120)@(120,84)$ |
| FC3 | $1$ | $84$ | $10$ | $(1,84)@(84,10)$ |

卷积层的 $M$ 较大，可以沿输出空间位置分块；全连接层的 $M=1$，主要沿输出维和归约维分块。相同 PE 阵列可以执行这些 GEMM，但不同形状对应的 tile 使用情况不同。

最大池化不需要乘法，可交给独立池化单元或可编程后处理单元。ReLU和重新量化通常在输出写回前完成，以避免额外生成独立中间张量。

### 1.9 软件栈

完整链路不只有硬件：

```text
训练模型
   |
   v
模型导出 / 计算图
   |
   v
编译器：形状推导、量化、算子融合、卷积降级、tiling
   |
   v
运行时：分配张量、建立描述符、组织命令
   |
   v
驱动：地址检查、同步、异常和设备管理
   |
   v
RV32I custom-0 指令
   |
   v
NPU 命令队列与执行单元
```

编译器决定算子怎样拆分并生成描述符；运行时管理一次推理所需的实际地址和命令；驱动负责平台级资源与同步；NPU 按描述符执行。仅有 MAC 阵列而没有这些软件与控制环节，不能直接运行完整模型。

### 1.10 基本执行流程

1. RV32I 软件准备输入、权重、偏置和输出内存。
2. 编译器或运行时为算子选择 tile 参数。
3. 软件建立描述符，并通过自定义指令提交命令。
4. 描述符单元检查地址、形状和支持的算子属性。
5. DMA 把输入 tile 和权重 tile搬入 Global Buffer。
6. 地址生成器把数据送到 PE 阵列。
7. PE 阵列按 Output Stationary 方式累计输出 tile。
8. 后处理单元执行偏置、重新量化和激活。
9. 输出 tile 写回共享内存。
10. 所有 tile 完成后，状态单元更新命令状态。
11. RV32I 软件通过 `NPU_WAIT` 或中断获知结果可用。

描述符非法、地址越界、算子属性不受支持或算术范围违反约定时，真实系统应返回错误状态，而不是静默产生结果。

### 1.11 直观例子

代码使用一个 $2\times2$ PE 阵列执行缩小卷积。输入形状为 $(1,1,4,4)$，两个卷积核形状均为 $(1,2,2)$：

$$
W_0=
\begin{bmatrix}
1&1\\
1&1
\end{bmatrix},
\qquad
W_1=
\begin{bmatrix}
1&0\\
0&-1
\end{bmatrix}.
$$

偏置为：

$$
b=[1,-1].
$$

卷积先通过 im2col 转换为：

$$
A\in\mathbb Z^{9\times4},
\qquad
B\in\mathbb Z^{4\times2}.
$$

$2\times2$ 阵列每次最多保存一个 $2\times2$ 输出 tile 的部分和。输出经过偏置和 ReLU 后，第一个通道为：

$$
\begin{bmatrix}
15&19&23\\
31&35&39\\
47&51&55
\end{bmatrix},
$$

第二个通道的卷积结果为负数，经过 ReLU 后全部为零。

## 2. 公式讲解

### 2.1 PE 的乘加递推

对于矩阵乘法：

$$
C=AB,
\qquad
A\in\mathbb Z^{M\times K},
\qquad
B\in\mathbb Z^{K\times N},
$$

第 $(i,j)$ 个输出为：

$$
C_{i,j}
=
\sum_{k=0}^{K-1}A_{i,k}B_{k,j}.
$$

Output Stationary PE 使用递推形式：

$$
p_{i,j}^{(0)}=0,
$$

$$
p_{i,j}^{(k+1)}
=
p_{i,j}^{(k)}
+A_{i,k}B_{k,j}.
$$

遍历完整归约维后：

$$
C_{i,j}=p_{i,j}^{(K)}.
$$

$A_{i,k}$ 和 $B_{k,j}$ 为 INT8，$p_{i,j}$ 使用 INT32 语义保存部分和。

### 2.2 分块矩阵乘法

将矩阵沿三个维度分块。对于输出 tile $(I,J)$：

$$
C_{I,J}
=
\sum_{R}
A_{I,R}B_{R,J}.
$$

其中：

$$
A_{I,R}\in\mathbb Z^{T_M\times T_K},
$$

$$
B_{R,J}\in\mathbb Z^{T_K\times T_N},
$$

$$
C_{I,J}\in\mathbb Z^{T_M\times T_N}.
$$

Output Stationary 要求 $C_{I,J}$ 在遍历所有 $R$ 时留在累加器中。

### 2.3 tile 的最小存储量

若输入 tile 和权重 tile 使用 INT8，部分和 tile 使用 INT32，不考虑对齐、描述符、队列和双缓冲时，三个 tile 至少需要：

$$
B_{\text{tile}}
=
T_MT_K
+T_KT_N
+4T_MT_N
$$

字节。

若使用双缓冲同时保存“当前计算 tile”和“下一组待计算 tile”，输入和权重缓冲需求还会增加。实际容量必须根据缓冲组织和并发方式重新计算。

### 2.4 卷积到 GEMM

卷积输出为：

$$
Y_{n,c_o,h_o,w_o}
=
b_{c_o}
+
\sum_{c_i=0}^{C_{\text{in}}-1}
\sum_{k_h=0}^{K_h-1}
\sum_{k_w=0}^{K_w-1}
X_{n,c_i,h_oS_h+k_h-P_h,w_oS_w+k_w-P_w}
W_{c_o,c_i,k_h,k_w}.
$$

定义 im2col 行索引：

$$
m=(nH_{\text{out}}+h_o)W_{\text{out}}+w_o,
$$

归约维索引：

$$
k=(c_iK_h+k_h)K_w+k_w,
$$

输出列索引：

$$
o=c_o.
$$

构造矩阵：

$$
A_{m,k}
=
X_{n,c_i,h_oS_h+k_h-P_h,w_oS_w+k_w-P_w},
$$

$$
B_{k,o}=W_{c_o,c_i,k_h,k_w}.
$$

则卷积可以写为：

$$
C_{m,o}
=
b_o+\sum_{k=0}^{K-1}A_{m,k}B_{k,o}.
$$

最后把 $C$ 从 $(M,O)$ 重新解释为 $(N_b,C_{\text{out}},H_{\text{out}},W_{\text{out}})$。

### 2.5 后处理

设 PE 阵列输出的 INT32 累加值为 $a$，偏置为 $b$，整数乘子为 $q_m$，右移量为 $q_s$：

$$
z=q_m(a+b).
$$

沿用第 25 章的带舍入右移：

$$
r
=
\operatorname{sgn}(z)
\left\lfloor
\frac{|z|+2^{q_s-1}}{2^{q_s}}
\right\rfloor,
\qquad q_s\ge1.
$$

当 $q_s=0$ 时，$r=z$。重新量化和 ReLU 输出为：

$$
y
=
\max
\left(
0,
\operatorname{clip}(r,-128,127)
\right).
$$

若当前层不启用 ReLU，则省略最外层的 $\max(0,\cdot)$。

### 2.6 变量含义

- $A$、$B$、$C$：GEMM 的左输入、右输入和输出矩阵。
- $M$：输出位置或矩阵行数。
- $K$：归约维长度。
- $N$：GEMM 输出列数；卷积映射中使用 $O$ 表示输出通道数以避免混淆。
- $i$、$j$、$k$：矩阵行、列和归约维索引。
- $p_{i,j}^{(k)}$：第 $(i,j)$ 个 PE 完成前 $k$ 次乘加后的部分和。
- $I$、$J$、$R$：输出行 tile、输出列 tile 和归约 tile 的编号。
- $T_M$、$T_K$、$T_N$：三个维度上的 tile 大小。
- $B_{\text{tile}}$：三个 tile 的最小存储字节数。
- $X$：NCHW 布局的卷积输入。
- $W$：卷积权重，布局为 $(C_{\text{out}},C_{\text{in}},K_h,K_w)$。
- $Y$：卷积输出。
- $b$、$b_o$：INT32 偏置。
- $N_b$：卷积批次大小。
- $C_{\text{in}}$、$C_{\text{out}}$：输入和输出通道数。
- $H_{\text{out}}$、$W_{\text{out}}$：卷积输出高度和宽度。
- $K_h$、$K_w$：卷积核高度和宽度。
- $S_h$、$S_w$：卷积步长。
- $P_h$、$P_w$：零填充大小。
- $m$：im2col 后的矩阵行索引。
- $o$：GEMM 输出列索引，对应卷积输出通道 $c_o$。
- $a$：PE 阵列产生的 INT32 累加值。
- $q_m$、$q_s$：重新量化的整数乘子和非负右移量。
- $z$、$r$、$y$：缩放结果、舍入结果和最终输出。
- $\operatorname{sgn}$：符号函数。
- $\operatorname{clip}$：区间饱和裁剪。

### 2.7 公式怎么理解

PE 递推公式说明了 Output Stationary 的核心：$p_{i,j}$ 在 PE 内保留，输入 $A_{i,k}$ 和权重 $B_{k,j}$ 随 $k$ 变化。只有完成全部 $K$ 次乘加后，输出才离开 PE。

分块公式把同一个矩阵乘法分成多个小矩阵乘法。$T_M$ 和 $T_N$ 通常与阵列可容纳的输出 tile 相关，$T_K$ 则受输入、权重缓冲区和调度影响。

存储公式只计算最基本的数据区：两个 INT8 输入 tile 和一个 INT32 部分和 tile。它不是完整 SRAM 容量公式，也不能替代端口、bank、对齐和双缓冲设计。

im2col 公式建立了卷积索引与 GEMM 索引之间的一一对应关系。代码显式创建 $A$ 便于验证；硬件地址生成器可以直接按相同索引读取卷积窗口。

后处理把 INT32 结果转换为下一层需要的 INT8。偏置、舍入、饱和与激活的先后顺序必须成为描述符或算子语义的一部分，软件和 NPU 才能得到一致结果。

## 3. 代码示例

### 3.1 最小可运行代码

下面使用 NumPy 实现一个功能级 NPU：

1. `im2col_nchw` 把卷积转换为 GEMM。
2. `PEArray` 使用 Output Stationary 方式分块累加。
3. `TeachingNPU` 模拟 DMA、Global Buffer、PE 阵列、后处理和写回。
4. 直接卷积参考实现用于检查 NPU 输出。

代码不模拟时钟周期、总线握手、PE 间物理连线或真实并发。

```python
from dataclasses import dataclass

import numpy as np


def round_shift(values, shift):
    values = np.asarray(values, dtype=np.int64)
    if shift < 0:
        raise ValueError("shift must be non-negative")
    if shift == 0:
        return values
    offset = 1 << (shift - 1)
    magnitude = (np.abs(values) + offset) >> shift
    return np.sign(values) * magnitude


def requantize_int8(values, multiplier, shift, relu):
    scaled = np.asarray(values, dtype=np.int64) * multiplier
    rounded = round_shift(scaled, shift)
    clipped = np.clip(rounded, -128, 127)
    if relu:
        clipped = np.maximum(clipped, 0)
    return clipped.astype(np.int8)


def tile_storage_bytes(tile_m, tile_k, tile_n):
    int8_inputs = tile_m * tile_k + tile_k * tile_n
    int32_partial_sums = 4 * tile_m * tile_n
    return int8_inputs + int32_partial_sums


def im2col_nchw(x, kernel_h, kernel_w, stride=1, padding=0):
    x = np.asarray(x, dtype=np.int8)
    batch, channels, height, width = x.shape
    out_h = (height + 2 * padding - kernel_h) // stride + 1
    out_w = (width + 2 * padding - kernel_w) // stride + 1
    padded = np.pad(
        x,
        ((0, 0), (0, 0), (padding, padding), (padding, padding)),
    )
    rows = []
    for n in range(batch):
        for out_y in range(out_h):
            for out_x in range(out_w):
                patch = padded[
                    n,
                    :,
                    out_y * stride:out_y * stride + kernel_h,
                    out_x * stride:out_x * stride + kernel_w,
                ]
                rows.append(patch.reshape(-1))
    return np.asarray(rows, dtype=np.int8), out_h, out_w


class PEArray:
    def __init__(self, rows, columns, tile_k):
        self.rows = rows
        self.columns = columns
        self.tile_k = tile_k
        self.trace = []

    def matmul_output_stationary(self, a, b):
        a = np.asarray(a, dtype=np.int8)
        b = np.asarray(b, dtype=np.int8)
        if a.ndim != 2 or b.ndim != 2 or a.shape[1] != b.shape[0]:
            raise ValueError("invalid GEMM shapes")

        m_size, k_size = a.shape
        _, n_size = b.shape
        output = np.zeros((m_size, n_size), dtype=np.int64)
        self.trace = []

        for m_start in range(0, m_size, self.rows):
            m_end = min(m_start + self.rows, m_size)
            for n_start in range(0, n_size, self.columns):
                n_end = min(n_start + self.columns, n_size)
                accumulator = np.zeros(
                    (m_end - m_start, n_end - n_start),
                    dtype=np.int64,
                )

                for k_start in range(0, k_size, self.tile_k):
                    k_end = min(k_start + self.tile_k, k_size)
                    a_tile = a[m_start:m_end, k_start:k_end]
                    b_tile = b[k_start:k_end, n_start:n_end]

                    for local_k in range(k_end - k_start):
                        accumulator += (
                            a_tile[:, local_k].astype(np.int64)[:, None]
                            * b_tile[local_k, :].astype(np.int64)[None, :]
                        )

                    self.trace.append({
                        "output_tile": (m_start, n_start),
                        "k_range": (k_start, k_end),
                    })

                output[m_start:m_end, n_start:n_end] = accumulator

        return output


@dataclass(frozen=True)
class ConvDescriptor:
    src: int
    weight: int
    bias: int
    dst: int
    stride: int
    padding: int
    multiplier: int
    shift: int
    relu: bool


class GlobalBuffer:
    def __init__(self, capacity_bytes):
        self.capacity_bytes = capacity_bytes
        self.entries = {}

    def load(self, name, value):
        value = np.asarray(value).copy()
        candidate_entries = dict(self.entries)
        candidate_entries[name] = value
        used_bytes = sum(item.nbytes for item in candidate_entries.values())
        if used_bytes > self.capacity_bytes:
            raise ValueError("resident tensors exceed global buffer capacity")
        self.entries[name] = value


class TeachingNPU:
    def __init__(self, dram, pe_rows, pe_columns, tile_k, buffer_bytes):
        self.dram = dram
        self.pe_array = PEArray(pe_rows, pe_columns, tile_k)
        self.global_buffer = GlobalBuffer(buffer_bytes)
        self.trace = []

    def execute_conv(self, descriptor):
        self.trace = []
        x = self.dram[descriptor.src]
        weight = self.dram[descriptor.weight]
        bias = self.dram[descriptor.bias]

        self.global_buffer.load("input", x)
        self.global_buffer.load("weight", weight)
        self.global_buffer.load("bias", bias)
        self.trace.extend(["DMA_LOAD_INPUT", "DMA_LOAD_WEIGHT", "DMA_LOAD_BIAS"])

        out_channels, in_channels, kernel_h, kernel_w = weight.shape
        if x.shape[1] != in_channels or bias.shape != (out_channels,):
            raise ValueError("invalid convolution descriptor")

        a_matrix, out_h, out_w = im2col_nchw(
            x,
            kernel_h,
            kernel_w,
            descriptor.stride,
            descriptor.padding,
        )
        b_matrix = weight.reshape(out_channels, -1).T
        accumulator = self.pe_array.matmul_output_stationary(
            a_matrix, b_matrix
        )
        self.trace.append("PE_ARRAY_GEMM")

        accumulator += bias.astype(np.int64)[None, :]
        output_matrix = requantize_int8(
            accumulator,
            descriptor.multiplier,
            descriptor.shift,
            descriptor.relu,
        )
        self.trace.append("POST_PROCESS")

        batch = x.shape[0]
        output = output_matrix.reshape(
            batch, out_h, out_w, out_channels
        ).transpose(0, 3, 1, 2)
        self.dram[descriptor.dst] = output
        self.trace.append("DMA_STORE_OUTPUT")
        return output


def direct_conv_reference(
    x, weight, bias, stride, padding, multiplier, shift, relu
):
    x = np.asarray(x, dtype=np.int8)
    weight = np.asarray(weight, dtype=np.int8)
    batch, in_channels, height, width = x.shape
    out_channels, _, kernel_h, kernel_w = weight.shape
    out_h = (height + 2 * padding - kernel_h) // stride + 1
    out_w = (width + 2 * padding - kernel_w) // stride + 1
    padded = np.pad(
        x.astype(np.int32),
        ((0, 0), (0, 0), (padding, padding), (padding, padding)),
    )
    accumulator = np.zeros(
        (batch, out_channels, out_h, out_w), dtype=np.int64
    )

    for n in range(batch):
        for out_channel in range(out_channels):
            for out_y in range(out_h):
                for out_x in range(out_w):
                    window = padded[
                        n,
                        :,
                        out_y * stride:out_y * stride + kernel_h,
                        out_x * stride:out_x * stride + kernel_w,
                    ]
                    accumulator[n, out_channel, out_y, out_x] = (
                        bias[out_channel]
                        + np.sum(
                            window
                            * weight[out_channel].astype(np.int32),
                            dtype=np.int64,
                        )
                    )
    return requantize_int8(accumulator, multiplier, shift, relu)


INPUT = 0x2000
WEIGHT = 0x3000
BIAS = 0x4000
OUTPUT = 0x5000

input_tensor = np.arange(1, 17, dtype=np.int8).reshape(1, 1, 4, 4)
weight_tensor = np.array([
    [[[1, 1], [1, 1]]],
    [[[1, 0], [0, -1]]],
], dtype=np.int8)
bias_tensor = np.array([1, -1], dtype=np.int32)

dram = {
    INPUT: input_tensor,
    WEIGHT: weight_tensor,
    BIAS: bias_tensor,
}
descriptor = ConvDescriptor(
    src=INPUT,
    weight=WEIGHT,
    bias=BIAS,
    dst=OUTPUT,
    stride=1,
    padding=0,
    multiplier=1,
    shift=0,
    relu=True,
)

npu = TeachingNPU(
    dram,
    pe_rows=2,
    pe_columns=2,
    tile_k=2,
    buffer_bytes=128,
)
npu_output = npu.execute_conv(descriptor)
reference_output = direct_conv_reference(
    input_tensor,
    weight_tensor,
    bias_tensor,
    stride=1,
    padding=0,
    multiplier=1,
    shift=0,
    relu=True,
)

expected_channel_0 = np.array([
    [15, 19, 23],
    [31, 35, 39],
    [47, 51, 55],
], dtype=np.int8)

# 单独检查 M、N、K 三个方向均存在边界 tile 的 GEMM。
edge_a = np.array([
    [1, 2, 3],
    [4, 5, 6],
    [7, 8, 9],
], dtype=np.int8)
edge_b = np.array([
    [1, 0, 2],
    [0, 1, 3],
    [1, 1, 0],
], dtype=np.int8)
edge_array = PEArray(rows=2, columns=2, tile_k=2)
edge_output = edge_array.matmul_output_stationary(edge_a, edge_b)

print("tile storage bytes:", tile_storage_bytes(2, 2, 2))
print("PE tile operations:", len(npu.pe_array.trace))
print("NPU stages:", npu.trace)
print("output channel 0:")
print(npu_output[0, 0])
print("output channel 1:")
print(npu_output[0, 1])

assert tile_storage_bytes(2, 2, 2) == 24
assert np.array_equal(edge_output, edge_a.astype(np.int64) @ edge_b)
assert len(npu.pe_array.trace) == 10
assert npu_output.shape == (1, 2, 3, 3)
assert np.array_equal(npu_output, reference_output)
assert np.array_equal(npu_output[0, 0], expected_channel_0)
assert np.array_equal(npu_output[0, 1], np.zeros((3, 3), dtype=np.int8))
assert npu.trace == [
    "DMA_LOAD_INPUT",
    "DMA_LOAD_WEIGHT",
    "DMA_LOAD_BIAS",
    "PE_ARRAY_GEMM",
    "POST_PROCESS",
    "DMA_STORE_OUTPUT",
]
print("RISC-V NPU verification passed.")
```

### 3.2 输入输出说明

- `input_tensor` 的形状为 `(1,1,4,4)`。
- `weight_tensor` 包含两个 $2\times2$ 卷积核，形状为 `(2,1,2,2)`。
- im2col 后左矩阵形状为 `(9,4)`，权重矩阵形状为 `(4,2)`。
- `PEArray` 为 $2\times2$，归约 tile 大小 `tile_k=2`。
- 当前 tile 的基本存储量为 $2\times2+2\times2+4\times2\times2=24$ 字节。
- `npu.trace` 记录 DMA 读取、PE 阵列计算、后处理和输出写回。
- `npu_output` 的形状为 `(1,2,3,3)`。
- 第一个输出通道为 `[[15,19,23],[31,35,39],[47,51,55]]`。
- 第二个输出通道经过 ReLU 后全部为零。
- NPU 的 im2col + GEMM 结果与独立直接卷积参考实现一致。

### 3.3 关键代码解释

`im2col_nchw` 按输出位置提取卷积窗口，每个窗口成为矩阵的一行。它明确展示卷积到 GEMM 的索引映射，但真实 NPU 不一定在内存中生成完整 im2col 矩阵。

`PEArray.matmul_output_stationary` 先为一个输出 tile 创建 `accumulator`，然后遍历所有 `k_start`。在完整归约维结束前，部分和不会写入最终输出矩阵，这就是本示例的 Output Stationary 行为。

`tile_storage_bytes` 对应本章的最小 tile 存储公式。它不包含完整输入张量、完整 im2col 矩阵、对齐空间和双缓冲，因此不能直接当作硬件 SRAM 容量。

`GlobalBuffer` 检查当前所有驻留数组的总字节数，但没有实现 bank、端口、替换或并发。为保持示例简洁，它会载入完整的小张量；真实 NPU 通常按 tile 搬运不能整体驻留的大张量。`TeachingNPU.execute_conv` 按顺序模拟 DMA 读取、卷积降级、PE 阵列、偏置与激活、输出写回。

`direct_conv_reference` 不使用 im2col 和 PE 阵列。两个实现采用不同循环结构却得到相同结果，可以检查卷积降级和 tile 累加是否正确。

模拟器使用 NumPy `int64` 保存软件中间值，避免 Python 示例自身溢出。目标 NPU 语义仍为 INT32 累加；实际实现需要检查最坏情况下的累加范围，或者明确规定溢出和饱和行为。
