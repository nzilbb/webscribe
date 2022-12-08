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
        } else {

          String mimeType = request.getParameter("format");
          if (mimeType == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            returnMessage("No format specified.", response);
          } else {
            // find serializer
            GraphSerializer serializer = findSerializer(mimeType);
            if (serializer == null) {
              response.setStatus(HttpServletResponse.SC_NOT_FOUND);
              returnMessage("No formatter found: " + mimeType, response);
            } else {
              Graph transcript = job.getTranscript();
              
              // configure serializer
              ParameterSet configuration = new ParameterSet();
              // default values
              serializer.configure(configuration, transcript.getSchema());
              
              // serialize the graph
              
              String[] layerIds = { transcript.getSchema().getUtteranceLayerId() };//serializer.getRequiredLayers();
              final Vector<NamedStream> files = new Vector<NamedStream>();
              serializer.serialize(
                nzilbb.ag.serialize.util.Utility.OneGraphSpliterator(transcript), layerIds,
                new Consumer<NamedStream>() {
                  public void accept(NamedStream stream) {
                    files.add(stream);
                  }},
                new Consumer<String>() {
                  public void accept(String warning) {
                    System.out.println("WARNING: " + warning);
                  }},
                new Consumer<SerializationException>() {
                  public void accept(SerializationException exception) {
                    System.err.println("SerializeFragment error: " + exception);
                  }       
                });

              if (files.size() == 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No files were generated");
              } else if (files.size() == 1) { // one file only
                // don't zip a single file, just return the file
                response.setContentType(mimeType);
                NamedStream stream = files.firstElement();
                response.addHeader("Content-Disposition", "attachment; filename=" + stream.getName());
                
                IO.Pump(stream.getStream(), response.getOutputStream());
              } else { /// multiple files
                response.setContentType("application/zip");
                response.addHeader(
                  "Content-Disposition", "attachment; filename="
                  + IO.SafeFileNameUrl(transcript.getId()) + ".zip");
                
                // create a stream to pump from
                PipedInputStream inStream = new PipedInputStream();
                final PipedOutputStream outStream = new PipedOutputStream(inStream);
                
                // start a new thread to extract the data and stream it back
                new Thread(new Runnable() {
                    public void run() {
                      try {
                        ZipOutputStream zipOut = new ZipOutputStream(outStream);
                        
                        // for each file
                        for (NamedStream stream : files) {
                          try {
                            // create the zip entry
                            zipOut.putNextEntry(
                              new ZipEntry(IO.SafeFileNameUrl(stream.getName())));
                            
                            IO.Pump(stream.getStream(), zipOut, false);
                          } catch (ZipException zx) {
                          } finally {
                            stream.getStream().close();
                          }
                        } // next file
                        try {
                          zipOut.close();
                        } catch(Exception exception) {
                          System.err.println(
                            "SerializeGraphs: Cannot close ZIP file: " + exception);
                        }
                      } catch(Exception exception) {
                        System.err.println("SerializeGraphs: open zip stream: " + exception);
                      }
                    }
                  }).start();
                
                // send headers immediately, so that the browser shows the 'save' prompt
                response.getOutputStream().flush();
                
                IO.Pump(inStream, response.getOutputStream());
              } // multiple files
              
            } // serializer found
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
