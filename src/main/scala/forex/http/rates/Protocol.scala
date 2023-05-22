package forex.http
package rates

import forex.domain.Currency.show
import forex.domain.Rate.Pair
import forex.domain._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder

import java.time.OffsetDateTime

object Protocol {

  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  final case class GetApiRequest(
      from: Currency,
      to: Currency
  )

  final case class GetApiResponse(
      from: Currency,
      to: Currency,
      bid: BigDecimal,
      ask: BigDecimal,
      price: Price,
      timestamp: Timestamp
  )

  implicit val currencyEncoder: Encoder[Currency] =
    Encoder.instance[Currency] { show.show _ andThen Json.fromString }

  implicit val pairEncoder: Encoder[Pair] =
    deriveConfiguredEncoder[Pair]

  implicit val rateEncoder: Encoder[Rate] =
    deriveConfiguredEncoder[Rate]

  implicit val responseEncoder: Encoder[GetApiResponse] =
    deriveConfiguredEncoder[GetApiResponse]

  implicit val currencyDecoder: Decoder[Currency] = Decoder.instance { cursor =>
    for {
      value <- cursor.as[String]
      result <- Currency.fromString(value)
        .toRight(DecodingFailure(s"Invalid currency format: $value", cursor.history))
    } yield result
  }

  implicit val timestampDecoder: Decoder[OffsetDateTime] = Decoder.instance { cursor =>
    for {
      value <- cursor.as[String]
      result = OffsetDateTime.parse(value)
    } yield result
  }

  implicit val rateDecoder: Decoder[Rate] = Decoder.instance { cursor =>
    for {
      from <- cursor.downField("from").as[Currency]
      to <- cursor.downField("to").as[Currency]
      bid <- cursor.downField("bid").as[BigDecimal]
      ask <- cursor.downField("ask").as[BigDecimal]
      price <- cursor.downField("price").as[BigDecimal]
      timestamp <- cursor.downField("time_stamp").as[OffsetDateTime]
    } yield {
      Rate(from, to, bid, ask, Price(price), Timestamp(timestamp))
    }
  }

  implicit val responseDecoder: Decoder[List[Rate]] = Decoder.decodeList(rateDecoder)

  implicit val oneFrameRateDecoder: Decoder[Rate] = Decoder.instance { cursor =>
    for {
      from <- cursor.downField("from").as[Currency]
      to <- cursor.downField("to").as[Currency]
      bid <- cursor.downField("bid").as[BigDecimal]
      ask <- cursor.downField("ask").as[BigDecimal]
      price <- cursor.downField("price").as[BigDecimal]
      timestamp <- cursor.downField("timestamp").as[OffsetDateTime]
    } yield {
      Rate(from, to, bid, ask, Price(price), Timestamp(timestamp))
    }
  }
}