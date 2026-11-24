// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L511-L513
    case p => when(p.valid && p.bits.attribute.isReturn && uras.io.specOut.isCanUse) {
        p.bits.target := uras.io.specOut.retTarget
      }
