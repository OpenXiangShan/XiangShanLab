# Cache-MainPipe：Kunminghu V2 CoupledL2 主流水线源码分析

> 结论先行：本文的 <code>MainPipe</code> 是 Kunminghu V2 默认 CHI 配置下每个 CoupledL2 Slice 的 <code>coupledL2/tl2chi/MainPipe.scala</code>，不是 L1 DCache 的同名模块，也不是 HuanCun 的模块。它接收 RequestArb 已准入的 S2 <code>TaskBundle</code>，在 S3 结合 Directory 结果分类 A/B/C/MSHR 任务，决定目录和 DataStorage 的读写、是否创建 MSHR、以及向 TileLink D 或 CHI TXREQ/TXRSP/TXDAT 的响应；S4/S5 主要承担长组合路径切分和 DataStorage MCP2 数据返回。MainPipe 本身没有入口 <code>ready</code>，真正的准入背压在 RequestArb、RequestBuffer、MSHRCtl、GrantBuffer 与 TX 队列的共同控制中完成。[MainPipe.scala:40-123](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:40) [RequestArb.scala:132-217](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:132) [Slice.scala:84-143](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:84)

## 1. 范围、版本与有效实现

### 1.1 分析基线

| 项目 | 本文采用的基线 | 处理方式 |
| --- | --- | --- |
| XiangShan | <code>/home/yanyusong/xs-memory-env/XiangShan</code>，<code>kunminghu-v2@e12436c7cba86b195deec24981976d78bc263661</code> | 用户指定的本地 checkout。工作树原有 <code>difftest</code> 修改和 <code>src/main/resources/aia/</code> 未跟踪内容，本文没有修改，也不以它们为证据。 |
| coupledL2 | <code>fb5469838c8902b6cb33992c0a30ee3d446e4453</code> | MainPipe、Directory、DataStorage、MSHR 和 CHI Slice 的直接源码。 |
| huancun | <code>65ef077373ecf398b4cecdea06b65ef9b8d79044</code> | 仅用于核对是否存在同名模块和 CHI 配置下是否被实例化。 |
| XiangShan Design Doc | <code>/home/yanyusong/XiangShan-Design-Doc@58d9e2ad11f044cb6f8887d9687d9e110696d1aa</code> | 仅用于定位设计意图；本文的行为结论均回到上述 Chisel 源码。 |
| XiangShanLab | <code>/home/yanyusong/XiangShanLab@680010a3cf7cc72900345600b99709bc337a52bf</code> | 用于课程概念和本课程目录的 Markdown 风格。 |
| skill 同步检查 | <code>weekly_sync.py</code> 返回 <code>skip: last sync 0.23 days ago &lt; 7 days</code> | 已按 skill 执行检查；没有 reset、clean、pull 或覆盖任何工作树。 |
| 本文目标 | <code>coupledL2/tl2chi/MainPipe.scala</code> | 追到其直接邻居 RequestArb、Directory、DataStorage、MSHRCtl、各 buffer、GrantBuffer、TX 队列与 CHI 顶层。 |

### 1.2 为什么有效实现是 <code>tl2chi/MainPipe</code>

<code>KunminghuV2Config</code> 使用 1 MiB、4 bank 的 L2 配置，并叠加 <code>WithCHI</code>；后者把 <code>EnableCHI</code> 置为真。<code>L2Top</code> 因此创建 <code>TL2CHICoupledL2</code>，并将 <code>BankBitsKey</code> 设置为 <code>log2Ceil(L2NBanks)</code>。CoupledL2 随后在每一个 bank 上选择 <code>tl2chi.Slice</code>，而该 Slice 直接例化本章的 MainPipe。[Configs.scala:477-485](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:477) [L2Top.scala:111-145](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:111) [CoupledL2.scala:419-440](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:419) [Slice.scala:52-61](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:52)

~~~scala
// Configs.scala:477-485; L2Top.scala:125-131
class WithCHI extends Config((_, _, _) => {
  case EnableCHI => true
})
class KunminghuV2Config(n: Int = 1) extends Config(
  L2CacheConfig("1MB", inclusive = true, banks = 4, tp = false)
    ++ new DefaultConfig(n) ++ new WithCHI
)
if (enableCHI) Some(LazyModule(new TL2CHICoupledL2()(new Config(config))))
else Some(LazyModule(new TL2TLCoupledL2()(new Config(config))))
~~~

这不是仅由文件名得出的判断：<code>tl2chi.Slice</code> 中同时实例化 <code>Directory</code>、<code>DataStorage</code>、Refill/Release MSHRBuffer、<code>RequestArb</code>、<code>MainPipe</code>、<code>RequestBuffer</code> 与 <code>MSHRCtl</code>，并把 MainPipe 的 D/TX/存储侧端口实连到这些邻居。[Slice.scala:39-91](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:39)

~~~mermaid
flowchart LR
  CFG[KunminghuV2Config<br/>EnableCHI = true] --> L2TOP[L2Top]
  L2TOP --> L2CHI[TL2CHICoupledL2]
  L2CHI --> S0[tl2chi Slice bank 0]
  L2CHI --> SN[tl2chi Slice bank N]
  S0 --> MP0[MainPipe]
  SN --> MPN[MainPipe]
~~~

### 1.3 <code>huancun</code> 与 DCache 同名模块的边界

给定配置下，<code>SoCParamsKey</code> 只在 <code>!EnableCHI</code> 时保留 <code>L3CacheParamsOpt</code>，而在 <code>EnableCHI</code> 时改设 <code>OpenLLCParamsOpt</code>。顶层只在前一 option 存在时构造 <code>HuanCun</code>。对 <code>huancun/src/main/scala</code> 的同名类搜索也没有发现 <code>class MainPipe</code>。所以 HuanCun 是本次源码搜索的相关边界和非 CHI 对照，不能写成 Kunminghu V2 默认 CHI 路径中的 MainPipe 下游。[Configs.scala:216-239](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:216) [Configs.scala:333-382](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:333) [Top.scala:111-121](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Top.scala:111)

<code>src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala</code> 是 L1 DCache 的独立三段流水：其接口带 Probe、MissQueue、StoreBuffer、Atomic 和 SRAM 端口，入口仲裁的是 probe/refill/store/atomic。它和本文 L2 CHI MainPipe 的任务格式、级数、存储所有权都不同，不能把两个文件的 <code>s1/s2/s3</code> 混成同一条流水线。[DCache MainPipe.scala:121-275](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:121) [DCache MainPipe.scala:304-495](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:304)

| 名称 | 有效位置 | 本文是否分析为目标 | 原因 |
| --- | --- | --- | --- |
| L2 CHI MainPipe | <code>coupledL2/tl2chi/MainPipe.scala</code> | 是 | V2 的 <code>EnableCHI=true</code> 选择的每 bank Slice 主流水。 |
| L2 TL-to-TL MainPipe | <code>coupledL2/tl2tl/MainPipe.scala</code> | 否 | 只在 <code>enableCHI=false</code> 的另一个 Slice 分支生效。 |
| L1 DCache MainPipe | <code>xiangshan/cache/dcache/mainpipe/MainPipe.scala</code> | 否 | 独立 L1D 事务管线，不是 CoupledL2 MainPipe。 |
| HuanCun | <code>huancun</code> 的 Slice/SourceD 等模块 | 否 | V2+CHI 下 <code>L3CacheParamsOpt=None</code>，且无同名类。 |

## 2. 理论、Design Doc 与有效代码

### 2.1 Theory-to-Code Mapping

课程中的流水化概念可以解释为什么在长组合逻辑之间插入级间寄存器和为什么资源饱和时要反压；它不等价于 MainPipe 是 CPU 指令的取指、发射或提交流水。这里的载荷是缓存事务 <code>TaskBundle</code>，它携带 <code>tag/set/off</code>、TileLink channel、MSHR 关联和 CHI 字段，而不是 ROB 指针或执行单元结果。[课程：Single Cycle vs Multi Cycle vs Pipeline](/home/yanyusong/XiangShanLab/xiangshan-course/docs/4-xiangshan-microarchitecture-analysis/1-superscalar-basic-knowledge/1_Single_Cycle_vs_Multi_Cycle_vs_Pipeline.md) [Common.scala:55-165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:55)

| 课程概念 | 在 MainPipe 周边的有效实体 | 代码上的具体含义 | 不应外推的内容 |
| --- | --- | --- | --- |
| 流水级与级间寄存器 | <code>task_s2</code>、<code>task_s3</code>、<code>task_s4</code>、<code>task_s5</code> | S2 任务在 MainPipe 入口寄存到 S3；S4/S5 保存 task、数据和输出类型。 | 没有 CPU 级 redirect/ROB flush 接口，不能称其为投机指令流水。 |
| 结构冲突 | Directory 单端口、DataStorage 单端口 MCP2、MSHR/TX/GrantBuffer 容量 | 入口会因端口或预计占用而不准入；不是在 MainPipe 输入端临时覆盖。 | 不能仅从 <code>taskFromArb_s2.valid</code> 推断一个“fire”。 |
| 命中/缺失 | Directory 的 tag/meta 查找和 MainPipe 的 <code>need_mshr_s3</code> | 命中可直接改元数据/回响应；需下行获取、探测、别名处理时创建 MSHR。 | “miss”不是一个单独 MainPipe 状态机枚举。 |
| 非阻塞缓存 | MSHRCtl 的多项 MSHR、Refill/Release buffer、TX 队列 | 长事务离开主流水后由 MSHR 重新生成 task 返回 RequestArb。 | MSHR allocation 接口是 <code>ValidIO</code>，没有 MainPipe-to-MSHR 的 ready/fire。 |

### 2.2 Design Doc 到源码的追踪矩阵

下表只把官方文档中的意图映射到当前提交的代码，不复制其叙述或图。状态为“部分”时，表示当前 Chisel 能证明局部机制，但无法证明设计文档可能包含的所有时序前提。

| ID | Design Doc 位置 | 可核对的意图 | 当前源码证据 | 状态与差异 |
| --- | --- | --- | --- | --- |
| D1 | [ReqArb_MainPipe.md](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/ReqArb_MainPipe.md) | ReqArb 为前两段，MainPipe 覆盖后续主流水 | RequestArb 在 S1 选任务、在 S2 保存 <code>task_s2</code>；MainPipe 将其注册为 S3，后续显式有 S4/S5。 [RequestArb.scala:145-217](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [MainPipe.scala:142-150](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:142) [MainPipe.scala:744-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:744) | 已验证；本文使用代码中的实际阶段边界。 |
| D2 | [DataStorage.md](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/DataStorage.md) | 数据阵列与主流水配合、避免端口冲突 | DataStorage 实例为 single-port + read MCP2，要求请求稳定两周期；MainPipe 以 hold 寄存器驱动相应输入。 [DataStorage.scala:50-131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:50) [MainPipe.scala:469-516](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:469) | 已验证；“两周期”是数组接口保持约束，不等同于所有请求固定两周期完成。 |
| D3 | [MSHR.md](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/MSHR.md) | miss/refill/替换由 MSHR 承担长时状态 | MainPipe 产生分配 bundle，MSHRCtl 选择空闲项，MSHR 将 refill/release/probe 等任务再送回主流水。 [MainPipe.scala:232-311](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:232) [MSHRCtl.scala:94-181](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:94) [MSHR.scala:264-296](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:264) | 已验证；以本提交的 false-as-valid 状态位解释，不假定文档版本的状态命名。 |
| D4 | [Error.md](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/Error.md) | tag/data ECC 错误向事务结果传播 | Directory 形成 tag error；DataStorage 在读返回生成 data error；MainPipe 在 S5 合并并输出 error/corrupt。 [Directory.scala:227-315](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:227) [DataStorage.scala:111-122](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:111) [MainPipe.scala:850-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:850) | 已验证错误通路；是否升级为系统异常要看 BEU/顶层消费者，非本文件可证明。 |

### 2.3 不从文档或注释外推的结论

1. <code>TL2CHICoupledL2</code> 的 TileLink manager <code>minLatency=2</code> 属于顶层 manager 参数，不能据此声称“请求从 L1 到 MainPipe 固定两拍”或“命中固定两拍”。MainPipe 前面还有 RequestArb/Directory，后面还会受 DataStorage、输出仲裁和下游 ready 影响。[TL2CHICoupledL2.scala:40-66](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:40)
2. <code>arb</code> 的本地帮助函数把序列接到 Chisel <code>Arbiter</code>；本文只依据 MainPipe 明确给出的输入顺序 <code>Seq(s5,s4,s3)</code>，称其为“老阶段列在前”。标准库仲裁器的展开门级实现没有在本仓库手写，故不把它描述为带年龄记录的自定义调度器。[CoupledL2.scala:211-215](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:211) [MainPipe.scala:1023-1031](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:1023)
3. 没有运行当前提交的 elaboration 或 FST 仿真。因此下文的位段和容量是由源码配置公式导出的静态结论；精确波形、跨 Slice 最终仲裁和异常上报时点需以生成 RTL/FST 再验证。

## 3. 模块契约：Who / Why / How / From / To

### 3.1 MainPipe 的接口归类

| 接口组 | Who / From what | Why | How | To what |
| --- | --- | --- | --- | --- |
| <code>taskFromArb_s2</code>、<code>taskInfo_s1</code> | RequestArb 输出 | 把已经过 S1/S2 准入的缓存任务送入主流水，并给提示逻辑保留 S1 信息 | 均为 <code>ValidIO</code>；MainPipe 中没有反向 <code>ready</code> | <code>task_s3</code>、CustomL1Hint。 |
| <code>dirResp_s3</code>、<code>replResp</code> | Directory 输出 | 获得 hit、way、meta、tag/error 或 replacement 结果 | <code>ValidIO</code>，与该次任务的流水时序配合 | S3 分类、MSHR 分配、目录/数据阵列写选择。 |
| <code>toDS</code> | MainPipe 输出，DataStorage 输入；其 rdata/error 回流 | 单端口数据阵列访问与 S5 数据返回 | <code>en_s3</code>、<code>req_s3</code>、<code>wdata_s3</code>；没有 Decoupled ready | DataStorage 的 way/set 索引、S5 的 D/TXDAT/ReleaseBuffer payload。 |
| <code>toMSHRCtl.mshr_alloc_s3</code> | MainPipe 输出 | 将不能在短流水内完成的 A/B 事务变成长时 MSHR | <code>ValidIO(MSHRRequest)</code>；空闲项由 MSHRCtl 在入口侧保证 | 被选中的 MSHR entry，之后可能回流 RequestArb。 |
| <code>toSourceD</code> | MainPipe 输出，GrantBuffer 消费 | 把 A/C 的 TileLink 响应送向 L1 | <code>DecoupledIO(TaskWithData)</code>；S3/S4/S5 三个候选经过 arb | GrantBuffer 的 D 任务 FIFO 和 L1 D/E 生命周期。 |
| <code>toTXREQ</code>、<code>toTXRSP</code>、<code>toTXDAT</code> | MainPipe 输出，TX 队列消费 | 向 CHI 下游发请求、响应或数据 | 三个 <code>DecoupledIO</code>；S3/S4/S5 各有候选 | TXREQ/TXRSP/TXDAT，再到 CoupledL2 CHI 聚合器。 |
| <code>metaWReq</code>、<code>tagWReq</code> | MainPipe 输出，Directory 消费 | 更新状态/clients/dirty/error 或新 tag | <code>ValidIO</code>；与 Directory 读端口互斥 | Directory 的单端口 tag/meta SRAM。 |
| <code>releaseBufWrite</code>、<code>dsResp</code>、<code>nestedwb</code> | MainPipe 输出，MSHRCtl/ReleaseBuffer 消费 | 保存后续 release/probe 所需旧数据，并让 MSHR 得知 data error/nested writeback | S5 写回和带 MSHR id 的 Valid 信息 | 关联 MSHR 和 ReleaseBuffer。 |
| <code>error</code>、CMO optional ports | MainPipe 输出，Slice/上层消费者 | 报告 L2 缓存错误或 CMO line 完成 | <code>ValidIO(L2CacheErrorInfo)</code>；CMO ports 由参数生成 | Slice 将 error 寄存后交到 <code>io.error</code>。 |

接口声明在 [MainPipe.scala:32-123](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:32)，实际接线在 [Slice.scala:65-143](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:65)。

### 3.2 握手边界：哪些信号可以说 <code>fire</code>

<code>taskFromArb_s2</code>、Directory response、MSHR allocation 和 DataStorage request 都是 <code>ValidIO</code> 关系，不存在本接口上的 <code>ready</code>；因此它们不能被描述成“MainPipe 收到 ready 后 fire”。真正的入口 <code>fire</code> 在 RequestArb 的 <code>s1_fire</code>，外侧的 <code>sinkA/B/C</code> 才是 Decoupled；最终对 L1/CHI 的 D、TXREQ、TXRSP、TXDAT 则重新是 Decoupled 输出。[RequestArb.scala:153-172](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) [MainPipe.scala:74-93](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:74)

~~~mermaid
flowchart LR
  SA[SinkA / RequestBuffer] -->|Decoupled A task| RA[RequestArb]
  SB[RXSNP] -->|Decoupled B task| RA
  SC[SinkC] -->|Decoupled C task| RA
  MC[MSHRCtl] -->|Decoupled mshrTask| RA
  RA -->|Valid taskToPipe_s2| MP[MainPipe]
  RA -->|Decoupled dirRead_s1| DIR[Directory]
  DIR -->|Valid dirResp_s3/replResp| MP
  MP -->|Valid DS request + en| DS[DataStorage]
  DS -->|S5 rdata/error| MP
  MP -->|Decoupled D| GB[GrantBuffer]
  MP -->|Decoupled TXREQ/RSP/DAT| TX[CHI TX queues]
  MP -->|Valid alloc / buffers| MC
~~~

### 3.3 为什么 MainPipe 能没有入口 <code>ready</code>

RequestArb 汇总了 MSHRCtl、MainPipe、GrantBuffer 和 TX 侧的阻塞信号；其通道仲裁候选顺序是 C、B、A，且若已有回流 MSHR task，则该任务覆盖通道任务。只有 Directory 读端 ready、reset 完成、没有滞留 MSHR task、以及 <code>s2_ready</code> 同时满足，入口才会对外给 ready。MainPipe 后续向 RequestArb 输出 set/tag 冲突信息并向 GrantBuffer/TX 输出在途状态，因而反压已经发生在任务进入 S2 之前。[RequestArb.scala:132-181](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:132) [MainPipe.scala:909-975](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:909)

~~~scala
// RequestArb.scala:145-169，通道任务的优先级和最终 S1 选择
val sinkValids = VecInit(Seq(
  io.sinkC.valid && !block_C,
  io.sinkB.valid && !block_B,
  io.sinkA.valid && !block_A
)).asUInt
val task_s1 = Mux(mshr_task_s1.valid, mshr_task_s1, chnl_task_s1)
val s1_to_s2_valid = task_s1.valid && !mshr_replRead_stall
s1_fire := s1_cango && s2_ready
~~~

同时请求时，C 优先于 B，B 优先于 A；若 <code>mshr_task_s1.valid</code>，它进一步优先于本次通道选择。未被选中的 Decoupled 通道没有 ready，会保持 valid/bits 或由其上游缓冲。这里不是 round-robin 仲裁，也没有从代码中看到 A/B/C 的年龄比较。

## 4. 参数、地址与存储结构

### 4.1 Kunminghu V2 下可由配置直接推导的参数

<code>L2CacheConfig</code> 的 sets 计算式为 <code>size / banks / ways / 64</code>。<code>KunminghuV2Config</code> 没有覆盖 <code>ways</code> 默认值 8；因此每个 bank 的 sets 是 <code>1024 KiB / 4 / 8 / 64 B = 512</code>。<code>L2Param</code> 的默认 <code>mshrs=16</code> 也没有在该配置中被覆写。以下是配置表达式推导，未替代一次 elaboration。[Configs.scala:278-330](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:278) [Configs.scala:481-485](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481) [L2Param.scala:65-75](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:65)

| 参数 | 源码表达式 | 本配置的结果 | 对 MainPipe 的影响 |
| --- | --- | --- | --- |
| L2 总容量 | <code>L2CacheConfig("1MB", ...)</code> | 1 MiB | 四个 Slice 的 aggregate 容量。 |
| banks | <code>banks=4</code> | 4，<code>bankBits=2</code> | 每 bank 一份 Slice/MainPipe；地址解析跳过 bank bits。 |
| ways | <code>ways: Int = 8</code> | 8，<code>wayBits=3</code> | Directory/DS way 索引和 <code>wayMask</code> 宽度。 |
| 每 Slice sets | <code>nKB*1024/banks/ways/64</code> | 512，<code>setBits=9</code> | Directory/DS 的 set 索引宽度。 |
| cache line | <code>blockBytes=64</code> | 64 B，<code>offsetBits=6</code> | Task 的 <code>off</code>、CHI/TL line-size 以及 DS block 宽度。 |
| CHI D beat | <code>channelBytes.d=32</code> | 32 B，<code>beatSize=2</code> | line 对 TXDAT/GrantBuffer 是两个 32 B beat；不代表 MainPipe 的 DS 只读半行。 |
| 每 Slice MSHR | <code>mshrs=16</code>，<code>mshrsAll=cacheParams.mshrs</code> | 16 | MSHRCtl entries、MSHRBuffer entries、TX queue 预留上界。 |

<code>HasCoupledL2Parameters</code> 定义了 <code>blockBytes</code>、<code>beatBytes</code>、<code>setBits</code>、<code>offsetBits</code>、<code>bankBits</code> 和 <code>mshrsAll</code>，不是由 MainPipe 硬编码常数得出。[CoupledL2.scala:46-56](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:46) [CoupledL2.scala:118-142](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:118)

### 4.2 地址、bank、set、way 与 cache-line 粒度

上游 SinkA 将 TileLink A 的 address 用 <code>parseAddress</code> 拆成 task 的 <code>tag/set/off</code>；RXSNP 将 CHI SNP 的 address 补回低 3 位后用同一函数拆分。该函数在取 set 前跳过 <code>offsetBits + bankBits</code>，所以 bank 位在 offset 之上、slice 内 set 位在 bank 位之上。CHIP 顶层对 RXSNP 也用相同的 bank 位把 snoop 路由到 slice。[SinkA.scala:56-75](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkA.scala:56) [CoupledL2.scala:179-204](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:179) [RXSNP.scala:131-145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:131) [TL2CHICoupledL2.scala:158-165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:158)

~~~scala
// CoupledL2.scala:186-190
def parseAddress(x: UInt): (UInt, UInt, UInt) = {
  val offset = x
  val set = offset >> (offsetBits + bankBits)
  val tag = set >> setBits
  (ZeroExt(tag, tagBits), set(setBits - 1, 0), offset(offsetBits - 1, 0))
}
~~~

在本配置下，若以送入 L2 的完整物理地址 PA 表示：

| 逻辑字段 | 可推导的 PA 位 | 代码如何消费 | 边界 |
| --- | --- | --- | --- |
| <code>off</code> | <code>PA[5:0]</code> | TaskBundle <code>off</code>；MainPipe 不以该字段索引 DS。 | 这是 line 内 byte offset，不是 DS array index。 |
| Slice bank | <code>PA[7:6]</code> | 2 个 bank bits；RXSNP 以 CHI address 的对应位选择 Slice。 | 此结论依赖给定配置的 4 bank 和 64 B line。 |
| Slice 内 <code>set</code> | <code>PA[16:8]</code> | Directory 读/写与 DataStorage 的 <code>arrayIdx</code>。 | 512 sets 故为 9 位。 |
| <code>tag</code> | 完整地址中高于 <code>PA[16:0]</code> 的 slice-local tag | Directory tag compare 和 CHI 地址重建。 | <code>fullAddressBits</code> 由 edge bundle 给出，本文不虚构顶层地址宽度。 |
| <code>way</code> | 不是地址位 | Directory hit 或 replacer 选择，最终与 set 拼成 DS 索引。 | 8 ways 时宽度为 3 位。 |

MainPipe 对外造 CHI 请求时使用 <code>Cat(tag, set, 0.U(offsetBits.W))</code>，即将 line offset 清零。这与 DataStorage 一次读写一个 <code>DSBlock</code> 相一致：MainPipe 的命中、替换、回填和 snoop 数据处理都以 cache line 为基本对象。它不能说明一个跨行的 CPU 内存访问如何拆分；那必须由进入 SinkA 前的上游缓存/访存模块处理。[Common.scala:55-165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Common.scala:55) [DataStorage.scala:84-109](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:84)

### 4.3 目录、数据阵列和 buffer 的 update / release / replace / search

| 结构 | 容量/索引 | reset 或初态 | update | release / replacement | search/read 与冲突处理 |
| --- | --- | --- | --- | --- | --- |
| Directory tag/meta | 每 Slice <code>512 sets x 8 ways</code> | MainPipe 未完成 reset 时逐 set、对所有 way 写空 <code>MetaEntry()</code>。 | MainPipe 以 <code>metaWReq</code> 更新状态、clients、dirty、error 等，以 <code>tagWReq</code> 写 refill tag。 | refill 选择 invalid way 或 replacement way；若所有可用 way 被占用，给 MSHR <code>retry</code>。 | S1 发读、S2 锁存、S3 compare；一旦 meta/tag/replacer 写有效，<code>read.ready</code> 为低，避免单端口竞争。 |
| DataStorage | <code>blocks = sets * ways = 4096</code> 个 line slot；<code>arrayIdx=Cat(way,set)</code> | SRAM 实现细节不在本文件展开。 | MainPipe S3 选 <code>ren/wen</code>、set、way、write data。 | C 的 ReleaseData、MSHR refill/replacement 等会写；旧数据可在 S5 写 ReleaseBuffer。 | single-port，不允许连续 <code>en</code>；request/address/wdata 必须保持两周期，S3 读、S4 传递、S5 取数据。 |
| RefillBuffer | <code>mshrsAll x beatSize = 16 x 2</code> beats | Reg array；每 entry 对应一个 MSHR。 | RXDAT/SinkC 等由 Slice 接到两个写端口。 | MSHR task 指定 id 读，MainPipe 可取其数据写 DS 或发响应。 | 读端为 ValidIO；MainPipe 只用连接中的结果，不自行按地址 CAM 查找。 |
| ReleaseBuffer | <code>16 x 2</code> beats，3 个写端口 | Reg array；每 entry 对应一个 MSHR。 | SinkC、MainPipe S5、MainPipe nested writeback 三类来源对应 3 写端口。 | 保存替换/探测所需旧 line，随后由 MSHR/主流水读走。 | 端口数量是显式结构资源；每个 entry 仍按 MSHR id 访问。 |
| MSHR | 每 Slice 16 项 | 每项 <code>req_valid=false</code>；allocation 时重置事务状态/计数。 | MainPipe alloc 交给 selected idle entry。 | <code>no_schedule && no_wait</code> 时清 <code>req_valid</code>。 | MSHRCtl 从 idle 向量取优先项；Directory 把正在 refill/alias 的 way 计为不可替换。 |

Directory 的流水、写优先级和端口互斥见 [Directory.scala:190-360](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:190)；DataStorage 的 single-port 和断言见 [DataStorage.scala:50-131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:50)；MSHRBuffer 的 entry/port 形状见 [MSHRBuffer.scala:39-58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39)，Slice 的实际 wPorts 参数见 [Slice.scala:52-56](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:52)。

### 4.4 Directory 的 replacement 与 retry

Directory 在 S2 根据所有 MSHR 的 <code>set</code>、<code>way</code> 和 <code>blockRefill/dirHit</code> 形成 occupied-way mask；在 S3 先优先 invalid way，否则用 replacement policy，再限制到 free-way mask。若 free-way mask 为空，<code>replResp.retry</code> 为真。MainPipe 对一个 MSHR refill 见到 retry 后不写 tag，也把该任务丢出短流水；该 MSHR 将 <code>s_refill</code>、<code>s_retry</code> 重新置为待做并累加 retry 计数，再经 RequestArb 回来重读目录。[Directory.scala:255-346](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:255) [MainPipe.scala:225-230](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:225) [MainPipe.scala:608-611](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:608) [MSHR.scala:1256-1296](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1256)

这条路径证明的是“对无 free way 的 refill 做 retry/backoff”，不是泛泛的 CPU load replay。原因、队列归属和可观测信号均在 L2 MSHR / Directory 边界。

## 5. S1--S5 阶段细化

### 5.1 阶段总表

| 阶段 | 所在模块与有效 payload | 主要工作 | 写入/输出 | 能停住或改变该阶段的条件 |
| --- | --- | --- | --- | --- |
| S1 | RequestArb；<code>task_s1</code> wire、<code>taskInfo_s1</code> | 汇总 block，C &gt; B &gt; A，MSHR 回流 task 可覆盖通道 task；提交 Directory read。 | <code>dirRead_s1</code>、<code>taskInfo_s1</code>、<code>s1Entrance</code>。 | Directory read 不 ready、reset 未完、滞留 MSHR task、各 block、<code>s2_ready=0</code>。 |
| S2 | RequestArb；<code>task_s2: Valid(TaskBundle)</code> | S1 fire 后注册任务，送给 MainPipe；对非 A Hint 插入 MCP2 保护。 | <code>taskToPipe_s2</code>。 | 上一拍非 Hint <code>s1_fire</code> 使 <code>ds_mcp2_stall</code>，下一拍 <code>s2_ready=0</code>。 |
| S3 | MainPipe；<code>task_s3</code> 和 Directory S3 result | 分类 A/B/C/MSHR，判断 hit/error/CMO、MSHR、DS read/write、meta/tag write、直接 D/TX 候选。 | MSHR alloc、DS request、Directory writes、S3 D/TX valid、block/status。 | Directory/replacement 结果、同 set/tag block、DS MCP2 规则、MSHR/TX/GrantBuffer 容量在入口前已经影响是否到达。 |
| S4 | MainPipe；注册的 task/data/error/输出类型 | 对 S3 没有被 drop 的任务寄存，切断 Directory meta 到 B 响应的长组合路径。 | S4 D/TX 候选；必要时继续携带 ReleaseBuffer 写需求。 | S3 已从对应 channel fire 且不需 ReleaseBuffer 时可 drop。 |
| S5 | MainPipe；注册 task、S5 DS data/error | 选择 DS 返回数据或 buffer 数据，写 ReleaseBuffer / dsResp，形成延后 D/TXDAT。 | S5 D/TX、<code>releaseBufWrite</code>、<code>dsResp</code>、<code>error</code>。 | S4 drop 或更早 channel fire 会抑制重复输出；真正输出仍受 Decoupled ready。 |

S1/S2 的代码证据见 [RequestArb.scala:145-217](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145)；S3、S4、S5 的寄存器与输出证据见 [MainPipe.scala:142-150](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:142) [MainPipe.scala:469-686](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:469) [MainPipe.scala:744-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:744)。

### 5.2 S1 / S2：仲裁、首个请求与 MCP2 间隔

复位后 RequestArb 的 <code>resetFinish</code> 逐项完成 Directory reset 扫描前不会准入通道任务。第一个真正的 A/B/C 请求同时需要 <code>dirRead_s1.ready</code>、<code>resetFinish</code> 和 <code>s2_ready</code>；S1 成功后 <code>task_s2.valid := s1_fire</code>。这给出首请求的明确路径，而不是假定 reset 释放后任意请求立即进入 MainPipe。[RequestArb.scala:78-118](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:78) [RequestArb.scala:153-208](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153)

<code>ds_mcp2_stall = RegNext(s1_fire &amp;&amp; !s1_AHint_fire)</code>，所以任何非 A Hint 的 S1 接受都会使下一拍 <code>s2_ready</code> 变低。注释给出的原因是 DataStorage 不允许连续访问；这是一条以吞吐换取 MCP2 SRAM 正确性的硬约束。它不是“所有请求隔一拍”的绝对句子，因为 A Hint 是代码中的例外，其他入口限制也可能更早生效。[RequestArb.scala:199-217](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:199) [DataStorage.scala:119-131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:119)

~~~waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......" },
    { "name": "sinkA.valid", "wave": "0100000" },
    { "name": "sinkA.ready", "wave": "0110000" },
    { "name": "RequestArb.s1_fire", "wave": "0010000" },
    { "name": "ds_mcp2_stall", "wave": "0001000" },
    { "name": "s2_ready", "wave": "1110111" },
    { "name": "sinkB.valid", "wave": "0001100" },
    { "name": "sinkB.ready", "wave": "1110011" },
    { "name": "taskToPipe_s2.valid", "wave": "0001000" }
  ],
  "config": { "hscale": 1 }
}
~~~

图中用的是源码局部信号名；它说明“一次非 Hint S1 接受后，下一拍 B 即使 valid 也会因 <code>s2_ready=0</code> 不被接收”的关系，并非仿真导出的固定绝对周期波形。

### 5.3 S3：任务分类、MSHR 决策和 DataStorage 请求

S3 先依据 <code>mshrTask</code> 和 <code>channel</code> 区分普通 A/B/C 与 MSHR 回流，再识别 AcquireBlock/AcquirePerm/Get/Hint/CMO、CHI snoop response、refill、writeback 等细项。其好处是同一主流水既能处理短路径的 A/C 响应，又能作为 MSHR 的数据/目录更新执行引擎；坏处是必须由 <code>need_mshr_s3</code>、<code>req_drop_s3</code> 和 buffer/status 控制避免重复消费。[MainPipe.scala:158-230](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:158) [MainPipe.scala:616-686](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:616)

MSHR 条件并非只有“L2 miss”：

- A：miss/需要 T 的权限提升、CMO、缓存别名处理或需要向 L1 发 Probe 时可进入 MSHR。
- B：需要 pProbe 或需 DCT 数据转发的 CHI snoop 可进入 MSHR。
- C：Release 通常能够在短路径更新数据和 meta；与已有 MSHR 的关联由 SinkC/MSHRCtl 的匹配路径处理，而不是一律新分配。

这些条件和 allocation bundle 见 [MainPipe.scala:232-311](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:232)。特别是：

~~~scala
// MainPipe.scala:293-309
val need_mshr_s3 = need_mshr_s3_a || need_mshr_s3_b
io.toMSHRCtl.mshr_alloc_s3.valid :=
  task_s3.valid && !mshr_req_s3 && need_mshr_s3
io.toMSHRCtl.mshr_alloc_s3.bits.dirResult := nestable_dirResult_s3
io.toMSHRCtl.mshr_alloc_s3.bits.task := req_s3
~~~

S3 数据阵列请求也不是“每项都读”。<code>ren</code> 覆盖 A 的 Get/AcquireBlock hit、B 的需要数据/forward、替换和 dirty CMO；<code>wen</code> 覆盖来自 C 的 ReleaseData 和若干 MSHR refill/writeback 路径。way 由 replacement result、MSHR task 自带 way 或 Directory hit way 三选一；set 同理在 MSHR task 与 Directory result 间选择，写数据来自 SinkC、ReleaseBuffer 或 RefillBuffer。[MainPipe.scala:469-516](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:469)

~~~scala
// MainPipe.scala:491-516，MCP2 保持和 DS 地址/数据选择
when(task_s2.valid) { task_s3_valid_hold2 := "b11".U }
io.toDS.en_s3 := task_s3.valid && (ren || wen)
io.toDS.req_s3.valid := task_s3_valid_hold2(0) && (ren || wen)
io.toDS.req_s3.bits.way := Mux(... replResp.way, Mux(mshr_req_s3, req_s3.way, dirResult_s3.way))
io.toDS.req_s3.bits.set := Mux(mshr_req_s3, req_s3.set, dirResult_s3.set)
io.toDS.wdata_s3.data := Mux(!mshr_req_s3, c_releaseData_s3, Mux(req_s3.useProbeData, releaseBuf, refillBuf))
~~~

### 5.4 S3 目录更新、reset sweep 与输出 one-hot

MainPipe 的 meta write 有五类来源：短路径 A、短路径 B、C、MSHR 和 CMO invalid；它以明确的 <code>ParallelPriorityMux</code> 顺序选择。复位期间不看正常 task，而是从 <code>sets-1</code> 向 0 递减，在当前 set 对所有 way 写 <code>MetaEntry()</code>。tag 只在 MSHR refill 且非 retry 时写，从而避免把尚未获得可用 victim way 的 refill 固化进 tag array。[MainPipe.scala:127-135](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:127) [MainPipe.scala:532-611](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:532)

~~~scala
// MainPipe.scala:594-611
io.metaWReq.valid := !resetFinish || task_s3.valid && (...)
io.metaWReq.bits.set := Mux(resetFinish, req_s3.set, resetIdx)
io.metaWReq.bits.wayOH := Mux(resetFinish, UIntToOH(metaW_way), Fill(cacheParams.ways, true.B))
io.tagWReq.valid := task_s3.valid && req_s3.tagWen && mshr_refill_s3 && !retry
~~~

S3 对每个有效 task 由 <code>isTXREQ_s3</code>、<code>isTXRSP_s3</code>、<code>isTXDAT_s3</code>、<code>isD_s3</code> 做 one-hot 检查。若短路径需要新 MSHR，或某输出已 fire 且不需要把旧数据写入 ReleaseBuffer，则 <code>req_drop_s3</code> 使其不再无谓进入 S4；MSHR refill retry 也走 drop。不能把这个 drop 理解为丢失事务：新 MSHR 接手的长事务或 MSHR retry 状态承担后续进展。[MainPipe.scala:616-686](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:616)

### 5.5 S4 / S5：时序切分、数据回归与出口仲裁

S4 在 <code>!req_drop_s3</code> 时锁存 task、data、error、DS read 标志和输出类型。源码明确指出 Directory meta 到 B response 的组合路径过长，因此数据型 D/TXDAT 响应会被锁存，以改善时序。[MainPipe.scala:628-636](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:628) [MainPipe.scala:744-811](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:744)

S5 从 <code>io.toDS.rdata_s5</code> 取得数组读数据，或保留 MSHR/snoop-release 已携带的 buffer 数据；随后生成 ReleaseBuffer 写和 <code>dsResp</code>，同时组合 <code>denied/corrupt</code>。<code>chnl_valid_s5</code> 排除早前已经 fire 的同一响应，避免重复输出。[MainPipe.scala:813-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:813)

最后 D、TXREQ、TXRSP、TXDAT 各自把 <code>s5,s4,s3</code> 候选按此顺序交给 <code>arb</code>。这使较老的阶段先作为 arb 输入；每条物理出口在一个周期只有一个候选能够取得该出口的 Decoupled 传输，不能把“四类输出”理解成同一 CHI channel 可以一拍发四条消息。[MainPipe.scala:1023-1031](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:1023)

~~~waveform-draw
{
  "signal": [
    { "name": "clk", "wave": "p......" },
    { "name": "taskFromArb_s2.valid", "wave": "0100000" },
    { "name": "taskFromArb_s2.bits", "wave": "x=.....", "data": ["T0"] },
    { "name": "task_s3.valid", "wave": "0010000" },
    { "name": "toDS.en_s3", "wave": "0010000" },
    { "name": "toDS.req_s3.valid", "wave": "0011000" },
    { "name": "task_s4.valid", "wave": "0001000" },
    { "name": "task_s5.valid", "wave": "0000100" },
    { "name": "toSourceD.valid", "wave": "0000010" },
    { "name": "toSourceD.ready", "wave": "1111111" },
    { "name": "toSourceD.fire", "wave": "0000010" }
  ],
  "config": { "hscale": 1 }
}
~~~

这张图只描述一个“需要 DS 数据、最后走 S5 D”的示例：<code>toDS.req_s3.valid</code> 两拍保持来自 MCP2 约束；不带数据的短响应可以在 S3/S4 被更早发出或 drop，实际 <code>toSourceD.valid</code> 也会受后级 GrantBuffer/arb 情况影响。

## 6. MSHR、资源仲裁与具体事务走读

### 6.1 MSHR allocation、保留项与回流

MSHRCtl 用所有 MSHR 的 <code>!status.valid</code> 建 idle 向量，<code>ParallelPriorityMux</code> 选择第一个可用 index；其计数同时包括主流水在途项和已经 valid 的 MSHR。满时 B 在 <code>mshrFull</code> 被阻塞，A 在剩最后一项时就由 <code>a_mshrFull</code> 被阻塞，显式给 B/snoop 留一项资源。MainPipe 因此可以用 Valid-only allocation，而不需要 allocation ready：准入时的 block 已经避免没有 entry 的情形。[MSHRCtl.scala:94-166](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:94)

~~~scala
// MSHRCtl.scala:106-123, 132-166
val mshrFull = pipeReqCount + mshrCount >= mshrsAll.U
val a_mshrFull = pipeReqCount + mshrCount >= (mshrsAll-1).U
mshrSelector.io.idle := mshrs.map(m => !m.io.status.valid)
io.toMainPipe.mshr_alloc_ptr := OHToUInt(selectedMSHROH)
m.io.alloc.valid := selectedMSHROH(i) && io.fromMainPipe.mshr_alloc_s3.valid
io.toReqArb.blockB_s1 := mshrFull
io.toReqArb.blockA_s1 := a_mshrFull
~~~

MSHR allocation 时把 <code>req_valid</code> 设为真、保存 Directory result 和 task，并把 retry/backoff、grant/probe/data 追踪状态初始化。其可调度任务包括 release、probeack、grant、copy-back data、DCT 和 CMO meta write；这些 task 通过 MSHRCtl 的 <code>fastArb</code> 回到 RequestArb，再复用 MainPipe 的 Directory/DataStorage/出口路径。任务结束条件不是“最后一个输出 fire”这么简单，而是所有 schedule 位和所有 wait 位完成后 <code>will_free</code> 清 <code>req_valid</code>。[MSHR.scala:132-157](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:132) [MSHR.scala:264-296](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:264) [MSHR.scala:977-991](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:977) [MSHR.scala:1303-1317](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1303)

### 6.2 同 set/tag 冲突：入口而非“MainPipe 内部 flush”

MainPipe 的 <code>toReqBuf</code> 阻塞同 set 的 A；<code>blockC_s1</code> 阻塞同 set 的 C；B 在 S2/S3 比较 set、在 S4/S5 比较 set+tag。这里的精细差异是由 <code>s23Block</code> 和 <code>bBlock</code> 明文写出的，目的在于避免即将写 Directory 的流水项与新的目录访问/一致性请求发生危险冲突。[MainPipe.scala:909-943](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:909)

此外，GrantBuffer 会对尚未收到 GrantAck 的相同 set/tag 阻塞 B；TXREQ/TXDAT/TXRSP 也根据“队列已有数 + 管线可能流入数”回压 MSHR/入口。MainPipe 将 S3/S4/S5 的 status 送给这些资源做容量预测，而不是等输出端口满了才在 MainPipe 中撤销已经接受的 task。[MainPipe.scala:945-975](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:945) [GrantBuffer.scala:292-326](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:292) [TXREQ.scala:47-75](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXREQ.scala:47) [TXDAT.scala:65-80](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TXDAT.scala:65)

| 竞争场景 | 精确触发 | 赢者 / 失败者 | 后续效果 |
| --- | --- | --- | --- |
| A、B、C 同拍到达 | 三个 valid 且各自未 block | C，再 B，再 A；MSHR task 若滞留则优先于它们 | 失败者没有 ready，留在原缓冲。 |
| 连续普通数组访问 | 上拍 <code>s1_fire &amp;&amp; !AHint</code> | 已进入 S2 的任务保留；下一拍没有新 S1 | <code>ds_mcp2_stall</code> 使 <code>s2_ready=0</code>。 |
| Directory 读与 meta/tag/replacer 写 | <code>metaWReq.valid || tagWReq.valid || replacerWen</code> | 写占单端口 | <code>Directory.read.ready=0</code>，ReqArb 不准入读。 |
| MSHR 只剩最后一项 | <code>a_mshrFull</code> | B 保留机会，A 被 block | 避免 A 抢走最后 entry 导致 snoop 无资源。 |
| refill 找不到 free way | <code>freeWayMask_s3.orR=0</code> | 当前 refill 不能写 tag/DS | <code>replResp.retry</code> 回到 MSHR retry/backoff。 |
| B 与未 GrantAck 的同地址 Grant 冲突 | GrantBuffer in-flight set/tag match | 旧 Grant 生命周期先完成 | B 在入口 block，避免 Probe 越过未确认 Grant。 |
| TX 预计满 | pipe status + queue count 达阈值 | 新 MSHR task 或通道入口被 block | 不以 MainPipe output ready 的临时冒险来承受溢出。 |

### 6.3 隐式状态机：不是一个独立 MainPipe FSM

MainPipe 没有一个名为 <code>state</code> 的枚举 FSM。其主生命周期由 S2/S3/S4/S5 的 Valid 寄存器和 <code>req_drop</code> 控制；长事务转移给 MSHR 的 <code>FSMState</code> 位。下图明确把“短流水结束”和“MSHR 持有”分开，避免把 MSHR 状态误称为 MainPipe 的 FSM。

~~~mermaid
stateDiagram-v2
  [*] --> ResetSweep
  ResetSweep --> AdmitS1: resetFinish and RequestArb conditions
  AdmitS1 --> S2: s1_fire
  S2 --> S3: task_s2.valid
  S3 --> ShortResp: D/TX candidate and no DS-long path
  S3 --> S4: !req_drop_s3
  S3 --> MSHRHeld: need_mshr_s3 and alloc valid
  S3 --> RetryHeld: refill retry
  S4 --> S5: !req_drop_s4
  S4 --> ShortResp: channel fire
  S5 --> ShortResp: D/TX fire or queued
  MSHRHeld --> AdmitS1: MSHR task returns through RequestArb
  RetryHeld --> AdmitS1: MSHR retry task returns
  ShortResp --> [*]
~~~

<code>resetFinish</code>、<code>task_s3/s4/s5.valid</code>、<code>req_drop_s3/s4</code> 的赋值见 [MainPipe.scala:127-150](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:127) [MainPipe.scala:622-624](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:622) [MainPipe.scala:759-823](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:759)。

### 6.4 动态案例 A：命中的 A Get / AcquireBlock

以下以某 A 请求为例，假设它不与 C/B/回流 MSHR 竞争、Directory hit，且需要返回 line 数据：

1. SinkA 先把 address 分解为 task 的 <code>tag/set/off</code>，RequestArb 在 S1 发送 Directory read；S1 fire 后 task 被保存为 S2。[SinkA.scala:56-75](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkA.scala:56) [RequestArb.scala:174-208](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174)
2. MainPipe 把该 task 注册为 S3；Directory 已在自己的 S3 给出 hit、way 和 meta。对于 <code>Get</code> 或 <code>AcquireBlock</code> hit，<code>need_data_a</code> 使 <code>ren</code> 为真，并用 <code>dirResult_s3.way/set</code> 访问 DataStorage。[MainPipe.scala:142-155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:142) [MainPipe.scala:469-507](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:469)
3. 由于数据还未从 MCP2 array 回来，响应需要通过 S4/S5；S5 用 <code>rdata_s5</code> 构造 D payload，并把 tag/data error 合入 denied/corrupt。[MainPipe.scala:744-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:744)
4. <code>toSourceD</code> 经过 s5/s4/s3 arb 进入 GrantBuffer，后者负责 D FIFO、GrantAck 等待和同地址 Probe 屏障。MainPipe 不直接消费 TileLink E。[Slice.scala:65-80](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:65) [GrantBuffer.scala:59-83](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:59)

这个案例说明“命中”不必然等于 S3 单拍返回。需要数据时，DataStorage 的 S3--S5 路径和出口 arbitration 仍是可见的变量。

### 6.5 动态案例 B：A miss / 权限提升 / replacement

1. S3 若 A miss、BRANCH 上的 need-T、别名、CMO 或需要 Probe，则 <code>need_mshr_s3_a</code> 为真；MainPipe 填入 Directory 结果、task 和初始状态到 allocation bundle。[MainPipe.scala:235-256](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:235) [MainPipe.scala:293-309](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:293)
2. MSHRCtl 的 selected idle entry 接受它；如果当前只剩最后一项，A 早在入口已被 block，因而此处不靠 ready 回退。[MSHRCtl.scala:106-166](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:106)
3. MSHR 在等待 CHI grant、L1 probe ack、replacement 信息、ReleaseBuffer 数据等期间保持 <code>req_valid</code>；到需写 DS 或需发 D/TX 的时刻，它生成 <code>mshrTask</code> 再回到 RequestArb/MainPipe。[MSHR.scala:264-296](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:264) [MSHRCtl.scala:168-181](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:168)
4. 若 Directory 表明 victim way 都被在途 refill/alias 占用，<code>replResp.retry</code> 令 refill 延后；MSHR 重置 refill/retry schedule 位，并在 backoff 后再走 Directory，不会写一个不安全的 victim way。[Directory.scala:255-346](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:255) [MSHR.scala:1256-1266](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1256)

### 6.6 动态案例 C：CHI snoop 与嵌套/阻塞

RXSNP 先把 CHI snoop 缓冲在深度 2 的 Queue，再按 MSHR 信息判断同请求地址、正在替换的 victim 地址或 CMO 是否需 block。未阻塞的 snoop 作为内部 B task 送 RequestArb；MainPipe 在 S3 决定是否需要向 L1 发 pProbe、是否可 forward data、以及是否需要 MSHR。tag error 时 <code>canFwd</code> 为假，不能直接 forward 数据。[RXSNP.scala:28-115](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:28) [MainPipe.scala:258-291](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:258)

这说明 snoop 并非“直接抢占 MainPipe”：它先受 RXSNP 队列/嵌套条件，再受 RequestArb 的 C 优先与入口 block，最后由 MainPipe 的 S3 分类决定短响应或 MSHR 长路径。

## 7. 正确性、前进性与异常边界

### 7.1 代码可见的前进性防线

| 防线 | 代码位置 | 作用 | 不能过度解读为 |
| --- | --- | --- | --- |
| MSHR 资源保留 | <code>a_mshrFull</code> 留最后一项给 B | 防止 A 完全耗尽可处理 snoop 的项 | 已证明所有协议死锁都不可能。 |
| Directory refill retry | free-way mask / <code>replResp.retry</code> | 避免写入正在被别的 MSHR 使用的 way | CPU 指令级 replay。 |
| DataStorage MCP2 断言 | 禁止连续 <code>en</code> 和变化的 req/wdata | 防止 SRAM 多周期路径输入被覆盖 | 每个缓存请求固定 latency。 |
| RXSNP stall 监测 | <code>stallCnt &lt;= 28000</code> 断言 | 在仿真中发现长期不前进的 snoop 队首 | 28000 是协议 credit 或保证恢复界。 |
| MSHR valid 计数 | <code>validCnt &lt;= 400000</code> 断言 | 在仿真中暴露长时间未释放 MSHR | 400000 后硬件自动恢复。 |
| 输出容量预测 | GrantBuffer/TX 的 pipe status + queue count | 提前阻止会溢出的入口或 MSHR task | output <code>ready</code> 永远不会拉低。 |

RXSNP 和 MSHR 的断言分别见 [RXSNP.scala:117-129](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:117) 与 [MSHR.scala:1435-1449](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1435)。

### 7.2 ECC / error 的路径

Directory 对 tag ECC decode 的 error 与 multi-hit 形成 <code>DirResult.error</code>；MainPipe 在 S3 合并 Directory tag error、meta 保存的 tag/data error，并在 S5 把 DataStorage 的 ECC error 合入 <code>l2Error_s5</code> 与 <code>corrupt</code>。<code>io.error</code> 带的是拼接后的 task address，Slice 再把该 error 寄存后向外输出。[Directory.scala:227-315](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:227) [MainPipe.scala:221-223](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:221) [MainPipe.scala:850-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:850) [MainPipe.scala:1033-1036](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:1033) [Slice.scala:196-205](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:196)

这条分析只证明缓存错误信号的产生和 Slice 边界；它没有证明错误在 CPU 中变成哪一种同步异常、异步 BEU 报警或 Difftest 字段，后者需要继续追踪 <code>io.error</code> 的顶层消费者。

### 7.3 Difftest 的范围

对 <code>tl2chi/MainPipe.scala</code>、<code>tl2chi/Slice.scala</code> 和 <code>tl2chi/MSHR.scala</code> 执行 <code>rg -n -i 'difftest|DiffTest'</code> 没有匹配项。因而本模块没有直接构造或驱动 Difftest bundle；不能因为它处理 L2 hit/miss 就虚构 ROB、提交、异常或寄存器状态的 difftest 事件。缓存错误只可先追到上述 <code>io.error</code>，系统级 difftest 对照需要在 SoC/test harness 继续查找。

### 7.4 虚拟页、cache line 与 MMIO / CHI 边界

| 边界 | MainPipe 代码可证明的事实 | 本文不声称的事实 | 应继续追踪的位置 |
| --- | --- | --- | --- |
| VA 到 PA / 跨页 | TaskBundle 可能携带可选 <code>vaddr</code> 用于预取训练，但 MainPipe 的主要查找字段是 <code>tag/set/off</code>，IO 没有 TLB、PMP、ASID 或 page-fault 接口。 | 跨页 load/store 的拆分、异常优先级或 VA 同义处理的全部机制。 | L1D/LoadStore/TLB 和发往 SinkA 前的路径。 |
| 跨 cache line | DS 访问以 <code>DSBlock</code> 为单位，CHI request address 将 offset 置 0。 | 一个上游请求是否可跨 line，或由哪一级拆为两个 L2 task。 | TileLink A 产生者和 L1D miss/uncache 逻辑。 |
| MMIO | CHI L2 有独立 <code>MMIOBridge</code>，其 TL manager 是 <code>RegionType.UNCACHED</code> 且支持 1--8 B；L2 cached manager 和 MainPipe 的 cache transaction 路径不是该 bridge。 | MainPipe 对 MMIO 做 cache lookup、MMIO 的完整 ordering 或 AXI 时序。 | MMIOBridge、Top 的 CHI route、OpenNCB / 外部 LLC。 |
| CHI 到 LLC / AXI | L2 顶层按 txnID / slice id 分发 RXRSP/RXDAT，按地址 bank 分发 RXSNP；Top 将 CHI 路由给 MMIO 或 OpenLLC。 | MainPipe 直接有 AXI master 接口。 | TL2CHICoupledL2、Top、OpenLLC/OpenNCB。 |

MMIO 的独立 region 和 entry 资源见 [MMIOBridge.scala:31-61](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MMIOBridge.scala:31) [MMIOBridge.scala:345-404](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MMIOBridge.scala:345)；CHI routing 的证据见 [TL2CHICoupledL2.scala:158-276](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:158) 与 [Top.scala:514-545](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Top.scala:514)。

## 8. 延迟与吞吐：代码能证明什么

### 8.1 延迟分解

| 路径 | 代码可见级数 / 条件 | 结论类型 |
| --- | --- | --- |
| 请求准入到 MainPipe S3 | RequestArb S1 fire -&gt; S2 reg -&gt; MainPipe S3 reg，且 Directory 自身 S1 read/S2 latch/S3 compare | 有级间关系，但不是从外部 A valid 到响应的固定周期数。 |
| 命中且需要数据 | S3 发 DS 读；DataStorage 明确标注 S3 read、S4 pass、S5 destination | 对 DS 数据返回的流水关系有代码依据；仍受入口与 D/GrantBuffer backpressure 影响。 |
| 不需数据的短响应 | S3 可形成 D/TXRSP/TXREQ/TXDAT 候选，S4/S5 可能继续或因 fire drop | 可短于数据路径，但不存在对所有 opcode 的单一固定值。 |
| miss / probe / refill / retry | MainPipe alloc -&gt; MSHR 等待 CHI/Probe/ReleaseBuffer/replacer -&gt; 回流 MainPipe | 变量延迟，无固定上界；仿真断言只是长期停滞检测。 |
| CMO All | invalid line 可在 S3 drop；valid line 等 MSHR CMO response，再延两拍给 <code>cmoLineDone</code> | 分支相关、配置相关；默认 V2 配置的 <code>enableFlush=false</code>，不要把 optional port 当必经路径。 |

MainPipe 的 CMO optional IO 和 done 计算见 [MainPipe.scala:119-123](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:119) [MainPipe.scala:1038-1046](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:1038)；配置默认 <code>enableFlush=false</code> 见 [Configs.scala:278-321](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:278)。

### 8.2 吞吐上界与限制

1. 对可访问 DataStorage 的普通请求，S1 后的 <code>ds_mcp2_stall</code> 会阻止紧邻下一拍再接受普通任务，因此不能宣称该 Slice 每拍无条件接收一条缓存事务。
2. DataStorage 单端口且 <code>assert(!io.en || !RegNext(io.en))</code>，读/写共享同一端口；同 set 或不同 set 都不能绕过这一物理端口约束。
3. Directory 的 tag/meta/replacer 同样存在读写互斥；写目录时新 Directory read 不 ready。
4. 16 个 MSHR、MSHRBuffer 的 16 entry、GrantBuffer/TX 的按 <code>mshrsAll</code> 队列只是每 Slice 的并发资源上限，实际入口还受 B 预留、GrantAck、CHI credits 和队列预测计数限制。
5. S3/S4/S5 可以同时保存不同任务，但对每一个 D/TX physical output，arb 一拍只服务一个候选；它是出口吞吐限制而不是任务“消失”。

因此最合适的结论是：MainPipe 是可重叠的事务流水，但其稳态 initiation interval 对普通数据阵列访问至少受 MCP2 的隔拍约束；命中延迟、miss 延迟和端到端 CPU load latency 都是路径相关，当前源码证据不支持把它们写为一个常数。

## 9. 面向验证的检查表

以下检查点来自实际条件、valid 生命周期和显式断言；它们适合波形、随机测试或形式性质，而不是泛泛的“测 hit/miss”建议。

| ID | 场景与刺激 | 应观察的源码条件 | 关键预期 / 不变量 | 相关证据 |
| --- | --- | --- | --- | --- |
| V1 | reset 后首个 A 请求 | RequestArb/MainPipe 的 <code>resetFinish</code>、Directory reset meta write | reset 扫描完成前不接受通道任务；每 set 所有 way meta 被清空。 | [MainPipe.scala:127-135](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:127) [MainPipe.scala:594-606](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:594) |
| V2 | A、B、C 同拍 valid | <code>sinkValids</code>、ready、<code>task_s1</code> | C 获得准入；若无 C 则 B；若无 B/C 才 A；已有回流 MSHR task 更优先。 | [RequestArb.scala:145-169](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) |
| V3 | 连续两个非 Hint 请求 | <code>s1_fire</code>、<code>ds_mcp2_stall</code>、<code>s2_ready</code> | 第一项进入 S2 后，下一拍普通入口不能 fire；DataStorage 无连续 <code>en</code>。 | [RequestArb.scala:199-217](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:199) [DataStorage.scala:124-131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:124) |
| V4 | Directory read 与 meta/tag write 同拍 | <code>read.ready</code>、<code>metaWReq</code>、<code>tagWReq</code> | 写优先，读不被接受；不能产生同端口 read/write 未定义行为。 | [Directory.scala:322-360](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:322) |
| V5 | 16 项 MSHR 接近满，随后 A 与 B 到达 | <code>mshrFull</code>、<code>a_mshrFull</code> | A 在剩一项时被阻塞，B 只在全满时被阻塞。 | [MSHRCtl.scala:106-166](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:106) |
| V6 | refill victim 的所有 way 被占用 | <code>freeWayMask_s3</code>、<code>replResp.retry</code> | 不写 tag；MSHR 进入 retry/backoff 并重试目录。 | [Directory.scala:255-346](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:255) [MSHR.scala:1256-1266](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1256) |
| V7 | B snoop 与正在等待 GrantAck 的同地址 block | GrantBuffer <code>inflightGrant</code> 和 B block | B 不越过仍在飞的 Grant；确认 E GrantAck 后才能解除相应表项。 | [GrantBuffer.scala:265-326](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:265) |
| V8 | S3/S4/S5 同时有 D 或 TX 候选 | <code>Seq(s5,s4,s3)</code> 与 output valid/ready | 观察每条出口的 arb 不重复发同一 task；早期 fire 后的 S4/S5 被 <code>chnl_valid</code> 排除。 | [MainPipe.scala:759-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:759) [MainPipe.scala:1023-1031](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:1023) |
| V9 | tag 或 data ECC error | Directory error、<code>toDS.error_s5</code>、D/TXDAT corrupt、<code>io.error</code> | 错误被合入响应和 Slice error；验证不应假设直接产生 Difftest 事件。 | [MainPipe.scala:850-907](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:850) [MainPipe.scala:1033-1036](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:1033) |
| V10 | snoop 长时间被 conflict block | RXSNP <code>stallCnt</code> | 队首无 fire 时计数增长，超过阈值触发断言；同时检查真正阻塞来自 req/replace/CMO 哪一类。 | [RXSNP.scala:57-129](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:57) |

## 10. 总结与仍待波形确认的问题

1. Kunminghu V2 的有效目标是 CHI CoupledL2 的 <code>tl2chi/MainPipe</code>。HuanCun 和 L1D 的同名模块不能混入这条实现链。
2. MainPipe 是 Directory/DS/MSHR/响应通道的汇合点，但入口准入属于 RequestArb 与周边资源反馈；不要对 Valid-only 输入杜撰 ready/fire。
3. S3 是主要决策点：分类事务、分配 MSHR、选 DS 地址/数据、更新 Directory、产生 one-hot 输出。S4/S5 将长组合和 MCP2 数据返回分离。
4. 正确性依赖明确的单端口、same-set/tag、MSHR reserve、GrantAck、retry 和输出容量规则；这些都是可直接写成断言/波形检查的条件。
5. 未做 elaboration/FST 的部分包括：最终地址宽度、生成 RTL 中标准 Arbiter/FIFO 的逐拍细节、不同 Slice 的最终 CHI arbitration，以及 error 到 SoC/BEU/Difftest 的终点。本文已经指出其应继续追踪的代码边界，而没有把未知部分写成事实。
