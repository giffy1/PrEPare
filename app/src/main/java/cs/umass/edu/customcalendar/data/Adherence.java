package cs.umass.edu.customcalendar.data;

import android.support.annotation.Nullable;

import java.io.Serializable;
import java.util.Calendar;

/**
 * The adherence class wraps an adherence type, along with a timestamp,
 * indicating when the pill was taken, if applicable.
 */
public class Adherence implements Serializable {

    /**
     * Indicates the type of adherence.
     */
    public enum AdherenceType {
        MISSED,
        TAKEN,
        TAKEN_CLARIFY_TIME,
        TAKEN_EARLY_OR_LATE,
        FUTURE,
        NONE;

        @Override
        public String toString() {
            return name();
        }
    }

    /**
     * The adherence type.
     */
    private AdherenceType adherenceType;

    /**
     * The timestamp at which the pill was taken; null if not applicable.
     */
    private Calendar timeTaken;

    public Adherence(AdherenceType adherenceType, @Nullable Calendar timeTaken){
        this.adherenceType = adherenceType;
        this.timeTaken = timeTaken;
    }

    /**
     * Returns the adherence type.
     * @return one of {@link AdherenceType}.
     */
    public AdherenceType getAdherenceType() {
        return adherenceType;
    }

    /**
     * Returns the timestamp at which the pill was taken.
     * @return a {@link Calendar} object encoding a timestamp.
     */
    @Nullable
    public Calendar getTimeTaken() {
        return timeTaken;
    }

    /**
     * Sets the adherence type.
     * @param adherenceType one of {@link AdherenceType}.
     */
    public void setAdherenceType(AdherenceType adherenceType) {
        this.adherenceType = adherenceType;
    }

    /**
     * Sets the timestamp at which the pill was taken.
     * @param timeTaken a {@link Calendar} object encoding a timestamp.
     */
    public void setTimeTaken(Calendar timeTaken) {
        this.timeTaken = timeTaken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Adherence)) return false;
        Adherence key = (Adherence) o;
        return adherenceType.equals(key.adherenceType) && timeTaken.equals(key.timeTaken);
    }

    @Override
    public int hashCode() {
        return 1013 * (adherenceType.hashCode()) ^ 1009 * (timeTaken.hashCode());
    }
}