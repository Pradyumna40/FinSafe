package com.example.finsafe_qr;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

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

        // 1. Not using HTTPS
        if (url.startsWith("http://")) return true;

        // 2. Suspicious domains
        if (url.contains(".tk") || url.contains(".xyz") || url.contains(".top") || url.contains(".zip"))
            return true;

        // 3. Long URL
        if (url.length() > 100) return true;

        // 4. Too many hyphens
        if (url.chars().filter(ch -> ch == '-').count() > 3) return true;

        // 5. Numbers in domain
        if (url.matches(".*[0-9].*")) return true;

        // 6. Too many subdomains
        if (url.split("\\.").length > 4) return true;

        // 7. Suspicious keywords
        String[] badWords = {"login", "verify", "secure", "bank", "password", "freegift"};
        for (String word : badWords) {
            if (url.contains(word)) return true;
        }

        // 8. URL encoding
        if (url.contains("%")) return true;

        // 9. @ symbol
        if (url.contains("@")) return true;

        return false; // URL looks safe
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
                    txtResult.setText("Scan cancelled.");
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