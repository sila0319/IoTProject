package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.request.OnDataPointListener
import com.google.android.gms.fitness.request.SensorRequest
import org.airpage.heartbeat.R
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private val TAG = MainActivity::class.java.name
    private var googleApiClient: GoogleApiClient? = null
    private var authInProgress = false
    private var onDataPointListener: OnDataPointListener? = null
    private val missingPermission: MutableList<String> = ArrayList()
    private var bCheckStarted = false
    private var bGoogleConnected = false
    private var btnStart: Button? = null
    private var spinner: ProgressBar? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: WakeLock? = null
    private var textMon: TextView? = null
    @SuppressLint("InvalidWakeLockTag")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //심박패턴을 측정하는 동안 화면이 꺼지지 않도록 제어하기 위해 전원관리자를 얻어옵니다
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager!!.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "WAKELOCK"
        )
        initUI()
        //필요한 권한을 얻었는지 확인하고, 얻지 않았다면 권한 요청을 하기 위한 코드를 호출합니다
        checkAndRequestPermissions()
    }

    private fun initUI() {
        //심박수를 측정하는 Google API의 호출을 위해 API 클라이언트를 초기화 합니다
        initGoogleApiClient()
        textMon = findViewById<TextView>(R.id.textMon)
        spinner = findViewById<ProgressBar>(R.id.progressBar1)
        spinner!!.setVisibility(View.INVISIBLE)
        btnStart = findViewById<Button>(R.id.btnStart!!)
        btnStart!!.setText("Wait please ...")
        btnStart!!.setEnabled(false)
        btnStart!!.setOnClickListener(View.OnClickListener {
            if (bCheckStarted) {
                //btnStart!!.setText(R.string.msg_start);
                btnStart!!!!.setText("Start")
                bCheckStarted = false
                unregisterFitnessDataListener()
                spinner!!.setVisibility(View.INVISIBLE)
                wakeLock!!.release()
            } else {
                //버튼을 처음 클릭할 경우 Google API 클라이언트에 로그인이 되어있는 상태인지를 확인합니다.
                //만약 로그인이 되어 있는 상태라면,
                if (bGoogleConnected == true) {
                    //심박수를 측정하기 위한 API를 설정합니다
                    findDataSources()
                    //심박수의 측정이 시작되면 심박수 정보를 얻을 콜백함수를 등록/설정하는 함수를 호출합니다
                    registerDataSourceListener(DataType.TYPE_HEART_RATE_BPM)
                    btnStart!!.setText("Stop")
                    //btnStart!!.setText(R.string.msg_stop);
                    bCheckStarted = true
                    spinner!!.setVisibility(View.VISIBLE)
                    //화면이 꺼지지 않도록 설정합니다
                    wakeLock!!.acquire()
                } else {
                    //Google API 클라이언트에 로그인 합니다
                    if (googleApiClient != null) googleApiClient!!.connect()
                }
            }
        })
    }

    private fun initGoogleApiClient() {
        googleApiClient = GoogleApiClient.Builder(this)
            .addApi(Fitness.SENSORS_API)
            .addScope(Scope(Scopes.FITNESS_BODY_READ)) //.addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
            .addConnectionCallbacks(
                object : ConnectionCallbacks {
                    //Google API 클라이언트의 로그인에 성공하면 호출이 되는 콜백입니다
                    override fun onConnected(bundle: Bundle?) {
                        Log.d(TAG, "initGoogleApiClient() onConnected good...")
                        bGoogleConnected = true
                        btnStart!!!!.text = "Start"
                        btnStart!!!!.isEnabled = true
                    }

                    override fun onConnectionSuspended(i: Int) {
                        if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                            Log.d(TAG, "onConnectionSuspended() network_lost bad...")
                        } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                            Log.d(TAG, "onConnectionSuspended() service_disconnected bad...")
                        }
                    }
                }
            )
            .addOnConnectionFailedListener(
                OnConnectionFailedListener { result ->
                    Log.d(TAG, "Connection failed. Cause: $result")
                    if (!result.hasResolution()) {
                        finish()
                        return@OnConnectionFailedListener
                    }
                    if (!authInProgress) {
                        try {
                            Log.d(TAG, "Attempting to resolve failed connection")
                            authInProgress = true
                            result.startResolutionForResult(
                                this@MainActivity,
                                AUTH_REQUEST
                            )
                        } catch (e: SendIntentException) {
                            Log.e(
                                TAG,
                                "Exception while starting resolution activity", e
                            )
                            finish()
                        }
                    } else {
                        finish()
                    }
                }
            )
            .build()
    }

    /**
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private fun checkAndRequestPermissions() {
        // Check for permissions
        for (eachPermission in REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    eachPermission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermission.add(eachPermission)
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            if (googleApiClient != null) googleApiClient!!.connect()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                missingPermission.toTypedArray(),
                REQUEST_PERMISSION_CODE
            )
        } else {
            if (googleApiClient != null) googleApiClient!!.connect()
        }
    }

    /**
     * Result of runtime permission request
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (i in grantResults.indices.reversed()) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i])
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            initGoogleApiClient()
            if (googleApiClient != null) googleApiClient!!.connect()
        } else {
            Toast.makeText(applicationContext, "Failed get permissions", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun findDataSources() {
        Fitness.SensorsApi.findDataSources(
            googleApiClient, DataSourcesRequest.Builder()
                .setDataTypes(DataType.TYPE_HEART_RATE_BPM) // .setDataTypes(DataType.TYPE_SPEED)
                // .setDataTypes(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .setDataSourceTypes(DataSource.TYPE_RAW)
                .build()
        )
            .setResultCallback { dataSourcesResult ->
                for (dataSource in dataSourcesResult.dataSources) {
                    if (dataSource.dataType == DataType.TYPE_HEART_RATE_BPM && onDataPointListener == null) {
                        Log.d(
                            TAG,
                            "findDataSources onResult() registering dataSource=$dataSource"
                        )
                        registerDataSourceListener(DataType.TYPE_HEART_RATE_BPM)
                    }
                }
            }
    }

    private fun registerDataSourceListener(dataType: DataType) {
        onDataPointListener = OnDataPointListener { dataPoint ->

            // 심박수가 측정되면 심박수를 얻어올 수 있는 콜백입니다
            for (field in dataPoint.dataType.fields) {
                val aValue = dataPoint.getValue(field)
                //Log.d(TAG, "Detected DataPoint field: " + field.getName());
                //Log.d(TAG, "Detected DataPoint value: " + aValue);

                //addContentToView("dataPoint=" + field.getName() + " " + aValue + "\n");
                addContentToView(aValue.asFloat())
            }
        }
        Fitness.SensorsApi.add(
            googleApiClient,
            SensorRequest.Builder()
                .setDataType(dataType)
                .setSamplingRate(2, TimeUnit.SECONDS)
                .setAccuracyMode(SensorRequest.ACCURACY_MODE_DEFAULT)
                .build(),
            onDataPointListener
        )
            .setResultCallback { status ->
                if (status.isSuccess) {
                    Log.d(TAG, "onDataPointListener  registered good")
                } else {
                    Log.d(TAG, "onDataPointListener failed to register bad")
                }
            }
    }

    private fun unregisterFitnessDataListener() {
        if (onDataPointListener == null) {
            return
        }
        if (googleApiClient == null) {
            return
        }
        if (googleApiClient!!.isConnected == false) {
            return
        }
        Fitness.SensorsApi.remove(
            googleApiClient,
            onDataPointListener
        )
            .setResultCallback { status ->
                if (status.isSuccess) {
                    Log.d(TAG, "Listener was removed!")
                } else {
                    Log.d(TAG, "Listener was not removed.")
                }
            }
        // [END unregister_data_listener]
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onStart connect attempted")
    }

    override fun onStop() {
        super.onStop()
        unregisterFitnessDataListener()
        if (googleApiClient != null && googleApiClient!!.isConnected) {
            googleApiClient!!.disconnect()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
       super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTH_REQUEST) {
            authInProgress = false
            if (resultCode == RESULT_OK) {
                if (!googleApiClient!!.isConnecting && !googleApiClient!!.isConnected) {
                    googleApiClient!!.connect()
                    Log.d(TAG, "onActivityResult googleApiClient.connect() attempted in background")
                }
            }
        }
    }

    @Synchronized
    private fun addContentToView(value: Float) {
        runOnUiThread {
            if (spinner!!.visibility == View.VISIBLE) spinner!!.visibility =
                View.INVISIBLE
            Log.d(TAG, "Heart Beat Rate Value : $value")
            textMon!!.text = "Heart Beat Rate Value : $value"
        }
    }

    companion object {
        private const val AUTH_REQUEST = 1
        private val REQUIRED_PERMISSION_LIST = arrayOf(
            Manifest.permission.BODY_SENSORS
        )
        private const val REQUEST_PERMISSION_CODE = 12345
    }
}