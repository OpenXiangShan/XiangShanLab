from dataclasses import dataclass

import numpy as np


CUSTOM_0 = 0x0B
FUNCT3 = {
    "NPU_CONV": 0b000,
    "NPU_POOL": 0b001,
    "NPU_FC": 0b010,
    "NPU_ACT": 0b011,
    "NPU_WAIT": 0b100,
}
FUNCT3_TO_NAME = {value: name for name, value in FUNCT3.items()}


def encode_npu_instruction(name, rd, rs1):
    if name not in FUNCT3:
        raise ValueError(f"unknown instruction: {name}")
    if not (0 <= rd < 32 and 0 <= rs1 < 32):
        raise ValueError("register index must be in [0, 31]")
    return (
        (rs1 << 15)
        | (FUNCT3[name] << 12)
        | (rd << 7)
        | CUSTOM_0
    )


def decode_npu_instruction(word):
    fields = {
        "opcode": word & 0x7F,
        "rd": (word >> 7) & 0x1F,
        "funct3": (word >> 12) & 0x7,
        "rs1": (word >> 15) & 0x1F,
        "rs2": (word >> 20) & 0x1F,
        "funct7": (word >> 25) & 0x7F,
    }
    if fields["opcode"] != CUSTOM_0:
        raise ValueError("not a custom-0 instruction")
    if fields["rs2"] != 0 or fields["funct7"] != 0:
        raise ValueError("reserved fields must be zero")
    if fields["funct3"] not in FUNCT3_TO_NAME:
        raise ValueError("unsupported funct3")
    fields["name"] = FUNCT3_TO_NAME[fields["funct3"]]
    return fields


def lenet_shapes_and_plan():
    layers = [
        ("Input", (1, 32, 32)),
        ("Conv1", (6, 28, 28)),
        ("ReLU1", (6, 28, 28)),
        ("MaxPool1", (6, 14, 14)),
        ("Conv2", (16, 10, 10)),
        ("ReLU2", (16, 10, 10)),
        ("MaxPool2", (16, 5, 5)),
        ("Flatten", (400,)),
        ("FC1", (120,)),
        ("ReLU3", (120,)),
        ("FC2", (84,)),
        ("ReLU4", (84,)),
        ("FC3", (10,)),
    ]
    plan = [
        "NPU_CONV", "NPU_ACT", "NPU_POOL",
        "NPU_CONV", "NPU_ACT", "NPU_POOL",
        "NPU_FC", "NPU_ACT", "NPU_FC", "NPU_ACT", "NPU_FC",
    ]
    return layers, plan


def round_shift(values, shift):
    values = np.asarray(values, dtype=np.int64)
    if shift < 0:
        raise ValueError("shift must be non-negative")
    if shift == 0:
        return values
    offset = 1 << (shift - 1)
    magnitude = (np.abs(values) + offset) >> shift
    return np.sign(values) * magnitude


def requantize_int8(values, multiplier, shift):
    scaled = np.asarray(values, dtype=np.int64) * multiplier
    rounded = round_shift(scaled, shift)
    return np.clip(rounded, -128, 127).astype(np.int8)


def conv2d_int8(x, weight, bias, stride, padding, multiplier, shift):
    x = np.asarray(x, dtype=np.int8)
    weight = np.asarray(weight, dtype=np.int8)
    bias = np.asarray(bias, dtype=np.int32)
    n, c_in, h_in, w_in = x.shape
    c_out, weight_c_in, kernel_h, kernel_w = weight.shape
    if c_in != weight_c_in or bias.shape != (c_out,):
        raise ValueError("invalid convolution shapes")
    h_out = (h_in + 2 * padding - kernel_h) // stride + 1
    w_out = (w_in + 2 * padding - kernel_w) // stride + 1
    padded = np.pad(
        x.astype(np.int32),
        ((0, 0), (0, 0), (padding, padding), (padding, padding)),
    )
    accumulator = np.zeros((n, c_out, h_out, w_out), dtype=np.int64)
    for batch in range(n):
        for out_channel in range(c_out):
            for out_h in range(h_out):
                for out_w in range(w_out):
                    window = padded[
                        batch,
                        :,
                        out_h * stride:out_h * stride + kernel_h,
                        out_w * stride:out_w * stride + kernel_w,
                    ]
                    accumulator[batch, out_channel, out_h, out_w] = (
                        bias[out_channel]
                        + np.sum(
                            window * weight[out_channel].astype(np.int32),
                            dtype=np.int64,
                        )
                    )
    return requantize_int8(accumulator, multiplier, shift)


def relu_int8(x):
    return np.maximum(np.asarray(x, dtype=np.int8), 0).astype(np.int8)


def max_pool2d_int8(x, kernel, stride):
    x = np.asarray(x, dtype=np.int8)
    n, channels, h_in, w_in = x.shape
    h_out = (h_in - kernel) // stride + 1
    w_out = (w_in - kernel) // stride + 1
    output = np.empty((n, channels, h_out, w_out), dtype=np.int8)
    for batch in range(n):
        for channel in range(channels):
            for out_h in range(h_out):
                for out_w in range(w_out):
                    window = x[
                        batch,
                        channel,
                        out_h * stride:out_h * stride + kernel,
                        out_w * stride:out_w * stride + kernel,
                    ]
                    output[batch, channel, out_h, out_w] = np.max(window)
    return output


def linear_int8(x, weight, bias, multiplier, shift):
    x = np.asarray(x, dtype=np.int8).reshape(-1).astype(np.int32)
    weight = np.asarray(weight, dtype=np.int8).astype(np.int32)
    bias = np.asarray(bias, dtype=np.int32)
    if weight.shape[1] != x.size or bias.shape != (weight.shape[0],):
        raise ValueError("invalid linear shapes")
    accumulator = weight.astype(np.int64) @ x.astype(np.int64) + bias
    return requantize_int8(accumulator, multiplier, shift)


@dataclass(frozen=True)
class ConvDescriptor:
    src: int
    weight: int
    bias: int
    dst: int
    input_shape: tuple[int, int, int, int]
    weight_shape: tuple[int, int, int, int]
    stride: int = 1
    padding: int = 0
    multiplier: int = 1
    shift: int = 0


@dataclass(frozen=True)
class PoolDescriptor:
    src: int
    dst: int
    input_shape: tuple[int, int, int, int]
    kernel: int
    stride: int


@dataclass(frozen=True)
class FCDescriptor:
    src: int
    weight: int
    bias: int
    dst: int
    input_features: int
    output_features: int
    multiplier: int = 1
    shift: int = 0


@dataclass(frozen=True)
class ActDescriptor:
    src: int
    dst: int
    input_shape: tuple[int, ...]


class TeachingNPU:
    def __init__(self, memory, descriptors):
        self.memory = memory
        self.descriptors = descriptors
        self.next_ticket = 1
        self.completed = set()

    def launch(self, name, descriptor_address):
        descriptor = self.descriptors[descriptor_address]
        if name == "NPU_CONV":
            if self.memory[descriptor.src].shape != descriptor.input_shape:
                raise ValueError("convolution input shape mismatch")
            if self.memory[descriptor.weight].shape != descriptor.weight_shape:
                raise ValueError("convolution weight shape mismatch")
            self.memory[descriptor.dst] = conv2d_int8(
                self.memory[descriptor.src],
                self.memory[descriptor.weight],
                self.memory[descriptor.bias],
                descriptor.stride,
                descriptor.padding,
                descriptor.multiplier,
                descriptor.shift,
            )
        elif name == "NPU_POOL":
            if self.memory[descriptor.src].shape != descriptor.input_shape:
                raise ValueError("pool input shape mismatch")
            self.memory[descriptor.dst] = max_pool2d_int8(
                self.memory[descriptor.src],
                descriptor.kernel,
                descriptor.stride,
            )
        elif name == "NPU_FC":
            if self.memory[descriptor.src].size != descriptor.input_features:
                raise ValueError("linear input size mismatch")
            if self.memory[descriptor.weight].shape != (
                descriptor.output_features,
                descriptor.input_features,
            ):
                raise ValueError("linear weight shape mismatch")
            self.memory[descriptor.dst] = linear_int8(
                self.memory[descriptor.src],
                self.memory[descriptor.weight],
                self.memory[descriptor.bias],
                descriptor.multiplier,
                descriptor.shift,
            )
        elif name == "NPU_ACT":
            if self.memory[descriptor.src].shape != descriptor.input_shape:
                raise ValueError("activation input shape mismatch")
            self.memory[descriptor.dst] = relu_int8(
                self.memory[descriptor.src]
            )
        else:
            raise ValueError(f"cannot launch {name}")
        ticket = self.next_ticket
        self.next_ticket += 1
        self.completed.add(ticket)
        return ticket

    def wait(self, ticket):
        return 0 if ticket in self.completed else 1

    def execute_instruction(self, word, registers):
        fields = decode_npu_instruction(word)
        name = fields["name"]
        rs1_value = registers[fields["rs1"]]
        result = (
            self.wait(rs1_value)
            if name == "NPU_WAIT"
            else self.launch(name, rs1_value)
        )
        if fields["rd"] != 0:
            registers[fields["rd"]] = result
        registers[0] = 0


layers, lenet_plan = lenet_shapes_and_plan()

INPUT, CONV_WEIGHT, CONV_BIAS = 0x2000, 0x2100, 0x2200
CONV_OUTPUT, RELU_OUTPUT, POOL_OUTPUT = 0x2300, 0x2400, 0x2500
FC_WEIGHT, FC_BIAS, FC_OUTPUT = 0x2600, 0x2700, 0x2800

memory = {
    INPUT: np.arange(1, 17, dtype=np.int8).reshape(1, 1, 4, 4),
    CONV_WEIGHT: np.ones((1, 1, 2, 2), dtype=np.int8),
    CONV_BIAS: np.array([0], dtype=np.int32),
    FC_WEIGHT: np.array([[1, 0, 0, 0], [0, 0, 0, 1]], dtype=np.int8),
    FC_BIAS: np.array([1, -1], dtype=np.int32),
}

CONV_DESC, ACT_DESC, POOL_DESC, FC_DESC = (
    0x1000, 0x1004, 0x1008, 0x100C
)
descriptors = {
    CONV_DESC: ConvDescriptor(
        INPUT, CONV_WEIGHT, CONV_BIAS, CONV_OUTPUT,
        input_shape=(1, 1, 4, 4),
        weight_shape=(1, 1, 2, 2),
        multiplier=1, shift=2,
    ),
    ACT_DESC: ActDescriptor(
        CONV_OUTPUT, RELU_OUTPUT, input_shape=(1, 1, 3, 3)
    ),
    POOL_DESC: PoolDescriptor(
        RELU_OUTPUT,
        POOL_OUTPUT,
        input_shape=(1, 1, 3, 3),
        kernel=2,
        stride=1,
    ),
    FC_DESC: FCDescriptor(
        POOL_OUTPUT,
        FC_WEIGHT,
        FC_BIAS,
        FC_OUTPUT,
        input_features=4,
        output_features=2,
    ),
}

registers = [0] * 32
registers[5:9] = [CONV_DESC, ACT_DESC, POOL_DESC, FC_DESC]
program = [
    encode_npu_instruction("NPU_CONV", 10, 5),
    encode_npu_instruction("NPU_ACT", 11, 6),
    encode_npu_instruction("NPU_POOL", 12, 7),
    encode_npu_instruction("NPU_FC", 13, 8),
]

npu = TeachingNPU(memory, descriptors)
for instruction in program:
    npu.execute_instruction(instruction, registers)

registers[9] = registers[13]
npu.execute_instruction(
    encode_npu_instruction("NPU_WAIT", 14, 9),
    registers,
)
decoded = [decode_npu_instruction(word) for word in program]

assert layers[-1] == ("FC3", (10,))
assert len(lenet_plan) == 11
assert [item["name"] for item in decoded] == [
    "NPU_CONV", "NPU_ACT", "NPU_POOL", "NPU_FC"
]
assert all(item["opcode"] == 0x0B for item in decoded)
assert program[0] == 0x0002850B
assert np.array_equal(
    round_shift(np.array([-6, -2, -1, 0, 1, 2, 6]), 2),
    [-2, -1, 0, 0, 0, 1, 2],
)
assert np.array_equal(
    requantize_int8(np.array([-200, 127, 128]), 1, 0),
    [-128, 127, 127],
)
assert np.array_equal(
    memory[CONV_OUTPUT][0, 0],
    [[4, 5, 6], [8, 9, 10], [12, 13, 14]],
)
assert np.array_equal(
    memory[POOL_OUTPUT][0, 0],
    [[9, 10], [13, 14]],
)
assert np.array_equal(memory[FC_OUTPUT], [10, 13])
assert registers[14] == 0
print("LeNet RISC-V custom instruction verification passed.")
