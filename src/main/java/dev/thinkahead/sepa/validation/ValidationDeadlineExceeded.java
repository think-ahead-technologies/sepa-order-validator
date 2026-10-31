// ABOUTME: Thrown when validating a payment file takes longer than its deadline allows.
// ABOUTME: Leads to a technical rejection (FF01) of the file rather than a retry, because it would recur.
package dev.thinkahead.sepa.validation;

class ValidationDeadlineExceeded extends RuntimeException {

    ValidationDeadlineExceeded() {
        super("Validation deadline exceeded");
    }
}
