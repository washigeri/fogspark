import java.net.URI
import java.nio.charset.StandardCharsets

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import org.apache.commons.io.IOUtils
import org.apache.http.HttpHeaders
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.spark.rdd.RDD
import org.apache.spark.util.SizeEstimator
import org.apache.spark.{SparkConf, SparkContext}
import spray.json.DefaultJsonProtocol

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn


//noinspection SpellCheckingInspection
object WebServer {

  val Conf: SparkConf = new SparkConf().setAppName("FogSpark").setMaster("local[*]")
  val CloudURL: String = "http://localhost:8080/"
  val bufferSize: Int = 100
  val sendIntervalMS: Int = 60000
  var t0, t1: Long = 0
  var firstData: Boolean = true
  var datareceived: Long = 0
  var datasent: Long = 0
  var stopped: Boolean = false


  def main(args: Array[String]): Unit = {

    //start spark
    val sc = new SparkContext(Conf)
    sc.setLogLevel("ERROR")


    var dataIoTs: ListBuffer[DataIoT] = new ListBuffer()
    implicit val system: ActorSystem = ActorSystem("fog-spark-http")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    var rddDataIot = sc.parallelize(dataIoTs)


    def saveData(dataIoT: DataIoT): Future[Done] = {
      if (firstData) {
        firstData = false
        t0 = System.nanoTime()
      }
      datareceived += SizeEstimator.estimate(MyJsonProtocol.DataIoTJsonFormat.write(dataIoT))
      if (!dataIoTs.exists(d => d.equals(dataIoT))) {
        dataIoTs.+=(dataIoT)
        emptyBuffer()
      }
      Future {
        Done
      }
    }

    def emptyBuffer(): Unit = {
      if (dataIoTs.length >= bufferSize) {
        val temp = sc.parallelize(dataIoTs)
        rddDataIot = rddDataIot.union(temp).distinct()
        dataIoTs.clear()
      }
    }

    def sendDataToCloud(dataRdd: RDD[DataIoT]): Unit = {
      if (!dataRdd.isEmpty()) {
        println(Thread.currentThread().getName + " Sending data to cloud, size :" + dataRdd.count())
        val jsonresult = "[" + dataRdd.map(d => MyJsonProtocol.DataIoTJsonFormat.write(d).compactPrint).reduce((a, b) => a + "," + b) + "]"
        val client = HttpClients.createDefault()
        val uri = new URI(CloudURL)
        val post = new HttpPost(uri)
        post.addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        post.setEntity(new StringEntity(jsonresult))
        val response = client.execute(post)
        datasent += SizeEstimator.estimate(jsonresult)
        try {
          val entity = response.getEntity
          println(response.getStatusLine.getStatusCode, response.getStatusLine.getReasonPhrase)
          println(IOUtils.toString(entity.getContent, StandardCharsets.UTF_8))
        }
        finally {
          response.close()

        }
      }
    }

    trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
      implicit val dataIotFormat: MyJsonProtocol.DataIoTJsonFormat.type = MyJsonProtocol.DataIoTJsonFormat
    }

    class MyJsonService extends Directives with JsonSupport {
      val route: Route = {
        path("data") {
          post {
            entity(as[DataIoT]) { data =>
              val saved: Future[Done] = saveData(data)
              onComplete(saved) { _ =>
                complete("OK")
              }

            }
          }
        }
      }
    }

    val bindingFuture = Http().bindAndHandle(new MyJsonService().route, "localhost", 8090)
    val thread = new Thread {
      override def run(): Unit = {
        var stop = false
        println(this.getName + " Started second (sender) thread...")
        while (!sc.isStopped && !stop) {
          Thread.sleep(sendIntervalMS)
          if (!sc.isStopped) {
            sendDataToCloud(rddDataIot)
            rddDataIot = sc.parallelize(new ListBuffer[DataIoT])
          }
          stop = stopped
        }
      }
    }
    thread.start()
    println(s"Server online at http://localhost:8090/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    println("Stopping web server")
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done
    println("Stopping Spark")
    println("Stopping thread")
    emptyBuffer()
    stopped = true
    thread.join()
    sc.stop()
    t1 = System.nanoTime()
    val ellipsedTime = t1 - t0
    println(s"Duration: $ellipsedTime nanoseconds")
    println(s"Data (size) received from IoT devices: $datareceived bytes")
    println(s"Data (size) sent to cloud for further processing: $datasent bytes")
  }
}
