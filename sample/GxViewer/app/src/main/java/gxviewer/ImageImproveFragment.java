package gxviewer;

import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;

import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;
import galaxy.GxIJNI;
import galaxy.Utility;

public class ImageImproveFragment extends BaseSettingFragment {

    private View m_view;                        ///< keep view obj

    private GxIJNI.ProcessParam m_processParam;  ///< image process param

    private Switch m_colorCorrectSwitch;
    private Switch m_gammaSwitch;
    private Switch m_contrastSwitch;
    private Switch m_mirrorSwitch;
    private SeekBarValue m_gammaSeekBar;
    private SeekBarValue m_contrastSeekBar;
    private Spinner m_mirrorSpinner;

    private static final double GAMMA_MIN    = 0.1;
    private static final double GAMMA_MAX    = 10.0;
    private static final long   CONTRAST_MIN = -50;
    private static final long   CONTRAST_MAX = 100;

    /**
     * brief  set image improve param
     */
    public ImageImproveFragment() {
        if(m_processParam == null) {
            m_processParam = new GxIJNI.ProcessParam();
        }
    }

    /**
     * brief  update param to image process thread
     */
    private void updateParam() {

        // m_threadShareObj is null return this function
        if(m_threadShareObj == null) {
            return;
        }

        Message msg = Message.obtain();
        msg.what = MainActivity.HandlerMessage.UPDATE_IMAGE_PROCESSING_PARAM.getValue();
        msg.obj = m_processParam;

        // send image process param by available message handler
        if(m_threadShareObj.m_acquireByBitmapRunnable != null) {
            m_threadShareObj.m_acquireByBitmapRunnable.m_msgHandler.sendMessage(msg);
        }
        if(m_threadShareObj.m_acquireBySurfaceRunnable != null) {
            m_threadShareObj.m_acquireBySurfaceRunnable.m_msgHandler.sendMessage(msg);
        }
        if(m_threadShareObj.m_imageProcessRunnable != null) {
            m_threadShareObj.m_imageProcessRunnable.m_msgHandler.sendMessage(msg);
        }
    }

    /**
     * brief init ui by default param
     */
    public void initUI() {

        if(m_view == null) {
            return;
        }
        // get param from camera, and set param to UI
        // get all ui obj
        m_colorCorrectSwitch = m_view.findViewById(R.id.color_correct_switch);
        m_gammaSwitch = m_view.findViewById(R.id.gamma_switch);
        m_contrastSwitch = m_view.findViewById(R.id.contrast_switch);
        m_mirrorSwitch = m_view.findViewById(R.id.mirror_switch);
        m_gammaSeekBar = m_view.findViewById(R.id.gamma_value);
        m_contrastSeekBar = m_view.findViewById(R.id.contrast_value);
        m_mirrorSpinner = m_view.findViewById(R.id.mirror_spinner);

        m_gammaSeekBar.setPrecision(100);
        m_gammaSeekBar.setRange(GAMMA_MIN, GAMMA_MAX);
        m_contrastSeekBar.setPrecision(1);
        m_contrastSeekBar.setRange(CONTRAST_MIN, CONTRAST_MAX);

        // setup mirror spinner
        List<String> mirrorList = new ArrayList<>();
        mirrorList.add("Horizontal");
        mirrorList.add("Vertical");

        ArrayAdapter<String> mirrorAdapter = new ArrayAdapter<>(m_view.getContext()
                , android.R.layout.simple_list_item_1
                , mirrorList);
        m_mirrorSpinner.setAdapter(mirrorAdapter);

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            setDisable();
            return;
        }

        if(!m_threadShareObj.getIsStart()) {
            setDisable();
            return;
        }

        // update ui
        m_gammaSwitch.setChecked(m_threadShareObj.m_imageImproveParam.getGammaStatus());
        m_contrastSwitch.setChecked(m_threadShareObj.m_imageImproveParam.getContrastStatus());
        m_mirrorSwitch.setChecked(m_threadShareObj.m_imageImproveParam.getMirrorStatus());
        m_colorCorrectSwitch.setChecked(m_threadShareObj.m_imageImproveParam.getColorCorrectStatus());

        try {
            m_colorCorrectSwitch.setEnabled(m_device.ColorCorrectionParam.isImplemented());
            m_gammaSwitch.setEnabled(m_device.GammaParam.isImplemented());
            m_gammaSeekBar.setEnabled(m_device.GammaParam.isImplemented());
            m_contrastSwitch.setEnabled(m_device.ContrastParam.isImplemented());
            m_contrastSeekBar.setEnabled(m_device.ContrastParam.isImplemented());
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }


        m_mirrorSpinner.setEnabled(m_mirrorSwitch.isChecked());

        // init mirror status
        m_processParam.setMirror(m_threadShareObj.m_imageImproveParam.getMirrorStatus());

        int mirrorDir = m_threadShareObj.m_imageImproveParam.getMirrorDirection().getValue();

        final int HORIZONTAL_MIRROR = 0;
        final int VERTICAL_MIRROR = 1;

        if(mirrorDir == HORIZONTAL_MIRROR) {
            m_processParam.setMirrorMode(EnumDefineSet.ImageMirrorMode.HORIZONTAL_MIRROR);
        } else if(mirrorDir == VERTICAL_MIRROR) {
            m_processParam.setMirrorMode(EnumDefineSet.ImageMirrorMode.VERTICAL_MIRROR);
        }

        // init color correct status
        try {
            if(m_threadShareObj.m_imageImproveParam.getColorCorrectStatus()) {
                long value = m_device.ColorCorrectionParam.get();
                m_processParam.setColorCorrectionParam(value);
            } else {
                m_processParam.setColorCorrectionParam(0);
            }
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }

        // init gamma status
        try {
            if(m_threadShareObj.m_imageImproveParam.getGammaStatus()) {

                m_processParam.setGammaLut(Utility.getGammaLut(m_threadShareObj.m_imageImproveParam.getGammaValue()));

            } else {
                m_processParam.setGammaLut(null);
            }
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }

        // init contrast status
        try {
            if(m_threadShareObj.m_imageImproveParam.getContrastStatus()) {

                m_processParam.setContrastLut(Utility.getContrastLut((int) m_threadShareObj.m_imageImproveParam.getContrastValue()));

            } else {
                m_processParam.setContrastLut(null);
            }
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }

        try {
            if(m_device.GammaParam.isImplemented()) {
                m_gammaSeekBar.setDefaultValue(m_threadShareObj.m_imageImproveParam.getGammaValue());
            }

            if(m_device.ContrastParam.isImplemented()) {
                m_contrastSeekBar.setDefaultValue(m_threadShareObj.m_imageImproveParam.getContrastValue());
            }

            m_mirrorSpinner.post(new Runnable() {
                @Override
                public void run() {
                    m_mirrorSpinner.setSelection(m_threadShareObj.m_imageImproveParam.getMirrorDirection().getValue());
                }
            });
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } catch(Exception exception) {
            exception.printStackTrace();
        }
        m_gammaSeekBar.setEnabled(m_gammaSwitch.isChecked());
        m_contrastSeekBar.setEnabled(m_contrastSwitch.isChecked());
    }

    private void setDisable() {
        m_colorCorrectSwitch.setEnabled(false);
        m_gammaSwitch.setEnabled(false);
        m_gammaSeekBar.setEnabled(false);
        m_contrastSeekBar.setEnabled(false);
        m_contrastSwitch.setEnabled(false);
        m_mirrorSwitch.setEnabled(false);
        m_mirrorSpinner.setEnabled(false);
    }

    /**
     * brief enable or disable color correct function
     * param isChecked[in]   true: enable, false: disable
     */
    private void onColorCorrectStatusChanged(boolean isChecked) {

        // if m_device is null or m_threadShareObj is null return this function
        if(m_device == null || m_threadShareObj == null) {
            return;
        }

        // check weather support color correct set
        try {
            if(!m_device.ColorCorrectionParam.isImplemented()) {
                return;
            }
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
        }

        m_threadShareObj.m_imageImproveParam.setEnableColorCorrect(isChecked);
        if(isChecked) {
            try {
                long value = m_device.ColorCorrectionParam.get();
                m_processParam.setColorCorrectionParam(value);
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
                m_colorCorrectSwitch.setChecked(false);
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
                msg.obj = exceptionSet.toString();
                m_threadShareObj.m_threadHandler.sendMessage(msg);
            } catch(Exception exception) {
                exception.printStackTrace();
            }
        } else {
            m_processParam.setColorCorrectionParam(0);
        }

        updateParam();
    }

    /**
     * brief  enable or disable gamma function
     * param isChecked[in]   true: enable, false: disable
     */
    private void onGammaStatusChanged(boolean isChecked) {

        // m_threadShareObj is null return this function
        if(m_threadShareObj == null) {
            return;
        }

        // when disable gamma function, disable gamma seek bar
        // when enable gamma function, enable gamma seek bar
        m_threadShareObj.m_imageImproveParam.setEnableGamma(isChecked);
        m_gammaSeekBar.setEnabled(isChecked);
        if(!isChecked) {
            m_processParam.setGammaLut(null);
        } else {
            try {
                m_processParam.setGammaLut(Utility.getGammaLut(m_gammaSeekBar.getValue()));
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
                msg.obj = exceptionSet.toString();
                m_threadShareObj.m_threadHandler.sendMessage(msg);
            }
        }
        updateParam();
    }

    /**
     * brief  set gamma value
     * param value[in]   value of gamma
     */
    private void onGammaValueChanged(float value) {

        // m_threadShareObj is null return this function
        if(m_threadShareObj == null) {
            return;
        }

        m_threadShareObj.m_imageImproveParam.setGammaValue(value);
        try {
            m_processParam.setGammaLut(Utility.getGammaLut(value));
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }
        updateParam();
    }

    /**
     * brief  enable or disable contrast function
     * param isChecked[in]  true: enable, false: disable
     */
    private void onContrastStatusChanged(boolean isChecked) {

        // m_threadShareObj is null return this function
        if(m_threadShareObj == null) {
            return;
        }

        // when disable contrast function, disable contrast seek bar
        // when enable contrast function, enable contrast seek bar
        m_threadShareObj.m_imageImproveParam.setEnableContrast(isChecked);
        m_contrastSeekBar.setEnabled(isChecked);
        if(!isChecked) {
            m_processParam.setContrastLut(null);
        } else {
            try {
                m_processParam.setContrastLut(Utility.getContrastLut((int) m_contrastSeekBar.getValue()));
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
                msg.obj = exceptionSet.toString();
                m_threadShareObj.m_threadHandler.sendMessage(msg);
            }
        }
        updateParam();
    }

    /**
     * brief  set contrast value
     * param value[in]  value to set
     */
    private void onContrastValueChanged(int value) {

        // m_threadShareObj is null return this function
        if(m_threadShareObj == null) {
            return;
        }

        m_threadShareObj.m_imageImproveParam.setContrastValue(value);
        try {
            m_processParam.setContrastLut(Utility.getContrastLut(value));
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_B.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }
        updateParam();
    }

    /**
     * brief  enable or disable mirror function
     * param isChecked[in]    true: enable, false: disable
     */
    private void onMirrorStatusChanged(boolean isChecked) {

        // m_threadShareObj is null return this function
        if(m_threadShareObj == null) {
            return;
        }

        m_threadShareObj.m_imageImproveParam.setEnableMirror(isChecked);
        m_processParam.setMirror(isChecked);
        m_mirrorSpinner.setEnabled(isChecked);
        if(m_threadShareObj.m_imageImproveParam.getMirrorDirection() == ImageImproveParam.MirrorDirection.HORIZONTAL) {
            m_processParam.setMirrorMode(EnumDefineSet.ImageMirrorMode.HORIZONTAL_MIRROR);
        } else if(m_threadShareObj.m_imageImproveParam.getMirrorDirection() == ImageImproveParam.MirrorDirection.VERTICAL) {
            m_processParam.setMirrorMode(EnumDefineSet.ImageMirrorMode.VERTICAL_MIRROR);
        }
        updateParam();
    }

    /**
     * brief select mirror direction
     * param index[in] select horizontal or vertical
     */
    private void onMirrorDirectionChanged(int index) {

        // m_threadShareObj is null return this function
        if(m_threadShareObj == null) {
            return;
        }

        m_threadShareObj.m_imageImproveParam.setMirrorDir(ImageImproveParam.MirrorDirection.valueOf(index));
        if(index == 0) {
            m_processParam.setMirrorMode(EnumDefineSet.ImageMirrorMode.HORIZONTAL_MIRROR);
        } else if(index == 1) {
            m_processParam.setMirrorMode(EnumDefineSet.ImageMirrorMode.VERTICAL_MIRROR);
        }
        updateParam();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        m_view = inflater.inflate(R.layout.fragment_image_processing, container, false);
        return m_view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        initUI();

        // register color correct status changed event
        m_colorCorrectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onColorCorrectStatusChanged(isChecked);
            }
        });

        m_gammaSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onGammaStatusChanged(isChecked);
            }
        });

        m_contrastSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onContrastStatusChanged(isChecked);
            }
        });

        m_mirrorSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onMirrorStatusChanged(isChecked);
            }
        });

        m_mirrorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onMirrorDirectionChanged(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        m_gammaSeekBar.setOnValueChangedListener(new SeekBarValue.OnValueChangedListener() {
            @Override
            public void onSeekBarValueChanged(double value) {
                onGammaValueChanged((float)value);
            }
        });

        m_contrastSeekBar.setOnValueChangedListener(new SeekBarValue.OnValueChangedListener() {
            @Override
            public void onSeekBarValueChanged(double value) {
                onContrastValueChanged((int) value);
            }
        });
    }
}
