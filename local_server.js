// Load environment variables (.env) if present
try {
  if (typeof process.loadEnvFile === 'function') {
    process.loadEnvFile();
  }
} catch (e) {}

const express = require('express');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const nodemailer = require('nodemailer');

let DatabaseSync = null;
try {
  DatabaseSync = require('node:sqlite').DatabaseSync;
} catch (e) {
  // node:sqlite not supported in this Node runtime
}

const app = express();
app.use(express.json({ limit: '10mb' }));

app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('Content-Security-Policy',
    "default-src 'self'; " +
    "script-src 'self' 'unsafe-inline' https://www.gstatic.com https://cdnjs.cloudflare.com; " +
    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
    "img-src 'self' data: blob: https:; " +
    "connect-src 'self' https://firestore.googleapis.com https://*.firebaseio.com https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://www.googleapis.com https://accounts.google.com https://translate.googleapis.com https://cdnjs.cloudflare.com https://www.gstatic.com https://fonts.googleapis.com https://fonts.gstatic.com wss://*.firebaseio.com; " +
    "font-src 'self' data: https://fonts.gstatic.com https://fonts.googleapis.com; " +
    "frame-ancestors 'self'; " +
    "base-uri 'self'; form-action 'self'");
  next();
});

let db = null;
if (DatabaseSync) {
  try {
    const isVercel = !!process.env.VERCEL;
    const dbPath = isVercel
      ? path.join('/tmp', 'focus.db')
      : (process.env.DATABASE_PATH || path.join(__dirname, 'focus.db'));
    db = new DatabaseSync(dbPath);
    db.exec(`
CREATE TABLE IF NOT EXISTS users(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  pass_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  sec_question TEXT NOT NULL,
  sec_answer_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions(
  id TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL,
  date TEXT NOT NULL,
  subject TEXT NOT NULL,
  workType TEXT,
  minutes INTEGER NOT NULL,
  ts INTEGER NOT NULL,
  isNonStudy INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS prefs(
  user_id INTEGER PRIMARY KEY,
  data TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS tokens(
  token TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS otps(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL,
  code TEXT NOT NULL,
  token TEXT,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  used INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS magic_links(
  token TEXT PRIMARY KEY,
  email TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS papers(
  id TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  subject TEXT,
  className TEXT,
  fullMarks TEXT,
  examType TEXT,
  sections TEXT,
  meta TEXT,
  published INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
`);
  } catch (err) {
    console.warn('SQLite initialization skipped or failed:', err.message);
  }
}

let mailer = null;
let fileConfig = {};
try {
  fileConfig = JSON.parse(fs.readFileSync(path.join(__dirname, 'mail.json'), 'utf8'));
} catch (e) {}

const user = process.env.SMTP_USER || fileConfig.user;
const pass = process.env.SMTP_PASS || fileConfig.pass;
if (user && pass) {
  mailer = nodemailer.createTransport({ service: 'gmail', auth: { user, pass } });
}

async function sendLoginEmail(to, code, magicUrl) {
  const html = `
  <div style="margin:0; padding:40px 16px; background:#f6f7f9; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
    <div style="max-width:460px; margin:0 auto; background:#ffffff; border-radius:16px; overflow:hidden; border:1px solid #e5e7eb;">
      <div style="padding:36px 36px 8px 36px; text-align:center;">
        <div style="display:inline-block; width:44px; height:44px; border-radius:12px; background:#111827; color:#fbbf24; font-weight:700; font-size:22px; line-height:44px; text-align:center;">F</div>
        <h1 style="margin:18px 0 6px; font-size:20px; font-weight:650; color:#111827; letter-spacing:-0.01em;">Your login code</h1>
        <p style="margin:0; font-size:14px; line-height:1.6; color:#6b7280;">Use this code to sign in to your Focus Study Timer account. It expires in 10 minutes.</p>
      </div>
      <div style="padding:24px 36px; text-align:center;">
        <div style="margin:0 auto; padding:18px 24px; background:#f9fafb; border:1px solid #e5e7eb; border-radius:12px; font-size:32px; font-weight:700; letter-spacing:10px; color:#111827; font-variant-numeric:tabular-nums;">${code}</div>
        <div style="height:1px; background:#f3f4f6; margin:28px 0;"></div>
        <p style="margin:0 0 14px; font-size:13px; color:#6b7280;">Prefer a one-click sign in?</p>
        <a href="${magicUrl}" style="display:inline-block; background:#111827; color:#ffffff; text-decoration:none; padding:13px 28px; border-radius:10px; font-size:14px; font-weight:600;">Sign in with magic link</a>
      </div>
      <div style="padding:20px 36px 32px 36px; border-top:1px solid #f3f4f6;">
        <p style="margin:0; font-size:12px; line-height:1.6; color:#9ca3af;">If you didn't request this email, you can safely ignore it. Never share this code with anyone.</p>
      </div>
    </div>
  </div>`;
  await mailer.sendMail({
    from: `"Focus Study Timer" <${mailer.options.auth.user}>`,
    to,
    subject: 'Your login code for Focus Study Timer',
    html
  });
}

const COOKIE_NAME = 'ft_token';
const COOKIE_MAX_AGE = 30 * 24 * 60 * 60 * 1000;
const TOKEN_MAX_AGE = 365 * 24 * 60 * 60 * 1000;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function cleanText(s, maxLen) {
  return String(s || '').replace(/[<>]/g, c => (c === '<' ? '&lt;' : '&gt;')).slice(0, maxLen);
}

function cookieOpts(req, remember) {
  const opts = { httpOnly: true, sameSite: 'lax', path: '/' };
  if (remember) opts.maxAge = COOKIE_MAX_AGE;
  if (req.secure || req.headers['x-forwarded-proto'] === 'https') opts.secure = true;
  return opts;
}

function hashPw(pw, salt) {
  return crypto.scryptSync(String(pw), salt, 64).toString('hex');
}

function verifyPw(pw, salt, hash) {
  const a = Buffer.from(hashPw(pw, salt), 'hex');
  const b = Buffer.from(hash, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function newToken() {
  return crypto.randomBytes(32).toString('hex');
}

function userById(id) {
  return db.prepare('SELECT id, email, name, sec_question, created_at FROM users WHERE id = ?').get(id);
}

function userFromReq(req) {
  const m = /(?:^|;\s*)ft_token=([^;]+)/.exec(req.headers.cookie || '');
  if (!m) return null;
  const row = db.prepare('SELECT user_id, created_at FROM tokens WHERE token = ?').get(m[1]);
  if (!row) return null;
  if (Date.now() - row.created_at > TOKEN_MAX_AGE) {
    db.prepare('DELETE FROM tokens WHERE token = ?').run(m[1]);
    return null;
  }
  return userById(row.user_id);
}

function requireAuth(req, res, next) {
  const user = userFromReq(req);
  if (!user) return res.status(401).json({ ok: false, error: 'not_authed' });
  req.user = user;
  next();
}

function makeRateLimiter(limit, windowMs) {
  const m = new Map();
  return {
    allow(key) {
      const rec = m.get(key);
      if (!rec) return true;
      if (rec.count >= limit && Date.now() - rec.at < windowMs) return false;
      if (Date.now() - rec.at >= windowMs) m.delete(key);
      return true;
    },
    fail(key) {
      const rec = m.get(key) || { count: 0, at: Date.now() };
      rec.count += 1;
      rec.at = Date.now();
      m.set(key, rec);
    },
    clear(key) { m.delete(key); }
  };
}

const loginLimiter = makeRateLimiter(5, 60 * 1000);
const forgotLimiter = makeRateLimiter(5, 60 * 1000);
const resetLimiter = makeRateLimiter(5, 60 * 1000);
const otpVerifyLimiter = makeRateLimiter(5, 60 * 1000);
const otpCooldown = new Map();
const OTP_COOLDOWN_MS = 15 * 1000;
function rateLimit(email) { return loginLimiter.allow(email); }
function recordFail(email) { loginLimiter.fail(email); }

app.get('/api/me', (req, res) => {
  const user = userFromReq(req);
  if (!user) return res.status(401).json({ ok: false, error: 'not_authed' });
  res.json({ ok: true, user });
});

app.post('/api/auth/check', (req, res) => {
  const { email } = req.body || {};
  const cleanEmail = String(email || '').trim().toLowerCase();
  const row = db.prepare('SELECT id FROM users WHERE email = ?').get(cleanEmail);
  res.json({ ok: true, registered: !!row });
});

app.post('/api/register', (req, res) => {
  const { email, password, name, secQuestion, secAnswer, remember } = req.body || {};
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (!EMAIL_RE.test(cleanEmail)) return res.status(400).json({ ok: false, error: 'invalid_email' });
  if (typeof password !== 'string' || password.length < 6) return res.status(400).json({ ok: false, error: 'weak_password' });
  if (!name || !secQuestion || !secAnswer) return res.status(400).json({ ok: false, error: 'missing_fields' });

  const exists = db.prepare('SELECT id FROM users WHERE email = ?').get(cleanEmail);
  if (exists) return res.status(409).json({ ok: false, error: 'email_taken' });

  const salt = crypto.randomBytes(16).toString('hex');
  const info = db.prepare(
    'INSERT INTO users(email, name, pass_hash, salt, sec_question, sec_answer_hash, created_at) VALUES(?,?,?,?,?,?,?)'
  ).run(
    cleanEmail,
    String(name).slice(0, 80),
    hashPw(password, salt),
    salt,
    String(secQuestion).slice(0, 200),
    hashPw(String(secAnswer).trim().toLowerCase(), salt),
    Date.now()
  );

  const token = newToken();
  db.prepare('INSERT INTO tokens(token, user_id, created_at) VALUES(?,?,?)').run(token, info.lastInsertRowid, Date.now());
  res.cookie(COOKIE_NAME, token, cookieOpts(req, remember));
  res.json({ ok: true, user: userById(info.lastInsertRowid) });
});

app.post('/api/login', (req, res) => {
  const { email, password, remember } = req.body || {};
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (!rateLimit(cleanEmail)) return res.status(429).json({ ok: false, error: 'too_many_attempts' });

  const row = db.prepare('SELECT * FROM users WHERE email = ?').get(cleanEmail);
  if (!row || !verifyPw(password, row.salt, row.pass_hash)) {
    recordFail(cleanEmail);
    return res.status(401).json({ ok: false, error: 'bad_credentials' });
  }

  loginLimiter.clear(cleanEmail);
  const token = newToken();
  db.prepare('INSERT INTO tokens(token, user_id, created_at) VALUES(?,?,?)').run(token, row.id, Date.now());
  res.cookie(COOKIE_NAME, token, cookieOpts(req, remember));
  res.json({ ok: true, user: userById(row.id) });
});

app.post('/api/logout', requireAuth, (req, res) => {
  const m = /(?:^|;\s*)ft_token=([^;]+)/.exec(req.headers.cookie || '');
  if (m) db.prepare('DELETE FROM tokens WHERE token = ?').run(m[1]);
  res.clearCookie(COOKIE_NAME, { path: '/' });
  res.json({ ok: true });
});

app.post('/api/forgot', (req, res) => {
  const { email } = req.body || {};
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (!forgotLimiter.allow(cleanEmail)) return res.status(429).json({ ok: false, error: 'too_many_attempts' });
  forgotLimiter.fail(cleanEmail);
  const row = db.prepare('SELECT sec_question FROM users WHERE email = ?').get(cleanEmail);
  if (!row) return res.status(404).json({ ok: false, error: 'email_not_found' });
  res.json({ ok: true, secQuestion: row.sec_question });
});

app.post('/api/reset-password', (req, res) => {
  const { email, secAnswer, newPassword } = req.body || {};
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (typeof newPassword !== 'string' || newPassword.length < 6) return res.status(400).json({ ok: false, error: 'weak_password' });
  if (!resetLimiter.allow(cleanEmail)) return res.status(429).json({ ok: false, error: 'too_many_attempts' });

  const row = db.prepare('SELECT * FROM users WHERE email = ?').get(cleanEmail);
  if (!row) {
    resetLimiter.fail(cleanEmail);
    return res.status(404).json({ ok: false, error: 'email_not_found' });
  }
  if (!verifyPw(String(secAnswer || '').trim().toLowerCase(), row.salt, row.sec_answer_hash)) {
    resetLimiter.fail(cleanEmail);
    return res.status(401).json({ ok: false, error: 'wrong_answer' });
  }

  resetLimiter.clear(cleanEmail);

  const salt = crypto.randomBytes(16).toString('hex');
  db.prepare('UPDATE users SET pass_hash = ?, salt = ? WHERE id = ?').run(hashPw(newPassword, salt), salt, row.id);
  db.prepare('DELETE FROM tokens WHERE user_id = ?').run(row.id);
  res.json({ ok: true });
});

app.post('/api/otp/send', async (req, res) => {
  const { email } = req.body || {};
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (!EMAIL_RE.test(cleanEmail)) return res.status(400).json({ ok: false, error: 'invalid_email' });

  if (otpCooldown.has(cleanEmail) && Date.now() - otpCooldown.get(cleanEmail) < OTP_COOLDOWN_MS) {
    return res.status(429).json({ ok: false, error: 'too_frequent' });
  }

  const user = db.prepare('SELECT id FROM users WHERE email = ?').get(cleanEmail);
  if (!user) return res.json({ ok: true, exists: false });

  db.prepare('DELETE FROM otps WHERE email = ? OR expires_at < ?').run(cleanEmail, Date.now());

  const code = String(Math.floor(100000 + Math.random() * 900000));
  const token = crypto.randomBytes(32).toString('hex');
  const expiresAt = Date.now() + 10 * 60 * 1000;
  db.prepare('INSERT INTO otps(email, code, token, expires_at) VALUES(?,?,?,?)').run(cleanEmail, code, token, expiresAt);

  const magicUrl = `${req.protocol}://${req.headers.host}/api/magiclink/verify?token=${token}`;
  const result = { ok: true, exists: true, emailSent: false };
  if (mailer) {
    try {
      await sendLoginEmail(cleanEmail, code, magicUrl);
      result.emailSent = true;
    } catch (e) {
      console.error('Email send failed:', e.message);
      result.mailError = String(e.message).slice(0, 200);
    }
  } else {
    console.log('[DEV] OTP for ' + cleanEmail + ':', code);
  }
  const isDev = process.env.NODE_ENV !== 'production';
  if (!result.emailSent && isDev) {
    result.devCode = code;
    result.devLink = magicUrl;
  }
  otpCooldown.set(cleanEmail, Date.now());
  res.json(result);
});

app.post('/api/otp/verify', (req, res) => {
  const { email, code } = req.body || {};
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (!otpVerifyLimiter.allow(cleanEmail)) return res.status(429).json({ ok: false, error: 'too_many_attempts' });
  const row = db.prepare('SELECT * FROM otps WHERE email = ? AND used = 0 ORDER BY id DESC LIMIT 1').get(cleanEmail);
  if (!row || row.expires_at < Date.now()) {
    if (row) db.prepare('DELETE FROM otps WHERE id = ?').run(row.id);
    return res.status(410).json({ ok: false, error: 'expired' });
  }
  if (row.attempts >= 5) {
    db.prepare('DELETE FROM otps WHERE id = ?').run(row.id);
    return res.status(429).json({ ok: false, error: 'too_many_attempts' });
  }
  const a = Buffer.from(String(row.code || ''));
  const b = Buffer.from(String(code || ''));
  const ok = a.length > 0 && a.length === b.length && crypto.timingSafeEqual(a, b);
  if (!ok) {
    db.prepare('UPDATE otps SET attempts = attempts + 1 WHERE id = ?').run(row.id);
    otpVerifyLimiter.fail(cleanEmail);
    return res.status(401).json({ ok: false, error: 'wrong_code' });
  }
  const user = db.prepare('SELECT id FROM users WHERE email = ?').get(cleanEmail);
  if (!user) return res.status(404).json({ ok: false, error: 'email_not_found' });
  db.prepare('DELETE FROM otps WHERE email = ?').run(cleanEmail);
  const token = newToken();
  db.prepare('INSERT INTO tokens(token, user_id, created_at) VALUES(?,?,?)').run(token, user.id, Date.now());
  res.cookie(COOKIE_NAME, token, cookieOpts(req, true));
  res.json({ ok: true, user: userById(user.id) });
});

app.get('/api/magiclink/verify', (req, res) => {
  const { token } = req.query;
  const row = token ? db.prepare('SELECT * FROM otps WHERE token = ? AND used = 0').get(String(token)) : null;
  if (!row || row.expires_at < Date.now()) {
    if (row) db.prepare('DELETE FROM otps WHERE id = ?').run(row.id);
    return res.redirect('/?magic=failed');
  }
  const user = db.prepare('SELECT id FROM users WHERE email = ?').get(row.email);
  if (!user) return res.redirect('/?magic=failed');
  db.prepare('DELETE FROM otps WHERE email = ?').run(row.email);
  const sessionToken = newToken();
  db.prepare('INSERT INTO tokens(token, user_id, created_at) VALUES(?,?,?)').run(sessionToken, user.id, Date.now());
  res.cookie(COOKIE_NAME, sessionToken, cookieOpts(req, true));
  res.redirect('/');
});

app.get('/api/sessions', requireAuth, (req, res) => {
  const rows = db.prepare('SELECT * FROM sessions WHERE user_id = ? ORDER BY ts DESC').all(req.user.id);
  const sessions = rows.map(r => ({
    id: r.id,
    date: r.date,
    subject: r.subject,
    workType: r.workType,
    minutes: r.minutes,
    ts: r.ts,
    isNonStudy: !!r.isNonStudy
  }));
  res.json({ ok: true, sessions });
});

app.post('/api/sessions', requireAuth, (req, res) => {
  const { id, date, subject, workType, minutes, ts, isNonStudy } = req.body || {};
  const mins = Math.max(0, Math.min(24 * 60, Math.round(Number(minutes)) || 0));
  if (!date || !subject || mins <= 0) return res.status(400).json({ ok: false, error: 'bad_data' });
  const sid = id || 's' + crypto.randomBytes(8).toString('hex');
  db.prepare(
    'INSERT OR IGNORE INTO sessions(id, user_id, date, subject, workType, minutes, ts, isNonStudy) VALUES(?,?,?,?,?,?,?,?)'
  ).run(sid, req.user.id, String(date), cleanText(subject, 200), cleanText(workType || 'Other', 100), mins, Number(ts) || Date.now(), isNonStudy ? 1 : 0);
  res.json({ ok: true, id: sid });
});

app.delete('/api/sessions/:id', requireAuth, (req, res) => {
  db.prepare('DELETE FROM sessions WHERE id = ? AND user_id = ?').run(req.params.id, req.user.id);
  res.json({ ok: true });
});

app.post('/api/sessions/bulk-delete', requireAuth, (req, res) => {
  const { from, to } = req.body || {};
  if (!from || !to) return res.status(400).json({ ok: false, error: 'bad_data' });
  const info = db.prepare('DELETE FROM sessions WHERE user_id = ? AND date >= ? AND date <= ?').run(req.user.id, String(from), String(to));
  res.json({ ok: true, deleted: info.changes });
});

app.post('/api/sessions/import', requireAuth, (req, res) => {
  const { sessions, nonStudySessions } = req.body || {};
  const stmt = db.prepare(
    'INSERT OR IGNORE INTO sessions(id, user_id, date, subject, workType, minutes, ts, isNonStudy) VALUES(?,?,?,?,?,?,?,?)'
  );
  const insertAll = (list, isNS) => {
    let count = 0;
    (list || []).forEach(s => {
      if (!s || !s.date || !s.subject || !s.minutes) return;
      const sid = s.id || 's' + crypto.randomBytes(8).toString('hex');
      const mins = Math.max(0, Math.min(24 * 60, Math.round(Number(s.minutes)) || 0));
      if (mins <= 0) return;
      const info = stmt.run(sid, req.user.id, String(s.date), cleanText(s.subject, 200), cleanText(s.workType || 'Other', 100), mins, Number(s.ts) || Date.now(), isNS ? 1 : 0);
      if (info.changes > 0) count++;
    });
    return count;
  };
  const studyCount = insertAll(sessions, false);
  const nsCount = insertAll(nonStudySessions, true);
  res.json({ ok: true, added: studyCount, addedNonStudy: nsCount });
});

app.get('/api/prefs', requireAuth, (req, res) => {
  const row = db.prepare('SELECT data FROM prefs WHERE user_id = ?').get(req.user.id);
  res.json({ ok: true, data: row ? JSON.parse(row.data) : null });
});

app.post('/api/prefs', requireAuth, (req, res) => {
  const { data } = req.body || {};
  if (data === undefined) return res.status(400).json({ ok: false, error: 'bad_data' });
  db.prepare('INSERT INTO prefs(user_id, data) VALUES(?,?) ON CONFLICT(user_id) DO UPDATE SET data = excluded.data')
    .run(req.user.id, JSON.stringify(data));
  res.json({ ok: true });
});

// ===== SMART NOTE PAPER ENDPOINTS =====

app.get('/api/papers', requireAuth, (req, res) => {
  const rows = db.prepare('SELECT * FROM papers WHERE user_id = ? ORDER BY updated_at DESC').all(req.user.id);
  const papers = rows.map(r => ({
    id: r.id,
    title: r.title,
    subject: r.subject,
    className: r.className,
    fullMarks: r.fullMarks,
    examType: r.examType,
    sections: JSON.parse(r.sections || '[]'),
    meta: JSON.parse(r.meta || '{}'),
    published: r.published === 1,
    createdAt: r.created_at,
    updatedAt: r.updated_at
  }));
  res.json({ ok: true, papers });
});

app.post('/api/papers', requireAuth, (req, res) => {
  const { title, subject, className, fullMarks, examType, sections, meta } = req.body || {};
  if (!title) return res.status(400).json({ ok: false, error: 'missing_title' });

  const id = 'p_' + crypto.randomBytes(8).toString('hex');
  const now = Date.now();
  
  db.prepare(
    'INSERT INTO papers(id, user_id, title, subject, className, fullMarks, examType, sections, meta, published, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)'
  ).run(
    id,
    req.user.id,
    cleanText(title, 200),
    cleanText(subject || '', 100),
    cleanText(className || '', 100),
    cleanText(fullMarks || '', 100),
    cleanText(examType || '', 100),
    JSON.stringify(sections || []),
    JSON.stringify(meta || {}),
    0,
    now,
    now
  );
  
  res.json({ ok: true, id });
});

app.put('/api/papers/:id', requireAuth, (req, res) => {
  const { id } = req.params;
  const { title, subject, className, fullMarks, examType, sections, meta } = req.body || {};
  if (!title) return res.status(400).json({ ok: false, error: 'missing_title' });

  const info = db.prepare(
    'UPDATE papers SET title=?, subject=?, className=?, fullMarks=?, examType=?, sections=?, meta=?, updated_at=? WHERE id=? AND user_id=?'
  ).run(
    cleanText(title, 200),
    cleanText(subject || '', 100),
    cleanText(className || '', 100),
    cleanText(fullMarks || '', 100),
    cleanText(examType || '', 100),
    JSON.stringify(sections || []),
    JSON.stringify(meta || {}),
    Date.now(),
    id,
    req.user.id
  );

  if (info.changes === 0) return res.status(404).json({ ok: false, error: 'not_found' });
  res.json({ ok: true });
});

app.delete('/api/papers/:id', requireAuth, (req, res) => {
  const info = db.prepare('DELETE FROM papers WHERE id=? AND user_id=?').run(req.params.id, req.user.id);
  if (info.changes === 0) return res.status(404).json({ ok: false, error: 'not_found' });
  res.json({ ok: true });
});

app.patch('/api/papers/:id/publish', requireAuth, (req, res) => {
  const { published } = req.body || {};
  const info = db.prepare('UPDATE papers SET published=?, updated_at=? WHERE id=? AND user_id=?').run(
    published ? 1 : 0,
    Date.now(),
    req.params.id,
    req.user.id
  );
  if (info.changes === 0) return res.status(404).json({ ok: false, error: 'not_found' });
  res.json({ ok: true });
});

app.use(express.static(path.join(__dirname, 'public')));

app.get('*', (req, res) => {
  const indexPath = path.join(__dirname, 'public', 'index.html');
  if (fs.existsSync(indexPath)) {
    return res.sendFile(indexPath);
  }
  res.status(200).send('Focus Study Timer');
});

app.use((err, req, res, next) => {
  if (err.type === 'entity.parse.failed') return res.status(400).json({ ok: false, error: 'bad_json' });
  console.error(err);
  res.status(500).json({ ok: false, error: 'server_error' });
});

const PORT = process.env.PORT || 3000;
if (require.main === module) {
  app.listen(PORT, () => {
    console.log('Focus Study Timer server running at http://localhost:' + PORT);
  });
}

module.exports = app;
