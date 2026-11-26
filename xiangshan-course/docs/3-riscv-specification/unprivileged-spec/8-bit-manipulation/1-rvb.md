# RISC-V B 扩展集合：位操作扩展的分组、版本与依赖

> 规范基线：[RISC-V Bit-Manipulation ISA-extensions, Version 1.0](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)。其中 `Zba`、`Zbb`、`Zbs`、`Zbc` 于 2021-06 批准；`Zbkb`、`Zbkc`、`Zbkx` 属于标量密码扩展，1.0.0 于 2021-12 批准、1.0.1 于 2022-02 批准。
>
> 本文是 **B 系列的分组索引**，不重复逐条指令定义；`Zba`、`Zbb` 的指令表见同目录 [2-zba.md](2-zba.md)、[3-zbb.md](3-zbb.md)。

## 1. 定位与结论

`B` 在今天不是一条可以直接写进 ratified 扩展清单的单一扩展。最初的 B 草案在批准前被拆分，最终以 `Zba`、`Zbb`、`Zbs`、`Zbc` 等 `Zb*` 子扩展发布。工具链仍接受字母 `B`，但它只是 **`Zba + Zbb + Zbs` 的简写**，不能据此推断 `Zbc`、`Zbkb`、`Zbkc`、`Zbkx` 也存在。

| 问题 | 准确结论 | 常见误读 | 标签 |
| --- | --- | --- | --- |
| `B` 是什么 | 工具链对 `Zba + Zbb + Zbs` 的简写；规范正文按子扩展定义 | `B` 是 ratified 的单一扩展，包含全部位操作 | T1-VERIFIED |
| 子扩展能否单独实现 | 能；每个 `Zb*` 有独立名称和版本，按需声明 | 实现 `Zbb` 必须同时实现 `Zba` | T1-VERIFIED |
| `Zbc` 是否属于 `B` | `Zbc` 是独立的无进位乘法扩展，不属于 `B` 的简写展开 | `B` 自动包含 `clmul`/`clmulh`/`clmulr` | T1-VERIFIED |
| `Zbkb`/`Zbkc`/`Zbkx` 是否属于 `B` | 属于标量密码扩展族，随 Scalar Cryptography 批准，不是基础 `B` | 带 `Zbk*` 前缀就等同于基础位操作 | T1-VERIFIED |
| 是否需要额外状态 | 这些扩展只增加指令，不增加 CSR、特权级状态或异常类型 | 启用 `Zb*` 会改变 trap 或地址转换行为 | T1-VERIFIED |

## 2. 子扩展清单

| 扩展 | 名称 | 关键能力 | 批准时间 | 本目录页面 |
| --- | --- | --- | --- | --- |
| `Zba` | Address generation | `sh1add`/`sh2add`/`sh3add` 及 RV64 的 `.uw` 形式，用于数组索引地址生成 | 2021-06 | [2-zba.md](2-zba.md) |
| `Zbb` | Basic bit-manipulation | 逻辑非与、前导/尾随零计数、popcount、最值、符号扩展、旋转、`orc.b`、`rev8` | 2021-06 | [3-zbb.md](3-zbb.md) |
| `Zbc` | Carry-less multiplication | `clmul`、`clmulh`、`clmulr` 无进位乘法 | 2021-06 | [4-zbc.md](4-zbc.md) |
| `Zbs` | Single-bit instructions | 单比特置位、清零、取反、测试（`bset`/`bclr`/`binv`/`bext` 及立即数形式） | 2021-06 | [5-zbs.md](5-zbs.md) |
| `Zbkb` | Bit-manipulation for Cryptography | 密码算法常用的位操作与打包/解包指令 | 1.0.0：2021-12；1.0.1：2022-02 | [6-zbkb.md](6-zbkb.md) |
| `Zbkc` | Carry-less multiplication for Cryptography | 密码用途的无进位乘法子集 | 1.0.0：2021-12；1.0.1：2022-02 | [7-zbkc.md](7-zbkc.md) |
| `Zbkx` | Crossbar permutations | `xperm4`/`xperm8` 等跨栏置换 | 1.0.0：2021-12；1.0.1：2022-02 | [8-zbkx.md](8-zbkx.md) |

> 本目录 B 系列页面已全部补齐；各扩展的指令语义与 RV32/RV64 边界以对应页面为准。

## 3. 版本与范围边界

| 边界 | 说明 | 标签 |
| --- | --- | --- |
| 规范版本 | 基础位操作固定为 Bit-Manipulation 1.0；密码子集固定为 Scalar Cryptography 1.0.x | T1-VERIFIED |
| 与基础 ISA 的关系 | `Zb*` 依赖 `I`（以及 RV32/RV64 的 `XLEN`）；不依赖 `M`、`A`、`F`、`V` | T1-VERIFIED |
| 与 profile 的关系 | `RVA23`/`RVB23` 等 profile 会点名部分 `Zb*`；profile 强制项不等于扩展本身的能力边界 | T1-VERIFIED |
| 与工具链的关系 | `-march=...b` 或 `rv64gcb` 通常展开为 `zba_zbb_zbs`；具体展开以所用 GCC/LLVM 版本为准 | T2-CROSS-CHECKED |
| 与实现的关系 | 扩展是否在某个核中实现、以什么延迟实现，属于微架构问题，不由 ISA 规定 | UNVERIFIED |

## 4. 工具链中的 `B`

课程中的香山应用构建示例使用：

```make
MARCH = rv64gc_zba_zbb_zbc_zbs_zbkb_zbkc_zbkx_zknd_zkne_zknh_zkr_zksed_zksh_zkt_zicbop
```

出处：[prefetch_i.md](../../../5-xiangshan-scenarios-analysis/instructions-lifecycle/prefetch/prefetch_i.md)。该字符串说明构建环境同时启用了 `Zba`、`Zbb`、`Zbc`、`Zbs` 和密码位操作子集；它 **只证明软件编译配置**，不能单独证明某个香山 commit 的 RTL 已实现全部指令。

阅读 `-march` 时注意：

1. `b` 或 `gcb` 只展开 `Zba + Zbb + Zbs`，不会带上 `Zbc`、`Zbkb`、`Zbkc`、`Zbkx`；
2. 显式写 `_zbkb_zbkc_zbkx` 才表示密码位操作子集；
3. 汇编器接受某个助记符，只说明工具链认识该编码；非法指令是否 trap 取决于硬件译码。

## 5. 香山实现核对清单

以下项目用于审查具体 RTL/commit，本文不给出“已实现”结论（UNVERIFIED）：

- [ ] 译码器是否识别各子扩展的 opcode/funct3/funct7 组合，RV32 与 RV64 分支是否分开；
- [ ] 扩展未启用时，对应编码是否按非法指令处理，而不是被错误译码成基础指令；
- [ ] ALU 是否覆盖移位加、前导/尾随零计数、popcount、最值、旋转和字节重排；
- [ ] RV64-only 指令（`*.uw`、`*w` 形式）在 RV32 配置下是否正确拒绝；
- [ ] `zext.h` 在实现 `Zbkb` 时是否按 `pack`/`packw` 别名处理；
- [ ] `rev8` 在 RV32 与 RV64 下的字节序和编码是否分别验证。

## 6. 参考

- [Bit-Manipulation ISA-extensions 1.0（B Standard Extension）](https://docs.riscv.org/reference/isa/v20260120/unpriv/b-st-ext.html)
- [RISC-V Ratified Specifications Library](https://docs.riscv.org/reference/home/index.html)
- [RISC-V UnifiedDB deployment](https://riscv.github.io/riscv-unified-db/)（机器可读扩展/指令数据，用于交叉检查，不替代 ratified 正文）
- [RVA23 Profile 1.0](https://docs.riscv.org/reference/rva23/rva23-profiles.html)
- 同目录：[2-zba.md](2-zba.md)、[3-zbb.md](3-zbb.md)
