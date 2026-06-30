package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic) - 2026-06-23
// RecyclerView adapter for offer history list.
// 2026-06-30 (Claude): inline post-shift town confirmation strip — Yes/No per row.

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.augusteenterprise.giglens.R
import com.augusteenterprise.giglens.data.OfferCapture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfferHistoryAdapter(
    private var offers: List<OfferCapture>,
    private val onItemClick: (OfferCapture) -> Unit,
    // Driver tapped Yes/No on a town estimate. accurate=true → confirmedTown=estimate;
    // accurate=false → activity prompts for the correct town. No-op if null (callers
    // that don't support confirmation, e.g. read-only contexts).
    private val onTownConfirm: ((OfferCapture, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<OfferHistoryAdapter.ViewHolder>() {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)

    private val teal = Color.parseColor("#00C9A7")
    private val red  = Color.parseColor("#EF4444")

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvVerdict:    TextView = itemView.findViewById(R.id.tvVerdict)
        val tvRestaurant: TextView = itemView.findViewById(R.id.tvRestaurant)
        val tvMeta:       TextView = itemView.findViewById(R.id.tvMeta)
        val tvNet:        TextView = itemView.findViewById(R.id.tvNet)
        val tvTime:       TextView = itemView.findViewById(R.id.tvTime)
        val layoutTownConfirm: LinearLayout = itemView.findViewById(R.id.layoutTownConfirm)
        val tvTownPrompt: TextView = itemView.findViewById(R.id.tvTownPrompt)
        val btnTownYes:   TextView = itemView.findViewById(R.id.btnTownYes)
        val btnTownNo:    TextView = itemView.findViewById(R.id.btnTownNo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_offer_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val offer = offers[position]

        val (label, color) = verdictStyle(offer.verdict)
        holder.tvVerdict.text = label
        holder.tvVerdict.background = roundedBadge(color)

        holder.tvRestaurant.text = offer.restaurant?.takeIf { it.isNotBlank() } ?: "Unknown"

        holder.tvMeta.text = buildString {
            offer.payAmount?.let { append(String.format(Locale.US, "$%.2f", it)) }
            offer.distance?.let {
                if (isNotEmpty()) append(" · ")
                append(String.format(Locale.US, "%.1f mi", it))
            }
        }

        val net = offer.netValue
        if (net != null) {
            holder.tvNet.text = if (net >= 0)
                String.format(Locale.US, "+$%.2f", net)
            else
                String.format(Locale.US, "-$%.2f", Math.abs(net))
            holder.tvNet.setTextColor(
                if (net >= 0) Color.parseColor("#00C9A7") else Color.parseColor("#EF4444")
            )
        } else {
            holder.tvNet.text = "--"
            holder.tvNet.setTextColor(Color.parseColor("#9CA3AF"))
        }

        holder.tvTime.text = timeFmt.format(Date(offer.timestamp))

        bindTownConfirm(holder, offer)

        holder.itemView.setOnClickListener { onItemClick(offer) }
    }

    // Town-confirmation strip: only meaningful when a town was estimated.
    //   estimatedTown == null            → strip hidden (nothing to confirm)
    //   townAccurate  == null            → show prompt + Yes/No
    //   townAccurate  != null            → show confirmed state, buttons hidden
    private fun bindTownConfirm(holder: ViewHolder, offer: OfferCapture) {
        val town = offer.estimatedTown
        if (town.isNullOrBlank()) {
            holder.layoutTownConfirm.visibility = View.GONE
            holder.btnTownYes.setOnClickListener(null)
            holder.btnTownNo.setOnClickListener(null)
            return
        }
        holder.layoutTownConfirm.visibility = View.VISIBLE

        when (offer.townAccurate) {
            null -> {
                holder.tvTownPrompt.text = "📍 ~$town — correct?"
                holder.tvTownPrompt.setTextColor(Color.parseColor("#9CA3AF"))
                holder.btnTownYes.visibility = View.VISIBLE
                holder.btnTownNo.visibility  = View.VISIBLE
                holder.btnTownYes.background = chipBg(teal)
                holder.btnTownNo.background  = chipBg(red)
                holder.btnTownYes.setTextColor(teal)
                holder.btnTownNo.setTextColor(red)
                holder.btnTownYes.setOnClickListener { onTownConfirm?.invoke(offer, true) }
                holder.btnTownNo.setOnClickListener  { onTownConfirm?.invoke(offer, false) }
            }
            true -> {
                holder.tvTownPrompt.text = "✓ Confirmed: $town"
                holder.tvTownPrompt.setTextColor(teal)
                hideTownButtons(holder)
            }
            false -> {
                val actual = offer.confirmedTown?.takeIf { it.isNotBlank() }
                holder.tvTownPrompt.text =
                    if (actual != null) "✗ Wrong (was $town) → $actual"
                    else                "✗ Marked wrong (was $town)"
                holder.tvTownPrompt.setTextColor(red)
                hideTownButtons(holder)
            }
        }
    }

    private fun hideTownButtons(holder: ViewHolder) {
        holder.btnTownYes.visibility = View.GONE
        holder.btnTownNo.visibility  = View.GONE
        holder.btnTownYes.setOnClickListener(null)
        holder.btnTownNo.setOnClickListener(null)
    }

    // Rounded outline chip — transparent fill, colored 1.5dp stroke.
    private fun chipBg(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18f
        setColor(Color.TRANSPARENT)
        setStroke(3, color)
    }

    override fun getItemCount() = offers.size

    fun update(newOffers: List<OfferCapture>) {
        offers = newOffers
        notifyDataSetChanged()
    }

    private fun verdictStyle(verdict: String?): Pair<String, Int> = when (verdict) {
        "TAKE"       -> "HIGH" to Color.parseColor("#00C9A7")
        "BORDERLINE" -> "MED"  to Color.parseColor("#F59E0B")
        "SKIP"       -> "LOW"  to Color.parseColor("#EF4444")
        else         -> "?"    to Color.parseColor("#6B7280")
    }

    private fun roundedBadge(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }
}
