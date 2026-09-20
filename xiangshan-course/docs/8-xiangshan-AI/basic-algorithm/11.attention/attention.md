# Attention（注意力机制）

## 1. 文字讲解

### 1.1 这个算法解决什么问题

Attention 解决的问题是：**面对多条输入信息，怎样根据当前查询动态判断每条信息的重要程度，并把重要信息汇总起来。**

它接收三类输入：

- Query（查询，记为 $Q$ ）：当前想寻找什么。
- Key（键，记为 $K$ ）：每条候选信息用于匹配的特征。
- Value（值，记为 $V$ ）：每条候选信息真正携带的内容。

输出是 Value 的加权和。同一组 Key 和 Value 面对不同 Query 时，会产生不同的权重和输出。

### 1.2 核心思想

Attention 可以理解为一次“带权检索”：

1. 用 Query 和每个 Key 计算匹配分数。
2. 对分数进行缩放，避免数值过大。
3. 用 Softmax 把分数变成非负且总和为 $1$ 的权重。
4. 用这些权重对 Value 加权求和。

Key 只负责“匹配”，Value 才是最后被汇总的“内容”。匹配程度越高，对应 Value 通常获得越大的权重。

### 1.3 基本流程

缩放点积注意力的计算顺序如下：

1. 准备 Query 矩阵 $Q$ 、Key 矩阵 $K$ 和 Value 矩阵 $V$。
2. 计算 $QK^\top$，得到所有 Query 与 Key 的点积匹配分数。
3. 将分数除以 $\sqrt{d_k}$，其中 $d_k$ 是 Query 和 Key 的维度。
4. 对每一行执行 Softmax，得到注意力权重。
5. 将权重矩阵与 $V$ 相乘，得到输出。

### 1.4 直观例子

设一个 Query 要从三条信息中提取内容：

$$

q=\begin{bmatrix}1 & 0\end{bmatrix},\qquad
K=\begin{bmatrix}
1 & 0\\
0 & 1\\
1 & 1
\end{bmatrix},\qquad
V=\begin{bmatrix}
1 & 0\\
0 & 2\\
3 & 1
\end{bmatrix}.

$$

Query 与第一、第三个 Key 的点积都是 $1$，与第二个 Key 的点积是 $0$。因此第一、第三个 Value 获得较高且相同的权重。最终输出不是直接选择某个 Value，而是将三个 Value 按权重混合。

## 2. 公式讲解

### 2.1 核心公式

缩放点积注意力的完整公式是：

$$

\operatorname{Attention}(Q,K,V)
=\operatorname{softmax}\left(\frac{QK^\top}{\sqrt{d_k}}\right)V.

$$

可以拆成三步：

$$

S=\frac{QK^\top}{\sqrt{d_k}},

$$

$$

A_{ij}=\frac{\exp(S_{ij})}{\sum_{r=1}^{n_k}\exp(S_{ir})},

$$

$$

O=AV.

$$

对于单个 Query，其输出为：

$$

o_i=\sum_{j=1}^{n_k}A_{ij}v_j.

$$

### 2.2 变量含义

- $Q\in\mathbb{R}^{n_q\times d_k}$：Query 矩阵，包含 $n_q$ 个 Query。
- $K\in\mathbb{R}^{n_k\times d_k}$：Key 矩阵，包含 $n_k$ 个 Key。
- $V\in\mathbb{R}^{n_k\times d_v}$：Value 矩阵，每个 Key 对应一个 Value。
- $d_k$：Query 和 Key 的向量维度。
- $S\in\mathbb{R}^{n_q\times n_k}$：缩放后的匹配分数矩阵。
- $A\in\mathbb{R}^{n_q\times n_k}$：Softmax 得到的注意力权重矩阵。
- $A_{ij}$：第 $i$ 个 Query 分配给第 $j$ 个 Value 的权重。
- $r$：Softmax 分母中的求和下标。
- $v_j$：第 $j$ 个 Value 向量。
- $O\in\mathbb{R}^{n_q\times d_v}$：最终输出矩阵， $o_i$ 是其第 $i$ 行。
- $\exp(\cdot)$：指数函数； $K^\top$ 表示 $K$ 的转置。

### 2.3 公式怎么理解

第一步 $QK^\top$ 一次计算所有 Query-Key 点积。点积越大，通常表示二者越匹配。

第二步除以 $\sqrt{d_k}$。当 $d_k$ 较大时，点积的绝对值容易变大，使 Softmax 过度集中；缩放可以缓和这一问题。

第三步对每一行执行 Softmax，使固定 Query 对应的所有权重满足：

$$

A_{ij}\geq 0,\qquad \sum_{j=1}^{n_k}A_{ij}=1.

$$

最后计算 $AV$，也就是按这些权重混合所有 Value。

将前面的例子代入， $d_k=2$，得到：

$$

S=\frac{qK^\top}{\sqrt{2}}
=\begin{bmatrix}0.7071 & 0 & 0.7071\end{bmatrix},

$$

$$

A\approx\begin{bmatrix}0.4011 & 0.1978 & 0.4011\end{bmatrix},

$$

$$

O=AV\approx\begin{bmatrix}1.6044 & 0.7967\end{bmatrix}.

$$

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np


def softmax(x):
    shifted = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(shifted)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def scaled_dot_product_attention(query, key, value):
    d_k = query.shape[-1]
    scores = query @ key.T / np.sqrt(d_k)
    weights = softmax(scores)
    output = weights @ value
    return output, weights, scores


Q = np.array([[1.0, 0.0]])
K = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
    [1.0, 1.0],
])
V = np.array([
    [1.0, 0.0],
    [0.0, 2.0],
    [3.0, 1.0],
])

output, weights, scores = scaled_dot_product_attention(Q, K, V)

np.set_printoptions(precision=4, suppress=True)
print("scores:", scores)
print("weights:", weights)
print("output:", output)

assert np.allclose(weights.sum(axis=-1), 1.0)
assert np.allclose(output, [[1.6044, 0.7967]], atol=1e-4)
```

### 3.2 输入输出说明

- `Q` 的形状是 `(1, 2)`，表示一个二维 Query。
- `K` 的形状是 `(3, 2)`，表示三个二维 Key。
- `V` 的形状是 `(3, 2)`，表示三个二维 Value。
- `scores` 和 `weights` 的形状都是 `(1, 3)`。
- `output` 的形状是 `(1, 2)`。

运行结果为：

```text
scores: [[0.7071 0.     0.7071]]
weights: [[0.4011 0.1978 0.4011]]
output: [[1.6044 0.7967]]
```

### 3.3 关键代码解释

`query @ key.T` 对应 $QK^\top$，计算 Query 与所有 Key 的点积。

`/ np.sqrt(d_k)` 对分数进行缩放。`softmax(scores)` 将每行分数转换为总和为 $1$ 的权重。

`weights @ value` 对应 $AV$，使用注意力权重对所有 Value 做加权求和。Softmax 实现先减去每行最大值，是为了降低指数计算溢出的风险，不会改变 Softmax 结果。
