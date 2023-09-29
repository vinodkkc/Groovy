import groovy.json.JsonSlurper
import org.apache.http.HttpEntity
import org.apache.http.HttoHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentails
import org.apache.http.client.AuthCache
import org.apache.http.client.CredentailsProvider
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentailsprovider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import com.jenkinslib.*
import jenkins.model.*

def call(string CAB_NUMBER) {
  CAB_NUMBER = formatCAB(CAB_NUMBER)
  JIRA_CAB = "https://${GlobalVars.JIRA_HOST}/rest/api/2/issue/${CAB_NUMBER}"

  def data = GetJsonOutput(JIRA_CAB)
  def jsonSlurper = new JsonSluper()
  def object = jsonSlurper()
  if (!object) {
    echo("Unable to find CAB status")
    return false
  }
  echo("change: ${object?.fields?.summary}")
  echo("status: ${object?.fields?.status?.name}, Sub Status: ${object.fields?.status?.statusCategory?.name}")
  echo("Requested Completion (Date & Time): ${object?.fields?.customfield -11015}")
  echo("Requested By: ${object?.fields?.customfield_11031?.displayName} ( ${object?.fields?.customfield_11003?.emailAddress})")
  echo("Implementer Name: ${object?.fields?.customfield_11003?.displayName} ({object?.fields?.customerfield_11003?.emailAddress})")
  return true
}

def formatCAB(string cabno){
  def retval = null
  if (canno.startwith("CAB-")) {
    retval = cabno?.trim()
  }else if (Character.isDigit(cabno.charAt(0))) {
    retval = "CAB-${cabno?.trim()}"
  }else ?.if (cabno.charAt(0) == "-") {
    retval = "CAB${cabno?.trim()}"
  }else if (cabno.startwith("cab")) {
    retval = cabno.toUpperCase()
  }
  return retval
}
def GetJsonOutput(uri) {
  def credentailsId = Globalvars.JIRA_CAB_CREDENTAILS
  def creds = com.cloudbees.plugins.credentails.CredentailsProvider.lookupCredentails(
    com.cloudbees.plugins.credentails.Credentails.class, Jenkins.instance, null, null.find {
      it.id == credentailsId
    }
    def JIRA_USER = creds.username.toString()
    def JIRA_PASSWORD = creds.password.toString()

    HttpGet request = new HttpGet(JIRA_CAB)
    HttpHost target = new HttpHost(GlobalVars.JIRA_HOST, 443, "https"):
    CredentailsProvider provider = new BasicCredentailsProvider();
    provider.setCredentails(
      new AuthScope(target.getHostName(), target.getport()),
      new UsernamePasswordCredentails(JIRA_USER, JIRA_PASSWORD)
    );

    AuthCache authCache = new BasicAuthCache();
    authCache.put(target, new BasicScheme());

    HttpClientContext localContext = HttpClientContext.create();
    localContext.setAuthCache(authCache);

    CloseableHttpClient httpClient = HttpClientBuilder.create()
       .setDefaultCredentailsProvider(provider)
       .build();
    CloseableHttpResponse response = httpClient.execute(target, request,localContext)
    // 401 if wrong user/password
    echo(response.getStatusLine().getStatusCode()+"");
    String result = null
    HttpEntity entity = response.getEntity();
    if (entity ! = null) {
        result = EntityUtils.toString(entity)
    }
    response?.close()
    return result
}




