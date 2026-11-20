import express from "express";
import admin from "firebase-admin";

const app = express();
app.use(express.json({ limit: "1mb" }));

if (!admin.apps.length) {
  admin.initializeApp();
}

const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
if (!OPENAI_API_KEY) {
  console.error("Missing OPENAI_API_KEY environment variable.");
  process.exit(1);
}

async function verifyFirebaseUser(req, res, next) {
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    return res.status(401).json({ error: { message: "Missing Firebase ID token" } });
  }
  const idToken = authHeader.slice("Bearer ".length).trim();
  try {
    req.user = await admin.auth().verifyIdToken(idToken);
    return next();
  } catch (err) {
    return res.status(401).json({ error: { message: "Invalid Firebase ID token" } });
  }
}

app.post("/v1/chat", verifyFirebaseUser, async (req, res) => {
  try {
    const response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${OPENAI_API_KEY}`,
      },
      body: JSON.stringify({
        model: req.body.model || "gpt-4o-mini",
        temperature: typeof req.body.temperature === "number" ? req.body.temperature : 0.5,
        response_format: req.body.response_format || { type: "json_object" },
        messages: Array.isArray(req.body.messages) ? req.body.messages : [],
      }),
    });

    const data = await response.json();
    return res.status(response.status).json(data);
  } catch (err) {
    return res.status(502).json({ error: { message: "Upstream OpenAI request failed" } });
  }
});

app.get("/health", (_req, res) => res.json({ status: "ok", service: "jarvis-vip-proxy" }));

const port = process.env.PORT || 8080;
app.listen(port, () => {
  console.log(`JARVIS VIP Secure Proxy listening on port ${port}`);
});
