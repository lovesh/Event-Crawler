package crawler.event_sources

import java.net.URL

import crawler._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.github.nscala_time.time.Imports._


case class EventbriteVenue (
        address: Option[Map[String, String]],
        name: String,
        longitude: Option[String],
        latitude: Option[String]
        )

case class EventbriteTicket (
      name: String,
      description: String,
      free: Boolean,
      cost: Option[Map[String, Any]]
      )

case class EventbriteEvent (
      id: String,
      name: Map[String, String],
      description: Map[String, String],
      url: String,
      logo_url: Option[String],
      start: Map[String, String],
      end: Option[Map[String, String]],
      created: String,
      changed: String,
      category: Option[Map[String, String]],
      venue: Option[EventbriteVenue],
      ticket_classes: List[EventbriteTicket]
      )


class Eventbrite extends EventSource {
  val sourceNo = 5
  val `type` = Constants.Source.API
  val baseUrl = new URL("https://www.eventbriteapi.com/v3")
  val categoryUrl = new URL("https://www.eventbriteapi.com/v3/categories")
  val eventSearchUrl = new URL("https://www.eventbriteapi.com/v3/events/search")
  val authToken = Constants.SourceAuth.eventbriteToken

  implicit val formats = DefaultFormats

  protected def getRequestUrl(url: URL, args: Map[String, String] = Map()): URL = {
    val queryString = (args + ("token"-> authToken)).map({ case(k,v) => s"$k=$v"}).mkString("&")
    val pattern = raw"\?.+".r
    val separator = pattern findFirstIn url.toString match {
      case Some(x) => "&"
      case None => "?"
    }
    new URL(url.toString + separator + queryString)
  }

  def getAllCategories(): List[String] = {
    val url = getRequestUrl(categoryUrl)
    // have to send `application/json` as headers otherwise the api sends an html response
    val resp = HTTPRequest.get(url, headers = Map("Accept"-> "application/json"))
    println(url)
    //println(resp.body)
    val json = parse(resp.body)
    val results = json \ "categories"
    for {
      JObject(catList) <- results
      JField("id", JString(id))  <- catList
    } yield id
  }

  def getEventsFromSearchPage(json: JValue): List[EventbriteEvent] = {
    val results = json \ "events"
    results.children.map {
      case j: JObject => j.extract[EventbriteEvent]
    }
  }

  def getPaginationData(json: JValue): (Int, Int) = {
    val pagination = json \ "pagination"
    val page_number = (pagination \ "page_number").values.asInstanceOf[Int]
    val page_count = (pagination \ "page_count").values.asInstanceOf[Int]
    (page_number, page_count)
  }

  def getEventsOfCategory(catId: String, filters: Map[String, String] = Map()): List[Map[String, Any]] = {
    val url = getRequestUrl(eventSearchUrl, Map("categories"-> catId) ++ filters)
    // have to send `application/json` as headers otherwise the api sends an html response
    val headers = Map("Accept"-> "application/json")
    val json = HTTPRequest.getJSON(url, headers = headers)
    val results = json \ "events"
    var events = getEventsFromSearchPage(json)
    var (page_number, page_count) = getPaginationData(json)

    while (page_number < page_count) {
      page_number += 1
      val url = getRequestUrl(eventSearchUrl, Map("categories"-> catId, "page"-> page_number.toString) ++ filters)
      val json = HTTPRequest.getJSON(url, headers=headers)
      events ++= getEventsFromSearchPage(json)
      page_count = getPaginationData(json)._2
    }

    println("Got %d events for cat id %s".format(events.size, catId))

    events map {event =>
      (Map[String, Any]() /: event.getClass.getDeclaredFields) {(a, f) =>
        f.setAccessible(true)
        a + (f.getName -> (f.get(event) match {
          case Some(x) => x
          case None => null
          case t: Any => t
        }))
      }
    }
  }

  def getRawEvents(args: Any*): List[Map[String, Any]] = {
    val categories = getAllCategories()
    println("Got %d categories".format(categories.size))
    var events = List[Map[String, Any]]()
    val filters = Map(
      "venue.country"-> "IN",
      "start_date.range_start"-> new DateTime(DateTimeZone.UTC).toString(DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z"))
    )
    categories map { id: String => events = events ++ getEventsOfCategory(id, filters) }
    events
  }

  protected def normalizeEvent(eventData: Map[String, Any]): NormalizedEvent = {
    var fields: Map[String, Any] = Map(
      "title"-> eventData("name").asInstanceOf[Map[String, String]]("text"),
      "description"-> eventData("description").asInstanceOf[Map[String, String]]("text"),
      "image_url"-> (eventData("logo_url") match {case null => null; case s: String => new URL(s)}),
      "source"-> "eventbrite",
      "id"-> eventData("id"),
      "source_id"-> (sourceNo.toString + ":" + eventData("id").toString),
      "source_url"-> new URL(eventData("url").toString),
      "all_day"-> false,
      "timezone"-> "UTC"
    )

    fields += (
      "start_time"-> new DateTime(eventData("start").asInstanceOf[Map[String, String]]("utc"), DateTimeZone.UTC),
      "end_time"-> new DateTime(eventData("end").asInstanceOf[Map[String, String]]("utc"), DateTimeZone.UTC),
      "created_at"-> new DateTime(eventData("created").asInstanceOf[String], DateTimeZone.UTC),
      "last_updated_at"-> new DateTime(eventData("changed").asInstanceOf[String], DateTimeZone.UTC)
      )

    fields += (
      "categories"-> (eventData("category") match {case null => List(); case cat: Map[String, String] => List(cat("name"))})
      )

    eventData("venue") match {
      case null =>
      case ev: EventbriteVenue =>
        fields += ("location_name"-> ev.name)

        ev.address match {
          case None =>
          case Some(addrs: Map[String, String]) =>
            if (addrs.contains("country"))
              fields += ("country"-> addrs("country"))
            if (addrs.contains("city"))
              fields += ("city"-> addrs("city"))
        }

        ev.latitude match {
          case None =>
          case Some(lat: String) =>
            fields += ("latitude"-> (try { Some(lat.toString.toDouble);lat.toString.toDouble; } catch { case _ => null }))
        }
        ev.longitude match {
          case None =>
          case Some(lon: String) =>
            fields += ("longitude"-> (try { Some(lon.toString.toDouble);lon.toString.toDouble; } catch { case _ => null }))
        }
    }

    eventData("ticket_classes") match {
      case null =>
        fields += (
          "display_price"-> "free",
          "price"-> null
          )
      case tcs: List[EventbriteTicket] if tcs.length > 0 =>
        val tc = tcs(0)
        if (tc.free)
          fields += (
            "display_price"-> "free",
            "price"-> null
            )
        else
          tc.cost match {
            case None =>
              fields += (
                "display_price"-> "free",
                "price"-> null
                )
            case Some(c: Map[String, Any]) =>
              fields += (
                "display_price"-> (c("value").toString + " " + c("currency").toString),
                "price"-> (c("value").asInstanceOf[Double], c("currency").asInstanceOf[String])
                )
          }
    }

    NormalizedEvent(fields)
  }
}
