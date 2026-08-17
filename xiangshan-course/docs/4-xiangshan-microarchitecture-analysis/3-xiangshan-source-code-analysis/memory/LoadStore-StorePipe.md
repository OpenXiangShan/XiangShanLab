# Kunminghu V2 StorePipe：DCache 写意图预取流水线源码解析

> 结论先行：这里的 <code>StorePipe</code> 不是把 store data 写进 L1 DCache 的流水线。它是 <code>StoreUnit</code>（STA）旁路的一条“查 DCache tag/meta、判断写意图是否已满足、必要时发 <code>M_PFW</code> 预取提示”的三段流水线。真正具有 data/mask、受 ROB 提交约束并写入 L1D 的路径是 <code>StoreQueue → Sbuffer → DCache MainPipe → data_write</code>。

## 1. 分析范围、基线与可复现性

| 项目 | 本文采用的事实 |
| --- | --- |
| 分析对象 | Kunminghu V2 的 DCache-side <code>StorePipe</code>，并追踪其 STA、LSQ、SBuffer、MainPipe 和 MissQueue 边界 |
| 源码根目录 | <code>/home/yanyusong/xs-memory-env/XiangShan</code> |
| 分支 / commit | <code>kunminghu-v2 @ e12436c7cba86b195deec24981976d78bc263661</code> |
| 主实现 | [StorePipe.scala:26](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:26) 到 [StorePipe.scala:197](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:197) |
| 设计文档基线 | 本地未发现 <code>XiangShan-Design-Doc</code> checkout；本文所有行为结论以这份 Scala 源码为准，不把设计文档推测写成事实 |
| skill 同步 | 依照当前 <code>analyze-xiangshan-kunminghu</code> skill 执行 weekly sync 检查；距最近同步不足 7 天，脚本按规则跳过网络同步 |
| 工作树处理 | 源码工作树已有 <code>difftest</code> 子模块改动和 <code>src/main/resources/aia/</code> 未跟踪内容；本文只读，未修改它们 |

### 1.1 最容易误读的名称边界

<code>StorePipe</code> 的类注释称它为 “Non-Blocking Store Dcache Pipeline”，但其接口请求只含 <code>cmd/vaddr/instrtype</code>，没有 store data 或 byte mask；它的 miss 输出固定为预取写 <code>M_PFW</code>。因此：

1. 它报告的 <code>miss</code> 是一次 cache tag/meta 与写权限检查的结果，不等同于架构 store 尚未提交或真实写入失败。
2. <code>miss_req</code> 仲裁失败时，丢失的是一次可选预取机会，不是那条架构 store。
3. store 的 data、mask、提交顺序、MMIO、uncache 和异常处理都属于 <code>StoreUnit / StoreQueue / Sbuffer / MainPipe</code> 的其他路径。

这个分界由源码接口直接证明：[DcacheStoreRequestIO](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:26) 只有三个字段；而 <code>StoreUnit</code> 自己也明确注释此次 DCache 访问 “not real dcache write”，只是查询 meta/tag [StoreUnit.scala:236](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:236)。

## 2. 从理论概念到有效代码

| 理论概念 | 在此处的准确含义 | 代码实体 / 信号 | 与常见教科书“store pipe”模型的差异 |
| --- | --- | --- | --- |
| 非阻塞访问 | STA 发射普通 store 时不等待此 tag/meta 探测端口的 ready | <code>StoreUnit.io.dcache.req.valid := s0_fire</code>，普通 store 不用 <code>dcache.req.ready</code> 反压 [StoreUnit.scala:236](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:236) | 不是一个保存写数据、等待 cache 接受再重试的 store buffer |
| cache hit | tag 命中且 coherence 对 <code>cmd</code> 已有足够权限，执行 <code>onAccess</code> 后状态不变 | <code>s1_tag_match</code>、<code>s1_has_permission</code>、<code>s1_new_hit_coh === s1_hit_coh</code> [StorePipe.scala:126](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:126) | tag 命中但需升级权限也会被当作 <code>!s1_hit</code>，不是“数据行不存在”的唯一含义 |
| store prefetch / write hint | 以 <code>DCACHE_PREFETCH_SOURCE + M_PFW</code> 请求 MissQueue 获取或升级一个 cache line | <code>io.miss_req</code> [StorePipe.scala:167](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:167) | 请求中不携带真实 store data/mask，不能改变程序可见存储值 |
| 精确提交 | 架构 store 仅在 ROB/StoreQueue 条件满足后进入 SBuffer | <code>StoreQueue</code>、<code>Sbuffer</code>、<code>MainPipe.store_req</code> | 不由 <code>StorePipe</code> 的 <code>resp</code> 或预取成功决定 |
| 回压 | S0 必须同时获得 meta/tag 读端口；MissQueue 拒绝不会使 S2 保持 | <code>req.ready = meta.ready && tag.ready</code>；无 miss holding register [StorePipe.scala:91](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:91) | 这是 best-effort 提示通路，不是完成可靠传输的写数据通路 |

## 3. 模块定位与完整数据/控制关系

### 3.1 谁调用它，为什么调用它

<code>MemBlock</code> 对每个 STA 建一个 <code>StoreUnit</code>，并把 <code>StoreUnit.io.dcache</code> 接到 DCache 的 <code>lsu.sta(i)</code> 端口 [MemBlock.scala:1247](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1247)。DCache wrapper 再把同编号端口接到 <code>StorePipe(i)</code> [DCacheWrapper.scala:1461](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1461)。

<code>StoreUnit</code> S0 的来源优先级是：misalign fragment、vector store、普通 RS store、硬件 store-prefetch；只有最后一种预取来源必须等待 <code>dcache.req.ready</code> [StoreUnit.scala:91](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:91)。普通 store 即使 StorePipe 未接受查询，仍可沿 LSQ/SQ 路径继续。

| 追问 | 源码答案 |
| --- | --- |
| Who | <code>StoreUnit(i)</code> 经 <code>DCacheWrapper.lsu.sta(i)</code> 驱动 <code>StorePipe(i)</code>；实例数由 <code>StorePipelineWidth</code> 决定 |
| Why | 在不阻塞普通 store 地址发射的前提下，尽早知道该 line 的 tag/coherence 写意图是否已满足，并在可选配置中为 miss 发 M_PFW |
| From | 输入是 <code>cmd/vaddr/instrtype</code> 及 StoreUnit 的 PA/kill sideband；没有 data、mask、ROB ID 或 commit token |
| How | S0 发全 way tag/meta read；S1 以 PA + coherence 判断 hit；S2 回应并按开关发预取请求 |
| To | 结果回 StoreUnit/LSQ 用于 miss/训练信息；可选 M_PFW 经 MissQueue；真实写入则离开这条路径，走 SQ/SBuffer/MainPipe |

~~~mermaid
flowchart LR
  RS[issueSta / vector / misalign] --> SU0[StoreUnit S0]
  SBPF[Sbuffer store_prefetch] --> SU0
  SU0 -->|cmd=M_PFW, vaddr, instrtype| SP[StorePipe]
  SU0 -->|DTLB paddr, s1_kill, s2_kill| SP
  SP -->|meta/tag all-way read| ARR[DCache tag + coherence meta]
  ARR -->|tag/meta response| SP
  SP -->|resp: miss/replay/tag_error| SU2[StoreUnit S2 / LSQ replenish]
  SP -->|optional M_PFW hint| ARB[MissReq arbiter]
  ARB --> MQ[MissQueue]

  SU0 -. address, data, mask, uop .-> SQ[StoreQueue]
  SQ -->|committed cacheable store| SBUF[Sbuffer]
  SBUF -->|M_XWR + addr/vaddr/data/mask| MP[MainPipe]
  MP --> DW[data_write into L1D]
~~~

实线的上半部分是本文 StorePipe 主体；下半部分是为了划清“真实写入”而追踪的独立路径。两者可能针对同一条程序 store，但没有共享 data/mask 接口。

### 3.2 StorePipe 的接口清单

| 接口 | 方向（相对 StorePipe） | 关键字段 / 握手 | 功能与责任 |
| --- | --- | --- | --- |
| <code>io.lsu.req</code> | 输入 Decoupled | <code>cmd, vaddr, instrtype, valid/ready</code> | 从 STA 接收 lookup / prefetch 请求；定义见 [StorePipe.scala:26](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:26) |
| <code>io.lsu.s1_paddr</code> | 输入 sideband | 物理地址 | S1 物理 tag 比较，而非在 StorePipe 内作地址翻译 [StorePipe.scala:32](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:32) |
| <code>io.lsu.s1_kill</code> | 输入 sideband | TLB miss / 早期异常等 | 抑制 S2 valid |
| <code>io.lsu.s2_kill</code> | 输入 sideband | uncache、晚期异常或 redirect 等 | 写入 <code>miss_req.cancel</code>；不让 MissQueue 分配该提示 |
| <code>io.lsu.s2_pc</code> | 输入 sideband | debug PC | 传给 MissReq 的 <code>pc</code> |
| <code>io.lsu.resp</code> | 输出 Decoupled | <code>miss, replay, tag_error</code> | 返回 lookup 结果；本体固定 <code>replay=false</code>、<code>tag_error=false</code> [StorePipe.scala:157](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:157) |
| <code>meta_read/tag_read</code> | 输出 Decoupled | <code>idx, way_en</code> | 同周期请求所有 way 的 coherence meta 和 tag |
| <code>meta_resp/tag_resp</code> | 输入 | 每 way meta/tag | 供 S1 比较；没有 data-array 读口 |
| <code>miss_req</code> | 输出 Decoupled | <code>source, cmd, addr, req_coh, cancel, pc</code> | 只产生可选 <code>M_PFW</code> 提示 |
| <code>replace_way/replace_access</code> | 输出 | replacement 相关 | 本模块把 <code>set.valid</code> 和 <code>replace_access.valid</code> 均固定为 false [StorePipe.scala:139](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:139) |
| <code>error</code> | 输出 | <code>Valid[L1CacheErrorInfo]</code> | 固定为零，不报告 ECC/tag error [StorePipe.scala:85](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:85) |

类形参 <code>id</code> 出现在声明中但在该文件内没有被使用；它不是请求 ID、ROB ID 或 MSHR ID [StorePipe.scala:59](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:59)。

## 4. 配置、实例数与地址索引

### 4.1 Kunminghu V2 的源码配置

| 参数 / 配置 | 代码值 | 对 StorePipe 的含义 |
| --- | --- | --- |
| <code>StorePipelineWidth</code> | 默认 2 [Parameters.scala:214](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:214) | Wrapper 会按此值构造两个 <code>StorePipe</code>，且要求等于后端 <code>StaCnt</code> [Parameters.scala:847](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:847) |
| <code>EnableStorePrefetchAtIssue</code> | 默认 false | 决定普通 STA 的 <code>!hit</code> 是否能发 M_PFW |
| <code>EnableStorePrefetchAtCommit</code> | 默认 false | 决定 SBuffer commit-side prefetch 是否产生 |
| <code>EnableStorePrefetchSPB</code> | 默认 false | 决定 Store prefetch buffer/训练路径是否启用 |
| <code>KunminghuV2Config</code> | 继承 <code>DefaultConfig</code> [Configs.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481) | 未见这三个开关被置 true；这是源码配置结论，不替代某个外部 elaboration 的实测 |
| 默认 KHV2 L1D | <code>WithNKBL1D(64, ways=4)</code>，即 64 KiB、4 ways、64 B line [Configs.scala:460](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:460) | 共有 256 sets；与 <code>DCacheParameters</code> 的通用构造默认值不同，分析 KHV2 时应采用该覆盖 |

三项 Store L1 prefetch 开关的声明默认均为 false [Parameters.scala:246](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:246)。<code>StorePipe</code> 仍会被实例化 [DCacheWrapper.scala:1043](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1043)，但 wrapper 的 <code>StorePrefetchL1Enabled = AtCommit || AtIssue || SPB</code> 为 false 时，会将 StorePipe 的 meta-read、tag-read、miss_req ready 关闭 [DCacheWrapper.scala:1000](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1000) [DCacheWrapper.scala:1147](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1147) [DCacheWrapper.scala:1250](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1250) [DCacheWrapper.scala:1489](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1489)。

因此，按源码中的标准 KHV2 配置，StorePipe 是“已实例化但无活跃握手”的可选功能。不要把这项默认关闭的优化误写成普通 store 的必经 correctness path。

### 4.2 由地址到 set / tag / block

通用 L1 cache helper 定义：

~~~scala
def get_tag(addr: UInt) = get_phy_tag(addr)
def get_idx(addr: UInt) = addr(untagBits - 1, blockOffBits)
def get_block_addr(addr: UInt) = (addr >> blockOffBits) << blockOffBits
~~~

证据：[L1Cache.scala:81](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/L1Cache.scala:81)。对标准 KHV2 的 256 sets、64 B line：

| 项 | 表达式 | 标准配置下的位域 | StorePipe 用途 |
| --- | --- | --- | --- |
| line offset | <code>blockOffBits = log2(64)</code> | <code>[5:0]</code> | 发 M_PFW 时被清零 |
| set index | <code>get_idx(vaddr)</code> | <code>vaddr[13:6]</code> | S0 同时送到 tag/meta 读端口 |
| page-compatible tag cut | <code>pgUntagBits = min(14, 12)</code> | 12 | S1 比较 <code>tag_resp</code> 与 <code>paddr &gt;&gt; 12</code> |
| prefetch block address | <code>get_block_addr(s2_paddr)</code> | <code>{paddr[...:6], 6'b0}</code> | MissQueue 的 cache-line 粒度地址 |

StorePipe 本体没有显式 alias 校验、跨页判断或地址翻译状态：它用 VA 取 index、使用外部 STA 在 S1 给出的 PA 做 tag 比较。这是 VIPT 类访问划分的一个局部事实；别把它外推为 StorePipe 自己解决了所有 synonym/alias 情况。

## 5. 三个流水阶段：逐信号、逐条件解析

### 5.1 S0：双阵列读取申请

~~~scala
val s0_valid = io.lsu.req.valid
val s0_fire = io.lsu.req.fire
io.meta_read.valid := s0_valid
io.meta_read.bits.idx := get_idx(io.lsu.req.bits.vaddr)
io.meta_read.bits.way_en := ~0.U(nWays.W)
io.tag_read.valid := s0_valid
io.tag_read.bits.idx := get_idx(io.lsu.req.bits.vaddr)
io.tag_read.bits.way_en := ~0.U(nWays.W)
io.lsu.req.ready := io.meta_read.ready && io.tag_read.ready
~~~

证据：[StorePipe.scala:91](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:91)。

| 信号 | 来源 → 去向 | 含义 | fire / 背压结论 |
| --- | --- | --- | --- |
| <code>io.lsu.req.valid</code> | StoreUnit → StorePipe | 有一个查询候选 | 直接成为两个 array read 的 valid |
| <code>meta_read.valid/tag_read.valid</code> | StorePipe → DCache arrays | 都读同一个 set、所有 ways | 本体将两个读请求同时提出 |
| <code>io.lsu.req.ready</code> | arrays → StorePipe → StoreUnit | 接受条件 | 只有 meta 与 tag 两端 ready 的合取；<code>fire = valid &amp;&amp; ready</code> |
| <code>s0_fire</code> | S0 → S1 | 真正被 StorePipe 接受 | 唯一允许 S1 寄存请求的条件 |

注意一个接口级 caveat：两个 array read 的 <code>valid</code> 均由上游 valid 直接驱动，而 S1 只由两个 ready 的合取推进。Wrapper 设计意图是它们配套使用；但 StorePipe 本身没有“单侧 fire 时撤销另一侧”或断言。是否存在非对称 ready 的动态场景，需要生成 RTL/波形验证，不能只靠本文件宣称原子性。

### 5.2 S1：物理 tag、meta coherence 与“真正的 hit”

~~~scala
val s1_valid = RegNext(s0_fire)
val s1_req = RegEnable(s0_req, s0_fire)
s1_tag_match := wayMap { wayid =>
  s1_tag_resp(wayid) === get_tag(s1_paddr) &&
  s1_meta_resp(wayid).coh.isValid()
}.asUInt
val (s1_has_permission, _, s1_new_hit_coh) =
  s1_hit_coh.onAccess(s1_req.cmd)
val s1_hit = s1_has_permission &&
  s1_new_hit_coh === s1_hit_coh && s1_tag_match.orR
~~~

证据：[StorePipe.scala:115](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:115)。

S1 的判断可拆成四层：

1. <code>s1_paddr</code> 来自 STA 的 DTLB 结果；StorePipe 从不产生 PA。
2. 每 way 必须同时满足“tag 等于物理 tag”与“coherence meta 有效”。
3. 若没有 tag match，代码使用 <code>ClientMetadata.onReset</code> 作为 fake meta，因此后续权限判断会自然走非 hit。
4. 即使 tag match，<code>onAccess(cmd)</code> 也必须表明当前 state 已有权限且 access 后 state 不变。一个命中但需要权限升级的行会得到 <code>s1_hit=false</code>，从而可能产生写意图预取。

这也是为什么本文用“写意图未满足”而不是简单“cache line 不在 L1”描述 <code>miss</code>。

### 5.3 S2：返回 lookup 结果并可选提出 M_PFW

~~~scala
val s2_valid = RegNext(s1_valid) && RegNext(!io.lsu.s1_kill)
io.lsu.resp.valid := s2_valid
io.lsu.resp.bits.miss := !s2_hit
io.lsu.resp.bits.replay := false.B
io.lsu.resp.bits.tag_error := false.B

io.miss_req.bits.source := DCACHE_PREFETCH_SOURCE.U
io.miss_req.bits.pf_source := L1_HW_PREFETCH_STORE
io.miss_req.bits.cmd := MemoryOpConstants.M_PFW
io.miss_req.bits.addr := get_block_addr(s2_paddr)
io.miss_req.bits.cancel := io.lsu.s2_kill
~~~

证据：[StorePipe.scala:149](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:149)。

| S2 输出 | 精确行为 | 不能推导出的结论 |
| --- | --- | --- |
| <code>lsu.resp.valid</code> | <code>s2_valid</code>，即 S1 接受后的下一拍且未被 S1 kill | 不代表真实 store 已写入或已提交 |
| <code>resp.miss</code> | <code>!s2_hit</code> | 不只等价于 tag miss，可能是 permission upgrade |
| <code>resp.replay</code> | 恒为 false | StorePipe 不实现请求重放协议 |
| <code>resp.tag_error</code> | 恒为 false | StorePipe 不报告 tag ECC 错误 |
| <code>miss_req</code> | 固定 prefetch source 和 <code>M_PFW</code>，地址 64B 对齐 | 没有真实 store 的 data / mask / store ID |

<code>EnableStorePrefetchAtIssue</code> 为 true 时，所有 <code>s2_valid &amp;&amp; !s2_hit</code> 都会尝试提出 M_PFW；否则只有输入 <code>instrtype == DCACHE_PREFETCH_SOURCE</code> 的 miss 才尝试提出 [StorePipe.scala:167](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:167)。

## 6. valid/ready/fire 时间线、吞吐和局部状态

### 6.1 StorePipe 不是 FSM，也没有重试队列

它只有随 <code>s0_fire</code> 推进的寄存器：

| 保存项 | 形成方式 | 生命周期 |
| --- | --- | --- |
| <code>s1_valid</code>、<code>s1_req</code> | <code>RegNext(s0_fire)</code>、<code>RegEnable(s0_req, s0_fire)</code> | S0 接受后保留到 S1 |
| <code>s2_valid</code> | <code>RegNext(s1_valid) &amp;&amp; RegNext(!s1_kill)</code> | S1 后一拍产生响应 |
| <code>s2_req/hit/paddr/hit_coh/is_prefetch</code> | 以 <code>s1_valid</code> 为 enable 的寄存器 | 供 S2 返回 / 发 hint |
| replacement 状态 | 无 | <code>replace_way.set.valid=false</code>，<code>replace_access.valid=false</code> |
| miss holding / retry 状态 | 无 | <code>miss_req.valid</code> 只依赖当前 S2，不会保持到 ready |

源码未为这些 StorePipe 寄存器给出显式 <code>RegInit</code>。复位后最早几拍的有效性依赖上游复位时序和生成实现；本文不把“它们一定被复位为 0”当成已证明事实。

### 6.2 一个无 kill、无 miss backpressure 的结构时序

下图表达的是本文件的寄存级关系：S0 在 C0 fire，S1 在下一拍有效，S2 / response 在再下一拍有效。外部 SRAM 的具体读延迟和实际 elaboration 时钟边界仍应以波形核验。

~~~wavedrom
{
  "signal": [
    { "name": "clk",                "wave": "p...." },
    { "name": "lsu.req.valid",      "wave": "010.." },
    { "name": "lsu.req.ready",      "wave": "010.." },
    { "name": "s0_fire",            "wave": "010.." },
    { "name": "s1_valid",           "wave": "0010." },
    { "name": "lsu.s1_kill",        "wave": "0000." },
    { "name": "s2_valid",           "wave": "00010" },
    { "name": "lsu.resp.valid",     "wave": "00010" },
    { "name": "miss_req.valid",     "wave": "00010" },
    { "name": "miss_req.ready",     "wave": "11111" },
    { "name": "miss_req.fire",      "wave": "00010" }
  ]
}
~~~

以一个 StorePipe instance 计，S0 的理想接收能力是每周期一项，但前提是 tag/meta 都 ready。标准参数有两个 STA/StorePipe channels，因此活跃配置下的理论上限是每周期两个互不冲突的查询；这不是对预取、MissQueue 或真实 data-write 吞吐的保证。

### 6.3 MissQueue 不 ready 时，发生什么

~~~wavedrom
{
  "signal": [
    { "name": "clk",                "wave": "p...." },
    { "name": "s2_valid",           "wave": "0010." },
    { "name": "s2_hit",             "wave": "0000." },
    { "name": "miss_req.valid",     "wave": "0010." },
    { "name": "miss_req.ready",     "wave": "0000." },
    { "name": "miss_req.fire",      "wave": "0000." },
    { "name": "next s2_valid",      "wave": "0000." }
  ]
}
~~~

这不是自动重发：本体只统计 <code>store_miss_prefetch_not_fire</code>，没有 valid 保持寄存器 [StorePipe.scala:192](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:192)。被拒绝的只是这次提示；普通 store 的地址/data 已经在另一条 LSQ/SQ 路径中继续。

## 7. DCache 资源冲突与仲裁

### 7.1 tag/meta 读端口

| 冲突场景 | 代码控制 | 对 StorePipe 的效果 |
| --- | --- | --- |
| L1 Store prefetch 功能关闭 | StorePipe meta/tag ready 被置 false [DCacheWrapper.scala:1147](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1147) [DCacheWrapper.scala:1250](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1250) | S0 无法 fire；普通 STA 不因此停住 |
| tag 写意图 | <code>tag_write_intend = mainPipe.io.tag_write_intend</code>，StorePipe tag read ready 取 <code>!tag_write_intend</code> [DCacheWrapper.scala:1234](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1234) | 即便 meta ready，也因 S0 要求二者合取而无法接受 |
| hybrid LDU/STU 共享端口 | meta/tag 中先选 load valid，否则才接 StorePipe [DCacheWrapper.scala:1119](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1119) [DCacheWrapper.scala:1270](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1270) | 同周期 load 优先，StorePipe 被反压 |
| replacement | StorePipe 计算 <code>s1_need_replacement</code> 但不消费 | 它不分配 victim、也不更新 replacement state |

### 7.2 MissQueue 端口优先级

Store prefetch 启用时，端口布局为 MainPipe、各 LoadPipe、各 StorePipe、Hybrid；源码明确说明“低编号优先” [DCacheWrapper.scala:1033](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1033)。StorePipe <code>w</code> 接在 <code>1 + LduCnt + w</code> [DCacheWrapper.scala:1489](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1489)。

<code>TreeArbiter</code> 以第一个 valid 选择，<code>MissReadyGen</code> 也要求所有更低编号端口没有 valid 才给本端 ready [DCacheWrapper.scala:865](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:865) [DCacheWrapper.scala:917](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:917)。因此：

- MainPipe 与 load miss 在固定编号上优先于 StorePipe prefetch。
- 若该通道属于 hybrid 单元，wrapper 还会先选择 hybrid load，再选择 hybrid store [DCacheWrapper.scala:1506](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1506)。
- StorePipe 没有 loser retry；仲裁饥饿至多降低预取机会，不能阻塞真实 store 写入。

## 8. kill、异常、MMIO 与恢复边界

### 8.1 两级 kill 的意义

<code>DCacheStoreIO</code> 把 STA 的两个阶段性 kill 明确暴露给 StorePipe [StorePipe.scala:32](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:32)：

| kill | 由谁产生 | StorePipe 如何消费 | 架构意义 |
| --- | --- | --- | --- |
| <code>s1_kill</code> | StoreUnit 的 TLB miss、已知 exception、MMIO/NC、redirect [StoreUnit.scala:420](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:420) | 延后一拍并与 <code>s2_valid</code> 相与 | 不给这一 lookup 产生 S2 response/hint |
| <code>s2_kill</code> | StoreUnit 的实际 uncache、晚期异常、redirect [StoreUnit.scala:504](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:504) | 保留 <code>s2_valid</code> / response，但置 <code>miss_req.cancel</code> | 不允许该 M_PFW 分配/合并 MSHR |

MissQueue 注释和实现都将一个请求的有效处理定义为 <code>req.valid &amp;&amp; !cancel</code> [MissQueue.scala:78](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:78)，分配/合并也显式检查 <code>!cancel</code> [MissQueue.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:529)。这说明 <code>s2_kill</code> 不会让 StorePipe 误发有效预取，但也提醒我们：StorePipe 的 <code>resp.valid</code> 本身没有被 <code>s2_kill</code> gate，LSQ/StoreUnit 还需按其自己的异常状态继续处理。

### 8.2 StorePipe 自己不产生恢复、错误或异常

- 没有 redirect 输入、没有 ROB pointer、没有 flush FSM。
- <code>resp.replay = false</code>、<code>resp.tag_error = false</code>、<code>error = 0</code>。
- 没有 PMP/PMA、page fault、access fault、MMIO decode 或 uncache request。
- 其 <code>miss_req</code> 恒为 <code>M_PFW</code>，不是 <code>M_XWR</code>。

因此，异常精确性必须归因于 StoreUnit、StoreQueue 和 ROB；不能把 “StorePipe 收到 kill” 描述成它独立完成了异常提交/回滚。

## 9. 真实 store 写入路径：为何它不属于 StorePipe

### 9.1 地址、data 和 mask 在 LSQ/SQ 中分离

<code>StoreUnit</code> 在普通流水线中形成 <code>s0_out.data</code> 和 <code>s0_out.mask</code>，并把 mask 独立送入 LSQ [StoreUnit.scala:247](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:247) [StoreUnit.scala:277](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:277)。S1 把地址结果送到 <code>io.lsq</code>，S2 再补充 miss/MMIO/异常等信息 [StoreUnit.scala:412](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:412) [StoreUnit.scala:535](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:535)。

<code>StoreQueue</code> 接收地址/掩码/uop 与独立的 store-data 输入；只有满足提交和 cacheable 条件的项经 DataBuffer 送往 SBuffer。该分离恰好解释了为何 StorePipe 的三个字段不足以实现真实写。

### 9.2 SBuffer 到 MainPipe 才是写入 L1D 的边界

~~~mermaid
sequenceDiagram
  participant SQ as StoreQueue
  participant SB as Sbuffer
  participant DC as DCacheWrapper
  participant MP as MainPipe
  participant DA as L1D data array
  SQ->>SB: committed M_XWR line request
  SB->>DC: addr, vaddr, data, mask
  DC->>MP: io.lsu.store.req <> mainPipe.store_req
  MP->>DA: data_write
  MP-->>DC: hit/replay response
  DC-->>SB: store response
~~~

SBuffer 形成的请求是 <code>M_XWR</code>，携带完整 cacheline 的 <code>addr/vaddr/data/mask</code> [Sbuffer.scala:693](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:693)。DCache wrapper 将它接到 <code>MainPipe.store_req</code> [DCacheWrapper.scala:1582](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1582)。MainPipe 把它转为自身请求，真正的 store 接收还会受 load data-read、阈值、<code>force_write</code>、probe/refill/atomic 冲突约束 [MainPipe.scala:231](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:231)。

这段路径才有 <code>data_write</code> 和 “cacheline evicted from Sbuffer to L1D” 性能事件 [MainPipe.scala:1073](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:1073)。它与 StorePipe 的 M_PFW hint 是不同协议、不同端口、不同完成条件。

## 10. 跨页、跨界、uncache / MMIO

### 10.1 StorePipe 的职责边界

| 场景 | 处理者 | 代码事实 | StorePipe 做 / 不做什么 |
| --- | --- | --- | --- |
| DTLB 翻译 | StoreUnit | 发 DTLB 写请求并取得 PA [StoreUnit.scala:215](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:215) | 只消费 <code>s1_paddr</code>；不保存 TLB 状态 |
| 跨 4 KiB 页 | StoreMisalignBuffer | <code>highPageAddress(12) != vaddr(12)</code> 检测 [StoreMisalignBuffer.scala:338](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:338) | 无页比较、无 split/reassembly |
| 跨 16 B / 非对齐 | StoreMisalignBuffer | 识别 <code>cross16BytesBoundary</code>，最多拆为两项 [StoreMisalignBuffer.scala:41](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:41) [StoreMisalignBuffer.scala:344](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:344) | 每个已拆 fragment 可独立进入 STA/StorePipe lookup；StorePipe 不保证它们的原子组合 |
| cache-line 边界 | 上游的地址/拆分与 SQ/SBuffer 路径 | StorePipe 仅对单一 <code>s2_paddr</code> 清 line offset | 无 <code>blockOffBits</code> 跨界检测、无两个 block 的协调状态 |
| MMIO / NC | StoreUnit + StoreQueue + uncache | S2 识别实际 uncache/MMIO 并 kill DCache hint [StoreUnit.scala:469](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:469)；uncache 有独立 FSM [MemBlock.scala:1445](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1445) | 不发 uncached store data request；只把 <code>s2_kill</code> 变成 prefetch cancel |
| 分裂项遇到 MMIO/NC | StoreMisalignBuffer | 任一 split uncache 时报告 <code>storeAddrMisaligned</code> 并转软件处理 [StoreMisalignBuffer.scala:542](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:542) | 不是 StorePipe 的错误恢复逻辑 |

跨页时，StoreMisalignBuffer 以 <code>s_idle → s_split → s_req → s_resp → s_wb → s_block</code> 管理两个 fragment，并等待 StoreQueue/ROB 的必要条件 [StoreMisalignBuffer.scala:136](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:136)。MemBlock 只把这个 split 接给 StoreUnit 0 [MemBlock.scala:1281](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1281)，进一步证明拆分不是 StorePipe 内部工作。

### 10.2 不能由 StorePipe 推导的 cache-line 原子性

<code>get_block_addr(s2_paddr)</code> 只把当前 fragment 的 PA 对齐到一个 line。若一条上游访问需要两项，它会得到两次独立 lookup/hint。源码没有在 StorePipe 内发现：

- 两个 fragment 的关联 ID；
- “两项都成功才提交”的状态；
- line-crossing 的重组 buffer；
- 地址或 data 的原子锁定。

所以对跨 cache-line 或 vector 非标量访问的最终可见顺序，必须继续在 StoreMisalignBuffer、StoreQueue、SBuffer 和 MainPipe 波形中验证，不能用 StorePipe 的 S2 response 下结论。

## 11. 预取来源与训练路径

StoreUnit 给 <code>instrtype</code> 的规则是：普通/misalign/vector store 使用 <code>STORE_SOURCE</code>，hardware prefetch 使用 <code>DCACHE_PREFETCH_SOURCE</code> [StoreUnit.scala:117](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:117)。这是 StorePipe 在 AtIssue 关闭时仍允许“预取来源的 miss”发 M_PFW 的依据。

SBuffer 的 commit-side 路径在 <code>EnableStorePrefetchAtCommit</code> 时把 prefetcher 请求或已提交项构成 <code>store_prefetch</code>，再由 MemBlock 接到相应 StoreUnit [Sbuffer.scala:403](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:403) [MemBlock.scala:1272](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1272)。

类注释提到 SMS training，但 StorePipe 本体没有 SMS/train IO；实际 <code>EnableStorePrefetchSMS</code> 的训练逻辑在 StoreUnit S2，且以 <code>io.dcache.resp.fire</code>、非 MMIO/NC、非 TLB miss 等条件门控 [StoreUnit.scala:550](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:550)。本文以有效连接而不是过时/宽泛注释归属这项功能。

## 12. 可观察性、Difftest 与验证计划

### 12.1 本体已有性能观测点

| 事件 | 条件 | 用途 |
| --- | --- | --- |
| <code>s0_valid</code> | <code>lsu.req.valid</code> | 查询候选量 |
| <code>s0_valid_not_ready</code> | valid 且非 ready | tag/meta 资源反压 |
| <code>store_fire</code> | <code>s2_valid &amp;&amp; !s2_kill</code> | 已完成有效 lookup |
| <code>sta_hit / sta_miss</code> | S2 hit/miss 且未 kill | coherence-aware hit/miss 统计 |
| <code>store_miss_prefetch_fire</code> | <code>miss_req.fire &amp;&amp; !cancel</code> | 真正被下游接受的 M_PFW |
| <code>store_miss_prefetch_not_fire</code> | valid 且非 ready 且未 cancel | 被仲裁/资源拒绝的预取机会 |

证据：[StorePipe.scala:192](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:192)。

StorePipe 不直接产生架构 store 的 Difftest 事件。store event 的 Difftest 信息在 MemBlock 中围绕 <code>sbuffer.io.diffStore</code>、LSQ 和 vector store 信息组织 [MemBlock.scala:1531](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1531)。这同样支持“预取探测”和“架构存储效果”分层的判断。

### 12.2 建议的最小验证矩阵

| 场景 | 激励 / 配置 | 应观察的关键波形 | 代码判据 |
| --- | --- | --- | --- |
| 默认配置普通 store | 三个 Store prefetch 开关均 false | StoreUnit 可发射；StorePipe <code>req.ready</code> 为 0；真实写仍进入 SQ/SBuffer | [Parameters.scala:246](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/Parameters.scala:246)、[StoreUnit.scala:236](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:236) |
| AtIssue miss | 仅打开 <code>EnableStorePrefetchAtIssue</code> | <code>s0_fire → s1_valid → s2_valid</code>；<code>miss_req.cmd=M_PFW</code> | [StorePipe.scala:149](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:149) |
| tag hit 但权限不足 | 令命中行的 coherence state 对写需要变化 | <code>tag_match=1</code> 但 <code>s1_hit=0</code>，按配置尝试 M_PFW | [StorePipe.scala:126](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:126) |
| tag-write 冲突 | MainPipe tag write intent 有效 | <code>tag_read.ready=0</code>，导致 <code>lsu.req.ready=0</code> | [DCacheWrapper.scala:1234](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1234) |
| MissQueue 仲裁拒绝 | 同周期 MainPipe/load miss 先 valid | StorePipe <code>miss_req.valid=1, ready=0</code> 一拍后消失；not_fire 计数 | [DCacheWrapper.scala:897](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:897)、[StorePipe.scala:195](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:195) |
| TLB miss / early fault | 让 StoreUnit S1 产生 kill | StorePipe S2 valid 不出现 | [StoreUnit.scala:420](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:420) |
| MMIO / NC / late fault | 让 StoreUnit S2 kill | response 可见但 <code>miss_req.cancel=1</code>；MissQueue 不分配 | [StorePipe.scala:183](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:183)、[MissQueue.scala:529](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:529) |
| 真正 store data 写入 | 已提交、cacheable store | <code>Sbuffer</code> 发 <code>M_XWR</code> 且带 data/mask，MainPipe <code>data_write</code> | [Sbuffer.scala:693](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:693) |

## 13. 已证实结论、未证实项与阅读边界

### 已由源码直接证实

1. StorePipe 只查 tag/meta，不携带 data/mask，也不写 DCache data array。
2. S0 用 VA index 做全 way 读；S1 用 STA 提供的 PA 做 tag/coherence 判断；S2 回报 miss 并可发 M_PFW。
3. <code>!hit</code> 包含 coherence permission / state transition 需求，而不只是 tag 不命中。
4. S1 kill 抑制 S2 valid；S2 kill 将 M_PFW 设为 cancel，MissQueue 不会把 cancel 请求分配/合并。
5. StorePipe 自身没有 replay、tag error、replacement 更新、MSHR holding 或 retry 状态。
6. 当前源码标准 KHV2 参数下三项 Store L1 prefetch 默认关闭，故普通 store correctness 不依赖此管线活跃。

### 仍需按具体构建或波形核验

1. 某一个实际仿真/综合目标是否覆写了 Store prefetch 开关。
2. tag/meta 两端 ready 是否在所有可达配置下严格同步。
3. 特定 prefetch 被低优先级仲裁拒绝后，是否会被另一种 AtCommit/SPB 策略在未来再次产生；StorePipe 本体没有 retry，但系统其他来源可能另发请求。
4. vector 非标量或跨 cache-line 访问的最终 fragment 次序与 L2/DRAM 完成时间。
5. StorePipe 未显式初始化的寄存器在目标 reset 序列上的最早有效周期。

## 14. 总结

把 StorePipe 视为“STA 对 DCache 写意图的轻量 tag/meta probe + 可丢弃 M_PFW 预取器”是与源码一致的阅读方式。它提高命中/权限准备的机会，但不定义 store data 的保存、提交和写入。

如果问题是“这条 store 何时对 L1D / 外部内存可见”，应沿 <code>StoreQueue → Sbuffer → MainPipe</code> 追踪；如果问题是“这条 store 是否触发了 L1 写预取/权限准备”，才应沿 <code>StoreUnit ↔ StorePipe → MissQueue</code> 追踪。两条路径可共享地址语义，却不共享架构完成条件。

## 15. 主要源码入口

- [StorePipe.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/storepipe/StorePipe.scala:1)
- [DCacheWrapper.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1)
- [StoreUnit.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:1)
- [StoreMisalignBuffer.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:1)
- [StoreQueue.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1)
- [Sbuffer.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:1)
- [MainPipe.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:1)
- [MissQueue.scala](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:1)
