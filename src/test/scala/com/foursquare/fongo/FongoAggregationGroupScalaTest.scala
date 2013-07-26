package com.foursquare.fongo

import _root_.com.foursquare.fongo.impl.Util
import _root_.com.mongodb._
import _root_.com.mongodb.util.JSON
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.collection.JavaConversions._
import org.scalatest.ParallelTestExecution

//import scala.collection.JavaConverters._

// TODO : sum of double value ($sum : 1.3)
// sum of "1" (String) must return 0.

// Handle $group { _id = 0}
@RunWith(classOf[JUnitRunner])
class FongoAggregationGroupScalaTest extends FongoAbstractTest with ParallelTestExecution {
  // If you want to test against real world (a real mongodb client).
  val realWorld = !true

  lazy val zips = {
    import scala.io.Source
    val source = Source.fromInputStream(this.getClass.getResourceAsStream("/zips.json"))
    source.getLines().map(JSON.parse(_).asInstanceOf[DBObject])
  }

  override def init() = {
    zips.foreach(collection.insert(_))
  }

  // see http://stackoverflow.com/questions/11418985/mongodb-aggregation-framework-group-over-multiple-values
  test("Fongo should handle 'States with Populations Over 5 Million'") {
    val pipeline = JSON.parse(
      """
        |[{ $group :
        |                         { _id : "$state",
        |                           totalPop : { $sum : "$pop" } } },
        |{ $match : {totalPop : { $gte : 5000000 } } },
        |{ $sort : {_id:1}}
        |]
      """.stripMargin).asInstanceOf[java.util.List[DBObject]]

    val output = collection.aggregate(pipeline(0), pipeline(1), pipeline(2))
    assert(output.getCommandResult.ok)
    assert(output.getCommandResult.containsField("result"))

    val resultAggregate = (output.getCommandResult.get("result").asInstanceOf[java.util.List[DBObject]])
    assert(resultAggregate.size === 3)
    assert(resultAggregate.get(0).get("_id") === "MA")
    assert(resultAggregate.get(0).get("totalPop") === 6016425)
    assert(resultAggregate.get(1).get("_id") === "NJ")
    assert(resultAggregate.get(1).get("totalPop") === 7730188)
    assert(resultAggregate.get(2).get("_id") === "NY")
    assert(resultAggregate.get(2).get("totalPop") === 12950936)
  }


  test("Fongo should handle 'Largest and Smallest Cities by State'") {
    val pipeline = JSON.parse(
      """
        |[   { $group:
        |      { _id: { state: "$state", city: "$city" },
        |        pop: { $sum: "$pop" } } },
        |    { $sort: { pop: 1 } },
        |    { $group:
        |      { _id : "$_id.state",
        |        biggestCity:  { $last: "$_id.city" },
        |        biggestPop:   { $last: "$pop" },
        |        smallestCity: { $first: "$_id.city" },
        |        smallestPop:  { $first: "$pop" } } },
        |    { $project:
        |      { _id: 0,
        |        state: "$_id",
        |        biggestCity:  { name: "$biggestCity",  pop: "$biggestPop" },
        |        smallestCity: { name: "$smallestCity", pop: "$smallestPop" } } },
        |    { $sort : { "biggestCity.name" : 1 } }
        |
        |]      """.stripMargin).asInstanceOf[java.util.List[DBObject]]
    val output = collection.aggregate(pipeline(0), pipeline(1), pipeline(2), pipeline(3), pipeline(4))

    assert(output.getCommandResult.ok)
    assert(output.getCommandResult.containsField("result"))

    val resultAggregate = (output.getCommandResult.get("result").asInstanceOf[java.util.List[DBObject]])
    assert(resultAggregate.size === 8)
    assert("BRIDGEPORT" === Util.extractField(resultAggregate(0), "biggestCity.name"))
    assert(141638 === Util.extractField(resultAggregate(0), "biggestCity.pop"))
    assert("EAST KILLINGLY" === Util.extractField(resultAggregate(0), "smallestCity.name"))
    assert(25 === Util.extractField(resultAggregate(0), "smallestCity.pop"))
    assert("WORCESTER" === Util.extractField(resultAggregate.last, "biggestCity.name"))
    assert(169856 === Util.extractField(resultAggregate.last, "biggestCity.pop"))
    assert("BUCKLAND" === Util.extractField(resultAggregate.last, "smallestCity.name"))
    assert(16 === Util.extractField(resultAggregate.last, "smallestCity.pop"))
  }

}