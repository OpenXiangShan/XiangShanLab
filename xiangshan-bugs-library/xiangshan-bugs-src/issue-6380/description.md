### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

When M-mode injects a supervisor external interrupt with `mideleg[9]=1`,
`mvien[9]=0`, `mvip[9]=1` (no SEI pending from APLIC/IMSIC), the injection
itself works: `sip.SEIP` reads 1 and `stopi` reports the SEI. However, the
injected SEI can never be cleared:

- M-mode writes `mvip = 0`; `mvip` reads back 0, but `sip.SEIP` still reads 1
  and `stopi` still reports the SEI.

Architectural consequence (demonstrated by the attached livelock test): with
`sie.SEIE=1` and `sstatus.SIE=1`, the SEI traps to S-mode; the handler clears
`mvip.SEIP` and executes `sret`; since `sip.SEIP` is still 1, the hart
immediately re-enters the handler, forever. The interrupted code makes zero
forward progress (livelock) — the run only stops at a host-side timeout:

    [H1] handler entry #1: cause=0x8000000000000009 sip(before clear)=0x200
    [H1]   M wrote mvip=0: mvip_rb=0x0 sip(after clear)=0x200
    [H1] handler entry #2: ... (identical, forever; main loop never advances)

Minimal CSR-sequence reproduction on kunminghu-v2 HEAD `e12436c7c`:

    [SEQ] after inject: stopi=0x900ff sip=0x200
    [SEQ] after clear mvip: mvip_rb=0x0
    [SEQ] after clear: stopi=0x900ff sip=0x200      <- sip.SEIP stuck at 1

(`stopi=0x900ff` decodes to IID=9, IPRIO=0xff; the IPRIO value reflects the
injected-SEI priority-number-256 fix from #6010 present on this commit. This
report is only about the clear path.)

Full logs are attached (logs/*.log.gz, gunzip to view).


### Expected behavior

Per *The RISC-V Advanced Interrupt Architecture*, Version 1.0, Revised
20250312, §5.3:

> "When bit 9 of mvien is zero, bit 9 of mvip is an alias of the
> software-writable bit 9 of mip (SEIP). ... for most purposes, the value of
> bit 9 of mvip is logically ORed into the readable value of mip.SEIP."

and Table 11: with `mideleg[9]=1`, `sip[9]` is an alias of `mip[9]`.

Per *The RISC-V Instruction Set Manual, Volume II: Privileged Architecture*
(mip.SEIP):

> "SEIP is writable in mip ... Supervisor-level external interrupts are made
> pending based on the logical-OR of the software-writable SEIP bit and the
> signal from the external interrupt controller."

Therefore, writing `mvip.SEIP=0` from M-mode must clear the software-writable
SEIP bit. With no external interrupt controller asserting SEI, the logical-OR
result — and hence `mip.SEIP`, its alias `sip.SEIP`, and `stopi` — must read 0.
Actual behavior: `sip.SEIP` and `stopi` stay non-zero — an injected SEI is
un-clearable and, once interrupts are enabled, livelocks the hart in the
S-mode handler.

(For completeness: the base privileged architecture states that "SEIP is
read-only in sip", so we do **not** claim that S-mode writes to `sip.SEIP`
must work; the clearing obligation above rests entirely on the M-mode
software-writable SEIP bit.)

### Environment

- Hardware
  - CPU: AMD Ryzen 9 5950X 16-Core Processor
  - Memory (GB): 125
  - Storage (GB): 900 (NFS home)
- Software
  - Operating system: Ubuntu 22.04.3 LTS (kernel 6.8.0-111-generic)
  - gcc version: gcc (Ubuntu 11.4.0-1ubuntu1~22.04.3) 11.4.0
  - clang version: Ubuntu clang version 14.0.0-1ubuntu1.1
  - java version: openjdk version "11.0.31" 2026-04-21
  - mill version: Mill Build Tool version 0.12.3
- Repo
  - XiangShan commit id: `e12436c7cba86b195deec24981976d78bc263661` (kunminghu-v2 HEAD as of 2026-08-18)
  - NEMU commit id: N/A (difftest not used; runs with `--no-diff`)
  - SPIKE commit id: N/A
- Build & Run
  - Build command: `make emu CONFIG=KunminghuV2Config`
  - Run command: `./emu -i repro-c010.bin --no-diff`
  - Workload (source + binaries) attached in "To Reproduce".

Note: `scripts/bug-report.sh` does not exist on kunminghu-v2 (checked at
`<repo>/scripts/bug-report.sh`), so the fields above were collected manually.


### To Reproduce

1. Unpack the attached `r2ev-seip-clear-issue.tar.gz`.
2. Minimal CSR sequence: `./emu -i repro/repro-c010.bin --no-diff`
   -> prints the [SEQ] lines above: `mvip` reads back 0 after the clear, but
      `sip` stays `0x200` and `stopi` stays `0x900ff`.
3. Livelock demo: `timeout 60 ./emu -i h1-livelock/livelock-h1.bin --no-diff`
   -> the S-mode handler is re-entered forever (see Describe the bug); the
      interrupted main loop never prints a single "alive" tick; the run is
      stopped by the wall-clock timeout (no GOOD/BAD TRAP).
4. Boundary probe: `./emu -i h2v2-sip-write/livelock-h2v2.bin --no-diff`
   -> trap-free; S-mode executes `csrc sip, 0x200` / `csrw sip, 0`, and `sip`
      still reads `0x200` — the S-side alias write path is broken as well.

All sources (nexus-am apps, ARCH=riscv64-xs), prebuilt binaries, and full emu
logs are included in the archive; see README.md inside.

[r2ev-seip-clear-issue.tar.gz](https://github.com/user-attachments/files/31169437/r2ev-seip-clear-issue.tar.gz)

### Additional context

- The `mvip` register itself updates correctly (it reads back 0 after the
  clear), so the suspicion is in the `mvip.SEIP -> mip.SEIP -> sip.SEIP` alias
  propagation (NewCSR, `MachineLevel.scala`): the pending value observed via
  `mip`/`sip` and by the interrupt filter is not cleared.
- Also reproduced on an older kunminghu-v2 commit `be3e376` (2026-06-09),
  where `stopi` read `0x90000` (pre-#6010 IPRIO encoding).
- Not verified on kunminghu-v3
- Related but distinct items checked before reporting: #6001 / PR #6010
  (injected-SEI priority number 256 — fixed and confirmed present on this
  commit), PR #5974 (mip.SEIP alias write gating), #3856 / PR #3868 (2024,
  mip.SEIP alias handling).
- AI assistance disclosure: this report was drafted with AI assistance
  (pi coding agent, model: kimi-coding/k3) within a specification-driven
  testing flow against the fixed AIA 1.0 (Revised 20250312) document; all
  experiments were executed by us on the commits stated above, and the
  content has been reviewed and verified by the submitter.
