# KV Cache

## 1. 文字讲解

### 1.1 这个算法解决什么问题

自回归模型一次生成一个 token。生成新 token 时，模型需要关注前面所有 token；如果每一步都重新计算完整序列，历史 token 的 Key 和 Value 会被反复计算。

KV Cache（Key-Value Cache）把已经计算过的 Key 和 Value 保存起来。下一步只计算新 token 的 Query、Key 和 Value，再将新的 Key、Value 追加到缓存中。

KV Cache 主要用于自回归推理。它减少重复计算，但需要额外空间保存每一层的历史 Key 和 Value。

### 1.2 核心思想

假设模型已经处理了前三个 token，并准备处理第四个 token：

- 不使用缓存：重新计算四个 token 的 Key 和 Value。
- 使用缓存：复用前三个 token 的 Key 和 Value，只计算第四个 token 的 Key 和 Value。

之所以可以复用，是因为在因果注意力中，历史 token 看不到未来 token。新 token 的到来不会改变已经得到的历史 Key 和 Value。

KV Cache 不缓存 Query。解码当前 token 时，只需要当前 Query 与全部缓存 Key 计算注意力分数。

### 1.3 基本流程

推理通常分成两个阶段：

**Prefill 阶段**：

1. 一次输入完整提示词。
2. 并行计算提示词中所有 token 的 Key 和 Value。
3. 将每一层的 Key 和 Value 写入缓存。
4. 使用最后一个位置的输出预测第一个新 token。

**Decode 阶段**：

1. 每次只输入最新生成的一个 token。
2. 计算该 token 的 Query、Key 和 Value。
3. 将新的 Key 和 Value 追加到缓存。
4. 当前 Query 读取全部缓存，计算注意力输出并预测下一个 token。
5. 重复以上过程，直到生成结束。

### 1.4 直观例子

设提示词包含三个 token，它们已经在 Prefill 阶段生成缓存：

$$

K_{\text{cache}}^{(3)}=
\begin{bmatrix}
k_1\\k_2\\k_3
\end{bmatrix},\qquad
V_{\text{cache}}^{(3)}=
\begin{bmatrix}
v_1\\v_2\\v_3
\end{bmatrix}.

$$

Decode 阶段收到第四个 token 后，只计算 $ q_4 $、$ k_4 $ 和 $ v_4 $，然后把 $ k_4 $、$ v_4 $ 追加到缓存。计算结果应与重新处理全部四个 token 完全一致。

若只统计 Key 和 Value 投影所处理的 token 数量，这一步中：

- 完整重算需要处理 $ 3+4=7 $ 个 token：Prefill 处理 3 个，Decode 时又处理 4 个。
- KV Cache 需要处理 $ 3+1=4 $ 个 token：Prefill 处理 3 个，Decode 时只处理新增的 1 个。

## 2. 公式讲解

### 2.1 核心公式

对第 $ t $ 个 token 的表示 $ x_t $，先计算：

$$

q_t=x_tW_Q,\qquad k_t=x_tW_K,\qquad v_t=x_tW_V.

$$

将新的 Key 和 Value追加到已有缓存：

$$

K_{\text{cache}}^{(t)}=
\operatorname{Concat}\left(K_{\text{cache}}^{(t-1)},k_t\right),

$$

$$

V_{\text{cache}}^{(t)}=
\operatorname{Concat}\left(V_{\text{cache}}^{(t-1)},v_t\right).

$$

当前 token 的注意力输出为：

$$

o_t=
\operatorname{softmax}\left(
\frac{q_t\left(K_{\text{cache}}^{(t)}\right)^\top}{\sqrt{d_k}}
\right)V_{\text{cache}}^{(t)}.

$$

### 2.2 变量含义

- $ x_t\in\mathbb{R}^{d_{\text{model}}} $：第 $ t $ 个 token 的输入表示。
- $ W_Q $、$ W_K $、$ W_V $：生成 Query、Key、Value 的投影矩阵。
- $ q_t $、$ k_t\in\mathbb{R}^{d_k} $：当前 token 的 Query 和 Key。
- $ v_t\in\mathbb{R}^{d_v} $：当前 token 的 Value。
- $ K_{\text{cache}}^{(t)}\in\mathbb{R}^{t\times d_k} $：包含位置 $ 1 $ 到 $ t $ 的 Key 缓存。
- $ V_{\text{cache}}^{(t)}\in\mathbb{R}^{t\times d_v} $：包含位置 $ 1 $ 到 $ t $ 的 Value 缓存。
- $ d_k $：Query 和 Key 的维度。
- $ o_t\in\mathbb{R}^{d_v} $：第 $ t $ 个位置的注意力输出。
- $ \operatorname{Concat} $：沿序列长度方向进行拼接。

实际模型具有多层和多头，因此每一层、每个注意力头都有各自的 Key 和 Value 缓存。

### 2.3 公式怎么理解

没有缓存时，第 $ t $ 步需要再次为整个前缀计算 Key 和 Value：

$$

K^{(t)}=X_{1:t}W_K,\qquad V^{(t)}=X_{1:t}W_V.

$$

使用缓存后，只计算新增 token 的 $ k_t $ 和 $ v_t $，历史部分直接复用。若 Prefill 长度为 $ p $，随后继续处理 $ m $ 个新 token，那么 Key/Value 投影处理的 token 数量可由重复累加变为：

$$

\text{不使用缓存：}\quad p+\sum_{i=1}^{m}(p+i),

$$

$$

\text{使用缓存：}\quad p+m.

$$

但计算 $ o_t $ 时，$ q_t $ 仍然要与缓存中的全部 Key 做点积。因此 KV Cache 消除的是历史 Key/Value 的重复计算，而不是消除对历史上下文的注意力计算。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np


def softmax(x):
    x = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(x)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def attention_last_token(x):
    """完整重算：为整个序列生成 K、V。"""
    q = x[-1:]
    key = x
    value = x
    scores = q @ key.T / np.sqrt(x.shape[-1])
    return softmax(scores) @ value


def prefill(prompt):
    """示例使用单位投影，因此 K、V 与输入相同。"""
    return prompt.copy(), prompt.copy()


def decode_with_cache(new_token, key_cache, value_cache):
    q_new = new_token
    k_new = new_token
    v_new = new_token
    key_cache = np.concatenate([key_cache, k_new], axis=0)
    value_cache = np.concatenate([value_cache, v_new], axis=0)
    scores = q_new @ key_cache.T / np.sqrt(q_new.shape[-1])
    output = softmax(scores) @ value_cache
    return output, key_cache, value_cache


prompt = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
    [1.0, 1.0],
])
new_token = np.array([[2.0, 1.0]])

key_cache, value_cache = prefill(prompt)
cached_output, key_cache, value_cache = decode_with_cache(
    new_token, key_cache, value_cache
)

full_sequence = np.concatenate([prompt, new_token], axis=0)
full_output = attention_last_token(full_sequence)

np.set_printoptions(precision=4, suppress=True)
print("cache shape:", key_cache.shape)
print("cached output:", cached_output)
print("full output:  ", full_output)
print("outputs equal:", np.allclose(cached_output, full_output))

assert key_cache.shape == (4, 2)
assert np.allclose(cached_output, full_output)
```

### 3.2 输入输出说明

- `prompt` 的形状是 `(3, 2)`，表示三个二维 token。
- `new_token` 的形状是 `(1, 2)`，表示 Decode 阶段的新 token。
- `key_cache` 和 `value_cache` 从 `(3, 2)` 增长为 `(4, 2)`。
- `cached_output` 使用 KV Cache 计算。
- `full_output` 重新处理完整序列，用于验证结果。

运行结果为：

```text
cache shape: (4, 2)
cached output: [[1.6616 0.9157]]
full output:   [[1.6616 0.9157]]
outputs equal: True
```

### 3.3 关键代码解释

`prefill(prompt)` 一次生成提示词的 Key 和 Value 缓存。示例使用单位投影矩阵，所以 Key、Value 与输入相同。

`np.concatenate` 把当前 token 的 Key 和 Value 追加到缓存。Decode 时只对 `new_token` 计算新的 Query、Key 和 Value。

`attention_last_token(full_sequence)` 重新计算完整序列。它与缓存版本输出相同，说明 KV Cache 改变的是计算过程，不是注意力的数学结果。
