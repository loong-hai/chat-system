-- MySQL dump 10.13  Distrib 8.0.45, for Linux (x86_64)
--
-- Host: localhost    Database: chat_system
-- ------------------------------------------------------
-- Server version	8.0.45-0ubuntu0.24.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `chat_system`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `chat_system` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `chat_system`;

--
-- Table structure for table `avatar_history`
--

DROP TABLE IF EXISTS `avatar_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `avatar_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `is_current` bit(1) DEFAULT NULL,
  `original_name` varchar(255) DEFAULT NULL,
  `s3_key` varchar(500) NOT NULL,
  `status` varchar(20) DEFAULT NULL,
  `thumbnail_key` varchar(500) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_created` (`user_id`,`created_at` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_index`
--

DROP TABLE IF EXISTS `file_index`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_index` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `file_hash` varchar(64) NOT NULL,
  `file_size` bigint DEFAULT NULL,
  `is_public` bit(1) DEFAULT NULL,
  `mime_type` varchar(100) DEFAULT NULL,
  `original_name` varchar(255) DEFAULT NULL,
  `ref_count` int DEFAULT NULL,
  `s3_key` varchar(500) NOT NULL,
  `source_type` varchar(20) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_file_hash` (`file_hash`),
  KEY `idx_s3_key` (`s3_key`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file_upload_transaction`
--

DROP TABLE IF EXISTS `file_upload_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_upload_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `content_type` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `error_msg` varchar(500) DEFAULT NULL,
  `file_hash` varchar(64) DEFAULT NULL,
  `file_key` varchar(500) NOT NULL,
  `file_size` bigint DEFAULT NULL,
  `retry_count` int DEFAULT NULL,
  `source_type` varchar(20) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_created` (`status`,`created_at`),
  KEY `idx_file_key` (`file_key`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `friend_group`
--

DROP TABLE IF EXISTS `friend_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `friend_group` (
  `group_id` bigint NOT NULL AUTO_INCREMENT,
  `color` varchar(10) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(200) DEFAULT NULL,
  `group_name` varchar(50) NOT NULL,
  `icon` varchar(255) DEFAULT NULL,
  `is_default` bit(1) NOT NULL,
  `is_visible` bit(1) NOT NULL,
  `sort_order` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `UKf0kmlnhop5bd3frpbdhnolqfj` (`user_id`,`group_name`),
  CONSTRAINT `FKjm08cnturokssd0x5isbspg54` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=50036 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `friend_relation`
--

DROP TABLE IF EXISTS `friend_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `friend_relation` (
  `relation_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `intimacy_level` int NOT NULL,
  `is_muted` bit(1) NOT NULL,
  `is_pinned` bit(1) NOT NULL,
  `last_interaction` datetime(6) DEFAULT NULL,
  `remark` varchar(50) DEFAULT NULL,
  `status` int NOT NULL,
  `friend_id` bigint NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `UKmfux9jlbvrr0wfbkiibwp8gw0` (`user_id`,`friend_id`),
  KEY `idx_user_group` (`user_id`,`group_id`),
  KEY `idx_last_interaction` (`user_id`,`last_interaction` DESC),
  KEY `FK9uibhxmianqa53jf3o5wshy9f` (`friend_id`),
  KEY `FK3jmywwmti161u6o1f5su3k85t` (`group_id`),
  CONSTRAINT `FK10ro4hm4r74s4vyj16yexhjox` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FK3jmywwmti161u6o1f5su3k85t` FOREIGN KEY (`group_id`) REFERENCES `friend_group` (`group_id`),
  CONSTRAINT `FK9uibhxmianqa53jf3o5wshy9f` FOREIGN KEY (`friend_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=400001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `friend_request`
--

DROP TABLE IF EXISTS `friend_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `friend_request` (
  `request_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `message` varchar(200) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `reject_reason` varchar(200) DEFAULT NULL,
  `source` varchar(50) DEFAULT NULL,
  `status` int NOT NULL,
  `receiver_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `UK1o6k35asg93qa1wjg8chjd5rf` (`sender_id`,`receiver_id`),
  KEY `idx_receiver_status` (`receiver_id`,`status`,`created_at` DESC),
  KEY `idx_sender_status` (`sender_id`,`status`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `FK9rnftqmm2lmkhv4xrq8b9lp4f` FOREIGN KEY (`sender_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FKpu7xdjn95orp6rucjsxps7gkg` FOREIGN KEY (`receiver_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `account_non_expired` bit(1) DEFAULT NULL,
  `account_non_locked` bit(1) DEFAULT NULL,
  `avatar_url` varchar(500) DEFAULT NULL,
  `birthday` date DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `credentials_non_expired` bit(1) DEFAULT NULL,
  `deregister_time` datetime(6) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `gender` int DEFAULT NULL,
  `is_verified` bit(1) DEFAULT NULL,
  `last_activity_time` datetime(6) DEFAULT NULL,
  `last_login_ip` varchar(50) DEFAULT NULL,
  `last_login_time` datetime(6) DEFAULT NULL,
  `nickname` varchar(50) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `register_time` datetime(6) NOT NULL,
  `signature` varchar(200) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_status` int NOT NULL,
  `username` varchar(50) NOT NULL,
  `verification_code` varchar(100) DEFAULT NULL,
  `verification_expire` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKob8kqyqqgmefl0aco34akdtpe` (`email`),
  UNIQUE KEY `UK589idila9li6a4arw1t8ht1gx` (`phone`),
  KEY `idx_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`user_status`),
  KEY `idx_register_time` (`register_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12008 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-08  9:01:01
