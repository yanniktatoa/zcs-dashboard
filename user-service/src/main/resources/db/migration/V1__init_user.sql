CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    CONSTRAINT uk_users_email UNIQUE (email)
);
