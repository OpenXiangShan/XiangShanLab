// https://github.com/OpenXiangShan/XiangShan/blob/6714a69d001dbb9e213ff077a518fb78e598c48a/src/main/scala/xiangshan/backend/vector/Decoder/Split/SplitTable.scala#L1123-L1125
      VSHA2MS_VV -> dup(null, null, vsha256ms, null)(_.S1v),
      VSHA2CL_VV -> dup(null, null, vsha256cl, null)(_.S1v),
      VSHA2CH_VV -> dup(null, null, vsha256ch, null)(_.S1v),
