package com.contest.pocketpharmacist

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// 讯飞SDK
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility
// 数据库
import com.contest.pocketpharmacist.db.AppDb
import com.contest.pocketpharmacist.db.Record

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_CHAT = 100
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var viewFinder: PreviewView
    private lateinit var tvResult: TextView
    private lateinit var progressBar: View
    private lateinit var btnCapture: FrameLayout
    private lateinit var btnConsult: CardView
    private lateinit var btnWeeklyReport: CardView // ✅ 新增：健康周报按钮
    private lateinit var tvHint: TextView
    private lateinit var icArrow: ImageView
    private lateinit var cardResult: CardView
    private lateinit var centerOverlay: View

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var audioManager: AudioManager

    private var lastPhotoBytes: ByteArray? = null
    private var lastDrugName: String = "未知药品"
    private var isIdentified = false

    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化讯飞SDK
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=a49de89c")

        setContentView(R.layout.activity_main)

        initViews()
        initTTS()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onResume() {
        super.onResume()
        if (isIdentified) {
            showResetDialog()
        }
    }

    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle("重新拍照")
            .setMessage("是否要重新拍摄其他药品？")
            .setPositiveButton("重新拍照") { _, _ ->
                resetToInitialState()
            }
            .setNegativeButton("退出应用") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun resetToInitialState() {
        isIdentified = false
        lastPhotoBytes = null
        lastDrugName = "未知药品"

        cardResult.visibility = View.GONE
        centerOverlay.visibility = View.GONE
        btnConsult.visibility = View.GONE
        icArrow.visibility = View.GONE

        tvHint.visibility = View.VISIBLE
        tvHint.text = "点击按钮拍摄药品"

        btnCapture.setOnClickListener {
            takePhotoAndIdentify()
        }

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
        }

        Toast.makeText(this, "请对准新的药品拍照", Toast.LENGTH_SHORT).show()
    }

    private fun initViews() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            0
        )

        viewFinder = findViewById(R.id.viewFinder)
        cardResult = findViewById(R.id.cardResult)
        tvResult = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)
        btnCapture = findViewById(R.id.btnCapture)
        btnConsult = findViewById(R.id.btnConsult)
        btnWeeklyReport = findViewById(R.id.btnWeeklyReport) // ✅ 绑定周报按钮
        tvHint = findViewById(R.id.tvHint)
        icArrow = findViewById(R.id.icArrow)
        centerOverlay = findViewById(R.id.centerOverlay)

        // 初始状态
        cardResult.visibility = View.GONE
        centerOverlay.visibility = View.GONE
        btnConsult.visibility = View.GONE
        icArrow.visibility = View.GONE
        tvHint.text = "点击按钮拍摄药品"

        // 拍照按钮
        btnCapture.setOnClickListener {
            if (!isIdentified) {
                takePhotoAndIdentify()
            } else {
                openChatConsult()
            }
        }

        // 详细咨询按钮
        btnConsult.setOnClickListener {
            openChatConsult()
        }

        // ✅ 新增：健康周报按钮点击跳转
        btnWeeklyReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }

    private fun openChatConsult() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("DRUG_NAME", lastDrugName)
        }
        startActivityForResult(intent, REQUEST_CODE_CHAT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CHAT) {
            Log.d(TAG, "从咨询页面返回")
        }
    }

    // ✅ 真实的药品识别逻辑（保留原有）
    private fun takePhotoAndIdentify() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        tvHint.text = "正在拍照..."

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    progressBar.visibility = View.GONE
                    tvHint.text = "拍照失败，请重试"
                    Log.e(TAG, "拍照失败: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    lastPhotoBytes = compressImage(savedUri)

                    runOnUiThread {
                        identifyDrugAutomatically() // 调用真实识别
                    }
                }
            }
        )
    }

    // ✅ 大模型API识别逻辑（保留原有）
    private fun identifyDrugAutomatically() {
        val photoBytes = lastPhotoBytes
        if (photoBytes == null) {
            progressBar.visibility = View.GONE
            tvHint.text = "图片处理失败"
            return
        }

        tvHint.text = "正在识别药品..."

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val base64Image = Base64.encodeToString(photoBytes, Base64.NO_WRAP)
                val imageUrl = "data:image/jpeg;base64,$base64Image"

                val autoQuestion = "请识别这是什么药品，并简单说明功效、用法用量和注意事项（控制在60字内，适合语音播报）"

                val request = ChatRequest(
                    messages = listOf(
                        Message(
                            role = "system",
                            content = "你是专业药师，请直接回答。"
                        ),
                        Message(
                            role = "user",
                            content = listOf(
                                ContentItem(type = "text", text = autoQuestion),
                                ContentItem(type = "image_url", image_url = ImageUrl(url = imageUrl))
                            )
                        )
                    )
                )

                val response = RetrofitClient.api.chat("Bearer ${RetrofitClient.API_KEY}", request)
                val answer = response.choices?.firstOrNull()?.message?.content ?: "识别失败，请检查网络"

                lastDrugName = extractDrugName(answer)

                // ✅ 保存到数据库（用于周报统计）
                try {
                    val today = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(System.currentTimeMillis())

                    AppDb.get(this@MainActivity).dao().insert(
                        Record(medName = lastDrugName, date = today)
                    )
                    Log.d(TAG, "✅ 已保存数据库: $lastDrugName")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 数据库保存失败: ${e.message}")
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    isIdentified = true

                    centerOverlay.visibility = View.VISIBLE
                    tvResult.text = answer
                    cardResult.visibility = View.VISIBLE

                    // 语音播报
                    speakText(answer)

                    btnConsult.visibility = View.VISIBLE
                    tvHint.visibility = View.GONE
                    icArrow.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                Log.e(TAG, "识别失败", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvHint.text = "识别失败，请重试"
                    Toast.makeText(this@MainActivity, "网络错误：${e.message?.take(20)}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractDrugName(text: String): String {
        val firstLine = text.lines().firstOrNull() ?: ""
        return if (firstLine.length > 2) {
            firstLine.take(15).replace("这是", "").replace("：", "").trim()
        } else {
            "未知药品"
        }
    }

    private fun speakText(text: String) {
        if (!::textToSpeech.isInitialized) {
            Log.w(TAG, "TTS未初始化")
            return
        }

        val formatted = text.replace("\n", "，").take(150)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(formatted, TextToSpeech.QUEUE_FLUSH, null, "drug_intro")
        } else {
            @Suppress("DEPRECATION")
            textToSpeech.speak(formatted, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                Log.d(TAG, "相机启动成功")
            } catch (exc: Exception) {
                Log.e(TAG, "相机启动失败", exc)
                tvHint.text = "相机启动失败"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun compressImage(uri: Uri?): ByteArray {
        if (uri == null) return ByteArray(0)
        return try {
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return ByteArray(0)
            val stream = ByteArrayOutputStream()
            var quality = 90
            do {
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                quality -= 10
            } while (stream.size() > 500 * 1024 && quality > 20)
            Log.d(TAG, "图片压缩后: ${stream.size()} bytes")
            stream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "压缩图片失败", e)
            ByteArray(0)
        }
    }

    private fun initTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "中文语音包缺失")
                } else {
                    textToSpeech.setSpeechRate(1.0f)
                    Log.d(TAG, "TTS初始化成功")
                }
            } else {
                Log.e(TAG, "TTS初始化失败: $status")
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("需要相机和录音权限才能使用")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}