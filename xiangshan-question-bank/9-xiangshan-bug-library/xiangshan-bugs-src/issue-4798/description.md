### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

在进行香山性能分析的过程中，我们使用了Topdown中的脚本，对perf的性能计数器进行分析，在perf文件中，运行coremark-10次迭代得到的数据为ITLBMissBubble：316488。在运行SPEC2017时，我们发现ITLB的占比也非常高，占据了大部分前端Frontend的Bubble。
我们在源码找到了ITLBMissBubble计算的触发条件，在ICacheMainPipe.scala中的第596行。可以发现在这里ITLBMISS的触发条件和上面的icache_bubble_s0_wayLookup是相同的，看起来不是ITLB导致的Bubble的统计条件。

[TRANSLATION]

During our XiangShan performance analysis we used the Top-Down scripts to examine the perf performance counters. In the perf output, running CoreMark for 10 iterations produced an ITLBMissBubble count of 316,488. While running SPEC 2017 we likewise found that ITLB accounted for a very large share, making up most of the bubbles on the Front-end side.

We traced the trigger for ITLBMissBubble calculation to the source code, at line 596 in ICacheMainPipe.scala. There the trigger condition for ITLBMISS is the same as the one above for icache_bubble_s0_wayLookup, so it does not actually look like a bubble caused by an ITLB miss.

![Image](https://github.com/user-attachments/assets/39d82227-e766-4975-9e15-1273e58ab619)

### Expected behavior

ITLBMissBubble目前看来不太正确，他实际代表的信息是否有效，是否有必要Merge到前端Frontend中作为性能分析。

[TRANSLATION]

Right now, ITLBMissBubble doesn’t seem accurate. Is the information it actually represents meaningful, and is it really necessary to merge this metric into the Front-end for performance analysis?

### To Reproduce

运行coremark等程序，得到对应的rtl dump出来的perf文件即可发现问题。

[TRANSLATION]

Run CoreMark (or similar programs) and inspect the perf file dumped from the RTL; the issue becomes apparent.

### Environment

- XiangShan branch: 
- XiangShan commit id: 
- XiangShan config: 
- NEMU commit id: 
- SPIKE commit id:


### Additional context

_No response_
