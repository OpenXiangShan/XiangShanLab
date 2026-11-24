val is1GpRdAddrReqSrcIdx: Seq[Int] = is1Next.bits.exuParam.readPortCfgs.zipWithIndex collect {
  case (readCfg, srcIdx) if readCfg.exists(_.getDataConfig == IntData()) => srcIdx
}
