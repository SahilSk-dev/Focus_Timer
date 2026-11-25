package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubjectEntity
import com.example.data.model.WorkTypeEntity
import com.example.ui.theme.BgDark
import com.example.ui.theme.DangerRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldLight
import com.example.ui.theme.LineBorder
import com.example.ui.theme.PanelDark
import com.example.ui.theme.PanelElevated
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary

@Composable
fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, isCore: Boolean, isNonStudy: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isCore by remember { mutableStateOf(false) }
    var isNonStudy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Subject", color = GoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name", color = TextDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                SubjectOptionRow(
                    title = "Pin / Core Subject",
                    subtitle = "Highlight on main subject bar",
                    checked = isCore,
                    onToggle = { isCore = it }
                )
                SubjectOptionRow(
                    title = "Non-Study Subject",
                    subtitle = "Personal / break time (excluded from study goals)",
                    checked = isNonStudy,
                    onToggle = { isNonStudy = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        onAdd(trimmed, isCore, isNonStudy)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextDim)
            }
        },
        containerColor = PanelDark
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSubjectDialog(
    subject: SubjectEntity,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (SubjectEntity) -> Unit,
    onDelete: () -> Unit
) {
    var editName by remember(subject.id) { mutableStateOf(subject.name) }
    var editIsCore by remember(subject.id) { mutableStateOf(subject.isCore) }
    var editIsNonStudy by remember(subject.id) { mutableStateOf(subject.isNonStudy) }
    var subList by remember(subject.id) { mutableStateOf(subject.subSubjects) }
    var newSubInput by remember(subject.id) { mutableStateOf("") }
    var confirmDelete by remember(subject.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Subject", color = GoldBright) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Subject Name", color = TextDim) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    text = "Sub-Subjects (${subList.size})",
                    color = GoldLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tap × to remove a sub-subject, or tap a chip to rename it.",
                    color = TextDim,
                    fontSize = 11.sp
                )

                if (subList.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subList.forEach { sub ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PanelElevated,
                                border = BorderStroke(1.dp, LineBorder),
                                modifier = Modifier.clickable {
                                    // Tap chip to pull into input for quick rename
                                    newSubInput = sub
                                    subList = subList.filter { it != sub }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(sub, color = TextPrimary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { subList = subList.filter { it != sub } },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $sub",
                                            tint = DangerRed,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubInput,
                        onValueChange = { newSubInput = it },
                        label = { Text("Add / Edit Sub-Subject", color = TextDim, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = LineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = newSubInput.trim()
                            if (trimmed.isNotBlank() && subList.none { it.equals(trimmed, ignoreCase = true) }) {
                                subList = subList + trimmed
                                newSubInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
                    ) {
                        Text("+")
                    }
                }

                SubjectOptionRow(
                    title = "Pin / Core Subject",
                    subtitle = "Keep highlighted in the subject list",
                    checked = editIsCore,
                    onToggle = { editIsCore = it }
                )

                SubjectOptionRow(
                    title = "Non-Study Subject",
                    subtitle = "Excluded from study goals & analytics",
                    checked = editIsNonStudy,
                    onToggle = { editIsNonStudy = it }
                )

                if (canDelete) {
                    OutlinedButton(
                        onClick = {
                            if (confirmDelete) {
                                onDelete()
                            } else {
                                confirmDelete = true
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (confirmDelete) TextPrimary else DangerRed,
                            containerColor = if (confirmDelete) DangerRed else PanelElevated
                        ),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (confirmDelete) "Tap again to confirm delete" else "Delete Subject",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = editName.trim()
                    if (trimmedName.isNotBlank()) {
                        val pendingSub = newSubInput.trim()
                        val finalSubs = if (
                            pendingSub.isNotBlank() &&
                            subList.none { it.equals(pendingSub, ignoreCase = true) }
                        ) {
                            subList + pendingSub
                        } else {
                            subList
                        }
                        onSave(
                            subject.copy(
                                name = trimmedName,
                                isCore = editIsCore,
                                isNonStudy = editIsNonStudy,
                                subSubjects = finalSubs
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextDim)
            }
        },
        containerColor = PanelDark
    )
}

@Composable
fun AddWorkTypeDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Work Type", color = GoldBright) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Work Type (e.g. Solving Papers)", color = TextDim) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldAccent,
                    unfocusedBorderColor = LineBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) {
                        onAdd(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextDim)
            }
        },
        containerColor = PanelDark
    )
}

@Composable
fun EditWorkTypeDialog(
    workType: WorkTypeEntity,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var editName by remember(workType.id) { mutableStateOf(workType.name) }
    var confirmDelete by remember(workType.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Work Type", color = GoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Work Type Name", color = TextDim) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = LineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (canDelete) {
                    OutlinedButton(
                        onClick = {
                            if (confirmDelete) {
                                onDelete()
                            } else {
                                confirmDelete = true
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (confirmDelete) TextPrimary else DangerRed,
                            containerColor = if (confirmDelete) DangerRed else PanelElevated
                        ),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (confirmDelete) "Tap again to confirm delete" else "Delete Work Type",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        text = "At least one work type must remain.",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = editName.trim()
                    if (trimmed.isNotBlank()) {
                        onSave(trimmed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = BgDark)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextDim)
            }
        },
        containerColor = PanelDark
    )
}

@Composable
private fun SubjectOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PanelElevated,
        border = BorderStroke(1.dp, LineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TextDim, fontSize = 11.sp)
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(checkedColor = GoldAccent)
            )
        }
    }
}
