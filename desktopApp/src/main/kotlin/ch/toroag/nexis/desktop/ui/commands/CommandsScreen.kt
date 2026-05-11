package ch.toroag.nexis.desktop.ui.commands

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
            .background(NxBg)
            .padding(24.dp)
    ) {
        Text(
            "COMMANDS",
            fontFamily    = FontFamily.Monospace,
            fontSize      = 13.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            color         = NxFg,
        )
        Text(
            "Built-in commands this worker device responds to from the controller.",
            fontFamily = FontFamily.Monospace,
            fontSize   = 10.sp,
            color      = NxFg2,
            modifier   = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        HorizontalDivider(color = NxBorder, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("COMMAND",     fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2, modifier = Modifier.width(180.dp))
            Text("DESCRIPTION", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2, modifier = Modifier.weight(1f))
            Text("TYPE",        fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2, modifier = Modifier.width(72.dp))
        }
        HorizontalDivider(color = NxBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(vm.builtinCommands) { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NxBg2, RoundedCornerShape(6.dp))
                        .border(0.5.dp, NxBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        cmd.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        color      = NxOrange,
                        modifier   = Modifier.width(180.dp),
                    )
                    Text(
                        cmd.description,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 10.sp,
                        color      = NxFg2,
                        modifier   = Modifier.weight(1f),
                    )
                    Row(
                        modifier          = Modifier.width(72.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint     = NxGreen,
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (cmd.isBuiltin) "BUILT-IN" else "CUSTOM",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 9.sp,
                            color      = if (cmd.isBuiltin) NxGreen else NxOrange,
                        )
                    }
                }
            }
        }
    }
}
