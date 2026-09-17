package com.ainovel.audiobook.tts.storage

import com.ainovel.audiobook.tts.engine.PcmUtils
import com.ainovel.audiobook.tts.engine.VieNeuConfig
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lossless WAV (RIFF/WAVE) binary encoder and decoder.
 *
 * Produces canonical 44-byte RIFF headers compliant with 48,000 Hz, 16-bit signed Mono PCM
 * as required by the VieNeu-TTS neural audio synthesis engine.
 */
object WavWriter {

    const val HEADER_SIZE = 44
    const val DEFAULT_SAMPLE_RATE = VieNeuConfig.SAMPLE_RATE // 48000
    const val DEFAULT_NUM_CHANNELS = 1                      // Mono
    const val DEFAULT_BITS_PER_SAMPLE = 16                  // 16-bit PCM
    const val AUDIO_FORMAT_PCM: Short = 1                   // Linear PCM

    // RIFF 4-char magic constants
    private val RIFF_MAGIC = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
    private val WAVE_MAGIC = byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte())
    private val FMT_MAGIC  = byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte())
    private val DATA_MAGIC = byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())

    /**
     * Generates a 44-byte canonical RIFF/WAVE header for PCM audio.
     */
    fun createHeader(
        dataSize: Int,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        numChannels: Int = DEFAULT_NUM_CHANNELS,
        bitsPerSample: Int = DEFAULT_BITS_PER_SAMPLE
    ): ByteArray {
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = (numChannels * bitsPerSample / 8).toShort()
        val totalChunkSize = 36 + dataSize

        val header = ByteArray(HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(RIFF_MAGIC)
        buffer.putInt(totalChunkSize)
        buffer.put(WAVE_MAGIC)
        buffer.put(FMT_MAGIC)
        buffer.putInt(16)
        buffer.putShort(AUDIO_FORMAT_PCM)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign)
        buffer.putShort(bitsPerSample.toShort())
        buffer.put(DATA_MAGIC)
        buffer.putInt(dataSize)

        return header
    }

    fun pcm16ToWav(
        pcm16: ShortArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        numChannels: Int = DEFAULT_NUM_CHANNELS
    ): ByteArray {
        val dataSize = pcm16.size * 2
        val totalSize = HEADER_SIZE + dataSize
        val wavBytes = ByteArray(totalSize)

        val header = createHeader(dataSize, sampleRate, numChannels, DEFAULT_BITS_PER_SAMPLE)
        System.arraycopy(header, 0, wavBytes, 0, HEADER_SIZE)

        var offset = HEADER_SIZE
        for (i in pcm16.indices) {
            val sample = pcm16[i].toInt()
            wavBytes[offset++] = (sample and 0xFF).toByte()
            wavBytes[offset++] = ((sample ushr 8) and 0xFF).toByte()
        }

        return wavBytes
    }

    fun pcmFloatToWav(
        pcmFloat: FloatArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        numChannels: Int = DEFAULT_NUM_CHANNELS
    ): ByteArray {
        val pcm16 = PcmUtils.floatToShort(pcmFloat)
        return pcm16ToWav(pcm16, sampleRate, numChannels)
    }

    fun writeWav(
        outputStream: OutputStream,
        pcm16: ShortArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        numChannels: Int = DEFAULT_NUM_CHANNELS
    ) {
        val dataSize = pcm16.size * 2
        val header = createHeader(dataSize, sampleRate, numChannels, DEFAULT_BITS_PER_SAMPLE)
        outputStream.write(header)

        val buffer = ByteArray(8192)
        var bufIdx = 0

        for (i in pcm16.indices) {
            val sample = pcm16[i].toInt()
            buffer[bufIdx++] = (sample and 0xFF).toByte()
            buffer[bufIdx++] = ((sample ushr 8) and 0xFF).toByte()

            if (bufIdx == buffer.size) {
                outputStream.write(buffer, 0, bufIdx)
                bufIdx = 0
            }
        }

        if (bufIdx > 0) {
            outputStream.write(buffer, 0, bufIdx)
        }
        outputStream.flush()
    }

    fun writeWav(
        file: File,
        pcm16: ShortArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        numChannels: Int = DEFAULT_NUM_CHANNELS
    ) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            writeWav(fos, pcm16, sampleRate, numChannels)
        }
    }

    fun writeWav(
        file: File,
        pcmFloat: FloatArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        numChannels: Int = DEFAULT_NUM_CHANNELS
    ) {
        val pcm16 = PcmUtils.floatToShort(pcmFloat)
        writeWav(file, pcm16, sampleRate, numChannels)
    }

    /**
     * Initializes a streaming WAV file with placeholder header.
     */
    fun initStreamingWav(
        file: File,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        numChannels: Int = DEFAULT_NUM_CHANNELS
    ) {
        file.parentFile?.mkdirs()
        FileOutputStream(file, false).use { fos ->
            val placeholderHeader = createHeader(0, sampleRate, numChannels, DEFAULT_BITS_PER_SAMPLE)
            fos.write(placeholderHeader)
            fos.flush()
        }
    }

    /**
     * Appends PCM chunk to an initialized streaming WAV file.
     */
    fun appendPcmChunk(file: File, pcm16: ShortArray) {
        if (pcm16.isEmpty()) return
        FileOutputStream(file, true).use { fos ->
            val buffer = ByteArray(8192)
            var bufIdx = 0
            for (i in pcm16.indices) {
                val sample = pcm16[i].toInt()
                buffer[bufIdx++] = (sample and 0xFF).toByte()
                buffer[bufIdx++] = ((sample ushr 8) and 0xFF).toByte()
                if (bufIdx == buffer.size) {
                    fos.write(buffer, 0, bufIdx)
                    bufIdx = 0
                }
            }
            if (bufIdx > 0) {
                fos.write(buffer, 0, bufIdx)
            }
            fos.flush()
        }
    }

    /**
     * Finalizes streaming WAV by updating the RIFF and data size headers based on actual file size.
     */
    fun finalizeStreamingWav(file: File) {
        if (!file.exists()) return
        val totalLength = file.length()
        val dataSize = (totalLength - HEADER_SIZE).coerceAtLeast(0).toInt()
        val totalChunkSize = 36 + dataSize

        RandomAccessFile(file, "rw").use { raf ->
            // Update RIFF total chunk size at offset 4
            raf.seek(4)
            val chunkBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalChunkSize).array()
            raf.write(chunkBuf)

            // Update data chunk size at offset 40
            raf.seek(40)
            val dataBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize).array()
            raf.write(dataBuf)
        }
    }

    data class ParsedWav(
        val sampleRate: Int,
        val numChannels: Int,
        val bitsPerSample: Int,
        val dataSize: Int,
        val pcm16: ShortArray
    ) {
        val durationSeconds: Float
            get() = if (sampleRate > 0 && numChannels > 0) {
                pcm16.size.toFloat() / (sampleRate * numChannels)
            } else 0f

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ParsedWav
            if (sampleRate != other.sampleRate) return false
            if (numChannels != other.numChannels) return false
            if (bitsPerSample != other.bitsPerSample) return false
            if (dataSize != other.dataSize) return false
            if (!pcm16.contentEquals(other.pcm16)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = sampleRate
            result = 31 * result + numChannels
            result = 31 * result + bitsPerSample
            result = 31 * result + dataSize
            result = 31 * result + pcm16.contentHashCode()
            return result
        }
    }

    fun readWav(inputStream: InputStream): ParsedWav {
        val headerBuffer = ByteArray(12)
        val readHeader = readFully(inputStream, headerBuffer, 0, 12)
        require(readHeader == 12) { "WAV stream too short ($readHeader < 12 bytes)" }

        val riffStr = String(headerBuffer, 0, 4, Charsets.US_ASCII)
        require(riffStr == "RIFF") { "Invalid RIFF magic: $riffStr" }

        val waveStr = String(headerBuffer, 8, 4, Charsets.US_ASCII)
        require(waveStr == "WAVE") { "Invalid WAVE format: $waveStr" }

        var sampleRate = DEFAULT_SAMPLE_RATE
        var numChannels = DEFAULT_NUM_CHANNELS
        var bitsPerSample = DEFAULT_BITS_PER_SAMPLE
        var foundData = false
        var actualDataBytes = 0
        var pcm16 = ShortArray(0)

        val chunkHeader = ByteArray(8)
        while (readFully(inputStream, chunkHeader, 0, 8) == 8) {
            val chunkIdStr = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = (chunkHeader[4].toInt() and 0xFF) or
                    ((chunkHeader[5].toInt() and 0xFF) shl 8) or
                    ((chunkHeader[6].toInt() and 0xFF) shl 16) or
                    ((chunkHeader[7].toInt() and 0xFF) shl 24)

            if (chunkIdStr == "fmt ") {
                val fmtBuf = ByteArray(chunkSize)
                val readFmt = readFully(inputStream, fmtBuf, 0, chunkSize)
                require(readFmt >= 16) { "Incomplete 'fmt ' chunk in WAV" }
                val fmtByteBuffer = ByteBuffer.wrap(fmtBuf).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = fmtByteBuffer.short
                require(audioFormat.toInt() == 1) { "Unsupported audio format: $audioFormat (only Linear PCM 1 supported)" }
                numChannels = fmtByteBuffer.short.toInt()
                sampleRate = fmtByteBuffer.int
                val byteRate = fmtByteBuffer.int
                val blockAlign = fmtByteBuffer.short
                bitsPerSample = fmtByteBuffer.short.toInt()
            } else if (chunkIdStr == "data") {
                foundData = true
                val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(2)
                val numSamples = chunkSize / bytesPerSample
                pcm16 = ShortArray(numSamples)

                val byteBuf = ByteArray(8192)
                var sampleIdx = 0
                var bytesReadTotal = 0
                var bytePending = -1

                while (bytesReadTotal < chunkSize) {
                    val toRead = minOf(byteBuf.size, chunkSize - bytesReadTotal)
                    val readCount = inputStream.read(byteBuf, 0, toRead)
                    if (readCount <= 0) break

                    var offset = 0
                    if (bytePending != -1) {
                        val s = (bytePending and 0xFF) or ((byteBuf[0].toInt() and 0xFF) shl 8)
                        if (sampleIdx < pcm16.size) {
                            pcm16[sampleIdx++] = s.toShort()
                        }
                        offset = 1
                        bytePending = -1
                    }

                    while (offset + 1 < readCount && sampleIdx < pcm16.size) {
                        val b0 = byteBuf[offset].toInt() and 0xFF
                        val b1 = byteBuf[offset + 1].toInt() and 0xFF
                        pcm16[sampleIdx++] = ((b0) or (b1 shl 8)).toShort()
                        offset += 2
                    }

                    if (offset < readCount) {
                        bytePending = byteBuf[offset].toInt() and 0xFF
                    }

                    bytesReadTotal += readCount
                }

                actualDataBytes = sampleIdx * 2
                if (sampleIdx < pcm16.size) {
                    pcm16 = pcm16.copyOf(sampleIdx)
                }
                break
            } else {
                skipFully(inputStream, chunkSize.toLong())
            }
        }

        require(foundData) { "No 'data' subchunk found in WAV" }

        return ParsedWav(
            sampleRate = sampleRate,
            numChannels = numChannels,
            bitsPerSample = bitsPerSample,
            dataSize = actualDataBytes,
            pcm16 = pcm16
        )
    }

    fun readWav(bytes: ByteArray): ParsedWav {
        return java.io.ByteArrayInputStream(bytes).use { readWav(it) }
    }

    fun readWav(file: File): ParsedWav {
        return java.io.FileInputStream(file).use { readWav(it) }
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var totalRead = 0
        while (totalRead < length) {
            val read = inputStream.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }

    private fun skipFully(inputStream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val skipBuf = ByteArray(minOf(4096L, remaining).coerceAtLeast(1L).toInt())
        while (remaining > 0) {
            val read = inputStream.read(skipBuf, 0, minOf(skipBuf.size.toLong(), remaining).toInt())
            if (read <= 0) break
            remaining -= read
        }
    }
}
