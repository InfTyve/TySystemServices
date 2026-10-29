package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 通用口令输入对话框。
 *
 * 只负责「输入 + 显示错误 + 回调」，具体校验逻辑由调用方通过 [onConfirm] 决定：
 * 返回 true 表示通过（对话框自动关闭），返回 false 则停留在原地并展示 [errorText]。
 *
 * @param message 标题下方的说明文字，可空
 */
@Composable
fun PasswordPromptDialog(
    title: String,
    label: String,
    errorText: String,
    confirmText: String,
    dismissText: String,
    showPasswordText: String,
    hidePasswordText: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    var value by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    var hasError by rememberSaveable { mutableStateOf(false) }

    fun attempt() {
        if (onConfirm(value)) onDismiss() else hasError = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                if (!message.isNullOrEmpty()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        hasError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = label) },
                    singleLine = true,
                    isError = hasError,
                    supportingText = if (hasError) {
                        { Text(text = errorText) }
                    } else {
                        null
                    },
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (visible) {
                                    hidePasswordText
                                } else {
                                    showPasswordText
                                },
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { attempt() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { attempt() }) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        },
    )
}
