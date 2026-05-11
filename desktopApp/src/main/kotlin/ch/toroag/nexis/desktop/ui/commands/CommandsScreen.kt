package ch.toroag.nexis.desktop.ui.commands

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.ui.theme.*

@Composable
fun CommandsScreen(vm: CommandsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NxBg),
    ) {
        // ── Page header — matches web UI page-head style ──────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "TOOLS & CAPABILITIES",
                fontFamily    = FontFamily.Monospace,
                fontSize      = 10.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                color         = NxFg2,
            )
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        // ── Scrollable content ────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Card header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NxBg3, RoundedCornerShape(16.dp))
                        .border(1.dp, NxBorder, RoundedCornerShape(16.dp)),
                ) {
                    // Card title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .border(
                                width = 0.dp,
                                color = NxBorder,
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            ),
                    ) {
                        Text(
                            "BUILT-IN CAPABILITIES",
                            fontFamily    = FontFamily.Monospace,
                            fontSize      = 9.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 0.15.sp,
                            color         = NxOrange,
                        )
                    }
                    HorizontalDivider(color = NxBorder, thickness = 1.dp)

                    // Sub-header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Always available. The controller can invoke these on any connected worker.",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 10.sp,
                            color      = NxFg2,
                        )
                    }
                    HorizontalDivider(color = NxBorder, thickness = 1.dp)

                    // Column headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text("COMMAND",     fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.12.sp, color = NxFg2, modifier = Modifier.width(160.dp))
                        Text("DESCRIPTION", fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.12.sp, color = NxFg2, modifier = Modifier.weight(1f))
                        Text("TYPE",        fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 0.12.sp, color = NxFg2, modifier = Modifier.width(70.dp))
                    }
                    HorizontalDivider(color = NxBorder, thickness = 1.dp)
                }
            }

            // Command rows — outside the card header but visually attached
            items(vm.builtinCommands) { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NxBg2, RoundedCornerShape(8.dp))
                        .border(1.dp, NxBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        cmd.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        color      = NxOrangeLit,
                        modifier   = Modifier.width(160.dp),
                    )
                    Text(
                        cmd.description,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 10.sp,
                        color      = NxFg2,
                        modifier   = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(70.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(
                            modifier = Modifier
                                .background(NxGreen.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                                .border(1.dp, NxGreen.copy(alpha = 0.20f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                if (cmd.isBuiltin) "BUILT-IN" else "CUSTOM",
                                fontFamily    = FontFamily.Monospace,
                                fontSize      = 9.sp,
                                letterSpacing = 0.06.sp,
                                color         = if (cmd.isBuiltin) NxGreen else NxOrange,
                            )
                        }
                    }
                }
            }

            // Bottom spacer
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
