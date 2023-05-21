package forex.domain

case class Rate(
    from: Currency,
    to: Currency,
    bid: BigDecimal,
    ask: BigDecimal,
    price: Price,
    timestamp: Timestamp
)

object Rate {
  final case class Pair(
      from: Currency,
      to: Currency
  )
}
