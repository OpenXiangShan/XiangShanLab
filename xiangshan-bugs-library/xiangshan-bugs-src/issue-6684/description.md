### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


After #6246, the vl free list can still lose an entry after a redirect. Later, a vl physical register that is still mapped can be allocated to another uop, and the architectural `vl` takes the value that uop writes.

After a redirect, the VTypeBuffer walk can end before the RAB walk. Since #6246 the vl free list's walk state follows `io.vlCommits.isWalk`, and rename lets nothing out while either walk is in progress (`canOut`). But the vl free list still performs a normal allocation in a cycle where only the RAB is walking:

```scala
vlFreeList.io.walk := io.vlCommits.isWalk
vlFreeList.io.doAllocate := intFreeList.io.canAllocate && fpFreeList.io.canAllocate && vecFreeList.io.canAllocate && io.toLsqEnqCtrl.canAccept && dispatchCanAcc || io.vlCommits.isWalk
vlFreeList.io.allocateReq(i) := needVlDest(i)
```

In such a cycle, a vl-writing uop waiting at rename moves the vl free list's head, but stays in rename. It is renamed again later and takes the next register, so one entry is skipped. From then on the free list is out of step with the uops that hold vl registers, and a later head restore can put a register that is still mapped back into the free region. These lines are unchanged from the #6246 merge commit to `09453ff8f`.

### Expected behavior

The vl free list's head should only move for uops that leave rename, and a vl physical register should not be allocated while it is still mapped.

### Environment

- XiangShan `kunminghu-v3` `09453ff8f`, `DefaultConfig`, difftest against NEMU, Verilator 5.052, GCC 13 (`OPT_FAST=-O3`).

### To Reproduce


Unmodified builds (`make emu CONFIG=DefaultConfig`, NEMU reference), `emu -i <prog>.bin --diff riscv64-nemu-interpreter-so -I 2000000 -C 80000`, programs attached:

| program | `09453ff8f` (latest) | `00eb9ee7c` (#6246 merge) | `4ce2f88ae7` (before #6246) |
|---|---|---|---|
| `stress_404_0001` | `vl` wrong at 0x8000008e (2 → 7), instruction 4,139 | `vl` wrong at 0x800000f0 (8 → 15), instruction 3,610 | good trap |
| `stress_404_0008` | `vl` wrong at 0x800000b2 (5 → 12), instruction 12,097 | `vl` wrong at 0x80000122 (12 → 5), instruction 5,314 | good trap |

The programs are random loops of vector instructions around data-dependent branches. They do not read `vl` with `csrr`, write v0, or use vector loads/stores, because `09453ff8f` stops earlier on unrelated divergences in those. `kunminghu-v2` (`e7bab53e66`) is not affected.

[vl_freelist_repro_short.zip](https://github.com/user-attachments/files/33191203/vl_freelist_repro_short.zip)

### Additional context

_No response_
