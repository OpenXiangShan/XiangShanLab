# RISC-V 指令 Latency 与 Throughput 模板

> RISC-V ISA 规定指令语义，但不规定固定性能数值。Latency 和 throughput 由具体微架构、运行条件和测量口径决定。本模板用于按扩展记录处理器实现或微基准结果。

## 1. 测试对象

| 字段 | 值 |
| --- | --- |
| 处理器/核名称 | TBD |
| 微架构版本/提交号 | TBD |
| ISA 字符串 | 例如：`rv64imafdcv_zba_zbb_zbs_zicond` |
| XLEN | 32 / 64 |
| VLEN / ELEN | N/A 或 TBD |
| 主频 | TBD |
| 编译器及版本 | TBD |
| 编译参数 | TBD |
| 测试环境 | RTL 仿真 / FPGA / 芯片 |
| 测试模式 | M / S / U |
| 测试日期 | YYYY-MM-DD |

## 2. 指标定义

| 指标 | 定义 | 单位 |
| --- | --- | --- |
| Latency | 指令结果可被后继相关指令使用所需的周期数 | cycle |
| Issue Interval | 同类独立指令连续进入执行单元的最小间隔 | cycle |
| Reciprocal Throughput | 平均完成一条同类独立指令所需的周期数 | cycle/instruction |
| Throughput | 每周期可完成的同类独立指令数 | instruction/cycle |

约定：

- 使用 RAW 依赖链测量 `Latency`，并扣除循环、计数器读取等开销。
- `Throughput = 1 / Reciprocal Throughput`；多执行单元并行时记录聚合吞吐率。
- `TBD` 表示待测，`N/A` 表示不适用，`variable` 表示结果依赖运行条件。
- 伪指令按汇编器展开后的真实指令记录。
- 不同源操作数的旁路延迟不同时，分别记录 `rs1 -> rd`、`rs2 -> rd` 等路径。

## 3. 扩展级汇总

| 扩展 | 类别 | 执行单元/端口 | 典型 Latency | Issue Interval | 峰值 Throughput | 备注 |
| --- | --- | --- | ---: | ---: | ---: | --- |
| RV32I/RV64I/E | 基础整数 | TBD | TBD | TBD | TBD |  |
| M / Zmmul | 整数乘除 | TBD | TBD | TBD | TBD | 乘法、除法分开记录 |
| A | 原子操作 | TBD | TBD | TBD | TBD | 标明缓存和竞争状态 |
| F / D / Q | 浮点 | TBD | TBD | TBD | TBD | 按精度记录 |
| Zfh / BF16 | 半精度浮点 | TBD | TBD | TBD | TBD |  |
| C / Zc* | 压缩编码 | TBD | TBD | TBD | TBD | 同时关注前端吞吐 |
| B | 位操作 | TBD | TBD | TBD | TBD | 按 Zba/Zbb/Zbc/Zbs 细分 |
| V / Zve* | 向量 | TBD | TBD | TBD | TBD | 记录 SEW/LMUL/VL |
| Scalar Crypto | 标量密码 | TBD | TBD | TBD | TBD |  |
| Vector Crypto | 向量密码 | TBD | TBD | TBD | TBD |  |
| Zicsr | CSR | TBD | TBD | TBD | TBD | 标明具体 CSR |
| Zifencei / CMO | 栅栏与缓存管理 | TBD | TBD | TBD | TBD | 通常为 variable |
| Privileged | 特权指令 | TBD | TBD | TBD | TBD | 记录权限级和系统状态 |
| Xvendor | 厂商自定义 | TBD | TBD | TBD | TBD | 替换为实际扩展名 |

## 4. 通用逐指令表

| 扩展 | 指令 | 操作数形式 | XLEN/SEW | 执行单元 | Latency | Issue Interval | Throughput | 测试条件 | 数据来源 | 备注 |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |
| TBD | `instruction` | `rd, rs1, rs2` | TBD | TBD | TBD | TBD | TBD | TBD | 文档/RTL/实测 | TBD |

## 5. I / E：基础整数

### 5.1 整数计算

| 指令组 | 操作 | Latency | Issue Interval | Throughput | 执行单元/端口 | 备注 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `LUI`, `AUIPC` | 立即数构造 | TBD | TBD | TBD | TBD |  |
| `ADD[I]`, `SUB` | 加减法 | TBD | TBD | TBD | TBD |  |
| `SLT[I][U]` | 比较 | TBD | TBD | TBD | TBD |  |
| `XOR[I]`, `OR[I]`, `AND[I]` | 逻辑 | TBD | TBD | TBD | TBD |  |
| `SLL[I]`, `SRL[I]`, `SRA[I]` | 移位 | TBD | TBD | TBD | TBD |  |
| `ADD[I]W`, `SUBW` | RV64 32-bit 加减 | TBD | TBD | TBD | TBD | RV64 |
| `SLL[I]W`, `SRL[I]W`, `SRA[I]W` | RV64 32-bit 移位 | TBD | TBD | TBD | TBD | RV64 |

### 5.2 控制流

| 指令组 | 场景 | Latency/代价 | Issue Interval | Throughput | 备注 |
| --- | --- | ---: | ---: | ---: | --- |
| `JAL` | 预测正确 | TBD | TBD | TBD |  |
| `JALR` | 目标预测正确 | TBD | TBD | TBD |  |
| `JALR` | 目标预测失败 | TBD | N/A | N/A | 记录恢复代价 |
| `BEQ/BNE/BLT[U]/BGE[U]` | 跳转，预测正确 | TBD | TBD | TBD |  |
| `BEQ/BNE/BLT[U]/BGE[U]` | 不跳转，预测正确 | TBD | TBD | TBD |  |
| `BEQ/BNE/BLT[U]/BGE[U]` | 预测失败 | TBD | N/A | N/A | 记录恢复代价 |

### 5.3 Load / Store

| 指令组 | 数据位置/场景 | 地址到结果 Latency | Issue Interval | Throughput | AGU/端口 | 备注 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `LB/LBU/LH/LHU/LW/LWU/LD` | L1 D-cache 命中 | TBD | TBD | TBD | TBD | 按 XLEN 筛选 |
| Load | L2 命中 | TBD | TBD | TBD | TBD |  |
| Load | LLC/内存 | variable | TBD | TBD | TBD | 报告均值和分位数 |
| `SB/SH/SW/SD` | Store buffer 可接收 | TBD | TBD | TBD | TBD | 区分退休和全局可见 |
| Load / Store | 未对齐 | TBD | TBD | TBD | TBD | 记录是否跨 cache line/page |
| Load -> ALU | load-use 依赖 | TBD | N/A | N/A | TBD |  |
| Store -> Load | 同地址转发 | TBD | N/A | N/A | TBD |  |
| `FENCE` | 指定 predecessor/successor | variable | N/A | N/A | TBD | 记录 fence 参数 |

## 6. M / Zmmul：整数乘除

| 指令组 | 操作数条件 | Latency | Issue Interval | Throughput | 执行单元 | 备注 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `MUL` | 任意 | TBD | TBD | TBD | TBD |  |
| `MULH/MULHU/MULHSU` | 任意 | TBD | TBD | TBD | TBD |  |
| `MULW` | RV64 | TBD | TBD | TBD | TBD |  |
| `DIV/DIVU` | 一般操作数 | TBD | TBD | TBD | TBD | 标明固定或数据相关延迟 |
| `DIVW/DIVUW` | RV64 | TBD | TBD | TBD | TBD |  |
| `REM/REMU` | 一般操作数 | TBD | TBD | TBD | TBD |  |
| `REMW/REMUW` | RV64 | TBD | TBD | TBD | TBD |  |
| `DIV/REM` | 除数为 0 | TBD | TBD | TBD | TBD |  |
| `DIV/REM` | 溢出特例 | TBD | TBD | TBD | TBD |  |

## 7. A 及相关原子扩展

记录 `aq/rl`、缓存层级、跨核竞争、reservation 成功与否、内存区域属性。

| 扩展 | 指令组 | 场景 | Latency | Issue Interval | Throughput | 备注 |
| --- | --- | --- | ---: | ---: | ---: | --- |
| A | `LR.W/LR.D` | L1 命中 | TBD | TBD | TBD |  |
| A | `SC.W/SC.D` | 成功 | TBD | TBD | TBD |  |
| A | `SC.W/SC.D` | 失败 | TBD | TBD | TBD |  |
| A | `AMOSWAP/AMOADD` | 无竞争 | TBD | TBD | TBD | W/D 分开记录 |
| A | `AMOXOR/AMOAND/AMOOR` | 无竞争 | TBD | TBD | TBD | W/D 分开记录 |
| A | `AMOMIN/MAX/MINU/MAXU` | 无竞争 | TBD | TBD | TBD | W/D 分开记录 |
| Zabha | Byte/Halfword AMO | 无竞争 | TBD | TBD | TBD |  |
| Zacas | `AMOCAS.W/D/Q` | 比较成功/失败 | TBD | TBD | TBD | 分场景记录 |
| Zawrs | `WRS.NTO/WRS.STO` | 唤醒/超时 | variable | N/A | N/A |  |

## 8. F / D / Q / Zfh / BF16：浮点

按 `.H`、`.S`、`.D`、`.Q`、`.BF16` 分别展开，并记录舍入模式、正常数、次正规数、NaN/Inf 和异常标志。

| 指令组 | 精度/场景 | Latency | Issue Interval | Throughput | 执行单元 | 备注 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `FLH/FLW/FLD/FLQ` | L1 命中 | TBD | TBD | TBD | TBD |  |
| `FSH/FSW/FSD/FSQ` | Store buffer 可接收 | TBD | TBD | TBD | TBD |  |
| `FADD/FSUB` | 正常数 | TBD | TBD | TBD | TBD |  |
| `FMUL` | 正常数 | TBD | TBD | TBD | TBD |  |
| `FMADD/FMSUB/FNMSUB/FNMADD` | 正常数 | TBD | TBD | TBD | TBD |  |
| `FDIV` | 正常数 | TBD | TBD | TBD | TBD |  |
| `FSQRT` | 正常数 | TBD | TBD | TBD | TBD |  |
| `FSGNJ/FSGNJN/FSGNJX` | 任意 | TBD | TBD | TBD | TBD |  |
| `FMIN/FMAX` | 普通值/NaN | TBD | TBD | TBD | TBD |  |
| `FEQ/FLT/FLE` | 普通值/NaN | TBD | TBD | TBD | TBD |  |
| `FCLASS` | 任意 | TBD | TBD | TBD | TBD |  |
| `FCVT` | 浮点精度转换 | TBD | TBD | TBD | TBD |  |
| `FCVT` | 整数/浮点转换 | TBD | TBD | TBD | TBD |  |
| `FMV` | 整数/浮点搬移 | TBD | TBD | TBD | TBD |  |
| Zfa 指令 | 按指令和精度 | TBD | TBD | TBD | TBD | 如 `FLI/FROUND/FMINM` |

## 9. C / Zc*：压缩扩展

压缩指令通常与对应 32-bit 指令共用后端路径。除后端延迟外，应记录 I-cache 占用、取指边界和前端供给吞吐率。

| 扩展 | 指令组 | 等价基础操作 | 后端 Latency | 前端 Throughput | 备注 |
| --- | --- | --- | ---: | ---: | --- |
| C/Zca | 压缩 ALU | 整数 ALU | TBD | TBD |  |
| C/Zca | 压缩 Load/Store | Load/Store | TBD | TBD | 按 XLEN |
| C/Zca | 压缩跳转/分支 | Control flow | TBD | TBD |  |
| Zcb | 简单压缩操作 | 对应基础操作 | TBD | TBD |  |
| Zcmp | `CM.PUSH/CM.POP*` | 多寄存器栈操作 | TBD | TBD | 记录寄存器数量 |
| Zcmt | `CM.JT/CM.JALT` | 表跳转 | TBD | TBD | 记录表命中层级 |

## 10. B：位操作扩展

| 子扩展 | 指令组 | Latency | Issue Interval | Throughput | 执行单元 | 备注 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| Zba | `SH1ADD/SH2ADD/SH3ADD` | TBD | TBD | TBD | TBD |  |
| Zba | `ADD.UW/SH*ADD.UW/SLLI.UW` | TBD | TBD | TBD | TBD | RV64 |
| Zbb | `ANDN/ORN/XNOR` | TBD | TBD | TBD | TBD |  |
| Zbb | `CLZ/CTZ/CPOP` | TBD | TBD | TBD | TBD | 含 `.W` 变体 |
| Zbb | `MAX/MAXU/MIN/MINU` | TBD | TBD | TBD | TBD |  |
| Zbb | `SEXT/ZEXT` | TBD | TBD | TBD | TBD |  |
| Zbb | `ROL/ROR/RORI` | TBD | TBD | TBD | TBD |  |
| Zbb | `ORC.B/REV8` | TBD | TBD | TBD | TBD |  |
| Zbc | `CLMUL/CLMULH/CLMULR` | TBD | TBD | TBD | TBD |  |
| Zbs | `BCLR/BEXT/BINV/BSET` | TBD | TBD | TBD | TBD | 含立即数变体 |

## 11. 常用 Z 扩展与缓存管理

| 扩展 | 指令组 | 场景 | Latency/代价 | Issue Interval | Throughput | 备注 |
| --- | --- | --- | ---: | ---: | ---: | --- |
| Zicsr | `CSRRW/CSRRS/CSRRC` | 普通 CSR | TBD | TBD | TBD | 含立即数变体 |
| Zicsr | CSR read | 计数器/流水线状态 | TBD | TBD | TBD | 标明 CSR |
| Zifencei | `FENCE.I` | 修改代码前/后 | variable | N/A | N/A | 记录 I-cache 范围 |
| Zicond | `CZERO.EQZ/CZERO.NEZ` | 条件真/假 | TBD | TBD | TBD |  |
| Zicntr | `RDCYCLE/RDTIME/RDINSTRET` | 允许访问 | TBD | TBD | TBD |  |
| Zihpm | `HPMCOUNTER*` | 允许访问 | TBD | TBD | TBD |  |
| Zihintpause | `PAUSE` | 单线程/SMT 竞争 | TBD | TBD | TBD | Hint 可为空操作 |
| Zicbom | `CBO.CLEAN/FLUSH/INVAL` | clean/dirty | variable | TBD | TBD | 记录层级 |
| Zicboz | `CBO.ZERO` | 命中/未命中 | variable | TBD | TBD |  |
| Zicbop | `PREFETCH.I/R/W` | L1/L2/LLC | variable | TBD | TBD | 记录后继访问收益 |
| Ztso | `FENCE.TSO` | 指定访问序列 | variable | N/A | N/A |  |

## 12. V / Zve*：向量扩展

每组结果必须记录 `VLEN`、`ELEN`、`SEW`、`LMUL`、`VL`、tail/mask policy、mask 密度和对齐状态。

建议同时报告：

- 首结果延迟：第一组元素结果可用的周期。
- 完成延迟：整条向量指令完成的周期。
- 元素吞吐率：`element/cycle`。
- 指令吞吐率：固定 `SEW/LMUL/VL` 下的 `instruction/cycle`。

| 指令组 | SEW | LMUL | VL | 场景 | 首结果 Latency | 完成 Latency | Throughput (element/cycle) | 备注 |
| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: | --- |
| `VSETVLI/VSETIVLI/VSETVL` | TBD | TBD | TBD | 配置变化/不变 | TBD | TBD | N/A |  |
| Unit-stride load/store | TBD | TBD | TBD | L1 命中 | TBD | TBD | TBD |  |
| Strided load/store | TBD | TBD | TBD | L1 命中 | TBD | TBD | TBD |  |
| Indexed load/store | TBD | TBD | TBD | ordered/unordered | TBD | TBD | TBD |  |
| Segment load/store | TBD | TBD | TBD | NF=TBD | TBD | TBD | TBD |  |
| Integer add/sub/logical | TBD | TBD | TBD | vv/vx/vi | TBD | TBD | TBD |  |
| Integer multiply/MAC | TBD | TBD | TBD | vv/vx | TBD | TBD | TBD |  |
| Integer divide/remainder | TBD | TBD | TBD | vv/vx | TBD | TBD | TBD | 标明数据相关延迟 |
| FP add/sub/multiply/FMA | TBD | TBD | TBD | vv/vf | TBD | TBD | TBD |  |
| FP divide/sqrt | TBD | TBD | TBD | 正常数 | TBD | TBD | TBD |  |
| Integer/FP reduction | TBD | TBD | TBD | ordered/unordered | TBD | TBD | TBD |  |
| Mask operations | TBD | TBD | TBD | mask 密度=TBD | TBD | TBD | TBD |  |
| Slide/gather/compress | TBD | TBD | TBD | TBD | TBD | TBD | TBD |  |

## 13. 密码扩展

| 类别 | 子扩展 | 指令组 | 配置 | Latency | Issue Interval/Throughput | 备注 |
| --- | --- | --- | --- | ---: | ---: | --- |
| 标量 | Zbkb/Zbkx/Zbkc | pack/xperm/CLMUL | XLEN=TBD | TBD | TBD |  |
| 标量 | Zknd/Zkne | AES | XLEN=TBD | TBD | TBD |  |
| 标量 | Zknh | SHA-2 | XLEN=TBD | TBD | TBD |  |
| 标量 | Zksed/Zksh | SM4/SM3 | XLEN=TBD | TBD | TBD |  |
| 标量 | Zkr | `SEED` | XLEN=TBD | variable | TBD | 记录熵源行为 |
| 向量 | Zvbb/Zvkb/Zvbc | 位操作/CLMUL | SEW/LMUL/VL=TBD | TBD | TBD |  |
| 向量 | Zvkg/Zvkn*/Zvks* | GCM/AES/SHA/SM | SEW/LMUL/VL=TBD | TBD | TBD |  |

## 14. 特权架构指令

| 扩展/级别 | 指令 | 场景 | Latency/代价 | 备注 |
| --- | --- | --- | ---: | --- |
| Privileged | `MRET/SRET` | 无中断挂起 | TBD | 记录前端重定向代价 |
| Privileged | `WFI` | 立即返回/等待中断 | variable |  |
| S | `SFENCE.VMA` | 单地址/ASID/全局 | variable | 记录 TLB 项数量 |
| H | `HFENCE.VVMA/HFENCE.GVMA` | 指定 VMID/地址 | variable |  |
| H | `HLV/HSV` | TLB/L1 命中 | TBD |  |
| Svinval | `SINVAL.VMA` 等 | 指定范围 | variable |  |
| Base | `ECALL/EBREAK` | 指定权限级和 handler | variable | 记录完整往返或仅陷入代价 |

## 15. 厂商自定义扩展

| 扩展 | 指令 | 功能 | 操作数形式 | Latency | Issue Interval | Throughput | 执行单元 | 备注 |
| --- | --- | --- | --- | ---: | ---: | ---: | --- | --- |
| `X...` | `instruction` | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

## 16. 微基准原始结果

| 测试 ID | 指令组 | 测试类型 | 循环展开 | 迭代次数 | 原始周期 | 扣除开销后周期 | 结果 | 日志/波形路径 |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- |
| TBD | TBD | latency/throughput | TBD | TBD | TBD | TBD | TBD | TBD |

```asm
# Latency：构造真实 RAW 依赖链
.rept N
    op rd, rd, rs2
.endr

# Throughput：使用互不相关的寄存器
.rept N
    op rd0, rs0, rs1
    op rd1, rs2, rs3
    op rd2, rs4, rs5
    op rd3, rs6, rs7
.endr
```

测试检查项：

- 固定或记录频率，预热 I-cache、D-cache、TLB 和分支预测器。
- 扣除计数器读取、循环分支和函数调用开销。
- 检查汇编结果，确认指令未被删除、融合或改写。
- 对乱序核使用足够长的依赖链和独立指令流。
- 分别报告冷/热 cache、预测正确/错误、原子竞争/无竞争结果。
- 重复测试并报告最小值、中位数和波动范围。

## 17. 数据状态

| 状态 | 含义 |
| --- | --- |
| `documented` | 来自处理器官方文档 |
| `rtl-derived` | 根据 RTL 流水级和执行单元分析得到 |
| `measured` | 由微基准实测得到 |
| `estimated` | 根据有限证据估算，尚未验证 |
| `unsupported` | 当前实现不支持该扩展或指令 |

## 18. 参考规范

- [RISC-V Unprivileged ISA Specification](https://docs.riscv.org/reference/isa/unpriv/unpriv-index.html)
- [RISC-V Privileged Architecture Specification](https://docs.riscv.org/reference/isa/priv/priv-index.html)
- [RISC-V ISA Extension Naming Conventions](https://docs.riscv.org/reference/isa/unpriv/naming.html)
- [RISC-V Vector Extension](https://docs.riscv.org/reference/isa/unpriv/v-st-ext.html)
