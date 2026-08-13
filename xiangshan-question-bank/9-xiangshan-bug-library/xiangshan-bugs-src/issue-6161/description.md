### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug


XiangShan appears to allow a page-table walk to consume an 8-byte PTE even when the lowest-numbered matching TOR PMP entry covers only half of that PTE.

According to the privileged specification:

- if accessing a PTE during page-table walk violates PMP, the core must raise an access-fault corresponding to the original access type;
- all implicit accesses to page-table data structures are performed using width `PTESIZE`;
- the lowest-numbered matching PMP entry must match all bytes of a memory operation, or the operation fails.

In the provided representative testcase:

- the final Sv39 leaf PTE sits at the last 8-byte slot of the L0 page table;
- a lower-numbered TOR entry covers only the high 4-byte half of that 8-byte PTE;
- a lower-priority allow-all entry covers the rest of memory;
- the original access is an S-mode store that requires PTW to read that PTE.

Because the PTW is performing an implicit 8-byte PTE access, partial TOR coverage should make the PTW access fail and the original store should raise store access fault (`mcause = 7`).

Instead, the store retires without taking the expected PTW PMP fault, reaches the fallback no-trap path, and the post-access readback confirms that `target_data` was modified.

The key PMP/PTE setup fragment is:

```text
    # Lower-priority allow-all plus a lower-numbered TOR entry covering only
    # the high 4-byte half of the 8-byte data PTE.
    la      t0, l0_data_pt
    li      t1, DATA_VA
    srli    t1, t1, 12
    andi    t1, t1, 0x1ff
    slli    t1, t1, 3
    add     t0, t0, t1
    addi    t0, t0, PARTIAL_OFFSET
    srli    t1, t0, 2
    csrw    pmpaddr0, t1
    addi    t0, t0, 4
    srli    t1, t0, 2
    csrw    pmpaddr1, t1
```

This places the final leaf PTE for `DATA_VA` at an 8-byte slot where the lowest-numbered TOR entry covers only the upper 4 bytes of that PTE. The PTW must still treat the PTE read as one 8-byte memory operation.

The key program fragment is:

```text
smode_probe:
    li      t0, DATA_VA
    sd      s3, 0(t0)
    li      a0, 10
    ecall
```

This `sd` should never reach the `ecall` fallback path. If PTW correctly faults on the partially covered 8-byte PTE read, the original S-mode store must trap before software reaches the post-store path at all.

Observed result:

```text
Core 0: HIT GOOD TRAP at pc = 0x80000368
```

That PC maps to the explicit side-effect failure exit in the reproducer:

```text
0x80000364 <psm_oracle_fail_store_side_effect>:
0x80000368: XS_EXIT
```

I also checked a whole-PTE deny control in the same family. That control exits at:

```text
Core 0: HIT GOOD TRAP at pc = 0x80000280
```

and `0x80000280` maps to the `psm_oracle_pass` exit for the control ELF, showing that the oracle distinguishes the good and bad cases.

### Expected behavior

For a PTW implicit access to an 8-byte PTE, if the lowest-numbered matching TOR PMP entry covers only part of that 8-byte access, the PTW memory operation should fail.

The original S-mode store should therefore raise store access fault (`mcause = 7`), and `target_data` should remain unchanged.

### Environment

- XiangShan branch: `kunminghu-v3`
- Observed XiangShan commit: `4c742fa44b76fe372f70c74aad2ca826be0de155`
- Emulator: `cores/XiangShan/build/verilator-compile/emu`
- Run mode: `--no-diff`
- Local emulator banner reports `dirty: 1`
- Latest master reproduction status in the current workspace: not yet verified

### To Reproduce


I provided the following files in the appendix .zip package.

- `ptw_pte_partial_high_store_readback.S`
- `ptw_pte_partial_high_store_readback.elf`
- `control_whole_pte_load.elf`

Run:

```bash
timeout 120 ./XiangShan/build/verilator-compile/emu \
  -i ptw_pte_partial_high_store_readback.elf \
  --no-diff -C 500000
```

The bug is reproduced if the output shows:

```text
Core 0: HIT GOOD TRAP at pc = 0x80000368
```

That `pc` is the explicit `psm_oracle_fail_store_side_effect` exit in the representative ELF, so it means:
- the original S-mode store did not take the expected PTW PMP fault; and
- the fallback post-access readback observed that `target_data` changed.

### Additional context

Source Code and elf: [appendix.zip](https://github.com/user-attachments/files/29428605/appendix.zip)
