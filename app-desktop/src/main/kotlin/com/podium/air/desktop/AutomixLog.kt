// SPDX-License-Identifier: GPL-3.0-only
package com.podium.air.desktop

/** Retains useful upstream fallback diagnostics without Android's Log dependency. */
object AutomixLog {
    fun d(tag: String, message: String) { if (java.lang.Boolean.getBoolean("podium.automix.debug")) System.err.println("$tag: $message") }
    fun w(tag: String, message: String, error: Throwable) { System.err.println("$tag: $message (${error.javaClass.simpleName}: ${error.message})") }
}
