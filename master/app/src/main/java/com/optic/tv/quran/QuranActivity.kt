package com.optic.tv.quran

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.optic.tv.BaseThemedAppCompatActivity
import com.optic.tv.databinding.ActivityQuranBinding
import com.optic.tv.utils.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuranActivity : BaseThemedAppCompatActivity() {

    private lateinit var binding: ActivityQuranBinding
    private var adapter: QuranAdapter? = null
    private var lastSurahs: List<Surah>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuranBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeHelper.applyPremiumHeroBackground(binding.root)

        binding.tvQuranSubtitle.text = getString(com.optic.tv.R.string.quran_subtitle_loading)
        binding.rvQuranSurahs.layoutManager = LinearLayoutManager(this)

        binding.ivBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { fetchSurahs() }
        binding.btnRetry.setOnClickListener { fetchSurahs() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter?.filter(s?.toString().orEmpty())
                refreshEmptySearchVisibility()
            }
        })

        fetchSurahs()
    }

    private fun fetchSurahs() {
        binding.pbLoading.visibility = View.VISIBLE
        binding.llError.visibility = View.GONE
        binding.rvQuranSurahs.visibility = View.VISIBLE
        binding.llEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    QuranApiClient.api.getAllSurahs()
                }
                binding.pbLoading.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!.data
                    lastSurahs = list
                    adapter = QuranAdapter(list)
                    binding.rvQuranSurahs.adapter = adapter
                    binding.tvQuranSubtitle.text = getString(
                        com.optic.tv.R.string.quran_subtitle_format,
                        list.size
                    )
                    val q = binding.etSearch.text?.toString().orEmpty()
                    adapter?.filter(q)
                    refreshEmptySearchVisibility()
                } else {
                    showError(getString(com.optic.tv.R.string.quran_load_error))
                }
            } catch (e: Exception) {
                binding.pbLoading.visibility = View.GONE
                showError(
                    getString(
                        com.optic.tv.R.string.quran_error_with_message,
                        e.message ?: ""
                    )
                )
            }
        }
    }

    private fun showError(message: String) {
        binding.llError.visibility = View.VISIBLE
        binding.rvQuranSurahs.visibility = View.GONE
        binding.llEmpty.visibility = View.GONE
        binding.tvError.text = message
    }

    private fun refreshEmptySearchVisibility() {
        val a = adapter
        val show = a != null && a.itemCount == 0 && lastSurahs?.isNotEmpty() == true
        binding.llEmpty.visibility = if (show) View.VISIBLE else View.GONE
    }
}
