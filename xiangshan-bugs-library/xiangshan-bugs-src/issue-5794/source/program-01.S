.section .text.init
.globl _start
_start:
  la t0, trap_handler
  csrw mtvec, t0

  csrr t0, mstatus
  li t1, 0x00003600     
  or t0, t0, t1
  csrw mstatus, t0
  csrw fcsr, x0

  j user_code

user_code:
  li x9, 0x8fffffb8

  li x10, 0xfff54323999c313b
  sd x10, 0(x9)

  li x10, 0x157731e1e526f5a0
  sd x10, 40(x9)

exit:
  li t0, 1
  la t1, skiptrap_store_buf
  sd t0, 0(t1)

  # DiffTest STATE_GOODTRAP
  li t0, 0
  .insn i 0x6b, 0, x0, t0, 0
  ebreak

  .align 2
trap_handler:
  csrr t0, mepc
  csrr t1, mcause
  csrr t4, mtval

  slli t5, t1, 1
  srli t1, t5, 1

  li t2, 2
  li t3, 2
  beq t1, t3, use_mtval
  li t3, 1
  beq t1, t3, fetch_inst
  li t3, 12
  beq t1, t3, fetch_inst

fetch_inst:
  lhu t4, 0(t0)
  j decode_length

use_mtval:
  j decode_length

decode_length:
  andi t4, t4, 3
  li t3, 3
  bne t4, t3, compressed_len
  li t2, 4
  j update_mepc

compressed_len:
  li t2, 2

update_mepc:
  add t0, t0, t2
  csrw mepc, t0
  csrw mcause, x0
  csrw mtval, x0
  mret

.section .data
.align 3
skiptrap_store_buf:
  .dword 0

.align 6
fuzz_mem_pool:
  .space 4096
