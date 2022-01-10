package io.joern.kotlin2cpg.querying

import io.joern.kotlin2cpg.Kt2CpgTestContext
import io.shiftleft.codepropertygraph.generated.Operators
import io.shiftleft.proto.cpg.Cpg.DispatchTypes
import io.shiftleft.semanticcpg.language._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class CallTests extends AnyFreeSpec with Matchers {

  implicit val resolver = NoResolve

  "CPG for code with two functions with the same name, but different params" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |fun foo(x: Int, y: Int): Int {
        |  return x + y
        |}
        |
        |fun main(args : Array<String>) {
        |  val argc: Int = args.size
        |  println(foo(argc, 1))
        |}
        |
        |fun foo(x: Int): Int {
        |  return x * y
        |}
        |""".stripMargin)

    "should contain a call node for `argc`'s declaration with correct fields" in {
      cpg.call(Operators.assignment).size shouldBe 1

      val List(c) = cpg.call(Operators.assignment).l
      c.argument.size shouldBe 2
      c.code shouldBe "val argc: Int = args.size"
      c.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH.toString
      c.lineNumber shouldBe Some(8)
      c.columnNumber shouldBe Some(6)
    }

    "should contain a call node for `x + y` with correct fields" in {
      cpg.call(Operators.addition).size shouldBe 1

      val List(c) = cpg.call(Operators.addition).l
      c.argument.size shouldBe 2
      c.code shouldBe "x + y"
      c.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH.toString
      c.lineNumber shouldBe Some(4)
      c.columnNumber shouldBe Some(9)
    }

    // TODO: check for the dispatch types as well
    "should contain a call node for `println` with correct fields" in {
      cpg.call("println").size shouldBe 1

      val List(p) = cpg.call("println").l
      p.argument.size shouldBe 1
      p.lineNumber shouldBe Some(9)
      p.code shouldBe "println(foo(argc, 1))"
      p.methodFullName shouldBe "kotlin.io.println:kotlin.Unit(kotlin.Int)"
      p.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH.toString
      p.columnNumber shouldBe Some(2)
    }

    "should contain a call node for `foo` with correct fields" in {
      cpg.call("foo").size shouldBe 1

      val List(p) = cpg.call("foo").l
      p.argument.size shouldBe 2
      p.lineNumber shouldBe Some(9)
      p.code shouldBe "foo(argc, 1)"
      p.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH.toString
      p.columnNumber shouldBe Some(10)
    }

    "should contain the correct number of CALL nodes" in {
      cpg.call.size shouldBe 6
    }

    "should allow traversing from call to surrounding method" in {
      val List(x) = cpg.call("foo").method.l
      x.name shouldBe "main"
    }

    "should allow traversing from call to callee method" in {
      // TODO: check why the dedupBy is needed
      val List(x) = cpg.call.code("foo.*").callee.dedupBy(_.id).l
      x.name shouldBe "foo"
    }

    "should allow traversing from argument to parameter" in {
      // TODO: check why the dedupBy is needed
      val List(x) = cpg.call.code("foo.*").argument(1).parameter.dedupBy(_.id).l
      x.name shouldBe "x"
    }
  }

  "CPG for code with a class declaration " - {
    // TODO: add a second class with a similarly-named method
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |class Foo {
        |    fun add1(x: Int): Int {
        |        return x + 1
        |    }
        |}
        |
        |fun main(argc: Int): Int {
        | val x = Foo()
        | val y = x.add1(argc)
        | return y
        |}
        |""".stripMargin)

    "should contain a CALL node for `Foo()` with the correct fields set" in {
      cpg.call("Foo").size shouldBe 1

      val List(p) = cpg.call("Foo").l
      p.methodFullName shouldBe "mypkg.Foo.<init>:mypkg.Foo()"
      p.signature shouldBe "mypkg.Foo()"
      p.code shouldBe "Foo()"
      p.columnNumber shouldBe Some(9)
      p.lineNumber shouldBe Some(10)
    }

    "should contain a CALL node for `add1` with the correct fields" in {
      val List(p) = cpg.call("add1").l
      p.argument.size shouldBe 2
      p.dispatchType shouldBe DispatchTypes.STATIC_DISPATCH.toString
      p.code shouldBe "x.add1(argc)"
      p.columnNumber shouldBe Some(9)
      p.lineNumber shouldBe Some(11)
      p.methodFullName shouldBe "mypkg.Foo.add1:kotlin.Int(kotlin.Int)"
      p.signature shouldBe "kotlin.Int(kotlin.Int)"
    }

    "should contain a call node for `add1` with a receiver set" in {
      cpg.call("add1").receiver.size shouldBe 1

      val List(r) = cpg.call("add1").receiver.l
      r.code shouldBe "x"
    }
  }

  "CPG for code with a call to an implicitly imported stdlib fn " - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |fun doSome(x: String) {
        |  println("PLACEHOLDER")
        |}
        |
        |fun main(args : Array<String>) {
        |  doSome("SOME")
        |}
        |""".stripMargin)

    "should contain a call node for `println` with a fully-qualified stdlib METHOD_FULL_NAME" in {
      val List(c) = cpg.call(".*println.*").l
      c.methodFullName shouldBe "kotlin.io.println:kotlin.Unit(kotlin.Any)"
      c.signature shouldBe "kotlin.Unit(kotlin.Any)"
    }

    "should contain a call node for `doSome` with the correct METHOD_FULL_NAME set" in {
      val List(c) = cpg.call(".*doSome.*").l
      c.methodFullName shouldBe "mypkg.doSome:kotlin.Unit(kotlin.String)"
      c.signature shouldBe "kotlin.Unit(kotlin.String)"
    }
  }

  "CPG for code with a call to a constructor from library with type-inference support" - {
    lazy val cpg = Kt2CpgTestContext.buildCpg("""
        |package mypkg
        |
        |import com.google.gson.Gson
        |
        |fun main() {
        |  val serialized = Gson().toJson(productList)
        |  println(serialized)
        |}
        |""".stripMargin)

    "should contain a call node for `Gson()`" in {
      val List(c) = cpg.call("Gson.*").l
      c.methodFullName shouldBe "com.google.gson.Gson.<init>:com.google.gson.Gson()"
      c.signature shouldBe "com.google.gson.Gson()"
    }
  }
}