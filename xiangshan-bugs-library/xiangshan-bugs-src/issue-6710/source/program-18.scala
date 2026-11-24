// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L484-L553
  val vecSchdParams = {
    implicit val schdType: SchedulerType = VecScheduler()
    SchdBlockParams(Seq(
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX0",
          Seq(VialuCfg, VimacCfg, VmoveCfg, VfcvtCfg, VfmacCfg),
          Seq(VfWB(port = 3, 0), IntWB(port = 4, 1), FpWB(port = 6, 0)),
          Seq(Seq(VfRD(0, 0), FpRD(12, 1)), Seq(VfRD(1, 0)), Seq(VfRD(2, 0))),
          vlRD = VlRD(0, 0),
          v0WB = V0WB(0, 0),
          v0RD = V0RD(0, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX1",
          Seq(VialuCfg, VidivCfg, VfmacCfg, VfdivCfg),
          Seq(VfWB(port = 4, 0)),
          Seq(Seq(VfRD(3, 0), FpRD(13, 1)), Seq(VfRD(4, 0)), Seq(VfRD(5, 0))),
          vlRD = VlRD(1, 0),
          v0WB = V0WB(1, 0),
          v0RD = V0RD(1, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX2",
          Seq(VialuCfg),
          Seq(VfWB(port = 5, 0)),
          Seq(Seq(VfRD(6, 0)), Seq(VfRD(7, 0)), Seq(VfRD(8, 0))),
          vlRD = VlRD(2, 0),
          v0WB = V0WB(2, 0),
          v0RD = V0RD(2, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX3",
          Seq(VialuCfg),
          Seq(VfWB(port = 6, 0)),
          Seq(Seq(VfRD(9, 0)), Seq(VfRD(10, 0)), Seq(VfRD(11, 0))),
          vlRD = VlRD(3, 0),
          v0WB = V0WB(3, 0),
          v0RD = V0RD(3, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VSTD0",
          Seq(VStdCfg),
          Seq(),
          Seq(Seq(VfRD(12, 0))),
        )
      ), numEntries = 16, numEnq = 2, numComp = 14),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VSTD1",
          Seq(VStdCfg),
          Seq(),
          Seq(Seq(VfRD(13, 0))),
        )
      ), numEntries = 16, numEnq = 2, numComp = 14),
    ),
      numPregs = vfPreg.numEntries,
      numDeqOutside = 0,
      schdType = schdType,
      rfDataWidth = vfPreg.dataCfg.dataWidth,
    )
  }
