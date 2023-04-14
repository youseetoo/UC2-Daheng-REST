package gxviewer;

import android.support.v4.app.Fragment;

import galaxy.Device;

public abstract class BaseSettingFragment extends Fragment {

    protected Device m_device;                    ///< keep camera device obj
    protected ThreadShareObj m_threadShareObj;    ///< shared obj among threads

    public BaseSettingFragment() {
        super();
    }

    abstract void initUI();
    /**
     * brief    set m_device and m_threadShareObj
     * param dev[in]               device obj
     * param threadShareObj[in]    ThreadShareObj
     */
    public void setDeviceAndThreadShareObj(Device dev, ThreadShareObj threadShareObj) {
        m_device = dev;
        m_threadShareObj = threadShareObj;
        initUI();
    }

    /**
     * brief    when hide fragment, do something to clear member obj in fragment
     *          e.g. Timer, Thread...
     */
    public void clearFragment()
    {

    }
}
