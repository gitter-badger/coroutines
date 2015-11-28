package scala.coroutines.common



import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context



object Stack {
  def init[T](stack: Array[T], size: Int): Unit = macro initMacro[T]

  def initMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, size: c.Tree): c.Tree = {
    import c.universe._

    val tpe = implicitly[c.WeakTypeTag[T]]
    if (size == q"-1") q"" else q"""
      if ($stack == null) $stack = new Array[$tpe]($size)
    """
  }

  def push[T](stack: Array[T], x: T, size: Int): Unit = macro pushMacro[T]

  def pushMacro[T: c.WeakTypeTag](c: Context)(
    stack: c.Tree, x: c.Tree, size: c.Tree
  ): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    q"""
      scala.coroutines.common.Stack.init[$tpe]($stack, $size)
      if ($stackptr >= $stack.length) {
        val nstack = new Array[$tpe]($stack.length * 2)
        java.lang.System.arraycopy($stack, 0, nstack, 0, $stack.length)
        $stack = nstack
      }
      $stack($stackptr) = $x
      $stackptr += 1
    """
  }

  def bulkPush[T](stack: Array[T], n: Int, size: Int): Unit = macro bulkPushMacro[T]

  def bulkPushMacro[T: c.WeakTypeTag](c: Context)(
    stack: c.Tree, n: c.Tree, size: c.Tree
  ): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    val valnme = TermName(c.freshName())
    q"""
      scala.coroutines.common.Stack.init[$tpe]($stack, $size)
      $stackptr += $n
      while ($stackptr >= $stack.length) {
        val nstack = new Array[$tpe]($stack.length * 2)
        java.lang.System.arraycopy($stack, 0, nstack, 0, $stack.length)
        $stack = nstack
      }
    """
  }

  def pop[T](stack: Array[T]): T = macro popMacro[T]

  def popMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    val valnme = TermName(c.freshName())
    q"""
      $stackptr -= 1
      val $valnme = $stack($stackptr)
      $stack($stackptr) = null.asInstanceOf[$tpe]
      $valnme
    """
  }

  def bulkPop[T](stack: Array[T], n: Int): Unit = macro bulkPopMacro[T]

  def bulkPopMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, n: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val tpe = implicitly[WeakTypeTag[T]]
    val valnme = TermName(c.freshName())
    q"""
      $stackptr -= $n
    """
  }

  def top[T](stack: Array[T]): T = macro topMacro[T]

  def topMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    q"""
      $stack($stackptr - 1)
    """
  }

  def get[T](stack: Array[T], n: Int): T = macro getMacro[T]

  def getMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, n: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val valnme = TermName(c.freshName())
    q"""
      $stack($stackptr - 1 - $n)
    """
  }

  def set[T](stack: Array[T], n: Int, x: T): Unit = macro setMacro[T]

  def setMacro[T: c.WeakTypeTag](c: Context)(
    stack: c.Tree, n: c.Tree, x: c.Tree
  ): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val valnme = TermName(c.freshName())
    q"""
      $stack($stackptr - 1 - $n) = $x
    """
  }

  def update[T](stack: Array[T], x: T): T = macro updateMacro[T]

  def updateMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree, x: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    val valnme = TermName(c.freshName())
    q"""
      val $valnme = $stack($stackptr)
      $stack($stackptr - 1) = $x
      $valnme
    """
  }

  def isEmpty[T](stack: Array[T]): Boolean = macro isEmptyMacro[T]

  def isEmptyMacro[T: c.WeakTypeTag](c: Context)(stack: c.Tree): c.Tree = {
    import c.universe._

    val q"$path.${name: TermName}" = stack
    val stackptrname = TermName(s"${name}ptr")
    val stackptr = q"$path.$stackptrname"
    q"""
      $stackptr <= 0
    """
  }

}
