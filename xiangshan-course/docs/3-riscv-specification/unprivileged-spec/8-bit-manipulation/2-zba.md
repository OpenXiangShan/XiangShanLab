# RISC-V Zba 标准扩展：地址生成指令解析

> 规范基线：[RISC-V Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)，`Zba`（Address generation）于 2021-06 批准。
>
> 本文只讨论 `Zba`；`Zbb`、`Zbs`、`Zbc` 与密码位操作子集见同目录 [1-rvb.md](1-rvb.md)、[3-zbb.md](3-zbb.md)。

## 1. 定位与结论

`Zba` 是一组 **地址生成** 指令：把一个索引左移 1/2/3 位后加到基地址上，正好对应按 2/4/8 字节步长索引数组。它不增加 CSR、异常或新的数据类型，只复用整数寄存器和 `XLEN` 位加法器。RV64 额外提供 `.uw` 形式，把 32 位无符号索引零扩展后再参与 64 位运算。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| 指令数量 | 共 8 条：`sh1add`/`sh2add`/`sh3add`，RV64 的 `sh1add.uw`/`sh2add.uw`/`sh3add.uw`、`add.uw`、`slli.uw` | `Zba` 还包含任意移位量或字节级置换 | T1-VERIFIED |
| 移位方向 | 对 **rs1** 左移 1/2/3 位，`rs2` 作为加数 | 对 `rs2` 左移，或移位量来自寄存器 | T1-VERIFIED |
| `.uw` 的含义 | 取 `rs1` 低 32 位并零扩展到 `XLEN`，再移位/相加 | 把整个 64 位 `rs1` 截断后做符号扩展 | T1-VERIFIED |
| 溢出行为 | 加法按 `XLEN` 模运算回绕，不产生异常 | 地址溢出会触发 access fault 或算术异常 | T1-VERIFIED |
| RV32 边界 | RV32 只有 `sh1add`/`sh2add`/`sh3add`；`.uw` 形式仅 RV64 | `.uw` 指令在 RV32 上也可用 | T1-VERIFIED |

## 2. 规范来源、版本与证据标签

### 2.1 版本锚点

| 来源 | 用途 | 标签 |
| --- | --- | --- |
| [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html) | `Zba` 指令语义、编码和 RV32/RV64 边界的主依据 | T1-VERIFIED |
| [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html) | 交叉核对助记符与 opcode 分类 | T1-VERIFIED |
| [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/) | 用机器可读的扩展、指令和编码数据交叉检查 | T2-CROSS-CHECKED |
| [RVA23 Profile 1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html) | 区分扩展本身与 profile 强制项 | T1-VERIFIED |

标签约定：

- **T1-VERIFIED**：可在 RISC-V ratified 规范或 profile 正文中直接定位；
- **T2-CROSS-CHECKED**：官方补充资料交叉支持，但不替代 T1 正文；
- **INTERPRETIVE**：由规范事实整理的教学归纳；
- **UNVERIFIED**：未对具体硬件、工具链或平台实测。

### 2.2 范围边界

| 范围 | 本文处理方式 | 标签 |
| --- | --- | --- |
| `Zba` 指令语义 | 完整覆盖 8 条指令 | T1-VERIFIED |
| `Zbb`/`Zbs`/`Zbc` | 不展开，仅说明同属 B 系列 | T1-VERIFIED |
| 地址翻译、PMP/PMA | 不属于 `Zba` 语义；地址计算结果之后仍按正常访存规则检查 | T1-VERIFIED |
| 延迟、执行单元、旁路 | 微架构问题，不在本文给出数值 | UNVERIFIED |
| 编译器是否生成这些指令 | 取决于优化级别、目标 `-march` 和代码形状 | UNVERIFIED |

## 3. 指令集合

### 3.1 汇总表

| 指令 | 语义 | RV32 | RV64 | 编码 |
| --- | --- | --- | --- | --- |
| `sh1add rd, rs1, rs2` | `rd = rs2 + (rs1 << 1)` | ✓ | ✓ | OP，`funct7=0010000`，`funct3=010` |
| `sh2add rd, rs1, rs2` | `rd = rs2 + (rs1 << 2)` | ✓ | ✓ | OP，`funct7=0010000`，`funct3=100` |
| `sh3add rd, rs1, rs2` | `rd = rs2 + (rs1 << 3)` | ✓ | ✓ | OP，`funct7=0010000`，`funct3=110` |
| `sh1add.uw rd, rs1, rs2` | `rd = rs2 + (zext(rs1[31:0]) << 1)` | — | ✓ | OP-32，`funct7=0010000`，`funct3=010` |
| `sh2add.uw rd, rs1, rs2` | `rd = rs2 + (zext(rs1[31:0]) << 2)` | — | ✓ | OP-32，`funct7=0010000`，`funct3=100` |
| `sh3add.uw rd, rs1, rs2` | `rd = rs2 + (zext(rs1[31:0]) << 3)` | — | ✓ | OP-32，`funct7=0010000`，`funct3=110` |
| `add.uw rd, rs1, rs2` | `rd = rs2 + zext(rs1[31:0])` | — | ✓ | OP-32，`funct7=0000100`，`funct3=000` |
| `slli.uw rd, rs1, shamt` | `rd = zext(rs1[31:0]) << shamt` | — | ✓ | OP-IMM-32，`funct6=000010`，`funct3=001` |

编码列中的 `OP`、`OP-32`、`OP-IMM-32` 是 RISC-V 标准 opcode 分类：`OP=0110011`、`OP-32=0111011`、`OP-IMM-32=0011011`。所有指令写 `x0` 时结果丢弃，读 `x0` 得到 0。

### 3.2 逐条语义

**`sh1add`/`sh2add`/`sh3add`。** 三条指令只差左移量。`rs1` 是索引，`rs2` 是基地址；结果按 `XLEN` 位模运算。移位量固定为 1/2/3，是规范刻意限制的结果：更大的步长用基础 `slli` 加普通 `add` 组合即可，硬件不必为任意移位量加宽加法器关键路径。

**`sh1add.uw`/`sh2add.uw`/`sh3add.uw`。** 先取 `rs1[31:0]` 并零扩展到 `XLEN`，再左移并加上完整宽度的 `rs2`。它们适合“32 位无符号索引 + 64 位基地址”的数组访问；如果索引在 32 位内为负，零扩展会把它解释成一个很大的正数，这是无符号语义，不是 bug。

**`add.uw`。** 相当于 `sh0add.uw`：不移动索引，只做零扩展后相加。`rs2 = x0` 时等价于 `zext.w rd, rs1`，这是规范给出的伪指令别名。

**`slli.uw`。** 先把 `rs1[31:0]` 零扩展，再左移 `shamt`。`shamt` 取 0–31；6 位立即数中超出该范围的值属于保留编码，不应被实现为任意 64 位移位。

### 3.3 编码字段速查

| 指令组 | opcode | `funct7`/`funct6` | `funct3` | 备注 |
| --- | --- | --- | --- | --- |
| `sh1add`/`sh2add`/`sh3add` | `0110011` | `0010000` | `010`/`100`/`110` | R 型，`rs2` 为加数 |
| `sh1add.uw`/`sh2add.uw`/`sh3add.uw` | `0111011` | `0010000` | `010`/`100`/`110` | 仅 RV64 |
| `add.uw` | `0111011` | `0000100` | `000` | 仅 RV64；`rs2=x0` 时别名 `zext.w` |
| `slli.uw` | `0011011` | `000010` | `001` | 仅 RV64；`shamt=0..31` |

## 4. 使用与边界

1. **索引元素宽度**：`sh1add`/`sh2add`/`sh3add` 分别对应 2/4/8 字节元素；更宽元素用 `slli` + `add`。
2. **基地址与索引的角色**：`rs1` 总是被移位的一侧，`rs2` 总是加数；交换操作数会得到不同结果。
3. **无符号索引**：`.uw` 形式只取低 32 位并零扩展，适合 32 位无符号下标；不要把有符号 32 位索引直接喂给 `.uw`。
4. **回绕**：`XLEN` 位加法溢出按模回绕，不设置标志、不触发异常；是否越界由后续访存检查决定。
5. **`x0`**：写 `x0` 丢弃结果；`add.uw` 的 `rs2=x0` 是有意义的别名，不要当作空操作。

## 5. 香山实现核对清单

课程中的香山应用构建示例启用了 `zba`（见 [prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md) 的 `MARCH`），但这只是软件编译配置。审查具体 RTL/commit 时建议逐项确认（UNVERIFIED）：

- [ ] 译码器是否把 `OP`/`OP-32` 下的 `funct7=0010000`、`funct3=010/100/110` 正确映射到三条移位加指令；
- [ ] `add.uw` 的 `funct7=0000100`、`funct3=000` 是否独立译码，而不是误落到基础 `add`；
- [ ] `slli.uw` 是否只接受 0–31 的 `shamt`，保留编码是否按非法指令处理；
- [ ] RV32 配置下 `.uw` 编码是否被拒绝；
- [ ] 移位加是否复用现有加法器/移位器，结果是否按 `XLEN` 截断；
- [ ] `add.uw` 且 `rs2=x0` 时是否产生与 `zext.w` 一致的结果。

## 6. 参考

- [Bit-Manipulation ISA-extensions 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)
- [RV32/64G instruction listings](https://docs.riscv.org/reference/isa/v20260120/unpriv/rv-32-64g.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)
- [RVA23 Profile 1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html)
- 同目录：[1-rvb.md](1-rvb.md)、[3-zbb.md](3-zbb.md)
