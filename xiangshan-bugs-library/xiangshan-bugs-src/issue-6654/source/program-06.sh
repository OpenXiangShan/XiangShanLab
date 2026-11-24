riscv64-unknown-elf-gcc -march=rv64ia_zicsr -mabi=lp64 -c -o zama16b_witness_witness.o zama16b_witness_witness.S
riscv64-unknown-elf-gcc -march=rv64ia_zicsr -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T zama16b_witness.ld -o zama16b_witness_witness.elf zama16b_witness_witness.o
