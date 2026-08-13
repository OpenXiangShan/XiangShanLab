### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

When software clears `SBPCTL.ABTB_ENABLE`, XiangShan disables the ABTB frontend predictor, but the dependent `uTage` predictor still issues SRAM bank reads. A train-then-disable variant also shows that these disabled-ABTB `uTage` reads can block pending `uTage` table writes when the read and write target the same bank.

In the minimal testcase, software writes `0x7d` to CSR `0x5c0`, clearing only `ABTB_ENABLE` from the reset value `0x7f`, executes `fence.i`, and then runs a tight branch loop to keep frontend prediction active:

```text
li      t0, 0x7d
csrw    0x5c0, t0
fence.i
li      s0, 512

branch_loop:
    addi    s1, s1, 1
    addi    s0, s0, -1
    bnez    s0, branch_loop
```

The independently parsed VCD shows concrete `uTage` SRAM read-clock activity while ABTB is disabled and frontend stage 0 is active. First reproduced events:

```text
time = 7800
table = 0, bank = 2
abtb_io_enable              = 0
bpu_sramResetDone           = 1
bpu_s0_fire                 = 1
utage_table_sramResetDone   = 1
utage_bank_resetDone        = 1
utage_bank_io_r_req_valid   = 1
utage_bank_rckEn            = 1

time = 7800
table = 1, bank = 0
abtb_io_enable              = 0
bpu_sramResetDone           = 1
bpu_s0_fire                 = 1
utage_table_sramResetDone   = 1
utage_bank_resetDone        = 1
utage_bank_io_r_req_valid   = 1
utage_bank_rckEn            = 1
```

The independent monitor result is:

```text
predicate_result      = reproduced
event_count           = 16
missing_core_signals  = []
tables_found          = [0, 1]
bank_groups_found     = 8
```

I also ran a train-then-disable testcase that first warms the predictor, then clears only `ABTB_ENABLE`. The write-buffer analyzer found pending `uTage` writes while ABTB was disabled:

```text
abtb_disabled_samples             = 737
disabled_try_write                = 10
disabled_blocked_write            = 9
disabled_samebank_blocked_write   = 9
```

The first same-bank blocked write was:

```text
time                          = 8801
table                         = 0
abtb_enable                   = 0
read_index                    = 0x160
read_bank                     = 0
read_bank_r_req_valid         = 1
write_index                   = 0x20
write_bank                    = 0
write_bank_r_req_valid        = 1
write_bank_w_req_valid        = 0
force_write                   = 0
write_success                 = 0
```

The likely root cause is that `ABTB_ENABLE` reaches `Bpu.scala` as `ctrl.abtbEnable` and gates `abtb.io.enable`, but `uTage` is not gated by the same control:

```scala
// Bpu.scala
utage.io.enable := true.B
abtb.io.enable  := ctrl.abtbEnable
```

In this checkout, the `utageEnable` control field is commented out in the BPU control bundle. `MicroTage.scala` then drives table requests as always valid:

```scala
t.req.valid := true.B
```

and `MicroTageTable.scala` drives bank read requests from the bank one-hot selection without qualifying by `io.req.valid` or predictor enable:

```scala
bank.io.r.req.valid := bankOH(bankIdx)
```

This matches the observed write-buffer side effect: when the pending write targets the same bank and `forceWrite` is false, `writeSuccess` stays false while the disabled-ABTB prediction read still asserts the selected bank read request.

This issue is adjacent to ABTB-disable gating issues, but it is not the same signal path as ABTB bank reads. This testcase observes `uTage` table bank read-valid plus `rckEn` activity, and the train-then-disable testcase observes `uTage` table write-buffer interference.

### Expected behavior

When `SBPCTL.ABTB_ENABLE` disables ABTB, dependent `uTage` prediction-table reads should also be suppressed, or at least no `uTage` SRAM bank should receive an accepted/read-clocked prediction read solely because frontend stage 0 is active.

In particular, while:

```text
abtb_io_enable = 0
bpu_s0_fire    = 1
```

the design should not assert:

```text
utage_bank_io_r_req_valid = 1
utage_bank_rckEn          = 1
```

for normal prediction-table reads.

It also should not be possible for a disabled-ABTB prediction read to block a pending `uTage` table write to the same bank:

```text
abtb_enable            = 0
read_bank              = write_bank
read_bank_r_req_valid  = 1
force_write            = 0
write_success          = 0
```

`uTage` is used as an ABTB correction predictor. If ABTB is disabled, these reads cannot contribute useful ABTB-based prediction, but they still toggle predictor SRAM state and can contend with the table write path.

### Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `3931c5112c528299a23c256bdd77fb90813afa6e`

### To Reproduce

The attachment [xiangshan-utage-abtb-disabled-read-writebuffer-attachment.zip](https://github.com/user-attachments/files/29404266/xiangshan-utage-abtb-disabled-read-writebuffer-attachment.zip) contains the bare-metal testcases, linker script, run scripts, VCD monitors, prebuilt ELF/bin, objdump, run logs, monitor outputs, and checksums. The large VCD files are intentionally not included; they are regenerated by the run scripts.

Run the minimal read-clock reproducer:

```bash
cd xiangshan-utage-abtb-disabled-read-writebuffer-attachment

EMU=/path/to/XiangShan/build/emu \
DIFF=/path/to/XiangShan/ready-to-run/riscv64-nemu-interpreter-so \
PREFIX=validation_utage_abtb_disabled_read \
CYCLES=9000 \
WAVE_BEGIN=7800 \
WAVE_END=9000 \
./run_poc.sh
```

Expected output from the monitor:

```text
predicate_result = reproduced
```

The attached validation log was run with difftest enabled. It terminates only because the testcase reaches the configured cycle limit in the terminal loop:

```text
The first instruction of core 0 has commited. Difftest enabled.
Core 0: EXCEEDING CYCLE/INSTR LIMIT at pc = 0x80000046
Core-0 instrCnt = 4394, cycleCnt = 9000, IPC = 0.488222
```

Run the train-then-disable write-buffer reproducer:

```bash
EMU=/path/to/XiangShan/build_vcd/emu \
PREFIX=utage_train_then_disable_buildvcd \
TRAIN_ITERS=345 \
CYCLES=9500 \
WAVE_BEGIN=8000 \
WAVE_END=9500 \
./run_writebuffer_poc.sh
```

Expected write-buffer evidence:

```text
disabled_try_write              = 10
disabled_blocked_write          = 9
disabled_samebank_blocked_write = 9
```

### Additional context

_No response_
