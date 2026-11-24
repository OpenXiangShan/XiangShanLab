### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


`AXI4IntrGenerator` implements an 8×32-bit register file (`intrGenRegs`, register `k` readable at byte offset `4*k`). The read data is selected with a 2-entry Vec `intrReg` indexed by the raw AXI byte address, so Chisel truncates the addr index to 1 bit and the selected 32-bit value is zero-extended onto the 64-bit R channel:

https://github.com/OpenXiangShan/XiangShan/blob/d136732cf9fabcd883d24454d4223ecf47d81c88/src/main/scala/device/AXI4IntrGenerator.scala#L40-L43

https://github.com/OpenXiangShan/XiangShan/blob/d136732cf9fabcd883d24454d4223ecf47d81c88/src/main/scala/device/AXI4IntrGenerator.scala#L92

```verilog
output [63:0] auto_in_r_bits_data,          // 64-bit read data
...
wire [63:0] nodeIn_r_bits_data =
  {32'h0,                                   // upper 32 bits hardwired to 0
   (_nodeIn_r_bits_user_T ? nodeIn_ar_bits_addr[0] : raddr_hold_data[0])
     ? intrGenRegs_1
     : intrGenRegs_0};                      // index = byte-address bit 0 only
...
assign auto_in_r_bits_data = nodeIn_r_bits_data;
```

Results:
1. the register index (byte-address bits [4:2]) never reaches the mux: for every aligned access (address bit 0 = 0) the mux always selects `intrGenRegs(0)`;
2. the selected 32-bit value is then assigned to the 64-bit R channel, so every read beat is `{32'b0, intrGenRegs(0)}`, the upper 32 bits of the beat are always zero.



### Expected behavior

`lw` of byte offset `4*k` returns `intrGenRegs(k)` (k = 0..7) in the byte lanes addressed by the load and a 64-bit `ld` of an 8-byte-aligned offset returns `{intrGenRegs(k+1), intrGenRegs(k)}`. e.g., after `intrGenRegs[0]=1`, `intrGenRegs[1]=2`, `intrGenRegs[4]=0x12345678`:
  `lw +0x00`=1, `lw +0x04`=2, `lw +0x10`=0x12345678, `ld +0x00`=0x0000000200000001.


### Environment

- Repo
  - XiangShan commit id: `d136732cf9fabcd883d24454d4223ecf47d81c88` (branch `kunminghu-v3`)
- Note: MMIO loads are not compared by the difftest, so this bug does not show up as a difftest mismatch. The values below were read back by the program itself and printed over the UART.

### To Reproduce

```asm
    # IntrGenerator register file: 0x40070000 + 4*k, k = 0..7
    li      s0, 0x40070000

    li      t1, 0x1
    sw      t1, 0x00(s0)            # intrGenRegs[0] = 0x1
    li      t1, 0x2
    sw      t1, 0x04(s0)            # intrGenRegs[1] = 0x2
    li      t1, 0x12345678
    sw      t1, 0x10(s0)            # intrGenRegs[4] = 0x12345678

    # register writes reach the register file after 1000 cycles (DelayN)
    li      t2, 2000
1:  addi    t2, t2, -1
    bnez    t2, 1b

    lw      s1, 0x00(s0)            # a) word 0
    lw      s2, 0x04(s0)            # b) word 1
    lw      s3, 0x10(s0)            # c) word 4
    ld      s4, 0x00(s0)            # d) 64-bit beat at word 0
    # print s1..s4 over UART

    # second round: rewrite only word 0; word 4 stays 0x12345678
    li      t1, 0xaaaa5555
    sw      t1, 0x00(s0)
    li      t2, 2000
2:  addi    t2, t2, -1
    bnez    t2, 2b
    lwu     s5, 0x10(s0)            # e) word 4
    lwu     s8, 0x04(s0)            # f) word 1
    # print s5, s8 over UART
```

Observed (the six values above, printed over UART, in order):

```
0000000000000001    # a) lw  0x00  -> intrGenRegs[0]  (expected 0x0000000000000001)
0000000000000000    # b) lw  0x04  -> expected intrGenRegs[1] = 0x0000000000000002
0000000000000001    # c) lw  0x10  -> expected intrGenRegs[4] = 0x0000000012345678
0000000000000001    # d) ld  0x00  -> expected 0x0000000200000001
00000000aaaa5555    # e) lwu 0x10  -> word 4 follows the rewritten word 0 (expected 0x12345678)
0000000000000000    # f) lwu 0x04  -> expected intrGenRegs[1] = 0x0000000000000002
```

[poc.zip](https://github.com/user-attachments/files/33303377/poc.zip)

### Additional context

_No response_
