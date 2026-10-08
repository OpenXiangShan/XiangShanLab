package minixiangshan

import chisel3._
import chisel3.util._
import config._
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.sys.process._

// ================================================================
// 统一 Verilog 生成入口
// ================================================================
object CoreGen extends App {

  // 提取公共删除函数
  def cleanAndMkdir(dirPath: String): File = {
    val dir = new File(dirPath)
    if (dir.exists() && dir.isDirectory) {
      def deleteRecursively(f: File): Unit = {
        if (f.isDirectory) f.listFiles().foreach(deleteRecursively)
        if (f.exists && !f.delete())
          throw new Exception(s"Exception DeleFail: ${f.getAbsolutePath}")
      }
      deleteRecursively(dir)
    }
    dir.mkdirs()
    dir
  }

  def tryRun(cmd: Seq[String], cwd: File): Option[String] = {
    try {
      val out = Process(cmd, cwd).!!.trim
      if (out.nonEmpty) Some(out) else None
    } catch {
      case _: Throwable => None
    }
  }

  def prependHeaderComment(file: Path, header: String): Unit = {
    val oldContent = Files.readString(file, StandardCharsets.UTF_8)
    Files.writeString(file, header + oldContent, StandardCharsets.UTF_8)
  }

  def buildCommentBlock(title: String, lines: Seq[String], fallback: String = "unknown"): String = {
    val body =
      if (lines.nonEmpty) lines.map(line => s"//   $line").mkString("\n")
      else s"//   $fallback"
    s"// $title\n$body\n"
  }

  case class GitNumStat(insertions: String, deletions: String)
  case class GitStatusEntry(indexStatus: Char, worktreeStatus: Char, path: String)

  def parseGitNumStat(text: Option[String]): Map[String, GitNumStat] = {
    text.toSeq
      .flatMap(_.linesIterator)
      .flatMap { line =>
        val parts = line.split("\t", 3)
        if (parts.length == 3) Some(parts(2) -> GitNumStat(parts(0), parts(1))) else None
      }
      .toMap
  }

  def parseGitStatus(text: Option[String]): Seq[GitStatusEntry] = {
    text.toSeq
      .flatMap(_.linesIterator)
      .flatMap { line =>
        if (line.length >= 4) Some(GitStatusEntry(line(0), line(1), line.drop(3))) else None
      }
      .toSeq
  }

  def statusLabel(code: Char): String = code match {
    case 'M' => "modified"
    case 'A' => "added"
    case 'D' => "deleted"
    case 'R' => "renamed"
    case 'C' => "copied"
    case 'U' => "unmerged"
    case 'T' => "type-changed"
    case '?' => "untracked"
    case _   => "updated"
  }

  def formatNumStat(stat: Option[GitNumStat]): String = stat match {
    case Some(GitNumStat(ins, del)) if ins == "-" || del == "-" => " (binary)"
    case Some(GitNumStat(ins, del))                             => s" (+$ins/-$del)"
    case None                                                   => ""
  }

  def formatGitEntry(path: String, label: String, stat: Option[GitNumStat]): String =
    s"$label : ${path.stripPrefix("designCPUByChisel/")}${formatNumStat(stat)}"

  def buildGitWorkspaceBlock(
      statusEntries: Seq[GitStatusEntry],
      stagedStats: Map[String, GitNumStat],
      unstagedStats: Map[String, GitNumStat]
  ): String = {
    val stagedLines = statusEntries.collect {
      case entry if entry.indexStatus != ' ' && entry.indexStatus != '?' =>
        formatGitEntry(entry.path, s"[staged]   ${statusLabel(entry.indexStatus)}", stagedStats.get(entry.path))
    }

    val unstagedLines = statusEntries.collect {
      case entry if entry.worktreeStatus != ' ' && entry.worktreeStatus != '?' =>
        formatGitEntry(entry.path, s"[worktree] ${statusLabel(entry.worktreeStatus)}", unstagedStats.get(entry.path))
    }

    val untrackedLines = statusEntries.collect {
      case entry if entry.indexStatus == '?' && entry.worktreeStatus == '?' =>
        formatGitEntry(entry.path, "[untracked]", None)
    }

    val summary =
      if (statusEntries.isEmpty) {
        Seq("workspace clean")
      } else {
        Seq(
          s"workspace dirty: ${statusEntries.size} file(s)",
          s"staged=${stagedLines.size}, worktree=${unstagedLines.size}, untracked=${untrackedLines.size}"
        ) ++ stagedLines ++ unstagedLines ++ untrackedLines
      }

    buildCommentBlock("Git changes     :", summary, fallback = "workspace status unavailable")
  }

  // 读取第一个参数，默认 simu
  val mode = args.headOption.getOrElse("simu").toLowerCase match {
    case "fpga" => "fpga"
    case _      => "simu"
  }

  val targetDirPath = mode match {
    case "simu" => "./out"
    case "fpga" => "./out"
  }

  val enableDifftest = mode == "simu"  // simu 开启 difftest，fpga 关闭

  val targetDir = cleanAndMkdir(targetDirPath)
  val repoRoot = new File(".").getCanonicalFile

  implicit val config: Parameters = new Parameters(Map(
    DebugConfigKeys.EnableDifftest -> enableDifftest
  ))

  emitVerilog(
    new core_top,
    Array(
      "--target-dir", targetDirPath,
      "--emit-modules", "verilog"
    )
  )

  // 清理多余文件
  val filesToDelete = List("core_top.anno.json", "core_top.fir")
  filesToDelete.foreach { filename =>
    val fileToDelete = new File(targetDirPath, filename)
    if (fileToDelete.exists()) fileToDelete.delete()
  }

  val generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
  val gitCommit = tryRun(Seq("git", "rev-parse", "--short", "HEAD"), repoRoot).getOrElse("unknown")
  val gitBranch = tryRun(Seq("git", "rev-parse", "--abbrev-ref", "HEAD"), repoRoot).getOrElse("unknown")
  val gitCommitSubject = tryRun(Seq("git", "log", "-1", "--pretty=%s", "HEAD"), repoRoot).getOrElse("unknown")
  val gitStatusPorcelain = tryRun(Seq("git", "status", "--short", "--untracked-files=all", "--", "."), repoRoot)
  val gitStagedNumStat = parseGitNumStat(
    tryRun(Seq("git", "diff", "--cached", "--numstat", "--find-renames", "--", "."), repoRoot)
  )
  val gitUnstagedNumStat = parseGitNumStat(
    tryRun(Seq("git", "diff", "--numstat", "--find-renames", "--", "."), repoRoot)
  )
  val gitCommitStatBlock = buildGitWorkspaceBlock(
    parseGitStatus(gitStatusPorcelain),
    gitStagedNumStat,
    gitUnstagedNumStat
  )

  val header =
    s"""// ================================================================
       |// Auto-generated by CoreGen
       |// Generated at    : $generatedAt
       |// Mode            : $mode
       |// Git branch      : $gitBranch
       |// Git commit      : $gitCommit
       |// Git message     : $gitCommitSubject
       |${gitCommitStatBlock.stripSuffix("\n")}
       |// ---------------------------------------------------------------
       |// BPU  : useNewBPU     =  ${config(CoreConfigKeys.useNewBPU)}, 
       |//        BtbPhtSize    =  ${config(CoreConfigKeys.BtbPhtSize)}, 
       |// Cache: L2Ways        =  ${config(CoreConfigKeys.L2Ways)}, 
       |//        L2Prefetch    =  ${config(CoreConfigKeys.L2Prefetch)}, 
       |//        usePIPT       =  ${config(CoreConfigKeys.usePIPT)}, 
       |//        nWaysI        =  ${config(CoreConfigKeys.NWaysI)}, 
       |//        nSetsI        =  ${config(CoreConfigKeys.NSetsI)}, 
       |//        nWaysD        =  ${config(CoreConfigKeys.NWaysD)}, 
       |//        nSetsD        =  ${config(CoreConfigKeys.NSetsD)}, 
       |//        nMshrEntries  =  ${config(CoreConfigKeys.NMshrEntries)}
       |// Pipe : ibufDepth     =  ${config(CoreConfigKeys.IbufDepth)}, 
       |//        RobSize       =  ${config(CoreConfigKeys.RobSize)}, 
       |//        SnapshotNum   =  ${config(CoreConfigKeys.SnapshotNum)}
       |//        CommitWidth   =  ${config(CoreConfigKeys.CommitWidth)}, 
       |//        LqSize        =  ${config(CoreConfigKeys.LqSize)}, 
       |//        SqSize        =  ${config(CoreConfigKeys.SqSize)}
       |// IQ   : LoadQVersion  =  ${config(CoreConfigKeys.LoadQVersion)}, 
       |//        IQ1           =  ${config(CoreConfigKeys.IQ1Params).numEntries}, 
       |//        IQ2           =  ${config(CoreConfigKeys.IQ2Params).numEntries}, 
       |//        IQ3           =  ${config(CoreConfigKeys.IQ3Params).numEntries}
       |//        IQ4           =  ${config(CoreConfigKeys.IQ4Params).numEntries}, 
       |//        IQ5           =  ${config(CoreConfigKeys.IQ5Params).numEntries}
       |// ================================================================
       |
       |""".stripMargin

  val verilogFiles = Option(targetDir.listFiles()).getOrElse(Array.empty)
    .filter(file => file.isFile && file.getName.endsWith(".v"))

  verilogFiles.foreach { file =>
    prependHeaderComment(file.toPath, header)
  }

  println(s"[CoreGen] Generated $mode version in $targetDirPath")
}
