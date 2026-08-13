// src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
private val isRVC = io.in.bits.ctrl.isRVC.get

val redirect = io.out.bits.res.redirect.get.bits
val redirectValid = io.out.bits.res.redirect.get.valid
redirectValid := io.in.valid && !jumpDataModule.io.isAuipc && (needRedirect || redirect.hasBackendFault)
redirect := 0.U.asTypeOf(redirect)
redirect.ftqOffset := io.in.bits.ctrl.ftqOffset.get
redirect.target := jumpDataModule.io.target
redirect.pc := io.in.bits.data.pc.get
redirect.isMisPred := needRedirect
