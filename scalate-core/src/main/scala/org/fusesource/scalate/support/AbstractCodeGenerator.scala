/**
 * Copyright (C) 2009-2011 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
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
package org.fusesource.scalate.support

import scala.collection.immutable.TreeMap
import util.parsing.input.{ Positional, OffsetPosition, Position }
import org.fusesource.scalate.{ TemplateSource, Binding, TemplateEngine }
import org.fusesource.scalate.util.Log

import scala.language.postfixOps

object AbstractCodeGenerator extends Log

/**
 * Provides a common base class for CodeGenerator implementations.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
abstract class AbstractCodeGenerator[T] extends CodeGenerator {

  abstract class AbstractSourceBuilder[T] {
    var indentLevel = 0
    var code = ""
    var generatedPositions = Map[OffsetPosition, Int]()

    def <<(): this.type = <<("")

    def <<(line: String): this.type = {
      for (i <- 0 until indentLevel) {
        code += "  "
      }
      code += line + "\n"
      this
    }

    def <<[T](list: List[T]): this.type = {
      for (i <- 0 until indentLevel) {
        code += "  "
      }
      for (value <- list) value match {
        case text: Positional => this << text.pos << text.toString
        case pos: Position => this << pos
        case _ => code += value.toString
      }
      code += "\n"
      this
    }

    def <<(pos: Position): this.type = {
      if (pos != null) {
        pos match {
          case p: OffsetPosition =>
            generatedPositions = generatedPositions + (p -> current_position)
          case _ =>
        }
      }
      this
    }

    def current_position = {
      code.length + (indentLevel * 2)
    }

    def positions() = {
      var rc = new TreeMap[OffsetPosition, OffsetPosition]()(new Ordering[OffsetPosition] {
        def compare(p1: OffsetPosition, p2: OffsetPosition): Int = {
          val rc = p1.line - p2.line
          if (rc == 0) {
            p1.column - p2.column
          } else {
            rc
          }
        }
      })
      generatedPositions.foreach {
        entry =>
          rc = rc + (OffsetPosition(code, entry._2) -> entry._1)
      }
      rc
    }

    def indent[T](op: => T): T = {
      indentLevel += 1
      val rc = op
      indentLevel -= 1
      rc
    }

    def generate(
      engine: TemplateEngine,
      source: TemplateSource,
      bindings: Traversable[Binding],
      statements: List[T]): Unit = {

      val packageName = source.packageName
      val className = source.simpleClassName

      this << "/* NOTE this file is autogenerated by Scalate : see http://scalate.fusesource.org/ */"
      if (packageName != "") {
        this << "package " + packageName
      }

      this << ""

      val engineImports = engine.importStatements
      for (i <- engineImports) {
        this << i
      }
      if (!engineImports.isEmpty) {
        this << ""
      }

      this << "object " + className + " {"
      indent {
        // We prefix the function an variables with $_scalate_$ to avoid namespace pollution which could
        // conflict with definitions declared in the template
        this << "def $_scalate_$render($_scalate_$_context: _root_.org.fusesource.scalate.RenderContext): Unit = {"
        indent {
          generateInitialImports
          generateBindings(bindings) {
            generateTemplatePackage(source, bindings)

            generate(statements)
            if (statements.isEmpty) {
              // lets generate a dummy statement to avoid a compile error
              this << "$_scalate_$_context << \"\""
            }
          }
        }
        this << "}"
      }
      this << "}"
      this <<

      this <<;
      this << "class " + className + " extends _root_.org.fusesource.scalate.Template {"
      indent {
        this << "def render(context: _root_.org.fusesource.scalate.RenderContext): Unit = " + className + ".$_scalate_$render(context)"
      }
      this << "}"

    }

    def generateInitialImports(): Unit = {}

    def generate(statements: List[T]): Unit

    def generateBindings(bindings: Traversable[Binding])(body: => Unit): Unit = {
      bindings.foreach(arg => {
        this << ";{"
        indentLevel += 1
        generateBinding(arg)
      })

      body

      bindings.foreach(arg => {
        indentLevel -= 1
        this << "}"
      })
    }

    def generateBinding(binding: Binding): Unit = {
      def generateImplicit = if (binding.isImplicit) "implicit " else ""

      def attributeMethodCall: List[_] = if (binding.defaultValue.isEmpty) {
        "attribute(" :: asString(binding.name) :: ")" :: Nil
      } else {
        "attributeOrElse(" :: asString(binding.name) :: ", " :: binding.defaultValuePositionalOrText :: ")" :: Nil
      }

      this << generateImplicit :: binding.kind :: " " :: binding.name :: ": " + binding.classNamePositionalOrText :: " = $_scalate_$_context." :: attributeMethodCall

      /*
            this << generateImplicit + binding.kind + " " + binding.name + ":" + binding.className + " = ($_scalate_$_context.attributes.get(" + asString(binding.name) + ") match {"
            indent {
              //this << "case Some(value: "+binding.className+") => value"
              this << "case Some(value) => value.asInstanceOf[" + binding.className + "]"
              if (binding.defaultValue.isEmpty) {
                this << "case None => throw new _root_.org.fusesource.scalate.NoValueSetException(" + asString(binding.name) + ")"
              } else {
                this << "case None => " + binding.defaultValue.get
              }
            }
            this << "});"
      */

      if (binding.importMembers) {
        this << "import " + binding.name + "._"
      }
    }

    protected def generateTemplatePackage(source: TemplateSource, bindings: Traversable[Binding]): Unit = {
      val templatePackage = TemplatePackage.findTemplatePackage(source).getOrElse(new DefaultTemplatePackage())
      this << templatePackage.header(source, bindings.toList)
      this <<
    }

    def asString(text: String): String = {
      val buffer = new StringBuffer
      buffer.append("\"")
      text.foreach(c => {
        if (c == '"')
          buffer.append("\\\"")
        else if (c == '\\')
          buffer.append("\\\\")
        else if (c == '\n')
          buffer.append("\\n")
        else if (c == '\r')
          buffer.append("\\r")
        else if (c == '\b')
          buffer.append("\\b")
        else if (c == '\t')
          buffer.append("\\t")
        else if ((c >= '#' && c <= '~') || c == ' ' || c == '!')
          buffer.append(c)
        else {
          buffer.append("\\u")
          buffer.append("%04x".format(c.asInstanceOf[Int]))
        }
      })
      buffer.append("\"")
      buffer.toString
    }
  }

}
