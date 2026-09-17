import numpy as np


def magnitude_unstructured_mask(weights, sparsity):
    flat_abs = np.abs(weights).ravel()
    prune_count = int(flat_abs.size * sparsity)
    order = np.argsort(flat_abs, kind="stable")
    mask_flat = np.ones(flat_abs.size, dtype=np.float64)
    mask_flat[order[:prune_count]] = 0.0
    return mask_flat.reshape(weights.shape)


def structured_row_mask(weights, prune_rows):
    row_scores = np.linalg.norm(weights, ord=2, axis=1)
    rows_to_prune = np.argsort(row_scores)[:prune_rows]
    mask = np.ones_like(weights, dtype=np.float64)
    mask[rows_to_prune, :] = 0.0
    return mask, row_scores


def mse(x, y, weights):
    error = x @ weights - y
    return np.mean(error ** 2)


def finetune_with_mask(x, y, weights, mask, steps=500, lr=0.1):
    weights = weights.copy()
    for _ in range(steps):
        error = x @ weights - y
        gradient = (2.0 / len(x)) * (x.T @ error)
        weights = mask * (weights - lr * gradient)
    return weights


weights = np.array([
    [0.90, 0.02, -0.70, 0.10],
    [0.08, -0.60, 0.03, 0.50],
    [0.40, -0.05, 0.30, -0.01],
])
expected_unstructured = np.array([
    [0.90, 0.0, -0.70, 0.0],
    [0.0, -0.60, 0.0, 0.50],
    [0.40, 0.0, 0.30, 0.0],
])

unstructured_mask = magnitude_unstructured_mask(weights, 0.5)
unstructured_weights = weights * unstructured_mask
row_mask, row_scores = structured_row_mask(weights, prune_rows=1)
structured_weights = weights * row_mask

print("original weights:\n", weights)
print("unstructured mask:\n", unstructured_mask)
print("unstructured result:\n", unstructured_weights)
print("row L2 scores:", np.round(row_scores, 4))
print("structured row mask:\n", row_mask)
print("structured result:\n", structured_weights)

rng = np.random.default_rng(7)
x = rng.normal(size=(200, 4))
target_weights = np.array([2.0, -1.0, 0.15, 0.05])
y = x @ target_weights
recovery_mask = magnitude_unstructured_mask(target_weights, 0.5)
pruned_weights = target_weights * recovery_mask
loss_before_pruning = mse(x, y, target_weights)
loss_after_pruning = mse(x, y, pruned_weights)
recovered_weights = finetune_with_mask(
    x, y, pruned_weights, recovery_mask
)
loss_after_recovery = mse(x, y, recovered_weights)

print("recovery mask:", recovery_mask)
print("pruned regression weights:", pruned_weights)
print("recovered regression weights:", np.round(recovered_weights, 6))
print("loss before pruning:", round(loss_before_pruning, 8))
print("loss after pruning:", round(loss_after_pruning, 8))
print("loss after recovery:", round(loss_after_recovery, 8))

assert np.array_equal(unstructured_weights, expected_unstructured)
assert np.count_nonzero(unstructured_mask == 0) == 6
assert np.argmin(row_scores) == 2
assert np.all(row_mask[2] == 0)
assert np.all(structured_weights[2] == 0)
assert np.array_equal(recovery_mask, [1.0, 1.0, 0.0, 0.0])
assert np.all(recovered_weights[recovery_mask == 0] == 0)
assert np.isclose(loss_before_pruning, 0.0)
assert loss_after_recovery <= loss_after_pruning
print("Pruning verification passed.")
