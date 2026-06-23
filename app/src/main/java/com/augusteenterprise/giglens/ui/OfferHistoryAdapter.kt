package com.augusteenterprise.giglens.ui

// Author: Claude (Anthropic) - 2026-06-23
// RecyclerView adapter for offer history list.

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.augusteenterprise.giglens.R
import com.augusteenterprise.giglens.data.OfferCapture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OfferHistoryAdapter(
    private var offers: List<OfferCapture>,
    private val onItemClick: (OfferCapture) -> Unit
) : RecyclerView.Adapter<OfferHistoryAdapter.ViewHolder>() {

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvVerdict:    TextView = itemView.findViewById(R.id.tvVerdict)
        val tvRestaurant: TextView = itemView.findViewById(R.id.tvRestaurant)
        val tvMeta:       TextView = itemView.findViewById(R.id.tvMeta)
        val tvNet:        TextView = itemView.findViewById(R.id.tvNet)
        val tvTime:       TextView = itemView.findViewById(R.id.tvTime)
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

        holder.itemView.setOnClickListener { onItemClick(offer) }
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
