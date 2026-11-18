const { json, requirePost, twilioGet } = require("../lib/twilio");

module.exports = async function handler(req, res) {
  if (!requirePost(req, res)) return;
  try {
    const { sid, kind } = req.body || {};
    if (kind === "sms") {
      if (typeof sid !== "string" || !/^(SM|MM)[0-9a-fA-F]{32}$/.test(sid)) {
        return json(res, 400, { error: "A valid Twilio message SID is required." });
      }
      const item = await twilioGet(`Messages/${sid}.json`);
      return json(res, 200, {
        kind: "sms", sid: item.sid, status: item.status,
        errorCode: item.error_code || null, errorMessage: item.error_message || null,
        dateSent: item.date_sent || null, to: item.to
      });
    }
    if (kind === "call") {
      if (typeof sid !== "string" || !/^CA[0-9a-fA-F]{32}$/.test(sid)) {
        return json(res, 400, { error: "A valid Twilio call SID is required." });
      }
      const item = await twilioGet(`Calls/${sid}.json`);
      return json(res, 200, {
        kind: "call", sid: item.sid, status: item.status,
        duration: item.duration || null, errorCode: item.error_code || null,
        to: item.to, startTime: item.start_time || null, endTime: item.end_time || null
      });
    }
    return json(res, 400, { error: "kind must be sms or call." });
  } catch (error) {
    return json(res, error.status || 502, { error: error.message || "Status lookup failed." });
  }
};
