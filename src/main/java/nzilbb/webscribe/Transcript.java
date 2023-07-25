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
import nzilbb.ag.Graph;
import nzilbb.ag.automation.Annotator;
import nzilbb.ag.automation.Transcriber;
import nzilbb.ag.automation.util.AnnotatorDescriptor;
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.ag.serialize.SerializationException;
import nzilbb.ag.serialize.util.NamedStream;
import nzilbb.util.IO;
import nzilbb.configure.ParameterSet;
import java.util.Vector;
import java.util.function.Consumer;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

/**
 * Returns the status of a transcription job.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet("/transcript/*")
public class Transcript extends ServletBase {
  /**
   * Default constructor.
   */
  public Transcript() {
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
        log("Transcript: " + jobId);
        Job job = Job.FindJob(jobId);
        if (job == null) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          returnMessage("Job not found: " + jobId, response);
        } else if (job.getTranscript() == null) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          if (job.getTranscriber() == null) {
            returnMessage("No transcript for job " + jobId, response);
          } else {
            returnMessage("No transcript: " + job.getTranscriber().getStatus(), response);
          }
        } else {

          String mimeType = request.getParameter("format");
          if (mimeType == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            returnMessage("No format specified.", response);
          } else {
            SerializeService serialization = new SerializeService(getServletContext());
            try {
              NamedStream stream = serialization.serialize(job.getTranscript(), mimeType);
              if (stream.getName().endsWith(".zip")) {
                response.setContentType("application/zip");
              } else {
                response.setContentType(mimeType);
              }
              response.addHeader(
                "Content-Disposition", "attachment; filename=" + stream.getName());
              // send headers immediately, so that the browser shows the 'save' prompt
              response.getOutputStream().flush();
              // send data...
              IO.Pump(stream.getStream(), response.getOutputStream());
            } catch (NullPointerException npe) {
              response.setStatus(HttpServletResponse.SC_NOT_FOUND);
              returnMessage(""+npe.getMessage(), response);
            }
          } // mimeType set
        } // job found
      } catch(Exception exception) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        returnMessage("Invalid Job ID: " + suffix, response);
      }
    }
  }
  
  /**
   * Finds the serializer that outputs the given format.
   * @param mimeType
   * @return The serializer, or null if it couldn't be found.
   */
  protected GraphSerializer findSerializer(String mimeType) throws Exception {
    if ("application/json".equals(mimeType)) {
      return new nzilbb.ag.serialize.json.JSONSerialization();
    }
    // get a list of serializers
    File serializerDir = new File(getServletContext().getRealPath("formatter"));
    File[] serializers = serializerDir.listFiles((File dir, String name)->{
        return name.endsWith(".jar");
      });
    for (File jar : serializers) {
      Vector implementors = IO.FindImplementorsInJar(
        jar, getClass().getClassLoader(), 
        Class.forName("nzilbb.ag.serialize.GraphSerializer"));
      for (Object o : implementors) {
        GraphSerializer serializer = (GraphSerializer)o;
        SerializationDescriptor descriptor = serializer.getDescriptor();
        if (descriptor.getMimeType().equals(mimeType)) {
          return serializer;
        }
      } // next serializer
    } // next jar file
    // if we got this far, we didn't find it
    return null;
  } // end of findSerializer()

}
