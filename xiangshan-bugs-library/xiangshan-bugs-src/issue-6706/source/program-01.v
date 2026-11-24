output [63:0] auto_in_r_bits_data,          // 64-bit read data
...
wire [63:0] nodeIn_r_bits_data =
  {32'h0,                                   // upper 32 bits hardwired to 0
   (_nodeIn_r_bits_user_T ? nodeIn_ar_bits_addr[0] : raddr_hold_data[0])
     ? intrGenRegs_1
     : intrGenRegs_0};                      // index = byte-address bit 0 only
...
assign auto_in_r_bits_data = nodeIn_r_bits_data;
