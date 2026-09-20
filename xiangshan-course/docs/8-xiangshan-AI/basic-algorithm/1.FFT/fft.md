# FFT

## 1. 文字讲解

### 1.1 这个算法解决什么问题

假设有一段离散信号 $ x = [1, 1, 1, 1, 0, 0, 0, 0] $ （8 个采样点），我们想知道这段信号里包含哪些频率成分——比如有没有某个周期性分量，幅度多大。这就是从**时域**（信号随时间变化）到**频域**（信号随频率分布）的转换问题。

**输入**：长度为 $ N $ 的离散信号序列 $ x_0, x_1, \dots, x_{N-1} $。

**输出**：长度为 $ N $ 的频谱序列 $ X_0, X_1, \dots, X_{N-1} $，每个 $ X_k $ 对应归一化频率 $ k/N $ （即实际频率 $ k \cdot f_s / N $，其中 $ f_s $ 为采样率）处的复数幅度和相位。

**适用边界**：信号必须是离散且有限长的（或通过窗函数截断为有限长）。FFT 要求 $ N $ 最好是 2 的整数次幂（基-2 FFT），否则可以使用混合基或其他 FFT 变体。

### 1.2 核心思想

直接按定义计算 DFT 需要对每个 $ X_k $ 做一次 $ N $ 项加权求和，总共 $ N $ 个 $ k $，计算量为 $ O(N^2) $。当 $ N $ 较大时（比如 $ N = 1024 $ ），需要超过 100 万次复数乘法。

FFT 的核心思想是利用**旋转因子的周期性和对称性**，将一个 $ N $ 点 DFT 拆解为两个 $ N/2 $ 点 DFT，再通过**蝶形运算**合并结果。这样递归分解下去，计算量降为 $ O(N \log N) $。

直觉上理解：$ N $ 个频率分量的计算之间不是独立的，它们共享大量相同的中间计算。FFT 把这些重复部分提取出来复用，避免了重复劳动。

### 1.3 基本流程

以基-2 时域抽选（Decimation-In-Time, DIT）FFT 为例：

1. **分组**：将长度为 $ N $ 的信号按下标的奇偶分成两组：
   - 偶数下标组：$ x_0, x_2, x_4, \dots$
   - 奇数下标组：$ x_1, x_3, x_5, \dots$
2. **递归**：对每组（长度 $ N/2 $ ）分别做 DFT，得到 $ E_k $ （偶数组的 DFT）和 $ O_k $ （奇数组的 DFT）。
3. **蝶形合并**：利用旋转因子 $ W_N^k $ 将两个 $ N/2 $ 点结果合并为 $ N $ 点 DFT：
   - $ X_k = E_k + W_N^k \cdot O_k$
   - $ X_{k+N/2} = E_k - W_N^k \cdot O_k$
4. **递归终止**：当子序列长度为 1 时，DFT 就是它本身，直接返回。

### 1.4 直观例子

贯穿全章使用一个 8 点信号：

$$ x = [1, 1, 1, 1, 0, 0, 0, 0]$$

这相当于一个矩形脉冲（前半段为 1，后半段为 0）。它的频谱会在低频处有较大幅值，在高频处振荡衰减——这是矩形信号的典型特征。

第一次分组后：
- 偶数下标：$ x_e = [x_0, x_2, x_4, x_6] = [1, 1, 0, 0] $ （4 点）
- 奇数下标：$ x_o = [x_1, x_3, x_5, x_7] = [1, 1, 0, 0] $ （4 点）

继续递归，4 点再分成 2 点，2 点再分成 1 点，最后逐层用蝶形运算合并回 8 点。

## 2. 公式讲解

### 2.1 核心公式

**DFT 定义：**

$$ X_k = \sum_{n=0}^{N-1} x_n \cdot W_N^{kn}, \quad k = 0, 1, \dots, N-1$$

**旋转因子：**

$$ W_N = e^{-j \frac{2\pi}{N}}$$

**奇偶分解（FFT 递推核心）：**

将 $ n $ 按奇偶分组，令 $ n = 2m $ （偶数）和 $ n = 2m+1 $ （奇数）：

$$ X_k = \sum_{m=0}^{N/2-1} x_{2m} \cdot W_N^{k \cdot 2m} + \sum_{m=0}^{N/2-1} x_{2m+1} \cdot W_N^{k(2m+1)}$$

利用 $ W_N^{2} = W_{N/2} $，化简为：

$$ X_k = \underbrace{\sum_{m=0}^{N/2-1} x_{2m} \cdot W_{N/2}^{km}}_{E_k} + W_N^k \cdot \underbrace{\sum_{m=0}^{N/2-1} x_{2m+1} \cdot W_{N/2}^{km}}_{O_k}$$

**蝶形运算（合并公式）：**

$$ X_k = E_k + W_N^k \cdot O_k, \quad k = 0, 1, \dots, N/2-1$$

$$ X_{k+N/2} = E_k - W_N^k \cdot O_k$$

**卷积定理：**

$$ x_n * h_n \longleftrightarrow X_k \cdot H_k$$

其中 $ * $ 表示循环卷积，$ X_k $ 和 $ H_k $ 分别是 $ x_n $ 和 $ h_n $ 的 DFT。

### 2.2 变量含义

| 符号 | 含义 |
|------|------|
| $ x_n $ | 输入信号在第 $ n $ 个采样点的值，$ n = 0, 1, \dots, N-1 $ |
| $ X_k $ | DFT 的第 $ k $ 个输出，表示频率分量 $ k $ 的复数幅度和相位 |
| $ N $ | 信号长度（FFT 中要求为 2 的整数次幂） |
| $ W_N $ | 旋转因子，$ W_N = e^{-j \cdot 2\pi/N} $，是一个单位圆上的复数 |
| $ W_N^k $ | 旋转因子的 $ k $ 次幂，表示频率 $ k $ 对应的复指数权重 |
| $ E_k $ | 偶数下标子序列的 $ N/2 $ 点 DFT 输出 |
| $ O_k $ | 奇数下标子序列的 $ N/2 $ 点 DFT 输出 |
| $ j $ | 虚数单位，$ j^2 = -1 $ |
| $ e^{j\theta} $ | 欧拉公式：$ e^{j\theta} = \cos\theta + j\sin\theta $ |
| $ * $ | 循环卷积运算符 |
| $ H_k $ | 滤波器 $ h_n $ 的 DFT |

### 2.3 公式怎么理解

**DFT 公式**：对每个频率 $ k $，把信号 $ x_n $ 与一个复指数 $ W_N^{kn} $ 逐点相乘再求和。复指数 $ W_N^{kn} = e^{-j \cdot 2\pi kn/N} $ 是一个旋转的单位向量，频率为 $ k/N $。这个求和本质上是在"检测"信号中包含多少频率为 $ k/N $ 的分量——如果信号恰好含有该频率，相乘求和后幅值会比较大；否则正负相消，结果趋近于零。

**旋转因子**：$ W_N = e^{-j \cdot 2\pi/N} $ 是单位圆上均匀分布的第一个点。$ W_N^k $ 就是单位圆上第 $ k $ 个等分点。它具有周期性 $ W_N^{k+N} = W_N^k $ 和对称性 $ W_N^{k+N/2} = -W_N^k $，这两个性质是 FFT 能减少计算量的关键。

**奇偶分解**：DFT 的求和中，偶数下标项和奇数下标项可以拆开。拆开后发现，偶数组的求和恰好是一个 $ N/2 $ 点 DFT（因为 $ W_N^2 = W_{N/2} $ ），奇数组也是。于是 $ N $ 点 DFT 变成了两个 $ N/2 $ 点 DFT 加一次合并。

**蝶形运算**：合并时，$ X_k $ 和 $ X_{k+N/2} $ 共享相同的 $ E_k $ 和 $ O_k $，只是旋转因子的符号相反。这是因为 $ W_N^{k+N/2} = -W_N^k $ （半圈旋转 = 取反）。所以一次蝶形运算同时算出两个输出，计算量减半。这个"蝴蝶"形状的运算流程图因此得名——两路输入、两路输出，交叉连接。

**卷积定理**：时域的卷积（逐点滑动加权求和）等于频域的逐点相乘。这意味着，如果要做 $ x $ 和 $ h $ 的卷积，可以先把两者分别做 FFT 转到频域，逐点相乘，再做一次逆 FFT 转回来。当序列较长时，FFT 卷积比直接卷积更快。

## 3. 代码示例

### 3.1 最小可运行代码

```python
import numpy as np

# --- 1. 手写 DFT（O(N^2)，用于验证正确性）---
def dft(x):
    N = len(x)
    n = np.arange(N).reshape(-1, 1)   # 列向量
    k = np.arange(N).reshape(1, -1)    # 行向量
    W = np.exp(-2j * np.pi * k * n / N)
    return W @ x                        # 矩阵乘法，得到 [X_0, ..., X_{N-1}]

# --- 2. 手写递归 FFT（O(N log N)）---
def fft_recursive(x):
    N = len(x)
    if N <= 1:
        return x
    # 奇偶分组
    even = fft_recursive(x[0::2])
    odd  = fft_recursive(x[1::2])
    # 蝶形运算
    factor = np.exp(-2j * np.pi * np.arange(N // 2) / N)
    first_half  = even + factor * odd
    second_half = even - factor * odd
    return np.concatenate([first_half, second_half])

# --- 3. 验证正确性 ---
x = np.array([1, 1, 1, 1, 0, 0, 0, 0], dtype=complex)

X_dft = dft(x)
X_fft = fft_recursive(x)
X_np  = np.fft.fft(x)

print("手写 DFT :", np.round(X_dft, 4))
print("手写 FFT :", np.round(X_fft, 4))
print("NumPy FFT:", np.round(X_np, 4))
print("DFT 与 FFT 一致:", np.allclose(X_dft, X_fft))
print("FFT 与 NumPy 一致:", np.allclose(X_fft, X_np))

# --- 4. FFT 加速卷积 ---
def fft_convolve(x, h):
    # 线性卷积需要零填充到 N1 + N2 - 1
    total_len = len(x) + len(h) - 1
    n_fft = 1
    while n_fft < total_len:
        n_fft *= 2                       # 补到 2 的幂
    x_padded = np.zeros(n_fft, dtype=complex)
    h_padded = np.zeros(n_fft, dtype=complex)
    x_padded[:len(x)] = x
    h_padded[:len(h)] = h
    # 频域相乘
    X_f = np.fft.fft(x_padded)
    H_f = np.fft.fft(h_padded)
    y = np.fft.ifft(X_f * H_f)
    return np.real(y[:total_len])

x = np.array([1, 1, 1, 1, 0, 0, 0, 0])
h = np.array([1, -1])                     # 简单差分滤波器

y_fft   = fft_convolve(x, h)
y_direct = np.convolve(x, h)              # 直接卷积（验证用）

print("\nFFT 卷积结果 :", np.round(y_fft, 4))
print("直接卷积结果 :", np.round(y_direct, 4))
print("两种卷积一致:", np.allclose(y_fft, y_direct))
```

### 3.2 输入输出说明

**输入**：
- 信号 `x = [1, 1, 1, 1, 0, 0, 0, 0]`，长度 8，类型为复数数组。
- 滤波器 `h = [1, -1]`，长度 2，一个简单的差分滤波器（对相邻点求差）。

**输出**：
- `X_dft` / `X_fft` / `X_np`：8 点 DFT 结果，都是长度 8 的复数数组。$ X_0 $ 是直流分量（信号均值 × N），其余是各频率分量的复数幅度和相位。对于 $ x = [1,1,1,1,0,0,0,0] $，$ X_0 = 4 $ （直流分量为 4），$ X_4 = 0 $，其余对称。
- `y_fft`：FFT 卷积结果，长度 $ 8 + 2 - 1 = 9 $。差分滤波器 $ [1, -1] $ 的作用是对相邻样本求差，因此输出在信号跳变处（ $ x_3 \to x_4 $ 从 1 跳到 0）会出现 $ -1 $，其余平坦处为 0。
- `y_direct`：NumPy 直接卷积结果，用于验证 FFT 卷积正确性。

### 3.3 关键代码解释

1. **`dft` 函数**：构造 $ N \times N $ 的 DFT 矩阵 $ W $，其中 $ W_{n,k} = e^{-j \cdot 2\pi kn/N} $，然后做矩阵乘法 $ W \cdot x $。这就是 DFT 定义的直接实现，复杂度 $ O(N^2) $。

2. **`fft_recursive` 函数**：递归实现基-2 FFT。
   - `x[0::2]` 取偶数下标元素，`x[1::2]` 取奇数下标元素——对应奇偶分解。
   - `factor = np.exp(-2j * np.pi * np.arange(N // 2) / N)` 生成本次蝶形运算需要的旋转因子 $ W_N^k $ （$ k = 0, \dots, N/2-1 $ ）。
   - `first_half = even + factor * odd` 和 `second_half = even - factor * odd` 就是蝶形运算公式 $ X_k = E_k + W_N^k O_k $ 和 $ X_{k+N/2} = E_k - W_N^k O_k $。

3. **`fft_convolve` 函数**：用 FFT 实现线性卷积。
   - 先将两个序列零填充到长度 $ \geq N_1 + N_2 - 1 $ 的 2 的幂（避免循环卷积的混叠）。
   - 对填充后的序列分别做 FFT，逐点相乘，再做 IFFT 转回时域。
   - 这就是卷积定理 $ x * h \leftrightarrow X \cdot H $ 的直接应用。

4. **验证**：代码分别用手写 DFT、手写 FFT、NumPy 内置 FFT 计算同一信号的频谱，并用 `np.allclose` 确认三者一致；同时对比 FFT 卷积和直接卷积的结果。
