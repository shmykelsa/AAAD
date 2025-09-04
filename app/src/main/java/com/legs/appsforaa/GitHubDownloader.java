package com.legs.appsforaa;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitHubDownloader implements Runnable {

    private static final String TAG = "GitHubDownloader";
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REDIRECTS = 5;
    
    MainActivity mainActivity;
    File file;
    String url;
    Boolean screen2auto;

    public GitHubDownloader(MainActivity mainActivity, File file, String url) {
        this.mainActivity = mainActivity;
        this.file = file;
        this.url = url;
        this.screen2auto = false;
    }

    public void setScreen2auto(Boolean screen2auto) {
        this.screen2auto = screen2auto;
    }

    @Override
    public void run() {
        try {
            Log.d(TAG, "Starting download from: " + url);
            boolean success = downloadFile(url, file, 0);
            
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                // Always dismiss progress dialog first
                mainActivity.dismissCurrentProgressDialog();
                
                if (success) {
                    Log.d(TAG, "Download completed successfully");
                    // Install the APK
                    mainActivity.installAPK(file);
                } else {
                    Log.e(TAG, "Download failed");
                    android.widget.Toast.makeText(mainActivity, 
                        "Download failed. Please try again.", 
                        android.widget.Toast.LENGTH_LONG).show();
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Download exception", e);
            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                mainActivity.dismissCurrentProgressDialog();
                android.widget.Toast.makeText(mainActivity, 
                    "Download error: " + e.getMessage(), 
                    android.widget.Toast.LENGTH_LONG).show();
            });
        }
    }

    // Convenience method to start download on background thread
    public void startDownload() {
        Thread downloadThread = new Thread(this);
        downloadThread.start();
    }

    private boolean downloadFile(String urlString, File outputFile, int redirectCount) {
        if (redirectCount > MAX_REDIRECTS) {
            Log.e(TAG, "Too many redirects");
            return false;
        }

        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            
            // Set proper headers for GitHub
            connection.setRequestProperty("User-Agent", "AAAD-Android-App/1.5");
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false); // Handle redirects manually
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);

            // Handle redirects
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308) {
                
                String newUrl = connection.getHeaderField("Location");
                if (newUrl != null) {
                    Log.d(TAG, "Redirecting to: " + newUrl);
                    connection.disconnect();
                    return downloadFile(newUrl, outputFile, redirectCount + 1);
                }
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned HTTP " + responseCode + " " + connection.getResponseMessage());
                return false;
            }

            // Get file size (may be -1 if not provided)
            long fileSize = connection.getContentLengthLong();
            Log.d(TAG, "File size: " + (fileSize > 0 ? fileSize + " bytes" : "unknown"));

            inputStream = new BufferedInputStream(connection.getInputStream());
            outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[BUFFER_SIZE];
            long totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                // Log progress occasionally
                if (totalBytesRead % (BUFFER_SIZE * 100) == 0) {
                    Log.d(TAG, "Downloaded: " + totalBytesRead + " bytes");
                }
            }

            outputStream.flush();
            Log.d(TAG, "Download completed. Total bytes: " + totalBytesRead);
            
            // Verify file was created and has content
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "File verified: " + outputFile.length() + " bytes");
                return true;
            } else {
                Log.e(TAG, "Downloaded file is empty or doesn't exist");
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "IOException during download", e);
            return false;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing resources", e);
            }
        }
    }
}