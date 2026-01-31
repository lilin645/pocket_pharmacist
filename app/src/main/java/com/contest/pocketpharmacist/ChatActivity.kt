package com.contest.pocketpharmacist

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.Locale
import org.json.JSONObject

// 👇 引入讯飞SDK
import com.iflytek.cloud.RecognizerListener
import com.iflytek.cloud.RecognizerResult
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechError
import com.iflytek.cloud.SpeechRecognizer

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var btnVoice: FrameLayout
    private lateinit var btnText: FrameLayout
    private lateinit var btnBack: FrameLayout
    private lateinit var btnSend: FrameLayout
    private lateinit var tvDrugName: TextView
    private lateinit var etInput: EditText

    private lateinit var textToSpeech: TextToSpeech

    private val messages = mutableListOf<ChatMessage>()
    private var drugName: String = ""

    // 👇 新增：讯飞听写对象
    private var mIat: SpeechRecognizer? = null
    // 👇 新增：用来拼接语音结果的容器
    private val sbResult = StringBuilder()

    data class ChatMessage(val content: String, val isUser: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        drugName = intent.getStringExtra("DRUG_NAME") ?: "未知药品"
        val initialQuestion = intent.getStringExtra("INITIAL_QUESTION")

        // 👇 初始化讯飞听写对象
        mIat = SpeechRecognizer.createRecognizer(this, null)

        initViews()
        initTTS()

        val welcome = "您好，关于$drugName，您还有什么想了解的吗？"
        addMessage(welcome, false)
        speak(welcome)

        if (!initialQuestion.isNullOrEmpty()) {
            recyclerView.postDelayed({
                handleUserQuestion(initialQuestion)
            }, 800)
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerChat)
        btnVoice = findViewById(R.id.btnVoice)
        btnText = findViewById(R.id.btnText)
        btnBack = findViewById(R.id.btnBack)
        btnSend = findViewById(R.id.btnSend)
        tvDrugName = findViewById(R.id.tvDrugName)
        etInput = findViewById(R.id.etInput)

        tvDrugName.text = "咨询：$drugName"

        chatAdapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recyclerView.adapter = chatAdapter

        btnBack.setOnClickListener { finish() }

        // 👇👇👇 核心修改：将原来的点击改为“按住说话”逻辑 👇👇👇
        btnVoice.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下：开始录音
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start() // 视觉反馈：缩小
                    startVoiceInput()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 松开：停止录音
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start() // 视觉反馈：恢复
                    mIat?.stopListening()
                    true
                }
                else -> false
            }
        }
        // 👆👆👆 修改结束 👆👆👆

        btnText.setOnClickListener { showTextInputDialog() }

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                etInput.setText("")
                handleUserQuestion(text)
            }
        }
    }

    // 👇👇👇 新增：配置并开始录音 👇👇👇
    private fun startVoiceInput() {
        mIat?.let { iat ->
            sbResult.clear() // 清空上一次的结果
            iat.setParameter(SpeechConstant.PARAMS, null)
            iat.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD)
            iat.setParameter(SpeechConstant.RESULT_TYPE, "json")
            iat.setParameter(SpeechConstant.LANGUAGE, "zh_cn")
            // iat.setParameter(SpeechConstant.ACCENT, "canton") // 如果需要粤语，取消注释这行

            iat.startListening(object : RecognizerListener {
                override fun onBeginOfSpeech() {
                    Toast.makeText(this@ChatActivity, "正在听...", Toast.LENGTH_SHORT).show()
                }
                override fun onEndOfSpeech() {
                    // 录音结束，正在分析
                }
                override fun onVolumeChanged(v: Int, b: ByteArray?) {}

                override fun onResult(results: RecognizerResult?, isLast: Boolean) {
                    val json = results?.resultString ?: return
                    val text = parseIatResult(json)
                    sbResult.append(text)

                    if (isLast) {
                        // 最终结果，发送给AI
                        val finalQuestion = sbResult.toString().trim()
                        if (finalQuestion.isNotEmpty()) {
                            Log.d("Chat", "语音识别结果: $finalQuestion")
                            handleUserQuestion(finalQuestion)
                        } else {
                            Toast.makeText(this@ChatActivity, "没听清，请再说一次", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(error: SpeechError?) {
                    Log.e("Chat", "语音错误: ${error?.errorCode}")
                    if (error?.errorCode != 10118) { // 10118是未检测到语音，忽略
                        Toast.makeText(this@ChatActivity, "语音识别出错", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onEvent(e: Int, a1: Int, a2: Int, b: Bundle?) {}
            })
        }
    }

    // 👇👇👇 新增：解析讯飞JSON结果 👇👇👇
    private fun parseIatResult(json: String): String {
        val ret = StringBuilder()
        try {
            val joResult = JSONObject(json)
            val words = joResult.getJSONArray("ws")
            for (i in 0 until words.length()) {
                val items = words.getJSONObject(i).getJSONArray("cw")
                val obj = items.getJSONObject(0)
                ret.append(obj.getString("w"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ret.toString()
    }

    private fun showTextInputDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.etQuestion)
        AlertDialog.Builder(this)
            .setTitle("输入问题")
            .setView(dialogView)
            .setPositiveButton("发送") { _, _ ->
                val question = editText.text.toString().trim()
                if (question.isNotEmpty()) handleUserQuestion(question)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleUserQuestion(question: String) {
        addMessage(question, true)
        GlobalScope.launch(Dispatchers.Main) {
            delay(300)
            withContext(Dispatchers.IO) {
                try {
                    val request = ChatRequest(messages = listOf(
                        Message(role = "system", content = "您是专业药师。用户正在咨询药品：$drugName。请简洁回答(60字以内)。"),
                        Message(role = "user", content = question)
                    ))
                    val response = RetrofitClient.api.chat("Bearer ${RetrofitClient.API_KEY}", request)
                    val answer = response.choices?.firstOrNull()?.message?.content ?: "没听清，请再说一遍"
                    withContext(Dispatchers.Main) {
                        addMessage(answer, false)
                        speak(answer)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { addMessage("网络差，请重试", false) }
                }
            }
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(ChatMessage(text, isUser))
        chatAdapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    private fun initTTS() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) textToSpeech.language = Locale.CHINESE
        }
    }

    private fun speak(text: String) {
        if (!::textToSpeech.isInitialized) return
        val formatted = text.replace("\n", "，").take(150)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech.speak(formatted, TextToSpeech.QUEUE_FLUSH, null, "chat")
        } else {
            textToSpeech.speak(formatted, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    inner class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
            val layoutBubble: FrameLayout = itemView.findViewById(R.id.layoutBubble)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = messages[position]
            holder.tvMessage.text = msg.content
            val params = holder.layoutBubble.layoutParams as FrameLayout.LayoutParams
            if (msg.isUser) {
                holder.layoutBubble.setBackgroundResource(R.drawable.bubble_user)
                params.gravity = android.view.Gravity.END
                holder.tvMessage.setTextColor(0xFFFFFFFF.toInt())
            } else {
                holder.layoutBubble.setBackgroundResource(R.drawable.bubble_ai)
                params.gravity = android.view.Gravity.START
                holder.tvMessage.setTextColor(0xFF333333.toInt())
            }
            holder.layoutBubble.layoutParams = params
        }
        override fun getItemCount() = messages.size
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) textToSpeech.shutdown()
        mIat?.destroy() // 销毁听写对象，释放资源
    }
}