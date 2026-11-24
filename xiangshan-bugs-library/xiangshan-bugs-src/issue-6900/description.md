### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [ ] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Executing lw to physical address 0x0000_0000_5000_0000 (the address gap between the MMIO peripheral region and DRAM, with no bus responder) does not raise the architecturally required load access fault (mcause=5). Instead, the kunminghu-v3 DUT silently completes the load and continues executing subsequent instructions. The NEMU reference model behaves identically (difftest reports no mismatch throughout), meaning this defect cannot be caught by the existing difftest framework.

The test program installs a mtvec trap handler to distinguish two exit paths: if the access fault is correctly delivered, the processor jumps to trap_handler (pc 0x80000024) and exits with the mcause value; if the load silently completes, the program falls through to the no-exception exit point (pc 0x80000020). Both tested unmapped addresses (0x5000_0000 and 0x0) took the no-exception path; disassembly confirms this pc is the fall-through location.

### Expected behavior

The RISC-V Privileged Architecture specifies: a load to a physical address with no bus responder or disallowed by PMA (Physical Memory Attributes) must raise a load access fault (exception code 5, mcause=5), delivered via mtvec. In bare-metal M-mode, addresses 0x0 and 0x5000_0000 have no PMA-permitted memory or device; both lw instructions must trigger the exception.

Specification: RISC-V Privileged Architecture, "Load access fault" (mcause=5).

### Environment

- Hardware
  - CPU: Intel(R) Xeon(R) CPU E5-2680 v2 @ 2.80GHz
  - Memory (GB): 62
  - Storage (GB): 468
- Software
  - Operating system: Ubuntu 20.04.6 LTS
  - gcc version: 9.4.0
  - clang version: 10.0.0-4ubuntu1
  - java version: 11.0.27
  - mill version: 
- Repo
  - XiangShan commit id: ``a863b899b17c80936b6a4ada27cfc0ccfaeed5a0
  - NEMU commit id (if difftest failed with NEMU): ``53bcb5686f8fd05248ae98546b7dc04bdca1bbb0
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: make ARCH = riscv64-xs


### To Reproduce

/opt/riscv/bin/riscv64-unknown-elf-as -march=rv64imafd -mabi=lp64 -o test_bug.o test_bug.S
/opt/riscv/bin/riscv64-unknown-elf-ld -T link.ld -o test_bug.elf test_bug.o
/opt/riscv/bin/riscv64-unknown-elf-objcopy -O binary test_bug.elf test_bug.bin

cd /xs-env && source env.sh
REF=/xs-env/XiangShan/ready-to-run/riscv64-nemu-interpreter-so
$NOOP_HOME/build/emu -b 0 -e 0 -C 3000000 -i /root/xsptr/test_bug.bin --diff "$REF"

[BUG-silent-load.zip](https://github.com/user-attachments/files/34590051/BUG-silent-load.zip)

### Additional context

_No response_
