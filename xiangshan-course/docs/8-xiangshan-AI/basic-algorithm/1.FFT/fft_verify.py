import numpy as np

# --- 1. 手写 DFT（O(N^2)，用于验证正确性）---
def dft(x):
    N = len(x)
    n = np.arange(N).reshape(-1, 1)   # 列向量
    k = np.arange(N).reshape(1, -1)    # 行向量
    W = np.exp(-2j * np.pi * k * n / N)
    return W @ x                        # 矩阵乘法，得到 [X[0], ..., X[N-1]]

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
