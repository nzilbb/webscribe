function listFormats() {
  const formats = document.getElementById("formats");
  const request = new XMLHttpRequest();
  request.open("GET", "listformats");
  request.setRequestHeader("Accept", "application/json");
  request.addEventListener("load", function(e) {
    console.log("statusResult " + this.responseText);
    const response = JSON.parse(this.responseText);
    if (this.status == 200) {
      // response is an array of serializers
      let firstElement = true;
      for (let format of response) {
        const div = document.createElement("div");
        formats.appendChild(div);
        const label = document.createElement("label");
        div.appendChild(label);
        const radio = document.createElement("input");
        radio.name = "mimeType";
        radio.type = "radio";
        radio.className = "format";
        radio.value = format.mimeType;
        if (firstElement) {
          radio.checked = true;
          firstElement = false;
        }
        label.appendChild(radio);
        const img = document.createElement("img");
        img.src = `formatter/${format.icon}`;
        label.appendChild(img);
        label.appendChild(document.createTextNode(format.name));
      }
    } else {
      formats.innerHTML = `<p class="error">${response.message}</p>`;
    }
  }, false);
  request.addEventListener("error", function(e) {
    const result = this.responseText;
    console.log("GET jobstatus failed " + result);
    formats.innerHTML = `<p class='error'>${result}</p>`;
  }, false);
  request.send();
}

var jobId = null;
function selectFile() {
  const input = document.getElementById("file");
  if (input.files.length == 0) return;
  const file = input.files[0];
  console.log("name " + file.name);
  if (!/.*\.[wW][aA][vV]$/.test(file.name)) {
    alert("Please select a .wav file.");
    return;
  }

  // hide file chooser
  document.getElementById("fileChooser").style.display = "none";
  // show upload progress
  document.getElementById("uploadProgress").style.display = "";
  const uploadProgress = document.getElementById("uploadProgressBar");

  const fd = new FormData();
  fd.append("file", file);
  const request = new XMLHttpRequest();
  request.open("POST", "starttranscription");
  request.setRequestHeader("Accept", "application/json");
  request.addEventListener("load", function(e) {
    console.log("uploadResult " + this.responseText);
    uploadProgress.max = uploadProgress.max || 100;
    uploadProgress.value = uploadProgress.max;
    const response = JSON.parse(this.responseText);
    if (this.status == 200) {
      document.getElementById("uploadResult").innerHTML = `<p>${response.message}</p>`;
      jobId = response.jobId;
      monitorJob();

      // can they get notification by email?
      if (response.canSendEmail) {
        // show email address form
        document.getElementById("notification").style.display = "";
      }
    } else {
      document.getElementById("uploadResult").innerHTML
        = `<p class="error">${response.message}</p>`;
    }
  }, false);
  request.addEventListener("error", function(e) {
    const result = this.responseText;
    console.log("uploadFailed " + result);
    uploadProgress.max = uploadProgress.max || 100;
    uploadProgress.value = uploadProgress.value || 1;
    uploadProgress.title = "100%";
    document.getElementById("uploadResult").innerHTML
      = `<p class='error'>${result}</p>`;
  }, false);
  request.upload.addEventListener("progress", function(e) {
    console.log("uploadProgress " + e.loaded);
    if (e.lengthComputable) {
      uploadProgress.max = e.total;
      uploadProgress.value = e.loaded;
      uploadProgress.title = (uploadProgress.value * 100 / uploadProgress.max) + "%";
    }
  }, false);
  document.getElementById("uploadResult").innerHTML = `<p>Uploading...</p>`;
  request.send(fd);
}

let monitorTimer = null;
function monitorJob() {
  // show upload progress
  document.getElementById("jobProgress").style.display = "";
  const jobProgress = document.getElementById("jobProgressBar");
  
  const request = new XMLHttpRequest();
  request.open("GET", `jobstatus/${jobId}`);
  request.setRequestHeader("Accept", "application/json");
  request.addEventListener("load", function(e) {
    console.log("statusResult " + this.responseText);
    try {
      const response = JSON.parse(this.responseText);
      if (this.status == 200) {
        jobProgress.value = response.percentComplete;
        jobProgress.title = `${jobProgress.value}%`;            
        document.getElementById("jobStatus").innerHTML
          = (/%/.test(response.message)? // percent progress (e.g. from download of models)
             `<pre>${response.message}</pre>`: // use <pre> for correct spacing of text 
             `<p>${response.message}</p>`); // just a message so no particular formatting
        
        if (response.running) {
          document.getElementById("jobRunning").style.display = "";
          // check back in a second
          monitorTimer = window.setTimeout(monitorJob, 1000);
        } else {
          document.getElementById("jobRunning").style.display = "none";
          document.getElementById("jobStatus").innerHTML = `<p>Transcription finished.</p>`;
          downloadTranscript();
        }
      } else {
        document.getElementById("jobRunning").style.display = "none";
        document.getElementById("jobStatus").innerHTML
          = `<p class="error">${response.message}</p>`;
      }
    } catch (x) {
      document.getElementById("jobRunning").style.display = "none";
      document.getElementById("jobStatus").innerHTML
        = `<p class="error">Status: ${this.status}</p>${this.responseText}`;
    }
  }, false);
  request.addEventListener("error", function(e) {
    const result = this.responseText;
    console.log("GET jobstatus failed " + result);
    document.getElementById("jobRunning").style.display = "none";
    document.getElementById("jobStatus").innerHTML = `<p class='error'>${result}</p>`;
  }, false);
  request.send();
}

function downloadTranscript() {
  // reset file chooser
  document.getElementById("fileChooser").style.display = "";
  document.getElementById("file").value = null;
  
  // get selected mimeType
  let mimeType = "application/json";
  for (let radio of document.getElementsByClassName("format")) {
    if (radio.checked) {
      mimeType = radio.value;
      break;
    }
  } // next format

  // get file
  window.location = "transcript/"+jobId+"?format="+encodeURIComponent(mimeType);
}

function notify() {
  if (document.getElementById("email").reportValidity()) {
    let email = document.getElementById("email").value;
    // get selected mimeType
    let mimeType = "application/json";
    for (let radio of document.getElementsByClassName("format")) {
      if (radio.checked) {
        mimeType = radio.value;
        break;
      }
    } // next format

    email = encodeURIComponent(email);
    mimeType = encodeURIComponent(mimeType);
    const request = new XMLHttpRequest();
    request.open("GET", `sendtranscript/${jobId}?email=${email}&format=${mimeType}`);
    request.setRequestHeader("Accept", "application/json");
    request.addEventListener("load", function(e) {
      console.log("sendtranscript " + this.responseText);
      if (this.status == 200) {
        try {
          const response = JSON.parse(this.responseText);
          jobProgress.value = response.percentComplete;
          jobProgress.title = `${jobProgress.value}%`;

          // stop monitoring job
          window.clearTimeout(monitorTimer);

          // notify the user
          document.getElementById("jobStatus").innerHTML
            = "<i>You will receive an email with a download link when transcription is finished.</i>";

          // reset the form
          document.getElementById("fileChooser").style.display = "";
          document.getElementById("file").value = null;
          document.getElementById("jobRunning").style.display = "none";
          document.getElementById("uploadProgress").style.display = "none";
          document.getElementById("notification").style.display = "none";
          
        } catch (x) {
          document.getElementById("jobRunning").style.display = "none";
          document.getElementById("jobStatus").innerHTML
            = `<p class="error">Status: ${this.status}</p>${this.responseText}`;
        }
      } else {
        try {
          const response = JSON.parse(this.responseText);
          document.getElementById("jobStatus").innerHTML
            = `<p class="error">${response.message}</p>`;
        } catch (x) {
          document.getElementById("jobStatus").innerHTML
            = `<p class="error">Status: ${this.status}</p>${this.responseText}`;
        }
      }
    }, false);
    request.addEventListener("error", function(e) {
      const result = this.responseText;
      console.log("GET jobstatus failed " + result);
      document.getElementById("jobStatus").innerHTML = `<p class='error'>${result}</p>`;
    }, false);
    request.send();
  } // email address was valid as far as the browser is concerned
}

document.getElementById("file").onchange = selectFile;
document.getElementById("sendtranscript").onclick = notify;
listFormats();
