package gxviewer;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;

import galaxy.Device;
import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;
import galaxy.GxIJNI;

public class AcquireByBitmapRunnable extends WorkRunnable {

    Handler m_msgHandler;                   ///< send and receive message from other thread

    private Device m_device;                         ///< device obj
    private ThreadShareObj m_threadShareObj;         ///< shared obj among threads
    private GxIJNI.ProcessParam m_processParam;      ///< image process param

    private final int DATA_STREAM_INDEX = 0;
    private final int GET_IMAGE_TIME_OUT = 200;

    /**
     * brief  acquire image by getBitmap interface obj
     * param dev[in]              device obj
     * param threadShareObj[in]   share threadShareObj among threads
     */
    AcquireByBitmapRunnable(Device dev, ThreadShareObj threadShareObj) {

        m_device = dev;
        m_threadShareObj = threadShareObj;
        m_processParam = new GxIJNI.ProcessParam();

        // message receiver
        m_msgHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {

                switch(MainActivity.HandlerMessage.valueOf(msg.what)) {

                    case UPDATE_IMAGE_PROCESSING_PARAM:///< receive a new image process param
                        m_processParam = (GxIJNI.ProcessParam) msg.obj;
                        break;
                    default: break;
                }
                return false;
            }
        });
    }

    /**
     * brief  run acquire by getBitmap, process soft trigger command
     */
    @Override
    public void run() {

        if(m_threadShareObj == null) {
            return;
        }

        if(m_device == null) {
            m_threadShareObj.setIsStart(false);
        }

        while (getRunning()) {

            // check thread suspend status
            checkSuspendStatus();

            // if isStart is false, break this loop
            if(!getRunning()) {
                break;
            }

            ImageInfo bmpImageInfo = null;
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
                        bmpImageInfo = acquireForSaveImage();
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
                        bmpImageInfo = acquireForSaveImage();
                    } else {
                        bmpImageInfo = acquireImage();
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

            if (bmpImageInfo != null) {
                // refresh fps and frame information
                m_threadShareObj.m_calculateFps.refreshFpsCount();
                // add bitmap to display image deque, then display thread would update it on ui
                synchronized (m_threadShareObj.m_displayRunnableImageLinkedDeque) {
                    m_threadShareObj.m_displayRunnableImageLinkedDeque.addFirst(bmpImageInfo);

                    // when processImageLinkedDeque's size is greater than MAX_PROCESS_DEQUE_SIZE, remove redundant elements.
                    final int MAX_PROCESS_DEQUE_SIZE = 3;   ///< max size of processDeque
                    UtilityTools.removeUnsavedItemsFormImageDequeTail(m_threadShareObj.m_displayRunnableImageLinkedDeque, MAX_PROCESS_DEQUE_SIZE);
                }
            }
        }

        m_threadShareObj.m_displayRunnableImageLinkedDeque.clear();

        // remove all message in handler deque
        if(m_msgHandler != null) {
            m_msgHandler.removeCallbacksAndMessages(null);
        }
    }

    private ImageInfo acquireImage()
    {
        // record frame information
        GxIJNI.FrameInfo frameInfo = new GxIJNI.FrameInfo();

        // get image by surface, and save image, note that, saved image is raw format
        try {

            Bitmap bmp = m_device.getDataStream(DATA_STREAM_INDEX).getBitmap(m_processParam, frameInfo, GET_IMAGE_TIME_OUT);

            ImageInfo img = new ImageInfo();
            img.setImageObj(bmp);
            img.setSaveImage(false);

            return img;

        } catch (ExceptionSet.Timeout timeout) {
            return null;
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
            return null;
        }
    }

    /**
     * brief    acquire image then save
     */
    private ImageInfo acquireForSaveImage()
    {
        GxIJNI.FrameInfo frameInfo = new GxIJNI.FrameInfo();

        Bitmap bmp = null;
        // wait for image (50 * GET_IMAGE_TIME_OUT) ms
        final int ACQ_TIME_OUT_TIMES = 50;
        boolean isAcqImageSucceed = false;
        for(int i = 0; i < ACQ_TIME_OUT_TIMES && getRunning(); i++) {
            try {

                bmp = m_device.getDataStream(DATA_STREAM_INDEX).getBitmap(m_processParam, frameInfo, GET_IMAGE_TIME_OUT);
                isAcqImageSucceed = true;
                break;

            } catch(ExceptionSet.Timeout timeout) {
                // when time out continue
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
                return null;
            }
        }

        if(!isAcqImageSucceed) {
            m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
            msg.obj = "Save Failed {Get Image Time Out}";
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        } else {
            // if bmp obj is null or image is incomplete, send save image failed message
            if(bmp == null || frameInfo.getStatus() != 0) {
                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {Image Is Incomplete}";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                return null;
            } else {
                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                ImageInfo img = new ImageInfo();
                img.setImageObj(bmp);
                img.setSaveImage(true);
                return img;
            }
        }
        return null;
    }
}
