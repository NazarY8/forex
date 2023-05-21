package forex.services.rates

object Errors {

  sealed trait Error
  object Error {
    final case class OneFrameLookupFailed(msg: String) extends Error
  }

}
