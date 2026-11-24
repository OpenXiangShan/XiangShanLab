riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -c -o mtime2_U_uartlite.o mtime2_U_uartlite.S
riscv64-unknown-elf-gcc -march=rv64imafdcvh_zicsr_zifencei_zicbom_zicbop_zicboz -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T mtime2.ld -o mtime2_U_uartlite.elf mtime2_U_uartlite.o
