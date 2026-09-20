import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, signInWithPopup, signOut, onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  initializeFirestore, persistentLocalCache, persistentMultipleTabManager,
  collection, doc, setDoc, deleteDoc, getDocs, getDoc, addDoc, onSnapshot, query, where, writeBatch
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyAWlmrAVHlBy2ugbGch6HpSJpp9ikfPbJ4",
  authDomain: "sahil-workspace-ab8af.firebaseapp.com",
  projectId: "sahil-workspace-ab8af",
  storageBucket: "sahil-workspace-ab8af.firebasestorage.app",
  messagingSenderId: "486309866833",
  appId: "1:486309866833:web:0e695c6720734ff7a27770",
  measurementId: "G-KRF20FSKVF"
};
const fbApp = initializeApp(firebaseConfig);
const auth = getAuth(fbApp);
const db = initializeFirestore(fbApp, {
  localCache: persistentLocalCache({tabManager: persistentMultipleTabManager()})
});
const provider = new GoogleAuthProvider();

let currentUser = null;

/* ---------- local (guest) persistence ---------- */
const LS_SESSIONS = 'st_sessions';
const LS_PREFS = 'st_prefs';
const LS_TIMERSTATE = 'st_timerstate';

function localGet(key, fallback){
  try{ const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; }catch(e){ return fallback; }
}
function localSet(key, val){
  try{ localStorage.setItem(key, JSON.stringify(val)); }catch(e){}
}
function escapeHTML(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
function sanitizeHTML(str) {
  return escapeHTML(str);
}

/* ---------- Screen Wake Lock (keeps display active while focusing) ---------- */
let wakeLockSentinel = null;

async function requestWakeLock() {
  try {
    if ('wakeLock' in navigator && !wakeLockSentinel) {
      wakeLockSentinel = await navigator.wakeLock.request('screen');
      wakeLockSentinel.addEventListener('release', () => {
        wakeLockSentinel = null;
      });
    }
  } catch (err) {
    console.debug('Wake lock request skipped:', err.message);
  }
}

async function releaseWakeLock() {
  if (wakeLockSentinel) {
    try {
      await wakeLockSentinel.release();
    } catch (e) {}
    wakeLockSentinel = null;
  }
}

document.addEventListener('visibilitychange', async () => {
  if (document.visibilityState === 'visible' && running) {
    await requestWakeLock();
  }
});

const DEFAULT_SUBJECTS = [
  { name: 'Bengali', isCore: true, sub: ['Text', 'Grammar'] },
  { name: 'English', isCore: true, sub: ['Text', 'Grammar'] },
  { name: 'Math', isCore: true },
  { name: 'Life Science', isCore: true },
  { name: 'Physical Science', isCore: true },
  { name: 'History', isCore: true },
  { name: 'Geography', isCore: true }
];
const DEFAULT_WORK_TYPES = ['Revision', 'New Topic', 'Memorize', 'Reading', 'Practice', 'Notes', 'Mock Test', 'Other'];

let sessions = [];               
let nonStudySessions = [];       
let prefs = { dailyTarget: 120, subjects: [], workTypes: [] };

let unsubSessions = null;
let unsubNonStudySessions = null;

/* ---------- data layer ---------- */
async function loadAll(){
  if(currentUser){
    const q = collection(db, 'users', currentUser.uid, 'sessions');
    
    if (unsubSessions) unsubSessions();
    unsubSessions = onSnapshot(q, (snap) => {
      const raw = snap.docs.map(d => ({ id:d.id, ...d.data() }));
      const byTs = new Map();
      raw.forEach(s => {
        const key = s.ts || s.timestamp || s.id;
        if (!byTs.has(key)) byTs.set(key, s);
      });
      sessions = Array.from(byTs.values());
      refreshEverything();
    });

    const qNS = collection(db, 'users', currentUser.uid, 'nonStudySessions');
    if (unsubNonStudySessions) unsubNonStudySessions();
    unsubNonStudySessions = onSnapshot(qNS, (snap) => {
      const raw = snap.docs.map(d => ({ id:d.id, ...d.data(), isNonStudy: true }));
      const byTs = new Map();
      raw.forEach(s => {
        const key = s.ts || s.timestamp || s.id;
        if (!byTs.has(key)) byTs.set(key, s);
      });
      nonStudySessions = Array.from(byTs.values());
      refreshEverything();
    });

    const prefSnap = await getDoc(doc(db,'users',currentUser.uid,'meta','prefs'));
    prefs = prefSnap.exists() ? prefSnap.data() : { dailyTarget:120, subjects: [...DEFAULT_SUBJECTS], workTypes: [...DEFAULT_WORK_TYPES] };
  } else {
    sessions = localGet(LS_SESSIONS, []);
    nonStudySessions = localGet('st_nonstudy_sessions', []).map(s => ({ ...s, isNonStudy: true }));
    prefs = localGet(LS_PREFS, { dailyTarget:120, subjects: [...DEFAULT_SUBJECTS], workTypes: [...DEFAULT_WORK_TYPES] });
  }

  const oldBengaliCore = ['বাংলা', 'ইংরেজি', 'অংক', 'জীবন বিজ্ঞান', 'ভৌত বিজ্ঞান', 'ইতিহাস', 'ভূগোল'];
  if(prefs.subjects && prefs.subjects.length > 0){
    prefs.subjects = prefs.subjects.filter(s => {
      const name = typeof s === 'string' ? s : s.name;
      return !oldBengaliCore.includes(name);
    });
  }
  
  if(!prefs.subjects || prefs.subjects.length === 0) {
    prefs.subjects = DEFAULT_SUBJECTS.map(s => ({...s}));
  } else {
    DEFAULT_SUBJECTS.forEach(cs => {
      if(!prefs.subjects.find(s => (s.name || s) === cs.name)){
        prefs.subjects.unshift({...cs});
      }
    });
    prefs.subjects = prefs.subjects.map(s => {
      if (typeof s === 'string') {
        const coreMatch = DEFAULT_SUBJECTS.find(d => d.name === s);
        if (coreMatch) return { ...coreMatch };
        return { name: s, isCore: false };
      }
      return s;
    });
  }
  
  const oldBengaliWT = ['রিভিশন', 'নতুন পড়া', 'মুখস্থ করা', 'রিডিং পড়া', 'প্রশ্ন উত্তর প্র্যাকটিস', 'নোট তৈরি', 'অন্যান্য'];
  if(prefs.workTypes && prefs.workTypes.length > 0){
    prefs.workTypes = prefs.workTypes.filter(wt => !oldBengaliWT.includes(wt));
    DEFAULT_WORK_TYPES.forEach(dwt => {
      if(!prefs.workTypes.includes(dwt)){
        prefs.workTypes.unshift(dwt);
      }
    });
  }
  
  if(!prefs.workTypes || prefs.workTypes.length === 0) {
    prefs.workTypes = [...DEFAULT_WORK_TYPES];
  }
}

async function addSession(subjName, minutes, workType){
  if(minutes <= 0) return;
  const subjObj = prefs.subjects.find(s => (s.name || s) === selectedSubject);
  const isNS = subjObj ? !!subjObj.isNonStudy : false;

  const rec = { date: todayStr(), subject: subjName, workType: workType || 'N/A', minutes, ts: Date.now() };
  
  if(isNS) {
    rec.isNonStudy = true;
    rec.id = String(rec.ts);
    if (currentUser) {
      try {
        const docRef = doc(db, 'users', currentUser.uid, 'nonStudySessions', String(rec.ts));
        await setDoc(docRef, rec, { merge: true });
      } catch(e) {
        showToast('No internet! Non-study session saved offline.');
        rec.id = 'ns' + Date.now();
        nonStudySessions.push(rec);
        localSet('st_nonstudy_sessions', nonStudySessions);
      }
    } else {
      rec.id = 'ns' + Date.now();
      nonStudySessions.push(rec);
      localSet('st_nonstudy_sessions', nonStudySessions);
    }
  } else {
    rec.id = String(rec.ts);
    if(currentUser){
      try {
        const docRef = doc(db, 'users', currentUser.uid, 'sessions', String(rec.ts));
        await setDoc(docRef, rec, { merge: true });
      } catch(e) {
        showToast('No internet! Session saved offline.');
        rec.id = 'l' + Date.now();
        sessions.push(rec);
        localSet(LS_SESSIONS, sessions);
      }
    } else {
      rec.id = 'l' + Date.now();
      sessions.push(rec);
      localSet(LS_SESSIONS, sessions);
    }
  }
}

async function deleteSession(id){
  const isNS = nonStudySessions.some(s => s.id === id);
  if(isNS) {
    nonStudySessions = nonStudySessions.filter(s => s.id !== id);
    if (currentUser) {
      try { await deleteDoc(doc(db, 'users', currentUser.uid, 'nonStudySessions', id)); }
      catch(e) {
        showToast('No internet! Deleted offline.');
        localSet('st_nonstudy_sessions', nonStudySessions);
      }
    } else { localSet('st_nonstudy_sessions', nonStudySessions); }
  } else {
    sessions = sessions.filter(s => s.id !== id);
    if(currentUser){
      try { await deleteDoc(doc(db,'users',currentUser.uid,'sessions',id)); }
      catch(e) {
        showToast('No internet! Deleted offline.');
        localSet(LS_SESSIONS, sessions);
      }
    } else { localSet(LS_SESSIONS, sessions); }
  }
}

async function savePrefs(){
  if(currentUser){
    try { await setDoc(doc(db,'users',currentUser.uid,'meta','prefs'), prefs); }
    catch(e) {
      showToast('No internet! Settings saved offline.');
      localSet(LS_PREFS, prefs);
    }
  } else { localSet(LS_PREFS, prefs); }
}

/* ---------- auth ---------- */
const authbar = document.getElementById('authbar');

document.getElementById('googleBtn').addEventListener('click', async ()=>{
  try{ await signInWithPopup(auth, provider); }
  catch(e){ showToast('Sign in failed - check if domain is added in Firebase console'); }
});

onAuthStateChanged(auth, async (user)=>{
  currentUser = user;
  renderAuthBar();
  
  if (currentUser) {
    const localSess = localGet(LS_SESSIONS, []);
    const localNS = localGet('st_nonstudy_sessions', []);
    const localP = localGet(LS_PREFS, null);
    
    const snap = await getDocs(collection(db,'users',currentUser.uid,'sessions'));
    const fbSess = snap.docs.map(d => ({ id:d.id, ...d.data() }));
    
    if (localSess.length > 0) {
      for (const s of localSess) {
        const exists = fbSess.find(fs => fs.ts === s.ts);
        if(!exists) {
          const rec = { date: s.date, subject: s.subject, minutes: s.minutes, ts: s.ts, workType: s.workType || 'Other' };
          await addDoc(collection(db,'users',currentUser.uid,'sessions'), rec);
        }
      }
      localStorage.removeItem(LS_SESSIONS);
    }

    const snapNS = await getDocs(collection(db,'users',currentUser.uid,'nonStudySessions'));
    const fbNS = snapNS.docs.map(d => ({ id:d.id, ...d.data(), isNonStudy: true }));
    
    if (localNS.length > 0) {
      for (const s of localNS) {
        const exists = fbNS.find(fs => fs.ts === s.ts);
        if(!exists) {
          const rec = { date: s.date, subject: s.subject, minutes: s.minutes, ts: s.ts, workType: s.workType || 'Other' };
          await addDoc(collection(db,'users',currentUser.uid,'nonStudySessions'), rec);
        }
      }
      localStorage.removeItem('st_nonstudy_sessions');
    }
    
    const prefSnap = await getDoc(doc(db,'users',currentUser.uid,'meta','prefs'));
    let fbPrefs = prefSnap.exists() ? prefSnap.data() : { dailyTarget:120, subjects: DEFAULT_SUBJECTS.map(s=>({...s})), workTypes: [...DEFAULT_WORK_TYPES] };
    
    if (localP) {
      if(localP.subjects) {
        const currentNames = fbPrefs.subjects.map(s => typeof s === 'string' ? s : s.name);
        localP.subjects.forEach(ls => {
          const name = typeof ls === 'string' ? ls : ls.name;
          if (!currentNames.includes(name)) {
            const coreMatch = DEFAULT_SUBJECTS.find(d => d.name === name);
            if (coreMatch) fbPrefs.subjects.push({ ...coreMatch });
            else fbPrefs.subjects.push(typeof ls === 'string' ? { name: ls, isCore: false } : ls);
            currentNames.push(name);
          }
        });
      }
      if(localP.workTypes) {
        localP.workTypes.forEach(w => {
          if(!fbPrefs.workTypes.includes(w)) fbPrefs.workTypes.push(w);
        });
      }
      fbPrefs.dailyTarget = localP.dailyTarget || fbPrefs.dailyTarget;
      localStorage.removeItem(LS_PREFS);
    }
    await setDoc(doc(db,'users',currentUser.uid,'meta','prefs'), fbPrefs);
  } else {
    if (unsubSessions) { unsubSessions(); unsubSessions = null; }
    if (unsubNonStudySessions) { unsubNonStudySessions(); unsubNonStudySessions = null; }
  }
  
  await loadAll();
  
  if(selectedSubject !== '' && !prefs.subjects.find(s => s.name === selectedSubject)) {
    selectedSubject = prefs.subjects[0]?.name || '';
  }
  if(selectedWorkType !== '' && !prefs.workTypes.includes(selectedWorkType)) {
    selectedWorkType = prefs.workTypes[0] || '';
  }
  
  renderSelectionChips();
  initTimerFromLocalState();
  await refreshEverything();
});

function renderAuthBar(){
  if(currentUser){
    authbar.innerHTML = `
      <div class="userchip">
        <img src="${currentUser.photoURL || ''}" alt="${currentUser.displayName || 'User'} profile picture" onerror="this.style.display='none'">
        <span>${currentUser.displayName || currentUser.email || 'User'}</span>
      </div>
      <button class="signout" id="signOutBtn">Sign Out</button>`;
    document.getElementById('signOutBtn').addEventListener('click', ()=> signOut(auth));
  } else {
    authbar.innerHTML = `<button class="gbtn" id="googleBtn2">Sign in with Google</button>`;
    document.getElementById('googleBtn2').addEventListener('click', async ()=>{
      try{ await signInWithPopup(auth, provider); }
      catch(e){ showToast('Sign in failed - check if domain is added in Firebase console'); }
    });
  }
}

/* ---------- date helpers ---------- */
function todayStr(d=new Date()){ return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'); }
function daysAgoStr(n){ const d=new Date(); d.setDate(d.getDate()-n); return todayStr(d); }
function formatDateTime(ts){
  if(!ts) return '';
  const d = new Date(ts);
  let h = d.getHours();
  const m = String(d.getMinutes()).padStart(2, '0');
  const ampm = h >= 12 ? 'PM' : 'AM';
  h = h % 12; h = h ? h : 12;
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const yyyy = d.getFullYear();
  return `${h}:${m} ${ampm}, ${dd}-${mm}-${yyyy}`;
}
function formatTimeOnly(ts) {
  if(!ts) return '';
  const d = new Date(ts);
  let h = d.getHours();
  const m = String(d.getMinutes()).padStart(2, '0');
  const ampm = h >= 12 ? 'PM' : 'AM';
  h = h % 12; h = h ? h : 12;
  return `${h}:${m} ${ampm}`;
}
function formatDateOnly(ts) {
  if(!ts) return '';
  const d = new Date(ts);
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const yyyy = d.getFullYear();
  return `${dd}-${mm}-${yyyy}`;
}
function formatTimeRange(endTs, minutes) {
  if (!endTs) return '';
  const startTs = endTs - (minutes * 60 * 1000);
  const startStr = formatTimeOnly(startTs);
  const endStr = formatTimeOnly(endTs);
  const dateStr = formatDateOnly(endTs);
  return `${startStr} - ${endStr}, ${dateStr}`;
}
function formatTimeRangeOnlyTime(endTs, minutes) {
  if (!endTs) return '';
  const startTs = endTs - (minutes * 60 * 1000);
  const startStr = formatTimeOnly(startTs);
  const endStr = formatTimeOnly(endTs);
  return `${startStr} - ${endStr}`;
}
const bnDayShort = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];

/* ---------- audio + vibration + notify ---------- */
let audioCtx;
let isMuted = false;
const muteBtn = document.getElementById('muteBtn');
muteBtn.addEventListener('click', (e)=>{
  isMuted = !isMuted;
  muteBtn.textContent = isMuted ? '🔇' : '🔊';
  muteBtn.className = 'audio-btn ' + (isMuted ? 'muted' : '');
});

function playChime(){
  if(isMuted) return;
  try{
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    const now = audioCtx.currentTime;
    [523.25, 659.25, 783.99, 1046.50].forEach((freq,i)=>{
      const osc = audioCtx.createOscillator(); 
      const gain = audioCtx.createGain();
      osc.type='sine'; 
      osc.frequency.value=freq;
      gain.gain.setValueAtTime(0.0001, now+i*0.1);
      gain.gain.exponentialRampToValueAtTime(0.2, now+i*0.1+0.05);
      gain.gain.exponentialRampToValueAtTime(0.0001, now+i*0.1+0.8);
      osc.connect(gain).connect(audioCtx.destination);
      osc.start(now+i*0.1); 
      osc.stop(now+i*0.1+0.85);
    });
  }catch(e){}
}
function vibrate(){ if(navigator.vibrate) navigator.vibrate([250,120,250,120,250]); }
function notify(msg){
  if('Notification' in window && Notification.permission==='granted'){
    try{ new Notification("Time's up! ⏰", { body: msg }); }catch(e){}
  }
}
if('Notification' in window && Notification.permission==='default'){
  document.addEventListener('click', function reqPerm(){ Notification.requestPermission(); document.removeEventListener('click', reqPerm); }, { once:true });
}
let toastTimer;
function showToast(msg){
  const t = document.getElementById('toast'); t.textContent = msg; t.classList.add('show');
  clearTimeout(toastTimer); toastTimer = setTimeout(()=> t.classList.remove('show'), 3500);
}

/* ---------- subjects & work type UI ---------- */
let selectedSubject = '';
let selectedSubMenu = '';
let selectedWorkType = '';
let isTimerActive = false;
let isEditMode = false;

const editModeToggle = document.getElementById('editModeToggle');
if (editModeToggle) {
  editModeToggle.addEventListener('click', () => {
    isEditMode = !isEditMode;
    if (isEditMode) {
      editModeToggle.classList.add('active');
      editModeToggle.innerHTML = '✓ Done';
    } else {
      editModeToggle.classList.remove('active');
      editModeToggle.innerHTML = '✏️ Edit';
    }
    renderSelectionChips();
  });
}

function renderSelectionChips(){
  const area1 = document.getElementById('subjSelectChips');
  const area2 = document.getElementById('subMenuChips');
  const area3 = document.getElementById('workTypeChips');
  
  if(isTimerActive){
    document.getElementById('subjectSelectionArea').classList.add('locked-chips');
    document.getElementById('workTypeSelectionArea').classList.add('locked-chips');
  } else {
    document.getElementById('subjectSelectionArea').classList.remove('locked-chips');
    document.getElementById('workTypeSelectionArea').classList.remove('locked-chips');
  }

  area1.innerHTML = prefs.subjects.map(s => {
    const isC = s.isCore ? 'core-chip' : '';
    const isA = (s.name === selectedSubject) ? 'active' : '';
    let visualClass = '';
    if(isTimerActive) visualClass = isA ? 'timer-glow' : 'inactive-dim';
    const safeName = escapeHTML(s.name);
    return `<div class="sel-chip ${isC} ${isA} ${visualClass}" data-name="${safeName}">${safeName}</div>`;
  }).join('') + (!isTimerActive && isEditMode ? `<div class="sel-chip add-chip" data-action="add-subject" style="border:1px dashed var(--accent); color:var(--accent); background:transparent;">＋ Subject</div>` : '');

  const subjObj = prefs.subjects.find(s => s.name === selectedSubject);
  if(subjObj && ((subjObj.sub && subjObj.sub.length > 0) || !isTimerActive)) {
    area2.style.display = 'flex';
    if(subjObj.sub && subjObj.sub.length > 0) {
      if(!selectedSubMenu || !subjObj.sub.includes(selectedSubMenu)) selectedSubMenu = subjObj.sub[0];
      area2.innerHTML = subjObj.sub.map(sub => {
        const isA = (sub === selectedSubMenu) ? 'active' : '';
        let visualClass = '';
        if(isTimerActive) visualClass = isA ? 'timer-glow' : 'inactive-dim';
        const safeSub = escapeHTML(sub);
        return `<div class="sel-chip ${isA} ${visualClass}" data-sub="${safeSub}">↳ ${safeSub}</div>`;
      }).join('') + (!isTimerActive && isEditMode ? `<div class="sel-chip add-chip" data-action="add-sub" style="border:1px dashed var(--accent); color:var(--accent); background:transparent;">＋ Sub-Subject</div>` : '');
    } else {
      selectedSubMenu = '';
      area2.innerHTML = (!isTimerActive && isEditMode ? `<div class="sel-chip add-chip" data-action="add-sub" style="border:1px dashed var(--accent); color:var(--accent); background:transparent;">＋ Sub-Subject</div>` : '');
    }
  } else {
    area2.style.display = 'none';
    selectedSubMenu = '';
  }

  area3.innerHTML = prefs.workTypes.map(w => {
    const isA = (w === selectedWorkType) ? 'active' : '';
    let visualClass = '';
    if(isTimerActive) visualClass = isA ? 'timer-glow' : 'inactive-dim';
    const safeW = escapeHTML(w);
    return `<div class="sel-chip ${isA} ${visualClass}" data-w="${safeW}">${safeW}</div>`;
  }).join('') + (!isTimerActive && isEditMode ? `<div class="sel-chip add-chip" data-action="add-worktype" style="border:1px dashed var(--accent); color:var(--accent); background:transparent;">＋ Work Type</div>` : '');
}

function handleChipInteraction(e, type) {
  if (isTimerActive) return;
  const chip = e.target.closest('.sel-chip');
  if (!chip) return;
  
  if (e.type === 'click') {
    const action = chip.dataset.action;
    if (action) {
      if(action === 'add-subject') openAddModal('subject');
      if(action === 'add-sub') openAddModal('sub');
      if(action === 'add-worktype') openAddModal('workType');
      return;
    }
    if (type === 'subject') { 
      selectedSubject = (selectedSubject === chip.dataset.name) ? '' : chip.dataset.name; 
      selectedSubMenu = ''; 
    }
    if (type === 'sub') { 
      selectedSubMenu = (selectedSubMenu === chip.dataset.sub) ? '' : chip.dataset.sub; 
    }
    if (type === 'workType') { 
      selectedWorkType = (selectedWorkType === chip.dataset.w) ? '' : chip.dataset.w; 
    }
    renderSelectionChips();
  } else if (e.type === 'dblclick' || e.type === 'longpress' || e.type === 'contextmenu') {
    if (chip.classList.contains('add-chip')) return;
    e.preventDefault();
    if (type === 'subject') openEditModal('subject', chip.dataset.name);
    if (type === 'sub') openEditModal('sub', chip.dataset.sub);
    if (type === 'workType') openEditModal('workType', chip.dataset.w);
  }
}

let pressTimer = null;
const setupDelegation = (areaId, type) => {
  const el = document.getElementById(areaId);
  if (!el) return;
  el.addEventListener('click', (e) => handleChipInteraction(e, type));
  el.addEventListener('dblclick', (e) => handleChipInteraction(e, type));
  el.addEventListener('contextmenu', (e) => handleChipInteraction(e, type));
  el.addEventListener('touchstart', (e) => {
    if(isTimerActive) return;
    const chip = e.target.closest('.sel-chip');
    if (!chip) return;
    pressTimer = setTimeout(() => {
      const pseudoEvent = { type: 'longpress', target: chip, preventDefault: () => {} };
      handleChipInteraction(pseudoEvent, type);
    }, 600);
  });
  el.addEventListener('touchend', () => clearTimeout(pressTimer));
  el.addEventListener('touchmove', () => clearTimeout(pressTimer));
};
setupDelegation('subjSelectChips', 'subject');
setupDelegation('subMenuChips', 'sub');
setupDelegation('workTypeChips', 'workType');

/* ---------- Modals ---------- */
const editModal = document.getElementById('editModal');
const modalInput = document.getElementById('modalInput');
const modalTitle = document.getElementById('modalTitle');
let editContext = { type: null, oldName: null }; 

function openEditModal(type, name) {
  editContext = { type, oldName: name };
  if(type === 'subject') modalTitle.textContent = 'Edit Subject';
  else if(type === 'sub') modalTitle.textContent = 'Edit Sub-Subject';
  else modalTitle.textContent = 'Edit Work Type';
  modalInput.value = name;
  editModal.classList.add('show');
}

function closeEditModal() {
  editModal.classList.remove('show');
  editContext = { type: null, oldName: null };
}

document.getElementById('modalCancel').addEventListener('click', closeEditModal);

document.getElementById('modalDelete').addEventListener('click', async () => {
  const { type, oldName } = editContext;
  
  if (type === 'subject') {
    if (prefs.subjects.length <= 1) { showToast('At least one subject is required'); return; }
    const subj = prefs.subjects.find(s => s.name === oldName);
    if (subj && subj.isCore) {
      if (!confirm('Are you sure you want to delete this core subject?')) return;
    }
    prefs.subjects = prefs.subjects.filter(s => s.name !== oldName);
    if (selectedSubject === oldName) selectedSubject = prefs.subjects[0].name;
  } 
  else if (type === 'sub') {
    const subj = prefs.subjects.find(s => s.name === selectedSubject);
    if (subj && subj.sub) {
      if (subj.sub.length <= 1) { showToast('At least one sub-subject is required'); return; }
      subj.sub = subj.sub.filter(x => x !== oldName);
      if (selectedSubMenu === oldName) selectedSubMenu = subj.sub[0];
    }
  }
  else if (type === 'workType') {
    if (prefs.workTypes.length <= 1) { showToast('At least one work type is required'); return; }
    prefs.workTypes = prefs.workTypes.filter(w => w !== oldName);
    if (selectedWorkType === oldName) selectedWorkType = prefs.workTypes[0];
  }
  
  await savePrefs();
  renderSelectionChips();
  closeEditModal();
});

document.getElementById('modalSave').addEventListener('click', async () => {
  const { type, oldName } = editContext;
  const newName = sanitizeHTML(modalInput.value.trim());
  if (!newName || newName === oldName) { closeEditModal(); return; }

  if (type === 'subject') {
    if (prefs.subjects.find(s => s.name === newName)) { showToast('This name already exists'); return; }
    const subj = prefs.subjects.find(s => s.name === oldName);
    if (subj && subj.isCore) {
      if (!confirm('Are you sure you want to change this core subject?')) return;
    }
    subj.name = newName;
    if (selectedSubject === oldName) selectedSubject = newName;
  } 
  else if (type === 'sub') {
    const subj = prefs.subjects.find(s => s.name === selectedSubject);
    if (subj && subj.sub) {
      if (subj.sub.includes(newName)) { showToast('This name already exists'); return; }
      const idx = subj.sub.indexOf(oldName);
      if (idx !== -1) subj.sub[idx] = newName;
      if (selectedSubMenu === oldName) selectedSubMenu = newName;
    }
  }
  else if (type === 'workType') {
    if (prefs.workTypes.includes(newName)) { showToast('This name already exists'); return; }
    const idx = prefs.workTypes.indexOf(oldName);
    if (idx !== -1) prefs.workTypes[idx] = newName;
    if (selectedWorkType === oldName) selectedWorkType = newName;
  }

  await savePrefs();
  renderSelectionChips();
  closeEditModal();
});

const addModal = document.getElementById('addModal');
const addModalTitle = document.getElementById('addModalTitle');
const addModalInput = document.getElementById('addModalInput');
const addModalOptions = document.getElementById('addModalOptions');
const modalIsCoreCheck = document.getElementById('modalIsCoreCheck');
const modalIsNonStudyCheck = document.getElementById('modalIsNonStudyCheck');

let addContextType = null;

function openAddModal(type) {
  addContextType = type;
  addModalInput.value = '';
  modalIsCoreCheck.checked = false;
  modalIsNonStudyCheck.checked = false;
  
  if (type === 'subject') {
    addModalTitle.textContent = 'Add New Subject';
    addModalOptions.style.display = 'block';
  } else if (type === 'sub') {
    addModalTitle.textContent = 'Add New Sub-Subject';
    addModalOptions.style.display = 'none';
  } else if (type === 'workType') {
    addModalTitle.textContent = 'Add New Work Type';
    addModalOptions.style.display = 'none';
  }
  
  addModal.classList.add('show');
  setTimeout(() => addModalInput.focus(), 100);
}

function closeAddModal() { addModal.classList.remove('show'); }

document.getElementById('addModalCancel').addEventListener('click', closeAddModal);

document.getElementById('addModalSave').addEventListener('click', async () => {
  const val = sanitizeHTML(addModalInput.value.trim());
  if (!val) return;
  
  if (addContextType === 'subject') {
    if(prefs.subjects.find(s => s.name === val)){ showToast('This subject already exists'); return; }
    prefs.subjects.push({ name: val, isCore: modalIsCoreCheck.checked, isNonStudy: modalIsNonStudyCheck.checked }); 
    selectedSubject = val;
  } else if (addContextType === 'sub') {
    if(!selectedSubject) { showToast('Select a subject first'); return; }
    const subjObj = prefs.subjects.find(s => s.name === selectedSubject);
    if(subjObj) {
      if(!subjObj.sub) subjObj.sub = [];
      if(subjObj.sub.includes(val)) { showToast('This sub-subject already exists!'); return; }
      subjObj.sub.push(val);
      selectedSubMenu = val;
    }
  } else if (addContextType === 'workType') {
    if(prefs.workTypes.includes(val)){ showToast('This work type already exists'); return; }
    prefs.workTypes.push(val); 
    selectedWorkType = val;
  }
  
  await savePrefs(); 
  renderSelectionChips();
  closeAddModal();
});

// Stub for dial to prevent reference errors
function updateDial() {}


/* ---------- pomodoro ---------- */
const pomoToggle = document.getElementById('pomoToggle');
const customTimeRow = document.getElementById('customTimeRow');
const phaseBadge = document.getElementById('phaseBadge');
let pomodoroMode = false;
let pomoPhase = 'work';
const WORK_MIN = 25, BREAK_MIN = 5;

pomoToggle.addEventListener('change', ()=>{
  pomodoroMode = pomoToggle.checked;
  customTimeRow.style.display = pomodoroMode ? 'none' : 'flex';
  phaseBadge.style.display = pomodoroMode ? 'block' : 'none';
  pomoPhase = 'work';
  updatePhaseBadge();
});
function updatePhaseBadge(){
  phaseBadge.textContent = pomoPhase === 'work' ? '⏳ Focus Session (25 mins)' : '☕ Break (5 mins)';
}

/* ---------- timer core ---------- */
const hoursEl = document.getElementById('hours');
const minutesEl = document.getElementById('minutes');
const displayEl = document.getElementById('display');
const hintEl = document.getElementById('hint');
const startBtn = document.getElementById('startBtn');
const pauseBtn = document.getElementById('pauseBtn');
const resetBtn = document.getElementById('resetBtn');

let totalSeconds=0, endTime=null, remaining=0, running=false;
let tickHandle=null;
let stopwatchMode=false, startAt=null;

function fmt(sec){
  sec = Math.max(0, sec);
  const h=String(Math.floor(sec/3600)).padStart(2,'0');
  const m=String(Math.floor((sec%3600)/60)).padStart(2,'0');
  const s=String(Math.floor(sec%60)).padStart(2,'0');
  return h+':'+m+':'+s;
}

function render(){
  let secLeft;
  
  // MODE 1: IDLE - Digital display shows timer only (00:00:00).
  // Local time is shown ONLY in the golden dial.
  if (!isTimerActive && !running && totalSeconds === 0 && remaining === 0) {
    displayEl.textContent = fmt(0);
    startBtn.disabled = false; 
    pauseBtn.disabled = true;
    return;
  }
  
  // MODE 2/3: TIMER RUNNING or PAUSED
  if(stopwatchMode){
    secLeft = running ? (Date.now()-startAt)/1000 : remaining;
  } else {
    secLeft = running ? Math.max(0,(endTime-Date.now())/1000) : remaining;
  }
  displayEl.textContent = fmt(secLeft);
  startBtn.disabled = running; pauseBtn.disabled = !running;
  
  // Update dial hands
  updateDial(secLeft);
}

function tick(){
  if(!running) return;
  if(stopwatchMode){ render(); return; }
  const secLeft = (endTime-Date.now())/1000;
  if(secLeft<=0){ finish(); return; }
  render();
}

async function finish(){
  const wasStopwatch = stopwatchMode;
  const saveTotal = totalSeconds;
  running=false; remaining=0; totalSeconds=0;
  clearInterval(tickHandle);
  releaseWakeLock();
  
  isTimerActive = false;
  renderSelectionChips();
  displayEl.classList.remove('glowing-timer-active');

  const sname = selectedSubMenu ? `${selectedSubject} - ${selectedSubMenu}` : selectedSubject;

  if(pomodoroMode){
    if(pomoPhase==='work'){
      playChime(); vibrate(); notify('Focus session ended - take a break');
      showToast('⏳ Focus session ended! Now 5 mins break');
      triggerConfetti();
      await addSession(sname, WORK_MIN, selectedWorkType);
      pomoPhase='break';
    } else {
      playChime(); vibrate(); notify('Break ended - back to focus');
      showToast('☕ Break ended! Start again');
      pomoPhase='work';
    }
    updatePhaseBadge();
    hintEl.textContent = pomoPhase==='work' ? 'Press Start for next session' : 'Press Start to begin break';
  } else {
    playChime(); vibrate(); notify(sname + ' study time ended');
    showToast('⏰ ' + sname + " — time's up!");
    triggerConfetti();
    const elapsedSec = wasStopwatch
      ? Math.max(0, (Date.now() - startAt) / 1000)
      : Math.max(0, saveTotal);
    const mins = Math.floor(elapsedSec / 60);
    if (mins >= 1) await addSession(sname, mins, selectedWorkType);
    hintEl.textContent = "Time's up! Press Start to begin again";
  }
  startBtn.disabled=false; pauseBtn.disabled=true;
  hoursEl.disabled=false; minutesEl.disabled=false;

  localStorage.removeItem(LS_TIMERSTATE);
  render(); // Return to clock mode
  await refreshEverything();
}

function startTimer(fromResume=false){
  if(!selectedSubject) {
    showToast('Select a subject first');
    return;
  }
  if(!fromResume){
    if(pomodoroMode){
      totalSeconds = (pomoPhase==='work' ? WORK_MIN : BREAK_MIN) * 60;
      stopwatchMode = false;
    } else {
      const rawH = parseInt(hoursEl.value) || 0;
      const rawM = parseInt(minutesEl.value) || 0;
      const h = Math.min(12, Math.max(0, rawH));
      const m = Math.min(59, Math.max(0, rawM));
      totalSeconds = h * 3600 + m * 60;
      stopwatchMode = totalSeconds <= 0;
    }
    remaining = stopwatchMode ? 0 : totalSeconds;
  }
  running=true;
  if(stopwatchMode){
    startAt = Date.now() - remaining*1000;
  } else {
    endTime = Date.now() + remaining*1000;
  }
  if(pomodoroMode){
    hintEl.textContent = pomoPhase==='work' ? selectedSubject+' studying...' : 'break is running...';
  } else if(stopwatchMode){
    hintEl.textContent = selectedSubject+' — stopwatch running... (Press Reset to save time)';
  } else {
    hintEl.textContent = selectedSubject + ' — countdown running...';
  }
  hoursEl.disabled=true; minutesEl.disabled=true;
  isTimerActive = true; renderSelectionChips();
  
  displayEl.classList.add('glowing-timer-active');
  requestWakeLock();
  
  render(); clearInterval(tickHandle); tickHandle = setInterval(tick,30);
  
  localSet(LS_TIMERSTATE, { 
    running:true, endTime, totalSeconds, subject: selectedSubject, 
    subMenu: selectedSubMenu, workType: selectedWorkType,
    pomodoroMode, pomoPhase, stopwatchMode, startAt 
  });
}

function pauseTimer(){
  running=false;
  releaseWakeLock();
  if(stopwatchMode){
    remaining = (Date.now()-startAt)/1000;
  } else {
    remaining = Math.max(0,(endTime-Date.now())/1000);
  }
  hintEl.textContent = stopwatchMode ? 'Paused - press Start to resume (Press Reset to save time)' : 'Paused - press Start to resume';
  render();
  displayEl.classList.remove('glowing-timer-active');
  
  localSet(LS_TIMERSTATE, { 
    running:false, remaining, totalSeconds, subject: selectedSubject, 
    subMenu: selectedSubMenu, workType: selectedWorkType,
    pomodoroMode, pomoPhase, stopwatchMode, startAt, endTime 
  });
}

async function resetTimer(){
  const wasRunning = running;
  const wasStopwatch = stopwatchMode;
  running=false; clearInterval(tickHandle);
  releaseWakeLock();

  let finalElapsed = 0;
  if(wasStopwatch){
    finalElapsed = wasRunning ? (Date.now()-startAt)/1000 : remaining;
  } else if (totalSeconds > 0) {
    const secLeft = wasRunning ? Math.max(0, (endTime-Date.now())/1000) : remaining;
    finalElapsed = totalSeconds - secLeft;
  }

  if (finalElapsed > 0) {
    const mins = Math.floor(finalElapsed/60);
    if (pomodoroMode && pomoPhase === 'break') {
      showToast('☕ Break cancelled');
    } else if(mins>=1){
      const sname = selectedSubMenu ? `${selectedSubject} - ${selectedSubMenu}` : selectedSubject;
      try {
        await addSession(sname, mins, selectedWorkType);
        showToast(`✅ ${mins} mins saved`);
        await refreshEverything();
      } catch(e) {
        showToast('⚠️ Failed to save');
      }
    } else {
      showToast('Not saved as it was less than 1 min');
    }
  }

  remaining=0; totalSeconds=0; endTime=null; startAt=null; stopwatchMode=false;
  hintEl.textContent='Press Start to begin studying';
  hoursEl.disabled=false; minutesEl.disabled=false;
  startBtn.disabled=false; pauseBtn.disabled=true;
  
  isTimerActive = false;
  renderSelectionChips();
  
  pomoPhase='work'; updatePhaseBadge();
  localStorage.removeItem(LS_TIMERSTATE);
  render(); // Return to clock mode
}

startBtn.addEventListener('click', ()=> startTimer(isTimerActive));
pauseBtn.addEventListener('click', pauseTimer);
resetBtn.addEventListener('click', resetTimer);

/* ---------- Continuous render loop for idle clock ---------- */
function clockLoop() {
  // Dial always shows local time, even while stopwatch/countdown is running
  updateDial();
  if (!isTimerActive && !running && totalSeconds === 0 && remaining === 0) {
    render();
  }
  requestAnimationFrame(clockLoop);
}
clockLoop();

/* ---------- fullscreen auto-hide & toggle ---------- */
const fsContainer = document.getElementById('fsContainer');
const fullscreenBtn = document.getElementById('fullscreenBtn');
fullscreenBtn.addEventListener('click', () => {
  if (!document.fullscreenElement) {
    fsContainer.requestFullscreen().then(() => {
      if(screen.orientation && screen.orientation.lock) {
        screen.orientation.lock('landscape').catch(e => console.log('Orientation lock failed:', e));
      }
    }).catch(err => console.log(err));
  } else {
    document.exitFullscreen().then(() => {
      if(screen.orientation && screen.orientation.unlock) {
        screen.orientation.unlock();
      }
    });
  }
});

let fsTimeout;
document.addEventListener('mousemove', () => {
  if (document.fullscreenElement) {
    fsContainer.style.cursor = 'default';
    clearTimeout(fsTimeout);
    fsTimeout = setTimeout(() => {
      fsContainer.style.cursor = 'none';
    }, 3000);
  }
});

document.addEventListener('fullscreenchange', () => {
  if (document.fullscreenElement) {
    fsContainer.classList.add('is-fullscreen');
    fullscreenBtn.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>';
  } else {
    fsContainer.classList.remove('is-fullscreen');
    fullscreenBtn.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>';
    fsContainer.style.cursor = 'default';
    clearTimeout(fsTimeout);
  }
});

/* ---------- target progress ---------- */
const targetInput = document.getElementById('targetInput');
targetInput.addEventListener('change', async ()=>{
  prefs.dailyTarget = Math.max(10, parseInt(targetInput.value)||120);
  await savePrefs(); renderTarget();
});
function renderTarget(){
  targetInput.value = prefs.dailyTarget;
  const today = todayStr();
  const done = sessions.filter(s=>s.date===today).reduce((a,s)=>a+s.minutes,0);
  const pct = Math.min(100, Math.round(done/prefs.dailyTarget*100));
  document.getElementById('targetFill').style.width = pct+'%';
  document.getElementById('targetDone').textContent = done+' mins';
  document.getElementById('targetPct').textContent = pct+'%';
}

/* ---------- level / xp ---------- */
function renderLevel(){
  const total = sessions.reduce((a,s)=>a+s.minutes,0);
  const XP_PER_LEVEL = 300; 
  const level = Math.floor(total/XP_PER_LEVEL)+1;
  const xp = total % XP_PER_LEVEL;
  const levelNumEl = document.getElementById('levelNum');
  if (levelNumEl) levelNumEl.textContent = 'Level ' + level;
  const xpMetaEl = document.getElementById('xpMeta');
  if (xpMetaEl) xpMetaEl.textContent = xp+' / '+XP_PER_LEVEL+' XP';
  const xpFillEl = document.getElementById('xpFill');
  if (xpFillEl) xpFillEl.style.width = Math.round(xp/XP_PER_LEVEL*100)+'%';

  const totalFocusHrsEl = document.getElementById('totalFocusHrs');
  if (totalFocusHrsEl) totalFocusHrsEl.textContent = (total / 60).toFixed(1);
  const totalFocusSessionsEl = document.getElementById('totalFocusSessions');
  if (totalFocusSessionsEl) totalFocusSessionsEl.textContent = sessions.length;
}

/* ---------- week comparison ---------- */
function renderCompare(){
  const thisWeek = sessions.filter(s=> s.date >= daysAgoStr(6)).reduce((a,s)=>a+s.minutes,0);
  const lastWeekDays = new Set(); for(let i=13;i>=7;i--) lastWeekDays.add(daysAgoStr(i));
  const lastWeek = sessions.filter(s=>lastWeekDays.has(s.date)).reduce((a,s)=>a+s.minutes,0);
  const el = document.getElementById('compareLine');
  if(lastWeek===0 && thisWeek===0){ el.textContent=''; return; }
  if(lastWeek===0){ el.innerHTML = 'Studied for <b>'+thisWeek+'</b> mins this week'; return; }
  const diff = Math.round((thisWeek-lastWeek)/lastWeek*100);
  const cls = diff>=0 ? 'up' : 'down';
  const arrow = diff>=0 ? '▲' : '▼';
  el.innerHTML = '<span class="'+cls+'">'+arrow+' '+Math.abs(diff)+'%</span> '+(diff>=0?'more':'less')+' than last week';
}

/* ---------- stats tabs ---------- */
let statsMode='daily';
const tabDaily=document.getElementById('tabDaily'), tabWeekly=document.getElementById('tabWeekly');
const chartEl=document.getElementById('chart'), todayTotalEl=document.getElementById('todayTotal');
tabDaily.addEventListener('click', ()=>{ statsMode='daily'; tabDaily.classList.add('active'); tabWeekly.classList.remove('active'); renderStats(); });
tabWeekly.addEventListener('click', ()=>{ statsMode='weekly'; tabWeekly.classList.add('active'); tabDaily.classList.remove('active'); renderStats(); });

function barRow(label,minutes,pct){
  return '<div class="bar-row"><div class="bar-label">'+label+'</div>'
    + '<div class="bar-track"><div class="bar-fill" style="width:'+pct+'%"></div></div>'
    + '<div class="bar-value">'+minutes+' mins</div></div>';
}
function renderStats(){
  if(statsMode==='daily'){
    const today = todayStr();
    const todaySessions = sessions.filter(s=>s.date===today);
    const bySubject={}; todaySessions.forEach(s=>{ bySubject[s.subject]=(bySubject[s.subject]||0)+s.minutes; });
    const entries = Object.entries(bySubject).sort((a,b)=>b[1]-a[1]);
    const total = todaySessions.reduce((a,s)=>a+s.minutes,0);
    if(entries.length===0){ chartEl.innerHTML='<div class="empty-note">No sessions completed today yet</div>'; todayTotalEl.innerHTML=''; return; }
    const max = Math.max(...entries.map(e=>e[1]));
    chartEl.innerHTML = entries.map(([s,m])=>barRow(s,m,Math.max(6,Math.round(m/max*100)))).join('');
    todayTotalEl.innerHTML = 'Total time today: <b>'+total+' mins</b>';
  } else {
    const days=[]; for(let i=6;i>=0;i--) days.push(daysAgoStr(i));
    const totals = days.map(d=>sessions.filter(s=>s.date===d).reduce((a,s)=>a+s.minutes,0));
    const grand = totals.reduce((a,b)=>a+b,0);
    if(grand===0){ chartEl.innerHTML='<div class="empty-note">No sessions completed in the last 7 days</div>'; todayTotalEl.innerHTML=''; return; }
    const max = Math.max(...totals,1);
    chartEl.innerHTML = days.map((d,i)=>{
      const parts = d.split('-');
      const dt = new Date(parts[0], parts[1]-1, parts[2]); 
      const label = bnDayShort[dt.getDay()];
      const pct = totals[i]===0?0:Math.max(6,Math.round(totals[i]/max*100));
      return barRow(label, totals[i], pct);
    }).join('');
    todayTotalEl.innerHTML = 'Total time in last 7 days: <b>'+grand+' mins</b>';
  }
}

/* ---------- streak ---------- */
let currentStreakVal = 0;
let bestStreakVal = 0;

function renderStreak(){
  const uniqueDates = [...new Set(sessions.map(s=>s.date))].sort();
  if(uniqueDates.length===0){
    currentStreakVal = 0;
    bestStreakVal = 0;
    const sNum = document.getElementById('streakNum');
    if (sNum) sNum.textContent = '0';
    const sBest = document.getElementById('streakBest');
    if (sBest) sBest.textContent = '0';
    return;
  }
  const dateSet = new Set(uniqueDates);
  let current=0; let cursor=new Date();
  if(!dateSet.has(todayStr(cursor))) cursor.setDate(cursor.getDate()-1);
  while(dateSet.has(todayStr(cursor))){ current++; cursor.setDate(cursor.getDate()-1); }
  let best=1, run=1;
  for(let i=1;i<uniqueDates.length;i++){
    const p1 = uniqueDates[i-1].split('-');
    const p2 = uniqueDates[i].split('-');
    const prev=new Date(p1[0], p1[1]-1, p1[2]), cur=new Date(p2[0], p2[1]-1, p2[2]);
    const diff=Math.round((cur-prev)/86400000);
    run = diff===1 ? run+1 : 1;
    if(run>best) best=run;
  }
  best=Math.max(best,current);
  currentStreakVal = current;
  bestStreakVal = best;
  const sNum = document.getElementById('streakNum');
  if (sNum) sNum.textContent = current;
  const sBest = document.getElementById('streakBest');
  if (sBest) sBest.textContent = best;
}

/* ---------- milestone badges system ---------- */
let currentBadgeFilter = 'all';

function calculateMilestoneBadges() {
  const totalStudyMinutes = sessions.reduce((a, s) => a + s.minutes, 0);
  const totalHours = Math.floor(totalStudyMinutes / 60);
  const maxSessionMin = sessions.reduce((max, s) => Math.max(max, s.minutes || 0), 0);
  const totalSessions = sessions.length;
  const streakToUse = Math.max(currentStreakVal, bestStreakVal);

  return [
    {
      id: "first_step",
      title: "First Step",
      description: "Completed your first study session",
      icon: "🌟",
      category: "Consistency",
      currentVal: Math.min(totalSessions, 1),
      targetVal: 1,
      unit: "session",
      isUnlocked: totalSessions >= 1
    },
    {
      id: "streak_3",
      title: "3-Day Streak",
      description: "Maintained study focus for 3 consecutive days",
      icon: "⚡",
      category: "Streak",
      currentVal: Math.min(streakToUse, 3),
      targetVal: 3,
      unit: "days",
      isUnlocked: streakToUse >= 3
    },
    {
      id: "streak_7",
      title: "7-Day Warrior",
      description: "Maintained study streak for a full week",
      icon: "🔥",
      category: "Streak",
      currentVal: Math.min(streakToUse, 7),
      targetVal: 7,
      unit: "days",
      isUnlocked: streakToUse >= 7
    },
    {
      id: "streak_10",
      title: "10-Day Streak",
      description: "Disciplined focus for 10 days in a row",
      icon: "🏆",
      category: "Streak",
      currentVal: Math.min(streakToUse, 10),
      targetVal: 10,
      unit: "days",
      isUnlocked: streakToUse >= 10
    },
    {
      id: "streak_30",
      title: "30-Day Master",
      description: "Elite consistency: 30-day continuous study habit",
      icon: "👑",
      category: "Streak",
      currentVal: Math.min(streakToUse, 30),
      targetVal: 30,
      unit: "days",
      isUnlocked: streakToUse >= 30
    },
    {
      id: "hours_10",
      title: "10 Hours Club",
      description: "Completed 10 hours of focused study",
      icon: "⏱️",
      category: "Study Hours",
      currentVal: Math.min(totalHours, 10),
      targetVal: 10,
      unit: "hours",
      isUnlocked: totalHours >= 10
    },
    {
      id: "hours_50",
      title: "50 Hours Studied",
      description: "Scholar milestone: 50 hours of total study time",
      icon: "🎓",
      category: "Study Hours",
      currentVal: Math.min(totalHours, 50),
      targetVal: 50,
      unit: "hours",
      isUnlocked: totalHours >= 50
    },
    {
      id: "hours_100",
      title: "100 Hours Titan",
      description: "Mastery milestone: 100 hours of deep study",
      icon: "💎",
      category: "Study Hours",
      currentVal: Math.min(totalHours, 100),
      targetVal: 100,
      unit: "hours",
      isUnlocked: totalHours >= 100
    },
    {
      id: "deep_focus",
      title: "Deep Focus",
      description: "Completed a continuous session of 60+ mins",
      icon: "🧘",
      category: "Focus",
      currentVal: maxSessionMin >= 60 ? 1 : 0,
      targetVal: 1,
      unit: "session",
      isUnlocked: maxSessionMin >= 60
    }
  ];
}

function renderMilestoneBadges() {
  const container = document.getElementById('milestoneBadgesGrid');
  const counterPill = document.getElementById('badgeCounterPill');
  if (!container) return;

  const badges = calculateMilestoneBadges();
  const unlockedCount = badges.filter(b => b.isUnlocked).length;
  if (counterPill) {
    counterPill.textContent = `${unlockedCount}/${badges.length} Earned 🏆`;
  }

  const filtered = badges.filter(b => {
    if (currentBadgeFilter === 'earned') return b.isUnlocked;
    if (currentBadgeFilter === 'locked') return !b.isUnlocked;
    return true;
  });

  if (filtered.length === 0) {
    container.innerHTML = `<div style="grid-column:1/-1; text-align:center; padding:20px; color:var(--text-dim); font-size:0.85rem;">No badges match this filter.</div>`;
    return;
  }

  container.innerHTML = filtered.map(b => {
    const pct = Math.min(100, Math.round((b.currentVal / b.targetVal) * 100));
    const statusClass = b.isUnlocked ? 'unlocked' : 'locked';
    const statusTag = b.isUnlocked ? 'Earned ✓' : 'Locked 🔒';

    return `
      <div class="milestone-badge-card ${statusClass}" data-id="${b.id}">
        <div class="mbadge-icon-wrap">${b.icon}</div>
        <div class="mbadge-title">${b.title}</div>
        <div class="mbadge-desc">${b.description}</div>
        <div class="mbadge-progress-wrap">
          <div class="mbadge-progress-bar">
            <div class="mbadge-progress-fill" style="width:${pct}%"></div>
          </div>
          <div class="mbadge-progress-text">
            <span>${b.currentVal}/${b.targetVal} ${b.unit}</span>
            <span>${pct}%</span>
          </div>
        </div>
        <div class="mbadge-status-tag">${statusTag}</div>
      </div>
    `;
  }).join('');

  container.querySelectorAll('.milestone-badge-card').forEach(card => {
    card.addEventListener('click', () => {
      const bId = card.getAttribute('data-id');
      const badge = badges.find(x => x.id === bId);
      if (badge) openBadgeDetailModal(badge);
    });
  });
}

function openBadgeDetailModal(badge) {
  const modal = document.getElementById('badgeDetailModal');
  if (!modal) return;
  const iconEl = document.getElementById('badgeModalIcon');
  if (iconEl) iconEl.textContent = badge.icon;
  const titleEl = document.getElementById('badgeModalTitle');
  if (titleEl) titleEl.textContent = badge.title;
  const catEl = document.getElementById('badgeModalCategory');
  if (catEl) catEl.textContent = `${badge.category} Milestone`;
  const descEl = document.getElementById('badgeModalDesc');
  if (descEl) descEl.textContent = badge.description;
  const progValEl = document.getElementById('badgeModalProgressVal');
  if (progValEl) progValEl.textContent = `${badge.currentVal} / ${badge.targetVal} ${badge.unit}`;
  
  const pct = Math.min(100, Math.round((badge.currentVal / badge.targetVal) * 100));
  const fillEl = document.getElementById('badgeModalFill');
  if (fillEl) fillEl.style.width = pct + '%';
  
  const statusEl = document.getElementById('badgeModalStatus');
  if (statusEl) {
    if (badge.isUnlocked) {
      statusEl.textContent = '✨ Earned & Unlocked!';
      statusEl.style.color = '#f59e0b';
    } else {
      const rem = badge.targetVal - badge.currentVal;
      statusEl.textContent = `🔒 Locked • ${rem} more ${badge.unit} needed`;
      statusEl.style.color = 'var(--text-dim)';
    }
  }

  modal.classList.add('show');
}

// Attach filter button listeners once DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  setupMilestoneEvents();
});

function setupMilestoneEvents() {
  document.querySelectorAll('.badge-filter-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.badge-filter-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentBadgeFilter = btn.getAttribute('data-filter');
      renderMilestoneBadges();
    });
  });

  const badgeModalClose = document.getElementById('badgeModalClose');
  if (badgeModalClose) {
    badgeModalClose.addEventListener('click', () => {
      document.getElementById('badgeDetailModal')?.classList.remove('show');
    });
  }
  const badgeDetailModal = document.getElementById('badgeDetailModal');
  if (badgeDetailModal) {
    badgeDetailModal.addEventListener('click', (e) => {
      if (e.target.id === 'badgeDetailModal') {
        e.target.classList.remove('show');
      }
    });
  }

  const statsPdfBtn = document.getElementById('statsPdfBtn');
  if (statsPdfBtn) {
    statsPdfBtn.addEventListener('click', () => {
      const exportBtn = document.getElementById('exportPdfBtn');
      if (exportBtn) exportBtn.click();
    });
  }
}
// Run immediately as well since module may execute after DOMContentLoaded
setupMilestoneEvents();

/* ============================================================
   TRANSLATION & PDF TEXT CLEANING HELPERS
   ============================================================ */
const b2eSubject = {
  'বাংলা': 'Bengali', 'ইংরেজি': 'English', 'অংক': 'Math', 
  'জীবন বিজ্ঞান': 'Life Science', 'ভৌত বিজ্ঞান': 'Physical Science', 
  'ইতিহাস': 'History', 'ভূগোল': 'Geography'
};
const b2eWorkType = {
  'রিভিশন': 'Revision', 'নতুন পড়া': 'New Topic', 'মুখস্থ করা': 'Memorize', 
  'রিডিং পড়া': 'Reading', 'প্রশ্ন উত্তর প্র্যাকটিস': 'Practice', 
  'নোট তৈরি': 'Notes', 'অন্যান্য': 'Other'
};

function toBanglish(str) {
  if (!str) return '';
  const map = {
    'অ':'o','আ':'a','ই':'i','ঈ':'i','উ':'u','ঊ':'u','ঋ':'ri','এ':'e','ঐ':'oi','ও':'o','ঔ':'ou',
    'ক':'k','খ':'kh','গ':'g','ঘ':'gh','ঙ':'ng','চ':'ch','ছ':'ch','জ':'j','ঝ':'jh','ঞ':'n',
    'ট':'t','ঠ':'th','ড':'d','ঢ':'dh','ণ':'n','ত':'t','থ':'th','দ':'d','ধ':'dh','ন':'n',
    'প':'p','ফ':'f','ব':'b','ভ':'v','ম':'m','য':'j','র':'r','ল':'l','শ':'sh','ষ':'sh','স':'s','হ':'h',
    'ড়':'r','ঢ়':'rh','য়':'y','ৎ':'t','ং':'ng','ঁ':'','ঃ':'h',
    'া':'a','ি':'i','ী':'i','ু':'u','ূ':'u','ৃ':'ri','ে':'e','ৈ':'oi','ো':'o','ৌ':'ou','্':''
  };
  let res = '';
  for(let i=0; i<str.length; i++){
    res += map[str[i]] !== undefined ? map[str[i]] : str[i];
  }
  return res.replace(/a+/g, 'a').replace(/i+/g, 'i');
}

function cleanPdfText(str) {
  if (str === null || str === undefined) return '';
  let s = String(str);
  
  // Strip HTML tags
  s = s.replace(/<[^>]*>/g, '');
  
  // Mathematical and directional symbols
  s = s.replace(/≥/g, '>=');
  s = s.replace(/≤/g, '<=');
  s = s.replace(/▲/g, '(+)');
  s = s.replace(/▼/g, '(-)');
  s = s.replace(/•/g, '-');
  s = s.replace(/[–—]/g, '-');
  s = s.replace(/[""]/g, '"');
  s = s.replace(/['']/g, "'");

  // Map known emojis to clean text tags
  s = s.replace(/🌅/g, '[Morning]');
  s = s.replace(/☀️/g, '[Afternoon]');
  s = s.replace(/🌆/g, '[Evening]');
  s = s.replace(/🌙/g, '[Night]');
  s = s.replace(/🧠/g, '[Deep Work]');
  s = s.replace(/⏱️?/g, '[Pacing]');
  s = s.replace(/⚠️/g, '[Warning]');
  s = s.replace(/⚖️?/g, '[Equilibrium]');
  s = s.replace(/📈/g, '[Velocity]');
  s = s.replace(/💡/g, '[Insight]');
  s = s.replace(/🏆/g, '[Badge]');
  s = s.replace(/🔥/g, '[Streak]');
  s = s.replace(/⚡/g, '[Focus]');
  s = s.replace(/🧩/g, '[Modality]');
  s = s.replace(/📑/g, '[Report]');
  s = s.replace(/ℹ️?/g, '[Notice]');

  // Translate Bengali subjects or work types if present
  Object.keys(b2eSubject).forEach(k => {
    if (s.includes(k)) s = s.split(k).join(b2eSubject[k]);
  });
  Object.keys(b2eWorkType).forEach(k => {
    if (s.includes(k)) s = s.split(k).join(b2eWorkType[k]);
  });
  if (/[\u0980-\u09FF]/.test(s)) {
    s = toBanglish(s);
  }

  // Strip any remaining characters outside standard printable ASCII range
  s = s.replace(/[^\x20-\x7E\n]/g, '');
  return s.trim();
}

/* ============================================================
   🧠 COGNITIVE DEEP ANALYSIS ENGINE (v2.0 PURE DETERMINISTIC MATH)
   ============================================================ */
let currentAnalyticsPeriod = '7'; // '7', '30', or 'all'

function getExamGoal() {
  if (!prefs.examGoal || typeof prefs.examGoal !== 'object') {
    const defaultTarget = new Date();
    defaultTarget.setDate(defaultTarget.getDate() + 60);
    const dStr = `${defaultTarget.getFullYear()}-${String(defaultTarget.getMonth() + 1).padStart(2, '0')}-${String(defaultTarget.getDate()).padStart(2, '0')}`;
    prefs.examGoal = {
      examName: 'Competitive / Academic Exam',
      targetDate: dStr,
      targetHours: 150,
      subjectScope: []
    };
  }
  return prefs.examGoal;
}

function saveExamGoal(newGoal) {
  prefs.examGoal = {
    examName: String(newGoal.examName || 'Target Exam').trim(),
    targetDate: String(newGoal.targetDate || '').trim(),
    targetHours: Math.max(1, Number(newGoal.targetHours) || 100),
    subjectScope: Array.isArray(newGoal.subjectScope) ? newGoal.subjectScope : []
  };
  savePrefs();
  renderDeepAnalytics();
}

function computeExamProjection(allStudy, goal, todayDate = new Date()) {
  const examName = goal.examName || 'Target Exam / Syllabus';
  const targetHours = Number(goal.targetHours) || 100;
  const targetDateStr = goal.targetDate || '';
  const subjectScope = Array.isArray(goal.subjectScope) ? goal.subjectScope : [];

  // 1. Filter sessions matching subject scope
  const scopedSessions = allStudy.filter(s => {
    if (s.isNonStudy) return false;
    if (subjectScope.length === 0) return true; // Empty scope = all study subjects count
    let m = s.subject || '';
    if (m.includes(' - ')) m = m.split(' - ')[0];
    return subjectScope.includes(m) || subjectScope.includes(s.subject);
  });

  const completedMinutes = scopedSessions.reduce((acc, s) => acc + (s.minutes || 0), 0);
  const completedHours = completedMinutes / 60;
  const remainingHours = Math.max(0, targetHours - completedHours);

  // 2. Calendar days remaining (midnight-to-midnight)
  let daysRemaining = 0;
  const todayMid = new Date(todayDate.getFullYear(), todayDate.getMonth(), todayDate.getDate(), 0, 0, 0);
  if (targetDateStr) {
    const parts = targetDateStr.split('-').map(Number);
    if (parts.length === 3) {
      const targetMid = new Date(parts[0], parts[1] - 1, parts[2], 0, 0, 0);
      daysRemaining = Math.round((targetMid - todayMid) / 86400000);
    }
  }

  // 3. Required daily pace
  const requiredDailyHours = daysRemaining > 0 ? (remainingHours / daysRemaining) : 0;

  // 4. Actual trailing 7 calendar days velocity
  // Must divide sum by 7.0 calendar days (including 0-study days)
  const sevenDaysAgo = new Date(todayMid);
  sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 6);
  const limitDateStr = `${sevenDaysAgo.getFullYear()}-${String(sevenDaysAgo.getMonth() + 1).padStart(2, '0')}-${String(sevenDaysAgo.getDate()).padStart(2, '0')}`;
  const todayDateStr = `${todayMid.getFullYear()}-${String(todayMid.getMonth() + 1).padStart(2, '0')}-${String(todayMid.getDate()).padStart(2, '0')}`;

  const last7DaysSessions = scopedSessions.filter(s => s.date >= limitDateStr && s.date <= todayDateStr);
  const last7DaysMins = last7DaysSessions.reduce((acc, s) => acc + (s.minutes || 0), 0);
  const currentDailyHours = (last7DaysMins / 60) / 7.0; // Div by 7 calendar days

  const paceDeltaHours = currentDailyHours - requiredDailyHours;
  const paceRatio = requiredDailyHours > 0 ? (currentDailyHours / requiredDailyHours) : 1.0;

  // 5. Feasibility status
  let status = 'ON_TRACK';
  let statusBadgeText = 'ON TRACK 🎯';
  let statusBadgeClass = 'on-track';
  let statusDescription = 'Current 7-day velocity is on schedule to complete the syllabus before the target deadline.';

  if (remainingHours <= 0) {
    status = 'COMPLETE';
    statusBadgeText = 'GOAL ACHIEVED 🏆';
    statusBadgeClass = 'complete';
    statusDescription = 'Congratulations! You have completed 100% of your targeted syllabus hours.';
  } else if (daysRemaining < 0) {
    status = 'EXPIRED';
    statusBadgeText = 'DEADLINE PASSED ⚠️';
    statusBadgeClass = 'critical-lag';
    statusDescription = 'The target exam date has passed. Edit the target date in settings to recalibrate.';
  } else if (daysRemaining === 0) {
    status = 'DEADLINE_TODAY';
    statusBadgeText = 'EXAM TODAY ⏳';
    statusBadgeClass = 'minor-deficit';
    statusDescription = 'Target deadline is today! Focus on high-yield formulas and active recall.';
  } else if (paceRatio >= 1.0) {
    status = 'ON_TRACK';
    statusBadgeText = 'ON TRACK 🎯';
    statusBadgeClass = 'on-track';
    statusDescription = `Velocity surplus of +${paceDeltaHours.toFixed(1)}h/day. Syllabus will be completed on schedule.`;
  } else if (paceRatio >= 0.8) {
    status = 'MINOR_DEFICIT';
    statusBadgeText = 'MINOR DEFICIT ⚠️';
    statusBadgeClass = 'minor-deficit';
    statusDescription = `Velocity lag of ${Math.abs(paceDeltaHours).toFixed(1)}h/day. Increase study blocks by ~${Math.round(Math.abs(paceDeltaHours) * 60)} mins/day to regain pace.`;
  } else {
    status = 'CRITICAL_LAG';
    statusBadgeText = 'CRITICAL LAG 🚨';
    statusBadgeClass = 'critical-lag';
    statusDescription = `Significant pace deficit of ${Math.abs(paceDeltaHours).toFixed(1)}h/day. Urgent pace recalibration needed to cover remaining ${remainingHours.toFixed(1)}h.`;
  }

  // 6. Projected Completion Date
  let projectedDate = 'Indeterminate (0h pace)';
  if (remainingHours <= 0) {
    projectedDate = 'Completed';
  } else if (currentDailyHours >= 0.05) {
    const daysNeeded = Math.ceil(remainingHours / currentDailyHours);
    const projDateObj = new Date(todayMid);
    projDateObj.setDate(projDateObj.getDate() + daysNeeded);
    projectedDate = `${projDateObj.getFullYear()}-${String(projDateObj.getMonth() + 1).padStart(2, '0')}-${String(projDateObj.getDate()).padStart(2, '0')}`;
  }

  return {
    examName,
    targetDate: targetDateStr,
    targetHours,
    completedHours,
    remainingHours,
    daysRemaining,
    requiredDailyHours,
    currentDailyHours,
    paceDeltaHours,
    paceRatio,
    projectedDate,
    status,
    statusBadgeText,
    statusBadgeClass,
    statusDescription,
    subjectScope
  };
}

function compute24hHourlyDensity(sessionsList) {
  const hourlyMins = new Array(24).fill(0);

  sessionsList.forEach(s => {
    if (!s.minutes || s.minutes <= 0) return;
    const endTs = s.ts ? Number(s.ts) : (s.date ? new Date(s.date + 'T14:00:00').getTime() : Date.now());
    const durationMs = s.minutes * 60000;
    const startTs = endTs - durationMs;

    // Exact splitting across hour boundaries
    let cur = new Date(startTs);
    let curHourStart = new Date(cur.getFullYear(), cur.getMonth(), cur.getDate(), cur.getHours(), 0, 0, 0).getTime();

    while (curHourStart < endTs) {
      const nextHourStart = curHourStart + 3600000;
      const overlapStart = Math.max(startTs, curHourStart);
      const overlapEnd = Math.min(endTs, nextHourStart);

      if (overlapEnd > overlapStart) {
        const h = new Date(curHourStart).getHours();
        const mins = (overlapEnd - overlapStart) / 60000;
        hourlyMins[h] = (hourlyMins[h] || 0) + mins;
      }
      curHourStart = nextHourStart;
    }
  });

  for (let i = 0; i < 24; i++) {
    hourlyMins[i] = Math.round(hourlyMins[i]);
  }
  return hourlyMins;
}

function computeFatigueReport(allStudySessions, todayDate = new Date()) {
  const todayMid = new Date(todayDate.getFullYear(), todayDate.getMonth(), todayDate.getDate(), 0, 0, 0);

  const dailyMins = new Array(7).fill(0);
  const dayStrings = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(todayMid);
    d.setDate(d.getDate() - i);
    dayStrings.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`);
  }

  allStudySessions.forEach(s => {
    if (s.isNonStudy) return;
    const idx = dayStrings.indexOf(s.date);
    if (idx !== -1) {
      dailyMins[idx] += (s.minutes || 0);
    }
  });

  const dailyHours = dailyMins.map(m => m / 60);
  const total7dHours = dailyHours.reduce((a, b) => a + b, 0);
  const avgDailyHours7d = total7dHours / 7.0;

  // 1. loadFactor: normalized against 6h daily baseline, capped at 1.0
  const loadFactor = Math.min(1.0, avgDailyHours7d / 6.0);

  // 2. consecutiveHighDays: consecutive days leading up to today (d0) with >= 5.0h
  let consecutiveHighDays = 0;
  for (let i = 6; i >= 0; i--) {
    if (dailyHours[i] >= 5.0) {
      consecutiveHighDays++;
    } else {
      break;
    }
  }
  const streakFactor = Math.min(1.0, consecutiveHighDays / 4.0);

  // 3. recoveryDays: days in 7d with < 2.0h
  const recoveryDaysCount = dailyHours.filter(h => h < 2.0).length;
  const recoveryRatio = recoveryDaysCount / 7.0;

  // Combined Fatigue Score: 0.40 * load + 0.35 * streak + 0.25 * (1 - recovery)
  const rawScore = 100 * (0.40 * loadFactor + 0.35 * streakFactor + 0.25 * (1.0 - recoveryRatio));
  const fatigueScore = Math.min(100, Math.max(0, Math.round(rawScore)));

  // Non-clinical Behavioral Tiers
  let fatigueTier = 'OPTIMAL_RECOVERY';
  let tierLabel = 'Optimal Recovery 🔋';
  let tierClass = 'optimal';
  let adviceText = 'Optimal Cognitive Recovery: High endurance reserve, ready for intensive focus blocks.';

  if (fatigueScore >= 70) {
    fatigueTier = 'HIGH_FATIGUE_LOAD';
    tierLabel = 'High Strain ⚠️';
    tierClass = 'fatigue';
    adviceText = `Elevated Cognitive Strain (${fatigueScore}/100): Extended peak exertion detected (${consecutiveHighDays} consecutive 5h+ days). Schedule structured active rest to prevent fatigue.`;
  } else if (fatigueScore >= 40) {
    fatigueTier = 'SUSTAINED_HIGH_LOAD';
    tierLabel = 'Sustained Workload ⚡';
    tierClass = 'sustained';
    adviceText = `Sustained High Workload (${fatigueScore}/100): Consistent daily output (${avgDailyHours7d.toFixed(1)}h/day). Maintain proper hydration and short recovery intervals.`;
  } else {
    fatigueTier = 'OPTIMAL_RECOVERY';
    tierLabel = 'Optimal Recovery 🔋';
    tierClass = 'optimal';
    adviceText = `Optimal Cognitive Recovery (${fatigueScore}/100): Well-paced focus routines with adequate recovery (${recoveryDaysCount} light/rest days). High cognitive reserve.`;
  }

  return {
    fatigueScore,
    fatigueTier,
    tierLabel,
    tierClass,
    adviceText,
    avgDailyHours7d,
    consecutiveHighDays,
    recoveryDaysCount
  };
}

function drawCircadianDensityCurve(canvas, hourlyMins) {
  if (!canvas) return;
  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  const width = rect.width || canvas.parentElement?.clientWidth || 480;
  const height = 140;
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;

  const ctx = canvas.getContext('2d');
  ctx.save();
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, width, height);

  const padLeft = 32;
  const padRight = 20;
  const padTop = 22;
  const padBottom = 26;
  const plotW = width - padLeft - padRight;
  const plotH = height - padTop - padBottom;

  const maxMins = Math.max(...hourlyMins, 10);

  // Subtle horizontal grid lines (0%, 50%, 100%)
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.06)';
  ctx.lineWidth = 1;
  [0, 0.5, 1].forEach(ratio => {
    const y = padTop + plotH * (1 - ratio);
    ctx.beginPath();
    ctx.moveTo(padLeft, y);
    ctx.lineTo(width - padRight, y);
    ctx.stroke();

    ctx.fillStyle = 'rgba(148, 163, 184, 0.7)';
    ctx.font = '9px Inter, sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText(`${Math.round(maxMins * ratio)}m`, padLeft - 6, y + 3);
  });

  // Calculate 24 coordinate points
  const points = [];
  for (let h = 0; h < 24; h++) {
    const x = padLeft + (h / 23) * plotW;
    const y = padTop + plotH * (1 - (hourlyMins[h] / maxMins));
    points.push({ x, y, val: hourlyMins[h], hour: h });
  }

  // Draw smooth monotonic Bézier curve with gradient fill
  ctx.beginPath();
  ctx.moveTo(points[0].x, padTop + plotH);
  ctx.lineTo(points[0].x, points[0].y);

  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(points.length - 1, i + 2)];

    const cp1x = p1.x + (p2.x - p0.x) / 6;
    let cp1y = p1.y + (p2.y - p0.y) / 6;
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    let cp2y = p2.y - (p3.y - p1.y) / 6;

    const baseY = padTop + plotH;
    if (cp1y > baseY) cp1y = baseY;
    if (cp2y > baseY) cp2y = baseY;

    ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
  }

  ctx.lineTo(points[23].x, padTop + plotH);
  ctx.closePath();

  const grad = ctx.createLinearGradient(0, padTop, 0, padTop + plotH);
  grad.addColorStop(0, 'rgba(16, 185, 129, 0.42)');
  grad.addColorStop(0.7, 'rgba(16, 185, 129, 0.12)');
  grad.addColorStop(1, 'rgba(16, 185, 129, 0.01)');
  ctx.fillStyle = grad;
  ctx.fill();

  // Draw stroke line
  ctx.beginPath();
  ctx.moveTo(points[0].x, points[0].y);
  for (let i = 0; i < points.length - 1; i++) {
    const p0 = points[Math.max(0, i - 1)];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = points[Math.min(points.length - 1, i + 2)];

    const cp1x = p1.x + (p2.x - p0.x) / 6;
    let cp1y = p1.y + (p2.y - p0.y) / 6;
    const cp2x = p2.x - (p3.x - p1.x) / 6;
    let cp2y = p2.y - (p3.y - p1.y) / 6;

    const baseY = padTop + plotH;
    if (cp1y > baseY) cp1y = baseY;
    if (cp2y > baseY) cp2y = baseY;

    ctx.bezierCurveTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y);
  }
  ctx.strokeStyle = '#34d399';
  ctx.lineWidth = 2.2;
  ctx.stroke();

  // Peak Hour Marker
  let peakIdx = 0;
  let peakVal = 0;
  points.forEach((p, idx) => {
    if (p.val > peakVal) {
      peakVal = p.val;
      peakIdx = idx;
    }
  });

  if (peakVal > 0) {
    const peakP = points[peakIdx];
    ctx.fillStyle = 'rgba(52, 211, 153, 0.25)';
    ctx.beginPath();
    ctx.arc(peakP.x, peakP.y, 7, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = '#10b981';
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.arc(peakP.x, peakP.y, 3.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();

    const ampm = peakIdx >= 12 ? 'PM' : 'AM';
    const hr = peakIdx % 12 === 0 ? 12 : peakIdx % 12;
    const peakText = `${hr}:00 ${ampm} (${peakVal}m)`;
    ctx.fillStyle = '#34d399';
    ctx.font = 'bold 9.5px Inter, sans-serif';
    ctx.textAlign = peakIdx > 18 ? 'right' : (peakIdx < 5 ? 'left' : 'center');
    ctx.fillText(peakText, peakP.x, Math.max(12, peakP.y - 10));
  }

  // X-axis time labels
  const hourTicks = [0, 4, 8, 12, 16, 20, 23];
  ctx.fillStyle = 'rgba(148, 163, 184, 0.8)';
  ctx.font = '9px Inter, sans-serif';
  ctx.textAlign = 'center';
  hourTicks.forEach(h => {
    const p = points[h];
    const ampm = h >= 12 ? 'p' : 'a';
    const hr = h % 12 === 0 ? 12 : h % 12;
    ctx.fillText(`${hr}${ampm}`, p.x, height - 8);
  });

  ctx.restore();
}

function drawSubjectRadar(canvas, subjectEquilibrium) {
  if (!canvas) return;
  const fallbackNotice = document.getElementById('radarFallbackNotice');
  const subjects = subjectEquilibrium || [];

  if (subjects.length < 3) {
    canvas.style.display = 'none';
    if (fallbackNotice) {
      fallbackNotice.style.display = 'block';
      const count = subjects.length;
      fallbackNotice.innerHTML = `
        <div style="font-size:1.4rem; margin-bottom:6px;">⚖️</div>
        <strong>Polygonal Radar Standby</strong>
        <div style="margin-top:4px;">Radar web polygon requires 3+ subjects (currently ${count} recorded). Comparative balance bars are displayed on the right.</div>
      `;
    }
    return;
  }

  canvas.style.display = 'block';
  if (fallbackNotice) fallbackNotice.style.display = 'none';

  const dpr = window.devicePixelRatio || 1;
  const width = 340;
  const height = 300;
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;

  const ctx = canvas.getContext('2d');
  ctx.save();
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, width, height);

  const topSubjects = subjects.slice(0, 8);
  const N = topSubjects.length;
  const cx = width / 2;
  const cy = height / 2 + 6;
  const R = Math.min(cx, cy) - 46;

  // Concentric polygon web rings (25%, 50%, 75%, 100%)
  const rings = [0.25, 0.50, 0.75, 1.0];
  rings.forEach((ringPct, ringIdx) => {
    const r = R * ringPct;
    ctx.beginPath();
    for (let i = 0; i < N; i++) {
      const angle = -Math.PI / 2 + (2 * Math.PI * i) / N;
      const x = cx + r * Math.cos(angle);
      const y = cy + r * Math.sin(angle);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();
    ctx.strokeStyle = ringIdx === 3 ? 'rgba(255, 255, 255, 0.18)' : 'rgba(255, 255, 255, 0.07)';
    ctx.lineWidth = ringIdx === 3 ? 1.2 : 0.8;
    ctx.stroke();
  });

  // Radial spokes
  for (let i = 0; i < N; i++) {
    const angle = -Math.PI / 2 + (2 * Math.PI * i) / N;
    const x = cx + R * Math.cos(angle);
    const y = cy + R * Math.sin(angle);
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(x, y);
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.lineWidth = 1;
    ctx.stroke();
  }

  // Data Polygon
  const maxMins = Math.max(...topSubjects.map(s => s.mins), 1);
  const dataPoints = topSubjects.map((s, i) => {
    const angle = -Math.PI / 2 + (2 * Math.PI * i) / N;
    const norm = Math.max(0.08, Math.min(1.0, s.mins / maxMins));
    const r = R * norm;
    return {
      x: cx + r * Math.cos(angle),
      y: cy + r * Math.sin(angle),
      name: s.name,
      hours: s.hours,
      mins: s.mins,
      angle
    };
  });

  // Draw Data Fill
  ctx.beginPath();
  dataPoints.forEach((p, i) => {
    if (i === 0) ctx.moveTo(p.x, p.y);
    else ctx.lineTo(p.x, p.y);
  });
  ctx.closePath();
  ctx.fillStyle = 'rgba(16, 185, 129, 0.28)';
  ctx.fill();

  // Draw Data Stroke
  ctx.strokeStyle = '#34d399';
  ctx.lineWidth = 2.2;
  ctx.stroke();

  // Draw Dots and Labels
  dataPoints.forEach(p => {
    ctx.fillStyle = '#10b981';
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.arc(p.x, p.y, 3.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();

    const labelDist = R + 16;
    const lx = cx + labelDist * Math.cos(p.angle);
    const ly = cy + labelDist * Math.sin(p.angle);

    ctx.fillStyle = '#f1f5f9';
    ctx.font = 'bold 9.5px Inter, sans-serif';
    ctx.textAlign = Math.cos(p.angle) > 0.3 ? 'left' : (Math.cos(p.angle) < -0.3 ? 'right' : 'center');
    ctx.textBaseline = Math.sin(p.angle) > 0.3 ? 'top' : (Math.sin(p.angle) < -0.3 ? 'bottom' : 'middle');

    const cleanName = p.name.length > 11 ? p.name.slice(0, 10) + '..' : p.name;
    ctx.fillText(`${cleanName} (${p.hours}h)`, lx, ly);
  });

  ctx.restore();
}

function drawPdfCircadianWave(doc, startX, startY, width, height, hourlyMins) {
  const maxMins = Math.max(...hourlyMins, 10);
  const padBottom = 8;
  const padTop = 6;
  const plotH = height - padBottom - padTop;
  const baseY = startY + height - padBottom;

  doc.setFillColor(248, 250, 252);
  doc.rect(startX, startY, width, height, 'F');
  doc.setDrawColor(226, 232, 240);
  doc.setLineWidth(0.4);
  doc.rect(startX, startY, width, height, 'S');

  // Baseline
  doc.setDrawColor(203, 213, 225);
  doc.line(startX + 10, baseY, startX + width - 6, baseY);

  const points = [];
  for (let h = 0; h < 24; h++) {
    const x = startX + 10 + (h / 23) * (width - 16);
    const y = baseY - (hourlyMins[h] / maxMins) * plotH;
    points.push({ x, y, h, mins: hourlyMins[h] });
  }

  // Draw filled area under curve
  doc.setFillColor(209, 250, 229);
  for (let i = 0; i < points.length - 1; i++) {
    const p1 = points[i];
    const p2 = points[i + 1];
    doc.triangle(p1.x, baseY, p1.x, p1.y, p2.x, p2.y, 'F');
    doc.triangle(p1.x, baseY, p2.x, p2.y, p2.x, baseY, 'F');
  }

  // Draw stroke
  doc.setDrawColor(16, 185, 129);
  doc.setLineWidth(0.75);
  for (let i = 0; i < points.length - 1; i++) {
    doc.line(points[i].x, points[i].y, points[i + 1].x, points[i + 1].y);
  }

  // Peak Marker
  let peakIdx = 0;
  let peakVal = 0;
  points.forEach((p, idx) => {
    if (p.mins > peakVal) {
      peakVal = p.mins;
      peakIdx = idx;
    }
  });

  if (peakVal > 0) {
    const p = points[peakIdx];
    doc.setFillColor(16, 185, 129);
    doc.circle(p.x, p.y, 1.2, 'FD');
    doc.setFontSize(6.5);
    doc.setTextColor(6, 95, 70);
    const ampm = peakIdx >= 12 ? 'PM' : 'AM';
    const hr = peakIdx % 12 === 0 ? 12 : peakIdx % 12;
    doc.text(`Peak: ${hr}:00 ${ampm} (${peakVal}m)`, p.x, Math.max(startY + 5, p.y - 2), { align: 'center' });
  }

  // Hour Labels
  doc.setFontSize(6.5);
  doc.setTextColor(100, 116, 139);
  [0, 6, 12, 18, 23].forEach(h => {
    const p = points[h];
    const ampm = h >= 12 ? 'p' : 'a';
    const hr = h % 12 === 0 ? 12 : h % 12;
    doc.text(`${hr}${ampm}`, p.x, baseY + 5, { align: 'center' });
  });
}

function drawPdfRadarChart(doc, cx, cy, radius, subjectEquilibrium) {
  const topSubjects = (subjectEquilibrium || []).slice(0, 8);
  const N = topSubjects.length;

  if (N < 3) {
    doc.setFontSize(7.5);
    doc.setTextColor(100, 116, 139);
    doc.text('Polygonal radar activates with 3+ subjects (see audit table for balance).', cx, cy, { align: 'center' });
    return;
  }

  // Concentric Rings
  [0.25, 0.50, 0.75, 1.0].forEach(pct => {
    const r = radius * pct;
    doc.setDrawColor(226, 232, 240);
    doc.setLineWidth(0.3);
    for (let i = 0; i < N; i++) {
      const a1 = -Math.PI / 2 + (2 * Math.PI * i) / N;
      const a2 = -Math.PI / 2 + (2 * Math.PI * (i + 1)) / N;
      doc.line(cx + r * Math.cos(a1), cy + r * Math.sin(a1), cx + r * Math.cos(a2), cy + r * Math.sin(a2));
    }
  });

  // Spokes
  for (let i = 0; i < N; i++) {
    const a = -Math.PI / 2 + (2 * Math.PI * i) / N;
    doc.setDrawColor(226, 232, 240);
    doc.line(cx, cy, cx + radius * Math.cos(a), cy + radius * Math.sin(a));
  }

  const maxMins = Math.max(...topSubjects.map(s => s.mins), 1);
  const points = topSubjects.map((s, i) => {
    const a = -Math.PI / 2 + (2 * Math.PI * i) / N;
    const norm = Math.max(0.08, Math.min(1.0, s.mins / maxMins));
    const r = radius * norm;
    return {
      x: cx + r * Math.cos(a),
      y: cy + r * Math.sin(a),
      a,
      name: s.name,
      hours: s.hours
    };
  });

  // Fill polygon
  doc.setFillColor(209, 250, 229);
  for (let i = 0; i < N; i++) {
    const p1 = points[i];
    const p2 = points[(i + 1) % N];
    doc.triangle(cx, cy, p1.x, p1.y, p2.x, p2.y, 'F');
  }

  // Stroke polygon
  doc.setDrawColor(16, 185, 129);
  doc.setLineWidth(0.75);
  for (let i = 0; i < N; i++) {
    const p1 = points[i];
    const p2 = points[(i + 1) % N];
    doc.line(p1.x, p1.y, p2.x, p2.y);
  }

  // Dots & Labels
  doc.setFontSize(6.5);
  doc.setTextColor(30, 41, 59);
  points.forEach(p => {
    doc.setFillColor(16, 185, 129);
    doc.circle(p.x, p.y, 0.9, 'FD');

    const lx = cx + (radius + 8) * Math.cos(p.a);
    const ly = cy + (radius + 8) * Math.sin(p.a);
    const clean = p.name.length > 9 ? p.name.slice(0, 8) + '..' : p.name;
    const align = Math.cos(p.a) > 0.2 ? 'left' : (Math.cos(p.a) < -0.2 ? 'right' : 'center');
    doc.text(`${clean} (${p.hours}h)`, lx, ly + 1.5, { align });
  });
}

function computeAnalyticsReport(period) {
  const allStudy = sessions.filter(s => !s.isNonStudy);
  let filtered = allStudy;
  let priorSessions = [];

  const today = todayStr();

  if (period === '7') {
    const limit = daysAgoStr(6);
    const priorLimit = daysAgoStr(13);
    filtered = allStudy.filter(s => s.date >= limit);
    priorSessions = allStudy.filter(s => s.date >= priorLimit && s.date < limit);
  } else if (period === '30') {
    const limit = daysAgoStr(29);
    const priorLimit = daysAgoStr(59);
    filtered = allStudy.filter(s => s.date >= limit);
    priorSessions = allStudy.filter(s => s.date >= priorLimit && s.date < limit);
  } else {
    // all
    filtered = allStudy;
    priorSessions = [];
  }

  const totalMinutes = filtered.reduce((a, s) => a + s.minutes, 0);
  const totalHours = (totalMinutes / 60).toFixed(1);
  const sessionCount = filtered.length;
  const uniqueDates = [...new Set(filtered.map(s => s.date))];
  const activeDaysCount = uniqueDates.length;
  const avgSessionMin = sessionCount > 0 ? Math.round(totalMinutes / sessionCount) : 0;

  // Deep Work: Sessions >= 45 mins
  const deepWorkSessions = filtered.filter(s => (s.minutes || 0) >= 45);
  const deepWorkMinutes = deepWorkSessions.reduce((a, s) => a + s.minutes, 0);
  const deepWorkRatio = totalMinutes > 0 ? Math.round((deepWorkMinutes / totalMinutes) * 100) : 0;

  // Study Velocity (% change vs prior period)
  const priorMinutes = priorSessions.reduce((a, s) => a + s.minutes, 0);
  let velocity = 0;
  if (priorMinutes > 0 && totalMinutes > 0) {
    velocity = Math.round(((totalMinutes - priorMinutes) / priorMinutes) * 100);
  } else if (totalMinutes > 0 && period !== 'all') {
    velocity = 100;
  }

  // 24-Hour Continuous Hourly Density with exact boundary splitting
  const hourlyMins = compute24hHourlyDensity(filtered);

  // Circadian Buckets calculated synchronously from hourlyMins
  const circadianBuckets = {
    morning: { label: '🌅 Morning (5am - 12pm)', cleanLabel: 'Morning (05:00 AM - 12:00 PM)', mins: 0, count: 0 },
    afternoon: { label: '☀️ Afternoon (12pm - 5pm)', cleanLabel: 'Afternoon (12:00 PM - 05:00 PM)', mins: 0, count: 0 },
    evening: { label: '🌆 Evening (5pm - 10pm)', cleanLabel: 'Evening (05:00 PM - 10:00 PM)', mins: 0, count: 0 },
    night: { label: '🌙 Night (10pm - 5am)', cleanLabel: 'Night (10:00 PM - 05:00 AM)', mins: 0, count: 0 }
  };
  for (let h = 0; h < 24; h++) {
    const m = hourlyMins[h];
    if (h >= 5 && h < 12) circadianBuckets.morning.mins += m;
    else if (h >= 12 && h < 17) circadianBuckets.afternoon.mins += m;
    else if (h >= 17 && h < 22) circadianBuckets.evening.mins += m;
    else circadianBuckets.night.mins += m;
  }

  // Find Peak 2-hour Window
  let max2Hour = 0;
  let peakStartHour = 9;
  for (let h = 0; h < 23; h++) {
    const sum = hourlyMins[h] + hourlyMins[h + 1];
    if (sum > max2Hour) {
      max2Hour = sum;
      peakStartHour = h;
    }
  }
  const formatHour = (h) => {
    const ampm = h >= 12 ? 'PM' : 'AM';
    const hr = h % 12 === 0 ? 12 : h % 12;
    return `${hr}:00 ${ampm}`;
  };
  const peakFocusWindow = max2Hour > 0 
    ? `${formatHour(peakStartHour)} - ${formatHour(peakStartHour + 2)}`
    : 'N/A (No study in period)';

  // Deterministic Algorithmic Pillars for Focus Quality Score (FQS / Cognitive Quality Index: 0-100)
  let daysInPeriod = 7;
  if (period === '7') {
    daysInPeriod = 7;
  } else if (period === '30') {
    daysInPeriod = 30;
  } else {
    // all
    if (allStudy.length > 0) {
      const allDates = allStudy.map(s => s.date).sort();
      const firstDate = new Date(allDates[0]);
      const lastDate = new Date(allDates[allDates.length - 1]);
      const spanDays = Math.max(1, Math.round((lastDate - firstDate) / 86400000) + 1);
      daysInPeriod = spanDays;
    } else {
      daysInPeriod = 1;
    }
  }
  const consistencyPct = Math.min(100, Math.round((activeDaysCount / daysInPeriod) * 100));

  // Daily Goal Hit Rate (G)
  const targetMins = (typeof prefs !== 'undefined' && prefs.dailyTarget) ? prefs.dailyTarget : 120;
  const dayTotals = {};
  filtered.forEach(s => {
    dayTotals[s.date] = (dayTotals[s.date] || 0) + s.minutes;
  });
  const daysMetTarget = Object.values(dayTotals).filter(m => m >= targetMins).length;
  const goalHitRate = activeDaysCount > 0 ? Math.min(100, Math.round((daysMetTarget / activeDaysCount) * 100)) : 0;

  // Session Pacing Stability (P: benchmark 50 mins)
  const pacingStability = Math.min(100, Math.round((avgSessionMin / 50) * 100));

  // Focus Quality Score (FQS): 0.35*DeepWork + 0.25*Consistency + 0.25*GoalHit + 0.15*Pacing
  const focusQualityScore = totalMinutes > 0
    ? Math.min(100, Math.max(0, Math.round(0.35 * deepWorkRatio + 0.25 * consistencyPct + 0.25 * goalHitRate + 0.15 * pacingStability)))
    : 0;

  let focusQualityTier = 'Fragmented Focus';
  if (focusQualityScore >= 85) {
    focusQualityTier = 'Elite Cognitive Focus';
  } else if (focusQualityScore >= 70) {
    focusQualityTier = 'Optimal Focus Stamina';
  } else if (focusQualityScore >= 50) {
    focusQualityTier = 'Moderate Pacing';
  } else {
    focusQualityTier = 'Fragmented Focus';
  }

  // Subject Equilibrium & Ebbinghaus Scientific Recall Matrix
  const subjMap = {};
  filtered.forEach(s => {
    let m = s.subject;
    if (m && m.includes(' - ')) m = m.split(' - ')[0];
    subjMap[m] = (subjMap[m] || 0) + s.minutes;
  });

  const lastStudiedDateMap = {};
  const subjectSessionCountMap = {};
  allStudy.forEach(s => {
    let m = s.subject;
    if (m && m.includes(' - ')) m = m.split(' - ')[0];
    subjectSessionCountMap[m] = (subjectSessionCountMap[m] || 0) + 1;
    if (!lastStudiedDateMap[m] || s.date > lastStudiedDateMap[m]) {
      lastStudiedDateMap[m] = s.date;
    }
  });

  const curDate = new Date();
  const subjectEquilibrium = Object.entries(subjMap).map(([name, mins]) => {
    const pct = totalMinutes > 0 ? Math.round((mins / totalMinutes) * 100) : 0;
    const lastDateStr = lastStudiedDateMap[name];
    let daysAgo = 0;
    if (lastDateStr) {
      const parts = lastDateStr.split('-');
      const lDate = new Date(parts[0], parts[1] - 1, parts[2]);
      daysAgo = Math.max(0, Math.round((curDate - lDate) / 86400000));
    }
    const sessionCount = subjectSessionCountMap[name] || 1;
    const stabilityDays = sessionCount <= 1 ? 2.5 : (sessionCount === 2 ? 5.0 : 9.0);
    const retentionPct = daysAgo === 0 ? 100 : Math.min(100, Math.max(0, Math.round(100 * Math.exp(-daysAgo / stabilityDays))));

    let recallStatus = 'Optimal Retention';
    if (retentionPct < 60) {
      recallStatus = 'Critical Recall Due';
    } else if (retentionPct < 80) {
      recallStatus = 'Review Recommended';
    }

    const isNeglected = daysAgo >= 3;
    return {
      name,
      mins,
      hours: (mins / 60).toFixed(1),
      pct,
      daysAgo,
      isNeglected,
      sessionCount,
      stabilityDays,
      retentionPct,
      recallStatus
    };
  }).sort((a, b) => b.mins - a.mins);

  // Cognitive Work-Type Distribution
  const workTypeMap = {};
  filtered.forEach(s => {
    const w = s.workType || 'Other';
    workTypeMap[w] = (workTypeMap[w] || 0) + s.minutes;
  });
  const cognitiveWorkTypes = Object.entries(workTypeMap).map(([name, mins]) => {
    const pct = totalMinutes > 0 ? Math.round((mins / totalMinutes) * 100) : 0;
    return { name, mins, pct };
  }).sort((a, b) => b.mins - a.mins);

  // Compute Exam Projection & Fatigue Report
  const examGoal = getExamGoal();
  const examProjection = computeExamProjection(allStudy, examGoal);
  const fatigueReport = computeFatigueReport(allStudy);

  // Deterministic Cognitive Diagnostic Insights
  const smartInsights = [];
  if (totalMinutes > 0) {
    let peakBucket = Object.values(circadianBuckets).sort((a, b) => b.mins - a.mins)[0];
    const bucketPct = Math.round((peakBucket.mins / totalMinutes) * 100);
    smartInsights.push({
      icon: '🌅',
      text: `<strong>Circadian Prime:</strong> Your peak focus window is <strong>${peakFocusWindow}</strong> (${bucketPct}% of study). Prioritize challenging analytical concepts during this period.`
    });

    smartInsights.push({
      icon: '🎯',
      text: `<strong>Focus Quality Score (${focusQualityScore}/100):</strong> Tier: <strong>${focusQualityTier}</strong>. Consistency: ${consistencyPct}%, Goal Hit: ${goalHitRate}%, Deep Work: ${deepWorkRatio}%.`
    });

    smartInsights.push({
      icon: '🔋',
      text: `<strong>Cognitive Workload Index (${fatigueReport.fatigueScore}/100):</strong> ${fatigueReport.adviceText}`
    });

    if (examProjection.status === 'ON_TRACK') {
      smartInsights.push({
        icon: '🎯',
        text: `<strong>Exam Horizon Projection:</strong> On schedule for <strong>${examProjection.examName}</strong> (${examProjection.completedHours.toFixed(1)}h done, pace: ${examProjection.currentDailyHours.toFixed(1)}h/d). Projected completion: <strong>${examProjection.projectedDate}</strong>.`
      });
    } else if (examProjection.status === 'MINOR_DEFICIT' || examProjection.status === 'CRITICAL_LAG') {
      smartInsights.push({
        icon: '⚠️',
        text: `<strong>Exam Pace Deficit:</strong> <strong>${examProjection.examName}</strong> requires ${examProjection.requiredDailyHours.toFixed(1)}h/day, current velocity is ${examProjection.currentDailyHours.toFixed(1)}h/day. ${examProjection.statusDescription}`
      });
    }

    if (deepWorkRatio >= 60) {
      smartInsights.push({
        icon: '🧠',
        text: `<strong>Deep Work Stamina:</strong> <strong>${deepWorkRatio}%</strong> of your focus occurs in sustained sessions (≥45m). Excellent cognitive endurance!`
      });
    } else {
      smartInsights.push({
        icon: '⏱️',
        text: `<strong>Focus Pacing:</strong> <strong>${100 - deepWorkRatio}%</strong> of your time is spent in short sprints. Consider lengthening study blocks to deepen immersion.`
      });
    }

    const criticalRecall = subjectEquilibrium.filter(s => s.retentionPct < 60);
    if (criticalRecall.length > 0) {
      const names = criticalRecall.slice(0, 2).map(s => `${s.name} (R=${s.retentionPct}%, ${s.daysAgo}d ago)`).join(', ');
      smartInsights.push({
        icon: '⚠️',
        text: `<strong>Ebbinghaus Memory Decay Warning:</strong> ${names} fallen below 60% retention. Priority 1 active recall session required today to restore memory stability.`
      });
    } else {
      const neglectedSubjects = subjectEquilibrium.filter(s => s.isNeglected);
      if (neglectedSubjects.length > 0) {
        const names = neglectedSubjects.slice(0, 2).map(s => `${s.name} (${s.daysAgo}d ago)`).join(', ');
        smartInsights.push({
          icon: '⚠️',
          text: `<strong>Subject Neglect Warning:</strong> ${names} untouched recently. Schedule a recall session to prevent forgetting curve decay.`
        });
      } else if (subjectEquilibrium.length > 1) {
        smartInsights.push({
          icon: '⚖️',
          text: `<strong>Curriculum Equilibrium:</strong> All active subjects were studied within the last 48 hours. Well-balanced curriculum distribution.`
        });
      }
    }

    if (period !== 'all' && (totalMinutes > 0 || priorMinutes > 0)) {
      const arrow = velocity >= 0 ? '▲' : '▼';
      const trendWord = velocity >= 0 ? 'acceleration' : 'dip';
      smartInsights.push({
        icon: '📈',
        text: `<strong>Study Velocity:</strong> ${arrow} <strong>${Math.abs(velocity)}%</strong> ${trendWord} compared to the previous timeframe.`
      });
    }
  } else {
    // totalMinutes === 0
    if (allStudy.length > 0) {
      const dates = allStudy.map(s => s.date).sort();
      const oldest = dates[0];
      const newest = dates[dates.length - 1];
      const allMins = allStudy.reduce((a, s) => a + s.minutes, 0);
      smartInsights.push({
        icon: 'ℹ️',
        text: `<strong>Historical Data Available:</strong> No study sessions logged in <strong>${period === 'all' ? 'All Time' : 'Last ' + period + ' Days'}</strong>. You have <strong>${allStudy.length}</strong> total sessions (<strong>${(allMins / 60).toFixed(1)} hrs</strong>) recorded between <strong>${oldest}</strong> and <strong>${newest}</strong>. Switch to <strong>'All Time'</strong> to view complete metrics.`
      });
    } else {
      smartInsights.push({
        icon: '💡',
        text: 'Log study sessions to unlock automated circadian analysis, stamina scores, and curriculum balance feedback.'
      });
    }
  }

  return {
    period,
    totalMinutes,
    totalHours,
    sessionCount,
    activeDaysCount,
    avgSessionMin,
    deepWorkMinutes,
    deepWorkRatio,
    velocity,
    hourlyMins,
    circadianBuckets,
    peakFocusWindow,
    subjectEquilibrium,
    cognitiveWorkTypes,
    smartInsights,
    filteredSessions: filtered,
    allStudyTotalCount: allStudy.length,
    allStudyTotalMinutes: allStudy.reduce((a, s) => a + s.minutes, 0),
    focusQualityScore,
    focusQualityTier,
    consistencyPct,
    goalHitRate,
    pacingStability,
    examProjection,
    fatigueReport
  };
}

function renderDeepAnalytics() {
  const report = computeAnalyticsReport(currentAnalyticsPeriod);

  // Notice banner when 0 sessions in selected period but history exists
  const noticeContainer = document.getElementById('analyticsEmptyNotice');
  if (noticeContainer) {
    if (report.totalMinutes === 0 && report.allStudyTotalCount > 0) {
      noticeContainer.style.display = 'block';
      const pLabel = currentAnalyticsPeriod === 'all' ? 'All Time' : `Last ${currentAnalyticsPeriod} Days`;
      noticeContainer.innerHTML = `
        <div class="analytics-empty-notice-card">
          <div class="notice-left">
            <span class="notice-icon">ℹ️</span>
            <div>
              <div class="notice-title">No sessions in selected timeframe (${pLabel})</div>
              <div class="notice-desc">You have <strong>${report.allStudyTotalCount}</strong> study sessions recorded in your profile. Switch to All Time to view them.</div>
            </div>
          </div>
          <button id="analyticsSwitchAllTimeBtn" class="notice-btn">Switch to All Time</button>
        </div>
      `;
      const switchBtn = document.getElementById('analyticsSwitchAllTimeBtn');
      if (switchBtn) {
        switchBtn.addEventListener('click', () => {
          document.querySelectorAll('.analytics-timeframe-btn').forEach(b => b.classList.remove('active'));
          const allBtn = document.querySelector('.analytics-timeframe-btn[data-period="all"]');
          if (allBtn) allBtn.classList.add('active');
          currentAnalyticsPeriod = 'all';
          renderDeepAnalytics();
        });
      }
    } else {
      noticeContainer.style.display = 'none';
      noticeContainer.innerHTML = '';
    }
  }

  // 🎯 Render Exam & Syllabus Projection Calculator Card
  const proj = report.examProjection;
  const nameEl = document.getElementById('examTargetName');
  if (nameEl) nameEl.textContent = proj.examName;
  const subEl = document.getElementById('examTargetSub');
  if (subEl) {
    const scopeStr = proj.subjectScope && proj.subjectScope.length > 0 ? `Scope: ${proj.subjectScope.join(', ')}` : 'Scope: All Subjects';
    subEl.textContent = `${scopeStr} • Deadline: ${proj.targetDate || 'Not set'}`;
  }
  const pillEl = document.getElementById('examCountdownPill');
  if (pillEl) {
    pillEl.textContent = proj.daysRemaining > 0 ? `⏳ ${proj.daysRemaining} Days Left` : (proj.daysRemaining === 0 ? '⏳ Exam Today' : '⚠️ Past Deadline');
  }
  const doneLabel = document.getElementById('examCompletedHoursLabel');
  if (doneLabel) {
    const pct = Math.min(100, Math.round((proj.completedHours / proj.targetHours) * 100));
    doneLabel.textContent = `${proj.completedHours.toFixed(1)}h Completed (${pct}%)`;
  }
  const targetLabel = document.getElementById('examTargetHoursLabel');
  if (targetLabel) targetLabel.textContent = `Target: ${proj.targetHours}h`;
  const progFill = document.getElementById('examProgressFill');
  if (progFill) {
    const pct = Math.min(100, Math.round((proj.completedHours / proj.targetHours) * 100));
    progFill.style.width = `${pct}%`;
  }
  const remVal = document.getElementById('examRemainingHoursVal');
  if (remVal) remVal.textContent = `${proj.remainingHours.toFixed(1)}h`;
  const reqVal = document.getElementById('examRequiredPaceVal');
  if (reqVal) reqVal.textContent = `${proj.requiredDailyHours.toFixed(1)}h/d`;
  const actVal = document.getElementById('examActualVelocityVal');
  if (actVal) actVal.textContent = `${proj.currentDailyHours.toFixed(1)}h/d`;
  const finishVal = document.getElementById('examProjectedDateVal');
  if (finishVal) finishVal.textContent = proj.projectedDate;
  const badgeEl = document.getElementById('examFeasibilityBadge');
  if (badgeEl) {
    badgeEl.textContent = proj.statusBadgeText;
    badgeEl.className = `exam-status-badge ${proj.statusBadgeClass}`;
  }
  const statusTextEl = document.getElementById('examFeasibilityText');
  if (statusTextEl) statusTextEl.textContent = proj.statusDescription;

  // 🎯 Focus Quality Score (FQS) Card
  const fqsVal = document.getElementById('fqsScoreVal');
  if (fqsVal) fqsVal.textContent = report.focusQualityScore;
  const fqsTier = document.getElementById('fqsTierBadge');
  if (fqsTier) {
    fqsTier.textContent = report.totalMinutes > 0 ? report.focusQualityTier : 'No Study Data';
    fqsTier.className = 'fqs-tier-badge';
    if (report.focusQualityScore >= 85) fqsTier.classList.add('elite');
    else if (report.focusQualityScore >= 70) fqsTier.classList.add('optimal');
    else if (report.focusQualityScore >= 50) fqsTier.classList.add('moderate');
    else if (report.totalMinutes > 0) fqsTier.classList.add('fragmented');
  }
  const fqsFill = document.getElementById('fqsFill');
  if (fqsFill) fqsFill.style.width = `${report.focusQualityScore}%`;
  const fqsCons = document.getElementById('fqsConsistencyVal');
  if (fqsCons) fqsCons.textContent = `${report.consistencyPct}%`;
  const fqsGoal = document.getElementById('fqsGoalHitVal');
  if (fqsGoal) fqsGoal.textContent = `${report.goalHitRate}%`;
  const fqsPace = document.getElementById('fqsPacingVal');
  if (fqsPace) fqsPace.textContent = `${report.pacingStability}%`;

  // 🔋 Cognitive Workload & Fatigue Card
  const fatigue = report.fatigueReport;
  const fatigueScoreVal = document.getElementById('fatigueScoreVal');
  if (fatigueScoreVal) fatigueScoreVal.textContent = fatigue.fatigueScore;
  const fatigueTierBadge = document.getElementById('fatigueTierBadge');
  if (fatigueTierBadge) {
    fatigueTierBadge.textContent = fatigue.tierLabel;
    fatigueTierBadge.className = `fatigue-tier-badge ${fatigue.tierClass}`;
  }
  const fatigueFill = document.getElementById('fatigueFill');
  if (fatigueFill) {
    fatigueFill.style.width = `${fatigue.fatigueScore}%`;
    fatigueFill.className = `fill ${fatigue.tierClass === 'fatigue' ? 'danger' : (fatigue.tierClass === 'sustained' ? 'gold' : 'green')}`;
  }
  const fatigueAvgLoad = document.getElementById('fatigueAvgLoadVal');
  if (fatigueAvgLoad) fatigueAvgLoad.textContent = `${fatigue.avgDailyHours7d.toFixed(1)}h`;
  const fatigueHighStrain = document.getElementById('fatigueHighStrainVal');
  if (fatigueHighStrain) fatigueHighStrain.textContent = `${fatigue.consecutiveHighDays}d`;
  const fatigueRestDays = document.getElementById('fatigueRestDaysVal');
  if (fatigueRestDays) fatigueRestDays.textContent = `${fatigue.recoveryDaysCount}d`;
  const fatigueAdvice = document.getElementById('fatigueAdviceText');
  if (fatigueAdvice) fatigueAdvice.textContent = fatigue.adviceText;

  // 🌅 Circadian Rhythm & 24h Focus Density Wave
  const peakBadge = document.getElementById('peakFocusBadge');
  if (peakBadge) {
    peakBadge.textContent = report.totalMinutes > 0 ? `Peak: ${report.peakFocusWindow}` : 'Peak: No Data';
  }
  const densityCanvas = document.getElementById('circadianDensityCanvas');
  if (densityCanvas) {
    drawCircadianDensityCurve(densityCanvas, report.hourlyMins);
  }

  // Circadian Summary Bars
  const circContainer = document.getElementById('circadianBars');
  if (circContainer) {
    const buckets = Object.values(report.circadianBuckets);
    const maxMins = Math.max(...buckets.map(b => b.mins), 1);
    circContainer.innerHTML = buckets.map(b => {
      const pctOfTotal = report.totalMinutes > 0 ? Math.round((b.mins / report.totalMinutes) * 100) : 0;
      const barFillPct = Math.min(100, Math.round((b.mins / maxMins) * 100));
      return `
        <div class="circadian-row">
          <div class="circadian-row-header">
            <span>${b.label}</span>
            <span><strong>${b.mins}m</strong> (${pctOfTotal}%)</span>
          </div>
          <div class="circadian-track">
            <div class="circadian-fill" style="width:${barFillPct}%"></div>
          </div>
        </div>
      `;
    }).join('');
  }

  // ⚡ Deep Work & Velocity Card
  const dwVal = document.getElementById('deepWorkRatioVal');
  if (dwVal) dwVal.textContent = `${report.deepWorkRatio}%`;
  const avgVal = document.getElementById('avgSessionVal');
  if (avgVal) avgVal.textContent = `${report.avgSessionMin}m`;
  const velVal = document.getElementById('studyVelocityVal');
  if (velVal) {
    if (report.totalMinutes > 0) {
      const sign = report.velocity >= 0 ? '+' : '';
      velVal.textContent = `${sign}${report.velocity}%`;
      velVal.style.color = report.velocity >= 0 ? 'var(--accent-bright)' : '#ef4444';
    } else {
      velVal.textContent = '0%';
      velVal.style.color = 'var(--text-dim)';
    }
  }
  const dwFill = document.getElementById('deepWorkFill');
  if (dwFill) dwFill.style.width = `${report.deepWorkRatio}%`;
  const dwSubtext = document.getElementById('deepWorkSubtext');
  if (dwSubtext) {
    dwSubtext.textContent = `${report.deepWorkMinutes} of ${report.totalMinutes} mins in sustained (≥45m) blocks`;
  }

  // 🕸️ Subject Equilibrium & Cognitive Radar Polygon
  const radarCanvas = document.getElementById('subjectRadarCanvas');
  if (radarCanvas) {
    drawSubjectRadar(radarCanvas, report.subjectEquilibrium);
  }

  // Ebbinghaus Scientific Recall Matrix
  const eqList = document.getElementById('subjectEquilibriumList');
  if (eqList) {
    if (report.subjectEquilibrium.length === 0) {
      eqList.innerHTML = `<div style="text-align:center; padding:12px; color:var(--text-dim); font-size:0.8rem;">No subjects studied in this timeframe.</div>`;
    } else {
      eqList.innerHTML = report.subjectEquilibrium.map(s => {
        const badgeText = s.daysAgo === 0 ? 'Active today' : (s.daysAgo === 1 ? 'Yesterday' : `${s.daysAgo}d ago`);
        const safeName = escapeHTML(s.name);
        const tagClass = s.retentionPct < 60 ? 'critical' : (s.retentionPct < 80 ? 'review' : 'optimal');
        const retentionColor = s.retentionPct < 60 ? '#ef4444' : (s.retentionPct < 80 ? '#fbbf24' : '#34d399');

        return `
          <div class="equilibrium-item">
            <div class="equilibrium-left">
              <div class="equilibrium-name">
                <span>${safeName}</span>
                <span style="font-size:0.7rem; color:var(--text-dim);">(${s.pct}% share)</span>
              </div>
              <div class="equilibrium-bar-wrap">
                <div class="equilibrium-bar-fill" style="width:${s.pct}%"></div>
              </div>
              <div style="display:flex; align-items:center; gap:8px; margin-top:6px;">
                <span style="font-size:0.68rem; color:var(--text-dim);">Retention (R):</span>
                <span style="font-size:0.72rem; font-weight:700; color:${retentionColor};">${s.retentionPct}%</span>
                <div style="flex:1; height:4px; background:rgba(255,255,255,0.08); border-radius:2px; overflow:hidden;">
                  <div class="retention-bar-fill ${tagClass}" style="width:${s.retentionPct}%; height:100%; border-radius:2px;"></div>
                </div>
                <span style="font-size:0.65rem; color:var(--text-dim); font-style:italic;">S=${s.stabilityDays}d (${s.sessionCount} sess)</span>
              </div>
            </div>
            <div class="equilibrium-right">
              <div class="equilibrium-time">${s.hours} hrs</div>
              <div class="recall-status-tag ${tagClass}">${s.recallStatus}</div>
              <div style="font-size:0.65rem; color:var(--text-dim); margin-top:3px;">${badgeText}</div>
            </div>
          </div>
        `;
      }).join('');
    }
  }

  // 🧩 Cognitive Work-Type Distribution
  const cwtList = document.getElementById('cognitiveWorkTypeList');
  if (cwtList) {
    if (report.cognitiveWorkTypes.length === 0) {
      cwtList.innerHTML = `<div style="text-align:center; padding:12px; color:var(--text-dim); font-size:0.8rem;">No activity recorded.</div>`;
    } else {
      cwtList.innerHTML = report.cognitiveWorkTypes.map(w => {
        const safeWork = escapeHTML(w.name);
        return `
          <div class="worktype-row">
            <div class="worktype-header">
              <span>${safeWork}</span>
              <span><strong>${w.mins}m</strong> (${w.pct}%)</span>
            </div>
            <div class="circadian-track">
              <div class="circadian-fill" style="width:${w.pct}%"></div>
            </div>
          </div>
        `;
      }).join('');
    }
  }
}

/* ============================================================
   📑 FULL EXECUTIVE ANALYSIS PDF EXPORT (100% PARITY & VECTORS)
   ============================================================ */
async function exportAnalysisPdf() {
  if (!window.jspdf || !window.jspdf.jsPDF) {
    showToast('jsPDF library not loaded');
    return;
  }
  const btn = document.getElementById('downloadAnalysisPdfBtn');
  if (btn) {
    btn.disabled = true;
    btn.innerHTML = '<span>⏳</span> Generating PDF...';
  }

  try {
    const report = computeAnalyticsReport(currentAnalyticsPeriod);
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF('p', 'mm', 'a4');
    const periodLabel = currentAnalyticsPeriod === 'all' ? 'All Time' : `Last ${currentAnalyticsPeriod} Days`;
    const today = todayStr();

    // Page 1: Dark Header Banner
    doc.setFillColor(4, 14, 8); // Dark #040e08
    doc.rect(0, 0, 210, 36, 'F');

    doc.setTextColor(52, 211, 153); // Accent bright #34d399
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(16);
    doc.text('FOCUS STUDY TIMER', 14, 15);

    doc.setTextColor(240, 253, 244);
    doc.setFontSize(11);
    doc.text('Cognitive Deep Analysis & Performance Report', 14, 22);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(167, 243, 208);
    doc.text(`Reporting Timeframe: ${cleanPdfText(periodLabel)}  |  Generated: ${today}`, 14, 29);

    let currentY = 42;

    const hasHistory = report.totalMinutes === 0 && report.allStudyTotalCount > 0;
    const effectiveReport = hasHistory ? computeAnalyticsReport('all') : report;

    if (hasHistory) {
      const fromLimit = currentAnalyticsPeriod === '7' ? daysAgoStr(6) : daysAgoStr(29);
      doc.autoTable({
        startY: currentY,
        head: [['NOTICE: SELECTED TIMEFRAME HAS 0 SESSIONS (' + cleanPdfText(periodLabel).toUpperCase() + ')']],
        body: [[
          cleanPdfText(
            `No study sessions were recorded during ${periodLabel} (${fromLimit} to ${today}).\n` +
            `Your account contains ${report.allStudyTotalCount} historical study sessions (${effectiveReport.totalHours} hrs total focus).\n` +
            `Below is your complete All-Time Historical Breakdown & Session Audit.`
          )
        ]],
        theme: 'grid',
        headStyles: { fillColor: [217, 119, 6], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 9 },
        styles: { fontSize: 8.5, cellPadding: 3, textColor: [120, 53, 15] },
        margin: { left: 14, right: 14 }
      });
      currentY = doc.lastAutoTable.finalY + 6;
    }

    // 1. Executive KPIs Table
    const velText = report.totalMinutes > 0 
      ? `${report.velocity >= 0 ? '+' : ''}${report.velocity}% vs prior period`
      : (hasHistory ? `All-Time: ${effectiveReport.totalHours} hrs` : 'N/A (0 min in period)');

    const execRows = [
      [
        cleanPdfText(`Total Focus: ${effectiveReport.totalHours} hrs (${effectiveReport.totalMinutes} mins)`),
        cleanPdfText(`Total Sessions: ${effectiveReport.sessionCount}`),
        cleanPdfText(`Active Days: ${effectiveReport.activeDaysCount}`)
      ],
      [
        cleanPdfText(`Focus Quality Score (FQS): ${effectiveReport.focusQualityScore}/100`),
        cleanPdfText(`Cognitive Tier: ${effectiveReport.focusQualityTier}`),
        cleanPdfText(`FQS Formula: 0.35*D + 0.25*C + 0.25*G + 0.15*P`)
      ],
      [
        cleanPdfText(`Deep Work Ratio: ${effectiveReport.deepWorkRatio}% (>= 45m blocks)`),
        cleanPdfText(`Avg Session Length: ${effectiveReport.avgSessionMin} mins`),
        cleanPdfText(`Study Velocity: ${velText}`)
      ],
      [
        cleanPdfText(`Circadian Peak Window: ${effectiveReport.peakFocusWindow}`),
        cleanPdfText(`Consistency: ${effectiveReport.consistencyPct}% | Goal Hit: ${effectiveReport.goalHitRate}%`),
        cleanPdfText(`Streak Status: ${currentStreakVal} days current (Best: ${bestStreakVal})`)
      ]
    ];

    const kpiTitle = hasHistory 
      ? `EXECUTIVE FOCUS PERFORMANCE SUMMARY (ALL-TIME HISTORICAL DATA)`
      : `EXECUTIVE PERFORMANCE & FOCUS STAMINA SUMMARY (${cleanPdfText(periodLabel).toUpperCase()})`;

    doc.autoTable({
      startY: currentY,
      head: [[kpiTitle, '', '']],
      body: execRows,
      theme: 'grid',
      headStyles: { fillColor: [16, 185, 129], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 9.5 },
      styles: { fontSize: 8.5, cellPadding: 3, textColor: [30, 41, 59] },
      margin: { left: 14, right: 14 }
    });

    currentY = doc.lastAutoTable.finalY + 6;

    // 2. Exam & Syllabus Projection Audit Table
    const proj = effectiveReport.examProjection;
    const projRows = [
      [
        cleanPdfText(`Exam / Goal: ${proj.examName}`),
        cleanPdfText(`Deadline: ${proj.targetDate} (${proj.daysRemaining} days left)`),
        cleanPdfText(`Status: ${proj.statusBadgeText}`)
      ],
      [
        cleanPdfText(`Completed: ${proj.completedHours.toFixed(1)}h / ${proj.targetHours}h`),
        cleanPdfText(`Remaining: ${proj.remainingHours.toFixed(1)}h`),
        cleanPdfText(`Required Pace: ${proj.requiredDailyHours.toFixed(1)} hrs/day`)
      ],
      [
        cleanPdfText(`Actual 7-Day Velocity: ${proj.currentDailyHours.toFixed(1)} hrs/day`),
        cleanPdfText(`Pace Delta: ${proj.paceDeltaHours >= 0 ? '+' : ''}${proj.paceDeltaHours.toFixed(1)} hrs/day`),
        cleanPdfText(`Projected Finish: ${proj.projectedDate}`)
      ],
      [
        { content: cleanPdfText(`Projection Audit: ${proj.statusDescription}`), colSpan: 3, styles: { fontStyle: 'italic', textColor: [71, 85, 105] } }
      ]
    ];

    doc.autoTable({
      startY: currentY,
      head: [['EXAM & SYLLABUS PROJECTION AUDIT TABLE', '', '']],
      body: projRows,
      theme: 'grid',
      headStyles: { fillColor: [6, 95, 70], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 9 },
      styles: { fontSize: 8, cellPadding: 2.5 },
      margin: { left: 14, right: 14 }
    });

    currentY = doc.lastAutoTable.finalY + 6;

    // 3. Cognitive Workload & Fatigue Index Table
    const fatigue = effectiveReport.fatigueReport;
    const fatigueRows = [
      [
        cleanPdfText(`Fatigue Index Score: ${fatigue.fatigueScore} / 100`),
        cleanPdfText(`Classification: ${fatigue.tierLabel}`),
        cleanPdfText(`7-Day Daily Load: ${fatigue.avgDailyHours7d.toFixed(1)} hrs/day`)
      ],
      [
        cleanPdfText(`High-Strain Days (>=5h): ${fatigue.consecutiveHighDays} consecutive`),
        cleanPdfText(`Recovery Days (<2h): ${fatigue.recoveryDaysCount} of 7 days`),
        cleanPdfText(`Rest Ratio: ${Math.round((fatigue.recoveryDaysCount / 7) * 100)}%`)
      ],
      [
        { content: cleanPdfText(`Behavioral Assessment: ${fatigue.adviceText}`), colSpan: 3, styles: { fontStyle: 'italic', textColor: [71, 85, 105] } }
      ]
    ];

    doc.autoTable({
      startY: currentY,
      head: [['COGNITIVE WORKLOAD & RECOVERY ASSESSMENT', '', '']],
      body: fatigueRows,
      theme: 'grid',
      headStyles: { fillColor: [30, 41, 59], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 9 },
      styles: { fontSize: 8, cellPadding: 2.5 },
      margin: { left: 14, right: 14 }
    });

    currentY = doc.lastAutoTable.finalY + 6;

    // 4. Vector 24-Hour Continuous Focus Density Wave (Drawn directly on Page 1)
    doc.setFontSize(8.5);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(30, 41, 59);
    doc.text('24-HOUR CONTINUOUS FOCUS DENSITY WAVE (00:00 - 23:59)', 14, currentY + 3);
    currentY += 5;

    drawPdfCircadianWave(doc, 14, currentY, 182, 38, effectiveReport.hourlyMins);
    currentY += 44;

    // ---------------- PAGE 2 ----------------
    doc.addPage();
    currentY = 18;

    // 5. Subject Equilibrium & Ebbinghaus Scientific Recall Matrix Table
    const subjRows = effectiveReport.subjectEquilibrium.map(s => {
      const statusText = s.daysAgo === 0 ? 'Today' : (s.daysAgo === 1 ? 'Yesterday' : `${s.daysAgo} days ago`);
      const recallText = s.retentionPct < 60 
        ? `CRITICAL RECALL DUE (${s.retentionPct}%)` 
        : (s.retentionPct < 80 ? `REVIEW RECOMMENDED (${s.retentionPct}%)` : `OPTIMAL RETENTION (${s.retentionPct}%)`);
      return [
        cleanPdfText(s.name), 
        `${s.hours} hrs (${s.mins}m)`, 
        `${s.pct}%`, 
        statusText, 
        `${s.retentionPct}% (S=${s.stabilityDays}d)`, 
        recallText
      ];
    });

    doc.autoTable({
      startY: currentY,
      head: [['SUBJECT EQUILIBRIUM & EBBINGHAUS SCIENTIFIC RECALL MATRIX', 'TOTAL TIME', 'SHARE', 'LAST STUDIED', 'RETENTION (R)', 'SCIENTIFIC RECALL STATUS']],
      body: subjRows.length > 0 ? subjRows : [['No subjects recorded in this period', '-', '-', '-', '-', '-']],
      theme: 'striped',
      headStyles: { fillColor: [201, 150, 47], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 8.5 },
      styles: { fontSize: 7.5, cellPadding: 2.2 },
      margin: { left: 14, right: 14 }
    });

    currentY = doc.lastAutoTable.finalY + 8;

    // 6. Vector Subject Equilibrium Radar Chart (Drawn directly on Page 2)
    doc.setFontSize(8.5);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(30, 41, 59);
    doc.text('CURRICULUM EQUILIBRIUM RADAR POLYGON', 14, currentY + 3);
    currentY += 6;

    drawPdfRadarChart(doc, 105, currentY + 32, 28, effectiveReport.subjectEquilibrium);
    currentY += 68;

    // 7. Cognitive Work Modality Breakdown Table
    const workRows = effectiveReport.cognitiveWorkTypes.map(w => {
      const activeType = ['Revision', 'Practice', 'Mock Test'].includes(w.name) ? 'Active Recall / Test' : 'Content Acquisition / Notes';
      return [cleanPdfText(w.name), `${w.mins} mins`, `${w.pct}%`, activeType];
    });

    doc.autoTable({
      startY: currentY,
      head: [['COGNITIVE WORK MODALITY', 'DURATION', 'SHARE (%)', 'COGNITIVE TYPE']],
      body: workRows.length > 0 ? workRows : [['No activity recorded in this period', '-', '-', '-']],
      theme: 'striped',
      headStyles: { fillColor: [30, 41, 59], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 9 },
      styles: { fontSize: 8, cellPadding: 2.2 },
      margin: { left: 14, right: 14 }
    });

    currentY = doc.lastAutoTable.finalY + 8;

    // 8. Deterministic Cognitive Insights Box
    const insightRows = effectiveReport.smartInsights.map(i => {
      return [cleanPdfText(`${i.icon || '[Insight]'} ${i.text}`)];
    });

    doc.autoTable({
      startY: currentY,
      head: [['DETERMINISTIC COGNITIVE INSIGHTS & ACTIONABLE RECOMMENDATIONS']],
      body: insightRows.length > 0 ? insightRows : [['[Insight] Maintain consistent daily study routines to build momentum.']],
      theme: 'grid',
      headStyles: { fillColor: [16, 185, 129], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 9 },
      styles: { fontSize: 8.5, cellPadding: 3, textColor: [15, 23, 42] },
      margin: { left: 14, right: 14 }
    });

    // ---------------- PAGE 3+ ----------------
    // 9. Complete Session Audit Log Table
    doc.addPage();
    currentY = 18;

    const sessionRows = effectiveReport.filteredSessions.slice(0, 75).map(s => {
      const timeStr = s.ts ? formatTimeRange(s.ts, s.minutes) : s.date;
      return [s.date, cleanPdfText(timeStr), cleanPdfText(s.subject), cleanPdfText(s.workType || 'Other'), `${s.minutes}m`];
    });

    const auditHeader = hasHistory 
      ? `SESSION AUDIT LOG (ALL TIME HISTORICAL SESSIONS: ${effectiveReport.sessionCount})`
      : `SESSION AUDIT LOG (${cleanPdfText(periodLabel).toUpperCase()})`;

    doc.autoTable({
      startY: currentY,
      head: [[auditHeader, 'TIME WINDOW', 'SUBJECT', 'WORK TYPE', 'MINUTES']],
      body: sessionRows.length > 0 ? sessionRows : [['No sessions recorded in this period', '-', '-', '-', '-']],
      theme: 'striped',
      headStyles: { fillColor: [71, 85, 105], textColor: [255, 255, 255], fontStyle: 'bold', fontSize: 8.5 },
      styles: { fontSize: 7.5, cellPadding: 2 },
      margin: { left: 14, right: 14 }
    });

    // Page Numbers Footer on all pages
    const pageCount = doc.internal.getNumberOfPages();
    for (let i = 1; i <= pageCount; i++) {
      doc.setPage(i);
      doc.setFontSize(8);
      doc.setTextColor(148, 163, 184);
      doc.text(`Page ${i} of ${pageCount}  |  Focus Study Timer Cognitive Analytics Report`, 105, 290, { align: 'center' });
    }

    const filename = `Focus_Study_Analysis_Report_${periodLabel.replace(/\s+/g, '_')}_${today}.pdf`;
    doc.save(filename);
    showToast('📑 Analysis PDF downloaded successfully!');
  } catch (err) {
    console.error('PDF export error:', err);
    showToast('Failed to export Analysis PDF: ' + err.message);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerHTML = '<span>📑</span> Full Analysis PDF';
    }
  }
}

function setupAnalyticsEvents() {
  document.querySelectorAll('.analytics-timeframe-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.analytics-timeframe-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentAnalyticsPeriod = btn.getAttribute('data-period');
      renderDeepAnalytics();
    });
  });

  const downloadAnalysisPdfBtn = document.getElementById('downloadAnalysisPdfBtn');
  if (downloadAnalysisPdfBtn) {
    downloadAnalysisPdfBtn.addEventListener('click', exportAnalysisPdf);
  }

  // Exam Goal Edit Modal Setup
  const examGoalEditBtn = document.getElementById('examGoalEditBtn');
  const examGoalModal = document.getElementById('examGoalModal');
  const examGoalCancelBtn = document.getElementById('examGoalCancelBtn');
  const examGoalSaveBtn = document.getElementById('examGoalSaveBtn');
  const examGoalNameInput = document.getElementById('examGoalNameInput');
  const examGoalDateInput = document.getElementById('examGoalDateInput');
  const examGoalHoursInput = document.getElementById('examGoalHoursInput');
  const examGoalScopeChips = document.getElementById('examGoalScopeChips');

  let selectedScopes = new Set();

  if (examGoalEditBtn && examGoalModal) {
    examGoalEditBtn.addEventListener('click', () => {
      const goal = getExamGoal();
      if (examGoalNameInput) examGoalNameInput.value = goal.examName || '';
      if (examGoalDateInput) examGoalDateInput.value = goal.targetDate || '';
      if (examGoalHoursInput) examGoalHoursInput.value = goal.targetHours || 100;

      selectedScopes = new Set(goal.subjectScope || []);

      // Populate Scope Chips from distinct available subjects
      if (examGoalScopeChips) {
        const availableSubjs = [...new Set([
          ...(prefs.subjects || []).map(s => typeof s === 'string' ? s : s.name),
          ...sessions.map(s => s.subject && s.subject.includes(' - ') ? s.subject.split(' - ')[0] : s.subject)
        ])].filter(Boolean);

        examGoalScopeChips.innerHTML = availableSubjs.map(subj => {
          const isAct = selectedScopes.has(subj);
          return `<div class="sel-chip ${isAct ? 'active' : ''}" data-subject="${escapeHTML(subj)}">${escapeHTML(subj)}</div>`;
        }).join('');

        examGoalScopeChips.querySelectorAll('.sel-chip').forEach(chip => {
          chip.addEventListener('click', () => {
            const subj = chip.getAttribute('data-subject');
            if (selectedScopes.has(subj)) {
              selectedScopes.delete(subj);
              chip.classList.remove('active');
            } else {
              selectedScopes.add(subj);
              chip.classList.add('active');
            }
          });
        });
      }

      examGoalModal.classList.add('show');
    });
  }

  if (examGoalCancelBtn && examGoalModal) {
    examGoalCancelBtn.addEventListener('click', () => {
      examGoalModal.classList.remove('show');
    });
  }

  if (examGoalSaveBtn && examGoalModal) {
    examGoalSaveBtn.addEventListener('click', () => {
      const name = examGoalNameInput?.value.trim() || 'Target Exam';
      const date = examGoalDateInput?.value || '';
      const hours = Number(examGoalHoursInput?.value) || 100;

      saveExamGoal({
        examName: name,
        targetDate: date,
        targetHours: hours,
        subjectScope: Array.from(selectedScopes)
      });

      examGoalModal.classList.remove('show');
      showToast('🎯 Exam goal updated successfully!');
    });
  }
}
setupAnalyticsEvents();

/* ---------- heatmap ---------- */
function renderHeatmap(){
  const grid = document.getElementById('heatmap');
  const totalsByDate = {};
  sessions.forEach(s=> totalsByDate[s.date]=(totalsByDate[s.date]||0)+s.minutes);
  const days = 84; 
  const cells = [];
  for(let i=days-1;i>=0;i--){
    const d = daysAgoStr(i);
    const min = totalsByDate[d]||0;
    let level = 0;
    if(min>0 && min<=30) level=1; else if(min>30 && min<=60) level=2; else if(min>60) level=3;
    cells.push({d, min, level});
  }
  grid.innerHTML = cells.map(c=>`<div class="hcell level-${c.level}" title="${c.d}: ${c.min} mins"></div>`).join('');
}

/* ---------- history ---------- */
function renderHistory(){
  const list = document.getElementById('historyList');
  const allSess = [...sessions, ...nonStudySessions];
  const sorted = allSess.sort((a,b)=> (b.ts||0)-(a.ts||0)).slice(0,30);
  if(sorted.length===0){ list.innerHTML='<div class="empty-note">No sessions yet</div>'; return; }
  list.innerHTML = sorted.map(s=>{
    const isNS = s.isNonStudy || (s.id && String(s.id).startsWith('ns'));
    const badge = isNS ? `<span style="background:var(--line); padding:2px 6px; border-radius:4px; font-size:0.7rem; display:inline-block; width:max-content;">Non-Study</span>` : '';
    const safeSubject = escapeHTML(s.subject);
    const safeWorkType = escapeHTML(s.workType || 'Other');
    const safeId = escapeHTML(s.id);
    const timeStr = s.ts ? formatTimeRange(s.ts, s.minutes) : escapeHTML(s.date);
    return `
    <div class="hist-row">
      <div class="hist-left">
        ${safeSubject} ${badge}
        <span class="hist-meta">[${safeWorkType}] &nbsp; ${timeStr}</span>
      </div>
      <div class="hist-right"><span class="hist-min">${Number(s.minutes) || 0} min</span><button class="del-btn" data-id="${safeId}">×</button></div>
    </div>`;
  }).join('');
  list.querySelectorAll('.del-btn').forEach(b=>{
    b.addEventListener('click', async ()=>{
      await deleteSession(b.dataset.id);
      await refreshEverything();
      showToast('Session deleted');
    });
  });
}

/* ---------- report export ---------- */
function getExportSessions(){
  const range = document.getElementById('exportRange').value;
  const allSess = [...sessions, ...nonStudySessions];
  if(range === 'all') return allSess;
  if(range === 'today') {
    const today = todayStr();
    return allSess.filter(s => s.date === today);
  }
  if(range === '24h') {
    const limit = Date.now() - 24 * 3600 * 1000;
    return allSess.filter(s => s.ts >= limit);
  }
  if(range === '48h') {
    const limit = Date.now() - 48 * 3600 * 1000;
    return allSess.filter(s => s.ts >= limit);
  }
  const limitDate = daysAgoStr(parseInt(range));
  return allSess.filter(s => s.date >= limitDate);
}

// Note: b2eSubject, b2eWorkType, and toBanglish are defined in the Translation & PDF section above.


const translationCache = new Map();

async function translateBatch(strings) {
  if (!strings || strings.length === 0) return {};
  
  const needingTranslation = [];
  const resultMap = {};

  strings.forEach(s => {
    if (!s) return;
    if (translationCache.has(s)) {
      resultMap[s] = translationCache.get(s);
    } else if (/[\u0980-\u09FF]/.test(s)) {
      needingTranslation.push(s);
    } else {
      // String contains no Bengali characters - return identity immediately (0 ms)
      resultMap[s] = s;
      translationCache.set(s, s);
    }
  });

  if (needingTranslation.length === 0) {
    return resultMap;
  }

  const query = needingTranslation.join(' ||| ');
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=bn&tl=en&dt=t&q=${encodeURIComponent(query)}`;
  try {
    const controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const timeoutId = controller ? setTimeout(() => controller.abort(), 1500) : null;
    const res = await fetch(url, controller ? { signal: controller.signal } : {});
    if (timeoutId) clearTimeout(timeoutId);
    const json = await res.json();
    let translated = '';
    json[0].forEach(part => translated += part[0]);
    const translatedParts = translated.split('|||').map(s => s.trim());
    needingTranslation.forEach((s, i) => {
      const val = translatedParts[i] || toBanglish(s);
      resultMap[s] = val;
      translationCache.set(s, val);
    });
    return resultMap;
  } catch(e) {
    console.warn('Translation API skipped/timed out, using Banglish fallback:', e);
    needingTranslation.forEach(s => {
      const val = toBanglish(s);
      resultMap[s] = val;
      translationCache.set(s, val);
    });
    return resultMap;
  }
}

document.getElementById('exportPdfBtn').addEventListener('click', async ()=>{
  const data = getExportSessions();
  if(data.length===0){ showToast('No data to export'); return; }
  
  const btn = document.getElementById('exportPdfBtn');
  btn.disabled = true;

  try {
    const uniqueStrings = new Set();
    data.forEach(s => {
      if (s.subject && !b2eSubject[s.subject]) uniqueStrings.add(s.subject);
      if (s.workType && !b2eWorkType[s.workType]) uniqueStrings.add(s.workType);
    });
    
    // Only show "Translating..." if there are actually uncached Bengali characters
    const hasUncachedBengali = Array.from(uniqueStrings).some(s => /[\u0980-\u09FF]/.test(s) && !translationCache.has(s));
    btn.textContent = hasUncachedBengali ? 'Translating...' : 'Preparing...';

    const translationMap = await translateBatch(Array.from(uniqueStrings));
    
    function getEn(str, isSubject) {
      if(!str) return '';
      if(isSubject && b2eSubject[str]) return b2eSubject[str];
      if(!isSubject && b2eWorkType[str]) return b2eWorkType[str];
      return translationMap[str] || str;
    }

    btn.textContent = 'Preparing...';

    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();
    
    const range = document.getElementById('exportRange').value;
    let titleStr = 'Study Report';
    if(range === 'all') titleStr = 'Full Study Report';
    else if(range === 'today') titleStr = 'Study Report (Today)';
    else if(range === '24h') titleStr = 'Study Report (Last 24h)';
    else if(range === '48h') titleStr = 'Study Report (Last 48h)';
    else titleStr = `Study Report (Last ${range} Days)`;
    
    let studyMin = 0; let nonStudyMin = 0;
    data.forEach(s => {
      const isNS = s.isNonStudy || (s.id && String(s.id).startsWith('ns'));
      if(isNS) nonStudyMin += s.minutes;
      else studyMin += s.minutes;
    });
    
    const studyHr = (studyMin/60).toFixed(1);
    const nonStudyHr = (nonStudyMin/60).toFixed(1);

    doc.setFontSize(18);
    doc.text('Focus Study Timer', 14, 22);
    doc.setFontSize(12);
    doc.setTextColor(100);
    doc.text(titleStr, 14, 30);
    
    doc.setFontSize(11);
    doc.setTextColor(0);
    doc.text(`Total Study: ${studyHr} hrs`, 14, 40);
    doc.text(`Non-Study: ${nonStudyHr} hrs`, 14, 46);

    const tableData = data.sort((a,b)=>(a.ts||0) - (b.ts||0)).map(s => {
      const isNS = s.isNonStudy || (s.id && String(s.id).startsWith('ns')) ? 'Non-Study' : 'Study';
      const timeRange = s.ts ? formatTimeRangeOnlyTime(s.ts, s.minutes) : '-';
      return [
        cleanPdfText(s.date),
        cleanPdfText(timeRange),
        cleanPdfText(getEn(s.subject, true)), 
        cleanPdfText(getEn(s.workType || 'N/A', false)),
        `${s.minutes} min`,
        isNS
      ];
    });

    doc.autoTable({
      startY: 52,
      head: [['Date', 'Time', 'Subject', 'Work Type', 'Minutes', 'Category']],
      body: tableData,
      theme: 'grid',
      headStyles: { fillColor: [201, 150, 47] },
      alternateRowStyles: { fillColor: [250, 250, 250] },
      styles: { fontSize: 10 },
      columnStyles: {
        0: { cellWidth: 24 }, 1: { cellWidth: 38 }, 2: { cellWidth: 'auto' }, 
        3: { cellWidth: 26 }, 4: { cellWidth: 18 }, 5: { cellWidth: 24 }  
      }
    });

    doc.save('focus-timer-report.pdf');
  } catch(err) {
    console.error(err);
    showToast('PDF Export failed');
  } finally {
    btn.textContent = 'PDF';
    btn.disabled = false;
  }
});

/* ---------- JSON Backup & Restore ---------- */
document.getElementById('backupJsonBtn').addEventListener('click', ()=>{
  const backupData = {
    timestamp: Date.now(),
    sessions,
    nonStudySessions,
    settings: {
      dailyTargetMinutes: (typeof prefs !== 'undefined' && prefs.dailyTarget) ? prefs.dailyTarget : 120
    },
    examGoal: (typeof getExamGoal === 'function') ? getExamGoal() : null
  };
  const jsonStr = JSON.stringify(backupData, null, 2);
  const blob = new Blob([jsonStr], { type:'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `focus-timer-backup-${todayStr()}.json`;
  a.click();
  showToast('Data backup download started');
});

document.getElementById('restoreJsonBtn').addEventListener('click', ()=>{
  document.getElementById('restoreFileInput').click();
});

document.getElementById('restoreFileInput').addEventListener('change', (e)=>{
  const file = e.target.files[0];
  if(!file) return;
  const reader = new FileReader();
  reader.onload = async (ev) => {
    try {
      const data = JSON.parse(ev.target.result);
      if (!data || (!Array.isArray(data.sessions) && !Array.isArray(data.nonStudySessions))) {
        showToast('Invalid backup file');
        return;
      }

      // Restore settings & examGoal if present in backup
      if (data.settings && (data.settings.dailyTargetMinutes || data.settings.dailyTarget)) {
        const tVal = Number(data.settings.dailyTargetMinutes || data.settings.dailyTarget);
        if (tVal > 0) {
          prefs.dailyTarget = tVal;
          const targetInput = document.getElementById('targetInput');
          if (targetInput) targetInput.value = tVal;
          await savePrefs();
        }
      }
      if (data.examGoal && typeof data.examGoal === 'object' && typeof saveExamGoal === 'function') {
        saveExamGoal(data.examGoal);
      }
      
      let newSessCount = 0;
      let newNonStudyCount = 0;

      const rawNonStudy = Array.isArray(data.nonStudySessions) ? data.nonStudySessions : [];
      for (const nsRaw of rawNonStudy) {
        if (!nsRaw || !nsRaw.date || !nsRaw.subject) continue;
        const ts = Number(nsRaw.ts || nsRaw.timestamp) || Date.now();
        const nsId = String(nsRaw.id || ('ns_' + ts));
        const ns = {
          ...nsRaw,
          id: nsId,
          ts,
          date: String(nsRaw.date),
          subject: escapeHTML(nsRaw.subject),
          workType: escapeHTML(nsRaw.workType || 'Other'),
          minutes: Math.max(0, Math.round(Number(nsRaw.minutes)) || 0),
          isNonStudy: true
        };
        if (ns.minutes <= 0) continue;
        const isDuplicate = nonStudySessions.some(s => (s.ts && ns.ts && s.ts === ns.ts) || (s.id && ns.id && s.id === ns.id && (s.id.startsWith('ns_') || s.id.startsWith('s_'))));
        if (!isDuplicate) {
          nonStudySessions.push(ns);
          newNonStudyCount++;
          if (currentUser) {
            await setDoc(doc(db, 'users', currentUser.uid, 'nonStudySessions', ns.id), ns);
          }
        }
      }
      if (!currentUser) { localSet('st_nonstudy_sessions', nonStudySessions); }

      const rawSessions = Array.isArray(data.sessions) ? data.sessions : [];
      for (const sRaw of rawSessions) {
        if (!sRaw || !sRaw.date || !sRaw.subject) continue;
        const ts = Number(sRaw.ts || sRaw.timestamp) || Date.now();
        const sId = String(sRaw.id || ('s_' + ts));
        const s = {
          ...sRaw,
          id: sId,
          ts,
          date: String(sRaw.date),
          subject: escapeHTML(sRaw.subject),
          workType: escapeHTML(sRaw.workType || 'Other'),
          minutes: Math.max(0, Math.round(Number(sRaw.minutes)) || 0)
        };
        if (s.minutes <= 0) continue;
        const isDuplicate = sessions.some(curr => (curr.ts && s.ts && curr.ts === s.ts) || (curr.id && s.id && curr.id === s.id && (curr.id.startsWith('s_') || curr.id.startsWith('ns_'))));
        if (!isDuplicate) {
          sessions.push(s);
          newSessCount++;
          if (currentUser) {
            await setDoc(doc(db, 'users', currentUser.uid, 'sessions', s.id), s);
          }
        }
      }
      if (!currentUser) { localSet(LS_SESSIONS, sessions); }
      await refreshEverything();
      showToast(`Restore successful: ${newSessCount} study, ${newNonStudyCount} non-study added!`);
    } catch(err) {
      console.error(err); showToast('Error reading backup file');
    }
  };
  reader.readAsText(file);
  e.target.value = ''; 
});

/* ---------- refresh everything (debounced via requestAnimationFrame for 60fps performance) ---------- */
let refreshRafId = null;
function refreshEverything(){
  if (refreshRafId) cancelAnimationFrame(refreshRafId);
  return new Promise(resolve => {
    refreshRafId = requestAnimationFrame(() => {
      refreshRafId = null;
      renderTarget();
      renderLevel();
      renderCompare();
      renderStats();
      renderStreak();
      renderMilestoneBadges();
      renderDeepAnalytics();
      renderHeatmap();
      renderHistory();
      renderSubjectAnalytics();
      resolve();
    });
  });
}

/* ---------- init / resume timer across reload (local only) ---------- */
let timerStateInitialized = false;
function initTimerFromLocalState(){
  if (timerStateInitialized) return;
  timerStateInitialized = true;
  const state = localGet(LS_TIMERSTATE, null);
  if(!state) return;
  
  selectedSubject = state.subject || prefs.subjects[0]?.name; 
  selectedSubMenu = state.subMenu || '';
  selectedWorkType = state.workType || 'Revision';

  totalSeconds = state.totalSeconds;
  stopwatchMode = !!state.stopwatchMode;
  pomodoroMode = !!state.pomodoroMode; pomoPhase = state.pomoPhase || 'work';
  pomoToggle.checked = pomodoroMode;
  customTimeRow.style.display = pomodoroMode ? 'none' : 'flex';
  phaseBadge.style.display = pomodoroMode ? 'block' : 'none';
  updatePhaseBadge();
  
  let wasActive = !!state.running;
  if (!wasActive && state.remaining !== undefined) {
    if (stopwatchMode && state.remaining > 0) wasActive = true;
    if (!stopwatchMode && state.remaining < state.totalSeconds && state.remaining > 0) wasActive = true;
  }
  isTimerActive = wasActive;

  if(state.running){
    if(stopwatchMode){
      startAt = state.startAt;
      remaining = (Date.now()-startAt)/1000;
      startTimer(true);
    } else {
      remaining = Math.max(0,(state.endTime-Date.now())/1000);
      if(remaining<=0){ finish(); }
      else { endTime = state.endTime; startTimer(true); }
    }
  } else {
    remaining = state.remaining;
    if(stopwatchMode){ startAt = state.startAt; }
    else { endTime = state.endTime; }
    hoursEl.disabled=true; minutesEl.disabled=true;
    hintEl.textContent = stopwatchMode ? 'Paused - press Start to resume (Press Reset to save time)' : 'Paused - press Start to resume';
    startBtn.disabled=false; pauseBtn.disabled=true;
    render();
  }
  renderSelectionChips();
}

/* ---------- timers run in real time ----------
   Countdown & stopwatch always keep counting in real time via absolute
   timestamps (endTime / startAt), so time spent with the browser closed
   or the device off is still counted. On reload the timer resumes with
   the correct elapsed time (or finishes if it already expired). */

(async function init(){
  try { localStorage.removeItem('st_app_theme'); } catch(e) {}
  document.body.setAttribute('data-theme', 'dark');
  renderAuthBar();
  await loadAll();
  
  if(selectedSubject !== '' && !prefs.subjects.find(s => s.name === selectedSubject)) {
    selectedSubject = prefs.subjects[0]?.name || '';
  }
  if(selectedWorkType !== '' && !prefs.workTypes.includes(selectedWorkType)) {
    selectedWorkType = prefs.workTypes[0] || '';
  }
  
  renderSelectionChips();
  initTimerFromLocalState();
  await refreshEverything();
})();

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').then(registration => {
      console.log('SW registered: ', registration.scope);
    });
  });
}

/* ---------- Desktop & Responsive Navigation ---------- */
const stageTitleEl = document.getElementById('stageTitle');
const stageTitles = {
  'view-timer': 'Focus Timer',
  'view-stats': 'Performance Dashboard',
  'view-history': 'History & Data'
};

const desktopShell = document.getElementById('desktopShell');
const appSidebar = document.getElementById('appSidebar');
const sidebarToggleBtn = document.getElementById('sidebarToggleBtn');
const mobileMenuBtn = document.getElementById('mobileMenuBtn');

// Restore sidebar collapse preference from local storage
if (localStorage.getItem('ft_sidebar_collapsed') === '1' && desktopShell) {
  desktopShell.classList.add('sidebar-collapsed');
}

if (sidebarToggleBtn && desktopShell) {
  sidebarToggleBtn.addEventListener('click', () => {
    desktopShell.classList.toggle('sidebar-collapsed');
    const isCollapsed = desktopShell.classList.contains('sidebar-collapsed');
    localStorage.setItem('ft_sidebar_collapsed', isCollapsed ? '1' : '0');
  });
}

if (mobileMenuBtn && appSidebar) {
  mobileMenuBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    appSidebar.classList.toggle('mobile-open');
  });
}

// Close mobile sidebar when clicking outside on mobile viewports
document.addEventListener('click', (e) => {
  if (appSidebar && appSidebar.classList.contains('mobile-open')) {
    if (!appSidebar.contains(e.target) && (!mobileMenuBtn || !mobileMenuBtn.contains(e.target))) {
      appSidebar.classList.remove('mobile-open');
    }
  }
});

function switchView(targetId) {
  document.querySelectorAll('.sidebar-nav .nav-item, .nav-item').forEach(nav => {
    if (nav.getAttribute('data-target') === targetId) {
      nav.classList.add('active');
    } else {
      nav.classList.remove('active');
    }
  });

  document.querySelectorAll('.view-section').forEach(view => view.classList.remove('active'));
  const targetView = document.getElementById(targetId);
  if (targetView) targetView.classList.add('active');

  if (stageTitleEl && stageTitles[targetId]) {
    stageTitleEl.textContent = stageTitles[targetId];
  }

  if (appSidebar) {
    appSidebar.classList.remove('mobile-open');
  }

  // Refresh charts and badges when entering stats
  if (targetId === 'view-stats') {
    renderCharts();
    renderMilestoneBadges();
  }
}

document.querySelectorAll('.sidebar-nav .nav-item, .nav-item').forEach(item => {
  item.addEventListener('click', () => {
    const targetId = item.getAttribute('data-target');
    if (targetId) switchView(targetId);
  });
});

/* ---------- Hash Routing for Deep-linking (e.g. from Android App) ---------- */
function handleHashRoute() {
  const hash = (window.location.hash || '').toLowerCase().replace(/^#\/?/, '');
  if (hash === 'stats' || hash === 'analytics' || hash === 'report') {
    switchView('view-stats');
  } else if (hash === 'history' || hash === 'data' || hash === 'pdf') {
    switchView('view-history');
  } else if (hash === 'timer') {
    switchView('view-timer');
  }
}
window.addEventListener('hashchange', handleHashRoute);
window.addEventListener('DOMContentLoaded', handleHashRoute);
handleHashRoute();

/* ---------- Bulk Delete ---------- */
document.getElementById('bulkDeleteBtn').addEventListener('click', async () => {
  const fromDate = document.getElementById('bulkDelFrom').value;
  const toDate = document.getElementById('bulkDelTo').value;

  if(!fromDate || !toDate) { showToast('Please select From and To dates!'); return; }
  if(fromDate > toDate) { showToast('From Date cannot be greater than To Date!'); return; }

  const allSess = [...sessions, ...nonStudySessions];
  const toDelete = allSess.filter(s => s.date >= fromDate && s.date <= toDate);
  
  if(toDelete.length === 0) { showToast('No sessions found in this date range!'); return; }

  if(confirm(`Are you sure you want to delete ${toDelete.length} sessions from ${fromDate} to ${toDate}? This cannot be undone!`)) {
    if (currentUser) {
      try {
        for (let i = 0; i < toDelete.length; i += 500) {
          const batch = writeBatch(db);
          const chunk = toDelete.slice(i, i + 500);
          chunk.forEach(s => {
            const isNS = s.isNonStudy || (s.id && String(s.id).startsWith('ns'));
            const colName = isNS ? 'nonStudySessions' : 'sessions';
            batch.delete(doc(db, 'users', currentUser.uid, colName, s.id));
          });
          await batch.commit();
        }
        sessions = sessions.filter(s => !toDelete.some(d => d.id === s.id));
        nonStudySessions = nonStudySessions.filter(s => !toDelete.some(d => d.id === s.id));
      } catch(err) {
        console.error(err);
        showToast('Failed to delete!');
        return;
      }
    } else {
      for(const s of toDelete) { await deleteSession(s.id); }
    }
    await refreshEverything();
    showToast(`${toDelete.length} sessions successfully deleted!`);
  }
});

/* ============================================================
   🎯 ADVANCED FEATURES (Confetti, Target Detection, Analytics)
   ============================================================ */

// SINGLE CONFETTI FUNCTION (Theme responsive)
function triggerConfetti() {
  const container = document.getElementById('particles');
  if(!container) return;
  container.innerHTML = ''; 
  const computed = getComputedStyle(document.body);
  const accent = computed.getPropertyValue('--accent').trim() || '#10b981';
  const bright = computed.getPropertyValue('--accent-bright').trim() || '#34d399';
  const glow = computed.getPropertyValue('--accent-glow').trim() || '#6ee7b7';
  const colors = [accent, bright, glow, '#ffffff'];
  for (let i = 0; i < 50; i++) {
    const p = document.createElement('div');
    p.classList.add('particle');
    p.style.left = (Math.random() * 100) + 'vw';
    p.style.top = '-20px';
    p.style.background = colors[Math.floor(Math.random() * colors.length)];
    const size = Math.random() * 8 + 4;
    p.style.width = size + 'px'; p.style.height = size + 'px';
    if(Math.random() > 0.5) p.style.borderRadius = '0'; 
    const duration = Math.random() * 2 + 1.5;
    const delay = Math.random() * 0.5;
    p.style.animationDuration = duration + 's';
    p.style.animationDelay = delay + 's';
    container.appendChild(p);
    setTimeout(() => p.remove(), (duration + delay) * 1000);
  }
}

// Intercept renderTarget to trigger confetti when reaching target
const oldRenderTarget = renderTarget;
renderTarget = function() {
  const today = todayStr();
  const prevDone = parseInt(document.getElementById('targetDone').getAttribute('data-prev') || '0');
  oldRenderTarget();
  const done = sessions.filter(s=>s.date===today).reduce((a,s)=>a+s.minutes,0);
  document.getElementById('targetDone').setAttribute('data-prev', done);
  
  if (prevDone < prefs.dailyTarget && done >= prefs.dailyTarget) {
    triggerConfetti();
    showToast('🎉 Daily target achieved! Great job!');
  }
};

// 7-Day Subject Analytics Logic (Groups by main subject)
function renderSubjectAnalytics() {
  const listContainer = document.getElementById('subjectRankingList');
  if(!listContainer) return;
  
  const d = new Date();
  d.setDate(d.getDate() - 7);
  const sevenDaysAgoStr = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;

  const recentSessions = sessions.filter(s => s.date >= sevenDaysAgoStr);
  
  const subjMap = {};
  recentSessions.forEach(s => {
    let mainSubject = s.subject;
    if (mainSubject && mainSubject.includes(' - ')) {
        mainSubject = mainSubject.split(' - ')[0];
    }
    subjMap[mainSubject] = (subjMap[mainSubject] || 0) + s.minutes;
  });

  const sorted = Object.entries(subjMap).sort((a,b) => b[1] - a[1]);
  
  if (sorted.length === 0) {
    listContainer.innerHTML = '<div style="color:var(--text-dim); font-size:0.9rem;">No study in the last 7 days.</div>';
    return;
  }

  listContainer.innerHTML = sorted.map((entry, index) => {
    const name = entry[0];
    const mins = entry[1];
    const safeName = escapeHTML(name);
    
    let badgeClass = 'normal';
    let badgeIcon = `${index + 1}`;
    
    if (index === 0) { badgeClass = 'rank-1'; badgeIcon = '👑'; }
    else if (index === 1) { badgeClass = 'silver'; badgeIcon = '🥈'; }
    else if (index === 2) { badgeClass = 'bronze'; badgeIcon = '🥉'; }

    return `
      <div class="subj-rank-item">
        <div class="badge ${badgeClass}">${badgeIcon}</div>
        <div class="rank-subj-name">${safeName}</div>
        <div class="rank-subj-time">${Number(mins) || 0} mins</div>
      </div>
    `;
  }).join('');
}