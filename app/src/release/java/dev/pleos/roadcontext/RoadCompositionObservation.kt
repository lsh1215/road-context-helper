package dev.pleos.roadcontext

import androidx.compose.runtime.Composable

/** Release builds have no observer, clock reads, storage, or SideEffect. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun ObserveRoadComposition(state: RoadUiState, scope: String, branch: String) = Unit
