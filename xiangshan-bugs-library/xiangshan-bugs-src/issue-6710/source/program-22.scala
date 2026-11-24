// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/ExuParam.scala#L322
      fuConfigs = exeUnitParams.fuConfigs.map(x => VecFuConfig.allConfigs.find(_.fuConfig == x).get),
