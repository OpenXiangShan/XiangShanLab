// SimpleDecodeChannel.scala:149
out.uop(i).bits.renameInfo.gpWen := uopInfos(i).bits.gpWen && instFields.RD =/= 0.U
// VsetDecoder.scala:116
out.bits.renameInfo.bits.gpWen := uop.gpWen && instFields.RD =/= 0.U
// VectorDecodeChannel.scala:248
out.uop(i).bits.renameInfo.gpWen := uopInfos(i).bits.gpWen && srcSelectModule.out.src(i).dest =/= 0.U
