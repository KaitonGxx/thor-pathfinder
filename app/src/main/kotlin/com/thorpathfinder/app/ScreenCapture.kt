package com.thorpathfinder.app

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Takes the screenshots Android's own leaves out: the bottom screen, or both.
 * `AccessibilityService.takeScreenshot` reaches any display, with no Shizuku
 * and no permission beyond the service's `canTakeScreenshot`. The rules are
 * in [Screenshots].
 *
 * Android takes one display a request and refuses a request within 333 ms of
 * the service's last, so the bottom screen is asked for only once the top
 * one's picture is in, and once that gap has passed. A picture comes as a
 * HardwareBuffer in the display's current orientation, as it looks on the
 * screen (the capture leaves Android's identity transform off), so nothing is
 * rotated. It is copied to an ordinary bitmap on a thread of its own, where
 * two are put together and saved as PNG to Pictures/Screenshots through
 * MediaStore, which lets an app add its own images without any storage
 * permission. Nothing is said until the end, so a message can't land in a
 * picture.
 *
 * [take] and the requests run on the main thread, the results on [pool]: not
 * the service's worker, where a swap or a Screen record could hold up the
 * bottom screen's picture for seconds after the top one's.
 */
class ScreenCapture(
    private val service: AccessibilityService,
    private val handler: Handler,
) {
    /** When the last request went (uptime), for Android's gap; null before the first. */
    private var lastRequest: Long? = null

    /** A screenshot under way: another press is let go, and the first one's message follows. */
    private var busy = false

    /** Set when the service stops: nothing more is asked for or saved. */
    @Volatile
    private var stopped = false

    private val pool = Executors.newSingleThreadExecutor()

    /**
     * Results go to [pool]. Once that has shut down, one still arriving runs
     * where it lands, so its picture's buffer is closed rather than left to
     * the garbage collector.
     */
    private val results = Executor { task ->
        try {
            pool.execute(task)
        } catch (e: RejectedExecutionException) {
            task.run()
        }
    }

    /** The service has stopped: lets the thread go, and drops what is under way. */
    fun shutdown() {
        stopped = true
        pool.shutdown()
    }

    /** One run of a shortcut. [pictures] belongs to [pool]'s thread. */
    private class Job(
        val target: ScreenshotTarget,
        val displays: List<Int>,
        val names: List<String>,
        val done: (Screenshots.Outcome) -> Unit,
    ) {
        val pictures = mutableListOf<Bitmap>()
    }

    /** Takes [target]'s screens and saves them; [done] hears how it went, on the main thread. */
    fun take(target: ScreenshotTarget, done: (Screenshots.Outcome) -> Unit) {
        if (busy) return
        val displays = Screenshots.screens(target).map { screen ->
            if (screen == Screenshots.Screen.TOP) return@map Display.DEFAULT_DISPLAY
            // As with a swap. And with both screens asked for, half is not taken either.
            val other = ScreenSwap.otherDisplay(service) ?: return done(Screenshots.Outcome.NoSecondScreen)
            if (other.state == Display.STATE_OFF) return done(Screenshots.Outcome.SecondScreenOff)
            other.displayId
        }
        if (displays.isEmpty()) return
        busy = true
        request(Job(target, displays, Screenshots.fileNames(target, LocalDateTime.now()), done), index = 0, tries = 0)
    }

    /** Asks for display [index]'s picture, once Android's gap since the last request has passed. */
    private fun request(job: Job, index: Int, tries: Int) {
        if (stopped) return
        val wait = Screenshots.waitBefore(lastRequest, SystemClock.uptimeMillis())
        if (wait > 0) {
            handler.postDelayed({ request(job, index, tries) }, wait)
            return
        }
        lastRequest = SystemClock.uptimeMillis()
        val display = job.displays[index]
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                if (stopped) {
                    runCatching { screenshot.hardwareBuffer.close() }
                    return
                }
                val picture = runCatching { software(screenshot) }
                    .onFailure { Log.w(TAG, "couldn't read display $display's picture", it) }
                    .getOrNull()
                    ?: return fail(job, Screenshots.Problem.INTERNAL)
                job.pictures += picture
                if (index + 1 < job.displays.size) handler.post { request(job, index + 1, tries = 0) } else finish(job)
            }

            override fun onFailure(errorCode: Int) {
                val problem = Screenshots.problem(errorCode)
                if (problem == Screenshots.Problem.TOO_SOON && tries < Screenshots.RETRIES) {
                    // request() waits out the gap again before it asks.
                    handler.post { request(job, index, tries + 1) }
                } else {
                    Log.w(TAG, "display $display: error $errorCode")
                    fail(job, problem)
                }
            }
        }
        try {
            service.takeScreenshot(display, results, callback)
        } catch (e: SecurityException) {
            // The service's connection lacks canTakeScreenshot: Android fixes capabilities when it connects.
            Log.w(TAG, "no screenshot capability", e)
            results.execute { fail(job, Screenshots.Problem.NO_ACCESS) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "takeScreenshot failed", e)
            results.execute { fail(job, Screenshots.Problem.INTERNAL) }
        }
    }

    /**
     * The picture as an ordinary bitmap, since a hardware one can't be drawn
     * on a software Canvas. The buffer is the service's to close.
     */
    private fun software(screenshot: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = screenshot.hardwareBuffer
        try {
            val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) ?: return null
            val copy = wrapped.copy(Bitmap.Config.ARGB_8888, false)
            wrapped.recycle()
            return copy
        } finally {
            buffer.close()
        }
    }

    /** Every screen is in: one image or two saved, or none at all. */
    private fun finish(job: Job) {
        val pictures = job.pictures
        // One line a screenshot, so a bug report shows what Android handed over.
        Log.i(TAG, "${job.target}: displays ${job.displays}, pictures ${pictures.joinToString { "${it.width}x${it.height}" }}")
        val outcome = runCatching {
            if (job.target == ScreenshotTarget.BOTH) {
                val both = stitch(pictures[0], pictures[1])
                try {
                    save(listOf(both), job.names)
                } finally {
                    both.recycle()
                }
            } else {
                save(pictures, job.names)
            }
        }.getOrElse {
            Log.w(TAG, "couldn't put the screenshot together", it)
            Screenshots.Outcome.Failed(Screenshots.Problem.INTERNAL)
        }
        pictures.forEach { it.recycle() }
        pictures.clear()
        end(job, outcome)
    }

    /** Something failed: whatever was taken goes, so nothing is saved. */
    private fun fail(job: Job, problem: Screenshots.Problem) {
        job.pictures.forEach { it.recycle() }
        job.pictures.clear()
        end(job, Screenshots.Outcome.Failed(problem))
    }

    private fun end(job: Job, outcome: Screenshots.Outcome) {
        handler.post {
            busy = false
            job.done(outcome)
        }
    }

    /** The top screen's picture above the bottom one's, in a new image laid out by [Screenshots.stack]. */
    private fun stitch(top: Bitmap, bottom: Bitmap): Bitmap {
        val stack = Screenshots.stack(
            Screenshots.Size(top.width, top.height),
            Screenshots.Size(bottom.width, bottom.height),
        )
        val (width, height) = stack.size
        // In the top picture's colour space, so it goes in unchanged; sRGB should that one not do.
        val space = top.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
        val image = runCatching { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, false, space) }
            .getOrElse { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) }
        Canvas(image).apply {
            drawColor(Color.BLACK)
            drawBitmap(top, stack.top.x.toFloat(), stack.top.y.toFloat(), null)
            drawBitmap(bottom, stack.bottom.x.toFloat(), stack.bottom.y.toFloat(), null)
        }
        return image
    }

    /** Saves [images] as [names], all or none: when one can't be written, those already written go too. */
    private fun save(images: List<Bitmap>, names: List<String>): Screenshots.Outcome {
        val written = mutableListOf<Uri>()
        for ((image, name) in images.zip(names)) {
            val uri = write(image, name)
            if (uri == null) {
                written.forEach { runCatching { service.contentResolver.delete(it, null, null) } }
                return Screenshots.Outcome.Failed(Screenshots.Problem.NOT_WRITTEN)
            }
            written += uri
        }
        return Screenshots.Outcome.Saved(written.size)
    }

    /** One PNG in Pictures/Screenshots, kept from galleries until it is whole; null when it couldn't be. */
    private fun write(image: Bitmap, name: String): Uri? {
        val resolver = service.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Screenshots.FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
            // A PNG carries no EXIF date, so a gallery sorting by date taken would have none to go by.
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, details) }
            .onFailure { Log.w(TAG, "couldn't add $name", it) }
            .getOrNull() ?: return null
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } == true &&
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0
        }.onFailure { Log.w(TAG, "couldn't write $name", it) }.getOrDefault(false)
        if (!ok) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return uri
    }

    private companion object {
        const val TAG = "PathfinderShot"
    }
}
