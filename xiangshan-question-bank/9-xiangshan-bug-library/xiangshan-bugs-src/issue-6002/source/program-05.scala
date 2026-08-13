// src/main/scala/xiangshan/mem/pipeline/NewStoreUnit.scala
val nukeQueryReq = Wire(new StoreNukeQueryReq)
nukeQueryReq.robIdx := robIdx
nukeQueryReq.paddr := paddr
nukeQueryReq.mask := mask
nukeQueryReq.matchType := Mux(
  isCbo,
  StLdNukeMatchType.CacheLine,
  Mux(
    cross16Byte && !cross4KPage,
    StLdNukeMatchType.OctaWord,
    StLdNukeMatchType.Normal
  )
)
