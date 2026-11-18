const crypto = require("node:crypto");

function json(res, status, payload) {
  res.setHeader("Content-Type", "application/json");
  res.status(status).send(JSON.stringify(payload));
}
function validE164(value) {
  return typeof value === "string" && /^\+[1-9]\d{7,14}$/.test(value);
}
function authorized(req) {
  const expected = process.env.NOTIFNIX_API_TOKEN || "";
  const supplied = (req.headers.authorization || "").replace(/^Bearer\s+/i, "");
  if (!expected || !supplied) return false;
  const a = Buffer.from(expected);
  const b = Buffer.from(supplied);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function requirePost(req, res) {
  if (req.method !== "POST") { json(res, 405, { error: "Method not allowed" }); return false; }
  if (!authorized(req)) { json(res, 401, { error: "Unauthorized. Configure the Notifnix API token." }); return false; }
  return true;
}
function credentials() {
  const sid = process.env.TWILIO_ACCOUNT_SID || "";
  const token = process.env.TWILIO_AUTH_TOKEN || "";
  if (!/^AC[0-9a-fA-F]{32}$/.test(sid) || !token) throw new Error("Twilio credentials are not configured on the backend.");
  return { sid, token };
}
async function twilioPost(path, params) {
  const { sid, token } = credentials();
  const response = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/${path}`, {
    method: "POST",
    headers: {
      Authorization: "Basic " + Buffer.from(`${sid}:${token}`).toString("base64"),
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: new URLSearchParams(params)
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.message || `Twilio request failed (${response.status})`);
    error.providerCode = data.code || null;
    error.status = response.status >= 500 ? 502 : 400;
    throw error;
  }
  return data;
}
async function twilioGet(path) {
  const { sid, token } = credentials();
  const response = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/${path}`, {
    method: "GET",
    headers: { Authorization: "Basic " + Buffer.from(`${sid}:${token}`).toString("base64") }
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.message || `Twilio request failed (${response.status})`);
    error.status = response.status >= 500 ? 502 : 400;
    throw error;
  }
  return data;
}
module.exports = { json, validE164, requirePost, twilioPost, twilioGet };
