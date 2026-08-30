# JumpUnit compressed `c.jr` backend redirect clears `isRVC`

## Before start

- I have read the RISC-V ISA Manual and this is not a RISC-V ISA question.
- I have read the XiangShan Documents.
- I have searched previous issues and did not find this `JumpUnit` path reported.
- I have reviewed nearby issue/PR patterns. This is related to compressed redirect metadata, but it is not the same path as the reported `BranchUnit` compressed conditional-branch issue.
- I reproduced the behavior on `kunminghu-v3` commit `064f8462a6bfc13994099e2eb70c63fa5f63b85b` with an observation-only monitor.

## Branch

`kunminghu-v3`

## Describe the bug

`JumpUnit` can produce a valid backend redirect for a compressed jump/JALR-family instruction while leaving `redirect.bits.isRVC` cleared.

Minimal instruction stream:

```asm
.option rvc

_start:
  auipc t0, 0
  addi  t0, t0, 16

victim_cjr:
  c.jr  t0
  c.nop
  c.nop
  c.nop
  c.nop

target:
  li t0, 0x60000000
  li t1, 1
  sd t1, 0(t0)
done:
  c.j done
```

The relevant source path is:

```scala
// src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
private val isRVC = io.in.bits.ctrl.isRVC.get

val redirect = io.out.bits.res.redirect.get.bits
val redirectValid = io.out.bits.res.redirect.get.valid
redirectValid := io.in.valid && !jumpDataModule.io.isAuipc && (needRedirect || redirect.hasBackendFault)
redirect := 0.U.asTypeOf(redirect)
redirect.ftqOffset := io.in.bits.ctrl.ftqOffset.get
redirect.target := jumpDataModule.io.target
redirect.pc := io.in.bits.data.pc.get
redirect.isMisPred := needRedirect
```

`JumpUnit` reads `isRVC`, but never assigns:

```scala
redirect.isRVC := isRVC
```

Because the redirect bundle is zeroed first, a compressed `c.jr` redirect carries `redirect.isRVC = 0`.

That bit is used by redirect PC reconstruction:

```scala
// src/main/scala/xiangshan/Bundle.scala
def getPcOffset() = {
  val ftqOffset = (this.ftqOffset << instOffsetBits).asUInt
  val rvcOffset = Mux(this.isRVC, 0.U, 2.U)
  val thisPcOffset = SignExt(ftqOffset -& rvcOffset, VAddrBits)
  thisPcOffset
}
```

So a compressed JumpUnit redirect can be interpreted as if it came from a 32-bit instruction.

## Expected behavior

For a compressed jump/JALR-family instruction, if `JumpUnit` emits a backend redirect, the redirect should preserve the decoded compressed-instruction bit:

```scala
redirect.isRVC := io.in.bits.ctrl.isRVC.get
```

For the PoC below, the compressed `c.jr` is at fetch-block base `0x80000000`, `ftqOffset = 3`, which corresponds to CFI PC `0x80000006`. The redirect should not report `isRVC = 0`.

## Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `064f8462a6bfc13994099e2eb70c63fa5f63b85b`
- RISC-V GCC used for PoC: `/opt/homebrew/bin/riscv64-elf-gcc`
- Emulator: local Verilator `emu`, rebuilt with an observation-only `printf` monitor in `JumpUnit.scala`

## To Reproduce

Build the ELF:

```bash
riscv64-elf-gcc \
  -nostdlib -nostartfiles \
  -march=rv64gc_zicsr_zifencei -mabi=lp64 \
  -T link.ld \
  -Wl,--no-relax \
  -o cjr_jumpunit_isrvc.riscv \
  instruction_stream.S

riscv64-elf-objdump -dr cjr_jumpunit_isrvc.riscv \
  > cjr_jumpunit_isrvc.disasm
```

The important disassembly is:

```text
80000000: 00000297           auipc t0,0x0
80000004: 02c1               addi  t0,t0,16 # 80000010 <target>
80000006: 8282               jr    t0
80000008: 0001               nop
8000000a: 0001               nop
8000000c: 0001               nop
8000000e: 0001               nop
80000010: 600002b7           lui   t0,0x60000
80000014: 4305               li    t1,1
80000016: 0062b023           sd    t1,0(t0)
8000001a: a001               j     8000001a
```

Run the emulator:

```bash
emu \
  -i cjr_jumpunit_isrvc.riscv \
  --no-diff \
  -C 3000 -b 0 -e 3000 \
  > run.log 2>&1
```

Observation-only monitor:

```scala
when (io.in.valid && isRVC) {
  printf(
    "[XSBUG_JUMP_ISRVC_OBS] pc=0x%x target=0x%x ftqOffset=%d redirectValid=%d redirect_isRVC=%d needRedirect=%d targetWrong=%d fixedTaken=%d predTaken=%d\n",
    io.in.bits.data.pc.get,
    jumpDataModule.io.target,
    io.in.bits.ctrl.ftqOffset.get,
    redirectValid,
    redirect.isRVC,
    needRedirect,
    targetWrong,
    fixedTaken,
    predTaken
  )
}
```

Reproduced result:

```text
[XSBUG_JUMP_ISRVC_OBS] pc=0x0000080000000 target=0x0000000080000010 ftqOffset= 3 redirectValid=1 redirect_isRVC=0 needRedirect=1 targetWrong=1 fixedTaken=1 predTaken=0
```

This means:

- the compressed JumpUnit input was dynamically reached;
- a real backend redirect was emitted: `redirectValid = 1`;
- the redirect was due to prediction repair: `needRedirect = 1`, `targetWrong = 1`;
- the redirect bundle still carried `redirect_isRVC = 0`.

## Suggested fix

In `JumpUnit.scala`, assign the missing metadata:

```scala
redirect.isRVC := isRVC
```

Then add a regression where compressed `c.jr` or `c.jalr` produces a backend redirect and verifies `redirect.bits.isRVC`.
