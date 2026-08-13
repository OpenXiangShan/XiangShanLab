### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

A legal RISC-V Zicbop software prefetch hint can cause XiangShan to stop making forward progress in RTL/Verilator simulation.

I have two reduced PoC pairs. In each pair, app_buggy.elf keeps one trigger prefetch hint instruction, while app_ok.elf replaces only that instruction with a direct jump to the final block.

Observed behavior:
- app_buggy.elf reaches SIMLEN without stop or register dump.
- app_ok.elf reaches the final stop request normally.
- Spike decodes the trigger words as legal Zicbop prefetch hints and continues execution.

Trigger instructions:
1. 0x80009dc8: 0xea176013
   objdump: ori x0,x14,-351
   Spike: prefetch.r -352(a4)

2. 0x8000134c: 0x74076013
   objdump: ori x0,x14,1856
   Spike: prefetch.i 1856(a4)

[xiangshan_poc_submission.zip](https://github.com/user-attachments/files/27749929/xiangshan_poc_submission.zip)

### Expected behavior

Zicbop software prefetch instructions are hints. They should complete or be safely ignored, and should not block retirement or forward progress.

Even if the prefetch target address is invalid, unmapped, or otherwise not useful, the core should continue execution and eventually reach the final stop request.


### Environment

- Hardware
  - CPU: AMD Ryzen Threadripper PRO 7965WX 24-Cores, 48 threads
  - Memory (GB): 123 GiB available in the test machine
  - Storage (GB): 1.8 TiB disk, about 967 GiB free during testing

- Software
  - Operating system: Ubuntu 26.04 LTS on the host machine
  - Verilator runtime environment: Ubuntu 22.04.3 LTS
  - gcc version:
    - Host: gcc (Ubuntu 15.2.0-16ubuntu1) 15.2.0
    - Verilator runtime: gcc (Ubuntu 11.4.0-1ubuntu1~22.04) 11.4.0
  - clang version: Ubuntu clang version 14.0.0-1ubuntu1.1
  - java version:
    - XiangShan RTL generation: openjdk version "21.0.11-ea"
    - Verilator runtime environment: openjdk version "1.8.0_382"
  - mill version: Mill Build Tool version 0.12.15
  - Verilator version: Verilator 5.032 2025-01-01 rev v5.032

- Repo
  - XiangShan branch: master (kunminghu-v2)
  - XiangShan commit id: ed346cdc8ca174210c48df96620b8887329f8e92
  - XiangShan config: TLMinimalConfig
  - NEMU commit id (if difftest failed with NEMU): N/A, not used
  - SPIKE commit id (if difftest failed with SPIKE): d1efcdffffee57bab0fdbd2b377c6132b37556fd
  - SPIKE version: Spike RISC-V ISA Simulator 1.1.1-dev

- Build & Run
  - Build command:
    mill -i -Djvm-xmx=64G -Djvm-xss=256m xiangshan.runMain top.TopMain \
      --target-dir build/rtl --config TLMinimalConfig --issue E.b \
      --num-cores 1 --target systemverilog \
      --split-verilog --dump-fir --fpga-platform --reset-gen

  - Run command:
    SIMLEN=50000 \
    SIMSRAMELF=/path/to/seed_500937/app_buggy.elf \
    TRACEFILE=/tmp/vanilla_headtail_500937_buggy.vcd \
    /path/to/Vtop_tiny_soc

  - Also upload workload binary/source in "To Reproduce" section:
    xiangshan_poc_submission.zip


### To Reproduce

Please see the attached PoC package.

Files:
- seed_500937/app_buggy.elf
- seed_500937/app_ok.elf
- seed_500937/app_buggy.elf.dump
- seed_500937/app_ok.elf.dump
- seed_502992/app_buggy.elf
- seed_502992/app_ok.elf
- seed_502992/app_buggy.elf.dump
- seed_502992/app_ok.elf.dump
- README.md

Run shape:

SIMLEN=50000 \
SIMSRAMELF=/path/to/seed_500937/app_buggy.elf \
TRACEFILE=/tmp/vanilla_headtail_500937_buggy.vcd \
/path/to/Vtop_tiny_soc

For seed_502992, replace SIMSRAMELF with:

/path/to/seed_502992/app_buggy.elf

Expected results:
- seed_500937/app_ok.elf: stop request observed, 24 register dumps
- seed_500937/app_buggy.elf: reaches SIMLEN, no stop, no register dump
- seed_502992/app_ok.elf: stop request observed, 24 register dumps
- seed_502992/app_buggy.elf: reaches SIMLEN, no stop, no register dump

The README marks the exact trigger lines in the dump files and includes minimal .word-based assembly snippets.


### Additional context

Suspected related modules:
- xiangshan/backend/decode/DecodeUnit.scala
- xiangshan/mem/pipeline/NewLoadUnit.scala
- xiangshan/mem/pipeline/package.scala

Hypothesis:
DecodeUnit recognizes OP-IMM + funct3=110 + rd=x0 as software prefetch and redirects it to the load unit. For prefetch.i / prefetch.r, at least one path may fail to provide a reliable completion/writeback signal to the ROB. Since these are hint instructions, they should not be able to block retirement or forward progress.

I have not tested kunminghu-v3, nanhu, or yanqihu.
**Please contact me at: wang_jiashun@bupt.edu.cn**
