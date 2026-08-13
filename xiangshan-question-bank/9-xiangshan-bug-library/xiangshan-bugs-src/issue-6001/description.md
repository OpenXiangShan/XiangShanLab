### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

**Buggy code** in [InterruptFilter.scala]

```scala
when (defaultPrio.U === InterruptNO.SEI.U) {
  iprio.isZero := platformValid || flag
  val stopeiGreaterThan255 = stopei.IPRIO.asUInt(10, 8).orR
  iprio.greaterThan255 := stopeiGreaterThan255
  iprio.prioNum := stopei.IPRIO.asUInt(7, 0)
}
```

When M-mode software injects SEI via `mvip.SEIP` and no external interrupt controller (APLIC/IMSIC) is providing a real external interrupt:

The resulting priority triple (isZero=0, greaterThan255=0, prioNum=0) encodes priority number 0, i.e., the highest numeric priority. The comparison logic in `minSelect` (line 179) uses `left.prioNum <= right.prioNum`, so this SEI incorrectly wins against any other interrupt with a non-zero explicit priority (e.g., an SSI with priority 5).

Spec reference: *The RISC-V Advanced Interrupt Architecture*, Version 1.0, Revised 20250312.

> If bit 9 for a supervisor external interrupt (SEI) is one in mideleg or mvien and in mvip, causing sip.SEIP to be one, but there is no supervisor-level interrupt from the hart's external interrupt controller (APLIC or IMSIC), then a priority number for the SEI is not supplied by the external interrupt controller as usual. In that case, the SEI is assigned a priority number of 256.

### Expected behavior

When SEI is pending solely due to M-level software injection (`mvip.SEIP=1`) and no supervisor-level external interrupt controller provides a priority number, the priority triple should be:

```
(isZero=0, greaterThan255=1, prioNum=0)
```

This represents priority number 256, causing `stopi.IPRIO = 255` (lowest representable priority), which correctly makes the software-injected SEI lower-priority than any interrupt with an explicitly programmed priority in the range 1–255.

### Environment

Branch: kunminghu-v2

### To Reproduce

I am still testing the correctness of this POC, just for your reference.

[aia-sei.zip](https://github.com/user-attachments/files/28106793/aia-sei.zip)

### Additional context

_No response_
