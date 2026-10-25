package dev.coala.execution;

import dev.coala.core.CoalaAction;

/** Optional action-execution wrapper; core planning and action contracts do not depend on decorators. */
@FunctionalInterface
public interface ActionExecutionDecorator {
    CoalaAction decorate(CoalaAction action);
}
