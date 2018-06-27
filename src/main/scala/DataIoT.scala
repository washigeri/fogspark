import java.util.Date

/**
  * Classe représentant les données reçues par les objets d'IoT
  *
  * @param devID     ID de l'objet envoyant les données
  * @param location  Position de l'objet
  * @param datatype  type de données transmises (temperature, humidity, etc.)
  * @param datavalue valeur de la donnée
  * @param added     Date de réception
  */
//noinspection SpellCheckingInspection
class DataIoT(var devID: Int, var location: String, var datatype: String, var datavalue: String, var added: Date) extends Serializable {

  /**
    * Méthode verifiant l'égalité de deux instances représentant des données d'IoT
    *
    * @param obj objet à comparer
    * @return vrai ou faux si les deux objets sont identiques
    */
  override def equals(obj: scala.Any): Boolean = obj match {
    case obj: DataIoT => obj.canEqual(this) && obj.fieldsEqual(this)
    case _ => false
  }

  /**
    * Méthode vérifiant pour [[DataIoT.equals]] si les deux objets sont des instances de la même classe
    *
    * @param a objet à comparer
    * @return vrai ou faux si même classe
    */
  def canEqual(a: Any): Boolean = a.isInstanceOf[DataIoT]

  /**
    * Vérifie l'égalité des champs + l'égalité de la valeur de données entre deux [[DataIoT]]
    *
    * @param a objet à vérifier
    * @return Vrai ou Faux si les champs sont égaux
    */
  def fieldsEqual(a: DataIoT): Boolean = a.datavalue == this.datavalue && a.typeEqual(this)

  /**
    * Vérfie l'égalité des type de données (ex: temperature == temperature) entre deux [[DataIoT]]
    *
    * @param a objet à comparer
    * @return Vrai ou Faux
    */
  def typeEqual(a: DataIoT): Boolean = a.datatype == this.datatype &&
    a.devID == this.devID && a.location == this.location

}
