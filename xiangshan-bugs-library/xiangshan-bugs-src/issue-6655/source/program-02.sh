git clone --branch kunminghu-v3 --single-branch --depth 1 \
  https://github.com/OpenXiangShan/XiangShan.git xs-v3
cd xs-v3
make init
export NOOP_HOME="$PWD"
make emu CONFIG=DefaultConfig CC=clang CXX=clang++ -j24

cat > repro.S <<'EOF'
    .option norvc
    .section .text.init
    .globl _start
_start:
    # Permit ordinary RAM accesses even if MPRV/MPP differs after reset.
    li t0, -1
    csrw pmpaddr0, t0
    li t0, 0x1f
    csrw pmpcfg0, t0

    li t0, 0x600
    csrs mstatus, t0           # VS=Dirty
    li t1, 4
    vsetvli zero, t1, e32, m1, ta, ma
    vmv.v.i v8, 0

#ifndef BASE_ADDR
#define BASE_ADDR 0x80010000
#endif
    li x6, BASE_ADDR
    li x7, 4
target:
    vsse32.v v8, (x6), x7
    .4byte 0x0000006b       # DiffTest good trap
hang:
    j hang
EOF

cat > link.ld <<'EOF'
OUTPUT_ARCH("riscv")
ENTRY(_start)
SECTIONS {
  . = 0x80000000;
  .text.init : { KEEP(*(.text.init)) }
  . = ALIGN(0x1000); .text : { *(.text .text.*) }
  . = ALIGN(0x1000); .data : { *(.data .data.* .sdata .sdata.*) }
  .bss (NOLOAD) : { *(.bss .bss.* COMMON) }
  /DISCARD/ : { *(.comment) *(.note*) *(.riscv.attributes) }
}
EOF

riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64 \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -Wl,-e,_start -o repro.elf repro.S
riscv64-unknown-elf-objdump -d repro.elf | sed -n '/<target>:/,+3p'
./build/emu -i repro.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so \
  -C 30000 --dump-commit-trace
./build/emu -i repro.elf --no-diff -C 30000
