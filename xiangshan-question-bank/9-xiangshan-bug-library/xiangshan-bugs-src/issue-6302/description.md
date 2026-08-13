### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. At PC `0x80001ba4`, the instruction `0x5e003457` (`vmv.v.i v8, 0`) causes a divergence: **NEMU raises an illegal instruction exception** (`mcause=2`), while **XiangShan commits the instruction** normally.

NEMU's exception state:

```
mcause: 2 (illegal instruction)
mepc:   0x80001ba4
mtval:  0x5e003457
```

XiangShan's commit trace:

```
[31] commit pc 0000000080001ba4 inst 5e003457 wen 1 dst 32 data 917af882bf50788e idx 010
```

The divergence is detected later at PC `0x80001714` when DiffTest compares CSR state:

```
mcause different at pc = 0x80001714, right = 0x2 (NEMU), wrong = 0x7 (XiangShan)
mepc   different at pc = 0x80001714, right = 0x80001ba4 (NEMU), wrong = 0x80001b78 (XiangShan)
```

XiangShan's `mcause=7` is a stale value from an earlier store access fault at `0x80001b74`. Because XiangShan does not trap on `vmv.v.i`, `mcause` and `mepc` are never updated, while NEMU overwrites them with the new exception.

The instruction encoding itself (`vmv.v.i`) is valid. The root cause is the CSR state at the time of execution: **`vtype = 0xd8`** encodes **`vsew = 6`**, a reserved SEW value:

```
vtype = 0xd8 = 0b 1101_1000
  bits[7:5] = 110  → vlmul = 6  (mf4)
  bits[4:2] = 110  → vsew  = 6  ← RESERVED
  bit[1]     = 0   → vta   = 0
  bit[0]     = 0   → vma   = 0
```

Per the RISC-V V extension specification, the csr::[vsew] encoding is:

| csr::[vsew][2] | csr::[vsew][1] | csr::[vsew][0] | SEW |
|---|---|---|---|
| 0 | 0 | 0 |    8 |
| 0 | 0 | 1 |   16 |
| 0 | 1 | 0 |   32 |
| 0 | 1 | 1 |   64 |
| 1 | X | X |   Reserved

> While it is anticipated the larger csr::[vsew] encodings
> (`100`-`111`) will be used to encode larger SEW, the encodings are
> formally _reserved_ at this point.

The specification further states:

> If the csr:vtype[] value is not supported by the implementation, then the csr::[vill] bit is set in csr:vtype[], the remaining bits in csr:vtype[] are set to zero

and:

> Implementations must consider all bits of the csr:vtype[] value to determine if the configuration is supported.  An unsupported value in any location within the csr:vtype[] value must result in csr::[vill] being set.

Furthermore:

> If the csr::[vill] bit is set, then any attempt to execute a vector instruction that depends upon csr:vtype[] will raise an illegal-instruction exception.

NEMU instead checks `vsew` validity directly at execution time and traps. XiangShan performs neither check and executes the instruction normally.

The reserved `vsew` originates from `vsetvli` instructions in the fuzzer-generated program, whose `zimm` immediate field carries the raw value `0x0d8`:

```
0x80001170: 0d837057  vsetvli zero, t1, e64, m1, ta, ma
  → zimm = 0x0d8 → vsew = 6 (reserved), vlmul = 6 (mf4)
```

Note: the disassembler renders this as `e64, m1` (vsew=3, vlmul=0), which is misleading — the raw `zimm` bits actually encode `vsew = 6`.

The DiffTest report is as follows：

[seeds_80_.log](https://github.com/user-attachments/files/30463576/seeds_80_.log)


### Expected behavior

XiangShan should either set `vill` when `vsetvli`/`vsetvl` writes a reserved `vsew` value , or trap at execution time like NEMU. In either case, it must not silently execute vector instructions with reserved `vsew` values.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_80_.zip](https://github.com/user-attachments/files/30463612/seeds_80_.zip)

### Additional context

_No response_
