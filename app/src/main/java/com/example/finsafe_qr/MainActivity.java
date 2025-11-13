package com.example.finsafe_qr;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {

    private static final String BACKEND_BASE = "https://finsafe.onrender.com";

    Button btnScan;
    TextView txtResult;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnScan = findViewById(R.id.btnScan);
        txtResult = findViewById(R.id.txtResult);
        Button btnCheckLink = findViewById(R.id.btnCheckLink);
        EditText editLink = findViewById(R.id.editLink);

        // 🔍 QR Scan Button
        btnScan.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Scan a QR Code");
            options.setOrientationLocked(true);
            options.setBeepEnabled(true);
            options.setBarcodeImageEnabled(true);
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            qrLauncher.launch(options);
        });

        // 🔗 Manual Link Check
        btnCheckLink.setOnClickListener(v -> {
            String url = editLink.getText().toString().trim();
            if (url.isEmpty()) {
                txtResult.setText("Please paste a link to check.");
                return;
            }

            if (url.startsWith("upi://pay")) {
                txtResult.setText(parseUpiData(url));
            } else if (looksLikeUrl(url)) {

                boolean ruleSuspicious = isSuspiciousLink(url);
                double mlScore = MLClassifier.predictProbability(url);
                boolean mlSuspicious = mlScore >= 0.5;
                boolean suspicious = ruleSuspicious || mlSuspicious;

                String message = (suspicious ? "⚠️ Suspicious Link!\n" : "✅ Safe Link\n")
                        + url + "\n\nML Score: " + String.format("%.2f", mlScore);

                txtResult.setText(message);
                fetchReportCount(url);

                // ⚠️ FIXED → Report only for suspicious links
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setTitle(suspicious ? "Suspicious Link" : "Open Link?")
                        .setMessage(message + "\n\nDo you want to open it?");

                // Always show OPEN
                builder.setPositiveButton("Open", (d, w) -> {
                    String urlToOpen = url.startsWith("http") ? url : "https://" + url;
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)));
                });

                // REPORT only if dangerous
                if (suspicious) {
                    builder.setNeutralButton("Report", (d, w) -> {
                        reportToServer(url);
                        fetchReportCount(url);
                    });
                }

                // Cancel always present
                builder.setNegativeButton("Cancel",
                        (d, w) -> txtResult.setText("Action cancelled."));

                builder.show();

            } else {
                txtResult.setText("Not a valid UPI or web URL:\n" + url);
            }
        });

        // 🌐 Fraud info page button
        Button btnFraud = findViewById(R.id.btnOpenFraud);
        btnFraud.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FraudWebActivity.class))
        );
    }

    // 🧾 UPI Parsing
    private String parseUpiData(String upiString) {
        StringBuilder parsed = new StringBuilder("UPI QR Detected :\n\n");
        String[] parts = upiString.split("\\?");
        if (parts.length > 1) {
            String[] params = parts[1].split("&");
            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0];
                    String value = keyValue[1];
                    switch (key) {
                        case "pa":
                            parsed.append("VPA: ").append(value).append("\n");
                            break;
                        case "pn":
                            parsed.append("Name: ").append(value.replace("%20", " ")).append("\n");
                            break;
                        case "am":
                            parsed.append("Amount: ").append(value).append("\n");
                            break;
                        case "tn":
                            parsed.append("Note: ").append(value.replace("%20", " ")).append("\n");
                            break;
                        default:
                            parsed.append(key).append(": ").append(value).append("\n");
                    }
                }
            }
        }
        return parsed.toString();
    }

    // 🚨 Suspicious Detection Rules
    private boolean isSuspiciousLink(String url) {
        url = url.toLowerCase();
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();

            if (host == null) return true;
            if (url.startsWith("http://")) return true;

            String[] badTlds = {".tk", ".xyz", ".top", ".zip", ".info", ".cf", ".ga", ".gq", ".ml"};
            for (String tld : badTlds)
                if (host.endsWith(tld)) return true;

            if (url.length() > 150) return true;
            if (host.chars().filter(ch -> ch == '-').count() > 3) return true;
            if (host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return true;

            String[] badWords = {"free", "login", "verify", "update", "secure",
                    "banking", "gift", "win", "bonus"};
            for (String word : badWords)
                if (url.contains(word)) return true;

            if (host.split("\\.").length > 4) return true;
            if (url.contains("%")) return true;
            if (url.contains("@")) return true;

        } catch (Exception e) {
            return true;
        }
        return false;
    }

    // 📷 QR Launcher
    @SuppressLint("SetTextI18n")
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) {
                    Toast.makeText(this, "⚠️ QR Scan Failed.", Toast.LENGTH_LONG).show();
                    return;
                }

                String scannedData = result.getContents().trim();

                if (scannedData.startsWith("upi://pay")) {
                    txtResult.setText(parseUpiData(scannedData));
                    return;
                }

                if (looksLikeUrl(scannedData)) {

                    boolean ruleSuspicious = isSuspiciousLink(scannedData);
                    double mlScore = MLClassifier.predictProbability(scannedData);
                    boolean mlSuspicious = mlScore >= 0.5;
                    boolean suspicious = ruleSuspicious || mlSuspicious;

                    String message = (suspicious ? "⚠️ Suspicious Link!\n" : "✅ Safe Link\n")
                            + scannedData + "\n\nML Score: " + String.format("%.2f", mlScore);

                    txtResult.setText(message);
                    fetchReportCount(scannedData);

                    // ⚠️ FIXED REPORT BUTTON HERE ALSO
                    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                            .setTitle(suspicious ? "Suspicious Link" : "Open Link?")
                            .setMessage(message + "\n\nDo you want to open it?");

                    builder.setPositiveButton("Open", (d, w) -> {
                        String urlToOpen = scannedData.startsWith("http") ? scannedData :
                                "https://" + scannedData;
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)));
                    });

                    if (suspicious) {
                        builder.setNeutralButton("Report", (d, w) -> {
                            reportToServer(scannedData);
                            fetchReportCount(scannedData);
                        });
                    }

                    builder.setNegativeButton("Cancel",
                            (d, w) -> txtResult.setText("Link scan cancelled."));

                    builder.show();
                } else {
                    txtResult.setText("Scanned Text:\n\n" + scannedData);
                }
            });

    // 🌐 URL Pattern Check
    private boolean looksLikeUrl(String data) {
        data = data.toLowerCase();
        if (data.startsWith("http://") || data.startsWith("https://")) return true;
        if (data.contains(".") && !data.contains(" ")) return true;
        String[] shorteners = {"bit.ly", "tinyurl.com", "goo.gl", "t.co"};
        for (String s : shorteners)
            if (data.contains(s)) return true;
        return false;
    }

    // 🚀 POST /report
    private void reportToServer(String urlToReport) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL apiUrl = new URL(BACKEND_BASE + "/report");
                conn = (HttpURLConnection) apiUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                String jsonBody = "{\"url\":\"" + urlToReport + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes());
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (code == 200)
                        Toast.makeText(this, "Reported successfully!", Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(this, "Report failed (code " + code + ")", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Report failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 🔁 GET /report
    private void fetchReportCount(String urlToCheck) {
        new Thread(() -> {
            try {
                String endpoint = BACKEND_BASE + "/report?url=" +
                        URLEncoder.encode(urlToCheck, "UTF-8");

                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("GET");

                Scanner sc = new Scanner(conn.getInputStream()).useDelimiter("\\A");
                String response = sc.hasNext() ? sc.next() : "";

                int count = 0;
                int idx = response.indexOf("\"count\":");
                if (idx != -1) {
                    String num = response.substring(idx + 8).replaceAll("[^0-9]", "");
                    if (!num.isEmpty()) count = Integer.parseInt(num);
                }

                int finalCount = count;
                runOnUiThread(() -> {
                    String text = txtResult.getText().toString();
                    text = text.replaceAll("\\n?\\n?🧾.*", "");

                    if (finalCount == 0)
                        text += "\n\n🧾 No reports yet";
                    else if (finalCount == 1)
                        text += "\n\n🧾 Reported 1 time";
                    else
                        text += "\n\n🧾 Reported " + finalCount + " times";

                    txtResult.setText(text);
                });

            } catch (Exception ignored) {}
        }).start();
    }
}
