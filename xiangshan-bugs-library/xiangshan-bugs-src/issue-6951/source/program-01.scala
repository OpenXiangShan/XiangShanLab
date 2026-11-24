val inflightCnt =
  PopCount(Cat(pipeStatus_s4_s5.map(/* ... */))) +
  PopCount(Cat(pipeStatus_s2_s3.map(/* ... */))) +
  queueCnt
