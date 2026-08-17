# 香山昆明湖 V2：访存单元 TLB（ITLB/DTLB/L2TLB/PTW）源码分析

## 1. 范围、版本与证据分层

### 1.1 本次源码基线

| 项目 | 本次记录 |
|---|---|
| 被分析源码 | <code>/home/yanyusong/xs-memory-env/XiangShan</code> |
| 分支 | <code>kunminghu-v2</code> |
| 提交 | <code>e12436c7cba86b195deec24981976d78bc263661</code> |
| 提交时间 | <code>2026-08-14T09:36:34+08:00</code> |
| 提交说明 | <code>fix(Store): prevent rdataptr from advancing out of order (#6353)</code> |
| 工作树注意事项 | 原 checkout 已有 <code>difftest</code> 修改及 <code>src/main/resources/aia/</code> 未跟踪内容；本文没有修改源码。 |
| skill 同步检查 | 已运行当前 skill 的 weekly sync；因上次同步距今不足 7 天而跳过。 |

本次没有发现能够与该提交核对的 <code>XiangShan-Design-Doc</code> checkout。因此课程材料只用于解释教学位置，不能替代昆明湖 V2 的实现证据。

### 1.2 证据等级

| 标记 | 含义 | 使用规则 |
|---|---|---|
| **[代码已证实]** | 可由本次 V2 Scala 源码直接定位 | 可作为实现结论。 |
| **[课程意图]** | 课程目录已有的 Load/Store 分析结构 | 只解释阅读位置，不替代当前实现。 |
| **[推导]** | 由参数、连线和状态机组合得到 | 写明前提，不伪装成波形实测。 |
| **[待验证]** | 静态追踪不能唯一确定 | 给出可执行的波形/测试检查点。 |

### 1.3 理论、课程和有效代码的映射

| 主题 | 理论/课程层 | 当前有效代码层 | 结论 |
|---|---|---|---|
| 虚拟地址翻译 | 常见的 TLB hit/miss/page-walk 模型 | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:39>)、[TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:86>) | DTLB 是 nonblocking L1 包装，PTW 返回还可旁路正在等待的查找。 |
| LSU 流水 | 课程的 LoadQueue、LoadPipe、StorePipe 分工 | [LoadStore-LoadQueue.md](</home/yanyusong/XiangShanLab/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/memory/LoadStore-LoadQueue.md:1>)、[LoadStore-LoadPipe.md](</home/yanyusong/XiangShanLab/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/3-xiangshan-source-code-analysis/memory/LoadStore-LoadPipe.md:1>) | Load/Store 在 S0/S1 通过 TLB 接口耦合，LSQ 不替每条访存直接完成翻译。 |
| 权限和属性 | 页表权限、PMP/PMA、PBMT | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:429>)、[PMP.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/PMP.scala:563>) | 译后物理地址仍经 PMP/PMA；PBMT 随 TLB response 返回。 |
| 精确异常 | 异常与 ROB/replay 协作 | [MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:563>)、[LoadUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:899>) | TLB 给出执行期异常信息，最终精确提交仍属于后端/ROB。 |

### 1.4 Design Doc 追溯矩阵

| ID | Design Doc 文件/命题 | Design Doc 证据 | 当前源码映射 | 状态 |
|---|---|---|---|---|
| D0 | TLB 的设计意图、容量、时序或算法 | 本地 <code>XiangShan-Design-Doc</code> checkout 缺失，未查阅 | 不适用 | **未以 Design Doc 主张任何实现事实** |
| C1 | L1 TLB 的端口、存储和失效 | 不适用 | [Frontend.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:172>)、[MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:686>)、[TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:86>) | 代码已证实 |
| C2 | L1 miss 到 L2/PTW 的有效路径 | 不适用 | [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:338>)、[L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:125>) | 代码已证实 |

**Design Doc discrepancies：** 没有被读取的设计文档，因此没有可做逐条比对的“版本不一致”结论；D0 是本文必须保留的证据边界。旧课程 <code>14_LoadStore.md</code> 仅帮助解释 Decoupled/访存术语，其源代码版本不等同于本次 commit。

## 2. 实际实例化、配置和模块层次

### 2.1 从 KunminghuV2Config 到 TLB 参数

<code>KunminghuV2Config</code> 组合 <code>DefaultConfig</code> 等配置片段，<code>BaseConfig</code> 建立核心参数，见 [Configs.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:40>) 和 [Configs.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:460>)。通用 <code>TLBParameters</code> 的默认值包含 <code>NSets=1</code>、全相联 <code>Associative="fa"</code> 和可选 PLRU，见 [MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:31>)。

当前参数定义对 <code>ldtlb</code>、<code>sttlb</code>、<code>hytlb</code>、<code>pftlb</code> 均给出 <code>NWays=48</code>、<code>outReplace=false</code>、<code>partialStaticPMP=true</code>、<code>outsideRecvFlush=true</code>、<code>saveLevel=false</code>、<code>lgMaxSize=4</code>，见 [Parameters.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:274>)。

两个边界必须保留：

1. <code>NWays=48</code> 是本次配置参数的默认路数，不是仿真 elaboration 后实测到的运行时容量。
2. <code>partialStaticPMP</code> 在本次追踪中仅证实为参数/配置项；不能据此断言 L1 TLB 内已经缓存了 PMP 判定。

### 2.2 MemBlock 中三个数据侧 L1 TLB

<code>MemBlock</code> 明确实例化三个 <code>TLBNonBlock</code>，不是每条 Load/Store pipe 各有一个完整 TLB：

| L1 DTLB 组 | 构造式 | requestor 数 | paddr dup 数 | 接入者 |
|---|---|---:|---:|---|
| load 组 | <code>TLBNonBlock(LduCnt + HyuCnt + 1, 2, ldtlbParams)</code> | <code>LduCnt + HyuCnt + 1</code> | 2 | LoadUnit、HybridUnit、流式预取；特殊路径也会复用 port 0。 |
| store 组 | <code>TLBNonBlock(StaCnt, 1, sttlbParams)</code> | <code>StaCnt</code> | 1 | StoreUnit。 |
| prefetch 组 | <code>TLBNonBlock(2, 2, pftlbParams)</code> | 2 | 2 | SMS/L2BOP 等预取路径。 |

直接证据在 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:686>)。HybridUnit 接到 load 组的 <code>LduCnt + i</code> requestor，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1081>)；因此 <code>hytlbParams</code> 的存在不等于 MemBlock 中有独立 hybrid TLB 实例。

<code>LduCnt</code>、<code>StaCnt</code>、<code>HyuCnt</code> 的派生见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:57>)。端口数应以最终配置参数求和，不能把源码中的旧注释或某个历史配置硬编码为固定值。

### 2.3 顶层模块连线

~~~mermaid
flowchart LR
  subgraph MB["MemBlock / data-side MMU"]
    LDTLB["dtlb_ld: TLBNonBlock<br/>load + hybrid + stream PF"]
    STTLB["dtlb_st: TLBNonBlock<br/>store"]
    PFTLB["dtlb_pref: TLBNonBlock<br/>SMS + L2BOP"]
    FILT["PTWNewFilter<br/>load/store/prefetch groups"]
    L2["ptw.io.tlb(1)<br/>L2TLB / PTW"]
    PMP["PMPChecker × DTlbSize<br/>PMA/PMP path"]
  end
  LDU["LoadUnit / VSegment"] -->|"TlbRequestIO"| LDTLB
  HYU["HybridUnit"] -->|"TlbRequestIO"| LDTLB
  STU["StoreUnit"] -->|"TlbRequestIO"| STTLB
  PF["Prefetch clients"] -->|"TlbRequestIO"| PFTLB
  LDTLB -->|"PtwReq vector"| FILT
  STTLB -->|"PtwReq vector"| FILT
  PFTLB -->|"PtwReq vector"| FILT
  FILT -->|"one RR-arbitrated PtwReq/cycle"| L2
  L2 -->|"PtwResp, grouped broadcast"| FILT
  FILT -->|"PtwResp vector"| LDTLB
  FILT -->|"PtwResp vector"| STTLB
  FILT -->|"PtwResp vector"| PFTLB
  LDTLB -->|"translated paddr,size,cmd"| PMP
  STTLB -->|"translated paddr,size,cmd"| PMP
  PFTLB -->|"translated paddr,size,cmd"| PMP
~~~

DTLB 接在 <code>ptw.io.tlb(1)</code>；指令侧使用另一个 PTW port。DTLB response 在 MemBlock 内按扁平 requestor 向量重组和广播，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:742>)。

### 2.4 Frontend ITLB：共享 PTW 的另一端

本文重点是访存单元的数据侧，但不能省略 ITLB：它与 DTLB 共用 L2TLB/PTW 的两个输入端口，SFENCE/CSR 上下文也同时到达 Frontend 和 MemBlock。默认 <code>ICacheParameters.PortNumber=2</code>，而 <code>itlbPortNum=PortNumber+1</code>，所以 Frontend 构造一个 3-port <code>TLB</code>：前两个 port 的 <code>Block=false</code> 接 ICache，最后一个 <code>Block=true</code> 接 IFU 的 <code>iTLBInter</code>。[ICache.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/icache/ICache.scala:52>) [Frontend.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:172>)

| ITLB port | 上游 | port 类型 | 特定职责 | miss 语义 |
|---|---|---|---|---|
| 0 | IPrefetch / ICache | non-block | 当前 fetch line 的 execute translation | response 可先带 <code>miss</code>，IPrefetch 保持/重试 |
| 1 | IPrefetch / ICache | non-block | 仅在跨 ICache line 时翻译 <code>nextlineStart</code> | 同上；两线翻译必须都完成 |
| 2 | IFU <code>iTLBInter</code> | blocked | 非压缩指令跨 uncache line 的第二段 MMIO 重译 | 保持 request，等待 matching PTW response |

~~~mermaid
flowchart LR
  IPF["IPrefetch<br/>startAddr / nextlineStart"] -->|"ITLB port 0/1, exec"| ITLB["TLB<br/>2 non-block + 1 blocked"]
  IFU["IFU m_sendTLB<br/>f3_resend_vaddr"] -->|"ITLB port 2, exec"| ITLB
  ITLB --> F["PTWFilter, ifilterSize=8"]
  F --> R["PTWRepeaterNB(passReady=false)"]
  R --> P0["MemBlock L2TLB port 0"]
  P0 --> R
  R --> F
  ITLB -->|"paddr/pbmt/excp"| IC["WayLookup / ICache"]
  ITLB -->|"paddr/pbmt/excp"| IFU
~~~

IPrefetch 的 S0 同时准备 <code>startAddr</code> 与可选 <code>nextlineStart</code>，并要求两个所用 ITLB port 的 ready；request 使用 <code>TlbCmd.exec</code>、<code>size=3</code>、<code>no_translate=false</code>，response ready 固定为真。发生 L1 ITLB miss 时，<code>s1_wait_itlb</code> 反复提交同一 VA，直到需要的两条线都翻译完成才更新 WayLookup。[IPrefetch.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala:102>) [IPrefetch.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala:151>) [IPrefetch.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala:417>)

port 2 不是普通 ICache lookup 端口。IFU 的 <code>m_sendTLB</code> 在非压缩指令跨 uncache line 时对 <code>f3_resend_vaddr</code> 重做 execute translation；blocked response 要求 <code>!miss</code>，之后才检查 PBMT/PMP 并发第二个 uncache request。[IFU.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/IFU.scala:659>) [IFU.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/IFU.scala:871>)

## 3. 接口、信号来源和握手

### 3.1 TlbRequestIO / TlbReq

每个客户端通过 Decoupled 的 <code>TlbRequestIO</code> 发送 <code>TlbReq</code>，接收反向 <code>TlbResp</code>，另有独立 <code>req_kill</code>，见 [MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:623>)。

| 字段 | 来源 | TLB 中的用途 | 结果/消费者 |
|---|---|---|---|
| <code>vaddr</code> | LoadUnit/StoreUnit S0 | VPN 查找、页内 offset 拼接 | storage、PTW request。 |
| <code>fullva</code>、<code>checkfullva</code> | 原始完整 VA | 规范地址检查、跨页 GPA 语义 | response fullva/gpaddr、异常。 |
| <code>cmd</code>、<code>size</code> | load/store/CBO 类型 | 页表权限、PMP/PMA | access fault / 属性。 |
| <code>hyperinst</code>、<code>hlvx</code> | 虚拟化语义 | S1/S2 mode 与权限 | GPF/PF 判断。 |
| <code>memidx</code> | LQ/SQ/预取索引 | 回送、重放关联 | PtwReq、tlbreplay。 |
| <code>kill</code>、<code>req_kill</code> | pipe kill/redirect | 防止错误路径继续使用 translation | 取消、replay。 |
| <code>no_translate</code> | 特殊访问 | 跳过页表翻译 | 但仍做 PMP/PMA。 |
| <code>robIdx</code>、<code>pc</code>、<code>isFirstIssue</code> | 执行/调试上下文 | need_gpa、debug、Difftest 条件 | 不是 storage lookup key。 |

字段定义见 [MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:563>)；PTW payload 中 <code>vpn</code>、<code>s2xlate</code>、<code>memidx</code>、<code>getGpa</code> 的定义见 [MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:1125>)。

### 3.2 TlbResp

| 输出 | 含义 | 下游典型用途 |
|---|---|---|
| <code>paddr(Vec(nDups))</code> | 翻译后的物理地址副本 | Load 组双 paddr、Store 单 paddr。 |
| <code>gpaddr</code> | guest physical address | 虚拟化/跨页异常信息。 |
| <code>miss</code> / <code>fastMiss</code> | 正常 lookup 未命中 / 快速 miss | DCache 投机请求 kill、PTW/replay。 |
| <code>excp.pf/gpf/af</code> | page/guest-page/access fault 分类 | Load/Store S1 异常向量。 |
| <code>pbmt</code> | 页表内存属性 | 导出 NC/MMIO 等属性。 |
| <code>ptwBack</code> | 与 PTW 返回关联 | replay 与反馈。 |

定义在 [MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:596>)；PBMT 的 <code>nc</code>、<code>io</code> 含义见 [MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:434>)。

### 3.3 模块接口图

~~~mermaid
flowchart LR
  REQ["client request<br/>valid + TlbReq"]
  KILL["req_kill / redirect"]
  CSR["satp/vsatp/hgatp<br/>privilege / PBMTE"]
  FENCE["sfence / hfence"]
  TLB["TLBNonBlock / TLB"]
  STORE["TLBFA + superpage storage<br/>replacement state"]
  PTW["PtwReq / PtwResp"]
  RESP["client response<br/>valid + TlbResp"]
  PMPIO["PMP request<br/>valid,paddr,size,cmd"]
  REQ -->|"req.fire captures request"| TLB
  KILL --> TLB
  CSR --> TLB
  FENCE --> TLB
  TLB --> STORE
  STORE -->|"hit entry / refill write"| TLB
  TLB --> PTW
  PTW -->|"same-cycle or delayed bypass"| TLB
  TLB --> RESP
  TLB --> PMPIO
~~~

### 3.4 valid、ready、fire

基础 <code>TLB</code> 只在 <code>io.requestor(i).req.fire</code> 时将请求锁存到 <code>req_out</code>；<code>req_out_v</code> 从该 fire 建立，并以 response fire 或 flush 清除，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:73>)。

DTLB 实际采用的 <code>TLBNonBlock</code> 走 <code>handle_nonblock</code>：

- <code>resp.valid := req_out_v</code>；
- <code>req.ready := resp.ready</code>；
- source 对 client 长期不 ready 有 <code>XSError</code> 检查。

证据在 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:544>)。LoadUnit、StoreUnit、HybridUnit 均将 TLB response <code>ready</code> 置真，见 [LoadUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:953>)、[StoreUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:320>)、[HybridUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/HybridUnit.scala:599>)。

所以，常态 DTLB 响应不会被普通 LDU/STU 的 response backpressure 长期节流；miss 变长主要来自 translation、filter 竞争、PTW 返回和 kill/flush。

## 4. L1 TLB 共用查找、存储和替换

### 4.1 流水骨架

<code>TLB</code> 顶层说明目标是“下一周期返回 paddr，随后继续 PMP/PMA”。request fire 后寄存 VA/fullVA/cmd 等，然后驱动查找，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:39>) 和 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:73>)。

~~~mermaid
flowchart LR
  S0["client S0<br/>VA / cmd / size / req.valid"] -->|"req.fire"| RQ["req_out register<br/>EffectiveVa + translation mode"]
  RQ --> RD["normal-page + superpage lookup"]
  RD --> HIT{"e_hit or p_hit"}
  HIT -->|hit| PERM["paddr assembly<br/>permission + PBMT + PMP/PMA"]
  HIT -->|miss| MISS["miss / fastMiss"]
  MISS --> FILT["PTWNewFilter"]
  FILT --> L2["L2TLB / PTW"]
  L2 --> BYP["PTW response bypass<br/>and refill"]
  BYP --> PERM
  PERM --> S1["client response<br/>paddr / miss / excp"]
~~~

下面的状态图把 <code>req_out_v</code>、blocked hold 和 PTW wait 所形成的**隐式**生命周期画出来；<code>TLB</code> 本身没有为这几项定义一个单独的 Scala <code>Enum</code>。

~~~mermaid
stateDiagram-v2
  [*] --> Idle
  Idle --> Held: requestor.req.fire and not kill
  Held --> HitResp: e_hit or p_hit
  Held --> NonBlockMiss: not hit and Block=false
  Held --> BlockedWait: not hit and Block=true
  NonBlockMiss --> SendPtw: io.ptw.req.fire
  NonBlockMiss --> TlbReplay: tlbreplay
  SendPtw --> Idle: miss response fire
  TlbReplay --> Idle: miss response fire
  HitResp --> Idle: response fire
  BlockedWait --> BlockedWait: unrelated ptw.resp
  BlockedWait --> HitResp: matching ptw.resp.fire
  Held --> Idle: req_kill or flushPipe
  BlockedWait --> ForcedPF: flushPipe
  ForcedPF --> Idle: response fire
~~~

non-block/blocked 分支和 PTW bypass 的实证代码分别在 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:544>)、[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:591>)、[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:684>)。

### 4.2 TLBFA：并行读、单 refill 写入口

<code>TLBFA</code> 使用 valid 位和 sector entry 寄存器向量。每个 read port 比较所有 way；命中向量在 request fire 后寄存，response valid 随后产生，见 [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:86>)。

| 资源 | 已证实行为 | 并发边界 |
|---|---|---|
| lookup | requestor 各有 read request，能并行比较 | 不是单读端口 TLB 仲裁模型。 |
| refill write | 单一 <code>io.w</code> 写入口，写一个 victim way | 同一 L1 组同时只能接受其单 response/write 资源支持的 refill。 |
| replacement | hit touch way；refill 使用 replacement state | 属于微结构状态。 |
| read/refill 同周期 | <code>refill_mask</code> 排除正改写 way | 避免错误把改写中的 way 当普通 hit。 |
| PTW 返回 | 顶层提供 same-cycle/delayed bypass | 避免等待下一次普通 storage lookup。 |

写入、重复写检查和 access 更新见 [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:169>)；normal/superpage storage 和内部 replacement 选择见 [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:379>)。

<code>TlbSectorEntry</code> 不是“一个 way 只存一个固定 4 KiB 翻译”：它包含 sector VPN tag、ASID/VMID、页级、8 个连续 4 KiB subslot 的 valid/PTE/PPN-low 相关字段、S1/S2 permission 和 PBMT；superpage hit 会忽略对应低 VPN 位，再由 <code>genPPN</code> 以请求 VPN 补齐低 PPN。[MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:181>) [MMUBundle.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:390>)

<code>NSets=1</code> 在本实现不构成一个 set-indexed data array：非 softTLB path 的工厂无条件建立 <code>TLBFA</code>，每个 read port 比较所有 way，<code>get_set_idx</code> 仅流到 replacement access metadata 且源码标注为未使用。<code>Associative</code> 虽作为工厂实参传入，却不在该分支选择不同存储实现。[TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:104>) [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:352>)

还有两项应明确标成未生效/待验证：<code>TlbStorageWrapper</code> 未把 <code>q.saveLevel</code> 传入 storage，故使用默认 <code>saveLevel=false</code>；本 commit 未找到 <code>partialStaticPMP</code> 的消费点。它们不能被当成已经启用的优化。

生命周期上，reset 只清 <code>v</code>；entry payload 由 valid 门控。refill 时单写口置目标 way valid 并写合并后的 sector entry；SFENCE/HFENCE 依据地址/ASID/VMID/global 规则清 valid。默认 <code>outReplace=false</code> 使 wrapper 内以 <code>ReplacementPolicy.fromString(q.Replacer, q.NWays)</code> 建立 PLRU，并把所有 hit/refill touch 提供给 policy。该源码没有显式“先选 invalid way”的 wrapper 选择，因此 cold refill、部分填充、多 port 同拍 touch 和 refill/fence 同拍都应做定向验证。[TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:169>) [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:187>) [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:434>)

### 4.3 命中、miss 和 PTW bypass

normal-page 与 superpage 命中以 <code>e_hit || p_hit</code> 合成，物理地址由 PPN 和 VA page offset 拼接。PTW response 一边更新 storage，一边对当前请求比较并提供 same-cycle / next-cycle bypass，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:300>)、[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:686>)。

因此“refill 已到达”不等价于“软件必须重新发一次 TLB request”。匹配请求可由 bypass 跨过寄存阵列写后可见性的空隙。

### 4.4 存储冲突的验证点

代码对 multi-hit 有性能检查痕迹，而结果由 hit vector 驱动；不能把 multi-hit 当成功能性正常状态。应定向构造同 ASID/VMID/VPN 的重叠 entry，观察 multi-hit 计数/断言，而不是依赖 Mux 的隐式选择。命中向量逻辑见 [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:104>)。

若启用 <code>softTLB</code>，storage 会改走伪实现分支，见 [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:352>)。本文的 replacement/timing 结论以标准 <code>softTLB=false</code> 路径为前提。

## 5. Load、Store、Hybrid 和预取接入

### 5.1 LoadUnit

LoadUnit S0 形成 TLB request，携带 VA、fullVA、size、访存命令、LQ 索引、ROB/debug 信息和 <code>no_translate</code>，见 [LoadUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:360>)。

Load S1 消费 <code>paddr(0/1)</code>、<code>gpaddr</code>、<code>miss</code>、<code>pbmt</code> 和 fault：

- TLB miss 会 kill 同期投机发出的 DCache 路径；
- PBMT 形成 NC/MMIO 属性；
- PF/GPF/AF 汇入 Load 异常向量；
- <code>ptwBack</code> 汇入 replay/反馈。

证据在 [LoadUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:899>)。正确理解是“Load S0 交 TLB，Load S1 用翻译结果修正/kill cache 投机”，不是“TLB hit 前 DCache 一定完全静止”。

### 5.2 StoreUnit

StoreUnit 在 S0 发出带 write/CBO 命令、SQ <code>memidx</code>、VA 与异常控制的 TLB request，见 [StoreUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215>)。Store S1 使用 paddr/gpaddr/miss/PBMT/fault 形成输出；miss、异常、MMIO/NC 会阻断不应继续的 DCache 路径，见 [StoreUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:290>)。

### 5.3 Hybrid、预取和特殊共享

HybridUnit 复用 load DTLB requestor，预取客户端使用 prefetch 组，分别见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1081>)、[MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1213>)。它们在 filter 中属于不同 group，预取 miss 不会直接等价为 demand load/store 的同一 pending 槽。

Vector segment 与 load port 0 共享 request 端口，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:935>)；其 response 接法见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:2058>)。AMO 也可借用 load port 0，但附近保留 PMP 支持 TODO，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1819>)，故不能把 AMO/PMP 覆盖率写成已充分验证。

## 6. 地址、S1/S2、权限、PBMT 和 PMP/PMA

### 6.1 Effective VA 与规范地址

TLB 先形成 Effective VA，并结合 PMM、Sv39/Sv48 等模式检查规范地址；非法地址可形成 PF/GPF/AF 语义，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:180>)。

### 6.2 一阶段和二阶段翻译

TLB 按 <code>virt</code>、<code>hyperinst</code>、<code>vsatp</code>、<code>hgatp</code>、特权模式等选择：

- 只走 S1 或 bare；
- <code>vsatp</code> bare、<code>hgatp</code> 有效时只走 S2；
- 两者有效时走 all-stage；
- <code>no_translate</code> 跳过页表翻译，但仍不跳过 PMP/PMA。

模式判断见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:96>)。

### 6.3 paddr、gpaddr 与跨页 fullva

译后 paddr 由 PPN 与 VA page offset 拼接。对于 split load/跨页虚拟化语义，代码使用 <code>fullva</code> 和 VA bit 12 选择 GPA 对应虚拟地址，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:397>)。

准确说，这是为第二页保留正确 GPA/异常地址语义；它**不是** TLB 自己把一条访存拆成两条的证据。

### 6.4 权限、PBMT 与 PMP/PMA

TLB 合成 S1/S2 R/W/X、U/S、SUM、MXR、VMXR、HLVX、A/D 等权限，形成 PF/GPF/AF；选择 S1 或 S2 PBMT；并发送 <code>valid/paddr/size/cmd</code> 给 PMP，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:429>)、[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:449>)。

MemBlock 为每个 DTLB requestor 建立 <code>PMPChecker</code>、分发 CSR 和 mode，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:790>)。PMPChecker/PMA 合成 access-fault/MMIO；PMA 中 <code>!cfg.c</code> 参与 MMIO 判定，见 [PMP.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/PMP.scala:563>)、[PMA.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/backend/fu/PMA.scala:211>)。

异常优先级必须按代码而不是泛泛的“page fault 优先”理解：<code>perm_check</code> 对 PF/GPF 的赋值受 <code>!af</code> 门控，所以 PF/GPF 与 AF 同拍成立时，AF 覆盖它们。对 execute，<code>ifetch</code> 还进入 S-mode/U-page 的条件，不能把 data load 的 SUM 直觉套到 ITLB。[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:449>)

<code>no_translate</code> 仅绕开页表翻译：TLB 在该条件下以 request 的 <code>pmp_addr</code> 代替译后 paddr 发送 PMP request，仍会做 size/cmd/PMP/PMA 检查。[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:270>)

## 7. SFENCE/HFENCE、redirect 和失效

### 7.1 失效源

TLB 将 <code>sfence.valid</code>、<code>satp/vsatp/hgatp</code> 变化和 <code>virt_changed</code> 合成为 MMU flush。<code>sfence</code>/CSR 在内部经 <code>q.fenceDelay</code> 延迟，默认 <code>fenceDelay=2</code>，见 [MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:31>)、[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:60>)。

对 DTLB，MemBlock 把 <code>flushPipe</code> 固定为 false，但仍连接 sfence、TLB CSR、redirect 和 ROB pending pointer，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:704>)。所以“不使用通用 flushPipe”不等于“DTLB 忽略 SFENCE/CSR flush”。

### 7.2 HFENCE 的保守失效

TLBStorage 中普通 SFENCE 可按 VA/ASID/global 等条件失效。对于 <code>HFENCE.VVMA</code>，源码明确说明二阶段 combined L1 entry 难以按地址精确匹配，实际会保守清除受影响 VMID/ASID 的 S2 entry，见 [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:187>)、[TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:216>)。<code>HFENCE.GVMA</code> 的 VMID/两阶段清除紧随其后，见 [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:268>)。

这是“失效粒度更大”的实现选择，不应误读为错误地保留 stale entry。

## 8. ITLB/DTLB miss、PTW filter、L2TLB 和 replay

### 8.1 DTLB 到 filter

MemBlock 扁平化三个 DTLB 的 PTW 接口，并经 <code>PTWNewFilter</code> 接到 <code>ptw.io.tlb(1)</code>，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:742>)。

| filter 组 | 输入 | 静态容量常量 | 含义 |
|---|---|---:|---|
| load | load DTLB 全部 requestor | 16 | 合并 demand load、hybrid/stream 一侧等待的翻译。 |
| store | store DTLB requestor | 8（当前 StorePipelineWidth 小于 3 的分支） | 合并 store translation。 |
| prefetch | prefetch DTLB requestor | 8 | 与 demand 组隔离。 |

常量在 [MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:126>)，filter 分组/构建在 [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:338>)。传给构造器的 <code>l2tlbParams.dfilterSize</code> 不是上述三组容量的简单总和，不能混写。

### 8.2 合并和 RR 仲裁

<code>PTWFilterEntry</code> 保留 pending 的 <code>vpn/s2xlate/getGpa/memidx</code> 和 <code>sent</code>。相同 translation key 可合并；每组挑选一个尚未发出的 entry，三个组经 3 输入 RR arbiter 向下游发一条 <code>PtwReq</code>，见 [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:238>)、[Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:425>)。

可精确得到：

- 三组可并行维护 pending state；
- filter 到 L2TLB 每周期至多一条 <code>PtwReq.fire</code>；
- RR 未获胜组保持 <code>v=1,sent=0</code> 等待；
- 相同 key 合并避免重复 page walk，但不等价于不同 VPN 并行发射。

filter 的满表/响应/flush 状态机见 [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:163>)。filter hints 进入 LoadQueue replay 控制，见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:999>)、[LoadQueueReplay.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:338>)。

### 8.3 ITLB PTWFilter、repeater 与 port 0

ITLB 的 <code>io.ptw</code> 先经 <code>PTWFilter(Width=itlbPortNum, Size=ifilterSize=8)</code>，再经 <code>PTWRepeaterNB(passReady=false)</code> 跨 Frontend/XSCore 到 MemBlock，并接到 <code>ptw.io.tlb(0)</code>；DTLB filter 则接 <code>ptw.io.tlb(1)</code>。<code>PtwWidth=2</code> 是这两个端口的宽度，而不是“两条 PTW 一定同时完成”的性能承诺。[Frontend.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/Frontend.scala:182>) [XSCore.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSCore.scala:233>) [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:784>) [MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:237>)

ITLB <code>PTWFilter</code> 与数据侧 filter 一样合并相同 VPN/<code>s2xlate</code> 的等待者并扇回 response，但它将 input ready 接为 <code>canEnqueue_fake</code>；源码注释明说可能看到 false ready 而实际接收，或丢弃未 fire request。该实现必须以真实 <code>fire</code>、entry 状态和波形验证，不能抽象成严格逐请求 FIFO。[Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:441>) [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:535>)

### 8.4 L2TLB/PTW 返回

标准非 <code>softPTW</code> 路径中，L2TLB 组织 TLB cache、miss queue、PTW/LLPTW；返回结果按 source 仲裁到各 <code>io.tlb</code> 输出，见 [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:35>)、[L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:628>)。fake/真实路径选择在 [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:1044>)。

L1 收到 response 后会 refill、做 PTW bypass、重新合成权限/PBMT/PMP/PMA response，并经 <code>ptwBack</code>/<code>tlbreplay</code> 恢复客户端。

L2TLB 的有效资源组合是 request/response arbiters、<code>PtwCache</code>、<code>L2TLBMissQueue</code>、<code>PTW</code>、<code>HPTW</code>、<code>LLPTW</code> 和 page-table memory path。<code>tlbCounter</code> 对 L2 TLB request/response 计数，flush 时清零，并以 <code>MissQueueSize</code> 限制进入 L2 的 outstanding translation；默认 <code>MissQueueSize = ifilterSize + dfilterSize = 8 + 32 = 40</code>。[L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:125>) [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:176>) [MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:274>) [L2TLBMissQueue.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLBMissQueue.scala:29>)

L2TLB 确实对 PtwCache 入口有“MissQueue request 优先”的源码注释，但通用 Chisel arbiter 的所有同时输入 winner 不在本文未展开的生成 RTL 中，因此本文只描述已知的 ready/queue/counter 约束，不声称固定的跨模块优先级。[L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:300>)

### 8.5 Page-table cache 与 walkers

| 阶段 | owner | 已证实的工作 | 状态/并行边界 |
|---|---|---|---|
| PtwCache | L2TLB | 查 L3/L2/L1/L0/superpage 的页表中间项；hit 直接参与返回 | 默认 L3=16 FA、L2=16 FA、L1=4×2、L0=64×4、superpage=16；flush 清 cache pipeline |
| PTW | L2TLB | 逐级遍历非-leaf PDE，并执行 page-table address/PMP/memory 控制 | 非无限宽；叶页处理转给 LLPTW |
| LLPTW | L2TLB | 处理 4 KiB leaf；同 VPN/stage 可合并 | <code>llptwsize=6</code>，每 entry 有独立状态，仍共享后端 memory 路径 |
| HPTW | L2TLB | 处理 GPA→HPA 的二阶段辅助 walk | guest/PMP/GPF 条件决定是否进入；不能与普通 S1 walk 混写 |

PTW 设计注释明确把非-leaf PDE（典型 1 GiB/2 MiB）留给 PTW，把 4 KiB leaf 留给 LLPTW。[PageTableWalker.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:32>) PtwCache 的结构/管线见 [PageTableCache.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:204>)，参数见 [MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:48>)。

LLPTW 的 state Vec 枚举 <code>idle</code>、<code>hptw_req</code>、<code>hptw_resp</code>、<code>addr_check</code>、<code>mem_req</code>、<code>mem_waiting</code>、<code>mem_out</code>、<code>last_hptw_req</code>、<code>last_hptw_resp</code>、<code>cache</code>、<code>bitmap_check</code>、<code>bitmap_resp</code>。这说明其是多 entry FSM，不等同于“六个 memory read 同周期返回”。[PageTableWalker.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:711>) [PageTableWalker.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala:920>)

page-table memory request 以 <code>blockBytes</code> 对齐（默认 64 B）。若 flush 发生在 memory response 到达前，<code>flush_latch</code> 阻止 stale refill 被拿来服务新上下文，同时继续 drain outstanding memory ack。这是 walker 的 page-table line 行为，不能外推为普通 DCache load/store line 的行为。[L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:361>) [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:687>)

默认 <code>enablePrefetch=true</code> 的 L2TLB prefetch 维护四个旧 VPN 记录，对 next VPN 去重。它是页翻译预取，不是 ICache <code>IPrefetch</code>，也不证明任何指令/数据 line 已被取到。[MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:48>) [L2TlbPrefetch.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala:35>)

### 8.6 need_gpa

GPF 等特殊情形下，TLB 通过受 ROB/执行状态约束的 <code>need_gpa</code> 路径发带 <code>getGpa</code> 的 PTW request；它有计数和 redirect/flush 清除条件，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:108>)、[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:300>)。验证时应按稳定 ROB 身份追踪，不能只按 PC 归属。

## 9. 动态场景与时序图

### 9.1 普通 Load TLB hit

1. LoadUnit S0 发送 VA/size/cmd，<code>req.valid && req.ready</code> 形成 fire。
2. TLB 锁存请求，下一周期进行 normal/superpage lookup。
3. hit 后形成 paddr、PBMT、权限和 PMP/PMA 结果。
4. LoadUnit S1 消费 response；不发 PTW request。

~~~waveform-draw
{
  "signal": [
    {"name":"clk","wave":"p......"},
    {"name":"ldu.tlb.req.valid","wave":"0100000"},
    {"name":"ldu.tlb.req.ready","wave":"1111111"},
    {"name":"ldu.tlb.req.fire (=valid&&ready)","wave":"0100000"},
    {"name":"ldu.tlb.resp.valid","wave":"0010000"},
    {"name":"ldu.tlb.resp.bits.miss","wave":"x000000"},
    {"name":"dtlb.ptw.req.valid","wave":"0000000"}
  ]
}
~~~

这是 hit 的局部 TLB 时序，不是 DCache 数据返回延迟承诺。请在 VS Code 的 Markdown Preview 中渲染 WaveDrom；直接查看 Markdown 源文件不会显示波形。

### 9.2 两个相同 VPN load miss 合并

1. 两个 requestor 相继发现 L1 miss。
2. 两者进入 load filter，若 VPN/S2xlate key 相同则合并为一条 pending PTW entry。
3. 三组中只有一个请求经 RR 得到下游 <code>PtwReq.fire</code>。
4. L2TLB/PTW 返回后，filter 回送 response，L1 refill/bypass 让等待者恢复。

~~~waveform-draw
{
  "signal": [
    {"name":"clk","wave":"p........."},
    {"name":"ldu0.tlb.req.fire","wave":"0100000000"},
    {"name":"ldu1.tlb.req.fire (same VPN)","wave":"0010000000"},
    {"name":"ldu0.tlb.resp.bits.miss","wave":"x010000000"},
    {"name":"ldu1.tlb.resp.bits.miss","wave":"xx01000000"},
    {"name":"load-filter.pending(same VPN)","wave":"0001110000"},
    {"name":"filter->L2TLB.ptw.req.fire","wave":"0001000000"},
    {"name":"L2TLB/PTW response","wave":"0000000100"},
    {"name":"L1 refill / PTW bypass","wave":"0000000010"}
  ]
}
~~~

后半段只表示可变长 page-walk 窗口，图中的绝对周期不是固定承诺。必须验证的 invariant 是“同 key 只应向下游发一条 PTW request”。

### 9.3 flush、redirect 和 kill

<code>sfence</code>/CSR 改变会使 storage/filter 进入 flush。若请求在 PTW 发射后被 <code>req_kill</code>/redirect 取消，nonblocking 逻辑撤销请求并用 <code>tlbreplay</code>/状态清理避免旧路径 translation 变成新路径 response，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:544>)；filter flush 清 pending entry，见 [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:372>)。

### 9.4 blocked ITLB：跨 uncache line 的恢复路径

1. IFU 发现在非压缩指令边界需要第二段 uncache 取指，进入 <code>m_sendTLB</code>，将 <code>f3_resend_vaddr</code> 送到 ITLB port 2。
2. port 2 的 <code>Block=true</code> 使 L1 miss request 被保持；它不是像 ICache port 一样返回一个让客户自行重发的普通 non-block miss。
3. matching PTW response 到达后，TLB 拼出 PA、PBMT/权限并返回；IFU 确认 <code>!miss</code> 后才做 PBMT/PMP 和第二个 uncache request。
4. 若 <code>flushPipe</code> 中途发生，blocked TLB 会人为产生 PF response 以释放外部等待者。该 PF 是 pipeline recovery 信号，IFU 应丢弃相应错误路径结果，不能记作已经验证的真实页故障。[TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:591>) [IFU.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/IFU.scala:659>)

## 10. 延迟、吞吐和资源边界

| 路径 | 可证实最短行为 | 不可静态承诺部分 |
|---|---|---|
| L1 hit | request fire 后寄存查找，目标为下一周期产生 paddr/response | PMP/PMA、客户 pipe、DCache 后续级数不属于“TLB hit 总访存延迟”。 |
| L1 miss、filter 空闲 | miss 可入 filter，获 RR 后可发一个 PTW request | L2 hit/miss、page walk、memory response 是可变延迟。 |
| 同 VPN miss 合并 | 后到者可加入已存在 pending entry | 仍等待共享 response，不是零延迟 hit。 |
| ITLB blocked miss | 保持 IFU request，匹配 PTW response 后给最终 PA | IFU MMIO/uncache FSM、PMP、page walk 和 pipe flush 都会改变时延。 |
| flush/redirect | stale lookup/refill 受 kill/flush 抑制 | 与正在返回 PTW response 的精确竞态需波形确认。 |

| 资源 | 吞吐/限制 | 影响 |
|---|---|---|
| L1 lookup | 多 requestor 并行比较 | 不等于单端口 TLB 每次仅服务一条。 |
| L1 refill | 单一 write 接口 | 同一 L1 组的 refill 受单 response/write 资源限制。 |
| 三个 filter group | 各自维护 pending entries | 可隔离流量、合并同 key。 |
| filter → L2TLB | 3 路 RR、每周期一条 | 不同组并发 miss 的共享瓶颈。 |
| ITLB filter → L2TLB | ITLB port 0 的 filter/repeater 独立于数据侧三组 | <code>canEnqueue_fake</code> ready caveat 需由 client fire/waveform 验证。 |
| L2TLB/PTW | cache/miss queue/PTW/LLPTW 共同决定 | 必须实测/看波形才可量化。 |

故延迟应写成“L1 hit 的局部近似固定流水 + miss 的可变延迟”，不能合并为一个常数。容量也要和带宽分开：<code>NWays=48</code> 是存储参数，filter 每周期一条是 PTW 发射带宽。

## 11. 跨边界代码解析

### 11.1 16B 非对齐、跨虚拟页

已证实的执行拆分边界是 **16B execution granule**，不是 TLB 内部 cache-line split。Load/Store 非对齐检测和 MisalignBuffer 两片构造见 [LoadMisalignBuffer.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:292>)、[StoreMisalignBuffer.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:330>)。

每一片都会重新进入普通 LoadUnit/StoreUnit S0 并走 DTLB：

- Load 的 <code>misalign_ldin</code> 参与 S0 候选，形成 <code>s0_tlb_valid/vaddr/fullva</code>，见 [LoadUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:315>)；MemBlock 连线见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1023>)。
- Store 的 S0 同样发 DTLB request，见 [StoreUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215>)；StoreMisalignBuffer 连线见 [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1284>)。

因此跨 4 KiB 虚拟页时，两片各自有翻译/权限结果；<code>fullva</code> 只保证正确 GPA/异常地址语义。

### 11.2 64B cache line：已证实和未证实

默认 DCache <code>blockBytes=64</code>，<code>blockOffBits=log2Up(blockBytes)</code>，见 [DCacheWrapper.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:53>)、[L1Cache.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/L1Cache.scala:48>)。

本次追踪到的 16B 条件来自 Load/Store/MisalignBuffer；LoadUnit 的 <code>blockOffBits</code> 比较用于 store-load nuke/相关判断，见 [LoadUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:990>)，不是 TLB 或执行拆分。

- **[代码已证实]** DTLB 翻译送给它的 VA，没有 64B line split 状态机。
- **[代码已证实]** 16B 非对齐/跨页分片会重新查 DTLB。
- **[待验证]** 单条请求跨 64B DCache line 时，DCache/MissQueue 怎样拆分或合并 transaction，超出本次 TLB source slice；不能误写成“DTLB 负责 line split”。

### 11.3 MMIO/uncache

PBMT、PMP/PMA 可使 response 为 NC/MMIO。若 split access 的任一片成为 uncache/MMIO，LoadMisalignBuffer/StoreMisalignBuffer 停止余片并转为软件可见的 misaligned 异常处理，见 [LoadMisalignBuffer.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala:522>)、[StoreMisalignBuffer.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542>)。

这避免将强序/非缓存访问静默拆为普通 cacheable fragments；最终何时精确提交仍是 LSQ/ROB 责任。

### 11.4 投机与 replay

TLB request 含 <code>robIdx</code>，MemBlock 也接入 redirect 和 <code>robPendingPtr</code>。LoadQueue replay 使用 TLB filter hints 协调阻塞/解除，见 [LoadQueueReplay.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueReplay.scala:685>)。做波形分析时应以 <code>robIdx + memidx</code> 追踪动态实例，不应只看 PC。

### 11.5 取指页、ICache line 与 uncache line

ITLB 有两个不同的“跨界”概念，不能混写：

| 情形 | 有效实现 | 例外/恢复 | 不可外推 |
|---|---|---|---|
| ICache 64 B line 跨越 | IPrefetch 在真正跨 line 时启动 port 1 对 <code>nextlineStart</code> 的 execute translation，并等待两线翻译 | 任一 L1 miss 都使 <code>s1_wait_itlb</code> 保持/重试；最终才写 WayLookup | 这不是 data DTLB 对任意 64 B line 自动双查 |
| instruction virtual-page 跨越 | TLB 注释明确 Frontend 自行处理 cross-page fetch，ITLB 的 <code>fullva</code> 在该路径不使用 | instruction PF/GPF 与对应 paddr/gpaddr/provenance 由前端路径保留 | 不把 data misalign split 的 <code>fullva</code> 规则套到 ITLB |
| non-compressed instruction 跨 uncache line | IFU 用 blocked port 2 对 second fragment 重译 | matching response 后 PBMT/PMP，再发第二个 uncache request | port 2 不是常规 ICache port，PF-for-release 不是普通 PF |

证据见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:397>)、[IPrefetch.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala:102>)、[IPrefetch.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala:417>)、[IFU.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/IFU.scala:659>)。

## 12. Difftest、性能计数和波形可观测性

<code>DiffL1TLBEvent</code> 只在 request/response handshake 成功、无 miss/PF/AF/GPF 且翻译有效时产生，并按 TLB 名称限制到 itlb/ldtlb/sttlb，见 [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:750>)。它是一次成功的**微结构翻译观察**，不是 ROB 已提交的访存。

L2 路径也产生 <code>DiffL2TLBEvent</code>，见 [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:563>)。建议的波形组如下：

| 层 | 信号/状态 | 目的 |
|---|---|---|
| requestor | req valid/ready、VA/fullVA、memidx、robIdx | 确认谁发出何种 translation。 |
| L1 TLB | req_out_v、hit/miss、response valid、fault、pbmt | 分开 lookup、exception、普通 response。 |
| filter | pending v/sent/vpn、group grant、ptw.req.fire | 证明合并和 RR 仲裁。 |
| L2/PTW | PtwReq、PtwResp、source/return index | 追踪 miss 服务与回送。 |
| kill/replay | req_kill、redirect、tlbreplay、filter hint | 排查错误路径和满表恢复。 |
| DCache/LSQ | S1 kill、NC/MMIO、MAB input/output | 核验跨 TLB 与执行边界。 |

## 13. 验证特别注意

| verification ID | risk invariant | directed stimulus | expected | named checker / observation | source evidence |
|---|---|---|---|---|---|
| V-TLB-01 | L1 hit 不应发 PTW | 同 ASID、同 VPN 连续 Load | fill 后后续 miss=0，ptw fire=0 | DiffL1TLBEvent + L1 hit/miss | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:300>) |
| V-TLB-02 | 同 key miss 只发一次 walk | 两个 LDU 同时/相邻发同 VPN/S2xlate | filter 合并，只有一条对应 PTW fire | pending key、RR output | [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:238>) |
| V-TLB-03 | 不同 group 不可同周期双发 PTW | load/store/prefetch 同时不同 VPN miss | 每周期最多一个下游 fire | 3-input RR grant/fire | [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:425>) |
| V-TLB-04 | stale PTW response 不污染有效路径 | miss 后 redirect 或 SFENCE，再使 PTW 返回 | kill/flush request 不产错误 response/refill | req_kill、flush、tlbreplay | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:544>) |
| V-TLB-05 | HFENCE.VVMA 保守失效仍无 stale S2 entry | 填多个 VMID/ASID/S2 entry 后执行 HFENCE | 受影响 VMID/ASID entry 均清除 | storage valid bitmap、fence 参数 | [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:216>) |
| V-TLB-06 | no_translate 不绕过 PMP/PMA | no_translate=1，地址位于 PMP deny 区 | 无 page walk，但产生 access fault | PMP valid/paddr/size/cmd、fault | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:429>) |
| V-TLB-07 | 跨页非对齐的两片都翻译 | 构造跨 4KiB 的 16B 非对齐 load/store | 两片走普通 DTLB，fullva/GPA 正确 | MAB、TLB req、paddr/gpaddr | [LoadUnit.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala:315>) |
| V-TLB-08 | MMIO/NC 分片不继续第二片 | split access 任一片为 PBMT/PMA NC/MMIO | 停止余片，走 misaligned 异常 | MAB state、NC/MMIO、exception | [StoreMisalignBuffer.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542>) |
| V-TLB-09 | 不存在多 hit 歧义 | 构造重叠 translation entry | multi-hit 检查/统计暴露问题 | hit vector、perf/assert | [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:104>) |
| V-TLB-10 | GPF GPA request 归属正确 | 虚拟化 GPF 后插 redirect | getGpa 与 ROB 实例匹配 | need_gpa、getGpa、robIdx | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:108>) |
| V-TLB-11 | refill 同 way lookup 不得消费旧 PPN | 对已有 way 发生 PTW refill，同时发送 matching lookup | storage hit 被 refill mask 遮蔽；matching request 只从 PTW bypass 获得新翻译 | hitVec、p_hit_fast/p_hit、old/new PPN scoreboard | [TLBStorage.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:114>) [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:684>) |
| V-TLB-12 | PF/GPF/AF 优先级符合实现 | 使 PTE PF/GPF 与 PMP/PMA AF 同拍成立，覆盖 ld/st/exec | AF 覆盖 PF/GPF 的实际门控；下游 exception vector 与 cmd 对应 | TlbResp excp checker；PMP/PMA + PTE scoreboarding | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:449>) |
| V-TLB-13 | blocked ITLB 不可提前返回普通 miss | IFU port 2 miss，混入无关/相关 PTW response；再插 flushPipe | 无关 response 不释放，相关 response 才返回 PA；flush 只形成 release PF | held request temporal assertion、IFU m_sendTLB state | [TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala:591>) [IFU.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/frontend/IFU.scala:659>) |
| V-TLB-14 | PTWFilter fake-ready 不得误判为已接受 | ITLB request 在 filter ready=false/同 key 同拍回包时交织 | 只依真实 fire 登记/丢弃，所有已登记 waiter 最终得到同 key response | filter entry v/sent、req.fire、response fanout waveform | [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:441>) [Repeater.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/Repeater.scala:535>) |
| V-TLB-15 | L2 flush 不得使旧 page-table memory refill 服务新上下文 | 发 PTE memory request 后发 sfence/CSR change，再给 response | <code>flush_latch</code> 抑制 stale refill，但 outstanding ack 仍被 drain | context-tag scoreboard、waiting_resp/flush_latch coverage | [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:452>) [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:687>) |
| V-TLB-16 | L2 outstanding 不超过 queue 上界且能 forward progress | 填满 ITLB/DTLB filters 和 L2 MissQueue，再逐步释放 response | <code>tlbCounter &lt;= 40</code>，无死锁；每个已接受 request 最终 response/replay | counter bound assertion、liveness cover、MissQueue occupancy | [MMUConst.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/MMUConst.scala:274>) [L2TLB.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/mmu/L2TLB.scala:183>) |
| V-TLB-17 | LDU0 共享端口的 response/PMP ownership 无交叉污染 | VSegment、Atomics 与 scalar LDU0 邻近/并发请求 | 无双 consumer/错路 response；AMO PMP TODO 不被测试掩盖 | generated RTL + waveform temporal assertions | [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:935>) [MemBlock.scala](</home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1817>) |

## 14. 已证实结论、限制和后续验证

### 14.1 已证实

1. MemBlock 对数据侧建立 load、store、prefetch 三个 <code>TLBNonBlock</code>；Hybrid 复用 load 组。
2. Frontend 的 ITLB 是两个 non-block ICache ports 加一个 blocked IFU uncache-resend port；它经独立 filter/repeater 接 L2TLB port 0。
3. TLB request/response 以 Decoupled 传递 VA、权限、ROB 等上下文，并返回 paddr、PBMT、miss 和 fault；non-block 与 blocked miss 的可见语义不同。
4. data miss 经 <code>PTWNewFilter</code> 合并，ITLB miss 经 <code>PTWFilter</code> 合并；两者分别使用 L2TLB port 1/0。
5. L1 storage 有全 way lookup、单 refill write、sector/superpage entry 和 PTW response bypass；<code>NSets=1</code> 不是 set-indexed array。
6. L2TLB 的 PtwCache、MissQueue、PTW/LLPTW/HPTW 决定 miss 的可变返回；PMP/PMA、PBMT 仍决定 access-fault、NC/MMIO 语义。
7. MisalignBuffer 两片重走普通 DTLB；ITLB cross-line/uncache boundary 有自己的 port 与 IFU FSM，二者不能混写。

### 14.2 未由本次静态追踪定论

| 项目 | 原因 | 下一步 |
|---|---|---|
| 特定 workload 的 L1/L2 TLB hit latency | L2/PTW/memory 返回可变 | 用 FST 依 ROB 跟踪 request、filter、response、replay。 |
| 最终 elaboration 的完整端口数 | 参数可覆盖 | 固定 config 后导出顶层参数/信号表。 |
| partialStaticPMP 的优化效果 | 只证实为配置项 | 单独追踪参数透传与生成硬件。 |
| 64B line-cross 的 DCache transaction | 超出本次 TLB source slice | 从 LoadUnit S1 沿 DCache/MissQueue 继续分析。 |
| AMO 复用 port 的 PMP 完整性 | 源码保留 TODO | AMO + PMP deny 定向测试/波形。 |
| ITLB filter 在 fake-ready 条件下的客户协议 | 注释承认特殊 ready 语义 | 以 req.fire、entry 状态和波形检查。 |
| L2TLB 多输入同拍 arbiter winner | 未展开生成 RTL / Chisel Arbiter 实现 | 生成 RTL 并观察 grant/fire。 |
| refill 与 SFENCE/HFENCE 同拍时的最终 valid/replacement 状态 | 同一 valid/state 有多个条件更新 | 以定向回归和波形确认。 |

## 15. 结论

昆明湖 V2 的 Load/Store TLB 并非孤立的 VA 到 PA 表，而是由 ITLB 的取指/uncache 重译、数据侧多端口 nonblocking L1 lookup、权限和属性检查、ITLB/DTLB filters、共享 L2TLB/PTW 发射及 LoadQueue replay/redirect 恢复共同构成的翻译子系统。

阅读和验证应始终：

1. 用 <code>valid/ready/fire</code> 与稳定的 <code>robIdx + memidx</code> 跟踪动态请求；
2. 将 L1 hit、filter 合并、L2/PTW 可变返回、flush/kill 分开建模；
3. 区分 16B 非对齐分片、4KiB 跨页翻译、ITLB line/uncache 路径与 64B data-cache line 行为；
4. 不把 TLB Difftest event 当作体系结构提交；
5. 对 AMO/PMP、<code>partialStaticPMP</code>、ITLB fake-ready、L2 arbiter 和 DCache line-cross 明确保留验证边界。
