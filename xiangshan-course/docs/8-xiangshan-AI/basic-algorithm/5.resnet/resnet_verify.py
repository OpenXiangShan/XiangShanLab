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
    out = relu(Fx + x)
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
    delta_out = out - y_target
    delta_z2 = delta_out * relu_grad(z2)
    delta_a1 = W2.T @ delta_z2
    delta_z1 = delta_a1 * relu_grad(z1)
    grad_x = W1.T @ delta_z1
    return grad_x

def residual_block_backward(x, y_target, W1, W2, b1, b2):
    z1 = W1 @ x + b1
    a1 = relu(z1)
    Fx = W2 @ a1 + b2
    out = relu(Fx + x)
    delta_out = out - y_target
    delta_sum = delta_out * relu_grad(Fx + x)
    delta_Fx = delta_sum
    delta_a1 = W2.T @ delta_Fx
    delta_z1 = delta_a1 * relu_grad(z1)
    grad_x_main = W1.T @ delta_z1
    grad_x_skip = delta_sum * 1.0
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

# === 3. 深层网络梯度衰减对比 ===
print(f"\n=== 深层网络梯度衰减对比 (5 blocks) ===")

def deep_plain_forward_backward(x, y_target, W1, W2, n_blocks):
    z1s, a1s, z2s, outs = [], [], [], []
    cur = x.copy()
    for _ in range(n_blocks):
        z1 = W1 @ cur + b1
        a1 = relu(z1)
        z2 = W2 @ a1 + b2
        out = relu(z2)
        z1s.append(z1); a1s.append(a1); z2s.append(z2); outs.append(out)
        cur = out
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
    grad = outs[-1] - y_target
    grad_norms = [np.linalg.norm(grad)]
    for i in reversed(range(n_blocks)):
        grad = grad * relu_grad(sums[i])
        grad_main = W2.T @ grad
        grad_main = grad_main * relu_grad(z1s[i])
        grad_main = W1.T @ grad_main
        grad_skip = grad * 1.0
        grad = grad_main + grad_skip
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

# 关键断言：普通梯度应显著衰减，残差梯度不应衰减
assert norms_p[-1] < norms_p[0] * 0.01, "普通网络梯度应显著衰减"
assert norms_r[-1] > norms_r[0] * 0.1, "残差网络梯度不应显著衰减"
print("梯度衰减验证通过 ✓")

# === 4. 训练对比（简化版，用解析梯度直接优化 W1）===
print(f"\n=== 训练对比（5 blocks, lr=0.5, 100 步）===")
lr = 0.5
for name, fwd_bwd in [("普通", deep_plain_forward_backward), ("残差", deep_residual_forward_backward)]:
    np.random.seed(42)
    W1_t = np.random.randn(2, 2) * 0.2
    W2_t = np.random.randn(2, 2) * 0.2
    losses = []
    for step in range(100):
        out, _ = fwd_bwd(x, y_target, W1_t, W2_t, 5)
        loss = 0.5 * np.sum((out - y_target) ** 2)
        losses.append(loss)
        # 数值梯度（仅对 W1）
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
    print(f"{name}: 初始 loss={losses[0]:.4f} -> 最终 loss={losses[-1]:.4f}")

print("\n========== ResNet 全部验证通过 ==========")
