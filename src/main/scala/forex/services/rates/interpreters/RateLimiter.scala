package forex.services.rates.interpreters

import java.time.Duration


private[interpreters] case class RateLimiter(private val limit: Int) {
  private var counter: Int = 0
  private var lastResetTime: Long = System.currentTimeMillis()

  def isRateLimited: Boolean = {
    val currentTime = System.currentTimeMillis()
    val elapsedTime = currentTime - lastResetTime

    if (elapsedTime >= Duration.ofDays(1).toMillis) {
      // Reset the counter if 24 hours have passed since the last reset
      counter = 0
      lastResetTime = currentTime
    }

    counter >= limit
  }

  val incrementCounter: () => Unit = () => counter += 1
}
