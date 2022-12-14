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
import java.util.Vector;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
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
import nzilbb.ag.serialize.GraphSerializer;
import nzilbb.ag.serialize.SerializationDescriptor;
import nzilbb.ag.serialize.util.IconHelper;
import nzilbb.util.IO;

/**
 * Lists available transcript formats.
 * @author Robert Fromont robert@fromont.net.nz
 */
@WebServlet("/listformats")
public class ListFormats extends ServletBase {
  /**
   * Default constructor.
   */
  public ListFormats() {
  } // end of constructor
  
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    response.setContentType("application/json;charset=UTF-8");
    
    // return a list of serializers
    File serializerDir = new File(getServletContext().getRealPath("formatter"));
    File[] serializers = serializerDir.listFiles((File dir, String name)->{
        return name.endsWith(".jar");
      });
    JsonGenerator json = Json.createGenerator(response.getWriter())
      .writeStartArray();
    try {
      for (File jar : serializers) {
        Vector implementors = IO.FindImplementorsInJar(
          jar, getClass().getClassLoader(), 
          Class.forName("nzilbb.ag.serialize.GraphSerializer"));
        for (Object o : implementors) {
          GraphSerializer serializer = (GraphSerializer)o;
          SerializationDescriptor descriptor = serializer.getDescriptor();
          json.writeStartObject();
          try {
            json.write("name", descriptor.getName())
              .write("version", descriptor.getVersion())
              .write("mimeType", descriptor.getMimeType());
            File iconFile = IconHelper.EnsureIconFileExists(descriptor, serializerDir);
            json.write("icon", iconFile.getName());            
          } finally {
            json.writeEnd();
          }
        }
      } // next serializer jar
    } catch (Exception x) {
      json.writeStartObject();
      json.write("error", ""+x);
      json.writeEnd();
    } finally {
      json.writeEnd()
        .close();
    }
    
  }
}
