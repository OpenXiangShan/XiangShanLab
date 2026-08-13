imsic.fromCSR.vgein     := csrMod.toAIA.vgein   // vgein is plumbed...
imsic.fromCSR.claims(0) := csrMod.toAIA.mClaim
imsic.fromCSR.claims(1) := csrMod.toAIA.sClaim
imsic.fromCSR.claims(2) := csrMod.toAIA.vsClaim // ...but never ANDed into claims(2)
