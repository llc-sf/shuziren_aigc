package ai.guiji.duix.test.ui.activity

import ai.guiji.duix.sdk.client.BuildConfig
import ai.guiji.duix.test.R
import ai.guiji.duix.test.databinding.ActivityMainBinding
import ai.guiji.duix.test.service.StorageService
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lodz.android.minervademo.ui.MainVoiceActivity
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader


class MainActivity : BaseActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREF_NAME = "duix_settings"
        private const val KEY_TTS_URL = "tts_url"
    }

    private lateinit var binding: ActivityMainBinding

    private val baseConfigUrl =
        "https://cdn.guiji.ai/duix/location/gj_dh_res.zip"
    private lateinit var baseDir: File
    private val baseConfigUUID = "d39caddd-488b-4682-b6d1-13549b135dd1"     // 可以用来控制模型文件版本
    private var baseConfigReady = false

    // https://cdn.guiji.ai/duix/digital/model/1706009711636/liangwei_540s.zip
    // https://cdn.guiji.ai/duix/digital/model/1706009766199/mingzhi_540s.zip
    private val modelUrl =
        "https://digital-public.obs.cn-east-3.myhuaweicloud.com/duix/digital/model/1719193748558/airuike_20240409.zip"   // ** 在这里更新模型地址 **
//        "https://digital-public.obs.cn-east-3.myhuaweicloud.com/duix/digital/model/1719194036608/zixuan_20240411v2.zip"   // ** 在这里更新模型地址 **
//        "https://digital-public.obs.cn-east-3.myhuaweicloud.com/duix/digital/model/1719193425133/sufei_20240409.zip"   // ** 在这里更新模型地址 **
    private lateinit var modelDir: File
    private val liangweiUUID = "d39caddd-488b-4682-b6d1-13549b135dd1"       // 可以用来控制模型文件版本
    private var modelReady = false

    // 添加需要检查的权限列表
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSdkVersion.text = "SDK Version: ${BuildConfig.VERSION_NAME}"

        val duixDir = mContext.getExternalFilesDir("duix")
        if (!duixDir!!.exists()) {
            duixDir.mkdirs()
        }
        baseDir = File(
            duixDir,
            baseConfigUrl.substring(baseConfigUrl.lastIndexOf("/") + 1).replace(".zip", "")
        )
        modelDir = File(
            duixDir,
            modelUrl.substring(modelUrl.lastIndexOf("/") + 1).replace(".zip", "")
        )        // 这里要求存放模型的文件夹的名字和下载的zip文件的一致以对应解压的文件夹路径

        // 从 SharedPreferences 加载保存的 URL
        val sharedPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val savedUrl = sharedPrefs.getString(KEY_TTS_URL, "http://14.19.140.88:8280/v1/tts")
        binding.etTtsUrl.setText(savedUrl)

        binding.btnBaseConfigDownload.setOnClickListener {
            downloadBaseConfig()
        }
        binding.btnModelPlay.setOnClickListener {
            if (!modelReady) {
                downloadModel()
            } else if (!baseConfigReady) {
                Toast.makeText(mContext, "您必须正确安装基础配置文件", Toast.LENGTH_SHORT).show()
            } else if (checkPermissions()) {
                // 保存当前输入的 URL
                val ttsUrl = binding.etTtsUrl.text.toString().trim()
                if (ttsUrl.isEmpty()) {
                    Toast.makeText(mContext, "请输入TTS服务地址", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                // 保存到 SharedPreferences
                sharedPrefs.edit().putString(KEY_TTS_URL, ttsUrl).apply()
                
                startCallActivity(ttsUrl)
            } else {
                requestPermissions()
            }
        }

        checkFile()

        binding.voice.setOnClickListener {
            startActivity(Intent(this, MainVoiceActivity::class.java))
        }
    }

    private fun checkPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                permission in arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                // Android 10 及以上不需要存储权限
                true
            } else {
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = requiredPermissions.filter { permission ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && 
                permission in arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                // Android 10 及以上不需要存储权限
                false
            } else {
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            }
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 所有权限都已获得，再次检查模型和配置
                if (!modelReady) {
                    downloadModel()
                } else if (!baseConfigReady) {
                    Toast.makeText(mContext, "您必须正确安装基础配置文件", Toast.LENGTH_SHORT).show()
                } else {
                    startCallActivity()
                }
            } else {
                // 有权限被拒绝
                Toast.makeText(
                    this,
                    "需要录音和存储权限才能使用该功能",
                    Toast.LENGTH_SHORT
                ).show()
                showPermissionExplanationDialog()
            }
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("此功能需要录音和存储权限才能正常使用。请在设置中开启相关权限。")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用设置页面
                openAppSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun startCallActivity(ttsUrl: String? = null) {
        val intent = Intent(this, CallActivity::class.java).apply {
            putExtra("baseDir", baseDir.absolutePath)
            putExtra("modelDir", modelDir.absolutePath)
            putExtra("ttsUrl", ttsUrl)  // 添加 ttsUrl 参数
        }
        startActivity(intent)
    }

    private fun downloadBaseConfig() {
        binding.btnBaseConfigDownload.isEnabled = false
        binding.progressBaseConfig.progress = 0
        StorageService.downloadAndUnzip(
            mContext,
            baseConfigUrl,
            baseDir.absolutePath,
            baseConfigUUID,
            object : StorageService.Callback {
                override fun onDownloadProgress(progress: Int) {
                    runOnUiThread {
                        binding.progressBaseConfig.progress = progress / 2
                    }
                }

                override fun onUnzipProgress(progress: Int) {
                    runOnUiThread {
                        binding.progressBaseConfig.progress = 50 + progress / 2
                    }
                }

                override fun onComplete(path: String?) {
                    runOnUiThread {
                        binding.btnBaseConfigDownload.isEnabled = false
                        binding.btnBaseConfigDownload.text = getString(R.string.ready)
                        binding.progressBaseConfig.progress = 100
                        baseConfigReady = true
                    }
                }

                override fun onError(msg: String?) {
                    runOnUiThread {
                        binding.btnBaseConfigDownload.isEnabled = true
                        binding.progressBaseConfig.progress = 0
                        Toast.makeText(mContext, "文件下载异常: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
            }, true
        )
    }

    private fun downloadModel() {
        binding.btnModelPlay.isEnabled = false
        binding.btnModelPlay.text = getString(R.string.download)
        binding.progressModel.progress = 0
        StorageService.downloadAndUnzip(
            mContext,
            modelUrl,
            modelDir.absolutePath,
            liangweiUUID,
            object : StorageService.Callback {
                override fun onDownloadProgress(progress: Int) {
                    runOnUiThread {
                        binding.progressModel.progress = progress / 2
                    }
                }

                override fun onUnzipProgress(progress: Int) {
                    runOnUiThread {
                        binding.progressModel.progress = 50 + progress / 2;
                    }
                }

                override fun onComplete(path: String?) {
                    runOnUiThread {
                        binding.btnModelPlay.isEnabled = true
                        binding.btnModelPlay.text = "进入数字人页面"
                        binding.progressModel.progress = 100
                        modelReady = true
                    }
                }

                override fun onError(msg: String?) {
                    runOnUiThread {
                        binding.btnModelPlay.isEnabled = true
                        binding.btnModelPlay.text = getString(R.string.download)
                        binding.progressModel.progress = 0
                        Toast.makeText(mContext, "文件下载异常: $msg", Toast.LENGTH_SHORT).show()
                    }
                }
            }, false        // for debug
        )
    }

    private fun checkFile() {
        if (baseDir.exists() && File(baseDir, "/uuid").exists() && baseConfigUUID == BufferedReader(
                InputStreamReader(
                    FileInputStream(
                        File(baseDir, "/uuid")
                    )
                )
            ).readLine()
        ) {
            binding.btnBaseConfigDownload.isEnabled = false
            binding.btnBaseConfigDownload.text = getString(R.string.ready)
            binding.progressBaseConfig.progress = 100
            baseConfigReady = true
        } else {
            binding.btnBaseConfigDownload.isEnabled = true
            binding.progressBaseConfig.progress = 0
        }
        if (modelDir.exists() && File(modelDir, "/uuid").exists() && liangweiUUID == BufferedReader(
                InputStreamReader(
                    FileInputStream(
                        File(modelDir, "/uuid")
                    )
                )
            ).readLine()
        ) {
            binding.btnModelPlay.isEnabled = true
            binding.btnModelPlay.text = "进入数字人页面"
            binding.progressModel.progress = 100
            modelReady = true
        } else {
            binding.btnModelPlay.isEnabled = true
            binding.btnModelPlay.text = getString(R.string.download)
            binding.progressModel.progress = 0
        }
    }


}
