package io.joern.c2cpg.passes

import io.joern.c2cpg.fixtures.CpgTypeNodeFixture
import io.shiftleft.codepropertygraph.generated.nodes.Type
import io.shiftleft.semanticcpg.language._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TypeNodePassTests extends AnyWordSpec with Matchers with CpgTypeNodeFixture {

  "TypeNodePass" should {
    "create TYPE nodes for used types" in CpgTypeNodeFixture("""
        |int main() {
        |  int x;
        |}""".stripMargin) { cpg =>
      cpg.typ.name.toSet shouldBe Set("int", "void", "ANY")
    }

    "create correct types for locals" in CpgTypeNodeFixture("""
       |int main() {
       |  char test[1024];
       |}""".stripMargin) { cpg =>
      cpg.local.l match {
        case List(test) =>
          test.typeFullName shouldBe "char[]"
          test.evalType.l shouldBe List("char[]")
          test.typ.l match {
            case List(t: Type) =>
              t.name shouldBe "char[]"
              t.fullName shouldBe "char[]"
              t.typeDeclFullName shouldBe "char[]"
            case _ => fail()
          }
        case _ => fail()
      }
    }
  }

}