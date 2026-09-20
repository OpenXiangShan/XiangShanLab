# BERT

## 1. 文字讲解

### 1.1 这个算法解决什么问题

Transformer 章节中我们实现了完整的 Encoder-Decoder 架构，但很多 NLP 任务并不需要"生成"——它们只需要**理解**。比如：

- 句子分类：这句话是正面还是负面？
- 命名实体识别：句子中哪些词是人名、地名？
- 问答：给定问题和文章，提取答案的起止位置。

这些"理解"任务的共同特点是：需要同时利用一个 token 左侧和右侧的上下文。比如判断" bank"是指银行还是河岸，必须看它周围的词。

BERT（Bidirectional Encoder Representations from Transformers, Devlin et al., 2018）只用 Transformer 的 **Encoder** 部分，通过**双向自注意力**让每个 token 同时看到左右两侧的上下文，学习到真正"理解"语义的表示。

**输入**：token 序列 $ X \in \mathbb{R}^{T \times d} $ （部分 token 被 [MASK] 替换）。

**输出**：每个 token 的上下文感知表示 $ Z \in \mathbb{R}^{T \times d} $，可用于分类、标注等下游任务。

**适用边界**：文本理解类任务（分类、NER、问答）。不适合文本生成——BERT 的双向注意力无法用于自回归生成（生成时未来 token 不存在）。

### 1.2 核心思想

BERT 的核心思想是**双向编码 + 掩码语言模型预训练**。

**双向 vs 单向**：GPT 使用 Decoder（因果掩码，只能看左侧），每个 token 只能利用之前的上下文。BERT 使用 Encoder（无掩码），每个 token 可以同时利用左右两侧的上下文。对于"理解"任务，右侧上下文往往同样重要——"I love [MASK] NLP"中，[MASK] 的正确答案需要同时看"love"（左）和"NLP"（右）。

**掩码语言模型（MLM）**：随机选 15% 的 token 替换为 [MASK]，让模型根据上下文预测被遮挡的原始 token。这就像完形填空——你必须理解全文才能填对空。

**预训练 + 微调**：
1. **预训练**：在海量无标注文本上做 MLM，学习通用语言表示。
2. **微调**：在预训练模型顶部加一个任务特定的输出层（如分类头），用少量标注数据微调所有参数。

直觉上理解：预训练相当于让模型先读大量书（学会语言规律），微调相当于针对特定考试做几套题。读书学到的通用理解能力，可以迁移到各种具体任务上。

### 1.3 基本流程

**预训练（MLM）**：

1. 输入序列，随机选 15% 的 token 做"遮挡处理"：
   - 80% 替换为 [MASK]
   - 10% 替换为随机 token
   - 10% 保持不变
2. 整个序列通过 Transformer Encoder（双向自注意力，无因果掩码）。
3. 在被遮挡的位置上，将 Encoder 输出投影到词表大小，做 softmax 分类。
4. 损失 = 被遮挡位置的交叉熵。

**微调**：

1. 取预训练的 BERT 模型。
2. 在 [CLS] token 的输出表示上加一个线性分类层。
3. 用标注数据训练（所有 BERT 参数 + 分类层参数一起微调）。

### 1.4 直观例子

贯穿全章使用一个 5 token 的小词汇表场景：

- 词汇表：`["I", "love", "math", "NLP", "[MASK]"]`，嵌入维度 $ d=4$
- 嵌入：`"I"=[1,0,0,0]`，`"love"=[0,1,0,0]`，`"math"=[0,0,1,0]`，`"NLP"=[0,0,1,1]`，`"[MASK]"=[0,0,0,1]`
- 原始句子：`["I", "love", "math", "NLP"]`
- 掩码后：`["I", "love", "[MASK]", "NLP"]`（第 3 个 token "math" 被遮挡）

**BERT Encoder 的双向注意力**：[MASK] 位置（index 2）的注意力权重可以分配到所有 4 个位置——包括左侧的"I"、"love"和右侧的"NLP"。这与 GPT 的因果掩码形成鲜明对比。

## 2. 公式讲解

### 2.1 核心公式

**BERT Encoder（无掩码自注意力）**：

$$ Z = \text{Encoder}(X + PE)$$

每个 token 的表示 $ Z_t $ 可以关注所有位置（无因果约束）。

**掩码语言模型（MLM）预测**：

$$ P(x_t \mid X_{\setminus t}) = \text{softmax}(Z_t \cdot W_{\text{vocab}} + b_{\text{vocab}})$$

其中 $ Z_t $ 是被遮挡位置 $ t $ 的 Encoder 输出，$ W_{\text{vocab}} \in \mathbb{R}^{d \times |V|} $ 是词表投影矩阵。

**MLM 损失**：

$$ L_{\text{MLM}} = -\sum_{t \in \text{masked}} \log P(x_t^* \mid X_{\setminus t})$$

其中 $ x_t^* $ 是被遮挡位置 $ t $ 的真实 token。

**下一句预测（NSP）损失**（BERT 原始预训练的辅助任务）：

$$ L_{\text{NSP}} = -\log P(\text{IsNext} \mid [\text{CLS}])$$

判断两个句子是否是原文中相邻的句子。后来的研究（RoBERTa）发现 NSP 任务并非必要。

**微调（分类任务）**：

$$ \hat{y} = \text{softmax}(Z_{[\text{CLS}]} \cdot W_{\text{cls}} + b_{\text{cls}})$$

$$ L = -\log P(y \mid [\text{CLS}])$$

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $ X $ | 输入 token 嵌入序列（部分被 [MASK] 替换） |
| $ PE $ | 位置编码 |
| $ Z $ | Encoder 输出（上下文感知表示） |
| $ Z_t $ | 位置 $ t $ 的表示（编码了全局上下文） |
| $ W_{\text{vocab}} $ | MLM 预测头，投影到词表维度 |
| $ \|V\| $ | 词汇表大小 |
| $ x_t^* $ | 被遮挡位置 $ t $ 的真实 token |
| $ X_{\setminus t} $ | 除位置 $ t $ 外的完整输入 |
| $ Z_{[\text{CLS}]} $ | [CLS] token 的输出表示，用于分类 |
| $ W_{\text{cls}} $ | 分类头权重 |

### 2.3 公式怎么理解

**双向注意力**：BERT 使用 Transformer Encoder（无因果掩码），因此 $ Z_t = f(X_1, X_2, \dots, X_T) $ ——位置 $ t $ 的表示融合了整个序列的信息。对比 GPT 的 Decoder：$ \hat{Z}_t = f(X_1, \dots, X_t) $ ——只能看到左侧。对于理解任务（分类、问答），双向信息通常带来更好的性能；对于生成任务，双向注意力无法使用（因为生成时右侧 token 尚未产生）。

**MLM 的遮挡策略**：不是简单的 100% 替换为 [MASK]。80% 替换 [MASK]、10% 随机替换、10% 保持不变，是为了让模型不只学会"看到 [MASK] 就预测"，而是对每个位置都学习有意义的表示。如果 100% 替换为 [MASK]，模型可能在非 [MASK] 位置"偷懒"。

**[CLS] token**：BERT 在序列开头加一个特殊的 [CLS] token，其最终表示 $ Z_{[\text{CLS}]} $ 用于分类。因为 [CLS] 通过自注意力可以汇聚整个序列的信息，相当于一个"全局摘要"。

**预训练 → 微调的参数效率**：BERT-Base 有约 1.1 亿参数，预训练一次后可以在各种下游任务上微调——只需少量标注数据和几个 epoch 就能达到很好的效果。这是因为预训练学到的语言表示是通用的，微调只是做任务适配。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

np.random.seed(42)
d_model = 4
seq_len = 4

# --- 词汇表和嵌入 ---
vocab = {
    "I":      [1, 0, 0, 0],
    "love":   [0, 1, 0, 0],
    "math":   [0, 0, 1, 0],
    "NLP":    [0, 0, 1, 1],
    "[MASK]": [0, 0, 0, 1],
}
vocab_list = list(vocab.keys())
vocab_size = len(vocab_list)

# --- 原始句子 ---
sentence = ["I", "love", "math", "NLP"]
X_original = np.array([vocab[w] for w in sentence], dtype=float)

# --- 掩码 ---
mask_pos = 2  # "math" -> "[MASK]"
masked_token = "math"

X_masked = X_original.copy()
X_masked[mask_pos] = np.array(vocab["[MASK]"])

print("=== BERT 掩码语言模型 ===")
print(f"原始: {sentence}")
print(f"掩码后: {['I', 'love', '[MASK]', 'NLP']}")
print(f"目标: 预测 [MASK] = '{masked_token}'")

# --- Transformer Encoder（复用 Transformer 章的组件）---
def softmax(x, axis=-1):
    x = x - np.max(x, axis=axis, keepdims=True)
    return np.exp(x) / np.sum(np.exp(x), axis=axis, keepdims=True)

def relu(x): return np.maximum(0, x)

def scaled_dot_product_attention(Q, K, V, mask=None):
    d_k = Q.shape[-1]
    scores = Q @ K.T / np.sqrt(d_k)
    if mask is not None:
        scores = scores + mask
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
    # Self-Attention（无掩码 = 双向）
    Q, K, V = X @ W_Q, X @ W_K, X @ W_V
    attn_out, attn = scaled_dot_product_attention(Q, K, V)  # 无 mask!
    Z = layer_norm(X + attn_out)
    # FFN
    W1 = np.eye(d_model) * 0.3
    ffn_out = relu(Z @ W1) @ W1
    return layer_norm(Z + ffn_out), attn

# --- 1. BERT 编码（双向注意力）---
PE = positional_encoding(seq_len, d_model)
X_input = X_masked + PE
Z, attn_weights = encoder_block(X_input)

print(f"\n=== 双向注意力权重 ===")
print(f"{'':>12} {'I':>8} {'love':>8} {'[MASK]':>8} {'NLP':>8}")
labels = ["I", "love", "[MASK]", "NLP"]
for i, label in enumerate(labels):
    row = "  ".join(f"{attn_weights[i,j]:.4f}" for j in range(seq_len))
    print(f"{label:>12} | {row}")

# [MASK] 位置应能关注到所有位置
assert attn_weights[mask_pos].min() > 0, "[MASK] 应关注所有位置（双向）"
print(f"\n[MASK] 位置 (index {mask_pos}) 注意力分布: {attn_weights[mask_pos]}")
print("→ 双向注意力：[MASK] 能看到左右所有 token ✓")

# --- 2. MLM 预测 ---
# MLM 预测头：将 d_model 维投影到 vocab_size
W_mlm = np.array([
    vocab["I"], vocab["love"], vocab["math"], vocab["NLP"], vocab["[MASK]"]
], dtype=float).T  # (d_model, vocab_size)

logits = Z[mask_pos] @ W_mlm  # (vocab_size,)
probs = softmax(logits)
print(f"\n=== MLM 预测 ===")
print(f"[MASK] 位置的 logits: {np.round(logits, 4)}")
print(f"概率分布:")
for i, word in enumerate(vocab_list):
    print(f"  {word:>10}: {probs[i]:.4f}")
predicted_idx = np.argmax(probs)
print(f"预测: '{vocab_list[predicted_idx]}'  真实: '{masked_token}'")

# --- 3. MLM 损失 ---
true_idx = vocab_list.index(masked_token)
mlm_loss = -np.log(probs[true_idx])
print(f"\nMLM 损失: {mlm_loss:.4f}")
assert mlm_loss > 0, "损失应为正"

# --- 4. 对比：BERT 双向 vs GPT 单向 ---
print(f"\n=== BERT vs GPT 注意力对比 ===")
# GPT 的因果掩码
causal = np.zeros((seq_len, seq_len))
for i in range(seq_len):
    for j in range(seq_len):
        if j > i: causal[i, j] = -1e9

Z_gpt, attn_gpt = encoder_block(X_input)  # 重新计算（参数共享）
_, attn_causal = scaled_dot_product_attention(
    X_input @ W_Q, X_input @ W_K, X_input @ W_V, mask=causal)

print(f"[MASK] 位置注意力:")
print(f"  BERT (双向): {np.round(attn_weights[mask_pos], 4)}")
print(f"  GPT  (单向): {np.round(attn_causal[mask_pos], 4)}")
print(f"  BERT 能看到右侧 'NLP': {'是' if attn_weights[mask_pos, 3] > 0 else '否'}")
print(f"  GPT 能看到右侧 'NLP': {'是' if attn_causal[mask_pos, 3] > 0.01 else '否'}")
assert attn_weights[mask_pos, 3] > 0, "BERT 应能看到右侧"
assert attn_causal[mask_pos, 3] < 0.01, "GPT 不应看到右侧"
print("双向 vs 单向对比验证通过 ✓")

# --- 5. 微调（分类任务）模拟 ---
print(f"\n=== 微调：句子分类 ===")
# 用 [CLS] 位置（这里用 index 0）的输出做分类
cls_repr = Z[0]  # "I" 的表示作为全局摘要
W_cls = np.array([[1, -1], [1, -1], [0, 1], [0, -1]])  # 2类分类
cls_logits = cls_repr @ W_cls
cls_probs = softmax(cls_logits)
print(f"[CLS] 表示: {np.round(cls_repr, 4)}")
print(f"分类 logits: {np.round(cls_logits, 4)}")
print(f"分类概率: {np.round(cls_probs, 4)}")
assert abs(cls_probs.sum() - 1) < 1e-6
print("分类概率和为 1 ✓")

# --- 6. 掩码策略验证 ---
print(f"\n=== 掩码策略 (80/10/10) ===")
def bert_mask(token, vocab_list, rng=None):
    """80% [MASK], 10% 随机, 10% 不变"""
    if rng is None: rng = np.random.default_rng(42)
    r = rng.random()
    if r < 0.8:
        return "[MASK]"
    elif r < 0.9:
        return rng.choice(vocab_list)
    else:
        return token

# 模拟 20 次掩码
rng = np.random.default_rng(42)
results = {"[MASK]": 0, "random": 0, "unchanged": 0}
for _ in range(20):
    out = bert_mask("math", vocab_list, rng)
    if out == "[MASK]": results["[MASK]"] += 1
    elif out == "math": results["unchanged"] += 1
    else: results["random"] += 1

print(f"20 次掩码结果: {results}")
print(f"比例: MASK={results['[MASK]']/20:.0%}, "
      f"随机={results['random']/20:.0%}, "
      f"不变={results['unchanged']/20:.0%}")
print("掩码策略验证通过 ✓")

print("\n========== BERT 全部验证通过 ==========")
```

### 3.2 输入输出说明

**输入**：
- 句子 `["I", "love", "math", "NLP"]`，4 个 token，4 维嵌入。
- 第 3 个 token "math" 被替换为 "[MASK]"。
- 词汇表 5 个词，每个 4 维嵌入。

**双向注意力输出**：
- [MASK] 位置的注意力权重分布到所有 4 个位置（包括右侧的 "NLP"），证明双向性。
- 与 GPT 的因果掩码对比：GPT 中 [MASK] 位置右侧权重为 0。

**MLM 预测**：
- [MASK] 位置的 Encoder 输出投影到 5 维词表空间，softmax 得到概率分布。
- 损失为被遮挡位置真实 token 的负对数似然。

**分类微调**：
- [CLS]（这里用 "I" 位置）的输出通过线性层投影到 2 类，softmax 得到分类概率。

### 3.3 关键代码解释

1. **`encoder_block`**：与 Transformer 章的 Encoder Block 相同，但关键区别是 `scaled_dot_product_attention(Q, K, V)` **不传 mask 参数**——没有因果掩码，每个位置可以关注所有位置，这就是"双向"的代码体现。

2. **MLM 预测**：`Z[mask_pos] @ W_mlm` 将被遮挡位置的 4 维表示投影到词表维度，再 softmax 得到每个词的概率。`W_mlm` 的每一列是一个词的嵌入——预测本质上是"被遮挡位置的表示与哪个词的嵌入最相似"。

3. **双向 vs 单向对比**：同一个输入分别通过无掩码（BERT）和因果掩码（GPT）的注意力，对比 [MASK] 位置是否能看到右侧 token。BERT 的权重 $ >0 $，GPT 的权重 $ \approx 0 $ ——这就是两种架构在注意力模式上的本质区别。

4. **微调分类**：`cls_repr @ W_cls` 将 [CLS] 位置的表示投影到类别数。微调时 BERT 的所有参数和 $ W_{\text{cls}} $ 一起训练，使 [CLS] 表示逐步适应分类任务。
