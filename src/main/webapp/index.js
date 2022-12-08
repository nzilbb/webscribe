var jobId = null;
function selectFile() {
    const input = document.getElementById("file");
    if (input.files.length == 0) return;
    const file = input.files[0];
    console.log("name " + file.name);

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
            jobId = response.result;
            monitorJob();
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
    request.addEventListener("progress", function(e) {
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

function monitorJob() {
    // show upload progress
    document.getElementById("jobProgress").style.display = "";
    const jobProgress = document.getElementById("jobProgressBar");
    
    const request = new XMLHttpRequest();
    request.open("GET", `jobstatus/${jobId}`);
    request.setRequestHeader("Accept", "application/json");
    request.addEventListener("load", function(e) {
        console.log("statusResult " + this.responseText);
        const response = JSON.parse(this.responseText);
        if (this.status == 200) {
            jobProgress.value = response.percentComplete;
            jobProgress.title = `${jobProgress.value}%`;
            document.getElementById("jobStatus").innerHTML = `<p>${response.message}</p>`;

            // check back in a second
            window.setTimeout(monitorJob, 1000);
        } else {
            document.getElementById("jobStatus").innerHTML
                = `<p class="error">${response.message}</p>`;
        }
    }, false);
    request.addEventListener("error", function(e) {
        const result = this.responseText;
        console.log("GET jobstatus failed " + result);
        document.getElementById("jobStatus").innerHTML = `<p class='error'>${result}</p>`;
    }, false);
    request.send();
}

document.getElementById("file").onchange = selectFile;
