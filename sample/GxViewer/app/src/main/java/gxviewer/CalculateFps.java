package gxviewer;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class CalculateFps {

    private long m_frameId = 0;                ///< record total number of frame
    private int m_frameCnt = 0;                ///< record number of frame during a time circle

    private float m_fps = 0.0f;

    private long m_endTime = 0;          ///< record last one picture time
    private long m_startTime = 0;
    private boolean m_isUpdateTime = false;


    private Lock m_calculateLock = new ReentrantLock();

    /**
     * brief  fps calculator
     * param shareObj[in]   share obj
     */
    CalculateFps() {

    }

    /**
     * brief  refresh fps by latest data
     */
    void refreshFpsCount() {

        m_calculateLock.lock();

        try {
            // record begin time of acquire image
            m_endTime = Calendar.getInstance().getTimeInMillis();
            // record frame count
            m_frameCnt = m_frameCnt + 1;
            m_frameId = m_frameId + 1;
            m_isUpdateTime = true;
        }finally {
            m_calculateLock.unlock();
        }
    }

    /**
     * brief  get fps
     * return fps
     */
    float getFps() {

        // when frame id greater than zero, calculate fps
        ///< frame per second
        if(m_frameId > 0) {

            long endTime = m_endTime;
            // when acquire thread is work, m_isUpdateTime will be true
            if(m_isUpdateTime) {

                m_isUpdateTime = false;

                long dt = endTime - m_startTime;

                if(dt >= 100) {
                    m_fps = (1000 * m_frameCnt / (float)dt);

                    m_calculateLock.lock();
                    try {
                        m_startTime = Calendar.getInstance().getTimeInMillis();
                        m_frameCnt = 0;
                    } finally {
                        m_calculateLock.unlock();
                    }

                }

            } else {
                long dt = Calendar.getInstance().getTimeInMillis() - endTime;
                //If there is no frame for more than 2 seconds, the frame rate will drop to zero.
                final int ZERO_FPS_INTERVAL = 2000;
                if(dt > ZERO_FPS_INTERVAL) {
                    m_fps = 0.0f;
                } else {
                    m_fps = 1000.0f / (float) dt;
                }
            }
        } else {
            m_fps = 0;
            m_isUpdateTime = false;
        }

        return m_fps;
    }

    /**
     * brief get frame id
     * return frame id
     */
    long getFrameId() {
        return m_frameId;
    }

    /**
     * brief clear frame id
     */
    void clearFrameId() {
        m_frameId = 0;
        m_frameCnt = 0;
    }
}
