# http
Single-class easy-to-use Java HTTP utility wrapping standard Java URLConnection class and other related classes.

[Read Javadoc](https://jitpack.io/sk/turn/http/1.6.2/javadoc/)  
[Download JAR](https://jitpack.io/sk/turn/http/1.6.2/http-1.6.2.jar)

## Setup
### Gradle
In your root `build.gradle` add the following maven url to the end your repositories list.
```gradle
allprojects {
	repositories {
		...
		maven { url "https://jitpack.io" }
	}
}
```
Add the dependecy to the `build.gradle`
```gradle
dependencies {
    ...
    compile 'sk.turn:http:1.6.2'
}
```

### Maven
Add the jitpack repository
```xml
<repositories>
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
</repositories>
```
Add the dependency
```xml
<dependency>
    <groupId>sk.turn</groupId>
    <artifactId>http</artifactId>
    <version>1.6.2</version>
</dependency>
```

## Example
### HTTP GET (synchronous)
```java
Http http = new Http("https://example.com/", Http.GET)
        .addParam("getParamName", "paramValue")
        .send();
if (http.getResponseCode() == 200) {
    System.out.println(http.getResponseString());
}
```

### HTTP GET with Object deserializing
```java
public class IpAddress {
    public String ip;
}

IpAddress ipAddress = new Http("http://ip.jsontest.com/", Http.GET)
        .send()
        .getResponseObject(IpAddress.class);
System.out.println("Your IP is " + (ipAddress != null ? ipAddress.ip : "Unknown"));
```

### HTTP POST (synchronous)
```java
Http http = new Http("https://example.com/", Http.POST)
        .addParam("postParamName", "paramValue")
        .send();
if (http.getResponseCode() == 200) {
    System.out.println(http.getResponseString());
}
```
The `Content-Type` header in this case will be `application/x-www-form-urlencoded`.

### HTTP POST (synchronous) with raw data
```java
Http http = new Http("https://example.com/", Http.POST)
        .addHeader("Content-Type", "application/json")
        .setData("{\"Message\": \"Hello World!\"}")
        .send();
if (http.getResponseCode() == 200) {
    System.out.println(http.getResponseString());
}
```

### HTTP GET (asynchronous)
```java
Http http = new Http("https://example.com/", Http.POST)
        .addParam("postParamName", "paramValue")
        .send(new Http.Listener() {
            public void onHttpResult(Http http) {
                if (http.getResponseCode() == 200) {
                    System.out.println(http.getResponseString());
                }
            }
        });
```
