/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package com.grumpysailor.cordova.magnetsensor;

import java.util.List;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;

/**
 * This class listens to the accelerometer sensor and stores the latest
 * acceleration values x,y,z.
 */
public class MagnetSensor extends CordovaPlugin implements SensorEventListener {

    public static int STOPPED = 0;
    public static int STARTING = 1;
    public static int RUNNING = 2;
    public static int ERROR_FAILED_TO_START = 3;
   
    private boolean magnetSensorTrigger = false;                                // most recent acceleration values
    private long timestamp;                         // time of most recent value
    private int status;                                 // status of listener
    private int accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    private SensorManager mSensorManager;
    private Sensor mSensor;
    private Sensor mMagnetometer;
    private ArrayList<float[]> mSensorData;
    private float[] mOffsets = new float[20];                           

    private CallbackContext callbackContext;              // Keeps track of the JS callback context.

    private Handler mainHandler=null;
    private Runnable mainRunnable =new Runnable() {
        public void run() {
            MagnetSensor.this.timeout();
        }
    };

    /**
     * Create an accelerometer listener.
     */
    public MagnetSensor() {
        this.magnetSensorTrigger = false;
        this.timestamp = 0;
        this.setStatus(MagnetSensor.STOPPED);
     }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The associated CordovaWebView.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.mSensorManager = (SensorManager) cordova.getActivity().getSystemService(Context.SENSOR_SERVICE);
        this.mSensorData = new ArrayList();
    }

    /**
     * Executes the request.
     *
     * @param action        The action to execute.
     * @param args          The exec() arguments.
     * @param callbackId    The callback id used when calling back into JavaScript.
     * @return              Whether the action was valid.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("start")) {
            this.callbackContext = callbackContext;
            if (this.status != MagnetSensor.RUNNING) {
                // If not running, then this is an async call, so don't worry about waiting
                // We drop the callback onto our stack, call start, and let start and the sensor callback fire off the callback down the road
                this.start();
            }
        }
        else if (action.equals("stop")) {
            if (this.status == MagnetSensor.RUNNING) {
                this.stop();
            }
        } else {
          // Unsupported action
            return false;
        }

        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT, "");
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
        return true;
    }

    /**
     * Called by AccelBroker when listener is to be shut down.
     * Stop listener.
     */
    public void onDestroy() {
        this.stop();
    }

    //--------------------------------------------------------------------------
    // LOCAL METHODS
    //--------------------------------------------------------------------------
    //
    /**
     * Start listening for acceleration sensor.
     * 
     * @return          status of listener
    */
    private int start() {
        // If already starting or running, then just return
        if ((this.status == MagnetSensor.RUNNING) || (this.status == MagnetSensor.STARTING)) {
            return this.status;
        }

        this.setStatus(MagnetSensor.STARTING);

        // Get accelerometer from sensor manager
        List<Sensor> list = this.mSensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);

        // If found, then register as listener
        if ((list != null) && (list.size() > 0)) {
          this.mMagnetometer = list.get(0);
          this.mSensorManager.registerListener(this, this.mMagnetometer, SensorManager.SENSOR_DELAY_UI);
          this.setStatus(MagnetSensor.STARTING);
        } else {
          this.setStatus(MagnetSensor.ERROR_FAILED_TO_START);
          this.fail(MagnetSensor.ERROR_FAILED_TO_START, "No sensors found to register magnet sensor listening to.");
          return this.status;
        }

        // Set a timeout callback on the main thread.
        stopTimeout();
        mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.postDelayed(mainRunnable, 2000);

        return this.status;
    }
    private void stopTimeout() {
        if(mainHandler!=null){
            mainHandler.removeCallbacks(mainRunnable);
        }
    }
    /**
     * Stop listening to acceleration sensor.
     */
    private void stop() {
        stopTimeout();
        if (this.status != MagnetSensor.STOPPED) {
            this.mSensorManager.unregisterListener(this);
        }
        this.setStatus(MagnetSensor.STOPPED);
        this.accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    }

    /**
     * Returns an error if the sensor hasn't started.
     *
     * Called two seconds after starting the listener.
     */
    private void timeout() {
        if (this.status == MagnetSensor.STARTING) {
            this.setStatus(MagnetSensor.ERROR_FAILED_TO_START);
            this.fail(MagnetSensor.ERROR_FAILED_TO_START, "Accelerometer could not be started.");
        }
    }

    /**
     * Called when the accuracy of the sensor has changed.
     *
     * @param sensor
     * @param accuracy
     */
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Only look at accelerometer events
        if (sensor.getType() != Sensor.TYPE_MAGNETIC_FIELD) {
            return;
        }

        // If not running, then just return
        if (this.status == MagnetSensor.STOPPED) {
            return;
        }
        this.accuracy = accuracy;
    }

    /**
     * Sensor listener event.
     *
     * @param SensorEvent event
     */
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.equals(this.mMagnetometer)) {
            float[] values = event.values;
            if ((values[0] == 0.0F) && (values[1] == 0.0F)
                    && (values[2] == 0.0F)) {
                return;
            }
            addData((float[]) event.values.clone(), event.timestamp);
        }

        // If not running, then just return
        if (this.status == MagnetSensor.STOPPED) {
            return;
        }
        this.setStatus(MagnetSensor.RUNNING);
    }

    private void addData(float[] values, long time) {
        if (this.mSensorData.size() > 40) {
            this.mSensorData.remove(0);
        }
        this.mSensorData.add(values);

        evaluateModel();
    }

    private void evaluateModel() {
        if (this.mSensorData.size() < 40) {
            return;
        }
        float[] means = new float[2];
        float[] maximums = new float[2];
        float[] minimums = new float[2];

        float[] baseline = (float[]) this.mSensorData.get(this.mSensorData
                .size() - 1);
        for (int i = 0; i < 2; i++) {
            int segmentStart = 20 * i;

            float[] mOffsets = computeOffsets(segmentStart, baseline);

            means[i] = computeMean(mOffsets);
            maximums[i] = computeMaximum(mOffsets);
            minimums[i] = computeMinimum(mOffsets);
        }
        float min1 = minimums[0];
        float max2 = maximums[1];
        if ((min1 < 30.0F) && (max2 > 130.0F)) {
            this.magnetSensorTrigger = true;
            this.mSensorData.clear();
            this.win();
            
        }else {
            this.magnetSensorTrigger = false;
        }
        
        
    }

  

    /**
     * Called when the view navigates.
     */
    @Override
    public void onReset() {
        if (this.status == MagnetSensor.RUNNING) {
            this.stop();
        }
    }

    // Sends an error back to JS
    private void fail(int code, String message) {
        // Error object
        JSONObject errorObj = new JSONObject();
        try {
            errorObj.put("code", code);
            errorObj.put("message", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PluginResult err = new PluginResult(PluginResult.Status.ERROR, errorObj);
        err.setKeepCallback(true);
        callbackContext.sendPluginResult(err);
    }

    private void win() {
        // Success return object
        PluginResult result = new PluginResult(PluginResult.Status.OK, "");
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void setStatus(int status) {
        this.status = status;
    }
    private JSONObject getAccelerationJSON() {
        JSONObject r = new JSONObject();
        try {
            r.put("magnetSensorTrigger", this.magnetSensorTrigger);
            r.put("timestamp", this.timestamp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return r;
    }

    private float[] computeOffsets(int start, float[] baseline) {
        for (int i = 0; i < 20; i++) {
            float[] point = (float[]) this.mSensorData.get(start + i);
            float[] o = { point[0] - baseline[0], point[1] - baseline[1],
                    point[2] - baseline[2] };
            float magnitude = (float) Math.sqrt(o[0] * o[0] + o[1] * o[1]
                    + o[2] * o[2]);
            this.mOffsets[i] = magnitude;
        }
        return this.mOffsets;
    }

    private float computeMean(float[] offsets) {
        float sum = 0.0F;
        for (float o : offsets) {
            sum += o;
        }
        return sum / offsets.length;
    }

    private float computeMaximum(float[] offsets) {
        float max = (-1.0F / 0.0F);
        for (float o : offsets) {
            max = Math.max(o, max);
        }
        return max;
    }

    private float computeMinimum(float[] offsets) {
        float min = (1.0F / 0.0F);
        for (float o : offsets) {
            min = Math.min(o, min);
        }
        return min;
    }
}
