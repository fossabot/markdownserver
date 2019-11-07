axios.get("/nav")
    .then(function(response) {
        document.getElementsByTagName("body")[0].innerHTML = response.data + document.getElementsByTagName("body")[0].innerHTML;
        updateSite();
    })