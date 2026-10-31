// ABOUTME: The database writes that record a step's result, run in the transaction that completes the work item.
// ABOUTME: Runs only while the worker still holds the item's lease, so a step's result is recorded exactly once.
package dev.thinkahead.sepa.processing;

@FunctionalInterface
public interface Completion {

    void record();
}
