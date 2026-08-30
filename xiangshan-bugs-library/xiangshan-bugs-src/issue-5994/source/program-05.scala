redirect.bits := 0.U.asTypeOf(io.out.bits.res.redirect.get.bits)
redirect.bits.ftqOffset := io.in.bits.ctrl.ftqOffset.get
redirect.bits.target := addModule.io.target
redirect.bits.pc := io.in.bits.data.pc.get
redirect.bits.isMisPred := isMisPred
redirect.bits.taken := dataModule.io.taken
