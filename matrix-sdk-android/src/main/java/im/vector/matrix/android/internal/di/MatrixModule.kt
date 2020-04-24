/*
 * Copyright 2019 New Vector Ltd
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
 */

package im.vector.matrix.android.internal.di

import android.content.Context
import android.content.res.Resources
import dagger.Module
import dagger.Provides
import im.vector.matrix.android.internal.util.MatrixCoroutineDispatchers
import im.vector.matrix.android.internal.util.createBackgroundHandler
import im.vector.matrix.android.internal.util.newNamedSingleThreadExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.matrix.olm.OlmManager
import java.io.File
import java.util.concurrent.Executors

@Module
internal object MatrixModule {

    @JvmStatic
    @Provides
    @MatrixScope
    fun providesMatrixCoroutineDispatchers(): MatrixCoroutineDispatchers {
        return MatrixCoroutineDispatchers(
                dbTransaction = newNamedSingleThreadExecutor("thread_db_transaction").asCoroutineDispatcher(),
                io = Dispatchers.IO,
                computation = Dispatchers.Default,
                main = Dispatchers.Main,
                crypto = createBackgroundHandler("thread_crypto").asCoroutineDispatcher(),
                dmVerif = newNamedSingleThreadExecutor("thread_dm_verif").asCoroutineDispatcher()
        )
    }

    @JvmStatic
    @Provides
    fun providesResources(context: Context): Resources {
        return context.resources
    }

    @JvmStatic
    @Provides
    @CacheDirectory
    fun providesCacheDir(context: Context): File {
        return context.cacheDir
    }

    @JvmStatic
    @Provides
    @ExternalFilesDirectory
    fun providesExternalFilesDir(context: Context): File? {
        return context.getExternalFilesDir(null)
    }

    @JvmStatic
    @Provides
    @MatrixScope
    fun providesOlmManager(): OlmManager {
        return OlmManager()
    }
}
