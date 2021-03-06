package com.lv.tools;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError;
import com.appian.connectedsystems.templateframework.sdk.IntegrationResponse;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appian.connectedsystems.templateframework.sdk.configuration.Document;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyPath;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateRequestPolicy;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateType;
import com.jcraft.jsch.*;
import com.lv.tools.exception.SFTPConnectedSystemExpcetion;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// ToDo: Replace username, password parameters with third party credentials

@TemplateId(name = "com.lv.tools.SFTPUploadFileIntegrationTemplate")
@IntegrationTemplateType(IntegrationTemplateRequestPolicy.WRITE)
public class SFTPUploadFileIntegrationTemplate extends SimpleIntegrationTemplate {

    public static final String INTEGRATION_PROP_SOURCE_APPIAN_DOCUMENT = "sourceAppianDocument";
    public static final String INTEGRATION_PROP_DESTINATION_SFTP_FOLDER_PATH = "destinationSFTPFolderPath";
    public static final String INTEGER_PROP_READ_ONLY_ABSOLUTE_PATH = "absolutePath";

    // Do not expose this now, for future implementation only.
    public static final String INTEGRATION_PROP_CHANNEL_TYPE = "sftp";

    @Override
    protected SimpleConfiguration getConfiguration(
            SimpleConfiguration integrationConfiguration,
            SimpleConfiguration connectedSystemConfiguration,
            PropertyPath propertyPath,
            ExecutionContext executionContext) {
        return integrationConfiguration.setProperties(
                documentProperty(INTEGRATION_PROP_SOURCE_APPIAN_DOCUMENT)
                        .label("Appian Document")
                        .isRequired(true)
                        .isExpressionable(true)
                        .description("Select appian document to send to SFTP folder")
                        .build(),
                textProperty(INTEGRATION_PROP_DESTINATION_SFTP_FOLDER_PATH)
                        .label("Destination Folder Path")
                        .isRequired(true)
                        .isExpressionable(true)
                        .description("Enter destination SFTP folder path")
                        .build(),
                textProperty(INTEGER_PROP_READ_ONLY_ABSOLUTE_PATH)
                        .label("Absolute File Path")
                        .isRequired(false)
                        .isReadOnly(true)
                        .instructionText("ToDo: Display readonly absolute path here.")
                        .build()
        );
    }

    @Override
    protected IntegrationResponse execute(
            SimpleConfiguration integrationConfiguration,
            SimpleConfiguration connectedSystemConfiguration,
            ExecutionContext executionContext) {
        Map<String, Object> requestDiagnostic = new HashMap<>();
        Map<String, Object> responseDiagnostic = new HashMap<>();

        String hostName = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_HOST_NAME);
        String port = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_PORT);
        String username = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_USERNAME);
        String password = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_PASSWORD);
        String baseFolder = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_BASE_FOLDER);

        Document sourceAppianDocument = integrationConfiguration.getValue(INTEGRATION_PROP_SOURCE_APPIAN_DOCUMENT);
        String destinationSFTPFolderPath = integrationConfiguration.getValue(INTEGRATION_PROP_DESTINATION_SFTP_FOLDER_PATH);

        requestDiagnostic.put("hostName", hostName);
        requestDiagnostic.put("port", port);
        requestDiagnostic.put("username", username);
        requestDiagnostic.put("sourceAppianDocument", sourceAppianDocument);
        requestDiagnostic.put("destinationSFTPFolderPath", destinationSFTPFolderPath);

        final long start = System.currentTimeMillis();

        try {
            Session session = getSession(hostName, username, password, Integer.parseInt(port));
            session.connect();
            uploadFile(session, sourceAppianDocument.getInputStream(), getDestinationFolderPath(baseFolder, destinationSFTPFolderPath));
            session.disconnect();

            responseDiagnostic.put("Appian Document Name", sourceAppianDocument.getFileName());
            responseDiagnostic.put("Document Size", sourceAppianDocument.getFileSize());
        } catch (Exception e) {
            return IntegrationResponse.forError(getIntegrationError("Unable to upload document", e)).build();
        }

        final long end = System.currentTimeMillis();

        final long executionTime = end - start;
        final IntegrationDesignerDiagnostic diagnostic = IntegrationDesignerDiagnostic.builder()
                .addExecutionTimeDiagnostic(executionTime)
                .addRequestDiagnostic(requestDiagnostic)
                .build();

        return IntegrationResponse
                .forSuccess(responseDiagnostic)
                .withDiagnostic(diagnostic)
                .build();
    }

    private String getDestinationFolderPath(String baseFolderPath, String relativeFolderPath) throws SFTPConnectedSystemExpcetion {
        if ((relativeFolderPath == null || relativeFolderPath == "") &&
                (baseFolderPath == null || baseFolderPath == "")
        ) {
            throw new SFTPConnectedSystemExpcetion("Invalid destination folder");
        }
        if (baseFolderPath == null || baseFolderPath == "") {
            return relativeFolderPath;
        }
        return baseFolderPath + "/" + relativeFolderPath;
    }

    private IntegrationError getIntegrationError(String title, Exception e) {
        return IntegrationError.builder()
                .title(title)
                .message(e.getMessage())
                .build();
    }

    private Session getSession(String hostname, String username, String password, int port) throws JSchException {
        Session session = new JSch().getSession(username, hostname, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");

        return session;
    }

    private void uploadFile(Session session, InputStream inStream, String destination) throws JSchException, SftpException {
        Channel channel = session.openChannel(INTEGRATION_PROP_CHANNEL_TYPE);
        channel.connect();
        ChannelSftp sftpChannel = (ChannelSftp) channel;
        sftpChannel.put(inStream, destination);
        sftpChannel.exit();
    }
}
