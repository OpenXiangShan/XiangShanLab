# RISC-V Zbkb 标准扩展：密码用位操作指令解析

> 规范基线：[RISC-V Cryptography Extensions 1.0.1（Scalar & Entropy Source）](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html)。`Zbkb`（Bit-manipulation for Cryptography）1.0.0 于 2021-12 批准，1.0.1 于 2022-02 批准。
>
> 本文只讨论 `Zbkb`；`Zbb` 的通用位操作语义见 [3-zbb.md](3-zbb.md)，B 系列分组见 [1-rvb.md](1-rvb.md)。

## 1. 定位与结论

`Zbkb` 是面向密码实现的位操作扩展。它复用了 `Zbb` 中密码常用的逻辑与旋转指令，并增加打包（`pack*`）、字节内位反转（`brev8`）和位交织（`zip`/`unzip`）。它不是 `Zbb` 的超集：`Zbkb` 不包含 `clz`/`ctz`/`cpop`、最值、`sext`/`zext`、`orc.b` 等指令。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 与 `Zbb` 的关系 | 共享 `andn`/`orn`/`xnor`、旋转、`rev8`，但缺少计数、最值、扩展等指令 | `Zbkb` 包含完整 `Zbb` | T1-VERIFIED |
| 指令数量 | 16 条：与 `Zbb` 共享 10 条，`Zbkb` 独有 6 条 | `Zbkb` 只有 `pack` 系列 | T1-VERIFIED |
| `pack` 的拼接宽度 | 拼接 `XLEN/2` 位的低半部分：RV32 为 16 位，RV64 为 32 位 | 固定拼接 16 位 | T1-VERIFIED |
| `packw` 的扩展方式 | RV64 下把 32 位结果 **符号扩展** 到 64 位 | 零扩展到 64 位 | T1-VERIFIED |
| `zip`/`unzip` 的 XLEN | 仅 RV32 可用 | RV32/RV64 都可用 | T1-VERIFIED |
| 常数时间 | ISA 不保证执行延迟与数据无关 | 带 `Zbkb` 就自动抗侧信道 | UNVERIFIED |

## 2. 规范来源与范围

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html) | `Zbkb` 指令集合、`pack*`/`brev8`/`zip` 语义与用途 | T1-VERIFIED |
| [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html) | 共享的 `andn`/`orn`/`xnor`/旋转/`rev8` 语义与编码 | T1-VERIFIED |
| [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 指令编码与 XLEN 约束交叉检查 | T2-CROSS-CHECKED |

范围边界：本文不展开 `Zbb` 共享指令的逐条语义，只给出编码索引；密码算法本身的实现不在本文范围。执行延迟、SBox 是否常数时间属于微架构/实现问题（UNVERIFIED）。

## 3. 指令集合

### 3.1 与 `Zbb` 共享的指令

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `andn`/`orn`/`xnor` | 按位与/或/异或的非 | ✓ | ✓ | OP，`funct7=0100000`，`funct3=111/110/100` |
| `rol`/`ror` | 寄存器旋转 | ✓ | ✓ | OP，`funct7=0110000`，`funct3=001/101` |
| `rori` | 立即数右旋 | ✓ | ✓ | OP-IMM，`funct7=0110000`，`funct3=101` |
| `rolw`/`rorw` | 低 32 位旋转，结果符号扩展 | — | ✓ | OP-32，`funct7=0110000`，`funct3=001/101` |
| `roriw` | 低 32 位立即数右旋，结果符号扩展 | — | ✓ | OP-IMM-32，`funct7=0110000`，`funct3=101` |
| `rev8` | 按字节反转整个 `XLEN` 值 | ✓ | ✓ | OP-IMM，RV32 `funct7=0110100` / RV64 `0110101`，`funct3=101` |

逐条语义与 RV32/RV64 边界见 [3-zbb.md](3-zbb.md) 的对应小节；上图只用于确认 `Zbkb` 的成员关系。

### 3.2 `Zbkb` 独有指令

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `pack rd, rs1, rs2` | `rd = {rs2[XLEN/2-1:0], rs1[XLEN/2-1:0]}`，高位零扩展 | ✓ | ✓ | OP，`funct7=0000100`，`funct3=100` |
| `packh rd, rs1, rs2` | `rd = {rs2[7:0], rs1[7:0]}`，其余位清零 | ✓ | ✓ | OP，`funct7=0000100`，`funct3=111` |
| `packw rd, rs1, rs2` | 低 16 位拼接成 32 位，符号扩展到 `XLEN` | — | ✓ | OP-32，`funct7=0000100`，`funct3=100` |
| `brev8 rd, rs1` | 每个字节内位序反转 | ✓ | ✓ | OP-IMM，`funct7=0110100`，`rs2=00111`，`funct3=101` |
| `zip rd, rs1` | 奇偶位分散到高低半区 | ✓ | — | OP-IMM，`funct7=0000100`，`rs2=01111`，`funct3=001` |
| `unzip rd, rs1` | 高低半区收集为奇偶位 | ✓ | — | OP-IMM，`funct7=0000100`，`rs2=01111`，`funct3=101` |

### 3.3 语义要点

1. **`pack`**：把 `rs1` 的低 `XLEN/2` 位放在 `rd` 低半区，`rs2` 的低 `XLEN/2` 位放在高半区，结果零扩展。RV32 下是 16+16 位，RV64 下是 32+32 位；不要把它当成固定 16 位操作。
2. **`packh`**：只取两个源的最低字节，拼到 `rd[15:0]`，其余位清零；与 `XLEN` 无关。
3. **`packw`**：仅 RV64。把 `rs1[15:0]` 和 `rs2[15:0]` 拼成 32 位，再把 bit 31 **符号扩展** 到 64 位，适合把两个半字打包成有符号字。
4. **`brev8`**：对每个字节独立反转 8 个 bit，不改变字节顺序；与 `rev8`（反转字节顺序）不同。
5. **`zip`/`unzip`**：仅 RV32。`zip` 把源字的高半区和低半区交替展开；`unzip` 是其逆操作，常用于位切片（bit-slicing）实现。

### 3.4 编码速查

| 指令 | opcode | `funct7`/`funct6` | `rs2`/`shamt` | `funct3` |
| --- | --- | --- | --- | --- |
| `pack` | `0110011` | `0000100` | `rs2` | `100` |
| `packh` | `0110011` | `0000100` | `rs2` | `111` |
| `packw` | `0111011` | `0000100` | `rs2` | `100` |
| `brev8` | `0010011` | `0110100` | `00111` | `101` |
| `zip` | `0010011` | `0000100` | `01111` | `001` |
| `unzip` | `0010011` | `0000100` | `01111` | `101` |

## 4. 使用与边界

1. **打包宽度随 `XLEN` 变化**：`pack` 在 RV32/RV64 下拼接宽度不同，移植代码时不要假设固定 16 位；需要固定 16 位时用 `packh` 或 `packw`。
2. **符号扩展**：`packw` 的结果按 bit 31 符号扩展；如果希望零扩展，应先确认高位为 0 或改用其他组合。
3. **RV32-only 的 `zip`/`unzip`**：RV64 代码不能使用；需要类似效果时用移位和掩码组合。
4. **`brev8` 与 `rev8`**：前者反转字节内位序，后者反转字节顺序；密码代码常同时使用。
5. **常数时间**：指令存在不等于实现常数时间；侧信道结论必须结合具体 RTL 与测量（UNVERIFIED）。

## 5. 香山实现核对清单

课程中的香山构建示例启用了 `zbkb`（见 [prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md)），但这是软件配置。审查具体 RTL/commit 时建议确认（UNVERIFIED）：

- [ ] 译码器是否把 `Zbkb` 共享指令与 `Zbb` 正确合并，避免重复或漏译；
- [ ] `pack` 的拼接宽度是否随 `XLEN` 参数化；
- [ ] `packw` 是否只对低 32 位结果做符号扩展；
- [ ] `packh` 是否正确清零高 `XLEN-16` 位；
- [ ] `brev8` 是否按字节内反转，`rev8` 是否按字节顺序反转；
- [ ] `zip`/`unzip` 是否只在 RV32 配置下译码；
- [ ] 扩展未启用时对应编码是否按非法指令处理。

## 6. 参考

- [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html)
- [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
- 同目录：[1-rvb.md](1-rvb.md)、[3-zbb.md](3-zbb.md)、[7-zbkc.md](7-zbkc.md)、[8-zbkx.md](8-zbkx.md)
