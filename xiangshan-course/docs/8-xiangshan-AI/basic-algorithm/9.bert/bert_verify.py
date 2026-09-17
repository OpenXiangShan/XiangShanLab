import numpy as np

np.random.seed(42)
d_model = 4
seq_len = 4

vocab = {
    "I":      [1, 0, 0, 0],
    "love":   [0, 1, 0, 0],
    "math":   [0, 0, 1, 0],
    "NLP":    [0, 0, 1, 1],
    "<tool_call>": [0, 0, 0, 1],
}
vocab_list = list(vocab.keys())
vocab_size = len(vocab_list)

sentence = ["I", "love", "math", "NLP"]
X_original = np.array([vocab[w] for w in sentence], dtype=float)
mask_pos = 2
masked_token = "math"
X_masked = X_original.copy()
X_masked[mask_pos] = np.array(vocab["<tool_call>"])

print("=== BERT 掩码语言模型 ===")
print(f"掩码后: ['I', 'love', '<tool_call>', 'NLP']")

def softmax(x, axis=-1):
    x = x - np.max(x, axis=axis, keepdims=True)
    return np.exp(x) / np.sum(np.exp(x), axis=axis, keepdims=True)

def relu(x): return np.maximum(0, x)

def scaled_dot_product_attention(Q, K, V, mask=None):
    d_k = Q.shape[-1]
    scores = Q @ K.T / np.sqrt(d_k)
    if mask is not None: scores = scores + mask
    attn = softmax(scores, axis=-1)
    return attn @ V, attn

def layer_norm(x, eps=1e-6):
    mu = np.mean(x, axis=-1, keepdims=True)
    sigma = np.std(x, axis=-1, keepdims=True)
    return (x - mu) / (sigma + eps)

def positional_encoding(seq_len, d_model):
    PE = np.zeros((seq_len, d_model))
    for pos in range(seq_len):
        for i in range(d_model // 2):
            PE[pos, 2*i] = np.sin(pos / (10000 ** (2*i / d_model)))
            PE[pos, 2*i+1] = np.cos(pos / (10000 ** (2*i / d_model)))
    return PE

W_Q = np.eye(d_model) * 0.5
W_K = np.eye(d_model) * 0.5
W_V = np.eye(d_model) * 0.5
W_O = np.eye(d_model) * 0.5

def encoder_block(X):
    Q, K, V = X @ W_Q, X @ W_K, X @ W_V
    attn_out, attn = scaled_dot_product_attention(Q, K, V)
    Z = layer_norm(X + attn_out)
    W1 = np.eye(d_model) * 0.3
    ffn_out = relu(Z @ W1) @ W1
    return layer_norm(Z + ffn_out), attn

PE = positional_encoding(seq_len, d_model)
X_input = X_masked + PE
Z, attn_weights = encoder_block(X_input)

print(f"\n=== 双向注意力权重 ===")
labels = ["I", "love", "<tool_call>", "NLP"]
for i, label in enumerate(labels):
    row = "  ".join(f"{attn_weights[i,j]:.4f}" for j in range(seq_len))
    print(f"{label:>12} | {row}")

assert attn_weights[mask_pos].min() > 0, "<tool_call> 应关注所有位置"
print(f"\n<tool_call> 位置注意力: {attn_weights[mask_pos]}")
print("双向注意力 ✓")

W_mlm = np.array([vocab[w] for w in vocab_list], dtype=float).T
logits = Z[mask_pos] @ W_mlm
probs = softmax(logits)
print(f"\n=== MLM 预测 ===")
for i, word in enumerate(vocab_list):
    print(f"  {word:>10}: {probs[i]:.4f}")
predicted_idx = np.argmax(probs)
print(f"预测: '{vocab_list[predicted_idx]}'  真实: '{masked_token}'")

true_idx = vocab_list.index(masked_token)
mlm_loss = -np.log(probs[true_idx])
print(f"MLM 损失: {mlm_loss:.4f}")
assert mlm_loss > 0

# === BERT vs GPT 对比 ===
print(f"\n=== BERT vs GPT 注意力对比 ===")
causal = np.zeros((seq_len, seq_len))
for i in range(seq_len):
    for j in range(seq_len):
        if j > i: causal[i, j] = -1e9

_, attn_causal = scaled_dot_product_attention(
    X_input @ W_Q, X_input @ W_K, X_input @ W_V, mask=causal)

print(f"<tool_call> 位置注意力:")
print(f"  BERT (双向): {np.round(attn_weights[mask_pos], 4)}")
print(f"  GPT  (单向): {np.round(attn_causal[mask_pos], 4)}")
assert attn_weights[mask_pos, 3] > 0, "BERT 应看到右侧"
assert abs(attn_causal[mask_pos, 3]) < 0.01, "GPT 不应看到右侧"
print("双向 vs 单向 ✓")

# === 微调 ===
print(f"\n=== 微调：句子分类 ===")
cls_repr = Z[0]
W_cls = np.array([[1, -1], [1, -1], [0, 1], [0, -1]])
cls_logits = cls_repr @ W_cls
cls_probs = softmax(cls_logits)
print(f"分类概率: {np.round(cls_probs, 4)}")
assert abs(cls_probs.sum() - 1) < 1e-6
print("分类概率和=1 ✓")

# === 掩码策略 ===
print(f"\n=== 掩码策略 (80/10/10) ===")
def bert_mask(token, vocab_list, rng=None):
    if rng is None: rng = np.random.default_rng(42)
    r = rng.random()
    if r < 0.8: return "<tool_call>"
    elif r < 0.9: return rng.choice(vocab_list)
    else: return token

rng = np.random.default_rng(42)
results = {"<tool_call>": 0, "random": 0, "unchanged": 0}
for _ in range(20):
    out = bert_mask("math", vocab_list, rng)
    if out == "<tool_call>": results["<tool_call>"] += 1
    elif out == "math": results["unchanged"] += 1
    else: results["random"] += 1
print(f"20 次掩码: {results}")
print("掩码策略 ✓")

print("\n========== BERT 全部验证通过 ==========")
