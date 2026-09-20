# MoE（混合专家模型）

## 1. 文字讲解

### 1.1 这个算法解决什么问题

普通神经网络中的同一层会对每个 token 使用同一组参数。扩大这一层时，参数量和每个 token 的计算量通常会一起增加。

MoE（Mixture of Experts，混合专家模型）把一层拆成多个“专家网络”，再由 Router（路由器）为每个 token 选择少量专家。这样可以增加模型的总参数量，同时只激活其中一部分参数。

在大语言模型中，专家通常是结构相同但参数不同的前馈网络。不同 token 可以被分配给不同专家。

### 1.2 核心思想

一个稀疏 MoE 层主要包含：

- **专家网络**：多个独立网络，每个专家都有自己的参数。
- **Router**：根据 token 表示计算它与各专家的匹配分数。
- **Top-K 选择**：只保留分数最高的 $K$ 个专家。
- **加权合并**：用路由权重合并被选专家的输出。

例如，一个 MoE 层共有 8 个专家，而 Top-K 取 2，那么每个 token 只经过其中 2 个专家，而不是全部 8 个。

### 1.3 基本流程

对每个输入 token，MoE 按以下步骤计算：

1. Router 计算该 token 对所有专家的分数。
2. Softmax 将分数转换为路由概率。
3. Top-K 选择概率最高的 $K$ 个专家，其余专家权重设为 0。
4. 被选中的专家分别处理该 token。
5. 按路由权重对专家输出求和。

训练时还要关注**负载均衡**。如果 Router 总把 token 分给少数专家，这些专家会过载，其他专家则得不到充分训练。常见做法是加入辅助损失，鼓励 token 更均匀地分配给各专家。

### 1.4 直观例子

设有三个专家，Router 为某个 token 输出的概率为：

$$

p(x)=\begin{bmatrix}0.6652 & 0.0900 & 0.2447\end{bmatrix}.

$$

当 Top-K 取 2 时，选择专家 1 和专家 3。示例将两项概率重新归一化，得到：

$$

g(x)=\begin{bmatrix}0.7311 & 0 & 0.2689\end{bmatrix}.

$$

如果两个专家的输出分别为：

$$

E_1(x)=\begin{bmatrix}1 & 0\end{bmatrix},\qquad
E_3(x)=\begin{bmatrix}0.5 & 1\end{bmatrix},

$$

那么 MoE 输出为：

$$

y=0.7311E_1(x)+0.2689E_3(x)
\approx\begin{bmatrix}0.8655 & 0.2689\end{bmatrix}.

$$

专家 2 没有被选中，因此不参与这个 token 的专家计算。

## 2. 公式讲解

### 2.1 核心公式

Router 首先计算专家分数和概率：

$$

z=xW_r,\qquad p_i(x)=\frac{\exp(z_i)}{\sum_{j=1}^{N}\exp(z_j)}.

$$

设 $\mathcal{T}_K(x)$ 是概率最高的 $K$ 个专家下标。本章示例使用以下稀疏门控权重：

$$

g_i(x)=
\begin{cases}
\displaystyle\frac{p_i(x)}{\sum_{j\in\mathcal{T}_K(x)}p_j(x)},
& i\in\mathcal{T}_K(x),\\
0,&i\notin\mathcal{T}_K(x).
\end{cases}

$$

MoE 层的输出为：

$$

y=\sum_{i=1}^{N}g_i(x)E_i(x)
=\sum_{i\in\mathcal{T}_K(x)}g_i(x)E_i(x).

$$

一种常见的 Top-1 负载均衡辅助损失是：

$$

L_{\text{balance}}=\alpha N\sum_{i=1}^{N}f_iP_i.

$$

### 2.2 变量含义

- $x\in\mathbb{R}^{d}$：一个 token 的输入表示。
- $W_r\in\mathbb{R}^{d\times N}$：Router 的可训练参数。
- $z_i$：Router 给第 $i$ 个专家的原始分数。
- $p_i(x)$：Softmax 后分配给第 $i$ 个专家的概率。
- $N$：专家总数。
- $K$：每个 token 选择的专家数量。
- $\mathcal{T}_K(x)$：输入 $x$ 对应的 Top-K 专家集合。
- $g_i(x)$：稀疏路由权重，未选中的专家权重为 0。
- $E_i(x)$：第 $i$ 个专家的输出。
- $y$：MoE 层的最终输出。
- $f_i$：一个批次中实际路由到专家 $i$ 的 token 比例。
- $P_i$：该批次中 Router 分配给专家 $i$ 的平均概率。
- $\alpha$：负载均衡损失的权重系数。

### 2.3 公式怎么理解

Router 本身不替代专家计算，它只决定“把 token 交给谁”。Top-K 将稠密概率变成稀疏权重，因此未被选中的专家不需要为该 token 计算输出。

输出公式仍然是专家结果的加权和。不同 token 的 Top-K 集合可以不同，所以各专家能够学习处理不同类型的输入。

负载均衡损失中的 $f_i$ 表示实际分配情况， $P_i$ 表示 Router 的平均偏好。当流量过度集中在少数专家时，这一辅助目标会产生惩罚。这里给出的是 Switch Transformer 使用的 Top-1 形式；不同 MoE 模型可能采用不同的平衡方法。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np


def softmax(x):
    x = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(x)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def sparse_moe(x, router_weight, expert_weights, top_k=2):
    router_probs = softmax(x @ router_weight)
    top_indices = np.argsort(
        -router_probs, axis=-1, kind="stable"
    )[:, :top_k]

    outputs = []
    sparse_gates = np.zeros_like(router_probs)

    for token_id, token in enumerate(x):
        selected = top_indices[token_id]
        gates = router_probs[token_id, selected]
        gates = gates / gates.sum()
        sparse_gates[token_id, selected] = gates

        token_output = np.zeros(expert_weights.shape[-1])
        for gate, expert_id in zip(gates, selected):
            token_output += gate * (token @ expert_weights[expert_id])
        outputs.append(token_output)

    return np.array(outputs), router_probs, sparse_gates, top_indices


tokens = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
])

router_weight = np.array([
    [2.0, 0.0, 1.0],
    [0.0, 2.0, 1.0],
])

expert_weights = np.array([
    [[1.0, 0.0], [0.0, 1.0]],
    [[2.0, 0.0], [0.0, 0.5]],
    [[0.5, 1.0], [1.0, 0.5]],
])

outputs, probs, gates, selected = sparse_moe(
    tokens, router_weight, expert_weights, top_k=2
)

expert_load = np.bincount(selected.ravel(), minlength=3)

np.set_printoptions(precision=4, suppress=True)
print("router probabilities:\n", probs)
print("selected experts:\n", selected + 1)
print("sparse gates:\n", gates)
print("MoE outputs:\n", outputs)
print("expert load:", expert_load)

assert np.allclose(gates.sum(axis=-1), 1.0)
assert np.count_nonzero(gates, axis=-1).tolist() == [2, 2]
```

### 3.2 输入输出说明

- `tokens` 包含两个二维 token。
- `router_weight` 将每个 token 映射为三个专家分数。
- `expert_weights` 保存三个线性专家的参数。
- `selected` 给出每个 token 选择的两个专家。
- `gates` 是 Top-K 后重新归一化的稀疏权重。
- `expert_load` 统计每个专家接收的 token 次数。

运行结果为：

```text
router probabilities:
 [[0.6652 0.09   0.2447]
 [0.09   0.6652 0.2447]]
selected experts:
 [[1 3]
 [2 3]]
sparse gates:
 [[0.7311 0.     0.2689]
 [0.     0.7311 0.2689]]
MoE outputs:
 [[0.8655 0.2689]
 [0.2689 0.5   ]]
expert load: [1 1 2]
```

### 3.3 关键代码解释

`softmax(x @ router_weight)` 计算每个 token 对所有专家的路由概率。

`np.argsort(... )[:, :top_k]` 选择概率最高的两个专家。代码随后只计算这些专家，并按重新归一化后的权重合并输出。

`expert_load` 展示专家接收 token 的次数。实际训练会在更大的批次上使用负载均衡目标，避免路由长期集中在少数专家。
