package crawler

import java.net.URL
import com.github.nscala_time.time.Imports._


case class Currency(name: String, code: String, symbol: String)

case class Location(name: String, city: String, country: String, latitude: Double, longitude: Double)

case class NormalizedEvent(fields: Map[String, Any]) {
  val title: String = fields("title").asInstanceOf[String]
  val description: String = fields("description").asInstanceOf[String]
  val start_time: DateTime = fields("start_time").asInstanceOf[DateTime]
  val end_time: DateTime = fields("end_time").asInstanceOf[DateTime]
  val timezone: String = fields("timezone").asInstanceOf[String]
  val all_day: Boolean = fields("all_day").asInstanceOf[Boolean]
  val source: String = fields("source").asInstanceOf[String]
  val source_id: String = fields("source_id").asInstanceOf[String]
  val source_url: URL = fields("source_url").asInstanceOf[URL]
  val ticket_link: URL = fields("ticket_link").asInstanceOf[URL]
  val price: (Double, Currency) = {
    ???
  }
  val location: Location = {
    ???
  }
  val categories: List[String] = fields("categories").asInstanceOf[List[String]]
  val image_urls: Map[String, URL] = {
    ???
  }

  def toMap: Map[String, Any] = {
    (Map[String, Any]() /: this.getClass.getDeclaredFields) {(a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(this))
    }
  }
}