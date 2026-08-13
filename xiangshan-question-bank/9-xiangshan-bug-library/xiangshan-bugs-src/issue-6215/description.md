### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

When V=1 and vsatp.MODE = Bare (onlyStage2), the guest's effective address is the guest-physical address (GPA) that goes directly into G-stage translation. With pointer masking enabled (henvcfg.PMM/senvcfg.PMM = PMLEN16), the TLB computes the masked address EffectiveVa (bit48/49 cleared), but the translation path keeps consuming the raw, unmasked vaddr.

Key code (kunminghu-v2):

```
// TLB.scala:550: PTW request VPN uses raw vaddr
io.ptw.req(idx).bits.vpn := get_pn(req_out(idx).vaddr)

// PageTableWalker.scala:374-376: onlyStage2 GPA = raw vpn; Sv48×4 skips the high-bit check
full_gvpn_reg := io.req.bits.req_info.vpn
val onlys2_gpaddr = Cat(io.req.bits.req_info.vpn, 0.U(offLen.W)) 
val check_gpa_high_fail = Mux(... === Sv39x4, ..., false.B)   
```

Under Sv48×4 the G-stage root index is VPN[3] = GPA[49:39], which includes bit48/49. So the raw tag bits directly index the root page table. 

Result: for a load/store whose pointer has a non-zero tag in bit48/49, the G-stage root walk uses the dirty tag bits instead of the masked GPA, so the access either maps to the wrong HPA if the mis-indexed PTE is valid, or raises a spurious guest page fault if it is invalid.


### Expected behavior

As Spec:
"When applied to a physical address, including guest-physical addresses (i.e., all cases except when the active satp register's MODE field != Bare), the ignore transformation replaces the upper PMLEN bits with 0."
"When running with virtualization in VS/VU mode with vsatp.MODE = Bare, this means that those two bits may be subject to pointer masking, depending on hgatp.MODE and servcfg.PMM/henvcfg.PMM (for VU/VS mode). If vsatp.MODE != BARE, this issue does not apply."
"An implementation could mask those two bits on the TLB access path … Alternatively … the pointer masking operation can be applied on the TLB refill path … some TLB entries need to be flushed when PMLEN changes …"
lists explicitly: "PMLEN=16 and hgatp.MODE=sv48x4".

Therefore, under onlyStage2 + hgatp=Sv48×4 + PMM=PMLEN16: the top 2 GPA bits (bit48/49) must be zeroed before G-stage translation. The spec allows two implementations (mask on the access path, or mask on the refill path with up to 4 duplicate entries + flush on PMLEN change); it does not permit skipping the mask on the translation path, which is the current behavior.

### Environment

  - XiangShan commit id: `master`


### To Reproduce

I will provide the POC and result analysis promptly tomorrow for your further reference.

### Additional context

_No response_
