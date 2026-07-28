---
name: xiangshan-bug-pipeline
description: End-to-end XiangShan bug pipeline: fetch GitHub issues and PRs, create per-issue bug-analysis-<issue-number> workdirs, build and run xs-env/XiangShan repros, and write waveform-based root-cause reports.
---

# XiangShan Bug Pipeline

## 适用场景
- 需要把 XiangShan 的 bug 收集、复现、波形分析和报告写作串成一条固定流程。
- 目标是对一个或多个 GitHub issue 做完整闭环，而不是只做其中某一步。

## 总流程

1. 先使用 `fetch-xiangshan-bugs` skill。
   - 生成或刷新 `xiangshan-bug-lib`。
   - 优先读取 `README.md`、`issue-index.md`、`bug-cause-summary.md` 来筛选目标 issue。

2. 对每个目标 bug issue，新建或复用一个工作目录。
   - 目录名固定为 `bug-analysis-xxxx`。
   - `xxxx` 必须是 GitHub issue 编号。
   - 一个目录只对应一个 issue。

3. 在该工作目录中搭建仿真环境。
   - `git clone git@github.com:OpenXiangShan/xs-env.git`
   - 进入 `xs-env` 后执行 `source setup.sh`
   - 再进入 `XiangShan` 目录
   - 按 issue 中给出的 commit hash 切换 RTL 版本
   - 执行 `make init`
   - 执行 `make emu EMU_TRACE=fst -j12`

4. 准备触发 bug 的测试程序。
   - 在 `nexus-am/apps` 中为该 issue 创建新的应用目录。
   - 复用 issue 里给出的下载渠道、源码仓库或二进制。
   - 如果下载遇到困难，先配置 SOCKS5 代理 `172.38.10.247:8970` 再重试。
   - 编译测试程序，直到生成 `build/PROG.bin`。

5. 运行带 difftest 的仿真。
   - 在 `XiangShan` 目录下执行：
     `./build/emu --diff ready-to-run/riscv64-nemu-interpreter-so --dump-wave-full -i <IMG_FILE>`
   - `IMG_FILE` 指向上一步生成的 `build/PROG.bin`。
   - 仿真时间很长时不要手动提前杀进程；让它自然结束或等待其自身报错停止。
   - 记录 emu 日志里打印的 FST 波形路径。

6. 使用 `xiangshan-bug-analysis` skill 做根因分析并写报告。
   - 结合 FST 波形、emu 日志、反汇编和 XiangShan 源码定位问题。
   - 输出 `bug-analysis-xxxx.md`，与工作目录编号保持一致。
   - 报告至少包含：复现环境、输入程序、波形路径、关键 cycle/time、源码位置、根因、修复思路。

## 执行与异常处理

- 每一步执行后都要立刻检查结果是否成功，包括：
  - 命令退出码
  - 日志中的 `error`、`failed`、`exception`、`timeout`、`panic`
  - 关键产物是否存在，例如 `xs-env`、`XiangShan/build/emu`、`build/PROG.bin`、波形文件、`bug-analysis-xxxx.md`
- 如果某一步失败，先在当前步骤内重试，不要直接跳到下一步。
- 同一步骤最多尝试 5 次。
- 5 次仍失败时，停止继续自动推进，并询问用户下一步怎么做。
- 如果错误来自环境问题、下载失败、编译失败或仿真提前退出，要把失败原因和最后一次命令的关键信息保留下来，再决定是否重试。
- 如果能从日志中明确判断是前一步输入不对、路径不对或产物缺失，应先修正输入再继续。
- 一个 issue 的完整流程要按步骤循环执行，但每个步骤都必须满足“执行 -> 检查 -> 通过后再进入下一步”。

## 约束
- 先收集，再复现，再分析，不要跳步。
- 报告中的结论必须能被波形和源码共同支撑。
- 如果 issue 中有多个 commit，只选与复现基线直接相关的那个。
- 如果工作目录已经存在，优先复用，不要覆盖用户已有结果。
