# l1miss_l2miss 波形分析报告

## 结论

**PASS，高置信度。** 目标 load 对 `0x80024000` 的访问在 L1 miss，随后在 L2 目录
miss，L2 分配 MSHR 并向 CHI/L3 请求数据；目标值 `0x12345678` 通过两拍无错误
`GrantData` 返回，L1 完成 GrantAck，目标 `ld` 最终提交。

## 验证对象

- 波形：`kmhv2-single/xs-env/nexus-am/apps/l1miss_l2miss/final.fst`
- 波形 SHA-256：`ad641ba2b62b627ddee98a99ac8bd1ffcbf05b26c325d3cdd63acccabefb0786`
- 采样：`TOP.SimTop.clock` 上升沿
- 目标地址：`0x80024000`
- 目标 load PC：`0x80000132`
- 目标数据：`0x12345678`
- L2 位置：slice 0、set `0x40`、tag `0x4001`

所有 TileLink/CHI 字段只在对应 `valid=1 && ready=1` 时参与分析。

## 硬件预期

测试程序只对冷启动的 `target_line` 执行一次 load。若场景符合预期：

1. L1 miss queue 应向 L2 发出目标地址的 `AcquireBlock NtoB`。
2. L2 main pipe 的目录结果应为 miss，并令 `need_mshr_s3=1`、分配 MSHR。
3. L2 应通过 CHI 发出目标地址的 `ReadNotSharedDirty`。
4. 下级数据应通过两拍 `GrantData` 返回，且 `denied=0`、`corrupt=0`。
5. L1 应发送 GrantAck，目标 `ld` 应提交并写 x6。

## Cycle 4311：L1 miss 请求

原始信号作用域：`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

| 原始信号名 | 预期值 | 实测值 | 证明意义 |
| --- | ---: | ---: | --- |
| `auto_inner_dcache_client_out_a_valid` | 1 | 1 | 请求有效 |
| `auto_inner_dcache_client_out_a_ready` | 1 | 1 | L2 接收请求 |
| `auto_inner_dcache_client_out_a_bits_opcode` | 6 | 6 | `AcquireBlock` |
| `auto_inner_dcache_client_out_a_bits_param` | 0 | 0 | `NtoB` |
| `auto_inner_dcache_client_out_a_bits_size` | 6 | 6 | 请求 64-byte cache line |
| `auto_inner_dcache_client_out_a_bits_source` | 0 | 0 | 与 D 响应关联 |
| `auto_inner_dcache_client_out_a_bits_address` | `0x80024000` | `0x80024000` | 目标行 |

真实 A-channel `AcquireBlock` 说明 load 未在 L1 命中；L1 hit 不会向 L2申请完整 line。

## Cycle 4314：L2 目录 miss

原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe`。

| 原始信号名 | 预期值 | 实测值 | 证明意义 |
| --- | ---: | ---: | --- |
| `task_s3_valid` | 1 | 1 | main pipe 正在处理请求 |
| `task_s3_bits_opcode` | 6 | 6 | 请求为 `AcquireBlock` |
| `task_s3_bits_set` | `0x40` | `0x40` | 目标 set |
| `task_s3_bits_tag` | `0x4001` | `0x4001` | 目标 tag |
| `io_dirResp_s3_valid` | 1 | 1 | 目录结果有效 |
| `io_dirResp_s3_bits_hit` | 0 | 0 | L2 tag miss |
| `need_mshr_s3_a` | 1 | 1 | A 请求需进入 miss 流程 |
| `need_mshr_s3` | 1 | 1 | 需要 MSHR |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 1 | 1 | 实际分配 MSHR |
| `sink_resp_s3_valid` | 0 | 0 | L2 不能直接返回数据 |

`io_dirResp_s3_bits_hit=0` 与 MSHR allocation 同时出现，直接证明该事务走 L2 miss
路径，而不是 L2 hit path。

## Cycle 4316：访问 CHI/L3

原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.Decoupled2LCredit_txreqLink`。

| 原始信号名 | 预期值 | 实测值 | 证明意义 |
| --- | ---: | ---: | --- |
| `io_in_valid` | 1 | 1 | CHI 请求有效 |
| `io_in_ready` | 1 | 1 | 下级接收请求 |
| `io_in_bits_addr` | `0x80024000` | `0x80024000` | 与目标行一致 |
| `io_in_bits_opcode` | `0x26` | `0x26` | `ReadNotSharedDirty` |
| `io_in_bits_txnID` | 可关联 | 0 | 下级事务 ID |

同地址 CHI read 是 L2 miss 的独立外部证据，排除“内部 miss 信号活动但数据仍由 L2
直接提供”的解释。

## Cycle 4357–4360：数据返回并被 L1 接收

原始信号作用域：`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

| Cycle | 原始信号 | 预期 | 实测 |
| ---: | --- | ---: | ---: |
| 4357 | `auto_inner_dcache_client_out_d_valid / auto_inner_dcache_client_out_d_ready` | `1/1` | `1/1` |
| 4357 | `auto_inner_dcache_client_out_d_bits_opcode` | 5 | 5 |
| 4357 | `auto_inner_dcache_client_out_d_bits_source` | 0 | 0 |
| 4357 | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | `0/0` | `0/0` |
| 4357 | `auto_inner_dcache_client_out_d_bits_data` | `0x12345678` | `0x12345678` |
| 4358 | `auto_inner_dcache_client_out_d_valid / auto_inner_dcache_client_out_d_ready` | `1/1` | `1/1` |
| 4358 | `auto_inner_dcache_client_out_d_bits_opcode` | 5 | 5 |
| 4358 | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | `0/0` | `0/0` |
| 4360 | `auto_inner_dcache_client_out_e_valid / auto_inner_dcache_client_out_e_ready` | `1/1` | `1/1` |
| 4360 | `auto_inner_dcache_client_out_e_bits_sink` | 768 | 768 |

两拍 `GrantData` 构成完整 64-byte line，首拍包含测试期望值，且没有协议错误；E-channel
确认说明 Grant 已被 L1 接收。

## Cycle 4378：目标 load 提交

原始信号作用域：`TOP.SimTop.endpoint.commit_3`。

| 原始信号名 | 预期值 | 实测值 |
| --- | ---: | ---: |
| `io_valid` | 1 | 1 |
| `io_bits_valid` | 1 | 1 |
| `io_bits_pc` | `0x80000132` | `0x80000132` |
| `io_bits_instr` | `0x0002b303` | `0x0002b303` |
| `io_bits_rfwen` | 1 | 1 |
| `io_bits_wdest` | 6 | 6 |

PC 和指令编码确认提交的是目标 `ld t1, 0(t0)`，不是同周期其他指令。

## 反证检查

- **不是 L1 hit：** cycle 4311 出现目标 `AcquireBlock`。
- **不是 L2 hit：** cycle 4314 目录 miss、分配 MSHR，cycle 4316 发出同地址 CHI read。
- **不是错误响应：** 两拍 D 均 `denied=0`、`corrupt=0`，随后 GrantAck 和目标 load commit。

## 最终证据链

```text
目标 AcquireBlock
  -> L1 miss
  -> L2 directory miss
  -> need_mshr_s3=1 + MSHR allocation
  -> CHI ReadNotSharedDirty
  -> 两拍无错误 GrantData
  -> GrantAck
  -> 目标 ld 提交
  => L1 miss、L2 miss，事务完成
```

## 分析输入

- 构造程序：`l1miss_l2miss.S`
- 反汇编：`disassembly.txt`
- 原始波形：`l1miss_l2miss.fst`
- 波形会话：`l1miss_l2miss.ron`
