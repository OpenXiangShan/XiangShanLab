# l1miss_l2miss 波形分析报告

## 结论

**PASS，高置信度。** 目标 load 冷启动访问 `0x80024000`，L1 DCache 产生
`AcquireBlock NtoB`；L2 目录 miss，分配 MSHR，并向 CHI/L3 发出
`ReadNotSharedDirty`。下级数据返回后，L1 收到两拍 `GrantData`（首拍包含
`0x12345678`），随后 E 通道完成 `GrantAck`，目标 load 返回正确值并最终提交。
该场景是普通 L1 miss + L2 miss/refill 的最小基线。

## 验证对象

- 波形：`l1miss_l2miss.fst`
- 波形 SHA-256：`5b55ba15106f429b07d4bee93083fbee39df6ad35c9af050d0d057e80255e71a`
- 仿真器：昆明湖 V3 `kmhv3-single`（XiangShan `6705b63685d301f8c0960f7cbcc8b964660dc58e`，
  XSCache `e9488aed486358e3e5984b531aab601f9b91a9c0`）
- 采样：`TOP.SimTop.clock` 上升沿
- 目标 cache line：`0x80024000`，line size 为 64 bytes
- 目标 load：PC `0x80000132`，指令 `ld t1, 0(t0)`
- 目标 L2 位置：slice `0`、set `0x40`、tag `0x10004`
- 程序结果：`HIT GOOD TRAP at pc = 0x80000156`，返回值为 0

当前 V3 构建没有把 ROB 的逐指令 commit 端导出到波形，因此本文用“目标 load 的
D/E 完成 + 程序最终 GOOD TRAP 且返回值为 0”作为提交证据。

TileLink 通道字段只在对应握手成立时有效。本报告只把 `valid=1 && ready=1` 的 A、D、E、
CHI 记录视为事务证据。

## 测试如何构造该场景

程序只对 `0x80024000` 执行一次目标 load，目标 line 中保存 `0x12345678`，不添加
冲突访问、替换压力或多核同步。这样可以把观察到的目标事务直接归因到唯一的 load。

```asm
la      t0, target_line
ld      t1, 0(t0)
li      t2, 0x12345678
bne     t1, t2, fail
li      a0, 0
ret
```

V3 的 L1 DCache 是 8-way、128 sets，L2 是 4 banks × 128 sets × 8 ways。目标行在
L2 slice 0、set `0x40`、tag `0x10004`。

## RTL 判据

- L1 DCache 的 S2 miss 通过 `io.miss_req.valid := s2_valid && s2_can_go_to_mq` 进入
  MissQueue（`src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:909-910`）。
- MissQueue 对普通 load miss 选择 `acquireBlock`，而不是 full-overwrite store 的
  `acquirePerm`（`MissQueue.scala:987-1003`）。
- CoupledL2 main pipe 在目录 miss 时把 `need_acquire_s3_a` 和 `need_mshr_s3_a`
  置位，并驱动 `mshr_alloc_s3.valid`（`coupledL2/src/main/scala/coupledL2/MainPipe.scala:245-266,305-310`）。
- MSHR 把 `AcquireBlock NtoB` 映射为 `ReadNotSharedDirty`
  （`coupledL2/src/main/scala/coupledL2/MSHR.scala:386-393`）。
- 目录 hit 且不需要 MSHR 时，`sink_resp_s3.valid` 直接产生响应
  （`MainPipe.scala:433-437`）。

本场景只覆盖 miss 分支，不覆盖 hit 直返分支。

V3 波形在 L1 DCache 侧 A 端口没有观察到可用的目标请求记录，本文把 A 通道证据取在 L2 入口
`slices_0.io_in_a_*`，并用 DCache missQueue 的 `io_misstrack_alloc_1_*` 补充 L1 miss
证据。

## 目标请求进入 L2

本节 L2 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0`。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_in_a_valid` | 1 | 1 | 目标请求有效 |
| `io_in_a_ready` | 1 | 1 | L2 接收，A fire |
| `io_in_a_bits_opcode` | 6 | 6 | `AcquireBlock` |
| `io_in_a_bits_param` | 0 | 0 | `NtoB` |
| `io_in_a_bits_size` | 6 | 6 | 64 bytes |
| `io_in_a_bits_address` | `0x80024000` | `0x80024000` | 目标行 |
| 握手周期 | — | 1123 | L1→L2 |

DCache missQueue 在 cycle 1125 分配 `io_misstrack_alloc_1_valid`，`paddr=0x80024000`，
与 L2 入口的 A 请求相互印证。

## L2 目录 miss 与 MSHR 分配

同一 slice 的 mainPipe：

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_taskFromArb_s2_bits_opcode` | 6 | 6 | AcquireBlock 进入 main pipe |
| `io_dirResp_s3_hit` | 0 | 0 | 目录 miss |
| `io_dirResp_s3_set` | `0x40` | `0x40` | 目标 set |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 1 | 1 | 分配 MSHR |
| 采样周期 | — | 1125 | s3 目录结果 |

## CHI ReadNotSharedDirty

`slices_0.txreq`：

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_out_valid` | 1 | 1 | CHI 请求有效 |
| `io_out_ready` | 1 | 1 | 下级接收 |
| `io_out_bits_opcode` | `0x26` | `38 (0x26)` | `ReadNotSharedDirty` |
| `io_out_bits_addr` | `0x80024000` | `0x80024000` | 目标行 |
| 握手周期 | — | 1127 | L2→CHI/L3 |

## 返回与完成

DCache D/E 作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache`。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `auto_client_nodes_out_0_d_valid` | 1 | 1 | GrantData 有效 |
| `auto_client_nodes_out_0_d_ready` | 1 | 1 | L1 接收 |
| `auto_client_nodes_out_0_d_bits_opcode` | 5 | 5 | `GrantData` |
| `auto_client_nodes_out_0_d_bits_data`（首拍） | `0x12345678` | `0x12345678` | 目标行数据 |
| `auto_client_nodes_out_0_d_bits_data`（次拍） | `0x0` | `0x0` | 64-byte line 第二拍 |
| `auto_client_nodes_out_0_d_bits_denied` | 0 | 0 | 无错误 |
| `auto_client_nodes_out_0_d_bits_corrupt` | 0 | 0 | 无错误 |
| 两拍 D 握手周期 | — | 1168、1169 | 完整 line |
| `auto_client_nodes_out_0_e_valid` | 1 | 1 | GrantAck |
| E 握手周期 | — | 1169 | 事务 sink 释放 |

完整链路：

```text
目标 load（PC 0x80000132）
  -> L1 AcquireBlock（L2 入口 1123）
  -> L2 directory miss（1125）
  -> 分配 MSHR（1125）
  -> CHI ReadNotSharedDirty（1127）
  -> 两拍 GrantData（1168、1169）
  -> GrantAck（1169）
  -> 目标 load 返回 0x12345678
  -> 程序 HIT GOOD TRAP
```

## 对照与边界

- 本场景没有任何同地址的第二次访问，CHI 请求与 A 请求一一对应。
- 没有观察到 L2 目录 hit 直返、replacement 或 dirty victim。
- 没有覆盖 `AcquirePerm`、NtoT/ReadUnique、store miss、AMO、prefetch 和 CMO。
- 当前 V3 构建未导出逐指令 commit 端；提交证据是 D/E 完成、程序返回值检查和最终
  GOOD TRAP。
- 本文不提供工具生成的 RTL line/branch coverage 数值。

## 相关文件

- 源程序：`l1miss_l2miss.S`
- 反汇编：`disassembly.txt`
- 波形：`l1miss_l2miss.fst`
- 构造分析：`CONSTRUCTION_ANALYSIS.md`
- RTL 代码覆盖分析：`RTL_CODE_ANALYSIS.md`
