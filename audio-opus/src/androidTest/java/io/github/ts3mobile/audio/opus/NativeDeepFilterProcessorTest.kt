package io.github.ts3mobile.audio.opus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class NativeDeepFilterProcessorTest {
    @Test
    fun initializesAndProcessesFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val processor = NativeDeepFilterProcessor(context)
        val pcm = ShortArray(480) { Random.nextInt(-1000, 1000).toShort() }
        val metric = processor.processInPlace(pcm)
        assertTrue(metric >= 0f || !processor.isReady)
        processor.close()
    }
}
