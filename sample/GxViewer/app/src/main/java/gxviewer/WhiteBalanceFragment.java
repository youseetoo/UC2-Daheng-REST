package gxviewer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;

public class WhiteBalanceFragment extends BaseSettingFragment {

    private View m_view;                ///< keep view obj

    private final int TURN_OFF_WHITE_BALANCE = 0;

    private Spinner m_whiteBalanceSpinner;
    private Handler m_msgHandler;
    private Timer m_updateWhiteBalanceTimer;    ///< when set once, use this timer to check weather camera turn to Off

    private int INDEX_OFF        = 0;
    private int INDEX_ONCE       = 1;
    private int INDEX_CONTINUOUS = 2;


    ArrayAdapter<String> m_whiteBalanceAdapter;

    public WhiteBalanceFragment() {

    }

    /**
     * brief when show this fragment, update ui by camera param
     */
    public void initUI() {

        List<String> modeItems = new ArrayList<>();
        modeItems.add("Off");
        modeItems.add("Once");
        modeItems.add("Continuous");

        if(m_view == null) {
            return;
        }

        // set white balance mode param to WhiteBalance Spinner
        m_whiteBalanceAdapter = new ArrayAdapter<>(m_view.getContext(),
                android.R.layout.simple_list_item_1,
                modeItems);

        m_whiteBalanceSpinner = m_view.findViewById(R.id.white_balance_spinner);
        m_whiteBalanceSpinner.setAdapter(m_whiteBalanceAdapter);

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            setEnable(false);
            return;
        }

        // read camera's white balance mode
        // set default value by camera's status
        try {

            if(!m_device.BalanceWhiteAuto.isImplemented()) {
                setEnable(false);
                return;
            }
            // set default auto white balance mode
            long mode = m_device.BalanceWhiteAuto.get();

            if(mode == EnumDefineSet.AutoEntry.OFF.getValue()) {

                m_whiteBalanceSpinner.post(new Runnable() {
                    @Override
                    public void run() {
                        m_whiteBalanceSpinner.setSelection(INDEX_OFF, true);
                    }
                });
            } else if(mode == EnumDefineSet.AutoEntry.ONCE.getValue()) {
                m_whiteBalanceSpinner.post(new Runnable() {
                    @Override
                    public void run() {
                        m_whiteBalanceSpinner.setSelection(INDEX_ONCE, true);
                    }
                });

            } else if(mode == EnumDefineSet.AutoEntry.CONTINUOUS.getValue()) {

                m_whiteBalanceSpinner.post(new Runnable() {
                    @Override
                    public void run() {
                        m_whiteBalanceSpinner.setSelection(INDEX_CONTINUOUS, true);
                    }
                });

            }
        } catch (ExceptionSet.NotImplemented notImplemented) {
            return;
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        m_msgHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                setEnable(true);
                m_whiteBalanceSpinner.post(new Runnable() {
                    @Override
                    public void run() {
                        m_whiteBalanceSpinner.setSelection(INDEX_OFF, true);
                    }
                });
                return false;
            }
        });
    }

    private void setEnable(boolean enable) {
        m_whiteBalanceSpinner.setEnabled(enable);
    }
    /**
     * brief when white balance status changed by user,
     *         set new white balance param to camera
     * param index[in]    set param index of whiteBalanceItems list
     */
    private void onWhiteBalanceChanged(int index) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            // check weather support balance white auto set
            if(!m_device.BalanceWhiteAuto.isImplemented()) {
                return;
            }

            if(index == INDEX_OFF) {
                m_device.BalanceWhiteAuto.set(EnumDefineSet.AutoEntry.OFF.getValue());
            } else if(index == INDEX_ONCE) {
                m_device.BalanceWhiteAuto.set(EnumDefineSet.AutoEntry.ONCE.getValue());
                setEnable(false);

                if(m_updateWhiteBalanceTimer != null) {
                    m_updateWhiteBalanceTimer.cancel();
                    m_updateWhiteBalanceTimer = null;
                }

                m_updateWhiteBalanceTimer = new Timer();
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            if(m_device.BalanceWhiteAuto.get() == EnumDefineSet.AutoEntry.OFF.getValue()) {
                                Message msg = Message.obtain();
                                msg.what = TURN_OFF_WHITE_BALANCE;
                                m_msgHandler.sendMessage(msg);

                                if(m_updateWhiteBalanceTimer != null) {
                                    m_updateWhiteBalanceTimer.cancel();
                                    m_updateWhiteBalanceTimer = null;
                                }
                            }
                        } catch(ExceptionSet.OffLine offLine) {
                            if(m_updateWhiteBalanceTimer != null) {
                                m_updateWhiteBalanceTimer.cancel();
                                m_updateWhiteBalanceTimer = null;
                            }
                        } catch (ExceptionSet exceptionSet) {
                            exceptionSet.printStackTrace();
                            Message msg = Message.obtain();
                            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
                            msg.obj = exceptionSet.toString();
                            m_threadShareObj.m_threadHandler.sendMessage(msg);

                            if(m_updateWhiteBalanceTimer != null) {
                                m_updateWhiteBalanceTimer.cancel();
                                m_updateWhiteBalanceTimer = null;
                            }
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    }
                };
                final int delay = 0;
                final int period = 200;
                m_updateWhiteBalanceTimer.schedule(task, delay, period);

            } else if(index == INDEX_CONTINUOUS) {
                m_device.BalanceWhiteAuto.set(EnumDefineSet.AutoEntry.CONTINUOUS.getValue());
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

    public void clearFragment() {
        if(m_updateWhiteBalanceTimer != null) {
            m_updateWhiteBalanceTimer.cancel();
        }

        // remove all message in handler deque
        if(m_msgHandler != null) {
            m_msgHandler.removeCallbacksAndMessages(null);
        }

        super.clearFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        m_view = inflater.inflate(R.layout.fragment_white_balance, container, false);
        return m_view;
    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);

        initUI();

        // register white balance changed event
        m_whiteBalanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // change white balance mode status
                onWhiteBalanceChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
    }
}
