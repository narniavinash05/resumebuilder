# ATS Resume Refactor Engine 🚀

A **Spring Boot–based resume optimization engine** that automatically refactors candidate resumes based on Job Description (JD) metadata to generate a **single-page, ATS-optimized, well-aligned resume**.

---

## 📌 Problem Statement

Tailoring resumes for every job application is:

* Time-consuming
* Repetitive
* Error-prone
* Often poorly optimized for ATS systems

This project eliminates manual effort by automatically generating JD-aligned, keyword-optimized resumes.

---

## 💡 Solution Overview

The application:

* Accepts **Candidate Metadata**
* Accepts **Job Description Metadata**
* Refactors experience bullet points dynamically
* Optimizes keyword density for ATS
* Produces a **clean, single-page formatted resume**
* Ensures structured, professional alignment

---

## ⚙️ Tech Stack

* **Java 17+**
* **Spring Boot**
* REST APIs
* JSON-based metadata input
* Modular architecture (scalable for LLM integration)

---

## 🏗 Architecture

```
Client (JSON Metadata)
        ↓
Resume Refactor Engine
        ↓
Keyword Optimization Layer
        ↓
Formatting Engine
        ↓
ATS-Optimized Single Page Resume Output
```

---

## 🔌 API Usage

### Endpoint

```
POST /api/refactor-resume
```

### Request Body (Sample)

```json
{
  "candidateMetaData": {
    "name": "John Doe",
    "experience": [...],
    "skills": [...],
    "education": [...]
  },
  "jobDescriptionMetaData": {
    "role": "Senior Java Developer",
    "requiredSkills": ["Spring Boot", "Microservices", "REST APIs"]
  }
}
```

### Response

* Fully refactored
* JD-aligned
* ATS-optimized
* Single-page structured resume

---

## 🚀 How To Run

```bash
git clone https://github.com/narniavinash05/resumebuilder
cd ats-resume-engine
mvn clean install
mvn spring-boot:run
```

Application runs at:

```
http://localhost:8080
```

---

## 🔮 Roadmap

* [ ] LLM Native Integration
* [ ] Resume Scoring Engine
* [ ] Frontend UI Dashboard
* [ ] Multi-format Export (PDF / DOCX)
* [ ] Resume Analytics

---

## 🎯 Key Benefits

* Saves hours of manual tailoring
* Improves ATS shortlisting probability
* Enables bulk job applications efficiently
* Reduces resume fatigue

---

## 📢 Future Enhancements

LLM-powered contextual rewriting and adaptive formatting intelligence are planned for the next phase.

Stay tuned.

---
