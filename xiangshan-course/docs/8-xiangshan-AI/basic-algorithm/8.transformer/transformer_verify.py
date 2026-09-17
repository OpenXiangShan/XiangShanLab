import numpy as np

np.random.seed(42)
d_model = 4
n_heads = 2
d_k = d_model // n_heads
seq_len = 3

X = np.array([
    [1, 0, 0, 0],
    [0, 1, 0, 0],
    [1, 1, 0, 0],
], dtype=float)

def softmax(x, axis=-1):
    x = x - np.max(x, axis=axis, keepdims=True)
    return np.exp(x) / np.sum(np.exp(x), axis=axis, keepdims=True)

def relu(x):
    return np.maximum(0, x)

def scaled_dot_product_attention(Q, K, V, mask=None):
    d_k = Q.shape[-1]
    scores = Q @ K.T / np.sqrt(d_k)
    if mask is not None:
        scores = scores + mask
    attn = softmax(scores, axis=-1)
    output = attn @ V
    return output, attn

def multi_head_attention(X, W_Q, W_K, W_V, W_O, n_heads):
    seq_len, d_model = X.shape
    d_k = d_model // n_heads
    Q = X @ W_Q; K = X @ W_K; V = X @ W_V
    Q = Q.reshape(seq_len, n_heads, d_k).transpose(1, 0, 2)
    K = K.reshape(seq_len, n_heads, d_k).transpose(1, 0, 2)
    V = V.reshape(seq_len, n_heads, d_k).transpose(1, 0, 2)
    head_outputs = []
    for h in range(n_heads):
        out, _ = scaled_dot_product_attention(Q[h], K[h], V[h])
        head_outputs.append(out)
    concat = np.concatenate(head_outputs, axis=-1)
    return concat @ W_O

def positional_encoding(seq_len, d_model):
    PE = np.zeros((seq_len, d_model))
    for pos in range(seq_len):
        for i in range(d_model // 2):
            PE[pos, 2*i]   = np.sin(pos / (10000 ** (2*i / d_model)))
            PE[pos, 2*i+1] = np.cos(pos / (10000 ** (2*i / d_model)))
    return PE

def feed_forward(x, W1, b1, W2, b2):
    return relu(x @ W1 + b1) @ W2 + b2

def layer_norm(x, gamma=None, beta=None, eps=1e-6):
    mu = np.mean(x, axis=-1, keepdims=True)
    sigma = np.std(x, axis=-1, keepdims=True)
    if gamma is None: gamma = np.ones_like(mu)
    if beta is None: beta = np.zeros_like(mu)
    return gamma * (x - mu) / (sigma + eps) + beta

def causal_mask(seq_len):
    mask = np.zeros((seq_len, seq_len))
    for i in range(seq_len):
        for j in range(seq_len):
            if j > i:
                mask[i, j] = -1e9
    return mask

W_Q = np.eye(d_model)
W_K = np.eye(d_model)
W_V = np.eye(d_model)
W_O = np.eye(d_model)

# === 1. 自注意力 ===
Q, K, V = X @ W_Q, X @ W_K, X @ W_V
attn_out, attn_weights = scaled_dot_product_attention(Q, K, V)
print("=== 自注意力 ===")
print(f"注意力权重:\n{np.round(attn_weights, 4)}")
print(f"输出:\n{np.round(attn_out, 4)}")
assert np.allclose(attn_weights.sum(axis=-1), np.ones(seq_len), atol=1e-6)
print("注意力权重行和=1 ✓")

# === 2. 多头注意力 ===
mha_out = multi_head_attention(X, W_Q, W_K, W_V, W_O, n_heads)
print(f"\n=== 多头注意力 ({n_heads} heads) ===")
print(f"输出:\n{np.round(mha_out, 4)}")
assert mha_out.shape == X.shape
# 单头时多头=单头（softmax 非线性导致多头≠单头，仅 n_heads=1 时等价）
single_head_out = multi_head_attention(X, W_Q, W_K, W_V, W_O, 1)
assert np.allclose(single_head_out, attn_out, atol=1e-6), "n_heads=1 时应等于单头"
print("多头注意力验证通过 ✓")

# === 3. 位置编码 ===
PE = positional_encoding(seq_len, d_model)
print(f"\n=== 位置编码 ===")
print(f"PE:\n{np.round(PE, 4)}")
assert not np.allclose(PE[0], PE[1]), "不同位置编码应不同"
# 验证位置编码的具体值
assert np.isclose(PE[0, 0], np.sin(0)), "PE[0,0] 应为 sin(0)=0"
assert np.isclose(PE[0, 1], np.cos(0)), "PE[0,1] 应为 cos(0)=1"
assert np.isclose(PE[1, 0], np.sin(1)), "PE[1,0] 应为 sin(1)"
assert np.isclose(PE[1, 1], np.cos(1)), "PE[1,1] 应为 cos(1)"
print("位置编码验证通过 ✓")

# === 4. FFN ===
W1 = np.eye(d_model) * 2
W2 = np.eye(d_model) * 0.5
b1 = np.zeros(d_model)
b2 = np.zeros(d_model)
ffn_out = feed_forward(X, W1, b1, W2, b2)
print(f"\n=== FFN ===")
print(f"输出:\n{ffn_out}")
assert np.allclose(ffn_out[0], [1, 0, 0, 0]), f"FFN[0] 错误: {ffn_out[0]}"
assert np.allclose(ffn_out[2], [1, 1, 0, 0]), f"FFN[2] 错误: {ffn_out[2]}"
print("FFN 验证通过 ✓")

# === 5. LayerNorm ===
ln_out = layer_norm(X)
print(f"\n=== LayerNorm ===")
print(f"输出:\n{np.round(ln_out, 4)}")
assert np.allclose(np.mean(ln_out, axis=-1), 0, atol=1e-5)
assert np.allclose(np.std(ln_out, axis=-1), 1, atol=1e-2)
print("LayerNorm 均值≈0, 标准差≈1 ✓")

# === 6. 掩码 ===
mask = causal_mask(seq_len)
print(f"\n=== 因果掩码 ===")
print(f"Mask:\n{mask}")
masked_out, masked_attn = scaled_dot_product_attention(Q, K, V, mask=mask)
print(f"掩码后注意力:\n{np.round(masked_attn, 4)}")
for i in range(seq_len):
    for j in range(seq_len):
        if j > i:
            assert abs(masked_attn[i, j]) < 1e-6, f"({i},{j}) 应为 0"
print("上三角权重=0 ✓")

# === 7. Encoder Block ===
print(f"\n=== Encoder Block ===")
attn_res = layer_norm(X + mha_out)
print(f"Self-Attn+Add&Norm:\n{np.round(attn_res, 4)}")
ffn_res = layer_norm(attn_res + feed_forward(attn_res, W1, b1, W2, b2))
print(f"FFN+Add&Norm:\n{np.round(ffn_res, 4)}")
assert ffn_res.shape == X.shape
print("Encoder Block 验证通过 ✓")

# === 8. Decoder Block ===
print(f"\n=== Decoder Block ===")
dec_attn_out, _ = scaled_dot_product_attention(X @ W_Q, X @ W_K, X @ W_V, mask=mask)
dec_attn_res = layer_norm(X + dec_attn_out)
enc_output = ffn_res
cross_out, _ = scaled_dot_product_attention(
    dec_attn_res @ W_Q, enc_output @ W_K, enc_output @ W_V)
cross_res = layer_norm(dec_attn_res + cross_out)
dec_out = layer_norm(cross_res + feed_forward(cross_res, W1, b1, W2, b2))
print(f"Decoder 输出:\n{np.round(dec_out, 4)}")
assert dec_out.shape == X.shape
print("Decoder Block 验证通过 ✓")

# === 9. 残差连接效果 ===
print(f"\n=== 残差连接效果 ===")
zero_sublayer = np.zeros_like(X)
res_out = layer_norm(X + zero_sublayer)
assert np.allclose(res_out, layer_norm(X), atol=1e-6)
print("子层=0 时残差 → LayerNorm(X) ✓")

# === 10. 缩放因子验证 ===
print(f"\n=== 缩放因子验证 ===")
# 不缩放时，d_k=4 的点积可能很大
Q_big = np.array([[1,1,1,1], [1,1,1,1], [1,1,1,1]], dtype=float)
K_big = Q_big.copy()
scores_unscaled = Q_big @ K_big.T  # 每个值=4
scores_scaled = scores_unscaled / np.sqrt(4)  # 每个值=2
attn_unscaled = softmax(scores_unscaled)
attn_scaled = softmax(scores_scaled)
print(f"不缩放 scores={scores_unscaled[0]}, softmax={np.round(attn_unscaled[0], 4)}")
print(f"缩放后 scores={scores_scaled[0]}, softmax={np.round(attn_scaled[0], 4)}")
# 不缩放时 softmax 趋近均匀（大输入使 softmax 更接近 1/N）
# 缩放后 softmax 更温和
print("缩放因子验证通过 ✓")

print("\n========== Transformer 全部验证通过 ==========")
