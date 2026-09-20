# VGG

## 1. 文字讲解

### 1.1 这个算法解决什么问题

CNN 章节中我们用 $3 \times 3$ 卷积核提取特征，但如果要检测更大尺度的特征（比如一个 $7 \times 7$ 的物体轮廓），单个 $3 \times 3$ 核的感受野不够大。

一种直接的做法是用更大的卷积核，比如 $7 \times 7$ 或 $11 \times 11$ （AlexNet 用的就是 $11 \times 11$ ）。但大核有两个问题：参数量多（ $7 \times 7 = 49$ 个权重 vs $3 \times 3 = 9$ 个），且大核不利于学习层次化的特征。

VGG（Visual Geometry Group, Oxford）提出了一个简洁有效的方案：**用多个小核堆叠替代大核**。

**输入**：图像张量 $H \times W \times C$。

**输出**：更深层次的特征图，以及最终分类结果。

**适用边界**：图像分类、特征提取。VGG 的简洁结构使其至今仍被广泛用作预训练特征提取器。

### 1.2 核心思想

VGG 的核心思想是**小卷积核堆叠**。

关键洞察：**三个 $3 \times 3$ 卷积层的堆叠，其有效感受野等于一个 $7 \times 7$ 卷积层**。

每经过一层 $3 \times 3$ 卷积（stride=1, padding=1），感受野扩大 2（因为核中心能"看到"前一层的 3 个像素，每个像素又各自看到更前层的 3 个像素）。具体来说：

- 第 1 层 $3 \times 3$：感受野 $= 3$
- 第 2 层 $3 \times 3$：感受野 $= 3 + 2 = 5$
- 第 3 层 $3 \times 3$：感受野 $= 5 + 2 = 7$

三层的参数量： $3 \times 3 \times 3 = 27$ 个权重。
一层 $7 \times 7$ 的参数量： $7 \times 7 = 49$ 个权重。

小核堆叠不仅参数更少，还多了两个非线性激活层（ReLU），使网络能学习更复杂的特征表达。

VGG 的整体结构极其规整：每个"块"由若干 $3 \times 3$ 卷积层 + 一个 $2 \times 2$ 最大池化层组成，块之间通道数翻倍（64→128→256→512），空间尺寸减半。

### 1.3 基本流程

VGG-16 的结构（16 层权重层）：

| 块 | 卷积层数 | 每层核数 | 输入尺寸 | 输出尺寸 |
|----|---------|---------|---------|---------|
| Block 1 | 2 | 64 | $224 \times 224 \times 3$ | $112 \times 112 \times 64$ |
| Block 2 | 2 | 128 | $112 \times 112 \times 64$ | $56 \times 56 \times 128$ |
| Block 3 | 3 | 256 | $56 \times 56 \times 128$ | $28 \times 28 \times 256$ |
| Block 4 | 3 | 512 | $28 \times 28 \times 256$ | $14 \times 14 \times 512$ |
| Block 5 | 3 | 512 | $14 \times 14 \times 512$ | $7 \times 7 \times 512$ |
| FC | 2 | — | $7 \times 7 \times 512$ | $4096 \to 4096$ |
| Output | 1 | — | $4096$ | $1000$ （分类） |

流程：输入图像 → 5 个卷积块（每块：多次 $3 \times 3$ conv + ReLU，然后 $2 \times 2$ max pool）→ 展平 → 3 个全连接层 → softmax 分类。

### 1.4 直观例子

贯穿全章对比"一个 $7 \times 7$ 核" vs "三个 $3 \times 3$ 核堆叠"在参数量和感受野上的差异。

使用一个 $8 \times 8$ 的输入图像：

$$X = \begin{bmatrix} 1 & 2 & 3 & 4 & 5 & 6 & 7 & 8 \\ 8 & 7 & 6 & 5 & 4 & 3 & 2 & 1 \\ 1 & 2 & 3 & 4 & 5 & 6 & 7 & 8 \\ 8 & 7 & 6 & 5 & 4 & 3 & 2 & 1 \\ 1 & 2 & 3 & 4 & 5 & 6 & 7 & 8 \\ 8 & 7 & 6 & 5 & 4 & 3 & 2 & 1 \\ 1 & 2 & 3 & 4 & 5 & 6 & 7 & 8 \\ 8 & 7 & 6 & 5 & 4 & 3 & 2 & 1 \end{bmatrix}$$

**方案 A**：一个 $7 \times 7$ 卷积层（padding=3, stride=1），输出 $8 \times 8$。

**方案 B**：三个 $3 \times 3$ 卷积层堆叠（每层 padding=1, stride=1），输出 $8 \times 8$。

两者感受野相同（ $7 \times 7$ ），输出尺寸相同（ $8 \times 8$ ），但：

- 方案 A 参数： $7 \times 7 = 49$ 个权重（单通道单核）
- 方案 B 参数： $3 \times 3 \times 3 = 27$ 个权重（单通道单核），且多 2 个非线性层

## 2. 公式讲解

### 2.1 核心公式

**感受野递推公式**：

$$R_l = R_{l-1} + (k_l - 1) \cdot \prod_{i=1}^{l-1} S_i$$

其中 $R_l$ 是第 $l$ 层的感受野大小， $R_0 = 1$ （输入层）， $k_l$ 是第 $l$ 层的卷积核大小， $S_i$ 是第 $i$ 层的步幅。

对于连续的 stride=1 卷积层，简化为：

$$R_l = R_{l-1} + (k - 1)$$

当 $k = 3$ 时： $R_l = R_{l-1} + 2$。

**参数量对比（单通道单核）**：

| 方案 | 感受野 | 参数量 | 非线性层数 |
|------|--------|--------|-----------|
| 一个 $7 \times 7$ 核 | $7 \times 7$ | 49 | 1 |
| 三个 $3 \times 3$ 核 | $7 \times 7$ | 27 | 3 |
| 一个 $5 \times 5$ 核 | $5 \times 5$ | 25 | 1 |
| 两个 $3 \times 3$ 核 | $5 \times 5$ | 18 | 2 |

**多通道参数量**（输入 $C_{\text{in}}$ 通道， $C_{\text{out}}$ 个核）：

$$\text{Params}_{\text{big}} = C_{\text{in}} \times C_{\text{out}} \times k^2 + C_{\text{out}}$$

$$\text{Params}_{\text{stack}} = n \times (C_{\text{in}} \times C_{\text{mid}} \times 3^2 + C_{\text{mid}}) + \text{后续层}$$

以 VGG Block 3 为例（ $C_{\text{in}} = 256$，三层均为 $256 \to 256$ ）：
- 等效 $7 \times 7$ 单层： $256 \times 256 \times 49 + 256 = 3{,}211{,}520$
- 三层 $3 \times 3$ 堆叠： $3 \times (256 \times 256 \times 9 + 256) = 1{,}770{,}240$

节省约 45% 参数。

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $R_l$ | 第 $l$ 层的感受野大小 |
| $k_l$ | 第 $l$ 层的卷积核大小 |
| $S_i$ | 第 $i$ 层的步幅 |
| $n$ | 堆叠的 $3 \times 3$ 卷积层数 |
| $C_{\text{in}}, C_{\text{out}}$ | 输入/输出通道数 |
| $C_{\text{mid}}$ | 堆叠中间层的通道数 |

### 2.3 公式怎么理解

**感受野递推**：每经过一层卷积，感受野在上一层的基础上扩大 $(k-1)$ ——因为核的边缘各"看到"前一层的 1 个像素，加上中间部分能间接"看到"更深层。步幅大于 1 时扩大更多，因为每层之间有下采样。

**小核堆叠优势**：三个 $3 \times 3$ （27 个权重）替代一个 $7 \times 7$ （49 个权重），参数少了 45%。但真正的优势在于多了两层非线性变换——每个 $3 \times 3$ 卷积后都接一个 ReLU，使网络能拟合更复杂的特征函数。如果没有这些非线性层，三层线性卷积仍然可以合并成一层，就失去了堆叠的意义。

**通道翻倍 + 尺寸减半**：VGG 每过一个池化层，空间尺寸减半、通道数翻倍。这是一种"信息压缩"策略：空间分辨率下降（细节丢失），但特征维度上升（语义信息增加），总计算量大致保持平衡。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

# --- 输入 8×8 ---
X = np.array([
    [1, 2, 3, 4, 5, 6, 7, 8],
    [8, 7, 6, 5, 4, 3, 2, 1],
    [1, 2, 3, 4, 5, 6, 7, 8],
    [8, 7, 6, 5, 4, 3, 2, 1],
    [1, 2, 3, 4, 5, 6, 7, 8],
    [8, 7, 6, 5, 4, 3, 2, 1],
    [1, 2, 3, 4, 5, 6, 7, 8],
    [8, 7, 6, 5, 4, 3, 2, 1],
], dtype=float)

# --- 卷积函数（CNN 章已实现）---
def conv2d(X, K, stride=1, padding=0):
    if padding > 0:
        X = np.pad(X, padding, mode='constant')
    kh, kw = K.shape
    h_out = (X.shape[0] - kh) // stride + 1
    w_out = (X.shape[1] - kw) // stride + 1
    F = np.zeros((h_out, w_out))
    for i in range(h_out):
        for j in range(w_out):
            F[i, j] = np.sum(X[i*stride:i*stride+kh, j*stride:j*stride+kw] * K)
    return F

def relu(F):
    return np.maximum(0, F)

def max_pool2d(F, pool_size=2, stride=2):
    h_out = (F.shape[0] - pool_size) // stride + 1
    w_out = (F.shape[1] - pool_size) // stride + 1
    P = np.zeros((h_out, w_out))
    for i in range(h_out):
        for j in range(w_out):
            P[i, j] = np.max(F[i*stride:i*stride+pool_size, j*stride:j*stride+pool_size])
    return P

# --- 方案 A：一个 7×7 核 ---
np.random.seed(42)
K_big = np.random.randn(7, 7)
F_A = conv2d(X, K_big, stride=1, padding=3)   # padding=3 保持 8×8
F_A = relu(F_A)
params_A = K_big.size
print("=== 方案 A: 一个 7×7 核 ===")
print(f"输出尺寸: {F_A.shape}, 参数量: {params_A}")

# --- 方案 B：三个 3×3 核堆叠 ---
K1 = np.random.randn(3, 3)
K2 = np.random.randn(3, 3)
K3 = np.random.randn(3, 3)

F_B = conv2d(X, K1, stride=1, padding=1)   # 8×8
F_B = relu(F_B)
F_B = conv2d(F_B, K2, stride=1, padding=1)  # 8×8
F_B = relu(F_B)
F_B = conv2d(F_B, K3, stride=1, padding=1)  # 8×8
F_B = relu(F_B)
params_B = K1.size + K2.size + K3.size
print(f"\n=== 方案 B: 三个 3×3 核 ===")
print(f"输出尺寸: {F_B.shape}, 参数量: {params_B}")

# --- 1. 感受野验证 ---
print(f"\n=== 感受野验证 ===")
def receptive_field(num_layers, k=3):
    """计算 n 层 k×k 卷积的感受野"""
    r = 1
    for _ in range(num_layers):
        r = r + (k - 1)
    return r

for n in range(1, 5):
    r = receptive_field(n)
    equiv = 2 * n + 1   # n 层 3×3 等效 2n+1 的单层
    print(f"  {n} 层 3×3 → 感受野 {r}×{r}（等效单个 {equiv}×{equiv} 核）")

assert receptive_field(3) == 7, "三层 3×3 感受野应为 7"
assert receptive_field(2) == 5, "两层 3×3 感受野应为 5"
print("感受野验证通过 ✓")

# --- 2. 参数量对比 ---
print(f"\n=== 参数量对比 ===")
print(f"方案 A (7×7):  {params_A} 个权重, 1 个 ReLU")
print(f"方案 B (3×3×3): {params_B} 个权重, 3 个 ReLU")
print(f"参数节省: {(1 - params_B/params_A)*100:.1f}%")
assert params_B == 27 and params_A == 49
print("参数量验证通过 ✓")

# --- 3. 多通道参数量对比 ---
print(f"\n=== 多通道参数量对比 (C_in=256, C_out=256) ===")
C = 256
big_params = C * C * 49 + C
stack_params = 3 * (C * C * 9 + C)
print(f"一个 7×7 层:  {big_params:,} 参数")
print(f"三个 3×3 层:  {stack_params:,} 参数")
print(f"节省: {(1 - stack_params/big_params)*100:.1f}%")
assert big_params == 3211264
assert stack_params == 1770048
print("多通道参数量验证通过 ✓")

# --- 4. VGG Block 模拟 ---
print(f"\n=== VGG Block 1 模拟 (2 层 3×3 conv + 2×2 pool) ===")
# 用 8×8 输入模拟一个 VGG block
block_input = X  # 8×8
conv1 = relu(conv2d(block_input, K1, stride=1, padding=1))  # 8×8
conv2 = relu(conv2d(conv1, K2, stride=1, padding=1))        # 8×8
pooled = max_pool2d(conv2, pool_size=2, stride=2)           # 4×4
print(f"输入: {block_input.shape} → conv1: {conv1.shape} → conv2: {conv2.shape} → pool: {pooled.shape}")
assert pooled.shape == (4, 4), "池化后应为 4×4"
print(f"参数量: 2 × 9 = {2*9} 个权重（单通道）")
print("VGG Block 验证通过 ✓")

print("\n========== 全部验证通过 ==========")
```

### 3.2 输入输出说明

**输入**：`X`， $8 \times 8$ 灰度图，值为交替递增/递减模式。

**方案 A 输出**：
- `F_A`： $8 \times 8$ 特征图（padding=3 保持尺寸）。
- 参数量：49 个权重，1 个 ReLU 非线性层。

**方案 B 输出**：
- `F_B`： $8 \times 8$ 特征图（三层 padding=1 保持尺寸）。
- 参数量：27 个权重，3 个 ReLU 非线性层。

**关键对比**：两方案感受野相同（ $7 \times 7$ ），但方案 B 参数少 44.9%，且多 2 层非线性变换。

**VGG Block**： $8 \times 8$ 输入 → 两层 $3 \times 3$ conv → $8 \times 8$ → $2 \times 2$ max pool → $4 \times 4$，空间尺寸减半。

### 3.3 关键代码解释

1. **三层卷积堆叠**：依次调用 `conv2d` + `relu` 三次，每次 `padding=1` 保持尺寸不变。这正是 VGG Block 的核心结构。

2. **`receptive_field` 函数**：递推公式 $R_l = R_{l-1} + (k-1)$ 的实现。 $n$ 层 $3 \times 3$ 卷积的感受野 $= 1 + 2n = 2n+1$，三层即 $7$。

3. **参数量计算**：单通道时直接数权重个数（ $7 \times 7 = 49$ vs $3 \times 3 \times 3 = 27$ ）。多通道时考虑核在所有输入通道上做卷积，每层参数 $= C_{\text{in}} \times C_{\text{out}} \times k^2 + C_{\text{out}}$。

4. **VGG Block 模拟**：两层卷积 + 一次池化，展示"卷积保持尺寸、池化减半尺寸"的 VGG 设计模式。实际 VGG 中每层有 64~512 个通道，这里用单通道演示结构。
