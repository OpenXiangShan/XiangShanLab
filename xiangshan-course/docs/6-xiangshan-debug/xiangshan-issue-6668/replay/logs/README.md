# 运行日志索引

四个构建、同一套用例、同一套仿真参数（`-C 20000 -I 60`）。

| 目录 | 构建 | 预期 |
| --- | --- | --- |
| `base09453/` | `09453ff8f`（issue 报告的缺陷基线，原样） | `xs_csrr_x0_vl`、`x0_readback` ABORT；`read_vlenb`、`x0_vtype_control` COMPLETED |
| `base09453-fix/` | `09453ff8f` + `../patch/0001`（最小修复） | 全部 COMPLETED |
| `head/` | `b9638259`（最新 v3，含上游 PR #6779 的修复） | 全部 COMPLETED |
| `revert/` | `b9638259` + `../patch/0002`（只回退那一行过滤） | `xs_csrr_x0_vl`、`x0_readback`、`x0_vtype_control` ABORT；`read_vlenb` COMPLETED |

每个目录包含：

- `summary.txt`：8 行结果（4 用例 × {`nodiff`, `nemu`}）。`base09453/`、`base09453-fix/` 的行尾还带 `instrCnt / cycleCnt`。
- `<case>.<mode>.log`：该次运行的精简日志（启动、加载、断言/陷阱、`instrCnt`、`Host time` 等关键行）。
- `<case>.<mode>.rc`：emu 进程退出码。
- `build.log.gz`：该构建的完整 `make emu` 日志。

`head/`、`revert/` 的日志来自首轮实验（脚本输出格式稍旧，只有分类没有计数）；缺陷签名（两条断言 + `instrCnt = 38, cycleCnt = 8,321`）在各目录的对应日志里可以直接对照。

完整（未裁剪）的 emu 输出只对两条 `xs_csrr_x0_vl` 运行保留为 `*.full.log.gz`；其余日志保留关键行，避免仓库体积膨胀。需要完整输出时按 README 的步骤重跑即可。
