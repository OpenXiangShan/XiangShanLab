### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A store to DRAM after that 4 KiB page is marked non-cacheable in the PMA CSRs is sent to the memory NCB as a CHI Device transaction. The NCB rejects Device, and the simulator aborts. The access never completes.

`uncache_pma.S`, M-mode, `satp=0`:

1. Dirty `0x80100000` with a cacheable store.
2. Program PMA: `pmaaddr0` (`0x7c8`) = `0x200401FF` (`(0x80100000 >> 2)` with the low 9 bits set, 4 KiB NAPOT), `pmacfg0` (`0x7c0`) = `0x1b` (R|W|A_NAPOT, `c=0`).
3. `sd` to `0(s0)` on that address.

```
Core 0: ABORT at pc = 0x800000cc
Core-0 instrCnt = 50, cycleCnt = 4,423
Seed=0
Assertion failed: illegal MemAttr asserted: Device
```

`--no-diff` and `--diff` die at the same instruction, before a register mismatch. Seed 0 matches the original seed 1 run (same PC, instruction count, and cycle count). This is not an NEMU disagreement.

### Expected behavior

A PMA non-cacheable load or store to a main-memory address (`>= 0x80000000`) must complete. It must not allocate or hit the old cache line, and its side effect must happen once, in program order. The CHI attribute should be Normal Non-cacheable, not Device.

The same image should pass its checks and print `HIT GOOD TRAP`, with no `illegal MemAttr asserted: Device`.

If the assertion is stripped for synthesis, the memory port still receives a Device transaction that this channel refuses, so the non-cacheable access still cannot complete. This is a simulation-CI abort, not a security or performance bug. Whether the old dirty line is written back, or a speculative non-cacheable access leaks, is not shown here: the simulator dies on the first non-cacheable access.

### Environment

- Published Hunt Verilator emu, not rebuilt for this report. Compiled 2026-09-24 02:43:15. emu sha256 `9f402402f5f2e8d15d1ef730650c9437d02a712940f23409b4454b991bfb7c55`.
- Workload toolchain: riscv64-unknown-linux-gnu-gcc (GCC) 12.0.1 20220505 (prerelease), `-march=rv64gcv -mabi=lp64d`.
- Image `case.bin` sha256 `643c4ca2146c9b04fddf7c83aff1184e9bf5330bde94eca2d0af891ec34f8129`.
- Source `uncache_pma.S` sha256 `f45e5d5b74ab079cff9a76fba3524f8a20898af58a6eb54c6c3e9505a5330610`.
- Farm evidence: run `run-20260923-163852-a147a6`, finding `bug-6f4731a497`, key `rtl-uncache-device-on-dram`.
- Reconfirmed 2026-10-08 with `-s 0 --no-diff`.


### To Reproduce

```
riscv64-unknown-linux-gnu-gcc -nostdlib -nostartfiles -static -fno-pic -fno-pie -no-pie \
  -mcmodel=medany -march=rv64gcv -mabi=lp64d -Wl,--build-id=none \
  -T link.ld uncache_pma.S -o case.elf
riscv64-unknown-linux-gnu-objcopy -O binary case.elf case.bin

./build/emu -b 0 -e 0 -s 0 -i case.bin --max-instr=200000 --no-diff
```

`case.bin` in this folder is the image that produced the log above. A failing run aborts at `pc = 0x800000cc` with `illegal MemAttr asserted: Device`.

### Additional context

### Possible cause

Reading of `f86f50b834`, not confirmed by editing the RTL. `c=0` is treated as MMIO all the way to the memory port:

- `backend/fu/PMA.scala`: `resp.mmio := !cfg.c`, so `c=0` is marked MMIO.
- `mem/pipeline/LoadUnit.scala` / `StoreUnit.scala`: `memBackTypeMM = !pmp.mmio`.
- `coupledL2/tl2chi/MMIOBridge.scala`: `device = !isBackTypeMM`, which encodes CHI Device. The comment in that file says PMA=MM and PBMT=NC should be Non-cacheable, not Device.
- `top/Top.scala`: only `AddressSet(0x0, 0x7fffffff)` goes to the MMIO OpenNCB. `0x80100000` goes to the LLC. Only the MMIO OpenNCB sets `acceptMemAttrDevice = true`.
- `openNCB/NCBUpstreamRXREQ.scala`: Device with `acceptMemAttrDevice=0` asserts `illegal MemAttr asserted`.

A main-memory address with PMA `c=0` should leave the memory NCB as Normal Non-cacheable (`device=0`). Device should stay on the low-2 GiB MMIO port. Widening `acceptMemAttrDevice` on the memory NCB, or clearing `memBackTypeMM` whenever `c=0`, does not match that split.

[xs-rt-020-pma.zip](https://github.com/user-attachments/files/33190071/xs-rt-020-pma.zip)
