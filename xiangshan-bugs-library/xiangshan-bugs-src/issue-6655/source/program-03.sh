# The originally found misaligned base, whose support is platform-dependent:
riscv64-unknown-elf-gcc -DBASE_ADDR=0x80010002 -march=rv64gcv -mabi=lp64 \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -Wl,-e,_start -o repro-misaligned.elf repro.S
./build/emu -i repro-misaligned.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 30000

# Aligned unit-stride control, differing only at the target instruction:
sed 's/vsse32.v v8, (x6), x7/vse32.v v8, (x6)/' repro.S > repro-control.S
riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64 \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -Wl,-e,_start -o repro-control.elf repro-control.S
./build/emu -i repro-control.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so -C 30000
