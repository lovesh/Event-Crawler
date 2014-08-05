package crawler.event_sources

import java.net.URL
import crawler._


trait EventSource {
  def sourceNo: Int
  def typ: Int
  def baseUrl: URL
  protected def getRequestUrl(url: URL, args: Map[String, String]): URL
  def getRawEvents(args: Any*): List[Map[String, Any]]
  protected def normalizeEvent(eventData: Map[String, Any]): NormalizedEvent
  def getEvents(args: Any): List[NormalizedEvent]
}
