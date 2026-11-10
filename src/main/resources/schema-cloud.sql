-- Create/select the smart_complaint database in your cloud database console first.
-- Then run this file against that database.


CREATE TABLE IF NOT EXISTS users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, 
 student_id VARCHAR(40) NOT NULL UNIQUE, 
 full_name VARCHAR(100) NOT NULL,
 email VARCHAR(150) NOT NULL UNIQUE, 
 password_hash VARCHAR(255) NOT NULL, 
 role ENUM('USER','ADMIN','STAFF') NOT NULL DEFAULT 'USER',
 floor_name VARCHAR(40) DEFAULT '', 
 room_number VARCHAR(20) DEFAULT '', 
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS complaints (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, 
 public_id VARCHAR(30) NOT NULL UNIQUE, 
 user_id BIGINT NOT NULL,
 student_id VARCHAR(40) NOT NULL, 
 floor_name VARCHAR(40) NOT NULL, 
 room_number VARCHAR(20) NOT NULL,
 category VARCHAR(40) NOT NULL, 
 priority VARCHAR(20) NOT NULL, 
 description TEXT NOT NULL,
 predicted_category VARCHAR(40), 
 predicted_priority VARCHAR(20), 
 expected_days INT DEFAULT 3,
 status ENUM('Pending','Assigned','In Progress','Resolved','Rejected') NOT NULL DEFAULT 'Pending',
 assigned_staff_id BIGINT NULL, 
 resolution_note TEXT, 
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_complaint_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_complaint_staff FOREIGN KEY(assigned_staff_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS complaint_updates (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, 
 complaint_id BIGINT NOT NULL, 
 updated_by BIGINT NOT NULL,
 old_status VARCHAR(30), 
 new_status VARCHAR(30) NOT NULL, 
 note VARCHAR(500), 
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(complaint_id) REFERENCES complaints(id) ON DELETE CASCADE, 
 FOREIGN KEY(updated_by) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS feedback (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, 
 complaint_id BIGINT NOT NULL UNIQUE, 
 user_id BIGINT NOT NULL,
 rating INT NOT NULL, 
 comments VARCHAR(1000), 
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(complaint_id) REFERENCES complaints(id) ON DELETE CASCADE, 
 FOREIGN KEY(user_id) REFERENCES users(id)
);


