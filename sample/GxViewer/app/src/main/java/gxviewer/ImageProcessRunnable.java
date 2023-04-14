package gxviewer;

import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.List;

import galaxy.EnumDefineSet;
import galaxy.ExceptionSet;
import galaxy.GxIJNI;
import galaxy.ARGBImage;
import galaxy.RawImage;


public class ImageProcessRunnable extends WorkRunnable {


    Handler m_msgHandler;                          ///< send and receive message from other thread
    ThreadShareObj m_threadShareObj;               ///< shared obj among threads
    private GxIJNI.ProcessParam m_processParam;    ///< image process param

    /**
     * brief image process
     * param objs    shared obj among threads
     */
    ImageProcessRunnable(ThreadShareObj threadShareObj) {
        m_threadShareObj = threadShareObj;
        m_processParam = new GxIJNI.ProcessParam();

        m_msgHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch(MainActivity.HandlerMessage.valueOf(msg.what)) {
                    case UPDATE_IMAGE_PROCESSING_PARAM:///< receive a new image process param
                        m_processParam = (GxIJNI.ProcessParam) msg.obj;
                        break;
                }

                return false;
            }
        });
    }

    @Override
    public void run() {

        while(getRunning()) {

            // check thread suspend status
            checkSuspendStatus();

            // if isStart is false, break this loop
            if(!getRunning()) {
                break;
            }

            // process image to display
            // if rawImageLinkedDeque is not empty, poll first item to process, then clear rawImageLinkedDeque
            if(!m_threadShareObj.m_rawImageLinkedDeque.isEmpty()) {

                // find image need to save
                List<ImageInfo> imageInfoList = new ArrayList<>();
                synchronized (m_threadShareObj.m_rawImageLinkedDeque) {
                    for (ImageInfo imageInfo : m_threadShareObj.m_rawImageLinkedDeque) {

                        if(imageInfo == null) {
                            continue;
                        }

                        if (imageInfo.getSaveImage()) {
                            imageInfoList.add(imageInfo);
                            m_threadShareObj.m_rawImageLinkedDeque.remove(imageInfo);
                        }
                    }

                    // if there is no image need to save, poll first image of m_rawImageLinedDeque to process
                    if (imageInfoList.isEmpty()) {
                        imageInfoList.add(m_threadShareObj.m_rawImageLinkedDeque.pollFirst());
                    }

                    m_threadShareObj.m_rawImageLinkedDeque.clear();
                }


                for(ImageInfo rawImageInfo : imageInfoList) {

                    if(rawImageInfo == null) {
                        continue;
                    }

                    ImageInfo bitmapImageInfo = new ImageInfo();
                    bitmapImageInfo.setSaveImage(rawImageInfo.getSaveImage());
                    RawImage rawImage = (RawImage) rawImageInfo.getImageObj();

                    try {

                        if (m_processParam.getMirror()) {
                            rawImage = rawImage.mirror(m_processParam.getMirrorMode(), EnumDefineSet.ValidBit.BIT0_7);
                        }

                        // convert raw image to rgb image
                        final byte alpha = (byte) 0xff;
                        ARGBImage argbImage = rawImage.convertToARGB(EnumDefineSet.BayerConvertType.NEIGHBOUR,
                                EnumDefineSet.ValidBit.BIT0_7,
                                false, alpha);

                        argbImage.imageImprovement(m_processParam.getColorCorrectionParam(),
                                m_processParam.getContrastLut(),
                                m_processParam.getGammaLut());

                        bitmapImageInfo.setImageObj(argbImage.getBitmap());

                        synchronized (m_threadShareObj.m_displayRunnableImageLinkedDeque) {
                            m_threadShareObj.m_displayRunnableImageLinkedDeque.addFirst(bitmapImageInfo);
                            final int MAX_DEQUE_SIZE = 3;
                            UtilityTools.removeUnsavedItemsFormImageDequeTail(m_threadShareObj.m_displayRunnableImageLinkedDeque, MAX_DEQUE_SIZE);
                        }

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
                    } catch(Exception exception) {
                        exception.printStackTrace();
                    }
                }

            } else {

                try {
                    final int FREE_SLEEP_NANOS = 50000;
                    Thread.sleep(0, FREE_SLEEP_NANOS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            // get image data for processing
            if(!m_threadShareObj.m_processImageLinkedDeque.isEmpty()) {
                RawImage rawImage = m_threadShareObj.m_processImageLinkedDeque.pollFirst();
                // do something with image

                // clear process image linked deque
                m_threadShareObj.m_processImageLinkedDeque.clear();
            }

            // remove all message in handler deque
            if(m_msgHandler != null) {
                m_msgHandler.removeCallbacksAndMessages(null);
            }
        }
    }
}
