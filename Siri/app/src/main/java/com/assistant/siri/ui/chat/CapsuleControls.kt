package com.assistant.siri.ui.chat

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.assistant.siri.ui.theme.CapsuleColors

/** Botón de icono para usar dentro de la cápsula (siempre sobre negro). */
@Composable
internal fun CapsuleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = CapsuleColors.TextPrimary,
            disabledContentColor = CapsuleColors.TextSecondary.copy(alpha = 0.4f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Botón de texto para usar dentro de la cápsula. */
@Composable
internal fun CapsuleTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (emphasized) CapsuleColors.Accent else CapsuleColors.TextPrimary
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
