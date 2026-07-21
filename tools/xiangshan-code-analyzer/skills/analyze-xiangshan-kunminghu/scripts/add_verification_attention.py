#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

BASE = "https://github.com/OpenXiangShan/XiangShan/blob/kunminghu-v2/src/main/scala/xiangshan/"


def link(path: str, lines: str) -> str:
    start, *rest = lines.split("-")
    anchor = f"#L{start}" + (f"-L{rest[0]}" if rest else "")
    return f"[{path}:{lines}]({BASE}{path}{anchor})"


def table(rows: list[tuple[str, str, str, str, str]]) -> str:
    out = [
        "## 验证特别注意",
        "",
        "> 本节依据 `tools/verification-driver/skills` 中的 FSM、冲突、前向进展、索引/哈希、缓存结构、异常/虚拟化和性能瓶颈规则生成。每个期望必须以当前 `kunminghu-v2` 有效 Chisel 为准。",
        "",
        "| Verification ID | 风险 / 不变量 | 定向激励 | 期望观察 | Checker / Coverage |",
        "| --- | --- | --- | --- | --- |",
    ]
    out.extend("| " + " | ".join(row) + " |" for row in rows)
    out += [
        "",
        "### 通用判定原则",
        "",
        "- `valid && !ready` 期间 payload 必须稳定；只有 `fire` 才能推进指针、状态或训练一次。",
        "- flush/redirect/replay 的胜负关系必须按代码优先级检查；错误路径不得提交、写表、训练预测器或暴露异常/数据。",
        "- 资源填满后必须验证可排空；重复冲突、retry 或 redirect 不得形成 deadlock/livelock，并检查低优先级旧请求是否饥饿。",
        "- 环形指针必须覆盖最大值到零的 wrap；表索引必须构造 same-index/different-tag 和同拍 read/write 冲突组。",
        "- 性能覆盖至少记录占用率、反压周期、redirect 恢复延迟、重试次数和恢复后的持续吞吐。",
        "",
    ]
    return "\n".join(out)


def common_predictor(path: str, lookup: str, update: str) -> list[tuple[str, str, str, str, str]]:
    return [
        ("`F_RESET_IDLE`", "复位扫描期间不能输出未初始化预测", "在 reset 释放前后持续给查询 PC", f"ready/response valid 与复位状态一致；首个有效计数器/entry 无陈旧值；证据 {link(path, lookup)}", "FSM checker；reset/first-request cover"),
        ("`H_SAME_INDEX_DIFF_TAG`", "索引 alias 不得伪造错误 hit", "按源码 index/hash 构造同 index、不同 tag 的 PC", "有 tag 表只能命中真实 tag；无 tag Bim 允许方向 alias 但不得破坏端口/状态", "Index/hash checker；alias cross"),
        ("`C_SAME_ENTRY_RW`", "lookup 与 update 同拍同 entry", "查询 PC 与提交 update 命中同 index/way", f"read-old/read-new/旁路/stall 行为与代码一致；证据 {link(path, update)}", "Storage conflict checker；RAW bypass cover"),
        ("`C_MULTI_WRITE_SAME_ENTRY`", "多个分支槽或更新源写同 entry", "构造同拍多个有效更新候选", "写掩码、优先级或非法断言符合代码；不能丢失未胜出请求而无 retry", "Multi-write checker；onehot/mask cover"),
        ("`F_REQ_AND_FLUSH`", "错误路径 lookup/update 与 redirect 竞争", "查询或 update valid 同拍施加 redirect/flush", "错误路径不得训练；流水 meta 被清除或恢复到正确 FTQ entry", "Flush/replay checker；predictor metadata scoreboard"),
        ("`P_LIVELOCK_REPLAY_LOOP`", "持续端口冲突或 update stall", "连续制造 lookup/write 冲突并周期释放端口", "在公平条件下查询和更新最终完成，无重复训练", "Forward-progress checker；retry-exit cover"),
        ("`PB_RECOVERY_THROUGHPUT`", "高负载 redirect 后预测带宽不能永久下降", "饱和查询后注入 redirect，再恢复稳定流", "无陈旧预测可见，流水在有限周期恢复持续服务", "Performance checker；recovery latency/throughput"),
    ]


def rows_for(name: str) -> list[tuple[str, str, str, str, str]]:
    if name == "Bim":
        rows = common_predictor("frontend/Tage.scala", "155-215", "217-267")
        rows[1] = ("`H_SAME_INDEX_DIFF_TAG`", "Bim 无 tag，alias 只允许改变方向统计", "构造两个 PC 映射同 `TageBTable` index", "允许共享 2-bit counter，但计数器必须饱和且不能污染其他 bank/slot", "Index checker；counter saturation cross")
        return rows
    if name == "FauFTB":
        return common_predictor("frontend/FauFTB.scala", "76-128", "139-205") + [
            ("`RESOURCE_CONTENTION`", "有限表容量和替换压力", "填充同 set/索引候选并持续插入新 block", "只替换代码选择的 entry；命中项、fall-through 与 meta 保持自洽", "Replacement scoreboard；capacity stress")
        ]
    if name == "FTB":
        return common_predictor("frontend/FTB.scala", "663-719", "849-878") + [
            ("`FTB_MULTI_HIT`", "多个 way 同时 tag hit", "人为建立两个匹配 entry 后查询", f"优先选择与 `multiHit` 标记一致，并触发修复/redirect；证据 {link('frontend/FTB.scala','694-719')}", "Multi-hit checker；redirect cover"),
            ("`FTB_FALSE_HIT`", "FTB entry 与真实预译码不一致", "让保存槽类型/target 与 IFU `pdWb` 不同", "FTQ 标记 false hit，错误 entry 不被强化并在 update 修复", "FTB/PreDecode scoreboard；false-hit cover"),
        ]
    if name == "Tage":
        return common_predictor("frontend/Tage.scala", "311-448", "904-1006") + [
            ("`TAGE_PROVIDER_ALT`", "provider/alternate/useAltOnNa 选择错误", "覆盖无 provider、弱 provider、强 provider 和 alternate 相反", "最长匹配、置信与 use-alt 规则逐项符合代码", "Provider scoreboard；decision cross"),
            ("`TAGE_ALLOC_FULL`", "更长历史表无可替换项", "把候选 useful 位置满后制造 mispredict", "不得越界分配；aging/跳过行为符合代码", "Allocation/useful checker；full-candidate cover"),
        ]
    if name == "SC":
        return common_predictor("frontend/SC.scala", "259-372", "376-448") + [
            ("`SC_THRESHOLD_EDGE`", "求和或动态阈值数值回绕", "驱动 counter/threshold 到 min、max 及边界两侧", "饱和而不 wrap；override 仅在代码阈值条件成立", "Arithmetic saturation checker；threshold cross"),
            ("`SC_OVERRIDE_TAGE`", "SC 错误覆盖 TAGE", "TAGE 与 SC 正负求和组合全覆盖", "最终方向、override 标志和训练条件一致", "TAGE-SC scoreboard；override outcome cross"),
        ]
    if name == "ITTAGE":
        return common_predictor("frontend/ITTAGE.scala", "311-410", "552-610") + [
            ("`ITTAGE_TARGET_PROVIDER`", "provider target/alternate target 串线", "同一 JALR PC 用不同历史产生多个真实 target", "provider index/tag/meta 对应同一上下文，S3 target 正确", "Indirect-target scoreboard；history-target cross"),
            ("`ITTAGE_ALLOC_FULL`", "目标误预测但无可替换 entry", "填满更长历史候选并触发 target mispredict", "不越界写表；useful aging/分配失败行为符合代码", "Allocation checker；candidate-full cover"),
        ]
    if name == "BPU":
        return [
            ("`F_HOLD_BACKPRESSURE`", "FTQ full 时 S0-S3 stage skew", "拉低 `io.bpu_to_ftq.resp.ready` 并保持查询", f"PC/history/prediction payload 稳定，stage 不误推进；证据 {link('frontend/BPU.scala','381-455')}", "Handshake checker；stage-valid scoreboard"),
            ("`C_REDIRECT_REDIRECT`", "S2、S3、backend redirect 同窗口竞争", "构造晚级 target 差异并同拍后端 redirect", f"唯一 winner、target 和 history 修复符合优先级；证据 {link('frontend/BPU.scala','827-883')}", "Redirect arbiter checker；history recovery checker"),
            ("`F_REQ_AND_FLUSH`", "新预测接受与 flush 同拍", "`resp.fire` 候选同拍施加 redirect", "被 kill block 不进入 FTQ，不更新历史两次", "Flush checker；FTQ allocation scoreboard"),
            ("`BPU_COMPONENT_STALL`", "单个 predictor ready 低导致 Composer 失配", "制造 TAGE/FTB update-read 冲突", "所有组件共同停住，meta 拼接顺序不漂移", "Composer ready checker；metadata scoreboard"),
            ("`P_LIVELOCK_REPLAY_LOOP`", "连续 S2/S3 覆盖导致无前进", "交替制造早晚级方向/target 差异", "在稳定控制流后有限周期产生可接受 prediction block", "Forward-progress checker；redirect-loop cover"),
            ("`PB_BACKPRESSURE_AMPLIFICATION`", "FTQ 阻塞向预测器链放大", "逐步阻塞 FTQ、释放并测量各 stage", "定位反压边界，释放后恢复无陈旧 payload", "Performance checker；stall propagation trace"),
            ("`PB_RECOVERY_THROUGHPUT`", "redirect 后预测吞吐恢复", "饱和流中注入 backend redirect", "目标路径首块和稳定吞吐延迟符合流水", "Recovery latency checker；throughput cover"),
        ]
    if name == "FTQ":
        return [
            ("`RESOURCE_CONTENTION`", "64-entry FTQ 满后覆盖未提交项", "停止 commit 并持续 BPU allocate 直到满", f"`fromBpu.resp.ready` 拉低，旧 entry 不变；证据 {link('frontend/NewFtq.scala','524-590')}", "Occupancy checker；full/almost-full cover"),
            ("`I_WRAP_PTR`", "多指针在 63→0 回绕时年龄错误", "分别推进 bpu/ifu/pf/wb/commit 指针跨回绕", "value+flag 年龄、empty/full 和 isAfter 均正确", "Pointer-age checker；all-pointer wrap cross"),
            ("`FTQ_ALLOC_RECLAIM`", "满状态同拍 reclaim+allocate", "最后空位、commit 和 BPU fire 同拍", "占用不越界，entry 只被合法复用一次", "Occupancy checker；simultaneous enq/deq cover"),
            ("`C_REDIRECT_REDIRECT`", "BPU S2/S3 overwrite 与 backend redirect", "三个来源重叠", "恢复指针、target、status 和历史快照来自唯一 winner", "Redirect checker；pointer/history scoreboard"),
            ("`FTQ_PDW_AFTER_FLUSH`", "被 flush entry 的晚到 `pdWb`", "IFU 请求后 redirect，再返回 predecode", f"晚响应不得复活 entry 或训练；证据 {link('frontend/NewFtq.scala','966-1039')}", "Flush checker；entry-state scoreboard"),
            ("`FTQ_STATUS_LIFECYCLE`", "commit/fetch/hit 状态非法跳转", "覆盖正常、false-hit、commit、flushed 顺序", f"只发生合法状态转移；证据 {link('frontend/NewFtq.scala','662-680')}", "FSM/valid-vector checker；transition cover"),
            ("`P_DEADLOCK_ALL_STALL`", "FTQ/IFU/ICache/BPU 全链阻塞", "阻塞下游后逐一释放", "队列最终排空且 predictor update 不丢失", "Forward-progress checker；drain cover"),
            ("`PB_BURST_ABSORB_DRAIN`", "突发预测吸收和排空能力", "突发填满后停止 BPU、开放消费/提交", "占用曲线达到容量并回到空，无气泡异常", "Performance/occupancy checker"),
        ]
    if name == "IBuffer":
        return [
            ("`F_FIRST_REQUEST`", "空队列首块旁路读取残留数据", "空 IBuffer 同拍 IFU valid 和 Decode ready", f"只输出真实 enq 项，旁路顺序正确；证据 {link('frontend/IBuffer.scala','188-215')}", "Handshake/occupancy checker；empty-bypass cover"),
            ("`F_HOLD_BACKPRESSURE`", "Decode stall 时输出 payload 漂移", "各 lane ready 拉低多拍", "每个 valid lane 的 CtrlFlow 稳定，deqPtr 不误推进", "Per-lane handshake checker；payload-stability assertion"),
            ("`RESOURCE_CONTENTION`", "48-entry full 覆盖最老指令", "停止 Decode、持续 IFU enqueue", f"`in.ready/full` 正确，entry 不覆盖；证据 {link('frontend/IBuffer.scala','158-215')}", "Occupancy checker；full/almost-full cover"),
            ("`IBUF_FULL_ENQ_DEQ`", "满状态同拍出入队容量误算", "full 时让 Decode 消费并保持 IFU valid", "合法复用释放槽，计数不超过 48", "Reference-count checker；simultaneous enq/deq cover"),
            ("`I_WRAP_PTR`", "enq/deq/bank pointer 回绕破坏年龄", "不同 numEnq/numDeq 组合跨回绕", "输出严格按程序年龄，bank rotation 正确", "Pointer-age checker；lane-order cover"),
            ("`F_REQ_AND_FLUSH`", "flush 与 enqueue/dequeue/bypass 竞争", "四类活动同拍 redirect", "flush winner 清 valid/指针，错误路径不输出", "Flush checker；priority cross"),
            ("`C_BANK_CONFLICT`", "多个读写映射同 bank", "构造最大入队和 6-lane 出队组合", "端口使用符合 bank 组织，无丢项/乱序", "Bank-access checker；bank cross"),
            ("`PB_BACKPRESSURE_AMPLIFICATION`", "Decode 阻塞放大到 IFU/FTQ", "逐步填充并测量 ready 链", "精确识别 full 前吸收量和释放后恢复周期", "Performance checker；occupancy/stall trace"),
        ]
    if name == "RAS":
        return [
            ("`RAS_EMPTY_POP`", "空栈 return 读取未定义地址", "复位后立即预测 ret", "underflow 行为与 valid/meta 规则一致，不泄露残留 top", "RAS model checker；empty-pop cover"),
            ("`RESOURCE_CONTENTION`", "spec queue near overflow 覆盖恢复记录", "连续 call/ret 使 TOSW 接近 BOS", f"`spec_near_overflow` 门控 push/pop；证据 {link('frontend/newRAS.scala','594-617')}", "Occupancy checker；near-overflow cover"),
            ("`RAS_RECURSION_CTR`", "递归相同返回地址计数 wrap", "同地址 push 到 ctr min/max 并 pop", f"计数饱和/递减和 nsp 变化符合代码；证据 {link('frontend/newRAS.scala','511-565')}", "Counter/stack model checker"),
            ("`I_WRAP_PTR`", "TOSR/TOSW/ssp/nsp 回绕", "push/pop 跨所有指针边界", "spec/commit top 和 NOS 一致，无年龄反转", "Pointer-age checker；all-pointer wrap"),
            ("`RAS_S3_CANCEL`", "S3 cancel 后漏做/多做 push-pop", "S2 推测与 S3 实际类型相反", f"恢复 meta 后只补做 missed operation；证据 {link('frontend/newRAS.scala','494-508')}", "History/RAS recovery checker"),
            ("`C_REDIRECT_REDIRECT`", "S3 cancel 与 backend redirect/commit 重叠", "同窗口注入三类事件", "最终 stack 与唯一正确路径快照一致", "Redirect-priority checker；stack scoreboard"),
            ("`C_SAME_ENTRY_RW`", "commit/spec 同拍访问相同 stack entry", "构造同 index push/pop/commit", "读写/旁路结果和优先级符合代码", "Storage conflict checker"),
            ("`PB_RECOVERY_THROUGHPUT`", "深递归 redirect 后持续 ret 性能", "填充栈后 redirect，再连续 return", "无永久错位，返回目标和吞吐恢复", "Performance/RAS target checker"),
        ]
    if name == "ICache":
        return [
            ("`CACHE_HIT`", "双行命中数据/way 选择错误", "覆盖 hit/hit 和跨行取指", "两路 tag/data/异常与 IFU block 对齐", "Cache hit scoreboard；two-line cross"),
            ("`CACHE_MISS_INVALID`", "invalid line 被误判 hit", "访问 invalid set/way", "产生 miss、合法 MSHR 请求，无陈旧数据", "Tag/valid checker；miss cover"),
            ("`H_SAME_INDEX_DIFF_TAG`", "同 set 不同 tag alias", "构造同 index 冲突地址", "miss/replace 选择和旧 line 可见性正确", "Index/tag checker；set-conflict cover"),
            ("`CACHE_MSHR_MERGE`", "同 block 重复分配", "两请求命中同在途 block", f"只保留一个底层 Get，后续请求 merge/wait；证据 {link('frontend/icache/ICacheMissUnit.scala','127-158')}", "MSHR scoreboard；merge cover"),
            ("`CACHE_MSHR_FULL`", "MSHR 满仍接受新 miss", "占满所有 MSHR 后再发 miss", "ready 反压且不覆盖 source/entry，释放后可前进", "Occupancy/forward-progress checker"),
            ("`CACHE_ARRAY_RW_CONFLICT`", "refill 写与 demand/meta 读同 set/way", "精确构造同拍数组冲突", "read-old/read-new/bypass/stall 符合仲裁", "Array conflict checker"),
            ("`CACHE_FR_MISS`", "redirect/fence.i 后旧 Grant 安装陈旧 line", "Get issue 后 flush，再返回 Grant 并 reload", f"已 issue MSHR 安全 drain，旧结果不可见；证据 {link('frontend/icache/ICacheMissUnit.scala','140-190')}", "Flush+reload checker；source routing checker"),
            ("`C_TLB_REFILL_INVALIDATE`", "ITLB refill 与 sfence/context switch", "翻译 miss 在途时切换 ASID/VMID/权限", "旧 translation/permission 不用于新请求", "TLB/context isolation checker"),
            ("`E_MEM_PAGE_ACCESS`", "取指 page fault 与 access fault 优先级", "同一 fetch 同时制造翻译和 PMP/PMA deny", "exception cause、PC/tval/gpa 和优先级正确", "Architecture exception scoreboard"),
            ("`P_DEADLOCK_ALL_STALL`", "WayLookup/MSHR/ITLB/总线全阻塞", "填满并阻塞各 sink 后逐一释放", "所有在途请求最终 drain，WFI safe 最终成立", "Forward-progress/WFI checker"),
            ("`P_STARVE_OLD_LOW_NEW_HIGH`", "prefetch 或低优先请求饥饿", "持续 demand 并保留旧 prefetch", "按代码优先级验证是否最终服务及性能影响", "Arbiter/fairness checker；starvation cover"),
        ]
    if name == "IFU":
        return [
            ("`F_HOLD_BACKPRESSURE`", "IBuffer 不 ready 时 F3 payload 漂移", "保持 `toIbuffer.valid`、拉低 ready", f"指令、PC、pd、异常、FTQ ptr 稳定；证据 {link('frontend/IFU.scala','953-980')}", "Handshake checker；payload stability"),
            ("`F_REQ_AND_FLUSH`", "F3 输出/`pdWb` 与 redirect 竞争", "输出候选同拍 older redirect", "错误路径不入 IBuffer、不写回有效训练", "Flush checker；FTQ/IBuffer scoreboard"),
            ("`IFU_LAST_HALF`", "跨块 32-bit 半指令覆盖/下溢", "覆盖保存、合并、flush 和无 half 四种情况", f"`f3_lastHalf.valid` 生命周期正确；证据 {link('frontend/IFU.scala','915-943')}", "Single-entry buffer checker"),
            ("`FSM_MMIO_ALL_TRANS`", "12 态 MMIO FSM 非法跳转", "覆盖普通、跨页、TLB fault、PMP fault、resend、commit", f"只走合法状态和 wait hold；证据 {link('frontend/IFU.scala','655-846')}", "FSM transition checker；state coverage"),
            ("`F_RESP_AND_REPLAY`", "uncache response 与 resend/retranslation 竞争", "第一半响应后同时触发 replay/flush", "只产生一次合法下一动作和最终指令", "Replay checker；response scoreboard"),
            ("`E_MEM_PAGE_ACCESS`", "instruction page/access/guest fault 合并优先级", "跨页并同时制造 ITLB/PMP 异常", "IBuffer exceptionType 和后端可见元数据正确", "Architecture exception scoreboard"),
            ("`CTX_VM_SWITCH`", "MMIO/跨页在途时 VMID/权限切换", "FSM busy 时切换 guest/host translation context", "旧翻译/权限/响应被 flush、标记或重检", "Context isolation checker"),
            ("`P_DEADLOCK_ALL_STALL`", "MMIO FSM 等待链死锁", "分别阻塞 uncache、ITLB、PMP、commit 后释放", "每个 wait state 可退出并最终回 idle", "Forward-progress checker；wait-state exit cover"),
        ]
    if name == "Overview":
        return [
            ("`F_HOLD_BACKPRESSURE`", "BPU→FTQ→IFU→IBuffer 任一反压边界丢失 payload", "逐级拉低 FTQ、ICache、IBuffer ready", f"每一级只在 fire 时推进，顶层连接不产生组合接受漏洞；证据 {link('frontend/Frontend.scala','199-220')}", "End-to-end handshake checker；payload scoreboard"),
            ("`RESOURCE_CONTENTION`", "FTQ、ICache、IBuffer 同时满导致全链阻塞", "饱和预测和取指并填满三个结构", f"模块实例和资源边界保持一致，停止新请求且已接受事务可 drain；证据 {link('frontend/Frontend.scala','103-109')}", "Cross-module occupancy checker"),
            ("`I_WRAP_PTR`", "FTQ 多指针回绕破坏 BPU/IFU/commit 年龄关系", "推进全部 FTQ 指针跨最大值并夹入回收", f"`bpuPtr/ifuPtr/pfPtr/ifuWbPtr/commPtr/robCommPtr` 顺序正确；证据 {link('frontend/NewFtq.scala','524-554')}", "Pointer-age checker；all-pointer cross"),
            ("`C_REDIRECT_REDIRECT`", "BPU S2/S3 与后端 redirect 竞争", "同拍或连续拍触发多级 redirect", f"唯一恢复目标驱动 FTQ/IFU/prefetch，较年轻路径不可见；证据 {link('frontend/NewFtq.scala','756-779')}", "Redirect-priority checker；recovery scoreboard"),
            ("`CTX_VM_SWITCH`", "sfence/CSR 延迟与在途取指上下文错配", "FTQ/ICache/ITLB 有请求时切换地址空间和权限", f"ITLB、PMP 与取指请求使用一致上下文，旧权限不泄漏；证据 {link('frontend/Frontend.scala','120-179')}", "Context-isolation/exception checker"),
            ("`P_DEADLOCK_ALL_STALL`", "预测、翻译、缓存和后端消费形成等待环", "阻塞所有 sink 后按不同顺序释放", "最老请求最终完成或被 redirect 唯一取消，所有结构可回空", "Forward-progress checker；drain cover"),
            ("`PB_RECOVERY_THROUGHPUT`", "redirect 或 miss 后前端吞吐永久下降", "饱和流中交替注入分支误预测、ITLB/ICache miss", f"FTQ 重新允许 BPU 输入并恢复 IFU 推进；证据 {link('frontend/NewFtq.scala','590-599')}", "Performance checker；recovery latency/IPC"),
        ]
    if name == "Decode":
        return [
            ("`F_HOLD_BACKPRESSURE`", "Rename 反压时多路译码结果不得漂移或越过", "令 `io.out.head.ready=0`，同时保持简单与复杂指令输入有效", f"`readyCounter`、`complexValid`、输出 valid/payload 和输入 ready 按接受条件保持；证据 {link('backend/decode/DecodeStage.scala','94-150')}", "Handshake checker；lane-order scoreboard"),
            ("`DECODE_COMPLEX_EXPAND`", "复杂指令扩展的 uop 数量与顺序错误", "覆盖不同 `complexNum`，前后夹简单指令", f"复杂 uop 在简单译码结果前输出，且不超过 Rename 可接收宽度；证据 {link('backend/decode/DecodeStage.scala','141-181')}", "Expansion scoreboard；uop-count cross"),
            ("`F_REQ_AND_FLUSH`", "redirect 与复杂译码/vtype 更新竞争", "`decoderComp.io.in.fire` 同拍拉高 redirect", f"错误路径不得更新 vtype，复杂译码状态与输出被杀死；证据 {link('backend/decode/DecodeStage.scala','156-176')}", "Flush/FSM checker；vtype scoreboard"),
            ("`DECODE_ILLEGAL_PRIORITY`", "非法/虚拟指令异常与既有异常优先级错误", "构造非法编码、虚拟指令和前端异常组合", f"`EX_II`/`EX_VI` 只落到对应指令，最老非法指令选择与代码一致；证据 {link('backend/decode/DecodeStage.scala','131-139')}", "Architecture exception scoreboard"),
            ("`C_MULTI_COMPLEX`", "同拍多条复杂指令只能选择一条处理", "多个 lane 同时标记 `isComplex`", "PriorityMux 只接受代码选中的最老候选，其余输入保持/重试且不丢失", "Arbiter checker；oldest-wins cover"),
            ("`DECODE_DEFAULT_SAFE`", "未知编码默认控制信号形成幽灵写回或访存", "随机保留/非法 opcode、funct 与扩展组合", f"DecodeUnitComp 输出默认值、异常和功能单元选择安全；证据 {link('backend/decode/DecodeUnitComp.scala','108-220')}", "Decode truth-table checker；illegal cross"),
            ("`PB_RECOVERY_THROUGHPUT`", "复杂译码与反压解除后吞吐无法恢复", "简单/复杂混合流饱和输入，周期性 redirect 和阻塞 Rename", "恢复后无重复/丢失 uop，并回到代码允许的持续译码带宽", "Performance checker；decode IPC/recovery latency"),
        ]
    if name == "Rename":
        return [
            ("`RESOURCE_CONTENTION`", "任一类型 free list 不足时发生部分分配", "分别耗尽 int/fp/vec/v0/vl 物理寄存器并混合多类型目的操作", f"各 free list 的 `doAllocate/canAllocate` 联锁保证原子接受，不能只推进部分列表；证据 {link('backend/rename/Rename.scala','152-165')}", "Multi-resource allocation checker；full/almost-full cross"),
            ("`I_WRAP_PTR`", "free-list head/archHead 环绕后空满或年龄错误", "连续分配、提交释放直到指针跨最大值，再重复一次", f"value/flag 同步回绕，物理寄存器不重复分配；证据 {link('backend/rename/freelist/BaseFreeList.scala','49-80')}", "Pointer-age checker；allocation uniqueness scoreboard"),
            ("`C_ALLOC_FREE_SAME_CYCLE`", "同拍 allocate/free/walk 导致计数错误", "让 rename 分配与 commit 释放、RAB walk 同拍发生", f"head/tail、可分配数和返回寄存器集合与代码优先级一致；证据 {link('backend/rename/freelist/BaseFreeList.scala','63-83')}", "Occupancy checker；simultaneous alloc/free cross"),
            ("`C_SAME_ENTRY_RW`", "映射表同拍读写同一逻辑寄存器缺少正确旁路", "前后 lane 连续写读同一 `ldest`，并叠加提交写回", f"年轻指令读取最近的推测映射，恢复映射不被错误覆盖；证据 {link('backend/rename/RenameTable.scala','60-150')}", "Rename-map scoreboard；RAW/WAW cross"),
            ("`RENAME_DUP_LDEST`", "同组多个目的寄存器相同导致旧映射/释放错误", "多 lane 产生相同逻辑目的寄存器，穿插源依赖", "lane 间旁路按程序序更新，旧 pdest 只回收一次，新 pdest 均唯一", "Map/free-list scoreboard；duplicate-dest cover"),
            ("`F_REQ_AND_FLUSH`", "redirect 与 rename 接受/快照恢复竞争", "`io.out.fire` 同拍触发 redirect，覆盖不同 snapshot 选择", f"映射表和所有 free list 恢复到同一检查点；证据 {link('backend/rename/freelist/BaseFreeList.scala','71-82')}", "Snapshot recovery checker；cross-list consistency"),
            ("`P_DEADLOCK_ALL_STALL`", "free list、snapshot-full 与 dispatch 反压形成闭环", "同时制造资源耗尽和下游阻塞，再依次释放 commit/dispatch", "旧指令最终 rename，所有列表可 drain，无物理寄存器泄漏", "Forward-progress checker；leak/double-allocation checker"),
        ]
    if name == "MoveElimination":
        return [
            ("`ME_LEGALITY`", "非纯 move 被错误消除", "覆盖整型 move、带异常/触发器/单步、非 move ALU 指令", f"只有代码定义的 `isMove` 且满足条件者共享源 pdest；证据 {link('backend/rename/freelist/MEFreeList.scala','45-66')}", "Move legality checker；opcode/exception cross"),
            ("`ME_REF_INC_DEC`", "共享物理寄存器引用计数溢出、下溢或提前释放", "多级 move 链后以不同提交/flush 顺序释放映射", f"refCounter 每次建立映射只加一次、映射消亡只减一次，归零才回 free list；证据 {link('backend/rename/freelist/MEFreeList.scala','59-86')}", "Reference-count scoreboard；overflow/underflow assertions"),
            ("`C_MULTI_WRITE_SAME_ENTRY`", "同拍多个 move 引用同一源 pdest", "多个 lane 从同一源物理寄存器产生 move", "合并后的引用增量等于有效 move 数，不丢增量、不重复释放", "Multi-update counter checker；same-pdest cross"),
            ("`F_REQ_AND_FLUSH`", "move 消除映射与 redirect 回滚竞争", "消除 move 被接受同拍或随后触发 snapshot redirect", f"推测引用和映射完整撤销，head 恢复与 refCounter 延迟关系一致；证据 {link('backend/rename/freelist/MEFreeList.scala','77-100')}", "Snapshot/refcount recovery checker"),
            ("`ME_NO_EXEC`", "已消除 move 仍进入执行或重复完成", "建立可消除 move，并对执行队列施加不同反压", f"dispatch 保留 `eliminatedMove` 元数据且不产生普通执行副作用；证据 {link('backend/dispatch/NewDispatch.scala','720-740')}", "Dispatch/ROB lifecycle scoreboard"),
            ("`ME_COMMIT_VISIBILITY`", "消除后提交、异常和 difftest 可见性不一致", "move 链跨提交边界并插入异常、redirect", f"ROB 只完成一次，架构映射和调试可见结果正确；证据 {link('backend/rob/Rob.scala','1506-1537')}", "Commit/difftest scoreboard"),
            ("`PB_RECOVERY_THROUGHPUT`", "高比例 move 或 refCounter 压力导致永久停顿", "长 move 链填充共享引用后提交并注入 redirect", "引用最终归零并回收，rename/dispatch 吞吐恢复", "Forward-progress/performance checker"),
        ]
    if name == "Dispatch":
        return [
            ("`RESOURCE_CONTENTION`", "ROB、LSQ、各 Dispatch Queue 资源联锁错误", "分别及同时填满 ROB、LSQ、整数/浮点/访存队列", f"只有全部必需资源允许的 lane 才 fire，不能部分丢失；证据 {link('backend/dispatch/NewDispatch.scala','784-835')}", "Multi-sink handshake checker；resource cross"),
            ("`DISPATCH_PREFIX_ORDER`", "部分宽度分发越过更老阻塞 lane", "阻塞中间 lane 的目标队列，保持年轻 lane 可接收", f"`notBlockedByPrevious` 保证程序序前缀接受；证据 {link('backend/dispatch/NewDispatch.scala','810-825')}", "Oldest-prefix checker；lane-mask coverage"),
            ("`C_BANK_CONFLICT`", "多个 uop 竞争同一队列端口/执行类别", "同拍构造多个相同 fuType 且端口不足的 uop", "grant one-hot，失败候选保持并在资源释放后重试", "Arbiter checker；port-conflict/fairness cross"),
            ("`F_HOLD_BACKPRESSURE`", "任一 sink 不 ready 时 rename payload 漂移", "保持 `fromRename.valid` 并持续改变其他资源 ready", "未 fire lane 的 uop、pdest、srcState、ROB/LSQ 请求保持一致", "Handshake checker；no-loss/no-duplicate scoreboard"),
            ("`F_REQ_AND_FLUSH`", "redirect 与 ROB/LSQ/DQ enqueue 同拍", "所有 enqueue valid 时注入 redirect", "错误路径不进入任何 sink，或按代码接受后被唯一 kill，不能残留半提交状态", "Flush checker；cross-sink transaction scoreboard"),
            ("`DISPATCH_SPECIAL`", "异常、单步和 eliminated move 的路由/阻塞不一致", "将三类特殊 uop 与普通 uop 混合在同一组", f"特殊属性传播及序列化条件正确；证据 {link('backend/dispatch/NewDispatch.scala','720-740')}", "Special-uop routing checker"),
            ("`P_DEADLOCK_ALL_STALL`", "多 sink ready 反馈形成死锁或饥饿", "填满所有 sink 后逐一释放单个队列", "最老请求最终跨过 dispatch，各 sink drain，吞吐恢复", "Forward-progress/performance checker"),
        ]
    if name == "MDP":
        return [
            ("`H_SAME_INDEX_DIFF_TAG`", "不同 PC 的 SSIT/hash alias 形成错误依赖", "构造 load/store PC 映射同 index、不同 tag/上下文", f"alias 行为只产生可恢复的保守等待，不能破坏表端口；证据 {link('mem/mdp/StoreSet.scala','280-320')}", "Index/hash checker；false-positive/negative cross"),
            ("`C_SAME_ENTRY_RW`", "SSIT 查询与 violation 训练同拍同 entry", "dispatch lookup 同拍提交 store-load violation update", f"读旧/读新/更新优先级与源码一致；证据 {link('mem/mdp/StoreSet.scala','300-320')}", "Storage conflict checker；training scoreboard"),
            ("`MDP_SET_MERGE`", "两个 store set 合并丢失成员或产生环形依赖", "让已属不同 set 的 load/store 重复违例", "SSIT 统一到合法 set id，后续 lookup 得到一致依赖", "Store-set scoreboard；merge coverage"),
            ("`RESOURCE_CONTENTION`", "LFST 有效项/分配槽耗尽仍覆盖活跃 store", "填满 LFST 后持续 dispatch 新 store", f"分配、valid、latest-store 指针和 full 行为一致；证据 {link('mem/mdp/StoreSet.scala','328-390')}", "Occupancy/pointer checker；full/almost-full cover"),
            ("`I_WRAP_PTR`", "LFST 环形 store 指针回绕破坏新旧关系", "推进 store SQ/ROB 标识跨最大值并查询依赖", "回绕后只等待真实未完成的最新 store，无 stale dependency", "Pointer-age checker；wrap cross"),
            ("`F_REQ_AND_FLUSH`", "redirect 后 LFST/WaitTable 保留错误路径依赖", "训练或 dispatch store/load 同拍 redirect，随后复用相同 PC", f"错误路径状态被清除或不可见；WaitTable 更新与查询符合源码；证据 {link('mem/mdp/WaitTable.scala','25-71')}", "Flush/replay checker；stale-dependency scoreboard"),
            ("`P_LIVELOCK_REPLAY_LOOP`", "重复 violation/等待预测导致 replay 活锁", "同一 load-store 对连续违例并周期性释放 store", "训练最终稳定且 load 可完成，不形成永久不必要串行化", "Forward-progress checker；violation/replay-rate cover"),
            ("`PB_RECOVERY_THROUGHPUT`", "过度保守预测长期降低内存并行度", "训练热点后切换到无冲突访存阶段", "陈旧依赖逐步消退，load 吞吐恢复并记录假阳性率", "Performance checker；serialization latency"),
        ]
    if name == "RegCache":
        return [
            ("`C_MULTI_WRITE_SAME_ENTRY`", "多个写端口同拍覆盖同一 RegCache entry", "强制两个 write port 选择相同 slot", f"触发源码断言或唯一合法优先级，tag/data 不分裂；证据 {link('backend/regcache/RegCacheDataModule.scala','45-75')}", "Multi-write assertion；tag/data scoreboard"),
            ("`REGCACHE_INVALID_READ`", "读取 invalid/stale slot 造成错误操作数", "tag miss、替换取消和 load cancel 后立即读取旧 slot", f"invalid 读取被断言/屏蔽，不能伪造 hit；证据 {link('backend/regcache/RegCacheDataModule.scala','45-65')}", "Valid-bit checker；stale-slot reuse cover"),
            ("`REGCACHE_TAG_DATA_ALIGN`", "流水化写索引导致 tag 与 data 落入不同 entry", "连续三拍写不同 pdest/slot，并插入 cancel/replace", f"写 tag、写 data 与延迟后的 index 对齐；证据 {link('backend/regcache/RegCache.scala','65-120')}", "Pipeline metadata scoreboard"),
            ("`C_SAME_ENTRY_RW`", "同拍读写/替换同一 slot 的旁路与命中错误", "read、write、replace 同时指向同 entry", f"tag 命中、取消和替换优先级符合源码；证据 {link('backend/regcache/RegCacheTagModule.scala','50-95')}", "Storage conflict checker；RAW/replace cross"),
            ("`H_SAME_INDEX_DIFF_TAG`", "不同 pdest 竞争或重复占用 slot", "制造相同低位索引不同 tag，并允许多个 slot 出现同 tag", f"TagTable 的 hit vector、写入和无效化保持 one-hot/可解释；证据 {link('backend/regcache/RegCacheTagTable.scala','55-105')}", "Tag uniqueness checker；multi-hit cover"),
            ("`REGCACHE_AGE_ORDER`", "年龄矩阵不传递或替换项不唯一", "读写多个 entry、让计时器饱和并跨组比较", f"年龄更新优先级和 replacement one-hot 断言成立；证据 {link('backend/regcache/RegCacheAgeTimer.scala','51-98')}、{link('backend/regcache/AgeDetector.scala','63-70')}", "Age-order checker；rank uniqueness cover"),
            ("`RESOURCE_CONTENTION`", "所有 slot/读写端口繁忙时覆盖活跃值", "填满有效 entry 并持续产生多读多写", "替换只选代码给出的最老/无效项，阻塞不造成架构错误", "Occupancy/arbiter checker；port-pressure cross"),
            ("`PB_RECOVERY_THROUGHPUT`", "低命中率或端口冲突导致旁路网络长期拥塞", "冷热 pdest 阶段切换并注入持续写回", "miss/stale hit 不破坏正确性，命中率和端口压力恢复", "Performance checker；hit-rate/stall coverage"),
        ]
    return [
        ("`F_HOLD_BACKPRESSURE`", "跨模块 ready/valid 反压", "阻塞一个下游接口", "payload 稳定，反压边界符合代码", "Handshake checker"),
        ("`C_REDIRECT_REDIRECT`", "多个恢复源竞争", "重叠 BPU/IFU/backend redirect", "唯一恢复目标和状态", "Redirect checker"),
        ("`RESOURCE_CONTENTION`", "有限队列/表/cache 资源耗尽", "填满相关结构", "full/ready/替换行为正确", "Occupancy checker"),
        ("`I_WRAP_PTR`", "环形指针回绕", "推进所有相关指针跨最大值", "年龄和空满正确", "Pointer-age checker"),
        ("`P_DEADLOCK_ALL_STALL`", "全链阻塞", "阻塞后逐步释放", "最终 drain", "Forward-progress checker"),
        ("`PB_RECOVERY_THROUGHPUT`", "恢复后吞吐", "饱和流中 flush/redirect", "无陈旧可见且吞吐恢复", "Performance checker"),
    ]


def classify(filename: str) -> str:
    explicit = {
        "1.译码.md": "Decode",
        "2.寄存器重命名.md": "Rename",
        "3.Move指令消除.md": "MoveElimination",
        "4.分发阶段.md": "Dispatch",
        "Mem-MDP.md": "MDP",
        "RegCache.md": "RegCache",
    }
    if filename in explicit:
        return explicit[filename]
    stem = filename.removeprefix("Frontend-").removesuffix(".md")
    if stem.startswith("IFU"):
        return "IFU"
    if stem.startswith("FTQ"):
        return "FTQ"
    if stem.startswith("IBuffer"):
        return "IBuffer"
    if stem.startswith("ICache"):
        return "ICache"
    if stem.startswith("总览"):
        return "Overview"
    return stem


def update(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    marker = "\n## 验证特别注意\n"
    if marker in text:
        text = text.split(marker, 1)[0].rstrip() + "\n"
    section = table(rows_for(classify(path.name)))
    path.write_text(text.rstrip() + "\n\n" + section, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    parser.add_argument("--glob", default="*.md")
    args = parser.parse_args()
    paths = sorted(args.directory.glob(args.glob))
    if not paths:
        raise SystemExit("no matching documents")
    for path in paths:
        update(path)
        print(path)


if __name__ == "__main__":
    main()
