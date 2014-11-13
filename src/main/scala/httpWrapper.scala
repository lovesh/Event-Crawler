package crawler


import scalaj.http._
import java.net.URL
import org.json4s._
import org.json4s.jackson.JsonMethods._

case class HTTPResponse(code: Int, body: String, url: URL)


object HTTPRequest {
	val options = Map(
		"connTimeout" -> 20000,
		"readTimeout" -> 20000
	)
	
	private def makeOptionList(options: Map[String, Any]): List[HttpOptions.HttpOption] = {
		val requestOptions = this.options ++ options
		List(
			HttpOptions.connTimeout(requestOptions("connTimeout").asInstanceOf[Int]),
			HttpOptions.readTimeout(requestOptions("readTimeout").asInstanceOf[Int])
			)
	}
	
	private def makeResponse(request: Http.Request): HTTPResponse = {
		if (request.responseCode > 199 && request.responseCode < 400)
			HTTPResponse(request.responseCode, request.asString, request.getUrl)
		else
			HTTPResponse(request.responseCode, "", request.getUrl)
	}
	
	def get(url: URL, params: Map[String, Any] = Map(), options: Map[String, Any] = Map(), headers: Map[String, String] = Map()): HTTPResponse = {
		val optionList = makeOptionList(options)
    val requestParams = params map { case (k,v) => (k, v.toString)}
		val request: Http.Request = Http(url.toString).options(optionList).params(requestParams).headers(headers)
		makeResponse(request)
	}
	
	def post(url: URL, params: Map[String, Any] = Map(), options: Map[String, Any] = Map(), headers: Map[String, String] = Map()): HTTPResponse = {
		val optionList = makeOptionList(options)
    val requestParams = params map { case (k,v) => (k, v.toString)}
		val request: Http.Request = Http.post(url.toString).options(optionList).params(requestParams).headers(headers)
		makeResponse(request)
	}

  def getJSON(url: URL, params: Map[String, Any] = Map(), options: Map[String, Any] = Map(), headers: Map[String, String] = Map()): JValue = {
    implicit val formats = DefaultFormats
    val response = get(url, params, options, headers)
    parse(response.body)
  }
	
}
