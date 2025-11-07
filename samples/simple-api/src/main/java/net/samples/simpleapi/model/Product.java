package net.vajraedge.samples.simpleapi.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Product data model for demo API.
 */
public record Product(
    Long id,
    String name,
    String description,
    Double price,
    String category,
    List<String> tags,
    boolean inStock,
    int quantity,
    Rating rating,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public record Rating(
        double average,
        int totalReviews
    ) {}
}
