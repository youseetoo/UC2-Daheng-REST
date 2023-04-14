package gxviewer;


import galaxy.Device;

public class AcquisitionByGetRawImage extends AcquisitionImage {

    private Thread m_acquireThread = null;               ///< acquire thread
    private Thread m_imageProcessRunnableThread = null;  ///< image process thread
    private Thread m_displayRunnableThread = null;       ///< display thread


    /**
     * brief acquisition by getRawImage
     * param dev[in]        device
     * param thShare[in]    share objects among threads
     */
    AcquisitionByGetRawImage(Device dev, ThreadShareObj threadShareObj) {
        super(dev, threadShareObj);

        if(m_threadShareObj != null) {
            m_threadShareObj.m_acquireByRawImageRunnable = new AcquireByRawImageRunnable(dev, threadShareObj);
            m_threadShareObj.m_displayRunnable = new DisplayRunnable(threadShareObj);
            m_threadShareObj.m_imageProcessRunnable = new ImageProcessRunnable(threadShareObj);
        }

    }

    /**
     * brief  start acquisition thread, image process thread, display thread
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
        if(m_threadShareObj.m_acquireByRawImageRunnable != null) {
            m_threadShareObj.m_acquireByRawImageRunnable.setRunning(true);
        }

        // set image process runnable running flag
        if(m_threadShareObj.m_imageProcessRunnable != null) {
            m_threadShareObj.m_imageProcessRunnable.setRunning(true);
        }

        // set display runnable running flag
        if(m_threadShareObj.m_displayRunnable != null) {
            m_threadShareObj.m_displayRunnable.setRunning(true);
        }

        m_acquireThread = new Thread(m_threadShareObj.m_acquireByRawImageRunnable);
        m_imageProcessRunnableThread = new Thread(m_threadShareObj.m_imageProcessRunnable);
        m_displayRunnableThread = new Thread(m_threadShareObj.m_displayRunnable);

        m_acquireThread.setName("acqThread");
        m_imageProcessRunnableThread.setName("imgProcessThread");
        m_displayRunnableThread.setName("disThread");

        m_acquireThread.start();
        m_imageProcessRunnableThread.start();
        m_displayRunnableThread.start();
    }

    /**
     * brief  stop acquisition thread, image process thread, display thread, then wait them stopped
     * throws Exception    join timeout exception
     */
    @Override
    public void stop() throws Exception {

        if(m_threadShareObj != null) {
            m_threadShareObj.setIsStart(false);
        }

        if(m_threadShareObj != null) {
            // reset acquire thread runnable running flag
            if (m_threadShareObj.m_acquireByRawImageRunnable != null) {
                m_threadShareObj.m_acquireByRawImageRunnable.setRunning(false);
            }

            // reset image process runnable running flag
            if (m_threadShareObj.m_imageProcessRunnable != null) {
                m_threadShareObj.m_imageProcessRunnable.setRunning(false);
            }

            // reset display runnable running flag
            if (m_threadShareObj.m_displayRunnable != null) {
                m_threadShareObj.m_displayRunnable.setRunning(false);
            }
        }


        // before stop thread resume them first
        resume();
        if(m_acquireThread != null) {
            m_acquireThread.join();
        }
        if(m_imageProcessRunnableThread != null) {
            m_imageProcessRunnableThread.join();
        }
        if(m_displayRunnableThread != null) {
            m_displayRunnableThread.join();
        }
    }

    @Override
    public void pause() {

        if(m_threadShareObj != null) {
            if(m_threadShareObj.m_acquireByRawImageRunnable != null) {
                m_threadShareObj.m_acquireByRawImageRunnable.suspend();
            }

            if(m_threadShareObj.m_imageProcessRunnable != null) {
                m_threadShareObj.m_imageProcessRunnable.suspend();
            }

            if(m_threadShareObj.m_displayRunnable != null) {
                m_threadShareObj.m_displayRunnable.suspend();
            }
        }
    }

    @Override
    public void resume() {

        if(m_threadShareObj != null) {
            if(m_threadShareObj.m_acquireByRawImageRunnable != null) {
                m_threadShareObj.m_acquireByRawImageRunnable.resume();
            }

            if(m_threadShareObj.m_imageProcessRunnable != null) {
                m_threadShareObj.m_imageProcessRunnable.resume();
            }

            if(m_threadShareObj.m_displayRunnable != null) {
                m_threadShareObj.m_displayRunnable.resume();
            }
        }

    }
}
