package io.github.ucrio.issshooter
import android.animation.ObjectAnimator
import android.view.View
import android.widget.*
import java.util.*

class Indicators(context: FullscreenActivity) {
    private lateinit var context: FullscreenActivity

    private lateinit var textAzimuth: TextView
    private lateinit var textPitch: TextView
    private lateinit var checkAzimuth: CheckedTextView
    private lateinit var checkPitch: CheckedTextView

    private lateinit var imageViewTurnLeft90: ImageView
    private lateinit var imageViewTurnLeft180: ImageView
    private lateinit var imageViewTurnRight90: ImageView
    private lateinit var imageViewTurnRight180: ImageView
    private lateinit var imageViewArrowUpper: ImageView
    private lateinit var imageViewArrowUpperLeft: ImageView
    private lateinit var imageViewArrowUpperRight: ImageView
    private lateinit var imageViewArrowLower: ImageView
    private lateinit var imageViewArrowLowerLeft: ImageView
    private lateinit var imageViewArrowLowerRight: ImageView
    private lateinit var imageViewArrowLeft: ImageView
    private lateinit var imageViewArrowRight: ImageView
    private lateinit var imageViewDummy: ImageView

    private lateinit var turnImageViewList: List<ImageView>
    private lateinit var arrowImageViewList: List<ImageView>

    private var direction = 0
    private var elevation = 0.0

    val MARGIN_PITCH = 5
    val MARGIN_AZIMUTH = 15

    init {
        this.context = context

        imageViewTurnLeft90 = context.findViewById(R.id.imageViewTurnLeft90)
        imageViewTurnLeft180 = context.findViewById(R.id.imageViewTurnLeft180)
        imageViewTurnRight90 = context.findViewById(R.id.imageViewTurnRight90)
        imageViewTurnRight180 = context.findViewById(R.id.imageViewTurnRight180)
        imageViewArrowUpper = context.findViewById(R.id.imageViewArrowUpper)
        imageViewArrowUpperLeft = context.findViewById(R.id.imageViewArrowUpperLeft)
        imageViewArrowUpperRight = context.findViewById(R.id.imageViewArrowUpperRight)
        imageViewArrowLower = context.findViewById(R.id.imageViewArrowLower)
        imageViewArrowLowerLeft = context.findViewById(R.id.imageViewArrowLowerLeft)
        imageViewArrowLowerRight = context.findViewById(R.id.imageViewArrowLowerRight)
        imageViewArrowLeft = context.findViewById(R.id.imageViewArrowLeft)
        imageViewArrowRight = context.findViewById(R.id.imageViewArrowRight)
        imageViewDummy = ImageView(context)

        turnImageViewList = Arrays.asList(imageViewTurnLeft90, imageViewTurnLeft180, imageViewTurnRight90, imageViewTurnRight180)
        arrowImageViewList = Arrays.asList(imageViewArrowUpperLeft, imageViewArrowUpper, imageViewArrowUpperRight,
                                            imageViewArrowLeft, imageViewDummy, imageViewArrowRight,
                                            imageViewArrowLowerLeft, imageViewArrowLower, imageViewArrowLowerRight)

        textAzimuth = context.findViewById(R.id.textAzimuth)
        textPitch = context.findViewById(R.id.textPitch)
        checkAzimuth = context.findViewById(R.id.checkAzimuth)
        checkPitch = context.findViewById(R.id.checkPitch)
    }

    fun init(direction: Int, elevation: Double) {
        this.direction = direction
        this.elevation = elevation
        initIndicators()
    }

    fun initIndicators() {
        for (v in turnImageViewList) {
            addAnimation(v)
        }
        for (v in arrowImageViewList) {
            addAnimation(v)
        }
    }

    fun addAnimation(v: ImageView) {
        ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0.0f).apply {
            repeatCount = ObjectAnimator.INFINITE  // 無限に繰り返す
            repeatMode = ObjectAnimator.REVERSE  // 逆方向に繰り返す
            duration = 500
            start()
        }
    }

    fun update (azimuth: Double, pitch: Double, roll:Double) {

        clearAllIndicators()

        val pitchDiff = elevation - pitch
        val dirDiff = direction - azimuth

        // get indicator direction for pitch and update checkbox
        val indicatorPitch = evaluatePitch(pitchDiff)
        checkPitch.isChecked = indicatorPitch.isOK

        val dirDiffRad = Math.toRadians(dirDiff.toDouble())
        val cos = Math.cos(dirDiffRad)
        val sin = Math.sin(dirDiffRad)
        if (cos >= Math.cos(Math.PI/4)) {
            // -45deg <= diff <= 45deg
            val indicatorAzimuth = evaluateAzimuth(dirDiff)
            setVisible(arrowImageViewList[indicatorPitch.indicateDirInt + indicatorAzimuth.indicateDirInt])
            checkAzimuth.isChecked = indicatorAzimuth.isOK
        } else if (Math.sin(Math.PI/4) <= sin) {
            // 45deg <= diff <= 135deg
            setVisible(imageViewTurnRight90)
        } else if (sin <= Math.sin(-Math.PI/4)) {
            // -135deg <= diff <= -45deg
            setVisible(imageViewTurnLeft90)
        } else if (Math.sin(Math.PI) <= sin && sin < Math.sin(Math.PI/4)) {
            // 135 deg <= diff < 180 deg
            setVisible(imageViewTurnRight180)
        } else {
            // -180deg <= diff < -135deg
            setVisible(imageViewTurnLeft180)
        }

/*
        if (45 < dirDiff && dirDiff <= 135) {
            // turn right
            setVisible(imageViewTurnRight90)
        } else if (-135 < dirDiff && dirDiff <= -45) {
            // turn left
            setVisible(imageViewTurnLeft90)
        } else if (dirDiff <= -135) {
            // opposite left
            setVisible(imageViewTurnLeft180)
        } else if (135 < dirDiff){
            // opposite right
            setVisible(imageViewTurnRight180)
        } else {
            val indicatorAzimuth = evaluateAzimuth(dirDiff)
            setVisible(arrowImageViewList[indicatorPitch.indicateDirInt + indicatorAzimuth.indicateDirInt])
            checkAzimuth.isChecked = indicatorAzimuth.isOK
        }

 */
        updateText(pitch, azimuth)
    }

    private fun setVisible(v: ImageView) {
        v.isEnabled = true
        v.visibility = View.VISIBLE
    }

    private fun setInvisible(v: ImageView) {
        v.isEnabled = false
        v.visibility = View.INVISIBLE
    }

    private fun clearAllIndicators() {
        for (v in turnImageViewList) {
            setInvisible(v)
        }
        for (v in arrowImageViewList) {
            setInvisible(v)
        }
        checkAzimuth.isChecked = false
        checkPitch.isChecked = false
    }

    private fun evaluatePitch(pitchDiff: Double): PitchStatus {
        var isOK = false
        var indicateDir: PitchStatus.INDICATE_DIR_PITCH

        if (Math.abs(pitchDiff) < MARGIN_PITCH) {
            isOK = true
            indicateDir = PitchStatus.INDICATE_DIR_PITCH.CENTER_DUMMY
        } else if (pitchDiff < 0) {
            indicateDir = PitchStatus.INDICATE_DIR_PITCH.LOWER
        } else {
            indicateDir = PitchStatus.INDICATE_DIR_PITCH.UPPER
        }
        return PitchStatus(isOK, indicateDir)
    }

    private fun evaluateAzimuth(aziDiff: Double): AzimuthStatus {

        var isOK = false
        var indicateDir: AzimuthStatus.INDICATE_DIR_AZIMUTH

        val diffRad = Math.toRadians(aziDiff)
        val sin = Math.sin(diffRad)
        val marginSin = Math.sin(Math.toRadians(MARGIN_AZIMUTH.toDouble()))
        if (Math.abs(sin) <= marginSin) {
            // -MARGIN deg <= diff <= MARGIN deg
            isOK = true
            indicateDir = AzimuthStatus.INDICATE_DIR_AZIMUTH.CENTER
        } else if (sin < -marginSin) {
            // diff < -MARGIN deg
            indicateDir = AzimuthStatus.INDICATE_DIR_AZIMUTH.LEFT
        } else {
            // MARGIN deg < diff
            indicateDir = AzimuthStatus.INDICATE_DIR_AZIMUTH.RIGHT
        }
        return AzimuthStatus(isOK, indicateDir)
    }

    private fun updateText(pitch: Double, azimuth: Double) {
        textAzimuth.text = "方角: %.1f°".format((if(azimuth < 0) azimuth+360 else azimuth))
        textPitch.text = "仰角: %.1f°".format(pitch)
    }
}