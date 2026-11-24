## Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

## Branch

kunminghu-v3

## Describe the bug

[#6112](https://github.com/OpenXiangShan/XiangShan/pull/6112) unconditionally filters direct and return resolves out of the backend input to `ResolveQueue`. The replacement IFU training event is generated only when `checkerRedirect.valid` is asserted. The only return-specific fault term in `PredChecker` is `retFaultVec`, and for a valid return its whole content is `!isPredTaken`; no term of the redirect condition compares a return target. A return target misprediction therefore cannot by itself assert `checkerRedirect.valid` once the return is predicted taken. A return in that state has no remaining resolve event that reaches BPU training: its target mismatch is resolved by the backend, and the backend resolve is what the new filter removes.

Measured on the assembled `MinimalConfig` core through the production frontend connections, with the same 16-iteration return workload run at both revisions: the parent of #6112 delivers `16` training events through the real FTQ-to-BPU connection to the main BTB's training stage, and the tested tip delivers `1`, a cold-start IFU event. A non-RAS indirect control delivers `16` on both revisions. What those events do once they arrive is narrower than the count suggests, and the section below measures it: the training-path change does not change the number of main-BTB writes in this workload. Both revisions write the entry once, and different training information supplies that one allocation, leaving a different target at each. That difference can affect a predicted target only through the S1 fallback taken when the micro-RAS bypass is invalid, and that fallback is not taken in this workload, measured below. No performance consequence is claimed.

The workload's return is not a correct prediction. It rewrites `ra` inside the callee, so the executed target differs from the RAS target; every backend resolve the run observes for that PC carries `mispredict` set, and the run separately counts `16` occurrences of the state described above: a return predicted taken for which the checker raises no fault.

This is a BPU training-coverage regression, not an architectural correctness failure. No incorrect execution follows from it, and no IPC or accuracy magnitude is claimed.

The backend resolve is filtered on the branch attribute, with no condition attached:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L86-L92
  private val backendFilteredResolve = io.backendResolve.map { backendResolve =>
    val filteredResolve = Wire(Valid(new ResolveWithSource))
    filteredResolve.valid := backendResolve.valid &&
      !(backendResolve.bits.attribute.isDirect || backendResolve.bits.attribute.isReturn)
    filteredResolve.bits.fromResolve(ResolveSource.Backend, backendResolve.bits)
    filteredResolve
  }
```

The replacement event exists only alongside a `PredChecker` redirect:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/Ifu.scala#L709-L713
    val attribute = checkerRedirect.bits.attribute
    val canTrain = attribute.isDirect || attribute.isReturn ||
      checkerRedirect.bits.invalidTaken || checkerRedirect.bits.notCfiTaken
    b.valid          := wbValid && checkerRedirect.valid
    b.bits.canTrain  := canTrain
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala#L47
    resolve.valid           := wbRedirect.valid && !backendRedirect && wbRedirect.bits.canTrain
```

`b.valid` requires `checkerRedirect.valid`. `canTrain` selects which redirects may train, but it cannot create an event where the checker produced no redirect. When a return the checker does not flag later resolves in the backend, that resolve is filtered out by the quotation above, so no `ResolveQueue` training event remains for that occurrence.

The return-specific fault term checks only whether the return was predicted taken; it does not compare the return target:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L88-L90
  retFaultVec := VecInit(pdInfoVec.zipWithIndex.map { case (pd, i) =>
    pd.brAttribute.hasPop && instrValid(i) && !isPredTaken(i)
  })
```

The indirect term beside it excludes returns explicitly, so it does not supply a target check for them either:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L85-L87
  jalrFaultVec := VecInit(pdInfoVec.zipWithIndex.map { case (pd, i) =>
    pd.brAttribute.isIndirect && !pd.brAttribute.hasPop && instrValid(i) && !isPredTaken(i)
  })
```

The redirect condition is the disjunction of those fault terms:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L121-L125
  private val stage1Fault = VecInit.tabulate(IBufferEnqueueWidth)(i =>
    jalFaultVec(i) || jalrFaultVec(i) || retFaultVec(i) || notCfiTaken(i) || invalidTaken(i)
  )
  dontTouch(stage1Fault.asUInt)
  mispredIdx.valid := ParallelOR(stage1Fault)
```

and the redirect's own valid bit is that result registered, with no further comparison:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L174
  io.resp.stage2Out.checkerRedirect.valid          := mispredIdxNext.valid && wbValid
```

So "`PredChecker` emits no redirect" is not the same statement as "the return was predicted correctly". A return that the BPU predicts taken and sends to the wrong target is in the no-redirect set, and it is the case the measurement below exercises.

<details>
<summary>Where the change came from, at the feature commit and its parent</summary>

At the parent commit the queue had no IFU port and the backend input was not filtered on the branch attribute:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/567d1da56d968a6dde8fbfe829896bc9b9ca2605/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L68-L74
  private val filteredResolve = io.backendResolve.map { backendResolve =>
    val filteredResolve = Wire(Valid(new Resolve))
    filteredResolve.valid := backendResolve.valid &&
      !(backendRedirect.reduce(_ || _) && backendResolve.bits.ftqIdx > backendRedirectPtr)
    filteredResolve.bits := backendResolve.bits
    filteredResolve
  }
```

The feature commit introduced the attribute filter and the replacement port together:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/24ed326a5786e43c9d2ce4f43821c19810f16c95/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L86-L95
  private val backendFilteredResolve = io.backendResolve.map { backendResolve =>
    val filteredResolve = Wire(Valid(new ResolveWithSource))
    filteredResolve.valid := backendResolve.valid && !(backendResolve.bits.attribute.isDirect || backendResolve.bits.attribute.isReturn) &&
      !(backendRedirect.reduce(_ || _) && backendResolve.bits.ftqIdx > backendRedirectPtr)
    filteredResolve.bits.fromResolve(ResolveSource.Backend, backendResolve.bits)
    filteredResolve
  }
  private val ifuFilteredResolve = Wire(Valid(new ResolveWithSource))
  ifuFilteredResolve.valid := io.ifuResolve.valid && !backendRedirect.reduce(_ || _)
  ifuFilteredResolve.bits.fromResolve(ResolveSource.Ifu, io.ifuResolve.bits)
```

</details>

### Why this report is about returns

The parent is not a baseline that trained every one of these branches. Its own dequeue stage already drops a correctly-predicted direct resolve, and rate-limits the rest under a saturating counter:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/567d1da56d968a6dde8fbfe829896bc9b9ca2605/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L97-L106
  private val resolve = filteredResolve.map { filteredResolve =>
    val hasMispredict = filteredResolve.bits.mispredict
    val isDirect      = filteredResolve.bits.attribute.isDirect
    val shouldDrop    = !hasMispredict && (dropResolveCounter.isSaturatePositive || isDirect)

    val resolve = Wire(Valid(new Resolve))
    resolve.valid := filteredResolve.valid && !shouldDrop
    resolve.bits  := filteredResolve.bits
    resolve
  }
```

So for direct branches the parent also withheld correct-prediction training, and what changes at the tip for those classes is a tip statement, not a regression. The parent-versus-tip comparison above is a return-path statement, and the measured workload puts it outside this rule entirely: `shouldDrop` is conjoined with `!hasMispredict`, every backend resolve in the run carries `mispredict` set, so the parent's `16` does not depend on the counter state and is not a correct-prediction case that this rule could have dropped.

### What the lost events would have done

The main BTB does not refresh a return's target. It writes an entry only on a miss or an attribute change, because the target-refresh case is reserved for indirect branches that do not pop the RAS:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala#L224-L232
  // Write entry only when there's a mispredict, and if:
  private val t1_entryNeedWrite = t1_needWrite && t1_mispredictInfo.valid && (
    // 1. not hit, always write a new entry, use mbtb replacer's victim way.
    !t1_hit ||
      // 2. hit, do write only if:
      //   a. it's an OtherIndirect-type branch (to update target and play the role of Ittage's base table).
      t1_mispredictInfo.bits.attribute.needIttage ||
      //   b. attribute changed, probably indicating a software self-modification.
      t1_mispredictInfo.bits.attribute =/= Mux1H(t1_hitMask, t1_meta.map(_.attribute))
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bundles.scala#L64
  def needIttage: Bool = isIndirect && !hasPop
```

RAS commit training is separate from the `ResolveQueue` and BPU-training path. It arrives through `callRetCommit` and the FTQ commit queue, neither of which #6112 touches:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ftq/Ftq.scala#L503-L505
  commitQueue.io.backendCommit := io.fromBackend.callRetCommit

  io.toBpu.commit.valid                     := commitQueue.io.bpuTrain.valid
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L87
  private val commit   = io.fromFtq.commit
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L227
  ras.io.commit                  := commit
```

So RAS commit training is separate from the `ResolveQueue` and BPU-training path examined here; what this report establishes is the loss of the resolve-training event.

The queue holds one entry per `ftqIdx` and creates it only when a resolve for that block arrives, so a block whose only resolve is the filtered return produces no training event at all rather than an event with one slot empty.

The run measures what the parent's `16` events did at that write stage, and what the tip's one event did. A second observer reads the main BTB's own write predicate rather than the training seam: it prints one line per `t1_fire` visit for the workload's three filtered addresses, carrying the target presented on that visit and the value of `finalTrace.needWrite`, which is `t1_entryNeedWrite` above. At the parent, one visit wrote the entry and `15` did not, which is the rule above: the first execution misses and allocates, and the rest hit with an unchanged attribute. At the tip the single cold-start event is the only visit, and it writes. So the training-path change does not change the number of main-BTB writes in this workload. It changes which training information supplies the single allocation: at the parent that is a backend resolve carrying the executed target, and at the tip it is the one IFU event.

<details>
<summary>Where the observed write predicate comes from</summary>

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala#L303
  io.trace.needWrite := t1_fire && t1_entryNeedWrite
```

</details>

The two revisions leave different targets in that entry. The parent writes `0x8000020c`, which is `subject_landing`, the target the return actually takes. The tip writes `0x80000014`, which is `expected_ras_return`, the address the call pushed and therefore the RAS target.

For prediction, that stored-target difference can affect the S1 target only when the micro-RAS bypass is unusable. For a return the predictors override the BTB target with the RAS target whenever the micro-RAS can be used:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L511-L513
    case p => when(p.valid && p.bits.attribute.isReturn && uras.io.specOut.isCanUse) {
        p.bits.target := uras.io.specOut.retTarget
      }
```

`isCanUse` says whether the micro-RAS bypass can supply the next S1 top-of-stack given the pushes and pops in flight at S2 and S3. It is a bypass-validity flag rather than a capacity condition, and a redirect is one of the things that clears it. So the stored target is what S1 predicts with when the bypass is invalid, and S3 does not consult it for a return at all: the S3 selector is the branch attribute alone, and its RAS arm precedes the arm that would have used the stored target.

<details>
<summary>The source for the two statements above</summary>

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/ras/MicroRas.scala#L139-L142
  when(io.hasRedirect) {
    // On redirect, the main RAS state is being restored,
    // and the S1–S3 pipeline is flushed → no valid prediction.
    isCanUse := false.B
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L394
  private val s3_useRas             = s3_firstTakenBranch.bits.attribute.isReturn
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L408-L416
  s3_prediction.target :=
    MuxCase(
      s3_fallThroughPrediction.target,
      Seq(
        (s3_taken && s3_useRas)    -> ras.io.topRetAddr,
        (s3_taken && s3_useIttage) -> ittage.io.prediction.target,
        s3_taken                   -> s3_firstTakenBranch.bits.target
      )
    )
```

</details>

A separate run measures that directly. It counts, per revision, the S1 taken-return predictions made while the micro-RAS bypass is unusable, which by the override above is exactly when S1 predicts a return from the stored target. That count is `0` at `567d1da56d968a6dde8fbfe829896bc9b9ca2605` and `0` at `50cdcfc2c45d0631591310435835c0180c105489`, across both seeds, so this workload never predicts a return from the stored target and the entry-content difference above is not read in it. The instrumented run is at `50cdcfc2c45d0631591310435835c0180c105489` and at the same parent; the whole `src/main/scala/xiangshan/frontend` subtree is one object at that revision and at the head this report measures, so the instrumented expression is the same source here.

The S1 prediction is sent to the FTQ as it is made, so an S1 target does reach the fetch stream; at S3 the return's target is taken from the main RAS rather than from what S1 predicted, and the FTQ rewinds to the S3 block when it overrides:

<details>
<summary>The source for the override and the rewind</summary>

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L469-L475
  io.toFtq.prediction.valid := s1_valid && s2_ready || s3_override
  when(s3_override) {
    io.toFtq.prediction.bits.fromStage(s3_startPc.get, s3_prediction)
  }.otherwise {
    io.toFtq.prediction.bits.fromStage(s1_startPc.get, s1_prediction)
  }
  io.toFtq.prediction.bits.s3Override := s3_override
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ftq/Ftq.scala#L200-L202
  when(prediction.bits.s3Override) {
    bpuPtr := io.fromBpu.s3FtqPtr + 1.U
  }.elsewhen(bpuEnqueue) {
```

</details>

So at S3 the stored BTB target is not selected for a return; the target source is `ras.io.topRetAddr`. The entry-content difference above can therefore affect a predicted target only through the S1 fallback examined here, and that fallback was not observed in this workload. No performance consequence is claimed.

## Expected behavior

A backend-resolved return target misprediction that would previously have reached BPU training should not lose it to the unconditional return-attribute filter alone, unless that loss is intended.

If the reduction is intentional, please confirm and a comment on the filter would record it.

## Environment

- Measured at `92cc48d2cb6af52a1fb2922f9eb99a37918654b3` on `kunminghu-v3`, the current default-branch commit. The branch moved while this report was prepared, and the training-loss reproduction and its counts were re-run at each head rather than argued forward; the quoted files and every count are the same at each. The separate S1 micro-RAS fallback measurement was run once, at `50cdcfc2c45d0631591310435835c0180c105489`, and the frontend subtree is one object at that revision and at the head above.
- The feature commit: `24ed326a5786e43c9d2ce4f43821c19810f16c95`, `feat(ifu): add a resolve feedback path from the IFU to the BPU for training (#6112)`.
- Its direct parent: `567d1da56d968a6dde8fbfe829896bc9b9ca2605`.

- Hardware
  - CPU: AMD EPYC 9534 64-Core Processor, one socket, one thread per core. Each arm ran as a batch job allocated 8 of those cores.
  - Memory (GB): 556 on the node, each arm under a `128G` request.
  - Storage (GB): 58368, shared network filesystem.
- Software
  - Operating system: Rocky Linux 9.8, kernel `5.14.0-687.29.1.el9_8.x86_64`. The emulator is built and run in a container image pinned by sha256 `d6bda86973a86ad94c251c088f6c09cc033413aabd74d9d7bc8e434853fee2cc`.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`.
  - clang version: not used.
  - java version: `openjdk version "17.0.19" 2026-04-21`, `OpenJDK Runtime Environment Temurin-17.0.19+10 (build 17.0.19+10)`.
  - mill version: 0.12.17 at the head arm, 0.12.15 at the parent arm; `firtool` 1.149.0 against 1.135.0. Each revision pins its own. Held constant across the arms: `Verilator 5.048 2026-04-26 rev conda-forge build 0` and the compiler above.
- Repo
  - XiangShan commit id: `92cc48d2cb6af52a1fb2922f9eb99a37918654b3` for the head arm and `567d1da56d968a6dde8fbfe829896bc9b9ca2605` for the parent arm.
  - NEMU commit id (if difftest failed with NEMU): difftest did not fail; both arms reach a good trap. Each arm used the `ready-to-run/riscv64-nemu-interpreter-so` from its own revision's submodule, `4cf9983569432035d41956c65e6a58724b5954be` at the head arm.
  - SPIKE commit id (if difftest failed with SPIKE): SPIKE was not used.
- Build & Run
  - Build command: `make emu CONFIG=MinimalConfig NUM_CORES=1`, after applying the observer in step 2 of "To Reproduce".
  - Run command (if applicable): `./build/emu -i return_target_mismatch.elf --diff ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 2000000 --ram-size 256MB`

## To Reproduce

One short workload run on the assembled core at two revisions. The source reads are the quotations above; these three steps are the build, the observer and the count.

1. Build the workload below and run it on `MinimalConfig` `XiangShanSim` at `92cc48d2cb6af52a1fb2922f9eb99a37918654b3` and at `567d1da56d968a6dde8fbfe829896bc9b9ca2605`. It calls a function whose return target is architecturally rewritten, so the executed return target differs from the speculative RAS target, and it repeats that `16` times. The linker script is part of the reproducer rather than a convenience: it places the return at `0x80000100`, which is the address the observer in step 2 filters on.

<details>
<summary>`return_target_mismatch.S`, exactly as built</summary>

```asm
    .option norvc
    .option norelax

    .section .text.entry, "ax", @progbits
    .globl _start
_start:
    la      t0, unexpected_trap
    csrw    mtvec, t0
    li      s0, 16

subject_loop:
    jal     ra, subject_function
expected_ras_return:
    j       fail

    .section .text.subject, "ax", @progbits
    .globl subject_ret
subject_ret:
    jalr    x0, 0(ra)

    .section .text.unused, "ax", @progbits
    .globl unused_observer_pc
unused_observer_pc:
    nop

    .section .text.rest, "ax", @progbits
subject_function:
    la      ra, subject_landing
    j       subject_ret

    .globl subject_landing
subject_landing:
    addi    s0, s0, -1
    bnez    s0, subject_loop

pass:
    li      a0, 0
    .word   0x0000006b

unexpected_trap:
fail:
    li      a0, 1
    .word   0x0000006b

    .section .tohost, "aw", @progbits
    .p2align 6
    .globl tohost
tohost:
    .dword 0
    .globl fromhost
fromhost:
    .dword 0
```

</details>

<details>
<summary>`integrated_frontend.ld`, exactly as built</summary>

```text
OUTPUT_ARCH(riscv)
ENTRY(_start)

MEMORY
{
  ram (rwx) : ORIGIN = 0x80000000, LENGTH = 1M
}

SECTIONS
{
  .text.entry 0x80000000 : ALIGN(4)
  {
    KEEP(*(.text.entry))
  } > ram

  .text.subject 0x80000100 : ALIGN(4)
  {
    KEEP(*(.text.subject))
  } > ram

  .text.control 0x80000180 : ALIGN(4)
  {
    KEEP(*(.text.control))
  } > ram

  .text.unused 0x800001c0 : ALIGN(4)
  {
    KEEP(*(.text.unused))
  } > ram

  .text.rest 0x80000200 : ALIGN(4)
  {
    *(.text.rest)
    *(.text*)
  } > ram

  .rodata : ALIGN(8)
  {
    *(.rodata*)
  } > ram

  .data : ALIGN(8)
  {
    *(.data*)
    *(.bss*)
  } > ram

  .tohost 0x80020000 : ALIGN(64)
  {
    *(.tohost)
  } > ram
}
```

</details>

Build and run, at each of the two revisions. The recorded ELFs were built by `riscv64-unknown-elf-gcc` 13.2.0 with exactly these arguments:

```sh
riscv64-unknown-elf-gcc -march=rv64gcv_zicsr_zifencei -mabi=lp64d \
  -nostdlib -nostartfiles -static -Wl,--build-id=none \
  -T integrated_frontend.ld -o return_target_mismatch.elf return_target_mismatch.S
make emu CONFIG=MinimalConfig NUM_CORES=1
./build/emu -i return_target_mismatch.elf \
  --diff ready-to-run/riscv64-nemu-interpreter-so \
  --seed 1 --max-cycles 2000000 --ram-size 256MB > emu.log 2>&1
```

<details>
<summary>How the recorded runs drove that build, if you want to reproduce the pinning too</summary>

The runs drove the three phases `make emu` drives, separately, so that the elaboration and the native toolchain could be pinned by digest: `mill xiangshan.test.runMain top.XiangShanSim --config MinimalConfig --issue E.b --num-cores 1 --target chirrtl --enable-difftest --full-stacktrace`, then `firtool` with `--split-verilog -O=release --disable-annotation-unknown --lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none --default-layer-specialization=enable`, then Verilator. Those are the elaboration arguments and the `firtool` options the `Makefile` composes for `emu` at its own defaults with `CONFIG=MinimalConfig NUM_CORES=1`, and splitting at CHIRRTL is the `Makefile`'s own `CHISEL_TARGET=chirrtl` decomposition. Running the phases separately is not a condition for reproducing the counts; `make emu` above is.

</details>

Each revision was also run at `--seed 2`, with every count identical. The finding does not depend on that, and one run reproduces it.

2. The training counts are not visible on a stock build, so the primary reproducer is one observer. Insert these eight lines into `ResolveQueue.scala` immediately after `io.bpuTrain.bits  := mem(deqPtr.value).bits`. They are the same eight lines at both revisions, which is why no revision-specific patch is needed:

```scala
  io.bpuTrain.bits.branches.foreach { branch =>
    branch.bits.debug_realCfiPc.foreach { pc =>
      when(io.bpuTrain.fire && branch.valid &&
        (pc === "h80000100".U || pc === "h80000180".U || pc === "h800001c0".U)) {
        printf(p"P4FEG4_INTS_QUEUE_ACCEPT pc=0x${Hexadecimal(pc)} ftq_idx=${io.bpuTrain.bits.ftqIdx.value} target=0x${Hexadecimal(branch.bits.target.toUInt)} taken=${branch.bits.taken} mispredict=${branch.bits.mispredict} branch_type=${branch.bits.attribute.branchType} ras_action=${branch.bits.attribute.rasAction}\n")
      }
    }
  }
```

`0x80000100` is `subject_ret` under the linker script above; `0x80000180` is the control workload's indirect jump and `0x800001c0` an address the programs never execute, kept as a silence control. `debug_realCfiPc` is present in the difftest build the commands above produce. Then count the accepted training events for the return:

```sh
grep 'P4FEG4_INTS_QUEUE_ACCEPT' emu.log | grep -c 'pc=0x0000080000100'
```

3. At `567d1da56d968a6dde8fbfe829896bc9b9ca2605` that prints `16`. At `92cc48d2cb6af52a1fb2922f9eb99a37918654b3` it prints `1`. That is the finding; everything below corroborates it rather than being needed to see it.

The same run instruments three further seams (`io.toBpu.train.fire` in `Ftq`, `io.fromFtq.train.fire` in `Bpu`, and the `MainBtb` `t0` training consumption) and the checker request itself. All three seams read `16` at the parent and `1` at the tip, and the checker observer is what counts the `16` predicted-taken-no-fault occurrences quoted earlier. Every observer is a `printf` under the condition that already exists at that point: the `fire`, or the `valid`. Nothing is registered, no production signal reads one, and the elaboration is checked for the production wiring (`ftq.io.fromIfu <> ifu.io.toFtq`, `bpu.io.fromFtq <> ftq.io.toBpu`) after the patch is applied.

The control is the same workload with the return replaced by an indirect jump through a register the RAS never sees, so the same `16` iterations run with `hasPop` clear. It reads `16` at both revisions. Its two differing sections are:

```asm
control_loop:
    la      t0, control_landing
    j       control_indirect

    .section .text.control, "ax", @progbits
    .globl control_indirect
control_indirect:
    jalr    x0, 0(t0)
```

## Additional context

<details>
<summary>A secondary observation about what the replacement event can report</summary>

Where the replacement event does exist, the receiver fixes its outcome field:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala#L53
    resolve.bits.mispredict := true.B
```

so the replacement path can only ever report a mispredict, while the removed backend path carried the resolved outcome either way. This is not the loss the report measures, since in the measured case no replacement event exists at all, and it is recorded here only because it bears on what a fix would have to carry.

</details>

### Earlier work on this queue and on this signal that the search did find

The "searched previous issues and PRs" box is ticked with a bound. Related changes exist and are listed below; none reports this finding. The search therefore supports only the statement that no public report of this behaviour was found.

- [#6398](https://github.com/OpenXiangShan/XiangShan/pull/6398), `fix(IFU): ifu redirect canTrain add notCfiTaken signal`, merged 2026-08-21, is the most recent change to the exact lines quoted above. It broadened `canTrain` to include `invalidTaken` and `notCfiTaken`, but left the event itself gated by `checkerRedirect.valid`; it therefore does not cover a direct or return branch for which the checker emits no redirect.
- [#5148](https://github.com/OpenXiangShan/XiangShan/pull/5148), `feat(Bpu,Ftq): bpu train use DecoupledIO`, gave the queue's OUTPUT to the BPU back-pressure, because the BPU can stall training on a predictor bank conflict. It is about the `ResolveQueue` to `Bpu` side, not the producer side, so it does not by itself argue that the IFU input should be `Decoupled`.
- [#5107](https://github.com/OpenXiangShan/XiangShan/pull/5107), `fix(resolve queue): only valid entries can be flushed`, and [#5273](https://github.com/OpenXiangShan/XiangShan/pull/5273), on flush conflicts between the BPU and redirect paths.

### What this report does not establish

- No workload occurrence frequency and no performance magnitude. How often a program presents the state, and what it costs, are not measured. The measurement is for this return workload and does not extend to every direct or return class.
- No every-class regression. At the parent, `shouldDrop` ([`ResolveQueue.scala:100`](https://github.com/OpenXiangShan/XiangShan/blob/567d1da56d968a6dde8fbfe829896bc9b9ca2605/src/main/scala/xiangshan/frontend/ftq/ResolveQueue.scala#L100)) already drops a correctly-predicted direct resolve regardless of the counter and rate-limits the rest under saturation, so the parent-versus-tip comparison here is a return-path statement and nothing is claimed about direct branches at the parent.

### Model and framework information

AI assistance: Claude Opus 5, through Claude Code, under human direction. Every quotation and count here is re-checked against the cited revision or transcript by a program.

### Suggested direction

Decide explicitly whether a return that the checker does not redirect on should still train. If it should, the backend filter needs a condition rather than being unconditional, or the IFU path needs to produce an event without requiring a redirect. Those two are not interchangeable: the receiver fixes the replacement event's `mispredict` field to `true.B`, as recorded under "Additional context", so the IFU path cannot preserve the backend resolve's distinction between a correct and a mispredicted outcome. If it should not, the change is deliberate and this report reduces to a request for a comment on the filter.

No patch is proposed here because which of the two is intended is a design decision.
