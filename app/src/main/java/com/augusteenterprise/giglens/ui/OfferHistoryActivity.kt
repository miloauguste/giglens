package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic) - 2026-06-23
// Full offer history screen: Day (with prev/next navigation) / Week / Month tabs.
// Tapping a row opens a bottom sheet with all captured offer fields.

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.augusteenterprise.giglens.GigLensApp
import com.augusteenterprise.giglens.R
import com.augusteenterprise.giglens.data.OfferCapture
import com.augusteenterprise.giglens.databinding.ActivityOfferHistoryBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class OfferHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfferHistoryBinding
    private lateinit var adapter: OfferHistoryAdapter

    private var activeTab = "day"
    private val selectedDay = Calendar.getInstance()   // day displayed in Day tab
    private val today       = Calendar.getInstance()   // reference point — never mutated

    private val dayLabelFmt   = SimpleDateFormat("EEE, MMM d", Locale.US)
    private val detailDateFmt = SimpleDateFormat("EEE MMM d, h:mm a", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfferHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = OfferHistoryAdapter(emptyList()) { offer -> showDetail(offer) }
        binding.recyclerOffers.layoutManager = LinearLayoutManager(this)
        binding.recyclerOffers.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        binding.btnTabDay.setOnClickListener   { setTab("day") }
        binding.btnTabWeek.setOnClickListener  { setTab("week") }
        binding.btnTabMonth.setOnClickListener { setTab("month") }

        binding.btnPrevDay.setOnClickListener {
            selectedDay.add(Calendar.DAY_OF_YEAR, -1)
            refreshDayNav()
            loadOffers()
        }
        binding.btnNextDay.setOnClickListener {
            selectedDay.add(Calendar.DAY_OF_YEAR, 1)
            refreshDayNav()
            loadOffers()
        }

        setTab("day")
    }

    private fun setTab(tab: String) {
        activeTab = tab
        val navy = Color.parseColor("#1A1A2E")
        val teal = Color.parseColor("#00C9A7")
        val gray = Color.parseColor("#6B7280")
        listOf(
            binding.btnTabDay   to "day",
            binding.btnTabWeek  to "week",
            binding.btnTabMonth to "month"
        ).forEach { (btn, t) ->
            btn.setBackgroundColor(if (t == tab) navy else Color.TRANSPARENT)
            btn.setTextColor(if (t == tab) teal else gray)
        }
        binding.layoutDayNav.visibility = if (tab == "day") View.VISIBLE else View.GONE
        refreshDayNav()
        loadOffers()
    }

    private fun refreshDayNav() {
        if (activeTab != "day") return

        val label = when {
            sameDay(selectedDay, today)                               -> "Today, ${dayLabelFmt.format(selectedDay.time).substringAfter(", ")}"
            sameDay(selectedDay, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday, ${dayLabelFmt.format(selectedDay.time).substringAfter(", ")}"
            else                                                      -> dayLabelFmt.format(selectedDay.time)
        }
        binding.tvDayLabel.text = label

        // Disable next-day button when already on today
        val isToday = sameDay(selectedDay, today)
        binding.btnNextDay.isEnabled = !isToday
        binding.btnNextDay.alpha = if (isToday) 0.3f else 1f
    }

    private fun loadOffers() {
        lifecycleScope.launch {
            val dao = GigLensApp.instance.database.offerCaptureDao()
            val (start, end) = dateRangeForTab()
            val offers = dao.getByDateRange(start, end)
            runOnUiThread {
                binding.tvTotalCount.text = "${offers.size} offer${if (offers.size == 1) "" else "s"}"
                binding.recyclerOffers.visibility = if (offers.isEmpty()) View.GONE else View.VISIBLE
                binding.tvEmpty.visibility        = if (offers.isEmpty()) View.VISIBLE else View.GONE
                adapter.update(offers)
            }
        }
    }

    private fun dateRangeForTab(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (activeTab) {
            "day" -> {
                val start = Calendar.getInstance().apply {
                    time = selectedDay.time
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                }
                val end = Calendar.getInstance().apply {
                    time = start.time
                    add(Calendar.DAY_OF_YEAR, 1)
                    add(Calendar.MILLISECOND, -1)
                }
                start.timeInMillis to end.timeInMillis
            }
            "week" -> {
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                }
                start.timeInMillis to System.currentTimeMillis()
            }
            else -> { // month
                val start = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                }
                start.timeInMillis to System.currentTimeMillis()
            }
        }
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun showDetail(offer: OfferCapture) {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.layout_offer_detail, null)
        sheet.setContentView(view)

        val (label, color) = verdictStyle(offer.verdict)
        val badge = view.findViewById<TextView>(R.id.detailVerdict)
        badge.text = label
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(color)
        }

        view.findViewById<TextView>(R.id.detailRestaurant).text =
            offer.restaurant?.takeIf { it.isNotBlank() } ?: "Unknown restaurant"
        view.findViewById<TextView>(R.id.detailDateTime).text =
            detailDateFmt.format(Date(offer.timestamp))

        view.findViewById<TextView>(R.id.detailPay).text =
            offer.payAmount?.let { String.format(Locale.US, "$%.2f", it) } ?: "--"
        view.findViewById<TextView>(R.id.detailDistance).text =
            offer.distance?.let { String.format(Locale.US, "%.1f mi", it) } ?: "--"

        val netView = view.findViewById<TextView>(R.id.detailNet)
        val net = offer.netValue
        netView.text = net?.let {
            if (it >= 0) String.format(Locale.US, "+$%.2f", it)
            else         String.format(Locale.US, "-$%.2f", Math.abs(it))
        } ?: "--"
        netView.setTextColor(when {
            net == null -> Color.parseColor("#9CA3AF")
            net >= 0    -> Color.parseColor("#00C9A7")
            else        -> Color.parseColor("#EF4444")
        })

        fun row(rowId: Int, label: String, value: String?) {
            val rowView = view.findViewById<View>(rowId)
            rowView.visibility = if (value == null) View.GONE else View.VISIBLE
            if (value != null) {
                rowView.findViewById<TextView>(R.id.tvDetailLabel).text = label
                rowView.findViewById<TextView>(R.id.tvDetailValue).text = value
            }
        }

        row(R.id.rowPayPerMile,  "Pay / mile",      offer.payPerMile?.let  { String.format(Locale.US, "$%.2f/mi", it) })
        row(R.id.rowTruePerMile, "True $/mile",     offer.truePayPerMile?.let { String.format(Locale.US, "$%.2f/mi", it) })
        row(R.id.rowVehicleCost, "Vehicle cost",    offer.vehicleCost?.let { String.format(Locale.US, "-$%.2f", it) })
        row(R.id.rowTimeCost,    "Time cost",       offer.timeCost?.let    { String.format(Locale.US, "-$%.2f", it) })
        row(R.id.rowMinutes,     "Est. minutes",    offer.minutesOnJob?.let { String.format(Locale.US, "%.0f min", it) })
        row(R.id.rowScore,       "Score",           offer.score?.let { "$it / 100" })
        row(R.id.rowPickupMi,    "Pickup leg",      offer.pickupDistance?.let   { String.format(Locale.US, "%.1f mi", it) })
        row(R.id.rowDeliveryMi,  "Delivery leg",    offer.deliveryDistance?.let { String.format(Locale.US, "%.1f mi", it) })
        row(R.id.rowTown,        "Est. town",       offer.estimatedTown)
        row(R.id.rowTownMethod,  "Town method",     offer.estimatedTownMethod)
        row(R.id.rowPlatform,    "Platform",        offer.platform.takeIf { it.isNotBlank() })

        sheet.show()
    }

    private fun verdictStyle(verdict: String?): Pair<String, Int> = when (verdict) {
        "TAKE"       -> "HIGH" to Color.parseColor("#00C9A7")
        "BORDERLINE" -> "MED"  to Color.parseColor("#F59E0B")
        "SKIP"       -> "LOW"  to Color.parseColor("#EF4444")
        else         -> "?"    to Color.parseColor("#6B7280")
    }
}
