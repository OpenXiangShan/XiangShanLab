Please use rebase and merge, do NOT squash this PR!

Stage part 2 of new Bpu, including:
- half-aligned fallthrough & ubtb
- disallow cross-page fallthorugh
- ubtb/abtb fixes
- mbtb
- ~~tage-sc (WIP)~~
- writebuffer (to resolve read-write conflicts)
- phr
- more performance counters

Rebased & squashed from [feat-v3-bpu](https://github.com/OpenXiangShan/XiangShan/tree/feat-v3-bpu) branch, original commit history:
093d3098f124a9f4c0f0e2b4b13942bdf43ab0a8 style(Bpu): re-format & cleanup unused imports
71403740e1272ff2f54e93198250a10fed64db9d refactor(phr): add diff counter
2ed1e1584d3b0aeb0f5607930e8a989f3c5cd580 fix(abtb): add targetLowerBits to meta
64d306338ad703c3f58a84f2551e756d1341a3a7 fix(Bpu): rename TargetCarry
b3e4a51692280e6ecd77e9721855513cf58d0ceb fix(ubtb): fix getTag() upper bound
278c6fd50b9d3f9e6cf5cac729fa5b4e49567488 refactor(Bpu): re-organize meta
a53b851666fea309c915682b29331e92a4ddabe3 feat(abtb): invalidate one entry when detect multi-hit
f59abd032b962075c3e5723e72622827d8022978 refactor(abtb): rename abtb bank and replacer
34ea8fe0c0f44ee5d84b4f6037edbed19a192956 fix(phr): fix s0_phrPtr update logic
a22574e300aab07ae9596cf7fd8e95b120fc406d refactor(phr): refactor phr parameters
16a464cc2d67d7f3bae6f73c7d978de914047ca3 fix(Bpu): rename TargetCarry
a2571cbcf26f5d9ec4a360af4fe57f794865114e chore(CODEOWNER): claim phr/abtb/mbtb
931606ac1a0343d6786c950dcb88b4bae2e6ac97 style(phr): add type annotation syntax
f9fa794d90cc0c26c25cb0924bc92f01abf604aa fix(phr): fix the incorrect use of ptr during update phr and the incorrect order of newestBitsSet in foldedPhr
587b7d614df9542c830203bff939d186d3d84212 refactor(bpu): use TargetFixHelper for all
4dd17d83a444f29023b09a6cd9661b924559c5fa feat(abtb): add EnableTargetFix option
0cb11ab2f301ea8a19e9d57b1788bc4ae78241b9 Revert "feat(abtb): not calculate target carry"
6e380c70970f9aa1f4aa6591dd34f2018acdebcb fix(ubtb): remove unused io.hit
7953df766fae84960fb027b5eb92d1b147b8a887 feat(abtb): use WriteBuffer to reduce multi-hit
67af0f00f13817e21a365418f3a16f5e4ce889b7 feat(abtb): not calculate target carry
62069337853969e07112cdb99edd8ebaec631102 refacotr(phr): remove some unused import and bundles.
075c7a856b96b6515bc8013b337af5d966a4466e fix(phr): fix phr and foldedPhr update logic.
ddd6222eed15cb78986ca9c45944964d9d8c4c19 feat(sc): add sc skeleton
6697f2d6750b9472b218d3a87ce6b046c0023f1b fix(abtb): set NumWays to 4
b597e54cb80c769717455700a8e92800963abb7e feat(abtb): add more perf ctr
a58da585c0d1585499589de7ae85c0b1869f7bd6 fix(phr): del s2_override signals.
3aa977bd5f000f15da9d382b1d583fcf9dad6f66 feat(Bpu): add phr.
7e1af5e05a7468c3115e7b69110ca0ab5e3af708 fix(bpu): remove prediction and hit from BasePredictorIO
cc1d398d87a4e06793b808622abe20a54d98ab4c fix(bpu): s1_ready should immediately assert high when s1_flush
0228dc9507678ccace1a9e0488828610aa267fe7 fix(bpu): modify s1 prediction select logic
46ff2b181f23b2ffdb59b0cb7dc93f78e481aef3 fix(abtb): fix replacer read touch condition
0255ec8fa2177807b64961892ee632a8cb29f0ad fix(abtb): fix previousPcValid
55aaf49392e585b3fa5c7cbc030b5451b77faada fix(bpu): remove s2 override
6bb37f86d6e795fedfd45e3e21c92e0fdfa47e3a fix(tage): add temporary connection for compile
41dc4d8896ca1e1d007d85169617518b18a6eeea refactor(abtb): rename some parameter and move one function to bpu top
5f22d239a11ad61023bc8b60353ee682d2290b03 feat(abtb): add performance counter for hit entry number
27d58a5a346bd40c24ec7eb731da69034220bba8 feat(bpu): add tage skeleton
1fa322b8657c2292c8a954dbfe59b0f8cf950b5d feat(bpu): implement a region-btb mbtb framework
f19e0d00732d6c0ba0e1d930f9c87a6d36384b28 fix(abtb): fix train logic
8ff87ba1c40ec8fc476a4fc65c5ca2db9528a974 feat(Bpu): disallow fallthrough a cross-page fetch block
c675cfc68367ade838731f219011a1ee625b8309 style(WriteBuffer): do not use anonymous Bundle.
20e5e5c6698f629df2dfd8296eb7d8f9124f6057 style(WriteBuffer): modify the style of the reference header file.
daf11ee984ecbabaabb2ce38c58ed7af53b1314a feat(bpu): add WriteBuffer & add counter update.
ee5e999fd09759deefc93437eadddf555cc01839 feat(Bpu): half-align
a6462d11001842e511ac71a2061843175ec21d3a feat(Bpu): add fetchBlockSize perf
94e52a9104cc9185c3fc6a0186485c639b8a7fe9 refactor(abtb): refactor abtb (force-pushed after #4851)
