package com.example.gamearchive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.widget.SwitchCompat

class SpecialsFragment : Fragment() {

    private val viewModel: SpecialsViewModel by viewModels()

    private lateinit var rv: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: MarketAdapter
    private lateinit var btnSort: ImageButton
    private lateinit var topBar: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_specials, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rv = view.findViewById(R.id.rvSpecials)
        progress = view.findViewById(R.id.pbSpecials)
        btnSort = view.findViewById(R.id.btnSort)
        topBar = view.findViewById(R.id.topBarContainer)

        rv.layoutManager = LinearLayoutManager(context)
        rv.setItemViewCacheSize(50)
        rv.setHasFixedSize(true)
        rv.itemAnimator = null


        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // TODO: Compose 迁移后底栏显隐由 LazyListState 控制
                if (dy > 10) {
                    animateTopBar(false)
                } else if (dy < -10) {
                    animateTopBar(true)
                }
            }
        })

        btnSort.setOnClickListener { showSortAndFilterDialog() }

        observeViewModel()
        viewModel.loadIfNeeded()
    }

    // 观察数据管家：数据一变就刷新界面
    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            progress.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        viewModel.rawList.observe(viewLifecycleOwner) { applySortAndFilter() }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun animateTopBar(visible: Boolean) {
        val targetY = if (visible) 0f else -topBar.height.toFloat()
        if (topBar.translationY != targetY) {
            topBar.animate().translationY(targetY).setDuration(200).start()
        }
    }

    // 显示排序和筛选弹窗
    private fun showSortAndFilterDialog() {
        val context = requireContext()
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 48, 0, 48)
            setBackgroundResource(R.drawable.bg_dialog_rounded)
        }

        // 标题：筛选
        val titleFilter = TextView(context).apply {
            text = context.getString(R.string.specials_filter_title)
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.miuix_blue))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(64, 0, 64, 16)
        }
        dialogView.addView(titleFilter)

        // 开关：隐藏已拥有
        val switchFilter = SwitchCompat(context).apply {
            text = context.getString(R.string.specials_filter_hide_owned)
            textSize = 16f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.miuix_text_primary_light))
            isChecked = viewModel.isFilteringOwned
            setPadding(64, 0, 64, 0)
            trackTintList = androidx.core.content.ContextCompat.getColorStateList(context, R.color.sel_switch_track)
            thumbTintList = androidx.core.content.ContextCompat.getColorStateList(context, R.color.sel_switch_thumb)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        dialogView.addView(switchFilter)

        // 分割线
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 2).apply {
                setMargins(0, 32, 0, 32)
            }
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.miuix_dialog_divider))
        }
        dialogView.addView(divider)

        // 标题：排序
        val titleSort = TextView(context).apply {
            text = context.getString(R.string.specials_sort_title)
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.miuix_blue))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(64, 0, 64, 16)
        }
        dialogView.addView(titleSort)

        // 排序选项
        val sortOptions = arrayOf(
            context.getString(R.string.specials_sort_sales),
            context.getString(R.string.specials_sort_price_asc),
            context.getString(R.string.specials_sort_price_desc),
            context.getString(R.string.specials_sort_discount),
            context.getString(R.string.specials_sort_rating)
        )
        val radioGroup = RadioGroup(context).apply {
            setPadding(48, 0, 48, 0)
        }

        for (i in sortOptions.indices) {
            val rb = RadioButton(context).apply {
                text = sortOptions[i]
                id = i
                textSize = 15f
                setPadding(24, 16, 24, 16)
                layoutParams = RadioGroup.LayoutParams(-1, -2)
            }
            if (i == viewModel.sortMode) rb.isChecked = true
            radioGroup.addView(rb)
        }
        dialogView.addView(radioGroup)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        // 确定按钮
        val confirmBtn = Button(context).apply {
            text = context.getString(R.string.specials_confirm)
            setBackgroundResource(R.drawable.bg_button_primary)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.white))
            setPadding(72, 20, 72, 20)
            setOnClickListener {
                viewModel.isFilteringOwned = switchFilter.isChecked
                viewModel.sortMode = radioGroup.checkedRadioButtonId
                if (viewModel.sortMode == -1) viewModel.sortMode = 0

                applySortAndFilter()
                dialog.dismiss()
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.END
                setMargins(0, 16, 48, 0)
            }
        }
        dialogView.addView(confirmBtn)

        dialog.show()
    }

    private fun applySortAndFilter() {
        val rawList = viewModel.rawList.value ?: emptyList()
        var list = if (viewModel.isFilteringOwned) {
            rawList.filter { !MainActivity.ownedGameIds.contains(it.id) }
        } else {
            rawList
        }

        list = when (viewModel.sortMode) {
            1 -> list.sortedBy { it.priceVal }
            2 -> list.sortedByDescending { it.priceVal }
            3 -> list.sortedByDescending { it.discount }
            4 -> list.sortedByDescending { it.reviewScore }
            else -> list
        }

        if (::adapter.isInitialized) {
            adapter.updateData(list)
            rv.scrollToPosition(0)
        } else {
            adapter = MarketAdapter(list)
            rv.adapter = adapter
        }
    }
}
