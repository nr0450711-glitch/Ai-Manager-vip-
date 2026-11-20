# JARVIS VIP Secure Backend Proxy

For production builds, never embed `OPENAI_API_KEY` inside the Android APK.

## Deploy to Cloud Run
1. Set `OPENAI_API_KEY` in Cloud Run / Secret Manager.
2. Deploy `/backend`:
   ```bash
   gcloud run deploy jarvis-vip-proxy --source . --region asia-south1 --allow-unauthenticated
   ```
   *(All `/v1/chat` requests verify the caller's Firebase Auth ID Token via `firebase-admin`.)*
3. Open **JARVIS VIP -> Settings -> Secure Backend Proxy URL** and paste your HTTPS Cloud Run URL.
