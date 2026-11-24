### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

In S mode with Sv39, two virtual pages VA1 and VA2 map to the same physical page P, so VA1 and VA2 are synonyms. On `kunminghu-v3` this sequence loads A, the older store's value, instead of B:

```asm
sd a1, 0x40(s2)     # A through VA1
sd a2, 0x40(s7)     # B through VA2
ld s4, 0x40(s2)     # through VA1: returns A, B is required
```

A later M-mode load from P reads B, so the younger store's value is visible afterwards; the load above returned the older store's value instead. The code reading below and the change under "Additional context" place the cause in the store queue's forwarding.

Under DiffTest the run stops at that load. The commit trace shows `ld s4, 64(s2)` at `0x80000234` writing A to x20, which is `s4`, and the reference holds B:

```text
commit group [15]: pc 0080000234 cmtcnt 2 <--
[30] commit pc 0000000080000234 inst 04093a03 wen 1 dst 20 data a0a0000000000000 idx 055 (00)
[31] commit pc 0000000080000238 inst 040c3a83 wen 1 dst 21 data b0b0000000000000 idx 056 (01) <--
     s4 different at pc = 0x9f3d3f04c48, right = 0xb0b0000000000000, wrong = 0xa0a0000000000000
Core 0: ABORT at pc = 0x9f3d3f04c48
```

The last commit group, at `0x80000234`, retired the load and the next instruction together, and `<--` marks that group and its last instruction. The pc printed in the last two lines is not a program address; the commit trace above them is where the load is.

The program under "To Reproduce" runs five steps. Its output without DiffTest:

```text
step 0 a0a0000000000000 b0b0000000000000 b0b0000000000000
step 1 a0a0000000000100 b0b0000000000100 b0b0000000000100
step 2 b0b0000000000200 b0b0000000000200 b0b0000000000200
step 3 b0b0000000000300 b0b0000000000300 b0b0000000000300
step 4 a0a0000000000400 b0b0000000000400 a0a0000000000400
done
```

Each line is `step <n> <first load> <second load> <P + 0x40 read by M mode after the step>`:

| Step | Name | Accesses | Printed | Required |
|---|---|---|---|---|
| 0 | synonym | `sd` A via VA1; `sd` B via VA2; `ld` via VA1; `ld` via VA2 | A, B, B | B, B, B |
| 1 | synonym after a division chain | division chain; `sd` A via VA1; `sd` B via VA2; `ld` via VA1; `ld` via VA2 | A, B, B | B, B, B |
| 2 | second load through VA2 | division chain; `sd` A via VA1; `sd` B via VA1; `ld` via VA1; `ld` via VA2 | B, B, B | B, B, B |
| 3 | one virtual address | division chain; `sd` A via VA1; `sd` B via VA1; `ld` via VA1; `ld` via VA1 | B, B, B | B, B, B |
| 4 | another physical page | division chain; `sd` A via VA1; `sd` B via VA3; `ld` via VA1; `ld` via VA3 | A, B, A | A, B, A |

VA1 = `0x100000000` and VA2 = `0x100002000` map to P = `0x80400000`; VA3 = `0x100004000` maps to P3 = `0x80401000`. Every access is an aligned doubleword at offset 0x40. In step n, A is `0xa0a0000000000n00` and B is `0xb0b0000000000n00`, and before the step M mode writes `0x2020000000000n00` to P + 0x40 and `0x3030000000000n00` to P3 + 0x40.

Steps 0 and 1 return A where B is required. Steps 2, 3 and 4 read as required.

The store queue selects the entry to forward from by virtual address:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L506-L516
      // Two-step selection to handle circular queue segments
      val s1CanForwardLow = s1AgeMaskLow & s1OverlapMask & s1VaddrMatchVec
      val s1CanForwardHigh = s1AgeMaskHigh & s1OverlapMask & s1VaddrMatchVec

      // find youngest entry, which is one-hot
      // Find youngest store (highest index = most recent)
      //   Reverse vector so we can find leftmost 1 (highest index)
      val (s1SelectLowOH, _)             = findYoungest(Reverse(s1CanForwardLow))
      val (s1ForwardHighOH, _)           = findYoungest(Reverse(s1CanForwardHigh))
      val s1SelectHighOH                 = s1ForwardHighOH & VecInit(Seq.fill(StoreQueuePhysicalSize)(!s1CanForwardLow.orR)).asUInt
      val s1SelectOH                     = Reverse(s1SelectLowOH | s1SelectHighOH) // index higher, mean it younger
```

It then compares the physical address of the selected entry only:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L616-L621
      // two situation need to trigger paddr not match :
      // [1]. vaddr match, but paddr not match.
      // [2]. vaddr not match, but paddr match.
      val s2PaddrNoMatch       = Mux(s2ForwardValid,
        !(s2PaddrMatchVec & s2CanForward & s2SelectOH).orR, // if forward valid, select entry's paddr must match
        (s2PaddrMatchVec & s2CanForward).orR) // if forward invalid, must no paddr match
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L659
      s2Resp.bits.matchInvalid     := s2PaddrNoMatch && s2SafeForward // if multi match, let load replay.
```

Read from the code, the five steps meet this check as follows. The change under "Additional context" tests this reading for steps 0 and 1; for steps 2 to 4 the printed values are consistent with it:

- Steps 0 and 1: the virtual-address match selects A, the only store made through VA1. Its physical address matches the load's, so the `Mux`'s true branch finds nothing wrong, although B, made through VA2, matches the load's physical address too. This is a third situation, which the comment's two cases do not list: the selected entry matches by both addresses, and B, a store younger than A, matches by physical address only. A is the only store that matches by virtual address, so `s2MultiMatch` is false and `s2SafeForward` is true, and `matchInvalid` is `s2PaddrNoMatch`, which is false.
- Step 2's second load goes through VA2, which no store used. Nothing is selected and B's physical address matches, so this is case [2], the `Mux`'s false branch. The load reads B, as required, consistent with that branch raising `matchInvalid`.
- Step 3 uses VA1 only, and the youngest store that matches, B, is selected.
- Step 4's second store goes to another physical page, so B matches neither address of the first load.

Steps 1 to 4 begin with eight dependent divisions whose result nothing reads. They are there to keep the stores in the store queue: a store leaves it only after it retires, and the divisions hold up retirement. Step 0 has no divisions and returns A all the same on this `DefaultConfig` build. On the `MinimalConfig` build of [`505315237f`](https://github.com/OpenXiangShan/XiangShan/commit/505315237f4079d6dafeacbab906e7e6962b41a5) under "Additional context", step 0 reads B and step 1, with the divisions, reads A. Of the builds that lack the full-mask comparison shown under "Additional context", the program ran on these two and on a `DefaultConfig` build of the previous head, [`09453ff8f4`](https://github.com/OpenXiangShan/XiangShan/commit/09453ff8f44e9d2b526e6149c60e1dc3f08c7192), which printed the same lines; it was not run on a `MinimalConfig` build of `kunminghu-v3`. Step 1 returned A on all three of these builds, and step 0 on two.

### Expected behavior

The load value axiom of RVWMO:

> Each byte of each load _i_ returns the value written to that byte by the store that is the latest in global memory order among the following stores:
> 1. Stores that write that byte and that precede _i_ in the global memory order
> 2. Stores that write that byte and that precede _i_ in program order
>
> — [rvwmo.adoc, lines 319-327](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/unpriv/rvwmo.adoc#L319-L327)

Preserved program order, which the global memory order respects, orders two stores to overlapping addresses:

> The complete definition of preserved program order is as follows (and note that AMOs are simultaneously both loads and stores): memory operation _a_ precedes memory operation _b_ in preserved program order (and hence also in the global memory order) if _a_ precedes _b_ in program order, _a_ and _b_ both access regular main memory (rather than I/O regions), and any of the following hold:
>
> — [rvwmo.adoc, lines 271-277](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/unpriv/rvwmo.adoc#L271-L277)

> 1. _b_ is a store, and _a_ and _b_ access overlapping memory addresses
>
> — [rvwmo.adoc, line 281](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/unpriv/rvwmo.adoc#L281-L281)

<details>
<summary>The supervisor chapter and the XiangShan user guide on the same point</summary>

The supervisor chapter adds, about accesses to one physical address through different virtual addresses:

> Some ISAs architecturally expose _virtually indexed, physically tagged_ caches, in that accesses to the same physical address via different virtual addresses might not be coherent unless the virtual addresses lie within the same cache set. Implicitly, this specification does not permit such behavior to be architecturally exposed.
>
> — [supervisor.adoc, lines 1441-1445](https://github.com/riscv/riscv-isa-manual/blob/95b6c3e21a56b0e941164ab9ded01b576131217c/src/priv/supervisor.adoc#L1441-L1445)

The user guide states the memory model cacheable memory follows:

```text
// https://github.com/OpenXiangShan/XiangShan-User-Guide/blob/6ef9b3fe7871ec8d32e51a0f8ff0a67c4fd400ee/docs/en/memory-model.md#L30-L35
The cacheable memory adopts the RVWMO (RISC-V Weak Memory Ordering) memory
model. Under this model, the actual read/write order of memory among multiple
cores may not match the program-specified access sequence. Therefore, the RISC-V
instruction set architecture provides Fence instructions to ensure memory access
synchronization. Additionally, the RISC-V A extension includes LR/SC
instructions and AMO instructions for locking and atomic operations.
```

</details>

So the first load in steps 0 and 1 should return B. A and B write the same bytes of P, which is regular main memory, and A precedes B in program order, so A precedes B in the global memory order. Both precede the load in program order, so the load value axiom gives B, and no `FENCE` is needed for it.

The design intends this case to be caught. In [#1915](https://github.com/OpenXiangShan/XiangShan/issues/1915) a maintainer answered that the forwarding failure the documentation describes, in which the virtual- and physical-address matches find different entries and the load is redirected, happens when two virtual addresses map to the same physical address and stores through both are in the store queue or sbuffer. [#2370](https://github.com/OpenXiangShan/XiangShan/issues/2370) describes the same check, forwarding data selected by virtual address and verified by physical address, and [#2396](https://github.com/OpenXiangShan/XiangShan/issues/2396) explains why a disagreement between the two leads to a redirect. None of the three reports a load that returns an older store's value.

### Environment

- Hardware
  - CPU: `AMD EPYC 9534 64-Core Processor`.
  - Memory (GB): 256, with 16 cores.
- Software
  - Operating system: `Ubuntu 24.04.4 LTS`, in the container the emulator ran in.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`, which built the emulator.
  - java version: 17.0.19 (Temurin).
  - mill version: 0.12.17.
- Repo
  - XiangShan commit id: [`a7b9dea601f2f08dcca7d97ac544221cf02b1fe9`](https://github.com/OpenXiangShan/XiangShan/commit/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9). The emulator prints `Core  0's Commit SHA is: a7b9dea601, dirty: 0`.
  - NEMU commit id (if difftest failed with NEMU): the head's own `ready-to-run/riscv64-nemu-interpreter-so`, from `ready-to-run` at [`4cf9983569432035d41956c65e6a58724b5954be`](https://github.com/OpenXiangShan/ready-to-run/commit/4cf9983569432035d41956c65e6a58724b5954be).
- Build & Run
  - Build command: `make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"`. The emulator we ran was built by the steps under "To Reproduce" instead, with `Verilator 5.048 2026-04-26 rev conda-forge build 0`.
  - Run command (if applicable): `./build/emu -i sq_alias_minimal.elf --no-diff --seed 1 --max-cycles 400000 --ram-size 256MB`, and with DiffTest `./build/emu -i sq_alias_minimal.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 400000 --ram-size 256MB`.

### To Reproduce

One bare-metal program on a single hart. It starts in M mode, builds the Sv39 page tables, enters S mode for each step, and prints one line per step through the UARTLite.

1. Assemble and link the program and linker script below:

```sh
riscv64-unknown-elf-gcc -march=rv64im_zicsr -mabi=lp64 -c -o sq_alias_minimal.o sq_alias_minimal.S
riscv64-unknown-elf-gcc -march=rv64im_zicsr -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T sq_alias_minimal.ld -o sq_alias_minimal.elf sq_alias_minimal.o
```

The ELF we ran was built with `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0` and has sha256 `752b2ec0ed1f9c6760901b40eaed09ef05bba70c1869461529280a79178e8006`.

2. Build the emulator at the commit under "Environment", then run it without and with DiffTest. With the GCC under "Environment", the command below builds this configuration; we did not run it as written, and the emulator we ran was built by the steps in the collapsed block after it.

```sh
make emu CONFIG=DefaultConfig OPT_FAST="-O3 -fno-tree-slp-vectorize"
```

<details>
<summary>Steps that built the emulator we ran</summary>

```sh
mill -i -Djvm-xmx=40G -Djvm-xss=256m xiangshan.test.runMain top.XiangShanSim --target-dir build/chirrtl --config DefaultConfig --issue E.b --num-cores 1 --target chirrtl --enable-difftest --full-stacktrace
firtool build/chirrtl/SimTop.fir -o build/rtl --split-verilog -O=release --disable-annotation-unknown --lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none --default-layer-specialization=enable
# then, as the Makefile's RTL rule does for this configuration: a header from git on SimTop.sv,
# $fatal replaced by xs_assert_v2(`__FILE__, `__LINE__), and $error( by $fwrite(32'h80000002,
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" \
make emu CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize" -j16
```

firtool was version 1.149.0. Those steps used this cluster's absolute paths, shortened here, and the `make` step also set `CXX`, `LINK` and `AR` to the GCC 15.2.0 toolchain named under "Environment", and `PGO_CFLAGS` and `PGO_LDFLAGS` to header and library directories for the native dependencies; those five are left out.

</details>

```sh
./build/emu -i sq_alias_minimal.elf --no-diff --seed 1 --max-cycles 400000 --ram-size 256MB
./build/emu -i sq_alias_minimal.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 400000 --ram-size 256MB
```

3. The first run prints the lines shown under "Describe the bug" and ends in a good trap. The second stops at step 0's first load. If step 0 reads B on another build, the run with DiffTest may report the difference at step 1's first load, at `0x80000278`. The program compares no loaded value: once the five steps have run it ends with `a0` = 0, a good trap, for any values they printed, and only an unexpected exception ends it in a bad trap. So a good trap means the program ran to the end; the printed values show the defect.

<details>
<summary><code>sq_alias_minimal.S</code>, exactly as built</summary>

```asm
/* Store-queue forwarding with a virtual-address synonym: a minimal reproducer.
 *
 * VA1 = 0x100000000 and VA2 = 0x100002000 map to the same physical page P = 0x80400000;
 * VA3 = 0x100004000 maps to another page, P3 = 0x80401000. Every access is an aligned
 * doubleword at offset 0x40. Each step runs in S mode with Sv39 and returns with ECALL. M mode
 * then reads P + 0x40 through its physical address and prints
 *
 *     step <n> <first load> <second load> <P + 0x40>
 *
 * Before each step M mode writes 0x2020... to P + 0x40 and 0x3030... to P3 + 0x40, and sets s4 and s5
 * to 0x5e5e5e5e5e5e5e5e, so a load that did not execute is visible.
 */
    .option norvc
    .text
    .globl _start
_start:
    la t0, trap
    csrw mtvec, t0
    csrw medeleg, zero            # every trap goes to M mode
    csrw mideleg, zero
    csrw mie, zero
    csrci mstatus, 8              # mstatus.MIE = 0
    csrsi 0x744, 8                # mnstatus.NMIE = 1
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0              # mstatus.MDT = 0
    li t0, -1
    srli t0, t0, 10
    csrw pmpaddr0, t0             # PMP entry 0: all of memory, RWX, NAPOT
    li t0, 0x1f
    csrw pmpcfg0, t0
    li t0, 0x8000000000080200     # Sv39, root page table at 0x80200000
    csrw satp, t0
    sfence.vma

    # step 0: synonym
    li s0, 0
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100002000            # second store: VA2
    li s8, 0x100002000            # second load: VA2
    la t0, step0
    jal ra, run_step
    jal ra, report

    # step 1: synonym after a division chain
    li s0, 1
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100002000            # second store: VA2
    li s8, 0x100002000            # second load: VA2
    la t0, step1
    jal ra, run_step
    jal ra, report

    # step 2: second load through VA2
    li s0, 2
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100000000            # second store: VA1
    li s8, 0x100002000            # second load: VA2
    la t0, step2
    jal ra, run_step
    jal ra, report

    # step 3: one virtual address
    li s0, 3
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100000000            # second store: VA1
    li s8, 0x100000000            # second load: VA1
    la t0, step3
    jal ra, run_step
    jal ra, report

    # step 4: another physical page
    li s0, 4
    li s2, 0x100000000            # first store and first load: VA1
    li s7, 0x100004000            # second store: VA3
    li s8, 0x100004000            # second load: VA3
    la t0, step4
    jal ra, run_step
    jal ra, report

    la a0, text_done
    jal ra, puts
    li a0, 0
    .word 0x0000006b              # the emulator's trap instruction: a0 = 0 is a good trap

# Prepare step s0 and enter it in S mode at t0. The step's ECALL comes back to ra, in M mode.
run_step:
    mv s3, ra
    slli t2, s0, 8                # the step number, in every value below
    li t1, 0x2020000000000000
    or t1, t1, t2
    li t3, 0x80400040
    sd t1, 0(t3)                  # P + 0x40 before the step
    li t1, 0x3030000000000000
    or t1, t1, t2
    li t3, 0x80401040
    sd t1, 0(t3)                  # P3 + 0x40 before the step
    fence rw, rw
    sfence.vma
    li a1, 0xa0a0000000000000
    or a1, a1, t2                 # A, stored first
    li a2, 0xb0b0000000000000
    or a2, a2, t2                 # B, stored second
    li s4, 0x5e5e5e5e5e5e5e5e
    li s5, 0x5e5e5e5e5e5e5e5e
    li t1, 0x1880
    csrc mstatus, t1              # MPP = U, MPIE = 0
    li t1, 0x800
    csrs mstatus, t1              # MPP = S
    csrw mepc, t0
    mret

step0:                            # S mode: sd A via VA1, sd B via VA2, ld via VA1, ld via VA2
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step1:                            # S mode: sd A via VA1, sd B via VA2, ld via VA1, ld via VA2, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step2:                            # S mode: sd A via VA1, sd B via VA1, ld via VA1, ld via VA2, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step3:                            # S mode: sd A via VA1, sd B via VA1, ld via VA1, ld via VA1, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

step4:                            # S mode: sd A via VA1, sd B via VA3, ld via VA1, ld via VA3, after a division chain
    li a3, 0x7fffffffffffffff
    li a4, 3
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    div a3, a3, a4
    sd a1, 0x40(s2)
    sd a2, 0x40(s7)
    ld s4, 0x40(s2)
    ld s5, 0x40(s8)
    ecall

    .balign 64
trap:
    csrr t0, mcause
    li t1, 9                      # environment call from S mode: the step is over
    bne t0, t1, unexpected
    li t0, 0x1800
    csrs mstatus, t0              # MPP = M, so MRET returns to M mode
    li t0, 1
    slli t0, t0, 42
    csrc mstatus, t0              # mstatus.MDT = 0
    csrw mepc, s3
    mret
unexpected:
    la a0, text_trap
    jal ra, puts
    csrr a0, mcause
    jal ra, puthex
    li a0, 32
    jal ra, putc
    csrr a0, mepc
    jal ra, puthex
    li a0, 32
    jal ra, putc
    csrr a0, mtval
    jal ra, puthex
    li a0, 10
    jal ra, putc
    li a0, 1
    .word 0x0000006b              # bad trap

# Read P + 0x40 into s6 and print "step <s0> <s4> <s5> <s6>".
report:
    mv s1, ra
    li t0, 0x80400040
    ld s6, 0(t0)
    la a0, text_step
    jal ra, puts
    addi a0, s0, 48
    jal ra, putc
    li a0, 32
    jal ra, putc
    mv a0, s4
    jal ra, puthex
    li a0, 32
    jal ra, putc
    mv a0, s5
    jal ra, puthex
    li a0, 32
    jal ra, putc
    mv a0, s6
    jal ra, puthex
    li a0, 10
    jal ra, putc
    mv ra, s1
    ret

# The UARTLite transmit register is at 0x40600004.
putc:
    li t0, 0x40600004
    sw a0, 0(t0)
    ret
puts:
    li t0, 0x40600004
1:  lbu t1, 0(a0)
    beqz t1, 2f
    sw t1, 0(t0)
    addi a0, a0, 1
    j 1b
2:  ret
puthex:                           # a0 as 16 hexadecimal digits
    li t0, 0x40600004
    li t2, 60
1:  srl t1, a0, t2
    andi t1, t1, 15
    addi t1, t1, 48
    li t3, 58
    bltu t1, t3, 2f
    addi t1, t1, 39
2:  sw t1, 0(t0)
    addi t2, t2, -4
    bgez t2, 1b
    ret

    .section .rodata
text_step:
    .asciz "step "
text_trap:
    .asciz "trap "
text_done:
    .asciz "done\n"

# Sv39 page tables. Root at 0x80200000: entry 2 maps 0x80000000 as one 1 GiB page (the code),
# entry 4 points to 0x80204000, whose entry 0 points to 0x80208000. There entries 0 and 2 map P, and
# entry 4 maps P3.
    .section .pt, "aw", @progbits
.org 0x10
    .dword 0x200000cf
.org 0x20
    .dword 0x20081001
.org 0x4000
    .dword 0x20082001
.org 0x8000
    .dword 0x201000c7           # VA1
.org 0x8010
    .dword 0x201000c7           # VA2
.org 0x8020
    .dword 0x201004c7           # VA3
.org 0x9000
```

</details>

<details>
<summary><code>sq_alias_minimal.ld</code>, exactly as built</summary>

```text
OUTPUT_ARCH(riscv)
ENTRY(_start)
SECTIONS
{
    . = 0x80000000;
    .text : { *(.text) }
    .rodata : { *(.rodata) }
    . = 0x80200000;
    .pt : { *(.pt) }
    /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.riscv.attributes) }
}
```

</details>

### Additional context

**When it was introduced.** The check of the selected entry came with [`505315237f`](https://github.com/OpenXiangShan/XiangShan/commit/505315237f4079d6dafeacbab906e7e6962b41a5) (`refactor(StoreQueue): refactor StoreQueue`), which replaced `StoreQueue` with `NewStoreQueue`. There the check compared the selected entry's physical address only. Its parent, [`e6228891e4`](https://github.com/OpenXiangShan/XiangShan/commit/e6228891e42f63bb262e323a8a3005dbe664612c), compared the full physical- and virtual-address match masks over all older stores and raised `matchInvalid` on any difference. Case [2] was added later, in [`20fa098e8b`](https://github.com/OpenXiangShan/XiangShan/commit/20fa098e8b359ef0453bb13cbe6184cd0a9f7627) (`style(StoreQueue): add pipeline stage for forward`), whose message lists both cases under "fix trigger of matchInvalid when forward".

<details>
<summary>The check at 505315237f and at its parent, e6228891e4</summary>

At [`505315237f`](https://github.com/OpenXiangShan/XiangShan/commit/505315237f4079d6dafeacbab906e7e6962b41a5):

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/505315237f4079d6dafeacbab906e7e6962b41a5/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L476-L482
      //TODO: consumer need to choise whether use paddrNoMatch or not.
      val selectIsCboZero     = s2SelectCtrlEntry.isCbo && isCboZero(s2SelectDataEntry.cboType)
      // !Paddrmatch
      val paddrNoMatch       = !(
        (s2SelectDataEntry.paddr(log2Ceil(CacheLineSize / 8) - 1, log2Ceil(VLENB)) === s2LoadPaddr(log2Ceil(CacheLineSize / 8) - log2Ceil(VLENB) - 1, 0) || selectIsCboZero) &&
        s2SelectDataEntry.paddr(PAddrBits - 1, log2Ceil(CacheLineSize / 8)) === s2LoadPaddr(s2LoadPaddr.getWidth - 1, log2Ceil(CacheLineSize / 8) - log2Ceil(VLENB))
        ) && s2Valid
```

At [`e6228891e4`](https://github.com/OpenXiangShan/XiangShan/commit/e6228891e42f63bb262e323a8a3005dbe664612c):

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e6228891e42f63bb262e323a8a3005dbe664612c/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L693-L702
    // vaddr cam result does not equal to paddr cam result
    // replay needed
    // val vpmaskNotEqual = ((paddrModule.io.forwardMmask(i).asUInt ^ vaddrModule.io.forwardMmask(i).asUInt) & needForward) =/= 0.U
    // val vaddrMatchFailed = vpmaskNotEqual && io.forward(i).valid
    val vpmaskNotEqual = (
      (RegEnable(paddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid) ^ RegEnable(vaddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid)) &
      RegNext(needForward) &
      GatedRegNext(addrRealValidVec.asUInt)
    ) =/= 0.U
    val vaddrMatchFailed = vpmaskNotEqual && RegNext(io.forward(i).valid)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/e6228891e42f63bb262e323a8a3005dbe664612c/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L751
    io.forward(i).matchInvalid := vaddrMatchFailed
```

</details>

The program above, run without DiffTest on `MinimalConfig` builds of the parent and of [`505315237f`](https://github.com/OpenXiangShan/XiangShan/commit/505315237f4079d6dafeacbab906e7e6962b41a5), reads every step as required at the parent. At [`505315237f`](https://github.com/OpenXiangShan/XiangShan/commit/505315237f4079d6dafeacbab906e7e6962b41a5), step 1 reads A at the first load, and step 2 reads P's earlier value at the second load, which is the situation case [2], added later in [`20fa098e8b`](https://github.com/OpenXiangShan/XiangShan/commit/20fa098e8b359ef0453bb13cbe6184cd0a9f7627), covers. Step 0, which has no division chain, reads B there, and steps 3 and 4 read as required.

Neither commit builds as it stands. Each build carried a compile repair, given below; neither changes a line of the forwarding logic. The parent's is four lines in `PMP.scala` and in `NewLoadUnit.scala`, a module neither commit instantiates. [`505315237f`](https://github.com/OpenXiangShan/XiangShan/commit/505315237f4079d6dafeacbab906e7e6962b41a5)'s is the same four and fourteen more in five further files, and it touches `NewStoreQueue.scala` only in two debug fields of the store writeback, inside `if(debugEn)`.

<details>
<summary>Compile repair for <code>e6228891e4</code></summary>

```diff
diff --git a/src/main/scala/xiangshan/backend/fu/PMP.scala b/src/main/scala/xiangshan/backend/fu/PMP.scala
index 0ece6ed..e7159a9 100644
--- a/src/main/scala/xiangshan/backend/fu/PMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMP.scala
@@ -28,7 +28,6 @@ import xiangshan.backend.fu.util.HasCSRConst
 import xiangshan.backend.decode.isa.CSRs
 import utility._
 import xiangshan.cache.mmu.TlbCmd
-import freechips.rocketchip.rocket.CSRs
 
 trait PMPConst extends HasPMParameters {
   val PMPOffBits = 2 // minimal 4bytes
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index b903c54..50be40a 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -384,8 +384,7 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   io.rawNukeQuery.revoke := s3.io.revoke
   io.rollback := s3.io.rollback
   io.cancel := s3.io.cancel
-  s3.io.uncacheData := RegNextN(io.uncacheData, 3) // TODO
-  io.prefetchTrain := s3.io.prefetchTrain
+  io.prefetchTrain := DontCare
   s3.io.csrCtrl := io.csrCtrl
 
   // Debug info
```

</details>

<details>
<summary>Compile repair for <code>505315237f</code></summary>

```diff
diff --git a/src/main/scala/xiangshan/backend/fu/PMP.scala b/src/main/scala/xiangshan/backend/fu/PMP.scala
index 0ece6ed..e7159a9 100644
--- a/src/main/scala/xiangshan/backend/fu/PMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMP.scala
@@ -28,7 +28,6 @@ import xiangshan.backend.fu.util.HasCSRConst
 import xiangshan.backend.decode.isa.CSRs
 import utility._
 import xiangshan.cache.mmu.TlbCmd
-import freechips.rocketchip.rocket.CSRs
 
 trait PMPConst extends HasPMParameters {
   val PMPOffBits = 2 // minimal 4bytes
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala b/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala
index 1c0a161..b25c5a0 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LSQBundle.scala
@@ -87,6 +87,10 @@ class StaUopInfo(implicit p: Parameters) extends MemBlockBundle {
   val ftqPtr          = new FtqPtr
   val ftqOffset       = UInt(log2Up(FetchBlockInstOffsetWidth).W)
 
+  // mdp
+  val isFirstIssue    = Bool()
+  val isRVC           = Bool()
+
   // debug info
   val pc              = Option.when(debugEn)(UInt(VAddrBits.W))
   val debugInfo       = Option.when(debugEn)(new PerfDebugInfo)
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
index 0e6cb18..1cf4947 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
@@ -346,7 +346,7 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
     stFtqIdx(w) := DelayNWithValid(storeIn(w).bits.uop.ftqPtr, storeIn(w).valid, TotalSelectCycles)._2
     stFtqOffset(w) := DelayNWithValid(storeIn(w).bits.uop.ftqOffset, storeIn(w).valid, TotalSelectCycles)._2
     stIsRVC(w) := DelayNWithValid(storeIn(w).bits.uop.isRVC, storeIn(w).valid, TotalSelectCycles)._2
-    stIsFirstIssue(w) := DelayNWithValid(storeIn(w).bits.isFirstIssue, storeIn(w).valid, TotalSelectCycles)._2 // for perf
+    stIsFirstIssue(w) := DelayNWithValid(storeIn(w).bits.uop.isFirstIssue, storeIn(w).valid, TotalSelectCycles)._2 // for perf
   }
 
   // select rollback (part2), generate rollback request, then fire rollback request
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
index 78de5d1..d53dbef 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -909,8 +909,8 @@ abstract class NewStoreQueueBase(implicit p: Parameters) extends LSQModule {
       io.writeBack.bits.debug.isNCIO  := isPbmtNC(dataEntries.head.memoryType)
       io.writeBack.bits.debug.vaddr   := dataEntries.head.debugVaddr.get
       io.writeBack.bits.debug.paddr   := dataEntries.head.debugPaddr.get
-      io.writeBack.bits.debug_seqNum  := dataEntries.head.debugUop.get.debug_seqNum
-      io.writeBack.bits.debugInfo     := dataEntries.head.debugUop.get.debugInfo
+      io.writeBack.bits.debug_seqNum.foreach(_ := dataEntries.head.debugUop.get.debug_seqNum)
+      io.writeBack.bits.perfDebugInfo.foreach(_ := dataEntries.head.debugUop.get.perfDebugInfo)
     }
 
     io.exceptionInfo.valid             := (uncacheState === UncacheState.writeback) || (cboState === CboState.writeback)
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index b903c54..50be40a 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -384,8 +384,7 @@ class NewLoadUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSMo
   io.rawNukeQuery.revoke := s3.io.revoke
   io.rollback := s3.io.rollback
   io.cancel := s3.io.cancel
-  s3.io.uncacheData := RegNextN(io.uncacheData, 3) // TODO
-  io.prefetchTrain := s3.io.prefetchTrain
+  io.prefetchTrain := DontCare
   s3.io.csrCtrl := io.csrCtrl
 
   // Debug info
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 8c27332..2e71cdc 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -422,6 +422,8 @@ class StoreUnit(val param: ExeUnitParams)(implicit p: Parameters) extends XSModu
   io.toLsq.bits.mask      := s1_out.mask
   io.toLsq.bits.size          := s1_out.alignedType
   connectSamePort(io.toLsq.bits.uop, s1_out.uop)
+  io.toLsq.bits.uop.isRVC     := s1_out.uop.isRVC
+  io.toLsq.bits.uop.isFirstIssue := s1_out.isFirstIssue
 
 
   //TODO: `isLastRequest` means it's last request to write to storeQueue. if is normal request, it will be true,
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index d79dcef..91ba38f 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -131,8 +131,6 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     val hasExp                               = ExceptionNO.selectByFu(source.exceptionVec, fuCfg).asUInt.orR
     sink.robidx                             := source.uop.robIdx
     sink.uopidx                             := source.uop.uopIdx
-    sink.sqIdx                              := source.uop.sqIdx
-    sink.lqIdx                              := source.uop.lqIdx
     sink.feedback(VecFeedbacks.COMMIT)      := !hasExp
     sink.feedback(VecFeedbacks.FLUSH)       := hasExp
     sink.feedback(VecFeedbacks.LAST)        := true.B
```

</details>

**A direction for a fix.** With the parent's full-mask comparison added back beside the two cases, a `DefaultConfig` build of the previous head, [`09453ff8f4`](https://github.com/OpenXiangShan/XiangShan/commit/09453ff8f44e9d2b526e6149c60e1dc3f08c7192), whose `NewStoreQueue.scala` is the same file, reads every step's required values, and the run under DiffTest ends in a good trap:

```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
--- a/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala
@@ -564,6 +564,7 @@
       val s2OverlapMask      = RegEnable(s1OverlapMask, s1Valid)
       val s2WaitStrictSqIdx  = RegEnable(s1LoadSqIdx - 1.U, s1Valid)
       val s2MultiMatch       = RegEnable(s1MultiMatch, s1Valid)
+      val s2VaddrMatchVec    = RegEnable(s1VaddrMatchVec, s1Valid) // the virtual-address match vector, registered
       val s2LoadPaddr        = RegEnable(s1QueryPaddr, s1Valid)
       val s2UnalignQueueMatch = RegEnable(s1UnalignQueueMatch, s1Valid)
       val s2LoadStart        = RegEnable(s1LoadStart, s1Valid)
@@ -618,7 +619,8 @@
       // [2]. vaddr not match, but paddr match.
       val s2PaddrNoMatch       = Mux(s2ForwardValid,
         !(s2PaddrMatchVec & s2CanForward & s2SelectOH).orR, // if forward valid, select entry's paddr must match
-        (s2PaddrMatchVec & s2CanForward).orR) // if forward invalid, must no paddr match
+        (s2PaddrMatchVec & s2CanForward).orR) || // if forward invalid, must no paddr match
+        ((s2PaddrMatchVec ^ s2VaddrMatchVec) & s2CanForward).orR // [3]. an older store matches the load by one address only
 
       val s2SelectData         = (0 until VLENB).map(j =>
         j.U -> rotateByteRight(s2SelectDataEntry.data, j * 8)
```

It was built and run with the program above only; no other workload was run on it. The commits between [`09453ff8f4`](https://github.com/OpenXiangShan/XiangShan/commit/09453ff8f44e9d2b526e6149c60e1dc3f08c7192) and [`a7b9dea601`](https://github.com/OpenXiangShan/XiangShan/commit/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9) change no file under `mem/`.

With the new term the `Mux` is redundant. The selected entry is one of `s2CanForward & s2VaddrMatchVec`, so in case [1] its bit of the new term is set. When nothing is selected, no entry of `s2CanForward` matches by virtual address, so in case [2] the entry that matches by physical address sets its bit. The expression therefore equals the new term alone; the form above is the one that was built. Timing was not evaluated: the change adds a register for `s2VaddrMatchVec` and, in stage 2, an XOR and an OR reduction.

**The sbuffer.** The sbuffer at `kunminghu-v3` still compares every entry's virtual and physical tags and drains on any difference, so this report concerns the store queue's path only.

<details>
<summary>The sbuffer's check at the same commit</summary>

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L796-L805
    val vtag_matches = VecInit(widthMap(w => vtag(w) === getVTag(s1Req.vaddr)))
    // ptag_matches uses paddr from dtlb, which is far from sbuffer
    val ptag_matches = VecInit(widthMap(w => RegEnable(ptag(w), s1ReqValid) === RegEnable(getPTag(s1Paddr), s1ReqValid)))
    val tag_matches = vtag_matches
    val tag_mismatch = RegNext(s1ReqValid) && VecInit(widthMap(w =>
      GatedValidRegNext(vtag_matches(w)) =/= ptag_matches(w) && GatedValidRegNext((activeMask(w) || inflightMask(w)))
    )).asUInt.orR && !RegEnable(s1Kill, s1ReqValid)
    mismatch(i) := tag_mismatch
    when (tag_mismatch) {
      forward_need_uarch_drain := true.B
```

</details>

**Scope.** Only aligned doublewords at offset 0x40 of both pages were run. Partial overlaps, other access widths and different offsets on the two pages were not.

**Software that maps one page twice.** A double-mapped ring buffer maps one physical buffer at two virtual ranges, and a kernel that touches a user page through its own mapping of that memory uses a second virtual address for the same physical page. Either can meet this when a store through one mapping is followed by a store to the same bytes through the other mapping and then a load through the first mapping, while both stores are still in the store queue. A single store through one mapping followed by a load through the other is case [2], which the check covers. I have not run Linux or any other operating system on this.

**Related issues.** The "alias" in the title of [#5851](https://github.com/OpenXiangShan/XiangShan/issues/5851) is a load from the same address as an older store, and [#5998](https://github.com/OpenXiangShan/XiangShan/issues/5998) is a forwarding defect with a single mapping; both are other problems in store-to-load forwarding. A synonym in this report is a second virtual address for the same physical address.

**Configuration and build.** `DefaultConfig`, with no local change. The emulator we ran was built by the steps in the collapsed block under "To Reproduce": CHIRRTL through mill, SystemVerilog through firtool called directly rather than from mill, and then `make emu`. `OPT_FAST` is set there and in the suggested command because difftest's default at this commit, `-O3 -fno-slp-vectorize`, is a clang option that GCC rejects; `-fno-tree-slp-vectorize` is GCC's name for the same pass. `SIM_VFLAGS` splits Verilator's output more finely than difftest's `--output-split 30000`, and is set in the environment because the difftest Makefile appends `+define+DIFFTEST` to it.
