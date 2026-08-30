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
