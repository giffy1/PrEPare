package cs.umass.edu.customcalendar.main;

import android.app.Application;
import android.util.Log;

import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

public class PrEPareApplication extends Application implements BootstrapNotifier {
    private static final String TAG = PrEPareApplication.class.getName();

    public void onCreate() {
        super.onCreate();
        BeaconManager beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        Region region = new Region("com.example.backgroundRegion",
                Identifier.parse("2F234454-CF6D-4A0F-ADF2-F4911BA9FFA6"), null, null);
        RegionBootstrap regionBootstrap = new RegionBootstrap(this, region);
    }

    @Override
    public void didEnterRegion(Region arg0) {
        Log.d(TAG, "Detected Metawear beacon.");
//        Intent intent = new Intent(this, WearableService.class);
//        intent.setAction(Constants.ACTION.START_SERVICE);
//        startService(intent);
    }

    @Override
    public void didExitRegion(Region region) {

    }

    @Override
    public void didDetermineStateForRegion(int i, Region region) {

    }

    // TODO : This may be a good place to change beacon scanning parameters or manage other services
    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }
}