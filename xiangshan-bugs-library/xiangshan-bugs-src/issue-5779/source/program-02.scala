// src/main/scala/xiangshan/backend/fu/Fence.scala:74
sfence.bits.id := RegEnable(io.in.bits.data.src(1), io.in.fire)
// src(1) is 64-bit → Chisel truncates to 16 bits → id = rs2[15:0]
