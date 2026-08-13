Previously, if we call `Mem` in XS, it will generate combmem RTL with x assignment if ren is low:

``` verilog
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
```

This is not expected because const-x should be avoided in synthesis. In this commit, we add `--ignore-read-enable-mem` option to disable x assignment if we generate release code.
