module data_mem_0_4x2(
  input  [1:0] R0_addr,
  input        R0_en,
  input        R0_clk,
  output [1:0] R0_data,
  input  [1:0] W0_addr,
  input        W0_en,
  input        W0_clk,
  input  [1:0] W0_data
);

  reg [1:0] Memory[0:3];
  always @(posedge W0_clk) begin
    if (W0_en & 1'h1)
      Memory[W0_addr] <= W0_data;
  end // always @(posedge)
  assign R0_data = R0_en ? Memory[R0_addr] : 2'bx;
endmodule
