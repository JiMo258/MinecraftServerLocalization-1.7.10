package ru.glitchless.serverlocalization.utils;

import javax.annotation.Nullable;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Http communication utilities
 */
public final class HttpUtils {
    /**
     * Executes a simple HTTP-GET request
     *
     * @param url URL to request
     * @return The result of request
     * @throws Exception I/O Exception or HTTP errors
     */
    public static String httpGet(String url) throws Exception {
        URL u = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) u.openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        InputStream is = connection.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        StringBuilder response = new StringBuilder();
        while ((line = br.readLine()) != null) {
            response = response.append(line).append('\r');
        }
        connection.disconnect();
        return response.toString();
    }

    @Nullable
    public static File downloadFile(String url, File dest) {
        URL urlObj;
        InputStream is = null;
        HttpURLConnection connection = null;
        try {
            dest.getParentFile().mkdirs();
            dest.createNewFile();
            urlObj = new URL(url);
            connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            is = connection.getInputStream();

            FileOutputStream outputStream = new FileOutputStream(dest);
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            long totalBytes = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            outputStream.flush();
            outputStream.close();
            System.out.println("[serverlocalization] Downloaded " + totalBytes + " bytes to " + dest.getAbsolutePath());
            System.out.println("[serverlocalization] Final file size: " + dest.length() + " bytes");
        } catch (IOException mue) {
            mue.printStackTrace();
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException ioe) {
                // Ignore
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        return dest;
    }
}
