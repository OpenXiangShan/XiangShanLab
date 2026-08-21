# 访存依赖关系检测与 Replay 机制

## 简单情况下的波形图分析

### 演示程序与解析

为了演示违例检测, Replay 过程, MDP (Store Set) 训练的行为, 和后续同一个 PC 的 Store-Load 执行情况, 我们编写一个简易的演示程序, 这个程序通过插入 `DEP`宏 (其实就是给一个寄存器值加一之后减一) 制造较长的依赖链. 那我们就可以把某个内存地址写入到两个寄存器中, 第一个寄存器直接可以使用 (所以分配给想要投机乱序执行的 Load), 第二个寄存器通过 DEP 宏拉出较长的依赖链, 所以需要等很多个周期之后才能就绪 (所以分配给想要稍微晚些就绪的 Store):

```c
#include <klib.h>
#include <stdint.h>

static volatile uint64_t x __attribute__((aligned(64))) = 0;

int main(void) {
    uint64_t sum;

    asm volatile(
        "li t1, 10\n"
        "li t3, 1\n"
        "li %[sum], 0\n"
        "1:\n"
        "mv t0, %[p]\n"
        #define DEP "addi t0,t0,1\naddi t0,t0,-1\n"
        DEP DEP DEP DEP DEP DEP DEP DEP
        DEP DEP DEP DEP DEP DEP DEP DEP
        DEP DEP DEP DEP DEP DEP DEP DEP
        DEP DEP DEP DEP DEP DEP DEP DEP
        #undef DEP
        "sd t3, 0(t0)\n"
        "ld t4, 0(%[p])\n"
        "add %[sum], %[sum], t4\n"
        "addi t3, t3, 1\n"
        "addi t1, t1, -1\n"
        "bnez t1, 1b\n"
        : [sum] "=&r"(sum)
        : [p] "r"(&x)
        : "t0", "t1", "t3", "t4", "memory");

    printf("mdp raw demo: x=%lu sum=%lu\n", (uint64_t)x, sum);
    return 0;
}
```

将其编译, 在香山昆明湖 V3 的 EMU 中执行该程序, 保存 FST 波形图 ([附件: demoMDP.zip](./attachments/kivYo6g6dphb9DiV/demoMDP.zip)), 可以得到反汇编代码:

```plain
000000008000012a <main>:
    8000012a:   1141                    addi    sp,sp,-16
    8000012c:   e406                    sd      ra,8(sp)
    8000012e:   00001797                auipc   a5,0x1
    80000132:   51278793                addi    a5,a5,1298 # 80001640 <x>
    80000136:   4329                    li      t1,10
    80000138:   4e05                    li      t3,1
    8000013a:   4601                    li      a2,0
    8000013c:   82be                    mv      t0,a5
    8000013e:   0285                    addi    t0,t0,1
    80000140:   12fd                    addi    t0,t0,-1
    // ... 重复的 addi 1 和 addi -1
    800001ba:   0285                    addi    t0,t0,1
    800001bc:   12fd                    addi    t0,t0,-1
    800001be:   01c2b023                sd      t3,0(t0)
    800001c2:   0007be83                ld      t4,0(a5)
    800001c6:   9676                    add     a2,a2,t4
    800001c8:   0e05                    addi    t3,t3,1
    800001ca:   137d                    addi    t1,t1,-1
    800001cc:   f60318e3                bnez    t1,8000013c <main+0x12>
    800001d0:   638c                    ld      a1,0(a5)
    800001d2:   00001517                auipc   a0,0x1
    800001d6:   17e50513                addi    a0,a0,382 # 80001350 <printf_+0x32>
    800001da:   144010ef                jal     8000131e <printf_>
    800001de:   60a2                    ld      ra,8(sp)
    800001e0:   4501                    li      a0,0
    800001e2:   0141                    addi    sp,sp,16
    800001e4:   8082                    ret
```

从上面的汇编代码中可以看出 a5 的值只想了某个内存地址, t0 复制了这个值, 并植入了很长的 DEP 依赖链. 最后的 sd 和 ld 指令操作同一个地址. 这个程序是一个有循环的程序, 所以也方便观察在发现违例后, 后续的循环中 MDP 预测器的行为.

### LoadQueueRAW 违例检测分析

本节使用开源的 `wavekit` 库解析 `2026-07-21-10-21-43.fst`。以下周期均在
`TOP.clock` 的上升沿采样；FST 中相邻周期的仿真时间相差 2。关注的第一对指令为
`0x800001be: sd t3, 0(t0)` 和 `0x800001c2: ld t4, 0(a5)`：二者最终都访问
`x = 0x80001640`，但 load 的基址 `a5` 已就绪，而 store 的 `t0` 仍被长 DEP
链阻塞，因此 load 可以先于 store 地址写回。

| 周期（仿真时间） | 波形证据 | 含义 |
| --- | --- | --- |
| 4337（8674） | `inner_LoadUnit_0.io_lqWrite_valid=1`；`ftqPtr=17`、`ftqOffset=18`、`robIdx=164`、`fullva=0x80001640`；`storeSetHit=0`、`loadWaitBit=0` | 首次 `ld t4, 0(a5)` 进入 LQ 时尚无 Store Set 预测，因而直接投机执行。`ftqOffset=18` 与下面 store 的 16 相差一个 32-bit 指令，和反汇编中 `0x1c2-0x1be=4` 字节相符。 |
| 4396（8792） | `inner_lsq.io_nuke_rollback_0_valid=1`；load 的 `robIdx=164, ftqIdx=17, ftqOffset=18`；携带 store 的 `stFtqIdx=17, stFtqOffset=16` | Store 地址就绪后，`LoadQueueRAW` 找到了这个更年轻、同地址且已取数的 load，产生 RAW 违例 redirect。`backend.io_mem_mdpTrain_valid` 在同一拍也为 1，说明同一个违例同时提供训练样本。 |

这里不是由数据缓存 miss 引起的 replay：load 的有效地址在第一次发射时已经是
`0x80001640`，违例请求中也明确携带了同一 Fetch Block 内的 store/load 身份。硬件的
判定方式正是 store 写回时对 RAW 队列中较年轻 load 做地址和 mask 匹配，再选出最老的
违例 load。源码中的注释与实现如下：

[`LoadQueueRAW.scala:234`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala#L234)

```scala
// When store writes back, it searches LoadQueue for younger load instructions
// with the same load physical address. They loaded wrong data and need re-execution.
val rollbackLqWb = Wire(Vec(StorePipelineWidth, Valid(new UopEntry)))
rollbackLqWb(w).valid := detectedRollback._1 &&
  DelayN(storeIn(w).valid && !storeIn(w).bits.tlbMiss, TotalSelectCycles)
```

[`LoadQueueRAW.scala:377`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala#L377)

```scala
redirect.valid := rollbackLqWb(i).valid
redirect.bits.robIdx := rollbackLqWb(i).bits.robIdx
redirect.bits.ftqIdx := rollbackLqWb(i).bits.ftqPtr
redirect.bits.ftqOffset := rollbackLqWb(i).bits.ftqOffset
redirect.bits.stFtqIdx := stFtqIdx(i)
redirect.bits.stFtqOffset := stFtqOffset(i)
redirect.bits.target := rollbackLqWb(i).bits.pc
```

### Replay 机制分析

这次 RAW 违例使用的是 **从违例 load 本身重新取指** 的 pipeline flush，而不是把该
load 放入 `LoadQueueReplay` 后在 RS 内局部重发。`LoadQueueRAW` 生成的 redirect 的
`robIdx` 是 load 的 ROB 项；该模块随后以 `robIdx - 1` 为 flush 边界，使 load 自身和
所有更年轻指令失效，再把 `target` 设为该 load 的 PC。因此，错误读取的旧值不会提交。

波形给出了完整的跨模块延迟链：

| 周期（仿真时间） | 有效信号 | 作用 |
| --- | --- | --- |
| 4396（8792） | `inner_lsq.io_nuke_rollback_0_valid=1` | LoadQueueRAW 输出针对 `robIdx=164`、PC `0x800001c2` 的内存违例。 |
| 4397（8794） | `backend.inner_ctrlBlock.loadReplay_valid_last_REG=1` | CtrlBlock 用寄存器接住 MemBlock 的违例请求。 |
| 4398（8796） | `backend.inner_ctrlBlock.redirectGen.io_stage2Redirect_valid=1`，`backend.io_frontend_toFtq_redirect_valid=1` | 后端仲裁得到该条 memory redirect，并将其送入前端恢复接口。 |
| 4399（8798） | `frontend.inner_ftq.io_toIfu_redirect_valid=1` | FTQ 向 IFU 发出重定向，重新从 `ld t4, 0(a5)` 取指。 |

从模块边界看，这次 replay 的流程可以拆成下面六步：

1. **Store 写回并触发查询。** 延迟地址链完成后，`sd t3, 0(t0)` 的 store pipeline 将地址写回；`LoadQueueRAW` 以该 store 的物理地址和 mask 查询已记录的、SQ 序号更年轻的 load。
2. **选择最老违例 load。** 若命中多个候选项，`detectRollback` 的分组年龄矩阵选择最老的 load。这里选中 `robIdx=164`、`(ftqIdx, ftqOffset)=(17,18)` 的 `ld t4, 0(a5)`，并同时保留触发者 store 的 `(17,16)` 身份；中间的多级选择延迟由 `TotalSelectCycles` 吸收。
3. **封装为 memory redirect。** 选中的 load 作为 redirect 的 `robIdx/ftqIdx/ftqOffset/target`，store 的 FTQ 信息放在 `stFtqIdx/stFtqOffset` 中。注释说明该类 redirect 以 `robIdx - 1` 为 flush 边界，所以违例 load 本身也会被冲刷，不能把已经读到的旧值继续写回或提交。
4. **CtrlBlock 还原准确 PC 并参与仲裁。** MemBlock 上报的 redirect 只有 FTQ 身份；CtrlBlock 用 `pcMem[ftqIdx] + getPcOffset()` 恢复 load 的真实 PC，随后将其送入 `RedirectGenerator`。本例波形中，4397 拍的 `loadReplay` 是这一拍寄存后的请求，4398 拍才成为被仲裁的 stage-2 redirect。
5. **FTQ/IFU 从 load PC 恢复。** 后端将仲裁结果送给 FTQ；4399 拍 `io_toIfu_redirect_valid=1`，IFU 从 `0x800001c2` 重取。因而 load 及其所有更年轻指令会重新经历 fetch、decode、rename、dispatch 和 issue；更老的 store 保留，避免错误地回滚已确定的程序顺序状态。
6. **重新执行时读取正确数据。** 重取后的 load 必须等到该 store 地址已经可见；本例中 MDP 同时完成训练，后续同一 PC 的 load 被赋予 `loadWaitBit=1`，从源头避免再次发生“load 先读、store 后到”的 RAW 违例。

`LoadQueueRAW` 把选中 load 的身份和触发 store 的身份组装为 redirect 的逻辑如下：

[`LoadQueueRAW.scala:353`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala#L353)

```scala
rollbackLqWb(w).valid := detectedRollback._1 &&
  DelayN(storeIn(w).valid && !storeIn(w).bits.tlbMiss, TotalSelectCycles)

redirect.bits.robIdx      := rollbackLqWb(i).bits.robIdx
redirect.bits.ftqIdx      := rollbackLqWb(i).bits.ftqPtr
redirect.bits.ftqOffset   := rollbackLqWb(i).bits.ftqOffset
redirect.bits.stFtqIdx    := stFtqIdx(i)
redirect.bits.stFtqOffset := stFtqOffset(i)
redirect.bits.target      := rollbackLqWb(i).bits.pc
```

CtrlBlock 对该 redirect 重建 PC 并将它送入前端的关键连接如下：

[`CtrlBlock.scala:324`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L324)

```scala
redirectGen.io.loadReplay <> loadReplay
loadRedirectTargetOffset := Mux(memViolation.bits.flushItself(), thisPcOffset, nextPcOffset)
val load_target = loadRedirectStartPcRead + loadRedirectTargetOffset
redirectGen.io.loadReplay.bits.target := load_target

io.frontend.toFtq.redirect.valid := s5_flushFromRobValid || s3_redirectGen.valid
io.frontend.toFtq.redirect.bits := Mux(s5_flushFromRobValid, frontendFlushBits, s3_redirectGen.bits)
```

这四拍也解释了为什么 `mdpTrain` 不能被误认为一次额外的 redirect：训练信号在
4396 拍和 RAW redirect 同时产生，而真正恢复前端的是 `violation -> loadReplay ->
redirectGen -> FTQ` 这条链。CtrlBlock 对 memory violation 的寄存和标记逻辑如下：

[`CtrlBlock.scala:208`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L208)

```scala
val loadReplay = Wire(ValidIO(new Redirect))
loadReplay.valid := GatedValidRegNext(memViolation.valid)
loadReplay.bits := RegEnable(memViolation.bits, memViolation.valid)
loadReplay.bits.debugIsCtrl := false.B
loadReplay.bits.debugIsMemVio := true.B
```

这一恢复会清除该 load 之后的投机工作，代价至少包括上述 3 个后端/前端传递周期以及
重新取指、rename、调度的气泡；也正是 MDP 值得训练的原因。

### MDP 训练行为分析

`LoadQueueRAW` 不只把最老违例 load 送往 redirect 仲裁，同时把同一条 redirect 送到
`mdpTrain`：`io.mdpTrain := Mux1H(oldestOH, allRedirect)`。因此，训练样本的 load 和
store 身份与实际导致 flush 的那一对完全一致，而不是由提交阶段猜测得到。

第一次违例的训练流水如下；`ldpc/stpc` 是 CtrlBlock 用 FTQ 起始 PC 与 offset 还原
静态 PC 后，再做 `XORFold` 得到的 10 位 SSIT 索引，而不是原始 PC：

| 周期（仿真时间） | 波形证据 | 训练阶段 |
| --- | --- | --- |
| 4396（8792） | `backend.io_mem_mdpTrain_valid=1`；load `(ftqIdx, offset)=(17,18)`，store `(17,16)` | 收到真实 RAW 违例样本。 |
| 4397（8794） | `memCtrl_io_memPredUpdate_valid_REG=1` | CtrlBlock 已完成 FTQ PC 存储体读请求，向 MemCtrl 提交 update。 |
| 4399（8798） | `ssit.s1_mempred_update_req_valid=1`，`ldpc=224, stpc=222` | SSIT 的读阶段取得 load/store 旧表项。 |
| 4400（8800） | `ssit.s2_mempred_update_req_valid=1`，`ldpc=224, stpc=222` | SSIT 写阶段；之后 `io_ssit2Rename_*_valid=1, ssid=29, strict=0`。 |

在这次首次训练前，两端都没有匹配表项，因此命中 `Cat(loadAssigned,
storeAssigned) == "b00"` 的分支：为 store 和 load 分配同一个 SSID。波形中最后可见的
`SSID=29` 是该配置下 `XORFold`/分配逻辑得到的 Store Set 号；`strict=0` 表示普通的
Store Set 等待，而不是 strict wait。

[`CtrlBlock.scala:217`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/CtrlBlock.scala#L217)

```scala
val mdpTrainValid = io.fromMem.mdpTrain.valid
memCtrl.io.memPredUpdate.ldpc := XORFold(
  (pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
memCtrl.io.memPredUpdate.stpc := XORFold(
  (pcMem.io.rdata(pcMemIdx).toUInt + offset)(VAddrBits - 1, 1), MemPredPCWidth)
memCtrl.io.memPredUpdate.valid := RegNext(mdpTrainValid)
```

[`StoreSet.scala:246`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L246)

```scala
when(s2_mempred_update_req_valid){
  switch (Cat(s2_loadAssigned, s2_storeAssigned)) {
    is ("b00".U(2.W)) {
      update_ld_ssit_entry(pc = s2_mempred_update_req.ldpc,
        valid = true.B, ssid = s2_allocSsid, strict = false.B)
      update_st_ssit_entry(pc = s2_mempred_update_req.stpc,
        valid = true.B, ssid = s2_allocSsid, strict = false.B)
    }
  }
}
```

波形中还在 4680（9360）观察到第二次 RAW redirect，但它的训练索引为
`ldpc=233, stpc=222`，不同于循环内 `ld t4, 0(a5)` 的 `ldpc=224`。它对应循环退出后
`0x800001d0: ld a1, 0(a5)` 与同一 store 的新 load/store 对，不能算作循环内目标 load
训练失败；这也说明 SSIT 以静态 load PC 区分不同依赖对。

### 后续同 Load 指令执行分析

第一次训练完成后，后续重新取到的同一静态 `ld t4, 0(a5)` 不再以“无依赖”的方式直接
发射。波形中所有 `ftqOffset=18` 且 `fullva=0x80001640` 的循环内 load，在 4430（8860）
起均携带：

```plain
storeSetHit = 1
loadWaitBit = 1
loadWaitStrict = 0
ssid = 29
waitForRobIdx = 本轮更老 sd 的 ROB index
```

例如，4430 拍的 load 为 `robIdx=235`、`waitForRobIdx=234`；4445 拍为
`robIdx=306`、`waitForRobIdx=305`；随后 4476、4498、4522、4547、4573、4601、4625、
4658、4681 拍仍可看到同样的 `SSID=29` 和相邻的 store ROB index。也就是说，预测器没有
把 load 粗暴地完全串行化，而是让它等待 **LFST 中同一 Store Set 的最近未完成 store**。
在 store 地址发射后 LFST 清除该 store，load 才可进入 LoadUnit；因此长 DEP 链的等待被
显式归因到预测到的 store，而不是再次依赖“先读、再由 RAW 检查兜底”。

从 4400 的首次 SSIT 写入到循环内后续各次 `ftqOffset=18` load 的执行窗口，FST 没有再
出现以该静态 load（folded `ldpc=224`）为目标的 `LoadQueueRAW` rollback；唯一后续
rollback 是上节说明的 `0x800001d0` 新 load。这与 `storeSetHit/loadWaitBit` 的变化相互
印证：MDP 已把循环内的 store-load 依赖从“违例后恢复”转为“发射前等待”。

Rename 先把 SSIT 结果写入 micro-op，Dispatch 再通过 LFST 给出真正应等待的最近 store：

[`Rename.scala:453`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala#L453)

```scala
uops(i).storeSetHit := io.ssit(i).valid
uops(i).loadWaitStrict := io.ssit(i).strict && io.ssit(i).valid
uops(i).ssid := io.ssit(i).ssid
uops(i).loadWaitBit := io.waittable(i)
```

[`Dispatch.scala:760`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala#L760)

```scala
io.lfst.req(i).bits.ssid := updatedUop(i).ssid
io.lfst.req(i).bits.robIdx := updatedUop(i).robIdx
fromRenameUpdate(i).bits.loadWaitBit := io.lfst.resp(i).bits.shouldWait
fromRenameUpdate(i).bits.waitForRobIdx := io.lfst.resp(i).bits.robIdx
```

## 复杂情况下的波形图分析

### 复杂演示程序解析

为覆盖 SSIT 更新状态机的四个分支，在 Kunminghu V3 环境中新增了
[`mdp-ssit-complex/main.c`](/home/yanyusong/mdp-kmhv3/nexus-am/tests/mdp-ssit-complex/main.c)。测试使用三个独立的静态 store（`S0`、`S1`、`S2`）和三个独立的静态 load（`L0`、`L1`、`L2`）；每个 store 都通过 32 组 `addi +1/-1` 形成地址依赖链，load 的地址则直接可用。store 函数返回后，处理器可以沿 RAS 预测继续进入 load 函数，因此 load 有机会在该 store 地址就绪前投机执行；每个 pair 后的 `fence rw,rw` 只用于隔离相邻测试 pair，不会撤销已经产生的 RAW 违例训练。

测试按以下顺序执行，其中括号中为该 pair 希望触发的 SSIT 更新分支：

```c
run_pair(store_s0, load_l0, 1); // b00：L0、S0 均未分配
run_pair(store_s1, load_l1, 2); // b00：建立第二个独立 Store Set
run_pair(store_s2, load_l0, 3); // b10：L0 已分配，S2 未分配
run_pair(store_s1, load_l2, 4); // b01：L2 未分配，S1 已分配
run_pair(store_s1, load_l0, 5); // b11：L0、S1 均已分配，合并两个 set
run_pair(store_s0, load_l2, 6); // b11：再次观察两个已分配 set 的合并
```

#### 测试程序关键代码

下面是测试程序中实际编译的 C 代码。`DELAYED_STORE` 中的 `DEP_CHAIN` 使 `t0` 在
`sd` 之前经历 32 组相互依赖的加一/减一；`SPECULATIVE_LOAD` 则只有一条直接使用
`a0` 的 load。三个宏展开实例分别保留不同的静态 PC，这一点是构造 SSIT 不同表项的关键。

[`mdp-ssit-complex/main.c`](/home/yanyusong/mdp-kmhv3/nexus-am/tests/mdp-ssit-complex/main.c)

```c
static volatile uint64_t shared_word __attribute__((aligned(64)));
static volatile uint64_t observed_sum;

#define DEP_CHAIN \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n" \
  "addi t0, t0, 1\n" "addi t0, t0, -1\n"

#define DELAYED_STORE(name) \
  static __attribute__((noinline)) void name(volatile uint64_t *address, uint64_t value) { \
    asm volatile( \
      "mv t0, %[address]\n" DEP_CHAIN "sd %[value], 0(t0)\n" \
      : : [address] "r"(address), [value] "r"(value) : "t0", "memory"); \
  }

#define SPECULATIVE_LOAD(name) \
  static __attribute__((noinline)) uint64_t name(volatile uint64_t *address) { \
    uint64_t value; \
    asm volatile("ld %[value], 0(%[address])\n" \
      : [value] "=&r"(value) : [address] "r"(address) : "memory"); \
    return value; \
  }

DELAYED_STORE(store_s0)
DELAYED_STORE(store_s1)
DELAYED_STORE(store_s2)
SPECULATIVE_LOAD(load_l0)
SPECULATIVE_LOAD(load_l1)
SPECULATIVE_LOAD(load_l2)

static __attribute__((noinline)) uint64_t run_pair(
    void (*store)(volatile uint64_t *, uint64_t),
    uint64_t (*load)(volatile uint64_t *), uint64_t value) {
  store(&shared_word, value);
  uint64_t observed = load(&shared_word);
  asm volatile("fence rw, rw" ::: "memory");
  return observed;
}

int main(void) {
  shared_word = 0;
  observed_sum += run_pair(store_s0, load_l0, 1);
  observed_sum += run_pair(store_s1, load_l1, 2);
  observed_sum += run_pair(store_s2, load_l0, 3);
  observed_sum += run_pair(store_s1, load_l2, 4);
  observed_sum += run_pair(store_s1, load_l0, 5);
  observed_sum += run_pair(store_s0, load_l2, 6);
  printf("mdp ssit complex: final=%lu sum=%lu\n", shared_word, observed_sum);
  return shared_word == 6 && observed_sum == 21 ? 0 : 1;
}
```

#### 相关反汇编

二进制 `mdp-ssit-complex-riscv64-xs.elf` 使用
`riscv64-linux-gnu-objdump -d -M no-aliases` 得到下面的相关部分。`store_s0`、
`store_s1`、`store_s2` 的 DEP 指令序列仅起始地址不同，且每段的末尾分别在
`0x8000016c`、`0x800001b4`、`0x800001fc` 执行 `sd`；中间重复的 32 组压缩
`c.addi` 在表中以省略号表示。反汇编中的 `c.jalr a5`、`c.jalr s0` 对应 `run_pair`
对 store/load 函数指针的两次间接调用，随后 `fence rw,rw` 隔离下一组测试。

```plain
000000008000012a <store_s0>:
    8000012a:  82aa        c.mv    t0,a0
    8000012c:  0285        c.addi  t0,1
    8000012e:  12fd        c.addi  t0,-1
    ...                     # 继续执行 31 组 c.addi +1/-1
    8000016c:  00b2b023    sd      a1,0(t0)       # S0
    80000170:  8082        c.jr    ra

0000000080000172 <store_s1>:
    80000172:  82aa        c.mv    t0,a0
    ...
    800001b4:  00b2b023    sd      a1,0(t0)       # S1
    800001b8:  8082        c.jr    ra

00000000800001ba <store_s2>:
    800001ba:  82aa        c.mv    t0,a0
    ...
    800001fc:  00b2b023    sd      a1,0(t0)       # S2
    80000200:  8082        c.jr    ra

0000000080000202 <load_l0>:
    80000202:  611c        c.ld    a5,0(a0)       # L0
    80000204:  853e        c.mv    a0,a5
    80000206:  8082        c.jr    ra

0000000080000208 <load_l1>:
    80000208:  611c        c.ld    a5,0(a0)       # L1
    8000020a:  853e        c.mv    a0,a5
    8000020c:  8082        c.jr    ra

000000008000020e <load_l2>:
    8000020e:  611c        c.ld    a5,0(a0)       # L2
    80000210:  853e        c.mv    a0,a5
    80000212:  8082        c.jr    ra

0000000080000214 <run_pair>:
    80000228:  9782        c.jalr  a5             # 调用 store
    80000232:  9402        c.jalr  s0             # 调用 load
    80000234:  0330000f    fence   rw,rw
    8000023e:  8082        c.jr    ra
```

`main` 将函数地址传给 `run_pair`。下面的地址装载序列可以直接验证 C 源码所列的
`S0/L0 → S1/L1 → S2/L0 → S1/L2 → S1/L0 → S0/L2` 顺序，而不是六个临时复制的
load/store 指令：

```plain
80000248:  ... # a1 = 0x80000202 <load_l0>
80000250:  ... # a0 = 0x8000012a <store_s0>
8000026a:  jal ra,80000214 <run_pair>
80000276:  ... # a1 = 0x80000208 <load_l1>
80000280:  ... # a0 = 0x80000172 <store_s1>
80000286:  jal ra,80000214 <run_pair>
80000292:  ... # a1 = 0x80000202 <load_l0>
8000029c:  ... # a0 = 0x800001ba <store_s2>
800002a2:  jal ra,80000214 <run_pair>
800002ae:  ... # a1 = 0x8000020e <load_l2>
800002b8:  ... # a0 = 0x80000172 <store_s1>
800002be:  jal ra,80000214 <run_pair>
800002ca:  ... # a1 = 0x80000202 <load_l0>
800002d4:  ... # a0 = 0x80000172 <store_s1>
800002da:  jal ra,80000214 <run_pair>
800002e6:  ... # a1 = 0x8000020e <load_l2>
800002f0:  ... # a0 = 0x8000012a <store_s0>
800002f6:  jal ra,80000214 <run_pair>
```

测试使用下面的命令编译和运行；仿真使用 `--dump-wave-full`，**没有**设置
`--max-cycles/-C`，因此 FST 覆盖从复位到 `HIT GOOD TRAP` 的全部 9764 个周期：

```bash
cd ~/mdp-kmhv3/nexus-am/tests/mdp-ssit-complex
source ~/mdp-kmhv3/env.sh
make ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1 -j2

~/mdp-kmhv3/XiangShan/build/emu \
  -i build/mdp-ssit-complex-riscv64-xs.bin \
  --no-diff --dump-wave-full \
  --wave-path=~/mdp-kmhv3/XiangShan/build/mdp-ssit-complex.fst \
  --force-dump-result
```

仿真输出 `mdp ssit complex: final=6 sum=21` 并在 `PC=0x8000033e` 进入 `HIT GOOD TRAP`。
本节使用开源 `wavekit` 解析生成的
`/home/yanyusong/mdp-kmhv3/XiangShan/build/mdp-ssit-complex.fst`，仍以 `TOP.clock`
上升沿采样。反汇编确认各静态内存指令 PC 如下：

| 指令 | 静态 PC | FST 中的 10 位 folded PC |
| --- | --- | --- |
| `S0: sd a1, 0(t0)` | `0x8000016c` | 183 |
| `S1: sd a1, 0(t0)` | `0x800001b4` | 219 |
| `S2: sd a1, 0(t0)` | `0x800001fc` | 255 |
| `L0: ld a5, 0(a0)` | `0x80000202` | 256 |
| `L1: ld a5, 0(a0)` | `0x80000208` | 261 |
| `L2: ld a5, 0(a0)` | `0x8000020e` | 262 |

`ldpc/stpc` 是 CtrlBlock 从 FTQ 身份恢复 PC 后做 `XORFold` 的结果，不能直接把 256、219 等值当作完整虚拟地址。对于每个真实 RAW 违例，`backend.io_mem_mdpTrain_valid` 在 redirect 产生拍有效，四拍后 SSIT 的 `s2_mempred_update_req_valid` 有效并携带更新前的 `loadAssigned/storeAssigned` 与旧 SSID。下面的总表是后续四个小节的共同波形依据：

| `mdpTrain` 周期（时间） | SSIT s2 周期（时间） | `ldpc/stpc` | `loadAssigned/storeAssigned` | 旧 `loadSSID/storeSSID` | 覆盖分支 |
| --- | --- | --- | --- | --- | --- |
| 4407（8814） | 4411（8822） | 256 / 183（L0/S0） | 0 / 0 | 无 / 无 | b00 |
| 4584（9168） | 4588（9176） | 261 / 219（L1/S1） | 0 / 0 | 无 / 无 | b00 |
| 4709（9418） | 4713（9426） | 256 / 255（L0/S2） | 1 / 0 | 4 / 无 | b10 |
| 4832（9664） | 4836（9672） | 262 / 219（L2/S1） | 0 / 1 | 无 / 1 | b01 |
| 4943（9886） | 4947（9894） | 256 / 219（L0/S1） | 1 / 1 | 4 / 1 | b11，合并 |
| 5066（10132） | 5070（10140） | 262 / 183（L2/S0） | 1 / 1 | 24 / 4 | b11，合并 |

SSIT 的四个更新分支均在同一段源码中实现：

[`StoreSet.scala:246`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L246)

```scala
switch (Cat(s2_loadAssigned, s2_storeAssigned)) {
  is ("b00".U(2.W)) { /* allocate and assign both entries */ }
  is ("b10".U(2.W)) { /* assign a store-set ID to the store */ }
  is ("b01".U(2.W)) { /* assign a store-set ID to the load */ }
  is ("b11".U(2.W)) { /* choose the winner and rewrite both entries */ }
}
```

### SSIT 四种情况下的更新流程分析

#### 情况 (一): 违例的 Load 和 Store 均不在 Store Set 中

这一情况对应 `Cat(loadAssigned, storeAssigned) == b00`。波形中有两次独立观测，用于先建立两个不同的 Store Set：

* 4411（8822）更新 `L0/S0`：`loadAssigned=0`、`storeAssigned=0`，随后 L0 与 S0 获得同一组 SSID；后续 4713 拍再次读取 L0 时，其旧 SSID 为 4，证明第一组已经写入并可被查询。
* 4588（9176）更新 `L1/S1`：同样为 `0/0`。后续 4836 拍读取 S1 时 `storeAssigned=1`、旧 SSID 为 1，证明第二组独立表项已经建立。

硬件并不为每次 b00 简单使用一个全局递增编号，而是先对 load/store 的 folded PC 分别做
`XORFold(..., SSIDWidth)`，再选择较小者作为 `s2_allocSsid`；所以两次 b00 在本波形中形成了可区分的 Store Set（后续可见的 4 与 1）。这正是后续 b11 能观察到“两个已分配但不同 ID 的 set 合并”的前提。

[`StoreSet.scala:198`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L198)

```scala
val s2_ldSsidAllocate = XORFold(s2_mempred_update_req.ldpc, SSIDWidth)
val s2_stSsidAllocate = XORFold(s2_mempred_update_req.stpc, SSIDWidth)
val s2_allocSsid = Mux(s2_ldSsidAllocate < s2_stSsidAllocate,
  s2_ldSsidAllocate, s2_stSsidAllocate)

is ("b00".U(2.W)) {
  update_ld_ssit_entry(..., ssid = s2_allocSsid, strict = false.B)
  update_st_ssit_entry(..., ssid = s2_allocSsid, strict = false.B)
}
```

#### 情况 (二): 违例的 Load 在 Store Set 中, Store 不在 Store Set 中

第三个 pair 为 `S2 -> L0`。L0 已在第一组 b00 中出现，而 S2 是新的静态 store PC；在
4713（9426）的 SSIT s2 拍，波形为 `ldpc=256`、`stpc=255`、`loadAssigned=1`、
`storeAssigned=0`、`loadOldSSID=4`，精确命中 b10 分支。

该分支不改变 load 的既有归属，而是将新 store 写入 load 的 set。因此下一次遇到 S2 时，
它会被视为 L0 所属依赖集合中的候选 producer；这是 Store Set 从“观察到一次依赖”扩展到“同一 load 的多个可能 store”的过程。

[`StoreSet.scala:264`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L264)

```scala
is ("b10".U(2.W)) {
  update_st_ssit_entry(
    pc = s2_mempred_update_req.stpc,
    valid = true.B,
    ssid = s2_ldSsidAllocate,
    strict = false.B
  )
}
```

#### 情况 (三): 违例的 Load 不在 Store Set 中, Load 在 Store Set 中

第四个 pair 为 `S1 -> L2`。S1 已由第二次 b00 建立，而 L2 是新的静态 load PC。4836（9672）的波形显示 `ldpc=262`、`stpc=219`、`loadAssigned=0`、`storeAssigned=1`、`storeOldSSID=1`，因此命中 b01 分支。

一个值得注意的源码细节是：此实现给新 load 写入的是 `s2_stSsidAllocate`（由 store PC 计算的分配 ID），而不是直接复制波形中的 `s2_storeOldSSID`。本例中 `S1` 的旧 SSID 是 1，而 L2 在下一次 b11 查询时显示旧 SSID 为 24；这正是后面 `L2/S0` 能形成两个不同 Store Set 并再次触发合并的原因。换言之，波形不仅证明 b01 被执行，也揭示了该实现的“按 store PC 再折叠分配”语义。

[`StoreSet.scala:274`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L274)

```scala
is ("b01".U(2.W)) {
  update_ld_ssit_entry(
    pc = s2_mempred_update_req.ldpc,
    valid = true.B,
    ssid = s2_stSsidAllocate,
    strict = false.B
  )
}
```

#### 情况 (四): 违例的 Load 和 Store 均在 Store Set 中

第五个 pair `S1 -> L0` 在 4947（9894）触发 b11：`loadAssigned=1`、
`storeAssigned=1`，但旧 SSID 分别为 4 与 1，`s2_ssidIsSame=0`。这不是重复写入同一
表项，而是一次真正的 Store Set 合并：源码比较两个旧 SSID，选择较小的 winner，并把
L0、S1 都重写为 winner。本例 winner 为 1。

第六个 pair `S0 -> L2` 在 5070（10140）再次命中 b11，旧 SSID 为 24 与 4，
`s2_ssidIsSame=0`，因此又执行一次合并，winner 为 4。两次 b11 都不是 same-set 的
strict 升级路径；后者需要 `s2_ssidIsSame=1`，本完整 FST 中针对这两个测试 pair 没有
观察到该条件为 1。这里的结论应明确为：测试覆盖了 b11 的 **不同 Store Set 合并** 子路径，
而非“已同 set 后再违例”的 strict 子路径。

[`StoreSet.scala:284`](/home/yanyusong/mdp-kmhv3/XiangShan/src/main/scala/xiangshan/mem/mdp/StoreSet.scala#L284)

```scala
is ("b11".U(2.W)) {
  update_ld_ssit_entry(..., ssid = s2_winnerSSID, strict = false.B)
  update_st_ssit_entry(..., ssid = s2_winnerSSID, strict = false.B)
  when(s2_ssidIsSame){
    data_array.io.wdata(SSIT_UPDATE_LOAD_READ_PORT).strict := true.B
    debug_strict(s2_mempred_update_req.ldpc) := true.B
  }
}
```

从性能角度看，b00/b10/b01 都是在扩大预测器已知的依赖关系，b11 则把两个原本独立的依赖图连通。合并后，LFST 会把同一 SSID 的最近未完成 store 暴露给 dispatch；这降低 RAW replay 的概率，但也可能让更多 load 因 `loadWaitBit` 等待无关但被合并到同一 Store Set 的 store。因此，SSIT 的准确性直接决定了“避免 replay”与“过度串行化”之间的平衡。
