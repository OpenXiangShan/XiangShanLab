### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


`csrr vl` that follows a `vsetvli` reads 0, while `vsetvli` itself returns the right value in `rd`. Minimal program:

```asm
loop:
  li      a0, 8
  vsetvli t1, a0, e32, m2, tu, mu
  csrr    t3, vl
  bne     t3, t1, bad           # vl must equal what vsetvli returned
  addi    s1, s1, -1
  bnez    s1, loop
```

(mstatus.VS is set beforehand, `li t0, 0x600; csrs mstatus, t0`.) Commit trace on the latest commit, difftest against NEMU:

```
[15] commit pc 0000000080000022 inst 01157357 wen 1 dst 06 data 0000000000000008   # vsetvli t1,a0,e32,m2 : t1 = 8, correct
[16] commit pc 0000000080000026 inst c2002e73 wen 1 dst 28 data 0000000000000000   # csrr t3,vl           : t3 = 0, wrong
      t3 different, right = 0x0000000000000008, wrong = 0x0000000000000000
```

(The `pc` printed on the `different` line is not meaningful in this build (`0x47253a591219`); the commit trace above has the right pc.)

Two slightly longer variants fail the same way (`vlbisB.S`: avl taken from a pseudo-random sequence; `vlbisC.S`: additionally a second `vsetvli` under a data-dependent branch), after 25 and 30 committed instructions.

### Expected behavior

`t3 = 8`, as NEMU and Spike.

### Environment

| version | result |
|---|---|
| kunminghu-v2 `e7bab53e66` (2026-09-24) | all three pass |
| kunminghu-v3 `4ce2f88ae7` (2026-07-31) | all three pass |
| kunminghu-v3 `00eb9ee7c` (2026-08-05) | all three pass |
| kunminghu-v3 `09453ff8f` (2026-09-28) | all three fail at the `csrr vl` |


### To Reproduce

`emu -i vlbisA.bin --diff riscv64-nemu-interpreter-so -I 100000 -C 60000` with `vlbisA.bin = objcopy -O binary vlbisA.elf`.

[testcase.zip](https://github.com/user-attachments/files/33084585/testcase.zip)

### Additional context

_No response_
