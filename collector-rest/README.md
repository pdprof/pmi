# collector-rest
An application running as a client of RESTConnector of Liberty using JAX-RS client API.
With this application, we can download performance statistics like `vmstat` command as CSV.

## Considerations for Basic Authentication using JAX-RS client API

### Build `Authorization` header with ourselves

The token of Basic Authentication should be *user-id*:*password* with BASE64 encoded.  
(Admin user-id/password of [_workloads_](../workloads/) application is m5radmin/passw0rd.)

Sample implementation will be found in [RestClientController](src/main/java/collector/rest/RestClientController.java).

## Considerations for accessing RESTConnector remotely

### Use dummy [TrustManager](https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/TrustManager.html) if you need bypassing server certificate validation

JAX-RS client verifies that the trust store managed by application server contains
the server certificate of peer of HTTPS connections.

For bypassing this operation, custom [SSLContext](https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/SSLContext.html)
with a TrustManager trusting any hosts should be applied for the JAX-RS
[Client](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/client/Client.html)
instance via [ClientBuilder](https://javaee.github.io/javaee-spec/javadocs/javax/ws/rs/client/ClientBuilder.html).

Sample implementation will be found in [RestClientController](src/main/java/collector/rest/RestClientController.java).

## Considerations for OpenShift environment

### Use dummy [HostnameVerifier](https://docs.oracle.com/javase/8/docs/api/javax/net/ssl/HostnameVerifier.html) because canonical name(CN) of certificate will be inappropriate

Default certificate is created when the pod is started and it's CN is `localhost`.

You can confirm this in following steps:
- Open terminal to the pod on which the image [_workloads_](../workloads) is running.
- Move to the directory which contains key store:
  ```
  cd /opt/ol/wlp/output/defaultServer/resources/security/
  ```
- Export certificate:
  ```
  keytool -exportcert -file liberty.cer -keystore key.p12 -alias default
  ```
  password of the key store is `passw0rd`.
- Confirm content of the certificate:
  ```
  keytool -printcert -file liberty.cer
  ```
  You'll find that both CN of the owner and issuer (because it is a self-signed certificate) are `localhost`.

Certificate-based HostnameVerifier will verify that the host name of the URL of resource
is same as the CN of the server certificate used in current HTTPS session.

Dummy HostnameVerifier resolves this problem.
Sample implementation will be found in [RestClientController](src/main/java/collector/rest/RestClientController.java).

### Specifying path for the targeted pod.

We can identify the server using service name as host name, IP address of the pod.
Number of pods is not fixed, so IP address is necessary for gathering metrics correctly.
For the other hand, name and IP address of a pod is determined dynamically,
some facilitation like discovery service is wished to have in the production environment.

### The gateway-timeout of Route may had better to be postponed.

Default timeout of Route (30 sec.) is relatively short for this application.
If interval of the polling operation exceeds this limit,
the connection for downloading CSV will be terminated.

- Before the service responding to request:  
  Route returns 504 (Gateway Timeout).
- During CSV download:  
  Status has been determined to 200 (OK), simply response entity is terminated.

You can postpone the limit with adding annotation to the Route:  
```
oc annotate route collector-rest --overwrite haproxy.router.openshift.io/timeout=90s
```
`timeout` is specified with us(micro-seconds), ms(milli-seconds), s(seconds), m(minutes), h(hours), or d(days).
