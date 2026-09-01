# l1miss_l2hit 波形分析报告

## 结论

**PASS，高置信度。** 波形符合测试期望的硬件行为：目标行第一次访问在 L1、L2
均 miss，并从 CHI/L3 取回后装入 L2；六次同 set 冲突访问使目标行离开 4-way L1；
第二次访问因此在 L1 miss，但在 8-way L2 目录命中，由 L2 直接返回两拍
`GrantData`，没有再次访问 CHI/L3，目标 `ld` 最终提交。

下面的硬件解释来自测试程序、反汇编、TileLink 定义和 CoupledL2 RTL，再逐项与
波形实测值核对。

## 验证对象

- 波形：`kmhv2-single/xs-env/nexus-am/apps/l1miss_l2hit/final.fst`
- 波形 SHA-256：`8a801d44240f3998383e0890ebc194d8e3ad871e2e7d756e2e73078a8dec4298`
- 采样：`TOP.SimTop.clock` 上升沿
- 目标 cache line：`0x80024000`，line size 为 64 bytes
- 目标 reload 指令：PC `0x8000016c`，指令 `0x0002b303`，即 `ld t1, 0(t0)`
- 目标 L2 位置：slice `0`、set `0x40`、tag `0x4001`

TileLink 通道字段只在对应握手成立时有效。本报告只把 `valid=1 && ready=1` 的 A、D、E、
CHI 记录视为事务证据；其他周期总线上的残留值不参与判断。

## 测试如何构造该场景

测试程序先加载 `target_line`，然后按指针链加载六条相隔 `128 KiB` 的冲突行，最后再次
加载 `target_line`。反汇编确认第一次和第二次目标 load 的地址均为 `0x80024000`，第二次
load 位于 PC `0x8000016c`。

本配置的 L1 DCache 为 `64 KiB / 256 sets = 4 ways`，L2 为 8 ways。目标行和六条冲突行
间隔 `128 KiB`，低位 set index 相同。因此，在目标行之后再放入六条同 set 行，足以超过
L1 的 4-way 容量；但总共只有七条相关 cache line，尚未超过 L2 的 8-way 容量。

由此得到可证伪的硬件预期：

1. 第一次目标 load 应产生 L1→L2 `AcquireBlock`；因为 L2 尚无该行，L2 目录应 miss、
   分配 MSHR，并向 CHI/L3 发请求。
2. 六条冲突行应在第一次目标访问之后、第二次目标访问之前真实进入 L1→L2 接口。
3. 第二次目标 load 若确实 L1 miss，应再次产生目标地址 `AcquireBlock`。
4. 该请求若 L2 hit，L2 目录应返回 `io_dirResp_s3_bits_hit=1`；当现有权限足以响应时，
   RTL应令 `need_mshr_s3=0`、`io_toMSHRCtl_mshr_alloc_s3_valid=0`、
   `sink_resp_s3_valid=1`，直接生成 `GrantData`。
5. L2 hit 不应向 CHI/L3 再发同地址请求；D 通道应返回无错误的完整 cache line，随后
   L1 在 E 通道确认 Grant，目标 load 最终提交。

## RTL 判据

TileLink 定义规定：A opcode `6` 是 `AcquireBlock`，A param `0` 是 `NtoB`，D opcode
`5` 是 `GrantData`。因此 `A fire + opcode=6 + address=目标行` 表示上层 cache 因没有
可用副本而请求整条 cache line，而不是普通已命中的 load。

CoupledL2 main pipe 的关键分支是：

- 目录 miss 时，`need_acquire_s3_a=1`，进而 `need_mshr_s3=1`，
  `io_toMSHRCtl_mshr_alloc_s3_valid=1`；请求必须进入 miss 处理流程。
- 目录 hit 且当前权限足以满足 `AcquireBlock NtoB` 时，`need_mshr_s3=0`；RTL通过
  `sink_resp_s3_valid=1` 直接生成 `GrantData`，并从 L2 data storage 读取数据。

所以，仅看到 `io_dirResp_s3_bits_hit=1` 还不够。必须同时看到
`need_mshr_s3=0`、`io_toMSHRCtl_mshr_alloc_s3_valid=0` 和
`sink_resp_s3_valid=1`，并以“没有目标 CHI 请求”作为独立反证，才足以确认 L2 hit 路径。

## 第一次目标访问：建立 L2 副本

### Cycle 4309：L1 发出目标行请求

本节 L1 DCache 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

硬件预期：第一次 load 访问冷 cache line，L1 miss queue 应通过 TileLink A 请求整条
64-byte cache line。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `auto_inner_dcache_client_out_a_valid` | 1 | 1 | 请求有效 |
| `auto_inner_dcache_client_out_a_ready` | 1 | 1 | L2 接收请求，A fire 成立 |
| `auto_inner_dcache_client_out_a_bits_opcode` | 6 | 6 | `AcquireBlock` |
| `auto_inner_dcache_client_out_a_bits_param` | 0 | 0 | `NtoB`，请求只读共享权限 |
| `auto_inner_dcache_client_out_a_bits_size` | 6 | 6 | `2^6 = 64 bytes` |
| `auto_inner_dcache_client_out_a_bits_source` | 0 | 0 | 后续响应关联 ID |
| `auto_inner_dcache_client_out_a_bits_address` | `0x80024000` | `0x80024000` | 目标 cache line |

波形与预期完全一致。原始信号
`auto_inner_dcache_client_out_a_valid=1` 且
`auto_inner_dcache_client_out_a_ready=1`，因此 A 通道出现真实 `AcquireBlock`，证明第一次
目标 load 在 L1 未命中。

### Cycle 4312：L2 目录 miss 并分配 MSHR

本节 L2 main pipe 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe`。

目标地址映射到 slice 0、set `0x40`、tag `0x4001`。硬件预期是目录找不到该 tag，
因此进入 miss 路径。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `task_s3_valid` | 1 | 1 | main pipe 正在处理该请求 |
| `task_s3_bits_opcode` | 6 | 6 | 请求仍是 `AcquireBlock` |
| `task_s3_bits_set` | `0x40` | `0x40` | 匹配目标 set |
| `task_s3_bits_tag` | `0x4001` | `0x4001` | 匹配目标 tag |
| `io_dirResp_s3_valid` | 1 | 1 | 目录查询结果有效 |
| `io_dirResp_s3_bits_hit` | 0 | 0 | L2 中没有目标行 |
| `need_mshr_s3` | 1 | 1 | 不能由 hit path 直接响应 |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 1 | 1 | 为该 miss 分配状态 |
| `sink_resp_s3_valid` | 0 | 0 | L2 此时不能直接返回数据 |

这组信号完整命中 RTL 的 L2 miss 分支，不只是一个全局 miss 脉冲。

### Cycle 4314：L2 向 CHI/L3 请求目标行

本节 CHI TxReq 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.linkMonitor.Decoupled2LCredit_txreqLink`。

硬件预期：L2 miss 分配 MSHR 后，应向下一级请求同一 cache line。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_in_valid` | 1 | 1 | 下级请求有效 |
| `io_in_ready` | 1 | 1 | 请求被接收 |
| `io_in_bits_addr` | `0x80024000` | `0x80024000` | 与目标行一致 |
| `io_in_bits_opcode` | 读取请求 | `38` | KMHv2 发出的 CHI read 请求 |
| `io_in_bits_txnID` | 可关联 | 0 | 下级事务 ID |

L2 确实向下一级请求目标行，验证了第一次访问不是 L2 hit。

### Cycle 4355–4356：目标行返回并进入 L1

本节 L1 DCache 原始信号作用域仍为：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

64-byte cache line 通过 256-bit D 通道分两拍返回。

| Cycle | `auto_inner_dcache_client_out_d_valid / auto_inner_dcache_client_out_d_ready` | `auto_inner_dcache_client_out_d_bits_opcode` | `auto_inner_dcache_client_out_d_bits_source` | `auto_inner_dcache_client_out_d_bits_sink` | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | 数据 |
| ---: | --- | ---: | ---: | ---: | --- | --- |
| 4355 | `1/1` | 5 | 0 | 768 | `0/0` | 低 256 bits，含 `0x80044000` |
| 4356 | `1/1` | 5 | 0 | 768 | `0/0` | 高 256 bits |

两拍均为 `GrantData`，source 与 cycle 4309 的请求一致，并且没有 denied/corrupt。第一拍
数据包含程序在 `target_line` 中存放的 `conflict_line_1 = 0x80044000`。这证明目标行已成功
从下级返回并装入 cache 层次，而不是只有请求没有完成。

## 冲突访问：构造 L1 淘汰条件

第一次目标 refill 后，波形中依次出现六次真实 A-channel `AcquireBlock`：

| Cycle | 地址 | 相对目标地址 | `auto_inner_dcache_client_out_a_valid / auto_inner_dcache_client_out_a_ready` | `auto_inner_dcache_client_out_a_bits_opcode / auto_inner_dcache_client_out_a_bits_param / auto_inner_dcache_client_out_a_bits_size` |
| ---: | --- | ---: | --- | --- |
| 4365 | `0x80044000` | `+128 KiB` | `1/1` | `6/0/6` |
| 4420 | `0x80064000` | `+256 KiB` | `1/1` | `6/0/6` |
| 4475 | `0x80084000` | `+384 KiB` | `1/1` | `6/0/6` |
| 4530 | `0x800a4000` | `+512 KiB` | `1/1` | `6/0/6` |
| 4585 | `0x800c4000` | `+640 KiB` | `1/1` | `6/0/6` |
| 4646 | `0x800e4000` | `+768 KiB` | `1/1` | `6/0/6` |

这些地址正是测试程序定义的六条同 set pointer-chain 访问，且全部发生在第一次目标 refill
之后、第二次目标请求之前。它们提供了使 4-way L1 淘汰目标行的实际访问压力，同时相关
line 总数为 7，没有填满 8-way L2。

## 第二次目标访问：验证 L1 miss、L2 hit

### Cycle 5302：L1 再次请求目标行

本节 L1 DCache 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

硬件预期：如果目标行已从 L1 淘汰，PC `0x8000016c` 的 reload 必须再次从 L1 miss
queue 发出 `AcquireBlock`。如果这是 L1 hit，则不会出现该 A-channel 请求。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `auto_inner_dcache_client_out_a_valid` | 1 | 1 | reload 产生请求 |
| `auto_inner_dcache_client_out_a_ready` | 1 | 1 | L2 接收请求 |
| `auto_inner_dcache_client_out_a_bits_opcode` | 6 | 6 | `AcquireBlock` |
| `auto_inner_dcache_client_out_a_bits_param` | 0 | 0 | `NtoB` |
| `auto_inner_dcache_client_out_a_bits_size` | 6 | 6 | 请求完整 64-byte line |
| `auto_inner_dcache_client_out_a_bits_source` | 0 | 0 | 与后续 D 响应关联 |
| `auto_inner_dcache_client_out_a_bits_address` | `0x80024000` | `0x80024000` | 正是目标行 |

因此原始信号 `auto_inner_dcache_client_out_a_valid=1` 且
`auto_inner_dcache_client_out_a_ready=1`，第二次目标访问明确是 L1 miss，而不是从程序
顺序推测出的 miss。

### Cycle 5305：L2 目录命中并选择直接响应路径

本节 L2 main pipe 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.l2top.inner_l2cache.slices_0.mainPipe`。

硬件预期：目标行仍在 8-way L2，目录查询应命中。对 `AcquireBlock NtoB`，现有 L2
权限足以直接返回数据，因此不应分配 MSHR，而应在 main pipe 生成 `GrantData`。

| 原始信号名 | 预期值 | 波形实测 | 为什么重要 |
| --- | ---: | ---: | --- |
| `task_s3_valid` | 1 | 1 | main pipe 正在处理该请求 |
| `task_s3_bits_opcode` | 6 | 6 | 与 cycle 5302 的 Acquire 类型一致 |
| `task_s3_bits_set` | `0x40` | `0x40` | 目标 set |
| `task_s3_bits_tag` | `0x4001` | `0x4001` | 目标 tag |
| `io_dirResp_s3_valid` | 1 | 1 | 目录结果有效 |
| `io_dirResp_s3_bits_hit` | 1 | 1 | 直接证明 L2 tag 命中 |
| `need_mshr_s3_a` | 0 | 0 | A 请求不需要下级 miss 流程 |
| `need_mshr_s3` | 0 | 0 | 整体无需 MSHR |
| `io_toMSHRCtl_mshr_alloc_s3_valid` | 0 | 0 | 没有为该请求分配 miss 状态 |
| `sink_resp_s3_valid` | 1 | 1 | L2 hit path 当场生成响应 |
| `sink_resp_s3_bits_opcode` | 5 | 5 | 响应是 `GrantData` |

这组值与第一次访问形成直接对照：第一次
`io_dirResp_s3_bits_hit=0 / need_mshr_s3=1 / io_toMSHRCtl_mshr_alloc_s3_valid=1`；
第二次 `io_dirResp_s3_bits_hit=1 / need_mshr_s3=0 /
io_toMSHRCtl_mshr_alloc_s3_valid=0 / sink_resp_s3_valid=1`。它明确区分了 L2 miss 和
L2 hit 两条 RTL 路径。

### Cycle 5309–5310：L2 返回完整且无错误的数据

本节 L1 DCache 原始信号作用域：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

硬件预期：L2 hit path 从 data storage 读取 cache line，通过 D 通道分两拍返回；两拍
都必须成功握手，source 应与 A 请求一致，denied/corrupt 必须为 0。

| Cycle | `auto_inner_dcache_client_out_d_valid / auto_inner_dcache_client_out_d_ready` | `auto_inner_dcache_client_out_d_bits_opcode` | `auto_inner_dcache_client_out_d_bits_source` | `auto_inner_dcache_client_out_d_bits_sink` | `auto_inner_dcache_client_out_d_bits_size` | `auto_inner_dcache_client_out_d_bits_denied / auto_inner_dcache_client_out_d_bits_corrupt` | 数据 |
| ---: | --- | ---: | ---: | ---: | ---: | --- | --- |
| 5309 | `1/1` | 5 | 0 | 768 | 6 | `0/0` | 低 256 bits，含 `0x80044000` |
| 5310 | `1/1` | 5 | 0 | 768 | 6 | `0/0` | 高 256 bits |

两拍 `GrantData` 的 source 与 cycle 5302 的 A source 都是 0，证明这是对应请求的响应；
sink 两拍一致，且没有协议错误。第一拍仍包含目标行应存储的 `0x80044000`。

### Cycle 5312：L1 确认接收 Grant

本节 L1 DCache 原始信号作用域仍为：
`TOP.SimTop.cpu.l_soc.core_with_l2.core.memBlock`。

硬件预期：Grant 类响应完成后，L1 应在 E 通道用相同 sink ID 确认。

| 原始信号名 | 预期值 | 波形实测 |
| --- | ---: | ---: |
| `auto_inner_dcache_client_out_e_valid` | 1 | 1 |
| `auto_inner_dcache_client_out_e_ready` | 1 | 1 |
| `auto_inner_dcache_client_out_e_bits_sink` | 768 | 768 |

E-channel 握手确认 cycle 5309–5310 的 Grant 已被 L1 接收，而不是响应停留在接口上。

### Cycle 5302–5318：没有同地址 CHI/L3 请求

硬件预期：L2 hit 不需要访问下一级。Wavekit 在第二次 A 请求 cycle 5302 到目标 load
提交后的 cycle 5319 半开窗口内，按
`io_in_valid && io_in_ready` 和 64-byte 对齐地址
`0x80024000` 搜索，匹配数为 **0**。

这项否定证据独立排除了“目录信号看起来 hit，但实际仍向下一级取数”的解释。相同查询
在第一次访问中能找到 cycle 4314 的目标请求，说明查询路径和过滤条件本身有效。

### Cycle 5318：目标 load 提交

本节 commit 原始信号作用域：`TOP.SimTop.endpoint.commit`。

硬件预期：数据返回并被 L1 接收后，目标 reload 指令应正常提交并写入 `t1`（x6）。

| 原始信号名 | 预期值 | 波形实测 | 解释 |
| --- | ---: | ---: | --- |
| `io_valid` | 1 | 1 | commit 记录有效 |
| `io_bits_valid` | 1 | 1 | 指令槽有效 |
| `io_bits_pc` | `0x8000016c` | `0x8000016c` | 目标 reload PC |
| `io_bits_instr` | `0x0002b303` | `0x0002b303` | `ld t1, 0(t0)` |
| `io_bits_rfwen` | 1 | 1 | 写整数寄存器 |
| `io_bits_wdest` | 6 | 6 | 写 `t1` / x6 |

这证明被分析的 cache 事务确实完成了测试中的目标 load，而非旁路或未退休请求。

## 反证检查

### 为什么不是 L1 hit

L1 hit 不会通过 miss queue 向 L2 发送完整 line 的 `AcquireBlock`。Cycle 5302 出现了
`auto_inner_dcache_client_out_a_valid=1`、
`auto_inner_dcache_client_out_a_ready=1`、
`auto_inner_dcache_client_out_a_bits_opcode=6` 且
`auto_inner_dcache_client_out_a_bits_address=0x80024000`，直接排除 L1 hit。

### 为什么不是 L2 miss

L2 miss 应表现为 `io_dirResp_s3_bits_hit=0`、`need_mshr_s3=1`、
`io_toMSHRCtl_mshr_alloc_s3_valid=1`，并产生目标地址 CHI 请求；第一次访问正是这个波形。
第二次访问却同时满足 `io_dirResp_s3_bits_hit=1`、`need_mshr_s3=0`、
`io_toMSHRCtl_mshr_alloc_s3_valid=0`、直接 `sink_resp_s3_bits_opcode=GrantData`，且目标
CHI 请求数为 0，因此排除 L2 miss。

### 为什么不是错误或未完成响应

原始信号 `auto_inner_dcache_client_out_d_valid` 和
`auto_inner_dcache_client_out_d_ready` 两拍都为 1，
`auto_inner_dcache_client_out_d_bits_denied` 和
`auto_inner_dcache_client_out_d_bits_corrupt` 两拍都为 0，随后 E 通道
`auto_inner_dcache_client_out_e_bits_sink=768` 完成确认，
目标 `ld` 最终提交并写 x6。因而不是 denied、corrupt、悬挂或被取消的事务。

## 最终证据链

```text
第一次目标 AcquireBlock
  -> `io_dirResp_s3_bits_hit=0`
  -> `need_mshr_s3=1`, `io_toMSHRCtl_mshr_alloc_s3_valid=1`
  -> 目标 CHI 请求
  -> 两拍无错误 GrantData
  -> 目标行建立在缓存层次中

六条同 set AcquireBlock
  -> 超过 4-way L1 容量
  -> 未超过 8-way L2 容量

第二次目标 AcquireBlock
  -> 证明 L1 miss
  -> `io_dirResp_s3_bits_hit=1`
  -> `need_mshr_s3=0`, `io_toMSHRCtl_mshr_alloc_s3_valid=0`
  -> `sink_resp_s3_bits_opcode=5`，main pipe 直接生成 GrantData
  -> 两拍无错误返回 + E GrantAck
  -> 无目标 CHI 请求
  -> 目标 ld 提交
  => L1 miss、L2 hit
```

## 限制

- 本报告证明的是目标 cache line 对应事务，不把同期取指、预取或其他地址流量计入结论。
- “六条冲突行使目标行离开 L1”主要由 4-way 容量、实际冲突请求和第二次
  `AcquireBlock` 共同证明；没有直接读取 L1 tag array 的每路 tag。
- 原始信号 `io_in_bits_opcode=38` 在本报告中只用于记录第一次下级读取事务；L2 hit 的
  核心判据来自 `io_dirResp_s3_bits_hit`、`need_mshr_s3`、
  `io_toMSHRCtl_mshr_alloc_s3_valid`、`sink_resp_s3_bits_opcode` 以及第二次窗口内无同
  地址 CHI 请求。

## 分析输入

- 构造程序：`l1miss_l2hit.S`
- 反汇编：`disassembly.txt`
- 原始波形：`l1miss_l2hit.fst`
- 波形会话：`l1miss_l2hit.ron`
