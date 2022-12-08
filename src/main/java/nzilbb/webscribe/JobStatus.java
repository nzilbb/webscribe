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
import java.io.IOException;
import java.util.List;
import javax.json.Json;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import nzilbb.ag.Constants;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.ag.automation.Annotator;
import nzilbb.ag.automation.Transcriber;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import org.apache.commons.fileupload.servlet.*;

/**
 * Serlvet for receiving a recording and starting transcription.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet("/jobstatus/*")
public class JobStatus extends ServletBase {
  /**
   * Default constructor.
   */
  public JobStatus() {
  } // end of constructor
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

    if (request.getPathInfo() == null        
        || !request.getPathInfo().startsWith("/")
        || request.getPathInfo().equals("/")) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      returnMessage("No Job ID specified.", response);
    } else {
      String suffix = request.getPathInfo().substring(1);
      try {
        long jobId = Long.parseLong(suffix);
        log("JobStatus: " + jobId);
        Job job = Job.FindJob(jobId);
        if (job == null) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          returnMessage("Job not found: " + jobId, response);
        } else {
          
          response.setContentType("application/json;charset=UTF-8");
          Json.createGenerator(response.getWriter())
            .writeStartObject()
            .write("message", job.getTranscriber().getStatus())
            .write("wav", job.getWav().getName())
            .write("percentComplete", job.getTranscriber().getPercentComplete())
            .writeEnd()
            .close();
        }
      } catch(Exception exception) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        returnMessage("Invalid Job ID: " + suffix, response);
      }
    }
  }
}
