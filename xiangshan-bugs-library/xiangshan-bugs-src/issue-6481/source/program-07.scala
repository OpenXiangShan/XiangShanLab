// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ifu/PredChecker.scala#L174
  io.resp.stage2Out.checkerRedirect.valid          := mispredIdxNext.valid && wbValid
