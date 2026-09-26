-- One database per service, per the architecture diagram.
-- Runs once, the first time the postgres volume is created.
CREATE DATABASE userdb;
CREATE DATABASE academicdb;
CREATE DATABASE scheduledb;
CREATE DATABASE enrollmentdb;
CREATE DATABASE learningdb;
CREATE DATABASE assessmentdb;
CREATE DATABASE paymentdb;
CREATE DATABASE analyticsdb;
CREATE DATABASE keycloak;
