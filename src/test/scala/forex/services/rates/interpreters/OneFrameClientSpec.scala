package forex.services.rates.interpreters

import cats.effect._
import cats.implicits.toTraverseOps
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import forex.config.OneFrameConfig
import forex.domain._
import forex.services.rates.Errors.Error.{OneFrameLookupFailed, RateLimitExceeded}
import org.http4s.server.Server

import scala.concurrent.ExecutionContext

class OneFrameClientSpec extends AnyFlatSpec with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  val rateLimit = 5
  val fakeRateLimiter = new FakeRateLimiter(rateLimit)

  val config: OneFrameConfig = OneFrameConfig(
    url = "http://localhost:8080",
    token = "10dc303535874aeccc86a8251e6992f5",
    ttl = scala.concurrent.duration.Duration.fromNanos(300000000000L),
    rateLimit = 10000
  )

  val mockRateResponse: String =
    """
      |[
      |  {
      |    "from": "USD",
      |    "to": "EUR",
      |    "bid": 0.6118225421857174,
      |    "ask": 0.8243869101616611,
      |    "price": 0.71810472617368925,
      |    "timestamp": "2023-05-21T20:56:12.907Z"
      |  }
      |]
      |""".stripMargin

  val testRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "rates" =>
      Ok(mockRateResponse)
  }
  val testServer: Resource[IO, Server[IO]] = BlazeServerBuilder[IO](ExecutionContext.global)
    .bindHttp(10000, "localhost")
    .withHttpApp(testRoutes.orNotFound)
    .resource

  "OneFrameClient" should "build correct request with correct parameters" in {
    testServer.use { _ =>
      val oneFrameClient = new OneFrameClient[IO](config, fakeRateLimiter)

      oneFrameClient.get(Rate.Pair(Currency.USD, Currency.EUR)).map {
        case Right(rate) =>
          rate.from should be(Currency.USD)
          rate.to should be(Currency.EUR)
        case Left(_) =>
          fail("Received an error instead of a rate")
      }
    }.unsafeRunSync()
  }

  "OneFrameClient" should "be correctly reported in case if the request was not valid" in {
    val errorRoutes = HttpRoutes.of[IO] {
      case GET -> Root / "rates" =>
        InternalServerError("Server error")
    }

    val errorServer = BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(8080, "localhost")
      .withHttpApp(errorRoutes.orNotFound)
      .resource

    errorServer.use { _ =>
      val oneFrameClient = new OneFrameClient[IO](config, fakeRateLimiter)

      oneFrameClient.get(Rate.Pair(Currency.USD, Currency.USD)).map {
        case Right(_) =>
          fail("Received a rate instead of an error")
        case Left(error) =>
          error shouldBe a[OneFrameLookupFailed]
      }
    }.unsafeRunSync()
  }

  "OneFrameClient" should "should use cache in case if user called the same request within 5 minutes" in {
    testServer.use { _ =>
      val oneFrameClient = new OneFrameClient[IO](config, fakeRateLimiter)

      for {
        addedToCacheResult <- oneFrameClient.get(Rate.Pair(Currency.USD, Currency.EUR))
        timestamp <- addedToCacheResult match {
          case Left(error) => IO.raiseError(new Exception(s"Unexpected error: $error"))
          case Right(rate) => IO.pure(rate.timestamp)
        }

        // should hit the cache and not create one more request
        firstResultFromCache <- oneFrameClient.get(Rate.Pair(Currency.USD, Currency.EUR))
        timestampFromCache <- firstResultFromCache match {
          case Left(error) => IO.raiseError(new Exception(s"Unexpected error: $error"))
          case Right(rateFromCache) => IO.pure(rateFromCache.timestamp)
        }

        _ <- IO(timestamp should be(timestampFromCache))

      } yield ()
    }.unsafeRunSync()
  }

  "OneFrameClient" should "limit the rate of requests" in {
    val counter = new FakeRateLimiter(rateLimit)
    testServer.use { _ =>
      val oneFrameClient = new OneFrameClient[IO](config, counter)

      val pair = Rate.Pair(Currency.USD, Currency.EUR)

      // Make rateLimit + 1 requests sequentially
      val results = (0 until rateLimit + 1).toList.traverse(_ => oneFrameClient.get(pair))

      results.map { res =>
        val successfulRequests = res.count(_.isRight)
        val failedRequests = res.count {
          case Left(RateLimitExceeded(_)) => true
          case _ => false
        }

        successfulRequests should be(rateLimit)
        failedRequests should be(1) // one request should exceed the rate limit
      }
    }.unsafeRunSync()
  }



}


class FakeRateLimiter(val limit: Int) extends RateLimiter(limit) {
  private var _counter: Int = 0
  override def isRateLimited: Boolean = _counter >= limit
  override val incrementCounter: () => Unit = () => _counter += 1
}

