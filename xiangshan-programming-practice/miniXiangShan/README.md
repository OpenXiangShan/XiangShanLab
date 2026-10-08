# miniXiangShan · 乱序 RISC-V 处理器（Chisel）

以香山（XiangShan）微架构为原型的教学级乱序处理器，指令集为 **RISC-V RV32IM
（Zicsr + M/S/U 特权模式 + Sv32 分页）**。

## 指令集与特权架构

- **RV32I**：LUI / AUIPC / JAL / JALR / 六条条件分支 / LB-LHU / SB-SW /
  ADDI-SRAI / ADD-AND
- **RV32M**：MUL / MULH / MULHSU / MULHU / DIV / DIVU / REM / REMU
- **Zicsr**：CSRRW / CSRRS / CSRRC / CSRRWI / CSRRSI / CSRRCI
- **特权指令**：ECALL / EBREAK / MRET / SRET / WFI / SFENCE.VMA / FENCE / FENCE.I
- **特权资源**：
  - 机器级：`mstatus misa medeleg mideleg mie mtvec mcounteren mscratch mepc
    mcause mtval mip pmpcfg0-3 pmpaddr0-15 mcycle minstret mvendorid marchid mimpid mhartid`
  - 监督级：`sstatus sie stvec scounteren sscratch sepc scause stval sip satp`
  - 用户级：`cycle time instret`
  - 异常编号遵循 RISC-V 规范（0~15），支持 `medeleg/mideleg` 委托与
    direct/vectored 两种陷入向量模式
- **MMU**：`satp.MODE = 0` 裸模式恒等映射；`satp.MODE = 1` 走 Sv32
  （两级页表、4KB 页、PTE 的 V/R/W/X/U/G/A/D、9 位 ASID）。
  TLB 缺失时由硬件页表遍历器（`mmu/Ptw.scala`）读取 PTE 并回填 TLB，
  PTE 读取复用 L2 的物理读端口（`memory/L2cache/L2ReadArbiter.scala` 与
  I-Cache 共享该端口）。

## 微架构

- 取指宽度 4 / 译码与分发宽度 3 / ROB 深度 32 / 提交宽度 3
- 寄存器重命名（重命名表 + 空闲列表 + BusyTable）、发射队列/调度器、访存队列
- 分支预测：BTB + PHT + RAS
- L1 分离 I/D Cache + 统一 L2 Cache，对外为 AXI3 接口
- 支持与参考模型对拍（`difftest/`，切到 RISC-V 后需配套 RISC-V NEMU）

## 构建

```shell
cd designCPUByChisel
sbt "runMain minixiangshan.CoreGen simu"   # 生成仿真用 Verilog
sbt "runMain minixiangshan.CoreGen fpga"   # 生成上板用 Verilog
```

生成结果位于 `chiplab/IP/myCPU/{Chisel,FPGA}/`。

## 目录

| 目录 | 说明 |
| --- | --- |
| `backend/decode` | 译码表、立即数生成、指令编码 |
| `backend/{rename,dispatch,scheduler,regfile,execute}` | 乱序后端各级 |
| `backend/Rob.scala`、`backend/RedirectController.scala` | 提交与重定向 |
| `csr` | RISC-V CSR 文件与陷入/中断处理 |
| `mmu` | Sv32 TLB、页表遍历器、地址翻译 |
| `frontend` | 取指、BPU、预译码、I-Cache |
| `memory` | LSQ、D-Cache、L2 Cache |

## TODO（后续）

- 更完整的 PMP/PMA 检查（当前仅占位，不产生 access fault）
- Sv32 的 A/D 位硬件置位与写回
- RISC-V NEMU 对拍环境与测试程序迁移
