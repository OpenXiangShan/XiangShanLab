import numpy as np


def relu(x):
    return np.maximum(x, 0.0)


def separate_affine_relu(x, weight, bias):
    matmul_output = x @ weight
    bias_output = matmul_output + bias
    return relu(bias_output)


def fused_affine_relu(x, weight, bias):
    return np.maximum(x @ weight + bias, 0.0)


def batch_norm_inference(z, mean, variance, gamma, beta, eps):
    return gamma * (z - mean) / np.sqrt(variance + eps) + beta


def fold_linear_batch_norm(
    weight, bias, mean, variance, gamma, beta, eps
):
    scale = gamma / np.sqrt(variance + eps)
    folded_weight = weight * scale
    folded_bias = scale * (bias - mean) + beta
    return folded_weight, folded_bias


x = np.array([
    [1.0, 2.0, -1.0],
    [-0.5, 1.0, 2.0],
])
weight = np.array([
    [1.0, -2.0],
    [0.5, 1.0],
    [-1.0, 0.25],
])
bias = np.array([0.1, -0.2])

separate_output = separate_affine_relu(x, weight, bias)
fused_output = fused_affine_relu(x, weight, bias)

mean = np.array([0.3, -0.5])
variance = np.array([1.44, 0.25])
gamma = np.array([1.2, 0.5])
beta = np.array([-0.1, 0.2])
eps = 1e-5

linear_output = x @ weight + bias
linear_bn_output = batch_norm_inference(
    linear_output, mean, variance, gamma, beta, eps
)
folded_weight, folded_bias = fold_linear_batch_norm(
    weight, bias, mean, variance, gamma, beta, eps
)
folded_output = x @ folded_weight + folded_bias

assert np.allclose(
    separate_output,
    [[3.1, 0.0], [0.0, 2.3]],
)
assert np.allclose(fused_output, separate_output)
assert np.allclose(folded_output, linear_bn_output)
print("Operator fusion verification passed.")
