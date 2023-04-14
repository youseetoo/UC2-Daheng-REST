package gxviewer;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import galaxy.Device;
import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;
import galaxy.GxIJNI;

import static gxviewer.MainActivity.HandlerMessage.*;


public class AcquireBySurfaceRunnable extends WorkRunnable {

    Handler m_msgHandler;                     ///< send and receive message from other thread

    private Device m_device;                         ///< device obj
    private ThreadShareObj m_threadShareObj;         ///< shared obj among threads
    private GxIJNI.ProcessParam m_processParam;      ///< image process param

    private final int GET_IMAGE_TIMEOUT = 200;      ///< get image timeout

    AcquireBySurfaceRunnable(Device dev, ThreadShareObj threadShareObj) {

        m_device = dev;
        m_threadShareObj = threadShareObj;

        // instance process param obj
        m_processParam = new GxIJNI.ProcessParam();

        m_msgHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(MainActivity.HandlerMessage.valueOf(msg.what)) {

                    case UPDATE_IMAGE_PROCESSING_PARAM:///< receive a new image process param
                        m_processParam = (GxIJNI.ProcessParam) msg.obj;
                        break;

                    default:break;
                }
                return false;
            }
        });
    }

    /**
     * brief  display thread run function
     */
    @Override
    public void run() {
        // waiting for surface created
        if(!m_threadShareObj.getIsSurfaceCreated()) {
            final long surfaceCreateTimeout = 200;
            long startTime = Calendar.getInstance().getTimeInMillis();
            while (!m_threadShareObj.getIsSurfaceCreated()) {
                long dt = Calendar.getInstance().getTimeInMillis() - startTime;
                // when wait too long, send time out message to main thread
                if (surfaceCreateTimeout < dt) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_C.getValue();
                    msg.obj = "Surface Create failed";
                    m_threadShareObj.m_threadHandler.sendMessage(msg);
                    return;
                }
                Thread.yield();
            }
        }

        // resize surface size
        resizeSurfaceView();

        // find save dir
        final String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/";
        // record frame information
        GxIJNI.FrameInfo frameInfo = new GxIJNI.FrameInfo();

        // begin to display image on surface view
        while(getRunning()) {

            // check thread suspend status
            checkSuspendStatus();

            // if isStart is false, break this loop
            if(!m_threadShareObj.getIsStart()) {
                break;
            }

            try {

                long triggerModeStatus = EnumDefineSet.SwitchEntry.OFF.getValue();
                // get trigger mode
                try {
                    triggerModeStatus = m_device.TriggerMode.get();
                } catch (ExceptionSet exceptionSet) {
                    exceptionSet.printStackTrace();
                }

                if(triggerModeStatus == EnumDefineSet.SwitchEntry.ON.getValue()) {
                    if(m_threadShareObj.getTryToSaveImage() == ThreadShareObj.SaveImageStatus.WAIT_FOR_IMAGE) {
                        m_device.getDataStream(0).flushQueue();
                        m_device.TriggerSoftware.sendCommand();
                        acquireAndSaveImage(dir, frameInfo);
                    } else {
                        // if you want use external trigger mode, uncomment next line, then comment [sleep] code
                        // acquireAndSaveImage();

                        // soft trigger mode, sleep for 10 ms
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if(m_threadShareObj.getTryToSaveImage() == ThreadShareObj.SaveImageStatus.WAIT_FOR_IMAGE) {
                        acquireAndSaveImage(dir, frameInfo);
                    } else {
                        acquireImage();
                    }
                }
            } catch (ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();
                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {" + exceptionSet.toString() + "}";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        m_threadShareObj.m_surfaceHolderLock.lock();

        try {
            if (m_threadShareObj.m_surfaceHolder != null) {
                Canvas canvas = m_threadShareObj.m_surfaceHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.WHITE);
                    m_threadShareObj.m_surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        } finally {
            m_threadShareObj.m_surfaceHolderLock.unlock();
        }

        // remove all message in handler deque
        if(m_msgHandler != null) {
            m_msgHandler.removeCallbacksAndMessages(null);
        }

    }

    private void acquireImage()
    {
        // record frame information
        GxIJNI.FrameInfo frameInfo = new GxIJNI.FrameInfo();

        // get image by surface, and save image, note that, saved image is raw format
        try {

            m_threadShareObj.m_surfaceHolderLock.lock();

            try {
                m_device.getDataStream(0).getImageBySurface(m_threadShareObj.m_surfaceHolder.getSurface(),
                        m_processParam,
                        frameInfo,
                        null,
                        GET_IMAGE_TIMEOUT
                );
            } finally {
                m_threadShareObj.m_surfaceHolderLock.unlock();
            }

        } catch (ExceptionSet.Timeout timeout) {
            return;
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();

            // reset runnable running flag
            setRunning(false);

            // stop all runnable running flag
            m_threadShareObj.setIsStart(false);

            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_A.getValue();
            msg.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(msg);
            return;
        }
        m_threadShareObj.m_calculateFps.refreshFpsCount();
    }

    /**
     * brief    acquire image then save
     */
    private void acquireAndSaveImage(String dir, GxIJNI.FrameInfo frameInfo)
    {
        // generate file name by current time
        long time=System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS",Locale.getDefault());
        String fileName = format.format(new Date(time));

        // wait for image (50 * GET_IMAGE_TIMEOUT) ms
        final int ACQ_TIME_OUT_TIMES = 50;
        boolean isAcqImageSucceed = false;
        for(int i = 0; i < ACQ_TIME_OUT_TIMES && getRunning(); i++) {
            try {

                m_threadShareObj.m_surfaceHolderLock.lock();

                try {
                    // get image by surface, and save image, note that, saved image is raw format
                    m_device.getDataStream(0).getImageBySurface(m_threadShareObj.m_surfaceHolder.getSurface(),
                            m_processParam,
                            frameInfo,
                            dir + fileName,
                            GET_IMAGE_TIMEOUT
                    );
                } finally {
                    m_threadShareObj.m_surfaceHolderLock.unlock();
                }

                isAcqImageSucceed = true;
                break;
            } catch(ExceptionSet.Timeout timeout) {
                // when time out continue
            } catch (ExceptionSet.SaveImageNoPermission saveImageNoPermission) {
                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {Permission Denied}";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                m_threadShareObj.m_calculateFps.refreshFpsCount();
                return;
            } catch(ExceptionSet exceptionSet) {
                exceptionSet.printStackTrace();

                // reset runnable running flag
                setRunning(false);

                // stop all runnable running flag
                m_threadShareObj.setIsStart(false);

                // when catch other exception set, send error message
                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);

                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_A.getValue();
                msg.obj = exceptionSet.toString();
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                return;
            }
        }

        if(!isAcqImageSucceed) {
            m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
            msg.obj = "Save Failed {Get Image Time Out}";
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }else {
            m_threadShareObj.m_calculateFps.refreshFpsCount();
            if(frameInfo.getStatus() != 0) {
                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {Image Is Incomplete}";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
            } else {

                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_COMPLETE.getValue();
                msg.obj = "Save Succeed";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                File file = new File(dir+fileName);
                // send broad cast to update media
                Uri uri = Uri.fromFile(file);
                m_threadShareObj.getContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
            }
        }
    }

    /**
     * brief  when surface view is created,
     *        use this function to send an message to main thread,
     *        then resize surface view in main thread
     */
    private void resizeSurfaceView() {
        // when there deque is empty, wait for image data
        // send surface size message to main thread
        Message msg = Message.obtain();
        msg.what = RESIZE_SURFACE.getValue();
        Rect disRect = new Rect();
        disRect.top = 0;
        disRect.left = 0;
        try {
            disRect.bottom = (int)m_device.Height.get();
            disRect.right = (int)m_device.Width.get();
        } catch (ExceptionSet exceptionSet) {
            exceptionSet.printStackTrace();
            Message message = Message.obtain();
            message.what = MainActivity.HandlerMessage.ERROR_LEVEL_A.getValue();
            message.obj = exceptionSet.toString();
            m_threadShareObj.m_threadHandler.sendMessage(message);
        } catch(Exception exception) {
            exception.printStackTrace();
        }

        msg.obj = disRect;
        m_threadShareObj.m_threadHandler.sendMessage(msg);
    }

}
