    val hitVecReg = RegEnable(hitVec, req.fire)
    // Sector tlb may trigger multi-hit, see def "wbhit"
    XSPerfAccumulate(s"port${i}_multi_hit", !(!resp.valid || (PopCount(hitVecReg) === 0.U || PopCount(hitVecReg) === 1.U)))
    ...
        resp.bits.ppn(d) := Mux1H(hitVecReg zip entries.map(_.genPPN(saveLevel, resp.valid)(reqVpn)))
        resp.bits.pbmt(d) := Mux1H(hitVecReg zip pbmt)
        resp.bits.g_pbmt(d) := Mux1H(hitVecReg zip gpbmt)
        resp.bits.perm(d) := Mux1H(hitVecReg zip perm)
        resp.bits.g_perm(d) := Mux1H(hitVecReg zip gPerm)
        resp.bits.s2xlate(d) := Mux1H(hitVecReg zip s2xLate)
