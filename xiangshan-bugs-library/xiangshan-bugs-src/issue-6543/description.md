### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


A DiffTest hang was observed between XiangShan and NEMU. After the `vsetvli` at `0x8000102c` commits, the DUT stops committing instructions entirely. After 15,000 cycles of inactivity, the DiffTest framework lets NEMU run one more instruction, which is the next (never-committed) instruction at `0x80001030`:

```
8000102c: 09a2f057   vsetvli  zero, t0, e64, m4, tu, ma   # AVL = t0 = 6  ->  vl = 6
80001030: 878b6257   vdiv.vx  v4, v24, s6                 # scalar divisor s6 = 0
```

The scalar divisor `s6` (x22) is never written by the test program, so it is 0 on both sides (the architectural register dump confirms `s6: 0x0000000000000000`, and no integer register mismatch is reported). NEMU executes the `vdiv.vx` normally and, per the divide-by-zero semantics, writes all 1s to the destination register group. The DUT never completes the instruction, so DiffTest reports a mismatch on exactly the registers the stuck instruction was supposed to write:

```
 v4_low different: right = 0xffffffffffffffff, wrong = 0x3ff8a3ad67d27cc6
 v4_high different: right = 0xffffffffffffffff, wrong = 0xbaf870bd6bd934fe
 v5_low different: right = 0xffffffffffffffff, wrong = 0xdc676cccfec06caa
 v5_high different: right = 0xffffffffffffffff, wrong = 0x851002dc1df06484
 v6_low different: right = 0xffffffffffffffff, wrong = 0x55781dbb3ceb10aa
 v6_high different: right = 0xffffffffffffffff, wrong = 0x5f6d100c5a4a24e8
```

The "wrong" values are simply the stale data loaded into v4–v6 by the earlier `vl1re8.v` initialization; the DUT's divide unit never wrote anything. The mismatch landing exactly on v4, v5 and v6 is a symptom of the hang rather than data-path corruption: with `e64, m4, vl=6` on VLEN=128, the six active body elements (two per register) occupy exactly v4, v5 and v6, while v7's elements are tail elements and `vta=tu` keeps them undisturbed, which is why v7 does not mismatch.

The deadlock is triggered solely by the zero divisor. Inserting `li s6, 1` before the `vdiv.vx` (keeping `e64, m4, vl=6` unchanged) makes the test pass with HIT GOOD TRAP, while reducing LMUL to m1 with the divisor still zero still deadlocks (only v4 mismatches in that configuration). The trigger is therefore unrelated to LMUL or uop splitting.

As specified in the RISC-V Vector Extension, Section "Vector Integer Divide Instructions":

> The divide and remainder instructions are equivalent to the RISC-V standard scalar integer multiply/divides, with the same results for extreme inputs.

And as specified in the RISC-V M Standard Extension for the referenced scalar semantics:

> The quotient of division by zero has all bits set, and the remainder of division by zero equals the dividend.

So a `vdiv.vx` with a zero divisor is a legal, fully specified operation: it must complete and write all 1s to the active body elements of the destination register group. XiangShan instead stops making forward progress entirely, which is a deadlock — the instruction neither retires with the architecturally defined result nor raises any exception.

The DiffTest report is as follows：

[seeds_1_.log](https://github.com/user-attachments/files/32047142/seeds_1_.log)


### Expected behavior

A `vdiv.vx` whose scalar divisor is zero must execute normally and retire with the architecturally defined result: the quotient written to the active body elements of the destination register group must have all bits set (per the scalar divide-by-zero semantics that vector divide inherits), tail elements must remain undisturbed under `vta=tu`, and the core must continue committing subsequent instructions. XiangShan must not stall or deadlock on this input.


### Environment

- Repo
    - XiangShan commit id: `eedc9aa511`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_1_.zip](https://github.com/user-attachments/files/32047217/seeds_1_.zip)

### Additional context

_No response_
