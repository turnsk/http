package sk.turn.http;

import org.junit.Assert;
import org.junit.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class HttpsTest {

  @Test
  public void syncGetOk() throws java.io.IOException {
    Http http = new Http("https://www.google.com", Http.GET).send();
    Assert.assertEquals(200, http.getResponseCode());
    Assert.assertEquals("OK", http.getResponseMessage());
  }

  @Test(expected = SSLHandshakeException.class)
  public void syncGetNok() throws java.io.IOException {
    new Http("https://untrusted-root.badssl.com/", Http.GET).send();
  }

  @Test
  public void syncGetOkPinnedRoot() throws java.io.IOException, CertificateException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("badssl_untrusted_root.pem")) {
      CertificateFactory fact = CertificateFactory.getInstance("X.509");
      X509Certificate cert = (X509Certificate) fact.generateCertificate(in);
      Http http = new Http("https://untrusted-root.badssl.com/", Http.GET).setTrustedRoot(cert).send();
      Assert.assertEquals(200, http.getResponseCode());
      Assert.assertEquals("OK", http.getResponseMessage());
    }
  }

  @Test
  public void syncGetOkPinnedClient() throws java.io.IOException, CertificateException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("badssl_untrusted_client.pem")) {
      CertificateFactory fact = CertificateFactory.getInstance("X.509");
      X509Certificate cert = (X509Certificate) fact.generateCertificate(in);
      Http http = new Http("https://untrusted-root.badssl.com/", Http.GET).setTrustedRoot(cert).send();
      Assert.assertEquals(200, http.getResponseCode());
      Assert.assertEquals("OK", http.getResponseMessage());
    }
  }

  @Test(expected = SSLHandshakeException.class)
  public void syncGetNokPinned() throws java.io.IOException, CertificateException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("badssl_untrusted_root.pem")) {
      CertificateFactory fact = CertificateFactory.getInstance("X.509");
      X509Certificate cert = (X509Certificate) fact.generateCertificate(in);
      new Http("https://www.google.com/", Http.GET).setTrustedRoot(cert).send();
    }
  }

  @Test(expected = IllegalStateException.class)
  public void syncGetNokPinnedHttp() throws java.io.IOException, CertificateException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("badssl_untrusted_root.pem")) {
      CertificateFactory fact = CertificateFactory.getInstance("X.509");
      X509Certificate cert = (X509Certificate) fact.generateCertificate(in);
      new Http("http://httpstat.us/200", Http.GET).setTrustedRoot(cert).send();
    }
  }

}
