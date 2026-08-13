**Type of issue**: bug report

**Impact**: L1D Cache

**Compile:** make emu -j32

**Used boom commit:** [f0d8a1c](https://github.com/OpenXiangShan/XiangShan/commit/f0d8a1cf543c113433ff98140b6d77055d930deb)

**How to reproduce the attack:** [AM workload](https://xiangshan-doc.readthedocs.io/zh-cn/latest/tools/gen-workload-with-am/)

**Development Phase**: proposal

I found a L1D Cache Side-channal on Nanhu.
The attack relies on the csr mcycle and PLRU eviction algorithm. 

The attached PoC attack is a Information Disclosure type of attack where an attacker leaks a secret from the L1D cache.

It still works and almost correctly retrieves the secret value.

[dcachetest.zip](https://github.com/OpenXiangShan/XiangShan/files/13596710/dcachetest.zip)
