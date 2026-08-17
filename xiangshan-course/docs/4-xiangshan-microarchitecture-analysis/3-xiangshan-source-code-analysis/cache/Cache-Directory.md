# 香山昆明湖 V2 缓存 Directory 源码分析

> 本文的结论以源码配置 top.KunminghuV2Config 为准，而不是只根据子模块目录名称推断。该配置的有效 L2 Directory 是 coupledL2.Directory；它保存 tag 与一致性元数据、做 set-associative 查找和候选 way 选择，但不保存 cache line 数据。HuanCun 的 Directory 实现也在同一工作树中，本文会分析其结构差异，不过它不是这一配置下已例化的 L2 或 LLC Directory。

## 1. 分析范围、版本与证据边界

### 1.1. 本文覆盖的对象

| 对象 | 本文的处理 | 判定依据 |
|---|---|---|
| Kunminghu V2 的 L2 Directory | 主体，逐级跟踪到读、写、替换与 MSHR 回路 | KunminghuV2Config 打开 CHI，L2Top 选择 TL2CHICoupledL2，CHI Slice 直接创建 coupledL2.Directory。 |
| HuanCun inclusive / noninclusive Directory | 作为同仓库的实现比较与配置边界 | HuanCun Slice 可按 inclusive 参数选择两者；但 KV2 的 EnableCHI 使 HuanCun L3 参数为空。 |
| OpenLLC Directory | 只用于区分边界，不展开实现 | KV2 的 CHI 下游 LLC 实际走 OpenLLC，不能把它和 coupledL2.Directory 或 HuanCun.Directory 混写。 |
| L1D、MemBlock、DataStorage | 只追踪与 Directory 的接口边界 | 它们提供或消费请求/数据；本文不把未进入 Directory 的虚实地址、PMA、异常语义虚构为 Directory 功能。 |

### 1.2. 源码基线与非修改约束

| 项目 | 基线 |
|---|---|
| XiangShan 主仓 | /home/yanyusong/xs-memory-env/XiangShan，分支 kunminghu-v2，提交 e12436c7cba86b195deec24981976d78bc263661。分析开始时主仓已有 difftest 修改和 src/main/resources/aia/ 未跟踪内容；本文没有触碰它们。 |
| coupledL2 子模块 | 提交 fb5469838c8902b6cb33992c0a30ee3d446e4453。 |
| huancun 子模块 | 提交 65ef077373ecf398b4cecdea06b65ef9b8d79044。 |
| Design Doc | /home/yanyusong/XiangShan-Design-Doc，提交 58d9e2ad11f044cb6f8887d9687d9e110696d1aa；只用于核对概念和阅读入口，所有实现结论均回链到上列源码。 |

skill 规定的周同步检查已经执行；状态文件显示距离上次同步不足七天，因此脚本跳过拉取。分析过程中没有执行 reset、checkout 或任何源码写入。

### 1.3. 有效例化路径：为什么主角是 coupledL2.Directory

KunminghuV2Config 组合 1 MB、4 bank 的 L2 配置与 WithCHI；WithCHI 令 EnableCHI 为真。[Configs.scala:477](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:477) [Configs.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481) L2 的每 bank sets 由 size / banks / ways / 64 计算，因此此配置是每 bank 512 sets、8 ways、64 B line，共 256 KiB；四个 bank 合计 1 MiB。[Configs.scala:278](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:278)

L2Top 将 L2 参数、EnableCHI 和 bank bits 注入 L2 子系统；EnableCHI 为真时选择 TL2CHICoupledL2，而不是 TL2TLCoupledL2。[L2Top.scala:112](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:112) [L2Top.scala:130](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:130) CoupledL2 按输入 bank 创建 Slice，CHI 分支使用 tl2chi.Slice；该 Slice 在同一处创建 Directory、DataStorage、RequestArb、MainPipe 与 MSHRCtl。[CoupledL2.scala:419](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:419) [tl2chi/Slice.scala:53](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:53)

~~~mermaid
flowchart LR
  L1["L1D / L1I / PTW"] --> BB["BankBinder"]
  BB --> L2T["L2Top"]
  L2T --> C2["TL2CHICoupledL2"]
  C2 --> S["tl2chi.Slice per bank"]
  S --> D["coupledL2.Directory"]
  S --> DS["DataStorage"]
  S --> MP["MainPipe"]
  S --> MC["MSHRCtl"]
  C2 --> CHI["CHI downstream"]
  CHI --> LLC["OpenLLC (KV2)"]
~~~

这张图中 DataStorage 与 Directory 是并列模块而非从属关系：Slice 把 MainPipe 的 s3 数据请求送给 DataStorage，[tl2chi/Slice.scala:89](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:89)；Directory 只返回 tag、meta、命中和 victim 信息。

L3CacheConfig 仅在 !EnableCHI 时创建 L3CacheParamsOpt/HCCacheParameters；在 EnableCHI 时创建 OpenLLCParamsOpt。[Configs.scala:333](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:333) HuanCun 顶层由 L3CacheParamsOpt.map 条件创建，因此其 inclusive/noninclusive Directory 不属于此 KV2 的有效硬件图。[Top.scala:104](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Top.scala:104) [Top.scala:111](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Top.scala:111)

### 1.4. Design Doc 命题到本次源码的追溯矩阵

官方 Directory 页面只给出了目录存放元数据、按 tag/set 查找、向 MainPipe/MSHRCtl 连线的概念图。[Directory.md:3](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/Directory.md:3) [Directory.md:5](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/Directory.md:5) 下表把可验证的原子命题落到当前提交，未把图中的概念直接当作时序或仲裁证据。

| ID | 概念命题 | 当前源码证据 | 结论与限制 |
|---|---|---|---|
| D1 | Directory 存储块的元数据 | MetaEntry 定义与 tag/meta SRAM：[Directory.scala:30](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:30) [Directory.scala:146](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:146) | 已验证。数据阵列另在 DataStorage。 |
| D2 | 读请求按 tag/set 查找 | DirRead 的 tag/set：[Directory.scala:70](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:70)；读阵列与比较：[Directory.scala:211](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:211) [Directory.scala:250](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:250) | 已验证。Directory 输入已经是分离后的 tag/set。 |
| D3 | 命中/候选 way 与元数据回送 | DirResult 与 resp 驱动：[Directory.scala:84](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:84) [Directory.scala:309](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:309) | 已验证。resp 是 ValidIO，没有 ready 回压。 |
| D4 | miss 选择 invalid 或替换 way | invalid 优先、MSHR 占用屏蔽和 retry：[Directory.scala:129](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:129) [Directory.scala:260](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:260) [Directory.scala:339](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:339) | 部分验证。replResp 仅随 refill 读有效；全部候选 way 忙时返回 retry，不是任意 miss 都立即可写回。 |
| D5 | 管线处理后更新目录 | Slice 接线和 MainPipe 生成 meta/tag 写：[tl2chi/Slice.scala:84](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:84) [MainPipe.scala:594](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:594) | 已验证。写端为 ValidIO，不能假设存在写完成 ready/ack 握手。 |

### 1.5. 理论到代码映射

| 缓存理论对象 | 在本实现中的代码落点 | 不应过度解释的范围 |
|---|---|---|
| set-associative tag store | parseAddress 得本地 tag/set，tagArray 以 set 读出所有 way | 不在 Directory 内做完整物理地址取得或 bank 路由。 |
| directory / coherence metadata | MetaEntry.state、dirty、clients、alias 等与 metaArray | 不是 line data array，也不是 L1 TLB/PMA 表。 |
| hit 判定 | tagMatchVec 与 state != INVALID 相与 | multiHit 是错误状态，不是多份副本可任选一份。 |
| victim selection | invalid 优先，随后 replacement state，refill 再受 freeWayMask 约束 | wayMask 在本提交的 finalWay 路径尚未生效。 |
| replacement state | DRRIP/SRRIP/其他 policy 的 state SRAM、origin bit、PSEL | 具体 policy 随 L2ParamKey 可变；本节后面单独给出 KV2 默认。 |
| coherence state update | MainPipe 的 A/B/C/MSHR/CMO metaWReq 选择 | Directory 不独立决定外部 CHI transaction 或全系统 permission。 |

## 2. coupledL2.Directory 的接口、状态与所有权

### 2.1. 模块接口及信号方向

Directory 的接口定义见 [Directory.scala:117](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:117)。下表中的 source/destination 指 Slice 的实际连线，而不是按名称猜测。

| 接口 | 方向和握手 | source -> destination | 含义 |
|---|---|---|---|
| read | Flipped DecoupledIO；只有 valid && ready 才接受 | RequestArb.dirRead_s1 -> Directory.read | 对一条 tag/set 请求读 tag/meta，并携带 refill、mshrId、CMO 与替换更新信息。 |
| resp | ValidIO；无 ready | Directory.resp -> MainPipe.dirResp_s3 | s3 返回命中、way、meta、tag、ECC/多命中 error。 |
| metaWReq | Flipped ValidIO；无 ready | MainPipe.metaWReq -> Directory | 写元数据，包括复位清空、权限/脏位变化、CMO invalidate、MSHR refill 后更新。 |
| tagWReq | Flipped ValidIO；无 ready | MainPipe.tagWReq -> Directory | refill 成功且非 retry 时写入新 tag。 |
| replResp | ValidIO；无 ready | Directory.replResp -> MainPipe 与 MSHRCtl | 仅针对 refill 查找，返回最终候选 way、旧 meta/tag、mshrId 和 retry。 |
| msInfo | Vec[ValidIO] 输入 | MSHRCtl.msInfo -> Directory | 反映各 MSHR 对 set/way 的占用，防止 refill 覆盖尚未完成的数据。 |

Slice 的前四个连接在 [tl2chi/Slice.scala:84](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:84)，Directory 的结果连接在 [tl2chi/Slice.scala:117](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:117) 与 [tl2chi/Slice.scala:139](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:139)。

~~~mermaid
flowchart LR
  RA["RequestArb"] -->|read Decoupled| D["Directory"]
  MP["MainPipe"] -->|metaWReq Valid| D
  MP -->|tagWReq Valid| D
  MC["MSHRCtl"] -->|msInfo Vec Valid| D
  D -->|resp Valid| MP
  D -->|replResp Valid| MP
  D -->|replResp Valid| MC
~~~

### 2.2. 每 way 保存什么，不保存什么

MetaEntry 含 dirty、coherence state、clients 有效位、可选 alias/prefetch 字段、accessed，以及 tagErr/dataErr。[Directory.scala:30](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:30) MetaEntry() 用全零初始化，[Directory.scala:47](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:47)；而 MetaData.INVALID 是状态 0。[Consts.scala:26](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Consts.scala:26) 因此复位清扫写入 MetaEntry() 会把 way 变为无效。

| 存储体 | 本模块中的声明 | 作用 | 端口/冲突含义 |
|---|---|---|---|
| tagArray | SplittedSRAM，singlePort = true | 每 set 的所有 way tag；配置启用时对 tag 做 ECC 编码/解码 | 读在 read.fire，tagWReq.valid 同周期写。读请求在写或替换更新时被 ready 阻止。 |
| metaArray | SRAMTemplate，singlePort = true | 每 set 的 MetaEntry 向量 | 与 tag 查找同 set，同样由 metaWReq 驱动写。 |
| replacer SRAM | 非 random replacement 时使用，singlePort = true | 每 set 的 replacement policy state | replacement 更新也会阻断新 Directory read。 |
| DataStorage | 不在 Directory 内 | 保存 cache line data | 由 MainPipe 单独访问，Directory 只提供 way/set 选择信息。 |

tag/meta SRAM 的声明在 [Directory.scala:146](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:146) 与 [Directory.scala:175](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:175)。这也是“Directory 是 metadata store”在代码中的准确含义，不应扩展成“Directory 保存数据块”。

### 2.3. tag、set、way 的计算边界

Directory 的 DirRead 只有 tag、set、wayMask、replacerInfo、refill、mshrId、cmoAll/cmoWay，没有完整物理地址、字节 offset 或 beat 字段。[Directory.scala:70](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:70) RequestArb 将 TaskBundle 中已形成的 tag/set 直接赋给 DirRead。[RequestArb.scala:174](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174)

CoupledL2 的公共参数 trait 给出了实际的局部地址拆分：offsetBits = log2Ceil(blockBytes)，parseAddress 先右移 offsetBits + bankBits 得到本 bank 的 set 源，再右移 setBits 得 tag。[CoupledL2.scala:47](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:47) [CoupledL2.scala:186](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:186) 因此该配置可以精确写为：

~~~text
offset[5:0]       = line 内字节位置，blockBytes = 64
bank[7:6]         = 4 bank 的 bank 选择，bankBits = 2
localSet[16:8]    = 本 bank 的 512 sets，setBits = 9
tag[high:17]      = 去掉 offset、bank、local set 后的高位
way               = hitWay，或 invalidWay，或 replacementWay
~~~

这里的 tag 高位写法是地址位域语义，实际 tag UInt 的总宽度仍取决于 TileLink 地址宽度，不能从上述常量把它硬编码成某个有限 bit 数。Directory.scala 本身不再做完整地址切分：它接收的就是 parseAddress/RequestArb 路径已经形成的 tag 和 set。[RequestArb.scala:174](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174)

### 2.4. 隐式状态生命周期

Directory 没有一个名为 FSM 的枚举状态机；下面是由 resetFinish、read.fire、写入 valid、流水寄存器和 replacement update 组合出的生命周期图。它描述接口状态，不把它误称为源码中的显式 FSM。

~~~mermaid
stateDiagram-v2
  [*] --> ResetWalk
  ResetWalk: resetIdx walks sets
  ResetWalk --> Ready: resetFinish
  Ready --> ReadS1: read.fire
  ReadS1 --> ReadS2: tag/meta SRAM response
  ReadS2 --> ResultS3: compare and choose way
  ResultS3 --> Ready: resp.valid
  Ready --> Write: metaWReq or tagWReq or replacerWen
  Write --> Ready: read.ready becomes eligible
~~~

Directory 自己的 resetIdx/resetFinish 用于 replacement/origin 相关 SRAM 初始化。[Directory.scala:441](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:441) 主 metadata 清扫的发起者是 MainPipe：reset 未完成时 metaWReq.valid 为真，set 用 resetIdx、wayOH 覆盖所有 way，wmeta 为 MetaEntry()。[MainPipe.scala:594](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:594)

## 3. coupledL2.Directory 的查找流水、命中与替换

### 3.1. s1/s2/s3 的精确责任

| 阶段 | 有效条件与寄存器 | 做什么 | 产物 |
|---|---|---|---|
| s1 | io.read.fire | 向 tagArray、metaArray、replacer SRAM 发出 set 读；锁存输入 | 请求被接受的唯一时刻。 |
| s2 | reqValid_s2 | 接收 SRAM 读出，锁存 tag/meta/ECC；并为 refill 计算同 set 已占用 way 的反码 | 为 s3 的比较与候选选择准备稳定数据。 |
| s3 | reqValid_s3 | tag 比较、valid 过滤、multiHit/error 判断、命中/invalid/replacer/free-way 选择；驱动 resp | normal resp.valid。若原请求是 refill，同时驱动 replResp.valid。 |

源码注释明确标注 s1/s2/s3，[Directory.scala:190](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:190)；有效位和请求寄存器的实际定义在 [Directory.scala:196](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:196)。这是 Directory 自己的逻辑阶段。综合后的 SRAM 宏延迟、时钟分频和整条交易完成时间还会受配置和下游状态影响，不能据此承诺外部可见的固定总周期。

下面的波形是从 valid/ready 条件抽出的相对时序示意，不是仿真波形。第 1 个请求在 s1 fire，随后经过 s2/s3；稍后的 meta 写使 read.ready 下降，等待的下一请求必须保持 valid 到新的 fire。

~~~waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p......."},
    {"name": "read.valid", "wave": "01...0.."},
    {"name": "read.ready", "wave": "01110011"},
    {"name": "read.fire", "wave": "0.10...."},
    {"name": "reqValid_s2", "wave": "0..10..."},
    {"name": "reqValid_s3", "wave": "0...10.."},
    {"name": "resp.valid", "wave": "0...10.."},
    {"name": "metaWReq.valid", "wave": "0.....10"}
  ],
  "config": {"hscale": 1}
}
~~~

### 3.2. 命中判定与 error

对每一个 way，tagMatchVec 比较 tag，metaValidVec 要求 state 非 INVALID；两者相与得到 hitVec。[Directory.scala:250](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:250) hitWay 是 hitVec 的 one-hot 编码；multiHit 由 PopCount(hitVec) 大于一检测，multiHit 会使正常 hit 失效并进入 error 路径。[Directory.scala:271](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:271) [Directory.scala:289](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:289)

| 条件 | hit | way | error |
|---|---|---|---|
| 恰好一个 valid tag match | 真 | hitWay | 若启用 Tag ECC，只看选中 valid way 的 ECC error。 |
| 无 match | 假 | finalWay | 若启用 Tag ECC，任一 valid way 的读 error 或 multiHit 会进入 errorMiss。 |
| 多个 valid tag match | 假 | finalWay | multiHit 被视为错误。 |
| cmoAll | 按 cmoWay 指向的 meta 是否 valid | cmoWay | 普通 tag match 不决定命中。 |

CMO 特例和 error 逻辑见 [Directory.scala:290](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:290) [Directory.scala:297](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:297)。Directory 只上报 error；如何转化为系统级报告由 MainPipe/上层错误处理决定，本文不把它写成 load/store exception 的直接来源。

### 3.3. miss、invalid way、MSHR 占用与 retry

候选选择按下列顺序发生：

1. invalid_way_sel 先搜索 state 为 INVALID 的 way；找到则优先用该 way。
2. 否则 replacement policy 从该 set 的替换状态给出 replaceWay。
3. 对 refill，Directory 由 msInfo 汇集同 set 且 blockRefill 或 dirHit 的 MSHR way，得到 freeWayMask。
4. chosenWay 若不在 freeWayMask 中，改用 freeWayMask 的 PriorityEncoder；若 freeWayMask 全零，则 replResp.retry 为真。

步骤 1/2 在 [Directory.scala:129](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:129) 与 [Directory.scala:271](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:271)，步骤 3/4 在 [Directory.scala:255](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:255) 与 [Directory.scala:284](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:284)。

ReplacerResult 只有 refillReqValid_s3 时 valid，且带回 mshrId、最终 way、被选 old tag/meta、retry 和 error。[Directory.scala:339](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:339) 所以应区分：

| 事件 | Directory.resp | Directory.replResp | 不能得出的结论 |
|---|---|---|---|
| 普通请求（包括普通 miss） | 有效 | 无效 | 不能只凭普通 miss 断言已经获得可写 victim。 |
| MSHR refill/replacement 查询 | 有效 | 有效 | 若所有 way 被占用，结果要求 retry，而非覆盖活跃 line。 |
| retry refill | 有效 | valid 且 retry | MainPipe 不应提交该 refill 的 tag 写。 |

MainPipe 的 tagWReq 条件显式包含 mshr_refill_s3 与 !retry。[MainPipe.scala:608](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:608)

replResp 不会被广播后由多个 MSHR 同时消费：MSHRCtl 以 replResp.bits.mshrId 精确生成每一项 MSHR 的 replResp.valid。[MSHRCtl.scala:131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:131) 目标 MSHR 收到 retry 时清 s_refill/s_retry、记录候选 way 并重置 backoff 计时；收到非 retry 时锁存 victim tag/meta/way，必要时继续 release 或 client probe。[MSHR.scala:1256](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1256) 因此 retry 是 Directory 与 MSHR 的明确回路，不是丢弃请求后的无状态重发。

有一个需要保留的源码 caveat：DirRead 中有 wayMask，RequestArb 在 MSHR retry 时把上次失败 way 排除。[RequestArb.scala:179](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:179) 但 finalWay 的 wayMask 选择代码被注释，紧邻 TODO 明说 wayMask 未纳入考虑；实际 finalWay 仅依据 freeWayMask。[Directory.scala:275](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:275) 因而不能把接口注释“结果 way 必在 wayMask”当作这一提交已实现的保证。

### 3.4. replacement state 的更新

replacement policy 的读在与 Directory read 同一条 set 访问中进行；非 random policy 使用 replacer SRAM，random policy 在 tag 写时调用 repl.miss。[Directory.scala:324](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:324)

| 策略条件 | hit update | refill update | 结构后果 |
|---|---|---|---|
| PLRU/其他非 RRIP 分支 | A channel 的 AcquirePerm 或 AcquireBlock 命中 | retry 为假时 | 不把 C channel hit 当作 updateHit。 |
| SRRIP/DRRIP | A 的 AcquirePerm/AcquireBlock/Hint 或 C 的 Release/ReleaseData 命中 | retry 为假时 | 维护更丰富的 reuse/prefetch 类型信息。 |
| random | 读出状态固定为 0 | 由 tagWReq 时 repl.miss 推进 | 无 replacer SRAM。 |

更新条件和 updateRefill 在 [Directory.scala:348](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:348) 至 [Directory.scala:361](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:361)。SRRIP/DRRIP 的 origin-bit 与 PSEL 是可选 replacement policy 的内部状态，[Directory.scala:363](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:363) [Directory.scala:398](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:398)。

对于本文固定的 KunminghuV2Config，L2CacheConfig 构造 L2Param 时没有覆盖 replacement，而 L2Param 的默认 replacement 是 drrip。[Configs.scala:297](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:297) [L2Param.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:65) 因而可将本配置的默认策略认定为 DRRIP；若后续配置层重写 L2ParamKey，这一结论需要重新核验。

### 3.5. 写回、复位与端口冲突

MainPipe 的 metadata 写有 A、B、C、MSHR、CMO 五类候选；metaWReq 在复位期间强制有效，正常运行时由这些条件之一触发。[MainPipe.scala:532](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:532) [MainPipe.scala:594](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:594) 其 payload 使用 ParallelPriorityMux；这是该写来源的选择点，验证时应覆盖多类 valid 同时到来，而不应假定它们可独立并行写同一个 single-port metaArray。

| 写来源 | 何时有效 | 写入的目录语义 |
|---|---|---|
| A 快路径 | 非 MSHR 且非 get/prefetch/CMO 等条件 | 保留 dirty；需要 T 或 promotion 时写 TRUNK；clients 以 l2Error 为条件置位，accessed 置真。 |
| B snoop | B 命中且满足 snoop 状态条件 | ToN 的直接完成写 MetaEntry() 使 line 无效；其他情况清 dirty 并保持或降到 BRANCH。 |
| C Release | C 命中 | dirty 取旧 dirty 或本次写数据；isParamFromT 为真时写 TIP，否则保留原 state；同时更新 clients 与错误标记。 |
| MSHR | task 请求 metaWen，且 refill 非 retry | 使用 MSHR 带回的 meta（mergeA 时用合并 meta），并记录 denied/corrupt。 |
| CMO | CBO invalidate 命中 | 写 MetaEntry()，即失效该 block。 |

这是 MainPipe 的 policy，而不是 Directory 内部对 A/B/C 的解码：五类候选条件和 payload 分别在 [MainPipe.scala:535](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:535) 至 [MainPipe.scala:586](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:586)。同周期竞争时 ParallelPriorityMux 的代码序列为 A、B、C、MSHR、CMO；需以该 helper 的左侧优先实现为准，验证应覆盖这种重合。[MainPipe.scala:601](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:601)

Directory 的 read.ready 是：

~~~scala
io.read.ready := !io.metaWReq.valid && !io.tagWReq.valid && !replacerWen
~~~

原代码在 [Directory.scala:322](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:322)。这给出严格的吞吐上界：只有没有 tag/meta 写且没有 replacement state 更新的周期，Directory 才可接收一个新读请求。它不是“写和读在同周期双发”的单端口 RAM 设计。

### 3.6. RequestArb 到 Directory 的进入优先级

RequestArb 对 sinkC、sinkB、sinkA 的 ready 显式编码为 C 高于 B 高于 A；其 source task 采用 C、B、A 次序的 ParallelPriorityMux。[RequestArb.scala:145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:145) [RequestArb.scala:155](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:155) 同时，已有 mshr_task_s1 时它优先成为 task_s1。[RequestArb.scala:163](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:163)

| 冲突层次 | 已证实的选择 | 对 Directory 的影响 |
|---|---|---|
| sink C / B / A 同时有效 | C > B > A | 只有赢得入口且 Directory ready 的 channel task 形成 dirRead。 |
| MSHR task 与 channel task | task_s1 选 MSHR task | 普通 channel 读可被 MSHR replacement/retry 流程推迟。 |
| Directory write / replacement update 与任何 read | write/update 阻止 read.ready | 上游 Decoupled producer 需要稳定保持 valid/bits 到 fire。 |
| 同 set refill 与活跃 MSHR | freeWayMask 排除占用 way | 无空闲 way 时走 retry，避免覆盖。 |

## 4. HuanCun Directory：同仓库实现比较与配置边界

### 4.1. 为什么仍然分析 HuanCun，但不把它写成 KV2 已例化逻辑

coupledL2 与 huancun 是独立 Git submodule，coupledL2 还对 huancun 有编译/Bundle 依赖；这不等于 HuanCun L3 已经被实例化。[build.sc:145](/home/yanyusong/xs-memory-env/XiangShan/build.sc:145) [coupledL2/common.sc:4](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/common.sc:4) 在非 CHI 的 L3 配置中，HuanCun Slice 才按 cacheParams.inclusive 选择 inclusive.Directory 或 noninclusive.Directory。[huancun/Slice.scala:381](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:381)

~~~mermaid
flowchart LR
  Q["mshrAlloc.dirRead"] --> A["ctrl_arb"]
  C["optional cache ctrl"] --> A
  A --> H["HuanCun Directory"]
  H --> R["result to MSHR / ctrl"]
  W["MSHR task writes"] --> B["write arbitration"]
  B --> H
  H --> I["inclusive.Directory"]
  H --> N["noninclusive.Directory"]
~~~

图中 I/N 是 elaboration 的二选一，而非同一 Slice 中并行工作的两个 Directory。读连接与选择见 [huancun/Slice.scala:381](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:381) 至 [huancun/Slice.scala:389](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:389)。

### 4.2. 公共 SubDirectory：接口、流水和复位

HuanCun 的 BaseDirectoryIO 把 read、result、dirWReq、tagWReq 分别定义为 Decoupled、Valid、Decoupled、Decoupled。[BaseDirectory.scala:37](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseDirectory.scala:37) [BaseDirectory.scala:47](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseDirectory.scala:47) 这与 coupledL2 的写端 ValidIO 不同，不能混用握手结论。

| 项目 | HuanCun SubDirectory 的源码行为 | 与 coupledL2 的可见差异 |
|---|---|---|
| 数组 | tag、meta 均为 singlePort SRAM；可选 tag ECC array | 物理单端口约束仍存在。 |
| read ready | !tag_wen && !dir_wen && !replacer_wen && resetFinish | 复位完成前明确拒绝 read。 |
| write ready | tag_w.ready 与 dir_w.ready 固定为真 | 外层仍可能因双读端或 clk-div2 加 readyMask。 |
| 流水 | read.fire 后等待 SRAM；resp.valid 由 reqValidReg；再锁存 hit/way/tag/dir 输出 | 注释是 stage 0、wait、stage 1、stage 2，不能照搬 coupledL2 的 s1/s2/s3 名称。 |
| 复位 | resetIdx 逐 set 将所有 way metadata 写成 dir_init；tag SRAM 不逐项清零 | 有效性由 metadata 的 INVALID 判定，旧 tag 不形成命中。 |

这些条件分别可见于 [BaseDirectory.scala:104](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseDirectory.scala:104)、[BaseDirectory.scala:115](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseDirectory.scala:115)、[BaseDirectory.scala:159](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseDirectory.scala:159) 和 [BaseDirectory.scala:221](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseDirectory.scala:221)。

~~~waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p......."},
    {"name": "resetFinish", "wave": "0..1...."},
    {"name": "read.valid", "wave": "01......"},
    {"name": "read.ready", "wave": "00011110"},
    {"name": "read.fire", "wave": "0...10.."},
    {"name": "result.valid", "wave": "0....10."},
    {"name": "dirWReq.valid", "wave": "0......1"}
  ],
  "config": {"hscale": 1}
}
~~~

这是基于 ready 条件的相对示意。若 sramClkDivBy2 为真，BaseDirectory 中有时钟门控与额外等待，精确 cycle distance 不能由该图固定化。[BaseDirectory.scala:104](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/BaseDirectory.scala:104)

### 4.3. inclusive.Directory

inclusive Directory 的单条 DirectoryEntry 是 dirty、state、clients 和可选 prefetch；命中谓词仅要求 state 非 INVALID。[inclusive/Directory.scala:19](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/Directory.scala:19) [inclusive/Directory.scala:57](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/Directory.scala:57) 它复用 SubDirectoryDoUpdate，并使用 UpdateOnAcquire；invalid way 优先逻辑与 coupledL2 一样先筛 INVALID，再取优先编码的 way。[inclusive/Directory.scala:50](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/Directory.scala:50)

从请求到结果的 idOH 会在 read.fire 时锁存，随后随 result 输出，使 MSHR 能知道应接收哪个目录查询结果。[inclusive/Directory.scala:74](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/inclusive/Directory.scala:74) 这与 coupledL2 通过 DirRead.mshrId/replResp.mshrId 回送的编码方式不同。

### 4.4. noninclusive.Directory

noninclusive Directory 同时维护 self directory 和 client directory：SelfDirEntry 记录本级 dirty/state/clientStates，ClientDirEntry 对每个 client 记录 state 和可选 alias。[noninclusive/Directory.scala:26](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/Directory.scala:26) [noninclusive/Directory.scala:33](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/Directory.scala:33)

| 行为 | 当前代码 | 含义 |
|---|---|---|
| client directory 的空 way | 每个 client state 都是 INVALID 才视为该 client way 无效 | 一个 way 有任一 client 有效，就不能被当作全空。 |
| self directory 的候选 way | 先 INVALID；否则优先可替换的 TRUNK；若 replacer 给的 way 本身是 TRUNK 则优先用它 | non-inclusive 一致性状态使其不等同于 coupledL2 的“invalid 否则 replacer”。 |
| 并行读取 | 同一 read 驱动 clientDir 和 selfDir；req.ready 要求二者都 ready | 结果 valid 还断言两路 valid 同时为真或同时为假。 |
| 地址重组 | addrConnect 连接不同 set/tag 宽度时先拼接再重新切分 | 表明 self/client directory 可以有不同 set/tag 位宽，但不代表在这里翻译虚拟地址。 |
| replacement update | ReleaseData 或 Hint 才更新 | 与 inclusive 的 UpdateOnAcquire 不同。 |

上述细节的代码位置为 [noninclusive/Directory.scala:173](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/Directory.scala:173)、[noninclusive/Directory.scala:198](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/Directory.scala:198)、[noninclusive/Directory.scala:233](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/Directory.scala:233)、[noninclusive/Directory.scala:240](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/Directory.scala:240) 和 [noninclusive/Directory.scala:103](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/noninclusive/Directory.scala:103)。

### 4.5. HuanCun Slice 的写冲突处理

HuanCun Slice 用 block_b_c 让最后一个 C source 直接接入，而 B source 在 select_c 时被阻塞；它把 directory.io.dirWReq 的来源接入该仲裁。[huancun/Slice.scala:410](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:410) [huancun/Slice.scala:426](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:426) tag write 则通过 arbTasks 接入。[huancun/Slice.scala:438](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/Slice.scala:438)

可以确定的是这些请求竞争一个 Directory 写入口，并且 C 相关写存在显式阻塞规则。FastArbiter 内部的所有细粒度优先级、控制寄存器请求和 MSHR source 的最终顺序需要结合其实现再验证；本文不把接口输入顺序误写为完整的全局优先级表。

## 5. 与昆明湖访存流水的边界

### 5.1. Directory 前后的数据流

~~~mermaid
flowchart LR
  A["SinkA"] --> RA["RequestArb"]
  B["SinkB / RXSNP"] --> RA
  C["SinkC"] --> RA
  M["MSHRCtl task"] --> RA
  RA -->|DirRead Decoupled| D["Directory"]
  D -->|DirResult Valid| P["MainPipe"]
  D -->|ReplacerResult Valid| P
  D -->|ReplacerResult Valid| M
  M -->|msInfo| D
  P -->|metaWReq / tagWReq| D
  P -->|s3 data req| DS["DataStorage"]
  DS -->|s5 data / error| P
~~~

RequestArb 将 Sink A/B/C 或所需的 MSHR 任务转换为 DirRead，并填充 refill、mshrId、CMO 和 replacerInfo。[RequestArb.scala:174](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/RequestArb.scala:174) MainPipe 在 s3 消费 dirResp、决定是否申请 MSHR，并同时发出数据阵列操作；其接口命名本身将这些动作标成 s3。[MainPipe.scala:32](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:32) [MainPipe.scala:491](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:491)

这条链说明 Directory 参与“给某 line 找状态和 way”，但不负责：

| 问题 | Directory 以外的责任边界 |
|---|---|
| load/store 的虚地址翻译、PMA/PMP、MMIO 分类 | Directory 输入没有虚地址或属性字段；这些必须在请求形成 Directory tag/set 之前解决。 |
| cache line 数据读写、sub-beat 合并和 data ECC | DataStorage 与 MainPipe 的接口承担数据通路。 |
| 下游 CHI 事务、回包和 retry 的外部协议 | txreq/txdat/txrsp/rx* 与 MSHRCtl/MainPipe 负责；Directory 仅提供本地候选和 retry 信息。 |
| L1D pipeline 的 load replay 或异常提交 | 从 L2 Directory 源码不能直接推出这些前端/后端时序。 |

### 5.2. 跨边界代码解析

| 边界情形 | Directory 可观察到的字段 | 当前代码的行为 | 明确不能声称的事情 |
|---|---|---|---|
| 虚拟页边界 | 无 vaddr、页号或 TLB 字段 | 只接收已经形成的 tag/set | Directory 不做 VA->PA 翻译，也无法自行判断跨页。 |
| MMIO / 非缓存属性 | 无 PMA、cacheable 或 device 字段 | 没有针对 MMIO 的分支 | 不能把 Directory 当成 MMIO 旁路判定点。 |
| 一条访问跨 cache line | 无 byte offset、size 或 beat；只有一个 tag/set | 一次 Directory lookup 标识一个 cache line | 没有分裂/合并两个 line 的代码；这类访问必须在上游分解为独立 line 事务。 |
| CMO 全局/按 way | cmoAll、cmoWay | cmoAll 时以 cmoWay 选择 valid meta | 这不是通用的跨 line 拆分器。 |
| refill 与活跃 line 冲突 | msInfo 的 set、way、blockRefill、dirHit | 占用 way 被排除；无 free way 时 retry | 不能写成“替换总能立即成功”。 |

第一、二、三行的证据是 DirRead 字段与 tag/meta 访问点。[Directory.scala:70](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:70) [Directory.scala:211](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:211) 最后一行证据为 [Directory.scala:260](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/Directory.scala:260)。

## 6. 延迟、吞吐与可观察的背压

### 6.1. 可由代码证明的内容

| 指标 | 可证明的界限 | 证据 |
|---|---|---|
| 单 bank Directory 新读接收率 | 在没有 tag/meta 写和 replacement 更新的可用周期，最多一条 read.fire | singlePort array 与 read.ready 条件。 |
| 正常 lookup 结果阶段 | 从 read.fire 经源码标注 s1/s2/s3 到 resp.valid | 三阶段注释和 reqValid 寄存器。 |
| refill 可用 victim | 需要 s3 的 freeWayMask；没有 free way 则 retry | msInfo 占用掩码和 replResp.retry。 |
| write 对读的影响 | metaWReq/tagWReq/replacerWen 任一为真，read.ready 为假 | Directory.scala:322。 |
| HuanCun read 启动条件 | resetFinish 后且没有 tag/dir/replacer 写 | BaseDirectory.scala:120。 |

### 6.2. 不能从当前静态代码承诺的内容

以下数值需要 elaboration 参数、SRAM macro、时钟配置和实际仿真/波形才能确认，因此本文不提供假精确数字：

1. 从 L1 请求进入到上游看到数据的端到端固定 cycle 数。
2. CHI 或 OpenLLC 回包、MSHR 状态机、写回链路造成的 miss 服务延迟。
3. 多 bank 同时访问的系统总吞吐，以及 BankBinder 的精确地址散列。
4. HuanCun 在 sramClkDivBy2 具体取值下的查找周期。

对于 wave trace，建议稳定跟踪同一个 Directory read.fire 对应的 mshrId，以及后续 Directory.resp、replResp、MainPipe.metaWReq/tagWReq、MSHRCtl.msInfo，而不是只按 PC 或 valid 单点判断 transaction 完成。

## 7. 验证特别注意

| 场景 / 断言目标 | 触发方法 | 必看信号或状态 | 预期 |
|---|---|---|---|
| F_RESET_IDLE | 上电后持续 reset 直到 resetFinish | MainPipe.metaWReq、Directory resetIdx、meta state | 每 set 的所有 way 被写成 MetaEntry()；在清扫完成前不把旧 tag 当作有效 line。 |
| F_FIRST_REQUEST | resetFinish 后第一个 read | read.valid/ready/fire、reqValid_s2/s3、resp.valid | 仅在 fire 后出现结果；不能由 valid 单独推断已接受。 |
| F_HOLD_BACKPRESSURE | 让 metaWReq、tagWReq 或 replacerWen 与 read.valid 同周期 | read.ready 与上游 bits 稳定性 | read.ready 必低；上游保持 request 到后续 fire。 |
| C_SAME_ENTRY_RW | 对同 set/way 制造读与 MainPipe meta/tag 写重叠 | read.ready、metaWReq/tagWReq、tag/meta SRAM 端口 | 不应同周期接受读；读/写冲突由入口背压序列化。 |
| C_MULTIHIT | 人工制造两 way 同 tag 且 state 非 INVALID | hitVec、multiHit、resp.hit/error | resp.hit 为假且 error 置位（Tag ECC 启用时 errorMiss 包含 multiHit）。 |
| C_OCCUPIED_REFILL | 一个或多个 MSHR 报同 set 且 blockRefill/dirHit | msInfo、freeWayMask、replResp.retry | 相关 way 不能做 refill victim；所有 way 忙时 retry。 |
| P_RETRY_PROGRESS | 连续 refill retry | RequestArb.dirRead.wayMask、Directory finalWay、MainPipe tagWReq | 验证 retry 后能重试并最终进展；同时观察当前提交的 wayMask TODO，不能只假设换 way 已生效。 |
| P_PRIORITY_CBA | Sink C/B/A 同周期进入 | sink*.valid/ready/fire、RequestArb task | C 胜 B 胜 A；验证被抑制请求没有丢失。 |
| P_MSHR_VS_CHANNEL | MSHR task 与新的 channel task 同周期 | mshr_task_s1、chnl_task_s1、dirRead.valid | MSHR task 占 task_s1 时 channel 访问可延后；检查饥饿风险与解除条件。 |
| E_ECC | 注入 tag ECC error 或记录 data/tag error 元数据 | errorRead/error_s3、resp.error、MainPipe 后续处理 | Directory 产生检测结果；系统级错误响应需在下游模块单独验证。 |
| H_INCL_RESET | 非 CHI HuanCun 配置下复位 | SubDirectory resetFinish/resetIdx/read.ready | tag 可保留旧值但 INVALID metadata 使其不命中。 |
| H_NONINCL_ALIGN | noninclusive 的 self/client 同步查询 | selfResp.valid、clientResp.valid、assert(valids...) | 两路 valid 必同时出现；不同步应触发断言。 |

对于当前 KV2 的实测波形，前十行是有效 L2 覆盖点；后两行只能在非 CHI HuanCun 配置 elaboration 后验证。Directory 相关源码本身没有直接的 Difftest 调用；因此不能把 Difftest 通过误报为 Directory 的本地验证证据。

## 8. 结论与尚未证明项

1. 在 top.KunminghuV2Config 下，缓存单元的实际 L2 Directory 是 coupledL2.Directory，位于每个 tl2chi.Slice；HuanCun Directory 是可比较源码，而不是这个配置下的工作实例。
2. coupledL2.Directory 的核心职责是 tag/meta 单端口访问、s1/s2/s3 查找、valid tag 命中、invalid/replacer 候选、以及与 MSHR 占用掩码协作的 refill retry。它不拥有 line 数据，也不翻译虚地址或判定 MMIO。
3. 与 Directory 有关的关键正确性约束是：写/替换更新会回压新读；多命中要报错；refill 不得覆盖 MSHR 占用 way；retry refill 不得 tag 写回。
4. HuanCun inclusive 复用单目录结构，noninclusive 则同时跟踪 self/client directory 并引入 TRUNK 候选规则。这些差异不能倒灌成 coupledL2 的行为。
5. 当前提交中 wayMask 的最终选择路径被 TODO 注释掉，是后续复查/波形验证时应优先关注的实现点。是否导致实际 retry 策略问题，需要结合运行配置和测试结果，而不能仅凭静态代码直接定性为 bug。
