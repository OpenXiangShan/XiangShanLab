// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/Parameters.scala#L586
      VecScheduler() -> (if (EnableBackendV2Config) backend.BackendV2SchdParams.vecSchdParams else vecSchdParams),
