# Introduction #

The Google Data Scala client library allows client software to connect to Google Services through the [Google Data API protocol](http://code.google.com/apis/gdata/index.html). It makes it easy to retrieve and modify data exposed by services like [Calendar](http://code.google.com/apis/calendar/) and [YouTube](http://code.google.com/apis/youtube/overview.html), without worrying about the underlying XML representation or how queries are made.

Currently the Google Data Scala client library supports the [YouTube API](http://code.google.com/apis/youtube/reference.html), [Contacts API](http://code.google.com/apis/contacts/) and the [Calendar API](http://code.google.com/apis/calendar/reference.html). It can be easily extended to support new services (and it will do so in time).

This document shows how to connect to a service, make queries and go through the results. It assumes the reader is familiar with the [Google Data protocol](http://code.google.com/apis/gdata/basics.html), and has some high-level understanding of the [YouTube](http://code.google.com/apis/youtube/overview.html) and [Calendar API](http://code.google.com/apis/youtube/overview.html)s.

# Installation #

Google Data Scala client is distributed as a jar file. As such, no special installation steps are needed. Just make sure your classpath contains the Scala standard library. If you don't intend to build from sources you can skip the following section.

_Note: The Scala client library needs a working [Scala](http://scala-lang.org) installation newer than 2.7.0-final. That means a [nightly build](http://www.scala-lang.org/downloads/distrib/files/nightly/), until a bug fix release is issued to deal with [this bug](https://lampsvn.epfl.ch/trac/scala/ticket/632)._

## Building ##

If you intend to build from sources, you also need:
  * [ant](http://ant.apache.org).
  * [junit](http://www.junit.org/) and [xmldiff](http://code.google.com/p/gdata-scala-client) for running unit tests (found under lib/ in the root project directory).
  * [emma](http://emma.sourceforge.net/) for code coverage.

Building the library is straight-forward. Make sure the environment variable `SCALA_HOME` points to your Scala installation. For most users, that's the only configuration needed:

```
ant build
```
should build the library in the default output directory (`classes/`). You can change the defaults in `build.properties`. You might also want to build the API documentation:
```
ant docs
```

For running tests and getting test coverage reports, see the DevelopersGuide.

# Library Overview #

The Scala client library handles XML serialization, HTTP connection, authentication and query building for Google Data. Serialization is provided for Atom and Google Data types, but the library makes it easy to write serializers for new data types. The `Service` class provides methods to retrieve and update data through the HTTP protocol, handling authentication through [Google Accounts](http://code.google.com/apis/accounts/index.html), using query classes to provide a simple syntax for building URLs.

If you are just evaluating the library or reading this for the first time, you can skip directly to the _Query_ tutorial.

## Organization ##

The XML serialization core is found in package `com.google.xml.combinators`, while the Google Data definitions are in `com.google.gdata.data` and sub-packages. Supported services have specific data under their own package below `com.google.gdata`, like `com.google.gdata.youtube` and `com.google.gdata.calendar`. Generic classes for handling the HTTP connection and authentication are located in `com.google.gdata.client`.

## Conventions ##

The library aims to have a consistent feel, and follows a few conventions. Whenever possible, Scala data types are used to represent the underlying data:
  * whenever a piece of data is optional, it is represented as an `Option[T]`.
  * whenever a default value is specified for an element or attribute, such data is represented as a value of the right type, and the default value is filled in when parsing.
  * all data is mutable. This allows for services where update is allowed.
  * serialization code is separated from data representation. Picklers (the name for serialization code) are usually found as values in the companion object of data classes.
  * errors in the HTTP transport layer are signaled as exceptions.
  * names in data classes closely follow the XML data they model. If an [event entry](http://code.google.com/apis/gdata/elements.html#gdEventKind) has an element called `eventStatus`, the `EventEntry` class will have a field named the same. This makes it easy to use existing Google documentation.

# Query #

The following sections present library features in a tutorial style. We will use the Scala interpreter, as the immediate feedback is extremely valuable when experimenting with a new library. Let's start the interpreter, giving the path to the library. We assume the current directory is the project directory, and the library has been built using the default output directory, `classes/`:

```
$ scala -cp classes/
Welcome to Scala version 2.7.0.r14476-b20080402152010 (Java HotSpot(TM) Server VM, Java 1.5.0_07).
Type in expressions to have them evaluated.
Type :help for more information.

scala>
```

The first thing we need is a service object. This object handles communication with the server, authentication, and provides convenient methods for making queries. Each supported service has a specific service class. The first example involves YouTube, so we instantiate a YouTubeService class giving our application name. We follow Google's recommendation to use names containing the company name, application name and version, separated by '-'.

```
scala> import com.google.gdata.youtube._
import com.google.gdata.youtube._

scala> val s = new YouTubeService("ACME-MoovieRowler-1.0")
```

We can already make queries on public feeds. If we wanted to query private feeds, at this point we'd need to authenticate. We don't worry about it for now, and proceed to make a category query. Queries have two components: a _category_ part, and a _search_ part. Categories are introduced by a forward slash, while the search part is introduced by `matching`. Queries my have a number of _modifiers_, like `maxResults` or `orderBy`, to further control the result set.

Our first query is a simple category query with no search part or modifiers. It retrieves videos in from _Comedy_:

```
scala> for (e <- s.getVideos(YouTubeQuery.empty / "Comedy")) println(e.rating)
Some(4.9/5 (18339 voters))
Some(4.82/5 (9717 voters))
Some(4.95/5 (3051 voters))
Some(4.95/5 (2654 voters))
Some(4.87/5 (8987 voters))
Some(4.79/5 (12032 voters))
Some(4.91/5 (6071 voters))
Some(4.92/5 (11520 voters))
Some(4.88/5 (2556 voters))
Some(4.89/5 (19477 voters))
Some(4.83/5 (8614 voters))
Some(4.91/5 (22535 voters))
Some(4.79/5 (12104 voters))
Some(4.92/5 (11285 voters))
Some(4.91/5 (11216 voters))
Some(4.8/5 (11264 voters))
Some(4.91/5 (21357 voters))
Some(4.84/5 (7161 voters))
Some(4.92/5 (10298 voters))
Some(4.9/5 (2485 voters))
Some(4.9/5 (24692 voters))
Some(4.87/5 (8520 voters))
Some(4.93/5 (4390 voters))
Some(4.89/5 (8273 voters))
Some(4.85/5 (13600 voters))
```

All queries to YouTube go through the `getVideos` method which takes a query object. Queries are created starting with an empty query, and adding categories, search terms and modifiers. The given query is encoded to `http://gdata.youtube.com/feeds/api/videos/-/Comedy`.

Notice that feeds support for comprehension. Also, optional elements such as rating are wrapped into `Some` objects. By default, Google Data servers return the first 25 results, so let's make a more complex query to retrieve the next ten entries:

```
scala> for (e <- s.getVideos(YouTubeQuery.empty / "Comedy" startIndex(26) maxResults(10))) println(e.title)
(Some(text),Frank Caliendo - Impressions)
(Some(text),Jeff Dunham and Peanut part 2)
(Some(text),This is why 11yr olds don't raid)
(Some(text),Achmed - O terrorista morto (Legendado))
(Some(text),Jeff Dunham (Spark Of Insanity) pt. 1)
(Some(text),Watcy Masch - Pozytywna wibracja)
(Some(text),Dave Chappelle's Funny Ass Shit)
(Some(text),Jim Carrey)
(Some(text),Jeff Dunham and Peanut (Part 1))
(Some(text),Horst Schlammer bei "Wer wird Millionaer" (4/4))
```

The URL for this query is ` http://gdata.youtube.com/feeds/api/videos/-/Comedy?start-index=26&max-results=10`.

The titles that we got back deserve a passing note. The first part (the optional 'text') is giving the type of contents of each title. Valid values are `text`, `html` and `xhtml`. For more information, see the [scaladoc](http://gdata-scala-client.googlecode.com/svn/trunk/docs/api/index.html).

To illustrate search terms, let's query the server for all videos in the Comedy category that match the words 'chuck norris' and don't match 'joke':

```
scala>  import com.google.gdata.Text
import com.google.gdata.Text
scala> for (e <- s.getVideos(YouTubeQuery.empty / "Comedy" matching Text("chuck norris") & !Text("joke"))) println(e.title)
(Some(text),Chuck Norris in Russia)
(Some(text),Comedy : Chuck Norris Fakten Witze Facts)
(Some(text),Mike Huckabee 2008 Ad- With Chuck Norris)
...
```

Search terms are introduced by `Text` and can be connected by `&` and `|`, having the usual meaning. Negation excludes a term from the result set. For a complete description of the query language, check the scala doc documentation on `Query` and `YouTubeQuery`.

## Navigation ##

What we have seen until now applies to queries in general, regardless of the specific service we are using. In this section we look at the way YouTube feeds are connected. For more detailed information check the [YouTube Reference](http://code.google.com/apis/youtube/reference.html#Navigating_between_feeds).

Video feeds have links to comments, video responses or related videos. The service object has support for the most common links. The following example gets related videos of a given video:

```
scala> val feed = s.getVideos(YouTubeQuery.empty / "Comedy" matching Text("carlin") & Text("religion"))
feed: s.videos.Feed =
Authors: (YouTube,Some(http://www.youtube.com/),None)
Id: http://gdata.youtube.com/feeds/api/videos
Title: (Some(text),YouTube Videos matching query: carlin religion)
Updated: 2008-03-25T16:11:45.601Z
Entries: Entry:
        Authors: (jvictorthegreat,Some(http://gdata.youtube.com/feeds/api/users/jvictorthegreat),None)
        Id: http://gdata.youtube.com/feeds/api/videos/CF1-...
```

Once we saved the video feed, we can choose an entry and navigate to its related videos. Let's assume we have at least one entry and we want to retrieve videos related to the first one:

```
scala> for (feed <- s.getRelatedVideos(feed.entries.head); 
               e <- feed) 
         println(e.title)
(Some(text),George Carlin- Guys named Todd)
(Some(text),O PAPA NO BRASIL)
(Some(text),Discurso do Capeta)
(Some(text),BRUTAL BOXING)
(Some(text),Apometria e regresso - Rosana Beni (4 de 10))
...
```

The for-comprehension above has to generators: the first one works on the optional feed (some videos might not have a related videos link), while the second iterates over all entries in the given feed.

In a similar way, the service object provides access to user profiles, comment feeds or video responses. Check the complete scaladoc documentation on [YouTubeService](http://gdata-scala-client.googlecode.com/svn/trunk/docs/api/com/google/gdata/youtube/YouTubeService.html) for more information.

# Raw queries #

To retrieve a feed from an arbitrary URL, use the `getVideos` method on the service object passing it the URL directly. The following call retrieves the top rated videos:

```
scala> for (e <- s.getVideos("http://gdata.youtube.com/feeds/api/standardfeeds/top_rated")) println(e.title)
(Some(text),Peanut - Video #3                 Part 3)
(Some(text),Joyful Joyful - Sister Act 2)
(Some(text),Head Tracking for Desktop VR Displays using the WiiRemote)
(Some(text),Imogen Heap - Just For Now (live at Studio 11 103.1FM))
(Some(text),Arby 'n' the Chief: Episode 3 - "Attitude")
(Some(text),The3tenors-Carreras-Domingo-Pavarotti--Nessun Dorma)
(Some(text),Jeff Dunham and Peanut part 2)
...
```

# Feed updates #

Most Google services allow an authenticated user to add, update or delete feed entries. For the next examples we'll use the Calendar API to manipulate event entries.

We start with a service object for Calendar. This time we need to authenticate:

```
scala> val s = new CalendarService("ACME-calor-1.0")
s: com.google.gdata.calendar.CalendarService = com.google.gdata.calendar.CalendarService@281902

scala> s.setUserCredentials("username@gmail.com", "secretthang")
```

If something went wrong during authentication, an exception is thrown. Assuming the user name and password are correct and Google accounts didn't issue a [Captcha challenge](http://code.google.com/apis/accounts/docs/AuthForInstalledApps.html#Using), we have a service object ready to handle requests.

Let's create a calendar entry for a football training session:

```
scala> import s.eventsFeed._
scala> val entry = new EventEntry("Football with Ronaldo", "Training session", 
     | DateTime(2008, 4, 9, 21, 0, 0, None, 0), DateTime(2008, 4, 9, 22, 30, 0, None, 0))
entry: s.eventsFeed.EventEntry = 
Entry:
	Authors: 
	Id: None
	Title: (None,Football with Ronaldo)
	Updated: 2008-04-09T12:05:05.456Z
```

The import statement is needed to get access to `EventEntries`, as they are defined in the `eventsFeed` of our service object. The event we just created has no `id` and `author`, as these fields will be filled in by the server. Next we add the event to the users default calendar:

```
scala> var e1 = s.addEvent(new java.net.URL(CalendarService.FEEDS 
        + "/" + "default" 
        + "/" + CalendarService.PRIVATE
        + "/" + CalendarService.FULL), entry)
e1: s.eventsFeed.Entry = 
Entry:
	Authors: (Iulian Test Dragos,None,Some(username@gmail.com))
	Id: Some(http://www.google.com/calendar/feeds/default/private/full/v5nn3vntdomnetj4kl1e6ag2q0)
	Title: (Some(text),Football with Ronaldo)
	Updated: 2008-04-09T12:06:14.000Z
```

The server replies with a complete entry, most notably with an `id` and `author` fields. Suppose we add a location to this event:

```
scala> e1.locations = List(new kinds.Where("Old Trafford", kinds.Schemas.EVENT))

scala> s.updateEvent(e1)
res4: s.eventsFeed.Entry = 
Entry:
	Authors: (Iulian Test Dragos,None,Some(username@gmail.com))
	Id: Some(http://www.google.com/calendar/feeds/default/private/full/lv7hm3q5f2f10n99ae1pr1i4uc)
	Title: (Some(text),Football with Ronaldo)
	Updated: 2008-04-09T12:46:14.000Z
```

You can check that the event has been updated by logging in to your Google Calendar account.

Deleting the event is equally simple:

```
scala>  s.delete(new URL(e1.editLink.get))
com.google.gdata.client.ConflictException: Specified version number doesn't match resource's latest version number.
	at com.google.gdata.client.Google DataRequest.handleErrorCode(Google DataRequest.scala:110)
	at com.google.gdata.client.Google DataRequest.connect(Google DataRequest.scala:95)
	at com.google.gdata.Service.delete(Service.scala:162)
	at .<init>(<console>:16)
	at .<clinit>(<console>)
	at RequestR...
```

Not so fast! What happened? We have taken the `edit` link from the entry and passed it to the `delete` method. The server complained that the server's version number is different from what we provided. Tracing back what we did, notice how we updated the event, and then tried to delete it _using the old entry id_. We should always use the most recent entry returned by the server (see [Optimistic Concurrency](http://code.google.com/apis/gdata/reference.html#Optimistic-concurrency)). Fortunately, the interpreter saves result values into automatically generated vals, so let's try it again, this time using the updated entry:

```
scala> s.delete(new URL(res4.editLink.get))

```

This time the operation succeeded. Check that it is gone using the web interface.

# How to use the library on not-yet-supported services #

Currently only YouTube and Calendar API are supported out-of-the box. However, the library can be used to access services for which the library lacks data binders. The Google Data protocol is based on [Atom](http://atomenabled.org/developers/syndication/atom-format-spec.php), and you can use the generic atom feed picklers for accessing new services. All extension elements can be accessed as XML elements in the parsed feeds and entries.

The following example shows how to access the [Contacts Data API](http://code.google.com/apis/contacts/) using the generic classes:

```
scala> val s = new Service("comp-test-1.0", "cp") {}
s: com.google.gdata.Service = $anon$1@1d281f1

scala> s.setUserCredentials("username@gmail.com", "secretthang")

scala> val atomFeed = new StdAtomFeed
atomFeed: com.google.gdata.data.StdAtomFeed = com.google.gdata.data.StdAtomFeed@e746a2
```

First we instantiate a `Service` object. This time we need to supply a second parameter which is the service name. This name is important for services that require authentication, and for Contacts API the name is `cp`. You can find a list of Google service names [here](http://code.google.com/support/bin/answer.py?answer=62712&topic=10433). Since all Contacts feeds are private, we need to specify a user name and password. Next step is to instantiate a standard Atom feed object. This object provides the types and picklers for atom feeds.

Next we make a request and print the results:
```
scala> val f = s.query("http://www.google.com/m8/feeds/contacts/username%40gmail.com/base", atomFeed.feedPickler)
scala> for (e <- f) println(e.title)
(Some(text),Gabi Werren)
(Some(text),Patricia Besson)
(Some(text),Diana Badila)
(Some(text),Iulian Dragos)
(Some(text),Martin Odersky)
(Some(text),Carla Neumann)
(Some(text),Florian Preknya)
...
```

We have successfully retrieved a feed of contact entries. To make sure the extra data is still present, we can print the unparsed XML elements in the first entry:

```
 println(f.entries.head.store)
LinearStore(, <gd:email primary="true" address="xxxx@xxxxx.com" 
rel="http://schemas.google.com/g/2005#other"
xmlns:gd="http://schemas.google.com/g/2005" 
xmlns:gContact="http://schemas.google.com/contact/2008" 
xmlns:openSearch="http://a9.com/-/spec/opensearchrss/1.0/" 
xmlns="http://www.w3.org/2005/Atom"></gd:email>,  
...
```

To learn more about how to write your own picklers and extend existing feeds and entries, check the DevelopersGuide.