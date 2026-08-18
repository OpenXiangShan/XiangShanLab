# Cache-SinkC：昆明湖 V2 的 CoupledL2 与 HuanCun C 通道接收路径

> 本文以给定的昆明湖 V2 源码为唯一行为依据。Design Doc 只用于追踪设计意图，所有“实现为……”的结论均可回溯到本机检出的 Chisel 源码。文中的 C 通道是 TileLink 的 Release／ReleaseData／ProbeAck／ProbeAckData 通道，而不是 CHI 的 C 通道。

## 1. 结论先行

1. 指定的 KunminghuV2Config 打开 EnableCHI，因此实际启用的二级缓存是 TL2CHICoupledL2，而不是 HuanCun L3。本文以 coupledL2 的 tl2chi.SinkC 为主对象；HuanCun 的两套 SinkC 作为同一源码树中、非 CHI 配置下的对照实现单列分析。
2. CoupledL2 SinkC 把入站 C 消息按 opcode 的两个低位分成两类：Release 类进入 4 项本地缓冲和 RequestArb，ProbeAck 类不占该任务缓冲而直接反馈给 MSHRCtl；ProbeAckData 还写入 ReleaseBuffer。
3. ReleaseData 的数据先在 SinkC 中按 beat 汇集，末拍才产生内部任务。任务被 RequestArb 接收后，下一拍从数据缓冲读出整行给 MainPipe；MainPipe 在目录命中、且该 Release 是“from T 且有数据”时写 DataStorage，并形成 ReleaseAck。
4. 这不是一条固定周期的端到端路径。入口会受 SinkC 缓冲、目录读端口、Reset、MSHR 任务、MCP2 单端口 DataStorage、同 set 阻塞和 GrantBuffer 容量共同反压。
5. HuanCun 的非包容 SinkC 有不同职责：它把 C 请求交给专用或嵌套 MSHR，利用 save／through 两组 beat-valid 同时支持写本地 DataStorage 与向下游 C 透传；不能把这条非 CHI L3 路径误称为 KunminghuV2Config 的实际后端。

## 2. 分析边界与可复现基线

| 项目 | 本次基线 | 影响 |
|---|---|---|
| 主仓库 | /home/yanyusong/xs-memory-env/XiangShan，分支 kunminghu-v2，提交 e12436c7cba86b195deec24981976d78bc263661 | 本文所有 RTL 行号以此为准。工作树已有 difftest 修改和 src/main/resources/aia/ 未跟踪内容，本文未修改它们。 |
| CoupledL2 子模块 | 提交 fb5469838c8902b6cb33992c0a30ee3d446e4453 | 主分析对象。 |
| HuanCun 子模块 | 提交 65ef077373ecf398b4cecdea06b65ef9b8d79044 | 仅作非 CHI L3 对照。 |
| Design Doc | /home/yanyusong/XiangShan-Design-Doc，分支 kunminghu-v2，提交 58d9e2ad11f044cb6f8887d9687d9e110696d1aa | 用于意图追踪及版本差异发现，不作为 RTL 事实来源。 |
| 每周同步守卫 | 已调用当前技能的 weekly_sync.py；上次同步不足 7 天，脚本按策略跳过 | 未执行 fetch、pull 或对子模块更新，保证以上提交可复现。 |

### 2.1 配置判定：为何主线是 tl2chi.SinkC

KunminghuV2Config 把 WithCHI 纳入配置，并为 L2 设置 1 MiB、4 bank、inclusive；WithCHI 令 EnableCHI 为真。[Configs.scala:477](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:477) [Configs.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481)

L2Top 据 EnableCHI 在 TL2CHICoupledL2 和 TL2TLCoupledL2 间选择。当前配置落在前者；每个 bank 由一个 Slice 承担。[L2Top.scala:111](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:111) [CoupledL2.scala:307](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:307)

相反，L3CacheConfig 只在非 CHI 条件下生成 HuanCun 参数，顶层也仅在 L3CacheParamsOpt 存在时实例化 HuanCun。因此“CoupledL2 经 TileLink 接 HuanCun”是源码支持的非 CHI 拓扑，但不是本节配置的实际后端。[Configs.scala:346](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:346) [Top.scala:111](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Top.scala:111)

~~~mermaid
flowchart LR
  L1["L1 DCache / 其他 TL client"]
  L1 -->|"inner TL C"| IC["inBuf.c"]
  IC --> SC["coupledL2.tl2chi.SinkC"]
  SC -->|"Release task"| RA["RequestArb"]
  RA -->|"s2 task"| MP["MainPipe"]
  MP --> DIR["Directory / DataStorage"]
  MP --> GB["GrantBuffer / TL D ReleaseAck"]
  SC -->|"ProbeAck resp"| MC["MSHRCtl / MSHR"]
  SC -->|"ProbeAckData"| RB["ReleaseBuffer"]
  SC -->|"nested ReleaseData"| RFB["RefillBuffer"]
  MP --> CHI["TXREQ / TXRSP / TXDAT to CHI"]
~~~

### 2.2 代码入口与 “谁、为何、如何、从哪来、到哪去”

| 问题 | CoupledL2 的代码事实 |
|---|---|
| 谁实例化 | tl2chi.Slice 直接实例化 SinkC，并把它接入 Directory、RequestArb、MainPipe 与 MSHRCtl。[Slice.scala:39](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:39) |
| 从哪来 | Slice 将 sinkC.io.c 接到 inBuf.c(io.in.c)，即上游 TileLink C 通道。[Slice.scala:197](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:197) 一个可追到的生产者是 DCacheWrapper 的 WritebackQueue：DCache MainPipe 的写回请求经 WBQ 汇入 bus.c。[DCacheWrapper.scala:1610](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1610) |
| 为何存在 | C 的主动 Release 与对 Probe 的 Ack 的后续动作不同：前者需要目录／数据阵列流水线，后者要解除等待 ProbeAck 的 MSHR；SinkC 的类注释与两个输出接口明确分开了这两种路径。[SinkC.scala:31](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:31) |
| 如何接收 | io.c 是 Flipped(DecoupledIO(TLBundleC))；以 valid && ready 为一次 beat 传输，以 edgeIn.count 给出 first、last、beat。[SinkC.scala:35](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:35) [SinkC.scala:46](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:46) |
| 到哪去 | Release task 接 RequestArb；ProbeAck resp 接 MSHRCtl；ProbeAckData 写 releaseBuf；嵌套 ReleaseData 可写 refillBuf；已缓冲 ReleaseData 经 bufResp 交 MainPipe。[Slice.scala:97](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:97) [Slice.scala:113](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:113) [Slice.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:145) |

## 3. 课程理论到当前 RTL 的映射

课程中的 Cache line、set、way、目录、非阻塞缓存与 TileLink A/B/C/D/E 的定义可作为阅读框架，但下面的映射只依赖当前实现。[15_XSCache.md:27](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/15_XSCache.md:27) [15_XSCache.md:131](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/15_XSCache.md:131)

| 理论概念 | 在 SinkC 周围的具体落点 | 不能由此推出的结论 |
|---|---|---|
| TileLink C 为上行缓存向管理者的 Release／ProbeAck 通道 | SinkC 以 opcode[1] 判 Release 类、opcode[0] 判是否携带数据；正式 opcode 名称来自 TLMessages。[SinkC.scala:46](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:46) | 不能把本地 C/B/A 仲裁优先级等同于 TileLink 全局通道优先级。 |
| valid/ready 背压 | Release 首拍只有缓冲未满才可 ready；已开始消息的非首拍保持可接收。[SinkC.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:186) | ready 不是“整个事务已完成”，只表示该 beat 被接受。 |
| 非阻塞 Cache 的资源限制 | MSHR、目录读端口、DataStorage MCP2、GrantBuffer 都会参与反压。[RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) [GrantBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:292) | 有 MSHR 不代表 C 消息永不阻塞。 |
| Cache line 的 beat 化传输 | L2 块为 64 B、通道 beat 为 32 B，RequestArb 强制 beatSize == 2；SinkC 把两个 beat 合成一条内部任务。[L2Param.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:65) [RequestArb.scala:276](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:276) | 不能从这一特定配置推导所有 XiangShan 配置也固定两拍。 |
| inclusive L2 的目录与数据更新 | MainPipe 的 C 分支用目录 hit 决定 DS／Meta 写入，ReleaseAck 的生成与 MSHR 分配分离。[MainPipe.scala:424](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:424) [MainPipe.scala:487](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:487) | 不应把“Release 一定命中”的旧设计说明当作未条件化的 tl2chi 断言。 |

## 4. 参数、地址和存储资源

### 4.1 当前配置的可计算量

L2CacheConfig 的默认 ways 是 8，KunminghuV2Config 指定 1 MiB、4 bank，因此每个 Slice 的 sets 为 1 MiB / 4 / 8 / 64 B = 512。CoupledL2 的 Slice 数由 in 端口数决定，并用 bankBits 编码 Slice。[Configs.scala:278](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:278) [Configs.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481) [CoupledL2.scala:307](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:307)

| 项目 | 值或表达式 | 源码依据 |
|---|---|---|
| blockBytes | 64 B | L2Param 默认值，当前 L2CacheConfig 未覆盖该字段。[L2Param.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:65) |
| channelBytes | 32 B | 同上。 |
| beatSize | 2 | 64 / 32，且 RequestArb 有 require。 |
| Slice 数／bankBits | 4／2 | 配置的 banks = 4；CoupledL2 用 log2Ceil(banks)。 |
| 每 Slice set 数／setBits | 512／9 | 配置表达式 l2sets 与以上代入。 |
| way 数／wayBits | 8／3 | L2CacheConfig 默认 ways = 8。 |
| SinkC Release 缓冲 | bufBlocks = 4 项 | 固定常量，非由 MSHR 数自动推导。[CoupledL2.scala:71](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:71) |
| MSHR | 16（L2Param 默认） | 当前 L2CacheConfig 未覆盖 mshrs。[L2Param.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:65) |

SinkC 调用 parseAddress。该函数先按 offsetBits 和 bankBits 右移得到局部 set，再按 setBits 右移得到 tag；故此基线下可读作 offset 为地址[5:0]、bank 为[7:6]、局部 set 为[16:8]、tag 为其上位。restoreAddress 会在 Slice 号位置复原 bank 位。[CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) [CoupledL2.scala:197](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:197)

### 4.2 SinkC 内部存储与所有权

| 存储／寄存器 | 容量与 valid 定义 | 写入时机 | 消费／释放时机 |
|---|---|---|---|
| dataBuf | 4 × beatSize × 32 B；对应 beatValids | ReleaseData 的 first 写 nextPtr，末拍写 nextPtrReg | io.task.fire 后采样给 bufResp；下一拍清该项所有 beatValids。 |
| taskBuf | 4 个 TaskBundle；taskValids 为任务可仲裁标志 | Release 的 last beat | RRArbiter 对应输入 fire 时清 taskValids。 |
| probeAckDataBuf | 1 个 32 B 暂存寄存器 | ProbeAckData 的 first beat | ProbeAckData 的 last beat 与当前 data Cat 成整行，写 releaseBuf。 |
| ReleaseBuffer | 每 MSHR 一项的 MSHRBuffer，3 写端口 | SinkC、MainPipe nested、MainPipe s5 可写 | RequestArb 按 MSHR id 读取。 |
| RefillBuffer | 每 MSHR 一项的 MSHRBuffer，2 写端口 | RXDAT 或 SinkC 的新 ReleaseData 覆盖 | RequestArb／MSHR 读出后用于后续动作。 |

以上 dataBuf、taskBuf、beatValids、taskValids 的初始化和占用定义都在 SinkC 中；每个 buffer entry 的占用是 taskValid 或任一 data beat valid。ReleaseBuffer 的 3 写端口和 RefillBuffer 的 2 写端口由 Slice 显式连线。[SinkC.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:50) [Slice.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:145) [Slice.scala:165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:165)

### 4.3 valid 生命周期、读取与冲突边界

| 状态 | reset | 置位 | 保持／清除 | 观察者与冲突结论 |
|---|---|---|---|---|
| beatValids[i][beat] | RegInit false | 对应 ReleaseData beat 的 c.fire | 在 task 未 fire 时保持；RegNext(task.fire) 时清整个 bufIdx 的所有 beat | dataValids、bufValids、full 观察它。dataBuf 在 task.fire 后才被读出，任务又只在最后 beat 入队，因此正常协议下同一项不应与尚未完成的 C 写并发读；该顺序来自控制，不是独立 RAW bypass。 |
| taskValids[i] | RegInit false | Release 的 last c.fire；有数据时使用 nextPtrReg，无数据时用 nextPtr | taskArb 对应输入 fire 时清；io.task.ready 低时保持 | taskArb 输入和 bufValids 观察它。taskValid 虽先于 beatValid 清除，但 dataValid 继续占位一个寄存阶段，防止同拍复用。 |
| nextPtrReg | RegEnable 的复位值为 0；只有有效 first fire 后其索引语义才成立 | isRelease && first && hasData && c.fire | 跨第二拍保持 | 它解决两 beat 索引一致性。若上游违反“多 beat 不交织”假设，dataBuf 的地址归属将不再由 SinkC 保护。 |
| probeAckDataBuf | RegEnable 的初值为 0 | ProbeAckData 的 first（注意此处用 valid 而非 fire） | 保持到 PData last 进行 Cat | 仅为同一两拍 PData 拼行；该实现假定正常协议的连续拍，不提供独立 valid 标记。 |

同一 MSHRBuffer entry 的多写是唯一在本模块边界明确可见的写写冲突：三个 writer 的有效位形成 wens，PriorityMux 选中一个数据／mask，且仅对 PopCount(wens) <= 2 断言。因此“二写时谁赢”是 PriorityMux 输入顺序的实现语义，必须在集成验证中锁定；不能把三写断言误解为“任何并发写都被拒绝”。[MSHRBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39)

## 5. CoupledL2 SinkC 的控制与生命周期

### 5.1 opcode 分流、数据汇集和 ready

~~~scala
val (first, last, _, beat) = edgeIn.count(io.c)
val isRelease = io.c.bits.opcode(1)
val hasData = io.c.bits.opcode(0)

val bufValids = taskValids.asUInt | dataValids
val full = bufValids.andR
val nextPtr = PriorityEncoder(~bufValids)

io.c.ready := !isRelease || !first || !full
~~~

以上是当前 RTL 的原文核心，见 [SinkC.scala:46](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:46) 与 [SinkC.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:186)。含义如下：

- Release 或 ReleaseData：只在首拍检查 4 项缓冲是否满；若已经开始多 beat 消息，后续 beat 不因 full 重新撤回 ready。
- ProbeAck 或 ProbeAckData：isRelease 为假，因此 C ready 恒为真。它们不进入 taskBuf。
- ReleaseData 的跨拍索引由 nextPtrReg 在首拍 fire 时锁存。代码明确假定 DCache 的 TLArbiter 使一个块的两个 beat 连续发送、不同地址不交织。[SinkC.scala:63](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:63)
- 对无数据 Release，last beat 可直接占用 nextPtr 的 taskBuf；对有数据 Release，只有 last beat 把同一 nextPtrReg 的 taskValid 置位。这保证 MainPipe 不会读到缺少末拍的数据。

~~~mermaid
stateDiagram-v2
  [*] --> Empty
  Empty --> CollectData: ReleaseData first && c.fire
  Empty --> Queued: Release(no data) && last && c.fire
  CollectData --> CollectData: non-last data beat
  CollectData --> Queued: last && c.fire
  Queued --> Issued: taskArb.out.fire
  Issued --> Empty: next cycle; clear beatValids
~~~

上图只描述有状态的 Release buffer 生命周期。ProbeAck／ProbeAckData 不是该状态机的一个寄存状态：io.resp.valid 是 c.valid && (first || last) && !isRelease 的组合 Valid 输出；ProbeAckData 仅在 last 时产生 ReleaseBuffer 写请求。[SinkC.scala:147](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:147) [SinkC.scala:160](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:160)

### 5.2 Release 任务和 RequestArb 的局部优先级

SinkC 的 taskBuf 输入进入 RRArbiterInit；只有仲裁输出与 io.task.ready 同时成立时，对应 taskValid 才清零。[SinkC.scala:132](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:132) 这意味着 taskValid 清除与 dataBuf 清除不是同拍：后者经 RegNext，在 task fire 的下一拍清除，同时 bufResp 将选中行送往 MainPipe。[SinkC.scala:188](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:188)

RequestArb 对已到达的 C/B/A 内部任务采用 C > B > A 的固定优先级，且只有目录读端口 ready、reset 完成、没有占用的 MSHR task、s2_ready 时才给 sinkC.ready。[RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145)

~~~scala
val sinkValids = VecInit(Seq(
  io.sinkC.valid && !block_C,
  io.sinkB.valid && !block_B,
  io.sinkA.valid && !block_A
)).asUInt
io.sinkC.ready := sink_ready_basic && !block_C
chnl_task_s1.bits := ParallelPriorityMux(sinkValids, Seq(C_task, B_task, A_task))
~~~

这是 Slice 内的局部入口优先级，不替代跨通道的 TileLink 协议优先级。当前课程材料也把这两层明确区分。[15_XSCache.md:170](/home/yanyusong/XiangShanLab/xiangshan-course/docs/xiangshan-microarchitecture/Beginner_Implementation_and_Principles_of_the_High_Performance_Xiangshan_Processor/15_XSCache.md:170)

### 5.3 ProbeAck 与 ProbeAckData 的 MSHR 路径

SinkC 对非 Release 在 first 或 last 位置产生 resp，并携带 tag、set、opcode、param、last、dirty、denied、corrupt。MSHRCtl 只把这份 resp 送给 status.w_c_resp 为真且 set/tag 匹配的 MSHR；tag 的取值依赖该 MSHR 是否 needsRepl。[SinkC.scala:147](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:147) [MSHRCtl.scala:124](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:124)

ProbeAckData 的 first beat 被暂存，last beat 时：

~~~scala
io.releaseBufWrite.valid := io.c.valid &&
  io.c.bits.opcode === ProbeAckData && last
io.releaseBufWrite.bits.data.data := Cat(io.c.bits.data, probeAckDataBuf)
io.releaseBufWrite.bits.beatMask := Fill(beatSize, true.B)
~~~

Slice 将该写请求的 id 覆盖为 MSHRCtl 比对出来的 releaseBufWriteId，再与 nested／MainPipe 写端口共同接入 3 写端口 MSHRBuffer。[SinkC.scala:160](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:160) [Slice.scala:151](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:151)

风险边界：MSHRBuffer 对同一 id 的同时写使用 PriorityMux，并仅断言“同一项三写”不发生；SinkC 本身没有断言“newdataMask 恰有一位”。验证应把同 id 双写覆盖优先级、以及 set/tag 匹配唯一性作为显式性质，而不是把它们当作已被本模块证明的事实。[MSHRBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39)

### 5.4 ReleaseData 覆盖尚未写回的数据

当一个已进入 task 的 ReleaseData 命中有效 msInfo 的同 tag、set 且 blockRefill 为真时，SinkC 在 task.fire 后延一拍把 dataBuf 的完整缓存行写入 RefillBuffer。这样处理的是“L1 新 ReleaseData 已到，而旧 refill 数据尚等待后续 Release／写 DataStorage”的顺序竞争。[SinkC.scala:169](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:169)

这是一种以新数据覆盖旧 refill 数据的局部修正，不等价于泛化的同地址排序器。它依赖 MSHR 信息和协议时序；没有波形或形式证明时，不能进一步宣称它覆盖所有重复 Release、不同 bank 或异常中断组合。

## 6. 从 RequestArb 到 MainPipe 的阶段表

| 观察阶段 | 输入／保持条件 | 主要动作 | 输出或阻塞证据 |
|---|---|---|---|
| C 入站 beat | io.c.valid && io.c.ready | first/last/beat 计数；ReleaseData 写 dataBuf；末拍写 taskBuf | full 时阻塞新 Release 首拍；ProbeAck 仍可接收。[SinkC.scala:107](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:107) |
| SinkC task 仲裁 | taskValids 中有项且 RequestArb ready | RRArbiter 输出一条 Release task | task fire 后任务 valid 清，数据有效延后清。 |
| RequestArb s1 | sinkC.fire、目录读可接收 | C 优先选择；发目录读；记录 s1 入口 set | C 与 B/A、MSHR task、同 set block 竞争。[RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) |
| RequestArb s2 | s1_fire | task_s2 寄存；交给 MainPipe | ds_mcp2_stall 是上一拍非 Hint s1_fire 的寄存值，令 s2_ready 拉低一拍。[RequestArb.scala:199](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:199) |
| MainPipe s3 | task_s2 寄存到 task_s3；Directory 返回 | C 不走 need_mshr；生成 ReleaseAck；根据目录决定 DS／Meta 写 | C Data 写仅在 fromT、opcode 有数据、dir hit；Meta C 写也要求 hit。[MainPipe.scala:469](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:469) |
| D 输出 | MainPipe C response 通过 GrantBuffer | 向内侧 TL D 返回 ReleaseAck | GrantBuffer 容量可向 RequestArb 反压 C 入口。[GrantBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:292) |

MainPipe 对 C 的关键数据／目录控制如下。条件中的 isParamFromT、opcode(0)、dirResult_s3.hit 都是源码条件，不能省略：

~~~scala
val wen_c = sinkC_req_s3 && isParamFromT(req_s3.param) &&
  req_s3.opcode(0) && dirResult_s3.hit
val metaW_valid_s3_c = sinkC_req_s3 && dirResult_s3.hit
sink_resp_s3.bits.opcode := ReleaseAck
~~~

数据阵列是单端口 MCP2；MainPipe 为保持请求两拍专门维护 task_s3_valid_hold2，DataStorage 也断言连续请求和载荷不应随意变化。[MainPipe.scala:487](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:487) [DataStorage.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/DataStorage.scala:50)

## 7. Design Doc 追踪矩阵与版本差异

本节不是把 Design Doc 当作实现依据，而是逐项检查它在当前子模块版本中的落点。文档基线见第 2 节。

| Doc 条目 | 设计文档表述的意图 | 当前代码证据 | 结论 |
|---|---|---|---|
| SinkC 接收四种 C 消息 | 接收 Release、ReleaseData、ProbeAck、ProbeAckData，并按请求／响应分流。[SinkC.md:4](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/upstream/SinkC.md:4) | SinkC 用 isRelease 与 hasData 进行位级分类；类注释给出两条处理路径。[SinkC.scala:31](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:31) [SinkC.scala:46](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:46) | 已核对。 |
| Release 缓冲深度 | Doc 写“内部 Buffer 深度为 3”。[SinkC.md:4](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/upstream/SinkC.md:4) | 当前 HasCoupledL2Parameters 固定 bufBlocks = 4。[CoupledL2.scala:71](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:71) | **版本不一致**。本文按 RTL 的 4 项写，不沿用 3 项。 |
| Release 转内部 task | Doc 说 Release(Data) 缓存后送 RequestArb。[SinkC.md:5](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/upstream/SinkC.md:5) | 最后 beat 设置 taskValids，Slice 把 io.task 接 RequestArb。[SinkC.scala:120](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:120) [Slice.scala:97](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:97) | 已核对。 |
| ProbeAckData 写 ReleaseBuffer | Doc 说 PData 回应 MSHR 并写 ReleaseBuf。[SinkC.md:7](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/upstream/SinkC.md:7) | SinkC 拼接两拍 PData，Slice 注入 MSHRCtl 给出的 id 后写 releaseBuf。[SinkC.scala:160](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:160) [Slice.scala:151](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:151) | 已核对；“直接”应理解为经 MSHRCtl 的 set/tag 匹配后路由。 |
| 新 ReleaseData 覆盖 refill | Doc 描述在 refill 尚未写 DS 时以更新数据覆盖旧 refill。[SinkC.md:9](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/upstream/SinkC.md:9) | newdataMask 匹配 blockRefill，task.fire 后 RegNext 写 refillBuf。[SinkC.scala:169](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:169) | 已核对，范围是该段代码的 blockRefill 条件。 |
| CoupledL2 Release 流 | Doc 概述 SinkC → 请求仲裁 → 目录／DS → ReleaseAck。[CoupledL2.md:145](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/CoupledL2.md:145) | RequestArb C 优先、MainPipe C ReleaseAck、hit 条件 DS／Meta 写均存在。[RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [MainPipe.scala:424](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:424) | 部分核对：当前 tl2chi 代码对 DS／Meta 写显式加 dirResult.hit 条件。 |
| C 通道错误编码 | Doc 说明 C 没有 denied，需用 opcode 区分 corrupt 的含义。[Error.md:66](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/Error.md:66) | toTaskBundle 与 respInfo 都实现相同的两组条件。[SinkC.scala:67](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:67) [SinkC.scala:147](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:147) | 已核对，但下文以当前 RTL 的布尔式重述，不依赖文档原句。 |

## 8. HuanCun 对照：同名 SinkC 的不同所有者与数据路径

### 8.1 为什么需要单列，而不能混入主线

HuanCun 的 Slice 在 elaboration 时按 cacheParams.inclusive 选择 inclusive.SinkC 或 noninclusive.SinkC；它接收自己的 inner TL C，并将 SinkC 的 direct release 与 SourceC 的 C 输出合并到 outer C。[Slice.scala:59](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:59) [Slice.scala:85](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:85)

~~~mermaid
flowchart LR
  IC["HuanCun inner C"] --> HSC["HuanCun SinkC"]
  HSC --> AL["MSHRAlloc / C 专用 MSHR"]
  AL --> HM["HuanCun MSHR"]
  HM --> T["SinkCReq: save / release / drop"]
  T --> HSC
  HSC -->|"save"| HDS["DataStorage"]
  HSC -->|"through"| OC["outer TL C"]
  SC2["SourceC"] --> OC
  OC["TLArbiter.lowest"] --> L3N["下级 TL manager"]
~~~

它与 CoupledL2 的主差异不是“是否也有 C 通道”，而是任务所有权：

| 维度 | 当前 KHV2 的 coupledL2.tl2chi.SinkC | HuanCun noninclusive.SinkC |
|---|---|---|
| 生效条件 | KunminghuV2Config + EnableCHI | 非 CHI 的 HuanCun L3 配置 |
| Release 主任务去向 | SinkC taskBuf → RequestArb → MainPipe | Release 首拍 → MSHRAlloc → MSHR 的 sink_c task |
| C response 去向 | SinkC resp → MSHRCtl，PData → ReleaseBuffer | SinkC resp 按 set 送 MSHR；PData 可利用本地缓冲执行 save／through |
| 向下 C | 不直接从 SinkC 发送；当前 Slice 的下行是 CHI TXREQ/TXRSP/TXDAT | SinkC.io.release 直接参与 outer TL C 仲裁，优先于 SourceC |
| 数据缓冲语义 | 4 项 Release task/data 槽，按 task fire 清空 | 每项有 save 与 through 两套 beat-valid，可并发完成本地保存和向外透传 |

### 8.2 HuanCun 公共接口与容量

BaseSinkC 显式给出 inner C 输入、MSHR alloc、MSHR response、SinkC task／taskack、DataStorage 写地址／数据、SourceD 读冲突和 outer C release 接口。[BaseSinkC.scala:27](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseSinkC.scala:27)

HuanCun 参数中 mshrsAll = mshrs + 2，而 sinkCbufBlocks = mshrsAll，注释直接说明该深度用于避免 SinkC buffer 死锁；这与 CoupledL2 固定 4 项不同。[HuanCun.scala:49](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/HuanCun.scala:49) [HuanCun.scala:69](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/HuanCun.scala:69)

### 8.3 inclusive.SinkC：单个 active task 与按行检查

inclusive.SinkC 也有 releaseBuf、每 beat valid 与每项 bufValid，但其任务控制是一个 busy_r 加 task_r 的单 active-task 结构：busy_r 仅在 banked-store 的 w_done 时清除。虽然 buffer 清理条件也写有 task_r.drop，但 inclusive.MSHR 产生的任务固定为 save=true、drop=false、release=false，因此本实现的有效路径是完成本地写后回收，而不是 noninclusive 版的 save／through 双路径实现。[inclusive/SinkC.scala:14](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/SinkC.scala:14) [inclusive/SinkC.scala:30](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/SinkC.scala:30) [inclusive/MSHR.scala:461](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/MSHR.scala:461)

~~~scala
val can_recv_req = Mux(first, io.alloc.ready && !noSpace, !noSpace)
val can_recv_resp = Mux(do_release, false.B, !hasData || io.bs_waddr.ready)
c.ready := Mux(isResp, can_recv_resp, can_recv_req)

assert(!c.valid || (c.bits.size === log2Up(blockBytes).U && off === 0.U))
io.task.ready := first && !busy_r && task_w_safe
~~~

其语义是：

- Release 首拍既要有 buffer 空间，又要能把 alloc 送给 MSHR；ReleaseData 各 beat 写入 releaseBuf，末拍才置 bufValids。[inclusive/SinkC.scala:44](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/SinkC.scala:44) [inclusive/SinkC.scala:84](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/SinkC.scala:84)
- ProbeAck／ProbeAckData 是 resp 路径；带数据回应可直接竞争 banked-store 写端口。若已有一个 release task 正在执行，can_recv_resp 为假，避免两个来源在该实现中同时驱动该端口。[inclusive/SinkC.scala:44](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/SinkC.scala:44) [inclusive/SinkC.scala:122](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/SinkC.scala:122)
- 它对每个 C valid 强制整行 size 与 off==0。这是 HuanCun inclusive 版本独有的代码检查，不能用来说明 CoupledL2 SinkC 也具有相同断言。

### 8.4 noninclusive.SinkC 的隐式状态机

| 状态／条件 | 寄存内容 | 转移与完成 |
|---|---|---|
| 空闲 | beatValsSave、beatValsThrough、busy、两个写计数器均无有效任务；数据 RAM 本身不 reset | 首拍以 PriorityEncoder 选择空槽。 |
| 收集 C 数据 | buffer、tag/set 与两套 beat-valid | 有数据 C 的每个 fire 同时置 save 和 through valid；首拍锁存 insertIdxReg 给后续 beat。 |
| 等 MSHR 决策 | alloc 已接受 Release，或 resp 已送入等待 MSHR | MSHR 发 SinkCReq，task 仅在无 busy 且不存在 SourceD 同 way 读冲突时 ready。 |
| 执行 save／through／drop | task_r 与 busy 保持任务；save 与 through 各有一个 beat counter | save 写 DataStorage；through 向 outer C 发；drop 只回收。两个路径都完成，或 drop，才统一清 valid 和 busy。 |
| 完成反馈 | taskack | busy 完成／drop 后下一拍向对应 MSHR 发 ack。 |

这些状态都来自控制寄存器，而非源码里的 Enum。具体有效位、首拍反压、buffer 写入、任务执行和 ack 分别见 [noninclusive/SinkC.scala:13](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:13)、[noninclusive/SinkC.scala:51](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:51)、[noninclusive/SinkC.scala:96](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:96)、[noninclusive/SinkC.scala:139](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:139)、[noninclusive/SinkC.scala:197](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:197)。

~~~scala
c.ready := Mux(first, !noSpace && !(isReq && !io.alloc.ready), true.B)
io.task.ready := !busy && task_w_safe
io.bs_waddr.valid := busy && task_r.save && !w_save_done_r
io.release.valid := busy && task_r.release &&
  beatValsThrough(task_r.bufIdx)(w_counter_through) && !w_through_done_r
~~~

这段逻辑说明两个要点：

- HuanCun 也不在多 beat 已开始后撤回 C ready；但 Release 首拍除空间外还需要 alloc.ready。
- save 与 release 是两个独立推进的 beat 计数器，因此它支持“同一缓存行同时向本地保存、向下游发送”的结构性并发；这不是 CoupledL2 SinkC 的功能。

ProbeAckData 的首拍还会记录 tag/set 并寻找旧缓冲项，代码断言最多一个匹配，随后清除旧项的 save／through valid。这是 HuanCun noninclusive 特有的去重规则。[noninclusive/SinkC.scala:115](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:115)

### 8.5 HuanCun C 的 MSHR 分配、目录和资源冲突

MSHRAlloc 每周期断言至多分配一个条目，并在输入选择中采取 C > B > A。C 可使用普通空闲 MSHR、专用 c_mshr 或合规的 nestC；无目录读 ready 时 c_req.ready 也会降低。[MSHRAlloc.scala:57](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/MSHRAlloc.scala:57) [MSHRAlloc.scala:112](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/MSHRAlloc.scala:112) [MSHRAlloc.scala:122](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/MSHRAlloc.scala:122)

Slice 对 MSHR 产生的 sinkC task 采用 C 专用 MSHR、BC MSHR、普通 ABC MSHR 的优先结构；对 C response 按 set 分发，并为 SinkC 提供对应 way。这里的 Mux1H／set 匹配意图依赖分配／嵌套逻辑维持唯一匹配，本次没有进一步形式证明。[Slice.scala:465](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:465) [Slice.scala:533](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:533) [Slice.scala:574](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:574)

HuanCun DataStorage 的单端口优先级还会反压 SinkC 的 save；SourceD 正在读同一 set/way 时，task_w_safe 为假，SinkC 不接 task。这一冲突属于 HuanCun 自己的数据阵列控制，不能移植到 CoupledL2 的 MCP2 解释中。[noninclusive/SinkC.scala:131](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:131) [DataStorage.scala:134](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/DataStorage.scala:134)

## 9. 时序、吞吐与反压

### 9.1 可由代码确定的时序关系

| 边界 | 可确定关系 | 不能声称的固定时延 |
|---|---|---|
| C ReleaseData → taskValid | 仅 last beat 的 c.fire 置 taskValid；因此任务不早于末拍可仲裁 | 不知道上游何时发送第二拍，不能给出从 first beat 到 task 的常数周期。 |
| SinkC task.fire → bufResp | bufResp 是 RegNext(RegEnable(dataBuf(...), task.fire))；beatValid 清除也在 RegNext(task.fire) | 不应把此寄存关系误称为“同周期送入 MainPipe”。 |
| RequestArb s1 → s2 | s1_fire 后 task_s2 寄存；上一次非 Hint s1_fire 令 ds_mcp2_stall，从而本周期 s2_ready 低 | 不代表所有 C 事务严格隔一个周期才能进入，因为其他 block 条件、Hint 例外和队列积压都会改变可见间隔。 |
| s2 → s3 → D | MainPipe 将 task_s2 寄存到 task_s3，C 分支产生 ReleaseAck，再由 GrantBuffer 输出 | Directory、DS、GrantBuffer ready 和后续 D 握手均可延迟，所以源码没有给出端到端固定 latency。 |

### 9.2 背压来源和最大可持续性

1. **最先吸收突发的资源是 4 项 SinkC buffer。** 它允许 RequestArb 暂时不能接任务时继续收集若干完整 ReleaseData，但第 5 个有数据 Release 的首拍会被 backpressure。
2. **task 侧不是无条件每拍出队。** RequestArb 的 sink_ready_basic 同时依赖目录读、resetFinish、没有 mshr_task_s1 和 s2_ready；C 即使在 C/B/A 中优先，也会被这些公共条件挡住。
3. **DataStorage 造成结构性气泡。** RequestArb 为非 Hint 的 s1_fire 记录 ds_mcp2_stall，MainPipe 还要求 DS 请求保持两拍。故可报告“DS/MCP2 限制了连续入口”，但没有源码依据把全链路吞吐写成一个常数 transactions/cycle。
4. **同 set 及响应容量会向上游传播。** MainPipe 在 s2 有可能写目录的同 set C task 时拉 blockC_s1；GrantBuffer 用在途与队列计数的 noSpaceForSinkReq 拉 blockC_s1。[MainPipe.scala:909](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:909) [GrantBuffer.scala:292](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/GrantBuffer.scala:292)

### 9.3 两个可观察的握手波形

以下为验证刺激的严格 JSON，不是从仿真导出的波形。名字与当前端口／寄存器一致，fire 由 valid && ready 推出。

正常两 beat ReleaseData：

~~~waveform-draw
{
  "signal": [
    {"name":"clk","wave":"p......."},
    {"name":"io.c.valid","wave":"01100000"},
    {"name":"io.c.ready","wave":"01111111"},
    {"name":"io.c.fire","wave":"01100000"},
    {"name":"first","wave":"01000000"},
    {"name":"last","wave":"00100000"},
    {"name":"beatValids[idx]","wave":"01110000"},
    {"name":"taskValids[idx]","wave":"00011000"},
    {"name":"io.task.valid","wave":"00001000"},
    {"name":"io.task.ready","wave":"00001000"},
    {"name":"bufResp.data","wave":"00000100"}
  ],
  "config": {"hscale": 1}
}
~~~

四槽满时的新 Release 首拍停住，而已经开始的尾拍仍不能被截断：

~~~waveform-draw
{
  "signal": [
    {"name":"clk","wave":"p......."},
    {"name":"full","wave":"01110000"},
    {"name":"new ReleaseData first valid","wave":"01100000"},
    {"name":"io.c.ready","wave":"00111111"},
    {"name":"new first fire","wave":"00010000"},
    {"name":"already-started last valid","wave":"00001100"},
    {"name":"already-started last ready","wave":"11111111"},
    {"name":"already-started last fire","wave":"00001100"},
    {"name":"io.task.fire","wave":"00000010"}
  ],
  "config": {"hscale": 1}
}
~~~

## 10. 错误、异常与跨边界说明

### 10.1 C 通道错误映射

TileLink C bundle 没有 denied 域，当前代码依据消息种类把 c.corrupt 映射为内部 denied 或 corrupt：

~~~scala
task.corrupt := c.corrupt &&
  (c.opcode === ProbeAckData || c.opcode === ReleaseData)
task.denied := c.corrupt &&
  (c.opcode === ProbeAck || c.opcode === Release)
~~~

同样的映射也进入 io.resp.respInfo。[SinkC.scala:67](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:67) [SinkC.scala:147](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:147) 这只是当前模块的错误字段转换；下游如何最终报告、重试或 poison 需要沿 MSHR／D／CHI 路径继续核查，本文不作超出源码的推断。

### 10.2 虚拟页、跨 cache line、MMIO 与 redirect

| 边界 | SinkC 中可见的证据 | 结论与待验证点 |
|---|---|---|
| 虚拟页／TLB | SinkC 只接受物理 TLBundleC.address，TaskBundle 的 vaddr 被赋 0；无 TLB、PMP、PMA、PBMT 端口。[SinkC.scala:67](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:67) | 地址翻译不在 SinkC。需从产生 C 的 DCache／MMU 验证物理地址及属性；不能把该模块当作跨页拆分点。 |
| 跨 cache line | 当前 block/beat 为 64 B/32 B，RequestArb 要求两 beat；SinkC 仅按 C 消息的 first/last 收集，不做地址跨行拆分检查。[SinkC.scala:63](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:63) | 正常路径依赖 TileLink 消息和上游写回按行对齐。CoupledL2 SinkC 内没有额外 alignment assert；应在上游／TL monitor 处验证。 |
| MMIO／不可缓存 | SinkC 没有 mmio、uncache、PMA 或 redirect 控制输入；tl2chi 外层另有 MMIO bridge 相关结构，而 C 输入逻辑没有分支到它。[CoupledL2.scala:76](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:76) | 本文只能说“未在 SinkC 找到 MMIO C 特判”，不能断言系统没有 MMIO 路径。 |
| flush／分支 redirect | SinkC 的本地缓冲仅在 reset 初始化，接口没有 flush、kill、redirect 或取消信号。[SinkC.scala:35](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:35) | C Release 是一致性／写回操作而非前端可投机取消请求。其上游必须保证不会把已取消的投机状态转成不可撤销的 C 事务；该保证不在 SinkC 内。 |

HuanCun inclusive.SinkC 则在 C valid 时断言消息大小等于 blockBytes、off 为 0，说明其实现确实有显式的对齐边界；这不能反向补到 CoupledL2 SinkC 上。[inclusive/SinkC.scala:51](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/SinkC.scala:51)

## 11. 验证特别注意

下表把容易被“代码看起来合理”掩盖的假设转成可执行检查。属性名称只是建议，实际应按项目的 ChiselTest、ScalaTest、FST 或形式平台命名。

| ID | 场景／激励 | 风险／不变量与预期观察 | 建议 checker／coverage | 锚点 |
|---|---|---|---|---|
| C01 | 四项均占用后发送新 ReleaseData 首拍 | valid 持续时 ready 为 0；一旦任意项真正释放，首拍可 fire；已开始事务的末拍仍可接受 | SVA C01_full_first_backpressure；cover full→free→fire | SinkC full / ready。[SinkC.scala:60](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:60) |
| C02 | 两 beat ReleaseData，第二拍延后 | taskValid 仅在 last fire 后置位；task.bufIdx 等于首拍锁存的索引；MainPipe 读到的两个 beat 属于同一事务 | SVA C02_two_beat_index_stable；cross first/last/bufIdx | nextPtrReg 与末拍入队。[SinkC.scala:63](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:63) |
| C03 | C/B/A 同周期有效 | 只选择 C，B/A 不 fire；C 被 block 后才允许 B/A 按其条件竞争 | assertion C03_C_priority；cover C+B+A simultaneous | RequestArb 优先级。[RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) |
| C04 | ProbeAckData 两拍且 MSHR 等待 C response | first data 被保存，last 时 MSHRCtl 仅匹配正确 tag/set 的等待 MSHR，ReleaseBuffer 获得完整行 | scoreboard C04_pdata_reassemble；cover w_c_resp hit/miss | SinkC resp/PData 与 MSHRCtl 匹配。[SinkC.scala:147](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:147) |
| C05 | ReleaseData 到达时同块 MSHR 为 blockRefill | task.fire 后一拍有 refillBufWrite，数据等于 SinkC dataBuf 完整块；同时断言 newdataMask 至多一位 | SVA C05_refill_override_onehot；cover nested overwrite | 嵌套覆盖逻辑。[SinkC.scala:169](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:169) |
| C06 | 同 MSHR id 的 SinkC／nested／MainPipe 写并发 | 明确预期 PriorityMux 的获胜端口，或在集成规则中禁止双写；三写必须触发现有断言 | assertion C06_mshrbuf_write_priority；cover 1/2/3 writers | MSHRBuffer。[MSHRBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39) |
| C07 | C 进入后 Directory 或 GrantBuffer 饱和、同 set 冲突 | sinkC.task 应保持稳定直到 ready；不得丢 task、重复发 ReleaseAck 或越过同 set block | SVA C07_task_hold; cover dir/grant/set block causes | RequestArb、MainPipe、GrantBuffer blockC。[RequestArb.scala:153](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:153) |
| C08 | C ReleaseData 的目录未命中或参数非 fromT | ReleaseAck 产生条件与 DS／Meta 写条件必须区分观测；不得误认为所有 C 都写数据 | scoreboard C08_C_hit_gating；cross hit/fromT/hasData | MainPipe wen_c / metaW_valid_s3_c。[MainPipe.scala:487](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:487) |
| H01 | HuanCun noninclusive save+through 同时执行 | 两个 beat 计数器可独立前进；仅双方完成后清 buffer 并发 taskack | SVA H01_dual_path_complete；cover save-before-through / reverse | HuanCun noninclusive SinkC。[noninclusive/SinkC.scala:153](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:153) |
| H02 | HuanCun ProbeAckData 与旧同 tag/set 缓冲 | 最多一个旧项匹配并被清理；timer 不持续增长到 leak 断言 | assertion H02_unique_cleaner；cover old-match cleanup | HuanCun 去重／计时器。[noninclusive/SinkC.scala:115](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/SinkC.scala:115) |

## 12. 动态例子

### 12.1 正常 ReleaseData

1. DCache WritebackQueue 形成一个按 cache line 的 ReleaseData，并在 inner C 上送出两个 32 B beat。
2. SinkC 接收首拍，选中空的 nextPtr，保存数据并锁存 nextPtrReg；接收末拍后保存另一 beat，并写 taskBuf[nextPtrReg]、置 taskValid。
3. 当 RequestArb 满足目录、reset、MCP2 等入口条件时，RRArbiter 输出该 task；task 进入 s1 读目录，随后作为 s2 task 交 MainPipe。
4. SinkC 在 task fire 后一个寄存阶段输出该 bufIdx 的整块数据；MainPipe s3 在 fromT、带数据且目录 hit 的条件下写 DS／更新相应 Meta。
5. C 分支形成 ReleaseAck，经过 GrantBuffer 以 TL D 向上游完成响应。

这条例子中的第 4 步是有条件写 DS，不是无条件写回；第 5 步的可见时间还取决于 GrantBuffer 和 D 的握手。

### 12.2 ProbeAckData

1. MSHR 向上游发 Probe 后在 w_c_resp 等待。
2. ProbeAckData 的 first/last 经过 SinkC；resp 直接传到 MSHRCtl，PData 同时被两拍拼接。
3. MSHRCtl 以当前 MSHR 的等待状态和 tag/set 进行匹配，Slice 把匹配 id 填入 SinkC 的 ReleaseBuffer 写请求。
4. 等待 MSHR 更新 ProbeAck 完成、dirty／错误等状态；后续是否将 ReleaseBuffer 数据送下游由 MSHR／RequestArb 决定，不是 SinkC 独自决定。

### 12.3 资源挤压

当 4 个 SinkC 槽都保有未发出的 ReleaseData，新的首拍会被 io.c.ready 反压；一项经 task.fire 后，taskValid 先清、下一拍 data valid 清，才重新形成可选空项。若 RequestArb 仍被目录、MCP2、同 set 或 GrantBuffer 阻塞，槽位不会立刻释放，这正是“有缓冲但不保证无阻塞”的可观察表现。

## 13. 未知项与结论

- 本文未执行该配置的 elaboration、仿真、FST 追踪或形式验证，因而没有把源代码的结构关系误报为测得的周期数或波形事实。
- TL edge 协商产生的最终 source 位宽、innerBuf 深度及某些可选字段需由具体顶层 elaboration 确认；本文只计算了 Config 与参数构造能确定的量。
- CoupledL2 的 newdataMask、HuanCun 的 set 匹配／Mux1H 都隐含并发唯一性假设。源码给出部分断言，但本次未证明所有并发路径均保持该不变量。
- 对当前 KunminghuV2Config，课程阅读应沿 “L1 C → tl2chi.SinkC → RequestArb/MainPipe/MSHRCtl → CHI” 路径展开。HuanCun 代码值得学习其 save/through 与专用 C MSHR 的处理，但必须标记为非 CHI 对照，不应取代实际配置的 SinkC 结论。
