package com.vajraedge.perftest.validation.checks;

import com.vajraedge.perftest.validation.CheckResult;
import com.vajraedge.perftest.validation.ValidationCheck;
import com.vajraedge.perftest.validation.ValidationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates network connectivity (DNS, TCP connections).
 */
@Component
public class NetworkCheck implements ValidationCheck {
    
    private static final Logger log = LoggerFactory.getLogger(NetworkCheck.class);
    private static final int TCP_TIMEOUT_MS = 5000;
    
    @Override
    public String getName() {
        return "Network Check";
    }
    
    @Override
    public CheckResult execute(ValidationContext context) {
        String taskType = context.getTaskType();
        
        // Only validate HTTP tasks
        if (!"HTTP_GET".equals(taskType) && !"HTTP_POST".equals(taskType) && !"HTTP".equals(taskType)) {
            return CheckResult.skip(getName(), "Not applicable for task type: " + taskType);
        }
        
        Object taskParam = context.getTaskParameter();
        String url = taskParam instanceof String ? (String) taskParam : null;
        if (url == null || url.isBlank()) {
            return CheckResult.skip(getName(), "No URL provided for validation");
        }
        
        return validateNetworkConnectivity(url);
    }
    
    private CheckResult validateNetworkConnectivity(String urlString) {
        List<String> details = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        try {
            URI uri = URI.create(urlString);
            String host = uri.getHost();
            int port = uri.getPort();
            
            // Use default ports if not specified
            if (port == -1) {
                port = "https".equals(uri.getScheme()) ? 443 : 80;
            }
            
            details.add(String.format("Target: %s:%d", host, port));
            
            // Check DNS resolution
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                details.add(String.format("✓ DNS resolved to %d address(es)", addresses.length));
                for (InetAddress address : addresses) {
                    details.add(String.format("  - %s", address.getHostAddress()));
                }
            } catch (UnknownHostException e) {
                errors.add(String.format("❌ DNS resolution failed for host: %s", host));
                errors.add("Suggestions:");
                errors.add("  - Verify the hostname is correct");
                errors.add("  - Check DNS server configuration");
                errors.add("  - Try using an IP address instead");
                return CheckResult.fail(getName(), "Cannot resolve hostname", errors);
            }
            
            // Check TCP connectivity
            try (Socket socket = new Socket()) {
                SocketAddress socketAddress = new InetSocketAddress(host, port);
                socket.connect(socketAddress, TCP_TIMEOUT_MS);
                details.add(String.format("✓ TCP connection successful to %s:%d", host, port));
            } catch (IOException e) {
                errors.add(String.format("❌ TCP connection failed to %s:%d", host, port));
                errors.add("Error: " + e.getMessage());
                errors.add("Suggestions:");
                errors.add("  - Verify the service is running");
                errors.add("  - Check firewall rules");
                errors.add("  - Verify the port number is correct");
                return CheckResult.fail(getName(), "Cannot establish TCP connection", errors);
            }
            
            // Check for proxy configuration
            String httpProxy = System.getProperty("http.proxyHost");
            String httpsProxy = System.getProperty("https.proxyHost");
            
            if (httpProxy != null || httpsProxy != null) {
                warnings.add("Proxy configuration detected:");
                if (httpProxy != null) {
                    warnings.add(String.format("  HTTP proxy: %s:%s", 
                        httpProxy, System.getProperty("http.proxyPort", "80")));
                }
                if (httpsProxy != null) {
                    warnings.add(String.format("  HTTPS proxy: %s:%s", 
                        httpsProxy, System.getProperty("https.proxyPort", "443")));
                }
                warnings.add("Ensure proxy can reach target service");
            }
            
            // Success
            if (!warnings.isEmpty()) {
                return CheckResult.warn(getName(), "Network connectivity OK with warnings", warnings);
            } else {
                return CheckResult.pass(getName(), "Network connectivity validated", details);
            }
            
        } catch (IllegalArgumentException e) {
            errors.add("❌ Invalid URL format: " + e.getMessage());
            return CheckResult.fail(getName(), "Invalid URL", errors);
        } catch (Exception e) {
            log.error("Unexpected error during network validation", e);
            errors.add("❌ Unexpected error: " + e.getMessage());
            return CheckResult.fail(getName(), "Network validation failed", errors);
        }
    }
}
