package gxviewer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

import galaxy.Device;
import galaxy.DeviceManager;
import galaxy.ExceptionSet;
import galaxy.RawImage;


public class MainActivity extends AppCompatActivity implements SlidingPaneLayout.PanelSlideListener
, SurfaceHolder.Callback
, View.OnClickListener, DeviceManager.OnUpdateDeviceListFinishedListener {

    private SettingListFragment m_settingFragment;            ///< setting list fragment
    private SlidingPaneLayout m_slidingPaneLayout;            ///< main view layout

    private boolean m_isRecentKeyPressed = false;             ///< used for divide home key and recent key
    private FloatingActionButton m_saveImageBtn;              ///< save image floating button

    protected Timer m_refreshFpsTimer = null;                 ///< refresh fps and frame information timer
    protected TimerTask m_refreshFpsTimerTask;                ///< refresh fps and frame information timer task

    private BroadcastReceiver m_usbBroadcastReceiver;         ///< usb broadcast receiver
    private BroadcastReceiver m_homeKeyBroadcastReceiver;     ///< key broadcast receiver

    private ThreadShareObj m_threadShareObj;                  ///< share obj among threads

    Device m_device = null;                                   ///< device obj

    private final int ENUM_DEVICE_TIMEOUT = 1000;

    private final String MOD_NAME_LABEL  = "ModeName:";
    private final String FRAME_NUM_LABEL = "FrameNum:";
    private final String ACQ_FPS_LABEL   = "AcqFPS:";

    private final String ERROR_LABEL   = "Error";
    private final String WARNING_LABEL = "Warning";

    private boolean m_isGetMaxSize = false;

    static final String SETTING_FRAGMENT_TAG = "setting_fragment_tag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.hide();
        }

        // request permission for write image to external storage, if no permission
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // required permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
        }

    }

    public void findCreateSettingListFragment() {
        m_settingFragment = (SettingListFragment) getSupportFragmentManager().findFragmentByTag(SETTING_FRAGMENT_TAG);


        if(m_settingFragment == null) {
            m_settingFragment = new SettingListFragment();
        } else {
            m_settingFragment.onDestroy();
        }

    }
    /**
     * brief  after onCreate() or back from recent app, execute this func
     */
    @Override
    protected void onStart() {
        super.onStart();

        m_isRecentKeyPressed = false;

        // share objects among threads
        if(m_threadShareObj == null) {
            m_threadShareObj = new ThreadShareObj();
        }

        // get Surface Holder obj, when surface view created, send message to display thread
        SurfaceView sv = findViewById(R.id.surface_view);
        m_threadShareObj.m_surfaceHolder = sv.getHolder();
        m_threadShareObj.m_surfaceHolder.addCallback(this);

        // get application context
        m_threadShareObj.setContext(getApplicationContext());

        // register save image button event, and disable it
        m_saveImageBtn = findViewById(R.id.save_image_btn);
        m_saveImageBtn.setOnClickListener(this);
        m_saveImageBtn.setBackgroundTintList(createColorStateList(Color.BLUE, Color.GRAY, Color.BLUE, Color.GRAY));
        m_saveImageBtn.setEnabled(false);

        // register SlidePanelLayout event
        m_slidingPaneLayout = findViewById(R.id.sliding_pane_layout);
        m_slidingPaneLayout.setPanelSlideListener(this);

        findCreateSettingListFragment();

        if(m_settingFragment == null) {
            m_settingFragment = new SettingListFragment();
        }
        m_settingFragment.setDeviceAndThreadShareObj(m_device, m_threadShareObj);

        // register usb broadcast service
        registerUsbBroadcast();
        // register home key broadcast service
        registerHomeKeyBroadcast();

        // process child thread message here
        threadMessageReceiver();

        // setup fps calculator and refresh timer
        startFpsCalculator();

        if(!m_threadShareObj.getIsStart()) {

            // get device manager instance, then register update device list finished listener
            // after that start update device list
            try {
                DeviceManager.getInstance().setOnUpdateDeviceListFinishedListener(this);
                DeviceManager.getInstance().startUpdateDeviceList(getApplicationContext(), ENUM_DEVICE_TIMEOUT);
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
                showMessageDialog(ERROR_LABEL, exceptionSet.toString());
            }

        }
        // update ui information
        updateUI();
    }

    /**
     * brief    when update device list is complete, invoke this override method
     *          this method overrides the onUpdateDeviceListFinished method in the OnUpdateDeviceListFinishedListener interface
     *          in DeviceManager, so you need to implements DeviceManager.OnUpdateDeviceListFinishedListener
     */
    @Override
    public void onUpdateDeviceListFinished() {

        if(m_threadShareObj == null) {
            return;
        }
        // get device info list from device manager
        try {
            // device list
            List<DeviceManager.DeviceInfo> deviceInfoList =
                    DeviceManager.getInstance().getDeviceInfoList();

            // device info list is null or size is zero, send level b message
            if(deviceInfoList == null || deviceInfoList.size() == 0) {
                // clear frame id
                m_threadShareObj.m_calculateFps.clearFrameId();

                // send warning message
                Message msg = Message.obtain();
                msg.what = HandlerMessage.ERROR_LEVEL_B.getValue();
                msg.obj = "No available device found";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                return;
            }

            // open first device in device info list
            Device device = openDevice(deviceInfoList.get(0));

            if(device == null) {

                // clear frame id
                m_threadShareObj.m_calculateFps.clearFrameId();

                // send warning message
                Message msg = Message.obtain();
                msg.what = HandlerMessage.ERROR_LEVEL_B.getValue();
                msg.obj = "Open device failed";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                return;
            }

            // reference device obj
            m_device = device;

            // create setting fragment
            if(m_settingFragment == null) {
                m_settingFragment = new SettingListFragment();
            }
            m_settingFragment.setDeviceAndThreadShareObj(device, m_threadShareObj);

            // stream on
            device.streamOn();
            // start image acquisition
            startAcquisitionImage();
            // update ui
            updateUI();

        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message msg = Message.obtain();
            msg.what = HandlerMessage.ERROR_LEVEL_A.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }
    }

    /**
     * brief  select one acquisition interface
     *        can only select one acq obj to acquisition image
     */
    private void startAcquisitionImage() {

        m_threadShareObj.m_acquisitionImage = new AcquisitionByGetBitmap(m_device, m_threadShareObj);           // acquisition by getBitmap interface
//        m_threadShareObj.m_acquisitionImage = new AcquisitionByGetImageBySurface(m_device, m_threadShareObj);   // acquisition by getImageBySurface interface
//        m_threadShareObj.m_acquisitionImage = new AcquisitionByGetRawImage(m_device, m_threadShareObj);         // acquisition by getRawImage interface

        // start acquisition image thread
        m_threadShareObj.m_acquisitionImage.start();


    }

    /**
     * brief    when update device list finished, invoke this method
     *          to open device by device info,
     *          on succeed record serial number from device info, then read image improve param from device
     * param    deviceInfo    device info of device to open
     * return   on succeed return device, on failed return null
     */
    private Device openDevice(DeviceManager.DeviceInfo deviceInfo) {

        Device device = null;
        try {

            if(deviceInfo == null) {
                return null;
            }

            // open device
            device = DeviceManager.getInstance().openDeviceByIndex(deviceInfo.getIndex());

            if(device == null) {
                return null;
            }

            // read device serial number
            m_threadShareObj.setDeviceSerialNumber(deviceInfo.getSN());
            // read image improve param as default param
            m_threadShareObj.m_imageImproveParam = readImageImproveParam(device);

            // disable offline status
            m_threadShareObj.setOffLineStatus(false);

        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            showMessageDialog(ERROR_LABEL, exceptionSet.toString());
        } catch(Exception exception) {
            exception.printStackTrace();
        }
        return device;
    }

    @Override
    protected void onStop() {

        if(m_threadShareObj.getIsStart()) {

            try {
                // stop acquisition image thread and display thread etc
                m_threadShareObj.m_acquisitionImage.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // when app is stop, clear all deque
            m_threadShareObj.clearAllDeque();

            // stream off device
            try {
                if(m_device != null) {
                    m_device.streamOff();
                }
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
            }

            // when stream off failed, still need to close device
            try {
                if(m_device != null) {
                    m_device.closeDevice();
                }
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
            }

            m_device = null;
        }

        // unregister broad cast receiver
        unregisterReceiver(m_usbBroadcastReceiver);
        unregisterReceiver(m_homeKeyBroadcastReceiver);

        if(m_settingFragment != null) {
            m_settingFragment.clearFragment();
            m_settingFragment.onDestroy();
        }
        if(m_slidingPaneLayout != null) {
            m_slidingPaneLayout.closePane();
            m_slidingPaneLayout = null;
        }

        // remove all message in handler deque
        if(m_threadShareObj.m_threadHandler != null) {
            m_threadShareObj.m_threadHandler.removeCallbacksAndMessages(null);
            m_threadShareObj.m_threadHandler = null;
        }

        super.onStop();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        System.exit(0);
    }


    /**
     * brief update ui by device status
     */
    public void updateUI() {

        if(m_threadShareObj.getIsStart()) {
            // read camera's model info, then show it on ui
            try {
                if(m_device != null) {
                    String modName = m_device.DeviceModelName.get();
                    TextView modNameText = findViewById(R.id.mod_name);
                    modNameText.setText(String.format("%s%s", MOD_NAME_LABEL, modName));
                }
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
                showMessageDialog(WARNING_LABEL, "Read device mode name failed!");
            } catch(Exception exception) {
                exception.printStackTrace();
            }

            FloatingActionButton saveBtn = findViewById(R.id.save_image_btn);
            saveBtn.setEnabled(true);



        } else {

            // clear mode info
            TextView modText = findViewById(R.id.mod_name);
            modText.setText(MOD_NAME_LABEL);

            // clear frame Num info
            TextView frameText = findViewById(R.id.frame_num);
            frameText.setText(FRAME_NUM_LABEL + " 0");

            // clear frame Num info
            TextView fpsText = findViewById(R.id.acq_fps);
            fpsText.setText(ACQ_FPS_LABEL + " 0.00");

            // disable save image button
            FloatingActionButton saveBtn = findViewById(R.id.save_image_btn);
            saveBtn.setEnabled(false);

            // close setting list
            if(m_slidingPaneLayout != null) {
                m_slidingPaneLayout.closePane();
            }
        }
    }

    /**
     * brief     start fps calculator task
     */
    private void startFpsCalculator() {
        if(m_refreshFpsTimer == null) {
            m_threadShareObj.m_calculateFps = new CalculateFps();
            m_refreshFpsTimer = new Timer();
            m_refreshFpsTimerTask = new TimerTask() {
                @Override
                public void run() {
                    // send refresh frame info message to ui
                    if(m_threadShareObj.m_threadHandler != null) {
                        Message msg = Message.obtain();
                        msg.what = MainActivity.HandlerMessage.REFRESH_FPS.getValue();
                        m_threadShareObj.m_threadHandler.sendMessage(msg);
                    }
                }
            };

            final int REFRESH_FPS_INTERVAL = 1000;
            final int REFRESH_FPS_DELAY = 0;
            m_refreshFpsTimer.schedule(m_refreshFpsTimerTask, REFRESH_FPS_DELAY, REFRESH_FPS_INTERVAL);
        }
    }

    private @NonNull ImageImproveParam readImageImproveParam(Device dev) {

        ImageImproveParam imgImproveParam = new ImageImproveParam();

        if(dev == null) {
            return imgImproveParam;
        }

        imgImproveParam.setEnableColorCorrect(false);
        imgImproveParam.setEnableContrast(false);
        imgImproveParam.setEnableGamma(false);
        imgImproveParam.setEnableMirror(false);
        try {
            if(dev.ContrastParam.isImplemented()) {
                imgImproveParam.setContrastValue(dev.ContrastParam.get());
            }
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
        }

        try {
            if(dev.GammaParam.isImplemented()) {
                imgImproveParam.setGammaValue(dev.GammaParam.get());
            }
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
        }

        return imgImproveParam;
    }

    /**
     * brief process message
     */
    private void threadMessageReceiver() {

        if(m_threadShareObj.m_threadHandler != null) {
            return;
        }

        m_threadShareObj.m_threadHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {

                switch(HandlerMessage.valueOf(msg.what)) {

                    case REFRESH_FPS:// refresh fps and frame num

                        float fps = m_threadShareObj.m_calculateFps.getFps();
                        long frameId = m_threadShareObj.m_calculateFps.getFrameId();
                        TextView frameText = findViewById(R.id.frame_num);
                        frameText.setText(String.format(Locale.CHINA, "%s %d", FRAME_NUM_LABEL, frameId));
                        TextView fpsText = findViewById(R.id.acq_fps);
                        fpsText.setText(String.format(Locale.CHINA,"%s %.2f", ACQ_FPS_LABEL, fps));
                        break;

                    case RESIZE_SURFACE:// resize surface view's size
                        Rect surRect = (Rect) msg.obj;
                        int width = surRect.width();
                        int height = surRect.height();
                        if(m_threadShareObj.getSurfaceMaxHeight() < height) {
                            float m = (float) m_threadShareObj.getSurfaceMaxHeight() / (float) height;
                            height = m_threadShareObj.getSurfaceMaxHeight();
                            width = (int) (m * width);
                        }
                        if(m_threadShareObj.getSurfaceMaxWidth() < width) {
                            float m = (float) m_threadShareObj.getSurfaceMaxWidth() / (float) width;
                            width = m_threadShareObj.getSurfaceMaxWidth();
                            height = (int) (m * height);
                        }

                        m_threadShareObj.m_surfaceHolderLock.lock();
                        try {
                            m_threadShareObj.m_surfaceHolder.setFixedSize(width, height);
                        } finally {
                            m_threadShareObj.m_surfaceHolderLock.unlock();
                        }
                        break;

                    case SAVE_IMAGE_COMPLETE:// image is saved
                        m_saveImageBtn.setEnabled(true);
                        if(msg.obj != null) {
                            Toast.makeText(MainActivity.this, (String) msg.obj,Toast.LENGTH_SHORT).show();
                        }
                        break;

                    case SAVE_IMAGE_FAILED:// image save failed
                        m_saveImageBtn.setEnabled(true);
                        if(msg.obj != null) {
                            Toast.makeText(MainActivity.this, (String) msg.obj,Toast.LENGTH_SHORT).show();
                        }
                        break;

                    case ERROR_LEVEL_A:// error level A, device error

                        m_threadShareObj.m_threadHandler.removeCallbacksAndMessages(null);
                        if(msg.obj != null) {
                            showMessageDialog(ERROR_LABEL, (String) msg.obj);
                        }
                        // when got level_A error: stop acquisition, stream off, close device
                        // close setting list pane
                        m_slidingPaneLayout.closePane();

                        // lock close device process
                        m_threadShareObj.m_offlineLock.lock();
                        try {
                            // stop acquisition thread
                            if (m_threadShareObj.m_acquisitionImage != null) {
                                try {
                                    m_threadShareObj.m_acquisitionImage.stop();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            if (m_device != null) {
                                // stream off, close device
                                try {
                                    m_device.streamOff();
                                } catch (ExceptionSet exceptionSet) {
                                    exceptionSet.printStackTrace();
                                }

                                // when stream off is failed, still need close device
                                try {
                                    m_device.closeDevice();
                                } catch (ExceptionSet exceptionSet) {
                                    exceptionSet.printStackTrace();
                                }

                                m_device = null;
                                m_threadShareObj.clearSerialNumber();

                            }
                        } finally {
                            m_threadShareObj.m_offlineLock.unlock();
                        }

                        // clear frame id
                        m_threadShareObj.m_calculateFps.clearFrameId();
                        // update ui, clear device information on ui, disable save image button
                        updateUI();
                        break;
                    case ERROR_LEVEL_B:// error level B, setting param error
                        if(msg.obj != null) {
                            showMessageDialog(WARNING_LABEL, (String) msg.obj);
                        }
                        break;

                    case ERROR_LEVEL_C:// error level C, app error
                        if(msg.obj != null) {
                            showMessageDialog(WARNING_LABEL, (String) msg.obj);
                        }
                        break;

                    default:break;
                }

                return false;
            }
        });
    }

    /**
     * brief process home key press broadcast,
     *        if press recent apps key, then touch back app,
     *        will get a home key press broadcast
     */
    private void registerHomeKeyBroadcast() {

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        m_homeKeyBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                assert action != null;
                if(action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                    String reason = intent.getStringExtra("reason");
                    if(reason.equals("homekey")) {
                        if(m_isRecentKeyPressed) {
                            m_isRecentKeyPressed = false;
                            return;
                        }
                        System.exit(0);
                    } else if(reason.equals("recentapps")) {
                        m_isRecentKeyPressed = true;
                    }
                }
            }
        };

        registerReceiver(m_homeKeyBroadcastReceiver, intentFilter);
    }

    /**
     * brief register usb attached and detached broadcast
     */
    private void registerUsbBroadcast() {

        m_usbBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                final int VENDOR_ID = 11170;
                final int PRODUCT_ID = 19797;

                UsbDevice usbDevice = intent.getParcelableExtra("device");

                if(usbDevice == null) {
                    return;
                }
                // check vendor id and product id, check serial number is null
                if(usbDevice.getVendorId() != VENDOR_ID ||
                        usbDevice.getProductId() != PRODUCT_ID ||
                        usbDevice.getSerialNumber() == null) {
                    return;
                }

                String action = intent.getAction();
                if(action == null) {
                    return;
                }

                if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    // if device is on line, ignore this message
                    if(m_threadShareObj.getIsStart()) {
                        return;
                    }

                    // close setting list panel
                    m_slidingPaneLayout.closePane();

                    // if there is no device online, try to open it
                    try {

                        // when device plug in, update device list
                        DeviceManager.getInstance().startUpdateDeviceList(getApplicationContext(), ENUM_DEVICE_TIMEOUT);

                    } catch (ExceptionSet exceptionSet) {
                        exceptionSet.printStackTrace();
                        Message msg = Message.obtain();
                        msg.what = HandlerMessage.ERROR_LEVEL_B.getValue();
                        msg.obj = exceptionSet.toString();
                        m_threadShareObj.m_threadHandler.sendMessage(msg);
                    }
                }
                else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {

                    // check serial number
                    if(usbDevice.getSerialNumber() == null) {
                        return;
                    }

                    // check vendor id and product id, check serial number is null
                    if(usbDevice.getVendorId() != VENDOR_ID ||
                        usbDevice.getProductId() != PRODUCT_ID ||
                        usbDevice.getSerialNumber() == null) {
                        return;
                    }

                    // if detached device is not current work device, ignore this message
                    if(!usbDevice.getSerialNumber().equals(m_threadShareObj.getDeviceSerialNumber())) {
                        return;
                    }

                    // setup off line status
                    m_threadShareObj.setOffLineStatus(true);

                    // lock close device process
                    m_threadShareObj.m_offlineLock.lock();
                    try {
                        // stop acquisition thread
                        try {
                            m_threadShareObj.m_acquisitionImage.stop();
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }

                        if (m_device != null) {
                            // stream off, close device
                            try {
                                m_device.streamOff();
                            } catch (ExceptionSet exceptionSet) {
                                exceptionSet.printStackTrace();
                            }

                            // when stream off is failed, still need close device
                            try {
                                m_device.closeDevice();
                            } catch (ExceptionSet exceptionSet) {
                                exceptionSet.printStackTrace();
                            }

                            m_device = null;
                            m_threadShareObj.clearSerialNumber();

                        }
                    } finally {
                        m_threadShareObj.m_offlineLock.unlock();
                    }

                    // close setting list pane
                    m_slidingPaneLayout.closePane();

                    // clear frame id
                    m_threadShareObj.m_calculateFps.clearFrameId();
                    // update ui, clear device information on ui, disable save image button
                    updateUI();
                }
            }
        };

        final IntentFilter usbDeviceStateFilter = new IntentFilter();
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbDeviceStateFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(m_usbBroadcastReceiver, usbDeviceStateFilter);
    }

    /**
     * brief replace fragment to setting frame layout
     * param fragment[in]    fragment to replace current fragment
     *                    in setting frame layout
     */
    public void replaceFragment(int id, Fragment fragment) {

        if(fragment == null) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        if(fm != null) {
            FragmentTransaction transaction = fm.beginTransaction();
            transaction.replace(id, fragment);
            transaction.commit();
        }
    }

    public void replaceFragment(int id, Fragment fragment, String tag) {

        if(fragment == null) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        if(fm != null) {
            FragmentTransaction transaction = fm.beginTransaction();
            transaction.replace(id, fragment, tag);
            transaction.commit();
        }
    }

    /**
     * brief show a message dialog
     * param title[in]   dialog's title
     * param msg[in]     dialog's message
     */
    private void showMessageDialog(String title, String msg) {

        if(getApplicationContext() == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton("OK", null);
        builder.show();
    }

    // override SlidePanelLayout's event
    @Override
    public void onPanelSlide(@NonNull View view, float v) {


    }

    @Override
    public void onPanelOpened(@NonNull View view) {
        // create setting fragment

        if(m_settingFragment == null) {

            m_settingFragment = new SettingListFragment();

        } else {

            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fm.beginTransaction();
            fragmentTransaction.show(m_settingFragment);
            fragmentTransaction.commit();

        }

        m_settingFragment.setDeviceAndThreadShareObj(m_device, m_threadShareObj);
        replaceFragment(R.id.setting, m_settingFragment, SETTING_FRAGMENT_TAG);
    }

    @Override
    public void onPanelClosed(@NonNull View view) {

        if(m_settingFragment == null) {
            return;
        }

        m_settingFragment.clearFragment();
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.hide(m_settingFragment);
        fragmentTransaction.commit();
        m_settingFragment = null;
    }

    // override SurfaceHolder's callback
    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        // get surface max height and width
        SurfaceView surfaceView = findViewById(R.id.surface_view);

        if(!m_isGetMaxSize) {
            m_threadShareObj.setSurfaceMaxHeight(surfaceView.getHeight());
            m_threadShareObj.setSurfaceMaxWidth(surfaceView.getWidth());
            m_isGetMaxSize = true;
        }


        // get surface holder
        m_threadShareObj.m_surfaceHolder = holder;

        Canvas canvas = holder.lockCanvas();
        if(canvas != null) {
            canvas.drawColor(Color.WHITE);
            holder.unlockCanvasAndPost(canvas);
        }

        m_threadShareObj.setIsSurfaceCreated(true);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if(m_threadShareObj != null) {
            m_threadShareObj.setIsSurfaceCreated(false);
        }
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.save_image_btn:
                // disable save button before try to save image
                v.setEnabled(false);

                // set tryToSaveImage flag
                if(m_threadShareObj != null) {
                    m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.WAIT_FOR_IMAGE);
                }

                break;
            default:break;
        }
    }


    private ColorStateList createColorStateList(int normal, int pressed, int focused, int unable) {
        int[] colors = new int[] { pressed, focused, normal, focused, unable, normal };
        int[][] states = new int[6][];
        states[0] = new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled };
        states[1] = new int[] { android.R.attr.state_enabled, android.R.attr.state_focused };
        states[2] = new int[] { android.R.attr.state_enabled };
        states[3] = new int[] { android.R.attr.state_focused };
        states[4] = new int[] { android.R.attr.state_window_focused };
        states[5] = new int[] {};
        return new ColorStateList(states, colors);
    }


    public enum HandlerMessage {
        DO_NOTHING(0),                       ///< nothing to do
        REFRESH_FPS(1),                      ///< refresh fps and frame number information
        RESIZE_SURFACE(3),                   ///< resize surface view message
        SAVE_IMAGE_COMPLETE(4),              ///< image saved complete message
        SAVE_IMAGE_FAILED(5),                ///< image save failed message
        UPDATE_IMAGE_PROCESSING_PARAM(6),    ///< update image process param
        ERROR_LEVEL_A(7),                    ///< image acquire error
        ERROR_LEVEL_B(8),                    ///< setting error
        ERROR_LEVEL_C(9);                    ///< android os error

        private final int m_value;
        HandlerMessage(int i) {
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
        public static HandlerMessage valueOf(int i) {
            for(HandlerMessage hm : values()) {
                if(hm.getValue() == i) {
                    return hm;
                }
            }
            return DO_NOTHING;
        }
    }
}

/**
 * brief share object among all thread
 */
class ThreadShareObj {

    SurfaceHolder m_surfaceHolder;    ///< SurfaceView's holder
    public Handler m_threadHandler;   ///< message handler to be used for child thread send message to main thread

    private boolean m_isStart   = false;                                          ///< device online flag
    private SaveImageStatus m_tryToSaveImage = SaveImageStatus.DO_NOTHING;        ///< save image flag

    private Context m_context;              ///< main activity context

    AcquisitionImage m_acquisitionImage;    ///< acquisition image manager

    AcquireByBitmapRunnable m_acquireByBitmapRunnable;
    AcquireBySurfaceRunnable m_acquireBySurfaceRunnable;
    AcquireByRawImageRunnable m_acquireByRawImageRunnable;
    ImageProcessRunnable m_imageProcessRunnable;
    DisplayRunnable m_displayRunnable;

    final ReentrantLock m_surfaceHolderLock = new ReentrantLock();

    private int m_surfaceMaxWidth = 0;    ///< max width of surface view
    private int m_surfaceMaxHeight = 0;   ///< max height of surface view

    private String m_deviceSN = " ";      ///< device serial number

    CalculateFps m_calculateFps;

    final ConcurrentLinkedDeque<RawImage> m_processImageLinkedDeque = new ConcurrentLinkedDeque<>();         ///< acquire image Linked Deque
    final ConcurrentLinkedDeque<ImageInfo> m_displayRunnableImageLinkedDeque = new ConcurrentLinkedDeque<>();///< display image Linked Deque
    final ConcurrentLinkedDeque<ImageInfo> m_rawImageLinkedDeque     = new ConcurrentLinkedDeque<>();        ///< raw image linked deque

    private boolean m_isSurfaceCreated = false;
    final ReentrantLock m_offlineLock = new ReentrantLock();

    private boolean m_isOffLine = true;    ///< device plug out status


    ImageImproveParam m_imageImproveParam = new ImageImproveParam();
    ThreadShareObj() {
        m_threadHandler = null;
        m_surfaceHolder = null;
        m_acquireByBitmapRunnable = null;
        m_acquireBySurfaceRunnable = null;
        m_acquireByRawImageRunnable = null;
        m_imageProcessRunnable = null;
        m_displayRunnable = null;
        m_acquisitionImage = null;
    }

    boolean getIsStart() {
        return m_isStart;
    }

    synchronized void setIsStart(boolean isStart) {
        m_isStart = isStart;
    }

    boolean getIsSurfaceCreated() {
        return m_isSurfaceCreated;
    }

    synchronized void setIsSurfaceCreated(boolean isSurfaceCreated) {
        m_isSurfaceCreated = isSurfaceCreated;
    }

    String getDeviceSerialNumber() {
        return m_deviceSN;
    }
    synchronized void setDeviceSerialNumber(String sn) {
        m_deviceSN = sn;
    }
    synchronized void clearSerialNumber() { m_deviceSN = ""; }

    Context getContext() { return m_context; }
    synchronized void setContext(Context context) { m_context = context; }

    int getSurfaceMaxWidth() { return m_surfaceMaxWidth; }
    synchronized void setSurfaceMaxWidth(int w) { m_surfaceMaxWidth = w; }

    int getSurfaceMaxHeight() { return m_surfaceMaxHeight; }
    synchronized void setSurfaceMaxHeight(int h) { m_surfaceMaxHeight = h; }

    SaveImageStatus getTryToSaveImage() { return m_tryToSaveImage; }
    synchronized void setTryToSaveImage(SaveImageStatus save) { m_tryToSaveImage = save; }

    boolean getOffLineStatus() { return m_isOffLine; }
    synchronized void setOffLineStatus(boolean offLine) { m_isOffLine = offLine; }

    void clearAllDeque() {
        m_processImageLinkedDeque.clear();
        m_displayRunnableImageLinkedDeque.clear();
        m_rawImageLinkedDeque.clear();
    }


    public enum SaveImageStatus {
        DO_NOTHING(0),
        WAIT_FOR_IMAGE(1);

        private final int m_value;
        SaveImageStatus(int i) {
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
        public static SaveImageStatus valueOf(int i) {
            for(SaveImageStatus hm : values()) {
                if(hm.getValue() == i) {
                    return hm;
                }
            }
            return DO_NOTHING;
        }
    }
}

/**
 * brief    image improve param
 */
class ImageImproveParam {

    public enum MirrorDirection {
        HORIZONTAL(0),
        VERTICAL(1),
        DO_NOTHING(2);

        private final int m_value;
        MirrorDirection(int i) {
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
        public static MirrorDirection valueOf(int i) {
            for(MirrorDirection hm : values()) {
                if(hm.getValue() == i) {
                    return hm;
                }
            }
            return DO_NOTHING;
        }
    }

    private double m_gammaValue = 0;
    private long m_contrastValue = 0;
    private MirrorDirection m_mirrorDirection = MirrorDirection.VERTICAL;
    private boolean m_isColorCorrectEnable = false;
    private boolean m_isGammaEnable = false;
    private boolean m_isContrastEnable = false;
    private boolean m_isMirrorEnable = false;

    double getGammaValue() {
        return m_gammaValue;
    }
    long getContrastValue() {
        return m_contrastValue;
    }

    MirrorDirection getMirrorDirection() {
        return m_mirrorDirection;
    }

    boolean getColorCorrectStatus() {
        return m_isColorCorrectEnable;
    }

    boolean getGammaStatus() {
        return m_isGammaEnable;
    }

    boolean getContrastStatus() {
        return m_isContrastEnable;
    }

    boolean getMirrorStatus() {
        return m_isMirrorEnable;
    }

    void setGammaValue(double val) {
        m_gammaValue = val;
    }
    void setContrastValue(long val) {
        m_contrastValue = val;
    }
    void setMirrorDir(MirrorDirection dir) {
        m_mirrorDirection = dir;
    }
    void setEnableColorCorrect(boolean f) {
        m_isColorCorrectEnable = f;
    }
    void setEnableGamma(boolean f) {
        m_isGammaEnable = f;
    }
    void setEnableContrast(boolean f) {
        m_isContrastEnable = f;
    }
    void setEnableMirror(boolean f) {
        m_isMirrorEnable = f;
    }
}


