# RISC-V Zbb 标准扩展：基础位操作指令解析

> 规范基线：[RISC-V Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)，`Zbb`（Basic bit-manipulation）于 2021-06 批准。
>
> 本文只讨论 `Zbb`；地址生成见 [2-zba.md](2-zba.md)，B 系列分组见 [1-rvb.md](1-rvb.md)。

## 1. 定位与结论

`Zbb` 是基础位操作扩展，覆盖逻辑组合、前导/尾随零计数、popcount、最值、符号扩展、旋转和字节重排。它不增加新的寄存器文件或 CSR，所有结果写回整数通用寄存器，按 `XLEN` 截断。RV64 另有 `*w` 形式，只对低 32 位运算，再把 32 位结果符号扩展到 64 位。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 指令数量 | 24 条（含 RV64 的 6 条 `*w` 形式） | `Zbb` 只有 `andn`/`orn`/`xnor` | T1-VERIFIED |
| 零输入的计数结果 | `clz(0)=XLEN`，`ctz(0)=XLEN`，`cpop(0)=0` | 零输入的结果是 0 或未定义 | T1-VERIFIED |
| 最值比较 | `min`/`max` 按有符号比较，`minu`/`maxu` 按无符号比较 | 四个助记符都按无符号比较 | T1-VERIFIED |
| 旋转的移位量 | `rs2` 只取低 5 位（RV32）或低 6 位（RV64），即对 `XLEN` 取模 | 旋转量超出 `XLEN` 会产生异常 | T1-VERIFIED |
| `*w` 形式 | 只对低 32 位运算，结果符号扩展到 `XLEN`；仅 RV64 | `*w` 在 RV32 也可用，或结果零扩展 | T1-VERIFIED |
| `zext.h` | 零扩展低 16 位；RV32/RV64 编码不同，实现 `Zbkb` 时是 `pack`/`packw` 的别名 | `zext.h` 是单一固定编码 | T1-VERIFIED |

## 2. 规范来源、版本与证据标签

### 2.1 版本锚点

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html) | `Zbb` 指令语义、编码和 RV32/RV64 边界的主依据 | T1-VERIFIED |
| [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 交叉核对助记符和 opcode 分类 | T1-VERIFIED |
| [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 机器可读指令/编码数据交叉检查 | T2-CROSS-CHECKED |
| [RVA23 Profile 1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html) | 区分扩展本身与 profile 强制项 | T1-VERIFIED |

标签约定：**T1-VERIFIED** 可在 ratified 规范正文定位；**T2-CROSS-CHECKED** 为官方补充资料交叉支持；**INTERPRETIVE** 为教学归纳；**UNVERIFIED** 表示未对具体实现或工具链实测。

### 2.2 范围边界

| 范围 | 本文处理方式 | 标签 |
| --- | --- | --- |
| `Zbb` 指令语义 | 完整覆盖 24 条指令 | T1-VERIFIED |
| `Zba`/`Zbs`/`Zbc` | 不展开 | T1-VERIFIED |
| 密码位操作 `Zbkb`/`Zbkc`/`Zbkx` | 只在 `zext.h` 别名处说明与 `Zbkb` 的关系 | T1-VERIFIED |
| 执行延迟、端口、旁路 | 微架构问题，不给出数值 | UNVERIFIED |
| 编译器生成策略 | 取决于优化级别、`-march` 与代码形状 | UNVERIFIED |

## 3. 指令集合

### 3.1 逻辑组合

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `andn rd, rs1, rs2` | `rd = rs1 & ~rs2` | ✓ | ✓ | OP，`funct7=0100000`，`funct3=111` |
| `orn rd, rs1, rs2` | `rd = rs1 \| ~rs2` | ✓ | ✓ | OP，`funct7=0100000`，`funct3=110` |
| `xnor rd, rs1, rs2` | `rd = ~(rs1 ^ rs2)` | ✓ | ✓ | OP，`funct7=0100000`，`funct3=100` |

注意操作数方向：`andn`/`orn` 取反的是 **`rs2`**，不是 `rs1`。

### 3.2 计数与最值

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `clz rd, rs1` | 前导零个数，`clz(0)=XLEN` | ✓ | ✓ | OP-IMM，`funct7=0110000`，`rs2=00000`，`funct3=001` |
| `ctz rd, rs1` | 尾随零个数，`ctz(0)=XLEN` | ✓ | ✓ | OP-IMM，`funct7=0110000`，`rs2=00001`，`funct3=001` |
| `cpop rd, rs1` | 1 的个数（popcount） | ✓ | ✓ | OP-IMM，`funct7=0110000`，`rs2=00010`，`funct3=001` |
| `clzw rd, rs1` | 低 32 位前导零个数，`clzw(0)=32` | — | ✓ | OP-32，`funct7=0110000`，`rs2=00000`，`funct3=001` |
| `ctzw rd, rs1` | 低 32 位尾随零个数，`ctzw(0)=32` | — | ✓ | OP-32，`funct7=0110000`，`rs2=00001`，`funct3=001` |
| `cpopw rd, rs1` | 低 32 位 popcount，结果 0–32 | — | ✓ | OP-32，`funct7=0110000`，`rs2=00010`，`funct3=001` |
| `min rd, rs1, rs2` | 有符号最小值 | ✓ | ✓ | OP，`funct7=0000101`，`funct3=100` |
| `minu rd, rs1, rs2` | 无符号最小值 | ✓ | ✓ | OP，`funct7=0000101`，`funct3=101` |
| `max rd, rs1, rs2` | 有符号最大值 | ✓ | ✓ | OP，`funct7=0000101`，`funct3=110` |
| `maxu rd, rs1, rs2` | 无符号最大值 | ✓ | ✓ | OP，`funct7=0000101`，`funct3=111` |

`clzw`/`ctzw`/`cpopw` 的结果都在 0–32 之间，写成 `XLEN` 位非负整数；不要因为编码属于 OP-32 就误以为结果会被符号扩展成很大的负数。

### 3.3 符号扩展、零扩展与字节重排

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `sext.b rd, rs1` | 低 8 位符号扩展到 `XLEN` | ✓ | ✓ | OP-IMM，`funct7=0110000`，`rs2=00100`，`funct3=001` |
| `sext.h rd, rs1` | 低 16 位符号扩展到 `XLEN` | ✓ | ✓ | OP-IMM，`funct7=0110000`，`rs2=00101`，`funct3=001` |
| `zext.h rd, rs1` | 低 16 位零扩展到 `XLEN` | ✓ | ✓ | RV32：OP `funct7=0000100`/`funct3=100`；RV64：OP-32 `funct7=0000100`/`funct3=100` |
| `orc.b rd, rs1` | 每个字节：非零 → `0xFF`，零 → `0x00` | ✓ | ✓ | OP-IMM，`funct7=0010100`，`rs2=00111`，`funct3=101` |
| `rev8 rd, rs1` | 按字节反转整个 `XLEN` 值 | ✓ | ✓ | OP-IMM，RV32 `funct7=0110100`；RV64 `funct7=0110101`；`rs2=11000`，`funct3=101` |

`zext.h` 在实现 `Zbkb` 时分别等价于 RV32 的 `pack` 和 RV64 的 `packw`；这是别名关系，不改变零扩展语义。

### 3.4 旋转

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `rol rd, rs1, rs2` | 左旋，移位量 `rs2 mod XLEN` | ✓ | ✓ | OP，`funct7=0110000`，`funct3=001` |
| `ror rd, rs1, rs2` | 右旋，移位量 `rs2 mod XLEN` | ✓ | ✓ | OP，`funct7=0110000`，`funct3=101` |
| `rori rd, rs1, shamt` | 立即数右旋 | ✓ | ✓ | OP-IMM，`funct7=0110000`，`funct3=101`；RV32 5 位、RV64 6 位立即数 |
| `rolw rd, rs1, rs2` | 低 32 位左旋，结果符号扩展 | — | ✓ | OP-32，`funct7=0110000`，`funct3=001` |
| `rorw rd, rs1, rs2` | 低 32 位右旋，结果符号扩展 | — | ✓ | OP-32，`funct7=0110000`，`funct3=101` |
| `roriw rd, rs1, shamt` | 低 32 位立即数右旋，结果符号扩展 | — | ✓ | OP-IMM-32，`funct7=0110000`，`funct3=101` |

旋转量只取 `rs2` 的低 5 位（RV32）或低 6 位（RV64），等价于对 `XLEN` 取模；`rori` 的立即数位数也随 `XLEN` 变化。`*w` 形式的输入是低 32 位，旋转后再把 bit 31 符号扩展到 `XLEN`。

### 3.5 编码字段速查

| 指令组 | opcode | `funct7`/`funct6` | `rs2`/`shamt` | `funct3` |
| --- | --- | --- | --- | --- |
| `andn`/`orn`/`xnor` | `0110011` | `0100000` | `rs2` | `111`/`110`/`100` |
| `clz`/`ctz`/`cpop`/`sext.b`/`sext.h` | `0010011` | `0110000` | `00000`/`00001`/`00010`/`00100`/`00101` | `001` |
| `clzw`/`ctzw`/`cpopw` | `0011011` | `0110000` | `00000`/`00001`/`00010` | `001` |
| `min`/`minu`/`max`/`maxu` | `0110011` | `0000101` | `rs2` | `100`/`101`/`110`/`111` |
| `zext.h` | RV32 `0110011` / RV64 `0111011` | `0000100` | `00000` | `100` |
| `rol`/`ror` | `0110011` | `0110000` | `rs2` | `001`/`101` |
| `rori` | `0010011` | `0110000` | `shamt` | `101` |
| `rolw`/`rorw` | `0111011` | `0110000` | `rs2` | `001`/`101` |
| `roriw` | `0011011` | `0110000` | `shamt` | `101` |
| `orc.b` | `0010011` | `0010100` | `00111` | `101` |
| `rev8` | `0010011` | RV32 `0110100` / RV64 `0110101` | `11000` | `101` |

## 4. 使用与边界

1. **计数结果的宽度**：`clz`/`ctz`/`cpop` 的结果最大为 `XLEN`，用 `XLEN` 位无符号整数表示；`clzw`/`ctzw`/`cpopw` 最大为 32。
2. **有符号与无符号最值**：`min`/`max` 使用二进制补码有符号比较；`minu`/`maxu` 使用无符号比较。两者对负数结果完全不同。
3. **`zext.h` 的别名**：实现 `Zbkb` 时，RV32 的 `zext.h` 可由 `pack` 提供，RV64 可由 `packw` 提供；审查译码时不要把别名编码当成独立指令漏掉。
4. **`rev8` 的字节序**：RV32 反转 4 个字节，RV64 反转 8 个字节；不能把 RV32 的编码直接套到 RV64。
5. **`x0`**：写 `x0` 丢弃结果；读 `x0` 得到 0。`andn`/`orn`/`xnor` 的操作数方向不要写反。

## 5. 香山实现核对清单

课程中的香山应用构建示例启用了 `zbb`（见 [prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md) 的 `MARCH`），但这是软件编译配置。审查具体 RTL/commit 时建议逐项确认（UNVERIFIED）：

- [ ] 译码器是否覆盖 `funct7=0100000/0110000/0000101/0010100/0110100/0110101` 的全部组合；
- [ ] `clz`/`ctz` 的零输入是否分别返回 `XLEN`，`clzw`/`ctzw` 是否返回 32；
- [ ] `min`/`max` 与 `minu`/`maxu` 是否使用正确的有符号/无符号比较；
- [ ] `*w` 形式是否只对低 32 位运算并按 bit 31 符号扩展；
- [ ] 旋转量是否正确取模，`rori` 立即数宽度是否随 `XLEN` 变化；
- [ ] `zext.h` 在 RV32/RV64 下是否使用不同 opcode，实现 `Zbkb` 时是否正确走别名；
- [ ] `rev8` 在 RV32 与 RV64 下的字节序和编码是否分别验证；
- [ ] `orc.b` 是否按字节处理，而不是按位或整体值。

## 6. 参考

- [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)
- [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
- [RVA23 Profile 1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html)
- 同目录：[1-rvb.md](1-rvb.md)、[2-zba.md](2-zba.md)
