# l3_probe_l1 波形分析报告

## 结论

**PASS，高置信度。** core 0 首先读取并持有目标 line `0x80001680`；core 1 随后读取
同一 line。LLC slice 2 检测到 peer RN0 持有副本并生成 snoop，snoop 经 CHI 到达
core 0 L2，再转换为 L1 TileLink B `Probe`。core 0 L1 返回 `ProbeAck`，core 0 L2 以
相同 txnID 返回 CHI `SnpResp`；core 1 最终收到正确数据并提交目标 load。

## 验证对象

- 波形：`kmhv2-dual/.../scenario4_l3_probe_l1/final.fst`
- 波形 SHA-256：`1846e671dc649c0358214cc45883be15ce1c7fb80f2929bd703bd71732521a08`
- 匹配反汇编：`scenario4_l3_probe_l1/build/scenario4_l3_probe_l1-riscv64-xs.txt`
- 目标地址：`0x80001680`
- core 0 load PC：`0x80000190`
- core 1 load PC：`0x80000154`
- 目标值：`0x1122334455667788`

CHI snoop 地址字段不含低 3 位，报告按 `io_in_bits_addr << 3` 恢复完整物理地址。

## Core 0 首先持有目标行

原始信号作用域：`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

| Cycle | 原始信号 | 实测值 | 解释 |
| ---: | --- | ---: | --- |
| 4401 | `auto_inner_dcache_client_out_a_valid / auto_inner_dcache_client_out_a_ready` | `1/1` | 请求握手 |
| 4401 | `auto_inner_dcache_client_out_a_bits_opcode` | 6 | `AcquireBlock` |
| 4401 | `auto_inner_dcache_client_out_a_bits_address` | `0x80001680` | 目标 line |
| 4447 | `auto_inner_dcache_client_out_d_bits_opcode` | 5 | `GrantData` |
| 4447 | `auto_inner_dcache_client_out_d_bits_data` | `0x1122334455667788` | 目标值 |
| 4447–4448 | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | `0/0` | 无错误 |


Core 0 ROB 作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.inner_ctrlBlock.rob`。

`io_commits_commitValid_0=1` 且 `io_commits_info_0_debug_pc=0x80000190` 于 cycle 4452
成立，确认 core 0 的目标 load 已完成。

## Core 1 请求同一 line

原始信号作用域：`TOP.SimTop.cpu.l_soc.core_with_l2_1.core.memBlock`。

| Cycle | 原始信号名 | 实测值 |
| ---: | --- | ---: |
| 4706 | `auto_inner_dcache_client_out_a_valid / auto_inner_dcache_client_out_a_ready` | `1/1` |
| 4706 | `auto_inner_dcache_client_out_a_bits_opcode` | 6 |
| 4706 | `auto_inner_dcache_client_out_a_bits_param` | 0 |
| 4706 | `auto_inner_dcache_client_out_a_bits_size` | 6 |
| 4706 | `auto_inner_dcache_client_out_a_bits_address` | `0x80001680` |


## Cycle 4719：LLC 确认 peer RN 命中并生成 snoop

原始信号作用域：
`TOP.SimTop.cpu.l_soc.chi_openllc_opt.slices_2.mainPipe`。

| 原始信号名 | 实测值 | 证明意义 |
| --- | ---: | --- |
| `task_s4_valid` | 1 | 正在处理 core 1 请求 |
| `task_s4_bits_tag / task_s4_bits_set / task_s4_bits_bank` | `0x800 / 0x16 / 2` | 还原地址 `0x80001680` |
| `task_s4_bits_chiOpcode` | `0x26` | `ReadNotSharedDirty` |
| `task_s4_bits_srcID` | 1 | 请求来自 RN/core 1 |
| `peerRNs_hit_s4` | 1 | 其他 RN 持有该 line |
| `request_snoop_s4` | 1 | LLC 选择 request snoop 路径 |
| `io_snoopTask_s4_valid` | 1 | snoop task 有效 |
| `io_snoopTask_s4_bits_chiOpcode` | 4 | `SnpNotSharedDirty` |
| `io_snoopTask_s4_bits_snpVec_0 / io_snoopTask_s4_bits_snpVec_1` | `1/0` | 目标仅为 RN0 |
| `io_snoopTask_s4_bits_txnID` | 1041 | 后续 snoop 关联 ID |

这些值证明后续 probe 是由 core 1 的目标请求和 peer RN 命中触发，而不是无关后台 snoop。

## Cycle 4720–4724：CHI snoop 到达 core 0 L2

LLC TxSnp 作用域：
`TOP.SimTop.cpu.l_soc.chi_openllc_opt.rnLinkMonitor.Decoupled2LCredit_txsnp`。

| 原始信号名 | 实测值 |
| --- | ---: |
| `io_in_valid / io_in_ready` | `1/1` |
| `io_in_bits_addr` | `0x100002d0` |
| `io_in_bits_addr << 3` | `0x80001680` |
| `io_in_bits_opcode` | 4 |
| `io_in_bits_txnID` | 1041 |


Core 0 L2 RxSnp 作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor`。

Cycle 4724 的 `io_in_rx_snp_valid / io_in_rx_snp_ready=1/1`，地址恢复后仍为
`0x80001680`，opcode 为 4，txnID 为 1041。跨 LLC→L2 边界地址、opcode 和事务 ID
完全一致。

## Cycle 4730–4739：Core 0 L1 Probe/ProbeAck

原始信号作用域：`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

| Cycle | 原始信号名 | 实测值 | 解释 |
| ---: | --- | ---: | --- |
| 4730 | `auto_inner_dcache_client_out_b_valid / auto_inner_dcache_client_out_b_ready` | `1/1` | Probe 握手 |
| 4730 | `auto_inner_dcache_client_out_b_bits_opcode` | 6 | TileLink `Probe` |
| 4730 | `auto_inner_dcache_client_out_b_bits_param` | 1 | 降权到 `toB` |
| 4730 | `auto_inner_dcache_client_out_b_bits_address` | `0x80001680` | 目标 line |
| 4739 | `auto_inner_dcache_client_out_c_valid / auto_inner_dcache_client_out_c_ready` | `1/1` | 响应握手 |
| 4739 | `auto_inner_dcache_client_out_c_bits_opcode` | 4 | `ProbeAck` |
| 4739 | `auto_inner_dcache_client_out_c_bits_address` | `0x80001680` | 同一 line |
| 4739 | `auto_inner_dcache_client_out_c_bits_corrupt` | 0 | 响应无错误 |

实测是 `ProbeAck` 而非 `ProbeAckData`。对于 `toB` 降权，core 0 保留共享副本，不需要
上传数据，这是合法响应。

## Cycle 4745：Core 0 L2 返回 CHI SnpResp

原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.Decoupled2LCredit_txrspLink`。

| 原始信号名 | 实测值 |
| --- | ---: |
| `io_in_valid / io_in_ready` | `1/1` |
| `io_in_bits_opcode` | 1 |
| `io_in_bits_txnID` | 1041 |
| `io_in_bits_dbID` | 1041 |

CHI opcode 1 是 `SnpResp`，txnID 与 LLC TxSnp 的 1041 一致，完成 snoop 往返关联。

## Cycle 4782–4787：Core 1 得到数据并提交

原始信号作用域：`TOP.SimTop.cpu.l_soc.core_with_l2_1.core.memBlock`。

| Cycle | 原始信号名 | 实测值 |
| ---: | --- | ---: |
| 4782 | `auto_inner_dcache_client_out_d_valid / auto_inner_dcache_client_out_d_ready` | `1/1` |
| 4782 | `auto_inner_dcache_client_out_d_bits_opcode` | 5 |
| 4782 | `auto_inner_dcache_client_out_d_bits_data` | `0x1122334455667788` |
| 4782–4783 | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | `0/0` |
| 4785 | `auto_inner_dcache_client_out_e_valid / auto_inner_dcache_client_out_e_ready` | `1/1` |
| 4785 | `auto_inner_dcache_client_out_e_bits_sink` | 256 |


Core 1 ROB 作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2_1.core.backend.inner_ctrlBlock.rob`。

Cycle 4787 的 `io_commits_commitValid_0=1` 且
`io_commits_info_0_debug_pc=0x80000154`，确认 core 1 目标 load 完成。

## 反证检查

- **不是无关 snoop：** LLC 同周期处理目标地址的 core 1 请求，`peerRNs_hit_s4=1`，且
  snoop vector 只选择 RN0。
- **没有丢失跨层关联：** LLC TxSnp、core 0 L2 RxSnp、core 0 L2 TxRsp 的 txnID 都为
  1041。
- **L1 确实参与一致性：** core 0 的 B/C 通道分别出现目标地址 Probe 和 ProbeAck。
- **请求 core 正常完成：** core 1 收到正确目标值、无错误 GrantData、GrantAck，并提交
  目标 load。

## 最终证据链

```text
core 0 AcquireBlock -> 得到目标值 -> load commit
core 1 AcquireBlock 同一 line
  -> LLC peerRNs_hit_s4=1
  -> LLC 为 RN0 生成 SnpNotSharedDirty, txnID=1041
  -> core 0 L2 接收同 txnID RxSnp
  -> core 0 L1 B Probe
  -> core 0 L1 C ProbeAck
  -> core 0 L2 CHI SnpResp, txnID=1041
  -> core 1 两拍正确 GrantData + GrantAck
  -> core 1 load commit
  => L3/LLC 正确 probe core 0 的 L1，一致性事务完成
```

## 分析输入

- 构造程序：`scenario4_l3_probe_l1.c`
- 反汇编：`disassembly.txt`
- 原始波形：`l3_probe_l1.fst`
- 波形会话：`l3_probe_l1.ron`
