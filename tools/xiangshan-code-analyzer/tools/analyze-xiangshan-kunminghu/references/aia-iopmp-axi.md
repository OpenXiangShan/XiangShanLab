# chiselAIA, chiselIOPMP, and AXI Bus Analysis

Use this file when analyzing chiselAIA, ChiselAIA, APLIC, IMSIC, interrupt delivery, chiselIOPMP, ChiselIOPMP, IOPMP permission checks, MMIO protection, or AXI/AXI4 bus master/slave interfaces.

## Source Roots and Search Terms

Search the selected XiangShan checkout and submodules for:

- AIA: `chiselAIA`, `ChiselAIA`, `APLIC`, `IMSIC`, `AXIRegIMSIC`, `IMSICBusType`, `aia`, `mstateen`, `sireg`, `siselect`, `external interrupt`, `msi`.
- IOPMP: `chiselIOPMP`, `ChiselIOPMP`, `IOPMP`, `iopmp`, `PMP`, `permission`, `deny`, `accessFault`, `APB`, `bypass`.
- AXI: `AXI4`, `AXI4MasterNode`, `AXI4SlaveNode`, `AXI4Bundle`, `AXI4Xbar`, `AXI4Buffer`, `AXI4Fragmenter`, `AXI4ToTL`, `TLToAXI4`, `aw`, `w`, `b`, `ar`, `r`, `ready`, `valid`, `last`, `resp`, `id`, `len`, `size`, `burst`, `strb`.

Likely repositories/modules include OpenXiangShan `XiangShan` submodules and standalone `OpenXiangShan/ChiselAIA` and `OpenXiangShan/ChiselIOPMP`. Verify the actual path and analyzed commit before explaining behavior.

## chiselAIA Required Coverage

For AIA/APLIC/IMSIC analysis, cover:

| Topic | Required analysis |
| --- | --- |
| Module boundary | Where APLIC/IMSIC wrappers are instantiated, their bus type, address map, hart mapping, and top-level interrupt outputs |
| Interrupt sources | External interrupt inputs, MSI writes, local interrupt paths, and software-visible interrupt injection |
| APLIC behavior | Source pending/enable/priority/delegation/domain logic, gateway behavior, target mapping, claim/complete path when present |
| IMSIC behavior | MSI write path, per-hart interrupt file, pending/enable bits, priority/selection, threshold/claim behavior, backpressure/FIFO behavior |
| CSR interaction | `mip/mie/sip/sie`, `mstateen/sstateen/hstateen`, `siselect/sireg/vsiselect/vsireg`, privilege/virtualization legality, and trap priority |
| Bus access | AXI/TL/APB register access path, read/write decode, response generation, error response, and backpressure |
| Protocol/FSM | Register access FSMs, interrupt arbitration states, pending-to-delivery lifecycle, claim/complete lifecycle |

For every AIA control signal or state, explain why it exists and give a scenario such as MSI arrival, APLIC external interrupt, disabled source, priority conflict, IMSIC FIFO full, M-mode injected SEI, VS-mode illegal access, or redirect/trap delivery.

## chiselIOPMP Required Coverage

For IOPMP analysis, cover:

| Topic | Required analysis |
| --- | --- |
| Module boundary | Where IOPMP is inserted in the MMIO/AXI/TL/APB path and what masters/slaves it protects |
| Configuration path | APB/AXI/TL config port, register map, entry programming, lock bits, reset defaults |
| Permission inputs | Address, size, read/write/execute type, source/master ID, privilege/domain/security metadata when present |
| Match algorithm | Entry priority, address range/NAPOT/TOR mode if present, first-match/no-match behavior, default policy |
| Decision output | Allow/deny, error/response mapping, accessFault/PMP/PMA interaction, side effects on denied writes |
| Datapath | Request pass-through, bypass mode, response path, outstanding transaction tracking, and ordering |
| FSM/backpressure | Config access states, protected transaction states, deny response states, ready/valid backpressure |

For every IOPMP control signal or state, explain why it exists and give a scenario such as DMA access allowed, unauthorized MMIO write denied, config port update, locked entry, bypass path, read response after permission check, or backpressure from downstream slave.

## AXI Master/Slave Required Coverage

Always identify each AXI endpoint role:

| Role | Drives | Receives/consumes | Typical responsibility |
| --- | --- | --- | --- |
| AXI master | `AW`, `W`, `AR` payload and valid; `B.ready`, `R.ready` | `AW.ready`, `W.ready`, `AR.ready`, `B`, `R` | Initiates reads/writes, assigns IDs, tracks outstanding transactions, emits burst/write data |
| AXI slave | `AW.ready`, `W.ready`, `AR.ready`, `B`, `R` payload and valid | `AW`, `W`, `AR`, `B.ready`, `R.ready` | Accepts address/data, performs memory/register action, returns read data or write response |
| Interconnect/xbar/buffer | Both master-facing slave and slave-facing master roles | Both directions | Arbitrates, routes by address/ID, buffers, width-converts, fragments, or bridges protocols |

Analyze all five AXI channels separately:

| Channel | Direction | Key payload/control | Required behavior |
| --- | --- | --- | --- |
| `AW` write address | master to slave | `valid`, `ready`, `addr`, `id`, `len`, `size`, `burst`, `lock`, `cache`, `prot`, `qos`, `user` | Address accept timing, burst parameters, ID/outstanding allocation, address decode/routing |
| `W` write data | master to slave | `valid`, `ready`, `data`, `strb`, `last`, `user` | Data beat sequencing, byte mask, `last`, pairing with AW, write buffering/backpressure |
| `B` write response | slave to master | `valid`, `ready`, `id`, `resp`, `user` | Response after accepted write beats, error mapping, ID return, outstanding release |
| `AR` read address | master to slave | `valid`, `ready`, `addr`, `id`, `len`, `size`, `burst`, `lock`, `cache`, `prot`, `qos`, `user` | Read request accept timing, burst parameters, ID/outstanding allocation, address decode/routing |
| `R` read data | slave to master | `valid`, `ready`, `id`, `data`, `resp`, `last`, `user` | Data beat sequencing, `last`, error response, ID return, outstanding release |

For every AXI channel/control signal, explain:

- Producer and consumer, including master/slave direction.
- Exact Chisel source lines and short core code snippet from the analyzed commit.
- `valid && ready` fire condition and payload stability during stalls.
- Why the signal exists: address phase, data phase, response phase, burst tracking, byte mask, ID ordering, error reporting, protection/cache attributes, or backpressure.
- Scenario example: write burst with AW accepted before W, W stalls before downstream ready, B response releases write tracker, read burst returns multiple R beats, permission denied maps to error `resp`, ID routes response back to the correct master, or xbar arbitrates two masters.

## AXI Protocol Algorithms and Corner Cases

Mandatory algorithms to inspect when present:

- Address decode and route selection from `addr` to slave port.
- Master ID/source ID allocation, widening, shrinking, or routing through xbar/bridge.
- Outstanding read/write tracking and release on `B.fire` or `R.fire && last`.
- AW/W pairing when address and data arrive independently.
- Burst address progression from `addr`, `len`, `size`, `burst`; include `INCR`, `FIXED`, and `WRAP` if supported.
- Byte write mask from `strb` and data width/beat alignment.
- Response mapping: `OKAY`, `SLVERR`, `DECERR`, denied/protection error, downstream error.
- Backpressure propagation across buffers, queues, xbars, bridges, and slaves.
- Read/write ordering rules for same ID and any code assumptions or assertions.

## Output Requirements

Add a section titled `chiselAIA / chiselIOPMP / AXI Bus` when relevant.

Include:

| Item | Commit | Source lines | Core Chisel code | Master/slave role | Protocol/control behavior | Why it exists | Scenario |
| --- | --- | --- | --- | --- | --- | --- | --- |

Also include AXI master/slave diagrams and waveform-draw timing diagrams for AW/W/B and AR/R handshakes when an AXI path is analyzed.
