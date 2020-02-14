/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.riotx.features.settings.devices

import androidx.lifecycle.viewModelScope
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.FragmentViewModelContext
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.MvRxState
import com.airbnb.mvrx.MvRxViewModelFactory
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import com.airbnb.mvrx.ViewModelContext
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import im.vector.matrix.android.api.failure.Failure
import im.vector.matrix.android.api.session.Session
import im.vector.matrix.android.api.session.crypto.sas.VerificationService
import im.vector.matrix.android.api.session.crypto.sas.VerificationTransaction
import im.vector.matrix.android.api.session.crypto.sas.VerificationTxState
import im.vector.matrix.android.internal.auth.data.LoginFlowTypes
import im.vector.matrix.android.internal.crypto.model.CryptoDeviceInfo
import im.vector.matrix.android.internal.crypto.model.rest.DeviceInfo
import im.vector.matrix.rx.rx
import im.vector.riotx.core.platform.VectorViewModel
import im.vector.riotx.features.crypto.verification.supportedVerificationMethods
import kotlinx.coroutines.launch
import timber.log.Timber

data class DevicesViewState(
        val myDeviceId: String = "",
        val devices: Async<List<DeviceInfo>> = Uninitialized,
        val cryptoDevices: Async<List<CryptoDeviceInfo>> = Uninitialized,
        // TODO Replace by isLoading boolean
        val request: Async<Unit> = Uninitialized
) : MvRxState

class DevicesViewModel @AssistedInject constructor(@Assisted initialState: DevicesViewState,
                                                   private val session: Session)
    : VectorViewModel<DevicesViewState, DevicesAction, DevicesViewEvents>(initialState), VerificationService.VerificationListener {

    @AssistedInject.Factory
    interface Factory {
        fun create(initialState: DevicesViewState): DevicesViewModel
    }

    companion object : MvRxViewModelFactory<DevicesViewModel, DevicesViewState> {

        @JvmStatic
        override fun create(viewModelContext: ViewModelContext, state: DevicesViewState): DevicesViewModel? {
            val fragment: VectorSettingsDevicesFragment = (viewModelContext as FragmentViewModelContext).fragment()
            return fragment.devicesViewModelFactory.create(state)
        }
    }

    // temp storage when we ask for the user password
    private var _currentDeviceId: String? = null
    private var _currentSession: String? = null

    init {
        refreshDevicesList()
        session.getCryptoService().getVerificationService().addListener(this)

        session.rx().liveUserCryptoDevices(session.myUserId)
                .execute {
                    copy(
                            cryptoDevices = it
                    )
                }
    }

    override fun onCleared() {
        session.getCryptoService().getVerificationService().removeListener(this)
        super.onCleared()
    }

    override fun transactionCreated(tx: VerificationTransaction) {}
    override fun transactionUpdated(tx: VerificationTransaction) {
        if (tx.state == VerificationTxState.Verified) {
            refreshDevicesList()
        }
    }

    /**
     * Force the refresh of the devices list.
     * The devices list is the list of the devices where the user is logged in.
     * It can be any mobile devices, and any browsers.
     */
    private fun refreshDevicesList() {
        if (!session.sessionParams.credentials.deviceId.isNullOrEmpty()) {
            // display something asap
            val localKnown = session.getCryptoService().getUserDevices(session.myUserId).map {
                DeviceInfo(
                        user_id = session.myUserId,
                        deviceId = it.deviceId,
                        displayName = it.displayName()
                )
            }
            setState {
                copy(
                        myDeviceId = session.sessionParams.credentials.deviceId ?: "",
                        // Keep known list if we have it, and let refresh go in backgroung
                        devices = this.devices.takeIf { it is Success } ?: Success(localKnown),
                        cryptoDevices = Success(session.getCryptoService().getUserDevices(session.myUserId))
                )
            }
            viewModelScope.launch {
                val deviceList = session.getCryptoService().getDevicesList()
                setState {
                    copy(devices = Success(deviceList))
                }
                try {
                    session.getCryptoService().downloadKeys(listOf(session.myUserId), true)
                } catch (failure: Throwable) {
                    Timber.v("Failure downloading keys")
                }
                setState {
                    copy(
                            cryptoDevices = Success(session.getCryptoService().getUserDevices(session.myUserId))
                    )
                }
            }
        } else {
            // Should not happen
        }
    }

    override fun handle(action: DevicesAction) {
        return when (action) {
            is DevicesAction.Retry          -> refreshDevicesList()
            is DevicesAction.Delete         -> handleDelete(action)
            is DevicesAction.Password       -> handlePassword(action)
            is DevicesAction.Rename         -> handleRename(action)
            is DevicesAction.PromptRename   -> handlePromptRename(action)
            is DevicesAction.VerifyMyDevice -> handleVerify(action)
        }
    }

    private fun handleVerify(action: DevicesAction.VerifyMyDevice) {
        val txID = session.getCryptoService().getVerificationService().requestKeyVerification(supportedVerificationMethods, session.myUserId, listOf(action.deviceId))
        _viewEvents.post(DevicesViewEvents.ShowVerifyDevice(
                session.myUserId,
                txID.transactionId
        ))
    }

    private fun handlePromptRename(action: DevicesAction.PromptRename) = withState { state ->
        val info = state.devices.invoke()?.firstOrNull { it.deviceId == action.deviceId }
        if (info != null) {
            _viewEvents.post(DevicesViewEvents.PromptRenameDevice(info))
        }
    }

    private fun handleRename(action: DevicesAction.Rename) {
        viewModelScope.launch {
            try {
                session.getCryptoService().setDeviceName(action.deviceId, action.newName)
                setState {
                    copy(request = Success(Unit))
                }
                refreshDevicesList()
            } catch (failure: Throwable) {
                setState {
                    copy(request = Fail(failure))
                }
                _viewEvents.post(DevicesViewEvents.Failure(failure))
            }
        }
    }

    /**
     * Try to delete a device.
     */
    private fun handleDelete(action: DevicesAction.Delete) {
        val deviceId = action.deviceId
        setState { copy(request = Loading()) }
        viewModelScope.launch {
            try {
                session.getCryptoService().deleteDevice(deviceId)
                setState {
                    copy(request = Success(Unit))
                }
                // force settings update
                refreshDevicesList()
            } catch (failure: Throwable) {
                var isPasswordRequestFound = false
                if (failure is Failure.RegistrationFlowError) {
                    // We only support LoginFlowTypes.PASSWORD
                    // Check if we can provide the user password
                    failure.registrationFlowResponse.flows?.forEach { interactiveAuthenticationFlow ->
                        isPasswordRequestFound = isPasswordRequestFound || interactiveAuthenticationFlow.stages?.any { it == LoginFlowTypes.PASSWORD } == true
                    }
                    if (isPasswordRequestFound) {
                        _currentDeviceId = deviceId
                        _currentSession = failure.registrationFlowResponse.session
                        setState {
                            copy(request = Success(Unit))
                        }
                        _viewEvents.post(DevicesViewEvents.RequestPassword)
                    }
                }
                if (!isPasswordRequestFound) {
                    // LoginFlowTypes.PASSWORD not supported, and this is the only one RiotX supports so far...
                    setState {
                        copy(request = Fail(failure))
                    }
                    _viewEvents.post(DevicesViewEvents.Failure(failure))
                }
            }
        }
    }

    private fun handlePassword(action: DevicesAction.Password) {
        val currentDeviceId = _currentDeviceId
        if (currentDeviceId.isNullOrBlank()) {
            // Abort
            return
        }
        setState { copy(request = Loading()) }
        viewModelScope.launch {
            try {
                session.getCryptoService().deleteDeviceWithUserPassword(currentDeviceId, _currentSession, action.password)
                _currentDeviceId = null
                _currentSession = null
                setState {
                    copy(request = Success(Unit))
                }
                // force settings update
                refreshDevicesList()
            } catch (failure: Throwable) {
                _currentDeviceId = null
                _currentSession = null
                // Password is maybe not good
                setState {
                    copy(request = Fail(failure))
                }
                _viewEvents.post(DevicesViewEvents.Failure(failure))
            }
        }
    }
}
