// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L659
      s2Resp.bits.matchInvalid     := s2PaddrNoMatch && s2SafeForward // if multi match, let load replay.
