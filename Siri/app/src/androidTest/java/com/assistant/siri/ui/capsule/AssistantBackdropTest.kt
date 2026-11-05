package com.assistant.siri.ui.capsule

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantBackdropTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inactiveScrimDoesNotInterceptTouches() {
        var clickCount by mutableIntStateOf(0)

        composeRule.setContent {
            AssistantBackdrop(
                active = false,
                blurIntensity = 0f,
                reduceMotion = true,
                onScrimClick = {}
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        modifier = Modifier.testTag("underlying-button"),
                        onClick = { clickCount++ }
                    ) {
                        Text("Tap")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("underlying-button").performClick()
        composeRule.runOnIdle {
            assertEquals(1, clickCount)
        }
    }
}
