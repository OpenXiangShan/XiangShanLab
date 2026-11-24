// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/Exu.scala#L41
  val fus: Seq[Func] = param.fuConfigs.map(cfg => cfg.fuGen2(p, cfg))
