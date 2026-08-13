# Commit Log
- Issue: #3818
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3818
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3818
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3818
- Changed files: 3
- Additions: 51
- Deletions: 0

## Files
- `build.sc`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/build.sc b/build.sc
index 89af9ccd3fe..d0c975702be 100644
--- a/build.sc
+++ b/build.sc
@@ -28,6 +28,7 @@ import $file.openLLC.common
 /* for publishVersion */
 import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
 import de.tobiasroeser.mill.vcs.version.VcsVersion
+import java.io.{BufferedReader, InputStreamReader}
 import java.time.LocalDateTime
 import java.time.format.DateTimeFormatter
 import java.util.Locale
@@ -277,8 +278,28 @@ object xiangshan extends XiangShanModule with HasChisel with ScalafmtModule {
       LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd hh:mm:ss yyyy").withLocale(new Locale("en")))),
   )
 
+  def gitStatus: T[String] = {
+    val gitRevParseBuilder = new ProcessBuilder("git", "rev-parse", "HEAD")
+    val gitRevParseProcess = gitRevParseBuilder.start()
+    val shaReader = new BufferedReader(new InputStreamReader(gitRevParseProcess.getInputStream))
+    val sha = shaReader.readLine()
+
+    val gitStatusBuilder = new ProcessBuilder("git", "status", "-uno", "--porcelain")
+    val gitStatusProcess = gitStatusBuilder.start()
+    val gitStatusReader = new BufferedReader(new InputStreamReader(gitStatusProcess.getInputStream))
+    val status = gitStatusReader.readLine()
+    val gitDirty = if (status == null) 0 else 1 
+
+    val str =
+      s"""|SHA=$sha
+          |dirty=$gitDirty
+          |""".stripMargin
+    str
+  }
+
   override def resources = T.sources {
     os.write(T.dest / "publishVersion", publishVersion())
+    os.write(T.dest / "gitStatus", gitStatus())
     super.resources() ++ Seq(PathRef(T.dest))
   }
 
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala
new file mode 100644
index 00000000000..e916ff5f542
--- /dev/null
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala
@@ -0,0 +1,24 @@
+package xiangshan.backend.fu.NewCSR
+
+import chisel3._
+
+import java.util.Properties
+
+class CommitIDModule(shaWidth: Int) extends Module {
+  val io = IO(new Bundle {
+    val commitID = Output(UInt(shaWidth.W))
+    val dirty    = Output(Bool())
+  })
+
+  val props = new Properties()
+  props.load((os.resource / "gitStatus").getInputStream)
+
+  val sha = props.get("SHA").asInstanceOf[String].take(shaWidth / 4)
+  val dirty = props.get("dirty").asInstanceOf[String].toInt
+
+  println(s"[CommitIDModule] SHA=$sha")
+  println(s"[CommitIDModule] dirty=$dirty")
+
+  io.commitID := BigInt(sha, 16).U(shaWidth.W)
+  io.dirty := dirty.U
+}
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index b1d6e1bde00..9d2648c40a1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -266,6 +266,12 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   val permitMod = Module(new CSRPermitModule)
   val sstcIRGen = Module(new SstcInterruptGen)
+  val commidIdMod = Module(new CommitIDModule(40))
+
+  val gitCommitSHA = WireInit(commidIdMod.io.commitID)
+  val gitDirty     = WireInit(commidIdMod.io.dirty)
+  dontTouch(gitCommitSHA)
+  dontTouch(gitDirty)
 
   private val wenLegal = permitMod.io.out.hasLegalWen
```
