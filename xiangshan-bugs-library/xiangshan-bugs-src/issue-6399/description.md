### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug


A DiffTest mismatch occurs in `mtval` after a vector store access fault followed by a scalar store access fault. 
The minimized instruction sequence is:
```
vsetivli x0, 4, e32, m1, ta, ma
vsse32.v v11, (t4), s1
li a5, 0x0000020000000000
li t2, 0x917af882bf50788e
srl t6, t2, a5
sb sp, -1918(t6)
```

At PC `0x8000102c`, `vsse32.v v11,(t4),s1` first raises a Store/AMO access fault (`mcause=7`). The trap handler reads `mtval` into `x20`, and the commit trace shows `mtval=0`:
```
[19] exception pc 000000008000102c inst 0a9ee5a7 cause 0000000000000007
[20] commit pc 0000000080001000 inst 34302a73 wen 1 dst 20 data 0000000000000000 idx 090
```

The handler skips the faulting instruction and returns with mret. 
The subsequent instructions produce: `t6 = 0x917af882bf50788e`
Therefore, at PC `0x8000105c`: `sb sp, -1918(t6)`
accesses: `0x917af882bf50788e - 0x77e = 0x917af882bf507110`
and raises another Store/AMO access fault (mcause=7).

However, `mtval` differs:

| **Field**                  | **DUT (XiangShan)**            | **REF (NEMU)**           |
| -------------------------- | ------------------------------ | ------------------------ |
| Instruction @ `0x8000105c` | `sb sp,-1918(t6)`              | same                     |
| `t6`                       | `0x917af882bf50788e`           | same                     |
| Effective address          | `0x917af882bf507110`           | same                     |
| `mcause`                   | `0x7` (Store/AMO access fault) | `0x7`                    |
| `mtval`                    | **`0x0`**                      | **`0x917af882bf507110`** |


The DiffTest report is: 
```
mtval different at pc = 0x0010000008, right = 0x917af882bf507110, wrong = 0x0000000000000000
```

The mismatch disappears if either `vsse32.v v11,(t4),s1`, `sb sp,-1918(t6)`, or the `li/li/srl` sequence generating the invalid address is removed. Therefore, the preceding vector store access fault appears necessary to trigger the `mtval` mismatch on the subsequent scalar store access fault.

The DiffTest report is as follows：

[t_min.log](https://github.com/user-attachments/files/31298371/t_min.log)

### Expected behavior

After returning from the `vsse32.v` exception, the subsequent independent scalar store access fault should not be affected by the preceding vector exception. For `sb sp,-1918(t6)` with address `0x917af882bf507110`, XiangShan should report the same `mtval` as NEMU.

### Environment

- Repo
    - XiangShan commit id: `e12436c7cb`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[t_min.zip](https://github.com/user-attachments/files/31298270/t_min.zip)

### Additional context

_No response_
