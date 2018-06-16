import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn


object WebServer {


  def main(args: Array[String]): Unit = {

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
      implicit val dataIotFormat = MyJsonProtocol.DataIoTJsonFormat
    }

    class MyJsonService extends Directives with JsonSupport {
      val route = {
        path("data") {
          post {
            entity(as[DataIoT]) { data =>
              val saved: Future[Done] = saveData(data)
              onComplete(saved) { done =>
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
  }
}
