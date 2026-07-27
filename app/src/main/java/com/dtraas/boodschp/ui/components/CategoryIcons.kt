package com.dtraas.boodschp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Spa
import androidx.compose.ui.graphics.vector.ImageVector
import com.dtraas.boodschp.data.model.Category

/** Recognizable icon per category, used consistently across scan, inventory and shopping list. */
val Category.icon: ImageVector
    get() = when (this) {
        Category.ZUIVEL -> Icons.Filled.Egg
        Category.GROENTE_FRUIT -> Icons.Filled.Eco
        Category.VLEES_VIS -> Icons.Filled.SetMeal
        Category.BROOD_BAKKERIJ -> Icons.Filled.BakeryDining
        Category.VOORRAADKAST -> Icons.Filled.Kitchen
        Category.DIEPVRIES -> Icons.Filled.AcUnit
        Category.DRANKEN -> Icons.Filled.LocalBar
        Category.SNOEP_SNACKS -> Icons.Filled.Cookie
        Category.HUISHOUDEN -> Icons.Filled.CleaningServices
        Category.VERZORGING -> Icons.Filled.Spa
        Category.OVERIG -> Icons.Filled.Inventory2
    }
