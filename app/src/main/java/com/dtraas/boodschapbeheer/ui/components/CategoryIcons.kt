package com.dtraas.boodschapbeheer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Spa
import androidx.compose.ui.graphics.vector.ImageVector
import com.dtraas.boodschapbeheer.data.model.Category

/** Recognizable icon per category, used consistently across scan, inventory and shopping list. */
val Category.icon: ImageVector
    get() = when (this) {
        Category.GROENTE_FRUIT -> Icons.Filled.Eco
        Category.MAALTIJDEN_SALADES -> Icons.Filled.RamenDining
        Category.KAAS_VLEESWAREN -> Icons.Filled.LunchDining
        Category.VLEES_VIS -> Icons.Filled.SetMeal
        Category.ZUIVEL -> Icons.Filled.Egg
        Category.BROOD_BAKKERIJ -> Icons.Filled.BakeryDining
        Category.ONTBIJT_BELEG -> Icons.Filled.FreeBreakfast
        Category.PASTA_RIJST_WERELDKEUKEN -> Icons.Filled.Kitchen
        Category.SOEPEN_SAUZEN_CONSERVEN -> Icons.Filled.DinnerDining
        Category.SNOEP_KOEK_CHOCOLADE -> Icons.Filled.Cookie
        Category.CHIPS_ZOUTJES_NOTEN -> Icons.Filled.Fastfood
        Category.FRISDRANK_SAPPEN -> Icons.Filled.LocalCafe
        Category.BIER_WIJN -> Icons.Filled.LocalBar
        Category.DIEPVRIES -> Icons.Filled.AcUnit
        Category.BABY_KIND -> Icons.Filled.ChildCare
        Category.VERZORGING -> Icons.Filled.Spa
        Category.HUISHOUDEN -> Icons.Filled.CleaningServices
        Category.HUISDIER -> Icons.Filled.Pets
        Category.OVERIG -> Icons.Filled.Inventory2
    }
