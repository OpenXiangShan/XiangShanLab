# Redirect / Flush 微架构场景分析讲解稿

## 1. 分析范围

这章节目标不是继续追一条具体指令的生命周期，而是把 `redirect / flush` 这一类微架构场景单独拎出来讲清楚：

- 它在香山里为什么存在
- 它大致经过哪些模块
- 会被哪些指令或事件触发
- 波形里应该看什么现象
- 典型的 `replay`、`flush`、`contention`、`exception` 场景分别是什么

这里使用的香山源码版本是：`556be598120db8e86b3a3e9f7fe6346e0e2127d4`。

## 2. 核心源码证据

这次主要围绕下面几条源码路径展开：

- 前端预测修正：
  [PredChecker.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala:104)
- IFU 接收并传播 flush：
  [Ifu.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/Ifu.scala:130)
- 前端把 `wbRedirect` 转成通用 redirect：
  [IfuRedirectReceiver.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ftq/IfuRedirectReceiver.scala:31)
- 后端分支执行单元产生 redirect：
  [BranchUnit.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala:55)
- 后端多个 redirect 候选做优先级选择：
  [RedirectGenerator.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala:31)
- CtrlBlock 把 redirect 广播回前端与译码：
  [CtrlBlock.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:368)
  [CtrlBlock.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:430)
- ROB 侧的 flush 与提交阻塞：
  [Rob.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:727)
  [Rob.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:835)

## 3. 理论概念与代码对象的对应关系

在教材或架构图里，我们通常会看到这些词：

- 分支预测错误
- 重定向
- 冲刷流水线
- 错路径指令清除
- 精确异常恢复

到了代码里，它们分别对应的是：

- 前端预测自纠错：`PredChecker` 发现 `jal/jalr/ret/notCFI/invalidTaken/targetFault` 后形成 `checkerRedirect`。 [PredChecker.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala:223)
- 后端执行纠错：`BranchUnit` 在 `isMisPred || hasBackendFault` 时拉起 `redirect.valid`。 [BranchUnit.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala:60)
- 后端多来源仲裁：`RedirectGenerator` 在执行单元 redirect、`loadReplay`、`robFlush` 之间选择真正有效的恢复事件。 [RedirectGenerator.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala:31)
- 前端恢复：`Ifu` 用 `s3_flush/s2_flush/s1_flush/s0_flush` 把旧取指块清掉。 [Ifu.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/Ifu.scala:131)
- 后端恢复：`CtrlBlock` 和 `ROB` 让译码、提交、状态更新停在正确边界。 [CtrlBlock.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:430) [Rob.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:864)

## 4. 这个机制为什么存在

如果处理器已经按错误的预测方向取了指，或者后端执行后才发现跳转目标不对，那么流水线里就会混进一批错路径指令。它们如果继续往后走，就会污染：

- 前端取指状态
- IBuffer / FTQ 元数据
- 后端 decode / rename / dispatch 的资源
- 最严重时还可能污染提交边界

所以 redirect / flush 的本质工作就是两件事：

1. 尽快告诉前端“下一条该从哪里重新开始取”。
2. 把错误路径上已经在路上的内容，按年龄和边界正确地清掉。

## 5. 这条机制里有哪些角色

### 5.1. 前端角色

前端这边最重要的角色是 `PredChecker` 和 `Ifu`。

`PredChecker` 的输入里已经拿到了：

- 指令是否有效 `instrValid`
- predecode 结果 `instrPds`
- 预测是否 taken `isPredTaken`
- 预测 target

也就是说，前端其实在“真正把指令送进后端之前”，就已经有机会先做一次预测正确性检查。相关定义见 [PredChecker.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala:34)。

### 5.2. 后端角色

后端这边有三类恢复来源：

- 执行单元分支错判：`oldestExuRedirect`
- 访存 replay：`loadReplay`
- ROB 发出的 flush：`robFlush`

这些信号统一进入 `RedirectGenerator`，说明香山并不是“谁发现错误谁直接把前端改了”，而是先过一个统一恢复入口，再决定真正向前广播的 redirect。对应代码见 [RedirectGenerator.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala:17)。

## 6. 正常动态路径

先用一句最简化的话概括这条路径：

前端或后端发现当前路径不对 -> 形成 redirect -> 统一仲裁 -> 广播回 FTQ/IFU/Decode/ROB -> 清理 wrong-path -> 从新 target 继续推进。

如果把它写成更接近看波形时的顺序，就是：

1. `PredChecker` 或 `BranchUnit/ROB` 先给出异常信号。
2. `RedirectGenerator` 选出本拍真正有效的恢复来源。
3. `CtrlBlock` 把 redirect 发给前端 FTQ，同时把 `decode.io.redirect.valid` 拉高。 [CtrlBlock.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala:368)
4. `Ifu` 把 `s3_flush` 一路往前传到 `s0_flush`。 [Ifu.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/Ifu.scala:131)
5. `ROB` 在恢复窗口内阻塞错误提交。 [Rob.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:864)

## 7. 前端自纠错场景

课程里最适合先讲的一类场景，是“前端自己就能发现明显不对”。

例如在 `PredChecker` 里，可以看到：

- direct jump 没预测 taken，会形成 `jalFaultVec`
- indirect jump 没预测 taken，会形成 `jalrFaultVec`
- return 没预测 taken，会形成 `retFaultVec`
- 非控制流指令却被预测成 taken，会形成 `notCfiTaken`

相关代码见 [PredChecker.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala:106)。

这一类场景的教学重点是：

- 错误并不一定要等到后端执行后才知道
- predecode 阶段已经能发现一批明显的方向型错误
- 一旦发现 fault，前端会先缩小有效范围 `fixedRange`，避免把更年轻的错误指令继续带下去。 [PredChecker.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala:136)

## 8. 后端分支纠错场景

另一类更经典的场景，是“前端觉得自己没问题，但后端真正算完以后发现 target 或方向不对”。

在 `BranchUnit` 里，核心逻辑非常直接：

- `targetWrong` 负责看真实 target 和预测 target 是否不同
- `isMisPred` 由方向错误或 target 错误组成
- 只要 `isMisPred` 为真，就形成后端 redirect

对应代码在 [BranchUnit.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala:53)。

这一类场景最值得学生建立的认识是：

- 前端预测给的是“猜测”
- 后端执行才给出“裁决”
- 一旦后端裁决错误，前端必须无条件回退到真实 target

## 9. 冲突场景：多个 redirect 同时出现怎么办

真正体现“微架构味道”的地方，不是某一个 redirect 单独触发，而是多个恢复来源同时来时怎么办。

`RedirectGenerator` 明确告诉我们：

- `oldestExuRedirect` 和 `loadReplay` 会先一起进入 `allRedirect`
- 通过 `Redirect.selectOldestRedirect(allRedirect)` 选最老者
- 但只要 `robFlush.valid` 为真，它又会压制掉这类次级 redirect

见 [RedirectGenerator.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala:31)。

这个地方特别适合讲“contention”这个词：

- 竞争的不是带宽，而是恢复权
- 谁先恢复，决定了哪批 younger 指令要被杀掉
- 如果优先级做错，最典型的问题不是性能差，而是恢复错对象

## 10. replay 场景为什么也会进入 redirect 链

任务要求里专门提到了 `replay`，这里很容易被初学者误解成“replay 只是访存内部重试”。

但从当前代码看，`loadReplay` 是直接作为 redirect 候选送进 `RedirectGenerator` 的。见 [RedirectGenerator.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/ctrlblock/RedirectGenerator.scala:18)。

这说明在香山里，有些 replay 不是局部重发一下就完事，而是已经上升到“要让前后端一起恢复一致状态”的级别。

所以课程里讲 replay 时，至少要区分两种理解：

- 局部 replay：只影响局部流水或局部请求
- 恢复型 replay：需要占用 redirect/flush 通道，影响全局时序

## 11. exception / interrupt / flush 场景

ROB 侧的这段代码也很关键：

`io.flushOut.bits.level := Mux(deqHasReplayInst || intrEnable || deqHasException || needModifyFtqIdxOffset, RedirectLevel.flush, RedirectLevel.flushAfter)`

对应位置在 [Rob.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:727)。

它说明：

- replay 指令
- 中断
- 异常
- 需要修正 FTQ offset 的情况

都会让 ROB 发出 flush 类恢复，而不是普通的“分支方向纠正”那么简单。

这部分在课程里应该强调：

- 分支错判和精确异常都能导致前端回退
- 但两者的语义不一样
- 前者更偏性能恢复，后者更偏精确状态恢复

## 12. commit 为什么还要额外阻塞

很多同学看到 redirect 广播出去之后，会下意识以为“事情已经结束了”。其实没有。

`ROB` 里还有一层专门的提交保护：

- `misPredBlockCounter`
- `deqFlushBlockCounter`
- 最终汇总成 `blockCommit`

相关代码见 [Rob.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/backend/rob/Rob.scala:840)。

这件事很好理解：

- redirect 只是告诉大家“前面错了”
- 但已经飞到提交窗口附近的 younger 指令，还需要被挡住
- 否则就会出现“明明已经知道走错路了，却还有错路径指令提交”的严重错误

## 13. 边界场景：半条指令与 `invalidTaken`

这个场景非常适合做课程里的“加分项”。

`PredChecker` 输出里有个很特别的字段：`invalidTaken`。 [PredChecker.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala:230)

`Ifu` 又会把它转成：

- `wbRedirect.isHalfInstr`
- `wbRedirect.halfPc`
- `wbRedirect.halfData`

见 [Ifu.scala](/nfs/home/wanghao/xs-env/XiangShan/src/main/scala/xiangshan/frontend/ifu/Ifu.scala:784)。

这说明 redirect 处理的并不只是“PC 改一下”这么粗糙，它还要考虑：

- fetch block 边界
- RVC / half instruction 拼接边界
- IBuffer 之前的半条指令缓存状态

也就是说，这已经是一个典型的跨边界微架构场景。

## 14. 看波形时应该抓哪些信号

如果后面你要真正配合 `learnRedirect` 的波形来讲，最少建议固定抓下面几组信号：

### 14.1. 前端检测与冲刷

- `checkerRedirect.valid`
- `checkerRedirect.bits.misIdx`
- `checkerRedirect.bits.target`
- `wbRedirect.valid`
- `s3_flush/s2_flush/s1_flush/s0_flush`

### 14.2. 后端恢复来源

- `oldestExuRedirect.valid`
- `loadReplay.valid`
- `robFlush.valid`
- `stage2Redirect.valid`
- `stage2oldestOH`

### 14.3. 提交保护

- `io.redirect.valid`
- `io.flushOut.valid`
- `misPredBlockCounter`
- `blockCommit`
- `io.commits.isCommit`

如果这几组信号都能跟到，基本就能把一个 redirect 场景讲完整了。

## 15. 建议优先讲的五个典型场景

如果把这一章做成课程内容，我建议优先讲下面五个场景。

### 15.1. 场景一：前端 direct jump 未预测 taken

这是最适合入门的场景。

它的特点是：

- 触发简单
- 波形现象明显
- 不依赖后端很深的执行路径

学生能直接看到：`jalFaultVec -> checkerRedirect -> wbRedirect -> flush` 这一条链。

### 15.2. 场景二：后端分支 target 计算后发现错误

这是最经典的 mispredict 恢复场景。

这里最适合讲清楚一句话：

“前端负责猜，后端负责判。”

### 15.3. 场景三：`oldestExuRedirect` 和 `loadReplay` 同拍竞争

这是最典型的 `contention` 场景。

这里建议重点让学生观察：

- 谁更老
- 谁真正进入 `stage2Redirect`
- loser 是被延后、被重试，还是直接被压掉

### 15.4. 场景四：`robFlush` 压制普通 redirect

这是最典型的“精确恢复优先于普通恢复”场景。

课程里可以借这个场景把：

- branch mispredict
- replay recovery
- exception / interrupt flush

三类恢复语义区分开。

### 15.5. 场景五：redirect 后 commit 仍被暂时阻塞

这是最容易漏讲、但最能体现严谨性的场景。

如果只讲“前端回退”，学生会以为 redirect 只是前端问题。实际上 `ROB blockCommit` 这段逻辑明确说明：恢复必须一直保护到提交边界。

## 16. 当前任务里的实际限制

这里需要明确说一下当前任务上下文：

- `波形生成.txt` 指向的是 `/nfs/home/wanghao/xs-env/nexus-am/apps/learnRedirect`
- 但当前 `/nfs/home/wanghao/xs-env/nexus-am/apps/learnRedirect/learn.c` 只有一个空 `main()`

这意味着：

- 现在可以先把“应该分析什么场景”定义清楚
- 但还没有真实程序去稳定触发这些场景
- 所以这篇讲解稿目前更适合作为课程脚本和后续补测试的指导文稿

## 17. 本章结论

把这章内容压缩成一句话，就是：

`redirect / flush` 不是单一模块的动作，而是一条跨前端、后端、FTQ、Decode、ROB 的恢复链。它处理的不只是“跳到哪里”，还处理“谁该被杀、谁还能活、谁暂时不能提交”。

所以在微架构场景分析里，`redirect` 最值得讲的从来不只是分支错判本身，而是它背后的：

- wrong-path 清除
- replay/flush 统一恢复
- 多来源竞争优先级
- commit 边界保护
- 半条指令和边界状态修复

这几件事合在一起，才构成一个完整的 redirect 微架构场景。

## 18. 后续建议

如果要把这一章真正做成“带波形的课程讲解”，下一步最合适的是补三类最小测试：

1. 一个稳定触发 direct jump 前端修正的测试。
2. 一个稳定触发后端 branch mispredict 的测试。
3. 一个带异常或 replay 的恢复测试。

这样就能把本文里定义的场景和真实波形一一对应起来。

## 19. 验证特别注意

| 验证点 | 风险 | 建议观察 |
| --- | --- | --- |
| redirect 来源唯一性 | 多个恢复源同时泄露到下游 | 看 `stage2Redirect.valid` 与最终 `toFtq.redirect.valid` |
| wrong-path kill | 错路径指令仍然进入 decode / commit | 看 `decode.io.redirect.valid` 与 `blockCommit` |
| replay 恢复路径 | replay 被误当作普通局部重试 | 看 `loadReplay` 是否进入 `RedirectGenerator` |
| flush 优先级 | `robFlush` 没压住普通 redirect | 看 `robFlush.valid` 同拍时 `stage2Redirect.valid` 的表现 |
| 边界状态修复 | 半条指令状态丢失 | 看 `invalidTaken`、`isHalfInstr`、`halfPc/halfData` |

