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

public class ExposureGainFragment extends BaseSettingFragment {

    private View m_view;                         ///< keep view obj

    private Timer m_updateGainTimer;             ///< refresh gain status timer
    private TimerTask m_updateGainTimerTask;     ///< gain timer task, for check gain status
    private Timer m_updateExposureTimer;         ///< refresh exposure status timer
    private TimerTask m_updateExposureTimerTask; ///< exposure timer task, for check exposure status
    private Handler m_msgHandler;                ///< refresh massage handler

    private Spinner m_gainSpinner;
    private Spinner m_exposureSpinner;
    private SeekBarValue m_exposureSeekBar;
    private SeekBarValue m_gainSeekBar;
    private SeekBarValue m_expectedGraySeekBar;

    private final int EXPOSURE_STATUS_CHANGED = 0;
    private final int EXPOSURE_VALUE_CHANGED  = 1;
    private final int GAIN_STATUS_CHANGED     = 2;
    private final int GAIN_VALUE_CHANGED      = 3;
    private final int TIMER_RUN_TIME          = 200;
    private final int TIMER_DELAY_TIME        = 0;

    private final int OFF        = 0;
    private final int ONCE       = 1;
    private final int CONTINUOUS = 2;

    /**
     * brief  exposure and gain setting
     */
    public ExposureGainFragment() {
        super();
    }

    /**
     * brief  when exposure value changed, set value to device
     * param value[in]  value to set to device
     */
    private void onExposureValueChanged(double value) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            // check weather support exposure value set
            if(!m_device.ExposureTime.isImplemented()) {
                return;
            }

            // check weather support exposure auto set
            if(!m_device.ExposureAuto.isImplemented()) {
                return;
            }

            // set exposure time when exposure auto is off
            if(m_device.ExposureAuto.get() == EnumDefineSet.AutoEntry.OFF.getValue()) {
                m_device.ExposureTime.set(value);
            }

        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } catch(Exception exception) {
            exception.printStackTrace();
        }

    }

    /**
     * brief  when change auto exposure status,
     *         set new auto exposure status to camera
     * param index[in]   index of exposure to set
     */
    private void onExposureModeChanged(int index) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            // check weather support exposure auto set
            if(!m_device.ExposureAuto.isImplemented()) {
                return;
            }

            if(index == OFF) {

                m_device.ExposureAuto.set(EnumDefineSet.AutoEntry.OFF.getValue());
                // when back to off, cancel exposure timer
                if(m_updateExposureTimer != null) {
                    m_updateExposureTimer.cancel();
                    m_updateExposureTimer = null;
                }
                if(m_updateExposureTimerTask != null) {
                    m_updateExposureTimerTask.cancel();
                    m_updateExposureTimerTask = null;
                }

            } else if(index == ONCE) {
                m_exposureSpinner.setEnabled(false);
                m_device.ExposureAuto.set(EnumDefineSet.AutoEntry.ONCE.getValue());
            } else if(index == CONTINUOUS) {
                m_device.ExposureAuto.set(EnumDefineSet.AutoEntry.CONTINUOUS.getValue());
            }

            // when set to continuous or once mode, run timer to refresh exposure value on seek bar
            if(index != OFF) {

                // disable exposure seek bar
                m_exposureSeekBar.setEnabled(false);
                // setup exposure timer to check if exposure value changed and update latest value to seek bar
                startExposureTimer();

            } else {

                // enable exposure seek bar
                m_exposureSeekBar.setEnabled(true);

            }

        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch(Exception exception) {

            exception.printStackTrace();

        }
    }

    /**
     * brief   set gain value
     * param value[in]    value to set to device
     */
    private void onGainValueChanged(double value) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            // check weather support gain value set
            if(!m_device.Gain.isImplemented()) {
                return;
            }

            // check weather support gain auto set
            if(!m_device.GainAuto.isImplemented()) {
                return;
            }

            // set gain value when gain auto is off
            if(m_device.GainAuto.get() == EnumDefineSet.AutoEntry.OFF.getValue()) {
                m_device.Gain.set(value);
            }

        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch(Exception exception) {
            exception.printStackTrace();
        }
    }

    /**
     * brief  when change auto gain status,
     *        set new auto gain status to camera
     * param  index[in]    index of gain mode
     */
    private void onGainModeChanged(int index) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            // check weather support gain auto set
            if(!m_device.GainAuto.isImplemented()) {
                return;
            }

            if(index == OFF) {

                m_device.GainAuto.set(EnumDefineSet.AutoEntry.OFF.getValue());
                // when back to off, cancel gain timer
                if(m_updateGainTimer != null) {
                    m_updateGainTimer.cancel();
                    m_updateGainTimer = null;
                }
                if(m_updateGainTimerTask != null) {
                    m_updateGainTimerTask.cancel();
                    m_updateGainTimerTask = null;
                }

            } else if(index == ONCE) {

                m_device.GainAuto.set(EnumDefineSet.AutoEntry.ONCE.getValue());
                m_gainSpinner.setEnabled(false);

            } else if(index == CONTINUOUS) {

                m_device.GainAuto.set(EnumDefineSet.AutoEntry.CONTINUOUS.getValue());

            }

            // when set to continuous or once mode, run timer to refresh gain value on seek bar
            if(index != OFF) {

                // disable gain seek bar
                m_gainSeekBar.setEnabled(false);
                // setup gain timer to check if gain value changed and update latest value to seek bar
                startGainTimer();

            } else {

                // enable gain seek bar
                m_gainSeekBar.setEnabled(true);

            }
        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch(Exception exception) {

            exception.printStackTrace();

        }
    }

    /**
     * brief  set expected gray value
     * param value[in]  value to set
     */
    public void onExpectedGrayValueChanged(long value) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        try {

            // check weather support expected gray value set
            if(!m_device.ExpectedGrayValue.isImplemented()) {
                return;
            }

            m_device.ExpectedGrayValue.set(value);
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } catch(Exception exception) {
            exception.printStackTrace();
        }
    }
    /**
     * brief  when show this fragment, update ui by camera param
     */
    public void initUI() {

        if(m_view == null) {
            return;
        }

        m_exposureSpinner = m_view.findViewById(R.id.exposure_spinner);
        m_gainSpinner = m_view.findViewById(R.id.gain_spinner);
        m_exposureSeekBar = m_view.findViewById(R.id.exposure_time_seek_bar);
        m_gainSeekBar = m_view.findViewById(R.id.gain_seek_bar);
        m_expectedGraySeekBar = m_view.findViewById(R.id.expected_gray_seek_bar);

        List<String> modeItems = new ArrayList<>();
        modeItems.add("Off");
        modeItems.add("Once");
        modeItems.add("Continuous");

        // set items to exposure spinner from camera's param
        ArrayAdapter<String> expoAdapter = new ArrayAdapter<>(m_view.getContext()
                , android.R.layout.simple_list_item_1
                , modeItems);

        m_exposureSpinner.setAdapter(expoAdapter);

        // set items to gain spinner from camera's param
        ArrayAdapter<String> gainAdapter = new ArrayAdapter<>(m_view.getContext()
                , android.R.layout.simple_list_item_1
                , modeItems);

        m_gainSpinner.setAdapter(gainAdapter);

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            setEnable(false);
            return;
        }

        if(!m_threadShareObj.getIsStart()) {
            setEnable(false);
            return;
        }

        // set default auto exposure mode
        __setDefaultExposureMode();

        // set default auto gain mode
        __setDefaultGainMode();

        // set default exposure time range and value
        __setExposureTimeRangeAndValue();

        // set default gain range and value
        __setGainRangeAndValue();

        // set expected gray value from camera's param
        __setExpectedGrayRangeAndValue();

    }

    private void __setDefaultExposureMode() {

        long l_exposureMode;
        try {

            if(m_device.ExposureAuto.isImplemented()) {

                l_exposureMode = m_device.ExposureAuto.get();
                // when exposure status is on once or continuous, start exposure timer to update exposure value
                if(l_exposureMode == EnumDefineSet.AutoEntry.OFF.getValue()) {
                    setExposureSelectItem(OFF);
                } else if(l_exposureMode == EnumDefineSet.AutoEntry.ONCE.getValue()) {
                    setExposureSelectItem(ONCE);
                    startExposureTimer();
                } else if(l_exposureMode == EnumDefineSet.AutoEntry.CONTINUOUS.getValue()) {
                    setExposureSelectItem(CONTINUOUS);
                    startExposureTimer();
                }

            } else {
                m_exposureSpinner.setEnabled(false);
            }

        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch(Exception exception) {
            exception.printStackTrace();
        }

    }

    private void __setDefaultGainMode() {
        long l_gainMode;
        try {

            if(m_device.GainAuto.isImplemented()) {

                l_gainMode = m_device.GainAuto.get();
                if(l_gainMode == EnumDefineSet.AutoEntry.OFF.getValue()) {
                    setGainSelectItem(OFF);
                } else if(l_gainMode == EnumDefineSet.AutoEntry.ONCE.getValue()) {
                    setGainSelectItem(ONCE);
                    startGainTimer();
                } else if(l_gainMode == EnumDefineSet.AutoEntry.CONTINUOUS.getValue()) {
                    setGainSelectItem(CONTINUOUS);
                    startGainTimer();
                }

            } else {
                m_gainSpinner.setEnabled(false);
            }

        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch(Exception exception) {
            exception.printStackTrace();
        }
    }

    private void __setExposureTimeRangeAndValue() {

        double d_exposureMax = 0.0;
        double d_exposureMin = 0.0;
        double d_exposureDefault = 0.0;
        boolean b_exposureIsImp = false;

        try {

            b_exposureIsImp = m_device.ExposureTime.isImplemented();
            if(b_exposureIsImp) {
                d_exposureMax = m_device.ExposureTime.getMax();
                d_exposureMin = m_device.ExposureTime.getMin();
                d_exposureDefault = m_device.ExposureTime.get();
            }

        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch(Exception exception) {
            exception.printStackTrace();
        }

        // set exposure value from camera's param
        m_exposureSeekBar.setRange((float)d_exposureMin, (float)d_exposureMax);
        m_exposureSeekBar.setPrecision(1);
        m_exposureSeekBar.setDefaultValue((float)d_exposureDefault);
        m_exposureSeekBar.setEnabled(b_exposureIsImp);

    }

    private void __setGainRangeAndValue() {

        double d_gainMax = 0.0;
        double d_gainMin = 0.0;
        double d_gainDefault = 0.0;
        boolean b_gainIsImp = false;
        try {

            b_gainIsImp = m_device.Gain.isImplemented();
            if(b_gainIsImp) {
                d_gainMin = m_device.Gain.getMin();
                d_gainMax = m_device.Gain.getMax();
                d_gainDefault = m_device.Gain.get();
            }

        } catch (ExceptionSet exceptionSet) {

            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);

        } catch(Exception exception) {
            exception.printStackTrace();
        }

        // set gain value from camera's param
        m_gainSeekBar.setRange((float)d_gainMin, (float)d_gainMax);
        m_gainSeekBar.setPrecision(10);
        m_gainSeekBar.setDefaultValue(d_gainDefault);
        m_gainSeekBar.setEnabled(b_gainIsImp);

    }

    private void __setExpectedGrayRangeAndValue() {
        long l_expectedMax = 0;
        long l_expectedMin = 0;
        long l_expectedDefault = 0;
        boolean exposureIsImp = false;
        try {
            exposureIsImp = m_device.ExpectedGrayValue.isImplemented();
            if(exposureIsImp) {
                l_expectedMax = m_device.ExpectedGrayValue.getMax();
                l_expectedMin = m_device.ExpectedGrayValue.getMin();
                l_expectedDefault = m_device.ExpectedGrayValue.get();
            }
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } catch(Exception exception) {
            exception.printStackTrace();
        }
        m_expectedGraySeekBar.setPrecision(1);
        m_expectedGraySeekBar.setRange((float)l_expectedMin, (float)l_expectedMax);
        m_expectedGraySeekBar.setDefaultValue((float)l_expectedDefault);
        m_expectedGraySeekBar.setEnabled(exposureIsImp);
    }



    public void setEnable(boolean enable) {
        m_exposureSpinner.setEnabled(enable);
        m_exposureSeekBar.setEnabled(enable);
        m_gainSpinner.setEnabled(enable);
        m_gainSeekBar.setEnabled(enable);
        m_expectedGraySeekBar.setEnabled(enable);
    }

    @Override
    public void clearFragment()
    {
        if(m_updateExposureTimer != null) {
            m_updateExposureTimer.cancel();
            m_updateExposureTimer = null;
        }

        if(m_updateExposureTimerTask != null) {
            m_updateExposureTimerTask.cancel();
            m_updateExposureTimerTask = null;
        }

        if(m_updateGainTimer != null) {
            m_updateGainTimer.cancel();
            m_updateGainTimer = null;
        }

        if(m_updateGainTimerTask != null) {
            m_updateGainTimerTask.cancel();
            m_updateGainTimerTask = null;
        }

        // remove all message in handler deque
        if(m_msgHandler != null) {
            m_msgHandler.removeCallbacksAndMessages(null);
        }

        super.clearFragment();
    }

    private void startExposureTimer() {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        if(m_updateExposureTimer == null) {
            m_updateExposureTimer = new Timer();
        }

        // setup exposure timer to check if exposure value changed and update latest value to seek bar
        if(m_updateExposureTimerTask == null) {

            m_updateExposureTimerTask = new TimerTask() {
                @Override
                public void run() {

                    try {
                        Message msg = Message.obtain();
                        if(m_device.ExposureAuto.get() == EnumDefineSet.AutoEntry.OFF.getValue()) {
                            msg.what = EXPOSURE_STATUS_CHANGED;
                        } else {
                            msg.what = EXPOSURE_VALUE_CHANGED;
                        }
                        msg.obj = m_device.ExposureTime.get();
                        m_msgHandler.sendMessage(msg);
                    } catch(ExceptionSet.OffLine offLine) {

                        // when device is offline, stop timer and timer task
                        if(m_updateExposureTimer != null) {
                            m_updateExposureTimer.cancel();
                            m_updateExposureTimer = null;
                        }

                        if(m_updateExposureTimerTask != null) {
                            m_updateExposureTimerTask.cancel();
                            m_updateExposureTimerTask = null;
                        }

                    } catch(ExceptionSet exceptionSet) {
                        exceptionSet.printStackTrace();
                        Message msg = Message.obtain();
                        msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
                        msg.obj = exceptionSet.toString();
                        m_threadShareObj.m_threadHandler.sendMessage(msg);

                        // when catch exception set, stop timer and timer task
                        if(m_updateExposureTimer != null) {
                            m_updateExposureTimer.cancel();
                            m_updateExposureTimer = null;
                        }

                        if(m_updateExposureTimerTask != null) {
                            m_updateExposureTimerTask.cancel();
                            m_updateExposureTimerTask = null;
                        }

                    } catch(Exception exception) {
                        exception.printStackTrace();
                    }
                }
            };
            m_updateExposureTimer.schedule(m_updateExposureTimerTask, TIMER_DELAY_TIME, TIMER_RUN_TIME);
        }
    }

    private void startGainTimer() {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        // setup gain timer to check if gain value changed and update latest value to seek bar
        if(m_updateGainTimer == null && m_updateGainTimerTask == null) {
            m_updateGainTimer = new Timer();
            m_updateGainTimerTask = new TimerTask() {
                @Override
                public void run() {

                    try {
                        Message msg = Message.obtain();
                        if(m_device.GainAuto.get() == EnumDefineSet.AutoEntry.OFF.getValue()) {
                            msg.what = GAIN_STATUS_CHANGED;
                        } else {
                            msg.what = GAIN_VALUE_CHANGED;
                        }
                        msg.obj = m_device.Gain.get();
                        m_msgHandler.sendMessage(msg);
                    } catch(ExceptionSet.OffLine offLine) {
                        // when device is offline stop timer and timer task
                        if(m_updateGainTimer != null)
                        {
                            m_updateGainTimer.cancel();
                            m_updateGainTimer = null;
                        }

                        if(m_updateGainTimerTask != null) {
                            m_updateGainTimerTask.cancel();
                            m_updateGainTimerTask = null;
                        }

                    } catch (ExceptionSet exceptionSet) {
                        exceptionSet.printStackTrace();
                        Message msg = Message.obtain();
                        msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
                        msg.obj = exceptionSet.toString();
                        m_threadShareObj.m_threadHandler.sendMessage(msg);

                        // when catch exception set, stop timer and timer task
                        if(m_updateGainTimer != null)
                        {
                            m_updateGainTimer.cancel();
                            m_updateGainTimer = null;
                        }

                        if(m_updateGainTimerTask != null) {
                            m_updateGainTimerTask.cancel();
                            m_updateGainTimerTask = null;
                        }

                    } catch(Exception exception) {
                        exception.printStackTrace();
                        Message msg = Message.obtain();
                        msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_C.getValue();
                        msg.obj = exception.toString();
                        m_threadShareObj.m_threadHandler.sendMessage(msg);
                    }
                }
            };
            m_updateGainTimer.schedule(m_updateGainTimerTask, TIMER_DELAY_TIME, TIMER_RUN_TIME);
        }
    }

    private void setupMessageHandler() {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        m_msgHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {

                if(msg.obj == null) {
                    return false;
                }

                switch(msg.what) {
                    case GAIN_STATUS_CHANGED:

                        if(m_gainSpinner != null) {
                            setGainSelectItem(OFF);
                            m_gainSpinner.setEnabled(true);
                        }

                    case GAIN_VALUE_CHANGED:

                        if(m_gainSeekBar != null) {
                            m_gainSeekBar.setDefaultValue((Double) msg.obj);
                        }
                        break;

                    case EXPOSURE_STATUS_CHANGED:

                        if(m_exposureSpinner != null) {
                            setExposureSelectItem(OFF);
                            m_exposureSpinner.setEnabled(true);
                        }

                    case EXPOSURE_VALUE_CHANGED:

                        if(m_exposureSeekBar != null) {
                            m_exposureSeekBar.setDefaultValue((Double) msg.obj);
                        }
                        break;

                    default:break;
                }

                return false;
            }
        });
    }

    private void setGainSelectItem(final int i) {
        m_gainSpinner.post(new Runnable() {
            @Override
            public void run() {
                m_gainSpinner.setSelection(i);
            }
        });
    }

    private void setExposureSelectItem(final int i) {
        m_exposureSpinner.post(new Runnable() {
            @Override
            public void run() {
                m_exposureSpinner.setSelection(i);
            }
        });
    }
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        m_view = inflater.inflate(R.layout.fragment_exposure_gain, container, false);

        return m_view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // register auto exposure status changed event
        final Spinner m_exposureSpinner = m_view.findViewById(R.id.exposure_spinner);
        m_exposureSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // change auto exposure status
                onExposureModeChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        // register exposure value changed event
        final SeekBarValue m_exposureSeekBar = m_view.findViewById(R.id.exposure_time_seek_bar);
        m_exposureSeekBar.setOnValueChangedListener(new SeekBarValue.OnValueChangedListener() {
            @Override
            public void onSeekBarValueChanged(double value) {
                // change exposure value
                onExposureValueChanged(value);
            }
        });

        // register auto gain status changed event
        final Spinner m_gainSpinner = m_view.findViewById(R.id.gain_spinner);
        m_gainSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // change auto gain status
                onGainModeChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        // register gain value changed event
        final SeekBarValue m_gainSeekBar = m_view.findViewById(R.id.gain_seek_bar);
        m_gainSeekBar.setOnValueChangedListener(new SeekBarValue.OnValueChangedListener() {
            @Override
            public void onSeekBarValueChanged(double value) {
                // change gain value
                onGainValueChanged(value);
            }
        });

        // register expected gray value changed event
        final SeekBarValue expectedGraySeekBar = m_view.findViewById(R.id.expected_gray_seek_bar);
        expectedGraySeekBar.setOnValueChangedListener(new SeekBarValue.OnValueChangedListener() {
            @Override
            public void onSeekBarValueChanged(double value) {
                // change expected gray value
                onExpectedGrayValueChanged((long)value);
            }
        });

        // before init ui, setup message handler
        setupMessageHandler();
        initUI();
    }
}
