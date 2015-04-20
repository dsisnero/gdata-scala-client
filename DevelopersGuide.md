# Introduction #

This document is intended to give a thorough view of the design and implementation of the Google Data Scala client library. If you want to know more about how this library works, or contribute to the project, this is the starting point. It assumes the reader is familiar with [Scala](http://scala-lang.org), the [Google Data API protocol](http://code.google.com/apis/gdata/index.html), [Atom](http://www.atomenabled.org/developers/syndication/atom-format-spec.php) and the [Atom Publishing Protocol](http://ietfreport.isoc.org/idref/draft-ietf-atompub-protocol/).

_Note: The Scala client library needs a working [Scala](http://scala-lang.org) installation newer than 2.7.1. It also works with the current 2.8.0-beta._

# Setup #

This section describes the steps to take if you want to start developing the library. It describes how to checkout the sources, build and run the tests. Everything is straight forward, so most readers used to [Scala](http://scala-lang.org) development can skip this section and come back if needed.

Before you start, make sure you have installed the following software:

  * An [svn](http://subversion.tigris.org/) client.
  * [ant](http://ant.apache.org)
  * A [Scala](http://scala-lang.org) distribution greater than 2.7.1 (including 2.8.0-beta).
  * [emma](http://emma.sourceforge.net/) for code coverage (optional).

## Setting up your working copy ##

Follow the instructions on the [project website](http://code.google.com/p/gdata-scala-client/source/checkout) to checkout a working copy. Then `cd` into the directory and type
```
$ ant build
```
Make sure the environment variable `SCALA_HOME` points to your Scala installation.

To build and run the tests, type
```
$ ant test
```

The build process can be configured by setting the appropriate values in `build.properties`. If you have installed [emma](http://emma.sourceforge.net/), make sure you update the properties file to point to its installation directory:
```
emma.dir=/usr/local/soft/emma-2.0.5312/lib
```

Now you can get an html report of test coverage:
```
$ ant coverage
```

# Library Overview #

The Scala client library handles XML serialization, HTTP connection, authentication and query building for Google Data. The library is centered around serializing and deserializing XML, and provides a core set of classes called _Picklers_, on which all the serialization code is based. The object model is decoupled from serialization code, and the library provides classes for Atom and Google Data common elements. Supported services, like YouTube or Calendar, define new model classes and picklers, using exactly the same approach as the core library. Users that need to extend the library (for a new service, or to accommodate an extension to an existing service) can use the same approach.

The library provides support for making queries and updating data through HTTP. The approach is straight forward, and similar to the road taken by the [Java client library](http://code.google.com/p/gdata-java-client/). Users connect to Google services through `Service` objects, that provide methods for making queries and updating data on the server. Queries are encoded as specific URLs, and the Query builder provides a natural syntax for expressing complex queries. These are the objects that are most visible to the users of this library.

## Conventions ##

The library goes to great lengths to provide a type-safe layer on top of the underlying XML protocol. Whenever possible, Scala data types are used to represent the underlying data. Here is a high level view of the conventions followed throughout the library, that should be followed by all extensions:
  * whenever a piece of data is optional, it is represented as an `Option[T]`. **null** values should never be valid values.
  * whenever a default value is specified for an element or attribute, such data is represented as a value of the right type, and the default value is filled in when parsing.
  * all data is mutable. This is in order to support update (although some services are read-only).
  * serialization code is separated from data representation. Picklers (serialization code) are usually found as values in the companion object of data classes.
  * errors in the HTTP layer are signaled as exceptions.
  * convenience methods and constructors are provided for the most common cases. For instance, an `EventEntry` has a constructor taking a title and a description as plain strings (although they could be `html` or `xhtml`) and two `DateTime` objects (although an event might have only one or none).
  * names in data classes closely follow the XML data they model. If an [event entry](http://code.google.com/apis/gdata/elements.html#gdEventKind) has an element called `eventStatus`, the `EventEntry` class will have a field named the same. This makes it easy to reuse existing documentation.

## Organization ##

Along with the core classes for XML serialization (_picklers_), there are classes that model the various data used by the Google Data protocol (like feeds, contacts, calendar entries, etc) and HTTP connection classes. The library is split in packages along the following lines:
  * `com.google.xml.combinators` The XML serialization core
  * `com.google.gdata.data` Google Data common classes, including the [Atom protocol](http://atomenabled.org/developers/syndication/atom-format-spec.php) classes.
    * `kinds` Google [kinds](http://code.google.com/apis/gdata/elements.html) classes (common data classes used by several Google services).
    * `media` [Media Rss](http://search.yahoo.com/mrss) classes (used by the YouTube service).
    * `util` various common classes like `DateTime` or `NormalPlayTime`
  * `com.google.gdata.client` Google Data HTTP connection and authentication handling.
  * `com.google.gdata.<servicename>` specific data classes and services for each implemented service, like Calendar or YouTube.

# XML Pickler Combinators #

The serialization code is built around [pickler combinators](http://research.microsoft.com/~akenn/fun/picklercombinators.pdf). A _pickler_ is an object that can serialize and deserialize some type. The library provides implementations for base cases, and _combinators_ for assembling more sophisticated picklers out of simple ones. This should strike a familiar note if you've ever used the [combinator parsing](http://www.scala-lang.org/docu/files/api/index.html) library in the Scala distribution.

Pickler combinators are implemented in `com.google.xml.combinators`. The `Pickler` interface needs just two methods:
```
  abstract class Pickler[A] {
    def pickle(v: A, in: XmlOutputStore): XmlOutputStore
    def unpickle(in: XmlInputStore): PicklerResult[A]
  }
```
The input and output types are abstractions over the XML representation. The result type of the unpickle method is a `PicklerResult`, which can be either `Success` or `Error`. All errors are issued through `PicklerResult` values, as this allows combinators to decide later if an error should fail the whole pickler, or try another alternative.

## Primitive picklers ##

The library provides picklers for primitive types. It is interesting to note that they do not commit to an attribute or an element content. Combinators will decide that when they wrap one of the basic picklers.
  * `text` This is a pickler for Strings.
  * `boolVal` This is a pickler for Booleans.
  * `intVal` This is a pickler for Integers.
  * `doubleVal` This is a pickler for Double values.
  * `dateTime` This is a pickler for date/time in RFC 3339 format.

## Combinators ##

Combinators are functions that take as arguments one or more picklers, and return a pickler for a more complex data type. Sometimes they return a pickler for the same data type as their argument, but perform some processing on input, like `attr` and `elem`. To keep the exposition clear, descriptions of picklers will talk from the point of view of _unpickling_ (parsing the input), but keep in mind that the pickling part is always implied:

  * `elem(label, p)` Wraps the given pickler in an element with the given `label`. For instance, `elem('id', text)` creates a pickler that accepts strings inside an element called 'id': `<id>Test</id>`. Namespaces can be specified as well (see the [API documentation](http://gdata-scala-client.googlecode.com/svn/trunk/docs/api/index.html)).
  * `attr(label, p)` Wraps the given pickler in an attribute with the given `label`.
  * `seq(pa, pb)` The two picklers are applied in sequence. This combinator is usually written as `pa ~ pb`. The `~` operator is a convenience method defined in the `Pickler` interface, so that sequences can be written using an infix operator.
  * `interleaved(p)`. Makes the given combinator accept input sequences in any order. Unknown elements are ignored. This is used heavily throughout the library.
  * `opt(p)` Turns the given pickler into an optional pickler. If `p` fails, `opt(p)` succeeds with a value of `None`.
  * `rep(p)` Repeatedly apply `p`, until it fails. It results into a list of values unpickled successfully by `p`.
  * `wrap` A combinator used to adapt one type to another. It has a dedicated section below.
  * other, less common combinators. See the [scaladoc](http://gdata-scala-client.googlecode.com/svn/trunk/docs/api/index.html).

## Example ##

All this has been pretty abstract, so now we turn to a simple example. Let's say we need to write a pickler for Google [rating](http://code.google.com/apis/gdata/elements.html#gdRating) elements, with the following schema:
```
rating =
   element gd:rating {
      gdCommonProperties,
      attribute rel { xs:string }?,
      attribute value { xs:int }?,
      attribute average { xs:float }?,
      attribute min { xs:int },
      attribute max { xs:int },
      attribute numRaters { xs:int }?
   }
```

We start by defining a class for ratings:
```
case class Rating(average: Option[Double], 
    min: Int, 
    max: Int, 
    numRaters: Option[Int],
    rel: String,
    value: Option[Int]) {
}
```

We follow the conventions and turn all elements and attributes into fields with the same name. Optional elements get an `Option` type. Next we need to define a pickler for ratings in the companion object:
```
object Rating {
  import Uris.gdNs
  import Picklers._

  def pickler: Pickler[Rating] = 
    elem("rating", 
          opt(attr("average", doubleVal))
        ~ attr("min", intVal)
        ~ attr("max", intVal)
        ~ opt(attr("numRaters", intVal))
        ~ default(attr("rel", text), "overall")
        ~ opt(attr("value", intVal)))(gdNs)
}
```
The pickler definition reads almost like an RNG schema for the rating element. The `default` combinator provides a value in case the attribute _rel_ is not present. The last argument to `elem` is the element namespace (in this case, the Google data namespace).

If you try to compile this code, you'll notice there is a type error: The return type of `pickler` is not `Pickler[Rating]`, but some complex type involving `~`. The reason has to do with the sequence combinator, which returns picklers for a pair-like type formed by the two picklers:
```
def seq[A, B](pa: => Pickler[A], pb: => Pickler[B]): Pickler[~[A, B]]
```
As in the combinator parsing library, `~` is both a convenience method in trait `Pickler`, and a holder class. To fix this error, we need the `wrap` combinator

## Wrap ##

We can fix our code by giving the library a way to transform between our type to the type it understands. The `wrap` combinator does just that: Given a pickler for some type `A`, and two functions `f: A => B` and `g: B => A`, it gives back a pickler for type `B`.
```
  def wrap[A, B](pb: => Pickler[A])(g: A => B)(f: B => A): Pickler[B]
```

```
  def rawPickler = // the previous definition

  def pickler: Pickler[Rating] =
    wrap (rawPickler) {
      case avg ~ min ~ max ~ numRaters ~ rel ~ value => Rating(avg, min, max, numRaters, rel, value)
    } (fromRating)

  private def fromRating(r: Rating) = 
    (new ~(r.average, r.min) ~ r.max ~ r.numRaters ~ r.rel ~ r.value)
```

Thanks to type inference, we got away easy: all types are filled in by the compiler. The first argument to `wrap` is the raw pickler, the second one is a function (using Scala's support for patterns as partial functions) that constructs `Rating` objects out of the pair-like structure. The last argument breaks a `Rating` object into pairs.

Oh, and one more thing: because `Rating` is a case class, we can use the automatically generated functions to get a much cleaner definition:
```
  def rawPickler = // as before

  def pickler: Pickler[Rating] =
    wrap (rawPickler) (Rating.apply) (Rating.unapply)
```

This code uses implicit conversions behind the scenes to adapt the given functions to the expected types. There is one sad thing, though: [ticket #508](http://lampsvn.epfl.ch/trac/scala/ticket/508). For the moment, the `unapply` method cannot be used.

## Extensions ##

The Google Data protocol is highly extensible. Most elements can be extended with new attributes or elements, and the library provides a solution based on picklers. The basic idea is to collect any unparsed content of an element and store it as XML. An extension is then just another pickler, which combined with an extensible element operates on the collected data. Here's an example involving the Atom `link` element.
```
case class Link(href: String, 
    rel: Option[String],
    tpe: Option[String],
    hrefLang: Option[String],
    title: Option[String],
    length: Option[String]) extends HasStore
    
    
object Link {
  implicit val nsAtom = Uris.atomNs
  
  val contentsPickler: Pickler[Link] = wrap(attr("href", text) 
        ~ opt(attr("rel", text))
        ~ opt(attr("type", text))
        ~ opt(attr("hrefLang", text))
        ~ opt(attr("title", text))
        ~ opt(attr("length", text))) (Link.apply) (toPair)
  
  lazy val pickler: Pickler[Link] = elem("link", makeExtensible(contentsPickler))
```

The interesting thing to note is the call to `makeExtensible` on the `contentsPickler`. This combinator simply stores whatever was not parsed by the given pickler into a field of the `Link` class. Note that `Link` extends `HasStore`, a trait that declares a `store` field for that purpose. Remember that, even though we talk only about parsing, the store goes both ways: when pickling a `Link`, all unknown elements are pickled too.

Suppose link elements are extended with a child element called `webContent`:
```
extend(pickler, elem("webContent", text)(Uris.gCalNs))
```
This returns a pickler that handles the additional element.

This scheme works fine for simple cases, but when a class contains objects of a class that was extended, this scheme will lose type information: the container will refer to its members by a super type. This is the case with feeds and entries, and the solution is described in a dedicated section.

# Common Data Classes #

The [Atom syndication protocol](http://www.atomenabled.org/developers/syndication/atom-format-spec.php) defines common constructs and the basic structure of feeds and entries. Their model classes and picklers are found under `com.google.gdata.data`, and follow the pattern described above with one exception: feeds and entries, which are described in the next section. The library uses a custom `DateTime` class, whose definition and picklers are found in `com.google.gdata.data.util`. This class handles time zones and parses dates in the RFC 3339 format.

Google defines a number of common classes, and their implementation is found in package `com.google.gdata.data.kinds`. Implementation is straight forward. The interesting cases are `FeedLink` and `EntryLink`, which are special because they might enclose a feed or entry element, and are described below.

# Feeds and Entries #

Feeds are at the center of the Google Data API. Each kind of data that is published by a service is represented as a feed. A feed contains _entries_ along with metadata (such as author, id, or publish date). In turn, each entry represents a specific kind of data, like videos, events, messages. As such, entries are by far the most extended structure in Google Data.

Going back to the extensibility issue, when modeling Atom feeds, we are faced with the choice of a type for entries. Our first attempt might look like this:
```
  class AtomFeed {
    var author: String
    ...
    var entries: List[AtomEntry]
  }
```

But what happens when we implement YouTube video feeds? We will extend `AtomEntry` to define `VideoEntry`, but the feed will still 'know' only about `AtomEntry`, and user code would need to down cast. Worse, feeds are often interconnected: a video entry has a comments feed, a related video feed and a user profile feed. All these have different type of entries, who can in turn be extended later. Redefining each feed for each extension (and the transitive closure of its uses) is clearly not a scalable solution.

In turn, we abstract over the type of entries, and let feeds and entries evolve independently, combining them using mixin composition.

## The Cake Pattern ##

The [cake pattern](http://scala.sygneca.com/patterns/component-mixins) is used when different components need to abstract over their dependencies, evolve independently and do so in a type safe way. In our case, feeds and entries are the components that need to inter-operate, but the dependencies should not be hard coded in either of them. We start by defining a trait for Entries:
```
trait Entries {
  type Entry <: HasStore
  
  def entryPickler: Pickler[Entry] = elem("entry", makeExtensible(entryContentsPickler))(Uris.atomNs)
  def entryContentsPickler: Pickler[Entry]
}
```

This component provides an abstract type `Entry`: all components using entries use this abstract type when referring to an entry. This allows them to work with different implementations of `Entries`. It also provides a pickler for this abstract type, expressed in terms of a pickler for entry contents. The `entryContentsPickler` is a method that needs to be defined by concrete implementations, and which should remain abstract until the type is fixed to a concrete type.

Next we look at a component for feeds. Since feeds depend on entries, we'll use a self type annotation to express this requirement:
```
trait Feeds { this: Feeds with Entries =>
  type Feed <: Seq[Entry] with HasStore
  
  def feedPickler: Pickler[Feed] = elem("feed", makeExtensible(feedContentsPickler))(Uris.atomNs)
  
  def feedContentsPickler: Pickler[Feed]
```

This component is very similar to the previous one, except for the self type annotation, that reads like 'all instances that mix in `Feeds` should also mix in `Entries`'. This allows `Feeds` to be defined in terms of the abstract type `Entry`, for instance by making them implement `Seq[Entry]`.

## Atom Feeds ##

Once we have defined feed and entry components, we can refine them to model the most basic feeds: atom feeds. At each step, we need to refine the abstract type and define a contents pickler for the new bound:
```
trait AtomEntries extends Entries {
  type Entry <: AtomEntry
  
  class AtomEntry extends AnyRef with LinkNavigation with HasStore {
    var authors: List[Person] = Nil
    var categories: List[Category] = Nil
    // ...
  }

  lazy val atomEntryContentsPickler: Pickler[AtomEntry] = wrap (...) ({
    case authors ~ cats ~ .. =>
      (new AtomEntry).fillOwnFields(authors, cats, content, contribs, id, links, published, 
          rights, src, summary, title, updated)
  }) (fromEntry)
}
```

This new component provides a more specific type of entries, and defines the `AtomEntry` class along with its pickler. Note that the contents pickler method is **not** implemented yet: instead, a `atomEntryContentsPickler` is provided. This allows future extensions, that can reuse the atom entry pickler. Similarly, atom feeds follow:
```
trait AtomFeeds extends Feeds { this: AtomFeeds with Entries =>
  type Feed <: AtomFeed with HasStore

  class AtomFeed extends AnyRef with Seq[Entry] with HasStore {
    var authors: List[Person] = Nil
    // ..

    var entries: List[Entry] = Nil
  }

  lazy val atomFeedContentsPickler: Pickler[AtomFeed] = 
    wrap (interleaved(rep(atomPerson("author")) ~ rep(entryPickler))) ({
      case authors ~ entries => new AtomFeed(...)
  }) (fromAtomFeed)
}
```
Notice how the pickler is using the abstract method `entryPickler` to handle the abstract `Entry` type.

## Tying the knot ##

To bring everything together, we need to come up with a concrete class that can be instantiated. This means fixing the abstract types, and their picklers. This is usually done at the point of use, as after types are concrete, no further refinement is possible. We choose to define named classes for all feeds, as it is very likely to use the same feed in more than one place. The name should be the name of the feed type, prefixed by `Std`:
```
class StdAtomFeed extends AtomFeeds with AtomEntries {
  type Feed = AtomFeed
  type Entry = AtomEntry
  
  def feedContentsPickler = atomFeedContentsPickler
  def entryContentsPickler = atomEntryContentsPickler
}
```

Now we can instantiate standard atom feeds and use the types and picklers. For example, the following code is unpickling a feed from a file:
```
val atomFeed = new StdAtomFeed
val is = new FileInputStream(...)
val xmlStore = LinearStore.fromInputStream(is)
atomFeed.feedPickler.unpickle(xmlStore) match {
  case Success(feed, _) => println('Unpickled feed: ' + feed)
  case f: NoSuccess => println(f.toString)
}
```
The unpickle method takes an `XmlInputStore` as parameter, so we need to create one based on the input stream. Then we match on the result, to check if the parsing was successful or not.

## Writing feeds that are easily extensible ##

In this section we lay out the pattern for refining entries and feeds. This should be the way new feeds are added to the library. The goals are to have a consistent feel, and limit the amount of code a subclass needs to write in order to reuse super class picklers.

During this section we'll talk about entries, but keep in mind that the same pattern applies to feeds as well. All new entries should subclass `AtomEntries` and give a more specific upper bound to the abstract type of entry. They should also provide a pickler, named after the entry type plus the suffix `ContentsPickler`, implemented in terms of the superclass pickler.
```
trait VideoEntries extends AtomEntries {
  type Entry <: VideoEntry

  class VideoEntry extends AtomEntry {
    var noembed: Boolean = false
    var restricted: Boolean = false

    // ..
  }

  def videoEntryContentsPickler: Pickler[VideoEntry] =
    wrap (atomEntryContentsPickler ~ videoEntryExtra) ({
      case ae ~ (noembed ~ restricted) => 
        val me = new VideoEntry
        me.fromAtomEntry(ae)
        me.fillOwnFields(noembed, restricted)
    }) (fromVideoEntry)
  //...
}
```

In this example we have assumed the extra fields of video entries have been gathered in their own pickler `videoEntryExtra`, but this is not always necessary. The pickler is using the `atomEntryContentsPickler` to parse everything the superclass may contain, and wrapping the contents pickler to instantiate video entries. The interesting bit is the following two lines, which fill the fields of video entries:
  * a call to `fromAtomEntry`. This is a copy constructor (inherited from `AtomEntry`), which fills all its known fields
  * a call to `fillOwnFields`. This is a method defined in `VideoEntry`, that fills the additional fields defined in video entries.

These two methods should be implemented by all entries. Let's go back to the definition of `VideoEntry` and add the necessary methods:
```
class VideoEntry extends AtomEntry {
  // ..
  def fromVideoEntry(me: VideoEntry) {
    this.fromAtomEntry(me)
    fillOwnFields(me.media, me.noembed, me.restricted, me.viewCount, me.rating, me.comments)
  }

  def fillOwnFields(noembed: Boolean, restricted: Boolean): this.type = {
    this.noembed = noembed
    this.restricted = restricted
    this
  }
  // ..
}
```
Notice how the `fromVideoEntry` method is implemented in terms of `fromAtomEntry` and `fillOwnFields` (in our previous definition of `AtomEntry` we have glossed over the implementation of these two methods). This has the nice effect that any extension requires code that is proportional in size to the _delta_ the extension introduces. You can check how that works in [PlaylistVideoEntries](http://gdata-scala-client.googlecode.com/svn/trunk/docs/api/com/google/gdata/youtube/PlaylistVideoEntries.html), which extends video entries even further.

To sum it up, a new refinement of Entries should:
  * extend `AtomEntries` or a subclass (extending `Entries` directly seems very unlikely).
  * define a new concrete class that subclasses the current entry class, for instance `AtomEntry`.
  * refine the upper bound of the `Entry` type.
  * provide a `fromNewConcreteClass` method that copies all fields from the given object.
  * provide a `fillOwnFields` method that copies only the new fields.
  * provide a contents pickler named after the entry class plus `ContentsPickler`.

If the entry is part of a new feed,
  * create a concrete class called `StdNewFeed` which fixes all abstract members to sensible defaults.

## References to other feeds ##

Many times a feed or an entry references another feed. For instance, video entries need to refer to comments, which have a feed of their own. The way to refer to comment feeds is to declare an abstract value of the feed type, and use the `Feed` and `Entry` types defined by that value.
```
trait VideoEntries {
  val commentsFeed: StdAtomFeed
  // ...
  val comments: commentFeed.Feed
}
```

This declaration makes it explicit that video entries depend on a comment feed implementation. Clients of video entries will need to provide a concrete implementation for commentsFeed. Most likely this will be in a `Service` implementation, and such an implementation will want to make sure the same comments component is used by all components in the service. To achieve that, it needs to override the `comments` value with a singleton type.
```
class YouTubeService {
  val videos = new StdVideoFeed {
    override lazy val commentsFeed: YouTubeService.this.comments.type = comments
  }
  
  val comments = new StdCommentsFeed
  //..
}
```

This code says to the compiler that the value `comments` and `videos.commentsFeed` are the same, and therefore the `Entry` type in comments is the same as the `Entry` type in `videos.commentsFeed`. Without this wiring, the compiler would think that `comments.Feed` and `videos.commentsFeed.Feed` are different types, as the paths are different.

## FeedLink and EntryLink ##

Google defines two elements, `feedLink` and `entryLink`, which represent either the link to a feed, or an embedded feed (we talk only about feeds, but the same applies to entries). Because they might enclose a feed, their pickler needs to be able to handle an arbitrary embedded feed. They simply abstract over the type and pickler for the embedded feed.
```
class FeedLink[Feed] extends HasStore {
  // ..
}
object FeedLink {
  def contentsPickler[F](feedPickler: Pickler[F]): Pickler[FeedLink[F]]
}
```

# Query #

Google Data implements queries using GET requests to a specific URL. The library provides a class for building URLs using a nice syntax. Queries have two components: a _category_ part, and a _search_ part. Categories are introduced by a forward slash, while the search part is introduced by `matching`. They may have a number of _modifiers_, like `maxResults` or `orderBy`, to further control the result set. The base class `Query` defines the standard query syntax and handles the encoding of parameters into a URL. Supported services define subclasses of `Query` to handle new, specific parameters.

## Category Queries ##

To build a query that matches several categories, start with a `Query` object and add categories separated by `/`. This method is defined in the `Query` class and takes a `CategoryQuery`. To make more complex queries, you can use `|` and `!` to build alternatives and negation, respectively. These methods are defined in the `CategoryQuery` class. One obtains the URL by passing a base URL to `mkUrl`:

```
scala> var q1 = Query.empty / "Comedy" / "Fun"
scala> q1.mkUrl("http://gdata.youtube.com/feeds/api/videos")
res6: String = http://gdata.youtube.com/feeds/api/videos/-/Comedy/Fun

scala> q1 = Query.empty / (cat("Comedy") | cat("Fun"))
scala> q1.mkUrl("http://gdata.youtube.com/feeds/api/videos")
res5: String = http://gdata.youtube.com/feeds/api/videos/-/Comedy%7CFun
```

## Search queries ##

To make a text query, start with a `Query` object and call `matching`, passing an instance of `SearchQuery`. Search queries are built using `&`, `|`, `!` and `Text` instances, with the usual meaning. For instance, the following query is translated to an URL by passing a base URL:

```
scala> q1 = Query.empty matching Text("fun") & !Text("office")
scala> q1.mkUrl("http://gdata.youtube.com/feeds/api/videos")
res13: String = http://gdata.youtube.com/feeds/api/videos?q=fun+-office
```

## Modifiers ##

In addition to category and search queries, one can control the result set by adding _modifiers_. Modifiers are additional parameters that get shipped with a query, and control the number of results or ordering. This last example shows a query having all parts described above:
```
scala> q1 = Query.empty / "Comedy" matching Text("fun") & !Text("office") maxResults(10)
scala> q1.mkUrl("http://gdata.youtube.com/feeds/api/videos")
res15: String = http://gdata.youtube.com/feeds/api/videos/-/Comedy?max-results=10&q=fun+-office
```

## Service-specific queries ##

Most services accept additional parameters. Supported services get a specialized query class that implements such new features.

All new parameters should be implemented in terms of `Query.addParam`, for instance the [YouTubeQuery](http://gdata-scala-client.googlecode.com/svn/trunk/docs/api/com/google/gdata/youtube/YouTubeQuery.html) adds an 'orderBy' parameter:
```
class YouTubeQuery extends Query {
  def orderBy(ordering: String): this.type =
    addParam("orderby", ordering)
}
```

# Http and Google Data requests #

The library defines helper classes for making Google Data requests and handling authentication. The `Service` class holds everything together:
  * creates requests using a `RequestFactory`.
  * handles authentication tokens using an `AuthenticationFactory`.
  * makes queries using a `Query` class.

The request factory creates requests and handles common headers that should be added to each request. The `Service` class supports stateful services, like calendar, by handling redirects with a `gsessionid` parameter by saving it and shipping it with future queries. It also provides generic query methods that use picklers for handling request/response content. Any pickling errors are translated to exceptions at this point.

For example, the YouTubeService class provides convenient methods for accessing video feeds by hiding the concrete picklers and URLs to which queries are made:
```
class YouTubeService {
   val videos = new StdVideoFeed {
    override lazy val commentsFeed: YouTubeService.this.comments.type = comments
  }
  // ..

  /** Return a video feed matching the given query. */
  def getVideos(q: Query): videos.Feed = {
    query(q.mkUrl(YouTubeService.BASE_VIDEO_FEED), videos.feedPickler)
  }
}
```

# Implementing a new service #

To support a new service, one needs to
  * implement its feeds.
  * define a service-specific query class if the service has additional search parameters
  * define a subclass of `Service` which provides convenient methods for accessing the given feeds.

All these additional classes should go into a service-specific package below `com.google.gdata`.

# Tests #

The library has a collection of unit tests. They are organized in the same packages as the classes they test, but live under `tests/` instead of `src/`. Unit tests are written using [junit](http://www.junit.org/) 4. Most tests are straight forward, but feed tests are more interesting. Each feed test unpickles a saved feed under `test-data`, then pickles it back and uses [xml-test](http://code.google.com/p/xml-test) to check that the resulting XML document matches the input. This ensures no input elements are lost.

To write a test for a new feed, get an XML document retrieved from the server, by using [curl](http://curl.haxx.se/) for instance. Save it under `test-data/feeds`, then write a new test class that extends `FeedFileTest`.
```
class YouTubeFeedsTest extends AnyRef with FeedFileTest {
  @Test def testVideoFeed {
    testRoundtrip("feeds/video-feed-in.xml", (new StdVideoFeed).feedPickler, "//rating")
  }
  //...
}
```

`testRoundtrip` takes the input file, the pickler and any number of XPath elements that should be ignored when comparing.