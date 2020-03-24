/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package oharastream.ohara.it.code

import java.lang.annotation.Annotation
import java.lang.reflect.{Method, Modifier}

import oharastream.ohara.common.rule.OharaTest
import oharastream.ohara.it.IntegrationTest
import oharastream.ohara.it.code.ClassUtils._
import org.junit.{After, Before, Test}
import org.scalatest.Matchers._

class TestTestCases extends OharaTest {
  /**
    * those classes don't have to extend correct super class.
    */
  private[this] val validTestGroups: Seq[Class[_]] = Seq(
    classOf[OharaTest],
    classOf[IntegrationTest]
  )

  @Test
  def testListTestClasses(): Unit = {
    val classes = testClasses()
    classes.size should not be 0
    classes.exists(_.getName.startsWith("oharastream.ohara.agent"))
    classes.exists(_.getName.startsWith("oharastream.ohara.client"))
    classes.exists(_.getName.startsWith("oharastream.ohara.common"))
    classes.exists(_.getName.startsWith("oharastream.ohara.connector"))
    classes.exists(_.getName.startsWith("oharastream.ohara.configurator"))
    classes.exists(_.getName.startsWith("oharastream.ohara.it"))
    classes.exists(_.getName.startsWith("oharastream.ohara.kafka"))
    classes.exists(_.getName.startsWith("oharastream.ohara.metrics"))
    classes.exists(_.getName.startsWith("oharastream.ohara.shabondi"))
    classes.exists(_.getName.startsWith("oharastream.ohara.stream"))
    classes.exists(_.getName.startsWith("oharastream.ohara.testing"))
  }

  @Test
  def failIfTestHasNoTestCase(): Unit = {
    val classAndCases: Map[Class[_], Set[String]] = testClasses().map(clz => clz -> testCases(clz)).toMap
    classAndCases.size should not be 0
    val invalidTests: Map[Class[_], Set[String]] = classAndCases
    // legal test class should have a super class from validTestNames
      .filter(_._2.isEmpty)

    if (invalidTests.nonEmpty)
      throw new IllegalArgumentException(s"${invalidTests.keys.map(_.getName).mkString(",")} have no test cases")
  }

  /**
    * fail if any test case have not extended the test catalog.
    * We will find any possible superClass is in [SmallTest, MediumTest, LargeTest] or not.
    */
  @Test
  def testSuperClassOfTestClass(): Unit = {
    val classAndSuperClasses = testClasses().map(c => c -> superClasses(c))
    classAndSuperClasses.size should not be 0
    val invalidClasses = classAndSuperClasses.filterNot {
      case (_, superClasses) => superClasses.exists(validTestGroups.contains)
    }
    if (invalidClasses.nonEmpty)
      throw new IllegalArgumentException(
        s"${invalidClasses.map(_._1.getName).mkString(",")}" +
          s" don't inherit test interfaces:${validTestGroups.mkString(",")}"
      )
  }

  /**
    * the name of class extending test group should start with "Test".
    */
  @Test
  def testNameOfTestClasses(): Unit = {
    val invalidClasses = classesInTestScope()
      .filterNot(isAnonymous)
      // there are many basic test cases for the various cases. Their names don't start with "Test" since they
      // are no prepared to test directly.
      .filterNot(isAbstract)
      .map(clz => clz -> superClasses(clz))
      .filter {
        case (_, supers) => supers.exists(validTestGroups.contains)
      }
      .filterNot {
        case (clz, _) => clz.getSimpleName.startsWith("Test")
      }
      .map(_._1)
    if (invalidClasses.nonEmpty)
      throw new IllegalArgumentException(
        s"those classes:${invalidClasses.map(_.getName).mkString(",")} extend one of ${validTestGroups.mkString(",")} but they are not abstract class, " +
          "and their name don't start with \"Test\""
      )
  }

  @Test
  def testCaseShouldHaveAnnotation(): Unit = {
    val methodNameInObject = classOf[Object].getMethods.map(_.getName)
    val requiredAnnotations = Seq(
      classOf[Test],
      classOf[Before],
      classOf[After]
    )

    def annotation(m: Method): Seq[Annotation] = if (m.getAnnotations == null) Seq.empty else m.getAnnotations

    val illegalCases: Map[Class[_], Set[Method]] = testClasses()
      .filter(clz => clz.getMethods != null)
      .map(
        clz =>
          clz -> clz.getMethods
          // public
            .filter(m => Modifier.isPublic(m.getModifiers))
            // non-protected
            .filterNot(m => Modifier.isProtected(m.getModifiers))
            // non-static
            .filterNot(m => Modifier.isStatic(m.getModifiers))
            // the methods generated by scala compiler
            .filterNot(_.getName.contains("$"))
            // the other return type is not able to be test case
            .filter(_.getReturnType == Void.TYPE)
            // the methods from object
            .filterNot(m => methodNameInObject.contains(m.getName))
            // no input parameters
            .filter(_.getParameterCount == 0)
            // it must have either @after or @Test
            .filterNot(m => annotation(m).map(_.annotationType()).exists(requiredAnnotations.contains))
            .toSet
      )
      .filter(_._2.nonEmpty)
      .toMap
    if (illegalCases.nonEmpty) {
      val sum = illegalCases
        .map {
          case (clz, methods) =>
            methods.map(m => s"${clz.getName}.${m.getName}").mkString(",")
        }
        .mkString("|")
      throw new AssertionError(
        s"Those test cases $sum MUST have @Test annotation. Or please remove the Public declaration from it"
      )
    }
  }
}
