package gxviewer;

import android.os.Message;

import galaxy.Device;
import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;
import galaxy.RawImage;

public class AcquireByRawImageRunnable extends WorkRunnable {

    private Device m_device;                         ///< m_device obj
    private ThreadShareObj m_threadShareObj;         ///< shared obj among threads

    private final int DATA_STREAM_INDEX = 0;
    private final int GET_IMAGE_TIME_OUT = 200;

    AcquireByRawImageRunnable(Device dev, ThreadShareObj threadShareObj) {

        m_threadShareObj = threadShareObj;
        m_device = dev;
    }

    /**
     * brief  run acquire by getRawImage, process soft trigger command
     */
    @Override
    public void run() {

        if(m_threadShareObj == null) {
            return;
        }

        if(m_device == null) {
            m_threadShareObj.setIsStart(false);
        }

        while(getRunning()) {

            // check thread suspend status
            checkSuspendStatus();

            // if isStart is false, break this loop
            if(!m_threadShareObj.getIsStart()) {
                break;
            }

            // get image by getRawImage interface,
            // then push image to top of rawImageLinkedDeque and processImageLinkedDeque
            ImageInfo rawImageInfo = null;
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
                        rawImageInfo = acquireAndSaveImage();
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
                        rawImageInfo = acquireAndSaveImage();
                    } else {
                        rawImageInfo = acquireImage();
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

            if(rawImageInfo != null) {
                m_threadShareObj.m_calculateFps.refreshFpsCount();
            }

            if (rawImageInfo != null) {
                RawImage rawImage = (RawImage) rawImageInfo.getImageObj();
                if(rawImage.getStatus() == 0) {
                    // add image data to rawImageLinkedDeque and processImageLinkedDeque,
                    // then can be used in DisplayThread and ImageProcessThread

                    final int MAX_DEQUE_SIZE = 3;           ///< max size of processDeque
                    synchronized (m_threadShareObj.m_rawImageLinkedDeque) {

                        m_threadShareObj.m_rawImageLinkedDeque.addFirst(rawImageInfo);
                        UtilityTools.removeUnsavedItemsFormImageDequeTail(m_threadShareObj.m_rawImageLinkedDeque, MAX_DEQUE_SIZE);

                    }

                    m_threadShareObj.m_processImageLinkedDeque.addFirst((RawImage) rawImageInfo.getImageObj());
                    // when deque's size is greater than MAX_DEQUE_SIZE, remove redundant elements.
                    if(m_threadShareObj.m_processImageLinkedDeque.size() > MAX_DEQUE_SIZE) {
                        UtilityTools.removeItemsFormDequeTail(m_threadShareObj.m_processImageLinkedDeque
                                , m_threadShareObj.m_processImageLinkedDeque.size() - MAX_DEQUE_SIZE);
                    }

                }
            }
        }

        m_threadShareObj.m_processImageLinkedDeque.clear();
        m_threadShareObj.m_rawImageLinkedDeque.clear();
    }

    private ImageInfo acquireImage()
    {

        // get image by surface, and save image, note that, saved image is raw format
        try {
            RawImage rawImage = m_device.getDataStream(DATA_STREAM_INDEX).getRawImage(GET_IMAGE_TIME_OUT);
            ImageInfo rawImageInfo = new ImageInfo();
            rawImageInfo.setImageObj(rawImage);
            rawImageInfo.setSaveImage(false);
            return rawImageInfo;
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
    private ImageInfo acquireAndSaveImage()
    {
        RawImage rawImage = null;
        // wait for image (50 * GET_IMAGE_TIME_OUT) ms
        final int ACQ_TIME_OUT_TIMES = 50;
        boolean isAcqImageSucceed = false;
        for(int i = 0; i < ACQ_TIME_OUT_TIMES && getRunning(); i++) {
            try {

                rawImage = m_device.getDataStream(DATA_STREAM_INDEX).getRawImage(GET_IMAGE_TIME_OUT);
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
            if(rawImage == null || rawImage.getStatus() != 0) {

                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {Image Is Incomplete}";
                m_threadShareObj.m_threadHandler.sendMessage(msg);
                return null;
            } else {
                m_threadShareObj.setTryToSaveImage(ThreadShareObj.SaveImageStatus.DO_NOTHING);
                ImageInfo rawImageInfo = new ImageInfo();
                rawImageInfo.setSaveImage(true);
                rawImageInfo.setImageObj(rawImage);

                return rawImageInfo;
            }
        }
        return null;
    }
}
