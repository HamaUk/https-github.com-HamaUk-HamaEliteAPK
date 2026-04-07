package com.bachors.iptv.sports

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bachors.iptv.BaseThemedAppCompatActivity
import com.bachors.iptv.R
import com.bachors.iptv.databinding.ActivitySportsBinding
import com.bachors.iptv.utils.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Locale

class SportsActivity : BaseThemedAppCompatActivity() {

    private lateinit var binding: ActivitySportsBinding
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val fetchRunnable = object : Runnable {
        override fun run() {
            fetchSportsData(showBlockingLoader = false)
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeHelper.applyPremiumHeroBackground(binding.root)

        binding.rvSportsLeagues.layoutManager = LinearLayoutManager(this)

        binding.ivBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { fetchSportsData(showBlockingLoader = true) }
        binding.btnRetry.setOnClickListener { fetchSportsData(showBlockingLoader = true) }

        binding.tvSportsSubtitle.text = getString(R.string.sports_subtitle_loading)
        fetchSportsData(showBlockingLoader = true)
        handler.postDelayed(fetchRunnable, REFRESH_INTERVAL_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateSubtitle(updatedAt: Long) {
        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        val timeStr = timeFmt.format(updatedAt)
        binding.tvSportsSubtitle.text = getString(
            R.string.sports_header_subtitle,
            getString(R.string.sports_updated_at, timeStr),
            getString(R.string.sports_auto_refresh_hint)
        )
    }

    private fun fetchSportsData(showBlockingLoader: Boolean) {
        if (showBlockingLoader) {
            binding.pbLoading.visibility = View.VISIBLE
            binding.llError.visibility = View.GONE
        }
        lifecycleScope.launch {
            try {
                val leagues = withContext(Dispatchers.IO) {
                    YsScoresRepository.fetchLiveLeagues()
                }
                binding.pbLoading.visibility = View.GONE
                binding.llError.visibility = View.GONE
                updateSubtitle(System.currentTimeMillis())

                if (leagues.isEmpty()) {
                    binding.rvSportsLeagues.visibility = View.GONE
                    binding.llEmpty.visibility = View.VISIBLE
                    binding.rvSportsLeagues.adapter = null
                } else {
                    binding.llEmpty.visibility = View.GONE
                    binding.rvSportsLeagues.visibility = View.VISIBLE
                    val flat = leagues.toFlatSportsItems()
                    binding.rvSportsLeagues.adapter = SportsAdapter(flat)
                }
            } catch (e: Exception) {
                binding.pbLoading.visibility = View.GONE
                if (showBlockingLoader) {
                    binding.rvSportsLeagues.visibility = View.GONE
                    binding.llEmpty.visibility = View.GONE
                    binding.llError.visibility = View.VISIBLE
                    binding.tvError.text = getString(R.string.sports_load_error, e.message ?: "")
                }
            }
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
    }
}
