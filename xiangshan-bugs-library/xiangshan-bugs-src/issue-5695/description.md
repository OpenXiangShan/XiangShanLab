### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

When a misaligned load or store targets a non-idempotent memory region (MMIO / uncacheable I/O), XiangShan raises `loadAddrMisaligned` / `storeAddrMisaligned` exceptions. The RISC-V Privileged Specification (§3.6.1 "Idempotency PMAs") recommends that such accesses should raise `*AccessFault` exceptions.

### Spec Reference

From the RISC-V Privileged Specification, §3.6.1 *Idempotency PMAs* (NOTE block):

> Non-idempotent regions might not support misaligned accesses. Misaligned accesses to such regions **should** raise access-fault exceptions rather than address-misaligned exceptions, indicating that software should not emulate the misaligned access using multiple smaller accesses, which could cause unexpected side effects.

<img width="1217" height="165" alt="Image" src="https://github.com/user-attachments/assets/64827d55-9d56-4702-9226-b7d858799314" />

### Code Evidence

**1. Load pipeline** (`src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`):
```scala
// Line 1312-1314
// We will generate misaligned exceptions at mmio.
val s2_real_exceptionVec = WireInit(s2_exception_vec)
s2_real_exceptionVec(loadAddrMisaligned) := (s2_out.isMisalign || s2_out.isFrmMisAlignBuf) && s2_uncache
```

**2. Store pipeline** (`src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`):
```scala
// Line 388
s1_out.uop.exceptionVec(storeAddrMisaligned) := s1_out.mmio && s1_in.isMisalign
```

And in S2:
```scala
s2_out.uop.exceptionVec(storeAddrMisaligned) := s2_actually_uncache && (s2_in.isMisalign || s2_in.isFrmMisAlignBuf) && !s2_un_misalign_exception
```

**3. LoadMisalignBuffer** (`src/main/scala/xiangshan/mem/lsqueue/LoadMisalignBuffer.scala`):
```scala
exceptionVec(loadAddrMisaligned) := true.B
```


### Expected behavior

Per the spec recommendation, when a misaligned load or store targets a non-idempotent (MMIO / uncacheable I/O) region:

- A misaligned **load** should raise `loadAccessFault` (exception code 5) instead of `loadAddrMisaligned` (exception code 4).
- A misaligned **store** should raise `storeAccessFault` (exception code 7) instead of `storeAddrMisaligned` (exception code 6).

### Environment

XiangShan branch:master
XiangShan config:DefaultConfig

### To Reproduce

N/A —sorry for no minimal reproducer yet. The issue was found via code inspection.


### Additional context

_No response_
