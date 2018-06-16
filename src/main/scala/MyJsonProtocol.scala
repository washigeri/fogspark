import spray.json._

object MyJsonProtocol extends DefaultJsonProtocol {

  implicit object DataIoTJsonFormat extends RootJsonFormat[DataIoT] {
    override def read(json: JsValue): DataIoT = {
      json.asJsObject.getFields("location", "deviceID", "data") match {
        case Seq(JsString(location), JsNumber(deviceID), JsObject(data)) =>
          new DataIoT(deviceID.intValue(), location, data("datatype").toString(), data("value").toString())
        case _ => throw DeserializationException("DataIoT expected")
      }
    }

    override def write(obj: DataIoT): JsValue = {
      JsObject(
        "location" -> JsString(obj.location),
        "deviceID" -> JsNumber(obj.devID),
        "data" -> JsObject(
          "datatype" -> JsString(obj.datatype),
          "value" -> JsString(obj.datavalue)
        )
      )
    }
  }

}
