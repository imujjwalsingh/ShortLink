package com.shortlink.urlservice.event;

/**
 * Hands a click event off for asynchronous delivery. Implementations must
 * return immediately and must never throw: this is called on the redirect
 * hot path, which must not block on, or fail because of, analytics.
 */
public interface ClickEventPublisher {

    void publish(LinkClickedEvent event);
}
