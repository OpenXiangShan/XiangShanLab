// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/BackendParams.scala#L771-L799
  def vecSchdParams = {
    implicit val schdType: SchedulerType = VecScheduler()
    SchdBlockParams(Seq(
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX0",
          Seq(VialuCfg, VimacCfg, VfcvtCfg, VSetRvfWvfCfg, VmoveCfg),
          Seq(VfWB(port = 0, 0), V0WB(port = 0, 0), IntWB(port = 7, 0), FpWB(port = 6, 0)),
          Seq(Seq(VfRD(0, 0)), Seq(VfRD(1, 0)), Seq(VfRD(2, 0)), Seq(V0RD(0, 0))),
          vlWB = VlWB(port = vfSchdVlWbPort, 0),
          vlRD = VlRD(0, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 12),
      IssueBlockParams(Seq(
        ExeUnitParams(
          "VFEX1",
          Seq(VialuCfg, VfdivCfg, VidivCfg),
          Seq(VfWB(port = 1, 0), V0WB(port = 1, 0), FpWB(port = 7, 0)),
          Seq(Seq(VfRD(3, 0)), Seq(VfRD(4, 0)), Seq(VfRD(5, 0)), Seq(V0RD(1, 0))),
          vlRD = VlRD(1, 0),
        ),
      ), numEntries = 16, numEnq = 2, numComp = 12),
    ),
      numPregs = numPregsVec,
      numDeqOutside = 0,
      schdType = schdType,
      rfDataWidth = rfDataWidthVec,
    )
  }
