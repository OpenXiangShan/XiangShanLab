### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

XiangShan appears to update `mepc` and `mtval` with the post-`satp` Sv39-formatted PC for the first fetch fault after a `satp` write, instead of reporting the faulting PC in the pre-flush context.

The attached testcase enters S-mode at bare physical PC `0x0000004000000000` while `satp` is still Bare. The S-mode payload then executes:

```text
0x0000004000000000: csrw satp, a0
0x0000004000000004: addi t0, zero, 0x55
```

`a0` contains an Sv39 `satp` value with an empty root page table. The first fetch after the `csrw satp` faults at `0x0000004000000004`. In this address, Sv39 bit 38 is set. If the trap PC is formatted using the new Sv39 mode, the value becomes `0xffffffc000000004`; if it is reported using the pre-flush Bare context, the value is `0x0000004000000004`.

The XiangShan run reports the sign-extended value in both `mepc` and `mtval`:

```text
[30] commit pc 0000004000000000 inst 18051073 ... csrw    satp, a0
[31] exception pc ffffffc000000004 inst 00000000 cause 000000000000000c c.unimp <--
mepc different at pc = 0x0080000024, right = 0x0000004000000004, wrong = 0xffffffc000000004
mtval different at pc = 0x0080000024, right = 0x0000004000000004, wrong = 0xffffffc000000004
Core 0: ABORT at pc = 0x80000024
```

The independent VCD monitor also observes:

```text
predicate                 = reproduced
satp mode                 = Sv39
instruction page fault    = observed
mcause                    = 12
trapToM/diff mepc         = 0xffffffc000000004
trapToM/diff mtval        = 0xffffffc000000004
expected reference value  = 0x0000004000000004
```

I also searched the local source for the metadata that would distinguish this first fetch after a `satp` flush. In this checkout, I did not find `satpFlush`, `satpFlushFirstFetchFault`, `oldSatp`, or `oldVsatp` fields under `src/main/scala/xiangshan`. The current M-mode trap-entry path computes `trapPC` from the live CSR state:

```scala
private val trapPC = genTrapVA(
  iMode,
  satp,
  vsatp,
  hgatp,
  in.trapPc,
)
```

and then writes `mepc` and `mtval` from that value. This appears to lose the provenance that the exception belongs to the first fetch after a `satp`-flush redirect.

### Expected behavior

For the first fetch fault caused by a `satp`-flush redirect, trap-entry logic should preserve enough old context to report the correct faulting instruction address. In this testcase, `mepc` and `mtval` should be:

```text
0x0000004000000004
```

They should not be:

```text
0xffffffc000000004
```

### Environment

- XiangShan branch: `kunminghu-v3`
- XiangShan commit: `3931c5112c528299a23c256bdd77fb90813afa6e`

### To Reproduce

The attachment [xiangshan-satp-first-fetch-trapva-attachment.zip](https://github.com/user-attachments/files/29403534/xiangshan-satp-first-fetch-trapva-attachment.zip) contains `poc.S`, `linker.ld`, `validate_satp_first_fetch.py`, prebuilt `poc.elf`/`poc.bin`, objdump, reproduced logs, monitor output, and `validation_satp_fault_1450_1560.vcd.gz`.

Build the testcase:

```bash
riscv64-unknown-elf-gcc \
  -march=rv64gc -mabi=lp64 -mcmodel=medany \
  -nostdlib -nostartfiles \
  -T linker.ld \
  -o poc.elf \
  poc.S

riscv64-unknown-elf-objcopy -O binary poc.elf poc.bin
riscv64-unknown-elf-objdump -d -s poc.elf > poc.objdump
```

Run a difftest reproduction without waveform dumping:

```bash
XS_EMU=/path/to/XiangShan/build/emu
XS_DIFF=/path/to/XiangShan/ready-to-run/riscv64-nemu-interpreter-so

$XS_EMU \
  --max-cycles=20000 \
  -b 0 -e 0 \
  --dump-commit-trace \
  -i poc.bin \
  --diff "$XS_DIFF" \
  > validation_nodump_diff.log 2>&1
```

Run the waveform reproduction and monitor:

```bash
$XS_EMU \
  --max-cycles=1700 \
  -b 1450 -e 1560 \
  --dump-wave-full \
  --wave-path=validation_satp_fault_1450_1560.vcd \
  --dump-commit-trace \
  -i poc.bin \
  --diff "$XS_DIFF" \
  > validation_wave_diff.log 2>&1

python3 validate_satp_first_fetch.py \
  validation_satp_fault_1450_1560.vcd \
  validation_wave_diff.log \
  validation_monitor.json \
  > validation_monitor.log
```

Alternatively, to inspect the attached reproduced waveform without rerunning the emulator:

```bash
gzip -dk validation_satp_fault_1450_1560.vcd.gz

python3 validate_satp_first_fetch.py \
  validation_satp_fault_1450_1560.vcd \
  validation_wave_diff.log \
  validation_monitor.recheck.json
```

Expected local output from the monitor:

```text
"predicate": "reproduced"
"expected_bare_fault_pc": "0x0000004000000004"
"observed_wrong_sv39_fault_pc": "0xffffffc000000004"
```

### Additional context

The raw VCD is included as `validation_satp_fault_1450_1560.vcd.gz`. The commands above also regenerate it. The included `validation_monitor.json`, `validation_wave_diff.log`, and `validation_nodump_diff.log` are the reproduced local outputs.

This looks related to the same bug family as PR #5860 (`fix(csr, satp): fix the update logic of xepc and xtval`), whose description mentions saving old `satp/vsatp` mode and using a `satpFlushFirstFetchFault` marker to update `epc/tval/tval2` with the old context. The observed checkout still lacks those fields and reproduces the mismatch above.
