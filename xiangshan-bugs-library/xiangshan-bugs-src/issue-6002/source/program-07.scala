// src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
val nukePAddrMatches = nukeQueryReqs.map(req => nukePAddrMatch(req.paddr, req.matchType, paddr))
val nukeStoreOlders = nukeQueryReqs.map(req => isAfter(robIdx, req.robIdx))
val nukeMaskMatches = nukeQueryReqs.map(req => (req.mask & in.mask).orR)
val nuke = Cat((nukeQueryValids lazyZip nukePAddrMatches lazyZip nukeStoreOlders lazyZip nukeMaskMatches).map {
  case (valid, paddrMatch, storeOlder, maskMatch) => valid && paddrMatch && storeOlder && maskMatch
}).orR && paddrEffective
