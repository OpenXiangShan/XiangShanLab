This PR completely refactors the commit mechanism from frontend to backend, addressing the commit problem once and for all. Additionally, these changes are expected to save noticeable area for frontend.

The backend now guarantees that every FTQ entry is committed on time, rather than committing only a subset of instructions. To accomplish this, each ROB entry can now compress a maximum of two FTQ entries. As a result, the `commitStateQueue` is no longer needed in the FTQ.

During the refactoring process, some historical bugs were identified and fixed.

**Attention**: Modifications in the FTQ may lead to a noticeable drop in BP accuracy. Performance data should be checked before merging into master.
