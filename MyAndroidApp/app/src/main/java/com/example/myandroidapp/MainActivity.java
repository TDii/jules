package com.example.myandroidapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the content view to a layout that should be created
        // R.layout.activity_main should correspond to res/layout/activity_main.xml
        setContentView(R.layout.activity_main);

        // Example of setting text programmatically, if a TextView with id "greeting" exists
        // TextView greetingTextView = findViewById(R.id.greeting);
        // if (greetingTextView != null) {
        //     greetingTextView.setText("Hello from MainActivity!");
        // }
    }
}
