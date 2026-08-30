### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest failure was observed between XiangShan and NEMU. The DiffTest mismatch on `s0` is a pure consequence of the core hang, not an independent execution bug. 

When executing the instruction sequence below, XiangShan commits `vsetvl zero, sp, a2` at PC `0x80001284` but then completely hangs for 15000+ cycles without committing any further instructions.

```
80001284:   80c17057    vsetvl zero, sp, a2
80001288:   c49fb42f    amominu.d.aq    s0, s1, (t6)
````

Upon detecting the hang, the DiffTest framework allows NEMU to run **one more instruction** — which is the `amominu.d.aq` at `0x80001288`. Since `amominu` writes to `s0`, NEMU's `s0` is updated while DUT's `s0` retains its stale historical value. This step-alignment gap triggers the following mismatch and ABORT:

```
s0 different at pc = 0x0080000164, right = 0x55555555ffffffef, wrong = 0xfffffffff65a9338
Core 0: ABORT at pc = 0x80000164
Core-0 instrCnt = 471, cycleCnt = 21,462, IPC = 0.021946
```

<html>
<body>
<!--StartFragment--><!-- obsidian --><h3 data-heading="Experimental Verification">Experimental Verification</h3>
<p>Replacing <code>vsetvl zero, sp, a2</code> at <code>0x80001284</code> with a standard <code>NOP</code> (<code>addi x0, x0, 0</code>):</p>

  | Original (vsetvl) | Patched (NOP)
-- | -- | --
Core hang | Yes (15000+ cycles) | No
Committed instrs | 471 | 546
Result | ABORT at 0x80000164 | HIT GOOD TRAP


<p>After replacing with NOP, the core no longer hangs, and the program runs to <code>HIT GOOD TRAP</code> successfully. This confirms that <code>vsetvl</code> with <code>rd=zero</code> leaves the pipeline/vector unit in an abnormal state that blocks subsequent instruction commitment.</p><!--EndFragment-->
</body>
</html>

The DiffTest report is as follows：

[emulator.zip](https://github.com/user-attachments/files/28355763/emulator.zip)

### Expected behavior

XiangShan should correctly handle `vsetvl` with `rd=zero` and allow subsequent instructions to issue and commit normally without causing a pipeline deadlock.

### Environment

- Repo
    - XiangShan commit id: `f464649442`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan-v2/build-v2/emu -b 0 -e 0 -i /***/seeds_170_.elf --diff /***/dut/XiangShan-v2/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds.zip](https://github.com/user-attachments/files/28355789/seeds.zip)

### Additional context

_No response_
