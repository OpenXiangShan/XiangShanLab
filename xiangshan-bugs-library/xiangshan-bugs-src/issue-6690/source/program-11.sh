./build/emu -i sq_alias_minimal.elf --no-diff --seed 1 --max-cycles 400000 --ram-size 256MB
./build/emu -i sq_alias_minimal.elf --diff ./ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 400000 --ram-size 256MB
