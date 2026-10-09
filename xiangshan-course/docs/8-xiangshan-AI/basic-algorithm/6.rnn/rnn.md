# RNN

## 1. 文字讲解

### 1.1 这个算法解决什么问题

DNN 和 CNN 处理的都是"一个输入 → 一个输出"的映射。但很多数据天然有序列结构：一句话的词有先后顺序，一段股价有时间先后，一段语音有时序。序列数据的核心特点是：**当前时刻的输出依赖于之前时刻的信息**。

例如预测句子 "The cat sat on the ___" 的下一个词，必须记住前面的 "The cat sat on the" 才能预测 "mat"。全连接网络和 CNN 都没有"记忆"机制——每个输入独立处理，无法利用历史信息。

循环神经网络（Recurrent Neural Network, RNN）通过**隐状态**在时间步之间传递信息，天然适合序列建模。

**输入**：序列 $x_1, x_2, \dots, x_T$ ，每个 $x_t$ 是 $d$ 维向量。

**输出**：序列 $y_1, y_2, \dots, y_T$ （每个时间步都有输出）或只在最后一步输出 $y_T$。

**适用边界**：短序列的序列分类、语言建模、时间序列预测。对于长序列，梯度消失问题使普通 RNN 难以训练，需要 LSTM 或 GRU。

### 1.2 核心思想

RNN 的核心思想是**参数共享 + 隐状态传递**。

在每一个时间步 $t$ ，RNN 接收当前输入 $x_t$ 和上一步的隐状态 $h_{t-1}$ ，计算新的隐状态：

$$h_t = \tanh(x_t \cdot W_{xh} + h_{t-1} \cdot W_{hh} + b_h)$$

关键点：
- **同一组权重** $W_{xh}, W_{hh}$ 在所有时间步共享——无论序列多长，参数量不变。
- **隐状态** $h_t$ 是对"截至时刻 $t$ 的所有历史信息的压缩"。它不是简单的记忆，而是一个经 tanh 非线性变换后的状态向量。
- 隐状态像一条"信息链"： $h_1$ 影响 $h_2$ ， $h_2$ 影响 $h_3$ ，以此类推。

直觉上理解：RNN 就像一个边读边记笔记的人。每读到一个新词 $x_t$ ，他翻看上一页笔记 $h_{t-1}$ ，结合新词写下新的一页 $h_t$。虽然每页笔记的内容有限（隐状态维度固定），但它编码了从开头到当前的所有关键信息。

### 1.3 基本流程

**前向传播（按时间步展开）**：

1. 初始化 $h_0 = \mathbf{0}$。
2. 对每个时间步 $t = 1, 2, \dots, T$：
   - 计算隐状态： $h_t = \tanh(x_t W_{xh} + h_{t-1} W_{hh} + b_h)$
   - 计算输出： $y_t = h_t W_{hy} + b_y$
3. 得到所有时间步的输出序列。

**反向传播通过时间（BPTT）**：

1. 在每个时间步计算损失 $L_t$ ，总损失 $L = \sum_t L_t$。
2. 从最后一步 $t = T$ 开始，沿时间反方向传播梯度。
3. 梯度从 $h_t$ 传到 $h_{t-1}$ 时，需要乘以 $W_{hh} \cdot \tanh'(z_t)$。
4. 参数梯度是所有时间步梯度的累加。

### 1.4 直观例子

贯穿全章使用一个标量 RNN（1 维输入，1 维隐状态，1 维输出）：

- 输入序列： $x = [1, 1, 1]$ （3 个时间步）
- 目标序列： $y_{\text{target}} = [0.5, 0.8, 0.9]$
- 权重： $W_{xh} = 1.0$ ， $W_{hh} = 0.5$ ， $W_{hy} = 1.0$ ，偏置 $b_h = b_y = 0$
- 激活函数： $\tanh$

前向传播（手算）：

$$h_0 = 0$$

$$t=1:\quad z_1 = 1 \times 1 + 0.5 \times 0 = 1, \quad h_1 = \tanh(1) \approx 0.762, \quad y_1 = 0.762$$

$$t=2:\quad z_2 = 1 + 0.5 \times 0.762 = 1.381, \quad h_2 = \tanh(1.381) \approx 0.881, \quad y_2 = 0.881$$

$$t=3:\quad z_3 = 1 + 0.5 \times 0.881 = 1.440, \quad h_3 = \tanh(1.440) \approx 0.894, \quad y_3 = 0.894$$

观察：隐状态逐步增大（ $0 \to 0.762 \to 0.881 \to 0.894$ ），趋向于稳定值 $\tanh\!\left(\frac{W_{xh}}{1 - W_{hh}}\right) = \tanh(2) \approx 0.964$。这说明 RNN 对持续输入有"累积"效果，但受 tanh 的饱和特性限制，信息逐渐被压缩。

## 2. 公式讲解

### 2.1 核心公式

**前向传播（时间步 $t$ ）：**

$$z_t = x_t \cdot W_{xh} + h_{t-1} \cdot W_{hh} + b_h$$

$$h_t = \tanh(z_t)$$

$$y_t = h_t \cdot W_{hy} + b_y$$

**损失函数（每步均方误差，总损失求和）：**

$$L = \sum_{t=1}^{T} \frac{1}{2}(y_t - y_t^{\text{target}})^2$$

**BPTT——隐状态梯度回传：**

$$\frac{\partial L}{\partial h_t} = \underbrace{\frac{\partial L_t}{\partial h_t}}_{\text{当前步的直接梯度}} + \underbrace{\frac{\partial L}{\partial h_{t+1}} \cdot \frac{\partial h_{t+1}}{\partial h_t}}_{\text{从后续步传回的梯度}}$$

其中：

$$\frac{\partial h_{t+1}}{\partial h_t} = W_{hh} \cdot \tanh'(z_{t+1}) = W_{hh} \cdot (1 - h_{t+1}^2)$$

**梯度衰减的关键：** 从第 $T$ 步回传到第 $t$ 步，梯度需要连乘：

$$\frac{\partial L}{\partial h_t} \bigg|_{\text{来自 } T} \approx \prod_{k=t+1}^{T} W_{hh} \cdot (1 - h_k^2)$$

当 $|W_{hh}| < 1$ 且 $\tanh$ 接近饱和（ $h_k^2 \to 1$ ）时，每一项 $|W_{hh} \cdot (1 - h_k^2)| < 1$ ，连乘导致梯度指数衰减。

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $x_t$ | 第 $t$ 个时间步的输入向量 |
| $h_t$ | 第 $t$ 个时间步的隐状态（对历史的压缩） |
| $h_{t-1}$ | 上一步的隐状态 |
| $z_t$ | 隐状态的线性变换前结果 |
| $W_{xh}$ | 输入到隐状态的权重矩阵 |
| $W_{hh}$ | 隐状态到隐状态的权重矩阵（递归权重） |
| $W_{hy}$ | 隐状态到输出的权重矩阵 |
| $b_h, b_y$ | 隐状态和输出的偏置 |
| $y_t$ | 第 $t$ 步的输出 |
| $L$ | 总损失（所有时间步求和） |
| $T$ | 序列长度 |
| $\tanh'$ | $\tanh$ 的导数， $1 - \tanh^2(z) = 1 - h^2$ |

### 2.3 公式怎么理解

**隐状态更新**： $h_t = \tanh(x_t W_{xh} + h_{t-1} W_{hh} + b_h)$。新隐状态是"当前输入的信息"和"历史隐状态的信息"的加权和，再过 tanh 压缩到 $(-1, 1)$。tanh 的作用是防止隐状态无限增长——如果用线性激活，持续输入会让 $h_t$ 发散。

**时间步展开**：RNN 在时间维度上展开后，等价于一个很深的网络——每一层对应一个时间步，层与层之间通过 $W_{hh}$ 连接。但与普通深度网络不同，所有"层"共享同一组参数。

**梯度回传**： $\partial h_{t+1} / \partial h_t = W_{hh} \cdot (1 - h_{t+1}^2)$。这个雅可比矩阵决定了一步回传中梯度的缩放因子。当 $|W_{hh}| < 1$ （梯度消失）或 $|W_{hh}| > 1$ （梯度爆炸）时，多步连乘会导致梯度指数级衰减或增长。即使 $|W_{hh}|$ 恰好接近 1，tanh 的饱和（ $h \to \pm 1$ 时 $1 - h^2 \to 0$ ）也会让导数趋近于零，加剧梯度消失。

**梯度消失的直觉**：想象一个长句子"我出生在法国 ... （100 个词后）... 我会说法语"。RNN 需要把"法国"这个词的信息保存在隐状态中，经过 100 步后仍能用来预测"法语"。但梯度从第 100 步回传到第 1 步，要经过 99 次 $W_{hh} \cdot (1 - h^2)$ 的连乘。如果每步缩放因子为 0.3， $0.3^{99} \approx 10^{-52}$ ——梯度完全消失，网络无法学到长期依赖。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

# --- 参数（标量 RNN）---
W_xh = 1.0
W_hh = 0.5
W_hy = 1.0
b_h = 0.0
b_y = 0.0

x_seq = np.array([1.0, 1.0, 1.0])
y_target = np.array([0.5, 0.8, 0.9])
T = len(x_seq)

def tanh(z):
    return np.tanh(z)

def tanh_grad_from_h(h):
    """从 tanh 输出 h 计算导数：1 - h^2"""
    return 1 - h**2

# --- 前向传播 ---
def rnn_forward(x_seq, W_xh, W_hh, W_hy, b_h, b_y):
    h = 0.0
    h_list, z_list, y_list = [], [], []
    for t in range(len(x_seq)):
        z = W_xh * x_seq[t] + W_hh * h + b_h
        h = tanh(z)
        y = W_hy * h + b_y
        z_list.append(z); h_list.append(h); y_list.append(y)
    return h_list, z_list, y_list

# --- 反向传播通过时间 (BPTT) ---
def rnn_bptt(x_seq, y_target, h_list, z_list, y_list,
             W_xh, W_hh, W_hy, b_h, b_y):
    T = len(x_seq)
    dW_xh = dW_hh = dW_hy = db_h = db_y = 0.0
    dh_next = 0.0  # 从 t+1 步传回的梯度
    dh_list = [0.0] * T  # 记录每步的隐状态梯度

    for t in reversed(range(T)):
        # 当前步输出梯度
        dy = y_list[t] - y_target[t]
        dW_hy += dy * h_list[t]
        db_y += dy
        # 隐状态梯度 = 直接梯度 + 从下一步传回的梯度
        dh = W_hy * dy + dh_next
        dh_list[t] = dh
        # tanh 反向
        dz = dh * tanh_grad_from_h(h_list[t])
        # 参数梯度
        dW_xh += dz * x_seq[t]
        dW_hh += dz * h_list[t-1] if t > 0 else dz * 0.0
        db_h += dz
        # 传给上一步
        dh_next = W_hh * dz
    return dW_xh, dW_hh, dW_hy, db_h, db_y, dh_list

# --- 1. 验证前向传播 ---
h_list, z_list, y_list = rnn_forward(x_seq, W_xh, W_hh, W_hy, b_h, b_y)
print("=== 前向传播 ===")
for t in range(T):
    print(f"  t={t+1}: z={z_list[t]:.4f}, h={h_list[t]:.4f}, y={y_list[t]:.4f}")

assert np.isclose(h_list[0], np.tanh(1.0), atol=1e-4)
assert np.isclose(h_list[1], np.tanh(1.0 + 0.5*np.tanh(1.0)), atol=1e-4)
print("前向传播验证通过 ✓")

# --- 2. 梯度检查 ---
dW_xh, dW_hh, dW_hy, db_h, db_y, dh_list = rnn_bptt(
    x_seq, y_target, h_list, z_list, y_list, W_xh, W_hh, W_hy, b_h, b_y)

def numerical_grad_rnn(param_name, eps=1e-7):
    """数值梯度"""
    params = {'W_xh': W_xh, 'W_hh': W_hh, 'W_hy': W_hy, 'b_h': b_h, 'b_y': b_y}
    orig = params[param_name]
    params[param_name] = orig + eps
    _, _, y_p = rnn_forward(x_seq, params['W_xh'], params['W_hh'],
                            params['W_hy'], params['b_h'], params['b_y'])
    loss_p = 0.5 * np.sum((np.array(y_p) - y_target)**2)
    params[param_name] = orig - eps
    _, _, y_m = rnn_forward(x_seq, params['W_xh'], params['W_hh'],
                            params['W_hy'], params['b_h'], params['b_y'])
    loss_m = 0.5 * np.sum((np.array(y_m) - y_target)**2)
    params[param_name] = orig
    return (loss_p - loss_m) / (2 * eps)

print(f"\n=== 梯度检查 ===")
grads = {'W_xh': dW_xh, 'W_hh': dW_hh, 'W_hy': dW_hy, 'b_h': db_h, 'b_y': db_y}
for name, grad in grads.items():
    num = numerical_grad_rnn(name)
    print(f"  {name}: 解析={grad:.6f}, 数值={num:.6f}, 一致={np.isclose(grad, num, atol=1e-5)}")
    assert np.isclose(grad, num, atol=1e-5), f"{name} 梯度检查失败"
print("梯度检查通过 ✓")

# --- 3. 梯度消失演示 ---
print(f"\n=== 梯度消失演示（10 步序列）===")
x_long = np.ones(10)
y_long = np.array([0.5]*10)
h_long, z_long, y_long_out = rnn_forward(x_long, W_xh, W_hh, W_hy, b_h, b_y)
_, _, _, _, _, dh_long = rnn_bptt(x_long, y_long, h_long, z_long, y_long_out,
                                  W_xh, W_hh, W_hy, b_h, b_y)

print(f"{'步':>4} | {'h_t':>10} | {'|dh_t|':>10} | {'衰减因子':>10}")
print("-" * 45)
for t in range(10):
    factor = W_hh * tanh_grad_from_h(h_long[t])
    print(f"{t+1:>4} | {h_long[t]:>10.4f} | {abs(dh_long[t]):>10.6f} | {factor:>10.4f}")

# 验证衰减因子的累计乘积
cumulative_factor = 1.0
for t in range(10):
    cumulative_factor *= W_hh * tanh_grad_from_h(h_long[t])
print(f"\n衰减因子累计乘积 (10步连乘): {cumulative_factor:.10f}")
assert abs(cumulative_factor) < 0.01, "10 步衰减因子连乘应 < 0.01"
print("梯度消失验证通过 ✓")

# --- 4. 训练循环 ---
print(f"\n=== 训练（lr=0.1, 500 步）===")
lr = 0.1
W_xh_t, W_hh_t, W_hy_t = 0.8, 0.3, 0.8
b_h_t, b_y_t = 0.0, 0.0
for step in range(500):
    h_l, z_l, y_l = rnn_forward(x_seq, W_xh_t, W_hh_t, W_hy_t, b_h_t, b_y_t)
    loss = 0.5 * np.sum((np.array(y_l) - y_target)**2)
    dW_xh, dW_hh, dW_hy, db_h, db_y, _ = rnn_bptt(
        x_seq, y_target, h_l, z_l, y_l, W_xh_t, W_hh_t, W_hy_t, b_h_t, b_y_t)
    W_xh_t -= lr * dW_xh; W_hh_t -= lr * dW_hh; W_hy_t -= lr * dW_hy
    b_h_t -= lr * db_h; b_y_t -= lr * db_y
    if step % 100 == 0 or step == 499:
        print(f"  step {step:3d}: loss={loss:.6f}, y={[f'{v:.4f}' for v in y_l]}")

print(f"\n最终输出: {[f'{v:.4f}' for v in y_l]}, 目标: {y_target}")
print("\n========== RNN 全部验证通过 ==========")
```

### 3.2 输入输出说明

**输入**：序列 `x = [1, 1, 1]`，3 个时间步，每个 1 维。目标 `y = [0.5, 0.8, 0.9]`。

**前向传播输出**：
- $h = [0.762, 0.881, 0.894]$ ，隐状态逐步增大并趋于饱和。
- $y = [0.762, 0.881, 0.894]$ ，输出等于隐状态（ $W_{hy}=1$ ）。

**梯度检查**：5 个参数（ $W_{xh}, W_{hh}, W_{hy}, b_h, b_y$ ）的解析梯度与数值梯度全部一致。

**梯度消失**：10 步序列中，衰减因子 $W_{hh} \cdot (1 - h^2) \approx 0.5 \times 0.2 = 0.1$ 每步，10 步连乘后梯度衰减到 $< 1\%$。

**训练**：500 步后输出逼近目标 $[0.5, 0.8, 0.9]$。

### 3.3 关键代码解释

1. **`rnn_forward`**：循环遍历时间步，每步计算 $z_t = W_{xh} x_t + W_{hh} h_{t-1}$ ，然后 $h_t = \tanh(z_t)$。保存所有中间值 `z_list, h_list` 供反向传播使用。这就是"时间步展开"的实现。

2. **`rnn_bptt`**：从最后一步反向遍历。`dh = W_hy * dy + dh_next` 是隐状态梯度的核心——它由"当前步的直接梯度"和"从下一步传回的梯度"两部分相加。`dh_next = W_hh * dz` 把梯度传给上一个时间步，其中 `dz = dh * (1 - h^2)` 是 tanh 的反向。

3. **梯度消失演示**：10 步序列中，每步的衰减因子 $W_{hh} \cdot (1 - h_t^2)$ 约为 0.1。从步 10 回传到步 1，梯度约乘以 $0.1^9 \approx 10^{-9}$ ，几乎完全消失。这就是 RNN 难以学习长序列依赖的根本原因。
