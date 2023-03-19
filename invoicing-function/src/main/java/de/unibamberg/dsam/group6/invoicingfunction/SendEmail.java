package de.unibamberg.dsam.group6.invoicingfunction;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class SendEmail {
    public static void main() throws IOException {

        final String username = "pytestertrack@gmail.com";
        final String password = "xwmisbkrycurxtsg";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.setProperty("mail.smtp.ssl.protocols", "TLSv1.2");

        //Set up GCP to use these service creds
        StorageOptions options = StorageOptions.newBuilder()
                .setProjectId("maximal-radius-375114")
                .setCredentials(GoogleCredentials.fromStream(
                        new FileInputStream("D:\\Sanketh\\Spring Workspace\\group6\\src\\main\\resources\\static\\maximal-radius-375114-c8c681e93685.json"))).build(); //Give the JSON file path
        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress("help.prost@gmail.com"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("sankethsg2021@gmail.com"));
            message.setSubject("Here is the invoice for your recent order");
//            message.setText("Hello, this is a test email!");
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("PFA Order details");

            //TODO: get the file  from bucket and send it as a attachment

            Storage storage = options.getService();
            Blob blob = storage.get("prost--invoice-pdf-bucket", "Invoice.pdf");
            final Path path = Paths.get("D:\\Sanketh\\Spring Workspace\\group6\\src\\main\\resources\\static\\Invoice.pdf");
            blob.downloadTo(path);

            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(new File("D:\\Sanketh\\Spring Workspace\\group6\\src\\main\\resources\\static\\Invoice.pdf"));


            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            multipart.addBodyPart(attachmentPart);
            message.setContent(multipart);

            Transport.send(message);

            Files.delete(path);
            System.out.println("Done");

        } catch (MessagingException | IOException e) {
            throw new RuntimeException(e);
        }

    }
}
