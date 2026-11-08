# l1miss_l2miss_replace 波形分析报告

## 结论

**PASS，高置信度。** 程序先用 8 条同 set line 填满 L2 的 8 个 way，再访问第 9 条
目标 line。目标请求在 L1 miss、L2 目录 miss 并分配 MSHR；replacement policy 返回
有效 victim `0x80024000`（way 0，state `TRUNK`）。目标行通过 CHI
`ReadNotSharedDirty` 取回，victim 通过 CHI `WriteEvictOrEvict` 释放，目标
`GrantData`、`GrantAck` 和 victim eviction 都在波形中真实出现。

本场景覆盖的是 clean victim replacement，不覆盖 dirty victim 的
`WriteBackFull`、replacement retry 或 victim 仍被 L1 client 持有时所需的反向 Probe。

## 验证对象

- 波形：`l1miss_l2miss_replace.fst`
- 波形 SHA-256：`b96759f11e6fe8a70fa13bb163fcca2de3f4ebc5c5bbdad7ea8874a654e86db0`
- 仿真器：昆明湖 V3 `kmhv3-single`（XiangShan `6705b63685d301f8c0960f7cbcc8b964660dc58e`，
  XSCache `e9488aed486358e3e5984b531aab601f9b91a9c0`）
- 采样：`TOP.SimTop.clock` 上升沿
- 目标 cache line：`0x80124000`，line size 为 64 bytes
- 目标 load：PC `0x80000166`，指令 `ld t3, 0(t1)`
- 目标 L2 位置：slice `0`、set `0x40`、tag `0x10024`
- victim：`0x80024000`，slice `0`、set `0x40`、tag `0x10004`、way `0`
- 程序结果：`HIT GOOD TRAP at pc = 0x80000194`，返回值为 0

当前 V3 构建没有把 ROB 的逐指令 commit 端导出到波形，因此本文用“目标 load 的
D/E 完成 + 程序最终 GOOD TRAP 且返回值为 0”作为提交证据。

TileLink/CHI 字段只在对应握手成立时有效。本报告只把 `valid=1 && ready=1` 的
A、D、E、CHI 记录视为事务证据。

## 测试如何构造该场景

程序按指针链串行访问 8 条同 L2 set、不同 tag 的 fill line，填满 8-way L2 的 slice 0、
set `0x40`：

| 地址 | L2 tag | 用途 |
| --- | --- | --- |
| `0x80024000` | `0x10004` | `fill_line_1` |
| `0x80044000` | `0x10008` | `fill_line_2` |
| `0x80064000` | `0x1000c` | `fill_line_3` |
| `0x80084000` | `0x10010` | `fill_line_4` |
| `0x800a4000` | `0x10014` | `fill_line_5` |
| `0x800c4000` | `0x10018` | `fill_line_6` |
| `0x800e4000` | `0x1001c` | `fill_line_7` |
| `0x80104000` | `0x10020` | `fill_line_8` |
| `0x80124000` | `0x10024` | 第 9 条目标 line |

8 条 fill line 通过指针链保持严格访问顺序；第 9 条目标 load 又依赖链尾返回的零值，
不会在 set 填充完成前提前发射。目标 load 之后增加 512 次空转循环，让 refill 和
victim release 在程序退出前完成。

V3 的 L1 DCache 是 8-way、128 sets，L2 是 4 banks × 128 sets × 8 ways。8 条 fill
line 同时占满同一 L1 set 和同一 L2 set；第 9 条目标 line 在 L1、L2 都触发替换。

## RTL 判据

- DCache S2 miss 进入 MissQueue：
  `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:909-910`。
- MissQueue 对 load miss 选择 `AcquireBlock`：
  `MissQueue.scala:987-1003`。
- L2 目录 miss 与 MSHR 分配：
  `coupledL2/src/main/scala/coupledL2/MainPipe.scala:245-266,305-310`。
- MSHR 接收 replacement response 并记录 victim tag/way/meta：
  `coupledL2/src/main/scala/coupledL2/MSHR.scala:1258-1291`。
- MSHR 导出 `reqTag`、`metaTag`、`needsRepl`、`w_replResp`：
  `MSHR.scala:1315-1322,1336-1364`。
- MSHRCtl 按 `mshrId` 路由 replacement response：
  `coupledL2/src/main/scala/coupledL2/MSHRCtl.scala:127-151`。
- 目标行 NtoB 映射 `ReadNotSharedDirty`：
  `MSHR.scala:386-393`。
- victim release opcode 选择：
  `MSHR.scala:511-516`。

## 8 条 fill line 填满 L2

每条 fill line 在 L2 入口都是 `AcquireBlock NtoB`、目录 miss 并分配 MSHR，随后向
CHI/L3 发出 `ReadNotSharedDirty`：

| fill line | A 握手周期 | L2 `dir_hit` | `mshr_alloc` | CHI read 周期 |
| --- | ---: | ---: | ---: | ---: |
| 1 `0x80024000` | 1122 | 0 | 1 | 1126 |
| 2 `0x80044000` | 1175 | 0 | 1 | 1179 |
| 3 `0x80064000` | 1226 | 0 | 1 | 1230 |
| 4 `0x80084000` | 1282 | 0 | 1 | 1286 |
| 5 `0x800a4000` | 1333 | 0 | 1 | 1337 |
| 6 `0x800c4000` | 1384 | 0 | 1 | 1390 |
| 7 `0x800e4000` | 1437 | 0 | 1 | 1441 |
| 8 `0x80104000` | 1488 | 0 | 1 | 1492 |

8 条 fill line 全部完成后，set `0x40` 的 8 个 way 都是有效 line。

## 第 9 条目标 line：L2 miss + replacement

### 目标请求与 MSHR 分配

L2 slice 0 mainPipe：

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_taskFromArb_s2_bits_opcode` | 6 | 6 | 目标 AcquireBlock 进入 main pipe |
| `io_dirResp_s3_hit` | 0 | 0 | 目标目录 miss |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 1 | 1 | 分配 MSHR |
| 目标 A 握手周期 | — | 2093（`0x80124000`） | L1→L2 |
| s3 采样周期 | — | 2095 | 目录结果与 MSHR 分配 |

DCache missQueue 在 cycle 2095 分配 `io_misstrack_alloc_1_valid`，
`paddr=0x80124000`，与 L2 入口请求一致。

### replacement response 返回有效 clean victim

mainPipe 的 replacement response：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `io_replResp_bits_set` | `0x40` | 目标 set |
| `io_replResp_bits_tag` | `0x10004` | victim `0x80024000` |
| `io_replResp_bits_way` | `0` | victim way |
| `io_replResp_bits_meta_state` | `2 (TRUNK)` | victim 有效 |
| `io_replResp_bits_retry` | `0` | 非 retry |

MSHR 0 的状态在 cycle 2137 起变为：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `io_status_valid` | 1 | MSHR 有效 |
| `io_status_bits_reqTag` | `0x10024` | 目标 `0x80124000` |
| `io_status_bits_metaTag` | `0x10004` | victim `0x80024000` |
| `io_status_bits_needsRepl` | 1 | victim 尚未释放 |
| `io_msInfo_bits_w_replResp` | 1 | 已收到 replacement response |

`needsRepl` 在 cycle 2150 变为 0，表示 victim release 已经开始。

### 目标取数与 victim eviction

L2 slice 0 `txreq`：

| 事务 | 握手周期 | opcode | 地址 |
| --- | ---: | ---: | --- |
| 目标 `ReadNotSharedDirty` | 2097 | `38 (0x26)` | `0x80124000` |
| victim `WriteEvictOrEvict` | 2153 | `66 (0x42)` | `0x80024000` |

`WriteEvictOrEvict` 是 clean victim 的无数据释放；波形中没有出现
`WriteBackFull`（`0x4b`），与 victim `dirty=0` 一致。

### 目标数据返回与完成

DCache D/E：

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `auto_client_nodes_out_0_d_bits_opcode` | 5 | 5 | `GrantData` |
| `auto_client_nodes_out_0_d_bits_data`（首拍） | `0x12345678` | `0x12345678` | 目标行数据 |
| `auto_client_nodes_out_0_d_bits_denied` | 0 | 0 | 无错误 |
| `auto_client_nodes_out_0_d_bits_corrupt` | 0 | 0 | 无错误 |
| 两拍 D 握手周期 | — | 2138、2139 | 完整 line |
| E 握手周期 | — | 2139 | GrantAck |

完整链路：

```text
8 条同 set fill line（A 1122..1488）
  -> L2 set 0x40 的 8 个 way 全部有效

第 9 条目标 AcquireBlock（2093）
  -> L2 directory miss（2095）
  -> 分配 MSHR（2095）
  -> replacement response：victim 0x80024000、way 0、TRUNK（2095）
  -> MSHR needsRepl=1、w_replResp=1（2137）
  -> 目标 ReadNotSharedDirty（2097）
  -> 目标两拍 GrantData（2138、2139）+ GrantAck（2139）
  -> victim WriteEvictOrEvict（2153）
  -> 程序 HIT GOOD TRAP
```

## 否定证据与对照

- 目标 `dir_hit=0` 且 `mshr_alloc=1`，排除 L2 hit 直返。
- replacement response 返回的是有效 `TRUNK` victim，不是 `INVALID` 空 way。
- victim 释放使用 `WriteEvictOrEvict`，不是 `WriteBackFull`，证明本次是 clean victim。
- `needsRepl` 在 2137–2149 为 1、2150 变 0，随后 2153 出现 victim release，说明释放
  不是只更新 metadata，而是真实发出 CHI 事务。
- 目标 `GrantData` 在 victim release 之前完成，说明目标 load 的完成不依赖 victim
  writeback 完成；两者由 MSHR 状态分别跟踪。

## 覆盖范围与限制

- 本场景覆盖 clean victim replacement、目标 refill、victim
  `WriteEvictOrEvict` 和 MSHR 的 `needsRepl`/`w_replResp` 状态。
- 不覆盖 dirty `WriteBackFull`、replacement retry、tag error、victim 仍被 L1 client
  持有时的 rProbe。
- replacement policy 的内部逐语句决策未由波形覆盖，只确认其返回结果。
- 当前 V3 构建未导出逐指令 commit 端；提交证据是 D/E 完成、程序返回值检查和最终
  GOOD TRAP。
- 本文不提供工具生成的 RTL line/branch coverage 数值。

## 相关文件

- 源程序：`l1miss_l2miss_replace.S`
- 反汇编：`disassembly.txt`
- 波形：`l1miss_l2miss_replace.fst`
- 构造分析：`CONSTRUCTION_ANALYSIS.md`
- RTL 代码覆盖分析：`RTL_CODE_ANALYSIS.md`
