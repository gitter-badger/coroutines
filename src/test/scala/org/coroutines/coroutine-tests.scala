package org.coroutines



import org.scalatest._
import scala.util.Failure



class CoroutineTest extends FunSuite with Matchers {
  test("should not yield") {
    val getOk = coroutine { () => "ok" }
    val c = call(getOk())
    assert(c.isLive)
    intercept[RuntimeException](c.result)
    intercept[RuntimeException](c.value)
    assert(!c.resume)
    intercept[RuntimeException](c.value)
    assert(c.result == "ok")
    assert(!c.isLive)
  }

  test("should throw when not alive") {
    val gimmeFive = coroutine { () => 5 }
    val c = call(gimmeFive())
    assert(c.isLive)
    assert(!c.resume)
    assert(c.result == 5)
    assert(c.getResult == Some(5))
    assert(!c.isLive)
    intercept[CoroutineStoppedException](c.resume)
    assert(c.getValue == None)
    c.tryValue match {
      case Failure(t: RuntimeException) =>
      case _ => assert(false)
    }
  }

  test("should yield once") {
    val plusMinus = coroutine { (x: Int) =>
      yieldval(x)
      -x
    }
    val c = call(plusMinus(5))
    assert(c.isLive)
    assert(c.resume)
    assert(c.value == 5)
    assert(c.isLive)
    assert(!c.resume)
    assert(c.result == -5)
    assert(!c.isLive)
  }

  test("should yield several times") {
    val sumAndDiffs = coroutine { (x: Int, y: Int) =>
      val sum = x + y
      yieldval(sum)
      val diff1 = x - y
      yieldval(diff1)
      val diff2 = y - x
      diff2
    }
    val c = call(sumAndDiffs(1, 2))
    assert(c.isLive)
    assert(c.resume)
    assert(c.value == 3)
    assert(c.isLive)
    assert(c.resume)
    assert(c.value == -1)
    assert(c.isLive)
    assert(!c.resume)
    assert(c.result == 1)
    assert(!c.isLive)
  }

  test("should lub yieldvals") {
    val lists = coroutine { (x: Int) =>
      yieldval(List(x))
      yieldval(List(x.toString))
    }
    val anotherLists: Coroutine[List[Any], Unit] = lists
    val c = call(lists(5))
    assert(c.resume)
    assert(c.value == List(5))
    assert(c.resume)
    assert(c.value == List("5"))
    assert(!c.resume)
    assert(c.result == (()))
  }

  test("should lub yieldtos and returns") {
    val wrapString = coroutine { (x: String) =>
      List(x.toString)
    }
    val f: Coroutine.Frame[Nothing, List[String]] = call(wrapString("ok"))
    val wrapInt = coroutine { (x: Int) =>
      yieldto(f)
      Vector(x)
    }
    val c = call(wrapInt(7))
    assert(c.resume)
    assert(c.getValue == None)
    assert(!c.resume)
    assert(c.result == Vector(7))
  }

  test("should declare body with if statement") {
    val xOrY = coroutine { (x: Int, y: Int) =>
      if (x > 0) {
        yieldval(x)
      } else {
        yieldval(y)
      }
    }
    val c1 = call(xOrY(5, 2))
    assert(c1.resume)
    assert(c1.value == 5)
    assert(!c1.resume)
    assert(c1.isCompleted)
    val c2 = call(xOrY(-2, 7))
    assert(c2.resume)
    assert(c2.value == 7)
    assert(!c2.resume)
    assert(c2.isCompleted)
  }

  test("should declare body with a coroutine call") {
    val doubleInt = coroutine { (x: Int) => 2 * x }
    val callOther = coroutine { (x: Int) =>
      val y = doubleInt(x)
      y
    }
    val c = call(callOther(5))
    assert(!c.resume)
    assert(c.result == 10)
    assert(!c.isLive)
  }

  test("should declare a value in a nested scope") {
    val someValues = coroutine { (x: Int, y: Int) =>
      if (x > 0) {
        val z = -x
        yieldval(z)
        yieldval(-z)
      } else {
        yieldval(y)
      }
      x
    }
    val c1 = call(someValues(5, 7))
    assert(c1.resume)
    assert(c1.value == -5)
    assert(c1.resume)
    assert(c1.value == 5)
    assert(!c1.resume)
    assert(c1.result == 5)
    val c2 = call(someValues(-5, 7))
    assert(c2.resume)
    assert(c2.value == 7)
    assert(!c2.resume)
    assert(c2.result == -5)
  }

  test("should declare a variable in a nested scope") {
    val someValues = coroutine { (x: Int, y: Int) =>
      if (x > 0) {
        var z = -x
        yieldval(z)
        z = -z
        yieldval(z)
      } else {
        yieldval(x)
      }
      y
    }
    val c1 = call(someValues(6, 11))
    assert(c1.resume)
    assert(c1.value == -6)
    assert(c1.resume)
    assert(c1.value == 6)
    assert(!c1.resume)
    assert(c1.result == 11)
    val c2 = call(someValues(-6, 11))
    assert(c2.resume)
    assert(c2.value == -6)
    assert(!c2.resume)
    assert(c2.result == 11)
  }

  test("coroutine should be called") {
    val emitTwice = coroutine { (x: Int) =>
      yieldval(x)
      x
    }
    val c = call(emitTwice(7))
    assert(c.resume)
    assert(c.value == 7)
    assert(!c.resume)
    assert(c.result == 7)
  }

  test("coroutine should contain an if statement and no yields") {
    val abs = coroutine { (x: Int) =>
      if (x > 0) x
      else -x
    }
    val c1 = call(abs(-5))
    assert(!c1.resume)
    assert(c1.result == 5)
    val c2 = call(abs(5))
    assert(!c2.resume)
    assert(c2.result == 5)
  }

  test("coroutine should contain two applications at the end of two branches") {
    val c1 = coroutine { (x: Int) => x }
    val c2 = coroutine { (x: Int) =>
      if (x > 0) {
        val y = c1(x)
      } else {
        val z = c1(-x)
      }
      x
    }
    val c = call(c2(5))
    assert(!c.resume)
    assert(c.result == 5)
    assert(c.isCompleted)
  }

  test("coroutine should contain two assignments at the end of two branches") {
    val c1 = coroutine { (n: Int) => 2 * n }
    val c2 = coroutine { (x: Int) =>
      var y = 0
      if (x > 0) {
        val z = c1(x)
        y = z
      } else {
        val z = c1(-x)
        y = z
      }
      y
    }
    val c = call(c2(5))
    assert(!c.resume)
    assert(c.result == 10)
  }

  test("coroutine should have an integer argument and a string local variable") {
    val stringify = coroutine { (x: Int) =>
      val s = x.toString
      s
    }
    val c = call(stringify(11))
    assert(!c.resume)
    assert(c.result == "11")
  }

  test("coroutine should assign") {
    val assign = coroutine { (x: Int) =>
      var y = 0
      y = x + 1
      y
    }
    val c = call(assign(5))
    assert(!c.resume)
    assert(c.result == 6)
  }

  test("coroutine should contain a while loop") {
    val number = coroutine { () =>
      var i = 0
      while (i < 10) {
        i += 1
      }
      i
    }
    val c = call(number())
    assert(!c.resume)
    assert(c.result == 10)
  }

  test("coroutine should contains a while loop with a yieldval") {
    val numbers = coroutine { () =>
      var i = 0
      while (i < 10) {
        yieldval(i)
        i += 1
      }
      i
    }
    val c = call(numbers())
    for (i <- 0 until 10) {
      assert(c.resume)
      assert(c.value == i)
    }
    assert(!c.resume)
    assert(c.result == 10)
    assert(c.isCompleted)
  }

  test("coroutine should correctly skip the while loop") {
    val earlyFinish = coroutine { (x: Int) =>
      var i = 1
      while (i < x) {
        yieldval(i) 
        i += 1
      }
      i
    }
    val c = call(earlyFinish(0))
    assert(!c.resume)
    assert(c.result == 1)
    assert(c.isCompleted)
  }

  test("coroutine should have a nested if statement") {
    val numbers = coroutine { () =>
      var z = 1
      var i = 1
      while (i < 5) {
        if (z > 0) {
          yieldval(z * i)
          z = -1
        } else {
          yieldval(z * i)
          z = 1
          i += 1
        }
      }
    }
    val c = call(numbers())
    for (i <- 1 until 5) {
      assert(c.resume)  
      assert(c.value == i)
      assert(c.resume)  
      assert(c.value == -i)
    }
    assert(!c.resume)
    assert(c.result == (()))
  }

  test("an anonymous coroutine should be applied") {
    coroutine { (x: Int) => x }
  }

  test("if statement should be properly regenerated") {
    val addOne = coroutine { (x: Int) =>
      if (x > 0) {
        x
      } else {
        -x
      }
      x + 1
    }
    val c1 = call(addOne(1))
    assert(!c1.resume)
    assert(c1.result == 2)
    val c2 = call(addOne(-1))
    assert(!c2.resume)
    assert(c2.result == 0)
  }

  test("if statement with unit last statement should be properly generated") {
    val addOne = coroutine { () =>
      var x = 5
      if (0 < x) {
        x = 2
        ()
      } else {
        x = 1
        ()
      }
      x
    }
    val c = call(addOne())
    assert(!c.resume)
    assert(c.result == 2)
  }

  test("coroutine should yield every second element") {
    val rube = coroutine { (x: Int) =>
      var i = 0
      while (i < x) {
        if (i % 2 == 0) yieldval(i)
        i += 1
      }
      i
    }
    val c = call(rube(5))
    for (i <- 0 until 5; if i % 2 == 0) {
      assert(c.resume)
      assert(c.value == i)
    }
    assert(!c.resume)
    assert(c.result == 5)
  }

  test("coroutine should yield x, 117, and -x") {
    val rube = coroutine { (x: Int) =>
      var z = x
      yieldval(z)
      yieldval(117)
      -z
    }
    val c = call(rube(7))
    assert(c.resume)
    assert(c.value == 7)
    assert(c.resume)
    assert(c.value == 117)
    assert(!c.resume)
    assert(c.result == -7)
    assert(c.isCompleted)
  }

  test("coroutine should yield 117, an even number and 17, or a negative odd number") {
    var rube = coroutine { () =>
      var i = 0
      while (i < 4) {
        var z = i
        if (i % 2 == 0) {
          yieldval(117)
          yieldval(z)
          yieldval(17)
        } else {
          yieldval(-z)
        }
        i += 1
      }
      i
    }
    val c = call(rube())
    assert(c.resume)
    assert(c.value == 117)
    assert(c.resume)
    assert(c.value == 0)
    assert(c.resume)
    assert(c.value == 17)
    assert(c.resume)
    assert(c.value == -1)
    assert(c.resume)
    assert(c.value == 117)
    assert(c.resume)
    assert(c.value == 2)
    assert(c.resume)
    assert(c.value == 17)
    assert(c.resume)
    assert(c.value == -3)
    assert(!c.resume)
    assert(c.result == 4)
    assert(c.isCompleted)
  }

  test("coroutine should yield first variable, then second variable, then first") {
    val rube = coroutine { () =>
      val x = 1
      yieldval(x)
      val y = 2
      yieldval(y)
      x
    }

    val c = call(rube())
    assert(c.resume)
    assert(c.value == 1)
    assert(c.resume)
    assert(c.value == 2)
    assert(!c.resume)
    assert(c.result == 1)
    assert(c.isCompleted)
  }

  test("coroutine should yield x, then y or z, then x again") {
    val rube = coroutine { (v: Int) =>
      val x = 1
      yieldval(x)
      if (v < 0) {
        val y = x * 3
        yieldval(y)
        yieldval(-y)
      } else {
        val z = x * 2
        yieldval(z)
        yieldval(-z)
      }
      x
    }

    val c0 = call(rube(5))
    assert(c0.resume)
    assert(c0.value == 1)
    assert(c0.resume)
    assert(c0.value == 2)
    assert(c0.resume)
    assert(c0.value == -2)
    assert(!c0.resume)
    assert(c0.result == 1)
    assert(c0.isCompleted)

    val c1 = call(rube(-2))
    assert(c1.resume)
    assert(c1.value == 1)
    assert(c1.resume)
    assert(c1.value == 3)
    assert(c1.resume)
    assert(c1.value == -3)
    assert(!c1.resume)
    assert(c1.result == 1)
    assert(c1.isCompleted)
  }

  test("coroutine should yield x, z, x, 117, or just x, 117") {
    val rube = coroutine { (v: Int) =>
      val x = v
      yieldval(x)
      if (x > 0) {
        val z = 1
        yieldval(z)
        yieldval(x)
      }
      117
    }

    val c0 = call(rube(5))
    assert(c0.resume)
    assert(c0.value == 5)
    assert(c0.resume)
    assert(c0.value == 1)
    assert(c0.resume)
    assert(c0.value == 5)
    assert(!c0.resume)
    assert(c0.result == 117)
    assert(c0.isCompleted)

    val c1 = call(rube(-7))
    assert(c1.resume)
    assert(c1.value == -7)
    assert(!c1.resume)
    assert(c1.result == 117)
    assert(c1.isCompleted)
  }

  test("should lub nested coroutine calls and returns") {
    val id = coroutine { (xs: List[Int]) => xs }
    val attach = coroutine { (xs: List[Int]) =>
      val ys = id(xs)
      "ok" :: ys
    }

    val c = call(attach(1 :: Nil))
    assert(!c.resume)
    assert(c.result == "ok" :: 1 :: Nil)
    assert(c.isCompleted)
  }

  test("nested coroutine definitions should not affect type of outer coroutine") {
    val rube: Coroutine._1[List[Int], Nothing, List[Int]] = coroutine {
      (xs: List[Int]) =>
      val nested = coroutine { (x: Int) =>
        yieldval(-x)
        x
      }
      2 :: xs
    }

    val c = call(rube(1 :: Nil))
    assert(!c.resume)
    assert(c.result == 2 :: 1 :: Nil)
    assert(c.isCompleted)
  }

  test("two nested loops should yield correct values") {
    val rube = coroutine { (xs: List[Int]) =>
      var left = xs
      while (left != Nil) {
        var i = 0
        while (i < left.head) {
          yieldval(i)
          i += 1
        }
        left = left.tail
      }
      0
    }

    val c = call(rube(List(1, 2, 3)))
    assert(c.resume)
    assert(c.value == 0)
    assert(c.resume)
    assert(c.value == 0)
    assert(c.resume)
    assert(c.value == 1)
    assert(c.resume)
    assert(c.value == 0)
    assert(c.resume)
    assert(c.value == 1)
    assert(c.resume)
    assert(c.value == 2)
    assert(!c.resume)
    assert(c.result == 0)
    assert(c.isCompleted)
  }
}


class WideValueTypesTest extends FunSuite with Matchers {
  test("should use a long stack variable") {
    val rube = coroutine { (x: Long) =>
      var y = x
      y = x * 3
      yieldval(-x)
      yieldval(y)
      x * 2
    }

    val c = call(rube(15L))
    assert(c.resume)
    assert(c.value == -15L)
    assert(c.resume)
    assert(c.value == 45L)
    assert(!c.resume)
    assert(c.result == 30L)
  }

  test("should use a double stack variable") {
    val rube = coroutine { (x: Double) =>
      var y = x
      y = x * 4
      yieldval(-x)
      yieldval(y)
      x * 2
    }

    val c = call(rube(2.0))
    assert(c.resume)
    assert(c.value == -2.0)
    assert(c.resume)
    assert(c.value == 8.0)
    assert(!c.resume)
    assert(c.result == 4.0)
  }

  test("should call a coroutine that returns a double value") {
    val twice = coroutine { (x: Double) =>
      x * 2
    }
    val rube = coroutine { (x: Double) =>
      yieldval(x)
      yieldval(twice(x))
      x * 4
    }

    val c = call(rube(2.0))
    assert(c.resume)
    assert(c.value == 2.0)
    assert(c.resume)
    assert(c.value == 4.0)
    assert(!c.resume)
    assert(c.result == 8.0)
    assert(c.isCompleted)
  }
}
