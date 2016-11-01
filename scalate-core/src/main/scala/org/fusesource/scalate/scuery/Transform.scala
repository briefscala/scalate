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
package org.fusesource.scalate.scuery

import xml.{ Attribute, Document, Elem, Node, NodeSeq, Null, Text }

import scala.language.implicitConversions

object Transform {
  implicit def toNodes(transform: Transform): NodeSeq = transform()
  implicit def toTraversable(transform: Transform): Traversable[Node] = transform()
}

/**
 * A helper class to make it easier to write new transformers within loops inside a ancestor transformer
 */
class Transform(val nodes: NodeSeq, ancestors: Seq[Node] = Nil) extends Transformer {
  def apply(): NodeSeq = apply(nodes, ancestors)

  implicit def toNodes(): NodeSeq = apply()

  implicit def toTraversable(): Traversable[Node] = apply()
}
