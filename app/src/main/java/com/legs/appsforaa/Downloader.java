package com.legs.appsforaa;

import android.os.StrictMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Downloader implements Runnable{


    MainActivity mainActivity;
    File file;
    String url;

    public void setScreen2auto(Boolean screen2auto) {
        this.screen2auto = screen2auto;
    }

    Boolean screen2auto;

    public Downloader(MainActivity mainActivity, File file, String url) {
        this.mainActivity = mainActivity;
        this.file = file;
        this.url = url;
        this.screen2auto = false;
    }



    @Override
    public void run() {
        try {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);

            Request request;

            OkHttpClient client = new OkHttpClient();
            if (this.screen2auto) {
                request = new Request.Builder().url(this.url).addHeader("REFERER", "https://inceptive.ru").build();

            } else {
                request = new Request.Builder().url(this.url).build();
            }

            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download file: " + response);
            }
            FileOutputStream fos = new FileOutputStream(this.file);
            fos.write(response.body().bytes());
            fos.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.mainActivity.installAPK(this.file);

        }
    }

}
