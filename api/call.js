const { json, validE164, requirePost, twilioPost } = require("../lib/twilio");

module.exports = async function handler(req, res) {
  if (!requirePost(req, res)) return;
  try {
    const { to, consent } = req.body || {};
    if (!validE164(to)) return json(res, 400, { error: "Destination must be E.164, e.g. +14155552671." });
    if (consent !== true) return json(res, 400, { error: "Confirm that you are authorized to call this recipient." });
    const from = process.env.TWILIO_FROM_NUMBER || "";
    if (!/^\+[1-9]\d{7,14}$/.test(from)) return json(res, 503, { error: "Set TWILIO_FROM_NUMBER to your Twilio or verified outgoing caller ID." });
    const twiml = "<Response><Say>Hello. This is a voice call placed using Notifnix. Goodbye.</Say></Response>";
    const call = await twilioPost("Calls.json", { To: to, From: from, Twiml: twiml });
    return json(res, 202, { accepted: true, provider: "twilio", sid: call.sid, status: call.status || "queued", note: "Accepted by Twilio; call connection is not yet confirmed." });
  } catch (error) {
    return json(res, error.status || 502, { error: error.message || "Voice provider request failed." });
  }
};
