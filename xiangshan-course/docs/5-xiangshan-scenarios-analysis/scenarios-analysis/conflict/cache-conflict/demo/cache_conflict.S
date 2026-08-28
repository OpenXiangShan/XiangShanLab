  .section .text.cache_conflict, "ax", @progbits
  .option push
  .option norvc

  .balign 64
  .globl cache_conflict_warmup
  .type cache_conflict_warmup, @function
cache_conflict_warmup:
  ld t0, 0(a0)
  fence rw, rw
  ld t1, 64(a0)
  fence rw, rw
  ld t2, 128(a0)
  fence rw, rw
  add t0, t0, t1
  add a0, t0, t2
  ret
  .size cache_conflict_warmup, .-cache_conflict_warmup

  .balign 64
  .globl cache_conflict_trigger
  .globl cache_conflict_load0
  .globl cache_conflict_load1
  .globl cache_conflict_load2
  .type cache_conflict_trigger, @function
cache_conflict_trigger:
cache_conflict_load0:
  ld t0, 0(a0)
cache_conflict_load1:
  ld t1, 64(a0)
cache_conflict_load2:
  ld t2, 128(a0)
  add t0, t0, t1
  add a0, t0, t2
  ret
  .size cache_conflict_trigger, .-cache_conflict_trigger

  .option pop
