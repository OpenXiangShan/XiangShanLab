# RISC-V Zbc 标准扩展：无进位乘法指令解析

> 规范基线：[RISC-V Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)，`Zbc`（Carry-less multiplication）于 2021-06 批准。
>
> 本文讨论 `Zbc` 与它所依赖的 `Zbkc`；B 系列分组见 [1-rvb.md](1-rvb.md)。

## 1. 定位与结论

`Zbc` 提供 GF(2) 多项式环上的 **无进位乘法**（carry-less multiplication）。普通整数乘法在列间进位，`clmul` 系列把每一列直接异或，因此适合 CRC、GHASH、有限域乘法等密码与校验算法。`Zbc` 依赖 `Zbkc`：`Zbkc` 只含 `clmul`/`clmulh`，`Zbc` 在此基础上增加 `clmulr`。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 乘法语义 | GF(2) 上多项式乘法，列间不进位，按位异或累加 | 与普通整数乘法的低/高位相同 | T1-VERIFIED |
| 三条指令的区别 | `clmul` 取乘积低 `XLEN` 位，`clmulh` 取高 `XLEN` 位，`clmulr` 取 `[2*XLEN-2:XLEN-1]` | `clmulr` 就是把 `clmulh` 左移一位 | T1-VERIFIED |
| 依赖关系 | `Zbc` 需要 `Zbkc`；`Zbkc` 是只含 `clmul`/`clmulh` 的子集 | `Zbc` 与 `Zbkc` 是互斥的两个扩展 | T1-VERIFIED |
| 异常 | 不产生算术异常；结果按 `XLEN` 截断 | 溢出会设置标志或触发 trap | T1-VERIFIED |
| RV32/RV64 | 三条指令在 RV32、RV64 都可用 | `.uw` 或 `*w` 形式存在 | T1-VERIFIED |

## 2. 规范来源与范围

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html) | `Zbc`/`Zbkc` 语义、编码和依赖关系 | T1-VERIFIED |
| [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html) | `Zbkc` 在 GHASH 中的用途与密码指令集合 | T1-VERIFIED |
| [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 指令编码交叉检查 | T2-CROSS-CHECKED |

范围边界：本文只讨论 `clmul`/`clmulh`/`clmulr`；`Zbkb`、`Zbkx` 等密码位操作见 [6-zbkb.md](6-zbkb.md)、[8-zbkx.md](8-zbkx.md)。执行延迟和是否使用专用乘法器属于微架构问题（UNVERIFIED）。

## 3. 指令集合

| 指令 | 语义 | 所属 | 编码 |
| --- | --- | --- | --- |
| `clmul rd, rs1, rs2` | 乘积低 `XLEN` 位 | `Zbkc` | OP，`funct7=0000101`，`funct3=001` |
| `clmulh rd, rs1, rs2` | 乘积高 `XLEN` 位 | `Zbkc` | OP，`funct7=0000101`，`funct3=011` |
| `clmulr rd, rs1, rs2` | 乘积 `[2*XLEN-2:XLEN-1]` | `Zbc` | OP，`funct7=0000101`，`funct3=010` |

`OP=0110011`。三条指令都是 R 型，`rs1`/`rs2` 为源，写入 `rd`。

### 3.1 逐条语义

设 `A = rs1`、`B = rs2`，把两者看作 GF(2) 上的多项式系数。

- **`clmul`**：对 `B` 的每个为 1 的位 `i`，把 `A << i` 异或进结果，最后取低 `XLEN` 位。
- **`clmulh`**：同一累加过程取高 `XLEN` 位，即乘积的 `[2*XLEN-1:XLEN]`。
- **`clmulr`**：取乘积的 `[2*XLEN-2:XLEN-1]`，可以理解为把 `clmulh` 的窗口整体右移一位。

三者都按 `XLEN` 位无符号值解释，不涉及符号扩展。

### 3.2 编码速查

| 指令 | opcode | `funct7` | `funct3` |
| --- | --- | --- | --- |
| `clmul` | `0110011` | `0000101` | `001` |
| `clmulr` | `0110011` | `0000101` | `010` |
| `clmulh` | `0110011` | `0000101` | `011` |

## 4. 使用与边界

1. **`Zbc` 与 `Zbkc`**：实现 `Zbc` 必须同时提供 `clmul`/`clmulh`；只实现 `Zbkc` 时没有 `clmulr`。审查 ISA 字符串时不要只看 `zbc`。
2. **窗口选择**：需要完整乘积时，用 `clmul` + `clmulh` 拼出 `2*XLEN` 位；`clmulr` 用于需要 `[2*XLEN-2:XLEN-1]` 窗口的算法（例如某些 CRC 变体）。
3. **数据相关延迟**：ISA 不规定执行延迟。密码实现常要求常数时间，是否满足取决于具体硬件，不能从指令存在推出（UNVERIFIED）。
4. **`x0`**：写 `x0` 丢弃结果；读 `x0` 得到 0。

## 5. 香山实现核对清单

香山构建示例的 `MARCH` 含 `zbc`（见 [prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md)），但这是软件配置。审查具体 RTL/commit 时建议确认（UNVERIFIED）：

- [ ] 译码器是否按 `funct3=001/010/011` 区分 `clmul`/`clmulr`/`clmulh`；
- [ ] 无进位乘法是否按 GF(2) 语义实现，而不是复用普通乘法器；
- [ ] 三条指令的低/高/右移窗口是否正确，特别是 `clmulr` 的 `XLEN-1` 偏移；
- [ ] RV32 与 RV64 的 `XLEN` 参数化是否正确；
- [ ] 扩展未启用时对应编码是否按非法指令处理。

## 6. 参考

- [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)
- [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
- 同目录：[1-rvb.md](1-rvb.md)、[6-zbkb.md](6-zbkb.md)、[8-zbkx.md](8-zbkx.md)
