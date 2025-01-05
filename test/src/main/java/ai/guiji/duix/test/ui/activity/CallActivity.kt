package ai.guiji.duix.test.ui.activity

import ai.guiji.duix.sdk.client.Constant
import ai.guiji.duix.sdk.client.DUIX
import ai.guiji.duix.sdk.client.render.DUIXRenderer
import ai.guiji.duix.test.databinding.ActivityCallBinding
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


class CallActivity : BaseActivity() {

    companion object {
        const val GL_CONTEXT_VERSION = 2
        private const val TTS_URL = "http://192.168.2.252:8280/v1/tts"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private var baseDir = ""
    private var modelDir = ""


    private lateinit var binding: ActivityCallBinding
    private var duix: DUIX? = null
    private var mDUIXRender: DUIXRenderer? = null
    private var mMinerva: MinervaVad? = null
    private val mFilePath by lazy { getExternalFilesDir("vad")?.absolutePath ?: "" }
    private var mStatus: AudioStatus = AudioStatus.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepScreenOn()
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        baseDir = intent.getStringExtra("baseDir") ?: ""
        modelDir = intent.getStringExtra("modelDir") ?: ""

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
                    runOnUiThread {

                    }
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_END -> {
                    Log.e(TAG, "CALLBACK_EVENT_PLAY_END: $msg")
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_ERROR -> {
                    Log.e(TAG, "CALLBACK_EVENT_PLAY_ERROR: $msg")
                }

                Constant.CALLBACK_EVENT_AUDIO_PLAY_PROGRESS -> {
//                    Log.e(TAG, "audio play progress: $info")

                }
            }
        }
        // 异步回调结果
        duix?.init()

        binding.btnRecord.setOnClickListener {
            when (mStatus) {
                AudioStatus.IDLE -> mMinerva?.start()
                AudioStatus.VAD_DETECT -> mMinerva?.stop()
                else -> {}
            }
        }

        if (checkRecordPermission()) {
            initMinerva()
        } else {
            requestRecordPermission()
        }
    }

    private fun initOk() {
        Log.e(TAG, "init ok")
        runOnUiThread {
            binding.btnPlay.visibility = View.VISIBLE
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
                        mStatus = AudioStatus.IDLE
                        updateRecordButton("开始录音")
                    }
                    is VadDetect -> {
                        mStatus = AudioStatus.VAD_DETECT
                        updateRecordButton("停止录音")
                    }
                    is VadFileSave -> {
                        sendAudioToServer(state.file)
                    }
                    is Error -> {
                        Toast.makeText(this, "录音错误", Toast.LENGTH_SHORT).show()
                        mStatus = AudioStatus.IDLE
                        updateRecordButton("开始录音")
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
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", 
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(TTS_URL)
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@CallActivity, "网络请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.i("net_error", e.message.toString())
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val audioUrl = response.body?.string()
                if (!audioUrl.isNullOrEmpty()) {
                    runOnUiThread {
                        duix?.playAudio(audioUrl)
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

}
