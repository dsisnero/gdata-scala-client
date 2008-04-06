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


package com.google.gdata.data.media;

import com.google.xml.combinators.~
import com.google.xml.combinators.Picklers._

/** 
 * A media:rating element, as defined by Media RSS.  @see http://search.yahoo.com/mrss
 *
 * @author Iulian Dragos 
 */
case class Rating(scheme: String, value: String)

object Rating {
  val pickler: Pickler[Rating] =
    (wrap (elem("rating", default(attr("scheme", text), "urn:simple") ~ text)(Uris.mediaNs))
          (Rating.apply)
          (r => new ~(r.scheme, r.value)))
}