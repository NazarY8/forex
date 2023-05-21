package forex.http.rates

import forex.domain._

object Converters {
  import Protocol._

  private[rates] implicit class GetApiResponseOps(val rate: Rate) extends AnyVal {
    def asGetApiResponse: GetApiResponse =
      GetApiResponse(
        from = rate.from,
        to = rate.to,
        bid = rate.bid,
        ask = rate.ask,
        price = rate.price,
        timestamp = rate.timestamp
      )
  }

}
