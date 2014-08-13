package crawler.event_sources

import crawler._
import java.net._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.github.nscala_time.time.Imports._


private case class MeetupFee(
                amount: Double,
                currency: String,
                required: String,
                description: String
                )


private case class MeetupVenue(
                  lat: Option[Double],
                  lon: Option[Double],
                  name: String,
                  country: String,
                  city: String
                  )


private case class MeetupEvent(
                  id: String,
                  name: String,
                  description: String,
                  timezone: Option[String],
                  time: Int,
                  duration: Option[Int],
                  created: Int,
                  updated: Int,
                  event_url: String,
                  fee: Option[MeetupFee],
                  venue: Option[MeetupVenue]
                  )

class Meetup extends EventSource {
  val sourceNo = 4
  val `type` = Constants.Source.API
  val baseUrl = new URL("https://api.meetup.com/2")
  val categoryUrl = new URL("https://api.meetup.com/2/categories?sign=true&photo-host=public&page=100")
  val cityUrl = new URL("https://api.meetup.com/2/cities?sign=true&photo-host=public&page=500")
  val eventSearchUrl = new URL("https://api.meetup.com/2/open_events?sign=true&page=100&time=,1m&fields=timezone,photo_count,photo_album_id")
  val apiKey = Constants.SourceAuth.meetupApiKey

  implicit val formats = DefaultFormats

  protected def getRequestUrl(url: URL, args: Map[String, String] = Map()): URL = {
    val queryString = (args + ("key"-> apiKey)).map({ case(k,v) => s"$k=$v"}).mkString("&")
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
    val results = json \ "results"
    val categories: List[(String, String)] = for {
      JObject(catList) <- results
      JField("id", JInt(id))  <- catList
      JField("name", JString(name))  <- catList
    } yield (id.toString, name)
    categories.toMap
  }

  def getCitiesOfCountry(countryCode: String): List[String] = {
    val url = getRequestUrl(cityUrl, Map("country"-> countryCode))
    val json = HTTPRequest.getJSON(url)
    var next = (json \ "meta" \ "next").values.asInstanceOf[String]
    var cities: List[String] = for {
      JObject(cityList) <- (json \ "results")
      JField("city", JString(city))  <- cityList
    } yield city
    var i = 1
    while (next != "") {
      val url = getRequestUrl(cityUrl, Map("country"-> countryCode, "offset"-> i.toString))
      val json = HTTPRequest.getJSON(url)
      next = (json \ "meta" \ "next").values.asInstanceOf[String]
      cities ++= (for {
        JObject(cityList) <- (json \ "results")
        JField("city", JString(city))  <- cityList
      } yield city)
      i += 1
    }
    cities
  }

  def getEventsOfCategory(catId: String, catName: String, filters: Map[String, String] = Map()): List[Map[String, Any]] = {
    val url = getRequestUrl(eventSearchUrl, Map("category"-> catId) ++ filters)
    val json = HTTPRequest.getJSON(url)
    val results = json \ "results"
    var events = results.children.map {case j: JObject => j.extract[MeetupEvent]}
    var next = (json \ "meta" \ "next").values.asInstanceOf[String]
    var i = 1
    while (next != "") {
      val url = getRequestUrl(eventSearchUrl, Map("category"-> catId) ++ filters ++ Map("offset"-> i.toString))
      val json = HTTPRequest.getJSON(url)
      val results = json \ "results"
      events ++= results.children.map {case j: JObject => j.extract[MeetupEvent]}
      next = (json \ "meta" \ "next").values.asInstanceOf[String]
      i += 1
    }

    events = events filter {
      ev: MeetupEvent => EventStore.hasEventChanged("meetup", ev.id, new DateTime(ev.updated))
    }
    events map {event =>
      (Map[String, Any]("categories"-> List(catName)) /: event.getClass.getDeclaredFields) {(a, f) =>
        f.setAccessible(true)
        a + (f.getName -> (f.get(event) match {
          case Some(x) => x
          case None => null
          case t: Any => t
        }))
      }
    }
  }

  def storeCitiesOfCountry(countryCode: String) = {
    val cities = getCitiesOfCountry(countryCode)
    EventSourceStore.saveMeetupCityNames(countryCode, cities)
  }

  def getRawEvents(args: Any*): List[Map[String, Any]] = {
    val categories = getAllCategories()
    var events = List[Map[String, Any]]()
    var cities = EventSourceStore.getMeetupCityNames("in")
    if (cities.length == 0) {
      storeCitiesOfCountry("in")
      cities = EventSourceStore.getMeetupCityNames("in")
    }
    cities foreach {
      city => categories.foreach { case (id, name) => events = events ++ getEventsOfCategory(id, name, Map("country"-> "in", "city"-> city, "fields"-> "photo_url,photo_count,timezone,duration,fee"))}
    }
    events
  }

  protected def normalizeEvent(eventData: Map[String, Any]) = {
    //TODO: Add image url field from photo album API

    var fields: Map[String, Any] = Map(
      "title"-> eventData("name"),
      "description"-> eventData("description"),
      "source"-> "meetup",
      "id"-> eventData("id"),
      "source_id"-> (sourceNo.toString + ":" + eventData("id")),
      "source_url"-> new URL(eventData("event_url").toString),
      "timezone"-> (eventData("timezone") match {case null => "UTC"; case t => t}),
      "categories"-> eventData("categories"),
      "ticket_link"-> null,
      "all_day"-> false
    )

    val duration = eventData("duration") match {case null => 3*60*60*1000; case t => t.asInstanceOf[Int]}

    fields += (
      "start_time"-> new DateTime(eventData("time").asInstanceOf[Int]),
      "end_time"-> new DateTime(eventData("time").asInstanceOf[Int] + duration),
      "created_at"-> new DateTime(eventData("created").asInstanceOf[Int] + duration),
      "last_updated_at"-> new DateTime(eventData("updated").asInstanceOf[Int] + duration)
      )

    eventData("venue") match {
      case null =>
      case mv: MeetupVenue =>
        fields += (
          "location_name"-> mv.name,
          "city"-> mv.city,
          "country"-> mv.country.toUpperCase
          )
        mv.lat match {
          case None =>
          case Some(la: Double) =>
            fields += ("latitude"-> la)
        }
        mv.lon match {
          case None =>
          case Some(lo: Double) =>
            fields += ("longitude"-> lo)
        }
    }

    eventData("fee") match {
      case null =>
        fields += (
          "display_price"-> "free",
          "price"-> null
          )
      case mf: MeetupFee =>
        fields += (
          "display_price"-> (mf.amount.toString + " " + mf.currency),
          "price"-> (mf.amount, mf.currency)
          )
    }

    NormalizedEvent(fields)
  }
}
