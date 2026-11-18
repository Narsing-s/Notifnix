const { json } = require("./_lib/twilio");

module.exports = function handler(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "Method not allowed" });
  const ready = Boolean(
    process.env.NOTIFNIX_API_TOKEN &&
    process.env.TWILIO_ACCOUNT_SID &&
    process.env.TWILIO_AUTH_TOKEN &&
    (process.env.TWILIO_MESSAGING_SERVICE_SID || process.env.TWILIO_FROM_NUMBER)
  );
  return json(res, 200, {
    provider: "twilio",
    configured: ready,
    smsConfigured: Boolean(process.env.TWILIO_MESSAGING_SERVICE_SID || process.env.TWILIO_FROM_NUMBER),
    voiceConfigured: Boolean(process.env.TWILIO_FROM_NUMBER),
    message: ready ? "Provider configuration detected. No test message was sent." : "Backend or Twilio environment variables are incomplete."
  });
};
