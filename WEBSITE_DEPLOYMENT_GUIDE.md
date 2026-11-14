# Website & Admin Dashboard Deployment Guide

## Files
- Public Website: `web/public/index.html`, `web/public/styles.css`, `web/public/app.js`
- Admin Dashboard: `web/public/admin/index.html`, `web/public/admin/admin.js`
- Hosting Config: `firebase.json` (`"hosting": { "public": "web/public" }`)
- Automated GitHub Action: `.github/workflows/deploy-web.yml`

## Option 1 — Deploy Public Website + Admin Dashboard via Firebase Hosting CLI
```bash
firebase login
firebase use gen-lang-client-0099902352
firebase deploy --only hosting
```
Expected live URLs once deployed:
- Public Website: `https://gen-lang-client-0099902352.web.app`
- Admin Dashboard: `https://gen-lang-client-0099902352.web.app/admin/`

## Option 2 — Automatic GitHub Actions Deployment
1. In Google Cloud / Firebase Console, create a Firebase Hosting Service Account JSON.
2. Save it in GitHub Repository &rarr; **Settings &rarr; Secrets and variables &rarr; Actions** as `FIREBASE_SERVICE_ACCOUNT_GEN_LANG_CLIENT_0099902352`.
3. Push changes under `web/public/` or run `.github/workflows/deploy-web.yml` manually.

## Option 3 — Website Studio Direct Netlify Deploy (Inside Android App)
In **Website Studio** inside LUMIQ AI, generate any HTML5 site and enter your Netlify Personal Access Token to publish directly to a live HTTPS Netlify URL (`https://api.netlify.com/api/v1/sites`).
