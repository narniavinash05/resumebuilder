# 🤖 RésuméAI — AI-Powered ATS Resume Optimization Engine

RésuméAI is a full-stack AI platform that generates **ATS-optimized resumes tailored to job descriptions** using **Spring Boot, React, OpenAI, and PostgreSQL**.

The system can:

- Parse uploaded resumes into structured profile data
- Tailor resumes to job descriptions using AI
- Generate ATS compatibility scores
- Export professional PDF resumes
- Track resume versions per job application

---

# 🧱 Tech Stack

| Layer | Technology |
|------|------------|
| Backend | Java 17, Spring Boot |
| AI / LLM | OpenAI Chat API |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Security | Spring Security + JWT |
| Email | SendGrid |
| PDF Generation | OpenPDF |
| Resume Parsing | Apache PDFBox + Apache POI |
| HTTP Client | Spring WebClient |
| Frontend | React |

---

# 🏗 System Architecture

```
React Frontend
│
│ JWT Bearer Token
▼
Spring Boot API
│
├── Authentication Layer
│   └── JWT + PostgreSQL
│
├── Profile Service
│   └── Stores structured resume data
│
├── Resume Parsing Service
│   └── Extract text from PDF/DOCX → LLM
│
├── Resume Tailoring Service
│   └── LLM generates ATS-optimized resume
│
├── ATS Score Service
│   └── Maps LLM scoring results
│
├── Resume PDF Service
│   └── Generates professional PDF
│
└── Resume Version Service
    └── Stores resume history

Database: PostgreSQL

External Services:
- OpenAI API
- SendGrid Email
```

---

# 🔌 API Endpoints

## Authentication

| Method | Endpoint | Description |
|------|----------|-------------|
| POST | `/api/auth/signup` | Register user |
| GET | `/api/auth/verify` | Verify email via token |
| POST | `/api/auth/login` | Login and receive JWT |
| POST | `/api/auth/forgot-password` | Request password reset |
| POST | `/api/auth/reset-password` | Reset password |

---

## Profile Management

| Method | Endpoint | Description |
|------|----------|-------------|
| GET | `/api/auth/profile` | Fetch profile JSON |
| POST | `/api/auth/profile` | Save profile JSON |

Profile data is stored in **JSONB format in PostgreSQL**.

---

## Resume Features

| Method | Endpoint | Description |
|------|----------|-------------|
| POST | `/api/resume/parse` | Upload resume and auto-extract profile data |
| POST | `/api/resume/generate` | Generate resume PDF |
| POST | `/api/resume/tailor-and-generate` | AI tailor resume to job description |
| POST | `/api/resume/tailor-generate-score` | Tailor resume + ATS score + PDF |

---

# 🧠 Core Features

## AI Resume Tailoring

The system sends the candidate profile and job description to the LLM.

The AI:

1. Extracts ATS keywords from the job description  
2. Rewrites the resume using those keywords  
3. Improves bullet points with quantified impact  
4. Produces a structured resume JSON  

The JSON resume is then rendered into a professional PDF.

---

## ATS Scoring

The LLM evaluates resume compatibility with the job description.

Scoring dimensions:

| Metric | Weight |
|------|------|
| Keyword Match | 40% |
| Candidate Fit | 25% |
| Resume Completeness | 20% |
| Keyword Density | 15% |

Returned fields:

```
atsScore
scoreLabel
matchedKeywords
missingKeywords
scoringBreakdown
```

---

## Resume Parsing (Auto-Fill)

Users can upload an existing resume.

Supported formats:

- PDF
- DOCX
- DOC
- TXT

Process:

```
Upload File
↓
Extract Text (PDFBox / POI)
↓
LLM Resume Parser
↓
Structured JSON Profile
```

The frontend can use this data to **auto-fill profile fields**.

---

## Resume Version Tracking

Each tailored resume is saved with:

- Job description  
- ATS score  
- Resume JSON  
- Timestamp  

Stored in table:

```
resume_versions
```

This allows tracking **resume history per job application**.

---

# 🗄 Database Schema

## Users

Table: `users`

| Column | Type |
|------|------|
| id | BIGSERIAL |
| email | VARCHAR |
| password | VARCHAR |
| full_name | VARCHAR |
| email_verified | BOOLEAN |
| verification_token | VARCHAR |
| role | VARCHAR |
| created_at | TIMESTAMP |

---

## User Profiles

Table: `user_profiles`

| Column | Type |
|------|------|
| id | BIGSERIAL |
| user_id | BIGINT |
| profile_json | JSONB |
| profile_complete | BOOLEAN |
| updated_at | TIMESTAMP |

---

## Resumes

Table: `resumes`

| Column | Type |
|------|------|
| id | BIGSERIAL |
| user_id | BIGINT |
| title | VARCHAR |
| created_at | TIMESTAMP |

---

## Resume Versions

Table: `resume_versions`

| Column | Type |
|------|------|
| id | BIGSERIAL |
| resume_id | BIGINT |
| resume_json | JSONB |
| job_description | TEXT |
| ats_score | INT |
| created_at | TIMESTAMP |

---

# ✉ Email System

Email functionality uses **SendGrid API**.

Used for:

- Email verification
- Password reset

Required configuration:

```
sendgrid.api.key
sendgrid.from.email
```

---

# 🤖 LLM Integration

OpenAI is used for two major functions.

### Resume Parsing

Extracts structured fields:

- name
- contact info
- experience
- education
- skills
- certifications

### Resume Tailoring

Generates:

- ATS-optimized resume
- keyword placement
- scoring breakdown

---

# ⚙️ Configuration

Create:

```
src/main/resources/application.properties
```

Example configuration:

```
server.port=8080

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/resumebuilder
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.hibernate.ddl-auto=validate

# JWT
jwt.secret=YOUR_SECRET
jwt.expiration=86400000

# OpenAI
openai.api-key=YOUR_API_KEY
openai.url=https://api.openai.com/v1/chat/completions
openai.model=gpt-4o-mini

# SendGrid
sendgrid.api.key=YOUR_SENDGRID_KEY
sendgrid.from.email=no-reply@yourdomain.com

# App URLs
app.base-url=http://localhost:3000
app.backend-url=http://localhost:8080
```

---

# 🚀 Running Locally

## 1️⃣ Start PostgreSQL

```
docker run -p 5432:5432 \
-e POSTGRES_DB=resumebuilder \
-e POSTGRES_USER=postgres \
-e POSTGRES_PASSWORD=postgres \
postgres
```

---

## 2️⃣ Run Backend

```
mvn clean install
mvn spring-boot:run
```

Backend runs at:

```
http://localhost:8080
```

---

## 3️⃣ Run Frontend

```
cd frontend
npm install
npm start
```

Frontend runs at:

```
http://localhost:3000
```

---

# 📁 Project Structure

```
com.resumebuilder
│
├── config
│   ├── SecurityConfig
│   └── JacksonConfig
│
├── controller
│   ├── AuthController
│   └── ResumeController
│
├── dto
│
├── llm
│   ├── LlmClient
│   ├── LlmConfig
│   └── PromptBuilder
│
├── model
│
├── repository
│
├── security
│   ├── JwtAuthFilter
│   └── JwtUtil
│
├── service
│   ├── AuthService
│   ├── EmailService
│   ├── ResumeParserService
│   ├── ResumePdfService
│   ├── ResumeTailoringService
│   ├── ResumeVersionService
│   └── AtsScoreService
```

---

# 👤 Author

**Avinash Narni**  
Dallas, Texas  
Software Engineer
