package audit
import org.chipsalliance.cde.config.Parameters
import xiangshan._
import top.DefaultConfig
import utility._
object AuditCfg {
  def apply(): Parameters = {
    val dc = new DefaultConfig
    dc.alter((site, here, up) => {
      case XSCoreParamsKey => up(XSTileKey).head
      case DFTOptionsKey => up(DFTOptionsKey).copy(EnableMbist = false, EnableSramCtl = false)
      case LogUtilsOptionsKey => LogUtilsOptions(false, false, false, false)
      case PerfCounterOptionsKey => PerfCounterOptions(false, false, XSPerfLevel.withName("VERBOSE"), 0)
    })
  }
}
