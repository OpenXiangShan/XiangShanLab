# RISC-V Zbkx 标准扩展：跨栏置换指令解析

> 规范基线：[RISC-V Cryptography Extensions 1.0.1（Scalar & Entropy Source）](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html)。`Zbkx`（Crossbar permutations）1.0.0 于 2021-12 批准，1.0.1 于 2022-02 批准。
>
> 本文只讨论 `Zbkx`；B 系列分组见 [1-rvb.md](1-rvb.md)。

## 1. 定位与结论

`Zbkx` 提供两条“寄存器内查表”指令：`xperm4` 以 4 位为元素，`xperm8` 以 8 位为元素。它把 `rs1` 当作查找表、`rs2` 当作索引向量，按索引从 `rs1` 中选出元素填入 `rd`。索引越界时结果为零。这条指令常用于实现 SBox 等 N 位到 N 位的布尔函数，使执行路径不依赖秘密数据的访存地址。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 元素宽度 | `xperm4` 为 4 位，`xperm8` 为 8 位 | 两条指令都是字节操作 | T1-VERIFIED |
| 查表方向 | `rs1` 是数据表，`rs2` 是索引 | `rs1` 是索引，`rs2` 是表 | T1-VERIFIED |
| 越界行为 | 索引选不到 `rs1` 内元素时，对应输出元素为 0 | 越界索引回绕，或触发异常 | T1-VERIFIED |
| RV32/RV64 | 两条指令都可用；可用元素个数随 `XLEN` 变化 | 仅 RV64 | T1-VERIFIED |
| 常数时间 | ISA 不保证延迟与数据无关 | 使用 `Zbkx` 就自动抗侧信道 | UNVERIFIED |

## 2. 规范来源与范围

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html) | `xperm4`/`xperm8` 语义、索引规则和密码用途 | T1-VERIFIED |
| [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 编码和操作语义交叉检查 | T2-CROSS-CHECKED |

范围边界：本文只讨论两条 `xperm*` 指令；`Zbkb`、`Zbkc` 见 [6-zbkb.md](6-zbkb.md)、[7-zbkc.md](7-zbkc.md)。SBox 的具体实现和侧信道评估不在本文范围（UNVERIFIED）。

## 3. 指令集合

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `xperm4 rd, rs1, rs2` | 以 4 位元素查表 | ✓ | ✓ | OP，`funct7=0010100`，`funct3=010` |
| `xperm8 rd, rs1, rs2` | 以 8 位元素查表 | ✓ | ✓ | OP，`funct7=0010100`，`funct3=100` |

`OP=0110011`。

### 3.1 语义

把 `rd`、`rs1`、`rs2` 都划分为 `XLEN/4`（`xperm4`）或 `XLEN/8`（`xperm8`）个元素。对每个输出位置 `i`：

1. 从 `rs2` 的第 `i` 个元素取出索引 `index`；
2. 如果 `index` 落在 `rs1` 的元素范围内，把 `rs1` 的第 `index` 个元素复制到 `rd` 的第 `i` 个元素；
3. 否则把 `rd` 的第 `i` 个元素写 0。

元素范围分别是：`xperm4` 要求 `4*index < XLEN`，`xperm8` 要求 `8*index < XLEN`。

| 指令 | RV32 可用元素 | RV64 可用元素 | 越界示例 |
| --- | --- | --- | --- |
| `xperm4` | 8 个 nibble（索引 0–7） | 16 个 nibble（索引 0–15） | RV32 索引 8–15 → 0 |
| `xperm8` | 4 个字节（索引 0–3） | 8 个字节（索引 0–7） | RV32 索引 4–255 → 0 |

### 3.2 编码速查

| 指令 | opcode | `funct7` | `funct3` |
| --- | --- | --- | --- |
| `xperm4` | `0110011` | `0010100` | `010` |
| `xperm8` | `0110011` | `0010100` | `100` |

## 4. 使用与边界

1. **索引是元素编号，不是位偏移**：`xperm4` 的索引 1 选第 2 个 nibble（bits `[7:4]`），不是 bit 1。
2. **越界清零**：不要假设索引会对元素个数取模；超出范围的结果是 0，这会影响 SBox 的填充逻辑。
3. **RV32/RV64 元素数不同**：同一段代码在 RV32 与 RV64 下的可用查表项数不同，移植时要重新检查索引范围。
4. **常数时间**：寄存器内查表避免了数据相关访存，但是否常数时间仍取决于 RTL 实现；需要实测或审查电路（UNVERIFIED）。
5. **`x0`**：写 `x0` 丢弃结果；读 `x0` 得到 0。

## 5. 香山实现核对清单

课程中的香山构建示例启用了 `zbkx`（见 [prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md)），但这是软件配置。审查具体 RTL/commit 时建议确认（UNVERIFIED）：

- [ ] 译码器是否按 `funct3=010/100` 区分 `xperm4`/`xperm8`；
- [ ] 索引是否按 4 位/8 位元素正确切分，而不是按位偏移；
- [ ] 越界索引是否输出 0，而不是回绕或触发异常；
- [ ] RV32 与 RV64 的元素个数是否随 `XLEN` 参数化；
- [ ] 实现是否为寄存器内查表，避免引入数据相关访存；
- [ ] 扩展未启用时对应编码是否按非法指令处理。

## 6. 参考

- [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
- 同目录：[1-rvb.md](1-rvb.md)、[6-zbkb.md](6-zbkb.md)、[7-zbkc.md](7-zbkc.md)
