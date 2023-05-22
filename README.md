## Implementation of Paidy Forex challenge.

### What was done? 👀️

1. The service returns an exchange rate when provided with 2 supported currencies
2. The rate cannot be older than 5 minutes, but in the case of the same request within 5 minutes - you will receive the same result, implemented using cache.
3. The service support at 10,000 successful requests per day with 1 API token, implemented using rate logic

### How tu run? 🚀️

1. download github code, `git clone .....`
2. run docker container,`docker run -p 8080:8080 paidyinc/one-frame`
3. from project folder, run `sbt compile`
4. from project folder, run `sbt run`
5. Then use Postman or any other app for requests.
   I'll provide several examples for routes:
   `http://localhost:10000/rates?from=USD&to=EUR`;
   `http://localhost:10000/rates?from=USD&to=USD`;
   `http://localhost:10000/rates?from=USD1&to=EUR`;
   `http://localhost:10000/rates?from=USD&to=EUR1`;
   `for test limit, just set 5 insted 10000 in application.conf and run 6 times request, like http://localhost:10000/rates?from=USD&to=EUR`

### How to test? 🎉️

1. from project folder, run `sbt test`

### My thoughts! 👍

First of all, I want to say that it was an interesting task in the process of which I had fun. I tried to predict real cases of currency exchange and create validation for them in the context of a proxy server. I created a proxy server for currency exchange and also used CaffeineCache to create a cache that managed by ttl time. In addition, I created a limit of requests within one day, which is set to 10000 (for testing, it is better to set 5 😄😄😄)
I think this task has great potential, I see several additional features that could be added, such as logging, pools and use Redis cache, but of course it all costs more time.
