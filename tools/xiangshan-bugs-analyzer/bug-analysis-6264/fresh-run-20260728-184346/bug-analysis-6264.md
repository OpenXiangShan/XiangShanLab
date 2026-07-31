# XiangShan Issue #6264 — Sv39 规范地址边界后的顺序取指未触发 IPF

## 结论

在 `kunminghu-v2` 的 `7be121c71ff0534982ee0521e0b7fe8f2605a67c` 上，顺序取指从最后一个 Sv39 规范虚拟地址缓存行跨入 `0x0000004000000000` 时，前端没有执行规范地址检查。`IPrefetch` 将该非规范地址发送给 ITLB；ITLB 按 VPN 位进行页表遍历，命中测试布置的 `root[256]` 映射，并返回 `0x0000000081200020` 一带的别名物理地址。最终 XiangShan 提交了 PC `0x0000004000000000` 的 alias stub 指令，而 NEMU 在同一 PC 正确报告 instruction page fault（`mcause=12`、`mepc=mtval=0x0000004000000000`）。

根因是规范地址检查只接在 backend redirect target 上，未覆盖 FTQ/IPrefetch 生成的 `nextlineStart` 顺序地址。

## 本次从零复现环境

- 新运行目录：`bug-analysis-6264/fresh-run-20260728-184346`
- `xs-env`：本次重新克隆并递归初始化全部子模块。
- RTL：`XiangShan` commit `7be121c71ff0534982ee0521e0b7fe8f2605a67c`。
- 仿真器：本次执行 `make emu EMU_TRACE=fst -j12` 构建。
- 兼容性处理：当前 GNU `sync` 不接受重复的 `-d`；仅将本次 clone 的 `difftest/verilator.mk` 中 `sync -d <dir1> -d <dir2>` 改为等价的 `sync -d <dir1> <dir2>`。未修改 RTL。
- 测试程序：从 issue #6264 附件重新下载 `main.c`，在本次 clone 的 `nexus-am/apps/bug-canonical-fetch-fallthrough` 中重新编译：
  - `make ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1`
  - 镜像：`xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough/build/PROG.bin`
  - SHA-256：`2c0b63a33f1b52a5195001e1878f11795917247724cd5410f96c6de126b5bb71`

仿真命令：

```bash
./build/emu \
  --diff ready-to-run/riscv64-nemu-interpreter-so \
  --dump-wave-full \
  --max-instr 50000 \
  -i ../nexus-am/apps/bug-canonical-fetch-fallthrough/build/PROG.bin
```

## 新生成产物

- 仿真日志：`repro.log`
- FST：`xs-env/XiangShan/build/2026-07-29-08-38-57.fst`（200 MiB）
- 波形头：`fst-header.vcd`
- 取指/ITLB 精简时间线：`prefetch-itlb-trace.vcdlog`
- 时钟时间线：`clock-trace.vcdlog`
- 反汇编：`xs-env/nexus-am/apps/bug-canonical-fetch-fallthrough/build/PROG.disasm`

## 可复现失配

仿真自然结束于 difftest abort；没有人工终止。

- 日志确认运行的 core SHA 为 `7be121c71f`。
- commit trace 的最后一条目标指令：

```text
commit pc 0000004000000000 inst 820102b7 wen 1 dst 05 data ffffffff82010000
```

- difftest 在 PC `0x0080000fbe` 报告：

```text
mepc different: right = 0x0000004000000000, wrong = 0x00000000800001cc
mtval different: right = 0x0000004000000000, wrong = 0x0000000000000000
mcause different: right = 0x000000000000000c, wrong = 0x0000000000000000
```

- 仿真统计：`instrCnt = 6836`，`cycleCnt = 23537`。

## 波形时间线

FST 的 time scale 是 `1 ps`，时钟每 `2 ps` 一个周期；下列 cycle 是 FST 从 `#0` 起的上升沿编号。

| 时间 / cycle | 本次 FST 观察 | 含义 |
| --- | --- | --- |
| `#46796 ps` / 上升沿 #23399 | `prefetcher.io_req_valid=1`；port0 `startAddr=0x0000003fffffffc0`；port1 `nextlineStart=0x0000004000000000` | 合法 `jr 0x3fffffffc0` 到达末尾 fetch block，双线顺序取指构造出非规范下一行。 |
| `#46796 ps` | `io_itlb_0_req_bits_vaddr=0x0000003fffffffc0`，`io_itlb_1_req_bits_vaddr=0x0000004000000000` | 非规范地址被真实送入 ITLB，而非被转为 instruction page fault。 |
| `#46798 ps` 至 `#47080 ps` | port1 ITLB miss 后完成遍历；`io_itlb_1_resp_bits_paddr_0=0x0000000081200020` | `root[256]` 别名映射被使用，物理地址落在测试布置的 alias stub 页面。 |
| 整个 FST | `io_itlb_0_resp_bits_excp_0_pf_instr`（`Zt@`）和 `io_itlb_1_resp_bits_excp_0_pf_instr`（`dt@`）仅在 `#0` 为 `0`，从未变为 `1` | ITLB/前端没有产生 instruction page fault。 |
| 提交阶段 | commit trace 提交 `pc=0x4000000000` 的 `lui t0, 0x82010` | 错误地址不仅被请求，而且已经执行并到达架构提交。 |

## 源码因果链

### 直接跳转目标检查正确

`src/main/scala/xiangshan/Bundle.scala:698` 的 `AddrTransType.checkPageFault` 对 Sv39 检查 `target[XLEN-1:39]` 是否全部等于 bit 38。`src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala:44` 至 `:52` 将跳转目标传给该函数并写入 `redirect.cfiUpdate.backendIPF`。

本测试的直接目标 `0x0000003fffffffc0` 仍是 Sv39 规范地址，所以这一路检查返回 false；它不是触发点。

### 顺序下一行绕过 backend redirect 检查

`src/main/scala/xiangshan/frontend/icache/IPrefetch.scala:102` 将请求的 `startAddr` 和 `nextlineStart` 组成两个 `s0_req_vaddr`。在 `:173` 至 `:179`，每个端口的地址直接写进 `toITLB(i).bits.vaddr`。因此，波形中的 `nextlineStart=0x4000000000` 会作为 port1 的 ITLB VA 发出。

`IPrefetchReq` 只携带外部传入的 `backendException`（`IPrefetch.scala:31` 至 `:37`）。`IPrefetch.scala:227` 至 `:233` 也说明高位规范性检查“在 backend 完成，并随 redirect 传回 frontend”。这只覆盖 redirect；顺序 `nextlineStart` 没有新的 redirect，所以 `backendException` 仍为 none。

### 缺失异常被正常送入后级

`IPrefetch.scala:369` 至 `:379` 仅把合并后的 `s1_itlb_exception` 写入 WayLookup。由于 port1 ITLB 响应也没有 `pf_instr`，`s1_itlb_exception` 为 none，后续 ICache/IFU 将 alias PTE 翻译结果当成正常取指。波形中 ITLB 返回 `0x81200020`，随后 commit trace 中出现 `0x4000000000` 的 alias stub，形成端到端证据。

## 根因

`AddrTransType.checkPageFault` 的 Sv39 规范性判定存在且用于 backend redirect，但前端的双线/顺序取指路径没有对每个待送入 ITLB 的 VA 执行等价判定。设计把“高位检查由 backend redirect 注入异常”作为前提；该前提对 `jr`、branch 和 CSR redirect 成立，却不覆盖 `nextlineStart` 自动跨一条 fetch block 的场景。

因此 `0x3fffffffc0 + fetch_block_size = 0x4000000000` 逃过检查，ITLB 用其 VPN bits 访问 `root[256]` 并翻译到 alias PTE，而不是抛出 instruction page fault。

## 修复建议

1. 在进入 ITLB 前，对 `IPrefetch` 每个有效 fetch VA（至少 `startAddr` 和有效 `nextlineStart`）执行与 `AddrTransType.checkPageFault` 一致的 Sv39/Sv48/Sv39x4/Sv48x4 规则。
2. 将命中的结果合成为端口级 instruction page-fault exception，阻止该端口进行正常 ITLB walk / ICache lookup，并通过 `s1_itlb_exception`、WayLookup、IFU、FTQ 和 trap 路径传递。
3. 保留触发异常的完整虚拟 PC，保证 `mepc` 和 `mtval` 都为 `0x0000004000000000`，而不是只修复 fault bit。
4. 回归覆盖 Sv39 两端边界、顺序单双线、ICache miss/resend、`jr`/branch/trap-return/顺序 fall-through，以及 Sv39x4/Sv48/Sv48x4 的正确 IPF/IGPF 分类。

## 结论置信度

高。新生成 FST 明确显示非规范 VA 的 ITLB 请求、ITLB 对 alias 物理页的成功响应、两个 `pf_instr` 均未断言；同一次仿真的 commit/difftest 日志又证明该 VA 的指令已提交，而参考模型要求 IPF。源码中 backend-only canonical check 与 IPrefetch 的无检查直通路径同这些波形事实一致。
