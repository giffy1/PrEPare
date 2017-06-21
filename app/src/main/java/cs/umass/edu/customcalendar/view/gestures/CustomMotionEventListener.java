package cs.umass.edu.customcalendar.view.gestures;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * This class handles right and left swipe events for arbitrary views and activities.
 * Register a {@link OnSwipeListener} for each view or activity for which swipe
 * events should be captured. Use {@link #setOnSwipeListener(View, OnSwipeListener)} for
 * views and {@link #setOnSwipeListener(Activity, OnSwipeListener)} for activities.
 */
public class CustomMotionEventListener implements View.OnTouchListener {

    /** Maps each view to a swipe listener. **/
    private final Map<View, OnSwipeListener> viewOnSwipeListeners = new HashMap<>();

    /** Maps each activity to a swipe listener. **/
    private final Map<Activity, OnSwipeListener> activityOnSwipeListeners = new HashMap<>();

    /** The initial position of the swipe. **/
    private float x1;

    /** The minimum distance between the initial and end position of the swipe for the motion event to be considered a swipe. **/
    private static final int MIN_DISTANCE = 150;

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                float x2 = event.getX();
                float deltaX = x2 - x1;
                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    if (x2 > x1) {
                        if (viewOnSwipeListeners.get(view) != null)
                            viewOnSwipeListeners.get(view).onSwipe(OnSwipeListener.Direction.RIGHT);
                    } else {
                        if (viewOnSwipeListeners.get(view) != null)
                            viewOnSwipeListeners.get(view).onSwipe(OnSwipeListener.Direction.LEFT);
                    }
                }
                break;
        }
        return false;
    }

    /**
     * Listens for swipe events.
     */
    public interface OnSwipeListener {
        /**
         * Indicates the swipe direction.
         */
        enum Direction {
            /** Left swipe. **/
            LEFT,
            /** Right swipe. **/
            RIGHT
        }
        /**
         * Called when a swipe event occurs.
         * @param direction The direction of the swipe.
         */
        void onSwipe(Direction direction);
    }

    /**
     * Sets the swipe listener for a particular view.
     * @param view The view of interest.
     * @param onSwipeListener The swipe listener whose {@link OnSwipeListener#onSwipe(OnSwipeListener.Direction)}
     *                        method is called when a swipe event occurs.
     */
    public void setOnSwipeListener(View view, OnSwipeListener onSwipeListener){
        this.viewOnSwipeListeners.put(view, onSwipeListener);
        view.setOnTouchListener(this);
    }

    /**
     * Sets the swipe listener for a particular activity.
     * @param activity The activity of interest.
     * @param onSwipeListener The swipe listener whose {@link OnSwipeListener#onSwipe(OnSwipeListener.Direction)}
     *                        method is called when a swipe event occurs.
     */
    public void setOnSwipeListener(Activity activity, OnSwipeListener onSwipeListener){
        activityOnSwipeListeners.put(activity, onSwipeListener);
    }

    public void onTouch(Activity activity, MotionEvent event){
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = event.getX();
                break;
            case MotionEvent.ACTION_UP:
                float x2 = event.getX();
                float deltaX = x2 - x1;
                if (Math.abs(deltaX) > MIN_DISTANCE) {
                    if (x2 > x1) {
                        if (activityOnSwipeListeners.get(activity) != null)
                            activityOnSwipeListeners.get(activity).onSwipe(OnSwipeListener.Direction.RIGHT);
                    } else {
                        if (activityOnSwipeListeners.get(activity) != null)
                            activityOnSwipeListeners.get(activity).onSwipe(OnSwipeListener.Direction.LEFT);
                    }
                }
                break;
        }
    }

}
