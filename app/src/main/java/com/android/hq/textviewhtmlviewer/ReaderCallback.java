package com.android.hq.textviewhtmlviewer;

public class ReaderCallback {
    public interface ContextMenuClickCallBack{
        void onOpen(String url);
        void onCopyMenu(String url);
        void onSaveMenu(String url);
    }
}
