package com.ordia.app.ui.screens
import com.ordia.app.ui.components.*

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ordia.app.R
import com.ordia.app.ui.components.ScreenHeader
import com.ordia.app.ui.components.SectionHeader

/** Estado honesto del análisis local de Ordía, sin descargas inoperantes. */
@Composable
fun IntelligenceScreen(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            stringResource(R.string.intel_header_eyebrow),
            stringResource(R.string.intel_header_title)
        )

        SectionHeader(stringResource(R.string.intel_section_current_mode))
        OrdiaCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.intel_rules_active_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.intel_rules_active_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.intel_rules_scope_title))
        OrdiaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                stringResource(R.string.intel_rules_scope_desc),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.intel_generative_title))
        OrdiaCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.intel_generative_unavailable),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.intel_generative_reason),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader(stringResource(R.string.intel_section_privacy))
        OrdiaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text(
                stringResource(R.string.intel_privacy_verified_desc),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}
