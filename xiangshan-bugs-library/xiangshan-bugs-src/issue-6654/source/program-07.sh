./build/emu -i zama16b_witness_witness.elf --no-diff --seed 1 --max-cycles 2000000 --ram-size 256MB
./build/emu -i zama16b_witness_witness.elf --no-diff --seed 2 --max-cycles 2000000 --ram-size 256MB
./build/emu -i zama16b_witness_witness.elf --diff ready-to-run/riscv64-nemu-interpreter-so --seed 1 --max-cycles 2000000 --ram-size 256MB
