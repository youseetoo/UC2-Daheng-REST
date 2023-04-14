package gxviewer;

import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import galaxy.DeviceManager;
import galaxy.ExceptionSet;


/**
 * brief    AboutInfoFragment class
 */
public class AboutInfoFragment extends BaseSettingFragment {

    private View m_view;                        ///< keep view obj
    /**
     * brief    show version information on ui
     */
    public AboutInfoFragment() {

    }

    /**
     * brief    read version info from device, then set to ui
     */
    void initUI() {

        // if m_view is null do nothing
        if(m_view == null) {
            return;
        }

        // get library version information
        try {

            String libVer = DeviceManager.getInstance().getVersion();
            TextView libText = m_view.findViewById(R.id.library_version);
            libText.setText(libVer);

        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
        }


        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        // read camera's device version info
        try {

            // get device version information
            String devVer = m_device.DeviceVersion.get();
            // get firmware version information
            String devFirmwareVer = m_device.DeviceFirmwareVersion.get();
            // get factory setting version information
            String facSettingVer = m_device.FactorySettingVersion.get();

            // write version information to ui
            TextView devText = m_view.findViewById(R.id.dev_version);
            devText.setText(devVer);

            TextView devFirmwareText = m_view.findViewById(R.id.dev_frame_version);
            devFirmwareText.setText(devFirmwareVer);

            TextView facSettingText = m_view.findViewById(R.id.fac_setting_version);
            facSettingText.setText(facSettingVer);

        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        m_view = inflater.inflate(R.layout.fragment_about_info, container, false);
        return m_view;

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // write info to ui
        initUI();

    }
}
