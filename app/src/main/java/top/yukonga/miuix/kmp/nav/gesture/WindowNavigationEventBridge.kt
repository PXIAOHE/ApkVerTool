// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner

/**
 * Forwards this platform window's back events to an explicitly inherited navigation dispatcher.
 *
 * This is an interoperability fallback for separate-window components that inherit a provided
 * `LocalNavigationEventDispatcherOwner` but register their own back handler before caller content
 * can rebind the dispatcher to the new window.
 */
@Composable
fun WindowNavigationEventBridge() {
    val inheritedDispatcher =
        LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    val view = LocalView.current
    val windowDispatcher = remember(view) {
        view.findViewTreeNavigationEventDispatcherOwner()?.navigationEventDispatcher
    } ?: return
    if (windowDispatcher === inheritedDispatcher) return

    val forwardingInput = remember(inheritedDispatcher) { DirectNavigationEventInput() }
    val forwardingHandler = remember(windowDispatcher, forwardingInput) {
        ForwardingNavigationEventHandler(forwardingInput)
    }

    DisposableEffect(inheritedDispatcher, forwardingInput) {
        inheritedDispatcher.addInput(forwardingInput)
        onDispose {
            try {
                inheritedDispatcher.removeInput(forwardingInput)
            } catch (_: IllegalStateException) {
                // The owning nav hierarchy already disposed this descendant dispatcher.
            }
        }
    }
    DisposableEffect(windowDispatcher, forwardingHandler) {
        windowDispatcher.addHandler(forwardingHandler)
        onDispose { forwardingHandler.remove() }
    }
}

internal class ForwardingNavigationEventHandler(
    private val input: DirectNavigationEventInput,
) : NavigationEventHandler<NavigationEventInfo>(
    initialInfo = NavigationEventInfo.None,
    isBackEnabled = true,
    isForwardEnabled = false,
) {
    override fun onBackStarted(event: NavigationEvent) {
        input.backStarted(event)
    }

    override fun onBackProgressed(event: NavigationEvent) {
        input.backProgressed(event)
    }

    override fun onBackCancelled() {
        input.backCancelled()
    }

    override fun onBackCompleted() {
        input.backCompleted()
    }
}
