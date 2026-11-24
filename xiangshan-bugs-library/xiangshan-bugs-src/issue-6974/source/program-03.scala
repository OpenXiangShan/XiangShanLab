fromRenameUpdate(i).bits.loadWaitStrict :=
  fromRename(i).bits.loadWaitStrict &&
  io.lfst.resp(i).bits.shouldWait &&
  io.lfst.resp(i).bits.strictShouldWait
