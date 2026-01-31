package com.contest.pocketpharmacist


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 获取控件
        val logo = findViewById<ImageView>(R.id.ivLogo)
        val title = findViewById<TextView>(R.id.tvTitle)
        val subtitle = findViewById<TextView>(R.id.tvSubtitle)

        // 动画组合：淡入 + 上滑
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 1000 }
        val slideUp = TranslateAnimation(0f, 0f, 100f, 0f).apply { duration = 1000 }

        val animSet = AnimationSet(true).apply {
            addAnimation(fadeIn)
            addAnimation(slideUp)
            fillAfter = true
        }

        // 启动动画
        logo.startAnimation(animSet)
        title.startAnimation(animSet)
        subtitle.startAnimation(AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 1500; startOffset = 500 })
            fillAfter = true
        })

        // 2秒后跳转主界面（去掉了disableBack的设置，标准做法）
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish() // 销毁开屏，防止返回键回来
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 2000)
    }
}