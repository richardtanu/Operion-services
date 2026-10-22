package com.example.operion.module.procurement.enums;

/**
 * BE-22: who made a status change, so the app never guesses from an empty
 * name. USER: a person did it. AUTOMATIC: the server derived it from a
 * person's action (see triggeredBy / triggerReference). NOT_RECORDED: no
 * actor and not automatic, i.e. rows written before actors were recorded
 * (test data only).
 */
public enum ActorKind {

    USER,
    AUTOMATIC,
    NOT_RECORDED;

    public static ActorKind of(Object changedBy, boolean automatic) {

        if (automatic) {
            return AUTOMATIC;
        }

        return changedBy != null ? USER : NOT_RECORDED;
    }
}
