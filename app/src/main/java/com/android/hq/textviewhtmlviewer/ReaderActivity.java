package com.android.hq.textviewhtmlviewer;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Browser;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ReaderActivity extends Activity implements ReaderCallback.ContextMenuClickCallBack{

    private ReaderTextView mTextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (ReaderTextView) findViewById(R.id.textView);
        String str = loadData();
        mTextView.setReaderText(str);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private String loadData(){
        StringBuffer buffer = new StringBuffer("");
        try {
            InputStream stream = getAssets().open("documents/test.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String str;
            while ((str = reader.readLine())!=null){
                buffer.append(str);
                buffer.append("\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer.toString();
    }

    @Override
    public void onOpen(String url) {
        try{
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, getPackageName());
            intent.setClassName("com.android.browser", "com.android.browser.BrowserActivity");
            intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true);
            startActivity(intent);
        }catch (ActivityNotFoundException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onCopyMenu(String url) {
        if (url != null && url != "") {
            ClipboardManager cm = (ClipboardManager) this
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText(url);
        }
    }

    @Override
    public void onSaveMenu(String url) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        long id = downloadManager.enqueue(request);
        Log.d("ReadActivity","id = "+id);
    }
}
