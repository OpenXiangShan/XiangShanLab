.option rvc

_start:
  auipc t0, 0
  addi  t0, t0, 16

victim_cjr:
  c.jr  t0
  c.nop
  c.nop
  c.nop
  c.nop

target:
  li t0, 0x60000000
  li t1, 1
  sd t1, 0(t0)
done:
  c.j done
