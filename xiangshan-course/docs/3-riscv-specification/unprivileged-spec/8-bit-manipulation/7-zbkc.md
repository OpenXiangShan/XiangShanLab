# RISC-V Zbkc 标准扩展：密码用无进位乘法解析

> 规范基线：[RISC-V Cryptography Extensions 1.0.1（Scalar & Entropy Source）](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html)。`Zbkc`（Carry-less multiplication for Cryptography）1.0.0 于 2021-12 批准，1.0.1 于 2022-02 批准。
>
> 本文只讨论 `Zbkc`；完整的 `Zbc`（含 `clmulr`）见 [4-zbc.md](4-zbc.md)，B 系列分组见 [1-rvb.md](1-rvb.md)。

## 1. 定位与结论

`Zbkc` 是 `Zbc` 的密码子集，只保留 GHASH（AES-GCM 认证部分）所需的 `clmul` 和 `clmulh`，不包含 `clmulr`。它与 `Zbc` 共享同样的 GF(2) 无进位乘法语义；区别只在指令集合，不在单条指令的行为。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 指令数量 | 2 条：`clmul`、`clmulh` | 还包含 `clmulr` | T1-VERIFIED |
| 与 `Zbc` 的关系 | `Zbc` 依赖 `Zbkc`，并额外提供 `clmulr` | 两者互斥，或 `Zbkc` 是 `Zbc` 的超集 | T1-VERIFIED |
| 乘法语义 | GF(2) 多项式乘法，列间不进位 | 普通整数乘法的高/低位 | T1-VERIFIED |
| 异常 | 不产生算术异常，结果按 `XLEN` 截断 | 溢出会触发 trap | T1-VERIFIED |
| RV32/RV64 | 两条指令都可用 | 仅 RV64 | T1-VERIFIED |

## 2. 规范来源与范围

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html) | `Zbkc` 指令集合、GHASH 用途和编码 | T1-VERIFIED |
| [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html) | `Zbc` 的完整集合与 `clmulr` 语义 | T1-VERIFIED |
| [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 编码交叉检查 | T2-CROSS-CHECKED |

范围边界：本文只讨论 `clmul`/`clmulh`；`Zbkb`、`Zbkx` 见 [6-zbkb.md](6-zbkb.md)、[8-zbkx.md](8-zbkx.md)。执行延迟和常数时间属性属于实现问题（UNVERIFIED）。

## 3. 指令集合

| 指令 | 语义 | 编码 |
| --- | --- | --- |
| `clmul rd, rs1, rs2` | GF(2) 乘积低 `XLEN` 位 | OP，`funct7=0000101`，`funct3=001` |
| `clmulh rd, rs1, rs2` | GF(2) 乘积高 `XLEN` 位 | OP，`funct7=0000101`，`funct3=011` |

`OP=0110011`。完整乘积需要两条指令组合：`clmul` 给低半，`clmulh` 给高半。

### 3.1 语义要点

- 把 `rs1`、`rs2` 看作 GF(2) 上的多项式系数；对 `rs2` 的每个为 1 的位 `i`，把 `rs1 << i` 异或进结果。
- `clmul` 取累加结果的低 `XLEN` 位；`clmulh` 取高 `XLEN` 位（乘积的 `[2*XLEN-1:XLEN]`）。
- 不需要 `clmulr` 时，`Zbkc` 足以实现 GHASH；需要 `[2*XLEN-2:XLEN-1]` 窗口时，必须使用 `Zbc`。

### 3.2 编码速查

| 指令 | opcode | `funct7` | `funct3` |
| --- | --- | --- | --- |
| `clmul` | `0110011` | `0000101` | `001` |
| `clmulh` | `0110011` | `0000101` | `011` |

## 4. 使用与边界

1. **`Zbkc` 与 `Zbc` 的选择**：只需要 GHASH 时用 `Zbkc` 即可；算法需要 `clmulr` 时必须声明 `Zbc`（它会带入 `Zbkc`）。
2. **高低半拼接**：`clmul` + `clmulh` 才能得到完整 `2*XLEN` 位乘积；只取一半会丢失信息。
3. **数据相关延迟**：ISA 不保证常数时间；侧信道结论需结合具体实现（UNVERIFIED）。
4. **`x0`**：写 `x0` 丢弃结果；读 `x0` 得到 0。

## 5. 香山实现核对清单

课程中的香山构建示例同时启用了 `zbc` 和 `zbkc`（见 [prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md)），但这是软件配置。审查具体 RTL/commit 时建议确认（UNVERIFIED）：

- [ ] `clmul`/`clmulh` 是否按 `funct3=001/011` 正确译码；
- [ ] 无进位乘法是否按 GF(2) 语义实现；
- [ ] 高低半窗口是否正确；
- [ ] `Zbc` 配置下 `clmulr` 是否可用，`Zbkc`-only 配置下是否正确拒绝 `clmulr`；
- [ ] RV32/RV64 的 `XLEN` 参数化是否正确。

## 6. 参考

- [Scalar Cryptography 1.0.1](https://docs.riscv.org/reference/isa/v20260120/unpriv/scalar-crypto.html)
- [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
- 同目录：[1-rvb.md](1-rvb.md)、[4-zbc.md](4-zbc.md)、[6-zbkb.md](6-zbkb.md)
