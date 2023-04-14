package gxviewer;

abstract class WorkRunnable implements Runnable {

    private boolean m_isSuspendStatus = false;   ///< current thread status
    private boolean m_suspend = false;           ///< thread suspend flag
    private boolean m_isRunning = false;

    synchronized void suspend() {
        m_suspend = true;
        while(!m_isSuspendStatus && m_isRunning) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    synchronized void resume() {
        m_suspend = false;
    }

    void checkSuspendStatus() {
        while(m_suspend && m_isRunning) {
            m_isSuspendStatus = true;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        m_isSuspendStatus = false;
    }

    synchronized void setRunning(boolean r) {
        m_isRunning = r;
    }

    boolean getRunning() {
        return m_isRunning;
    }
}
