/*
 * Copyright 2020 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package im.vector.riotx.features.settings.devices

import im.vector.matrix.android.api.session.Session
import im.vector.matrix.android.internal.crypto.model.CryptoDeviceInfo
import im.vector.matrix.android.internal.crypto.model.rest.DeviceInfo
import im.vector.riotx.core.platform.VectorViewEvents

/**
 * Transient events for Ignored users screen
 */
sealed class DevicesViewEvents : VectorViewEvents {
    data class Loading(val message: CharSequence? = null) : DevicesViewEvents()
    data class Failure(val throwable: Throwable) : DevicesViewEvents()

    object RequestPassword : DevicesViewEvents()

    data class PromptRenameDevice(val deviceInfo: DeviceInfo) : DevicesViewEvents()

    data class ShowVerifyDevice(
            val userId: String,
            val transactionId: String?
    ) : DevicesViewEvents()

    data class SelfVerification(
            val session: Session
    ) : DevicesViewEvents()

    data class ShowManuallyVerify(val cryptoDeviceInfo: CryptoDeviceInfo) : DevicesViewEvents()
}
