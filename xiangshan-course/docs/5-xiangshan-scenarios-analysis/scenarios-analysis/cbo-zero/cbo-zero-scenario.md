# 香山昆明湖 CBO Zero 三场景演示与波形分析

## 结论摘要

本报告分析 Kunminghu V2 的完整波形 2026-08-25-13-36-09.fst。分析使用 /nfs/home/yanyusong/wavekit 开源仓库中的 wavekit.FstReader，在 TOP.SimTop.cpu.l_soc.chi_llcBridge_opt.clock 上升沿采样；对于 Decoupled 接口，fire 定义为 valid && ready。

三个 cbo.zero 的架构语义均已复现：DCache hit 场景直接写零并保持 Dirty；两个 DCache miss 场景经 MissQueue/L2 返回后写入全零 cache line，并最终标记 Dirty。程序正常结束，emu 输出 HIT GOOD TRAP，242 条指令，4979 个 core cycles。

但当前 Kunminghu V2 RTL 的协议名称与最初期望不完全一致：

- 完整覆盖 CBO 请求被标记为 full_overwrite，MissQueue 选择 AcquirePerm -> Grant(no data)，不是 AcquireBlock -> GrantData。
- 当前 CHI REQ opcode 表生成 ReadNoSnp，没有 ReadShared；CHI DAT 中仍有 CompData。
- 因此 AM 程序可以验证零填充和 Dirty 状态，不能单独把硬件消息编码改成 AcquireBlock/GrantData 或 ReadShared。

关键锚点：

| 场景 | CBO PC / 指令字 | LSQ cboZeroStout fire | ROB 提交 | DCache 结果 |
|---|---|---:|---:|---|
| 1. L1D hit | 0x8000014a / 0x0047a00f | cycle 4452, time 8904 | cycle 4455, time 8910 | s3_req_miss=0, s3_hit=1，写 0，coh=Dirty(3) |
| 2. L1D miss、L2 hit | 0x8000019e / 0x0048200f | cycle 4895, time 9790 | cycle 4898, time 9796 | miss 返回后整行写 0，coh=Dirty(3) |
| 3. L1D/L2 cold miss | 0x800001ae / 0x0047a00f | cycle 4963, time 9926 | cycle 4966, time 9932 | miss 返回后整行写 0，coh=Dirty(3) |

## 演示程序说明与三场景触发保证

程序位于 [cbo-zero-demo.c:1](/nfs/home/yanyusong/cbo-env/nexus-am/apps/cbo-zero-scenario/cbo-zero-demo.c:1)。它使用普通 cacheable 数据、64B 对齐、RISC-V cbo.zero 内联汇编和 fence rw,rw。

### 地址布局

    __attribute__((aligned(64)))
    static volatile uint8_t same_set_lines[11][0x2000];

    __attribute__((aligned(64)))
    static volatile uint8_t target_l2_miss[64];

最终链接地址：

| 符号 | 地址 | 用途 |
|---|---:|---|
| same_set_lines[0] | 0x80001540 | 场景 1，先写入并读入 L1D |
| same_set_lines[1] | 0x80003540 | 场景 2，先进入层次结构，再驱逐出 L1D |
| same_set_lines[2..10] | 从 0x80005540 开始，步长 0x2000 | 与场景 2 目标保持同一个 DCache set 的冲突线 |
| target_l2_miss | 0x80001500 | 场景 3，程序显式访问前保持冷状态 |

Kunminghu V2 默认 DCache 是 128 sets、8 ways、64B line，参数见 [DCacheWrapper.scala:39-54](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:39)。所以 128 * 64 = 8192 = 0x2000，数组第二维步长正好跨过一个完整 set 的地址跨度。11 条同 set cache line 大于 8-way 容量，重复访问冲突线可以把目标线从 L1D 驱逐而保留在 L2。

### 场景 1：构造 DCache hit

    target_hit[0] = 0x5a;
    sink ^= read_line(target_hit);
    fence_rw();
    cbo_zero(target_hit);
    fence_rw();

字节存储和 ld 让 0x80001540 进入 L1D；波形 cycle 4409 观察到准备数据 0x5a，cycle 4449 的 CBO 是 hit。volatile 防止访问被删除，fence 固定准备访问和 CBO 的顺序。

### 场景 2：构造 DCache miss、L2 hit

    sink ^= read_line(target_l2_hit);
    for (unsigned i = 2; i < 11; ++i)
      sink ^= read_line(&same_set_lines[i][0]);
    fence_rw();

    for (unsigned i = 2; i < 11; ++i)
      sink ^= read_line(&same_set_lines[i][0]);
    fence_rw();

    cbo_zero(target_l2_hit);
    fence_rw();

第一次访问把目标线和冲突线带入缓存层次；第二次重新访问 9 条冲突线，使目标线超过 8-way L1D 的驻留能力。最终 CBO 在 DCache MainPipe 中 miss，L2 很快返回无数据 Grant。这个 L2 hit 由 L2 SourceD 事件确认，而不是依赖链接器偶然布局。

### 场景 3：构造 DCache/L2 cold miss

    cbo_zero(target_l2_miss);
    fence_rw();

target_l2_miss 在程序中没有普通 load/store 预热。CBO 进入 L1D 时是冷行，最终请求由 MissQueue 取得权限后直接形成全零 line。波形中该地址曾出现更早的 ReadNoSnp/CompData，但它远早于本次 CBO 的 MissQueue 请求，不能当作 CBO refill 响应。

## 构建、仿真工件与 wavekit 方法

Makefile 是 [Makefile:1](/nfs/home/yanyusong/cbo-env/nexus-am/apps/cbo-zero-scenario/Makefile)：

    NAME = cbo-zero-demo
    SRCS = cbo-zero-demo.c
    MARCH ?= rv64gc_zicbom_zicboz
    include $(AM_HOME)/Makefile.app

构建：

    make -s ARCH=riscv64-xs AM_HOME=/nfs/home/yanyusong/cbo-env/nexus-am

镜像：

/nfs/home/yanyusong/cbo-env/nexus-am/apps/cbo-zero-scenario/build/cbo-zero-demo-riscv64-xs.bin

完整波形仿真：

    ./build/emu --no-diff --dump-wave-full \
      -i /nfs/home/yanyusong/cbo-env/nexus-am/apps/cbo-zero-scenario/build/cbo-zero-demo-riscv64-xs.bin

结果：HIT GOOD TRAP，242 条 committed instructions，4979 core cycles。完整 FST 是 [2026-08-25-13-36-09.fst](/nfs/home/yanyusong/cbo-env/XiangShan/build/2026-08-25-13-36-09.fst)，日志为 /tmp/cbo-zero-demo-emu-requested.log。

wavekit 使用 PYTHONPATH=/nfs/home/yanyusong/wavekit/src 和 /nfs/home/yanyusong/wavekit/.venv/bin/python 调用 FstReader。所有周期是绝对 clock cycle；该 FST 上升沿采样时 wavekit 报告 time=2*cycle。CBO 身份由 ROB commit、LSQ cboZeroStout 和 CBO 指令编码三重确认。三个提交项均为 isStore=1，rfwen/fpwen/vecwen/v0wen=0，符合 CBO 不产生寄存器结果的语义。

## 源码路径总览

    DecodeUnit
      -> StoreUnit / StorePipe
      -> StoreQueue (数据槽写 0，等待 SBuffer，cboZeroStout)
      -> MemBlock (CBO store writeback)
      -> DCache MainPipe
           hit: s3_store_hit -> data_write
           miss: full_overwrite -> MissQueue
      -> MissQueue
           full_overwrite -> AcquirePerm
           Grant(no data) -> refill_and_store_data = new_data = 0
      -> MainPipe refill response -> data_write + meta_write(Dirty)
      -> L2 SourceD / CHI bridge

## 场景一：DCache Hit

### 波形证据

| cycle / time | 信号 | 波形值 | 含义 |
|---:|---|---|---|
| 4409 / 8818 | MainPipe io_data_write_valid, data, wmask, meta | 1, 0x5a, 0xff, coh=3 | 普通 store/refill 把目标线带入 L1D，状态已为 Dirty |
| 4449 / 8898 | s3_valid, addr, s3_req_miss, s3_hit, s3_store_hit | 1, 0x80001540, 0, 1, 1 | CBO 在 L1D hit |
| 4449 / 8898 | io_data_write_valid, wmask, data | 1, 0xff, 0 | 整条 64B line 写零 |
| 4452 / 8904 | LSQ cboZeroStout_valid/ready | 1/1 | StoreQueue 输出 fire |
| 4455 / 8910 | ROB commit | PC=0x8000014a, instr=0x0047a00f, ROB=59, SQ=5 | 指令提交 |

wmask=0xff 表示 8 个 64-bit bank 全选；coh=3 是 ClientStates.Dirty。Dirty 编码定义见 [Metadata.scala:11-21](/nfs/home/yanyusong/cbo-env/XiangShan/rocket-chip/src/main/scala/tilelink/Metadata.scala:11)。

### 源码因果

[MainPipe.scala:683-685](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:683) 令 update_data = miss || store_hit || amo_write；[MainPipe.scala:743-755](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:743) 计算 s3_store_can_go/s3_fire；[MainPipe.scala:778-793](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:778) 在 store hit 时选择写 mask；[MainPipe.scala:963-978](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:963) 生成 io.data_write。该端口直接接 BankedDataArray，见 [DCacheWrapper.scala:1307-1312](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1307)。

    val update_data = s3_req.miss || s3_store_hit || s3_can_do_amo_write
    io.data_write.valid := s3_valid && s3_update_data_cango && update_data
    io.data_write.bits.wmask := banked_wmask

## 场景二：DCache Miss、L2 Hit

### 波形证据

| cycle / time | 模块/信号 | 波形值 | 含义 |
|---:|---|---|---|
| 4879 / 9758 | MainPipe io_miss_req_valid | 1, addr=0x80003540, full_overwrite=1 | L1D miss，完整覆盖请求进入 MQ |
| 4880 / 9760 | MissQueue io_mem_acquire | opcode=7, addr=0x80003540 | 当前 RTL 的 AcquirePerm |
| 4883 / 9766 | L2 slice 1 taskFromArb_s2 | opcode=7, param=1, vaddr=0x2000155 | L2 收到 acquire |
| 4885 / 9770 | L2 slice 1 toSourceD | opcode=4, data=0 | TL SourceD Grant，无 GrantData |
| 4888 / 9776 | MissQueue io_mem_grant | opcode=4, data=0 | 无数据 Grant |
| 4889 / 9778 | MissQueue io_main_pipe_req | valid=1, addr=0x80003540 | 送回 MainPipe |
| 4892 / 9784 | MainPipe data/meta write | data=0, mask=0xff, coh=3 | 零填充并标 Dirty |
| 4895 / 9790 | LSQ CBO output | valid=1, ready=1 | StoreQueue 完成 |
| 4898 / 9796 | ROB commit | PC=0x8000019e, ROB=4, SQ=25 | CBO 提交 |

MainPipe 在 S2 将整行 store mask 转为 full_overwrite，见 [MainPipe.scala:832-850](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:832)：

    miss_req.store_mask := s2_req.store_mask
    miss_req.full_overwrite := s2_req.isStore && s2_req.store_mask.andR

MissQueue 在 full_overwrite 时选择 AcquirePerm，见 [MissQueue.scala:250-265](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:250)。Grant 无数据时，代码要求 full_overwrite 并直接把 new_data 写入 refill buffer，见 [MissQueue.scala:654-688](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:654)：

    }.otherwise {
      assert(full_overwrite)
      for (i <- 0 until blockRows) {
        refill_and_store_data(i) := new_data(i)
      }
      hasData := false.B
    }

io.main_pipe_req 在 w_l2hint || w_grantlast 时发出，见 [MissQueue.scala:878-895](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:878)。MainPipe 对 miss 选择全 bank mask，并在 miss_update_meta 时更新一致性元数据，见 [MainPipe.scala:563-584](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:563)。

## 场景三：DCache Miss、L2 Miss

### 波形证据

| cycle / time | 模块/信号 | 波形值 | 含义 |
|---:|---|---|---|
| 4947 / 9894 | MainPipe io_miss_req_valid | 1, addr=0x80001500, full_overwrite=1 | 冷行进入 MQ |
| 4948 / 9896 | MissQueue io_mem_acquire | opcode=7, addr=0x80001500 | 当前 RTL 仍是 AcquirePerm |
| 4951 / 9902 | L2 slice 0 taskFromArb_s2 | opcode=7, param=1, vaddr=0x2000114 | L2 收到权限请求 |
| 4953 / 9906 | L2 slice 0 toSourceD | opcode=4 | L2 SourceD 返回 Grant |
| 4956 / 9912 | MissQueue io_mem_grant | opcode=4 | 无数据 Grant |
| 4957 / 9914 | MissQueue io_main_pipe_req | valid=1, addr=0x80001500 | 送回 MainPipe |
| 4960 / 9920 | MainPipe data/meta write | data=0, mask=0xff, coh=3 | 零填充并标 Dirty |
| 4963 / 9926 | LSQ CBO output | valid=1, ready=1 | CBO writeback fire |
| 4966 / 9932 | ROB commit | PC=0x800001ae, ROB=8, SQ=26 | CBO 提交 |

### CHI 事务边界

最终 CBO miss 窗口（场景二约 4879--4892，场景三约 4947--4960）没有目标地址的 CHI tx.req。但同一 FST 中可见更早的读事务：

| 地址 | CHI request | CHI response | 解释 |
|---:|---|---|---|
| 0x80001500 | cycle 4382，opcode 0x04 | cycle 4398/4399，opcode 0x04，data 全零 | 早于场景三 CBO 请求 565 cycles |
| 0x80003540 | cycle 4511，opcode 0x04 | cycle 4527/4528，opcode 0x04，data 全零 | 早于场景二 CBO 请求 368 cycles |

这些事务可能来自前序/并发 demand 或预取路径；无论具体来源如何，它们都不是与最终 CBO MissQueue request 关联的 outstanding response。因此不能据此宣称 CBO 已走 ReadShared -> CompData。

## CBO 指令进入存储系统的源码分析

### Decode 与 StoreUnit

CBO Zero 在解码表中作为 store functional unit 的 LSUOpType.cbo_zero：[DecodeUnit.scala:473-482](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:473)。操作编码和识别函数见 [package.scala:582-597](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/package.scala:582)。

    CBO_ZERO -> XSDecode(SrcType.reg, SrcType.DC, SrcType.X,
      FuType.stu, LSUOpType.cbo_zero, SelImm.IMM_S)

StoreUnit 把 CBO 标成整行 wlineflag，并送出地址、SQ/ROB 信息，见 [StoreUnit.scala:117-126](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:117) 和 [StoreUnit.scala:236-258](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala:236)。这一步是 hit/miss 查询；真正的 cache data 写入在 MainPipe S3。

### StoreQueue、SBuffer 与 MemBlock

StoreQueue 对 cbo.zero 写数据槽固定写 0，见 [StoreQueue.scala:594-612](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:594)。CBO 的整行写入会使 wline 路径覆盖整条 line；SBuffer 对每个 word/byte 在 line_write_buffer_wline 为真时写入数据，见 [Sbuffer.scala:131-158](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:131)。SBuffer 对 wline 的提交数据有 rawData === 0 断言，见 [Sbuffer.scala:959-970](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala:959)。

StoreQueue 在 SBuffer 清空后产生 cboZeroStout，见 [StoreQueue.scala:993-1010](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:993) 和 [StoreQueue.scala:1078-1095](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1078)。MemBlock 对 CBO writeback 提供优先级，见 [MemBlock.scala:1366-1388](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/mem/MemBlock.scala:1366)。

### MainPipe 一致性状态

ClientStates.Dirty 是数值 3，写权限要求 state 大于 Branch，见 [Metadata.scala:11-21](/nfs/home/yanyusong/cbo-env/XiangShan/rocket-chip/src/main/scala/tilelink/Metadata.scala:11)。MainPipe 的 miss_new_coh 使用 missCohGen 根据写命令和 grant param 生成目标状态，见 [MainPipe.scala:570-584](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:570)。完整覆盖的 new_data 为零，写 mask 为整行，所以波形最终同时看到 data=0 和 coh=3。

### L2/CHI

DCacheWrapper 实例化 MainPipe/MissQueue 并连接 refill 信息，见 [DCacheWrapper.scala:1041-1060](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala:1041)。L2 MainPipe 向下一级生成 ReadNoSnp 或 WriteNoSnpFull，见 [openLLC/MainPipe.scala:415-438](/nfs/home/yanyusong/cbo-env/XiangShan/openLLC/src/main/scala/openLLC/MainPipe.scala:415)。CHI REQ 表列出 ReadNoSnp(0x04)，没有 ReadShared，见 [CHISNFOpcodesREQ.scala:13-23](/nfs/home/yanyusong/cbo-env/XiangShan/openLLC/openNCB/src/main/scala/openncb/chi/opcode/CHISNFOpcodesREQ.scala:13)。CHI DAT 的 CompData 是 0x04，见 [CHISNFOpcodesDAT.scala:14-21](/nfs/home/yanyusong/cbo-env/XiangShan/openLLC/openNCB/src/main/scala/openncb/chi/opcode/CHISNFOpcodesDAT.scala:14)。若 snoop 没有提供数据，ResponseUnit 构造 ReadNoSnp 发向内存，见 [ResponseUnit.scala:307-325](/nfs/home/yanyusong/cbo-env/XiangShan/openLLC/src/main/scala/openLLC/ResponseUnit.scala:307)。

## 三场景源码与事件对照

| 层次 | 场景 1 | 场景 2 | 场景 3 |
|---|---|---|---|
| StorePipe/MainPipe | 4449：hit + store hit，直接 data_write | 4879 miss request；4892 refill write | 4947 miss request；4960 refill write |
| StoreQueue | 4452 cboZeroStout.fire | 4895 fire | 4963 fire |
| MissQueue | 不经过 MQ | 4880 AcquirePerm；4888 Grant | 4948 AcquirePerm；4956 Grant |
| L2 | 无 CBO miss 事务 | slice 1：4883 task opcode 7，4885 SourceD opcode 4 | slice 0：4951 task opcode 7，4953 SourceD opcode 4 |
| CHI | 非 CBO 读事务可见 | CBO 窗口无目标 CHI | CBO 窗口无目标 CHI |
| 最终状态 | data=0，coh=Dirty | data=0，coh=Dirty | data=0，coh=Dirty |

MissQueue 的模块级状态枚举是 s_idle/s_sreq/s_wresp/s_lsq_resp，见 [MissQueue.scala:299-330](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:299)；请求阶段寄存器 s_acquire/s_grantack/s_mainpipe_req 的转换见 [MissQueue.scala:487-505](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:487)。三次 CBO 输出均为 valid && ready，没有 CBO 输出端 backpressure。

## Redirect、异常与性能影响

cbo.zero 不改变控制流。波形查询中，CBO 相邻窗口没有由 CBO 自身产生的 ROB flush/前端重定向：

| 窗口 | 相关事件 | 结论 |
|---|---|---|
| 场景 1，4400--4465 | cycle 4416 flush / 4417 memory redirect，ROB=58；CBO 在 4452/4455 | 前序内存恢复事件，不属于 CBO ROB=59 |
| 场景 2，4870--4910 | cycle 4909 flush / 4910 memory redirect，ROB=5；CBO 已于 4898 提交 | 后续事务事件 |
| 场景 3，4930--4980 | cycle 4977 flush / 4978 memory redirect，ROB=9；CBO 已于 4966 提交 | 后续事务事件 |

CBO 输出端 valid && !ready 在三个窗口均为空。MissQueue 到 DCache 最终写零的延迟为场景 2：4879 -> 4892，13 cycles；场景 3：4947 -> 4960，13 cycles。该延迟由 acquire、L2 SourceD/Grant 和 MainPipe refill 交接造成，不是 CBO commit 或前端 redirect 造成。

## 协议差异、限制与最终结论

当前 RTL 的关键选择：

    // MainPipe.scala
    miss_req.full_overwrite := s2_req.isStore && s2_req.store_mask.andR

    // MissQueue.scala
    acquire := Mux(req.full_overwrite, acquirePerm, acquireBlock)
    ...
    assert(full_overwrite)
    refill_and_store_data(i) := new_data(i)

源码位置分别是 [MainPipe.scala:832-850](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala:832)、[MissQueue.scala:250-265](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:250) 和 [MissQueue.scala:654-688](/nfs/home/yanyusong/cbo-env/XiangShan/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala:654)。

用户期望：

    L1 miss + L2 hit: AcquireBlock -> GrantData -> fill zero
    L1/L2 miss:       CHI ReadShared -> CompData -> fill zero

当前 Kunminghu V2 实测：

    L1 miss + L2 hit: AcquirePerm -> Grant(no data) -> MainPipe fill zero
    L1/L2 miss:       AcquirePerm -> Grant(no data) -> MainPipe fill zero
    other demand:     ReadNoSnp -> CompData (not the CBO outstanding response)

三个 CBO Zero 的零填充、整行写和 Dirty 语义已经通过正式完整波形验证；AcquireBlock/GrantData 和 ReadShared 这两个具体协议路径没有在当前 RTL 中出现，且不能通过只修改 AM APP 来强制出现。若必须复现这些协议名称，需要修改 cache/CHI RTL 或切换到对应实现版本。

