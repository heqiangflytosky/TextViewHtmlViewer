package com.android.hq.textviewhtmlviewer;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.annotation.IntDef;
import android.text.Html;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Patterns;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


public class ReaderTextView extends TextView {
    private final static String TAG = "ReaderTextView";
    private final static float IMAGE_SCALE_SIZE = 2.0f;
    private String mContent;
    private Drawable mDrawableLoading;
    private long mPreviousTapUpTime = 0;

    private int mLashHitPosition;
    private int mUrlSpanStart = -1;
    private int mUrlSpanEnd = -1;
    private ReaderContextMenuClickListener mContextMenuClickListener;
    private ReaderCallback.ContextMenuClickCallBack mContextMenuClickCallBack;
    private Object mBaseEditor = null;
    private Method method_startSelectionActionMode;

    public static final int HIT_TYPE_UNKNOWN = 0;
    public static final int HIT_TYPE_SRC_ANCHOR = 1;
    public static final int HIT_TYPE_EMAIL = 2;
    public static final int HIT_TYPE_IMAGE = 3;
    public static final int HIT_TYPE_SRC_IMAGE_ANCHOR = 4;
    public static final int HIT_TYPE_TELEPHONE = 5;
    @IntDef({HIT_TYPE_UNKNOWN, HIT_TYPE_SRC_ANCHOR, HIT_TYPE_EMAIL, HIT_TYPE_IMAGE,
            HIT_TYPE_SRC_IMAGE_ANCHOR, HIT_TYPE_TELEPHONE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface HitType{}

    private List<ImageGetterAsyncTask> mRequestList = new ArrayList<>();

    private ConcurrentHashMap<String,CacheImageInfo> mImageCache;
    public static final int NOTLOADED = 0;
    public static final int LOADING = 1;
    public static final int LOADED = 2;
    public static class CacheImageInfo{
        Drawable drawable;
        int loadingType = NOTLOADED;
    }

    public ReaderTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context){
        //长按可以选择复制
        setTextIsSelectable(true);
        //超链接可以点击
        setMovementMethod(ReaderMovementMethod.getInstance());

        mContextMenuClickListener = new ReaderContextMenuClickListener();
        setOnCreateContextMenuListener(mContextMenuClickListener);
        mContextMenuClickCallBack = (ReaderCallback.ContextMenuClickCallBack)getContext();

        mDrawableLoading = getResources().getDrawable(R.mipmap.ic_launcher);
        mDrawableLoading.setBounds(0,0,mDrawableLoading.getIntrinsicWidth(),mDrawableLoading.getIntrinsicHeight());

        mImageCache = new ConcurrentHashMap<>();

        initInflection();

    }

    public void setReaderText(String text){
        mContent = text;
        refreshContent();
    }

    private void refreshContent(){
        CharSequence s = Html.fromHtml(mContent, new ReaderImageGetter(ReaderTextView.this), null);
        setText(s);
    }

    private Drawable doGetDrawable(final String source){
        if(TextUtils.isEmpty(source))
            return mDrawableLoading;
        if(mImageCache.containsKey(source)) {
            CacheImageInfo info = mImageCache.get(source);
            if(info.drawable != null) {
                setImageCenterHorizontal(info.drawable, true);
                return info.drawable;
            }
            else if(info.loadingType == LOADING)
                return mDrawableLoading;
        }

        ImageGetterAsyncTask task = new ImageGetterAsyncTask();
        task.execute(source);
        mRequestList.add(task);

        CacheImageInfo info = new CacheImageInfo();
        info.drawable = null;
        info.loadingType = LOADING;
        mImageCache.put(source, info);
        return mDrawableLoading;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if(action == MotionEvent.ACTION_UP)
            mPreviousTapUpTime = SystemClock.uptimeMillis();
        if(action == MotionEvent.ACTION_DOWN){
            long duration = SystemClock.uptimeMillis() - mPreviousTapUpTime;
            //屏蔽双击事件
            if (duration <= ViewConfiguration.getDoubleTapTimeout()) {
                return true;
            }

            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= getTotalPaddingLeft();
            y -= getTotalPaddingTop();

            x += getScrollX();
            y += getScrollY();

            Layout layout = getLayout();
            int line = layout.getLineForVertical(y);
            mLashHitPosition = layout.getOffsetForHorizontal(line, x);
        }
        return super.onTouchEvent(event);
    }

    private class ReaderContextMenuClickListener implements View.OnCreateContextMenuListener {
        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            @HitType int type = HIT_TYPE_UNKNOWN;

            String url = "";
            String image_url = "";
            mUrlSpanStart = -1;
            mUrlSpanEnd = -1;
            ClickableSpan[] link = getText().getSpans(mLashHitPosition, mLashHitPosition, ClickableSpan.class);
            if (link.length != 0) {
                if(link[0] instanceof URLSpan){
                    url = ((URLSpan)link[0]).getURL();
                    if(Patterns.WEB_URL.matcher(url).matches()){
                        type = HIT_TYPE_SRC_ANCHOR;
                        mUrlSpanStart = getText().getSpanStart(link[0]);
                        mUrlSpanEnd= getText().getSpanEnd(link[0]);
                    }
                }
            }

            if(type == HIT_TYPE_UNKNOWN || type == HIT_TYPE_SRC_ANCHOR){
                ImageSpan[] image = getText().getSpans(mLashHitPosition, mLashHitPosition, ImageSpan.class);
                if (image.length != 0) {
                    if(type == HIT_TYPE_SRC_ANCHOR) {
                        type = HIT_TYPE_SRC_IMAGE_ANCHOR;
                        image_url = image[0].getSource();
                    }
                    else {
                        type = HIT_TYPE_IMAGE;
                        url = image[0].getSource();
                    }
                }
            }


            if(type == HIT_TYPE_UNKNOWN) {
                if((mLashHitPosition+1) > getText().length()){
                    return;
                }
                setSelection(mLashHitPosition, mLashHitPosition + 1);
                return;
            }

            MenuInflater inflater = ((ReaderActivity)getContext()).getMenuInflater();
            inflater.inflate(R.menu.menu_context, menu);
            menu.setGroupVisible(R.id.IMAGE_MENU, type == HIT_TYPE_IMAGE || type == HIT_TYPE_SRC_IMAGE_ANCHOR);
            menu.setGroupVisible(R.id.ANCHOR_MENU, type == HIT_TYPE_SRC_ANCHOR || type == HIT_TYPE_SRC_IMAGE_ANCHOR);

            final String f_url = url;
            final String f_image_url = image_url;
            @HitType final int f_type = type;
            menu.findItem(R.id.copy_image_link_context_menu_id).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mContextMenuClickCallBack.onCopyMenu(f_url);
                    return true;
                }
            });
            menu.findItem(R.id.copy_link_context_menu_id).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mContextMenuClickCallBack.onCopyMenu(f_url);
                    return true;
                }
            });
            menu.findItem(R.id.select_text_context_menu_id).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    if(mUrlSpanStart == -1 || mUrlSpanEnd == -1 || mUrlSpanEnd > getText().length())
                        return false;
                    ReaderTextView.this.requestFocus();
                    setSelection(mUrlSpanStart, mUrlSpanEnd);
                    startSelectionMode();
                    return true;
                }
            });
            menu.findItem(R.id.save_link_context_menu_id).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mContextMenuClickCallBack.onSaveMenu(f_url);
                    return true;
                }
            });

            switch (type) {
                case HIT_TYPE_SRC_ANCHOR:
                case HIT_TYPE_SRC_IMAGE_ANCHOR:
                    MenuItem newTabItem = menu
                            .findItem(R.id.contextmenu_open_link_menu_id);
                    newTabItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            mContextMenuClickCallBack.onOpen(f_url);
                            return true;
                        }
                    });
                    if(type == HIT_TYPE_SRC_IMAGE_ANCHOR){
                        menu.findItem(R.id.select_text_context_menu_id).setVisible(false);
                        menu.findItem(R.id.copy_link_context_menu_id).setVisible(false);
                        menu.findItem(R.id.view_image_context_menu_id).setVisible(false);
                    }
                    if (type == HIT_TYPE_SRC_ANCHOR)
                        break;
                case HIT_TYPE_IMAGE:
                    if (type == HIT_TYPE_IMAGE) {
                        menu.findItem(R.id.copy_image_link_context_menu_id).setVisible(false);
                    }

                    menu.findItem(R.id.view_image_context_menu_id)
                            .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    mContextMenuClickCallBack.onOpen(f_url);
                                    return false;
                                }
                            });
                    menu.findItem(R.id.download_context_menu_id).setOnMenuItemClickListener(
                            new MenuItem.OnMenuItemClickListener() {
                                @Override
                                public boolean onMenuItemClick(MenuItem item) {
                                    if(f_type == HIT_TYPE_SRC_IMAGE_ANCHOR){
                                        mContextMenuClickCallBack.onSaveMenu(f_image_url);
                                    }else{
                                        mContextMenuClickCallBack.onSaveMenu(f_url);
                                    }
                                    return true;
                                }
                            });
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public Spannable getText() {
        return (Spannable) super.getText();
    }

    public void setSelection(int start, int stop) {
        Selection.setSelection(getText(), start, stop);
    }

    private void initInflection() {
        try {
            Field field = TextView.class.getDeclaredField("mEditor");
            field.setAccessible(true);
            mBaseEditor = field.get(this);
            if(mBaseEditor != null){
                Class editorClass = mBaseEditor.getClass();
                method_startSelectionActionMode = editorClass.getDeclaredMethod("startSelectionActionMode");
                method_startSelectionActionMode.setAccessible(true);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void startSelectionMode() {
        try {
            if(method_startSelectionActionMode != null)
                method_startSelectionActionMode.invoke(this.mBaseEditor);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setImageCenterHorizontal(Drawable drawable, boolean scale){
        //+left 解决图片不居中问题
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int width = ReaderTextView.this.getWidth();
        int left = 0;
        int w = intrinsicWidth;
        int h = drawable.getIntrinsicHeight();
        if(intrinsicWidth < width){
            if(scale){
                if(intrinsicWidth * IMAGE_SCALE_SIZE > width){
                    w = width;
                    h = (int) (h * ((float)width/intrinsicWidth));
                }else{
                    w = (int)(intrinsicWidth * IMAGE_SCALE_SIZE);
                    h = (int)(h * IMAGE_SCALE_SIZE);
                    left = (width - w)/2;
                }
            }else{
                left = (width - intrinsicWidth)/2;
            }
        }else{
            //解决宽度大于TextView宽度的部分不显示的问题
            w = width;
            h = (int) (h * ((float)width/intrinsicWidth));
        }
        drawable.setBounds(left, 0, w + left, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if(null != mRequestList){
            for(ImageGetterAsyncTask request:mRequestList) {
                request.cancel(true);
            }
            mRequestList.clear();
        }
    }

    private static class ReaderImageGetter implements Html.ImageGetter{
        private WeakReference<ReaderTextView> mView;

        public ReaderImageGetter(ReaderTextView view){
            mView = new WeakReference<ReaderTextView>(view);
        }

        @Override
        public Drawable getDrawable(String source) {
            if(null != mView.get()) {
                return mView.get().doGetDrawable(source);
            }
            return null;
        }
    }

    public  class ImageGetterAsyncTask extends AsyncTask<String,Void,Drawable> {
        private String mUrl;
        private Resources mRes = getResources();
        public ImageGetterAsyncTask(){
        }

        @Override
        protected Drawable doInBackground(String... params) {
            try {
                mUrl = params[0];
                InputStream inputStream = new URL(mUrl).openStream();
                BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
                decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
                decodeOptions.inDensity = 0;
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream,null, decodeOptions);
                return new BitmapDrawable(mRes, bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            CacheImageInfo info = mImageCache.get(mUrl);
            info.drawable = drawable;
            refreshContent();
            if(mRequestList.contains(this)){
                mRequestList.remove(this);
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if(mRequestList.contains(this)){
                mRequestList.remove(this);
            }
        }
    }
}
