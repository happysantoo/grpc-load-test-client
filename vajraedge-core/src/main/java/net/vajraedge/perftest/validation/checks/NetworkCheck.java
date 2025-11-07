package net.vajraedge.perftest.validation.checks;

import net.vajraedge.perftest.constants.TaskType;
import net.vajraedge.perftest.validation.CheckResult;
import net.vajraedge.perftest.validation.ValidationCheck;
import net.vajraedge.perftest.validation.ValidationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates network connectivity to the target HTTP service.
 * 
 * <p>This check verifies DNS resolution and TCP connectivity as a pre-flight check
 * before attempting actual HTTP requests. Helps identify network-level issues early.</p>
 * 
 * <p>Only applies to HTTP-based task types. Other task types are skipped.</p>
 * 
 * @see TaskType
 * @see ValidationCheck
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
        String taskTypeStr = context.getTaskType();
        TaskType taskType = TaskType.fromString(taskTypeStr);
        
        // Only validate HTTP tasks
        if (taskType == null || !taskType.isHttpTask()) {
            log.debug("Skipping network check for non-HTTP task type: {}", taskTypeStr);
            return CheckResult.skip(getName(), "Not applicable for task type: " + taskTypeStr);
        }
        
        Object taskParam = context.getTaskParameter();
        String url = taskParam instanceof String ? (String) taskParam : null;
        if (url == null || url.isBlank()) {
            log.debug("Skipping network check: No URL provided");
            return CheckResult.skip(getName(), "No URL provided for validation");
        }
        
        log.info("Performing network check for URL: {}", url);
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
