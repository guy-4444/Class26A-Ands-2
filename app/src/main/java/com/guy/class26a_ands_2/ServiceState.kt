package com.guy.class26a_ands_2

/**
 * Possible states of the foreground service.
 */
enum class ServiceState {
    STOPPED,   // Service is not running
    RUNNING,   // Service is actively working
    PAUSED;    // Service is alive but work is paused

    companion object {
        fun fromString(value: String?): ServiceState {
            return entries.find { it.name == value } ?: STOPPED
        }
    }
}
