# DeepSeek 系列核心算法

## 1. 文字讲解

### 1.1 这个算法解决什么问题

DeepSeek 不是一个单独的算法，而是一系列大语言模型。理解其技术主线，可以抓住三个部分：

- **DeepSeekMoE**：通过共享专家和路由专家扩大模型容量，同时让每个 token 只激活部分专家。
- **MLA**：将 Key 和 Value 联合压缩，减少自回归推理时需要保存的缓存内容。
- **DeepSeek-R1 训练流程**：使用强化学习提升数学、代码等任务中的推理能力。

DeepSeek-V2 引入并组合了 MLA 与 DeepSeekMoE；DeepSeek-V3 延续这两种架构，并加入无辅助损失的负载均衡策略和多 token 预测目标；DeepSeek-R1 则重点研究如何通过强化学习形成推理能力。

### 1.2 核心思想

**1. DeepSeekMoE**

普通稀疏 MoE 只使用 Router 选择专家。DeepSeekMoE 进一步采用：

- **细粒度专家划分**：将专家拆得更细，使路由组合更加灵活。
- **共享专家**：始终处理所有 token，用于承载通用知识。
- **路由专家**：每个 token 只选择其中一部分，用于学习更有差异的知识。

**2. MLA**

标准多头注意力需要缓存每个历史 token 的完整 Key 和 Value。MLA 先把二者压缩到同一个低维潜向量中，推理时主要缓存这个潜向量，需要计算注意力时再通过投影使用其中的信息。

完整 MLA 还使用解耦 RoPE：内容部分走低秩压缩路径，位置信息由单独的 Query/Key 分支携带，从而避免位置变换破坏低秩投影的合并。

**3. DeepSeek-R1**

DeepSeek-R1-Zero 直接在基础模型上进行大规模强化学习，没有先进行监督微调，表现出推理行为，但存在可读性和语言混杂问题。DeepSeek-R1 因此加入少量冷启动数据，并使用多阶段训练提高推理质量和回答可读性。

### 1.3 基本流程

DeepSeek 系列模型的核心计算与训练流程可以概括为：

1. Transformer 的注意力部分使用 MLA，压缩需要缓存的 Key/Value 内容。
2. 前馈网络部分使用 DeepSeekMoE，共享专家全部激活，Router 为每个 token 选择少量路由专家。
3. 基础模型先经过大规模预训练。
4. DeepSeek-R1 使用少量高质量推理样本进行冷启动监督微调。
5. 使用 GRPO 等强化学习方法进行推理训练，并通过可验证奖励判断答案正确性。
6. 通过拒绝采样收集较好的推理结果，与其他监督数据一起再次微调。
7. 再进行覆盖推理、帮助性和安全性等目标的强化学习。

GRPO 的具体目标函数将在后面的 GRPO 独立章节中讲解，本章只说明它在 R1 训练流程中的位置。

### 1.4 直观例子

以 MLA 的 Key/Value 联合压缩为例。假设单个 token 的完整 Key 和 Value 都是 4 维：

$$

k_t\in\mathbb{R}^{4},\qquad v_t\in\mathbb{R}^{4}.

$$

标准缓存需要为每个 token 保存 $ 4+4=8 $ 个数。若使用一个 2 维潜向量同时表示 Key 和 Value 的内容：

$$

c_t^{KV}\in\mathbb{R}^{2},

$$

教学示例中，每个 token 的内容缓存就从 8 个数变为 2 个数。对于 3 个 token，完整 Key/Value 共 24 个数，而压缩潜向量共 6 个数。

这只是 MLA 内容分支的简化示例。完整 MLA 还需要缓存解耦 RoPE 使用的位置 Key。

## 2. 公式讲解

### 2.1 核心公式

MLA 首先对 Key 和 Value 进行低秩联合压缩：

$$

c_t^{KV}=h_tW^{DKV},

$$

$$

k_t^{C}=c_t^{KV}W^{UK},\qquad
v_t^{C}=c_t^{KV}W^{UV}.

$$

其中 $ c_t^{KV} $ 是需要缓存的低维内容表示。完整 MLA 还将内容分支与携带 RoPE 的位置分支拼接：

$$

q_{t,i}=\left[q_{t,i}^{C};q_{t,i}^{R}\right],\qquad
k_{t,i}=\left[k_{t,i}^{C};k_t^{R}\right].

$$

标准多头注意力与 MLA 在每个 token、每层所需缓存的元素数量分别为：

$$

M_{\text{MHA}}=2n_hd_h,

$$

$$

M_{\text{MLA}}=d_c+d_h^{R}.

$$

DeepSeekMoE 的输出可以写成：

$$

h_t'=u_t
+\sum_{i=1}^{N_s}\operatorname{FFN}^{(s)}_i(u_t)
+\sum_{i=1}^{N_r}g_{i,t}\operatorname{FFN}^{(r)}_i(u_t).

$$

只有分数位于 Top-K 的路由专家具有非零权重：

$$

g_{i,t}=
\begin{cases}
s_{i,t},&s_{i,t}\in\operatorname{TopK}(s_{1,t},\ldots,s_{N_r,t}),\\
0,&\text{其他情况}.
\end{cases}

$$

### 2.2 变量含义

- $ h_t\in\mathbb{R}^{d} $：第 $ t $ 个 token 在注意力层的输入。
- $ W^{DKV}\in\mathbb{R}^{d\times d_c} $：Key/Value 联合压缩的降维矩阵。
- $ c_t^{KV}\in\mathbb{R}^{d_c} $：压缩后的 Key/Value 内容潜向量。
- $ W^{UK} $、$ W^{UV} $：从潜向量生成内容 Key 和 Value 的升维矩阵。
- $ k_t^{C} $、$ v_t^{C} $：内容 Key 和内容 Value。
- $ q_{t,i}^{C} $、$ k_{t,i}^{C} $：第 $ i $ 个注意力头的内容 Query 和内容 Key。
- $ q_{t,i}^{R} $、$ k_t^{R} $：应用 RoPE 的位置 Query 和共享位置 Key。
- $ n_h $：注意力头数量；$ d_h $：每个标准注意力头的维度。
- $ d_c $：MLA 的 Key/Value 压缩维度。
- $ d_h^{R} $：解耦 RoPE 分支的维度。
- $ u_t $：DeepSeekMoE 的输入。
- $ N_s $、$ N_r $：共享专家和路由专家的数量。
- $ \operatorname{FFN}^{(s)}_i $、$ \operatorname{FFN}^{(r)}_i $：第 $ i $ 个共享专家和路由专家。
- $ s_{i,t} $：token $ t $ 对路由专家 $ i $ 的匹配分数。
- $ g_{i,t} $：经过 Top-K 选择后的路由权重。
- $ h_t' $：DeepSeekMoE 层包含残差连接后的输出。

### 2.3 公式怎么理解

MLA 的关键不是分别压缩 Key 和 Value，而是让二者共享同一个低维潜向量 $ c_t^{KV} $。因此推理时不必保存每个头的完整内容 Key 和 Value。

RoPE 与位置有关，不能简单地与低秩升维矩阵合并。MLA 将位置部分拆出，使内容部分仍能保持低秩压缩。完整缓存由 $ c_t^{KV} $ 和位置 Key $ k_t^R $ 组成。

DeepSeekMoE 中，共享专家始终参与计算，路由专家则采用稀疏激活。这样既保留通用处理路径，又允许不同 token 调用不同的专门参数。

## 3. 代码示例

### 3.1 最小可运行代码

下面代码只演示 MLA 的低秩 Key/Value 联合压缩。为了突出核心思想，它省略了多头拆分、Query 压缩和解耦 RoPE，不是完整的 DeepSeek 模型实现。

```python
import numpy as np


def softmax(x):
    x = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(x)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def simplified_mla(x, w_down, w_up_k, w_up_v):
    latent_kv = x @ w_down
    key = latent_kv @ w_up_k
    value = latent_kv @ w_up_v
    query = x
    scores = query @ key.T / np.sqrt(query.shape[-1])
    weights = softmax(scores)
    output = weights @ value
    return output, weights, latent_kv, key, value


x = np.array([
    [1.0, 0.0, 1.0, 0.0],
    [0.0, 1.0, 0.0, 1.0],
    [1.0, 1.0, 0.0, 0.0],
])

w_down = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
    [1.0, 1.0],
    [1.0, -1.0],
])
w_up_k = np.array([
    [1.0, 0.0, 1.0, 0.0],
    [0.0, 1.0, 0.0, 1.0],
])
w_up_v = np.array([
    [1.0, 1.0, 0.0, 0.0],
    [0.0, 0.0, 1.0, 1.0],
])

output, weights, latent_kv, key, value = simplified_mla(
    x, w_down, w_up_k, w_up_v
)

standard_kv_elements = key.size + value.size
compressed_elements = latent_kv.size

np.set_printoptions(precision=4, suppress=True)
print("latent KV:\n", latent_kv)
print("attention weights:\n", weights)
print("output:\n", output)
print("standard KV elements:", standard_kv_elements)
print("compressed latent elements:", compressed_elements)

assert latent_kv.shape == (3, 2)
assert key.shape == value.shape == (3, 4)
assert np.allclose(weights.sum(axis=-1), 1.0)
assert compressed_elements < standard_kv_elements
```

### 3.2 输入输出说明

- `x` 包含三个 4 维 token。
- `w_down` 将每个 token 压缩为 2 维潜向量。
- `w_up_k` 和 `w_up_v` 从同一个潜向量生成 Key 和 Value。
- `latent_kv` 是教学示例真正需要缓存的内容。
- `standard_kv_elements` 统计完整 Key 和 Value 的元素数量。
- `compressed_elements` 统计压缩潜向量的元素数量。

运行结果为：

```text
latent KV:
 [[ 2.  1.]
 [ 1.  0.]
 [ 1.  1.]]
attention weights:
 [[0.5761 0.2119 0.2119]
 [0.4223 0.1554 0.4223]
 [0.5065 0.1863 0.3072]]
output:
 [[1.5761 1.5761 0.7881 0.7881]
 [1.4223 1.4223 0.8446 0.8446]
 [1.5065 1.5065 0.8137 0.8137]]
standard KV elements: 24
compressed latent elements: 6
```

### 3.3 关键代码解释

`x @ w_down` 对应 $ c_t^{KV}=h_tW^{DKV} $，把 Key 和 Value 的共同内容压缩到低维空间。

`latent_kv @ w_up_k` 和 `latent_kv @ w_up_v` 从同一潜向量生成内容 Key 和 Value。真实 MLA 在推理时可以进一步合并部分投影计算，但本例显式还原二者，便于观察数据形状。

示例将完整 Key/Value 的 24 个元素压缩为 6 个潜向量元素。完整 MLA 还需要保存解耦 RoPE 的位置 Key，因此实际缓存不能只按本示例的 6 个元素计算。
