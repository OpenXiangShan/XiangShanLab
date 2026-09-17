import numpy as np


def quantize_asymmetric(x, bits):
    q_min = -(2 ** (bits - 1))
    q_max = 2 ** (bits - 1) - 1
    x_min = min(float(x.min()), 0.0)
    x_max = max(float(x.max()), 0.0)
    if x_min == x_max:
        return np.zeros_like(x, dtype=np.int32), 1.0, 0

    scale = (x_max - x_min) / (q_max - q_min)
    zero_point = int(np.clip(
        np.rint(q_min - x_min / scale), q_min, q_max
    ))
    q = np.clip(
        np.rint(x / scale) + zero_point, q_min, q_max
    ).astype(np.int32)
    return q, scale, zero_point


def quantize_symmetric(x, bits):
    q_max = 2 ** (bits - 1) - 1
    q_min = -q_max
    abs_max = float(np.max(np.abs(x)))
    if abs_max == 0.0:
        return np.zeros_like(x, dtype=np.int32), 1.0, 0

    scale = abs_max / q_max
    q = np.clip(
        np.rint(x / scale), q_min, q_max
    ).astype(np.int32)
    return q, scale, 0


def dequantize(q, scale, zero_point):
    return scale * (q.astype(np.float64) - zero_point)


def print_result(name, x, q, x_hat, scale, zero_point):
    error = x_hat - x
    mae = np.mean(np.abs(error))
    mse = np.mean(error ** 2)
    print(f"{name}:")
    print("  scale:", round(scale, 6))
    print("  zero point:", zero_point)
    print("  quantized:", q)
    print("  dequantized:", np.round(x_hat, 6))
    print("  MAE:", round(mae, 6))
    print("  MSE:", round(mse, 8))
    return mae, mse


x = np.array([-1.0, -0.3, 0.0, 0.8, 1.2], dtype=np.float64)
q_asym8, s_asym8, z_asym8 = quantize_asymmetric(x, bits=8)
x_asym8 = dequantize(q_asym8, s_asym8, z_asym8)
q_sym8, s_sym8, z_sym8 = quantize_symmetric(x, bits=8)
x_sym8 = dequantize(q_sym8, s_sym8, z_sym8)
q_sym4, s_sym4, z_sym4 = quantize_symmetric(x, bits=4)
x_sym4 = dequantize(q_sym4, s_sym4, z_sym4)

print("original:", x)
mae_asym8, mse_asym8 = print_result(
    "asymmetric INT8", x, q_asym8, x_asym8, s_asym8, z_asym8
)
mae_sym8, mse_sym8 = print_result(
    "symmetric INT8", x, q_sym8, x_sym8, s_sym8, z_sym8
)
mae_sym4, mse_sym4 = print_result(
    "symmetric INT4", x, q_sym4, x_sym4, s_sym4, z_sym4
)

assert np.array_equal(q_asym8, [-128, -47, -12, 81, 127])
assert np.array_equal(q_sym8, [-106, -32, 0, 85, 127])
assert np.array_equal(q_sym4, [-6, -2, 0, 5, 7])
assert np.isclose(s_asym8, 2.2 / 255)
assert np.isclose(s_sym8, 1.2 / 127)
assert np.isclose(s_sym4, 1.2 / 7)
assert z_asym8 == -12
assert z_sym8 == 0 and z_sym4 == 0
assert mse_sym4 > mse_sym8
assert np.isfinite([mae_asym8, mse_asym8, mae_sym8, mse_sym8]).all()
print("Quantization verification passed.")
