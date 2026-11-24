riscv64-unknown-elf-gcc -march=rv64gcv_zicsr_zifencei -mabi=lp64d \
  -nostdlib -nostartfiles -static -Wl,--build-id=none \
  -T integrated_frontend.ld -o return_target_mismatch.elf return_target_mismatch.S
make emu CONFIG=MinimalConfig NUM_CORES=1
./build/emu -i return_target_mismatch.elf \
  --diff ready-to-run/riscv64-nemu-interpreter-so \
  --seed 1 --max-cycles 2000000 --ram-size 256MB > emu.log 2>&1
