package mozilla.lockbox.presenter

import android.content.Context
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.view.autofill.AutofillManager
import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import io.reactivex.subjects.PublishSubject
import mozilla.lockbox.action.FingerprintSensorAction
import mozilla.lockbox.action.OnboardingStatusAction
import mozilla.lockbox.action.RouteAction
import mozilla.lockbox.action.SettingAction
import mozilla.lockbox.extensions.assertLastValue
import mozilla.lockbox.flux.Action
import mozilla.lockbox.flux.Dispatcher
import mozilla.lockbox.model.FingerprintAuthCallback
import mozilla.lockbox.store.FingerprintStore
import mozilla.lockbox.store.SettingStore
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.mockito.Mockito.`when` as whenCalled

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class FingerprintOnboardingPresenterTest {
    open class FakeOnboardingView : FingerprintOnboardingView {
        var success: Boolean = false
        var errors: Boolean = false
        var failure: Boolean = false

        override fun onSucceeded() {
            success = true
        }

        override fun onFailed(error: String?) {
            failure = true
        }

        override fun onError(error: String?) {
            errors = true
        }

        private var authCallbackStub = PublishSubject.create<FingerprintAuthCallback>()
        override val authCallback: Observable<FingerprintAuthCallback>
            get() = authCallbackStub

        override val onSkipClick = PublishSubject.create<Unit>()
    }

    open class FakeFingerprintStore : FingerprintStore() {
        val authStateStub = PublishSubject.create<FingerprintStore.AuthenticationState>()
        override val authState: Observable<FingerprintStore.AuthenticationState>
            get() = authStateStub

        private val isFingerprintAuthAvailableStub: Boolean = true
        override val isFingerprintAuthAvailable: Boolean
            get() = isFingerprintAuthAvailableStub
    }

    open class FakeSettingStore : SettingStore() {
        private val isAutofillAvailableStub: Boolean = true
        private val hasEnabledAutofillServicesStub: Boolean = true

        override val autofillAvailable: Boolean
            get() = isAutofillAvailableStub

        override val isCurrentAutofillProvider: Boolean
            get() = hasEnabledAutofillServicesStub
    }

    @Mock
    private val autofillManager = PowerMockito.mock(AutofillManager::class.java)

    @Mock
    val context: Context = Mockito.mock(Context::class.java)

    @Mock
    private val fingerprintManager = PowerMockito.mock(FingerprintManager::class.java)

    val dispatcher = Dispatcher()
    val settingStore = FakeSettingStore()
    private val view = Mockito.spy(FakeOnboardingView())
    private val fingerprintStore = FakeFingerprintStore()
    private val dispatcherObserver = TestObserver.create<Action>()

    val subject = FingerprintOnboardingPresenter(view, dispatcher, fingerprintStore, settingStore)

    @Before
    fun setUp() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 26)

        fingerprintStore.fingerprintManager = fingerprintManager
        dispatcher.register.subscribe(dispatcherObserver)

        whenCalled(autofillManager.isAutofillSupported).thenReturn(settingStore.autofillAvailable)
        whenCalled(autofillManager.hasEnabledAutofillServices()).thenReturn(settingStore.isCurrentAutofillProvider)
        whenCalled(context.getSystemService(AutofillManager::class.java)).thenReturn(autofillManager)

        subject.onViewReady()
    }

    @Test
    fun `update on succeeded state`() {
        fingerprintStore.authStateStub.onNext(FingerprintStore.AuthenticationState.Succeeded)
        Assert.assertEquals(true, view.success)
    }

    @Test
    fun `update on failed state`() {
        fingerprintStore.authStateStub.onNext(FingerprintStore.AuthenticationState.Failed("error"))
        Assert.assertEquals(true, view.failure)
    }

    @Test
    fun `update on error state`() {
        fingerprintStore.authStateStub.onNext(FingerprintStore.AuthenticationState.Error("error"))
        Assert.assertEquals(true, view.errors)
    }

    @Test
    fun `move to autofill screen when skip is tapped and autofill is available`() {
        view.onSkipClick.onNext(Unit)

        dispatcherObserver.assertValueAt(0, OnboardingStatusAction(true))
        dispatcherObserver.assertValueAt(1, SettingAction.UnlockWithFingerprint(false))
        dispatcherObserver.assertValueAt(2, RouteAction.Onboarding.Autofill)
    }

    @Test
    fun `move to confirmation screen when skip is tapped and autofill is not available`() {
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 15)

        view.onSkipClick.onNext(Unit)
        dispatcherObserver.assertValueAt(0, OnboardingStatusAction(true))
        dispatcherObserver.assertValueAt(1, SettingAction.UnlockWithFingerprint(false))
        dispatcherObserver.assertValueAt(2, RouteAction.Onboarding.Confirmation)
    }

    @Test
    fun `should start listening on resume`() {
        subject.onResume()
        dispatcherObserver.assertValue(FingerprintSensorAction.Start)
    }

    @Test
    fun `should stop listening on pause`() {
        subject.onPause()
        dispatcherObserver.assertLastValue(FingerprintSensorAction.Stop)
    }
}