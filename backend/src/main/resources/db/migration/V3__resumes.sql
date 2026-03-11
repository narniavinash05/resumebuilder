CREATE TABLE resumes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE resume_versions (
    id BIGSERIAL PRIMARY KEY,
    resume_id BIGINT REFERENCES resumes(id) ON DELETE CASCADE,
    job_description TEXT,
    resume_json JSONB,
    ats_score INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);