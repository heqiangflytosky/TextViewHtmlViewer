package com.android.hq.textviewhtmlviewer;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Browser;
import android.text.Layout;
import android.text.Spannable;
import android.text.method.ArrowKeyMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Patterns;
import android.view.MotionEvent;
import android.widget.TextView;

public class ReaderMovementMethod extends ArrowKeyMovementMethod {
    private static ReaderMovementMethod sInstance;

    public static ReaderMovementMethod getInstance(){
        if(sInstance == null){
            sInstance = new ReaderMovementMethod();
        }
        return sInstance;
    }

    @Override
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_UP) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();

            x += widget.getScrollX();
            y += widget.getScrollY();

            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);

            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

            if (link.length != 0) {
                if (action == MotionEvent.ACTION_UP) {
                    if(link[0] instanceof URLSpan){
                        String url = ((URLSpan)link[0]).getURL();
                        if(Patterns.WEB_URL.matcher(url).matches()){
                            try {
                                Context context = widget.getContext();
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                intent.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
                                intent.setClassName("com.android.browser", "com.android.browser.BrowserActivity");
                                intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
                                context.startActivity(intent);
                            }catch (ActivityNotFoundException exception){
                                exception.printStackTrace();
                            }
                            return true;
                        }else{
                            link[0].onClick(widget);
                        }
                    }
                }
                return true;
            }
        }

        return super.onTouchEvent(widget, buffer, event);
    }
}
