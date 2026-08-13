$INJECT_ADDR:
  # maybe do something else
  ret

test:
  la t0, $BASE_ADDR     # load icache control base addr
  la t1, $INJECT_ADDR   # load inject addr
  jalr ra, 0(t1)        # jump to injected addr to load it i
  sd t1, 8(t0)          # set inject addr
  la t2, (target << 2 | 1 << 1 | 1 << 0)  # load inject target & inject enable & ecc enable
  sd t1, 0(t0)          # set inject enable & ecc enable
loop:
  ld t1, 0(t0)          # get ecc control state
  andi t1, t1, (0b11 << (4+1)) # get high bits of inject state
  beqz t1, loop         # if is idle, or working, loop

  addi t1, t1, -1       # t1 = inject_state[2:1] - 1
  bnez t1, error        # if is not injected, error or rsvd

  jalr ra, 0(t1)        # jump to injected addr to trigger error
  j    finish

error:
  # handle error
finish:
  # finish
