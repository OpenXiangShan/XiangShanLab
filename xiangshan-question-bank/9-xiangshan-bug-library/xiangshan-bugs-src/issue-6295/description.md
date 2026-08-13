### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. A `vle32ff.v v11, (s5)` instruction at `0x800011ec` with `s5=0x10249cc78be16559` raises a Load Access Fault because the address is not a valid physical address. NEMU correctly traps to M-mode and **preserves the original value of v11**. XiangShan also traps, but **zeroes out v11** and records a nonsensical `vstart=86` (`vl=4`).

The faulting address `0x10249cc78be16559` is an invalid physical address that fails the PMA check:
```
isa pma check failed, vaddr=0x10249cc78be16559, paddr=0x10249cc78be16559, len=0x4, type=0x1, mode=0x3
```

The instruction encoding `0x028ae587` decodes as `vle32ff.v v11, (s5)` (unit-stride fault-only-first, EEW=32, unmasked). The vector state at the time of execution is `vtype=0xd0` (vsew=010=SEW32, vlmul=000=m1, vta=1, vma=1) with `vl=4`, so four 32-bit elements are to be loaded into v11 (VLEN=256, 8 elements per register).

After the fault, DiffTest reports:

```
v11_low different: right=0x35980664f958d130, wrong=0x0000000000000000
v11_high different: right=0x740d0003053da60e, wrong=0x0000000000000000
 vstart different: right=0x0000000000000000, wrong=0x0000000000000056
```

- **NEMU**: traps to M-mode (`mcause=5`, Load Access Fault), v11 retains its original value, vstart=0.
- **XiangShan**: v11 is zeroed entirely, vstart is set to 86 (`0x56`) — an impossible value given `vl=4`.

As specified in the RISC-V V Extension, Section "Precise Traps":

> The V vector extension has precise traps.

For fault-only-first loads (Section "Vector Loads and Stores"):

> If element 0 raises an exception, csr:vl[] is not modified, and the trap is taken.

And regarding vstart on traps (Section "Vector Start Index CSR"):

> Normally, vstart is only written by hardware on a trap on a vector instruction, with the vstart value representing the element on which the trap was taken (either a synchronous exception or an asynchronous interrupt), and at which execution should resume after a resumable trap is handled.

Taken together, these three rules require that when element 0 of a `vle32ff.v` raises an access fault, the trap is taken immediately and precisely: `vstart` must be set to 0 (the index of the faulting element), and `vl` must be left unchanged at 4.

XiangShan violates this. The reported `vstart=86` is impossible given `vl=4` — there is no element 86 to have faulted on. This indicates the trap was taken **imprecisely**: the hardware processed 86 elements (far exceeding `vl`) before detecting the access fault, and in doing so corrupted v11 (zeroed all elements, including body elements 0–3 that should have triggered the immediate trap).

The DiffTest report is as follows：

[seeds_301.log](https://github.com/user-attachments/files/30452474/seeds_301.log)

### Expected behavior

A `vle32ff.v` that raises a Load Access Fault on element 0 must take the trap precisely: `vstart` must be set to 0 and `vl` must be left unchanged. XiangShan must not continue executing elements beyond the faulting element, and must not corrupt destination registers before the trap is taken.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_301.zip](https://github.com/user-attachments/files/30452492/seeds_301.zip)

### Additional context

_No response_
