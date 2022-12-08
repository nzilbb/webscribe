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
@WebServlet("/starttranscription")
public class StartTranscription extends ServletBase {
  /**
   * Default constructor.
   */
  public StartTranscription() {
  } // end of constructor
  
  @Override
  @SuppressWarnings("unchecked")
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    
    File wav = null;
    String name = null;
    ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
    try {
      List<FileItem> items = upload.parseRequest(request);
      for (FileItem item : items) {
        if (!item.isFormField()
            && item.getName() != null && item.getName().toLowerCase().endsWith(".wav")) {
          log("File: " + item.getName());
          
          // save file
          File tempDir = File.createTempFile("webscriber", item.getName());
          tempDir.deleteOnExit();
          tempDir.delete();
          wav = new File(tempDir, item.getName());
          wav.deleteOnExit();
          item.write(wav);
          log("Saved: " + wav.getPath());
          break; // only one file at a time
        } // .wav file
      } // next item
      
      if (wav == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        returnMessage("No wav file found.", response);
      } else {
        // start transcription task
        Job job = startTranscriptionJob(wav);
        response.setContentType("application/json;charset=UTF-8");
        Json.createGenerator(response.getWriter())
          .writeStartObject()
          .write("message", "Uploaded " + wav.getName())
          .write("jobId", ""+job.getId())
          .write("transcriber", job.getTranscriber().getAnnotatorId())
          .write("version", job.getTranscriber().getVersion())
          .write("wav", job.getWav().getName())
          .writeEnd()
          .close();
      }
    } catch (Exception x) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      log("ERROR: " + x);
      returnMessage("ERROR: " + x, response);
    }
  } // doPost
  
  /**
   * Starts a job transcribing the given recording.
   * @param wav
   * @return The job thread.
   */
  public Job startTranscriptionJob(File wav) throws Exception {
    
    // create and configure the transcriber...

    // the transcriber implementation is the first jar in the transcriber directory
    File transcriberDir = new File(getServletContext().getRealPath("transcriber"));
    File[] transcribers = transcriberDir.listFiles((File dir, String name)->{
        return name.endsWith(".jar");
      });
    if (transcribers.length == 0) {
      throw new Exception("There are no transcribers in " + transcriberDir.getPath());
    }
    File jar = transcribers[0];
    AnnotatorDescriptor descriptor = new AnnotatorDescriptor(jar);
    Annotator annotator = descriptor.getInstance();
    if (!(annotator instanceof Transcriber)) {
      throw new Exception("Annotator: " + jar.getName() + " is not a transcriber");
    }
    Transcriber transcriber = (Transcriber)annotator;

    // give the transcriber the resources it needs...
    
    transcriber.setSchema(
      new Schema(
        "who", "turn", "utterance", "word",
        new Layer("who", "Participants").setAlignment(Constants.ALIGNMENT_NONE)
        .setPeers(true).setPeersOverlap(true).setSaturated(true),
        new Layer("turn", "Speaker turns").setAlignment(Constants.ALIGNMENT_INTERVAL)
        .setPeers(true).setPeersOverlap(false).setSaturated(false)
        .setParentId("who").setParentIncludes(true),
        new Layer("utterance", "Utterances").setAlignment(Constants.ALIGNMENT_INTERVAL)
        .setPeers(true).setPeersOverlap(false).setSaturated(true)
        .setParentId("turn").setParentIncludes(true),
        new Layer("word", "Words").setAlignment(Constants.ALIGNMENT_INTERVAL)
        .setPeers(true).setPeersOverlap(false).setSaturated(false)
        .setParentId("turn").setParentIncludes(true)));
    
    File workingDir = new File(transcriberDir, transcriber.getAnnotatorId());
    if (!workingDir.exists()) workingDir.mkdir();
    transcriber.setWorkingDirectory(workingDir);      
    transcriber.getStatusObservers().add(s->log(s));

    Job job = new Job()
      .setTranscriber(transcriber)
      .setWav(wav);
    job.start();
    return job;
  } // end of startTranscriptionJob()

} // end of class StartTranscription
