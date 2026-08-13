### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

XiangShan appears to perform the final physical PMP check for `HLV.WU` using the current M-mode PMP bypass behavior instead of the effective privilege selected by `hstatus.SPVP`.

According to the privileged specification, HLV/HLVX/HSV explicit memory accesses use VS or VU effective privilege according to `hstatus.SPVP`; `mstatus.MPRV` does not affect these instructions. 

In the test case:
- current privilege is M-mode
- `hstatus.SPVP = 0`, so the HLV memory access should use VU effective privilege
- `vsatp = 0` and `hgatp = 0`, so the HLV address reaches the target physical page directly
- `PMP0 = unlocked NAPOT no-access` over `target_data`
- `PMP1 = NAPOT RWX allow-all`

Because the target page is denied to VU effective privilege, `HLV.WU` should fault even though the instruction is executed from M-mode. Instead, `HLV.WU` reads the protected word and reaches the explicit bug exit. In this testcase, `HIT GOOD TRAP` only means the testcase reached `XS_EXIT`; the PC decides whether that exit is the failure path.

Observed result:

```text
Core 0: HIT GOOD TRAP at pc = 0x80000090
```

That PC maps to the exit after the PMP-denied word was read:

```text
0x8000006a: HLV.WU t1, (t0)
0x80000078: beq t1,t2,0x8000008c <bug_loaded_secret>
0x8000008c: li a0,94
0x80000090: XS_EXIT
```

I also checked two local controls to validate the oracle: the same `HLV.WU` faults when the deny entry is locked, and succeeds when the target PMP entry is explicitly RWX-allowed. Those controls are not needed to trigger the bug; they only confirm the testcase setup.

### Expected behavior

For `hstatus.SPVP = 0`, the final PMP check for `HLV.WU` should use VU effective privilege. `HLV.WU` should raise load access fault (`mcause = 5`) when the target page is covered by an unlocked PMP no-access entry.


### Environment

- XiangShan branch: `kunminghu-v3`
- Observed XiangShan commit: `4c742fa44b76fe372f70c74aad2ca826be0de155`
- Emulator: `build/verilator-compile/emu`
- Run mode: `--no-diff`
- Local emulator banner also reports `dirty: 1`

### To Reproduce

Run the command:

```bash
timeout 120 cores/XiangShan/build/verilator-compile/emu \
  -i ./elfs/xs_hlv_spvp_u_unlocked_pmp_deny_load_must_fault.elf \
  --no-diff -C 500000
```

Expected local output showing the bug path:

```text
Core 0: HIT GOOD TRAP at pc = 0x80000090
```

### Additional context

[appendix.zip](https://github.com/user-attachments/files/29280647/appendix.zip)
