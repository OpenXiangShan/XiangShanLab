<!--
# 香山昆明湖 V2：CoupledL2 RXDAT 源码分析

> **结论先行。** `coupledL2.tl2chi.RXDAT` 不是一个缓存阵列、队列或有限状态机，而是每个 CHI CoupledL2 Slice 中的无状态接收适配点：它把一个已被顶层按 `txnID` 路由到本 Slice 的 `CHIDAT` 同时扇出为 RefillBuffer 写入和按 MSHR ID 匹配的响应。`RXDAT` 本体把 `ready` 恒置为 1，因此不会因 MSHR 或 RefillBuffer 满而在模块接口反压；真正决定数据能否被协议接受的是链路 L-Credit、顶层一项 Decoupled 管线、外部协议的 `txnID` 有效性，以及下游 MSHR 的生命周期。对当前 `KunminghuV2Config`，每个 Slice 是 64 B line、32 B CHI data beat、2 beat/refill、16 个 MSHR；A 通道只有至多 15 个可分配项，另一个保留给 SinkB。

本文只报告下列源码基线可定位的事实。设计文档只用于提出待核对的设计意图；每一个实现结论均回到本地 `kunminghu-v2` checkout 的 Scala 源码，不把设计文档、生成 RTL 或未采集波形当作实现证据。
-->

# XiangShan Kunminghu V2: CoupledL2 RXDAT Source Analysis

> **Conclusion first.** `coupledL2.tl2chi.RXDAT` is neither a cache array, queue, nor finite-state machine. It is the stateless receive-adaptation point in each CHI CoupledL2 Slice: a `CHIDAT` already routed to this Slice by the top level according to `txnID` is fanned out simultaneously to a RefillBuffer write and an MSHR-ID-matched response. The RXDAT body ties `ready` to 1, so an MSHR or RefillBuffer becoming full cannot backpressure at this module interface. Protocol acceptance of data is actually determined by link L-Credit, the one-entry top-level Decoupled pipeline, validity of the external-protocol `txnID`, and the downstream MSHR lifetime. Under the current `KunminghuV2Config`, each Slice has 64-B lines, 32-B CHI data beats, two beats per refill, and 16 MSHRs; Channel A can allocate at most 15, with one reserved for SinkB.

This note reports only facts traceable to the source baselines below. The design document is used only to identify design intent to check. Every implementation conclusion returns to Scala source in the local `kunminghu-v2` checkout; neither the design document, generated RTL, nor uncollected waveforms is treated as implementation evidence.

<!--
## 1. 范围、版本与证据边界

| 项目 | 本文口径 |
|---|---|
| 目标模块 | `coupledL2.tl2chi.RXDAT`，不是 `openLLC.chi.RXDAT`，也不是旧 TileLink HuanCun 的任一模块 |
| 源码根目录 | `/home/yanyusong/xs-memory-env/XiangShan` |
| 主仓库基线 | `kunminghu-v2`，`e12436c7cba86b195deec24981976d78bc263661` |
| 子模块基线 | `coupledL2` 为 `fb5469838c8902b6cb33992c0a30ee3d446e4453`；`huancun` 为 `65ef077373ecf398b4cecdea06b65ef9b8d79044` |
| 配置口径 | `KunminghuV2Config`：`L2CacheConfig("1MB", banks = 4, tp = false)` 加 `WithCHI`，而非 non-CHI 的 TL2TL 配置。[【Configs.scala:481】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481) |
| 源码树状态 | 分析开始时已有 `difftest` 子模块修改和 `src/main/resources/aia/` 未跟踪内容；本文未修改被分析源码树 |
| skill 同步检查 | 按 `analyze-xiangshan-kunminghu` 的 7 天保护规则执行；距离上次检查约 0.22 天，因此跳过同步，未更新 Design Doc 或课程仓库 |
| Design Doc | 参考 `/home/yanyusong/XiangShan-Design-Doc/docs/zh` 的 RXDAT/CoupledL2 描述，但不复制其文字，也不以它取代源码 |
| 波形与 RTL | 本次未生成 Verilog、仿真或 FST；WaveDrom 是由源码的握手和寄存器关系导出的检查示意，不能当作实测时序 |

### 1.1 有效实例化链

`KunminghuV2Config` 打开 `EnableCHI`；`L2Top` 因而选择 `TL2CHICoupledL2`，而不是 `TL2TLCoupledL2`。`BaseCoupledL2Imp` 对每个 bank 建立一个 `tl2chi.Slice`，Slice 再实例化目标 RXDAT。[【L2Top.scala:130】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:130) [【CoupledL2.scala:419】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:419) [【Slice.scala:49】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:49)

```text
XSTile.io.chi
  -> L2Top.io.chi
  -> TL2CHICoupledL2.io_chi
  -> LinkMonitor (physical CHI flit <-> Decoupled)
  -> Pipeline(depth = 1)
  -> txnID 路由: cacheable Slice 或 MMIOBridge
  -> tl2chi.Slice.io.out.rx.dat
  -> RXDAT.io.out
  -> { RefillBuffer.w[0], MSHRCtl.resps.rxdat }
```

顶层 CHI 端口在 L2Top 被连接到实际的 CHI L2 实例，XSTile 再将其导出。[【L2Top.scala:368】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:368) [【XSTile.scala:228】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:228)

### 1.2 名称相同但不应混淆的对象

| 名称 | 是否本文目标 | 代码依据 | 正确关系 |
|---|---|---|---|
| `coupledL2.tl2chi.RXDAT` | 是 | [【RXDAT.scala:26】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:26) | 每个 CHI Slice 的缓存回填接收适配器 |
| `openLLC.chi.RXDAT` | 否 | `openLLC` 包内独立类 | 同名，不共享本节的数据路径或状态 |
| `huancun.HuanCun` | 否 | [【HuanCun.scala:180】](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/HuanCun.scala:180) | 旧 TileLink 方向的 HuanCun LazyModule，具有 `TLAdapterNode`，不例化 CHI RXDAT |
| `MMIOBridge` | 否，但与路由并列 | [【TL2CHICoupledL2.scala:258】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:258) | 顶层收到 MMIO RXDAT 时走该路径，不进入目标 RXDAT |

`coupledL2` 确实导入 HuanCun 的参数/类型，例如 `CacheParameters`、`AliasKey` 与 `BankBitsKey`。这是类型和配置复用，不是 RXDAT 的父子实例化或 flit 连接。[【L2Param.scala:26】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:26) [【CoupledL2.scala:34】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:34)
-->

## 1. Scope, Version, and Evidence Boundary

| Item | Basis used here |
|---|---|
| Target module | `coupledL2.tl2chi.RXDAT`, not `openLLC.chi.RXDAT` and not any module in the legacy TileLink HuanCun |
| Source root | `/home/yanyusong/xs-memory-env/XiangShan` |
| Main-repository baseline | `kunminghu-v2`, `e12436c7cba86b195deec24981976d78bc263661` |
| Submodule baselines | `coupledL2` is `fb5469838c8902b6cb33992c0a30ee3d446e4453`; `huancun` is `65ef077373ecf398b4cecdea06b65ef9b8d79044` |
| Configuration basis | `KunminghuV2Config`: `L2CacheConfig("1MB", banks = 4, tp = false)` plus `WithCHI`, rather than the non-CHI TL2TL configuration. [Configs.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481) |
| Source-tree state | Existing `difftest` submodule changes and untracked `src/main/resources/aia/` content were present when analysis began; the analyzed source tree was not modified |
| Skill synchronization check | The `analyze-xiangshan-kunminghu` seven-day protection rule was applied. The previous check was about 0.22 days ago, so synchronization was skipped and neither the Design Doc nor course repository was updated |
| Design Doc | RXDAT/CoupledL2 descriptions under `/home/yanyusong/XiangShan-Design-Doc/docs/zh` were consulted, but neither copied nor used in place of source |
| Waveforms and RTL | No Verilog, simulation, or FST was generated. The WaveDrom diagram is a check sketch derived from source handshakes and register relationships, not measured timing |

### 1.1 Effective instantiation chain

`KunminghuV2Config` enables `EnableCHI`; `L2Top` therefore selects `TL2CHICoupledL2` rather than `TL2TLCoupledL2`. `BaseCoupledL2Imp` builds one `tl2chi.Slice` per bank, and each Slice instantiates the target RXDAT. [L2Top.scala:130](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:130) [CoupledL2.scala:419](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:419) [Slice.scala:49](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:49)

```text
XSTile.io.chi
  -> L2Top.io.chi
  -> TL2CHICoupledL2.io_chi
  -> LinkMonitor (physical CHI flit <-> Decoupled)
  -> Pipeline(depth = 1)
  -> txnID routing: cacheable Slice or MMIOBridge
  -> tl2chi.Slice.io.out.rx.dat
  -> RXDAT.io.out
  -> { RefillBuffer.w[0], MSHRCtl.resps.rxdat }
```

The top-level CHI port is connected to the actual CHI L2 instance in L2Top and exported again by XSTile. [L2Top.scala:368](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:368) [XSTile.scala:228](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/xiangshan/XSTile.scala:228)

### 1.2 Same-name objects that must not be confused

| Name | Target of this note? | Source basis | Correct relationship |
|---|---|---|---|
| `coupledL2.tl2chi.RXDAT` | Yes | [RXDAT.scala:26](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:26) | Cache-refill receive adapter in each CHI Slice |
| `openLLC.chi.RXDAT` | No | Independent class in the `openLLC` package | Same name; it shares neither this section's data path nor state |
| `huancun.HuanCun` | No | [HuanCun.scala:180](/home/yanyusong/xs-memory-env/XiangShan/huancun/src/main/scala/huancun/HuanCun.scala:180) | Legacy TileLink-oriented HuanCun LazyModule with `TLAdapterNode`; it does not instantiate CHI RXDAT |
| `MMIOBridge` | No, but parallel to routing | [TL2CHICoupledL2.scala:258](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:258) | MMIO RXDAT received at the top level uses this path and does not enter the target RXDAT |

`coupledL2` does import HuanCun parameters/types such as `CacheParameters`, `AliasKey`, and `BankBitsKey`. This is type/configuration reuse, not parent-child instantiation or flit connectivity for RXDAT. [L2Param.scala:26](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:26) [CoupledL2.scala:34](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:34)

<!--
## 2. 源码证据与理论到实现映射

| 理论概念 | 当前实现中的拥有者 | 源码可观察事实 | 不应外推的结论 |
|---|---|---|---|
| 非阻塞 cache miss 跟踪 | `MSHRCtl` 和每个 `MSHR` | `mshrsAll = cacheParams.mshrs`；每个 MSHR 有 `status.valid`，RXDAT 按 ID 投递响应。[【CoupledL2.scala:127】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:127) [【MSHRCtl.scala:142】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:142) | RXDAT 自己不分配、不释放 MSHR，也不保存 miss 地址 |
| 数据回填暂存 | `MSHRBuffer` | `Reg(Vec(mshrsAll, Vec(beatSize, UInt(...))))`，每个 entry 对应一个 MSHR。[【MSHRBuffer.scala:39】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39) | 它不是有 `full/empty` 握手的 FIFO，状态完整性由 MSHR 协议控制 |
| ready/valid 背压 | LinkMonitor/Pipeline/`RXDAT.io.out` | RXDAT 把 `ready` 固定为真；物理链路侧仍由 L-Credit、RUN 状态和上游 `flitv` 约束。[【RXDAT.scala:86】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:86) [【LinkLayer.scala:213】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:213) | 不能把“模块内 `ready=1`”表述为外部 CHI 永远能传输 |
| 两拍 line refill | CHIDAT、RXDAT、MSHRBuffer、MSHR | 当前配置为 64 B line 和 32 B beat，`beatSize=2`；RXDAT 用 `dataID==00/10` 形成 beat mask。[【L2Param.scala:69】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:69) [【CoupledL2.scala:50】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:50) [【RXDAT.scala:37】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:37) | 不应把 CHI 的 16 B `DataID` 粒度注释简单等同于本实现的 32 B storage beat |
| 一致性并发控制 | MSHR 状态与 `RXSNP` | 收到第一拍后，`w_grantfirst` 参与同地址 snoop 阻塞。[【MSHR.scala:1167】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1167) [【RXSNP.scala:57】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:57) | RXDAT 没有地址，不能独立判断同 line 冲突 |
-->

## 2. Source Evidence and Theory-to-Implementation Mapping

| Theoretical concept | Owner in the current implementation | Observable source fact | Conclusion that must not be extrapolated |
|---|---|---|---|
| Non-blocking cache-miss tracking | `MSHRCtl` and each `MSHR` | `mshrsAll = cacheParams.mshrs`; every MSHR has `status.valid`, and RXDAT delivers responses by ID. [CoupledL2.scala:127](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:127) [MSHRCtl.scala:142](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:142) | RXDAT itself neither allocates nor releases an MSHR and does not retain the miss address |
| Temporary data-refill storage | `MSHRBuffer` | `Reg(Vec(mshrsAll, Vec(beatSize, UInt(...))))`, with one entry per MSHR. [MSHRBuffer.scala:39](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:39) | It is not a FIFO with `full/empty` handshakes; MSHR protocol controls state integrity |
| ready/valid backpressure | LinkMonitor/Pipeline/`RXDAT.io.out` | RXDAT ties `ready` high; on the physical-link side, L-Credit, RUN state, and upstream `flitv` still constrain transfers. [RXDAT.scala:86](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:86) [LinkLayer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:213) | “`ready=1` in the module” must not be stated as “external CHI can always transfer” |
| Two-beat line refill | CHIDAT, RXDAT, MSHRBuffer, MSHR | The current configuration has 64-B lines and 32-B beats, so `beatSize=2`; RXDAT derives a beat mask from `dataID==00/10`. [L2Param.scala:69](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:69) [CoupledL2.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:50) [RXDAT.scala:37](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:37) | A CHI comment's 16-B `DataID` allocation granularity must not be equated mechanically with this implementation's 32-B storage beat |
| Coherence concurrency control | MSHR state and `RXSNP` | Once the first beat arrives, `w_grantfirst` participates in same-address snoop blocking. [MSHR.scala:1167](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1167) [RXSNP.scala:57](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:57) | RXDAT has no address and cannot independently detect same-line conflicts |

<!--
## 3. Design Doc 追溯矩阵与版本差异

下表仅概括本地 Design Doc 的意图，再给出本 checkout 的可执行实现依据；没有复用原文段落。

| ID | 设计意图来源 | 在代码中找到的事实 | 结论 |
|---|---|---|---|
| D1 | RXDAT 接收有数据响应，以 `txnID` 对应 MSHR，并向回填存储和 MSHR 控制发出信息。[【Design Doc RXDAT.md:4】](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/downstream/RXDAT.md:4) | RXDAT 把 `txnID` 同时写到 `refillBufWrite.bits.id` 和 `io.in.mshrId`；Slice 分别接到 RefillBuffer 与 MSHRCtl。[【RXDAT.scala:58】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58) [【Slice.scala:136】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:136) | 已确认 |
| D2 | Issue B 处理 `CompData`，Issue C 还处理 `DataSepResp`。[【Design Doc RXDAT.md:4】](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/downstream/RXDAT.md:4) | RXDAT 不以 opcode 过滤，而是原样转交；MSHR 才按 `CompData` 和 `DataSepResp` 更新状态。[【RXDAT.scala:74】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:74) [【MSHR.scala:1151】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1151) | 部分确认；“RXDAT 只接收 CompData”不是源代码事实 |
| D3 | 首拍锁存、次拍与首拍组合并写 RefillBuffer。[【Design Doc RXDAT.md:10】](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/downstream/RXDAT.md:10) | RXDAT 没有首拍寄存器；它对每个有效 flit 产生写请求，MSHRBuffer 根据 `beatMask` 分别更新 Reg entry 中的 beat。[【RXDAT.scala:58】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58) [【MSHRBuffer.scala:47】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:47) | 存储效果可对应，但实现位置不同；不应说首拍被 RXDAT 自身锁存 |
| D4 | 1 MB、4 Slice、64 B line、32 B data、每 line 两拍及每 Slice 16 MSHR。[【CoupledL2.md:82】](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/CoupledL2.md:82) | 配置计算 sets 并写入 4 bank；`L2Param` 默认 `mshrs=16`，而 `beatSize=blockBytes/beatBytes`。[【Configs.scala:295】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:295) [【L2Param.scala:74】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:74) | 对当前配置确认；不是 RXDAT 的通用参数化保证 |
| D5 | `NDERR` 变 `denied`，`DERR/NDERR/dataCheck/poison` 变 `corrupt`。[【Error.md:114】](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/Error.md:114) | RXDAT 的布尔表达式一致；DataCheck 另有有效输入即断言。[【RXDAT.scala:55】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:55) [【RXDAT.scala:83】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:83) | 已确认，且代码比文档多出致命检查语义 |
-->

## 3. Design-Doc Traceability Matrix and Version Differences

The table summarizes local Design Doc intent and then gives the executable implementation evidence in this checkout; no original document paragraphs are reused.

| ID | Design-intent source | Fact found in code | Conclusion |
|---|---|---|---|
| D1 | RXDAT receives data-bearing responses, associates MSHR through `txnID`, and sends information to refill storage and MSHR control. [Design Doc RXDAT.md:4](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/downstream/RXDAT.md:4) | RXDAT writes `txnID` to both `refillBufWrite.bits.id` and `io.in.mshrId`; Slice connects these to RefillBuffer and MSHRCtl respectively. [RXDAT.scala:58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58) [Slice.scala:136](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:136) | Confirmed |
| D2 | Issue B handles `CompData`; Issue C also handles `DataSepResp`. [Design Doc RXDAT.md:4](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/downstream/RXDAT.md:4) | RXDAT does not filter by opcode and forwards it unchanged; MSHR updates state according to `CompData` and `DataSepResp`. [RXDAT.scala:74](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:74) [MSHR.scala:1151](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1151) | Partially confirmed; “RXDAT receives only CompData” is not a source-code fact |
| D3 | Latch the first beat, combine it with the second, and write RefillBuffer. [Design Doc RXDAT.md:10](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/downstream/RXDAT.md:10) | RXDAT has no first-beat register. It emits a write request for every valid flit, and MSHRBuffer updates individual beats in its Reg entry according to `beatMask`. [RXDAT.scala:58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58) [MSHRBuffer.scala:47](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:47) | The stored result can correspond, but the implementation location differs; the first beat must not be described as latched by RXDAT itself |
| D4 | 1 MB, 4 Slices, 64-B lines, 32-B data, two beats per line, and 16 MSHRs per Slice. [CoupledL2.md:82](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/CoupledL2.md:82) | Configuration computes sets and instantiates four banks; `L2Param` defaults `mshrs=16`, while `beatSize=blockBytes/beatBytes`. [Configs.scala:295](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:295) [L2Param.scala:74](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:74) | Confirmed for the current configuration, not a general RXDAT parameterization guarantee |
| D5 | `NDERR` becomes `denied`; `DERR/NDERR/dataCheck/poison` become `corrupt`. [Error.md:114](/home/yanyusong/XiangShan-Design-Doc/docs/zh/cache/l2cache/Error.md:114) | RXDAT's Boolean expressions match; DataCheck also has a valid-input assertion. [RXDAT.scala:55](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:55) [RXDAT.scala:83](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:83) | Confirmed; the code adds fatal-check semantics beyond the document |

<!--
## 4. 模块边界：Who / Why / How / From / To

| Who / From | Why | How | To | 结果 |
|---|---|---|---|---|
| 外部 CHI 对端，经 `PortIO.rx.dat` | 返回 cacheable read 的数据 flit | 物理 `flitv/flit/lcrdv` 经 LinkMonitor 转为 `DecoupledIO[CHIDAT]`。[【LinkLayer.scala:28】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:28) [【LinkLayer.scala:95】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:95) | `TL2CHICoupledL2` 顶层 RXDAT wire | 开始本 L2 的回填响应路径 |
| 顶层 `TL2CHICoupledL2` | 在共享 CHI 端口中找到归属 Slice | `txnID` 高位判 MMIO，中间位取 Slice ID，随后恢复内部 TXNID。[【TL2CHICoupledL2.scala:112】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:112) [【TL2CHICoupledL2.scala:251】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:251) | 一个 cacheable Slice 或 MMIOBridge | cacheable flit 才到目标 RXDAT |
| `Slice.io.out.rx.dat` | 将该 Slice 的 CHI data sink 接到具体处理器 | `rxdat.io.out <> io.out.rx.dat`，标准 Decoupled 连接。[【Slice.scala:212】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:212) | `RXDAT.io.out` | 目标模块取得 `valid/bits`，并输出 `ready` |
| `RXDAT` | 一次 flit 要同时保存数据并推进事务状态 | `refillBufWrite` 是 Valid 写请求；`io.in` 是 Valid 风格的 MSHR response，二者都没有 consumer `ready`。[【RXDAT.scala:27】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:27) | `MSHRBuffer.w[0]` 与 `MSHRCtl.resps.rxdat` | RefillBuffer 的相应 beat 更新；匹配 MSHR 采样 metadata/status |
| `MSHRCtl` | 让回复只影响当前仍有效的目标 MSHR | 对每个 i 用 `status.valid && mshrId==i` 产生局部 `rxdat.valid`。[【MSHRCtl.scala:131】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:131) | `MSHR(i)` | 只有当前有效且 ID 相等的 MSHR 状态会推进 |
| `MSHR` / MainPipe | 处理 coherence completion，最终更新目录/数据存储并生成上游响应 | MSHR 形成 `mp_grant`；当 `denied` 时禁止 meta/tag/data storage write。[【MSHR.scala:730】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:730) [【MSHR.scala:802】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:802) | MainPipe、DataStorage、Directory、GrantBuffer | RXDAT 不直接写 DataStorage，也不直接向 TileLink D 通道响应 |
-->

## 4. Module Boundary: Who / Why / How / From / To

| Who / From | Why | How | To | Result |
|---|---|---|---|---|
| External CHI peer through `PortIO.rx.dat` | Return data flits for cacheable reads | Physical `flitv/flit/lcrdv` is converted by LinkMonitor into `DecoupledIO[CHIDAT]`. [LinkLayer.scala:28](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:28) [LinkLayer.scala:95](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:95) | Top-level `TL2CHICoupledL2` RXDAT wire | Starts this L2's refill-response path |
| Top-level `TL2CHICoupledL2` | Locate the owning Slice on a shared CHI port | High `txnID` bit selects MMIO; middle bits select Slice ID; the inner TXNID is then restored. [TL2CHICoupledL2.scala:112](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:112) [TL2CHICoupledL2.scala:251](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:251) | One cacheable Slice or MMIOBridge | Only a cacheable flit reaches the target RXDAT |
| `Slice.io.out.rx.dat` | Connect this Slice's CHI data sink to a concrete handler | `rxdat.io.out <> io.out.rx.dat`, a standard Decoupled connection. [Slice.scala:212](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:212) | `RXDAT.io.out` | Target module receives `valid/bits` and drives `ready` |
| `RXDAT` | One flit must both save data and advance transaction state | `refillBufWrite` is a Valid write request; `io.in` is a Valid-style MSHR response. Neither has consumer `ready`. [RXDAT.scala:27](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:27) | `MSHRBuffer.w[0]` and `MSHRCtl.resps.rxdat` | The relevant RefillBuffer beat updates; the matched MSHR samples metadata/state |
| `MSHRCtl` | Allow a return response to affect only the currently valid target MSHR | For each `i`, `status.valid && mshrId==i` produces local `rxdat.valid`. [MSHRCtl.scala:131](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:131) | `MSHR(i)` | Only an MSHR that is currently valid and ID-equal advances state |
| `MSHR` / MainPipe | Complete coherence processing, ultimately update directory/data storage, and generate an upstream response | MSHR forms `mp_grant`; when `denied`, meta/tag/data-storage writes are suppressed. [MSHR.scala:730](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:730) [MSHR.scala:802](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:802) | MainPipe, DataStorage, Directory, GrantBuffer | RXDAT neither writes DataStorage directly nor responds directly on TileLink D |

<!--
## 5. 当前配置、位宽与索引

### 5.1 配置推导

`KunminghuV2Config` 的容量公式是 `nKB * 1024 / banks / ways / 64`。在 `1MB`、4 bank、默认 8 ways 下，每个 Slice 的 sets 为 `1024 * 1024 / 4 / 8 / 64 = 512`，总容量为 1 MiB。[【Configs.scala:278】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:278) [【Configs.scala:481】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481)

| 参数 | 当前口径 | 代码来源及对 RXDAT 的意义 |
|---|---:|---|
| L2 bank / Slice | 4 | 配置 `banks = 4`；顶层用 TXNID 的 Slice 字段路由。[【Configs.scala:482】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:482) |
| ways / sets per Slice | 8 / 512 | `L2CacheConfig` 默认 8 ways 和上面的容量公式；RXDAT 本身不读 set/way。[【Configs.scala:281】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:281) |
| cache line | 64 B | `L2Param.blockBytes` 默认值。[【L2Param.scala:69】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:69) |
| L2 CHI/TL beat | 32 B = 256 bit | `channelBytes = 32`，TL manager `beatBytes = 32`，CHI default `DATA_WIDTH = 256`。[【L2Param.scala:71】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:71) [【TL2CHICoupledL2.scala:57】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:57) [【Message.scala:251】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:251) |
| `beatSize` | 2 | `blockBytes / beatBytes = 64 / 32`。[【CoupledL2.scala:50】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:50) |
| MSHR entries per Slice | 16 | `L2Param.mshrs` 默认 16，配置未覆盖此字段。[【L2Param.scala:74】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:74) |
| channel A 可分配 MSHR | 至多 15 | `a_mshrFull` 在剩 1 项时置位，代码明确保留一项给 SinkB。[【MSHRCtl.scala:110】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:110) [【MSHRCtl.scala:162】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:162) |
| `mshrBits` | 8 | `idsAll = 256`，MSHR ID 使用 8 位空间；只有低编号有效 MSHR 由 MSHRCtl 例化。[【CoupledL2.scala:127】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:127) |
| CHI `dataID` | 2 bit | 当前 CHI default；协议注释称其分配粒度为 16 B。[【Message.scala:250】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:250) [【Message.scala:359】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:359) |
| DataCheck / poison | odd parity / enabled | `L2CacheConfig` 显式设 `dataCheck = Some("oddparity")` 与 `enablePoison = true`；因此当前配置会带这些字段并运行 RXDAT 的检查逻辑。[【Configs.scala:315】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:315) [【Message.scala:299】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:299) |

### 5.2 TXNID 是 RXDAT 的唯一事务索引

RXDAT 不接收地址，`io.in.set` 和 `io.in.tag` 都被置零。因此它既不做 set/tag 计算，也不做虚实地址转换；它依靠顶层已恢复的 `txnID` 索引 MSHRBuffer，并由 MSHRCtl 的 equality compare 选择 MSHR。[【RXDAT.scala:59】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:59) [【RXDAT.scala:65】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:65) [【MSHRBuffer.scala:51】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:51)

```text
外部 cacheable TXNID = 0 | SliceID | inner TXNID
外部 MMIO      TXNID = 1 |          inner TXNID

RXDAT 到达顶层后：
  txnID.head == 1 -> MMIOBridge
  txnID.head == 0 -> getSliceID -> 对应 Slice
                        restoreTXNID -> RXDAT / MSHRBuffer / MSHRCtl
```

这说明同一 `inner TXNID` 可以在不同 Slice 上复用，前提是外层 `SliceID` 路由正确；进入 Slice 后该外层字段被清除。[【TL2CHICoupledL2.scala:112】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:112) [【TL2CHICoupledL2.scala:254】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:254)
-->

## 5. Current Configuration, Widths, and Indices

### 5.1 Configuration derivation

The capacity formula in `KunminghuV2Config` is `nKB * 1024 / banks / ways / 64`. At `1MB`, four banks, and the default eight ways, each Slice has `1024 * 1024 / 4 / 8 / 64 = 512` sets, for a total capacity of 1 MiB. [Configs.scala:278](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:278) [Configs.scala:481](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:481)

| Parameter | Current basis | Source and significance for RXDAT |
|---|---:|---|
| L2 bank / Slice | 4 | Configuration sets `banks = 4`; the top level routes on the Slice field in TXNID. [Configs.scala:482](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:482) |
| Ways / sets per Slice | 8 / 512 | `L2CacheConfig` defaults to eight ways and uses the capacity formula above; RXDAT itself does not read set/way. [Configs.scala:281](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:281) |
| Cache line | 64 B | Default `L2Param.blockBytes`. [L2Param.scala:69](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:69) |
| L2 CHI/TL beat | 32 B = 256 bits | `channelBytes = 32`, TL manager `beatBytes = 32`, and default CHI `DATA_WIDTH = 256`. [L2Param.scala:71](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:71) [TL2CHICoupledL2.scala:57](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:57) [Message.scala:251](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:251) |
| `beatSize` | 2 | `blockBytes / beatBytes = 64 / 32`. [CoupledL2.scala:50](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:50) |
| MSHR entries per Slice | 16 | `L2Param.mshrs` defaults to 16 and this configuration does not override it. [L2Param.scala:74](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/L2Param.scala:74) |
| Allocatable MSHRs on Channel A | At most 15 | `a_mshrFull` asserts when one item remains; the code explicitly reserves one entry for SinkB. [MSHRCtl.scala:110](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:110) [MSHRCtl.scala:162](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:162) |
| `mshrBits` | 8 | `idsAll = 256`, so MSHR ID has an eight-bit space; only low-numbered valid MSHRs are instantiated by MSHRCtl. [CoupledL2.scala:127](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/CoupledL2.scala:127) |
| CHI `dataID` | 2 bits | Current CHI default; the protocol comment calls its allocation granularity 16 B. [Message.scala:250](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:250) [Message.scala:359](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:359) |
| DataCheck / poison | odd parity / enabled | `L2CacheConfig` explicitly sets `dataCheck = Some("oddparity")` and `enablePoison = true`; the current configuration therefore includes these fields and runs RXDAT checking. [Configs.scala:315](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:315) [Message.scala:299](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:299) |

### 5.2 TXNID is RXDAT's only transaction index

RXDAT receives no address, and both `io.in.set` and `io.in.tag` are set to zero. It consequently neither computes set/tag nor performs virtual-to-physical translation. It indexes MSHRBuffer with the top-level-restored `txnID`, and MSHRCtl selects the MSHR using an equality comparison. [RXDAT.scala:59](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:59) [RXDAT.scala:65](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:65) [MSHRBuffer.scala:51](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:51)

```text
External cacheable TXNID = 0 | SliceID | inner TXNID
External MMIO      TXNID = 1 |          inner TXNID

After RXDAT reaches the top level:
  txnID.head == 1 -> MMIOBridge
  txnID.head == 0 -> getSliceID -> corresponding Slice
                        restoreTXNID -> RXDAT / MSHRBuffer / MSHRCtl
```

The same `inner TXNID` can therefore be reused in different Slices, provided the outer `SliceID` routes correctly; that outer field is removed after entering the Slice. [TL2CHICoupledL2.scala:112](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:112) [TL2CHICoupledL2.scala:254](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:254)

<!--
## 6. 接口、握手与字段去向

### 6.1 RXDAT 的三个端口

| 端口 | 方向和协议 | 生产者 / 消费者 | 关键字段或行为 |
|---|---|---|---|
| `io.out` | `Flipped(DecoupledIO[CHIDAT])` | 生产者是本 Slice 上游路由；消费者是 RXDAT | `valid` 与 `bits` 输入，`ready` 输出且恒为真。严格的接收事件为 `io.out.fire`，在本模块中等价于 `io.out.valid`。[【RXDAT.scala:27】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:27) [【RXDAT.scala:86】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:86) |
| `io.refillBufWrite` | `ValidIO[MSHRBufWrite]` | RXDAT -> `MSHRBuffer.w[0]` | 无 `ready`；所有 `io.out.valid` 都请求写入，ID 为 TXNID，mask 由 `dataID` 决定。[【RXDAT.scala:58】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58) |
| `io.in` | `Output[RespBundle]`，其中含 valid | RXDAT -> `MSHRCtl.resps.rxdat` | 仅 first 或 last beat 报有效；没有返回 `ready`。[【RXDAT.scala:64】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:64) [【Slice.scala:136】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:136) |

`CHIDAT` 的主要字段包括 `txnID`、`homeNID`、`opcode`、`respErr`、`resp`、`dbID`、`dataID`、`traceTag`、`data`，以及按参数存在的 `dataCheck` 与 `poison`。[【Message.scala:509】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:509)

### 6.2 字段映射和可见性

| RXDAT 输入 | 输出去向 | 作用 |
|---|---|---|
| `txnID` | RefillBuffer `id`、`io.in.mshrId` | 作为缓存回填暂存和 MSHR response 的索引 |
| `data` | `Fill(beatSize, data)` 后送 RefillBuffer | 宽度适配为一个完整 `DSBlock`，真正被写的段由 `beatMask` 选定 |
| `dataID == 00` | `first = 1`，mask 选择低 beat | 报告给 MSHR，作为代码认可的首拍 |
| `dataID == 10` | `last = 1`，mask 选择高 beat | 报告给 MSHR，作为代码认可的末拍，并驱动 `respInfo.last` |
| `opcode`、`resp`、`dbID`、`homeNID`、`respErr`、`traceTag` | `RespInfoBundle` | MSHR 再解释 `CompData`/`DataSepResp`、CompAck 所需 ID、错误与 trace 信息 |
| `respErr`、dataCheck、poison | `denied/corrupt` | `NDERR -> denied`；`DERR/NDERR/dataCheck/poison -> corrupt` |
| 地址、set、tag | 无输入；`io.in.set/tag = 0` | RXDAT 不拥有地址或目录 lookup 责任 |

核心连接可直接读作：

```scala
io.refillBufWrite.valid := io.out.valid
io.refillBufWrite.bits.id := io.out.bits.txnID
io.refillBufWrite.bits.data.data := Fill(beatSize, io.out.bits.data)
io.refillBufWrite.bits.beatMask := Cat(last, first)

io.in.valid := (first || last) && io.out.valid
io.in.mshrId := io.out.bits.txnID
io.out.ready := true.B
```

源码：[RXDAT.scala:58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58)。注意 `refillBufWrite.valid` 以输入 valid 为条件，严格说没有显式以 `fire` 为条件；这是因为本模块把 ready 固定为真。

### 6.3 链路级握手先于 RXDAT

物理 CHI RXDAT 不是直接的 ready/valid：它用 `flitv/flit/lcrdv`。LinkMonitor 为 RXDAT 调用 `LCredit2Decoupled(..., 15, false)`；非 blocking 分支仅在 RX link 状态为 RUN、credit pool 非空且 Decoupled 下游 ready 时返还 L-Credit。[【LinkLayer.scala:397】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:397) [【LinkLayer.scala:213】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:213)

后续 `Pipeline(linkMonitor.io.in.rx.dat)` 默认是一个 `Queue(entries = 1, pipe = true, flow = false)`。它是一个单项的 Decoupled 缓冲，不是 RXDAT 本体的寄存器。[【TL2CHICoupledL2.scala:267】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:267) [【Pipeline.scala:22】](/home/yanyusong/xs-memory-env/XiangShan/utility/src/main/scala/utility/Pipeline.scala:22)
-->

## 6. Interfaces, Handshakes, and Field Destinations

### 6.1 RXDAT's three ports

| Port | Direction and protocol | Producer / consumer | Key fields or behavior |
|---|---|---|---|
| `io.out` | `Flipped(DecoupledIO[CHIDAT])` | Producer is routing upstream of this Slice; consumer is RXDAT | `valid` and `bits` are inputs; `ready` is an output tied high. The strict receive event is `io.out.fire`, which equals `io.out.valid` in this module. [RXDAT.scala:27](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:27) [RXDAT.scala:86](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:86) |
| `io.refillBufWrite` | `ValidIO[MSHRBufWrite]` | RXDAT -> `MSHRBuffer.w[0]` | No `ready`; every `io.out.valid` requests a write, indexed by TXNID with mask selected by `dataID`. [RXDAT.scala:58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58) |
| `io.in` | `Output[RespBundle]`, containing valid | RXDAT -> `MSHRCtl.resps.rxdat` | Valid is reported only for the first or last beat; there is no return `ready`. [RXDAT.scala:64](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:64) [Slice.scala:136](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:136) |

The main `CHIDAT` fields are `txnID`, `homeNID`, `opcode`, `respErr`, `resp`, `dbID`, `dataID`, `traceTag`, and `data`, plus parameter-dependent `dataCheck` and `poison`. [Message.scala:509](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Message.scala:509)

### 6.2 Field mapping and visibility

| RXDAT input | Output destination | Function |
|---|---|---|
| `txnID` | RefillBuffer `id`, `io.in.mshrId` | Index for temporary cache-refill storage and the MSHR response |
| `data` | RefillBuffer after `Fill(beatSize, data)` | Width-adapted into a complete `DSBlock`; `beatMask` selects the fragment actually written |
| `dataID == 00` | `first = 1`, mask selects low beat | Reported to MSHR as the first beat recognized by this implementation |
| `dataID == 10` | `last = 1`, mask selects high beat | Reported to MSHR as the recognized final beat and drives `respInfo.last` |
| `opcode`, `resp`, `dbID`, `homeNID`, `respErr`, `traceTag` | `RespInfoBundle` | MSHR later interprets `CompData`/`DataSepResp`, IDs needed for CompAck, and error/trace information |
| `respErr`, dataCheck, poison | `denied/corrupt` | `NDERR -> denied`; `DERR/NDERR/dataCheck/poison -> corrupt` |
| Address, set, tag | No input; `io.in.set/tag = 0` | RXDAT owns neither addresses nor directory lookup |

The core wiring reads directly as follows:

```scala
io.refillBufWrite.valid := io.out.valid
io.refillBufWrite.bits.id := io.out.bits.txnID
io.refillBufWrite.bits.data.data := Fill(beatSize, io.out.bits.data)
io.refillBufWrite.bits.beatMask := Cat(last, first)

io.in.valid := (first || last) && io.out.valid
io.in.mshrId := io.out.bits.txnID
io.out.ready := true.B
```

Source: [RXDAT.scala:58](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:58). Note that `refillBufWrite.valid` is conditioned on input valid rather than explicitly on `fire`; this is equivalent here because the module fixes ready high.

### 6.3 Link-level handshake precedes RXDAT

Physical CHI RXDAT is not direct ready/valid; it uses `flitv/flit/lcrdv`. LinkMonitor invokes `LCredit2Decoupled(..., 15, false)` for RXDAT. In the non-blocking branch, L-Credit returns only when the RX link is RUN, the credit pool is nonempty, and the Decoupled downstream is ready. [LinkLayer.scala:397](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:397) [LinkLayer.scala:213](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:213)

The subsequent `Pipeline(linkMonitor.io.in.rx.dat)` defaults to `Queue(entries = 1, pipe = true, flow = false)`. It is a one-entry Decoupled buffer, not a register in the RXDAT body. [TL2CHICoupledL2.scala:267](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:267) [Pipeline.scala:22](/home/yanyusong/XiangShan/utility/src/main/scala/utility/Pipeline.scala:22)

<!--
## 7. 有效动态路径

### 7.1 从物理 flit 到 MSHR 状态的阶段表

| 阶段 | valid / ready / fire 语义 | 数据和状态行为 | 阻塞或假设 |
|---|---|---|---|
| P0: CHI RX link | `accept = lcreditInflight != 0 && flitv` | LinkMonitor 从物理 flit 解包成 `CHIDAT`；L-Credit 计数更新。[【LinkLayer.scala:152】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:152) | 非 RUN、无 in-flight credit 或下游不 ready 时不能按这条路径接受 |
| P1: `rxdatPipe` | Queue 的 enq/deq Decoupled | 提供一个 `flow=false` 的 1-entry 弹性边界 | 队列满时可把 pressure 传回 LinkMonitor；不把它称为固定一拍延迟 |
| P2: 顶层路由 | `rxdat.valid` 按 `txnID` 一热送一个 Slice 或 MMIO | cacheable 路径清除外层 Slice bits，MMIO 走桥 | TXNID route 必须匹配原事务；错误 route 不会由 RXDAT 检出 |
| P3: RXDAT | `out.fire = out.valid && true` | 同拍组合产生 RefillBuffer Valid 写和 MSHR RespBundle | 目标 RXDAT 无 FIFO/FSM；`refillBufWrite` 和 `io.in` 无 ready 可反压 |
| P4: MSHRBuffer / MSHRCtl | RefillBuffer 在时钟边沿写 Reg；MSHRCtl 组合按 ID gate response | 匹配 MSHR 的状态寄存器在该边沿更新 | 若 MSHR 无效，MSHRCtl 不把 response 发给其状态机；但 RXDAT 的 RefillBuffer 写请求仍已产生 |
| P5: MSHR -> MainPipe | MSHR 等待 grant/replacement/probe 条件后发 `mainpipe` task | MainPipe 读取 RefillBuffer，再写 Directory/DataStorage 或产生上游 grant | 这已越出 RXDAT 的职责，受其它 pipeline 和 coherence 状态约束 |

### 7.2 正常两拍 `CompData` 场景

1. P0/P1 使第一个已路由 `CHIDAT` 到达 RXDAT，且 `dataID == 00`。
2. RXDAT 同拍把该 data 扩为 `DSBlock`、置 RefillBuffer 写 valid、`beatMask` 选首 beat，并对 MSHR 输出 `valid=1, last=0`。
3. MSHRCtl 只把该 response 给匹配的有效 entry。MSHR 遇到第一个 `CompData` 后设置 `w_grantfirst`、`w_grant` 与 `gotGrantData`；`w_grantlast` 取此前周期的 `w_grantfirst`，所以首个有效 CompData 不会完成两拍条件。[【MSHR.scala:1165】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1165)
4. 合法协议下第二个 flit 是 `dataID == 10`，它写同一 `txnID` 的另一 beat，并输出 `last=1`。
5. **MSHR 并不读取 `respInfo.last`。** 它看到第二次有效的 `CompData` 时就使 `w_grantlast` 成立；因此上述 `00 -> 10` 次序是输入协议约定，而不是 MSHR 在本处主动检查的顺序。随后 MSHR 还须满足 probe/replacement 等等待条件才可产生 MainPipe grant/refill task。[【MSHR.scala:285】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:285) [【MSHR.scala:1165】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1165)

这段路径中“RXDAT 接收”与“line 对上游可见/写入主 DataStorage”不是同一个时刻。后者要通过 MainPipe 的 `toDS` 请求和 MSHR task，不能依据 RXDAT valid 断言为已完成。[【Slice.scala:121】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) [【MainPipe.scala:499】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:499)

### 7.3 `DataSepResp` 与 opcode 的责任划分

`CompData` 是 0x4，CHI C 以后还有 `DataSepResp`。[【Opcode.scala:249】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Opcode.scala:249) [【Opcode.scala:254】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Opcode.scala:254)

RXDAT 的注释叫作 “for Transactions: CompData”，但它实际不检查 `opcode`：只要 `dataID` 为 00/10，就会写 RefillBuffer 并对 MSHR 报 response。真正的 opcode 选择在 MSHR：Issue C 分支处理 `DataSepResp`，普通分支处理 `CompData`，两者都 `require(beatSize == 2)`。两个 MSHR 分支也不以 `RespInfo.last` 判断 completed beat；`last` 目前用于 RXDAT 的性能事件和其他消费者，而完成顺序依赖 protocol。[【RXDAT.scala:33】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:33) [【MSHR.scala:1150】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1150) [【MSHRCtl.scala:262】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:262)

这不是可以忽略的表述差异：验证必须在 RXDAT 边界注入意外 opcode，检查 RefillBuffer 不会被不应处理的 DAT 污染，或由上游协议检查器保证它永不发生。仅从当前 RXDAT 源码看，没有局部 assertion 覆盖该条件。
-->

## 7. Effective Dynamic Path

### 7.1 Stage table from physical flit to MSHR state

| Stage | `valid` / `ready` / `fire` meaning | Data and state behavior | Blocking or assumption |
|---|---|---|---|
| P0: CHI RX link | `accept = lcreditInflight != 0 && flitv` | LinkMonitor unpacks a physical flit into `CHIDAT`; L-Credit count changes. [LinkLayer.scala:152](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:152) | This path cannot accept while not RUN, with no inflight credit, or when downstream is not ready |
| P1: `rxdatPipe` | Queue enqueue/dequeue Decoupled | Provides a one-entry `flow=false` elastic boundary | A full queue can propagate pressure to LinkMonitor; it must not be called a fixed one-cycle latency |
| P2: Top-level routing | `rxdat.valid` is one-hot routed by `txnID` to one Slice or MMIO | Cacheable path strips outer Slice bits; MMIO uses the bridge | TXNID route must match the original transaction; RXDAT does not detect a bad route |
| P3: RXDAT | `out.fire = out.valid && true` | Generates the RefillBuffer Valid write and MSHR RespBundle combinationally in the same cycle | Target RXDAT has no FIFO/FSM; `refillBufWrite` and `io.in` have no ready to backpressure |
| P4: MSHRBuffer / MSHRCtl | RefillBuffer writes Reg at the clock edge; MSHRCtl gates response combinationally by ID | Registers in the matching MSHR update at that edge | If the MSHR is invalid, MSHRCtl does not send the response to its state machine, but RXDAT's RefillBuffer write request has already been produced |
| P5: MSHR -> MainPipe | MSHR sends a `mainpipe` task after grant/replacement/probe conditions are satisfied | MainPipe reads RefillBuffer, then writes Directory/DataStorage or creates an upstream grant | This is beyond RXDAT responsibility and is constrained by other pipeline/coherence state |

### 7.2 Normal two-beat `CompData` scenario

1. P0/P1 deliver the first routed `CHIDAT` to RXDAT, with `dataID == 00`.
2. In the same cycle RXDAT expands the data to `DSBlock`, asserts RefillBuffer write valid, selects the first beat with `beatMask`, and emits MSHR `valid=1, last=0`.
3. MSHRCtl exposes the response only to a matching valid entry. On the first `CompData`, MSHR sets `w_grantfirst`, `w_grant`, and `gotGrantData`; `w_grantlast` uses the previous cycle's `w_grantfirst`, so the first valid CompData cannot satisfy the two-beat condition. [MSHR.scala:1165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1165)
4. Under the legal protocol, the second flit has `dataID == 10`, writes the other beat of the same `txnID`, and emits `last=1`.
5. **MSHR does not read `respInfo.last`.** It sets `w_grantlast` upon the second valid `CompData`; the `00 -> 10` order is thus an input-protocol convention rather than a sequence actively checked here by MSHR. MSHR must still meet probe/replacement wait conditions before it can create a MainPipe grant/refill task. [MSHR.scala:285](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:285) [MSHR.scala:1165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1165)

“RXDAT accepted” and “the line is visible upstream/written into main DataStorage” are not the same instant. The latter must pass through MainPipe's `toDS` request and MSHR task and cannot be asserted complete merely from RXDAT valid. [Slice.scala:121](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:121) [MainPipe.scala:499](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MainPipe.scala:499)

### 7.3 `DataSepResp` and allocation of opcode responsibility

`CompData` is 0x4, and CHI Issue C additionally has `DataSepResp`. [Opcode.scala:249](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Opcode.scala:249) [Opcode.scala:254](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/Opcode.scala:254)

RXDAT's comment calls it “for Transactions: CompData,” but it does not actually check `opcode`: whenever `dataID` is 00/10, it writes RefillBuffer and reports a response to MSHR. Opcode selection is truly in MSHR: the Issue C branch handles `DataSepResp`, the normal branch handles `CompData`, and both `require(beatSize == 2)`. Neither MSHR branch uses `RespInfo.last` to determine a completed beat; `last` is currently used by RXDAT performance events and other consumers, while completion order depends on protocol. [RXDAT.scala:33](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:33) [MSHR.scala:1150](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1150) [MSHRCtl.scala:262](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:262)

This is not a cosmetic distinction. Verification must inject an unexpected opcode at the RXDAT boundary and check that RefillBuffer is not polluted by DAT that should not be processed, or an upstream protocol checker must guarantee that it never occurs. From current RXDAT source alone, no local assertion covers this condition.

<!--
## 8. 存储、更新、释放与替换

### 8.1 存储所有权表

| 存储 / 状态 | 写者 | 读者 | RXDAT 的角色 | 释放 / 完整性条件 |
|---|---|---|---|---|
| `MSHRBuffer` RefillBuffer | RXDAT port 0、SinkC port 1 | RequestArb/MainPipe 经 `refillBuf.io.r` | 根据 `txnID` 与 beat mask 请求更新 | 无 valid bit/full bit；entry 生命期由 MSHR 管理。[【Slice.scala:165】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:165) |
| MSHR request/state | MainPipe allocation、MSHR 响应路径 | MSHRCtl、RXSNP、MainPipe | 按 ID 扇出 CHI response | `will_free` 同时要求所有 schedule/wait 条件完成。[【MSHR.scala:1303】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1303) |
| Directory / DataStorage | MainPipe | cache lookup/MainPipe | 无直接端口 | RXDAT 数据须先经 RefillBuffer 和 MSHR task 才能被写入。[【Slice.scala:84】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:84) [【Slice.scala:89】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:89) |
| Link credit pool / inflight | `LCredit2Decoupled` | LinkMonitor | 无直接端口 | 控制物理 RXDAT flit 的可接受数量。[【LinkLayer.scala:152】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:152) |

### 8.2 RefillBuffer 的真实写法

`MSHRBuffer` 是 `Reg(Vec(...))`。对于每个 entry，它收集所有写端口中 `valid && id==i` 的条件，选择一个 `w_data/w_beatSel`，再按 mask 的每一位更新相应 beat。读端没有 ready，读数据经 `RegEnable` 注册。[【MSHRBuffer.scala:47】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:47) [【MSHRBuffer.scala:64】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:64)

```scala
val wens = VecInit(io.w.map(w => w.valid && w.bits.id === i.U)).asUInt
val w_data = PriorityMux(wens, io.w.map(_.bits.data))
val w_beatSel = PriorityMux(wens, io.w.map(_.bits.beatMask))
when (wens.orR) {
  block.zip(w_beatSel.asBools).foreach { case (beat, sel) =>
    when (sel) { beat := /* selected data fragment */ }
  }
}
```

源码：[MSHRBuffer.scala:51](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:51)。这直接给出两个结论：

- `RXDAT` 每拍把 256 bit 输入复制到两个 beat 槽，但 mask 只会选一个槽，所以一次合法 RXDAT flit 只更新一个 32 B beat。
- 若 RXDAT 与 SinkC 在同一周期写同一 MSHR entry，代码用一个 `PriorityMux` 选择一套 data/mask，并不在这个表达式中合并两套 payload。Slice 的写端口序列是 `Seq(rxdat.io.refillBufWrite, sinkC.io.refillBufWrite)`，而本 checkout 固定使用 Chisel 6.7.0；该 `PriorityMux` 对该序列取第一个命中项，因此 RXDAT port 0 优先、SinkC port 1 的整笔写被丢弃。`PopCount(wens) <= 2` 只禁止三写，并不禁止两写。该同 ID 竞争仍必须通过事务调度避免，并用生成 RTL/随机测试证明它在合法事务中不可达。[【Slice.scala:167】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:167) [【build.sc:42】](/home/yanyusong/xs-memory-env/XiangShan/build.sc:42)
- SinkC 的第二写端口不是死代码：它在与有效 MSHR 的 set/tag 匹配且 `blockRefill` 时，将 `ReleaseData` 延后一拍写入同一个 refill buffer entry，并使用全 beat mask。当前连接处没有 RXDAT/SinkC 的显式互斥或 ready 回压，因此“不会同拍冲突”不能由本段静态代码证明。[【SinkC.scala:169】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:169) [【SinkC.scala:181】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:181)

### 8.3 查找、更新、释放与替换顺序

1. **查找**：RXDAT 不做地址查找；它仅用 `txnID` 定位 RefillBuffer entry。地址相关 set/tag 在分配时已经属于 MSHR request。
2. **更新**：每个 RXDAT valid 试图更新一个 buffer beat；只有 first/last 还推进 MSHR response。
3. **完成检测**：MSHR 将 `w_grantfirst/w_grantlast/w_grant` 等状态与 probe/release acknowledgement 一起作为继续条件；不能以“buffer 写过两个 beat”替代 completion。[【MSHR.scala:1311】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1311)
4. **替换/回填写入**：`mp_grant` 只有在允许时向 MainPipe 发起工作；若 `denied`，`metaWen`、`tagWen`、`dsWen` 都被抑制。[【MSHR.scala:802】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:802)
5. **释放**：`will_free` 时才清 `req_valid`。若一个过期/错误 route 的 RXDAT 在该时刻后到达，MSHRCtl 不再把它交给无效 MSHR；但 RefillBuffer 的无握手写仍是协议必须避免的输入。[【MSHR.scala:1313】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1313)

### 8.4 valid 状态的设定、保持与释放

| valid / 状态 | 何时置位 | 何时保持或清除 | 下游观察者 | 为什么存在 |
|---|---|---|---|---|
| `RXDAT.io.out.valid` | 上游 Decoupled producer 提供 CHIDAT | 由上游按 Decoupled 规则保持到 fire；本模块 `ready=1`，故同拍消费 | RefillBuffer write 和 `io.in` 组合逻辑 | 表示一个物理 response 已通过 link/pipeline/routing 到达该 Slice |
| `refillBufWrite.valid` | 每个 `io.out.valid` | 纯组合，无本地 hold/clear | `MSHRBuffer.w[0]` | 保证每一个输入 flit 都有对应的存储尝试；也暴露非法 TXNID/ID 的 buffer 污染风险 |
| `io.in.valid` | `io.out.valid && (dataID==00 || dataID==10)` | 纯组合；其它 DataID 不推进 MSHR | MSHRCtl 的 per-entry ID gate | 仅把当前实现认可的两个 data beat 送入 MSHR 控制状态 |
| `MSHR.req_valid` / `status.valid` | `io.alloc.valid` 时置 1，并重置 grant/beat 等状态 | `will_free && req_valid` 时清 0；其余周期保持 | MSHRCtl、RXSNP、RequestArb/MainPipe | 使 RXDAT response 只改变仍在途的事务，提供资源占用和 snoop 冲突判断。[【MSHR.scala:132】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:132) [【MSHR.scala:1321】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1321) |
| `MSHRBuffer` entry valid | 不存在独立 valid bit | 不存在本地清零/满空握手 | MainPipe 根据 MSHR task 请求读取 | 把容量和完成性所有权留给 MSHR，而不是将该 Reg array 误解为可自保护队列 |
-->

## 8. Storage, Updates, Release, and Replacement

### 8.1 Storage-ownership table

| Storage / state | Writer | Reader | RXDAT's role | Release / integrity condition |
|---|---|---|---|---|
| `MSHRBuffer` RefillBuffer | RXDAT port 0, SinkC port 1 | RequestArb/MainPipe through `refillBuf.io.r` | Requests an update by `txnID` and beat mask | No valid or full bit; entry lifetime is owned by MSHR. [Slice.scala:165](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:165) |
| MSHR request/state | MainPipe allocation, MSHR response paths | MSHRCtl, RXSNP, MainPipe | Fans out a CHI response by ID | `will_free` requires all schedule/wait conditions to complete. [MSHR.scala:1303](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1303) |
| Directory / DataStorage | MainPipe | Cache lookup/MainPipe | No direct port | RXDAT data must first pass RefillBuffer and an MSHR task. [Slice.scala:84](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:84) [Slice.scala:89](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:89) |
| Link credit pool / inflight | `LCredit2Decoupled` | LinkMonitor | No direct port | Controls how many physical RXDAT flits can be accepted. [LinkLayer.scala:152](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:152) |

### 8.2 Actual RefillBuffer write behavior

`MSHRBuffer` is a `Reg(Vec(...))`. For every entry, it collects `valid && id==i` conditions from all write ports, selects one `w_data/w_beatSel`, then updates each selected beat according to the mask. The read port has no ready and registers its data through `RegEnable`. [MSHRBuffer.scala:47](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:47) [MSHRBuffer.scala:64](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:64)

```scala
val wens = VecInit(io.w.map(w => w.valid && w.bits.id === i.U)).asUInt
val w_data = PriorityMux(wens, io.w.map(_.bits.data))
val w_beatSel = PriorityMux(wens, io.w.map(_.bits.beatMask))
when (wens.orR) {
  block.zip(w_beatSel.asBools).foreach { case (beat, sel) =>
    when (sel) { beat := /* selected data fragment */ }
  }
}
```

Source: [MSHRBuffer.scala:51](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/MSHRBuffer.scala:51). This directly yields two conclusions:

- In each cycle RXDAT replicates its 256-bit input into both beat slots, but the mask selects only one slot, so one legal RXDAT flit updates exactly one 32-B beat.
- If RXDAT and SinkC write the same MSHR entry in the same cycle, the code selects one data/mask pair with a `PriorityMux`; it does not merge the two payloads in that expression. Slice orders the ports as `Seq(rxdat.io.refillBufWrite, sinkC.io.refillBufWrite)`, and this checkout fixes Chisel at 6.7.0. `PriorityMux` selects the first matching item in that sequence, so RXDAT port 0 wins and the whole SinkC port-1 write is discarded. `PopCount(wens) <= 2` prohibits only three writes, not two. The same-ID race must therefore be avoided by transaction scheduling and shown unreachable for legal transactions by generated RTL/random testing. [Slice.scala:167](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/Slice.scala:167) [build.sc:42](/home/yanyusong/XiangShan/build.sc:42)
- SinkC's second write port is not dead code: when it matches an active MSHR's set/tag and `blockRefill`, it writes `ReleaseData` to the same refill-buffer entry one cycle later with a full beat mask. The current connections provide no explicit RXDAT/SinkC mutual exclusion or ready backpressure, so “they cannot conflict in one cycle” cannot be proven from this static code. [SinkC.scala:169](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:169) [SinkC.scala:181](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/SinkC.scala:181)

### 8.3 Lookup, update, release, and replacement order

1. **Lookup:** RXDAT does no address lookup; it uses only `txnID` to locate the RefillBuffer entry. Address-related set/tag already belongs to the MSHR request at allocation time.
2. **Update:** Every RXDAT valid attempts to update one buffer beat; only first/last also advances the MSHR response.
3. **Completion detection:** MSHR combines state such as `w_grantfirst/w_grantlast/w_grant` with probe/release acknowledgements as continuation conditions; “two beats were written to the buffer” is not a substitute for completion. [MSHR.scala:1311](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1311)
4. **Replacement/refill write:** `mp_grant` starts MainPipe work only when allowed. If `denied`, `metaWen`, `tagWen`, and `dsWen` are all suppressed. [MSHR.scala:802](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:802)
5. **Release:** `req_valid` clears only at `will_free`. If a stale or incorrectly routed RXDAT arrives after that point, MSHRCtl no longer delivers it to an invalid MSHR, but the handshake-free RefillBuffer write remains an input the protocol must prevent. [MSHR.scala:1313](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1313)

### 8.4 Setting, holding, and releasing valid state

| Valid / state | When set | When held or cleared | Downstream observer | Why it exists |
|---|---|---|---|---|
| `RXDAT.io.out.valid` | An upstream Decoupled producer supplies CHIDAT | Held by upstream under Decoupled rules until fire; since `ready=1` here, it is consumed in that cycle | RefillBuffer-write and `io.in` combinational logic | Indicates that a physical response has reached this Slice through link/pipeline/routing |
| `refillBufWrite.valid` | Every `io.out.valid` | Combinational; no local hold/clear | `MSHRBuffer.w[0]` | Ensures each input flit has a corresponding storage attempt; also exposes the risk of buffer pollution from illegal TXNID/ID |
| `io.in.valid` | `io.out.valid && (dataID==00 || dataID==10)` | Combinational; other DataIDs do not advance MSHR | Per-entry ID gate in MSHRCtl | Sends only the two data beats recognized by the current implementation into MSHR control state |
| `MSHR.req_valid` / `status.valid` | Set to 1 on `io.alloc.valid`, resetting grant/beat and related state | Cleared on `will_free && req_valid`; otherwise held | MSHRCtl, RXSNP, RequestArb/MainPipe | Makes RXDAT responses alter only in-flight transactions and provides resource occupancy/snoop-conflict information. [MSHR.scala:132](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:132) [MSHR.scala:1321](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1321) |
| `MSHRBuffer` entry valid | No independent valid bit | No local clear or full/empty handshake | MainPipe reads when requested by MSHR task | Leaves capacity and completion ownership to MSHR rather than treating this Reg array as a self-protecting queue |

<!--
## 9. 控制路径、错误路径与并发冲突

### 9.1 MSHR response 的状态影响

MSHRCtl 的分派条件同时要求目标 entry `status.valid` 和 ID 相等，因此“有 RXDAT valid”不等于“有 MSHR 接收”。[【MSHRCtl.scala:142】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:142)

对被接收的 `CompData`，MSHR 设置 `w_grantfirst`、`w_grant`、`gotGrantData`，保存 `dbID/homeNID` 以备 CompAck，并累积错误/traceTag。`DataSepResp` 在 Issue C 后同样推进 first/last 计数，但仍以实际 opcode 为门。[【MSHR.scala:1145】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1145)

该状态也会反馈到 snoop 冲突控制：RXSNP 对同 set/tag 的有效 MSHR，当 `w_grantfirst` 且 `blockRefill` 或 release ack 等条件存在时阻塞 snoop。这正是第一拍 RXDAT 会影响全局一致性时序的直接源码证据。[【RXSNP.scala:43】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:43) [【RXSNP.scala:57】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:57)

### 9.2 错误、assertion 与架构可见性

| 条件 | RXDAT 直接行为 | 后续可见行为 | 证据 |
|---|---|---|---|
| `respErr == NDERR` | `denied=1` 且 `corrupt=1` | MSHR 累积状态；最终 `mp_grant` 因 `denied` 禁止 cache storage 写入 | [【RXDAT.scala:83】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:83) [【MSHR.scala:804】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:804) |
| `respErr == DERR` | `corrupt=1`，`denied=0` | MSHR 累积 corrupt；下游 task 可携带该标记 | [【RXDAT.scala:84】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:84) |
| dataCheck error | `assert(!(dataCheck && valid))` 失败，同时 `corrupt=1` | 仿真/断言优先暴露；不能把它当作普通可恢复无声错误 | [【RXDAT.scala:41】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:41) |
| poison bit | `corrupt=1`，没有同类 assertion | 由 MSHR/后续上游响应路径传播 | [【RXDAT.scala:54】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:54) |
| `dataID` 不是 00/10 | 仍有 RefillBuffer write valid，但 mask 为 0，MSHR response valid 为 0 | 不更新任何 buffer beat，也不推进目标 MSHR | [【RXDAT.scala:37】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:37) |

最后一行是当前实现的协议假设，不是“安全丢弃”的设计保证。因为 RXDAT 的 `refillBufWrite.valid` 仍为真、只有 mask 为零，验证应将其列为非法输入检查点。

### 9.3 不存在的控制面

静态检索目标 RXDAT 与其 IO 表明：没有 CSR 输入、特权级输入、TLB/PMP 输入、flush/redirect 输入，也没有直接 Difftest emitter。其可观察 debug/performance 接口在近邻 MSHRCtl：`l2_cache_refill` 和 `l2_cache_rd_refill` 用 `resps.rxdat.valid && last` 计数。[【MSHRCtl.scala:262】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:262)

因此应区分两层：RXDAT 只转换下游 CHI response；是否形成架构异常、如何由上游 L1/BEU 处理，属于更外层响应和错误通路，不能从本模块的 `corrupt` 位单独推导。
-->

## 9. Control Path, Error Path, and Concurrent Conflicts

### 9.1 State effects of the MSHR response

MSHRCtl's dispatch condition requires both target-entry `status.valid` and an equal ID. Thus “RXDAT valid exists” does not mean “an MSHR receives it.” [MSHRCtl.scala:142](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:142)

For an accepted `CompData`, MSHR sets `w_grantfirst`, `w_grant`, and `gotGrantData`, saves `dbID/homeNID` for CompAck, and accumulates error/traceTag. After Issue C, `DataSepResp` likewise advances first/last counting but remains gated by its actual opcode. [MSHR.scala:1145](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1145)

This state feeds back into snoop-conflict control: for a valid MSHR with the same set/tag, RXSNP blocks a snoop when `w_grantfirst` and `blockRefill` or release-ack conditions are present. This is direct source evidence that the first RXDAT beat affects global coherence timing. [RXSNP.scala:43](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:43) [RXSNP.scala:57](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:57)

### 9.2 Errors, assertions, and architectural visibility

| Condition | RXDAT direct behavior | Subsequently visible behavior | Evidence |
|---|---|---|---|
| `respErr == NDERR` | `denied=1` and `corrupt=1` | MSHR accumulates state; final `mp_grant` suppresses cache-storage writes because of `denied` | [RXDAT.scala:83](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:83) [MSHR.scala:804](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:804) |
| `respErr == DERR` | `corrupt=1`, `denied=0` | MSHR accumulates corrupt; downstream tasks can carry that marker | [RXDAT.scala:84](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:84) |
| dataCheck error | `assert(!(dataCheck && valid))` fails and `corrupt=1` | Exposed first by simulation/assertion; it must not be treated as an ordinary recoverable silent error | [RXDAT.scala:41](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:41) |
| poison bit | `corrupt=1`, with no equivalent assertion | Propagated through MSHR/later upstream-response paths | [RXDAT.scala:54](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:54) |
| `dataID` is not 00/10 | RefillBuffer write valid remains high, but mask is zero and MSHR response valid is zero | No buffer beat updates and no target MSHR advances | [RXDAT.scala:37](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXDAT.scala:37) |

The final row is a protocol assumption in the current implementation, not a “safe discard” guarantee. Because `refillBufWrite.valid` remains high and only the mask is zero, verification should classify it as an illegal-input checkpoint.

### 9.3 Missing control planes

Static inspection of the target RXDAT and its I/O shows no CSR, privilege-level, TLB/PMP, flush/redirect, or direct Difftest-emitter input. Its observable debug/performance interface is in nearby MSHRCtl: `l2_cache_refill` and `l2_cache_rd_refill` count `resps.rxdat.valid && last`. [MSHRCtl.scala:262](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHRCtl.scala:262)

Two levels must therefore be separated: RXDAT only converts a downstream CHI response. Whether it becomes an architectural exception and how upstream L1/BEU handles it belongs to outer response and error paths and cannot be inferred from this module's `corrupt` bit alone.

<!--
## 10. 时序、吞吐与前向进展

### 10.1 能从源码确认的量

| 量 | 可确认结论 | 限制 |
|---|---|---|
| RXDAT 本体状态 | 无显式 `Reg`、Queue 或 FSM | 是组合适配，不代表端到端零延迟 |
| RXDAT 端口接收率 | 若本 Slice 已拿到 Decoupled valid，则 `ready=1`，理论上一拍可消费一个 CHIDAT | 受顶层路由、前级 `Pipeline`、链路 credit 和 CHI 对端实际供给限制 |
| line 完整回填率 | 两个受认可的 32 B beat 可组成一条 64 B line；连续两个 beat 的理想上限是每两拍一条 line | MSHR、probe、release、MainPipe/目录资源可能延后对 L1 可见的完成 |
| 物理入口容量 | RXDAT link 使用 15 个 L-Credit | 这不是 15-entry RXDAT FIFO；`blocking=false` 分支没有一个 15-entry Queue。[【LinkLayer.scala:181】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:181) [【LinkLayer.scala:211】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:211) |
| Decoupled 缓冲 | `Pipeline` 提供一个 `Queue(1, pipe=true, flow=false)` | 不把 Chisel Queue 的 ready 旁路细节简化为固定 N-cycle latency |

### 10.2 不能从静态源码声称的量

- 没有给出“外部 `flitv` 到 MainPipe/DataStorage 的固定周期数”。链路状态、credit、队列、MSHR 状态、RequestArb 和 MainPipe 均会参与。
- 没有给出“RXDAT 支持 15 个在途 cache line”的结论。15 是物理 link credit；每 Slice MSHR 数是 16，A channel 可用数是 15，二者语义不同。
- 没有凭 `io.out.ready := true` 断言 RefillBuffer 永不冲突。两个无 ready 下游使正确性依赖合法协议和周边调度。

### 10.3 关键前向进展条件

1. RX link 必须进入 RUN，且 `lcreditPool > 0`、下游 Decoupled ready 才发 L-Credit。[【LinkLayer.scala:148】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:148)
2. `txnID` 必须指向仍有效且对应本 Slice 的 cacheable MSHR。
3. 收到所需 data 后，MSHR 仍须等待 probe/replacement/release/ack 条件。`will_free` 的 `no_wait` 明确包含 `w_grantlast`、`w_grant`、`w_releaseack` 和 `w_replResp`。[【MSHR.scala:1311】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1311)
4. 同地址 snoop 的阻塞不应无限持续；RXSNP 自带 stall counter assertion，超过阈值会报潜在 deadlock。[【RXSNP.scala:117】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:117)
-->

## 10. Timing, Throughput, and Forward Progress

### 10.1 Quantities confirmed by source

| Quantity | Confirmed conclusion | Limitation |
|---|---|---|
| RXDAT body state | No explicit `Reg`, Queue, or FSM | It is a combinational adapter, not an end-to-end zero-latency claim |
| RXDAT port receive rate | If this Slice already has Decoupled valid, `ready=1`, so it can theoretically consume one CHIDAT per cycle | Limited by top-level routing, upstream `Pipeline`, link credit, and actual CHI-peer supply |
| Complete-line refill rate | Two recognized 32-B beats form one 64-B line; with two consecutive beats, the ideal limit is one line every two cycles | MSHR, probe, release, MainPipe/directory resources can delay completion visible to L1 |
| Physical ingress capacity | RXDAT link uses 15 L-Credits | This is not a 15-entry RXDAT FIFO; the `blocking=false` branch has no 15-entry Queue. [LinkLayer.scala:181](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:181) [LinkLayer.scala:211](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:211) |
| Decoupled buffering | `Pipeline` provides `Queue(1, pipe=true, flow=false)` | Chisel Queue ready-bypass details must not be simplified to a fixed N-cycle latency |

### 10.2 Claims static source cannot establish

- It does not give a fixed number of cycles from external `flitv` to MainPipe/DataStorage. Link state, credits, queues, MSHR state, RequestArb, and MainPipe all participate.
- It does not establish that RXDAT supports 15 in-flight cache lines. Fifteen is physical link credit; there are 16 MSHRs per Slice and 15 usable on Channel A, with distinct meanings.
- `io.out.ready := true` does not prove that RefillBuffer never conflicts. Two downstream interfaces without ready make correctness depend on legal protocol and surrounding scheduling.

### 10.3 Key forward-progress conditions

1. The RX link must enter RUN, have `lcreditPool > 0`, and see downstream Decoupled ready before it returns L-Credit. [LinkLayer.scala:148](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/chi/LinkLayer.scala:148)
2. `txnID` must identify a still-valid cacheable MSHR belonging to this Slice.
3. After receiving the required data, MSHR must still wait for probe/replacement/release/ack conditions. `will_free`'s `no_wait` explicitly includes `w_grantlast`, `w_grant`, `w_releaseack`, and `w_replResp`. [MSHR.scala:1311](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MSHR.scala:1311)
4. Same-address snoop blocking must not persist indefinitely; RXSNP has a stall-counter assertion that reports a potential deadlock after its threshold. [RXSNP.scala:117](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/RXSNP.scala:117)

<!--
## 11. 跨边界分析

### 11.1 cacheable 与 MMIO 边界

顶层的 `rxdatIsMMIO = txnID.head` 决定到 Slice 还是 MMIOBridge；目标 RXDAT 只可见 cacheable 路径。MMIOBridge 自己将 `rxdat.ready` 绑定为 `!w_compdata && s_txreq`，与 RXDAT 的恒 ready 行为不同。[【TL2CHICoupledL2.scala:251】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:251) [【MMIOBridge.scala:321】](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MMIOBridge.scala:321)

所以“RXDAT 不反压”仅适用于 cacheable Slice RXDAT，不能推广到 MMIO response；也不能将 MMIO side effect、PMA/PBMT、TLB/PMP 等归给目标模块。

### 11.2 地址、页和 cache-line 边界

| 边界 | RXDAT 是否拥有判断 | 依据 | 应如何分析 |
|---|---|---|---|
| 虚拟页 / TLB / PMP | 否 | IO 没有地址或翻译/权限字段，set/tag 被置 0 | 沿上游 L1、TL request 与 MSHR allocation 查；不能在 RXDAT 内声称有跨页处理 |
| set / tag / way | 否 | 只携带 TXNID；Directory/DataStorage 由 MainPipe 所有 | 回填的实际 set/tag 来自 MSHR request/dir result，不来自 CHIDAT |
| 64 B line 的两个 32 B storage beat | 是，作为 `dataID==00/10` 的实现约定 | `beatMask = Cat(last, first)` | 覆盖合法首/末拍和乱序/重复拍；对其它 DataID 应有协议约束或 assertion |
| Slice 边界 | 是，位于顶层路由 | TXNID 外层 SliceID | 验证同 inner ID 跨 Slice 同时返回不会串写 |

### 11.3 与 HuanCun / OpenLLC 的真实边界

在 `EnableCHI` 为真的当前配置中，`SoCParamsKey` 只在 `!EnableCHI` 时构造 `L3CacheParamsOpt`；CHI 时构造 `OpenLLCParamsOpt`。[【Configs.scala:219】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:219) [【Configs.scala:232】](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:232)

故本 RXDAT 的上游生产者应写作“CHI 对端，例如本地 OpenLLC 或外部互连/LLC”，不能写作“Huancun 返回数据”。HuanCun 的源码可以用于理解参数历史和旧 TileLink cache，但不是此 CHI RXDAT 的动态数据路径。
-->

## 11. Cross-boundary Analysis

### 11.1 Cacheable versus MMIO boundary

At the top level, `rxdatIsMMIO = txnID.head` selects a Slice or MMIOBridge. The target RXDAT sees only the cacheable path. MMIOBridge itself ties `rxdat.ready` to `!w_compdata && s_txreq`, unlike RXDAT's constant-ready behavior. [TL2CHICoupledL2.scala:251](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/TL2CHICoupledL2.scala:251) [MMIOBridge.scala:321](/home/yanyusong/xs-memory-env/XiangShan/coupledL2/src/main/scala/coupledL2/tl2chi/MMIOBridge.scala:321)

Therefore “RXDAT does not backpressure” applies only to cacheable Slice RXDAT and must not be generalized to MMIO responses. Nor may MMIO side effects, PMA/PBMT, or TLB/PMP be assigned to the target module.

### 11.2 Address, page, and cache-line boundaries

| Boundary | Does RXDAT own the decision? | Basis | How to analyze it |
|---|---|---|---|
| Virtual page / TLB / PMP | No | I/O has no address, translation, or permission field; set/tag are zero | Trace upstream L1, TL requests, and MSHR allocation; do not claim page-crossing handling inside RXDAT |
| Set / tag / way | No | Only TXNID is carried; MainPipe owns Directory/DataStorage | Actual refill set/tag comes from MSHR request/directory result, not CHIDAT |
| Two 32-B storage beats of a 64-B line | Yes, as an implementation convention for `dataID==00/10` | `beatMask = Cat(last, first)` | Cover legal first/last beats and reordered/repeated beats; other DataIDs need protocol constraints or assertions |
| Slice boundary | Yes, in top-level routing | Outer SliceID in TXNID | Verify that concurrent returns with the same inner ID in different Slices do not cross-write |

### 11.3 Real boundary with HuanCun / OpenLLC

In the current configuration with `EnableCHI` true, `SoCParamsKey` constructs `L3CacheParamsOpt` only when `!EnableCHI`; CHI uses `OpenLLCParamsOpt`. [Configs.scala:219](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:219) [Configs.scala:232](/home/yanyusong/xs-memory-env/XiangShan/src/main/scala/top/Configs.scala:232)

The upstream producer of this RXDAT should therefore be described as “a CHI peer, for example local OpenLLC or an external interconnect/LLC,” not “HuanCun returns data.” HuanCun source can help explain parameter history and the older TileLink cache, but it is not the dynamic data path for this CHI RXDAT.

<!--
## 12. 结构与时序示意

### 12.1 数据和接口图

```mermaid
flowchart LR
  EXT[CHI peer RXDAT flit] -- >|flitv/flit/lcrdv| LINK[LinkMonitor\nLCredit2Decoupled: 15, nonblocking]
  LINK -- > PIPE[Pipeline\nQueue depth 1, flow=false]
  PIPE -- > ROUTE{txnID route}
  ROUTE -- >|cacheable + SliceID| SLICE[tl2chi.Slice]
  ROUTE -- >|MMIO| MMIO[MMIOBridge\nnot this RXDAT]
  SLICE -- > RXDAT[RXDAT\nready=1, no state]
  RXDAT -- >|Valid MSHRBufWrite| RB[RefillBuffer\nMSHRBuffer.w[0]]
  RXDAT -- >|Valid RespBundle| CTL[MSHRCtl]
  CTL -- >|id match + status.valid| MSHR[MSHR state]
  MSHR -- >|mainpipe task| MP[MainPipe]
  MP -- > DS[Directory / DataStorage / GrantBuffer]
```

### 12.2 RXDAT 模块接口图

```mermaid
flowchart TB
  CHI[Slice.io.out.rx.dat\nDecoupled CHIDAT] -- >|valid, bits| RX[RXDAT]
  RX -- >|ready = true| CHI
  RX -- >|ValidIO MSHRBufWrite\nid, replicated data, beatMask| RB[MSHRBuffer.w[0]\nRefillBuffer]
  RX -- >|RespBundle\nvalid, mshrId, RespInfo| CTL[MSHRCtl.resps.rxdat]
  CTL -- >|status.valid and ID match| MSHR[MSHR entries]
```

这张图刻意没有画 DataStorage 直连：RXDAT 到 DataStorage 必须先经过 RefillBuffer read、MSHR task 和 MainPipe，任何省略这几步的图都会误导为“收到 DAT 即安装 cache line”。

### 12.3 两拍回填的 WaveDrom 检查模型

下图是一个合法连续 `CompData` 示例。`rxdat.fire` 在本模块内与 `rxdat.valid` 等价，因为 `ready` 恒为 1；`mshr.last` 只在第二个 beat 为真。它不是实测波形。

```waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p....."},
    {"name": "link RUN", "wave": "1....."},
    {"name": "rxdat.valid", "wave": "010100", "data": ["first: dataID=00", "last: dataID=10"]},
    {"name": "rxdat.ready", "wave": "1....."},
    {"name": "rxdat.fire", "wave": "010100"},
    {"name": "refillBufWrite.valid", "wave": "010100"},
    {"name": "beatMask", "wave": "0x0x00", "data": ["01", "10"]},
    {"name": "mshrResp.valid", "wave": "010100"},
    {"name": "mshrResp.last", "wave": "000100"},
    {"name": "MSHR w_grantfirst", "wave": "001111"},
    {"name": "MSHR w_grantlast", "wave": "000011"}
  ]
}
```

时间关系的关键点是：RXDAT 在 fire 当拍给出 Valid 输出，RefillBuffer/MSHR 的 `Reg` 状态在该拍末更新；MainPipe 的读取和最终 DataStorage 写入处于更后的、可阻塞阶段。
-->

## 12. Structural and Timing Sketches

### 12.1 Data and interface diagram

```mermaid
flowchart LR
  EXT[CHI peer RXDAT flit] -->|flitv/flit/lcrdv| LINK[LinkMonitor\nLCredit2Decoupled: 15, nonblocking]
  LINK --> PIPE[Pipeline\nQueue depth 1, flow=false]
  PIPE --> ROUTE{txnID route}
  ROUTE -->|cacheable + SliceID| SLICE[tl2chi.Slice]
  ROUTE -->|MMIO| MMIO[MMIOBridge\nnot this RXDAT]
  SLICE --> RXDAT[RXDAT\nready=1, no state]
  RXDAT -->|Valid MSHRBufWrite| RB[RefillBuffer\nMSHRBuffer.w[0]]
  RXDAT -->|Valid RespBundle| CTL[MSHRCtl]
  CTL -->|id match + status.valid| MSHR[MSHR state]
  MSHR -->|mainpipe task| MP[MainPipe]
  MP --> DS[Directory / DataStorage / GrantBuffer]
```

### 12.2 RXDAT module interface diagram

```mermaid
flowchart TB
  CHI[Slice.io.out.rx.dat\nDecoupled CHIDAT] -->|valid, bits| RX[RXDAT]
  RX -->|ready = true| CHI
  RX -->|ValidIO MSHRBufWrite\nid, replicated data, beatMask| RB[MSHRBuffer.w[0]\nRefillBuffer]
  RX -->|RespBundle\nvalid, mshrId, RespInfo| CTL[MSHRCtl.resps.rxdat]
  CTL -->|status.valid and ID match| MSHR[MSHR entries]
```

The diagram deliberately omits a direct DataStorage edge: RXDAT reaches DataStorage only through RefillBuffer read, an MSHR task, and MainPipe. A diagram omitting these stages would incorrectly imply that receiving DAT installs the cache line immediately.

### 12.3 WaveDrom check model for a two-beat refill

The following is a legal consecutive `CompData` example. In this module, `rxdat.fire` equals `rxdat.valid` because `ready` is always 1; `mshr.last` is true only for the second beat. It is not a measured waveform.

```waveform-draw
{
  "signal": [
    {"name": "clk", "wave": "p....."},
    {"name": "link RUN", "wave": "1....."},
    {"name": "rxdat.valid", "wave": "010100", "data": ["first: dataID=00", "last: dataID=10"]},
    {"name": "rxdat.ready", "wave": "1....."},
    {"name": "rxdat.fire", "wave": "010100"},
    {"name": "refillBufWrite.valid", "wave": "010100"},
    {"name": "beatMask", "wave": "0x0x00", "data": ["01", "10"]},
    {"name": "mshrResp.valid", "wave": "010100"},
    {"name": "mshrResp.last", "wave": "000100"},
    {"name": "MSHR w_grantfirst", "wave": "001111"},
    {"name": "MSHR w_grantlast", "wave": "000011"}
  ]
}
```

The timing relationship is important: RXDAT produces Valid outputs in the fire cycle, while RefillBuffer/MSHR `Reg` state updates at that cycle's end. MainPipe reads and final DataStorage write occur in later stages that may block.

<!--
## 13. 动态场景与验证矩阵

| 场景 | 刺激 | 必看信号 / 状态 | 正确判定 |
|---|---|---|---|
| 正常两拍 `CompData` | 同 TXNID 先 `dataID=00` 后 `dataID=10` | `refillBufWrite.id/mask`、`io.in.valid/last`、MSHR `w_grantfirst/w_grantlast` | 两个 beat 分别写入，同一个有效 MSHR 完成数据阶段 |
| Issue C `DataSepResp` | 按实际 Issue C 时序输入 response/data | `chiOpcode`、MSHR `beatCnt` 和 `w_grantlast` | 仅 MSHR 的 Issue C 分支负责推进，不可断言 RXDAT 有 opcode filter |
| 单拍、反序或重复 first | 连续两个 `dataID=00`，或 `10 -> 00` | mask、MSHR `w_grantlast`、buffer entry | 当前 MSHR 可在第二个有效 `CompData` 后完成，但 buffer 可能只写低 beat 或以非协议次序更新；这应由协议 checker 报错，不能指望模块自检 |
| 非 00/10 DataID | 例如 `01` 或 `11` | `refillBufWrite.valid`、`beatMask`、`io.in.valid` | 当前代码出现 valid+zero mask 且不报 MSHR；必须决定由上游约束还是增加 assertion |
| DataCheck error | `valid=1` 且生成错误 check | RXDAT assertion、`corrupt` | 断言应触发；不能只检查 downstream corrupt |
| poison / DERR / NDERR | 分别激励三类错误 | `denied`、`corrupt`、MSHR `mp_grant.*Wen` | NDERR 既 denied 又 corrupt；DERR/poison 至少 corrupt；denied 时禁止 DS/meta/tag write |
| 同 TXNID 双写端口 | RXDAT 与 SinkC 同拍命中同一 RefillBuffer ID | `wens`、`PriorityMux` 结果、最终两个 beat | RXDAT port 0 优先，SinkC port 1 payload 不被合并；须证明合法调度避免该冲突 |
| snoop 与首拍交叠 | 首拍完成后向同 set/tag 施加 snoop | `w_grantfirst`、`msInfo.blockRefill`、RXSNP `stall` | snoop 在代码指定窗口被阻塞，直到可安全嵌套/推进 |
| MSHR 已释放的迟到 DAT | 释放后向旧 TXNID 注入 DAT | MSHRCtl per-entry valid、RefillBuffer write | MSHR 不应复活；此输入应由 CHI transaction 协议禁止，且应评估 buffer 污染风险 |
| MMIO RXDAT | TXNID 高位为 1 | 顶层 route、`MMIOBridge.rxdat.ready` | 不应实例化/触及 cacheable Slice RXDAT |
-->

## 13. Dynamic Scenarios and Verification Matrix

| Scenario | Stimulus | Signals / state to inspect | Correct verdict |
|---|---|---|---|
| Normal two-beat `CompData` | Same TXNID with `dataID=00` then `dataID=10` | `refillBufWrite.id/mask`, `io.in.valid/last`, MSHR `w_grantfirst/w_grantlast` | Two beats write separately and the same valid MSHR completes its data stage |
| Issue C `DataSepResp` | Apply response/data in the actual Issue C sequence | `chiOpcode`, MSHR `beatCnt`, and `w_grantlast` | Only the MSHR Issue C branch advances it; do not assert that RXDAT has an opcode filter |
| One beat, reversed, or repeated first | Two `dataID=00` beats, or `10 -> 00` | Mask, MSHR `w_grantlast`, buffer entry | Current MSHR can complete after the second valid `CompData`, but the buffer may write only the low beat or update in non-protocol order; a protocol checker must flag it rather than relying on module self-checking |
| Non-00/10 DataID | For example `01` or `11` | `refillBufWrite.valid`, `beatMask`, `io.in.valid` | Current code produces valid plus zero mask and no MSHR report; decide whether upstream constrains it or an assertion is added |
| DataCheck error | `valid=1` with an erroneous check | RXDAT assertion, `corrupt` | Assertion must trigger; checking only downstream corrupt is insufficient |
| poison / DERR / NDERR | Stimulate the three error classes separately | `denied`, `corrupt`, MSHR `mp_grant.*Wen` | NDERR is both denied and corrupt; DERR/poison are at least corrupt; denied suppresses DS/meta/tag writes |
| Two write ports with same TXNID | RXDAT and SinkC hit the same RefillBuffer ID in one cycle | `wens`, `PriorityMux` result, final two beats | RXDAT port 0 has priority and SinkC port-1 payload is not merged; legal scheduling must be proven to avoid this conflict |
| Snoop overlapping the first beat | Apply a snoop to the same set/tag after first beat completes | `w_grantfirst`, `msInfo.blockRefill`, RXSNP `stall` | Snoop blocks in the source-defined window until it can nest/progress safely |
| Late DAT for a released MSHR | Inject DAT with old TXNID after release | Per-entry MSHRCtl valid, RefillBuffer write | MSHR must not revive; the CHI transaction protocol must forbid this input and buffer-pollution risk must be assessed |
| MMIO RXDAT | TXNID high bit is 1 | Top-level route, `MMIOBridge.rxdat.ready` | Cacheable Slice RXDAT must not be instantiated or touched |

<!--
## 14. 结论

1. 有效路径是 CHI link -> 单项 pipeline -> TXNID 路由 -> Slice RXDAT -> RefillBuffer 与 MSHRCtl，而非 HuanCun。
2. RXDAT 本体无状态、无地址、无 opcode 过滤、无 downstream ready；它的职责是格式/状态信息扇出，不是事务完成器。
3. 64 B line 的两拍重组实际发生在 `MSHRBuffer` 的两个 beat 寄存槽中，MSHR 用 response state 决定何时真正完成并进入 MainPipe。
4. 固定 `ready` 仅说明本模块不产生接口反压。链路 credit、pipeline、合法 TXNID、MSHR 生命周期、双写端口和 snoop/replace 状态仍是吞吐与正确性的决定因素。
5. 当前源码最需要被动态验证的角落是非法 `dataID`、RXDAT/SinkC 同 entry 双写、迟到 DAT 和首拍后的 snoop 竞争。
-->

## 14. Conclusions

1. The active path is CHI link -> one-entry pipeline -> TXNID routing -> Slice RXDAT -> RefillBuffer and MSHRCtl, not HuanCun.
2. RXDAT itself is stateless, addressless, has no opcode filter and no downstream ready; its role is to fan out format/state information, not to complete a transaction.
3. Two-beat reconstruction of a 64-B line actually occurs in the two beat-register slots of `MSHRBuffer`; MSHR response state decides when completion truly occurs and proceeds to MainPipe.
4. Fixed `ready` means only that this module creates no interface backpressure. Link credit, the pipeline, valid TXNID, MSHR lifetime, dual write ports, and snoop/replacement state still determine throughput and correctness.
5. The source corners that most need dynamic validation are illegal `dataID`, same-entry RXDAT/SinkC dual writes, late DAT, and snoop contention after the first beat.

<!--
## 15. 验证特别注意

本次只做静态源码审阅，未运行仿真。下面是建议的最小验证闭环，按风险优先级排列。

| 优先级 | 验证动作 | 可观察点 | 通过条件 |
|---|---|---|---|
| P0 | 在 CHI B/C testbench 发连续合法两拍 response | `TL2CHICoupledL2.rxdat`、`Slice.rxdat.io.out`、`MSHRBuffer.buffer`、MSHR state | 两拍 mask 为 `01/10`，同 MSHR 完成，数据在进入 MainPipe 前可读出完整 line |
| P0 | 注入 dataCheck error | RXDAT assertion | `valid=1` 时 assertion 确实失败，证明错误不会静默越过本模块 |
| P0 | 注入 DERR/NDERR/poison | `denied/corrupt`、`mp_grant.metaWen/tagWen/dsWen` | 映射与第 9.2 节一致，NDERR 不写 cache storage |
| P0 | 用 formal/assertion 或随机约束限制输入 DataID 顺序 | `dataID`、mask、`io.in.valid`、MSHR `w_grantlast` | 对本实现承认的 `00 -> 10` 序列建立不变量；反序/重复 00 必须被拒绝，因为当前状态机不会利用 `last` 自检 |
| P1 | 同拍 RXDAT 与 SinkC 写同 MSHRBuffer entry | `wens`、`PriorityMux`、entry 两个 beat | 验证 RXDAT port 0 的优先选择，并证明合法调度永不生成该冲突 |
| P1 | 多 Slice 同 inner TXNID 并发返回 | 外层 TXNID、`getSliceID/restoreTXNID`、各 Slice buffer | 数据只到预期 Slice，恢复后的 inner ID 不串写 |
| P1 | MSHR free 后迟到 response | MSHR `status.valid`、buffer write | 无效 MSHR 不接收 response；系统协议/monitor 能发现非法迟到 DAT |
| P1 | 首拍后施加同地址 RXSNP | `w_grantfirst`、`blockRefill`、RXSNP `stallCnt` | snoop 按源码阻塞并最终前进，不触发 deadlock assertion |
| P2 | 采集 FST 并按 TXNID 而非 PC 追踪 | link flit、pipe enq/deq、RXDAT、MSHR、MainPipe | 能区分 link accept、RXDAT fire、buffer write、MSHR completion 和最终上游 grant |
| P2 | 对比 cacheable 与 MMIO RXDAT | route mux、MMIOBridge ready | cacheable 路径恒 ready 的结论不被误用于 MMIO |

建议在波形中固定一个 cacheable TXNID，至少记录：物理 `flitv/lcrdv`、`linkMonitor.io.in.rx.dat.valid/ready`、`rxdatPipe` enqueue/dequeue、顶层 Slice 选择、`RXDAT.io.out.valid/ready`、`refillBufWrite`、`MSHRCtl.resps.rxdat`、目标 `MSHR` 的 `w_grantfirst/w_grantlast`，以及 MainPipe 的 RefillBuffer read。只观察 `RXDAT.io.out.valid` 会遗漏 credit、路由和 MSHR 生命周期三类关键原因。
-->

## 15. Special Verification Considerations

This pass performed only static source review and did not run simulation. The following is a suggested minimum verification loop, ordered by risk priority.

| Priority | Verification action | Observable points | Pass condition |
|---|---|---|---|
| P0 | Send consecutive legal two-beat responses in the CHI B/C testbench | `TL2CHICoupledL2.rxdat`, `Slice.rxdat.io.out`, `MSHRBuffer.buffer`, MSHR state | Masks are `01/10`, the same MSHR completes, and the complete line is readable before MainPipe entry |
| P0 | Inject a dataCheck error | RXDAT assertion | Assertion truly fails when `valid=1`, proving the error cannot silently cross this module |
| P0 | Inject DERR/NDERR/poison | `denied/corrupt`, `mp_grant.metaWen/tagWen/dsWen` | Mapping agrees with Section 9.2 and NDERR does not write cache storage |
| P0 | Constrain input DataID order with formal/assertions or random constraints | `dataID`, mask, `io.in.valid`, MSHR `w_grantlast` | Establish invariants for this implementation's `00 -> 10` sequence; reversed/repeated 00 must be rejected because the current state machine does not use `last` for self-checking |
| P1 | Write one MSHRBuffer entry from RXDAT and SinkC in the same cycle | `wens`, `PriorityMux`, entry's two beats | Verify RXDAT port-0 priority and prove legal scheduling never creates the conflict |
| P1 | Concurrent returns with the same inner TXNID in multiple Slices | Outer TXNID, `getSliceID/restoreTXNID`, per-Slice buffers | Data reaches only the intended Slice and restored inner IDs never cross-write |
| P1 | Late response after MSHR free | MSHR `status.valid`, buffer write | Invalid MSHR does not receive a response; system protocol/monitor detects illegal late DAT |
| P1 | Apply same-address RXSNP after first beat | `w_grantfirst`, `blockRefill`, RXSNP `stallCnt` | Snoop blocks according to source and eventually progresses without a deadlock assertion |
| P2 | Collect FST and track by TXNID rather than PC | Link flit, pipe enq/deq, RXDAT, MSHR, MainPipe | Distinguish link accept, RXDAT fire, buffer write, MSHR completion, and final upstream grant |
| P2 | Compare cacheable and MMIO RXDAT | Route mux, MMIOBridge ready | Constant-ready conclusion for cacheable path is not misapplied to MMIO |

In waveforms, pin one cacheable TXNID and record at minimum physical `flitv/lcrdv`, `linkMonitor.io.in.rx.dat.valid/ready`, `rxdatPipe` enqueue/dequeue, top-level Slice selection, `RXDAT.io.out.valid/ready`, `refillBufWrite`, `MSHRCtl.resps.rxdat`, target MSHR `w_grantfirst/w_grantlast`, and MainPipe's RefillBuffer read. Observing only `RXDAT.io.out.valid` misses the three key classes of cause: credit, routing, and MSHR lifetime.
