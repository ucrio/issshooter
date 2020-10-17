package io.github.ucrio.issshooter

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.Html
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.ikovac.timepickerwithseconds.MyTimePickerDialog
import com.ikovac.timepickerwithseconds.TimePicker
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

private val PERMISSIONS_REQUEST = 1

private val DATE_FORMAT = "yyyy/MM/dd"
private val TIME_FORMAT = "HH:mm:ss"

private val KEY_DATE_FROM = "dateFrom"
private val KEY_TIME_FROM = "timeFrom"
private val KEY_DATE_TO = "dateTo"
private val KEY_TIME_TO = "timeTo"
private val KEY_DIR_FROM = "directionFrom"
private val KEY_DIR_TO = "directionTo"
private val KEY_ELV_FROM = "elevationFrom"
private val KEY_ELV_TO = "elevationTo"

private lateinit var sp: SharedPreferences

private var calFrom = Calendar.getInstance()
private var calTo = Calendar.getInstance()
val sdfDateTime = SimpleDateFormat(DATE_FORMAT + " " + TIME_FORMAT)

class MainActivity : AppCompatActivity() {

    class TextWatcherImpl (storeKey: String, sharedPreferences: SharedPreferences): TextWatcher {
        private lateinit var key: String
        private lateinit var sp: SharedPreferences
        init {
            key = storeKey
            sp = sharedPreferences
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun afterTextChanged(s: Editable?) {
            sp.edit().putString(key, s.toString()).commit()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //setSupportActionBar(toolbar)


        MobileAds.initialize(this)
        val requestCfg = RequestConfiguration.Builder()
            .setTestDeviceIds(Arrays.asList(getString(R.string.test_device)))
            .build()
        MobileAds.setRequestConfiguration(requestCfg)

        requestPermissions()

        sp = PreferenceManager.getDefaultSharedPreferences(this)
        val spDateFrom = sp.getString(KEY_DATE_FROM, null)
        val spTimeFrom = sp.getString(KEY_TIME_FROM, null)
        val spDateTo = sp.getString(KEY_DATE_TO, null)
        val spTimeTo = sp.getString(KEY_TIME_TO, null)
        val spDirectionFrom = sp.getString(KEY_DIR_FROM, "0")
        val spElevationFrom = sp.getString(KEY_ELV_FROM, "0")
        //val spDirectionTo = sp.getString(KEY_DIR_TO, "0")
        //val spElevationTo = sp.getString(KEY_ELV_TO, "0")

        buttonGo.setOnClickListener {

            val intent = Intent(application, FullscreenActivity::class.java)
            intent.putExtra("dateTimeFrom", calFrom)
            intent.putExtra("dateTimeTo", calTo)
            intent.putExtra("directionFrom", directionFrom.getText().toString().toInt())
            //intent.putExtra("directionTo", directionTo.getText().toString().toInt())
            intent.putExtra("elevationFrom", elevationFrom.getText().toString().toDouble())
            //intent.putExtra("elevationTo", elevationTo.getText().toString().toDouble())
            startActivity(intent)
        }

        val sdfDate = SimpleDateFormat(DATE_FORMAT)
        val sdfTime = SimpleDateFormat(TIME_FORMAT)
        if (spDateFrom != null && spTimeFrom != null) {
            val dt = sdfDateTime.parse(spDateFrom + " " + spTimeFrom)
            val tmp = Calendar.getInstance()
            tmp.setTime(dt)
            if (calFrom.getTime().before(tmp.getTime())) {
                calFrom.setTime(dt)
                calTo.setTime(dt)
            }
        }
        if (spDateTo != null && spTimeTo != null) {
            val dt = sdfDateTime.parse(spDateTo + " " + spTimeTo)
            val tmp = Calendar.getInstance()
            tmp.setTime(dt)
            if (calFrom.getTime().before(tmp.getTime())) {
                calTo.setTime(dt)
            }
        }

        dateFrom.setText(sdfDate.format(calFrom.getTime()))
        timeFrom.setText(sdfTime.format(calFrom.getTime()))
        dateTo.setText(sdfDate.format(calTo.getTime()))
        timeTo.setText(sdfTime.format(calTo.getTime()))

        directionFrom.setFilters(arrayOf<InputFilter>(InputNumFilter("0", "359")))
        //directionTo.setFilters(arrayOf<InputFilter>(InputNumFilter("0", "359")))
        elevationFrom.setFilters(arrayOf<InputFilter>(InputNumFilter("0", "90")))
        //elevationTo.setFilters(arrayOf<InputFilter>(InputNumFilter("0", "90")))

        directionFrom.addTextChangedListener(TextWatcherImpl(KEY_DIR_FROM, sp))
        //directionTo.addTextChangedListener(TextWatcherImpl(KEY_DIR_TO, sp))
        elevationFrom.addTextChangedListener(TextWatcherImpl(KEY_ELV_FROM, sp))
        //elevationTo.addTextChangedListener(TextWatcherImpl(KEY_ELV_TO, sp))

        directionFrom.setText(spDirectionFrom)
        directionTo.setText("---")
        elevationFrom.setText(spElevationFrom)
        elevationTo.setText("--")

        //val data_source = findViewById<View>(R.id.datasource_url) as TextView
        datasource_url.text = Html.fromHtml("<a href=\"http://kibo.tksc.jaxa.jp/sp/\">http://kibo.tksc.jaxa.jp/sp/</a>")
        val mMethod = LinkMovementMethod.getInstance()
        datasource_url.movementMethod = mMethod

        //val privacy = findViewById<View>(R.id.privacy) as TextView
        privacy.text = Html.fromHtml("<a href=\"https://ucrio.github.io/issshooter/\">Privacy Policy</a>")
        privacy.movementMethod = mMethod

        // set ad
        val adView = findViewById<AdView>(R.id.ad_main)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun showDatePickerDialog(v: View) {
        val cal = getCalendar(v.getId())

        val newFragment = DatePicker(cal.getTime(), object: DatePickerDialog.OnDateSetListener {
            override fun onDateSet(
                view: android.widget.DatePicker?,
                year: Int,
                month: Int,
                dayOfMonth: Int
            ) {
                val str = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, dayOfMonth)
                (v as EditText).setText(str)
                setCalendar(v.getId())
                controlDateTime()
            }
        })
        newFragment.show(supportFragmentManager, "datePicker")

    }

    fun showTimePickerDialog(v: View) {

        val cal = getCalendar(v.getId())
        val mTimePicker = MyTimePickerDialog(this, object: MyTimePickerDialog.OnTimeSetListener {
            override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int, seconds: Int) {
                val str = String.format(Locale.US, "%02d:%02d:%02d", hourOfDay, minute, seconds)
                (v as EditText).setText(str)
                setCalendar(v.getId())
                controlDateTime()
            }
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND), true)
        mTimePicker.show()

    }

    fun getCalendar(id: Int): Calendar {
        var cal: Calendar
        when(id) {
            R.id.dateFrom, R.id.timeFrom -> {
                cal = calFrom
            }
            R.id.dateTo, R.id.timeTo -> {
                cal = calTo
            }
            else -> {
                cal = Calendar.getInstance()
            }
        }

        return cal
    }
    fun setCalendar(id: Int) {
        when(id) {
            R.id.dateFrom, R.id.timeFrom -> {
                val str_d = dateFrom.getText().toString()
                val str_t = timeFrom.getText().toString()
                calFrom.setTime(sdfDateTime.parse(str_d+ " " + str_t))
                sp.edit().putString(KEY_DATE_FROM, str_d).commit()
                sp.edit().putString(KEY_TIME_FROM, str_t).commit()
            }
            R.id.dateTo, R.id.timeTo -> {
                val str_d = dateTo.getText().toString()
                val str_t = timeTo.getText().toString()
                calTo.setTime(sdfDateTime.parse(str_d+ " " + str_t))
                sp.edit().putString(KEY_DATE_TO, str_d).commit()
                sp.edit().putString(KEY_TIME_TO, str_t).commit()
            }
        }
    }
    fun controlDateTime() {
        if (!compareDateTime()) {
            dateTo.setText(dateFrom.getText())
            timeTo.setText(timeFrom.getText())
            setCalendar(R.id.dateTo)
        }
    }
    fun compareDateTime(): Boolean {
        return calFrom.getTime().before(calTo.getTime())
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                Toast.makeText(
                    this@MainActivity,
                    "Camera permission is required for this app",
                    Toast.LENGTH_LONG
                ).show()
            }
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(
                    this@MainActivity,
                    "Storage access permission is required for this app",
                    Toast.LENGTH_LONG
                ).show()
            }
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), PERMISSIONS_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                // DO NOTHING
            } else {
                finish()
            }
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
}
