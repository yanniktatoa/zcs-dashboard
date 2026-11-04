CREATE TABLE loans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    book_id BIGINT,
    loan_date DATE,
    return_date DATE,
    status VARCHAR(255)
);
