// src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
private val dret_EX_II = dret && !debugMode
private val dretIllegal = dret_EX_II

io.out.Xret_EX_II := mnret_EX_II || mret_EX_II || sret_EX_II || dret_EX_II
io.out.hasLegalDret := dret && !dretIllegal
