/*
Copyright 2018 Alex Redaelli <a.redaelli at gmail dot com>

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, 
modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the 
Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR 
THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package com.alexr.falldetection;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

public class MainActivity extends Activity implements SensorEventListener {

    public static final boolean SERVERTRACE = false;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private Button button;

    private FallDetectAlgo fallDetectAlgo;
    private Handler h0;
    private boolean flag_fall = false;
    private boolean flag_buffer_ready = false;
    private ImageView image;
    private boolean f_send_msg = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        if (SERVERTRACE) enableStrictMode(); // for socket handling in mainloop

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();

        // start sample and analyze
        fallDetectAlgo = new FallDetectAlgo();
        fallDetectAlgo.setDaemon(true);
        fallDetectAlgo.start();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Toast.makeText(this, "No Accelerometer Found!!",
                    Toast.LENGTH_LONG).show();
        }

        image = (ImageView) findViewById(R.id.fallicon);
        image.setImageResource(R.drawable.fall_icon);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("SMS", "Permission is not granted, requesting");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, 123);
        } else {
            Log.d("SMS", "Permission is granted");
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 123) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("SMS", "Permission has been granted");
            } else {
                Log.d("SMS", "Permission has been denied or request cancelled");

            }
        }
    }

    public void initializeViews() {
        button = (Button) findViewById(R.id.button);
    }

    //onResume() register the accelerometer for listening the events
    //todo create a bound service instead of unregister
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        // display if buffer is ready
        flag_buffer_ready = fallDetectAlgo.get_buffer_ready();
        if (flag_buffer_ready) {
            button.setText("BUFFER OK");
        } else {
            button.setText("NO BUFFER");
        }
        // store values in buffer and visualize fall
        flag_fall = fallDetectAlgo.set_data(event); // event has values minus gravity
        if (flag_fall) {
            if (f_send_msg) {
                f_send_msg = false;
                playTone();
                //send_sms();
            }
            image.setVisibility(View.VISIBLE);
        } else {
            f_send_msg = true;
            image.setVisibility(View.INVISIBLE);
        }

    }

    public void send_sms() {
        System.out.println("----------------------- SEND SMS");
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage("3383305701", null, "code 0000 Fall detected!", null, null);
            Toast.makeText(getApplicationContext(), "SMS Sent!",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),
                    "SMS faied, please try again later!",
                    Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }


    }

    public void enableStrictMode() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);
    }

    public void playTone() {
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
        r.play();
    }

}
