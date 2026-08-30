### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

A minimal standalone bare-metal workload triggers a fatal `LoadQueueReplay` assertion on Kunminghu v2 when executing a translated vector byte load/store sequence that crosses a 4 KiB page boundary.

The failing assertion is:

```text
LoadQueueReplay: vector load, should not have replay entry 8 when commit or flush.
```

The workload does the following:

1. Configure allow-all PMP.
2. Execute one bare vector byte load/store round trip as warm-up.
3. Enable a minimal Sv39 page table.
4. Execute one translated 4 KiB-local vector byte load/store round trip.
5. Execute one translated vector byte load/store round trip crossing a 4 KiB page boundary.

Each vector round trip uses this instruction shape:

```text
vsetvli
vle8.v v8, (src)
vse8.v v8, (dst)
fence rw, rw
scalar byte compare
```

There is no fence between `vsetvli` and `vle8.v`. The final `fence rw,rw` is only used to make the store result visible before the scalar byte comparison.

Observed result:

```text
Assertion failed at .../LoadQueueReplay.sv:22765.
The simulation stopped. There might be some assertion failed.
Core 0: ABORT at pc = 0x80000258
Core-0 instrCnt = 5361, cycleCnt = 7468, IPC = 0.717863
Seed=241027 Guest cycle spent: 7473
Assertion failed
at LogUtils.scala:132 assert(false.B)
[ERROR][time=7472] SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner.lsq.loadQueue.loadQueueReplay:
vector load, should not have replay entry 8 when commit or flush.
```

This looks like a lifecycle handling issue between the vector load merge buffer feedback and `LoadQueueReplay`: a replay entry with the same `robIdx/uopIdx` can still be allocated when the VL merge buffer sends commit/flush feedback. The current RTL asserts that such an entry should not exist, but the nearby comment says these entries should be released when the VL merge buffer commits or flushes.


### Expected behavior

The core should not abort with a `LoadQueueReplay` assertion for this sequence.

When the VL merge buffer sends commit/flush feedback for a vector load, any stale vector replay entry with the same `robIdx/uopIdx` should be released or canceled instead of being replayed again or treated as an impossible state.

The workload should reach `HIT GOOD TRAP` after the scalar byte comparison passes.



### Environment

- Software
  - Operating system:Linux open07 6.17.0-23-generic #23~24.04.1-Ubuntu SMP PREEMPT_DYNAMIC Tue Apr 14 16:11:48 UTC 2 x86_64 x86_64 x86_64 GNU/Linux
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0

  ```
  Mill Build Tool version 0.12.3
  Java version: 21.0.10, vendor: Ubuntu, runtime: /usr/lib/jvm/java-21-openjdk-amd64
  Default locale: en_US, platform encoding: UTF-8
  OS name: "Linux", version: 6.17.0-23-generic, arch: amd64
  ```
- Repo
  - XiangShan commit id: `4d1d56db9374d8163c1475e0ebf41265ec85d240`


### To Reproduce

Minimal workload source is attached as:

```text
kmh-v2-vector-replay-min-poc/
```

It is standalone and contains only:

```text
.gitignore
Makefile
README.md
linker.ld
main.c
start.S
```

Build:

```sh
make -C tests/kmh-v2-vector-replay-min-poc
```

Run with the Kunminghu v2 difftest runner:

```sh
/path/to/kmh-v2/difftest/emu \
  -s 241027 \
  -i /path/to/kmh-v2-vector-replay-min-poc/build/kmh-v2-vector-replay-min-poc.bin \
  --diff /path/to/riscv64-nemu-interpreter-so \
  -e 0
```

Observed result on the unpatched Kunminghu v2 runner:

```text
ABORT at pc = 0x80000258
Core-0 instrCnt = 5361, cycleCnt = 7468
LoadQueueReplay: vector load, should not have replay entry 8 when commit or flush.
```

As additional context, I tested a local reference fix generated with the help of an AI coding assistant. I am not claiming this is the final or preferred fix; it is only provided as a debugging hint and as evidence for the suspected lifecycle issue. With this local reference fix that releases matching stale vector replay entries on VL merge-buffer commit/flush feedback, the same binary reaches:

```text
Core 0: HIT GOOD TRAP at pc = 0x80000070
Core-0 instrCnt = 5470, cycleCnt = 7911, IPC = 0.691442
Seed=241027 Guest cycle spent: 7917
```

### Additional context

[kmh-v2-vector-replay-issue.tar.gz](https://github.com/user-attachments/files/27742615/kmh-v2-vector-replay-issue.tar.gz)
