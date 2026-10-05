-- phpMyAdmin SQL Dump
-- Database: `ebanking_db`
-- Core Architecture, Authentication & Real-time Transfer (Dev 1)

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `accounts`
--

CREATE TABLE IF NOT EXISTS `accounts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `account_number` varchar(20) NOT NULL,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `balance` double NOT NULL DEFAULT 0,
  `status` varchar(20) DEFAULT 'ACTIVE',
  PRIMARY KEY (`id`),
  UNIQUE KEY `account_number` (`account_number`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dữ liệu khởi tạo cho bảng `accounts`
--

INSERT INTO `accounts` (`id`, `account_number`, `username`, `password`, `full_name`, `balance`, `status`) VALUES
(1, '1001', 'usera', '123456', 'Nguyen Van A', 3500000, 'ACTIVE'),
(2, '1002', 'userb', '123456', 'Tran Thi B', 1650000, 'LOCKED'),
(3, '1003', 'userc', '123456', 'Le Van C', 2000000, 'ACTIVE')
ON DUPLICATE KEY UPDATE `full_name`=VALUES(`full_name`);

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `transactions` (Ghi nhận lịch sử chuyển tiền)
--

CREATE TABLE IF NOT EXISTS `transactions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `transaction_type` varchar(30) NOT NULL,
  `from_account` varchar(20) DEFAULT NULL,
  `to_account` varchar(20) DEFAULT NULL,
  `amount` double NOT NULL,
  `description` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
