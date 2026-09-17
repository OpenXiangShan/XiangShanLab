import numpy as np


def softmax(x):
    x = x - np.max(x, axis=-1, keepdims=True)
    exp_x = np.exp(x)
    return exp_x / np.sum(exp_x, axis=-1, keepdims=True)


def sparse_moe(x, router_weight, expert_weights, top_k=2):
    router_probs = softmax(x @ router_weight)
    top_indices = np.argsort(
        -router_probs, axis=-1, kind="stable"
    )[:, :top_k]

    outputs = []
    sparse_gates = np.zeros_like(router_probs)

    for token_id, token in enumerate(x):
        selected = top_indices[token_id]
        gates = router_probs[token_id, selected]
        gates = gates / gates.sum()
        sparse_gates[token_id, selected] = gates

        token_output = np.zeros(expert_weights.shape[-1])
        for gate, expert_id in zip(gates, selected):
            token_output += gate * (token @ expert_weights[expert_id])
        outputs.append(token_output)

    return np.array(outputs), router_probs, sparse_gates, top_indices


tokens = np.array([
    [1.0, 0.0],
    [0.0, 1.0],
])

router_weight = np.array([
    [2.0, 0.0, 1.0],
    [0.0, 2.0, 1.0],
])

expert_weights = np.array([
    [[1.0, 0.0], [0.0, 1.0]],
    [[2.0, 0.0], [0.0, 0.5]],
    [[0.5, 1.0], [1.0, 0.5]],
])

outputs, probs, gates, selected = sparse_moe(
    tokens, router_weight, expert_weights, top_k=2
)
expert_load = np.bincount(selected.ravel(), minlength=3)

np.set_printoptions(precision=4, suppress=True)
print("router probabilities:\n", probs)
print("selected experts:\n", selected + 1)
print("sparse gates:\n", gates)
print("MoE outputs:\n", outputs)
print("expert load:", expert_load)

assert np.allclose(gates.sum(axis=-1), 1.0)
assert np.count_nonzero(gates, axis=-1).tolist() == [2, 2]
assert selected.tolist() == [[0, 2], [1, 2]]
assert np.allclose(outputs, [[0.8655, 0.2689], [0.2689, 0.5]], atol=1e-4)
print("MoE verification passed.")
