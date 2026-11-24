// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/fu/FuConfig.scala#L724-L727
  val VSha256msCfg = FuConfig(
    name = "vsha256ms",
    fuType = FuType.vsha256ms,
    fuGen = (p: Parameters, cfg: FuConfig) => Module(new VSha256msWrapper(cfg)(p).suggestName("VSha256ms")),
