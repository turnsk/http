package sk.turn.http;

import org.junit.Assert;
import org.junit.Test;

public class HttpTest {

	@Test
	public void syncGet() throws java.io.IOException {
		Http http = new Http("http://httpstat.us/200", Http.GET).send();
		Assert.assertEquals(200, http.getResponseCode());
		Assert.assertEquals("OK", http.getResponseMessage());
	}

	@Test
	public void syncGetMoved() throws java.io.IOException {
		Http http = new Http("http://httpstat.us/301", Http.GET).send();
		Assert.assertEquals(301, http.getResponseCode());
		Assert.assertEquals("Moved Permanently", http.getResponseMessage());
		Assert.assertEquals("http://httpstat.us", http.getResponseHeader("Location"));
	}

	@Test
	public void syncPost() throws java.io.IOException {
		Http http = new Http("http://httpstat.us/200", Http.POST).send();
		Assert.assertEquals(200, http.getResponseCode());
		Assert.assertEquals("OK", http.getResponseMessage());
	}

	@Test
	public void asyncGet() {
		final boolean[] lock = { true };
		Http http = new Http("http://httpstat.us/200", Http.GET).send(new Http.Listener() {
			public void onHttpResult(Http http) {
				synchronized (lock) {
					lock[0] = false;
					lock.notify();
				}
			}
		});
		synchronized (lock) {
			while (lock[0]) {
				try {
					lock.wait(100);
				} catch (InterruptedException e) { }
			}
		}
		Assert.assertEquals(200, http.getResponseCode());
		Assert.assertEquals("OK", http.getResponseMessage());
	}

}
