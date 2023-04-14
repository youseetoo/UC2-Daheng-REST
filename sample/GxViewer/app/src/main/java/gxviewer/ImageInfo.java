package gxviewer;

public class ImageInfo {

    private boolean m_saveImage = false;
    private Object m_imageObj;

    ImageInfo() {}

    boolean getSaveImage() { return m_saveImage; }
    void setSaveImage(boolean saveImage) { m_saveImage = saveImage; }

    Object getImageObj() { return m_imageObj; }
    void setImageObj(Object image) { m_imageObj = image; }

}
