// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/Ifu.scala#L709-L713
    val attribute = checkerRedirect.bits.attribute
    val canTrain = attribute.isDirect || attribute.isReturn ||
      checkerRedirect.bits.invalidTaken || checkerRedirect.bits.notCfiTaken
    b.valid          := wbValid && checkerRedirect.valid
    b.bits.canTrain  := canTrain
