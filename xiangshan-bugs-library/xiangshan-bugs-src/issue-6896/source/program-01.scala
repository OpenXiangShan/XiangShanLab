io.channel_sel := Mux(RegNext(io.req.fire), selectChannel(req.address), 0.U)
