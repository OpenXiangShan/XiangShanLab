#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

# Mill compiles the tutorial and test sources before running the suite.
./mill --no-server Tutorial.test "$@"
./mill --no-server Tutorial.runMain chapter_13.BasicAdderMain
./mill --no-server Tutorial.runMain chapter_13.MultiAdderMain
exec ./mill --no-server Tutorial.runMain chapter_13.NegotiatedAdderMain
