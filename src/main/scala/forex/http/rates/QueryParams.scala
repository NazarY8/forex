package forex.http.rates

import forex.domain.Currency
import org.http4s.{ParseFailure, QueryParamDecoder}
import org.http4s.dsl.impl.ValidatingQueryParamDecoderMatcher

object QueryParams {

  implicit val currencyQueryParamDecoder: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].emap { str =>
      Currency.fromString(str)
        .toRight(ParseFailure("Invalid currency format", s"Invalid currency format: $str"))
    }

  object FromQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends ValidatingQueryParamDecoderMatcher[Currency]("to")
}
