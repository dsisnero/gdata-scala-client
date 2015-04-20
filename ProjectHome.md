The Google Data Scala client library allows client software to connect to Google Services through the [Google Data API protocol](http://code.google.com/apis/gdata/index.html). It makes it easy to retrieve and modify data exposed by services like [Calendar](http://code.google.com/apis/calendar/) or [YouTube](http://code.google.com/apis/youtube/overview.html), without worrying about the underlying XML representation or how queries are made.

Currently it supports the following services:

  * [Calendar API](http://code.google.com/apis/calendar/)
  * [YouTube API](http://code.google.com/apis/youtube/overview.html) (read-only)
  * [Contacts Data API](http://code.google.com/apis/contacts/)
Here's a sample of what you can do:

```
  val s = new YouTubeService("test")
  for (v <- s.getVideos(Query.empty / "Comedy" matching Text("carlin")) {
    for (relatedFeed <- s.getRelatedVideos(v);
         relatedVideo <- relatedFeed)
      println(relatedVideo.title)
```

or

```
for (e <- s.getEvents("private", "full", q matching Text("Tennis"))) {
  e.locations = List(Where[s.contacts.Entry]("Vidy, Lausanne", Schemas.EVENT))
  s.updateEvent(e)
}
```

For more information, see the [User's guide](UsersGuide.md).