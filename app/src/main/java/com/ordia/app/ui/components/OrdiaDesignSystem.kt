package com.ordia.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OrdiaButton(modifier: Modifier = Modifier, onClick: () -> Unit = {}, text: String = "Button") {
    Button(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

@Composable
fun OrdiaInput(modifier: Modifier = Modifier, value: String = "", onValueChange: (String) -> Unit = {}, supportingText: @Composable (() -> Unit)? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        supportingText = supportingText
    )
}

@Composable
fun OrdiaSheet(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaDialog(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaCard(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaTask(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaNote(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaAction(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaGuardian(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaTimeline(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaCommand(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}

@Composable
fun OrdiaKeyboardBar(modifier: Modifier = Modifier) {
    Box(modifier = modifier)
}
