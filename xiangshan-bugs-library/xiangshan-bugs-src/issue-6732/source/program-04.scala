// CSRPermitModule.scala:214, 224
private val csrIsVec = Seq(CSRs.vstart, CSRs.vxsat, CSRs.vxrm, CSRs.vcsr, CSRs.vtype).map(_.U === addr).reduce(_ || _)
...
private val vecOff_EX_II = csrIsVec && vsEffectiveOff
