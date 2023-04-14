package gxviewer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Message;

import java.util.Calendar;


public class DisplayRunnable extends WorkRunnable {
    
    private ThreadShareObj m_threadShareObj;    ///< shared obj among threads

    private Matrix m_scaleBitmap;               ///< scale bitmap matrix

    private int m_currentImageWidth = 0;
    private int m_currentImageHeight = 0;

    /**
     * brief    display image on surface view
     * param threadShareObj[in]    shared obj among threads
     */
    DisplayRunnable(ThreadShareObj threadShareObj) {
        m_threadShareObj = threadShareObj;
    }

    /**
     * brief display thread run function
     */
    @Override
    public void run() {

        // waiting for surface created
        if(!m_threadShareObj.getIsSurfaceCreated()){
            final long surfaceCreateTimeout = 1000;
            long startTime = Calendar.getInstance().getTimeInMillis();
            while(!m_threadShareObj.getIsSurfaceCreated()) {
                long dt = Calendar.getInstance().getTimeInMillis() - startTime;
                // when wait too long, send time out message to main thread
                if(surfaceCreateTimeout < dt) {
                    Message msg = Message.obtain();
                    msg.what = MainActivity.HandlerMessage.ERROR_LEVEL_A.getValue();
                    msg.obj = "Surface Create failed";
                    m_threadShareObj.m_threadHandler.sendMessage(msg);
                    m_threadShareObj.m_displayRunnableImageLinkedDeque.clear();
                    return;
                }
                Thread.yield();
            }
        }

        // begin to display image on surface view
        while(getRunning()) {

            // check thread suspend status
            checkSuspendStatus();

            // if isStart is false, break this loop
            if(!getRunning()) {
                break;
            }

            long startFpsCtrTime = Calendar.getInstance().getTimeInMillis();
            // refresh display
            updateDisplay();
            long diffTime = Calendar.getInstance().getTimeInMillis() - startFpsCtrTime;

            // control image refresh interval
            try {
                final long showTimeInterval = 16; ///< image refresh interval 16 ms
                if(showTimeInterval > diffTime) {
                    Thread.sleep(showTimeInterval - diffTime);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
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

        m_threadShareObj.m_displayRunnableImageLinkedDeque.clear();

    }

    /**
     * brief  if displayImageLinkedDeque is not empty,
     *        get first image and clear displayImageLinkedDeque,
     *        then display on SurfaceView
     */
    private void updateDisplay() {
        if(!m_threadShareObj.m_displayRunnableImageLinkedDeque.isEmpty()) {

            // get first image then clear displayImageLinkedDeque
            Bitmap originBmp = null;

            // lock display deque before operate it
            synchronized (m_threadShareObj.m_displayRunnableImageLinkedDeque) {

                // find out image needed to be saved
                for (ImageInfo imageInfo : m_threadShareObj.m_displayRunnableImageLinkedDeque) {
                    if (imageInfo.getSaveImage()) {
                        originBmp = (Bitmap) imageInfo.getImageObj();
                        Message msg = UtilityTools.saveJpeg(originBmp,
                                m_threadShareObj.getContext());

                        m_threadShareObj.m_threadHandler.sendMessage(msg);
                    }
                }

                // if there are no image needed to save, poll first image of deque, then clear deque
                if (originBmp == null) {

                    ImageInfo imageInfo = m_threadShareObj.m_displayRunnableImageLinkedDeque.pollFirst();

                    if (imageInfo != null) {
                        originBmp = (Bitmap) imageInfo.getImageObj();
                    } else {
                        return;
                    }
                }

                m_threadShareObj.m_displayRunnableImageLinkedDeque.clear();
            }

            if(originBmp == null) {
                return;
            }

            // check image size changed
            if(originBmp.getHeight() != m_currentImageHeight ||
                    originBmp.getWidth() != m_currentImageWidth) {
                onFrameSizeChanged(originBmp);
            }

            if(originBmp.getHeight() <= 0 || originBmp.getWidth() <= 0) {
                return;
            }

            // display on surface view
            Bitmap bmp = Bitmap.createBitmap(originBmp,
                    0, 0, originBmp.getWidth(), originBmp.getHeight(),
                    m_scaleBitmap, false);

            m_threadShareObj.m_surfaceHolderLock.lock();
            try {
                Canvas canvas = m_threadShareObj.m_surfaceHolder.lockCanvas();
                if (canvas != null) {
                    final int left = 0;
                    final int top = 0;
                    canvas.drawBitmap(bmp, left, top, null);
                    m_threadShareObj.m_surfaceHolder.unlockCanvasAndPost(canvas);
                }
            } finally {
                m_threadShareObj.m_surfaceHolderLock.unlock();
            }
        }
    }

    /**
     * brief  when surface view is created,
     *        use this function to send an message to main thread,
     *        then resize surface view in main thread
     */
    void onFrameSizeChanged(Bitmap bmp) {

        // calculate fixed size of image, if image size is to large, scale it to match screen's size
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        if(width <= 0 || height <= 0) {
            return;
        }

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

        // init scale matrix
        m_scaleBitmap = new Matrix();
        float scaleX;
        float scaleY;

        scaleX = (float) width / (float) bmp.getWidth();
        scaleY = (float) height / (float) bmp.getHeight();

        m_scaleBitmap.postScale(scaleX, scaleY);

        m_currentImageHeight = height;
        m_currentImageWidth = width;

        // send surface size message to main thread
        Message msg = Message.obtain();
        msg.what = MainActivity.HandlerMessage.RESIZE_SURFACE.getValue();
        Rect disRect = new Rect();
        disRect.top = 0;
        disRect.left = 0;
        disRect.bottom = height;
        disRect.right = width;
        msg.obj = disRect;
        if(m_threadShareObj != null && m_threadShareObj.m_threadHandler != null) {
            m_threadShareObj.m_threadHandler.sendMessage(msg);
        }
    }

}
