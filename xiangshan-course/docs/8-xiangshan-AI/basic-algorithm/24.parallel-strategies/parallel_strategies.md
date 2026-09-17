# 并行策略

## 1. 文字讲解

### 1.1 并行策略解决什么问题

模型训练或推理包含大量张量计算。并行策略把数据、参数、网络层或专家划分成多个部分，让多个工作进程共同完成一次完整计算。

不同策略切分的对象不同：

- **数据并行**：切分输入批次，每个工作进程保留完整模型副本。
- **模型并行**：切分模型本身，不要求单个工作进程保存并执行全部模型。
- **张量并行**：在单个网络层内部切分权重或张量计算。
- **流水线并行**：按模型深度切分连续网络层，并使用微批次在不同阶段间流动。
- **专家并行**：在 MoE 模型中切分专家，并按路由结果把 token 发送给对应专家。

模型并行是一个上位概念。张量并行、流水线并行和专家并行都可以看作模型并行的具体形式，但它们分别沿张量维度、网络深度和专家维度切分模型。

### 1.2 数据并行

数据并行（Data Parallelism，DP）在每个工作进程中保存相同的模型参数，再把一个全局批次切成多个局部批次：

```text
全局批次
  |----> 工作进程 0：完整模型副本 -> 局部梯度 g0
  |----> 工作进程 1：完整模型副本 -> 局部梯度 g1
  |----> 工作进程 2：完整模型副本 -> 局部梯度 g2
                         |
                    聚合梯度并更新
```

每个工作进程独立完成前向传播和反向传播，然后聚合局部梯度。若所有模型副本从相同参数开始，并使用相同的聚合梯度更新，它们在更新后仍保持一致。

数据并行切分的是样本，不切分单层权重。它适合模型副本能够完整保存、同时希望处理更大全局批次的情况。

### 1.3 模型并行与张量并行

模型并行（Model Parallelism，MP）把模型参数和计算分给不同工作进程。例如，一个深层模型可以按层切分，一个大型线性层也可以按权重矩阵维度切分。

张量并行（Tensor Parallelism，TP）属于层内模型并行。以线性层 $Y=XW$ 为例，若沿权重矩阵的输出维切分：

```text
W = [W1 | W2]

工作进程 0：Y1 = XW1
工作进程 1：Y2 = XW2
合并结果：Y = [Y1 | Y2]
```

这称为列切分。每个分片生成一部分输出特征，最后按列拼接即可恢复完整输出。

若沿输入维切分 $X$ 和 $W$，每个工作进程得到一部分乘积，最后需要求和：

```text
X = [X1 | X2]       W = [W1; W2]

工作进程 0：P1 = X1W1
工作进程 1：P2 = X2W2
合并结果：Y = P1 + P2
```

因此，张量切分方式决定了后续是拼接分片，还是归约部分结果。

### 1.4 流水线并行

流水线并行（Pipeline Parallelism，PP）按网络深度把连续层划分为多个阶段：

```text
输入 -> 阶段 0 -> 中间激活 -> 阶段 1 -> 中间激活 -> 阶段 2 -> 输出
```

如果整个批次一次通过所有阶段，后面的阶段需要等待前面的阶段完成。流水线并行通常把批次继续切成多个微批次，使不同阶段可以在同一时刻处理不同微批次：

```text
时间 0：阶段 0 处理微批次 0
时间 1：阶段 0 处理微批次 1；阶段 1 处理微批次 0
时间 2：阶段 0 处理微批次 2；阶段 1 处理微批次 1
时间 3：阶段 1 处理微批次 2
```

流水线开始填充和结束排空时，会有部分阶段暂时没有可处理的微批次，这种空闲区间称为流水线气泡。训练时还需要安排反向传播，因此实际调度会比上面的前向示例更复杂。

### 1.5 专家并行

专家并行（Expert Parallelism，EP）用于 MoE 层。不同工作进程保存不同专家，路由器先为每个 token 选择专家，再执行四步：

1. 根据路由结果把 token 按专家分组。
2. 将每组 token 发送给持有目标专家的工作进程。
3. 各专家处理分配给自己的 token。
4. 将专家输出送回原 token 位置，并按路由权重合并。

```text
token 0、2 -> 专家 0 -> 输出回到位置 0、2
token 1、3 -> 专家 1 -> 输出回到位置 1、3
```

专家并行切分的是专家集合，不等于张量并行。一个专家内部仍然可以再使用张量并行；多个 MoE 层也可以放在不同流水线阶段。

### 1.6 通信对象与组合方式

理解并行策略时，除了看“切分什么”，还要看“需要重新组合什么”：

| 策略 | 主要切分对象 | 需要组合或传递的内容 |
|---|---|---|
| 数据并行 | 输入批次 | 聚合参数梯度 |
| 张量并行 | 单层权重或激活维度 | 拼接输出或归约部分结果 |
| 流水线并行 | 连续网络层 | 阶段之间传递激活及其梯度 |
| 专家并行 | 专家集合 | 按路由分发 token 并收集专家输出 |

实际模型可以组合多种策略。例如，先把模型按流水线阶段切分，再在每个阶段内使用张量并行，最后复制这组模型分片以形成数据并行组。MoE 层还可以增加专家并行维度。

组合并行时，每个工作进程可能同时属于多个通信组。并行度不能只看工作进程总数，还要明确每一维怎样切分、哪些参数被复制、哪些张量需要通信以及各组之间的对应关系。

### 1.7 直观例子

本章代码使用四个小例子：

- 将四个样本平均分给两个数据并行分片，验证局部梯度平均值等于全批次梯度。
- 将线性层权重按输出列切成两份，验证分片输出拼接后等于完整矩阵乘法。
- 将函数 $F(x)=2x+3$ 切成两个流水线阶段，并让三个微批次依次流过两个阶段。
- 把四个 token 分给两个专家，验证分组执行并放回原位置后等于逐 token 执行。

这些示例验证的是切分与重组的数学关系。它们在单进程中模拟多个分片，不包含真实分布式通信。

## 2. 公式讲解

### 2.1 数据并行的梯度聚合

设全局批次 $B$ 被平均切成 $P$ 个大小相同的局部批次 $B_1,\ldots,B_P$，模型参数为 $\theta$。第 $r$ 个工作进程的平均损失和局部梯度为：

$$
\mathcal L_r(\theta)
=
\frac{1}{|B_r|}
\sum_{(x,y)\in B_r}
\ell\left(f_\theta(x),y\right),
$$

$$
g_r=\nabla_\theta\mathcal L_r(\theta).
$$

全局平均损失和梯度为：

$$
\mathcal L(\theta)
=
\frac{1}{P}\sum_{r=1}^{P}\mathcal L_r(\theta),
$$

$$
g
=
\nabla_\theta\mathcal L(\theta)
=
\frac{1}{P}\sum_{r=1}^{P}g_r.
$$

使用学习率 $\eta$ 更新参数：

$$
\theta'=\theta-\eta g.
$$

上述简单平均要求每个局部批次大小相同，并且局部损失都采用样本平均。若各分片样本数不同，应按样本数加权，而不能直接平均局部梯度。

### 2.2 张量并行

设：

$$
X\in\mathbb R^{N\times D},
\qquad
W\in\mathbb R^{D\times M},
\qquad
Y=XW.
$$

沿 $W$ 的输出维做列切分：

$$
W=
\begin{bmatrix}
W_1 & W_2 & \cdots & W_P
\end{bmatrix}.
$$

每个分片独立计算：

$$
Y_r=XW_r.
$$

完整输出由各分片沿最后一维拼接：

$$
Y=
\operatorname{Concat}
\left(Y_1,Y_2,\ldots,Y_P\right).
$$

沿输入维做行切分时：

$$
X=
\begin{bmatrix}
X_1 & X_2 & \cdots & X_P
\end{bmatrix},
\qquad
W=
\begin{bmatrix}
W_1\\
W_2\\
\vdots\\
W_P
\end{bmatrix}.
$$

根据分块矩阵乘法：

$$
Y=XW=\sum_{r=1}^{P}X_rW_r.
$$

因此列切分需要拼接输出特征，行切分需要对部分乘积求和。

### 2.3 流水线并行

将模型划分为 $S$ 个连续阶段：

$$
F=F_S\circ F_{S-1}\circ\cdots\circ F_1.
$$

对于第 $m$ 个微批次 $x^{(m)}$，定义：

$$
h_0^{(m)}=x^{(m)},
$$

$$
h_s^{(m)}
=
F_s\left(h_{s-1}^{(m)}\right),
\qquad
s=1,2,\ldots,S.
$$

最终输出为：

$$
y^{(m)}=h_S^{(m)}.
$$

在只考虑前向传播、每个阶段耗时相同的简化模型中，$M$ 个微批次通过 $S$ 个阶段至少需要：

$$
T_{\text{slot}}=M+S-1
$$

个阶段时间槽。多出的 $S-1$ 个时间槽来自流水线的填充与排空。该公式是理想化调度模型，不包含阶段耗时不均、通信、反向传播和调度策略差异。

### 2.4 专家并行

设 MoE 层包含 $E$ 个专家函数 $E_i$，路由器为输入 token $x$ 计算分数 $s_i(x)$：

$$
p_i(x)
=
\frac{\exp(s_i(x))}
{\sum_{j=1}^{E}\exp(s_j(x))}.
$$

令 $\mathcal T_K(x)$ 表示概率最高的 $K$ 个专家索引。对选中概率重新归一化：

$$
\alpha_i(x)
=
\frac{p_i(x)}
{\sum_{j\in\mathcal T_K(x)}p_j(x)},
\qquad
i\in\mathcal T_K(x).
$$

MoE 输出为：

$$
y(x)
=
\sum_{i\in\mathcal T_K(x)}
\alpha_i(x)E_i(x).
$$

专家并行不会改变这个函数定义，它改变的是专家参数放置位置，以及 token 根据 $\mathcal T_K(x)$ 被分发和收集的过程。

### 2.5 变量含义

- $B$：全局训练批次。
- $B_r$：第 $r$ 个数据并行工作进程处理的局部批次。
- $P$：并行分片数量。
- $|B_r|$：局部批次中的样本数。
- $(x,y)$：一个输入与目标值样本。
- $\theta$、$\theta'$：更新前和更新后的模型参数。
- $f_\theta$：参数为 $\theta$ 的模型。
- $\ell$：单个样本的损失函数。
- $\mathcal L_r$、$\mathcal L$：局部平均损失和全局平均损失。
- $g_r$、$g$：局部梯度和聚合后的全局梯度。
- $\nabla_\theta$：对模型参数 $\theta$ 求梯度。
- $\eta$：学习率。
- $X$：线性层输入矩阵；$N$ 是样本数，$D$ 是输入特征数。
- $W$：线性层权重矩阵；$M$ 是输出特征数。
- $W_r$、$X_r$：第 $r$ 个权重或输入分片。
- $Y_r$、$Y$：局部输出分片和完整输出。
- $\operatorname{Concat}$：按照指定维度拼接张量。
- $F$：完整模型函数。
- $F_s$：流水线中的第 $s$ 个阶段。
- $S$：流水线阶段数量。
- $M$：微批次数量；它与线性层输出维度中的 $M$ 只在各自小节内使用。
- $x^{(m)}$、$y^{(m)}$：第 $m$ 个微批次的输入和输出。
- $h_s^{(m)}$：第 $m$ 个微批次经过第 $s$ 个阶段后的中间激活。
- $T_{\text{slot}}$：简化前向流水线需要的阶段时间槽数。
- $E$：MoE 专家总数。
- $E_i$：第 $i$ 个专家函数。
- $s_i(x)$：路由器给专家 $i$ 的未归一化分数。
- $p_i(x)$：Softmax 后专家 $i$ 的路由概率。
- $\mathcal T_K(x)$：输入 $x$ 选中的 Top-K 专家集合。
- $\alpha_i(x)$：选中专家重新归一化后的组合权重。
- $\circ$：函数复合运算符。

### 2.6 公式怎么理解

数据并行公式说明，等大小局部批次的平均梯度等于全局批次梯度。梯度聚合后，每个模型副本使用同一个 $g$ 更新，因此参数保持一致。

张量并行公式来自分块矩阵乘法。沿输出维切分时，每个分片负责不同输出列；沿输入维切分时，每个分片只得到同一输出的一部分贡献，所以必须求和。

流水线并行不改变模型函数 $F$，只是把函数复合拆成阶段，并让多个微批次交错经过这些阶段。微批次切分和最终重新拼接必须保持样本顺序。

专家并行也不改变 MoE 公式。路由器决定 token 与专家之间的稀疏对应关系，系统按照这个关系分组计算，再把结果放回原 token 位置。若使用 Top-K 路由，还要按 $\alpha_i(x)$ 合并多个专家输出。

## 3. 代码示例

### 3.1 最小可运行代码

下面用 NumPy 在一个进程中模拟数据并行、张量并行、流水线并行和专家并行。代码不创建分布式进程，而是重点验证各策略的切分与重组公式。

```python
import numpy as np


def scalar_half_squared_error_gradient(x, target, weight):
    prediction = weight * x
    return np.mean(x * (prediction - target))


def data_parallel_gradient(x, target, weight, shard_count):
    x_shards = np.array_split(x, shard_count)
    target_shards = np.array_split(target, shard_count)
    local_gradients = [
        scalar_half_squared_error_gradient(x_part, y_part, weight)
        for x_part, y_part in zip(x_shards, target_shards)
    ]
    return np.mean(local_gradients), local_gradients


def column_tensor_parallel(x, weight, shard_count):
    weight_shards = np.array_split(weight, shard_count, axis=1)
    output_shards = [x @ shard for shard in weight_shards]
    return np.concatenate(output_shards, axis=1)


def pipeline_schedule(stage_count, microbatch_count):
    slots = []
    for time_step in range(stage_count + microbatch_count - 1):
        active = []
        for stage in range(stage_count):
            microbatch = time_step - stage
            if 0 <= microbatch < microbatch_count:
                active.append((stage, microbatch))
        slots.append(active)
    return slots


def pipeline_forward(x, microbatch_count):
    microbatches = np.array_split(x, microbatch_count)
    outputs = []
    for microbatch in microbatches:
        stage_0_output = 2.0 * microbatch
        stage_1_output = stage_0_output + 3.0
        outputs.append(stage_1_output)
    return np.concatenate(outputs)


def expert_0(x):
    return 2.0 * x


def expert_1(x):
    return x + 10.0


def expert_parallel(tokens, routes):
    experts = [expert_0, expert_1]
    output = np.empty_like(tokens, dtype=np.float64)
    for expert_id, expert in enumerate(experts):
        token_indices = np.flatnonzero(routes == expert_id)
        output[token_indices] = expert(tokens[token_indices])
    return output


# 1. 数据并行：两个等大小分片的梯度平均
x_train = np.array([1.0, 2.0, 3.0, 4.0])
target = 2.0 * x_train
weight = 1.0
full_gradient = scalar_half_squared_error_gradient(
    x_train, target, weight
)
dp_gradient, local_gradients = data_parallel_gradient(
    x_train, target, weight, shard_count=2
)

# 2. 张量并行：按输出列切分线性层
x_linear = np.array([
    [1.0, 2.0, -1.0],
    [0.5, -1.0, 2.0],
])
linear_weight = np.array([
    [1.0, 0.0, 2.0, -1.0],
    [0.5, 1.0, -0.5, 2.0],
    [-1.0, 3.0, 1.0, 0.5],
])
dense_output = x_linear @ linear_weight
tp_output = column_tensor_parallel(
    x_linear, linear_weight, shard_count=2
)

# 3. 流水线并行：两个阶段和三个微批次
pipeline_input = np.arange(1.0, 7.0)
pipeline_output = pipeline_forward(
    pipeline_input, microbatch_count=3
)
direct_output = 2.0 * pipeline_input + 3.0
schedule = pipeline_schedule(
    stage_count=2, microbatch_count=3
)

# 4. 专家并行：按路由分组计算并恢复 token 顺序
tokens = np.array([1.0, 2.0, 3.0, 4.0])
routes = np.array([0, 1, 0, 1])
ep_output = expert_parallel(tokens, routes)
serial_output = np.array([
    expert_0(token) if route == 0 else expert_1(token)
    for token, route in zip(tokens, routes)
])

print("local gradients:", local_gradients)
print("full / data-parallel gradient:", full_gradient, dp_gradient)
print("tensor-parallel output:")
print(tp_output)
print("pipeline schedule:", schedule)
print("pipeline output:", pipeline_output)
print("expert-parallel output:", ep_output)

assert np.isclose(full_gradient, -7.5)
assert np.allclose(local_gradients, [-2.5, -12.5])
assert np.isclose(dp_gradient, full_gradient)
assert np.allclose(tp_output, dense_output)
assert schedule == [
    [(0, 0)],
    [(0, 1), (1, 0)],
    [(0, 2), (1, 1)],
    [(1, 2)],
]
assert np.allclose(pipeline_output, direct_output)
assert np.allclose(ep_output, [2.0, 12.0, 6.0, 14.0])
assert np.allclose(ep_output, serial_output)
print("Parallel strategies verification passed.")
```

### 3.2 输入输出说明

- `x_train` 包含四个训练样本，被平均切成两个局部批次。示例使用 $\ell=\frac{1}{2}(\hat y-y)^2$，因此单个样本对标量权重的梯度为 $x(\hat y-y)$。
- 两个局部梯度分别为 `-2.5` 和 `-12.5`，平均后为 `-7.5`，与完整批次梯度相同。
- `linear_weight` 的形状为 `(3, 4)`，代码沿输出维切成两个 `(3, 2)` 分片。
- `tp_output` 由两个局部输出拼接而成，与 `dense_output` 完全一致。
- `pipeline_input` 被分成三个微批次，依次经过 $F_1(x)=2x$ 和 $F_2(x)=x+3$。
- 两阶段、三微批次的简化流水线共有 $3+2-1=4$ 个时间槽。
- `routes` 把位置 `0、2` 分给专家 `0`，把位置 `1、3` 分给专家 `1`。
- `ep_output` 等于 `[2.0, 12.0, 6.0, 14.0]`，并保持原 token 顺序。

### 3.3 关键代码解释

`data_parallel_gradient` 先计算两个等大小局部批次的平均梯度，再对局部梯度求平均。这对应公式 $g=(g_1+g_2)/2$。若局部批次大小不同，代码需要改成按各分片样本数加权。

`column_tensor_parallel` 使用 `np.array_split` 沿权重矩阵第 `1` 维切分。每个分片与完整输入相乘，最后沿相同维度拼接局部输出。

`pipeline_schedule` 根据“微批次编号等于时间步减阶段编号”生成简化前向调度。`pipeline_forward` 顺序模拟数据经过两个阶段，断言用于确认阶段拆分不改变函数 $2x+3$。

`expert_parallel` 先使用路由结果找出每个专家负责的 token 下标，再批量调用该专家，并按原下标写回输出数组。这对应专家并行中的分发、专家计算和结果恢复三个核心步骤。

模型并行没有单独实现为一个函数，因为它是策略类别；代码中的张量并行和流水线并行分别展示了层内模型切分和按深度模型切分。
