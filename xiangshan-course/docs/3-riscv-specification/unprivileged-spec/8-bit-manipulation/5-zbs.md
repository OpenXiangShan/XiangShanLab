# RISC-V Zbs 标准扩展：单比特指令解析

> 规范基线：[RISC-V Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)，`Zbs`（Single-bit instructions）于 2021-06 批准。
>
> 本文只讨论 `Zbs`；B 系列分组见 [1-rvb.md](1-rvb.md)。

## 1. 定位与结论

`Zbs` 提供 8 条单比特操作：对寄存器中由索引选中的一位做置位、清零、取反或读出。索引可以来自寄存器 `rs2`，也可以来自立即数。它不增加 CSR，不产生算术异常，所有结果写回整数通用寄存器。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 指令数量 | 8 条：`bset`/`bclr`/`binv`/`bext` 及 `bseti`/`bclri`/`binvi`/`bexti` | 只有寄存器形式，没有立即数形式 | T1-VERIFIED |
| 索引范围 | 寄存器形式取 `rs2 & (XLEN-1)`；立即数形式取 `shamt & (XLEN-1)` | 索引越界会触发异常 | T1-VERIFIED |
| 索引回绕 | 索引对 `XLEN` 取模，例如 RV64 下索引 64 等价于 0 | 索引 64 访问“第 65 位” | T1-VERIFIED |
| `bext` 结果 | 返回 0 或 1，其余位为 0 | 返回被测试位所在的整个字节 | T1-VERIFIED |
| RV32/RV64 | 8 条指令都可用，立即数宽度随 `XLEN` 变化 | 立即数形式仅 RV64 | T1-VERIFIED |

## 2. 规范来源与范围

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html) | 指令语义、编码和索引规则 | T1-VERIFIED |
| [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 助记符与 opcode 分类交叉检查 | T1-VERIFIED |
| [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 编码字段交叉检查 | T2-CROSS-CHECKED |

范围边界：本文只讨论 `Zbs`；`Zba`、`Zbb`、`Zbc` 见相邻页面。执行延迟、位操作是否复用 ALU 属于微架构问题（UNVERIFIED）。

## 3. 指令集合

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `bset rd, rs1, rs2` | `rd = rs1 \| (1 << index)` | ✓ | ✓ | OP，`funct7=0010100`，`funct3=001` |
| `bclr rd, rs1, rs2` | `rd = rs1 & ~(1 << index)` | ✓ | ✓ | OP，`funct7=0100100`，`funct3=001` |
| `binv rd, rs1, rs2` | `rd = rs1 ^ (1 << index)` | ✓ | ✓ | OP，`funct7=0110100`，`funct3=001` |
| `bext rd, rs1, rs2` | `rd = (rs1 >> index) & 1` | ✓ | ✓ | OP，`funct7=0100100`，`funct3=101` |
| `bseti rd, rs1, shamt` | `rd = rs1 \| (1 << shamt)` | ✓ | ✓ | OP-IMM，`funct7=0010100`（RV32）/`funct6=001010`（RV64），`funct3=001` |
| `bclri rd, rs1, shamt` | `rd = rs1 & ~(1 << shamt)` | ✓ | ✓ | OP-IMM，`funct7=0100100`（RV32）/`funct6=010010`（RV64），`funct3=001` |
| `binvi rd, rs1, shamt` | `rd = rs1 ^ (1 << shamt)` | ✓ | ✓ | OP-IMM，`funct7=0110100`（RV32）/`funct6=011010`（RV64），`funct3=001` |
| `bexti rd, rs1, shamt` | `rd = (rs1 >> shamt) & 1` | ✓ | ✓ | OP-IMM，`funct7=0100100`（RV32）/`funct6=010010`（RV64），`funct3=101` |

其中 `index = rs2 & (XLEN-1)`。`OP=0110011`，`OP-IMM=0010011`。

### 3.1 语义要点

1. **置位/清零/取反**：`bset`、`bclr`、`binv` 分别对应 OR、AND NOT、XOR，只影响被索引的那一位。
2. **读出**：`bext` 把目标位右移到 bit 0，再与 1 相与；结果只可能是 0 或 1。
3. **索引回绕**：寄存器形式对 `rs2` 取低 `log2(XLEN)` 位；RV32 取低 5 位，RV64 取低 6 位。索引 32/64 分别回绕到 0。
4. **立即数宽度**：RV32 的 `shamt` 是 5 位，RV64 是 6 位；编码中 `funct7` 与 `funct6` 的差异正对应这一点。
5. **`x0`**：写 `x0` 丢弃结果；读 `x0` 得到 0。

### 3.2 编码速查

| 指令组 | opcode | 寄存器形式 | 立即数形式 | `funct3` |
| --- | --- | --- | --- | --- |
| `bset`/`bseti` | `0110011`/`0010011` | `funct7=0010100` | RV32 `funct7=0010100`；RV64 `funct6=001010` | `001` |
| `bclr`/`bclri` | `0110011`/`0010011` | `funct7=0100100` | RV32 `funct7=0100100`；RV64 `funct6=010010` | `001` |
| `binv`/`binvi` | `0110011`/`0010011` | `funct7=0110100` | RV32 `funct7=0110100`；RV64 `funct6=011010` | `001` |
| `bext`/`bexti` | `0110011`/`0010011` | `funct7=0100100` | RV32 `funct7=0100100`；RV64 `funct6=010010` | `101` |

## 4. 使用与边界

1. **位图与标志位**：`bset`/`bclr` 适合置位/清除位图；`binv` 适合翻转；`bext` 适合测试后分支。
2. **索引必须显式取模**：软件若传入等于 `XLEN` 的索引，硬件会回绕到 0，不会报错；需要越界检查时由软件完成。
3. **不要与 `Zbb` 的 `sext`/`zext` 混淆**：`Zbs` 只操作单比特，不做符号/零扩展。
4. **立即数形式与寄存器形式的编码不同**：审查译码时两组都要覆盖。

## 5. 香山实现核对清单

课程中的香山构建示例启用了 `zbs`（见 [prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md)），但这是软件配置。审查具体 RTL/commit 时建议确认（UNVERIFIED）：

- [ ] 译码器是否覆盖 8 条指令的 `funct7`/`funct6` 与 `funct3` 组合；
- [ ] 索引是否正确取 `rs2` 或 `shamt` 的低 5/6 位；
- [ ] `bset`/`bclr`/`binv` 的 OR/AND NOT/XOR 语义是否正确；
- [ ] `bext`/`bexti` 是否把结果零扩展为 0/1；
- [ ] RV32 与 RV64 的立即数宽度是否分开处理；
- [ ] 扩展未启用时对应编码是否按非法指令处理。

## 6. 参考

- [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)
- [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
- 同目录：[1-rvb.md](1-rvb.md)、[3-zbb.md](3-zbb.md)、[4-zbc.md](4-zbc.md)
