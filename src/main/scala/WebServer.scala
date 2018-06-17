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
import org.apache.spark.{SparkConf, SparkContext}
import spray.json.DefaultJsonProtocol

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn


object WebServer {

  val Conf: SparkConf = new SparkConf().setAppName("FogSpark").setMaster("local[*]")
  val CloudURL = "http://localhost:8080/"
  val bufferSize = 10000
  val sendIntervalMS = 60000


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
      if (!dataIoTs.exists(d => d.equals(dataIoT))) {
        dataIoTs.+=(dataIoT)
        if (dataIoTs.length >= bufferSize) {
          val temp = sc.parallelize(dataIoTs)
          rddDataIot = rddDataIot.union(temp).distinct()
          dataIoTs.clear()
        }
      }
      Future {
        Done
      }
    }

    def sendDataToCloud(dataRdd: RDD[DataIoT]): Unit = {
      println(Thread.currentThread().getName + " Sending data to cloud")
      val jsonresult = "[" + dataRdd.map(d => MyJsonProtocol.DataIoTJsonFormat.write(d).compactPrint).reduce((a, b) => a + "," + b) + "]"
      val client = HttpClients.createDefault()
      val uri = new URI(CloudURL)
      val post = new HttpPost(uri)
      post.addHeader(HttpHeaders.CONTENT_TYPE, "application/json")
      post.setEntity(new StringEntity(jsonresult))
      val response = client.execute(post)
      try {
        val entity = response.getEntity
        println(response.getStatusLine.getStatusCode, response.getStatusLine.getReasonPhrase)
        println(IOUtils.toString(entity.getContent, StandardCharsets.UTF_8))
      }
      finally {
        response.close()
      }
    }

    trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
      implicit val dataIotFormat: MyJsonProtocol.DataIoTJsonFormat.type = MyJsonProtocol.DataIoTJsonFormat
    }

    class MyJsonService extends Directives with JsonSupport {
      val route: Route = {
        path("/") {
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
        println(this.getName + " Started second (sender) thread...")
        while (!sc.isStopped) {
          Thread.sleep(sendIntervalMS)
          sendDataToCloud(rddDataIot)
          rddDataIot = sc.parallelize(new ListBuffer[DataIoT])
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
    sc.stop()
    println("Stopping thread")
    thread.join()
  }
}
