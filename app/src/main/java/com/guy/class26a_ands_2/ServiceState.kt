package com.guy.class26a_ands_2

enum class ServiceState {
    STOPPED,
    RUNNING,
    PAUSED;

    companion object {
        fun fromString(value: String?): ServiceState {
            return entries.find { it.name == value } ?: STOPPED
        }
    }
}