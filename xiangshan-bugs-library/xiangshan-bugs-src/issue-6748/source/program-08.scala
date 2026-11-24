// https://github.com/OpenXiangShan/XiangShan/blob/6705b63685d301f8c0960f7cbcc8b964660dc58e/src/main/scala/xiangshan/backend/rob/Rob.scala#L907
  val exceptionHappen = (state === s_idle) && deqPtrEntryValid && (intrEnable || deqHasException && (!deqIsVlsException || deqVlsCanCommit)) && !lastCycleFlush
