package com.fyp.facer.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceEmbedder(
    ctx: Context,
    modelName: String = "ml/face_embedder.tflite"
) {
    private var interpreter: Interpreter

    init {
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }

        try {
            val model = FileUtil.loadMappedFile(ctx, modelName)
            interpreter = Interpreter(model, options)
            Log.d("FaceEmbedder", "✅ Model loaded successfully")
        } catch (e: Exception) {
            Log.e("FaceEmbedder", "❌ Failed to load model: ${e.message}")
            throw e
        }
    }

    /**
     * Convert a bitmap to normalized float buffer ([-1,1]) for FaceNet input.
     * FaceNet expects shape [1,160,160,3] float32.
     */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val size = 160
        val bmp = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val buffer =
            ByteBuffer.allocateDirect(1 * size * size * 3 * 4).order(ByteOrder.nativeOrder())

        val pixels = IntArray(size * size)
        bmp.getPixels(pixels, 0, size, 0, 0, size, size)
        for (p in pixels) {
            // Extract RGB, normalize to [-1, 1]
            val r = ((p shr 16 and 0xFF) - 128f) / 128f
            val g = ((p shr 8 and 0xFF) - 128f) / 128f
            val b = ((p and 0xFF) - 128f) / 128f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Generate a 128-D embedding for a face crop (bitmap).
     */
    fun embed(faceBitmap: Bitmap): FloatArray {
        val input = preprocess(faceBitmap)
        val output = Array(1) { FloatArray(128) }
        interpreter.run(input, output)
        return l2Normalize(output[0])
    }

    /** L2 normalize the embedding vector. */
    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0f
        for (v in vec) sum += v * v
        val norm = sqrt(sum)
        return FloatArray(vec.size) { i -> vec[i] / (norm + 1e-10f) }
    }

    /** Cosine similarity between two embeddings (-1 to 1). */
    companion object {
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            require(a.size == b.size) { "Embedding sizes differ" }
            var dot = 0f
            var na = 0f
            var nb = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                na += a[i] * a[i]
                nb += b[i] * b[i]
            }
            return dot / (sqrt(na) * sqrt(nb) + 1e-10f)
        }
    }
}
