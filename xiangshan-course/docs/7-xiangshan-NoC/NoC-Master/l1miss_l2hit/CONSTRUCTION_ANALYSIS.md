# l1miss_l2hit 场景构造程序分析

## 1. 场景目标

本程序构造一个两级缓存层次场景：第一次访问目标 cache line 时产生 L1 miss、L2
miss；随后使用 16 条与目标地址映射到同一 L1 set、但分散到不同 L2 set 的冲突 line，
把目标行挤出 8-way L1，同时不填满 8-way L2；最后再次访问目标行，形成
**L1 miss、L2 hit**。

这里的 Hit 指 L2 hit，不是 L1 hit。第二次访问仍需通过 L1 miss queue 向 L2 发出
`AcquireBlock`，由 L2 直接返回已经保留的数据。

本程序按昆明湖 V3 的缓存参数重新构造：L1 DCache 为 `64 KiB / 128 sets = 8 ways`，
L2 为 4 banks × 128 sets × 8 ways。

## 2. 参数与地址布局

程序定义如下参数：

| 参数 | 值 | 用途 |
| --- | ---: | --- |
| `LOCAL_SET_STRIDE` | `8 KiB` | 相邻测试 line 的地址间隔 |
| `TARGET_ALIGNMENT` | `128 KiB` | 数据段对齐粒度 |
| `TARGET_SET_OFFSET` | `16 KiB` | 目标数据区相对对齐基址的偏移 |
| `LINE_DATA_SIZE` | `8 bytes` | 每个指针节点实际存储的数据大小 |
| `SETTLE_ITERATIONS` | `512` | 冲突访问后的等待循环次数 |

V3 的 set index 划分是：

- L1 set = `addr[12:6]`；
- L2 bank = `addr[7:6]`，L2 set = `addr[14:8]`。

`8 KiB (0x2000)` 的 stride 只改变 `addr[13]`：

- `addr[12:6]` 不变，所以 17 条 line 落在同一个 L1 set；
- `addr[14:8]` 每次增加 `0x20`，所以它们分散到 4 个 L2 set，每个 L2 set 最多 5 条
  line，低于 8-way L2 容量。

目标行和 16 条冲突行的地址如下：

| 逻辑对象 | 地址 | 作用 |
| --- | --- | --- |
| `target_line` | `0x80024000` | 第一次预热和最后 reload |
| `conflict_line_1` | `0x80026000` | 第一个冲突访问 |
| `conflict_line_2` | `0x80028000` | 第二个冲突访问 |
| `conflict_line_3` | `0x8002a000` | 第三个冲突访问 |
| `conflict_line_4` | `0x8002c000` | 第四个冲突访问 |
| `conflict_line_5` | `0x8002e000` | 第五个冲突访问 |
| `conflict_line_6` | `0x80030000` | 第六个冲突访问 |
| `conflict_line_7` | `0x80032000` | 第七个冲突访问 |
| `conflict_line_8` | `0x80034000` | 第八个冲突访问 |
| `conflict_line_9` | `0x80036000` | 第九个冲突访问 |
| `conflict_line_10` | `0x80038000` | 第十个冲突访问 |
| `conflict_line_11` | `0x8003a000` | 第十一个冲突访问 |
| `conflict_line_12` | `0x8003c000` | 第十二个冲突访问 |
| `conflict_line_13` | `0x8003e000` | 第十三个冲突访问 |
| `conflict_line_14` | `0x80040000` | 第十四个冲突访问 |
| `conflict_line_15` | `0x80042000` | 第十五个冲突访问 |
| `conflict_line_16` | `0x80044000` | 第十六个冲突访问，链尾为零 |

目标行和 16 条冲突行共 17 条 cache line。相对于 8-way L1，这必然产生至少一次替换；
相对于 8-way L2，每个 set 只有 3–5 条 line，目标行应继续留在 L2。

V2 的旧程序使用 6 条 `128 KiB` stride 的冲突 line。该 stride 同时保持 L1 set 和
L2 set 不变，在 V2 的 4-way L1 / 8-way L2 下成立；但 V3 的 L1 也是 8-way，6 条冲突
不会驱逐目标行，第二次访问会退化成 L1 hit。因此本程序必须改为 16 条、8 KiB stride。

## 3. 指令执行流程

### 3.1 第一次目标访问

```asm
la  t0, target_line
ld  t1, 0(t0)
```

`t0` 保存目标行地址，第一次 `ld` 读取目标行中的指针值 `conflict_line_1`。在冷启动
条件下，该访问预期产生：

1. L1 DCache 向 L2 发出目标地址的 TileLink `AcquireBlock`。
2. L2 目录查询 miss，并分配 MSHR。
3. L2 向 CHI/L3 请求目标 line。
4. 数据返回后，目标 line 同时建立在 L1/L2 层次中。

### 3.2 防止冲突访问提前发射

```asm
la   t2, conflict_line_1
and  t1, t1, zero
add  t2, t2, t1
```

第一次 load 的结果先经过 `and t1, t1, zero` 清零，再参与 `t2` 的地址计算。该数据
依赖使第一条冲突访问依赖第一次目标 load 完成，避免处理器在目标 line 尚未 refill
完成时就提前发射冲突访问。

清零操作不会改变 `t2` 的地址，因此最终仍从 `conflict_line_1` 开始遍历指针链。

### 3.3 16 条同 L1 set 冲突访问

```asm
ld t2, 0(t2)
... 共 16 次 ...
```

每一次 load 都读取前一个节点中保存的下一个节点地址，形成严格的地址依赖链：

```text
conflict_line_1 -> conflict_line_2 -> ... -> conflict_line_16 -> 0
```

因此 16 次访问按程序顺序发生，不会被处理器作为互相独立的内存请求大规模并行发射。
它们分别对不同 L2 set、相同 L1 set 的 cache line 产生访问，给 L1 制造真实的 set
冲突。波形中对应 16 次 A-channel `AcquireBlock`。

### 3.4 等待事务稳定

```asm
li   t4, 512
.Lsettle:
addi t4, t4, -1
bnez t4, .Lsettle
```

该循环不访问内存，只用于给冲突 line 的 refill、可能的 replacement 以及相关缓存
状态更新留出时间。这样最后的目标 reload 不会与前面的冲突事务重叠，便于将波形中的
第二次目标请求单独归因到 `target_reload`。

### 3.5 第二次目标访问

```asm
and  t2, t2, zero
add  t0, t0, t2
ld   t1, 0(t0)
```

指针链最终返回零，因此 `t0` 仍为 `target_line` 地址。这里保留一次数据依赖，确保
reload 不会在冲突链完成前提前发射。反汇编中 reload 指令位于 PC `0x80000194`。

如果目标行已经被 L1 冲突访问驱逐，reload 会再次产生 `AcquireBlock`；如果目标行仍在
L2，则 L2 应满足目录 hit、无需 MSHR、直接产生 `GrantData`，并且窗口内不应出现同地址
CHI 请求。

### 3.6 程序结果校验

```asm
la  t2, conflict_line_1
bne t1, t2, fail
li  a0, 0
ret
```

目标行存储的是 `conflict_line_1` 地址，因此 reload 返回该值时程序成功返回 0；否则
返回 1。实际运行结果为 `HIT GOOD TRAP at pc = 0x800001b8`，返回值 0。

这个检查验证了缓存层次返回的数据内容正确，但不能单独区分 L1 hit、L2 hit 或下级
重新取数，层次归因必须结合波形信号判断。

## 4. 与波形的对应关系

| 程序阶段 | 关键波形现象 | 判定 |
| --- | --- | --- |
| 第一次 `ld target_line` | `AcquireBlock`（1122），L2 `dir_hit=0`，MSHR allocation（1124），目标 CHI read（1126） | L1 miss、L2 miss |
| 16 次冲突 load | 16 个同 L1 set 地址的 `AcquireBlock`（1175..1940） | 构造 L1 淘汰压力 |
| 第二次 `ld target_line` | 目标地址再次 `AcquireBlock`（2528） | 目标不再命中 L1 |
| 第二次 L2 查询 | `dir_hit=1`（2530）、`mshr_alloc=0`、`GrantData`（2534、2535） | L2 hit 直接响应 |
| 第二次返回窗口 | 无同地址 CHI 请求，D/E 握手完成 | 没有再次访问 L3，事务完成 |
| reload 完成 | 目标 load 返回 `0x80026000`，程序 GOOD TRAP | 程序行为完成 |

## 5. 构造成立的前提与限制

- 地址间隔 `8 KiB` 与 V3 的 L1/L2 set index 划分共同决定同 set 关系；如果缓存配置
  改变，必须重新计算地址布局。
- 17 条相关 line 必然超过 8-way L1 容量，但具体被替换的 way 由 replacement policy
  决定；程序没有直接读取 L1 tag array。“目标行离开 L1”由 16 条冲突请求和第二次目标
  `AcquireBlock` 共同佐证。
- 17 条相关 line 分散在 4 个 L2 set 中，每个 set 最多 5 条，未超过 8-way L2 容量，
  因此 L2 hit 是设计目标；最终仍需检查目录和 CHI 波形。
- 512 次算术循环只提供时间间隔，不是缓存同步原语；真正的事务完成性仍应由波形中的
  handshake、D/E 通道和程序结果确认。
- 当前 V3 构建未导出逐指令 commit 端；程序结果以最终 GOOD TRAP 和返回值为准。

## 6. 相关文件

- 源程序：`l1miss_l2hit.S`
- 反汇编：`disassembly.txt`
- 波形：`l1miss_l2hit.fst`
- 波形报告：`WAVEFORM_ANALYSIS.md`
