// src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:371-373
    this.n := inner_n
    val isSuperPage = inner_level =/= 0.U || inner_n =/= 0.U
    this.valididx := Mux(isSuperPage, VecInit(Seq.fill(tlbcontiguous)(true.B)),
// src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:227
    tag_matchs(0) := Mux(n === 0.U, tag(vpnnLen - sectortlbwidth - 1, 0) === vpn(vpnnLen - 1, sectortlbwidth), tag(vpnnLen - sectortlbwidth - 1, pteNapotBits - sectortlbwidth) === vpn(vpnnLen - 1, pteNapotBits))
// src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:397-399
      Mux(inner_level >= "b01".U , vpn(vpnnLen - 1, 0),
        // inner_level == "b00".U (4KB), need to check whether n is 0
        Mux(n === 0.U, Cat(ppn(vpnnLen - sectortlbwidth - 1, 0), ppn_low(vpn(sectortlbwidth - 1, 0))), Cat(ppn(vpnnLen - sectortlbwidth - 1, pteNapotBits - sectortlbwidth), vpn(pteNapotBits - 1, 0)))))
