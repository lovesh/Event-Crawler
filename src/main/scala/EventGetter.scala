package crawler
import crawler.event_sources.{Eventful}


class EventGetter(sourceName: String) {
  val source = sourceName match {
    case "eventful" => new Eventful()
    case _ => throw new Exception("Invalid source name")
  }

  var events = List[NormalizedEvent]()

  def getEvents() {
    events = source.getEvents()
  }
}
