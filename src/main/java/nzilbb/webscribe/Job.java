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
import nzilbb.ag.Constants;
import nzilbb.ag.Graph;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.ag.automation.Annotator;
import nzilbb.ag.automation.InvalidConfigurationException;
import nzilbb.ag.automation.Transcriber;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.configure.ParameterSet;
import nzilbb.util.IO;

/**
 * Ongoing transcription job.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Job extends Thread {

  protected static final ThreadGroup jobThreadGroup = new ThreadGroup("nzilbb.webscribe.Job");
  
  /**
   * Finds a job given its ID.
   * @param lThreadId the Job's ID
   * @return The identified Job, or null if it can't be found.
   */
  public static Job FindJob(long id) {
    Thread[] threads = new Thread[jobThreadGroup.activeCount()];
    jobThreadGroup.enumerate(threads);
    for (Thread thread : threads) {
      if (thread != null && thread.getId() == id) {
        return (Job)thread;
      }
    } // next thread
    return null;
  }

  /**
   * The speech recording to transcribe.
   * @see #getWav()
   * @see #setWav(File)
   */
  protected File wav;
  /**
   * Getter for {@link #wav}: The speech recording to transcribe.
   * @return The speech recording to transcribe.
   */
  public File getWav() { return wav; }
  /**
   * Setter for {@link #wav}: The speech recording to transcribe.
   * @param newWav The speech recording to transcribe.
   */
  public Job setWav(File newWav) {
    wav = newWav;
    // name the thread after the wav
    setName(wav.getName());
    return this; }
  
  /**
   * The transcriber implementation to use for transcription.
   * @see #getTranscriber()
   * @see #setTranscriber(Transcriber)
   */
  protected Transcriber transcriber;
  /**
   * Getter for {@link #transcriber}: The transcriber implementation to use for transcription.
   * @return The transcriber implementation to use for transcription.
   */
  public Transcriber getTranscriber() { return transcriber; }
  /**
   * Setter for {@link #transcriber}: The transcriber implementation to use for transcription.
   * @param newTranscriber The transcriber implementation to use for transcription.
   */
  public Job setTranscriber(Transcriber newTranscriber) { transcriber = newTranscriber; return this; }

  /**
   * The resulting transcript.
   * @see #getTranscript()
   * @see #setTranscript(Graph)
   */
  protected Graph transcript;
  /**
   * Getter for {@link #transcript}: The resulting transcript.
   * @return The resulting transcript.
   */
  public Graph getTranscript() { return transcript; }
  /**
   * Setter for {@link #transcript}: The resulting transcript.
   * @param newTranscript The resulting transcript.
   */
  public Job setTranscript(Graph newTranscript) { transcript = newTranscript; return this; }
  
  /**
   * Default constructor.
   */
  public Job() {
    super(jobThreadGroup, "");
  } // end of constructor

  public void run() {
    Graph transcript = new Graph();
    transcript.setId(IO.WithoutExtension(wav));
    transcript.setSchema((Schema)transcriber.getSchema().clone());
    try {      
      // transcribe the audio
      getTranscriber().transcribe(getWav(), transcript);
      setTranscript(transcript);
    } catch(Exception exception) {
      System.err.println("Error transcribing " + wav.getName() + ": " + exception);
      exception.printStackTrace(System.err);
    }
    try { // give any observers a chance to get the status before we finish
      Thread.sleep(10000);
    } catch (Exception x) {
    }
  } // run
} // end of class Job
