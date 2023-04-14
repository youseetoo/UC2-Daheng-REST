package gxviewer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;

/**
 * brief    CamParamFragment class
 */
public class CamParamsFragment extends BaseSettingFragment {

    View m_view;                 ///< keep view obj

    private Spinner m_userSetSelectSpinner;
    private Button m_userSetLoadBtn;
    private Button m_userSetSaveBtn;
    private Spinner m_userSetDefaultSpinner;

    private final int USER_SET_DEFAULT   = 0;
    private final int USER_SET_USER_SET0 = 1;


    public enum UIHandlerEnumSet {

        DO_NOTHING(0),                       ///< nothing to do
        ENABLE_USER_SET_LOAD_BTN(1);

        private final int m_value;
        UIHandlerEnumSet(int i) {
            m_value = i;
        }

        /**
         * brief get value of enum
         * return    value
         */
        public int getValue() {
            return m_value;
        }

        /**
         * brief get enum of value
         * param i[in]    value of enum
         * return enum
         */
        public static UIHandlerEnumSet valueOf(int i) {
            for(UIHandlerEnumSet enumSet : values()) {
                if(enumSet.getValue() == i) {
                    return enumSet;
                }
            }
            return DO_NOTHING;
        }
    }

    private Handler m_UIHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            UIHandlerEnumSet enumSet = UIHandlerEnumSet.valueOf(msg.what);
            switch (enumSet) {

                case DO_NOTHING:
                    break;
                case ENABLE_USER_SET_LOAD_BTN:
                    m_userSetLoadBtn.setEnabled(true);

                    break;

            }
            return false;
        }
    });

    public CamParamsFragment() {

    }

    /**
     * brief when show this fragment, update ui by device param
     */
    void initUI() {

        if(m_view == null) {
            return;
        }

        m_userSetDefaultSpinner = m_view.findViewById(R.id.user_set_default);
        m_userSetLoadBtn = m_view.findViewById(R.id.user_set_load);
        m_userSetSaveBtn = m_view.findViewById(R.id.user_set_save);
        m_userSetSelectSpinner = m_view.findViewById(R.id.user_set_selector);

        if(m_userSetDefaultSpinner == null ||
                m_userSetLoadBtn == null ||
                m_userSetSaveBtn == null ||
                m_userSetSelectSpinner == null) {
            return;
        }

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            setDisable();
            return;
        }

        // when device is not work disable this view
        if(!m_threadShareObj.getIsStart()) {
            setDisable();
            return;
        }

        // init ui by device param
        try {

            // read user set default from device, then init userSetDefault Spinner
            long userSetDef = m_device.UserSetDefault.get();
            if(userSetDef == EnumDefineSet.UserSetEntry.DEFAULT.getValue()) {
                m_userSetDefaultSpinner.setSelection(USER_SET_DEFAULT);
            } else if(userSetDef == EnumDefineSet.UserSetEntry.USER_SET0.getValue()) {
                m_userSetDefaultSpinner.setSelection(USER_SET_USER_SET0);
            }

        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();

            m_userSetDefaultSpinner.setEnabled(false);

            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        try {

            // read user set selector from device, then init userSetSelector Spinner
            long userSetSelector = m_device.UserSetSelector.get();
            if(userSetSelector == EnumDefineSet.UserSetEntry.DEFAULT.getValue()) {
                m_userSetSelectSpinner.setSelection(USER_SET_DEFAULT);
            } else if(userSetSelector == EnumDefineSet.UserSetEntry.USER_SET0.getValue()) {
                m_userSetSelectSpinner.setSelection(USER_SET_USER_SET0);
            }

            // when user set selector is default, disable user set save button
            if(userSetSelector == EnumDefineSet.UserSetEntry.DEFAULT.getValue()) {
                m_userSetSaveBtn.setEnabled(false);
            } else {
                m_userSetSaveBtn.setEnabled(true);
            }


        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();

            m_userSetSelectSpinner.setEnabled(false);
            m_userSetSaveBtn.setEnabled(false);

            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /**
     * brief    set ui status
     * param enable[in]  false to disable ui, true to enable ui
     */
    private void setDisable() {
        m_userSetSelectSpinner.setEnabled(false);
        m_userSetLoadBtn.setEnabled(false);
        m_userSetSaveBtn.setEnabled(false);
        m_userSetDefaultSpinner.setEnabled(false);
    }

    /**
     * brief  when push user set load button, send user set load command to device
     *        note: before send command, must to stream off
     */
    void onUserSetLoad() {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        m_userSetLoadBtn.setEnabled(false);

        // load user set:
        // 1) pause acquisition image thread;
        // 2) stream off;
        // 3) send UserSetLoad command;
        // 4) stream on;
        // 5) resume acquisition image thread.
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                m_threadShareObj.m_offlineLock.lock();

                try {
                    // pause acquisition image thread
                    m_threadShareObj.m_acquisitionImage.pause();

                    if (m_threadShareObj.getOffLineStatus()) {
                        return;
                    }

                    try {
                        // stream off before load user set param
                        m_device.streamOff();
                        // send UserSetLoad command
                        m_device.UserSetLoad.sendCommand();
                    } catch (ExceptionSet.OffLine exception) {
                        exception.printStackTrace();
                        // stop acquisition image thread
                        try {
                            m_threadShareObj.m_acquisitionImage.stop();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;

                    } catch (ExceptionSet exceptionSet) {
                        exceptionSet.printStackTrace();
                        Message msg = Message.obtain();
                        msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
                        msg.obj = "UserSetLoad failed, Please try again!\nDetail:\n" + exceptionSet.toString();
                        m_threadShareObj.m_threadHandler.sendMessage(msg);
                    }

                    if (m_threadShareObj.getOffLineStatus()) {
                        return;
                    }

                    try {
                        m_device.streamOn();
                    } catch (ExceptionSet.OffLine exception) {
                        exception.printStackTrace();
                        // stop acquisition image thread
                        try {
                            m_threadShareObj.m_acquisitionImage.stop();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;
                    } catch (ExceptionSet exceptionSet) {
                        // when stream on is failed, send level A error msg to stop acquisition
                        exceptionSet.printStackTrace();

                        Message msg = Message.obtain();
                        msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_A.getValue();
                        msg.obj = exceptionSet.toString();
                        m_threadShareObj.m_threadHandler.sendMessage(msg);
                    }
                } finally {
                    // resume acquisition image thread
                    m_threadShareObj.m_acquisitionImage.resume();
                    // unlock before exit this thread
                    m_threadShareObj.m_offlineLock.unlock();
                }

                // send message to enable USER SET LOAD button
                Message msg = Message.obtain();
                msg.what = UIHandlerEnumSet.ENABLE_USER_SET_LOAD_BTN.getValue();
                m_UIHandler.sendMessage(msg);
            }
        };

        // create user set load runnable, then start this thread
        Thread thread = new Thread(runnable);
        thread.start();
    }

    /**
     * brief  when push user set save button, send user set save command to device
     */
    void onUserSetSave() {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        // save user set param
        try {
            m_device.UserSetSave.sendCommand();
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

    /**
     * brief  when user set selector changed, set user set to device
     * param index[in]   index of user set
     */
    void onUserSetSelectorChanged(int index) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            if(index == USER_SET_DEFAULT) {
                m_device.UserSetSelector.set(EnumDefineSet.UserSetEntry.DEFAULT.getValue());
            } else if(index == USER_SET_USER_SET0) {
                m_device.UserSetSelector.set(EnumDefineSet.UserSetEntry.USER_SET0.getValue());
            }

            if(m_device.UserSetSelector.get() == EnumDefineSet.UserSetEntry.DEFAULT.getValue()) {
                m_userSetSaveBtn.setEnabled(false);
            } else {
                m_userSetSaveBtn.setEnabled(true);
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

    /**
     * brief  when user set default changed, set default user set to device
     * param index[in]   index of user set
     */
    void onUserSetDefaultChanged(int index) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            if(index == USER_SET_DEFAULT) {
                m_device.UserSetDefault.set(EnumDefineSet.UserSetEntry.DEFAULT.getValue());
            } else if(index == USER_SET_USER_SET0) {
                m_device.UserSetDefault.set(EnumDefineSet.UserSetEntry.USER_SET0.getValue());
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
    public void clearFragment() {

        if(m_UIHandler != null) {
            m_UIHandler.removeCallbacksAndMessages(null);
        }

        super.clearFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        m_view = inflater.inflate(R.layout.fragment_cam_params, container, false);
        return m_view;

    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initUI();

        m_userSetLoadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUserSetLoad();
            }
        });

        m_userSetSaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onUserSetSave();
            }
        });

        m_userSetSelectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onUserSetSelectorChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        m_userSetDefaultSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onUserSetDefaultChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }
}
