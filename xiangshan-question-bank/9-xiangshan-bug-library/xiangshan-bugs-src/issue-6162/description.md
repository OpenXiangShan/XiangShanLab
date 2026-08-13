### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

XiangShan appears to admit an aligned full-width data access across a TOR granularity boundary even though the lowest-numbered matching TOR PMP entry does not cover all bytes of the access.

According to the privileged specification:

- the lowest-numbered matching PMP entry determines whether a memory operation succeeds or fails;
- the matching PMP entry must match all bytes of the memory operation, or the operation fails;
- the example in the specification explicitly says that if a PMP entry matches only bytes `0xC`-`0xF`, then an 8-byte access to `0x8`-`0xF` must fail.

In the provided representative testcase:

- `granularity_cross_addr` is naturally 8-byte aligned at `0x80001800`;
- the access is a plain aligned `sd`, not a misaligned split store;
- the first TOR boundary is placed at `granularity_cross_addr + 4`, so the lowest-numbered matching TOR entry covers only the upper half of the 8-byte access;
- a lower-priority allow-all entry covers the rest of memory.

Because the deny entry does not cover all bytes of the aligned 8-byte store, the store should fail with store access fault (`mcause = 7`) before retire.

Instead, the store reaches the explicit side-effect failure path and the post-store readback shows that memory changed.

The key program fragment is:

```text
access_probe:
    sd      s3, 0(s0)
    la      t0, observed_reg
    sd      s2, 0(t0)
    li      a0, 10
    ecall
```

Here `s0` points at the naturally aligned `granularity_cross_addr`. This is not a misaligned split-store case. The aligned `sd` itself should fault before the program can fall through to the no-trap / side-effect check path.

The key TOR setup fragment is:

```text
    # A target with G=1 must not treat pmpaddr[0]=1 as a 4-byte TOR
    # boundary. A full-width access at granularity_cross_addr should not be
    # accepted by checking only the access start address.
    la      t0, granularity_cross_addr
    addi    t0, t0, BOUNDARY_OFFSET_BYTES
    srli    t0, t0, 2
    csrw    pmpaddr0, t0
    la      t0, denied_window_end
    srli    t0, t0, 2
    csrw    pmpaddr1, t0
```

This makes the lowest-numbered TOR entry begin 4 bytes into the aligned 8-byte store, so the entry matches only half of that full-width access. Under the specification, the store should fail rather than retire.

Observed result:

```text
Core 0: HIT GOOD TRAP at pc = 0x800001d4
```

That PC maps to the failure exit after committed side effect:

```text
0x800001d0 <psm_oracle_fail_store_side_effect>:
0x800001d4: XS_EXIT
```

I also checked an allow control in the same harness family. That control exits at:

```text
Core 0: HIT GOOD TRAP at pc = 0x80000178
```

and `0x80000178` is the `XS_EXIT` inside `psm_oracle_pass` for the control ELF.

### Expected behavior

The aligned 8-byte store at `granularity_cross_addr` should fail because the lowest-numbered matching TOR entry covers only part of the access.

The testcase should raise store access fault (`mcause = 7`) and should not modify `granularity_cross_addr`.

### Environment

- XiangShan branch: `kunminghu-v3`
- Observed XiangShan commit: `4c742fa44b76fe372f70c74aad2ca826be0de155`
- Emulator: `cores/XiangShan/build/verilator-compile/emu`
- Run mode: `--no-diff`
- Local emulator banner reports `dirty: 1`
- Latest master reproduction status in the current workspace: not yet verified

### To Reproduce


Representative testcase source and current local artifacts:

- `tor_granularity_followup.S`
- `tor_granularity_store8_smode_control.elf`
- `tor_granularity_load8_allow_control.elf`

Run:

```bash
timeout 120 ./XiangShan/build/verilator-compile/emu \
  -i tor_granularity_store8_smode_control.elf \
  --no-diff -C 500000
```

The bug is reproduced if the output shows:

```text
Core 0: HIT GOOD TRAP at pc = 0x800001d4
```

That `pc` is the explicit `psm_oracle_fail_store_side_effect` exit in the
representative ELF, so it means:

- the aligned `sd` did not take the expected store access fault; and
- the post-store readback observed a committed side effect on
  `granularity_cross_addr`.


### Additional context

Source code and elfs: [appendix.zip](https://github.com/user-attachments/files/29428705/appendix.zip)
