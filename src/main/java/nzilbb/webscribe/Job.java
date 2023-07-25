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
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.function.Consumer;
import nzilbb.ag.Annotation;
import nzilbb.ag.Constants;
import nzilbb.ag.Graph;
import nzilbb.ag.Layer;
import nzilbb.ag.Schema;
import nzilbb.ag.automation.Annotator;
import nzilbb.ag.automation.InvalidConfigurationException;
import nzilbb.ag.automation.Transcriber;
import nzilbb.ag.serialize.util.Utility;
import nzilbb.ag.util.FileMediaProvider;
import nzilbb.configure.ParameterSet;
import nzilbb.util.IO;

/**
 * Ongoing transcription job.
 * @author Robert Fromont robert@fromont.net.nz
 */
public class Job extends Thread { // TODO periodically purge finished jobs

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
   * What to do when finished, if anything.
   * @see #getOnFinished()
   * @see #setOnFinished(Consumer<Job>)
   */
  protected Consumer<Job> onFinished;
  /**
   * Getter for {@link #onFinished}: What to do when finished, if anything.
   * @return What to do when finished, if anything.
   */
  public Consumer<Job> getOnFinished() { return onFinished; }
  /**
   * Setter for {@link #onFinished}: What to do when finished, if anything.
   * @param newOnFinished What to do when finished, if anything.
   */
  public Job setOnFinished(Consumer<Job> newOnFinished) {
    onFinished = newOnFinished;
    // has the transcriber already finished?
    if (transcriber != null && !transcriber.getRunning()) {
      try {
        onFinished.accept(this);
      } catch(Throwable exception) {
      }
    }
    return this;
  }

  private SimpleDateFormat utcIsoTime;
  
  /**
   * Default constructor.
   */
  public Job() {
    super(jobThreadGroup, "");

    TimeZone tz = TimeZone.getTimeZone("UTC");
    utcIsoTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    utcIsoTime.setTimeZone(tz);

  } // end of constructor

  @Override public void run() {
    Graph transcript = new Graph();
    transcript.setId(IO.WithoutExtension(wav));
    transcript.setSchema((Schema)transcriber.getSchema().clone());
    // ensure the serializer can know the media file name
    transcript.setMediaProvider(new FileMediaProvider().withFile(getWav()));
    // include transcriber name tag
    String annotator = getTranscriber().getAnnotatorId() + " v" + getTranscriber().getVersion();
    transcript.createTag(transcript, "scribe", annotator)
      .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
    // include transcription date tag
    transcript.createTag(transcript, "date", utcIsoTime.format(new java.util.Date()))
      .setConfidence(Constants.CONFIDENCE_AUTOMATIC);
    try {      
      // transcribe the audio
      getTranscriber().transcribe(getWav(), transcript);
      setTranscript(transcript);
      // tag all anotations as annotated by the transcriber
      for (Annotation annotation : transcript.getAnnotationsById().values()) {
        annotation.setAnnotator(annotator);
      }
      // delete the wav file
      System.err.println("Deleting " + getWav().getPath());
      getWav().delete();
      
      // email the human?
      if (onFinished != null) {
        try {
          onFinished.accept(this);
        } catch(Throwable exception) {
        }
      }
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
