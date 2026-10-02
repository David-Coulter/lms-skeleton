package edu.lms.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server.
 *
 * Serves per-service, per-profile configuration to every other service in the
 * system, so the dev/prod difference lives in one place instead of being
 * duplicated in each service's jar.
 *
 * Config files live in src/main/resources/config and are served as:
 *   GET /{application}/{profile}
 * e.g. http://localhost:8888/user-service/dev
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceApplication.class, args);
    }
}
