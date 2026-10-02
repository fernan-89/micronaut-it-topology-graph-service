package com.thinklab.domain.model;

/**
 * Direction of a blast-radius traversal. {@code DOWNSTREAM}: what the node depends on (edges followed
 * source -> target). {@code UPSTREAM}: what depends on the node (edges followed target -> source).
 */
public enum TraversalDirection {
    DOWNSTREAM, UPSTREAM, BOTH
}
