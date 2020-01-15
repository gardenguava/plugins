package com.lv.tools;

import com.appian.connectedsystems.simplified.sdk.SimpleIntegrationTemplate;
import com.appian.connectedsystems.simplified.sdk.configuration.SimpleConfiguration;
import com.appian.connectedsystems.templateframework.sdk.ExecutionContext;
import com.appian.connectedsystems.templateframework.sdk.IntegrationError;
import com.appian.connectedsystems.templateframework.sdk.IntegrationResponse;
import com.appian.connectedsystems.templateframework.sdk.TemplateId;
import com.appian.connectedsystems.templateframework.sdk.configuration.Document;
import com.appian.connectedsystems.templateframework.sdk.configuration.FolderPropertyDescriptor;
import com.appian.connectedsystems.templateframework.sdk.configuration.PropertyPath;
import com.appian.connectedsystems.templateframework.sdk.diagnostics.IntegrationDesignerDiagnostic;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateRequestPolicy;
import com.appian.connectedsystems.templateframework.sdk.metadata.IntegrationTemplateType;
import com.jcraft.jsch.*;
import com.lv.tools.exception.SFTPConnectedSystemExpcetion;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@TemplateId(name = "com.lv.tools.SFTPDownloadFileIntegrationTemplate")
@IntegrationTemplateType(IntegrationTemplateRequestPolicy.READ)
public class SFTPDownloadFileIntegrationTemplate extends SimpleIntegrationTemplate {

    public static final String INTEGRATION_PROP_SOURCE_SFTP_FOLDER_PATH = "sourceSFTPFolderPath";
    public static final String INTEGRATION_PROP_DEST_APPIAN_FOLDER = "destinationAppianFolder";
    public static final String INTEGRATION_PROP_APPIAN_FILE_NAME = "appianDocumentFileName";

    // Do not expose this now, for future implementation only.
    public static final String INTEGRATION_PROP_CHANNEL_TYPE = "sftp";

    @Override
    protected SimpleConfiguration getConfiguration(SimpleConfiguration integrationConfiguration, SimpleConfiguration connectedSystemConfiguration, PropertyPath updatedProperty, ExecutionContext executionContext) {
        return integrationConfiguration.setProperties(
                textProperty(INTEGRATION_PROP_SOURCE_SFTP_FOLDER_PATH)
                        .label("Source File Path")
                        .isRequired(true)
                        .isExpressionable(true)
                        .description("File Path in SFTP")
                        .build(),
                // 19.3+ dependency here
                FolderPropertyDescriptor.builder()
                        .key(INTEGRATION_PROP_DEST_APPIAN_FOLDER)
                        .label("Save to Folder")
                        .isRequired(true)
                        .instructionText("Destination Appian folder id to store fetched document")
                        .isExpressionable(true)
                        .build(),
                textProperty(INTEGRATION_PROP_APPIAN_FILE_NAME)
                        .label("Appian Document Name")
                        .isRequired(true)
                        .isExpressionable(true)
                        .instructionText("If left blank source filename will be used")
                        .build()
        );
    }

    @Override
    protected IntegrationResponse execute(SimpleConfiguration integrationConfiguration, SimpleConfiguration connectedSystemConfiguration, ExecutionContext executionContext) {
        Map<String, Object> requestDiagnostic = new HashMap<>();
        Map<String, Object> responseDiagnostic = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        String hostName = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_HOST_NAME);
        String port = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_PORT);
        String username = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_USERNAME);
        String password = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_PASSWORD);
        String baseFolder = connectedSystemConfiguration.getValue(SFTPConnectedSystem.CS_PROP_BASE_FOLDER);

        String sourceSFTPFolderPath = integrationConfiguration.getValue(INTEGRATION_PROP_SOURCE_SFTP_FOLDER_PATH);
        Long folderId = integrationConfiguration.getValue(INTEGRATION_PROP_DEST_APPIAN_FOLDER);
        String fileName = integrationConfiguration.getValue(INTEGRATION_PROP_APPIAN_FILE_NAME);

        requestDiagnostic.put("hostName", hostName);
        requestDiagnostic.put("port", port);
        requestDiagnostic.put("username", username);
        requestDiagnostic.put("sourceFolderPath", sourceSFTPFolderPath);
        requestDiagnostic.put("folderId", folderId);

        final long start = System.currentTimeMillis();

        try {
            Session session = getSession(hostName, username, password, Integer.parseInt(port));

            // Fetch document from SFTP to Appian
            session.connect();
            Document document = downloadFile(session, executionContext, getSourceFolderPath(baseFolder, sourceSFTPFolderPath), folderId, fileName);
            session.disconnect();

            // Capture responses
            responseDiagnostic.put("File Name", document.getFileName() + "." + document.getExtension());
            responseDiagnostic.put("File Size", document.getFileSize());

            // Save result
            result.put("Document", document);
        } catch (Exception e) {
            return IntegrationResponse.forError(getIntegrationError("Unable to download document", e)).build();
        }

        final long end = System.currentTimeMillis();

        final long executionTime = end - start;
        final IntegrationDesignerDiagnostic diagnostic = IntegrationDesignerDiagnostic.builder()
                .addExecutionTimeDiagnostic(executionTime)
                .addRequestDiagnostic(requestDiagnostic)
                .addResponseDiagnostic(responseDiagnostic)
                .build();

        return IntegrationResponse
                .forSuccess(result)
                .withDiagnostic(diagnostic)
                .build();
    }

    private Document downloadFile(Session session, ExecutionContext executionContext, String sourcePath, Long folderId, String fileName) throws JSchException, SftpException {
        ChannelSftp channel = (ChannelSftp) session.openChannel(INTEGRATION_PROP_CHANNEL_TYPE);

        channel.connect();
        InputStream inputStream = channel.get(sourcePath);
        Document document = executionContext.getDocumentDownloadService().downloadDocument(inputStream, folderId, fileName);

        return document;
    }

    private String getSourceFolderPath(String baseFolderPath, String relativeFolderPath) throws SFTPConnectedSystemExpcetion {
        if ((relativeFolderPath == null || relativeFolderPath == "") &&
                (baseFolderPath == null || baseFolderPath == "")
        ) {
            throw new SFTPConnectedSystemExpcetion("Invalid Source folder");
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
}
