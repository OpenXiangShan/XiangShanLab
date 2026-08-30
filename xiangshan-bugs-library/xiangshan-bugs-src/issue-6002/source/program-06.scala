// src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
def nukePAddrMatch(storePAddr: UInt, storeMatchType: UInt, loadPAddr: UInt): Bool = {
  val storeVWordAddr = storePAddr >> DCacheVWordOffset
  val loadVWordAddr = loadPAddr >> DCacheVWordOffset
  Mux(
    StLdNukeMatchType.isCacheLine(storeMatchType),
    (storePAddr >> blockOffBits) === (loadPAddr >> blockOffBits),
    Mux(
      StLdNukeMatchType.isOctaWord(storeMatchType),
      storeVWordAddr === loadVWordAddr || (storeVWordAddr + 1.U) === loadVWordAddr,
      storeVWordAddr === loadVWordAddr
    )
  )
}
