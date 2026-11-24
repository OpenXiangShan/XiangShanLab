// ICacheMainPipe.scala:394-406
  io.toIfu.req.bits.zipWithIndex.foreach { case (req, i) =>
    req.valid                := s1_req(i).valid
    req.startVAddr           := s1_req(i).startVAddr
    req.ftqIdx               := s1_req(i).ftqIdx
    req.takenCfiOffset.valid := s1_req(i).taken
    req.takenCfiOffset.bits  := s1_takenCfiOffset(i)
    req.range                := Fill(FetchBlockInstNum, 1.U(1.W)) >> (~s1_takenCfiOffset(i)).asUInt
    req.size                 := s1_takenCfiOffset(i) +& 1.U
    req.data                 := s1_data(i)
    req.maybeRvcMap          := s1_maybeRvcMap(i)
    req.perf_isCrossLine     := s1_isCrossLine(i)

    req.icacheMeta.exception          := s1_exceptionOut
