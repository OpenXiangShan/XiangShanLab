# uncategorized bug summary

- Count: `462`
- Exception-triggered: `35`
- Interrupt-triggered: `7`
- Source: `issues.jsonl` and `pulls.jsonl`
- Rule: classified from labels, title, body, branch names, and referenced directory/component names.
- Trigger: `exception` and `interrupt` are highlighted from title/body/labels keywords.

| Number | Type | State | Updated | Trigger | Labels | Title |
| --- | --- | --- | --- | --- | --- | --- |
| [#6205](https://github.com/OpenXiangShan/XiangShan/issues/6205) | Issue | open | 2026-07-11T06:47:27Z |  | module: tool | Duplicate --data in the build command triggered by difftest/verilator.mk:126 when compiling emu with a newer version of gcc (Ubuntu) |
| [#6206](https://github.com/OpenXiangShan/XiangShan/pull/6206) | PR | closed | 2026-07-10T07:21:34Z |  | module: utility, module: top, note: submodule bump | fix(reset): use pre-scan reset for CPU clock gate |
| [#5361](https://github.com/OpenXiangShan/XiangShan/pull/5361) | PR | closed | 2026-07-10T02:48:19Z |  |  | fix(CtrlBlock): typo in comment, 'disble' to 'disable' |
| [#6189](https://github.com/OpenXiangShan/XiangShan/issues/6189) | Issue | closed | 2026-07-06T06:44:29Z |  |  | PerfCCT LifeTimeCommitTrace: rare blanked rows (SN=0, PC=0, stages=0) for real committed instructions |
| [#6145](https://github.com/OpenXiangShan/XiangShan/issues/6145) | Issue | open | 2026-06-29T12:54:03Z |  | type: question | Effort required to change XiangShan from 8-issue to 4-issue or single-issue |
| [#6146](https://github.com/OpenXiangShan/XiangShan/issues/6146) | Issue | closed | 2026-06-26T09:12:45Z |  | note: duplicate, type: question | 想咨询下将香山目前的8发射修改为4发射甚至是单发射难度和工作量有多大？ |
| [#6064](https://github.com/OpenXiangShan/XiangShan/issues/6064) | Issue | open | 2026-06-24T08:58:04Z |  | type: bug/reported, module: unknown | Copy-Paste Typo in Mux1H Default Condition — ```isvrgatherei16``` Repeated Instead of ```isvcompress``` in   VPermSrcTypeModule |
| [#6055](https://github.com/OpenXiangShan/XiangShan/pull/6055) | PR | closed | 2026-06-05T13:45:26Z |  | module: tool, note: submodule bump | submodule(difftest): expose cpu AXI when !isFPGA |
| [#4607](https://github.com/OpenXiangShan/XiangShan/issues/4607) | Issue | closed | 2026-05-26T02:27:02Z |  | type: question | Developing workloads on bare metal devices |
| [#4342](https://github.com/OpenXiangShan/XiangShan/issues/4342) | Issue | closed | 2026-05-26T02:26:41Z |  | type: problem | What could be the reason that the serial port only prints a few characters after running the `onboard-ai1-119.tcl` script in the on-board test of the minimized XiangShan project? |
| [#1416](https://github.com/OpenXiangShan/XiangShan/issues/1416) | Issue | closed | 2026-05-26T02:25:54Z |  | type: feature/planned, topic: performance | Optimizations to integer divider latency |
| [#855](https://github.com/OpenXiangShan/XiangShan/issues/855) | Issue | closed | 2026-05-26T02:25:48Z |  | type: feature/planned, topic: usability | Add this core to chipyard |
| [#5940](https://github.com/OpenXiangShan/XiangShan/issues/5940) | Issue | closed | 2026-05-26T02:19:19Z |  | type: problem | Excessive resource consumption during compilation and execution in pdb mode |
| [#5953](https://github.com/OpenXiangShan/XiangShan/issues/5953) | Issue | closed | 2026-05-26T02:19:00Z |  | type: question | Is it possible to measure the number of cycles/instructions and prefetch hits within a region of interest (ROI) ? |
| [#5917](https://github.com/OpenXiangShan/XiangShan/issues/5917) | Issue | closed | 2026-05-22T01:39:14Z |  | type: feature/planned | Add Berti support to PrefetchMonitor |
| [#5758](https://github.com/OpenXiangShan/XiangShan/pull/5758) | PR | closed | 2026-04-27T13:34:25Z |  | topic: functionality, module: top | feat(config): fix KMHV2 minimal and support CHI for FpgaDiff |
| [#5825](https://github.com/OpenXiangShan/XiangShan/pull/5825) | PR | closed | 2026-04-27T08:49:34Z |  | module: tool, note: submodule bump | submodule(difftest): bump difftest for kmh-v2 |
| [#5856](https://github.com/OpenXiangShan/XiangShan/issues/5856) | Issue | closed | 2026-04-24T03:12:58Z |  | type: question | "Does kmh V2R2 support atomic operations in space with the NC (Non-Cacheable) attribute? Why do I get an error when executing them?" |
| [#5818](https://github.com/OpenXiangShan/XiangShan/pull/5818) | PR | closed | 2026-04-16T10:52:40Z |  | topic: functionality, module: top | fix(trace): pipe trace encoder IO in XSNoCTop |
| [#4556](https://github.com/OpenXiangShan/XiangShan/issues/4556) | Issue | closed | 2026-03-31T04:33:32Z |  | type: example | [FAQ] Which branch should I use? |
| [#5608](https://github.com/OpenXiangShan/XiangShan/issues/5608) | Issue | closed | 2026-03-31T02:06:47Z |  | module: top, type: bug/fixed | Compilation failed on the nanhu branch |
| [#5621](https://github.com/OpenXiangShan/XiangShan/issues/5621) | Issue | closed | 2026-03-31T02:06:31Z |  | type: problem | Can the Nanhu version generate a 50-core super-large Xiangshan project? |
| [#5641](https://github.com/OpenXiangShan/XiangShan/issues/5641) | Issue | closed | 2026-03-31T02:04:08Z |  | type: question | Will there be consideration to add a dispatch queue pipeline stage in the future? |
| [#5688](https://github.com/OpenXiangShan/XiangShan/issues/5688) | Issue | closed | 2026-03-31T02:03:52Z |  | type: problem | After compiling with VCS, when running simulation with ./build/simv +workload="${BIN_FILE}" +dump-wave=fsdb > "${LOG_FILE}" 2>&1, only 720 addresses are written in 12 hours. Is this speed normal and how to accelerate simulation? |
| [#5690](https://github.com/OpenXiangShan/XiangShan/issues/5690) | Issue | closed | 2026-03-31T02:02:24Z |  | type: problem | VCS simulation time precision |
| [#5715](https://github.com/OpenXiangShan/XiangShan/issues/5715) | Issue | closed | 2026-03-31T02:01:32Z |  | type: problem | How to modify RTL during EMU simulation |
| [#5727](https://github.com/OpenXiangShan/XiangShan/issues/5727) | Issue | closed | 2026-03-31T02:01:02Z |  | type: question | Is there any way to trim/cut down Kunming Lake so that it can be mapped onto an FPGA board with much fewer resources than the VU19? |
| [#5724](https://github.com/OpenXiangShan/XiangShan/issues/5724) | Issue | closed | 2026-03-26T04:28:38Z | exception | type: bug/reported, module: unknown | 【Bug Report】Some CSRs is inaccessible incorrectly in Debug Mode |
| [#5723](https://github.com/OpenXiangShan/XiangShan/issues/5723) | Issue | closed | 2026-03-25T11:16:27Z |  | type: problem | \xs-env\nexus-am\apps guide |
| [#5709](https://github.com/OpenXiangShan/XiangShan/pull/5709) | PR | closed | 2026-03-24T13:50:39Z |  | module: top | fix(SoC): change AsyncBrigeSink fifo to 4 |
| [#5714](https://github.com/OpenXiangShan/XiangShan/issues/5714) | Issue | closed | 2026-03-24T05:05:36Z | exception | type: bug/invalid, module: unknown | [bug report](Trigger): triggerActionGen uses index-based priority instead of action-type priority when multiple triggers fire simultaneously |
| [#5713](https://github.com/OpenXiangShan/XiangShan/issues/5713) | Issue | closed | 2026-03-24T05:00:10Z |  | type: bug/invalid, module: unknown | [Bug Report](Mcontrol6): Missing forward dmode check when writing mcontrol6.chain |
| [#5710](https://github.com/OpenXiangShan/XiangShan/issues/5710) | Issue | closed | 2026-03-24T00:53:41Z |  | type: question | where is the branch that OpenXiangShan/XiangShan/tree/decoupled-oracle |
| [#5656](https://github.com/OpenXiangShan/XiangShan/pull/5656) | PR | closed | 2026-03-09T06:21:27Z |  | module: tool | feat(ci): update ci branch name from master to kunminghu-v2 |
| [#5624](https://github.com/OpenXiangShan/XiangShan/issues/5624) | Issue | closed | 2026-03-06T23:31:57Z |  | type: problem | GSim undefined reference while building Xiangshan Simulation |
| [#5582](https://github.com/OpenXiangShan/XiangShan/pull/5582) | PR | open | 2026-03-06T09:06:19Z |  | module: other, module: top | feat(dsu_cdb): Add dsu_cdb to support chi async bridge from DSU |
| [#5524](https://github.com/OpenXiangShan/XiangShan/pull/5524) | PR | open | 2026-03-06T09:06:19Z |  | topic: functionality, module: top | Fix async sink powerack |
| [#5498](https://github.com/OpenXiangShan/XiangShan/pull/5498) | PR | open | 2026-03-06T09:06:19Z |  |  | ci: disable bolt pgo for mc |
| [#5445](https://github.com/OpenXiangShan/XiangShan/pull/5445) | PR | open | 2026-03-06T09:06:19Z |  |  | Chore core device range test |
| [#5444](https://github.com/OpenXiangShan/XiangShan/pull/5444) | PR | open | 2026-03-06T09:06:19Z |  | note: do not merge, module: utility, module: top | feat: dump Diplomacy bus addressing on elaboration |
| [#5214](https://github.com/OpenXiangShan/XiangShan/pull/5214) | PR | open | 2026-03-06T09:06:18Z |  | module: documentation | doc(readme): add usage info of galaxsim simu |
| [#5202](https://github.com/OpenXiangShan/XiangShan/pull/5202) | PR | open | 2026-03-06T09:06:18Z |  | module: tool | chore(make): delete out folder when `make clean` |
| [#5047](https://github.com/OpenXiangShan/XiangShan/pull/5047) | PR | open | 2026-03-06T09:06:18Z |  |  | Uart16550 intgration |
| [#5011](https://github.com/OpenXiangShan/XiangShan/pull/5011) | PR | open | 2026-03-06T09:06:18Z |  | module: tool | feat(pdb): add asm and dasm commands for XSPdb |
| [#4928](https://github.com/OpenXiangShan/XiangShan/pull/4928) | PR | open | 2026-03-06T09:06:17Z | exception |  | fix(PrintControl): handle IntModule in onModule method |
| [#4926](https://github.com/OpenXiangShan/XiangShan/pull/4926) | PR | open | 2026-03-06T09:06:17Z |  |  | feat(pydrofoil): (WIP) add new reference model with pydrofoil |
| [#4888](https://github.com/OpenXiangShan/XiangShan/pull/4888) | PR | open | 2026-03-06T09:06:17Z |  | note: do not merge | fix(AXI4Memory): re-written AXI interface of AXI4Memory |
| [#4858](https://github.com/OpenXiangShan/XiangShan/pull/4858) | PR | open | 2026-03-06T09:06:17Z |  |  | fix(XSNoCTop): fix exico cross clock domain |
| [#4763](https://github.com/OpenXiangShan/XiangShan/pull/4763) | PR | open | 2026-03-06T09:06:17Z |  | topic: code quality | WIP: refactor(XSTileWrap): Refactor XSTileWrap with feature traits |
| [#4721](https://github.com/OpenXiangShan/XiangShan/pull/4721) | PR | open | 2026-03-06T09:06:16Z |  |  | fix(XSNoCTop): change clock gate enable signal to cpuReset_sync |
| [#4584](https://github.com/OpenXiangShan/XiangShan/pull/4584) | PR | open | 2026-03-06T09:06:16Z |  |  | fix:sync process about all the intSrc,and reset sync for lowpower |
| [#4518](https://github.com/OpenXiangShan/XiangShan/pull/4518) | PR | open | 2026-03-06T09:06:16Z | interrupt |  | wfi wakeup condition change by turn on Power Gate  |
| [#4458](https://github.com/OpenXiangShan/XiangShan/pull/4458) | PR | open | 2026-03-06T09:06:16Z |  |  | fix(Configs): add XSNoCTopPowerGateConfig to turn on all power relate… |
| [#4450](https://github.com/OpenXiangShan/XiangShan/pull/4450) | PR | open | 2026-03-06T09:06:16Z |  | note: do not merge | chore: bump scala to 2.13.16 and chisel to 6.7.0 |
| [#4377](https://github.com/OpenXiangShan/XiangShan/pull/4377) | PR | open | 2026-03-06T09:06:16Z |  |  | ci:Add the cvm-test workflow |
| [#4106](https://github.com/OpenXiangShan/XiangShan/pull/4106) | PR | open | 2026-03-06T09:06:15Z |  |  | perf(VlMergeBuffer): change `VlMergeBufferSize` to `24` |
| [#4082](https://github.com/OpenXiangShan/XiangShan/pull/4082) | PR | open | 2026-03-06T09:06:15Z |  |  | ppa(LRQ): reduce LRQ entries |
| [#4060](https://github.com/OpenXiangShan/XiangShan/pull/4060) | PR | open | 2026-03-06T09:06:15Z |  |  | build: bump mill to 0.12.4 |
| [#4040](https://github.com/OpenXiangShan/XiangShan/pull/4040) | PR | open | 2026-03-06T09:06:15Z |  |  | submodule: bump submodule yunsuan and rocket-chip |
| [#3969](https://github.com/OpenXiangShan/XiangShan/pull/3969) | PR | open | 2026-03-06T09:06:15Z |  | module: tool | ci: use `KunminghuV2Config` |
| [#3726](https://github.com/OpenXiangShan/XiangShan/pull/3726) | PR | open | 2026-03-06T09:06:14Z |  |  | feat(AXI4Memory): add support for WRAP burst mode, and fix a bug |
| [#3576](https://github.com/OpenXiangShan/XiangShan/pull/3576) | PR | open | 2026-03-06T09:06:14Z |  |  | refactor: use `Option.when` instead of ifelse or OptionWrapper |
| [#3098](https://github.com/OpenXiangShan/XiangShan/pull/3098) | PR | open | 2026-03-06T09:06:14Z |  |  | Update Snapshot.scala |
| [#3061](https://github.com/OpenXiangShan/XiangShan/pull/3061) | PR | open | 2026-03-06T09:06:14Z |  |  | Add format checking for XiangShan |
| [#2846](https://github.com/OpenXiangShan/XiangShan/pull/2846) | PR | open | 2026-03-06T09:06:14Z |  |  | Add the mill wrapper (millw) |
| [#2672](https://github.com/OpenXiangShan/XiangShan/pull/2672) | PR | open | 2026-03-06T09:06:14Z |  | note: do not merge | hartid: dynamically append hartid |
| [#5453](https://github.com/OpenXiangShan/XiangShan/issues/5453) | Issue | closed | 2026-01-24T03:16:01Z |  | type: problem | [FPGA][CHI] the system will hang after startup. |
| [#5534](https://github.com/OpenXiangShan/XiangShan/issues/5534) | Issue | closed | 2026-01-15T01:12:47Z |  | type: problem | make emu CONFIG=KunminghuV2 |
| [#5450](https://github.com/OpenXiangShan/XiangShan/issues/5450) | Issue | closed | 2025-12-31T09:01:06Z | exception | type: bug/fixed | Error when run “make verilog CONFIG=FpgaDefaultConfig” |
| [#5191](https://github.com/OpenXiangShan/XiangShan/issues/5191) | Issue | closed | 2025-12-30T01:33:27Z |  | type: problem | Error when make emu with ` EMU_COVERAGE=1` |
| [#5338](https://github.com/OpenXiangShan/XiangShan/issues/5338) | Issue | closed | 2025-12-15T07:55:28Z |  | type: problem/answered | Svpbmt support question |
| [#5284](https://github.com/OpenXiangShan/XiangShan/issues/5284) | Issue | closed | 2025-12-01T08:23:55Z |  | type: question/answered | 请教关于香山最小系统的复位改造问题 |
| [#3346](https://github.com/OpenXiangShan/XiangShan/issues/3346) | Issue | closed | 2025-11-24T07:15:02Z |  | type: problem/answered | How can I make a 4core image? |
| [#5171](https://github.com/OpenXiangShan/XiangShan/issues/5171) | Issue | open | 2025-11-21T09:52:24Z |  | type: problem | Peripheral port access efficiency |
| [#5161](https://github.com/OpenXiangShan/XiangShan/issues/5161) | Issue | closed | 2025-11-17T07:45:19Z |  | type: problem | Dual-core version of kmh FPGA prototype，the OS can only recognize one core. |
| [#5198](https://github.com/OpenXiangShan/XiangShan/issues/5198) | Issue | closed | 2025-11-11T11:07:58Z |  | type: problem | UART output out of order with XS NANHU minimal verification system |
| [#3388](https://github.com/OpenXiangShan/XiangShan/issues/3388) | Issue | closed | 2025-11-05T03:02:19Z |  | type: problem | How to debug Xiangshan on FPGA with OpenOCD? |
| [#3466](https://github.com/OpenXiangShan/XiangShan/issues/3466) | Issue | closed | 2025-11-05T03:01:51Z |  | type: question | GPRs modification |
| [#2143](https://github.com/OpenXiangShan/XiangShan/issues/2143) | Issue | closed | 2025-11-05T02:59:22Z |  | type: question | How to modify the reset address and stack address space of coremark and dhrystone in nexus-am/apps? |
| [#4974](https://github.com/OpenXiangShan/XiangShan/issues/4974) | Issue | closed | 2025-11-05T02:57:18Z |  | type: problem, note: need more information | No instruction commits for 5000 cycles |
| [#5173](https://github.com/OpenXiangShan/XiangShan/issues/5173) | Issue | closed | 2025-11-04T16:31:58Z |  | type: question | Commit log without difftest |
| [#5105](https://github.com/OpenXiangShan/XiangShan/issues/5105) | Issue | closed | 2025-10-29T02:05:10Z |  | type: question/answered | An open-source FPGA minimal system integrating Ethernet MAC IP Core based on Xiangshan South Lake（Dual Core） |
| [#5140](https://github.com/OpenXiangShan/XiangShan/issues/5140) | Issue | closed | 2025-10-24T09:04:14Z | exception | type: problem | How to use NEMU to debug |
| [#5119](https://github.com/OpenXiangShan/XiangShan/issues/5119) | Issue | open | 2025-10-17T06:01:24Z |  | type: problem | MIG is Invalid Core |
| [#5116](https://github.com/OpenXiangShan/XiangShan/issues/5116) | Issue | closed | 2025-10-15T09:15:09Z |  | type: problem | MIG is Invalid Core |
| [#5117](https://github.com/OpenXiangShan/XiangShan/issues/5117) | Issue | closed | 2025-10-15T09:14:56Z |  | type: problem | MIG is Invalid Core |
| [#5089](https://github.com/OpenXiangShan/XiangShan/issues/5089) | Issue | closed | 2025-10-15T02:44:31Z |  | type: problem | 基于香山的 FPGA 开源最小系统构建指引当中镜像用户名和密码错误 |
| [#5100](https://github.com/OpenXiangShan/XiangShan/issues/5100) | Issue | closed | 2025-10-11T03:33:18Z |  | type: question/answered | 昆明湖怎么跑双核版本？如果想用一片VU19P的资源跑双核版本的昆明湖可以对哪些资源进行配置裁剪？ |
| [#4289](https://github.com/OpenXiangShan/XiangShan/issues/4289) | Issue | closed | 2025-10-09T08:44:46Z |  | type: question/answered | Troubles of FPGA prototyping on Xilinx xcvu19p |
| [#3743](https://github.com/OpenXiangShan/XiangShan/issues/3743) | Issue | closed | 2025-09-10T07:20:17Z | interrupt | type: feature/requested, topic: usability | Package the IP and compile it into a Verilog module |
| [#4355](https://github.com/OpenXiangShan/XiangShan/issues/4355) | Issue | closed | 2025-09-10T07:18:31Z |  | type: feature/not planned | software simulation trace |
| [#4483](https://github.com/OpenXiangShan/XiangShan/issues/4483) | Issue | closed | 2025-09-10T07:15:49Z |  | type: question | current pc |
| [#3312](https://github.com/OpenXiangShan/XiangShan/issues/3312) | Issue | closed | 2025-09-10T07:09:09Z |  | type: problem, note: need more information | add some IO ports |
| [#3084](https://github.com/OpenXiangShan/XiangShan/issues/3084) | Issue | closed | 2025-09-10T07:08:45Z |  | type: bug/reported, topic: functionality | In VCS simulation, multi-core simulation of some harts ended prematurely due to incorrect execution of SEQZ instruction |
| [#2114](https://github.com/OpenXiangShan/XiangShan/issues/2114) | Issue | closed | 2025-09-10T06:33:01Z |  | type: problem, note: need more information | How to verify XSTop.v on FPGA |
| [#2179](https://github.com/OpenXiangShan/XiangShan/issues/2179) | Issue | closed | 2025-09-10T06:32:16Z |  | type: problem, note: need more information | Dual-core simulation failed |
| [#3678](https://github.com/OpenXiangShan/XiangShan/issues/3678) | Issue | closed | 2025-09-10T06:30:58Z |  | type: question, note: need more information | dump-wave config: how display structs of sv  in  vcd wave when use gtkwave  |
| [#3571](https://github.com/OpenXiangShan/XiangShan/issues/3571) | Issue | closed | 2025-09-10T06:30:41Z |  | type: problem, note: need more information | Run sudo -s ./setup-tools.sh C++ compiler has problem |
| [#3638](https://github.com/OpenXiangShan/XiangShan/issues/3638) | Issue | closed | 2025-09-10T06:30:23Z |  | type: question | How Many LUTs Are Required for the FPGA Minimal System of the Xiangshan Processor to Operate Normally? |
| [#4418](https://github.com/OpenXiangShan/XiangShan/issues/4418) | Issue | closed | 2025-09-10T06:27:42Z |  | type: question | How to use Palladium to do emulation? |
| [#4695](https://github.com/OpenXiangShan/XiangShan/issues/4695) | Issue | closed | 2025-09-05T08:11:45Z |  | type: problem/answered | There is a problem generating emu after modifying simtop-v |
| [#4609](https://github.com/OpenXiangShan/XiangShan/issues/4609) | Issue | closed | 2025-09-05T08:11:22Z |  | type: problem/answered | The nanhu FPGA Prototype phenomenon of FPGA on the board？ |
| [#4589](https://github.com/OpenXiangShan/XiangShan/issues/4589) | Issue | closed | 2025-09-05T08:08:23Z |  | type: problem/answered | the data.txt may has invalid RISCV instr |
| [#4552](https://github.com/OpenXiangShan/XiangShan/issues/4552) | Issue | closed | 2025-09-05T08:07:54Z |  | type: problem/answered | M-mode |
| [#4457](https://github.com/OpenXiangShan/XiangShan/issues/4457) | Issue | closed | 2025-09-05T08:04:45Z |  | type: problem/answered | How to generate a CHI port? |
| [#4425](https://github.com/OpenXiangShan/XiangShan/issues/4425) | Issue | closed | 2025-09-05T08:04:15Z |  | type: problem/answered | build |
| [#4138](https://github.com/OpenXiangShan/XiangShan/issues/4138) | Issue | closed | 2025-09-05T08:02:58Z |  | type: problem/answered | mill error about maven |
| [#3817](https://github.com/OpenXiangShan/XiangShan/issues/3817) | Issue | closed | 2025-09-05T08:00:14Z |  | type: problem/answered |  workload error; It runs for a long time with no results |
| [#3530](https://github.com/OpenXiangShan/XiangShan/issues/3530) | Issue | closed | 2025-09-05T07:59:11Z |  | type: problem/answered | no error when make verilog, but no Top.v generated |
| [#3446](https://github.com/OpenXiangShan/XiangShan/issues/3446) | Issue | closed | 2025-09-05T07:58:46Z |  | type: problem/answered | How to use Xiangshan tutorial for multi-core testing about spec 2006 |
| [#3415](https://github.com/OpenXiangShan/XiangShan/issues/3415) | Issue | closed | 2025-09-05T07:58:26Z |  | type: problem/answered | About the clock problem in Xiangshan |
| [#3390](https://github.com/OpenXiangShan/XiangShan/issues/3390) | Issue | closed | 2025-09-05T07:58:14Z |  | type: problem/answered | wrong address space |
| [#3377](https://github.com/OpenXiangShan/XiangShan/issues/3377) | Issue | closed | 2025-09-05T07:58:02Z |  | type: problem/answered | Is there any official OpenSBI version for booting linux on S2C's Prodigy™ S7-19PD Logic System？ |
| [#3365](https://github.com/OpenXiangShan/XiangShan/issues/3365) | Issue | closed | 2025-09-05T07:57:30Z |  | type: problem/answered | Question about emulation of the CHI version |
| [#3234](https://github.com/OpenXiangShan/XiangShan/issues/3234) | Issue | closed | 2025-09-05T07:56:33Z |  | type: problem/answered | How to enable the DebugModule of StandAloneDebugModule with the AXI version |
| [#939](https://github.com/OpenXiangShan/XiangShan/issues/939) | Issue | closed | 2025-09-05T07:55:42Z | exception | type: problem/answered | idea java.lang.StackOverflowError |
| [#2642](https://github.com/OpenXiangShan/XiangShan/issues/2642) | Issue | closed | 2025-09-05T07:53:10Z |  | type: bug/fixed | Non-Canonical NaN Representation in Double-Precision Results from fmadd.d Instruction |
| [#4242](https://github.com/OpenXiangShan/XiangShan/issues/4242) | Issue | closed | 2025-09-05T07:48:48Z |  | good first issue, type: bug/fixed | Possible bug in statistical corrector |
| [#3154](https://github.com/OpenXiangShan/XiangShan/issues/3154) | Issue | closed | 2025-09-05T07:43:22Z |  | type: bug/invalid | error massage |
| [#851](https://github.com/OpenXiangShan/XiangShan/issues/851) | Issue | closed | 2025-09-05T07:26:15Z |  | type: feature/planned, topic: verification | A fast CI is what we need |
| [#846](https://github.com/OpenXiangShan/XiangShan/issues/846) | Issue | closed | 2025-09-05T07:24:05Z |  | topic: performance, type: feature/not planned | Consider to implement CoLT: Coalesced Large-Reach TLBs |
| [#4787](https://github.com/OpenXiangShan/XiangShan/issues/4787) | Issue | closed | 2025-09-05T07:19:24Z |  | type: question/answered | Regarding the clock frequency for simulation |
| [#4778](https://github.com/OpenXiangShan/XiangShan/issues/4778) | Issue | closed | 2025-09-05T07:19:04Z |  | type: question/answered | Regarding the Measurement of DDR Interface Execution Rate |
| [#4738](https://github.com/OpenXiangShan/XiangShan/issues/4738) | Issue | closed | 2025-09-05T07:18:22Z |  | type: question/answered | clock frequency |
| [#4447](https://github.com/OpenXiangShan/XiangShan/issues/4447) | Issue | closed | 2025-09-05T07:17:43Z |  | type: question/answered | regfile |
| [#4434](https://github.com/OpenXiangShan/XiangShan/issues/4434) | Issue | closed | 2025-09-05T07:16:44Z |  | type: question/answered | The function of assert stmt in Xiangshan |
| [#4372](https://github.com/OpenXiangShan/XiangShan/issues/4372) | Issue | closed | 2025-09-05T07:16:10Z |  | type: question/answered | Regarding the development of configuration programs |
| [#4261](https://github.com/OpenXiangShan/XiangShan/issues/4261) | Issue | closed | 2025-09-05T07:12:45Z |  | type: question/answered | Question about generate anno.json file of XiangShan |
| [#4004](https://github.com/OpenXiangShan/XiangShan/issues/4004) | Issue | closed | 2025-09-05T07:12:32Z | interrupt | type: bug/invalid | Inconsistent values ​​of mip registers |
| [#3954](https://github.com/OpenXiangShan/XiangShan/issues/3954) | Issue | closed | 2025-09-05T07:11:30Z |  | type: bug/invalid | The UF flag in the fcsr register is different |
| [#3448](https://github.com/OpenXiangShan/XiangShan/issues/3448) | Issue | closed | 2025-09-05T07:10:05Z |  | type: question/answered | About SDCard module |
| [#3306](https://github.com/OpenXiangShan/XiangShan/issues/3306) | Issue | closed | 2025-09-05T07:09:55Z |  | type: question/answered | --no-check-comb-loops |
| [#2646](https://github.com/OpenXiangShan/XiangShan/issues/2646) | Issue | closed | 2025-09-05T07:09:18Z |  | type: question/answered | Is there a performance visulation tool, or pipe viewer tool available? |
| [#2630](https://github.com/OpenXiangShan/XiangShan/issues/2630) | Issue | closed | 2025-09-05T07:08:57Z |  | type: question/answered | TileLink to CHI |
| [#2481](https://github.com/OpenXiangShan/XiangShan/issues/2481) | Issue | closed | 2025-09-05T07:08:41Z |  | type: question/answered | About the Use of TL-Test |
| [#1142](https://github.com/OpenXiangShan/XiangShan/issues/1142) | Issue | closed | 2025-09-05T07:07:34Z |  | type: question/answered | Question concerning AXI bus |
| [#1971](https://github.com/OpenXiangShan/XiangShan/issues/1971) | Issue | closed | 2025-09-05T06:46:15Z |  | type: question/answered | It is said that Xiangshan has successfully launched X Window. Is there an FPGA project for download reference as well as a companion bin file. |
| [#1671](https://github.com/OpenXiangShan/XiangShan/issues/1671) | Issue | closed | 2025-09-05T06:44:27Z |  | type: question/answered | Question about XiangShan verification |
| [#4568](https://github.com/OpenXiangShan/XiangShan/issues/4568) | Issue | closed | 2025-08-27T02:50:11Z | exception | type: problem/answered | Segmentation fault when running simple example (hello) on emu |
| [#4639](https://github.com/OpenXiangShan/XiangShan/issues/4639) | Issue | closed | 2025-08-26T13:09:58Z | exception | type: bug/fixed | `mtval` different |
| [#2464](https://github.com/OpenXiangShan/XiangShan/issues/2464) | Issue | closed | 2025-08-26T13:09:57Z |  | type: bug/fixed | Fusion decoder does not prevent rs1=rs2 |
| [#49](https://github.com/OpenXiangShan/XiangShan/issues/49) | Issue | closed | 2025-08-26T13:09:55Z |  | type: bug/fixed | 重命名表初始化与后续维护存在问题 |
| [#48](https://github.com/OpenXiangShan/XiangShan/issues/48) | Issue | closed | 2025-08-26T13:09:53Z |  | type: bug/fixed | regfile写口仲裁 |
| [#4879](https://github.com/OpenXiangShan/XiangShan/issues/4879) | Issue | closed | 2025-08-26T12:43:04Z |  | type: problem/answered | 关于制作workload的问题 |
| [#4726](https://github.com/OpenXiangShan/XiangShan/issues/4726) | Issue | closed | 2025-08-26T12:43:02Z |  | type: problem/answered | problem with XiangShan nanhu minimal core project, simulation with the nanhu minimal core project ,XS core read DDR4 data step stopped at "0x00002cc0" . |
| [#4656](https://github.com/OpenXiangShan/XiangShan/issues/4656) | Issue | closed | 2025-08-26T12:43:01Z | interrupt | type: problem/answered | Dual Core Coherency Problem |
| [#4499](https://github.com/OpenXiangShan/XiangShan/issues/4499) | Issue | closed | 2025-08-26T12:42:58Z |  | type: problem/answered | Error when compiling with make sim-verilog |
| [#4424](https://github.com/OpenXiangShan/XiangShan/issues/4424) | Issue | closed | 2025-08-26T12:42:56Z |  | type: problem/answered | unlimited loop |
| [#4421](https://github.com/OpenXiangShan/XiangShan/issues/4421) | Issue | closed | 2025-08-26T12:42:55Z |  | type: problem/answered | build |
| [#4405](https://github.com/OpenXiangShan/XiangShan/issues/4405) | Issue | closed | 2025-08-26T12:42:53Z |  | type: problem/answered | Emu: conflicting declaration of C function ‘void flash_read(uint32_t, uint64_t*)’ |
| [#4343](https://github.com/OpenXiangShan/XiangShan/issues/4343) | Issue | closed | 2025-08-26T12:42:52Z |  | type: problem/answered | trouble with nanhu prototype board test |
| [#4327](https://github.com/OpenXiangShan/XiangShan/issues/4327) | Issue | closed | 2025-08-26T12:42:51Z |  | type: problem/answered | Problem with generating verilog |
| [#4309](https://github.com/OpenXiangShan/XiangShan/issues/4309) | Issue | closed | 2025-08-26T12:42:50Z |  | type: problem/answered | The NEMU profiling process remains active, even though the screen displays output that matches the definitions in the source program. |
| [#4270](https://github.com/OpenXiangShan/XiangShan/issues/4270) | Issue | closed | 2025-08-26T12:42:49Z |  | type: problem/answered | make simv RELEASE=1 :  compile-error |
| [#4170](https://github.com/OpenXiangShan/XiangShan/issues/4170) | Issue | closed | 2025-08-26T12:42:47Z |  | type: problem/answered | Waveform generation error |
| [#4130](https://github.com/OpenXiangShan/XiangShan/issues/4130) | Issue | closed | 2025-08-26T12:42:46Z |  | type: problem/answered | mill 404 when building XiangShan tag v2.1 |
| [#3666](https://github.com/OpenXiangShan/XiangShan/issues/3666) | Issue | closed | 2025-08-26T12:42:42Z |  | type: problem/answered | How did Xiangshan achieve 10Mhz uart_16550? |
| [#3797](https://github.com/OpenXiangShan/XiangShan/issues/3797) | Issue | closed | 2025-08-26T12:42:25Z |  | type: problem/answered | NEMU error |
| [#3782](https://github.com/OpenXiangShan/XiangShan/issues/3782) | Issue | closed | 2025-08-26T12:42:24Z |  | type: problem/answered | problem about generating bitstream |
| [#3780](https://github.com/OpenXiangShan/XiangShan/issues/3780) | Issue | closed | 2025-08-26T12:42:23Z |  | note: duplicate, type: problem/answered | make init error |
| [#3773](https://github.com/OpenXiangShan/XiangShan/issues/3773) | Issue | closed | 2025-08-26T12:42:22Z | interrupt | type: problem/answered | Xiangshan stuck when execute `wfi` |
| [#3567](https://github.com/OpenXiangShan/XiangShan/issues/3567) | Issue | closed | 2025-08-26T12:42:19Z |  | type: problem/answered | After adding swap, still got 1 targets failed      xiangshan.test.runMain Subprocess failed |
| [#3405](https://github.com/OpenXiangShan/XiangShan/issues/3405) | Issue | closed | 2025-08-26T12:42:16Z |  | type: problem/answered | Emu-compile Error |
| [#3289](https://github.com/OpenXiangShan/XiangShan/issues/3289) | Issue | closed | 2025-08-26T12:42:15Z |  | type: problem/answered | Run spec2006 using opensbi-kernel-for-xs tutorial |
| [#3271](https://github.com/OpenXiangShan/XiangShan/issues/3271) | Issue | closed | 2025-08-26T12:42:14Z |  | type: problem/answered | How can I check the CPU frequency of the two cores of Xiangshan? |
| [#3248](https://github.com/OpenXiangShan/XiangShan/issues/3248) | Issue | closed | 2025-08-26T12:42:13Z |  | type: problem/answered | How to use other versions of Linux kernel for Xiangshan projects？ |
| [#3195](https://github.com/OpenXiangShan/XiangShan/issues/3195) | Issue | closed | 2025-08-26T12:42:12Z |  | type: problem/answered | Using docker to check data.txt generated by compiling app, but cannot find compiled instruction after burning |
| [#3144](https://github.com/OpenXiangShan/XiangShan/issues/3144) | Issue | closed | 2025-08-26T12:42:11Z |  | type: problem/answered | xsdebug |
| [#3123](https://github.com/OpenXiangShan/XiangShan/issues/3123) | Issue | closed | 2025-08-26T12:42:10Z |  | type: problem/answered | ./build/emu  |
| [#3057](https://github.com/OpenXiangShan/XiangShan/issues/3057) | Issue | closed | 2025-08-26T12:42:09Z |  | type: problem/answered | Assertion failed     at UserYanker.scala:63 assert (!out.r.valid \|\| r_valid) // Q must be ready faster than the response |
| [#2989](https://github.com/OpenXiangShan/XiangShan/issues/2989) | Issue | closed | 2025-08-26T12:42:08Z | exception | type: problem/answered | Compile Exception while using "make emu EMU_THREADS=8 MFC=1 CONFIG=KunminghuV2Config", |
| [#2962](https://github.com/OpenXiangShan/XiangShan/issues/2962) | Issue | closed | 2025-08-26T12:42:07Z |  | type: problem/answered | 使用命令时遇到错误“已放弃（核心已转储） ” |
| [#2801](https://github.com/OpenXiangShan/XiangShan/issues/2801) | Issue | closed | 2025-08-26T12:42:06Z | exception | type: problem/answered | make emu error - Exception in thread "main" java.util.NoSuchElementException: NOOP_HOME |
| [#2769](https://github.com/OpenXiangShan/XiangShan/issues/2769) | Issue | closed | 2025-08-26T12:42:04Z |  | type: problem/answered | Correct NEMU version for nanhu branch of XiangShan |
| [#3883](https://github.com/OpenXiangShan/XiangShan/issues/3883) | Issue | closed | 2025-08-26T12:32:12Z |  | type: question/answered | The initial value of `tdata2` is inconsistent, `tdata1` is consistent with NEMU. |
| [#3882](https://github.com/OpenXiangShan/XiangShan/issues/3882) | Issue | closed | 2025-08-26T12:32:11Z |  | type: question/answered | The initial value of `mnscratch` is inconsistent |
| [#3735](https://github.com/OpenXiangShan/XiangShan/issues/3735) | Issue | closed | 2025-08-26T12:32:09Z |  | type: question/answered | fpSchdParms should use FpDqDeqWidth instead of VecDqDeqWidth |
| [#3663](https://github.com/OpenXiangShan/XiangShan/issues/3663) | Issue | closed | 2025-08-26T12:32:07Z |  | type: question/answered | How to set trapCode to STATE_GOODTRAP by instruction? |
| [#3613](https://github.com/OpenXiangShan/XiangShan/issues/3613) | Issue | closed | 2025-08-26T12:32:05Z |  | type: question/answered | Could you please provide the relevant documents for building on the VCU128 FPGA development board? |
| [#3155](https://github.com/OpenXiangShan/XiangShan/issues/3155) | Issue | closed | 2025-08-26T12:32:00Z |  | type: question/answered | What is the stable version of Nanhu-V2 verified on FPGA? |
| [#2987](https://github.com/OpenXiangShan/XiangShan/issues/2987) | Issue | closed | 2025-08-26T12:31:59Z | exception | type: question/answered | Eeception while compile using "make emu EMU_THREADS=8 MFC=1 CONFIG=KunminghuV2Config" |
| [#2929](https://github.com/OpenXiangShan/XiangShan/issues/2929) | Issue | closed | 2025-08-26T12:31:58Z |  | type: question/answered | How can projects using Chisel 3 and Chisel 5/6 be integrated together? |
| [#2886](https://github.com/OpenXiangShan/XiangShan/issues/2886) | Issue | closed | 2025-08-26T12:31:56Z |  | type: question/answered | Using XiangShan to reproduce the example code in RISC-V Architecture Programming and Practice. |
| [#2858](https://github.com/OpenXiangShan/XiangShan/issues/2858) | Issue | closed | 2025-08-26T12:31:56Z |  | type: question/answered | How to use profile function of verilator in XiangShan environment |
| [#2711](https://github.com/OpenXiangShan/XiangShan/issues/2711) | Issue | closed | 2025-08-26T12:31:54Z |  | type: question/answered | Ensuring RVWMO Compliance for XS: Missing aq/rl Annotation Implementations？ |
| [#2317](https://github.com/OpenXiangShan/XiangShan/issues/2317) | Issue | closed | 2025-08-26T12:31:52Z |  | type: question/answered | Question about debug mode on branch `nanhu` |
| [#2137](https://github.com/OpenXiangShan/XiangShan/issues/2137) | Issue | closed | 2025-08-26T12:31:51Z |  | type: question/answered | How to enter debug mode via trigger |
| [#3879](https://github.com/OpenXiangShan/XiangShan/issues/3879) | Issue | closed | 2025-08-26T12:31:13Z |  | type: bug/fixed | c.unimp instruction problem |
| [#2698](https://github.com/OpenXiangShan/XiangShan/issues/2698) | Issue | closed | 2025-08-26T12:31:02Z |  | type: question/answered | Can core0 and core1 be configured asynchronously？ |
| [#2697](https://github.com/OpenXiangShan/XiangShan/issues/2697) | Issue | closed | 2025-08-26T12:31:01Z |  | type: question/answered | Is there a restriction for core number in nanhu？ |
| [#2444](https://github.com/OpenXiangShan/XiangShan/issues/2444) | Issue | closed | 2025-08-26T12:30:59Z |  | type: question/answered | VCS simulation does not support Dramsim3 |
| [#2396](https://github.com/OpenXiangShan/XiangShan/issues/2396) | Issue | closed | 2025-08-26T12:30:58Z | exception | type: question/answered | 访存子系统中的异常处理 |
| [#1887](https://github.com/OpenXiangShan/XiangShan/issues/1887) | Issue | closed | 2025-08-26T12:30:57Z |  | type: question/answered | 请问一致性树的问题 |
| [#869](https://github.com/OpenXiangShan/XiangShan/issues/869) | Issue | closed | 2025-08-26T12:30:55Z |  | type: question/answered | 请教关于bpu中if3_prevHalfInstr的信号类型问题 |
| [#4904](https://github.com/OpenXiangShan/XiangShan/issues/4904) | Issue | closed | 2025-08-26T12:30:24Z |  | module: documentation, type: bug/fixed | A typo in README.md / README.md中的拼写错误 |
| [#4796](https://github.com/OpenXiangShan/XiangShan/issues/4796) | Issue | closed | 2025-08-26T12:30:08Z |  | type: question/answered | How to make an image suitable for kunminghu? |
| [#4775](https://github.com/OpenXiangShan/XiangShan/issues/4775) | Issue | closed | 2025-08-26T12:30:07Z |  | type: question/answered | How to prototype latest Nanhu and Kunminghu versions on FPGA? |
| [#4756](https://github.com/OpenXiangShan/XiangShan/issues/4756) | Issue | closed | 2025-08-26T12:30:06Z |  | type: question/answered | Kunminghu V3 related  Question |
| [#4739](https://github.com/OpenXiangShan/XiangShan/issues/4739) | Issue | closed | 2025-08-26T12:30:05Z |  | type: question/answered | How to evaluate the actual execution time of a program |
| [#4651](https://github.com/OpenXiangShan/XiangShan/issues/4651) | Issue | closed | 2025-08-26T12:30:04Z |  | type: question/answered | Metals unsupported Scala 2.13.15 |
| [#4459](https://github.com/OpenXiangShan/XiangShan/issues/4459) | Issue | closed | 2025-08-26T12:30:03Z |  | type: question/answered | Regarding the version issue of Vivado |
| [#4365](https://github.com/OpenXiangShan/XiangShan/issues/4365) | Issue | closed | 2025-08-26T12:30:01Z |  | type: question/answered | Address mapping question about workloads under AM environment |
| [#4323](https://github.com/OpenXiangShan/XiangShan/issues/4323) | Issue | closed | 2025-08-26T12:30:00Z |  | type: question/answered | Phenomenon after loading mirroring |
| [#4243](https://github.com/OpenXiangShan/XiangShan/issues/4243) | Issue | closed | 2025-08-26T12:29:58Z |  | type: question/answered | change the length of GPRS, all more control registers |
| [#4043](https://github.com/OpenXiangShan/XiangShan/issues/4043) | Issue | closed | 2025-08-26T12:29:56Z | exception | type: question/answered | Address misaligned and access fault question |
| [#4029](https://github.com/OpenXiangShan/XiangShan/issues/4029) | Issue | closed | 2025-08-26T12:29:55Z |  | type: question/answered | Is this condition redundant regarding the code details? |
| [#3973](https://github.com/OpenXiangShan/XiangShan/issues/3973) | Issue | closed | 2025-08-26T12:29:53Z |  | type: question/answered | Shlcofideleg extension question |
| [#3368](https://github.com/OpenXiangShan/XiangShan/issues/3368) | Issue | closed | 2025-08-26T12:29:48Z |  | type: question/answered | Is there any tutorial on how to run XiangShan RISC-V Processor on S2C's vu19p? |
| [#4958](https://github.com/OpenXiangShan/XiangShan/issues/4958) | Issue | closed | 2025-08-25T10:42:32Z |  | type: bug/invalid | Mismatch between Xiangshan and NEMU |
| [#4973](https://github.com/OpenXiangShan/XiangShan/issues/4973) | Issue | closed | 2025-08-25T10:42:18Z | exception | type: bug/invalid | Mismatch between Xiangshan and NEMU in a random generated program |
| [#4951](https://github.com/OpenXiangShan/XiangShan/issues/4951) | Issue | closed | 2025-08-18T07:57:01Z |  | type: bug/invalid | Mismatch when access  hcontext(scontext, mcontext) between Xiangshan and NEMU |
| [#4950](https://github.com/OpenXiangShan/XiangShan/issues/4950) | Issue | closed | 2025-08-18T07:57:00Z |  | type: bug/invalid | Mismatch at access hcontext(scontext, mcontext) between Xiangshan and NEMU |
| [#4948](https://github.com/OpenXiangShan/XiangShan/issues/4948) | Issue | closed | 2025-08-18T07:56:59Z | exception | type: bug/invalid | Mismatch at pc = 0x0080000344 between Xiangshan and NEMU |
| [#4947](https://github.com/OpenXiangShan/XiangShan/issues/4947) | Issue | closed | 2025-08-18T07:55:38Z |  | type: bug/invalid | Mismatch at  csrrwi between Xiangshan and NEMU |
| [#2498](https://github.com/OpenXiangShan/XiangShan/issues/2498) | Issue | closed | 2025-07-10T08:17:42Z |  |  | Questions about Synthesis |
| [#4868](https://github.com/OpenXiangShan/XiangShan/issues/4868) | Issue | closed | 2025-07-07T06:12:12Z |  | type: bug/reported | riscv-rootfs: fatal error: libfdt.h: No such file or directory |
| [#4581](https://github.com/OpenXiangShan/XiangShan/issues/4581) | Issue | closed | 2025-05-07T15:04:34Z |  | type: bug/reported | Inconsistent `vsiselect` register range. |
| [#4637](https://github.com/OpenXiangShan/XiangShan/issues/4637) | Issue | closed | 2025-04-28T07:51:05Z |  | type: bug/reported | NEMU incorrectly allows sc.w to succeed after address change |
| [#4574](https://github.com/OpenXiangShan/XiangShan/issues/4574) | Issue | closed | 2025-04-21T07:47:12Z |  | type: bug/reported | Debug register `tdata1` mismatch |
| [#4577](https://github.com/OpenXiangShan/XiangShan/issues/4577) | Issue | closed | 2025-04-21T07:14:49Z |  | type: bug/reported | Mismatch in `sc.w` Instruction After `lr.w` |
| [#4575](https://github.com/OpenXiangShan/XiangShan/issues/4575) | Issue | closed | 2025-04-21T07:01:00Z |  | type: bug/reported | `amomin.w` Instruction Behavior Inconsistency |
| [#4582](https://github.com/OpenXiangShan/XiangShan/issues/4582) | Issue | closed | 2025-04-21T03:12:38Z |  | type: bug/reported | Inconsistent `siselect` register range. |
| [#4549](https://github.com/OpenXiangShan/XiangShan/issues/4549) | Issue | closed | 2025-04-15T01:37:21Z |  |  | Mismatch in `medeleg` bit 4 between XiangShan and NEMU |
| [#4547](https://github.com/OpenXiangShan/XiangShan/issues/4547) | Issue | closed | 2025-04-14T08:02:18Z |  |  | Mismatch in `mstatus` bit 42 after `ecall` or `ebreak` between XiangShan and NEMU |
| [#4548](https://github.com/OpenXiangShan/XiangShan/issues/4548) | Issue | closed | 2025-04-14T07:55:30Z | interrupt |  | Mismatch in write `mip` between XiangShan and NEMU |
| [#4550](https://github.com/OpenXiangShan/XiangShan/issues/4550) | Issue | closed | 2025-04-14T07:54:59Z |  |  | Mismatch in FS field of `sstatus` and `mstatus` after executing `fcvt.w.s` |
| [#4398](https://github.com/OpenXiangShan/XiangShan/issues/4398) | Issue | closed | 2025-03-19T07:01:34Z | exception | type: bug/invalid | XiangShan and NEMU show inconsistencies when executing `amoswap.w` |
| [#4385](https://github.com/OpenXiangShan/XiangShan/issues/4385) | Issue | closed | 2025-03-19T07:00:14Z | exception | type: bug/invalid | `sltiu` instruction bug in NEMU |
| [#4399](https://github.com/OpenXiangShan/XiangShan/issues/4399) | Issue | closed | 2025-03-19T06:59:59Z |  | type: bug/invalid | NEMU executes the `ori` instruction incorrectly. |
| [#4402](https://github.com/OpenXiangShan/XiangShan/issues/4402) | Issue | closed | 2025-03-19T06:59:49Z | exception | type: bug/invalid | PC jump back followed by memory access triggers register errors |
| [#4401](https://github.com/OpenXiangShan/XiangShan/issues/4401) | Issue | closed | 2025-03-12T09:35:43Z |  | type: bug/invalid | NEMU has a problem when executing the `addi` instruction. |
| [#4400](https://github.com/OpenXiangShan/XiangShan/issues/4400) | Issue | closed | 2025-03-12T05:38:22Z |  | type: bug/invalid | The value read from the `sscratch` register is inconsistent. |
| [#4388](https://github.com/OpenXiangShan/XiangShan/issues/4388) | Issue | closed | 2025-03-12T00:54:52Z | exception | type: bug/reported | Difference in the upper 16 bits of the PC being zero |
| [#3952](https://github.com/OpenXiangShan/XiangShan/issues/3952) | Issue | closed | 2025-01-31T09:14:07Z |  | type: bug/reported, type: question/answered | fadd.h instruction operation results are different |
| [#3951](https://github.com/OpenXiangShan/XiangShan/issues/3951) | Issue | closed | 2025-01-31T09:13:25Z |  | type: bug/reported, type: question/answered | Sign bit handling error |
| [#4129](https://github.com/OpenXiangShan/XiangShan/issues/4129) | Issue | closed | 2025-01-06T03:17:57Z | exception | type: bug/reported | Unexpected behavior with LUI and FLD instructions on zero register |
| [#4046](https://github.com/OpenXiangShan/XiangShan/issues/4046) | Issue | closed | 2024-12-16T16:21:18Z |  | type: bug/reported | mstatus.sdt has different |
| [#4020](https://github.com/OpenXiangShan/XiangShan/issues/4020) | Issue | closed | 2024-12-12T06:23:07Z | exception | type: bug/reported | Certain instructions cannot cause exceptions |
| [#3979](https://github.com/OpenXiangShan/XiangShan/issues/3979) | Issue | closed | 2024-12-11T03:40:48Z |  | type: bug/reported | `flh` instruction does not perform sign extension |
| [#3959](https://github.com/OpenXiangShan/XiangShan/issues/3959) | Issue | closed | 2024-12-02T10:00:21Z | exception | type: bug/reported | Unable to Handle Specific Sequences of Illegal Instructions |
| [#3934](https://github.com/OpenXiangShan/XiangShan/issues/3934) | Issue | closed | 2024-11-28T09:13:17Z |  | type: bug/reported | Unexpected Modification of `xstatus` WPRI Field During `menvcfg` Reads/Writes |
| [#852](https://github.com/OpenXiangShan/XiangShan/issues/852) | Issue | closed | 2024-11-21T13:17:37Z |  |  | make emu error |
| [#3860](https://github.com/OpenXiangShan/XiangShan/issues/3860) | Issue | closed | 2024-11-15T03:00:56Z | exception | type: bug/reported | Wrong `mstatus, mtval` value when Xiangshan executes an illegal instruction. |
| [#3864](https://github.com/OpenXiangShan/XiangShan/issues/3864) | Issue | closed | 2024-11-14T08:48:50Z | exception |  | When FS is 0 and a fld/fsd specific instruction causes an exception, there is a problem with the value of the mtval register |
| [#3846](https://github.com/OpenXiangShan/XiangShan/issues/3846) | Issue | closed | 2024-11-14T02:08:36Z |  |  | sc.w Instruction Returns 0 When Operated on Different Memory Addresses, Indicating Incorrect Success Status |
| [#3839](https://github.com/OpenXiangShan/XiangShan/issues/3839) | Issue | closed | 2024-11-08T08:01:07Z | exception | type: bug/reported | When the fs field in the mstatus register is 0, executing instructions such as flh will not cause an illegal instruction exception |
| [#3723](https://github.com/OpenXiangShan/XiangShan/issues/3723) | Issue | closed | 2024-10-25T09:07:06Z |  | type: bug/reported | `csrc` instr get wrong `mstatus` value |
| [#3709](https://github.com/OpenXiangShan/XiangShan/issues/3709) | Issue | closed | 2024-10-13T06:01:43Z |  | type: bug/reported | D extension instr `fle.d` bug. |
| [#3587](https://github.com/OpenXiangShan/XiangShan/issues/3587) | Issue | closed | 2024-09-16T02:50:53Z |  | type: bug/reported | reading fflags changes the status |
| [#3438](https://github.com/OpenXiangShan/XiangShan/issues/3438) | Issue | closed | 2024-08-28T07:56:06Z |  |  | I have read the RISC-V ISA Manual and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集相关的问题。 |
| [#2961](https://github.com/OpenXiangShan/XiangShan/issues/2961) | Issue | closed | 2024-05-13T02:09:30Z |  | type: bug/reported | Can not generate RTL when NUM_CORES >= 3 |
| [#2602](https://github.com/OpenXiangShan/XiangShan/issues/2602) | Issue | closed | 2024-03-15T04:26:01Z | exception |  | "Java heap space" issue when using virtual machine to compile FPGA prototype |
| [#2668](https://github.com/OpenXiangShan/XiangShan/issues/2668) | Issue | closed | 2024-03-15T01:43:18Z |  |  | About getting the 128 bit floating-point function module. |
| [#2767](https://github.com/OpenXiangShan/XiangShan/issues/2767) | Issue | closed | 2024-03-13T02:17:06Z |  | type: bug/reported | Incorrect Rounding Mode Handling for Specific Cases |
| [#2731](https://github.com/OpenXiangShan/XiangShan/issues/2731) | Issue | closed | 2024-03-03T00:48:51Z |  |  | suffix missing in python code |
| [#1543](https://github.com/OpenXiangShan/XiangShan/issues/1543) | Issue | closed | 2024-02-26T07:25:29Z |  |  | 在 Linux Kernel 跑 Spec2006 的问题 |
| [#2693](https://github.com/OpenXiangShan/XiangShan/issues/2693) | Issue | closed | 2024-02-03T04:48:03Z |  |  | S |
| [#2682](https://github.com/OpenXiangShan/XiangShan/issues/2682) | Issue | closed | 2024-01-30T10:17:56Z |  |  | Got an error when compiling xiangshan emu |
| [#2681](https://github.com/OpenXiangShan/XiangShan/issues/2681) | Issue | closed | 2024-01-30T07:10:26Z |  |  | How can I conveniently add Xiangshan as a submodule?（怎么方便的将xiangshan作为子模块） |
| [#2679](https://github.com/OpenXiangShan/XiangShan/issues/2679) | Issue | closed | 2024-01-30T03:28:17Z |  |  | Could you please give me some speed data about difftest-NanhuG? |
| [#2666](https://github.com/OpenXiangShan/XiangShan/issues/2666) | Issue | closed | 2024-01-29T01:30:53Z |  |  | How to flush dirty datas to the Memory？ |
| [#2661](https://github.com/OpenXiangShan/XiangShan/issues/2661) | Issue | closed | 2024-01-23T06:44:04Z |  |  | About P extension of case generation and nemu simulator |
| [#2664](https://github.com/OpenXiangShan/XiangShan/issues/2664) | Issue | closed | 2024-01-23T01:29:57Z |  |  | F |
| [#2638](https://github.com/OpenXiangShan/XiangShan/issues/2638) | Issue | closed | 2024-01-16T07:17:24Z |  |  | VCS Simulatrion Run Err |
| [#2637](https://github.com/OpenXiangShan/XiangShan/issues/2637) | Issue | closed | 2024-01-12T10:22:06Z |  |  | VCS Simulation cfg |
| [#2545](https://github.com/OpenXiangShan/XiangShan/issues/2545) | Issue | closed | 2024-01-05T17:04:10Z |  |  | Errors Occurred While Building BBL and Linking Kernel |
| [#2601](https://github.com/OpenXiangShan/XiangShan/issues/2601) | Issue | closed | 2023-12-29T03:46:34Z |  |  | Issue encountered when compiling FPGA prototype following the official document |
| [#2587](https://github.com/OpenXiangShan/XiangShan/issues/2587) | Issue | closed | 2023-12-26T07:19:10Z |  |  | About the meaning of programs in 'ready-to-run' |
| [#2580](https://github.com/OpenXiangShan/XiangShan/issues/2580) | Issue | closed | 2023-12-23T06:22:56Z |  |  | Is there any plan to release the source code supporting open source eda flow? |
| [#2561](https://github.com/OpenXiangShan/XiangShan/issues/2561) | Issue | closed | 2023-12-22T06:57:25Z |  |  | L2Prefetch parameter Uninitialized |
| [#2551](https://github.com/OpenXiangShan/XiangShan/issues/2551) | Issue | closed | 2023-12-21T07:21:57Z |  |  | about address map |
| [#2543](https://github.com/OpenXiangShan/XiangShan/issues/2543) | Issue | closed | 2023-12-15T06:46:51Z |  |  | question about benchmark |
| [#2548](https://github.com/OpenXiangShan/XiangShan/issues/2548) | Issue | closed | 2023-12-14T09:47:36Z |  |  | Unexpected Behavior in LR/SC Sequence with Branch Prediction Errors |
| [#2544](https://github.com/OpenXiangShan/XiangShan/issues/2544) | Issue | closed | 2023-12-14T04:24:13Z | exception, interrupt |  | Unexpected Behavior When Executing 'mret' Instruction |
| [#2205](https://github.com/OpenXiangShan/XiangShan/issues/2205) | Issue | closed | 2023-12-13T08:18:28Z |  |  | Should parameter be changed for dual-core or quad-core design of XiangShan when `make emu CONFIG=MinimalConfig EMU_TRACE=1 -j32`? |
| [#2257](https://github.com/OpenXiangShan/XiangShan/issues/2257) | Issue | closed | 2023-12-13T08:12:03Z | exception |  | `make verilog -j9 NUM_CORES=2` fail |
| [#2313](https://github.com/OpenXiangShan/XiangShan/issues/2313) | Issue | closed | 2023-12-13T07:55:54Z |  |  | Error when running Debian in NEMU |
| [#2403](https://github.com/OpenXiangShan/XiangShan/issues/2403) | Issue | closed | 2023-12-13T07:55:04Z |  |  | make riscv-pk ERROR: ../machine/dtb.S:5: Error: file not found: system.dtb |
| [#2214](https://github.com/OpenXiangShan/XiangShan/issues/2214) | Issue | closed | 2023-12-13T07:47:24Z |  |  | 请问MinimalConfig配置有推荐的FPGA开发板型号吗？ |
| [#2309](https://github.com/OpenXiangShan/XiangShan/issues/2309) | Issue | closed | 2023-12-13T07:47:03Z |  |  | 请问SDCARD_IMAGE这个文件的格式要求 |
| [#2475](https://github.com/OpenXiangShan/XiangShan/issues/2475) | Issue | closed | 2023-12-13T07:43:30Z |  |  | VCS Simulation Error: Can not open 'ram.bin' |
| [#2446](https://github.com/OpenXiangShan/XiangShan/issues/2446) | Issue | closed | 2023-12-13T07:39:16Z |  |  | FPGA minimal prototype |
| [#2510](https://github.com/OpenXiangShan/XiangShan/issues/2510) | Issue | closed | 2023-12-13T07:38:54Z |  |  | a question about spec cpu 2006 in Nanhu |
| [#2529](https://github.com/OpenXiangShan/XiangShan/issues/2529) | Issue | closed | 2023-12-13T07:38:37Z |  |  | About GDS Files |
| [#2506](https://github.com/OpenXiangShan/XiangShan/issues/2506) | Issue | closed | 2023-11-26T04:01:33Z |  |  | How to print the information from XSDebug in Xiangshan |
| [#2495](https://github.com/OpenXiangShan/XiangShan/issues/2495) | Issue | closed | 2023-11-24T02:45:38Z |  |  | what's the version of qt6 in PerfUI? I can't use this tool , I use qt6.6 in my ubuntu 20.04 ,but it gives me this error |
| [#337](https://github.com/OpenXiangShan/XiangShan/issues/337) | Issue | closed | 2023-11-23T10:43:01Z | exception |  | 关于 `src/test/csrc/main.cpp` 里面返回值的问题 |
| [#100](https://github.com/OpenXiangShan/XiangShan/issues/100) | Issue | closed | 2023-11-23T10:41:54Z |  | type: feature/planned | 对飞线的管理 |
| [#30](https://github.com/OpenXiangShan/XiangShan/issues/30) | Issue | closed | 2023-11-23T10:38:17Z |  | type: feature/planned | logutils: 需要一些改进 |
| [#16](https://github.com/OpenXiangShan/XiangShan/issues/16) | Issue | closed | 2023-11-23T10:33:36Z |  |  | ExuIO相关的架构需要更新 |
| [#2492](https://github.com/OpenXiangShan/XiangShan/issues/2492) | Issue | closed | 2023-11-22T18:44:18Z | exception |  | "requirement failed" when building emu with MinimalSimConfig |
| [#1757](https://github.com/OpenXiangShan/XiangShan/issues/1757) | Issue | closed | 2023-11-15T08:48:41Z |  |  | emu运行测试用例的波形，为何时钟没变化、所有信号都当做wire？ |
| [#2451](https://github.com/OpenXiangShan/XiangShan/issues/2451) | Issue | closed | 2023-11-06T01:18:27Z |  |  | make verilog fail (xiangshan[chisel3].runMain subprocess failed make: *** [Makefile:117: build/XSTop.v] Error 1) |
| [#2417](https://github.com/OpenXiangShan/XiangShan/issues/2417) | Issue | closed | 2023-10-30T06:55:53Z |  |  | Missing header files in constantin.cpp |
| [#2438](https://github.com/OpenXiangShan/XiangShan/issues/2438) | Issue | closed | 2023-10-30T06:46:21Z |  |  | 关于DRAMsim3的用法 |
| [#2421](https://github.com/OpenXiangShan/XiangShan/issues/2421) | Issue | closed | 2023-10-30T06:39:06Z |  |  | 关于香山代码中的device |
| [#1453](https://github.com/OpenXiangShan/XiangShan/issues/1453) | Issue | closed | 2023-10-21T08:30:54Z |  |  | 吐槽:真是内存大户啊 |
| [#1519](https://github.com/OpenXiangShan/XiangShan/issues/1519) | Issue | closed | 2023-10-21T03:06:07Z |  |  | how to simulate the DRAM time and power? |
| [#1837](https://github.com/OpenXiangShan/XiangShan/issues/1837) | Issue | closed | 2023-10-21T03:04:40Z |  |  | LRSC匹配失败问题 |
| [#931](https://github.com/OpenXiangShan/XiangShan/issues/931) | Issue | closed | 2023-10-21T03:02:06Z |  |  | full install step share |
| [#1187](https://github.com/OpenXiangShan/XiangShan/issues/1187) | Issue | closed | 2023-10-21T03:01:00Z |  | type: feature/planned | Steps to get .bin files from c code. |
| [#839](https://github.com/OpenXiangShan/XiangShan/issues/839) | Issue | closed | 2023-10-21T03:00:38Z |  |  | 我是Java程序员 如何做贡献 |
| [#834](https://github.com/OpenXiangShan/XiangShan/issues/834) | Issue | closed | 2023-10-21T02:56:54Z |  |  | 请问有没有啥群组方便交流学习的？ |
| [#529](https://github.com/OpenXiangShan/XiangShan/issues/529) | Issue | closed | 2023-10-21T02:54:05Z |  | type: feature/planned | 分支预测优化点 |
| [#2392](https://github.com/OpenXiangShan/XiangShan/issues/2392) | Issue | closed | 2023-10-20T07:12:01Z |  |  | 请教关于chisel3.0升级到chisel6.0 #2372 |
| [#2368](https://github.com/OpenXiangShan/XiangShan/issues/2368) | Issue | closed | 2023-10-17T03:02:11Z | exception |  | 为什么mmio需要等待执行 |
| [#1992](https://github.com/OpenXiangShan/XiangShan/issues/1992) | Issue | closed | 2023-10-11T07:53:34Z |  |  | Add support for Chisel 5.0 and CIRCT |
| [#2364](https://github.com/OpenXiangShan/XiangShan/issues/2364) | Issue | closed | 2023-10-10T09:14:27Z |  |  | Diplomacy参数协商在核内微观设计中的疑虑 |
| [#2345](https://github.com/OpenXiangShan/XiangShan/issues/2345) | Issue | closed | 2023-10-08T11:57:23Z |  |  | DiffTest如何支持SOC级验证，目前看香山重点利用DiffTest验证Core的功能，后续是否考虑拓展到整个SOC |
| [#2349](https://github.com/OpenXiangShan/XiangShan/issues/2349) | Issue | closed | 2023-10-07T03:30:32Z |  |  | Consider Providing an Alternative Project Host/Mirror |
| [#2073](https://github.com/OpenXiangShan/XiangShan/issues/2073) | Issue | closed | 2023-09-30T02:34:59Z |  |  | Questions about generating checkpoints |
| [#2339](https://github.com/OpenXiangShan/XiangShan/issues/2339) | Issue | closed | 2023-09-26T07:05:28Z |  |  | 在Verilator中编译香山时出现报错 |
| [#2281](https://github.com/OpenXiangShan/XiangShan/issues/2281) | Issue | closed | 2023-09-15T02:14:52Z |  |  | Question about run programs in linux kernel on fpga |
| [#2287](https://github.com/OpenXiangShan/XiangShan/issues/2287) | Issue | closed | 2023-09-08T03:13:08Z |  |  | 请教tllogger的用法 |
| [#2175](https://github.com/OpenXiangShan/XiangShan/issues/2175) | Issue | closed | 2023-08-18T11:01:49Z |  |  | VCS仿真出错，是源代码的问题还是操作的问题？ |
| [#2212](https://github.com/OpenXiangShan/XiangShan/issues/2212) | Issue | closed | 2023-08-08T02:47:01Z |  |  | 更换FPGA型号 XDMA不支持该芯片 |
| [#2220](https://github.com/OpenXiangShan/XiangShan/issues/2220) | Issue | closed | 2023-08-08T02:03:54Z |  |  | 关于FPGA Prototype的一个疑问 |
| [#2209](https://github.com/OpenXiangShan/XiangShan/issues/2209) | Issue | closed | 2023-08-01T01:28:43Z |  |  | Broken `build.sc` for Mill 0.11.0 and newer versions |
| [#2190](https://github.com/OpenXiangShan/XiangShan/issues/2190) | Issue | closed | 2023-07-21T10:31:51Z |  |  | Modify Multiplier.scala to generate pure combination multiplier instead of a time series multiplier. |
| [#2182](https://github.com/OpenXiangShan/XiangShan/issues/2182) | Issue | closed | 2023-07-21T01:42:22Z |  |  | array_0_ext.v 生成的深度会在32和128之间切换 |
| [#2163](https://github.com/OpenXiangShan/XiangShan/issues/2163) | Issue | closed | 2023-07-17T05:38:53Z |  |  | make simv 提示BankConflict0Writer.v文件第28行，Syntax error.token is ';' parameter string site; |
| [#2170](https://github.com/OpenXiangShan/XiangShan/issues/2170) | Issue | closed | 2023-07-09T01:49:54Z |  |  | # Find the repo in the git diff and then set it to an env variables.   REPO_TO_LINT=$(           git diff origin/main -- readme.md \|           # Look for changes (indicated by lines starting with +).           grep ^+ \|           # Get the line that includes the readme.           grep -Eo 'https.*#readme' \|           # Get just the URL.           sed 's/#readme//')      # If there's no repo found, exit quietly.   if [ -z "$REPO_TO_LINT" ]; then           echo "No new link found in the format:  https://....#readme"   else           echo "Cloning $REPO_TO_LINT"           mkdir cloned           cd cloned           git clone "$REPO_TO_LINT" .           npx awesome-lint   fi |
| [#2128](https://github.com/OpenXiangShan/XiangShan/issues/2128) | Issue | closed | 2023-06-10T07:59:18Z |  |  | Error occurred when "make" for the coremark workload |
| [#1821](https://github.com/OpenXiangShan/XiangShan/issues/1821) | Issue | closed | 2023-05-30T16:54:22Z |  |  | 请教一下，Synthesis后IOB过高，如何降低IOB的使用率 |
| [#2066](https://github.com/OpenXiangShan/XiangShan/issues/2066) | Issue | closed | 2023-05-13T02:36:08Z |  |  | about generatePerfEvent |
| [#2070](https://github.com/OpenXiangShan/XiangShan/issues/2070) | Issue | closed | 2023-05-06T07:59:33Z |  |  | There should be record in  XiangShan/.gitmodules for mill ; In XiangShan/tools path (Not a really bug) |
| [#2026](https://github.com/OpenXiangShan/XiangShan/issues/2026) | Issue | closed | 2023-04-23T01:33:33Z |  |  | RISC-V工具链部署官方文档建议 |
| [#1976](https://github.com/OpenXiangShan/XiangShan/issues/1976) | Issue | closed | 2023-04-19T09:32:06Z |  |  | 请教一个关于香山串口输入的问题 |
| [#1974](https://github.com/OpenXiangShan/XiangShan/issues/1974) | Issue | closed | 2023-04-18T11:26:54Z |  |  | 请问昆明湖对应于哪个分支？ |
| [#2011](https://github.com/OpenXiangShan/XiangShan/issues/2011) | Issue | closed | 2023-04-04T14:52:18Z |  |  | Dispatch2Rs 的 LessExu 实现BUG |
| [#2019](https://github.com/OpenXiangShan/XiangShan/issues/2019) | Issue | closed | 2023-04-04T03:02:26Z |  |  | 请教浮点运算部件的实现细节和设计考虑 |
| [#2016](https://github.com/OpenXiangShan/XiangShan/issues/2016) | Issue | closed | 2023-04-01T08:34:55Z |  |  | Cannot run program "firtool": error=2, No such file or directory |
| [#2007](https://github.com/OpenXiangShan/XiangShan/issues/2007) | Issue | closed | 2023-03-31T02:38:46Z |  |  | 香山的pec2006跑分相关 |
| [#1980](https://github.com/OpenXiangShan/XiangShan/issues/1980) | Issue | closed | 2023-03-20T16:15:57Z |  |  | 请问南湖在vu19p上面大概要用掉多少资源 |
| [#1948](https://github.com/OpenXiangShan/XiangShan/issues/1948) | Issue | closed | 2023-03-13T03:42:24Z |  |  | 请问Simulation用的SPEC workload是Linux下运行的，还是非OS环境的裸机程序？ |
| [#1940](https://github.com/OpenXiangShan/XiangShan/issues/1940) | Issue | closed | 2023-03-11T05:15:34Z | exception |  | 关于浮点动态舍入模式的几个疑问 |
| [#1427](https://github.com/OpenXiangShan/XiangShan/issues/1427) | Issue | closed | 2023-02-15T03:37:07Z |  |  | PRINTF_COND里面的那些debug信息，如何让他们打印出来？ |
| [#1491](https://github.com/OpenXiangShan/XiangShan/issues/1491) | Issue | closed | 2023-02-15T03:33:00Z |  |  | The latest version of XiangShan,包括哪些？ |
| [#1624](https://github.com/OpenXiangShan/XiangShan/issues/1624) | Issue | closed | 2023-02-15T03:32:15Z |  |  | Enquiry about Synthesis or even Layout flow/scripts |
| [#995](https://github.com/OpenXiangShan/XiangShan/issues/995) | Issue | closed | 2023-02-15T03:31:46Z |  |  | XiangShan.test.runMain subprocess failed |
| [#968](https://github.com/OpenXiangShan/XiangShan/issues/968) | Issue | closed | 2023-02-15T03:30:18Z |  |  | error with "make emu"  with verilator 4.104 |
| [#1034](https://github.com/OpenXiangShan/XiangShan/issues/1034) | Issue | closed | 2023-02-15T03:28:13Z |  |  | 请教关于roq的两个问题。 |
| [#1490](https://github.com/OpenXiangShan/XiangShan/issues/1490) | Issue | closed | 2023-02-15T03:27:00Z |  |  | 有没有定点的DSP ? |
| [#1046](https://github.com/OpenXiangShan/XiangShan/issues/1046) | Issue | closed | 2023-02-15T03:26:43Z |  |  | Verification flow |
| [#1259](https://github.com/OpenXiangShan/XiangShan/issues/1259) | Issue | closed | 2023-02-15T03:23:34Z | exception |  | Abort due to mismatch after latest pull |
| [#1460](https://github.com/OpenXiangShan/XiangShan/issues/1460) | Issue | closed | 2023-02-15T03:22:48Z |  |  | Questa flow for XiangShan |
| [#1510](https://github.com/OpenXiangShan/XiangShan/issues/1510) | Issue | closed | 2023-02-15T03:20:40Z |  |  | Emu failed |
| [#1529](https://github.com/OpenXiangShan/XiangShan/issues/1529) | Issue | closed | 2023-02-15T03:20:12Z |  |  | 请问ready-to-run目录下自带的coremark-2-iteration.bin是在什么环境下编译的？ |
| [#1532](https://github.com/OpenXiangShan/XiangShan/issues/1532) | Issue | closed | 2023-02-15T03:19:27Z |  |  | 有没有可能通过SimTop.v或者XSTop.v配以一个ram.v来进行最小化的RTL仿真？ |
| [#1520](https://github.com/OpenXiangShan/XiangShan/issues/1520) | Issue | closed | 2023-02-15T03:18:55Z |  |  | Post video showing an implementation of this CPU |
| [#1526](https://github.com/OpenXiangShan/XiangShan/issues/1526) | Issue | closed | 2023-02-15T03:18:38Z |  |  | 如何更改香山 verilator emu 的仿真频率到 2GHz |
| [#1531](https://github.com/OpenXiangShan/XiangShan/issues/1531) | Issue | closed | 2023-02-15T03:18:27Z |  |  | email list 惨遭退信 |
| [#1528](https://github.com/OpenXiangShan/XiangShan/issues/1528) | Issue | closed | 2023-02-15T03:18:14Z |  |  | 请问有没有类似果壳的debug信息打印出来pc、npc等信息（$fwrite(32'h80000002）？ |
| [#1554](https://github.com/OpenXiangShan/XiangShan/issues/1554) | Issue | closed | 2023-02-15T03:18:00Z |  |  | 有没有DSP指令集？  |
| [#1555](https://github.com/OpenXiangShan/XiangShan/issues/1555) | Issue | closed | 2023-02-15T03:17:43Z |  |  | 请问SimTop.v 模块里的一些SRAMTemplate模块的功能是什么？ 我把array_*_ext.v模块的输出全部置为0，功能也正常。 |
| [#1534](https://github.com/OpenXiangShan/XiangShan/issues/1534) | Issue | closed | 2023-02-15T03:17:10Z |  |  | 你们一般怎么调试功能错误啊？ |
| [#1535](https://github.com/OpenXiangShan/XiangShan/issues/1535) | Issue | closed | 2023-02-15T03:16:58Z |  |  | microbench.bin在果壳和香山的文件不一样，是有什么原因吗？ |
| [#1572](https://github.com/OpenXiangShan/XiangShan/issues/1572) | Issue | closed | 2023-02-15T03:16:09Z |  |  | Do you have any plan to add deep learning acceleralor feature.  |
| [#1577](https://github.com/OpenXiangShan/XiangShan/issues/1577) | Issue | closed | 2023-02-15T03:15:52Z |  |  | Chisel 之外的另一个硬件设计语言 |
| [#1710](https://github.com/OpenXiangShan/XiangShan/issues/1710) | Issue | closed | 2023-02-15T03:13:25Z |  |  | 生成 vcs simv 时，不带 difftest 跑用例，如何自动结束仿真 |
| [#1749](https://github.com/OpenXiangShan/XiangShan/issues/1749) | Issue | closed | 2023-02-15T03:13:07Z |  |  | 关于L2/L3的端口位宽 |
| [#1777](https://github.com/OpenXiangShan/XiangShan/issues/1777) | Issue | closed | 2023-02-15T03:12:34Z |  |  | coremark配置 |
| [#1788](https://github.com/OpenXiangShan/XiangShan/issues/1788) | Issue | closed | 2023-02-15T03:12:23Z |  |  | 请问香山支持4core吗？ |
| [#1797](https://github.com/OpenXiangShan/XiangShan/issues/1797) | Issue | closed | 2023-02-15T03:12:13Z |  |  | 缓存分slice后，请求是如何根据地址路由到不同的slice中的？ |
| [#1864](https://github.com/OpenXiangShan/XiangShan/issues/1864) | Issue | closed | 2023-02-15T03:11:53Z |  |  | 执行./build/emu -b 0 -e 1 -i ./ready-to-run/microbench.bin出现错误，是主机内存太小了吗 |
| [#1869](https://github.com/OpenXiangShan/XiangShan/issues/1869) | Issue | closed | 2023-02-15T03:11:22Z |  |  | 关于dcache/icache中0x5c0-0x5FF，这几个自定义读写寄存器是如何设置的？ |
| [#1807](https://github.com/OpenXiangShan/XiangShan/issues/1807) | Issue | closed | 2023-02-11T15:41:46Z |  |  | Make verilog error |
| [#1892](https://github.com/OpenXiangShan/XiangShan/issues/1892) | Issue | closed | 2023-02-11T15:40:49Z |  |  | Dispatch2rs 的算法需要一点提点 |
| [#1863](https://github.com/OpenXiangShan/XiangShan/issues/1863) | Issue | closed | 2023-02-04T02:39:20Z |  |  | 香山项目是否有chisel版本兼容性的说明？ |
| [#1884](https://github.com/OpenXiangShan/XiangShan/issues/1884) | Issue | closed | 2023-02-02T12:23:46Z | exception |  | 请教关于异常委托的问题 |
| [#1413](https://github.com/OpenXiangShan/XiangShan/issues/1413) | Issue | closed | 2022-11-23T06:50:41Z |  |  | 请问调试香山处理器需要什么型号fpga开发板，或者是对fpga开发板有什么资源要求 |
| [#1830](https://github.com/OpenXiangShan/XiangShan/issues/1830) | Issue | closed | 2022-11-18T00:59:53Z |  |  | TileLink比AHB5有哪些优势？ |
| [#1798](https://github.com/OpenXiangShan/XiangShan/issues/1798) | Issue | closed | 2022-10-22T07:26:21Z |  |  | 请问香山串口的输入信号的作用是什么？如何才能灌激励进去？ |
| [#1794](https://github.com/OpenXiangShan/XiangShan/issues/1794) | Issue | closed | 2022-10-10T08:00:35Z |  |  | make verilog erro |
| [#1796](https://github.com/OpenXiangShan/XiangShan/issues/1796) | Issue | closed | 2022-10-10T08:00:18Z |  |  | make verilog erro |
| [#1774](https://github.com/OpenXiangShan/XiangShan/issues/1774) | Issue | closed | 2022-09-08T04:35:49Z |  |  | make emu error |
| [#1770](https://github.com/OpenXiangShan/XiangShan/issues/1770) | Issue | closed | 2022-09-06T09:36:50Z |  |  | Make emu error |
| [#1485](https://github.com/OpenXiangShan/XiangShan/issues/1485) | Issue | closed | 2022-05-14T08:55:45Z |  |  | 为什么没看到NPU ? |
| [#1549](https://github.com/OpenXiangShan/XiangShan/issues/1549) | Issue | closed | 2022-05-12T13:04:20Z |  |  | perf model? |
| [#1541](https://github.com/OpenXiangShan/XiangShan/issues/1541) | Issue | closed | 2022-05-04T10:33:15Z |  |  | 请问flash.cpp中的0x01f292930010029b和0x00028067是有什么特别的作用吗？ |
| [#1539](https://github.com/OpenXiangShan/XiangShan/issues/1539) | Issue | closed | 2022-05-01T05:44:27Z |  |  | Generate verilog through CIRCT |
| [#1524](https://github.com/OpenXiangShan/XiangShan/issues/1524) | Issue | closed | 2022-04-20T03:45:27Z |  |  | 关于编译RV64GCB 工具链失败的问题请教 |
| [#1521](https://github.com/OpenXiangShan/XiangShan/issues/1521) | Issue | closed | 2022-04-11T06:02:14Z |  |  | 请问南湖coremark跑分7.81对应哪个分支？ |
| [#1495](https://github.com/OpenXiangShan/XiangShan/issues/1495) | Issue | closed | 2022-03-24T06:59:27Z |  |  | 微架构仿真预研 |
| [#963](https://github.com/OpenXiangShan/XiangShan/issues/963) | Issue | closed | 2022-03-16T06:41:56Z |  |  | error while make verilog |
| [#997](https://github.com/OpenXiangShan/XiangShan/issues/997) | Issue | closed | 2022-03-16T06:41:13Z |  |  | "make help" fail on jdk 8 |
| [#1338](https://github.com/OpenXiangShan/XiangShan/issues/1338) | Issue | closed | 2022-03-16T06:40:25Z | exception |  | Core 0: ABORT at pc = 0x216bce2f5 |
| [#1459](https://github.com/OpenXiangShan/XiangShan/issues/1459) | Issue | closed | 2022-03-09T09:24:41Z |  |  | 如何让编译出的仿真程序产生波形 |
| [#1486](https://github.com/OpenXiangShan/XiangShan/issues/1486) | Issue | closed | 2022-03-08T01:05:27Z |  |  | 能不能把音频codec也加进去？ |
| [#1471](https://github.com/OpenXiangShan/XiangShan/issues/1471) | Issue | closed | 2022-03-02T04:01:08Z |  |  | Betapoint |
| [#1483](https://github.com/OpenXiangShan/XiangShan/issues/1483) | Issue | closed | 2022-03-01T11:45:11Z |  |  | How to generate boom verilog code in chipyard? |
| [#1476](https://github.com/OpenXiangShan/XiangShan/issues/1476) | Issue | closed | 2022-02-24T07:25:35Z |  |  | 如果你们是为了RSIC-V的推广和普及 |
| [#1472](https://github.com/OpenXiangShan/XiangShan/issues/1472) | Issue | closed | 2022-02-23T08:02:35Z |  |  | Waveform terminator? |
| [#1469](https://github.com/OpenXiangShan/XiangShan/issues/1469) | Issue | closed | 2022-02-21T03:52:11Z |  |  | 香山系列芯片，有什么应用场景没有？ |
| [#1451](https://github.com/OpenXiangShan/XiangShan/issues/1451) | Issue | closed | 2022-02-02T13:44:36Z |  |  | 是否可以考虑再提供一个编译成verilog的库 |
| [#1449](https://github.com/OpenXiangShan/XiangShan/issues/1449) | Issue | closed | 2022-02-01T15:55:52Z |  |  | install mill link is expired |
| [#1436](https://github.com/OpenXiangShan/XiangShan/issues/1436) | Issue | closed | 2022-01-21T02:39:47Z |  |  | How to do like this? |
| [#1385](https://github.com/OpenXiangShan/XiangShan/issues/1385) | Issue | closed | 2022-01-04T04:55:59Z |  |  | Make EMU Error |
| [#1386](https://github.com/OpenXiangShan/XiangShan/issues/1386) | Issue | closed | 2022-01-04T04:55:49Z |  |  | 请教上板调试时遇到的问题 |
| [#1384](https://github.com/OpenXiangShan/XiangShan/issues/1384) | Issue | closed | 2021-12-23T08:18:03Z |  |  | 请教 关于南湖B扩展的问题 |
| [#1371](https://github.com/OpenXiangShan/XiangShan/issues/1371) | Issue | closed | 2021-12-17T02:14:15Z |  |  | 关于工具链的问题 |
| [#1351](https://github.com/OpenXiangShan/XiangShan/issues/1351) | Issue | closed | 2021-12-16T01:42:36Z |  |  | 关于lightSSS的使用 |
| [#1348](https://github.com/OpenXiangShan/XiangShan/issues/1348) | Issue | closed | 2021-12-13T09:18:01Z |  |  | Future Request:Design New Open Hardware/Operating System |
| [#1346](https://github.com/OpenXiangShan/XiangShan/issues/1346) | Issue | closed | 2021-12-13T08:45:01Z |  |  | 香山make emu问题 |
| [#1311](https://github.com/OpenXiangShan/XiangShan/issues/1311) | Issue | closed | 2021-12-05T12:15:52Z |  |  | 建议开设一个xiangshan-tools的仓库 |
| [#1310](https://github.com/OpenXiangShan/XiangShan/issues/1310) | Issue | closed | 2021-12-05T10:29:56Z |  |  | not found: /root/.ivy2/local/edu.berkeley.cs/chisel3_2.12/3.5-SNAPSHOT/ivys/ivy.xml |
| [#1291](https://github.com/OpenXiangShan/XiangShan/issues/1291) | Issue | closed | 2021-12-03T01:30:25Z |  |  | 请教noninclusive mshr中w_grant信号的一个问题。 |
| [#1281](https://github.com/OpenXiangShan/XiangShan/issues/1281) | Issue | closed | 2021-12-01T05:52:52Z |  |  | 请教关于noninclusive mshr中的a_schedule中req_acquire时写selfdir条件问题 |
| [#1280](https://github.com/OpenXiangShan/XiangShan/issues/1280) | Issue | closed | 2021-12-01T05:52:40Z |  |  | 请教一个关于noninclusive mshr中a_schedule流程的问题 |
| [#1264](https://github.com/OpenXiangShan/XiangShan/issues/1264) | Issue | closed | 2021-11-26T09:09:20Z |  |  | 请教noninclusive mshr中关于will_release_through的问题 |
| [#1261](https://github.com/OpenXiangShan/XiangShan/issues/1261) | Issue | closed | 2021-11-25T07:28:22Z |  |  | 请教noninclusive mshr中如下代码的问题 |
| [#1250](https://github.com/OpenXiangShan/XiangShan/issues/1250) | Issue | closed | 2021-11-25T06:21:54Z |  |  | 关于noninclusive mshr中 onAReq产生new_self_meta.clientStates代码的问题 |
| [#1244](https://github.com/OpenXiangShan/XiangShan/issues/1244) | Issue | closed | 2021-11-24T01:53:38Z |  |  | 关于noninclusive mshr中的probe_next_state的问题。 |
| [#879](https://github.com/OpenXiangShan/XiangShan/issues/879) | Issue | closed | 2021-11-18T05:38:07Z |  |  | 关于freelist的问题 |
| [#948](https://github.com/OpenXiangShan/XiangShan/issues/948) | Issue | closed | 2021-11-18T05:37:40Z |  |  | 请教一个关于renametable的问题 |
| [#1220](https://github.com/OpenXiangShan/XiangShan/issues/1220) | Issue | closed | 2021-11-18T05:37:03Z |  |  | 请教noninclusive mshr中的preferCache信号。 |
| [#1232](https://github.com/OpenXiangShan/XiangShan/issues/1232) | Issue | closed | 2021-11-18T05:25:41Z |  |  | 请教noninclusive mshr中的transmit_from_other_client信号 |
| [#1243](https://github.com/OpenXiangShan/XiangShan/issues/1243) | Issue | closed | 2021-11-18T02:41:56Z |  |  | 请教huncun中ProbeHelper的作用 |
| [#1240](https://github.com/OpenXiangShan/XiangShan/issues/1240) | Issue | closed | 2021-11-17T05:53:20Z |  |  | 请教关于noninclusive mshr中的replace_param参数的问题 |
| [#1149](https://github.com/OpenXiangShan/XiangShan/issues/1149) | Issue | closed | 2021-11-16T01:02:35Z |  |  | 请教inclusive mshr中miss put请求处理的问题。 |
| [#1206](https://github.com/OpenXiangShan/XiangShan/issues/1206) | Issue | closed | 2021-11-10T10:14:16Z |  |  | 请教sourceC中的back_pressure信号 |
| [#1208](https://github.com/OpenXiangShan/XiangShan/issues/1208) | Issue | closed | 2021-11-09T07:18:26Z |  |  | A proposal of uploading verilog files |
| [#1201](https://github.com/OpenXiangShan/XiangShan/issues/1201) | Issue | closed | 2021-11-08T01:05:17Z |  |  | 请教一个关于sourceD的问题 |
| [#1197](https://github.com/OpenXiangShan/XiangShan/issues/1197) | Issue | closed | 2021-11-04T02:43:35Z |  |  | 请教huncun中RefillBuffer的问题 |
| [#1194](https://github.com/OpenXiangShan/XiangShan/issues/1194) | Issue | closed | 2021-11-02T09:38:54Z |  |  | MaskGen生成的mask错误 |
| [#1175](https://github.com/OpenXiangShan/XiangShan/issues/1175) | Issue | closed | 2021-11-01T08:47:50Z |  |  | 请教一个huncun中SRAMTemplate.scala的问题 |
| [#1165](https://github.com/OpenXiangShan/XiangShan/issues/1165) | Issue | closed | 2021-10-26T12:57:16Z |  |  | "no member named '__Vm_threadPoolp' in 'VSimTop'" error in test run  |
| [#1141](https://github.com/OpenXiangShan/XiangShan/issues/1141) | Issue | closed | 2021-10-26T08:52:56Z |  |  | 请教一个inclusive/mshr中的put处理流程问题 |
| [#1148](https://github.com/OpenXiangShan/XiangShan/issues/1148) | Issue | closed | 2021-10-25T07:14:47Z |  |  | 请教在inclusive mshr中trunk节点收到acquire toB处理的问题。 |
| [#1130](https://github.com/OpenXiangShan/XiangShan/issues/1130) | Issue | closed | 2021-10-25T01:17:39Z |  |  | Vivado2021.1综合XSTop出现的问题 |
| [#1117](https://github.com/OpenXiangShan/XiangShan/issues/1117) | Issue | closed | 2021-10-16T06:35:26Z |  |  | 请教关于skipProbeN的问题 |
| [#1118](https://github.com/OpenXiangShan/XiangShan/issues/1118) | Issue | closed | 2021-10-16T06:35:18Z |  |  | 请教inclusive mshr中的probe_next_state |
| [#1119](https://github.com/OpenXiangShan/XiangShan/issues/1119) | Issue | closed | 2021-10-16T06:35:09Z |  |  | TLLogWrite和DirLogWrite |
| [#1095](https://github.com/OpenXiangShan/XiangShan/issues/1095) | Issue | closed | 2021-10-09T02:46:01Z |  |  | make emu error |
| [#1079](https://github.com/OpenXiangShan/XiangShan/issues/1079) | Issue | closed | 2021-09-30T08:10:54Z |  |  | No FPGA files |
| [#1077](https://github.com/OpenXiangShan/XiangShan/issues/1077) | Issue | closed | 2021-09-29T04:13:06Z |  |  | NutShell processor dead? |
| [#1049](https://github.com/OpenXiangShan/XiangShan/issues/1049) | Issue | closed | 2021-09-20T03:25:44Z |  |  | Using ready-to-run linux.bin in verilator |
| [#913](https://github.com/OpenXiangShan/XiangShan/issues/913) | Issue | closed | 2021-09-19T07:26:58Z |  |  | make verilog error |
| [#998](https://github.com/OpenXiangShan/XiangShan/issues/998) | Issue | closed | 2021-09-06T05:03:56Z |  |  | make ARCH=riscv64-xs出错 |
| [#858](https://github.com/OpenXiangShan/XiangShan/issues/858) | Issue | closed | 2021-09-04T10:31:17Z |  | type: feature/planned | Use GenVerilogMemBehaviorModelAnno to replace vlsi_mem_gen |
| [#989](https://github.com/OpenXiangShan/XiangShan/issues/989) | Issue | closed | 2021-09-02T02:57:06Z |  |  | 关于MicroOp.roqIdx在Rename及Roq中使用的疑问，谢谢！ |
| [#981](https://github.com/OpenXiangShan/XiangShan/issues/981) | Issue | closed | 2021-09-01T00:08:02Z |  |  | master及雁栖湖分支，解码表 FDivSqrtDecode  中设置的fuType字段怎么是“FuType.fmisc” 而不是 “FuType.fDivSqrt”？谢谢！ |
| [#916](https://github.com/OpenXiangShan/XiangShan/issues/916) | Issue | closed | 2021-08-31T08:20:01Z |  |  | master分支中difftest模块编译过程中跟NOOP_HOME、NEMU_HOME两个环境变量最好解耦开。NOOP_HOME可以通过相对目录关系解决。而NEMU_HOME是不是不是必须的？ |
| [#952](https://github.com/OpenXiangShan/XiangShan/issues/952) | Issue | closed | 2021-08-31T08:19:52Z |  |  | class ExuWbArbiter中特意将ctrl与data分开使用两个Arbiter，这个基于什么考虑呢？  是为了避免位宽大导致延时比较大么？ |
| [#980](https://github.com/OpenXiangShan/XiangShan/issues/980) | Issue | closed | 2021-08-31T08:19:06Z |  |  | abstract class XSCoreBase()中针对有些EXU设置了两个dispatch port，这个的用意是？谢谢！ |
| [#965](https://github.com/OpenXiangShan/XiangShan/issues/965) | Issue | closed | 2021-08-27T01:34:48Z |  |  | error while make emu |
| [#955](https://github.com/OpenXiangShan/XiangShan/issues/955) | Issue | closed | 2021-08-26T03:05:24Z |  |  | Request feature Debug and development in intellij IDE |
| [#957](https://github.com/OpenXiangShan/XiangShan/issues/957) | Issue | closed | 2021-08-24T15:45:46Z |  |  |  debug RocketChip and generate .v |
| [#932](https://github.com/OpenXiangShan/XiangShan/issues/932) | Issue | closed | 2021-08-23T09:08:57Z |  |  | can you make a version for IntelliJ |
| [#901](https://github.com/OpenXiangShan/XiangShan/issues/901) | Issue | closed | 2021-07-26T14:40:35Z |  |  | 请问你们的验证足够吗？想过UVM吗？  |
| [#876](https://github.com/OpenXiangShan/XiangShan/issues/876) | Issue | closed | 2021-07-24T15:32:57Z |  |  | 制作bbl.bin失败 |
| [#898](https://github.com/OpenXiangShan/XiangShan/issues/898) | Issue | closed | 2021-07-24T14:04:16Z |  |  | yanqihu分支的build.sc文件引用了不存在的文件 |
| [#897](https://github.com/OpenXiangShan/XiangShan/issues/897) | Issue | closed | 2021-07-24T05:54:00Z |  |  | env配置的仓库路径不对 |
| [#894](https://github.com/OpenXiangShan/XiangShan/issues/894) | Issue | closed | 2021-07-21T11:44:56Z |  |  | ready-to-run/riscv64-nemu-interpreter-so cannot be loaded |
| [#881](https://github.com/OpenXiangShan/XiangShan/issues/881) | Issue | closed | 2021-07-19T03:48:16Z |  |  | mill installation method has expired |
| [#886](https://github.com/OpenXiangShan/XiangShan/issues/886) | Issue | closed | 2021-07-19T03:26:11Z |  |  | %Error: build/SimTop.v:2487721:3: Cannot find file containing module: 'array_18_ext' |
| [#835](https://github.com/OpenXiangShan/XiangShan/issues/835) | Issue | closed | 2021-07-05T03:46:13Z |  |  | 添加中文README和文档 |
| [#862](https://github.com/OpenXiangShan/XiangShan/issues/862) | Issue | closed | 2021-07-04T09:59:00Z |  |  | 中文文档链接失效 |
| [#848](https://github.com/OpenXiangShan/XiangShan/issues/848) | Issue | closed | 2021-06-27T09:04:40Z |  |  | build.sc中的依赖是如何得到的 |
| [#847](https://github.com/OpenXiangShan/XiangShan/issues/847) | Issue | closed | 2021-06-26T07:36:02Z |  |  | 编译时间太久 |
| [#116](https://github.com/OpenXiangShan/XiangShan/issues/116) | Issue | closed | 2020-07-12T02:43:47Z |  |  | coremark pass |
| [#114](https://github.com/OpenXiangShan/XiangShan/issues/114) | Issue | closed | 2020-07-12T02:34:03Z |  |  | cputest pass |
