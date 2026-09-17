---
name: riscv-spec
description: >
  Look up RISC-V ISA specification details using the local riscv-unified-db (UDB) repository.
  Activate when answering questions about RISC-V extensions, instructions, encodings, assembly
  syntax, CSR fields, architectural parameters (hardware configuration knobs), profile
  requirements (RVA20/22/23, RVB23, etc.), or any ISA-level spec claim. Also use for verifying
  RISC-V spec claims before stating them - including claims in patches, commit messages, or
  upstream discussions about spec compliance. For intrinsics (compiler-level), check the
  rvv-intrinsic-doc repo via GitHub API instead.
---

# RISC-V Spec Lookup

## Rule

Never state RISC-V spec claims without verifying against UDB or upstream spec sources. If UDB doesn't cover it, say so explicitly.

## Local Database

Path: `~/repos/riscv-unified-db/`

If the directory does not exist, **ask the user for confirmation before cloning** (~200MB). Do not clone silently.

### Keeping UDB Fresh

After cloning or fetching, record the date in `<skill_dir>/.last-update` (same directory as this SKILL.md).

On each activation, check the file. If older than 7 days (or missing), run:
```bash
cd ~/repos/riscv-unified-db && git fetch origin && git merge --ff-only origin/main
```
Then update the timestamp.

## Lookup Procedure

### Extensions
```
spec/std/isa/ext/<Name>.yaml
```
Key fields: `name`, `long_name`, `description`, `versions[]` (state, ratification_date), `requirements`

If unsure of exact name: `find spec/std/isa/ext/ -iname '*keyword*'`

### Instructions
```
spec/std/isa/inst/<Ext>/<name>.yaml
```
Key fields: `name`, `long_name`, `description`, `assembly` (syntax), `encoding.match` (bitmask), `encoding.variables[]` (operand bit locations), `access` (s/u/vs/vu permissions), `definedBy`, `operation()`

List an extension's instructions: `ls spec/std/isa/inst/<Ext>/`

### CSRs
```
spec/std/isa/csr/<name>.yaml        # top-level CSRs
spec/std/isa/csr/<Ext>/<name>.yaml  # extension-specific CSRs
```
Key fields: `address`, `priv_mode`, `length`, `fields` (with location, type, reset_value), `definedBy`

Search: `grep -rl '<csr_name>' spec/std/isa/csr/`

### Profiles
```
cfgs/profile/<Profile>.yaml
```
Available: RVA20U64, RVA20S64, RVA22U64, RVA22S64, RVA23U64, RVA23S64, RVA23M64, RVB23U64, RVB23S64, RVB23M64, RVI20U32, RVI20U64, MP-S-64, MP-U-64

Top-level keys: `mandatory_extensions`, `non_mandatory_extensions`, `additional_extensions`

For profile spec prose (rationale, detailed requirements), fetch from `riscv/riscv-profiles` repo via `gh api`.

Use `scripts/profile-query.py` for quick profile queries - see below.

### Architectural Parameters
```
spec/std/isa/param/<NAME>.yaml
```
~206 parameters (excluding test mocks). These are the hardware "configuration knobs" that define a specific RISC-V implementation's design choices.

Key fields: `name`, `long_name`, `description`, `schema` (value type/constraints), `definedBy` (which extension), `requirements` (IDL constraints between parameters)

Value type distribution: boolean (107), string/enum (60), integer (27), array (19), conditional/oneOf (10), integer/enum (4)

Examples:
- `MISALIGNED_LDST` (boolean) - support misaligned loads/stores?
- `MXLEN` (integer/enum: 32|64) - M-mode XLEN
- `NUM_PMP_ENTRIES` (integer: 0-64) - number of PMP entries
- `HW_MSTATUS_FS_DIRTY_UPDATE` (string/enum) - how FP dirty bit updates

Parameters with `requirements` contain IDL code expressing **inter-parameter constraints** (e.g., `MXLEN == 32 -> xlen() == 32`). These are used by UDB's configuration validation to check whether a set of parameter values is legal.

Search: `grep -rl '<term>' spec/std/isa/param/`

### Cross-reference

`grep -r '<term>' spec/std/isa/` to find all mentions across extensions, instructions, and CSRs.

## Gotchas

1. **`definedBy` can be complex** - Some instructions use nested `anyOf`/`allOf`/`oneOf` structures (e.g., `andn` is defined by `anyOf: [Zbb, Zbkb]`). Don't assume a flat `name` field; always check the actual structure.

2. **Profile `additional_extensions` may be boolean** - In some profile YAMLs, `additional_extensions: true` means "any additional extension is allowed," not a list. Check `type()` before iterating.

3. **CSR directory has mixed structure** - Some CSRs are at top level (`csr/cycle.yaml`), others under extension subdirectories (`csr/V/vl.yaml`). Always use `grep -rl` to search.

4. **Profile configs are auto-generated** - Files in `cfgs/profile/` are generated from `spec/std/isa/profile/` source. Both are valid references, but `cfgs/` is more convenient for machine parsing.

5. **Extension requirements can reference params** - Some extensions (e.g., Zicclsm) require specific parameter values (`MISALIGNED_LDST: true`), not just other extensions.

## Scripts

### profile-query.py

Quick profile extension listing:
```bash
python3 <skill_dir>/scripts/profile-query.py <Profile> [--mandatory|--optional|--all]
```

## What UDB Does NOT Cover

| Topic | Where to look |
|-------|---------------|
| Profile spec prose | `riscv/riscv-profiles` via `gh api` |
| ISA manual prose | `riscv/riscv-isa-manual` via `gh api` |
| Vector intrinsics | `riscv-non-isa/rvv-intrinsic-doc` via `gh api` |
| ELF/ABI | `riscv-non-isa/riscv-elf-psabi-doc` via `gh api` |
| Platform specs | `riscv-non-isa/riscv-platform-specs` via `gh api` |

For detailed repo structure and search patterns, see `references/repo-structure.md`.
