package cs.umass.edu.prepare.data;

import android.graphics.Bitmap;

import java.io.Serializable;

/**
 * A medication includes a name and, if available, an image.
 */
public class Medication implements Serializable {

    private final String name;

    private SerialBitmap image;

    private SerialBitmap defaultImage;

    public Medication(String name){
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Medication)) return false;
        Medication key = (Medication) o;
        return name.equals(key.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public void setImage(Bitmap image){
        this.image = new SerialBitmap(image);
    }

    public void setDefaultImage(Bitmap defaultImage){
        this.defaultImage = new SerialBitmap(defaultImage);
    }

    public Bitmap getImage(){
        if (image == null)
            return null;
        return image.getBitmap();
    }

    public Bitmap getDefaultImage() {
        if (defaultImage == null)
            return null;
        return defaultImage.getBitmap();
    }

    public String getName(){
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
