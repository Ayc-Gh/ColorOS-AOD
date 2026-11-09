package com.op.aod.enhance.data

enum class AodSyncState { SYNCED, PENDING, FAILED }

enum class AodSyncIssue { LOCAL_SAVE, LOCAL_BACKUP, REMOTE, CONFIRMATION }

/** Configuration delivery status; this does not claim that a host Hook is active. */
data class AodSyncStatus(
    val state: AodSyncState = AodSyncState.PENDING,
    val issue: AodSyncIssue? = null,
    val hasPendingChanges: Boolean = false,
)
