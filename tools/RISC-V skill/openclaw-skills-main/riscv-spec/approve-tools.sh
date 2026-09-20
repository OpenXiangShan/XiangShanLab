#!/bin/bash
# Pre-approve riscv-spec skill's UDB file access for Claude Code.
# Run this once after installing the skill in a Claude Code project.
#
# Usage: cd /path/to/your/project && bash /path/to/riscv-spec/approve-tools.sh

UDB_PATH="${UDB_PATH:-$HOME/repos/riscv-unified-db}"

if [ ! -d "$UDB_PATH" ]; then
    echo "Error: UDB not found at $UDB_PATH"
    echo "Set UDB_PATH to override, or clone: git clone https://github.com/riscv/riscv-unified-db.git"
    exit 1
fi

# Get current allowed permissions and append new ones
CURRENT=$(claude config get -p project permissions.allow 2>/dev/null || echo "[]")

# Build new permissions array
NEW_PERMS=$(cat <<EOF
[
  "Bash(find ${UDB_PATH}/**)",
  "Bash(cat ${UDB_PATH}/**)",
  "Bash(head ${UDB_PATH}/**)",
  "Bash(grep -r ${UDB_PATH}/**)",
  "Bash(ls ${UDB_PATH}/**)",
  "Bash(python3 ${UDB_PATH}/**)"
]
EOF
)

echo "Approving UDB file access for Claude Code..."
claude config set -p project permissions.allow "$NEW_PERMS"

echo "Done. Claude Code can now read UDB files without prompting."
