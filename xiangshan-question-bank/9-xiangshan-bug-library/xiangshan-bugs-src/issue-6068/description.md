### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

This is a follow-up to #6032. I understand the previous issue was closed because "While WFI is in flight, hvictl cannot be written, so virtual interrupts cannot be injected" — thank you for the clarification. However, I still have some questions regarding a slightly different scenario that I think remains problematic: if hvictl is written before the WFI instruction is executed, should WFI not wake immediately per spec? I have now prepared a PoC and verified the behavior against Spike, and would appreciate your review.

spec (WFI):
> execution must resume from a WFI whenever an interrupt is pending at any privilege level ... if the H extension is implemented, an interrupt is pending at VS level if vstopi is not zero.

XiangShan's WFI wake-up condition only inspects (mie & mip):

`io.status.wfiEvent := debugIntr || (mie.rdata.asUInt & mip.rdata.asUInt).orR || nmip.asUInt.orR`

The hvictl.VTI=1 direct-injection path sets vstopi without touching mip. When this path is the only pending source, (mie & mip) == 0 and wfiEvent never fires.

### Expected behavior

WFI should wake up when vstopi ≠ 0, regardless of mip/mie state. Spike does this correctly.

### Environment

- Repo
  - XiangShan commit id: `master (commit ad21e8099e)`
  - SPIKE commit id (if difftest failed with SPIKE): `master`


### To Reproduce

[wfi.gz](https://github.com/user-attachments/files/28692236/wfi.gz)

### results
XiangShan emu (KunminghuV2Config, no-diff):

> [A] mip path: WFI resumed in 20 cycles
> [B] hvictl.VTI path: WFI resumed in 1048591 cycles (vstopi=0x00100002)
> BUG: vstopi pending but WFI stalled 1048591 cycles to hardware timeout
> Core 0: HIT GOOD TRAP at pc = 0x800002ac
> Case A wakes in 20 cycles (correct). Case B stalls 1,048,591 cycles (~2^20, wfi_cycles timeout) despite vstopi being non-zero.

Spike:

> Case A: mcycle before=0x7b, after=0x7d  → delta = 2 cycles (woke promptly)
> Case B: mcycle before=0x585, after=0x587 → delta = 2 cycles (woke promptly)
>          vstopi = 0x00100002 (IID=16 pending)
> Spike wakes WFI in 2 cycles for both cases — spec-correct behavior.

### Additional context

_No response_
