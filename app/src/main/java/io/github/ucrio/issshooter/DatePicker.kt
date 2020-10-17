package io.github.ucrio.issshooter

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import java.util.*

class DatePicker(date: Date, callback: DatePickerDialog.OnDateSetListener) : DialogFragment() {
    private var initDate: Date?
    private lateinit var onDateSetCallback: DatePickerDialog.OnDateSetListener
    init {
        initDate = date
        onDateSetCallback = callback
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = Calendar.getInstance()

        if (initDate != null) {
            c.setTime(initDate)
        }
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        return DatePickerDialog(
            activity!!,
            this.onDateSetCallback, year, month, day
        )
    }

    /*
    override fun onDateSet(
        view: android.widget.DatePicker, year: Int,
        monthOfYear: Int, dayOfMonth: Int
    ) {
    }
*/

}