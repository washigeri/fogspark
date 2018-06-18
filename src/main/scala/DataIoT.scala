import java.util.Date

//noinspection SpellCheckingInspection
class DataIoT(var devID: Int, var location: String, var datatype: String, var datavalue: String, var added: Date) extends Serializable {

  override def equals(obj: scala.Any): Boolean = obj match {
    case obj: DataIoT => obj.canEqual(this) && obj.fieldsEqual(this)
    case _ => false
  }

  def canEqual(a: Any): Boolean = a.isInstanceOf[DataIoT]

  def fieldsEqual(a: DataIoT): Boolean = a.datavalue == this.datavalue && a.typeEqual(this)

  def typeEqual(a: DataIoT): Boolean = a.datatype == this.datatype &&
    a.devID == this.devID && a.location == this.location

}
