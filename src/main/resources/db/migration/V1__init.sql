-- ===================================================================
-- Flyway Database Migration: V1__init.sql
-- LifeLink: Blood Donation Platform Initial Schema
-- ===================================================================

-- 1. Users Table
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    user_type VARCHAR(20) NOT NULL,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    INDEX idx_users_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Donor Profiles Table (1:1 with users)
CREATE TABLE donor_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    blood_group VARCHAR(20) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(20) NOT NULL,
    available_to_donate BOOLEAN NOT NULL DEFAULT TRUE,
    last_donation_date DATE NULL,
    medical_conditions VARCHAR(1000) NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uq_donor_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_donor_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_donor_blood_group (blood_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Seeker Requests Table
CREATE TABLE seeker_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seeker_id BIGINT NOT NULL,
    blood_group VARCHAR(20) NOT NULL,
    units_needed INT NOT NULL,
    urgency VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NULL,
    hospital_name VARCHAR(150) NOT NULL,
    hospital_address VARCHAR(255) NOT NULL,
    required_by DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_seeker_requests_seeker FOREIGN KEY (seeker_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_requests_blood_group (blood_group),
    INDEX idx_requests_status (status),
    INDEX idx_requests_bg_status (blood_group, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Matches Table
CREATE TABLE matches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    donor_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    contact_shared BOOLEAN NOT NULL DEFAULT FALSE,
    matched_at DATETIME NOT NULL,
    CONSTRAINT uq_matches_request_donor UNIQUE (request_id, donor_id),
    CONSTRAINT fk_matches_request FOREIGN KEY (request_id) REFERENCES seeker_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_matches_donor FOREIGN KEY (donor_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_matches_status (status),
    INDEX idx_matches_req_donor (request_id, donor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
