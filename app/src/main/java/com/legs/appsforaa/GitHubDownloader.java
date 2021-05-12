package com.legs.appsforaa;

import android.os.StrictMode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class GitHubDownloader implements Runnable{


    MainActivity mainActivity;
    File file;
    String url;

    public void setScreen2auto(Boolean screen2auto) {
        this.screen2auto = screen2auto;
    }

    Boolean screen2auto;

    public GitHubDownloader(MainActivity mainActivity, File file, String url) {
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

            URL u = new URL (this.url);
            URLConnection conn = u.openConnection();

            int contentLenght = conn.getContentLength();

            DataInputStream stream = new DataInputStream(u.openStream());

            byte[] buffer = new byte[contentLenght];
            stream.readFully(buffer);
            stream.close();

            DataOutputStream fos = new DataOutputStream(new FileOutputStream(this.file));
            fos.write(buffer);
            fos.flush();
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.mainActivity.installAPK(this.file);
        }
    }

}
