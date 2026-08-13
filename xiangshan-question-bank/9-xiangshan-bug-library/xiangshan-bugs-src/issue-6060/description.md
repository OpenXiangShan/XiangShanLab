### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A difftest run exposes a mismatch when `cbo.flush` targets a RAM page with PBMT=`NC`.

The PoC enables S-mode CBO execution and Svpbmt, maps the target RAM address `0x80002000` with a valid  leaf PTE whose PBMT field is `NC`, then executes:

```text
cbo.flush (a0), with a0 = 0x80002000
```

In the difftest result, the reference model(spike) advances past the CBO instruction, but XiangShan traps at the CBO instruction with a store access fault:

```text
pc    = 0x8000013c
inst  = 0x0025200f
cause = 7
mtval = 0x80002000
```

The likely cause is that XiangShan wrongly treats the PBMT non-cacheable attribute as an access-fault condition for CBO operations.

Source:

- `XiangShan/src/main/scala/xiangshan/mem/pipeline/NewStoreUnit.scala`

```scala
val isNC      = tlbHit && tlbAccessible && Pbmt.isNC(pbmt)
val isMMIO    = tlbHit && tlbAccessible &&
                (Pbmt.isIO(pbmt) || Pbmt.isPMA(pbmt) && pmp.mmio)
val isUncache = isNC || isMMIO     
val afCboUncache = isCbo && isUncache  // CBO + PBMT=NC 被归入 isUncache，进而触发 Store Access Fault
val af = afInaccessible || afVectorUncache || afCboUncache || afUnalignMMIO
stageInfo.uop.exceptionVec(storeAccessFault) := af

```

`Pbmt.isNC(pbmt)` is true, so `afCboUncache` becomes true and XiangShan reports `storeAccessFault`.

### Expected behavior

`cbo.flush`and other cbo instructions should not raise a store access fault because the target page is PBMT=`NC`.

<img width="1136" height="131" alt="Image" src="https://github.com/user-attachments/assets/595cbda5-24b1-48ed-a796-5342f5bea7b7" />

Therefore, for the PoC above, XiangShan should execute `cbo.flush` on `0x80002000` and continue to the next instruction instead of raising `mcause=7` which is aligned to spike .

### Environment

Branch: kunminghu-v3

### To Reproduce

[cbo.zip](https://github.com/user-attachments/files/28644332/cbo.zip)

[cbo_diff_spike.log](https://github.com/user-attachments/files/28644455/cbo_diff_spike.log)

### Additional context

_No response_
