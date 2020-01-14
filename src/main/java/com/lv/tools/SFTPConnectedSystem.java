package com.lv.tools;

import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.simplified.sdk.connectiontesting.SimpleTestableConnectedSystemTemplate;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appian.connectedsystems.templateframework.sdk.connectiontesting.TestConnectionResult;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

@TemplateId(name = "com.example.helloleela.SFTPConnectedSystem")
public class SFTPConnectedSystem extends SimpleTestableConnectedSystemTemplate {

  public static final String CS_PROP_HOST_NAME = "hostName";
  public static final String CS_PROP_USERNAME = "username";
  public static final String CS_PROP_PASSWORD = "password";
  public static final String CS_PROP_PORT = "port";
  public static final String CS_PROP_BASE_FOLDER = "baseFolder";


  @Override
  protected SimpleConfiguration getConfiguration(
          SimpleConfiguration simpleConfiguration, ExecutionContext executionContext) {

    return simpleConfiguration.setProperties(
            textProperty(CS_PROP_HOST_NAME).label("Host Name").description("SFTP Server host name").build(),
            textProperty(CS_PROP_PORT).label("Port").description("SFTP Server port to connect").build(),
            textProperty(CS_PROP_USERNAME).label("Userame").description("Username to login to SFTP Server").build(),
            encryptedTextProperty(CS_PROP_PASSWORD).label("Password").description("Password to login to SFTP Server").build(),
            textProperty(CS_PROP_BASE_FOLDER).label("Base Folder").description("Base folder, leave blank if you want to use absolute path with integration.").build()
    );
  }

  @Override
  protected TestConnectionResult testConnection(SimpleConfiguration simpleConfiguration, ExecutionContext executionContext) {
    JSch jsch = new JSch();
    Session session;

    try {
      session = jsch.getSession(
              simpleConfiguration.getValue(CS_PROP_USERNAME),
              simpleConfiguration.getValue(CS_PROP_HOST_NAME),
              Integer.parseInt(simpleConfiguration.getValue(CS_PROP_PORT))
      );
      session.setPassword((String) simpleConfiguration.getValue(CS_PROP_PASSWORD));
      session.setConfig("StrictHostKeyChecking", "no");
      session.connect();
      session.disconnect();
      return TestConnectionResult.success();
    } catch (JSchException e) {
      return TestConnectionResult.error("Something went wrong: " + e.getMessage());
    }

      /*String bearerTokenval=simpleConfiguration.getValue(DropBoxConnectedSystemTemplate.CS_DROPBOX_API_BEARER_TOKEN);

      BufferedReader httpResponseReader = null;
      try {


        URL serverUrl = new URL(CS_DROPBOX_CONNECT_URL);
        HttpURLConnection urlConnection = (HttpsURLConnection) serverUrl.openConnection();
        urlConnection.addRequestProperty ("Authorization", "Bearer " +bearerTokenval);
        urlConnection.setDoOutput(true);
        urlConnection.setRequestMethod("POST");

        int statusCode = urlConnection.getResponseCode();
        if(statusCode != 200){
          String errorMessage = "Invalid Access";
          return TestConnectionResult.error(errorMessage);
        }
        return TestConnectionResult.success();
      } catch (IOException e) {
        return TestConnectionResult.error("Something went wrong: " + e.getMessage());
      } finally {
        if (httpResponseReader != null) {
          try {
            httpResponseReader.close();
          } catch (IOException e) {
            return TestConnectionResult.error("Something went wrong: " + e.getMessage());
          }
        }
      }*/
  }
}
