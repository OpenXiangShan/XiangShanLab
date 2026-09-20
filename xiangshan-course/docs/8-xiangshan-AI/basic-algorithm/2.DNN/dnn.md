# DNN

## 1. 文字讲解

### 1.1 这个算法解决什么问题

假设要做一个简单的分类器：给定一个人的身高和体重两个特征，判断是否肥胖。输入是两个数值，输出是一个 0 或 1 的标签。

如果肥胖与非肥胖的分界线在特征空间中是一条直线，逻辑回归就够了。但现实中的分类边界往往是非直线的——比如"高个子且体重适中"的人不肥胖，这个边界在身高-体重平面上是一条曲线而非直线。

全连接神经网络（Deep Neural Network, DNN）通过堆叠多层线性变换和非线性激活函数，能够拟合任意复杂的非线性映射。

**输入**：特征向量 $ x = [x_1, x_2, \dots, x_d] $，$ d $ 为特征维度。

**输出**：预测值 $ \hat{y} $ （回归任务为实数，二分类任务为 0 到 1 之间的概率）。

**适用边界**：适合结构化/表格数据。对于图像（有空间结构）和序列（有时间结构）的数据，后续章节的 CNN 和 RNN 更合适。

### 1.2 核心思想

DNN 的核心思想是**分层特征变换**。一个复杂的映射 $ f: x \to y $ 被拆解为多个简单变换的级联：

$$ f(x) = f_L(f_{L-1}(\cdots f_2(f_1(x)) \cdots))$$

每一层做两件事：

1. **线性变换**：$ z^{(l)} = a^{(l-1)} \cdot W^{(l)} + b^{(l)} $，其中 $ W^{(l)} $ 是权重矩阵，$ b^{(l)} $ 是偏置向量。
2. **激活函数**：$ a^{(l)} = \text{激活}(z^{(l)}) $，对每个元素施加非线性变换。

**为什么需要激活函数？** 如果每一层只做线性变换（没有激活函数），那么多层线性变换的叠加仍然是线性变换——堆叠再多层，等价于一层：

$$ W_2 \cdot (W_1 \cdot x + b_1) + b_2 = (W_2 W_1) \cdot x + (W_2 b_1 + b_2)$$

合并后就是一个单层线性变换。激活函数的引入打破了这种可合并性，是 DNN 能拟合非线性映射的关键。

直觉上理解：每一层把数据投影到一个新的特征空间。在原始空间中无法线性分开的数据，经过几层变换后，在新空间中可能变得线性可分。就像折纸——一张纸上两种颜色的墨迹混在一起，但对折几次后，可能就把两种颜色分到了不同的面上。

### 1.3 基本流程

DNN 的训练交替执行两个阶段：

**前向传播（Forward Pass）**：从输入到输出，逐层计算。

1. 将输入 $ x $ 送入第一层。
2. 每层计算线性变换 $ z^{(l)} = a^{(l-1)} \cdot W^{(l)} + b^{(l)} $，再施加激活函数 $ a^{(l)} = \text{激活}(z^{(l)}) $。
3. 最后一层输出预测值 $ \hat{y} $。

**反向传播（Backward Pass）**：从输出到输入，逐层计算梯度并更新参数。

1. 计算损失 $ L $，衡量预测值 $ \hat{y} $ 与真实值 $ y $ 的差距。
2. 从最后一层开始，利用链式法则计算损失对每个参数的偏导数 $ \partial L / \partial W^{(l)} $、$ \partial L / \partial b^{(l)} $。
3. 用梯度下降更新参数：$ W^{(l)} \leftarrow W^{(l)} - \eta \cdot \partial L / \partial W^{(l)} $。

两个阶段反复交替，损失逐步下降，网络逐步学会从输入到输出的映射。

### 1.4 直观例子

贯穿全章使用一个 2-3-1 的 DNN（2 个输入，3 个隐藏神经元，1 个输出）：

- 输入：$ x = [1, 1]$
- 目标：$ y = 0 $ （希望输出尽量接近 0）
- 第一层：$ W^{(1)} = \begin{bmatrix} 2 & -1 & 1 \\ 0 & 2 & 1 \end{bmatrix} $，$ b^{(1)} = [0, 0, 0]$
- 第二层：$ W^{(2)} = \begin{bmatrix} 0.5 \\ -2 \\ 0.5 \end{bmatrix} $，$ b^{(2)} = 0$
- 隐藏层激活函数：ReLU
- 输出层激活函数：Sigmoid
- 损失函数：$ L = \frac{1}{2}(\hat{y} - y)^2 $ （均方误差）

前向传播手动计算：

$$ z^{(1)} = x \cdot W^{(1)} + b^{(1)} = [1 \times 2 + 1 \times 0, \; 1 \times (-1) + 1 \times 2, \; 1 \times 1 + 1 \times 1] = [2, 1, 2]$$

$$ a^{(1)} = \text{ReLU}([2, 1, 2]) = [2, 1, 2]$$

$$ z^{(2)} = a^{(1)} \cdot W^{(2)} + b^{(2)} = 2 \times 0.5 + 1 \times (-2) + 2 \times 0.5 = 0$$

$$ \hat{y} = \sigma(0) = \frac{1}{1 + e^0} = 0.5$$

$$ L = \frac{1}{2}(0.5 - 0)^2 = 0.125$$

网络当前预测 $ \hat{y} = 0.5 $ （相当于"拿不准"），目标是 0，损失为 0.125。接下来通过反向传播调整权重，让 $ \hat{y} $ 逐步逼近 0。

## 2. 公式讲解

### 2.1 核心公式

**前向传播（第 $ l $ 层）：**

$$ z^{(l)} = a^{(l-1)} \cdot W^{(l)} + b^{(l)}$$

$$ a^{(l)} = f(z^{(l)})$$

其中 $ a^{(0)} = x $ （输入），$ a^{(L)} = \hat{y} $ （输出），$ f $ 是激活函数。

**ReLU 激活函数（隐藏层常用）：**

$$ \text{ReLU}(z) = \max(0, z)$$

$$ \text{ReLU}'(z) = \begin{cases} 1 & z > 0 \\ 0 & z \leq 0 \end{cases}$$

**Sigmoid 激活函数（输出层，用于二分类）：**

$$ \sigma(z) = \frac{1}{1 + e^{-z}}$$

$$ \sigma'(z) = \sigma(z) \cdot (1 - \sigma(z))$$

**损失函数（均方误差）：**

$$ L = \frac{1}{2}(\hat{y} - y)^2$$

**反向传播——输出层误差：**

$$ \delta^{(L)} = \frac{\partial L}{\partial z^{(L)}} = (\hat{y} - y) \cdot f'(z^{(L)})$$

对于 sigmoid 输出层，由于 $ f'(z^{(L)}) = \sigma'(z^{(L)}) = \hat{y}(1 - \hat{y}) $：

$$ \delta^{(L)} = (\hat{y} - y) \cdot \hat{y} \cdot (1 - \hat{y})$$

**反向传播——隐藏层误差（逐层回传）：**

$$ \delta^{(l)} = \left(\delta^{(l+1)} \cdot (W^{(l+1)})^T\right) \odot f'(z^{(l)})$$

其中 $ \odot $ 表示逐元素相乘（Hadamard 积）。

**参数梯度：**

$$ \frac{\partial L}{\partial W^{(l)}} = (a^{(l-1)})^T \cdot \delta^{(l)}$$

$$ \frac{\partial L}{\partial b^{(l)}} = \delta^{(l)}$$

**梯度下降更新：**

$$ W^{(l)} \leftarrow W^{(l)} - \eta \cdot \frac{\partial L}{\partial W^{(l)}}$$

$$ b^{(l)} \leftarrow b^{(l)} - \eta \cdot \frac{\partial L}{\partial b^{(l)}}$$

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $ x $ | 输入特征向量，维度 $ 1 \times d $ |
| $ y $ | 真实标签（目标值） |
| $ \hat{y} $ | 网络预测值，即 $ a^{(L)} $ |
| $ W^{(l)} $ | 第 $ l $ 层的权重矩阵，维度 $ n_{l-1} \times n_l $ |
| $ b^{(l)} $ | 第 $ l $ 层的偏置向量，维度 $ 1 \times n_l $ |
| $ z^{(l)} $ | 第 $ l $ 层的线性变换结果（激活前），维度 $ 1 \times n_l $ |
| $ a^{(l)} $ | 第 $ l $ 层的激活输出，维度 $ 1 \times n_l $ |
| $ \delta^{(l)} $ | 第 $ l $ 层的误差项 $ \partial L / \partial z^{(l)} $，维度 $ 1 \times n_l $ |
| $ f $ | 激活函数（如 ReLU、Sigmoid） |
| $ f' $ | 激活函数的导数 |
| $ \sigma $ | Sigmoid 函数 |
| $ L $ | 损失函数 |
| $ \eta $ | 学习率 |
| $ \odot $ | 逐元素相乘（Hadamard 积） |
| $ n_l $ | 第 $ l $ 层的神经元数量 |

### 2.3 公式怎么理解

**前向传播**：每一层把上一层的输出做一个线性变换（旋转 + 缩放 + 平移），再过一遍激活函数。线性变换 $ z = a \cdot W + b $ 的几何意义是在特征空间中做一次坐标变换—— $ W $ 决定新坐标轴的方向和缩放， $ b $ 决定平移量。

**ReLU 激活函数**：$ \text{ReLU}(z) = \max(0, z) $，把所有负值截断为 0，正值原样通过。它的导数很简单：正数处为 1，负数处为 0。ReLU 是现代 DNN 最常用的隐藏层激活函数，因为计算简单、且正数区域梯度恒为 1，不容易出现梯度消失问题。

**Sigmoid 激活函数**：$ \sigma(z) = 1/(1+e^{-z}) $，把任意实数压缩到 $ (0, 1) $ 区间。当 $ z $ 很大时输出接近 1，$ z $ 很小时接近 0，$ z = 0 $ 时输出 0.5。它常用于二分类的输出层，将网络输出解释为概率。但它的导数 $ \sigma'(z) = \sigma(z)(1-\sigma(z)) $ 最大值仅为 0.25（在 $ z=0 $ 处取得），在深层网络中容易导致梯度消失。

除了 ReLU 和 Sigmoid，常见的激活函数还有 $ \tanh(z) = \frac{e^z - e^{-z}}{e^z + e^{-z}} $ （输出范围 $ (-1, 1) $ ）和 Leaky ReLU（负数区域给一个小的斜率而非直接截断为 0）。选择哪种激活函数取决于任务和网络深度，但核心目的都一样：**引入非线性**。

**损失函数**：均方误差 $ L = \frac{1}{2}(\hat{y} - y)^2 $ 衡量预测值与真实值的差距。前面的 $ \frac{1}{2} $ 是为了求导后系数变为 1（ $ \frac{d}{d\hat{y}} \frac{1}{2}(\hat{y}-y)^2 = \hat{y} - y $ ），方便计算。

**反向传播**：核心是链式法则。损失 $ L $ 对第 $ l $ 层参数的梯度，等于" $ L $ 对第 $ l+1 $ 层输入的梯度"乘以"第 $ l+1 $ 层输入对第 $ l $ 层参数的导数"。逐层往前乘下去，就得到了每一层的梯度。

具体来说：
- $ \delta^{(L)} $ 是输出层的误差，表示"输出层的线性输出 $ z^{(L)} $ 每变化一点，损失变化多少"。在本例中：$ \delta^{(2)} = (0.5 - 0) \times 0.5 \times (1 - 0.5) = 0.125 $。
- $ \delta^{(l)} = (\delta^{(l+1)} \cdot (W^{(l+1)})^T) \odot f'(z^{(l)}) $ 把误差从第 $ l+1 $ 层传到第 $ l $ 层：先乘权重矩阵的转置（反方向传播信号），再乘激活函数的导数（只通过激活函数没被截断的部分）。在本例中：$ \delta^{(1)} = (0.125 \times [0.5, -2, 0.5]) \odot [1, 1, 1] = [0.0625, -0.25, 0.0625] $。
- $ \partial L / \partial W^{(l)} = (a^{(l-1)})^T \cdot \delta^{(l)} $ 把误差转化为参数的梯度：某权重 $ W^{(l)}_{ij} $ 的梯度等于"上一层第 $ i $ 个神经元的输出"乘以"这一层第 $ j $ 个神经元的误差"。

**梯度下降**：参数沿着梯度的反方向更新一小步，使损失下降。学习率 $ \eta $ 控制步长大小——太大可能跳过最优解，太小则收敛太慢。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

# --- 网络参数 ---
W1 = np.array([[2, -1, 1],
               [0,  2, 1]], dtype=float)       # 2×3
b1 = np.array([[0, 0, 0]], dtype=float)         # 1×3
W2 = np.array([[0.5],
               [-2],
               [0.5]], dtype=float)             # 3×1
b2 = np.array([[0.0]])                           # 1×1

x = np.array([[1.0, 1.0]])                       # 1×2
y = np.array([[0.0]])                            # 目标

# --- 激活函数 ---
def relu(z):
    return np.maximum(0, z)

def relu_grad(z):
    return (z > 0).astype(float)

def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-z))

def sigmoid_grad_from_output(a):
    """从激活输出 a 计算 sigmoid 的导数：σ'(z) = a(1-a)"""
    return a * (1 - a)

# --- 前向传播 ---
def forward(x, W1, b1, W2, b2):
    z1 = x @ W1 + b1          # 1×3
    a1 = relu(z1)              # 1×3
    z2 = a1 @ W2 + b2          # 1×1
    a2 = sigmoid(z2)           # 1×1
    return z1, a1, z2, a2

# --- 反向传播 ---
def backward(x, y, z1, a1, z2, a2, W1, W2):
    # 输出层误差：δ² = (ŷ - y) · σ'(z²)
    delta2 = (a2 - y) * sigmoid_grad_from_output(a2)    # 1×1
    # 输出层参数梯度
    dW2 = a1.T @ delta2                                  # 3×1
    db2 = delta2.copy()                                  # 1×1
    # 隐藏层误差：δ¹ = (δ² · W²ᵀ) ⊙ ReLU'(z¹)
    delta1 = (delta2 @ W2.T) * relu_grad(z1)             # 1×3
    # 隐藏层参数梯度
    dW1 = x.T @ delta1                                  # 2×3
    db1 = delta1.copy()                                  # 1×3
    return dW1, db1, dW2, db2

# --- 损失函数 ---
def mse_loss(a2, y):
    return 0.5 * np.sum((a2 - y) ** 2)

# --- 1. 验证前向传播 ---
z1, a1, z2, a2 = forward(x, W1, b1, W2, b2)
loss = mse_loss(a2, y)
print("=== 前向传播 ===")
print(f"z1 = {z1}")
print(f"a1 = {a1}")
print(f"z2 = {z2}")
print(f"a2 (y_hat) = {a2}")
print(f"loss = {loss}")

# --- 2. 验证反向传播（梯度检查）---
dW1, db1, dW2, db2 = backward(x, y, z1, a1, z2, a2, W1, W2)

def numerical_grad(param, fn, eps=1e-7):
    """数值梯度：中心差分"""
    grad = np.zeros_like(param)
    it = np.nditer(param, flags=['multi_index'])
    while not it.finished:
        idx = it.multi_index
        orig = param[idx]
        param[idx] = orig + eps
        loss_plus = fn()
        param[idx] = orig - eps
        loss_minus = fn()
        param[idx] = orig
        grad[idx] = (loss_plus - loss_minus) / (2 * eps)
        it.iternext()
    return grad

print("\n=== 梯度检查 ===")

def loss_fn_W1():
    _, _, _, a = forward(x, W1, b1, W2, b2)
    return mse_loss(a, y)

def loss_fn_W2():
    _, _, _, a = forward(x, W1, b1, W2, b2)
    return mse_loss(a, y)

num_dW1 = numerical_grad(W1, loss_fn_W1)
print(f"dW1 (解析) = \n{dW1}")
print(f"dW1 (数值) = \n{num_dW1}")
print(f"dW1 一致: {np.allclose(dW1, num_dW1, atol=1e-6)}")

num_dW2 = numerical_grad(W2, loss_fn_W2)
print(f"dW2 (解析) = \n{dW2}")
print(f"dW2 (数值) = \n{num_dW2}")
print(f"dW2 一致: {np.allclose(dW2, num_dW2, atol=1e-6)}")

# --- 3. 训练循环 ---
lr = 1.0
print(f"\n=== 训练（lr={lr}）===")
for step in range(200):
    z1, a1, z2, a2 = forward(x, W1, b1, W2, b2)
    loss = mse_loss(a2, y)
    dW1, db1, dW2, db2 = backward(x, y, z1, a1, z2, a2, W1, W2)
    W1 -= lr * dW1
    b1 -= lr * db1
    W2 -= lr * dW2
    b2 -= lr * db2
    if step % 50 == 0 or step == 199:
        print(f"step {step:3d}: loss = {loss:.6f}, y_hat = {a2[0,0]:.6f}")

print(f"\n最终预测值: {forward(x, W1, b1, W2, b2)[3][0,0]:.6f} (目标: {y[0,0]})")
```

### 3.2 输入输出说明

**输入**：
- 特征 `x = [[1, 1]]`，形状 $ 1 \times 2 $ （一个样本，两个特征）。
- 目标 `y = [[0]]`，形状 $ 1 \times 1 $ （二分类标签）。

**前向传播输出**：
- `z1 = [[2, 1, 2]]`：隐藏层线性变换结果。
- `a1 = [[2, 1, 2]]`：ReLU 激活后，正值不变。
- `z2 = [[0]]`：输出层线性变换结果。
- `a2 = [[0.5]]`：sigmoid 输出，即预测值 $ \hat{y} = 0.5 $。
- `loss = 0.125`：均方误差。

**梯度检查输出**：
- `dW1`（解析）与 `dW1`（数值）一致。
- `dW2`（解析）与 `dW2`（数值）一致。
- 解析梯度的具体值：$ dW^{(1)} = \begin{bmatrix} 0.0625 & -0.25 & 0.0625 \\ 0.0625 & -0.25 & 0.0625 \end{bmatrix} $，$ dW^{(2)} = \begin{bmatrix} 0.25 \\ 0.125 \\ 0.25 \end{bmatrix} $。

**训练输出**：
- 经过 200 步训练，损失从 0.125 下降到接近 0，预测值 $ \hat{y} $ 从 0.5 逐步逼近目标 0。

### 3.3 关键代码解释

1. **前向传播 `forward`**：按 $ z^{(1)} = x \cdot W^{(1)} + b^{(1)} \to a^{(1)} = \text{ReLU}(z^{(1)}) \to z^{(2)} = a^{(1)} \cdot W^{(2)} + b^{(2)} \to a^{(2)} = \sigma(z^{(2)}) $ 的顺序逐层计算。`@` 是 NumPy 的矩阵乘法运算符。

2. **反向传播 `backward`**：
   - `delta2 = (a2 - y) * sigmoid_grad_from_output(a2)`：输出层误差 $ \delta^{(L)} = (\hat{y} - y) \cdot \sigma'(z^{(L)}) $。这里用了一个技巧——直接从 sigmoid 的输出 $ a $ 计算导数 $ \sigma'(z) = a(1-a) $，避免重新计算 $ \exp $。
   - `delta1 = (delta2 @ W2.T) * relu_grad(z1)`：隐藏层误差 $ \delta^{(1)} = (\delta^{(2)} \cdot (W^{(2)})^T) \odot \text{ReLU}'(z^{(1)}) $。先反传信号（乘权重转置），再通过激活函数的导数过滤。
   - `dW1 = x.T @ delta1`：参数梯度 $ \partial L / \partial W^{(1)} = x^T \cdot \delta^{(1)} $。

3. **梯度检查 `numerical_grad`**：对每个参数用中心差分法 $ (L(\theta+\varepsilon) - L(\theta-\varepsilon)) / (2\varepsilon) $ 计算数值梯度，与解析梯度对比。如果两者一致，说明反向传播的公式推导和代码实现都是正确的。这是验证 backprop 实现的标准方法。

4. **训练循环**：每步执行前向传播 → 计算损失 → 反向传播 → 参数更新。学习率 `lr = 1.0` 控制每步更新的幅度。
