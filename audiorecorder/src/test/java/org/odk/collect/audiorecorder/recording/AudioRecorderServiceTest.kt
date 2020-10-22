package org.odk.collect.audiorecorder.recording

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.odk.collect.audiorecorder.AudioRecorderDependencyModule
import org.odk.collect.audiorecorder.overrideDependencies
import org.odk.collect.audiorecorder.recorder.Recorder
import org.robolectric.Robolectric.buildService
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AudioRecorderServiceTest {

    private val application: Application by lazy { ApplicationProvider.getApplicationContext() }
    private val recordingRepository = RecordingRepository()
    private val recorder = FakeRecorder()

    @Before
    fun setup() {
        application.overrideDependencies(
            object : AudioRecorderDependencyModule() {
                override fun providesRecorder(application: Application): Recorder {
                    return recorder
                }

                override fun providesRecordingRepository(): RecordingRepository {
                    return recordingRepository
                }
            }
        )
    }

    @Test
    fun startAction_startsRecorder() {
        val intent = Intent(application, AudioRecorderService::class.java)
        intent.action = AudioRecorderService.ACTION_START

        buildService(AudioRecorderService::class.java, intent)
            .create()
            .startCommand(0, 0)

        assertThat(recorder.isRecording(), equalTo(true))
    }

    @Test
    fun startAction_whenRecordingInProgress_doesNothing() {
        val intent = Intent(application, AudioRecorderService::class.java)
        intent.action = AudioRecorderService.ACTION_START

        buildService(AudioRecorderService::class.java, intent)
            .create()
            .startCommand(0, 0)

        assertThat(recorder.isRecording(), equalTo(true))

        buildService(AudioRecorderService::class.java, intent)
            .create()
            .startCommand(0, 0)

        assertThat(recorder.isRecording(), equalTo(true))
        assertThat(recorder.recordings.size, equalTo(1))
    }

    @Test
    fun stopAction_stopsRecorder_stopsSelf_andSetsRecordingOnRepository() {
        val intent = Intent(application, AudioRecorderService::class.java)
        intent.action = AudioRecorderService.ACTION_STOP

        val service = buildService(AudioRecorderService::class.java, intent)
            .create()
            .startCommand(0, 0)

        assertThat(recorder.isRecording(), equalTo(false))
        assertThat(recordingRepository.getRecording().value, equalTo(recorder.file))
        assertThat(shadowOf(service.get()).isStoppedBySelf, equalTo(true))
    }

    @Test
    fun cancelAction_cancelsRecorder_stopsSelf() {
        val intent = Intent(application, AudioRecorderService::class.java)
        intent.action = AudioRecorderService.ACTION_CANCEL

        val service = buildService(AudioRecorderService::class.java, intent)
            .create()
            .startCommand(0, 0)

        assertThat(recorder.isRecording(), equalTo(false))
        assertThat(recorder.wasCancelled(), equalTo(true))
        assertThat(shadowOf(service.get()).isStoppedBySelf, equalTo(true))
    }
}
