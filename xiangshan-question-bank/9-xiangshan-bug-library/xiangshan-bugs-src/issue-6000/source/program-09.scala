when (io.in.valid && isRVC) {
  printf(
    "[XSBUG_JUMP_ISRVC_OBS] pc=0x%x target=0x%x ftqOffset=%d redirectValid=%d redirect_isRVC=%d needRedirect=%d targetWrong=%d fixedTaken=%d predTaken=%d\n",
    io.in.bits.data.pc.get,
    jumpDataModule.io.target,
    io.in.bits.ctrl.ftqOffset.get,
    redirectValid,
    redirect.isRVC,
    needRedirect,
    targetWrong,
    fixedTaken,
    predTaken
  )
}
