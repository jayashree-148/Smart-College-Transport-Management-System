-- Smart College Transportation Management - MySQL 8 schema
CREATE DATABASE IF NOT EXISTS college_transport_db;
USE college_transport_db;

CREATE TABLE IF NOT EXISTS pickup_stops (
    stop_id   VARCHAR(10)  PRIMARY KEY,
    stop_name VARCHAR(100) NOT NULL
);

-- Depot node used by the route graph
INSERT IGNORE INTO pickup_stops (stop_id, stop_name) VALUES ('CAMPUS', 'Kalvi College Campus (Depot)');

CREATE TABLE IF NOT EXISTS buses (
    bus_id   VARCHAR(10) PRIMARY KEY,
    bus_type ENUM('STANDARD','MINI') NOT NULL,
    capacity INT NOT NULL CHECK (capacity > 0),
    status   ENUM('AVAILABLE','BROKEN_DOWN') NOT NULL DEFAULT 'AVAILABLE'
);

CREATE TABLE IF NOT EXISTS road_links (
    link_id   INT AUTO_INCREMENT PRIMARY KEY,
    from_stop VARCHAR(10) NOT NULL,
    to_stop   VARCHAR(10) NOT NULL,
    minutes   INT NOT NULL CHECK (minutes > 0),
    narrow    TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (from_stop) REFERENCES pickup_stops(stop_id),
    FOREIGN KEY (to_stop)   REFERENCES pickup_stops(stop_id)
);

CREATE TABLE IF NOT EXISTS students (
    student_id VARCHAR(10)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    stop_id    VARCHAR(10)  NOT NULL,
    authorized TINYINT(1)   NOT NULL DEFAULT 1,
    FOREIGN KEY (stop_id) REFERENCES pickup_stops(stop_id)
);

CREATE TABLE IF NOT EXISTS attendance (
    student_id VARCHAR(10) NOT NULL,
    att_date   DATE        NOT NULL,
    status     ENUM('PRESENT','ABSENT') NOT NULL,
    PRIMARY KEY (student_id, att_date),
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE
);

-- Fees are kept apart from pickup planning (only an administrative report)
CREATE TABLE IF NOT EXISTS fee_records (
    student_id  VARCHAR(10) PRIMARY KEY,
    amount_due  INT NOT NULL,
    amount_paid INT NOT NULL DEFAULT 0,
    FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS daily_trips (
    trip_id       INT AUTO_INCREMENT PRIMARY KEY,
    trip_date     DATE NOT NULL,
    bus_id        VARCHAR(10) NOT NULL,
    route_text    VARCHAR(500) NOT NULL,
    total_minutes INT NOT NULL,
    trip_status   VARCHAR(20) NOT NULL,
    FOREIGN KEY (bus_id) REFERENCES buses(bus_id)
);

CREATE TABLE IF NOT EXISTS trip_assignments (
    trip_id     INT NOT NULL,
    student_id  VARCHAR(10) NOT NULL,
    stop_id     VARCHAR(10) NOT NULL,
    board_order INT NOT NULL,
    PRIMARY KEY (trip_id, student_id),
    FOREIGN KEY (trip_id)    REFERENCES daily_trips(trip_id) ON DELETE CASCADE,
    FOREIGN KEY (student_id) REFERENCES students(student_id),
    FOREIGN KEY (stop_id)    REFERENCES pickup_stops(stop_id)
);

CREATE TABLE IF NOT EXISTS event_log (
    event_id   INT AUTO_INCREMENT PRIMARY KEY,
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type VARCHAR(20) NOT NULL,
    details    VARCHAR(255) NOT NULL
);

CREATE OR REPLACE VIEW v_fee_report AS
SELECT s.student_id, s.name, f.amount_due, f.amount_paid,
       CASE WHEN f.amount_paid >= f.amount_due THEN 'PAID'
            WHEN f.amount_paid > 0 THEN 'PARTIAL' ELSE 'PENDING' END AS fee_status
FROM students s JOIN fee_records f ON f.student_id = s.student_id;
