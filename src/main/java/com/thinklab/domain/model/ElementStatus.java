package com.thinklab.domain.model;

/** Lifecycle shared by {@link Node} and {@link Edge}: {@code ACTIVE <-> RETIRED}. Never a physical delete. */
public enum ElementStatus {
    ACTIVE, RETIRED
}
