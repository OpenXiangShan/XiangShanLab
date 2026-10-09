# CNN

## 1. 文字讲解

### 1.1 这个算法解决什么问题

DNN 用全连接层处理图像时有一个致命问题：一张 $28 \times 28$ 的灰度图被展平成 784 维向量后送入网络，如果第一个隐藏层有 128 个神经元，仅这一层就需要 $784 \times 128 + 128 = 100480$ 个参数。更大的图片（如 $224 \times 224$ ）参数量会爆炸式增长。

更关键的是，全连接层**破坏了图像的空间结构**——展平后，相邻像素的二维关系完全丢失，网络无法利用"局部区域内的像素通常有强相关性"这一先验知识。

卷积神经网络（Convolutional Neural Network, CNN）通过**卷积层**、**池化层**的交替堆叠，专门提取图像的空间特征。

**输入**：图像张量，形状为 $H \times W \times C$ （高、宽、通道数；灰度图 $C=1$ ，彩色图 $C=3$ ）。

**输出**：特征图（feature map），是经过卷积和池化后提取的抽象空间特征。

**适用边界**：图像分类、目标检测、语义分割等计算机视觉任务。对于序列数据，RNN/Transformer 更合适。

### 1.2 核心思想

CNN 的核心思想是**局部感知 + 参数共享**。

**局部连接**：卷积层中，每个输出像素只与输入图像的一个小区域（如 $3 \times 3$ ）相连，而非整张图。这个小区域称为**感受野**（receptive field）。就像人眼视网膜的每个感光细胞只负责视野中的一小块区域。

**参数共享**：同一个卷积核（kernel）在整张图上滑动，所有位置共用同一组参数。一个 $3 \times 3$ 的卷积核只有 9 个权重，无论图片多大。这比全连接层的参数量少了几个数量级。

直觉上理解：一个 $3 \times 3$ 的卷积核就像一个"特征探测器"，在图片上从左到右、从上到下滑动，每到一处就检查"这个小区域里有没有我要找的特征"（比如垂直边缘、水平边缘、色块）。如果找到了，输出就大；没找到，输出就小。一个卷积层可以有多个卷积核，每个负责检测不同的特征，从而产生多张特征图。

### 1.3 基本流程

一个典型的 CNN 层包含三步：

1. **卷积**：用 $K$ 个卷积核在输入图像上滑动，每个核与局部区域做逐元素相乘再求和，得到 $K$ 张特征图。
2. **激活**：对特征图逐元素施加 ReLU（或其他激活函数），引入非线性。
3. **池化**：用最大池化（或平均池化）对特征图降采样，缩小尺寸，保留最显著的响应。

这三步可以反复堆叠：每堆叠一次，特征图变小一层，但抽象程度更高——浅层检测边缘和纹理，深层检测物体部件甚至整体。

### 1.4 直观例子

贯穿全章使用一个 $5 \times 5$ 的灰度图，中心有一个 $3 \times 3$ 的亮块：

$$\text{input} = \begin{bmatrix} 0 & 0 & 0 & 0 & 0 \\ 0 & 1 & 1 & 1 & 0 \\ 0 & 1 & 1 & 1 & 0 \\ 0 & 1 & 1 & 1 & 0 \\ 0 & 0 & 0 & 0 & 0 \end{bmatrix}$$

使用两个 $3 \times 3$ 卷积核：

**核 1（全 1，检测亮度）**：

$$K_1 = \begin{bmatrix} 1 & 1 & 1 \\ 1 & 1 & 1 \\ 1 & 1 & 1 \end{bmatrix}$$

**核 2（Sobel 竖直边缘）**：

$$K_2 = \begin{bmatrix} 1 & 0 & -1 \\ 1 & 0 & -1 \\ 1 & 0 & -1 \end{bmatrix}$$

无填充（padding=0），步幅（stride=1），卷积输出尺寸为 $(5 - 3) / 1 + 1 = 3$ ，即 $3 \times 3$。

**核 1 的特征图**（手算左上角）：取输入左上 $3 \times 3$ 区域 $\begin{bmatrix} 0 & 0 & 0 \\ 0 & 1 & 1 \\ 0 & 1 & 1 \end{bmatrix}$ ，与 $K_1$ 逐元素相乘求和 $= 0+0+0+0+1+1+0+1+1 = 4$。但更直观的是：全 1 核等价于对 $3 \times 3$ 窗口求和，所以特征图反映的是每个位置的"局部亮度总和"。

手算完整特征图 1：

$$F_1 = \begin{bmatrix} 4 & 6 & 4 \\ 6 & 9 & 6 \\ 4 & 6 & 4 \end{bmatrix}$$

中心值 9 对应卷积核完全覆盖 $3 \times 3$ 亮块的位置。

**核 2 的特征图**（手算左上角）：取同一区域， $0 \times 1 + 0 \times 0 + 0 \times (-1) + 0 \times 1 + 1 \times 0 + 1 \times (-1) + 0 \times 1 + 1 \times 0 + 1 \times (-1) = -2$。核 2 检测竖直方向的亮度跳变：左→右变亮为正，变暗为负。

手算完整特征图 2：

$$F_2 = \begin{bmatrix} -2 & 0 & 2 \\ -3 & 0 & 3 \\ -2 & 0 & 2 \end{bmatrix}$$

左边 $-3$ 表示从暗到亮的竖直边缘，右边 $+3$ 表示从亮到暗的竖直边缘，中间 $0$ 表示该位置没有竖直方向的跳变。

**ReLU 后**：

$$F_1' = \begin{bmatrix} 4 & 6 & 4 \\ 6 & 9 & 6 \\ 4 & 6 & 4 \end{bmatrix} \quad (\text{已全部非负，不变})$$

$$F_2' = \begin{bmatrix} 0 & 0 & 2 \\ 0 & 0 & 3 \\ 0 & 0 & 2 \end{bmatrix} \quad (\text{负值截断为 0})$$

**最大池化 $2 \times 2$ ，步幅 1**（输出尺寸 $(3-2)/1+1 = 2$ ，即 $2 \times 2$ ）：

$$\text{Pool}(F_1') = \begin{bmatrix} \max(4,6,6,9) & \max(6,4,9,6) \\ \max(6,9,4,6) & \max(9,6,6,4) \end{bmatrix} = \begin{bmatrix} 9 & 9 \\ 9 & 9 \end{bmatrix}$$

$$\text{Pool}(F_2') = \begin{bmatrix} \max(0,0,0,0) & \max(0,2,0,3) \\ \max(0,0,0,0) & \max(0,3,0,2) \end{bmatrix} = \begin{bmatrix} 0 & 3 \\ 0 & 3 \end{bmatrix}$$

最终得到两个 $2 \times 2$ 的特征图，尺寸从 $5 \times 5$ 压缩到 $2 \times 2$ ，但保留了关键信息：核 1 报告"有大片亮区"，核 2 报告"右侧有竖直边缘"。

## 2. 公式讲解

### 2.1 核心公式

**二维卷积（严格说是互相关 cross-correlation，深度学习中不翻转核）：**

$$F[i, j] = \sum_{m=0}^{k_h - 1} \sum_{n=0}^{k_w - 1} X[i + m,\, j + n] \cdot K[m, n]$$

其中 $X$ 是输入图像， $K$ 是 $k_h \times k_w$ 的卷积核， $F$ 是输出的特征图。

**输出尺寸公式：**

$$H_{\text{out}} = \left\lfloor \frac{H_{\text{in}} - k_h + 2P}{S} \right\rfloor + 1$$

$$W_{\text{out}} = \left\lfloor \frac{W_{\text{in}} - k_w + 2P}{S} \right\rfloor + 1$$

其中 $P$ 是零填充（padding）大小， $S$ 是步幅（stride）。

**多通道卷积**（输入有 $C_{\text{in}}$ 个通道时）：

$$F[i, j] = \sum_{c=0}^{C_{\text{in}} - 1} \sum_{m=0}^{k_h - 1} \sum_{n=0}^{k_w - 1} X_c[i + m,\, j + n] \cdot K_c[m, n] + b$$

每个卷积核在所有输入通道上做卷积后求和，再加偏置 $b$。 $K$ 个这样的核产生 $K$ 张输出特征图。

**ReLU 激活：**

$$F'[i, j] = \max(0,\, F[i, j])$$

**最大池化（ $p \times p$ 窗口，步幅 $S_p$ ）：**

$$P_{\text{out}}[i, j] = \max_{0 \leq m < p,\; 0 \leq n < p} F'\left[i \cdot S_p + m,\; j \cdot S_p + n\right]$$

**参数量对比：**

| 方式 | 参数量（输入 $5 \times 5$ ，输出 $3 \times 3 \times 2$ ） |
|------|------|
| 全连接（DNN） | $5 \times 5 \times 18 + 18 = 468$ |
| 卷积（CNN，2 个 $3 \times 3$ 核） | $2 \times 3 \times 3 + 2 = 20$ |

CNN 参数量仅为 DNN 的 $4.3\%$ ，而且无论输入图片多大，卷积核参数量不变。

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $X$ | 输入图像，形状 $H_{\text{in}} \times W_{\text{in}} \times C_{\text{in}}$ |
| $K$ | 卷积核，形状 $k_h \times k_w \times C_{\text{in}}$ （单核）或 $k_h \times k_w \times C_{\text{in}} \times C_{\text{out}}$ （多核） |
| $b$ | 偏置标量，每个输出通道一个 |
| $F$ | 卷积输出（特征图），形状 $H_{\text{out}} \times W_{\text{out}} \times C_{\text{out}}$ |
| $P$ | 零填充（padding）大小 |
| $S$ | 步幅（stride），卷积核每次移动的像素数 |
| $k_h, k_w$ | 卷积核的高和宽 |
| $C_{\text{in}}$ | 输入通道数（灰度图为 1，彩色图为 3） |
| $C_{\text{out}}$ | 输出通道数（= 卷积核数量） |
| $F'$ | ReLU 后的特征图 |
| $P_{\text{out}}$ | 池化输出 |
| $p$ | 池化窗口大小 |
| $S_p$ | 池化步幅 |

### 2.3 公式怎么理解

**卷积公式**：核心操作是"滑动窗口 + 逐元素相乘 + 求和"。每个输出位置 $[i, j]$ 的值，是输入图上一个 $k_h \times k_w$ 的局部窗口与卷积核的内积。内积越大，说明该区域的特征与核所检测的特征越相似——这与 FFT 章中学的内积/相关性检测在数学上是同构的。

**输出尺寸公式**：输入大小减去核大小（因为核不能超出边界），加上两倍填充（每边加 $P$ ），除以步幅（每次跳 $S$ 个像素），向下取整加 1。无填充、步幅 1 时，输出尺寸 $= H - k + 1$。

**多通道卷积**：当输入是彩色图（3 通道）时，一个卷积核不是 $3 \times 3$ ，而是 $3 \times 3 \times 3$ （高 $\times$ 宽 $\times$ 通道）。核在所有通道上分别卷积后求和，得到一张特征图。有 $C_{\text{out}}$ 个核就产生 $C_{\text{out}}$ 张特征图。

**参数共享**：同一个核在图片的所有位置滑动，共用 9 个权重（ $3 \times 3$ 核）。这就是"局部连接 + 参数共享"的数学体现——每个输出位置只看局部窗口（局部连接），且所有位置用同一个核（参数共享）。

**最大池化**：在 $p \times p$ 窗口内取最大值。直觉是：如果一个特征在某个区域内被检测到（响应值大），那么它的精确位置并不重要——重要的是"这里有这个特征"。池化让特征对小幅度的平移具有不变性，同时缩小特征图、减少后续计算量。

**参数量对比**：全连接层中，输入的每个像素都与输出的每个神经元相连，参数量为 $H \times W \times C_{\text{in}} \times C_{\text{out}}$。卷积层中，每个核只有 $k_h \times k_w \times C_{\text{in}}$ 个参数，且在所有位置共享，参数量为 $k_h \times k_w \times C_{\text{in}} \times C_{\text{out}}$。当 $H, W \gg k$ 时，差距极其悬殊。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

# --- 输入图像 (5×5) ---
X = np.array([
    [0, 0, 0, 0, 0],
    [0, 1, 1, 1, 0],
    [0, 1, 1, 1, 0],
    [0, 1, 1, 1, 0],
    [0, 0, 0, 0, 0],
], dtype=float)

# --- 两个 3×3 卷积核 ---
K1 = np.array([
    [1, 1, 1],
    [1, 1, 1],
    [1, 1, 1],
], dtype=float)

K2 = np.array([
    [1,  0, -1],
    [1,  0, -1],
    [1,  0, -1],
], dtype=float)

kernels = [K1, K2]

# --- 卷积运算 ---
def conv2d(X, K, stride=1, padding=0):
    if padding > 0:
        X = np.pad(X, padding, mode='constant')
    kh, kw = K.shape
    h_out = (X.shape[0] - kh) // stride + 1
    w_out = (X.shape[1] - kw) // stride + 1
    F = np.zeros((h_out, w_out))
    for i in range(h_out):
        for j in range(w_out):
            region = X[i*stride:i*stride+kh, j*stride:j*stride+kw]
            F[i, j] = np.sum(region * K)
    return F

# --- ReLU ---
def relu(F):
    return np.maximum(0, F)

# --- 最大池化 ---
def max_pool2d(F, pool_size=2, stride=1):
    h_out = (F.shape[0] - pool_size) // stride + 1
    w_out = (F.shape[1] - pool_size) // stride + 1
    P = np.zeros((h_out, w_out))
    for i in range(h_out):
        for j in range(w_out):
            region = F[i*stride:i*stride+pool_size, j*stride:j*stride+pool_size]
            P[i, j] = np.max(region)
    return P

# --- 1. 卷积 ---
F1 = conv2d(X, K1)
F2 = conv2d(X, K2)

print("=== 卷积 ===")
print(f"特征图 1 (核1=全1):\n{F1}")
print(f"特征图 2 (核2=Sobel竖直):\n{F2}")

# --- 2. ReLU ---
F1_relu = relu(F1)
F2_relu = relu(F2)

print(f"\n=== ReLU ===")
print(f"特征图 1 ReLU:\n{F1_relu}")
print(f"特征图 2 ReLU:\n{F2_relu}")

# --- 3. 最大池化 ---
P1 = max_pool2d(F1_relu, pool_size=2, stride=1)
P2 = max_pool2d(F2_relu, pool_size=2, stride=1)

print(f"\n=== 最大池化 (2×2, stride=1) ===")
print(f"池化 1:\n{P1}")
print(f"池化 2:\n{P2}")

# --- 4. 验证手算结果 ---
print(f"\n=== 验证 ===")
expected_F1 = np.array([[3,5,3],[5,9,5],[3,5,3]], dtype=float)
expected_F2 = np.array([[-2,0,2],[-3,0,3],[-2,0,2]], dtype=float)
expected_P1 = np.array([[9,9],[9,9]], dtype=float)
expected_P2 = np.array([[0,3],[0,3]], dtype=float)

print(f"F1 一致: {np.allclose(F1, expected_F1)}")
print(f"F2 一致: {np.allclose(F2, expected_F2)}")
print(f"P1 一致: {np.allclose(P1, expected_P1)}")
print(f"P2 一致: {np.allclose(P2, expected_P2)}")

# --- 5. 参数量对比 ---
print(f"\n=== 参数量对比 ===")
# DNN: 5×5=25 输入 → 3×3×2=18 输出
dnn_params = 25 * 18 + 18
# CNN: 2 个 3×3 核 + 2 个偏置
cnn_params = 2 * (3 * 3) + 2
print(f"DNN 参数量: {dnn_params}")
print(f"CNN 参数量: {cnn_params}")
print(f"CNN 仅为 DNN 的 {cnn_params/dnn_params*100:.1f}%")

# --- 6. 带填充和步幅的卷积 ---
print(f"\n=== padding=1, stride=1 (same模式) ===")
F1_pad = conv2d(X, K1, stride=1, padding=1)
print(f"padding=1 输出尺寸: {F1_pad.shape}")
print(f"特征图 1 (padding=1):\n{F1_pad}")
assert F1_pad.shape == (5, 5), "padding=1 时输出应与输入同尺寸"

print(f"\n=== stride=2 ===")
F1_s2 = conv2d(X, K1, stride=2, padding=0)
print(f"stride=2 输出尺寸: {F1_s2.shape}")
print(f"特征图 1 (stride=2):\n{F1_s2}")
assert F1_s2.shape == (2, 2), "stride=2 时输出尺寸应为 2×2"

print("\n全部验证通过 ✓")
```

### 3.2 输入输出说明

**输入**：
- 图像 `X`： $5 \times 5$ 灰度图，中心 $3 \times 3$ 为 1，其余为 0。
- 核 `K1`： $3 \times 3$ 全 1 核，检测局部亮度总和。
- 核 `K2`： $3 \times 3$ Sobel 竖直边缘检测核。

**卷积输出**：
- `F1`： $3 \times 3$ 特征图，值为 $\begin{bmatrix} 4 & 6 & 4 \\ 6 & 9 & 6 \\ 4 & 6 & 4 \end{bmatrix}$ ，中心 9 对应核完全覆盖亮块的位置。
- `F2`： $3 \times 3$ 特征图，值为 $\begin{bmatrix} -2 & 0 & 2 \\ -3 & 0 & 3 \\ -2 & 0 & 2 \end{bmatrix}$ ，左侧 $-3$ 表示暗→亮的竖直边缘，右侧 $+3$ 表示亮→暗的竖直边缘。

**ReLU 后**：
- `F1_relu`：全正，不变。
- `F2_relu`：负值截断为 0，变为 $\begin{bmatrix} 0 & 0 & 2 \\ 0 & 0 & 3 \\ 0 & 0 & 2 \end{bmatrix}$。

**池化后**：
- `P1`： $2 \times 2$ ，全为 9（每 $2 \times 2$ 窗口的 max 都包含中心的 9）。
- `P2`： $2 \times 2$ ，值为 $\begin{bmatrix} 0 & 3 \\ 0 & 3 \end{bmatrix}$ ，保留了右侧边缘的强响应。

**参数量**：DNN = 468 个参数，CNN = 20 个参数，CNN 仅为 DNN 的 4.3%。

### 3.3 关键代码解释

1. **`conv2d` 函数**：双层循环遍历输出特征图的每个位置。`X[i*stride:i*stride+kh, j*stride:j*stride+kw]` 取出当前窗口，`np.sum(region * K)` 计算逐元素相乘后的总和——这就是卷积公式 $F[i,j] = \sum \sum X \cdot K$ 的直接实现。如果 `padding > 0`，先用 `np.pad` 在四周补零。

2. **`max_pool2d` 函数**：结构与 `conv2d` 类似，但把 `np.sum(region * K)` 换成 `np.max(region)`。在 $p \times p$ 窗口内取最大值，保留最显著的特征响应。

3. **ReLU `relu` 函数**：`np.maximum(0, F)`，将负响应截断为 0。对于核 2 的特征图，负值代表"反方向的边缘"（暗→亮），截断后只保留正向边缘（亮→暗）。

4. **参数量对比**：DNN 把 $5 \times 5 = 25$ 个输入全连接到 $3 \times 3 \times 2 = 18$ 个输出，参数 $25 \times 18 + 18 = 468$。CNN 用 2 个 $3 \times 3$ 核，参数 $2 \times 9 + 2 = 20$。差距随图片增大急剧拉大——输入改为 $100 \times 100$ 时，DNN 第一层需要约 18 万参数，CNN 仍只需 20 个。

5. **padding 和 stride**：`padding=1` 时输出与输入同尺寸（same 模式），`stride=2` 时输出尺寸减半。这两个参数控制特征图的尺寸和感受野的重叠程度。
