# Redirect 场景教学资料

本目录整理了 `redirect` 的课程材料，主题是香山 Kunminghu 中 `redirect / flush` 这条恢复链如何由前端、后端、ROB 与 IFU 共同完成。

## 资料清单

- [analysis.md](/nfs/home/wanghao/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/redirect/learn-redirect/analysis.md)：课程版教学文档，按 `redirect场景描述.md` 的主线展开。
- [redirect场景描述.md](/nfs/home/wanghao/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/redirect/learn-redirect/redirect场景描述.md)：原始场景说明归档。
- [demo/learn.c](/nfs/home/wanghao/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/redirect/learn-redirect/demo/learn.c)：测试程序源码。
- [demo/Makefile](/nfs/home/wanghao/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/redirect/learn-redirect/demo/Makefile)：构建入口。
- `demo/build/`：生成的 `elf/bin/objdump txt`。
- `waveform/learnRedirect.vcd`：本场景原始波形，文件较大。

## 阅读顺序

1. 先看 [analysis.md](/nfs/home/wanghao/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/redirect/learn-redirect/analysis.md) 的第 1、2、3 章，建立 redirect 的整体认识。
2. 再对照 `learn.c` 和反汇编，理解 `jal`、`jalr`、条件分支风暴、`fence.i` 四类触发源。
3. 最后回到 `analysis.md` 的第 19、20 章和 `验证特别注意`，把场景、代码和验证点串起来。

## 说明

本文档主线遵循 [redirect场景描述.md](/nfs/home/wanghao/XiangShanLab/xiangshan-course/docs/5-xiangshan-scenarios-analysis/scenarios-analysis/redirect/learn-redirect/redirect场景描述.md)，并按 `tools/xiangshan-wave-analysis` 与 `tools/analyze-xiangshan-wavekit` 的要求组织证据：先定场景，再看波形锚点，最后回到源码解释 redirect、flush、replay、contention 与 commit 保护。
