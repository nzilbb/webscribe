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

import java.io.IOException;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Base class for providing convenience functionality to servlets.
 * @author Robert Fromont robert@fromont.net.nz
 */

public class ServletBase extends HttpServlet {
  
  /**
   * Default constructor.
   */
  public ServletBase() {
  } // end of constructor

  /**
   * Writes a JSON-formatted via the given response.
   * @param message The message to return.
   * @param response The response to write to.
   * @throws IOException
   */
  protected void returnMessage(String message, HttpServletResponse response) throws IOException {
    Json.createGenerator(response.getWriter())
      .writeStartObject()
      .write("message", message)
      .writeEnd()
      .close();
  } // end of returnMessage()

  /**
   * Writes a JSON-formatted via the given response.
   * @param message The message to return.
   * @param response The response to write to.
   * @throws IOException
   */
  protected void returnResult(String message, String result, HttpServletResponse response)
    throws IOException {
    Json.createGenerator(response.getWriter())
      .writeStartObject()
      .write("message", message)
      .write("result", result)
      .writeEnd()
      .close();
  } // end of returnMessage()

} // end of class ServletBase
