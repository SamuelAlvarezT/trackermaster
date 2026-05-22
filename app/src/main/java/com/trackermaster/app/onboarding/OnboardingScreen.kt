package com.trackermaster.app.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackermaster.app.R

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf(
        stringResource(R.string.onboarding_title_1),
        stringResource(R.string.onboarding_title_2),
        stringResource(R.string.onboarding_title_3),
    )
    val bodies = listOf(
        stringResource(R.string.onboarding_body_1),
        stringResource(R.string.onboarding_body_2),
        stringResource(R.string.onboarding_body_3),
    )

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(titles[page], style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(bodies[page], style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(48.dp))
        Button({
            if (page < 2) page++ else onComplete()
        }) {
            Text(if (page < 2) stringResource(R.string.next) else stringResource(R.string.get_started))
        }
    }
}
