package cs.umass.edu.prepare.main;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;

import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.util.Utils;

public class CheckForUpdatesTask extends AsyncTask<Boolean, Void, String> {

    private final Context context;

    private boolean onStart;

    private boolean updateWearable;

    public CheckForUpdatesTask(Context context){
        this.context = context;
    }

    @Override
    protected String doInBackground(Boolean... flags) {
        if (flags.length > 0) {
            this.onStart = flags[0];
        } else {
            this.onStart = false;
        }
        if (flags.length > 1){
            this.updateWearable = flags[1];
        } else {
            this.updateWearable = false;
        }
        if (this.updateWearable){
            return null;
        }else {
            return downloadText(getVersionFile());
        }
    }

    @Override
    protected void onPostExecute(String newestVersion) {
        if (updateWearable){
            String description = "The application is not installed on your wearable.";
            new AlertDialog.Builder(new ContextThemeWrapper(context, android.R.style.Theme_Dialog))
                    .setTitle("Update Wearable?")
                    .setMessage(description + " Would you like to install the latest updates?")
                    .setPositiveButton("Right away!", (dialog, which) -> {
                        Intent updateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getApkFile()));
                        context.startActivity(updateIntent);
                    })
                    .setNegativeButton("Later", (dialog, which) -> {
                        // do nothing
                    })
                    .setIcon(R.drawable.ic_file_download_white_24dp)
                    .show();
            return;
        }

        String currentVersion = Utils.getVersionName(context);
        Log.i("PrEPare", currentVersion + ", " + newestVersion);
        final String description;
        if (currentVersion == null) {
            description = String.format(Locale.getDefault(), "Failed to get application version. We recommend installing version %s", newestVersion);
        } else if (newestVersion == null) {
            if (!onStart)
                Toast.makeText(context, "Failed to get newest version. Make sure you have a working network connection.", Toast.LENGTH_LONG).show();
            return;
        } else if (newestVersion.equals(currentVersion)){
            if (!onStart)
                Toast.makeText(context, "You are already running the latest version.", Toast.LENGTH_LONG).show();
            return;
        } else {
            description = String.format(Locale.getDefault(), "Version %s is available.", newestVersion);
        }
        new AlertDialog.Builder(new ContextThemeWrapper(context, android.R.style.Theme_Dialog))
                .setTitle("Update?")
                .setMessage(description + " Would you like to install the latest updates?")
                .setPositiveButton("Right away!", (dialog, which) -> {
                    Intent updateIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getApkFile()));
                    context.startActivity(updateIntent);
                })
                .setNegativeButton("Later", (dialog, which) -> {
                    // do nothing
                })
                .setIcon(R.drawable.ic_file_download_white_24dp)
                .show();
    }

    /**
     * Downloads the text at the given URL.
     * @param url a public URL.
     * @return the text at that URL, null if an {@link IOException} occurs.
     */
    private String downloadText(String url) {
        int BUFFER_SIZE = 2000;
        final InputStream in;
        try {
            in = openHttpConnection(url);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        String str = "";
        if (in != null) {
            InputStreamReader isr = new InputStreamReader(in);
            int charRead;
            char[] inputBuffer = new char[BUFFER_SIZE];
            try {
                while ((charRead = isr.read(inputBuffer)) > 0) {
                    // ---convert the chars to a String---
                    String readString = String.copyValueOf(inputBuffer, 0, charRead);
                    str += readString;
                    inputBuffer = new char[BUFFER_SIZE];
                }
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return str;
    }

    /**
     * Returns the url linking the up-to-date mobile .apk version file
     * @return url
     */
    private String getVersionFile(){
        return context.getString(R.string.url_mobile_version);
    }

    /**
     * Returns the url linking the up-to-date mobile .apk file
     * @return url
     */
    private String getApkFile(){
        return context.getString(R.string.url_mobile_apk_shortened);
    }

    /**
     * Opens an HTTP connection with the given URL
     * @param urlString public url
     * @return an input stream for reading data at the url
     * @throws IOException if the input stream cannot be established/opened
     */
    private InputStream openHttpConnection(String urlString) throws IOException {
        InputStream in = null;
        int response;

        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();

        if (!(conn instanceof HttpURLConnection))
            throw new IOException("Not an HTTP connection");

        HttpURLConnection httpConn = (HttpURLConnection) conn;
        httpConn.setAllowUserInteraction(false);
        httpConn.setInstanceFollowRedirects(true);
        httpConn.setRequestMethod("GET");
        httpConn.connect();

        response = httpConn.getResponseCode();
        if (response == HttpURLConnection.HTTP_OK) {
            in = httpConn.getInputStream();
        }

        return in;
    }
}
