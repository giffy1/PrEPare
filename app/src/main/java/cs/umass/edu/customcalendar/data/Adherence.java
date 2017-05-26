package cs.umass.edu.customcalendar.data;

import java.io.Serializable;
import java.util.Calendar;

public class Adherence implements Serializable {

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

    private AdherenceType adherenceType;
    private Calendar timeTaken;

    public Adherence(AdherenceType adherenceType, Calendar timeTaken){
        this.adherenceType = adherenceType;
        this.timeTaken = timeTaken;
    }

    public AdherenceType getAdherenceType() {
        return adherenceType;
    }

    public Calendar getTimeTaken() {
        return timeTaken;
    }

    public void setAdherenceType(AdherenceType adherenceType) {
        this.adherenceType = adherenceType;
    }

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