package pl.dakil.music.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import pl.dakil.music.ui.theme.tabular

/**
 * Text whose digits roll vertically when they change, odometer-style: up when a digit grows, down
 * when it shrinks. Meant for live values that update at most about once a second; characters are
 * keyed from the end so units and the last digits stay put while the number grows a digit.
 */
@Composable
fun RollingNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val tabularStyle = style.tabular()
    Row(modifier) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                if (!char.isDigit()) {
                    Text(char.toString(), style = tabularStyle, color = color, maxLines = 1)
                } else {
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(spatial) { if (up) it else -it } + fadeIn(effects)) togetherWith
                                (slideOutVertically(spatial) { if (up) -it else it } + fadeOut(effects)) using
                                SizeTransform(clip = true)
                        },
                        modifier = Modifier.clipToBounds(),
                        label = "rollingDigit",
                    ) { digit ->
                        Text(digit.toString(), style = tabularStyle, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}
