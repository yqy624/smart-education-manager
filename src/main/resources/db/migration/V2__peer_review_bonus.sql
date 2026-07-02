ALTER TABLE assignments
  ADD COLUMN peer_review_enabled BOOLEAN DEFAULT FALSE,
  ADD COLUMN peer_review_open_at DATETIME NULL,
  ADD COLUMN peer_review_close_at DATETIME NULL,
  ADD COLUMN peer_review_required_count INT DEFAULT 1,
  ADD COLUMN peer_review_bonus_per_review DOUBLE DEFAULT 1,
  ADD COLUMN peer_review_bonus_cap DOUBLE DEFAULT 1,
  ADD COLUMN peer_review_prompt TEXT NULL;

ALTER TABLE enrollments
  ADD COLUMN base_score DOUBLE DEFAULT -1,
  ADD COLUMN peer_review_bonus DOUBLE DEFAULT 0;

CREATE TABLE IF NOT EXISTS peer_reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  assignment_id BIGINT NOT NULL,
  reviewer_id BIGINT NOT NULL,
  target_submission_id BIGINT NOT NULL,
  status VARCHAR(20) DEFAULT 'ASSIGNED',
  rating INT,
  comment TEXT,
  assigned_at DATETIME,
  submitted_at DATETIME,
  bonus_granted_at DATETIME,
  CONSTRAINT fk_peer_review_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id),
  CONSTRAINT fk_peer_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(id),
  CONSTRAINT fk_peer_review_target_submission FOREIGN KEY (target_submission_id) REFERENCES submissions(id),
  CONSTRAINT uk_peer_review_unique UNIQUE (assignment_id, reviewer_id, target_submission_id)
);
