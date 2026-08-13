### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

With pointer masking (PM) enabled, Load/Store still feed the raw vaddr to the debug address trigger:

```
// LoadUnit.scala
s1_out.fullva := ... io.tlb.resp.bits.fullva     // :1007  
loadTrigger.io.fromLoadStore.vaddr := s1_vaddr   // :1133  

// StoreUnit.scala
val s1_fullva = io.tlb.resp.bits.fullva           // :306
storeTrigger.io.fromLoadStore.vaddr := s1_in.vaddr// :348 
```

s1_vaddr/s1_in.vaddr is the original pointer (not masked). Inside the trigger it is compared over [VAddrBits-1:0]:
```
TriggerCmp(vaddr, tdata2, matchType, enable)   // data(VAddrBits-1, 0) === tdata(VAddrBits-1, 0)
```
In the default H+Sv48 build VAddrBits = 50, so the window is bits [49:0]. Under PMLEN16 the tag sits in bits [63:48], so tag bits 48/49 are inside the compared window,so the trigger compares the tagged pointer, not the address the access actually uses.



### Expected behavior

As spec:
pointer masking, when applicable, is applied … to the memory access address when matching address triggers in debug.

The trigger must compare tdata2 against the masked effective address (the address actually accessed).

### Environment

  - XiangShan commit id: `master`

### To Reproduce

I will provide the POC and result analysis promptly tomorrow for your further reference.

### Additional context

_No response_
