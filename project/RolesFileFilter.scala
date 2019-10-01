import java.io.File

import scala.annotation.tailrec

// Used to determine what to exclude when getting roles defined in the roles/ directory.
object RolesFileFilter extends sbt.FileFilter  {
  
  // Can't find an easy way to access the sbt logger from here
  private def info(message: String): Unit = 
    println(s"[info] $message")

  @tailrec private def isInDirs(file: File, dirs: Set[String]): Boolean =
    if (file.getName == "roles") false
    else if (dirs.contains(file.getName)) true
    else isInDirs(file.getParentFile, dirs)

  // Want to ignore:
  // - files: bootstrap.sh, Vagrantfile, log files from vagrant (*.log)
  // - directories: .vagrant/, __test
  override def accept(file: File): Boolean =
    if (file.isFile && file.getParent == "roles" && 
      (file.getName == "bootstrap.sh" || file.getName == "Vagrantfile" || file.getName.matches("(.*).log$"))) {
      info(s"ignoring file $file in roles directory")
      false
    } else {
      val result = !isInDirs(file, Set(".vagrant", "__test"))
      if (!result) info(s"ignoring file $file in roles directory")
      result
    }
}
