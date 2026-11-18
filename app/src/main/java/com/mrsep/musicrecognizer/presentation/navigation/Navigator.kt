package com.mrsep.musicrecognizer.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

enum class TrackPopStyle { NoAction, Dismissed, Deleted }

class Navigator(val state: NavigationState) {
    var trackPopStyle: TrackPopStyle by mutableStateOf(TrackPopStyle.NoAction)

    fun navigate(key: NavKey) {
        trackPopStyle = TrackPopStyle.NoAction
        if (key in state.backStacks) {
            if (key != state.topLevelRoute) {
                state.topLevelRoute = key
            }
        } else {
            state.backStacks.getValue(state.topLevelRoute).add(key)
        }
    }

    fun goBack() {
        trackPopStyle = TrackPopStyle.NoAction
        popLast()
    }

    fun popTrack(style: TrackPopStyle) {
        trackPopStyle = style
        popLast()
    }

    fun openTopLevel(tab: NavKey) {
        state.backStacks.values.forEach { stack -> popToRoot(stack) }
        state.topLevelRoute = tab
    }

    fun applyDeepLink(link: ParsedDeepLink) {
        trackPopStyle = TrackPopStyle.NoAction
        state.backStacks.forEach { (route, stack) ->
            popToRoot(stack)
            if (route == link.owner) {
                link.stack.drop(1).forEach { key -> stack.add(key) }
            }
        }
        state.topLevelRoute = link.owner
    }

    private fun popLast() {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        val poppedChild = stack.size > 1
        val switchToStart = !poppedChild && state.topLevelRoute != state.startRoute
        if (poppedChild) {
            stack.removeAt(stack.lastIndex)
        } else if (switchToStart) {
            state.topLevelRoute = state.startRoute
        }
    }

    private fun popToRoot(stack: NavBackStack<NavKey>) {
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }
}
