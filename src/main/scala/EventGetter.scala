package crawler
import crawler.event_sources.{Eventful, Meetup, Eventbrite}


class EventGetter(sourceName: String) {
  val source = sourceName match {
    case "eventful" => new Eventful()
    case "meetup" => new Meetup()
    case "eventbrite" => new Eventbrite()
    case _ => throw new Exception("Invalid source name")
  }

  var events = List[NormalizedEvent]()

  def getEvents() {
    events = source.getEvents()
  }

  def storeEvents() = {
    events.foreach(EventStore.saveEvent)
  }
}
