import numpy as np

np.random.seed(42)
d_model = 4

vocab = {
    "I":      [1, 0, 0, 0],
    "love":   [0, 1, 0, 0],
    "math":   [0, 0, 1, 0],
    "NLP":    [0, 0, 1, 1],
    "<tool_call>": [0, 0, 0, 1],
}
vocab_list = list(vocab.keys())
vocab_size = len(vocab_list)
word_to_emb = {w: np.array(v, dtype=float) for w, v in vocab.items()}

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

def causal_mask(seq_len):
    mask = np.zeros((seq_len, seq_len))
    for i in range(seq_len):
        for j in range(seq_len):
            if j > i: mask[i, j] = -1e9
    return mask

W_Q = np.eye(d_model) * 0.5
W_K = np.eye(d_model) * 0.5
W_V = np.eye(d_model) * 0.5
W1_ffn = np.eye(d_model) * 0.3

def gpt_decoder_block(X, seq_len_t):
    mask = causal_mask(seq_len_t)
    Q, K, V = X @ W_Q, X @ W_K, X @ W_V
    attn_out, attn = scaled_dot_product_attention(Q, K, V, mask=mask)
    Z = layer_norm(X + attn_out)
    ffn_out = relu(Z @ W1_ffn) @ W1_ffn
    return layer_norm(Z + ffn_out), attn

def generate(prompt_tokens, n_generate=3):
    tokens = list(prompt_tokens)
    print(f"提示词: {tokens}")
    for step in range(n_generate):
        X = np.array([word_to_emb[t] for t in tokens], dtype=float)
        T = len(tokens)
        PE = positional_encoding(T, d_model)
        X_input = X + PE
        Z, attn = gpt_decoder_block(X_input, T)
        last_repr = Z[-1]
        W_vocab = np.array([word_to_emb[w] for w in vocab_list], dtype=float)
        logits = last_repr @ W_vocab.T
        probs = softmax(logits)
        next_idx = np.argmax(probs)
        next_token = vocab_list[next_idx]
        tokens.append(next_token)
        print(f"  步 {step+1}: 输入 {tokens[:-1]} → 预测 '{next_token}' (p={probs[next_idx]:.4f})")
        if step == 0:
            print(f"  注意力（最后位置）: {np.round(attn[-1], 4)}")
    print(f"最终生成: {tokens}")
    return tokens

# === 1. 自回归生成 ===
print("=== GPT 自回归生成 ===")
generated = generate(["I", "love"], n_generate=3)

# === 2. 因果掩码验证 ===
print(f"\n=== 因果掩码验证 ===")
X_test = np.array([word_to_emb[w] for w in ["I", "love", "math", "NLP"]], dtype=float)
PE_test = positional_encoding(4, d_model)
Z_test, attn_test = gpt_decoder_block(X_test + PE_test, 4)
print(f"注意力矩阵:")
labels = ["I", "love", "math", "NLP"]
print(f"{'':>12} {'I':>8} {'love':>8} {'math':>8} {'NLP':>8}")
for i, label in enumerate(labels):
    row = "  ".join(f"{attn_test[i,j]:.4f}" for j in range(4))
    print(f"{label:>12} | {row}")
for i in range(4):
    for j in range(4):
        if j > i:
            assert abs(attn_test[i, j]) < 1e-4, f"({i},{j}) 应为 0"
print("上三角注意力=0 ✓")

# === 3. 自回归损失 ===
print(f"\n=== 自回归语言模型损失 ===")
sentence = ["I", "love", "math", "NLP"]
X_sent = np.array([word_to_emb[w] for w in sentence], dtype=float)
PE_sent = positional_encoding(4, d_model)
Z_sent, _ = gpt_decoder_block(X_sent + PE_sent, 4)
W_vocab = np.array([word_to_emb[w] for w in vocab_list], dtype=float)
total_loss = 0
print(f"句子: {sentence}")
for t in range(3):
    logits = Z_sent[t] @ W_vocab.T
    probs = softmax(logits)
    target_idx = vocab_list.index(sentence[t+1])
    loss = -np.log(probs[target_idx])
    total_loss += loss
    print(f"  位置 {t} ('{sentence[t]}') → 预测 '{sentence[t+1]}': p={probs[target_idx]:.4f}, loss={loss:.4f}")
avg_loss = total_loss / 3
print(f"平均损失: {avg_loss:.4f}")
assert avg_loss > 0

# === 4. BERT vs GPT 对比 ===
print(f"\n=== BERT vs GPT 注意力对比 ===")
_, attn_bert = scaled_dot_product_attention(X_test @ W_Q, X_test @ W_K, X_test @ W_V)
_, attn_gpt = scaled_dot_product_attention(
    X_test @ W_Q, X_test @ W_K, X_test @ W_V, mask=causal_mask(4))
print(f"位置 2 (math) 注意力:")
print(f"  BERT (双向): {np.round(attn_bert[2], 4)}  ← 能看到位置 3")
print(f"  GPT  (单向): {np.round(attn_gpt[2], 4)}  ← 看不到位置 3")
assert attn_bert[2, 3] > 0, "BERT 应看到右侧"
assert abs(attn_gpt[2, 3]) < 1e-4, "GPT 不应看到右侧"
print("双向 vs 单向 ✓")

# === 5. 统一接口 ===
print(f"\n=== 统一接口（prompt → 生成）===")
print("翻译任务:")
generate(["I", "love"], n_generate=2)
print("\n问答任务:")
generate(["I", "love"], n_generate=2)
print("→ 同一模型，不同 prompt 适配不同任务")

print("\n========== GPT 全部验证通过 ==========")
