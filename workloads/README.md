# Overview
This project represents a container which is allowing cluster-internal access to monitoring facility.

## Reference

- [Monitoring facility equivalent to PMI](https://openliberty.io/docs/21.0.0.9/reference/feature/monitor-1.0.html)
- [REST connector for AdminClient](https://openliberty.io/docs/21.0.0.9/reference/feature/restConnector-2.0.html)

## Endpoints for starting mock workload
- Connection Pool: POST http://localhost:8080/workloads/api/v1/jdbc
- HTTP Session: POST http://localhost:8080/workloads/api/v1/session

- Parameters:

```
curl -H 'Content-Type: application/json' -d '{"duration":180000}' -v http://localhost:9080/workloads/api/v1/session
```

|Name|Meaning|Default Value|
|---|---|---|
|duration|Expected time for resource occupation(ms)|15000|
|range|Time variety(ms): actual occupation time should be between (duration - range) and (duration + range)|3000|
|multiplicity|Number of concurrent action of mock workloads started by this invocation|3|

## Endpoints for obtaining performance statistic(s)
- JVMStats
```
curl -u m5radmin:passw0rd -k https://localhost:9443/IBMJMXConnectorREST/mbeans/WebSphere:type=JvmStats/attributes
```
- ConnectionPoolStats
```
curl -u m5radmin:passw0rd -k https://localhost:9443/IBMJMXConnectorREST/mbeans/WebSphere:type=ConnectionPoolStats,name=jdbc/derby/attributes
```
- SessionStats
```
curl -u m5radmin:passw0rd -k https://localhost:9443/IBMJMXConnectorREST/mbeans/WebSphere:type=SessionStats,name=default_host/workloads/attributes
```
