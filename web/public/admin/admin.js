import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.2/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/11.0.2/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  addDoc,
  query,
  orderBy,
  limit,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/11.0.2/firebase-firestore.js";

// Public Firebase client configuration for gen-lang-client-0099902352
const firebaseConfig = {
  apiKey: "AIzaSyBybQmUV1I5TgfJGwcyMp88pmsyohhvsrM",
  authDomain: "gen-lang-client-0099902352.firebaseapp.com",
  projectId: "gen-lang-client-0099902352",
  storageBucket: "gen-lang-client-0099902352.firebasestorage.app",
  appId: "1:750655526285:android:a683df3449e75061a159c4"
};

const NAMED_DB_ID = "ai-studio-android-lumiqai-02bb64ec-3121-4fae-995f-3f44cf6d4a9a";
const BOOTSTRAP_ADMIN_EMAIL = "pranavsmmsupport@gmail.com";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app, NAMED_DB_ID);
const provider = new GoogleAuthProvider();

const el = (id) => document.getElementById(id);
let currentAdminUser = null;
let cachedUsers = [];

el("signInBtn").addEventListener("click", async () => {
  try {
    await signInWithPopup(auth, provider);
  } catch (err) {
    el("authStatusMsg").textContent = "Sign-In failed: " + err.message;
  }
});

el("signOutBtn").addEventListener("click", () => signOut(auth));

onAuthStateChanged(auth, async (user) => {
  currentAdminUser = user;
  if (!user) {
    el("adminIdentity").textContent = "Not signed in";
    el("signInBtn").classList.remove("hidden");
    el("signOutBtn").classList.add("hidden");
    el("authGateCard").classList.remove("hidden");
    el("adminConsole").classList.add("hidden");
    el("authStatusMsg").textContent = "";
    return;
  }

  el("signInBtn").classList.add("hidden");
  el("signOutBtn").classList.remove("hidden");
  el("adminIdentity").textContent = `${user.email} (${user.uid.slice(0, 8)}...)`;

  const authorized = await verifyAdminAccess(user);
  if (!authorized) {
    el("authStatusMsg").textContent = "Access denied: Account is not registered as a LUMIQ LABS administrator in Firestore rules.";
    el("authGateCard").classList.remove("hidden");
    el("adminConsole").classList.add("hidden");
    return;
  }

  el("authGateCard").classList.add("hidden");
  el("adminConsole").classList.remove("hidden");
  await refreshAllAdminData();
});

async function verifyAdminAccess(user) {
  try {
    if (user.emailVerified && user.email === BOOTSTRAP_ADMIN_EMAIL) return true;
    const snap = await getDoc(doc(db, "admins", user.uid));
    return snap.exists();
  } catch (err) {
    el("authStatusMsg").textContent = "Verification error: " + err.message;
    return false;
  }
}

async function writeAudit(action, details, targetUid = "", targetId = "") {
  if (!currentAdminUser) return;
  const payload = {
    actorUid: currentAdminUser.uid,
    action,
    details: String(details).slice(0, 1900),
    createdAt: serverTimestamp()
  };
  if (targetUid) payload.targetUid = targetUid;
  if (targetId) payload.targetId = targetId;
  await addDoc(collection(db, "auditLogs"), payload);
}

async function refreshAllAdminData() {
  await Promise.all([
    loadAppConfig(),
    loadUsers(),
    loadLicenses(),
    loadAnnouncements(),
    loadProjects(),
    loadAuditLogs()
  ]);
}

async function loadAppConfig() {
  const snap = await getDoc(doc(db, "config", "app_status"));
  if (snap.exists()) {
    const d = snap.data();
    el("maintModeCheck").checked = Boolean(d.maintenanceMode);
    el("maintMsgInput").value = d.maintenanceMessage || "";
    el("minVerInput").value = d.minSupportedVersion || "1.0.0";
  }
}

el("saveConfigBtn").addEventListener("click", async () => {
  const maintenanceMode = el("maintModeCheck").checked;
  const maintenanceMessage = el("maintMsgInput").value.trim();
  const minSupportedVersion = el("minVerInput").value.trim() || "1.0.0";
  await setDoc(doc(db, "config", "app_status"), {
    maintenanceMode,
    maintenanceMessage,
    minSupportedVersion,
    updatedAt: serverTimestamp()
  });
  await writeAudit("config_update", `maintenanceMode=${maintenanceMode}, minVer=${minSupportedVersion}`);
  await loadAuditLogs();
  alert("Global App Config saved.");
});

async function loadUsers() {
  const snap = await getDocs(query(collection(db, "users"), limit(100)));
  cachedUsers = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  renderUsersTable();
}

el("userSearchInput").addEventListener("input", renderUsersTable);
el("refreshUsersBtn").addEventListener("click", loadUsers);

function renderUsersTable() {
  const q = el("userSearchInput").value.trim().toLowerCase();
  const rows = cachedUsers.filter((u) => {
    if (!q) return true;
    return (
      (u.publicId || "").toLowerCase().includes(q) ||
      (u.username || "").toLowerCase().includes(q) ||
      (u.userId || "").toLowerCase().includes(q)
    );
  });

  const tbody = el("usersTableBody");
  tbody.innerHTML = "";
  rows.forEach((u) => {
    const tr = document.createElement("tr");
    const statusBadge = u.isBanned
      ? `<span class="badge badge-bad">BANNED</span>`
      : `<span class="badge badge-ok">ACTIVE</span>`;
    tr.innerHTML = `
      <td><code>${escapeHtml(u.publicId || "-")}</code></td>
      <td>${escapeHtml(u.username || "-")}</td>
      <td><span class="badge badge-warn">${escapeHtml((u.vipStatus || "free").toUpperCase())}</span></td>
      <td><code>${escapeHtml(u.deviceId || "Unbound")}</code></td>
      <td>${statusBadge}</td>
      <td class="row"></td>
    `;
    const actionsTd = tr.querySelector("td:last-child");

    const banBtn = makeBtn(u.isBanned ? "Unban" : "Ban", u.isBanned ? "primary" : "danger", async () => {
      await updateDoc(doc(db, "users", u.id), {
        isBanned: !u.isBanned,
        updatedAt: serverTimestamp()
      });
      await writeAudit(u.isBanned ? "user_unban" : "user_ban", `Toggled ban for ${u.publicId}`, u.id);
      await refreshAllAdminData();
    });

    const vipBtn = makeBtn("Grant VIP", "primary", async () => {
      await updateDoc(doc(db, "users", u.id), {
        vipStatus: "vip",
        updatedAt: serverTimestamp()
      });
      await writeAudit("grant_vip", `Granted VIP to ${u.publicId}`, u.id);
      await refreshAllAdminData();
    });

    const unbindBtn = makeBtn("Unbind Device", "", async () => {
      await updateDoc(doc(db, "users", u.id), {
        deviceId: "",
        updatedAt: serverTimestamp()
      });
      await writeAudit("device_unbind", `Cleared deviceId for ${u.publicId}`, u.id);
      await refreshAllAdminData();
    });

    actionsTd.append(banBtn, vipBtn, unbindBtn);
    tbody.appendChild(tr);
  });
}

el("genLicenseBtn").addEventListener("click", async () => {
  const tier = el("licTierSelect").value;
  const durationDays = tier === "lifetime" ? 0 : Math.max(1, parseInt(el("licDaysInput").value || "30", 10));
  const rawCode = el("licCustomCode").value.trim().replace(/[^a-zA-Z0-9_-]/g, "");
  const code = rawCode.length >= 6 ? rawCode : `LUMIQ-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;

  await setDoc(doc(db, "licenses", code), {
    code,
    tier,
    durationDays,
    isActive: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await writeAudit("license_create", `Created ${tier} license ${code} (${durationDays}d)`, "", code);
  el("licResultMsg").textContent = `Created License Key: ${code}`;
  el("licCustomCode").value = "";
  await Promise.all([loadLicenses(), loadAuditLogs()]);
});

async function loadLicenses() {
  const snap = await getDocs(query(collection(db, "licenses"), limit(100)));
  const tbody = el("licensesTableBody");
  tbody.innerHTML = "";
  snap.docs.forEach((d) => {
    const lic = d.data();
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td><code>${escapeHtml(lic.code)}</code></td>
      <td>${escapeHtml((lic.tier || "").toUpperCase())}</td>
      <td>${lic.durationDays}</td>
      <td>${lic.isActive ? '<span class="badge badge-ok">ACTIVE</span>' : '<span class="badge badge-bad">INACTIVE</span>'}</td>
      <td><code>${escapeHtml(lic.redeemedByUid || "-")}</code></td>
      <td></td>
    `;
    const btn = makeBtn("Revoke", "danger", async () => {
      await updateDoc(doc(db, "licenses", d.id), {
        isActive: false,
        updatedAt: serverTimestamp()
      });
      await writeAudit("license_revoke", `Deactivated license ${lic.code}`, "", d.id);
      await Promise.all([loadLicenses(), loadAuditLogs()]);
    });
    tr.querySelector("td:last-child").appendChild(btn);
    tbody.appendChild(tr);
  });
}

el("publishAnnBtn").addEventListener("click", async () => {
  const title = el("annTitleInput").value.trim();
  const body = el("annBodyInput").value.trim();
  const severity = el("annSeveritySelect").value;
  if (!title || !body) return alert("Enter title and body.");

  await addDoc(collection(db, "announcements"), {
    title,
    body,
    severity,
    isActive: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await writeAudit("announcement_publish", `Published "${title}" (${severity})`);
  el("annTitleInput").value = "";
  el("annBodyInput").value = "";
  await Promise.all([loadAnnouncements(), loadAuditLogs()]);
});

async function loadAnnouncements() {
  const snap = await getDocs(query(collection(db, "announcements"), limit(20)));
  const wrap = el("announcementsList");
  wrap.innerHTML = "";
  snap.docs.forEach((d) => {
    const a = d.data();
    const div = document.createElement("div");
    div.style.cssText = "padding:10px;border:1px solid var(--chrome-border);border-radius:12px;margin-bottom:8px;";
    div.innerHTML = `<strong>${escapeHtml(a.title)}</strong> <small>(${escapeHtml(a.severity)})</small><p style="margin:4px 0;">${escapeHtml(a.body)}</p>`;
    const delBtn = makeBtn("Delete", "danger", async () => {
      await deleteDoc(doc(db, "announcements", d.id));
      await writeAudit("announcement_delete", `Deleted "${a.title}"`, "", d.id);
      await Promise.all([loadAnnouncements(), loadAuditLogs()]);
    });
    div.appendChild(delBtn);
    wrap.appendChild(div);
  });
}

async function loadProjects() {
  const snap = await getDocs(query(collection(db, "projects"), limit(40)));
  const wrap = el("projectsList");
  wrap.innerHTML = "";
  snap.docs.forEach((d) => {
    const p = d.data();
    const div = document.createElement("div");
    div.style.cssText = "padding:10px;border:1px solid var(--chrome-border);border-radius:12px;margin-bottom:8px;";
    div.innerHTML = `
      <strong>${escapeHtml(p.name)}</strong> <span class="badge badge-warn">${escapeHtml(p.projectType)}</span>
      <div style="font-size:0.82rem;color:var(--ink-secondary);">Owner: ${escapeHtml(p.ownerPublicId || p.userId)} &bull; Status: ${escapeHtml(p.deploymentStatus)}</div>
    `;
    const blockBtn = makeBtn("Block Project", "danger", async () => {
      await updateDoc(doc(db, "projects", d.id), {
        isModerated: true,
        deploymentStatus: "blocked",
        moderationReason: "Moderated by LUMIQ LABS Admin",
        updatedAt: serverTimestamp()
      });
      await writeAudit("project_block", `Blocked project ${p.name}`, p.userId, d.id);
      await Promise.all([loadProjects(), loadAuditLogs()]);
    });
    div.appendChild(blockBtn);
    wrap.appendChild(div);
  });
}

async function loadAuditLogs() {
  const snap = await getDocs(query(collection(db, "auditLogs"), orderBy("createdAt", "desc"), limit(30)));
  const wrap = el("auditLogsList");
  wrap.innerHTML = "";
  snap.docs.forEach((d) => {
    const l = d.data();
    const p = document.createElement("div");
    p.style.cssText = "padding:6px 0;border-bottom:1px solid var(--chrome-border);font-size:0.86rem;";
    p.innerHTML = `<code>${escapeHtml(l.action)}</code> — ${escapeHtml(l.details)} <small>(Actor: ${escapeHtml((l.actorUid || "").slice(0, 10))})</small>`;
    wrap.appendChild(p);
  });
}

function makeBtn(label, variant, onClick) {
  const btn = document.createElement("button");
  btn.className = `btn-sm ${variant}`;
  btn.textContent = label;
  btn.addEventListener("click", onClick);
  return btn;
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}
