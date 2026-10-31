package org.ruoyi.service.knowledge;

/** A fixed user-facing state, never an arbitrary infrastructure message. */
public final class KnowledgeBaseDisabledException extends RuntimeException {
    public KnowledgeBaseDisabledException() {
        super("知识库已停用");
    }

    /** Retrieval executors can wrap the denial; it must never trigger a plain-answer fallback. */
    public static void rethrowIfDisabled(Throwable error) {
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        while (error != null && visited.add(error)) {
            if (error instanceof KnowledgeBaseDisabledException disabled) {
                throw disabled;
            }
            error = error.getCause();
        }
    }
}
