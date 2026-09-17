import numpy as np

# --- 网络参数 ---
W1 = np.array([[2, -1, 1],
               [0,  2, 1]], dtype=float)
b1 = np.array([[0, 0, 0]], dtype=float)
W2 = np.array([[0.5],
               [-2],
               [0.5]], dtype=float)
b2 = np.array([[0.0]])

x = np.array([[1.0, 1.0]])
y = np.array([[0.0]])

# --- 激活函数 ---
def relu(z):
    return np.maximum(0, z)

def relu_grad(z):
    return (z > 0).astype(float)

def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-z))

def sigmoid_grad_from_output(a):
    return a * (1 - a)

# --- 前向传播 ---
def forward(x, W1, b1, W2, b2):
    z1 = x @ W1 + b1
    a1 = relu(z1)
    z2 = a1 @ W2 + b2
    a2 = sigmoid(z2)
    return z1, a1, z2, a2

# --- 反向传播 ---
def backward(x, y, z1, a1, z2, a2, W1, W2):
    delta2 = (a2 - y) * sigmoid_grad_from_output(a2)
    dW2 = a1.T @ delta2
    db2 = delta2.copy()
    delta1 = (delta2 @ W2.T) * relu_grad(z1)
    dW1 = x.T @ delta1
    db1 = delta1.copy()
    return dW1, db1, dW2, db2

def mse_loss(a2, y):
    return 0.5 * np.sum((a2 - y) ** 2)

# === 1. 验证前向传播 ===
z1, a1, z2, a2 = forward(x, W1, b1, W2, b2)
loss = mse_loss(a2, y)
print("=== 前向传播 ===")
print(f"z1 = {z1}")
print(f"a1 = {a1}")
print(f"z2 = {z2}")
print(f"a2 (y_hat) = {a2}")
print(f"loss = {loss}")

assert np.allclose(z1, [[2, 1, 2]]), f"z1 错误: {z1}"
assert np.allclose(a1, [[2, 1, 2]]), f"a1 错误: {a1}"
assert np.allclose(z2, [[0]]), f"z2 错误: {z2}"
assert np.allclose(a2, [[0.5]]), f"a2 错误: {a2}"
assert np.isclose(loss, 0.125), f"loss 错误: {loss}"
print("前向传播全部断言通过 ✓")

# === 2. 验证反向传播 ===
dW1, db1, dW2, db2 = backward(x, y, z1, a1, z2, a2, W1, W2)

# 手算验证
# delta2 = (0.5 - 0) * 0.5 * 0.5 = 0.125
expected_delta2 = 0.125
# dW2 = a1^T * delta2 = [[2],[1],[2]] * 0.125 = [[0.25],[0.125],[0.25]]
expected_dW2 = np.array([[0.25], [0.125], [0.25]])
# delta1 = (delta2 * W2^T) * relu_grad(z1) = 0.125 * [0.5, -2, 0.5] * [1,1,1] = [0.0625, -0.25, 0.0625]
expected_delta1 = np.array([[0.0625, -0.25, 0.0625]])
# dW1 = x^T * delta1 = [[1],[1]] * [0.0625, -0.25, 0.0625]
expected_dW1 = np.array([[0.0625, -0.25, 0.0625], [0.0625, -0.25, 0.0625]])

print(f"\n=== 反向传播 ===")
print(f"delta2 = {expected_delta2} (期望 0.125)")
print(f"dW2 = \n{dW2}")
print(f"期望 dW2 = \n{expected_dW2}")
print(f"dW1 = \n{dW1}")
print(f"期望 dW1 = \n{expected_dW1}")

assert np.allclose(dW2, expected_dW2), f"dW2 错误"
assert np.allclose(dW1, expected_dW1), f"dW1 错误"
print("反向传播手算验证通过 ✓")

# === 3. 数值梯度检查 ===
def numerical_grad(param, fn, eps=1e-7):
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

print(f"\n=== 梯度检查 ===")

def loss_fn_W1():
    _, _, _, a = forward(x, W1, b1, W2, b2)
    return mse_loss(a, y)

def loss_fn_W2():
    _, _, _, a = forward(x, W1, b1, W2, b2)
    return mse_loss(a, y)

num_dW1 = numerical_grad(W1, loss_fn_W1)
num_dW2 = numerical_grad(W2, loss_fn_W2)

print(f"dW1 (解析) = \n{dW1}")
print(f"dW1 (数值) = \n{num_dW1}")
print(f"dW1 一致: {np.allclose(dW1, num_dW1, atol=1e-6)}")
print(f"dW2 (解析) = \n{dW2}")
print(f"dW2 (数值) = \n{num_dW2}")
print(f"dW2 一致: {np.allclose(dW2, num_dW2, atol=1e-6)}")

assert np.allclose(dW1, num_dW1, atol=1e-6), "dW1 梯度检查失败"
assert np.allclose(dW2, num_dW2, atol=1e-6), "dW2 梯度检查失败"
print("梯度检查全部通过 ✓")

# === 4. 训练循环 ===
lr = 1.0
print(f"\n=== 训练（lr={lr}）===")
losses = []
for step in range(200):
    z1, a1, z2, a2 = forward(x, W1, b1, W2, b2)
    loss = mse_loss(a2, y)
    dW1, db1, dW2, db2 = backward(x, y, z1, a1, z2, a2, W1, W2)
    W1 -= lr * dW1
    b1 -= lr * db1
    W2 -= lr * dW2
    b2 -= lr * db2
    losses.append(loss)
    if step % 50 == 0 or step == 199:
        print(f"step {step:3d}: loss = {loss:.6f}, y_hat = {a2[0,0]:.6f}")

final_y_hat = forward(x, W1, b1, W2, b2)[3][0, 0]
print(f"\n最终预测值: {final_y_hat:.6f} (目标: {y[0,0]})")

assert losses[-1] < losses[0], "训练后损失应下降"
assert final_y_hat < 0.1, f"训练后预测值应接近0，实际 {final_y_hat}"
print("训练收敛验证通过 ✓")

# === 5. 线性可合并性验证（无激活函数时多层=单层）===
print(f"\n=== 线性可合并性验证 ===")
W1_lin = np.array([[1, 2], [3, 4]], dtype=float)
W2_lin = np.array([[5, 6], [7, 8]], dtype=float)
x_test = np.array([[1.0, 2.0]])

# 两层线性（无激活）
two_layer = (x_test @ W1_lin) @ W2_lin
# 合并为单层
merged = x_test @ (W1_lin @ W2_lin)
print(f"两层线性: {two_layer}")
print(f"合并单层: {merged}")
print(f"一致: {np.allclose(two_layer, merged)}")
assert np.allclose(two_layer, merged), "线性可合并性验证失败"
print("线性可合并性验证通过 ✓（无激活函数时多层确实等价于单层）")

print("\n========== 全部验证通过 ==========")
