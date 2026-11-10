package edu.lms.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Netflix Eureka service registry.
 *
 * Every business service registers itself here at startup. The gateway then
 * routes to lb://user-service rather than a fixed host and port, so a service
 * can move, restart, or run as several instances without anything upstream
 * being reconfigured.
 *
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
