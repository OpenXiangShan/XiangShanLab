import numpy as np

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

np.random.seed(42)
K_big = np.random.randn(7, 7)
F_A = conv2d(X, K_big, stride=1, padding=3)
F_A = relu(F_A)
params_A = K_big.size
print(f"=== 方案 A: 一个 7x7 核 === 输出尺寸: {F_A.shape}, 参数量: {params_A}")

K1 = np.random.randn(3, 3)
K2 = np.random.randn(3, 3)
K3 = np.random.randn(3, 3)
F_B = conv2d(X, K1, stride=1, padding=1)
F_B = relu(F_B)
F_B = conv2d(F_B, K2, stride=1, padding=1)
F_B = relu(F_B)
F_B = conv2d(F_B, K3, stride=1, padding=1)
F_B = relu(F_B)
params_B = K1.size + K2.size + K3.size
print(f"=== 方案 B: 三个 3x3 核 === 输出尺寸: {F_B.shape}, 参数量: {params_B}")

# === 1. 感受野验证 ===
def receptive_field(num_layers, k=3):
    r = 1
    for _ in range(num_layers):
        r = r + (k - 1)
    return r

print(f"\n=== 感受野验证 ===")
for n in range(1, 5):
    r = receptive_field(n)
    equiv = 2 * n + 1
    print(f"  {n} 层 3x3 -> 感受野 {r}x{r}（等效单个 {equiv}x{equiv} 核）")
assert receptive_field(3) == 7, "三层 3x3 感受野应为 7"
assert receptive_field(2) == 5, "两层 3x3 感受野应为 5"
print("感受野验证通过 ✓")

# === 2. 参数量对比 ===
print(f"\n=== 参数量对比 ===")
print(f"方案 A (7x7):  {params_A} 个权重, 1 个 ReLU")
print(f"方案 B (3x3x3): {params_B} 个权重, 3 个 ReLU")
print(f"参数节省: {(1 - params_B/params_A)*100:.1f}%")
assert params_B == 27 and params_A == 49
print("参数量验证通过 ✓")

# === 3. 多通道参数量对比 ===
C = 256
big_params = C * C * 49 + C
stack_params = 3 * (C * C * 9 + C)
print(f"\n=== 多通道参数量对比 (C_in=256, C_out=256) ===")
print(f"一个 7x7 层:  {big_params:,} 参数")
print(f"三个 3x3 层:  {stack_params:,} 参数")
print(f"节省: {(1 - stack_params/big_params)*100:.1f}%")
assert big_params == 3211520
assert stack_params == 1770240
print("多通道参数量验证通过 ✓")

# === 4. VGG Block 模拟 ===
print(f"\n=== VGG Block 1 模拟 (2 层 3x3 conv + 2x2 pool) ===")
block_input = X
conv1 = relu(conv2d(block_input, K1, stride=1, padding=1))
conv2 = relu(conv2d(conv1, K2, stride=1, padding=1))
pooled = max_pool2d(conv2, pool_size=2, stride=2)
print(f"输入: {block_input.shape} -> conv1: {conv1.shape} -> conv2: {conv2.shape} -> pool: {pooled.shape}")
assert pooled.shape == (4, 4), "池化后应为 4x4"
print("VGG Block 验证通过 ✓")

print("\n========== VGG 全部验证通过 ==========")
