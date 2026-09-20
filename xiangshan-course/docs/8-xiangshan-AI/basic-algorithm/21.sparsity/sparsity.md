# 稀疏

## 1. 文字讲解

### 1.1 这个算法解决什么问题

当矩阵或张量中大量元素等于零时，可以称它是稀疏的。稀疏性（Sparsity）描述的是非零元素只占全部元素一小部分的状态。

如果仍然按照稠密矩阵保存和计算，零元素会和非零元素一样占据位置并参与运算。稀疏表示只记录非零值及其位置，稀疏计算则尽量只处理这些非零项。

在神经网络中，稀疏性主要出现在两个位置：

- **稀疏权重**：模型参数中包含大量零值，可以由剪枝、稀疏正则或受约束训练产生。
- **稀疏激活**：模型对某个输入进行前向传播时，激活张量中出现大量零值。

稀疏性本身只说明零元素的比例和分布。稀疏表示还要保存索引，因此只有在非零元素足够少、计算方式能够利用其结构时，才有必要采用稀疏形式。

### 1.2 核心思想

考虑一个包含 $12$ 个元素、但只有 $4$ 个非零值的矩阵。稠密形式保存全部 $12$ 个值；稀疏形式可以只保存 $4$ 个非零值和它们的位置。

常见稀疏格式包括 COO、CSR、CSC 和块稀疏格式。本章使用 CSR（Compressed Sparse Row，压缩稀疏行）讲解基本思想。

CSR 使用三个数组表示二维矩阵：

- `values`：按行保存所有非零值。
- `column_indices`：保存每个非零值所在的列。
- `row_pointer`：指出每一行的非零数据在前两个数组中的起止位置。

稀疏计算的核心不是“与零相乘以后得到零”，而是根据索引直接跳过零元素，只计算非零值对应的项。

### 1.3 基本流程

1. 统计张量中的零元素和非零元素，计算稀疏率。
2. 判断稀疏对象是权重、激活还是其他中间结果。
3. 选择适合其形状和访问方式的稀疏格式。
4. 将非零值及其索引写入稀疏数据结构。
5. 计算时根据索引读取需要参与运算的元素。
6. 将稀疏计算结果与对应的稠密计算结果比较，验证数值一致性。

剪枝是产生稀疏权重的一种方法，但两者不是同一个概念：剪枝关注如何决定删除哪些参数，稀疏关注零元素形成后如何描述和计算。

### 1.4 直观例子

设稀疏权重矩阵为：

$$

W=
\begin{bmatrix}
0 & 2 & 0 & 0\\
-1 & 0 & 0 & 3\\
0 & 0 & 4 & 0
\end{bmatrix}.

$$

该矩阵共有 $12$ 个元素，其中 $4$ 个非零，因此稀疏率为：

$$

S=1-\frac{4}{12}=\frac{2}{3}.

$$

它的 CSR 表示为：

$$

\begin{aligned}
\text{values}&=[2,-1,3,4],\\
\text{column\_indices}&=[1,0,3,2],\\
\text{row\_pointer}&=[0,1,3,4].
\end{aligned}

$$

若输入向量为：

$$

\mathbf x=[1,2,-1,0.5]^{\mathsf T},

$$

则矩阵向量乘法结果为：

$$

W\mathbf x=[4,0.5,-4]^{\mathsf T}.

$$

CSR 计算只需要处理矩阵中的四个非零权重，零位置不会进入求和。

## 2. 公式讲解

### 2.1 核心公式

对于矩阵 $A\in\mathbb R^{m\times n}$，非零元素数量定义为：

$$

\operatorname{nnz}(A)
=\sum_{i=1}^{m}\sum_{j=1}^{n}
\mathbb I(A_{ij}\ne 0).

$$

矩阵的稀疏率为：

$$

S(A)
=1-\frac{\operatorname{nnz}(A)}{mn}.

$$

稀疏权重可以使用二值掩码表示：

$$

W_{\text{sparse}}=M\odot W,
\qquad M_{ij}\in\{0,1\}.

$$

ReLU 激活函数为：

$$

a_i=\operatorname{ReLU}(h_i)
=\max(0,h_i).

$$

当 $h_i\le 0$ 时，ReLU 输出 $a_i=0$，因此一次前向传播可能产生稀疏激活。

稠密矩阵向量乘法为：

$$

y_i=\sum_{j=0}^{n-1}A_{ij}x_j,
\qquad i=0,1,\ldots,m-1.

$$

在 CSR 表示中，设非零值数组为 $v$，列索引数组为 $c$，行指针数组为 $p$，则同一个结果可以写为：

$$

y_i=\sum_{k=p_i}^{p_{i+1}-1}v_kx_{c_k},
\qquad i=0,1,\ldots,m-1.

$$

### 2.2 变量含义

- $A$：包含 $m$ 行、 $n$ 列的矩阵。
- $\mathbb R^{m\times n}$：所有 $m$ 行 $n$ 列实数矩阵的集合。
- $A_{ij}$：矩阵 $A$ 第 $i$ 行、第 $j$ 列的元素。
- $\operatorname{nnz}(A)$：矩阵 $A$ 中非零元素的数量。
- $\mathbb I(\cdot)$：指示函数，条件成立时为 $1$，否则为 $0$。
- $S(A)$：矩阵 $A$ 的稀疏率。
- $W$：原始权重矩阵。
- $M$：二值稀疏掩码。
- $\odot$：逐元素乘法。
- $W_{\text{sparse}}$：应用掩码后的稀疏权重。
- $h_i$：激活函数之前的第 $i$ 个输入值。
- $a_i$：经过 ReLU 后的第 $i$ 个激活值。
- $x_j$：输入向量的第 $j$ 个元素。
- $y_i$：输出向量的第 $i$ 个元素。
- $v_k$：CSR 非零值数组的第 $k$ 个元素。
- $c_k$： $v_k$ 在原矩阵中的列索引。
- $p_i$ 、 $p_{i+1}$：CSR 中第 $i$ 行非零数据的起始位置和下一行起始位置。

### 2.3 公式怎么理解

$\operatorname{nnz}(A)$ 只统计不等于零的元素。稀疏率 $S(A)$ 越接近 $1$，零元素占比越高；当矩阵没有零元素时，稀疏率为 $0$。

稀疏权重是模型参数本身包含零值，通常在不同输入之间保持不变。稀疏激活由当前输入和激活函数共同决定，换一个输入后，零元素的位置可能改变。

ReLU 把所有非正输入变为零，因此可能自然产生稀疏激活。并不是所有激活函数都会产生精确零值，例如某些平滑激活函数的负半轴输出通常只是接近零。

稠密公式对每一列都执行乘加。CSR 公式通过 $p_i$ 找到第 $i$ 行对应的非零片段，再使用 $c_k$ 找到输入向量中的对应位置，因此求和项数量由 $\operatorname{nnz}(A)$ 决定。

稀疏格式除了保存 $v_k$，还要保存 $c_k$ 和 $p_i$。因此，稀疏率、非零元素分布、数据格式和具体实现都会影响稀疏表示是否合适，不能只根据零元素数量推断实际运行效果。

## 3. 代码示例

### 3.1 最小可运行代码

下面用 NumPy 手动构造 CSR 表示，并分别执行稠密和稀疏矩阵向量乘法。同时使用 ReLU 展示稀疏激活。

```python
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
assert np.allclose(sparse_output, dense_output)
assert np.isclose(sparsity(activation), 0.6)
```

### 3.2 输入输出说明

- `weights` 是形状为 `(3, 4)` 的稀疏权重矩阵。
- `vector` 是长度为 $4$ 的输入向量。
- `values` 保存四个非零权重，形状为 `(4,)`。
- `column_indices` 保存四个非零权重的列位置，形状为 `(4,)`。
- `row_pointer` 的长度为行数加一，因此形状为 `(4,)`。
- `dense_output` 和 `sparse_output` 都应等于 `[4.0, 0.5, -4.0]`。
- `activation` 是 ReLU 输出，其中三个元素为零，稀疏率为 $0.6$。

运行代码后，稠密计算与 CSR 稀疏计算得到完全相同的输出。权重稀疏率约为 $0.6667$，激活稀疏率为 $0.6$。

### 3.3 关键代码解释

`dense_to_csr` 逐行寻找非零列，将非零值写入 `values`，把列位置写入 `column_indices`，并在每一行结束时记录当前非零值总数。

`csr_matvec` 使用 `row_pointer[row]` 和 `row_pointer[row + 1]` 确定当前行的非零范围，只遍历该范围内的元素。

`np.maximum(pre_activation, 0.0)` 实现 ReLU。负数和零都得到零输出，正数保持不变，从而在这个示例中产生稀疏激活。
