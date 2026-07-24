# mmu-smmpt

This directory now contains a standalone SMMPT wrapper module that can compile
without the parent XiangShan workspace.

## Standalone module

- `smmpt-module/`: self-contained Chisel module for the `HasMptCheck` related
  SMMPT boundary logic.
- `mmu-smmpt-rtl/smmpt-module.sv`: generated RTL for the standalone module.
- `build.mill`: local Mill build; it does not call `../build.mill` or
  `xiangshan.runMain`.

The standalone module packages the MPT-facing pieces needed outside XiangShan:

- `mmpt` CSR fields: `mode`, `optOutInNode`, `sdid`, `ppn`, `changed`
- `mfence`/flush visibility
- MPT request/response bundles
- `mptEn = mmpt.mode =/= 0.U`
- `mpt_af = !resp.bits.mptPerm(0) || resp.bits.accessFault`, latched on response
- `accessFaultMpt = upstreamAccessFault || mpt_af`
- MPT access-fault blocking of subsequent memory requests

It intentionally does not depend on `ChiselAIA`, `ChiselIOPMP`, full XiangShan
frontend/backend, or the parent repository.

## Build

Compile the standalone module:

```sh
mill smmptModule.compile
```

Generate RTL:

```sh
make rtl
```

or directly:

```sh
mill smmpt.rtl
```

The generated SystemVerilog is copied to:

```text
mmu-smmpt-rtl/smmpt-module.sv
```

## Legacy extraction

`mmu-smmpt-module/` is kept as the earlier XiangShan-oriented extraction for
reference. The standalone build target above does not compile or depend on it.
