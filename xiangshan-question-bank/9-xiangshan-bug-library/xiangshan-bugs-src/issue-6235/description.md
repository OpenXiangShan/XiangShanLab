We have removed ifuWbPtr in v3 Ftq, so here backendExceptionPtr is always 0, and no exception is sent to backend while accessing vaddr that violates Sv39/48

This commit sets it to redirect.newFtqIdx (i.e. the value to be written into bpuPtr/pfPtr/fetchPtr in the next cycle), and clears backendException after 3 more fetch blocks are sent to Ifu (see comments there)

Checked waveform:
<img width="1738" height="1175" alt="屏幕截图 2026-07-13 175238" src="https://github.com/user-attachments/assets/20238507-2f0f-4e1e-8f3a-b404f5b5ca6a" />

Fixes #6210

Also remove unused `ifuWbPtr`
