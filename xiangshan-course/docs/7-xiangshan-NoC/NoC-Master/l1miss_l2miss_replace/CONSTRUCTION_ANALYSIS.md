# l1miss_l2miss_replace 场景构造程序分析

## 1. 场景目标

本程序构造一个真实的 L2 replacement 场景：先将同一个 L2 set 的 8 个 way 填入有效
cache line，再访问第 9 条不同 tag 的目标 line。目标访问应在 L1/L2 均 miss，L2
必须选择 victim way，必要时执行 victim eviction，然后完成目标 line refill。

该程序验证的不只是 replacement metadata 变化，还要求波形中出现 victim 地址的
CHI eviction 请求。

## 2. 参数与地址布局

程序定义：

| 参数 | 值 | 用途 |
| --- | ---: | --- |
| `LOCAL_SET_STRIDE` | `128 KiB` | 保持相同 cache set 的地址间隔 |
| `TARGET_SET_OFFSET` | `16 KiB` | 测试区域偏移 |
| `LINE_DATA_SIZE` | `8 bytes` | 每个链表节点的数据大小 |
| `TARGET_VALUE` | `0x12345678` | 第九条目标 line 的数据 |
| `SETTLE_ITERATIONS` | `512` | 填充完成后的等待时间 |

填充链从 `0x80024000` 开始，连续包含 8 条 line；目标 line 位于第 9 个位置
`0x80124000`。对应 L2 slice 0、set `0x40` 的 tag 如下：

| 逻辑对象 | 地址 | L2 tag | 作用 |
| --- | --- | --- | --- |
| `fill_line_1` | `0x80024000` | `0x4001` | 预期 victim |
| `fill_line_2` | `0x80044000` | `0x4002` | 填充 way |
| `fill_line_3` | `0x80064000` | `0x4003` | 填充 way |
| `fill_line_4` | `0x80084000` | `0x4004` | 填充 way |
| `fill_line_5` | `0x800a4000` | `0x4005` | 填充 way |
| `fill_line_6` | `0x800c4000` | `0x4006` | 填充 way |
| `fill_line_7` | `0x800e4000` | `0x4007` | 填充 way |
| `fill_line_8` | `0x80104000` | `0x4008` | 填充 way，链尾为零 |
| `target_line` | `0x80124000` | `0x4009` | 第九条目标 line |

8 条 fill line 的 tag 不同但 set 相同，正好覆盖报告所依据的 8-way L2 set。

## 3. 指令执行流程

### 3.1 串行填充 8 个 way

```asm
la t0, fill_line_1
ld t0, 0(t0)
ld t0, 0(t0)
ld t0, 0(t0)
ld t0, 0(t0)
ld t0, 0(t0)
ld t0, 0(t0)
ld t0, 0(t0)
ld t0, 0(t0)
```

每个节点保存下一个节点的地址，load 结果继续作为下一次 load 的地址。因此 8 条
填充访问存在真实的数据依赖，按链表顺序逐条完成：

```text
fill_line_1 -> fill_line_2 -> ... -> fill_line_8 -> 0
```

每条 line 第一次访问时会从上层 cache 发出 `AcquireBlock`。波形报告中对应 8 次
地址依次递增 `128 KiB` 的 A-channel 事务。

### 3.2 等待填充事务完成

```asm
li   t2, 512
.Lsettle:
addi t2, t2, -1
bnez t2, .Lsettle
```

该循环不产生额外数据访问，目的是将 8 条 fill line 的 refill 和目录更新与第九条
目标请求隔开。它不能从软件层面直接确认事务完成，因此仍需用波形确认 8 次请求的
返回和缓存状态已经稳定。

### 3.3 构造目标地址依赖

```asm
la  t1, target_line
add t1, t1, t0
```

由于第 8 条节点的内容为零，`t0` 最终为零，故 `t1` 保持 `target_line` 地址
`0x80124000`。但地址计算依赖整个填充链完成，避免第九条访问提前发射。

### 3.4 第九条目标访问

```asm
target_load:
    ld t3, 0(t1)
```

反汇编确认目标 load 位于 PC `0x80000166`。预期行为为：

1. L1 发出目标地址的 `AcquireBlock`，证明 L1 miss。
2. L2 目录查询返回 `dir_hit=0`，并分配目标 MSHR。
3. MSHR 记录新 tag `reqTag=0x4009` 和候选旧 tag `metaTag=0x4001`。
4. replacement policy 返回有效 victim，选择 way 0。
5. L2 为目标 line 发出 CHI `ReadNotSharedDirty`。
6. 对 clean victim `0x80024000` 发出 `WriteEvictOrEvict`。
7. 目标数据返回，L1 完成 `GrantData`、`GrantAck`，目标 load 提交。

### 3.5 目标数据校验

```asm
li  t4, TARGET_VALUE
bne t3, t4, fail
```

目标 line 保存 `0x12345678`。比较成功返回 0，失败返回 1。该比较验证 replacement
完成后返回的数据内容正确，但 replacement 是否真实发生仍需依赖 MSHR victim 信息和
victim CHI eviction 证据。

## 4. 与波形的对应关系

| 程序阶段 | 关键波形现象 | 判定 |
| --- | --- | --- |
| 8 次 pointer-chain load | 8 个同 set、不同 tag 的 `AcquireBlock` | L2 set 被填充 |
| 第九条目标 load | 目标 `0x80124000` 的 `AcquireBlock` | L1 miss |
| 目标 L2 查询 | `dir_hit=0`、`need_mshr=1`、MSHR allocation | L2 miss |
| MSHR 状态 | `reqTag=0x4009`、`metaTag=0x4001`、`needsRepl=1` | 需要 replacement |
| replacement 返回 | victim tag `0x4001`、way 0、state 有效、clean | 选定真实 victim |
| CHI 请求 | 目标 `ReadNotSharedDirty` + victim `WriteEvictOrEvict` | refill 与 eviction 均发出 |
| 目标完成 | 两拍无错误 `GrantData`、`GrantAck`、commit | replacement 后事务完成 |

## 5. 构造成立的前提与限制

- 8 条 fill line 必须确实映射到同一个 L2 set；地址间隔和 L2 参数变化时必须重新计算。
- 程序假定目标 L2 set 为 8-way；若实际 way 数不同，第九条访问不一定触发 replacement。
- 程序中的链表依赖保证访问顺序，但 512 次算术循环不是硬件事务完成确认。
- replacement policy 的具体 victim 选择由硬件实现决定。程序构造的是“必须需要 victim”
  的条件，不应仅根据源代码断言一定选择 way 0；way 0 需要由波形确认。
- `WriteEvictOrEvict` 的实际出现是“真实 eviction”的关键证据；只有 metadata 中出现
  victim tag 还不足以证明下级 eviction 已发生。

## 6. 相关文件

- 源程序：`l1miss_l2miss_replace.S`
- 反汇编：`disassembly.txt`
- 波形：`l1miss_l2miss_replace.fst`
- 波形报告：`WAVEFORM_ANALYSIS.md`
