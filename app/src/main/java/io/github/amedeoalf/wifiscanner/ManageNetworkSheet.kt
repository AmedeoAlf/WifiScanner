package io.github.amedeoalf.wifiscanner

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.collections.isEmpty
import kotlin.collections.toList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageNetworksSheet(
    toast: (String) -> Unit,
    shareFiles: (files: List<File>) -> Unit,
    areFilesCleared: MutableState<Boolean>,
    refresh: () -> Unit,
    dumpDir: File,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismiss,
        sheetState = bottomSheetState
    ) {
        Column() {
            @Composable
            fun Btn(@DrawableRes icon: Int, text: String, onClick: () -> Unit) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(icon), "Icon", Modifier.padding(16.dp))
                    Text(text)
                }
            }

            // Is it scanning the directory on each frame??
            val fileCount = if (areFilesCleared.value) 0 else dumpDir.listFiles()?.size ?: 0
            Text(
                "$fileCount report ${if (fileCount == 1) "salvato" else "salvati"}",
                Modifier.padding(horizontal = 20.dp)
            )
            Btn(R.drawable.outline_share_24, "Condividi") {
                val files = dumpDir.listFiles()
                if (files == null || files.isEmpty()) {
                    toast("No reports saved")
                } else {
                    shareFiles(files.toList())
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 5.dp))
            Btn(R.drawable.outline_delete_24, "Pulisci salvataggi") {
                dumpDir.deleteRecursively()
                areFilesCleared.value = true
            }
            HorizontalDivider(Modifier.padding(horizontal = 5.dp))
            val scope = rememberCoroutineScope()
            Btn(R.drawable.outline_refresh_24, "Ripeti scansione wifi") {
                refresh()
                scope.launch {
                    bottomSheetState.hide()
                }.invokeOnCompletion {
                    if (!bottomSheetState.isVisible) onDismiss()
                }
            }
        }
    }
}