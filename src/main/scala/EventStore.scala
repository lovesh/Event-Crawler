package crawler

import com.mongodb.casbah.Imports._
import com.github.nscala_time.time.Imports._
import com.mongodb.casbah.commons.conversions.scala._


object EventStore {
  RegisterJodaTimeConversionHelpers()
  val connection = MongoClient(Constants.eventDBConfig.host, Constants.eventDBConfig.port)
  val db = connection(Constants.eventDBConfig.dbName)
  val coll = db(Constants.eventDBConfig.collName)

  def hasEventChanged(source: String, id: String, last_updated_at: DateTime): Boolean = {
    val builder = MongoDBObject.newBuilder
    Map("source" -> source, "id"-> id, "last_updated_at"-> last_updated_at).foreach {case (k: String, v: Any) => builder += k-> v}
    coll.findOne(builder.result()) match {
      case Some(x) => false
      case None => true
    }
  }

  def saveEvent(event: NormalizedEvent) {
    val query = MongoDBObject("source_id" -> event.source_id)
    val builder = MongoDBObject.newBuilder
    event.toMap.foreach {case (k: String, v: Any) => builder += k-> v}
    coll.update(query, builder.result(), upsert = true)
  }
}


object EventSourceStore {
  val connection = MongoClient(Constants.eventSourceDBConfig.host, Constants.eventSourceDBConfig.port)
  val db = connection(Constants.eventSourceDBConfig.dbName)
  val coll = db(Constants.eventSourceDBConfig.collName)

  def saveMeetupCityNames(countryCode: String, cityNames: List[String]) {
    val docId = "meetup_" + countryCode + "_cities"
    val query = MongoDBObject("id" -> docId)
    val builder = MongoDBObject.newBuilder
    builder += "id" -> docId
    builder += "cities" -> cityNames
    coll.update(query, builder.result(), upsert=true)
  }

  def getMeetupCityNames(countryCode: String): List[String] = {
    val query = MongoDBObject("id" -> ("meetup_" + countryCode + "_cities"))
    coll.findOne(query) match {
      case Some(doc) => doc.as[List[String]]("cities")
      case None => List()
    }
  }
}