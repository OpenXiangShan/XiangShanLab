# #6267 波形证据说明

本目录保留实际 replay 的日志、输入、关键波形提取值和两个可重新运行的提取脚本。原始 FST 和 xs-env 源码树留在本地 replay 工作目录，课程仓库不包含它们。

## 产物身份

| 对象 | 标识 |
| --- | --- |
| XiangShan | `b90dbba40d16d54f2814870ff8b4d8809a41e680` |
| xs-env | `33d5f6f611d15a65c6194290fa62caf0c0c27f41` |
| bundled REF 的 ready-to-run gitlink | `377a8548f5cde0fc0c37468d055ef46e52870784` |
| ELF SHA-256 | `2688f1ca2fbf7b92eb38bb5f237d2d461297e3eec49b6e28fdc343579ba7e053` |
| 原始 replay.fst 大小 | 56,165,502 bytes |
| 原始 replay.fst SHA-256 | `485a1bf2fe8726c8ed84f119fd10159605136ddbc0f5cfb9eaf945d1bebae73c` |
| replay.log SHA-256 | `edf18288fe773498f4cf82564e919227b327a048cd8353e21f7e525a971daec5` |

[replay.log](replay.log) 记录了实际输入、波形路径和 DUT commit。上述信息支持这些材料来自同次 replay，但缺少完整构建记录来严格证明现有可执行文件与源码树的对应关系。证据 JSON 中的绝对路径和解释器路径是历史采集元数据，不是新主机的运行要求。

## 采样与关键观测

提取使用 Wavekit 0.7.2，以 `TOP.clock` 上升沿采样。仿真器在 eval 后 dump，以下为沿后状态，不能把同一样本中新出现的 valid/ready 倒算为该沿已完成的握手。FST native 范围为 `[0,44327]`，未取得可靠 timescale 元数据，因此仅使用 raw ticks 和采样 cycle，不标 ns。

| 证据文件 | 内容 |
| --- | --- |
| [evidence.json](store-fault-analysis/evidence.json) | `[7138,7170)` 的 fsw 请求、地址与分类；`[7162,22164)` 的 15002 个无提交样本及 ROB187 停滞状态 |
| [head_audit.json](store-fault-analysis/head_audit.json) | `[6000,7180)` 的队头、virtual retire、physical redirect 指针及状态变化 |
| [event_state.json](store-fault-analysis/event_state.json) | cycle 6704、6705、6721、6724、7160、7161、7162 的选定信号快照 |
| [rows_redirect.json](store-fault-analysis/rows_redirect.json) | cycle `[6690,6740)` 的逐周期 redirect 与队列状态快照 |

`verify.py` 和 `head_audit.py` 检查选定信号的非空采样窗口、X/Z 掩码、时间轴对齐、reset 及输入文件元数据未变化。后两个 JSON 是历史辅助快照；本提交不声称其原始提取脚本具有与这两个校验脚本相同的未知值和轴检查。

## 复验

在本案例目录准备原始 `replay.fst`，或先执行 `./replay.sh` 生成新波形。使用安装了 Wavekit 0.7.2 和 NumPy 的 Python 环境执行：

```bash
python store-fault-analysis/verify.py --input replay.fst --output /tmp/6267-evidence.json
python store-fault-analysis/head_audit.py --input replay.fst \
  --output /tmp/6267-head.json --begin 6680 --end 6740 --quiet
```

2026-10-23 对原始 FST 重跑上述两项成功。`verify.py` 输出 `af=PASS, no_commit=PASS`：前者只证明实际 S2 的 `pmp.st=0` 与指定观测值一致，不代表硬件行为正确；后者确认长窗口内无有效提交。队头提取成功表示所选窗口及信号校验通过，根因判断还需结合 [report.md](report.md) 中的指针时间线和源码门控。

重新构建后的波形哈希可能不同；应核对事件链，不能仅以字节哈希判断是否复现。没有在整理课程提交时重新构建或重新仿真。

## 结论边界

本例已经建立“异常 vector store 提前释放 virtual entry → redirect 保留物理异常槽 → SQ 队头无出队路径 → 后续 fsw 及 ROB 停滞”的证据链。ROB145 的确切架构 PC 尚未独立建立；定位依赖 ROB 标识、队列槽及 vector-store 异常通道。物理 debug PC 经压缩，不能直接作为架构 PC。

REF 的 cause 7 是 timeout 后单独前进的结果，不是 DUT fsw 在 S2 产生 fault 的证明。更早的 vector store 已上报 store access fault，但未进一步确定其具体 PMA/PMP 规则来源。波形只覆盖有限窗口，持续停滞的机制由源码路径解释。
