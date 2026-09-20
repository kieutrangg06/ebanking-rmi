-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Máy chủ: 127.0.0.1
-- Thời gian đã tạo: Th9 20, 2026 lúc 02:19 PM
-- Phiên bản máy phục vụ: 10.4.32-MariaDB
-- Phiên bản PHP: 8.0.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Cơ sở dữ liệu: `ebanking_db`
--

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `accounts`
--

CREATE TABLE `accounts` (
  `id` int(11) NOT NULL,
  `account_number` varchar(20) NOT NULL,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `balance` double NOT NULL DEFAULT 0,
  `status` varchar(20) DEFAULT 'ACTIVE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `accounts`
--

INSERT INTO `accounts` (`id`, `account_number`, `username`, `password`, `full_name`, `balance`, `status`) VALUES
(3, '1001', 'usera', '123456', 'Nguyen Van A', 3500000, 'ACTIVE'),
(4, '1002', 'userb', '123456', 'Tran Thi B', 1650000, 'LOCKED'),
(5, '1003', 'userc', '123456', 'Le Van C', 2000000, 'ACTIVE');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `bills`
--

CREATE TABLE `bills` (
  `id` int(11) NOT NULL,
  `bill_code` varchar(50) NOT NULL,
  `service_type` varchar(50) NOT NULL,
  `customer_name` varchar(100) NOT NULL,
  `amount` double NOT NULL,
  `status` varchar(20) DEFAULT 'UNPAID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `bills`
--

INSERT INTO `bills` (`id`, `bill_code`, `service_type`, `customer_name`, `amount`, `status`) VALUES
(5, 'EVN_HANOI_01', 'Tien Dien', 'Nguyen Van A', 350000, 'PAID'),
(6, 'WA_DANANG_02', 'Tien Nuoc', 'Tran Thi B', 120000, 'UNPAID'),
(7, 'FPT_NET_03', 'Internet FPT', 'Le Van C', 250000, 'UNPAID');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `savings`
--

CREATE TABLE `savings` (
  `id` int(11) NOT NULL,
  `account_number` varchar(20) NOT NULL,
  `deposit_amount` double NOT NULL,
  `interest_rate` double NOT NULL,
  `term_period` int(11) NOT NULL,
  `accumulated_interest` double DEFAULT 0,
  `status` varchar(20) DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `savings`
--

INSERT INTO `savings` (`id`, `account_number`, `deposit_amount`, `interest_rate`, `term_period`, `accumulated_interest`, `status`, `created_at`) VALUES
(1, '1002', 500000, 5, 15, 150000, 'ACTIVE', '2026-09-19 14:42:19');

-- --------------------------------------------------------

--
-- Cấu trúc bảng cho bảng `transactions`
--

CREATE TABLE `transactions` (
  `id` int(11) NOT NULL,
  `transaction_type` varchar(30) NOT NULL,
  `from_account` varchar(20) DEFAULT NULL,
  `to_account` varchar(20) DEFAULT NULL,
  `amount` double NOT NULL,
  `description` text DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Đang đổ dữ liệu cho bảng `transactions`
--

INSERT INTO `transactions` (`id`, `transaction_type`, `from_account`, `to_account`, `amount`, `description`, `created_at`) VALUES
(1, 'CHUYEN_TIEN', '1001', '1002', 1500000, 'Chuyen tien', '2026-09-19 14:40:50'),
(2, 'THANH_TOAN_HOA_DON', '1002', 'EVN_HANOI_01', 350000, 'Thanh toan hoa don: Tien Dien (Nguyen Van A)', '2026-09-19 14:41:26');

--
-- Chỉ mục cho các bảng đã đổ
--

--
-- Chỉ mục cho bảng `accounts`
--
ALTER TABLE `accounts`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `account_number` (`account_number`),
  ADD UNIQUE KEY `username` (`username`);

--
-- Chỉ mục cho bảng `bills`
--
ALTER TABLE `bills`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `bill_code` (`bill_code`);

--
-- Chỉ mục cho bảng `savings`
--
ALTER TABLE `savings`
  ADD PRIMARY KEY (`id`),
  ADD KEY `account_number` (`account_number`);

--
-- Chỉ mục cho bảng `transactions`
--
ALTER TABLE `transactions`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT cho các bảng đã đổ
--

--
-- AUTO_INCREMENT cho bảng `accounts`
--
ALTER TABLE `accounts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

--
-- AUTO_INCREMENT cho bảng `bills`
--
ALTER TABLE `bills`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

--
-- AUTO_INCREMENT cho bảng `savings`
--
ALTER TABLE `savings`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT cho bảng `transactions`
--
ALTER TABLE `transactions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- Các ràng buộc cho các bảng đã đổ
--

--
-- Các ràng buộc cho bảng `savings`
--
ALTER TABLE `savings`
  ADD CONSTRAINT `savings_ibfk_1` FOREIGN KEY (`account_number`) REFERENCES `accounts` (`account_number`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
