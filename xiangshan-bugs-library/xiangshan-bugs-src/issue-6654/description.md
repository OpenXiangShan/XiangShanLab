### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

`XSCoreParameters.ISAExtensions` declares `zama16b`, added by 50cdcfc2 ([#6463](https://github.com/OpenXiangShan/XiangShan/pull/6463), `config(isa): align isa extensions with implementation`), and the device tree passes that list to software as what the core implements. With Zama16b, an AMO whose bytes all lie inside one naturally aligned 16-byte granule in main memory must not raise an exception for reasons of address alignment. `AtomicsUnit` still raises a store/AMO address-misaligned exception for every AMO that is not naturally aligned to its own size, and ends the instruction without performing it.

This is not a request to support misaligned AMOs. [#4667](https://github.com/OpenXiangShan/XiangShan/issues/4667) reported the same exception before the declaration existed and was closed as not planned. This report is about the declaration and the behaviour disagreeing since #6463.

On the assembled core, six such AMOs (word and doubleword, at offsets 1 to 11 inside their 16-byte granules) each trap with `mcause` 6 (store/AMO address-misaligned) and `mtval` equal to the address. Memory and `rd` are left unchanged. The program is under "To Reproduce"; its result lines:

Seed 1, without difftest:

| # | Instruction | Address | Byte offsets from the 16-byte boundary | Required | Observed |
|---|---|---|---|---|---|
| 0 | `ecall` | – | – | exception, `mcause` 11 (environment call) | `mcause` 11, `mtval` = 0; memory and `rd` unchanged |
| 1 | `amoadd.w` | `0x80004024` | 4–7 | completes (naturally aligned) | completes; `rd` = `0xffffffffbd9e7f60`, memory updated as the specification computes |
| 2 | `amoadd.d` | `0x80004048` | 8–15 | completes (naturally aligned) | completes; `rd` = `0xf2d3b49576573819`, memory updated as the specification computes |
| 3 | `lr.w` | `0x80004061` | 1–4 | exception, `mcause` 4 or 5 (misaligned LR) | `mcause` 4, `mtval` = `0x80004061`; memory and `rd` unchanged |
| 4 | `amoadd.w` | `0x80004081` | 1–4 | completes (Zama16b) | `mcause` 6, `mtval` = `0x80004081`; memory and `rd` unchanged |
| 5 | `amoswap.w` | `0x800040a6` | 6–9 | completes (Zama16b) | `mcause` 6, `mtval` = `0x800040a6`; memory and `rd` unchanged |
| 6 | `amomaxu.w` | `0x800040cb` | 11–14 | completes (Zama16b) | `mcause` 6, `mtval` = `0x800040cb`; memory and `rd` unchanged |
| 7 | `amoadd.d` | `0x800040e1` | 1–8 | completes (Zama16b) | `mcause` 6, `mtval` = `0x800040e1`; memory and `rd` unchanged |
| 8 | `amoor.d` | `0x80004102` | 2–9 | completes (Zama16b) | `mcause` 6, `mtval` = `0x80004102`; memory and `rd` unchanged |
| 9 | `amoxor.d` | `0x80004127` | 7–14 | completes (Zama16b) | `mcause` 6, `mtval` = `0x80004127`; memory and `rd` unchanged |
| 10 | `amoadd.w` | `0x8000414e` | 14–17 (crosses) | exception (crosses a 16-byte boundary) | `mcause` 6, `mtval` = `0x8000414e`; memory and `rd` unchanged |
| 11 | `amoadd.d` | `0x80004169` | 9–16 (crosses) | exception (crosses a 16-byte boundary) | `mcause` 6, `mtval` = `0x80004169`; memory and `rd` unchanged |

Rows 4 to 9 are the AMOs Zama16b requires to complete. Rows 0 to 3 are controls whose outcome does not depend on Zama16b, and rows 10 and 11 cross a 16-byte boundary. The runs with seed 2 and with difftest printed the same twelve lines.

The declaration:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/Parameters.scala#L304-L317
  def ISAExtensions = Seq(
    // single letter extensions, in canonical order
    "i", "m", "a", "f", "d", "c", "b", "v", "h",
    // multi-letter extensions, sorted alphanumerically
    "sdext", "sdtrig", "sha", "shcounterenw", "shgatpa", "shlcofideleg", "shtvala", "shvsatpa", "shvstvala",
    "shvstvecd", "smaia", "smcdeleg", "smcntrpmf", "smcsrind", "smdbltrp", "smmpm", "smnpm", "smrnmi", "smstateen",
    "ss1p13", "ssaia", "ssccfg", "ssccptr", "sscofpmf", "sscounterenw", "sscsrind", "ssdbltrp", "ssnpm",
    "sspm", "ssstateen", "ssstrict", "sstc", "sstvala", "sstvecd", "ssu64xl", "supm", "sv39",
    "sv48", "svade", "svbare", "svinval", "svnapot", "svpbmt", "za64rs", "zabha", "zacas", "zama16b", "zawrs", "zba",
    "zbb", "zbc", "zbkb", "zbkc", "zbkx", "zbs", "zcb", "zcmop", "zfa", "zfh", "zfhmin", "zic64b",
    "zicbom", "zicbop", "zicboz", "ziccamoa", "ziccamoc", "ziccif", "zicclsm", "ziccrse", "zicntr", "zicond",
    "zicsr", "zifencei", "zihintntl", "zihintpause", "zihpm", "zimop", "zkn", "zknd", "zkne", "zknh",
    "zksed", "zksh", "zkt", "zvbb", "zvfh", "zvfhmin", "zvkt", "zvl128b", "zvl32b", "zvl64b"
  )
```

This list is what the generated device tree tells software the core implements:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/XSDts.scala#L34-L35
      "riscv,isa-base" -> ISABase.asProperty,
      "riscv,isa-extensions" -> ISAExtensions.map(ResourceString),
```

`AtomicsUnit` checks only natural alignment to the operand size. The check does not consider a 16-byte granule, so an AMO fails it even when all its bytes lie inside one:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala#L261-L269
      val addrAligned = LookupTree(amoSize, List(
        LSUOpType.AMOSize.B.U -> true.B,
        LSUOpType.AMOSize.H.U -> (vaddr(0) === 0.U),
        LSUOpType.AMOSize.W.U -> (vaddr(1,0) === 0.U),
        LSUOpType.AMOSize.D.U -> (vaddr(2,0) === 0.U),
        LSUOpType.AMOSize.Q.U -> (vaddr(3,0) === 0.U)
      ))
      exceptionVec(loadAddrMisaligned)  := !addrAligned && isLr
      exceptionVec(storeAddrMisaligned) := !addrAligned && !isLr
```

A misaligned AMO then goes straight to `s_finish`, before the PMA stage and before any DCache request:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala#L280-L290
      when (!io.dtlb.resp.bits.miss) {
        when (!addrAligned || triggerDebugMode || triggerBreakpoint) {
          // NOTE: when addrAligned or trigger fire, do not need to wait tlb actually
          // check for miss aligned exceptions, tlb exception are checked next cycle for timing
          // if there are exceptions, no need to execute it
          state := s_finish
          out_valid := true.B
          atom_override_xtval := true.B
        }.otherwise {
          state := s_pm
        }
```

Every address in the program is inside main memory, which the SoC's default parameters place at:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/system/SoC.scala#L58
  PmemRanges: Seq[MemoryRange] = Seq(MemoryRange(0x80000000L, 0x80000000000L)),
```

The two naturally aligned AMOs in the same program complete in main memory with the values the specification gives, so the region does perform AMOs.

### Expected behavior

The privileged specification, in its section on the misaligned atomicity granule PMA:

> The Zama16b extension requires MAG16 support to main memory regions.
>
> — [machine.adoc, lines 3117-3118](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/priv/machine.adoc#L3117-L3118)

MAG16 means that the misaligned atomicity granule, a naturally aligned power-of-two number of bytes, is at least 16 bytes ([machine.adoc, lines 3086-3089](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/priv/machine.adoc#L3086-L3089)). The Zaamo chapter, for the set of instructions the granule applies to, which includes AMOs:

> For an instruction in that set, if all accessed bytes lie within the same misaligned atomicity granule, the instruction will not raise an exception for reasons of address alignment, and the instruction will give rise to only one memory operation for the purposes of RVWMO--i.e., it will execute atomically.
>
> — [zaamo.adoc, lines 34-37](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/unpriv/zaamo.adoc#L34-L37)

So with `zama16b` declared, each of the six AMOs in the table above should complete, update memory and write `rd`. Two neighbouring behaviours are already correct and should stay as they are.

- An AMO that crosses a 16-byte boundary still raises an exception ([machine.adoc, lines 3101-3103](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/priv/machine.adoc#L3101-L3103)). The two crossing AMOs in the program do trap.
- LR/SC are outside the granule and always raise an exception when misaligned ([machine.adoc, lines 3111-3112](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/priv/machine.adoc#L3111-L3112)). The misaligned `lr.w` in the program traps with `mcause` 4.

Either of two changes makes the declaration and the behaviour agree.

1. **Implement MAG16 for AMOs.** An AMO whose bytes all lie inside one naturally aligned 16-byte granule then completes atomically instead of raising an exception.
2. **Stop declaring `zama16b`.** Without a misaligned atomicity granule, the Zaamo chapter permits exactly what the core does today:

> For AMOs, the Zaamo extension requires that the address held in _rs1_ be naturally aligned to the size of the operand (i.e., eight-byte aligned for _doublewords_ and four-byte aligned for _words_). If the address is not naturally aligned, an address-misaligned exception or an access-fault exception will be generated. The access-fault exception can be generated for a memory access that would otherwise be able to complete except for the misalignment, if the misaligned access should not be emulated.
>
> — [zaamo.adoc, lines 16-23](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/unpriv/zaamo.adoc#L16-L23)

The contributor comments listed under "Additional context" describe the exception as intended, which points towards the second change. The second change also leaves RVA23U64's mandatory set untouched: the profile lists Zama16b among its development options, "new development options intended to become mandatory in a future RVA profile" ([rva23.adoc, lines 111-130](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/profiles/rva23.adoc#L111-L130)). Which change is intended is for the maintainers to decide.

If misaligned AMOs are planned but not ready, the second change still applies until they are. #6463 describes the list as aligned with the implementation, and the device tree hands that list to software as a statement of what the core does now.

### Environment

- Hardware
  - CPU: `AMD EPYC 9534 64-Core Processor`.
  - Memory (GB): 16, with 2 cores.
- Software
  - Operating system: `Ubuntu 24.04.4 LTS`, in the container image the emulator ran in, sha256 `d6bda86973a86ad94c251c088f6c09cc033413aabd74d9d7bc8e434853fee2cc`.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`, which built the emulator.
  - java version: 17.0.19 (Temurin).
  - mill version: 0.12.17, with `firtool` 1.149.0 and `Verilator 5.048 2026-04-26 rev conda-forge build 0`.
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`, the head of `kunminghu-v3`, with no local change. The emulator prints `Core  0's Commit SHA is: 09453ff8f4, dirty: 0`.
  - NEMU commit id (if difftest failed with NEMU): difftest did not fail. The `--diff` run used `ready-to-run/riscv64-nemu-interpreter-so`, sha256 `9188e7dda30709962f0bfbc1dd87cea73d88a18c92c30892f93ed529c22def43`, from `ready-to-run` at `4cf9983569432035d41956c65e6a58724b5954be`, whose commit message names NEMU `513786c4c597dd628c6126144937372d6f7fc92e`. The `difftest` submodule is at `4c64fe86b17e0564232b52bb3800d9b15c45a3a0`.
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize"`, with `SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000"`. `OPT_FAST` is explained under "Additional context". The RTL it compiles was generated beforehand in two steps: `mill` to CHIRRTL with `--config DefaultConfig --num-cores 1 --enable-difftest`, then `firtool` with the Makefile's options and its `$fatal` and `$error` rewrites.
  - Run command (if applicable): `./build/emu -i zama16b_witness_witness.elf --no-diff --seed 1 --max-cycles 2000000 --ram-size 256MB`; the other two runs are under "To Reproduce".

### To Reproduce

One bare-metal program, run in M mode with translation off on a single hart. It runs twelve cases, one instruction each. Before each case it fills a 32-byte slot with known bytes and puts a sentinel in `rd`. Its trap handler records `mcause`, `mtval` and `mepc`, then resumes at the next instruction. After each case it prints one line through the UARTLite.

1. Assemble and link the program and linker script below.

```sh
riscv64-unknown-elf-gcc -march=rv64ia_zicsr -mabi=lp64 -c -o zama16b_witness_witness.o zama16b_witness_witness.S
riscv64-unknown-elf-gcc -march=rv64ia_zicsr -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T zama16b_witness.ld -o zama16b_witness_witness.elf zama16b_witness_witness.o
```

With `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0` this produces the ELF we ran, sha256 `4e775f881ffbef20b416b0810446546705322f292ae57cc86b30963bf1ece2ec`. The object file's name is recorded in the ELF's symbol table, so keep the file names to reproduce the digest.

2. Build the emulator with `make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize"`, then run:

```sh
./build/emu -i zama16b_witness_witness.elf --no-diff --seed 1 --max-cycles 2000000 --ram-size 256MB
./build/emu -i zama16b_witness_witness.elf --no-diff --seed 2 --max-cycles 2000000 --ram-size 256MB
./build/emu -i zama16b_witness_witness.elf --diff ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 2000000 --ram-size 256MB
```

3. Read the `ZW` lines. Their fields are `ZW <case> <traps> <mcause> <mtval> <mepc> <instruction address> <rd> <slot word 0> <slot word 1> <slot word 2> <slot word 3>`. When no trap was taken, the three CSR fields read all ones. The table under "Describe the bug" shows these lines for seed 1.

<details>
<summary><code>zama16b_witness_witness.S</code>, exactly as built</summary>

```asm
/* Generated by build_zama16b_witness_workload.py: the WITNESS program. */
.option norvc
.option norelax
.section .text.entry,"ax",@progbits
.globl _start
_start:
    j main
.section .text.main,"ax",@progbits
.globl main
main:
    la t0, trap
    csrw mtvec, t0
    csrw mie, zero
    la t0, total_traps
    sd zero, 0(t0)
    li a0, 90
    jal ra, putchar
    li a0, 87
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 66
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 71
    jal ra, putchar
    li a0, 73
    jal ra, putchar
    li a0, 78
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 0x5a414d4157495431
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
case_k_ecall:
    la s1, slot_0
    li t0, 0x8061422304e5c6a7
    sd t0, 0(s1)
    li t0, 0x78593a1bfcddbe9f
    sd t0, 8(s1)
    li t0, 0x70513213f4d5b697
    sd t0, 16(s1)
    li t0, 0x68492a0beccdae8f
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 0
    li a1, 0x0000000000000000
.globl insn_k_ecall
insn_k_ecall:
    ecall
    fence
    li a0, 0
    la a1, insn_k_ecall
    jal ra, report
case_k_amoadd_w_aligned_4:
    la s1, slot_1
    li t0, 0xbd9e7f60412203e4
    sd t0, 0(s1)
    li t0, 0xb5967758391afbdc
    sd t0, 8(s1)
    li t0, 0xad8e6f503112f3d4
    sd t0, 16(s1)
    li t0, 0xa5866748290aebcc
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 4
    li a1, 0x0000000000000123
.globl insn_k_amoadd_w_aligned_4
insn_k_amoadd_w_aligned_4:
    amoadd.w a2, a1, (a0)
    fence
    li a0, 1
    la a1, insn_k_amoadd_w_aligned_4
    jal ra, report
case_k_amoadd_d_aligned_8:
    la s1, slot_2
    li t0, 0xfadbbc9d7e5f4021
    sd t0, 0(s1)
    li t0, 0xf2d3b49576573819
    sd t0, 8(s1)
    li t0, 0xeacbac8d6e4f3011
    sd t0, 16(s1)
    li t0, 0xe2c3a48566472809
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 8
    li a1, 0x0000000100000123
.globl insn_k_amoadd_d_aligned_8
insn_k_amoadd_d_aligned_8:
    amoadd.d a2, a1, (a0)
    fence
    li a0, 2
    la a1, insn_k_amoadd_d_aligned_8
    jal ra, report
case_k_lr_w_within_granule_1:
    la s1, slot_3
    li t0, 0x3718f9dabb9c7d5e
    sd t0, 0(s1)
    li t0, 0x2f10f1d2b3947556
    sd t0, 8(s1)
    li t0, 0x2708e9caab8c6d4e
    sd t0, 16(s1)
    li t0, 0x1f00e1c2a3846546
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 1
    li a1, 0x0000000000000000
.globl insn_k_lr_w_within_granule_1
insn_k_lr_w_within_granule_1:
    lr.w a2, (a0)
    fence
    li a0, 3
    la a1, insn_k_lr_w_within_granule_1
    jal ra, report
case_s_amoadd_w_1:
    la s1, slot_4
    li t0, 0x74553617f8d9ba9b
    sd t0, 0(s1)
    li t0, 0x6c4d2e0ff0d1b293
    sd t0, 8(s1)
    li t0, 0x64452607e8c9aa8b
    sd t0, 16(s1)
    li t0, 0x5c3d1effe0c1a283
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 1
    li a1, 0x0000000000000123
.globl insn_s_amoadd_w_1
insn_s_amoadd_w_1:
    amoadd.w a2, a1, (a0)
    fence
    li a0, 4
    la a1, insn_s_amoadd_w_1
    jal ra, report
case_s_amoswap_w_6:
    la s1, slot_5
    li t0, 0xb19273543516f7d8
    sd t0, 0(s1)
    li t0, 0xa98a6b4c2d0eefd0
    sd t0, 8(s1)
    li t0, 0xa18263442506e7c8
    sd t0, 16(s1)
    li t0, 0x997a5b3c1dfedfc0
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 6
    li a1, 0x00000000c0ffee11
.globl insn_s_amoswap_w_6
insn_s_amoswap_w_6:
    amoswap.w a2, a1, (a0)
    fence
    li a0, 5
    la a1, insn_s_amoswap_w_6
    jal ra, report
case_s_amomaxu_w_11:
    la s1, slot_6
    li t0, 0xeecfb09172533415
    sd t0, 0(s1)
    li t0, 0xe6c7a8896a4b2c0d
    sd t0, 8(s1)
    li t0, 0xdebfa08162432405
    sd t0, 16(s1)
    li t0, 0xd6b798795a3b1cfd
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 11
    li a1, 0x00000000fffffff0
.globl insn_s_amomaxu_w_11
insn_s_amomaxu_w_11:
    amomaxu.w a2, a1, (a0)
    fence
    li a0, 6
    la a1, insn_s_amomaxu_w_11
    jal ra, report
case_s_amoadd_d_1:
    la s1, slot_7
    li t0, 0x2b0cedceaf907152
    sd t0, 0(s1)
    li t0, 0x2304e5c6a788694a
    sd t0, 8(s1)
    li t0, 0x1bfcddbe9f806142
    sd t0, 16(s1)
    li t0, 0x13f4d5b69778593a
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 1
    li a1, 0x0000000100000123
.globl insn_s_amoadd_d_1
insn_s_amoadd_d_1:
    amoadd.d a2, a1, (a0)
    fence
    li a0, 7
    la a1, insn_s_amoadd_d_1
    jal ra, report
case_s_amoor_d_2:
    la s1, slot_8
    li t0, 0x68492a0beccdae8f
    sd t0, 0(s1)
    li t0, 0x60412203e4c5a687
    sd t0, 8(s1)
    li t0, 0x58391afbdcbd9e7f
    sd t0, 16(s1)
    li t0, 0x503112f3d4b59677
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 2
    li a1, 0x0102040810204080
.globl insn_s_amoor_d_2
insn_s_amoor_d_2:
    amoor.d a2, a1, (a0)
    fence
    li a0, 8
    la a1, insn_s_amoor_d_2
    jal ra, report
case_s_amoxor_d_7:
    la s1, slot_9
    li t0, 0xa5866748290aebcc
    sd t0, 0(s1)
    li t0, 0x9d7e5f402102e3c4
    sd t0, 8(s1)
    li t0, 0x9576573819fadbbc
    sd t0, 16(s1)
    li t0, 0x8d6e4f3011f2d3b4
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 7
    li a1, 0x00ff00ff00ff00ff
.globl insn_s_amoxor_d_7
insn_s_amoxor_d_7:
    amoxor.d a2, a1, (a0)
    fence
    li a0, 9
    la a1, insn_s_amoxor_d_7
    jal ra, report
case_x_amoadd_w_14:
    la s1, slot_10
    li t0, 0xe2c3a48566472809
    sd t0, 0(s1)
    li t0, 0xdabb9c7d5e3f2001
    sd t0, 8(s1)
    li t0, 0xd2b39475563718f9
    sd t0, 16(s1)
    li t0, 0xcaab8c6d4e2f10f1
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 14
    li a1, 0x0000000000000123
.globl insn_x_amoadd_w_14
insn_x_amoadd_w_14:
    amoadd.w a2, a1, (a0)
    fence
    li a0, 10
    la a1, insn_x_amoadd_w_14
    jal ra, report
case_x_amoadd_d_9:
    la s1, slot_11
    li t0, 0x1f00e1c2a3846546
    sd t0, 0(s1)
    li t0, 0x17f8d9ba9b7c5d3e
    sd t0, 8(s1)
    li t0, 0x0ff0d1b293745536
    sd t0, 16(s1)
    li t0, 0x07e8c9aa8b6c4d2e
    sd t0, 24(s1)
    la s2, trap_record
    sd zero, 0(s2)
    li t0, -1
    sd t0, 8(s2)
    sd t0, 16(s2)
    sd t0, 24(s2)
    fence
    li a2, 0x5a5a5a5a5a5a5a5a
    addi a0, s1, 9
    li a1, 0x0000000100000123
.globl insn_x_amoadd_d_9
insn_x_amoadd_d_9:
    amoadd.d a2, a1, (a0)
    fence
    li a0, 11
    la a1, insn_x_amoadd_d_9
    jal ra, report
    li a0, 90
    jal ra, putchar
    li a0, 87
    jal ra, putchar
    li a0, 95
    jal ra, putchar
    li a0, 69
    jal ra, putchar
    li a0, 78
    jal ra, putchar
    li a0, 68
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    li a0, 12
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    li a0, 0
    jal ra, finish
halt:
    j halt

report:
    mv s9, ra
    mv s3, a0
    mv s4, a1
    mv s5, a2
    li a0, 90
    jal ra, putchar
    li a0, 87
    jal ra, putchar
    li a0, 32
    jal ra, putchar
    mv a0, s3
    jal ra, puthex64
    la s6, trap_record
    li a0, 32
    jal ra, putchar
    ld a0, 0(s6)
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    ld a0, 8(s6)
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    ld a0, 16(s6)
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    ld a0, 24(s6)
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s4
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    mv a0, s5
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    ld a0, 0(s1)
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    ld a0, 8(s1)
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    ld a0, 16(s1)
    jal ra, puthex64
    li a0, 32
    jal ra, putchar
    ld a0, 24(s1)
    jal ra, puthex64
    li a0, 10
    jal ra, putchar
    mv ra, s9
    ret

puthex64:
    mv s10, ra
    mv t3, a0
    li t4, 60
1:  srl a0, t3, t4
    andi a0, a0, 15
    li t1, 10
    bltu a0, t1, 2f
    addi a0, a0, 87
    j 3f
2:  addi a0, a0, 48
3:  jal ra, putchar
    addi t4, t4, -4
    bgez t4, 1b
    mv ra, s10
    ret

/* XiangShan's UARTLite write-only TX register; no readiness poll exists. */
putchar:
    li t0, 0x40600004
    sw a0, 0(t0)
    ret

/* XiangShan's trap instruction: a0 = 0 is HIT GOOD TRAP. */
finish:
    .word 0x0000006b
    j finish

.balign 64
trap:
    la t6, trap_record
    ld t5, 0(t6)
    addi t5, t5, 1
    sd t5, 0(t6)
    csrr t5, mcause
    sd t5, 8(t6)
    csrr t5, mtval
    sd t5, 16(t6)
    csrr t5, mepc
    sd t5, 24(t6)
    addi t5, t5, 4
    csrw mepc, t5
    la t6, total_traps
    ld t5, 0(t6)
    addi t5, t5, 1
    sd t5, 0(t6)
    li t6, 12
    bltu t6, t5, runaway
    mret
runaway:
    li a0, 3
    jal ra, finish
    j runaway

.section .data,"aw",@progbits
.balign 64
slot_0:
    .space 32
slot_1:
    .space 32
slot_2:
    .space 32
slot_3:
    .space 32
slot_4:
    .space 32
slot_5:
    .space 32
slot_6:
    .space 32
slot_7:
    .space 32
slot_8:
    .space 32
slot_9:
    .space 32
slot_10:
    .space 32
slot_11:
    .space 32
.org 2048
trap_record:
    .dword 0, 0, 0, 0
.org 2112
total_traps:
    .dword 0
```

</details>

<details>
<summary><code>zama16b_witness.ld</code>, exactly as built</summary>

```text
OUTPUT_ARCH(riscv)
ENTRY(_start)
SECTIONS
{
    . = 0x80000000;
    .text.entry : { *(.text.entry) }
    ASSERT(. <= 0x80000040, "the entry must stay below every observed fetch address")
    . = 0x80001000;
    .text : { *(.text.main) *(.text*) }
    ASSERT(. <= 0x80004000, "code runs into data")
    . = 0x80004000;
    .data : { *(.data*) }
    /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.riscv.attributes) }
}
```

</details>

### Additional context

**Difftest does not catch this, because NEMU does the same.** For a store/AMO address-misaligned exception, difftest does not force the exception onto the reference; it lets NEMU execute the instruction itself. The run with `--diff ready-to-run/riscv64-nemu-interpreter-so` reached `HIT GOOD TRAP`, and its twelve `ZW` lines are identical to those of the two runs without difftest. NEMU was therefore stepped through each of these AMOs on its own and raised an exception as well, since performing one would have changed `rd` and the next PC. So a change that implements the granule in the RTL needs the matching change in NEMU, or difftest will report the repaired core as wrong.

<details>
<summary>Where difftest hands the instruction to NEMU</summary>

Only page faults, guest page faults and hardware errors are forced onto the reference:

```cpp
// https://github.com/OpenXiangShan/difftest/blob/4c64fe86b17e0564232b52bb3800d9b15c45a3a0/src/test/csrc/difftest/checkers/traps.cpp#L83-L85
  if (probe.exception == EX_IPF || probe.exception == EX_LPF || probe.exception == EX_SPF ||
      probe.exception == EX_IGPF || probe.exception == EX_LGPF || probe.exception == EX_SGPF ||
      probe.exception == EX_HWE) {
```

```cpp
// https://github.com/OpenXiangShan/difftest/blob/4c64fe86b17e0564232b52bb3800d9b15c45a3a0/src/test/csrc/difftest/checkers/traps.cpp#L99-L106
  } else {
#ifdef DEBUG_MODE_DIFF
    if (DEBUG_MEM_REGION(true, probe.exceptionPC)) {
      debug_mode_copy(probe.exceptionPC, 4, probe.exceptionInst);
    }
#endif
    proxy->ref_exec(1);
  }
```

</details>

<details>
<summary>Where NEMU raises the exception</summary>

The run's `.so` is from `ready-to-run`, whose commit message names NEMU `513786c4c5`; that commit cannot be found in the public NEMU repository, so the lines below are from its default branch at `42dd82b6` (2026-09-29). `amo_slow_path` in `amo.c` calls the check before its memory access:

```c
// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/instr/rva/amo.c#L120-L123
  // AMO does not support misalign operation
  // So check misalign before real memory access
  void isa_amo_misalign_data_addr_check(vaddr_t vaddr, int len, int type);
  isa_amo_misalign_data_addr_check(*src1, width, type);
```

The check compares the address with the operand size only, and raises the exception when `CONFIG_AMO_AC_SOFT` is set:

```c
// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/system/mmu.c#L1068-L1078
// AMO access currently does not support hardware misalignment.
void isa_amo_misalign_data_addr_check(vaddr_t vaddr, int len, int type) {
  if (unlikely((vaddr & (len - 1)) != 0)) {
    Logm("addr misaligned happened: vaddr:%lx len:%d type:%d pc:%lx", vaddr, len, type, cpu.pc);
    if (ISDEF(CONFIG_AMO_AC_SOFT)) {
      int ex = cpu.amo || type == MEM_TYPE_WRITE ? EX_SAM : EX_LAM;
      cpu.trapInfo.tval = vaddr;
      longjmp_exception(ex);
    }
  }
}
```

`riscv64-xs-ref_defconfig`, the first configuration that commit message lists, sets it:

```text
// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/configs/riscv64-xs-ref_defconfig#L209
CONFIG_AMO_AC_SOFT=y
```

</details>

**The project's own supported-extension list.** The user guide's instruction-set page for `kunminghu-v3` does not list `zama16b`:

```text
// https://github.com/OpenXiangShan/XiangShan-User-Guide/blob/6ef9b3fe7871ec8d32e51a0f8ff0a67c4fd400ee/docs/en/instruction-set.md#L8-L19
## Supported ISA Extensions

i, m, a, f, d, c, b, v, h, sdtrig, sha, shcounterenw, shgatpa, shlcofideleg,
shtvala, shvsatpa, shvstvala, shvstvecd, smaia, smcsrind, smdbltrp, smmpm,
smnpm, smrnmi, smstateen, ss1p13, ssaia, ssccptr, sscofpmf, sscounterenw,
sscsrind, ssdbltrp, ssnpm, sspm, ssstateen, ssstrict, sstc, sstvala, sstvecd,
ssu64xl, supm, sv39, sv48, svade, svbare, svinval, svnapot, svpbmt, za64rs,
zacas, zawrs, zba, zbb, zbc, zbkb, zbkc, zbkx, zbs, zcb, zcmop, zfa, zfh,
zfhmin, zic64b, zicbom, zicbop, zicboz, ziccamoa, ziccif, zicclsm, ziccrse,
zicntr, zicond, zicsr, zifencei, zihintntl, zihintpause, zihpm, zimop, zkn,
zknd, zkne, zknh, zksed, zksh, zkt, zvbb, zvfh, zvfhmin, zvkt, zvl128b, zvl32b,
zvl64b
```

The page was last changed on 2025-07-11, before the #6463 commit of 2026-09-03, so it may lag the declaration rather than contradict it.

**Earlier public statements about misaligned AMOs.** Contributors have said that XiangShan does not support misaligned AMOs, in [#3813](https://github.com/OpenXiangShan/XiangShan/issues/3813), [#3830](https://github.com/OpenXiangShan/XiangShan/issues/3830), [#3844](https://github.com/OpenXiangShan/XiangShan/issues/3844) and [#4667](https://github.com/OpenXiangShan/XiangShan/issues/4667). #4667 reported an unaligned `amomin.d` raising `mcause` 6 and was closed as not planned. [#3320](https://github.com/OpenXiangShan/XiangShan/pull/3320) named Zama16b in its title in 2024, beside hardware support for misaligned loads and stores, but did not add it to any list: `git log -S'"zama16b"' -- src/main/scala/xiangshan/Parameters.scala` finds only 50cdcfc2, and no file under `src` contains the string at its parent. All of these predate #6463, and none of them concerns the declaration.

**Configuration and build.** The runs used `DefaultConfig`, on a build with no local change. `OPT_FAST` is overridden because difftest's default at this commit, `-O3 -fno-slp-vectorize` (from OpenXiangShan/difftest#968), is a clang option that GCC rejects. `-fno-tree-slp-vectorize` is GCC's option for the same pass.

**What this report does not establish.**

- Anything about misaligned loads and stores, the other half of Zama16b. Their atomicity cannot be observed from one hart, and no case tests it.
- Anything in S or U mode, under address translation, or with more than one hart.

**Model and framework information.** AI assistance: Claude Opus 5.5, through Claude Code, under human direction. Every quotation, value and table here is re-checked by a program against the revision or run it cites.
