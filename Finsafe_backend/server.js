// finsafe-backend/server.js
const express = require("express");
const cors = require("cors");
const mongoose = require("mongoose");

const app = express();
const PORT = 4000;

// ✅ MongoDB Connection
mongoose.connect(
  "mongodb+srv://Pradyumna_Arakeri:Prad1406@cluster0.drqqnu0.mongodb.net/?appName=Cluster0")
.then(() => console.log("✅ Connected to MongoDB Atlas"))
.catch((err) => console.error("❌ MongoDB Atlas connection error:", err));



// ✅ Middleware
app.use(cors());
app.use(express.json());

// ✅ Schema
const reportSchema = new mongoose.Schema({
  url: { type: String, required: true, unique: true },
  count: { type: Number, default: 0 },
});

const Report = mongoose.model("Report", reportSchema);

// ✅ Helper: normalize URLs
function normalize(url) {
  if (!url) return "";
  let s = url.trim().toLowerCase();
  if (s.endsWith("/")) s = s.slice(0, -1);
  return s;
}

// ✅ POST /report → increment or create
app.post("/report", async (req, res) => {
  try {
    const { url } = req.body;
    if (!url) return res.status(400).json({ error: "url required" });

    const key = normalize(url);
    let record = await Report.findOne({ url: key });

    if (!record) {
      record = new Report({ url: key, count: 1 });
    } else {
      record.count += 1;
    }

    await record.save();
    console.log(`🔹 Reported: ${key} (${record.count} times)`);

    res.json({ url: key, count: record.count });
  } catch (err) {
    console.error("❌ Error in POST /report:", err);
    res.status(500).json({ error: "server error" });
  }
});

// ✅ GET /report?url=<url>
app.get("/report", async (req, res) => {
  try {
    const { url } = req.query;
    if (!url) return res.status(400).json({ error: "url required" });

    const key = normalize(url);
    const record = await Report.findOne({ url: key });

    res.json({ url: key, count: record ? record.count : 0 });
  } catch (err) {
    console.error("❌ Error in GET /report:", err);
    res.status(500).json({ error: "server error" });
  }
});

// ✅ GET /all → list all reports
app.get("/all", async (req, res) => {
  try {
    const allReports = await Report.find({}, { _id: 0, __v: 0 });
    res.json(allReports);
  } catch (err) {
    console.error("❌ Error in GET /all:", err);
    res.status(500).json({ error: "server error" });
  }
});

// ✅ Fallback
app.use((req, res) => {
  res.status(404).send(`No route for ${req.method} ${req.url}`);
});

// ✅ Start server
app.listen(PORT, () => console.log(`🚀 Server running at http://localhost:${PORT}`));
