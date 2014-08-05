import crawler.util._
import crawler.event_sources._
import java.net._
import org.json4s._
import org.json4s.jackson.JsonMethods._


implicit val formats = DefaultFormats

val url = new URL("http://api.eventful.com/json/categories/list?app_key=6Cg389qxws592QDW")

val resp = HTTPRequest.get(url)

val json = parse(resp.body)

val lst: List[(String, String)] = for {
  JObject(cat_list) <- json
  JField("id", JString(id))  <- cat_list
  JField("name", JString(name))  <- cat_list
} yield (id, name)

lst.toMap