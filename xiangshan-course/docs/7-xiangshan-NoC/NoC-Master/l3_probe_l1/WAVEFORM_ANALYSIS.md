# l3_probe_l1 波形分析报告

## 结论

**PASS，高置信度。** core 0 先读取并持有目标 line `0x80001680`；core 1 随后读取
同一行，core 1 的 L2 目录 miss 并向 OpenLLC 发出 `ReadNotSharedDirty`。OpenLLC 的
client directory 检测到 RN0 持有 peer 副本，生成只指向 RN0 的
`SnpNotSharedDirty`。core 0 的 L2 收到 CHI snoop 后向 L1 DCache 发出 TileLink
`Probe(toB)`；L1 返回无数据 `ProbeAck`，L2 再向 OpenLLC 返回 `SnpResp`。OpenLLC
随后读取内存并向 core 1 返回 `CompData`；core 1 收到两拍 `GrantData`，数据为
`0x1122334455667788`，两个 hart 都最终 HIT GOOD TRAP。

## 验证对象

- 波形：`l3_probe_l1.fst`
- 波形 SHA-256：`6c948d17794de9c72b457a0cacc52d11b037441e5d295ef5ab85c29ad7cf6a1e`
- 仿真器：昆明湖 V3 `kmhv3-2c`（XiangShan `6705b63685d301f8c0960f7cbcc8b964660dc58e`，
  XSCache `e9488aed486358e3e5984b531aab601f9b91a9c0`）
- 采样：`TOP.SimTop.clock` 上升沿
- 目标地址：`0x80001680`，初值 `0x1122334455667788`
- core 0 目标 load PC：`0x80000190`
- core 1 目标 load PC：`0x80000154`
- 目标 L2 位置：两个核均为 slice `2`、set `0x16`、tag `0x10000`
- 程序结果：core 0 `HIT GOOD TRAP at pc = 0x800001f6`，
  core 1 `HIT GOOD TRAP at pc = 0x80000214`

当前 V3 构建没有把 ROB 的逐指令 commit 端导出到波形，因此本文用“目标 load 的
D/E 完成 + 两个 hart 最终 GOOD TRAP 且返回值为 0”作为提交证据。

CHI/TileLink 字段只在对应握手成立时有效。本报告只把 `valid=1 && ready=1` 的记录
视为事务证据。

## 测试如何构造该场景

程序运行在双核环境。core 0 先读取目标 line 并提交，然后通过独立 cache line 上的
同步变量 `hart0_ready`（`0x80001700`）通知 core 1；core 1 收到通知后再读取同一目标
line。两个 hart 使用 fence 和轮询建立明确的先后关系，避免 core 1 在 core 0 建立
副本前发起请求。

V3 的 L1 DCache 是 8-way、128 sets，L2 是 4 banks × 128 sets × 8 ways；OpenLLC
在本配置下只有 1 个 slice。目标地址 `0x80001680` 在 L2 的 slice 2、set `0x16`。

## RTL 判据

- OpenLLC 在 s3 形成 peer RN vector：
  `openLLC/src/main/scala/openLLC/MainPipe.scala:114`。
- shared read 在 LLC self miss 时触发 snoop：
  `MainPipe.scala:317-319`。
- snoop vector 和 opcode 选择：
  `MainPipe.scala:326-355`，其中 `ReadNotSharedDirty` 映射为
  `SnpNotSharedDirty`（`MainPipe.scala:351`）。
- `snp_s4.valid` 与 snoop 输出：
  `MainPipe.scala:362-363`。
- 普通内存 read 条件：
  `MainPipe.scala:451-454`。
- CoupledL2 把 snoop-to-B 转换成 pProbe：
  `coupledL2/src/main/scala/coupledL2/MainPipe.scala:287-303`。
- MSHR 构造 TileLink `Probe toB`：
  `coupledL2/src/main/scala/coupledL2/MSHR.scala:436-447`。
- MSHRCtl 的 SourceB 仲裁：
  `coupledL2/src/main/scala/coupledL2/MSHRCtl.scala:174-177`。
- DCache ProbeQueue 接收 B 通道：
  `src/main/scala/xiangshan/cache/dcache/mainpipe/Probe.scala:144-164`。
- ProbeEntry 送入 main pipe：
  `Probe.scala:93-108`。
- MainPipe 把 probe 送入 writeback：
  `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:816-818,1097-1108`。
- WritebackQueue 构造无数据/带数据 `ProbeAck`：
  `src/main/scala/xiangshan/cache/dcache/mainpipe/WritebackQueue.scala:226-270`。

## core 0 建立目标副本

core 0 的目标 load（PC `0x80000190`）在 L1 miss，向 L2 发出 `AcquireBlock NtoB`。
L2 此时已经持有目标行（`dir_hit=1`），直接返回数据：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| core 0 L2 `slices_2.io_in_a_bits_opcode` | 6 | `AcquireBlock` |
| core 0 L2 `slices_2.io_in_a_bits_address` | `0x80001680` | 目标行 |
| A 握手周期 | 1308 | L1→L2 |
| `io_dirResp_s3_hit` | 1 | L2 命中 |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 0 | 不分配 MSHR |
| DCache `auto_client_nodes_out_0_d_bits_data`（首拍） | `0x1122334455667788` | 目标值 |
| D/E 握手周期 | 1355、1356 | core 0 建立 L1 副本 |

core 0 的 L2 已经把目标行保留在自己的目录/data storage 中，因此 core 1 后续请求
能够被 OpenLLC 识别为 peer RN hit。

## core 1 请求触发 LLC peer snoop

core 1 的目标 load（PC `0x80000154`）在 L1 miss，L2 目录 miss，并向 OpenLLC 发出
`ReadNotSharedDirty`：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| core 1 L2 `slices_2.io_in_a_bits_opcode` | 6 | `AcquireBlock` |
| `io_in_a_bits_address` | `0x80001680` | 目标行 |
| A 握手周期 | 1605 | core 1 L1→L2 |
| `io_dirResp_s3_hit` | 0 | core 1 L2 目录 miss |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 1 | 分配 MSHR |
| core 1 L2 `txreq.io_out_bits_opcode` | `38 (0x26)` | `ReadNotSharedDirty` |
| core 1 L2 CHI 请求周期 | 1609 | L2→OpenLLC |

OpenLLC slice 0 在 cycle 1613 接收该请求：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `io_in_tx_req_bits_opcode` | `38 (0x26)` | `ReadNotSharedDirty` |
| `io_in_tx_req_bits_srcID` | `1` | 请求来自 RN1 |
| `io_in_tx_req_bits_txnID` | `1024` | core 1 事务 ID |
| `io_in_tx_req_bits_addr` | `0x80001680` | 目标行 |

OpenLLC mainPipe 在 s4 形成 snoop 任务：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `io_snoopTask_s4_valid` | 1（cycle 1617） | 产生 snoop |
| `io_snoopTask_s4_bits_snpVec_0` | 1 | 指向 RN0 |
| `io_snoopTask_s4_bits_snpVec_1` | 0 | 不指向 RN1 |
| `io_snoopTask_s4_bits_chiOpcode` | `4` | `SnpNotSharedDirty` |
| `io_snoopTask_s4_bits_txnID` | `81` | snoop 事务 ID |
| `io_dirResp_s3_self_hit` | 0 | LLC 自身未命中 |

OpenLLC 在 cycle 1617 通过 `txsnpUp` 发出 snoop：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `io_snp_valid` | 1 | snoop 有效 |
| `io_snp_ready` | 1 | 被 RN0 接收 |
| `io_snp_bits_opcode` | `4` | `SnpNotSharedDirty` |
| `io_snp_bits_txnID` | `81` | 与 snoop task 一致 |

## CHI snoop 转换为 TileLink Probe

core 0 的 L2 在 cycle 1622 收到 snoop：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| core 0 L2 `slices_2.rxsnp.io_rxsnp_valid` | 1 | RxSnp 有效 |
| `io_rxsnp_bits_opcode` | `4` | `SnpNotSharedDirty` |
| `io_rxsnp_bits_txnID` | `81` | 与 LLC snoop 一致 |
| `io_rxsnp_bits_addr` | `0x10001690` | CHI 地址编码 |

core 0 L1 DCache 收到 TileLink B 通道 Probe：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `auto_client_nodes_out_0_b_valid` | 1 | Probe 有效 |
| `auto_client_nodes_out_0_b_ready` | 1 | L1 接收 |
| `auto_client_nodes_out_0_b_bits_opcode` | 6 | `Probe` |
| `auto_client_nodes_out_0_b_bits_param` | 1 | `toB` |
| `auto_client_nodes_out_0_b_bits_address` | `0x80001680` | 目标行 |
| B 握手周期 | 1628 | L2→L1 |

core 0 L1 通过 C 通道返回无数据 `ProbeAck`：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `auto_client_nodes_out_0_c_valid` | 1 | ProbeAck 有效 |
| `auto_client_nodes_out_0_c_ready` | 1 | L2 接收 |
| `auto_client_nodes_out_0_c_bits_opcode` | 4 | `ProbeAck` |
| `auto_client_nodes_out_0_c_bits_address` | `0x80001680` | 同一 line |
| C 握手周期 | 1635 | L1→L2 |

`toB` 表示降权到共享状态，因此 core 0 可以保留共享副本，不需要返回
`ProbeAckData`（opcode 5）。

## CHI SnpResp 与 core 1 完成

core 0 的 L2 在 cycle 1641 向 OpenLLC 返回 `SnpResp`：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| core 0 L2 `slices_2.txrsp.io_out_bits_opcode` | `1` | `SnpResp` |
| `io_out_bits_txnID` | `81` | 与 snoop 一致 |

OpenLLC 在 cycle 1646 为目标行发出 `ReadNoSnp`：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `io_out_tx_req_bits_opcode` | `4` | `ReadNoSnp` |
| `io_out_tx_req_bits_addr` | `0x80001680` | 目标行 |
| `io_out_tx_req_bits_txnID` | `81` | 与 snoop 事务关联 |

随后 OpenLLC 通过 `txdatUp` 向 core 1 返回 `CompData`：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| `txdatUp.io_dat_valid` | 1（cycle 1667、1668） | 两拍数据 |
| `txdatUp.io_dat_bits_opcode` | `4` | `CompData` |
| `txdatUp.io_dat_bits_txnID` | `1024` | 与 core 1 请求一致 |

core 1 的 L1 收到两拍 `GrantData`：

| 原始信号名 | 波形实测 | 解释 |
| --- | ---: | --- |
| core 1 `auto_client_nodes_out_0_d_bits_opcode` | 5 | `GrantData` |
| `auto_client_nodes_out_0_d_bits_data`（首拍） | `0x1122334455667788` | 目标值 |
| `auto_client_nodes_out_0_d_bits_denied` | 0 | 无错误 |
| `auto_client_nodes_out_0_d_bits_corrupt` | 0 | 无错误 |
| D 握手周期 | 1678、1679 | 完整 line |
| E 握手周期 | 1679 | GrantAck |

完整链路：

```text
core 0 load（PC 0x80000190）
  -> core 0 L1 fill（D/E 1355、1356）
  -> core 0 持有目标副本

core 1 load 同一 line（PC 0x80000154）
  -> core 1 L2 AcquireBlock（1605）
  -> L2 目录 miss + MSHR（1607）
  -> CHI ReadNotSharedDirty（1609）
  -> OpenLLC 检测 peer RN0 hit（1617，snpVec=1/0）
  -> CHI SnpNotSharedDirty（1617，txnID 81）
  -> core 0 L2 RxSnp（1622）
  -> TileLink Probe(toB)（1628）
  -> core 0 L1 ProbeAck（1635）
  -> core 0 L2 SnpResp（1641，txnID 81）
  -> OpenLLC ReadNoSnp（1646）
  -> OpenLLC CompData（1667、1668，txnID 1024）
  -> core 1 两拍 GrantData + GrantAck（1678、1679）
  -> 两个 hart 均 HIT GOOD TRAP
```

## 否定证据与对照

- core 1 目标请求在 L2 目录 miss 并分配 MSHR，排除 core 1 本地命中。
- OpenLLC `snpVec=1/0` 且 `self_hit=0`，说明 snoop 只指向 RN0，而不是广播给两个 RN。
- core 0 的 B 通道 opcode 为 `Probe`、param 为 `toB`；C 通道 opcode 为 `ProbeAck`，
  不是 `ProbeAckData`，与 clean/shared 降权一致。
- core 0 的 `SnpResp` txnID 81 与 LLC snoop txnID 81 一致，证明跨层事务关联没有丢失。
- core 1 的 `CompData` txnID 1024 与 core 1 请求 txnID 1024 一致，数据为
  `0x1122334455667788`。

## 覆盖范围与限制

- 本场景覆盖 shared read 引发的 peer RN snoop、CHI snoop→TileLink Probe、
  `ProbeAck`、`SnpResp`、LLC `ReadNoSnp` 和 `CompData`。
- 不覆盖 `ProbeAckData`、toN invalidation、dirty owner、forwarded snoop、多 peer RN
  snoop vector 或 LLC replacement snoop。
- V3 OpenLLC 在本配置下只有 1 个 slice，因此报告中不再出现 V2 的 slice 2 说法。
- 当前 V3 构建未导出逐指令 commit 端；提交证据是 D/E 完成、程序返回值检查和最终
  GOOD TRAP。
- 本文不提供工具生成的 RTL line/branch coverage 数值。

## 相关文件

- 源程序：`scenario4_l3_probe_l1.c`
- 反汇编：`disassembly.txt`
- 波形：`l3_probe_l1.fst`
- 构造分析：`CONSTRUCTION_ANALYSIS.md`
- RTL 代码覆盖分析：`RTL_CODE_ANALYSIS.md`
