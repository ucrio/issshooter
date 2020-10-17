package io.github.ucrio.issshooter

abstract class IndicatorStatus (isOK: Boolean) {
    /******************
     *   0 | 1 | 2
     *   ---------
     *   3 | * | 5
     *   ---------
     *   6 | 7 | 8
     ******************/
    var isOK: Boolean = false
    var indicateDirInt: Int = 9999

    init {
        this.isOK = isOK
    }
}

class PitchStatus (isOK: Boolean, indicatePitchDir: INDICATE_DIR_PITCH): IndicatorStatus (isOK) {
    enum class INDICATE_DIR_PITCH (val value: Int) {
        UPPER(1),
        CENTER_DUMMY(4),
        LOWER(7)
    }

    var indicatePitchDir: INDICATE_DIR_PITCH = INDICATE_DIR_PITCH.CENTER_DUMMY
        get() = field
        set(value) {
            field = value
            this.indicateDirInt = field.value
        }

    init {
        this.indicatePitchDir = indicatePitchDir
        this.indicateDirInt = indicatePitchDir.value
    }
}

class AzimuthStatus (isOK: Boolean, indicateAzimuthDir: INDICATE_DIR_AZIMUTH): IndicatorStatus (isOK) {
    enum class INDICATE_DIR_AZIMUTH (val value: Int) {
        LEFT(-1),
        CENTER(0),
        RIGHT(1),
        NONE(9999)
    }

    var indicateAzimuthDir: INDICATE_DIR_AZIMUTH = INDICATE_DIR_AZIMUTH.NONE
        get() = field
        set(value) {
            field = value
            this.indicateDirInt = field.value
        }

    init {
        this.indicateAzimuthDir = indicateAzimuthDir
        this.indicateDirInt = indicateAzimuthDir.value
    }
}