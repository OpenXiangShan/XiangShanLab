# 剪枝

## 1. 文字讲解

### 1.1 这个算法解决什么问题

神经网络通常包含大量参数，其中一部分参数对当前任务输出的影响较小，或者其作用能够被其他参数部分替代。这种现象可以理解为参数冗余。

剪枝（Pruning）根据某种重要性标准移除部分权重或完整结构，得到参数更少的子网络。它的输入通常是已经训练好的模型和目标剪枝率，输出是剪枝掩码以及应用掩码后的模型。

剪枝并不保证删除参数后模型精度不变。剪枝比例、重要性标准和后续微调都会影响最终结果，过度剪枝可能造成无法恢复的性能下降。

### 1.2 核心思想

剪枝的关键是回答两个问题：删除哪些参数，以及删除后如何恢复模型能力。

一种常见方法是**幅值剪枝**：把绝对值较小的权重视为较低优先级，先将它们置零。权重幅值是一种简单的重要性启发式，但小权重不一定在所有模型中都不重要。

按照剪除对象的粒度，剪枝可以分为两类：

- **非结构化剪枝**：独立删除单个权重，产生元素级稀疏模式。保留下来的非零权重位置可能不规则。
- **结构化剪枝**：以完整结构为单位删除参数，例如一整行权重、一个神经元、一个卷积通道或一个卷积核。

剪枝后通常要在保留的参数上继续训练，也就是微调。微调过程中需要保持被剪参数为零，让剩余参数适应新的网络结构。

### 1.3 基本流程

1. 训练一个未剪枝模型，得到参数 $W$。
2. 选择剪枝粒度，例如单个权重或完整通道。
3. 为权重或参数组计算重要性分数。
4. 根据目标剪枝率确定阈值，生成二值掩码 $M$。
5. 计算 $W'=M\odot W$，将被剪参数置零。
6. 评估剪枝后的模型，观察损失或精度变化。
7. 固定掩码并微调保留参数，恢复部分模型能力。
8. 根据需要重复“剪枝、微调”过程，逐步达到目标剪枝率。

一次直接剪到目标比例称为一次性剪枝；分多轮逐渐提高剪枝率称为迭代剪枝。本章代码使用一次性剪枝来展示核心计算。

### 1.4 直观例子

假设某层权重矩阵为：

$$

W=
\begin{bmatrix}
0.90 & 0.02 & -0.70 & 0.10\\
0.08 & -0.60 & 0.03 & 0.50\\
0.40 & -0.05 & 0.30 & -0.01
\end{bmatrix}.

$$

如果进行 $50\%$ 的非结构化幅值剪枝，删除绝对值最小的六个元素，可以得到：

$$

W'_{	ext{unstructured}}=
\begin{bmatrix}
0.90 & 0 & -0.70 & 0\\
0 & -0.60 & 0 & 0.50\\
0.40 & 0 & 0.30 & 0
\end{bmatrix}.

$$

非零元素分散在矩阵的不同位置。如果改为按行进行结构化剪枝，并删除 $L_2$ 范数最小的一行，则第三行被整体删除：

$$

W'_{	ext{structured}}=
\begin{bmatrix}
0.90 & 0.02 & -0.70 & 0.10\\
0.08 & -0.60 & 0.03 & 0.50\\
0 & 0 & 0 & 0
\end{bmatrix}.

$$

## 2. 公式讲解

### 2.1 核心公式

设权重张量为 $W$，二值剪枝掩码为 $M$，则剪枝后的权重为：

$$

W'=M\odot W,
\qquad M_{ij}\in\{0,1\}.

$$

基于阈值 $\tau$ 的非结构化幅值掩码可以写为：

$$

M_{ij}=
\begin{cases}
1, & |W_{ij}|\ge \tau,\\
0, & |W_{ij}|<\tau.
\end{cases}

$$

权重张量的稀疏率为：

$$

S=\frac{\sum_{i,j}\mathbb I(M_{ij}=0)}{N}.

$$

从约束优化角度，非结构化剪枝可以理解为在非零参数数量受限的条件下寻找低损失权重：

$$

\min_W\mathcal L(W)
\quad \text{s.t.}\quad
\|W\|_0\le K.

$$

结构化剪枝先把参数划分为若干组。若第 $g$ 组参数为 $W_g$，可以使用 $L_2$ 范数作为组重要性分数：

$$

s_g=\|W_g\|_2
=\sqrt{\sum_{k=1}^{n_g}W_{g,k}^2}.

$$

当 $s_g$ 低于结构化剪枝阈值 $\tau_g$ 时，整组共享同一个掩码值：

$$

M_g=
\begin{cases}
1, & s_g\ge \tau_g,\\
0, & s_g<\tau_g.
\end{cases}

$$

固定掩码后的微调更新可以表示为：

$$

W^{(t+1)}=
M\odot\left(
W^{(t)}-\eta\nabla_W\mathcal L(W^{(t)})
\right).

$$

### 2.2 变量含义

- $W$：剪枝前的权重张量。
- $W'$：应用掩码后的剪枝权重。
- $M$：与 $W$ 形状相同或可广播的二值掩码。
- $M_{ij}$：位置 $(i,j)$ 的元素级掩码。
- $\odot$：逐元素乘法。
- $\tau$：元素级重要性阈值。
- $S$：权重张量的稀疏率。
- $\mathbb I(\cdot)$：指示函数，条件成立时为 $1$，否则为 $0$。
- $N$：权重元素总数。
- $\mathcal L(W)$：模型在参数 $W$ 下的训练损失。
- $\|W\|_0$： $W$ 中非零元素的数量，并不是通常意义上的向量范数。
- $K$：允许保留的非零参数数量上限。
- $g$：参数组索引，例如行、神经元、通道或卷积核。
- $W_g$：第 $g$ 个参数组。
- $s_g$：第 $g$ 组的重要性分数。
- $n_g$：第 $g$ 组包含的参数数量。
- $W_{g,k}$：第 $g$ 组中的第 $k$ 个参数。
- $M_g$：第 $g$ 组共享的结构化掩码。
- $\tau_g$：结构化剪枝使用的组分数阈值。
- $t$：微调的迭代步数。
- $\eta$：微调学习率。
- $\nabla_W\mathcal L$：损失相对于权重的梯度。

### 2.3 公式怎么理解

掩码中的 $1$ 表示保留参数， $0$ 表示删除参数。将掩码与权重逐元素相乘，就能把被剪权重固定为零。

非结构化幅值剪枝比较每个 $|W_{ij}|$ 与阈值，因此同一行或同一通道中可能同时出现保留权重和零权重。结构化剪枝先计算整组分数，再让组内所有参数共享一个决策。

$\|W\|_0\le K$ 表明剪枝希望在限制非零参数数量的同时保持较低损失。实际的大模型很难直接求解这个组合优化问题，因此通常使用权重幅值、梯度或其他近似重要性分数构造掩码。

微调公式在普通梯度更新后再次乘以 $M$。这样被剪参数不会在训练中重新变成非零值，而保留参数可以继续调整，以减小剪枝造成的损失。微调通常只能恢复部分能力，是否能回到原始水平取决于剪枝强度和模型剩余容量。

## 3. 代码示例

### 3.1 最小可运行代码

下面先对一个小矩阵执行非结构化和按行结构化剪枝，再用线性回归示例展示固定掩码后的微调恢复。

```python
import numpy as np


def magnitude_unstructured_mask(weights, sparsity):
    flat_abs = np.abs(weights).ravel()
    prune_count = int(flat_abs.size * sparsity)
    order = np.argsort(flat_abs, kind="stable")
    mask_flat = np.ones(flat_abs.size, dtype=np.float64)
    mask_flat[order[:prune_count]] = 0.0
    return mask_flat.reshape(weights.shape)


def structured_row_mask(weights, prune_rows):
    row_scores = np.linalg.norm(weights, ord=2, axis=1)
    rows_to_prune = np.argsort(row_scores)[:prune_rows]
    mask = np.ones_like(weights, dtype=np.float64)
    mask[rows_to_prune, :] = 0.0
    return mask, row_scores


def mse(x, y, weights):
    error = x @ weights - y
    return np.mean(error ** 2)


def finetune_with_mask(x, y, weights, mask, steps=500, lr=0.1):
    weights = weights.copy()
    for _ in range(steps):
        error = x @ weights - y
        gradient = (2.0 / len(x)) * (x.T @ error)
        weights = mask * (weights - lr * gradient)
    return weights


weights = np.array([
    [0.90, 0.02, -0.70, 0.10],
    [0.08, -0.60, 0.03, 0.50],
    [0.40, -0.05, 0.30, -0.01],
])

unstructured_mask = magnitude_unstructured_mask(
    weights, sparsity=0.5
)
unstructured_weights = weights * unstructured_mask

row_mask, row_scores = structured_row_mask(weights, prune_rows=1)
structured_weights = weights * row_mask

print("original weights:\n", weights)
print("unstructured mask:\n", unstructured_mask)
print("unstructured result:\n", unstructured_weights)
print("row L2 scores:", np.round(row_scores, 4))
print("structured row mask:\n", row_mask)
print("structured result:\n", structured_weights)

# 使用一个小型线性回归任务演示剪枝后的微调。
rng = np.random.default_rng(7)
x = rng.normal(size=(200, 4))
target_weights = np.array([2.0, -1.0, 0.15, 0.05])
y = x @ target_weights

recovery_mask = magnitude_unstructured_mask(
    target_weights, sparsity=0.5
)
pruned_weights = target_weights * recovery_mask
loss_before_pruning = mse(x, y, target_weights)
loss_after_pruning = mse(x, y, pruned_weights)

recovered_weights = finetune_with_mask(
    x, y, pruned_weights, recovery_mask
)
loss_after_recovery = mse(x, y, recovered_weights)

print("recovery mask:", recovery_mask)
print("pruned regression weights:", pruned_weights)
print("recovered regression weights:", np.round(recovered_weights, 6))
print("loss before pruning:", round(loss_before_pruning, 8))
print("loss after pruning:", round(loss_after_pruning, 8))
print("loss after recovery:", round(loss_after_recovery, 8))

assert np.count_nonzero(unstructured_mask == 0) == 6
assert np.all(row_mask[2] == 0)
assert np.all(recovered_weights[recovery_mask == 0] == 0)
assert loss_after_recovery <= loss_after_pruning
```

### 3.2 输入输出说明

- `weights` 是形状为 `(3, 4)` 的示例权重矩阵。
- `unstructured_mask` 是同形状的元素级掩码，其中六个位置为零，对应 $50\%$ 稀疏率。
- `row_scores` 是三行权重的 $L_2$ 范数，形状为 `(3,)`。
- `row_mask` 将分数最小的第三行整体置零。
- `x` 是形状为 `(200, 4)` 的线性回归输入，`y` 是形状为 `(200,)` 的目标值。
- `recovery_mask` 保留四个回归权重中绝对值最大的两个。
- 三个 `loss` 分别表示剪枝前、剪枝后和固定掩码微调后的均方误差。

微调后，被剪除的两个回归权重仍然为零；保留的两个权重发生小幅调整，使恢复后的损失不高于刚完成剪枝时的损失。

### 3.3 关键代码解释

`magnitude_unstructured_mask` 将权重展平并按绝对值排序，然后把指定比例的最低幅值位置设为零。排序只用于生成掩码，权重本身的位置不会改变。

`structured_row_mask` 使用 `np.linalg.norm` 计算每一行的 $L_2$ 分数，并将最低分行的全部掩码设为零。真实模型可以按相同思路对神经元、通道或卷积核分组。

`finetune_with_mask` 在每次梯度更新后乘以固定掩码。这一步保证已剪权重不会重新生长，只优化仍被保留的参数。
