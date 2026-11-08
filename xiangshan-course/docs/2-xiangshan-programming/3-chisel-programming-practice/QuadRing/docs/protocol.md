# QuadRing CHI 子集规格（P1）

状态：W1 按当前实现整理的本地详细规格。功能、重试及一致性验证已完成；本文与 PLAN.md 继续忽略，不提交。可提交的实现说明见 design.md，具体行为以 src/main/scala 为准。
编码及事务规则核对自 [Arm IHI 0050B](https://documentation-service.arm.com/static/5f914e1cf86e16515cdc2b3b)：
§2.3、§2.6/2.7、§2.11、§4.5/4.7/4.9、§5.2.3、§12.9.15/12.9.25/12.9.33/12.9.36。
本文是内部 CHI 子集模型：缩减字段和 ID 位宽，使用 Decoupled 和整行单 flit，不能直接连接完整 CHI 链路。

## 1. 范围与接口

- 默认 RN0/HN/RN1/SN 的 nodeID 为 0/1/2/3；环方向 0 → 1 → 2 → 3 → 0。
- 四条独立环依次承载 REQ、RSP、SNP、DAT。NoC 只解释外层 `dst`。
- `ChiNoC` 根据 Diplomacy 协商的端点 nodeID 映射到物理 RingStop 索引：REQ/RSP/DAT 使用 `tgtID`；SNP 没有 `tgtID`，发送方另外提供目的 nodeID。环上 `dst` 是映射后的索引，默认 nodeID 与索引相同时才等于 `tgtID`。
- 每个 RN 一次一笔请求；HN 一次处理一笔事务；SN 一次执行一个内存操作。
- cache 行为 64 B，地址按行对齐，一个 DAT flit 为完整 512-bit 数据。
- 支持 RN→HN 和 HN→SN 的请求重试，使用 RetryAck、PCrdGrant 和单一 P-Credit 类型 0，见 §7。
- 不做 partial、SD、MakeUnique/CleanUnique、DMT、转发 snoop、错误响应及链路层。
- 默认非一致模式下 RN 经 HN 发 ReadNoSnp/WriteNoSnpFull。开启一致性后 RN 的本地命令走 cache 路径；coherent HN 仍可处理非一致请求，但目标不得被 cache 持有。

当前传输接口 `ChiPort` 从端点侧定义 reqTx/reqRx、rspTx/rspRx、snpTx/snpRx、datTx/datRx。
REQ/RSP/DAT 的收发 bits 为对应 Flit；snpTx 为 `{dst: 目的 nodeID, payload: SnpFlit}`，snpRx 只交付 SnpFlit。
传输层不解析 opcode，也不按角色执行或限制事务；端点状态机负责协议行为。

## 2. 字段和编码

默认位宽：nodeID 2、txnID/dbID 4、addr 48、data 512。P2 参数化 ID 和地址位宽；
四通道 opcode 宽度固定为 6/4/5/3，resp 固定为 3。allowRetry 为 Bool，pCrdType 为 4 bit，当前只接受值 0。
data 固定 512，行低 6 个地址位必须为零。

| Bundle | 字段（按声明顺序） | 默认 payload 位宽 | 加外层 dst 后 |
|---|---|---:|---:|
| ReqFlit | tgtID:2, srcID:2, txnID:4, opcode:6, addr:48, allowRetry:1, pCrdType:4 | 67 | 69 |
| RspFlit | tgtID:2, srcID:2, txnID:4, opcode:4, resp:3, dbID:4, pCrdType:4 | 23 | 25 |
| SnpFlit | srcID:2, txnID:4, opcode:5, addr:48 | 59 | 61 |
| DatFlit | tgtID:2, srcID:2, txnID:4, opcode:3, resp:3, dbID:4, data:512 | 530 | 532 |

REQ addr 保留完整字节地址；SNP addr 在本模型也使用完整行对齐字节地址，区别于 CHI 线上的 Addr[47:3]。
ExpCompAck 由 opcode 固定推导：RN→HN 的 ReadShared/ReadUnique 为 1，其余为 0；
省略的 Size 恒为 64 B，RespErr 恒为 OK，RetToSrc 恒为 0。
HomeNID 省略：数据始终由 HN 转回 RN，RN 的 CompAck 发给该 CompData 的 srcID。
其他未实现的字段不参与模型，不能把本 Bundle 的打包位序视为标准 CHI flit 布局。

| 通道 | opcode = 编码 | 使用方向 |
|---|---|---|
| REQ | ReadShared=0x01, ReadUnique=0x07, Evict=0x0D, WriteBackFull=0x1B | RN → HN |
| REQ | ReadNoSnp=0x04, WriteNoSnpFull=0x1D | HN → SN；R0 允许 RN → HN |
| RSP | SnpResp=0x1, CompAck=0x2 | RN → HN |
| RSP | Comp=0x4 | HN → RN 的 Evict 完成；SN → HN 的写完成 |
| RSP | CompDBIDResp=0x5 | HN → RN，授予写数据槽并完成协议响应 |
| RSP | DBIDResp=0x6 | SN → HN，授予写数据槽 |
| RSP | RetryAck=0x3, PCrdGrant=0x7 | HN → RN、SN → HN |
| SNP | SnpShared=0x01, SnpUnique=0x07 | HN → RN |
| DAT | SnpRespData=0x1, CopyBackWrData=0x2 | RN → HN |
| DAT | NonCopyBackWrData=0x3 | HN → SN；R0 允许 RN → HN |
| DAT | CompData=0x4 | SN → HN、HN → RN |

**cache 状态与线上 Resp 分开定义。** 内部 I/SC/UC/UD 可以用独立的 2-bit 状态枚举；
线上没有 `UD=011` 这个约定，`011` 属于本项目不实现的 SD。

| Resp 名称 | 值 | 本项目使用场景 |
|---|---|---|
| I | 000 | CompData 非一致读、Comp/Evict、SnpResp、取消的 CopyBackWrData、NonCopyBackWrData |
| SC | 001 | CompData、SnpResp、干净 SnpRespData |
| UC | 010 | CompData |
| I_PD | 100 | SnpRespData：失效并转交脏数据责任 |
| SC_PD | 101 | SnpRespData：降级为 SC 并转交脏数据责任 |
| UD_PD | 110 | CompData、CopyBackWrData：转交脏数据责任 |

CompAck、CompDBIDResp、DBIDResp、RetryAck、PCrdGrant 以及写完成 Comp 的 resp 恒为 000。
RetryAck、PCrdGrant 的 dbID 填 0；其余 RSP 的 pCrdType 填 0。无意义的字段填零。

## 3. ID 与完成条件

原请求用 `(srcID, txnID)` 标识，两个 RN 可以使用相同 txnID。
HN 保存原请求映射，为发往 SN 的请求及 snoop 分配自己的 txnID；不能靠原 RN 的 txnID 区分 snoop。
HN/SN 的单个活动槽可固定 dbID=0，直到该槽全部预期消息收到后才能复用。

| 消息 | txnID 取值 | dbID 取值 |
|---|---|---|
| CompData、CompDBIDResp、DBIDResp、Comp | 对应请求的 txnID | 完成方活动槽 ID；Comp/Evict 填 0，写完成 Comp 与 DBIDResp 相同 |
| CompAck | 收到的 CompData.dbID | 0 |
| CopyBackWrData、NonCopyBackWrData | 收到的 CompDBIDResp/DBIDResp.dbID | 0；写数据通过 txnID 匹配槽位 |
| SnpResp、SnpRespData | 收到的 SnpFlit.txnID | DAT.dbID 填 0 |
| RetryAck | 被拒绝请求的 txnID | 0 |
| PCrdGrant | 0，不用于匹配请求 | 0 |

CompData 非一致读不需要 CompAck，dbID 填 0。
每条回复的 tgtID 指向对应请求方，srcID 是当前发送方；HN 转回 RN 时恢复原请求的 txnID。
PCrdGrant 按 `(srcID=信用提供方, pCrdType)` 匹配，不能使用其 txnID 关联请求。
CompAck 匹配 `(srcID, txnID=dbID)`，写数据匹配 `(srcID, txnID=dbID)`；不得把 DBID 当作原请求 ID。

- ReadShared/ReadUnique：RN 接收数据、安装 cache 行，再发 CompAck；HN 收到匹配的 CompAck 后结束。
- ReadNoSnp：RN 收到 CompData 后结束；HN 转发数据被网络接受后结束。
- Evict：RN 发 REQ 前先置 I，收到 Comp(I) 后结束；HN 更新目录并发出 Comp 后结束。不发 CompAck。
- WriteBackFull/WriteNoSnpFull：先收 CompDBIDResp，再发写数据；RN 写数据被网络接受后可结束本地请求。
  CompDBIDResp 保证后续数据可被接收，并不表示 SN 已经写入。HN 收齐写数据并收到 SN 的写完成 Comp 后才处理下一笔。
- HN→SN 写采用分开的 DBIDResp 和 Comp：SN 先授予数据槽，收到 NonCopyBackWrData 后更新内存，再发 Comp。
  HN 收到 Comp 才认为脏共享清理或下游写完成，避免把提前授予 DBID 误当成内存已更新。

## 4. 事务流

箭头表示对应消息完成 valid/ready 握手；同一个模板严格按从上往下的顺序执行。
以下是请求被接受后的正常路径；REQ 如被 RetryAck 拒绝，先执行 §7 的流程，重发被接受后再继续。
`t` 是 RN 请求 ID，`h` 是 HN 下游请求 ID，`s` 是 HN snoop ID；`dH/dS` 是 HN/SN 返回的 DBID。

### 4.1 从内存读：ReadShared、ReadUnique、R0 ReadNoSnp

```text
RN                       HN                       SN
 |-- REQ Read*(t) ------->|                         |
 |                       |-- REQ ReadNoSnp(h) ----->|
 |                       |<-- DAT CompData(I,h) ---|
 |<-- DAT CompData(r,t) --|                         |
 |-- RSP CompAck(dH) ---->|                         |
```

无其他持有者的 ReadShared/ReadUnique：`r=UC`，结尾发送 CompAck。
ReadShared 有其他 SC：先执行 4.2 的 SnpShared，再从内存读，`r=SC`。
ReadUnique 有其他 SC/UC：先执行 4.2 的 SnpUnique，再从内存读，`r=UC`。
R0 ReadNoSnp：`r=I`，省略末尾 CompAck，不分配 cache、不改目录。

### 4.2 snoop 前缀和数据选择

```text
请求 RN                  HN                  另一 RN
 |-- REQ Read*(t) ------->|                       |
 |                       |-- SNP Snp*(s) ------->|
 |                       |<-- RSP/DAT SnpResp*(s)-|
```

下表穷举被 snoop 方的四种状态；每个格子是一条确定的响应及其新状态。

| 被 snoop 方原状态 | SnpShared | SnpUnique |
|---|---|---|
| I | SnpResp(I)，保持 I | SnpResp(I)，保持 I |
| SC | SnpResp(SC)，保持 SC | SnpResp(I)，变 I |
| UC | SnpResp(SC)，变 SC | SnpResp(I)，变 I |
| UD | SnpRespData(SC_PD, data)，变 SC | SnpRespData(I_PD, data)，变 I |

SnpShared 返回无数据且 resp=SC：HN 从 SN 读，给请求方 CompData(SC)。
返回 I：按没有其他持有者处理，给请求方 CompData(UC)。
SnpUnique 返回无数据：HN 从 SN 读，给请求方 CompData(UC)。
SnpShared 返回脏数据：HN 执行 4.3 的下游写，把整行交给 SN。原 RN 保留 SC 副本（SC_PD）时给请求方 CompData(SC)；脏 victim 交出数据后不保留副本（I_PD）时给请求方 CompData(UC)。
SnpUnique 返回脏数据：HN 直接给请求方 CompData(UD_PD)，不写 SN。
两种返回数据路径均以请求 RN 的 CompAck(dH) 结束。HN 接收脏数据后负责保存它，直到写入 SN 或交给新 owner。

### 4.3 整行写：WriteBackFull、R0 WriteNoSnpFull

```text
RN                       HN                       SN
 |-- REQ Write*(t) ------>|                         |
 |<-- RSP CompDBIDResp ---|                         |
 |-- DAT WriteData(dH) -->|                         |
 |                       |-- REQ WriteNoSnpFull(h)->|
 |                       |<-- RSP DBIDResp ---------|
 |                       |-- DAT NonCopyBack(dS) ->|
 |                       |<-- RSP Comp(I,h) --------|
```

WriteBackFull 使用 CopyBackWrData(UD_PD)，清除发送方目录项，接收数据后按图写 SN。
R0 WriteNoSnpFull 使用 NonCopyBackWrData(I)，不改目录。
4.2 的脏共享清理只执行此图的 HN↔SN 部分，收到写完成 Comp 后再向请求 RN 发读数据。
取消的 WriteBackFull 仍发 CopyBackWrData(I, data=0)，HN 消耗该消息后丢弃，不访问 SN、不改另一 RN 的目录项。
这些写事务均不发送独立 CompAck；写数据承担对应的数据交付职责。

### 4.4 干净换出

```text
RN                       HN
 |-- REQ Evict(t) ------->|
 |<-- RSP Comp(I,t) ------|
```

RN 发送前已经是 I；HN 只清除该 RN 的目录项。无 SN 访问，无 CompAck。

## 5. cache 与目录

| RN 原状态 | CPU 读 | CPU 写 | 替换 |
|---|---|---|---|
| I | ReadShared → 安装返回状态 | ReadUnique → 安装后修改，变 UD | 无 |
| SC | 命中，保持 SC | ReadUnique → 安装后修改，变 UD | Evict → I |
| UC | 命中，保持 UC | 本地修改 → UD | Evict → I |
| UD | 命中，保持 UD | 本地修改，保持 UD | WriteBackFull → I |

CPU 操作与 snoop 命中同一行时，RN 优先处理 snoop，再按更新后的状态重试 CPU 操作。
一次替换先完成 Evict/WriteBackFull，再发新地址请求。等待 ReadUnique 时，原有 SC 副本仍须正常回应 snoop。
安装脏数据或执行待完成 CPU 写发生在 CompAck 发出之前，后续 snoop 可看到该次写入。

HN 目录每行记录两个 RN 的 `I/SC/U`；`U` 表示独占，可能实际为 UC 或 UD。
RN 的 UC→UD 是静默本地写，HN 不记录精确 dirty 位。HN 不缓存数据，只保留当前事务所需的整行暂存。目录在一致性 REQ 获准时或 snoop 响应到达时更新；下表描述对应事务结果，不将发出 CompData 当作唯一更新时刻。

| 本笔事务的返回结果 | 对应目录关系 / 后续动作 |
|---|---|
| 给 RN 发 CompData(UC/UD_PD) | 该 RN → U，另一 RN → I；等待 CompAck |
| 给 RN 发 CompData(SC) | 该 RN → SC；另一 RN 根据已收到的 snoop 响应记 SC 或 I；等待 CompAck |
| 收到 SnpResp / SnpRespData | 更新被 snoop 方为响应中的 I 或 SC；PD 数据必须保存 |
| 完成有效 WriteBackFull | 发送方 → I，整行下发 SN |
| 完成取消的 WriteBackFull(I) | 发送方保持 I；不覆盖已经转移的 owner 或较新内存数据 |
| Evict | 发送方 → I；回复 Comp(I) |
| R0 非一致读写 | 不改目录 |

目录是持有者的保守记录：干净 Evict 尚未处理时可暂时保留旧持有者，随后 snoop 可能返回 I。
在事务静止且请求已处理的边界检查目录与 RN 持有关系；不能逐周期要求精确匹配。
任何时刻，最多一个 RN 持有 UC/UD，且此时另一 RN 必须为 I；SC/SC 时 SN 已有最新数据。
脏数据责任可以暂时位于 RN、写回缓冲或 HN 活动事务，不能要求 SN 始终最新。

## 6. 写回竞争与接收约束

WriteBackFull 从首次 REQ 发出直到 CompDBIDResp 到达，写回缓冲必须保存地址、数据及当前状态，包含等待 P-Credit 的阶段。
若同地址 snoop 先到，必须读取写回缓冲并回应，不能因为 cache 已换出就直接回无数据的 I。
本项目对写回缓冲采取统一失效策略：SnpShared 或 SnpUnique 都返回 SnpRespData(I_PD, data)，
转交脏数据并把缓冲状态置 I；之后 CopyBackWrData(I, 0) 被 HN 丢弃。
再次 snoop 时缓冲已为 I，回 SnpResp(I)。没有竞争时仍发 CopyBackWrData(UD_PD)。
若原 WriteBackFull 正在等待重试，仍按原 opcode 和地址重发；获 CompDBIDResp 后再发 CopyBackWrData(I, 0)，不能直接撤销请求并丢掉信用。
HN 发出 CompDBIDResp 后，在收齐写数据前不发同地址 snoop；全局串行进一步延长该保护到收到下游写完成 Comp。

串行处理不能等同于拒收其他请求。HN 使用有界重试表，同一请求方最多一项等待记录，并保存完整被拒 REQ 供重发身份核对；这些快照不是待自动执行的事务队列。
忙时接收可重试请求并回 RetryAck，资源空闲后预留活动槽并发 PCrdGrant。
REQ 的 ready 取决于能否保存准入结果或待发 RetryAck，不能仅由 HN 活动槽是否空闲决定，
否则 REQ 环的队头阻塞可能挡住活动事务的 HN→SN REQ。
RN 在请求等待期间始终可处理 SNP，并保留一份独立 snoop 响应暂存；不能等待本地请求完成才回应。
RSP/DAT 接收与待处理 REQ 的调度分开。各个发送端在 valid 被反压时保持 bits 不变。
上述暂存用于保证串行模型的进展，不增加多笔活动事务。

## 7. 请求重试（RetryAck / PCrdGrant）

P-Credit 表示完成方已为一个请求预留协议资源，区别于 RingStop 的每跳缓冲 credit。
网络 credit 和 bubble 规则继续独立运行；即使拿到 P-Credit，REQ 仍要等待网络 inject.ready。
本项目所有 REQ opcode 均可走此流程；SNP、RSP、DAT 不允许用 RetryAck 拒绝。

```text
请求方（RN / HN）                  完成方（HN / SN）
 |-- REQ(t, allowRetry=1, type=0) --->|  没有活动槽
 |<-- RSP RetryAck(t, type=0) --------|  记录等待者，未执行事务
 |                                  |  槽空闲，先预留给等待者
 |<-- RSP PCrdGrant(txnID=0, type=0) -|
 |-- REQ(t, allowRetry=0, type=0) --->|  消耗预留，保证接受
 |<-- 正常完成响应 -------------------|  按 §4 执行
```

### 7.1 请求方规则

- 首次发送 `allowRetry=1, pCrdType=0`。保存完整原请求，直到本次协议请求结束；重试等待期间不能用另一请求替换该上下文，独立 snoop 响应仍可前进。
- RetryAck 匹配原请求的 txnID 和完成方 srcID，只表示本次未接受，不是完成响应，不能释放数据或改变目录。
- PCrdGrant 匹配完成方 srcID 和 pCrdType；它的 txnID 固定为 0，不能按请求 ID 匹配。
- 分别记录 `retryAckSeen` 和 `creditHeld`；支持 RetryAck→Grant 和 Grant→RetryAck 两种到达顺序。
  收齐匹配的两者才允许重发，不做定时轮询重发，也不能仅凭 RetryAck 重发。
- 重发沿用 tgtID/srcID/txnID/opcode/addr，只把 allowRetry 改成 0，pCrdType 使用收到的匹配类型 0。
  保持原 txnID 是本项目的简化约定，CHI 本身允许重发时换 txnID。
- 网络反压时保留信用和稳定的重发 flit；重发在 `inject.fire` 时消耗一个 P-Credit，并清除两项记录。
  重发不能再收到 RetryAck，也不能再消耗第二份信用；后续按正常完成路径等待。
- RN 等重试时仍处理 snoop；HN 等 SN 重试时仍接收 RN 请求并生成必要的 RetryAck，不停止 RSP/DAT 接收。

### 7.2 完成方规则

- HN 一项活动事务资源、两个 RN 等待记录；SN 一项活动内存资源、一个 HN 等待记录。
  等待记录保存完整被拒 REQ 和有效位，用于核对重发的 srcID/tgtID/txnID/opcode/addr；请求方仍须保留并主动重发原请求，完成方不能直接执行等待快照。身份从实际请求取得，不写死为物理位置。
- 初次请求到达，若没有活动事务、预留槽或先到的重试等待者，可直接接受；否则消费 REQ 并记录等待者、返回 RetryAck。
  被拒绝的尝试不得发 snoop、分配 DBID、更新目录或访问内存，也不得在以后自行执行它。
- 发送 RetryAck 前确保有等待记录和响应暂存。回复出口受反压时可短暂反压 REQ，但不能等活动事务结束才接收被拒请求。
- 空闲时优先服务重试等待者：HN 对两个 RN 轮转授权，SN 服务唯一 HN；持续重试者不能被新请求反复抢先。
- 发 PCrdGrant 之前就将活动资源预留给指定请求者；授权在 RSP 网络握手后生效，待发消息也必须保持稳定。
  HN 选择等待者时，将其请求身份移入空闲的活动上下文，并立即释放对应等待槽；Grant 及重发核对使用活动上下文，不再引用可被复用的等待槽。
  已授权但重发还在网络中的资源不能给新请求或另一位等待者，不重复发 Grant。
  HN 在等待该重发时仍可消费其他 RN 的首次请求，另记等待者并回 RetryAck；不能假定 REQ 队头一定是获授权者，也不能让被拒请求更新目录。
  即使 retrySlots=1，另一 RN 的新请求也能占用释放的等待槽，避免堵住同一 FIFO 后方的带信用重发；重发接受时不能再清除已复用的等待槽。
  待发 RetryAck 若被反压，先将其发完，再接受可能呈现写授权响应的重发，保证出口消息稳定。
- 收到匹配请求者的 `allowRetry=0, pCrdType=0` REQ，消费一次预留并转为活动事务，保证接受，不再次 RetryAck。
  没有匹配预留的 allowRetry=0、非零类型或重复授权是协议错误，当前端点用断言检查。
- 活动事务按 §3 的结束条件释放资源；收到重发、发出完成响应、预留信用是三个不同的时点。

### 7.3 边界与测试约定

每个请求方只挂一笔请求，无主动撤销、无多余或预发 P-Credit；因此暂不需要 PCrdReturn。
写回缓冲被 snoop 失效属于数据路径的取消，REQ 仍重发并用 CopyBackWrData(I) 结束，不能跳过协议。
复位是全系统复位，清空请求、等待记录、信用及预留；不支持某个端点独立复位后保留旧网络消息。

HN→SN 在串行正常运行下通常直接接受；SN 单测要模拟资源暂不可用以强制走重试。
测试资源恢复、端点处理及网络就绪最终有进展；如果这些外部条件永远不恢复，协议不能保证完成。

## 8. 验证范围

P2：四个 Bundle 宽度、opcode/Resp 编码及重试字段、结构化数据逐字段穿过四环；改变 ID/地址位宽；拒绝无效参数。
D1/D2：从 pinned Diplomacy 源码确认 API；验证参数协商、端口方向、nodeID 改动和实际传输。
端点：覆盖 4.1～4.4 及八格 snoop 表；写数据必须晚于 DBID 响应；SN 写完成 Comp 必须晚于内存更新；CompAck/写数据使用 DBID 而非原 txnID。
竞争：两个 RN 使用相同请求 ID；写回前分别插入两个 snoop；取消写回不覆盖较新数据；干净 Evict 先置 I。
重试：两种 Ack/Grant 顺序、只有一个响应时不重发、来源/类型/txnID 匹配、反压时保留信用、每份信用只消费一次。
准入：初次请求直接接受/拒绝、无资源也消费请求、Grant 先预留、不被新请求抢占、带信用重发必接受、两个 RN 轮转获得信用。
副作用：拒绝的尝试不执行；重发后只执行一次；读、Evict、两种写均覆盖，写回等待信用期间处理 snoop 不丢脏数据。
进展：HN 忙时其他 RN 的 REQ 不挡住 HN→SN；SN 重试时 HN 继续处理拒绝回复；延迟 SN 写数据及 Comp，HN 不提前结束写或发出共享读数据。
系统：逐笔对照参考内存，检查独占与脏数据责任；所有等待有超时，最后排空网络及所有暂存。

## 9. 读事务状态机（当前实现的子路径）

本节用无其他持有者的 ReadShared 说明已经实现的三端状态和 retry 分支。非一致读省略 cache 安装及 CompAck；写、替换及 snoop 的完整路径见 §10。状态表使用可读名称，具体 Scala 成员名以实现为准。

### 9.1 事务归属与消息路径

RN 自己管理 CPU 请求和 cache 安装；HN 自己管理上游请求、下游访问和目录；SN 自己管理内存操作。
每个端点只有一份活动事务上下文。REQ 由上下文构建，RSP/DAT 解码直接驱动该端点的状态转移。
retry 是这些状态机的功能分支，不在 RN 和 NoC 之间增加一套独立的事务生命周期。
HN 同时是 RN 的完成方、SN 的请求方，两种职责共享一个活动事务，但各自的信用记录不能混用。

```text
 RN                         HN                         SN
  |-- ReadShared(t) -------->|                          |
  |                         |-- ReadNoSnp(h) ---------->|
  |                         |<-- CompData(I,h) ---------|
  |<-- CompData(UC,t,dH) ----|                          |
  |-- CompAck(txnID=dH) ---->|                          |
```

RN 和 HN 的请求先后经过 REQ 环；CompData 经过 DAT 环；CompAck 经过 RSP 环。
RN 收数时安装 UC；HN 在请求获准或 snoop 响应时建立对应持有关系，直到收到 CompAck 都保持活动资源占用。

### 9.2 RN：一个 CPU 请求对应一个活动上下文

上下文保留地址、原请求 txnID/opcode、目标 HN、返回数据/状态及返回的 HN DBID。
CPU 结果使用已接收的数据快照，反压期间不因后续 cache/snoop 更新而改变。
该上下文包含 retryAckSeen、creditHeld、是否已重发的记录。

| 状态 | 转移事件 | 动作及下一状态 |
|---|---|---|
| Idle | CPU 读缺失请求握手 | 保存上下文；进入 SendReq |
| SendReq | REQ inject.fire | 首次发 ReadShared；进入 WaitData |
| WaitData | 匹配的 DAT CompData 握手 | 保存 data/resp/dbID，安装 cache 行；进入 SendCompAck |
| SendCompAck | RSP CompAck inject.fire | txnID 使用返回的 dbID，目标使用返回的 srcID；进入 ReturnResult |
| ReturnResult | CPU 结果握手 | 释放上下文；进入 Idle |

WaitData 的匹配包含 tgtID=自身、srcID=目标 HN、txnID=原请求 ID 和 opcode=CompData。
不以任何 RSP 到达作为读完成。RN 向网络交出 CompAck 后不再等 HN 回复；CPU 结果握手前不接受下一笔 CPU 请求。
R0 的 ReadNoSnp 不安装 cache、不发 CompAck：WaitData 收数后直接进入 ReturnResult。
SNP 接收/响应使用独立响应暂存，在上述等待和发送状态仍继续处理；不增加第二笔 CPU 活动事务。

### 9.3 HN：保留上游身份，再以自己的身份访问 SN

上下文保留 RN 的 srcID/txnID/opcode/地址、HN 下游 txnID、HN DBID 和待转发数据。
下游 REQ 使用 srcID=HN、tgtID=SN、txnID=h；回 RN 时恢复原 txnID=t。
HN 的单个数据槽可用 dH=0，但 CompAck 仍按 srcID 和 txnID=dH 匹配，不能用原请求 t 匹配。

| 状态 | 转移事件 | 动作及下一状态 |
|---|---|---|
| Idle | 可接受的 RN REQ 握手 | 保存上游上下文、分配下游 ID；进入 SendSnReq |
| SendSnReq | REQ ReadNoSnp inject.fire | 进入 WaitSnData |
| WaitSnData | 匹配的 SN DAT CompData 握手 | 保存整行数据；进入 SendRnData |
| SendRnData | DAT CompData(UC,t,dH) inject.fire | 更新 RN 持有关系；进入 WaitCompAck |
| WaitCompAck | 匹配的 RN RSP CompAck 握手 | 释放活动事务；进入 Idle 或为重试等待者预留资源 |

SN 数据匹配 srcID=SN、tgtID=HN、txnID=h；RN CompAck 匹配 srcID=原 RN、tgtID=HN、txnID=dH。
SendRnData 反压时保存数据及全部字段，不能提前释放活动槽。
R0 非一致读返回 resp=I，不改目录：SendRnData 握手后直接释放活动事务，不进入 WaitCompAck。

### 9.4 SN：读内存一次，再保存返回消息

上下文保留请求方 srcID/txnID、地址及读出的整行数据。

| 状态 | 转移事件 | 动作及下一状态 |
|---|---|---|
| Idle | 可接受的 ReadNoSnp REQ 握手 | 保存请求身份/地址；进入 ReadLine |
| ReadLine | 本地读操作完成 | 保存内存行；进入 SendData |
| SendData | DAT CompData(I) inject.fire | 释放活动资源；进入 Idle 或为重试等待者预留资源 |

返回 tgtID=请求方、srcID=SN、txnID=原请求 txnID、dbID=0。
SendData 反压期间只保持已经保存的返回消息，不重复读内存。SN 的非一致读不等待 CompAck。

### 9.5 请求方重试：RN / HN 的正常等待状态

读、写、Evict 的正常等待状态共用以下重试分支。
原请求由活动上下文重建，不另存第二份事务。首次 allowRetry=1/type=0。

| 事件 | 上下文更新 / 状态动作 |
|---|---|
| 匹配 RetryAck | 设置 retryAckSeen；保留全部事务资源 |
| 匹配 PCrdGrant | 设置 creditHeld；即使 Ack 未到也先记住 |
| 两个标记都已设置 | 回到原 SendReq / SendSnReq，设置本次为带信用重发 |
| 重发被反压 | 保持原 ID/opcode/地址、allowRetry=0/type=0 及信用 |
| 重发 inject.fire | 清除两个标记，记录已重发；返回原等待响应状态 |
| 正常 CompData | 继续原正常路径，沿用端点自身的完成条件 |

RetryAck 检查回复目标、完成方 srcID、原 txnID 和 type=0；PCrdGrant 检查目标、完成方 srcID 和 type=0，不按 txnID 匹配。
已重发请求不允许再次 RetryAck。消息身份不匹配不能推进状态；非法或重复信用由当前端点断言拒绝。
等待期间仍接收必要的 RSP/DAT；RN 的 SNP 响应、HN 的上游请求准入继续工作。

### 9.6 完成方重试：HN/SN 的准入记录与活动状态共同工作

HN 有可配置的有界等待表，同一 RN 最多一项；SN 有一项等待记录。拒绝时保存完整 REQ 用于后续身份核对，只有真正收到带信用重发后才执行。HN 授权时将选中快照移入活动上下文并释放等待槽；SN 单一上游模型继续保留等待记录到重发到达。
REQ 接收还需要一个待发 RSP 暂存。它满时可短暂反压，但不能把 REQ ready 直接绑定到主状态 Idle。
RSP 暂存消息一旦 valid，就保持到发送握手；调度新回复不能覆盖它。

| 条件 / 事件 | 行为 |
|---|---|
| 资源空闲，无预留、无等待者 | 初次 REQ 可直接建立活动上下文 |
| 活动事务、预留或先到等待者占用资源 | 消费可重试 REQ，记录等待者并回 RetryAck；不执行事务 |
| 活动资源释放，存在等待者 | 选择等待者；HN 轮转选择；先记录资源预留，再准备 PCrdGrant |
| PCrdGrant 被反压 | 保持预留和回复，不准其他请求抢占 |
| PCrdGrant inject.fire | 记录授权已发送，继续等待该请求方的重发 |
| 匹配的 allowRetry=0/type=0 REQ 握手 | 消费该预留，用重发 REQ 建立活动上下文，进入正常路径 |

在 HN 等待 SN 数据、信用或 RN CompAck 时，以上准入功能仍要消费其他 RN 的可重试请求并回 RetryAck。
SN 资源暂不可用时同样可拒绝请求，恢复后才预留和授权。
读操作和目录更新只能由已接受的主事务状态触发，不能由等待记录触发。
重试分支与正常状态机共享活动上下文；等待记录不触发内存操作或目录修改。

### 9.7 状态机验证重点

所有发送状态只在 inject.fire 前进；所有接收状态只在匹配消息的 eject.fire 前进。
正常路径分别核对三端完成点，改变 txnID，反压每个发送口和 CPU 结果口，确保字段稳定、不提前释放、不重复访问内存。
加入 retry 后，两种响应顺序、缺失/错误响应、资源预留保护、拒绝无副作用及最终只执行一次都在真实端点上下文中验证。
系统复位同时清空主状态、上下文有效位、retry 标记、等待记录、预留和待发回复。

V3a 的系统随机数据检查每批最多两笔（每 RN 一笔），允许同地址交错。独立参考模型按请求接受与结果首次 valid 的区间枚举合法顺序；CPU ready 只决定消费，不用于推断内存顺序。两笔交错写的最终值保留为合法集合，后续读必须匹配并缩小集合，不能直接用 DUT 返回值建立期望。批次间先消费全部本地结果，但不假定网络已空闲；结尾双方逐行读回、可替换行强制 miss，再空闲观察。V3b1 通过测试专用的 Select.registers / BoringUtils.tapAndRead 读取真实 cacheValid/cacheTags/cacheStates 和 victimDirty/victimAddress，重建所有权地址并逐周期断言：单 RN 无重复副本、跨 RN 同行只允许 SC/SC，未交出的脏 victim 视作独占。观察只在 OwnershipCheckedSystem 中挂入；普通 ChiSystem/RnNode 不增加调试端口或修改寄存器可见性。绑定按名称唯一匹配并校验维度/位宽，改名或编码变化会在 elaboration 明确失败，不能静默跳过。V3b2 的 ProtocolCheckedSystem 复用这些所有权观察，增加独立的 RetryProtocolMonitor：按实际接收 REQ/发送 RSP 握手记账，核对拒绝、授权、原身份重发和活动上下文开始/结束。HN 的 CompDBIDResp 只是首个授权结果，事务结束仍观察其活动区间退出，不能用授权替代 SN 写完成。SN 的 ReadLine/WriteLine 实际执行须每笔恰好一次。等待者在本测试有界反压条件下应于 1024 周期内进入执行。逐周期核对四环每跳 credit + 下游 FIFO 占用 = 缓冲深度；排空时首发/执行/结束以及 Ack/Grant/重发计数分别相等，端点无等待记录、预留或持有信用，环缓冲全空且信用全恢复。产品 HN 串行不会自然触发 SN 争用，另外驱动真实 SN 制造忙时拒绝写请求，并检查重发执行与复位。所有观察只存在于测试 wrapper，数据参考模型不依赖这些探针。

## 10. 端点的完整事务状态路径

当前实现覆盖 §4 的事务以及 retry、cache、目录和 snoop 路径。以下状态表描述当前行为；不支持的 opcode 不会被当作读请求处理。

### 10.1 接口和活动上下文

RN 本地接口使用 Decoupled：request 为 {write, addr, data:512, txnID}，response 为 {txnID, data:512}。
地址是行对齐字节地址，写操作为整行写；读请求的 data 忽略。R0 绕过 cache，选择 ReadNoSnp/WriteNoSnpFull；
R1 根据本地命令和 cache 状态选择 ReadShared/ReadUnique，Evict/WriteBackFull 由替换产生，不要求 CPU 填 CHI opcode。
写完成的本地 response.data 填 0，txnID 用于关联原本地命令；RN 本地写完成与 HN/SN 的完成条件仍按 §3 区分。
CPU 结果反压时保持结果快照，直到 response.fire 才接收下一条本地命令。

| 端点 | 一个活动上下文保留的内容 | 生命周期之外的必要记录 |
|---|---|---|
| RN | 本地命令；当前 REQ 的 opcode/地址/txnID；返回数据/Resp/DBID；替换时的 victim 地址和数据；请求重试标记 | cache；独立 snoop 响应暂存及被 snoop 的写回缓冲状态 |
| HN | 原 RN 的完整 REQ；下游 txnID；数据槽；snoop ID；收到的数据；下游操作结束后的继续动作；请求重试标记 | 目录；每 RN 的准入等待记录；资源预留；待发准入 RSP |
| SN | 原 REQ；内存地址；待读/写的整行数据；活动槽 DBID | 内存；HN 准入等待记录；资源预留；待发准入 RSP |

HN 下游 txnID 和 HN/SN DBID 在全局串行时可固定为 0；仍严格匹配 srcID/tgtID、对应 txnID 和 opcode。
不能用 RN 的 txnID 匹配 SN 回复，也不能用原请求 txnID 匹配通过 DBID 标识的写数据或 CompAck。
状态决定上下文有效性；所有发送消息从已保存的上下文构建，不能跟随输入 bits 改变。

### 10.2 RN 主状态机的事务路径

以下省略首尾 Idle。ReturnResult 是统一的本地结果交付状态，读返回整行数据，写返回完成通知。
同一个 RN 类按 coherent 配置选择非一致命令路径或 cache 路径。

| 操作 | 状态路径 | 完成边界 |
|---|---|---|
| 非一致读 | SendReq → WaitData → ReturnResult | 收到匹配 CompData(I)，本地结果握手 |
| 非一致整行写 | SendReq → WaitDBID → SendWriteData → ReturnResult | CompDBIDResp 授权后发送 NonCopyBackWrData(I)，本地结果握手 |
| 读命中 | ReturnResult | 保持数据快照，等待本地结果握手 |
| 读缺失 / 写需独占 | SendReq → WaitData → SendCompAck → ReturnResult | 安装返回行；CPU 写先修改为 UD，再发送 CompAck，最后本地结果握手 |
| 干净替换 | SendReq(Evict) → WaitComp → 新地址请求路径 | 发 Evict 前置 I；收到 Comp 后才发原本地命令的新请求 |
| 脏替换 | SendReq(WriteBackFull) → WaitDBID → SendWriteData → 新地址请求路径 | 数据交给网络后可继续；HN 仍等 SN 写完成 |

SendWriteData 按当前操作选择 CopyBackWrData 或 NonCopyBackWrData，txnID 使用收到的 DBID。
请求返回的数据、DBID、请求类型和替换继续动作都属于原活动上下文，不为 retry 建第二套上下文。

RN snoop 响应使用接收 → 查 cache/写回缓冲 → 更新状态并保存响应 → 发送 RSP 或 DAT 的独立路径。
主状态在 WaitData、WaitDBID、WaitComp、等待 retry 或发送被反压期间都必须允许这条路径前进。
CPU 与 snoop 同行时 snoop 优先；写回缓冲被 snoop 取走后保留取消标记，原 WriteBackFull 后续仍发送 CopyBackWrData(I,0)。

### 10.3 HN 主状态机的事务路径

Idle 接收已获准 REQ 时保存上游身份并按 opcode 分派；后续状态不重新读取上游输入。
下游读写共用 SendSnReq 及活动数据槽，继续动作区分“返回读数据”和“结束写/脏共享清理”。
HN 保留 SN 授予的 DBID，用于下游写 DAT 及 Comp 匹配。HN 等 SN Comp 后才释放事务，RN 交出写数据不代表下游已经完成。

| REQ / 子流程 | 状态路径 | 结束或继续动作 |
|---|---|---|
| ReadNoSnp | SendSnReq → WaitSnData → SendRnData | 返回 CompData(I)，DAT 被网络接受后释放槽 |
| WriteNoSnpFull / WriteBackFull | SendRnRsp → WaitRnData → SendSnReq → WaitSnDBID → SendSnData → WaitSnComp | 先向 RN 发 CompDBIDResp；SN Comp 到达后才释放槽 |
| 取消的 WriteBackFull | SendRnRsp → WaitRnData | CopyBackWrData(I) 消费后丢弃，不访问 SN |
| Evict | SendRnRsp | 更新目录，Comp 被网络接受后释放槽 |
| 无其他 holder 的一致读 | SendSnReq → WaitSnData → SendRnData → WaitCompAck | 返回 UC，记录 holder；收到匹配 CompAck 后释放槽 |
| 需 snoop 的一致读 | SendSnp → WaitSnpResponse → 按响应选择下游或返回路径 | 按 §4.2 更新目录和决定数据来源 |

WaitSnpResponse 按相同 snoop ID 接收 RSP 或 DAT；先收到其中一个便完成该 snoop 子流程，不等待另一个。
无数据时读 SN；SnpUnique 的 PD 数据直接 SendRnData(UD_PD)；SnpShared 的 PD 数据走下游写，
WaitSnComp 后继续 SendRnData(SC)，然后 WaitCompAck。不能把授权 DBIDResp 当作 SN 写完成。
HN 上游准入与正常事务等待分开；有空闲等待项及 RSP 暂存时消费其他 RN 的首次请求并拒绝，不等待主事务结束。

### 10.4 SN 主状态机的事务路径

| REQ | 状态路径 | 完成边界 |
|---|---|---|
| ReadNoSnp | ReadLine → SendData | 读出的快照以 CompData(I) 发送，DAT 握手后释放槽 |
| WriteNoSnpFull | SendDBID → WaitWriteData → WriteLine → SendComp | DBIDResp 先授权；匹配 DAT 后写一次内存，再发 Comp；RSP 握手后释放槽 |

同一份原 REQ 保存 requester、原 txnID 和 addr；写数据的 txnID 匹配槽 DBID，写完成 Comp 恢复原请求 txnID。
读/写内存只由 ReadLine/WriteLine 执行一次；响应反压不重复内存操作。SN 不等 CompAck。
当前 SN 内存使用初始化寄存器，系统复位恢复 initialLines，同时取消未完成操作；不模拟复位后保留内存数据。

### 10.5 Retry 与接收进展

请求方在当前正常等待状态中记录 RetryAck/PCrdGrant，收齐后回原发送状态重发；
发送成功再回原等待状态。读、写、Evict 的 REQ 共用这个规则，正常完成点不变。
完成方的等待者/预留和待发 RSP 独立于主状态；带信用重发被接受后按 opcode 进入相应正常路径。
RSP 发送需仲裁普通完成回复和准入回复，已选中且被反压的消息保持稳定。SNP/RSP/DAT 不能用 RetryAck 拒绝。

当前 T1 已在 RN 及 HN 下游实现请求方重试：复用原活动上下文，分别记录 retryAckSeen、creditHeld、retried。
等待正常回复时接收两种重试 RSP；RetryAck 匹配原请求 txnID，PCrdGrant 检查零 txnID/dbID、完成方身份和类型 0。
收齐后回原发送状态，以 allowRetry=0 重发；在 REQ 注入握手时消费信用，随后继续原读写完成路径。
读在重试待决期间不接收 CompData；已收到任一重试响应的写请求，必须先完成带信用重发，才接受 DBID 授权。
重复回复和带信用请求再次被拒绝会触发断言。
HN/SN 的完成方准入 T2 已实现：忙时消费请求、保存等待记录并回 RetryAck；活动资源释放后先预留再发 PCrdGrant，带信用重发进入正常状态路径。HN 在授权时把等待请求移入活动上下文并释放等待槽，保证单槽配置下新 REQ 不会在环 FIFO 队头挡住已授权重发。cache、目录及 snoop 已实现；当前验证进度以 PLAN.md 的 V3/V4 为准。
