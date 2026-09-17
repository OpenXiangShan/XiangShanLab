import numpy as np


def softmax(x):
    x = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(x)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def simplified_mla(x, w_down, w_up_k, w_up_v):
    latent_kv = x @ w_down
    key = latent_kv @ w_up_k
    value = latent_kv @ w_up_v
    query = x
    scores = query @ key.T / np.sqrt(query.shape[-1])
    weights = softmax(scores)
    output = weights @ value
    return output, weights, latent_kv, key, value


x = np.array([
    [1.0, 0.0, 1.0, 0.0],
    [0.0, 1.0, 0.0, 1.0],
    [1.0, 1.0, 0.0, 0.0],
])

w_down = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
    [1.0, 1.0],
    [1.0, -1.0],
])
w_up_k = np.array([
    [1.0, 0.0, 1.0, 0.0],
    [0.0, 1.0, 0.0, 1.0],
])
w_up_v = np.array([
    [1.0, 1.0, 0.0, 0.0],
    [0.0, 0.0, 1.0, 1.0],
])

output, weights, latent_kv, key, value = simplified_mla(
    x, w_down, w_up_k, w_up_v
)
standard_kv_elements = key.size + value.size
compressed_elements = latent_kv.size

np.set_printoptions(precision=4, suppress=True)
print("latent KV:\n", latent_kv)
print("attention weights:\n", weights)
print("output:\n", output)
print("standard KV elements:", standard_kv_elements)
print("compressed latent elements:", compressed_elements)

assert latent_kv.shape == (3, 2)
assert key.shape == value.shape == (3, 4)
assert np.allclose(weights.sum(axis=-1), 1.0)
assert compressed_elements == 6
assert standard_kv_elements == 24
print("DeepSeek MLA verification passed.")
