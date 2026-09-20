# 量化

## 1. 文字讲解

### 1.1 这个算法解决什么问题

神经网络的权重和激活通常用浮点数表示。量化（Quantization）用位数更少的数据格式近似这些数值，例如把 FP32 权重转换为 FP16、BF16、INT8 或 INT4。

量化的输入通常是浮点张量，输出是低精度张量或整数张量及其量化参数。位数减少后，可表示的数值数量也会减少，因此量化结果只是原数值的近似值。

量化需要在表示位数和数值误差之间取舍。位数越少，量化网格通常越稀疏；如果量化范围选择不合适，还可能把超出范围的数值裁剪到边界。

### 1.2 核心思想

常见低精度格式可以分成两类。

**低精度浮点格式**仍包含符号位、指数位和尾数位：

| 格式 | 总位数 | 符号位 | 指数位 | 尾数位 | 主要特点 |
|---|---:|---:|---:|---:|---|
| FP32 | 32 | 1 | 8 | 23 | 范围和精度都较高 |
| FP16 | 16 | 1 | 5 | 10 | 尾数比 BF16 多，但指数范围更窄 |
| BF16 | 16 | 1 | 8 | 7 | 指数位与 FP32 相同，但尾数精度更低 |

指数位主要影响可表示数值的范围，尾数位主要影响相邻可表示数值之间的精细程度。因此，FP16 和 BF16 虽然都是 16 位格式，数值特性却不同。

**整数格式**没有指数位。INT8 有 $2^8=256$ 种编码，有符号整数范围通常是 $[-128,127]$；INT4 有 $2^4=16$ 种编码，有符号范围通常是 $[-8,7]$。量化算法需要使用缩放因子把浮点范围映射到这些整数编码。

在线性整数表示中，每个整数编码对应量化网格上的一个位置。缩放因子 $s$ 决定网格间距，零点 $z$ 决定实数零映射到哪个整数编码。

### 1.3 基本流程

1. 收集待量化张量或校准数据的数值范围。
2. 选择数据格式和位数，例如 INT8 或 INT4。
3. 选择对称量化或非对称量化。
4. 根据浮点范围和整数范围计算缩放因子 $s$ 与零点 $z$。
5. 对浮点数进行缩放、取整和裁剪，得到整数编码。
6. 使用 $s$ 和 $z$ 反量化，得到浮点近似值。
7. 比较原值与反量化值，检查量化误差是否可以接受。

这里的数值范围可以为整个张量共享，也可以为不同通道或分组分别计算。本章使用最简单的逐张量量化，即整个数组共享一组 $s$ 和 $z$。

### 1.4 直观例子

假设要量化下面五个浮点数：

$$

\mathbf x=[-1.0,\ -0.3,\ 0,\ 0.8,\ 1.2].

$$

使用对称 INT4 量化时，常取对称整数范围 $[-7,7]$ ，并令绝对值最大的 $1.2$ 映射到 $7$。缩放因子为：

$$

s=\frac{1.2}{7}\approx 0.1714.

$$

原数组量化后约为：

$$

\mathbf q=[-6,\ -2,\ 0,\ 5,\ 7].

$$

反量化结果约为：

$$

\hat{\mathbf x}=[-1.0286,\ -0.3429,\ 0,\ 0.8571,\ 1.2].

$$

这些结果与原值接近，但并不完全相同，差值就是量化误差。

## 2. 公式讲解

### 2.1 核心公式

对于规格化浮点数，可用下面的形式理解其数值：

$$

x=(-1)^a\times 2^{e-b}\times (1+f).

$$

在线性整数量化中，设浮点数允许范围为 $[x_{\min},x_{\max}]$ ，整数编码范围为 $[q_{\min},q_{\max}]$。非对称量化的缩放因子为：

$$

s=\frac{x_{\max}-x_{\min}}
{q_{\max}-q_{\min}}.

$$

零点为：

$$

z=\operatorname{clip}\left(
\operatorname{round}\left(q_{\min}-\frac{x_{\min}}{s}\right),
q_{\min},q_{\max}
\right).

$$

浮点数 $x$ 的量化公式为：

$$

q=\operatorname{clip}\left(
\operatorname{round}\left(\frac{x}{s}\right)+z,
q_{\min},q_{\max}
\right).

$$

反量化公式为：

$$

\hat x=s(q-z).

$$

对称量化令浮点范围关于零对称。对于 $B$ 位有符号整数，常使用：

$$

q_{\max}^{\text{sym}}=2^{B-1}-1,
\qquad
q_{\min}^{\text{sym}}=-q_{\max}^{\text{sym}},

$$

$$

\alpha=\max(|x_{\min}|,|x_{\max}|),
\qquad
s=\frac{\alpha}{q_{\max}^{\text{sym}}},
\qquad
z=0.

$$

单个元素的量化误差可以写为：

$$

e_i=\hat x_i-x_i.

$$

常用的平均绝对误差和均方误差分别为：

$$

\operatorname{MAE}=\frac{1}{N}\sum_{i=1}^{N}|e_i|,

$$

$$

\operatorname{MSE}=\frac{1}{N}\sum_{i=1}^{N}e_i^2.

$$

### 2.2 变量含义

- $x$：量化前的浮点数。
- $\hat x$：整数编码反量化后得到的浮点近似值。
- $a$：浮点数的符号位，取值为 $0$ 或 $1$。
- $e$：浮点格式中存储的指数编码。
- $b$：指数偏置。
- $f$：由尾数位表示的小数部分。
- $x_{\min}$ 、 $x_{\max}$：量化使用的浮点下界和上界。
- $q_{\min}$ 、 $q_{\max}$：整数编码的下界和上界。
- $s$：正的缩放因子，也就是相邻量化级别之间的间距。
- $z$：整数零点，使实数零能够映射到某个整数编码。
- $q$：量化后的整数编码。
- $\operatorname{round}$：将数值舍入到相邻整数。
- $\operatorname{clip}$：将数值限制在给定上下界内。
- $B$：整数格式的位数。
- $\alpha$：对称量化使用的最大绝对值。
- $i$：数组中元素的索引。
- $e_i$：第 $i$ 个元素的量化误差。
- $N$：参与误差统计的元素数量。

### 2.3 公式怎么理解

非对称量化把浮点区间的两个端点映射到整数区间的两个端点。零点 $z$ 可以不为 $0$ ，因此量化网格能够向正数或负数一侧移动，适合数值分布明显不以零为中心的情况。

对称量化固定 $z=0$ ，并用最大绝对值构造 $[-\alpha,\alpha]$。它的参数更简单，但如果数据只集中在零的一侧，另一侧的部分编码范围可能没有被使用。

INT8 类型本身可以表示 $[-128,127]$ ，但对称 INT8 量化常使用 $[-127,127]$ ，舍弃额外的 $-128$ ，使正负范围严格对称。同理，本章的对称 INT4 示例使用 $[-7,7]$ ，而不是完整的 $[-8,7]$。

量化误差主要来自两部分：取整使数值落到最近的量化网格点；裁剪使超出校准范围的数值落到边界。位数越少，整数级别越少，在相同浮点范围内通常会得到更大的网格间距。

## 3. 代码示例

### 3.1 最小可运行代码

下面分别实现非对称量化和对称量化，并比较 INT8 与 INT4 的量化结果。

```python
import numpy as np


def quantize_asymmetric(x, bits):
    q_min = -(2 ** (bits - 1))
    q_max = 2 ** (bits - 1) - 1

    # 将 0 包含在校准范围内，使实数 0 可以被精确表示。
    x_min = min(float(x.min()), 0.0)
    x_max = max(float(x.max()), 0.0)
    if x_min == x_max:
        return np.zeros_like(x, dtype=np.int32), 1.0, 0

    scale = (x_max - x_min) / (q_max - q_min)
    zero_point = int(np.clip(
        np.rint(q_min - x_min / scale), q_min, q_max
    ))
    q = np.clip(
        np.rint(x / scale) + zero_point, q_min, q_max
    ).astype(np.int32)
    return q, scale, zero_point


def quantize_symmetric(x, bits):
    q_max = 2 ** (bits - 1) - 1
    q_min = -q_max
    abs_max = float(np.max(np.abs(x)))
    if abs_max == 0.0:
        return np.zeros_like(x, dtype=np.int32), 1.0, 0

    scale = abs_max / q_max
    q = np.clip(
        np.rint(x / scale), q_min, q_max
    ).astype(np.int32)
    return q, scale, 0


def dequantize(q, scale, zero_point):
    return scale * (q.astype(np.float64) - zero_point)


def print_result(name, x, q, x_hat, scale, zero_point):
    error = x_hat - x
    mae = np.mean(np.abs(error))
    mse = np.mean(error ** 2)
    print(f"{name}:")
    print("  scale:", round(scale, 6))
    print("  zero point:", zero_point)
    print("  quantized:", q)
    print("  dequantized:", np.round(x_hat, 6))
    print("  MAE:", round(mae, 6))
    print("  MSE:", round(mse, 8))


x = np.array([-1.0, -0.3, 0.0, 0.8, 1.2], dtype=np.float64)

q_asym8, s_asym8, z_asym8 = quantize_asymmetric(x, bits=8)
x_asym8 = dequantize(q_asym8, s_asym8, z_asym8)

q_sym8, s_sym8, z_sym8 = quantize_symmetric(x, bits=8)
x_sym8 = dequantize(q_sym8, s_sym8, z_sym8)

q_sym4, s_sym4, z_sym4 = quantize_symmetric(x, bits=4)
x_sym4 = dequantize(q_sym4, s_sym4, z_sym4)

print("original:", x)
print_result(
    "asymmetric INT8", x, q_asym8, x_asym8, s_asym8, z_asym8
)
print_result(
    "symmetric INT8", x, q_sym8, x_sym8, s_sym8, z_sym8
)
print_result(
    "symmetric INT4", x, q_sym4, x_sym4, s_sym4, z_sym4
)

assert np.array_equal(q_sym4, [-6, -2, 0, 5, 7])
assert np.all(q_asym8 >= -128) and np.all(q_asym8 <= 127)
assert np.all(q_sym4 >= -7) and np.all(q_sym4 <= 7)
```

### 3.2 输入输出说明

- `x` 是待量化的一维浮点数组，形状为 `(5,)`。
- `q_asym8` 是非对称 INT8 编码，取值被限制在 `[-128, 127]`。
- `q_sym8` 是对称 INT8 编码，取值被限制在 `[-127, 127]`。
- `q_sym4` 是对称 INT4 编码，取值被限制在 `[-7, 7]`。
- `scale` 和 `zero_point` 分别对应公式中的 $s$ 和 $z$。
- `x_hat` 是反量化后的浮点近似值。
- `MAE` 和 `MSE` 用于衡量该数组上的量化误差。

NumPy 没有通用的 INT4 数组类型，因此示例使用 `int32` 保存 INT4 编码，但通过裁剪保证数值位于 INT4 示例采用的范围内。实际的 INT4 存储还需要将多个 4 位编码打包到字节中，这不影响本例展示的量化公式。

### 3.3 关键代码解释

`quantize_asymmetric` 使用浮点最小值和最大值计算缩放因子，再求出可能非零的 `zero_point`。`np.rint` 对缩放后的数值取整，`np.clip` 处理整数溢出和超出校准范围的数值。

`quantize_symmetric` 使用最大绝对值构造对称范围，并固定零点为 `0`。传入 `bits=8` 和 `bits=4` 时，同一函数分别得到 INT8 和 INT4 编码。

`dequantize` 执行 $\hat x=s(q-z)$。`print_result` 再比较 $x$ 与 $\hat x$ ，计算每种方案的 MAE 和 MSE。
