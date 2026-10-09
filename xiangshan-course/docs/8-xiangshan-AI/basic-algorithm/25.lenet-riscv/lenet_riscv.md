# 现代 LeNet、RISC-V 自定义指令与硬件执行结构

## 1. 文字讲解

### 1.1 本章解决什么问题

前面的 CNN 章节解释了卷积网络怎样计算，本章进一步讨论三个问题：

1. 现代化 LeNet 的每一层包含什么计算？
2. 怎样通过 RISC-V 自定义指令启动这些计算？
3. 一个能够执行这些指令的教学型加速单元需要哪些模块？

本章采用以下固定设计：

- 基础指令集为 RV32I。
- 使用 RISC-V 的 `custom-0` 主操作码空间。
- 权重和激活使用 INT8，乘法结果使用 INT32 累加。
- 卷积、池化、全连接和激活参数放在内存描述符中。
- 自定义指令通过寄存器传递描述符地址，而不是把所有张量尺寸直接编码在指令中。

需要严格区分两件事：`custom-0` 是 RISC-V 规范留给自定义扩展的编码空间；本章定义的 `NPU_CONV`、`NPU_POOL` 等指令是原创教学方案，不属于标准 RISC-V 指令。

### 1.2 本章使用的现代 LeNet

本章使用一个适合整数推理和硬件映射的现代简化版 LeNet：

| 层 | 参数 | 输出形状 |
|---|---|---|
| Input | 灰度图像 | $(1,32,32)$ |
| Conv1 | $6$ 个 $5\times5$ 卷积核 | $(6,28,28)$ |
| ReLU1 | 逐元素激活 | $(6,28,28)$ |
| MaxPool1 | $2\times2$ ，步长 $2$ | $(6,14,14)$ |
| Conv2 | $16$ 个 $5\times5$ 卷积核 | $(16,10,10)$ |
| ReLU2 | 逐元素激活 | $(16,10,10)$ |
| MaxPool2 | $2\times2$ ，步长 $2$ | $(16,5,5)$ |
| Flatten | 展平 | $(400,)$ |
| FC1 | $400\rightarrow120$ | $(120,)$ |
| ReLU3 | 逐元素激活 | $(120,)$ |
| FC2 | $120\rightarrow84$ | $(84,)$ |
| ReLU4 | 逐元素激活 | $(84,)$ |
| FC3 | $84\rightarrow10$ | $(10,)$ |

该结构保留了 LeNet 的“卷积、池化、卷积、池化、全连接”主干，但使用完整通道连接、ReLU、最大池化和普通分类 logits。它不是 1998 年原始 LeNet-5 的逐项复刻。

输入按照 NCHW 顺序表示为 $(N,C,H,W)$。本章只讨论批次大小 $N=1$ 的推理，但描述符中仍保留张量形状，便于扩展。

### 1.3 为什么使用描述符

一条 32 位 RISC-V 指令无法同时容纳输入地址、权重地址、输出地址、通道数、卷积核尺寸、步长和量化参数。因此，本章让软件先在内存中建立算子描述符：

```text
ConvDescriptor
├── src_addr
├── weight_addr
├── bias_addr
├── dst_addr
├── input_shape
├── weight_shape 或 output_features
├── stride
├── padding
├── requant_multiplier
└── requant_shift
```

RV32I 通用寄存器 `rs1` 保存描述符首地址。自定义指令只负责告诉加速单元“读取这个描述符并执行哪类算子”。

这种方式把两类信息分开：

- 指令编码确定操作类别，例如卷积或池化。
- 描述符确定本次操作的数据地址、形状和参数。

同一条 `NPU_CONV` 指令因此可以启动 Conv1 或 Conv2，而不需要为每种形状定义不同指令。

### 1.4 自定义指令格式

`custom-0` 的 7 位主操作码为 `0001011`，十六进制为 `0x0B`。本章采用与 R 型指令相同的字段布局：

```text
31          25 24      20 19      15 14   12 11       7 6        0
+--------------+----------+----------+-------+----------+----------+
|    funct7    |   rs2    |   rs1    |funct3 |    rd    | 0001011  |
+--------------+----------+----------+-------+----------+----------+
```

本章将 `funct7` 和 `rs2` 固定为零，用 `funct3` 区分五条教学指令：

| 指令 | `funct3` | `rs1` 的含义 | `rd` 的含义 |
|---|---:|---|---|
| `NPU_CONV` | `000` | 卷积描述符地址 | 命令编号 |
| `NPU_POOL` | `001` | 池化描述符地址 | 命令编号 |
| `NPU_FC` | `010` | 全连接描述符地址 | 命令编号 |
| `NPU_ACT` | `011` | 激活描述符地址 | 命令编号 |
| `NPU_WAIT` | `100` | 待等待的命令编号 | 完成状态 |

前四条指令向命令队列提交任务，并在 `rd` 中返回命令编号。`NPU_WAIT` 等待 `rs1` 中指定的命令完成，并在 `rd` 中返回状态。本章约定状态 `0` 表示成功。

本教学方案约定命令队列按照提交顺序执行，因此后一条算子任务可以读取前一条任务写出的张量。若真实实现允许乱序执行，就必须在描述符或命令中增加依赖信息，或者在有数据依赖的任务之间插入等待。

若平台未实现该自定义扩展，相关编码应被当作非法指令处理。真实实现还必须定义异常、描述符对齐、内存访问权限、缓存一致性和指令排序规则；本章模拟器只实现正常执行路径。

### 1.5 LeNet 到指令序列的映射

LeNet 前向传播可以转换为下面的算子任务：

```text
NPU_CONV  Conv1_descriptor
NPU_ACT   ReLU1_descriptor
NPU_POOL  Pool1_descriptor
NPU_CONV  Conv2_descriptor
NPU_ACT   ReLU2_descriptor
NPU_POOL  Pool2_descriptor
NPU_FC    FC1_descriptor
NPU_ACT   ReLU3_descriptor
NPU_FC    FC2_descriptor
NPU_ACT   ReLU4_descriptor
NPU_FC    FC3_descriptor
NPU_WAIT  last_command
```

`Flatten` 只改变张量的逻辑形状，不改变元素顺序，因此在连续 NCHW 存储的假设下不需要单独的计算指令。软件可以让 FC1 描述符直接把 Pool2 输出解释成长度为 $400$ 的向量。

这段序列只表示算子启动关系。权重加载、输入准备和输出读取仍需要标准 RV32I 程序、运行时函数或 DMA 配置配合完成。

### 1.6 教学型硬件执行结构

能够执行上述指令的教学型系统可以分成 RISC-V 控制端和加速执行端：

```text
               RV32I 处理器
        +-------------------------+
        | 取指/译码/寄存器文件    |
        | custom-0 识别与发射接口 |
        +------------+------------+
                     |
                     v
        +-------------------------+
        | 命令队列与完成状态表    |
        | 描述符读取与参数检查    |
        +------------+------------+
                     |
          +----------+----------+
          |                     |
          v                     v
+------------------+   +------------------+
| Load/Store 单元  |   | 算子控制器       |
| 输入/权重/输出传输|   | 循环与地址生成   |
+--------+---------+   +--------+---------+
         |                      |
         v                      v
+------------------------------------------------+
| 输入缓冲区 | 权重缓冲区 | INT8 乘法阵列       |
| INT32 累加器 | ReLU/重新量化 | 最大池化单元    |
+-------------------------+----------------------+
                          |
                          v
                     输出缓冲区
```

各模块职责如下：

- **自定义指令译码与发射接口**：识别 `opcode=0x0B`，读取 `rs1`，并把命令交给加速端。
- **命令队列**：保存尚未完成的任务，并为每个任务分配命令编号。
- **描述符读取单元**：从内存读取地址、形状、步长和重新量化参数。
- **Load/Store 单元**：在共享内存与局部缓冲区之间移动输入、权重和输出。
- **输入与权重缓冲区**：暂存当前计算块需要的数据。
- **乘法与累加单元**：执行 INT8 乘法和 INT32 累加，可同时服务卷积和全连接。
- **后处理单元**：执行偏置加法、ReLU、舍入、移位、饱和裁剪和 INT8 输出。
- **池化单元**：对窗口执行最大值归约。
- **完成状态表**：记录命令是否结束以及是否出现错误，供 `NPU_WAIT` 查询。

这是一种功能级模块划分，不是完整 RTL。MAC 数量、缓冲区容量、端口数量和总线协议都必须结合实现目标另行确定，本章不提供面积、时钟频率、功耗或吞吐率结论。

### 1.7 基本执行流程

1. RV32I 软件把输入、权重和偏置放入约定内存区域。
2. 软件为每个 LeNet 算子建立描述符。
3. 软件把描述符地址写入某个通用寄存器。
4. `custom-0` 译码逻辑识别 `NPU_CONV` 等指令。
5. 加速端读取描述符，将任务放入命令队列并返回命令编号。
6. Load/Store 单元准备输入和权重，算子控制器生成访问顺序。
7. 乘法阵列和 INT32 累加器完成卷积或全连接。
8. 后处理单元执行偏置、ReLU及重新量化，或由池化单元完成最大池化。
9. 结果写入描述符指定的输出地址。
10. `NPU_WAIT` 返回完成状态，RV32I 软件继续处理结果。

### 1.8 直观例子

代码示例不装入完整 LeNet 权重，而是执行一条缩小的算子链：

```text
输入 (1,1,4,4)
    -> 2x2 卷积，权重全为 1
    -> 右移 2 位并舍入，得到 INT8
    -> ReLU
    -> 2x2 最大池化，步长 1
    -> Flatten
    -> 4 输入、2 输出的全连接层
```

输入为：

$$

X=
\begin{bmatrix}
1&2&3&4\\
5&6&7&8\\
9&10&11&12\\
13&14&15&16
\end{bmatrix}.

$$

卷积后重新量化的结果为：

$$

Q=
\begin{bmatrix}
4&5&6\\
8&9&10\\
12&13&14
\end{bmatrix}.

$$

最大池化得到：

$$

P=
\begin{bmatrix}
9&10\\
13&14
\end{bmatrix}.

$$

最后全连接层选择第一个和第四个输入，并分别加上 $1$ 和 $-1$ 的偏置，因此输出为：

$$

Y=[10,13]^{\mathsf T}.

$$

## 2. 公式讲解

### 2.1 LeNet 张量形状

二维卷积的输出高度和宽度为：

$$

H_{\text{out}}
=
\left\lfloor
\frac{H_{\text{in}}+2P_h-K_h}{S_h}
\right\rfloor+1,

$$

$$

W_{\text{out}}
=
\left\lfloor
\frac{W_{\text{in}}+2P_w-K_w}{S_w}
\right\rfloor+1.

$$

Conv1 使用 $H_{\text{in}}=W_{\text{in}}=32$ 、 $K_h=K_w=5$ 、 $S_h=S_w=1$ 和零填充，因此：

$$

H_{\text{out}}=W_{\text{out}}
=
\frac{32-5}{1}+1
=28.

$$

$2\times2$ 最大池化使用步长 $2$ ，因此把 $28\times28$ 变成 $14\times14$。Conv2 再把 $14\times14$ 变成 $10\times10$ ，第二次池化得到 $5\times5$。最终展平长度为：

$$

16\times5\times5=400.

$$

### 2.2 INT8 卷积与 INT32 累加

设输入激活为 $X$ ，卷积权重为 $W$ ，INT32 偏置为 $b$。批次大小为 $1$ 时，卷积累加结果为：

$$

A_{c_o,h_o,w_o}
=
b_{c_o}
+
\sum_{c_i=0}^{C_{\text{in}}-1}
\sum_{k_h=0}^{K_h-1}
\sum_{k_w=0}^{K_w-1}
X_{c_i,h_oS_h+k_h-P_h,w_oS_w+k_w-P_w}
W_{c_o,c_i,k_h,k_w}.

$$

$X$ 和 $W$ 的元素是有符号 INT8，乘积进入 INT32 累加器。若索引落在填充区域，本章约定对应输入值为零。

### 2.3 整数重新量化

本章描述符使用整数乘子 $m$ 和非负右移量 $n$。先计算：

$$

z=mA.

$$

当 $n\ge1$ 时，采用“绝对值四舍五入，恰好一半时远离零”的规则：

$$

\operatorname{RoundShift}(z,n)
=
\operatorname{sgn}(z)
\left\lfloor
\frac{|z|+2^{n-1}}{2^n}
\right\rfloor.

$$

当 $n=0$ 时：

$$

\operatorname{RoundShift}(z,0)=z.

$$

最终 INT8 输出为：

$$

q
=
\operatorname{clip}
\left(
\operatorname{RoundShift}(mA,n),
-128,
127
\right).

$$

若随后执行 ReLU，则：

$$

q_{\text{relu}}=\max(0,q).

$$

真实量化模型中的 $m$ 和 $n$ 应由输入、权重与输出量化尺度推导或校准。本章只规定执行语义，不重复推导量化参数。

### 2.4 最大池化

对于窗口大小 $K_p\times K_p$ 、步长 $S_p$ 的最大池化：

$$

Y_{c,h_o,w_o}
=
\max_{\substack{0\le i<K_p\\0\le j<K_p}}
X_{c,h_oS_p+i,w_oS_p+j}.

$$

最大池化只比较 INT8 激活，不需要 INT32 乘加。

### 2.5 全连接层

设输入向量 $x\in\mathbb Z^{D_{\text{in}}}$ ，权重矩阵 $W\in\mathbb Z^{D_{\text{out}}\times D_{\text{in}}}$ ，偏置 $b\in\mathbb Z^{D_{\text{out}}}$。INT32 累加结果为：

$$

A_j=b_j+\sum_{i=0}^{D_{\text{in}}-1}W_{j,i}x_i.

$$

全连接输出同样通过 `RoundShift` 和 `clip` 转回 INT8。卷积与全连接因此可以共享 INT8 乘法器、INT32 累加器和重新量化单元。

### 2.6 自定义指令编码

设 32 位指令字为 $I$ ，本章采用以下编码：

$$

\begin{aligned}
I={}&
(\text{funct7}\ll25)
\mathbin{|}
(\text{rs2}\ll20)
\mathbin{|}
(\text{rs1}\ll15)\\
&\mathbin{|}
(\text{funct3}\ll12)
\mathbin{|}
(\text{rd}\ll7)
\mathbin{|}
\text{0x0B}.
\end{aligned}

$$

字段可以从指令字中恢复：

$$

\text{opcode}=I\mathbin{\&}\text{0x7F},

$$

$$

\text{rd}=(I\gg7)\mathbin{\&}\text{0x1F},

$$

$$

\text{funct3}=(I\gg12)\mathbin{\&}\text{0x7},

$$

$$

\text{rs1}=(I\gg15)\mathbin{\&}\text{0x1F},

$$

$$

\text{rs2}=(I\gg20)\mathbin{\&}\text{0x1F},

$$

$$

\text{funct7}=(I\gg25)\mathbin{\&}\text{0x7F}.

$$

### 2.7 变量含义

- $N$：批次大小，本章 LeNet 推理取 $N=1$。
- $C$ 、 $C_{\text{in}}$ 、 $C_{\text{out}}$：通道数、输入通道数和输出通道数。
- $H_{\text{in}}$ 、 $W_{\text{in}}$：输入特征图高度和宽度。
- $H_{\text{out}}$ 、 $W_{\text{out}}$：输出特征图高度和宽度。
- $K_h$ 、 $K_w$：卷积核高度和宽度。
- $S_h$ 、 $S_w$：卷积垂直和水平方向的步长。
- $P_h$ 、 $P_w$：卷积垂直和水平方向的零填充大小。
- $\lfloor\cdot\rfloor$：向下取整。
- $X$：INT8 输入激活张量。
- $W$：INT8 卷积核或全连接权重。
- $b$：INT32 偏置。
- $A$：偏置加法完成后的 INT32 累加结果。
- $c_i$ 、 $c_o$：输入通道和输出通道索引。
- $h_o$ 、 $w_o$：输出特征图的位置索引。
- $k_h$ 、 $k_w$：卷积核内部的位置索引。
- $m$：重新量化使用的整数乘子。
- $n$：重新量化使用的非负右移位数。
- $z$：右移前的整数中间值 $mA$。
- $\operatorname{sgn}(z)$：符号函数，正数为 $1$ 、负数为 $-1$ 、零为 $0$。
- $\operatorname{RoundShift}$：本章定义的带舍入算术右移。
- $\operatorname{clip}(x,a,b)$：把 $x$ 限制到闭区间 $[a,b]$。
- $q$ 、 $q_{\text{relu}}$：重新量化结果和 ReLU 结果。
- $K_p$ 、 $S_p$：池化窗口大小和池化步长。
- $D_{\text{in}}$ 、 $D_{\text{out}}$：全连接层输入和输出维度。
- $I$：32 位 RISC-V 自定义指令字。
- `funct7`、`funct3`：自定义功能字段。
- `rs1`、`rs2`、`rd`：RISC-V 源寄存器和目标寄存器编号字段。
- $\ll$ 、 $\gg$：逻辑左移和逻辑右移。
- $\mathbin{|}$：按位或。
- $\mathbin{\&}$：按位与。
- `0x0B`：`custom-0` 的 7 位主操作码。

### 2.8 公式怎么理解

输出形状公式决定每层描述符中的张量尺寸。描述符若写错形状，后续地址生成和循环边界也会错误，因此软件生成描述符时必须先完成形状推导。

卷积和全连接都可以归结为多次 INT8 乘法与 INT32 求和。两者的主要区别是索引方式：卷积在空间窗口和通道上遍历，全连接直接遍历输入向量。

重新量化公式把较宽的累加结果转换为下一层需要的 INT8。舍入和饱和规则属于指令语义的一部分，软件模拟器与硬件必须使用同一规则，否则边界输入可能得到不同结果。

指令编码公式只是把若干位域放入 32 位指令字。`opcode` 确定这是 `custom-0` 指令，`funct3` 选择具体 NPU 操作，`rs1` 和 `rd` 则连接 RV32I 寄存器文件与加速命令接口。

## 3. 代码示例

### 3.1 最小可运行代码

下面的代码包含四部分：

1. 推导现代 LeNet 各层形状并生成算子计划。
2. 编码和解码五条 RISC-V 教学型自定义指令。
3. 实现 INT8 卷积、ReLU、最大池化、全连接和重新量化。
4. 通过描述符和寄存器执行一段缩小的指令程序。

```python
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

    funct7 = 0
    rs2 = 0
    funct3 = FUNCT3[name]
    return (
        (funct7 << 25)
        | (rs2 << 20)
        | (rs1 << 15)
        | (funct3 << 12)
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
        "NPU_CONV",
        "NPU_ACT",
        "NPU_POOL",
        "NPU_CONV",
        "NPU_ACT",
        "NPU_POOL",
        "NPU_FC",
        "NPU_ACT",
        "NPU_FC",
        "NPU_ACT",
        "NPU_FC",
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
    accumulator = np.zeros(
        (n, c_out, h_out, w_out), dtype=np.int64
    )

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
                            window
                            * weight[out_channel].astype(np.int32),
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
        if name == "NPU_WAIT":
            result = self.wait(rs1_value)
        else:
            result = self.launch(name, rs1_value)
        if fields["rd"] != 0:
            registers[fields["rd"]] = result
        registers[0] = 0


layers, lenet_plan = lenet_shapes_and_plan()

# 缩小示例使用的“共享内存”地址
INPUT = 0x2000
CONV_WEIGHT = 0x2100
CONV_BIAS = 0x2200
CONV_OUTPUT = 0x2300
RELU_OUTPUT = 0x2400
POOL_OUTPUT = 0x2500
FC_WEIGHT = 0x2600
FC_BIAS = 0x2700
FC_OUTPUT = 0x2800

memory = {
    INPUT: np.arange(1, 17, dtype=np.int8).reshape(1, 1, 4, 4),
    CONV_WEIGHT: np.ones((1, 1, 2, 2), dtype=np.int8),
    CONV_BIAS: np.array([0], dtype=np.int32),
    FC_WEIGHT: np.array([
        [1, 0, 0, 0],
        [0, 0, 0, 1],
    ], dtype=np.int8),
    FC_BIAS: np.array([1, -1], dtype=np.int32),
}

CONV_DESC = 0x1000
ACT_DESC = 0x1004
POOL_DESC = 0x1008
FC_DESC = 0x100C
descriptors = {
    CONV_DESC: ConvDescriptor(
        INPUT,
        CONV_WEIGHT,
        CONV_BIAS,
        CONV_OUTPUT,
        input_shape=(1, 1, 4, 4),
        weight_shape=(1, 1, 2, 2),
        multiplier=1,
        shift=2,
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

# x5~x8 保存描述符地址，x10~x13 接收命令编号
registers = [0] * 32
registers[5] = CONV_DESC
registers[6] = ACT_DESC
registers[7] = POOL_DESC
registers[8] = FC_DESC

program = [
    encode_npu_instruction("NPU_CONV", rd=10, rs1=5),
    encode_npu_instruction("NPU_ACT", rd=11, rs1=6),
    encode_npu_instruction("NPU_POOL", rd=12, rs1=7),
    encode_npu_instruction("NPU_FC", rd=13, rs1=8),
]

npu = TeachingNPU(memory, descriptors)
for instruction in program:
    npu.execute_instruction(instruction, registers)

# x9 保存最后一个命令编号，NPU_WAIT 把状态写入 x14
registers[9] = registers[13]
wait_instruction = encode_npu_instruction("NPU_WAIT", rd=14, rs1=9)
npu.execute_instruction(wait_instruction, registers)

decoded = [decode_npu_instruction(word) for word in program]

print("LeNet shapes:", layers)
print("LeNet operator plan:", lenet_plan)
print("encoded instructions:", [f"0x{word:08x}" for word in program])
print("decoded names:", [item["name"] for item in decoded])
print("convolution output:")
print(memory[CONV_OUTPUT][0, 0])
print("pool output:")
print(memory[POOL_OUTPUT][0, 0])
print("FC output:", memory[FC_OUTPUT])
print("wait status:", registers[14])

assert layers[-1] == ("FC3", (10,))
assert len(lenet_plan) == 11
assert [item["name"] for item in decoded] == [
    "NPU_CONV",
    "NPU_ACT",
    "NPU_POOL",
    "NPU_FC",
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
```

### 3.2 输入输出说明

- `layers` 保存本章现代 LeNet 从 $(1,32,32)$ 到 $(10,)$ 的完整形状变化。
- `lenet_plan` 包含两个卷积、两个池化、三个全连接和四个激活任务，共 $11$ 个计算指令。
- `program` 包含缩小示例的卷积、激活、池化和全连接指令。
- 所有指令的 `opcode` 都等于 `0x0B`，`funct3` 决定具体操作。
- `memory` 使用整数地址模拟共享内存，数组对象模拟相应地址处的张量。
- 卷积输入形状为 `(1,1,4,4)`，权重形状为 `(1,1,2,2)`。
- 卷积描述符指定 `multiplier=1`、`shift=2`，对应除以 $4$ 并按本章规则舍入。
- 池化输出为 `[[9,10],[13,14]]`，展平后送入全连接层。
- 最终 INT8 输出为 `[10,13]`，`NPU_WAIT` 返回状态 `0`。

### 3.3 关键代码解释

`encode_npu_instruction` 按 R 型字段位置构造 32 位指令。`decode_npu_instruction` 执行相反操作，并检查主操作码、保留字段和 `funct3` 是否符合本章定义。

`round_shift` 使用绝对值实现对正负数一致的舍入规则，避免直接依赖不同语言对负数右移和除法舍入的具体行为。`requantize_int8` 随后把结果饱和到 $[-128,127]$。

`conv2d_int8` 和 `linear_int8` 都先把 INT8 操作数扩展后相乘，并使用更宽的整数保存累加结果。代码使用 NumPy 的 `int64` 防止教学示例在软件侧发生中间溢出，但描述的目标算子语义是 INT32 累加；实际描述符必须保证累加结果处于 INT32 范围，或明确定义溢出处理。

四个描述符类分别保存各算子的地址、形状和参数。`TeachingNPU.launch` 会先比较描述符形状与内存张量形状，再执行对应整数算子。`TeachingNPU.execute_instruction` 从 RV32I 寄存器读取描述符地址，并把命令编号写回 `rd`。

模拟器为了保持代码简单，会在 `launch` 中立即完成任务，然后把命令编号标记为完成。真实命令队列可以异步执行，此时 `NPU_WAIT` 必须等待状态表中的对应任务结束。

最后的断言同时验证 LeNet 形状、自定义编码、指令译码、卷积重新量化、最大池化、全连接和等待状态。该模拟器验证的是功能语义，不代表 RTL 时序、总线协议或综合结果。
