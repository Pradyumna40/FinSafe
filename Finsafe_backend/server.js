// finsafe-backend/server.js
const express = require("express");
const fs = require("fs-extra");
const cors = require("cors");

const app = express();
const PORT = 4000;
const DATA_FILE = "./reports.json";

// --- Middlewares ---
app.use(cors());              // allow requests from Android / emulator
app.use(express.json());      // parse JSON request body

// --- Helper functions ---
async function loadReports() {
  // Create file if missing
  if (!(await fs.pathExists(DATA_FILE))) await fs.writeJson(DATA_FILE, {});
  return fs.readJson(DATA_FILE);
}

async function saveReports(data) {
  await fs.writeJson(DATA_FILE, data, { spaces: 2 });
}

function normalize(url) {
  if (!url) return "";
  let s = url.trim().toLowerCase();
  if (s.endsWith("/")) s = s.slice(0, -1);
  return s;
}

// --- Routes ---

//  POST /report → increment report count for URL
app.post("/report", async (req, res) => {
  try {
    const { url } = req.body;

    if (!url) {
      return res.status(400).json({ error: "url required" });
    }

    const data = await loadReports();
    const key = normalize(url);

    if (!data[key]) data[key] = 0;
    data[key]++;

    await saveReports(data);

    console.log(`Reported: ${key} (${data[key]} times)`);

    res.json({ url: key, count: data[key] });
  } catch (err) {
    console.error("Error in POST /report:", err);
    res.status(500).json({ error: "server error" });
  }
});

//  GET /report?url=<url> → get report count for a single URL
app.get("/report", async (req, res) => {
  try {
    const { url } = req.query;
    if (!url) {
      return res.status(400).json({ error: "url query param required" });
    }

    const data = await loadReports();
    const key = normalize(url);

    res.json({ url: key, count: data[key] || 0 });
  } catch (err) {
    console.error("Error in GET /report:", err);
    res.status(500).json({ error: "server error" });
  }
});

//  GET /all → view all reported links
app.get("/all", async (req, res) => {
  try {
    const data = await loadReports();
    res.json(data);
  } catch (err) {
    console.error("Error in GET /all:", err);
    res.status(500).json({ error: "server error" });
  }
});

//  Fallback for undefined routes (helps debugging)
app.use((req, res) => {
  res.status(404).send(`No route for ${req.method} ${req.url}`);
});

// --- Start server ---
app.listen(PORT, () => {
  console.log(`✅ Server running at http://localhost:${PORT}`);
});
