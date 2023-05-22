package forex.http.rates

import cats.data.Validated.{Invalid, Valid}
import cats.effect.Sync
import cats.implicits.catsSyntaxApplicativeError
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.Errors.Error.RateLimitExceeded
import forex.programs.rates.{Protocol => RatesProgramProtocol}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(fromValidated) +& ToQueryParam(toValidated) =>
      (fromValidated, toValidated) match {
        case (Valid(from), Valid(to)) =>
          rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap(Sync[F].fromEither).flatMap { rate =>
            Ok(rate.asGetApiResponse)
          }.handleErrorWith {
            case RateLimitExceeded(message) =>
              InternalServerError(s"The number of requests is limited: -> $message")
            case _: Throwable if from == to =>
              BadRequest(s"Currency should be different, you can't exchange the same type of currency: $from -> $to")
            case _: Throwable =>
              InternalServerError("An unexpected error occurred")
          }
        case (Invalid(errors), _) =>
          BadRequest("Invalid 'from' parameter: " + errors.toList.map(_.toString).mkString(", "))
        case (_, Invalid(errors)) =>
          BadRequest("Invalid 'to' parameter: " + errors.toList.map(_.toString).mkString(", "))
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
