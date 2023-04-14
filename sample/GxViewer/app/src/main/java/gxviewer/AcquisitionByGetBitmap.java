package gxviewer;

import galaxy.Device;

public class AcquisitionByGetBitmap extends AcquisitionImage {

    private Thread m_acquireThread;            ///< acquire thread
    private Thread m_displayRunnableThread;    ///< display thread

    /**
     * brief acquisition by getBitmapImage
     * param dev[in]        device
     * param thShare[in]    share objects among threads
     */
    AcquisitionByGetBitmap(Device dev, ThreadShareObj threadShareObj) {
        super(dev, threadShareObj);

        if(m_threadShareObj != null) {
            m_threadShareObj.m_acquireByBitmapRunnable = new AcquireByBitmapRunnable(dev, m_threadShareObj);
            m_threadShareObj.m_displayRunnable = new DisplayRunnable(threadShareObj);
        }
    }

    /**
     * brief  start acquisition thread, start display thread
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

        // set acquire thread runnable running flag
        if(m_threadShareObj.m_displayRunnable != null) {
            m_threadShareObj.m_displayRunnable.setRunning(true);
        }

        // set acquire thread runnable running flag
        if(m_threadShareObj.m_acquireByBitmapRunnable != null) {
            m_threadShareObj.m_acquireByBitmapRunnable.setRunning(true);
        }

        m_displayRunnableThread = new Thread(m_threadShareObj.m_displayRunnable);
        m_acquireThread = new Thread(m_threadShareObj.m_acquireByBitmapRunnable);
        m_acquireThread.setName("acqByBitmapThread");
        m_displayRunnableThread.setName("displayThread");

        m_acquireThread.start();
        m_displayRunnableThread.start();
    }

    /**
     * brief  stop acquisition thread, stop display thread, then wait them stopped
     * @throws Exception    join timeout exception
     */
    @Override
    public void stop() throws Exception {

        // when isDeviceOpen is false, thread will exist after end of loop
        if(m_threadShareObj != null) {
            m_threadShareObj.setIsStart(false);
        }

        if(m_threadShareObj != null) {

            if(m_threadShareObj.m_acquireByBitmapRunnable != null) {
                m_threadShareObj.m_acquireByBitmapRunnable.setRunning(false);
            }

            if(m_threadShareObj.m_displayRunnable != null) {
                m_threadShareObj.m_displayRunnable.setRunning(false);
            }
        }

        // before stop thread resume them first
        resume();
        if(m_acquireThread != null) {
            m_acquireThread.join();
        }
        if(m_displayRunnableThread != null) {
            m_displayRunnableThread.join();
        }
    }

    @Override
    public void pause() {

        if(m_threadShareObj != null) {

            if(m_threadShareObj.m_acquireByBitmapRunnable != null) {
                m_threadShareObj.m_acquireByBitmapRunnable.suspend();
            }

            if(m_threadShareObj.m_displayRunnable != null) {
                m_threadShareObj.m_displayRunnable.suspend();
            }
        }

    }

    @Override
    public void resume() {

        if(m_threadShareObj != null) {

            if(m_threadShareObj.m_acquireByBitmapRunnable != null) {
                m_threadShareObj.m_acquireByBitmapRunnable.resume();
            }

            if(m_threadShareObj.m_displayRunnable != null) {
                m_threadShareObj.m_displayRunnable.resume();
            }
        }

    }
}
