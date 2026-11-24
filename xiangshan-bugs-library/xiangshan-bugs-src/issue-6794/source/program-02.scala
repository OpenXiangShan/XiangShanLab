  abstract class EnvCfg extends CSRBundle {
    // Set all fields not supported as RO in base class
    ...
    val FIOM  =      RO(     0)           .withReset(0.U).withDescription("Fence of I/O implies memory ordering.")
