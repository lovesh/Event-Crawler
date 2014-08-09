package crawler


object Constants {

  object Source {
    val API = 1
    val Website = 2
    val RSSFeed = 3
  }

  object SourceAuth {
    val eventfulAppKey = "6Cg389qxws592QDW"
  }

  object DBConfig {
    val host = "localhost"
    val port = 27017
    val dbName = "other_events"
    val collName = "data"
  }
}