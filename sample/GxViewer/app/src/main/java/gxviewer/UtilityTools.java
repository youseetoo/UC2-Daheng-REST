package gxviewer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.support.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;

class UtilityTools {

    /**
     * brief save image to Pictures dir
     * param bmp[in]    image to save
     * return    true: saved    false: failed
     */
    static @NonNull
    Message saveJpeg(Bitmap bmp, Context context) {

        // check is external storage is mounted
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
            msg.obj = "Save Failed {Media Unmounted}";
            return msg;
        }

        // find save dir and generate file name by data
        String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/";
        long time=System.currentTimeMillis();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS",Locale.getDefault());
        String fileName = format.format(new Date(time)) + ".jpg";

        // check if path is exist, if not create it
        File path = new File(dir);
        if(!path.exists()) {
            if(!path.mkdir()) {
                Message msg = Message.obtain();
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {Path Create Failed}";
                return msg;
            }
        }


        // save image to disk
        try {

            File file = new File(dir, fileName);
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            // send broad cast to update media
            Uri uri = Uri.fromFile(file);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));

        } catch (FileNotFoundException fileNotFond) {

            Message msg = Message.obtain();
            if(context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {Permission Denied}";
            } else {
                msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
                msg.obj = "Save Failed {File Not Found}";
            }

            return msg;
        } catch (Exception e) {
            e.printStackTrace();
            Message msg = Message.obtain();
            msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_FAILED.getValue();
            msg.obj = "Save Failed {Exception}";
            return msg;
        }

        Message msg = Message.obtain();
        msg.what = MainActivity.HandlerMessage.SAVE_IMAGE_COMPLETE.getValue();
        msg.obj = "Save Succeed";
        return msg;
    }

    /**
     * brief   remove n items from the end of deque
     * param deque[in]    deque to process
     * param n[in]        number of remove form end of deque
     */
    static void removeItemsFormDequeTail(ConcurrentLinkedDeque deque, int n) {
        if (deque == null) {
            return;
        }
        for (int i = 0; i < n; i++) {
            deque.pollLast();
        }
    }

    /**
     * brief    remove unsaved image items form tail of image deque
     * param    deque    deque needs process
     * param    n        max size of deque
     */
    static void removeUnsavedItemsFormImageDequeTail(ConcurrentLinkedDeque<ImageInfo> deque, int n) {

        ConcurrentLinkedDeque<ImageInfo> dequeBuf = new ConcurrentLinkedDeque<>();

        while(deque.size() + dequeBuf.size() > n) {

            ImageInfo imageInfo = deque.pollLast();

            if(imageInfo == null) {
                continue;
            }

            if(imageInfo.getSaveImage()) {
                dequeBuf.add(imageInfo);
            }

            // when deque is empty, break this loop, then add all dequeBuf's item to deque
            if(deque.isEmpty()) {
                break;
            }
        }

        deque.addAll(dequeBuf);
    }

}
