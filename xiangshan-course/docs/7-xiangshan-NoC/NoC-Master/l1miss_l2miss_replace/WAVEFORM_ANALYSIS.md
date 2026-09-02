# l1miss_l2miss_replace 波形分析报告

## 结论

**PASS，高置信度。** 测试先以八条同 set cache line 填满 8-way L2，随后访问第九条
`0x80124000`。目标访问在 L1、L2 miss，L2 MSHR 记录目标 tag `0x4009` 和 victim tag
`0x4001`，replacement policy 选择 way 0；目标行从下级返回，同时 victim
`0x80024000` 以 CHI `WriteEvictOrEvict` 被淘汰，目标 load 最终提交。

## 验证对象

- 波形：`kmhv2-single/xs-env/nexus-am/apps/l1miss_l2miss_replace/final.fst`
- 波形 SHA-256：`fcecb64d3b3bdc8843c55076362d2cb0aaf105addef343ea1106869e78cbc910`
- 目标地址：`0x80124000`，slice 0、set `0x40`、tag `0x4009`
- 目标 load PC：`0x80000166`
- L2：8 ways

## 前置状态：八条同 set line

原始信号作用域：`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

| Cycle | `auto_inner_dcache_client_out_a_bits_address` | L2 tag | `auto_inner_dcache_client_out_a_valid / auto_inner_dcache_client_out_a_ready` | `auto_inner_dcache_client_out_a_bits_opcode` |
| ---: | --- | --- | --- | ---: |
| 4309 | `0x80024000` | `0x4001` | `1/1` | 6 |
| 4364 | `0x80044000` | `0x4002` | `1/1` | 6 |
| 4419 | `0x80064000` | `0x4003` | `1/1` | 6 |
| 4474 | `0x80084000` | `0x4004` | `1/1` | 6 |
| 4529 | `0x800a4000` | `0x4005` | `1/1` | 6 |
| 4584 | `0x800c4000` | `0x4006` | `1/1` | 6 |
| 4645 | `0x800e4000` | `0x4007` | `1/1` | 6 |
| 4706 | `0x80104000` | `0x4008` | `1/1` | 6 |

八条地址均映射到 slice 0、set `0x40`，并通过真实 A-channel `AcquireBlock` 进入缓存
层次，因此为第九条 line 构造了有效 victim 条件。

## Cycle 5375–5378：第九条目标 line 在 L1/L2 miss

L1 DCache 作用域：`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

| 原始信号名 | 预期值 | 实测值 |
| --- | ---: | ---: |
| `auto_inner_dcache_client_out_a_valid` | 1 | 1 |
| `auto_inner_dcache_client_out_a_ready` | 1 | 1 |
| `auto_inner_dcache_client_out_a_bits_opcode` | 6 | 6 |
| `auto_inner_dcache_client_out_a_bits_address` | `0x80124000` | `0x80124000` |

目标 `AcquireBlock` 证明 L1 miss。

L2 main pipe 作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe`。

| 原始信号名 | 预期值 | 实测值 | 证明意义 |
| --- | ---: | ---: | --- |
| `task_s3_bits_set` | `0x40` | `0x40` | 目标 set |
| `task_s3_bits_tag` | `0x4009` | `0x4009` | 目标 tag |
| `io_dirResp_s3_valid` | 1 | 1 | 目录结果有效 |
| `io_dirResp_s3_bits_hit` | 0 | 0 | L2 miss |
| `need_mshr_s3` | 1 | 1 | 进入 miss 流程 |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 1 | 1 | 分配 MSHR |
| `sink_resp_s3_valid` | 0 | 0 | 不能直接响应 |


## Cycle 5379–5439：MSHR 记录 replacement 状态

原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mshrCtl.mshrs_0`。

| Cycle | 原始信号名 | 实测值 | 解释 |
| ---: | --- | ---: | --- |
| 5379 | `io_status_valid` | 1 | MSHR 0 有效 |
| 5379 | `io_status_bits_set` | `0x40` | 目标 set |
| 5379 | `io_status_bits_reqTag` | `0x4009` | 新目标 tag |
| 5379 | `io_status_bits_metaTag` | `0x4001` | 候选 victim tag |
| 5379 | `io_status_bits_is_miss` | 1 | 目标为 L2 miss |
| 5420 | `io_status_bits_needsRepl` | 1 | replacement 阶段有效 |
| 5420 | `io_msInfo_bits_w_replResp` | 1 | replacement response 已被 MSHR 接收 |
| 5420 | `state_w_replResp` | 1 | replacement response 已登记到状态寄存器 |

`reqTag` 与 `metaTag` 不同，并且后续 replacement response 返回 `meta_state=3` 的有效
victim，明确表示新行 `0x4009` 将占用旧行 `0x4001` 的位置，而不只是普通空 way refill。

## Cycle 5419：replacement policy 选中 victim

原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mshrCtl`。

| 原始信号名 | 实测值 | 解释 |
| --- | ---: | --- |
| `io_replResp_valid` | 1 | replacement response 有效 |
| `io_replResp_bits_tag` | `0x4001` | victim 为第一条 fill line |
| `io_replResp_bits_way` | 0 | victim way 0 |
| `io_replResp_bits_meta_state` | 3 | victim line 有效 |
| `io_replResp_bits_meta_dirty` | 0 | victim 为 clean line |
| `io_replResp_bits_retry` | 0 | replacement 无需重试 |
| `io_replResp_bits_mshrId` | 0 | 对应目标 MSHR 0 |


## 目标 refill 与 victim eviction

CHI TxReq 作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.Decoupled2LCredit_txreqLink`。

| Cycle | `io_in_bits_addr` | `io_in_bits_opcode` | 解释 |
| ---: | --- | ---: | --- |
| 5380 | `0x80124000` | `0x26` | 为新目标行执行 `ReadNotSharedDirty` |
| 5425 | `0x80024000` | `0x42` | 对 clean victim 执行 `WriteEvictOrEvict` |

两个事务的地址分别对应新 tag `0x4009` 和 victim tag `0x4001`。opcode `0x42` 是
`WriteEvictOrEvict`，cycle 5425 证明 eviction 请求真实发出，而不只是 replacement
metadata 变化。

Cycle 5439，MSHR 0 的 `state_w_releaseack=1`。该状态由收到 eviction 对应的 CHI
`Comp`/`CompDBIDResp` 后置位，证明下级已经完成该 eviction，而不只是接收请求。

## Cycle 5421–5430：目标数据完成

L1 DCache 作用域：`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

| Cycle | 原始信号 | 实测值 |
| ---: | --- | ---: |
| 5421 | `auto_inner_dcache_client_out_d_bits_opcode` | 5 |
| 5421 | `auto_inner_dcache_client_out_d_bits_data` | `0x12345678` |
| 5421 | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | `0/0` |
| 5422 | `auto_inner_dcache_client_out_d_bits_opcode` | 5 |
| 5422 | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | `0/0` |
| 5424 | `auto_inner_dcache_client_out_e_valid / auto_inner_dcache_client_out_e_ready` | `1/1` |
| 5424 | `auto_inner_dcache_client_out_e_bits_sink` | 768 |


Commit 作用域：`TOP.SimTop.endpoint.commit`。

| 原始信号名 | 实测值 |
| --- | ---: |
| `io_valid / io_bits_valid` | `1/1` |
| `io_bits_pc` | `0x80000166` |
| `io_bits_instr` | `0x00033e03` |
| `io_bits_rfwen` | 1 |
| `io_bits_wdest` | 28 |

这与 `ld t3, 0(t1)` 完全一致。

## 反证检查

- **不是空 way refill：** replacement response 返回有效 victim tag/state/way，MSHR
  `needsRepl=1`。
- **不是只选择但未淘汰：** cycle 5425 对 victim 地址发出 `WriteEvictOrEvict`，cycle
  5439 收到对应 completion 并置位 `state_w_releaseack`。
- **不是 dirty writeback：** `io_replResp_bits_meta_dirty=0`，所以实测使用 clean eviction。
- **目标事务未被 replacement 阻断：** 两拍无错误 GrantData、GrantAck 和目标 load commit 均完成。

## 最终证据链

```text
八条同 set line 填满 8-way L2
  -> 第九条目标 AcquireBlock
  -> L2 directory miss + MSHR allocation
  -> MSHR reqTag=0x4009, metaTag=0x4001
  -> 目标 ReadNotSharedDirty
  -> replacement response: victim tag=0x4001, way=0, clean
  -> 目标 GrantData + GrantAck
  -> victim WriteEvictOrEvict
  -> 目标 load commit
  -> victim eviction CHI completion
  => L1/L2 miss，并发生真实 L2 victim replacement
```

## 分析输入

- 构造程序：`l1miss_l2miss_replace.S`
- 反汇编：`disassembly.txt`
- 原始波形：`l1miss_l2miss_replace.fst`
- 波形会话：`l1miss_l2miss_replace.ron`
