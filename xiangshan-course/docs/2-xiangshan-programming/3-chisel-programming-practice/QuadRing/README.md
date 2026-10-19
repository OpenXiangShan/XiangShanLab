# QuadRing

Ring interconnect implemented in Chisel and Scala. Built with Mill.

See [Design](docs/design.md) for architecture and transaction behavior.

Supports four independent unidirectional rings with configurable node count,
buffer depth, and payload types. Nodes can inject messages, route them by destination,
and receive them through valid/ready interfaces. Credit and bubble flow control
handle backpressure and reserve space for forwarding.

CHI subset payloads (REQ, RSP, SNP, DAT) connect through Diplomacy. Endpoint node IDs
map to ring positions; DAT carries a complete 512-bit line. RN0 and RN1 accept local
line reads and writes. `ChiSystemParams(coherent = true)` enables RN caches
(I/SC/UC/UD), an HN directory, shared reads, unique ownership transfers, and
clean/dirty replacement. Cache and directory sizes are configurable; the directory
must cover SN memory. The default mode uses non-coherent reads and writes.
HN preserves request IDs and waits for SN write completion. Each endpoint handles
one active transaction. RetryAck and PCrdGrant support retries; snoops can cancel
a pending dirty writeback. SN provides initialized storage for complete lines.

Each channel uses this topology in the four-node configuration:

```text
    +----------------+                +----------------+
    |   Endpoint 0   |                |   Endpoint 1   |
    +----------------+                +----------------+
      inject | ^ eject                  inject | ^ eject
             v |                               v |
    +----------------+                +----------------+
    |   RingStop 0   |--------------->|   RingStop 1   |
    +--------^-------+                +--------|-------+
             |                                 |
             |             RING                |
             |                                 v
    +--------|-------+                +----------------+
    |   RingStop 3   |<---------------|   RingStop 2   |
    +----------------+                +----------------+
             ^ |                               ^ |
      inject | v eject                  inject | v eject
    +----------------+                +----------------+
    |   Endpoint 3   |                |   Endpoint 2   |
    +----------------+                +----------------+
```

Randomized ring, non-coherent, and coherent tests each cover five seeds and at least
500,000 cycles in long mode. System tests compare reads and writes with an independent
reference model and check backpressure, transaction IDs, loss, duplication, and completion.
Coherent tests also check cache/victim ownership, retry execution, and credit/reservation
balance. CHI transport tests cover node ID mapping and all source/destination pairs.

Requires JDK 21, Verilator, a C++ compiler, and Make.
Dependency sources are included in `third_party/`.

```bash
./test.sh
```

Compiles sources, runs tests, and emits SystemVerilog to `generated/` with Verilator lint checks.
Random tests run 5,000 cycles per seed; use `./test.sh --long` for 100,000.
CI runs the same script in long mode.
Emits a 32-bit single ring to `generated/ring/` and the four-channel CHI transport
to `generated/chi/`. Endpoint systems are emitted to `generated/system/`
(non-coherent) and `generated/system-coherent/` (coherent).
Each output has its own `filelist.f`, with four nodes and three buffer entries per node.
