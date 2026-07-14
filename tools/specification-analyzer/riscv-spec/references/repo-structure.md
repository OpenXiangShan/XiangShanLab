# riscv-unified-db Repository Structure

## Directory Layout

```
riscv-unified-db/
├── spec/std/isa/
│   ├── ext/              # Extension definitions (~165 YAML files)
│   ├── inst/             # Instruction definitions (~60 extension groups)
│   │   ├── I/            # Base integer (add, sub, lw, sw, ...)
│   │   ├── M/            # Multiply/divide
│   │   ├── A/            # Atomics (old, split into Zaamo/Zalrsc)
│   │   ├── F/D/          # Float (single/double)
│   │   ├── V/            # Vector
│   │   ├── C/            # Compressed
│   │   ├── B/            # Bitmanip
│   │   ├── H/            # Hypervisor
│   │   ├── S/            # Supervisor
│   │   ├── Zaamo/Zalrsc/ # Split atomics
│   │   ├── Zba/Zbb/Zbc/Zbs/ # Bitmanip sub-extensions
│   │   ├── Zcb/          # Compressed bitmanip
│   │   └── ...           # Many more
│   ├── csr/              # CSR definitions (~389 YAML files, mixed flat + subdirs)
│   ├── param/            # Architectural parameters (~206 YAML files, hardware config knobs)
│   └── profile/          # Profile source definitions
├── cfgs/
│   └── profile/          # Auto-generated profile configs (machine-friendly)
└── schemas/              # JSON schemas for validation
```

## Common Search Patterns

### Find an extension by keyword
```bash
find spec/std/isa/ext/ -iname '*misalign*'
grep -rl 'misaligned' spec/std/isa/ext/
```

### List all instructions in an extension
```bash
ls spec/std/isa/inst/V/       # all vector instructions
ls spec/std/isa/inst/Zba/     # address generation bitmanip
```

### Find which extension defines an instruction
```bash
grep -rl 'name: vadd' spec/std/isa/inst/
```

### Find a CSR by name or address
```bash
grep -rl 'vlenb' spec/std/isa/csr/
grep -rl '0xC20' spec/std/isa/csr/     # by address
```

### Find all extensions required by another extension
```bash
grep -A10 'requirements:' spec/std/isa/ext/V.yaml
```

### List all ratified extensions
```bash
grep -l 'state: ratified' spec/std/isa/ext/*.yaml | wc -l
```

### Find all instructions with complex definedBy (anyOf/allOf)
```bash
grep -rl 'anyOf\|allOf\|oneOf' spec/std/isa/inst/
```

### Find an architectural parameter
```bash
find spec/std/isa/param/ -iname '*pmp*'
grep -rl 'MISALIGNED' spec/std/isa/param/
```

### List all boolean parameters
```bash
grep -l 'type: boolean' spec/std/isa/param/*.yaml
```

### Find parameters defined by a specific extension
```bash
grep -rl "name: Smpmp" spec/std/isa/param/
```

### Find parameters with inter-parameter constraints
```bash
grep -l 'requirements:' spec/std/isa/param/*.yaml
```

### Check what profiles include an extension
```bash
grep -l 'Zicclsm' cfgs/profile/*.yaml
```

## Upstream Repos for What UDB Doesn't Cover

| Topic | Repo |
|-------|------|
| Profile spec prose | `riscv/riscv-profiles` (src/rva23-profile.adoc) |
| ISA manual prose | `riscv/riscv-isa-manual` |
| Vector intrinsics | `riscv-non-isa/rvv-intrinsic-doc` |
| ELF/ABI | `riscv-non-isa/riscv-elf-psabi-doc` |
| Platform specs | `riscv-non-isa/riscv-platform-specs` |
