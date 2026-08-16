package com.ordia.app.ui

import androidx.annotation.StringRes
import com.ordia.app.R
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.domain.GuardianEngine

/**
 * Mapeo UI → R.string para el vocabulario del guardián (ORD-029 i18n).
 *
 * El dominio ([GuardianSpecies], [GuardianEngine]) conserva un modelo puro sin texto de
 * producto; la capa UI resuelve etiquetas y descripciones desde recursos para permitir
 * localización sin tocar reglas ni persistencia.
 */
@StringRes
fun GuardianSpecies.labelRes(): Int = when (this) {
    GuardianSpecies.LUMI -> R.string.guardian_species_lumi
    GuardianSpecies.MOSS -> R.string.guardian_species_moss
    GuardianSpecies.ORBIT -> R.string.guardian_species_orbit
    GuardianSpecies.EMBER -> R.string.guardian_species_ember
    GuardianSpecies.TIDE -> R.string.guardian_species_tide
    GuardianSpecies.NOVA -> R.string.guardian_species_nova
}

@StringRes
fun GuardianSpecies.descriptionRes(): Int = when (this) {
    GuardianSpecies.LUMI -> R.string.guardian_species_lumi_desc
    GuardianSpecies.MOSS -> R.string.guardian_species_moss_desc
    GuardianSpecies.ORBIT -> R.string.guardian_species_orbit_desc
    GuardianSpecies.EMBER -> R.string.guardian_species_ember_desc
    GuardianSpecies.TIDE -> R.string.guardian_species_tide_desc
    GuardianSpecies.NOVA -> R.string.guardian_species_nova_desc
}

@StringRes
fun GuardianEngine.Stage.labelRes(): Int = when (this) {
    GuardianEngine.Stage.SPARK -> R.string.guardian_stage_spark
    GuardianEngine.Stage.HATCHLING -> R.string.guardian_stage_hatchling
    GuardianEngine.Stage.YOUNG -> R.string.guardian_stage_young
    GuardianEngine.Stage.COMPANION -> R.string.guardian_stage_companion
    GuardianEngine.Stage.ASCENDED -> R.string.guardian_stage_ascended
}

@StringRes
fun GuardianEngine.Mood.labelRes(): Int = when (this) {
    GuardianEngine.Mood.CALM -> R.string.component_guardian_mood_calm
    GuardianEngine.Mood.HAPPY -> R.string.component_guardian_mood_happy
    GuardianEngine.Mood.FOCUSED -> R.string.component_guardian_mood_focused
    GuardianEngine.Mood.SLEEPY -> R.string.component_guardian_mood_sleepy
    GuardianEngine.Mood.CURIOUS -> R.string.component_guardian_mood_curious
    GuardianEngine.Mood.PROUD -> R.string.component_guardian_mood_proud
    GuardianEngine.Mood.PLAYFUL -> R.string.component_guardian_mood_playful
    GuardianEngine.Mood.CONCERNED -> R.string.component_guardian_mood_concerned
}

@StringRes
fun GuardianEngine.Archetype.labelRes(): Int = when (this) {
    GuardianEngine.Archetype.BALANCED -> R.string.guardian_archetype_balanced
    GuardianEngine.Archetype.ACHIEVER -> R.string.guardian_archetype_achiever
    GuardianEngine.Archetype.FOCUSED -> R.string.guardian_archetype_focused
    GuardianEngine.Archetype.CONSISTENT -> R.string.guardian_archetype_consistent
    GuardianEngine.Archetype.CREATIVE -> R.string.guardian_archetype_creative
}

@StringRes
fun GuardianEngine.Archetype.descriptionRes(): Int = when (this) {
    GuardianEngine.Archetype.BALANCED -> R.string.guardian_archetype_balanced_desc
    GuardianEngine.Archetype.ACHIEVER -> R.string.guardian_archetype_achiever_desc
    GuardianEngine.Archetype.FOCUSED -> R.string.guardian_archetype_focused_desc
    GuardianEngine.Archetype.CONSISTENT -> R.string.guardian_archetype_consistent_desc
    GuardianEngine.Archetype.CREATIVE -> R.string.guardian_archetype_creative_desc
}

@StringRes
fun GuardianEngine.Interaction.labelRes(): Int = when (this) {
    GuardianEngine.Interaction.PET -> R.string.guardian_interaction_pet
    GuardianEngine.Interaction.PLAY -> R.string.guardian_interaction_play
    GuardianEngine.Interaction.FEED -> R.string.guardian_interaction_feed
    GuardianEngine.Interaction.TALK -> R.string.guardian_interaction_talk
    GuardianEngine.Interaction.REST -> R.string.guardian_interaction_rest
}
