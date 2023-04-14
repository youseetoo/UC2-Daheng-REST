package gxviewer;

import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;


import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;

public class TriggerModeFragment extends BaseSettingFragment {

    private View m_view;                 ///< keep view obj
    Switch m_trigModeSwitch;
    /**
     * brief  trigger mode set
     */
    public TriggerModeFragment() {

    }

    /**
     * brief when show this fragment, update ui by camera param
     */
    public void initUI() {

        if(m_view == null) {
            return;
        }

        m_trigModeSwitch = m_view.findViewById(R.id.trigger_switch);

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            setDisable();
            return;
        }
        m_trigModeSwitch.setEnabled(true);

        // check m_device's trigger mode, set it to ui
        try {
            if(!m_device.TriggerMode.isImplemented()) {
                m_trigModeSwitch.setEnabled(false);
                return;
            }
            long triMode = m_device.TriggerMode.get();
            if(triMode == EnumDefineSet.SwitchEntry.OFF.getValue()) {
                // off
                m_trigModeSwitch.post(new Runnable() {
                    @Override
                    public void run() {
                        m_trigModeSwitch.setChecked(false);
                    }
                });

            } else if(triMode == EnumDefineSet.SwitchEntry.ON.getValue()) {
                // on
                m_trigModeSwitch.post(new Runnable() {
                    @Override
                    public void run() {
                        m_trigModeSwitch.setChecked(true);
                    }
                });
            }
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

    private void setDisable() {
        m_trigModeSwitch.setEnabled(false);
    }
    /**
     * brief when trigger mode is change call this function to change trigger mode
     * param index[in]    set param index of triggerItems list
     */
    public void onTriggerModeChanged(boolean enable) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        // set select trigger mode to camera here
        try {

            // check weather support trigger mode set
            if(!m_device.TriggerMode.isImplemented()) {
                return;
            }

            if(!enable) {// turn off trigger mode
                m_device.TriggerMode.set(EnumDefineSet.SwitchEntry.OFF.getValue());
            } else {// turn on trigger mode
                m_device.TriggerMode.set(EnumDefineSet.SwitchEntry.ON.getValue());
            }

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
        m_view = inflater.inflate(R.layout.fragment_trigger_mode, container, false);
        return m_view;
    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // register trigger mode selected event
        Switch triggerSwitch = m_view.findViewById(R.id.trigger_switch);
        triggerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // change trigger mode
                onTriggerModeChanged(isChecked);
            }
        });
        
        // update ui by camera's param
        initUI();
    }
}
