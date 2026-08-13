This commit implements a basic nop-based Zawrs extension.

- `wrs.sto` in this commit acts as a nop instruction.
- `wrs.nto` in this commit acts as a nop instruction, except it:
  - raises illegal instruction exception when !isModeM && mstatus.TW=1, or
  - raises virtual instruction exception when privState.V && mstatus.TW=0 && hstatus.VTW=1

Seems that completely raises no exception is also a valid implementation,
but raises an exception can help OS to do scheduling during waiting.
