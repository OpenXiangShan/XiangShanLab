# l1miss_l2hit 波形分析报告

## 结论

**PASS，高置信度。** 目标行第一次访问在 L1、L2 均 miss，并从 CHI/L3 取回后装入
L2；随后 16 条与目标地址映射到同一 L1 set、但分散到不同 L2 set 的冲突 line 把目标
行挤出 8-way L1；第二次访问因此在 L1 miss，但在 8-way L2 目录命中，由 L2 直接返回
两拍 `GrantData`，没有再次访问 CHI/L3，目标 `ld` 返回正确值并最终提交。

下面的硬件解释来自测试程序、反汇编、TileLink 定义和 V3 RTL，再逐项与波形实测值核对。

## 验证对象

- 波形：`l1miss_l2hit.fst`
- 波形 SHA-256：`99911644b8be9ef69affcb26e27e35165fbfbfda64a12f990bfdf38b632bdb35`
- 仿真器：昆明湖 V3 `kmhv3-single`（XiangShan `6705b63685d301f8c0960f7cbcc8b964660dc58e`，
  XSCache `e9488aed486358e3e5984b531aab601f9b91a9c0`）
- 采样：`TOP.SimTop.clock` 上升沿
- 目标 cache line：`0x80024000`，line size 为 64 bytes
- 目标 reload 指令：PC `0x80000194`，指令 `0x0002b303`，即 `ld t1, 0(t0)`
- 目标 L2 位置：slice `0`、set `0x40`、tag `0x10004`
- 程序结果：`HIT GOOD TRAP at pc = 0x800001b8`，目标值校验通过

当前 V3 构建没有把 ROB 的逐指令 commit 端导出到波形，因此本文用“目标 load 的
D/E 完成 + 程序最终 GOOD TRAP 且返回值为 0”作为提交证据，不伪造逐指令 commit PC。

TileLink 通道字段只在对应握手成立时有效。本报告只把 `valid=1 && ready=1` 的 A、D、E、
CHI 记录视为事务证据；其他周期总线上的残留值不参与判断。

## 测试如何构造该场景

测试程序先加载 `target_line`，然后按指针链串行加载 16 条冲突 line，最后再次加载
`target_line`。反汇编确认第一次和第二次目标 load 的地址均为 `0x80024000`，第二次
load 位于 PC `0x80000194`。

V3 的 L1 DCache 是 `64 KiB / 128 sets = 8 ways`，L2 是 4 banks × 128 sets × 8 ways。
二者的 set index 不同：

- L1 set = `addr[12:6]`；
- L2 bank = `addr[7:6]`，L2 set = `addr[14:8]`。

本程序取冲突 stride 为 `8 KiB (0x2000)`：

- `addr[12:6]` 不变，所以 17 条 line 落在同一个 L1 set；
- `addr[14:8]` 每次增加 `0x20`，所以它们分散到 4 个 L2 set，每个 L2 set 最多 5 条 line。

这样既保证超过 8-way L1 的容量，又不会超过 8-way L2 的容量。目标与 16 条冲突 line
的地址为：

| 逻辑对象 | 地址 |
| --- | --- |
| `target_line` | `0x80024000` |
| `conflict_line_1` | `0x80026000` |
| `conflict_line_2` | `0x80028000` |
| `conflict_line_3` | `0x8002a000` |
| `conflict_line_4` | `0x8002c000` |
| `conflict_line_5` | `0x8002e000` |
| `conflict_line_6` | `0x80030000` |
| `conflict_line_7` | `0x80032000` |
| `conflict_line_8` | `0x80034000` |
| `conflict_line_9` | `0x80036000` |
| `conflict_line_10` | `0x80038000` |
| `conflict_line_11` | `0x8003a000` |
| `conflict_line_12` | `0x8003c000` |
| `conflict_line_13` | `0x8003e000` |
| `conflict_line_14` | `0x80040000` |
| `conflict_line_15` | `0x80042000` |
| `conflict_line_16` | `0x80044000` |

指针链建立了逐次 load 的真实数据依赖，防止处理器并行或提前发射冲突访问；之后的
等待循环将第二次目标 load 与前面的 refill 分开。

这些地址是 V3 参数下的重新构造。V2 的 6 条 `128 KiB` 冲突只在 4-way L1 上成立；
V3 的 L1 已是 8-way，6 条冲突不会驱逐目标行，直接照搬旧程序会退化成 L1 hit，不能
证明 L1 miss、L2 hit。

## RTL 判据

TileLink 定义规定：A opcode `6` 是 `AcquireBlock`，A param `0` 是 `NtoB`，D opcode
`5` 是 `GrantData`。因此 `A fire + opcode=6 + address=目标行` 表示上层 cache 因没有
可用副本而请求整条 cache line，而不是普通已命中的 load。

CoupledL2 main pipe 的关键分支是：

- 目录 miss 时，`need_acquire_s3_a=1`，进而 `need_mshr_s3=1`，
  `io_toMSHRCtl.mshr_alloc_s3.valid=1`；请求进入 miss 处理流程。
- 目录 hit 且当前权限足以满足 `AcquireBlock NtoB` 时，`need_mshr_s3=0`；RTL 通过
  `sink_resp_s3.valid=1` 直接生成 `GrantData`，并从 L2 data storage 读取数据。

源码依据：

- `coupledL2/src/main/scala/coupledL2/MainPipe.scala:245-266`：`need_acquire_s3_a`、
  `need_mshr_s3_a`；
- `MainPipe.scala:305-310`：`need_mshr_s3` 与 `mshr_alloc_s3.valid`；
- `MainPipe.scala:433-437`：`sink_resp_s3.valid` 和 `odOpGen`。

所以，仅看到目录 `hit` 还不够。必须同时看到 `mshr_alloc_s3.valid=0`，并以“没有目标
CHI 请求”作为独立反证，才足以确认 L2 hit 路径。

V3 波形在 L1 DCache 侧 A 端口没有观察到可用的目标请求记录，本文把 A 通道证据取在 L2 入口
`slices_0.io_in_a_*`，并用 DCache missQueue 的 `io_misstrack_alloc_*_valid` 补充
L1 miss 证据。

## 第一次目标访问：建立 L2 副本

### 第一次目标请求进入 L2

本节 L2 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0`。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_in_a_valid` | 1 | 1 | 目标请求有效 |
| `io_in_a_ready` | 1 | 1 | L2 接收请求，A fire 成立 |
| `io_in_a_bits_opcode` | 6 | 6 | `AcquireBlock` |
| `io_in_a_bits_param` | 0 | 0 | `NtoB`，请求只读共享权限 |
| `io_in_a_bits_size` | 6 | 6 | `2^6 = 64 bytes` |
| `io_in_a_bits_address` | `0x80024000` | `0x80024000` | 目标行 |
| 握手周期 | — | 1122 | L1→L2 目标请求 |

DCache missQueue 在 cycle 1124 分配 `io_misstrack_alloc_1_valid`，`paddr=0x80024000`，
与 L2 入口的 A 请求相互印证：这是 L1 侧真实 miss，而不是 L2 入口的残留值。

### L2 目录 miss 并分配 MSHR

同一 slice 的 mainPipe：

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_taskFromArb_s2_bits_opcode` | 6 | 6 | AcquireBlock 进入 main pipe |
| `io_dirResp_s3_hit` | 0 | 0 | 目录 miss |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 1 | 1 | 分配 MSHR |
| `io_dirResp_s3_set` | `0x40` | `0x40` | 目标 set |
| 采样周期 | — | 1124 | s3 目录结果 |

### MSHR 向 CHI/L3 取数

`slices_0.txreq`：

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_out_valid` | 1 | 1 | CHI 请求有效 |
| `io_out_ready` | 1 | 1 | 请求被下级接收 |
| `io_out_bits_opcode` | `0x26` | `38 (0x26)` | `ReadNotSharedDirty` |
| `io_out_bits_addr` | `0x80024000` | `0x80024000` | 目标行 |
| 握手周期 | — | 1126 | L2→CHI/L3 |

### 数据返回 L1

DCache 的 D/E 作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner_dcache`。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `auto_client_nodes_out_0_d_valid` | 1 | 1 | GrantData 有效 |
| `auto_client_nodes_out_0_d_ready` | 1 | 1 | L1 接收 |
| `auto_client_nodes_out_0_d_bits_opcode` | 5 | 5 | `GrantData` |
| `auto_client_nodes_out_0_d_bits_data`（首拍） | `0x80026000` | `0x80026000` | 目标行数据指向 `conflict_line_1` |
| `auto_client_nodes_out_0_d_bits_denied` | 0 | 0 | 无错误 |
| `auto_client_nodes_out_0_d_bits_corrupt` | 0 | 0 | 无错误 |
| `auto_client_nodes_out_0_e_valid` | 1 | 1 | GrantAck |
| 两拍 D 握手周期 | — | 1167、1168 | 完整 64-byte line |
| E 握手周期 | — | 1168 | 事务 sink 释放 |

第一次访问完整走通 L1 miss → L2 miss → MSHR → CHI 取数 → GrantData → GrantAck。

## 16 条同 L1 set 冲突访问

16 条冲突 line 依次在 L2 入口出现 `AcquireBlock NtoB`。每条请求都有独立的目标
地址、目录 miss 和 MSHR 分配：

| 冲突 line | A 握手周期 | L2 `dir_hit` | `mshr_alloc` | CHI read 周期 |
| --- | ---: | ---: | ---: | ---: |
| 1 `0x80026000` | 1175 | 0 | 1 | 1179 |
| 2 `0x80028000` | 1226 | 0 | 1 | 1230 |
| 3 `0x8002a000` | 1277 | 0 | 1 | 1281 |
| 4 `0x8002c000` | 1328 | 0 | 1 | 1332 |
| 5 `0x8002e000` | 1379 | 0 | 1 | 1383 |
| 6 `0x80030000` | 1430 | 0 | 1 | 1434 |
| 7 `0x80032000` | 1481 | 0 | 1 | 1485 |
| 8 `0x80034000` | 1532 | 0 | 1 | 1536 |
| 9 `0x80036000` | 1583 | 0 | 1 | 1587 |
| 10 `0x80038000` | 1634 | 0 | 1 | 1638 |
| 11 `0x8003a000` | 1685 | 0 | 1 | 1689 |
| 12 `0x8003c000` | 1736 | 0 | 1 | 1740 |
| 13 `0x8003e000` | 1787 | 0 | 1 | 1791 |
| 14 `0x80040000` | 1838 | 0 | 1 | 1842 |
| 15 `0x80042000` | 1889 | 0 | 1 | 1893 |
| 16 `0x80044000` | 1940 | 0 | 1 | 1944 |

这些冲突访问与目标行共用同一个 L1 set。17 条 line（目标 + 16 条冲突）超过 8-way
容量，因此目标行必然被替换出 L1；同时它们分散在 4 个 L2 set 中，每个 L2 set 只有
3–5 条 line，未超过 8-way L2 容量。

## 第二次目标访问：L2 直接响应

### 第二次目标请求仍从 L1 发出

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_in_a_bits_opcode` | 6 | 6 | `AcquireBlock` |
| `io_in_a_bits_param` | 0 | 0 | `NtoB` |
| `io_in_a_bits_address` | `0x80024000` | `0x80024000` | 再次访问目标行 |
| A 握手周期 | — | 2528 | 目标已不在 L1 |
| DCache `io_misstrack_alloc_1_valid` | 1 | 1（cycle 2530，`paddr=0x80024000`） | L1 miss 再次成立 |

### L2 目录命中，不分配 MSHR

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_taskFromArb_s2_bits_opcode` | 6 | 6 | AcquireBlock 进入 main pipe |
| `io_dirResp_s3_hit` | 1 | 1 | 目录命中 |
| `io_dirResp_s3_set` | `0x40` | `0x40` | 目标 set |
| `io_dirResp_s3_tag` | `0x10004` | `0x10004` | 目标 tag |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 0 | 0 | 不再分配 MSHR |
| 采样周期 | — | 2530 | s3 目录结果 |

### L2 直接返回 GrantData

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `auto_client_nodes_out_0_d_bits_opcode` | 5 | 5 | `GrantData` |
| `auto_client_nodes_out_0_d_bits_data`（首拍） | `0x80026000` | `0x80026000` | 目标行数据 |
| `auto_client_nodes_out_0_d_bits_denied` | 0 | 0 | 无错误 |
| `auto_client_nodes_out_0_d_bits_corrupt` | 0 | 0 | 无错误 |
| 两拍 D 握手周期 | — | 2534、2535 | 完整 line |
| E 握手周期 | — | 2535 | GrantAck |

从第二次目标 `AcquireBlock`（2528）到目标 load 完成（2535）的窗口内，没有再出现
目标地址 `0x80024000` 的 CHI TxReq。2535、2539、2540 的 CHI read 地址分别属于
`0x80001400` 和 `0x80001500`，是取指流量，不是目标行。因此可以排除“目录显示 hit、
实际仍向下级取数”的解释。

## 最终链路

```text
第一次目标 AcquireBlock（1122）
  -> L1 miss
  -> L2 directory miss（1124）
  -> 分配 MSHR（1124）
  -> CHI ReadNotSharedDirty（1126）
  -> 两拍 GrantData（1167、1168）
  -> GrantAck（1168）

16 条同 L1 set 冲突 line（1175..1940）
  -> 每条约 8 KiB，L1 set 不变、L2 set 分散
  -> 17 条 line 超过 8-way L1

第二次目标 AcquireBlock（2528）
  -> L1 miss
  -> L2 directory hit（2530）
  -> mshr_alloc=0
  -> 直接两拍 GrantData（2534、2535）
  -> GrantAck（2535）
  -> 无目标 CHI 请求
  -> 目标 load 返回 0x80026000，程序 HIT GOOD TRAP
```

## 否定证据与对照

- 第二次目标请求没有分配 MSHR（`mshr_alloc_s3_valid=0`），与第一次访问的
  `mshr_alloc_s3_valid=1` 形成直接对照。
- 第二次目标请求的目录结果是 `hit=1`，而 16 条冲突请求和第一次目标请求均为
  `hit=0`。
- 第二次目标窗口内没有同地址 CHI TxReq；同窗口的 CHI read 地址不是目标行。
- 目标 load 返回 `0x80026000`，与 `conflict_line_1` 地址一致，程序返回 0。

## 覆盖范围与限制

- 本场景覆盖 L1 miss、L2 hit 直返、L2 miss→MSHR→CHI、clean refill 和 GrantAck。
- 本场景不覆盖 L1 hit、dirty victim、`AcquirePerm`/NtoT、store miss、AMO、prefetch
  和 CMO。
- 没有通过 L1 tag array 内部端口直接证明具体 victim way；只证明 17 条同 set line
  的压力足以让目标行离开 L1，并由第二次 A 请求和 L1 misstrack 共同佐证。
- 当前 V3 构建未导出逐指令 commit 端；提交证据是 D/E 完成、程序返回值检查和最终
  GOOD TRAP。
- 本文不提供工具生成的 RTL line/branch coverage 数值。

## 相关文件

- 源程序：`l1miss_l2hit.S`
- 反汇编：`disassembly.txt`
- 波形：`l1miss_l2hit.fst`
- 构造分析：`CONSTRUCTION_ANALYSIS.md`
- RTL 代码覆盖分析：`RTL_CODE_ANALYSIS.md`
