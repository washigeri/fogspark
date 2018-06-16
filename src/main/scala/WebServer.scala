import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import org.apache.spark.{SparkConf, SparkContext}
import spray.json.DefaultJsonProtocol

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn


object WebServer {

  val Conf: SparkConf = new SparkConf().setAppName("FogSpark").setMaster("local[*]")
  val CloudURL = "http://localhost:8090/data"


  def main(args: Array[String]): Unit = {

    //start spark
    val sc = new SparkContext(Conf)
    sc.setLogLevel("ERROR")


    var dataIoTs: ListBuffer[DataIoT] = new ListBuffer()
    implicit val system: ActorSystem = ActorSystem("fog-spark-http")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val dataformat: MyJsonProtocol.type = MyJsonProtocol

    def saveData(dataIoT: DataIoT): Future[Done] = {
      dataIoTs.+=(dataIoT)
      Future {
        Done
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

    val bindingFuture = Http().bindAndHandle(new MyJsonService().route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ ⇒ system.terminate()) // and shutdown when done
    println(dataIoTs)
    sc.stop()
  }
}
