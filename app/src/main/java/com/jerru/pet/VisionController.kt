package com.jerru.pet

import android.annotation.SuppressLint
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class VisionController(private val service: OverlayService, private val state: JerruStateMachine) {
    private val executor = Executors.newSingleThreadExecutor()
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )
    private val active = AtomicBoolean(true)
    private val busy = AtomicBoolean(false)
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var lastFrame = 0L

    fun start() {
        val future = ProcessCameraProvider.getInstance(service)
        future.addListener({
            if (!active.get()) return@addListener
            try {
                provider = future.get()
                val useCase = ImageAnalysis.Builder()
                    .setTargetResolution(Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                useCase.setAnalyzer(executor, ::analyze)
                analysis = useCase
                provider?.bindToLifecycle(service, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
            } catch (_: Exception) {
                state.clearFace()
            }
        }, ContextCompat.getMainExecutor(service))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(frame: ImageProxy) {
        val now = android.os.SystemClock.elapsedRealtime()
        val image = frame.image
        if (!active.get() || image == null || now - lastFrame < 700 || !busy.compareAndSet(false, true)) {
            frame.close()
            return
        }
        lastFrame = now
        val input = InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener(ContextCompat.getMainExecutor(service)) { faces ->
                if (active.get()) {
                    when {
                        faces.size >= 2 -> {
                            val face = faces.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }[1]
                            val width = if (frame.imageInfo.rotationDegrees % 180 == 0) frame.width else frame.height
                            val x = 1f - 2f * face.boundingBox.exactCenterX() / width.coerceAtLeast(1)
                            state.face(Expression.ANGRY, x)
                        }
                        faces.firstOrNull()?.smilingProbability?.let { it > 0.7f } == true -> state.face(Expression.HAPPY, 0f)
                        else -> state.clearFace()
                    }
                }
            }
            .addOnCompleteListener {
                frame.close()
                busy.set(false)
            }
    }

    fun stop() {
        active.set(false)
        analysis?.clearAnalyzer()
        analysis?.let { provider?.unbind(it) }
        detector.close()
        executor.shutdown()
    }
}
