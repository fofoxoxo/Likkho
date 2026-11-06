package com.likkho.nativeapp.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.likkho.nativeapp.LikkhoApp
import com.likkho.nativeapp.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.concurrent.thread

class MicForegroundService : Service() {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var sampleRate = 44100
    private var channelConfig = AudioFormat.CHANNEL_IN_MONO
    private var audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var targetLogId = ""

    private var startTimeMs = 0L
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sampleRate = intent.getIntExtra(EXTRA_SAMPLE_RATE, 44100)
                targetLogId = intent.getStringExtra(EXTRA_LOG_ID) ?: ""
                startRecordingService()
            }
            ACTION_STOP -> {
                stopRecordingService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecordingService() {
        if (isRecording) return

        val audioDir = File(filesDir, "likkho_audio").apply { if (!exists()) mkdirs() }
        val filename = "mic_${UUID.randomUUID()}.wav"
        outputFile = File(audioDir, filename)

        val notification = buildRecordingNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = Math.max(minBufferSize, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true
            Companion.isRecordingActive = true
            startTimeMs = System.currentTimeMillis()

            recordingThread = thread(start = true) {
                writeAudioDataToWav(outputFile!!, bufferSize)
            }
        } catch (e: Exception) {
            stopRecordingService()
        }
    }

    private fun writeAudioDataToWav(file: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var totalAudioLen = 0L

        FileOutputStream(file).use { fos ->
            // Placeholder 44-byte WAV header (will be patched at the end)
            fos.write(ByteArray(44))

            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    fos.write(buffer, 0, read)
                    totalAudioLen += read
                }
            }
        }

        // Write real WAV header
        try {
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * 1 * 16 / 8).toLong()

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.writeBytes("RIFF")
                raf.writeInt(Integer.reverseBytes(totalDataLen.toInt()))
                raf.writeBytes("WAVE")
                raf.writeBytes("fmt ")
                raf.writeInt(Integer.reverseBytes(16)) // sub-chunk size
                raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // PCM = 1
                raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // 1 channel
                raf.writeInt(Integer.reverseBytes(sampleRate))
                raf.writeInt(Integer.reverseBytes(byteRate.toInt()))
                raf.writeShort(java.lang.Short.reverseBytes((1 * 16 / 8).toShort()).toInt()) // block align
                raf.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt()) // bits per sample
                raf.writeBytes("data")
                raf.writeInt(Integer.reverseBytes(totalAudioLen.toInt()))
            }

            val durationSec = (totalAudioLen / (sampleRate * 2f))
            broadcastRecordingFinished(file.absolutePath, durationSec, targetLogId)
        } catch (ignored: Exception) {}
    }

    private fun broadcastRecordingFinished(filePath: String, durationSec: Float, logId: String) {
        val intent = Intent(ACTION_RECORDING_FINISHED).apply {
            putExtra(EXTRA_FILE_PATH, filePath)
            putExtra(EXTRA_DURATION_SEC, durationSec)
            putExtra(EXTRA_LOG_ID, logId)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun stopRecordingService() {
        isRecording = false
        Companion.isRecordingActive = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (ignored: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildRecordingNotification(seconds: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MicForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, LikkhoApp.MIC_CHANNEL_ID)
            .setContentTitle("Likkho Voice Recording Active")
            .setContentText("Recording audio note...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop & Save", stopPendingIntent)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.likkho.nativeapp.action.START_MIC"
        const val ACTION_STOP = "com.likkho.nativeapp.action.STOP_MIC"
        const val ACTION_RECORDING_FINISHED = "com.likkho.nativeapp.action.RECORDING_FINISHED"

        const val EXTRA_SAMPLE_RATE = "sample_rate"
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_DURATION_SEC = "duration_sec"

        var isRecordingActive = false

        fun start(context: Context, sampleRate: Int = 44100, logId: String = "") {
            val intent = Intent(context, MicForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SAMPLE_RATE, sampleRate)
                putExtra(EXTRA_LOG_ID, logId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MicForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
