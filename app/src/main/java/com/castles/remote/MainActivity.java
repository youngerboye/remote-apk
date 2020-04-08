package com.castles.remote;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.IBinder;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private boolean bindService;
    private RemoteService.RemoteBinder remoteBinder;
    private RemoteService remoteService;
    private TextView tv_text;
    private TextView tv_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        tv_text = findViewById(R.id.messagetv);
        tv_id = findViewById(R.id.idtv);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        findViewById(R.id.buttonStart).setOnClickListener(this);
        bindService();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonStart:
                if (remoteBinder!=null){
                    remoteBinder.start();
                }
                break;
        }
    }
    private void bindService() {
        Intent intent = new Intent(this, RemoteService.class);
        startService(intent);
        bindService = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            remoteBinder = (RemoteService.RemoteBinder) iBinder;
            remoteService = remoteBinder.getRemoteService();
            remoteService.registerCallBack(callBack);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (remoteService != null) {
                remoteService.unRegisterCallBack(callBack);
            }
        }
    };

    private RemoteService.CallBack callBack = new RemoteService.CallBack() {
        @Override
        public void showID(int id) {
            tv_id.setText("ID:" + id);
        }

        @Override
        public void postMessage(int message) {
            switch (message) {
                case 1:
                    tv_text.setText("WIFI DISCONNECTED!!!");
                    break;
                case 2:
                case 3:
                    tv_text.setText("Start Error! Try again!");
                    break;
                default:
                    tv_text.setText("UNKOWN");
                    break;
            }
        }
    };

    public void unBindService() {
        if (bindService && serviceConnection != null) {
            unbindService(serviceConnection);
        }
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
}
