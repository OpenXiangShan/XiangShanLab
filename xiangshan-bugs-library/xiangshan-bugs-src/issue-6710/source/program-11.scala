// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/fu/FuConfig.scala#L745-L748
  val VSha256cCfg = FuConfig(
    name = "vsha256c",
    fuType = FuType.vsha256c,
    fuGen = (p: Parameters, cfg: FuConfig) => Module(new VSha256cWrapper(cfg)(p).suggestName("VSha256c")),
