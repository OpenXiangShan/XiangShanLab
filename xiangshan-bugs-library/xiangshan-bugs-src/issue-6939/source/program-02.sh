curl -fsSL -o sail.tgz \
  https://github.com/rems-project/sail/releases/latest/download/sail-Linux-x86_64.tar.gz
tar xf sail.tgz
export PATH="$PWD/sail/bin:$PATH"

git clone https://github.com/riscv/sail-riscv.git
cd sail-riscv
./build_simulator.sh
# output: build/c_emulator/sail_riscv_sim
