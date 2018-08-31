package com.github.mbrun.proxymetrics;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class usable for measuring method invocation counts and times of interface methods implemented by any class and instance.
 * Usage of java dynamic proxy functionality for simple injection.
 *
 * @author M. Brun
 */
public class InvocationMetricsProxy implements InvocationHandler {

    private static final Map<Object, InvocationMetricsProxy> objectProxyMap = new ConcurrentHashMap<>();

    private Map<String, Map.Entry<Method, LongSummaryStatistics>> methodStats = new ConcurrentHashMap<>();
    private Object target;

    /**
     * Creates a new proxy object which measures all methods implemented by all interfaces of the given object. The provided {@code marker} object serves for identification purpose of the {@link InvocationMetricsProxy}. Make sure that the marker object has a {@code hashCode()} implementation that provides unique not changing values.
     *
     * @param obj    The object to create the proxy for. All implemented interface methods of the given class will be captured by metrics measurement
     * @param marker Object to be used as marker to later retrieve the measurements in form of an {@link InvocationMetricsProxy} object. This object must use a not changing proper implementation of {@code hashcode()} method as a hash based map is used in the background!
     * @param <T>    type of the passed in object that shall be reflected by the proxy
     * @return Returns the newly created proxy object that can be used instead of the original passed in object for invocation.
     */
    @SuppressWarnings("unchecked")
    public static <T> T newInstance(T obj, Object marker) {
        InvocationMetricsProxy metricsProxy = new InvocationMetricsProxy(obj);
        T proxy = (T) java.lang.reflect.Proxy.newProxyInstance(
                obj.getClass().getClassLoader(),
                obj.getClass().getInterfaces(),
                metricsProxy);
        objectProxyMap.putIfAbsent(marker, metricsProxy);
        return proxy;
    }

    /**
     * Provides access to the set of created {@link InvocationMetricsProxy} instances and returns the related instance by providing the formerly used marker object (see {@link InvocationMetricsProxy#newInstance(Object, Object)}).
     *
     * @param marker object used to identify the specific {@link InvocationMetricsProxy} instance (see {@link InvocationMetricsProxy#newInstance(Object, Object)})
     * @return Returns an {@link Optional} that contains the desired instance if found.
     */
    public static Optional<InvocationMetricsProxy> getMetricsProxy(Object marker) {
        return Optional.ofNullable(objectProxyMap.get(marker));
    }

    /**
     * Provides a directly loggable string of a single {@link LongSummaryStatistics} object with optional preliminary lines.
     *
     * @param statistics  the statistics to be loggable
     * @param additionals additional string array as preliminary log content. Each array entry will be on a dedicated line
     */
    public static String getLoggable(LongSummaryStatistics statistics, String... additionals) {
        StringJoiner joiner = new StringJoiner("\n");
        for (String additional : additionals) {
            joiner.add(additional);
        }
        return joiner.add(String.format("Count: %d", statistics.getCount())).add(String.format("Min: %dns", statistics.getMin())).add(String.format("Max: %dns", statistics.getMax())).add(String.format("Avg: %fns", statistics.getAverage())).toString();
    }

    private InvocationMetricsProxy(Object target) {
        this.target = target;
        for (Class<?> clazz : target.getClass().getInterfaces()) {
            for (Method method : clazz.getMethods()) {
                this.methodStats.putIfAbsent(method.toString(), new AbstractMap.SimpleEntry<>(method, new LongSummaryStatistics()));
            }
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        Object result = null;
        try {
            long start = System.nanoTime();
            result = method.invoke(target, args);
            long elapsed = System.nanoTime() - start;
            Map.Entry<Method, LongSummaryStatistics> methodStatistics = methodStats.get(method.toString());
            if (null != methodStatistics) {
                methodStatistics.getValue().accept(elapsed);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Provides a list of found methods and according methods.
     *
     * @param methodName       Name of the method to log the statistics for. May be used to specify only parts of a method name. See {@code containsSearch} parameter.
     * @param containsSearch   Flag to define if the given {@code methodName} shall be used for a contains search among all the methods or not.
     * @param stopOnFirstMatch Flag to define if the log of statistics shall be stopped after finding the first matching method.
     * @return Returns a list of all found entries matching to the given search criterias. The return will be for sure not be null.
     */
    public List<Map.Entry<String, LongSummaryStatistics>> getStatsForMethod(String methodName,
                                                                            boolean containsSearch, boolean stopOnFirstMatch) {
        List<Map.Entry<String, LongSummaryStatistics>> result = new ArrayList<>();
        for (Map.Entry<Method, LongSummaryStatistics> stats : methodStats.values()) {
            boolean hit = containsSearch ? stats.getKey().getName().contains(methodName) : stats.getKey().getName().equals(methodName);
            if (hit) {
                result.add(new AbstractMap.SimpleEntry<>(stats.getKey().toString(), stats.getValue()));
                if (stopOnFirstMatch) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Provides the metrics of all methods in a loggable way.
     *
     * @return Returns a directly loggable string with all method invocation metrics
     */
    public String getAllLoggable() {
        StringJoiner joiner = new StringJoiner("\n");
        for (Map.Entry<Method, LongSummaryStatistics> stats : methodStats.values()) {
            joiner.add(InvocationMetricsProxy.getLoggable(stats.getValue(), "++++ Stats for " + stats.getKey() + " ++++"));
        }
        return joiner.toString();

    }

    /**
     * Provides the metrics of one or more methods in a loggable way.
     *
     * @param methodName       Name of the method to log the statistics for. May be used to specify only parts of a method name. See {@code containsSearch} parameter.
     * @param containsSearch   Flag to define if the given {@code methodName} shall be used for a contains search among all the methods or not.
     * @param stopOnFirstMatch Flag to define if the log of statistics shall be stopped after finding the first matching method.
     * @return Returns a directly loggable string with the found method invocation metrics
     */
    public String getLoggable(String methodName, boolean containsSearch, boolean stopOnFirstMatch) {
        List<Map.Entry<String, LongSummaryStatistics>> stats = getStatsForMethod(methodName, containsSearch, stopOnFirstMatch);
        StringJoiner joiner = new StringJoiner("\n");
        for (Map.Entry<String, LongSummaryStatistics> singleStats : stats) {
            joiner.add(InvocationMetricsProxy.getLoggable(singleStats.getValue(), "++++ Stats for " + singleStats.getKey() + " ++++"));
        }
        return joiner.toString();
    }
}
