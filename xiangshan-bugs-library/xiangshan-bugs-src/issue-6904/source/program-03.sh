riscv64-unknown-elf-gcc -march=rv64gcv -mabi=lp64d \
  -nostdlib -nostartfiles -static -fno-pic \
  -Wl,-T,link.ld -o repro.elf repro.S
riscv64-unknown-elf-objdump -d repro.elf
mkdir -p run-diff/build run-nodiff/build
NOOP_HOME="$PWD/run-diff" ./build/emu -i "$PWD/repro.elf" \
  --diff "$PWD/ready-to-run/riscv64-nemu-interpreter-so" \
  -C 30000 --dump-commit-trace
NOOP_HOME="$PWD/run-nodiff" ./build/emu -i "$PWD/repro.elf" \
  --no-diff -C 30000 --dump-commit-trace
