# XiangShan Source Code Analysis

This directory collects modular source-code analysis notes for [OpenXiangShan/XiangShan](https://github.com/OpenXiangShan/XiangShan.git). The focus is on frontend, backend, cache, and memory-related microarchitecture. The goal is not to restate the source code, but to connect design intent, effective code paths, module boundaries, pipeline behavior, and verification concerns.

Chinese entry: [README.md](README.md).

## Getting The Source

```bash
git clone https://github.com/OpenXiangShan/XiangShan.git
cd XiangShan
```

Recommended references:

- Source repository: [https://github.com/OpenXiangShan/XiangShan.git](https://github.com/OpenXiangShan/XiangShan.git)
- Zread source-code reference: [https://zread.ai/OpenXiangShan/XiangShan](https://zread.ai/OpenXiangShan/XiangShan)

## Reading Map

The main XiangShan Scala source tree is under `src/main/scala/xiangshan/`. This documentation directory follows the same high-level structure:

| Source directory | Local documents | Focus |
| --- | --- | --- |
| `frontend/` | [frontend/](frontend/) | Fetch, predecode, branch prediction, FTQ, ICache request side, instruction buffering |
| `backend/` | [backend/](backend/) | Decode, dispatch, rename, register cache, decode fusion, move elimination |
| `cache/` | Currently covered through frontend ICache and memory notes | Cache hierarchy, instruction cache, and connection points with the memory system |
| `mem/` | [memory/](memory/) | Load/store, memory dependence prediction, replay, memory ordering |

Suggested reading order:

1. [Frontend Overview and End-to-End Signal Analysis](frontend/Frontend-Overview-and-End-to-End-Signal-Analysis.md)
2. [Decode](backend/Decode.md)
3. [Rename](backend/Rename.md)
4. [Dispatch](backend/Dispatch.md)
5. [MDP / Memory Dependence Predictor](memory/mdp-ref.md)

## Frontend

Source path: `src/main/scala/xiangshan/frontend/`

The frontend starts from predicted PCs, issues fetch requests, runs branch prediction, interacts with ICache, performs predecode, and delivers instruction streams to the backend through structures such as FTQ and IBuffer. When reading this part, focus on redirect, override, history update, fetch packet generation, and frontend/backend handshakes.

| Document | Topic |
| --- | --- |
| [Frontend-Overview-and-End-to-End-Signal-Analysis.md](frontend/Frontend-Overview-and-End-to-End-Signal-Analysis.md) | End-to-end frontend signal flow |
| [Frontend-BPU.md](frontend/Frontend-BPU.md) | BPU top level, predictor composition, history, redirect/override |
| [Frontend-BPU-Doc.md](frontend/Frontend-BPU-Doc.md) | BPU design-document style overview |
| [Frontend-FTB.md](frontend/Frontend-FTB.md) | Fetch Target Buffer |
| [Frontend-FauFTB.md](frontend/Frontend-FauFTB.md) | Fast FTB / fall-through prediction |
| [Frontend-Tage.md](frontend/Frontend-Tage.md) | TAGE conditional branch predictor |
| [Frontend-SC.md](frontend/Frontend-SC.md) | Statistical Corrector |
| [Frontend-ITTAGE.md](frontend/Frontend-ITTAGE.md) | Indirect branch prediction |
| [Frontend-RAS.md](frontend/Frontend-RAS.md) | Return Address Stack |
| [Frontend-Bim.md](frontend/Frontend-Bim.md) | BIM base predictor |
| [Frontend-FTQ.md](frontend/Frontend-FTQ.md) | Fetch Target Queue |
| [Frontend-ICache.md](frontend/Frontend-ICache.md) | ICache request path, misses, prefetch, and backpressure |
| [Frontend-IBuffer.md](frontend/Frontend-IBuffer.md) | Instruction buffering and frontend/backend delivery |
| [Frontend-IFU-and-Predecode-Deep-Dive.md](frontend/Frontend-IFU-and-Predecode-Deep-Dive.md) | IFU and predecode deep dive |
| [Frontend-Question-Code-Evidence.md](frontend/Frontend-Question-Code-Evidence.md) | Frontend questions and code evidence index |

## Backend

Source path: `src/main/scala/xiangshan/backend/`

The backend converts frontend-delivered instructions into micro-ops and handles decode, rename, dispatch, scheduling, execution, writeback, and commit. The current notes mainly cover the decode-to-dispatch region, plus performance-oriented mechanisms such as rename, RegCache, move elimination, and decode fusion.

| Document | Topic |
| --- | --- |
| [Decode.md](backend/Decode.md) | Decode information, decode tables, predecode relation, macro-op splitting |
| [Decode-Fusion.md](backend/Decode-Fusion.md) | Decode fusion cases and boundary conditions |
| [Rename.md](backend/Rename.md) | Physical register renaming, FreeList, snapshots, recovery |
| [Move-elimination.md](backend/Move-elimination.md) | Move elimination and reference-counted FreeList behavior |
| [Dispatch.md](backend/Dispatch.md) | Dispatch path, queue connections, and control conditions |
| [RegCache.md](backend/RegCache.md) | RegCache structure, replacement, bypassing, and cancellation paths |

## Cache

Source path: `src/main/scala/xiangshan/cache/`

Cache-specific notes can be expanded later around L1/L2/L3, MSHR, prefetch, uncache, TLB/coherence interfaces, and refill/writeback paths. The current notes most directly related to cache behavior are:

| Document | Topic |
| --- | --- |
| [Frontend-ICache.md](frontend/Frontend-ICache.md) | Frontend ICache, misses, MSHR, prefetch, and fetch backpressure |
| [mdp-ref.md](memory/mdp-ref.md) | Memory dependence prediction and Load/Store queue interaction, useful as an entry point for data-side memory behavior |

When reading the source, inspect `cache/` together with `mem/`, because cache behavior usually depends on Load/Store queues, MSHRs, replay, TLBs, and coherence requests.

## Memory

Source path: `src/main/scala/xiangshan/mem/`

The memory subsystem covers load/store execution, memory ordering, replay, dependence prediction, exceptions, and commit constraints. This documentation directory uses `memory/` as the local name, corresponding to the source tree's `mem/`.

| Document | Topic |
| --- | --- |
| [mdp-ref.md](memory/mdp-ref.md) | Memory Dependence Predictor, SSIT, LFST, Load/Store queue wait and training paths |

## How To Use These Notes

- Read alongside the source: confirm paths and signal names in the XiangShan repository.
- Start with boundaries, then algorithms: understand module I/O, pipeline stages, and flush/redirect/commit behavior before table organization and update policy.
- Track effective code paths: XiangShan contains configuration switches and implementation history, so distinguish design intent, currently effective code, and verification concerns.
- Keep source-backed questions: when notes and design docs diverge, prefer the current source, configuration parameters, and instantiation path.
