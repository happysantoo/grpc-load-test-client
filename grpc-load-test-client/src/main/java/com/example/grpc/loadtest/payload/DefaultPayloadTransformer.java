package com.example.grpc.loadtest.payload;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Default implementation of PayloadTransformer with built-in transformation functions.
 */
public class DefaultPayloadTransformer implements PayloadTransformer {
    
    private final SecureRandom random = new SecureRandom();
    private final Map<TransformationType, Function<TransformationRule, Function<Object, Object>>> transformerFactory;
    
    public DefaultPayloadTransformer() {
        this.transformerFactory = createTransformerFactory();
    }
    
    @Override
    public Map<String, Object> transform(Map<String, Object> originalPayload, 
                                       Map<String, TransformationRule> transformationRules) {
        Map<String, Object> transformedPayload = new HashMap<>(originalPayload);
        
        for (Map.Entry<String, TransformationRule> entry : transformationRules.entrySet()) {
            String fieldName = entry.getKey();
            TransformationRule rule = entry.getValue();
            
            Object originalValue = transformedPayload.get(fieldName);
            Function<Object, Object> transformer = getTransformer(rule);
            Object transformedValue = transformer.apply(originalValue);
            
            transformedPayload.put(fieldName, transformedValue);
        }
        
        return transformedPayload;
    }
    
    private Function<Object, Object> getTransformer(TransformationRule rule) {
        if (rule.getTransformer() != null) {
            return rule.getTransformer();
        }
        
        return transformerFactory.get(rule.getType()).apply(rule);
    }
    
    private Map<TransformationType, Function<TransformationRule, Function<Object, Object>>> createTransformerFactory() {
        Map<TransformationType, Function<TransformationRule, Function<Object, Object>>> factory = new HashMap<>();
        
        factory.put(TransformationType.RANDOM_STRING, this::createRandomStringTransformer);
        factory.put(TransformationType.RANDOM_NUMBER, this::createRandomNumberTransformer);
        factory.put(TransformationType.INCREMENT, this::createIncrementTransformer);
        factory.put(TransformationType.PREFIX, this::createPrefixTransformer);
        factory.put(TransformationType.SUFFIX, this::createSuffixTransformer);
        factory.put(TransformationType.REPLACE, this::createReplaceTransformer);
        factory.put(TransformationType.TEMPLATE, this::createTemplateTransformer);
        
        return factory;
    }
    
    private Function<Object, Object> createRandomStringTransformer(TransformationRule rule) {
        int length = (Integer) rule.getParameters().getOrDefault("length", 10);
        String charset = (String) rule.getParameters().getOrDefault("charset", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        
        return original -> generateRandomString(length, charset);
    }
    
    private Function<Object, Object> createRandomNumberTransformer(TransformationRule rule) {
        Number min = (Number) rule.getParameters().getOrDefault("min", 1);
        Number max = (Number) rule.getParameters().getOrDefault("max", 1000);
        String type = (String) rule.getParameters().getOrDefault("type", "int");
        
        return original -> {
            switch (type.toLowerCase()) {
                case "long":
                    return ThreadLocalRandom.current().nextLong(min.longValue(), max.longValue());
                case "double":
                    return ThreadLocalRandom.current().nextDouble(min.doubleValue(), max.doubleValue());
                default:
                    return ThreadLocalRandom.current().nextInt(min.intValue(), max.intValue());
            }
        };
    }
    
    private Function<Object, Object> createIncrementTransformer(TransformationRule rule) {
        Number step = (Number) rule.getParameters().getOrDefault("step", 1);
        
        return original -> {
            if (original instanceof Number) {
                if (original instanceof Long) {
                    return ((Long) original) + step.longValue();
                } else if (original instanceof Double) {
                    return ((Double) original) + step.doubleValue();
                } else {
                    return ((Integer) original) + step.intValue();
                }
            }
            return original;
        };
    }
    
    private Function<Object, Object> createPrefixTransformer(TransformationRule rule) {
        String prefix = (String) rule.getParameters().get("prefix");
        
        return original -> prefix + String.valueOf(original);
    }
    
    private Function<Object, Object> createSuffixTransformer(TransformationRule rule) {
        String suffix = (String) rule.getParameters().get("suffix");
        
        return original -> String.valueOf(original) + suffix;
    }
    
    private Function<Object, Object> createReplaceTransformer(TransformationRule rule) {
        Object replacement = rule.getParameters().get("value");
        
        return original -> replacement;
    }
    
    private Function<Object, Object> createTemplateTransformer(TransformationRule rule) {
        String template = (String) rule.getParameters().get("template");
        
        return original -> {
            String result = template;
            result = result.replace("${original}", String.valueOf(original));
            result = result.replace("${timestamp}", String.valueOf(System.currentTimeMillis()));
            result = result.replace("${random}", String.valueOf(random.nextInt(10000)));
            result = result.replace("${uuid}", UUID.randomUUID().toString());
            return result;
        };
    }
    
    private String generateRandomString(int length, String charset) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(charset.length());
            sb.append(charset.charAt(index));
        }
        return sb.toString();
    }
}