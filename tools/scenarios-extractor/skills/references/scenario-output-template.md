# Scenario Output Template

Use this structure for generated scenario descriptions. Adapt section depth to the request size, but keep the evidence and checker fields.

```markdown
# <Mechanism> Scenario Extraction

## Scope
- Mechanism:
- Interpreted meaning:
- XiangShan source revision:
- Primary modules/paths:
- Analyzer references used:
- Verification-driver rules used:

## Mechanism Model
| Aspect | Description | Source evidence |
| --- | --- | --- |
| Goal |  |  |
| Inputs |  |  |
| Internal state |  |  |
| Algorithm/control rule |  |  |
| Outputs |  |  |
| Observability |  |  |

## Scenario Taxonomy
| Family | Why it matters | Applicable driver files |
| --- | --- | --- |
| Baseline | Establish nominal behavior | xiangshanVerificationDriver.md, performanceBottleneckDrivers.md |
| Saturation | Stress optimization limits | performanceBottleneckDrivers.md |
| Conflict | Expose arbitration and hazard handling | conflictScenarioDrivers.md |
| Recovery | Verify replay/redirect/flush cleanup | xiangshanVerificationDriver.md, forwardProgressDrivers.md |
| Forward progress | Prevent deadlock/livelock/starvation | forwardProgressDrivers.md |
| Boundary | Cover indices, queues, operands, addresses | operandBoundaryDrivers.md, indexBusHashDrivers.md |
| Observability | Make failures diagnosable | performanceMonitorCounterDrivers.md, difftest references |

## Detailed Scenarios
| ID | Scenario | Initial state | Stimulus sequence | Concurrent pressure | Expected observation | Failure signature | Checkers / coverage | Source evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `<MECH>_BASELINE_001` |  |  |  |  |  |  |  |  |

## Directed Scenario Descriptions

### `<SCENARIO_ID>` - <Scenario Name>
- Intent:
- Code-derived trigger:
- Preconditions:
- Cycle-level stimulus:
- Expected state transitions:
- Expected outputs:
- Negative checks:
- Metrics:
- Coverage bins:
- Debug/waveform signals:
- Source evidence:
- Evidence gaps:

## Checker Plan
| Checker | Type | Watches | Pass condition | Failure message |
| --- | --- | --- | --- | --- |

## Coverage Plan
| Coverpoint | Bins | Crosses | Source rationale |
| --- | --- | --- | --- |

## Evidence Gaps
| Gap | Next file/search/action |
| --- | --- |
```

## Scenario ID Naming

Use stable IDs:
- `<MECH>_BASELINE_<NNN>`
- `<MECH>_SAT_<NNN>`
- `<MECH>_CONFLICT_<NNN>`
- `<MECH>_RECOVERY_<NNN>`
- `<MECH>_PROGRESS_<NNN>`
- `<MECH>_BOUNDARY_<NNN>`
- `<MECH>_OBS_<NNN>`

For MDP, use `MDP_*`.

## Quality Bar

Each scenario should be concrete enough that a verification engineer can implement it without asking what to drive or what to observe. Avoid rows that only say "test stalls" or "check replay"; name the exact requester, resource, condition, state update, downstream effect, and failure signature.
