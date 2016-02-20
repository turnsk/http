# http
Single-class easy-to-use Java HTTP utility wrapping standard Java URLConnection class and other related classes.

## Example

### HTTP GET (synchronous)

    Http http = new Http("https://example.com/", Http.GET)
            .addParam("getParamName", "paramValue")
            .send();
    if (http.getResponseCode() == 200) {
        System.out.println(http.getResponseString());
    }

### HTTP POST (synchronous)

    Http http = new Http("https://example.com/", Http.POST)
            .addParam("postParamName", "paramValue")
            .send();
    if (http.getResponseCode() == 200) {
        System.out.println(http.getResponseString());
    }
The `Content-Type` header in this case will be `application/x-www-form-urlencoded`.

### HTTP POST (synchronous) with raw data

    Http http = new Http("https://example.com/", Http.POST)
            .addHeader("Content-Type", "application/json")
            .setData("{\"Message\": \"Hello World!\"}")
            .send();
    if (http.getResponseCode() == 200) {
        System.out.println(http.getResponseString());
    }

### HTTP GET (asynchronous)

    Http http = new Http("https://example.com/", Http.POST)
            .addParam("postParamName", "paramValue")
            .send(new Http.Listener() {
                public void onHttpResult(Http http) {
                    if (http.getResponseCode() == 200) {
                        System.out.println(http.getResponseString());
                    }
                }
            });
