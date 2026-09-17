from dataclasses import dataclass

import numpy as np


def round_shift(values, shift):
    values = np.asarray(values, dtype=np.int64)
    if shift < 0:
        raise ValueError("shift must be non-negative")
    if shift == 0:
        return values
    offset = 1 << (shift - 1)
    magnitude = (np.abs(values) + offset) >> shift
    return np.sign(values) * magnitude


def requantize_int8(values, multiplier, shift, relu):
    scaled = np.asarray(values, dtype=np.int64) * multiplier
    rounded = round_shift(scaled, shift)
    clipped = np.clip(rounded, -128, 127)
    if relu:
        clipped = np.maximum(clipped, 0)
    return clipped.astype(np.int8)


def tile_storage_bytes(tile_m, tile_k, tile_n):
    return tile_m * tile_k + tile_k * tile_n + 4 * tile_m * tile_n


def im2col_nchw(x, kernel_h, kernel_w, stride=1, padding=0):
    x = np.asarray(x, dtype=np.int8)
    batch, channels, height, width = x.shape
    out_h = (height + 2 * padding - kernel_h) // stride + 1
    out_w = (width + 2 * padding - kernel_w) // stride + 1
    padded = np.pad(
        x,
        ((0, 0), (0, 0), (padding, padding), (padding, padding)),
    )
    rows = []
    for n in range(batch):
        for out_y in range(out_h):
            for out_x in range(out_w):
                patch = padded[
                    n,
                    :,
                    out_y * stride:out_y * stride + kernel_h,
                    out_x * stride:out_x * stride + kernel_w,
                ]
                rows.append(patch.reshape(-1))
    return np.asarray(rows, dtype=np.int8), out_h, out_w


class PEArray:
    def __init__(self, rows, columns, tile_k):
        self.rows = rows
        self.columns = columns
        self.tile_k = tile_k
        self.trace = []

    def matmul_output_stationary(self, a, b):
        a = np.asarray(a, dtype=np.int8)
        b = np.asarray(b, dtype=np.int8)
        if a.ndim != 2 or b.ndim != 2 or a.shape[1] != b.shape[0]:
            raise ValueError("invalid GEMM shapes")
        m_size, k_size = a.shape
        _, n_size = b.shape
        output = np.zeros((m_size, n_size), dtype=np.int64)
        self.trace = []
        for m_start in range(0, m_size, self.rows):
            m_end = min(m_start + self.rows, m_size)
            for n_start in range(0, n_size, self.columns):
                n_end = min(n_start + self.columns, n_size)
                accumulator = np.zeros(
                    (m_end - m_start, n_end - n_start), dtype=np.int64
                )
                for k_start in range(0, k_size, self.tile_k):
                    k_end = min(k_start + self.tile_k, k_size)
                    a_tile = a[m_start:m_end, k_start:k_end]
                    b_tile = b[k_start:k_end, n_start:n_end]
                    for local_k in range(k_end - k_start):
                        accumulator += (
                            a_tile[:, local_k].astype(np.int64)[:, None]
                            * b_tile[local_k, :].astype(np.int64)[None, :]
                        )
                    self.trace.append({
                        "output_tile": (m_start, n_start),
                        "k_range": (k_start, k_end),
                    })
                output[m_start:m_end, n_start:n_end] = accumulator
        return output


@dataclass(frozen=True)
class ConvDescriptor:
    src: int
    weight: int
    bias: int
    dst: int
    stride: int
    padding: int
    multiplier: int
    shift: int
    relu: bool


class GlobalBuffer:
    def __init__(self, capacity_bytes):
        self.capacity_bytes = capacity_bytes
        self.entries = {}

    def load(self, name, value):
        value = np.asarray(value).copy()
        candidate_entries = dict(self.entries)
        candidate_entries[name] = value
        used_bytes = sum(item.nbytes for item in candidate_entries.values())
        if used_bytes > self.capacity_bytes:
            raise ValueError("resident tensors exceed global buffer capacity")
        self.entries[name] = value


class TeachingNPU:
    def __init__(self, dram, pe_rows, pe_columns, tile_k, buffer_bytes):
        self.dram = dram
        self.pe_array = PEArray(pe_rows, pe_columns, tile_k)
        self.global_buffer = GlobalBuffer(buffer_bytes)
        self.trace = []

    def execute_conv(self, descriptor):
        self.trace = []
        x = self.dram[descriptor.src]
        weight = self.dram[descriptor.weight]
        bias = self.dram[descriptor.bias]
        self.global_buffer.load("input", x)
        self.global_buffer.load("weight", weight)
        self.global_buffer.load("bias", bias)
        self.trace.extend(["DMA_LOAD_INPUT", "DMA_LOAD_WEIGHT", "DMA_LOAD_BIAS"])
        out_channels, in_channels, kernel_h, kernel_w = weight.shape
        if x.shape[1] != in_channels or bias.shape != (out_channels,):
            raise ValueError("invalid convolution descriptor")
        a_matrix, out_h, out_w = im2col_nchw(
            x, kernel_h, kernel_w, descriptor.stride, descriptor.padding
        )
        b_matrix = weight.reshape(out_channels, -1).T
        accumulator = self.pe_array.matmul_output_stationary(
            a_matrix, b_matrix
        )
        self.trace.append("PE_ARRAY_GEMM")
        accumulator += bias.astype(np.int64)[None, :]
        output_matrix = requantize_int8(
            accumulator,
            descriptor.multiplier,
            descriptor.shift,
            descriptor.relu,
        )
        self.trace.append("POST_PROCESS")
        output = output_matrix.reshape(
            x.shape[0], out_h, out_w, out_channels
        ).transpose(0, 3, 1, 2)
        self.dram[descriptor.dst] = output
        self.trace.append("DMA_STORE_OUTPUT")
        return output


def direct_conv_reference(
    x, weight, bias, stride, padding, multiplier, shift, relu
):
    x = np.asarray(x, dtype=np.int8)
    weight = np.asarray(weight, dtype=np.int8)
    batch, in_channels, height, width = x.shape
    out_channels, _, kernel_h, kernel_w = weight.shape
    out_h = (height + 2 * padding - kernel_h) // stride + 1
    out_w = (width + 2 * padding - kernel_w) // stride + 1
    padded = np.pad(
        x.astype(np.int32),
        ((0, 0), (0, 0), (padding, padding), (padding, padding)),
    )
    accumulator = np.zeros(
        (batch, out_channels, out_h, out_w), dtype=np.int64
    )
    for n in range(batch):
        for out_channel in range(out_channels):
            for out_y in range(out_h):
                for out_x in range(out_w):
                    window = padded[
                        n,
                        :,
                        out_y * stride:out_y * stride + kernel_h,
                        out_x * stride:out_x * stride + kernel_w,
                    ]
                    accumulator[n, out_channel, out_y, out_x] = (
                        bias[out_channel]
                        + np.sum(
                            window
                            * weight[out_channel].astype(np.int32),
                            dtype=np.int64,
                        )
                    )
    return requantize_int8(accumulator, multiplier, shift, relu)


INPUT, WEIGHT, BIAS, OUTPUT = 0x2000, 0x3000, 0x4000, 0x5000
input_tensor = np.arange(1, 17, dtype=np.int8).reshape(1, 1, 4, 4)
weight_tensor = np.array([
    [[[1, 1], [1, 1]]],
    [[[1, 0], [0, -1]]],
], dtype=np.int8)
bias_tensor = np.array([1, -1], dtype=np.int32)
dram = {INPUT: input_tensor, WEIGHT: weight_tensor, BIAS: bias_tensor}
descriptor = ConvDescriptor(
    INPUT, WEIGHT, BIAS, OUTPUT, 1, 0, 1, 0, True
)
npu = TeachingNPU(dram, 2, 2, 2, 128)
npu_output = npu.execute_conv(descriptor)
reference_output = direct_conv_reference(
    input_tensor, weight_tensor, bias_tensor, 1, 0, 1, 0, True
)
expected_channel_0 = np.array([
    [15, 19, 23],
    [31, 35, 39],
    [47, 51, 55],
], dtype=np.int8)
edge_a = np.array([
    [1, 2, 3],
    [4, 5, 6],
    [7, 8, 9],
], dtype=np.int8)
edge_b = np.array([
    [1, 0, 2],
    [0, 1, 3],
    [1, 1, 0],
], dtype=np.int8)
edge_array = PEArray(rows=2, columns=2, tile_k=2)
edge_output = edge_array.matmul_output_stationary(edge_a, edge_b)

assert tile_storage_bytes(2, 2, 2) == 24
assert np.array_equal(edge_output, edge_a.astype(np.int64) @ edge_b)
assert len(npu.pe_array.trace) == 10
assert npu_output.shape == (1, 2, 3, 3)
assert np.array_equal(npu_output, reference_output)
assert np.array_equal(npu_output[0, 0], expected_channel_0)
assert np.array_equal(npu_output[0, 1], np.zeros((3, 3), dtype=np.int8))
assert npu.trace == [
    "DMA_LOAD_INPUT",
    "DMA_LOAD_WEIGHT",
    "DMA_LOAD_BIAS",
    "PE_ARRAY_GEMM",
    "POST_PROCESS",
    "DMA_STORE_OUTPUT",
]
print("RISC-V NPU verification passed.")
