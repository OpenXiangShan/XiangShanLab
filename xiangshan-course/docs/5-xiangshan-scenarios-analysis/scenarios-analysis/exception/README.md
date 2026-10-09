# Trap 跳转：从一条 ECALL 开始看懂异常处理

先阅读 `trap-execution-process.md`。它按“程序想做什么 → 波形实际发生什么 → 哪段源码解释它”的顺序，分析两次异常和两次返回。

- `trap-execution-process.md`：面向初学者的正文，含逐拍表和人工拉波形的清单。
- `source-excerpts.md`：与本次仿真版本一致的源码摘录，正文以 S1～S12 引用。
- `cycle-review.md`：C4250～C4444 连续 195 拍的可读索引，每拍标出有效 trap/CSR 事件与状态。
- `analyze_trap.py`：使用仓库 `tools/wavekit-xslab` 重新提取证据的脚本。
- `evidence/cycle-by-cycle.csv`：连续逐周期原始数值，包含没有发生有效传输的周期。
- `evidence/column-map.json`：CSV 列名到完整波形信号路径的映射。
- `evidence/stage-events.csv`：按 valid && ready 筛选的译码、重命名和派遣记录。
- `evidence/interface-cycles.csv`：关键派遣/发射/CSR 接口逐拍 valid、ready、fire 和 ROB 编号。
- `evidence/csr-snapshots.csv`：本次所选 CSR/difftest 状态字段快照，不将无关字段堆入正文。
- `evidence/commits.csv`：全程八个提交通道的有效记录。

测试源码、ELF、反汇编、仿真日志和完整 VCD 保持在 `/nfs/home/wanghao/doCIE/xs-env/nexus-am/apps/learnTrap`，不将大型波形复制到课程仓库。
