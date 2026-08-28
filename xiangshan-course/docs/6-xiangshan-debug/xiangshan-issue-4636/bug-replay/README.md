# prefetch-replay

This AM program reproduces the pre-PR #4636 XiangShan behavior where a
software `PREFETCH.R` to an Svpbmt `PBMT.NC` page becomes a real uncached
`M_XRD` request.

The measured access is deliberately separated from the DTLB warm-up:

- warm-up load: VA `0x1040`, PA `nc_data_page + 0x40`;
- measured `PREFETCH.R`: VA `0x1180`, PA `nc_data_page + 0x180`.

Build with the Linux cross toolchain available in this environment:

```sh
source ~/prefetch-env/env.sh
make ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=1
```

The instruction at `measured_prefetch_r` is emitted as raw word
`0x02166013` so the test does not depend on assembler mnemonic support.
