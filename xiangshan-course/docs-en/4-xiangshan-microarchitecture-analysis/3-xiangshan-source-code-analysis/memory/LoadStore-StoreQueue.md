# Kunminghu V2 StoreQueue（SQ）代码细粒度分析

## 1. 范围、基线与结论

| 项目 | 本次分析基线 |
| --- | --- |
| 使用的 skill | 当前目录的 analyze-xiangshan-kunminghu |
| 用户指定源码 | /home/yanyusong/xs-memory-env/XiangShan |
| 上游仓库 | [OpenXiangShan/XiangShan](https://github.com/OpenXiangShan/XiangShan.git) |
| 分支/提交 | kunminghu-v2 @ e12436c7cba86b195deec24981976d78bc263661 |
| 主要源码 | StoreQueue.scala、StoreQueueData.scala、LSQWrapper.scala、StoreUnit.scala、MemBlock.scala、Sbuffer.scala |
| Design Doc 基线 | 未咨询：本地 XiangShan-Design-Doc checkout 不存在。本文不将设计文档或通用教材说法表述为当前 RTL 行为。 |
| 课程材料 | 只用本地 [14_LoadStore.md](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/14_LoadStore.md) 解释术语；它记录的源码提交不同，不能作为本次实现证据。 |
| weekly sync | 已按 skill 执行；结果为 skip: last sync 2.66 days ago < 7 days。 |
| 源码工作树 | 已有 difftest 修改和 src/main/resources/aia/ 未跟踪内容；本次仅只读分析，未触碰它们。 |

结论：StoreQueue 不是 store 执行阶段立即写 DCache 的 FIFO。它在 dispatch 时为 store 或 vector-store flow 分配环形 SQ 项；分别收集 STA 地址、STD 数据和 mask；在 load 查询时执行 store-to-load forwarding；只在 ROB 的提交边界后将普通可缓存 store 交给 SBuffer。NC、MMIO、CMO 走专用状态机。SQ 的 completed 表示该项已成功交给 SBuffer 或相应特殊路径，并不表示 DCache 已最终完成 cache-line 写入。

该最后一点必须严格区分：StoreUnit 对 DCache 的 s0 请求是 tag/meta 探测，不是实际写（[StoreUnit.scala:236](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:236)）；真正的 cacheable write 路径是 StoreQueue -> DataBuffer -> Sbuffer -> DCache main pipe。StoreQueue 在 SBuffer fire 时置 completed，而 SBuffer 之后才选择 cache-line、发 M_XWR 并等待 DCache response。

### 1.1 关键代码证据

| 主题 | 有效源码 | 证明内容 |
| --- | --- | --- |
| 顶层装配 | [MemBlock.scala:615](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:615)、[LSQWrapper.scala:142](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:142) | MemBlock 实例化 LsqWrapper/Sbuffer；LsqWrapper 实例化 StoreQueue。 |
| SQ 接口 | [StoreQueue.scala:151](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:151)、[StoreQueue.scala:156](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:156) | 地址、数据、mask、ROB、forward、SBuffer、uncache、CMO 和异常接口。 |
| 参数 | [Parameters.scala:167](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:167)、[Parameters.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214) | SQ=56、write bank=8、StorePipelineWidth=2、LoadPipelineWidth=3、EnsbufferWidth=2。 |
| 分配 | [StoreQueue.scala:360](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:360)、[LSQWrapper.scala:155](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:155) | LQ/SQ 共同接收能力、按 numLsElem 分配 range。 |
| 写入 | [StoreQueue.scala:507](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:507)、[StoreQueue.scala:594](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:594) | 地址和数据独立写入；datavalid 延后一拍。 |
| forwarding | [StoreQueue.scala:650](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:650)、[StoreQueueData.scala:220](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:220) | 环形年龄范围、地址 CAM、逐 byte forward 与 invalid 原因。 |
| 提交/退出 | [StoreQueue.scala:1131](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1131)、[StoreQueue.scala:1330](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1330) | committed 与 completed 是不同状态；SBuffer fire 才完成。 |
| 特殊路径 | [StoreQueue.scala:824](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:824)、[StoreQueue.scala:925](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:925) | MMIO 和 NC 的独立 FSM。 |
| SBuffer/DCache 后段 | [MemBlock.scala:1520](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1520)、[Sbuffer.scala:607](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:607) | SBuffer 再进行 line merge、DCache req 和响应释放。 |
| redirect | [StoreQueue.scala:1482](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1482) | 未提交项取消、enq 指针两拍后的恢复。 |

### 1.2 Design Doc 可追溯性矩阵

| ID | Design Doc 位置 | 断言 | 当前源码关系 | 状态 |
| --- | --- | --- | --- | --- |
| D0 | 本地 Design Doc 缺失 | 无可核验的 StoreQueue 设计断言 | 直接从 Scala/Chisel 追踪有效连接 | 未咨询；不以意图替代实现 |

## 2. 理论到代码映射

| 理论概念 | 当前代码实体 | 具体信号/状态 | 本实现如何专化 |
| --- | --- | --- | --- |
| 投机 store | StoreQueue entry | allocated、addrvalid、datavalid、committed | store 地址/数据可在 commit 前完成，但未提交项可被 redirect 删除。 |
| store-load RAW | PipeLoadForwardQueryIO 与 SQ CAM | forwardMask、forwardData、dataInvalid、addrInvalid | 不只是一个 ready bit；按 byte 转发并区分数据未到、地址未到、VA/PA 不一致。 |
| 精确提交 | cmtPtrExt 与 ROB 边界 | pendingPtr、commitVec、committed | 地址/数据乱序到达；副作用只在连续可提交前缀中进入后续路径。 |
| 有限资源冲突 | SQ/SBuffer/bank/uncache | canAccept、ready、bank、force_write | SQ 余量、阵列写端口和后端 SBuffer 都可能限制吞吐。 |
| 环形队列 | SqPtr | flag、value、distanceBetween | 深度 56 非 2 次幂，回绕时使用 flag 翻转，不能只看 value。 |
| cache-line 合并 | Sbuffer | ptag/vtag、state_valid/state_inflight | SQ 只交付已提交 payload；SBuffer 才负责 line merge、DCache drain。 |

## 3. 有效连接：Who / Why / How / From / To

| 对象 | Who | Why | How | From what | To what |
| --- | --- | --- | --- | --- | --- |
| LsqWrapper.storeQueue | LsqWrapper | 让 LQ/SQ 作为同一个 dispatch 资源边界 | 两侧 canAccept 取 AND，交叉填 lqIdx/sqIdx | enqLsq、dispatch uop | LoadQueue、StoreQueue、上游 dispatch |
| StoreQueue | mem 子系统 | 保存投机 store、向 load 转发、按序释放副作用 | 状态 Vec、地址/数据 array、指针组、MMIO/NC FSM | STA、STD、ROB、redirect、load query | SBuffer、uncache、LoadQueue、异常地址输出 |
| StoreUnit | STA pipeline | 形成 VA/mask、TLB/PMP/PMA/MMIO 分类 | s0 地址；s1 TLB；s2 权限/分类；s3 writeback | issueSta/向量/非对齐输入 | SQ storeAddrIn/storeAddrInRe、DCache probe、misalign buffer |
| SQAddrModule | StoreQueue | 供 data drain 读地址且支持 load 地址 CAM | 注册读、多写端口、line/subline/mask match | STA 地址 | DataBuffer 和 forward 候选 mask |
| SQDataModule | StoreQueue | 保存数据和逐 byte 有效位，支持 byte forwarding | 16 个 SQData8Module、8-bank 写入 | STD data、STA mask | DataBuffer、load forward |
| DatamoduleResultBuffer | StoreQueue | 断开同步读与 SBuffer ready，保持前缀次序 | EnsbufferWidth 槽 FIFO，prefix valid/ready | DataBufferEntry | Sbuffer in |
| StoreExceptionBuffer | StoreQueue | 把多个 store 异常源归并为最老异常地址 | 按 robIdx/uopIdx 递归 selectOldest | STA S1/S2、vector、MMIO | LSQ/ROB exceptionAddr |
| Sbuffer | MemBlock | 合并已提交 store 并产生真实 DCache 写 | ptag merge/allocate，s0/s1/response drain | SQ Decoupled output | DCache lsu.store.req |

### 3.1 实际模块接口图

~~~mermaid
flowchart LR
  Dispatch[Dispatch / LsqEnqCtrl] -->|DynInst, needAlloc, sqIdx| SQ[StoreQueue]
  STA[StoreUnit] -->|storeMaskIn, storeAddrIn, storeAddrInRe| SQ
  STD[STD data path] -->|storeDataIn| SQ
  ROB[RobLsqIO] -->|pendingPtr, scommit, commit| SQ
  Redirect[brqRedirect] -->|needFlush| SQ
  LoadPipe[LoadUnit x3] -->|PipeLoadForwardQueryIO| SQ
  SQ -->|forward data/mask/invalid| LoadPipe
  SQ -->|DataBufferEntry Decoupled x2| SBuffer[Sbuffer]
  SQ -->|uncache req/resp| UArb[LSQ uncache arbiter]
  SBuffer -->|M_XWR req/response| DCache[DCache MainPipe]
~~~

有效接线见 [LSQWrapper.scala:186](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:186)-[LSQWrapper.scala:209](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:186) 和 [MemBlock.scala:1520](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1520)-[MemBlock.scala:1529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1520)。

### 3.2 Store 数据/控制阶段图

~~~mermaid
flowchart LR
  A[Dispatch alloc SQ] --> B[STD data write]
  A --> C[STA s0 VA and mask]
  C --> D[STA s1 TLB response]
  D --> E[SQ addrvalid and address array]
  D --> F[STA s2 PMP/PMA/MMIO]
  F --> G[SQ replenish and waitStoreS2 clear]
  B --> H[datavalid]
  E --> I[allvalid]
  G --> J[ROB commit prefix]
  H --> I
  I --> J
  J --> K[DataBuffer]
  K --> L[Sbuffer fire]
  L --> M[SQ completed and deq]
  L --> N[Sbuffer merge and DCache write]
  J --> O[NC/MMIO/CMO FSM]
~~~

## 4. 参数、容量与索引

### 4.1 结构参数

| 参数 | 当前默认值/表达式 | 影响 | 证据 |
| --- | --- | --- | --- |
| StoreQueueSize | 56 | entry Vec、CAM 宽度、SqPtr 范围 | [Parameters.scala:174](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:174) |
| StoreQueueNWriteBanks | 8 | 每个 byte lane 的 data/mask write bank 数 | [Parameters.scala:175](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:175) |
| StoreQueueForwardWithMask | true | 地址命中是否还要求 byte mask 相交 | [Parameters.scala:176](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:176) |
| StorePipelineWidth | 2 | SQ 的 STA addr 和 STD data 写端口数 | [Parameters.scala:215](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:215) |
| LoadPipelineWidth | 3 | forwarding query 端口数 | [Parameters.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214) |
| EnsbufferWidth | 2 | SQ 到 DataBuffer/SBuffer 的连续交付宽度 | [Parameters.scala:226](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:226) |
| StoreBufferSize | 16 | 后段 line-oriented SBuffer 项数，不是 SQ 深度 | [Parameters.scala:224](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:224) |
| LSQStEnqWidth | LSQEnqWidth min backendParams.numStoreDp | SQ 可接收时保留的最大 store dispatch 空间 | [Parameters.scala:783](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:783)-[Parameters.scala:785](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:783) |

### 4.2 环形指针

SqPtr 是 flag 加 value 的 CircularQueuePtr。该队列深度为 56，非 2 次幂，因此 ptr 加法在 sum 超过 56 时将 value 减 56 并翻转 flag；distanceBetween 根据两指针 flag 是否相同选择普通差或 56 加差。

~~~scala
val new_value = this.value +& v
val diff = Cat(0.U(1.W), new_value).asSInt -
  Cat(0.U(1.W), entries.U.asTypeOf(new_value)).asSInt
new_ptr.flag := Mux(diff >= 0.S, !this.flag, this.flag)
new_ptr.value := Mux(diff >= 0.S, diff.asUInt, new_value)
~~~

证据：[CircularQueuePtr.scala:35](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/CircularQueuePtr.scala:35)-[CircularQueuePtr.scala:58](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/CircularQueuePtr.scala:35)，[CircularQueuePtr.scala:102](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/CircularQueuePtr.scala:102)-[CircularQueuePtr.scala:107](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/CircularQueuePtr.scala:102)。

| 指针 | reset | 推进条件 | 作用 |
| --- | --- | --- | --- |
| enqPtrExt | 顺序 0 | 有效 store flow 数；redirect 两拍后减取消数 | 分配尾、返回 sqIdx、occupancy |
| rdataPtrExt | 0 | DataBuffer 接收、NC/特殊路径完成 | 同步读 SQ array 的预读指针 |
| deqPtrExt | 0 | 连续 allocated and completed 前缀 | 真实回收入口和 SQ 空判定 |
| cmtPtrExt | 0 | commitCount | 扫描/标记连续 ROB 可提交 store |
| addrReadyPtrExt | 0 | 最多扫描四项，遇未准备项停止 | 给 LoadQueue 观察 store 地址 ready |
| dataReadyPtrExt | 0 | 类似地址指针但需数据 ready 且非 unaligned | 给 LoadQueue 观察 store 数据 ready |

证据：[StoreQueue.scala:278](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:278)-[StoreQueue.scala:348](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:278)，[StoreQueue.scala:429](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:429)-[StoreQueue.scala:489](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:429)。

### 4.3 分配与 full

~~~scala
val validCount = distanceBetween(enqPtrExt(0), deqPtrExt(0))
val allowEnqueue = validCount <= (StoreQueueSize - LSQStEnqWidth).U
io.enq.canAccept := allowEnqueue
~~~

这意味着可以接收的条件是保留一个最大 store dispatch group 的空间，并非等到正好占用 56 项才阻塞。LsqWrapper 又用 LQ.canAccept AND SQ.canAccept 形成对 dispatch 的总 backpressure，避免同一条 memory uop 出现 LQ/SQ 半分配。

分配 index 来自 dispatch 给定的 sqIdx 加 numLsElem 闭开区间。跨回绕时 entry 命中条件是 i 大于等于 low 或小于 up；不跨回绕时是 low 小于等于 i 且 i 小于 up。每次分配会清旧 entry 的 completed、addrvalid、datavalid、committed、MMIO/NC/exception 等状态，并令 waitStoreS2=true，等待 STA s2 的最终分类。

证据：[StoreQueue.scala:365](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:365)-[StoreQueue.scala:419](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:365)。

### 4.4 地址、bank 与 CAM index

| index/address | 计算 | 消费者 |
| --- | --- | --- |
| 标量 VA | src(0) + SignExt(imm[11:0]) | DTLB、DCache s0 probe、SQ address payload |
| byte-array bank | waddr 的低 log2(StoreQueueNWriteBanks) 位 | SQData8Module write bank |
| bank local index | waddr 右移 bank bit 数 | bank 内 7 个位置（当前 56/8） |
| forward line match | address 高位到 DCacheLineOffset 相同 | SQAddrModule CAM |
| subline/byte match | DCacheLineOffset 到 DCacheVWordOffset 相同，且 mask 相交 | forward 能否覆盖该 byte |
| forward age mask | 预解码 sqIdxMask 与 deq flag/deqMask 切为两段 | 只查询仍在 SQ 的更老项 |
| cross16B fragment | base 对齐到 8B，high=low+8；data/mask 左移切 low/high | 两项 DataBuffer payload |

VA/mask 证据：[StoreUnit.scala:141](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:141)-[StoreUnit.scala:255](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:141)。bank/CAM 证据：[StoreQueueData.scala:132](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:132)-[StoreQueueData.scala:168](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:132)，[StoreQueueData.scala:74](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:74)-[StoreQueueData.scala:83](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:74)。

## 5. 存储结构与状态生命周期

### 5.1 每项隐式 FSM

~~~mermaid
stateDiagram-v2
  [*] --> Free
  Free --> Allocated: dispatch range hits
  Allocated --> AddrReady: storeAddrIn updateAddrValid
  Allocated --> DataReady: storeDataIn delayed valid
  AddrReady --> Ready: data valid
  DataReady --> Ready: addr valid
  Ready --> Committed: ROB prefix authorization
  Committed --> ToSbuffer: cached DataBuffer and SBuffer fire
  Committed --> NCWait: NC request/ack/response
  Committed --> MMIOWait: pending reaches ROB head
  ToSbuffer --> Completed
  NCWait --> Completed: ncDeqTrigger
  MMIOWait --> Completed: mmioStout fire
  Completed --> Free: contiguous deq prefix
  Allocated --> Free: redirect before commit
  AddrReady --> Free: redirect before commit
  DataReady --> Free: redirect before commit
~~~

这是 status bit 形成的隐式 FSM；代码没有为普通 cached store 另设 Enum。最关键的不变量是 committed 与 completed 分离。

| 状态/结构 | reset | set/update | hold | clear/release | 使用者 |
| --- | --- | --- | --- | --- | --- |
| allocated | false | dispatch range 命中 | 直到 deq 或 redirect | 连续 completed 前缀，或未提交 redirect | capacity、forward、ready vec |
| addrvalid | false | STA s1 updateAddrValid，S2 exception 可补置 | 直到重分配 | 重分配初始化；allocated 无效后不可解释 | allvalid、forward、SBuffer admission |
| datavalid | false | STD fire 的下一拍，且 entry 仍 allocated | 直到重分配 | 重分配初始化 | allvalid、forward |
| committed | false | cmt pointer 连续前缀获 ROB 边界授权 | 一旦 true 不被普通 redirect 取消 | 重分配初始化 | SBuffer/NC/MMIO admission |
| completed | false | SBuffer fire，NC response，或 MMIO/CMO writeback | 等较老项完成 | deq 连续前缀清除 | deqPtr 和 free capacity |
| pending/mmio/nc | false | StoreUnit s2 replenish 分类 | 特殊 FSM 运行期间 | mmio request 发出清 pending；重分配清 | MMIO/NC 流程 |
| hasException | false | StoreUnit s2 exception | 允许异常路径完成但抑制正常可见 store data | 重分配清 | exception buffer、DataBuffer vecValid |

字段定义证据：[StoreQueue.scala:255](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:255)-[StoreQueue.scala:276](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:255)。分配初始化证据：[StoreQueue.scala:377](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:377)-[StoreQueue.scala:410](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:377)。

### 5.2 地址阵列

SQ 有一个 PA 和一个 VA 的 SQAddrModule。读端口通过寄存后的 raddr 访问数组；写端口依次写 data/mask/lineflag；CAM 用 cache-line 位、subline 位和可选 mask overlap 给每个 entry 产生 forwardMmask。

~~~scala
for (i <- 0 until numRead) {
  io.rdata(i) := data(GatedRegNext(io.raddr(i)))
}
for (i <- 0 until numWrite) {
  when (io.wen(i)) {
    data(io.waddr(i)) := io.wdata(i)
  }
}
assert(!(io.wen(i) && io.wen(j) && io.waddr(i) === io.waddr(j)))
~~~

证据：[StoreQueueData.scala:59](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:59)-[StoreQueueData.scala:90](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:59)。

可确认的冲突规则是：同拍两个地址写端口写同 index 会 assertion；不同 index 可并发写。源码没有显式同 index read/write bypass mux，因此 read-old/read-new 的精确边沿语义需要 generated RTL 或波形证明，不能凭寄存器数组名称假设。

### 5.3 数据与 mask 阵列

SQDataModule 固定例化 16 个独立 SQData8Module，每个 entry 保存一个 byte 的 valid 和 data。数据与 mask 是两套独立 two-stage write 路：data write 只更新 data；mask write 才更新对应 byte valid。因而 datavalid 仅证明 STD 路到达，不可被解释为每个 byte mask 都一定为 1。

~~~scala
val s0_wenVec = Wire(Vec(StoreQueueNWriteBanks, Bool()))
s0_wenVec(bank) := io.data.wen(i) &&
  get_bank(io.data.waddr(i)) === bank.U
val s1_wenVec = GatedValidRegNext(s0_wenVec)
when(s1_wen && s1_waddr === index.U) {
  data(get_vec_index(index, bank)).data := s1_wdata
}
~~~

证据：[StoreQueueData.scala:130](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:130)-[StoreQueueData.scala:200](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:130)，[StoreQueueData.scala:305](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:305)-[StoreQueueData.scala:348](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:305)。

| 冲突 | 当前代码行为 |
| --- | --- |
| 两个 data port 写同 SQ index | assertion，非法输入。 |
| 两个 mask port 写同 SQ index | assertion，非法输入。 |
| 两个 address port 写同 SQ index | assertion，非法输入。 |
| 同 index 的 data 写和 mask 写 | 允许；它们写不同字段。 |
| 不同 index 映射到同一 bank | 没有仲裁/ready 回压代码；每写端口有独立 s0/s1 逻辑。 |
| read/write 同 index | 无显式 bypass；必须波形/RTL 继续确认。 |

assertion 证据：[StoreQueueData.scala:208](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:208)-[StoreQueueData.scala:218](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:208)。

### 5.4 DataBuffer

DatamoduleResultBuffer 是 EnsbufferWidth=2 槽的中间 FIFO。它的 lane1 valid/ready 依赖 lane0；deq 和 enq 均只能为连续前缀。entry_allowin 允许空槽或同拍 dequeue 的槽重用。

~~~scala
io.deq(index).valid := valids(deq_flag + index.U) &&
  (if (index == 0) 1.B else io.deq(index - 1).valid)
io.enq(index).ready := entry_allowin(enq_flag + index.U) &&
  (if (index == 0) 1.B else io.enq(index - 1).ready)
~~~

证据：[DatamoduleResultBuffer.scala:50](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/DatamoduleResultBuffer.scala:50)-[DatamoduleResultBuffer.scala:93](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/DatamoduleResultBuffer.scala:50)。

## 6. 核心控制/数据算法

### 6.1 地址、数据、mask 独立写入

STA 在 s0 形成 VA/mask，并向 TLB 发 write 类请求；s1 成功时送 StoreQueue.storeAddrIn；s2 经 PMP/PMA/PBMT/MMIO 判定后用 StoreQueue.storeAddrInRe 回填最终分类/异常。STD 则独立对 StoreQueue.storeDataIn 发有效数据；STA s0 的 StoreMaskBundle 又是第三条 mask 路。

~~~scala
when (io.storeAddrIn(i).fire &&
      io.storeAddrIn(i).bits.updateAddrValid &&
      !io.storeAddrIn(i).bits.miss) {
  addrvalid(stWbIndex) := true.B
}
...
when (RegNext(io.storeDataIn(i).fire) && allocated(lastStWbIndex)) {
  datavalid(lastStWbIndex) := true.B
}
~~~

证据：[StoreUnit.scala:215](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215)-[StoreUnit.scala:279](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215)，[StoreUnit.scala:412](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:412)-[StoreUnit.scala:547](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:412)，[StoreQueue.scala:507](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:507)-[StoreQueue.scala:643](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:507)。

这解释了为什么 allocated 不等于“可 forward”或“可提交”：allvalid 只是 addrvalid AND datavalid；实际访问它的路径还同时检查 allocated、unaligned、MMIO/NC 和异常条件。

### 6.2 Store-to-load forwarding

每条 load pipeline 带来 VA、PA、mask、uop、sqIdx 和提前计算的 sqIdxMask。SQ 先按 deq flag 判断是否绕回，再切出 forwardMask1/forwardMask2 两个环段；候选要同时为 allocated、addrvalid、datavalid，才交给 VA CAM 和数据 byte CAM。

~~~scala
val canForward1 = forwardMask1 & allValidVec.asUInt
val canForward2 = forwardMask2 & allValidVec.asUInt
dataModule.io.needForward(i)(0) :=
  canForward1 & vaddrModule.io.forwardMmask(i).asUInt
dataModule.io.needForward(i)(1) :=
  canForward2 & vaddrModule.io.forwardMmask(i).asUInt
~~~

快速 mask 在 load_s1 生成，数据和普通 forwardMask 在下一拍送 load_s2。地址命中但 data 尚未 valid 或存在更老 unaligned 项会产生 dataInvalidFast/dataInvalid；StoreSet/LFST 相关且地址尚未就绪会产生 addrInvalid；PA/VA CAM 掩码不一致会产生 matchInvalid。

证据：[StoreQueue.scala:659](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:659)-[StoreQueue.scala:821](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:659)，接口定义：[Bundles.scala:185](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:185)-[Bundles.scala:230](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:185)。

byte CAM 使用 tree reduction；两个候选同时有效时右边获胜：

~~~scala
res.validFast := l.validFast || r.validFast
res.valid := l.valid || r.valid
res.data := Mux(r.valid, r.data, l.data)
~~~

候选序列先放环段0，再放环段1；同段内 index 更大的候选覆盖更小 index，环绕时第二段又覆盖第一段。验证不应只以物理 index 判断年龄，而应检查最终每个 byte 来自程序序最近的更老匹配 store。证据：[StoreQueueData.scala:228](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:228)-[StoreQueueData.scala:269](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:228)。

### 6.3 ROB commit、SBuffer 和真正回收

SQ commit 扫描最多 CommitWidth 项。每项要 allocated、ROB age 不晚于 GatedRegNext(pendingPtr)、未被 needCancel、且 waitStoreS2 已结束；vector 还需 vecMbCommit。commitVec 形成连续前缀，cmtPtrExt 加 PopCount(commitVec)。

~~~scala
when (allocated(ptr) &&
      isNotAfter(uop(ptr).robIdx, GatedRegNext(io.rob.pendingPtr)) &&
      !needCancel(ptr) && (!waitStoreS2(ptr) || isVec(ptr))) {
  committed(ptr) := true.B
  commitVec(0) := true.B
}
commitCount := PopCount(commitVec)
cmtPtrExt := cmtPtrExt.map(_ + commitCount)
~~~

证据：[StoreQueue.scala:1137](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1137)-[StoreQueue.scala:1173](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1137)。

普通 cacheable store 只有 committed 且地址/数据条件满足后才进入 DataBuffer。DataBuffer 再与 SBuffer 作真实 Decoupled 握手；只有 SBuffer fire 且 sqNeedDeq 时才置 completed。deq 只释放连续 allocated AND completed 前缀，不能让后项越过未完成老项释放。

~~~scala
when (io.sbuffer(i).fire && io.sbuffer(i).bits.sqNeedDeq &&
      !io.sbuffer(i).bits.wline) {
  completed(ptr) := true.B
}
when (readyDeqVec.take(i + 1).reduce(_ && _)) {
  allocated(ptr) := false.B
  completed(ptr) := false.B
}
~~~

证据：[StoreQueue.scala:1219](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1219)-[StoreQueue.scala:1349](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1219)，[StoreQueue.scala:334](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:334)-[StoreQueue.scala:348](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:334)。

SBuffer 接到 SQ payload 后，按 ptag 查 active line 以 merge；否则分配 invalid line。SBuffer s0 挑 active candidate，s1 才向 DCache 发送 M_XWR；DCache hit response fire 才把该 SBuffer line 清为 invalid。这是 completed 不等于 DCache completion 的直接证据。

证据：[Sbuffer.scala:320](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:320)-[Sbuffer.scala:390](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:320)，[Sbuffer.scala:607](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:607)-[Sbuffer.scala:722](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:607)。

### 6.4 MMIO、NC、CMO

| FSM | 状态 | 入口/动作/出口 |
| --- | --- | --- |
| MMIO/CMO | s_idle | 等候 pending store 成为 ROB head，地址和数据有效、无异常；捕获 uop 后进入 s_req。 |
| MMIO/CMO | s_req | 产生非 NC uncache req；CBO 可先请求 flush SBuffer，再走 cmoOpReq。req.fire 后进入 s_resp。 |
| MMIO/CMO | s_resp | 等 uncache/CMO response；denied 映射 storeAccessFault，corrupt 且非 denied 映射 hardwareError；进入 s_wb。 |
| MMIO/CMO | s_wb/s_wait | mmioStout fire 后，有异常则 idle；正常则等 scommit 再 idle。 |
| NC | nc_idle | 仅限 committed、allvalid、非 vector、非异常、非 MMIO/CMO 的 NC entry。 |
| NC | nc_req/nc_req_ack/nc_resp | 先发 req，再等待 UBuffer ack，最后在 response fire 后回 idle；对应 completed 在 ncDeqTrigger 置位。 |

MMIO 与 NC 同时请求时，SQ 内部让 MMIO 优先：ncReq.ready 等于 uncache.req.ready AND not mmioReq.valid。LoadQueue 与 StoreQueue 同时申请 uncache 时，LsqWrapper 选择更小 robIdx；相同 robIdx 的平局会落到 StoreQueue，因为选择 LoadQueue 的条件使用严格小于。

证据：[StoreQueue.scala:836](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:836)-[StoreQueue.scala:987](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:836)，[LSQWrapper.scala:265](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:265)-[LSQWrapper.scala:321](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LSQWrapper.scala:265)。

## 7. 异常、redirect 与特例

| 类别 | producer | SQ 处理 | 架构/下游效果 |
| --- | --- | --- | --- |
| 地址未对齐 | StoreUnit s0/s1/s2 | unaligned/cross16Byte 或 hasException；必要时交 StoreMisalignBuffer | 分片或异常，不应成为普通可见写。 |
| TLB PF/AF/GPF | StoreUnit s1 | uop exception 随 LsPipelineBundle 到 SQ；StoreExceptionBuffer 保留最老地址元数据 | 上层异常地址/ROB 路径使用 vaddr/gpaddr/vstart/vl。 |
| PMP/PMA/PBMT MMIO/NC | StoreUnit s2 | 写 mmio/nc/memBackTypeMM/hasException，清 waitStoreS2 | 取消普通 DCache write intent，转特殊 FSM。 |
| uncache denied/corrupt | uncache/CMO response | 写 uncacheUop.exceptionVec | denied -> storeAccessFault；corrupt without denied -> hardwareError。 |
| trigger/debug | StoreUnit trigger | trigger/exception 元数据随 uop 传递 | SQ 携带，不自己裁决 trap priority。 |
| redirect | branch/exception redirect | 只取消 allocated AND not committed AND needFlush 项 | 清 allocated/completed，之后恢复 enqPtr。 |

StoreExceptionBuffer 接收 STA s1、STA s2、vector 反馈和 MMIO error；候选比较先按 robIdx，再按 uopIdx 选择最老项。reset 时 req_valid=false；redirect 会滤掉需要 flush 的候选。证据：[StoreQueue.scala:73](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:73)-[StoreQueue.scala:144](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:73)。

redirect 取消算法的 entry 条件是 allocated AND not committed；vector exception flag 存在时使用显式 isAfter 分支，否则使用 uop.robIdx.needFlush。取消计数包含在飞 enqueue 和 entry cancellation；enqPtr 在 redirect 后两拍相减，并禁止不安全的新 enqueue 窗口。

证据：[StoreQueue.scala:1483](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1483)-[StoreQueue.scala:1525](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1483)。

限制：vector MMIO 当前未实现完整通路，代码将 vecmmioStout.valid 固定为 false。因此不能将标量 MMIO 状态机的行为外推为完整向量 MMIO 支持。证据：[StoreQueue.scala:1104](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1104)-[StoreQueue.scala:1119](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1104)。

## 8. 访存类别、延迟与吞吐

| 类别 | 到 SQ 的有效路径 | 特例 |
| --- | --- | --- |
| 标量 store | STA 地址 + STD 数据 + STA mask -> SQ -> commit -> SBuffer | decode opcode/FP 格式不由 SQ 重新解释。 |
| vector store | 多 flow 分配，vecMbCommit/last-flow/exception flag | 一个 architectural 指令可占多个 SQ flow。 |
| CBO/CMO | 地址 classification -> CMO/flush SBuffer 或 zero path | 真正 cache CMO 行为在 DCache 模块。 |
| NC | commit 后 -> NC FSM -> uncache ack/resp | 在 response 前保留 SQ 的 forwarding 能力。 |
| MMIO | pending 且 ROB head -> MMIO FSM -> response/writeback -> commit | 禁止把它当普通 SBuffer store。 |

| 路径 | 起点 -> 终点 | 可证明时序 | 主要变量/瓶颈 |
| --- | --- | --- | --- |
| STD -> datavalid | storeDataIn.fire -> status bit | RegNext(fire) 后才置 datavalid；data array 也含 s0/s1 写 | STD issue、bank/端口约束、redirect |
| STA -> addrvalid | storeAddrIn.fire -> status bit | S1 fire 同拍写 addrvalid/addr array；S2 最终分类另到 | TLB miss、s1/s2 ready、PMP/PMA、redirect |
| SQ -> SBuffer | committed and allvalid -> sbuffer.fire | DataBuffer 同步读和 Decoupled fire 是必经边界 | SBuffer ready、前项 NC/MMIO、非对齐双项 |
| SBuffer -> DCache | active line -> DCache req.fire -> hit response | SBuffer s0/s1/response 三段 | DCache ready、同 block inflight、merge、replace、probe/refill |
| MMIO/NC | eligibility -> req/resp/writeback | 有明确 FSM、无固定 response 周期 | uncache arbitration/outstanding、error、ROB commit |

默认 STA/STD/SQ 输入宽度和 SQ->SBuffer 宽度均可为 2，但它们是结构上限，不是保证的最终 memory throughput。SBuffer 只暴露一个 DCache req，且 NumDcacheWriteResp=1；因此不能由双入口推导每拍两条最终 DCache cache-line 写。证据：[Sbuffer.scala:45](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:45)-[Sbuffer.scala:48](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:45)，[Sbuffer.scala:661](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:661)-[Sbuffer.scala:704](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:661)。

## 9. 跨边界代码解析

| 边界 | 第一/第二 fragment | 独立检查/资源 | 合并与顺序 | 失败/恢复 |
| --- | --- | --- | --- | --- |
| 虚拟页边界 | StoreMisalignBuffer 用 crossPageWithHit、crossPageCanDeq、paddr 回报 SQ | STA 的 TLB/PMP/PMA；SQ 提供 sqPtr/doDeq/uop 回给 misalign buffer | 高页物理地址被送入对应 DataBuffer 片段 | SQ 本身没有泛化双页原子 FSM；二次翻译/异常细节应继续核对 StoreMisalignBuffer。 |
| 16B 子行跨界 | data/mask 左移，切 low/high；地址 high=low+8 | 两个 DataBuffer entry 必须 ready | low 的 sqNeedDeq=false，high 才 true，故一个 SQ entry 只完成一次 | 任一片被阻塞不能错误回收 SQ。 |
| cache-line 边界 | SQ 交普通 payload 给 SBuffer；SBuffer 以 ptag/vtag merge/allocate | SBuffer active/inflight state、DCache req | SBuffer 聚合成 line data/mask 后发 M_XWR | SQ 未出现一般 CacheLineSize split 算法，不能声称单 SQ payload 自动原子跨两 line。 |
| MMIO/uncache | StoreUnit s2 分类后的 uncache request | translation/PMA/PMP、commit/pending、uncache ready/resp | MMIO 在 ROB head，NC 在 commit 后发；都不进普通 SBuffer | request 发出前 redirect 可取消未提交项；发出后由 FSM/ROB writeback 处理。 |

跨页控制接口：[Bundles.scala:277](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:277)-[Bundles.scala:295](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/Bundles.scala:277)。SQ 分片逻辑：[StoreQueue.scala:1193](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1193)-[StoreQueue.scala:1297](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1193)。SBuffer line merge：[Sbuffer.scala:320](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:320)-[Sbuffer.scala:383](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:320)。

组合例：高页 fragment 还没有 crossPageCanDeq 时，不应因低页地址已知就视为副作用完成；如果 redirect 在该 store 仍未 committed 时到来，needCancel 清 allocated，且 enq 指针按取消数恢复。因此跨界的低片可见地址、SBuffer fire、DCache 最终写是三个必须分开观察的事件。

## 10. Difftest：架构边界

| 信号/状态 | 类别 | 产生时机 | 正确解读 |
| --- | --- | --- | --- |
| allocated/addrvalid/datavalid/committed/completed | 微结构状态 | StoreQueue 寄存器 | 不属于 RISC-V architectural state。 |
| diffStore.pmaStore | reference-model event 输入 | SQ 的 DataBuffer enq.fire | 到 SBuffer 的 PMA store payload。 |
| diffStore.ncStore | reference-model event 输入 | NC 或 MMIO request fire 且 memBackTypeMM | 普通 SBuffer 以外的 store 事件来源。 |
| DiffStoreEvent | difftest event | SBuffer 以 pmaStore.fire、mask、vecValid gate | store 地址/data/mask/ROB/PC 记录，不等于 SQ entry valid。 |
| DiffSbufferEvent | cache debug/reference event | SBuffer 的 DCache hit response fire | 比 SQ completed 更晚的 line-level DCache 结果。 |

SQ 生成 difftest 输入：[StoreQueue.scala:1408](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1408)-[StoreQueue.scala:1433](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1408)。MemBlock 将它接至 SBuffer：[MemBlock.scala:1538](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1538)-[MemBlock.scala:1555](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1538)。SBuffer event enable：[Sbuffer.scala:943](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:943)-[Sbuffer.scala:999](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:943)。

## 11. 动态流程示例

### 11.1 正常 cacheable 标量 store，同时给更年轻 load 转发

1. Dispatch 在 SQ 有余量时分配一个 sqIdx，相关 entry 变 allocated，旧的 addr/data/commit 状态清零。
2. STD 可先 fire；数据进入 byte data array，下一拍 datavalid=true。STA 后到时在 s0 形成 VA/mask，s1 经过 TLB 后以同 sqIdx 写 PA/VA 并置 addrvalid。
3. 更年轻 load 的 sqIdxMask 覆盖这个更老项，VA CAM 和 byte mask 命中；fast mask 在 load_s1 返回，data 在 load_s2 返回。
4. ROB 边界走到该 store 时，SQ 将 committed 置位。普通路径随后把同步读出的 payload 放到 DataBuffer。
5. SBuffer ready 后，io.sbuffer.fire 使 SQ completed=true。deqPtr 仅在它前面的项也 completed 时回收该 entry；其后的 SBuffer 仍可能在之后 merge、等待 DCache 并写入。

### 11.2 地址未到导致 load replay

1. 更老 store 已 allocated，StoreSet/LFST 表示它可能与 load 相关，但 STA 尚未写 addrvalid。
2. SQ 的 addrInvalidMask 在两个环段中寻找此类项，给出 addrInvalid 和地址对应 SqPtr；loadWaitStrict 时直接指向 load 前一个 SQ 项，代表等待所有更老 store 的保守条件。
3. LoadUnit/RS replay load；SQ 项保持不变，地址到达后仍可实现正常 forwarding。这里的 replay 不会把更老 store 删除。

## 12. 波形：分配、交付和反压

io.enq.req 是 ValidIO 而不是 Decoupled；图中的 alloc.accept 是本文定义的 req.valid AND canAccept。SQ 到 SBuffer 才是实际 Decoupled，io.sbuffer[0].fire 等于 valid AND ready。SBuffer ready 为低时，payload/valid 保持，completed 不能置位。

~~~waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p........." },
    { "name": "io.enq.req[0].valid", "wave": "01........" },
    { "name": "io.enq.canAccept", "wave": "01........" },
    { "name": "alloc.accept", "wave": "0.1......." },
    { "name": "addrvalid[sqIdx]", "wave": "0..1......" },
    { "name": "datavalid[sqIdx]", "wave": "0...1....." },
    { "name": "committed[sqIdx]", "wave": "0.....1..." },
    { "name": "io.sbuffer[0].valid", "wave": "0......1.." },
    { "name": "io.sbuffer[0].ready", "wave": "0......01." },
    { "name": "io.sbuffer[0].fire", "wave": "0.......1." },
    { "name": "completed[sqIdx]", "wave": "0........1" },
    { "name": "payload", "wave": "x......=..", "data": ["DataBufferEntry"] }
  ],
  "config": { "hscale": 1 }
}
~~~

这是条件关系图，不代表所有路径固定 9 个周期；TLB、S1/S2 ready、ROB、DataBuffer、SBuffer 和 uncache 都可插入停顿。请在 VS Code 中以 Markdown Preview 查看 waveform-draw 图，源编辑器显示 JSON 是预期行为。

## 13. 验证特别注意

| Verification ID | 风险/不变量 | 定向 stimulus | 期望观察 | checker/coverage 与代码证据 |
| --- | --- | --- | --- | --- |
| F_RESET_IDLE | 所有 SQ 状态和指针从空开始 | reset 后第一条合法 store | 无陈旧 addr/data；仅目标范围 allocated | occupancy + pointer checker；[StoreQueue.scala:255](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:255) |
| F_FIRST_REQUEST | 地址/数据分离不能使用旧阵列值 | STD 和 STA 先后反转 | allvalid 前不能按普通 cached 路交付 | storage lifecycle scoreboard；[StoreQueue.scala:507](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:507)-[StoreQueue.scala:630](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:507) |
| RESOURCE_CONTENTION | full 门槛要保留最大 dispatch group | 填到 Size-LSQStEnqWidth 前后，插 vector multi-flow | canAccept/sqFull 精确翻转，回收后恢复 | occupancy checker；[StoreQueue.scala:291](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:291)-[StoreQueue.scala:292](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:291) |
| I_WRAP_PTR | 非 2 次幂 56 的回绕年龄正确 | value=55 后多分配/回收 | flag 翻转，distance/forward age 正确 | pointer-age checker；[CircularQueuePtr.scala:35](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/CircularQueuePtr.scala:35) |
| C_MULTI_WRITE_SAME_ENTRY | 无声双写会损坏 address/data/mask | 两 lane 同时给同 waddr | 对应 assertion 必须触发 | storage conflict checker；[StoreQueueData.scala:85](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:85)-[StoreQueueData.scala:90](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:85) |
| C_SAME_ENTRY_RW | 同址读写语义不应被错误假定 | SBuffer/read 或 load CAM 同拍覆盖同 index | 对照 elaborated RTL/FST，确认 read-old/read-new | waveform + storage checker；[StoreQueueData.scala:59](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:59)-[StoreQueueData.scala:72](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueueData.scala:59) |
| F_HOLD_BACKPRESSURE | SBuffer stall 不能 double-complete 或篡改 payload | ready=0 多拍后再恢复 | valid/bits 保持；只在 fire 拍置 completed | handshake checker；[DatamoduleResultBuffer.scala:57](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/DatamoduleResultBuffer.scala:57)-[DatamoduleResultBuffer.scala:93](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/DatamoduleResultBuffer.scala:57) |
| F_REQ_AND_FLUSH | redirect 不可取消 committed 项，也不可留下未提交项 | dispatch/STA/STD 与 redirect 重叠 | 未提交项取消，已提交项保留，enqPtr 恢复 | flush + pointer checker；[StoreQueue.scala:1483](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1483)-[StoreQueue.scala:1522](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1483) |
| P_DEADLOCK_ALL_STALL | SBuffer/uncache 都停顿不能错误释放 | 低 ready、无 uncache resp，随后放开 | completed/deq 只按合法路径推进，最终恢复 canAccept | forward-progress checker；[StoreQueue.scala:323](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:323)-[StoreQueue.scala:348](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:323) |
| P_LIVELOCK_REPLAY_LOOP | repeated data/addr invalid 不可损坏 SQ | 同 load 重复制造 data-late/VA-PA mismatch | SQ 项保持，数据最终唯一正确 forward | forward scoreboard + replay coverage；[StoreQueue.scala:694](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:694)-[StoreQueue.scala:821](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:694) |
| E_MMIO_ORDER | MMIO/NC 不可越序且 error mapping 正确 | 同时构造 MMIO、NC、cached store，注入 denied/corrupt | MMIO FSM、NC FSM 和 age arbiter 规则正确 | FSM + exception scoreboard；[StoreQueue.scala:845](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:845)-[StoreQueue.scala:985](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:845) |
| B_CROSS_PAGE | 高页 fragment 或 redirect 不能泄露 side effect | crossPageWithHit，延迟 crossPageCanDeq，并在途中 redirect | doDeq 只在 permit+fire；未提交项被取消 | boundary + flush checker；[StoreQueue.scala:1193](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1193)-[StoreQueue.scala:1217](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1193) |

## 14. 确认结论与待补证据

已确认：

- SQ 默认深度 56；地址、数据、mask 可独立到达。
- committed、completed、SBuffer line 完成和 DCache response 是四个不同事件。
- load forwarding 有按 byte 数据结果，并显式报告 data/addr/match invalid。
- redirect 只删除未提交 SQ 项，并延迟修正 enq 指针。
- NC/MMIO/CMO 有专用路径；vector MMIO 不是当前已完整实现的功能。

还需 generated RTL、FST 或更下游模块才能确认：

1. 地址/数据阵列同 index read/write 的准确 read-old/read-new 语义，因代码没有显式 bypass。
2. DCache miss/refill/coherence 下的最终 memory 可见周期；本章只证明到 SQ/SBuffer/DCache request-response 边界。
3. StoreMisalignBuffer 的第二页翻译、异常优先级与完整 split completion。
4. 具体配置下的固定周期或最终 IPC；受 issue、TLB、SBuffer merge、DCache、uncache、ROB、redirect 共同影响。

所有有效实现结论均针对 kunminghu-v2 @ e12436c7cba86b195deec24981976d78bc263661。 
