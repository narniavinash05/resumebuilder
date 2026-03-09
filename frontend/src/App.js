import { useState, useEffect } from "react";

// ─── API Layer ────────────────────────────────────────────────────────────────
const BASE_URL = "http://localhost:8080";

const getToken = () => localStorage.getItem("ats_token");

const apiFetch = async (path, options = {}) => {
  const token = getToken();

  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    },
  });

  // Handle JWT expiration
  if (res.status === 401) {
    localStorage.removeItem("ats_token");
    localStorage.removeItem("ats_user");
    window.location.href = "/";
    return;
  }

  const data = await res.json();
  if (!res.ok) throw new Error(data.message || "Request failed");
  return data;
};

const api = {
  signup: (fullName, email, password) =>
    apiFetch("/api/auth/signup", { method: "POST", body: JSON.stringify({ fullName, email, password }) }),
  login: (email, password) =>
    apiFetch("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  getProfile: () => apiFetch("/api/auth/profile"),
  saveProfile: (profileJson) =>
    apiFetch("/api/auth/profile", { method: "POST", body: JSON.stringify({ profileJson }) }),

  // ── Single unified call: AI extracts JD keywords, builds resume,
  //    self-audits keyword coverage, and returns ATS score + PDF together.
  //    No separate getAtsScore call needed anymore.
  generateResumeWithScore: (resumeMetaData, jobDescription) =>
    apiFetch("/api/resume/tailor-generate-score", {
      method: "POST",
      body: JSON.stringify({ resumeMetaData, jobDescription }),
    }),
};

// ─── Month/Year Dropdown Helpers ──────────────────────────────────────────────
const MONTHS = [
  { value: "01", label: "January" },  { value: "02", label: "February" },
  { value: "03", label: "March" },    { value: "04", label: "April" },
  { value: "05", label: "May" },      { value: "06", label: "June" },
  { value: "07", label: "July" },     { value: "08", label: "August" },
  { value: "09", label: "September" },{ value: "10", label: "October" },
  { value: "11", label: "November" }, { value: "12", label: "December" },
];

const YEARS = (() => {
  const current = new Date().getFullYear();
  const arr = [];
  for (let y = current; y >= 1980; y--) arr.push(String(y));
  return arr;
})();

function formatMonthYear(value) {
  if (!value) return "";

  const [year, month] = value.split("-");

  const map = {
    "01":"Jan","02":"Feb","03":"Mar","04":"Apr",
    "05":"May","06":"Jun","07":"Jul","08":"Aug",
    "09":"Sep","10":"Oct","11":"Nov","12":"Dec"
  };

  return `${map[month]}, ${year}`;
}

// Stores value as "YYYY-MM"
function MonthYearPicker({ value, onChange, disabled }) {
  const [year, setYear] = useState("");
  const [month, setMonth] = useState("");

  useEffect(() => {
    if (value && value.includes("-")) {
      const [y, m] = value.split("-");
      setYear(y || "");
      setMonth(m || "");
    } else {
      setYear("");
      setMonth("");
    }
  }, [value]);

  const handleYearChange = (newYear) => {
    setYear(newYear);
    onChange(newYear && month ? `${newYear}-${month}` : "");
  };

  const handleMonthChange = (newMonth) => {
    setMonth(newMonth);
    onChange(year && newMonth ? `${year}-${newMonth}` : "");
  };

  const selectStyle = {
    width: "100%",
    padding: "14px 12px",
    background: disabled ? "rgba(255,255,255,0.03)" : "var(--surface)",
    border: "1px solid var(--border)",
    borderRadius: "var(--radius)",
    color: disabled ? "var(--muted)" : "var(--text)",
    fontFamily: "var(--font-body)",
    fontSize: 14,
    outline: "none",
    opacity: disabled ? 0.5 : 1,
    cursor: disabled ? "not-allowed" : "pointer",
  };

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
      <select value={month} onChange={(e) => handleMonthChange(e.target.value)} disabled={disabled} style={selectStyle}>
        <option value="">Month</option>
        {MONTHS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
      </select>
      <select value={year} onChange={(e) => handleYearChange(e.target.value)} disabled={disabled} style={selectStyle}>
        <option value="">Year</option>
        {YEARS.map((y) => <option key={y} value={y}>{y}</option>)}
      </select>
    </div>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────
const css = `
  @import url('https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;700;900&family=DM+Sans:wght@300;400;500;600&display=swap');
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :root {
    --bg: #0a0a0f; --surface: #12121a; --surface2: #1a1a26;
    --border: rgba(255,255,255,0.08); --accent: #e8c547; --accent2: #4ecdc4;
    --text: #f0ede8; --muted: #7a7890; --danger: #ff6b6b; --success: #4ecdc4;
    --radius: 12px; --font-display: 'Playfair Display', serif; --font-body: 'DM Sans', sans-serif;
  }
  body { background: var(--bg); color: var(--text); font-family: var(--font-body); min-height: 100vh; }
  .app { min-height: 100vh; display: flex; flex-direction: column; }

  .auth-page { min-height: 100vh; display: grid; grid-template-columns: 1fr 1fr; }
  .auth-brand {
    background: var(--surface); display: flex; flex-direction: column;
    justify-content: center; padding: 60px; position: relative; overflow: hidden;
    border-right: 1px solid var(--border);
  }
  .auth-brand::before {
    content: ''; position: absolute; top: -100px; left: -100px;
    width: 400px; height: 400px; border-radius: 50%;
    background: radial-gradient(circle, rgba(232,197,71,0.15), transparent 70%);
  }
  .auth-brand::after {
    content: ''; position: absolute; bottom: -80px; right: -80px;
    width: 300px; height: 300px; border-radius: 50%;
    background: radial-gradient(circle, rgba(78,205,196,0.1), transparent 70%);
  }
  .brand-logo { font-family: var(--font-display); font-size: 48px; font-weight: 900; line-height: 1; position: relative; z-index: 1; }
  .brand-logo span { color: var(--accent); }
  .brand-tagline { margin-top: 20px; font-size: 18px; color: var(--muted); line-height: 1.6; max-width: 340px; position: relative; z-index: 1; }
  .brand-stats { display: flex; gap: 32px; margin-top: 48px; position: relative; z-index: 1; }
  .stat { display: flex; flex-direction: column; }
  .stat-num { font-family: var(--font-display); font-size: 32px; font-weight: 700; color: var(--accent); }
  .stat-label { font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 1px; }
  .auth-form-side { display: flex; align-items: center; justify-content: center; padding: 40px; background: var(--bg); }
  .auth-card { width: 100%; max-width: 420px; }
  .auth-title { font-family: var(--font-display); font-size: 32px; font-weight: 700; margin-bottom: 8px; }
  .auth-sub { color: var(--muted); font-size: 14px; margin-bottom: 32px; }

  .field { margin-bottom: 20px; }
  .field label { display: block; font-size: 13px; font-weight: 500; color: var(--muted); margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }
  .field input, .field textarea, .field select {
    width: 100%; padding: 14px 16px; background: var(--surface);
    border: 1px solid var(--border); border-radius: var(--radius);
    color: var(--text); font-family: var(--font-body); font-size: 15px;
    transition: border-color 0.2s; outline: none;
  }
  .field input:focus, .field textarea:focus, .field select:focus {
    border-color: var(--accent); box-shadow: 0 0 0 3px rgba(232,197,71,0.1);
  }
  .field textarea { resize: vertical; min-height: 100px; }
  select option { background: #1a1a26; color: var(--text); }

  .btn { padding: 14px 24px; border-radius: var(--radius); border: none; cursor: pointer; font-family: var(--font-body); font-size: 15px; font-weight: 600; transition: all 0.2s; display: inline-flex; align-items: center; gap: 8px; }
  .btn-primary { background: var(--accent); color: #0a0a0f; width: 100%; justify-content: center; }
  .btn-primary:hover { background: #f0d055; transform: translateY(-1px); box-shadow: 0 8px 24px rgba(232,197,71,0.3); }
  .btn-primary:disabled { opacity: 0.5; transform: none; cursor: not-allowed; }
  .btn-ghost { background: transparent; color: var(--text); border: 1px solid var(--border); }
  .btn-ghost:hover { border-color: var(--accent); color: var(--accent); }
  .btn-teal { background: var(--accent2); color: #0a0a0f; }
  .btn-teal:hover { background: #6eded6; transform: translateY(-1px); }
  .btn-sm { padding: 8px 16px; font-size: 13px; }
  .btn-icon { padding: 10px; border-radius: 8px; }
  .auth-switch { margin-top: 20px; text-align: center; font-size: 14px; color: var(--muted); }
  .auth-switch button { background: none; border: none; color: var(--accent); cursor: pointer; font-weight: 600; }

  .alert { padding: 12px 16px; border-radius: 8px; font-size: 14px; margin-bottom: 16px; }
  .alert-error { background: rgba(255,107,107,0.1); border: 1px solid rgba(255,107,107,0.3); color: #ff9999; }
  .alert-success { background: rgba(78,205,196,0.1); border: 1px solid rgba(78,205,196,0.3); color: var(--accent2); }
  .alert-info { background: rgba(232,197,71,0.1); border: 1px solid rgba(232,197,71,0.3); color: var(--accent); }

  .dashboard { display: flex; min-height: 100vh; }
  .sidebar { width: 240px; background: var(--surface); border-right: 1px solid var(--border); display: flex; flex-direction: column; padding: 24px 0; position: fixed; height: 100vh; z-index: 10; }
  .sidebar-logo { padding: 0 24px 24px; border-bottom: 1px solid var(--border); }
  .sidebar-logo span { font-family: var(--font-display); font-size: 22px; font-weight: 900; }
  .sidebar-logo span b { color: var(--accent); }
  .sidebar-nav { flex: 1; padding: 16px 12px; display: flex; flex-direction: column; gap: 4px; }
  .nav-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 8px; cursor: pointer; color: var(--muted); font-size: 14px; font-weight: 500; transition: all 0.15s; border: none; background: none; width: 100%; text-align: left; }
  .nav-item:hover { background: var(--surface2); color: var(--text); }
  .nav-item.active { background: rgba(232,197,71,0.12); color: var(--accent); }
  .nav-icon { font-size: 16px; width: 20px; text-align: center; }
  .sidebar-user { padding: 16px 24px; border-top: 1px solid var(--border); }
  .user-info { display: flex; align-items: center; gap: 12px; }
  .user-avatar { width: 36px; height: 36px; border-radius: 50%; background: linear-gradient(135deg, var(--accent), var(--accent2)); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #0a0a0f; }
  .user-name { font-size: 13px; font-weight: 600; }
  .user-email { font-size: 11px; color: var(--muted); }
  .main-content { margin-left: 240px; flex: 1; padding: 40px; min-height: 100vh; }
  .page-header { margin-bottom: 32px; }
  .page-title { font-family: var(--font-display); font-size: 28px; font-weight: 700; }
  .page-sub { color: var(--muted); font-size: 15px; margin-top: 6px; }

  .stepper { display: flex; align-items: center; margin-bottom: 40px; flex-wrap: wrap; gap: 4px; }
  .step { display: flex; align-items: center; gap: 10px; cursor: pointer; padding: 8px 16px; border-radius: 24px; transition: all 0.2s; }
  .step.active { background: rgba(232,197,71,0.12); }
  .step-num { width: 28px; height: 28px; border-radius: 50%; background: var(--surface2); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; border: 1px solid var(--border); flex-shrink: 0; transition: all 0.2s; }
  .step.active .step-num { background: var(--accent); color: #0a0a0f; border-color: var(--accent); }
  .step.done .step-num { background: var(--success); color: #0a0a0f; border-color: var(--success); }
  .step-label { font-size: 13px; font-weight: 500; color: var(--muted); white-space: nowrap; }
  .step.active .step-label { color: var(--text); }
  .step-divider { flex: 1; height: 1px; background: var(--border); min-width: 16px; max-width: 32px; }

  .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 28px; margin-bottom: 20px; }
  .card-title { font-size: 16px; font-weight: 600; margin-bottom: 20px; display: flex; align-items: center; gap: 10px; }
  .card-title span { font-size: 18px; }

  .skills-grid { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
  .skill-chip { padding: 8px 14px; border-radius: 20px; border: 1px solid var(--border); font-size: 13px; cursor: pointer; transition: all 0.15s; user-select: none; background: var(--surface2); }
  .skill-chip:hover { border-color: var(--accent); }
  .skill-chip.selected { background: rgba(232,197,71,0.15); border-color: var(--accent); color: var(--accent); }
  .skill-chip.teal.selected { background: rgba(78,205,196,0.15); border-color: var(--accent2); color: var(--accent2); }

  .add-row-btn { display: flex; align-items: center; gap: 8px; padding: 10px 16px; border-radius: 8px; border: 1px dashed var(--border); background: none; color: var(--muted); cursor: pointer; font-size: 13px; transition: all 0.15s; margin-top: 8px; }
  .add-row-btn:hover { border-color: var(--accent); color: var(--accent); }
  .cert-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 12px; align-items: end; margin-bottom: 12px; }

  .date-label { font-size: 13px; font-weight: 500; color: var(--muted); margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; display: block; }
  .present-pill { display: flex; align-items: center; gap: 8px; padding: 14px 16px; background: rgba(78,205,196,0.08); border: 1px solid rgba(78,205,196,0.25); border-radius: var(--radius); color: var(--accent2); font-size: 14px; }

  .score-display { display: flex; align-items: center; gap: 24px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 28px; margin-bottom: 20px; }
  .score-ring { width: 100px; height: 100px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; position: relative; }
  .score-ring::before { content: ''; position: absolute; width: 76px; height: 76px; border-radius: 50%; background: var(--surface); }
  .score-num { position: relative; z-index: 1; font-family: var(--font-display); font-size: 26px; font-weight: 700; }
  .score-info h3 { font-size: 20px; font-weight: 700; margin-bottom: 6px; }
  .score-desc { color: var(--muted); font-size: 14px; line-height: 1.6; }
  .skills-match { display: flex; flex-wrap: wrap; gap: 8px; }
  .match-chip { padding: 6px 12px; border-radius: 16px; font-size: 12px; font-weight: 500; }
  .match-chip.hit { background: rgba(78,205,196,0.15); color: var(--accent2); border: 1px solid rgba(78,205,196,0.3); }
  .match-chip.miss { background: rgba(255,107,107,0.1); color: #ff9999; border: 1px solid rgba(255,107,107,0.2); }

  .keyword-section-title { font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.8px; margin: 16px 0 10px; }

  .generating { display: flex; flex-direction: column; align-items: center; padding: 60px; gap: 20px; }
  .spinner { width: 48px; height: 48px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
  .prog-steps { display: flex; flex-direction: column; gap: 10px; width: 300px; }
  .prog-step { display: flex; align-items: center; gap: 12px; font-size: 14px; color: var(--muted); }
  .prog-step.done { color: var(--success); }
  .prog-step.active { color: var(--text); }
  .prog-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--surface2); flex-shrink: 0; }
  .prog-step.done .prog-dot { background: var(--success); }
  .prog-step.active .prog-dot { background: var(--accent); animation: pulse 1s infinite; }
  @keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }

  .download-btn { display: flex; align-items: center; gap: 10px; padding: 16px 32px; background: linear-gradient(135deg, var(--accent), #d4aa30); color: #0a0a0f; border: none; border-radius: var(--radius); font-size: 16px; font-weight: 700; cursor: pointer; transition: all 0.2s; }
  .download-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 32px rgba(232,197,71,0.4); }

  .profile-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  .info-row { display: flex; flex-direction: column; gap: 4px; }
  .info-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; color: var(--muted); }
  .info-val { font-size: 15px; font-weight: 500; }
  .complete-badge { display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
  .complete-badge.done { background: rgba(78,205,196,0.15); color: var(--accent2); }
  .complete-badge.pending { background: rgba(232,197,71,0.15); color: var(--accent); }

  .tab-bar { display: flex; gap: 4px; margin-bottom: 24px; background: var(--surface); border: 1px solid var(--border); padding: 4px; border-radius: 10px; width: fit-content; flex-wrap: wrap; }
  .tab { padding: 8px 18px; border-radius: 7px; border: none; background: none; color: var(--muted); font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.15s; }
  .tab.active { background: var(--surface2); color: var(--text); }

  @media (max-width: 900px) {
    .auth-page { grid-template-columns: 1fr; }
    .auth-brand { display: none; }
    .profile-grid { grid-template-columns: 1fr; }
    .sidebar { width: 200px; }
    .main-content { margin-left: 200px; padding: 24px; }
  }
`;

// ─── Skill Options ────────────────────────────────────────────────────────────
const SKILL_CATEGORIES = {
  "Programming Languages": ["JavaScript","TypeScript","Python","Java","C++","C#","Go","Rust","Swift","Kotlin","PHP","Ruby","Scala","R"],
  "Frontend": ["React","Vue.js","Angular","Next.js","Svelte","HTML5","CSS3","Tailwind CSS","SASS","Redux","GraphQL"],
  "Backend": ["Node.js","Express.js","Django","FastAPI","Spring Boot","Laravel","Ruby on Rails","REST APIs","Microservices"],
  "Cloud & DevOps": ["AWS","Azure","GCP","Docker","Kubernetes","Terraform","CI/CD","Jenkins","GitHub Actions","Linux"],
  "Databases": ["PostgreSQL","MySQL","MongoDB","Redis","DynamoDB","Elasticsearch","SQLite","Cassandra"],
  "Data & AI": ["Machine Learning","Deep Learning","TensorFlow","PyTorch","Pandas","NumPy","Scikit-learn","Data Analysis","Power BI","Tableau"],
  "Soft Skills": ["Leadership","Communication","Problem Solving","Team Collaboration","Agile","Scrum","Project Management","Mentoring"],
};

// ─── Shared UI ────────────────────────────────────────────────────────────────
function Alert({ type = "error", children }) {
  return <div className={`alert alert-${type}`}>{children}</div>;
}
function Spinner({ small }) {
  return <span className="spinner" style={small ? { width: 20, height: 20, borderWidth: 2 } : {}} />;
}

// ─── Auth Brand ───────────────────────────────────────────────────────────────
function AuthBrand() {
  return (
    <div className="auth-brand">
      <div className="brand-logo">Résumé<span>AI</span></div>
      <p className="brand-tagline">Beat the bots. Land the interview. AI-powered ATS optimization that gets your resume seen.</p>
      <div className="brand-stats">
        <div className="stat"><span className="stat-num">94%</span><span className="stat-label">Pass Rate</span></div>
        <div className="stat"><span className="stat-num">3x</span><span className="stat-label">More Interviews</span></div>
        <div className="stat"><span className="stat-num">2min</span><span className="stat-label">To Generate</span></div>
      </div>
    </div>
  );
}

// ─── Login ────────────────────────────────────────────────────────────────────
function LoginPage({ onLogin, onSwitch, verifiedMsg }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async () => {
    setError(""); setLoading(true);
    try {
      const data = await api.login(email, password);
      localStorage.setItem("ats_token", data.token);
      localStorage.setItem("ats_user", JSON.stringify({ email: data.email, fullName: data.fullName }));
      onLogin(data);
    } catch (e) { setError(e.message); }
    setLoading(false);
  };

  return (
    <div className="auth-page">
      <AuthBrand />
      <div className="auth-form-side">
        <div className="auth-card">
          <h1 className="auth-title">Welcome back</h1>
          <p className="auth-sub">Sign in to continue optimizing your career</p>
          {verifiedMsg && <Alert type="success">✓ Email verified! You can now log in.</Alert>}
          {error && <Alert>{error}</Alert>}
          <div className="field"><label>Email Address</label><input type="email" placeholder="you@example.com" value={email} onChange={e => setEmail(e.target.value)} /></div>
          <div className="field"><label>Password</label><input type="password" placeholder="••••••••" value={password} onChange={e => setPassword(e.target.value)} onKeyDown={e => e.key === "Enter" && handleSubmit()} /></div>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={loading || !email || !password}>
            {loading ? <><Spinner small /> Signing in...</> : "Sign In →"}
          </button>
          <div className="auth-switch">Don't have an account? <button onClick={onSwitch}>Create one</button></div>
        </div>
      </div>
    </div>
  );
}

// ─── Signup ───────────────────────────────────────────────────────────────────
function SignupPage({ onSwitch }) {
  const [form, setForm] = useState({ fullName: "", email: "", password: "", confirm: "" });
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState("");
  const set = k => e => setForm(f => ({ ...f, [k]: e.target.value }));

  const handleSubmit = async () => {
    setError("");
    if (form.password !== form.confirm) { setError("Passwords don't match"); return; }
    if (form.password.length < 6) { setError("Password must be at least 6 characters"); return; }
    setLoading(true);
    try {
      await api.signup(form.fullName, form.email, form.password);
      setSuccess(true);
    } catch (e) { setError(e.message); }
    setLoading(false);
  };

  if (success) return (
    <div className="auth-page">
      <AuthBrand />
      <div className="auth-form-side">
        <div className="auth-card">
          <h1 className="auth-title">Check your inbox</h1>
          <p className="auth-sub">Verification email sent to <strong>{form.email}</strong></p>
          <Alert type="success">✓ Account created! Click the link in your email to verify your account.</Alert>
          <Alert type="info">💡 If you don't see the email, check your spam folder. The verify link also appears in the Spring Boot console logs.</Alert>
          <div style={{ textAlign: "center", marginTop: 20 }}>
            <button className="btn btn-ghost" onClick={onSwitch}>Back to Sign In</button>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="auth-page">
      <AuthBrand />
      <div className="auth-form-side">
        <div className="auth-card">
          <h1 className="auth-title">Create account</h1>
          <p className="auth-sub">Start landing more interviews today</p>
          {error && <Alert>{error}</Alert>}
          <div className="field"><label>Full Name</label><input placeholder="Jane Smith" value={form.fullName} onChange={set("fullName")} /></div>
          <div className="field"><label>Email Address</label><input type="email" placeholder="you@example.com" value={form.email} onChange={set("email")} /></div>
          <div className="field"><label>Password</label><input type="password" placeholder="Min 6 characters" value={form.password} onChange={set("password")} /></div>
          <div className="field"><label>Confirm Password</label><input type="password" placeholder="••••••••" value={form.confirm} onChange={set("confirm")} /></div>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={loading || !form.fullName || !form.email || !form.password}>
            {loading ? <><Spinner small /> Creating...</> : "Create Account →"}
          </button>
          <div className="auth-switch">Already have an account? <button onClick={onSwitch}>Sign in</button></div>
        </div>
      </div>
    </div>
  );
}

// ─── Stepper ──────────────────────────────────────────────────────────────────
const STEPS = [
  { id: "personal",       label: "Personal",       icon: "👤" },
  { id: "experience",     label: "Experience",     icon: "🏢" },
  { id: "education",      label: "Education",      icon: "🎓" },
  { id: "skills",         label: "Skills",         icon: "⚡" },
  { id: "certifications", label: "Certifications", icon: "🏅" },
];

function StepperBar({ currentStep, completedSteps, onNavigate }) {
  return (
    <div className="stepper">
      {STEPS.map((step, i) => (
        <div key={step.id} style={{ display: "flex", alignItems: "center" }}>
          <div
            className={`step ${currentStep === step.id ? "active" : ""} ${completedSteps.includes(step.id) ? "done" : ""}`}
            onClick={() => (completedSteps.includes(step.id) || currentStep === step.id) && onNavigate(step.id)}
          >
            <div className="step-num">{completedSteps.includes(step.id) && currentStep !== step.id ? "✓" : i + 1}</div>
            <span className="step-label">{step.label}</span>
          </div>
          {i < STEPS.length - 1 && <div className="step-divider" />}
        </div>
      ))}
    </div>
  );
}

// ─── Personal Step ────────────────────────────────────────────────────────────
function PersonalStep({ data, onChange, onNext }) {
  const set = k => e => onChange({ ...data, [k]: e.target.value });
  return (
    <div>
      <div className="card">
        <div className="card-title"><span>👤</span> Personal Information</div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px" }}>
          <div className="field"><label>Full Name *</label><input value={data.name || ""} onChange={set("name")} placeholder="Jane Smith" /></div>
          <div className="field"><label>Job Title / Headline *</label><input value={data.headline || ""} onChange={set("headline")} placeholder="Senior Software Engineer" /></div>
          <div className="field"><label>Email *</label><input type="email" value={data.email || ""} onChange={set("email")} placeholder="jane@example.com" /></div>
          <div className="field"><label>Phone *</label><input value={data.phone || ""} onChange={set("phone")} placeholder="+1 (555) 000-0000" /></div>
          <div className="field"><label>Location *</label><input value={data.location || ""} onChange={set("location")} placeholder="San Francisco, CA" /></div>
          <div className="field"><label>LinkedIn URL</label><input value={data.linkedin || ""} onChange={set("linkedin")} placeholder="linkedin.com/in/janesmith" /></div>
          <div className="field"><label>GitHub / Portfolio</label><input value={data.github || ""} onChange={set("github")} placeholder="github.com/janesmith" /></div>
          <div className="field"><label>Website</label><input value={data.website || ""} onChange={set("website")} placeholder="janesmith.dev" /></div>
        </div>
        <div className="field"><label>Professional Summary</label><textarea value={data.summary || ""} onChange={set("summary")} placeholder="Brief 2-3 sentence summary..." rows={4} /></div>
      </div>
      <button className="btn btn-primary" style={{ width: "auto" }} disabled={!data.name || !data.email || !data.phone || !data.location} onClick={onNext}>Continue to Experience →</button>
    </div>
  );
}

// ─── Experience Step ──────────────────────────────────────────────────────────
function ExperienceStep({ data, onChange, onNext, onBack }) {
  const empty = () => ({
    company: "", role: "", location: "",
    startDate: "", endDate: "", current: false, description: "",
  });

  const companies = data.companies?.length ? data.companies : [empty()];

  const update = (i, field, val) =>
    onChange({ ...data, companies: companies.map((c, idx) => idx === i ? { ...c, [field]: val } : c) });

  const add    = () => onChange({ ...data, companies: [...companies, empty()] });
  const remove = i  => onChange({ ...data, companies: companies.filter((_, idx) => idx !== i) });

  return (
    <div>
      <div className="card">
        <div className="card-title"><span>🏢</span> Work Experience</div>

        {companies.map((c, i) => (
          <div
            key={i}
            style={{
              borderBottom: i < companies.length - 1 ? "1px solid var(--border)" : "none",
              paddingBottom: 28, marginBottom: 28,
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
              <span style={{ fontSize: 12, fontWeight: 700, color: "var(--accent)", textTransform: "uppercase", letterSpacing: "0.6px" }}>
                Position {i + 1}
              </span>
              {companies.length > 1 && (
                <button className="btn btn-ghost btn-sm" onClick={() => remove(i)}>✕ Remove</button>
              )}
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px" }}>
              <div className="field">
                <label>Company *</label>
                <input value={c.company} onChange={e => update(i, "company", e.target.value)} placeholder="Acme Corp" />
              </div>
              <div className="field">
                <label>Job Title *</label>
                <input value={c.role} onChange={e => update(i, "role", e.target.value)} placeholder="Software Engineer" />
              </div>
            </div>

            <div className="field">
              <label>Location</label>
              <input
                value={c.location || ""}
                onChange={e => update(i, "location", e.target.value)}
                placeholder="Dallas, TX  /  Remote  /  New York, NY (Hybrid)"
              />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px", marginBottom: 8 }}>
              <div>
                <span className="date-label">Start Date</span>
                <MonthYearPicker value={c.startDate || ""} onChange={val => update(i, "startDate", val)} />
              </div>
              <div>
                <span className="date-label">End Date</span>
                {c.current
                  ? <div className="present-pill">📌 Currently working here</div>
                  : <MonthYearPicker value={c.endDate || ""} onChange={val => update(i, "endDate", val)} />
                }
                <label style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 8, cursor: "pointer", fontSize: 13, color: "var(--muted)" }}>
                  <input
                    type="checkbox"
                    checked={!!c.current}
                    onChange={e => update(i, "current", e.target.checked)}
                    style={{ width: 14, height: 14 }}
                  />
                  I currently work here
                </label>
              </div>
            </div>

            <div className="field" style={{ marginTop: 8 }}>
              <label>Key Achievements & Responsibilities</label>
              <textarea
                value={c.description}
                onChange={e => update(i, "description", e.target.value)}
                placeholder={"• Led team of 5 engineers...\n• Built system that improved performance by 40%...\n• Deployed microservices on AWS EKS..."}
                rows={5}
              />
            </div>
          </div>
        ))}

        <button className="add-row-btn" onClick={add}>+ Add Another Position</button>
      </div>

      <div style={{ display: "flex", gap: 12 }}>
        <button className="btn btn-ghost" onClick={onBack}>← Back</button>
        <button className="btn btn-primary" style={{ width: "auto" }} onClick={onNext}>Continue to Education →</button>
      </div>
    </div>
  );
}

// ─── Education Step ───────────────────────────────────────────────────────────
function EducationStep({ data, onChange, onNext, onBack }) {
  const edu = data.education || [{
  institution: "",
  location: "",
  degree: "",
  field: "",
  year: "",
  gpa: ""
}];

  const update = (i, field, val) =>
    onChange({ ...data, education: edu.map((e, idx) => idx === i ? { ...e, [field]: val } : e) });

  const add = () => onChange({
  ...data,
  education: [...edu, {
    institution: "",
    location: "",
    degree: "",
    field: "",
    year: "",
    gpa: ""
  }]
});
  const remove = (i) => onChange({ ...data, education: edu.filter((_, idx) => idx !== i) });

  return (
    <div>
      <div className="card">
        <div className="card-title"><span>🎓</span> Education</div>

        {edu.map((e, i) => (
          <div
            key={i}
            style={{
              borderBottom: i < edu.length - 1 ? "1px solid var(--border)" : "none",
              paddingBottom: 20, marginBottom: 20,
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--muted)" }}>Degree {i + 1}</span>
              {edu.length > 1 && <button className="btn btn-ghost btn-sm" onClick={() => remove(i)}>✕ Remove</button>}
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px" }}>
              <div className="field">
                <label>Institution *</label>
                <input value={e.institution} onChange={ev => update(i, "institution", ev.target.value)} placeholder="MIT" />
              </div>
              <div className="field">
                <label>University Location</label>
                <input
                  value={e.location || ""}
                  onChange={ev => update(i, "location", ev.target.value)}
                  placeholder="Cambridge, MA"
                />
              </div>
              
              <div className="field">
                <label>Degree</label>
                <select value={e.degree} onChange={ev => update(i, "degree", ev.target.value)}>
                  <option value="">Select degree</option>
                  <option>Bachelor of Science</option>
                  <option>Bachelor of Arts</option>
                  <option>Master of Science</option>
                  <option>Master of Arts</option>
                  <option>MBA</option>
                  <option>PhD</option>
                  <option>Associate Degree</option>
                  <option>Diploma</option>
                </select>
              </div>
              <div className="field">
                <label>Field of Study</label>
                <input value={e.field} onChange={ev => update(i, "field", ev.target.value)} placeholder="Computer Science" />
              </div>
              <div className="field">
                <label>Graduation Year</label>
                <input type="number" value={e.year} onChange={ev => update(i, "year", ev.target.value)} placeholder="2020" />
              </div>
              <div className="field">
                <label>GPA (Optional)</label>
                <input value={e.gpa || ""} onChange={ev => update(i, "gpa", ev.target.value)} placeholder="3.8 / 4.0" />
              </div>
            </div>
          </div>
        ))}

        <button className="add-row-btn" onClick={add}>+ Add Another Degree</button>
      </div>

      <div style={{ display: "flex", gap: 12 }}>
        <button className="btn btn-ghost" onClick={onBack}>← Back</button>
        <button className="btn btn-primary" style={{ width: "auto" }} onClick={onNext}>Continue to Skills →</button>
      </div>
    </div>
  );
}

// ─── Skills Step ──────────────────────────────────────────────────────────────
function SkillsStep({ data, onChange, onNext, onBack }) {
  const selected = data.skills || [];
  const [activeTab, setActiveTab] = useState(Object.keys(SKILL_CATEGORIES)[0]);
  const [custom, setCustom] = useState("");
  const toggle = skill => onChange({ ...data, skills: selected.includes(skill) ? selected.filter(s => s !== skill) : [...selected, skill] });
  const addCustom = () => {
    if (custom.trim() && !selected.includes(custom.trim())) {
      onChange({ ...data, skills: [...selected, custom.trim()] });
      setCustom("");
    }
  };

  return (
    <div>
      <div className="card">
        <div className="card-title"><span>⚡</span> Technical & Professional Skills</div>
        <p style={{ color: "var(--muted)", fontSize: 14, marginBottom: 20 }}>Select all applicable skills. These will be matched against job descriptions.</p>
        <div className="tab-bar">
          {Object.keys(SKILL_CATEGORIES).map(cat => <button key={cat} className={`tab ${activeTab === cat ? "active" : ""}`} onClick={() => setActiveTab(cat)}>{cat}</button>)}
        </div>
        <div className="skills-grid">
          {SKILL_CATEGORIES[activeTab].map(skill => (
            <div key={skill} className={`skill-chip ${selected.includes(skill) ? "selected" : ""}`} onClick={() => toggle(skill)}>
              {selected.includes(skill) ? "✓ " : ""}{skill}
            </div>
          ))}
        </div>
        <div style={{ marginTop: 20, display: "flex", gap: 10, alignItems: "center" }}>
          <div className="field" style={{ flex: 1, margin: 0 }}>
            <input value={custom} onChange={e => setCustom(e.target.value)} placeholder="Add custom skill..." onKeyDown={e => e.key === "Enter" && addCustom()} />
          </div>
          <button className="btn btn-ghost btn-sm" onClick={addCustom}>+ Add</button>
        </div>
        {selected.length > 0 && (
          <div style={{ marginTop: 20 }}>
            <div style={{ fontSize: 12, color: "var(--muted)", marginBottom: 10, textTransform: "uppercase", letterSpacing: "0.5px" }}>{selected.length} Selected</div>
            <div className="skills-grid">{selected.map(s => <div key={s} className="skill-chip selected teal" onClick={() => toggle(s)}>✓ {s} ✕</div>)}</div>
          </div>
        )}
      </div>
      <div style={{ display: "flex", gap: 12 }}>
        <button className="btn btn-ghost" onClick={onBack}>← Back</button>
        <button className="btn btn-primary" style={{ width: "auto" }} disabled={selected.length === 0} onClick={onNext}>Continue to Certifications →</button>
      </div>
    </div>
  );
}

// ─── Certifications Step ──────────────────────────────────────────────────────
function CertificationsStep({ data, onChange, onSave, onBack, saving }) {
  const certs = data.certifications || [{ name: "", issuer: "", year: "", url: "" }];
  const update = (i, field, val) => onChange({ ...data, certifications: certs.map((c, idx) => idx === i ? { ...c, [field]: val } : c) });
  const add    = () => onChange({ ...data, certifications: [...certs, { name: "", issuer: "", year: "", url: "" }] });
  const remove = i  => onChange({ ...data, certifications: certs.filter((_, idx) => idx !== i) });

  return (
    <div>
      <div className="card">
        <div className="card-title"><span>🏅</span> Certifications & Licenses</div>
        {certs.map((c, i) => (
          <div key={i} className="cert-row">
            <div className="field" style={{ margin: 0 }}><label>Certification Name</label><input value={c.name} onChange={e => update(i, "name", e.target.value)} placeholder="AWS Solutions Architect" /></div>
            <div className="field" style={{ margin: 0 }}><label>Issuer</label><input value={c.issuer} onChange={e => update(i, "issuer", e.target.value)} placeholder="Amazon Web Services" /></div>
            <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
              <div className="field" style={{ margin: 0, width: 90 }}><label>Year</label><input type="number" value={c.year} onChange={e => update(i, "year", e.target.value)} placeholder="2023" /></div>
              {certs.length > 1 && <button className="btn btn-ghost btn-icon" style={{ marginBottom: 0, height: 48 }} onClick={() => remove(i)}>✕</button>}
            </div>
          </div>
        ))}
        <button className="add-row-btn" onClick={add}>+ Add Certification</button>
      </div>
      <div style={{ display: "flex", gap: 12 }}>
        <button className="btn btn-ghost" onClick={onBack}>← Back</button>
        <button className="btn btn-teal" style={{ padding: "14px 32px" }} onClick={onSave} disabled={saving}>
          {saving ? <><Spinner small /> Saving...</> : "✓ Save Profile & Continue"}
        </button>
      </div>
    </div>
  );
}

// ─── Profile Builder ──────────────────────────────────────────────────────────
function ProfileBuilder({ session, initialProfile, onComplete }) {
  const [currentStep, setCurrentStep] = useState("personal");
  const [completedSteps, setCompletedSteps] = useState([]);
  const [profile, setProfile] = useState({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (initialProfile) {
      setProfile(initialProfile);
      const completed = [];
      if (initialProfile.name) completed.push("personal");
      if (initialProfile.companies?.length) completed.push("experience");
      if (initialProfile.education?.length) completed.push("education");
      if (initialProfile.skills?.length) completed.push("skills");
      if (initialProfile.certifications?.length) completed.push("certifications");
      setCompletedSteps(completed);
    }
  }, [initialProfile]);

  const stepIndex = STEPS.findIndex(s => s.id === currentStep);

  const goNext = () => {
    const next = STEPS[stepIndex + 1];
    if (next) {
      setCompletedSteps(prev => [...new Set([...prev, currentStep])]);
      setCurrentStep(next.id);
    }
  };

  const goBack = () => {
    const prev = STEPS[stepIndex - 1];
    if (prev) setCurrentStep(prev.id);
  };

  const saveProfile = async () => {
    setSaving(true); setError("");
    try {
      const formattedProfile = {
        ...profile,
        companies: (profile.companies || []).map(c => ({
          ...c,
          startDate: formatMonthYear(c.startDate),
          endDate: c.current ? "Present" : formatMonthYear(c.endDate)
        }))
      };

      await api.saveProfile(JSON.stringify(formattedProfile));

      onComplete(profile);
    } catch (e) { setError(e.message); }
    setSaving(false);
  };

  return (
    <div className="main-content" style={{ marginLeft: 0 }}>
      <div className="page-header">
        <h1 className="page-title">Build Your Profile</h1>
        <p className="page-sub">Complete your profile to unlock AI-powered resume generation</p>
      </div>
      {error && <Alert>{error}</Alert>}
      <StepperBar currentStep={currentStep} completedSteps={completedSteps} onNavigate={setCurrentStep} />
      {currentStep === "personal" && <PersonalStep data={profile} onChange={setProfile} onNext={goNext} />}
      {currentStep === "experience" && <ExperienceStep data={profile} onChange={setProfile} onNext={goNext} onBack={goBack} />}
      {currentStep === "education" && <EducationStep data={profile} onChange={setProfile} onNext={goNext} onBack={goBack} />}
      {currentStep === "skills" && <SkillsStep data={profile} onChange={setProfile} onNext={goNext} onBack={goBack} />}
      {currentStep === "certifications" && <CertificationsStep data={profile} onChange={setProfile} onSave={saveProfile} onBack={goBack} saving={saving} />}
    </div>
  );
}

// ─── Resume Generator ─────────────────────────────────────────────────────────
const GEN_STEPS_LIST = [
  "Analyzing job description keywords...",
  "Extracting technical & domain skills...",
  "Tailoring resume content to match JD...",
  "Self-auditing keyword placement...",
  "Calculating ATS compatibility score...",
  "Generating formatted PDF resume...",
];

function ResumeGenerator({ profile }) {
  const [jd, setJd] = useState("");
  const [status, setStatus] = useState("idle");
  const [genStep, setGenStep] = useState(0);
  const [atsResult, setAtsResult] = useState(null);
  const [pdfBlob, setPdfBlob] = useState(null);
  const [error, setError] = useState("");

  const generate = async () => {
    if (!jd.trim()) return;
    setStatus("generating"); setGenStep(0); setError(""); setAtsResult(null); setPdfBlob(null);
    try {
      const stepInterval = setInterval(() => {
        setGenStep(prev => prev < GEN_STEPS_LIST.length - 1 ? prev + 1 : prev);
      }, 1800);

      // Single call — backend LLM extracts JD keywords, builds the resume,
      // self-audits which keywords it placed, then scores and returns PDF.
      const result = await api.generateResumeWithScore(profile, jd);

      clearInterval(stepInterval);
      setGenStep(GEN_STEPS_LIST.length);

      // Decode base64 PDF → Blob for download
      const binaryStr = atob(result.pdfBase64);
      const bytes = new Uint8Array(binaryStr.length);
      for (let i = 0; i < binaryStr.length; i++) bytes[i] = binaryStr.charCodeAt(i);
      const blob = new Blob([bytes], { type: "application/pdf" });

      setAtsResult({
        atsScore:        result.atsScore,
        scoreLabel:      result.scoreLabel,
        matchedSkills:   result.matchedSkills,
        totalSkills:     result.totalSkills,
        matchedKeywords: result.matchedKeywords || [],
        missingKeywords: result.missingKeywords || [],
      });
      setPdfBlob(blob);
      setStatus("done");
    } catch (e) { setError(e.message); setStatus("idle"); }
  };

  const downloadPdf = () => {
    const url = URL.createObjectURL(pdfBlob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${(profile.name || "Resume").replace(/\s+/g, "_")}_ATS_Optimized.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const reset = () => { setStatus("idle"); setAtsResult(null); setPdfBlob(null); setJd(""); setGenStep(0); setError(""); };

  // ── Generating state ──
  if (status === "generating") {
    return (
      <div className="card generating">
        <div className="spinner" />
        <p style={{ fontFamily: "var(--font-display)", fontSize: 22, fontWeight: 700 }}>Generating Your Resume</p>
        <div className="prog-steps">
          {GEN_STEPS_LIST.map((s, i) => (
            <div key={i} className={`prog-step ${i < genStep ? "done" : i === genStep ? "active" : ""}`}>
              <div className="prog-dot" />{i < genStep ? `✓ ${s}` : s}
            </div>
          ))}
        </div>
        <p style={{ color: "var(--muted)", fontSize: 13 }}>This may take 30–60 seconds while the AI tailors your resume…</p>
      </div>
    );
  }

  // ── Done state ──
  if (status === "done" && atsResult) {
    const scoreColor =
      atsResult.atsScore >= 85 ? "var(--success)" :
      atsResult.atsScore >= 70 ? "var(--accent)"  : "var(--danger)";

    return (
      <div>
        {/* Score Ring */}
        <div className="score-display">
          <div
            className="score-ring"
            style={{ background: `conic-gradient(${scoreColor} ${atsResult.atsScore * 3.6}deg, var(--surface2) 0)` }}
          >
            <span className="score-num" style={{ color: scoreColor }}>{atsResult.atsScore}</span>
          </div>
          <div className="score-info">
            <h3>ATS Score: <span style={{ color: scoreColor }}>{atsResult.scoreLabel}</span></h3>
            <p className="score-desc">
              Matched <strong style={{ color: scoreColor }}>{atsResult.matchedSkills}</strong> of{" "}
              <strong>{atsResult.totalSkills}</strong> keywords the AI extracted from the job description.
              {atsResult.atsScore >= 85
                ? " Excellent — your resume is highly likely to pass ATS filters."
                : atsResult.atsScore >= 70
                  ? " Good match. Weaving in the missing keywords below could push you to Excellent."
                  : " Consider adding the highlighted keywords to strengthen your ATS match."}
            </p>
          </div>
        </div>

        {/* Keyword Analysis */}
        <div className="card">
          <div className="card-title"><span>🎯</span> Keyword Match Analysis</div>

          <div style={{ display: "flex", gap: 24, marginBottom: 20, flexWrap: "wrap" }}>
            <div style={{ textAlign: "center" }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: "var(--success)" }}>{atsResult.matchedKeywords.length}</div>
              <div style={{ fontSize: 12, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.5px" }}>Matched</div>
            </div>
            <div style={{ textAlign: "center" }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: "var(--danger)" }}>{atsResult.missingKeywords.length}</div>
              <div style={{ fontSize: 12, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.5px" }}>Missing</div>
            </div>
            <div style={{ textAlign: "center" }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: "var(--accent)" }}>{atsResult.totalSkills}</div>
              <div style={{ fontSize: 12, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.5px" }}>Total JD Keywords</div>
            </div>
          </div>

          {atsResult.matchedKeywords.length > 0 && (
            <>
              <div className="keyword-section-title">✓ Found in your resume</div>
              <div className="skills-match">
                {atsResult.matchedKeywords.map(s => (
                  <span key={s} className="match-chip hit">✓ {s}</span>
                ))}
              </div>
            </>
          )}

          {atsResult.missingKeywords.length > 0 && (
            <>
              <div className="keyword-section-title" style={{ marginTop: 20 }}>✕ Missing from your resume</div>
              <div className="skills-match">
                {atsResult.missingKeywords.map(s => (
                  <span key={s} className="match-chip miss">{s}</span>
                ))}
              </div>
              <p style={{ fontSize: 13, color: "var(--muted)", marginTop: 12 }}>
                💡 Tip: Update your profile to include the missing keywords and regenerate for a higher score.
              </p>
            </>
          )}
        </div>

        {/* Actions */}
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
          <button className="download-btn" onClick={downloadPdf}>⬇ Download ATS-Optimized PDF</button>
          <button className="btn btn-ghost" onClick={reset}>Generate Another</button>
        </div>
      </div>
    );
  }

  // ── Idle state ──
  return (
    <div>
      {error && <Alert>{error}</Alert>}
      <div className="card">
        <div className="card-title"><span>📋</span> Job Description</div>
        <p style={{ color: "var(--muted)", fontSize: 14, marginBottom: 16 }}>
          Paste the full job description. The AI will extract all important keywords, build your resume around them, and report exactly which ones made it in.
        </p>
        <div className="field">
          <label>Job Description *</label>
          <textarea value={jd} onChange={e => setJd(e.target.value)} placeholder="Paste the full job description here..." rows={14} />
        </div>
      </div>
      <div className="card">
        <div className="card-title"><span>👤</span> Profile Summary</div>
        <div className="profile-grid">
          {[
            ["Name",           profile.name],
            ["Headline",       profile.headline || "—"],
            ["Location",       profile.location],
            ["Skills",         `${(profile.skills || []).length} selected`],
            ["Experience",     `${(profile.companies || []).filter(c => c.company).length} positions`],
            ["Certifications", `${(profile.certifications || []).filter(c => c.name).length}`],
          ].map(([k, v]) => (
            <div key={k} className="info-row"><span className="info-label">{k}</span><span className="info-val">{v}</span></div>
          ))}
        </div>
      </div>
      <button
        className="btn btn-primary"
        style={{ width: "auto", padding: "16px 40px", fontSize: 16 }}
        disabled={!jd.trim()}
        onClick={generate}
      >
        🤖 Generate ATS-Optimized Resume
      </button>
      {!jd.trim() && <p style={{ color: "var(--muted)", fontSize: 13, marginTop: 10 }}>Paste a job description above to enable generation</p>}
    </div>
  );
}

// ─── Profile Overview ─────────────────────────────────────────────────────────
function ProfileOverview({ profile, onEdit }) {
  const fmtDate = (val) => {
    if (!val) return "";
    const [y, m] = val.split("-");
    const month = MONTHS.find(mo => mo.value === m);
    return [month?.label?.slice(0, 3), y].filter(Boolean).join(" ");
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <span className={`complete-badge ${profile ? "done" : "pending"}`}>{profile ? "✓ Profile Complete" : "⚠ Profile Incomplete"}</span>
        <button className="btn btn-ghost btn-sm" onClick={onEdit}>{profile ? "✎ Edit Profile" : "Complete Profile"}</button>
      </div>
      {profile ? (
        <>
          <div className="card">
            <div className="card-title"><span>👤</span> Personal</div>
            <div className="profile-grid">
              {[
                ["Name", profile.name], ["Email", profile.email], ["Phone", profile.phone],
                ["Location", profile.location], ["LinkedIn", profile.linkedin || "—"], ["GitHub", profile.github || "—"],
              ].map(([k, v]) => (
                <div key={k} className="info-row"><span className="info-label">{k}</span><span className="info-val">{v}</span></div>
              ))}
            </div>
            {profile.summary && (
              <div style={{ marginTop: 16, padding: "12px 16px", background: "var(--surface2)", borderRadius: 8, fontSize: 14, lineHeight: 1.7, color: "var(--muted)" }}>
                {profile.summary}
              </div>
            )}
          </div>

          <div className="card">
            <div className="card-title"><span>🏢</span> Experience ({(profile.companies || []).filter(c => c.company).length} positions)</div>
            {(profile.companies || []).filter(c => c.company).map((c, i) => (
              <div key={i} style={{ borderBottom: "1px solid var(--border)", paddingBottom: 12, marginBottom: 12 }}>
                <div style={{ fontWeight: 600 }}>{c.role} @ {c.company}</div>
                <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 3 }}>
                  {c.location && <span style={{ marginRight: 8 }}>📍 {c.location}</span>}
                  {fmtDate(c.startDate)} — {c.current ? "Present" : fmtDate(c.endDate)}
                </div>
              </div>
            ))}
          </div>
          <div className="card">
            <div className="card-title"><span>🎓</span> Education</div>
            {(profile.education || []).filter(e => e.institution).map((e, i) => (
              <div
                key={i}
                style={{
                  padding: "8px 0",
                  borderBottom: "1px solid var(--border)"
                }}
              >
                <div style={{ fontWeight: 600 }}>
                  {e.degree} {e.field && `in ${e.field}`}
                </div>
                <div style={{ fontSize: 13, color: "var(--muted)" }}>
                  {e.institution}
                  {e.location && ` · ${e.location}`}
                  {e.year && ` · ${e.year}`}
                  {e.gpa && ` · GPA: ${e.gpa}`}
                </div>
              </div>
            ))}
          </div>
          <div className="card">
            <div className="card-title"><span>⚡</span> Skills ({(profile.skills || []).length})</div>
            <div className="skills-grid">
              {(profile.skills || []).map(s => <span key={s} className="skill-chip selected">{s}</span>)}
            </div>
          </div>

          {(profile.certifications || []).filter(c => c.name).length > 0 && (
            <div className="card">
              <div className="card-title"><span>🏅</span> Certifications</div>
              {(profile.certifications || []).filter(c => c.name).map((c, i) => (
                <div key={i} style={{ display: "flex", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px solid var(--border)" }}>
                  <span style={{ fontWeight: 500 }}>{c.name}</span>
                  <span style={{ fontSize: 13, color: "var(--muted)" }}>{c.issuer}{c.year ? ` · ${c.year}` : ""}</span>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="card" style={{ textAlign: "center", padding: "60px 40px" }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>📄</div>
          <h3 style={{ fontFamily: "var(--font-display)", fontSize: 22, marginBottom: 8 }}>No profile yet</h3>
          <p style={{ color: "var(--muted)", marginBottom: 24 }}>Complete your profile to start generating ATS-optimized resumes</p>
          <button className="btn btn-primary" style={{ width: "auto" }} onClick={onEdit}>Complete Your Profile</button>
        </div>
      )}
    </div>
  );
}

// ─── Dashboard ────────────────────────────────────────────────────────────────
function Dashboard({ session, profile, onLogout, onEditProfile }) {
  const [activePage, setActivePage] = useState(profile ? "generate" : "profile");

  return (
    <div className="dashboard">
      <div className="sidebar">
        <div className="sidebar-logo"><span>Résumé<b>AI</b></span></div>
        <nav className="sidebar-nav">
          {[{ id: "generate", label: "Generate Resume", icon: "✨" }, { id: "profile", label: "My Profile", icon: "👤" }].map(item => (
            <button key={item.id} className={`nav-item ${activePage === item.id ? "active" : ""}`} onClick={() => setActivePage(item.id)}>
              <span className="nav-icon">{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-user">
          <div className="user-info">
            <div className="user-avatar">{session.fullName?.[0]?.toUpperCase() || "U"}</div>
            <div><div className="user-name">{session.fullName}</div><div className="user-email">{session.email}</div></div>
          </div>
          <button className="btn btn-ghost btn-sm" style={{ marginTop: 12, width: "100%" }} onClick={onLogout}>Sign Out</button>
        </div>
      </div>

      <div className="main-content">
        {activePage === "generate" && (
          <>
            <div className="page-header">
              <h1 className="page-title">Generate Resume</h1>
              <p className="page-sub">Paste a job description and let AI craft your perfect ATS-optimized resume</p>
            </div>
            {!profile ? (
              <div className="card" style={{ textAlign: "center", padding: "60px" }}>
                <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
                <h3 style={{ fontFamily: "var(--font-display)", fontSize: 22, marginBottom: 8 }}>Complete your profile first</h3>
                <p style={{ color: "var(--muted)", marginBottom: 24 }}>We need your details to generate a personalized resume</p>
                <button className="btn btn-primary" style={{ width: "auto" }} onClick={() => { setActivePage("profile"); onEditProfile(); }}>Complete Profile</button>
              </div>
            ) : <ResumeGenerator profile={profile} />}
          </>
        )}
        {activePage === "profile" && (
          <>
            <div className="page-header">
              <h1 className="page-title">My Profile</h1>
              <p className="page-sub">Your professional details used to generate tailored resumes</p>
            </div>
            <ProfileOverview profile={profile} onEdit={onEditProfile} />
          </>
        )}
      </div>
    </div>
  );
}

// ─── Root App ─────────────────────────────────────────────────────────────────
export default function App() {
  const [authView, setAuthView] = useState("login");
  const [session, setSession] = useState(() => {
    const user  = localStorage.getItem("ats_user");
    const token = localStorage.getItem("ats_token");
    return (user && token) ? JSON.parse(user) : null;
  });
  const [profile, setProfile] = useState(null);
  const [buildingProfile, setBuildingProfile] = useState(false);
  const [loadingProfile, setLoadingProfile] = useState(false);
  const [verifiedMsg, setVerifiedMsg] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("verified") === "true") {
      setVerifiedMsg(true);
      window.history.replaceState({}, "", window.location.pathname);
    }
  }, []);

  useEffect(() => {
    if (session) {
      setLoadingProfile(true);
      api.getProfile()
        .then(data => {
          if (data.profileComplete && data.profile) {
            try { setProfile(JSON.parse(data.profile)); }
            catch { setProfile(null); setBuildingProfile(true); }
          } else {
            setBuildingProfile(true);
          }
        })
        .catch(() => setBuildingProfile(true))
        .finally(() => setLoadingProfile(false));
    }
  }, [session]);

  const handleLogin  = data => setSession({ email: data.email, fullName: data.fullName });
  const handleLogout = () => {
    localStorage.removeItem("ats_token");
    localStorage.removeItem("ats_user");
    setSession(null); setProfile(null); setBuildingProfile(false);
  };

  return (
    <>
      <style>{css}</style>
      <div className="app">
        {!session && (
          authView === "login"
            ? <LoginPage onLogin={handleLogin} onSwitch={() => setAuthView("signup")} verifiedMsg={verifiedMsg} />
            : <SignupPage onSwitch={() => setAuthView("login")} />
        )}
        {session && loadingProfile && (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100vh", flexDirection: "column", gap: 16 }}>
            <div className="spinner" /><p style={{ color: "var(--muted)" }}>Loading your profile…</p>
          </div>
        )}
        {session && !loadingProfile && buildingProfile && (
          <ProfileBuilder
            session={session}
            initialProfile={profile}
            onComplete={p => {
              setProfile(p);
              setBuildingProfile(false);
            }}
          />
        )}
        {session && !loadingProfile && !buildingProfile && (
          <Dashboard session={session} profile={profile} onLogout={handleLogout} onEditProfile={() => setBuildingProfile(true)} />
        )}
      </div>
    </>
  );
}