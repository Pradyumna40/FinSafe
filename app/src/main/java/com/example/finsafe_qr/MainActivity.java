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

public class MainActivity extends AppCompatActivity {

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

        btnScan.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setOrientationLocked(true);
            options.setPrompt("Scan a QR Code");
            options.setCameraId(0);  // use back camera
            options.setBeepEnabled(true);
            options.setBarcodeImageEnabled(true);
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);


            options.addExtra("SCAN_INVERTED" , true);

            qrLauncher.launch(options);
        });
        //check link button
        btnCheckLink.setOnClickListener(v -> {
            String url = editLink.getText().toString().trim();
            if(url.isEmpty()) {
                txtResult.setText("Please paste a link to check.");
                return;
            }

            if(url.startsWith("upi://pay")) {
                txtResult.setText(parseUpiData(url));
            } else if(looksLikeUrl(url)) {
                boolean suspicious = isSuspiciousLink(url);
                String message = (suspicious ? "⚠️ Suspicious Link!\n" : "✅ Safe Link\n") + url;

                txtResult.setText(message);
                new AlertDialog.Builder(this)
                        .setTitle(suspicious ? "Suspicious Link" : "Open Link?")
                        .setMessage(message + "\n\nDo you want to open it?")
                        .setPositiveButton("Open", (d, w) -> {
                            String urlToOpen = url.startsWith("http") ? url : "https://" + url;
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)));
                        })
                        .setNegativeButton("Cancel", (d, w) -> txtResult.setText("Link check cancelled."))
                        .show();
            } else {
                txtResult.setText("Not a valid UPI or web URL:\n" + url);
            }
        });

    }



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

                        default:
                            parsed.append(key).append(": ").append(value).append("\n");
                    }
                }
            }
        }

        return parsed.toString();
    }


    private boolean isSuspiciousLink(String url) {
        url = url.toLowerCase();

        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();

            if (host == null) return true; // invalid URL

            // 1. Not using HTTPS
            if (url.startsWith("http://")) return true;

            // 2. Suspicious / cheap TLDs
            String[] badTlds = {".tk", ".xyz", ".top", ".zip", ".info", ".cf", ".ga", ".gq", ".ml"};
            for (String tld : badTlds) {
                if (host.endsWith(tld)) return true;
            }

            // 3. Unusually long URL
            if (url.length() > 150) return true;

            // 4. Too many hyphens in domain name
            if (host.chars().filter(ch -> ch == '-').count() > 3) return true;

            // 5. IP address instead of domain
            if (host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) return true;

            // 6. Suspicious keywords
            String[] badWords = {"free", "login", "verify", "update", "secure", "banking", "gift", "win", "bonus"};
            for (String word : badWords) {
                if (url.contains(word)) return true;
            }

            // 7. Too many subdomains (like phishing.bankofindia.secure-login.com)
            if (host.split("\\.").length > 4) return true;

            // 8. URL encoding (often used in phishing)
            if (url.contains("%")) return true;

            // 9. @ symbol (phishers use it to hide real URL)
            if (url.contains("@")) return true;

        } catch (Exception e) {
            return true; // if parsing fails, treat as suspicious
        }

        return false; // looks safe
    }




    @SuppressLint("SetTextI18n")
    private final androidx.activity.result.ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    String scannedData = result.getContents().trim();

                    // 1️⃣ UPI QR
                    if (scannedData.startsWith("upi://pay")) {
                        txtResult.setText(parseUpiData(scannedData));

                    }
                    // 2️⃣ Looks like URL
                    else if (looksLikeUrl(scannedData)) {
                        boolean suspicious = isSuspiciousLink(scannedData);

                        String message = (suspicious ? "⚠️ Suspicious Link!\n" : "✅ Safe Link\n") + scannedData;

                        txtResult.setText(message);

                        new AlertDialog.Builder(this)
                                .setTitle(suspicious ? "Suspicious Link" : "Open Link?")
                                .setMessage(message + "\n\nDo you want to open it?")
                                .setPositiveButton("Open", (d, w) -> {
                                    String urlToOpen = scannedData.startsWith("http") ? scannedData : "https://" + scannedData;
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen)));
                                })
                                .setNegativeButton("Cancel", (d, w) -> txtResult.setText("Link scan cancelled."))
                                .show();
                    }
                    // 3️⃣ Plain text / other data
                    else {
                        txtResult.setText("Scanned Text:\n\n" + scannedData);
                    }
                } else {
                    String failMessage = "⚠️ QR Scan Failed . Try scanning in light mode.";
                    Toast.makeText(this,failMessage,Toast.LENGTH_LONG).show();
                }
            });

    // Detect if string looks like a URL (even without http)
    private boolean looksLikeUrl(String data) {
        data = data.toLowerCase();
        if (data.startsWith("http://") || data.startsWith("https://")) return true;
        if (data.contains(".") && !data.contains(" ")) return true;

        String[] shorteners = {"bit.ly","tinyurl.com","goo.gl","t.co"};
        for(String s : shorteners){
            if(data.contains(s)) return true;
        }
        return false;
    }

    // Suspicious URL rules
   
}