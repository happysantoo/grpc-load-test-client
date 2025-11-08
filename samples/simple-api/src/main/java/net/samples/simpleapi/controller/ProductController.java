package net.vajraedge.samples.simpleapi.controller;

import net.vajraedge.samples.simpleapi.model.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Product controller for VajraEdge load testing demonstration.
 * Returns substantial JSON output after a configurable delay.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /**
     * Get all products endpoint.
     * Simulates database query with 10ms delay and returns substantial JSON data.
     *
     * @return List of products
     * @throws InterruptedException if sleep is interrupted
     */
    @GetMapping
    public List<Product> getProducts() throws InterruptedException {
        // Simulate database query delay
        Thread.sleep(10);

        // Generate substantial JSON response
        List<Product> products = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        products.add(new Product(
            1L,
            "Premium Wireless Headphones",
            "High-quality over-ear headphones with active noise cancellation, 30-hour battery life, and premium sound quality. Perfect for music lovers and professionals.",
            299.99,
            "Electronics",
            List.of("audio", "wireless", "premium", "noise-cancelling"),
            true,
            150,
            new Product.Rating(4.7, 1243),
            now.minusDays(90),
            now.minusDays(5)
        ));

        products.add(new Product(
            2L,
            "Ergonomic Office Chair",
            "Premium ergonomic office chair with lumbar support, adjustable armrests, and breathable mesh back. Designed for all-day comfort and productivity.",
            449.99,
            "Furniture",
            List.of("office", "ergonomic", "comfort", "adjustable"),
            true,
            75,
            new Product.Rating(4.5, 892),
            now.minusDays(120),
            now.minusDays(10)
        ));

        products.add(new Product(
            3L,
            "Smart Watch Pro",
            "Advanced smartwatch with health tracking, GPS, heart rate monitor, sleep tracking, and 5-day battery life. Compatible with iOS and Android.",
            399.99,
            "Electronics",
            List.of("wearable", "fitness", "smart", "health"),
            true,
            230,
            new Product.Rating(4.6, 2156),
            now.minusDays(60),
            now.minusDays(2)
        ));

        products.add(new Product(
            4L,
            "Mechanical Keyboard RGB",
            "Premium mechanical keyboard with customizable RGB lighting, hot-swappable switches, and programmable keys. Perfect for gamers and developers.",
            179.99,
            "Electronics",
            List.of("gaming", "keyboard", "rgb", "mechanical"),
            true,
            320,
            new Product.Rating(4.8, 3421),
            now.minusDays(45),
            now.minusDays(1)
        ));

        products.add(new Product(
            5L,
            "4K Webcam Ultra HD",
            "Professional 4K webcam with auto-focus, built-in microphone, and low-light correction. Ideal for streaming, video conferencing, and content creation.",
            149.99,
            "Electronics",
            List.of("camera", "4k", "streaming", "professional"),
            true,
            180,
            new Product.Rating(4.4, 756),
            now.minusDays(30),
            now.minusDays(3)
        ));

        products.add(new Product(
            6L,
            "Standing Desk Converter",
            "Height-adjustable standing desk converter with dual-tier design. Transform any desk into a sit-stand workstation for better health and productivity.",
            249.99,
            "Furniture",
            List.of("office", "standing-desk", "ergonomic", "health"),
            true,
            95,
            new Product.Rating(4.3, 534),
            now.minusDays(75),
            now.minusDays(8)
        ));

        products.add(new Product(
            7L,
            "USB-C Docking Station",
            "Universal USB-C docking station with dual 4K display support, multiple USB ports, Ethernet, and 100W power delivery. One cable to connect everything.",
            199.99,
            "Electronics",
            List.of("usb-c", "docking", "connectivity", "multi-display"),
            true,
            140,
            new Product.Rating(4.5, 982),
            now.minusDays(55),
            now.minusDays(4)
        ));

        products.add(new Product(
            8L,
            "Portable SSD 2TB",
            "Ultra-fast portable SSD with 2TB storage, USB 3.2 Gen 2 interface, and rugged design. Perfect for professionals who need speed and reliability.",
            279.99,
            "Electronics",
            List.of("storage", "ssd", "portable", "fast"),
            true,
            210,
            new Product.Rating(4.7, 1876),
            now.minusDays(40),
            now.minusDays(6)
        ));

        products.add(new Product(
            9L,
            "Wireless Mouse Precision",
            "Ergonomic wireless mouse with precision sensor, customizable buttons, and 70-day battery life. Designed for productivity and comfort.",
            79.99,
            "Electronics",
            List.of("mouse", "wireless", "ergonomic", "precision"),
            true,
            450,
            new Product.Rating(4.6, 2341),
            now.minusDays(100),
            now.minusDays(7)
        ));

        products.add(new Product(
            10L,
            "Monitor Arm Dual",
            "Premium dual monitor arm with full articulation, cable management, and VESA mount compatibility. Support monitors up to 32 inches each.",
            159.99,
            "Furniture",
            List.of("monitor-arm", "dual", "ergonomic", "desk"),
            true,
            125,
            new Product.Rating(4.4, 678),
            now.minusDays(85),
            now.minusDays(9)
        ));

        return products;
    }
}
