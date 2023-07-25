//
// Copyright 2023 New Zealand Institute of Language, Brain and Behaviour, 
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

import java.net.URL;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.json.Json;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.util.IO;
import nzilbb.ag.Constants;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.ag.automation.Annotator;
import nzilbb.ag.automation.Transcriber;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.serialize.util.NamedStream;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;

/**
 * Flags a given job to email the resulting transcript to a given address.
 * <p> The URL path is formatted <tt>sendtranscript/<var>jobId</var></tt>
 * <p> Two HTTP parameters are required:
 * <dl>
 *  <dt>email</dt>
 *   <dd> The recipient email address. </dd>
 *  <dt>format</dt>
 *   <dd> The content-type of the transcript format, e.g. <q>text/x-eaf+xml</q>. </dd>
 * </dl>
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet("/sendtranscript/*")
public class SendTranscript extends ServletBase {
  /**
   * Default constructor.
   */
  public SendTranscript() {
  } // end of constructor

  long maxTranscriptAge = 1000 * 60 * 60 * 24; // 24 hours
  long ageCheckInterval = 1000 * 60 * 5; // 5 minutes
  Timer fileCheckTimer = new Timer("SendTranscript");    
  
  /**
   * Start a regular file-cleanup job.
   */
  @Override public void init() throws ServletException {
    final File transcriptsDir = new File(getServletContext().getRealPath("transcripts"));
    transcriptsDir.mkdir();
    TimerTask task = new TimerTask() {
        public void run() {
          log("Checking for old transcripts...");
          final long cutoffTime = new Date().getTime() - maxTranscriptAge;
          File[] subdirectories = transcriptsDir.listFiles(f -> {
              return f.isDirectory()
                && f.lastModified() < cutoffTime;
                });
          for (File subdirectory : subdirectories) {
            if (IO.RecursivelyDelete​(subdirectory)) {
              log("Removed " + subdirectory.getPath());
            } else {
              log("Could not remove " + subdirectory.getPath());
            }
          }
        }
      };
    fileCheckTimer.schedule(task, ageCheckInterval, ageCheckInterval);
    
    super.init();
  } // end of init()
  
  /**
   * Stop old-transcript-cleanup task.
   */
  public void destroy() {
    fileCheckTimer.cancel();
    super.destroy();
  } // end of destroy()
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    doPost(request, response);
  }
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    try {
      final SendEmailService mailer = new SendEmailService(getServletContext());

      if (request.getPathInfo() == null        
          || !request.getPathInfo().startsWith("/")
          || request.getPathInfo().equals("/")) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        returnMessage("No Job ID specified.", response);
        return;
      }
    
      String suffix = request.getPathInfo().substring(1);
      try {
        long jobId = Long.parseLong(suffix);
        log("SendTranscript: " + jobId);
        final Job job = Job.FindJob(jobId);
        if (job == null) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          returnMessage("Job not found: " + jobId, response);
          return;
        }
      
        final String email = request.getParameter("email");
        if (email == null || email.length() == 0) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          returnMessage("No email specified", response);
          return;
        }
        final String format = request.getParameter("format");
        if (format == null || format.length() == 0) {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          returnMessage("No format specified", response);
          return;
        }

        // send an initial message to confirm it's possible
        try {
          String subject = "Webscribe transcribing: " + job.getWav().getName();
          String html = 
            "<p>"+job.getTranscriber().getAnnotatorId()+" is transcribing "
            +job.getWav().getName()+" ...</p>"
            +"<p>You will receive a notification when finished.</p>";
          mailer.sendHtmlEmail(email, subject, html);
        } catch (Exception x) {
          log("Could not send initial email: " + x);
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          returnMessage("Could not send email: " + x, response);
          return;
        }

        // give the Job the email and format
        URL requestUrl = new URL(request.getRequestURL().toString());
        job.setOnFinished(finishedJob -> {
            String subject = "Webscribe finished: " + finishedJob.getWav().getName();
            String html =
              "<p>"+finishedJob.getTranscriber().getAnnotatorId()+" has finished transcribing "
              +finishedJob.getWav().getName()+"</p>";
            // serialize transcript
            SerializeService serialization = new SerializeService(getServletContext());
            try {
              NamedStream stream = serialization.serialize(finishedJob.getTranscript(), format);
            
              // save the transcript to a file
              File transcriptsDir = new File(getServletContext().getRealPath("transcripts"));
              transcriptsDir.mkdir();
              File jobDir = new File(transcriptsDir, ""+finishedJob.getId());
              jobDir.mkdir();
              stream.save(jobDir);

              // compute the download URL
              URL transcriptUrl = new URL(
                requestUrl,
                "../"+transcriptsDir.getName()+"/"+jobDir.getName()+"/"+stream.getName());
              
              // send an email
              html += "<p>"
                +"You can download it here: "
                +"<a href=\""+transcriptUrl+"\" download>"+transcriptUrl+"</a>"
                +"</p><p>This link will work for 24 hours.</p>";

              // TODO ensure it's deleted within 24 hours
            } catch (Throwable t) {
              html += "<p style='color: red;'>An error occured during formatting: "
                +t.getMessage() +"</p>";
              System.err.println(
                "Serializing for email " + finishedJob.getWav().getName() + ": " + t);
              t.printStackTrace(System.err);
            }
            try {
              mailer.sendHtmlEmail(email, subject, html);
            } catch (Exception x) {
              log("Could not send email: " + x);
            }
          });
        
        response.setContentType("application/json;charset=UTF-8");
        Json.createGenerator(response.getWriter())
          .writeStartObject()
          .write("email", email)
          .write("format", format)
          .write("message", job.getTranscriber().getStatus())
          .write("wav", job.getWav().getName())
          .write("percentComplete", job.getTranscriber().getPercentComplete())
          .write("running", job.getTranscriber().getRunning())
          .writeEnd()
          .close();
      } catch(Exception exception) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        returnMessage("Invalid Job ID: " + suffix, response);
      }
    } catch (NullPointerException npe) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      returnMessage(""+npe, response);
    }
  }

}
