package com.example.finsafe_qr;
import android.net.Uri;
import android.text.TextUtils;
public class MLClassifier {
    // ⚙️ Example logistic regression weights (you can tune later)
    private static final double[] WEIGHTS = {
            0.008,   // length
            0.5,     // hasHttp
            1.2,     // hasIp
            0.3,     // dotCount
            0.4,     // hyphenCount
            1.0,     // badTldFlag
            0.9,     // percentEncodedFlag
            0.6,     // containsAt
            0.7,     // shortenerFlag
            0.9,     // suspiciousWordsCount
            -0.01,   // digitRatio
            0.0      // reserved
    };
    private static final double BIAS = -2.0;

    // 🔍 Main prediction method (0 to 1 probability)
    public static double predictProbability(String url) {
        double[] x = extractFeatures(url);
        double sum = BIAS;
        for (int i = 0; i < Math.min(x.length, WEIGHTS.length); i++) {
            sum += WEIGHTS[i] * x[i];
        }
        return sigmoid(sum);
    }

    // 🚨 Returns true if likely phishing
    public static boolean predictIsPhishing(String url, double threshold) {
        return predictProbability(url) >= threshold;
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    // 🧠 Feature extractor
    private static double[] extractFeatures(String url) {
        if (url == null) url = "";
        String u = url.toLowerCase();

        double length = u.length();
        boolean hasHttp = u.startsWith("http://");
        boolean hasIp = false;
        String host = null;

        try {
            Uri uri = Uri.parse(u);
            host = uri.getHost();
            if (host == null) {
                Uri u2 = Uri.parse("http://" + u);
                host = u2.getHost();
            }
            if (host != null && host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) hasIp = true;
        } catch (Exception ignored) {}

        int dotCount = TextUtils.isEmpty(host) ? 0 : host.split("\\.").length;
        int hyphenCount = host == null ? 0 : (int) host.chars().filter(ch -> ch == '-').count();

        String[] badTlds = {".tk", ".xyz", ".top", ".zip", ".info", ".cf", ".ga", ".gq", ".ml"};
        boolean badTldFlag = false;
        if (host != null) {
            for (String t : badTlds)
                if (host.endsWith(t)) {
                    badTldFlag = true;
                    break;
                }
        }

        boolean percentEncodedFlag = u.contains("%");
        boolean containsAt = u.contains("@");

        String[] shorteners = {"bit.ly", "tinyurl.com", "goo.gl", "t.co", "ow.ly"};
        boolean shortenerFlag = false;
        for (String s : shorteners)
            if (u.contains(s)) {
                shortenerFlag = true;
                break;
            }

        String[] suspiciousWords = {"free", "login", "verify", "update", "secure", "banking", "gift", "win", "bonus"};
        int suspiciousWordsCount = 0;
        for (String w : suspiciousWords)
            if (u.contains(w)) suspiciousWordsCount++;

        double digitRatio = 0.0;
        if (host != null && host.length() > 0) {
            long digits = host.chars().filter(Character::isDigit).count();
            digitRatio = (double) digits / host.length();
        }

        double normLength = Math.min(length / 200.0, 1.0);
        double normDotCount = Math.min(dotCount / 6.0, 1.0);
        double normHyphenCount = Math.min(hyphenCount / 6.0, 1.0);

        return new double[]{
                normLength, hasHttp ? 1.0 : 0.0, hasIp ? 1.0 : 0.0, normDotCount,
                normHyphenCount, badTldFlag ? 1.0 : 0.0, percentEncodedFlag ? 1.0 : 0.0,
                containsAt ? 1.0 : 0.0, shortenerFlag ? 1.0 : 0.0, suspiciousWordsCount,
                digitRatio, 0.0
        };
    }
}

