Currently, `DelayNWithValid` generates code like:

``` verilog
module DelayNWithValid(
  input         clock,
  input  [47:0] io_in_bits,
  input         io_in_valid,
  output [47:0] io_out_bits
);

  reg        valid_REG, valid_REG_1, valid_REG_2, valid_REG_3;
  reg [47:0] data, data_1, data_2, data_3, res_bits;
  always @(posedge clock) begin
    valid_REG <= io_in_valid;
    if (io_in_valid)
      data <= io_in_bits;
    valid_REG_1 <= valid_REG;
    if (valid_REG)
      data_1 <= data;
    valid_REG_2 <= valid_REG_1;
    if (valid_REG_1)
      data_2 <= data_1;
    valid_REG_3 <= valid_REG_2;
    if (valid_REG_2)
      data_3 <= data_2;
    if (valid_REG_3)
      res_bits <= data_3;
  end // always @(posedge)
  assign io_out_bits = res_bits;
endmodule
```

If we pass `reset` as `io_in_valid`, it is actually:

``` verilog
// ...
  always @(posedge clock)
    if (reset)
      data <= io_in_bits;
// ...
```

This is synchronous reset which violates our design rule.

Actually, we do not need `DelayNWithValid` or `RegNext` here because `io.reset_vector` is connected to Vcc or Vdd and will never change. We can set multi-cycle-path to avoid false timing violation.
