package com.genaro.radiomp3.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.genaro.radiomp3.R
import com.genaro.radiomp3.api.RadioApiService
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class TechnicalDetailsBottomSheet : BottomSheetDialogFragment() {

    private var stationId: String = ""
    private var stationName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            stationId = it.getString(ARG_STATION_ID, "")
            stationName = it.getString(ARG_STATION_NAME, "Unknown")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottomsheet_technical_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set station name
        view.findViewById<TextView>(R.id.txtStationName).text = stationName

        // Close button
        view.findViewById<Button>(R.id.btnClose).setOnClickListener {
            dismiss()
        }

        // Fetch station data from API
        fetchStationData(view)
    }

    private fun fetchStationData(view: View) {
        lifecycleScope.launch {
            try {
                val station = RadioApiService.getStationByUuid(requireContext(), stationId)

                if (station != null) {
                    // Audio Format Section
                    view.findViewById<TextView>(R.id.txtCodec).text =
                        station.codec?.let { "$it (audio/${it.lowercase()})" } ?: "Unknown"

                    view.findViewById<TextView>(R.id.txtBitrate).text =
                        station.bitrate?.let { "$it kbps" } ?: "Unknown"

                    view.findViewById<TextView>(R.id.txtSampleRate).text = "Unknown" // API doesn't provide this

                    view.findViewById<TextView>(R.id.txtChannels).text = "2 (Stereo)" // API doesn't provide this

                    // Stream Info Section
                    view.findViewById<TextView>(R.id.txtServer).text = "Icecast/Shoutcast" // Would need HTTP headers

                    view.findViewById<TextView>(R.id.txtProtocol).text =
                        if (station.url.startsWith("https")) "HTTPS" else "HTTP"

                    // Performance Section (static values for now)
                    view.findViewById<TextView>(R.id.txtBuffer).text = "Not playing"
                    view.findViewById<TextView>(R.id.txtState).text = "Idle"
                    view.findViewById<TextView>(R.id.txtTTFA).text = "N/A"

                } else {
                    // Show error
                    view.findViewById<TextView>(R.id.txtCodec).text = "Failed to load data"
                }

            } catch (e: Exception) {
                android.util.Log.e("TechDetails", "Error: ${e.message}")
                view.findViewById<TextView>(R.id.txtCodec).text = "Error: ${e.message}"
            }
        }
    }

    companion object {
        private const val ARG_STATION_ID = "station_id"
        private const val ARG_STATION_NAME = "station_name"

        fun newInstance(stationId: String, stationName: String): TechnicalDetailsBottomSheet {
            return TechnicalDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_STATION_ID, stationId)
                    putString(ARG_STATION_NAME, stationName)
                }
            }
        }
    }
}
