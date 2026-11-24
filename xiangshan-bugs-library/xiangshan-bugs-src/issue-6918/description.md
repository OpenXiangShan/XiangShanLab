### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

`vsse8.v` with SEW=8 never commits: after 15000 cycles the DUT prints `CROB-STUCK` with `headPC=0x80000962`. The indexed stores `vsoxei8/16/32/64` and `vsuxei8/16/32/64` show the same behavior; about 30 of the 42 indexed/segment-store programs hang. The remaining ones abort earlier on the `mf2` offset-load mismatch.


### Expected behavior

`vsse8.v` SEW=8 must complete and retire, with the same memory side effects as the Spike reference.


### Environment

- Hardware
  - CPU:
  - Memory (GB):
  - Storage (GB):
- Software
  - Operating system:
  - gcc version: <!-- run `gcc --version 2>&1 | head -n 1` to get the version -->
  - clang version: <!-- run `clang --version 2>&1 | head -n 1` to get the version, only needed when you use clang -->
  - java version: <!-- run `java -version 2>&1 | head -n 1` to get the version -->
  - mill version: <!-- run `mill -i --version 2>&1 | head -n 1` to get the version -->
- Repo
  - XiangShan commit id: ``
  - NEMU commit id (if difftest failed with NEMU): ``
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: ``
  - Run command (if applicable): ``
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

1. Build the bare-metal image from `prog.c` with `-march=rv64gcv_zicsr_zifencei_zvbb_zvkb -mabi=lp64 -mcmodel=medany -O2 -static -no-pie`.
2. The program configures SEW=8, LMUL=m1, loads the value and stride registers, and executes `vsse8.v` at the probe.
3. Run the image on the DUT with difftest enabled and RAM size 128 MB; the run stops after 15000 cycles without a commit.
4. Run the same ELF on the Spike difftest reference: the reference completes the store and retires.

[indexed-store-hang-upload.zip](https://github.com/user-attachments/files/34626107/indexed-store-hang-upload.zip)

### Additional context

_No response_
