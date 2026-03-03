# 🤖 RésuméAI — ATS Resume Optimization Engine

A full-stack **AI-powered resume builder** that generates JD-aligned, ATS-optimized resumes using Spring Boot, React, and OpenAI.

---

## 🧱 Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2 |
| AI / LLM | OpenAI GPT-4o-mini |
| PDF Generation | OpenPDF (LibrePDF) |
| Authentication | Spring Security + JWT |
| Database | H2 (file-based, persists across restarts) |
| Email | Spring Mail (Gmail SMTP) |
| HTTP Client | WebClient (Reactive) |
| Frontend | React 18, CRA |

---

## 🏗 Architecture

```
React Frontend (localhost:3000)
        ↓  JWT Bearer Token
Spring Boot Backend (localhost:8080)
        ↓
   ┌────────────────────────────────┐
   │  Auth Layer (JWT + H2 DB)      │
   │  User → Profile → Resume       │
   └────────────────────────────────┘
        ↓
   Prompt Builder (resume-tailor-prompt.txt)
        ↓
   OpenAI GPT-4o-mini
        ↓
   JSON Validator + Resume Model Mapper
        ↓
   OpenPDF Rendering Engine
        ↓
   ATS-Optimized PDF Resume
```

---

## 🔌 API Endpoints

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/signup` | ❌ | Register new user |
| GET | `/api/auth/verify` | ❌ | Verify email via token |
| POST | `/api/auth/login` | ❌ | Login, returns JWT |
| GET | `/api/auth/profile` | ✅ | Fetch saved profile |
| POST | `/api/auth/profile` | ✅ | Save profile JSON |
| POST | `/api/auth/ats-score` | ✅ | Calculate ATS keyword score |
| POST | `/api/resume/tailor-and-generate` | ✅ | AI tailor + download PDF |
| POST | `/api/resume/generate` | ✅ | Generate PDF (no AI) |

---

## 🧠 Core Features

**AI Resume Tailoring**
- Extracts all technical keywords from job description
- Forces keyword distribution across Summary, Experience, and Skills
- Rewrites experience bullets with measurable impact
- Normalizes terminology: RDBMS, NoSQL, Cloud technologies, CI/CD pipelines
- Target ATS match: 90–95%

**ATS Scoring Engine**
- Matches candidate skills against JD keywords
- Returns score, matched keywords, and missing keyword suggestions
- Color-coded result: Excellent / Good / Needs Improvement

**Auth & User Management**
- JWT-based stateless authentication
- Email verification on signup (Gmail SMTP)
- Per-user profile storage in H2 database
- Profile builder with 5-step breadcrumb flow

**PDF Generation**
- Professional layout with right-aligned dates
- Lato font typography
- Sections: Summary, Experience, Skills, Education, Certifications
- Clickable hyperlinks for LinkedIn, Portfolio, Certificates

---

## 🚀 Running Locally

### Prerequisites
- Java 17+
- Maven 3.8+
- Node.js 16+

### Backend

```bash
cd backend
mvn clean install
mvn spring-boot:run
# Runs at http://localhost:8080
```

### Frontend

```bash
cd frontend
npm install
npm start
# Runs at http://localhost:3000
```

---

## ⚙️ Configuration

Create `src/main/resources/application.properties` (not committed — see `.gitignore`):

```properties
spring.application.name=resumebuilder
spring.main.allow-circular-references=true
server.port=8080

# H2 File Database (persists across restarts)
spring.datasource.url=jdbc:h2:file:./data/resumedb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true

# JWT
jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
jwt.expiration=86400000

# OpenAI
openai.api-key=YOUR_OPENAI_API_KEY
openai.url=https://api.openai.com/v1/chat/completions
openai.model=gpt-4o-mini

# Gmail SMTP
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=YOUR_GMAIL
spring.mail.password=YOUR_GMAIL_APP_PASSWORD
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# App URLs
app.base-url=http://localhost:3000
app.backend-url=http://localhost:8080
```

> 💡 If Gmail isn't configured, the verification token prints to the IntelliJ console automatically.

---

## 📁 Project Structure

```
resumebuilder/
├── backend/
│   └── src/main/java/com/resumebuilder/
│       ├── config/          → SecurityConfig, JacksonConfig
│       ├── controller/      → AuthController, ResumeController
│       ├── dto/             → AuthDtos (request/response objects)
│       ├── llm/             → LlmClient, LlmConfig, PromptBuilder
│       ├── model/           → User, UserProfile, Resume, Experience...
│       ├── repository/      → UserRepository, UserProfileRepository
│       ├── security/        → JwtUtil, JwtAuthFilter
│       └── service/         → AuthService, EmailService, AtsScoreService,
│                              ResumeTailoringService, ResumePdfService
│   └── src/main/resources/
│       ├── prompts/resume-tailor-prompt.txt
│       └── fonts/Lato-Regular.ttf, Lato-Bold.ttf
│
└── frontend/
    └── src/
        └── App.js           → Complete React SPA (auth + profile + generator)
```

---

## 🔐 Security Notes

- `application.properties` is excluded from git via `.gitignore`
- Never commit API keys — use IntelliJ **Run Configurations → Environment Variables**
- JWT tokens expire after 24 hours

---

## 👤 Author

Avinash Narni — Dallas, TX
