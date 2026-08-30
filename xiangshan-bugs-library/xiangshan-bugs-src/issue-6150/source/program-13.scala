// src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
val isXRet = valid && func === CSROpType.jmp && !isEcall && !isEbreak
val isXRetReg = RegEnable(isXRet, false.B, io.in.fire)

io.out.bits.res.redirect.get.valid := io.out.valid && isXRetReg
redirect.fullTarget := csrMod.io.xretTargetPc.bits.pc
redirect.target     := csrMod.io.xretTargetPc.bits.pc
redirect.backendIPF := csrMod.io.xretTargetPc.bits.raiseIPF
redirect.backendIAF := csrMod.io.xretTargetPc.bits.raiseIAF
redirect.backendIGPF := csrMod.io.xretTargetPc.bits.raiseIGPF
