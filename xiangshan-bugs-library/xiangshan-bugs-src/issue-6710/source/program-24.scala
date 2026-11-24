// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/fu/VecFuConfig.scala#L231
  def fromFuConfig(cfg: FuConfig, fuGen2: (Parameters, VecFuConfig) => Func = null, _latency: Option[Int] = None): VecFuConfig = {
