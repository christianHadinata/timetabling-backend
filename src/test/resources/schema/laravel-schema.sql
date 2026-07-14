-- Schema DDL for the Testcontainers/docker-compose MySQL seed (phase10.md §5.4).
-- Provenance: the real Laravel database was never connected (see phase9.md §0), so this
-- was materialized by booting the actual JPA entities with ddl-auto=update against a
-- throwaway MySQL 8.0 container, then mysqldump --no-data. It therefore reflects exactly
-- what Hibernate's ddl-auto=validate will check at runtime -- the best available proxy
-- for the production schema until the real Laravel DB dump is substituted (see D4).
-- Regenerate: boot the app with DDL_AUTO=update, SPRING_PROFILES_ACTIVE=schemagen against
-- a scratch MySQL container, then 'mysqldump --no-data --skip-comments <db> > this file'.
--
-- NOTE: mysqldump wraps its housekeeping SET statements in /*! ... */ "executable comments" --
-- real MySQL runs them, but Testcontainers' generic (DB-agnostic) ScriptUtils strips /* */
-- blocks as plain comments before executing, so FOREIGN_KEY_CHECKS never actually got disabled
-- and CREATE TABLE order (alphabetical, not FK-dependency order) broke on forward references
-- (e.g. `activities` -> `activity_types`). Plain, unwrapped SET statements fix that.
SET FOREIGN_KEY_CHECKS=0;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `activities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activities` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `course_class` varchar(3) NOT NULL,
  `course_session` int NOT NULL,
  `duration` int NOT NULL,
  `quota` int NOT NULL,
  `activity_type_id` int NOT NULL,
  `course_code` varchar(12) NOT NULL,
  `semester_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9y4tob28y3dslhftgbcb22d94` (`activity_type_id`),
  KEY `FKq1w76yhfpbxnebh28d9jq31rg` (`course_code`),
  KEY `FKqw1sxyda47q18r6sny72ntot8` (`semester_id`),
  CONSTRAINT `FK9y4tob28y3dslhftgbcb22d94` FOREIGN KEY (`activity_type_id`) REFERENCES `activity_types` (`id`),
  CONSTRAINT `FKq1w76yhfpbxnebh28d9jq31rg` FOREIGN KEY (`course_code`) REFERENCES `courses` (`code`),
  CONSTRAINT `FKqw1sxyda47q18r6sny72ntot8` FOREIGN KEY (`semester_id`) REFERENCES `semesters` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `activity_constraints`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_constraints` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `type` varchar(255) NOT NULL,
  `value` varchar(100) NOT NULL,
  `activity_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmd7w0p8iiighjvd4q3cu2gphf` (`activity_id`),
  CONSTRAINT `FKmd7w0p8iiighjvd4q3cu2gphf` FOREIGN KEY (`activity_id`) REFERENCES `activities` (`id`),
  CONSTRAINT `activity_constraints_chk_1` CHECK ((`type` in (_utf8mb4'RoomType',_utf8mb4'Lecturer',_utf8mb4'Room')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `activity_gaps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_gaps` (
  `id` int NOT NULL AUTO_INCREMENT,
  `id_act` int NOT NULL,
  `min_gap` int NOT NULL,
  `with_id_act` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `activity_paralels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_paralels` (
  `id` int NOT NULL AUTO_INCREMENT,
  `id_act` int NOT NULL,
  `with_id_act` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `activity_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_types` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `name` text NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `course_constraints`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_constraints` (
  `id` int NOT NULL AUTO_INCREMENT,
  `with_semester` int DEFAULT NULL,
  `course_id` int NOT NULL,
  `with_course_id` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKo0hahfqvb9b5l48yfp9fbicxo` (`course_id`),
  KEY `FKb3ek8d7ra56dl5rpp1xtb2pl3` (`with_course_id`),
  CONSTRAINT `FKb3ek8d7ra56dl5rpp1xtb2pl3` FOREIGN KEY (`with_course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `FKo0hahfqvb9b5l48yfp9fbicxo` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courses` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `code` varchar(12) NOT NULL,
  `konsentrasi` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `tingkat` int DEFAULT NULL,
  `type` enum('Pilihan','Wajib') NOT NULL,
  `jurusan_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK61og8rbqdd2y28rx2et5fdnxd` (`code`),
  KEY `FKn0pcc6wn93lyhdwdhjv6xaibr` (`jurusan_id`),
  CONSTRAINT `FKn0pcc6wn93lyhdwdhjv6xaibr` FOREIGN KEY (`jurusan_id`) REFERENCES `jurusans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `jurusans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jurusans` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `color` int NOT NULL,
  `faculty` varchar(255) NOT NULL,
  `jenjang` enum('D3','S1','S2','S3') NOT NULL DEFAULT 'S1',
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `konsentrasi`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `konsentrasi` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `konsentrasi` text NOT NULL,
  `jurusan_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhrv8otk7eyxqkl49iyl2cgunf` (`jurusan_id`),
  CONSTRAINT `FKhrv8otk7eyxqkl49iyl2cgunf` FOREIGN KEY (`jurusan_id`) REFERENCES `jurusans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lecturer_time_n_as`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturer_time_n_as` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `day` int NOT NULL,
  `end_time` time NOT NULL,
  `start_time` time NOT NULL,
  `type` varchar(100) NOT NULL,
  `lecturer_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKike68co5r0w6y36og5f6ng1eh` (`lecturer_id`),
  CONSTRAINT `FKike68co5r0w6y36og5f6ng1eh` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturers` (`id`),
  CONSTRAINT `lecturer_time_n_as_chk_1` CHECK ((`type` in (_utf8mb4'Priority',_utf8mb4'Not-Available')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lecturers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturers` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `alias` text,
  `home_base` int NOT NULL,
  `name` text NOT NULL,
  `nik` varchar(100) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `results` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `day` text,
  `end_time` time DEFAULT NULL,
  `start_time` time DEFAULT NULL,
  `valid` tinyint NOT NULL,
  `activity_id` int NOT NULL,
  `room_id` int DEFAULT NULL,
  `semester_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKf5tg20jk7i902rarmhx9mx9t4` (`activity_id`),
  KEY `FK5w10qfjscqsna2w80tn0knet5` (`room_id`),
  KEY `FK73q560jsav8cjqowlm8ycc05t` (`semester_id`),
  CONSTRAINT `FK5w10qfjscqsna2w80tn0knet5` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`),
  CONSTRAINT `FK73q560jsav8cjqowlm8ycc05t` FOREIGN KEY (`semester_id`) REFERENCES `semesters` (`id`),
  CONSTRAINT `FKf5tg20jk7i902rarmhx9mx9t4` FOREIGN KEY (`activity_id`) REFERENCES `activities` (`id`),
  CONSTRAINT `results_chk_1` CHECK ((`valid` in (0,1)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `room_availables`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_availables` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `day` int NOT NULL,
  `end_time` time NOT NULL,
  `start_time` time NOT NULL,
  `room_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKd27fjcxjk3n05x2wmcvhpf9oi` (`room_id`),
  CONSTRAINT `FKd27fjcxjk3n05x2wmcvhpf9oi` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `room_types`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `room_types` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `name` text NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rooms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rooms` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `building` text NOT NULL,
  `capacity` int NOT NULL,
  `floor` varchar(20) NOT NULL,
  `location` text NOT NULL,
  `name` text NOT NULL,
  `room_code` varchar(20) NOT NULL,
  `unit_owner` text NOT NULL,
  `virtual` varchar(255) DEFAULT NULL,
  `parent_room_id` int DEFAULT NULL,
  `room_type_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK13e87vs7wsn73ev7bah49ii87` (`parent_room_id`),
  KEY `FKh9m2n1paq5hmd3u0klfl7wsfv` (`room_type_id`),
  CONSTRAINT `FK13e87vs7wsn73ev7bah49ii87` FOREIGN KEY (`parent_room_id`) REFERENCES `rooms` (`id`),
  CONSTRAINT `FKh9m2n1paq5hmd3u0klfl7wsfv` FOREIGN KEY (`room_type_id`) REFERENCES `room_types` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `semesters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `semesters` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `academic_year` text NOT NULL,
  `current` bit(1) NOT NULL,
  `type` text NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `setting_constraints`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `setting_constraints` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `settingable_type` varchar(255) NOT NULL,
  `settingable_value` varchar(255) NOT NULL,
  `setting_id` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `setting_constraint_const` (`setting_id`,`settingable_value`,`settingable_type`),
  CONSTRAINT `FKkolchwtgahgpb4vl573w4a3be` FOREIGN KEY (`setting_id`) REFERENCES `settings` (`id`),
  CONSTRAINT `setting_constraints_chk_1` CHECK ((`settingable_type` in (_utf8mb4'hari',_utf8mb4'activity',_utf8mb4'waktu',_utf8mb4'jurusan',_utf8mb4'activityType',_utf8mb4'roomType',_utf8mb4'room')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `settings` (
  `id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `semester_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqn0jtgqhjn9n3ikqif2h20uja` (`semester_id`),
  CONSTRAINT `FKqn0jtgqhjn9n3ikqif2h20uja` FOREIGN KEY (`semester_id`) REFERENCES `semesters` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `slot_acts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `slot_acts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `priority` int NOT NULL,
  `activity_id` int NOT NULL,
  `slot_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkrup31hmifuw9istvrywwkvi7` (`activity_id`),
  KEY `FKbrxfgbnggm1gj6t2hlpwl2gm4` (`slot_id`),
  CONSTRAINT `FKbrxfgbnggm1gj6t2hlpwl2gm4` FOREIGN KEY (`slot_id`) REFERENCES `slots` (`id`),
  CONSTRAINT `FKkrup31hmifuw9istvrywwkvi7` FOREIGN KEY (`activity_id`) REFERENCES `activities` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `slots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `slots` (
  `id` int NOT NULL AUTO_INCREMENT,
  `room_id` int NOT NULL,
  `time_id` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKn2wronpe2efhkkhmrrbhnjyfv` (`room_id`),
  KEY `FKmrlxkyikct1csgvvbn2woogtr` (`time_id`),
  CONSTRAINT `FKmrlxkyikct1csgvvbn2woogtr` FOREIGN KEY (`time_id`) REFERENCES `times` (`id`),
  CONSTRAINT `FKn2wronpe2efhkkhmrrbhnjyfv` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `times`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `times` (
  `id` int NOT NULL AUTO_INCREMENT,
  `day` int NOT NULL,
  `hour` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `email_verified_at` datetime(6) DEFAULT NULL,
  `faculty` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `remember_token` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `validate_lock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `validate_lock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `lock` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
SET FOREIGN_KEY_CHECKS=1;

