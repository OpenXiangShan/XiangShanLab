**For maintainer: This bug does not affect kunminghu-v3 as we've rewritten the whole uncache fetch logic. Do NOT cherry-pick.**

We have `def crossCacheline = startAddr(blockOffBits - 1) === 1.U`, but uncache fetch only works on addr `[start:start+4)`. So, whether we should do uncache fetch should only depends on `f3_exception(0)`.

For example in this case:

```
start            page boundary      assumed end (start+32)
  | uncache fetch region |              |
0xfffc                0xffff         0x10018
  |   exception = None   | exception = Access Fault
```

This is a crossline request (`start(5) === 1.U`), and it's also a crosspage request, different page can have different permissions / attributes, so we can have the first page normal while the second page af/pf/gpf.

In this case, we should fetch and send the first 4B (assume it's an RVI instr) to ibuffer, and mark the first instr in next page as invalid/af/pf/gpf.

However, in current implementation, we simply canceled the uncache fetch, sending 4B of 0 to backend, causing an Illegal Instruction Exception.

This PR fixes this, we do uncache fetch if exception(0) is none, if there's an RVI instr crossing page, we'll check exception(1) before resend.

Test: https://github.com/OpenXiangShan/nexus-am/pull/68

We can see mcause & mepc changes in this test:
<img width="2081" height="1130" alt="image" src="https://github.com/user-attachments/assets/7fb7cccb-ec2d-4ed8-a529-6a583e13c3f0" />

mcause: 0x2(illegal instr) -> 0x1(instr access fault)
mepc: 0x82fffffa(first instr in test_func) -> 0x82fffffe(address of the cross page RVI)
