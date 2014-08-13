package crawler


object Constants {

  object Source {
    val API = 1
    val Website = 2
    val RSSFeed = 3
  }

  object SourceAuth {
    val eventfulAppKey = "6Cg389qxws592QDW"
    val meetupApiKey = "293d561751234385640366c12221e41"
    val eventbriteToken = "226ML7PF3QWT7X6WH2W4"
  }

  object eventDBConfig {
    val host = "localhost"
    val port = 27017
    val dbName = "other_events"
    val collName = "data"
  }

  object eventSourceDBConfig {
    val host = "localhost"
    val port = 27017
    val dbName = "event_sources"
    val collName = "data"
  }
}