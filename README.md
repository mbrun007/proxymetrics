# proxymetrics
This repository provides a single class "InvocationMetricsProxy" that can be used to measure invocation counts and durations of a class instance interface methods. It is build around the java proxy functionality and allows therefore a very easy usage for e.g. debugging purposes. **It is not recommended to use this in production environment!**

## Limitations
* Please see the general java proxy limitations at https://docs.oracle.com/javase/8/docs/technotes/guides/reflection/proxy.html
* This class is not thread safe.

## Usage examples
```java
// markers are required to use because lists change their hashcode with every element changed...
List<String> arrayList = new ArrayList<>();
String arrayListMarker = "ARRAYLIST";
List<String> linkedList = new LinkedList<>();
String linkedListMarker = "LINKEDLIST";

// create the proxy objects for later invocation
List<String> arrayListProxy = InvocationMetricsProxy.newInstance(arrayList, arrayListMarker);
List<String> linkedListProxy = InvocationMetricsProxy.newInstance(linkedList, linkedListMarker);

// create some statistics
for (int i = 0; i < 1000; i++) {
    arrayListProxy.add(String.valueOf(i));
    linkedListProxy.add(String.valueOf(i));
}

/* +++ Evaluation Section +++*/

// Retrieve the metrics objects
InvocationMetricsProxy arrayMetrics = InvocationMetricsProxy.getMetricsProxy(arrayListMarker).get();
InvocationMetricsProxy linkedMetrics = InvocationMetricsProxy.getMetricsProxy(linkedListMarker).get();

// print from metrics object
System.out.println(arrayMetrics.getAllLoggable()); // prints metrics for all methods
System.out.println(arrayMetrics.getLoggable("add", false, false));

// retrieve the specific method statistics directly
List<Map.Entry<String, LongSummaryStatistics>> methodStats = arrayMetrics.getStatsForMethod("add", false, false);
methodStats.stream().forEach(entry -> System.out.println(InvocationMetricsProxy.getLoggable(entry.getValue(), "++++ Stats for: " + entry.getKey())));

// get and print in one line
InvocationMetricsProxy.getMetricsProxy(arrayListMarker).ifPresent(metrics -> System.out.println(metrics.getLoggable("add", false, false)));

// tell me if ArrayList implementation will be faster then LinkedList...
LongSummaryStatistics arrayListAddMetrics = arrayMetrics.getStatsForMethod("add", false, false).stream().filter(entry -> entry.getValue().getCount() > 0).findFirst().get().getValue();
LongSummaryStatistics linkedListAddMetrics = linkedMetrics.getStatsForMethod("add", false, false).stream().filter(entry -> entry.getValue().getCount() > 0).findFirst().get().getValue();
Assert.assertTrue("Wow, this is unexpected....", arrayListAddMetrics.getAverage() > linkedListAddMetrics.getAverage());
``` 
