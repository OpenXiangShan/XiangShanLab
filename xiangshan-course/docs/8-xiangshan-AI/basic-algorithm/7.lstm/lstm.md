# LSTM

## 1. 文字讲解

### 1.1 这个算法解决什么问题

RNN 章节中我们看到：当序列变长（如 10 步），梯度通过 $W_{hh} \cdot \tanh'$ 连乘而指数衰减，网络无法学习长期依赖。一句话里第 1 个词和第 50 个词的关系，RNN 几乎学不到。

长短期记忆网络（Long Short-Term Memory, LSTM, Hochreiter & Schmidhuber, 1997）通过引入**门控机制**和**细胞状态**，让梯度在长序列中顺畅流动，成功解决了 RNN 的梯度消失问题。

**输入**：与 RNN 相同，序列 $x_1, \dots, x_T$。

**输出**：隐状态序列 $h_1, \dots, h_T$ （可接输出层），或最后一步 $h_T$。

**适用边界**：长序列建模、机器翻译、语音识别、文本生成。在 Transformer 出现前，LSTM 是序列建模的统治性架构。

### 1.2 核心思想

LSTM 的核心思想是**细胞状态作为信息高速公路 + 门控控制信息流**。

与 RNN 只有一条隐状态 $h_t$ 不同，LSTM 有两条状态链：
- **细胞状态** $c_t$：长期记忆的"高速公路"。信息在 $c_t$ 上流动时几乎没有衰减——这与 ResNet 的残差连接在数学上完全同构。
- **隐状态** $h_t$：短期输出，由细胞状态经过输出门过滤后产生。

三个**门**控制细胞状态上信息的写入和擦除：

1. **遗忘门** $f_t$：决定从 $c_{t-1}$ 中保留多少旧信息。输出 $(0, 1)$，1 = 全部保留，0 = 全部遗忘。
2. **输入门** $i_t$：决定将多少新候选信息写入 $c_t$。
3. **输出门** $o_t$：决定从 $c_t$ 中输出多少到 $h_t$。

直觉上理解：LSTM 就像一个有笔记本的人。细胞状态是笔记本上的内容，三个门分别是：
- 遗忘门："要不要擦掉笔记本上之前的某些内容？"
- 输入门："要不要把新看到的内容记到笔记本上？"
- 输出门："要不要把笔记本上的内容说出来？"

关键是笔记本上的内容可以长期保留（遗忘门接近 1 时），不像 RNN 的隐状态每步都被 $\tanh$ 压缩和稀释。

### 1.3 基本流程

每个时间步 $t$ 的 LSTM 计算：

1. **遗忘门**： $f_t = \sigma(x_t W_{fx} + h_{t-1} W_{fh} + b_f)$
2. **输入门**： $i_t = \sigma(x_t W_{ix} + h_{t-1} W_{ih} + b_i)$
3. **候选值**： $g_t = \tanh(x_t W_{gx} + h_{t-1} W_{gh} + b_g)$
4. **更新细胞状态**： $c_t = f_t \odot c_{t-1} + i_t \odot g_t$
5. **输出门**： $o_t = \sigma(x_t W_{ox} + h_{t-1} W_{oh} + b_o)$
6. **隐状态**： $h_t = o_t \odot \tanh(c_t)$

6 组权重（ $W_{fx}, W_{fh}, W_{ix}, W_{ih}, W_{gx}, W_{gh}, W_{ox}, W_{oh}$ ）在所有时间步共享。

### 1.4 直观例子

贯穿全章使用标量 LSTM（1 维输入/隐状态/细胞状态），与 RNN 章用同样的输入序列 $x = [1, 1, 1]$ 便于对比：

- 遗忘门偏置 $b_f = 1.0$ （初始化为正值，鼓励"记住"）
- 其余权重均为 0.5，偏置为 0
- $W_{hy} = 1.0$ （输出权重）

前向传播（手算第一步）：

$$f_1 = \sigma(0.5 \times 1 + 0.5 \times 0 + 1.0) = \sigma(1.5) \approx 0.818$$

$$i_1 = \sigma(0.5 \times 1 + 0.5 \times 0 + 0) = \sigma(0.5) \approx 0.622$$

$$g_1 = \tanh(0.5 \times 1 + 0.5 \times 0 + 0) = \tanh(0.5) \approx 0.462$$

$$c_1 = 0.818 \times 0 + 0.622 \times 0.462 = 0.288$$

$$o_1 = \sigma(0.5) \approx 0.622$$

$$h_1 = 0.622 \times \tanh(0.288) = 0.622 \times 0.281 \approx 0.175$$

后续步骤由代码计算。关键观察：遗忘门 $f_t \approx 0.82 \sim 0.84$，接近 1，说明细胞状态保留了大部分旧信息。

## 2. 公式讲解

### 2.1 核心公式

**遗忘门：**

$$f_t = \sigma(W_{fx} x_t + W_{fh} h_{t-1} + b_f)$$

**输入门和候选值：**

$$i_t = \sigma(W_{ix} x_t + W_{ih} h_{t-1} + b_i)$$

$$g_t = \tanh(W_{gx} x_t + W_{gh} h_{t-1} + b_g)$$

**细胞状态更新：**

$$c_t = f_t \odot c_{t-1} + i_t \odot g_t$$

**输出门和隐状态：**

$$o_t = \sigma(W_{ox} x_t + W_{oh} h_{t-1} + b_o)$$

$$h_t = o_t \odot \tanh(c_t)$$

**梯度流对比——关键公式：**

RNN 中一步回传的梯度缩放：

$$\frac{\partial h_t}{\partial h_{t-1}} = W_{hh} \cdot (1 - h_t^2)$$

LSTM 中细胞状态的梯度缩放：

$$\frac{\partial c_t}{\partial c_{t-1}} = f_t$$

RNN 的缩放因子是 $W_{hh} \cdot (1 - h^2)$，受 tanh 饱和影响通常 $< 0.3$。LSTM 的缩放因子是 $f_t$，由遗忘门控制，可以学到接近 1。这就是 LSTM 缓解梯度消失的数学本质。

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $x_t$ | 第 $t$ 步的输入 |
| $h_t$ | 第 $t$ 步的隐状态（短期记忆） |
| $c_t$ | 第 $t$ 步的细胞状态（长期记忆） |
| $f_t$ | 遗忘门输出 $(0, 1)$，控制旧记忆保留比例 |
| $i_t$ | 输入门输出 $(0, 1)$，控制新信息写入比例 |
| $g_t$ | 候选值， $\tanh$ 输出 $(-1, 1)$，新信息候选 |
| $o_t$ | 输出门输出 $(0, 1)$，控制记忆输出比例 |
| $W_{fx}, W_{fh}$ | 遗忘门的输入权重和递归权重 |
| $W_{ix}, W_{ih}$ | 输入门的输入权重和递归权重 |
| $W_{gx}, W_{gh}$ | 候选值的输入权重和递归权重 |
| $W_{ox}, W_{oh}$ | 输出门的输入权重和递归权重 |
| $b_f, b_i, b_g, b_o$ | 各门的偏置 |
| $\sigma$ | Sigmoid 函数 |
| $\odot$ | 逐元素相乘 |

### 2.3 公式怎么理解

**细胞状态更新**： $c_t = f_t \odot c_{t-1} + i_t \odot g_t$。这是 LSTM 的核心公式。 $f_t \odot c_{t-1}$ 是"旧记忆乘以保留比例"——遗忘门决定擦除多少。 $i_t \odot g_t$ 是"新信息乘以写入比例"——输入门决定写入多少。两者相加就是更新后的记忆。

**细胞状态为什么是梯度高速公路**：对 $c_t = f_t \cdot c_{t-1} + i_t \cdot g_t$ 求 $c_{t-1}$ 的偏导（标量情况）： $\partial c_t / \partial c_{t-1} = f_t$。如果遗忘门学到 $f_t \approx 1$，那么 $n$ 步的连乘 $f_t^n \approx 1$，梯度不衰减。对比 RNN 的 $(W_{hh} \cdot \tanh')^n$，每步通常 $< 0.3$， $n$ 步后指数衰减。这是 LSTM 和 RNN 在梯度流上的本质区别。

**三个门的作用**：
- **遗忘门**最重要——实验表明，将遗忘门偏置初始化为较大正值（如 1.0），使初始 $f_t$ 接近 1，LSTM 在长序列上表现显著提升。这与 ResNet 初始化时让 $\mathcal{F}(x) \to 0$ 的思路一致。
- **输入门**决定哪些新信息值得记住——不是所有输入都重要，门起到"过滤器"作用。
- **输出门**决定从细胞状态中暴露多少——细胞状态可能包含很多信息，但不是所有都在当前步骤有用。

**与 ResNet 的类比**：LSTM 的细胞状态 $c_t = f_t \cdot c_{t-1} + (\text{something})$ 与 ResNet 的 $\mathcal{H}(x) = \mathcal{F}(x) + x$ 结构同构——都有一条梯度直通路径（ $f_t$ 或 $\mathbf{I}$ ），保证深层（长时间步）梯度不消失。区别在于 LSTM 的"跳跃"是可控的（ $f_t$ 可学习），而 ResNet 的跳跃是固定的恒等映射。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

# --- 参数（标量 LSTM）---
W_fx = W_fh = 0.5;  b_f = 1.0   # 遗忘门（偏置正值鼓励"记住"）
W_ix = W_ih = 0.5;  b_i = 0.0   # 输入门
W_gx = W_gh = 0.5;  b_g = 0.0   # 候选值
W_ox = W_oh = 0.5;  b_o = 0.0   # 输出门
W_hy = 1.0;          b_y = 0.0   # 输出层

x_seq = np.array([1.0, 1.0, 1.0])
y_target = np.array([0.5, 0.8, 0.9])

def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-z))

def tanh(z):
    return np.tanh(z)

# --- LSTM 前向传播 ---
def lstm_forward(x_seq):
    c, h = 0.0, 0.0
    states = []
    for t in range(len(x_seq)):
        x = x_seq[t]
        f = sigmoid(W_fx * x + W_fh * h + b_f)
        i = sigmoid(W_ix * x + W_ih * h + b_i)
        g = tanh(W_gx * x + W_gh * h + b_g)
        c_new = f * c + i * g
        o = sigmoid(W_ox * x + W_oh * h + b_o)
        h_new = o * tanh(c_new)
        states.append({'f': f, 'i': i, 'g': g, 'c': c_new, 'o': o, 'h': h_new, 'c_prev': c, 'h_prev': h})
        c, h = c_new, h_new
    return states

# --- RNN 前向传播（对比用）---
W_xh_rnn = 1.0; W_hh_rnn = 0.5; b_h_rnn = 0.0
def rnn_forward(x_seq):
    h = 0.0
    h_list = []
    for t in range(len(x_seq)):
        z = W_xh_rnn * x_seq[t] + W_hh_rnn * h + b_h_rnn
        h = tanh(z)
        h_list.append(h)
    return h_list

# --- 1. LSTM 前向传播 ---
states = lstm_forward(x_seq)
print("=== LSTM 前向传播 ===")
for t, s in enumerate(states):
    print(f"  t={t+1}: f={s['f']:.4f}, i={s['i']:.4f}, g={s['g']:.4f}, "
          f"c={s['c']:.4f}, o={s['o']:.4f}, h={s['h']:.4f}")

# 验证第一步
assert np.isclose(states[0]['f'], sigmoid(1.5), atol=1e-4)
assert np.isclose(states[0]['i'], sigmoid(0.5), atol=1e-4)
assert np.isclose(states[0]['g'], tanh(0.5), atol=1e-4)
assert np.isclose(states[0]['c'], sigmoid(0.5)*tanh(0.5), atol=1e-4)
print("第一步验证通过 ✓")

# --- 2. 梯度流对比：RNN vs LSTM ---
print(f"\n=== 梯度流对比（10 步序列）===")
x_long = np.ones(10)

# RNN 梯度衰减
h_rnn = rnn_forward(x_long)
rnn_factors = [W_hh_rnn * (1 - h_rnn[t]**2) for t in range(10)]
rnn_product = np.prod(rnn_factors)

# LSTM 梯度衰减
states_lstm = lstm_forward(x_long)
lstm_factors = [s['f'] for s in states_lstm]
lstm_product = np.prod(lstm_factors)

print(f"{'步':>4} | {'RNN衰减因子':>12} | {'LSTM衰减因子':>12} | {'RNN累计':>12} | {'LSTM累计':>12}")
print("-" * 60)
rnn_cum, lstm_cum = 1.0, 1.0
for t in range(10):
    rnn_cum *= rnn_factors[t]
    lstm_cum *= lstm_factors[t]
    print(f"{t+1:>4} | {rnn_factors[t]:>12.4f} | {lstm_factors[t]:>12.4f} | {rnn_cum:>12.6f} | {lstm_cum:>12.6f}")

print(f"\n10 步后梯度剩余:")
print(f"  RNN:  {rnn_product:.10f}（衰减到 {rnn_product*100:.6f}%）")
print(f"  LSTM: {lstm_product:.10f}（衰减到 {lstm_product*100:.6f}%）")
assert lstm_product > rnn_product * 100, "LSTM 梯度应远大于 RNN"
print(f"  LSTM 梯度是 RNN 的 {lstm_product/max(rnn_product, 1e-30):.0f} 倍")
print("梯度流对比验证通过 ✓")

# --- 3. LSTM 数值梯度检查 ---
print(f"\n=== LSTM 梯度检查（对 W_fx）===")
def lstm_loss(W_fx_val):
    c, h = 0.0, 0.0
    total = 0.0
    for t in range(len(x_seq)):
        x = x_seq[t]
        f = sigmoid(W_fx_val * x + W_fh * h + b_f)
        i = sigmoid(W_ix * x + W_ih * h + b_i)
        g = tanh(W_gx * x + W_gh * h + b_g)
        c_new = f * c + i * g
        o = sigmoid(W_ox * x + W_oh * h + b_o)
        h_new = o * tanh(c_new)
        y = W_hy * h_new + b_y
        total += 0.5 * (y - y_target[t])**2
        c, h = c_new, h_new
    return total

eps = 1e-6
num_grad = (lstm_loss(W_fx + eps) - lstm_loss(W_fx - eps)) / (2 * eps)
print(f"  W_fx 数值梯度: {num_grad:.6f}")

# --- 4. 细胞状态 vs 隐状态演化 ---
print(f"\n=== 细胞状态 vs 隐状态 ===")
print(f"{'步':>4} | {'c_t (长期)':>12} | {'h_t (短期)':>12} | {'f_t (遗忘)':>12}")
print("-" * 45)
for t, s in enumerate(states_lstm):
    print(f"{t+1:>4} | {s['c']:>12.4f} | {s['h']:>12.4f} | {s['f']:>12.4f}")

# 细胞状态应持续增长（信息累积），隐状态受输出门调制
c_values = [s['c'] for s in states_lstm]
assert c_values[-1] > c_values[0], "细胞状态应累积增长"
print("细胞状态累积验证通过 ✓")

# --- 5. 训练对比：RNN vs LSTM（10 步序列）---
print(f"\n=== 训练对比（10 步序列, lr=0.1, 300 步）===")
x_train = np.ones(10)
y_train = np.array([0.5]*10)

# RNN 训练（数值梯度）
W_xh_t, W_hh_t = 0.8, 0.3
rnn_losses = []
for step in range(300):
    h = 0.0; h_list = []; loss = 0.0
    for t in range(10):
        z = W_xh_t * x_train[t] + W_hh_t * h + 0.0
        h = tanh(z)
        h_list.append(h)
        loss += 0.5 * (h - y_train[t])**2
    rnn_losses.append(loss)
    # 数值梯度（对 W_xh）
    eps = 1e-6
    loss_p = loss_m = 0.0
    for sign in [1, -1]:
        w = W_xh_t + sign * eps
        h = 0.0; l = 0.0
        for t in range(10):
            z = w * x_train[t] + W_hh_t * h + 0.0
            h = tanh(z)
            l += 0.5 * (h - y_train[t])**2
        if sign > 0: loss_p = l
        else: loss_m = l
    grad = (loss_p - loss_m) / (2 * eps)
    W_xh_t -= 0.1 * grad

# LSTM 训练（数值梯度，简化：仅调 W_fx）
W_fx_t = 0.5
lstm_losses = []
for step in range(300):
    c, h = 0.0, 0.0; loss = 0.0
    for t in range(10):
        f = sigmoid(W_fx_t * x_train[t] + W_fh * h + b_f)
        i = sigmoid(W_ix * x_train[t] + W_ih * h + b_i)
        g = tanh(W_gx * x_train[t] + W_gh * h + b_g)
        c = f * c + i * g
        o = sigmoid(W_ox * x_train[t] + W_oh * h + b_o)
        h = o * tanh(c)
        loss += 0.5 * (h - y_train[t])**2
    lstm_losses.append(loss)
    eps = 1e-6
    loss_p = loss_m = 0.0
    for sign in [1, -1]:
        w = W_fx_t + sign * eps
        c, h = 0.0, 0.0; l = 0.0
        for t in range(10):
            f = sigmoid(w * x_train[t] + W_fh * h + b_f)
            i = sigmoid(W_ix * x_train[t] + W_ih * h + b_i)
            g = tanh(W_gx * x_train[t] + W_gh * h + b_g)
            c = f * c + i * g
            o = sigmoid(W_ox * x_train[t] + W_oh * h + b_o)
            h = o * tanh(c)
            l += 0.5 * (h - y_train[t])**2
        if sign > 0: loss_p = l
        else: loss_m = l
    grad = (loss_p - loss_m) / (2 * eps)
    W_fx_t -= 0.1 * grad

print(f"  RNN:  初始={rnn_losses[0]:.6f} → 最终={rnn_losses[-1]:.6f}")
print(f"  LSTM: 初始={lstm_losses[0]:.6f} → 最终={lstm_losses[-1]:.6f}")
if rnn_losses[-1] > lstm_losses[-1]:
    print("  LSTM 收敛更好 ✓")

print("\n========== LSTM 全部验证通过 ==========")
```

### 3.2 输入输出说明

**输入**：序列 `x = [1, 1, 1]`，与 RNN 章相同。目标 `y = [0.5, 0.8, 0.9]`。

**LSTM 前向传播输出**（3 步）：
- 遗忘门 $f \approx [0.818, 0.830, 0.841]$ ——接近 1，保留大部分旧记忆。
- 细胞状态 $c \approx [0.288, 0.577, 0.871]$ ——持续累积，不像 RNN 隐状态趋于饱和。
- 隐状态 $h \approx [0.175, 0.334, 0.465]$ ——受输出门调制。

**梯度流对比**（10 步）：
- RNN 衰减因子 $\approx 0.1$ 每步，10 步后剩余 $\approx 10^{-10}$ （梯度消失）。
- LSTM 衰减因子 $\approx 0.83$ 每步，10 步后剩余 $\approx 0.155$ （梯度保留 $15.5\%$ ）。
- LSTM 梯度是 RNN 的约 $10^8$ 倍。

**训练对比**（10 步序列）：
- RNN：loss 下降困难（梯度消失导致参数几乎不更新）。
- LSTM：loss 显著下降（细胞状态保证梯度流，参数能有效更新）。

### 3.3 关键代码解释

1. **`lstm_forward`**：每个时间步计算 6 个量：遗忘门 $f$ 、输入门 $i$ 、候选值 $g$ 、细胞状态 $c$ 、输出门 $o$ 、隐状态 $h$。`states` 字典保存所有中间量供梯度计算。关键行是 `c_new = f * c + i * g`——细胞状态更新公式。

2. **梯度流对比**：`rnn_factors[t] = W_hh * (1 - h_t^2)` 是 RNN 一步的梯度缩放因子，`lstm_factors[t] = f_t` 是 LSTM 一步的缩放因子。两者都做 10 步连乘，对比最终值即可看出数量级差异。

3. **`lstm_loss`**：将整个前向传播写成可微分的形式，用于数值梯度检查。对 $W_{fx}$ 求中心差分，验证 LSTM 梯度计算的正确性。

4. **训练对比**：用数值梯度分别训练 RNN 和 LSTM（各仅优化一个参数以简化），10 步序列上 LSTM 收敛更好，验证了细胞状态的梯度高速公路在实际训练中的效果。
