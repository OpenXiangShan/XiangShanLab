### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug


We found that the bug class reported in #6126 ("HLV.WU ignores SPVP=VU effective privilege for final PMP checks", filed against kunminghu-v3) also exists on **NEMU** reference model, so difftest stays silent.

Concretely, the reproducer executes in M-mode with `hstatus.SPVP = 0` (so the explicit memory access of `HLV.WU` must use VU effective privilege), `vsatp = 0` and `hgatp = 0` (Bare), with:

- `pmpaddr0/pmpcfg0` entry0: **unlocked** NAPOT, no permissions (R=W=X=0), covering a 4 KiB data page at `0x80100000`
- entry1: NAPOT RWX allow-all over the full address space (fallback)

Observed behavior:

```
[setup] pmpaddr0 = 0x200401ff, pmpaddr1 = 0x3fffffffffff, pmpcfg0 = 0x1f18
        entry0 = 0x18 (NAPOT deny, unlocked), entry1 = 0x1f (NAPOT RWX)
[control b: M-mode lw] val = 0x5ec3e7, trap_count = 0 -> OK (M bypass, expected)
[test: hlv.wu SPVP=VU] val = 0x5ec3e7, trap_count = 0, mcause = 0, mtval = 0x0
[setup] pmpcfg0 = 0x1f98 (entry0 locked deny)
[src/memory/paddr.c:240,check_paddr] isa pmp check failed, vaddr=0x0000000080100000, paddr=0x0000000080100000, len=0x4, type=0x1, mode=0x3
[control a: hlv.wu, entry0 locked] val = 0x2e, trap_count = 1, mcause = 5 -> OK (locked entry applies to M-mode too)
BUG REPRODUCED: HLV.WU ignored SPVP=VU for PMP check
Core 0: HIT BAD TRAP at pc = 0x800003c2
```

### Expected behavior

`HLV.WU` with `hstatus.SPVP = 0` targeting a page covered by an unlocked PMP no-access entry raises a load access fault: `mcause = 5`, `mtval` = faulting address. The data must not be returned.

### Environment

- XiangShan branch: kunminghu-v2
- XiangShan commit id: 7be121c71f (difftest run, see `difftest.log` banner `Core 0's Commit SHA is: 7be121c71f`)
- NEMU commit id: `ready-to-run/riscv64-nemu-interpreter-so` used as difftest reference (also observed with the standalone `riscv64-nemu-interpreter` build)

### To Reproduce

Workload: `bug-6126-hlv-spvp-pmp-riscv64-xs.bin`, a bare-metal AM program.

Run with difftest:

```bash
./build/emu -i bug-6126-hlv-spvp-pmp-riscv64-xs.bin --diff ready-to-run/riscv64-nemu-interpreter-so
```

[hlv-spvp-variant.zip](https://github.com/user-attachments/files/30211972/hlv-spvp-variant.zip) (PoC and log included)

### Additional context

*Relation to #6126*: issue #6126 reports the same bug class ("HLV.WU ignores SPVP=VU effective privilege for final PMP checks") against kunminghu-v3. This issue confirms that **NEMU is affected as well**.

1. **The NEMU reference model shares the same bug**, so difftest cannot flag this behavior: in the run above, DUT and reference both return the protected word without trapping. Quick look at the NEMU side (needs confirmation by the NEMU maintainers): address translation for `hld_st` does use `hstatus->spvp` (`src/isa/riscv64/system/mmu.c:226,419`), but the final PMP permission check `isa_pmp_check_permission()` (`src/isa/riscv64/system/mmu.c`, ~line 1115) derives the check mode only from `cpu.mode`/`mstatus.MPRV`/`mstatus.MPP` and never from `hstatus.SPVP`, which matches the `mode=0x3` seen in the log.
