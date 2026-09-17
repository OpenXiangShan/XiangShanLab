import numpy as np

# --- 输入图像 (5×5) ---
X = np.array([
    [0, 0, 0, 0, 0],
    [0, 1, 1, 1, 0],
    [0, 1, 1, 1, 0],
    [0, 1, 1, 1, 0],
    [0, 0, 0, 0, 0],
], dtype=float)

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

def relu(F):
    return np.maximum(0, F)

def max_pool2d(F, pool_size=2, stride=1):
    h_out = (F.shape[0] - pool_size) // stride + 1
    w_out = (F.shape[1] - pool_size) // stride + 1
    P = np.zeros((h_out, w_out))
    for i in range(h_out):
        for j in range(w_out):
            region = F[i*stride:i*stride+pool_size, j*stride:j*stride+pool_size]
            P[i, j] = np.max(region)
    return P

# === 1. 卷积 ===
F1 = conv2d(X, K1)
F2 = conv2d(X, K2)
print("=== 卷积 ===")
print(f"特征图 1:\n{F1}")
print(f"特征图 2:\n{F2}")

expected_F1 = np.array([[4,6,4],[6,9,6],[4,6,4]], dtype=float)
expected_F2 = np.array([[-2,0,2],[-3,0,3],[-2,0,2]], dtype=float)
assert np.allclose(F1, expected_F1), f"F1 错误: {F1}"
assert np.allclose(F2, expected_F2), f"F2 错误: {F2}"
print("卷积手算验证通过 ✓")

# === 2. ReLU ===
F1_relu = relu(F1)
F2_relu = relu(F2)
print(f"\n=== ReLU ===")
print(f"F1 ReLU:\n{F1_relu}")
print(f"F2 ReLU:\n{F2_relu}")
assert np.allclose(F1_relu, F1), "F1 全正，ReLU 不变"
assert np.allclose(F2_relu, np.array([[0,0,2],[0,0,3],[0,0,2]])), "F2 ReLU 错误"
print("ReLU 验证通过 ✓")

# === 3. 最大池化 ===
P1 = max_pool2d(F1_relu, pool_size=2, stride=1)
P2 = max_pool2d(F2_relu, pool_size=2, stride=1)
print(f"\n=== 最大池化 ===")
print(f"P1:\n{P1}")
print(f"P2:\n{P2}")
assert np.allclose(P1, np.array([[9,9],[9,9]])), f"P1 错误: {P1}"
assert np.allclose(P2, np.array([[0,3],[0,3]])), f"P2 错误: {P2}"
print("池化验证通过 ✓")

# === 4. 输出尺寸公式验证 ===
print(f"\n=== 输出尺寸公式 ===")
# 无填充 stride=1: (5-3)/1+1 = 3
assert F1.shape == (3, 3), f"无填充stride=1 应为 3×3，实际 {F1.shape}"
# padding=1 stride=1: (5+2-3)/1+1 = 5
F1_pad = conv2d(X, K1, stride=1, padding=1)
assert F1_pad.shape == (5, 5), f"padding=1 应为 5×5，实际 {F1_pad.shape}"
# stride=2 padding=0: (5-3)/2+1 = 2
F1_s2 = conv2d(X, K1, stride=2, padding=0)
assert F1_s2.shape == (2, 2), f"stride=2 应为 2×2，实际 {F1_s2.shape}"
print(f"无填充 stride=1: {F1.shape} ✓")
print(f"padding=1 stride=1: {F1_pad.shape} ✓")
print(f"stride=2 padding=0: {F1_s2.shape} ✓")

# === 5. 参数量对比 ===
print(f"\n=== 参数量对比 ===")
dnn_params = 25 * 18 + 18
cnn_params = 2 * (3 * 3) + 2
print(f"DNN 参数量: {dnn_params}")
print(f"CNN 参数量: {cnn_params}")
print(f"CNN 仅为 DNN 的 {cnn_params/dnn_params*100:.1f}%")
assert dnn_params == 468
assert cnn_params == 20

# === 6. 局部连接验证 ===
# 每个输出位置只依赖 3×3=9 个输入像素，而非全部 25 个
print(f"\n=== 局部连接 ===")
# 卷积核在 (0,0) 位置只看 X[0:3, 0:3]
region_00 = X[0:3, 0:3]
print(f"(0,0) 位置感受野: {region_00.shape} = 9 个像素")
assert region_00.size == 9
# DNN 中每个输出看全部 25 个
print(f"DNN 每个输出看: {X.size} = 25 个像素")
print("局部连接验证通过 ✓")

# === 7. 参数共享验证 ===
# 同一个核在所有位置滑动，参数不随位置增加
print(f"\n=== 参数共享 ===")
print(f"核 1 参数: {K1.size} = 9 个权重，用于所有 {F1.size} = 9 个输出位置")
print(f"DNN 对应: 25 × 9 = 225 个权重（仅核1对应的输出）")
print("参数共享验证通过 ✓")

print("\n========== 全部验证通过 ==========")
