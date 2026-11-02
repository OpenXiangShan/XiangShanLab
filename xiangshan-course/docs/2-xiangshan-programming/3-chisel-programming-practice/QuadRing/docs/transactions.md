# QuadRing 事务与一致性说明

本文合并了能力参考和按真实请求的走读：先给能力表和实现约定，再按 RN0/RN1 的本地命令展开一致性路径，最后给端点状态机、完成边界、断言和验证入口。所有结论以 `src/main/scala` 为准；图只表示消息路径和因果顺序，不表示时钟周期。

本文只描述 `coherent = true` 的配置。`ReadNoSnp` 和 `WriteNoSnpFull` 仅作为 HN 访问 SN 的内部请求出现，不作为 RN 的事务形态讨论。

先给结论：

- 系统运行 `coherent = true`：RN 有直接映射 cache，HN 有目录；产品 RN 真正发出的 REQ 只有 `ReadShared`、`ReadUnique`、`Evict`、`WriteBackFull` 四个。
- HN 端点接受六个 REQ opcode（额外两个是 `ReadNoSnp`/`WriteNoSnpFull`），SN 只接受这两个；系统的下行访问由 HN 发起。
- 所有数据都经过 HN；没有 DMT、没有 RN↔RN 直接传输、没有转发 snoop。
- 每个 RN 一笔本地命令；HN 一笔活动事务；SN 一次内存操作。并发靠 HN 串行化和 retry 排队。
- 本地 cache 命中不产生任何 Ring flit。

## 0. 能力总表

### 0.1 CPU 可见能力

| 本地命令 | 条件 | 线上事务 | 本地结果 |
|---|---|---|---|
| 读 | cache 命中 SC/UC/UD | 无 | cache 数据 |
| 写 | cache 命中 UC/UD | 无 | 0 |
| 写 | cache 命中 SC | `ReadUnique` | 0 |
| 读 | cache miss | `ReadShared` | 返回的整行 |
| 写 | cache miss | `ReadUnique` | 0 |
| 任意 | 同索引有 victim | 先 `Evict` 或 `WriteBackFull` | victim 阶段结束后再发原命令 |

### 0.2 线上 opcode

| 通道 | opcode | 方向 | 系统何时使用 |
|---|---|---|---|
| REQ | `ReadNoSnp` | HN→SN | HN 读内存 |
| REQ | `WriteNoSnpFull` | HN→SN | HN 写内存 |
| REQ | `ReadShared` | RN→HN | 读 miss |
| REQ | `ReadUnique` | RN→HN | 写 miss 或 SC 命中升级 |
| REQ | `Evict` | RN→HN | 干净替换 |
| REQ | `WriteBackFull` | RN→HN | 脏替换 |
| SNP | `SnpShared` | HN→RN | 共享 snoop |
| SNP | `SnpUnique` | HN→RN | 独占 snoop |
| RSP | `SnpResp` | RN→HN | snoop 无数据响应 |
| RSP | `CompAck` | RN→HN | 一致读/写完成确认 |
| RSP | `RetryAck` | HN→RN；SN→HN | 拒绝首次请求 |
| RSP | `PCrdGrant` | HN→RN；SN→HN | 授予重发信用 |
| RSP | `Comp` | HN→RN；SN→HN | Evict 完成；SN 写完成 |
| RSP | `CompDBIDResp` | HN→RN | 授予 RN 写数据槽 |
| RSP | `DBIDResp` | SN→HN | 授予 HN 写数据槽 |
| DAT | `CompData` | SN→HN；HN→RN | 读数据 |
| DAT | `SnpRespData` | RN→HN | snoop 携带脏数据 |
| DAT | `CopyBackWrData` | RN→HN | 脏替换写回 |
| DAT | `NonCopyBackWrData` | HN→SN | HN 写内存数据 |

### 0.3 端点能力不等于系统能力

HN 端点也接受 `ReadNoSnp` 和 `WriteNoSnpFull`，但前提是目标行在目录中未被持有，而且不会为它分配目录项。产品中的 RN 从不发这两个 opcode；一致 RN 也无法消费 `CompData(I)`，因为它在 `datRx` 断言 `resp` 只能是 SC/UC/UD_PD。因此：

- HN 端点接受 6 个 REQ opcode。
- 产品 RN 只发 4 个。
- SN 永远只接受 `ReadNoSnp` 和 `WriteNoSnpFull`。

本文后面说的“系统能力”，指产品 RN 实际会发起、HN/SN 实际会执行的组合；端点测试能注入的额外组合会单独说明。

## 1. 系统模型与约定

### 1.1 端点、拓扑和状态

默认拓扑是 RN0/Stop0、HN/Stop1、RN1/Stop2、SN/Stop3。四条环方向都是 `0 → 1 → 2 → 3 → 0`。

| 角色 | 默认 Stop | 保存的状态 | 职责 |
|---|---|---|---|
| RN0/RN1 | 0/2 | 一个本地命令上下文；cache；victim 缓冲；一份 snoop 响应暂存 | 选 opcode、发 REQ、安装数据、发 CompAck、响应 snoop、返回本地结果 |
| HN | 1 | 一个活动事务；每行两个 holder 的目录；retry 等待表；下游 ID/DBID；待转发数据 | 准入或拒绝、查目录、发 snoop、读写 SN、把数据转回 RN |
| SN | 3 | 内存行；一个活动内存操作；一个 retry 等待记录 | 读/写整行，先授权写数据，写完再发 Comp |
| RingStop | 每个 Stop | 每通道 FIFO（默认 3 项）；到下一 Stop 的 credit | 入队、按 dst 本地弹出或转发、仲裁本地注入 |

端点都是 Diplomacy `SourceNode`，网络是 `SinkNode`；连接顺序决定物理位置，CHI node ID 可以稀疏或重排。见 [ChiSystem.scala](../src/main/scala/chi/ChiSystem.scala)、[ChiNoC.scala](../src/main/scala/chi/ChiNoC.scala)。

约束：

- 每次访问完整 64 B；一条 DAT flit 携带全部 512 bit；地址必须 64 B 对齐。
- 每个 RN 一次只接受一条本地命令；`io.request.ready` 还要求当前没有正在输入的 snoop。
- HN 只有一笔活动事务，但可以有多个被拒绝的请求在 retry 表里等待。
- SN 一次执行一个内存操作，另有一个被拒绝的请求等待重发。
- HN 不缓存数据，只暂存当前事务需要的一整行。
- cache 和目录大小必须是大于 1 的 2 的幂；目录必须覆盖 SN 内存，且不做目录替换。
- SN 内存行数只要求大于 0；地址按 `addr >> 6` 索引，越界会断言。

### 1.2 数据粒度和本地接口

- 行大小 64 字节，所有地址必须 64 字节对齐（低 6 位为零）。
- 一次 DAT 传输承载完整 512 bit 行，没有多 beat、没有字节掩码。
- 所有写都是整行替换；没有部分写、没有 `WriteUnique` 系列。

本地接口见 [RnPort.scala](../src/main/scala/chi/RnPort.scala)：

```text
request  = {write, addr, data, txnID}
response = {txnID, data}
```

读返回整行；写返回 0。`response` 被反压时结果保持稳定，RN 不接受新的本地命令，但 snoop 仍可处理。

### 1.3 内部 flit 与标准 CHI 线格式的差异

内部 flit 是精简结构，不是完整的 CHI 线格式：

| 通道 | 字段 | 默认位宽 |
|---|---|---:|
| REQ | `tgtID, srcID, txnID, opcode, addr, allowRetry, pCrdType` | 67 |
| RSP | `tgtID, srcID, txnID, opcode, resp, dbID, pCrdType` | 23 |
| SNP | `srcID, txnID, opcode, addr` | 59 |
| DAT | `tgtID, srcID, txnID, opcode, resp, dbID, data` | 530 |

没有 `MemAttr`、`Size`、`NS`、`Order`、`QoS`、`PBHA`、`Expect`/`ReturnNID`、`FwdNID`、`VMID` 等标准字段。REQ 和 SNP 的 `addr` 是包含 6 bit 行内偏移的完整字节地址，默认 48 bit。SNP 没有 `tgtID`，由发送方额外提供 `dst` 供环路由。

`opcode` 宽度：REQ 6 bit、RSP 4 bit、SNP 5 bit、DAT 3 bit。`pCrdType` 4 bit，本实现只用 type 0。cache 状态 `I/SC/UC/UD` 用 2 bit 内部编码；线协议 `resp` 用 3 bit，包含 `I_PD/SC_PD/UD_PD`，两套编码不可混用。

### 1.4 ID 语义

图中的 `A` 是 64 B 对齐地址，`D` 是整行数据。ID 约定：

| ID | 本实现取值 | 用途 |
|---|---|---|
| `t` | RN 本地命令的 `txnID` | RN→HN 的 REQ；HN→RN 的 `CompData`、`CompDBIDResp`、`Comp` 恢复为 `t` |
| `h` | HN 下游事务 ID，当前固定 0 | HN→SN 的 REQ；SN→HN 的 `CompData`、`DBIDResp`、`Comp` |
| `s` | HN snoop ID，当前固定 0 | SNP 和 `SnpResp`/`SnpRespData` |
| `dH` | HN 数据槽 ID，当前固定 0 | `CompDBIDResp.dbID`；RN 写 DAT 的 `txnID`；`CompData.dbID`；`CompAck` 使用收到的 `CompData.dbID` |
| `dS` | SN 数据槽 ID，当前固定 0 | `DBIDResp.dbID`；HN 写 DAT 的 `txnID`；SN 写完成 `Comp.dbID` |

容易混淆的点：

- HN 保存上游的 `(srcID, txnID)` 作为事务身份并用于重试校验；向 SN 发请求时把 `srcID` 改成 HN、`tgtID` 改成 SN、`txnID` 改成 `h`。返回 RN 时恢复 `t`。HN 是重新构造消息，不是把同一条 flit 原样转发。
- 写 DAT 用 `CompDBIDResp.dbID` 作为 `txnID`；`CompAck` 用收到的 `CompData.dbID`。当前两者都为 0，但匹配规则不同，不能拿原请求的 `t` 去匹配。
- `RetryAck` 的 `txnID` 是被拒绝请求的 ID；`PCrdGrant` 的 `txnID` 固定为 0，按“提供方 + `pCrdType`”匹配，不能按请求 ID 匹配。
- `SnpResp`/`SnpRespData` 的 `txnID` 是 snoop ID `s`，不是原读请求的 `t`。
- 消息身份或字段错误会触发断言；正常输入下状态才会按上述路径推进。

### 1.5 cache 和目录

每个 RN 有一个直接映射 cache，默认 8 行。索引是 `addr[6 + indexWidth - 1 : 6]`，tag 是更高位；一个 DAT flit 就是一行。cache 状态是内部 2 bit 编码 I/SC/UC/UD，和线上 3 bit 的 `resp` 是两套编码。

| cache 状态 | 含义 | 本地读 | 本地写 | 替换 |
|---|---|---|---|---|
| I | 无有效副本 | 缺失，发 `ReadShared` | 缺失，发 `ReadUnique` | 无 |
| SC | 共享、干净 | 命中，本地返回 | 发 `ReadUnique` 获取独占权 | `Evict → I` |
| UC | 独占、干净 | 命中，本地返回 | 本地修改，变 UD | `Evict → I` |
| UD | 独占、脏 | 命中，本地返回 | 本地修改，保持 UD | `WriteBackFull → I` |

HN 目录默认 64 行，每行两个 holder 槽，每个槽记录 `valid/id/state`，state 只有 `I/SC/UC`：

- 最多一个 RN 持有 UC；如果某行是 UC，另一个 RN 必须是 I。
- 两个 RN 都是 SC 时，SN 必须是最新数据（在事务完成边界）。
- 目录不记录 UD：RN 从 UC 静默变 UD 时，HN 仍按 UC 记录。因此 HN 的“UC”表示“独占权限”，不表示干净。
- 目录覆盖 SN 的全部内存；没有目录替换，一致请求的地址超出目录范围会断言。
- 目录是保守记录：`Evict` 或 `WriteBackFull` 已发出但尚未处理时，HN 可能仍保留旧 holder；如果此时发生 snoop，可能得到 `I`（干净 victim）或 `I_PD`（脏 victim）。在事务静止且请求处理完的边界检查目录与 RN 持有关系，不能逐周期要求精确。
- 产品 RN 不直接发 `ReadNoSnp`/`WriteNoSnpFull`；它们只作为 HN 访问 SN 的下游请求（见 6.4、7）。

### 1.6 复位

复位是全系统复位：清空所有端点的活动上下文、retry 标记、等待表、预留、待发响应、cache、目录、Ring FIFO，并恢复所有 link credit；SN 内存恢复 `initialLines`。复位不保留脏数据，也不支持某个端点单独复位后继续使用旧网络消息。

## 2. Ring 传输层

### 2.1 拓扑与跳数

REQ、RSP、SNP、DAT 各有一条独立物理环，四个 Stop 的物理顺序相同：

```text
                环方向（顺时针）
        RN0  ────────────────────────►  HN
         ▲                               │
         │                               │
         │                               ▼
        SN   ◄────────────────────────  RN1

        物理位置索引：0=RN0, 1=HN, 2=RN1, 3=SN
```

端点之间没有 RN↔SN、SN↔RN 或 RN↔RN 的直接消息；所有消息都以 HN 为源或目的。单向绕行导致往返跳数不对称：

| 从 \ 到 | RN0 | HN | RN1 | SN |
|---|---:|---:|---:|---:|
| **RN0** | – | 1 | 2 | 3 |
| **HN** | 3 | – | 1 | 2 |
| **RN1** | 2 | 3 | – | 1 |
| **SN** | 1 | 2 | 3 | – |

例如 RN0 到 SN 要 3 跳，SN 回 RN0 只要 1 跳。HN→RN0 的消息会经过 RN1 和 SN 的 Stop，但只有 `dst` 等于本 Stop 时才弹出；经过不等于收到。

### 2.2 Stop 内部与仲裁

每个 Stop 每通道一个 FIFO，只有环输入进入 FIFO；本地注入不经过本 Stop 的 FIFO：

```text
                                  dst == local
ringIn → FIFO head ────────────────→ local.eject → endpoint
                 └── dst != local → ringOut → 下一 Stop 的 FIFO
local.inject ─────────────────────→ ringOut
```

规则：

- 只有 FIFO 队头能离开。队头是本地目的时等待 `eject.ready`；不是本地目的时等待 `ringOut.ready` 和至少 1 个下游 credit。
- 转发优先于注入。有可转发的队头时，本地注入不能抢输出；转发需要 `downstreamCredits >= 1`，注入需要 `>= 2`。
- 注入要求 2 个空槽，是为了在环上保留一个 bubble，防止一组已接受的 flit 把整圈填满后无法前进。
- 任意 FIFO 出队（本地弹出或转发）都会向上游返回一个 credit；返回的 credit 下一周期才可用。
- 本地弹出和本地注入可以同一周期发生；本地队头被阻塞时，它后面的 flit 不能越过它。
- `RingStop` 的输出是普通 `Decoupled`，不是 `Irrevocable`：在 `valid` 已拉高但未被接受期间，新到的转发可以替换尚未传输的注入。只有 `valid && ready` 才真正传输。

最后一点容易和端点内部的行为混淆：RN 的 RSP/DAT 输出仲裁在停滞时会锁定已呈现的选择（见 3.4），那是端点自己的约束，不是 `RingStop` 的。

### 2.3 credit 与 bubble

每个 Stop 只统计下一 Stop 的 FIFO 空位：

```text
downstreamCredits + downstream FIFO occupancy = bufferDepth
forward allowed: downstreamCredits >= 1
inject allowed:  downstreamCredits >= 2
```

发送一个 flit 消耗一个 credit；下游出队一个 flit 返回一个 credit。发送和返回同周期发生时相互抵消。返回的 credit 被寄存，下一周期才参与判断。复位清空 FIFO 并恢复 `bufferDepth`。

这里有两套不同的“信用”：

- **Ring credit**：下一 Stop 的 FIFO 空位，按 flit/hop 计数。
- **P-Credit**：HN/SN 的一个事务资源，按请求计数；拿到它只表示完成方预留了上下文，不表示环上一定有位置。

### 2.4 路由与物理位置

`ChiNoC` 把 CHI 目的 node ID 映射成外层 `RingFlit.dst`：REQ/RSP/DAT 使用 `tgtID`；SNP 没有 `tgtID`，使用独立的 `RoutedSnp.dst`。默认 node ID 等于 Stop 索引，但代码支持稀疏或重排的 ID；`dst` 是物理 Stop 索引，不一定等于 `tgtID`。未知目标在 `valid` 时直接断言，重复端点 ID 在 elaboration 阶段失败。见 [ChiNoC.scala](../src/main/scala/chi/ChiNoC.scala) 和 [ChiFlit.scala](../src/main/scala/chi/ChiFlit.scala)。

传输层不解析 opcode，只按 `dst` 转发或 eject。

### 2.5 跨通道没有全局顺序

四条环彼此独立，REQ、RSP、SNP、DAT 之间没有物理顺序保证。协议顺序靠消息依赖维持：

- RN 不会在收到 `CompDBIDResp` 之前发写 DAT。
- HN 不会在收到 SN 数据或 snoop 数据之前向 RN 发 `CompData`。
- HN 只在收到 SN 写完成 `Comp` 之后才认为下游写结束。
- RN 先安装数据，再发 `CompAck`；HN 收到 `CompAck` 才释放一致读上下文。

同一通道、同一源发出的消息在环上保持 FIFO 顺序，不会被后来的消息超越；不同源的消息可以交错。HN 的串行化和 retry 负责把不同源的交错整理成可执行的顺序。一个直接后果是重试的 `RetryAck` 与 `PCrdGrant` 可以任意顺序到达，请求方必须分别记账。

## 3. RN 本地行为

### 3.1 cache 状态与决策

RN 在发出线上请求前先看本地 cache：

| 本地命令 | cache 状态 | RN 做什么 | 线上事务 |
|---|---|---|---|
| 读 | 命中 SC/UC/UD | 直接返回 cache 数据 | 无 |
| 写 | 命中 UC/UD | 本地写，转 UD | 无 |
| 写 | 命中 SC | 需要独占权 | `ReadUnique` |
| 读 | miss | 需要数据 | `ReadShared` |
| 写 | miss | 需要数据和独占权 | `ReadUnique` |
| 任意 | miss，同索引有 victim | 先腾位置 | `Evict` 或 `WriteBackFull`，再发原命令 |

实现细节见 [RnNode.scala](../src/main/scala/chi/RnNode.scala#L85)：

- 读命中：直接把 `cacheData` 放进结果，`idle → returnResult → idle`，没有 Ring flit。
- 写命中 UC/UD：本地写 `cacheData`，状态置 UD，结果 `data=0`。UC→UD 是静默的，HN 目录仍把该行记作 UC。
- 写命中 SC：不能静默升级，发 `ReadUnique` 获取独占权。
- 读缺失：发 `ReadShared`。
- 写缺失：发 `ReadUnique`。
- 同一索引、不同 tag 的有效行是 victim：先把它移出 cache，再发 `Evict`（干净）或 `WriteBackFull`（脏）；victim 阶段结束后才发原命令。

`ReadUnique` 的 `CompData` 一定落在写路径：RN 用本地写数据 `W` 安装成 UD，不使用 HN 返回的旧数据。`ReadShared` 则使用返回的数据；它在没有其他 holder 时可能返回 `UC`，此时 RN 安装读回的数据，状态为 UC。

命中路径不产生任何 flit，也不需要 snoop 或 `CompAck`。下面的场景都是 miss、升级或替换。

### 3.2 替换：victim 和原命令是两笔请求

```text
同一索引访问不同 tag
  → 选中 victim，保存 victimAddress / victimData / victimDirty
  → 立即把 victim 行置 I
  → 干净 victim:  REQ Evict(victim)         → 等 Comp
  → 脏 victim:   REQ WriteBackFull(victim)  → 等 CompDBIDResp → 发 CopyBackWrData
  → resumeRequest(): 用原命令的 write 选择 ReadUnique 或 ReadShared
  → 发原命令
```

细节：

- victim 地址由 `{tag, index, 6'b0}` 重建；`victimData` 保存整行；`victimDirty` 只在该行是 UD 时为真。
- victim 请求和原命令使用同一个本地 `txnID=t`。它们不是并发事务：RN 等 victim 阶段结束（Evict 的 Comp，或 WriteBackFull 的数据注入）后才发原命令；HN 按顺序处理，并用 opcode/地址校验身份。
- 干净 victim 只发 `Evict`，没有 DAT；HN 清目录后回 `Comp`。
- 脏 victim 发 `WriteBackFull`，HN 先回 `CompDBIDResp` 授权数据，RN 再发 `CopyBackWrData(UD_PD, D)`；HN 收到后清目录并写 SN。
- 如果 victim 在等待授权/信用期间被 snoop 取走，RN 回 `SnpRespData(I_PD, D)` 并清 `victimDirty`；原来的 WriteBackFull 仍然要完成授权和数据阶段，最后发 `CopyBackWrData(I, data=0)`，HN 丢弃，不写 SN。取消的写回仍有一条 DAT flit，不能算作“整笔无数据事务”。
- `resumeRequest()` 会清空 retry 标记并回到 `sendReq`；原命令可能被 HN 再次 RetryAck，这是正常的。

### 3.3 RN 状态机

```text
idle → sendReq → waitData → sendCompAck → returnResult → idle
       sendReq → waitDbId → sendWriteData → sendReq           (写回后继续原命令)
       sendReq → waitComp → sendReq                           (Evict 后继续原命令)
```

| 状态 | 进入条件 | 离开事件 | 下一状态 |
|---|---|---|---|
| `idle` | 复位或上一结果被取走 | 本地命令握手 | 命中/写 UC/UD → `returnResult`；否则 `sendReq` |
| `sendReq` | 有新命令或重发 | `reqTx.fire` | `ReadShared`/`ReadUnique` → `waitData`；`WriteBackFull` → `waitDbId`；`Evict` → `waitComp` |
| `waitData` | 等 `CompData` | 匹配的 `datRx.fire` | 安装数据 → `sendCompAck` |
| `waitDbId` | 等 `CompDBIDResp` | 匹配的 `rspRx.fire` | `sendWriteData` |
| `waitComp` | 等 Evict 的 `Comp` | 匹配的 `rspRx.fire` | `resumeRequest()` → `sendReq` |
| `sendWriteData` | 有写数据槽 | `datTx.fire` | 清 `victimDirty` → `sendReq` |
| `sendCompAck` | 数据已安装 | `rspTx.fire` | `returnResult` |
| `returnResult` | 有本地结果 | `response.fire` | `idle` |

`waitingForReply = waitData || waitDbId || waitComp` 是接收 RSP 的条件；`retryAckSeen && creditHeld` 时 `rspRx.ready` 关闭，RN 回到 `sendReq` 重发。接收 `datRx` 还会被 `rspRx.valid`、重试标记和 snoop 优先级挡住，避免在重试待决时把旧数据当成完成。

### 3.4 snoop 与本地事务并行

RN 还有一条 snoop 响应路径。它不占用本地命令上下文，也不要求本地命令已经完成：

- `snpRx.ready = !snoopPending`：一次接收一个 snoop，查 cache 或 victim，保存响应，直到 RSP/DAT 注入完成才接受下一个 snoop。
- `snoopPending` 期间本地主状态可以继续等待数据、等待 DBID 或等待重试；snoop 响应和本地事务并行。
- snoop 响应优先于主 RSP/DAT：两者同时新有效时选 snoop；如果主消息已经被反压锁住，则保持主消息不变，等它发完再发 snoop；反过来，snoop 被反压时也保持 snoop，不会被主消息替换。
- 本地结果被 CPU 反压时，RN 仍能响应 snoop；这是系统能完成 snoop 竞争的关键。

## 4. 从 RN0/RN1 的真实请求看一致性

下面每个场景都从 CPU 在某个 RN 上的本地命令开始。`ReadShared`、`ReadUnique`、`Evict`、`WriteBackFull` 不是讨论对象，而是 RN 为完成这条命令发出的消息；HN 再用 `ReadNoSnp`/`WriteNoSnpFull` 访问 SN。

这些场景共同维护两条不变量：

1. 任一行最多只有一个 RN 持有独占/写权限（UC 或 UD），其余持有者只能是 SC 或 I。
2. 只要还存在 SC 持有者，内存就必须是最新的；唯一的 UD 持有者可以把脏数据留在 cache。

HN 的目录、snoop 和 `CompAck` 完成边界，都是为在请求路径上维持这两条不变量。

### 4.1 场景 1：RN0 读 miss，没有其他副本

CPU 在 RN0 上读 A；RN0 未缓存 A，HN 目录也没有 A。

```text
RN0                     HN                      SN
 |-- REQ ReadShared ---->|                       |
 |                       |-- REQ ReadNoSnp ----->|
 |                       |<---- DAT CompData(I) -|
 |<---- DAT CompData(UC)-|                       |
 |-- RSP CompAck -------->|                       |
```

1. RN0 发 `ReadShared`（`tgtID = HN`，`srcID = RN0`）。
2. HN 查目录，A 没有 holder，也没有其他 RN 需要 snoop。它把 RN0 记为 UC holder，然后向 SN 发 `ReadNoSnp`。
3. SN 返回 `CompData(I)`。
4. HN 把数据以 `CompData(UC)` 发给 RN0。没有其他副本，所以直接给独占权限。
5. RN0 安装 UC，向 CPU 返回数据，并发送 `CompAck`。
6. HN 收到 `CompAck` 后才释放上下文。

完成点：CPU 的读结果在 `CompAck` 注入后才返回；HN 在收到 `CompAck` 前一直保留这笔事务和目录状态。

### 4.2 场景 2：RN1 读 miss，RN0 持有干净副本

CPU 在 RN1 上读 A；RN0 已经以 SC 或 UC 持有 A。

```text
RN1                    HN                     RN0                SN
 |-- REQ ReadShared --->|                      |                  |
 |                      |-- SNP SnpShared ---->|                  |
 |                      |<-- RSP SnpResp(SC) --|                  |
 |                      |-- REQ ReadNoSnp ----------------------->|
 |                      |<-- DAT CompData(I) ---------------------|
 |<---- DAT CompData(SC)-|                     |                  |
 |-- RSP CompAck ------->|                      |                  |
```

1. RN1 发 `ReadShared`。
2. HN 目录显示 RN0 是 holder 且不是请求者，于是发 `SnpShared`。
3. RN0 是干净的，回 `SnpResp(SC)`，自己保持 SC（如果原来是 UC，会降为 SC）。
4. snoop 响应不带数据，HN 需要自己去 SN 取数据：发 `ReadNoSnp`，SN 回 `CompData(I)`。
5. HN 把数据以 `CompData(SC)` 发给 RN1；RN1 安装 SC。
6. RN1 回 `CompAck`；HN 在目录中把 RN1 记为 SC holder。

完成点同上。

### 4.3 场景 3：RN1 读 miss，RN0 持有脏副本

CPU 在 RN1 上读 A；RN0 以 UD 持有 A，数据比内存新。

```text
RN1                    HN                     RN0                SN
 |-- REQ ReadShared --->|                      |                  |
 |                      |-- SNP SnpShared ---->|                  |
 |                      |<-- DAT SnpRespData --|                  |
 |                      |     (SC_PD, 脏数据)   |                  |
 |                      |-- REQ WriteNoSnpFull ---------------->|
 |                      |<-- RSP DBIDResp ----------------------|
 |                      |-- DAT NonCopyBackWrData -------------->|
 |                      |<-- RSP Comp --------------------------|
 |<---- DAT CompData(SC)-|                     |                  |
 |-- RSP CompAck ------->|                      |                  |
```

1. RN1 发 `ReadShared`；HN 向 RN0 发 `SnpShared`。
2. RN0 是 UD，回 `SnpRespData(SC_PD)` 并携带数据，自己降为 SC。
3. 因为 RN0 还保留共享副本，内存必须变干净。HN 先把这份脏数据写回 SN（`WriteNoSnpFull` → `DBIDResp` → `NonCopyBackWrData` → `Comp`）。
4. 写回完成后，HN 才把同一份数据以 `CompData(SC)` 发给 RN1。
5. RN1 安装 SC 并回 `CompAck`。

关键点：`SnpShared` 之后 holder 仍存在，所以先清内存；如果 holder 失效，则不需要写回，权限可以直接给 UC。

### 4.4 场景 4：RN1 写 miss，RN0 持有脏副本

CPU 在 RN1 上写 A；RN1 未缓存 A，RN0 以 UD 持有 A。

```text
RN1                    HN                     RN0
 |-- REQ ReadUnique --->|                      |
 |                      |-- SNP SnpUnique ---->|
 |                      |<-- DAT SnpRespData ---|   （I_PD）
 |<---- DAT CompData(UD_PD) --------------------|   （脏数据直接转交）
 |  安装本地写数据为 UD   |                      |
 |-- RSP CompAck ------->|                      |
```

1. 写 miss 需要独占权，RN1 发 `ReadUnique`。
2. HN 向 RN0 发 `SnpUnique`，要求失效。
3. RN0 回 `SnpRespData(I_PD)`，交出脏数据并变为 I。
4. HN **不写 SN**，把数据作为 `CompData(UD_PD)` 直接转交给 RN1。
5. RN1 不使用返回的数据，而是安装自己的写数据并置 UD，然后回 `CompAck`。

关键点：这是所有权转移，不是写内存。内存仍然是旧值，新的脏副本在 RN1。若 RN0 响应是干净的 `SnpResp(I)`，HN 仍从 SN 取数据，返回 `UC`。

### 4.5 场景 5：RN0 写命中 SC（升级）

CPU 在 RN0 上写 A；RN0 已经以 SC 持有 A。

```text
RN0                     HN                      SN
 |-- REQ ReadUnique ---->|                       |
 |                       |-- REQ ReadNoSnp ----->|
 |                       |<---- DAT CompData(I) -|
 |<---- DAT CompData(UC)-|                       |
 |  安装本地写数据为 UD   |                       |
 |-- RSP CompAck -------->|                       |
```

1. SC 不能直接写，RN0 发 `ReadUnique`。
2. HN 目录里只有请求者自己，不需要 snoop；但它仍会向 SN 重读一次（保守实现）。
3. HN 返回 `CompData(UC)`。RN0 丢弃数据，安装自己的写数据并置 UD。
4. RN0 回 `CompAck`。

关键点：这条路径没有 snoop，也没有写内存；它只是把 SC 升级成 UD。HN 重读 SN 在语义上不是必需的，但实现选择了统一路径。

### 4.6 场景 6：命中的本地请求

- 读命中 SC/UC/UD：RN 直接返回 cache 行。
- 写命中 UC/UD：RN 直接修改 cache，状态转 UD。

以上都不产生任何 flit，也不需要 snoop 或 `CompAck`。这是一致性协议的快路径；只有 miss、SC 写升级和替换才会进入线上事务。

### 4.7 场景 7：miss 且同索引有 victim

直接映射 cache 的索引被别的行占用时，RN 必须先处理 victim，再发原命令。victim 和原命令是两笔独立请求。

干净 victim（SC/UC）：

```text
RN0                    HN
 |-- REQ Evict --------->|
 |<-- RSP Comp ----------|
 |-- REQ <原命令> ------->|
```

- RN 在接受本地命令时就把 victim 移出 cache。
- `Evict` 让 HN 删除该 holder 记录并回 `Comp`；不访问 SN。
- RN 收到 `Comp` 后发原命令（`ReadShared` 或 `ReadUnique`）。
- HN 的可达情况：发送方是 holder 且无其他 holder → 清除；发送方是 holder 且另一 RN 也持 SC → 只清发送方；发送方不是 holder 或重复 `Evict` → 不清任何项，仍回 `Comp`。
- HN 不检查这一行是否真的干净；产品 RN 只会对 SC/UC 干净行发 `Evict`，脏行必须走 `WriteBackFull`。

脏 victim（UD）：

```text
RN0                    HN                      SN
 |-- REQ WriteBackFull ->|                       |
 |<-- RSP CompDBIDResp --|                       |
 |-- DAT CopyBackWrData ->|                      |
 |-- REQ <原命令> ------->|                       |
 |                       |-- REQ WriteNoSnpFull->|
 |                       |<---- RSP DBIDResp ----|
 |                       |-- DAT NonCopyBackWrData ->|
 |                       |<---- RSP Comp ---------|
```

- RN 发 `WriteBackFull`，等 `CompDBIDResp` 后把脏数据作为 `CopyBackWrData(UD_PD)` 发出。
- DAT 注入后 RN 立即发原命令，不等 SN 写完；原命令如果先到，会被 HN `RetryAck`。
- HN 等 SN 的 `Comp` 才算写回完成。

取消路径：如果 `WriteBackFull` 还没收到 `CompDBIDResp`，而 snoop 命中 victim，RN 用 `SnpRespData(I_PD)` 把数据交给 snoop 并清除 `victimDirty`；原来的写回继续，但 `CopyBackWrData` 带 `resp = I` 和全零数据，HN 丢弃、不写 SN。

### 4.8 场景 8：两个 RN 争用 HN

RN0 正在写回另一行的脏 victim，RN1 发来写 miss 的 `ReadUnique`。HN 只有一个活动上下文，于是：

```text
RN1                         HN
 |-- REQ ReadUnique(allowRetry=1) -->|
 |<-- RSP RetryAck ------------------|
 |                                   |  （HN 完成 RN0 的写回）
 |<-- RSP PCrdGrant -----------------|
 |-- REQ ReadUnique(allowRetry=0) -->|
 |<-- 正常事务 ---------------------->|
```

- 第一次请求被存入等待表，HN 回 `RetryAck`。
- HN 空闲后把等待项搬进上下文并回 `PCrdGrant`。
- RN1 收齐 `RetryAck` 和 `PCrdGrant`（顺序任意）后，用相同身份重发，`allowRetry = 0`。
- HN 对 credited retry 不能再拒绝。

这就是两个 RN 共享一个 HN 时的排队方式：不是让请求停在环上，而是协议层拒绝后重新授权。

### 4.9 路径总表与 snoop 数据来源

产品 RN 只发四个 REQ，但每个 opcode 的结果由目录和对方 cache 状态决定。下面把所有可达路径列全。

#### ReadShared

| 对方/目录状态 | HN 动作 | SN | 返回给请求方 | 请求方新状态 |
|---|---|---|---|---|
| 无 holder | 直接读 SN | 读 | `CompData(UC)` | UC |
| 干净 SC/UC 驻留 | `SnpShared` → `SnpResp(SC)` | 读 | `CompData(SC)` | SC |
| UD 驻留 | `SnpShared` → `SnpRespData(SC_PD,D)` | 先写 D | `CompData(SC)` | SC |
| 脏 victim | `SnpShared` → `SnpRespData(I_PD,D)` | 先写 D | `CompData(UC)` | UC |
| 干净 victim/已 Evict（目录暂时保守） | `SnpShared` → `SnpResp(I)` | 读 | `CompData(UC)` | UC |

关键点：

- 没有其他 holder 时返回 UC，不是 SC；一次读也能拿到独占权，之后可以静默本地写。
- 干净 holder 没有数据可给，HN 从 SN 读；SC/SC 不变量保证 SN 的数据是最新的。
- UD holder 的 `SC_PD` 表示“降为 SC 并把脏数据责任交给 HN”；HN 必须先写 SN，再把同一份 D 返回给请求方，不能返回旧 SN 数据。
- 脏 victim 回 `I_PD`：对方没有驻留副本，数据责任转给 HN；请求方拿到 UC，因为没有别的副本。
- 干净 victim 或 Evict 已发出但尚未处理时，目录可能仍保留旧 holder；snoop 得到 `I`，按“没有其他 holder”处理，请求方拿 UC。

#### ReadUnique

| 请求方对该行 | 对方/目录状态 | HN 动作 | SN | 返回 | 请求方结果 |
|---|---|---|---|---|---|
| I | 无 holder | 直接读 SN | 读 | UC | 安装 W 为 UD |
| I | 干净 SC/UC 驻留 | `SnpUnique` → `I` | 读 | UC | 安装 W 为 UD |
| I | UD 驻留 | `SnpUnique` → `I_PD` | 无 | UD_PD | 安装 W 为 UD |
| I | 脏 victim | `SnpUnique` → `I_PD` | 无 | UD_PD | 安装 W 为 UD |
| I | 干净 victim/已 Evict | `SnpUnique` → `I` | 读 | UC | 安装 W 为 UD |
| SC | 无其他 holder | 不发 snoop | 读 | UC | 升级并安装 W 为 UD |
| SC | 另一 SC | `SnpUnique` → `I` | 读 | UC | 升级并安装 W 为 UD |

关键点：

- 没有其他 holder、或对方是干净行时，HN 仍从 SN 读一整行；RN 是写路径，会忽略这份数据，只用返回的 UC 权限安装本地 W。这是当前实现的固定路径，不是数据需要。
- 对方是 UD 时，`SnpUnique` 得到 `I_PD`，HN 直接把 D 转给请求方，不写 SN；脏数据责任随 `UD_PD` 转移给新 owner。
- 本地写命中 SC 时，HN 已经有请求方的 SC holder；如果没有其他 holder，不 snoop，但仍从 SN 读一次，把 SC 升级为 UC。
- 如果请求方是 SC 且另一个 RN 也是 SC，`SnpUnique` 使对方变 I，请求方升级；之后请求方本地安装 W 为 UD。
- 请求方是 UC/UD 时不会发 `ReadUnique`，因为本地写已经命中；HN 也会断言请求方不能已经是 unique holder。

#### snoop 响应

| 被 snoop 方原状态 | `SnpShared` 响应 / 新状态 | `SnpUnique` 响应 / 新状态 |
|---|---|---|
| I | `SnpResp(I)` / I | `SnpResp(I)` / I |
| SC | `SnpResp(SC)` / SC | `SnpResp(I)` / I |
| UC | `SnpResp(SC)` / SC | `SnpResp(I)` / I |
| UD | `SnpRespData(SC_PD,D)` / SC | `SnpRespData(I_PD,D)` / I |
| 脏 victim | `SnpRespData(I_PD,D)` / I | `SnpRespData(I_PD,D)` / I |

victim 命中条件：

```text
victimDirty && requestOpcode == WriteBackFull && victimAddress == snp.addr
```

命中时无论 `SnpShared` 还是 `SnpUnique` 都回 `SnpRespData(I_PD, victimData)`，并清 `victimDirty`。如果原 WriteBackFull 已经收到 `CompDBIDResp` 并正在 `sendWriteData`，HN 不应再 snoop 它；RN 用断言检查这种情况。

HN 收到 snoop 响应后的数据来源：

| snoop 响应 | 被 snoop 方新状态 | HN 数据来源 | 给请求方的 resp | 是否写 SN |
|---|---|---|---|---|
| `SnpResp(I)` | I | 从 SN 读 | UC | 否 |
| `SnpResp(SC)` | SC | 从 SN 读 | SC | 否 |
| `SnpRespData(SC_PD)` | SC | snoop 数据 D | SC | 是，先写 SN |
| `SnpRespData(I_PD)` + `ReadUnique` | I | snoop 数据 D | `UD_PD` | 否，直接转给请求方 |
| `SnpRespData(I_PD)` + `ReadShared`（victim 命中） | I | snoop 数据 D | UC | 是，先写 SN |

`SnpResp(SC)` 和 `SnpRespData(SC_PD)` 只出现在 `ReadShared` 路径；`SnpUnique` 只接受 `SnpResp(I)` 或 `SnpRespData(I_PD)`。

`finishSnoop` 的映射（`invalid = resp == I || I_PD`）：

- 被 snoop 方：`invalid` → I，否则 SC。
- 请求方：`invalid` → UC，否则 SC。
- `completionResp`：`ReadUnique && I_PD` → `UD_PD`；否则 `invalid` → UC，否则 SC。

这解释了为什么脏共享返回 SC，而脏独占返回 UD_PD，以及 victim 被取走后请求方拿到 UC。

## 5. 完整时序实例：写 miss + 脏替换 + 跨核 snoop

场景：RN0 写 miss，同索引有脏 victim；目标行由 RN1 以 UD 持有。

| 步骤 | 消息 | 通道 | 路径（经过的 stop） | 跳数 |
|---:|---|---|---|---:|
| 1 | `WriteBackFull` | REQ | RN0 → HN | 1 |
| 2 | `CompDBIDResp` | RSP | HN → RN1 → SN → RN0 | 3 |
| 3 | `CopyBackWrData`（脏数据） | DAT | RN0 → HN | 1 |
| 4 | `ReadUnique`（原命令，`allowRetry=1`） | REQ | RN0 → HN | 1 |
| 5 | `RetryAck` | RSP | HN → RN1 → SN → RN0 | 3 |
| 6 | `WriteNoSnpFull`（HN 写回 victim） | REQ | HN → RN1 → SN | 2 |
| 7 | `DBIDResp` | RSP | SN → RN0 → HN | 2 |
| 8 | `NonCopyBackWrData` | DAT | HN → RN1 → SN | 2 |
| 9 | `Comp`（victim 写回完成） | RSP | SN → RN0 → HN | 2 |
| 10 | `PCrdGrant` | RSP | HN → RN1 → SN → RN0 | 3 |
| 11 | `ReadUnique`（`allowRetry=0`） | REQ | RN0 → HN | 1 |
| 12 | `SnpUnique` | SNP | HN → RN1 | 1 |
| 13 | `SnpRespData(I_PD)` + 脏数据 | DAT | RN1 → SN → RN0 → HN | 3 |
| 14 | `CompData(UD_PD)` + 数据 | DAT | HN → RN1 → SN → RN0 | 3 |
| 15 | `CompAck` | RSP | RN0 → HN | 1 |

这里展示的是原命令恰好落在 HN 忙碌期间的竞争路径。如果 `ReadUnique` 在 HN 完成写回并回到 `idle` 之后才到达，就不会出现步骤 5 和 10，HN 直接接纳原命令。

关键因果：

1. RN0 先处理 victim，发 `WriteBackFull`；此时原命令还没发出。
2. HN 回 `CompDBIDResp`，RN0 发 `CopyBackWrData`。**DAT 注入后 RN0 立即恢复原命令**。
3. 原命令 `ReadUnique` 到达时 HN 仍在处理写回，HN 把它放进等待表并回 `RetryAck`。
4. HN 完成写回（步骤 6–9）后空闲，把等待项搬进上下文，发 `PCrdGrant`。
5. RN0 重发 `ReadUnique`，HN 查目录发现 RN1 持有，发 `SnpUnique`；RN1 回脏数据 `I_PD`。
6. HN 不写 SN，直接把数据作为 `CompData(UD_PD)` 转交 RN0；RN0 安装本地写数据为 UD，回 `CompAck`。

15 条消息中 REQ 4、RSP 6、SNP 1、DAT 4，分别走四条独立环。步骤 5 的 `RetryAck` 与步骤 6 的 `WriteNoSnpFull` 可以同时在途。

## 6. HN：准入、目录和重试

### 6.1 一个活动上下文

HN 只有一个活动事务：

- 同一时刻只有一笔事务能改目录、发 snoop、访问 SN。
- 新请求到达时，如果 HN 空闲且没有等待者，直接接受；如果忙，则把请求记入 retry 等待表并回 `RetryAck`，不执行它。
- 空闲且有等待者时，先把选中的请求搬进活动上下文并释放等待槽，再发 `PCrdGrant`；这样新的请求不能抢占已经预留的资源。
- 等待表满或 `RetryAck` 未发完时，反压 REQ，不消费队头。

活动上下文保存：上游完整 REQ、下游 txnID、HN/SN DBID、收到的整行数据、snoop 目标/槽位、`completionResp`、`snoopWrite`，以及 HN 作为请求方等待 SN 的 retry 标记。见 [HnNode.scala](../src/main/scala/chi/HnNode.scala#L35)。

### 6.2 准入和 retry

HN 的 `reqRx.ready` 不是简单地等于“空闲”：

```text
(idle && !hasRetryWaiting)
|| (activeState && retrySlotFree && !retryAckPending)
|| (waitRetryReq && !retryAckPending && (reservedRetry || retrySlotFree))
```

含义：

- 空闲且没有等待者：接受首次请求。
- 忙但还有等待槽：接受请求，记入等待表，回 `RetryAck`，不执行。
- 正在等已授权重发（`waitRetryReq`）：如果来的是被预留的请求方，直接接受；如果是另一个 RN 且还有空槽，也接受并拒绝它，避免队头阻塞；如果 `retryAckPending`，先发完待发的 `RetryAck`。
- 等待表满或 `RetryAck` 未发完：反压 REQ，不消费队头；空闲但还有等待者时也不接收新请求，而是先去授权。

被拒绝的请求只保存完整 REQ 快照；HN 不会为它分配 DBID、发 snoop、改目录或访问 SN。等待表里的快照不是待执行队列，只有收到带信用的重发才执行。

### 6.3 目录

目录结构和记录规则见 1.5。需要强调的是目录是**保守记录**：`Evict` 或 `WriteBackFull` 已发出但尚未处理时，HN 可能仍保留旧 holder；如果此时发生 snoop，可能得到 `I`（干净 victim）或 `I_PD`（脏 victim）。在事务静止且请求处理完的边界检查目录与 RN 持有关系，不能逐周期要求精确。

目录更新时点：

| 事件 | 更新 |
|---|---|
| 一致读获准（无 snoop） | 记录请求方 holder；`ReadShared` 且行已被占 → SC，否则 UC |
| 一致读需要 snoop | 暂不记录请求方；收到 snoop 响应后由 `finishSnoop` 同时更新被 snoop 方和请求方 |
| `Evict` | 清除发送方的目录项 |
| `WriteBackFull` 收到 `UD_PD` 数据 | 校验发送方是唯一 holder 且无其他 holder，然后清除发送方目录项，准备写 SN |
| `WriteBackFull` 收到 `I` 数据 | 取消写回，不改目录，丢弃数据 |
| snoop 响应 | `finishSnoop`：被 snoop 方 invalid→I 否则 SC；请求方 invalid→UC 否则 SC |

注意：`WriteBackFull` 的 `UD_PD` 数据路径校验“唯一 holder”；`I` 取消路径不校验 holder，产品中它只由 snoop 取走 victim 触发，目录已由该 snoop 更新。

### 6.4 下游 opcode 转换

HN 把上游一致请求转换成 SN 能处理的 `ReadNoSnp`/`WriteNoSnpFull`：

| 上游 REQ | HN 动作 | 下游 REQ |
|---|---|---|
| `ReadShared` | 查目录/snoop；干净则从 SN 读，脏则先写 SN | 需要读时 `ReadNoSnp`，脏共享写 SN 时 `WriteNoSnpFull` |
| `ReadUnique` | 查目录/snoop；脏数据直接转给 RN | 需要读时 `ReadNoSnp`，脏数据直接转交时无下游 REQ |
| `Evict` | 清目录，回 `Comp` | 无 |
| `WriteBackFull` | 先授权 RN 数据，收齐后写 SN | `WriteNoSnpFull` |
| 脏共享清理（`SnpRespData(SC_PD)`，或 `ReadShared` 的 victim `I_PD`） | 先把 snoop 数据写入 SN | `WriteNoSnpFull` |

`snoopWrite` 标志区分最后一种情况：HN 收到的是 `ReadShared` 的脏 snoop 数据（`SC_PD`，或 victim 的 `I_PD`），说明需要先更新 SN，HN 才能把数据发给新请求方。`SnpUnique` 的 `I_PD` 数据不走这条路径，直接转给请求方。

### 6.5 请求重试

#### RN → HN

| # | 消息 | 方向 | 通道 | 关键字段 | 状态/动作 |
|---|---|---|---|---|---|
| 1 | REQ 首次尝试 | RN0→HN | REQ | `txnID=t`, `allowRetry=1`, `pCrdType=0` | HN 忙：保存 REQ 到等待槽，回 `RetryAck` |
| 2 | RSP `RetryAck` | HN→RN0 | RSP | `txnID=t`, `dbID=0` | RN 置 `retryAckSeen`；不执行事务 |
| 3 | 活动事务完成 | — | — | — | HN 空闲且有等待者：把请求搬进活动上下文，释放等待槽 |
| 4 | RSP `PCrdGrant` | HN→RN0 | RSP | `txnID=0`, `pCrdType=0` | RN 置 `creditHeld`；资源已预留 |
| 5 | REQ 重发 | RN0→HN | REQ | 原 `txnID=t`, 原 opcode/addr, `allowRetry=0` | RN 在 `reqTx.fire` 消费信用并清标记；HN 接受并校验身份后执行 |

`RetryAck` 和 `PCrdGrant` 可以任意顺序到达；RN 分别记录两个标记，只有二者都置位才重发，不做定时轮询。只有 `RetryAck` 不重发；只有 `PCrdGrant` 也不重发。

#### HN → SN

HN 作为请求方时走同一套协议，但下游 ID 是 `h=0`：

- HN 发 `ReadNoSnp`/`WriteNoSnpFull` 首次尝试，`allowRetry=1`。
- SN 忙则接受请求、保存等待记录、回 `RetryAck`；HN 置 `retryAckSeen`，不释放上游 RN 上下文。
- SN 空闲后 `sendGrant` 发 `PCrdGrant`；HN 置 `creditHeld`。
- 两者齐备后 HN 回到 `sendSnReq`，用原下游身份、`allowRetry=0` 重发；SN 在 `waitRetryReq` 接受。
- SN 的 `RetryAck` 不会转发给 RN；HN 保留原 RN 上下文，等下游完成后再继续。

关键点：

- `RetryAck` 匹配被拒绝请求的 `txnID`；`PCrdGrant` 按“提供方 + `pCrdType`”匹配，`txnID` 固定为 0。
- 被拒绝的尝试没有副作用：不分配 DBID、不发 snoop、不改目录、不读/写内存；等待表里的快照不会自动执行。
- HN 在发 `PCrdGrant` 之前就把请求搬进活动上下文并释放等待槽，因此新请求不能抢占预留；`retrySlots=1` 时也能让另一个 RN 的请求占用释放的槽，避免它卡在 REQ 队头挡住已授权重发。
- HN 在 `waitRetryReq` 中如果还有待发 `RetryAck`，会先把它发完再接收 credited retry，避免 RSP 出口上授权响应插到 `RetryAck` 前面。
- HN 的“上游 retry 标记”和“下游 SN retry 标记”是两组独立寄存器；上游重发成功会把下游 `retried` 清掉，因为上游信用不算作下游 SN 请求的重试。
- P-Credit 和 Ring credit 独立：拿到 P-Credit 后仍要等 REQ 环的 `inject.ready`；重发被反压时保持原 flit 和信用不变，直到真正注入。

### 6.6 HN 状态机

| 状态 | 用途 | 离开事件 | 下一状态 |
|---|---|---|---|
| `idle` | 无活动事务 | 接受 REQ | 读 → `sendSnReq`/`sendSnp`；写/Evict → `sendRnRsp` |
| `sendSnReq` | 向 SN 发下游 REQ | `reqTx.fire` | 读 → `waitSnData`；写 → `waitSnDbId` |
| `waitSnData` | 等 SN 读数据 | 匹配 `datRx.fire` | `sendRnData` |
| `sendRnData` | 向 RN 发 `CompData` | `datTx.fire` | 一致读 → `waitRnCompAck` |
| `sendRnRsp` | 向 RN 发 `CompDBIDResp` 或 Evict `Comp` | `rspTx.fire` | 写 → `waitRnData`；Evict → `idle` |
| `waitRnData` | 等 RN 写 DAT | 匹配 `datRx.fire` | 有效写回/写 → `sendSnReq`；取消写回 → `idle` |
| `waitSnDbId` | 等 SN `DBIDResp` | 匹配 `rspRx.fire` | `sendSnData` |
| `sendSnData` | 向 SN 发写 DAT | `datTx.fire` | `waitSnComp` |
| `waitSnComp` | 等 SN 写完成 `Comp` | 匹配 `rspRx.fire` | `snoopWrite` → `sendRnData`；否则 `idle` |
| `sendSnp` | 发 snoop | `snpTx.fire` | `waitSnpResponse` |
| `waitSnpResponse` | 等 snoop 响应 | 匹配 RSP 或 DAT | `sendSnReq` 或 `sendRnData` |
| `sendGrant` | 发 `PCrdGrant` | `rspTx.fire` | `waitRetryReq` |
| `waitRetryReq` | 等已授权重发 | 匹配 REQ | 按 opcode 分派 |
| `waitRnCompAck` | 等 RN `CompAck` | 匹配 `rspRx.fire` | `idle` |

`waitingForSnReply = waitSnData || waitSnDbId` 是 HN 接收 SN 的 RetryAck/PCrdGrant 的条件；`waitSnComp` 只接受写完成 `Comp`，不会把 SN 的 RetryAck 当作完成。

### 6.7 队头阻塞和进展

- HN 的 `reqRx.ready` 不能简单绑定到 `idle`。如果只在空闲时接收 REQ，REQ 环队头可能是一个会被拒绝的请求，挡住后面的已授权重发。当前实现忙时也消费可重试请求并回 `RetryAck`。
- HN 的本地注入不经过 HN 自己的 FIFO，`RingStop` 只把环输入入队。因此 HN 不接收 REQ 不会直接挡住 HN 自己发往 SN 的 `reqTx`；被挡住的是环上后续的 flit，包括已授权重发。若队头不是本地目的且下游没有 credit，本地注入也会被转发优先级和 `>=2` credit 规则挡住，但那是 credit 饥饿，不是 `reqRx.ready` 造成的。
- HN 在授权时把请求移入活动上下文并释放等待槽；`waitRetryReq` 还能接收另一个 RN 的首次请求并拒绝它，避免它挡在已授权重发前面。
- RN 的 snoop 响应路径独立于主事务；本地结果反压也不会阻止已经注入的 `CompAck` 或 snoop 响应继续前进。
- 四条环独立，REQ 被挡住时 RSP/DAT/SNP 仍可前进；这是系统能完成的重要原因。
- 转发优先于注入不提供无限竞争下的注入公平性；如果下游永远不接收，或者外部消费者永远不取本地结果，协议不能保证完成。

## 7. SN：一次一个内存操作

SN 保存整行内存，默认 4 行；地址按 `addr >> 6` 索引。它只接受 `ReadNoSnp` 和 `WriteNoSnpFull`，一次执行一个内存操作。

### 7.1 读

```text
REQ ReadNoSnp(h, A) → readLine（从 memory 取行）→ sendData（CompData(I, data)）→ idle
```

`readLine` 在本地完成一次读，`sendData` 在 DAT 注入成功后释放资源。反压期间保持已经读出的数据，不重复读内存。

### 7.2 写

```text
REQ WriteNoSnpFull(h, A)
  → sendDbId（DBIDResp，授权数据槽）
  → waitWriteData（收 NonCopyBackWrData，txnID=dbID）
  → writeLine（真正写内存）
  → sendComp（Comp，txnID=原 h，dbID=dS）
```

关键顺序：`DBIDResp` 只表示“可以发数据”，不表示内存已更新；`writeLine` 在收到匹配 DAT 后执行一次；`Comp` 在内存更新之后才发。HN 收到 `Comp` 才释放上游上下文。

### 7.3 重试

SN 有一个等待记录：

- 忙时接受一个 `ReadNoSnp`/`WriteNoSnpFull`，保存完整 REQ，回 `RetryAck`，不读/写内存。
- 当前操作完成（`sendData` 或 `sendComp` 之后）如果 `retryWaiting`，进入 `sendGrant` 发 `PCrdGrant`，再进入 `waitRetryReq`。
- 收到匹配的 `allowRetry=0` 重发后，清除等待记录，按 opcode 进入 `readLine` 或 `sendDbId`。
- 重发必须匹配保存的 `opcode/addr/txnID/srcID/tgtID`，否则断言。

产品系统里 HN 是串行的，不会自然制造多个 HN 对 SN 的竞争；SN retry 主要由端点测试驱动，见 [RetryAdmissionSpec.scala](../src/test/scala/chi/RetryAdmissionSpec.scala#L140)。

### 7.4 SN 状态机

| 状态 | 用途 | 离开事件 | 下一状态 |
|---|---|---|---|
| `idle` | 无操作 | 接受 REQ 或发现等待者 | 读 → `readLine`；写 → `sendDbId`；等待者 → `sendGrant` |
| `readLine` | 读内存 | 本地读完成 | `sendData` |
| `sendData` | 发 `CompData` | `datTx.fire` | 等待者 → `sendGrant`；否则 `idle` |
| `sendDbId` | 发 `DBIDResp` | `rspTx.fire` | `waitWriteData` |
| `waitWriteData` | 等写 DAT | 匹配 `datRx.fire` | `writeLine` |
| `writeLine` | 写内存 | 本地写完成 | `sendComp` |
| `sendComp` | 发写完成 `Comp` | `rspTx.fire` | 等待者 → `sendGrant`；否则 `idle` |
| `sendGrant` | 发 `PCrdGrant` | `rspTx.fire` | `waitRetryReq` |
| `waitRetryReq` | 等已授权重发 | 匹配 REQ | 读 → `readLine`；写 → `sendDbId` |

## 8. 完成、顺序和不变量

### 8.1 完成边界

| 操作 | RN 本地结果 | HN 释放活动上下文 | SN 完成 | 关键保证 |
|---|---|---|---|---|
| 本地 cache 命中 | 读：本地读出；写 UC/UD：本地更新 | 不涉及 HN | 不涉及 | 没有任何 Ring flit |
| 一致读/写 | 安装数据、注入 `CompAck` 后 | 收到 RN `CompAck` 后 | 读：SN 注入 `CompData` 后；写：`writeLine` 后发 `Comp` | 目录和 cache 在 HN 释放时已一致 |
| Evict 替换 | 收到 `Comp` 后继续原命令 | 注入 `Comp` 后 | 不涉及 | 目录项已清除 |
| WriteBackFull 替换 | 注入 victim DAT 后继续原命令 | 收到 SN `Comp`，或消耗取消 DAT 后 | 非取消：`writeLine` 后 `Comp` | 取消写回仍有 DAT flit，但不写 SN |
| snoop 响应 | 无本地结果；RSP/DAT 注入后 | 不释放；继续后续路径 | 不涉及 | snoop 路径独立于本地事务 |

“RN 完成”“HN 释放”“SN 内存已更新”是三个不同事件，不能混为一谈。脏写回是最典型的例子：RN 在注入 victim DAT 后就可以继续原命令，但 HN 还要等 SN 的 `Comp` 才释放。

### 8.2 提前完成和顺序

- 一致读/写：RN 先安装数据（写路径安装 W），再发 `CompAck`；HN 收到 `CompAck` 才释放。因此 HN 的目录和 RN 的 cache 在释放时已经一致。
- 脏替换：RN 在 victim DAT 注入后就可以发原命令，HN 仍可能在写 SN；干净替换则要等 `Comp` 到达后才发原命令。原命令可能被 RetryAck，HN 的串行化保证它不会越过 victim 写回。
- 脏写回被 snoop 取消时，RN 仍会发出一条带 `I` 的 `CopyBackWrData`；HN 消耗后直接释放，不访问 SN。
- 结果反压：RN 在 `returnResult` 保持结果快照，直到 `response.fire`；期间不接受新的本地命令，但 snoop 仍可处理。
- HN 在向 RN 注入 `CompData` 后进入 `waitRnCompAck`，本地结果是否被消费不影响 HN 的释放条件。

### 8.3 一致性不变量

- 同一行最多一个 RN 持有 UC/UD；此时另一个 RN 必须是 I。
- 两个 RN 都是 SC 时，SN 保存最新数据（在事务完成边界）。
- 目录只记录 SC/UC；UD 被当作 UC。因此“目录 UC”不等于“cache 干净”。
- 脏数据责任可以位于 RN 的 UD 行、RN 的 victim 缓冲、或 HN 的活动事务中；不能要求 SN 始终最新。
- 目录是保守记录；`Evict` 或 `WriteBackFull` 未处理完时可能暂时保留旧 holder，脏共享写 SN 前目录也可能暂时显示两个 SC。检查应在事务静止且请求处理完的边界进行。
- 任何未被取消、实际更新内存的写请求只在 SN `writeLine` 后发 `Comp`；HN 收到 `Comp` 才释放上游。写授权（`CompDBIDResp`/`DBIDResp`）不是完成；取消的 WriteBackFull 不写 SN，HN 在消耗 `CopyBackWrData(I,0)` 后释放。
- 任何被接受的一致读在 RN 安装数据并发出 `CompAck` 后才让 HN 释放；`CompAck` 使用 `CompData.dbID`，不是原 `t`。
- Ring credit 满足 `credit + downstream occupancy = bufferDepth`；发送前转发至少需要 1 个 credit，注入至少需要 2 个。转发可以用尽下游空位，注入后至少剩 1 个；credit 不会下溢或超过深度。
- 正常事务中的每条消息只被接收一次；重复/错误身份/错误 opcode 会触发断言。断言用于诊断非法输入，不等同于硬件状态更新的门控。

### 8.4 关键断言

| 断言 | 位置 | 防止什么 |
|---|---|---|
| RN 地址 64 B 对齐 | `RnNode` 请求握手 | 非整行访问 |
| RN `CompData` 的 `tgtID/srcID/txnID/opcode` 匹配，`resp` 属于允许集合 | RN `datRx` | 收到别的事务或非法状态的数据 |
| 写路径的 `CompData` 必须是 UC/UD_PD | RN `datRx` | 写请求拿到共享权限 |
| `RetryAck` 只能匹配原 `txnID`，`PCrdGrant` 只能零 ID | RN `rspRx` | 重试响应错配 |
| 已重发后不能再收 RetryAck/PCrdGrant | RN `rspRx` | 重复信用 |
| 已授权的写回不能被 snoop | RN snoop | 数据竞争 |
| HN REQ 目标/对齐/`allowRetry`/类型 | HN `reqRx` | 非法请求 |
| HN 请求方不能已经是 unique holder | HN `reqRx` | 重复获取独占 |
| HN 目录地址范围、holder 数量、唯一 holder | HN 目录 | 目录越界或三 holder |
| 脏写回必须来自唯一 holder | HN `waitRnData` | 覆盖别人的数据 |
| SN 地址范围、对齐、opcode、写 DAT 匹配 | `SnNode` | 越界/错配 |
| Ring credit 不越界 | `RingStop` | credit 计数错误 |

### 8.5 复位

复位是全系统复位：清空所有端点的活动上下文、retry 标记、等待表、预留、待发响应、cache、目录、Ring FIFO，并恢复所有 link credit；SN 内存恢复 `initialLines`。复位不保留脏数据，也不支持某个端点单独复位后继续使用旧网络消息。

## 9. 微架构取舍：硬件式部分与刻意的串行化

这个实现首先是协议子集的功能模型，而不是性能导向的微架构。传输层是常规 RTL，端点层则有多处刻意串行化、偏“软件式建模”的结构。把它们分开看，有助于判断哪些是范围选择，哪些是以后要改的。

### 9.1 传输层是常规硬件写法

- `RingStop` 用 FIFO + 转发优先仲裁 + credit/bubble：转发需要 1 个下游空位，注入需要 2 个（`RingStop.scala:19-49`）。队头阻塞被保留，不做旁路。
- 四条通道各有一条独立单向环，用物理资源换通道级并发；REQ 阻塞不会拖住 RSP/SNP/DAT。
- 所有边界都是显式 `Decoupled` 握手，没有隐藏的阻塞调用。
- RN 有独立的 snoop 响应路径，本地事务等待或结果背压时仍能回 snoop（`RnNode.scala:284-355`）。
- 路由是静态的：`ChiNoC` 把 `tgtID` 译成物理 `dst`（`ChiNoC.scala:27-33`），所有缓冲有界，没有动态分配。

这些部分的设计方式与真实硬件一致。

### 9.2 端点层的软件式痕迹

1. **单线程事务模型**：每个端点只有一个活动上下文，其余请求被 `RetryAck` 进等待表，空闲后再授权。这等价于“单线程服务器 + 等待队列”，而不是多 outstanding 事务。HN 的 14 个状态中大量是 `wait*` 阻塞阶段（`HnNode.scala:35-37`）。
2. **store-and-forward**：HN 把 SN 返回的整行 512 bit 存进 `data` 寄存器，再重新发一条 `CompData` 给 RN（`HnNode.scala:381`、`436-456`），而不是直通转发。脏 snoop 数据同样先落入 HN。没有 DMT。
3. **写路径串行化**：对脏写回，HN 先回 `CompDBIDResp`，等 RN 的 `CopyBackWrData` 到达后才向 SN 发 `WriteNoSnpFull`（`HnNode.scala:318-382`、`261-287`）。这不仅是风格问题：数据到达前 victim 仍可能被 snoop 抢走，HN 必须能选择不写内存（见 4.7）。若要提前发下游请求，需要额外的写回中止机制。
4. **重试表是软件等待队列**：把整条 `ReqFlit` 当作“请求对象”保存，在槽位间搬运，授权时再整体搬进事务上下文（`HnNode.scala:63-88`、`246-254`、`468-477`）。重复检测和选槽在默认 2 槽时是小规模并行比较/优先级逻辑，结构本身没问题；软件式的地方是“保存对象、调度、再执行”的模型。槽位增多时，这种整对象保存会明显变得不划算。
5. **用寄存器数组模拟存储器**：SN 的 memory 是 `RegInit(VecInit(initialLines))`，读为组合逻辑一拍出数（`SnNode.scala:29-31`、`84-87`）；没有 SRAM 宏、读延迟或初始化流程。cache 的有效位/状态位和目录条目用 `RegInit` 数组复位初始化，cache 数据和标签则不复位。
6. **长组合链与隐式优先级**：`reqRx.ready` 同时依赖状态、等待槽、待发 `RetryAck` 和 `reservedRetry`（`HnNode.scala:181-184`）；重试选择和空闲槽分配用 Scala `for` 生成优先级逻辑，目录更新则对两个槽位做展开比较；`ChiNoC.route` 是线性 `MuxCase`。4 个节点、2 个槽位时综合没有问题，但代码里没有 pipeline register 或时序边界的意识。

### 9.3 为什么在当前范围内可以接受

项目的范围是功能正确性和事务完成语义，不是延迟、面积或频率。上述选择让不变量容易陈述和验证：单一活动上下文使目录和所有权状态没有并发交叉；store-and-forward 让数据归属和完成顺序显式化；阻塞式 FSM 与断言、参考模型直接对应；复位初始化数组让测试结果确定。

代价同样明确：多段往返被串行化，HN 和 SN 各自持有 512 bit 数据寄存器，事务之间没有重叠。

### 9.4 如果转向性能导向

按收益/改动量排序：

1. **重叠写回路径**：为脏写回引入显式的取消/中止机制后，让 HN 在等 RN 数据时提前向 SN 申请授权；否则保留当前“等数据到达”的顺序。
2. **拆分 HN 状态机**：按通道拆成 REQ/RSP/DAT/SNP 控制器加事务 scoreboard，允许阶段重叠并缩短组合路径。
3. **多事务上下文**：每个 RN 允许多个 outstanding，按 `txnID` 索引，替代“一笔 + 等待表”。
4. **数据直通**：读数据和 snoop 脏数据经 HN 转发，不再整行缓存；进一步可做 DMT。
5. **存储器宏化**：SN 用 SRAM 模型和读延迟，cache/目录只复位 valid 位。
6. **重试表瘦身**：只存身份字段；槽位增多时改用 CAM/并行比较。

这些是范围变化，不是缺陷修复。在当前“每端点一笔 + 功能正确性优先”的约束下，9.2 的写法是自洽的。

## 10. 没有实现的能力

按类别列出，避免把端点测试能力误当成系统能力：

- **REQ opcode**：`ReadOnce`、`ReadClean`、`ReadNotSharedDirty`、`CleanShared`、`CleanUnique`、`MakeUnique`、`WriteUnique`、`WriteCleanFull`、`WriteEvictFull` 等都没有实现。
- **SNP opcode**：`SnpOnce`、`SnpClean`、`SnpNotSharedDirty`、`SnpCleanShared`、`SnpCleanInvalid`、`SnpMakeInvalid`、`SnpUniqueStash` 等都没有实现。
- **传输粒度**：只有整行 64 B；没有部分写、sector、byte enable、32 B/16 B 传输。
- **状态编码**：不实现线上 `SD`；内部 `UD` 状态编码与线上 `SD` 不是同一个协议编码。
- **一致协议扩展**：没有原子操作、exclusive monitor、lock、DVM、stash、DCT。
- **数据路径**：没有 DMT、没有转发 snoop、没有 RN 间直接传数据；所有数据都经 HN。
- **错误和元数据**：没有错误响应、poison、DataID、QoS、MPAM、trace、PMU、低功耗状态。
- **并发规模**：每个 RN 只有一笔 outstanding；HN 只有一笔活动事务；SN 只有一笔活动内存操作；没有多个 HN、多个 SN、超过 2 个 RN。`ChiSystem` 固定 4 个端点；通用 `Ring` 支持其他 node 数，但不是 CHI 系统。
- **结构与策略**：目录不替换且必须覆盖 SN 内存；cache 是直接映射，没有组相联或替换策略；cache/目录大小必须是大于 1 的 2 的幂。
- **信用和完成**：没有 `PCrdReturn`、没有主动撤销、没有多个 P-Credit 类型；没有乱序完成、没有持久化。
- **端点组合**：RN 不发 `ReadNoSnp`/`WriteNoSnpFull`；HN 端点虽然接受，但要求目标行未被 cache 持有，且不会分配目录。一致 RN 也不能消费 `CompData(I)`。

## 11. 验证入口和源码地图

| 能力 | 测试入口 |
|---|---|
| 一致系统、cache 命中、共享/独占、替换 | [ChiCoherentSystemSpec.scala](../src/test/scala/chi/ChiCoherentSystemSpec.scala)、[ChiCoherentRandomSpec.scala](../src/test/scala/chi/ChiCoherentRandomSpec.scala) |
| 共享读和脏共享 | [ChiSharedReadSpec.scala](../src/test/scala/chi/ChiSharedReadSpec.scala) |
| 独占获取和脏数据转交 | [ChiUniqueReadSpec.scala](../src/test/scala/chi/ChiUniqueReadSpec.scala) |
| RN cache、替换、snoop 竞争 | [RnCacheSpec.scala](../src/test/scala/chi/RnCacheSpec.scala)、[RnEvictSpec.scala](../src/test/scala/chi/RnEvictSpec.scala)、[RnWriteBackSpec.scala](../src/test/scala/chi/RnWriteBackSpec.scala)、[RnSnoopSpec.scala](../src/test/scala/chi/RnSnoopSpec.scala#L399) |
| HN 目录、Evict、WriteBack、snoop | [HnDirectorySpec.scala](../src/test/scala/chi/HnDirectorySpec.scala)、[HnEvictSpec.scala](../src/test/scala/chi/HnEvictSpec.scala)、[HnWriteBackSpec.scala](../src/test/scala/chi/HnWriteBackSpec.scala)、[HnSharedSnoopSpec.scala](../src/test/scala/chi/HnSharedSnoopSpec.scala)、[HnUniqueSnoopSpec.scala](../src/test/scala/chi/HnUniqueSnoopSpec.scala) |
| RetryAck/PCrdGrant/重发 | [RnRetrySpec.scala](../src/test/scala/chi/RnRetrySpec.scala)、[HnRetrySpec.scala](../src/test/scala/chi/HnRetrySpec.scala)、[RetryAdmissionSpec.scala](../src/test/scala/chi/RetryAdmissionSpec.scala)、[RetryProtocolSpec.scala](../src/test/scala/chi/RetryProtocolSpec.scala) |
| 所有权和 dirty victim 监视 | [CacheOwnershipSpec.scala](../src/test/scala/chi/CacheOwnershipSpec.scala) |
| Ring 路由/仲裁/credit | [RingStopSpec.scala](../src/test/scala/noc/RingStopSpec.scala)、[RingCreditSpec.scala](../src/test/scala/noc/RingCreditSpec.scala)、[RingBubbleSpec.scala](../src/test/scala/noc/RingBubbleSpec.scala) |

源码入口：

| 主题 | 文件 | 关键成员 |
|---|---|---|
| RN 本地命令、cache、替换 | [RnNode.scala](../src/main/scala/chi/RnNode.scala#L85)、[RnCache.scala](../src/main/scala/chi/RnCache.scala) | `io.request`、`localCacheHit`、`resumeRequest` |
| RN 状态机、写数据、CompAck | [RnNode.scala](../src/main/scala/chi/RnNode.scala#L133) | `state`、`mainDatTx`、`mainRspTx` |
| RN snoop 和输出仲裁 | [RnNode.scala](../src/main/scala/chi/RnNode.scala#L284) | `snoopPending`、`victimHit`、`arbitrate` |
| HN 目录和 finishSnoop | [HnNode.scala](../src/main/scala/chi/HnNode.scala#L47) | `directoryHolder*`、`finishSnoop` |
| HN 准入和 retry | [HnNode.scala](../src/main/scala/chi/HnNode.scala#L181) | `reqRx.ready`、`retryWaiting`、`sendGrant` |
| HN 下游写和完成 | [HnNode.scala](../src/main/scala/chi/HnNode.scala#L261) | `reqTx`、`datRx`、`rspRx`、`datTx` |
| SN 读写和重试 | [SnNode.scala](../src/main/scala/chi/SnNode.scala#L48) | `reqRx`、`readLine`、`writeLine`、`sendGrant` |
| Ring Stop 和 credit | [RingStop.scala](../src/main/scala/noc/RingStop.scala#L18) | `buffer`、`downstreamCredits`、`forward` |
| CHI 路由 | [ChiNoC.scala](../src/main/scala/chi/ChiNoC.scala#L27) | `route`、`connect` |
| Flit 字段和 opcode | [ChiFlit.scala](../src/main/scala/chi/ChiFlit.scala)、[ChiOpcode.scala](../src/main/scala/chi/ChiOpcode.scala) | `ReqFlit`、`RspFlit`、`SnpFlit`、`DatFlit` |
| 系统参数和拓扑 | [ChiSystem.scala](../src/main/scala/chi/ChiSystem.scala)、[ChiSystemMain.scala](../src/main/scala/chi/ChiSystemMain.scala) | `ChiSystemParams`、`--coherent` |
| 本地接口 | [RnPort.scala](../src/main/scala/chi/RnPort.scala) | `RnRequest`、`RnResponse` |

物理图源为 [render_transactions.py](diagrams/render_transactions.py)，只依赖 Python 标准库。修改场景消息或拓扑后运行：

```bash
python3 docs/diagrams/render_transactions.py
```

图中路径统一按 `0→1→2→3→0` 计算，避免人工绘图出现逆向箭头；分步格表示消息顺序，不是时钟周期。
