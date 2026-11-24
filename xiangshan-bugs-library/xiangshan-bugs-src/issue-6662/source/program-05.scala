// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/Parameters.scala#L666
  def HasMptCheck = coreParams.HasMptCheck && !coreParams.HasBitmapCheck
