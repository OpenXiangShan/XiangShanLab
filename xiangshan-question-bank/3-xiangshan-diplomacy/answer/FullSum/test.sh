#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

# Mill compiles the design and test sources before running the test suite.
exec ./mill --no-server FullSum.test "$@"
