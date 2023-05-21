package forex.services.rates.interpreters

import cats.Applicative
import cats.effect.{ConcurrentEffect, Resource}
import cats.implicits.{catsSyntaxApplicativeError, catsSyntaxEitherId, toFunctorOps}
import forex.config.OneFrameConfig
import forex.domain.Rate
import forex.http.rates.Protocol.responseDecoder
import forex.services.rates.Algebra
import forex.services.rates.Errors.Error.{OneFrameLookupFailed, RateLimitExceeded}
import forex.services.rates.Errors._
import org.http4s._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import com.github.benmanes.caffeine.cache.{Cache, Caffeine}

import java.time.Duration
import scala.concurrent.ExecutionContext

class OneFrameClient[F[_]: ConcurrentEffect](config: OneFrameConfig) extends Algebra[F] {
  private val blazeClient : Resource[F, Client[F]] = BlazeClientBuilder[F](ExecutionContext.global).resource
  private implicit val rateDecoder: EntityDecoder[F, List[Rate]] = jsonOf[F, List[Rate]]

  private val cache: Cache[Rate.Pair, List[Rate]] = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofSeconds(config.ttl.toSeconds))
    .build[Rate.Pair, List[Rate]]()

  private val rateLimiter: RateLimiter = RateLimiter(config.rateLimit)

  override def get(pair: Rate.Pair): F[Error Either Rate] = {
    if (rateLimiter.isRateLimited) {
      Applicative[F].pure(Left(RateLimitExceeded(s"The rate within one day is - ${config.rateLimit}, please try again later")))
    } else {
      rateLimiter.incrementCounter()
      Option(cache.getIfPresent(pair)) match {
        case Some(value) =>
          Applicative[F].pure(Right(value.last))
        case None =>
          buildRequest(pair).map {
            case Right(value) =>
              cache.put(pair, value)
              Right(value.last)
            case Left(error) =>
              Left(error)
          }
      }
    }
  }

  def buildRequest(pair: Rate.Pair): F[Error Either List[Rate]] = {
    val uri = s"${config.url}/rates"

    val request = Request[F](
      method = Method.GET,
      uri = Uri.unsafeFromString(uri).withQueryParam("pair", s"${pair.from}${pair.to}"),
      headers = Headers.of(Header("token", config.token))
    )

    blazeClient.use(client =>
      client.expect[List[Rate]](request))
      .map(response => response.asRight[Error])
      .handleError(_ => OneFrameLookupFailed("Response Error").asLeft[List[Rate]])
  }

}