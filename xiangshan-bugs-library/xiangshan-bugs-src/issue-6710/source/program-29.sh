riscv64-unknown-elf-gcc -march=rv64imafdv_zicsr_zvknha_zvkg -mabi=lp64d -c -o vsha2_minimal_vsha2ch.o vsha2_minimal_vsha2ch.S
riscv64-unknown-elf-gcc -march=rv64imafdv_zicsr_zvknha_zvkg -mabi=lp64d -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T vsha2_minimal.ld -o vsha2_minimal_vsha2ch.elf vsha2_minimal_vsha2ch.o
