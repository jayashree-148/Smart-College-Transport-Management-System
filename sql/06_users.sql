-- Login accounts for the web dashboard (passwords are stored as salted PBKDF2 hashes, never as text).
-- The Java server also creates this table automatically and adds the first account: admin / Admin@123
USE college_transport_db;

CREATE TABLE IF NOT EXISTS app_users (
    user_id         VARCHAR(20)  PRIMARY KEY,
    full_name       VARCHAR(60)  NOT NULL,
    role            ENUM('ADMIN','STAFF','VIEWER') NOT NULL DEFAULT 'VIEWER',
    password_hash   VARCHAR(200) NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
