package qut.importsdkdemo;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;
import static java.lang.StrictMath.max;
import static qut.importsdkdemo.R.id.seekBar2;
import static qut.importsdkdemo.R.id.seekBarMeanPitchAfterChange;
import static qut.importsdkdemo.R.id.seekBarThreshold22;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    public static BaseProduct mProduct;
    private Handler mHandler;
//---------------------------------------------//
//-----------------CUSUM-----------------------//
//---------------------------------------------//
    private SeekBar seekBar;
    private SeekBar seekBarThreshold;
    private SeekBar seekBarMeanPitch;
    private TextView textView;
    private TextView textViewThreshold;
    private TextView nbSamples;
    private TextView meanPitchs;
    private TextView stdPitchs;
    private TextView cusumsPos;
    private TextView textViewMeanPitchAfterChange;
    private TextView changeDetected;
    private final double multiplier= (float) 0.05;
    private final double multiplierThreshold= (float) 0.5;
    private final double multiplierMeanAfterChange= (float) 0.05;
    private Timer noiseTimer;
    private double pitch;
    private double meanPitch;
    private double sPitch;
    private double stdPitch;
    private int nbSample;
    private double cusumPos;
    private double cusumNeg;
    private double meanPitchExpectedAfterChange;
    private double threshold;
    private int numberOfChange=0;
    private ToggleButton toggle;
    private double progressThreshold;
    private double progress;
    private double progressMeanAfterChange;
    // Graph view attributes
    private LineGraphSeries<DataPoint> pitchSeries;
    private int lastX=0;
    private LineGraphSeries<DataPoint> pitchMeanAfterChangeSeries;

//---------------------------------------------//
//-----------------CUSUM-----------------------//
//---------------------------------------------//

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,Manifest.permission.BLUETOOTH,
                    }
                    , 1);
        }
        setContentView(R.layout.activity_main);

        //Initialize DJI SDK Manager
        mHandler = new Handler(Looper.getMainLooper());
        DJISDKManager.getInstance().registerApp(this, mDJISDKManagerCallback);
        mProduct = (Aircraft) DJISDKManager.getInstance().getProduct();

           if (DJISampleApplication.getAircraftInstance() != null
                && DJISampleApplication.getAircraftInstance().getFlightController() != null) {
            DJISampleApplication.getAircraftInstance()
                    .getFlightController()
                    .setStateCallback(new FlightControllerState.Callback() {
                        @Override
                        public void onUpdate(final FlightControllerState djiStateData) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {

                                @Override
                                public void run() {
                                    textView.setText("Yaw : "
                                            + djiStateData.getAttitude().yaw
                                            + ","
                                            + "Roll : "
                                            + djiStateData.getAttitude().roll
                                            + "\n"
                                            + "Pitch : "
                                            + djiStateData.getAttitude().pitch);

                                }
                            });
                        }
                    });
        }
//---------------------------------------------//
//-----------------CUSUM-----------------------//
//---------------------------------------------//
        initializeVariables();
        threshold=10;
        meanPitchExpectedAfterChange=1;
        // Initialize the textview with '0'.

        // We get graph view instance
        final GraphView graph = (GraphView) findViewById(R.id.graph);
        // We initialize a series of data
        pitchSeries = new LineGraphSeries<DataPoint>();
        pitchMeanAfterChangeSeries = new LineGraphSeries<DataPoint>();

        graph.addSeries(pitchSeries);
        graph.addSeries(pitchMeanAfterChangeSeries);

        // Customize viewport
        final Viewport viewport = graph.getViewport();
        viewport.setYAxisBoundsManual(true);
        viewport.setXAxisBoundsManual(true);
        viewport.setMinX(0);
        viewport.setMaxX(100);
        viewport.setMinY(-1);
        viewport.setMaxY(1);
        viewport.setScrollable(true);
        viewport.setScalable(true);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                progress = progresValue;
                pitch=progress*multiplier;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                textView.setText("Pitch: " + pitch );
            }
        });
        seekBarThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                progressThreshold = progresValue;
                threshold=progressThreshold*multiplierThreshold;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                textViewThreshold.setText("Threshold: " + threshold );
            }
        });
        seekBarMeanPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                progressMeanAfterChange = progresValue;
                meanPitchExpectedAfterChange=progressMeanAfterChange*multiplier;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                textViewMeanPitchAfterChange.setText("PostMeanPitch: " + meanPitchExpectedAfterChange );
            }
        });


        toggle = (ToggleButton) findViewById(R.id.toggleButton);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    cusumsPos.setText("CUSUM = ");
                    changeDetected.setText("noChangeDetected");
                    // The toggle is enabled
                    noiseTimer=new Timer();
                    noiseTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            TimerMethod();
                        }
                    }, 0, 100);
                    initializeCusumVariables();
                    seekBarThreshold.setProgress((int)progressThreshold);
                    seekBarMeanPitch.setProgress((int)progressMeanAfterChange);
                    graph.removeAllSeries();
                    pitchSeries = new LineGraphSeries<DataPoint>();
                    pitchMeanAfterChangeSeries=new LineGraphSeries<DataPoint>();
                    graph.addSeries(pitchMeanAfterChangeSeries);
                    graph.addSeries(pitchSeries);
                    viewport.setMinX(0);
                    viewport.setMaxX(100);
                    lastX=0;
                    // styling series
                    pitchSeries.setTitle("pitch rate");
                    pitchSeries.setColor(Color.BLUE);
                    // styling series
                    pitchMeanAfterChangeSeries.setTitle("MeanAfterChange");
                    pitchMeanAfterChangeSeries.setColor(Color.RED);

                } else {
                    // The toggle is disabled
                    if (noiseTimer != null){
                        noiseTimer.cancel();}
                    seekBar.setProgress(0);

                }
            }
        });
//---------------------------------------------//
//-----------------CUSUM-----------------------//
//---------------------------------------------//
    }
    private DJISDKManager.SDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.SDKManagerCallback() {
        @Override
        public void onRegister(DJIError error) {
            Log.d(TAG, error == null ? "success" : error.getDescription());
            if(error == DJISDKError.REGISTRATION_SUCCESS) {
                DJISDKManager.getInstance().startConnectionToProduct();
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Register Success", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "register sdk failed, check if network is available", Toast.LENGTH_LONG).show();
                    }
                });
            }
            Log.e("TAG", error.toString());
        }
        @Override
        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
            mProduct = newProduct;
            if(mProduct != null) {
                mProduct.setBaseProductListener(mDJIBaseProductListener);
            }
            notifyStatusChange();
        }
    };
    private BaseProduct.BaseProductListener mDJIBaseProductListener = new BaseProduct.BaseProductListener() {
        @Override
        public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {
            if(newComponent != null) {
                newComponent.setComponentListener(mDJIComponentListener);
            }
            notifyStatusChange();
        }
        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {
        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };
    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };
//---------------------------------------------//
//-----------------CUSUM-----------------------//
//---------------------------------------------//

    // Method for GrahView

    private void addEntry(double pitch){
        //Here we choose to display maximum 10 points and we scroll to the end
        if (lastX<100){
            pitchSeries.appendData(new DataPoint(lastX++,pitch), false,100);
            pitchMeanAfterChangeSeries.appendData(new DataPoint(lastX,meanPitchExpectedAfterChange),false,100);
        }
        else{
            pitchSeries.appendData(new DataPoint(lastX++,pitch), true,100);
            pitchMeanAfterChangeSeries.appendData(new DataPoint(lastX,meanPitchExpectedAfterChange),true,100);
        }
    }
    // A private method to help us initialize our variables.
    private void initializeVariables() {
        seekBar = (SeekBar) findViewById(seekBar2);
        seekBarThreshold = (SeekBar) findViewById(seekBarThreshold22);
        seekBarMeanPitch=(SeekBar) findViewById(seekBarMeanPitchAfterChange);
        textView = (TextView) findViewById(R.id.textView2);
        textViewThreshold = (TextView) findViewById(R.id.textViewThreshold);
        textViewMeanPitchAfterChange = (TextView) findViewById(R.id.textViewMeanPitchAfterChange);
        nbSamples=(TextView) findViewById(R.id.nbSamples);
        meanPitchs=(TextView) findViewById(R.id.meanPitchs);
        stdPitchs=(TextView) findViewById(R.id.stdPitchs);
        changeDetected=(TextView) findViewById(R.id.changeDetected);
        cusumsPos=(TextView) findViewById(R.id.cusum);

    }
    private void initializeCusumVariables() {
        meanPitch=0;
        sPitch=0;
        stdPitch=0;
        nbSample=0;
        cusumPos=0;
        cusumsPos.setText("CUSUM = "+ cusumPos);
        progressThreshold = threshold/multiplierThreshold;
        progressMeanAfterChange=meanPitchExpectedAfterChange/multiplierMeanAfterChange;
        progress =pitch/multiplier;

    }
    private void processSample(double sample){
        double prev_mean=meanPitch;
        nbSample=nbSample+1;
        nbSamples.setText("nb Samples:  "+nbSample);
        meanPitch=meanPitch+(sample-meanPitch)/nbSample;
        meanPitchs.setText("meanPitch:  "+meanPitch);
        sPitch=sPitch+(sample-meanPitch)*(sample - prev_mean);
        stdPitch=sqrt(sPitch/nbSample);
        stdPitchs.setText("stdPitch:  "+stdPitch);
        if(nbSample>100){
            cusumPos=cusumPos + max(0,((meanPitchExpectedAfterChange-meanPitch)/stdPitch)*(sample-(meanPitch+meanPitchExpectedAfterChange)/2));
            cusumsPos.setText("CUSUM = "+ cusumPos);
            if (abs(cusumPos) > threshold){
                numberOfChange++;
                noiseTimer.cancel();
                changeDetected.setText("Change Detecteed !");
                toggle.toggle();
            }
        }

    }

    private void TimerMethod()
    {
        //This method is called directly by the timer
        //and runs in the same thread as the timer.

        //We call the method that will work with the UI
        //through the runOnUiThread method.
        this.runOnUiThread(Timer_Tick);
    }

    private Runnable Timer_Tick = new Runnable() {
        public void run() {
            double noise = 0.1*Math.random()-0.05;
            pitch=multiplier*seekBar.getProgress()+noise;
            processSample(pitch);
            textView.setText("Pitch: " + pitch);
            textViewThreshold.setText("Threshold"+threshold);
            textViewMeanPitchAfterChange.setText("PostMeanPitch"+meanPitchExpectedAfterChange);
            addEntry(pitch);

            //This method runs in the same thread as the UI.

            //Do something to the UI thread here

        }
    };
//---------------------------------------------//
//-----------------CUSUM-----------------------//
//---------------------------------------------//
}


