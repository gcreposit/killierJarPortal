let universalBucketName="";
let universalFileName="";
let universalFilePath="";
document.getElementById('uploadButton').addEventListener('click', async () => {
    const fileInput = document.getElementById('jarFileExp');
    const file = fileInput.files[0];

    if (!file) {
        alert('Please select a file to upload.');
        return;
    }

    const bucketName = 'gpdm_bucket';
    universalBucketName=bucketName;

    const fileName = file.name;
    universalFileName=fileName;
    const apiUrl = `https://storage.googleapis.com/upload/storage/v1/b/${bucketName}/o?uploadType=media&name=${fileName}`;

    // Fetch access token from the Spring Boot endpoint
    const tokenResponse = await fetch("/data/api/token");
    const accessToken = await tokenResponse.text();
    console.log("acessToken IS", accessToken);

    if (!accessToken) {
        alert("Failed to fetch access token.");
        return;
    }


    try {

        // Upload file to Google Cloud Storage
        const response = await fetch(apiUrl, {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${accessToken}`,
                'Content-Type': file.type,
            },
            body: file,
        });

        if (response.ok) {
            alert('File uploaded successfully!');
            universalFilePath= response.name;

        } else {
            const errorText = await response.text();
            alert(`File upload failed: ${response.statusText}\n${errorText}`);
        }
    } catch (error) {
        console.error('Error:', error);
        alert(`Error uploading file: ${error.message}`);
    }
});


async function commonBucketFun() {
    const statusDiv = document.getElementById("status");
    statusDiv.textContent = "Processing...";
debugger;

    try {

        const id = document.getElementById("id")?.value ?? "";
        // Step 3: Notify backend to finalize upload
        // Get all user inputs from the form
        const developerName = document.getElementById("developerName").value;
        const website = document.getElementById("website").value;
        const domain = document.getElementById("domain").value;
        const port = document.getElementById("port").value;
        const url = document.getElementById("url").value;
        const date = document.getElementById("date").value;
        const month = document.getElementById("month").value;
        const time = document.getElementById("time").value;
        const originalFileName = document.getElementById("jarFileName").value;
        const filePath = `${universalBucketName}/${universalFileName}`; // Construct the file path
        const finalizeResponse = await fetch("/data/finalizeUpload", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                id:id,
                developerName: developerName,
                website: website,
                domain: domain,
                port: port,
                url: url,
                date: date,
                month: month,
                time: time,
                bucketFilePath: originalFileName,
                originalFileName: originalFileName,// Include the constructed filePath
            }),
        });

        if (finalizeResponse.ok) {
            statusDiv.textContent = "File uploaded successfully to GCS! Finalizing...";
            alert("Downloaded successfully!");
        } else {
            const errorText = await finalizeResponse.text();
            console.error("Finalize upload failed:", errorText);
            alert(`Finalize upload failed: ${errorText}`);
        }

        statusDiv.textContent = "File uploaded and processed successfully!";
    } catch (error) {
        console.error(error);
        statusDiv.textContent = "An error occurred during the upload process.";
    }
}



//It an Update function Implementation in Bucket Script
function fetchIdWiseData(id) {
    console.log("id", id);
    fetch('/data/fetchIdWiseDataForJar/' + id, {
        method: 'GET'
    }).then(response => response.json()).then(data => {
        console.log("Id Wise data", data);

        // Set data to modal inputs
        document.getElementById('developerName').value = data[0].developerName;
        document.getElementById('jarFileName').value = data[0].originalFileName;
        document.getElementById('id').value = data[0].id;
        // document.getElementById('jarFilePath').value = data[0].jarFilePath;
        document.getElementById('website').value = data[0].website;
        document.getElementById('domain').value = data[0].domain;
        document.getElementById('port').value = data[0].port;
        document.getElementById('url').value = data[0].url;
        document.getElementById('date').value = data[0].date;
        document.getElementById('month').value = data[0].month;
        document.getElementById('time').value = data[0].time;

        // Open the modal

    }).catch(error => {
        console.error('Error fetching data:', error);
    });
}


































