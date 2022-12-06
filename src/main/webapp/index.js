function selectFile() {
    const input = document.getElementById("file");
    if (input.files.length == 0) return;
    const file = input.files[0];
    console.log("name " + file.name);

    // hide file chooser
    document.getElementById("fileChooser").style.display = "none";
    // show upload progress
    document.getElementById("uploadProgress").style.display = "";
    const uploadProgress = document.getElementById("progress");

    const fd = new FormData();
    fd.append("file", file);
    const request = new XMLHttpRequest();
    request.open("POST", "starttranscription");
    request.setRequestHeader("Accept", "application/json");
    request.addEventListener("load", function(e) {
        console.log("uploadResult " + this.responseText);
        uploadProgress.max = uploadProgress.max || 100;
        uploadProgress.value = uploadProgress.max;
        const result = this.responseText;
        if (this.status == 200) {
            document.getElementById("uploadResult").innerHTML = `<p>${result}</p>`;
        } else {
            document.getElementById("uploadResult").innerHTML = `<p class="error">${result}</p>`;
        }
    }, false);
    request.addEventListener("error", function(e) {
        const result = this.responseText;
        console.log("uploadFailed " + result);
        uploadProgress.max = uploadProgress.max || 100;
        uploadProgress.value = uploadProgress.value || 1;
        document.getElementById("uploadResult").innerHTML
            = `<p class='error'>${result}</p>`;
    }, false);
    request.addEventListener("progress", function(e) {
        console.log("uploadProgress " + e.loaded);
        if (e.lengthComputable) {
            uploadProgress.max = e.total;
            uploadProgress.value = e.loaded;
        }
    }, false);
    document.getElementById("uploadResult").innerHTML = `<p>Uploading...</p>`;
    request.send(fd);
}

document.getElementById("file").onchange = selectFile;
