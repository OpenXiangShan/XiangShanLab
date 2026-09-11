# CHI Retry / Snoop / Dataless 场景详解

---

## 一、CHI Retry 机制

### 1.1 为什么需要 Retry

CHI 协议中，当 Home Node (HN) 收到 Request Node (RN) 的请求但**暂时无法处理**时，不能简单丢弃或挂起——那会导致协议死锁。HN 通过 `RetryAck` 拒绝本次请求，要求 RN 稍后重发。

**触发 Retry 的典型场景**：

| 场景 | 说明 |
|------|------|
| HN 目录状态机忙碌 | HN 正在处理同一地址的另一个事务（如正在等待 Snoop 响应），新请求无法立即接入 |
| HN 的 Outstanding 表满 | HN 追踪的并发事务数已达上限 |
| PCrdType 资源不足 | HN 按 PCrdType（Permission Credit Type）限流，某种类型的许可额度已耗尽 |
| HN 内部缓冲区反压 | 向下发送的请求遇到 SN 反压，HN 无法继续接收新请求 |

### 1.2 Retry 协议流程

```
RN_F                             HN_F
  │                                │
  │──── ReadUnique(Addr A) ──────→│   RN 发起请求
  │                                │
  │←─── RetryAck(PCrdType=1) ─────│   HN 拒绝，指明需要哪种 Credit
  │                                │
  │   (RN 将请求挂起，等待 Credit)  │
  │                                │
  │←─── PCrdGrant(PCrdType=1) ────│   HN 有空位了，发放 Credit
  │                                │
  │──── ReadUnique(Addr A) ──────→│   RN 重发请求
  │                                │
  │←─── CompData(Data) ───────────│   HN 正常响应
```

**关键信号**：

- **`RetryAck`**：HN 返回的拒绝响应，携带 `PCrdType` 字段告诉 RN 需要哪种 Credit 才能重发
- **`PCrdGrant`**：HN 主动发送的 Credit 授权，RN 收到后才可重发对应类型的请求
- **`PCrdType`**：Permission Credit Type，区分不同请求类型（如 ReadShared / ReadUnique / CleanShared 等），HN 可按类型独立限流

### 1.3 Retry 的协议约束

1. **RN 收到 RetryAck 后必须停止发送同类型新请求**，直到收到对应的 PCrdGrant
2. **HN 必须保证公平性**：一旦资源释放，必须发出 PCrdGrant，不能无限期让 RN 等待
3. **RetryAck 不携带数据**——它不是缓存响应，仅是流控信号
4. **RN 可同时等待多个 PCrdType 的 Credit**，每种独立计数

### 1.4 Retry vs 反压（Back-pressure）

| 特性 | Credit-based 反压 | RetryAck/PCrdGrant |
|------|-------------------|-------------------|
| 触发时机 | 链路级，每 flit | 协议级，每事务 |
| 粒度 | 逐 flit 阻塞 | 按请求类型拒绝 |
| 恢复 | 链路有空间自动恢复 | 必须等 PCrdGrant |
| 语义 | "我暂时收不了" | "我处理不了这个请求，稍后重发" |

---

## 二、Snoop 场景

### 2.1 Snoop 的本质

Snoop 是 HN 向 RN 发出的**缓存查询请求**，询问："你有没有这个地址的缓存副本？如果有，请执行相应操作。"

**Snoop 类型**（CHI Issue E.b）：

| Snoop 类型 | 语义 | 期望的 RN 行为 |
|-----------|------|--------------|
| `SnpShared` | "我要把这个行降级为 Shared" | RN 若持有 Unique/Dirty 副本，必须写回数据；若持有 Shared 副本，可保留 |
| `SnpUnique` | "我要独占这个行" | RN 必须失效（Invalidate）其缓存副本，若是 Dirty 则写回数据 |
| `SnpClean` | "我要检查你是否持有 Clean 副本" | RN 报告缓存状态但不需要写回（仅用于目录更新）|
| `SnpNotShared` | "我要确认是否还有其他 Shared 持有者" | RN 报告是否持有 Shared 副本（用于目录优化）|
| `SnpStashShared` | "建议你缓存这个行（Shared）" | 非强制，RN 可选择是否接受 Stash |
| `SnpStashUnique` | "建议你缓存这个行（Unique）" | 非强制，RN 可选择是否接受 Stash |

### 2.2 典型 Snoop 场景

#### 场景 A：ReadUnique 触发的 Snoop

```
RN_0                             HN_F                              RN_1
  │                                │                                │
  │──── ReadUnique(A) ────────────→│                                │
  │                                │                                │
  │                                │──── SnpUnique(A) ────────────→│  HN 向 RN_1 发 Snoop
  │                                │                                │
  │                                │←─── RespI(I) ─────────────────│  RN_1 回复：无副本/已失效
  │                                │                                │
  │←─── CompData(Data, UC) ───────│                                │  HN 授权 RN_0 Unique
```

**目录状态变化**：`[Shared: {RN_1}] → [Unique: RN_0]`

#### 场景 B：SnpShared 降级

```
RN_0                             HN_F                              RN_1
  │                                │                                │
  │──── ReadShared(A) ────────────→│                                │
  │                                │                                │
  │                                │──── SnpShared(A) ────────────→│  RN_1 持有 Dirty 副本
  │                                │                                │
  │                                │←─── RespDataData(Data, UD) ──│  RN_1 写回脏数据
  │                                │                                │
  │←─── CompData(Data, SC) ───────│                                │  HN 授权双方 Shared
```

**目录状态变化**：`[Unique: RN_1, Dirty] → [Shared: {RN_0, RN_1}]`

#### 场景 C：Evict 触发的 Snoop（目录驱逐）

当 RN 想驱逐某行时，发 `Evict` 给 HN。HN 需要更新目录，可能触发 Snoop 确认无其他持有者。

```
RN_0                             HN_F
  │                                │
  │──── EvictClean(A) ────────────→│   RN_0 释放缓存行
  │                                │
  │←─── DBIDResp ─────────────────│   HN 确认 Evict 完成
  │                                │
  │   (HN 从目录中移除 RN_0)       │
```

### 2.3 LinkNan 中的 Snoop 实现

ClusterHub 有 `blockSnp` 机制：

```scala
// ClusterHub 中：blockSnp 屏蔽 Snoop 请求
when(io.blockSnp) {
  // Snoop 请求不传递到 core
  // Core 侧看不到 Snoop
}
```

**`blockSnp` 的正确使用时机**：

| 阶段 | blockSnp | 原因 |
|------|----------|------|
| Core 正常运行 | false | Core 必须响应 Snoop 维护一致性 |
| Core 进入 WFI/Wait | false | Core 仍需响应 Snoop |
| Core Power-Down 过渡中 | true | Core 正在刷 L1/L2，Snoop 请求会与 flush 冲突 |
| Core 已 Power-Down | true | Core 无法响应，Snoop 必须被屏蔽 |

**安全隐患**：`blockSnp` 无来源过滤——任何 Cluster 的 Snoop 都被统一屏蔽或放行，无法做到"只屏蔽来自特定源的 Snoop"。

---

## 三、Dataless 场景

### 3.1 什么是 Dataless 响应

**Dataless** 是 CHI 中"不需要传输数据"的响应组合。HN 在某些场景下只需传递权限/状态信息，不需要传输缓存行数据，节省带宽。

### 3.2 Dataless 响应类型

| 响应 | 携带数据？ | 语义 |
|------|-----------|------|
| `CompAck` | 否 | 完成确认，不传数据 |
| `Comp` (Resp=I) | 否 | 行已 Invalid，无数据可传 |
| `Comp` (Resp=UC/SC 等) | 否 | 仅报告权限，数据已在其他通道传输或 RN 已有副本 |
| `RespI` | 否 | Snoop 响应：RN 无此行 |
| `RespSnpDataless` | 否 | Snoop 响应：RN 有 Clean 副本，不需要传数据 |

### 3.3 Dataless 典型场景

#### 场景 A：ReadShared 命中 HN 缓存（数据在 HN，不需要 Snoop）

```
RN_0                             HN_F
  │                                │
  │──── ReadShared(A) ────────────→│
  │                                │  (HN 缓存中有 Clean 数据，目录显示无其他持有者)
  │←─── CompData(Data, SC) ───────│  直接返回数据（这不是 Dataless）
```

**但如果目录显示 RN_0 自己已持有 Shared 副本**：

```
RN_0                             HN_F
  │                                │
  │──── ReadShared(A) ────────────→│
  │                                │  (HN 发现 RN_0 已是 Shared 持有者)
  │←─── Comp(Resp=SC) ────────────│  Dataless：RN_0 已有数据，只需确认权限
```

#### 场景 B：SnpClean — 目录状态更新不需要数据

```
HN_F                             RN_0
  │                                │
  │──── SnpClean(A) ──────────────→│  HN 仅查询 RN_0 是否持有副本
  │                                │
  │←─── RespI(I) ─────────────────│  Dataless：RN_0 无副本，无需传数据
```

或：

```
HN_F                             RN_0
  │                                │
  │──── SnpClean(A) ──────────────→│
  │                                │
  │←─── RespSnpDataless(SC) ──────│  Dataless：RN_0 有 Clean Shared 副本，
  │                                │        数据已在缓存中，不需要传回
```

#### 场景 C：WriteBack 后的 Evict（数据已在 Evict 中传递）

```
RN_0                             HN_F
  │                                │
  │──── WriteBackFull(A, Data) ───→│  RN_0 写回脏数据
  │                                │
  │←─── DBIDResp ─────────────────│  Dataless 确认：数据已收到
  │                                │
  │──── Evict(A) ─────────────────→│  RN_0 驱逐目录条目
  │                                │
  │←─── CompAck ──────────────────│  Dataless 确认：Evict 完成
```

### 3.4 Dataless 与带宽优化

| 对比 | 带数据响应 | Dataless 响应 |
|------|-----------|-------------|
| CHI Data 通道使用 | 需要（64B cache line） | 不需要 |
| 带宽开销 | 1 flit (CMD) + N flits (DATA) | 1 flit (CMD) |
| 延迟 | 较高（等待数据传输） | 较低（仅命令 flit） |
| 典型应用 | 首次 miss、Dirty 写回 | 权限降级、目录更新、Evict 确认 |

**Dataless 的价值**：在 4 Cluster 系统中，约 30%~50% 的 Snoop 响应是 Dataless（RespI 或 RespSnpDataless），节省的带宽非常可观。

---

## 四、Retry + Snoop + Dataless 组合场景

### 场景：ReadUnique 被 Retry，Snoop 在 Retry 期间完成

```
RN_0                             HN_F                              RN_1
  │                                │                                │
  │──── ReadUnique(A) ────────────→│                                │
  │                                │  (HN 正在处理另一个同地址事务)
  │←─── RetryAck(PCrdType=1) ─────│                                │
  │                                │                                │
  │   (RN_0 等待 Credit)           │──── SnpUnique(A) ────────────→│  HN 完成前一个事务
  │                                │                                │  顺便 Snoop RN_1
  │                                │←─── RespI(I) ─────────────────│
  │                                │                                │
  │←─── PCrdGrant(PCrdType=1) ────│                                │
  │                                │                                │
  │8──- ReadUnique(A+PCrd) ──────→│                                │
  │                                │  (HN 现在有空间了，直接处理)
  │←─── Comp(Resp=UC) ────────────│  Dataless！数据已在 Snoop 期间缓存到 HN
```

**关键**：HN 在 Retry 期间可能已经通过 Snoop 获取了数据缓存到自身，所以 RN_0 重发时 HN 可以用 Dataless 响应（Comp 不带 Data），RN_0 的数据由 HN 的缓存提供——但实际 CHI 中 Comp 权限响应通常仍需伴随 Data，具体取决于 HN 的实现策略。

---

## 五、LinkNan / ZhuJiang 中的实现现状

### Retry

ZhuJiang NoC 内部 HNF（Home Node）实现了 outstanding table 和 credit-based 流控。当 outstanding 表满时会触发 RetryAck。**但没有 PCrdType 分级**——所有请求类型共享同一 Credit 池，简化但丧失了按类型限流的能力。

### Snoop

通过 `ClusterHub.blockSnp` 做功能性屏蔽，**不做安全过滤**。Snoop 在 NoC 中按 NodeID 路由到目标 CC 节点。

### Dataless

ZhuJiang HNF 的目录协议在 Evict/WriteBack 确认、Snoop Clean 响应等场景使用 Dataless 响应，节省 Data 通道带宽。**但未实现 Stash（SnpStashShared/SnpStashUnique）**——Stash 是 CHI Issue B+ 的可选特性，允许 HN 建议而非强制 RN 缓存数据。

---

## 六、安全视角

| 机制 | 安全风险 | 缓解措施 |
|------|---------|---------|
| Retry | 恶意 RN 可高频发请求消耗 HN Credit → DoS | 按 SourceID 独立 Credit 配额（需 AMU/MPAM） |
| Snoop | 恶意 RN 可通过高频 Snoop 探测其他 RN 的缓存内容（侧信道） | Snoop 频率限制 + Watchpoint 监控 |
| Dataless | Dataless 响应不携带数据，无法做完整性校验 | End-to-End ECC 应覆盖命令 flit 中的状态字段 |

---

[ClusterHub 与 CHI 桥接](10-clusterhub-and-chi-bridging)
[NoC 拓扑配置](14-noc-topology-configuration)
[UncoreTop 与 NoC 集成](6-uncoretop-and-noc-integration)