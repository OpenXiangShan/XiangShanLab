### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

The four vector floating-point move/merge instructions — `vfmerge.vfm`, `vfmv.v.f`, `vfmv.f.s`, `vfmv.s.f` — do not raise an illegal-instruction exception when the `frm` CSR contains a frm value (5, 6, or 7).
Spike raises an illegal-instruction exception for these instructions when `frm` ≥ 5.

<img width="1046" height="129" alt="Image" src="https://github.com/user-attachments/assets/7704c27a-f257-4a1e-b71a-f908259e1f80" />

The spec states that all vector FP instructions must check `frm`even when no elements are processed.


Analysis

The vector frm legality check is enabled through two mechanisms:

```scala
(decodedInst.needFrm.vectorNeedFrm || FuType.isVectorNeedFrm(decodedInst.fuType)) && io.fromCSR.illegalInst.frm
```

The two sets that gate this check are:

- `FuType.vectorNeedFrm = Seq(vfalu, vfma, vfdiv, vfcvt)` — does **not** include `vmove`
- `vectorNeedFrmInsts = Seq(VFSLIDE1UP_VF, VFSLIDE1DOWN_VF)` — does **not** include the 4 FP vmove instructions

All four affected instructions are decoded in XiangShan with FuType.vmove, and none of them are covered by either the FuType.vectorNeedFrm path or the vectorNeedFrmInsts whitelist.

### Expected behavior

When `frm` contains a reserved value (5, 6, or 7), executing any of `vfmerge.vfm`, `vfmv.v.f`, `vfmv.f.s`, `vfmv.s.f` should raise an illegal-instruction exception, consistent with the specification and Spike reference model behavior.

### Environment

Branch: kunminghu-v3

### To Reproduce

li      t0, 5
csrw    frm, t0

vsetvli t1, zero, e32, m1, ta, ma

vfmerge.vfm v1, v2, fa0, v0    
vfmv.v.f    v1, fa0           
vfmv.f.s    fa0, v1           
vfmv.s.f    v1, fa0         

### Additional context

_No response_
