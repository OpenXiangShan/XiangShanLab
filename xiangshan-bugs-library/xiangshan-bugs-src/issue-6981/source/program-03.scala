val VmoveCfg = FuConfig (
  name = "vmove",
  fuType = FuType.vmove,
  fuGen = null,
  srcData = Seq(
    Seq(VecData(), VecData(), VecData()), // vs1, vs2, vd_old
