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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snailtrail.androidgo.R

@Composable
fun TitleBar(
    onMenuNewGame: () -> Unit,
    onMenuSave: () -> Unit,
    onMenuHistory: () -> Unit,
    onMenuAbout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.app_name),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        IconButton(onClick = onMenuNewGame, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_new_game),
                contentDescription = stringResource(R.string.menu_new_game),
                modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onMenuSave, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_save),
                contentDescription = stringResource(R.string.menu_save),
                modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onMenuHistory, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_history),
                contentDescription = stringResource(R.string.menu_history),
                modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onMenuAbout, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_about),
                contentDescription = stringResource(R.string.menu_about),
                modifier = Modifier.size(24.dp))
        }
    }
}
