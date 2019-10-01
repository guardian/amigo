import java.io.File

import scala.annotation.tailrec

// Used to determine what to exclude when getting roles defined in the roles/ directory.
object RolesFileFilter extends sbt.FileFilter  {

  // Can't find an easy way to access the sbt logger from here
  private def info(message: String): Unit =
    println(s"[info] $message")

  @tailrec private def isInDirs(file: File, dir: String): Boolean =
    if (file.getName == "roles") false
    else if (file.getName == dir) true
    else isInDirs(file.getParentFile, dir)

  // Want to ignore:
  // - files: bootstrap.sh, Vagrantfile, log files from vagrant (*.log)
  // - directories: .vagrant/, __test
  override def accept(file: File): Boolean = {
    val files = Set("bootstrap.sh", "Vagrantfile", "extra-vars.yaml", "playbook.yaml")
    if (file.isFile && file.getParent == "roles" &&
      (files.contains(file.getName) || file.getName.matches("(.*).log$"))) {
      info(s"ignoring file $file in roles directory")
      false
    } else {
      val shouldAccept = !isInDirs(file, dir = ".vagrant")
      if (!shouldAccept) info(s"ignoring file $file in roles directory")
      shouldAccept
    }
  }
}
