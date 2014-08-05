package crawler.event_sources

import java.net.URL
import crawler._
import org.json4s._
import org.json4s.jackson.JsonMethods._


//case class Category(id: String, name: String)



class Eventful extends EventSource {
  val sourceNo = 6
  val typ = Constants.Source.API
  val baseUrl = new URL("http://api.eventful.com/json")
  val categoryUrl = new URL("http://api.eventful.com/json/categories/list")
  val eventSearchUrl = new URL("http://api.eventful.com/json/events/search?page_size=100&t=Next+30+days&location=India")

  val appKey = Constants.Auth.eventfulAppKey

  implicit val formats = DefaultFormats

  def getRequestUrl(url: URL, args: Map[String, String] = Map()): URL = {
    val queryString = (args + ("app_key"-> appKey)).map({ case(k,v) => s"$k=$v"}).mkString("&")
    val pattern = ".+?.+".r
    val separator = url.toString match {
      case pattern(q) => "&"
      case _ => "?"
    }
    new URL(url.toString + separator + queryString)
  }

  def getCategories(): Map[String, String] = {
    val url = getRequestUrl(categoryUrl)
    val resp = HTTPRequest.get(url)
    val json = parse(resp.body)
    val lst: List[(String, String)] = for {
      JObject(cat_list) <- json
      JField("id", JString(id))  <- cat_list
      JField("name", JString(name))  <- cat_list
    } yield (id, name)
    lst.toMap
  }

  def getEventsOfCategory(catId: String): List[Map[String, Any]] = {

  }

  def getRawEvents(args: Any*): List[Map[String, Any]] = {
    val categories = getCategories()
    var events = List[Map[String, Any]]()
    categories.foreach { case (id, name) => events = events ++ getEventsOfCategory(id)}
    events
  }

  protected def normalizeEvent(eventData: Map[String, Any]): NormalizedEvent = {
    NormalizedEvent(eventData)
  }

  def getEvents(args: Any): List[NormalizedEvent] = {
    getRawEvents().map { e: Map[String, Any] => normalizeEvent(e) }
  }
}


