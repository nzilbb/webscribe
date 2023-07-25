//
// Copyright 2022 New Zealand Institute of Language, Brain and Behaviour, 
// University of Canterbury
// Written by Robert Fromont - robert.fromont@canterbury.ac.nz
//
//    This file is part of webscribe.
//
//    This is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; either version 2 of the License, or
//    (at your option) any later version.
//
//    This software is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this software; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
package nzilbb.webscribe;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletContext;

/**
 * Sends emails. This uses the following servlet configuration parameters:
 * <dl>
 *  <dt>SMTPHost</dt> <dd>Host name of the SMTP server.</dd>
 *  <dt>EmailFrom</dt> <dd>Email address that messages are sent from.</dd>
 *  <dt>SMTPUser</dt> <dd>SMTP username, if required.</dd>
 *  <dt>SMTPPassword</dt> <dd>SMTP password, if required.</dd>
 * </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
public class SendEmailService {
  
  ServletContext context;

  String SMTPHost; // Host name of the SMTP server.
  String EmailFrom; // Email address that messages are sent from.
  String SMTPUser; // SMTP username, if required.
  String SMTPPassword; // SMTP password, if required.  
  
  /**
   * Constructor.
   * @param config The servlet configuration.
   * @throws NullPointerException If the server is not configured to send email.
   */
  public SendEmailService(ServletContext context) throws NullPointerException {
    this.context = context;
    SMTPHost = context.getInitParameter("SMTPHost");
    if (SMTPHost != null && SMTPHost.length() == 0) SMTPHost = null;
    EmailFrom = context.getInitParameter("EmailFrom");
    if (EmailFrom != null && EmailFrom.length() == 0) EmailFrom = null;
    SMTPUser = context.getInitParameter("SMTPUser");
    if (SMTPUser != null && SMTPUser.length() == 0) SMTPUser = null;
    SMTPPassword = context.getInitParameter("SMTPPassword");
    if (SMTPPassword != null && SMTPPassword.length() == 0) SMTPPassword = null;

    if (SMTPHost == null) {
      context.log("SendEmailService: SMTPHost is not set.");
      throw new NullPointerException("SMTPHost is not set.");
    }
  } // end of constructor
  
  /**
   * Sends an email.
   * @param to Email address of recipient. Multiple recipients must be delimited by ';'.
   * @param subject Subject line.
   * @param message Message body.
   */
  public void sendHtmlEmail(String to, String subject, String html)
    throws AddressException, MessagingException {
    sendEmail(null, to, subject, html, true, null);
  }
  /**
   * Sends an email.
   * @param from Email address, or null to default to correctionsEmail system attribute.
   * @param to Email address of recipient. Multiple recipients must be delimited by ';'.
   * @param subject Subject line.
   * @param message Message body.
   * @param html Whether the message is HTML (true) or plain text (false).
   * @param attachment A file to attach, or null.
   */
  public void sendEmail(
    String from, String to, String subject, String message, boolean html, File attachment)
    throws AddressException, MessagingException {
    
    // Set the host smtp address
    Properties props = new Properties();
    props.put("mail.smtp.host", context.getInitParameter("SMTPHost"));
    
    if (SMTPUser != null) {
      if (SMTPPassword != null) { // password is set too
        props.put("mail.smtp.auth", "true");
      } else { // no password
        // treat SMTPUser as the sending host instead
        // otherwise, set mail.smtp.localhost
        props.put("mail.smtp.localhost", SMTPUser);
      }
      props.put("mail.smtp.starttls.enable","true");	 
      props.put("mail.smtp.EnableSSL.enable","true");
      
      props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");   
      props.setProperty("mail.smtp.socketFactory.fallback", "false");   
      props.setProperty("mail.smtp.port", "465");   
      props.setProperty("mail.smtp.socketFactory.port", "465");
    }
    // create some properties and get the default Session
    Session session = Session.getDefaultInstance(props, null);

    // create a message
    Message msg = new MimeMessage(session);

    // set the from and to address
    if (from == null) from = EmailFrom;
    InternetAddress addressFrom = new InternetAddress(from);
    msg.setFrom(addressFrom);

    for (String recipient : to.split(";")) {
       InternetAddress addressTo = new InternetAddress(recipient); 
       msg.addRecipient(Message.RecipientType.TO, addressTo);
    } // next recipient
   
    // Setting the Subject and Content Type
    msg.setSubject(subject);
    if (attachment == null) {
      msg.setContent(message, "text/" + (html?"html":"plain"));
    } else { // there's an attachment
      // Create the message part 
      BodyPart messageBodyPart = new MimeBodyPart();
	 
      // Fill the message
      messageBodyPart.setContent(message, "text/" + (html?"html":"plain"));
         
      // Create a multipart message
      Multipart multipart = new MimeMultipart();
	 
      // Set text message part
      multipart.addBodyPart(messageBodyPart);
	 
      // Part two is attachment
      messageBodyPart = new MimeBodyPart();
      String filename = attachment.getPath();
      DataSource source = new FileDataSource(filename);
      messageBodyPart.setDataHandler(new DataHandler(source));
      messageBodyPart.setFileName(attachment.getName());
      multipart.addBodyPart(messageBodyPart);

      // Send the complete message parts
      msg.setContent(multipart);
    } // attachment

    if (SMTPUser != null && SMTPPassword != null) {
      try {
        Transport tr = session.getTransport("smtp");
        tr.connect(SMTPHost, SMTPUser, SMTPPassword);
        msg.saveChanges();
        tr.sendMessage(msg, msg.getAllRecipients());
        tr.close();
      } catch(javax.mail.MessagingException t) {
        context.log("sendEmail: " + t);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        context.log(sw.toString());
        throw t;
      } catch(Throwable t) {
        context.log("sendEmail: " + t);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        context.log(sw.toString());
      }
    } else {
      Transport.send(msg);
    }
  } // end of sendEmail()
  
} // end of class SendEmailService
