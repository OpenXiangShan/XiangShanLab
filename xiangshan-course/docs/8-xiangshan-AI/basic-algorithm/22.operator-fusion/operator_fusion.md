# 算子融合

## 1. 文字讲解

### 1.1 算子融合解决什么问题

在计算图中，算子可以看作一个“输入张量到输出张量”的计算节点。例如，线性层可以拆成矩阵乘法和偏置加法，随后还可能连接 ReLU 激活：

```text
X -> MatMul -> T1 -> BiasAdd -> T2 -> ReLU -> Y
```

如果运行系统逐个执行这些算子，`T1` 和 `T2` 是相邻算子之间的中间结果。算子融合（Operator Fusion）会识别一段满足条件的子图，用一个融合算子或一段统一生成的程序实现相同计算：

```text
X -> FusedAffineReLU -> Y
```

融合后的计算图节点更少，而且具体实现可以不把部分中间结果完整写出后再读回。不过，“在一行代码里写出多个运算”不等于底层一定完成了融合；是否真正融合，要看计算图优化器、编译器和执行后端生成的程序。

### 1.2 核心思想

若连续算子为 $f_1,f_2,\ldots,f_k$，原计算为：

$$
Y=f_k\left(f_{k-1}\left(\cdots f_1(X)\right)\right).
$$

算子融合把这段组合计算替换为一个新算子 $F_{\text{fused}}$：

$$
Y=F_{\text{fused}}(X),
\qquad
F_{\text{fused}}=f_k\circ f_{k-1}\circ\cdots\circ f_1.
$$

关键要求是融合前后的计算语义一致。常见融合模式包括：

- 矩阵乘法或卷积、偏置加法与激活函数的融合。
- 推理阶段将 Linear 或 Conv 后的 BatchNorm 折叠进前一层参数。
- 多个逐元素运算组成的连续子图融合。
- 大模型中的 Bias + GELU、残差加法 + LayerNorm 等特定子图融合。

### 1.3 融合通常怎样发生

1. 将模型表示成由算子节点和张量边构成的计算图。
2. 在图中匹配后端支持的子图模式，例如 `MatMul -> Add -> ReLU`。
3. 检查张量形状、数据类型、属性和依赖关系是否满足融合规则。
4. 用融合节点替换原子图，或为整段子图生成统一程序。
5. 比较融合前后的输出，检查数值误差是否在允许范围内。

融合不是无条件进行的。例如，中间张量还被其他节点使用、形状信息不足、算子属性不匹配，或者后端没有对应实现时，融合规则可能无法应用。浮点运算若因融合而改变运算顺序，也可能产生很小的舍入差异，因此通常使用误差容限检查等价性。

### 1.4 两类融合要区分

**运行时算子融合**保留原数学表达式，但把多个计算步骤放进同一个融合实现中。`MatMul + Bias + ReLU` 是典型例子：

$$
Y=\operatorname{ReLU}(XW+b).
$$

**参数折叠**在执行前直接改写参数。推理阶段的 BatchNorm 使用固定均值和方差，因此可以把 `Linear + BatchNorm` 改写为一个新的 Linear。此时 BatchNorm 节点可从推理图中删除。

训练阶段的 BatchNorm 使用当前批次统计量，并且还要更新运行统计量，不能把这些量当作固定常数直接折叠。参数折叠与运行时融合都属于广义的算子融合，但实现方法不同。

### 1.5 直观例子

设：

$$
X=
\begin{bmatrix}
1 & 2 & -1\\
-0.5 & 1 & 2
\end{bmatrix},
\quad
W=
\begin{bmatrix}
1 & -2\\
0.5 & 1\\
-1 & 0.25
\end{bmatrix},
\quad
b=
\begin{bmatrix}
0.1 & -0.2
\end{bmatrix}.
$$

依次执行矩阵乘法、偏置加法和 ReLU，或者直接按照融合表达式计算，结果均为：

$$
Y=
\begin{bmatrix}
3.1 & 0\\
0 & 2.3
\end{bmatrix}.
$$

这里验证的是融合前后的数学等价性。NumPy 表达式 `np.maximum(X @ W + b, 0.0)` 是否被底层生成为单个融合实现，不由这一行 Python 代码本身保证。

## 2. 公式讲解

### 2.1 MatMul、Bias 与 ReLU

设输入矩阵 $X\in\mathbb R^{N\times D}$，权重矩阵 $W\in\mathbb R^{D\times M}$，偏置 $b\in\mathbb R^M$。逐算子形式为：

$$
T_1=XW,
$$

$$
T_2=T_1+b,
$$

$$
Y=\operatorname{ReLU}(T_2)=\max(0,T_2).
$$

偏置 $b$ 会沿批次维广播。将中间变量代回后，可得到融合表达式：

$$
Y=\operatorname{ReLU}(XW+b).
$$

两种写法定义同一个函数，差别在于执行系统是否将它们作为多个独立步骤，还是一个融合单元来实现。

### 2.2 Linear 与 BatchNorm 的推理折叠

线性层输出为：

$$
Z=XW+b.
$$

在推理阶段，BatchNorm 对第 $j$ 个输出特征执行：

$$
Y_j=
\gamma_j
\frac{Z_j-\mu_j}{\sqrt{\sigma_j^2+\epsilon}}
+\beta_j.
$$

先定义每个输出特征的缩放系数：

$$
a_j=\frac{\gamma_j}{\sqrt{\sigma_j^2+\epsilon}}.
$$

把 $Z_j=XW_{:,j}+b_j$ 代入 BatchNorm 公式：

$$
\begin{aligned}
Y_j
&=a_j\left(XW_{:,j}+b_j-\mu_j\right)+\beta_j\\
&=X\left(a_jW_{:,j}\right)
+a_j(b_j-\mu_j)+\beta_j.
\end{aligned}
$$

因此可以构造新的权重和偏置：

$$
W'_{:,j}=a_jW_{:,j},
$$

$$
b'_j=a_j(b_j-\mu_j)+\beta_j.
$$

融合后的单个线性层为：

$$
Y=XW'+b'.
$$

### 2.3 变量含义

- $X$：输入矩阵；$N$ 是样本数，$D$ 是输入特征数。
- $W$：线性层权重；$M$ 是输出特征数。
- $b$：线性层偏置，长度为 $M$。
- $T_1$、$T_2$：融合前需要在算子之间传递的中间结果。
- $Y$：最终输出矩阵，形状为 $N\times M$。
- $\operatorname{ReLU}$：逐元素取 $\max(0,x)$ 的激活函数。
- $Z$：BatchNorm 之前的线性层输出。
- $j$：输出特征索引，$W_{:,j}$ 表示 $W$ 的第 $j$ 列。
- $\mu_j$、$\sigma_j^2$：推理时第 $j$ 个特征固定的运行均值和运行方差。
- $\gamma_j$、$\beta_j$：BatchNorm 的可学习缩放参数和偏移参数。
- $\epsilon$：加在方差上的正数，用于避免分母为零。
- $a_j$：由 BatchNorm 参数和运行方差计算出的固定缩放系数。
- $W'$、$b'$：折叠 BatchNorm 后的新权重和新偏置。
- $\circ$：函数复合符号，$g\circ f$ 表示先执行 $f$，再执行 $g$。

### 2.4 公式怎么理解

`MatMul + Bias + ReLU` 融合没有改变参数，只是把函数复合关系交给一个融合实现。融合成立的直接依据，是把 $T_1$ 和 $T_2$ 逐层代回后恰好得到 $\operatorname{ReLU}(XW+b)$。

`Linear + BatchNorm` 折叠则把推理阶段的固定仿射变换吸收到 $W$ 和 $b$ 中。BatchNorm 的均值、方差必须固定，公式中的 $a_j$ 才能预先计算。因此该折叠要求模型处于推理模式，并且已经获得可用的运行均值和运行方差。

数学等价不代表浮点结果逐位相同。不同实现可能使用不同的乘加顺序，验证时应根据数据类型选择合理的绝对误差和相对误差容限。

## 3. 代码示例

### 3.1 最小可运行代码

下面的 NumPy 代码完成两项验证：

1. 比较逐步执行与融合表达式 `MatMul + Bias + ReLU`。
2. 比较 `Linear + BatchNorm` 与折叠参数后的单个 Linear。

```python
import numpy as np


def relu(x):
    return np.maximum(x, 0.0)


def separate_affine_relu(x, weight, bias):
    matmul_output = x @ weight
    bias_output = matmul_output + bias
    return relu(bias_output)


def fused_affine_relu(x, weight, bias):
    # 这是融合后的数学表达式，不保证 NumPy 后端生成单个融合实现。
    return np.maximum(x @ weight + bias, 0.0)


def batch_norm_inference(z, mean, variance, gamma, beta, eps):
    return gamma * (z - mean) / np.sqrt(variance + eps) + beta


def fold_linear_batch_norm(
    weight, bias, mean, variance, gamma, beta, eps
):
    scale = gamma / np.sqrt(variance + eps)
    folded_weight = weight * scale
    folded_bias = scale * (bias - mean) + beta
    return folded_weight, folded_bias


x = np.array([
    [1.0, 2.0, -1.0],
    [-0.5, 1.0, 2.0],
])
weight = np.array([
    [1.0, -2.0],
    [0.5, 1.0],
    [-1.0, 0.25],
])
bias = np.array([0.1, -0.2])

separate_output = separate_affine_relu(x, weight, bias)
fused_output = fused_affine_relu(x, weight, bias)

mean = np.array([0.3, -0.5])
variance = np.array([1.44, 0.25])
gamma = np.array([1.2, 0.5])
beta = np.array([-0.1, 0.2])
eps = 1e-5

linear_output = x @ weight + bias
linear_bn_output = batch_norm_inference(
    linear_output, mean, variance, gamma, beta, eps
)
folded_weight, folded_bias = fold_linear_batch_norm(
    weight, bias, mean, variance, gamma, beta, eps
)
folded_output = x @ folded_weight + folded_bias

print("separate affine + ReLU:")
print(separate_output)
print("fused expression:")
print(fused_output)
print("linear + BatchNorm:")
print(np.round(linear_bn_output, 6))
print("folded linear:")
print(np.round(folded_output, 6))

assert np.allclose(
    separate_output,
    [[3.1, 0.0], [0.0, 2.3]],
)
assert np.allclose(fused_output, separate_output)
assert np.allclose(folded_output, linear_bn_output)
print("Operator fusion verification passed.")
```

### 3.2 输入输出说明

- `x` 的形状为 `(2, 3)`，表示两个样本，每个样本有三个输入特征。
- `weight` 的形状为 `(3, 2)`，将三个输入特征映射为两个输出特征。
- `bias`、`mean`、`variance`、`gamma` 和 `beta` 的形状均为 `(2,)`，分别对应两个输出特征。
- `separate_output` 和 `fused_output` 都应为 `[[3.1, 0.0], [0.0, 2.3]]`。
- `linear_bn_output` 与 `folded_output` 应在浮点误差范围内一致。

### 3.3 关键代码解释

`separate_affine_relu` 显式保留矩阵乘法和偏置加法的中间变量，表示融合前的计算图。`fused_affine_relu` 直接写出组合表达式，用来验证数学结果一致。

`fold_linear_batch_norm` 按输出特征计算 `scale`，然后分别缩放权重矩阵的列并修正偏置。NumPy 广播会将长度为 $M$ 的 `scale` 作用到形状为 $D\times M$ 的权重矩阵每一列。

最后三个断言分别检查已知输出、激活融合等价性和 BatchNorm 参数折叠等价性。若任一公式或实现有误，程序会抛出 `AssertionError`。

