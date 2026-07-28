# Issue #6264：昆明湖 V2 Sv48 非规范顺序取指未产生 ITLB 指令页故障

## 1. 方法与结论摘要

本分析使用 `/home/yanyusong/wavekit` 开源仓库中的 `wavekit.FstReader` 解析 FST，使用 `TOP.clock` 上升沿进行 clock-sampled 查询；没有重新运行仿真，也不分析性能 bubble。波形时间单位为 2，因而相邻 `TOP.clock` 上升沿间隔为 2 个仿真时间单位。

| 项目 | 结果 |
|---|---|
| 核心/版本 | 用户提供的香山昆明湖 V2 源码：`/home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan` |
| 波形 | `/home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/build/2026-07-27-11-52-30.fst` |
| 时钟 | `TOP.clock`，posedge |
| 触发点 | `jr 0x00007fffffffffc0`，随后顺序 fall-through 到 `0x0000800000000000` |
| 期望 | Sv48 非规范 VA 应产生 instruction page fault，`mcause=12`，`mtval=mepc=0x800000000000` |
| 实际 | ITLB/PTW 将该 VA 翻译到 PA `0x81200000`，没有 `pf_instr`，并提交 alias 页中的 `lui t0, 0x82020` |
| 结论 | 根因在前端 ITLB 的高位地址检查缺失：顺序取指走普通 ITLB/PTW 翻译，未经过 backend redirect 的 `checkPageFault()`；而当前 TLB 的高位截断预检查又明确只给 LSU 产生 load/store fault，不给 instruction fault。 |

**一句话定位：** `0x7fffffffffc0` 跨越 Sv48 canonical boundary 后，FTQ/IFU 产生的下一 fetch PC `0x800000000000` 进入 ITLB；ITLB 只使用 VPN `0x800000000` 查页表，命中测试专门放在 `root[256]` 下的 PTE，于是把一个本应先判定为非规范地址的 PC 当作普通可翻译 VA，最终执行了 PA `0x81200000` 的 alias stub。

## 2. 测试程序与反汇编依据

测试程序在 [`main.c`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/main.c ) 中已经把 bug 条件构造得很直接：

- `root[255] -> HI_L2 -> HI_L1[0x1ff]` 将 `0x00007fff_ffff_f000..ffff` 映射到 PA `0x811ff000..ffff`。
- `root[256] -> NC_L2 -> NC_L1[0]` 将非规范 VA `0x00008000_0000_0000` 映射到 PA `0x81200000`。
- `0x811fffc0` 起填充 NOP；PA `0x81200000` 写入 `lui/slli/srli/addi/sw/ecall` alias stub。
- S-mode 入口 `f7_s_entry` 在 [`main.c:85-110`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/main.c#L85 ) 执行：

```asm
f7_s_entry:
  li   t0, 0x00007FFFFFFFFFC0
  jr   t0
```

- 页表和 alias 代码见 [`main.c:140-200`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/main.c#L140 )。
- 测试在 [`main.c:224-231`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/main.c#L224 ) 以 `flag == 0xfa` 判定 bug 复现。

反汇编 [`bug-canonical-fetch-fallthrough-sv48-riscv64-xs.txt`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/build/bug-canonical-fetch-fallthrough-sv48-riscv64-xs.txt ) 与上述构造一致：

| PC | 指令 | 作用 |
|---|---|---|
| `0x800001cc` | `f7_s_entry` | `li t0, 0x7fffffffffc0; jr t0` |
| `0x7fffffffffc0` | `nop` | 高端 canonical 页中的 NOP |
| `0x7fffffffffd8`、`0x7fffffffffe0` 等 | `nop` | 顺序执行至页尾 |
| `0x800000000000` | `0x820202b7` | alias PA `0x81200000` 的 `lui t0,0x82020` |
| `0x800000000004` | `0x02029293` | `slli t0,t0,32` |
| `0x800000000008` | `0x0202d293` | `srli t0,t0,32` |
| `0x80000000000c` | `0x0fa00313` | `addi t1,x0,0xfa` |
| `0x800000000010` | `0x0062a023` | 写 `FLAG_PA`，置 `flag=0xfa` |
| `0x800000000014` | `0x00000073` | `ecall` |

日志也直接证明了错误发生在 alias 指令被提交之后：

```text
commit pc 0000800000000000 inst 820202b7 ...
mcause: 0x000000000000000c mepc: 0x0000800000000000 mtval: 0x0000800000000000
BUG REPRODUCED: fetch crossed canonical boundary without fault
```

参考模型在 `pc=0x008000105c` 报告 `t0/mode/mstatus/mepc/mtval/mcause` 差异；其中参考状态为 `mcause=12`，而错误模型已经执行了 alias stub，说明不是 trap handler 本身的问题，而是 trap 之前前端错误地接受了非规范 fetch PC。

## 3. FST 全局时间线

以下周期均为从 FST `TOP.clock` posedge 数出的绝对 cycle；仿真时间为对应采样时间。

| cycle | time | 事件 | 关键波形证据 |
|---:|---:|---|---|
| 31387 | 62774 | 高端 canonical 页的第一条 ITLB 请求仍在 miss | `prefetcher.io_itlb_0_req_bits_vaddr=0x7fffffffffc0`，`miss=1`，`pf_instr=0` |
| 31388 | 62776 | `0x7fffffffffe0` 翻译成功 | PA 为 `0x811fffc0`，`miss=0`，无异常 |
| 31390 | 62780 | 顺序 fetch PC 进入 `0x800000000000` | `inner_itlb.io_requestor_0_req_bits_vaddr=0x800000000000`，VPN 为 `0x800000000` |
| 31391–31482 | 62782–62964 | 非规范 PC 在 ITLB 中等待 PTW | 请求 valid 持续；`miss=1`、`pf_instr=0`、`af_instr=0` |
| 31482 | 62964 | PTW 返回 root[256] 路径的有效 leaf | `io_ptw_resp_valid=1`，`entry_ppn=0x10240`，`level=1`，`pf=0`，`af=0` |
| 31483 | 62966 | ITLB 输出错误翻译 | PA `0x81200000`，`miss=0`，`pf_instr=0`，`af_instr=0` |
| 31485 | 62970 | ICache/Prefetcher 继续请求 alias 页下一 cache line | 请求 VA `0x800000000020`，仍无异常 |
| 31486 | 62972 | alias 页继续取指 | PA `0x81200020`，无异常 |
| 31529 | 63058 | 错误 alias 指令提交 | `difftest_commit_0_pc=0x800000000000`，`instr=0x820202b7` |
| 31529 | 63058 | 预期的 backend IPF redirect 缺失 | frontend/backend redirect 的 `backendIPF` 路径未被激活；alias 指令直接进入提交序列 |

### 3.1 ITLB/PTW 关键信号

波形层级如下：

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core.frontend.inner_itlb
TOP.SimTop.cpu.l_soc.core_with_l2.core.frontend.inner_icache.prefetcher
TOP.SimTop.cpu.l_soc.core_with_l2.core.frontend.io_backend_toFtq_redirect_*
```

在 cycle 31390 起，ITLB request port 0 的 `vaddr` 是 `0x800000000000`。该地址的 Sv48 VPN 是 `0x800000000`，其最高一级索引为 256，正好选择测试程序构造的 `root[256]`。因此这是一个真正走 PTW 的顺序 fetch，不是显示值被截断后仍代表 `0x00007fff...` 的问题。

cycle 31482 PTW response 的关键值：

```text
frontend.inner_itlb.io_ptw_resp_valid                         = 1
frontend.inner_itlb.io_ptw_resp_bits_s1_entry_ppn             = 0x10240
frontend.inner_itlb.io_ptw_resp_bits_s1_entry_level           = 1
frontend.inner_itlb.io_ptw_resp_bits_s1_pf                    = 0
frontend.inner_itlb.io_ptw_resp_bits_s1_af                    = 0
frontend.inner_itlb.io_ptw_resp_bits_s1_entry_v                = 1
frontend.inner_itlb.io_ptw_resp_bits_s1_entry_perm_x          = 1
```

ITLB 随后按 `PPN << 12 | page offset` 形成 PA `0x81200000`。这与测试程序 `nc_l1[0] = (0x81200000 >> 2) | 0xcf` 完全吻合，故可以排除 PTW 读错页表、PTE 无效或 ICache 数据错误。

### 3.2 Prefetcher 状态和异常传播

在 `frontend.inner_icache.prefetcher` 中，波形观察到：

| cycle | `state` | `next_state` | `s1_wait_itlb_0` | `s1_need_itlb_0` | `itlb_finish` | `s1_itlb_exception_0` |
|---:|---:|---:|---:|---:|---:|---:|
| 31387 | 1 | 1 | 1 | 1 | 0 | 0 |
| 31388 | 1 | 3 | 1 | 0 | 1 | 0 |
| 31389 | 3 | 0 | 0 | 0 | 1 | 0 |
| 31391 | 1 | 1 | 0 | 0 | 0 | 0 |
| 31482 | 1 | 1 | 0 | 0 | 0 | 0 |
| 31483 | 1 | 3 | 0 | 0 | 1 | 0 |
| 31484 | 3 | 0 | 0 | 0 | 1 | 0 |
| 31485 | 0 | 4 | 0 | 0 | 1 | 0 |
| 31486 | 4 | 0 | 0 | 0 | 1 | 0 |

状态值按源码 [`IPrefetch.scala:137-144`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala#L137 ) 对应：`0=m_idle`、`1=m_itlbResend`、`2=m_metaResend`、`3=m_enqWay`、`4=m_enterS2`。这里的状态机只是等待 ITLB miss 完成后重新使用翻译结果；由于 ITLB response 的异常位为 0，所以 `s1_itlb_exception_0` 一直为 0，Prefetcher 正常进入下一阶段。

## 4. 根因代码链

### 4.1 当前设计把 canonical 检查放在 backend redirect

香山的地址转换类型在 [`Bundle.scala:688-700`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/Bundle.scala#L688 ) 定义。Sv48 的规范地址判断本身是存在的：

```scala
class AddrTransType(implicit p: Parameters) extends XSBundle {
  val bare, sv39, sv39x4, sv48, sv48x4 = Bool()

  def checkPageFault(target: UInt): Bool =
    sv39 && target(XLEN - 1, 39) =/= VecInit.fill(XLEN - 39)(target(38)).asUInt ||
    sv48 && target(XLEN - 1, 48) =/= VecInit.fill(XLEN - 48)(target(47)).asUInt
}
```

但该函数在普通控制流目标上是由 backend redirect generator 调用的，见 [`RedirectGenerator.scala:34-41`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala#L34 )：

```scala
oldestExuRedirect.bits.fullTarget :=
  Cat(io.oldestExuRedirect.bits.fullTarget.head(XLEN - VAddrBits),
      io.oldestExuRedirect.bits.cfiUpdate.target)
when(!io.oldestExuRedirectIsCSR){
  oldestExuRedirect.bits.cfiUpdate.backendIPF :=
    io.instrAddrTransType.checkPageFault(oldestExuRedirect.bits.fullTarget)
}
```

这条路径只覆盖后端产生的 redirect target，例如 `jr/jalr/branch/mret` 的 target。测试真正触发 bug 的 `0x800000000000` 是从 `0x7fffffffffe0` 顺序增加出来的 next-line/fall-through PC，不是后端 redirect target。因此 `RedirectGenerator` 没有机会为它设置 `backendIPF`。

### 4.2 ITLB 普通翻译路径只检查 PTE 和权限

ITLB 使用 `TlbCmd.exec` 请求，波形对应的 request 由 [`IPrefetch.scala:151-183`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala#L151 ) 发出：

```scala
private val s1_need_itlb = VecInit(Seq(
  (RegNext(s0_fire) || s1_wait_itlb(0)) && fromITLB(0).bits.miss,
  (RegNext(s0_fire) || s1_wait_itlb(1)) && fromITLB(1).bits.miss && s1_doubleline
))

toITLB(i).valid             := s1_need_itlb(i) || ...
toITLB(i).bits.vaddr        := Mux(s1_need_itlb(i), s1_req_vaddr(i), s0_req_vaddr(i))
toITLB(i).bits.cmd          := TlbCmd.exec
toITLB(i).bits.no_translate := false.B
```

TLB 的正常 miss/translation 逻辑在 [`TLB.scala:331-364`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L331 )：

```scala
val hit  = e_hit || p_hit
val miss = (!hit && enable) || ...
resp(i).bits.miss := miss
...
val paddr = Cat(ppn(d), get_off(req_out(i).vaddr))
resp(i).bits.paddr(d) := Mux(enable, paddr, notTranslatePaddr)
```

这里的 `paddr` 只拼接 PTW/TLB 返回的 PPN 和原请求的 page offset；没有对 `req_out(i).vaddr` 的 `[63:48]` 是否等于 bit 47 的 sign extension 做检查。于是 VPN `0x800000000` 可以被当成普通 VPN 送给 PTW。

TLB 的 PTW 请求也直接从请求 VA 取 VPN，见 [`TLB.scala:541-562`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L541 )：

```scala
when (req_out_v(idx) && missVec(idx)) {
  miss_req_v := true.B
}
io.ptw.req(idx).bits.vpn := get_pn(req_out(idx).vaddr)
```

### 4.3 现有高位地址预检查明确排除了 instruction fault

最关键的代码在 [`TLB.scala:467-498`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L467 )。当 `prepf || pregpf || preaf` 成立时，代码明确把 instruction fault 置为 false：

```scala
when (RegNext(prepf || pregpf || preaf)) {
  resp(idx).bits.excp(nDups).pf.ld    := RegNext(prepf) && isLd
  resp(idx).bits.excp(nDups).pf.st    := RegNext(prepf) && isSt
  resp(idx).bits.excp(nDups).pf.instr := false.B

  resp(idx).bits.excp(nDups).gpf.ld    := RegNext(pregpf) && isLd
  resp(idx).bits.excp(nDups).gpf.st    := RegNext(pregpf) && isSt
  resp(idx).bits.excp(nDups).gpf.instr := false.B

  resp(idx).bits.excp(nDups).af.ld    := RegNext(preaf) && TlbCmd.isRead(cmd)
  resp(idx).bits.excp(nDups).af.st    := RegNext(preaf) && TlbCmd.isWrite(cmd)
  resp(idx).bits.excp(nDups).af.instr := false.B

  resp(idx).bits.miss := false.B
}
```

这段实现本身体现了设计意图：高位截断检查当时是为 LSU 的地址扩展/截断异常准备的，不把它用于 ITLB instruction fetch。普通 TLB 权限 fault 的 instruction 分支虽然存在于 [`TLB.scala:435-499`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L435 )，例如 `instrPf` 和 `pf.instr`，但它们只检查 PTE 的 `X/V/A/U/R/W` 权限；它们不会把非规范 VA 本身判成 fault。

因此当前设计存在两个互补缺口：

1. 顺序 fall-through fetch 没有经过 backend `checkPageFault()`。
2. ITLB/TLB 对 instruction request 没有在翻译前执行 Sv48 canonical-address check；已有的 high-address pre-check 又明确不产生 `pf.instr`。

### 4.4 前端异常传播链没有丢失异常，而是从源头得到 0

Prefetcher 从 ITLB response 形成异常值，见 [`IPrefetch.scala:212-233`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala#L212 )：

```scala
private val s1_itlb_exception_tmp = VecInit((0 until PortNumber).map { i =>
  ResultHoldBypass(
    valid = tlb_valid_pulse(i),
    init  = ExceptionType.none,
    data  = ExceptionType.fromTlbResp(fromITLB(i).bits)
  )
})
```

随后 ICache 将 ITLB/PMP 异常送给 IFU，见 [`ICacheMainPipe.scala:548-570`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala#L548 )：

```scala
private val s2_exception_out = ExceptionType.merge(
  s2_exception,
  s2_l2_exception
)

toIFU.bits.exception(i) := Mux(
  needThisLine, s2_exception_out(i), ExceptionType.none
)
```

波形中 cycle 31483/31484 的 `s1_itlb_exception_0` 和后续 `s2_itlb_exception_0` 都为 0，故这里没有“异常在 ICache 被吞掉”的证据；异常是在 ITLB response 产生之前就已经为 0。

## 5. Redirect / trap 对照

### 5.1 发生的 `jr` redirect 与未发生的 fall-through fault

`jr` 本身的后端逻辑会计算 redirect target，并在 target 不匹配或有 backend fault 时发 redirect，见 [`JumpUnit.scala:36-51`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala#L36 )。这解释了为什么进入 `0x7fffffffffc0` 的跳转可以正常工作。

但跨页之后的 `0x800000000000` 没有对应的 backend redirect producer。frontend 对 backend redirect 中的 `backendIPF` 只做“把异常标记到 redirect target 后的第一条指令”，见 [`NewFtq.scala:557-584`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/NewFtq.scala#L557 )：

```scala
backendException := ExceptionType.fromOH(
  has_pf  = fromBackendRedirect.bits.cfiUpdate.backendIPF,
  has_gpf = fromBackendRedirect.bits.cfiUpdate.backendIGPF,
  has_af  = fromBackendRedirect.bits.cfiUpdate.backendIAF
)
```

该机制不能覆盖自然顺序 fetch 的 canonical boundary crossing。

### 5.2 没有发生 ITLB IPF

波形事实：

- `inner_itlb.io_requestor_0_resp_bits_excp_0_pf_instr=0`；
- `inner_itlb.io_requestor_0_resp_bits_excp_0_af_instr=0`；
- `inner_icache.prefetcher.s1_itlb_exception_0=0`；
- `inner_icache.prefetcher.s2_itlb_exception_0=0`；
- ITLB response `miss` 从 1 变为 0 后，PA 变成 `0x81200000`；
- 之后 `0x800000000000` 的 `0x820202b7` 在 cycle 31529 提交。

所以“没有 trap”不是因为 trap redirect 竞争、ROB flush、或 exception commit 时机错误，而是因为前端没有生成 `instrPageFault`，程序被当作合法指令流继续取指。

## 6. 诊断与修复建议

### 6.1 推荐修复位置

推荐在 ITLB instruction translation 的共同入口增加 Sv39/Sv48 canonical check，而不是只在某个 ICache/FTQ 特殊分支补丁。逻辑应在发起 PTW 或接受 TLB hit/PTW response 前执行：

```scala
val isInst = TlbCmd.isExec(req_out(i).cmd)
val canonicalFault = MuxLookup(translationMode, false.B, Seq(
  Sv39 -> (vaddr(XLEN - 1, 39) =/= Fill(XLEN - 39, vaddr(38))),
  Sv48 -> (vaddr(XLEN - 1, 48) =/= Fill(XLEN - 48, vaddr(47)))
))
val instrPf = isInst && canonicalFault
```

对于 ITLB，建议在 PTW request 发出之前将此类请求转换为非 miss 的 `pf_instr` response，避免对明显非法 VA 进行页表行走；如果实现上必须等待当前流水级，至少必须在 PTW response 被消费前阻止 PA 形成和 ICache fetch。

### 6.2 需要保持的语义

- 只对 instruction fetch 设置 `pf.instr`，不要复用当前 `prepf` 分支中明确为 `false.B` 的 `pf.instr` 赋值而造成 load/store 语义污染。
- `mtval`/`mepc` 应保持 faulting fetch PC `0x800000000000`。
- 不应把该检查仅放在 `RedirectGenerator`，否则仍会漏掉所有顺序跨 canonical boundary 的 fetch。
- 在 ITLB hit、PTW response、以及 ITLB refill 后重放三条路径上使用同一个 canonical 判定，避免 miss 首次和 refill replay 行为不同。
- 对 Sv39、Sv48、以及对应 guest translation 模式分别确认 fault 类型；本 issue 的直接目标是 S-mode Sv48 的 `instruction page fault`。

### 6.3 建议增加的回归断言

针对任意 `TlbCmd.exec` 请求，在 translation mode 为 Sv48 时增加断言：

```scala
when (req_valid && sv48 && !canonical(vaddr)) {
  assert(resp.valid)
  assert(!resp.bits.miss)
  assert(resp.bits.excp(0).pf.instr)
}
```

并保留本测试的页表布局，使 `root[256]` 仍然包含一个有效、可执行 leaf；这样可以确保测试检查的是“canonical check 缺失”，而不是“无效 PTE 自然产生 page fault”。

## 7. 源码链接与引用文件

- [`main.c`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/main.c )：页表、NOP sled、alias stub、S-mode 跳转与判定逻辑。
- [`main.c:85-110`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/main.c#L85 )：`f7_s_entry` 与 trap resume 代码。
- [`main.c:140-200`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough-sv48-riscv64-xs/main.c#L140 )：Sv48 页表和 alias PA 内容。
- [`TLB.scala:331-364`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L331 )：TLB hit/miss、PPN 与 PA 拼接。
- [`TLB.scala:423-498`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L423 )：PTE 权限 fault 与 high-address pre-check，其中 instruction fault 被置 false。
- [`TLB.scala:541-562`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/cache/mmu/TLB.scala#L541 )：PTW request 使用 VPN。
- [`Bundle.scala:688-700`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/Bundle.scala#L688 )：Sv39/Sv48 canonical `checkPageFault()`。
- [`RedirectGenerator.scala:34-41`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala#L34 )：backend redirect target 的 IPF 生成。
- [`IPrefetch.scala:137-183`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala#L137 )：ITLB miss 重发、request VA 与 `TlbCmd.exec`。
- [`IPrefetch.scala:212-233`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala#L212 )：ITLB response 到前端异常的转换。
- [`ICacheMainPipe.scala:548-570`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala#L548 )：ITLB/PMP 异常到 IFU 的传播。
- [`NewFtq.scala:557-584`]( /home/yanyusong/xiangshan-bug-analysis/xs-issues-6264/xs-env/XiangShan/src/main/scala/xiangshan/frontend/NewFtq.scala#L557 )：backend redirect fault 标记到 FTQ 第一条指令。

## 8. 最终结论

Issue #6264 的直接 bug 位置在**前端 ITLB 对 instruction fetch VA 缺少 Sv48 canonical-address 检查**，并由当前架构把 canonical 检查主要放在 backend redirect target 的设计共同造成。`0x800000000000` 被 ITLB 当作正常 VPN `0x800000000` 去 PTW，命中测试专门布置的 `root[256]` 映射，得到 PA `0x81200000`，从而执行 alias stub 并写入 `flag=0xfa`。

这不是页表构造错误、PTE 权限错误、PTW 读错、ICache 数据错、trap handler 错，也不是 backend commit 错。修复应让**所有 instruction translation 请求**在 ITLB/PTW 之前或共同 response 生成点执行 Sv48 canonical 检查，并生成 `pf_instr`；仅修复 backend redirect 路径不能覆盖本复现。
