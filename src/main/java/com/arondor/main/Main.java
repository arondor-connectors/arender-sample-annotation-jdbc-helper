package com.arondor.main;

import com.arondor.viewer.annotation.api.Annotation;
import com.arondor.viewer.annotation.exceptions.InvalidAnnotationFormatException;
import com.arondor.viewer.client.api.document.DocumentId;
import com.arondor.viewer.client.api.document.DocumentPageLayout;
import com.arondor.viewer.client.api.document.PageDimensions;
import com.arondor.viewer.jdbc.annotation.JDBCAnnotationContentAccessor;
import com.arondor.viewer.rendition.api.annotation.AnnotationConverter;
import com.arondor.viewer.xfdf.annotation.AnnotationPositionTransformer;
import com.arondor.viewer.xfdf.annotation.AnnotationTransformer;
import com.arondor.viewer.xfdf.annotation.SerializedAnnotationContent;
import com.arondor.viewer.xfdf.annotation.XFDFAnnotationConverter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class Main
{
    private static DocumentId documentId = new DocumentId("testID");

    private static String driverClassName = "org.sqlite.JDBC";

    private static String url =
            "jdbc:sqlite:" + System.getProperty("user.home") + "/ARenderAnnotations/ARenderAnnotations.db";

    private static String login = "";

    private static String password = "";

    public static void main(String[] args)
    {
        // Read Properties
        CompositeConfiguration config = new CompositeConfiguration();
        config.addConfiguration(new SystemConfiguration());
        try
        {
            config.addConfiguration(new PropertiesConfiguration("src/main/resources/properties"));
        }
        catch (ConfigurationException e)
        {
            System.out.println("Exception caught : " + e);
        }
        if (config.getString("documentId") != null && !config.getString("documentId").isEmpty())
        {
            documentId = new DocumentId(config.getString("documentId"));
        }
        if (config.getString("driverClassName") != null && !config.getString("driverClassName").isEmpty())
        {
            driverClassName = config.getString("driverClassName");
        }
        if (config.getString("url") != null && !config.getString("url").isEmpty())
        {
            url = config.getString("url");
        }
        if (config.getString("login") != null && !config.getString("login").isEmpty())
        {
            login = config.getString("login");
        }
        if (config.getString("password") != null && !config.getString("password").isEmpty())
        {
            password = config.getString("password");
        }

        // Create XFDF annotation converter
        DocumentPageLayout documentLayout = new DocumentPageLayout();
        documentLayout.addPageDimensions(0, new PageDimensions(512f, 1024f));
        AnnotationTransformer annotationTransformer = new AnnotationPositionTransformer(documentId, documentLayout);
        AnnotationConverter annotationConverter = new XFDFAnnotationConverter(annotationTransformer);

        // Read annotations file
        List<Annotation> annotations = null;
        try
        {
            annotations = annotationConverter
                    .parse(documentId, new FileInputStream("src/main/resources/annotation.xml"));
        }
        catch (InvalidAnnotationFormatException | FileNotFoundException e)
        {
            System.out.println("Exception caught : " + e);
        }

        if (annotations.isEmpty())
        {
            return;
        }

        // Set JDBC properties
        JDBCAnnotationContentAccessor jDBCAnnotationContentAccessor = new JDBCAnnotationContentAccessor();
        DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource();
        driverManagerDataSource.setDriverClassName(driverClassName);
        driverManagerDataSource.setUrl(url);
        driverManagerDataSource.setUsername(login);
        driverManagerDataSource.setPassword(password);
        jDBCAnnotationContentAccessor.setDataSource(driverManagerDataSource);

        // Create or update DB table
        try
        {
            SerializedAnnotationContent content = jDBCAnnotationContentAccessor.getForModification(documentId, null);
            List<Annotation> existingAnnotations;
            InputStream inputStream = content.get();
            if (inputStream != null)
            {
                existingAnnotations = annotationConverter.parse(documentId, inputStream);
            }
            else
            {
                existingAnnotations = new ArrayList<>();
            }
            existingAnnotations.addAll(annotations);
            InputStream updatedContent = annotationConverter.serialize(documentId, existingAnnotations);
            content.update(updatedContent);
        }
        catch (Exception e)
        {
            System.out.println("Exception caught : " + e);
        }
    }
}
