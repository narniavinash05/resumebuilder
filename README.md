# 🚀 AI Resume Builder -- ATS Optimization Engine

An enterprise-grade **Spring Boot AI Resume Optimization Engine** that
automatically generates **JD-aligned, ATS-optimized, single-page
professional resumes** using structured metadata and LLM-based
tailoring.

------------------------------------------------------------------------

# 📌 Problem

Manually tailoring resumes for every job application is:

-   Time-consuming\
-   Repetitive\
-   Prone to keyword mismatch\
-   Inefficient for ATS systems

Modern Applicant Tracking Systems rely heavily on **literal keyword
matching**, not semantic similarity.

------------------------------------------------------------------------

# 💡 Solution

This engine:

✔ Accepts structured **candidate metadata (JSON)**\
✔ Accepts full **Job Description (JD)**\
✔ Extracts JD keywords\
✔ Rewrites experience bullets intelligently\
✔ Optimizes keyword placement for ATS\
✔ Normalizes cloud/database terminology\
✔ Validates and parses LLM JSON safely\
✔ Generates a clean, professional **PDF resume**

Result:\
**Interview-ready, ATS-optimized resume in seconds.**

------------------------------------------------------------------------

# 🏗 System Architecture

Client (Resume + JD JSON)\
↓\
Prompt Builder (Template Engine)\
↓\
LLM Integration Layer\
↓\
JSON Validation & Recovery\
↓\
Resume Model Mapping\
↓\
PDF Rendering Engine\
↓\
ATS-Optimized Resume Output

------------------------------------------------------------------------

# ⚙️ Tech Stack

-   Java 17+\
-   Spring Boot\
-   WebClient (Reactive HTTP)\
-   Jackson (JSON serialization)\
-   iText (PDF generation)\
-   External Prompt Templates\
-   OpenAI-compatible LLM API

------------------------------------------------------------------------

# 🧠 Core Engine Features

## Universal ATS Optimization

-   Extracts all technical keywords from JD\
-   Forces distribution across Summary, Experience, and Skills\
-   Emphasizes architecture decisions, scalability, API design\
-   Normalizes terminology (RDBMS, NoSQL, Cloud technologies, CI/CD
    pipelines)

Target ATS Match: **90--95% (realistic range)**

------------------------------------------------------------------------

## Safe LLM Parsing & Recovery

Handles:

-   Wrapped JSON\
-   Malformed output\
-   Missing fields\
-   Empty responses

Includes JSON extraction fallback and validation layer.

------------------------------------------------------------------------

## Clean PDF Layout Engine

-   Proper right-aligned dates & locations\
-   Professional typography\
-   Sectioned layout\
-   Single-page formatting

------------------------------------------------------------------------

## Prompt Template Externalization

Prompt stored in:

src/main/resources/prompts/resume-tailor-prompt.txt

Benefits:

-   Easy tuning\
-   Versioning support\
-   Domain-agnostic design\
-   Enterprise-level flexibility

------------------------------------------------------------------------

# 🔌 API Endpoints

## Tailor Resume

POST /api/resume/tailor

### Request Body

{ "resumeMetaData": { ... }, "jobDescription": "Full job description
text here..." }

------------------------------------------------------------------------

## Generate PDF

POST /api/resume/pdf

Returns a professionally formatted PDF resume.

------------------------------------------------------------------------

# 🚀 Running the Application

Clone Repository:

git clone https://github.com/narniavinash05/resumebuilder\
cd resumebuilder

Build:

mvn clean install

Run:

mvn spring-boot:run

Application runs at:

http://localhost:8080

------------------------------------------------------------------------

# 🔐 Environment Variables

Add to application.properties:

openai.api-key=YOUR_API_KEY\
openai.url=https://api.openai.com/v1/chat/completions\
openai.model=gpt-4o-mini

------------------------------------------------------------------------

# 📈 Future Enhancements

-   Automated ATS scoring engine\
-   Keyword-gap detection + re-prompt loop\
-   Multi-pass LLM optimization\
-   Resume analytics dashboard\
-   Multi-format export (DOCX)\
-   SaaS deployment architecture

------------------------------------------------------------------------

# 🎯 Why This Project Matters

This is not just a resume formatter.

It is an AI-driven resume alignment engine engineered for ATS dominance.

------------------------------------------------------------------------

# 👤 Author

Avinash Narni\
Dallas, TX