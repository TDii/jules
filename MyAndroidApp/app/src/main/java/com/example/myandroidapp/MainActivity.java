package com.example.myandroidapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;
import android.content.Intent;
import android.net.VpnService;
import java.util.ArrayList; // Added import
import android.widget.ArrayAdapter; // Added import
import android.widget.ListView; // Added import
import androidx.localbroadcastmanager.content.LocalBroadcastManager; // Added import
import android.content.BroadcastReceiver; // Added import
import android.content.Context; // Added import
import android.content.IntentFilter;
import android.util.Log; // Added for logging


public class MainActivity extends Activity {

    private static final String TAG = "MainActivity"; // Added TAG
    private static final int VPN_REQUEST_CODE = 101;

    private ListView requestListView;
    private ArrayList<HttpRequestInfo> requestList;
    private ArrayAdapter<HttpRequestInfo> requestListAdapter;
    private BroadcastReceiver httpRequestListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // VPN Buttons
        Button startVpnButton = findViewById(R.id.button_start_vpn);
        Button stopVpnButton = findViewById(R.id.button_stop_vpn);

        startVpnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent prepareVpnIntent = VpnService.prepare(MainActivity.this);
                if (prepareVpnIntent != null) {
                    startActivityForResult(prepareVpnIntent, VPN_REQUEST_CODE);
                } else {
                    onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null); // Already prepared
                }
            }
        });

        stopVpnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, MyVpnService.class));
            }
        });

        // ListView setup
        requestListView = findViewById(R.id.listview_requests);
        requestList = new ArrayList<>();
        requestListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, requestList);
        requestListView.setAdapter(requestListAdapter);

        // BroadcastReceiver setup
        httpRequestListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && MyVpnService.ACTION_HTTP_REQUEST_CAPTURED.equals(intent.getAction())) {
                    HttpRequestInfo info = intent.getParcelableExtra(MyVpnService.EXTRA_HTTP_REQUEST_INFO);
                    if (info != null) {
                        requestList.add(0, info); // Add to top of the list
                        requestListAdapter.notifyDataSetChanged();
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(httpRequestListener, new IntentFilter(MyVpnService.ACTION_HTTP_REQUEST_CAPTURED));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent intent = new Intent(this, MyVpnService.class);
            startService(intent);
        } else if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_CANCELED) {
            Log.i(TAG, "User cancelled VPN permission request.");
            // Optionally, inform the user via a Toast or UI update
            // Toast.makeText(this, "VPN Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the receiver to prevent memory leaks
        if (httpRequestListener != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(httpRequestListener);
        }
    }
}
