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
  if (res.status === 401) {
    localStorage.removeItem("ats_token");
    localStorage.removeItem("ats_user");
    window.location.href = "/";
    return;
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) throw new Error(data.message || "Request failed");
  return data;
};

const api = {
  signup: (fullName, email, password) =>
    apiFetch("/api/auth/signup", { method: "POST", body: JSON.stringify({ fullName, email, password }) }),
  login: (email, password) =>
    apiFetch("/api/auth/login", { method: "POST", body: JSON.stringify({ email, password }) }),
  forgotPassword: (email) =>
    apiFetch("/api/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) }),
  resetPassword: (email, token, password) =>
    apiFetch("/api/auth/reset-password", { method: "POST", body: JSON.stringify({ email, token, password }) }),
  getProfile: () => apiFetch("/api/auth/profile"),
  saveProfile: (profileJson) =>
    apiFetch("/api/auth/profile", { method: "POST", body: JSON.stringify({ profileJson }) }),
  generateResumeWithScore: (resumeMetaData, jobDescription) =>
    apiFetch("/api/resume/tailor-generate-score", {
      method: "POST",
      body: JSON.stringify({ resumeMetaData, jobDescription }),
    }),
  parseResume: (file) => {
    const token = getToken();
    const formData = new FormData();
    formData.append("file", file);
    return fetch(`${BASE_URL}/api/resume/parse`, {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    }).then(async (res) => {
      if (res.status === 401) {
        localStorage.removeItem("ats_token");
        localStorage.removeItem("ats_user");
        window.location.href = "/";
        return;
      }
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || "Parse failed");
      return data;
    });
  },
};

// ─── Helpers ──────────────────────────────────────────────────────────────────
const MONTHS = [
  { value: "01", label: "January" }, { value: "02", label: "February" },
  { value: "03", label: "March" }, { value: "04", label: "April" },
  { value: "05", label: "May" }, { value: "06", label: "June" },
  { value: "07", label: "July" }, { value: "08", label: "August" },
  { value: "09", label: "September" }, { value: "10", label: "October" },
  { value: "11", label: "November" }, { value: "12", label: "December" },
];
const YEARS = (() => {
  const cur = new Date().getFullYear();
  const arr = [];
  for (let y = cur; y >= 1980; y--) arr.push(String(y));
  return arr;
})();

function MonthYearPicker({ value, onChange, disabled }) {
  const [year, setYear] = useState("");
  const [month, setMonth] = useState("");
  useEffect(() => {
    if (value && value.includes("-")) {
      const [y, m] = value.split("-");
      setYear(y || ""); setMonth(m || "");
    } else { setYear(""); setMonth(""); }
  }, [value]);
  const handleYear = (v) => { setYear(v); if (v && month) onChange(`${v}-${month}`); else onChange(""); };
  const handleMonth = (v) => { setMonth(v); if (year && v) onChange(`${year}-${v}`); else onChange(""); };
  const sel = { width: "100%", padding: "14px 12px", background: disabled ? "rgba(255,255,255,0.03)" : "var(--surface)", border: "1px solid var(--border)", borderRadius: "var(--radius)", color: disabled ? "var(--muted)" : "var(--text)", fontFamily: "var(--font-body)", fontSize: 14, outline: "none", opacity: disabled ? 0.5 : 1, cursor: disabled ? "not-allowed" : "pointer" };
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
      <select value={month} onChange={e => handleMonth(e.target.value)} disabled={disabled} style={sel}>
        <option value="">Month</option>
        {MONTHS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
      </select>
      <select value={year} onChange={e => handleYear(e.target.value)} disabled={disabled} style={sel}>
        <option value="">Year</option>
        {YEARS.map(y => <option key={y} value={y}>{y}</option>)}
      </select>
    </div>
  );
}

const fmtDate = (val) => {
  if (!val) return "";
  const [y, m] = val.split("-");
  const mo = MONTHS.find(x => x.value === m);
  return [mo?.label?.slice(0, 3), y].filter(Boolean).join(" ");
};

const scoreColor = (s) => s >= 90 ? "var(--success)" : s >= 75 ? "var(--accent)" : s >= 55 ? "#f0a830" : "var(--danger)";
const scoreBg = (s) => s >= 90 ? "rgba(78,205,196,0.08)" : s >= 75 ? "rgba(232,197,71,0.08)" : s >= 55 ? "rgba(240,168,48,0.08)" : "rgba(255,107,107,0.08)";

// ─── CSS ──────────────────────────────────────────────────────────────────────
const css = `
  @import url('https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;700;900&family=DM+Sans:wght@300;400;500;600&display=swap');
  *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
  :root {
    --bg: #0a0a0f; --surface: #12121a; --surface2: #1a1a26; --surface3: #202030;
    --border: rgba(255,255,255,0.08); --accent: #e8c547; --accent2: #4ecdc4;
    --text: #f0ede8; --muted: #7a7890; --danger: #ff6b6b; --success: #4ecdc4;
    --radius: 12px; --font-display: 'Playfair Display', serif; --font-body: 'DM Sans', sans-serif;
  }
  body { background: var(--bg); color: var(--text); font-family: var(--font-body); min-height: 100vh; }
  .app { min-height: 100vh; display: flex; flex-direction: column; }

  .auth-page { min-height: 100vh; display: grid; grid-template-columns: 1fr 1fr; }
  .auth-brand { background: var(--surface); display: flex; flex-direction: column; justify-content: center; padding: 60px; position: relative; overflow: hidden; border-right: 1px solid var(--border); }
  .auth-brand::before { content: ''; position: absolute; top: -100px; left: -100px; width: 400px; height: 400px; border-radius: 50%; background: radial-gradient(circle, rgba(232,197,71,0.15), transparent 70%); }
  .auth-brand::after { content: ''; position: absolute; bottom: -80px; right: -80px; width: 300px; height: 300px; border-radius: 50%; background: radial-gradient(circle, rgba(78,205,196,0.1), transparent 70%); }
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
  .auth-switch { margin-top: 20px; text-align: center; font-size: 14px; color: var(--muted); }
  .auth-switch button { background: none; border: none; color: var(--accent); cursor: pointer; font-weight: 600; }

  .field { margin-bottom: 20px; }
  .field label { display: block; font-size: 13px; font-weight: 500; color: var(--muted); margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }
  .field input, .field textarea, .field select { width: 100%; padding: 14px 16px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); color: var(--text); font-family: var(--font-body); font-size: 15px; transition: border-color 0.2s; outline: none; }
  .field input:focus, .field textarea:focus, .field select:focus { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(232,197,71,0.1); }
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
  .btn-danger { background: rgba(255,107,107,0.1); color: var(--danger); border: 1px solid rgba(255,107,107,0.2); }
  .btn-danger:hover { background: rgba(255,107,107,0.2); }
  .btn-sm { padding: 8px 16px; font-size: 13px; }
  .btn-icon { padding: 10px; border-radius: 8px; }

  .alert { padding: 12px 16px; border-radius: 8px; font-size: 14px; margin-bottom: 16px; }
  .alert-error { background: rgba(255,107,107,0.1); border: 1px solid rgba(255,107,107,0.3); color: #ff9999; }
  .alert-success { background: rgba(78,205,196,0.1); border: 1px solid rgba(78,205,196,0.3); color: var(--accent2); }
  .alert-info { background: rgba(232,197,71,0.1); border: 1px solid rgba(232,197,71,0.3); color: var(--accent); }

  .dashboard { display: flex; min-height: 100vh; }
  .sidebar { width: 240px; background: var(--surface); border-right: 1px solid var(--border); display: flex; flex-direction: column; padding: 0; position: fixed; height: 100vh; z-index: 10; }
  .sidebar-logo { padding: 24px; border-bottom: 1px solid var(--border); cursor: pointer; transition: opacity 0.15s; }
  .sidebar-logo:hover { opacity: 0.8; }
  .sidebar-logo .logo-text { font-family: var(--font-display); font-size: 22px; font-weight: 900; }
  .sidebar-logo .logo-text b { color: var(--accent); }
  .sidebar-logo .home-hint { font-size: 10px; color: var(--muted); display: block; margin-top: 3px; text-transform: uppercase; letter-spacing: 0.6px; }
  .sidebar-nav { flex: 1; padding: 16px 12px; display: flex; flex-direction: column; gap: 4px; overflow-y: auto; }
  .nav-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: 8px; cursor: pointer; color: var(--muted); font-size: 14px; font-weight: 500; transition: all 0.15s; border: none; background: none; width: 100%; text-align: left; }
  .nav-item:hover { background: var(--surface2); color: var(--text); }
  .nav-item.active { background: rgba(232,197,71,0.12); color: var(--accent); }
  .nav-icon { font-size: 16px; width: 20px; text-align: center; flex-shrink: 0; }
  .nav-badge { font-size: 11px; font-weight: 700; background: var(--accent); color: #0a0a0f; border-radius: 10px; padding: 2px 7px; }
  .sidebar-user { padding: 16px; border-top: 1px solid var(--border); }
  .user-info { display: flex; align-items: center; gap: 10px; }
  .user-avatar { width: 36px; height: 36px; border-radius: 50%; background: linear-gradient(135deg, var(--accent), var(--accent2)); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; color: #0a0a0f; flex-shrink: 0; }
  .user-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .user-email { font-size: 11px; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .main-content { margin-left: 240px; flex: 1; padding: 40px; min-height: 100vh; }
  .page-header { margin-bottom: 32px; }
  .page-title { font-family: var(--font-display); font-size: 28px; font-weight: 700; }
  .page-sub { color: var(--muted); font-size: 15px; margin-top: 6px; }

  .card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 28px; margin-bottom: 20px; }
  .card-title { font-size: 16px; font-weight: 600; margin-bottom: 20px; display: flex; align-items: center; gap: 10px; }
  .card-title span { font-size: 18px; }

  .home-hero { background: linear-gradient(135deg, rgba(232,197,71,0.08), rgba(78,205,196,0.06)); border: 1px solid rgba(232,197,71,0.15); border-radius: 16px; padding: 40px; margin-bottom: 28px; display: flex; justify-content: space-between; align-items: center; gap: 24px; flex-wrap: wrap; }
  .home-hero h1 { font-family: var(--font-display); font-size: 32px; font-weight: 900; margin-bottom: 10px; }
  .home-hero p { color: var(--muted); font-size: 16px; line-height: 1.6; max-width: 420px; }
  .home-stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 28px; }
  .stat-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 24px; text-align: center; }
  .stat-card-num { font-family: var(--font-display); font-size: 36px; font-weight: 700; }
  .stat-card-label { font-size: 13px; color: var(--muted); margin-top: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
  .home-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 28px; }
  .action-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 28px; cursor: pointer; transition: all 0.2s; }
  .action-card:hover { border-color: var(--accent); transform: translateY(-2px); box-shadow: 0 8px 32px rgba(0,0,0,0.3); }
  .action-card.teal:hover { border-color: var(--accent2); }
  .action-card-icon { font-size: 32px; margin-bottom: 14px; }
  .action-card-title { font-size: 18px; font-weight: 700; margin-bottom: 6px; font-family: var(--font-display); }
  .action-card-sub { font-size: 14px; color: var(--muted); line-height: 1.5; }

  .resumes-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
  .resume-card { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 20px; transition: all 0.15s; cursor: pointer; }
  .resume-card:hover { border-color: rgba(255,255,255,0.15); transform: translateY(-1px); }
  .resume-card-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 14px; }
  .resume-card-company { font-weight: 700; font-size: 16px; }
  .resume-card-position { font-size: 13px; color: var(--muted); margin-top: 3px; }
  .resume-card-score { font-family: var(--font-display); font-size: 28px; font-weight: 700; flex-shrink: 0; }
  .resume-card-score-label { font-size: 10px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.5px; text-align: right; }
  .resume-card-meta { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }
  .resume-card-chip { font-size: 11px; padding: 3px 8px; border-radius: 10px; background: var(--surface2); color: var(--muted); border: 1px solid var(--border); }
  .resume-card-actions { display: flex; gap: 8px; margin-top: 14px; }

  .stepper { display: flex; align-items: center; margin-bottom: 40px; flex-wrap: wrap; gap: 4px; }
  .step { display: flex; align-items: center; gap: 10px; cursor: pointer; padding: 8px 16px; border-radius: 24px; transition: all 0.2s; }
  .step.active { background: rgba(232,197,71,0.12); }
  .step-num { width: 28px; height: 28px; border-radius: 50%; background: var(--surface2); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; border: 1px solid var(--border); flex-shrink: 0; transition: all 0.2s; }
  .step.active .step-num { background: var(--accent); color: #0a0a0f; border-color: var(--accent); }
  .step.done .step-num { background: var(--success); color: #0a0a0f; border-color: var(--success); }
  .step-label { font-size: 13px; font-weight: 500; color: var(--muted); white-space: nowrap; }
  .step.active .step-label { color: var(--text); }
  .step-divider { flex: 1; height: 1px; background: var(--border); min-width: 16px; max-width: 32px; }

  .skills-grid { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
  .skill-chip { padding: 8px 14px; border-radius: 20px; border: 1px solid var(--border); font-size: 13px; cursor: pointer; transition: all 0.15s; user-select: none; background: var(--surface2); }
  .skill-chip:hover { border-color: var(--accent); }
  .skill-chip.selected { background: rgba(232,197,71,0.15); border-color: var(--accent); color: var(--accent); }
  .skill-chip.teal.selected { background: rgba(78,205,196,0.15); border-color: var(--accent2); color: var(--accent2); }

  .tab-bar { display: flex; gap: 4px; margin-bottom: 24px; background: var(--surface); border: 1px solid var(--border); padding: 4px; border-radius: 10px; width: fit-content; flex-wrap: wrap; }
  .tab { padding: 8px 18px; border-radius: 7px; border: none; background: none; color: var(--muted); font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.15s; }
  .tab.active { background: var(--surface2); color: var(--text); }

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
  .kw-section-title { font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.8px; margin: 16px 0 10px; }
  .breakdown-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 4px; }
  .breakdown-item { background: var(--surface2); border-radius: 10px; padding: 14px 16px; }
  .breakdown-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.6px; color: var(--muted); margin-bottom: 8px; }
  .breakdown-bar-bg { height: 6px; background: var(--border); border-radius: 3px; overflow: hidden; }
  .breakdown-bar-fill { height: 100%; border-radius: 3px; transition: width 0.6s ease; }
  .breakdown-score-val { font-size: 20px; font-weight: 700; margin-bottom: 4px; }

  .generating { display: flex; flex-direction: column; align-items: center; padding: 60px; gap: 20px; }
  .spinner { width: 48px; height: 48px; border: 3px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: spin 0.8s linear infinite; }
  .spinner-sm { width: 20px; height: 20px; border-width: 2px; }
  @keyframes spin { to { transform: rotate(360deg); } }
  .prog-steps { display: flex; flex-direction: column; gap: 10px; width: 320px; }
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

  .upload-zone { border: 2px dashed var(--border); border-radius: var(--radius); padding: 32px 24px; text-align: center; cursor: pointer; transition: all 0.2s; background: var(--surface2); position: relative; }
  .upload-zone:hover, .upload-zone.drag-over { border-color: var(--accent); background: rgba(232,197,71,0.05); }
  .upload-zone input[type="file"] { position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; height: 100%; }
  .upload-formats { display: flex; gap: 8px; justify-content: center; margin-top: 12px; flex-wrap: wrap; }
  .format-chip { padding: 4px 10px; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); font-size: 11px; color: var(--muted); font-weight: 600; letter-spacing: 0.5px; }
  .parse-result-banner { display: flex; align-items: center; gap: 14px; padding: 14px 20px; border-radius: var(--radius); background: rgba(78,205,196,0.08); border: 1px solid rgba(78,205,196,0.3); margin-bottom: 20px; }
  .parse-field-pill { display: inline-flex; align-items: center; gap: 5px; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500; background: rgba(78,205,196,0.1); border: 1px solid rgba(78,205,196,0.2); color: var(--accent2); }

  .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.75); z-index: 200; display: flex; align-items: center; justify-content: center; padding: 24px; backdrop-filter: blur(4px); }
  .modal { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; width: 100%; max-width: 720px; max-height: 90vh; overflow-y: auto; padding: 32px; position: relative; }
  .modal-close { position: absolute; top: 16px; right: 16px; background: var(--surface2); border: 1px solid var(--border); color: var(--muted); border-radius: 8px; padding: 8px 12px; cursor: pointer; font-size: 16px; transition: all 0.15s; }
  .modal-close:hover { color: var(--text); border-color: var(--accent); }

  @media (max-width: 900px) {
    .auth-page { grid-template-columns: 1fr; }
    .auth-brand { display: none; }
    .profile-grid, .home-actions, .home-stats-row, .breakdown-grid { grid-template-columns: 1fr; }
    .sidebar { width: 200px; }
    .main-content { margin-left: 200px; padding: 24px; }
  }
`;

// ─── Skill categories ─────────────────────────────────────────────────────────
const SKILL_CATEGORIES = {
  "Programming Languages": ["JavaScript", "TypeScript", "Python", "Java", "C++", "C#", "Go", "Rust", "Swift", "Kotlin", "PHP", "Ruby", "Scala", "R"],
  "Frontend": ["React", "Vue.js", "Angular", "Next.js", "Svelte", "HTML5", "CSS3", "Tailwind CSS", "SASS", "Redux", "GraphQL"],
  "Backend": ["Node.js", "Express.js", "Django", "FastAPI", "Spring Boot", "Laravel", "Ruby on Rails", "REST APIs", "Microservices"],
  "Cloud & DevOps": ["AWS", "Azure", "GCP", "Docker", "Kubernetes", "Terraform", "CI/CD", "Jenkins", "GitHub Actions", "Linux"],
  "Databases": ["PostgreSQL", "MySQL", "MongoDB", "Redis", "DynamoDB", "Elasticsearch", "SQLite", "Cassandra"],
  "Data & AI": ["Machine Learning", "Deep Learning", "TensorFlow", "PyTorch", "Pandas", "NumPy", "Scikit-learn", "Data Analysis", "Power BI", "Tableau"],
  "Soft Skills": ["Leadership", "Communication", "Problem Solving", "Team Collaboration", "Agile", "Scrum", "Project Management", "Mentoring"],
};

// ─── Shared UI ────────────────────────────────────────────────────────────────
function Alert({ type = "error", children }) {
  return <div className={`alert alert-${type}`}>{children}</div>;
}
function Spinner({ small }) {
  return <span className={`spinner${small ? " spinner-sm" : ""}`} style={small ? { display: "inline-block" } : {}} />;
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
// ✅ FIX: Added onForgotPassword to props
function LoginPage({ onLogin, onSwitch, verifiedMsg, onForgotPassword }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async () => {
    setError(""); setLoading(true);
    try {
      const data = await api.login(email, password);
      localStorage.setItem("ats_token", data.token || data.accessToken);
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
          <div className="field">
            <label>Email Address</label>
            <input type="email" placeholder="you@example.com" value={email} onChange={e => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label>Password</label>
            <input type="password" placeholder="••••••••" value={password} onChange={e => setPassword(e.target.value)} onKeyDown={e => e.key === "Enter" && handleSubmit()} />
          </div>
          {/* ✅ FIX: Uses prop instead of undefined variable, no page reload */}
          <div style={{ textAlign: "right", marginBottom: 14 }}>
            <button
              style={{ background: "none", border: "none", color: "var(--accent)", cursor: "pointer", fontSize: 13 }}
              onClick={onForgotPassword}
            >
              Forgot Password?
            </button>
          </div>
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
    try { await api.signup(form.fullName, form.email, form.password); setSuccess(true); }
    catch (e) { setError(e.message); }
    setLoading(false);
  };

  if (success) return (
    <div className="auth-page">
      <AuthBrand />
      <div className="auth-form-side">
        <div className="auth-card">
          <h1 className="auth-title">Check your inbox</h1>
          <p className="auth-sub">Verification email sent to <strong>{form.email}</strong></p>
          <Alert type="success">✓ Account created! Click the link in your email to verify.</Alert>
          <Alert type="info">💡 Check spam if not received. Link also appears in Spring Boot logs.</Alert>
          <div style={{ textAlign: "center", marginTop: 20 }}><button className="btn btn-ghost" onClick={onSwitch}>Back to Sign In</button></div>
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

// ─── Forgot Password ──────────────────────────────────────────────────────────
function ForgotPasswordPage({ onBack }) {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  const submit = async () => {
    setLoading(true);
    setError("");
    try {
      await api.forgotPassword(email);
      setSent(true);
    } catch (e) {
      setError(e.message);
    }
    setLoading(false);
  };

  if (sent) {
    return (
      <div className="auth-page">
        <AuthBrand />
        <div className="auth-form-side">
          <div className="auth-card">
            <h1 className="auth-title">Check your email</h1>
            <Alert type="success">Reset link sent to {email}</Alert>
            <button className="btn btn-primary" onClick={onBack}>Back to Login</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <AuthBrand />
      <div className="auth-form-side">
        <div className="auth-card">
          <h1 className="auth-title">Forgot Password</h1>
          <p className="auth-sub">Enter your email to receive a reset link</p>
          {error && <Alert>{error}</Alert>}
          <div className="field">
            <label>Email Address</label>
            <input
              type="email"
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              onKeyDown={e => e.key === "Enter" && email && submit()}
            />
          </div>
          <button className="btn btn-primary" disabled={!email || loading} onClick={submit}>
            {loading ? <><Spinner small /> Sending...</> : "Send Reset Link"}
          </button>
          <div className="auth-switch">
            <button onClick={onBack}>← Back to Login</button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Reset Password Page ──────────────────────────────────────────────────────

function ResetPasswordPage({ onBack }) {
  // ✅ Read token and email from URL query params
  // Email link format: http://localhost:3000/?page=reset-password&token=xxx&email=yyy
  const params = new URLSearchParams(window.location.search);
  const token = params.get("token");
  const email = params.get("email");

  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // ✅ Invalid/missing token — show error with full styled layout
  if (!token || !email) {
    return (
      <div className="auth-page">
        <AuthBrand />
        <div className="auth-form-side">
          <div className="auth-card">
            <h1 className="auth-title">Invalid Link</h1>
            <Alert>This reset link is invalid or has expired. Please request a new one.</Alert>
            <button className="btn btn-primary" onClick={onBack}>Back to Login</button>
          </div>
        </div>
      </div>
    );
  }

  const submit = async () => {
    if (password !== confirm) { setError("Passwords do not match"); return; }
    if (password.length < 6) { setError("Password must be at least 6 characters"); return; }
    setLoading(true);
    setError("");
    try {
      await api.resetPassword(email, token, password);
      setSuccess(true);
    } catch (e) {
      setError(e.message);
    }
    setLoading(false);
  };

  // ✅ Success state
  if (success) {
    return (
      <div className="auth-page">
        <AuthBrand />
        <div className="auth-form-side">
          <div className="auth-card">
            <h1 className="auth-title">Password Reset!</h1>
            <Alert type="success">Your password has been updated successfully.</Alert>
            <button className="btn btn-primary" onClick={onBack}>Sign In →</button>
          </div>
        </div>
      </div>
    );
  }

  // ✅ Reset form
  return (
    <div className="auth-page">
      <AuthBrand />
      <div className="auth-form-side">
        <div className="auth-card">
          <h1 className="auth-title">Reset Password</h1>
          <p className="auth-sub">Choose a new password for <strong>{email}</strong></p>
          {error && <Alert>{error}</Alert>}
          <div className="field">
            <label>New Password</label>
            <input
              type="password"
              placeholder="Min 6 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <div className="field">
            <label>Confirm Password</label>
            <input
              type="password"
              placeholder="••••••••"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              onKeyDown={e => e.key === "Enter" && password && confirm && submit()}
            />
          </div>
          <button
            className="btn btn-primary"
            disabled={!password || !confirm || loading}
            onClick={submit}
          >
            {loading ? <><Spinner small /> Resetting...</> : "Reset Password"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Profile Stepper ──────────────────────────────────────────────────────────
const STEPS = [
  { id: "personal", label: "Personal", icon: "👤" },
  { id: "experience", label: "Experience", icon: "🏢" },
  { id: "education", label: "Education", icon: "🎓" },
  { id: "skills", label: "Skills", icon: "⚡" },
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

function ExperienceStep({ data, onChange, onNext, onBack }) {
  const empty = () => ({ company: "", role: "", location: "", startDate: "", endDate: "", current: false, description: "" });
  const companies = data.companies?.length ? data.companies : [empty()];
  const update = (i, field, val) => onChange({ ...data, companies: companies.map((c, idx) => idx === i ? { ...c, [field]: val } : c) });
  const add = () => onChange({ ...data, companies: [...companies, empty()] });
  const remove = i => onChange({ ...data, companies: companies.filter((_, idx) => idx !== i) });

  return (
    <div>
      <div className="card">
        <div className="card-title"><span>🏢</span> Work Experience</div>
        {companies.map((c, i) => (
          <div key={i} style={{ borderBottom: i < companies.length - 1 ? "1px solid var(--border)" : "none", paddingBottom: 28, marginBottom: 28 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
              <span style={{ fontSize: 12, fontWeight: 700, color: "var(--accent)", textTransform: "uppercase", letterSpacing: "0.6px" }}>Position {i + 1}</span>
              {companies.length > 1 && <button className="btn btn-ghost btn-sm" onClick={() => remove(i)}>✕ Remove</button>}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px" }}>
              <div className="field"><label>Company *</label><input value={c.company} onChange={e => update(i, "company", e.target.value)} placeholder="Acme Corp" /></div>
              <div className="field"><label>Job Title *</label><input value={c.role} onChange={e => update(i, "role", e.target.value)} placeholder="Software Engineer" /></div>
            </div>
            <div className="field"><label>Location</label><input value={c.location || ""} onChange={e => update(i, "location", e.target.value)} placeholder="Dallas, TX / Remote / New York, NY (Hybrid)" /></div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px", marginBottom: 8 }}>
              <div><span className="date-label">Start Date</span><MonthYearPicker value={c.startDate || ""} onChange={val => update(i, "startDate", val)} /></div>
              <div>
                <span className="date-label">End Date</span>
                {c.current ? <div className="present-pill">📌 Currently working here</div> : <MonthYearPicker value={c.endDate || ""} onChange={val => update(i, "endDate", val)} />}
                <label style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 8, cursor: "pointer", fontSize: 13, color: "var(--muted)" }}>
                  <input type="checkbox" checked={!!c.current} onChange={e => update(i, "current", e.target.checked)} style={{ width: 14, height: 14 }} />
                  I currently work here
                </label>
              </div>
            </div>
            <div className="field" style={{ marginTop: 8 }}>
              <label>Key Achievements & Responsibilities</label>
              <textarea value={c.description} onChange={e => update(i, "description", e.target.value)} placeholder={"• Led team of 5 engineers...\n• Built system that improved performance by 40%...\n• Deployed microservices on AWS EKS..."} rows={5} />
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

function EducationStep({ data, onChange, onNext, onBack }) {
  const empty = () => ({ institution: "", degree: "", field: "", location: "", year: "", gpa: "" });
  const edu = data.education?.length ? data.education : [empty()];
  const update = (i, field, val) => onChange({ ...data, education: edu.map((e, idx) => idx === i ? { ...e, [field]: val } : e) });
  const add = () => onChange({ ...data, education: [...edu, empty()] });
  const remove = i => onChange({ ...data, education: edu.filter((_, idx) => idx !== i) });

  return (
    <div>
      <div className="card">
        <div className="card-title"><span>🎓</span> Education</div>
        {edu.map((e, i) => (
          <div key={i} style={{ borderBottom: i < edu.length - 1 ? "1px solid var(--border)" : "none", paddingBottom: 20, marginBottom: 20 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--muted)" }}>Degree {i + 1}</span>
              {edu.length > 1 && <button className="btn btn-ghost btn-sm" onClick={() => remove(i)}>✕ Remove</button>}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px" }}>
              <div className="field"><label>Institution *</label><input value={e.institution} onChange={ev => update(i, "institution", ev.target.value)} placeholder="MIT" /></div>
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
              <div className="field"><label>Field of Study</label><input value={e.field} onChange={ev => update(i, "field", ev.target.value)} placeholder="Computer Science" /></div>
              <div className="field"><label>Location (City, ST)</label><input value={e.location || ""} onChange={ev => update(i, "location", ev.target.value)} placeholder="Cambridge, MA" /></div>
              <div className="field"><label>Graduation Year</label><input type="number" value={e.year} onChange={ev => update(i, "year", ev.target.value)} placeholder="2020" /></div>
              <div className="field"><label>GPA (Optional)</label><input value={e.gpa || ""} onChange={ev => update(i, "gpa", ev.target.value)} placeholder="3.8 / 4.0" /></div>
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

function SkillsStep({ data, onChange, onNext, onBack }) {
  const selected = data.skills || [];
  const [activeTab, setActiveTab] = useState(Object.keys(SKILL_CATEGORIES)[0]);
  const [custom, setCustom] = useState("");
  const toggle = skill => onChange({ ...data, skills: selected.includes(skill) ? selected.filter(s => s !== skill) : [...selected, skill] });
  const addCustom = () => { if (custom.trim() && !selected.includes(custom.trim())) { onChange({ ...data, skills: [...selected, custom.trim()] }); setCustom(""); } };

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
          <div className="field" style={{ flex: 1, margin: 0 }}><input value={custom} onChange={e => setCustom(e.target.value)} placeholder="Add custom skill..." onKeyDown={e => e.key === "Enter" && addCustom()} /></div>
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

function CertificationsStep({ data, onChange, onSave, onBack, saving }) {
  const certs = data.certifications || [{ name: "", issuer: "", year: "", url: "" }];
  const update = (i, field, val) => onChange({ ...data, certifications: certs.map((c, idx) => idx === i ? { ...c, [field]: val } : c) });
  const add = () => onChange({ ...data, certifications: [...certs, { name: "", issuer: "", year: "", url: "" }] });
  const remove = i => onChange({ ...data, certifications: certs.filter((_, idx) => idx !== i) });

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

// ─── Resume Upload Zone ───────────────────────────────────────────────────────
function ResumeUploadZone({ onParsed }) {
  const [dragging, setDragging] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [parseStep, setParseStep] = useState(0);
  const [error, setError] = useState("");

  const PARSE_STEPS = [
    "Reading your resume…",
    "Extracting work experience…",
    "Pulling skills & education…",
    "Finalising profile fields…",
  ];

  const handleFile = async (file) => {
    if (!file) return;
    const ext = file.name.split(".").pop().toLowerCase();
    if (!["pdf", "docx", "doc", "txt"].includes(ext)) {
      setError("Please upload a PDF, Word (.docx/.doc), or .txt file."); return;
    }
    setParsing(true); setParseStep(0); setError("");
    const ticker = setInterval(() => setParseStep(p => Math.min(p + 1, PARSE_STEPS.length - 1)), 1800);
    try {
      const data = await api.parseResume(file);
      clearInterval(ticker);
      onParsed(data);
    } catch (e) {
      clearInterval(ticker);
      setError(e.message || "Failed to parse resume. You can fill the form manually instead.");
      setParsing(false);
    }
  };

  if (parsing) {
    return (
      <div style={{ textAlign: "center", padding: "32px 0" }}>
        <div className="spinner" style={{ margin: "0 auto 20px", width: 40, height: 40 }} />
        <p style={{ fontFamily: "var(--font-display)", fontSize: 20, fontWeight: 700, marginBottom: 8 }}>Analysing your resume…</p>
        <p style={{ color: "var(--accent)", fontSize: 14, minHeight: 20 }}>{PARSE_STEPS[parseStep]}</p>
        <div style={{ display: "flex", flexDirection: "column", gap: 8, marginTop: 24, maxWidth: 280, margin: "24px auto 0" }}>
          {PARSE_STEPS.map((s, i) => (
            <div key={s} className={`prog-step ${i < parseStep ? "done" : i === parseStep ? "active" : ""}`}>
              <div className="prog-dot" />
              <span style={{ fontSize: 13 }}>{s}</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div>
      {error && <Alert>{error}</Alert>}
      <div
        className={`upload-zone ${dragging ? "drag-over" : ""}`}
        style={{ padding: "48px 32px" }}
        onDragOver={e => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={e => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]); }}
      >
        <input type="file" accept=".pdf,.docx,.doc,.txt" onChange={e => handleFile(e.target.files[0])} />
        <div style={{ fontSize: 44, marginBottom: 14 }}>📄</div>
        <div style={{ fontSize: 17, fontWeight: 700, marginBottom: 8 }}>Drop your resume here or click to browse</div>
        <div style={{ fontSize: 14, color: "var(--muted)", marginBottom: 16 }}>AI will extract all your details and auto-fill the form</div>
        <div className="upload-formats">
          {["PDF", "DOCX", "DOC", "TXT"].map(f => <span key={f} className="format-chip">{f}</span>)}
        </div>
      </div>
    </div>
  );
}

// ─── Profile Builder ──────────────────────────────────────────────────────────
function ProfileBuilder({ session, initialProfile, onComplete, onCancel }) {
  const [phase, setPhase] = useState(initialProfile ? "steps" : "upload");
  const [currentStep, setCurrentStep] = useState("personal");
  const [completedSteps, setCompletedSteps] = useState([]);
  const [profile, setProfile] = useState({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [parsedData, setParsedData] = useState(null);

  useEffect(() => {
    if (initialProfile) {
      setProfile(initialProfile);
      setPhase("steps");
      const completed = [];
      if (initialProfile.name) completed.push("personal");
      if (initialProfile.companies?.length) completed.push("experience");
      if (initialProfile.education?.length) completed.push("education");
      if (initialProfile.skills?.length) completed.push("skills");
      if (initialProfile.certifications?.length) completed.push("certifications");
      setCompletedSteps(completed);
    }
  }, [initialProfile]);

  const handleParsed = (data) => {
    setParsedData(data);
    setProfile(prev => ({
      ...prev,
      name: data.name || prev.name || "",
      headline: data.headline || prev.headline || "",
      email: data.email || prev.email || "",
      phone: data.phone || prev.phone || "",
      location: data.location || prev.location || "",
      linkedin: data.linkedin || prev.linkedin || "",
      github: data.github || prev.github || "",
      website: data.website || prev.website || "",
      summary: data.summary || prev.summary || "",
      companies: (data.companies?.length ? data.companies : null) || prev.companies || [],
      education: (data.education?.length ? data.education : null) || prev.education || [],
      skills: (data.skills?.length ? data.skills : null) || prev.skills || [],
      certifications: (data.certifications?.length ? data.certifications : null) || prev.certifications || [],
    }));
    setCurrentStep("personal");
    setCompletedSteps([]);
    setPhase("steps");
  };

  const stepIndex = STEPS.findIndex(s => s.id === currentStep);
  const goNext = () => {
    const next = STEPS[stepIndex + 1];
    if (next) { setCompletedSteps(prev => [...new Set([...prev, currentStep])]); setCurrentStep(next.id); }
  };
  const goBack = () => {
    if (stepIndex === 0 && !initialProfile) { setPhase("upload"); return; }
    const prev = STEPS[stepIndex - 1]; if (prev) setCurrentStep(prev.id);
  };

  const saveProfile = async () => {
    setSaving(true); setError("");
    try {
      await api.saveProfile(JSON.stringify(profile));
      onComplete(profile);
    } catch (e) { setError(e.message); }
    setSaving(false);
  };

  const FIELD_LABELS = { name: "Name", email: "Email", phone: "Phone", location: "Location", headline: "Headline", summary: "Summary", companies: "Experience", education: "Education", skills: "Skills", certifications: "Certifications" };
  const filledFields = parsedData ? Object.entries(parsedData).filter(([k, v]) => Array.isArray(v) ? v.length > 0 : v && String(v).trim()).map(([k]) => FIELD_LABELS[k] || k) : [];

  if (phase === "upload") {
    return (
      <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", background: "var(--bg)", padding: "40px 20px" }}>
        <div style={{ width: "100%", maxWidth: 560 }}>
          <div style={{ textAlign: "center", marginBottom: 36 }}>
            <div style={{ fontFamily: "var(--font-display)", fontSize: 36, fontWeight: 900, marginBottom: 12 }}>
              Ré<b style={{ color: "var(--accent)" }}>su</b>méAI
            </div>
            <h1 style={{ fontFamily: "var(--font-display)", fontSize: 26, fontWeight: 700, marginBottom: 8 }}>Build Your Profile</h1>
            <p style={{ color: "var(--muted)", fontSize: 15, lineHeight: 1.6 }}>Upload your existing resume to auto-fill all fields — or start from scratch.</p>
          </div>
          <div className="card" style={{ marginBottom: 16 }}>
            <ResumeUploadZone onParsed={handleParsed} />
          </div>
          <button className="btn btn-ghost" style={{ width: "100%" }} onClick={() => { setProfile({}); setCurrentStep("personal"); setCompletedSteps([]); setPhase("steps"); }}>
            ✏️ Fill in manually instead
          </button>
          {onCancel && (
            <div style={{ textAlign: "center", marginTop: 12 }}>
              <button className="btn btn-ghost btn-sm" onClick={onCancel}>✕ Cancel</button>
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="main-content" style={{ marginLeft: 0 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 32 }}>
        <div>
          <h1 className="page-title">Build Your Profile</h1>
          <p className="page-sub">Complete your profile to unlock AI-powered resume generation</p>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          {!initialProfile && (
            <button className="btn btn-ghost btn-sm" onClick={() => setPhase("upload")} title="Go back to upload screen">📄 Re-upload</button>
          )}
          {onCancel && <button className="btn btn-ghost btn-sm" onClick={onCancel}>✕ Cancel</button>}
        </div>
      </div>

      {error && <Alert>{error}</Alert>}

      {parsedData && (
        <div className="parse-result-banner" style={{ marginBottom: 20 }}>
          <span style={{ fontSize: 28 }}>✅</span>
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 600, color: "var(--accent2)", marginBottom: 4 }}>Resume parsed — {filledFields.length} fields auto-filled</div>
            <div style={{ fontSize: 13, color: "var(--muted)" }}>Review and edit each section below, then save your profile.</div>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 8 }}>
              {filledFields.map(f => <span key={f} className="parse-field-pill">✓ {f}</span>)}
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={() => { setParsedData(null); setProfile({}); setCompletedSteps([]); setCurrentStep("personal"); setPhase("upload"); }}>✕ Clear</button>
        </div>
      )}

      <StepperBar currentStep={currentStep} completedSteps={completedSteps} onNavigate={setCurrentStep} />

      {currentStep === "personal" && <PersonalStep data={profile} onChange={setProfile} onNext={goNext} />}
      {currentStep === "experience" && <ExperienceStep data={profile} onChange={setProfile} onNext={goNext} onBack={goBack} />}
      {currentStep === "education" && <EducationStep data={profile} onChange={setProfile} onNext={goNext} onBack={goBack} />}
      {currentStep === "skills" && <SkillsStep data={profile} onChange={setProfile} onNext={goNext} onBack={goBack} />}
      {currentStep === "certifications" && <CertificationsStep data={profile} onChange={setProfile} onSave={saveProfile} onBack={goBack} saving={saving} />}
    </div>
  );
}

// ─── Scoring Breakdown ────────────────────────────────────────────────────────
function ScoringBreakdown({ breakdown }) {
  if (!breakdown) return null;
  const dims = [
    { label: "Keyword Match", key: "keywordMatch", weight: "40%" },
    { label: "Candidate Fit", key: "candidateFit", weight: "25%" },
    { label: "Resume Completeness", key: "resumeCompleteness", weight: "20%" },
    { label: "Keyword Density", key: "keywordDensity", weight: "15%" },
  ];
  return (
    <div className="card">
      <div className="card-title"><span>📊</span> Score Breakdown</div>
      <div className="breakdown-grid">
        {dims.map(d => {
          const val = breakdown[d.key] || 0;
          const col = scoreColor(val);
          return (
            <div key={d.key} className="breakdown-item">
              <div className="breakdown-label">{d.label} <span style={{ color: "var(--accent)", opacity: 0.7 }}>({d.weight})</span></div>
              <div className="breakdown-score-val" style={{ color: col }}>{val}</div>
              <div className="breakdown-bar-bg"><div className="breakdown-bar-fill" style={{ width: `${val}%`, background: col }} /></div>
            </div>
          );
        })}
      </div>
      {breakdown.notes && (
        <div style={{ marginTop: 14, padding: "12px 16px", background: "var(--surface2)", borderRadius: 8, fontSize: 13, color: "var(--muted)", lineHeight: 1.6, borderLeft: "3px solid var(--accent)" }}>
          💬 {breakdown.notes}
        </div>
      )}
    </div>
  );
}

// ─── Resume Generator ─────────────────────────────────────────────────────────
const GEN_STEPS_LIST = [
  "Analyzing job description for technical keywords...",
  "Mapping your profile to role requirements...",
  "Generating tailored resume content...",
  "Enriching skills and experience bullets...",
  "Calculating holistic ATS score...",
  "Rendering PDF...",
];

function ResumeGenerator({ profile, onSaveResume, prefillCompany = "", prefillPosition = "" }) {
  const [company, setCompany] = useState(prefillCompany);
  const [position, setPosition] = useState(prefillPosition);
  const [jd, setJd] = useState("");
  const [step, setStep] = useState("meta");
  const [genStep, setGenStep] = useState(0);
  const [atsResult, setAtsResult] = useState(null);
  const [pdfBlob, setPdfBlob] = useState(null);
  const [error, setError] = useState("");

  const startGenerate = async () => {
    if (!jd.trim()) return;
    setStep("generate"); setGenStep(0); setError(""); setAtsResult(null); setPdfBlob(null);
    try {
      const intervalId = setInterval(() => {
        setGenStep(prev => prev < GEN_STEPS_LIST.length - 1 ? prev + 1 : prev);
      }, 2000);
      const result = await api.generateResumeWithScore(
        { ...profile, targetCompany: company, targetPosition: position }, jd
      );
      clearInterval(intervalId);
      setGenStep(GEN_STEPS_LIST.length);

      const binaryStr = atob(result.pdfBase64);
      const bytes = new Uint8Array(binaryStr.length);
      for (let i = 0; i < binaryStr.length; i++) bytes[i] = binaryStr.charCodeAt(i);
      const blob = new Blob([bytes], { type: "application/pdf" });

      const ats = {
        atsScore: result.atsScore,
        scoreLabel: result.scoreLabel,
        matchedSkills: result.matchedSkills,
        totalSkills: result.totalSkills,
        matchedKeywords: result.matchedKeywords || [],
        missingKeywords: result.missingKeywords || [],
        scoringBreakdown: result.scoringBreakdown || null,
      };
      setAtsResult(ats);
      setPdfBlob(blob);
      setStep("done");

      onSaveResume({
        id: Date.now().toString(),
        company, position,
        generatedAt: new Date().toISOString(),
        atsScore: result.atsScore,
        scoreLabel: result.scoreLabel,
        matchedKeywords: result.matchedKeywords || [],
        missingKeywords: result.missingKeywords || [],
        scoringBreakdown: result.scoringBreakdown || null,
        pdfBase64: result.pdfBase64,
      });
    } catch (e) { setError(e.message); setStep("meta"); }
  };

  const downloadPdf = () => {
    const url = URL.createObjectURL(pdfBlob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${(profile.name || "Resume").replace(/\s+/g, "_")}_${company || "ATS"}_Optimized.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const reset = () => { setStep("meta"); setAtsResult(null); setPdfBlob(null); setJd(""); setGenStep(0); setError(""); setCompany(""); setPosition(""); };

  if (step === "generate") {
    return (
      <div className="card generating">
        <div className="spinner" />
        <p style={{ fontFamily: "var(--font-display)", fontSize: 22, fontWeight: 700 }}>Generating Your Resume</p>
        <p style={{ color: "var(--muted)", fontSize: 14 }}>{company && position ? `Tailoring for ${position} at ${company}` : "AI is crafting your ATS-optimized resume"}</p>
        <div className="prog-steps">
          {GEN_STEPS_LIST.map((s, i) => (
            <div key={i} className={`prog-step ${i < genStep ? "done" : i === genStep ? "active" : ""}`}>
              <div className="prog-dot" />{i < genStep ? `✓ ${s}` : s}
            </div>
          ))}
        </div>
        <p style={{ color: "var(--muted)", fontSize: 13 }}>This may take 30–60 seconds…</p>
      </div>
    );
  }

  if (step === "done" && atsResult) {
    const sc = atsResult.atsScore;
    const col = scoreColor(sc);
    const bg = scoreBg(sc);
    const contextMsg = sc >= 90 ? "Outstanding — your resume is highly competitive for this role." :
      sc >= 75 ? "Strong match. Weaving in the missing keywords could push you to Excellent." :
        sc >= 55 ? "Reasonable fit. Adding the missing skills and regenerating will improve your score." :
          "Significant skill gap for this role. Focus on building the missing technical skills.";

    return (
      <div>
        {company && position && (
          <div style={{ marginBottom: 20, padding: "12px 20px", background: "var(--surface2)", borderRadius: 10, display: "flex", alignItems: "center", gap: 12 }}>
            <span style={{ fontSize: 20 }}>🎯</span>
            <div>
              <div style={{ fontWeight: 600 }}>{position}</div>
              <div style={{ fontSize: 13, color: "var(--muted)" }}>{company}</div>
            </div>
          </div>
        )}
        <div className="score-display" style={{ background: bg }}>
          <div className="score-ring" style={{ background: `conic-gradient(${col} ${sc * 3.6}deg, var(--surface2) 0)` }}>
            <span className="score-num" style={{ color: col }}>{sc}</span>
          </div>
          <div className="score-info" style={{ flex: 1 }}>
            <h3>ATS Score: <span style={{ color: col }}>{atsResult.scoreLabel}</span></h3>
            <p className="score-desc" style={{ marginBottom: 6 }}>Matched <strong style={{ color: col }}>{atsResult.matchedSkills}</strong> of <strong>{atsResult.totalSkills}</strong> technical keywords.</p>
            <p className="score-desc">{contextMsg}</p>
          </div>
        </div>

        <ScoringBreakdown breakdown={atsResult.scoringBreakdown} />

        <div className="card">
          <div className="card-title"><span>🎯</span> Keyword Match Analysis</div>
          <div style={{ display: "flex", gap: 28, marginBottom: 20, flexWrap: "wrap" }}>
            {[
              { label: "Matched", val: atsResult.matchedKeywords.length, col: "var(--success)" },
              { label: "Missing", val: atsResult.missingKeywords.length, col: "var(--danger)" },
              { label: "Total in JD", val: atsResult.totalSkills, col: "var(--accent)" },
            ].map(({ label, val, col: c }) => (
              <div key={label} style={{ textAlign: "center" }}>
                <div style={{ fontSize: 28, fontWeight: 700, color: c }}>{val}</div>
                <div style={{ fontSize: 11, color: "var(--muted)", textTransform: "uppercase", letterSpacing: "0.5px" }}>{label}</div>
              </div>
            ))}
          </div>
          {atsResult.matchedKeywords.length > 0 && (
            <><div className="kw-section-title">✓ Found in your resume</div>
              <div className="skills-match">{atsResult.matchedKeywords.map(s => <span key={s} className="match-chip hit">✓ {s}</span>)}</div></>
          )}
          {atsResult.missingKeywords.length > 0 && (
            <><div className="kw-section-title" style={{ marginTop: 20 }}>✕ Not in your resume</div>
              <div className="skills-match">{atsResult.missingKeywords.map(s => <span key={s} className="match-chip miss">{s}</span>)}</div>
              <p style={{ fontSize: 13, color: "var(--muted)", marginTop: 12, lineHeight: 1.6 }}>💡 Add missing skills to your profile and regenerate to improve your score.</p></>
          )}
        </div>

        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
          <button className="download-btn" onClick={downloadPdf}>⬇ Download ATS-Optimized PDF</button>
          <button className="btn btn-ghost" onClick={reset}>Try Another Job</button>
        </div>
      </div>
    );
  }

  return (
    <div>
      {error && <Alert>{error}</Alert>}
      <div className="card">
        <div className="card-title"><span>🎯</span> Target Role</div>
        <p style={{ color: "var(--muted)", fontSize: 14, marginBottom: 20 }}>Tell us where you're applying. This lets us tailor the resume specifically for this company and role.</p>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 20px" }}>
          <div className="field"><label>Company Name *</label><input value={company} onChange={e => setCompany(e.target.value)} placeholder="Google, Amazon, Stripe..." /></div>
          <div className="field"><label>Position / Role *</label><input value={position} onChange={e => setPosition(e.target.value)} placeholder="Senior Software Engineer" /></div>
        </div>
      </div>
      <div className="card">
        <div className="card-title"><span>📋</span> Job Description</div>
        <p style={{ color: "var(--muted)", fontSize: 14, marginBottom: 16 }}>Paste the full job description. The AI will extract keywords, build your tailored resume, and calculate a realistic ATS score.</p>
        <div className="field">
          <label>Job Description *</label>
          <textarea value={jd} onChange={e => setJd(e.target.value)} placeholder="Paste the full job description here..." rows={14} />
        </div>
      </div>
      <div className="card">
        <div className="card-title"><span>👤</span> Profile Summary</div>
        <div className="profile-grid">
          {[["Name", profile.name], ["Headline", profile.headline || "—"], ["Location", profile.location],
          ["Skills", `${(profile.skills || []).length} selected`],
          ["Experience", `${(profile.companies || []).filter(c => c.company).length} positions`],
          ["Certifications", `${(profile.certifications || []).filter(c => c.name).length}`]
          ].map(([k, v]) => (
            <div key={k} className="info-row"><span className="info-label">{k}</span><span className="info-val">{v}</span></div>
          ))}
        </div>
      </div>
      <button
        className="btn btn-primary"
        style={{ width: "auto", padding: "16px 40px", fontSize: 16 }}
        disabled={!jd.trim() || !company.trim() || !position.trim()}
        onClick={startGenerate}
      >
        🤖 Generate ATS-Optimized Resume
      </button>
      {(!jd.trim() || !company.trim() || !position.trim()) && (
        <p style={{ color: "var(--muted)", fontSize: 13, marginTop: 10 }}>Fill in company name, position, and job description to continue</p>
      )}
    </div>
  );
}

// ─── Resume Detail Modal ──────────────────────────────────────────────────────
function ResumeDetailModal({ resume, onClose }) {
  const sc = resume.atsScore;
  const col = scoreColor(sc);
  const bg = scoreBg(sc);

  const downloadPdf = () => {
    const binaryStr = atob(resume.pdfBase64);
    const bytes = new Uint8Array(binaryStr.length);
    for (let i = 0; i < binaryStr.length; i++) bytes[i] = binaryStr.charCodeAt(i);
    const blob = new Blob([bytes], { type: "application/pdf" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `Resume_${resume.company}_${resume.position}.pdf`.replace(/\s+/g, "_");
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="modal-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal">
        <button className="modal-close" onClick={onClose}>✕</button>
        <div style={{ marginBottom: 24 }}>
          <div style={{ fontFamily: "var(--font-display)", fontSize: 22, fontWeight: 700, marginBottom: 6 }}>{resume.position}</div>
          <div style={{ fontSize: 16, color: "var(--muted)" }}>{resume.company}</div>
          <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 4 }}>
            Generated {new Date(resume.generatedAt).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })}
          </div>
        </div>
        <div className="score-display" style={{ background: bg, marginBottom: 20 }}>
          <div className="score-ring" style={{ background: `conic-gradient(${col} ${sc * 3.6}deg, var(--surface2) 0)` }}>
            <span className="score-num" style={{ color: col }}>{sc}</span>
          </div>
          <div className="score-info" style={{ flex: 1 }}>
            <h3>ATS Score: <span style={{ color: col }}>{resume.scoreLabel}</span></h3>
            <p className="score-desc">Matched {resume.matchedKeywords?.length || 0} keywords from the job description.</p>
          </div>
        </div>
        <ScoringBreakdown breakdown={resume.scoringBreakdown} />
        {(resume.matchedKeywords?.length > 0 || resume.missingKeywords?.length > 0) && (
          <div className="card">
            <div className="card-title"><span>🎯</span> Keywords</div>
            {resume.matchedKeywords?.length > 0 && (
              <><div className="kw-section-title">✓ Matched</div>
                <div className="skills-match">{resume.matchedKeywords.map(s => <span key={s} className="match-chip hit">✓ {s}</span>)}</div></>
            )}
            {resume.missingKeywords?.length > 0 && (
              <><div className="kw-section-title" style={{ marginTop: 16 }}>✕ Missing</div>
                <div className="skills-match">{resume.missingKeywords.map(s => <span key={s} className="match-chip miss">{s}</span>)}</div></>
            )}
          </div>
        )}
        <div style={{ display: "flex", gap: 12, marginTop: 8 }}>
          {resume.pdfBase64 && <button className="download-btn" onClick={downloadPdf}>⬇ Download PDF</button>}
          <button className="btn btn-ghost" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  );
}

// ─── Resume Card ──────────────────────────────────────────────────────────────
function ResumeCard({ resume, onNavigate, onDelete, showDate = "short" }) {
  const [showDetail, setShowDetail] = useState(false);
  const col = scoreColor(resume.atsScore);

  const downloadPdf = (e) => {
    e.stopPropagation();
    const s = atob(resume.pdfBase64); const b = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) b[i] = s.charCodeAt(i);
    const blob = new Blob([b], { type: "application/pdf" });
    const url = URL.createObjectURL(blob); const a = document.createElement("a"); a.href = url;
    a.download = `Resume_${resume.company}_${resume.position}.pdf`.replace(/\s+/g, "_");
    a.click(); URL.revokeObjectURL(url);
  };

  return (
    <div>
      {showDetail && <ResumeDetailModal resume={resume} onClose={() => setShowDetail(false)} />}
      <div className="resume-card" onClick={() => setShowDetail(true)}>
        <div className="resume-card-header">
          <div>
            <div className="resume-card-company">{resume.company}</div>
            <div className="resume-card-position">{resume.position}</div>
          </div>
          <div style={{ textAlign: "right" }}>
            <div className="resume-card-score" style={{ color: col }}>{resume.atsScore}</div>
            <div className="resume-card-score-label">{resume.scoreLabel}</div>
          </div>
        </div>
        <div style={{ height: 4, background: "var(--surface2)", borderRadius: 2, overflow: "hidden" }}>
          <div style={{ height: "100%", width: `${resume.atsScore}%`, background: col, borderRadius: 2 }} />
        </div>
        <div className="resume-card-meta">
          <span className="resume-card-chip">✓ {resume.matchedKeywords?.length || 0} matched</span>
          <span className="resume-card-chip">✕ {resume.missingKeywords?.length || 0} missing</span>
          <span className="resume-card-chip">
            {new Date(resume.generatedAt).toLocaleDateString("en-US", showDate === "long"
              ? { month: "short", day: "numeric", year: "numeric" }
              : { month: "short", day: "numeric" })}
          </span>
        </div>
        <div className="resume-card-actions" onClick={e => e.stopPropagation()}>
          {resume.pdfBase64 && <button className="btn btn-ghost btn-sm" onClick={downloadPdf}>⬇ PDF</button>}
          <button className="btn btn-ghost btn-sm" onClick={() => onNavigate("generate", { company: resume.company, position: resume.position })}>↻ Redo</button>
          <button className="btn btn-danger btn-sm" onClick={() => onDelete(resume.id)}>🗑</button>
        </div>
      </div>
    </div>
  );
}

// ─── Home Dashboard ───────────────────────────────────────────────────────────
function HomeDashboard({ profile, generatedResumes, onNavigate, onDeleteResume }) {
  const avgScore = generatedResumes.length > 0
    ? Math.round(generatedResumes.reduce((s, r) => s + r.atsScore, 0) / generatedResumes.length) : null;
  const bestScore = generatedResumes.length > 0 ? Math.max(...generatedResumes.map(r => r.atsScore)) : null;
  const sorted = [...generatedResumes].sort((a, b) => new Date(b.generatedAt) - new Date(a.generatedAt));

  return (
    <div>
      <div className="home-hero">
        <div>
          <h1>{profile ? `Welcome back, ${profile.name?.split(" ")[0] || "there"}!` : "Welcome to RésuméAI"}</h1>
          <p>{profile
            ? "Your AI-powered career command center. Generate tailored resumes, track your applications, and maximize your ATS scores."
            : "Complete your profile to start generating ATS-optimized resumes tailored to every job you apply for."}</p>
        </div>
        <div style={{ display: "flex", gap: 12, flexShrink: 0, flexWrap: "wrap" }}>
          {profile
            ? <button className="btn btn-primary" style={{ width: "auto" }} onClick={() => onNavigate("generate")}>✨ Generate Resume</button>
            : <button className="btn btn-primary" style={{ width: "auto" }} onClick={() => onNavigate("profile")}>Complete Profile →</button>
          }
        </div>
      </div>

      {generatedResumes.length > 0 && (
        <div className="home-stats-row">
          <div className="stat-card">
            <div className="stat-card-num" style={{ color: "var(--accent)" }}>{generatedResumes.length}</div>
            <div className="stat-card-label">Resumes Generated</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-num" style={{ color: avgScore !== null ? scoreColor(avgScore) : "var(--accent)" }}>{avgScore ?? "—"}</div>
            <div className="stat-card-label">Average ATS Score</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-num" style={{ color: bestScore !== null ? scoreColor(bestScore) : "var(--accent)" }}>{bestScore ?? "—"}</div>
            <div className="stat-card-label">Best Score</div>
          </div>
        </div>
      )}

      <div className="home-actions">
        <div className="action-card" onClick={() => onNavigate("generate")}>
          <div className="action-card-icon">✨</div>
          <div className="action-card-title">Generate ATS Resume</div>
          <div className="action-card-sub">Paste a job description, enter company details, and let AI craft a perfectly tailored resume with ATS score analysis.</div>
        </div>
        <div className="action-card teal" onClick={() => onNavigate("profile")}>
          <div className="action-card-icon">👤</div>
          <div className="action-card-title">{profile ? "Edit Profile" : "Build Profile"}</div>
          <div className="action-card-sub">{profile ? "Update your work history, skills, education, and certifications to improve future resume quality." : "Build your professional profile with your work history, skills, and education."}</div>
        </div>
      </div>

      {sorted.length > 0 && (
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
            <div>
              <h2 style={{ fontFamily: "var(--font-display)", fontSize: 22, fontWeight: 700 }}>Generated Resumes</h2>
              <p style={{ color: "var(--muted)", fontSize: 14, marginTop: 4 }}>Your application history — click any card to view details</p>
            </div>
            <button className="btn btn-ghost btn-sm" onClick={() => onNavigate("generate")}>+ New Resume</button>
          </div>
          <div className="resumes-grid">
            {sorted.map(r => <ResumeCard key={r.id} resume={r} onNavigate={onNavigate} onDelete={onDeleteResume} />)}
          </div>
        </div>
      )}

      {generatedResumes.length === 0 && profile && (
        <div className="card" style={{ textAlign: "center", padding: "60px 40px" }}>
          <div style={{ fontSize: 48, marginBottom: 16 }}>📄</div>
          <h3 style={{ fontFamily: "var(--font-display)", fontSize: 22, marginBottom: 8 }}>No resumes generated yet</h3>
          <p style={{ color: "var(--muted)", marginBottom: 24 }}>Generate your first ATS-optimized resume for a specific job posting</p>
          <button className="btn btn-primary" style={{ width: "auto" }} onClick={() => onNavigate("generate")}>✨ Generate Your First Resume</button>
        </div>
      )}
    </div>
  );
}

// ─── Profile Overview ─────────────────────────────────────────────────────────
function ProfileOverview({ profile, onEdit }) {
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
        <span className={`complete-badge ${profile ? "done" : "pending"}`}>{profile ? "✓ Profile Complete" : "⚠ Profile Incomplete"}</span>
        <button className="btn btn-ghost btn-sm" onClick={onEdit}>{profile ? "✏ Edit Profile" : "Complete Profile"}</button>
      </div>
      {profile ? (
        <>
          <div className="card">
            <div className="card-title"><span>👤</span> Personal</div>
            <div className="profile-grid">
              {[["Name", profile.name], ["Email", profile.email], ["Phone", profile.phone],
              ["Location", profile.location], ["LinkedIn", profile.linkedin || "—"], ["GitHub", profile.github || "—"]
              ].map(([k, v]) => (
                <div key={k} className="info-row"><span className="info-label">{k}</span><span className="info-val">{v}</span></div>
              ))}
            </div>
            {profile.summary && <div style={{ marginTop: 16, padding: "12px 16px", background: "var(--surface2)", borderRadius: 8, fontSize: 14, lineHeight: 1.7, color: "var(--muted)" }}>{profile.summary}</div>}
          </div>
          <div className="card">
            <div className="card-title"><span>🏢</span> Experience ({(profile.companies || []).filter(c => c.company).length} positions)</div>
            {(profile.companies || []).filter(c => c.company).map((c, i) => (
              <div key={i} style={{ borderBottom: "1px solid var(--border)", paddingBottom: 12, marginBottom: 12 }}>
                <div style={{ fontWeight: 600 }}>{c.role} @ {c.company}</div>
                <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 3 }}>
                  {c.location && <span style={{ marginRight: 8 }}>📍 {c.location}</span>}
                  {fmtDate(c.startDate)} – {c.current ? "Present" : fmtDate(c.endDate)}
                </div>
              </div>
            ))}
          </div>
          <div className="card">
            <div className="card-title"><span>🎓</span> Education</div>
            {(profile.education || []).filter(e => e.institution).map((e, i) => (
              <div key={i} style={{ borderBottom: "1px solid var(--border)", paddingBottom: 12, marginBottom: 12 }}>
                <div style={{ fontWeight: 600 }}>{e.degree} {e.field ? `in ${e.field}` : ""}</div>
                <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 3 }}>
                  {e.institution}{e.location ? ` · ${e.location}` : ""}{e.year ? ` · ${e.year}` : ""}{e.gpa ? ` · GPA ${e.gpa}` : ""}
                </div>
              </div>
            ))}
          </div>
          <div className="card">
            <div className="card-title"><span>⚡</span> Skills ({(profile.skills || []).length})</div>
            <div className="skills-grid">{(profile.skills || []).map(s => <span key={s} className="skill-chip selected">{s}</span>)}</div>
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

// ─── Main Dashboard Shell ─────────────────────────────────────────────────────
function Dashboard({ session, profile, generatedResumes, onLogout, onEditProfile, onSaveResume, onDeleteResume }) {
  const [activePage, setActivePage] = useState("home");
  const [generatePrefill, setGeneratePrefill] = useState(null);

  const navigate = (page, prefill = null) => {
    setGeneratePrefill(prefill);
    setActivePage(page);
  };

  const NAV = [
    { id: "home", label: "Dashboard", icon: "🏠" },
    { id: "generate", label: "Generate Resume", icon: "✨" },
    { id: "resumes", label: "My Resumes", icon: "📄", badge: generatedResumes.length || null },
    { id: "profile", label: "My Profile", icon: "👤" },
  ];

  return (
    <div className="dashboard">
      <div className="sidebar">
        <div className="sidebar-logo" onClick={() => setActivePage("home")} title="Go to Dashboard">
          <div className="logo-text">Résumé<b>AI</b></div>
          <span className="home-hint">↩ Click to go home</span>
        </div>
        <nav className="sidebar-nav">
          {NAV.map(item => (
            <button key={item.id} className={`nav-item ${activePage === item.id ? "active" : ""}`} onClick={() => navigate(item.id)}>
              <span className="nav-icon">{item.icon}</span>
              <span style={{ flex: 1 }}>{item.label}</span>
              {item.badge ? <span className="nav-badge">{item.badge}</span> : null}
            </button>
          ))}
        </nav>
        <div className="sidebar-user">
          <div className="user-info">
            <div className="user-avatar">{session.fullName?.[0]?.toUpperCase() || "U"}</div>
            <div style={{ overflow: "hidden" }}>
              <div className="user-name">{session.fullName}</div>
              <div className="user-email">{session.email}</div>
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" style={{ marginTop: 12, width: "100%" }} onClick={onLogout}>Sign Out</button>
        </div>
      </div>

      <div className="main-content">
        {activePage === "home" && (
          <>
            <div className="page-header">
              <h1 className="page-title">Dashboard</h1>
              <p className="page-sub">Your career optimization center</p>
            </div>
            <HomeDashboard profile={profile} generatedResumes={generatedResumes} onNavigate={navigate} onDeleteResume={onDeleteResume} />
          </>
        )}

        {activePage === "generate" && (
          <>
            <div className="page-header">
              <h1 className="page-title">Generate Resume</h1>
              <p className="page-sub">Create an ATS-optimized resume tailored to a specific job posting</p>
            </div>
            {!profile ? (
              <div className="card" style={{ textAlign: "center", padding: "60px" }}>
                <div style={{ fontSize: 48, marginBottom: 16 }}>⚠️</div>
                <h3 style={{ fontFamily: "var(--font-display)", fontSize: 22, marginBottom: 8 }}>Complete your profile first</h3>
                <p style={{ color: "var(--muted)", marginBottom: 24 }}>We need your details to generate a personalized resume</p>
                <button className="btn btn-primary" style={{ width: "auto" }} onClick={() => { navigate("profile"); onEditProfile(); }}>Complete Profile</button>
              </div>
            ) : (
              <ResumeGenerator
                key={generatePrefill ? JSON.stringify(generatePrefill) : "default"}
                profile={profile}
                onSaveResume={onSaveResume}
                prefillCompany={generatePrefill?.company || ""}
                prefillPosition={generatePrefill?.position || ""}
              />
            )}
          </>
        )}

        {activePage === "resumes" && (
          <>
            <div className="page-header">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <h1 className="page-title">My Resumes</h1>
                  <p className="page-sub">{generatedResumes.length} resume{generatedResumes.length !== 1 ? "s" : ""} generated</p>
                </div>
                <button className="btn btn-primary" style={{ width: "auto" }} onClick={() => navigate("generate")}>✨ New Resume</button>
              </div>
            </div>
            {generatedResumes.length === 0 ? (
              <div className="card" style={{ textAlign: "center", padding: "60px 40px" }}>
                <div style={{ fontSize: 48, marginBottom: 16 }}>📄</div>
                <h3 style={{ fontFamily: "var(--font-display)", fontSize: 22, marginBottom: 8 }}>No resumes yet</h3>
                <p style={{ color: "var(--muted)", marginBottom: 24 }}>Generate your first ATS-optimized resume tailored to a job posting</p>
                <button className="btn btn-primary" style={{ width: "auto" }} onClick={() => navigate("generate")}>✨ Generate First Resume</button>
              </div>
            ) : (
              <div className="resumes-grid">
                {[...generatedResumes].sort((a, b) => new Date(b.generatedAt) - new Date(a.generatedAt)).map(r => (
                  <ResumeCard key={r.id} resume={r} onNavigate={navigate} onDelete={onDeleteResume} showDate="long" />
                ))}
              </div>
            )}
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
// Replace your entire App() export default function with this:

export default function App() {
  const [authView, setAuthView] = useState("login");
  const [showForgot, setShowForgot] = useState(false);
  const [session, setSession] = useState(() => {
    const user = localStorage.getItem("ats_user");
    const token = localStorage.getItem("ats_token");
    if (!user || !token) return null;
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      if (payload.exp && payload.exp * 1000 < Date.now()) {
        localStorage.removeItem("ats_token"); localStorage.removeItem("ats_user"); return null;
      }
    } catch { }
    return JSON.parse(user);
  });
  const [profile, setProfile] = useState(null);
  const [buildingProfile, setBuildingProfile] = useState(false);
  const [loadingProfile, setLoadingProfile] = useState(false);
  const [verifiedMsg, setVerifiedMsg] = useState(false);
  const [generatedResumes, setGeneratedResumes] = useState([]);

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
            try {
              const parsed = JSON.parse(data.profile);
              setProfile(parsed);
              if (parsed._generatedResumes) {
                setGeneratedResumes(parsed._generatedResumes);
              }
            } catch { setProfile(null); setBuildingProfile(true); }
          } else {
            setBuildingProfile(true);
          }
        })
        .catch(() => {
          localStorage.removeItem("ats_token"); localStorage.removeItem("ats_user");
          setSession(null); setProfile(null); setBuildingProfile(false);
        })
        .finally(() => setLoadingProfile(false));
    }
  }, [session]);

  const handleLogin = data => setSession({ email: data.email, fullName: data.fullName });

  const handleLogout = () => {
    localStorage.removeItem("ats_token"); localStorage.removeItem("ats_user");
    setSession(null); setProfile(null); setBuildingProfile(false); setGeneratedResumes([]);
  };

  const handleSaveResume = async (resume) => {
    const updated = [resume, ...generatedResumes].slice(0, 50);
    setGeneratedResumes(updated);
    if (profile) {
      const profileWithHistory = { ...profile, _generatedResumes: updated };
      try { await api.saveProfile(JSON.stringify(profileWithHistory)); } catch { }
    }
  };

  const handleDeleteResume = async (id) => {
    const updated = generatedResumes.filter(r => r.id !== id);
    setGeneratedResumes(updated);
    if (profile) {
      const profileWithHistory = { ...profile, _generatedResumes: updated };
      try { await api.saveProfile(JSON.stringify(profileWithHistory)); } catch { }
    }
  };

  const handleProfileComplete = async (p) => {
    const profileWithHistory = { ...p, _generatedResumes: generatedResumes };
    setProfile(profileWithHistory);
    setBuildingProfile(false);
  };

  // ✅ Use query param instead of pathname so React SPA loads index.html correctly
  const urlParams = new URLSearchParams(window.location.search);
  const isResetPage = urlParams.get("page") === "reset-password";

  return (
    <>
      <style>{css}</style>
      <div className="app">

        {/* ✅ Reset password page — triggered via ?page=reset-password in email link */}
        {isResetPage && (
          <ResetPasswordPage onBack={() => {
            // Clear query params and go back to login
            window.history.pushState({}, "", "/");
            window.location.reload();
          }} />
        )}

        {/* ✅ All normal app routes */}
        {!isResetPage && (
          <>
            {/* Not logged in — show login, signup, or forgot password */}
            {!session && !showForgot && (
              authView === "login"
                ? <LoginPage
                    onLogin={handleLogin}
                    onSwitch={() => setAuthView("signup")}
                    verifiedMsg={verifiedMsg}
                    onForgotPassword={() => setShowForgot(true)}
                  />
                : <SignupPage onSwitch={() => setAuthView("login")} />
            )}

            {!session && showForgot && (
              <ForgotPasswordPage onBack={() => setShowForgot(false)} />
            )}

            {/* Loading profile */}
            {session && loadingProfile && (
              <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100vh", flexDirection: "column", gap: 16 }}>
                <div className="spinner" />
                <p style={{ color: "var(--muted)" }}>Loading your profile…</p>
              </div>
            )}

            {/* Profile builder */}
            {session && !loadingProfile && buildingProfile && (
              <ProfileBuilder
                session={session}
                initialProfile={profile}
                onComplete={handleProfileComplete}
                onCancel={profile ? () => setBuildingProfile(false) : null}
              />
            )}

            {/* Main dashboard */}
            {session && !loadingProfile && !buildingProfile && (
              <Dashboard
                session={session}
                profile={profile}
                generatedResumes={generatedResumes}
                onLogout={handleLogout}
                onEditProfile={() => setBuildingProfile(true)}
                onSaveResume={handleSaveResume}
                onDeleteResume={handleDeleteResume}
              />
            )}
          </>
        )}

      </div>
    </>
  );
}