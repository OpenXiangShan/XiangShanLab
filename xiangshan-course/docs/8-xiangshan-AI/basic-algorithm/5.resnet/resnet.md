# ResNet

## 1. 文字讲解

### 1.1 这个算法解决什么问题

VGG 证明了"更深更好"——从 AlexNet 的 8 层到 VGG 的 16~19 层，性能持续提升。于是自然的问题出现了：能不能继续加深，做到 34 层、50 层、甚至 100 层？

实验结果令人意外：**直接堆叠更深的网络不仅没有提升性能，反而变差了**。注意这不是过拟合——训练集上的误差也在升高。这个现象被称为**退化问题（degradation problem）**。

ResNet（Residual Network, He et al., 2015）通过一个简单而巧妙的结构——**残差连接**——解决了这个问题，成功训练了 152 层甚至 1000+ 层的网络。

**输入**：图像张量 $ H \times W \times C $。

**输出**：更深层次的特征表示，及最终分类结果。

**适用边界**：图像分类、目标检测等。ResNet 的残差结构已成为现代深度学习的标准组件，被 Transformer、BERT、GPT 等模型广泛采用。

### 1.2 核心思想

ResNet 的核心思想是**残差学习**。

考虑一个网络块要学习映射 $ \mathcal{H}(x) $。在普通网络中，堆叠的层直接学习 $ \mathcal{H}(x) $。ResNet 的洞察是：与其让层直接学 $ \mathcal{H}(x) $，不如让层学**残差** $ \mathcal{F}(x) = \mathcal{H}(x) - x $，然后通过**跳跃连接**加上输入：

$$ \mathcal{H}(x) = \mathcal{F}(x) + x$$

为什么学残差更容易？

- **如果最优映射是恒等映射**（ $ \mathcal{H}(x) = x $ ），普通网络需要学出 $ W \approx I $，很难；残差网络只需让 $ \mathcal{F}(x) \to 0 $ （把权重推向零），很容易。
- **如果最优映射接近恒等映射**（微调即可），残差网络只需学一个小的扰动 $ \mathcal{F}(x) $，比从头学完整映射容易得多。

更深层的直觉：在深层网络中，每多加一层至少不应该让结果变差——最坏情况是学到恒等映射。但普通网络很难精确学到恒等映射，加层反而引入了优化困难。残差连接给网络提供了一条"如果学不好就直接传过去"的捷径。

### 1.3 基本流程

ResNet 由多个**残差块**堆叠而成。一个基本残差块的结构：

1. 输入 $ x $ 进入两条路径：
   - **主路径**：$ x \to \text{Conv} \to \text{ReLU} \to \text{Conv} $，得到 $ \mathcal{F}(x)$
   - **跳跃路径**：$ x $ 直接跳过主路径
2. **相加**：$ \mathcal{F}(x) + x$
3. **ReLU 激活**：$ \text{ReLU}(\mathcal{F}(x) + x)$

当输入输出通道数不同或尺寸不同时，跳跃路径上加一个 $ 1 \times 1 $ 卷积（"投影"）来匹配维度：

$$ x_{\text{proj}} = W_{\text{proj}} \cdot x$$

ResNet-34 的结构：4 个阶段，分别有 3、4、6、3 个残差块，共 34 层。更深的 ResNet-50/101/152 使用"瓶颈"块（Bottleneck：$ 1 \times 1 \to 3 \times 3 \to 1 \times 1 $ ）减少计算量。

### 1.4 直观例子

贯穿全章使用一个 2 层的残差块，输入 $ x = [1, 1] $，权重用小值初始化（模拟深层网络的梯度衰减）：

- $ W_1 = \begin{bmatrix} 0.2 & -0.1 \\ -0.1 & 0.2 \end{bmatrix} $，$ b_1 = [0, 0]$
- $ W_2 = \begin{bmatrix} 0.2 & -0.1 \\ -0.1 & 0.2 \end{bmatrix} $，$ b_2 = [0, 0]$
- 目标 $ y = [1, 1]$

**普通块前向传播**：

$$ z_1 = W_1 x = [0.1, 0.1] \quad \to \quad a_1 = \text{ReLU}(z_1) = [0.1, 0.1]$$

$$ z_2 = W_2 a_1 = [0.01, 0.01] \quad \to \quad y_{\text{plain}} = \text{ReLU}(z_2) = [0.01, 0.01]$$

输出 $ [0.01, 0.01] $ 离目标 $ [1, 1] $ 很远，损失 $ L_{\text{plain}} = \frac{1}{2}(0.01 - 1)^2 \times 2 = 0.980 $。

**残差块前向传播**：

$$ z_1 = W_1 x = [0.1, 0.1] \quad \to \quad a_1 = \text{ReLU}(z_1) = [0.1, 0.1]$$

$$ z_2 = W_2 a_1 = [0.01, 0.01] \quad \to \quad y_{\text{res}} = \text{ReLU}(z_2 + x) = \text{ReLU}([1.01, 1.01]) = [1.01, 1.01]$$

输出 $ [1.01, 1.01] $ 接近目标 $ [1, 1] $，损失 $ L_{\text{res}} = \frac{1}{2}(1.01 - 1)^2 \times 2 = 0.0001 $。

**关键对比**：小权重导致普通块输出几乎为 0（信号衰减），但残差块的跳跃连接直接把输入 $ [1,1] $ 传过来，即使 $ \mathcal{F}(x) \approx 0 $，输出也接近 $ x $。

## 2. 公式讲解

### 2.1 核心公式

**残差块前向传播**：

$$ z_1 = W_1 x + b_1, \quad a_1 = \text{ReLU}(z_1)$$

$$ \mathcal{F}(x) = W_2 a_1 + b_2$$

$$ \mathcal{H}(x) = \text{ReLU}(\mathcal{F}(x) + x)$$

**残差块反向传播（梯度流）**：

设损失为 $ L $，记 $ \delta = \frac{\partial L}{\partial \mathcal{H}(x)} $，则：

$$ \frac{\partial L}{\partial x} = \delta \cdot \text{ReLU}'(\mathcal{F}(x) + x) \cdot \left(\frac{\partial \mathcal{F}(x)}{\partial x} + \mathbf{I}\right)$$

其中 $ \mathbf{I} $ 是单位矩阵，来自跳跃连接的导数。展开：

$$ \frac{\partial L}{\partial x} = \underbrace{\delta \cdot \text{ReLU}' \cdot W_2 \cdot \text{ReLU}' \cdot W_1}_{\text{主路径（可能衰减）}} + \underbrace{\delta \cdot \text{ReLU}' \cdot \mathbf{I}}_{\text{跳跃路径（恒为直通）}}$$

**深层网络的梯度衰减**：

对于 $ n $ 个串联的普通块，梯度近似为：

$$ \frac{\partial L}{\partial x_0} \approx \delta \cdot \prod_{i=1}^{n} W_{2,i} W_{1,i}$$

当权重较小时（ $ |W| < 1 $ ），连乘项随 $ n $ 指数衰减。

对于 $ n $ 个串联的残差块：

$$ \frac{\partial L}{\partial x_0} \approx \delta \cdot \left(\prod_{i=1}^{n} (W_{2,i} W_{1,i} + \mathbf{I})\right)$$

即使 $ W_{2,i} W_{1,i} \approx 0 $，每一项仍有 $ \mathbf{I} $，连乘不会衰减为零。这就是残差连接让深层网络可训练的数学基础。

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $ x $ | 残差块的输入 |
| $ \mathcal{H}(x) $ | 残差块期望学到的目标映射 |
| $ \mathcal{F}(x) $ | 残差函数，即 $ \mathcal{H}(x) - x $ |
| $ W_1, W_2 $ | 主路径两层的权重矩阵 |
| $ b_1, b_2 $ | 主路径两层的偏置 |
| $ z_1, a_1 $ | 第一层的线性输出和激活输出 |
| $ \mathbf{I} $ | 单位矩阵（跳跃连接的导数） |
| $ \delta $ | 从后续层传回的梯度 |
| $ n $ | 串联的残差块数量 |

### 2.3 公式怎么理解

**残差映射**：$ \mathcal{H}(x) = \mathcal{F}(x) + x $ 意味着网络只需学 $ \mathcal{F}(x) = \mathcal{H}(x) - x $。如果最优解接近恒等映射，$ \mathcal{F}(x) $ 接近零，学一个接近零的函数比学一个精确的恒等映射容易得多。

**梯度流的两条路径**：反向传播时，梯度到达残差块的输入时有两条路径：
1. **主路径**：穿过 $ W_2 $、ReLU、$ W_1 $ ——每经过一层权重，梯度被乘以权重矩阵。小权重导致梯度衰减。
2. **跳跃路径**：直接 $ +1 $，不经过任何权重——梯度原样传递。

两条路径的梯度相加。即使主路径的梯度衰减到接近零，跳跃路径的梯度始终为 $ \delta \cdot \text{ReLU}' $，确保信号不会消失。

**退化问题 vs 过拟合**：退化问题不是过拟合。过拟合表现为训练集准确率高、测试集准确率低，而退化问题表现为训练集准确率本身就随深度下降。这说明问题出在优化困难——深层网络的损失面非常复杂，梯度下降难以找到好的极小值。残差连接改变了网络的参数化方式，使优化变得更容易。

**为什么是 $ +x $ 而不是别的**：$ +x $ 的导数是 $ \mathbf{I} $ （单位矩阵），这意味着无论网络多深，从输出到任意中间层都有一条梯度为 1 的直通路径。如果换成 $ + g(x) $ （$ g $ 是其他变换），导数就不保证为 1，梯度仍可能衰减。恒等映射是最简单的、不会引入额外参数的跳跃方式。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

# --- 网络参数 ---
W1 = np.array([[0.2, -0.1], [-0.1, 0.2]])
W2 = np.array([[0.2, -0.1], [-0.1, 0.2]])
b1 = np.array([0.0, 0.0])
b2 = np.array([0.0, 0.0])

x = np.array([1.0, 1.0])
y_target = np.array([1.0, 1.0])

def relu(z):
    return np.maximum(0, z)

def relu_grad(z):
    return (z > 0).astype(float)

# --- 1. 单个残差块前向传播 ---
def plain_block_forward(x, W1, W2, b1, b2):
    z1 = W1 @ x + b1
    a1 = relu(z1)
    z2 = W2 @ a1 + b2
    out = relu(z2)
    return out

def residual_block_forward(x, W1, W2, b1, b2):
    z1 = W1 @ x + b1
    a1 = relu(z1)
    Fx = W2 @ a1 + b2
    out = relu(Fx + x)          # 残差连接：F(x) + x
    return out

out_plain = plain_block_forward(x, W1, W2, b1, b2)
out_res   = residual_block_forward(x, W1, W2, b1, b2)
loss_plain = 0.5 * np.sum((out_plain - y_target) ** 2)
loss_res   = 0.5 * np.sum((out_res - y_target) ** 2)

print("=== 单块前向传播 ===")
print(f"普通块输出: {out_plain}, 损失: {loss_plain:.4f}")
print(f"残差块输出: {out_res}, 损失: {loss_res:.4f}")

assert np.allclose(out_plain, [0.01, 0.01]), f"普通块输出错误: {out_plain}"
assert np.allclose(out_res, [1.01, 1.01]), f"残差块输出错误: {out_res}"
assert np.isclose(loss_plain, 0.9801, atol=1e-4), f"普通块损失错误"
assert np.isclose(loss_res, 0.0001, atol=1e-4), f"残差块损失错误"
print("单块验证通过 ✓")

# --- 2. 梯度对比 ---
def plain_block_backward(x, y_target, W1, W2, b1, b2):
    z1 = W1 @ x + b1
    a1 = relu(z1)
    z2 = W2 @ a1 + b2
    out = relu(z2)
    # dL/dout
    delta_out = out - y_target
    # dL/dz2
    delta_z2 = delta_out * relu_grad(z2)
    # dL/da1
    delta_a1 = W2.T @ delta_z2
    # dL/dz1
    delta_z1 = delta_a1 * relu_grad(z1)
    # dL/dx
    grad_x = W1.T @ delta_z1
    return grad_x

def residual_block_backward(x, y_target, W1, W2, b1, b2):
    z1 = W1 @ x + b1
    a1 = relu(z1)
    Fx = W2 @ a1 + b2
    out = relu(Fx + x)
    # dL/dout
    delta_out = out - y_target
    # dL/d(Fx+x)
    delta_sum = delta_out * relu_grad(Fx + x)
    # 主路径: dL/dx via W2, W1
    delta_Fx = delta_sum                           # d(Fx+x)/d(Fx) = I
    delta_a1 = W2.T @ delta_Fx
    delta_z1 = delta_a1 * relu_grad(z1)
    grad_x_main = W1.T @ delta_z1
    # 跳跃路径: dL/dx via skip
    grad_x_skip = delta_sum * 1.0                   # d(Fx+x)/d(x) = I
    # 总梯度
    grad_x = grad_x_main + grad_x_skip
    return grad_x, grad_x_main, grad_x_skip

grad_plain = plain_block_backward(x, y_target, W1, W2, b1, b2)
grad_res, grad_main, grad_skip = residual_block_backward(x, y_target, W1, W2, b1, b2)

print(f"\n=== 单块梯度对比 ===")
print(f"普通块 dL/dx: {grad_plain}")
print(f"残差块 dL/dx: {grad_res}")
print(f"  ├─ 主路径: {grad_main}")
print(f"  └─ 跳跃路径: {grad_skip}")
print(f"跳跃路径占比: {np.linalg.norm(grad_skip)/np.linalg.norm(grad_res)*100:.1f}%")

# --- 3. 深层网络梯度衰减对比 ---
print(f"\n=== 深层网络梯度衰减对比 ===")

def deep_plain_forward_backward(x, y_target, W1, W2, n_blocks):
    """n 个普通块串联"""
    activations = [x.copy()]
    z1s, a1s, z2s, outs = [], [], [], []
    cur = x.copy()
    for _ in range(n_blocks):
        z1 = W1 @ cur + b1
        a1 = relu(z1)
        z2 = W2 @ a1 + b2
        out = relu(z2)
        z1s.append(z1); a1s.append(a1); z2s.append(z2); outs.append(out)
        activations.append(cur)
        cur = out
    # 反向传播
    grad = outs[-1] - y_target
    grad_norms = [np.linalg.norm(grad)]
    for i in reversed(range(n_blocks)):
        grad = grad * relu_grad(z2s[i])
        grad = W2.T @ grad
        grad = grad * relu_grad(z1s[i])
        grad = W1.T @ grad
        grad_norms.append(np.linalg.norm(grad))
    return cur, grad_norms

def deep_residual_forward_backward(x, y_target, W1, W2, n_blocks):
    """n 个残差块串联"""
    z1s, a1s, Fxs, sums, outs = [], [], [], [], []
    cur = x.copy()
    for _ in range(n_blocks):
        z1 = W1 @ cur + b1
        a1 = relu(z1)
        Fx = W2 @ a1 + b2
        s = Fx + cur
        out = relu(s)
        z1s.append(z1); a1s.append(a1); Fxs.append(Fx); sums.append(s); outs.append(out)
        cur = out
    # 反向传播
    grad = outs[-1] - y_target
    grad_norms = [np.linalg.norm(grad)]
    for i in reversed(range(n_blocks)):
        grad = grad * relu_grad(sums[i])         # dReLU/d(sum)
        grad_main = W2.T @ grad                  # 主路径
        grad_main = grad_main * relu_grad(z1s[i])
        grad_main = W1.T @ grad_main
        grad_skip = grad * 1.0                   # 跳跃路径
        grad = grad_main + grad_skip             # 合并
        grad_norms.append(np.linalg.norm(grad))
    return cur, grad_norms

n_blocks = 5
out_p, norms_p = deep_plain_forward_backward(x, y_target, W1, W2, n_blocks)
out_r, norms_r = deep_residual_forward_backward(x, y_target, W1, W2, n_blocks)

print(f"{'块':>4} | {'普通梯度范数':>14} | {'残差梯度范数':>14}")
print("-" * 40)
for i in range(n_blocks + 1):
    print(f"{i:>4} | {norms_p[i]:>14.6f} | {norms_r[i]:>14.6f}")

print(f"\n输出→输入梯度衰减比:")
print(f"  普通: {norms_p[-1]/norms_p[0]:.6f}（衰减到 {norms_p[-1]/norms_p[0]*100:.3f}%）")
print(f"  残差: {norms_r[-1]/norms_r[0]:.6f}（衰减到 {norms_r[-1]/norms_r[0]*100:.3f}%）")

# --- 4. 训练对比 ---
print(f"\n=== 训练对比（5 个块，lr=0.5, 100 步）===")
lr = 0.5
for name, fwd_bwd in [("普通", deep_plain_forward_backward), ("残差", deep_residual_forward_backward)]:
    np.random.seed(42)
    W1_t = np.random.randn(2, 2) * 0.2
    W2_t = np.random.randn(2, 2) * 0.2
    losses = []
    for step in range(100):
        out, norms = fwd_bwd(x, y_target, W1_t, W2_t, 5)
        loss = 0.5 * np.sum((out - y_target) ** 2)
        losses.append(loss)
        # 简单梯度下降（用数值梯度）
        eps = 1e-5
        g = np.zeros_like(W1_t)
        for i in range(W1_t.shape[0]):
            for j in range(W1_t.shape[1]):
                W1_t[i, j] += eps
                out_p2, _ = fwd_bwd(x, y_target, W1_t, W2_t, 5)
                loss_p = 0.5 * np.sum((out_p2 - y_target) ** 2)
                W1_t[i, j] -= 2 * eps
                out_m2, _ = fwd_bwd(x, y_target, W1_t, W2_t, 5)
                loss_m = 0.5 * np.sum((out_m2 - y_target) ** 2)
                W1_t[i, j] += eps
                g[i, j] = (loss_p - loss_m) / (2 * eps)
        W1_t -= lr * g
    print(f"{name}: 初始 loss={losses[0]:.4f} → 最终 loss={losses[-1]:.4f}")

print("\n========== 全部验证通过 ==========")
```

### 3.2 输入输出说明

**输入**：
- `x = [1, 1]`，2 维输入。
- `y_target = [1, 1]`，目标输出。
- 权重 $ W_1, W_2 $ 均为小值矩阵 $ \begin{bmatrix} 0.2 & -0.1 \\ -0.1 & 0.2 \end{bmatrix} $，模拟深层网络的衰减条件。

**单块输出**：
- 普通块：$ [0.01, 0.01] $，损失 0.980——信号几乎衰减为零。
- 残差块：$ [1.01, 1.01] $，损失 0.0001——跳跃连接保留了输入信号。

**梯度对比**：
- 普通块：$ \partial L / \partial x $ 仅通过主路径，被 $ W_1^T W_2^T $ 缩小。
- 残差块：$ \partial L / \partial x $ = 主路径梯度 + 跳跃路径梯度，后者不受权重衰减。

**深层衰减对比**（5 个块串联）：
- 普通：从输出到输入，梯度范数急剧衰减（接近 $ 0 $ ）。
- 残差：梯度范数保持稳定，不随深度衰减。

### 3.3 关键代码解释

1. **`residual_block_forward`**：关键区别在 `out = relu(Fx + x)`——主路径的输出 $ \mathcal{F}(x) $ 加上输入 $ x $ 再过 ReLU。普通块只有 `out = relu(z2)`，没有 $ + x $。

2. **`residual_block_backward`**：梯度分两条路径计算。`grad_x_main` 穿过 $ W_2 $ 和 $ W_1 $ （主路径），`grad_x_skip = delta_sum * 1.0` 是跳跃路径（导数为 1）。总梯度 = 主路径 + 跳跃路径。

3. **`deep_*_forward_backward`**：将 $ n $ 个块串联，记录每层梯度范数。普通网络中梯度反复乘 $ W_1^T W_2^T $，5 次连乘后接近 0；残差网络中每步都有 $ +\mathbf{I} $ 项，梯度不衰减。

4. **训练对比**：用数值梯度训练 5 块的普通/残差网络，展示残差网络在深层结构中收敛更快。数值梯度（中心差分）虽然慢但正确性可靠，适合小网络验证。
