class YourActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 1001
    
    private fun checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 请求权限
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // 已经有权限，开始录音
            startRecording()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 用户同意授权，开始录音
                    startRecording()
                } else {
                    // 用户拒绝授权，显示提示
                    Toast.makeText(
                        this,
                        "需要录音权限才能继续操作",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun startRecording() {
        try {
            // 在这里初始化和启动 AudioRecord
            // 建议使用 try-catch 包裹 AudioRecord 相关代码
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "录音初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 