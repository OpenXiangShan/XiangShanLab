import numpy as np

W_xh = 1.0; W_hh = 0.5; W_hy = 1.0; b_h = 0.0; b_y = 0.0
x_seq = np.array([1.0, 1.0, 1.0])
y_target = np.array([0.5, 0.8, 0.9])
T = len(x_seq)

def tanh(z): return np.tanh(z)
def tanh_grad_from_h(h): return 1 - h**2

def rnn_forward(x_seq, W_xh, W_hh, W_hy, b_h, b_y):
    h = 0.0; h_list, z_list, y_list = [], [], []
    for t in range(len(x_seq)):
        z = W_xh * x_seq[t] + W_hh * h + b_h
        h = tanh(z); y = W_hy * h + b_y
        z_list.append(z); h_list.append(h); y_list.append(y)
    return h_list, z_list, y_list

def rnn_bptt(x_seq, y_target, h_list, z_list, y_list, W_xh, W_hh, W_hy, b_h, b_y):
    T = len(x_seq)
    dW_xh = dW_hh = dW_hy = db_h = db_y = 0.0
    dh_next = 0.0; dh_list = [0.0] * T
    for t in reversed(range(T)):
        dy = y_list[t] - y_target[t]
        dW_hy += dy * h_list[t]; db_y += dy
        dh = W_hy * dy + dh_next
        dh_list[t] = dh
        dz = dh * tanh_grad_from_h(h_list[t])
        dW_xh += dz * x_seq[t]
        dW_hh += dz * (h_list[t-1] if t > 0 else 0.0)
        db_h += dz
        dh_next = W_hh * dz
    return dW_xh, dW_hh, dW_hy, db_h, db_y, dh_list

# === 1. 前向传播 ===
h_list, z_list, y_list = rnn_forward(x_seq, W_xh, W_hh, W_hy, b_h, b_y)
print("=== 前向传播 ===")
for t in range(T):
    print(f"  t={t+1}: z={z_list[t]:.4f}, h={h_list[t]:.4f}, y={y_list[t]:.4f}")
assert np.isclose(h_list[0], np.tanh(1.0), atol=1e-4)
assert np.isclose(h_list[1], np.tanh(1.0 + 0.5*np.tanh(1.0)), atol=1e-4)
print("前向传播验证通过 ✓")

# === 2. 梯度检查 ===
dW_xh, dW_hh, dW_hy, db_h, db_y, dh_list = rnn_bptt(
    x_seq, y_target, h_list, z_list, y_list, W_xh, W_hh, W_hy, b_h, b_y)

def numerical_grad_rnn(param_name, eps=1e-7):
    params = {'W_xh': W_xh, 'W_hh': W_hh, 'W_hy': W_hy, 'b_h': b_h, 'b_y': b_y}
    orig = params[param_name]; params[param_name] = orig + eps
    _, _, y_p = rnn_forward(x_seq, params['W_xh'], params['W_hh'], params['W_hy'], params['b_h'], params['b_y'])
    loss_p = 0.5 * np.sum((np.array(y_p) - y_target)**2)
    params[param_name] = orig - eps
    _, _, y_m = rnn_forward(x_seq, params['W_xh'], params['W_hh'], params['W_hy'], params['b_h'], params['b_y'])
    loss_m = 0.5 * np.sum((np.array(y_m) - y_target)**2)
    params[param_name] = orig
    return (loss_p - loss_m) / (2 * eps)

print(f"\n=== 梯度检查 ===")
grads = {'W_xh': dW_xh, 'W_hh': dW_hh, 'W_hy': dW_hy, 'b_h': db_h, 'b_y': db_y}
for name, grad in grads.items():
    num = numerical_grad_rnn(name)
    ok = np.isclose(grad, num, atol=1e-5)
    print(f"  {name}: 解析={grad:.6f}, 数值={num:.6f}, 一致={ok}")
    assert ok, f"{name} 梯度检查失败"
print("梯度检查通过 ✓")

# === 3. 梯度消失演示 ===
print(f"\n=== 梯度消失演示（10 步序列）===")
x_long = np.ones(10); y_long = np.array([0.5]*10)
h_long, z_long, y_long_out = rnn_forward(x_long, W_xh, W_hh, W_hy, b_h, b_y)
_, _, _, _, _, dh_long = rnn_bptt(x_long, y_long, h_long, z_long, y_long_out, W_xh, W_hh, W_hy, b_h, b_y)

print(f"{'步':>4} | {'h_t':>10} | {'|dh_t|':>10} | {'衰减因子':>10}")
print("-" * 45)
for t in range(10):
    factor = W_hh * tanh_grad_from_h(h_long[t])
    print(f"{t+1:>4} | {h_long[t]:>10.4f} | {abs(dh_long[t]):>10.6f} | {factor:>10.4f}")
ratio = abs(dh_long[0]) / abs(dh_long[9]) if abs(dh_long[9]) > 0 else 0
print(f"\n梯度衰减比 (步1/步10): {ratio:.6f}")

# 衰减因子累计乘积才是梯度消失的真正度量
cumulative_factor = 1.0
for t in range(10):
    cumulative_factor *= W_hh * tanh_grad_from_h(h_long[t])
print(f"衰减因子累计乘积 (10步连乘): {cumulative_factor:.10f}")
assert abs(cumulative_factor) < 0.01, "10 步衰减因子连乘应 < 0.01"
print("梯度消失验证通过 ✓")

# === 4. 训练循环 ===
print(f"\n=== 训练（lr=0.1, 500 步）===")
lr = 0.1; W_xh_t, W_hh_t, W_hy_t = 0.8, 0.3, 0.8; b_h_t, b_y_t = 0.0, 0.0
for step in range(500):
    h_l, z_l, y_l = rnn_forward(x_seq, W_xh_t, W_hh_t, W_hy_t, b_h_t, b_y_t)
    loss = 0.5 * np.sum((np.array(y_l) - y_target)**2)
    dW_xh, dW_hh, dW_hy, db_h, db_y, _ = rnn_bptt(
        x_seq, y_target, h_l, z_l, y_l, W_xh_t, W_hh_t, W_hy_t, b_h_t, b_y_t)
    W_xh_t -= lr * dW_xh; W_hh_t -= lr * dW_hh; W_hy_t -= lr * dW_hy
    b_h_t -= lr * db_h; b_y_t -= lr * db_y
    if step % 100 == 0 or step == 499:
        print(f"  step {step:3d}: loss={loss:.6f}")
print(f"\n最终输出: {[f'{v:.4f}' for v in y_l]}, 目标: {y_target}")
print("\n========== RNN 全部验证通过 ==========")
