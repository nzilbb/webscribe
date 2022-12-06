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



