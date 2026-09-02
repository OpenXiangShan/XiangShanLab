# 香山昆明湖 V3 Issue #6132 分析：自修改代码下的 `fence.i` 与旧 `cfVec`

## 1. 核心结论与证据边界

Issue #6132 记录了一个容易误判的现象：自修改代码执行 `fence.i` 后，修改前的指令字仍短暂出现在前端送往后端的 `cfVec` 接口。内部接口出现旧数据不等于该指令仍然有效，更不等于它已经执行或退休；判断正确性必须继续追踪 redirect、动态指令身份和微架构副作用。

本文结论如下：

1. **V3 Issue 已观察到接口现象，本地尚未独立复现 V3**：报告波形中，ICache 侧注册 `fence.i` 后，`cfVec` 连续两拍携带修改前的指令字。
2. **PoC 未观察到功能错误，但不能据此证明所有路径都安全**：程序最终执行修改后的函数并返回 `0x5a`；严格监视器未发现旧编码 load 进入 LSQ、写回或退休。V3 上从 `cfVec` 到退休的动态身份链仍待补齐。
3. **V2 波形只用于解释机制**：本地 `kunminghu-v2` 对照确认了 ICache 全量失效和 ROB/FTQ redirect 两条独立路径，不能替代 Issue 所用 V3 提交的验证。
4. **V2 的 96 周期是两个动态 `fence.i` 副本之间的错误路径窗口**：窗口为 `[C4235,C4331)`，真正有效的副本在 C4331 才进入 `cfVec`，ICache 全量失效在窗口外 C4340。它不是 V3 旧指令在 `fence.i` 后持续 96 周期。
5. **V2 窗口中的 Cache 填充与 11 条错误路径指令没有已证因果关系**：两次 L1D 填充来自窗口前 load 和已提交 store；六次 L2 填充来自窗口前 prefetch 和同一条 load。
6. **当前定性**：#6132 是需要继续验证的安全相关功能增强议题，不是已确认的功能 Bug，也没有形成已确认的可利用侧信道。

| 证据来源 | 已支持的结论 | 不能推出的结论 |
| --- | --- | --- |
| V3 Issue 波形与 PoC | 旧指令字在 `cfVec` 瞬时可见；最终执行结果正确；选定监视点未见旧 load 副作用 | 本地已复现 V3；所有旧条目必然在产生影响前被清除 |
| V2 本地波形 | 两个动态副本、两条冲刷路径和错误路径条目的去向；Cache 请求可按来源归因 | V3 的 C16750-C16751 必然由相同路径造成 |
| 当前安全证据 | 尚无“旧内容条目执行秘密访问并形成可测状态”的完整链路 | 已证明不存在任何侧信道，或已证明存在可利用漏洞 |

下文中的“已确认”只对紧邻标注的版本和证据成立，不跨 V2/V3 外推。

## 2. Issue 现象与 PoC

### 2.1 测试对象

| 项目 | 内容 |
| --- | --- |
| Issue | #6132 |
| 分支与测试提交 | `kunminghu-v3`，`3931c5112c528299a23c256bdd77fb90813afa6e` |
| 编译器 | `riscv64-unknown-elf-gcc 15.1.0` |
| 仿真方式 | `emu --no-diff --dump-wave-full` |
| 报告时分类 | `topic: security`、`type: feature/requested` |

PoC 先把旧函数加载到 ICache，再通过普通 store 改写函数体。目标地址及编码如下：

| PC | 修改前 | 修改后 |
| --- | --- | --- |
| `0x80000080` | `ld t5,0(a1)`，`0x0005bf03` | `addi a0,zero,0x5a`，`0x05a00513` |
| `0x80000084` | `addi a0,zero,0x11`，`0x01100513` | `ret`，`0x00008067` |
| `0x80000088` | `ret`，`0x00008067` | `0x00000000`（前一条为 `ret`，不会执行） |

本 PoC 的执行序列为：

```text
调用旧函数，使旧代码进入 ICache
  -> store 写入新函数体
  -> fence rw,rw
  -> fence.i
  -> jal 再次调用目标函数
```

`fence rw,rw; fence.i` 是本测试选择的序列。关键要求是代码写入满足相应的可见性条件，并由按序执行的 `fence.i` 建立后续取指的一致性边界。

### 2.2 V3 Issue 时间线

| 周期 | 观测事件 |
| ---: | --- |
| C16746-C16747 | 后端侧识别到 `fence.i` |
| C16748-C16749 | ICache 侧注册收到 `fence.i` |
| C16750-C16751 | `cfVec` 再次出现旧函数的三个指令字 |
| C16856-C16857 | `cfVec` 出现修改后的 `addi 0x5a; ret` |

C16750-C16751 的三个 slot 为：

```text
pc=0x80000080, instr=0x0005bf03
pc=0x80000084, instr=0x01100513
pc=0x80000088, instr=0x00008067
```

最终函数返回 `0x5a`，说明本次运行的架构结果正确。Issue 以 ICache 注册 `fence.i` 的周期作为监视分界，并推测未直接接入专用 `fence.i` 的 MainPipe、WayLookup 或预取响应路径可能让失效前的响应到达 `cfVec`。这个分界适合发现现象，但不能代替动态身份和 redirect 检查。

### 2.3 两个对照实验

- **秘密索引变体**：旧函数包含秘密值加载、移位、地址计算和探针加载。旧指令块仍出现在 `cfVec`，但监视器未发现选定探针 Cache 行被访问，也未发现旧编码 load 进入 LSQ 或写回。这是有限范围的负证据，不是完整的信息流证明。
- **顺序落入变体**：`fence.i` 后不使用 `jal`，而是顺序进入修改后的目标地址。该版本只观察到新指令，说明现象与重定向、预测和重新取指的时序有关，并非所有 `fence.i` 后取指都会返回旧内容。

Issue 的原始声明也只覆盖 `cfVec` 的旧输出，没有声称旧指令退休、秘密访问完成、形成 Spectre 风格侧信道或产生错误架构结果。

### 2.4 `fence.i` 需要覆盖的两类状态

自修改代码先从数据侧写入新指令，再从指令侧读取同一地址。数据写入完成并不保证 ICache 已丢弃旧 tag/data，也不保证此前发出的取指或预取响应不会在稍后返回。因此实现需要同时处理：

- **指令存储一致性**：恢复后的有效取指不能命中旧 ICache 元数据，失效前在途 miss 的迟到响应也不能把旧内容重新安装为有效 line。
- **推测流水失效**：程序顺序位于 `fence.i` 后、但已提前进入 IFU、IBuffer 或后端早期级的条目必须被清除；`fence.i` 前的指令和 `fence.i` 本身仍要完成。

架构保证关注的是恢复后的有效执行，不要求所有内部接口在同一周期清零。反过来，架构结果正确也不自动证明没有瞬时微架构副作用。本文使用下列最小术语集区分这些层次：

| 术语 | 含义 |
| --- | --- |
| `cfVec.fire` | `valid && ready`，只表示前端到后端边界完成传输 |
| 动态身份 | PC/编码加 FTQ pointer、offset、ROB 序号；用于区分同一静态指令的不同副本 |
| 程序顺序 | 相对同一动态 `fence.i` 的前、边界自身或后，与采样周期无关 |
| 失效前请求 | 在按序失效请求被接受前已发出的 fetch/prefetch；可能在失效开始后返回 |
| redirect/flush | 放弃旧预测路径并从新 PC 恢复的控制动作，不等同于 ICache 全相失效 |
| Cache footprint | tag、有效位、一致性、替换状态或访问延迟等可观察变化；必须追溯其请求来源 |

## 3. 判断框架与实现契约

### 3.1 `cfVec` 不是执行或退休接口

`frontend.io_backend_cfVec` 是 IBuffer 向后端传输控制流信息的 `Decoupled` 接口。条目在此处完成 `valid && ready` 握手后，仍可能被程序顺序位于它之前的异常或 redirect 清除。

```text
ICache -> IFU / IBuffer -> cfVec -> Decode/Rename/Dispatch
                                      -> ROB/IQ/LSQ -> EXU -> Retire
                   ^                         |
                   +-------- redirect -------+
```

同一条目必须分别记录三种关系：

1. **程序顺序**：位于动态 `fence.i` 前、就是边界自身，还是位于其后。应由 FTQ/ROB 指针和 slot 判断。
2. **请求时序**：产生该响应的取指请求是在 `fencei_req` 接受前发出，还是在完成失效并恢复后发出。
3. **控制时序**：条目在匹配的 backend redirect 生效前还是生效后被采样，以及 redirect 后是否仍有资格前进。

失效前发出的预测取指可以包含程序顺序位于 `fence.i` 后的指令，其响应又可能在 `io.fencei` 拉高后才返回。因此“响应在后”“请求在前”“程序顺序在后”可以同时成立，单纯比较周期无法判定架构有效性。

### 3.2 两条冲刷路径

Issue 对应设计的契约是：每条按序执行的 `fence.i` 都伴随 backend redirect。两条物理路径职责不同：

| 路径 | 主要作用 |
| --- | --- |
| Fence FU -> `io.fencei` -> MetaArray/MissUnit | 清除 ICache 有效元数据，使本地在途结果失效或禁止安装；已下发到下层的事务仍可能完成 |
| ROB `flushPipe` -> FTQ redirect -> `io.flush` | 清除 MainPipe、PrefetchPipe、WayLookup、IFU/IBuffer 等推测流水状态，并从顺序后继 PC 恢复 |

官方设计文档据此没有把专用 `io.fencei` 重复连接到所有前端流水级。若 redirect 契约、队列优先级和同拍握手在所有路径上成立，`cfVec` 短暂出现旧数据可以只是随后被清除的推测状态；但不可回滚的 Cache、预测器或预取器更新仍须单独验证。

后端译码中的 `FENCE_I` 带有：

```text
noSpec = true, blockBack = true, flushPipe = true
```

`blockBack` 是 Decode 属性，Dispatch 侧以 `blockBackward` 落实阻塞。它们体现“禁止后继越过屏障”的设计意图，与 PoC 未见旧 load 进入 LSQ/DCache 的结果一致，但不能替代 V3 端到端门控、redirect 优先级和副作用检查。

### 3.3 正确的验证判据

功能监视器至少要关联：

- 动态 `fence.i` 的 FTQ/ROB 身份及 `fencei_req`、完成和 backend redirect 周期；
- `cfVec` 的 valid/ready、FTQ pointer、offset 和 PC；
- 对应取指请求的发起时间或 MSHR 身份；
- 旧内容条目是否进入 Decode、Rename、Dispatch、ROB、IQ、LSQ、EXU 和写回；
- 恢复后实际退休的目标函数版本。

功能判据可写为：

```text
对程序顺序位于 fence.i 后且携带修改前内容的条目：
  匹配的 redirect 生效后，不得再分配 ROB/LSQ、执行、写回或退休；
  恢复后，第一个可退休的目标函数体必须是修改后的版本。
```

安全判据更严格：即使条目最终被清除，也要检查其是否在此前改变 TLB、预测器、预取器、L1I/L1D/L2 或其他攻击者可观察状态，并证明状态变化确由该动态条目触发。时间重叠和最终 Cache 状态都不足以单独建立因果关系。

## 4. V2 本地对照：动态副本与控制链

### 4.1 实验对象与采样口径

本地对照程序为 [smc_fencei_direct_probe.S](/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6132/bug-replay/smc_fencei_direct_probe.S)，其旧函数、补丁和 `fence rw,rw; fence.i; jal` 序列与直接加载 PoC 一致。

- 源码分支：`kunminghu-v2`
- 源码提交：`0fa7bb8259a7922481289d8d5932797afce84030`
- emu 生成的波形路径：`/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6132/bug-replay/2026-08-31-11-43-57.fst`
- 当前归档：`bug-replay/wave.zip`，内部文件名 `2026-08-31-11-43-57.fst`
- 波形大小：83,971,898 字节
- SHA-256：`496bc90fb306ced17a1ff52e37cbd551a1631a67a741a43e6d338c0b0b356d78`

波形使用 `wavekit.FstReader`，并设置 `sample_on_posedge=True`。该文件中 `FST time = 2 * cycle`。所有 `Decoupled` 接口仅在 `valid && ready` 时计为传输；`valid=0` 时残留的数据字段不作为证据。动态身份由 PC、编码、FTQ pointer/offset 和 ROB 序号联合确定。

V2 与 Issue 所用 V3 不是同一分支。本节只能验证该次 V2 构建中的控制链和归因方法。

### 4.2 同一静态指令的两个动态副本

跟踪对象是 `PC=0x8000004c`、编码 `0x0000100f` 的 `fence.i`：

| 副本 | `cfVec.fire` | FTQ 身份 | 去向 |
| --- | ---: | --- | --- |
| 第一个 | C4235/T8470 | FTQ9/off12 | 先后停在 Decode/Rename 与 Rename/Dispatch 边界，C4318 被程序顺序位于它之前的普通 `fence` 清除；未进入 ROB/IQ/Fence FU |
| 第二个 | C4331/T8662 | FTQ10/off0 | 分配 ROB23，进入 Fence FU，触发 ICache 失效并正常退休 |

第一个副本前的普通 `fence rw,rw` 位于 `PC=0x80000048`、ROB22。它在等待 Store Buffer 排空后产生 redirect，将第一个 `fence.i` 及其错误路径后继清除。C4331 是重取后的新动态副本，不能把 C4235 与 C4339 拼成同一 uop，并把 104 周期误报为 `fence.i` 的执行延迟。

### 4.3 第一个副本如何被清除

第一副本的 ready/valid 轨迹说明，“接口上传输过”和“实际获得后端资源”是两个不同事件：

| 周期 | 第一副本与前序 `fence` 的状态 |
| ---: | --- |
| C4234 | 普通 `fence rw,rw` 从 `cfVec` 进入后端，后续分配 ROB22 |
| C4235 | 目标 `fence.i` 第一副本完成 `cfVec[0].fire`，FTQ9/off12 |
| C4236-C4285 | 第一副本停在 Decode->Rename 输出，`valid=1, ready=0` |
| C4286 | Decode->Rename 终于 fire；波形数据出现候选 ROB23，但 ROB 尚未分配 entry |
| C4287-C4317 | 第一副本停在 Rename->Dispatch，持续 `valid=1, ready=0`；`dispatch.io_enqRob.req[0].valid` 没有为它形成真实入队 |
| C4290 | ROB22 的普通 `fence` 进入 Fence FU，`fuOpType=0x10` |
| C4291-C4311 | Fence FU 位于 `s_wait`，持续要求 SBuffer 排空 |
| C4312-C4313 | `sbIsEmpty` 成立，普通 `fence` 完成 |
| C4316 | ROB22 产生 `rob.io_flushOut.valid=1`，身份为 FTQ9/off10 |
| C4317 | `s1_robFlushRedirect` 生效；第一副本仍在 Dispatch，但不能 fire |
| C4318 | 第一副本及其错误路径后继从各级消失 |
| C4322 | backend redirect 到达 Frontend，目标为 `0x8000004c` |
| C4331 | 重取后的第二副本完成新的 `cfVec.fire` |

这里出现的“候选 ROB23”只是组合数据。只有 ROB enqueue 接口真正握手，才能认为 entry 已建立。相同原则也适用于候选 pdest、LSQ index 和各执行队列字段。

### 4.4 存活副本的关键周期

| 周期 | 阶段与证据 |
| ---: | --- |
| C4331 | 第二个副本完成 `cfVec[0].fire`；FTQ10/off0，译码为 `FuType.fence`、`fuOpType=0x12`、`flushPipe=1` |
| C4332 | Rename 传输，获得 ROB 候选值 23 |
| C4333-C4334 | Dispatch，随后真实入 ROB23 和 Fence Issue Queue |
| C4335-C4336 | Issue delay、DataPath 和 bypass 流水 |
| C4337 | Exu7/Fence FU 接受 ROB23 |
| C4338 | Fence FSM 位于 `s_wait`，`flushSb=1`、`sbIsEmpty=1` |
| C4339 | FSM 进入单拍 `s_icache`；Fence 输出、backend `fencei` 和 frontend 原始 `fencei` 同拍有效 |
| C4340 | Frontend 的 `RegNext` 使 `ICache.io_fencei=1`、`MetaArray.flushAll=1`、`MissUnit.io_fencei=1`；ROB 同拍收到 ROB23 写回 |
| C4342 | ROB23 产生 `flushOut`；同拍的 `ICache.io_flush` 实际来自 IFU `pdWb.misOffset`，不是该 ROB redirect |
| C4343-C4347 | CtrlBlock 传播 ROB flush redirect |
| C4348 | 匹配 ROB23 的 backend redirect 到达 Frontend/FTQ，目标 `0x80000050`；此时只有普通 `io.flush`，没有 `flushAll` |
| C4349 | ROB/Difftest 记录 `fence.i` 正常提交 |

因此，实际副本从 `cfVec.fire` 到后端原始 `fencei` 为 8 周期，到 ICache 消费并执行 `flushAll` 为 9 周期。

### 4.5 源码链

| 模块 | 关键实现 |
| --- | --- |
| `DecodeUnit.scala:228` | `FENCE_I` 选择 Fence FU、`FenceOpType.fencei`，并设置 `noSpec/blockBack/flushPipe` |
| `Fence.scala:47-87` | FSM 接受 uop 后先进入 `s_wait` 排空 SBuffer，再以单拍 `s_icache` 产生 `fencei` |
| `XSCore.scala:128` | Backend 的 `fenceio.fencei` 组合连接到 Frontend |
| `Frontend.scala:209` | `icache.io.fencei := RegNext(io.fencei)`，因此 ICache 晚一拍消费 |
| `ICache.scala:362,634` | `flushAll` 清空各 way 的 valid bitmap；MissUnit 同时收到 `fencei` |
| `ICacheMissUnit.scala:127,311,394` | 阻止新请求或 SRAM 写入，并清理本地队列；已下发事务的返回仍需被正确吸收 |
| `RobBundles.scala:139`、`Rob.scala:571,622` | `flushPipe` 记录为 `needFlush`，ROB 头部随后产生 `flushOut` |
| `CtrlBlock.scala:333`、`NewFtq.scala:1261` | ROB flush 延迟为 backend redirect；FTQ 将 backend/IFU redirect 汇成普通 `icacheFlush` |

Fence FSM 的关键关系可以压缩为：

```scala
sbuffer := state === s_wait
fencei  := state === s_icache

when (state === s_idle && io.in.valid) {
  state := s_wait
}
when (state === s_wait && func === FenceOpType.fencei && sbEmpty) {
  state := s_icache
}
when (state =/= s_idle && state =/= s_wait) {
  state := s_idle
}

io.in.ready  := state === s_idle
io.out.valid := state =/= s_idle && state =/= s_wait
```

这解释了 C4337-C4339：C4337 接受 uop，C4338 必经 `s_wait`；即使 `sbIsEmpty=1`，也要到 C4339 才进入持续一拍的 `s_icache`。输出携带 ROB/`flushPipe` 元数据，但不写通用寄存器。

Frontend 到 ICache 的一拍延迟和 ICache 内部职责为：

```scala
frontend.io.fencei <> backend.io.fenceio.fencei
icache.io.fencei := RegNext(io.fencei)

metaArray.io.flushAll := io.fencei
missUnit.io.fencei := io.fencei
missUnit.io.flush  := io.flush
```

MetaArray 在 `flushAll` 时清除全部 way 的 valid bitmap。MissUnit 则禁止相关本地请求继续安装到 Meta/Data SRAM，并清理 priority FIFO。这里的“清理”不等于撤销已经下发的 TileLink 事务；按设计契约，返回 grant 不得重新成为有效 L1I 内容，但同拍及已离开 MSHR 的响应仍需结合下游验证。

`flushPipe` 的恢复链在直接失效之后发生：

```text
C4340  ROB 写回收到 ROB23/flushPipe，同时 ICache 执行 flushAll
C4341  ROB23 的 commit_w 与 needFlush 已建立
C4342  rob.io_flushOut.valid，level=flushAfter
C4343  s1_robFlushRedirect
C4344-C4347  CtrlBlock 延迟链
C4348  backend redirect 到 Frontend/FTQ，target=0x80000050
C4349  Difftest 记录 fence.i 提交
```

C4342 的 `rob.io_flushOut` 与 IFU 产生的 `ICache.io.flush` 恰好同拍，不能据此把后者归到 ROB23。真正匹配 ROB23 的 FTQ redirect 到 C4348 才出现；这也是必须同时核对 redirect 身份和来源 valid 的例子。

波形中三个相邻但语义不同的事件为：

| 周期 | `fencei/flushAll` | 普通 `io.flush` | 来源 |
| ---: | ---: | ---: | --- |
| C4340 | 1 | 0 | Fence FU 直接失效路径 |
| C4342 | 0 | 1 | IFU predecode redirect |
| C4348 | 0 | 1 | ROB23 `flushPipe` 的 backend redirect |

这份 V2 波形确认 ICache 全量失效和前端流水恢复是两张物理网络，也说明只按信号名或静态 PC 关联事件会得到错误结论。它没有解释 V3 C16750-C16751 旧条目的具体来源和 kill 点。

## 5. V2 96 周期窗口的因果归因

### 5.1 窗口定义与错误路径

窗口 `[C4235,C4331)` 从被清除的第一个 `fence.i` 副本完成 `cfVec.fire` 开始，到重取后的第二个副本完成 `cfVec.fire` 为止，共 96 周期。它包含第一个副本及其预测后继的排队、阻塞、清除和重取，不包含第二个有效副本在 C4340 触发的 ICache 全量失效。

窗口内 `cfVec.fire` 只发生在三拍，共 11 条动态指令、10 个静态 PC。`0x8000005c` 因预测回跳出现两个动态副本：

| 周期/lane | PC/指令 | FTQ | 最深位置与结果 |
| --- | --- | --- | --- |
| C4235/0 | `0x8000004c / 0000100f`，`fence.i` | 9/off12 | C4286 Rename fire，候选 ROB23；停在 Dispatch，C4318 清除 |
| C4235/1 | `0x80000050 / 030000ef`，`jal 0x80000080` | 9/off14 | 候选 ROB24/pdest18；停在 Dispatch，RAT/free-list 随 redirect 恢复 |
| C4242/0 | `0x80000080 / 0005bf03`，旧 `ld` | 10/off0 | 进入 Decode->Rename pipeline，未实际 Rename、未分配 LQ |
| C4242/1 | `0x80000084 / 01100513`，旧 `addi` | 10/off2 | 只出现候选 ROB26/pdest20，未分配 |
| C4242/2 | `0x80000088 / 00008067`，旧 `ret` | 10/off4 | 只出现候选 ROB27，未分配 |
| C4287/0 | `0x80000054 / 05a00e13`，`addi t3,zero,90` | 11/off0 | Decode 输入，`ready=0` |
| C4287/1 | `0x80000058 / 01c51863`，`bne` | 11/off2 | Decode 输入，`ready=0` |
| C4287/2 | `0x8000005c / 000061b7`，`lui gp,0x6` | 11/off4 | Decode 输入，`ready=0` |
| C4287/3 | `0x80000060 / 00d1819b`，`addiw gp,gp,13` | 11/off6 | Decode 输入，`ready=0` |
| C4287/4 | `0x80000064 / ff9ff06f`，`jal zero,0x8000005c` | 11/off8 | Decode 输入，`ready=0` |
| C4287/5 | `0x8000005c / 000061b7`，第二动态副本 | 12/off0 | Decode 输入，`ready=0` |

前端预测路径为：

```text
0x8000004c fence.i
0x80000050 jal/call  -> 0x80000080
0x80000080 ld
0x80000084 addi
0x80000088 ret       -> 0x80000054
0x80000054 ... 0x80000064
0x80000064 jump      -> 0x8000005c
```

call、ret 和无条件跳转都由前端预测展开。它们越过 `cfVec` 边界并不代表 BJU 已执行这些跳转；最深阶段仍以每行的真实握手为准。

关键控制事件如下：

| 周期 | 事件 |
| ---: | --- |
| C4234 | 程序顺序更早的普通 `fence` 从 `cfVec` 进入后端，后续分配 ROB22 |
| C4286 | 第一组完成 Rename；第二组进入 Decode->Rename pipeline |
| C4287 | ROB22 入队并以 `blockBackward/flushPipe` 阻止后继 Dispatch；第三组同时进入 `cfVec` |
| C4290-C4313 | ROB22 在 Fence FU 等待 SQ/SBuffer 排空并完成 |
| C4316-C4318 | ROB22 产生 `flushOut`，redirect 清除上述 11 条错误路径条目 |
| C4322 | backend redirect 到 Frontend，重取目标为 `0x8000004c` |
| C4331 | 第二个有效 `fence.i` 副本重新进入 `cfVec` |

对 ROB、Issue Queue、LSQ、27 路写回和退休端口的身份交叉扫描结果为：

| 动作 | 11 条错误路径指令的结果 |
| --- | ---: |
| 真实 ROB allocation | 0 |
| IQ enqueue / issue / EXU 接受 | 0 |
| LQ/SQ allocation及 DCache 请求 | 0 |
| 写回与退休 | 0 |

端口级核对包括：

- `rob.io_enq.req[0..5]` 没有候选 ROB23-27；C4287 的真实入队是程序顺序更早的 `PC=0x80000048 / ROB22`。
- `dispatch.io_toIssueQueues[0..33]` 没有这些候选 ROB 的 enqueue。
- `dispatch.io_toMem.lsqEnqIO.req[0..5]` 没有错误路径 `ld` 的 LQ enqueue。
- 27 路后端 writeback 和 ROB commit 均没有匹配这 11 条动态身份。

C4283 出现过 `0x80000080/84/88` 的提交记录，但它们属于窗口开始前的 ROB11/12/13 副本，不能仅按静态 PC 与 FTQ10 的候选 ROB25/26/27 拼接。上述负证据来自跨端口的身份匹配，而不是只根据某一级 `ready=0` 推测。

错误路径 `jal` 曾使 speculative RAT 的 `x1` 暂时从 p8 指向 p18，redirect 后恢复，p18 未写回并重新可用。该现象说明可回滚的 Rename 状态确实短暂变化，但不能据此推断 Cache 等不可自动回滚状态也由这些条目改变。

### 5.2 Cache 活动的真实来源

窗口中的三条代码修改 store 位于第一个 `fence.i` 之前：

| PC | ROB/SQ | 地址与数据 |
| --- | --- | --- |
| `0x80000028: sw t0,0(s0)` | ROB17/SQ0 | `0x80000080 <- 0x05a00513` |
| `0x8000002c: sw t1,4(s0)` | ROB18/SQ1 | `0x80000084 <- 0x00008067` |
| `0x80000030: sw zero,8(s0)`，编码 `0x00042423` | ROB19/SQ2 | `0x80000088 <- 0` |

它们在 C4288 前后提交，C4289-C4290 合并到 SBuffer，C4294 发出对 `0x80000080` 的请求，C4309 完成 write-allocate 并形成 Dirty L1D line。redirect 后保留已提交 store 的结果是正确行为。

所有主要 Cache 状态可归因为：

| 层级/Line | 请求或原状态 | 窗口内事件 | 因果结论 |
| --- | --- | --- | --- |
| L1D `0x80001000` | ROB11 load 在 C4228 请求、C4231 发 Acquire，均早于窗口 | C4282 完成 refill，状态 Trunk | 不是 C4242 的错误路径 `ld` 发起 |
| L1D `0x80000080` | ROB17-19 三条已提交 store | C4294 请求，C4309 write-allocate，状态 Dirty | 是正确路径 store 的预期结果 |
| L1I `0x80000080` | 旧 line 于 C4203 已填入 | C4239-C4242 命中旧 line；窗口内无 meta/data write 或 refill | 旧内容被取出，但未形成新的 L1I 填充 |
| L1I 全量失效 | 第一个 `fence.i` 被清除 | 窗口内未发生 | 第二个副本在窗口外 C4340 才执行 `flushAll` |
| L2 `0x800002c0` 至 `0x800003c0` 的五条 line | C4192-C4212 发出的 ICache prefetch | C4238-C4258 分批填入，状态 TIP | 请求早于窗口；L1I flush 不会自动撤销已下发到 L2 的事务 |
| L2 `0x80001000` | 同一条窗口前 ROB11 load | C4276 填入，状态 TRUNK | 与 11 条错误路径指令无关 |
| L2 `0x80000080` | 已提交 store 的 Acquire | C4301 metadata-only hit | 只更新一致性/client 元数据，不是第七次 L2 line 填充 |

#### 5.2.1 SQ 与 SBuffer

三条 store 的提交和写出过程如下：

| 周期 | 事件 |
| ---: | --- |
| C4235-C4236 | ROB17-19 分配 SQ0-2；它们与第一副本 `cfVec.fire` 时间重叠，但程序顺序更早 |
| C4239-C4242 | 地址和数据进入 SQ，三项均达到 `addrvalid && datavalid` |
| C4285-C4288 | ROB 发出 `scommit=3`，SQ0-2 全部成为 committed |
| C4289-C4290 | SQ0/1/2 依次进入 SBuffer；同一 64 B line 合并为 mask `0x0fff` |
| C4291-C4292 | 三个 SQ entry 依次释放 |
| C4293 | `sqEmpty=1`，SBuffer 进入 `x_drain_all` |
| C4294 | SBuffer 向 DCache 发出 `addr=0x80000080, mask=0x0fff, id=1` 的请求 |
| C4309 | DCache 完成请求，SBuffer entry 释放 |
| C4311 | `sbempty=1` 且 flush empty，满足前序普通 `fence` 的完成条件 |
| C4318 | redirect 到达 SQ 时三项早已提交并释放，`sqCancelCnt=0` |

这条链确认该次波形中上述 SQ/SBuffer 活动及 `0x80000080` 的 DCache write-allocate 由正确路径、已提交的代码修改 store 发起。它们在 redirect 后留下 Dirty line 是 PoC 正确执行所必需的结果。

#### 5.2.2 两次 L1D 填充

`0x80001000` 的 load 使用 ROB11/LQ0：

```text
C4225  分配 LQ
C4228  发出 load 请求
C4230  确认为 miss
C4231  发出 64 B Acquire
C4281  最后一个 refill beat，DiffRefillEvent.addr=0x80001020
C4282  meta_write/data_write，line base=0x80001000，coh=Trunk
```

请求和 Acquire 均早于 C4235。`0x80001020` 是 refill beat 地址，按 64 B 对齐后的 line base 才是 `0x80001000`。因此不能把 C4282 的填充归给 C4242 才进入 `cfVec` 的错误路径 `ld`。

`0x80000080` 则是已提交 store 的 write-allocate：

```text
C4295  DCache MainPipe s1，source=STORE，tag_match=0
C4296  s2 miss，发出 miss_req，store_mask=0x0fff
C4297  TileLink Acquire 请求 64 B block 0x80000080
C4305  refill request 返回 MainPipe
C4308  最后一个 refill beat
C4309  tag/meta/data write 同拍有效，状态变为 Dirty
```

返回的旧 line 与 store mask 合并后，`0x80000080` 和 `0x80000084` 已分别成为 `addi a0,zero,0x5a` 与 `ret`，`0x80000088` 被写为 0。到 C4330，该 Dirty line 仍驻留 L1D，窗口内没有 writeback 或 Release；这仍是已提交 store 的正常状态。

#### 5.2.3 L1I 与 L2

目标 L1I line 最近一次 refill 在窗口前 C4203，内容仍是旧 `ld/addi/ret`。错误路径 call 在 C4239-C4242 命中并 touch 该 line，但主窗口内：

```text
ICache meta_write.valid = 0
ICache data_write.valid = 0
ICache DiffRefillEvent.valid = 0
```

因此旧指令来自已有 line 的命中，不是窗口中新填回的 line。目标 set2/way0 的 PLRU 在 touch 前后都为 `0b101`，本次 hit 没有翻转这组已观察的 PLRU 位。真正的 ICache 全量失效在 C4340。

窗口前发出的 6 组 ICache prefetch 在 C4237-C4263 返回；L1I 请求状态已被 flush，因此均未写入 L1I。其中 5 组形成了下表中的 L2 新 line：

| L2 填充周期 | Line base | 原请求 | 结果 |
| ---: | --- | --- | --- |
| C4238 | `0x800002c0` | C4192 ICache prefetch | TIP，clients=0，dirty=0 |
| C4243 | `0x80000300` | C4197 ICache prefetch | TIP，clients=0，dirty=0 |
| C4250 | `0x80000340` | C4202 ICache prefetch | TIP，clients=0，dirty=0 |
| C4253 | `0x80000380` | C4207 ICache prefetch | TIP，clients=0，dirty=0 |
| C4258 | `0x800003c0` | C4212 ICache prefetch | TIP，clients=0，dirty=0 |
| C4276 | `0x80001000` | 窗口前 ROB11 load | TRUNK，clients=1，dirty=0 |

`0x80000080` 的 store Acquire 在 C4299 到达 L2，C4301 directory hit 只把状态从 `TIP, clients=0, accessed=0` 更新为 `TRUNK, clients=1, accessed=1`，没有 tag/data write、L2 MSHR 分配或新下层请求。

因此，本次 V2 波形支持三项有限结论：

1. 11 条错误路径指令到达过 `cfVec` 或早期流水级，但未进入 ROB/IQ/LSQ/EXU，也未发出 DCache 请求。
2. 两次 L1D 和六次 L2 Cache Line 填充都有可识别的其他请求来源，不能归因于这 11 条指令。
3. 已发往下层的 prefetch 可以在上层请求被 flush 后继续留下 L2 footprint。这是值得按威胁模型独立审计的通用性质，但当前波形没有将其与 V3 #6132 或秘密相关访问建立因果链。

## 6. `fence.i` 前端安全硬化方案

### 6.1 设计目标与约束

前端早识别 `fence.i` 可以缩小推测窗口，但承担架构正确性的最终失效必须获得按序授权。本方案把三类职责分开：

1. **程序顺序隔离**：预译码产生无副作用的 `isFenceI` hint；取指包完成 `io.toIbuffer.fire` 后，锁存 `ftqPtr + ftqOffset + PC` 为 `fencei_boundary`，只阻止程序顺序位于边界后的条目。
2. **请求隔离**：屏障活动期间停止发出新的取指和预取；在 `fencei_req.fire` 时，把此前尚未完成的请求标为 `discard_on_return`，使迟到响应只能被吸收。
3. **按序失效与恢复**：Fence FU 等待前序 store 排空后发出 `fencei_req`。ICache 清有效位并返回 `fencei_done`，前端还要等匹配的 backend redirect 到达，才能从顺序后继 PC 恢复。

预译码候选不能直接承担最终 `flushAll`：

- 候选可能位于错误路径，失效 Cache 后无法回滚；
- 前序 store 可能尚未排空，过早失效会破坏顺序语义；
- 同一静态指令的多个动态副本可能重复失效；
- 若前端同时阻止 `fence.i` 自身到达后端，会形成死锁。

额外的推测性早期失效在架构上并非绝对禁止，但会引入不可回滚性能和侧信道状态，本方案不采用它。

只把 `fence.i` 加入 `fixedRange` 也不够。包内掩码不能清理已经位于 FTQ/IBuffer 的后续条目、跨块半条指令状态，或识别失效前已经发出的 MSHR 响应。`fencei_boundary` 处理指令顺序，`discard_on_return` 处理请求来源，两者不可互换。

### 6.2 当前实现与缺口

下表基于第 4 章的 V2 源码快照，用于确定修改边界；实现 V3 补丁前必须在 Issue 对应提交上重新核对接口和行号。

| 位置 | 当前行为 | 对硬化方案的影响 |
| --- | --- | --- |
| PreDecode | `PreDecodeInfo` 只保存长度和少量 CFI 属性，`fence.i` 被视为普通非 CFI 指令 | 需要新增与后端规则一致的 `isFenceI`，但只作为可取消 hint |
| F3Predecoder | F3 会重算并比较预译码结果 | 新字段必须在 F2/F3 同步生成，否则 `f3PdDiff` 或调试比较会不一致 |
| `fixedRange`/IBuffer enqueue | 只按 jal/jalr/ret 等修正包内有效范围 | 可复用其掩码框架截断同块后继，但不能替代全局边界 |
| FTQ predecode 压缩 | `Ftq_pd_Entry.toPd` 只重建现有 branch/jump/RVC 信息 | 若边界建立依赖该字段，必须扩展 bundle；更简单的是固定唯一建立位置 |
| Backend Decode | 已有 `FuType.fence/FenceOpType.fencei` 与 `noSpec/blockBack/flushPipe` | 继续作为按序授权点，不改变其架构职责 |
| Dispatch/ROB | `blockBackward` 阻挡后继 dispatch，ROB 记录 `needFlush` | 生效位置较晚，不能消除前端早期可见窗口，但仍是最终顺序保护 |
| Fence FU | 先排空 SBuffer，再产生一拍 `fencei` | 一拍脉冲不能表达 ICache 是否接受及何时完成，需要持久 req/done 语义 |
| ICache Meta/Miss | 清 valid 并禁止相关本地写入 | 需要保证所有失效前响应都有可保持的丢弃身份，不能只靠同拍 `flush` |
| MainPipe/PrefetchPipe/WayLookup | 普通冲刷依赖 FTQ redirect | 必须核对迟到响应和 redirect 同拍优先级，必要时携带等价的丢弃标志 |

当前设计的核心缺口不是“完全没有 flush”，而是前端看不到一个覆盖“按序授权、失效接受、迟到响应隔离、匹配 redirect、恢复”全过程的显式协议。硬化方案应把这个跨模块隐含契约变成状态、握手和断言。

### 6.3 单活动屏障协议

同一时间最多允许一个活动边界。连续 `fence.i` 在前一条释放并恢复取指后串行处理。

协议先固定以下不变量：

1. `fencei_boundary` 有效时，程序顺序位于其前的指令和 `fence.i` 自身仍可前进；只有后继条目被阻止。
2. 从 `SEEN` 到释放，前端不能再发出新的 ICache fetch/prefetch 请求，但已进入的前序指令仍可排空。
3. 只有后端按序执行的动态 `fence.i` 能发出真实 `fencei_req`；错误路径 hint 不能改变 Cache 状态。
4. `fencei_req.fire` 时尚未完成的请求必须锁存丢弃状态，直到响应被吸收或协议定义的取消完成。
5. 每个 request 恰好对应一个 done；重复或无主的 done 都是协议错误。
6. 匹配的 backend redirect 不能早于完成确认；屏障只在 done 和 redirect 均被记录后释放。
7. 在后端授权前，若程序顺序更早的 redirect 或异常杀掉候选，`SEEN/WAIT_AUTH` 必须可取消，不得留下永久阻塞。

这些不变量分别约束指令、请求和控制事件。实现不应把三者压成一个“当前正在 fence”的布尔量，否则难以正确处理指针回绕、迟到响应和竞争周期。

```text
IDLE
  -> 预译码识别最早的有效 fence.i
SEEN（可取消）
  -> 当前取指块只保留到 fence.i，停止新取指/预取
  -> 若更早的 redirect/异常杀掉候选，返回 IDLE
  -> io.toIbuffer.fire，锁存 fencei_boundary
WAIT_AUTH
  -> fence.i 前的指令和 fence.i 自身继续排空
  -> fence.i 后的条目不得进入 cfVec/后端
  -> Fence FU 等待 SBuffer 清空并发出 fencei_req
INVALIDATING
  -> 清除 L1I valid
  -> 给失效前在途请求置 discard_on_return
  -> 迟到响应不得写 L1I 或送入 IFU
  -> 接收 fencei_done
WAIT_RELEASE
  -> 等待身份匹配的 backend redirect
  -> done_seen && redirect_seen 后清除边界
  -> 从 fence.i 顺序后继 PC 恢复，返回 IDLE
```

推荐协议要求匹配的 backend redirect 不早于 `fencei_done`；同拍到达允许。`done_seen` 和 `redirect_seen` 必须分别锁存，不能依赖两个组合脉冲碰巧相遇。无关的 IFU predecode redirect 不能代替该动态 `fence.i` 的有序 redirect。

`fencei_done` 至少表示：

- L1I 有效位已经清除；
- 失效前在途请求已经排空，或已进入不会写 Meta/Data SRAM、不会生成有效 IFU 输出的丢弃状态。

它不表示 L2 或更下层已经撤销所有事务。带 `discard_on_return` 的 MSHR 在响应被吸收前也不能把请求 ID 复用于新请求。

### 6.4 实现工作包

| 工作包 | 主要修改 | 完成条件 |
| --- | --- | --- |
| 1. 预译码与包内截断 | 在 `PreDecodeInfo`、主 PreDecode 和 F3Predecoder 统一增加 `isFenceI`；选择包内最早候选并清除其后 `enqEnable` | 覆盖 lane 首尾、RVC/32 位、跨块、异常和 CFI redirect；不生成预测器训练用 `wbRedirect` |
| 2. 顺序边界 | `io.toIbuffer.fire` 时建立单个 `fencei_boundary`；FTQ/IBuffer/`cfVec` 复用环形指针和 offset 比较 | 前序条目与 `fence.i` 可排空，后续条目不能握手；候选被更早 redirect 杀掉时可取消 |
| 3. ICache 握手 | 将单拍 `fencei` 扩为 `fencei_req/fencei_done` 或等价持久协议；MSHR/流水响应增加 `discard_on_return` | 一个 req 对应一个 done；迟到 grant 可被吸收但不写 L1I、不送 IFU，请求 ID 不被过早复用 |
| 4. 唯一恢复与回归 | 保留后端 SBuffer 排空和唯一 `flushPipe` redirect；以 `done_seen && redirect_seen` 释放 | 无重复 invalidate/恢复，无死锁；普通无 `fence.i` 程序的前端时序不变 |

**工作包 1：预译码与包内截断**

- 主 PreDecode 与 F3Predecoder 应复用同一 `FENCE_I` 模式，避免前后端接受规则随版本演进后分叉。
- `fenceiIdx` 只选择当前 `instrRange/instrValid` 中程序顺序最早的有效候选；掩码保留该 slot，清除其后 slot。
- `fence.i` 不能伪装成 jal/jalr，也不能产生普通预测故障 `wbRedirect`，否则会污染 BTB/RAS 更新和错误预测统计。
- MMIO、跨页、last-half、取指异常与更早 CFI redirect 同拍时，应由更早事件取消候选，避免错误路径屏障锁住前端。

**工作包 2：顺序边界**

- 当前 F3/IFU 取指包完成 `io.toIbuffer.fire` 时，锁存 `ftqPtr + ftqOffset + PC`。包因背压停顿时，hint 和身份必须保持稳定。
- FTQ、IBuffer 和 `cfVec` 使用项目已有的环形指针年龄比较，并单独比较同一 FTQ entry 内的 offset；当前周期大小不能代替顺序关系。
- 若 `WAIT_AUTH` 中确认动态 `fence.i` 已被更早 redirect 杀掉，边界必须清除。直接把全部 lane 置零会连同 `fence.i` 自身一起丢掉，导致后端永远无法授权。

**工作包 3：请求、完成和迟到响应**

- Fence FU 保留 `s_wait` 对 SBuffer 的排空要求，在 ICache 确认前不能向 ROB 报告完成。
- `fencei_req.fire` 同拍清除 L1I 有效位，并为当时所有 fetch/prefetch MSHR 建立持久丢弃身份。
- grant 可以继续被协议层接收，但不能写 MetaArray/DataArray，也不能产生有效 MainPipe、PrefetchPipe 或 IFU 输出。
- 如果响应已经离开 MSHR，流水寄存器仍须携带等价 epoch/kill 信息；仅禁止 SRAM 写入不足以证明 `cfVec` 不会收到迟到响应。
- 若接口继续使用单拍脉冲，必须另外定义接受、最后旧响应和完成三个周期，并用寄存状态隔离组合环；显式 req/done 通常更易验证。

**工作包 4：恢复与发布门槛**

- Fence FU 完成后只产生一条有序 `flushPipe` redirect，从 `fence.i` 的顺序后继 PC 恢复。普通 IFU redirect 不能抢先释放边界。
- `done_seen` 和 `redirect_seen` 在消费后清零；复位、异常和连续 `fence.i` 都要覆盖状态清理。
- 无 `fence.i` 工作负载中，屏障状态应保持空闲，不改变普通取指、IBuffer 或预译码时序。
- 发布前先以断言和 V3 波形证明协议正确，再评估关键路径、面积、功耗和更强的数据阵列清理策略。

实施时还需注意：

- `PreDecodeInfo` 的宽度变化要同步检查 `f3PdDiff`、默认赋值、调试接口和 `asUInt` 比较。
- 边界建立位置应唯一，避免依赖会丢失新字段的 FTQ 压缩/重建路径。
- MainPipe、PrefetchPipe 和 WayLookup 中已经离开 MSHR 的响应也要继承等价丢弃状态。
- 只有一个模块发起真正的全相失效，避免 predecode 和 Fence FU 各执行一次。
- 数据阵列清零或随机化属于更强威胁模型下的独立选项，不是第一版功能协议的必要条件。

### 6.5 精简测试矩阵

| 类别 | 代表场景 | 核心通过条件 |
| --- | --- | --- |
| 解码 | `FENCE_I` 正例；`FENCE`、CSR、异常和非法指令反例 | 前后端识别一致，无误报 |
| 包位置 | lane 首/中/尾，RVC/32 位邻接，跨块和 cache-line | 保留 `fence.i`，只截断其后槽位 |
| 基本 SMC | store -> fence -> `fence.i` -> jump，目标分别命中 L1I 和跨 line | 只退休新函数，结果与 Spike/QEMU 一致 |
| 候选取消 | 更早的异常、分支预测错误或 backend redirect 杀掉候选 | 不发真实失效请求，屏障可取消并恢复 |
| 在途请求 | 多个 fetch/prefetch MSHR，grant 与 req/done 同拍 | 所有旧请求正确归类；丢弃响应不写 L1I、不送 IFU |
| 背压与容量 | MSHR/IBuffer 满、SQ/SBuffer 延迟排空、随机 backpressure | `fence.i` 不丢，最终完成，无环形等待 |
| 竞争 | IFU redirect、backend redirect、done 相邻或同拍；连续 `fence.i` | 只匹配正确身份和恢复 PC；每个 req 恰好一个 done |
| 安全波形 | 直接加载、秘密索引、顺序落入三个 V3 PoC | 记录旧条目身份、kill 点及 Cache/预测器副作用，不用时间重叠代替因果 |
| 回归 | 无 `fence.i`、`fencei_heavy`、不同前端/MSHR 参数、多 hart | 功能无回归；量化屏障延迟和 IPC；不把本地 `fence.i` 当作跨 hart 同步 |

对几个高风险类别还应细化通过条件：

- **解码一致性**：检查 F2/F3 对同一包的 `isFenceI` 一致，FTQ 压缩/重建不会意外丢失边界建立所需信息；`FENCE`、CSR、ECALL/EBREAK 和非法编码均不误报。
- **边界与异常**：覆盖 `fence.i` 位于 lane 0、中间和末尾，16/32 位相邻及跨 cache line；异常若位于候选之前，应优先取消候选，异常若位于之后则不能越过边界。
- **自修改代码**：覆盖目标已经命中 L1I、跨 set/line、无前序 store、SQ/SBuffer 满和大量合并 store。`fencei_req` 只能在规定的前序写入可见条件满足后接受。
- **请求竞争**：强制 grant 与 req/done 同拍、MSHR 满、多个预取返回和 request ID 回收。每个返回必须唯一归类为可用或丢弃，不能既写入又报告完成。
- **控制竞争**：枚举 IFU redirect、匹配 backend redirect 和 `fencei_done` 同拍或相邻拍；恢复 PC 必须唯一，非匹配 redirect 不能置 `redirect_seen`。
- **活性**：在公平的 Cache 响应假设下施加随机 backpressure，要求状态机最终离开 `INVALIDATING/WAIT_RELEASE`，并为最大容忍延迟设置仿真 watchdog。

建议保留以下核心断言：

```text
assert(fencei_done -> prior_fencei_req_pending)
assert(fencei_req.fire -> sb_drain_complete)
assert(active_boundary && after_boundary -> !cfVec.fire && !ROB_alloc && !LSQ_alloc && !EXU_issue)
assert(frontend_fencei_block -> !new_fetch_req && !new_prefetch_req)
assert(discard_on_return_response -> !meta_write && !data_write && !IFU_valid)
assert(matching_backend_redirect -> done_seen || fencei_done)
assert(release -> done_seen && redirect_seen)
```

其中 `after_boundary` 必须由 FTQ 环形指针和 offset 计算；`discard_on_return_response` 必须来自请求在 `fencei_req.fire` 时锁存的状态。二者都不能用“当前周期晚于 fence 周期”替代。

波形验收要分别报告：

- `fencei_hint -> boundary`、`boundary -> req`、`req -> done`、`done -> first_new_fetch` 和 `first_new_fetch -> retire` 的延迟；
- 屏障期间 boundary 后的 `cfVec.fire` 数及对应 ROB/LSQ/EXU/DCache 事件数；
- 所有 L1I/L1D/L2 refill 的发起者、发起周期、动态指令提交状态和安装周期；
- redirect 后仍存在的 tag、一致性、PLRU、预取痕迹及其来源；
- 是否出现重复失效、过期响应写入或 request ID 提前复用。

不得把 V2 的 96 周期窗口用作 V3 协议延迟基线。V3 的窗口应由其自身动态 `fence.i` 身份和 req/done/redirect 定义。

### 6.6 方案边界与最小建议

该协议直接处理同一取指块和 FTQ/IBuffer 中位于 `fence.i` 后的条目、屏障期间的新取指/预取，以及失效前在途的 L1I 请求。它不能自动撤销：

- 已经发往 L2 或更下层的 prefetch/Acquire；
- 程序顺序位于 `fence.i` 前且已提交的操作留下的 L1D/L2 状态；
- 更下层 Cache 的替换、一致性元数据或其他持久痕迹。

若目标只是低风险改进，优先实现“预译码标记、包内截断、redirect/ICache 契约注释和断言”。它只能缩小前端可见窗口，不能处理失效前在途响应。完整硬化还需要 `fencei_boundary`、`fencei_req/fencei_done` 和 `discard_on_return`。

是否存在安全漏洞仍需在 V3 上证明：程序顺序位于 `fence.i` 后且携带旧内容的条目，能否在被清除前发出秘密相关访问，并形成跨上下文可稳定测量的状态差异。

## 7. V3 独立复现与验收

V3 验证应在 Issue 对应提交上运行三组程序：

1. 直接加载 PoC，确认旧目标已在 L1I 时的基本现象。
2. 秘密索引 PoC，检查旧 load、地址计算和探针访问的完整生命周期。
3. 顺序落入对照组，区分 `jal`/redirect 时序与一般取指行为。

每组应记录精确提交、编译器和参数、ELF 反汇编、仿真命令、结束原因及实际 FST/FSDB 路径。波形按以下因果链组织：

```text
store 修改代码
  -> fence / fence.i 按序执行
  -> ICache 元数据与在途 miss 处理
  -> backend redirect
  -> 前端各级 flush
  -> 旧 cfVec 条目的动态身份和 kill 点
  -> 新目标重新取指并退休
```

验收需要回答：

| 问题 | 必要证据 |
| --- | --- |
| 接口现象能否稳定重现 | `cfVec.valid/ready`、PC/编码、FTQ pointer/offset 和周期 |
| 旧条目是否越过失效边界 | Decode、Rename、Dispatch、ROB/IQ/LSQ/EXU、写回与退休的同一动态身份 |
| 响应来自何时的请求 | ICache request/MSHR 身份、`fencei` 接受与完成、refill 返回周期 |
| 是否产生副作用 | L1I/L1D/L2、TLB、预测器和预取器事件，并追溯到发起者 |
| 架构结果是否正确 | 修改后的目标指令退休，寄存器和内存结果与参考模型一致 |

采样时应先固定时钟口径和每类接口的 `fire` 定义，再导出事件表。旧条目的生命周期建议以动态身份作为主键，记录：

| 阶段 | 最小字段 |
| --- | --- |
| Fetch/ICache | request ID 或 MSHR、PC/line、发起与响应周期、是否在失效前发出 |
| IFU/IBuffer/`cfVec` | FTQ pointer、offset、PC、编码、valid/ready、redirect kill |
| Decode/Rename/Dispatch | ROB 候选值与真实 allocation、pdest 分配、block/flush 条件 |
| ROB/IQ/LSQ/EXU | ROB 身份、队列 enqueue/issue、内存地址、写回及 squash |
| Retire | PC、编码、ROB 身份、架构写入和异常 |
| Cache/预测结构 | 操作发起者、set/way/line、meta/data write、替换或训练更新 |

针对每个观察到的旧 `cfVec` 条目，至少存在三类结果：

1. **仅接口可见**：完成 `cfVec.fire`，但在真实 ROB/LSQ allocation 前被匹配 redirect 清除，且没有可归因的不可回滚更新。这支持“推测条目按契约失效”。
2. **进入后端但及时失效**：条目建立了 ROB 或队列状态，但在 issue、内存请求、写回和退休前被清除。功能结果仍可能正确，但预测器、预取器等更早更新要继续审计。
3. **越过安全边界**：匹配 redirect 后仍 issue/请求/写回/退休，或失效前产生由秘密控制且可稳定观测的状态。前者需要定位具体功能控制缺陷；后者还要通过重复实验、对照组和统计区分证明可利用性。

报告不能把“没有匹配事件”笼统写成“没有执行”。应列出扫描过的端口、身份字段、周期范围和参数配置；同样，也不能把某次 Cache fill 只因落在窗口中就归给旧条目，而应反向追踪到 miss/Acquire 的发起周期。

若旧条目在匹配 redirect 后仍分配资源、执行或退休，应按具体路径评估功能 Bug；若它们只在内部接口出现并在产生影响前被清除，则证据支持“瞬时推测可见”。侧信道结论还需要建立秘密依赖、持久状态和可重复观测三者之间的完整链路。

## 8. 调试方法小结

1. 内部接口上的数据只是事实链起点，`valid && ready`、动态身份、redirect 和退休共同决定其有效性。
2. `fence.i` 的逻辑语义可以由专用 ICache 失效和通用流水 redirect 共同完成，不能只追踪同名信号。
3. 静态 PC 无法区分被清除和重取的动态副本；FTQ/ROB 身份必须贯穿前端到退休。
4. Cache 状态必须追溯请求发起周期和提交状态。时间窗口重叠不等于因果，更不等于可利用侧信道。

## 参考资料

- [XiangShan Issue #6132](https://github.com/OpenXiangShan/XiangShan/issues/6132)
- [昆明湖 V3 ICache 设计文档：冲刷](https://docs.xiangshan.cc/projects/design/zh-cn/kunminghu-v3/frontend/ICache/#sec:icache-flush)
- [昆明湖 V3 ICache 设计文档：`fence.i` 冲刷脚注](https://docs.xiangshan.cc/projects/design/zh-cn/kunminghu-v3/frontend/ICache/#fn:redirect_tab_fencei)
- [Issue 所用提交 ICacheImp.scala](https://github.com/OpenXiangShan/XiangShan/blob/3931c5112c528299a23c256bdd77fb90813afa6e/src/main/scala/xiangshan/frontend/icache/ICacheImp.scala)
- [Issue 所用提交 ICacheMainPipe.scala](https://github.com/OpenXiangShan/XiangShan/blob/3931c5112c528299a23c256bdd77fb90813afa6e/src/main/scala/xiangshan/frontend/icache/ICacheMainPipe.scala)
- [FENCE_I 译码定义](https://github.com/OpenXiangShan/XiangShan/blob/96c3f568f943a096ffd3d712dc6f462ac4b1ba33/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala#L250)
- [本地 V2 对照程序](/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6132/bug-replay/smc_fencei_direct_probe.S)
- [本地 V2 对照波形归档](/nfs/home/yanyusong/XiangShanLab/xiangshan-course/docs/6-xiangshan-debug/xiangshan-issue-6132/bug-replay/wave.zip)
