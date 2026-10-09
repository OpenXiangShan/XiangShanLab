# Transformer

## 1. 文字讲解

### 1.1 这个算法解决什么问题

RNN 和 LSTM 处理序列有一个根本限制：**必须按时间步顺序计算**。第 $t$ 步的隐状态依赖第 $t-1$ 步的结果，无法并行化。序列越长，训练越慢。

更深层的困难是：RNN 中相隔 $n$ 步的两个 token 之间的信息路径长度为 $n$ ，长距离依赖难以建模。

Transformer（Vaswani et al., 2017）彻底抛弃了循环结构，用**自注意力机制**让序列中任意两个 token 直接交互，路径长度变为 $O(1)$。同时，由于没有时间步依赖，整个序列可以并行计算。

**输入**：token 嵌入序列 $X \in \mathbb{R}^{T \times d}$ （ $T$ 个 token，每个 $d$ 维）。

**输出**：上下文感知的表示序列 $Z \in \mathbb{R}^{T \times d}$ ，每个 token 的表示融合了整个序列的信息。

**适用边界**：机器翻译、文本生成、语言模型（BERT、GPT 均基于 Transformer）、语音识别、图像处理（ViT）。Transformer 已成为现代深度学习的通用架构。

### 1.2 核心思想

Transformer 的核心思想是**自注意力 + 并行计算**。

在自注意力中，每个 token 同时扮演三个角色：
- **Query（Q）**：我想找什么样的信息？
- **Key（K）**：我能提供什么样的信息？
- **Value（V）**：我实际携带的信息内容。

每个 token 的输出是所有 token 的 Value 的加权和，权重由"该 token 的 Query 与所有 token 的 Key 的相似度"决定。相似度越高，分配的权重越大。

直觉上理解：在一个会议室里，每个人（token）都同时提问（Q）和回答（K）。谁的回答与你的问题最相关，你就更多地采纳谁的信息（V）。这个过程是同时发生的——不需要排队，所有人并行完成。

为了引入位置信息（自注意力本身是位置无关的），Transformer 在输入嵌入上加上**位置编码**。

### 1.3 基本流程

一个完整的 Transformer 包含 **Encoder** 和 **Decoder** 两部分。

**Encoder Block**（堆叠 $N$ 次）：

1. **多头自注意力** + **残差连接** + **LayerNorm**：
$$Z = \text{LayerNorm}(X + \text{MultiHead}(X))$$
2. **前馈网络（FFN）** + **残差连接** + **LayerNorm**：
$$Z' = \text{LayerNorm}(Z + \text{FFN}(Z))$$

**Decoder Block**（堆叠 $N$ 次）：

1. **带掩码的多头自注意力** + 残差 + LayerNorm（掩码防止看到未来 token）
2. **交叉注意力**（Q 来自 Decoder，K/V 来自 Encoder 输出）+ 残差 + LayerNorm
3. **FFN** + 残差 + LayerNorm

### 1.4 直观例子

贯穿全章使用 2 个 token、2 维的例子：

- 输入嵌入： $X = \begin{bmatrix} 1 & 0 \\ 0 & 1 \end{bmatrix}$ （token 1 = $[1,0]$ ，token 2 = $[0,1]$ ）
- 令 $W_Q = W_K = W_V = \mathbf{I}$ （单位矩阵），则 $Q = K = V = X$

**注意力分数**（缩放点积）：

$$\text{scores} = \frac{Q K^T}{\sqrt{d_k}} = \frac{1}{\sqrt{2}} \begin{bmatrix} 1 & 0 \\ 0 & 1 \end{bmatrix} = \begin{bmatrix} 0.707 & 0 \\ 0 & 0.707 \end{bmatrix}$$

**Softmax 归一化**：

$$\text{attn} = \text{softmax}\!\left(\begin{bmatrix} 0.707 & 0 \\ 0 & 0.707 \end{bmatrix}\right) = \begin{bmatrix} 0.669 & 0.331 \\ 0.331 & 0.669 \end{bmatrix}$$

**加权求和**：

$$\text{out} = \text{attn} \cdot V = \begin{bmatrix} 0.669 & 0.331 \\ 0.331 & 0.669 \end{bmatrix} \begin{bmatrix} 1 & 0 \\ 0 & 1 \end{bmatrix} = \begin{bmatrix} 0.669 & 0.331 \\ 0.331 & 0.669 \end{bmatrix}$$

观察：token 1（ $[1,0]$ ）的输出是 $[0.669, 0.331]$ ——以自身信息为主（权重 0.669），但也融合了 token 2 的信息（权重 0.331）。这就是"上下文感知"：每个 token 的表示融入了其他 token 的信息。

## 2. 公式讲解

### 2.1 核心公式

**缩放点积注意力（Scaled Dot-Product Attention）：**

$$\text{Attention}(Q, K, V) = \text{softmax}\!\left(\frac{Q K^T}{\sqrt{d_k}}\right) V$$

其中 $Q \in \mathbb{R}^{T \times d_k}$ ， $K \in \mathbb{R}^{T \times d_k}$ ， $V \in \mathbb{R}^{T \times d_v}$ ， $d_k$ 是 Key 的维度。

**Q、K、V 的计算**：

$$Q = X W_Q, \quad K = X W_K, \quad V = X W_V$$

其中 $X \in \mathbb{R}^{T \times d}$ ， $W_Q, W_K \in \mathbb{R}^{d \times d_k}$ ， $W_V \in \mathbb{R}^{d \times d_v}$。

**为什么除以 $\sqrt{d_k}$ **：当 $d_k$ 较大时， $QK^T$ 的值会很大，导致 softmax 进入饱和区（梯度趋近零）。除以 $\sqrt{d_k}$ 将分数缩放到合理范围，使梯度稳定。

**多头注意力（Multi-Head Attention）**：

$$\text{MultiHead}(X) = \text{Concat}(\text{head}_1, \dots, \text{head}_h) W^O$$

$$\text{head}_i = \text{Attention}(X W_i^Q, X W_i^K, X W_i^V)$$

每个头独立学习不同子空间的注意力模式，最后拼接后线性投影回 $d$ 维。

**位置编码（Positional Encoding）**：

$$PE_{(pos, 2i)} = \sin\!\left(\frac{pos}{10000^{2i/d}}\right)$$

$$PE_{(pos, 2i+1)} = \cos\!\left(\frac{pos}{10000^{2i/d}}\right)$$

其中 $pos$ 是 token 在序列中的位置， $i$ 是维度索引。正弦/余弦的周期性使模型能泛化到不同长度的序列。

**前馈网络（FFN）**：

$$\text{FFN}(x) = \text{ReLU}(x W_1 + b_1) W_2 + b_2$$

两层线性变换中间插入 ReLU。FFN 对每个 token 独立施加相同的变换——相当于对注意力输出的"逐 token 非线性加工"。

**层归一化（LayerNorm）**：

$$\text{LayerNorm}(x) = \gamma \cdot \frac{x - \mu}{\sigma} + \beta$$

其中 $\mu$ 和 $\sigma$ 是 $x$ 在特征维度上的均值和标准差， $\gamma$ 和 $\beta$ 是可学习参数。LayerNorm 对每个 token 独立归一化，稳定深层网络的训练。

**残差连接 + LayerNorm（Add & Norm）**：

$$Z = \text{LayerNorm}(X + \text{Sublayer}(X))$$

这与 ResNet 的残差连接完全同构——子层的输出加上输入再归一化。残差连接保证梯度流，LayerNorm 防止数值发散。

**掩码注意力（Masked Attention）**：

$$\text{scores}_{\text{masked}} = \text{scores} + M$$

其中 $M$ 是掩码矩阵，上三角部分（未来位置）填 $-\infty$ ，其余填 0。softmax 后被掩码位置的权重变为 0，确保生成第 $t$ 个 token 时只能看到位置 $\leq t$ 的信息。

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $X$ | 输入嵌入序列， $T \times d$ |
| $Q, K, V$ | 查询、键、值矩阵，各 $T \times d_k$ （或 $T \times d_v$ ） |
| $W_Q, W_K, W_V$ | 投影权重矩阵， $d \times d_k$ （或 $d \times d_v$ ） |
| $d_k, d_v$ | Key/Value 的维度 |
| $d$ | 模型维度（ $d_{\text{model}}$ ） |
| $h$ | 注意力头数 |
| $W^O$ | 多头输出投影矩阵， $hd_v \times d$ |
| $PE$ | 位置编码矩阵， $T \times d$ |
| $pos$ | token 在序列中的位置（0, 1, ..., T-1） |
| $i$ | 维度索引（0, 1, ..., d/2-1） |
| $W_1, W_2$ | FFN 的两层权重矩阵 |
| $\gamma, \beta$ | LayerNorm 的可学习缩放和平移参数 |
| $\mu, \sigma$ | LayerNorm 中特征维度的均值和标准差 |
| $M$ | 掩码矩阵（上三角为 $-\infty$ ） |
| $T$ | 序列长度 |
| $N$ | Encoder/Decoder 块的堆叠层数 |

### 2.3 公式怎么理解

**自注意力**： $Q K^T$ 计算每对 token 之间的点积相似度——Query 和 Key 越相似，点积越大，注意力权重越高。除以 $\sqrt{d_k}$ 防止大维度下 softmax 饱和。softmax 将分数归一化为概率分布，再与 $V$ 相乘得到加权平均。关键特性：任意两个 token 之间的信息路径长度为 1（直接交互），不受序列长度影响。

**缩放因子**：点积 $q \cdot k = \sum_{i=1}^{d_k} q_i k_i$ 的期望方差为 $d_k$ （假设各分量独立、均值 0、方差 1）。因此点积的标准差为 $\sqrt{d_k}$。除以 $\sqrt{d_k}$ 使分数的方差稳定为 1，避免 softmax 进入梯度消失的饱和区。

**多头注意力**：将 $d$ 维向量分成 $h$ 组，每组 $d/h$ 维，每组独立做注意力。不同头可以学习不同模式——有的关注相邻 token，有的关注长距离依赖。最后拼接并线性投影回 $d$ 维。头数 $h$ 是超参数，原论文用 $h=8$。

**位置编码**：自注意力对位置完全无感——打乱 token 顺序，输出只是重新排列，内容不变。位置编码通过在每个位置的嵌入上加一个与位置相关的向量，让模型知道"每个 token 在哪里"。用正弦/余弦是因为它们有不同周期，不同维度可以编码不同粒度的位置信息。

**FFN**：注意力的输出是"信息的混合"，但没有做非线性变换。FFN 对每个 token 独立施加两层 MLP + ReLU，增加模型的表达能力。FFN 的隐层维度通常是 $4d$ （原论文 $d=512$ ，FFN 隐层 $2048$ ）。

**LayerNorm vs BatchNorm**：BatchNorm 在 batch 维度归一化（不同样本的同一特征），需要 batch 内统计，不适合序列（序列长度可变）。LayerNorm 在特征维度归一化（同一样本的所有特征），每个 token 独立计算，与 batch 无关，适合序列建模。

**残差连接**：与 ResNet 完全同构。 $X + \text{Sublayer}(X)$ 保证即使子层学不好，信息也能通过 $+X$ 直接传递。在堆叠 $N$ 层的 Transformer 中，残差连接是保证训练稳定的关键。

**掩码**：Decoder 在生成第 $t$ 个 token 时，不能看到位置 $> t$ 的 token（那是"未来"）。通过将注意力分数矩阵的上三角设为 $-\infty$ ，softmax 后这些位置的权重为 0，实现了"因果"约束。这就是自回归生成的核心——每次只看到已经生成的部分。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

np.random.seed(42)
d_model = 4
n_heads = 2
d_k = d_model // n_heads  # 2
seq_len = 3

# --- 输入 (3 tokens, 4D) ---
X = np.array([
    [1, 0, 0, 0],
    [0, 1, 0, 0],
    [1, 1, 0, 0],
], dtype=float)

# --- 激活函数 ---
def softmax(x, axis=-1):
    x = x - np.max(x, axis=axis, keepdims=True)
    return np.exp(x) / np.sum(np.exp(x), axis=axis, keepdims=True)

def relu(x):
    return np.maximum(0, x)

# --- 1. 缩放点积注意力 ---
def scaled_dot_product_attention(Q, K, V, mask=None):
    d_k = Q.shape[-1]
    scores = Q @ K.T / np.sqrt(d_k)
    if mask is not None:
        scores = scores + mask
    attn = softmax(scores, axis=-1)
    output = attn @ V
    return output, attn

# --- 2. 多头注意力 ---
def multi_head_attention(X, W_Q, W_K, W_V, W_O, n_heads):
    seq_len, d_model = X.shape
    d_k = d_model // n_heads
    Q = X @ W_Q  # (seq, d_model)
    K = X @ W_K
    V = X @ W_V
    # 重切分为多头: (seq, n_heads, d_k) -> (n_heads, seq, d_k)
    Q = Q.reshape(seq_len, n_heads, d_k).transpose(1, 0, 2)
    K = K.reshape(seq_len, n_heads, d_k).transpose(1, 0, 2)
    V = V.reshape(seq_len, n_heads, d_v := d_k).transpose(1, 0, 2)
    # 每个头独立注意力
    head_outputs = []
    for h in range(n_heads):
        out, _ = scaled_dot_product_attention(Q[h], K[h], V[h])
        head_outputs.append(out)
    # 拼接并投影
    concat = np.concatenate(head_outputs, axis=-1)  # (seq, d_model)
    return concat @ W_O

# --- 3. 位置编码 ---
def positional_encoding(seq_len, d_model):
    PE = np.zeros((seq_len, d_model))
    for pos in range(seq_len):
        for i in range(d_model // 2):
            PE[pos, 2*i]   = np.sin(pos / (10000 ** (2*i / d_model)))
            PE[pos, 2*i+1] = np.cos(pos / (10000 ** (2*i / d_model)))
    return PE

# --- 4. 前馈网络 ---
def feed_forward(x, W1, b1, W2, b2):
    return relu(x @ W1 + b1) @ W2 + b2

# --- 5. LayerNorm ---
def layer_norm(x, gamma=None, beta=None, eps=1e-6):
    mu = np.mean(x, axis=-1, keepdims=True)
    sigma = np.std(x, axis=-1, keepdims=True)
    if gamma is None: gamma = np.ones_like(mu)
    if beta is None: beta = np.zeros_like(mu)
    return gamma * (x - mu) / (sigma + eps) + beta

# --- 6. 因果掩码 ---
def causal_mask(seq_len):
    mask = np.zeros((seq_len, seq_len))
    for i in range(seq_len):
        for j in range(seq_len):
            if j > i:
                mask[i, j] = -1e9
    return mask

# === 验证 ===

# --- 权重初始化 ---
W_Q = np.eye(d_model)
W_K = np.eye(d_model)
W_V = np.eye(d_model)
W_O = np.eye(d_model)

# --- 1. 自注意力验证 ---
Q, K, V = X @ W_Q, X @ W_K, X @ W_V
attn_out, attn_weights = scaled_dot_product_attention(Q, K, V)
print("=== 自注意力 ===")
print(f"注意力权重:\n{np.round(attn_weights, 4)}")
print(f"输出:\n{np.round(attn_out, 4)}")

# 行和应为 1
assert np.allclose(attn_weights.sum(axis=-1), np.ones(seq_len), atol=1e-6)
print("注意力权重行和=1 ✓")

# --- 2. 多头注意力验证 ---
mha_out = multi_head_attention(X, W_Q, W_K, W_V, W_O, n_heads)
print(f"\n=== 多头注意力 ({n_heads} heads) ===")
print(f"输入形状: {X.shape}, 输出形状: {mha_out.shape}")
assert mha_out.shape == X.shape
# n_heads=1 时多头等价于单头（softmax 非线性导致多头≠单头）
single_head = multi_head_attention(X, W_Q, W_K, W_V, W_O, 1)
assert np.allclose(single_head, attn_out, atol=1e-6), "n_heads=1 时应等于单头"
print("多头注意力验证通过 ✓")

# --- 3. 位置编码验证 ---
PE = positional_encoding(seq_len, d_model)
print(f"\n=== 位置编码 ===")
print(f"PE:\n{np.round(PE, 4)}")
# 不同位置应有不同编码
assert not np.allclose(PE[0], PE[1]), "不同位置编码应不同"
# 加入位置编码后
X_pos = X + PE
print(f"X + PE:\n{np.round(X_pos, 4)}")

# --- 4. FFN 验证 ---
W1 = np.eye(d_model) * 2  # 放大
W2 = np.eye(d_model) * 0.5  # 缩小
b1 = np.zeros(d_model)
b2 = np.zeros(d_model)
ffn_out = feed_forward(X, W1, b1, W2, b2)
print(f"\n=== FFN ===")
print(f"输入:\n{X}")
print(f"输出 (ReLU(2x)*0.5):\n{ffn_out}")
# ReLU(2*[1,0,0,0])*0.5 = [1,0,0,0]
assert np.allclose(ffn_out[0], [1, 0, 0, 0])
assert np.allclose(ffn_out[2], [1, 1, 0, 0])  # ReLU(2*[1,1,0,0])*0.5
print("FFN 验证通过 ✓")

# --- 5. LayerNorm 验证 ---
ln_out = layer_norm(X)
print(f"\n=== LayerNorm ===")
print(f"输入:\n{X}")
print(f"输出:\n{np.round(ln_out, 4)}")
# 每行均值应为 0（近似）
assert np.allclose(np.mean(ln_out, axis=-1), 0, atol=1e-5)
# 每行标准差应为 1（近似）
assert np.allclose(np.std(ln_out, axis=-1), 1, atol=1e-2)
print("LayerNorm 均值≈0, 标准差≈1 ✓")

# --- 6. 掩码验证 ---
mask = causal_mask(seq_len)
print(f"\n=== 因果掩码 ===")
print(f"Mask:\n{mask}")
Q_d, K_d, V_d = X @ W_Q, X @ W_K, X @ W_V
masked_out, masked_attn = scaled_dot_product_attention(Q_d, K_d, V_d, mask=mask)
print(f"掩码后注意力权重:\n{np.round(masked_attn, 4)}")
# 上三角应为 0
for i in range(seq_len):
    for j in range(seq_len):
        if j > i:
            assert abs(masked_attn[i, j]) < 1e-6, f"({i},{j}) 应为 0"
print("上三角权重=0 ✓（token 只能看到当前位置及之前）")

# --- 7. 完整 Encoder Block ---
print(f"\n=== Encoder Block ===")
# Sublayer 1: Multi-Head Self-Attention + Add & Norm
attn_res = layer_norm(X + mha_out)
print(f"Self-Attn + Add&Norm 输出形状: {attn_res.shape}")
# Sublayer 2: FFN + Add & Norm
ffn_res = layer_norm(attn_res + feed_forward(attn_res, W1, b1, W2, b2))
print(f"FFN + Add&Norm 输出形状: {ffn_res.shape}")
assert ffn_res.shape == X.shape
print("Encoder Block 验证通过 ✓")

# --- 8. 完整 Decoder Block (带掩码) ---
print(f"\n=== Decoder Block (带掩码) ===")
# Sublayer 1: Masked Self-Attention + Add & Norm
dec_attn_out, _ = scaled_dot_product_attention(X @ W_Q, X @ W_K, X @ W_V, mask=mask)
dec_attn_res = layer_norm(X + dec_attn_out)
# Sublayer 2: Cross-Attention + Add & Norm (K,V from encoder)
enc_output = ffn_res  # encoder 最终输出
cross_out, _ = scaled_dot_product_attention(
    dec_attn_res @ W_Q,   # Q from decoder
    enc_output @ W_K,     # K from encoder
    enc_output @ W_V,     # V from encoder
)
cross_res = layer_norm(dec_attn_res + cross_out)
# Sublayer 3: FFN + Add & Norm
dec_out = layer_norm(cross_res + feed_forward(cross_res, W1, b1, W2, b2))
print(f"Decoder Block 输出形状: {dec_out.shape}")
assert dec_out.shape == X.shape
print("Decoder Block 验证通过 ✓")

# --- 9. 残差连接效果验证 ---
print(f"\n=== 残差连接效果 ===")
# 如果子层输出为 0，残差连接使输出 = LayerNorm(X)
zero_sublayer = np.zeros_like(X)
res_out = layer_norm(X + zero_sublayer)
print(f"子层输出为 0 时: 残差连接 → LayerNorm(X) ✓")
# 信息不会丢失
assert np.allclose(res_out, layer_norm(X), atol=1e-6)
print("残差连接验证通过 ✓")

print("\n========== Transformer 全部验证通过 ==========")
```

### 3.2 输入输出说明

**输入**：
- `X`： $3 \times 4$ 嵌入矩阵，3 个 token 各 4 维。token 1 = $[1,0,0,0]$ ，token 2 = $[0,1,0,0]$ ，token 3 = $[1,1,0,0]$。
- 权重： $W_Q = W_K = W_V = W_O = \mathbf{I}_4$ （单位矩阵，便于验证）。
- 2 个注意力头，每头 $d_k = 2$。

**自注意力输出**： $3 \times 4$ 矩阵，每行是对所有 token 的 Value 的加权和。注意力权重矩阵 $3 \times 3$ ，每行和为 1。

**多头注意力输出**：与单头注意力相同（因权重为单位矩阵），形状 $3 \times 4$。

**位置编码**： $3 \times 4$ 矩阵，不同位置有不同的正弦/余弦值。加到输入嵌入上使模型感知位置。

**FFN 输出**：`ReLU(2x) * 0.5`，对正输入放大后缩小，负输入截断为 0。

**LayerNorm 输出**：每行均值 $\approx 0$ 、标准差 $\approx 1$ ，消除不同 token 间的数值尺度差异。

**掩码注意力**：注意力权重矩阵上三角为 0，token 只能看到当前位置及之前的 token。

**Encoder/Decoder Block 输出**： $3 \times 4$ 矩阵，形状不变，但每个 token 的表示已融合了上下文信息并经过非线性变换。

### 3.3 关键代码解释

1. **`scaled_dot_product_attention`**：核心公式 $\text{softmax}(QK^T / \sqrt{d_k}) V$ 的直接实现。`scores = Q @ K.T / np.sqrt(d_k)` 计算缩放点积，`softmax` 按行归一化，再乘 $V$ 得到加权平均。可选 `mask` 参数在 softmax 前加到 scores 上。

2. **`multi_head_attention`**：将 $Q, K, V$ 重切分为 `(n_heads, seq_len, d_k)`，每个头独立做注意力，最后 `concatenate` 拼接并乘 $W^O$ 投影回 $d$ 维。这实现了"多子空间并行关注不同模式"的设计。

3. **`positional_encoding`**：双重循环为每个位置、每个维度计算正弦（偶数维）或余弦（奇数维）值。 $10000^{2i/d}$ 控制不同维度的周期——低维周期短（编码局部位置），高维周期长（编码全局位置）。

4. **`feed_forward`**：两层线性变换中间插入 ReLU，`x @ W1 + b1 → ReLU → @ W2 + b2`。对每个 token 独立施加相同变换——注意 FFN 不混合不同 token 的信息（那是注意力的事）。

5. **`layer_norm`**：`mu = mean(x)` 和 `sigma = std(x)` 在最后一个维度（特征维度）计算，然后 `(x - mu) / sigma * gamma + beta`。与 BatchNorm 不同，LayerNorm 不需要 batch 统计，每个 token 独立归一化。

6. **`causal_mask`**：生成上三角为 $-\infty$ （实际用 $-10^9$ ）、其余为 0 的矩阵。加到 scores 上后，softmax 使被掩码位置权重为 0，实现"看不到未来"的因果约束。

7. **Encoder Block**：`LayerNorm(X + MultiHead(X))` 然后是 `LayerNorm(Z + FFN(Z))`。残差连接保证信息直通，LayerNorm 稳定数值——两者缺一不可，堆叠 $N$ 层时尤其关键。

8. **Decoder Block**：比 Encoder 多一个掩码自注意力层和一个交叉注意力层。交叉注意力中 Q 来自 Decoder（"我要从 Encoder 的输出中找什么？"），K/V 来自 Encoder（"Encoder 提供什么信息？"），实现源序列到目标序列的信息传递。
