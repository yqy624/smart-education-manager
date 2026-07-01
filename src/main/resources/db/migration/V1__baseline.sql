CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  display_name VARCHAR(50),
  email VARCHAR(100),
  role VARCHAR(20) NOT NULL,
  enabled BOOLEAN DEFAULT TRUE,
  created_at DATETIME,
  last_login DATETIME
);

CREATE TABLE IF NOT EXISTS courses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  teacher_id BIGINT NOT NULL,
  schedule VARCHAR(50),
  credits INT DEFAULT 3,
  max_students INT DEFAULT 50,
  enrolled_count INT DEFAULT 0,
  CONSTRAINT fk_courses_teacher FOREIGN KEY (teacher_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS enrollments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  enrolled_at DATETIME,
  score DOUBLE DEFAULT -1,
  CONSTRAINT fk_enroll_student FOREIGN KEY (student_id) REFERENCES users(id),
  CONSTRAINT fk_enroll_course FOREIGN KEY (course_id) REFERENCES courses(id)
);

CREATE TABLE IF NOT EXISTS assignments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  due_date DATETIME,
  created_at DATETIME,
  attachment_paths VARCHAR(500),
  total_points INT DEFAULT 100,
  CONSTRAINT fk_assign_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_assign_teacher FOREIGN KEY (teacher_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS submissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  assignment_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(20) DEFAULT 'PENDING',
  score DOUBLE,
  content TEXT,
  file_paths VARCHAR(500),
  file_name VARCHAR(50),
  teacher_comment TEXT,
  submitted_at DATETIME,
  graded_at DATETIME,
  CONSTRAINT fk_sub_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id),
  CONSTRAINT fk_sub_student FOREIGN KEY (student_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  recipient VARCHAR(50) NOT NULL,
  type VARCHAR(20),
  title VARCHAR(200) NOT NULL,
  content VARCHAR(500),
  link VARCHAR(100),
  category VARCHAR(50),
  is_read BOOLEAN DEFAULT FALSE,
  created_at DATETIME
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50),
  role VARCHAR(20),
  action VARCHAR(255) NOT NULL,
  details VARCHAR(500),
  ip_address VARCHAR(50),
  timestamp DATETIME
);
