### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

`vnclip.wi v4, v2, 1` with SEW=16, `vs2 = 0xffff` and `vxrm=RNU` must round, saturate and set `vxsat=1`; Spike produces `v4 = 0` and `vxsat = 1`. The DUT produces `v4 = 0xffffffffffffffff` and leaves `vxsat = 0`, i.e. it commits the unrounded shift result and never reports saturation. `vnclipu` shows the same vxsat/vcsr mismatch.


### Expected behavior

`vnclip.wi v4, v2, 1` with `vs2=0xffff`, SEW=16, `vxrm=RNU` must produce `v4 = 0` and `vxsat = 1`.



### Environment

 - Hardware
    - CPU: AMD EPYC 7H12 64-Core Processor × 2 sockets (64 cores / 128 threads each, 256 threads total)
    - Memory (GB): 1007
    - Storage (GB): 879 (local /); 900 (NFS workspace)
  - Software
    - Operating system: Ubuntu 24.04.3 LTS
    - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
    - clang version: Ubuntu clang version 18.1.3 (1ubuntu1)
    - java version: openjdk version "21.0.12.1" 2026-08-18
    - mill version: Mill Build Tool version 0.12.3
  - Repo
    - XiangShan commit id: `a253c1163982ae0b485b3a2ed9b61253d980d837`
    - NEMU commit id (if difftest failed with NEMU): N/A — difftest was against Spike
    - SPIKE commit id (if difftest failed with SPIKE): `5e8a1eae3f` (OpenXiangShan/riscv-isa-sim, tag `v2026.09.r2`, with difftest patches)
  - Build & Run
    - Build command: `export NOOP_HOME=$PWD && make init && make emu -j64`
    - Run command (if applicable): `./build/emu --diff $SPIKE_HOME/difftest/build/riscv64-spike-so --ram-size=128MB -i prog.elf`
    - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

1. Build the bare-metal image from `prog.c` with `-march=rv64gcv_zicsr_zifencei_zvbb_zvkb -mabi=lp64 -mcmodel=medany -O2 -static -no-pie`.
2. The program configures SEW=16, LMUL=m1, sets `vxrm=RNU`, fills `v2` with `0xffff`, and executes `vnclip.wi v4, v2, 1` at the probe.
3. Run the image on the DUT with difftest enabled and RAM size 128 MB.
4. Run the same ELF on the Spike difftest reference and compare `v4`, `vxsat` and `vcsr`.

Reproducer ELF SHA-256: `592d1428a8e180b4e5cf78df500981b201aa486b4c4a671096a82ae2bf3a2097`

[vnclip-rounding-upload.zip](https://github.com/user-attachments/files/34628171/vnclip-rounding-upload.zip)

### Additional context

_No response_
