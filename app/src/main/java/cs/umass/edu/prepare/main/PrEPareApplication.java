package cs.umass.edu.prepare.main;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import cs.umass.edu.prepare.constants.Constants;
import cs.umass.edu.prepare.services.WearableService;

public class PrEPareApplication extends Application {
    private static final String TAG = PrEPareApplication.class.getName();

    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate()");
    }

    // TODO : This may be a good place to change beacon scanning parameters or manage other services
    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }
}