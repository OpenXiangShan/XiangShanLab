## prefetch.r / prefetch.w / prefetch.i vs load 执行流程信号对比

### 一、译码与重命名阶段

| 信号 / 字段 | `load` (如 `ld`) | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **指令编码格式** | I-type, funct3≠110 或 rd≠x0 | OP-IMM, funct3=110, rd=x0, rs2=00001 | OP-IMM, funct3=110, rd=x0, rs2=00011 | OP-IMM, funct3=110, rd=x0, rs2=00000 |
| **识别为 Zicbop** | ✗ | ✓ | ✓ | ✓ |
| **fuOpType** | `LduCfg` (load) | `LduCfg` (仍发往 LoadUnit) | `LduCfg` (仍发往 LoadUnit) | 发往 ICache PrefetchPipe，不进 LoadUnit |
| **目标执行单元** | LoadUnit | LoadUnit | LoadUnit | ICache (PrefetchPipe) |
| **进入 ROB** | ✓ (需提交) | ✓ (需写回完成) | ✓ (需写回完成) | ✓ (Hint，需安全完成) |
| **分配 LQ/SQ 项** | LQ ✓, SQ ✗ | LQ ✗, SQ ✗ | LQ ✗, SQ ✗ | LQ ✗, SQ ✗ |
| **写回目的寄存器** | rd ← 数据 | 无 (rd=x0) | 无 (rd=x0) | 无 (rd=x0) |

### 二、LoadUnit S0 — 请求源仲裁

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **entrance** | `scalarIssue` | `prefetchHiConf` / `prefetchLoConf` | `prefetchHiConf` / `prefetchLoConf` | N/A (不进 LoadUnit) |
| **仲裁优先级** | 6 (scalarIssue) | 4/7 (高/低置信度) | 4/7 (高/低置信度) | — |
| **accessType.instrType** | `scalar` | `prefetch` | `prefetch` | — |
| **accessType.pftType** | DontCare | `swData` (01) | `swData` (01) | `swInstr` (11) |
| **accessType.pftCoh** | DontCare | `read` (0) | `write` (1) | `read` (0) |
| **noQuery** | `false.B` | `true.B` | `true.B` | — |
| **hasROBEntry** | `true.B` | `false.B` | `false.B` | `false.B` |
| **size** | B/H/W/D | DontCare | DontCare | — |
| **mask** | 按地址+宽度计算 | `0.U` (全零) | `0.U` (全零) | — |
| **vaddr** | `src(0) + SignExt(imm)` | 从 L1PrefetchReq 获取 | 从 L1PrefetchReq 获取 | 从指令计算 EA |
| **paddr** | S1 经 TLB 翻译获得 | 从 L1PrefetchReq 直接提供 | 从 L1PrefetchReq 直接提供 | 经 ITLB 翻译 |

### 三、TLB 翻译

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **TLB 端口** | DTLB (ld 通道) | DTLB (ld 通道) | DTLB (ld 通道) | ITLB |
| **查询方式 (noQuery)** | `false` — 正常查询 TLB | `true` — **跳过 TLB 查询**，直接用已有 paddr | `true` — **跳过 TLB 查询** | 正常查询 ITLB |
| **tlbAccessResult** | `hit` / `miss` / `noQuery` | `noQuery` | `noQuery` | `hit` / `miss` |
| **TLB miss 处理** | 进入 `tlbMiss` 状态，等待 L2 TLB 重填 | 不触发（paddr 已由 L1 预取器提前翻译好） | 不触发 | 进入 `itlbResend`，占用 ITLB 端口重发 |
| **异常 (Page Fault 等)** | 正常上报 → ROB 记录 | **不上报**（noQuery 跳过） | **不上报** | 可能触发，Hint 不应阻塞 |
| **pbmt / gpaddr** | 正常生成 | DontCare | DontCare | 正常生成 |

> ⚠️ 注意：硬件预取请求（Stream/Stride/Berti）在发出 `L1PrefetchReq` 时**已经携带了 paddr**（由预取器自行经 TLB 翻译获得），所以 LoadUnit 路径上设 `noQuery=true` 直接跳过 TLB。软件预取指令 `prefetch.r/w` 是否走同一路径取决于实现——当前代码中软件预取也经 `L1PrefetchReq` 接口注入，paddr 在进入 LoadUnit 前已翻译完成。

### 四、DCache 请求

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **目标缓存** | L1 DCache | L1 DCache | L1 DCache | L1 ICache |
| **进入的 Pipe** | LoadPipe (M_XRD) | LoadPipe (M_PFR) | LoadPipe (M_PFW) | PrefetchPipe |
| **cmd** | `M_XRD` | `M_PFR` | `M_PFW` | — (ICache 内部) |
| **S0 meta_read.idx** | `get_dcache_idx(vaddr)` | `get_dcache_idx(vaddr)` | `get_dcache_idx(vaddr)` | `get_icache_idx(vaddr)` |
| **S0 tag_read** | ✓ | ✓ | ✓ | ✓ |
| **S0 data_read** | ✓ (读数据阵列) | ✗ (仅读 tag，不读数据) | ✗ (仅读 tag，不读数据) | ✗ (不读数据阵列) |
| **mask** | 有效字节掩码 | `0.U` (无数据需求) | `0.U` (无数据需求) | — |
| **bank 读使能** | 按 mask 展开 | 全零 | 全零 | — |

### 五、DCache S1 — Tag 匹配与命中判定

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **tag 比较** | ✓ 全路比较 | ✓ 全路比较 | ✓ 全路比较 | ✓ 全路比较 |
| **命中判定** | tag_match → hit/miss | tag_match → hit/miss | tag_match → hit/miss | tag_match → hit/miss |
| **读取命中数据** | ✓ 从 data array 读出 | ✗ 不读数据 | ✗ 不读数据 | ✗ |
| **access_flag 更新** | ✓ 标记已访问 | ✓ 标记预取访问 | ✓ 标记预取访问 | ✓ |
| **prefetch_flag 更新** | ✗ | ✓ 写入 pf_source | ✓ 写入 pf_source | — |
| **pf_source 标记** | — | `L1_HW_PREFETCH_STREAM/STRIDE` 或软件标记 | 同左 | — |

### 六、DCache S2 — Miss 处理

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **miss 时进入 MissQueue** | ✓ 强制进入 | ✓ 可选（MSHR 满时**可丢弃**） | ✓ 可选（MSHR 满时**可丢弃**） | ✓ 进入 ICache MissUnit |
| **MissQueue cmd** | `M_XRD` | `M_PFR` | `M_PFW` | — |
| **MSHR 分配优先级** | 高（demand 优先） | 低（Hint 可丢弃） | 低（Hint 可丢弃） | — |
| **向 L2 发起请求** | `CHI ReadShared` | `CHI ReadShared` | `CHI ReadShared` (预写：仅分配行) | `CHI ReadShared` |
| **重填数据写入 DCache** | ✓ 数据 + tag | ✓ 数据 + tag（可选不填数据） | ✓ tag + 元数据（**可能不填数据**，仅分配行） | ✓ 数据 + tag (ICache) |
| **MissQueue 写预取提前分配** | ✗ | ✗ | ✓ `M_PFW` 可在 Store Buffer 数据到达前预热 | ✗ |
| **MSHR 满 → 丢弃** | ✗ (demand 不可丢弃) | ✓ (Hint 允许丢弃) | ✓ (Hint 允许丢弃) | ✗ (ICache MissUnit 不可丢弃) |

### 七、写回与完成

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **数据写回 ROB** | ✓ load 数据写入 rd | ✗ 无数据写回 | ✗ 无数据写回 | ✗ 无数据写回 |
| **异常写回 ROB** | ✓ (若发生) | ✗ (noQuery 跳过) | ✗ (noQuery 跳过) | ✗ (Hint 不上报) |
| **ROB 提交** | 正常提交 | Hint 完成即可提交 | Hint 完成即可提交 | Hint 完成即可提交 |
| **唤醒 IQ (wakeup)** | ✓ 唤醒依赖此 load 的后续指令 | ✗ | ✗ | ✗ |
| **Fast wakeup** | ✓ (DCache hit 时快速唤醒) | ✗ | ✗ | ✗ |
| **LSQ 释放** | 释放 LQ 项 | 无 LQ 项可释放 | 无 LQ 项可释放 | 无 LQ 项可释放 |
| **Store Forward (STLF)** | ✓ (检查 SQ/SBuffer 转发) | ✗ | ✗ | ✗ |

### 八、与 L2 PrefetchReceiver 联动

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **l1_pf_to_l2 通道** | ✗ | ✓ (若 `l2_pf_recv_enable=true`) | ✓ (若 `l2_pf_recv_enable=true`) | ✗ |
| **L2 PrefetchReceiver 接收** | — | ✓ 分配 L2 MSHR | ✓ 分配 L2 MSHR | — |
| **L2 向 L3 发请求** | — | `CHI ReadShared` | `CHI ReadShared` | — |

### 九、ICache 路径（仅 prefetch.i）

| 信号 / 字段 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **进入 ICache PrefetchPipe** | ✗ | ✗ | ✗ | ✓ |
| **PrefetchPipe S0** | — | — | — | 接收请求，读 MetaArray + ITLB |
| **入 WayLookup** | — | — | — | ✗ (软件预取**不入 WayLookup**) |
| **BPU s3 override 冲刷** | — | — | — | ✗ (软件预取**不被冲刷**) |
| **PrefetchPipe S1 状态机** | — | — | — | `idle→itlbResend→enqWay→enterS2` |
| **PrefetchPipe S2** | — | — | — | 判定是否需要预取，向 MissUnit 发请求 |
| **MissUnit 重填** | — | — | — | 向 L2 发 ReadShared，重填至 ICache |

---

## 关键差异总结

| 维度 | `load` | `prefetch.r` | `prefetch.w` | `prefetch.i` |
|---|---|---|---|---|
| **数据读取** | ✓ 读数据阵列 | ✗ | ✗ | ✗ |
| **TLB 查询** | ✓ 正常查询 | ✗ noQuery (paddr 已有) | ✗ noQuery (paddr 已有) | ✓ 查 ITLB |
| **异常上报** | ✓ | ✗ 静默 | ✗ 静默 | ✗ 静默 |
| **可丢弃性** | 不可丢弃 | 可丢弃 (Hint) | 可丢弃 (Hint) | 不可丢弃 (ICache 路径) |
| **写回数据** | ✓ | ✗ | ✗ | ✗ |
| **唤醒依赖指令** | ✓ | ✗ | ✗ | ✗ |
| **一致性暗示** | — | 读共享 | 写独占 | 指令取 |
| **目标缓存** | DCache | DCache | DCache | ICache |

> 核心设计逻辑：**prefetch 本质上是一条"只查 tag、不读数据、不写回、不唤醒"的 load**——它复用 LoadPipe 的 tag 比较和 MissQueue 重填路径，但在数据通路和完成信号上做了大量旁路。

[NewLoadUnit.scala](src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala#L92-L180)
[package.scala](src/main/scala/xiangshan/mem/pipeline/package.scala#L90-L139)
[L1Cache.scala](src/main/scala/xiangshan/cache/L1Cache.scala#L28-L124)