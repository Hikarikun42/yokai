package dev.yokai.presentation.component.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.storage.preference.collectAsState

@Composable
fun storageLocationText(
    storageDirPref: eu.kanade.tachiyomi.core.preference.Preference<String>,
): String {
    val context = LocalContext.current
    val storageDir by storageDirPref.collectAsState()

    if (storageDir == storageDirPref.defaultValue()) {
        return stringResource(R.string.no_location_set)
    }

    return remember(storageDir) {
        val file = UniFile.fromUri(context, storageDir.toUri())
        file?.filePath
    } ?: stringResource(R.string.invalid_location, storageDir)
}
