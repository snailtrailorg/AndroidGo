package org.snailtrail.androidgo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snailtrail.androidgo.R

data class ButtonLayout(
    val position: Offset,
    val size: IntSize
)

@Composable
fun TitleBar(
    onMenuNewGame: () -> Unit,
    onMenuSettings: () -> Unit,
    onMenuSave: () -> Unit,
    onMenuHistory: () -> Unit,
    onMenuAbout: () -> Unit,
    onButtonLayout: ((String, ButtonLayout) -> Unit)? = null
) {
    // Helper: track layout of a button
    fun Modifier.trackButton(key: String): Modifier = this.onGloballyPositioned { coords ->
        onButtonLayout?.invoke(key, ButtonLayout(coords.positionInRoot(), coords.size))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_app_icon),
            contentDescription = null,
            modifier = Modifier.size(36.dp).padding(start = 4.dp),
            tint = Color.Unspecified
        )
        Text(
            stringResource(R.string.app_name),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        IconButton(
            onClick = onMenuNewGame,
            modifier = Modifier.size(36.dp).trackButton("new_game")
        ) {
            Icon(painterResource(R.drawable.ic_new_game),
                contentDescription = stringResource(R.string.menu_new_game),
                modifier = Modifier.size(30.dp))
        }
        IconButton(
            onClick = onMenuSettings,
            modifier = Modifier.size(36.dp).trackButton("settings"),
        ) {
            Icon(painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.menu_settings),
                modifier = Modifier.size(30.dp))
        }
        IconButton(
            onClick = onMenuSave,
            modifier = Modifier.size(36.dp).trackButton("save"),
        ) {
            Icon(painterResource(R.drawable.ic_save),
                contentDescription = stringResource(R.string.menu_save),
                modifier = Modifier.size(30.dp))
        }
        IconButton(
            onClick = onMenuHistory,
            modifier = Modifier.size(36.dp).trackButton("history"),
        ) {
            Icon(painterResource(R.drawable.ic_history),
                contentDescription = stringResource(R.string.menu_history),
                modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = onMenuAbout, modifier = Modifier.size(36.dp).trackButton("about")) {
            Icon(painterResource(R.drawable.ic_about),
                contentDescription = stringResource(R.string.menu_about),
                modifier = Modifier.size(30.dp))
        }
    }
}
