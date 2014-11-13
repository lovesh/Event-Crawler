package crawler.event_sources


import java.net.URL
import crawler._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.github.nscala_time.time.Imports._
import java.util.Locale


private case class EventfulLink(url: String, `type`: String)

private case class EventfulCategory(id: String, name: String)

private case class EventfulEvent(
          id: String,
          title: String,
          description: Option[String],
          olson_path: Option[String],
          start_time: String,
          stop_time: Option[String],
          created: String,
          modified: String,
          address: Option[String],
          venue_name: Option[String],
          latitude: Option[String],
          longitude: Option[String],
          city: Option[String],
          country: Option[String],
          url: String,
          all_day: Option[String],
          price: Option[String]
          )

class Eventful extends EventSource {
  val sourceNo = 6
  val `type` = Constants.Source.API
  val baseUrl = new URL("http://api.eventful.com/json")
  val categoryUrl = new URL("http://api.eventful.com/json/categories/list")
  val eventSearchUrl = new URL("http://api.eventful.com/json/events/search?page_size=100&t=Next+30+days&location=India")
  val eventDetailUrl = new URL("http://api.eventful.com/json/events/get")
  val appKey = Constants.SourceAuth.eventfulAppKey

  implicit val formats = DefaultFormats

  protected def getRequestUrl(url: URL, args: Map[String, String] = Map()): URL = {
    val queryString = (args + ("app_key"-> appKey)).map({ case(k,v) => s"$k=$v"}).mkString("&")
    val pattern = raw"\?.+".r
    val separator = pattern findFirstIn url.toString match {
      case Some(x) => "&"
      case None => "?"
    }
    new URL(url.toString + separator + queryString)
  }

  def getAllCategories(): Map[String, String] = {
    val url = getRequestUrl(categoryUrl)
    val resp = HTTPRequest.get(url)
    val json = parse(resp.body)
    val categories: List[(String, String)] = for {
      JObject(catList) <- json
      JField("id", JString(id))  <- catList
      JField("name", JString(name))  <- catList
    } yield (id, name)
    categories.toMap
  }

  def getEventsSummaryFromSearchPage(json: JValue): List[Map[String, Any]] = {
    val lst: List[(String, String, String)] = for {
      JObject(eventList) <- json
      JField("id", JString(id))  <- eventList
      JField("olson_path", JString(timezone))  <- eventList
      JField("modified", JString(updated))  <- eventList
    } yield (id, timezone, updated)
    lst.map {
      case (i, t, u) =>
        val dateParser = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZone(DateTimeZone.forID(t))
        Map("id"-> i, "updated"-> dateParser.parseDateTime(u))
    }
  }

  def getEventDetailFromEventPage(json: JValue): Map[String, Any] = {
    val event = json.extract[EventfulEvent]
    val link = (json \\ "link").values
    val category = (json \\ "category").values
    val image = (json \\ "image").values

    val ticketLink = link match {
      case l: Map[String, String] if l.contains("type") && (l("type") == "Tickets") => l("url")
      case l: List[Map[String, String]] =>
        l.filter({
          case m: Map[String, String]=> m.contains("type") && m("type") == "Tickets"
        }) match {
          case List() => null
          case head :: tail => head("url")
        }
      case _ => null
    }

    val categories = category match {
      case c: Map[String, String] => List[String](c("name"))
      case c: List[Map[String, String]] =>
        c.map(_("name"))
      case _ => null
    }

    val imageUrl = image match {
      case img: Map[String, Any] if img.contains("medium") => img("medium").asInstanceOf[Map[String, String]]("url")
      case img: Map[String, Any] if img.contains("small") => img("small").asInstanceOf[Map[String, String]]("url")
      case img: Map[String, Any] if img.contains("thumb") => img("thumb").asInstanceOf[Map[String, String]]("url")
      case _ => null
    }

    val eventFields = (Map[String, Any]() /: event.getClass.getDeclaredFields) {(a, f) =>
      f.setAccessible(true)
      a + (f.getName -> (f.get(event) match {
        case Some(x) => x
        case None => ""
        case t: Any => t
      }))
    }

    eventFields ++ Map("ticket_link"-> ticketLink, "categories"-> categories, "image_url"-> imageUrl)
  }

  def getEventsOfCategory(catId: String): List[Map[String, Any]] = {
    val url = getRequestUrl(eventSearchUrl, Map("c"-> catId))
    val resp = HTTPRequest.get(url).body
    val json = parse(resp)
    var eventsSummary = getEventsSummaryFromSearchPage(json)
    val pageCount = (json \\ "page_count").values.toString.toInt
    if (pageCount > 1) {
      var i = 2
      while (i<=pageCount) {
        val url = getRequestUrl(eventSearchUrl, Map("c"-> catId, "page_number"-> i.toString))
        val json = parse(HTTPRequest.get(url).body)
        eventsSummary = eventsSummary ++ getEventsSummaryFromSearchPage(json)
        i+= 1
      }
    }
    var events = List[Map[String, Any]]()
    eventsSummary.foreach {
      case m:Map[String, Any] if EventStore.hasEventChanged("eventful", m("id").toString, m("updated").asInstanceOf[DateTime]) =>
        val url = getRequestUrl(eventDetailUrl, Map("id"-> m("id").toString))
        val json = parse(HTTPRequest.get(url).body)
        events = events :+ getEventDetailFromEventPage(json)
    }
    println("Got %d events for cat id %s".format(events.size, catId))
    events
  }

  def getRawEvents(args: Any*): List[Map[String, Any]] = {
    val categories = getAllCategories()
    println("Got %d categories".format(categories.size))
    var events = List[Map[String, Any]]()
    categories.foreach { case (id, name) => events = events ++ getEventsOfCategory(id)}
    events
  }

  private val countryCodes: Map[String, String] = {
    (Locale.getISOCountries() map {
      case code: String =>
        val l = new Locale("", code)
        (l.getDisplayCountry(), code)
    }).toMap
  }

  protected def normalizeEvent(eventData: Map[String, Any]): NormalizedEvent = {
    var fields: Map[String, Any] = Map(
                                    "title"-> eventData("title"),
                                    "description"-> eventData("description"),
                                    "source"-> "eventful",
                                    "id"-> eventData("id"),
                                    "source_id"-> (sourceNo.toString + ":" + eventData("id").toString),
                                    "source_url"-> new URL(eventData("url").toString),
                                    "timezone"-> (eventData("timezone") match {case "" => "UTC"; case t => t}),
                                    "categories"-> eventData("categories"),
                                    "display_price"-> (eventData("price") match {case "" => "free"; case t => t})
                                    )

    val dateParser = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZone(DateTimeZone.forID(fields("timezone").toString))
    fields += ("start_time"-> dateParser.parseDateTime(eventData("start_time").toString))
    fields += ("end_time"-> (eventData("end_time") match {case "" => fields("start_time").asInstanceOf[DateTime].plusDays(1).withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0); case s: String => dateParser.parseDateTime(s)}))
    fields += ("created_at"-> dateParser.parseDateTime(eventData("created_at").toString))
    fields += ("last_updated_at"-> dateParser.parseDateTime(eventData("last_updated_at").toString))
    fields += ("all_day"-> (eventData("all_day") match {case "true" => true; case o => false}))
    fields += ("image_url"-> (eventData("image_url") match {case null => null; case s: String => new URL(s)}))
    fields += ("ticket_link"-> (eventData("ticket_link") match {case null => null; case s: String => new URL(s)}))
    fields += (
      "country"-> (eventData("country") match {case "" => null; case c: String if countryCodes.contains(c) => countryCodes(c); case c: Any => c}),
      "city"-> (eventData("city") match {case "" => null; case c: String => c}),
      "location_name"-> (eventData("venue_name") match {case "" => null; case l: String => l}),
      "latitude"-> (try { Some(eventData("latitude").toString.toDouble);eventData("latitude").toString.toDouble; } catch { case _ => null }),
      "longitude"-> (try { Some(eventData("longitude").toString.toDouble);eventData("longitude").toString.toDouble; } catch { case _ => null })
      )
    NormalizedEvent(fields)
  }
}


