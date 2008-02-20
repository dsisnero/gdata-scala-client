/* Copyright (c) 2008 Google Inc.
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


package com.google.gdata.data;

import com.google.xml.combinators._
import com.google.gdata.data.util.DateTime
import Picklers._
import Atom._

import scala.xml._

/** An Atom Entry. */
class Entry extends AnyRef with Extensible {
  var authors: List[Person] = Nil
  var categories: List[Category] = Nil
  var content: Option[Content] = None
  var contributors: List[Person] = Nil
  var id: String = ""
  var links: List[Link] = Nil
  var published: Option[DateTime] = None
  var rights: Option[String] = None
  var source: Option[Source] = None
  var summary: Option[Text] = None
  var title: Text = NoText
  var updated: DateTime = new DateTime(new java.util.Date())
  
  override def toString = {
    val sb = new StringBuffer
    sb.append("Authors: ").append(authors.mkString("", ", ", ""))
      .append("\nCategories: ").append(categories.mkString("", ", ", ""))
      .append("\nContent: ").append(content)
      .append("\nContributors: ").append(contributors.mkString("", ", ", ""))
      .append("\nId: ").append(id)
      .append("\nLinks: ").append(links.mkString("", ", ", ""))
      .append("\nPublished: ").append(published)
      .append("\nRights: ").append(rights)
      .append("\nSource: ").append(source)
      .append("\nSummary: ").append(summary)
      .append("\nTitle: ").append(title)
      .append("\nUpdated: ").append(updated)
      .toString
  }
}

object Entry {
  lazy val atomEntryContents =
    interleaved(
        rep(atomPerson("author"))
      ~ rep(Category.pickler)
      ~ opt(Content.pickler)
      ~ rep(atomPerson("contributor"))
      ~ elem("id", text)
      ~ rep(Link.pickler)
      ~ opt(elem("published", dateTime))
      ~ opt(elem("rights", text))
      ~ opt(Source.pickler)
      ~ opt(atomText("summary"))
      ~ atomText("title")
      ~ elem("updated", dateTime))
        
  lazy val pickler: Pickler[Entry] = wrap (elem("entry", atomEntryContents)) ({
    case authors ~ cats ~ content ~ contribs ~ id
         ~ links ~ published ~ rights ~ src ~ summary ~ title
         ~ updated => 
      val e = new Entry
      e.authors = authors
      e.categories = cats
      e.content = content
      e.contributors = contribs
      e.id = id
      e.links = links
      e.published = published
      e.rights = rights
      e.source = src
      e.summary = summary
      e.title = title
      e.updated = updated
      e
  }) (fromEntry)

  private def fromEntry(e: Entry) = (new ~(e.authors, e.categories) 
      ~ e.content
      ~ e.contributors
      ~ e.id
      ~ e.links
      ~ e.published
      ~ e.rights
      ~ e.source
      ~ e.summary
      ~ e.title
      ~ e.updated)
      
}