package io.github.ucrio.issshooter

import android.animation.ObjectAnimator
import android.app.Fragment
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.hardware.*
import android.hardware.Camera
import android.hardware.Camera.PreviewCallback
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import io.github.ucrio.issshooter.customview.AutoFitTextureView
import kotlinx.android.synthetic.main.activity_fullscreen.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class FullscreenActivity : AppCompatActivity(), SensorEventListener, OnImageAvailableListener, PreviewCallback {

    val FORMAT_COUNTDOWN = "HH:mm:ss"
    val DESIRED_PREVIEW_SIZE: Size = Size(1920, 1080)

    private var useCamera2API: Boolean = true

    private lateinit var shutterButton : Button
    private lateinit var numberPicker  : NumberPicker
    //private lateinit var previewView   : TextureView
    private lateinit var textureView: AutoFitTextureView
    private lateinit var imageReader   : ImageReader


    private lateinit var previewRequestBuilder : CaptureRequest.Builder
    private lateinit var previewRequest        : CaptureRequest
    private var backgroundHandler              : Handler?                = null
    private var backgroundThread               : HandlerThread?          = null
    private var cameraDevice                   : CameraDevice?           = null
    private lateinit var captureSession        : CameraCaptureSession

    private lateinit var calFrom: Calendar
    private lateinit var calTo: Calendar

    private val sdfCountDown = SimpleDateFormat(FORMAT_COUNTDOWN)

    private lateinit var sensorManager: SensorManager
    private var rotationMatrix = FloatArray(9)
    private var accel = FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private var attitude = FloatArray(3)


    private lateinit var indicators: Indicators
    private lateinit var imageViewArrow: ImageView
    private lateinit var imageViewTurnRight: ImageView
    private lateinit var imageViewTurnLeft: ImageView
    private var elevation: Double = 0.0
    private var direction: Int = 0


    var sdf = SimpleDateFormat("yyyyMMdd_hhmmssSSS")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // set ad
        val adView = findViewById<AdView>(R.id.ad_camera)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        //previewView = findViewById(R.id.SurfaceView)
        //val params = previewView.layoutParams
        //params.width = DESIRED_PREVIEW_SIZE.width
        //params.height = DESIRED_PREVIEW_SIZE.height
        //previewView.setLayoutParams(ViewGroup.LayoutParams(DESIRED_PREVIEW_SIZE.width, DESIRED_PREVIEW_SIZE.height))
        //previewView.surfaceTextureListener = surfaceTextureListener
        startBackgroundThread()

        intent = getIntent()
        calFrom = intent.getSerializableExtra("dateTimeFrom") as Calendar
        calTo = intent.getSerializableExtra("dateTimeTo") as Calendar
        elevation = intent.getDoubleExtra("elevationFrom", 0.0)
        direction = intent.getIntExtra("directionFrom", 0)

        if (direction > 180) {
            direction -= 360
        }

        sdfCountDown.setTimeZone(TimeZone.getTimeZone("GMT"))

        var countMillis = calFrom.getTimeInMillis() - Calendar.getInstance().getTimeInMillis()
        var durationMillis = calTo.getTimeInMillis() - calFrom.getTimeInMillis()
        textEndCountDown.setText("終了まで" + sdfCountDown.format(Date(durationMillis)))

        val endcountdown = object: CountDownTimer(countMillis, 100) {
            override fun onTick(millisUntilFinished: Long) {
                textEndCountDown.setText("終了まで" + sdfCountDown.format(Date(millisUntilFinished)))
            }
            override fun onFinish() {

            }
        }
        val startcountdown = object: CountDownTimer(countMillis, 100) {
            override fun onTick(millisUntilFinished: Long) {
                textStartCountDown.setText("開始まで" + sdfCountDown.format(Date(millisUntilFinished)))
            }
            override fun onFinish() {
                endcountdown.start()
                ObjectAnimator.ofFloat(textStartCountDown, "alpha", 1.0f, 0.0f).apply {
                    repeatCount = 7
                    repeatMode = ObjectAnimator.REVERSE
                    duration = 500
                    start()
                }
            }
        }
        startcountdown.start()

        shutterButton = findViewById(R.id.Shutter);

        /**
         * シャッターボタンにイベント生成
         */
        shutterButton.setOnClickListener {
            textureView = findViewById(R.id.texture)
            saveBitmap(textureView.bitmap, sdf.format(Calendar.getInstance().time), getContentResolver())
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        indicators = Indicators(this)
        indicators.init(direction, elevation)

        val radius = imageViewCompassCircle.layoutParams.width/2
        val posCenterX = imageViewCompassCircle.translationX + radius + imageViewCompassCircle.marginLeft
        val posCenterY = imageViewCompassCircle.translationY + radius + imageViewCompassCircle.marginTop
        val ptX = posCenterX + radius * Math.sin(Math.toRadians(direction.toDouble())).toFloat()
        val ptY = posCenterY - radius * Math.cos(Math.toRadians(direction.toDouble())).toFloat()
        var ptView = object: View(this) {
            override fun onDraw(canvas: Canvas) {
                val paint = Paint();
                paint.setColor(Color.argb(255, 255, 255, 255));

                // アンチエイリアスの円を描画
                paint.setAntiAlias(false);
                canvas.drawCircle(ptX, ptY, 15f, paint);
            }
        }

        frameLayout2.addView(ptView)
        ObjectAnimator.ofFloat(ptView, "alpha", 1.0f, 0.0f).apply {
            repeatCount = ObjectAnimator.INFINITE  // 無限に繰り返す
            repeatMode = ObjectAnimator.REVERSE  // 逆方向に繰り返す
            duration = 500
            start()
        }

        setFragment()
    }

    /**
     * カメラをバックグラウンドで実行
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * TextureView Listener
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener
    {
        // TextureViewが有効になった
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
        {
            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG,2)
            //openCamera()
            setFragment()
        }

        // TextureViewのサイズが変わった
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) { }

        // TextureViewが更新された
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) { }

        // TextureViewが破棄された
        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean
        {
            return false
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private fun isHardwareLevelSupported(
        characteristics: CameraCharacteristics, requiredLevel: Int
    ): Boolean {
        val deviceLevel =
            characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        return if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            requiredLevel == deviceLevel
        } else requiredLevel <= deviceLevel
        // deviceLevel is not LEGACY, can use numerical sort
    }

    private fun chooseCamera(): String? {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics =
                    manager.getCameraCharacteristics(cameraId)

                // We don't use a front facing camera in this sample.
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }
                val map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        ?: continue

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL
                        || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                ))
                Log.i("ISSShooter", "Camera API lv2?: %s".format(useCamera2API.toString()))
                return cameraId
            }
        } catch (e: CameraAccessException) {
            Log.e("ISSShooter","Not allowed to access camera", e)
        }
        return null
    }

    protected fun setFragment() {
        val cameraId = chooseCamera()
        val fragment: Fragment
        if (useCamera2API) {
            val camera2Fragment: CameraConnectionFragment = CameraConnectionFragment.newInstance(
                object : CameraConnectionFragment.ConnectionCallback {
                    override fun onPreviewSizeChosen(
                        size: Size,
                        rotation: Int
                    ) {
                        //previewHeight = size.height
                        //previewWidth = size.width
                        //this@FullscreenActivity.onPreviewSizeChosen(size, rotation)
                    }
                },
                this,
                R.layout.camera_connection_fragment,
                DESIRED_PREVIEW_SIZE
            )
            camera2Fragment.setCamera(cameraId)
            fragment = camera2Fragment
        } else {
            fragment = LegacyCameraConnectionFragment(this, R.layout.camera_connection_fragment, DESIRED_PREVIEW_SIZE)
        }
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
/*
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes.get(0),
                    yuvBytes.get(1),
                    yuvBytes.get(2),
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes
                )
            }
            postInferenceCallback = Runnable {
                image.close()
            }
        } catch (e: java.lang.Exception) {
            Log.e("ISSSHooter", "Exception!", e)
            return
        }

 */
    }

    override fun onPreviewFrame( bytes: ByteArray?, camera: Camera) {
        /*
        if (isProcessingFrame) {
            io.github.ucrio.oneshot.CameraActivity.LOGGER.w("Dropping frame!")
            return
        }
        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                val previewSize =
                    camera.parameters.previewSize
                previewHeight = previewSize.height
                previewWidth = previewSize.width
                rgbBytes = IntArray(previewWidth * previewHeight)
                onPreviewSizeChosen(Size(previewSize.width, previewSize.height), 90)
            }
        } catch (e: java.lang.Exception) {
            io.github.ucrio.oneshot.CameraActivity.LOGGER.e(e, "Exception!")
            return
        }
        isProcessingFrame = true
        yuvBytes.get(0) = bytes
        yRowStride = previewWidth
        imageConverter = Runnable {
            ImageUtils.convertYUV420SPToARGB8888(
                bytes,
                previewWidth,
                previewHeight,
                rgbBytes
            )
        }
        postInferenceCallback = Runnable {
            camera.addCallbackBuffer(bytes)
        }

         */
    }

/*
    protected fun onPreviewSizeChosen(size: Size, rotation: Int)
    {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            io.github.ucrio.oneshot.ClassifierActivity.TEXT_SIZE_DIP,
            resources.displayMetrics
        )
        borderedText = BorderedText(textSizePx)
        borderedText.setTypeface(Typeface.MONOSPACE)

        recreateClassifier(getModel(), getDevice(), getNumThreads())
        if (classifier == null) {
            io.github.ucrio.oneshot.ClassifierActivity.LOGGER.e("No classifier on preview!")
            return
        }
*/

    /**
     * カメラ起動処理関数
     */
/*
    private fun openCamera() {
        /**
         * カメラマネジャーの取得
         */
        val manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            /**
             * カメラIDの取得
             */
            val camerId: String = manager.cameraIdList[0]

            /**
             * カメラ起動パーミッションの確認
             */
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            if (permission != PackageManager.PERMISSION_GRANTED) {
                //requestPermissions()
                //return
                finish()
            }

            /**
             * カメラ起動
             */
            manager.openCamera(camerId, stateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * カメラ状態取得コールバック関数
     */
    private val stateCallback = object : CameraDevice.StateCallback() {

        /**
         * カメラ接続完了
         */
        override fun onOpened(cameraDevice: CameraDevice) {
            this@FullscreenActivity.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        /**
         * カメラ切断
         */
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
            this@FullscreenActivity.cameraDevice = null
        }

        /**
         * カメラエラー
         */
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            finish()
        }
    }

    /**
     * カメラ画像生成許可取得ダイアログを表示
     */
    private fun createCameraPreviewSession()
    {
        try
        {
            val texture = previewView.surfaceTexture
            texture.setDefaultBufferSize(previewView.width, previewView.height)

            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader.surface),
                object : CameraCaptureSession.StateCallback()
                {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession)
                    {
                        if (cameraDevice == null) return
                        try
                        {
                            captureSession = cameraCaptureSession
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            previewRequest = previewRequestBuilder.build()
                            cameraCaptureSession.setRepeatingRequest(previewRequest, null, Handler(backgroundThread?.looper))
                        } catch (e: CameraAccessException) {
                            Log.e("erfs", e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        //Tools.makeToast(baseContext, "Failed")
                    }
                }, null)
        } catch (e: CameraAccessException) {
            Log.e("erf", e.toString())
        }
    }
/*
    private fun getApplicationOrientation(): Int {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay.rotation
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> throw IllegalStateException()
        }
    }

    private fun rotateTextureView() {
        //val texture = previewView.surfaceTexture
        val orientation = getApplicationOrientation()
        val viewWidth = previewView.width
        val viewHeight = previewView.height
        val matrix = Matrix()
        matrix.postRotate(- orientation.toFloat(), viewWidth * 0.5F, viewHeight * 0.5F)
        previewView.setTransform(matrix)
    }

    fun getSupportedPreviewSizes(cameraId: String?): List<Size> {
        var previewSizes: List<Size> = ArrayList()
        val cameraManager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            val map = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?: return previewSizes
            previewSizes = (map.getOutputSizes(SurfaceTexture::class.java)).toList()
            val sizeComparator: Comparator<Size> =
                object : Comparator<Size> {
                    override fun compare(lhs: Size, rhs: Size): Int {
                        var result = rhs.width - lhs.width
                        if (result == 0) {
                            result = rhs.height - lhs.height
                        }
                        return result
                    }
                }

            Collections.sort(previewSizes, sizeComparator)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return previewSizes
    }

    fun choosePreviewSize(cameraId: String?) {
        // 端末の向き.
        val displayRotation: Int = getWindowManager().getDefaultDisplay().getRotation()
        // カメラの向き.
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val sensorOrientation: Int
        sensorOrientation = try {
            val characteristics =
                cameraManager.getCameraCharacteristics(cameraId!!)
            val tempSO = characteristics[CameraCharacteristics.SENSOR_ORIENTATION]
            tempSO ?: 0
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            0
        }
        var viewWidth: Int = previewView.getWidth()
        var viewHeight: Int = previewView.getHeight()
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> if (sensorOrientation == 90 || sensorOrientation == 270) {
                viewWidth = previewView.getHeight()
                viewHeight = previewView.getWidth()
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> if (sensorOrientation == 0 || sensorOrientation == 180) {
                viewWidth = previewView.getHeight()
                viewHeight = previewView.getWidth()
            }
        }
        val sameAspectSizes: MutableList<Size> = ArrayList()
        val previewSizes: List<Size> = getSupportedPreviewSizes(cameraId)
        for (previewSize in previewSizes) {
            val w: Int = previewSize.getWidth()
            val h: Int = previewSize.getHeight()
            if (h == w * DESIRED_PREVIEW_SIZE.height / DESIRED_PREVIEW_SIZE.width) {
                if (w >= viewWidth && h >= viewHeight) {
                    sameAspectSizes.add(previewSize)
                }
            }
        }
        if (0 < sameAspectSizes.size) {
            val previewSize: Size = Collections.min(sameAspectSizes,
                Comparator { lhs, rhs -> java.lang.Long.signum(lhs.getWidth() as Long * lhs.getHeight() - rhs.getWidth() as Long * rhs.getHeight()) })
            mPreviewSize.set(previewSize.getWidth(), previewSize.getHeight())
        } else {
            val previewSize: Size = previewSizes[0]
            mPreviewSize.set(previewSize.getWidth(), previewSize.getHeight())
        }
    }
*/

 */
    override protected fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {accelerometer  ->
            sensorManager.registerListener(
                this,
                accelerometer ,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {magneticField  ->
            sensorManager.registerListener(
                this,
                magneticField ,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.getType()) {
            Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomagnetic, 0, geomagnetic.size)
            Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, accel, 0, accel.size)
        }
        if(geomagnetic != null && accel != null){

            SensorManager.getRotationMatrix(
                rotationMatrix, null,
                accel, geomagnetic)

            var outR = FloatArray(9)
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, outR)

            SensorManager.getOrientation(
                outR,
                attitude)
        }
        val azimuth = Math.toDegrees(attitude[0].toDouble())
        val pitch = -Math.toDegrees(attitude[1].toDouble())
        val roll = Math.toDegrees(attitude[2].toDouble())

        indicators .update(azimuth, pitch, roll)

        val imageViewCompassArrow = findViewById<ImageView>(R.id.imageViewCompassArrow)
        imageViewCompassArrow.setRotation(azimuth.toFloat())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onPause() {
        super.onPause()
        //リスナーを解除しないとバックグラウンドにいるとき常にコールバックされ続ける
        sensorManager.unregisterListener(this)
    }

    private fun animationRotate() {

        // RotateAnimation(float fromDegrees, float toDegrees, int pivotXType, float pivotXValue, int pivotYType,float pivotYValue)
        val rotate = RotateAnimation(
            0.0f, 45.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )

        // animation時間 msec
        rotate.setDuration(100)
        // 繰り返し回数
        rotate.setRepeatCount(1)
        // animationが終わったそのまま表示にする
        rotate.setFillAfter(true)

        //アニメーションの開始
        shutterButton.startAnimation(rotate)
    }

    private fun saveBitmap(bitmap: Bitmap, filename: String, contentResolver: ContentResolver): Boolean {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
        Log.i("ISSShooter", "Saving %dx%d bitmap to %s.".format(bitmap.getWidth(), bitmap.getHeight(), root));
        val dir = File(root);

        if (!dir.mkdirs()) {
            Log.i("ISSShooter", "Make dir failed");
        }

        val fname = filename + ".jpg";
        val file = File(dir, fname);
        if (file.exists()) {
            // recursive: save with another name and return
            return saveBitmap(bitmap, filename + "_01", contentResolver);
        }
        try {
            val out = FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            animationRotate()
            Toast.makeText(this, "Saved: " + fname, Toast.LENGTH_SHORT).show()

            val contentValues = ContentValues();
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            contentValues.put("_data", file.getAbsolutePath());

            contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

            return true;
        } catch (e: Exception) {
            Log.e("ISSShooter", "Failed to save image to %s.".format(root), e);
            return false;
        }
    }
}
