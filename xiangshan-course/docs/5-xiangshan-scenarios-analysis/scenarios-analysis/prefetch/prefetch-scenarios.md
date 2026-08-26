## prefetch.r — 模块 × 场景对照表

| 模块 | R1: 命中DCache | R2: 未命中DCache | R3: 跨页边界 | R4: 地址无效 | R5: 非对齐imm | R6: 端口冲突 | R7: MSHR满 | R8: L2联动 | R9: demand清除标记 |
|------|------|------|------|------|------|------|------|------|------|
| **Decode** | 识别prefetch.r→LoadUnit μop, instrType=prefetch, pftType=swData, pftCoh=read | 同R1 | 同R1 | 同R1（译码无法判断地址有效性） | 同R1，不校验imm[4:0] | 同R1 | 同R1 | 同R1 | 同R1；后续demand load正常译码 |
| **ICache** | — | — | — | — | — | — | — | — | — |
| **DCache** | LoadPipe S0读meta+tag; S1命中; S2不读数据, 更新access_flag+pf_source | S0读meta+tag; S1未命中; S2发M_PFR至MissQueue | 同R2（cache line完全属新页,4096%64=0） | S0读meta+tag; S1用已有paddr比较,不感知无效 | S0 get_dcache_idx天然忽略低6位; S1 tag比较用对齐地址,等效对齐 | LoadPipe共用,同周期仅处理一请求; S0仲裁决定谁进入 | S2发M_PFR; MissQueue返回full | 预取完成后通过l1_pf_to_l2向L2发预取提示 | 预取:写入行+pf_source标记; demand:命中→pf_source更新为CLEAR |
| **LoadUnit** | S0 prefetchHiConf/LoConf入口; S1 noQuery=true跳过TLB; S2-S3无数据写回无唤醒 | 同R1 S0-S1; S2-S3无数据写回(Hint可不等miss完成) | 同R2; paddr指向新页物理地址 | S1 noQuery=true不产生tlbException; S2-S3无异常上报 | 同R1; vaddr低6位在DCache内被丢弃 | S0仲裁: demand>prefetchLoConf; 冲突时prefetchReq.ready=false | S2 MissQueue拒绝→请求直接丢弃,不replay | 正常路径 | 预取:正常路径; demand:正常load路径,命中已预取行 |
| **StoreUnit** | — | — | — | — | — | — | — | — | — |
| **ITLB** | — | — | — | — | — | — | — | — | — |
| **DTLB** | 不查询(noQuery=true) | 不查询 | 不查询(paddr已由预取器提前翻译) | 不查询→异常被静默吞掉 | 不查询 | 不查询 | 不查询 | 不查询 | 预取:不查询; demand:正常查询DTLB |
| **L2TLB/PTW** | — | — | — | — | — | — | — | — | demand路径可能触发 |
| **MissQueue** | 不进入(命中) | 分配MSHR→L2 CHI ReadShared→重填tag+数据+pf_source | 同R2 | paddr无效→L2请求可能被拒绝→最终完成或丢弃 | 同R2, 重填时用get_block_addr对齐 | 不进入(请求阻塞在S0) | 返回full,不分配MSHR | 可能参与DCache侧重填 | 不进入(两步都命中) |
| **ROB** | μop正常写回完成(无数据),可提交 | 同R1 | 同R1 | μop正常完成不报异常; ⚠️若完成信号缺失可能阻塞推进 | 同R1 | 预取μop在IQ等待; demand正常执行 | 预取丢弃视为Hint完成,μop正常完成 | 同R1 | 预取μop完成; demand正常写回数据 |
| **L2 PfRecv** | — | 不触发(除非l2_pf_recv_enable) | — | — | — | — | — | 接收l1_pf_to_l2→分配L2 MSHR→L3 ReadShared→重填L2→发l2_hint | — |

---

## prefetch.w — 模块 × 场景对照表

| 模块 | W1: 命中DCache | W2: 未命中DCache | W3: M_PFW提前分配 | W4: 地址无效 | W5: 后续store命中 | W6: 预取浪费 | W7: L2联动 | W8: 冲突/MSHR满 |
|------|------|------|------|------|------|------|------|------|
| **Decode** | 识别prefetch.w→LoadUnit μop, pftCoh=write | 同W1 | 同W1 | 同W1 | 同W1; 后续store正常译码 | 同W1 | 同W1 | 同W1 |
| **ICache** | — | — | — | — | — | — | — | — |
| **DCache** | LoadPipe S0 cmd=M_PFW; S1命中→不读数据, 更新access_flag+pf_source; 一致性状态不变 | S0 cmd=M_PFW; S1未命中; S2发M_PFW至MissQueue | MissQueue收到M_PFW→先分配不含数据的缓存行; 后续store数据到达时直接写入 | S0读meta+tag; S1用已有paddr比较,不感知无效 | 预取:miss→重填行进DCache+pf_source; store:StorePipe/MainPipe写入该行→直接命中 | 重填行进DCache+pf_source; 该行后续从未被store写入→占据DCache空间,可能驱逐有用行 | 预取完成后通过l1_pf_to_l2向L2发写预取提示 | LoadPipe端口冲突:同W1被阻塞; MSHR满:S2 MissQueue拒绝 |
| **LoadUnit** | S0仲裁入LoadPipe pftCoh=write; S1 noQuery=true; S2-S3无数据写回 | 同W1 | S2发M_PFW后即可完成(Hint不等重填) | S1 noQuery=true无异常上报; S2-S3不报异常 | 正常预取路径 | 正常预取路径 | 正常路径 | 端口冲突:S0仲裁失败; MSHR满:S2丢弃 |
| **StoreUnit** | — | — | 不直接参与; 后续store受益:cache行已分配,减少write miss延迟 | — | store正常执行→命中已预取行→避免write miss (核心价值) | — | — | — |
| **ITLB** | — | — | — | — | — | — | — | — |
| **DTLB** | 不查询(noQuery=true) | 不查询 | 不查询 | 不查询→异常被静默吞掉 | 预取:不查询; store:正常查询DTLB(st通道) | 不查询 | 不查询 | 不查询 |
| **L2TLB/PTW** | — | — | — | — | store路径可能触发 | — | — | — |
| **MissQueue** | 不进入(命中) | 分配MSHR cmd=M_PFW→L2 CHI ReadShared(非ReadUnique)→重填 | MSHR分配→L2 ReadShared→重填; 期间标记"预取中"→后续demand可merge | paddr无效→L2请求可能被拒绝 | 预取阶段参与重填; store阶段不进入(命中) | 参与重填 | 可能参与 | 端口冲突:不进入; MSHR满:返回full |
| **ROB** | μop正常写回完成 | 同W1 | prefetch.w μop完成; 后续store受益减少等待 | 同R4: ⚠️已知风险 | 两条μop各自正常完成 | 正常完成 | 正常完成 | 预取丢弃视为完成 |
| **L2 PfRecv** | — | 可触发(若enable) | 可触发 | — | 预取阶段可能触发 | — | 接收写预取→分配L2 MSHR→L3 ReadShared→重填L2; 后续store可在L2命中 | — |
| **性能** | — | — | 正优化:减少store write miss延迟 | — | 正优化:核心价值场景 | ⚠️负优化:占DCache容量+消耗带宽,无收益 | — | — |

---

## prefetch.i — 模块 × 场景对照表

| 模块 | I1: 命中ICache | I2: 未命中ICache | I3: MemBlock注入 | I4: ITLB miss | I5: 地址无效 | I6: BPU冲刷 | I7: 重填广播更新 | I8: RVA22合规 | I9: 后续取指命中 |
|------|------|------|------|------|------|------|------|------|------|
| **Decode** | 识别prefetch.i pftType=swInstr; 不发往LoadUnit,注入ICache PrefetchPipe | 同I1 | 同I1 | 同I1 | 同I1(译码无法判断) | 同I1 | — | 同I1; RVA22要求实现Zicbop | 同I1; 后续取指:IFU正常 |
| **ICache** | PrefetchPipe S0:接收请求,读Meta+ITLB; S1:tag命中→不入WayLookup→判定不需预取; S2:不发miss请求 | S0读Meta+ITLB; S1:tag未命中→不入WayLookup; S2:向MissUnit发miss请求 | S0:请求来自MemBlock软件预取接口,与FTQ硬预取仲裁; S1:不入WayLookup,不进MainPipe | S0:ITLB查询→miss; S1:进入itlbResend状态,占用ITLB端口持续重发→阻塞新请求进S0 | S0:ITLB查询→返回异常; S1:检测异常→Hint直接丢弃,不发miss请求 | S0/S1:检测BPU override→非软件预取被冲刷; 软件预取不被冲刷 | S1生成hit信息后; MissUnit重填可能改变hit状态; 软件预取不入WayLookup→不受WayLookup阻塞 | NOP:ICache忽略; L2预取:正常执行,预取至L2而非L1 | 预取:PrefetchPipe→MissUnit→重填至ICache; 取指:MainPipe→tag命中→直接读出指令(核心价值) |
| **DCache** | — | — | — | — | — | — | — | — | — |
| **LoadUnit** | — | — | — | — | — | — | — | — | — |
| **StoreUnit** | — | — | — | — | — | — | — | — | — |
| **ITLB** | PrefetchPipe S0查询; S1:命中 | S0查询; S1:命中 | S0查询 | S0:miss→触发L2TLB/PTW | S0:返回异常(Page Fault/Access Fault) | 硬预取被冲刷后ITLB查询作废; 软预取ITLB查询继续 | — | NOP:不查询; L2预取:查询 | 预取:查询ITLB; 取指:查询ITLB(可能命中缓存) |
| **DTLB** | — | — | — | — | — | — | — | — | — |
| **L2TLB/PTW** | — | — | — | L2TLB查询→若命中重填ITLB; 若miss→PTW遍历→重填ITLB | — | — | — | — | — |
| **MissQueue** | 不进入(命中) | ICache MissUnit分配MSHR→L2 ReadShared→重填指令数据 | 可能参与(若ICache miss) | 等待ITLB重填完成后才进入MissUnit | 不进入(丢弃) | 被冲刷的硬预取不进入; 软预取正常进入 | MissUnit正常重填并广播 | NOP:不进入; L2预取:向L2 MissUnit发请求 | 预取阶段参与重填; 取指阶段不进入(命中) |
| **ROB** | μop正常完成写回 | 同I1(Hint可不等重填) | 同I1 | μop等待完成; ⚠️ITLB miss期间PrefetchPipe S0被阻塞 | μop正常完成(Hint丢弃=完成); ⚠️若完成信号缺失可能阻塞(Issue#5960) | 软预取μop不受影响 | 不受影响 | μop正常完成 | 两条μop各自正常完成 |
| **L2 PfRecv** | — | — | — | — | — | — | — | — | — |