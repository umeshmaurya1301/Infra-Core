package org.infra.kafka.it;

/**
 * Plain JSON payload used by the infra-kafka integration tests.
 *
 * <p>Deliberately a classic mutable POJO (no-arg constructor + getters/setters) so Jackson
 * deserializes it without relying on {@code -parameters} or the record module — keeping the
 * integration tests focused on the library's Kafka wiring rather than serialization edge cases.
 */
public class OrderEvent {

    private String id;
    private String status;

    public OrderEvent() {
    }

    public OrderEvent(String id, String status) {
        this.id = id;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "OrderEvent{id='" + id + "', status='" + status + "'}";
    }
}
