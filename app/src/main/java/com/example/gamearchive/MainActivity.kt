package com.example.gamearchive

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    companion object {
        // 存储已拥有的游戏ID，用于在特惠页面进行过滤
        val ownedGameIds = mutableSetOf<Int>()

        // 全局API服务实例
        lateinit var apiServiceGlobal: SteamApiService
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 初始化Retrofit网络请求客户端
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.steam-tracker-proxy.cyou/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiServiceGlobal = retrofit.create(SteamApiService::class.java)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        // 设置ViewPager适配器，禁止用户滑动切换，预加载2个页面
        viewPager.adapter = MainPagerAdapter(this)
        viewPager.isUserInputEnabled = true
        viewPager.offscreenPageLimit = 2

        // 底部导航栏点击事件监听
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_library -> viewPager.setCurrentItem(0, true)
                R.id.nav_specials -> viewPager.setCurrentItem(1, true)
            }
            true
        }

        // 页面滑动时同步底部导航栏选中状态
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNav.menu.getItem(position).isChecked = true
            }
        })
    }

    // 控制底部导航栏显示或隐藏的方法
    fun setBottomNavVisibility(visible: Boolean) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (visible) {
            if (bottomNav.translationY != 0f) {
                bottomNav.animate().translationY(0f).setDuration(200).start()
            }
        } else {
            if (bottomNav.translationY == 0f) {
                bottomNav.animate().translationY(bottomNav.height.toFloat()).setDuration(200).start()
            }
        }
    }

    // ViewPager适配器，用于管理Fragment
    inner class MainPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> LibraryFragment()
                1 -> SpecialsFragment()
                else -> LibraryFragment()
            }
        }
    }
}