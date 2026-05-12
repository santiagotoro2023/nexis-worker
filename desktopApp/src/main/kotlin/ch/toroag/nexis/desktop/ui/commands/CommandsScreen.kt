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
    Column(modifier = Modifier.fillMaxSize().background(NxBg)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "TOOLS & CAPABILITIES",
                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp), color = NxOrange, strokeWidth = 1.5.dp,
                    )
                }
                OutlinedButton(
                    onClick = { vm.load() },
                    enabled = !vm.isLoading,
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                    border  = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    shape   = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("RELOAD", fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                }
            }
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        if (vm.statusMessage.isNotEmpty()) {
            val msgColor = if (vm.hasError) NxRed else NxGreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(msgColor.copy(alpha = 0.06f))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(vm.statusMessage, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = msgColor)
            }
            HorizontalDivider(color = NxBorder, thickness = 1.dp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            item { SectionHeader("BUILT-IN CAPABILITIES", "Always available. The controller can invoke these on any connected worker.") }

            if (vm.builtinCommands.isEmpty() && !vm.isLoading) {
                item { EmptyRow("No built-in capabilities reported by controller.") }
            } else {
                items(vm.builtinCommands) { cmd ->
                    CommandRow(cmd = cmd, onToggle = null)
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            item { SectionHeader("CUSTOM TOOLS", "Defined on the controller. Toggle to enable or disable per tool.") }

            if (vm.customCommands.isEmpty() && !vm.isLoading) {
                item { EmptyRow("No custom tools configured yet. Add them in the controller settings.") }
            } else {
                items(vm.customCommands, key = { it.id }) { cmd ->
                    CommandRow(cmd = cmd, onToggle = { vm.setEnabled(cmd.id, it) })
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp, color = NxOrange)
        Text(subtitle, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2)
    }
}

@Composable
private fun EmptyRow(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NxBg2, RoundedCornerShape(8.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(message, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxFg2.copy(alpha = 0.5f))
    }
}

@Composable
private fun CommandRow(cmd: WorkerCommand, onToggle: ((Boolean) -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NxBg2, RoundedCornerShape(8.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(cmd.name, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, color = NxOrangeLit)
            Text(cmd.description.ifEmpty { "—" }, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, color = NxFg2)
            if (cmd.commandType.isNotEmpty()) {
                Text(cmd.commandType.uppercase(), fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp, letterSpacing = 0.1.sp, color = NxFg2.copy(alpha = 0.45f))
            }
        }

        NxBadge(
            label     = if (cmd.isBuiltin) "BUILT-IN" else "CUSTOM",
            textColor = if (cmd.isBuiltin) NxGreen else NxOrange,
        )

        if (onToggle != null) {
            Switch(
                checked         = cmd.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor    = NxBg,
                    checkedTrackColor    = NxOrange,
                    uncheckedThumbColor  = NxFg2,
                    uncheckedTrackColor  = NxBorder,
                    uncheckedBorderColor = NxBorder,
                ),
            )
        } else {
            NxBadge(label = "ENABLED", textColor = NxGreen)
        }
    }
}

@Composable
private fun NxBadge(label: String, textColor: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(textColor.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .border(1.dp, textColor.copy(alpha = 0.20f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 8.sp,
            letterSpacing = 0.06.sp, color = textColor)
    }
}
