### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

The attached PoC log shows a mismatch after the `mret` instruction at `pc = 0x800001e6`:

```text
vsstatus different at pc = 0x00800001e6,
right = 0x0000000200000000,
wrong = 0x0000000201000000
```

This `mret` returns from M-mode to VU-mode. The difference in `vsstatus` is `0x01000000`, which is bit 24, `SDT`. After returning to VU-mode, XiangShan still keeps `vsstatus.SDT = 1`, while the expected state has `vsstatus.SDT = 0`.

The likely cause is in `XiangShan/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala`. The MRET event computes the intended clear value:

```scala
out.vsstatus.bits.SDT := Mux(mretToVu, 0.U, in.vsstatus.SDT.asBool)
```

However, the event only marks `privState`, `mstatus`, and `targetPc` as valid:

```scala
out.privState.valid := valid
out.mstatus  .valid := valid
out.targetPc .valid := valid
```

It does not drive `out.vsstatus.valid := valid`, so the computed `vsstatus.SDT` update may not be accepted by the CSR update path.

### Expected behavior

When `MRET` returns to VU-mode, XiangShan should clear `vsstatus.SDT` to 0

after the `mret` at `pc = 0x800001e6`, `vsstatus` should be:

```text
0x0000000200000000
```

It should not retain bit 24 as:

```text
0x0000000201000000
```

<img width="1189" height="84" alt="Image" src="https://github.com/user-attachments/assets/278a4c2d-b63f-41f7-b465-c3d93c808e24" />


### Environment

Branch: kunminghu-v3 

### To Reproduce

[emu_diff_nemu.log](https://github.com/user-attachments/files/28644870/emu_diff_nemu.log)

[mret-sdt.zip](https://github.com/user-attachments/files/28644912/mret-sdt.zip)

### Additional context

_No response_
