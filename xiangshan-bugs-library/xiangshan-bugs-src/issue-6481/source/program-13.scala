// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/ftq/Ftq.scala#L503-L505
  commitQueue.io.backendCommit := io.fromBackend.callRetCommit

  io.toBpu.commit.valid                     := commitQueue.io.bpuTrain.valid
