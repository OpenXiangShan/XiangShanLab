import numpy as np


def softmax(x):
    shifted = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(shifted)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def scaled_dot_product_attention(query, key, value):
    d_k = query.shape[-1]
    scores = query @ key.T / np.sqrt(d_k)
    weights = softmax(scores)
    output = weights @ value
    return output, weights, scores


Q = np.array([[1.0, 0.0]])
K = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
    [1.0, 1.0],
])
V = np.array([
    [1.0, 0.0],
    [0.0, 2.0],
    [3.0, 1.0],
])

output, weights, scores = scaled_dot_product_attention(Q, K, V)

np.set_printoptions(precision=4, suppress=True)
print("scores:", scores)
print("weights:", weights)
print("output:", output)

assert np.allclose(weights.sum(axis=-1), 1.0)
assert np.allclose(output, [[1.6044, 0.7967]], atol=1e-4)
print("Attention verification passed.")
