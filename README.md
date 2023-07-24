# webscribe

Simple web service for audio transcription

This is a web service that allows upload of a wav file and returns a transcript in the
desired format.

Webscribe uses the [nzilbb.ag](https://github.com/nzilbb/ag) annotation graph automation
framework which includes:

- An abstract object model for modelling speech acts
  ([Annotation Graphs](https://nzilbb.github.io/ag/apidocs/nzilbb/ag/package-summary.html))
- [Transcriber](https://github.com/nzilbb/ag/tree/master/transcriber) implementations that
  integrate with different ASR systems in order to create annotation graphs representing
  transcripts of recordings. 
- [Serializer](https://github.com/nzilbb/ag/tree/master/formatter) implementations that
  transform annotation graphs into different tool formats, e.g. ELAN, Praat TextGrid,
  WebVTT subtitles, etc. 

The default implementation uses [OpenAI's](https://openai.com/)
[Whisper](https://github.com/openai/whisper) speech-to-text system for transcription and
includes download as ELAN transcript or Praat TextGrid.

## Installation

These instructions assume installation on a Linux-based server.

Prerequisites include:

- at least version 3.7 of python
  (`sudo yum install python3.8 && sudo alternatives --set python3 /usr/bin/python3.8`)
- pip3 (`sudo yum install python3-pip`)
- git (`sudo yum install git`)
- ffmpeg (`sudo yum install ffmpeg`)
- Apache Tomcat (`sudo yum install tomcat`)

1. Install Whisper on the server.
  For up-to-date instructions, see the [Whisper site](https://github.com/openai/whisper#setup)
  but the following should work:
  `sudo pip3 install git+https://github.com/openai/whisper.git`
2. Copy *webscribe.war* to the Tomcat webapps directory.
  `cp ~/webscribe.war /var/lib/tomcat/webapps/`

That's it. If you browse to your server's address with `webscribe/` appended to the URL
(e.g. `http://localhost:8080/webscribe/`), you'll see the *webscribe* browser interface
where you can upload a recording.

## How to use

1. Click the *Browse* button and select a .wav file to transcribe.
2. Select the transcript format you want.
3. Wait patiently while the file uploads to the server (there's a progress bar that
   indicates how much is uploaded so far).
4. Wait patiently while the recording is transcribed (there's a second progress bar that
   may indicate progress, although generally this stays at 0% for a long time and then is
   suddenly at 100%).
   The first time you upload a recording, there is an initial delay while Whisper
   downloads its models for STT. This is a one-time process, and subsequent recordings
   don't require this initial delay.
5. Save the resulting transcript.

