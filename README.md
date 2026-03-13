# 🤖 RésuméAI — AI-Powered Resume Optimization Platform

> Generate **ATS-optimized resumes tailored to job descriptions** using AI — built with Spring Boot, React, OpenAI, and PostgreSQL.

🌐 **Live Demo:** [app.resumebuild.it.com](https://app.resumebuild.it.com) &nbsp;|&nbsp; 📡 **API:** [api.resumebuild.it.com](https://api.resumebuild.it.com)

---

## ✨ Features

- 📄 **Resume Parsing** — Upload PDF/DOCX and auto-extract structured profile data
- 🧠 **AI Resume Tailoring** — Rewrites your resume to match a job description using LLM
- 📊 **ATS Scoring** — Evaluates keyword match, candidate fit, and completeness
- 📑 **PDF Export** — Generates professional, clean PDF resumes
- 🗂 **Version Tracking** — Stores resume history per job application

---

## 🧱 Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot |
| AI / LLM | OpenAI Chat API |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + JWT |
| Email | SendGrid |
| PDF Generation | OpenPDF |
| Resume Parsing | Apache PDFBox + Apache POI |
| HTTP Client | Spring WebClient |
| Frontend | React |

---

## 🏗 System Architecture

```
React Frontend
│
│  JWT Bearer Token
▼
Spring Boot API
│
├── Authentication Layer       → JWT + PostgreSQL
├── Profile Service            → Stores structured resume data
├── Resume Parsing Service     → Extract text from PDF/DOCX → LLM
├── Resume Tailoring Service   → LLM generates ATS-optimized resume
├── ATS Score Service          → Maps LLM scoring results
├── Resume PDF Service         → Generates professional PDF
└── Resume Version Service     → Stores resume history

Database: PostgreSQL
External: OpenAI API, SendGrid
```

---

## ☁️ Production Deployment

### Infrastructure Overview

| Component | Purpose |
|---|---|
| EC2 | Spring Boot API server |
| S3 | React static hosting |
| CloudFront | CDN + HTTPS |
| Route 53 | DNS |
| Nginx | Reverse proxy |
| Let's Encrypt | SSL certificates |
| Systemd | Backend service management |

### Deployment Flow

```
Frontend:   React → S3 → CloudFront CDN → app.resumebuild.it.com
Backend:    Spring Boot → EC2 → Nginx → api.resumebuild.it.com
Database:   PostgreSQL on EC2
Email:      SendGrid API
```

---

## 🔌 API Reference

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/signup` | Register user |
| `GET` | `/api/auth/verify` | Verify email via token |
| `POST` | `/api/auth/login` | Login and receive JWT |
| `POST` | `/api/auth/forgot-password` | Request password reset |
| `POST` | `/api/auth/reset-password` | Reset password |

### Profile Management

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/auth/profile` | Fetch profile JSON |
| `POST` | `/api/auth/profile` | Save profile JSON |

> Profile data is stored in **JSONB format** in PostgreSQL.

### Resume Features

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/resume/parse` | Upload resume and auto-extract profile data |
| `POST` | `/api/resume/generate` | Generate resume PDF |
| `POST` | `/api/resume/tailor-and-generate` | AI tailor resume to job description |
| `POST` | `/api/resume/tailor-generate-score` | Tailor + ATS score + PDF in one call |

---

## 🧠 Core Features

### AI Resume Tailoring

Sends candidate profile + job description to the LLM, which:

1. Extracts ATS keywords from the job description
2. Rewrites resume bullet points with quantified impact
3. Aligns experience with job requirements
4. Returns structured resume JSON → rendered to professional PDF

### ATS Scoring

| Metric | Weight |
|---|---|
| Keyword Match | 40% |
| Candidate Fit | 25% |
| Resume Completeness | 20% |
| Keyword Density | 15% |

**Response fields:** `atsScore`, `scoreLabel`, `matchedKeywords`, `missingKeywords`, `scoringBreakdown`

### Resume Parsing (Auto-Fill)

Supported formats: **PDF, DOCX, DOC, TXT**

```
Upload File → Extract Text (PDFBox / POI) → LLM Parser → Structured JSON Profile
```

The frontend uses this data to auto-fill all profile fields.

### Resume Version Tracking

Each tailored resume is saved with: job description, ATS score, resume JSON, and timestamp — stored in the `resume_versions` table for full application history.

---

## 🗄 Database Schema

### `users`

| Column | Type |
|---|---|
| id | BIGSERIAL |
| email | VARCHAR |
| password | VARCHAR |
| full_name | VARCHAR |
| email_verified | BOOLEAN |
| verification_token | VARCHAR |
| role | VARCHAR |
| created_at | TIMESTAMP |

### `user_profiles`

| Column | Type |
|---|---|
| id | BIGSERIAL |
| user_id | BIGINT |
| profile_json | JSONB |
| profile_complete | BOOLEAN |
| updated_at | TIMESTAMP |

### `resumes`

| Column | Type |
|---|---|
| id | BIGSERIAL |
| user_id | BIGINT |
| title | VARCHAR |
| created_at | TIMESTAMP |

### `resume_versions`

| Column | Type |
|---|---|
| id | BIGSERIAL |
| resume_id | BIGINT |
| resume_json | JSONB |
| job_description | TEXT |
| ats_score | INT |
| created_at | TIMESTAMP |

---

## 🔐 Security

- **JWT** — Stateless authentication with token expiration enforcement
- **BCrypt** — Password hashing via `BCryptPasswordEncoder`
- **Email Verification** — Required before accessing protected features
- **Spring Security** — Protects all routes except `/api/auth/**` and `/health`
- **CORS** — Restricted to `https://app.resumebuild.it.com` and `http://localhost:3000`

All protected endpoints require:
```
Authorization: Bearer <JWT>
```

---

## ✉️ Email System

Uses **SendGrid API** for email verification and password reset flows.

Required config:
```properties
sendgrid.api.key=YOUR_KEY
sendgrid.from.email=no-reply@yourdomain.com
```

---

## 🧠 Prompt Engineering

### Resume Parsing Prompt

The LLM extracts structured JSON from raw resume text:

```json
{
  "name": "",
  "email": "",
  "skills": [],
  "experience": [],
  "education": []
}
```

### Resume Tailoring Prompt

Instructs the model to extract job keywords, rewrite bullet points with impact metrics, align experience with the job description, and optimize for ATS scanning.

---

## ⚙️ Configuration

Create `src/main/resources/application.properties`:

```properties
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

### Environment Variables

Store sensitive credentials as environment variables:

```
DB_URL
DB_USER
DB_PASSWORD
JWT_SECRET
OPENAI_API_KEY
SENDGRID_API_KEY
```

---

## 🚀 Running Locally

### 1. Start PostgreSQL

```bash
docker run -p 5432:5432 \
  -e POSTGRES_DB=resumebuilder \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres
```

### 2. Run Backend

```bash
mvn clean install
mvn spring-boot:run
```

> Runs at `http://localhost:8080`

### 3. Run Frontend

```bash
cd frontend
npm install
npm start
```

> Runs at `http://localhost:3000`

---

## 📁 Project Structure

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
├── repository
│
├── security
│   ├── JwtAuthFilter
│   └── JwtUtil
│
└── service
    ├── AuthService
    ├── EmailService
    ├── ResumeParserService
    ├── ResumePdfService
    ├── ResumeTailoringService
    ├── ResumeVersionService
    └── AtsScoreService
```

---

## 📈 Scalability Roadmap

**Infrastructure improvements:**
- Migrate PostgreSQL to **AWS RDS**
- Add **Redis** for caching
- Introduce a **load balancer**
- Build out a **CI/CD pipeline**
- Enable **async LLM request processing**
- Add **API rate limiting**

**Feature roadmap:**
- Resume templates
- Job tracking dashboard
- LinkedIn profile import
- AI cover letter generator
- Resume keyword analytics
- Chrome extension for job boards

---

## 👤 Author

**Avinash Narni**
Dallas, Texas — Software Engineer
