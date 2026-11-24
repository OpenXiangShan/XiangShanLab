### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug


An address-specific `hfence.gvma` with a matching VMID invalidates the G-stage leaf and superpage caches, but not the non-leaf page-table cache. A later walk still follows the old next-level pointer.

OnlyStage2, Sv39x4, VMID=1, in `gvma_addr_sel.S`:

1. Walk a guest-physical address once. The load returns `0x1111...`.
2. Patch that leaf in place and execute an address-specific `hfence.gvma` (`rs1` = GPA>>2, `rs2` = the matching VMID). Both XiangShan and NEMU then read `0x2222...`. The leaf path is fine.
3. Point the root non-leaf at a different table whose data is `0x3333...`, and execute the same address-specific fence again.

NEMU reads `0x3333...`. XiangShan still reads `0x2222...`. The L1 TLB is invalidated by VMID and refills, but the refill walks through the stale non-leaf: REF PPN `0x8000e`, DUT PPN `0x8000d`, vpn `0x8000c`, `s2xlate=2`.

```
s2 different at pc = 0x0080000112, right = 0x3333333333333333, wrong = 0x2222222222222222
Core 0: ABORT at pc = 0x80000112
Core-0 instrCnt = 147, cycleCnt = 5,024
Seed=0
```

`--no-diff` prints `HIT GOOD TRAP at pc = 0x8000027c`. That is not a pass: the program does not check `s2`. Difftest is required. Seed 0 reproduces the same mismatch as the original seed 1 runs.

This is not the separate case where NEMU flushes the whole table on a wrong VMID and the DUT keeps the old entry. That case is not reported as a DUT bug.

### Expected behavior

After `hfence.gvma` with a matching VMID, every G-stage translation cache that can still be used for that GPA must be invalid, including non-leaf entries that point at the old next level. A wider flush is allowed. Leaving a matching non-leaf entry is not.

The same image under difftest should reach `HIT GOOD TRAP` with no `s2 different`.

A hypervisor that switches a second-stage table with an address-specific fence otherwise keeps using the old physical page. The failure is a silent stale translation, not a spurious fault.

### Environment

- Published Hunt Verilator emu and NEMU, not rebuilt for this report. emu sha256 `9f402402f5f2e8d15d1ef730650c9437d02a712940f23409b4454b991bfb7c55`, NEMU sha256 `848dc936d074b78675628cd08a4d12daee3ba326706eb55b13ed83521f0b5b85`.
- Workload toolchain: riscv64-unknown-linux-gnu-gcc (GCC) 12.0.1 20220505 (prerelease), `-march=rv64gcv -mabi=lp64d`.
- Image `case.bin` sha256 `205f2281afde4a5ac57cf71ccba27104254b94702de77ebc2f0fc35e7e144341`.
- Source `gvma_addr_sel.S` sha256 `fb903147df373c7ae8d402e5a7a2bd8aca0c3faa0ec70b944d8f06508bda4f3e`.
- Farm evidence: run `run-20260923-163852-a147a6`, finding `bug-21f1e39be3`, key `rtl-ptw-hfence-gvma-nonleaf-stale`.
- Reconfirmed 2026-10-08 with `-s 0 --diff`.


### To Reproduce

riscv64-unknown-linux-gnu-gcc -nostdlib -nostartfiles -static -fno-pic -fno-pie -no-pie \
  -mcmodel=medany -march=rv64gcv -mabi=lp64d -Wl,--build-id=none \
  -T link.ld gvma_addr_sel.S -o case.elf
riscv64-unknown-linux-gnu-objcopy -O binary case.elf case.bin

./build/emu -b 0 -e 0 -s 0 -i case.bin --max-instr=200000 --diff=<nemu>
```

Do not judge this case with `--no-diff`. A failing difftest aborts at `pc = 0x80000112` with `s2` right = `0x3333...`, wrong = `0x2222...`.

### Additional context

`rs2=x0` (all VMIDs) and an address-specific fence after an in-place leaf update already match NEMU. Those are not this bug.

[xs-rt-017-hlv.zip](https://github.com/user-attachments/files/33189607/xs-rt-017-hlv.zip)

Possible cause
Reading of `f86f50b834` `cache/mmu/PageTableCache.scala`, not confirmed by editing the RTL:

- When `rs1=x0`, the onlyStage2 path clears L0/L1/L2/SP by VMID, or clears everything.
- When `rs1` is a specific address, only `l0v` and `spv` are cleared. `l1v` and `l2v` are not updated in that branch.

The L1 TLB refill after the fence therefore hits the old L1/L2 non-leaf and walks into the old leaf (`0x2222...`) instead of the table installed by the new root pointer (`0x3333...`).
