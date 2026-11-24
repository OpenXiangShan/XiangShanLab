./build/emu -i vsha2_minimal_vsha2ch.elf --no-diff --seed 1 --max-cycles 30000 --ram-size 256MB
./build/emu -i vsha2_minimal_vsha2ch.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 30000 --ram-size 256MB
