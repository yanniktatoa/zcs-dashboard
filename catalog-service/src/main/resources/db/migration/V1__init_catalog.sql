CREATE TABLE books (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),
    author VARCHAR(255),
    category VARCHAR(255),
    available_stock INT,
    version BIGINT NOT NULL DEFAULT 0
);
