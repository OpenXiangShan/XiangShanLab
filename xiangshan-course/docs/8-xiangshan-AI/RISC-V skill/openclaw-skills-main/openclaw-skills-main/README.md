# OpenClaw Skills

Custom skills for [OpenClaw](https://github.com/openclaw/openclaw) - an AI assistant framework.

## Skills

### riscv-spec
Look up RISC-V ISA specification details using the local [riscv-unified-db](https://github.com/riscv/riscv-unified-db) repository. Covers extensions, instructions, encodings, CSR fields, and profile requirements.

### english-word-card
Generate illustrated English word learning cards for kids, with pronunciation, example sentences, and Chinese explanations.

## Installation

These skills follow the standard [Agent Skills](https://docs.claude.com/en/docs/claude-code/skills) layout and work with any compatible host (Claude Code, OpenClaw, etc.).

Copy a skill directory into your host's skills folder. For Claude Code:

```bash
# User-level (available in all projects)
cp -r riscv-spec ~/.claude/skills/

# Or project-level
cp -r riscv-spec /path/to/project/.claude/skills/
```

Or clone and symlink:

```bash
git clone https://github.com/docularxu/openclaw-skills.git
ln -s "$(pwd)/openclaw-skills/riscv-spec" ~/.claude/skills/riscv-spec
```

For other hosts (e.g. OpenClaw), substitute the host's skills directory (e.g. `~/.openclaw/skills/`).

## License

MIT
