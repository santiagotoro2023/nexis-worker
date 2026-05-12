package ch.toroag.nexis.desktop.ui.personality

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
fun PersonalityScreen(vm: PersonalityViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().background(NxBg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "PERSONALITY",
                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
            )
            if (vm.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp), color = NxOrange,
                    strokeWidth = 1.5.dp,
                )
            }
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        if (vm.statusMessage.isNotEmpty()) {
            val msgColor = if (vm.hasError) NxRed else NxGreen
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(msgColor.copy(alpha = 0.06f))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    vm.statusMessage, fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, color = msgColor,
                )
            }
            HorizontalDivider(color = NxBorder, thickness = 1.dp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NxField(label = "AI NAME", value = vm.name, singleLine = true) { vm.name = it }
            }

            item {
                NxTextArea(
                    label    = "STYLE & TONE",
                    hint     = "Short description of speaking style shown as reminder per message",
                    value    = vm.style,
                    minLines = 3,
                ) { vm.style = it }
            }

            item {
                NxTextArea(
                    label    = "BASE PERSONALITY",
                    hint     = "Full personality definition injected at the start of every system prompt",
                    value    = vm.basePrompt,
                    minLines = 10,
                ) { vm.basePrompt = it }
            }

            item {
                NxTextArea(
                    label    = "CUSTOM INSTRUCTIONS",
                    hint     = "Extra instructions appended after the personality block (optional)",
                    value    = vm.customInstr,
                    minLines = 4,
                ) { vm.customInstr = it }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { vm.save() },
                        enabled = !vm.isLoading,
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = NxOrange, contentColor = NxBg,
                            disabledContainerColor = NxOrange.copy(alpha = 0.4f),
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "SAVE", fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.resetToDefault() },
                        enabled = !vm.isLoading,
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                        shape   = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "RESET TO DEFAULT", fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = { vm.load() },
                        enabled = !vm.isLoading,
                        colors  = ButtonDefaults.outlinedButtonColors(contentColor = NxFg2),
                        border  = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                        shape   = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            "RELOAD", fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun NxField(label: String, value: String, singleLine: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
            letterSpacing = 0.12.sp, color = NxFg2,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = NxOrange.copy(alpha = 0.5f),
                unfocusedBorderColor    = NxBorder,
                focusedTextColor        = NxFg,
                unfocusedTextColor      = NxFg,
                cursorColor             = NxOrange,
                focusedContainerColor   = NxBg2,
                unfocusedContainerColor = NxBg2,
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun NxTextArea(label: String, hint: String, value: String, minLines: Int, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Text(
                label, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                letterSpacing = 0.12.sp, color = NxFg2,
            )
            Text(
                hint, fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                color = NxFg2.copy(alpha = 0.5f),
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = NxOrange.copy(alpha = 0.5f),
                unfocusedBorderColor    = NxBorder,
                focusedTextColor        = NxFg,
                unfocusedTextColor      = NxFg,
                cursorColor             = NxOrange,
                focusedContainerColor   = NxBg2,
                unfocusedContainerColor = NxBg2,
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
            ),
            shape = RoundedCornerShape(8.dp),
        )
    }
}
