# l1miss_l2miss 场景构造程序分析

## 1. 场景目标

本程序构造最小的冷 cache line 访问场景，用一次目标 load 验证完整的
**L1 miss、L2 miss、向 CHI/L3 取数、返回 L1 并提交**路径。

程序不包含冲突访问、预取控制或多核同步，因此观察到的目标事务主要由唯一的
`target_load` 产生，适合作为 miss 路径基线。

## 2. 参数与数据布局

程序定义：

| 参数 | 值 | 用途 |
| --- | ---: | --- |
| `TARGET_VALUE` | `0x12345678` | 目标 cache line 中的测试数据 |
| `TARGET_ALIGNMENT` | `128 KiB` | 数据段对齐粒度 |
| `TARGET_OFFSET` | `16 KiB` | 目标 line 在对齐区域中的偏移 |

链接后的目标地址为 `0x80024000`。目标对象只保存一个 64-bit 值：

```text
target_line = 0x0000000012345678
```

数据地址按 64-byte cache line 对齐，目标地址对应报告中的 L2 slice 0、set `0x40`、
tag `0x4001`。

## 3. 指令执行流程

### 3.1 形成目标地址

```asm
la t0, target_line
```

`la` 只计算地址，不访问数据。反汇编确认 `t0` 被设置为 `0x80024000`，因此后续
唯一的内存访问可以直接按目标地址过滤。

### 3.2 唯一目标 load

```asm
target_load:
    ld t1, 0(t0)
```

这是场景的核心。冷启动时目标 line 不在 L1，也不在 L2，预期产生以下链路：

1. L1 DCache 发出 TileLink A-channel `AcquireBlock`，地址为 `0x80024000`。
2. L2 main pipe 查询目录，`dir_hit=0`。
3. L2 令 `need_mshr_s3=1`，并分配 MSHR。
4. L2 通过 CHI 发出 `ReadNotSharedDirty`，地址仍为目标 line。
5. 下级返回完整 cache line，L1 接收两拍 `GrantData`。
6. L1 发送 `GrantAck`，目标 load 最终提交。

反汇编中该 load 位于 PC `0x80000132`，对应场景配置中的 `target_load_pc`。

### 3.3 目标数据校验

```asm
li  t2, TARGET_VALUE
bne t1, t2, fail
```

程序将 load 结果与 `0x12345678` 比较。比较成功返回 0，失败返回 1。该检查证明
返回内容正确，但不能单独证明数据来自哪一级缓存；L1/L2/CHI 路径需要由波形中的
目录、MSHR、CHI 和 TileLink 信号共同判定。

## 4. 与波形的对应关系

| 程序位置 | 关键波形现象 | 判定 |
| --- | --- | --- |
| `target_load`，PC `0x80000132` | 目标地址 `AcquireBlock` 握手 | L1 miss |
| 目标请求进入 L2 | `task_s3_valid=1`、目标 set/tag 匹配、`dir_hit=0` | L2 directory miss |
| L2 miss 处理 | `need_mshr_s3=1`、MSHR allocation 有效 | 进入 MSHR 路径 |
| 下级请求 | 目标地址 CHI `ReadNotSharedDirty` | 向 CHI/L3 取数 |
| 数据返回 | 两拍无错误 `GrantData` | 目标 line 返回 |
| 接收确认 | `GrantAck` 握手 | L1 完成接收 |
| 提交 | PC `0x80000132`、写 x6 | 目标 load 完成 |

## 5. 场景隔离性

- 程序只对 `target_line` 执行一次数据 load，没有指针链和同 set 冲突访问。
- `la`、立即数构造和分支比较不访问测试数据区域之外的 cache line。
- 目标数据在程序初始化镜像中已经存在，但这不等于目标 line 已经进入 L1/L2；缓存
  层级状态仍由仿真启动时的 cache 初始状态决定。
- 如果启动环境存在预取、残留 cache 状态或其他后台访问，仅凭程序源码不能保证冷 miss，
  应以目标 A-channel、L2 目录和 CHI 证据为准。

## 6. 相关文件

- 源程序：`l1miss_l2miss.S`
- 反汇编：`disassembly.txt`
- 波形：`l1miss_l2miss.fst`
- 波形报告：`WAVEFORM_ANALYSIS.md`
