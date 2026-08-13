### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

@wissygh  This issue still illustrates the problem described in #5713. I apologize for not providing sufficient information previously, and really thank you for your response. But I still have concerns about the lack of implementation for this specification check. Below is the complete trigger scenario and reproduction evidence for your reference.



RISC-V Debug Specification:

> Because chain affects the next trigger, hardware **must** zero it in writes to mcontrol6 that set dmode to 0 if the next trigger has dmode of 1.

### Vulnerability Scenario

This vulnerability is triggered by a debug workflow as follows. 

halt
Step 1: Debugger (debug mode) sets the 1st breakpoint (bp 0x80002000 4 hw)
        → OpenOCD allocates trigger[0], writes dmode=1, action=1, tdata2=addr_A

Step 2: Debugger sets the 2nd breakpoint (bp 0x80000164 4 hw)
        → OpenOCD allocates trigger[1], writes dmode=1, action=1, tdata2=addr_B

Step 3: Debugger removes the 1st breakpoint (rbp 0x80002000)
        → OpenOCD writes 0 to trigger[0]'s tdata1, dmode becomes 0, marked as free
        Current state: trigger[0] dmode=0 (free), trigger[1] dmode=1 (still in use)

Step 4: Debugger resumes → CPU returns to M-mode

Step 5: M-mode writes trigger[0]: type=6, dmode=0, chain=1, tdata2=0xDEAD0000
        → Hardware should check trigger[1].dmode=1 and force chain=0 according to spec
        → But Actual: chain=1 is preserved (missing forward check)

Result: trigger[1]'s breakpoint is disabled (chain requires trigger[0] to also match, but trigger[0] can never matches)




### Expected behavior

When M-mode writes to trigger[0] with `dmode=0, chain=1`, the hardware should check trigger[1].dmode:
- If trigger[1].dmode=1, force chain to be cleared to 0
- Reading back trigger[0].tdata1 should return `0x6000000000000044` (chain=0), not `0x6000000000000844` (chain=1)

### Environment

- **Branch**: kunminghu-v3
- **Config**: DefaultConfig
- **Emulator**: emu with `--enable-jtag --no-diff`
- **OpenOCD**: riscv-openocd ([RISC-V fork](https://github.com/riscv-collab/riscv-openocd)),
The configuration process refers to https://docs.xiangshan.cc/projects/design/zh-cn/nanhu/misc/debug/ .
Only use OpenOCD

### To Reproduce

The attachment contains two AM program folders (chain-dmode-poc and trigger-baseline). Place them under nexus-am/apps/

### Test A: Baseline Test (Prove breakpoint works normally)

When there is no chain attack, the debugger's breakpoint (action=1, dmode=1) can normally halt the CPU.

Terminal 1 — Start emu:
```bash
emu --enable-jtag --no-diff -i trigger-baseline-riscv64-xs.bin 2>&1 | tee baseline.txt
```

Terminal 2 — Start OpenOCD:
```bash
openocd -f openocd-xs.cfg
```

Terminal 3 — Set breakpoint and resume (execute after OpenOCD shows `Listening on port 4444`):
```bash
(sleep 1; echo "halt"; sleep 5; echo "bp 0x80002000 4 hw"; sleep 3; echo "bp 0x8000012a 4 hw"; sleep 3; echo "rbp 0x80002000"; sleep 3; echo "resume"; sleep 60; echo "exit") | nc localhost 4444
```

> `0x8000012a` is the compiled target_func address in trigger-baseline. The actual address will be printed after emu starts; please refer to the actual output.

Expected Result: After emu outputs `"If this next line prints, trigger did NOT fire."`, there should be no further output (CPU halted), and the OpenOCD terminal should display `riscv.cpu halted due to breakpoint.`

### Test B: Test for bug 
same with Test A,but cpu cant trigger the breakpoint.

[chain-dmode-poc.zip](https://github.com/user-attachments/files/26221030/chain-dmode-poc.zip)

I would like to apologize again for the previous false positive result. If this information is of any reference value to you, please feel free to refer to it. Thank you for all your hard work!"

### Additional context

_No response_
