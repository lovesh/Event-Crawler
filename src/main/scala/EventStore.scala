package crawler

import com.mongodb.casbah.Imports._
import com.github.nscala_time.time.Imports._
import com.mongodb.casbah.commons.conversions.scala._


object EventStore {
  RegisterJodaTimeConversionHelpers()
  val connection = MongoClient(Constants.DBConfig.host, Constants.DBConfig.port)
  val db = connection(Constants.DBConfig.dbName)
  val coll = db(Constants.DBConfig.collName)

  def hasEventChanged(source: String, id: String, last_updated_at: DateTime): Boolean = {
    val builder = MongoDBObject.newBuilder
    Map("source" -> source, "id"-> id, "last_updated_at"-> last_updated_at).foreach { case (k: String, v: Any) => builder += k-> v}
    coll.findOne(builder.result()) match {
      case Some(x) => false
      case None => true
    }
  }

  def saveEvent(event: NormalizedEvent) {

  }
}
