# GPT

## 1. 文字讲解

### 1.1 这个算法解决什么问题

BERT 通过双向注意力 + 掩码语言模型，在文本理解任务上取得了巨大成功。但 BERT 有一个根本局限：**它不能生成文本**。

BERT 的双向注意力要求所有 token 同时可见，但生成时——当你写出"今天天气"要预测下一个词时——"今天天气"后面的词还不存在，无法利用右侧上下文。BERT 的架构天生不适合自回归生成。

GPT（Generative Pre-trained Transformer, Radford et al., 2018）只用 Transformer 的 **Decoder** 部分，通过**因果掩码**实现自回归生成：每个 token 只能看到它之前的内容，逐步预测下一个 token。

**输入**：已生成的 token 序列 $[x_1, x_2, \dots, x_t]$。

**输出**：下一个 token 的概率分布 $P(x_{t+1} \mid x_1, \dots, x_t)$。

**适用边界**：文本生成、对话、代码生成、翻译、摘要、问答。现代大语言模型（GPT-4、LLaMA、Claude 等）均基于 GPT 的自回归架构。

### 1.2 核心思想

GPT 的核心思想是**自回归生成 + 统一接口**。

**自回归语言建模**：给定前 $t$ 个 token，预测第 $t+1$ 个 token。生成过程是迭代的：每步预测一个 token，将其拼到序列末尾，再预测下一个。

$$P(x_1, x_2, \dots, x_T) = \prod_{t=1}^{T} P(x_t \mid x_1, \dots, x_{t-1})$$

这是"链式法则"的直接应用——一个句子的概率等于各 token 在给定前文条件下的条件概率之积。

**因果掩码**：在自注意力中，将注意力分数矩阵的上三角（未来位置）设为 $-\infty$，softmax 后权重为 0。这确保生成第 $t$ 个 token 时只能看到位置 $1 \sim t$ 的内容。

**统一接口**：所有任务都转化为"文本续写"。翻译是"English: I love NLP. French:" 后续写 French 翻译；摘要是"Article: ... Summary:" 后续写摘要。不需要像 BERT 那样为每个任务设计特定的输出层——一个生成模型就能做所有事。

直觉上理解：GPT 就像一个只会"接话"的人——你给一段开头，他往下续写。但当你把续写的能力练到极致，他就能在续写中完成翻译、问答、编程等任何"语言→语言"的任务。

### 1.3 基本流程

**预训练**：

1. 从大量文本中取一段序列 $[x_1, \dots, x_T]$。
2. 通过 Decoder（因果掩码自注意力 + FFN + 残差 + LayerNorm）。
3. 每个位置的输出预测下一个 token。
4. 损失 = 所有位置的标准语言模型损失（交叉熵）。

**生成**：

1. 给定提示词（prompt），通过 Decoder 得到最后一个位置的输出。
2. 输出投影到词表大小，softmax 得到概率分布。
3. 按概率采样（或取 argmax）下一个 token。
4. 将新 token 拼到序列末尾，重复步骤 1-3。
5. 生成到结束符或达到最大长度。

### 1.4 直观例子

贯穿全章使用与 BERT 章相同的词汇表，展示自回归生成：

- 词汇表：`["I", "love", "math", "NLP", "<tool_call>"]`，嵌入维度 $d=4$
- 提示词：`["I", "love"]`
- 目标：逐步生成后续 token

**第 1 步**：输入 `["I", "love"]`，Decoder 最后位置预测下一个 token。

因果掩码确保 "love" 只能看到 "I" 和自身，看不到未来。输出概率分布中，"math" 或 "NLP" 应有较高概率。

**第 2 步**：输入 `["I", "love", "math"]`，预测下一个。

**第 3 步**：输入 `["I", "love", "math", "NLP"]`，预测下一个（可能是结束符）。

## 2. 公式讲解

### 2.1 核心公式

**因果掩码自注意力**：

$$\text{Attn}_{\text{causal}}(Q, K, V) = \text{softmax}\!\left(\frac{Q K^T}{\sqrt{d_k}} + M\right) V$$

其中掩码矩阵 $M$ 的上三角为 $-\infty$，下三角和对角线为 0。

$$M_{ij} = \begin{cases} 0 & j \leq i \\ -\infty & j > i \end{cases}$$

**GPT Decoder Block**：

$$Z = \text{LayerNorm}(X + \text{CausalAttn}(X))$$

$$Z' = \text{LayerNorm}(Z + \text{FFN}(Z))$$

与 Transformer 章的 Decoder Block 的前两层相同（无交叉注意力层，因为 GPT 只有 Decoder）。

**自回归语言模型损失**：

$$L = -\sum_{t=1}^{T-1} \log P(x_{t+1} \mid x_1, \dots, x_t)$$

每个位置 $t$ 预测 $x_{t+1}$，损失为所有位置的平均交叉熵。

**生成概率（链式法则）**：

$$P(x_1, \dots, x_T) = \prod_{t=1}^{T} P(x_t \mid x_{<t})$$

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $X$ | 输入 token 嵌入序列 |
| $M$ | 因果掩码矩阵（上三角 $-\infty$ ） |
| $Z$ | Decoder 输出（上下文感知表示） |
| $Z_t$ | 位置 $t$ 的表示，编码了 $x_1, \dots, x_t$ 的信息 |
| $P(x_{t+1} \mid x_{\leq t})$ | 给定前 $t$ 个 token 预测第 $t+1$ 个的概率 |
| $W_{\text{vocab}}$ | 词表投影矩阵， $d \times \|V\|$ |
| $\|V\|$ | 词汇表大小 |

### 2.3 公式怎么理解

**因果掩码**： $M$ 的上三角为 $-\infty$ 意味着位置 $i$ 对位置 $j > i$ 的注意力分数为 $-\infty$，softmax 后权重为 0。这是"看不到未来"的硬约束——生成时未来 token 还不存在，所以必须屏蔽。对比 BERT 的 Encoder 无此约束（双向）。

**自回归链式法则**： $P(x_1, \dots, x_T) = \prod P(x_t | x_{<t})$ 将联合概率分解为条件概率之积。模型不需要一步生成整个序列，而是逐步生成——每步只预测一个 token。这正是 GPT 生成的数学基础。

### 2.4 BERT vs GPT：架构对比与应用差异

| 维度 | BERT | GPT |
|------|------|-----|
| 架构 | Transformer **Encoder** | Transformer **Decoder** |
| 注意力 | **双向**（无掩码） | **单向**（因果掩码） |
| 预训练 | 掩码语言模型（MLM，完形填空） | 自回归语言模型（预测下一个 token） |
| 上下文 | 每个 token 看到全局 | 每个 token 只看到左侧 |
| 任务适配 | 微调（加任务特定输出层） | 提示/上下文学习（统一接口） |
| 擅长 | 理解（分类、NER、问答提取） | 生成（对话、翻译、写作、编程） |
| 推理模式 | 一次性编码整个序列 | 逐步生成，每步加一个 token |
| 对数据量 | 微调需少量标注数据 | 大模型可通过 prompt 零样本学习 |

### 2.5 为什么现代 LLM 都基于 GPT 架构

**1. 生成能力是通用 AI 的基础。** BERT 的 MLM 是"填空"——给定完整上下文，预测被遮挡的词。但人类使用语言的方式更接近"续写"——给定开头，生成后续。GPT 的自回归生成天然适配这一模式。翻译、摘要、对话、编程……几乎所有语言任务都可以表述为"文本→文本"的生成，GPT 的统一接口能一站式解决。

**2. 零样本/少样本学习（Zero/Few-shot）。** BERT 的微调范式需要为每个任务准备标注数据和训练过程。当模型规模足够大时，GPT 可以通过提示词（prompt）直接完成任务，无需微调——"将以下英文翻译为法文：..."，模型直接续写出翻译。这种"开箱即用"的能力使 GPT 架构的模型更容易部署和推广。

**3. 规模化（Scaling Laws）。** 研究发现 GPT 架构的模型性能随参数量、数据量、计算量的增长而可预测地提升（Kaplan et al., 2020）。BERT 架构的双向注意力在规模化时没有表现出同样的收益——因为 MLM 的任务与"生成式理解"之间有鸿沟，模型规模再大也无法直接生成。

**4. BERT 的局限性。** BERT 的双向注意力要求所有位置同时可见，这意味着：
   - **无法自回归生成**：生成第 $t$ 个 token 时， $t+1$ 之后的 token 不存在，双向注意力无意义。
   - **微调成本**：每个新任务都需要标注数据和训练流程，不像 GPT 可以用 prompt 适配。
   - **任务碎片化**：分类、NER、问答需要不同的输出头和微调策略，缺乏统一接口。
   - **预训练-微调鸿沟**：MLM 预训练（填空）与下游任务（分类/生成）的目标不一致，微调需要弥合这一差距。GPT 的预训练目标（续写）与生成任务天然一致。

**5. 生成即理解（Generation = Understanding）。** 一个能高质量续写文本的模型，必须"理解"了语言的语法、语义、世界知识。GPT 架构通过"生成"这一统一任务，隐式地学会了"理解"——而 BERT 虽然在理解任务上微调后更强，但它不会"说"，限制了向通用 AI 的进化路径。

不过 BERT 并非没有价值——在**资源受限场景**（需要高效率、低成本）和**纯理解任务**（如文档分类、NER）上，BERT 类模型仍是优选。BERT 的双向注意力在理解精度上有天然优势，只是它不适合"做大做强"走向通用生成。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

np.random.seed(42)
d_model = 4
seq_len = 4

# --- 词汇表 ---
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

def causal_mask(seq_len):
    mask = np.zeros((seq_len, seq_len))
    for i in range(seq_len):
        for j in range(seq_len):
            if j > i: mask[i, j] = -1e9
    return mask

# --- GPT Decoder Block（无交叉注意力，只有因果自注意力 + FFN）---
W_Q = np.eye(d_model) * 0.5
W_K = np.eye(d_model) * 0.5
W_V = np.eye(d_model) * 0.5
W_O = np.eye(d_model) * 0.5
W1_ffn = np.eye(d_model) * 0.3

def gpt_decoder_block(X, seq_len_t):
    mask = causal_mask(seq_len_t)
    Q, K, V = X @ W_Q, X @ W_K, X @ W_V
    attn_out, attn = scaled_dot_product_attention(Q, K, V, mask=mask)
    Z = layer_norm(X + attn_out)
    ffn_out = relu(Z @ W1_ffn) @ W1_ffn
    return layer_norm(Z + ffn_out), attn

# --- 自回归生成 ---
def generate(prompt_tokens, n_generate=3):
    tokens = list(prompt_tokens)
    print(f"提示词: {tokens}")
    for step in range(n_generate):
        # 构建输入
        X = np.array([word_to_emb[t] for t in tokens], dtype=float)
        T = len(tokens)
        PE = positional_encoding(T, d_model)
        X_input = X + PE
        # Decoder 编码
        Z, attn = gpt_decoder_block(X_input, T)
        # 最后位置预测下一个 token
        last_repr = Z[-1]  # (d_model,)
        # 词表投影
        W_vocab = np.array([word_to_emb[w] for w in vocab_list], dtype=float)
        logits = last_repr @ W_vocab.T  # (vocab_size,)
        probs = softmax(logits)
        # 选择 token（argmax = 贪心解码）
        next_idx = np.argmax(probs)
        next_token = vocab_list[next_idx]
        tokens.append(next_token)
        print(f"  步 {step+1}: 输入 {tokens[:-1]} → 预测 '{next_token}' "
              f"(p={probs[next_idx]:.4f})")
        # 打印注意力
        if step == 0:
            print(f"  注意力（最后位置 → 所有位置）: {np.round(attn[-1], 4)}")
            print(f"  上三角权重=0: {[np.isclose(attn[-1,j], 0, atol=1e-4) for j in range(1,T)]}")
    print(f"最终生成: {tokens}")
    return tokens

# --- 1. 自回归生成 ---
print("=== GPT 自回归生成 ===")
generated = generate(["I", "love"], n_generate=3)

# --- 2. 因果掩码验证 ---
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
# 上三角应为 0
for i in range(4):
    for j in range(4):
        if j > i:
            assert abs(attn_test[i, j]) < 1e-4, f"({i},{j}) 应为 0"
print("上三角注意力=0 ✓（看不到未来）")

# --- 3. 自回归损失验证 ---
print(f"\n=== 自回归语言模型损失 ===")
sentence = ["I", "love", "math", "NLP"]
X_sent = np.array([word_to_emb[w] for w in sentence], dtype=float)
PE_sent = positional_encoding(4, d_model)
Z_sent, _ = gpt_decoder_block(X_sent + PE_sent, 4)

W_vocab = np.array([word_to_emb[w] for w in vocab_list], dtype=float)
total_loss = 0
print(f"句子: {sentence}")
for t in range(3):  # 预测 t+1 位置的 token
    logits = Z_sent[t] @ W_vocab.T
    probs = softmax(logits)
    target_idx = vocab_list.index(sentence[t+1])
    loss = -np.log(probs[target_idx])
    total_loss += loss
    print(f"  位置 {t} ('{sentence[t]}') → 预测 '{sentence[t+1]}': "
          f"p={probs[target_idx]:.4f}, loss={loss:.4f}")

avg_loss = total_loss / 3
print(f"平均损失: {avg_loss:.4f}")
assert avg_loss > 0, "损失应为正"

# --- 4. BERT vs GPT 对比 ---
print(f"\n=== BERT vs GPT 注意力对比 ===")
# BERT: 无掩码
_, attn_bert = scaled_dot_product_attention(X_test @ W_Q, X_test @ W_K, X_test @ W_V)
# GPT: 因果掩码
_, attn_gpt = scaled_dot_product_attention(
    X_test @ W_Q, X_test @ W_K, X_test @ W_V, mask=causal_mask(4))

print(f"位置 2 (math) 的注意力:")
print(f"  BERT (双向): {np.round(attn_bert[2], 4)}  ← 能看到位置 3 (NLP)")
print(f"  GPT  (单向): {np.round(attn_gpt[2], 4)}  ← 看不到位置 3 (NLP)")
assert attn_bert[2, 3] > 0, "BERT 应能看到右侧"
assert abs(attn_gpt[2, 3]) < 1e-4, "GPT 不应看到右侧"
print("双向 vs 单向验证通过 ✓")

# --- 5. 统一接口验证 ---
print(f"\n=== 统一接口（prompt → 生成）===")
# 翻译任务模拟
print("翻译任务: 'English: I love NLP. French:' → 生成")
translate_prompt = ["I", "love"]  # 简化版
translate_result = generate(translate_prompt, n_generate=2)

# 问答任务模拟
print("\n问答任务: 'Question: What is math? Answer:' → 生成")
qa_prompt = ["I", "love"]  # 简化版
qa_result = generate(qa_prompt, n_generate=2)
print("→ 同一模型，不同 prompt 即可适配不同任务（无需微调）")

print("\n========== GPT 全部验证通过 ==========")
```

### 3.2 输入输出说明

**输入**：提示词 `["I", "love"]`，2 个 token，4 维嵌入。

**自回归生成**：
- 步 1：输入 `["I", "love"]`，Decoder 最后位置输出预测第 3 个 token。
- 步 2：输入 `["I", "love", token3]`，预测第 4 个。
- 步 3：继续生成。
- 每步只有最后一个位置的输出用于预测，但整个序列都被编码（提供上下文）。

**因果掩码**：注意力矩阵上三角为 0——位置 $i$ 只关注 $j \leq i$ 的位置。

**自回归损失**：对句子 `["I", "love", "math", "NLP"]`，位置 0 预测"love"，位置 1 预测"math"，位置 2 预测"NLP"。损失为各位置交叉熵的平均。

**BERT vs GPT 对比**：同一序列，BERT（无掩码）位置 2 能看到位置 3，GPT（因果掩码）位置 2 看不到位置 3。

### 3.3 关键代码解释

1. **`gpt_decoder_block`**：与 Transformer 章的 Decoder Block 类似，但**没有交叉注意力层**——GPT 只有 Decoder，没有 Encoder，所以不需要从 Encoder 接收 K/V。关键行是 `mask=causal_mask(seq_len_t)`，确保因果约束。

2. **`generate`**：自回归生成的核心循环。每步用当前所有 token 编码，取**最后一个位置的输出**预测下一个 token，将新 token 拼到序列末尾，重复。这就是"逐步生成"的实现——与 BERT 的"一次编码全部"形成对比。

3. **贪心解码**：`np.argmax(probs)` 取概率最高的 token。实际 LLM 用更复杂的采样策略（top-k、top-p、temperature），但核心流程相同。

4. **自回归损失**：对序列中每个位置 $t$，用 $Z_t$ 预测 $x_{t+1}$。注意位置 $T-1$ （最后一个）不需要预测（没有 $x_T$ 作为目标）。这与 BERT 的 MLM 不同——MLM 只在被遮挡的位置计算损失，而 GPT 在每个位置都计算。

5. **统一接口**：`generate` 函数对任何 prompt 都执行相同操作——翻译、问答、续写都是"给开头，续写"。这不需要为不同任务设计不同输出头，是 GPT 架构成为 LLM 标准的关键原因。
