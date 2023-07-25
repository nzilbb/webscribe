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

import javax.servlet.ServletContext;
import java.util.Vector;
import java.util.function.Consumer;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
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

/**
 * Serializes annotation graphs to given formats.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class SerializeService {

  ServletContext context;

  /**
   * Constructor.
   * @param config The servlet configuration.
   */
  public SerializeService(ServletContext context) {
    this.context = context;
  } // end of constructor
  
  /**
   * Formats the given annotation graph as the given content type.
   * <p> This method is asynchronous; the stream may be returned before any data has been
   * written to it, and serialization is completed in another thread.
   * @param transcript
   * @param mimeType
   * @return The serialized data, which may be a compressed zip stream containing multiple
   * files.
   * @throws NullPointerException If there's no serializer available for the
   * <var>mimeType</var> or it generated no output files.
   * @throws NullPointerException If there's no serializer available for the
   * <var>mimeType</var> or it generated no output files.
   */
  public NamedStream serialize(Graph transcript, String mimeType) throws NullPointerException, Exception {
    // find serializer
    GraphSerializer serializer = findSerializer(mimeType);
    if (serializer == null) throw new NullPointerException("No formatter found: " + mimeType);

    // configure serializer
    ParameterSet configuration = new ParameterSet();
    // get default value suggestions
    configuration = serializer.configure(configuration, transcript.getSchema());
    // use default values
    serializer.configure(configuration, transcript.getSchema());
    
    // serialize the graph
    
    String[] layerIds = {
      transcript.getSchema().getUtteranceLayerId(), "scribe", "date" };
    final Vector<NamedStream> files = new Vector<NamedStream>();
    serializer.serialize(
      nzilbb.ag.serialize.util.Utility.OneGraphSpliterator(transcript), layerIds,
      new Consumer<NamedStream>() {
        public void accept(NamedStream stream) {
          files.add(stream);
        }},
      new Consumer<String>() {
        public void accept(String warning) {
          context.log("SerializeService: WARNING: " + warning);
        }},
      new Consumer<SerializationException>() {
        public void accept(SerializationException exception) {
          context.log("SerializeService: SerializeFragment error: " + exception);
        }       
      });
    
    if (files.size() == 0) throw new NullPointerException("No files were generated");
    
    if (files.size() == 1) { // one file only
      // don't zip a single file, just return the file
      return files.firstElement();
    } else { /// multiple files
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
                  zipOut.putNextEntry(new ZipEntry(IO.SafeFileNameUrl(stream.getName())));
                  
                  IO.Pump(stream.getStream(), zipOut, false);
                } catch (ZipException zx) {
                } finally {
                  stream.getStream().close();
                }
              } // next file
              try {
                zipOut.close();
              } catch(Exception exception) {
                context.log("SerializeService: Cannot close ZIP file: " + exception);
              }
            } catch(Exception exception) {
              context.log("SerializeGraphs: open zip stream: " + exception);
            }
          }
        }).start();

      return new NamedStream(inStream, IO.SafeFileNameUrl(transcript.getId()) + ".zip");      
    } // multiple files              
  } // end of serialize()
  
  /**
   * Finds the serializer that outputs the given format.
   * @param mimeType
   * @return The serializer, or null if it couldn't be found.
   */
  protected GraphSerializer findSerializer(String mimeType) {
    if ("application/json".equals(mimeType)) {
      return new nzilbb.ag.serialize.json.JSONSerialization();
    }
    // get a list of serializers
    File serializerDir = new File(context.getRealPath("formatter"));
    File[] serializers = serializerDir.listFiles((File dir, String name)->{
        return name.endsWith(".jar");
      });
    for (File jar : serializers) {
      try {
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
      } catch (ClassNotFoundException x) {
        context.log("SerializeService: " + x);
      } catch (IOException x) {
        context.log("SerializeService: " + x);
      }
    } // next jar file
    // if we got this far, we didn't find it
    return null;
  } // end of findSerializer()

} // end of class SerializeService
