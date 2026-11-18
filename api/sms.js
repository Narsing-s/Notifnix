const { json, validE164, requirePost, twilioPost } = require("./_lib/twilio");

module.exports = async function handler(req, res) {
  if (!requirePost(req, res)) return;
  try {
    const { to, body, consent } = req.body || {};
    if (!validE164(to)) return json(res, 400, { error: "Destination must be E.164, e.g. +14155552671." });
    if (typeof body !== "string" || !body.trim() || body.length > 1500) return json(res, 400, { error: "Message must contain 1–1500 characters." });
    if (consent !== true) return json(res, 400, { error: "Confirm that the recipient has agreed to receive this message." });
    const sender = process.env.TWILIO_MESSAGING_SERVICE_SID
      ? { MessagingServiceSid: process.env.TWILIO_MESSAGING_SERVICE_SID }
      : { From: process.env.TWILIO_FROM_NUMBER || "" };
    if (!sender.MessagingServiceSid && !/^\+[1-9]\d{7,14}$/.test(sender.From)) {
      return json(res, 503, { error: "Set TWILIO_MESSAGING_SERVICE_SID or a valid TWILIO_FROM_NUMBER." });
    }
    const message = await twilioPost("Messages.json", { To: to, Body: body.trim(), ...sender });
    return json(res, 202, { accepted: true, provider: "twilio", sid: message.sid, status: message.status || "queued", note: "Accepted by Twilio; delivery is not yet confirmed." });
  } catch (error) {
    return json(res, error.status || 502, { error: error.message || "SMS provider request failed." });
  }
};
