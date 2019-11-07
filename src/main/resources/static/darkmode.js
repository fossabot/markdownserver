$(document).ready(function () {
    console.log("Darkmode? " + hasDarkmode());
    updateSite();
});

function updateSite() {
    if (hasDarkmode()) {
        $("body").addClass("dark");
        $("table").addClass("dark");
        $(".dropdown-menu").addClass("dark");
        $("#dark-on").removeClass("d-none");
        $("#dark-off").addClass("d-none");
    } else {
        $("body").removeClass("dark");
        $("table").removeClass("dark");
        $(".dropdown-menu").removeClass("dark");
        $("#dark-on").addClass("d-none");
        $("#dark-off").removeClass("d-none");
    }
}

function toggleDarkmode() {
    if (hasDarkmode()) {
        Cookies.remove('dark');
        console.log("Darkmode disabled");
    } else {
        Cookies.set('dark', 'true', {
            expires: 365
        });
        console.log("Darkmode enabled");
    }
    updateSite();
}

function hasDarkmode() {
    return Cookies.get('dark') === "true";
}