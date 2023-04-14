package gxviewer;


import galaxy.Device;

public class AcquisitionByGetImageBySurface extends AcquisitionImage{

    private Thread m_acquireThread = null;                 ///< acquire thread

    /**
     * brief acquisition by getImageBySurface
     * param dev[in]        device
     * param thShare[in]    share objects among threads
     */
    AcquisitionByGetImageBySurface(Device dev, ThreadShareObj threadShareObj) {
        super(dev, threadShareObj);

        if(m_threadShareObj != null) {
            m_threadShareObj.m_acquireBySurfaceRunnable = new AcquireBySurfaceRunnable(dev, threadShareObj);
        }
    }

    /**
     * brief  start acquisition thread
     */
    @Override
    public void start() {

        // when m_threadShareObj is null, return this method
        if(m_threadShareObj == null) {
            return;
        }

        // when m_device is null, return this method
        if(m_device == null) {
            return;
        }

        // set up start flag
        m_threadShareObj.setIsStart(true);

        // set up thread running flag
        if(m_threadShareObj.m_acquireBySurfaceRunnable != null) {
            m_threadShareObj.m_acquireBySurfaceRunnable.setRunning(true);
        }


        m_acquireThread = new Thread(m_threadShareObj.m_acquireBySurfaceRunnable);
        m_acquireThread.setName("acqBySurfaceThread");

        m_acquireThread.start();
    }

    /**
     * brief  stop acquisition thread, then wait it stopped
     * @throws Exception    join timeout exception
     */
    @Override
    public void stop() throws Exception {

        // when isDeviceOpen is false, thread will exist after end of loop
        if(m_threadShareObj != null) {
            m_threadShareObj.setIsStart(false);
        }

        // reset thread running flag
        if(m_threadShareObj != null) {
            if (m_threadShareObj.m_acquireBySurfaceRunnable != null) {
                m_threadShareObj.m_acquireBySurfaceRunnable.setRunning(false);
            }
        }

        // before stop thread resume them first
        resume();
        if(m_acquireThread != null) {
            m_acquireThread.join();
        }
    }

    @Override
    public void pause() {

        if(m_threadShareObj != null) {
            if(m_threadShareObj.m_acquireBySurfaceRunnable != null) {
                m_threadShareObj.m_acquireBySurfaceRunnable.suspend();
            }
        }

    }

    @Override
    public void resume() {

        if(m_threadShareObj != null) {
            if(m_threadShareObj.m_acquireBySurfaceRunnable != null) {
                m_threadShareObj.m_acquireBySurfaceRunnable.resume();
            }
        }

    }
}
