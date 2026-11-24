riscv64-unknown-elf-gcc -march=rv64im_zicsr -mabi=lp64 -c -o sq_alias_minimal.o sq_alias_minimal.S
riscv64-unknown-elf-gcc -march=rv64im_zicsr -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T sq_alias_minimal.ld -o sq_alias_minimal.elf sq_alias_minimal.o
