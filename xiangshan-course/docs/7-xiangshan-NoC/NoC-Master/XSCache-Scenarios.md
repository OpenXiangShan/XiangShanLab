# XSCache 典型场景分析

本文汇总同目录下四个 XSCache 场景，说明测试如何构造缓存状态、请求在 L1、CoupledL2、
OpenLLC/CHI 之间如何传递，以及波形用什么证据判定事务成立。四个场景分别覆盖：

1. L1 miss、L2 hit，由 L2 直接返回数据；
2. L1 miss、L2 miss，L2 经 CHI/L3 取数；
3. L1 miss、L2 miss，且 L2 set 已满，需要替换 victim；
4. LLC 发现另一个 RN 持有副本，经 CHI snoop 触发 L2 对 L1 的 TileLink Probe。

详细的程序构造、逐周期波形和 RTL 路径分别位于每个场景目录的
`CONSTRUCTION_ANALYSIS.md`、`WAVEFORM_ANALYSIS.md` 和 `RTL_CODE_ANALYSIS.md`。

## 0. 版本基线

本章统一采用昆明湖 **V3**，不再混用 V2 的缓存参数和波形：

- XSCache 总述（`XSCache.md`）：`kunminghu-v3` 分支，XiangShan `022cee074` 快照；
- 四个场景包：`kunminghu-v3` 分支，XiangShan
  `6705b63685d301f8c0960f7cbcc8b964660dc58e`，XSCache 子模块
  `e9488aed486358e3e5984b531aab601f9b91a9c0`。

波形由 `TestkitV3Config` 构建的单核/双核仿真器产生。该配置的缓存参数为：L1
DCache 64 KiB、128 sets × 8 ways；L2 256 KiB、4 banks × 128 sets × 8 ways；
OpenLLC 1 个 slice。表中所有地址、set、tag 和周期都按这个配置核对。

四个场景的波形、RTL 行号和程序地址均按上述 V3 基线重新生成/核对。V2 时代的
“6 条 128 KiB 冲突、4-way L1、LLC slice 2、txnID 1041”等结论已按 V3 参数改写，
不能与 V3 波形混用。

## 1. 分析对象与统一判据

前三个场景使用单核环境，主要观察 L1 DCache 与 CoupledL2 之间的 TileLink，以及
CoupledL2 与下一级之间的 CHI。第四个场景使用双核环境，并额外观察 OpenLLC 的 peer RN
目录和 snoop 路径。

V3 的缓存参数：

| 层次 | 参数 |
| --- | --- |
| L1 DCache | 64 KiB，128 sets × 8 ways |
| L2 CoupledL2 | 4 banks × 128 sets × 8 ways（每 bank 64 KiB） |
| L2 set index | `addr[14:8]` |
| L2 bank | `addr[7:6]` |
| L1 set index | `addr[12:6]` |
| OpenLLC | 本配置 1 个 slice |

本文沿用各报告的判定方式：总线字段只有在 `valid=1 && ready=1` 时才作为真实事务
证据，不能根据非握手周期残留在总线上的 opcode、地址或数据判断行为。

### 1.1 TileLink 侧

| 现象 | 判定意义 |
| --- | --- |
| A 通道 `AcquireBlock`，opcode `6` | L1 没有可直接满足请求的副本，向 L2 申请完整 cache line |
| B 通道 `Probe`，opcode `6` | L2 要求 L1 对目标 line 降权或失效 |
| C 通道 `ProbeAck`，opcode `4` | L1 完成无数据 Probe 响应 |
| D 通道 `GrantData`，opcode `5` | L2 向 L1 返回数据并授予权限 |
| E 通道 `GrantAck` | L1 已接收 Grant，事务的 sink 资源可以释放 |

本配置的 cache line 为 64 bytes，D 通道宽度为 256 bits，因此完整 line 的
`GrantData` 分两拍返回。

### 1.2 CoupledL2 侧

| 信号组合 | 判定意义 |
| --- | --- |
| `io_dirResp_s3_hit=1`、`io_toMSHRCtl_mshr_alloc_s3_valid=0`、D 通道 `GrantData` | L2 hit，main pipe 直接响应 |
| `io_dirResp_s3_hit=0`、`io_toMSHRCtl_mshr_alloc_s3_valid=1` | L2 miss，进入 MSHR 路径 |
| `io_replResp_bits_tag/way/meta_state` 返回有效 victim | miss 需要使用有效 victim，而不是填入空 way |

只看到目录 `hit` 或 `miss` 还不足以描述完整路径。是否分配 MSHR、是否直接生成响应、
是否出现同地址 CHI 请求，构成相互独立的交叉证据。

### 1.3 CHI/OpenLLC 侧

| 消息或状态 | 本文中的作用 |
| --- | --- |
| `ReadNotSharedDirty`，opcode `0x26` | CoupledL2 为 TileLink `AcquireBlock NtoB` 向下级读取目标 line |
| `WriteEvictOrEvict`，opcode `0x42` | L2 淘汰 clean victim |
| `SnpNotSharedDirty`，opcode `4` | LLC 请求 peer RN 对目标 line 执行 snoop |
| `SnpResp`，opcode `1` | 被 snoop 的 RN 完成一致性响应 |
| `ReadNoSnp`，opcode `4` | L3/OpenLLC 向内存读取目标 line |

## 2. 四个场景总览

| 场景 | 构造方法 | L1/L2 结果 | 关键下级或一致性动作 | 场景结论 |
| --- | --- | --- | --- | --- |
| [`l1miss_l2hit`](./l1miss_l2hit/) | 先加载目标行，再访问 16 条同 L1 set、不同 L2 set 的冲突行 | 第二次目标访问 L1 miss、L2 hit | 第二次访问无目标地址 CHI 请求 | L2 main pipe 直接返回 `GrantData` |
| [`l1miss_l2miss`](./l1miss_l2miss/) | 冷启动后只加载一次目标行 | L1 miss、L2 miss | CHI `ReadNotSharedDirty` | 完整覆盖普通 miss/refill 路径 |
| [`l1miss_l2miss_replace`](./l1miss_l2miss_replace/) | 用 8 条同 set line 填满 8-way L2，再访问第 9 条 | L1 miss、L2 miss | 目标 read + victim `WriteEvictOrEvict` | 完整覆盖 clean victim replacement |
| [`l3_probe_l1`](./l3_probe_l1/) | core 0 先持有共享行，core 1 再读同一行 | core 1 请求触发 peer RN snoop | LLC snoop -> L2 -> L1 `Probe/ProbeAck` -> `SnpResp` -> `ReadNoSnp` | 完整覆盖跨层共享一致性路径 |

从请求方向看，前三个场景是由 L1 向下发起：

```text
L1 DCache --TileLink Acquire--> CoupledL2 --CHI Read/Evict--> OpenLLC/下级
```

第四个场景包含反向一致性流量：

```text
OpenLLC --CHI Snoop--> CoupledL2 --TileLink Probe--> L1 DCache
        <--SnpResp---            <--ProbeAck-----
```

## 3. 场景一：L1 miss、L2 hit

### 3.1 构造方法

目标 line 为 `0x80024000`。程序首先访问目标行，使其进入 L1 和 L2；然后串行访问
16 条相隔 `8 KiB` 的冲突 line：

```text
0x80026000 -> 0x80028000 -> 0x8002a000 -> 0x8002c000
           -> 0x8002e000 -> 0x80030000 -> 0x80032000 -> 0x80034000
           -> 0x80036000 -> 0x80038000 -> 0x8003a000 -> 0x8003c000
           -> 0x8003e000 -> 0x80040000 -> 0x80042000 -> 0x80044000
```

`8 KiB` stride 使 `addr[12:6]` 不变、`addr[14:8]` 每步增加 `0x20`：17 条 line 落在
同一个 L1 set，但分散在 4 个 L2 set。V3 的 L1 为 8-way，17 条 line 必然驱逐目标行；
V3 的 L2 为 8-way，每个 L2 set 最多 5 条 line，目标行仍可留在 L2。指针链建立了逐次
load 的真实数据依赖，防止处理器并行或提前发射冲突访问；之后的等待循环将第二次目标
load 与前面的 refill 分开。

### 3.2 第一次访问：建立 L2 副本

第一次访问目标行时：

```text
L1 AcquireBlock NtoB
  -> L2 directory miss
  -> mshr_alloc=1，分配 MSHR
  -> CHI ReadNotSharedDirty(0x80024000)
  -> 两拍 GrantData
```

这一步先证明目标行确实从下级进入缓存层次，为后续的 L2 hit 建立前置状态。

### 3.3 第二次访问：L2 直接响应

冲突访问完成后，目标地址再次出现 TileLink `AcquireBlock`，直接证明目标行已经不能在
L1 命中。该请求进入 CoupledL2 后满足：

```text
dir_hit=1
mshr_alloc=0
D opcode=GrantData
```

L2 从 data storage 读出完整 line，以两拍无错误 `GrantData` 返回，L1 随后发送
`GrantAck`。从第二次 `AcquireBlock` 到目标 load 完成的窗口内，没有同地址 CHI 请求，
因此可以排除“目录显示 hit、实际仍向下级取数”的解释。

最终链路为：

```text
第二次目标 AcquireBlock
  -> L1 miss
  -> L2 directory hit
  -> main pipe 直接 GrantData
  -> GrantAck
  -> 无目标 CHI 请求
  -> 目标 load 返回正确值
```

详细报告：

- [场景构造分析](./l1miss_l2hit/CONSTRUCTION_ANALYSIS.md)
- [波形分析](./l1miss_l2hit/WAVEFORM_ANALYSIS.md)
- [RTL 代码覆盖分析](./l1miss_l2hit/RTL_CODE_ANALYSIS.md)

## 4. 场景二：L1 miss、L2 miss

### 4.1 构造方法

该场景是普通冷 miss 的最小基线。程序只对 `0x80024000` 执行一次目标 load，目标 line
中保存 `0x12345678`，不添加冲突访问、替换压力或多核同步。这样可以把观察到的目标
事务直接归因到唯一的 load。

### 4.2 完整 miss/refill 路径

目标 load 首先在 L1/L2 接口产生 `AcquireBlock NtoB`。CoupledL2 查询 slice 0、set
`0x40`、tag `0x10004`，目录结果为 miss：

```text
dir_hit=0
mshr_alloc=1
```

MSHR 将该 TileLink 请求映射为 CHI `ReadNotSharedDirty`，并向下级发送相同目标地址。
数据返回后，L1 收到两拍 `GrantData`，首拍包含 `0x12345678`，两拍均
`denied=0`、`corrupt=0`；随后 E 通道完成 `GrantAck`，程序返回 0 并 HIT GOOD TRAP。

完整链路为：

```text
目标 load
  -> L1 AcquireBlock
  -> L2 directory miss
  -> 分配 MSHR
  -> CHI ReadNotSharedDirty
  -> 两拍无错误 GrantData
  -> GrantAck
  -> 程序返回 0
```

该场景与场景一形成直接对照：两者都会出现 L1 `AcquireBlock`，但场景一本次请求由 L2
直接响应且无下级请求；场景二必须分配 MSHR 并访问 CHI/L3。

详细报告：

- [场景构造分析](./l1miss_l2miss/CONSTRUCTION_ANALYSIS.md)
- [波形分析](./l1miss_l2miss/WAVEFORM_ANALYSIS.md)
- [RTL 代码覆盖分析](./l1miss_l2miss/RTL_CODE_ANALYSIS.md)

## 5. 场景三：L2 miss 并发生 replacement

### 5.1 构造方法

该场景先串行访问 8 条相同 L2 set、不同 tag 的 line，填满 8-way L2 的 slice 0、set
`0x40`：

| 地址范围 | L2 tag | 用途 |
| --- | --- | --- |
| `0x80024000` 至 `0x80104000`，步长 `128 KiB` | `0x10004` 至 `0x10020` | 填满 8 个 way |
| `0x80124000` | `0x10024` | 第 9 条目标 line |

8 条 fill line 通过指针链保持严格的访问顺序。第 9 条目标 load 又依赖链尾返回的零值，
从而不会在 set 填充完成前提前发射。目标 load 之后增加 512 次空转，让 victim release
在程序退出前完成。

### 5.2 目标 miss 与 victim 选择

第 9 条 line 在 L1 产生 `AcquireBlock`，在 L2 目录查询 miss，并分配 MSHR。波形中的
MSHR 状态记录：

```text
reqTag   = 0x10024
metaTag  = 0x10004
needsRepl = 1
w_replResp = 1
```

replacement response 返回 victim tag `0x10004`、way 0、有效 `TRUNK` 状态，且该
victim 最终通过 clean eviction 释放。因此本次 refill 使用的是有效 clean victim，
而不是目录中的空 way。

### 5.3 目标 refill 与 victim eviction

CoupledL2 向下级发出两个可区分的 CHI 事务：

| 地址 | CHI 消息 | 作用 |
| --- | --- | --- |
| `0x80124000` | `ReadNotSharedDirty` | 读取新目标 line |
| `0x80024000` | `WriteEvictOrEvict` | 淘汰 clean victim |

目标数据先通过两拍无错误 `GrantData` 返回 L1，随后完成 `GrantAck` 和程序返回；
victim eviction 也随后发出。这说明目标请求的完成和 victim 释放都真实发生，而不只是
replacement metadata 被更新。

完整链路为：

```text
8 条同 set line 填满 L2
  -> 第 9 条目标 AcquireBlock
  -> L2 directory miss + MSHR allocation
  -> replacement policy 返回有效 clean victim
  -> 目标 ReadNotSharedDirty
  -> 目标 GrantData + GrantAck + 程序返回 0
  -> victim WriteEvictOrEvict
```

该场景只覆盖 clean victim。dirty victim 所需的 `WriteBackFull`、replacement retry，
以及 victim 仍被 L1 client 持有时的反向 Probe 不在本场景范围内。

详细报告：

- [场景构造分析](./l1miss_l2miss_replace/CONSTRUCTION_ANALYSIS.md)
- [波形分析](./l1miss_l2miss_replace/WAVEFORM_ANALYSIS.md)
- [RTL 代码覆盖分析](./l1miss_l2miss_replace/RTL_CODE_ANALYSIS.md)

## 6. 场景四：LLC snoop 触发 L1 Probe

### 6.1 构造方法

该场景运行在双核环境，目标共享 line 为 `0x80001680`，初值为
`0x1122334455667788`。core 0 先读取目标 line 并返回，然后通过独立 cache line 上的
同步变量通知 core 1；core 1 收到通知后再读取同一目标 line。两个 hart 使用 fence 和
轮询建立明确的先后关系，避免 core 1 在 core 0 建立副本前发起请求。

### 6.2 LLC 选择 peer RN snoop

core 1 对目标地址发出 `AcquireBlock`，并经其 CoupledL2 形成
`ReadNotSharedDirty`。OpenLLC slice 0 处理该请求时观察到：

```text
srcID=1
snpVec=1/0
self_hit=0
snoop opcode=4 (SnpNotSharedDirty)
snoop txnID=81
```

这表示请求来自 RN1，而目标 line 的 peer 副本位于 RN0。LLC 因此生成只指向 RN0 的
`SnpNotSharedDirty`。V3 OpenLLC 在本配置下只有 1 个 slice，不再使用 V2 的 slice 2
编号。

### 6.3 CHI snoop 转换为 TileLink Probe

LLC 发出的 snoop 与 core 0 L2 接收的 RxSnp 在地址、opcode 和 txnID 上一致。core 0
CoupledL2 检测到上层 L1 client 持有该 line，分配 snoop 处理状态，并通过 TileLink B
通道发出：

```text
Probe(address=0x80001680, param=toB)
```

core 0 DCache 的 ProbeQueue 接收请求，送入 MainPipe 和 WritebackQueue，最后在 C
通道返回无数据 `ProbeAck`。`toB` 表示降权到共享状态，因此 core 0 可以保留共享副本，
不需要返回 `ProbeAckData`。

### 6.4 Snoop 和请求方事务完成

收到 L1 `ProbeAck` 后，core 0 L2 向 LLC 返回 CHI `SnpResp`。LLC TxSnp、core 0 L2
RxSnp 和 SnpResp 使用相同 txnID 81，证明跨层事务关联没有丢失。随后 OpenLLC 为
目标行发出 `ReadNoSnp`，并向 core 1 返回 `CompData`；core 1 收到包含目标值的两拍
无错误 `GrantData`，发送 `GrantAck`，两个 hart 最终都 HIT GOOD TRAP。

完整链路为：

```text
core 0 load -> 建立目标副本
core 1 load 同一 line
  -> OpenLLC 检测 peer RN0 hit
  -> CHI SnpNotSharedDirty(RN0)
  -> core 0 CoupledL2 RxSnp
  -> TileLink Probe(toB) 到 core 0 L1
  -> core 0 L1 ProbeAck
  -> core 0 L2 CHI SnpResp
  -> OpenLLC ReadNoSnp
  -> OpenLLC CompData 到 core 1
  -> core 1 GrantData + GrantAck
```

该场景覆盖的是共享读引发的降权路径，不覆盖 `ProbeAckData`、toN 失效、dirty owner、
forwarded snoop、多 peer RN 或 LLC replacement snoop。

详细报告：

- [场景构造分析](./l3_probe_l1/CONSTRUCTION_ANALYSIS.md)
- [波形分析](./l3_probe_l1/WAVEFORM_ANALYSIS.md)
- [RTL 代码覆盖分析](./l3_probe_l1/RTL_CODE_ANALYSIS.md)

## 7. 场景间的关键区别

### 7.1 L2 hit 与 L2 miss

两者在 L1 侧都可以表现为 `AcquireBlock`，因此不能只看 A 通道。区分点在于 L2 是否能
直接响应：

| 路径 | Directory | MSHR | CHI read | 响应来源 |
| --- | --- | --- | --- | --- |
| L2 hit | hit | 不分配 | 无 | L2 data storage |
| L2 miss | miss | 分配 | 有 | 下级返回后 refill |

### 7.2 普通 miss 与 replacement miss

普通 miss 可以选择 invalid way，不需要释放有效 victim；replacement miss 则必须看到
有效 victim 的 tag/way/meta、`needsRepl` 状态和真实 eviction/writeback 请求。只有替换器
返回一个 way 编号，不能单独证明发生了有效 line 淘汰。

### 7.3 下行请求与反向 Probe

前三个场景由 CPU load 发起，主要链路是 A/D/E 和 CHI read/evict。第四个场景中的核心
行为由 LLC peer directory 决策触发，沿 CHI snoop 和 TileLink B/C 通道反向传播。它把
OpenLLC、CoupledL2 与 L1 DCache 的一致性处理连接为一个完整往返事务。

## 8. 覆盖范围与限制

四个场景共同覆盖了：

- DCache load miss 生成 `AcquireBlock`；
- CoupledL2 directory hit 直返与 directory miss 分配 MSHR；
- `AcquireBlock NtoB` 到 CHI `ReadNotSharedDirty` 的映射；
- clean victim 的 replacement 和 `WriteEvictOrEvict`；
- OpenLLC peer RN 命中与 `SnpNotSharedDirty`；
- CoupledL2 snoop-to-B、DCache `Probe/ProbeAck` 和 CHI `SnpResp`；
- `GrantData`、`GrantAck` 以及程序返回值/GOOD TRAP 的完成路径。

限制：

- 当前 V3 构建未导出 ROB 的逐指令 commit 端，完成证据以 D/E 握手和程序最终
  GOOD TRAP 为准。
- 场景一只覆盖 clean refill 和 L2 hit；不覆盖 dirty victim、NtoT、store miss、AMO、
  prefetch 和 CMO。
- 场景四不覆盖 `ProbeAckData`、toN、dirty owner、forwarded snoop、多 peer RN 或
  LLC replacement snoop。
- `ReadNoSnp` 的精确微架构触发原因未由波形逐信号证明；报告只记录事务级事实。
- 各报告不提供工具生成的 RTL line/branch coverage 百分比。
