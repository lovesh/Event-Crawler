package crawler

import java.net.URL
import com.github.nscala_time.time.Imports._


case class Currency(name: String, code: String, symbol: String)

//case class Location(name: String, city: String, country: String, latitude: Double, longitude: Double)

case class NormalizedEvent(fields: Map[String, Any]) {
  val title = fields("title").asInstanceOf[String]
  val description = fields("description").asInstanceOf[String]
  val created_at = fields("created_at").asInstanceOf[DateTime]
  val last_updated_at = fields("last_updated_at").asInstanceOf[DateTime]
  val start_time = fields("start_time").asInstanceOf[DateTime]
  val end_time = fields("end_time").asInstanceOf[DateTime]
  val timezone = fields("timezone").asInstanceOf[String]
  val all_day = fields("all_day").asInstanceOf[Boolean]
  val source = fields("source").asInstanceOf[String]
  val id = fields("id").asInstanceOf[String]
  val source_url = fields("source_url").asInstanceOf[URL]
  val ticket_link = fields("ticket_link").asInstanceOf[URL]
  val image_url = fields("image_url").asInstanceOf[URL]

  val price: Option[(Double, Currency)] = {
    fields("price") match {
      case null => None
      case t:(Double, Currency) => Some(t)
    }
  }

  val display_price = fields("display_price").asInstanceOf[String]

  val location: Map[String, Any] = {
    var loc = Map[String, Any]()
    if (fields.contains("country"))
      loc += ("country"-> fields("country").asInstanceOf[String])
    if (fields.contains("city"))
      loc += ("city"-> fields("city").asInstanceOf[String])
    if (fields.contains("venue_name"))
      loc += ("name"-> fields("venue_name").asInstanceOf[String])
    else if (fields.contains("address"))
      loc += ("name"-> fields("address").asInstanceOf[String])
    if (fields.contains("latitude")) {
      loc += ("geo"-> Map(
        "type"-> "Point",
        "coordinates"-> Seq(fields("longitude").asInstanceOf[Double], fields("latitude").asInstanceOf[Double])
        ))
    }
    loc
  }
  val categories: List[String] = fields("categories").asInstanceOf[List[String]]

  def toMap: Map[String, Any] = {
    (Map[String, Any]() /: this.getClass.getDeclaredFields) {(a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(this))
    }
  }
}