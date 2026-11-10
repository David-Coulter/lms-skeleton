package lms;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Load test for LearningHub, driven entirely through the gateway.
 *
 * Every virtual user does what a real one does:
 *   1. logs in at /auth and gets a JWT from Keycloak
 *   2. reuses that one token for several authenticated calls
 *
 * Reusing the token is the point. Because the services are stateless and verify
 * the signature locally, no request touches Keycloak after login and no request
 * needs a session - which is what makes the service horizontally scalable.
 *
 * Run it:
 *     docker compose --profile load run --rm gatling
 *
 * Scale first to show load-balancing through Eureka:
 *     docker compose up -d --scale user-service=3
 *     docker compose --profile load run --rm gatling
 *
 * The HTML report lands in gatling/target/gatling/.
 */
public class UserServiceSimulation extends Simulation {

    // Inside the compose network the gateway is reachable by service name.
    private static final String GATEWAY =
            System.getProperty("gateway", "http://gateway-service:8080");

    private static final int    USERS    = Integer.getInteger("users", 60);
    private static final int    RAMP     = Integer.getInteger("ramp", 30);
    private static final int    DURATION = Integer.getInteger("duration", 60);

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(GATEWAY)
            .acceptHeader("application/json")
            .userAgentHeader("Gatling/LearningHub-Phase2");

    /** Log in through the gateway and keep the access token in the session. */
    private final ScenarioBuilder scn = scenario("Authenticated user-service traffic")

            .exec(http("POST /auth - login")
                    .post("/auth/realms/learninghub/protocol/openid-connect/token")
                    .formParam("grant_type", "password")
                    .formParam("client_id", "learninghub-spa")
                    .formParam("username", "student1")
                    .formParam("password", "student123")
                    .check(status().is(200))
                    .check(jsonPath("$.access_token").saveAs("token")))

            .pause(Duration.ofMillis(200))

            // The work a signed-in student actually does, repeated.
            .repeat(10).on(
                    exec(http("GET /api/users/teachers")
                            .get("/api/users/teachers")
                            .header("Authorization", "Bearer #{token}")
                            .check(status().is(200))
                            .check(jsonPath("$[0].role").is("teacher")))

                    .pause(Duration.ofMillis(100))

                    .exec(http("GET /api/users/profiles/whoami")
                            .get("/api/users/profiles/whoami")
                            .header("Authorization", "Bearer #{token}")
                            .check(status().is(200)))

                    .pause(Duration.ofMillis(100))
            );

    {
        setUp(
                scn.injectOpen(
                        // warm the JIT and the connection pool before measuring
                        rampUsers(10).during(Duration.ofSeconds(5)),
                        // then the real load
                        rampUsers(USERS).during(Duration.ofSeconds(RAMP)),
                        constantUsersPerSec(USERS / 10.0).during(Duration.ofSeconds(DURATION))
                )
        )
        .protocols(httpProtocol)
        .assertions(
                // Resilience: nothing fails under sustained load.
                global().failedRequests().percent().lt(1.0),
                // Scalability: latency stays bounded as concurrency rises.
                global().responseTime().percentile3().lt(1500),   // p95
                global().responseTime().mean().lt(500)
        );
    }
}
