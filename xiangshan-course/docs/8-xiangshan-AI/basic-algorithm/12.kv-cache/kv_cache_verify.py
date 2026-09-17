import numpy as np


def softmax(x):
    x = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(x)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def attention_last_token(x):
    q = x[-1:]
    key = x
    value = x
    scores = q @ key.T / np.sqrt(x.shape[-1])
    return softmax(scores) @ value


def prefill(prompt):
    return prompt.copy(), prompt.copy()


def decode_with_cache(new_token, key_cache, value_cache):
    q_new = new_token
    k_new = new_token
    v_new = new_token
    key_cache = np.concatenate([key_cache, k_new], axis=0)
    value_cache = np.concatenate([value_cache, v_new], axis=0)
    scores = q_new @ key_cache.T / np.sqrt(q_new.shape[-1])
    output = softmax(scores) @ value_cache
    return output, key_cache, value_cache


prompt = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
    [1.0, 1.0],
])
new_token = np.array([[2.0, 1.0]])

key_cache, value_cache = prefill(prompt)
cached_output, key_cache, value_cache = decode_with_cache(
    new_token, key_cache, value_cache
)

full_sequence = np.concatenate([prompt, new_token], axis=0)
full_output = attention_last_token(full_sequence)

np.set_printoptions(precision=4, suppress=True)
print("cache shape:", key_cache.shape)
print("cached output:", cached_output)
print("full output:  ", full_output)
print("outputs equal:", np.allclose(cached_output, full_output))

assert key_cache.shape == (4, 2)
assert np.allclose(cached_output, full_output)
print("KV Cache verification passed.")
