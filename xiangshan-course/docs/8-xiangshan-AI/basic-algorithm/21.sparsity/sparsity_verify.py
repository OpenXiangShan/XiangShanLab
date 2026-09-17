import numpy as np


def sparsity(array):
    return 1.0 - np.count_nonzero(array) / array.size


def dense_to_csr(matrix):
    values = []
    column_indices = []
    row_pointer = [0]
    for row in matrix:
        nonzero_columns = np.flatnonzero(row)
        values.extend(row[nonzero_columns])
        column_indices.extend(nonzero_columns)
        row_pointer.append(len(values))
    return (
        np.asarray(values, dtype=np.float64),
        np.asarray(column_indices, dtype=np.int64),
        np.asarray(row_pointer, dtype=np.int64),
    )


def csr_matvec(values, column_indices, row_pointer, vector):
    row_count = len(row_pointer) - 1
    output = np.zeros(row_count, dtype=np.float64)
    for row in range(row_count):
        start = row_pointer[row]
        end = row_pointer[row + 1]
        for index in range(start, end):
            column = column_indices[index]
            output[row] += values[index] * vector[column]
    return output


weights = np.array([
    [0.0, 2.0, 0.0, 0.0],
    [-1.0, 0.0, 0.0, 3.0],
    [0.0, 0.0, 4.0, 0.0],
])
vector = np.array([1.0, 2.0, -1.0, 0.5])

values, column_indices, row_pointer = dense_to_csr(weights)
dense_output = weights @ vector
sparse_output = csr_matvec(
    values, column_indices, row_pointer, vector
)

pre_activation = np.array([-2.0, 0.5, 0.0, 3.0, -0.1])
activation = np.maximum(pre_activation, 0.0)

print("weight sparsity:", round(sparsity(weights), 4))
print("CSR values:", values)
print("CSR column indices:", column_indices)
print("CSR row pointer:", row_pointer)
print("dense output:", dense_output)
print("sparse output:", sparse_output)
print("ReLU activation:", activation)
print("activation sparsity:", round(sparsity(activation), 4))

assert np.isclose(sparsity(weights), 2.0 / 3.0)
assert np.array_equal(values, [2.0, -1.0, 3.0, 4.0])
assert np.array_equal(column_indices, [1, 0, 3, 2])
assert np.array_equal(row_pointer, [0, 1, 3, 4])
assert np.allclose(dense_output, [4.0, 0.5, -4.0])
assert np.allclose(sparse_output, dense_output)
assert np.array_equal(activation, [0.0, 0.5, 0.0, 3.0, 0.0])
assert np.isclose(sparsity(activation), 0.6)
print("Sparsity verification passed.")
