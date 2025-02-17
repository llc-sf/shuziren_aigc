package ai.guiji.duix.test.ui.activity

import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.render.DUIXRenderer
import ai.guiji.duix.test.databinding.ActivityCallBinding
import ai.guiji.duix.test.util.LogUtils
import android.media.AudioFormat
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import com.konovalov.vad.VadFrameSizeType
import com.konovalov.vad.VadMode
import com.konovalov.vad.VadSampleRate
import com.lodz.android.minerva.agent.MinervaAgent
import com.lodz.android.minerva.bean.AudioFormats
import com.lodz.android.minerva.bean.states.Idle
import com.lodz.android.minerva.bean.states.VadDetect
import com.lodz.android.minerva.bean.states.VadFileSave
import com.lodz.android.minerva.contract.MinervaVad
import com.lodz.android.minervademo.enums.AudioStatus
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fasterxml.jackson.databind.ObjectMapper


class CallActivity : BaseActivity() {

    companion object {
        const val GL_CONTEXT_VERSION = 2
        private const val DEFAULT_TTS_URL = "http://14.19.169.45:8280/v1/tts"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val TAG_NET = "DUIX_NET"
    }

    private var baseDir = ""
    private var modelDir = ""
    private var ttsUrl = DEFAULT_TTS_URL
    private var apiKey = ""  // 新增 apiKey 变量


    private lateinit var binding: ActivityCallBinding
    private var duix: DUIX? = null
    private var mDUIXRender: DUIXRenderer? = null
    private var mMinerva: MinervaVad? = null
    private val mFilePath by lazy { getExternalFilesDir("vad")?.absolutePath ?: "" }
    private var mStatus: AudioStatus = AudioStatus.IDLE
    private var isProcessingRequest = false
    private val objectMapper = ObjectMapper()
    private var isPlaying = false
    
    // 创建一个配置了超时时间的 OkHttpClient
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn()
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        baseDir = intent.getStringExtra("baseDir") ?: ""
        modelDir = intent.getStringExtra("modelDir") ?: ""
        ttsUrl = intent.getStringExtra("ttsUrl") ?: DEFAULT_TTS_URL
        apiKey = intent.getStringExtra("apiKey") ?: ""  // 获取 apiKey

        Log.e("123", "baseDir: $baseDir")
        Log.e("123", "modelDir: $modelDir")

        binding.btnPlay.setOnClickListener {
            playWav()
        }

        Glide.with(mContext).load("file:///android_asset/bg/bg1.png").into(binding.ivBg)

        binding.glTextureView.setEGLContextClientVersion(GL_CONTEXT_VERSION)
        binding.glTextureView.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // 透明
//        binding.glTextureView.preserveEGLContextOnPause = true      // 后台运行不要释放上下文
        binding.glTextureView.isOpaque = false           // 透明

        mDUIXRender =
            DUIXRenderer(
                mContext,
                binding.glTextureView
            )
        binding.glTextureView.setRenderer(mDUIXRender)
        binding.glTextureView.renderMode =
            GLSurfaceView.RENDERMODE_WHEN_DIRTY      // 一定要在设置完Render之后再调用

        duix = DUIX(mContext, baseDir, modelDir, mDUIXRender) { event, msg, info ->
            when (event) {
                Constant.CALLBACK_EVENT_INIT_READY -> {
                    initOk()
                }

                Constant.CALLBACK_EVENT_INIT_ERROR -> {
                    runOnUiThread {
                        Toast.makeText(mContext, "初始化异常: $msg", Toast.LENGTH_SHORT).show()
                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_START -> {
                    Log.i(TAG_NET, "数字人开始播放")
                    mMinerva?.pause()
                    runOnUiThread {
                        binding.btnRecord.isEnabled = false
                        binding.btnRecord.text = "播放中..."
                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_END -> {
                    Log.i(TAG_NET, "数字人播放结束")
                    isProcessingRequest = false  // 播放结束，重置状态
                    mMinerva?.start()
                    runOnUiThread {
                        binding.btnRecord.isEnabled = true
                        updateRecordButton("正在录音")
                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR -> {
                    Log.e(TAG_NET, "数字人播放错误: $msg")
                    isProcessingRequest = false  // 播放错误，重置状态
                    mMinerva?.start()
                    runOnUiThread {
                        Toast.makeText(mContext, "播放失败: $msg", Toast.LENGTH_SHORT).show()
                        binding.btnRecord.isEnabled = true
                        updateRecordButton("正在录音")
                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_PROGRESS -> {
//                    Log.e(TAG, "audio play progress: $info")

                }
            }
        }
        // 异步回调结果
        duix?.init()

        binding.btnRecord.isEnabled = false  // 按钮不可点击，只用于显示状态

        if (checkRecordPermission()) {
            initMinerva()
        } else {
            requestRecordPermission()
        }
    }

    private fun initOk() {
        Log.i(TAG_NET, "数字人初始化完成")
        // 初始化完成后自动开始录音
        if (checkRecordPermission()) {
            initMinerva()
            // 自动开始录音
            mMinerva?.start()
            runOnUiThread {
                binding.btnRecord.text = "正在录音"
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        mMinerva?.stop()
        duix?.release()
        mDUIXRender?.release()
    }

    /**
     * 播放16k采样率单通道16位深的wav本地文件
     */
    private fun playWav() {
        val wavName = "voice4.wav"
        val wavDir = File(mContext.getExternalFilesDir("duix"), "wav")
        if (!wavDir.exists()) {
            wavDir.mkdirs()
        }
        val wavFile = File(wavDir, wavName)
        if (!wavFile.exists()) {
            // 拷贝到sdcard
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                val input = mContext.assets.open("wav/${wavName}")
                val out: OutputStream = FileOutputStream("${wavFile.absolutePath}.tmp")
                val buffer = ByteArray(1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                input.close()
                out.close()
                File("${wavFile.absolutePath}.tmp").renameTo(wavFile)
                duix?.playAudio(wavFile.absolutePath)
            }
        } else {
            duix?.playAudio(wavFile.absolutePath)
        }
    }

    private fun initMinerva() {
        if (!checkRecordPermission()) {
            Toast.makeText(this, "缺少录音权限", Toast.LENGTH_SHORT).show()
            return
        }

        mMinerva = MinervaAgent.vad()
            .setChannel(AudioFormat.CHANNEL_IN_MONO)
            .setSampleRate(VadSampleRate.SAMPLE_RATE_16K.value)
            .setAudioFormat(AudioFormats.WAV)
            .setSaveDirPath(mFilePath)
            .setSaveActivityVoice(true)
            .setFrameSizeType(VadFrameSizeType.SMALL)
            .setVadMode(VadMode.VERY_AGGRESSIVE)
            .setVadInterceptor { vad, buffer, end, db ->
                vad.isSpeech(buffer) && db > 40
            }
            .setOnRecordingStatesListener { state ->
                when (state) {
                    is Idle -> {
                        if (!isProcessingRequest) {
                            mStatus = AudioStatus.IDLE
                            // 空闲状态自动开始录音
                            mMinerva?.start()
                            updateRecordButton("正在录音")
                        }
                    }
                    is VadDetect -> {
                        if (!isProcessingRequest) {
                            mStatus = AudioStatus.VAD_DETECT
                            updateRecordButton("正在录音")
                        }
                    }
                    is VadFileSave -> {
                        if (!isProcessingRequest) {
                            sendAudioToServer(state.file)
                        }
                    }
                    is Error -> {
                        if (!isProcessingRequest) {
                            Toast.makeText(this, "录音错误", Toast.LENGTH_SHORT).show()
                            mStatus = AudioStatus.IDLE
                            // 错误后自动重试
                            mMinerva?.start()
                            updateRecordButton("正在录音")
                        }
                    }
                    else -> {}
                }
            }
            .build(this)
    }

    private fun updateRecordButton(text: String) {
        runOnUiThread {
            binding.btnRecord.text = text
        }
    }

    private fun sendAudioToServer(audioFile: File) {
        isProcessingRequest = true  // 开始处理请求
        runOnUiThread {
            binding.btnRecord.isEnabled = false
            binding.btnRecord.text = "处理中..."
        }

        // 打印请求信息
        Log.i(TAG_NET, "开始发送请求")
        LogUtils.getInstance(this).log("请求URL: $ttsUrl")
        Log.i(TAG_NET, "请求URL: $ttsUrl")
        Log.i(TAG_NET, "音频文件: ${audioFile.absolutePath}")
        Log.i(TAG_NET, "文件大小: ${audioFile.length()} bytes")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio_file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .build()

        val requestBuilder = Request.Builder()
            .url(ttsUrl)
            .post(requestBody)

        // 如果有 apiKey，添加到请求头
        if (apiKey.isNotEmpty()) {
            requestBuilder.addHeader("X-API-Key", apiKey)
        }

        val request = requestBuilder.build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LogUtils.getInstance(this@CallActivity).log("请求失败: ${e.message}")
                Log.e(TAG_NET, "请求失败: ${e.message}")
                Log.e(TAG_NET, "失败URL: ${call.request().url}")
                Log.e(TAG_NET, "异常堆栈:", e)
                runOnUiThread {
                    Toast.makeText(this@CallActivity, "网络请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    isProcessingRequest = false  // 请求失败，重置状态
                    binding.btnRecord.isEnabled = true
                    updateRecordButton("正在录音")
                    mMinerva?.start()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    Log.i(TAG_NET, "收到响应 状态码: ${response.code}")
                    Log.i(TAG_NET, "响应头: ${response.headers}")
                    
                    val responseStr = response.body?.string()
                    Log.i(TAG_NET, "响应数据: $responseStr")
                    LogUtils.getInstance(this@CallActivity).log("响应数据: $responseStr")
                    if (responseStr != null) {
                        val jsonNode = objectMapper.readTree(responseStr)
                        val url = jsonNode.get("url")?.asText()
                        Log.i(TAG_NET, "解析到URL: $url")
                        
                        if (!url.isNullOrEmpty()) {
                            // 下载音频文件并播放
                            downloadAudioAndPlay(url)
                        } else {
                            val errorMsg = "服务器返回数据格式错误"
                            Log.e(TAG_NET, "$errorMsg: $responseStr")
                            runOnUiThread {
                                Toast.makeText(this@CallActivity, errorMsg, Toast.LENGTH_SHORT).show()
                                isProcessingRequest = false
                                binding.btnRecord.isEnabled = true
                                updateRecordButton("正在录音")
                                mMinerva?.start()
                            }
                        }
                    } else {
                        Log.e(TAG_NET, "响应体为空")
                        runOnUiThread {
                            isProcessingRequest = false  // 响应为空，重置状态
                            binding.btnRecord.isEnabled = true
                            updateRecordButton("正在录音")
                            mMinerva?.start()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG_NET, "解析响应数据失败: ${e.message}")
                    Log.e(TAG_NET, "异常堆栈:", e)
                    runOnUiThread {
                        isProcessingRequest = false  // 解析失败，重置状态
                        binding.btnRecord.isEnabled = true
                        updateRecordButton("正在录音")
                        mMinerva?.start()
                    }
                }
            }
        })
    }

    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initMinerva()
            } else {
                Toast.makeText(this, "需要录音权限才能使用该功能", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadAudioAndPlay(audioUrl: String) {
        Log.i(TAG_NET, "开始下载音频: $audioUrl")
        val request = Request.Builder()
            .url(audioUrl)
            .get()
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG_NET, "音频下载失败: ${e.message}")
                Log.e(TAG_NET, "失败URL: ${call.request().url}")
                Log.e(TAG_NET, "异常堆栈:", e)
                runOnUiThread {
                    Toast.makeText(this@CallActivity, "音频下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    isProcessingRequest = false
                    binding.btnRecord.isEnabled = true
                    updateRecordButton("正在录音")
                    mMinerva?.start()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.let { responseBody ->
                        // 创建保存音频的目录
                        val audioDir = File(mContext.getExternalFilesDir("duix"), "audio")
                        if (!audioDir.exists()) {
                            audioDir.mkdirs()
                        }

                        // 生成本地文件名
                        val fileName = "response_${System.currentTimeMillis()}.wav"
                        val localFile = File(audioDir, fileName)

                        // 保存音频文件
                        localFile.outputStream().use { fileOut ->
                            responseBody.byteStream().use { bodyIn ->
                                bodyIn.copyTo(fileOut)
                            }
                        }

                        Log.i(TAG_NET, "音频下载完成，保存至: ${localFile.absolutePath}")

                        // 播放本地音频文件
                        runOnUiThread {
                            duix?.playAudio(localFile.absolutePath)
                        }
                    } ?: run {
                        Log.e(TAG_NET, "下载响应体为空")
                        runOnUiThread {
                            Toast.makeText(this@CallActivity, "下载响应体为空", Toast.LENGTH_SHORT).show()
                            isProcessingRequest = false
                            binding.btnRecord.isEnabled = true
                            updateRecordButton("正在录音")
                            mMinerva?.start()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG_NET, "保存音频文件失败: ${e.message}")
                    Log.e(TAG_NET, "异常堆栈:", e)
                    runOnUiThread {
                        Toast.makeText(this@CallActivity, "保存音频文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        isProcessingRequest = false
                        binding.btnRecord.isEnabled = true
                        updateRecordButton("正在录音")
                        mMinerva?.start()
                    }
                }
            }
        })
    }

}
