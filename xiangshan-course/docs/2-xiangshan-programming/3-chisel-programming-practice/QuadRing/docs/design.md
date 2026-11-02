# QuadRing design

QuadRing implements a four-endpoint ring with a CHI protocol subset. Two RNs issue
whole-line reads and writes through one HN to one SN. The default system is
non-coherent; `ChiSystemParams(coherent = true)` enables RN caches and the HN directory.
Each RN accepts one local command at a time. HN has one active transaction, and SN
executes one memory operation at a time.

## Network and endpoint boundaries

Each of REQ, RSP, SNP, and DAT has its own physical ring. All four use this order:

```text
       RN0                              HN
        |                               |
   +----------+                    +----------+
   |  Stop 0  |------------------->|  Stop 1  |
   +----^-----+                    +-----|----+
        |                               |
        |                               v
   +----|-----+                    +----------+
   |  Stop 3  |<-------------------|  Stop 2  |
   +----------+                    +----------+
        |                               |
        SN                              RN1
```

Vertical connections carry both TX and RX through `ChiPort`. Arrows show ring
direction. Physical positions follow connection order in
[ChiSystem.scala](../src/main/scala/chi/ChiSystem.scala). Default node IDs equal these
positions, but sparse or permuted IDs are supported.

| Layer | Responsibility | Entry file |
|---|---|---|
| `RingStop` / `Ring` / `QuadRing` | Buffer, route by physical `dst`, and control injection | [RingStop.scala](../src/main/scala/noc/RingStop.scala) |
| `ChiNoC` | Map CHI node IDs to ring positions and carry typed payloads | [ChiNoC.scala](../src/main/scala/chi/ChiNoC.scala) |
| RN | Select transactions, manage cache and snoop responses, return local results | [RnNode.scala](../src/main/scala/chi/RnNode.scala) |
| HN | Serialize transactions, track holders, issue snoops and SN operations | [HnNode.scala](../src/main/scala/chi/HnNode.scala) |
| SN | Store initialized lines and perform reads/writes | [SnNode.scala](../src/main/scala/chi/SnNode.scala) |

Endpoints are Diplomacy `SourceNode`s; the network is a `SinkNode`. In
`network.node := rn0.node`, source/sink describe parameter flow and graph edges.
Each negotiated bundle carries traffic in both directions. The endpoint supplies
its node ID and role; the network supplies flit widths and ring parameters through
[ChiNodeImp](../src/main/scala/chi/ChiNode.scala). Transport does not decode opcodes.

Defaults are four stops, three buffer entries per stop/channel, 2-bit node IDs,
4-bit transaction/DB IDs, and 48-bit byte addresses. The coherent configuration
defaults to eight direct-mapped cache lines per RN and 64 directory entries.
Cache and directory sizes must be powers of two greater than one; the directory
must cover SN memory. The generic ring supports other node counts, while
`ChiSystem` requires four endpoints.

| Design choice | Reason |
|---|---|
| Four physical rings | Keep response, snoop and data paths available when REQ is blocked |
| One complete line per DAT flit | Keep each data transfer and its buffering self-contained |
| One HN transaction at a time | Keep directory changes and ownership transfers serialized |
| Directory covers SN memory | Avoid directory eviction and its additional transactions |
| SC writes acquire through ReadUnique | Use the same ownership-acquisition path as a write miss |
| One P-Credit type | Reserve the single active endpoint resource with one credit class |

## RingStop arbitration and flow control

Only traffic received from the ring enters the FIFO. Local injection waits at the
output arbiter:

```text
             +-------------------------+
 ringIn ---->| FIFO: 3 entries default  |----> head
             +-------------------------+       |
                                  +------------+------------+
                                  |                         |
                              dst = local               dst != local
                                  |                         |
                                  v                         v
                             local.eject            +---------------+
                                                    | output arbiter|----> ringOut
 local.inject ------------------------------------->| forward first |
                                                    +---------------+
```

The FIFO head either ejects locally or competes for ring output. A buffered forward
wins over injection. Local ejection and injection may happen together. A blocked
local head holds the FIFO; traffic behind it cannot bypass.

Each sender counts free entries in the next stop's FIFO. A ring output transfer
consumes a credit; the next FIFO's dequeue returns one. Returned credits become
usable next cycle. With the current direct wiring:

```text
downstreamCredits + downstream FIFO occupancy = bufferDepth
forward allowed: downstreamCredits >= 1
inject allowed:  downstreamCredits >= 2
```

Injection therefore leaves an empty entry downstream. Forwarding moves empty
space around the ring rather than consuming the last global bubble; ejection adds
space. This prevents an accepted set of flits from filling the ring completely.
Ring reset empties every FIFO and restores all credits.

Endpoint messages remain stable while stalled. The RingStop output uses plain
Decoupled semantics: a newly available forward can replace a stalled injection at
the arbiter before either transfers. Only `valid && ready` transfers a flit.

## Commands and protocol payloads

[RnPort.scala](../src/main/scala/chi/RnPort.scala) exposes local commands
`{write, addr, data, txnID}` and results `{txnID, data}`. Reads return a complete
512-bit line; writes replace a complete line and return zero. Addresses must be
64-byte aligned. The RN chooses CHI opcodes from the command and cache state.

[ChiFlit.scala](../src/main/scala/chi/ChiFlit.scala) defines these internal payloads:

| Channel | Fields | Default bits |
|---|---|---:|
| REQ | tgtID, srcID, txnID, opcode, addr, allowRetry, pCrdType | 67 |
| RSP | tgtID, srcID, txnID, opcode, resp, dbID, pCrdType | 23 |
| SNP | srcID, txnID, opcode, addr | 59 |
| DAT | tgtID, srcID, txnID, opcode, resp, dbID, data | 530 |

Opcode widths are 6/4/5/3 bits for REQ/RSP/SNP/DAT; `allowRetry` is one bit and
`pCrdType` is four bits. DAT always carries 512 data bits.

The outer `RingFlit.dst` adds two bits in the default ring. REQ/RSP/DAT route using
`tgtID`. SNP has no `tgtID`; its sender supplies a separate destination node ID,
which `ChiNoC` maps to the outer `dst`. SNP addresses are full aligned byte addresses.
These bundles omit standard fields and do not represent a complete CHI wire layout.

| Channel | Supported messages |
|---|---|
| REQ | ReadShared, ReadUnique, Evict, WriteBackFull, ReadNoSnp, WriteNoSnpFull |
| RSP | SnpResp, CompAck, RetryAck, Comp, CompDBIDResp, DBIDResp, PCrdGrant |
| SNP | SnpShared, SnpUnique |
| DAT | SnpRespData, CopyBackWrData, NonCopyBackWrData, CompData |

Encodings are centralized in [ChiOpcode.scala](../src/main/scala/chi/ChiOpcode.scala).
Internal cache states I/SC/UC/UD use two bits; wire `resp` uses three bits and also
expresses dirty responsibility through I_PD, SC_PD, and UD_PD. They are separate
encodings.

An upstream request is identified by `(srcID, txnID)`; the RNs may reuse the same
transaction ID. HN preserves this identity while using its own downstream ID.
Currently HN's downstream request ID, snoop ID, and HN/SN data-slot DBIDs are zero.
CompData, Comp, CompDBIDResp, and DBIDResp use the corresponding request's transaction
ID. CompAck and write DAT instead put the received DBID in `txnID` to identify the
allocated slot.

## Transactions and completion

Arrows below describe message paths. Each hop uses the corresponding physical
channel and advances only on a handshake.

For a coherent read with no other holder:

```text
 RN                        HN                        SN
  |-- REQ ReadShared ------>|                         |
  |                        |-- REQ ReadNoSnp -------->|
  |                        |<-- DAT CompData(I) ------|
  |<-- DAT CompData(UC) ----|                         |
  |-- RSP CompAck --------->|                         |
```

RN installs the data before sending CompAck. A coherent local write that needs
ownership uses ReadUnique, installs its local write data as UD, then sends CompAck.
RN offers the local result after injecting CompAck; HN releases its context only
after receiving CompAck. Local result backpressure does not retain HN's context.

| Operation | RN behavior | HN completion boundary |
|---|---|---|
| Non-coherent read | ReadNoSnp; receive CompData(I); return result | CompData injected toward RN; no CompAck |
| Non-coherent write | WriteNoSnpFull; receive CompDBIDResp; inject NonCopyBackWrData | SN write-completion Comp received |
| Coherent read/write miss | ReadShared/ReadUnique; install line; inject CompAck | RN CompAck received |
| Clean replacement | Invalidate victim; Evict; wait for Comp; issue original access | Evict Comp injected toward RN |
| Dirty replacement | Move victim to buffer; WriteBackFull; receive CompDBIDResp; inject CopyBackWrData | SN Comp received, or cancelled data consumed |

For either uncached writes or dirty writebacks, HN authorizes RN data with
CompDBIDResp, then issues WriteNoSnpFull to SN. SN responds with DBIDResp, receives
NonCopyBackWrData, writes memory once, and finally sends Comp. DBID authorization
does not mean memory has been updated. RN can continue after handing write data to
the network; HN retains the transaction until SN completes it.

RN cache hits return locally. Reads hit in SC, UC, or UD; writes hit locally only
in UC/UD and leave UD. A write to SC requests ReadUnique. Replacement and the
original access are separate protocol requests within one local command.

The HN directory records up to two holders per line: shared holders or one unique
holder. Its UC permission record covers either UC or UD at the RN because a local
write can silently dirty a unique line. There is no directory replacement. A
non-coherent access through a coherent HN must target an uncached line.

When another RN holds the requested line, HN sends a snoop. A resident cache line
responds as follows:

| Holder state | SnpShared response / new state | SnpUnique response / new state |
|---|---|---|
| I | SnpResp(I) / I | SnpResp(I) / I |
| SC or UC | SnpResp(SC) / SC | SnpResp(I) / I |
| UD | SnpRespData(SC_PD) / SC | SnpRespData(I_PD) / I |

A response without data makes HN read SN. Dirty SnpShared data is written to SN
before HN returns CompData: SC if the peer retains a copy, UC if it relinquishes
the line. Dirty SnpUnique data transfers directly through HN to the requester as
CompData(UD_PD). Both coherent read paths end with CompAck.

RN has a separate snoop-response buffer, serviced while its local transaction or
result is waiting. Snoop handling wins over a simultaneous new local command or
line installation. Snoop replies have sending priority, while an already stalled
RSP/DAT selection stays locked until transfer.

A dirty victim still owns its data until writeback DAT is injected or a snoop takes
it. A snoop can take an unapproved victim, reply with SnpRespData(I_PD), and clear
`victimDirty`; the victim leaves no resident copy. The original WriteBackFull
still completes with CopyBackWrData(I, zero); HN discards it without
writing SN. HN protects an authorized writeback from conflicting snoops; a snoop
arriving with the DBID response can still cancel the victim before DAT is sent.

## Retry and progress

Request retry uses P-Credit type zero. RN-to-HN and HN-to-SN credits are independent.
P-Credit reserves endpoint transaction capacity; ring link credits count FIFO space.

```text
 requester                         completion side
     |-- REQ allowRetry=1 --------->| busy: record waiter
     |<-- RSP RetryAck -------------|
     |                              | finish active work; reserve resource
     |<-- RSP PCrdGrant ------------|
     |-- REQ allowRetry=0 --------->| accept reserved request
     |<-- normal transaction -------|
```

The requester retains its context and records Ack and Grant independently, so
either arrival order is accepted. RetryAck matches the original transaction ID;
PCrdGrant matches the provider and credit type, with zero transaction/DB IDs.
After both arrive, the requester reissues the same identity, opcode, and address.
Credit is consumed on REQ injection. A credited request cannot be rejected again.

`HnParams.retrySlots` sets the retry table size, default two entries, with no duplicate
waiting source. Each entry saves the rejected REQ for identity checks; it is not queued
work to execute automatically. HN selects waiters round-robin. Before granting,
it moves the selected request into its idle transaction context and frees the
waiting entry. This lets another first attempt be consumed and rejected even when
it reaches the REQ FIFO before the credited retry. The new request cannot take the
reservation. Pending RetryAck transmission is drained before admitting a credited
request that could produce a write authorization on RSP.

SN has one waiting record. The serial product HN does not naturally contend with
itself at SN; endpoint tests drive overlapping requests to exercise SN retries.
There is no unsolicited credit, cancellation of granted requests, or PCrdReturn.

Progress relies on three mechanisms: bubble space prevents a full-ring routing
cycle; independent channels let replies and snoops proceed while REQ is occupied;
retry admission consumes unrelated REQs rather than holding them ahead of a
reserved retry. RN's independent snoop path and HN's completion boundaries remove
dependencies on consuming a local result or accepting a new transaction first.
These arguments cover finite offered traffic with eventual downstream acceptance.
Strict forwarding priority gives no injection fairness guarantee under unlimited
competing traffic, and a permanently blocked external consumer prevents completion.

## Validation and model limits

| Property | Main tests |
|---|---|
| Routing, FIFO/output arbitration, credit and bubble rules | [RingStopSpec](../src/test/scala/noc/RingStopSpec.scala), [RingCreditSpec](../src/test/scala/noc/RingCreditSpec.scala), [RingRandomSpec](../src/test/scala/noc/RingRandomSpec.scala) |
| Four-channel independence and negotiated transport | [QuadRingSpec](../src/test/scala/noc/QuadRingSpec.scala), [ChiNoCSpec](../src/test/scala/chi/ChiNoCSpec.scala) |
| Local commands and coherent end-to-end behavior | [ChiSystemSpec](../src/test/scala/chi/ChiSystemSpec.scala), [ChiCoherentSystemSpec](../src/test/scala/chi/ChiCoherentSystemSpec.scala) |
| Random data correctness | [ChiSystemRandomSpec](../src/test/scala/chi/ChiSystemRandomSpec.scala), [ChiCoherentRandomSpec](../src/test/scala/chi/ChiCoherentRandomSpec.scala) |
| Real cache and dirty-victim ownership | [CacheOwnershipSpec](../src/test/scala/chi/CacheOwnershipSpec.scala) |
| Retry execution, single-slot progress, reset and drain | [RetryProtocolSpec](../src/test/scala/chi/RetryProtocolSpec.scala), [RetryObservationSpec](../src/test/scala/chi/RetryObservationSpec.scala), [HnNoCSpec](../src/test/scala/chi/HnNoCSpec.scala) |

Random data models use local command history independently of DUT state. Separate
test-only probes observe cache/victim ownership, request execution, retry credit,
and every ring's credit/occupancy balance. Checks at drain require no waiting
records or reservations, empty FIFOs, and fully restored link credits. The retry
wait bound is a test assertion under bounded backpressure, not a protocol latency
guarantee. These monitors do not appear in generated product RTL.

[test.sh](../test.sh) runs compilation, tests, four independent RTL generators, and
Verilator lint; `--long` selects 100,000 random cycles per seed. CI runs the same
long entry point. See [README](../README.md) for prerequisites and output paths.

This model uses complete lines, Decoupled ports, fixed RN-to-HN-to-SN routing, and
one active context per endpoint. It omits partial writes, SD, CleanUnique/MakeUnique,
direct memory transfer, forwarded snoops, error recovery, and the CHI link layer.
Accepted messages are checked with assertions. Reset empties the network, clears caches,
directory and retry state, and restores SN's configured initial contents; dirty
cache data is not preserved across reset.

See the [completion report](report.md) for delivered capabilities, recorded
regression results, and reproduction commands.
