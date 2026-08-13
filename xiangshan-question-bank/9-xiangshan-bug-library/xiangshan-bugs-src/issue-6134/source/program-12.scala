// src/main/scala/xiangshan/frontend/ifu/IfuUncacheUnit.scala
private val reqIsMmio = io.req.valid && io.req.bits.isMmio

uncacheState := Mux(reqIsMmio, UncacheFsmState.WaitLastCommit, UncacheFsmState.SendReq)
itlbPbmt     := io.req.bits.pbmt
