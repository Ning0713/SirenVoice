package xyz.mufanc.vap.util

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.OutputStream

@Suppress("ConstPropertyName")
@SuppressLint("MissingPermission")
class Recorder(
    mSampleRate: Int,
    mChannelConfig: Int
) {
    companion object {
        private const val TAG = "RecorderWrapper"

        private const val sAudioSource = MediaRecorder.AudioSource.MIC
        private const val sAudioFormat = AudioFormat.ENCODING_PCM_16BIT
    }

    private val mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelConfig, sAudioFormat)
    private val mRecorder = AudioRecord(sAudioSource, mSampleRate, mChannelConfig, sAudioFormat, mBufferSize)

    private val mBuffer = ByteArray(mBufferSize)

    private fun now() = System.currentTimeMillis()

    fun record(duration: Long, os: OutputStream) {
        record(duration) { buffer ->
            os.write(buffer)
        }

        os.flush()
    }

    fun record(duration: Long, callback: (ByteArray) -> Unit) {
        mRecorder.startRecording()
        Log.i(TAG, "recording...")

        try {
            val sTime = now()
            while (true) {
                val count = mRecorder.read(mBuffer, 0, mBufferSize)

                if (now() - sTime >= duration || count < 0) {
                    break
                }

                callback(mBuffer)
            }
        } catch(err: Throwable) {
            Log.e(TAG, "error occurred while recording", err)
        } finally {
            mRecorder.stop()
            mRecorder.release()
            Log.i(TAG, "stop.")
        }
    }
}