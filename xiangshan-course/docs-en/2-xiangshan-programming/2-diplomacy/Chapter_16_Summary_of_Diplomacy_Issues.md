<!-- # 第十六章 Diplomacy 问题总结 -->
# Chapter 16: Summary of Diplomacy Issues

<!-- 1. AXI CrossBar， AXI ID [0,65536] 范围太大，导致**编译结束不了**的问题 -->
1. The AXI crossbar has an excessively large AXI ID range of [0, 65536], causing compilation to **never finish**.

<!-- 应用场景 1 个 Master 和 2 个 Slave -->
Application scenario: one master and two slaves.

<!-- ![画板](img/chapter-16-summary-of-diplomacy-issues/figure-001-diplomacy-master-slave.jpeg) -->
![Diagram](img/chapter-16-summary-of-diplomacy-issues/figure-001-diplomacy-master-slave.jpeg)



<!-- 代码实现 -->
Implementation:

```c
  // AXI4 Bus
  // AXI4 bus
  val axi_reg_imsic = Option.when(soc.IMSICBusType == device.IMSICBusType.AXI)(LazyModule(new aia.AXIRegIMSIC_WRAP(soc.IMSICParams, seperateBus = false)))

  val axi = axi_reg_imsic.map { axi_reg_imsic =>
    val axinode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
      Seq(AXI4MasterParameters(
        name = "s_axi_",
        id = IdRange(0, 65536),
       
      ))
    )))
    axi_reg_imsic.imsic_xbar1to2 := AXI4Buffer() := axinode
    axinode
  }
```

<!-- 默认  maxFlight  = 7 -->
The default value of `maxFlight` is 7.

<!-- 编译问题 -->
Compilation problem:

```c
make verilog MFC=1 NUM_CORES=1 WITH_CONSTANTIN=0 CONFIG=XSNoCTopConfig
```

<!-- 编译 24h 依然没有结束 -->
The compilation still had not finished after 24 hours.

<!-- [附件: ca3ae03ecd6a83bb92a8552db1b7436a.mp4](./attachments/q-Dk3NGDymhXbNEK/ca3ae03ecd6a83bb92a8552db1b7436a.mp4) -->
[Attachment: ca3ae03ecd6a83bb92a8552db1b7436a.mp4](./attachments/q-Dk3NGDymhXbNEK/ca3ae03ecd6a83bb92a8552db1b7436a.mp4)

<!-- 暂时解决方法 -->
Temporary workaround:

```c
 // AXI4 Bus
  val axi_reg_imsic = Option.when(soc.IMSICBusType == device.IMSICBusType.AXI)(LazyModule(new aia.AXIRegIMSIC_WRAP(soc.IMSICParams, seperateBus = false)))

  val axi = axi_reg_imsic.map { axi_reg_imsic =>
    val axinode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
      Seq(AXI4MasterParameters(
        name = "s_axi_",
        id = IdRange(0, 65536),
        maxFlight = Some(0)
      ))
    )))
    axi_reg_imsic.imsic_xbar1to2 := AXI4Buffer() := axinode
    axinode
  }

  val axi4 = axi.map(x => InModuleBody(x.makeIOs()))
```

<!-- a. 改为maxFlight = Some(0) 之后 可以编译成功， -->
a. After changing to `maxFlight = Some(0)`, compilation succeeds.

<!-- b. id = IdRange(0, 4096), maxFlight 保持不变，可以编译成功 -->
b. With `id = IdRange(0, 4096)` and the original `maxFlight`, compilation also succeeds.

<!-- c. AXI ID 的范围 该如何设置 -->
c. How should the AXI ID range be configured?

<!-- d. maxFlight 对应AXI 协议的哪种能力 -->
d. Which AXI protocol capability does `maxFlight` represent?

<!-- e. 当前改法是否有副作用，对整个SoC 的影响 -->
e. Does the current change have side effects, and how does it affect the SoC as a whole?

<!-- （1） 是否会出现死锁、活锁 (不会) -->
(1) Can it cause deadlock or livelock? (No.)

<!-- （2）是否会影响性能 （会） -->
(2) Does it affect performance? (Yes.)

<!-- **实验： 分别生成 0 maxflight+65536 ID range 和 2 maxflight+2 ID range,rtl区别如下** -->
**Experiment: generate RTL separately with `maxFlight = 0` and an ID range of 65,536, and with `maxFlight = 2` and an ID range of 2; the RTL differences are shown below.**

![1768380173488-1b813c08-fd99-4854-923f-67510cb2537b.png](img/chapter-16-summary-of-diplomacy-issues/figure-002-diplomacy-maxflight-id-range.png)

<!-- 变化有AXI的5个通道的id位宽和Xbar的一些计数控制逻辑，实际的第二个图片的存储部分，仍然是2个深度。 -->
The changes affect the ID width of AXI's five channels and some Xbar counter/control logic. In the second image, the actual storage section still has a depth of two.

<!-- 所以，我们认为： -->
Therefore, we conclude:

<!-- + 导致2max flight+65536编译不过是rocket-chip的axi编译问题，会生成成倍数的下述代码，资源增多。 -->
+ The failure to compile with `maxFlight = 2` and 65,536 IDs is an AXI compilation issue in Rocket Chip; it generates the following code in multiples, increasing resource usage.
<!-- + 实际存储部分深度一致，是因为存储部分不是Xbar的，是bundle部分。 -->
+ The actual storage depth is the same because the storage belongs to the bundle rather than the Xbar.
<!-- + rocket-chip 的axi写的一般（性能），仅限于能用，后续应该减少使用。 -->
+ Rocket Chip's AXI implementation is mediocre in terms of performance and is merely usable; its use should be reduced in the future.
    <!-- - 比如 maxflight 参数，rocket-chip认为与 id个数有关，并影响到了Xbar（其实没有任何关系）。 -->
    - For example, Rocket Chip treats `maxFlight` as related to the number of IDs and lets it affect the Xbar, although the two are actually unrelated.

![1768386572769-a2100187-79a4-448f-b578-ab27e924943b.png](img/chapter-16-summary-of-diplomacy-issues/figure-003-diplomacy-maxflight-rocket-chip.png)

![1768386616090-a00251d6-0c66-4bd3-8c4c-eecd611f15df.png](img/chapter-16-summary-of-diplomacy-issues/figure-004-diplomacy-maxflight-rocket-chip.png)

<!-- 附 生成的rtl：[附件: build-TEE.tar.gz](./attachments/q-Dk3NGDymhXbNEK/build-TEE.tar.gz) -->
Generated RTL: [Attachment: build-TEE.tar.gz](./attachments/q-Dk3NGDymhXbNEK/build-TEE.tar.gz)



<!-- > 更新: 2026-05-26 17:10:31
> 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/bc3zn5dan1qpt9s6> -->
> Updated: 2026-05-26 17:10:31
> Original: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/bc3zn5dan1qpt9s6>
