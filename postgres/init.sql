-- One database per service, per the team architecture.
-- Runs once, the first time the postgres volume is created.
--
-- dashboard-service owns no database (it aggregates the others over REST), but
-- dashboarddb is created anyway so the skeleton template works unchanged if
-- someone generates it.
CREATE DATABASE userdb;
CREATE DATABASE scheduledb;
CREATE DATABASE enrollmentdb;
CREATE DATABASE learningdb;
CREATE DATABASE assessmentdb;
CREATE DATABASE paymentdb;
CREATE DATABASE dashboarddb;
CREATE DATABASE keycloak;
