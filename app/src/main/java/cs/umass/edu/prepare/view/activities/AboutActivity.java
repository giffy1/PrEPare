package cs.umass.edu.prepare.view.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

import cs.umass.edu.prepare.R;
import cs.umass.edu.prepare.main.CheckForUpdatesTask;
import cs.umass.edu.prepare.util.Utils;

/**
 * Shows general information about the application.
 */
public class AboutActivity extends AppCompatActivity{

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView txtVersion = (TextView) findViewById(R.id.txtVersion);
        txtVersion.setText(String.format(Locale.getDefault(), getString(R.string.about_app_version), Utils.getVersionName(this)));

        View checkForUpdatesButton = findViewById(R.id.btnCheckForUpdates);
        checkForUpdatesButton.setOnClickListener(view -> new CheckForUpdatesTask(AboutActivity.this).execute());

        //when the user clicks the metawear icon, direct them to the main site
        ImageView metawearIcon = (ImageView) findViewById(R.id.metawearIcon);
        metawearIcon.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setData(Uri.parse(getString(R.string.link_mbientlab)));
            startActivity(intent);
        });

    }


}
