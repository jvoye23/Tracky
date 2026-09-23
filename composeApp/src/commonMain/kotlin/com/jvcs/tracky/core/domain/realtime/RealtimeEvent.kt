package com.jvcs.tracky.core.domain.realtime

/**
 * What the server said down the socket.
 *
 * Every one of these means the same thing to this client — "there may be something newer than your
 * cursor, come and get it" — which is the whole contract. The backend spec is explicit that no
 * message may be the only carrier of a fact, so nothing here carries rows; the delta feed remains
 * the single way data arrives.
 */
sealed interface RealtimeEvent {

    /** The handshake landed. The server follows this with an [Invalidate] if we are behind. */
    data object Ready : RealtimeEvent

    /** Something changed. The cursor is informational — we pull from our own. */
    data class Invalidate(val cursor: Long) : RealtimeEvent

    /**
     * A timer transition. The server may inline the active timer's state alongside this so a
     * device can repaint in one hop; that is deliberately not modelled yet, because the surfaces
     * repaint from Room and writing the inline state there would make the socket a carrier of rows.
     */
    data class Timer(val cursor: Long) : RealtimeEvent

    /**
     * A type this build does not know. Treated as an [Invalidate], because under the contract above
     * every server message means at most "come and get it", and a redundant delta pull is an empty
     * page. A future backend addition must not be able to break an old client.
     */
    data class Unknown(val type: String) : RealtimeEvent
}
