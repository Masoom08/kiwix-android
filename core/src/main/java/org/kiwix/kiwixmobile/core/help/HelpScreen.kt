/*
 * Kiwix Android
 * Copyright (c) 2025 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.core.help

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.error.DiagnosticReportActivity
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.start
import org.kiwix.kiwixmobile.core.ui.components.KiwixAppBar
import org.kiwix.kiwixmobile.core.ui.components.NavigationIcon
import org.kiwix.kiwixmobile.core.ui.theme.KiwixTheme
import org.kiwix.kiwixmobile.core.ui.theme.MineShaftGray350
import org.kiwix.kiwixmobile.core.ui.theme.MineShaftGray600
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.HELP_SCREEN_DIVIDER_HEIGHT
import org.kiwix.kiwixmobile.core.utils.ComposeDimens.SIXTEEN_DP

@Suppress("ComposableLambdaParameterNaming")
@Composable
fun HelpScreen(
  data: List<HelpScreenItemDataClass>,
  navigationIcon: @Composable () -> Unit
) {
  val dividerColor =
    if (isSystemInDarkTheme()) {
      MineShaftGray600
    } else {
      MineShaftGray350
    }
  KiwixTheme {
    Scaffold(
      topBar = {
        KiwixAppBar(R.string.menu_help, navigationIcon)
      }
    ) { innerPadding ->
      Column(modifier = Modifier.padding(innerPadding)) {
        SendReportRow()
        HorizontalDivider(color = dividerColor, thickness = HELP_SCREEN_DIVIDER_HEIGHT)
        HelpItemList(data, dividerColor)
      }
    }
  }
}

@Composable
fun SendReportRow() {
  val context = LocalContext.current
  val isDarkTheme = isSystemInDarkTheme()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { (context as? Activity)?.start<DiagnosticReportActivity>() },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Start
  ) {
    Image(
      painter = painterResource(R.drawable.ic_feedback_orange_24dp),
      contentDescription = stringResource(R.string.send_report),
      modifier = Modifier.padding(SIXTEEN_DP)
    )

    Text(
      text = stringResource(R.string.send_report),
      color = if (isDarkTheme) Color.LightGray else Color.DarkGray,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.minimumInteractiveComponentSize()
    )
  }
}

@Composable
fun HelpItemList(data: List<HelpScreenItemDataClass>, dividerColor: Color) {
  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
  ) {
    itemsIndexed(data, key = { _, item -> item.title }) { _, item ->
      HelpScreenItem(data = item)
      HorizontalDivider(color = dividerColor, thickness = HELP_SCREEN_DIVIDER_HEIGHT)
    }
  }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
// @Preview()
@Composable
fun PreviewScreen() {
  HelpScreen(
    data = transformToHelpScreenData(LocalContext.current, rawTitleDescriptionMap())
  ) { NavigationIcon(onClick = { }) }
}

fun rawTitleDescriptionMap(): List<Pair<Int, Any>> =
  listOf(
    R.string.help_2 to R.array.description_help_2,
    R.string.help_5 to R.array.description_help_5,
    R.string.how_to_update_content to R.array.update_content_description
  )

// fun transformToHelpScreenData(
//   context: Context,
//   rawTitleDescriptionMap: List<Pair<Int, Any>>
// ): List<HelpScreenItemDataClass> {
//   return rawTitleDescriptionMap.map { (titleResId, description) ->
//     val title = context.getString(titleResId)
//     val descriptionValue = when (description) {
//       is String -> description
//       is Int -> context.resources.getStringArray(description).joinToString(separator = "\n")
//       else -> {
//         throw IllegalArgumentException("Invalid description resource type for title: $titleResId")
//       }
//     }
//     HelpScreenItemDataClass(title, descriptionValue)
//   }
// }
