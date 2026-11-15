package com.cricketsim.ui.career

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cricketsim.ui.custom.OptionButton
import com.cricketsim.ui.custom.PageColumn
import com.cricketsim.ui.custom.PageHeading

/** A school leaver chooses one of the three colleges of his state (My Player, part twenty-six). It comes by itself when he is back in the hub. */
@Composable
fun CollegeChoiceScreen(colleges: List<String>, onChoose: (String) -> Unit) {
    PageColumn {
        PageHeading("Choose your college")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You have finished school. Choose the college you will play for. You join your district club when you finish college.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        colleges.forEach { college ->
            OptionButton(college, "A college in your state.", true, false) { onChoose(college) }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
