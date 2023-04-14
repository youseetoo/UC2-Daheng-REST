package gxviewer;

import galaxy.Device;

public abstract class AcquisitionImage {

    protected ThreadShareObj m_threadShareObj;    ///< share object among threads
    protected Device m_device;                    ///< device obj
    /**
     * brief   acquisition by getBitmapImage
     * param dev[in]               device
     * param threadShareObj[in]    share objects among threads
     */
    AcquisitionImage(Device dev, ThreadShareObj threadShareObj) {
        m_device = dev;
        m_threadShareObj = threadShareObj;
    }

    /**
     * brief start acquisition
     */
    public abstract void start();

    /**
     * brief stop acquisition
     * throws Exception   thread join exception
     */
    public abstract void stop() throws Exception;

    public abstract void pause();
    public abstract void resume();
}
