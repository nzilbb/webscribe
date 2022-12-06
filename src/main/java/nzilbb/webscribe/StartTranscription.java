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

import java.util.List;
import java.io.IOException;
import java.io.File;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import org.apache.commons.fileupload.disk.*;

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
            && item.getName() != null && item.getName().endsWith(".wav")) { // it's a wav file
          log("File: " + item.getName());
          
          // save file
          wav = File.createTempFile(item.getName(), ".wav");
          wav.deleteOnExit();
          wav.delete(); // (so item.write doesn't complain)
          item.write(wav);
          log("Saved: " + wav.getPath());
          break; // only one file at a time
        } // .wav file
      } // next item
      
      if (wav == null) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        returnMessage("No wav file found.", response);
      } else {
        // TODO start transcription task
        returnMessage("Uploaded " + wav.getName(), response);
      }
    } catch (Exception x) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      log("ERROR: " + x);
      returnMessage("ERROR: " + x, response);
    }
  } // doPost
} // end of class StartTranscription
