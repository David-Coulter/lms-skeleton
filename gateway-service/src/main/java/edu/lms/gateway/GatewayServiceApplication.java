package edu.lms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway - the single origin for the whole system.
 *
 * Everything the browser or Postman touches comes through port 8080:
 *
 *   /auth/**        -> Keycloak        (login, token, password reset)
 *   /api/users/**   -> lb://user-service
 *
 * Routes to services go through Eureka (lb://), not fixed hosts, so running
 * several instances of a service needs no change here.
 *
 * The gateway does not verify tokens. It passes the Authorization header
 * straight through and each service verifies it against the realm's JWKS -
 * so a service is still safe if something reaches it directly.
 */
@SpringBootApplication
public class GatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
