package org.snailtrail.androidgo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.snailtrail.androidgo.R
import org.snailtrail.androidgo.engine.EngineManager
import org.snailtrail.androidgo.game.PrefKeys
import kotlin.math.roundToInt

enum class PlayerRole { Human, AI }

data class PlayerConfig(
    val role: PlayerRole = PlayerRole.Human,
    val name: String = "黑方",
    val engine: AiEngine = AiEngine.GnuGo,
    val difficulty: Int = 5
)

enum class AiEngine(val label: String) {
    GnuGo("GNU Go"),
    KataGo("KataGo")
}

data class NewGameConfig(
    val boardSize: Int = 13,
    val handicap: Int = 0,
    val blackPlayer: PlayerConfig = PlayerConfig(name = "黑方"),
    val whitePlayer: PlayerConfig = PlayerConfig(
        role = PlayerRole.AI, name = "GNU Go", engine = AiEngine.GnuGo
    )
)

@Composable
fun NewGameDialog(
    onConfirm: (NewGameConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(PrefKeys.NAME, Context.MODE_PRIVATE) }

    var boardSize by remember { mutableIntStateOf(prefs.getInt(PrefKeys.BOARD_SIZE, 13)) }
    var handicap by remember { mutableIntStateOf(prefs.getInt(PrefKeys.HANDICAP, 0)) }

    var blackRole by remember { mutableStateOf(enumPref(prefs, PrefKeys.BLACK_ROLE, PlayerRole.Human)) }
    var blackName by remember { mutableStateOf(prefs.getString(PrefKeys.BLACK_NAME, "黑方") ?: "黑方") }
    var blackNameEdited by remember { mutableStateOf(false) }
    var blackEngine by remember { mutableStateOf(enumPref(prefs, PrefKeys.BLACK_ENGINE, AiEngine.GnuGo)) }
    var blackDifficulty by remember { mutableIntStateOf(prefs.getInt(PrefKeys.BLACK_DIFFICULTY, 5)) }

    var whiteRole by remember { mutableStateOf(enumPref(prefs, PrefKeys.WHITE_ROLE, PlayerRole.AI)) }
    var whiteName by remember { mutableStateOf(prefs.getString(PrefKeys.WHITE_NAME, "GNU Go") ?: "GNU Go") }
    var whiteNameEdited by remember { mutableStateOf(false) }
    var whiteEngine by remember { mutableStateOf(enumPref(prefs, PrefKeys.WHITE_ENGINE, AiEngine.GnuGo)) }
    var whiteDifficulty by remember { mutableIntStateOf(prefs.getInt(PrefKeys.WHITE_DIFFICULTY, 5)) }

    val defaultHumanBlackName = stringResource(R.string.default_black_name)
    val defaultHumanWhiteName = stringResource(R.string.default_white_name)
    fun defaultName(role: PlayerRole, engine: AiEngine, isBlack: Boolean): String = when (role) {
        PlayerRole.Human -> if (isBlack) defaultHumanBlackName else defaultHumanWhiteName
        PlayerRole.AI -> engine.label
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .navigationBarsPadding()
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                        .heightIn(max = 600.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.new_game_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

                    // ── Group: Board size ──
                    GroupBox(stringResource(R.string.board_size_label)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf<Pair<Int, Int>>(
                                9 to R.string.board_size_9,
                                13 to R.string.board_size_13,
                                19 to R.string.board_size_19
                            ).forEach { (size, resId) ->
                                FilterChip(
                                    selected = boardSize == size,
                                    onClick = { boardSize = size },
                                    label = { Text(stringResource(resId), fontSize = 14.sp) },
                                    modifier = Modifier.padding(end = 6.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // ── Group: Handicap ──
                    GroupBox(stringResource(R.string.handicap_label)) {
                        SliderWithLabel(
                            value = handicap.toFloat(),
                            onValueChange = { handicap = it.roundToInt() },
                            valueRange = 0f..9f,
                            steps = 8,
                            label = if (handicap == 0) stringResource(R.string.handicap_none) else stringResource(R.string.handicap_count, handicap)
                        )
                    }

                    // ── Group: Players ──
                    GroupBox(stringResource(R.string.player_info_label)) {
                        PlayerBlock(
                            color = Color(0xFF1A1A1A),
                            label = stringResource(R.string.black_label),
                            role = blackRole,
                            name = blackName,
                            engine = blackEngine,
                            difficulty = blackDifficulty,
                            onRoleChange = { r ->
                                blackRole = r
                                if (!blackNameEdited) blackName = defaultName(r, blackEngine, true)
                            },
                            onNameChange = { blackName = it; blackNameEdited = true },
                            onEngineChange = { e ->
                                blackEngine = e
                                if (!blackNameEdited) blackName = defaultName(blackRole, e, true)
                            },
                            onDifficultyChange = { blackDifficulty = it }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        PlayerBlock(
                            color = Color(0xFFE8E8E8),
                            label = stringResource(R.string.white_label),
                            role = whiteRole,
                            name = whiteName,
                            engine = whiteEngine,
                            difficulty = whiteDifficulty,
                            onRoleChange = { r ->
                                whiteRole = r
                                if (!whiteNameEdited) whiteName = defaultName(r, whiteEngine, false)
                            },
                            onNameChange = { whiteName = it; whiteNameEdited = true },
                            onEngineChange = { e ->
                                whiteEngine = e
                                if (!whiteNameEdited) whiteName = defaultName(whiteRole, e, false)
                            },
                            onDifficultyChange = { whiteDifficulty = it }
                        )
                    }
                } // end scrollable

                // ── Fixed buttons ──
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        Button(onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)
                        ) { Text(stringResource(R.string.btn_cancel), fontSize = 14.sp) }
                        Button(onClick = {
                            savePrefs(prefs, boardSize, handicap,
                                blackRole, blackName, blackEngine, blackDifficulty,
                                whiteRole, whiteName, whiteEngine, whiteDifficulty)
                            onConfirm(
                                NewGameConfig(
                                    boardSize = boardSize,
                                    handicap = handicap,
                                    blackPlayer = PlayerConfig(blackRole, blackName, blackEngine, blackDifficulty),
                                    whitePlayer = PlayerConfig(whiteRole, whiteName, whiteEngine, whiteDifficulty)
                                )
                            )
                        },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)
                        ) { Text(stringResource(R.string.btn_start), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            } // outer Column
        } // Surface
    } // Dialog
}

// ── Group box ──

@Composable
private fun GroupBox(title: String, content: @Composable () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            content()
        }
    }
}

// ── Reusable slider row ──

@Composable
private fun SliderWithLabel(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f).height(32.dp)
        )
    }
}

// ── One player block ──

@Composable
private fun PlayerBlock(
    color: Color,
    label: String,
    role: PlayerRole,
    name: String,
    engine: AiEngine,
    difficulty: Int,
    onRoleChange: (PlayerRole) -> Unit,
    onNameChange: (String) -> Unit,
    onEngineChange: (AiEngine) -> Unit,
    onDifficultyChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Row 1: stone + label + role
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Gray, CircleShape)
            )
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            RoleChip(stringResource(R.string.role_human), role == PlayerRole.Human) { onRoleChange(PlayerRole.Human) }
            Spacer(Modifier.width(6.dp))
            RoleChip(stringResource(R.string.role_ai), role == PlayerRole.AI) { onRoleChange(PlayerRole.AI) }
        }

        // Row 2: name
        SettingRow(stringResource(R.string.name_label)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Engine + difficulty (AI only)
        if (role == PlayerRole.AI) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.engine_label), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp))
                AiEngine.entries.forEach { eng ->
                    val sel = engine == eng
                    Text(
                        eng.label, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .clickable { onEngineChange(eng) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            SettingRow(stringResource(R.string.difficulty_label)) {
                Text(difficultyLabel(engine, difficulty), fontSize = 13.sp, modifier = Modifier.width(68.dp))
                Slider(
                    value = difficulty.toFloat(),
                    onValueChange = { onDifficultyChange(it.roundToInt()) },
                    valueRange = 1f..10f, steps = 8,
                    modifier = Modifier.weight(1f).height(28.dp)
                )
            }
        }
    }
}

// ── Tiny helpers ──

@Composable
private fun RoleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
        )
        Text(
            label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 5.dp)
        )
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
        content()
    }
}

@Composable
private fun difficultyLabel(engine: AiEngine, level: Int): String = when (engine) {
    AiEngine.GnuGo -> stringResource(R.string.difficulty_gnugo, level)
    AiEngine.KataGo -> stringResource(R.string.difficulty_katago, level)
}

@Composable
private fun formatKomi(k: Float): String =
    if (k == k.toLong().toFloat()) "${k.toInt()}" else "$k"

// ── Preferences ──

private inline fun <reified T : Enum<T>> enumPref(prefs: android.content.SharedPreferences, key: String, default: T): T {
    val name = prefs.getString(key, null) ?: return default
    return T::class.java.enumConstants?.firstOrNull { it.name == name } ?: default
}

private fun savePrefs(
    prefs: android.content.SharedPreferences,
    boardSize: Int, handicap: Int,
    blackRole: PlayerRole, blackName: String, blackEngine: AiEngine, blackDifficulty: Int,
    whiteRole: PlayerRole, whiteName: String, whiteEngine: AiEngine, whiteDifficulty: Int
) {
    prefs.edit()
        .putInt(PrefKeys.BOARD_SIZE, boardSize)
        .putInt(PrefKeys.HANDICAP, handicap)
        .putString(PrefKeys.BLACK_ROLE, blackRole.name)
        .putString(PrefKeys.BLACK_NAME, blackName)
        .putString(PrefKeys.BLACK_ENGINE, blackEngine.name)
        .putInt(PrefKeys.BLACK_DIFFICULTY, blackDifficulty)
        .putString(PrefKeys.WHITE_ROLE, whiteRole.name)
        .putString(PrefKeys.WHITE_NAME, whiteName)
        .putString(PrefKeys.WHITE_ENGINE, whiteEngine.name)
        .putInt(PrefKeys.WHITE_DIFFICULTY, whiteDifficulty)
        .apply()
}
